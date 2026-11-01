# LensPreset

A PlayMemories Camera App for Sony bodies that run OpenMemories / Sony-PMCA-RE apps. Pick a
**shooting situation** — group photo, portrait with a blurred background, kids on the move, low
light — and the app works out exposure, focus and drive settings **for the body and the lens on
it**, sets them, and shows framing and distance hints over the live view.

Presets are rules, not fixed numbers: "widest aperture, a little stopped down", "enough depth of
field for three rows of people", "the slowest speed you can hand-hold with this lens". The same
preset works on a 16–50 kit zoom and on a Sigma 30 mm f/1.4.

> **Status: early, and not yet tested on a camera.** The engine, profiles and build are covered by
> tests; everything that talks to the camera still needs a real body. The first target is the
> **A5100**. If you try it, please send a [camera test report](../../issues/new?template=compatibility_report.yml).

> **Disclaimer.** Unofficial software, not made or endorsed by Sony. Installing apps through
> Sony-PMCA-RE and running them is at your own risk.

## Presets

| Situation | What it does |
|---|---|
| Group photo | Aperture priority, aperture from the depth of field the rows need (adjustable 1–4 rows), ≥1/125, face detection, burst, optional self-timer |
| Portrait, blurred background | Widest aperture two-thirds of a stop closed (Sigma 30 f/1.4 → f/1.8), eye AF when the body has it, Portrait style |
| Portrait with scenery | The lens's sharpest apertures, face AF, rule-of-thirds and horizon guides |
| Backlight / silhouette | Spot metering, −1.7 EV |
| High-key | +1.3 EV, Light style |
| Kids on the move | Shutter priority 1/500, continuous AF, fast burst, Auto ISO |
| Indoors, low light | Widest aperture, the slowest hand-holdable speed (stabilisation-aware), Auto ISO to the body's ceiling |

Each preset also tells you where to stand ("about 2.0 m for a half-body shot") and what will be
sharp ("1.8 m to 2.1 m"). Add your own presets on the memory card — see [docs/PROFILES.md](docs/PROFILES.md).

## Compatibility

| Body | Profile | Status |
|---|---|---|
| A5100 (ILCE-5100) | `ilce-5100` | untested |
| A6000 (ILCE-6000) | `ilce-6000` | untested |
| any other PMCA body | `generic` (APS-C assumed) | untested |

Bodies newer than the A6500 / A99 II have no PlayMemories app support and cannot run this app.
Lenses: Sony E PZ 16-50mm F3.5-5.6 OSS, Sigma 30mm F1.4 DC DN, and any manual lens (the app
tells you where to turn the aperture and focus rings).

## Install

1. Download `LensPreset-<version>.apk` from [Releases](../../releases) (the `dev` prerelease is
   built from every change).
2. Camera: *Setup → USB Connection → Mass Storage*, connect USB.
3. Install with [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE):
   `pmca-console install -d native -f LensPreset-<version>.apk`, or PMCA-GUI → *Install app*.
4. Open it from the camera's *Application List*.

To remove it, use the camera's application management, or reinstall the camera's apps with
Sony-PMCA-RE. An APK signed with a different key cannot be installed over an existing one:
remove the old one first. Installation problems belong in the
[Sony-PMCA-RE issues](https://github.com/ma1co/Sony-PMCA-RE/issues); preset and app problems here.

## Using it

| Key | Live view | Lists |
|---|---|---|
| Up / Down, wheel | previous / next situation (applied at once) | move |
| Left / Right | change the situation's first setting (e.g. rows) | change a value |
| Centre | list of situations | select |
| MENU | app menu: lens, language, camera probe, reload, about, exit | back |
| Trash | hide / show hints | back |

On bodies with a touchscreen, tapping the live view opens the list. Shoot with the shutter button
as usual; settings apply while the app is open.

**The memory card** (`LENSPRST/`): `CAMERAS/`, `LENSES/`, `PRESETS/` for your own profiles
(8.3 file names such as `NIGHT.JSN`), `PROBE.TXT` from *Run camera probe*, `LOG.TXT` with what
each apply did and any crash.

Languages: English, Tiếng Việt. Translations are welcome.

## Building

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). In short: `./tools/test.sh` (JDK 17+ only) and
`./build.sh` (Android SDK with `platforms;android-10` and `build-tools;35.0.0`). No Gradle.

## Contributing

Camera test reports, lens profiles, presets and translations: [CONTRIBUTING.md](CONTRIBUTING.md).

## License

GPL-3.0 — see [LICENSE](LICENSE). Third-party notices: [NOTICE.md](NOTICE.md).
