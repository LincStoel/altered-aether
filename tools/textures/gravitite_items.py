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
PLACEHOLDER art for the gravitite rework, to be redrawn by hand before release.

Recolours other mods' sprites to gravitite's palette by brightness:

- raw_gravitite, crushed_gravitite and the raw block from Create's raw zinc, crushed raw zinc and raw zinc block
  (the ore's duller purple);
- the enchanted nugget from Create's zinc nugget, and the dusts from Oritech's iron dusts (enchanted purple / ore
  purple);
- the enchanted ingot is drawn here: a squat, near-cubic block in the same three-quarter view as Create's ingots.

The palettes are read from the Aether's own enchanted gravitite and gravitite ore textures.

The textures in HAND_DRAWN have been redrawn by hand, and this script leaves them alone.

Usage:  python tools/textures/gravitite_items.py   (needs the Create, Oritech and Aether jars in libs/ and libs/optional)
"""
import io
import pathlib
import zipfile

from PIL import Image

REPO = pathlib.Path(__file__).resolve().parents[2]
OUT = REPO / "src/main/resources/assets/altered_aether/textures"
HAND_DRAWN = {"item/enchanted_gravitite_ingot.png", "item/enchanted_gravitite_nugget.png", "item/gravitite_dust.png"}


def save(image, name):
    if name in HAND_DRAWN:
        print("leaving hand-drawn", name)
        return
    image.save(OUT / name)


def jar(prefix):
    for folder in (REPO / "libs", REPO / "libs/optional"):
        for path in sorted(folder.glob(prefix + "*.jar")):
            return zipfile.ZipFile(path)
    raise SystemExit("missing jar " + prefix)


def read(archive, name):
    return Image.open(io.BytesIO(archive.read(name))).convert("RGBA")


def luma(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def palette(image, purple_only):
    """The image's colours, darkest first; with purple_only, only its purple ones (the ore's flecks, not its stone)."""
    colours = set()
    for c in image.getdata():
        if c[3] == 0:
            continue
        if purple_only and not (c[0] > c[1] + 25 and c[2] > c[1] + 15):
            continue
        colours.add(c[:3])
    return sorted(colours, key=luma)


def recolour(image, colours):
    """Each pixel takes the palette colour at its brightness within the image's own range."""
    lit = [luma(c) for c in image.getdata() if c[3] > 0]
    low, high = min(lit), max(lit)
    out = Image.new("RGBA", image.size)
    for i, c in enumerate(image.getdata()):
        if c[3] == 0:
            continue
        t = 0.0 if high == low else (luma(c) - low) / (high - low)
        r, g, b = colours[min(len(colours) - 1, round(t * (len(colours) - 1)))]
        out.putpixel((i % image.width, i // image.width), (r, g, b, c[3]))
    return out


def shade(colours, t):
    return colours[min(len(colours) - 1, max(0, round(t * (len(colours) - 1))))] + (255,)


def cube(colours):
    """
    A squat cube of enchanted gravitite in three-quarter view: a light top, a mid left face and a dark right face,
    with a bright leading edge and a dark outline, like an ingot cut short.
    """
    rows = [
        "................",
        "................",
        "................",
        "......oTTo......",
        "....oTTTTTTo....",
        "..oTTTTTTTTTTo..",
        "..oLTTTTTTTTRo..",
        "..oLLLTTTTRRRo..",
        "..oLLLLEERRRRo..",
        "..oLLLLERRRRRo..",
        "..oLLLLERRRRRo..",
        "..oLLLLERRRRRo..",
        "...ooLLERRRoo...",
        ".....ooERoo.....",
        ".......oo.......",
        "................",
    ]
    key = {"o": 0.0, "T": 0.85, "L": 0.55, "R": 0.3, "E": 0.7}
    out = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in key:
                out.putpixel((x, y), shade(colours, key[ch]))
    # a glint on the top face
    out.putpixel((6, 4), shade(colours, 1.0))
    out.putpixel((7, 4), shade(colours, 1.0))
    out.putpixel((5, 5), shade(colours, 1.0))
    return out


def main():
    aether = jar("aether-")
    create = jar("create-1")
    oritech = jar("oritech-")
    enchanted = palette(read(aether, "assets/aether/textures/block/construction/enchanted_gravitite.png"), False)
    ore = palette(read(aether, "assets/aether/textures/block/natural/gravitite_ore.png"), True)
    (OUT / "item").mkdir(parents=True, exist_ok=True)
    (OUT / "block").mkdir(parents=True, exist_ok=True)

    item = "assets/create/textures/item/"
    save(recolour(read(create, item + "raw_zinc.png"), ore), "item/raw_gravitite.png")
    save(recolour(read(create, item + "crushed_raw_zinc.png"), ore), "item/crushed_gravitite.png")
    save(recolour(read(create, item + "zinc_nugget.png"), enchanted), "item/enchanted_gravitite_nugget.png")
    save(recolour(read(create, "assets/create/textures/block/raw_zinc_block.png"), ore), "block/raw_gravitite_block.png")
    dust = "assets/oritech/textures/item/"
    save(recolour(read(oritech, dust + "iron_dust.png"), ore), "item/gravitite_dust.png")
    save(recolour(read(oritech, dust + "small_iron_dust.png"), ore), "item/small_gravitite_dust.png")
    save(cube(enchanted), "item/enchanted_gravitite_ingot.png")


if __name__ == "__main__":
    main()
