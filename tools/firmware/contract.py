#!/usr/bin/env python3
"""Contract test: does a firmware have every Sony class, method and constant the app compiles against?

The contract is camera-adapter/stubs: the stubs declare exactly what the adapter calls, so they are
compiled (javac, against nothing but the JDK and android.jar-free stand-ins) and every public member
is looked up in the framework code extracted from a firmware. Constants are compared by value,
because javac copies them into the app.

    python3 tools/firmware/contract.py --extracted .firmware/extracted/ILCE-5100-3.10 \\
        --model ILCE-5100 --version 3.10 [--stubs-classes out/build/stubs] [--out firmware/reports]

Writes firmware/reports/<model>-<version>.json (facts only, no firmware bytes) and prints a
summary. Exits 1 when something the app calls is missing, 0 otherwise. extract.py runs this for
you after unpacking.

    python3 tools/firmware/contract.py --check-reports

needs no firmware and is what CI runs: it fails when a committed report says a firmware lacks
something the app calls, and warns when the stubs call something a report has not checked yet
(re-run extract.py for that firmware and commit the new report).
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import classfile  # noqa: E402
import dex  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STUBS_SRC = os.path.join(ROOT, "camera-adapter", "stubs", "src")

# Methods worth a look on a new firmware: names the app does not call yet but that hint at features
# the camera profiles track (eye AF, soft skin, silent shutter, lens and focal reporting).
INTERESTING = re.compile(r"(?i)eye|skin|silent|electronic.?shutter|lensinfo|focal|face")


def compile_stubs(out_dir):
    """Compiles the stubs; android.* references resolve against minimal stand-ins generated here."""
    shims = os.path.join(out_dir, "shims")
    for path, body in {
        "android/hardware/Camera.java": "package android.hardware; public class Camera { public static class Parameters {} }",
        "android/util/Pair.java": "package android.util; public class Pair<F, S> { public F first; public S second; }",
    }.items():
        p = os.path.join(shims, path)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w") as f:
            f.write(body)
    sources = []
    for d, _, files in os.walk(STUBS_SRC):
        sources += [os.path.join(d, f) for f in files if f.endswith(".java")]
    classes = os.path.join(out_dir, "classes")
    subprocess.check_call(["javac", "-nowarn", "--release", "8", "-sourcepath", shims, "-d", classes] + sources)
    return classes


def load_contract(stub_classes_dir):
    stubs = classfile.read_dir(stub_classes_dir)
    return {n: c for n, c in stubs.items() if n.startswith("com.sony.")}


def scan_framework(extracted):
    """Every class in every dex / odex / jar / apk under the extracted tree; first definition wins."""
    found = {}
    files = []
    for d, _, names in os.walk(extracted):
        for n in sorted(names):
            if not n.lower().endswith((".dex", ".odex", ".jar", ".apk")):
                continue
            path = os.path.join(d, n)
            try:
                classes = dex.read_file(path)
            except Exception as e:  # noqa: BLE001 — one unreadable file must not stop the scan
                print("  skipped %s: %s: %s" % (os.path.relpath(path, extracted), type(e).__name__, e), file=sys.stderr)
                continue
            if any(c.startswith("com.sony.scalar.") for c in classes):
                files.append(os.path.relpath(path, extracted))
            for name, c in classes.items():
                found.setdefault(name, c)
    return found, files


def _lookup(found, cls, kind, key):
    """A member declared on the class or inherited from a superclass in the firmware."""
    seen = set()
    while cls is not None and cls.name not in seen:
        seen.add(cls.name)
        if kind == "method" and key in cls.methods:
            return cls
        if kind == "field" and key in cls.fields:
            return cls
        cls = found.get(cls.superclass) if cls.superclass else None
    return None


def contract_keys(contract):
    """The api keys a report holds for these stubs: class, class#method, class.field."""
    keys = set()
    for name, stub in contract.items():
        keys.add(name)
        keys.update("%s#%s" % (name, m) for m in stub.methods)
        keys.update("%s.%s" % (name, f) for f in stub.fields)
    return keys


