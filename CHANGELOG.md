# Changelog

## 1.2.1 — unreleased

## 1.2.0 — 2026-11-06

### Playback

- The Playback button opens an in-app playback screen, newest picture first, like the camera's
  own: Left / Right and the wheel browse (wrapping past the newest), Up or ? / Trash cycles the
  information (full → file name → picture only), and Playback, MENU or the shutter button return
  to shooting. It is for checking pictures just taken: no movies or delete.
- The zoom lever zooms in and out, one step per push, up to 3x: the four-way moves around, the
  wheel goes to the next picture at the same zoom and place (to compare focus across a burst),
  Centre shows the whole picture again, and a minimap of two outlines shows which part is on
  screen. It zooms the 1616x1080 screennail, at once; the camera's own playback zooms further.
- Pictures come from the camera's own picture index (AvindexStore), as ma1co/PMCADemo reads it:
  the 160x120 thumbnail and the Exif line (aperture, shutter, ISO, EV, focal length, date, size)
  appear at once, then the screen-sized JPEG. Portrait pictures are turned upright, and pictures
  keep their proportions on the A5100's non-square UI pixels (640x480 on a 16:9 panel).
- After a shot, playback brings the camera's picture index up to date before showing anything, so
  it opens on the picture just taken rather than the one before it.
- The camera is closed while playback is open and opened again on return, which applies the
  preset again. The UI framebuffer is 32-bit only while playback is open, with the camera closed,
  as Sony's own picture grid does; pause and crashes set 16-bit again.
- `LENSPRST/DEBUG.TXT` switch `playback-16bit` keeps 16-bit colour in playback; adb control adds
  `playback open|close|next|prev|zoom-in|zoom-out|zoom-off|wheel-next|wheel-prev|fit`.

### Validation and compatibility

- Every new Sony API member is in the ILCE-5100 3.10 firmware report: all present, constants
  matching. The formatting and layout geometry have unit tests.
- Checked on ILCE-5100 3.10: browsing, opening on the picture just taken, the live view on return,
  and zoom up to 3x with the minimap.

## 1.1.0 — 2026-11-02

### Shooting

- Compact full-screen menus with one line per choice, the selected description below, and a
  position counter / scrollbar. All nine main-menu entries fit at 640×480. Shorter English and
  Vietnamese labels and key guides; live-view settings wrap without splitting individual values.
  Recipe hints are unchanged. Text pages now wrap and scroll with Up / Down or the wheel.
- The main menu shows the current lens and language beside their labels.
- Live-view settings use rounded cards with translated labels above their values, wrapping into
  balanced rows. Recipe hint text and its font size are unchanged.
- Preset options such as group row count and self-timer have a separate amber area below the
  preset and lens. Computed exposure and focus settings stay in the bottom strip.
- Live-view Left / Right selects ISO, EV, then the preset's own options (Group rows and self-timer).
  The selected card has a brighter amber background and border; touchscreen users can select a
  card by tapping it. Turning the wheel applies the selected value immediately.
- ISO steps between AUTO and the body's supported manual sensitivities; EV steps by 1/3 stop.
  Each preset remembers ISO and EV across restarts, including with Keep current look enabled.
  ISO/EV writes and read-back do not reapply focus or drive. Fixed ISO in A/P modes shows a
  shutter-speed reminder instead of claiming an enforced Auto ISO shutter threshold.
- In preset options, the wheel changes the selected value; Up / Down selects the setting.
  Page Up / Page Down simulate wheel turns on an emulator.
- The ? / Trash button cycles live-view detail: hide hints, hide the key guide too, hide all
  overlays (including composition guides), then show everything again.

### Profile format

- Group row-count labels belong to each preset's `adjust.rows.label`, including English and
  Vietnamese in the bundled group preset. Custom group presets must supply this label. The
  self-timer label remains shared app i18n text.
- Group-specific distance guidance and depth-of-field warnings live in `subject.distance_hint`
  and `aperture.dof_warning`. These optional translated fields support English fallback; the
  distance template receives `{m}`. Presets without overrides use shared generic guidance.

### Validation and compatibility

- The new live-view controls and ISO/EV choices have automated tests and emulator checks;
  real-camera testing of these changes is pending. Existing A5100 evidence is listed below.

## 1.0.1 — 2026-11-01

Published on [GitHub Releases](https://github.com/alfrededison/LensPreset-sony-pmca/releases/tag/v1.0.1).

### Shooting

- Beach scenery and portraits, pool action, garden portraits, and forest/mountain scenery,
  with English and Vietnamese light, framing, and highlight hints.
- Preset-owned hint translations in `hint_text` for all twelve bundled presets, so adding
  card presets does not require new app language-table entries. Missing preset hint text is
  rejected with a profile error; duplicate hint translations have been removed from app i18n.

### Profile format

- Presets with hints must include matching `hint_text` entries. Update custom card presets
  that previously relied on app translations before loading them in this version.
- All twelve bundled presets carry their own English and Vietnamese hint text.

### Validation and compatibility

- The new trip presets and hint rendering are software-tested; real-camera testing of these
  changes is still pending. Existing A5100 compatibility evidence is listed below.

## 1.0.0 — tagged MVP baseline

### Shooting

- Seven shooting situations with body- and lens-aware exposure, focus, and drive settings.
- Live view with composition guides, estimated framing distance, and depth-of-field hints.
- English and Vietnamese, button and touch navigation, and profiles loaded from the memory card.
- Settings read-back with unsupported values and mismatches recorded in the app log.
- Physical self-timer shooting at 2 s / 10 s, half-press autofocus in timer mode, and return to
  burst shooting when the group's timer is disabled.
- Saved preset and adjustments reapplied after startup once the camera and live view are ready.
- Keep current look enabled by default; Creative Style and Soft Skin are left alone. With it
  disabled, omitted look fields resolve to Standard / Soft Skin off. EV follows the preset.
- Optional power-on reopening, disabled by default, with explicit exit clearing the registration
  before closing to prevent immediate reopening and failed live view.

### Compatibility and contributions

- Sony A5100 (ILCE-5100), firmware 3.10, with Sigma 30mm F1.4 DC DN community-tested by
  [@alfrededison](https://github.com/alfrededison).
- A6000 and generic camera profiles, Sony 16–50mm kit zoom and manual 50mm example profiles
  supplied for further testing; their hardware compatibility is not established.
- JSON schemas, contribution instructions, compatibility reports, camera probe/log tools, and
  firmware API contract checks for community work.

### Known limits

- No Auto ISO range control or app-triggered Eye AF on the A5100 through the current adapter.
- Zoom tracking, other body/lens combinations, long-session reliability, and moving-subject AF
  performance still need hardware testing.
- Keep current look does not preserve EV or guarantee complete Recipe Lab interoperability.
- A new body's API differences may require adapter changes in addition to a JSON profile.

See [the README](README.md) for usage and [Releasing](docs/RELEASING.md) for the publication steps.
