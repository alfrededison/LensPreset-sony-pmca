#!/usr/bin/env python3
"""Build using the maintainer's ignored local signing credentials; never prints secret values."""
import argparse
import base64
import json
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", action="store_true", help="build the stable version without a dev suffix")
    parser.add_argument("--credentials", type=Path, default=ROOT / ".signing/credentials.json")
    args = parser.parse_args()
    try:
        config = json.loads(args.credentials.read_text())
        keystore = args.credentials.parent / config["keystore_file"]
        env = dict(os.environ)
        env.update({
            "ANDROID_KEYSTORE_B64": base64.b64encode(keystore.read_bytes()).decode("ascii"),
            "ANDROID_KEYSTORE_PASSWORD": config["store_password"],
            "ANDROID_KEY_ALIAS": config["alias"],
            "ANDROID_KEY_PASSWORD": config["key_password"],
            "REQUIRE_PROJECT_SIGNING": "1",
            "RELEASE": "1" if args.release else "0",
        })
    except (OSError, ValueError, KeyError):
        parser.exit(1, "Cannot load local signing credentials; no secret values were printed.\n")
    return subprocess.call(["bash", str(ROOT / "build.sh")], cwd=ROOT, env=env)


if __name__ == "__main__":
    raise SystemExit(main())
