# Contributing

All kinds of help move this forward, most of all **tests on real cameras**.

## Test the app on your body

1. Install the latest `dev` build (see the README).
2. MENU → *Run camera probe*. Try each preset, take a few shots, and watch what the camera actually
   does (the app's status line says what it set; the camera's own display says what happened).
3. Open a [camera test report](../../issues/new?template=compatibility_report.yml) with
   `LENSPRST/PROBE.TXT`, `LENSPRST/LOG.TXT` (MENU → *Save log to card*) and the feature table
   filled in.

A report is how a profile's `features` move from `unknown` to `yes` / `no`, and its `status` from
`untested` to `community-tested`. Report what you saw, not what the app claimed.

## Add a lens, camera or preset

1. Copy the closest file in `profiles/` and edit it. The format is in [docs/PROFILES.md](docs/PROFILES.md).
2. Try it from the memory card first (`LENSPRST/LENSES/MYLENS.JSN`, MENU → *Reload profiles*).
3. Check it:
   ```bash
   python tools/validate_profiles.py
   ./tools/test.sh
   ```
   `tools/test.sh` runs every camera × lens × preset combination through the engine and fails if
   anything leaves the body's or lens's limits.
4. For a lens, include the name the camera reports (`getLensInfo` in PROBE.TXT) under `match.names`.
5. A new preset needs `name` and `description` in every language in `i18n/`, and any new hint keys
   added to every `i18n/*.json`.

## Translate

Copy `i18n/en.json` to `i18n/<code>.json` and translate the values; keep the keys and the
`{placeholders}`. Add your language to every bundled preset's `name` and `description`.
`./tools/test.sh` checks that nothing is missing. The camera's font draws Latin scripts
(Vietnamese included); other scripts need a bundled font first — open an issue.

## Code

- Read [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Code, comments and files the app writes are in
  English; only `i18n/` is multilingual.
- Java must run on Android 2.3.7: `./build.sh` compiles against API 10 and fails on newer calls.
- Logic that needs no camera goes into `core/` with a test; `MainActivity` and `SonyCamera` stay thin.
- A new Sony API call means adding it to `camera-adapter/stubs/` — that is the contract the
  firmware check verifies.
- Commits and PR titles: `type(scope): subject` — types `feat fix docs refactor test build ci chore`,
  scopes such as `engine presets profiles adapter ui i18n firmware build`. PRs are squash-merged.
- Say in the PR whether it was tested on a camera, and which one.

## Firmware

Sony firmware is Sony's copyright. Never commit firmware or anything extracted from it, and never
upload it anywhere. Capability reports are made on your own machine with `tools/firmware/extract.py`
(see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#firmware-contract-pipeline)) and only the report is
committed: `firmware/sources.json` holds only download pages and checksums, `firmware/reports/`
only facts (which API names exist). If your body's firmware is no longer on Sony's site, a dump of
your own body works too. Reading firmware to check compatibility, without redistributing it, is the
intent here; this is not legal advice.
