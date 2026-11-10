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
Turns a Blockbench project (.bbmodel, Bedrock or Modded Entity format, box UV) into the Java a HierarchicalModel
needs: the parts of its LayerDefinition, and its animations as vanilla AnimationDefinitions. The code is written into
the given Java files between marker comments, so the hand-written code around it survives a re-run:

    // BEGIN GENERATED MODEL ... // END GENERATED MODEL              (statements inside createBodyLayer)
    // BEGIN GENERATED ANIMATIONS ... // END GENERATED ANIMATIONS    (static fields)

The conversions are Blockbench's own Java exporter's: x and the x/y rotations flip going from Blockbench to the
model's y-down space, a cube with a rotation of its own becomes a sub-part "<group>_r<n>", and in the animations
position x and rotation x/y flip (which is what makes them the same numbers as the exported .animation.json). Unlike
Blockbench's exporter, a keyframe is made CATMULLROM when either end of the segment leading to it is, which is how
Blockbench previews it (vanilla interpolates a segment by the mode of its end keyframe).

Usage:  python tools/blockbench/bbmodel_to_java.py MODEL.bbmodel --model Model.java --animations Animations.java
        [--prefix animation.aerbison.]   (stripped from the animation names to make the field names)
"""
import argparse
import json
import re
import sys


def num(v):
    v = round(float(v), 4)
    if v == int(v):
        return f"{int(v)}"
    return f"{v:g}F"


def rad(deg):
    if deg == 0:
        return "0"
    return f"{deg:g} * DEG" if deg == int(deg) else f"{deg:g}F * DEG"


def var_name(name):
    parts = re.split(r"[^A-Za-z0-9]+", name)
    out = parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])
    return out if out[:1].isalpha() else "p" + out


def model_code(project):
    elements = {e["uuid"]: e for e in project["elements"]}
    groups = {g["uuid"]: g for g in project["groups"]}
    lines = []
    used = set()

    def unique(name):
        n, i = name, 1
        while n in used:
            i += 1
            n = f"{name}{i}"
        used.add(n)
        return n

    def cube(c, origin):
        x = origin[0] - c["to"][0]
        y = -c["to"][1] + origin[1]
        z = c["from"][2] - origin[2]
        size = [c["to"][i] - c["from"][i] for i in range(3)]
        u, v = c.get("uv_offset", [0, 0])
        inflate = c.get("inflate", 0) or 0
        s = f".texOffs({int(u)}, {int(v)})"
        if c.get("mirror_uv"):
            s += ".mirror()"
        s += f".addBox({num(x)}, {num(y)}, {num(z)}, {num(size[0])}, {num(size[1])}, {num(size[2])}"
        s += f", new CubeDeformation({num(inflate)}))" if inflate else ")"
        if c.get("mirror_uv"):
            s += ".mirror(false)"
        return s

    def pose(origin, parent_origin, rotation):
        if parent_origin is None:
            off = [-origin[0], 24 - origin[1], origin[2]]
        else:
            off = [-(origin[0] - parent_origin[0]), -(origin[1] - parent_origin[1]), origin[2] - parent_origin[2]]
        rot = [-rotation[0], -rotation[1], rotation[2]]
        off_s = ", ".join(num(o) for o in off)
        if any(rot):
            return f"PartPose.offsetAndRotation({off_s}, {', '.join(rad(r) for r in rot)})"
        return f"PartPose.offset({off_s})"

    def emit(var, parent_var, name, cubes_code, pose_code, has_children):
        builder = "CubeListBuilder.create()"
        if cubes_code:
            builder += "\n        " + "\n        ".join(cubes_code)
        head = f"PartDefinition {var} = " if has_children else ""
        lines.append(f'{head}{parent_var}.addOrReplaceChild("{name}", {builder},\n        {pose_code});')

    def walk(node, parent_var, parent_origin):
        g = groups[node["uuid"]]
        name = g["name"]
        if not g.get("export", True):
            return
        origin = g["origin"]
        children = node.get("children", [])
        plain, rotated, subgroups = [], [], []
        for ch in children:
            if isinstance(ch, str):
                c = elements[ch]
                if c.get("export", True) is False:
                    continue
                (rotated if any(c.get("rotation", [0, 0, 0])) else plain).append(c)
            else:
                subgroups.append(ch)
        # cubes sharing a rotation and origin share a sub-part, as in Blockbench's exporter
        rot_parts = []
        for c in rotated:
            for rp in rot_parts:
                if rp["rotation"] == c["rotation"] and rp["origin"] == c["origin"]:
                    rp["cubes"].append(c)
                    break
            else:
                rot_parts.append({"rotation": c["rotation"], "origin": c["origin"], "cubes": [c]})
        var = unique(var_name(name))
        emit(var, parent_var, name, [cube(c, origin) for c in plain], pose(origin, parent_origin, g.get("rotation", [0, 0, 0])),
             bool(rot_parts or subgroups))
        for i, rp in enumerate(rot_parts, 1):
            emit(None, var, f"{name}_r{i}", [cube(c, rp["origin"]) for c in rp["cubes"]],
                 pose(rp["origin"], origin, rp["rotation"]), False)
        for sg in subgroups:
            walk(sg, var, origin)

    for node in project["outliner"]:
        if isinstance(node, str):
            sys.exit("cubes at the root are not supported: put them in a group")
        walk(node, "root", None)
    return "\n".join(lines)


def animation_code(project, prefix):
    out = []
    for anim in project.get("animations", []):
        name = anim["name"]
        if prefix and name.startswith(prefix):
            name = name[len(prefix):]
        field = re.sub(r"[^A-Za-z0-9]+", "_", name).upper()
        length = float(anim.get("length", 0))
        loop = anim.get("loop") == "loop"
        parts = [f"public static final AnimationDefinition {field} = AnimationDefinition.Builder.withLength({num(length)})"]
        if loop:
            parts.append(".looping()")
        for animator in anim.get("animators", {}).values():
            if animator.get("type", "bone") != "bone":
                continue
            bone = animator["name"]
            frames = animator.get("keyframes", [])
            for channel in ("rotation", "position"):
                kfs = sorted((k for k in frames if k["channel"] == channel), key=lambda k: float(k["time"]))
                if not kfs:
                    continue
                written = []
                for i, k in enumerate(kfs):
                    if k["interpolation"] not in ("linear", "catmullrom"):
                        sys.exit(f"{anim['name']} {bone} {channel} @ {k['time']}: {k['interpolation']} is not supported")
                    smooth = k["interpolation"] == "catmullrom" or (i > 0 and kfs[i - 1]["interpolation"] == "catmullrom")
                    for j, dp in enumerate(k["data_points"]):
                        try:
                            x, y, z = (float(dp[a]) for a in "xyz")
                        except ValueError:
                            sys.exit(f"{anim['name']} {bone} {channel} @ {k['time']}: Molang is not supported")
                        if channel == "rotation":
                            x, y = -x, -y
                        else:
                            x = -x
                        t = float(k["time"]) + 0.001 * j
                        fn = ("r" if channel == "rotation" else "p") + ("c" if smooth else "l")
                        written.append(f"{fn}({num(t)}, {num(x)}, {num(y)}, {num(z)})")
                target = "ROT" if channel == "rotation" else "POS"
                parts.append(f'.addAnimation("{bone}", ch({target}, ' + ", ".join(written) + "))")
        parts.append(".build();")
        out.append(f"/** {anim['name']}, {num(length).rstrip('F')} s{', looping' if loop else ''}. */\n"
                   + parts[0] + "".join("\n        " + p for p in parts[1:]))
    return "\n\n".join(out)


def splice(path, marker, code):
    raw = open(path, encoding="utf-8", newline="").read()
    eol = "\r\n" if "\r\n" in raw else "\n"
    text = raw.replace("\r\n", "\n")
    pattern = re.compile(rf"([ \t]*)// BEGIN GENERATED {marker}[^\n]*\n.*?[ \t]*// END GENERATED {marker}", re.S)
    m = pattern.search(text)
    if not m:
        sys.exit(f"{path}: no '// BEGIN GENERATED {marker}' ... '// END GENERATED {marker}' region")
    indent = m.group(1)
    body = "\n".join((indent + line) if line else "" for line in code.split("\n"))
    replacement = (f"{indent}// BEGIN GENERATED {marker} (tools/blockbench/bbmodel_to_java.py; edit the .bbmodel, not this)\n"
                   f"{body}\n{indent}// END GENERATED {marker}")
    text = text[:m.start()] + replacement + text[m.end():]
    open(path, "w", encoding="utf-8", newline="").write(text.replace("\n", eol))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("bbmodel")
    ap.add_argument("--model")
    ap.add_argument("--animations")
    ap.add_argument("--prefix", default="")
    args = ap.parse_args()
    project = json.load(open(args.bbmodel, encoding="utf-8"))
    if not project.get("meta", {}).get("box_uv", True):
        sys.exit("only box-UV projects are supported")
    if args.model:
        splice(args.model, "MODEL", model_code(project))
    if args.animations:
        splice(args.animations, "ANIMATIONS", animation_code(project, args.prefix))
    res = project.get("resolution", {})
    print(f"texture size {res.get('width')}x{res.get('height')}")
    for anim in project.get("animations", []):
        print(f"  {anim['name']}: {anim.get('length')} s ({round(float(anim.get('length', 0)) * 20)} ticks), {anim.get('loop')}")


if __name__ == "__main__":
    main()
