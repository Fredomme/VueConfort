#!/usr/bin/env python3
"""Compare exported production calculations to independently reconstructed optics."""
import hashlib
import json
from pathlib import Path

import numpy as np

import independent_optical_audit as reference

root = Path(__file__).resolve().parent
generated = root / 'build'
generated.mkdir(exist_ok=True)
exports = generated / 'kotlin'
checks = {'meaning': 'Numerical model validation only; no diagnosis or established human benefit',
          'directPupilPointChecks': json.loads((exports / 'point-checks.json').read_text()),
          'fullGrids': {}, 'cases': {}}


def read_complex(stem):
    re = np.fromfile(exports / f'{stem}-real.f64be', dtype='>f8').reshape(4, 8)
    im = np.fromfile(exports / f'{stem}-imag.f64be', dtype='>f8').reshape(4, 8)
    return re + 1j * im


original = read_complex('fft-input')
expected_fft = np.fft.fft2(original)
checks['rectangularFft'] = {
    'maximumComplexErrorVsNumpy': float(np.max(np.abs(read_complex('fft-output') - expected_fft))),
    'maximumRoundtripError': float(np.max(np.abs(read_complex('fft-roundtrip') - original))),
    'width': 8, 'height': 4,
}

reference_files = {}
for sphere in (.15, .25, .4):
    label = f's{round(sphere * 1000)}-d400'
    path = generated / f'reference-{label}.npz'
    high, preparation = reference.build_grid(sphere, 400., order=128)
    np.savez_compressed(path, h=high, metadata=json.dumps(preparation))
    reference_files[sphere] = path
    produced = np.fromfile(exports / f'transfer-{label}.f64be', dtype='>f8').reshape(high.shape)
    delta = np.abs(produced - high)
    index = np.unravel_index(np.argmax(delta), delta.shape)
    yy = (-np.arange(high.shape[0])) % high.shape[0]
    xx = (-np.arange(high.shape[1])) % high.shape[1]
    checks['fullGrids'][label] = {
        'maximumDifference': float(delta[index]), 'maximumAtYX': [int(index[0]), int(index[1])],
        'dc': float(produced[0, 0]), 'negativeSamples': int(np.count_nonzero(produced < 0.)),
        'minimum': float(produced.min()), 'hermitianMaximumError': float(np.max(np.abs(produced - produced[np.ix_(yy, xx)]))),
        'preparation': preparation,
    }
    if delta.max() > 2e-5:
        raise AssertionError(f'Full-grid discrepancy: {label}')
    for folder in sorted(exports.glob(f'*-{label}')):
        if not folder.is_dir():
            continue
        result = reference.audit_exports(folder, path)
        checks['cases'][folder.name] = result
        print(folder.name, json.dumps({k: result[k] for k in ('flags', 'outputRelativeImprovement',
              'clippingFraction', 'boundaryMaxDifference', 'displayCodeMaximumDifference', 'displayCodesDiffering')}))

# Historical S25 results are in independent-results.json; this run uses synthetic cases only.
source = root.parent / 'app/src/main/java/fr/vueconfort/vision'
checks['engineSourceSha256'] = {name: hashlib.sha256((source / name).read_bytes()).hexdigest()
                               for name in ('OpticalExperiment.kt', 'RectangularFft.kt')}
vectors = generated / 'otf-vectors.json'
if not vectors.exists():
    vectors = root / 'otf-vectors.json'
checks['directPupilReferenceConvergence'] = json.loads(vectors.read_text())['maximumQuadratureChange128to256']
(generated / 'independent-results.json').write_text(json.dumps(checks, indent=2) + '\n')
if checks['rectangularFft']['maximumComplexErrorVsNumpy'] > 1e-10:
    raise AssertionError('Rectangular FFT differs from NumPy')
failures = {name: result['flags'] for name, result in checks['cases'].items() if result['flags']}
if failures:
    raise AssertionError(f'Independent audit flags: {failures}')
print('PASS: independent pupil transfer, rectangular FFT, candidate pixels and output scoring; no human-benefit conclusion.')
