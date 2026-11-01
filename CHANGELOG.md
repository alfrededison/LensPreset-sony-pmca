# Changelog

## Unreleased

Initial MVP planned for version 1.0.0.

### Shooting

- Twelve shooting situations with body- and lens-aware exposure, focus, and drive settings.
- Beach scenery and portraits, pool action, garden portraits, and forest/mountain scenery,
  with English and Vietnamese light, framing, and highlight hints.
- Preset-owned hint translations in `hint_text` for all twelve bundled presets, so adding
  card presets does not require new app language-table entries. Missing preset hint text is
  rejected with a profile error; duplicate hint translations have been removed from app i18n.
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
