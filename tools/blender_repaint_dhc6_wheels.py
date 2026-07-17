"""Repaint and optimize the assembled DHC-6 wheel aircraft for the desktop app."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import bpy
from mathutils import Vector


PALETTE = {
    "white": (0.882, 0.925, 0.953, 1.0),  # #E1ECF3
    "navy": (0.031, 0.157, 0.239, 1.0),  # #08283D
    "navy_deep": (0.008, 0.059, 0.102, 1.0),  # #020F1A
    "blue": (0.122, 0.561, 0.859, 1.0),  # #1F8FDB
    "cyan": (0.302, 0.733, 1.0, 1.0),  # #4DBBFF
    "metal": (0.31, 0.36, 0.39, 1.0),
    "rubber": (0.018, 0.027, 0.035, 1.0),
}


def script_arguments() -> list[str]:
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def make_material(
    name: str,
    color: tuple[float, float, float, float],
    roughness: float,
    metallic: float = 0.0,
) -> bpy.types.Material:
    material = bpy.data.materials.new(name)
    material.diffuse_color = color
    material.use_nodes = True
    principled = material.node_tree.nodes.get("Principled BSDF")
    if principled is not None:
        principled.inputs["Base Color"].default_value = color
        principled.inputs["Roughness"].default_value = roughness
        principled.inputs["Metallic"].default_value = metallic
    return material


def aircraft_material_key(name: str) -> str:
    lower = name.lower()
    if lower.startswith("wing,outer") or lower.startswith("tail,hz"):
        return "white"
    if lower.startswith("fuse,cabin") or lower.startswith("tail,cone"):
        return "white"
    if lower == "nosecone" or lower.startswith("nose,door"):
        return "white"
    if "flaperon" in lower or "fleperon" in lower or "rudder" in lower:
        return "blue"
    if "wing,flap" in lower or "tail,elev" in lower:
        return "cyan"
    if lower.startswith("engine,") and "xhst" not in lower:
        return "navy"
    if lower.startswith("wing,strut") or lower in {"nose,panel", "fuse,paxdoor"}:
        return "blue"
    if lower.startswith("fuse,sidewall") or lower.startswith("fuse,seats"):
        return "navy"
    if lower.startswith("gear,mainwhl"):
        return "rubber"
    if lower.startswith("gear,") or "xhst" in lower:
        return "metal"
    if lower.startswith("propellors"):
        return "navy_deep"
    if lower.startswith("fuse,airstair"):
        return "cyan"
    return "white"


def cockpit_material_key(obj: bpy.types.Object) -> str:
    lower = obj.name.lower()
    dimensions = obj.dimensions
    largest = max(dimensions)
    smallest = min(dimensions)
    if lower.startswith("torus"):
        return "cyan" if largest < 2.0 else "navy_deep"
    if lower.startswith("cylinder"):
        return "metal"
    if largest < 0.32:
        suffix = int(lower.split(".")[-1]) if "." in lower else 0
        return "cyan" if suffix % 5 == 0 else "white"
    if smallest < 0.12 and largest < 1.7:
        return "blue"
    return "navy_deep" if largest > 3.0 else "navy"


def assign_material(obj: bpy.types.Object, material: bpy.types.Material) -> None:
    obj.data.materials.clear()
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0


def decimate_ratio(obj: bpy.types.Object) -> float:
    lower = obj.name.lower()
    faces = len(obj.data.polygons)
    if faces < 5_000:
        return 1.0
    if lower.startswith("fuse,seats"):
        return 0.28
    if lower.startswith("fuse,cabin") or lower == "nosecone":
        return 0.44
    if "flaperon" in lower or "fleperon" in lower or "rudder" in lower:
        return 0.30
    if lower.startswith("wing,flap") or lower.startswith("tail,elev"):
        return 0.34
    if lower.startswith("tail,cone"):
        return 0.38
    return 0.48


def optimize_mesh(obj: bpy.types.Object) -> None:
    ratio = decimate_ratio(obj)
    if ratio < 1.0:
        bpy.context.view_layer.objects.active = obj
        obj.select_set(True)
        modifier = obj.modifiers.new("Desktop exterior optimization", "DECIMATE")
        modifier.ratio = ratio
        modifier.use_collapse_triangulate = True
        bpy.ops.object.modifier_apply(modifier=modifier.name)
        obj.select_set(False)
    while obj.data.uv_layers:
        obj.data.uv_layers.remove(obj.data.uv_layers[0])
    while obj.data.color_attributes:
        obj.data.color_attributes.remove(obj.data.color_attributes[0])


def scene_bounds(meshes: list[bpy.types.Object]) -> tuple[Vector, Vector]:
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


def render_preview(path: Path, meshes: list[bpy.types.Object]) -> None:
    minimum, maximum = scene_bounds(meshes)
    center = (minimum + maximum) * 0.5
    size = maximum - minimum
    distance = max(size.x, size.y) * 0.72

    scene = bpy.context.scene
    camera_data = bpy.data.cameras.new("Trainer livery preview camera")
    camera = bpy.data.objects.new(camera_data.name, camera_data)
    scene.collection.objects.link(camera)
    camera.location = Vector(
        (
            center.x - distance * 0.58,
            center.y - distance * 0.66,
            center.z + size.z * 0.58,
        )
    )
    camera_data.lens = 58
    point_camera(camera, center + Vector((0.0, 0.0, size.z * 0.04)))
    scene.camera = camera

    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.studio_light = "paint.sl"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    scene.display.shading.cavity_type = "WORLD"
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 720
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.filepath = str(path)
    scene.world.color = (0.025, 0.04, 0.06)
    bpy.ops.render.render(write_still=True)


def main() -> None:
    args = script_arguments()
    if len(args) != 3:
        raise SystemExit("usage: blender ... -- input.glb output.glb preview.png")
    input_path = Path(args[0]).resolve()
    output_path = Path(args[1]).resolve()
    preview_path = Path(args[2]).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    preview_path.parent.mkdir(parents=True, exist_ok=True)

    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    bpy.ops.import_scene.gltf(filepath=str(input_path))

    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    before_faces = sum(len(obj.data.polygons) for obj in meshes)
    before_vertices = sum(len(obj.data.vertices) for obj in meshes)
    materials = {
        "white": make_material("Trainer White", PALETTE["white"], 0.34),
        "navy": make_material("Trainer Navy", PALETTE["navy"], 0.38),
        "navy_deep": make_material("Trainer Deep Navy", PALETTE["navy_deep"], 0.32),
        "blue": make_material("Trainer Strong Blue", PALETTE["blue"], 0.30),
        "cyan": make_material("Trainer Cyan", PALETTE["cyan"], 0.26),
        "metal": make_material("Trainer Metal", PALETTE["metal"], 0.30, 0.55),
        "rubber": make_material("Trainer Rubber", PALETTE["rubber"], 0.82),
    }

    for obj in meshes:
        is_aircraft_part = "," in obj.name or obj.name == "Nosecone" or obj.name.startswith("Propellors")
        key = aircraft_material_key(obj.name) if is_aircraft_part else cockpit_material_key(obj)
        assign_material(obj, materials[key])
        if is_aircraft_part:
            optimize_mesh(obj)

    after_faces = sum(len(obj.data.polygons) for obj in meshes)
    after_vertices = sum(len(obj.data.vertices) for obj in meshes)
    render_preview(preview_path, meshes)
    bpy.ops.export_scene.gltf(
        filepath=str(output_path),
        export_format="GLB",
        export_materials="EXPORT",
        export_yup=True,
        export_normals=True,
        export_tangents=False,
        export_texcoords=False,
        export_cameras=False,
        export_lights=False,
        export_extras=False,
        export_apply=True,
    )
    print(
        "CODEX_REPAINTED_DHC6="
        + json.dumps(
            {
                "input": str(input_path),
                "output": str(output_path),
                "before_vertices": before_vertices,
                "after_vertices": after_vertices,
                "before_faces": before_faces,
                "after_faces": after_faces,
                "materials": len(materials),
            }
        )
    )


if __name__ == "__main__":
    main()
