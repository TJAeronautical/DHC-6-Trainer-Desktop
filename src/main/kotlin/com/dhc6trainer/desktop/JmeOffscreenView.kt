package com.dhc6trainer.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jme3.app.SimpleApplication
import com.jme3.asset.plugins.ClasspathLocator
import com.jme3.math.ColorRGBA
import com.jme3.math.Vector3f
import com.jme3.post.SceneProcessor
import com.jme3.profile.AppProfiler
import com.jme3.renderer.RenderManager
import com.jme3.renderer.ViewPort
import com.jme3.renderer.queue.RenderQueue
import com.jme3.system.AppSettings
import com.jme3.system.JmeContext
import com.jme3.texture.FrameBuffer
import com.jme3.texture.Image as JmeImage
import com.jme3.util.BufferUtils
import java.awt.image.BufferedImage
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.ByteBuffer
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.cos
import kotlin.math.sin

/* =====================================================================
   JmeOffscreenView - offscreen jMonkeyEngine to Compose bridge.

   ONE engine instance ("hub") renders into a single hidden GLFW window
   (JmeContext.Type.OffscreenSurface, jme3-lwjgl3) for the lifetime of
   the app. Viewers acquire the hub and swap their scene in; frames are
   read back and published as Compose ImageBitmaps.

   Why this design:
    - Embedded AWT canvases are unavailable (LWJGL3) or broken on modern
      Intel drivers (LWJGL2 Pbuffers).
    - One JME application per viewer crashes the JVM: destroying any
      LWJGL3 window calls glfwTerminate(), killing every other window
      ("Win32: Failed to register window class: Class already exists").

   Camera input (drag-to-orbit, scroll-to-zoom) is captured with Compose
   pointer handlers, since a hidden window receives no OS input events.
   ===================================================================== */

/** Declarative description of a 3D scene shown in the shared engine. */
internal class JmeSceneSpec(
    val initialYaw: Float = -0.35f,
    val initialPitch: Float = 0.22f,
    val initialDist: Float = 3.8f,
    val minDist: Float = 1.2f,
    val maxDist: Float = 16f,
    val autoRotate: Boolean = true,
    val lookAt: Vector3f = Vector3f(0f, 0f, 0f),
    val eyeHeightBias: Float = 0f,
    /**
     * When true the hub's orbit camera is disabled and the spec's [update]
     * hook drives the camera itself (flight-sim views). Drag/scroll input is
     * still smoothed and available through [JmeHostApplication.orbitYaw],
     * [JmeHostApplication.orbitPitch] and [JmeHostApplication.orbitDist].
     */
    val manualCamera: Boolean = false,
    /** Per-frame hook on the render thread (e.g. model auto-spin). */
    val update: (SimpleApplication, Float) -> Unit = { _, _ -> },
    /** Builds lighting, environment, post FX and content. Render thread. */
    val build: (SimpleApplication) -> Unit,
)

/** Handle a viewer holds while its scene is active in the hub. */
internal class JmeSceneSession internal constructor(
    private val host: JmeHostApplication,
) {
    val frameBitmap: MutableState<ImageBitmap?> get() = host.frameBitmap
    val frameHealth: MutableState<JmeFrameHealth> get() = host.frameHealth
    val errorMessage: MutableState<String?> get() = host.errorMessage
    fun orbitBy(dxPx: Float, dyPx: Float) = host.orbitBy(dxPx, dyPx)
    fun zoomBy(scrollDelta: Float) = host.zoomBy(scrollDelta)
}

internal data class JmeFrameHealth(
    val frameCount: Int = 0,
    val consecutiveBlankFrames: Int = 0,
    val averageBrightness: Float = 0f,
) {
    val isPersistentlyBlank: Boolean
        get() = frameCount >= 90 && consecutiveBlankFrames >= 90
}

private object JmeDiagnostics {
    private val logger = Logger.getLogger("JmeHostApplication")
    private val logFile: File by lazy {
        val base = FlightGearInstallation.root
            ?.let { File(it, "app-data") }
            ?: File(System.getProperty("user.home"), "DHC-6 Trainer Desktop")
        File(base, "logs/desktop-3d.log")
    }

