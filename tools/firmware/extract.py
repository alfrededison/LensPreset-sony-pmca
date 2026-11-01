#!/usr/bin/env python3
"""Unpacks firmware with ma1co's fwtool.py into .firmware/extracted/<model>-<version>, then runs
the contract test on it.

The input is Sony's Windows updater (.exe; the Mac .dmg is not supported by fwtool). `fwtool
unpack` takes it apart (exe -> FirmwareData.dat -> decrypted FDAT -> firmware.tar) and recurses into
every archive it writes, so the partition images inside firmware.tar (ext2, axfs, cramfs, ...) land
in *_unpacked folders, Android's /system/framework among them. contract.py scans that whole tree.

    python3 tools/firmware/extract.py [--model ILCE-5100]          # every fetched source
    python3 tools/firmware/extract.py --dump dump.dat --model ILCE-5100 --version 3.10

--dump takes a firmware image a contributor dumped from their own body (Sony-PMCA-RE's updater
mode), for models whose download Sony has taken down. Only the resulting report is meant to be
shared.

fwtool.py is not on PyPI: point FWTOOL at a checkout (CI clones it at a pinned commit), and
install its requirements (pycryptodomex, pyyaml).
"""
import argparse
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import contract  # noqa: E402
import fetch  # noqa: E402

ROOT = fetch.ROOT
EXTRACTED = os.path.join(ROOT, ".firmware", "extracted")


def unpack(image, model, version):
    fwtool = os.environ.get("FWTOOL")
    if not fwtool or not os.path.exists(os.path.join(fwtool, "fwtool.py")):
        raise SystemExit("set FWTOOL to a checkout of https://github.com/ma1co/fwtool.py")
    out = os.path.join(EXTRACTED, "%s-%s" % (model, version))
    if os.path.isdir(out) and os.listdir(out):
        return out
    os.makedirs(out, exist_ok=True)
    subprocess.check_call([sys.executable, os.path.join(fwtool, "fwtool.py"), "unpack", "-f", image, "-o", out])
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--model")
    ap.add_argument("--version")
    ap.add_argument("--dump", help="a firmware dump instead of a download")
    args = ap.parse_args(argv)

    jobs = []
    if args.dump:
        if not (args.model and args.version):
            ap.error("--dump needs --model and --version")
        jobs.append((args.dump, args.model, args.version))
    else:
        for s in fetch.sources(args.model):
            if not fetch.ready(s):
                print("skip %s: not in sources.json yet" % s["model"])
                continue
            image = os.path.join(fetch.DOWNLOADS, s["file"])
            if not os.path.exists(image):
                print("skip %s: run fetch.py first" % s["model"])
                continue
            jobs.append((image, s["model"], s["version"]))

    status = 0
    for image, model, version in jobs:
        out = unpack(image, model, version)
        rc = contract.main(["--extracted", out, "--model", model, "--version", version])
        status = max(status, rc)
    return status


if __name__ == "__main__":
    sys.exit(main())
