"""Prepare the user-supplied DHC-6 float model for the desktop renderer.

Run with Blender:

blender --background assets-source/aircraft/dhc-6_float.blend \
  --python tools/blender_prepare_dhc6_float.py -- output.glb
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix


DECIMATE_RATIO = 0.22
DECIMATE_FACE_THRESHOLD = 2_500


def script_arguments() -> list[str]:
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def prepare_meshes() -> tuple[int, int]:
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    faces_before = sum(len(obj.data.polygons) for obj in meshes)

    # The supplied model faces Blender -Y. Rotate it so Blender's glTF
    # conversion produces the app convention: Y up and nose toward -Z.
    turn_to_app_heading = Matrix.Rotation(math.pi, 4, "Z")
    for index, obj in enumerate(meshes, start=1):
        obj.matrix_world = turn_to_app_heading @ obj.matrix_world
        if obj.name.startswith(("Cube", "Cylinder", "Torus")):
            obj.name = f"Airframe_Detail_{index:03d}"

        face_count = len(obj.data.polygons)
        if face_count >= DECIMATE_FACE_THRESHOLD:
            modifier = obj.modifiers.new(name="Desktop optimization", type="DECIMATE")
            modifier.decimate_type = "COLLAPSE"
            modifier.ratio = DECIMATE_RATIO
            modifier.use_collapse_triangulate = True
            bpy.context.view_layer.objects.active = obj
            obj.select_set(True)
            bpy.ops.object.modifier_apply(modifier=modifier.name)
            obj.select_set(False)

        for polygon in obj.data.polygons:
            polygon.use_smooth = True

    faces_after = sum(len(obj.data.polygons) for obj in meshes)
    return faces_before, faces_after


def export_glb(output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(output_path),
        export_format="GLB",
        export_yup=True,
        export_apply=True,
        export_animations=False,
        export_cameras=False,
        export_lights=False,
        export_extras=True,
        export_materials="EXPORT",
    )


def main() -> None:
    args = script_arguments()
    if not args:
        raise SystemExit("Output GLB path is required after --")

    output_path = Path(args[0]).resolve()
    faces_before, faces_after = prepare_meshes()
    bpy.context.scene["dhc6_desktop_source"] = "user-supplied dhc-6_float.blend"
    bpy.context.scene["dhc6_desktop_purpose"] = "DHC-6 float exterior"
    bpy.context.scene["dhc6_desktop_instruments"] = "live Compose cockpit overlay"
    export_glb(output_path)
    print(
        "CODEX_DHC6_FLOAT_EXPORT="
        + json.dumps(
            {
                "output": str(output_path),
                "faces_before": faces_before,
                "faces_after": faces_after,
                "size_bytes": output_path.stat().st_size,
            }
        )
    )


if __name__ == "__main__":
    main()