    @Synchronized
    fun record(context: String, failure: Throwable? = null, detail: String? = null) {
        val message = buildString {
            append(context)
            detail?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        }
        if (failure == null) {
            logger.warning(message)
        } else {
            logger.log(Level.SEVERE, message, failure)
        }

        runCatching {
            logFile.parentFile?.mkdirs()
            val trace = failure?.let {
                StringWriter().also { writer ->
                    it.printStackTrace(PrintWriter(writer))
                }.toString()
            }
            logFile.appendText(
                buildString {
                    append(Instant.now()).append(" ").append(message).append('\n')
                    trace?.let { append(it) }
                    append('\n')
                },
            )
        }
    }

    fun summary(failure: Throwable?, fallback: String?): String {
        val root = generateSequence(failure) { it.cause }.lastOrNull()
        if (root != null) {
            val type = root.javaClass.simpleName.ifBlank { root.javaClass.name }
            return root.message?.takeIf { it.isNotBlank() }?.let { "$type: $it" } ?: type
        }
        return fallback?.takeIf { it.isNotBlank() } ?: "3D renderer error"
    }
}

/** Lazily starts and hands out the single shared engine. */
internal object JmeEngineHub {
    private var host: JmeHostApplication? = null
    private var failure: Throwable? = null

    @Synchronized
    fun acquire(spec: JmeSceneSpec): Result<JmeSceneSession> {
        failure?.let { return Result.failure(it) }
        val app = host ?: runCatching { startHost() }.getOrElse {
            failure = it
            JmeDiagnostics.record("3D engine startup failed", it)
            return Result.failure(it)
        }
        host = app
        app.activateSpec(spec)
        return Result.success(JmeSceneSession(app))
    }

    private fun startHost(): JmeHostApplication {
        val app = JmeHostApplication()
        val settings = AppSettings(true).apply {
            setResolution(960, 540)
            samples = 4
            isGammaCorrection = true
            frameRate = 30
            audioRenderer = null
            isVSync = false
        }
        app.isShowSettings = false
        app.setSettings(settings)
        app.start(JmeContext.Type.OffscreenSurface)
        return app
    }
}

/* ---- Host application -------------------------------------------------- */

internal class JmeHostApplication : SimpleApplication() {

    /** Latest rendered frame, published from the render thread. */
    val frameBitmap: MutableState<ImageBitmap?> = mutableStateOf(null)

    /** Basic render-health signal so Compose can avoid a dead black panel. */
    val frameHealth: MutableState<JmeFrameHealth> = mutableStateOf(JmeFrameHealth())

    /** Set when the render thread reports an error. */
    val errorMessage: MutableState<String?> = mutableStateOf(null)

    @Volatile private var activeSpec: JmeSceneSpec? = null

    @Volatile private var camYaw = 0f
    @Volatile private var camPitch = 0f
    @Volatile private var camDist = 4f
    @Volatile private var idleTimer = 0f

    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var smoothDist = 4f

    /** Smoothed look/orbit input, readable by manual-camera scene specs. */
    val orbitYaw: Float get() = smoothYaw
    val orbitPitch: Float get() = smoothPitch
    val orbitDist: Float get() = smoothDist

    /** Snaps the orbit input back to a given pose (e.g. on view change). */
    fun resetOrbit(yaw: Float, pitch: Float, dist: Float) {
        camYaw = yaw
        camPitch = pitch
        camDist = dist
    }

    private var envCam: com.jme3.environment.EnvironmentCamera? = null
    private var probeBaked = true

    /** Non-multisampled FBO the main viewport resolves into; readback source. */
    @Volatile private var captureFbo: FrameBuffer? = null

    private val grabber = FrameGrabProcessor({ captureFbo }) { bitmap, averageBrightness ->
        Snapshot.withMutableSnapshot {
            frameBitmap.value = bitmap
            val previous = frameHealth.value
            val blank = averageBrightness < 0.002f
            frameHealth.value = JmeFrameHealth(
                frameCount = previous.frameCount + 1,
                consecutiveBlankFrames = if (blank) previous.consecutiveBlankFrames + 1 else 0,
                averageBrightness = averageBrightness,
            )
        }
    }

