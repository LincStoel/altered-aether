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
PLACEHOLDER textures for Essence Channels and Wellsprings, to be redrawn by hand:

- block/essence_capstone_<gem>: a capstone's side, smooth celezite with a two-pixel frame band top and bottom, two
  carved rings (the outer spanning pixels 2-13, the inner 4-11) round a 2x2 gem spot at 7-8: dark when empty, else the
  gem's colour (none, zanite, skyjade, gravitite, ambrosium, other).
- block/essence_capstone_end: its top and bottom, smooth celezite in a square frame.
- block/essence_plinth_top, block/essence_plinth_side: the plinth's celezite body, bevelled like the golem tablet's
  slab, without glyphs (its frame is polished nadirite).
- block/essence_orb: the orb's breaking particles, a soft white glow.
- entity/essence_orb: the orb the renderer draws, a soft white disc; entity/essence_ring: a ring band's cross-section.
- particle/essence_note_0..3: twinkling notes of light; particle/essence_mote_0..1: the motes.
- item/quintessence_phial: a glass bottle of white light.
- item/dowsing_rod: a forked skyroot rod with an ambrosium bead at the fork; item/dowsing_rod_glow_1..3: its icy blue
  glow, drawn full bright over it, stronger at each step.

Usage:  python tools/textures/essence.py   (overwrites all of them)
"""
import io
import math
import pathlib
import random
import zipfile

from PIL import Image

REPO = pathlib.Path(__file__).resolve().parents[2]
ROOT = REPO / "src/main/resources/assets/altered_aether/textures"
VANILLA = REPO / "build/moddev/artifacts/neoforge-21.1.264-client-extra-aka-minecraft-resources.jar"
AETHER = REPO / "libs/aether-1.21.1-1.5.10-neoforge.jar"

# smooth celezite, from polished_celezite's face
FACE = [(249, 215, 220), (248, 214, 219), (249, 217, 221), (248, 211, 217)]
LIGHT = (255, 240, 244)
SHADE = (214, 182, 188)
GROOVE = (193, 163, 168)
DEEP = (168, 140, 146)
GEMS = {
    "none": ((70, 52, 60), (52, 38, 45)),
    "zanite": ((178, 128, 246), (116, 62, 196)),
    "skyjade": ((150, 236, 182), (64, 170, 112)),
    "gravitite": ((246, 150, 236), (188, 76, 186)),
    "ambrosium": ((255, 246, 150), (236, 196, 64)),
    "other": ((236, 244, 255), (170, 190, 230)),
}
WHITE = (255, 255, 255)


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def read(jar, name):
    with zipfile.ZipFile(jar) as archive:
        return Image.open(io.BytesIO(archive.read(name))).convert("RGBA")


def smooth(seed):
    """Smooth celezite: its face colours, lightly speckled."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), FACE[rnd.randrange(len(FACE))] + (255,))
    return img


def ring(img, inner, outer):
    """A carved ring between radii inner and outer about the middle: a groove lit from the top left."""
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if inner <= d < outer:
                # the side of the groove facing the light is lighter
                lit = (x - 7.5) + (y - 7.5) > 0
                img.putpixel((x, y), (GROOVE if lit else DEEP) + (255,))


def capstone_side(gem):
    img = smooth(7)
    for x in range(16):
        img.putpixel((x, 0), LIGHT + (255,))
        img.putpixel((x, 1), GROOVE + (255,))
        img.putpixel((x, 14), LIGHT + (255,))
        img.putpixel((x, 15), GROOVE + (255,))
    ring(img, 5.05, 6.05)
    ring(img, 3.0, 3.9)
    light, dark = GEMS[gem]
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        img.putpixel((x, y), dark + (255,))
    img.putpixel((7, 7), light + (255,))
    return img


def capstone_end():
    img = smooth(11)
    for i in range(16):
        for (x, y) in ((i, 0), (0, i)):
            img.putpixel((x, y), LIGHT + (255,))
        for (x, y) in ((i, 15), (15, i)):
            img.putpixel((x, y), GROOVE + (255,))
    for i in range(2, 14):
        for (x, y) in ((i, 2), (2, i)):
            img.putpixel((x, y), GROOVE + (255,))
        for (x, y) in ((i, 13), (13, i)):
            img.putpixel((x, y), LIGHT + (255,))
    return img


def plinth_top():
    """The body's top, sampled at pixels 2-13: bevelled at its edge like the tablet's slab."""
    img = smooth(13)
    for i in range(2, 14):
        for (x, y) in ((i, 2), (2, i)):
            img.putpixel((x, y), LIGHT + (255,))
        for (x, y) in ((i, 13), (13, i)):
            img.putpixel((x, y), GROOVE + (255,))
    for i in range(4, 12):
        for (x, y) in ((i, 4), (4, i)):
            img.putpixel((x, y), SHADE + (255,))
        for (x, y) in ((i, 11), (11, i)):
            img.putpixel((x, y), LIGHT + (255,))
    return img


def plinth_side():
    """The body's sides, sampled at pixels 2-13 across and 3-15 down: lit along the top edge, shaded to the foot."""
    img = smooth(17)
    for x in range(16):
        img.putpixel((x, 3), LIGHT + (255,))
        img.putpixel((x, 4), SHADE + (255,))
        img.putpixel((x, 15), GROOVE + (255,))
    for y in range(3, 16):
        img.putpixel((2, y), LIGHT + (255,))
        img.putpixel((13, y), GROOVE + (255,))
    return img