def check_reports(keys, reports_dir):
    """(failures, warnings) for the committed reports against the current contract keys."""
    failures = []
    warnings = []
    names = sorted(n for n in os.listdir(reports_dir) if n.endswith(".json")) if os.path.isdir(reports_dir) else []
    for n in names:
        with open(os.path.join(reports_dir, n)) as f:
            report = json.load(f)
        api = report.get("api", {})
        absent = sorted(k for k in keys if api.get(k) is False)
        if absent:
            failures.append("%s: the firmware lacks %s" % (n, ", ".join(absent)))
        if report.get("constant_mismatches"):
            failures.append("%s: constants differ: %s" % (n, "; ".join(report["constant_mismatches"])))
        unchecked = sorted(k for k in keys if k not in api)
        if unchecked:
            warnings.append("%s: not checked yet for %s — re-run tools/firmware/extract.py on this firmware"
                            % (n, ", ".join(unchecked)))
    return names, failures, warnings


def check(contract, found):
    api = {}
    missing_classes = []
    missing_members = []
    mismatches = []
    for name in sorted(contract):
        stub = contract[name]
        real = found.get(name)
        if real is None:
            missing_classes.append(name)
            api[name] = False
            continue
        api[name] = True
        for m in sorted(stub.methods):
            ok = _lookup(found, real, "method", m) is not None
            api["%s#%s" % (name, m)] = ok
            if not ok:
                missing_members.append("%s#%s" % (name, m))
        for f, (desc, value) in sorted(stub.fields.items()):
            owner = _lookup(found, real, "field", f)
            api["%s.%s" % (name, f)] = owner is not None
            if owner is None:
                missing_members.append("%s.%s" % (name, f))
                continue
            real_desc, real_value = owner.fields[f]
            if real_desc != desc:
                mismatches.append("%s.%s: type %s, firmware has %s" % (name, f, desc, real_desc))
            elif value is not None and real_value is not None and _norm(value) != _norm(real_value):
                mismatches.append("%s.%s: app uses %r, firmware has %r" % (name, f, value, real_value))
    return api, missing_classes, missing_members, mismatches


def _norm(v):
    return int(v) if isinstance(v, bool) else v


def observations(found):
    """Related method names on the firmware's camera classes, for whoever extends the adapter."""
    out = []
    for name in ("com.sony.scalar.hardware.CameraEx", "com.sony.scalar.hardware.CameraEx$ParametersModifier"):
        c = found.get(name)
        if c:
            out += sorted("%s#%s" % (name.rsplit(".", 1)[1], m) for m in c.methods if INTERESTING.search(m.split("(")[0]))
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check-reports", action="store_true", help="check committed reports; no firmware needed")
    ap.add_argument("--extracted", help="directory produced by extract.py")
    ap.add_argument("--model")
    ap.add_argument("--version")
    ap.add_argument("--stubs-classes", help="compiled stubs; compiled into a temp dir when omitted")
    ap.add_argument("--out", default=os.path.join(ROOT, "firmware", "reports"))
    args = ap.parse_args(argv)

    with tempfile.TemporaryDirectory() as tmp:
        contract = load_contract(args.stubs_classes or compile_stubs(tmp))

    if args.check_reports:
        names, failures, warnings = check_reports(contract_keys(contract), args.out)
        prefix = "::warning::" if os.environ.get("GITHUB_ACTIONS") else "warning: "
        for w in warnings:
            print(prefix + w)
        for f in failures:
            print("error: " + f)
        print("%d reports checked, %d failing, %d out of date" % (len(names), len(failures), len(warnings)))
        return 1 if failures else 0

    if not (args.extracted and args.model and args.version):
        ap.error("--extracted, --model and --version are required (or use --check-reports)")
    found, files = scan_framework(args.extracted)
    if not files:
        print("no Sony framework code found under %s" % args.extracted, file=sys.stderr)
        return 2
    api, missing_classes, missing_members, mismatches = check(contract, found)
    report = {
        "model": args.model,
        "firmware": args.version,
        "framework_files": files,
        "checked": len(api),
        "ok": not (missing_classes or missing_members or mismatches),
        "missing_classes": missing_classes,
        "missing_members": missing_members,
        "constant_mismatches": mismatches,
        "api": api,
        "observations": observations(found),
    }
    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, "%s-%s.json" % (args.model, args.version))
    with open(path, "w") as f:
        json.dump(report, f, indent=2, sort_keys=False)
        f.write("\n")

    print("%s %s: %d members checked in %s" % (args.model, args.version, len(api), ", ".join(files)))
    for title, items in (("missing classes", missing_classes), ("missing members", missing_members),
                         ("constant mismatches", mismatches)):
        if items:
            print("  %s:" % title)
            for i in items:
                print("    " + i)
    print("  report: %s" % os.path.relpath(path, ROOT))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
