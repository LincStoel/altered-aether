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
Writes the Aerogel Wheel's Ponder schematic (see compat/create/WheelPonder):
src/main/resources/assets/altered_aether/ponder/aerogel_wheel.nbt.

A 7x5x7 scene on a holystone-brick plate: Aether Jars at (1,1,3) and (5,1,3) with lightning rods on them, the wheel at
(3,2,3) between the rod tips, and a shaft up to a millstone over it, for the wheel to turn.
"""
import gzip
import pathlib
import struct

SIZE = (7, 5, 7)
DATA_VERSION = 3955  # Minecraft 1.21.1


def name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag(kind, key, payload):
    return bytes([kind]) + name(key) + payload


def compound(*entries):
    return b"".join(entries) + b"\x00"


def int_list(values):
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(items):
    return bytes([10]) + struct.pack(">i", len(items)) + b"".join(items)


def empty_list():
    return bytes([0]) + struct.pack(">i", 0)


def state(block, **properties):
    entries = [tag(8, "Name", name(block))]
    if properties:
        entries.append(tag(10, "Properties", compound(*(tag(8, k, name(v)) for k, v in sorted(properties.items())))))
    return compound(*entries)


PALETTE = [
    state("minecraft:air"),
    state("aether:holystone_bricks"),
    state("altered_aether:aether_jar", charge="0"),
    state("minecraft:lightning_rod", facing="up", powered="false", waterlogged="false"),
    state("altered_aether:aerogel_wheel"),
    state("create:shaft", axis="y", waterlogged="false"),
    state("create:millstone"),
]
AIR, PLATE, JAR, ROD, WHEEL, SHAFT, MILLSTONE = range(len(PALETTE))

blocks = {}
for x in range(SIZE[0]):
    for z in range(SIZE[2]):
        blocks[(x, 0, z)] = PLATE
for x in (1, 5):
    blocks[(x, 1, 3)] = JAR
    blocks[(x, 2, 3)] = ROD
blocks[(3, 2, 3)] = WHEEL
blocks[(3, 3, 3)] = SHAFT
blocks[(3, 4, 3)] = MILLSTONE

entries = [compound(tag(9, "pos", int_list(list(pos))), tag(3, "state", struct.pack(">i", index)))
           for pos, index in sorted(blocks.items())]
root = tag(10, "", compound(
    tag(3, "DataVersion", struct.pack(">i", DATA_VERSION)),
    tag(9, "size", int_list(list(SIZE))),
    tag(9, "palette", compound_list(PALETTE)),
    tag(9, "blocks", compound_list(entries)),
    tag(9, "entities", empty_list()),
))
out = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/assets/altered_aether/ponder/aerogel_wheel.nbt"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(gzip.compress(root))
print(out)
