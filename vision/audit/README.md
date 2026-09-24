# Independent rectangular optical audit

The recorded VueConfort Vision 0.2 checks passed within the numerical model below. They do **not** establish a diagnosis, emitted-light accuracy, or improvement in human vision. The audit compiles the application's actual `OpticalExperiment.kt` and `RectangularFft.kt`; it does not maintain a separate copy of that engine.

## Reproduce the synthetic checks

Requirements: JDK 17 or newer (`JAVA_HOME` or Java on `PATH`), Python 3.10 or newer, and NumPy. An Android SDK and a separately installed Kotlin compiler are not required. The checked-in Gradle wrapper obtains Gradle and Kotlin 2.1.20 dependencies on the first run; network access is needed unless they are already cached.

From this directory, on macOS/Linux:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python independent_optical_audit.py vectors
.venv/bin/python run_harness.py
.venv/bin/python run_independent_checks.py
```

On Windows, create the environment with `py -m venv .venv` and use `.venv\Scripts\python.exe` for the remaining commands. `run_harness.py` selects the Windows Gradle wrapper automatically. `--offline` can be passed to the harness only after the required dependencies have been downloaded.

Alternatively, export the Kotlin results directly with `../gradlew -p . runHarness` (Windows: `..\gradlew.bat -p . runHarness`). The standalone Gradle project in this directory resolves the compiler dependencies and uses the current Java runtime. It does not configure the Android application build.

The vectors command regenerates both JSON and TSV references in `build/`. The harness uses those fresh vectors when present, otherwise the recorded TSV. It exports nine transfer-point profiles, three full grids, FFT examples, and eight raster cases to `build/kotlin/`. The Python check independently recomputes the grids and display codes, then writes `build/independent-results.json`; a failed tolerance or decision check exits with an error. Generated outputs stay under ignored `build/` and do not overwrite the recorded results. Allow roughly 1 GB of memory for the JVM plus memory for NumPy grids; duration depends on the machine.

## Model and independent method

The displayed image is 768 × 256 physical pixels in grayscale. Inversion and the smaller forward calculation use 1024 × 512; the larger forward calculation uses 2048 × 1024. The fixed assumed circular pupil is 3 mm, wavelength is 555 nm, and nominal Samsung S25 pixel pitch is 0.061104890543218424 mm. The spherical hypotheses are +0.15, +0.25, and +0.40 D, with zero cylinder. They are simulation inputs, not prescriptions.

The independent reference evaluates the two pupil phases separately and integrates their overlap with two-dimensional Gauss–Legendre quadrature. It does not copy the production Simpson integration or its variable substitution. Its full-grid spherical reference factors the transverse integral, reuses exactly equal integer squared radii, and sums every square-pixel aperture alias within the optical cutoff without OTF interpolation. NumPy supplies the independent rectangular FFTs.

The target and proposed output are cropped, clipped, and quantized before the forward check. The exterior is the actually encoded gray, sRGB code 188 (linear luminance 0.5028864580325687). Scoring distinguishes an attempted candidate from the final unchanged original when the candidate fails its gate.

## Recorded 0.2 numerical results

- 144 independent transfer points over all three hypotheses at 300, 400, and 600 mm: maximum engine/reference discrepancy 1.091 × 10⁻⁸, against a 2 × 10⁻⁵ tolerance. Changing independent quadrature from 128 to 256 nodes changes the reference by at most 7.547 × 10⁻⁸.
- Three full 2048 × 1024 transfer grids at 400 mm: maximum discrepancy 8.736 × 10⁻⁸; DC is 1 and Hermitian discrepancy is zero. The +0.40 D grid retains 1,528,650 negative samples. No disagreement occurs at the inverse's `abs(H) < 0.02` mask.
- Complex rectangular 8 × 4 FFT: maximum discrepancy against NumPy 1.987 × 10⁻¹⁵; inverse round-trip error 2.776 × 10⁻¹⁶.
- Eight complete output cases at 400 mm: independent apply/identity decisions agree; maximum normal/output MSE discrepancy is 1.453 × 10⁻⁹. Six rasters match every code; the bars and +0.40 D moderate cases differ at four and two pixels respectively by one 8-bit code.
- The black/white case correctly returns the original because the attempted inverse clips 94.885% of pixels, above the 1% gate. Constant gray returns the original because it has no modeled improvement.

`independent-results.json` contains those historical results and the SHA-256 hashes of the two tested engine files. Timings may differ between runs. `otf-vectors.json` and `otf-vectors.tsv` contain the independent scalar references.

## Historical phone checks and finite gray field

The recorded JSON also includes a separate synthetic S25 capture for hypothesis A at 400 mm. Its output matches the independent raster at every pixel. Its modeled MSE reduction is 24.5449469%, with no clipping and low/high forward discrepancy of 3.408 × 10⁻⁵. The first phone computation took about 3.91 seconds, including 2.83 seconds of kernel preparation. These figures describe a static experimental calculation, not a live frame rate or human benefit.

The phone capture and screen comparison are historical device checks and are **not** regenerated by the portable commands above. Raw device captures are not included. Screen-code and capture-pipeline checks are summarized in the project's validation documentation.

`gray-guard-sensitivity.json` records a separate sensitivity calculation for the physical field's 156-pixel horizontal and 128-pixel vertical margins. Its conservative bound is about 0.000502 linear luminance for arbitrary outside-field content in [0,1], within the finite high-grid model, at 400 mm for the three profiles. This recorded calculation is not rerun by the commands above and does not prove an infinite gray exterior or actual OLED behavior.

## Limits

The pupil, working distance, pixel aperture, monochromatic emission, and individual eye remain assumptions. Full-grid and image cases here use 400 mm; 300 and 600 mm have independent transfer-point coverage. Cylinder, color, other pupil diameters, and arbitrary residuals outside the selected hypotheses are not qualified by this audit. No personal vision profile or subjective rating is included. A numerically correct optical inverse does not demonstrate that a person reads better or that an ordinary display replaces glasses.
