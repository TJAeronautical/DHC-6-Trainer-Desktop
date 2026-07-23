package com.dhc6trainer.desktop

import com.jme3.app.SimpleApplication
import com.jme3.asset.AssetManager
import com.jme3.environment.EnvironmentCamera
import com.jme3.environment.LightProbeFactory
import com.jme3.environment.generation.JobProgressAdapter
import com.jme3.light.AmbientLight
import com.jme3.light.DirectionalLight
import com.jme3.light.LightProbe
import com.jme3.material.Material
import com.jme3.math.ColorRGBA
import com.jme3.math.FastMath
import com.jme3.math.Vector3f
import com.jme3.post.FilterPostProcessor
import com.jme3.post.filters.BloomFilter
//noinspection SpellCheckingInspection
import com.jme3.post.filters.FXAAFilter
import com.jme3.post.ssao.SSAOFilter
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Mesh
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.VertexBuffer
import com.jme3.scene.shape.Cylinder
import com.jme3.scene.shape.Sphere
import com.jme3.scene.shape.Torus
import com.jme3.shadow.DirectionalLightShadowFilter
import com.jme3.shadow.EdgeFilteringMode
import com.jme3.texture.Image
import com.jme3.texture.Texture
import com.jme3.texture.Texture2D
import com.jme3.texture.image.ColorSpace
import com.jme3.util.BufferUtils

/* =====================================================================
   JmeFlightSimScene - shared flight-sim-grade rendering kit for every
   embedded jMonkeyEngine viewer in the desktop app.

   Provides:
     - Cinematic 4-light rig (key / fill / rim / ambient)
     - Full post-processing stack: PCF soft shadows, SSAO, bloom, FXAA
     - Procedural hangar sky dome (drives image-based lighting)
     - PBR light probe generation so GLB metallic/roughness materials
       render with real reflections instead of flat shading
     - Holographic ground stage: shadow-catching disc + glowing rings
   ===================================================================== */
internal object JmeFlightSimScene {

    /* ---- Lighting rig ------------------------------------------------ */

    /** Installs the standard light rig and returns the key light (for shadows). */
    fun installLightRig(root: Node): DirectionalLight {
        val key = DirectionalLight().apply {
            direction = Vector3f(-0.45f, -0.75f, -0.55f).normalizeLocal()
            color = ColorRGBA(1.00f, 0.96f, 0.88f, 1.0f).multLocal(1.15f)
        }
        root.addLight(key)
        // Fill light - cool-blue, softer, lower-right
        root.addLight(DirectionalLight().apply {
            direction = Vector3f(0.65f, -0.25f, 0.60f).normalizeLocal()
            color = ColorRGBA(0.38f, 0.52f, 0.88f, 1.0f).multLocal(0.65f)
        })
        // Rim / back light - cyan accent, from behind-above
        root.addLight(DirectionalLight().apply {
            direction = Vector3f(0.05f, 0.40f, -0.92f).normalizeLocal()
            color = ColorRGBA(0.20f, 0.72f, 1.00f, 1.0f).multLocal(0.75f)
        })
        // Ambient - low, preserves shadow depth (Phong materials only)
        root.addLight(AmbientLight().apply {
            color = ColorRGBA(0.14f, 0.18f, 0.26f, 1.0f)
        })
        return key
    }

    /* ---- Post-processing stack --------------------------------------- */

    /**
     * Full flight-sim post stack. Each filter is individually guarded so a
     * driver that rejects one pass never takes down the whole viewer.
     */
    fun installPostFx(app: SimpleApplication, keyLight: DirectionalLight) {
        val fpp = FilterPostProcessor(app.assetManager)
        val samples = app.context.settings.samples
        if (samples > 0) runCatching { fpp.numSamples = samples }

        // Soft cascaded shadows from the key light
        runCatching {
            fpp.addFilter(
                DirectionalLightShadowFilter(app.assetManager, 2048, 3).apply {
                    light = keyLight
                    shadowIntensity = 0.55f
                    lambda = 0.65f
                    edgeFilteringMode = EdgeFilteringMode.PCFPOISSON
                },
            )
        }
        // Screen-space ambient occlusion - grounds parts in crevices
        runCatching { fpp.addFilter(SSAOFilter()) }
        // Bloom - picks up GLB emissive maps and the stage glow rings
        runCatching {
            fpp.addFilter(
                BloomFilter(BloomFilter.GlowMode.Objects).apply {
                    bloomIntensity = 0.85f
                    blurScale = 1.6f
                    exposurePower = 3.5f
                    exposureCutOff = 0.0f
                },
            )
        }
        // Fast anti-aliasing pass over the final frame
        //noinspection SpellCheckingInspection
        runCatching { fpp.addFilter(FXAAFilter().apply { subPixelShift = 0.25f }) }

        app.viewPort.addProcessor(fpp)
    }

