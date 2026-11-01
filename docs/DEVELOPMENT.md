# Development

## Layout

```
core/               plain Java, no Android: JSON, profile models and parser, optics, PresetEngine,
                    ValueSeeker, i18n, HUD text — runs on the JVM, tested by tools/test.sh
camera-adapter/
  stubs/            compile-time stubs of Sony's API (CameraEx, ScalarInput, ...): exactly what the
                    adapter calls; never packaged; the contract the firmware check verifies
  src/main/java     SonyCamera (open, preview, listeners, apply, probe), DeviceIdentity;
                    SonyValues / ApplyReport / ProbeReport are Android-free and tested
app/                Android 2.3.7 UI: MainActivity (screens, keys), OverlayView, ListPanel, card
                    storage, preferences
profiles/           cameras/, lenses/, presets/ (bundled into the APK) and schema/
i18n/               en.json, vi.json — every on-screen word
firmware/           sources.json (Sony's download pages, file sha256s) and reports/ (capability facts)
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
the apply report and in the app's log, never a crash.

| setting | call | confidence |
|---|---|---|
| exposure mode | `Camera.Parameters.setSceneMode("aperture-priority" / "shutter-speed" / "manual-exposure" / "program-auto")` | used by BetterManual (A, M); all four checked on ILCE-5100 3.10 |
| aperture, shutter | `increment/decrementAperture()`, `increment/decrementShutterSpeed()`, stepped with feedback from the change listeners (`ValueSeeker` learns the direction) | used by BetterManual; checked on ILCE-5100 3.10 |
| ISO, Auto ISO | `setISOSensitivity(0 = auto)`, `setISOAutoMin/Max` | ISO used by BetterManual; the range calls do not exist on ILCE-5100 3.10 (firmware report) |
| Auto ISO minimum speed | `setAutoShutterSpeedLowLimit(code)`, BetterManual's experimental code table | used by BetterManual; checked on ILCE-5100 3.10 |
| EV | `Camera.Parameters.setExposureCompensation(index)` | used by BetterManual; checked on ILCE-5100 3.10 |
| metering, AF mode / area, face detection, drive, Creative Style, Soft Skin | `ParametersModifier` setters, value checked against the matching `getSupported…()` list first | checked on ILCE-5100 3.10; the A5100 lists AF area `wide` but keeps `center`, so `multi` is used |
| self-timer | as above | read back on ILCE-5100 3.10, only tested off |
| lens | `getLensInfo()` name / id (`LensId` does not exist on ILCE-5100 3.10); `FocalLengthChangeListener` (tenths of a mm on ILCE-5100 3.10; `Matcher.focalMm` still infers ×1/×10/×100 from the lens range for other bodies); `ApertureInfo.currentAvailableMin` as the widest aperture now | name and focal length checked on ILCE-5100 3.10 with a prime; tracking a zoom unverified |
| eye AF | no API, confirmed on ILCE-5100 3.10 (firmware report): the app tells the user to use the camera's own Eye AF button | — |

**Roadmap steps 1–2 (spike and API survey on the A5100)** are what MENU → *Run camera probe*
is for: it writes every supported list, current value, lens report and the full
`Camera.Parameters.flatten()` to `LENSPRST/PROBE.TXT`, changing nothing. Use it with the test
report template to fill in a camera profile's `features`, lens `match` names, and the focal-length
unit.

Open questions carried from the plan: whether the scene mode can be set while a physical mode dial
points elsewhere (A6000), whether settings persist after leaving the app, and option (b) — writing
to the settings store like Recipe Lab — which needs its native backup library.

## Firmware contract pipeline

Run locally; only the report is committed. CI never downloads firmware.

```bash
# once: a checkout of ma1co/fwtool.py (tested at cdba742) and its requirements
pip install pycryptodomex pyyaml

# download the updater from the page in firmware/sources.json, then
FWTOOL=~/src/fwtool.py python3 tools/firmware/extract.py \
    --file Update_ILCE5100V310.exe --model ILCE-5100 --version 3.10
