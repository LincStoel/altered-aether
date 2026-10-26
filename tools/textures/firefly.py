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
Draws the firefly particle (8x8): a white-hot core fading through a soft halo to nothing at the rim. It is white so
client/FireflyParticle.java can tint each firefly (mostly ambrosium gold, now and then pale blue or green); the halo's
alpha does the glowing, the core stays opaque so the mote reads at a distance.

Usage:  python tools/textures/firefly.py
"""
import math
import pathlib

from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/textures/particle/firefly.png"
SIZE = 8


def main():
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    centre = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - centre, y - centre) / (SIZE / 2)
            if d >= 1:
                continue
            # Opaque core, then a halo falling off with the square of the distance.
            alpha = 255 if d < 0.3 else int(255 * (1 - (d - 0.3) / 0.7) ** 2)
            image.putpixel((x, y), (255, 255, 255, alpha))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
