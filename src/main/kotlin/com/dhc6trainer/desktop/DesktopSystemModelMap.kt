package com.dhc6trainer.desktop

/**
 * Stable model-selection metadata for the desktop Technical Lab.
 *
 * This does not embed or launch a live renderer. It only chooses the most relevant
 * packaged GLB/GLTF model per training group and exposes camera/framing notes for
 * the future renderer pass.
 */
internal data class DesktopSystemCameraMetadata(
    val presetId: String,
    val primaryUse: String,
    val preferredModels: List<String>,
    val cameraPosition: String,
    val lookAt: String,
    val scaleHint: String,
    val framingNote: String,
)

private data class DesktopSystemModelPreset(
    val familyKeys: Set<String>,
    val nameKeys: Set<String>,
    val camera: DesktopSystemCameraMetadata,
)

private val desktopSystemModelPresets = listOf(
    DesktopSystemModelPreset(
        familyKeys = setOf("engine", "powerplant", "pt6a"),
        nameKeys = setOf("pt6a", "powerplant", "engine"),
        camera = DesktopSystemCameraMetadata(
            presetId = "engine-pt6a-cutaway",
            primaryUse = "PT6A engine cutaway / training model",
            preferredModels = listOf(
                "pt6a27_cutaway.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.55, 3.4)",
            lookAt = "Vector3f(0.0, 0.0, 0.0)",
            scaleHint = "1.60-1.85 / modelRadius",
            framingNote = "Engine models are long and often origin-offset; centre on world-bound centre before scaling.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("fuel"),
        nameKeys = setOf("fuel", "tank", "crossfeed", "boost"),
        camera = DesktopSystemCameraMetadata(
            presetId = "fuel-system-training",
            primaryUse = "Fuel system training schematic/model",
            preferredModels = listOf(
                "fuel_system_training.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.45, 3.2)",
            lookAt = "Vector3f(0.0, 0.0, 0.0)",
            scaleHint = "1.75 / modelRadius",
            framingNote = "Procedural DHC-6 fuel model: belly tanks, boost pumps, ejectors, crossfeed and per-nacelle FCU chain inside a ghost airframe.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("propeller", "prop"),
        nameKeys = setOf("propeller", "beta", "autofeather", "csu", "governor"),
        camera = DesktopSystemCameraMetadata(
            presetId = "propeller-hartzell-training",
            primaryUse = "Hartzell propeller / hub / beta training model",
            preferredModels = listOf(
                "constant_speed_propeller.glb",
                "hartzell_propeller.glb",
                "beta_backup.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.35, 3.0)",
            lookAt = "Vector3f(0.0, 0.0, 0.0)",
            scaleHint = "1.90 / modelRadius",
            framingNote = "Propeller/hub models benefit from a tighter camera and slower orbit than engine cutaways.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("hydraulics", "hydraulic", "flaps", "brakes"),
        nameKeys = setOf("hydraulic", "flap", "brake", "steering", "hand pump"),
        camera = DesktopSystemCameraMetadata(
            presetId = "hydraulic-pack-training",
            primaryUse = "Hydraulic system / pack / brake training model",
            preferredModels = listOf(
                "hydraulic_pack_training.glb",
                "nosewheel_steering_training.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.45, 3.3)",
            lookAt = "Vector3f(0.0, 0.0, 0.0)",
            scaleHint = "1.70 / modelRadius",
            framingNote = "Hydraulic/brake assets vary in physical scale; centre and scale by bounds before display.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("aircraft", "airframe", "variant", "variants"),
        nameKeys = setOf("aircraft", "wheel", "wheels", "float", "floats", "ski", "skis"),
        camera = DesktopSystemCameraMetadata(
            presetId = "aircraft-variant-training",
            primaryUse = "DHC-6 aircraft variant model",
            preferredModels = listOf(
                "aircraft_variants/dhc6_wheels_painted_training.glb",
                "aircraft_variants/dhc6_floats_painted_training.glb",
                "aircraft_variants/dhc6_skis_painted_training.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.75, 4.8)",
            lookAt = "Vector3f(0.0, 0.10, 0.0)",
            scaleHint = "1.25-1.50 / modelRadius",
            framingNote = "Full aircraft models need wider framing than component/system models. Models are in aircraft_variants/ subdirectory.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("landinggear", "landing", "gear", "tyre"),
        nameKeys = setOf("landing gear", "nosewheel", "undercarriage", "tyre", "tire"),
        camera = DesktopSystemCameraMetadata(
            presetId = "landing-gear-training",
            primaryUse = "Landing gear, nosewheel and tyre system training model",
            preferredModels = listOf(
                "nose_gear_assembly.glb",
                "main_gear_assembly.glb",
                "nosewheel_steering_training.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.45, 3.2)",
            lookAt = "Vector3f(0.0, 0.0, 0.0)",
            scaleHint = "1.65 / modelRadius",
            framingNote = "Landing gear models are compact; use standard framing. main_gear_assembly.glb is the rear/main double-wheel gear.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("pneumatics", "pneumatic", "bleed", "bleedair"),
        nameKeys = setOf("bleed air", "pneumatic", "anti-ice", "conditioning", "pressurisation"),
        camera = DesktopSystemCameraMetadata(
            presetId = "bleed-air-pneumatic-training",
            primaryUse = "Bleed air, pneumatics, anti-ice and cabin conditioning training model",
            preferredModels = listOf(
                "environmental_bleed_training.glb",
                "bleed_valve.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.45, 3.5)",
            lookAt = "Vector3f(0.0, 0.0, 0.0)",
            scaleHint = "1.70 / modelRadius",
            framingNote = "Bleed air duct model; use standard centre-and-scale framing.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("electrical", "electric", "cas"),
        nameKeys = setOf("electrical", "generator", "battery", "bus", "cas", "annunciator"),
        camera = DesktopSystemCameraMetadata(
            presetId = "electrical-system-training",
            primaryUse = "DHC-6 28 VDC electrical system training model",
            preferredModels = listOf(
                "electrical_system_training.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.45, 3.2)",
            lookAt = "Vector3f(0.0, 0.0, 0.0)",
            scaleHint = "1.70 / modelRadius",
            framingNote = "Procedural electrical model: nacelle starter-generators, external power and an in-fuselage bus-bar board mirroring the poster layout. Keep electrical_g950/electrical_legacy diagrams alongside.",
        ),
    ),
    DesktopSystemModelPreset(
        familyKeys = setOf("flightcontrols", "controls"),
        nameKeys = setOf("flight controls", "aileron", "elevator", "rudder", "trim"),
        camera = DesktopSystemCameraMetadata(
            presetId = "flight-controls-training",
            primaryUse = "DHC-6 cable/pulley flight controls training model",
            preferredModels = listOf(
                "flight_controls_training.glb",
            ),
            cameraPosition = "Vector3f(0.0, 0.65, 4.2)",
            lookAt = "Vector3f(0.0, 0.10, 0.0)",
            scaleHint = "1.35 / modelRadius",
            framingNote = "Full-airframe controls model (columns, cable runs, surfaces, tabs); needs wider framing like the aircraft variants.",
        ),
    ),
)

private val fallbackCameraMetadata = DesktopSystemCameraMetadata(
    presetId = "generic-system-model",
    primaryUse = "Generic packaged systems-lab model",
    preferredModels = emptyList(),
    cameraPosition = "Vector3f(0.0, 0.55, 3.4)",
    lookAt = "Vector3f(0.0, 0.0, 0.0)",
    scaleHint = "1.65-1.75 / modelRadius",
    framingNote = "Use world-bound centre and radius; keep 2D references visible for groups without real GLB coverage.",
)

internal fun SystemAssetGroup.mappedCameraMetadata(): DesktopSystemCameraMetadata =
    matchingSystemPreset()?.camera ?: fallbackCameraMetadata


private fun SystemAssetGroup.matchingSystemPreset(): DesktopSystemModelPreset? {
    val familyKey = family.lowercase()
    val nameKey = name.lowercase()
    return desktopSystemModelPresets.firstOrNull { preset ->
        preset.familyKeys.any { familyKey.contains(it) } ||
            preset.nameKeys.any { nameKey.contains(it) }
    }
}

