# LensPreset

A PlayMemories Camera App for Sony bodies that run OpenMemories / Sony-PMCA-RE apps. Pick a
**shooting situation** — group photo, portrait with a blurred background, kids on the move, low
light — and the app works out exposure, focus and drive settings **for the body and the lens on
it**, sets them, and shows framing and distance hints over the live view.

Presets are rules, not fixed numbers: "widest aperture, a little stopped down", "enough depth of
field for three rows of people", "the slowest speed you can hand-hold with this lens". The same
preset works on a 16–50 kit zoom and on a Sigma 30 mm f/1.4.

> **Status: MVP, community-tested on a Sony A5100 (ILCE-5100), firmware 3.10, with a Sigma 30mm
> F1.4 DC DN.** Tested by [@alfrededison](https://github.com/alfrededison). Other bodies and lens
> combinations still need real-camera tests. Please send a
> [camera test report](../../issues/new?template=compatibility_report.yml) before claiming support
> for another combination.

> **Disclaimer.** Unofficial software, not made or endorsed by Sony. Installing apps through
> Sony-PMCA-RE and running them is at your own risk.

Current release: [v1.1.0](https://github.com/alfrededison/LensPreset-sony-pmca/releases/tag/v1.1.0).
This version adds live-view ISO/EV adjustment, wheel selection of preset options, and a card-based
settings display.

## Presets

| Situation | What it does |
|---|---|
| Group photo | Aperture priority, aperture from the depth of field the rows need (adjustable 1–4 rows), ≥1/125, face detection; burst without a timer, single shots with a 2 s / 10 s timer |
| Portrait, blurred background | Widest aperture two-thirds of a stop closed (Sigma 30 f/1.4 → target f/1.8), face AF, guidance for the camera's Eye AF button; optional Portrait style and Soft Skin Low |
| Portrait with scenery | The lens's sharpest apertures, face AF, rule-of-thirds and horizon guides |
| Beach scenery | Lens sweet spot, f/5.6–f/8 target, Auto ISO minimum 1/250, +1/3 EV for bright sand; check sand and cloud highlights |
| Portrait by the sea | Lens sweet spot, f/4–f/5.6 target, Auto ISO minimum 1/250, face AF, +1/3 EV; soft-light and horizon hints |
| Portrait in a garden | Wide aperture targeting f/2–f/4 within the lens's limits, Auto ISO minimum 1/250, face AF; even shade and distant foliage |
| Forest, garden and mountains | Lens sweet spot, f/5.6–f/8 target, Auto ISO minimum 1/125, −1/3 EV; focus and sunlit-leaf hints |
| Backlight / silhouette | Spot metering, −1.7 EV |
| High-key | +1.3 EV; optional Light style, falling back to Standard when unavailable |
| Kids on the move | Shutter priority 1/500, continuous AF, fast burst, Auto ISO |
| Kids splashing in the pool | Shutter priority 1/1000, continuous AF, fast burst, Auto ISO, −1/3 EV; use Kids on the move for dimmer pools |
| Indoors, low light | Widest aperture, the slowest hand-holdable speed (stabilisation-aware), Auto ISO |

Creative Style and Soft Skin are applied only when **Keep current look** is off. Presets with
subject framing also estimate where to stand ("about 2.0 m for a half-body shot") and depth of
field ("1.8 m to 2.1 m"); these are guides, not measured subject distances. Add your own presets
on the memory card — see [docs/PROFILES.md](docs/PROFILES.md).
All bundled presets carry their own translated hints in `hint_text`, so a single file on the card
contains their settings and guidance without changes to the app's language tables. Every id in
`hints` must have text in that preset's `hint_text`.

## Compatibility

| Body | Profile | Status |
|---|---|---|
| A5100 (ILCE-5100) | `ilce-5100` | community-tested (firmware 3.10) |
| A6000 (ILCE-6000) | `ilce-6000` | untested |
| any other PMCA body | `generic` (APS-C assumed) | untested |

Bodies newer than the A6500 / A99 II have no PlayMemories app support and cannot run this app.
Bundled lens profiles: Sony E PZ 16-50mm F3.5-5.6 OSS, Sigma 30mm F1.4 DC DN, and a manual
50mm F1.8 example. The Sigma 30mm is tested on the A5100; the kit zoom, zoom tracking, and manual
lens workflow remain untested on hardware. Other manual lenses need their own profile values.

Adding JSON is enough when the existing adapter supports the body's API and the preset's
settings. A different API or camera behavior can require adapter changes as well as a profile.
The generic fallback assumes APS-C; provide a matching camera profile for other sensor formats.

### Confirmed on the A5100

Live view, lens identification, preset application, P/A/S/M settings, aperture and shutter
stepping, EV, metering, AF-S/AF-C settings, drive mode, and Creative Style were checked through
camera read-back and photo EXIF where recorded. Soft Skin Low was checked in a separate face
test. Physical 2 s / 10 s timed shooting, half-press autofocus with the timer, saved preset
restoration, optional power-on reopening, and explicit exit followed by manual relaunch were
also exercised on the body.

### Current limits

- The A5100 API used here cannot set an Auto ISO minimum/maximum ISO range; it uses the body's
  own range. The minimum shutter-speed setting is supported.
- The app cannot trigger Eye AF on the A5100; use the camera's assigned Eye AF button.
- Playback inside the app has not been tested on a body yet. It is for checking pictures just
  taken: no movies, zoom or delete; use the camera's own playback for those.
- Other bodies, lens combinations, autofocus performance on moving subjects, and long-session
  reliability still need community testing. A firmware contract report proves API signatures,
  not working camera behavior.
- **Keep current look** skips Creative Style and Soft Skin writes. EV still changes with the
  preset, so this is not a guarantee that a complete Recipe Lab recipe stays unchanged or is
  restored after exit. Interoperability with Recipe Lab has not been verified on the body.

## Install

1. Download the APK and matching `.sha256` from a published [Release](../../releases).
   Signed development snapshots are available as `LensPreset-dev-apk` artifacts in Actions and
   the `dev` prerelease, which is removed at each release until the next code change. The pull
   request `ci` artifact `LensPreset-apk` uses a debug key for testing.
   An unpublished release draft is visible only to maintainers.
2. Camera: *Setup → USB Connection → Mass Storage*, connect USB.
3. Install with [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE):
   `pmca-console install -d native -f LensPreset-<version>.apk`, or PMCA-GUI → *Install app*.
4. Open it from the camera's *Application List*.

To remove it: *MENU → Application → Application Management → Manage and Remove → LensPreset*.
An APK signed with a different key cannot be installed over an existing one: remove the old one
first. Installation problems belong in the
[Sony-PMCA-RE issues](https://github.com/ma1co/Sony-PMCA-RE/issues); preset and app problems here.

**If the app hangs or shows a black screen:** connect the USB cable to a computer — the camera
closes the app — then remove it as above or install a newer build over it. If that does not work,
take the battery out for a minute. Please open a [bug report](../../issues/new?template=bug_report.yml)
with the app's log: *MENU → Save log to card* writes it to `LENSPRST/LOG.TXT`.

## Using it

| Key | Live view | Lists |
|---|---|---|
| Up / Down | previous / next situation (applied at once) | move |
| Wheel | change the highlighted ISO, EV, or preset option (applied at once) | move; in preset options, change the selected value |
| Left / Right | select ISO, EV, or a preset option to highlight | change a value |
| Centre | list of situations | select |
| MENU | app menu: lens, language, settings, camera probe, save log, reload, about, exit | back |
| ? / Trash | cycle: hide hints → hide key guide too → hide all overlays → show all | back |
| Playback | review the pictures on the card, newest first (below) | review |

On bodies with a touchscreen, tap a configurable card to select it; tapping elsewhere in live view
opens the list. Shoot with the shutter button as usual; settings apply while the app is open.
Lists use the full screen, with one line per choice and the selected item's description below.
The main menu fits all nine entries at 640×480; longer lists show a position counter and scrollbar.
About and profile errors wrap their text; Up / Down or the wheel scrolls them.
Left / Right cycles through ISO, EV, then the preset's own options. The selected card has a brighter
amber background and border; the key guide names the setting the wheel will change. Group photo
includes row count and self-timer in the options area below the preset and lens. These values
remain visible when hints and the key guide are hidden.
Computed camera settings appear in rounded cards at the bottom, with a small label above each
value. The cards wrap into balanced rows while recipe hints remain above them.
With EV selected, the wheel changes compensation by 1/3 stop. With ISO selected, it steps through
AUTO and the body's supported fixed ISO values (100, 125, 160, …). Turn back below the lowest fixed
ISO to return to AUTO. Fixed ISO uses the full manual ISO range, independently of the Auto ISO
ceiling; in A/P modes, watch the resulting shutter speed. Each preset remembers its ISO and EV
across restarts; until adjusted, it uses the preset's defaults. Keep current look does not suppress
ISO or EV changes. Selecting an option only highlights it; turning the wheel applies the change.
On opening or resuming, the app reapplies the last selected preset and its saved adjustments
after live view and lens identification are ready. Wait for the apply status before shooting.
On a fresh install, choose and apply a situation first.

**Playback** opens the card's pictures inside the app, newest first, as the camera's own playback
does. Left / Right or the wheel move to the previous / next one (past the newest comes the oldest),
and Up or ? / Trash cycles the information shown (full → file name only → picture only). Each
picture shows its position, folder and file name, aperture, shutter speed, ISO, exposure
compensation, focal length, date and size. Playback, MENU or the shutter button returns to shooting:
the camera is closed while playback is open, as it is on the camera itself, and the preset is
applied again on return. It is there to check what you just shot, not to replace the camera's own
playback: it shows no movies and does not delete, protect or rotate pictures.

**MENU → Settings** has two saved switches:

- **Keep current look** (on by default): leave Creative Style and Soft Skin as they are, including
  when switching presets. ISO and exposure compensation follow the selected situation and its
  saved wheel adjustments. Turning this off lets presets set their look; omitted fields become
  Standard and Soft Skin off.
- **Reopen after power-on** (off by default): return to LensPreset after power-on or wake when it
  was running. MENU → Exit cancels reopening for that session. USB still closes the app.

**The memory card** (`LENSPRST/`): `CAMERAS/`, `LENSES/`, `PRESETS/` for your own profiles
(8.3 file names such as `NIGHT.JSN`), `PROBE.TXT` from *Run camera probe*, `LOG.TXT` from *Save log
to card* (start-up, what each apply did, any crash). The app writes to the card only when you choose
one of those two.

Languages: English, Tiếng Việt. Translations are welcome.

## Building

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). In short: `./tools/test.sh` (JDK 17+ only) and
`./build.sh` (Android SDK with `platforms;android-10` and `build-tools;35.0.0`). No Gradle.
Maintainers: see [docs/RELEASING.md](docs/RELEASING.md) for signing, release validation, and
publishing. Release notes for 1.1.0 are recorded in [CHANGELOG.md](CHANGELOG.md).

## Contributing

Camera test reports, lens profiles, presets and translations: [CONTRIBUTING.md](CONTRIBUTING.md).

## License

GPL-3.0 — see [LICENSE](LICENSE). Third-party notices: [NOTICE.md](NOTICE.md).
