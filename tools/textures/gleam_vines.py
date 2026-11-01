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

The vine blocks are then split in two, so the leaves can take the colour of the leaves the vine hangs from: a greyscale
<name>_leaves.png, which the game tints, and an untinted <name>_overlay.png of the stem and berries. Greyscale times
DEFAULT_TINT gives back the recolored leaves, which is what a vine under anything but leaves is tinted with.

Reads the textures from the Minecraft resources jar that ModDevGradle unpacks into build/moddev/artifacts, so run a
Gradle build (or IDE sync) first.

Usage:  python tools/textures/gleam_vines.py
        python tools/textures/gleam_vines.py --preview out.png    (also draws the vines under real leaves)

The tint maths (from_leaf) is mirrored by client/GleamVineTint.java; keep the constants in step.
"""
import colorsys
import io
import pathlib
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
JAR = next((ROOT / "build/moddev/artifacts").glob("*client-extra*.jar"))
OUT = ROOT / "src/main/resources/assets/altered_aether/textures"

# vanilla texture -> ours, then split into leaves and overlay. (item/gleamberries.png began as this recoloring of
# item/glow_berries and has since been touched up by hand, so it is not written here.)
BLOCKS = {
    "block/cave_vines": "block/gleam_vines",
    "block/cave_vines_lit": "block/gleam_vines_lit",
    "block/cave_vines_plant": "block/gleam_vines_plant",
    "block/cave_vines_plant_lit": "block/gleam_vines_plant_lit",
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

# --- tint maths, mirrored by GleamVineTint.java --------------------------------------------------------------------
# The mean grey of the leaves layer, so a tint of (leaf average / MEAN_GREY) puts the vine's average on the leaf's.
MEAN_GREY = 0.809
# Leaf-derived tints are held a little below the leaf's own brightness: the vines hang in the canopy's shade.
DARKEN = 0.88
# Pale leaves (bright and not very saturated) get this much more saturation, at most, or the vines wash out.
BOOST = 0.5
PALE_VALUE_FROM, PALE_VALUE_SPAN = 0.70, 0.30
PALE_SATURATION_FROM, PALE_SATURATION_SPAN = 0.45, 0.25


def clamp01(x):
    return max(0.0, min(1.0, x))


def from_leaf(avg):
    """The tint for vines hanging from leaves whose average colour is avg (0-255 RGB)."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in avg))
    paleness = clamp01((v - PALE_VALUE_FROM) / PALE_VALUE_SPAN) * clamp01((PALE_SATURATION_FROM - s) / PALE_SATURATION_SPAN)
    s = min(1.0, s * (1 + BOOST * paleness))
    boosted = colorsys.hsv_to_rgb(h, s, v)
    scale = DARKEN / MEAN_GREY
    tint = [c * scale * 255 for c in boosted]
    over = max(tint) / 255
    if over > 1:
        tint = [c / over for c in tint]
    return tuple(round(c) for c in tint)


# --- recoloring ----------------------------------------------------------------------------------------------------
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


