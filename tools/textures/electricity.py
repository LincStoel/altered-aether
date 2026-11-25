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
PLACEHOLDER textures for the Tempest Peaks electricity, to be redrawn by hand. The models lean on the Aether's own
aerogel, skyroot and the vanilla lightning rod for most of their look; these are only the new parts:

- block/aether_jar_charge_1..4: the glow inside a charged jar, a pale blue haze with a few sparks, brighter per step.
- block/storm_aercloud: a dark slate aercloud with forks of light running through it, 4 animated frames.
- block/squall_generator_spark: the crackle on a lit generator's bowl floor, 4 animated frames.
- item/static_boots: vanilla's leather boots in storm grey, a yellow zig-zag up each side.

Usage:  python tools/textures/electricity.py   (overwrites all of them)
"""
import io
import json
import pathlib
import random
import zipfile

from PIL import Image

REPO = pathlib.Path(__file__).resolve().parents[2]
ROOT = REPO / "src/main/resources/assets/altered_aether/textures"
VANILLA = REPO / "build/moddev/artifacts/neoforge-21.1.264-client-extra-aka-minecraft-resources.jar"

SPARK = (255, 244, 170)
HAZE = (150, 215, 255)


def save(img, path, frames=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    meta = path.with_suffix(".png.mcmeta")
    if frames:
        meta.write_text(json.dumps({"animation": {"frametime": frames}}, indent=2) + "\n")
    elif meta.exists():
        meta.unlink()


def jar_charge(step):
    """A haze filling more of the jar's middle the fuller it is, with sparks."""
    rnd = random.Random(100 + step)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    alpha = 60 + 40 * step
    for y in range(16):
        for x in range(16):
            # brighter in the middle column, where the rod runs
            d = abs(x - 7.5) / 7.5
            a = int(alpha * (1 - 0.6 * d) * (0.85 + 0.15 * rnd.random()))
            px[x, y] = HAZE + (max(0, min(255, a)),)
    for _ in range(2 + 2 * step):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = SPARK + (255,)
    return img


def fork(px, rnd, colour, w, h, x0=None):
    """A jagged bolt from the top of a w*h tile down to its bottom."""
    x = rnd.randrange(w) if x0 is None else x0
    for y in range(h):
        px[x % w, y] = colour + (255,)
        x += rnd.choice((-1, 0, 0, 1))


def storm_aercloud():
    frames = 4
    img = Image.new("RGBA", (16, 16 * frames))
    px = img.load()
    for f in range(frames):
        rnd = random.Random(7)
        for y in range(16):
            for x in range(16):
                v = 70 + int(25 * rnd.random())
                px[x, f * 16 + y] = (v - 10, v, v + 22, 200)
        # forks of light: one every frame, two in the last
        frnd = random.Random(31 + f)
        for _ in range(1 if f < frames - 1 else 2):
            tile = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            fork(tile.load(), frnd, SPARK, 16, 16)
            for y in range(16):
                for x in range(16):
                    if tile.getpixel((x, y))[3]:
                        px[x, f * 16 + y] = SPARK + (230,)
    return img


def generator_spark():
    frames = 4
    img = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    px = img.load()
    for f in range(frames):
        rnd = random.Random(50 + f)
        for _ in range(3):
            tile = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            fork(tile.load(), rnd, SPARK, 16, 16)
            for y in range(16):
                for x in range(16):
                    if tile.getpixel((x, y))[3]:
                        px[x, f * 16 + y] = SPARK + (255,)
                        # a soft blue edge
                        for dx, dy in ((1, 0), (-1, 0)):
                            nx = x + dx
                            if 0 <= nx < 16 and not px[nx, f * 16 + y][3]:
                                px[nx, f * 16 + y] = HAZE + (140,)
    return img


def static_boots():
    with zipfile.ZipFile(VANILLA) as jar:
        boots = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/item/leather_boots.png"))).convert("RGBA")
    px = boots.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a:
                # leather's grey base, tinted to storm slate
                v = (r + g + b) // 3
                px[x, y] = (int(v * 0.55), int(v * 0.62), int(v * 0.78), a)
    # a zig-zag up each boot
    for x, y in ((3, 12), (4, 11), (3, 10), (4, 9), (10, 12), (11, 11), (10, 10), (11, 9)):
        if px[x, y][3]:
            px[x, y] = SPARK + (255,)
    return boots


def main():
    for step in range(1, 5):
        save(jar_charge(step), ROOT / f"block/aether_jar_charge_{step}.png")
    save(storm_aercloud(), ROOT / "block/storm_aercloud.png", frames=3)
    save(generator_spark(), ROOT / "block/squall_generator_spark.png", frames=2)
    save(static_boots(), ROOT / "item/static_boots.png")


if __name__ == "__main__":
    main()
