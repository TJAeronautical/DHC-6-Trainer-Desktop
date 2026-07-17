package com.dhc6trainer.desktop

import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Closed-loop render verification: starts the real offscreen jME engine,
 * activates the actual scenes the app uses, and checks that rendered frames
 * are not black. Dumps sample frames as PNGs for visual inspection.
 * Run with: gradlew :desktop-app:runRenderSmokeTest
 */
fun main() {
    val outDir = File(System.getProperty("java.io.tmpdir"), "dhc6-render-smoke")
    outDir.mkdirs()
    var failures = 0

    fun testScene(name: String, spec: JmeSceneSpec) {
        val result = JmeEngineHub.acquire(spec)
        val session = result.getOrNull()
        if (session == null) {
            println("FAIL  $name -> engine unavailable: ${result.exceptionOrNull()}")
            failures++
            return
        }
        // Wait for the scene swap (frameHealth resets), then let it render.
        // A clean Windows build can spend 30+ seconds parsing the complete
        // FlightGear cockpit before its first frame; leave enough startup
        // headroom while still failing a genuinely stalled renderer.
        val deadline = System.currentTimeMillis() + 60_000
        var health = JmeFrameHealth()
        val countAtAcquire = session.frameHealth.value.frameCount
        var sawReset = countAtAcquire < 30
        while (System.currentTimeMillis() < deadline) {
            health = session.frameHealth.value
            if (health.frameCount < countAtAcquire) sawReset = true
            if (sawReset && health.frameCount >= 120) break
            Thread.sleep(100)
        }
        if (!sawReset) println("WARN  $name -> never saw a frame-health reset; scene swap may not have happened")
        val bitmap = session.frameBitmap.value
        val error = session.errorMessage.value
        if (bitmap != null) {
            runCatching {
                ImageIO.write(bitmap.toAwtImage(), "png", File(outDir, "$name.png"))
            }
        }
        val blank = health.averageBrightness < 0.002f
        val status = if (bitmap == null || blank) "FAIL" else "OK  "
        if (bitmap == null || blank) failures++
        println(
            "$status  $name -> frames=${health.frameCount} " +
                "brightness=${"%.4f".format(health.averageBrightness)} " +
                "blankStreak=${health.consecutiveBlankFrames} " +
                (error?.let { "error=$it " } ?: "") +
                "png=${File(outDir, "$name.png").absolutePath}",
        )
    }

    val freeFlightSession = FreeFlightSession()
    testScene("freeflight", freeFlightSceneSpec(freeFlightSession))

    FsxAircraftPackageLibrary.loadAllAuto()
        .filter { it.id == "dhc6-300-fsx-pad" || it.id == "dhc6-400-fsx-pad" }
        .forEach { aircraft ->
            val localVariantSession = FreeFlightSession().apply {
                selectedVariantId = aircraft.id
            }
            testScene("freeflight-${aircraft.id}", freeFlightSceneSpec(localVariantSession))
            val replacementActive =
                localVariantSession.sceneStatus.contains("trainer 3D model", ignoreCase = true) ||
                    localVariantSession.sceneStatus.contains("custom GLB", ignoreCase = true)
            if (replacementActive) {
                println("OK    ${aircraft.label} visual -> ${localVariantSession.sceneStatus}")
            } else {
                failures++
                println("FAIL  ${aircraft.label} visual -> ${localVariantSession.sceneStatus}")
            }
        }

    // Same scene from the pilot's seat: switch camera, wait, dump a frame.
    run {
        freeFlightSession.cameraMode = FreeFlightCameraMode.Cockpit
        Thread.sleep(4000)
        val result = JmeEngineHub.acquire(freeFlightSceneSpec(freeFlightSession))
        result.getOrNull()?.let { session ->
            Thread.sleep(6000)
            println("      cockpit pass status: ${freeFlightSession.sceneStatus} | selected=${freeFlightSession.selectedVariantId}")
            session.frameBitmap.value?.let { bitmap ->
                runCatching {
                    ImageIO.write(bitmap.toAwtImage(), "png", File(outDir, "freeflight-cockpit.png"))
                    println("OK    freeflight-cockpit -> png=${File(outDir, "freeflight-cockpit.png").absolutePath}")
                }
            }
            // Look straight down to find the airframe relative to the eye.
            session.orbitBy(0f, -160f)
            Thread.sleep(2500)
            session.frameBitmap.value?.let { bitmap ->
                runCatching {
                    ImageIO.write(bitmap.toAwtImage(), "png", File(outDir, "freeflight-cockpit-down.png"))
                    println("OK    freeflight-cockpit-down -> png saved")
                }
            }
        }
    }
    testScene(
        "systems-glb",
        JmeSceneSpec(
            initialDist = 3.8f,
            build = { app ->
                val keyLight = JmeFlightSimScene.installLightRig(app.rootNode)
                runCatching { app.rootNode.attachChild(JmeFlightSimScene.buildSkyDome(app.assetManager)) }
                runCatching { app.rootNode.attachChild(JmeFlightSimScene.buildGroundStage(app.assetManager)) }
                runCatching { JmeFlightSimScene.installPostFx(app, keyLight) }
                val model = app.assetManager.loadModel("assets/models/systems_lab/pt6a27_cutaway.glb")
                model.setLocalScale(1.4f)
                app.rootNode.attachChild(model)
            },
        ),
    )

    println(if (failures == 0) "RENDER CHECKS PASSED" else "$failures RENDER CHECK(S) FAILED")
    exitProcess(if (failures == 0) 0 else 1)
}
