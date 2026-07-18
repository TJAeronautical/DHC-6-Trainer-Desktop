package com.dhc6trainer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jme3.bounding.BoundingBox
import com.jme3.math.Vector3f
import com.jme3.scene.Spatial
import kotlin.math.max

/**
 * 3D views of the DHC-6 Twin Otter (wheels) built from the vendored X-Plane
 * OBJ8 package via [XPlaneObj8Loader]. Outside = exterior orbit view; Inside =
 * the modelled flight deck (static for now; the interactive switch/lever
 * "tagging" mode attaches here once a cockpit-interior model is supplied).
 * Rendering runs on the shared offscreen jME engine ([JmeEngineHub]).
 */

private const val WheelsVariantId = "dhc61-t"

private fun wheelsPackage(): XPlaneTwinOtterVariantPackage? =
    XPlaneTwinOtterVariantLibrary.loadAuto().firstOrNull { it.id == WheelsVariantId }

/** Centres a model on the origin and normalises it to [targetRadius]. */
private fun frameModel(model: Spatial, targetRadius: Float) {
    model.setLocalScale(1f)
    model.setLocalTranslation(0f, 0f, 0f)
    model.updateModelBound()
    model.updateGeometricState()
    val bound = model.worldBound
    val center = bound?.center?.clone() ?: Vector3f.ZERO.clone()
    val radius = when (bound) {
        is BoundingBox -> max(max(bound.xExtent, bound.yExtent), bound.zExtent)
        else -> 1f
    }.coerceAtLeast(0.05f)
    val scale = targetRadius / radius
    model.setLocalScale(scale)
    model.setLocalTranslation(center.negate().multLocal(scale))
}

private const val ExteriorGlbPath = "assets/models/systems_lab/aircraft_variants/dhc6_wheels_painted_training.glb"

internal fun dhc6ExteriorSpec(): JmeSceneSpec = JmeSceneSpec(
    initialYaw = -0.7f,
    initialPitch = 0.28f,
    initialDist = 6.2f,
    minDist = 2.5f,
    maxDist = 22f,
    autoRotate = true,
    build = { app ->
        val key = JmeFlightSimScene.installLightRig(app.rootNode)
        app.rootNode.addLight(com.jme3.light.AmbientLight(com.jme3.math.ColorRGBA(0.62f, 0.66f, 0.72f, 1f)))
        runCatching { app.rootNode.attachChild(JmeFlightSimScene.buildSkyDome(app.assetManager)) }
        runCatching { JmeFlightSimScene.installPostFx(app, key) }
        // Use the higher-quality GLB airframe (painted) for the exterior shell.
        val model = runCatching { app.assetManager.loadModel(ExteriorGlbPath) }.getOrNull()
        if (model != null) {
            Dhc6AircraftPaint.applyLivery(app.assetManager, model)
            frameModel(model, targetRadius = 2.4f)
            model.shadowMode = com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive
            app.rootNode.attachChild(model)
        }
    },
)

internal fun dhc6InteriorSpec(): JmeSceneSpec = JmeSceneSpec(
    initialYaw = 0f,
    initialPitch = 0.05f,
    initialDist = 2.4f,
    minDist = 0.8f,
    maxDist = 8f,
    autoRotate = false,
    build = { app ->
        val key = JmeFlightSimScene.installLightRig(app.rootNode)
        app.rootNode.addLight(com.jme3.light.AmbientLight(com.jme3.math.ColorRGBA(0.62f, 0.66f, 0.72f, 1f)))
        runCatching { JmeFlightSimScene.installPostFx(app, key) }
        val pkg = wheelsPackage()
        val loaded = pkg?.let { XPlaneObj8Loader.loadAircraft(app.assetManager, it) }
        if (loaded != null) {
            // Show only the flight deck / interior; hide the exterior shell so
            // it does not obstruct the pilot view.
            loaded.exteriorNodes.forEach { it.cullHint = Spatial.CullHint.Always }
            frameModel(loaded.cockpitNode, targetRadius = 1.6f)
            app.rootNode.attachChild(loaded.cockpitNode)
            loaded.interiorNodes.forEach { app.rootNode.attachChild(it) }
        }
    },
)

@Composable
internal fun Dhc6ExteriorStage(modifier: Modifier = Modifier) {
    Dhc63dOffscreen(spec = remember { dhc6ExteriorSpec() }, title = "DHC-6 Twin Otter (wheels)", modifier = modifier)
}

@Composable
internal fun Dhc6InteriorStage(modifier: Modifier = Modifier) {
    Dhc63dOffscreen(spec = remember { dhc6InteriorSpec() }, title = "DHC-6 flight deck", modifier = modifier)
}

@Composable
private fun Dhc63dOffscreen(spec: JmeSceneSpec, title: String, modifier: Modifier = Modifier) {
    val sessionResult = remember(spec) { JmeEngineHub.acquire(spec) }
    val session = sessionResult.getOrNull()
    if (session == null) {
        Stage3dMessage(title, sessionResult.exceptionOrNull()?.message ?: "3D renderer unavailable.", modifier)
        return
    }
    val error by session.errorMessage
    if (error != null) {
        Stage3dMessage(title, error ?: "3D render failed.", modifier)
        return
    }
    JmeOffscreenView(session = session, modifier = modifier, blankFallback = { Stage3dMessage(title, "Loading...", Modifier) })
}

@Composable
private fun Stage3dMessage(title: String, message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF03111D), RoundedCornerShape(18.dp)).padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(message.take(140), color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
