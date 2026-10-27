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
Compares Altered Aether's biomes with the World Traveler 2 datapack they replace.

For each of the old pack's biomes, the matching biome here is assembled the way the game will see it: the biome file,
plus every biome modifier in this mod that adds features or spawns to it (directly, by list, or through
#aether:is_aether), minus what modifiers remove. Ids are mapped through feature_map.json, which also lists what was
dropped on purpose and why. The report shows, per biome:

  - climate and colour differences;
  - old features with no counterpart here (a parity gap, unless listed as dropped);
  - features here with no counterpart in the old pack (new work: fine, but listed);
  - spawn entries that differ.

Usage: python tools/parity/parity.py [--old <wt2_overrides/data>] [--strict]
With --strict the exit code is 1 if any unexplained gap remains.
"""
import argparse
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
GENERATED = ROOT / "src" / "generated" / "resources" / "data"
DEFAULT_OLD = pathlib.Path.home() / "curseforge/minecraft/Instances/World Traveler 2/config/openloader/packs/wt2_overrides/data"
STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures",
         "strongholds", "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration",
         "top_layer_modification"]
OURS = "altered_aether"


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def as_list(value):
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def aether_biomes():
    tag = GENERATED / "aether" / "tags" / "worldgen" / "biome" / "is_aether.json"
    ids = set()
    for entry in load(tag)["values"]:
        ids.add(entry["id"] if isinstance(entry, dict) else entry)
    return ids


def targets(biomes_field, biome, aether):
    for entry in as_list(biomes_field):
        if entry == biome or entry == "#aether:is_aether" and biome in aether:
            return True
    return False


def modifiers():
    folder = GENERATED / OURS / "neoforge" / "biome_modifier"
    for path in sorted(folder.rglob("*.json")):
        yield path.relative_to(folder).with_suffix("").as_posix(), load(path)


def assemble(name, aether):
    """Our biome as the game sees it: features per step, and spawns as (category, mob) -> (weight, min, max)."""
    biome = f"{OURS}:{name}"
    data = load(GENERATED / OURS / "worldgen" / "biome" / f"{name}.json")
    features = [list(step) for step in data["features"]] + [[] for _ in range(len(STEPS) - len(data["features"]))]
    conditional = set()
    removed = set()
    spawns = {}
    for category, entries in data["spawners"].items():
        for entry in entries:
            spawns[(category, entry["type"])] = (entry["weight"], entry["minCount"], entry["maxCount"])
    for modifier_id, modifier in modifiers():
        kind = modifier.get("type")
        if not targets(modifier.get("biomes"), biome, aether):
            continue
        conditions = [c.get("modid") for c in modifier.get("neoforge:conditions", [])]
        if kind == "neoforge:add_features":
            step = STEPS.index(modifier["step"])
            for feature in as_list(modifier["features"]):
                features[step].append(feature)
                if conditions:
                    conditional.add((feature, "+".join(conditions)))
        elif kind == "neoforge:remove_features":
            removed.update(as_list(modifier["features"]))
        elif kind in ("neoforge:add_spawns", f"{OURS}:configured_spawns"):
            if kind == "neoforge:add_spawns":
                for entry in as_list(modifier["spawners"]):
                    spawns[("modifier", entry["type"])] = (entry["weight"], entry["minCount"], entry["maxCount"])
            else:
                spawns[(modifier.get("category") or "default", modifier["mob"])] = (
                    modifier["weight"], modifier["min_count"], modifier["max_count"])
    features = [[f for f in step if f not in removed] for step in features]
    return data, features, conditional, spawns


def old_spawns(data):
    spawns = {}
    for category, entries in data["spawners"].items():
        for entry in entries:
            spawns[(category, entry["type"])] = (entry["weight"], entry["minCount"], entry["maxCount"])
    return spawns


def mapped(feature, mapping):
    """The id an old feature has here: an explicit mapping, or the same path under our namespace."""
    if feature in mapping["features"]:
        return as_list(mapping["features"][feature])
    if feature.startswith("world_traveler:"):
        return [OURS + ":" + feature.split(":", 1)[1]]
    return [feature]


def report(name, old_root, mapping, aether, out):
    old = load(old_root / "world_traveler" / "worldgen" / "biome" / f"{name}.json")
    ours, features, conditional, spawns = assemble(name, aether)
    gaps = 0
    out.append(f"== {name}")
    for key in ("temperature", "downfall", "has_precipitation"):
        if old.get(key) != ours.get(key):
            out.append(f"  climate {key}: old {old.get(key)} -> new {ours.get(key)}")
    for key, value in old.get("effects", {}).items():
        if isinstance(value, int) and ours["effects"].get(key) != value:
            out.append(f"  effect {key}: old {value} -> new {ours['effects'].get(key)}")
    present = {f for step in features for f in step}
    accounted = set()
    for step, old_step in enumerate(old["features"]):
        for feature in old_step:
            # a drop applies everywhere, or as "<biome>/<feature>" to one biome only
            reason = mapping["dropped"].get(feature) or mapping["dropped"].get(f"{name}/{feature}")
            if reason:
                out.append(f"  dropped  {feature}: {reason}")
                continue
            targets_here = mapped(feature, mapping)
            found = [t for t in targets_here if t in present]
            if not found:
                gaps += 1
                out.append(f"  MISSING  [{STEPS[step]}] {feature} (looked for {', '.join(targets_here)})")
            accounted.update(found)
    for step, new_step in enumerate(features):
        for feature in new_step:
            if feature not in accounted and feature not in mapping.get("global", []) \
                    and feature.startswith(OURS + ":"):
                note = next((f" (needs {mods})" for f, mods in conditional if f == feature), "")
                out.append(f"  new      [{STEPS[step]}] {feature}{note}")
    old_s, new_s = old_spawns(old), spawns
    new_by_mob = {mob.split(":")[-1]: value for (_, mob), value in new_s.items()}
    for (category, mob), value in sorted(old_s.items()):
        short = mob.split(":")[-1]
        if mob in mapping["dropped_spawns"]:
            out.append(f"  dropped  spawn {mob}: {mapping['dropped_spawns'][mob]}")
        elif short not in new_by_mob:
            gaps += 1
            out.append(f"  MISSING  spawn {category} {mob} {value}")
        elif new_by_mob[short] != value:
            out.append(f"  spawn    {mob}: old {value} -> new {new_by_mob[short]}")
    old_mobs = {mob.split(":")[-1] for _, mob in old_s}
    for (category, mob), value in sorted(new_s.items()):
        if mob.split(":")[-1] not in old_mobs:
            out.append(f"  new      spawn {category} {mob} {value}")
    return gaps


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--old", type=pathlib.Path, default=DEFAULT_OLD)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    if not (args.old / "world_traveler").is_dir():
        print(f"Old pack not found at {args.old}; pass --old <wt2_overrides/data>.")
        return 2
    mapping = load(pathlib.Path(__file__).with_name("feature_map.json"))
    aether = aether_biomes()
    out = []
    gaps = 0
    for path in sorted((args.old / "world_traveler" / "worldgen" / "biome").glob("*.json")):
        gaps += report(path.stem, args.old, mapping, aether, out)
    print("\n".join(out))
    print(f"\n{gaps} unexplained gap(s).")
    return 1 if args.strict and gaps else 0


if __name__ == "__main__":
    sys.exit(main())
