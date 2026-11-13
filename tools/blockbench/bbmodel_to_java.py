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
Turns a Blockbench project (.bbmodel, Bedrock or Modded Entity format, box UV), or Blockbench's Bedrock export (a
.geo.json and its .animation.json), into the Java a HierarchicalModel needs: the parts of its LayerDefinition, and its animations as vanilla AnimationDefinitions. The code is written into
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
        python tools/blockbench/bbmodel_to_java.py MODEL.geo.json --bedrock-animations MODEL.animation.json ...

A Bedrock export is read back into the .bbmodel's terms, undoing what Blockbench's Bedrock exporter does: x and the x/y
rotations flip in the geometry, and an animation's position x and rotation x/y flip. Either way, a looping
animation with no length (a held pose) is given 1 s, since vanilla can't loop a zero-length animation, and one whose
name ends in "dev_only" is left out.

A cube with per-face UV (in a .bbmodel) becomes one box per face, each showing only that face, which works when every
face is laid out as box UV would lay it, just somewhere else on the texture; the model file then needs java.util.Set
and net.minecraft.core.Direction imported.
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
        if c.get("box_uv") is False:
            return per_face_cube(c, x, y, z, size)
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

    def per_face_cube(c, x, y, z, size):
        # Vanilla has only box UV, but a box can show some of its faces alone. So each face becomes a box of the cube's
        # size showing just that face, its texOffs chosen to put the face's box-UV spot where its own UV is. That only
        # works for a face laid out as box UV would lay it (same size, unrotated, the same way round).
        w, h, d = size
        name = c.get("name", "?")
        if c.get("inflate") or c.get("mirror_uv"):
            sys.exit(f"{name}: per-face UV with inflate or mirror is not supported")
        # Blockbench face -> (Java direction, box-UV corner relative to texOffs, face size, how the uv is written)
        # up and down are written flipped, as Blockbench writes box UV; Java's y points down, so up is DOWN.
        layout = {
            "north": ("NORTH", (d, d), (w, h), "xyXY"),
            "east": ("WEST", (0, d), (d, h), "xyXY"),
            "south": ("SOUTH", (2 * d + w, d), (w, h), "xyXY"),
            "west": ("EAST", (d + w, d), (d, h), "xyXY"),
            "up": ("DOWN", (d, 0), (w, d), "XYxy"),
            "down": ("UP", (d + w, 0), (w, d), "XyxY"),
        }
        boxes = {}
        for face, (direction, (du, dv), (fw, fh), order) in layout.items():
            f = c.get("faces", {}).get(face)
            if not f or f.get("texture") is None:
                continue
            uv = f["uv"]
            x1, y1, x2, y2 = min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3])
            corners = {"x": x1, "y": y1, "X": x2, "Y": y2}
            if f.get("rotation") or [corners[k] for k in order] != list(uv) or (x2 - x1, y2 - y1) != (fw, fh):
                sys.exit(f"{name}: its {face} face's UV {uv} isn't laid out as box UV would; only that can be converted")
            boxes.setdefault((int(x1 - du), int(y1 - dv)), []).append(direction)
        out = []
        for (u, v), dirs in boxes.items():
            faces = ", ".join(f"Direction.{dr}" for dr in dirs)
            out.append(f".texOffs({u}, {v}).addBox({num(x)}, {num(y)}, {num(z)}, {num(w)}, {num(h)}, {num(d)}, "
                       f"Set.of({faces}))")
        return "\n        ".join(out)

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
                # locators and the like have no geometry
                if c.get("export", True) is False or c.get("type", "cube") != "cube":
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
        if name.endswith("dev_only"):
            continue  # a Blockbench-only helper pose, by its name
        field = re.sub(r"[^A-Za-z0-9]+", "_", name).upper()
        length = float(anim.get("length", 0))
        loop = anim.get("loop") == "loop"
        if loop and length == 0:
            length = 1.0  # a held pose: vanilla can't loop a zero-length animation
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


