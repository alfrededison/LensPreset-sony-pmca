#!/usr/bin/env python3
"""Downloads the firmware listed in firmware/sources.json into .firmware/downloads and checks its
SHA-256. A source without url and sha256 is skipped, never guessed.

    python3 tools/firmware/fetch.py [--model ILCE-5100]

Firmware is Sony's copyright: it is read during tests to check compatibility and is never
committed, uploaded as a CI artifact, or redistributed. .firmware/ is git-ignored.
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DOWNLOADS = os.path.join(ROOT, ".firmware", "downloads")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def sources(model=None):
    with open(os.path.join(ROOT, "firmware", "sources.json")) as f:
        items = json.load(f)["sources"]
    return [s for s in items if model is None or s["model"] == model]


def ready(source):
    return bool(source.get("url") and source.get("sha256") and source.get("file") and source.get("version"))


def fetch(source):
    """Path of the verified download, fetching it if it is not cached."""
    os.makedirs(DOWNLOADS, exist_ok=True)
    path = os.path.join(DOWNLOADS, source["file"])
    if os.path.exists(path) and sha256(path) == source["sha256"]:
        return path
    part = path + ".part"
    with urllib.request.urlopen(source["url"]) as r, open(part, "wb") as f:
        shutil.copyfileobj(r, f)
    actual = sha256(part)
    if actual != source["sha256"]:
        os.remove(part)
        raise RuntimeError("%s: sha256 %s does not match sources.json" % (source["file"], actual))
    os.replace(part, path)
    return path


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--model")
    args = ap.parse_args(argv)
    for s in sources(args.model):
        if not ready(s):
            print("skip %s: url / sha256 not filled in firmware/sources.json" % s["model"])
            continue
        print("%s %s: %s" % (s["model"], s["version"], os.path.relpath(fetch(s), ROOT)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
