package com.dhc6trainer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.jme3.app.SimpleApplication
import com.jme3.bounding.BoundingBox
import com.jme3.bounding.BoundingSphere
import com.jme3.material.Material
import com.jme3.math.ColorRGBA
import com.jme3.math.Vector3f
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Spatial
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

@Composable
internal fun DesktopActualGlbInAppPanel(
    modelPath: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val sessionResult = remember(modelPath) {
        JmeEngineHub.acquire(actualGlbSceneSpec(modelPath, title))
    }
    val session = sessionResult.getOrNull() ?: return

    JmeOffscreenView(session = session, modifier = modifier)
}

private fun actualGlbSceneSpec(modelPath: String, title: String): JmeSceneSpec {
    // Captured by both build and update closures for the auto-spin
    val spinTarget = AtomicReference<Spatial?>(null)

    return JmeSceneSpec(
        initialYaw = 0f,
        initialPitch = 0.14f,
        initialDist = 3.4f,
        minDist = 1.2f,
        maxDist = 12f,
        autoRotate = false,
        update = { _, tpf -> spinTarget.get()?.rotate(0f, tpf * 0.35f, 0f) },
        build = { app ->
            val keyLight = JmeFlightSimScene.installLightRig(app.rootNode)
            runCatching { app.rootNode.attachChild(JmeFlightSimScene.buildSkyDome(app.assetManager)) }
            runCatching { app.rootNode.attachChild(JmeFlightSimScene.buildGroundStage(app.assetManager)) }
            runCatching { JmeFlightSimScene.installPostFx(app, keyLight) }

            val model = runCatching {
                app.assetManager.loadModel(normalizeActualGlbPath(modelPath))
            }.getOrElse {
                makeFallbackBox(app, title)
            }

            prepareActualGlbModel(app, model)
            model.shadowMode = RenderQueue.ShadowMode.CastAndReceive
            app.rootNode.attachChild(model)
            spinTarget.set(model)
        },
    )
}

private fun prepareActualGlbModel(app: SimpleApplication, model: Spatial) {
    model.setLocalTranslation(0f, 0f, 0f)
    model.updateModelBound()
    model.updateGeometricState()

    val bound = model.worldBound
    val center = bound?.center?.clone() ?: Vector3f.ZERO.clone()

    val radius = when (bound) {
        is BoundingBox -> max(bound.xExtent, max(bound.yExtent, bound.zExtent))
        is BoundingSphere -> bound.radius
        else -> 1f
    }.takeIf { it > 0.001f } ?: 1f

    model.setLocalTranslation(center.negate())
    model.setLocalScale(1.85f / radius)

    runCatching {
        model.depthFirstTraversal { spatial ->
            val geometry = spatial as? Geometry ?: return@depthFirstTraversal
            if (geometry.material == null) {
                geometry.material = Material(app.assetManager, "Common/MatDefs/Light/PBRLighting.j3md").apply {
                    setColor("BaseColor", ColorRGBA(0.72f, 0.90f, 1.00f, 1.00f))
                    setFloat("Metallic", 0.65f)
                    setFloat("Roughness", 0.38f)
                }
            }
        }
    }

    model.updateModelBound()
    model.updateGeometricState()
}

private fun makeFallbackBox(app: SimpleApplication, title: String): Spatial {
    val box = com.jme3.scene.shape.Box(0.7f, 0.35f, 0.7f)
    return Geometry("$title fallback model", box).apply {
        material = Material(app.assetManager, "Common/MatDefs/Light/PBRLighting.j3md").apply {
            setColor("BaseColor", ColorRGBA(0.25f, 0.75f, 1.00f, 1.00f))
            setFloat("Metallic", 0.55f)
            setFloat("Roughness", 0.42f)
        }
    }
}

private fun normalizeActualGlbPath(rawPath: String): String {
    return rawPath
        .replace('\\', '/')
        .removePrefix("/")
        .removePrefix("src/main/resources/")
        .removePrefix("desktop-app/src/main/resources/")
        .removePrefix("core-res/src/main/assets/")
        .let { path ->
            if (path.startsWith("assets/")) path else "assets/$path"
        }
}
