"""Import a GLB and report its object/material structure with preview renders."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import bpy
from mathutils import Vector


def script_arguments() -> list[str]:
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def bounds(meshes: list[bpy.types.Object]) -> tuple[Vector, Vector]:
    corners = [
        obj.matrix_world @ Vector(corner)
        for obj in meshes
        for corner in obj.bound_box
    ]
    minimum = Vector(min(corner[axis] for corner in corners) for axis in range(3))
    maximum = Vector(max(corner[axis] for corner in corners) for axis in range(3))
    return minimum, maximum


def point_camera(camera: bpy.types.Object, target: Vector) -> None:
    camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()


def render_preview(
    output_path: Path,
    location: Vector,
    target: Vector,
) -> None:
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
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    scene.display.shading.cavity_type = "WORLD"
    scene.render.resolution_x = 960
    scene.render.resolution_y = 640
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.filepath = str(output_path)
    scene.world.color = (0.04, 0.05, 0.07)
    bpy.ops.render.render(write_still=True)

    bpy.data.objects.remove(camera, do_unlink=True)
    bpy.data.cameras.remove(camera_data)


def material_color(material: bpy.types.Material) -> list[float]:
    color = material.diffuse_color
    if material.use_nodes:
        principled = material.node_tree.nodes.get("Principled BSDF")
        if principled is not None:
            color = principled.inputs["Base Color"].default_value
    return [round(float(component), 4) for component in color]


def main() -> None:
    args = script_arguments()
    if len(args) != 2:
        raise SystemExit("usage: blender ... -- input.glb output-directory")
    input_path = Path(args[0]).resolve()
    output_dir = Path(args[1]).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    bpy.ops.import_scene.gltf(filepath=str(input_path))

    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    minimum, maximum = bounds(meshes)
    center = (minimum + maximum) * 0.5
    size = maximum - minimum
    report = {
        "input": str(input_path),
        "bounds": {
            "minimum": [round(value, 4) for value in minimum],
            "maximum": [round(value, 4) for value in maximum],
            "size": [round(value, 4) for value in size],
        },
        "objects": [
            {
                "name": obj.name,
                "parent": obj.parent.name if obj.parent is not None else None,
                "location": [round(float(value), 4) for value in obj.location],
                "dimensions": [round(float(value), 4) for value in obj.dimensions],
                "vertices": len(obj.data.vertices),
                "faces": len(obj.data.polygons),
                "materials": [
                    slot.material.name if slot.material is not None else None
                    for slot in obj.material_slots
                ],
            }
            for obj in sorted(meshes, key=lambda candidate: candidate.name.lower())
        ],
        "materials": [
            {
                "name": material.name,
                "color": material_color(material),
                "users": material.users,
            }
            for material in sorted(bpy.data.materials, key=lambda item: item.name.lower())
        ],
    }
    (output_dir / "material-report.json").write_text(
        json.dumps(report, indent=2),
        encoding="utf-8",
    )

    distance = max(size.x, size.z) * 0.85
    target = Vector((center.x, center.y + size.y * 0.08, center.z))
    render_preview(
        output_dir / "material-front-left.png",
        Vector((center.x - distance * 0.65, center.y + size.y * 0.28, center.z - distance)),
        target,
    )
    render_preview(
        output_dir / "material-rear-right.png",
        Vector((center.x + distance * 0.65, center.y + size.y * 0.28, center.z + distance)),
        target,
    )
    print(
        "CODEX_GLB_MATERIAL_REPORT="
        + json.dumps(
            {
                "objects": len(meshes),
                "materials": len(bpy.data.materials),
                "output": str(output_dir),
            }
        )
    )


if __name__ == "__main__":
    main()
