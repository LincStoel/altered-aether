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
Draws the gravitite golem's placeholder textures, until its Blockbench model comes with its own:

- textures/entity/mobs/gravitite_golem/gravitite_golem.png (64x64), laid out for the boxes in
  client/golem/GravititeGolemModel.java: a holystone-grey body and limbs, a big head of enchanted gravitite with
  darker veins and pale eyes;
- gravitite_golem_eyes.png, the eyes alone, drawn glowing by the renderer;
- textures/item/storage_pebble.png and storage_pebble_dull.png (16x16): a chip of gravitite, and the same gone grey
  once its golem is gone.

Each box's UV region follows Minecraft's cube layout: for a box w x h x d at (u, v), the top face is at (u+d, v), the
bottom at (u+d+w, v), both w x d; the sides run along the row below from (u, v+d), h tall: right, front, left, back.
The pixels are drawn here, not copied from the Aether.

Usage:  python tools/textures/gravitite_golem.py
"""
import pathlib
import random

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/textures"
ENTITY = ROOT / "entity/mobs/gravitite_golem"
ITEM = ROOT / "item"

STONE = [(0x8A, 0x8F, 0x94), (0x9D, 0xA3, 0xA8), (0x7A, 0x7F, 0x84), (0x92, 0x97, 0x9C)]
STONE_DARK = (0x66, 0x6B, 0x70)
GRAV = [(0xBD, 0x42, 0xB2), (0xCF, 0x61, 0xC5), (0xAD, 0x2A, 0xA2), (0xBD, 0x42, 0xB2)]
GRAV_LIGHT = (0xE0, 0x72, 0xD7)
GRAV_DARK = (0x94, 0x22, 0x8A)
GRAV_DEEP = (0x87, 0x1F, 0x7E)
EYE = (0xF8, 0xDC, 0xF4)

rng = random.Random(4242)


def fill(img, x, y, w, h, palette):
    for i in range(w):
        for j in range(h):
            img.putpixel((x + i, y + j), palette[rng.randrange(len(palette))] + (255,))


def box(img, u, v, w, h, d, palette):
    """All six faces of a w x h x d box at (u, v)."""
    fill(img, u + d, v, w, d, palette)
    fill(img, u + d + w, v, w, d, palette)
    fill(img, u, v + d, d + w + d + w, h, palette)


def veins(img, x, y, w, h, count):
    for _ in range(count):
        px, py = x + rng.randrange(w), y + rng.randrange(h)
        for _ in range(rng.randrange(2, 5)):
            if x <= px < x + w and y <= py < y + h:
                img.putpixel((px, py), GRAV_DARK + (255,))
            px += rng.choice((-1, 0, 1))
            py += 1


def main():
    golem = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # head 9x9x9 at (0, 0): gravitite with veins, lighter on top
    box(golem, 0, 0, 9, 9, 9, GRAV)
    fill(golem, 9, 0, 9, 9, GRAV + [GRAV_LIGHT])
    veins(golem, 0, 9, 36, 9, 14)
    # the face: two pale eyes and a dark brow line; the front face is at (9, 9)
    fx, fy = 9, 9
    for x in range(fx + 1, fx + 8):
        golem.putpixel((x, fy + 3), GRAV_DEEP + (255,))
    for ex in (fx + 2, fx + 5):
        for dx in range(2):
            # dark in the base texture, so the eyes go out when redstone makes the golem rest; the glow layer lights them
            golem.putpixel((ex + dx, fy + 4), (0x4A, 0x10, 0x44, 255))
            eyes.putpixel((ex + dx, fy + 4), EYE + (255,))
            golem.putpixel((ex + dx, fy + 5), GRAV_DEEP + (255,))
    # a small mouth, pressed shut in concentration
    for x in range(fx + 3, fx + 6):
        golem.putpixel((x, fy + 7), GRAV_DEEP + (255,))

    # body 5x7x3 at (0, 28): holystone with a gravitite seam down the front
    box(golem, 0, 28, 5, 7, 3, STONE)
    for y in range(31, 38):
        golem.putpixel((5, y), GRAV_DARK + (255,))
    for x in range(3, 8):
        golem.putpixel((x, 37), STONE_DARK + (255,))

    # legs 2x7x2 at (0, 40) and (8, 40); arms 1x7x1 at (16, 40) and (20, 40)
    for u in (0, 8):
        box(golem, u, 40, 2, 7, 2, STONE)
        for x in range(u, u + 8):
            golem.putpixel((x, 48), STONE_DARK + (255,))
    for u in (16, 20):
        box(golem, u, 40, 1, 7, 1, STONE)
        for x in range(u, u + 4):
            golem.putpixel((x, 47), GRAV_LIGHT + (255,))

    ENTITY.mkdir(parents=True, exist_ok=True)
    golem.save(ENTITY / "gravitite_golem.png")
    eyes.save(ENTITY / "gravitite_golem_eyes.png")

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
    print("wrote", ENTITY, "and", ITEM)


if __name__ == "__main__":
    main()
