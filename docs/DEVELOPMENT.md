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
i18n/               en.json, vi.json — UI and generated messages (preset hints live in profiles/)
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
Dev numbering starts at `dev.1` on the commit that changes `versionName` along first-parent
history, then increases with subsequent commits on that history. Each merge counts once;
old-version edits on a merged branch cannot reset the counter. An uncommitted version change
also builds as `dev.1`.
Tags do not reset the counter. Use full Git history for reproducible dev numbers, and bump
`versionName` before starting development after a release: dev builds of the same version rank
below that release. `python3 tools/test_version.py` checks this against temporary Git histories.
For a public build, use a stable project signing key; local debug signing is a development
fallback. See [Releasing](RELEASING.md) for the secret names, certificate check, release workflow,
and publication steps.

## Tests

```bash
./tools/test.sh                    # core + Android-free adapter classes, JUnit 5
./tools/test.sh Engine             # classes whose name contains "Engine"
python tools/validate_profiles.py  # schemas (pip install jsonschema)
python3 tools/test_signing.py       # missing secrets and certificate mismatch guards
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
(roadmap option (a)). Camera calls are guarded so missing methods and refused values can be
reported in the apply report and app log. These guards do not establish compatibility with a
body that has not been tested.

| setting | call | confidence |
|---|---|---|
| exposure mode | `Camera.Parameters.setSceneMode("aperture-priority" / "shutter-speed" / "manual-exposure" / "program-auto")` | used by BetterManual (A, M); all four checked on ILCE-5100 3.10 |
| aperture, shutter | `increment/decrementAperture()`, `increment/decrementShutterSpeed()`, stepped with feedback from the change listeners (`ValueSeeker` learns the direction) | used by BetterManual; checked on ILCE-5100 3.10 |
| ISO, Auto ISO | `setISOSensitivity(0 = auto)`, `setISOAutoMin/Max` | ISO used by BetterManual; the range calls do not exist on ILCE-5100 3.10 (firmware report) |
| Auto ISO minimum speed | `setAutoShutterSpeedLowLimit(code)`, BetterManual's experimental code table | used by BetterManual; checked on ILCE-5100 3.10 |
| EV | `Camera.Parameters.setExposureCompensation(index)` | used by BetterManual; checked on ILCE-5100 3.10 |
| metering, AF mode / area, face detection, drive, Creative Style, Soft Skin | `ParametersModifier` setters, value checked against the matching `getSupported…()` list first | checked on ILCE-5100 3.10; the A5100 lists AF area `wide` but keeps `center`, so `multi` is used |
| self-timer | `setSelfTimer`, `startSelfTimerShutter`, `cancelSelfTimerShutter`; direct shutter stopped while enabled, physical S2 handled by the app | 2 s / 10 s and return to immediate shooting checked on ILCE-5100 3.10 with the physical shutter button |
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

With a nonzero self-timer, group photos use single drive. The app stops direct shutter before the
button is pressed and starts the native self-timer capture on S2, using the same stop-preview and
capture cleanup as shots from code. MENU or Trash cancels a pending shot; a new preset or pause
also cancels it. The watchdog allows the selected delay plus ten seconds for the shot to finish.
Stopping direct shutter also disables native half-press handling. In timed mode, S1_1 instead
starts `setQuickAutoFocus` with a supported AF/AE mode (`af_wiae`, otherwise `af_woaf`); S1_2
is consumed as in PMCADemo. Releasing S1_1 calls `resetQuickAutoFocus`, unless a timed capture is
in progress: release is deferred until capture cleanup. Keeping S1_1 held preserves the lock
across shots; an unsuccessful reset remains pending so later cleanup can retry it. Manual focus
bypasses this. Half-press autofocus followed by a timed shot was checked on ILCE-5100 3.10 with
the Sigma 30mm F1.4. Holding half-press across several shots still needs checking on the body.
Turning the timer off restores direct shutter and the group's burst mode. On ILCE-5100 3.10 with
a Sigma 30mm F1.4, the user confirmed the 2 s / 10 s delay and immediate shooting when disabled;
the log records successful timed shots about 2.8 s / 10.8 s after full press and reads back single
drive with the timer, burst without it. Autofocus performance and cancellation still need testing
on the body. On opening or resuming, the app reapplies the saved preset and its adjustments once
profiles, lens identification, and live view are ready. This includes rearming the self-timer;
the shutter button still starts the shot. A fresh install, or a saved preset that no longer
exists, asks the user to choose a preset first.

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

USB always closes the app. **Reopen after power-on** is off by default; when enabled,
`BaseActivity` sends the Sony `resume_key` list and keeps it across pause for power-off/wake.
Disabling it, explicit `finish()`, or a handled startup failure sends an empty list to clear it.
Explicit exit waits for an ordered broadcast to finish before closing the activity, so Sony
cannot resume it using stale keys while it is releasing the camera. An exiting activity cannot
open the camera again. Resume also restarts preview when the surface already exists; it does
not rely solely on a new `surfaceCreated` callback.
The preference stays enabled after an explicit exit, but the platform registration is cleared
until the user launches the app again. Framebuffer depth remains RGBA4444 in either mode.
The settings defaults, power-on reopening with preset restoration, disabling reopening, explicit
exit with reopening enabled, and manual relaunch with live view were checked on ILCE-5100 3.10.

**Keep current look** defaults to on in app preferences and is passed through `Conditions` to
the engine. The engine returns null look fields in that mode, so the adapter skips those writes
and the HUD does not claim the preset's look was applied. In managed mode, omitted look fields
resolve to Standard / Soft Skin off. EV uses the preset default or its saved wheel adjustment
in both modes; this setting alone does not guarantee an entire Recipe Lab recipe remains unchanged.
The adb test sends `test-look managed` to exercise look writes without editing the user's saved
preference, and `test-look restore` on cleanup. Pause also clears the temporary override. `--face`
requires an OK Soft Skin Low apply report before it will shoot, so a missing look write cannot
silently pass that test.

## The live view activity follows BetterManual

`app/.../BaseActivity.java` and the camera lifecycle in `MainActivity` copy BetterManual, which runs
on the A5100: the layout is inflated from `res/layout/main.xml`; `onResume` sets the framebuffer to
16-bit (RGBA4444), announces the app, opens a `DisplayManager` kept until `onPause`, then opens the
camera, registers the surface callback and starts direct shutter; the preview starts in
`surfaceCreated`, or on resume when its surface already exists; `onPause` removes the callback,
stops the preview and releases the camera. The
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

Page Up / Page Down simulate counter-clockwise / clockwise wheel turns. These use the same
PREV / NEXT actions as the Sony dial scan codes. `adb shell input keyevent` takes Android key
codes, not Sony scan codes: sending `522` does not simulate a Sony wheel turn.

With the app open, test one step in either direction:

```bash
adb -s emulator-5554 shell input keyevent KEYCODE_PAGE_DOWN  # clockwise
adb -s emulator-5554 shell input keyevent KEYCODE_PAGE_UP    # counter-clockwise
```

In live view, Left / Right selects ISO, EV, then the preset's own options; the wheel changes the
highlighted value and the preset must stay unchanged. Check EV in 1/3-stop increments and ISO
through AUTO → 100 → 125 → …, including returning to AUTO. The selected card has a brighter
amber fill and border; on touchscreen bodies, tapping an editable card selects it.

Each preset remembers absolute EV in third stops (`ev.<preset id>`) and ISO (`iso.<preset id>`;
0 is Auto, absent uses the preset's default). Check bounds against the camera profile, switch
presets with Up / Down and return, then restart the app to check restoration. Manual ISO uses
the full body range rather than an Auto ISO ceiling. Fixed ISO in A/P modes removes the Auto ISO
minimum-shutter card and adds a shutter-speed reminder. Returning to Auto restores its range and
minimum-shutter policy. Keep current look does not suppress ISO or EV. Wheel ISO/EV changes use
separate adapter write/read-back paths without restarting focus or drive; full apply/restore
receives both saved values through `Conditions`. Firmware ISO choices are preferred; when the
capability list is missing, standard third stops within the profile's range are used.

For Group photo, select rows and self-timer with Left / Right, then Page Up / Page Down to change
the highlighted value. Verify rows stop at 1 and 4, and timer values are off / 2 s / 10 s. Switch
to a preset without those options: selection should fall back to EV if its old option is missing.
In lists the wheel moves the selection. In preset options, Up / Down chooses a setting and the
wheel (or Left / Right) changes its value. Turning the wheel on the Apply row does nothing.
This verifies app action handling; actual Sony dial events still need a real-camera check.

## Installing on the camera

Camera on, *USB Connection → Mass Storage*, then
`pmca-console install -d native -f out/LensPreset.apk`.