    /** Drag input from Compose, in pixels. */
    fun orbitBy(dxPx: Float, dyPx: Float) {
        camYaw += dxPx * 0.008f
        camPitch = (camPitch - dyPx * 0.008f).coerceIn(-1.45f, 1.45f)
        idleTimer = 0f
    }

    /** Scroll input from Compose (positive = wheel down = zoom out). */
    fun zoomBy(scrollDelta: Float) {
        val spec = activeSpec ?: return
        camDist = (camDist + scrollDelta * 0.45f).coerceIn(spec.minDist, spec.maxDist)
        idleTimer = 0f
    }

    /** Swaps the rendered scene; safe to call from any thread. */
    fun activateSpec(spec: JmeSceneSpec) {
        enqueue { swapScene(spec) }
    }

    private fun swapScene(spec: JmeSceneSpec) {
        errorMessage.value = null
        // Tear down previous scene: content, lights, filters, processors.
        // Every step individually guarded: enqueued-task exceptions disappear
        // into an unread Future, which used to abort the swap silently and
        // leave the old scene on screen.
        runCatching { viewPort.clearProcessors() }
            .onFailure {
                java.util.logging.Logger.getLogger("JmeHostApplication")
                    .warning("clearProcessors failed during scene swap: $it")
            }
        runCatching { rootNode.detachAllChildren() }
        runCatching {
            val lights = rootNode.localLightList
            while (lights.size() > 0) {
                rootNode.removeLight(lights.get(0))
            }
        }
        runCatching { viewPort.setOutputFrameBuffer(captureFbo) }

        runCatching { spec.build(this) }
            .onFailure {
                errorMessage.value = JmeDiagnostics.summary(it, "Scene build failed")
                JmeDiagnostics.record("3D scene build failed", it)
            }

        runCatching { rootNode.updateGeometricState() }

        // Grabber re-attached last so it captures post-processed output
        viewPort.addProcessor(grabber)

        activeSpec = spec
        camYaw = spec.initialYaw
        camPitch = spec.initialPitch
        camDist = spec.initialDist
        smoothYaw = camYaw
        smoothPitch = camPitch
        smoothDist = camDist
        frameHealth.value = JmeFrameHealth()
        idleTimer = 0f
        probeBaked = false
        applyOrbitCamera()
    }

