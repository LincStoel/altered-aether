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
Draws the tamed aerwhale's saddle texture (128x64), laid out for the boxes in client/AerwhaleSaddleLayer.java: a
sky-blue blanket with gold trim, a brown leather seat with stitched edges, a darker cantle, a gold pommel, a dark
girth strap and gold buckles, in the Aether's blue-and-gold palette; and the saddlebags a chest adds: leather pouches
with stitched seams under darker flaps edged in gold.

Each box's UV region follows Minecraft's cube layout: for a box w x h x d at (u, v), the top face is at (u+d, v) and
the bottom at (u+d+w, v), both w x d; the four sides run along the row below, from (u, v+d), h tall. All the strap
boxes share one region of plain strap leather, and the buckles another of gold.

Usage:  python tools/textures/aerwhale_saddle.py
"""
import pathlib
import random

from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/textures/entity/aerwhale_saddle.png"

BLUE = (112, 170, 222)
BLUE_DARK = (78, 128, 186)
GOLD = (232, 197, 71)
GOLD_DARK = (176, 136, 40)
GOLD_LIGHT = (252, 230, 140)
LEATHER = (122, 74, 38)
LEATHER_LIGHT = (148, 96, 58)
STITCH = (199, 154, 100)
LEATHER_DARK = (94, 53, 24)
STRAP = (74, 44, 22)
STRAP_LIGHT = (92, 58, 32)

rng = random.Random(1487)
img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))


def jitter(color, amount=6):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, c + d)) for c in color) + (255,)


def fill(x0, y0, w, h, base, alt=None, alt_chance=0.0, amount=6):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            color = alt if alt is not None and rng.random() < alt_chance else base
            img.putpixel((x, y), jitter(color, amount))


def border(x0, y0, w, h, color):
    for x in range(x0, x0 + w):
        img.putpixel((x, y0), jitter(color, 4))
        img.putpixel((x, y0 + h - 1), jitter(color, 4))
    for y in range(y0, y0 + h):
        img.putpixel((x0, y), jitter(color, 4))
        img.putpixel((x0 + w - 1, y), jitter(color, 4))


def cube(u, v, w, h, d):
    """The faces of a box's UV region: (top, bottom, side row) as (x, y, w, h)."""
    return (u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, 2 * (d + w), h)


# Blanket, 18 x 1 x 16: blue cloth with a woven check, framed in gold; the one-pixel sides are all trim.
top, bottom, sides = cube(0, 0, 18, 1, 16)
fill(*top, BLUE, BLUE_DARK, 0.15)
x0, y0, w, h = top
for y in range(y0 + 2, y0 + h - 2):
    for x in range(x0 + 2, x0 + w - 2):
        if (x - x0 + y - y0) % 4 == 0:
            img.putpixel((x, y), jitter(BLUE_DARK, 4))
border(x0, y0, w, h, GOLD)
border(x0 + 1, y0 + 1, w - 2, h - 2, GOLD_DARK)
fill(*bottom, BLUE_DARK)
fill(*sides, GOLD, GOLD_DARK, 0.3)

# Seat, 10 x 2 x 10: leather with a stitched rim on top and a darker welt round the sides.
top, bottom, sides = cube(0, 17, 10, 2, 10)
fill(*top, LEATHER, LEATHER_LIGHT, 0.2)
x0, y0, w, h = top
for i in range(1, w - 1, 2):
    img.putpixel((x0 + i, y0 + 1), jitter(STITCH, 4))
    img.putpixel((x0 + i, y0 + h - 2), jitter(STITCH, 4))
for i in range(1, h - 1, 2):
    img.putpixel((x0 + 1, y0 + i), jitter(STITCH, 4))
    img.putpixel((x0 + w - 2, y0 + i), jitter(STITCH, 4))
fill(*bottom, LEATHER_DARK)
x0, y0, w, h = sides
fill(x0, y0, w, 1, LEATHER_LIGHT)
fill(x0, y0 + 1, w, 1, LEATHER_DARK)

# Cantle, 10 x 2 x 2: dark leather capped in gold.
top, bottom, sides = cube(40, 17, 10, 2, 2)
fill(*top, GOLD, GOLD_LIGHT, 0.3)
fill(*bottom, LEATHER_DARK)
x0, y0, w, h = sides
fill(x0, y0, w, 1, LEATHER_DARK, LEATHER, 0.3)
fill(x0, y0 + 1, w, 1, LEATHER_DARK)

# Pommel, 3 x 3 x 2: a gold horn, lighter at its cap.
top, bottom, sides = cube(40, 21, 3, 3, 2)
fill(*top, GOLD_LIGHT)
fill(*bottom, GOLD_DARK)
x0, y0, w, h = sides
fill(x0, y0, w, 1, GOLD_LIGHT, amount=4)
fill(x0, y0 + 1, w, h - 1, GOLD, GOLD_DARK, 0.35)

# Straps: every strap box samples this one region, so it's plain leather with faint grain, no edges to misalign.
fill(0, 32, 64, 22, STRAP, STRAP_LIGHT, 0.18, amount=4)

# Buckles: gold, with a dark pin across the middle.
fill(64, 32, 8, 6, GOLD, GOLD_LIGHT, 0.25)
for x in range(64, 72):
    img.putpixel((x, 35), jitter(GOLD_DARK, 4))

# Saddlebags, 3 x 8 x 7: leather pouches with a stitched seam round each broad side.
top, bottom, sides = cube(72, 0, 3, 8, 7)
fill(*top, LEATHER_DARK)
fill(*bottom, LEATHER_DARK)
fill(*sides, LEATHER, LEATHER_LIGHT, 0.2)
x0, y0, w, h = sides
# the broad faces are the 7-wide ones, at the start of the row and after the first 3-wide edge
for face_x in (x0, x0 + 7 + 3):
    for i in range(1, 7 - 1, 2):
        img.putpixel((face_x + i, y0 + 1), jitter(STITCH, 4))
        img.putpixel((face_x + i, y0 + h - 2), jitter(STITCH, 4))
    for i in range(2, h - 2, 2):
        img.putpixel((face_x + 1, y0 + i), jitter(STITCH, 4))
        img.putpixel((face_x + 5, y0 + i), jitter(STITCH, 4))

# Saddlebag flaps, 4 x 3 x 8: darker leather, their lower edge trimmed in gold.
top, bottom, sides = cube(72, 16, 4, 3, 8)
fill(*top, LEATHER_DARK, LEATHER, 0.15)
fill(*bottom, STRAP)
x0, y0, w, h = sides
fill(x0, y0, w, h - 1, LEATHER_DARK, LEATHER, 0.15)
fill(x0, y0 + h - 1, w, 1, GOLD, GOLD_DARK, 0.3)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(f"wrote {OUT}")
