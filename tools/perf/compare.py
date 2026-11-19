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
"""Compares two PerfProbe runs (see docs/worldgen-performance.md).

    python -I tools/perf/compare.py <tag A> <tag B> [--dir run-server/perf] [--watch block.aether.cold_aercloud,...]

Reads <dir>/<tag>-fingerprint.txt and <dir>/<tag>-chunks.txt for both tags, when present.

- Fingerprints must be equal for a change meant to leave the terrain alone. The per-area hashes show where they
  differ.
- Chunk hashes always differ in some chunks: decorations that cross chunk borders depend on the order chunks
  generate in. The block counts are what to read. A change is clean when the blocks it touches (--watch) are equal in
  every chunk, and the other differences look like the usual noise (leaves, grass, ores, the odd flowing water).
"""
import argparse
import collections
import os
import sys


def fingerprint(directory, tag):
    path = os.path.join(directory, f"{tag}-fingerprint.txt")
    if not os.path.exists(path):
        return None
    line = open(path, encoding="utf-8").read().strip()
    fields = dict(part.split("=", 1) for part in line.split(" areas:")[0].split() if "=" in part)
    areas = dict(area.split("=") for area in line.split(" areas:")[1].split()) if " areas:" in line else {}
    return fields.get("hash"), areas


def chunks(directory, tag):
    path = os.path.join(directory, f"{tag}-chunks.txt")
    if not os.path.exists(path):
        return None
    result = {}
    for line in open(path, encoding="utf-8"):
        x, z, digest, rest = line.split(" ", 3)
        rest = rest.strip()
        counts = dict(kv.split("=") for kv in rest.strip("{}").split(", ")) if rest != "{}" else {}
        result[(int(x), int(z))] = (digest, {k: int(v) for k, v in counts.items()})
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("a")
    parser.add_argument("b")
    parser.add_argument("--dir", default=os.path.join("run-server", "perf"))
    parser.add_argument("--watch", default="", help="comma-separated block ids that must match in every chunk")
    args = parser.parse_args()
    clean = True

    fa, fb = fingerprint(args.dir, args.a), fingerprint(args.dir, args.b)
    if fa and fb:
        if fa[0] == fb[0]:
            print(f"fingerprint: equal ({fa[0]})")
        else:
            clean = False
            print(f"fingerprint: DIFFERENT ({fa[0]} vs {fb[0]})")
            for area in sorted(set(fa[1]) | set(fb[1])):
                if fa[1].get(area) != fb[1].get(area):
                    print(f"  area {area}: {fa[1].get(area)} vs {fb[1].get(area)}")
    else:
        print("fingerprint: missing for one side")

    ca, cb = chunks(args.dir, args.a), chunks(args.dir, args.b)
    if ca and cb:
        shared = [k for k in ca if k in cb]
        differing = [k for k in shared if ca[k][0] != cb[k][0]]
        deltas = collections.Counter()
        for k in differing:
            for block in set(ca[k][1]) | set(cb[k][1]):
                delta = cb[k][1].get(block, 0) - ca[k][1].get(block, 0)
                if delta:
                    deltas[block] += abs(delta)
        print(f"chunks: {len(differing)} of {len(shared)} differ")
        for block, total in deltas.most_common(15):
            print(f"  {total:8d}  {block}")
        for block in [b for b in args.watch.split(",") if b]:
            off = [k for k in shared if ca[k][1].get(block, 0) != cb[k][1].get(block, 0)]
            total_a = sum(c[1].get(block, 0) for c in ca.values())
            total_b = sum(c[1].get(block, 0) for c in cb.values())
            status = "equal in every chunk" if not off else f"DIFFERS in {len(off)} chunks, e.g. {off[:5]}"
            print(f"watch {block}: {total_a} vs {total_b}, {status}")
            clean &= not off
    else:
        print("chunks: missing for one side")
    sys.exit(0 if clean else 1)


if __name__ == "__main__":
    main()