    override fun simpleInitApp() {
        setDisplayStatView(false)
        setDisplayFps(false)
        flyCam.isEnabled = false
        runCatching { renderer.setDefaultAnisotropicFilter(4) }

        // CRITICAL: render into an explicit FBO, never the hidden window's
        // default framebuffer. The OffscreenSurface context is an invisible
        // GLFW window and the GL spec leaves its framebuffer contents
        // undefined (pixel-ownership test) - Intel drivers return solid
        // black on readback, which blanked every 3D view in the app.
        try {
            val fbo = FrameBuffer(cam.width, cam.height, 1)
            fbo.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(JmeImage.Format.Depth))
            fbo.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(JmeImage.Format.RGBA8))
            viewPort.setOutputFrameBuffer(fbo)
            captureFbo = fbo
            Logger.getLogger("JmeHostApplication")
                .info("Offscreen FBO attached: ${cam.width}x${cam.height}")
        } catch (t: Throwable) {
            JmeDiagnostics.record("Offscreen FBO creation failed; using window framebuffer", t)
        }

        viewPort.backgroundColor = ColorRGBA(0.01f, 0.04f, 0.08f, 1f)

        assetManager.registerLocator("/", ClasspathLocator::class.java)
        // .glb is the BINARY glTF container and needs a GlbLoader; registering
        // GltfLoader (JSON) for it breaks every .glb with "Not a JSON Object".
        // The caching variants also decode shared texture atlases only once
        // per load instead of once per material (multi-GB savings).
        assetManager.registerLoader(CachingGltfLoader::class.java, "gltf")
        assetManager.registerLoader(CachingGlbLoader::class.java, "glb")

        envCam = JmeFlightSimScene.createEnvironmentCamera(this)
        // Scene arrives via activateSpec() enqueue, drained on first update
    }

    override fun simpleUpdate(tpf: Float) {
        val spec = activeSpec ?: return
        idleTimer += tpf

        if (!probeBaked) {
            probeBaked = JmeFlightSimScene.tryBakeLightProbe(this, envCam)
        }
        if (spec.autoRotate && !spec.manualCamera && idleTimer > 3f) {
            camYaw += tpf * 0.28f
        }
        val follow = (tpf * 9f).coerceAtMost(1f)
        smoothYaw += (camYaw - smoothYaw) * follow
        smoothPitch += (camPitch - smoothPitch) * follow
        smoothDist += (camDist - smoothDist) * follow
        if (!spec.manualCamera) {
            applyOrbitCamera()
        }

        // A scene update must never take down the shared engine: after a few
        // consecutive failures the update hook is muted and the scene keeps
        // rendering statically instead.
        runCatching { spec.update(this, tpf) }
            .onSuccess { updateFailureStreak = 0 }
            .onFailure { failure ->
                updateFailureStreak++
                if (updateFailureStreak >= 8 && errorMessage.value == null) {
                    errorMessage.value = JmeDiagnostics.summary(failure, "Scene update failed")
                    JmeDiagnostics.record("3D scene update muted after repeated failures", failure)
                    activeSpec = JmeSceneSpec(
                        initialYaw = spec.initialYaw,
                        initialPitch = spec.initialPitch,
                        initialDist = spec.initialDist,
                        minDist = spec.minDist,
                        maxDist = spec.maxDist,
                        autoRotate = spec.autoRotate,
                        lookAt = spec.lookAt,
                        eyeHeightBias = spec.eyeHeightBias,
                        manualCamera = false,
                        build = spec.build,
                    )
                }
            }
    }

    private var updateFailureStreak = 0

    private fun applyOrbitCamera() {
        val spec = activeSpec ?: return
        val x = (smoothDist * sin(smoothYaw.toDouble()) * cos(smoothPitch.toDouble())).toFloat()
        val y = (smoothDist * sin(smoothPitch.toDouble())).toFloat() + spec.eyeHeightBias
        val z = (smoothDist * cos(smoothYaw.toDouble()) * cos(smoothPitch.toDouble())).toFloat()
        cam.location = Vector3f(spec.lookAt.x + x, spec.lookAt.y + y, spec.lookAt.z + z)
        cam.lookAt(spec.lookAt, Vector3f.UNIT_Y)
    }

    private var renderFailureStreak = 0

    /**
     * Top-level per-frame guard. If anything in the update/render pass throws
     * (bad GLB material, driver-rejected post filter, scene race), the frame is
     * dropped instead of letting the exception reach the LWJGL window loop,
     * which would tear down the ONE shared GL context for the whole app.
     * Repeated failures progressively strip post-processing, then the scene.
     */
    override fun update() {
        try {
            super.update()
            renderFailureStreak = 0
        } catch (t: Throwable) {
            renderFailureStreak++
            if (errorMessage.value == null) {
                errorMessage.value = JmeDiagnostics.summary(t, "3D frame failed")
            }
            if (renderFailureStreak == 1 || renderFailureStreak == 4 || renderFailureStreak == 12) {
                JmeDiagnostics.record("Dropped 3D frame (streak $renderFailureStreak)", t)
            }
            if (renderFailureStreak == 4) {
                // Most common culprit: a post-processor the driver rejects.
                runCatching {
                    viewPort.clearProcessors()
                    viewPort.addProcessor(grabber)
                }
            }
            if (renderFailureStreak >= 12) {
                // Last resort: drop scene content but keep the engine alive so
                // other viewers can still swap their scenes in.
                runCatching {
                    rootNode.detachAllChildren()
                    val lights = rootNode.localLightList
                    while (lights.size() > 0) {
                        rootNode.removeLight(lights.get(0))
                    }
                }
            }
        }
    }

    override fun handleError(errMsg: String?, t: Throwable?) {
        // Never call super.handleError: the default implementation stops the
        // application, which destroys the ONE shared GLFW context and takes
        // every 3D view in the app down with it (and glfwDestroyWindow has
        // crashed the whole JVM natively on some drivers - see hs_err logs).
        // Record the error, keep the engine alive.
        errorMessage.value = JmeDiagnostics.summary(t, errMsg)
        JmeDiagnostics.record("Render thread error; engine kept alive", t, errMsg)
    }

    override fun simpleRender(rm: RenderManager?) = Unit
}