    /* ---- Procedural sky dome ----------------------------------------- */

    /**
     * Gradient hangar-sky dome. Rendered in the Sky bucket and captured by
     * the environment camera, so it doubles as the IBL source for PBR
     * reflections on GLB models.
     */
    fun buildSkyDome(assetManager: AssetManager): Spatial {
        val tex = buildSkyGradientTexture()
        val mesh = Sphere(24, 32, 160f, false, true)
        return Geometry("FlightSimSkyDome", mesh).apply {
            material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").apply {
                setTexture("ColorMap", tex)
                additionalRenderState.isDepthWrite = false
            }
            queueBucket = RenderQueue.Bucket.Sky
            cullHint = Spatial.CullHint.Never
            shadowMode = RenderQueue.ShadowMode.Off
            // Sphere poles are on the Z axis; stand the gradient upright.
            rotate(-FastMath.HALF_PI, 0f, 0f)
        }
    }

    private fun buildSkyGradientTexture(): Texture2D {
        // Vertical gradient, brightest band at the equator (horizon glow) and
        // dark at both poles so the dome reads correctly either way up.
        val stops = listOf(
            0.00f to ColorRGBA(0.010f, 0.030f, 0.055f, 1f),
            0.35f to ColorRGBA(0.030f, 0.100f, 0.170f, 1f),
            0.50f to ColorRGBA(0.075f, 0.230f, 0.340f, 1f),
            0.65f to ColorRGBA(0.030f, 0.100f, 0.170f, 1f),
            1.00f to ColorRGBA(0.010f, 0.030f, 0.055f, 1f),
        )
        val width = 4
        val height = 256
        val buffer = BufferUtils.createByteBuffer(width * height * 4)
        for (y in 0 until height) {
            val t = y / (height - 1f)
            val c = sampleGradient(stops, t)
            repeat(width) {
                buffer.put((c.r * 255f).toInt().toByte())
                buffer.put((c.g * 255f).toInt().toByte())
                buffer.put((c.b * 255f).toInt().toByte())
                buffer.put(255.toByte())
            }
        }
        buffer.flip()
        val image = Image(Image.Format.RGBA8, width, height, buffer, ColorSpace.sRGB)
        return Texture2D(image).apply {
            setWrap(Texture.WrapMode.EdgeClamp)
            magFilter = Texture.MagFilter.Bilinear
            minFilter = Texture.MinFilter.BilinearNoMipMaps
        }
    }

    private fun sampleGradient(stops: List<Pair<Float, ColorRGBA>>, t: Float): ColorRGBA {
        val clamped = t.coerceIn(0f, 1f)
        for (i in 0 until stops.size - 1) {
            val (t0, c0) = stops[i]
            val (t1, c1) = stops[i + 1]
            if (clamped in t0..t1) {
                val f = if (t1 > t0) (clamped - t0) / (t1 - t0) else 0f
                return ColorRGBA(
                    FastMath.interpolateLinear(f, c0.r, c1.r),
                    FastMath.interpolateLinear(f, c0.g, c1.g),
                    FastMath.interpolateLinear(f, c0.b, c1.b),
                    1f,
                )
            }
        }
        return stops.last().second
    }

    /* ---- Ground stage ------------------------------------------------ */

    /**
     * Shadow-catching display pedestal: dark disc plus glowing accent rings
     * (the rings feed the bloom pass, giving the "hologram stage" look).
     */
    fun buildGroundStage(assetManager: AssetManager, floorY: Float = -2.0f): Node {
        val stage = Node("FlightSimGroundStage")

        val disc = Geometry("StageDisc", Cylinder(2, 64, 5.5f, 0.06f, true)).apply {
            material = Material(assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
                setBoolean("UseMaterialColors", true)
                setColor("Diffuse", ColorRGBA(0.085f, 0.115f, 0.155f, 1f))
                setColor("Ambient", ColorRGBA(0.05f, 0.07f, 0.10f, 1f))
                setColor("Specular", ColorRGBA(0.30f, 0.36f, 0.42f, 1f))
                setFloat("Shininess", 22f)
            }
            rotate(FastMath.HALF_PI, 0f, 0f)
            setLocalTranslation(0f, floorY - 0.03f, 0f)
            shadowMode = RenderQueue.ShadowMode.Receive
        }
        stage.attachChild(disc)

        fun accentRing(radius: Float, tube: Float, color: ColorRGBA, glow: ColorRGBA): Geometry =
            Geometry("StageRing$radius", Torus(64, 8, tube, radius)).apply {
                material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").apply {
                    setColor("Color", color)
                    setColor("GlowColor", glow)
                }
                rotate(FastMath.HALF_PI, 0f, 0f)
                setLocalTranslation(0f, floorY + 0.01f, 0f)
                shadowMode = RenderQueue.ShadowMode.Off
            }

        stage.attachChild(
            accentRing(
                radius = 2.35f, tube = 0.020f,
                color = ColorRGBA(0.30f, 0.80f, 1.00f, 1f),
                glow = ColorRGBA(0.10f, 0.45f, 0.80f, 1f),
            ),
        )
        stage.attachChild(
            accentRing(
                radius = 3.30f, tube = 0.012f,
                color = ColorRGBA(0.16f, 0.45f, 0.65f, 1f),
                glow = ColorRGBA(0.05f, 0.22f, 0.42f, 1f),
            ),
        )
        return stage
    }

    /* ---- Auto-generated outside terrain ------------------------------ */

    /**
     * Runtime-generated outside-world ground: a large, gently noise-displaced
     * grid with a vegetation→tundra vertex gradient. No external assets, so it
     * frames any loaded cockpit / aircraft model as a believable outside
     * environment inside the sky dome. Sized to sit within [buildSkyDome]'s
     * 160-unit radius. Call after the sky dome; guard at the call site.
     */
    fun buildAutoTerrain(
        assetManager: AssetManager,
        extent: Float = 150f,
        floorY: Float = -3.0f,
    ): Geometry {
        val n = 96                       // 97x97 = 9409 verts (fits u16 indices)
        val step = (extent * 2f) / n
        val stride = n + 1
        val pos = FloatArray(stride * stride * 3)
        val col = FloatArray(stride * stride * 4)
        val idx = ShortArray(n * n * 6)

        fun height(x: Float, z: Float): Float =
            FastMath.sin(x * 0.017f) * 3.2f +
                FastMath.sin(z * 0.021f) * 2.6f +
                FastMath.sin((x + z) * 0.009f) * 4.4f +
                FastMath.sin((x - z) * 0.033f) * 1.1f

        val low = ColorRGBA(0.17f, 0.24f, 0.15f, 1f)   // vegetation
        val high = ColorRGBA(0.46f, 0.42f, 0.30f, 1f)  // tundra / rock
        var p = 0
        var c = 0
        for (i in 0..n) {
            for (j in 0..n) {
                val x = -extent + i * step
                val z = -extent + j * step
                val hgt = height(x, z)
                pos[p++] = x
                pos[p++] = floorY + hgt
                pos[p++] = z
                val t = ((hgt + 6f) / 12f).coerceIn(0f, 1f)
                col[c++] = FastMath.interpolateLinear(t, low.r, high.r)
                col[c++] = FastMath.interpolateLinear(t, low.g, high.g)
                col[c++] = FastMath.interpolateLinear(t, low.b, high.b)
                col[c++] = 1f
            }
        }
        var k = 0
        for (i in 0 until n) {
            for (j in 0 until n) {
                val a = i * stride + j
                val b = i * stride + j + 1
                val d = (i + 1) * stride + j
                val e = (i + 1) * stride + j + 1
                idx[k++] = a.toShort(); idx[k++] = d.toShort(); idx[k++] = b.toShort()
                idx[k++] = b.toShort(); idx[k++] = d.toShort(); idx[k++] = e.toShort()
            }
        }

        val mesh = Mesh().apply {
            setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(*pos))
            setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(*col))
            setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(*idx))
            updateBound()
        }
        return Geometry("FlightSimAutoTerrain", mesh).apply {
            material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").apply {
                setBoolean("VertexColor", true)
            }
            queueBucket = RenderQueue.Bucket.Opaque
            shadowMode = RenderQueue.ShadowMode.Receive
        }
    }

    /* ---- PBR environment probe --------------------------------------- */

    /** Creates the environment camera app state; call from simpleInitApp. */
    fun createEnvironmentCamera(app: SimpleApplication): EnvironmentCamera? =
        runCatching {
            EnvironmentCamera(128, Vector3f(0f, 0f, 0f)).also { app.stateManager.attach(it) }
        }.getOrNull()

    /**
     * Attempts to bake the IBL light probe. Call once per frame from
     * simpleUpdate until it returns true (the env camera initialises a few
     * frames after attach). Returns true when baked or permanently failed.
     */
    fun tryBakeLightProbe(app: SimpleApplication, envCam: EnvironmentCamera?, radius: Float = 80f): Boolean {
        if (envCam == null) return true
        if (!envCam.isInitialized) return false
        runCatching {
            val probe = LightProbeFactory.makeProbe(
                envCam,
                app.rootNode,
                object : JobProgressAdapter<LightProbe>() {
                    override fun done(result: LightProbe) = Unit
                },
            )
            runCatching { probe.area.radius = radius }
            app.rootNode.addLight(probe)
        }
        return true
    }
}
