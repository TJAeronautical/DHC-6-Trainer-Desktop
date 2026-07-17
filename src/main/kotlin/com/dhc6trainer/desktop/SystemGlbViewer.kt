package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jme3.bounding.BoundingBox
import com.jme3.bounding.BoundingSphere
import com.jme3.material.Material
import com.jme3.math.ColorRGBA
import com.jme3.math.FastMath
import com.jme3.math.Vector3f
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.shape.Cylinder
import kotlin.math.max

/* =====================================================================
   SystemGlbViewer - real-time 3D model viewer for the Technical Lab.

   Uses jMonkeyEngine rendering offscreen (see JmeOffscreenView) with the
   flight-sim scene kit:
     - Orbit/arcball camera: drag to rotate, scroll to zoom, inertial
       smoothing, auto-rotate when idle
     - PBR image-based lighting from a baked environment probe
     - PCF shadows, SSAO, bloom and FXAA post-processing
     - Holographic ground stage and sky dome
     - PT6A-27 procedural fallback when no GLB is packaged
     - Integrated with DesktopSystemModelMap camera presets
   ===================================================================== */

@Composable
internal fun SystemGlbViewer(
    group: SystemAssetGroup,
    modifier: Modifier = Modifier,
) {
    val modelPaths = remember(group) { group.glbCandidates() }
    var selectedPath by remember(group) { mutableStateOf(modelPaths.firstOrNull()) }
    val cameraMetadata = remember(group) { group.mappedCameraMetadata() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Overlay),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "REAL-TIME 3D MODEL",
                        color = Dhc6DesktopColors.Accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = selectedPath?.substringAfterLast('/') ?: "${group.name} - no GLB asset indexed",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "drag to orbit  ·  scroll to zoom",
                    color = Dhc6DesktopColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (group.family == "Aircraft") 460.dp else 380.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF030D17), Color(0xFF071C2C), Color(0xFF020F1A)),
                        ),
                        shape = RoundedCornerShape(18.dp),
                    ),
            ) {
                val active = selectedPath
                if (active == null) {
                    MissingGlbPanel(group)
                } else {
                    GlbOffscreenCanvas(
                        modelPath = active,
                        title = group.name,
                        cameraMetadata = cameraMetadata,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (modelPaths.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    modelPaths.take(4).forEach { path ->
                        ModelPathChip(
                            label = path.substringAfterLast('/'),
                            selected = path == selectedPath,
                            onClick = { selectedPath = path },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Text(
                    text = "No GLB model found for this group. Add models under src/main/resources/assets/models/systems_lab and rebuild. The PT6A-27 procedural engine renders as the fallback.",
                    color = Dhc6DesktopColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

/* ---- Composable wrappers ------------------------------------------- */

@Composable
private fun GlbOffscreenCanvas(
    modelPath: String,
    title: String,
    cameraMetadata: DesktopSystemCameraMetadata?,
    modifier: Modifier = Modifier,
) {
    val sessionResult = remember(modelPath) {
        JmeEngineHub.acquire(systemGlbSceneSpec(modelPath, cameraMetadata))
    }
    val session = sessionResult.getOrNull()

    if (session == null) {
        GlbUnavailablePanel(
            title = title,
            modelPath = modelPath,
            message = sessionResult.exceptionOrNull()?.message ?: "Desktop 3D renderer could not initialise.",
            modifier = modifier,
        )
        return
    }

    val error by session.errorMessage
    if (error != null) {
        GlbUnavailablePanel(
            title = title,
            modelPath = modelPath,
            message = error ?: "Desktop 3D renderer failed.",
            modifier = modifier,
        )
        return
    }

    JmeOffscreenView(
        session = session,
        modifier = modifier,
        blankFallback = { SystemSoftwareModelFallback(title) },
    )
}

@Composable
private fun GlbUnavailablePanel(
    title: String,
    modelPath: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF03111D), RoundedCornerShape(18.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("3D VIEWER SAFE MODE", color = Dhc6DesktopColors.Gold, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(modelPath.substringAfterLast('/'), color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(message.take(120), color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun MissingGlbPanel(group: SystemAssetGroup) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.family.uppercase(), color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black)
            Text("No GLB model indexed", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("Add GLB to assets/models/systems_lab and rebuild", color = Dhc6DesktopColors.TextSecondary)
        }
    }
}

@Composable
private fun SystemSoftwareModelFallback(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF071C2C), Color(0xFF04111C), Color(0xFF08253A)),
                ),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("SYSTEM MODEL PREVIEW", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    "Intake" to Color(0xFF38BDF8),
                    "Compressor" to Color(0xFF60A5FA),
                    "Combustor" to Color(0xFFF97316),
                    "Turbine" to Color(0xFF84CC16),
                    "Gearbox" to Color(0xFFA3A3A3),
                ).forEach { (label, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(42.dp)
                                .background(color.copy(alpha = 0.74f), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)), RoundedCornerShape(12.dp)),
                        )
                        Text(label, color = Dhc6DesktopColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "3D preview is recovering; schematic view is shown meanwhile.",
                color = Dhc6DesktopColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ModelPathChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier.size(8.dp)
                    .background(if (selected) Dhc6DesktopColors.Accent else Dhc6DesktopColors.TextMuted, RoundedCornerShape(99.dp)),
            )
            Text(
                text = label,
                color = if (selected) Color.White else Dhc6DesktopColors.TextSecondary,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ---- Scene spec for the shared offscreen engine ---------------------- */

private fun systemGlbSceneSpec(
    modelPath: String,
    cameraMetadata: DesktopSystemCameraMetadata?,
): JmeSceneSpec = JmeSceneSpec(
    initialYaw = -0.35f,
    initialPitch = 0.22f,
    // Models are normalized to radius 1.85 by prepareGlbModel, so a start
    // distance in this band always frames the whole model; metadata presets
    // are clamped into it (some were tuned for the old, uncentered layout).
    initialDist = (parseCamDist(cameraMetadata?.cameraPosition) ?: 5.2f).coerceIn(4.2f, 8.5f),
    minDist = 1.2f,
    maxDist = 16f,
    autoRotate = true,
    build = { app ->
        val keyLight = JmeFlightSimScene.installLightRig(app.rootNode)
        runCatching { app.rootNode.attachChild(JmeFlightSimScene.buildSkyDome(app.assetManager)) }
        runCatching { JmeFlightSimScene.installPostFx(app, keyLight) }

        val raw = runCatching { app.assetManager.loadModel(normalizeAssetPath(modelPath)) }.getOrNull()
        val model = if (raw != null) { prepareGlbModel(app, raw); raw } else buildFallbackEngine(app)
        model.shadowMode = RenderQueue.ShadowMode.CastAndReceive
        app.rootNode.attachChild(model)
    },
)

/* ---- Model preparation ------------------------------------------ */

private fun prepareGlbModel(app: com.jme3.app.SimpleApplication, model: Spatial) {
    model.setLocalTranslation(0f, 0f, 0f)
    model.updateModelBound()
    model.updateGeometricState()

    val bound = model.worldBound
    val center = bound?.center?.clone() ?: Vector3f.ZERO.clone()
    val radius = when (bound) {
        is BoundingBox -> max(max(bound.xExtent, bound.yExtent), bound.zExtent)
        is BoundingSphere -> bound.radius
        else -> 1f
    }.coerceAtLeast(0.05f)

    // A spatial's own localScale does not scale its localTranslation, so the
    // centering offset must be expressed in scaled units - otherwise models
    // authored away from their origin end up far off-camera.
    val scale = (1.85f / radius).coerceIn(0.05f, 30f)
    model.setLocalScale(scale)
    model.setLocalTranslation(center.negate().multLocal(scale))

    // Fix any geometry with null material - PBR so it matches GLB parts
    // and picks up the baked environment reflections
    runCatching {
        model.depthFirstTraversal { spatial ->
            val geom = spatial as? Geometry ?: return@depthFirstTraversal
            if (geom.material == null) {
                geom.material = Material(app.assetManager, "Common/MatDefs/Light/PBRLighting.j3md").apply {
                    setColor("BaseColor", ColorRGBA(0.72f, 0.88f, 1.00f, 1f))
                    setFloat("Metallic", 0.65f)
                    setFloat("Roughness", 0.38f)
                }
            }
        }
    }

    model.updateModelBound()
    model.updateGeometricState()
}

/* ---- PT6A-27 procedural fallback --------------------------------- */

private fun buildFallbackEngine(app: com.jme3.app.SimpleApplication): Spatial {
    data class Sec(val name: String, val radius: Float, val length: Float, val color: ColorRGBA)

    val sections = listOf(
        Sec("Propeller shaft",        0.07f, 0.45f, ColorRGBA(0.40f, 0.45f, 0.50f, 1f)),
        Sec("Reduction gearbox",      0.32f, 0.32f, ColorRGBA(0.10f, 0.43f, 0.50f, 1f)),
        Sec("Axial compressor",       0.28f, 0.55f, ColorRGBA(0.13f, 0.53f, 0.80f, 1f)),
        Sec("Centrifugal compressor", 0.34f, 0.22f, ColorRGBA(0.27f, 0.67f, 0.93f, 1f)),
        Sec("Combustion chamber",     0.43f, 0.38f, ColorRGBA(0.87f, 0.47f, 0.13f, 1f)),
        Sec("Compressor turbine",     0.30f, 0.14f, ColorRGBA(0.20f, 0.67f, 0.33f, 1f)),
        Sec("Power turbine",          0.27f, 0.14f, ColorRGBA(0.53f, 0.80f, 0.20f, 1f)),
        Sec("Exhaust section",        0.22f, 0.28f, ColorRGBA(0.53f, 0.60f, 0.67f, 1f)),
    )

    val gap = 0.05f
    val totalLen = sections.sumOf { it.length.toDouble() }.toFloat() + (sections.size - 1) * gap
    var xPos = -(totalLen / 2f)
    val root = Node("PT6A-27 Procedural")

    sections.forEach { sec ->
        val cyl = Cylinder(8, 24, sec.radius, sec.length, true)
        val geom = Geometry(sec.name, cyl).apply {
            rotate(0f, 0f, FastMath.HALF_PI)          // Y-axis cylinder to X-axis
            setLocalTranslation(xPos + sec.length / 2f, 0f, 0f)
            material = Material(app.assetManager, "Common/MatDefs/Light/PBRLighting.j3md").apply {
                setColor("BaseColor", sec.color)
                setFloat("Metallic", 0.72f)
                setFloat("Roughness", 0.34f)
            }
        }
        root.attachChild(geom)
        xPos += sec.length + gap
    }

    return root
}

/* ---- Helpers ---------------------------------------------------- */

private fun normalizeAssetPath(raw: String): String =
    raw.replace('\\', '/')
        .removePrefix("/")
        .removePrefix("src/main/resources/")
        .removePrefix("desktop-app/src/main/resources/")
        .removePrefix("core-res/src/main/assets/")
        .let { if (it.startsWith("assets/")) it else "assets/$it" }

private fun parseCamDist(camPosHint: String?): Float? {
    // Parse the last number in "Vector3f(x, y, z)" - z is approximate viewing distance
    return camPosHint?.let { Regex("""([\d.]+)\s*\)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }
}

/* ---- SystemAssetGroup extensions ----------------------------------- */

private fun SystemAssetGroup.glbCandidates(): List<String> {
    val direct = matchedAssets
        .map { it.replace('\\', '/') }
        .filter { it.endsWith(".glb", ignoreCase = true) || it.endsWith(".gltf", ignoreCase = true) }
        .distinct()
    return direct.ifEmpty { fallbackGlbCandidates() }
}

private fun SystemAssetGroup.fallbackGlbCandidates(): List<String> {
    val fk = family.lowercase(); val nk = name.lowercase()
    return when {
        fk.contains("engine")  || nk.contains("pt6")  || nk.contains("powerplant") -> listOf(
            "assets/models/systems_lab/pt6a27_cutaway.glb",
        )
        fk.contains("prop")    || nk.contains("prop")  || nk.contains("beta") -> listOf(
            "assets/models/systems_lab/constant_speed_propeller.glb",
            "assets/models/systems_lab/hartzell_propeller.glb",
            "assets/models/systems_lab/beta_backup.glb",
        )
        fk.contains("fuel")    || nk.contains("fuel") -> listOf(
            "assets/models/systems_lab/bleed_valve.glb",
        )
        fk.contains("hydraulic") || nk.contains("hydraulic") -> listOf(
            "assets/models/systems_lab/hydraulic_pack_training.glb",
            "assets/models/systems_lab/nosewheel_steering_training.glb",
        )
        fk.contains("landinggear") || fk.contains("landing") || nk.contains("landing gear") || nk.contains("nosewheel") -> listOf(
            "assets/models/systems_lab/nose_gear_assembly.glb",
            "assets/models/systems_lab/main_gear_assembly.glb",
            "assets/models/systems_lab/nosewheel_steering_training.glb",
        )
        fk.contains("pneumatic") || fk.contains("bleed") || nk.contains("bleed air") || nk.contains("pneumatic") -> listOf(
            "assets/models/systems_lab/bleed_valve.glb",
        )
        fk.contains("aircraft") || nk.contains("aircraft") || nk.contains("variant") -> listOf(
            "assets/models/systems_lab/aircraft_variants/dhc6_wheels_painted_training.glb",
            "assets/models/systems_lab/aircraft_variants/dhc6_floats_painted_training.glb",
            "assets/models/systems_lab/aircraft_variants/dhc6_skis_painted_training.glb",
        )
        fk.contains("electrical") || nk.contains("electrical") -> emptyList()
        fk.contains("fire")    || nk.contains("fire") -> listOf(
            "assets/models/systems_lab/bleed_valve.glb",
        )
        else -> listOf(
            "assets/models/systems_lab/aircraft_variants/dhc6_wheels_painted_training.glb",
        )
    }
}
