#!/usr/bin/env python
# Altered Aether
# Copyright (C) 2026 Linc Stoel
#
# This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
# License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
# version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
# warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with this program. If not, see
# <https://www.gnu.org/licenses/>.
"""
Draws the ambrosium campfire's flame: 16 frames of 16x16, stacked into a vertical strip like any animated block
texture, in the palette of the Aether's ambrosium shard (cream core, yellow, gold, dark gold at the fringe).

It is drawn from scratch rather than recolored from the vanilla flame: an atlas source can recolor a texture at load
time, but the sprite it makes loses its animation, so the strip would show all its frames at once. The tongues sway and
flicker on integer harmonics of the frame count, so the loop has no seam.

Usage:  python tools/textures/ambrosium_campfire_fire.py
"""
import json
import math
import pathlib
import random

from PIL import Image

FRAMES = 16
SIZE = 16
OUT = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/textures/block"

# brightest to dimmest, from the ambrosium shard
PALETTE = [(250, 249, 180), (244, 242, 96), (219, 212, 67), (184, 168, 40), (128, 106, 20)]

# (centre column, width at the base, height, sway, phase) of each tongue of flame
TONGUES = [
    (4.0, 3.6, 10.0, 1.1, 0.0),
    (8.0, 5.0, 14.0, 1.4, 1.7),
    (11.5, 3.8, 11.0, 1.0, 3.1),
    (6.0, 2.6, 7.0, 0.8, 4.4),
    (10.0, 2.4, 8.0, 0.9, 5.3),
]


def frame(index):
    t = index / FRAMES * 2 * math.pi
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    heat = [[0.0] * SIZE for _ in range(SIZE)]
    for centre, width, height, sway, phase in TONGUES:
        h = height * (1 + 0.16 * math.sin(2 * t + phase) + 0.07 * math.sin(3 * t + phase * 1.3))
        for y in range(SIZE):
            rise = y / max(h, 1)          # 0 at the base, 1 at the tip
            if rise >= 1:
                continue
            half = width / 2 * (1 - rise) ** 0.75
            c = centre + sway * math.sin(t + phase) * rise ** 1.4
            for x in range(SIZE):
                d = abs(x + 0.5 - c)
                if d < half:
                    strength = (1 - d / max(half, 0.01)) * (1 - 0.55 * rise) + 0.25 * (1 - rise)
                    heat[y][x] = max(heat[y][x], min(1.0, strength))
    rng = random.Random(index * 7919)
    # a spark or two lifting off the tips
    for _ in range(2):
        if rng.random() < 0.7:
            sx, sy = rng.randrange(3, 13), rng.randrange(9, 15)
            if heat[sy][sx] < 0.05:
                heat[sy][sx] = 0.3
    for y in range(SIZE):
        for x in range(SIZE):
            v = heat[y][x]
            if v < 0.14:
                continue
            level = 0 if v > 0.78 else 1 if v > 0.52 else 2 if v > 0.33 else 3 if v > 0.2 else 4
            r, g, b = PALETTE[level]
            # row 0 of the image is the top, so the base of the flame is the last row
            img.putpixel((x, SIZE - 1 - y), (r, g, b, 255))
    return img


def main():
    strip = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    for i in range(FRAMES):
        strip.paste(frame(i), (0, i * SIZE))
    OUT.mkdir(parents=True, exist_ok=True)
    strip.save(OUT / "ambrosium_campfire_fire.png")
    (OUT / "ambrosium_campfire_fire.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 2}}, indent=2) + "\n", encoding="utf-8")
    print("wrote", OUT / "ambrosium_campfire_fire.png")


if __name__ == "__main__":
    main()
