#!/usr/bin/env python3
"""Analyze a local Perfetto trace. No device calls, network, or application mutation.

Usage: python3 analyze_equalizer.py app.json recording.pftrace analysis-prefix
Requires the sibling official trace_processor_shell and recovered SQL.
"""
import collections
import bisect
import csv
import hashlib
import io
import json
import math
import os
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
TRACE_PROCESSOR = os.environ.get("TRACE_PROCESSOR", str(HERE / "trace_processor_shell"))
NULL = "[NULL]"


def percentile(values):
    values = sorted(values)
    return {"count": len(values), **{
        f"p{p}": values[math.ceil(len(values) * p / 100) - 1] if values else None
        for p in (50, 95, 99)
    }, "maximum": values[-1] if values else None}


def query(trace, sql):
    result = subprocess.run([TRACE_PROCESSOR, "query", str(trace), sql],
                            check=True, text=True, capture_output=True)
    return list(csv.DictReader(io.StringIO(result.stdout)))


def main():
    app_path, trace_path, output = map(Path, sys.argv[1:])
    app = json.loads(app_path.read_text())
    result = subprocess.run([TRACE_PROCESSOR, "query", "-f",
        str(HERE / "equalizer-presentation-recovered.sql"), str(trace_path)],
        check=True, text=True, capture_output=True)
    output.with_suffix(".raw.csv").write_text(result.stdout)
    rows = list(csv.DictReader(io.StringIO(result.stdout)))
    if not rows:
        raise RuntimeError("No equalizer command trace markers; cannot report performance")
    metadata = query(trace_path, """
      SELECT b.start_ts, b.end_ts, (b.end_ts-b.start_ts)/1e9 AS trace_seconds,
        (SELECT COUNT(DISTINCT t.upid) FROM slice s JOIN thread_track tt ON tt.id=s.track_id
          JOIN thread t USING(utid) WHERE s.name GLOB 'VC_EQ_CMD:*') AS marker_process_count,
        (SELECT GROUP_CONCAT(DISTINCT CAST(p.pid AS TEXT)) FROM slice s
          JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING(utid)
          JOIN process p USING(upid) WHERE s.name GLOB 'VC_EQ_CMD:*') AS marker_pids
      FROM trace_bounds b""")[0]
    if int(metadata["marker_process_count"]) != 1:
        raise RuntimeError("Multiple marker processes: no unambiguous app attribution")
    trace_stats = query(trace_path, """
      SELECT name,value,severity FROM stats WHERE value!=0 AND
       (severity!='info' OR name LIKE '%lost%' OR name LIKE '%discard%'
        OR name LIKE '%overrun%' OR name LIKE '%flush%')""")
    for row in rows:
        for key, value in row.items():
            if value == NULL:
                row[key] = None
        for key in ("command_id", "command_trace_ts", "draw_trace_ts", "display_present_trace_ts",
                    "surface_frame_token", "metric_surface_frame_token"):
            if row[key] is not None:
                row[key] = int(row[key])
        row["command_marker_to_present_ms"] = (float(row["command_marker_to_present_ms"])
                                        if row["command_marker_to_present_ms"] is not None else None)
    by_id = {c["id"]: c for c in app["commands"]}
    traced_ids = {r["command_id"] for r in rows}
    if traced_ids - by_id.keys():
        raise RuntimeError("Trace contains commands outside supplied app report")
    clocks = query(trace_path, "SELECT clock_value,ts,ts-clock_value AS trace_offset FROM clock_snapshot WHERE clock_id=3 ORDER BY clock_value")
    clocks = [{k:int(v) for k,v in c.items()} for c in clocks]
    clock_values = [c["clock_value"] for c in clocks]
    marker_delays_ms = []
    for row in rows:
        command_ns = by_id[row["command_id"]]["commandNanos"]
        clock_index = bisect.bisect_right(clock_values, command_ns)-1
        if clock_index < 0:
            raise RuntimeError("No preceding CLOCK_MONOTONIC snapshot; cannot anchor true callback time")
        command_in_trace = command_ns + clocks[clock_index]["trace_offset"]
        marker_delay = (row["command_trace_ts"]-command_in_trace)/1e6
        if marker_delay < -0.01 or marker_delay > 10:
            raise RuntimeError(f"Unexplained command clock alignment: {marker_delay}ms")
        marker_delays_ms.append(marker_delay)
        row["command_callback_trace_ts"] = command_in_trace
        row["command_to_present_ms"] = None
        # Preserve exact-token eligibility from SQL; only move the start from the
        # slightly later trace marker to the measured UI-callback nanoTime.
        if row["command_marker_to_present_ms"] is not None:
            row["command_to_present_ms"] = (row["display_present_trace_ts"]-command_in_trace)/1e6
    presented = [r for r in rows if r["command_to_present_ms"] is not None]
    known = [r for r in rows if r["metric_surface_frame_token"] is not None]
    mismatches = [r for r in known if r["metric_surface_frame_token"] != r["surface_frame_token"]]
    controls = {}
    for control in sorted({r["control"] for r in rows}):
        subgroup = [r for r in rows if r["control"] == control]
        good = [r for r in subgroup if r["command_to_present_ms"] is not None]
        controls[control] = {
            "commands": len(subgroup), "verified_presentations": len(good),
            "verified_fraction": len(good) / len(subgroup),
            "first_draw_frame_dropped": sum(r["app_present_type"] == "Dropped Frame" for r in subgroup),
            "missing_surface_frame": sum(r["matching_app_frames"] == "0" for r in subgroup),
            "token_disagreements": sum(r in mismatches for r in subgroup),
            "command_to_present_ms": percentile([r["command_to_present_ms"] for r in good]),
            "app_jank_labels": dict(collections.Counter(r["app_jank_type"] for r in subgroup)),
            "display_jank_labels": dict(collections.Counter(r["display_jank_type"] for r in subgroup)),
        }
    groups = []
    for r in rows:
        if not groups or groups[-1][0]["control"] != r["control"]:
            groups.append([])
        groups[-1].append(r)
    cadence = {}
    sliders = {"size", "sharpness", "contrast", "comfort", "intensity", "text_weight"}
    for control in sorted(sliders):
        # Harness uses exactly 40 changed callback values per completed 700-ms gesture.
        gestures = [g for g in groups if g[0]["control"] == control and len(g) == 40]
        intervals, presentation_intervals, command_span_ns = [], [], 0
        verified_intervals = 0
        for gesture in gestures:
            times = [by_id[r["command_id"]]["commandNanos"] for r in gesture]
            intervals += [(b-a)/1e6 for a,b in zip(times,times[1:])]
            command_span_ns += times[-1]-times[0]
            display_times = sorted({r["display_present_trace_ts"] for r in gesture
                                    if r["command_to_present_ms"] is not None})
            verified_intervals += max(0,len(display_times)-1)
            presentation_intervals += [(b-a)/1e6 for a,b in zip(display_times,display_times[1:])]
        cadence[control] = {
            "complete_gestures": len(gestures),
            "excluded_non40_command_groups": sum(g[0]["control"] == control and len(g) != 40 for g in groups),
            "callback_interval_ms": percentile(intervals),
            "callback_hz_during_active_spans": len(intervals)/(command_span_ns/1e9) if command_span_ns else None,
            "verified_presented_update_intervals_per_active_second": verified_intervals/(command_span_ns/1e9) if command_span_ns else None,
            "verified_presentation_interval_ms": percentile(presentation_intervals),
            "definition": "Completed 40-change gestures only. Active spans run first to last callback; rate counts distinct verified presented changes minus one per gesture over those same spans. Missing/dropped states are not invented. This is a conservative proof rate, not a guarantee that every unverified state was never displayed later.",
        }
    memory = app["memorySamples"]
    duration = (memory[-1]["timestampNanos"]-memory[0]["timestampNanos"])/1e9
    last_minute = [m for m in memory if m["timestampNanos"] >= memory[-1]["timestampNanos"]-60e9]
    memory_summary = {"sampling_seconds": duration,
        "allocation_scope": app["allocationScope"],
        "allocated_mib": app["processAllocatedBytesDuringSamples"]/2**20,
        "allocated_mib_per_second": app["processAllocatedBytesDuringSamples"]/2**20/duration,
        "gc_count_delta": memory[-1]["gcCount"]-memory[0]["gcCount"],
        "gc_time_ms_delta": memory[-1]["gcTimeMs"]-memory[0]["gcTimeMs"],
        "interpretation": "Cumulative allocations are not retained heap. Periodic samples do not establish an instantaneous peak or absence of leaks. Whole instrumented process includes Compose, UI automation, renderer and recorder; no precise renderer-only attribution. ART gc-time is cumulative GC activity, not a measurement of UI stop-the-world pauses.",
    }
    for key in ("javaUsedBytes", "nativeAllocatedBytes", "totalPssBytes", "privateDirtyBytes", "graphicsPrivateBytes"):
        memory_summary[key] = {"start_mib": memory[0][key]/2**20,
            "end_mib": memory[-1][key]/2**20,
            "sampled_max_mib": max(m[key] for m in memory)/2**20,
            "last_minute_min_mib": min(m[key] for m in last_minute)/2**20,
            "last_minute_max_mib": max(m[key] for m in last_minute)/2**20}
    draw_clock_crosscheck = []
    for r in rows:
        c = by_id[r["command_id"]]
        if r["draw_trace_ts"] is not None and c["drawNanos"] is not None:
            draw_clock_crosscheck.append(((r["draw_trace_ts"]-r["command_trace_ts"])
               -(c["drawNanos"]-c["commandNanos"]))/1e6)
    row_by_id = {r["command_id"]: r for r in rows}
    temporal_windows = []
    elapsed_seconds = (app["endedNanos"]-app["startedNanos"])/1e9
    for start_second in range(0, math.ceil(elapsed_seconds), 60):
        end_second = min(start_second+60, elapsed_seconds)
        commands = [c for c in app["commands"] if start_second <=
                    (c["commandNanos"]-app["startedNanos"])/1e9 < end_second]
        verified = [row_by_id[c["id"]]["command_to_present_ms"] for c in commands
                    if c["id"] in row_by_id and row_by_id[c["id"]]["command_to_present_ms"] is not None]
        temporal_windows.append({"from_second":start_second,"to_second":end_second,
            "commands":len(commands),
            "command_to_draw_ms":percentile([(c["drawNanos"]-c["commandNanos"])/1e6
                                             for c in commands if c["drawNanos"] is not None]),
            "conditional_command_to_present_ms":percentile(verified),
            "verified_fraction":len(verified)/len(commands) if commands else None})
    out = {
        "method": "Command marker identifies callback; CLOCK_MONOTONIC snapshots anchor the actual callback nanoTime in trace time. Enclosing Choreographer#doFrame exact token -> app SurfaceFrame -> linked SurfaceFlinger DisplayFrame end. No nearest-time frame joins. Known metric-token disagreements excluded. FrameTimeline is OS presentation evidence, not photodiode measurement or touch-hardware latency.",
        "trace": {**metadata, "file":trace_path.name,"bytes": trace_path.stat().st_size, "stats": trace_stats,
                  "sha256": hashlib.file_digest(trace_path.open('rb'), 'sha256').hexdigest()},
        "app_report_file":app_path.name,
        "app_report_sha256": hashlib.file_digest(app_path.open('rb'), 'sha256').hexdigest(),
        "device": {k:app[k] for k in ("deviceModel","sdkInt","packageName","nominalRefreshHzAtStart")},
        "coverage": {"full_app_commands": len(by_id), "traced_commands": len(rows),
            "all_app_commands_in_trace": traced_ids == set(by_id),
            "verified_presentations": len(presented),
            "verified_fraction_of_trace": len(presented)/len(rows),
            "verified_fraction_of_entire_app_run": len(presented)/len(by_id),
            "unverified_commands_in_trace":len(rows)-len(presented),
            "unverified_commands_in_entire_run":len(by_id)-len(presented),
            "unique_verified_surface_frames":len({r["surface_frame_token"] for r in presented}),
            "unique_verified_display_frames":len({r["display_frame_token"] for r in presented}),
            "known_metric_tokens": len(known), "ancestor_tokens_agree_with_known": len(known)-len(mismatches),
            "token_disagreement_command_ids": [r["command_id"] for r in mismatches],
            "first_draw_frame_dropped": sum(r["app_present_type"] == "Dropped Frame" for r in rows),
            "missing_surface_frames": sum(r["matching_app_frames"] == "0" for r in rows),
            "nonpresented_not_assumed_slow_or_fast": True},
        "conditional_command_to_present_ms": percentile([r["command_to_present_ms"] for r in presented]),
        "conditional_trace_marker_to_present_ms": percentile([r["command_marker_to_present_ms"] for r in presented]),
        "clock_alignment": {"source": "clock_snapshot CLOCK_MONOTONIC id3; most recent preceding snapshot, matching Perfetto clock-sync rule",
            "snapshots": len(clocks), "trace_offset_spread_ns": max(c["trace_offset"] for c in clocks)-min(c["trace_offset"] for c in clocks),
            "command_to_trace_marker_ms": {"minimum":min(marker_delays_ms), **percentile(marker_delays_ms)}},
        "verified_latencies_above_50ms": sum(r["command_to_present_ms"] > 50 for r in presented),
        "controls": controls, "active_gesture_cadence": cadence,
        "command_frame_jank":{
            "app_present_types":dict(collections.Counter(str(r["app_present_type"]) for r in rows)),
            "app_jank_labels":dict(collections.Counter(str(r["app_jank_type"]) for r in rows)),
            "display_present_types":dict(collections.Counter(str(r["display_present_type"]) for r in rows)),
            "display_jank_labels":dict(collections.Counter(str(r["display_jank_type"]) for r in rows)),
        },
        "temporal_windows": temporal_windows,
        "trace_marker_minus_api_command_to_draw_delta_ms": {"minimum":min(draw_clock_crosscheck), **percentile(draw_clock_crosscheck)},
        "memory_full_app_run": memory_summary,
        "full_app_run": {k:app[k] for k in ("actualManipulationMillis", "completedSliderGestures", "moveEventsInjected", "survivedProlongedManipulation", "commandToDraw", "commandToAppFrameComplete", "frameCount", "appDeadlineMisses", "deadlineFromSystemFrames", "frameMetricsReportsDropped", "coalescedBeforeDraw", "unmatchedDraws", "commandsPendingDraw")},
        "limitations": [
            "Conditional presentation quantiles exclude explicit drops, missing surfaces and token disagreements; coverage must accompany them.",
            "Only the first draw for each command is instrumented. A dropped first draw may carry its state into a later frame, but that propagation is not proven here.",
            "FrameMetrics timestamp joins can miss or misassociate frames during Android 16 buffer-stuffing recovery; do not use their aggregate as exact presentation evidence.",
            "~60 callbacks/s is distinct from ~60 verified displayed changes/s, especially for size/scene transitions.",
            "A single instrumented S25 run is not a thermal, refresh-mode, GPU-memory or multi-device certification.",
        ],
    }
    with output.with_suffix(".commands.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    output.with_suffix(".json").write_text(json.dumps(out,indent=2)+"\n")
    print(json.dumps({k:out[k] for k in ("coverage","conditional_command_to_present_ms","verified_latencies_above_50ms")},indent=2))


if __name__ == "__main__":
    main()
