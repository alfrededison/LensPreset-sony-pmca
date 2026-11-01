#!/usr/bin/env python3
"""Checks profiles against profiles/schema/*.schema.json and that each file is named after its id.

    python3 tools/validate_profiles.py                # the bundled profiles
    python3 tools/validate_profiles.py MYPRESET.JSN   # files you wrote (kind from the folder, or --kind)

Needs the jsonschema package (pip install jsonschema). The app's own parser is stricter in places
(rules spanning fields); tools/test.sh runs it over every bundled file and combination.
"""
import argparse
import json
import os
import sys

try:
    import jsonschema
except ImportError:
    sys.exit("pip install jsonschema")

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
KINDS = {"cameras": "camera", "lenses": "lens", "presets": "preset"}


def schema(kind):
    with open(os.path.join(ROOT, "profiles", "schema", kind + ".schema.json")) as f:
        return json.load(f)


def check(path, kind, bundled):
    try:
        with open(path, encoding="utf-8-sig") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:
        return ["%s: %s" % (path, e)]
    shown = os.path.relpath(path, ROOT) if os.path.abspath(path).startswith(ROOT + os.sep) else path
    errors = []
    validator = jsonschema.Draft7Validator(schema(kind))
    for e in sorted(validator.iter_errors(data), key=lambda e: [str(p) for p in e.absolute_path]):
        where = ".".join(str(p) for p in e.absolute_path) or "(top)"
        errors.append("%s: %s: %s" % (shown, where, e.message))
    if bundled and isinstance(data, dict) and str(data.get("id")) + ".json" != os.path.basename(path):
        errors.append("%s: file name must be <id>.json" % shown)
    return errors


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("files", nargs="*")
    ap.add_argument("--kind", choices=sorted(KINDS.values()))
    args = ap.parse_args(argv)

    jobs = []
    if args.files:
        for f in args.files:
            folder = os.path.basename(os.path.dirname(os.path.abspath(f))).lower()
            kind = args.kind or KINDS.get(folder)
            if not kind:
                ap.error("%s: cannot tell the kind from its folder; pass --kind" % f)
            jobs.append((f, kind, False))
    else:
        for folder, kind in KINDS.items():
            d = os.path.join(ROOT, "profiles", folder)
            jobs += [(os.path.join(d, n), kind, True) for n in sorted(os.listdir(d)) if n.endswith(".json")]

    errors = []
    for path, kind, bundled in jobs:
        errors += check(path, kind, bundled)
    for e in errors:
        print(e)
    print("%d files, %d problems" % (len(jobs), len(errors)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
