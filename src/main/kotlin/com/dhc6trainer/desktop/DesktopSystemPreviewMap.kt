package com.dhc6trainer.desktop

internal fun String.mappedModelPreviewPath(
    availablePaths: List<String> = emptyList(),
): String {
    val fileName = substringAfterLast('/').substringAfterLast('\\')
    val baseName = fileName.substringBeforeLast('.', fileName)

    val mappedName = when (fileName.lowercase()) {
        "pt6a27_cutaway.glb" -> "pt6a27_cutaway.png"
        "pt6a_engine_training.glb" -> "pt6a_engine_training.png"
        "fuel_system_training.glb" -> "fuel_system_training.png"
        "hartzell_propeller.glb" -> "hartzell_propeller.png"
        "propeller_hub_training.glb" -> "propeller_hub_training.png"
        "hydraulic_system_training.glb" -> "hydraulic_system_training.png"
        "hydraulic_pack_training.glb" -> "hydraulic_pack_training.png"
        "brake_system_training.glb" -> "brake_system_training.png"
        "dhc6_wheels_painted_training.glb" -> "dhc6_wheels_painted_training.png"
        "dhc6_floats_painted_training.glb" -> "dhc6_floats_painted_training.png"
        "dhc6_skis_painted_training.glb" -> "dhc6_skis_painted_training.png"
        else -> "$baseName.png"
    }

    val expectedPath = "assets/models/systems_lab/previews/$mappedName"

    return availablePaths.firstOrNull { it.endsWith(expectedPath, ignoreCase = true) }
        ?: availablePaths.firstOrNull { it.endsWith(mappedName, ignoreCase = true) }
        ?: expectedPath
}

internal fun SystemAssetGroup.mappedModelPreviewPath(
    modelPath: String?,
): String? {
    return modelPath?.mappedModelPreviewPath(matchedAssets)
}
