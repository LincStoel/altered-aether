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
PLACEHOLDER item textures for nadirite's way to andesite, to be redrawn by hand:

- crushed_nadirite: a few angular lumps, shaded from nadirite's own palette, dark outline.
- nadirite_grit: a low mound of fine grains, lighter than the stone (the iron washed out).
- aether_crust_mix: the same mound, grit speckled through with quicksoil's yellow.

Usage:  python tools/textures/nadirite_items.py [--preview out.png]   (overwrites all three)
"""
import pathlib
import random
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/textures"
ITEMS = ROOT / "item"

QUICKSOIL = [(208, 194, 118), (216, 206, 130), (227, 223, 150)]


def nadirite_palette():
    stone = Image.open(ROOT / "block/nadirite.png").convert("RGB")
    colours = sorted(stone.get_flattened_data() if hasattr(stone, "get_flattened_data") else stone.getdata(),
                     key=lambda c: sum(c))
    return [colours[int(i * (len(colours) - 1) / 4)] for i in range(5)]  # dark .. light


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def outline(img, colour):
    px = img.load()
    out = img.copy()
    po = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3]:
                    po[x, y] = colour + (255,)
                    break
    return out


def lump(px, cx, cy, rx, ry, pal, rng):
    """An angular lump: a diamond-ish blob lit top-left, shaded bottom-right."""
    for y in range(16):
        for x in range(16):
            d = abs(x - cx) / rx + abs(y - cy) / ry
            if d <= 1.0:
                light = (cx - x) / rx + (cy - y) / ry  # >0 toward the top left
                i = 2 + (1 if light > 0.35 else 0) - (1 if light < -0.35 else 0)
                i = max(0, min(4, i + (1 if rng.random() < 0.12 else 0)))
                px[x, y] = pal[i] + (255,)


def crushed(pal):
    rng = random.Random(11)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for cx, cy, rx, ry in ((5, 9, 3.6, 3.2), (10.5, 10, 3.4, 3.0), (8, 5, 3.0, 2.8)):
        lump(px, cx, cy, rx, ry, pal, rng)
    return outline(img, shade(pal[0], 0.45))


def mound(colours, seed):
    """A heap of grains: a half-ellipse resting on row 13, grains picked per pixel, lit along its top."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 6.5, (13 - y) / 7.0
            if y <= 13 and dx * dx + dy * dy <= 1.0 and y >= 5:
                c = rng.choice(colours)
                edge = dx * dx + (dy + 0.12) ** 2 > 0.8
                if edge and y < 11:
                    c = shade(c, 1.12)
                elif x > 10 or y == 13:
                    c = shade(c, 0.85)
                px[x, y] = c + (255,)
    return outline(img, shade(colours[0], 0.5))


def main():
    pal = nadirite_palette()
    grit = [shade(c, 1.12) for c in pal[1:]]
    textures = {
        "crushed_nadirite": crushed(pal),
        "nadirite_grit": mound(grit, 23),
        "aether_crust_mix": mound(grit[:2] + QUICKSOIL + QUICKSOIL[1:2], 37),
    }
    for name, img in textures.items():
        img.save(ITEMS / f"{name}.png")
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        sheet = Image.new("RGBA", (16 * len(textures) + 2 * (len(textures) - 1), 16), (60, 60, 70, 255))
        for i, img in enumerate(textures.values()):
            sheet.alpha_composite(img, (i * 18, 0))
        sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(out)


if __name__ == "__main__":
    main()
