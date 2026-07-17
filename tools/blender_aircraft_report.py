"""Inspect and preview an aircraft .blend opened by Blender in background mode."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector


def script_arguments() -> list[str]:
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def world_bounds(objects: list[bpy.types.Object]) -> tuple[Vector, Vector]:
    corners = [
        obj.matrix_world @ Vector(corner)
        for obj in objects
        if obj.type == "MESH"
        for corner in obj.bound_box
    ]
    minimum = Vector(min(corner[axis] for corner in corners) for axis in range(3))
    maximum = Vector(max(corner[axis] for corner in corners) for axis in range(3))
    return minimum, maximum


def point_camera(camera: bpy.types.Object, target: Vector) -> None:
    camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()


def render_preview(output_path: Path, location: Vector, target: Vector) -> None:
    scene = bpy.context.scene
    camera_data = bpy.data.cameras.new(f"Preview Camera {output_path.stem}")
    camera = bpy.data.objects.new(camera_data.name, camera_data)
    scene.collection.objects.link(camera)
    camera.location = location
    camera_data.lens = 55
    point_camera(camera, target)
    scene.camera = camera

    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.studio_light = "paint.sl"
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    scene.display.shading.cavity_type = "WORLD"
    scene.display.shading.color_type = (
        "VERTEX"
        if any(
            obj.type == "MESH" and len(obj.data.color_attributes) > 0
            for obj in scene.objects
        )
        else "MATERIAL"
    )
    scene.render.resolution_x = 960
    scene.render.resolution_y = 640
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.filepath = str(output_path)
    scene.render.film_transparent = False
    scene.world.color = (0.04, 0.05, 0.07)
    bpy.ops.render.render(write_still=True)

    bpy.data.objects.remove(camera, do_unlink=True)
    bpy.data.cameras.remove(camera_data)


def main() -> None:
    args = script_arguments()
    output_dir = Path(args[0]) if args else Path.cwd()
    y_up = len(args) > 1 and args[1].lower() == "y-up"
    output_dir.mkdir(parents=True, exist_ok=True)

    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    minimum, maximum = world_bounds(meshes)
    center = (minimum + maximum) * 0.5
    size = maximum - minimum
    rows = [
        {
            "name": obj.name,
            "location": [round(value, 3) for value in obj.location],
            "dimensions": [round(value, 3) for value in obj.dimensions],
            "vertices": len(obj.data.vertices),
            "faces": len(obj.data.polygons),
        }
        for obj in meshes
    ]
    report = {
        "bounds": {
            "minimum": [round(value, 3) for value in minimum],
            "maximum": [round(value, 3) for value in maximum],
            "center": [round(value, 3) for value in center],
            "size": [round(value, 3) for value in size],
        },
        "totals": {
            "objects": len(bpy.context.scene.objects),
            "mesh_objects": len(meshes),
            "vertices": sum(row["vertices"] for row in rows),
            "faces": sum(row["faces"] for row in rows),
        },
        "objects": rows,
    }
    (output_dir / "aircraft-report.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8"
    )

    if y_up:
        distance = max(size.x, size.z) * 0.9
        height = center.y + size.y * 0.35
        render_preview(
            output_dir / "aircraft-front-left.png",
            Vector((center.x - distance * 0.7, height, center.z - distance)),
            center,
        )
        render_preview(
            output_dir / "aircraft-front-right.png",
            Vector((center.x + distance * 0.7, height, center.z - distance)),
            center,
        )
        render_preview(
            output_dir / "aircraft-rear-left.png",
            Vector((center.x - distance * 0.7, height, center.z + distance)),
            center,
        )
    else:
        distance = max(size.x, size.y) * 0.9
        height = center.z + size.z * 0.35
        render_preview(
            output_dir / "aircraft-front-left.png",
            Vector((center.x - distance * 0.7, center.y - distance, height)),
            center,
        )
        render_preview(
            output_dir / "aircraft-front-right.png",
            Vector((center.x + distance * 0.7, center.y - distance, height)),
            center,
        )
        render_preview(
            output_dir / "aircraft-rear-left.png",
            Vector((center.x - distance * 0.7, center.y + distance, height)),
            center,
        )

    print(
        "CODEX_AIRCRAFT_REPORT="
        + json.dumps(
            {
                "output": str(output_dir),
                "objects": report["totals"]["objects"],
                "vertices": report["totals"]["vertices"],
                "faces": report["totals"]["faces"],
            }
        )
    )


if __name__ == "__main__":
    main()
