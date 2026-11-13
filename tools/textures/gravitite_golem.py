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
Draws the gravitite golem's pebble. The golem's own texture (gravitite_golem.png) comes from Blockbench with its
model, and gravitite_golem_glow.py derives its glow layers from it; this draws:

- textures/item/storage_pebble.png and storage_pebble_dull.png (16x16): a chip of gravitite, and the same gone grey
  once its golem is gone.

The pixels are drawn here, not copied from the Aether.

Usage:  python tools/textures/gravitite_golem.py
"""
import pathlib
import random

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/textures"
ITEM = ROOT / "item"

STONE = [(0x8A, 0x8F, 0x94), (0x9D, 0xA3, 0xA8), (0x7A, 0x7F, 0x84), (0x92, 0x97, 0x9C)]
STONE_DARK = (0x66, 0x6B, 0x70)
GRAV = [(0xBD, 0x42, 0xB2), (0xCF, 0x61, 0xC5), (0xAD, 0x2A, 0xA2), (0xBD, 0x42, 0xB2)]
GRAV_LIGHT = (0xE0, 0x72, 0xD7)
GRAV_DARK = (0x94, 0x22, 0x8A)
GRAV_DEEP = (0x87, 0x1F, 0x7E)

rng = random.Random(4242)


def main():
    # the pebble: a rounded chip of gravitite with a highlight and a dark underside
    shape = [
        "................",
        "................",
        "................",
        "......####......",
        "....########....",
        "...##########...",
        "..############..",
        "..############..",
        "..############..",
        "...##########...",
        "....########....",
        ".....######.....",
        "................",
        "................",
        "................",
        "................",
    ]
    for name, light, mid, dark, deep in (
            ("storage_pebble", GRAV_LIGHT, GRAV[0], GRAV_DARK, GRAV_DEEP),
            ("storage_pebble_dull", (0xA8, 0x9F, 0xA6), (0x8C, 0x84, 0x8A), (0x6E, 0x67, 0x6C), (0x58, 0x52, 0x57))):
        pebble = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch != "#":
                    continue
                edge = shape[y + 1][x] != "#" or x + 1 < 16 and shape[y][x + 1] != "#"
                top = shape[y - 1][x] != "#" or shape[y][x - 1] != "#"
                color = deep if edge else light if top else mid
                if not edge and not top and rng.random() < 0.15:
                    color = dark
                pebble.putpixel((x, y), color + (255,))
        # a glint
        pebble.putpixel((5, 5), (0xFF, 0xF0, 0xFC, 255) if name == "storage_pebble" else light + (255,))
        ITEM.mkdir(parents=True, exist_ok=True)
        pebble.save(ITEM / (name + ".png"))
    print("wrote", ITEM)


if __name__ == "__main__":
    main()
