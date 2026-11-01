#!/usr/bin/env python3
"""Unpacks a firmware file with ma1co's fwtool.py and writes its capability report.

    FWTOOL=~/src/fwtool.py python3 tools/firmware/extract.py \\
        --file Update_ILCE5100V310.exe --model ILCE-5100 --version 3.10

Run it on your own machine, then commit firmware/reports/<model>-<version>.json. The firmware and
the extracted tree stay in .firmware/ (git-ignored): they are Sony's copyright and are never
committed or uploaded. CI only checks the committed reports (contract.py --check-reports).

--file takes Sony's Windows updater (.exe; fwtool does not read the Mac .dmg), its
FirmwareData.dat, or a dump from your own body (Sony-PMCA-RE's updater mode) for models Sony no
longer hosts. Newer updaters (Update_ILCE5100V310.exe among them) are a format fwtool does not know;
updater.py takes their FirmwareData_*.dat out first. `fwtool unpack` then takes it apart
(FirmwareData.dat -> decrypted FDAT -> firmware.tar) and recurses into every archive it writes, so
the partition images inside firmware.tar (ext2, axfs, cramfs, ...) land in *_unpacked folders,
Android's /system/framework among them. contract.py scans that whole tree.

When firmware/sources.json lists the model and version with a sha256, the file must match it;
otherwise the file's sha256 is printed so it can be recorded there.

fwtool.py is not on PyPI: point FWTOOL at a checkout (https://github.com/ma1co/fwtool.py, tested at
cdba742) and install its requirements (pycryptodomex, pyyaml).
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import contract  # noqa: E402
import updater  # noqa: E402

ROOT = contract.ROOT
EXTRACTED = os.path.join(ROOT, ".firmware", "extracted")
SOURCES = os.path.join(ROOT, "firmware", "sources.json")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def sources():
    with open(SOURCES) as f:
        return json.load(f)["sources"]


def expected_sha256(model, version):
    """The sha256 recorded in sources.json for this model and version, or None."""
    for s in sources():
        if s.get("model") == model and s.get("version") == version:
            return s.get("sha256")
    return None


def unpack(image, model, version):
    """Unpacks into .firmware/extracted/<model>-<version>, reusing a finished earlier unpack."""
    out = os.path.join(EXTRACTED, "%s-%s" % (model, version))
    if os.path.isdir(out):
        return out
    fwtool = os.environ.get("FWTOOL")
    if not fwtool or not os.path.exists(os.path.join(fwtool, "fwtool.py")):
        raise SystemExit("set FWTOOL to a checkout of https://github.com/ma1co/fwtool.py")
    # Unpack beside the target and rename on success, so a failed run is never mistaken for a result.
    tmp = out + ".tmp"
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp)
    dat = os.path.join(tmp, "FirmwareData.dat")
    found = updater.extract_dat(image, dat)
    if found:
        print("Took %s out of the updater" % found)
        image = dat
    subprocess.check_call([sys.executable, os.path.join(fwtool, "fwtool.py"), "unpack", "-f", image, "-o", tmp])
    if found:
        os.remove(dat)
    os.rename(tmp, out)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--file", required=True, help="updater .exe, FirmwareData.dat, or a dump of your body")
    ap.add_argument("--model", required=True, help="model.name, e.g. ILCE-5100")
    ap.add_argument("--version", required=True, help="firmware version, e.g. 3.10")
    args = ap.parse_args(argv)

    actual = sha256(args.file)
    expected = expected_sha256(args.model, args.version)
    if expected and expected != actual:
        print("%s: sha256 %s does not match firmware/sources.json (%s)" % (args.file, actual, expected), file=sys.stderr)
        return 2
    if not expected:
        print("sha256 %s — record it in firmware/sources.json for %s %s" % (actual, args.model, args.version))

    out = unpack(args.file, args.model, args.version)
    return contract.main(["--extracted", out, "--model", args.model, "--version", args.version])


if __name__ == "__main__":
    sys.exit(main())