/* ---- Frame readback processor ----------------------------------------- */

private class FrameGrabProcessor(
    /**
     * The framebuffer to read pixels from. The `out` parameter of [postFrame]
     * cannot be trusted: with a FilterPostProcessor attached it is the FPP's
     * internal MULTISAMPLED buffer, and glReadPixels on a multisampled
     * framebuffer is an invalid operation that silently yields black pixels
     * (this blanked every 3D view). The host's resolve FBO is authoritative.
     */
    private val captureSource: () -> FrameBuffer?,
    private val onFrame: (ImageBitmap, Float) -> Unit,
) : SceneProcessor {
    private var renderManager: RenderManager? = null
    private var width = 0
    private var height = 0
    private var readBuffer: ByteBuffer? = null
    private var pixels: IntArray = IntArray(0)
    private var awtImage: BufferedImage? = null
    private var readFailureLogged = false
    private var sourceLogged = false

    override fun initialize(rm: RenderManager, vp: ViewPort) {
        renderManager = rm
        reshape(vp, vp.camera.width, vp.camera.height)
    }

    override fun reshape(vp: ViewPort, w: Int, h: Int) {
        width = w
        height = h
        readBuffer = BufferUtils.createByteBuffer(w * h * 4)
        pixels = IntArray(w * h)
        awtImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    }

    override fun isInitialized(): Boolean = renderManager != null

    override fun preFrame(tpf: Float) = Unit

    override fun postQueue(rq: RenderQueue) = Unit

    override fun postFrame(out: FrameBuffer?) {
        val renderer = renderManager?.renderer ?: return
        val buf = readBuffer ?: return
        val image = awtImage ?: return
        if (width <= 0 || height <= 0) return

        buf.clear()
        val source = captureSource() ?: out
        val read = runCatching { renderer.readFrameBuffer(source, buf) }
        if (read.isFailure) {
            if (!readFailureLogged) {
                readFailureLogged = true
                java.util.logging.Logger.getLogger("FrameGrabProcessor")
                    .warning("readFrameBuffer failed (source=$source): ${read.exceptionOrNull()}")
            }
            return
        }
        if (!sourceLogged) {
            sourceLogged = true
            java.util.logging.Logger.getLogger("FrameGrabProcessor")
                .info("Reading frames from framebuffer: $source")
        }

        // RGBA bytes, bottom row first to ARGB ints, top row first.
        var dst = 0
        var lumaSum = 0f
        var lumaSamples = 0
        for (y in height - 1 downTo 0) {
            var src = y * width * 4
            for (x in 0 until width) {
                val r = buf.get(src).toInt() and 0xFF
                val g = buf.get(src + 1).toInt() and 0xFF
                val b = buf.get(src + 2).toInt() and 0xFF
                pixels[dst] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                if ((dst and 31) == 0) {
                    lumaSum += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                    lumaSamples++
                }
                src += 4
                dst++
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width)
        onFrame(image.toComposeImageBitmap(), if (lumaSamples == 0) 0f else lumaSum / lumaSamples)
    }

    override fun cleanup() = Unit

    override fun setProfiler(profiler: AppProfiler?) = Unit
}

/* ---- Compose view ------------------------------------------------------ */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun JmeOffscreenView(
    session: JmeSceneSession,
    modifier: Modifier = Modifier,
    placeholder: String = "Starting 3D renderer...",
    blankFallback: (@Composable () -> Unit)? = null,
) {
    val frame by session.frameBitmap
    val health by session.frameHealth
    Box(
        modifier = modifier
            .pointerInput(session) {
                detectDragGestures { change, drag ->
                    change.consume()
                    session.orbitBy(drag.x, drag.y)
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) session.zoomBy(delta)
            },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = frame
        if (bitmap != null && !(health.isPersistentlyBlank && blankFallback != null)) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (blankFallback != null && (bitmap != null || health.isPersistentlyBlank)) {
            blankFallback()
        } else {
            Text(
                text = placeholder,
                color = Color(0xFF6FA9C9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
