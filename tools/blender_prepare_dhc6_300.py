"""Normalize the converted SketchUp DHC-6-300 for the desktop renderer.

Run after tools/convert_skp_to_glb.py:

blender --background --python tools/blender_prepare_dhc6_300.py -- \
  input.glb output.glb
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector


SOURCE_UNIT_SCALE = 0.001
REAL_WINGSPAN_METERS = 19.812


def script_arguments() -> list[str]:
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def world_bounds(objects: list[bpy.types.Object]) -> tuple[Vector, Vector]:
    corners = [
        obj.matrix_world @ Vector(corner)
        for obj in objects
        for corner in obj.bound_box
    ]
    minimum = Vector(min(corner[axis] for corner in corners) for axis in range(3))
    maximum = Vector(max(corner[axis] for corner in corners) for axis in range(3))
    return minimum, maximum


def normalize_meshes() -> dict[str, object]:
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError("Converted GLB contains no mesh objects")

    minimum, maximum = world_bounds(meshes)
    center = (minimum + maximum) * 0.5

    # SketchUp source: X wingspan, Y fuselage length, Z vertical, in mm.
    # Desktop glTF: X wingspan, Y vertical, Z fuselage, nose toward -Z.
    transform = Matrix(
        (
            (SOURCE_UNIT_SCALE, 0.0, 0.0, -center.x * SOURCE_UNIT_SCALE),
            (0.0, 0.0, SOURCE_UNIT_SCALE, -minimum.z * SOURCE_UNIT_SCALE),
            (0.0, SOURCE_UNIT_SCALE, 0.0, -center.y * SOURCE_UNIT_SCALE),
            (0.0, 0.0, 0.0, 1.0),
        )
    )
    for index, obj in enumerate(meshes, start=1):
        obj.matrix_world = transform @ obj.matrix_world
        obj.name = f"DHC6_300_Exterior_{index:02d}"
        for polygon in obj.data.polygons:
            polygon.use_smooth = True

    normalized_minimum, normalized_maximum = world_bounds(meshes)
    size = normalized_maximum - normalized_minimum
    if size.x <= 0.0:
        raise RuntimeError("Converted model has an invalid wingspan")

    # Preserve the real-world scale even if the SketchUp source was drawn
    # slightly undersized or oversized.
    wingspan_scale = REAL_WINGSPAN_METERS / size.x
    scale_matrix = Matrix.Scale(wingspan_scale, 4)
    for obj in meshes:
        obj.matrix_world = scale_matrix @ obj.matrix_world

    final_minimum, final_maximum = world_bounds(meshes)
    final_size = final_maximum - final_minimum
    return {
        "objects": len(meshes),
        "vertices": sum(len(obj.data.vertices) for obj in meshes),
        "faces": sum(len(obj.data.polygons) for obj in meshes),
        "source_size": [round(value, 3) for value in (maximum - minimum)],
        "final_size_meters": [round(value, 3) for value in final_size],
        "wingspan_scale": round(wingspan_scale, 6),
    }


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
    if len(args) != 2:
        raise SystemExit("Input and output GLB paths are required after --")

    input_path = Path(args[0]).resolve()
    output_path = Path(args[1]).resolve()
    clear_scene()
    bpy.ops.import_scene.gltf(filepath=str(input_path))
    report = normalize_meshes()
    bpy.context.scene["dhc6_desktop_source"] = "Twin_Otter_300_WIP.skp"
    bpy.context.scene["dhc6_desktop_purpose"] = "DHC-6-300 exterior"
    bpy.context.scene["dhc6_desktop_instruments"] = "live Compose cockpit overlay"
    export_glb(output_path)
    report["output"] = str(output_path)
    report["size_bytes"] = output_path.stat().st_size
    print("CODEX_DHC6_300_EXPORT=" + json.dumps(report))


if __name__ == "__main__":
    main()
