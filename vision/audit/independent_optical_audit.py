#!/usr/bin/env python3
"""Independent numerical check of the rectangular VueConfort optical experiment.

Reference: direct normalized overlap integral of two continuous circular pupils,
with their separate quadratic phases evaluated in physical pupil coordinates.
Uses Gauss-Legendre quadrature, not the production Simpson substitution. The
spherical radial grid evaluator factors the transverse quadrature (constant
phase in that coordinate), retaining the original longitudinal coordinate.
No pupil raster, sampled-PSF optics, production output, or interpolation is used
as the optical reference. NumPy supplies independent rectangular FFTs.

This checks a declared optical model, not emitted OLED light or human benefit.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import time

import numpy as np
from numpy.polynomial.legendre import leggauss

PITCH_MM = 25.4 / (math.hypot(2340.0, 1080.0) / 6.2)
PUPIL_MM = 3.0
LAMBDA_NM = 555.0
SHOWN_WIDTH, SHOWN_HEIGHT = 768, 256
LOW_WIDTH, LOW_HEIGHT = 1024, 512
HIGH_WIDTH, HIGH_HEIGHT = 2048, 1024
HERE = Path(__file__).resolve().parent


def decode_srgb(a):
    a = np.asarray(a, dtype=np.float64)
    return np.where(a <= .04045, a / 12.92, ((a + .055) / 1.055) ** 2.4)


def codes8(a):
    a = np.clip(np.asarray(a, dtype=np.float64), 0., 1.)
    encoded = np.where(a <= .0031308, 12.92 * a, 1.055 * a ** (1 / 2.4) - .055)
    return np.floor(encoded * 255. + .5).astype(np.uint8)


def quantize(a):
    return decode_srgb(codes8(a).astype(np.float64) / 255.)


EXTERIOR = float(quantize(.5))


def pixels_per_degree(distance_mm):
    return 1. / math.degrees(2. * math.atan(PITCH_MM / (2. * distance_mm)))


def cutoff_cpd(pupil_mm=PUPIL_MM):
    return pupil_mm * 1e-3 / (LAMBDA_NM * 1e-9) * math.pi / 180.


def eye_otf_2d(fx_cpd, fy_cpd, sphere_d, pupil_mm=PUPIL_MM, order=256, transverse_order=12):
    """Direct 2-D overlap-area quadrature of P(r+d/2) conj(P(r-d/2))."""
    radius = pupil_mm * .0005
    wavelength = LAMBDA_NM * 1e-9
    separation = wavelength * math.hypot(fx_cpd, fy_cpd) * 180. / math.pi
    if separation == 0.:
        return 1.
    if separation >= 2. * radius:
        return 0.
    nodes_x, weights_x = leggauss(order)
    nodes_y, weights_y = leggauss(transverse_order)
    half_width = radius - separation / 2.
    positive_x = (nodes_x + 1.) * half_width / 2.
    x = np.concatenate((-positive_x, positive_x))
    wx = np.tile(weights_x * half_width / 2., 2)
    half_height = np.sqrt(np.maximum(0., radius ** 2 - (np.abs(x) + separation / 2.) ** 2))
    xx = x[:, None]
    yy = half_height[:, None] * nodes_y[None, :]
    # Evaluate the two physical pupils separately rather than using production's
    # reduced phase coefficient or its transformed one-dimensional integral.
    wave_a = -.5 * sphere_d * ((xx + separation / 2.) ** 2 + yy ** 2)
    wave_b = -.5 * sphere_d * ((xx - separation / 2.) ** 2 + yy ** 2)
    product = np.exp(2j * np.pi / wavelength * (wave_a - wave_b))
    integral = np.sum(product * wx[:, None] * half_height[:, None] * weights_y[None, :])
    integral /= math.pi * radius ** 2
    if abs(integral.imag) > 1e-12:
        raise AssertionError(f"Spherical autocorrelation acquired imaginary component {integral.imag}")
    return float(integral.real)


def screen_otf_point(qx, qy, sphere_d, distance_mm, order=256):
    ppd = pixels_per_degree(distance_mm)
    cutoff = cutoff_cpd()
    reach = math.ceil(cutoff / ppd + .5)
    result = 0.
    for my in range(-reach, reach + 1):
        for mx in range(-reach, reach + 1):
            ax, ay = qx + mx, qy + my
            if math.hypot(ax, ay) * ppd >= cutoff:
                continue
            weight = float(np.sinc(ax) * np.sinc(ay))
            if abs(weight) < 1e-15:
                continue
            result += weight * eye_otf_2d(ax * ppd, ay * ppd, sphere_d, order=order)
    return result


def write_vectors(destination):
    points = [(0., 0.), (1 / 128., 0.), (1 / 32., 0.), (1 / 16., 0.),
              (.125, 0.), (.25, 0.), (.375, 0.), (.5, 0.), (-.5, 0.),
              (.5, .21875), (-.5, .21875), (.25, .25), (.46875, .46875),
              (0., .375), (-.1875, .09375), (-.25, -.25)]
    vectors = []
    maximum_change = 0.
    for distance in (300., 400., 600.):
        for sphere in (.15, .25, .40):
            for qx, qy in points:
                reference = screen_otf_point(qx, qy, sphere, distance, order=256)
                lower = screen_otf_point(qx, qy, sphere, distance, order=128)
                change = abs(reference - lower)
                maximum_change = max(maximum_change, change)
                vectors.append(dict(distanceMm=distance, sphereD=sphere, pupilMm=3.,
                                    qx=qx, qy=qy, x=int(round(qx * HIGH_WIDTH)) % HIGH_WIDTH,
                                    y=int(round(qy * HIGH_HEIGHT)) % HIGH_HEIGHT,
                                    expected=reference, quadratureChange128to256=change,
                                    tolerance=2e-5))
    content = dict(reference="Direct 2D physical pupil Gauss-Legendre quadrature; full square-pixel alias sum",
                   pixelPitchMm=PITCH_MM, wavelengthNm=LAMBDA_NM,
                   gridWidth=HIGH_WIDTH, gridHeight=HIGH_HEIGHT, exteriorLinear=EXTERIOR,
                   maximumQuadratureChange128to256=maximum_change, vectors=vectors)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(content, indent=2) + "\n")
    columns = ("distanceMm", "sphereD", "x", "y", "expected", "tolerance")
    destination.with_suffix(".tsv").write_text("\t".join(columns) + "\n" +
        "".join("\t".join(str(row[key]) for key in columns) + "\n" for row in vectors))
    return content


def radial_values(frequencies_cpd, sphere_d, order=128, chunk_size=4096):
    """Factor the transverse integration for a spherical pupil, without interpolation."""
    frequencies_cpd = np.asarray(frequencies_cpd, dtype=np.float64)
    out = np.zeros_like(frequencies_cpd)
    radius = PUPIL_MM * .0005
    wavelength = LAMBDA_NM * 1e-9
    separation = wavelength * frequencies_cpd * 180. / math.pi
    nodes, weights = leggauss(order)
    unit_nodes, unit_weights = (nodes + 1.) / 2., weights / 2.
    valid_indices = np.flatnonzero((separation > 0.) & (separation < 2. * radius))
    for begin in range(0, valid_indices.size, chunk_size):
        ids = valid_indices[begin:begin + chunk_size]
        d = separation[ids, None]
        span = radius - d / 2.
        x = span * unit_nodes[None, :]
        half_height = np.sqrt(np.maximum(0., radius ** 2 - (x + d / 2.) ** 2))
        # Two x half-domains and transverse interval [-half_height,+half_height].
        phase_difference = -2. * np.pi / wavelength * sphere_d * d * x
        out[ids] = (4. / (np.pi * radius ** 2) *
                    np.sum(unit_weights[None, :] * span * half_height * np.cos(phase_difference), axis=1))
    out[separation == 0.] = 1.
    return out


def build_grid(sphere_d, distance_mm, width=HIGH_WIDTH, height=HIGH_HEIGHT, order=128):
    if width % height or width < height:
        raise ValueError("This independent spherical lattice implementation requires width to be a multiple of height")
    if sphere_d not in (.15, .25, .4) or not 300. <= distance_mm <= 600.:
        raise ValueError("Outside this audit's explicitly selected spherical hypotheses")
    started = time.perf_counter()
    ppd = pixels_per_degree(distance_mm)
    cutoff = cutoff_cpd()
    ratio = width // height
    radius_lattice = math.ceil(cutoff / ppd * width)
    xs = np.arange(radius_lattice + 1, dtype=np.int64)
    ys = np.arange(0, radius_lattice + 1, ratio, dtype=np.int64)
    radius_squared = ys[:, None] ** 2 + xs[None, :] ** 2
    keep = radius_squared < (cutoff / ppd * width) ** 2
    radial_keys = np.unique(radius_squared[keep])
    del radius_squared, keep
    h_radial = radial_values(np.sqrt(radial_keys) / width * ppd, sphere_d, order=order)
    # Dense integer-radius lookup avoids interpolation and floating cache keys.
    lookup = np.zeros(radius_lattice * radius_lattice + 1, dtype=np.float64)
    lookup[radial_keys] = h_radial
    qx = np.fft.fftfreq(width)
    qy = np.fft.fftfreq(height)
    ix = np.rint(qx * width).astype(np.int64)
    iy = np.rint(qy * width).astype(np.int64)
    h = np.zeros((height, width), dtype=np.float64)
    reach = math.ceil(cutoff / ppd + .5)
    cutoff_squared = (cutoff / ppd * width) ** 2
    for my in range(-reach, reach + 1):
        for mx in range(-reach, reach + 1):
            xx, yy = ix + mx * width, iy + my * width
            r2 = yy[:, None] ** 2 + xx[None, :] ** 2
            valid = r2 < cutoff_squared
            if not np.any(valid):
                continue
            v = np.zeros_like(h)
            v[valid] = lookup[r2[valid]]
            h += v * (np.sinc(qy + my)[:, None] * np.sinc(qx + mx)[None, :])
    return h, dict(seconds=time.perf_counter() - started, quadratureOrder=order,
                   radialSamples=int(radial_keys.size), distanceMm=distance_mm,
                   sphereD=sphere_d, pupilMm=PUPIL_MM, width=width, height=height)


def embed(shown, height, width):
    shown = np.asarray(shown, dtype=np.float64)
    if shown.shape != (SHOWN_HEIGHT, SHOWN_WIDTH):
        raise ValueError(f"Unexpected shown raster {shown.shape}")
    if height < shown.shape[0] or width < shown.shape[1]:
        raise ValueError("FFT field smaller than the displayed raster")
    y, x = (height - shown.shape[0]) // 2, (width - shown.shape[1]) // 2
    field = np.full((height, width), EXTERIOR, dtype=np.float64)
    field[y:y + shown.shape[0], x:x + shown.shape[1]] = shown
    return field, (slice(y, y + shown.shape[0]), slice(x, x + shown.shape[1]))


def forward(shown, transfer):
    field, region = embed(shown, *transfer.shape)
    retinal = np.fft.ifft2(np.fft.fft2(field) * transfer)
    if np.max(np.abs(retinal.imag)) > 1e-10:
        raise AssertionError("Non-real forward: Hermitian symmetry/frequency indexing error")
    return retinal.real[region].copy()


def inverse_candidate(target, transfer):
    field, region = embed(target, *transfer.shape)
    inverse = np.clip(transfer / (transfer ** 2 + .03), -1.1, 1.1)
    inverse[np.abs(transfer) < .02] = 1.
    inverse[0, 0] = 1.
    raw = np.fft.ifft2(np.fft.fft2(field) * inverse).real[region].copy()
    return raw


def read_argb(path):
    argb = np.fromfile(path, dtype=">u4")
    if argb.size != SHOWN_WIDTH * SHOWN_HEIGHT:
        raise ValueError(f"Unexpected ARGB length in {path}: {argb.size}")
    red, green, blue = (argb >> 16) & 255, (argb >> 8) & 255, argb & 255
    if np.any(red != green) or np.any(red != blue):
        raise AssertionError("Exported optical raster is not monochrome")
    return decode_srgb(red.astype(np.float64).reshape(SHOWN_HEIGHT, SHOWN_WIDTH) / 255.)


def audit_exports(folder, reference_grid=None):
    metadata = json.loads((folder / "metadata.json").read_text())
    if reference_grid is None:
        high, preparation = build_grid(float(metadata["sphereD"]), float(metadata["distanceMm"]))
    else:
        with np.load(reference_grid) as stored:
            high = stored["h"]
            preparation = json.loads(str(stored["metadata"]))
        if float(preparation["sphereD"]) != float(metadata["sphereD"]) or float(preparation["distanceMm"]) != float(metadata["distanceMm"]):
            raise ValueError("Reference grid profile/geometry mismatch")
    low = high[::2, ::2]
    target = read_argb(folder / "baseline.argb.i32be")
    shown = read_argb(folder / "candidate.argb.i32be")
    began = time.perf_counter()
    normal_low, normal_high = forward(target, low), forward(target, high)
    candidate_low, candidate_high = forward(shown, low), forward(shown, high)
    raw_proposal = inverse_candidate(target, low)
    clipping = float(np.mean((raw_proposal < 0.) | (raw_proposal > 1.)))
    proposed = quantize(np.clip(raw_proposal, 0., 1.))
    proposed_low, proposed_high = forward(proposed, low), forward(proposed, high)
    before = float(np.mean((normal_high - target) ** 2))
    after = float(np.mean((candidate_high - target) ** 2))
    proposed_mse = float(np.mean((proposed_high - target) ** 2))
    boundary = max(float(np.max(np.abs(normal_high - normal_low))),
                   float(np.max(np.abs(proposed_high - proposed_low))))
    results = dict(mseNormal=before, mseOutput=after, boundaryMaxDifference=boundary,
                   mseCandidate=proposed_mse, clippingFraction=clipping,
                   relativeImprovement=(before - proposed_mse) / max(before, 1e-15),
                   outputRelativeImprovement=(before - after) / max(before, 1e-15),
                   outputBoundaryMaxDifference=max(float(np.max(np.abs(normal_high - normal_low))),
                       float(np.max(np.abs(candidate_high - candidate_low)))),
                   forwardCheckSeconds=time.perf_counter() - began, preparation=preparation,
                   exteriorCode=int(codes8(.5)), exteriorLinear=EXTERIOR,
                   candidateIsIdentity=bool(np.array_equal(target, shown)),
                   interpretation="Independent model checks only; no human benefit or physical photometry established")
    differences = {}
    for name in ("mseNormal", "mseOutput", "mseCandidate", "clippingFraction", "boundaryMaxDifference", "relativeImprovement"):
        if name in metadata.get("metrics", {}):
            differences[name] = abs(float(metadata["metrics"][name]) - results[name])
    results["metricDifferences"] = differences
    transfer_file = folder / "transfer.f64be"
    if transfer_file.exists():
        engine_h = np.fromfile(transfer_file, dtype=">f8").reshape(high.shape)
        delta = np.abs(engine_h - high)
        index = np.unravel_index(np.argmax(delta), delta.shape)
        results["transferMaximumDifference"] = float(delta[index])
        results["transferMaximumAtYX"] = [int(index[0]), int(index[1])]
        results["dc"] = float(engine_h[0, 0])
        results["negativeTransferSamples"] = int(np.sum(engine_h < 0.))
    flags = []
    if results.get("transferMaximumDifference", 0.) > 2e-5:
        flags.append("TRANSFER_DISAGREEMENT")
    if differences.get("mseNormal", 0.) > 2e-6 or differences.get("mseOutput", 0.) > 2e-6:
        flags.append("MSE_DISAGREEMENT")
    if boundary > .0015:
        flags.append("FINITE_SUPPORT_NOT_CONVERGED")
    if metadata.get("applied") and results["relativeImprovement"] < .009:
        flags.append("APPLIED_CANDIDATE_LACKS_INDEPENDENT_GAIN")
    expected_apply = (clipping <= .01 and boundary <= .0015 and results["relativeImprovement"] >= .01)
    if bool(metadata.get("applied")) != expected_apply:
        flags.append("DECISION_DISAGREEMENT")
    expected_display = proposed if expected_apply else target
    code_difference = np.abs(codes8(expected_display).astype(np.int16) - codes8(shown).astype(np.int16))
    results["displayCodeMaximumDifference"] = int(code_difference.max())
    results["displayCodesDiffering"] = int(np.count_nonzero(code_difference))
    if code_difference.max() > 1:
        flags.append("DISPLAY_RASTER_DISAGREEMENT")
    results["flags"] = flags
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    vectors = sub.add_parser("vectors")
    vectors.add_argument("--out", type=Path, default=HERE / "build" / "otf-vectors.json")
    grid = sub.add_parser("grid")
    grid.add_argument("--sphere", type=float, required=True)
    grid.add_argument("--distance", type=float, default=400.)
    grid.add_argument("--order", type=int, default=128)
    grid.add_argument("--out", type=Path, required=True)
    audit = sub.add_parser("audit")
    audit.add_argument("folder", type=Path)
    audit.add_argument("--reference-grid", type=Path)
    audit.add_argument("--out", type=Path)
    args = parser.parse_args()
    if args.command == "vectors":
        result = write_vectors(args.out)
        print(json.dumps(dict(path=str(args.out), count=len(result["vectors"]),
                              maximumQuadratureChange128to256=result["maximumQuadratureChange128to256"]), indent=2))
    elif args.command == "grid":
        h, metadata = build_grid(args.sphere, args.distance, order=args.order)
        np.savez_compressed(args.out, h=h, metadata=json.dumps(metadata))
        print(json.dumps(metadata, indent=2))
    else:
        results = audit_exports(args.folder, args.reference_grid)
        serialized = json.dumps(results, indent=2) + "\n"
        if args.out:
            args.out.write_text(serialized)
        print(serialized)


if __name__ == "__main__":
    main()
