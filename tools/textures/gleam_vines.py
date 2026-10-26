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
Recolors vanilla's cave vines and glow berries into gleam vines and gleamberries: the leaves turned to the cool
blue-green of the Aether's grass, the berries to a magenta ramp around #cf0484, and the brown stems left as they are.

The berries are mapped colour by colour, so each vanilla shade keeps its place in the ramp (outline, shade, body,
highlights); the leaves are shifted as a band of hues, keeping their lightness.

Reads the textures from the Minecraft resources jar that ModDevGradle unpacks into build/moddev/artifacts, so run a
Gradle build (or IDE sync) first.

Usage:  python tools/textures/gleam_vines.py
"""
import colorsys
import io
import pathlib
import zipfile

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
JAR = next((ROOT / "build/moddev/artifacts").glob("*client-extra*.jar"))
OUT = ROOT / "src/main/resources/assets/altered_aether/textures"

# vanilla texture -> ours
TEXTURES = {
    "block/cave_vines": "block/gleam_vines",
    "block/cave_vines_lit": "block/gleam_vines_lit",
    "block/cave_vines_plant": "block/gleam_vines_plant",
    "block/cave_vines_plant_lit": "block/gleam_vines_plant_lit",
    "item/glow_berries": "item/gleamberries",
}

# every orange and yellow of the glow berries, lightest to darkest, onto the magenta ramp
BERRIES = {
    0xF7E26B: 0xFFB8E8,
    0xF4C05E: 0xFF7ACF,
    0xF19645: 0xE81E9B,
    0xEB8931: 0xCF0484,
    0xD57219: 0xB8037A,
    0xBF6717: 0xA1026C,
    0xA46422: 0x8A025D,
    0x92441A: 0x6B0148,
}

# the Aether grass's hue, and how much of vanilla's saturation the leaves keep
LEAF_HUE = 150 / 360
LEAF_SATURATION = 0.72


def recolor(pixel):
    r, g, b, a = pixel
    if a == 0:
        return pixel
    rgb = (r << 16) | (g << 8) | b
    if rgb in BERRIES:
        mapped = BERRIES[rgb]
        return (mapped >> 16, (mapped >> 8) & 0xFF, mapped & 0xFF, a)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    # the leaves run from yellow-green (~60) to green (~125); the stems are browns below that, left alone
    if 55 / 360 <= h <= 160 / 360:
        # keep a little of the original spread of hues, so the leaves don't flatten to one colour
        h = LEAF_HUE + (h - 95 / 360) * 0.25
        s *= LEAF_SATURATION
        r, g, b = (round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))
    return (r, g, b, a)


def main():
    with zipfile.ZipFile(JAR) as jar:
        for source, target in TEXTURES.items():
            image = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{source}.png"))).convert("RGBA")
            image.putdata([recolor(p) for p in image.getdata()])
            out = OUT / f"{target}.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            image.save(out)
            print(f"wrote {out}")


if __name__ == "__main__":
    main()
