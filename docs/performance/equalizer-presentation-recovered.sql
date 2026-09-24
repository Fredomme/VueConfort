-- EXACT token recovery: VC_EQ_DRAW must be nested in Choreographer#doFrame <token>.
-- A known public FrameMetrics token disagreement is excluded, not silently overwritten.
-- The UI main thread and MainActivity layer must belong to the unique process emitting
-- VC_EQ markers. Android may leave process.name at zygote64; verify PID/layer/package.
-- SurfaceFlinger display slices have NULL surface_frame_token in Perfetto v58.2.
-- SF end is platform-reported presentation, not a photodiode measurement.
-- Public FrameMetrics vsync ID -> app SurfaceFrame token -> SF DisplayFrame token.
-- The END of an app timeline slice is NOT presentation. Only the linked
-- SurfaceFlinger DisplayFrame end is used below. No nearest-time matching.
-- Ref: https://perfetto.dev/docs/data-sources/frametimeline

CREATE PERFETTO TABLE eq_markers AS
SELECT s.id, s.ts, s.name, s.track_id, p.upid
FROM slice s
JOIN thread_track tt ON tt.id = s.track_id
JOIN thread t ON t.utid = tt.utid
JOIN process p ON p.upid = t.upid
WHERE s.name GLOB 'VC_EQ_*';

CREATE PERFETTO TABLE eq_commands AS
SELECT CAST(STR_SPLIT(name, ':', 1) AS INT) AS command_id,
       MIN(ts) AS command_trace_ts, MAX(STR_SPLIT(name, ':', 2)) AS control,
       COUNT(*) AS command_markers
FROM eq_markers WHERE name GLOB 'VC_EQ_CMD:*'
GROUP BY command_id;

CREATE PERFETTO TABLE eq_draw_ancestors AS
SELECT CAST(STR_SPLIT(m.name, ':', 1) AS INT) AS command_id, m.id AS draw_slice_id,
       m.ts AS draw_trace_ts,
       (SELECT CAST(STR_SPLIT(a.name, ' ', 1) AS INT)
        FROM ancestor_slice(m.id) a
        WHERE a.name GLOB 'Choreographer#doFrame [0-9]*'
        ORDER BY a.depth DESC LIMIT 1) AS ancestor_surface_frame_token,
       (SELECT a.name FROM ancestor_slice(m.id) a
        WHERE a.name GLOB 'Choreographer#doFrame [0-9]*'
        ORDER BY a.depth DESC LIMIT 1) AS ancestor_name
FROM eq_markers m WHERE m.name GLOB 'VC_EQ_DRAW:*';

CREATE PERFETTO TABLE eq_draws AS
SELECT command_id, MIN(draw_trace_ts) AS draw_trace_ts, COUNT(*) AS draw_markers,
       MIN(ancestor_surface_frame_token) AS ancestor_surface_frame_token,
       MAX(ancestor_name) AS ancestor_name
FROM eq_draw_ancestors GROUP BY command_id;

CREATE PERFETTO TABLE eq_frame_ids AS
SELECT CAST(STR_SPLIT(name, ':', 1) AS INT) AS command_id,
       MIN(CAST(STR_SPLIT(name, ':', 2) AS INT)) AS surface_frame_token,
       COUNT(*) AS frame_id_markers
FROM eq_markers WHERE name GLOB 'VC_EQ_FRAME:*'
GROUP BY command_id;

CREATE PERFETTO TABLE eq_app_frames AS
SELECT f.* FROM actual_frame_timeline_slice f JOIN process p USING (upid)
WHERE p.upid IN (SELECT DISTINCT upid FROM eq_markers) AND f.layer_name LIKE '%MainActivity%';

CREATE PERFETTO TABLE eq_sf_frames AS
SELECT f.* FROM actual_frame_timeline_slice f JOIN process p USING (upid)
WHERE p.name GLOB '*surfaceflinger' AND (f.surface_frame_token IS NULL OR f.surface_frame_token = 0);

CREATE PERFETTO TABLE eq_joined AS
SELECT c.command_id, c.control, c.command_trace_ts, c.command_markers,
       d.draw_trace_ts, d.draw_markers, d.ancestor_surface_frame_token AS surface_frame_token,
       m.surface_frame_token AS metric_surface_frame_token, m.frame_id_markers, d.ancestor_name,
       COUNT(DISTINCT a.id) AS matching_app_frames,
       COUNT(DISTINCT sf.id) AS matching_display_frames,
       MAX(a.present_type) AS app_present_type, MAX(a.jank_type) AS app_jank_type,
       MAX(sf.present_type) AS display_present_type, MAX(sf.jank_type) AS display_jank_type,
       MAX(a.display_frame_token) AS display_frame_token,
       MAX(a.ts) AS app_start_trace_ts, MAX(a.ts + a.dur) AS app_end_trace_ts,
       MAX(a.dur) AS app_duration_ns, MAX(sf.dur) AS display_duration_ns,
       MAX(sf.ts + sf.dur) AS display_present_trace_ts
FROM eq_commands c
LEFT JOIN eq_draws d USING (command_id)
LEFT JOIN eq_frame_ids m USING (command_id)
LEFT JOIN eq_app_frames a ON a.surface_frame_token = d.ancestor_surface_frame_token
LEFT JOIN eq_sf_frames sf ON sf.display_frame_token = a.display_frame_token
GROUP BY c.command_id;

CREATE PERFETTO TABLE eq_results AS
SELECT *, CASE
  WHEN command_markers = 1 AND draw_markers = 1 AND surface_frame_token IS NOT NULL
   AND (metric_surface_frame_token IS NULL OR metric_surface_frame_token = surface_frame_token)
   AND matching_app_frames = 1 AND matching_display_frames = 1
   AND app_duration_ns >= 0 AND display_duration_ns > 0
   AND app_start_trace_ts <= draw_trace_ts AND app_end_trace_ts >= draw_trace_ts
   AND app_present_type IN ('On-time Present', 'Late Present', 'Early Present')
   AND display_present_type IN ('On-time Present', 'Late Present', 'Early Present')
   AND display_present_trace_ts >= draw_trace_ts
   AND draw_trace_ts >= command_trace_ts
  THEN (display_present_trace_ts - command_trace_ts) / 1000000.0
  ELSE NULL END AS command_marker_to_present_ms
FROM eq_joined;

SELECT * FROM eq_results ORDER BY command_id;