def bedrock_project(geo_path, anim_path):
    """A Bedrock .geo.json (and .animation.json) as the parts of a .bbmodel project that the code above reads."""
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"]
    if len(geo) != 1:
        sys.exit(f"{geo_path}: expected one geometry, found {len(geo)}")
    geo = geo[0]
    desc = geo.get("description", {})
    elements, groups, nodes, roots = [], [], {}, []

    def flip(v):
        return [-v[0], v[1], v[2]]

    def flip_rot(v):
        return [-v[0], -v[1], v[2]]

    for bone in geo.get("bones", []):
        name = bone["name"]
        groups.append({"uuid": name, "name": name, "origin": flip(bone.get("pivot", [0, 0, 0])),
                       "rotation": flip_rot(bone.get("rotation", [0, 0, 0]))})
        node = nodes[name] = {"uuid": name, "children": []}
        for i, c in enumerate(bone.get("cubes", [])):
            if not isinstance(c.get("uv", [0, 0]), list):
                sys.exit(f"{geo_path}: {name} has per-face UV; only box UV is supported")
            o, size = c["origin"], c["size"]
            frm = [-(o[0] + size[0]), o[1], o[2]]
            el = {"uuid": f"{name}#{i}", "from": frm, "to": [frm[k] + size[k] for k in range(3)],
                  "uv_offset": c.get("uv", [0, 0]), "mirror_uv": c.get("mirror", bone.get("mirror", False)),
                  "inflate": c.get("inflate", 0), "rotation": flip_rot(c.get("rotation", [0, 0, 0])),
                  "origin": flip(c.get("pivot", [0, 0, 0]))}
            elements.append(el)
            node["children"].append(el["uuid"])
    for bone in geo.get("bones", []):
        parent = bone.get("parent")
        (nodes[parent]["children"] if parent else roots).append(nodes[bone["name"]])

    animations = []
    if anim_path:
        for anim_name, anim in json.load(open(anim_path, encoding="utf-8")).get("animations", {}).items():
            animators, longest = {}, 0.0
            for bone, channels in anim.get("bones", {}).items():
                keyframes = []
                for channel, value in channels.items():
                    if channel not in ("rotation", "position"):
                        sys.exit(f"{anim_name} {bone}: {channel} keyframes are not supported")
                    frames = value.items() if isinstance(value, dict) else [("0", value)]
                    for time, frame in frames:
                        lerp = "linear"
                        if isinstance(frame, dict):
                            lerp = frame.get("lerp_mode", "linear")
                            points = [frame["pre"], frame["post"]] if "pre" in frame and frame.get("pre") != frame.get(
                                "post", frame.get("pre")) else [frame.get("post", frame.get("pre"))]
                        else:
                            points = [frame]
                        data = []
                        for pt in points:
                            if not isinstance(pt, list):
                                pt = [pt] * 3
                            if any(isinstance(n, str) for n in pt):
                                sys.exit(f"{anim_name} {bone} {channel} @ {time}: Molang is not supported")
                            pt = [float(n) for n in pt]
                            if channel == "rotation":
                                pt = flip_rot(pt)
                            elif channel == "position":
                                pt = flip(pt)
                            data.append(dict(zip("xyz", pt)))
                        longest = max(longest, float(time))
                        keyframes.append({"channel": channel, "time": float(time), "interpolation": lerp,
                                          "data_points": data})
                animators[bone] = {"name": bone, "type": "bone", "keyframes": keyframes}
            loop = anim.get("loop") is True
            length = float(anim.get("animation_length", longest))
            if loop and length == 0:
                length = 1.0
            animations.append({"name": anim_name, "length": length,
                               "loop": "loop" if loop else "hold" if anim.get("loop") else "once",
                               "animators": animators})
    return {"meta": {"box_uv": True}, "elements": elements, "groups": groups, "outliner": roots,
            "animations": animations,
            "resolution": {"width": desc.get("texture_width"), "height": desc.get("texture_height")}}


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
    replacement = (f"{indent}// BEGIN GENERATED {marker} (tools/blockbench/bbmodel_to_java.py; edit the Blockbench model, not this)\n"
                   f"{body}\n{indent}// END GENERATED {marker}")
    text = text[:m.start()] + replacement + text[m.end():]
    open(path, "w", encoding="utf-8", newline="").write(text.replace("\n", eol))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("bbmodel", help="a .bbmodel, or a Bedrock .geo.json")
    ap.add_argument("--model")
    ap.add_argument("--animations")
    ap.add_argument("--prefix", default="")
    ap.add_argument("--bedrock-animations", help="the .animation.json beside a .geo.json")
    args = ap.parse_args()
    if args.bbmodel.endswith(".geo.json"):
        project = bedrock_project(args.bbmodel, args.bedrock_animations)
    else:
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
