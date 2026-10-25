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
"""Writes the empty structure the feature GameTests run in: src/main/resources/data/altered_aether/structure/arena.nbt."""
import gzip
import pathlib
import struct

SIZE = (48, 40, 48)
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


root = tag(10, "", compound(
    tag(3, "DataVersion", struct.pack(">i", DATA_VERSION)),
    tag(9, "size", int_list(SIZE)),
    tag(9, "palette", compound_list([compound(tag(8, "Name", name("minecraft:air")))])),
    tag(9, "blocks", empty_list()),
    tag(9, "entities", empty_list()),
))
out = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/data/altered_aether/structure/arena.nbt"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(gzip.compress(root))
print(out)
