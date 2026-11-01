# Development

## Layout

```
core/               plain Java, no Android: JSON, profile models and parser, optics, PresetEngine,
                    ValueSeeker, i18n, HUD text — runs on the JVM, tested by tools/test.sh
camera-adapter/
  stubs/            compile-time stubs of Sony's API (CameraEx, ScalarInput, ...): exactly what the
                    adapter calls; never packaged; the contract the firmware check verifies
  src/main/java     SonyCamera (open, preview, listeners, apply, probe), SystemHooks,
                    DeviceIdentity; SonyValues / ApplyReport / ProbeReport are Android-free and tested
app/                Android 2.3.7 UI: MainActivity (screens, keys), OverlayView, ListPanel, card
                    storage, preferences
profiles/           cameras/, lenses/, presets/ (bundled into the APK) and schema/
i18n/               en.json, vi.json — every on-screen word
firmware/           sources.json (pointers to Sony's downloads) and reports/ (capability facts)
tools/              test.sh, version.sh, validate_profiles.py, make_icon.py, firmware/
```

The dependency direction is app → camera-adapter → core. Logic that needs no camera goes into
`core` (or an Android-free adapter class listed in `ADAPTER_PURE` in `tools/test.sh`) with a test.

## Building

Needs a JDK 17+ and the Android SDK packages `platforms;android-10` and `build-tools;35.0.0`:

```bash
sdkmanager "platforms;android-10" "build-tools;35.0.0"
ANDROID_SDK=~/Library/Android/sdk ./build.sh       # out/LensPreset.apk, X.Y.Z-dev.N
RELEASE=1 ./build.sh                               # X.Y.Z
```

No Gradle: aapt, javac, d8, zipalign, apksigner (v1 signature only — the camera does not read
v2/v3). Everything is compiled against the **API 10** platform jar with `-source 8 -target 8`, so a
library call Android 2.3 lacks (`Objects`, `String.join`, try-with-resources, lambdas) fails the
build. The d8 in build-tools 34.0.0 crashes on this code; use 35.0.0 or later.

Versions: `app/AndroidManifest.xml` `versionName` is the only one in the tree; `tools/version.sh`
derives the dev suffix and versionCode (`X·10⁷ + Y·10⁵ + Z·10³ + N`, `N = 999` for a release).

## Tests

```bash
./tools/test.sh                    # core + Android-free adapter classes, JUnit 5
./tools/test.sh Engine             # classes whose name contains "Engine"
python tools/validate_profiles.py  # schemas (pip install jsonschema)
ANDROID_SDK=... python -m unittest discover -s tools/firmware -p 'test_*.py'
```

What they pin down: every bundled camera × lens × preset at both ends of each zoom and every
`adjust` value stays inside the body's and lens's limits and on the camera's third-stop scales;
the engine's strategies and fallbacks; profile parsing and its error messages; every i18n key the
code uses exists in every language with the same placeholders; the Sony value mappings; the
firmware DEX reader and contract check.

What they cannot: anything the camera does. Whether a setting takes, survives, or behaves as named
needs a real body — say so in a PR rather than implying a green build means it works.

## The camera API, and what is still unverified

The adapter uses Sony's `CameraEx` as BetterManual does, applying settings while the app is open
(roadmap option (a)). Every call is wrapped; a missing method or refused value becomes a line in
the apply report and in `LOG.TXT`, never a crash.

| setting | call | confidence |
|---|---|---|
| exposure mode | `Camera.Parameters.setSceneMode("aperture-priority" / "shutter-speed" / "manual-exposure" / "program-auto")` | used by BetterManual (A, M) |
| aperture, shutter | `increment/decrementAperture()`, `increment/decrementShutterSpeed()`, stepped with feedback from the change listeners (`ValueSeeker` learns the direction) | used by BetterManual |
| ISO, Auto ISO | `setISOSensitivity(0 = auto)`, `setISOAutoMin/Max` | ISO used by BetterManual; range unverified |
| Auto ISO minimum speed | `setAutoShutterSpeedLowLimit(code)`, BetterManual's experimental code table | used by BetterManual |
| EV | `Camera.Parameters.setExposureCompensation(index)` | used by BetterManual |
| metering, AF mode / area, face detection, drive, self-timer, Creative Style, Soft Skin | `ParametersModifier` setters, value checked against the matching `getSupported…()` list first | unverified |
| lens | `getLensInfo()` name / id; `FocalLengthChangeListener` (unit unknown: `Matcher.focalMm` infers ×1/×10/×100 from the lens range); `ApertureInfo.currentAvailableMin` as the widest aperture now | unverified |
| eye AF | no API found in the stubs: the app tells the user to use the camera's own Eye AF button | — |

**Roadmap steps 1–2 (spike and API survey on the A5100)** are what MENU → *Run camera probe*
is for: it writes every supported list, current value, lens report and the full
`Camera.Parameters.flatten()` to `LENSPRST/PROBE.TXT`, changing nothing. Use it with the test
report template to fill in a camera profile's `features`, lens `match` names, and the focal-length
unit.

Open questions carried from the plan: whether the scene mode can be set while a physical mode dial
points elsewhere (A6000), whether settings persist after leaving the app, and option (b) — writing
to the settings store like Recipe Lab — which needs its native backup library.

## Firmware contract pipeline

```
firmware/sources.json  ->  tools/firmware/fetch.py    .firmware/downloads/  (sha256-checked)
                       ->  tools/firmware/extract.py  .firmware/extracted/<model>-<version>/
                                                      (ma1co/fwtool.py; set FWTOOL to a checkout)
                       ->  tools/firmware/contract.py firmware/reports/<model>-<version>.json
```

`fwtool unpack` turns the Windows updater `.exe` into `firmware.tar` and recurses into every
filesystem image inside it (`*_unpacked`). `contract.py` compiles the stubs, reads every
`.dex/.odex/.jar/.apk` in the extracted tree with a small DEX parser, and checks each stub class,
method (with inherited members) and constant value. The report holds facts only; it is committed,
the firmware never is. A source without `url` and `sha256` is skipped. For models Sony no longer
hosts, a contributor can dump their own body (Sony-PMCA-RE updater mode) and run
`extract.py --dump dump.dat --model … --version …` locally, sending only the report.

The stubs were cross-checked by hand against OpenMemories-Framework's Jasmin stubs (which were
taken from firmware): every member and constant matches.

## Running on a desktop emulator

The UI runs in an API 30 emulator: every Sony call fails cleanly ("Camera not available"), so
lists, overlay, languages and lens choice can be checked. A 640×480, 160 dpi AVD matches the
cameras' screens. Keys map from the D-pad, Enter, Menu/Back and Delete.

## Installing on the camera

Camera on, *USB Connection → Mass Storage*, then
`pmca-console install -d native -f out/LensPreset.apk`.
