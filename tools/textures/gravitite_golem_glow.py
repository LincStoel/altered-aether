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
Derives the gravitite golem's glow layers from its Blockbench texture (gravitite_golem.png), so they follow any repaint:

- gravitite_golem_glow.png: every purple (gravitite) pixel, unchanged; the renderer draws it fullbright over the body;
- gravitite_golem_nose_glow.png: the nose's purple pixels at NOSE_BOOST strength; the renderer adds it on top, so the
  nose shines brighter than the rest.

A pixel counts as purple when its red and blue both stand clear of its green; the stone is plain grey.

Usage:  python tools/textures/gravitite_golem_glow.py
"""
import pathlib

from PIL import Image

ENTITY = (pathlib.Path(__file__).resolve().parents[2]
          / "src/main/resources/assets/altered_aether/textures/entity/mobs/gravitite_golem")

PURPLE_MARGIN = 15
# the nose box is 6 x 8 x 3 at (55, 29), so its faces fill u 55..72, v 29..39
NOSE = (55, 29, 73, 40)
# the additive pass adds this share of each nose pixel's colour; higher washes it toward white
NOSE_BOOST = 0.55


def purple(r, g, b):
    return r - g > PURPLE_MARGIN and b - g > PURPLE_MARGIN


def main():
    base = Image.open(ENTITY / "gravitite_golem.png").convert("RGBA")
    glow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    nose = Image.new("RGBA", base.size, (0, 0, 0, 0))
    count = 0
    for y in range(base.height):
        for x in range(base.width):
            r, g, b, a = base.getpixel((x, y))
            if a == 0 or not purple(r, g, b):
                continue
            count += 1
            glow.putpixel((x, y), (r, g, b, a))
            if NOSE[0] <= x < NOSE[2] and NOSE[1] <= y < NOSE[3]:
                # additive blending ignores alpha, so the strength goes in the colour
                nose.putpixel((x, y), (round(r * NOSE_BOOST), round(g * NOSE_BOOST), round(b * NOSE_BOOST), 255))
    glow.save(ENTITY / "gravitite_golem_glow.png")
    nose.save(ENTITY / "gravitite_golem_nose_glow.png")
    print("wrote", count, "glowing pixels to", ENTITY)


if __name__ == "__main__":
    main()
