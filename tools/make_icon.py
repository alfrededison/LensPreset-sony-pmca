#!/usr/bin/env python3
"""Draws the launcher icon (a lens: aperture ring and blades) as PNGs, with no image library.

    python3 tools/make_icon.py        # writes app/res/drawable-*/ic_launcher.png

The PNGs are committed; rerun this only to change the icon.
"""
import math
import os
import struct
import zlib

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96}
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")

ORANGE = (230, 120, 30)
DARK = (28, 28, 32)
LIGHT = (236, 236, 236)


def pixel(x, y, n):
    """Colour of one pixel, supersampled 4x4 for smooth edges."""
    acc = [0.0, 0.0, 0.0, 0.0]
    for sy in range(4):
        for sx in range(4):
            px = (x + (sx + 0.5) / 4) / n * 2 - 1
            py = (y + (sy + 0.5) / 4) / n * 2 - 1
            r = math.hypot(px, py)
            colour = None
            if r <= 0.96:
                colour = DARK
                if r >= 0.80:
                    colour = ORANGE
                elif r <= 0.62:
                    # Six aperture blades: a hexagonal opening with a pinwheel edge.
                    a = math.atan2(py, px)
                    sector = (a + math.pi) % (math.pi / 3)
                    edge = 0.34 / math.cos(sector - math.pi / 6)
                    colour = LIGHT if r > edge else DARK
            if colour:
                acc[0] += colour[0]
                acc[1] += colour[1]
                acc[2] += colour[2]
                acc[3] += 255
    alpha = acc[3] / 16
    if alpha == 0:
        return (0, 0, 0, 0)
    k = 16 * alpha / 255
    return (round(acc[0] / k), round(acc[1] / k), round(acc[2] / k), round(alpha))


def png(n):
    rows = b""
    for y in range(n):
        rows += b"\x00" + b"".join(struct.pack("4B", *pixel(x, y, n)) for x in range(n))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", n, n, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def main():
    for density, n in SIZES.items():
        d = os.path.join(ROOT, "app", "res", "drawable-" + density)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "ic_launcher.png"), "wb") as f:
            f.write(png(n))


if __name__ == "__main__":
    main()
