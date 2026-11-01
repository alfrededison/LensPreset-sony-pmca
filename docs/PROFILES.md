# Profile format

Three kinds of JSON file, each checked against a schema in `profiles/schema/` (editors that
understand `"$schema"` offer completion) and by the app's own parser, which also checks rules
that span fields. Unknown keys are errors: a misspelt setting is never silently ignored.

Bundled profiles live in `profiles/<kind>/<id>.json`. To try one without a new build, copy it to
the memory card as `LENSPRST/CAMERAS/`, `LENSPRST/LENSES/` or `LENSPRST/PRESETS/` with an 8.3
name (`NIGHT.JSN`), then MENU → *Reload profiles from the card*. A card file with the id of a
bundled profile replaces it. Files that fail to load are listed under MENU → *Profile problems*.

## Camera

| key | meaning |
|---|---|
| `id`, `name` | lower-case id (`ilce-5100`); display name |
| `models` | `model.name` values the camera reports (see PROBE.TXT) |
| `sensor` | `format` (`APS-C`, `full-frame`, `1-inch`, …), `width_mm`, `height_mm`, `coc_mm` (circle of confusion for depth of field) |
| `ibis` | in-body stabilisation |
| `fixed_lens` | lens profile id of a compact's built-in lens |
| `iso` | `min`, `max`, optional `auto_max` |
| `ev_range` | exposure compensation, ± EV (default 3) |
| `burst_fps` | `lo`, `hi` (informational) |
| `controls` | `touchscreen` (required), `front_dial`, `rear_wheel`, `top_dial`, `custom_keys` |
| `features` | each `yes` / `no` / `unknown`: `face_detection`, `eye_af`, `soft_skin`, `creative_style`, `silent_shutter`, `read_lens_info`, `read_focal_length`, `set_exposure_mode`, `auto_iso_min_shutter`, `auto_iso_range`, `continuous_af`, `burst`, `self_timer`, `metering` |
| `status` | `verified`, `community-tested`, `untested` |
| `tested_firmware`, `tested_by`, `notes` | provenance |

**Features record observations, not spec sheets.** `unknown` is tried and the result shown;
`no` is never tried and the preset falls back with a notice. Change a value only with a test
report from a real body.

## Lens

| key | meaning |
|---|---|
| `mount` | `E`, `A`, `fixed` |
| `coverage` | image circle: `APS-C`, `full-frame`, … An APS-C lens on a full-frame body is computed in crop mode |
| `type` | `prime` or `zoom` |
| `focal_min`, `focal_max` | mm; equal on a prime |
| `aperture_max`, `aperture_max_tele` | widest f-number at the wide and tele end (variable-aperture zooms) |
| `aperture_min` | narrowest f-number |
| `sweet_spot` | `[low, high]` sharpest f-numbers |
| `ois`, `electronic`, `af` | stabilised; the camera can set the aperture; autofocus |
| `min_focus_m` | closest focus |
| `match` | `names` (substrings of the name the camera reports) and/or `ids` — how the lens is recognised |

## Preset

```json
{
  "id": "portrait-bokeh",
  "order": 20,
  "name": { "en": "Portrait, blurred background", "vi": "1 người xoá phông" },
  "description": { "en": "…", "vi": "…" },
  "goal": "shallow_dof",
  "exposure_mode": "aperture_priority",
  "aperture": { "strategy": "widest_safe", "stop_down_from_max": 0.67 },
  "shutter": { "strategy": "handheld_min", "min": "1/125" },
  "iso": "auto",
  "metering": "multi",
  "af": { "mode": "single", "area": "center", "face": true, "eye": "if_available" },
  "drive": { "mode": "single" },
  "style": { "creative_style": "portrait", "fallback": "standard", "soft_skin": "low" },
  "subject": { "framing": "half_body" },
  "hints": ["local_light"],
  "hint_text": {
    "local_light": {
      "en": "Turn the face toward soft light.",
      "vi": "Cho mặt hướng về ánh sáng mềm."
    }
  },
  "overlays": ["thirds"]
}
```

| key | values |
|---|---|
| `exposure_mode` | `program`, `aperture_priority` (needs `aperture`), `shutter_priority` (needs a `fixed` shutter, no `aperture`), `manual` (both) |
| `aperture.strategy` | `widest`; `widest_safe` + `stop_down_from_max` (stops); `sweet_spot` + `position` `low`/`mid`/`high`; `dof` + `depth_m`, `per_row_m` (needs `subject`); `fixed` + `value`. Then `min_f` / `max_f`, then the lens's limits |
| `shutter.strategy` | `handheld_min`: 1 / (2 × focal × crop), `stabilization_stops` slower (default 2) with OIS or IBIS, never slower than `min`. In aperture priority and program it becomes Auto ISO's minimum speed. `fixed` + `value` |
| `iso` | `"auto"`, `{"mode": "auto", "min": …, "max": …}`, `{"mode": "fixed", "value": 400}` |
| `ev` | −3 … +3, rounded to thirds, clamped to the body |
| `metering` | `multi`, `center`, `spot` |
| `af` | `mode` `single`/`continuous`/`automatic`/`manual`; `area` `wide`/`zone`/`center`/`flexible_spot`/`tracking`; `face`; `eye` `off`/`if_available` |
| `drive.mode`, `self_timer` | `single`/`burst_lo`/`burst_hi`; 0, 2 or 10 s |
| `style` | `creative_style` and `fallback` (Sony names: `standard`, `vivid`, `portrait`, `landscape`, `light`, `mono`, …), `soft_skin` `off`/`low`/`mid`/`high` |
| `subject.framing` | `head_shoulders`, `half_body`, `full_body`, `environment`, `group` (+ `people_per_row`): drives the distance hint and depth of field |
| `adjust` | values the user changes with left/right: `rows` (`min`, `max`, `default`; group only), `self_timer` (`values`, `default`) |
| `hints` | ordered hint ids; every id must have text in this preset's `hint_text` |
| `hint_text` | map of hint id to `{"en": "…", "vi": "…"}` or plain English text; required when `hints` is non-empty, and every id must appear in `hints`. Missing languages fall back to English |
| `overlays` | `thirds`, `center`, `horizon` guides |

A preset written for yourself can carry `name` as plain English text; bundled presets need every
bundled language.

Keep new hint text inside the preset's `hint_text`: copying a single JSON file to the memory
card then carries its settings, name, description and hints together, with no app i18n changes.
Text belongs only to that preset; it never changes another preset's hints. The parser rejects
missing hint text and reports the field under MENU → *Profile problems*. App language tables
contain UI and generated guidance (distance, depth of field, manual focus), not preset hints.
All bundled presets include local text for every hint. Apps built before `hint_text` support
need an update to read this field.

The app's **Keep current look** setting is on by default and skips `style` writes entirely.
When it is off, a missing `creative_style` becomes `standard` and a missing `soft_skin` becomes
`off`, so values from another preset do not linger. Exposure compensation (`ev`) is always
controlled by the preset. Keeping the look does not restore a Recipe Lab look already overwritten
by a previous apply; apply that recipe again first.
