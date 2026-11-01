#!/usr/bin/env python3
"""Compares what the app says it set with what the camera recorded in the photo.

    exif_check.py <exiftool JSON> <the app's apply report for the preset>

Used by tools/camera-test.sh --photos. The apply report holds lines like "OK metering center-spot,
read back" and "OK aperture got f/1.8 in 4 steps, nearest (...)"; the photo's EXIF and Sony
MakerNotes (read by exiftool) say what the shot really used. Prints one line per setting; a
disagreement is a "PHOTO MISMATCH" line. Settings the photo does not record are listed as such.
"""
import json
import math
import re
import sys

EXPOSURE_PROGRAM = {
    "aperture-priority": "Aperture-priority AE",
    "shutter-speed": "Shutter speed priority AE",
    "manual-exposure": "Manual",
    "program-auto": "Program AE",
}
METERING = {"multi": "Multi-segment", "center-weighted": "Center-weighted average", "center-spot": "Spot"}
FOCUS = {"af-s": "AF-S", "af-c": "AF-C", "af-a": "AF-A", "manual": "Manual"}
STYLE = {"red-leaves": "Autumn", "mono": "B&W", "night": "Night"}
SOFT_SKIN = {"off": "Off", "low": "Low", "middle": "Mid", "high": "High"}


def applied(report_text):
    """{key: value} for every setting the report marks OK, e.g. {"metering": "center-spot"}."""
    out = {}
    for line in report_text.splitlines():
        m = re.search(r"\bOK (\w+) (.*)$", line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def text_match(want, have):
    return have is not None and str(have).lower().startswith(str(want).lower())


def main(exif_path, report_path):
    with open(exif_path) as f:
        exif = json.load(f)[0]
    with open(report_path) as f:
        set_ = applied(f.read())
    lines = []

    def compare(name, key, want, tag, ok):
        have = exif.get(tag)
        if have is None:
            lines.append("  photo has no %s (%s %s)" % (tag, key, want))
        elif ok(have):
            lines.append("  photo ok    %-14s %s = %s" % (key, want, have))
        else:
            lines.append("  PHOTO MISMATCH %s: set %s, photo says %s" % (name, want, have))

    value = lambda k: set_[k].split(",")[0].strip()
    if "exposure_mode" in set_:
        v = value("exposure_mode")
        compare("exposure mode", "exposure_mode", v, "ExposureProgram", lambda h: text_match(EXPOSURE_PROGRAM.get(v, v), h))
    if "metering" in set_:
        v = value("metering")
        compare("metering", "metering", v, "MeteringMode", lambda h: text_match(METERING.get(v, v), h))
    if "focus_mode" in set_:
        v = value("focus_mode")
        compare("focus mode", "focus_mode", v, "FocusMode", lambda h: text_match(FOCUS.get(v, v), h))
    if "creative_style" in set_:
        v = value("creative_style")
        compare("creative style", "creative_style", v, "CreativeStyle", lambda h: text_match(STYLE.get(v, v), h))
    if "soft_skin" in set_:
        v = value("soft_skin")
        compare("soft skin", "soft_skin", v, "SoftSkinEffect", lambda h: text_match(SOFT_SKIN.get(v, v), h))
    if "ev" in set_:
        v = value("ev")
        compare("exposure compensation", "ev", v, "ExposureCompensation",
                lambda h: abs(float(h) - float(v)) < 0.2)
    if "aperture" in set_:
        m = re.search(r"got f/([\d.]+)", set_["aperture"])
        if m:
            want = float(m.group(1))
            compare("aperture", "aperture", "f/%s" % m.group(1), "FNumber",
                    lambda h: abs(2 * math.log2(float(h) / want)) < 0.2)
    if "shutter" in set_:
        m = re.search(r"got (\d+)/(\d+)|got ([\d.]+)\"", set_["shutter"])
        if m:
            want = float(m.group(1)) / float(m.group(2)) if m.group(1) else float(m.group(3))
            compare("shutter", "shutter", m.group(0)[4:], "ExposureTime",
                    lambda h: abs(math.log2(float(h) / want)) < 0.2)
    for tag in ("ISO", "FNumber", "ExposureTime", "AFAreaMode", "FaceDetection", "ReleaseMode"):
        if tag in exif:
            lines.append("  photo says  %-14s %s" % (tag, exif[tag]))
    print("\n".join(lines))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