def glow(size, falloff, core=0.0):
    """A soft white disc: opaque white at the middle fading out to the edge."""
    img = Image.new("RGBA", (size, size))
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / (size / 2)
            a = max(0.0, 1 - d) ** falloff
            if d < core:
                a = 1.0
            img.putpixel((x, y), WHITE + (int(255 * min(1.0, a)),))
    return img


def ring_band():
    """Across a ring's band: bright in the middle, soft at both edges (16 long, so it tiles along the ring)."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        a = math.sin(math.pi * (y + 0.5) / 16) ** 1.5
        for x in range(16):
            img.putpixel((x, y), WHITE + (int(255 * a),))
    return img


def note(frame):
    """A twinkling note: a small four-pointed star whose arms grow and shrink, 8x8."""
    img = Image.new("RGBA", (8, 8))
    arm = [1, 2, 3, 2][frame]
    for i in range(-arm, arm + 1):
        a = int(255 * (1 - abs(i) / (arm + 1)))
        for (x, y) in ((3 + i, 3), (3, 3 + i), (4 + i, 4), (4, 4 + i)):
            if 0 <= x < 8 and 0 <= y < 8:
                old = img.getpixel((x, y))[3]
                img.putpixel((x, y), WHITE + (max(old, a),))
    for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
        img.putpixel((x, y), WHITE + (255,))
    return img


def mote(frame):
    disc = glow(8, 1.6 if frame == 0 else 2.2, 0.15)
    return disc


def phial():
    bottle = read(VANILLA, "assets/minecraft/textures/item/potion.png")
    liquid = read(VANILLA, "assets/minecraft/textures/item/potion_overlay.png")
    img = Image.new("RGBA", (16, 16))
    rnd = random.Random(5)
    for y in range(16):
        for x in range(16):
            r, g, b, a = liquid.getpixel((x, y))
            if a:
                shade = r / 255
                tone = (int(214 + 41 * shade), int(222 + 33 * shade), 255)
                if rnd.random() < 0.12:
                    tone = WHITE
                img.putpixel((x, y), tone + (255,))
    img.alpha_composite(bottle)
    return img


def line(img, x0, y0, x1, y1, colours):
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), colours[i % len(colours)] + (255,))


ROD_HAND, ROD_FORK, ROD_TIPS = (2, 13), (8, 8), ((12, 2), (14, 5))
ICE = [(214, 246, 255), (150, 220, 255), (96, 186, 250)]


def dowsing_rod():
    stick = read(AETHER, "assets/aether/textures/item/materials/skyroot_stick.png")
    palette = sorted({p[:3] for p in stick.get_flattened_data() if p[3]}, key=sum)
    wood = [palette[-2], palette[-1], palette[-3]]
    img = Image.new("RGBA", (16, 16))
    # a Y: the stem from the fork down to the holder's hand, two prongs on up
    line(img, *ROD_HAND, *ROD_FORK, wood)
    for tip in ROD_TIPS:
        line(img, *ROD_FORK, *tip, wood)
    bead = (255, 236, 120)
    fx, fy = ROD_FORK
    for (x, y) in ((fx, fy), (fx + 1, fy), (fx, fy - 1)):
        img.putpixel((x, y), bead + (255,))
    return img


def dowsing_glow(level):
    """The rod's glow at level 1 to 3: the prongs and fork lit icy blue, brightest at the tips, in a soft fringe."""
    rod = dowsing_rod()
    img = Image.new("RGBA", (16, 16))
    strength = (0.0, 0.4, 0.7, 1.0)[level]
    hx, hy = ROD_HAND
    reach = math.hypot(14 - hx, 2 - hy)
    for y in range(16):
        for x in range(16):
            # stronger toward the prongs' tips than at the hand
            along = 0.35 + 0.65 * min(1.0, math.hypot(x - hx, y - hy) / reach)
            if rod.getpixel((x, y))[3]:
                tone = ICE[0] if along > 0.8 else ICE[1]
                img.putpixel((x, y), tone + (int(255 * min(1.0, strength * (0.55 + 0.45 * along))),))
                continue
            near = min((math.hypot(x - u, y - v) for v in range(16) for u in range(16) if rod.getpixel((u, v))[3]))
            if near <= 1.5 and level > 1 or near <= 1.0:
                fringe = (1.0 if near <= 1.0 else 0.45) * strength * along * 0.55
                img.putpixel((x, y), ICE[2] + (int(255 * fringe),))
    return img


def main():
    block, entity, particle, item = ROOT / "block", ROOT / "entity", ROOT / "particle", ROOT / "item"
    for gem in GEMS:
        save(capstone_side(gem), block / f"essence_capstone_{gem}.png")
    save(capstone_end(), block / "essence_capstone_end.png")
    save(plinth_top(), block / "essence_plinth_top.png")
    save(plinth_side(), block / "essence_plinth_side.png")
    save(glow(16, 1.4, 0.2), block / "essence_orb.png")
    save(glow(32, 1.8, 0.18), entity / "essence_orb.png")
    save(ring_band(), entity / "essence_ring.png")
    for frame in range(4):
        save(note(frame), particle / f"essence_note_{frame}.png")
    for frame in range(2):
        save(mote(frame), particle / f"essence_mote_{frame}.png")
    save(phial(), item / "quintessence_phial.png")
    save(dowsing_rod(), item / "dowsing_rod.png")
    for level in range(1, 4):
        save(dowsing_glow(level), item / f"dowsing_rod_glow_{level}.png")


if __name__ == "__main__":
    main()
