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
First-pass polished and bricks textures for nadirite, celezite and sylicite, derived from their hand-drawn base textures, to be
touched up by hand.

Bricks follow holystone bricks' layout: four courses of 3px brick over a 1px mortar line, 8px bricks with the joints
offset by half a brick each course. Each brick face is the base texture with its top row lit; the mortar is the base
darkened.

Polished is the base with its grain flattened toward the mean, framed by a 1px bevel (lit top and left, shaded bottom
and right). Celezite, a quartzite, and sylicite, an opal, also get a diagonal sheen.

The base textures are made fully opaque in place (celezite's had stray partial alpha).

Usage:  python tools/textures/aether_stones.py [stone ...]     (only the stones named; all of them by default, which
                                                            overwrites any hand touch-ups)
        python tools/textures/aether_stones.py --preview out.png    (also draws every texture side by side, x8)
"""
import pathlib
import sys

from PIL import Image

BLOCKS = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/textures/block"

# stone: (mortar darkening, brick top-row lift, polished grain kept, sheen strength)
STONES = {
    "nadirite": (0.62, 1.10, 0.45, 0.0),
    "celezite": (0.80, 1.05, 0.35, 0.10),
    "sylicite": (0.78, 1.06, 0.40, 0.14),
}


def clamp(v):
    return max(0, min(255, int(round(v))))


def scale(rgb, f):
    return tuple(clamp(c * f) for c in rgb)


def opaque(path):
    image = Image.open(path).convert("RGBA")
    flat = Image.new("RGBA", image.size)
    flat.putdata([(r, g, b, 255) for r, g, b, _ in image.getdata()])
    flat.save(path)
    return flat.convert("RGB")


def bricks(base, mortar, lift):
    out = Image.new("RGB", (16, 16))
    for y in range(16):
        course, row = divmod(y, 4)
        joint = 3 if course % 2 == 0 else 7
        for x in range(16):
            # sample a shifted copy so the bricks don't trace the base texture's own pattern
            px = base.getpixel(((x + 5 * course) % 16, (y + 7) % 16))
            if row == 3 or x % 8 == joint:
                out.putpixel((x, y), scale(px, mortar))
            elif row == 0:
                out.putpixel((x, y), scale(px, lift))
            else:
                out.putpixel((x, y), px)
    return out


def polished(base, keep, sheen):
    pixels = list(base.getdata())
    mean = tuple(sum(p[i] for p in pixels) / len(pixels) for i in range(3))
    out = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            px = base.getpixel((x, y))
            c = [mean[i] + (px[i] - mean[i]) * keep for i in range(3)]
            if sheen:
                # a soft band from bottom-left to top-right
                d = abs((x + (15 - y)) - 15) / 15
                c = [v + (255 - v) * sheen * max(0.0, 1 - d * 3) for v in c]
            f = 1.0
            if y == 0 or x == 0:
                f = 1.12
            if y == 15 or x == 15:
                f = 0.78
            out.putpixel((x, y), scale(c, f))
    return out


def main():
    made = []
    args = sys.argv[1:]
    if "--preview" in args:
        del args[args.index("--preview"):args.index("--preview") + 2]
    for stone, (mortar, lift, keep, sheen) in STONES.items():
        if args and stone not in args:
            continue
        base = opaque(BLOCKS / f"{stone}.png")
        b = bricks(base, mortar, lift)
        p = polished(base, keep, sheen)
        b.save(BLOCKS / f"{stone}_bricks.png")
        p.save(BLOCKS / f"polished_{stone}.png")
        made += [base, p, b]
    if "--preview" in sys.argv:
        out = pathlib.Path(sys.argv[sys.argv.index("--preview") + 1])
        sheet = Image.new("RGB", (len(made) * 136, 128), (40, 40, 40))
        for i, image in enumerate(made):
            sheet.paste(image.resize((128, 128), Image.NEAREST), (i * 136, 0))
        sheet.save(out)


if __name__ == "__main__":
    main()