def is_leaf(rgb):
    """Whether a recolored pixel is one of the leaves (the stems are brown, the berries magenta)."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    return 100 / 360 <= h <= 200 / 360 and s > 0.1


def luma(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def pixels(image):
    return [image.getpixel((x, y)) for y in range(image.height) for x in range(image.width)]


def split(recolored):
    """name -> image, into name -> (leaves, overlay, default tint, mean grey)."""
    shades = sorted({p[:3] for im in recolored.values() for p in pixels(im) if p[3] and is_leaf(p[:3])}, key=luma)
    top = luma(shades[-1])
    # each shade's grey is its luminance relative to the brightest, so the brightest leaf is pure white and the tint
    # carries the whole colour (luminance, not the max channel, keeps the two dark shades apart)
    grey = {c: round(255 * luma(c) / top) for c in shades}
    counts = {c: sum(1 for im in recolored.values() for p in pixels(im) if p[3] and p[:3] == c) for c in shades}
    # the default tint: least-squares fit of grey * tint to the shades, weighted by how often each occurs
    default = tuple(
        min(255, round(255 * sum(counts[c] * (grey[c] / 255) * c[i] / 255 for c in shades)
                       / sum(counts[c] * (grey[c] / 255) ** 2 for c in shades)))
        for i in range(3))
    mean_grey = sum(counts[c] * grey[c] for c in shades) / sum(counts.values()) / 255
    parts = {}
    for name, im in recolored.items():
        leaves = Image.new("RGBA", im.size, (0, 0, 0, 0))
        overlay = Image.new("RGBA", im.size, (0, 0, 0, 0))
        for y in range(im.height):
            for x in range(im.width):
                p = im.getpixel((x, y))
                if not p[3]:
                    continue
                if is_leaf(p[:3]):
                    g = grey[p[:3]]
                    leaves.putpixel((x, y), (g, g, g, p[3]))
                else:
                    overlay.putpixel((x, y), p)
        parts[name] = (leaves, overlay)
    return parts, default, mean_grey


# --- preview -------------------------------------------------------------------------------------------------------
def compose(leaves, overlay, tint):
    out = Image.new("RGBA", leaves.size, (0, 0, 0, 0))
    for y in range(leaves.height):
        for x in range(leaves.width):
            g = leaves.getpixel((x, y))
            if g[3]:
                out.putpixel((x, y), tuple(round(g[0] * t / 255) for t in tint) + (255,))
    out.alpha_composite(overlay)
    return out


def multiply(im, rgb):
    out = im.copy()
    out.putdata([(p[0] * rgb[0] // 255, p[1] * rgb[1] // 255, p[2] * rgb[2] // 255, p[3]) for p in pixels(im)])
    return out


def average(leaf):
    px = [p[:3] for p in pixels(leaf) if p[3] > 127]
    return tuple(sum(p[i] for p in px) / len(px) for i in range(3))


def preview(path, parts, default):
    libs = ROOT / "libs"
    aether = zipfile.ZipFile(next(libs.glob("aether-*.jar")))
    deep = zipfile.ZipFile(next((libs / "optional").glob("deep_aether-*.jar")))
    mc = zipfile.ZipFile(JAR)

    def load(jar, name, tint=None):
        im = Image.open(io.BytesIO(jar.read(name))).convert("RGBA").crop((0, 0, 16, 16))
        return multiply(im, tint) if tint else im

    a, d, m = "assets/aether/textures/block/natural/", "assets/deep_aether/textures/block/", "assets/minecraft/textures/block/"
    leaves = [
        ("skyroot", load(aether, a + "skyroot_leaves.png")),
        ("golden oak", load(aether, a + "golden_oak_leaves.png")),
        ("crystal", load(aether, a + "crystal_leaves.png")),
        ("holiday", load(aether, a + "holiday_leaves.png")),
        ("roseroot", load(deep, d + "roseroot_leaves.png")),
        ("blue roseroot", load(deep, d + "blue_roseroot_leaves.png")),
        ("yagroot", load(deep, d + "yagroot_leaves.png")),
        ("cruderoot", load(deep, d + "cruderoot_leaves.png")),
        ("conberry", load(deep, d + "conberry_leaves.png")),
        ("sunroot", load(deep, d + "sunroot_leaves.png")),
        ("aercloud", load(deep, d + "aercloud_leaves.png")),
        ("oak (plains)", load(mc, m + "oak_leaves.png", (0x77, 0xAB, 0x2F))),
        ("cherry", load(mc, m + "cherry_leaves.png")),
        ("azalea", load(mc, m + "azalea_leaves.png")),
    ]
    scale, pad, label = 6, 10, 30
    # columns: current look, then each leaf; rows: the leaf, then the plain tint (no darkening or boost) and the tuned one
    columns = [("default", None, default)] + [(n, leaf, None) for n, leaf in leaves]
    stack = ["gleam_vines_plant_lit", "gleam_vines_plant", "gleam_vines_lit"]
    rows = [("plain", lambda avg: tuple(min(255, round(c / MEAN_GREY)) for c in avg)), ("tuned", from_leaf)]
    cell = 16 * scale
    width = len(columns) * (cell + pad) + pad
    height = label + cell + len(rows) * len(stack) * cell + len(rows) * pad + pad
    sheet = Image.new("RGBA", (width, height), (38, 42, 56, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for i, (name, leaf, fixed) in enumerate(columns):
        x = pad + i * (cell + pad)
        draw.text((x, 4), name, fill=(230, 230, 230), font=font)
        y = label
        if leaf is not None:
            sheet.alpha_composite(leaf.resize((cell, cell), Image.NEAREST), (x, y))
        y += cell
        for row, (rowname, rule) in enumerate(rows):
            tint = fixed if fixed else rule(average(leaf))
            if i == 0:
                draw.text((2, y + 2), rowname, fill=(150, 150, 160), font=font)
            else:
                draw.text((x, y), f"#{tint[0]:02x}{tint[1]:02x}{tint[2]:02x}", fill=(150, 150, 160), font=font)
            y += pad
            for vine in stack:
                tile = compose(*parts["block/" + vine], tint)
                sheet.alpha_composite(tile.resize((cell, cell), Image.NEAREST), (x, y))
                y += cell
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print(f"wrote {path}")


def main():
    recolored = {}
    with zipfile.ZipFile(JAR) as jar:
        for source, target in BLOCKS.items():
            image = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{source}.png"))).convert("RGBA")
            image.putdata([recolor(p) for p in pixels(image)])
            recolored[target] = image
    parts, default, mean_grey = split(recolored)
    for target, (leaves, overlay) in parts.items():
        for suffix, image in (("leaves", leaves), ("overlay", overlay)):
            out = OUT / f"{target}_{suffix}.png"
            image.save(out)
            print(f"wrote {out}")
    print(f"default tint #{default[0]:02x}{default[1]:02x}{default[2]:02x}, mean leaf grey {mean_grey:.3f}")
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1], parts, default)


if __name__ == "__main__":
    main()