git add firmware/reports/ILCE-5100-3.10.json      # and the sha256 it printed, in sources.json
```

`extract.py` checks the file against the `sha256` in `sources.json` (or prints it to record),
unpacks into `.firmware/extracted/<model>-<version>/` (git-ignored), and runs `contract.py`.
Newer updaters (Sony's "Firmware Updater Starter" 1.1.8, e.g. `Update_ILCE5100V310.exe`) append an
uncompressed file archive fwtool does not know; `tools/firmware/updater.py` takes their
`FirmwareData_*.dat` out first, and fwtool reads that. `fwtool unpack` turns it into `firmware.tar`
and recurses into every filesystem image inside it (`*_unpacked`). `contract.py` compiles the stubs, reads every
`.dex/.odex/.jar/.apk` in the extracted tree with a small DEX parser, and checks each stub class,
method (with inherited members) and constant value. The report holds facts only.

Members listed in `camera-adapter/stubs/optional.txt` may be missing: the adapter catches
`NoSuchMethodError` / `NoSuchFieldError` for them and reports the setting as unsupported. A firmware
without one is recorded under `missing_optional` and still passes. Add a member there only after its
call site copes with the absence.

For models Sony no longer hosts, dump your own body (Sony-PMCA-RE updater mode) and pass the dump
as `--file`.

CI runs `contract.py --check-reports`, which needs no firmware: it fails when a committed report
says a firmware lacks something the app calls, and warns when the stubs call something a report has
not checked yet — re-run `extract.py` for that firmware and commit the new report.

The stubs were cross-checked by hand against OpenMemories-Framework's Jasmin stubs (which were
taken from firmware): every member and constant matches.

## When the app misbehaves on a body

The app's log lives in its own storage (`files/log.txt`); *MENU → Save log to card* copies it to
`LENSPRST/LOG.TXT`. It
traces start-up step by step (`boot +<uptime>ms: <step>`; the camera's clock reads 1970 to apps),
then window focus, the first draw, the first 30 key presses with their scan codes, pause / stop /
destroy, every apply, crashes, and why the lens could not be read. The last line before a hang says
where it is.

`LENSPRST/DEBUG.TXT` leaves start-up steps out without a new build, one switch per line:

| switch | leaves out |
|---|---|
| `no-camera` | opening the camera (no live view, nothing applied) |
| `no-preview` | starting the live view |
| `card-log` | adds, rather than leaves out: every log line is also appended to `LENSPRST/LOG.TXT` as it happens, for a body where the app never gets as far as its menu |
| `adb-control` | adds: the app takes commands from adb (`am broadcast -a com.lenspreset.debug --es cmd apply --es arg <preset id>`; also `probe`, `save-log`, `awake on/off`, `exit`). `tools/camera-test.sh` writes and removes it |

USB always closes the app, and the app never asks to be reopened after power-off (see
`BaseActivity`), so a misbehaving build can be left and replaced.

## The live view activity follows BetterManual

`app/.../BaseActivity.java` and the camera lifecycle in `MainActivity` copy BetterManual, which runs
on the A5100: the layout is inflated from `res/layout/main.xml`; `onResume` sets the framebuffer to
16-bit (RGBA4444), announces the app, opens a `DisplayManager` kept until `onPause`, then opens the
camera, registers the surface callback and starts direct shutter; the preview starts in
`surfaceCreated`; `onPause` removes the callback, stops the preview and releases the camera. The
first builds did all of this differently (32-bit colour, a `DisplayManager` opened and closed at
once, views built in code) and showed only a black screen on an A5100. Change this order only with
a test on a body.

What Sony's own apps on ILCE-5100 3.10 do (read from the firmware's odex files with
`dexdump`; dexopt mangles virtual calls, so only static and direct calls and constants are certain):

- **The framebuffer is a screen-wide setting, 16-bit by default.** Smart Remote (a live view app)
  only ever sets `RGBA4444`, when it leaves its playback thumbnail grid; the Application List
  (`ScalarALauncher`) never sets it and draws in whatever the previous app left. A build that switches
  to 32-bit and exits without switching back (USB, a kill, a hang) leaves the Application List black
  until another app sets 16-bit again — what happened on the A5100. Never use `ABGR8888` with the live
  view, and set `RGBA4444` on both resume and pause.
- `DisplayManager` instances live as long as the app (`HDMIInfoWrapper`, `OLEDBurning`).
- `CameraEx.open` is called on an init thread with an `OpenOptions` (still recording mode, the media
  id); BetterManual's `open(0, null)` on the UI thread works too, and is what this app does.
- `AppInfoReceive` also carries `large_category` (`CATEGORY_REC` for shooting) and `small_category`
  (`STILL`), and per-state `pullingback_key` / `resume_key` lists. BetterManual sends neither
  category; adding them is a candidate once the app runs, not before.
- The Application List's `Mode.BLACK` is its own exit transition (MENU), unrelated to the above.

## Testing on a body in one pass

With OpenMemories-Tweak's adb on (Wi-Fi or USB) and `out/LensPreset.apk` built:

```bash
tools/camera-test.sh 192.168.1.68:5555            # or set CAMERA; about two minutes
tools/camera-test.sh --photos 192.168.1.68:5555   # about three minutes; needs exiftool
tools/camera-test.sh --face 192.168.1.68:5555     # Soft Skin only; needs a face in the frame
# add --skip-install to test the app already on the camera
```

How a setting is known to have taken:

1. **Read back, on every apply.** After writing, the app reads each setting back from the camera
   (`getMeteringMode`, `getAutoFocusMode`, `getColorMode`, …). The log says `read back` when the
   camera holds the value and `MISMATCH … camera reads back X` when it took the call but kept
   something else. "OK" without "read back" only means the call did not throw.
2. **The photo (`--photos`).** One picture per preset; its EXIF and Sony MakerNotes (exposure
   program, f-number, shutter, exposure compensation, metering, focus mode, Creative Style, Soft Skin)
   are compared with the report by `tools/camera-test/exif_check.py`: `photo ok`, `PHOTO MISMATCH`,
   `photo has no …` when the picture does not record a setting, or `photo can't tell` for Soft Skin
   in a picture without a face (the body may record Off then; `--face` asks for one and shoots
   portrait-bokeh only; each try is kept as `portrait-bokeh-try<n>.*`). SoftSkinEffect is
   compared by its low 16 bits: the A5100 also set 0x10000 (65537 for Low) on the one test photo
   with a detected face, and not on photos without one. This is what the camera really shot with.
   The pictures stay on the card; delete them on the camera.
3. **Your eyes**, for what a photo does not record (face frames on the live view).

It installs the APK, puts `DEBUG.TXT` (`adb-control`) and the test presets in `tools/camera-test/`
on the card, starts the app, keeps the camera from sleeping (key presses sent over adb do not count
as activity, so it would power off mid-run), applies every preset by id — shutter stepped both ways,
manual mode setting both — waits for each apply report, runs the probe, then puts everything back.
Results: `out/camera-test/<time>/summary.txt`, `logcat.txt`, `PROBE.TXT`. Every line the app logs
also goes to logcat under the tag `LensPreset`. Against an emulator (`CARD=/sdcard`) it checks the
plumbing only: there is no camera to apply to.

## Running on a desktop emulator

The UI runs in an API 30 emulator: every Sony call fails cleanly ("Camera not available"), so
lists, overlay, languages and lens choice can be checked. A 640×480, 160 dpi AVD matches the
cameras' screens. Keys map from the D-pad, Enter, Menu/Back and Delete.

## Installing on the camera

Camera on, *USB Connection → Mass Storage*, then
`pmca-console install -d native -f out/LensPreset.apk`.
