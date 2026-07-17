package com.dhc6trainer.desktop

import com.jme3.app.SimpleApplication
import com.jme3.asset.AssetManager
import com.jme3.asset.TextureKey
import com.jme3.asset.plugins.ZipLocator
import com.jme3.light.AmbientLight
import com.jme3.light.DirectionalLight
import com.jme3.material.Material
import com.jme3.material.RenderState
import com.jme3.math.ColorRGBA
import com.jme3.math.FastMath
import com.jme3.math.Quaternion
import com.jme3.math.Vector2f
import com.jme3.math.Vector3f
import com.jme3.post.FilterPostProcessor
import com.jme3.post.filters.BloomFilter
import com.jme3.post.filters.FXAAFilter
import com.jme3.post.filters.FogFilter
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Mesh
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.VertexBuffer
import com.jme3.scene.shape.Box as JmeBox
import com.jme3.scene.shape.Quad
import com.jme3.scene.shape.Sphere
import com.jme3.shadow.DirectionalLightShadowFilter
import com.jme3.shadow.EdgeFilteringMode
import com.jme3.texture.Texture
import com.jme3.texture.Texture2D
import com.jme3.texture.image.ColorSpace
import com.jme3.util.BufferUtils
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* =====================================================================
   FreeFlightScene - real-time flyable DHC-6 world.

   A genuine flight-simulator scene: the personalized FlightGear DHC-6
   flying over a 3D airfield world (runway, apron, hangars, town,
   mountains, clouds) textured or preview-backed from local FSX environment
   packages when present. The Dhc6FlightModel integrates
   physics every frame and the camera follows in cockpit, chase, or
   tower view.
   ===================================================================== */

internal enum class FreeFlightCameraMode(val label: String) {
    Cockpit("Cockpit"),
    Chase("Chase"),
    Tower("Tower");

    fun next(): FreeFlightCameraMode = entries[(ordinal + 1) % entries.size]
}

internal enum class FreeFlightReset { None, Runway, Final, Cruise }

internal enum class FreeFlightDhc6Variant(
    val aircraftId: String,
    val label: String,
    val gearModelFile: String,
) {
    Wheels("dhc6", "Wheels", "wheels.ac"),
    Floats("dhc6F", "Floats", "floats.ac"),
    Skis("dhc6S", "Skis", "wheels.ac");

    companion object {
        fun fromAircraftId(id: String): FreeFlightDhc6Variant? =
            entries.firstOrNull { it.aircraftId == id }
    }
}

internal data class FreeFlightAircraftOption(
    val id: String,
    val label: String,
)

/** Shared state bridge between the Compose UI and the render thread. */
internal class FreeFlightSession {
    val controls = Dhc6FlightControls()
    val openSourceSim = OpenSourceSimLibrary.loadAuto()
    val fsxAircraftPackages = FsxAircraftPackageLibrary.loadAllAuto()
    val fsxAircraftPackage =
        fsxAircraftPackages.firstOrNull { it.nativeModelSupported }
            ?: fsxAircraftPackages.firstOrNull()
    val environmentPackage = FsxEnvironmentLibrary.loadAuto()
    val model = Dhc6FlightModel(
        openSourceSim.primaryDhc6Profile?.let(Dhc6Params::fromJsbsimProfile)
            ?: fsxAircraftPackage?.aircraftCfgText?.let(Dhc6Params::fromAircraftCfgText)
            ?: Dhc6Params.fromBundledAircraftCfg(),
    )
    val sceneryPackage: XPlaneSceneryPackage? = null

    @Volatile var cameraMode = FreeFlightCameraMode.Chase
    @Volatile var pendingReset = FreeFlightReset.None
    @Volatile var sceneStatus = "Starting flight sim..."
    @Volatile var selectedVariantId = FreeFlightDhc6Variant.Wheels.aircraftId

    val telemetry: Dhc6Telemetry get() = model.telemetry
    val selectedFlightGearAircraftPackage: FlightGearAircraftPackage?
        get() = openSourceSim.flightGearAircraft
            ?.takeIf { FreeFlightDhc6Variant.fromAircraftId(selectedVariantId) != null }
    val selectedFlightGearVariant: FreeFlightDhc6Variant?
        get() = FreeFlightDhc6Variant.fromAircraftId(selectedVariantId)
    val selectedFsxAircraftPackage: FsxAircraftPackage?
        get() = fsxAircraftPackages.firstOrNull {
            it.id == selectedVariantId && it.nativeModelSupported && it.id != "air-alpes-fsx"
        }
    val availableAircraftOptions: List<FreeFlightAircraftOption>
        get() = buildList {
            if (openSourceSim.flightGearAircraft != null) {
                FreeFlightDhc6Variant.entries.forEach { add(FreeFlightAircraftOption(it.aircraftId, it.label)) }
            }
            fsxAircraftPackages
                .filter { it.nativeModelSupported && it.id != "air-alpes-fsx" }
                .forEach { add(FreeFlightAircraftOption(it.id, "${it.label.removeSuffix(" FSX")} local")) }
        }
}

internal fun freeFlightSceneSpec(session: FreeFlightSession): JmeSceneSpec {
    val world = FreeFlightWorld(session)
    return JmeSceneSpec(
        initialYaw = 0f,
        initialPitch = 0.22f,
        initialDist = 24f,
        minDist = 6f,
        maxDist = 180f,
        autoRotate = false,
        manualCamera = true,
        build = { app -> world.build(app) },
        update = { app, tpf -> world.update(app, tpf) },
    )
}

private class FreeFlightWorld(private val session: FreeFlightSession) {

    private lateinit var aircraftNode: Node
    private var modelSpatial: Spatial? = null
    private var fgExteriorNodes = listOf<Spatial>()
    private var propMeshGeometries = listOf<Geometry>()
    private var propDiscs = listOf<Geometry>()
    private var skyNode: Node? = null
    private var cockpitEyeLocal = Vector3f(0f, 1.75f, -3.1f)
    private var lastCameraMode: FreeFlightCameraMode? = null
    private var loadedVariantId: String? = null

    private val scratchEye = Vector3f()
    private val scratchDir = Vector3f()
    private val scratchUp = Vector3f()
    private val lookQuat = Quaternion()

    /* ------------------------------------------------------------------ */
    /* Build                                                                */
    /* ------------------------------------------------------------------ */

    fun build(app: SimpleApplication) {
        val assets = app.assetManager
        FsxMdlLoaderRegistry.register(assets)
        val scenery = session.sceneryPackage
        val environment = session.environmentPackage
        val envTextureSource = if (scenery == null) FsxEnvironmentTextureSource(assets, environment) else null

        // Day lighting: warm tropical sun plus cool sky ambient.
        val sun = DirectionalLight().apply {
            direction = Vector3f(-0.38f, -0.74f, -0.42f).normalizeLocal()
            color = ColorRGBA(1.0f, 0.975f, 0.90f, 1f).multLocal(1.35f)
        }
        app.rootNode.addLight(sun)
        app.rootNode.addLight(AmbientLight().apply {
            color = ColorRGBA(0.42f, 0.48f, 0.58f, 1f)
        })

        skyNode = Node("FreeFlightSky").also { sky ->
            sky.attachChild(buildDaySkyDome(assets))
            app.rootNode.attachChild(sky)
        }

        session.model.runwayStart.set(0f, 0f, if (scenery != null) 1540f else 520f)
        if (scenery != null) {
            app.rootNode.attachChild(buildVrmmWorld(assets, scenery))
        } else {
            if (environment.isAvailable) {
                app.rootNode.attachChild(buildFsxPreviewEnvironment(assets, environment))
            }
            app.rootNode.attachChild(buildTerrain(assets))
            app.rootNode.attachChild(buildRunway(assets))
            app.rootNode.attachChild(buildApron(assets, envTextureSource ?: FsxEnvironmentTextureSource(assets, environment)))
            app.rootNode.attachChild(buildTown(assets, envTextureSource ?: FsxEnvironmentTextureSource(assets, environment)))
            app.rootNode.attachChild(buildMountains(assets))
        }
        if (scenery != null && environment.isAvailable) {
            app.rootNode.attachChild(buildFsxPreviewEnvironment(assets, environment))
        }
        app.rootNode.attachChild(buildClouds(assets))

        aircraftNode = Node("Dhc6Aircraft")
        app.rootNode.attachChild(aircraftNode)
        loadAircraft(assets)

        app.camera.setFrustumPerspective(
            62f,
            app.camera.width.toFloat() / app.camera.height.toFloat().coerceAtLeast(1f),
            0.35f,
            42_000f,
        )
        installPostFx(app, sun)

        val aircraftLabel = session.selectedFlightGearAircraftPackage
            ?.takeIf { loadedVariantId == session.selectedFlightGearVariant?.aircraftId }
            ?.let { "${it.statusBadge} - ${session.selectedFlightGearVariant?.label}" }
            ?: session.selectedFsxAircraftPackage
                ?.takeIf { loadedVariantId == it.id }
                ?.let { "${it.label} data - ${LocalAircraftModelLibrary.sourceLabel(it.id)}" }
            ?: "Aircraft stand-in"
        session.sceneStatus = buildString {
            append(aircraftLabel)
            append(" - ")
            append(
                scenery?.statusBadge
                    ?.let { if (environment.isAvailable) "$it + ${environment.statusBadge}" else it }
                    ?: if (environment.isAvailable) environment.statusBadge else "procedural world textures",
            )
            append(" - ")
            append(session.openSourceSim.primaryStatus)
        }
        session.pendingReset = FreeFlightReset.Runway
    }

    private fun loadAircraft(assets: AssetManager) {
        aircraftNode.detachAllChildren()
        modelSpatial = null
        fgExteriorNodes = emptyList()
        propMeshGeometries = emptyList()
        propDiscs = emptyList()
        cockpitEyeLocal = Vector3f(0f, 1.75f, -3.1f)
        loadedVariantId = null

        session.selectedFlightGearAircraftPackage?.let { aircraftPackage ->
            val variant = session.selectedFlightGearVariant ?: FreeFlightDhc6Variant.Wheels
            val model = FlightGearAc3dLoader.loadAircraft(assets, aircraftPackage, variant)
            if (model != null) {
                attachFlightGearAircraftModel(assets, model, aircraftPackage, variant)
                return
            }
        }

        session.selectedFsxAircraftPackage?.let { aircraftPackage ->
            val replacement = LocalAircraftModelLibrary.replacementFor(aircraftPackage.id)
            val customModel = replacement?.load(assets)
            if (
                customModel != null &&
                attachExternalGlbAircraftModel(
                    model = customModel,
                    variantId = aircraftPackage.id,
                    statusBadge = "${aircraftPackage.label} custom GLB loaded",
                )
            ) return

            val trainerPackage = session.openSourceSim.flightGearAircraft
            val trainerModel = trainerPackage?.let {
                FlightGearAc3dLoader.loadAircraft(assets, it, FreeFlightDhc6Variant.Wheels)
            }
            if (trainerPackage != null && trainerModel != null) {
                attachFlightGearAircraftModel(
                    assets = assets,
                    model = trainerModel,
                    aircraftPackage = trainerPackage,
                    variant = FreeFlightDhc6Variant.Wheels,
                    variantId = aircraftPackage.id,
                    statusBadge = "${aircraftPackage.label} data - trainer 3D model loaded",
                )
                return
            }
        }
    }

    private fun attachFlightGearAircraftModel(
        assets: AssetManager,
        model: Spatial,
        aircraftPackage: FlightGearAircraftPackage,
        variant: FreeFlightDhc6Variant,
        variantId: String = variant.aircraftId,
        statusBadge: String = "${aircraftPackage.statusBadge} - ${variant.label} loaded",
    ) {
        model.updateModelBound()
        model.updateGeometricState()
        val gearDrop = groundOffsetFor(model)
        model.setLocalTranslation(0f, gearDrop, 0f)
        aircraftNode.attachChild(model)
        modelSpatial = model
        loadedVariantId = variantId

        // Exterior shell/cabin/wheels obstruct the pilot's view from inside
        // (their interior faces render as untextured white); collect them so
        // cockpit view can hide everything except the flight deck.
        val exteriorNames = setOf("dhc-6.ac", "cabin.ac", "wheels.ac", "floats.ac")
        fgExteriorNodes = (model as? Node)?.children
            ?.filter { it.name in exteriorNames }
            .orEmpty()

        // Pilot eye: derive from the flightdeck sub-model bounds (left seat,
        // eye level, slightly aft of the deck centre so the panel is in view).
        cockpitEyeLocal = Vector3f(-0.35f, gearDrop + 1.05f, 0.35f)
        model.depthFirstTraversal { spatial ->
            if (spatial is Node && spatial.name.startsWith("flightdeck", ignoreCase = true)) {
                // worldBound already includes the gear-drop translation.
                val bound = spatial.worldBound as? com.jme3.bounding.BoundingBox
                if (bound != null) {
                    cockpitEyeLocal = Vector3f(
                        bound.center.x - 0.35f,
                        bound.center.y + 0.42f,
                        bound.center.z + 0.85f,
                    )
                }
            }
        }
        buildFlightGearPropDiscs(assets, gearDrop)
        session.sceneStatus = statusBadge
    }

    private fun attachExternalGlbAircraftModel(
        model: Spatial,
        variantId: String,
        statusBadge: String,
    ): Boolean {
        model.setLocalScale(1f)
        model.setLocalTranslation(0f, 0f, 0f)
        model.updateModelBound()
        model.updateGeometricState()

        val bound = model.worldBound as? com.jme3.bounding.BoundingBox ?: return false
        val wingspan = (bound.xExtent * 2f).coerceAtLeast(0.01f)
        val scale = (19.812f / wingspan).coerceIn(0.001f, 100f)
        val bottom = bound.center.y - bound.yExtent
        model.setLocalScale(scale)
        model.setLocalTranslation(
            -bound.center.x * scale,
            -bottom * scale,
            -bound.center.z * scale,
        )
        model.shadowMode = RenderQueue.ShadowMode.CastAndReceive
        model.updateModelBound()
        model.updateGeometricState()

        aircraftNode.attachChild(model)
        modelSpatial = model
        loadedVariantId = variantId
        cockpitEyeLocal = Vector3f(-0.4f, 1.65f, -3.1f)
        session.sceneStatus = statusBadge
        return true
    }

    private fun attachFsxAircraftModel(
        assets: AssetManager,
        model: Spatial,
        variantId: String,
        statusBadge: String,
    ) {
        model.updateModelBound()
        model.updateGeometricState()

        // Rest the gear on y=0 of the aircraft node.
        val gearDrop = groundOffsetFor(model)
        model.setLocalTranslation(0f, gearDrop, 0f)
        aircraftNode.attachChild(model)
        modelSpatial = model
        loadedVariantId = variantId

        // FS2004 MDLs model animated/instanced parts (props, VC panel, pilot
        // figures, some interior plates) AT THE ORIGIN and position them via
        // animation transforms this static parser does not interpret. Left
        // visible they form a polygon cluster at the CG, so hide them, and
        // derive prop-disc positions from the engine nacelle geometry instead.
        val props = mutableListOf<Geometry>()
        val nacelles = mutableListOf<Geometry>()
        model.depthFirstTraversal { spatial ->
            val geom = spatial as? Geometry ?: return@depthFirstTraversal
            val bound = geom.mesh.bound as? com.jme3.bounding.BoundingBox
            val nearOrigin = bound != null && bound.center.length() < 1.2f
            when {
                geom.name.startsWith("fsx-tex-prop") -> {
                    props += geom
                    geom.cullHint = Spatial.CullHint.Always
                }
                geom.name.startsWith("fsx-tex-VCpan") ||
                    geom.name.startsWith("fsx-tex-pilots") -> {
                    geom.cullHint = Spatial.CullHint.Always
                }
                geom.name.startsWith("fsx-tex-interiors") && nearOrigin -> {
                    geom.cullHint = Spatial.CullHint.Always
                }
                geom.name.startsWith("fsx-tex-Twin_engine") -> nacelles += geom
            }
        }
        propMeshGeometries = emptyList()

        // Pilot eye in the exterior shell's cockpit section (windshield area).
        cockpitEyeLocal = Vector3f(-0.4f, gearDrop + 0.85f, -2.75f)

        buildFsxPropDiscs(assets, nacelles, gearDrop)
        session.sceneStatus = "$statusBadge loaded"
    }

    private fun groundOffsetFor(model: Spatial): Float {
        val bound = model.worldBound
        return if (bound is com.jme3.bounding.BoundingBox) {
            -(bound.center.y - bound.yExtent)
        } else {
            0f
        }
    }

    /**
     * Blurred prop discs for the FSX MDL variant. The MDL's own prop meshes
     * sit unplaced at the origin, so the spinner positions are derived from
     * the engine-nacelle geometry: cluster nacelle vertices by side, disc at
     * each cluster's centre, just ahead of its foremost point.
     */
    private fun buildFsxPropDiscs(assets: AssetManager, nacelles: List<Geometry>, gearDrop: Float) {
        var leftSum = Vector3f(); var leftCount = 0; var leftMinZ = Float.MAX_VALUE
        var rightSum = Vector3f(); var rightCount = 0; var rightMinZ = Float.MAX_VALUE
        nacelles.forEach { geom ->
            val positions = geom.mesh.getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Position) ?: return@forEach
            positions.rewind()
            while (positions.remaining() >= 3) {
                val x = positions.get(); val y = positions.get(); val z = positions.get()
                if (kotlin.math.abs(x) < 1.2f || kotlin.math.abs(x) > 5f) continue
                if (x < 0f) {
                    leftSum.addLocal(x, y, z); leftCount++
                    if (z < leftMinZ) leftMinZ = z
                } else {
                    rightSum.addLocal(x, y, z); rightCount++
                    if (z < rightMinZ) rightMinZ = z
                }
            }
        }
        if (leftCount == 0 || rightCount == 0) return
        val left = leftSum.divideLocal(leftCount.toFloat())
        val right = rightSum.divideLocal(rightCount.toFloat())
        val centers = listOf(
            Vector3f(left.x, left.y + gearDrop, leftMinZ - 0.18f),
            Vector3f(right.x, right.y + gearDrop, rightMinZ - 0.18f),
        )
        val discs = centers.mapIndexed { i, center ->
            Geometry("PropDisc$i", com.jme3.scene.shape.Cylinder(2, 32, 1.29f, 0.015f, true)).apply {
                material = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
                    setColor("Color", ColorRGBA(0.06f, 0.06f, 0.07f, 0.30f))
                    additionalRenderState.blendMode = RenderState.BlendMode.Alpha
                    additionalRenderState.isDepthWrite = false
                }
                queueBucket = RenderQueue.Bucket.Transparent
                shadowMode = RenderQueue.ShadowMode.Off
                setLocalTranslation(center)
                cullHint = Spatial.CullHint.Always
            }
        }
        discs.forEach(aircraftNode::attachChild)
        propDiscs = discs
    }

    private fun buildFlightGearPropDiscs(assets: AssetManager, gearDrop: Float) {
        val material = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
            setColor("Color", ColorRGBA(0.06f, 0.06f, 0.07f, 0.24f))
            additionalRenderState.blendMode = RenderState.BlendMode.Alpha
            additionalRenderState.isDepthWrite = false
        }
        propDiscs = listOf(-2.857f, 2.857f).mapIndexed { index, x ->
            Geometry("FlightGearPropDisc$index", com.jme3.scene.shape.Cylinder(2, 36, 1.22f, 0.012f, true)).apply {
                this.material = material
                setLocalTranslation(x, gearDrop + 1.012f, -2.66f)
                queueBucket = RenderQueue.Bucket.Transparent
                shadowMode = RenderQueue.ShadowMode.Off
                cullHint = Spatial.CullHint.Always
                aircraftNode.attachChild(this)
            }
        }
    }

    private fun buildXPlanePropDiscs(assets: AssetManager, gearDrop: Float) {
        val material = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
            setColor("Color", ColorRGBA(0.06f, 0.06f, 0.07f, 0.24f))
            additionalRenderState.blendMode = RenderState.BlendMode.Alpha
            additionalRenderState.isDepthWrite = false
        }
        propDiscs = listOf(-2.858f, 2.858f).mapIndexed { index, x ->
            Geometry("XPlanePropDisc$index", com.jme3.scene.shape.Cylinder(2, 36, 1.25f, 0.012f, true)).apply {
                this.material = material
                rotate(FastMath.HALF_PI, 0f, 0f)
                setLocalTranslation(x, gearDrop + 1.97f, 3.71f)
                queueBucket = RenderQueue.Bucket.Transparent
                shadowMode = RenderQueue.ShadowMode.Off
                cullHint = Spatial.CullHint.Always
                aircraftNode.attachChild(this)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Per-frame update (render thread)                                     */
    /* ------------------------------------------------------------------ */

    fun update(app: SimpleApplication, tpf: Float) {
        when (session.pendingReset) {
            FreeFlightReset.Runway -> session.model.resetOnRunway()
            FreeFlightReset.Final -> session.model.resetOnFinal()
            FreeFlightReset.Cruise -> session.model.resetInCruise()
            FreeFlightReset.None -> Unit
        }
        session.pendingReset = FreeFlightReset.None

        if (loadedVariantId != session.selectedVariantId) {
            loadAircraft(app.assetManager)
        }

        val model = session.model
        model.update(session.controls, tpf)

        aircraftNode.setLocalTranslation(model.position)
        aircraftNode.setLocalRotation(model.attitude())

        val running = session.controls.enginesRunning
        val cockpitView = session.cameraMode == FreeFlightCameraMode.Cockpit
        val hideShellInCockpit = cockpitView && session.selectedFlightGearAircraftPackage == null
        modelSpatial?.cullHint =
            if (hideShellInCockpit) Spatial.CullHint.Always else Spatial.CullHint.Inherit
        // FlightGear model: hide the exterior shell in cockpit view so the
        // flight deck and instruments are unobstructed.
        fgExteriorNodes.forEach {
            it.cullHint = if (cockpitView) Spatial.CullHint.Always else Spatial.CullHint.Inherit
        }
        propDiscs.forEach {
            it.cullHint = if (running && !hideShellInCockpit) Spatial.CullHint.Dynamic else Spatial.CullHint.Always
        }
        propMeshGeometries.forEach { it.cullHint = if (running) Spatial.CullHint.Always else Spatial.CullHint.Dynamic }

        updateCamera(app)
        skyNode?.setLocalTranslation(app.camera.location)
    }

    private fun updateCamera(app: SimpleApplication) {
        val host = app as? JmeHostApplication
        val mode = session.cameraMode
        if (mode != lastCameraMode) {
            when (mode) {
                FreeFlightCameraMode.Cockpit -> host?.resetOrbit(0f, 0f, 12f)
                FreeFlightCameraMode.Chase -> host?.resetOrbit(0f, 0.22f, 24f)
                FreeFlightCameraMode.Tower -> Unit
            }
            lastCameraMode = mode
        }
        val orbitYaw = host?.orbitYaw ?: 0f
        val orbitPitch = host?.orbitPitch ?: 0.2f
        val orbitDist = host?.orbitDist ?: 24f
        val model = session.model
        val cam = app.camera

        when (mode) {
            FreeFlightCameraMode.Cockpit -> {
                val attitude = model.attitude()
                attitude.mult(cockpitEyeLocal, scratchEye)
                scratchEye.addLocal(model.position)
                lookQuat.fromAngles(-orbitPitch, -orbitYaw, 0f)
                scratchDir.set(0f, 0f, -1f)
                lookQuat.mult(scratchDir, scratchDir)
                attitude.mult(scratchDir, scratchDir)
                attitude.mult(scratchUp.set(Vector3f.UNIT_Y), scratchUp)
                cam.location = scratchEye
                cam.lookAtDirection(scratchDir, scratchUp)
            }
            FreeFlightCameraMode.Chase -> {
                val azimuth = model.headingRad + orbitYaw
                val pitch = orbitPitch.coerceIn(-0.2f, 1.35f)
                val horiz = orbitDist * cos(pitch)
                scratchEye.set(
                    model.position.x - sin(azimuth) * horiz,
                    model.position.y + orbitDist * sin(pitch) + 2.2f,
                    model.position.z + cos(azimuth) * horiz,
                )
                if (scratchEye.y < 1.2f) scratchEye.y = 1.2f
                cam.location = scratchEye
                cam.lookAt(model.position.add(0f, 1.6f, 0f), Vector3f.UNIT_Y)
            }
            FreeFlightCameraMode.Tower -> {
                scratchEye.set(85f, 14f, 420f)
                cam.location = scratchEye
                cam.lookAt(model.position.add(0f, 1.2f, 0f), Vector3f.UNIT_Y)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* World construction                                                   */
    /* ------------------------------------------------------------------ */

    private fun buildDaySkyDome(assets: AssetManager): Spatial {
        val stops = listOf(
            0.00f to ColorRGBA(0.34f, 0.52f, 0.72f, 1f),
            0.42f to ColorRGBA(0.42f, 0.62f, 0.82f, 1f),
            0.50f to ColorRGBA(0.78f, 0.86f, 0.93f, 1f),
            0.56f to ColorRGBA(0.42f, 0.62f, 0.82f, 1f),
            0.80f to ColorRGBA(0.18f, 0.38f, 0.66f, 1f),
            1.00f to ColorRGBA(0.10f, 0.26f, 0.52f, 1f),
        )
        val width = 4
        val height = 512
        val buffer = BufferUtils.createByteBuffer(width * height * 4)
        for (y in 0 until height) {
            val t = y / (height - 1f)
            val c = sampleStops(stops, t)
            repeat(width) {
                buffer.put((c.r * 255f).toInt().toByte())
                buffer.put((c.g * 255f).toInt().toByte())
                buffer.put((c.b * 255f).toInt().toByte())
                buffer.put(255.toByte())
            }
        }
        buffer.flip()
        val image = com.jme3.texture.Image(com.jme3.texture.Image.Format.RGBA8, width, height, buffer, ColorSpace.sRGB)
        val tex = Texture2D(image).apply {
            setWrap(Texture.WrapMode.EdgeClamp)
            magFilter = Texture.MagFilter.Bilinear
            minFilter = Texture.MinFilter.BilinearNoMipMaps
        }
        return Geometry("DaySkyDome", Sphere(28, 36, 20_000f, false, true)).apply {
            material = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
                setTexture("ColorMap", tex)
                additionalRenderState.isDepthWrite = false
            }
            queueBucket = RenderQueue.Bucket.Sky
            cullHint = Spatial.CullHint.Never
            shadowMode = RenderQueue.ShadowMode.Off
            rotate(-FastMath.HALF_PI, 0f, 0f)
        }
    }

    private fun sampleStops(stops: List<Pair<Float, ColorRGBA>>, t: Float): ColorRGBA {
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

    private fun buildVrmmWorld(assets: AssetManager, scenery: XPlaneSceneryPackage): Node {
        val node = Node("VRMM Male Maldives")
        scenery.registerOrthoLocator(assets)
        node.attachChild(buildVrmmOcean(assets))
        node.attachChild(buildVrmmOrthoTiles(assets, scenery))
        node.attachChild(buildVrmmPavements(assets, scenery))
        node.attachChild(buildVrmmRunway(assets, scenery))
        node.attachChild(buildVrmmAirportObjects(assets, scenery))
        return node
    }

    private fun buildFsxPreviewEnvironment(assets: AssetManager, environment: FsxEnvironmentPackage): Node {
        val texture = previewTexture(assets, environment) ?: return Node("FSX preview environment empty")
        val node = Node("FSX preview environment ${environment.id}")
        val mat = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
            setTexture("ColorMap", texture)
        }
        val width = 5600f
        val height = 2400f
        val y = 90f
        listOf(
            Triple(Vector3f(-width / 2f, y, -4200f), 0f, "front"),
            Triple(Vector3f(width / 2f, y, 4200f), FastMath.PI, "rear"),
            Triple(Vector3f(-4200f, y, width / 2f), FastMath.HALF_PI, "left"),
            Triple(Vector3f(4200f, y, -width / 2f), -FastMath.HALF_PI, "right"),
        ).forEach { (pos, yaw, label) ->
            node.attachChild(
                Geometry("FSX ${environment.id} backdrop $label", Quad(width, height)).apply {
                    material = mat
                    setLocalTranslation(pos)
                    rotate(0f, yaw, 0f)
                    shadowMode = RenderQueue.ShadowMode.Off
                },
            )
        }
        val ground = Geometry("FSX ${environment.id} photo ground", Quad(5200f, 5200f).also {
            it.scaleTextureCoordinates(Vector2f(1.4f, 1.4f))
        }).apply {
            material = mat
            rotate(-FastMath.HALF_PI, 0f, 0f)
            setLocalTranslation(-2600f, -0.045f, 2600f)
            shadowMode = RenderQueue.ShadowMode.Receive
        }
        node.attachChild(ground)
        return node
    }

    private fun previewTexture(assets: AssetManager, environment: FsxEnvironmentPackage): Texture2D? {
        val zipPath = environment.zipPath ?: return null
        val entryName = environment.previewEntry.takeIf { it.isNotBlank() } ?: return null
        val image = runCatching {
            java.util.zip.ZipFile(zipPath.toFile()).use { zip ->
                val entry = zip.getEntry(entryName) ?: return@use null
                zip.getInputStream(entry).use(ImageIO::read)
            }
        }.getOrNull() ?: return null
        return Texture2D(awtToJmeImage(image)).apply {
            setWrap(Texture.WrapMode.EdgeClamp)
            minFilter = Texture.MinFilter.Trilinear
            anisotropicFilter = 4
        }
    }

    private fun buildVrmmOcean(assets: AssetManager): Spatial {
        val tex = Texture2D(awtToJmeImage(maldivesWaterTexture())).apply {
            setWrap(Texture.WrapMode.Repeat)
            minFilter = Texture.MinFilter.Trilinear
            anisotropicFilter = 8
        }
        val quad = Quad(80_000f, 80_000f)
        quad.scaleTextureCoordinates(Vector2f(220f, 220f))
        return Geometry("Indian Ocean", quad).apply {
            material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                setBoolean("UseMaterialColors", true)
                setColor("Diffuse", ColorRGBA(0.60f, 0.95f, 1.0f, 1f))
                setColor("Ambient", ColorRGBA(0.18f, 0.42f, 0.52f, 1f))
                setColor("Specular", ColorRGBA(0.75f, 0.88f, 0.92f, 1f))
                setFloat("Shininess", 48f)
                setTexture("DiffuseMap", tex)
            }
            rotate(-FastMath.HALF_PI, 0f, 0f)
            setLocalTranslation(-40_000f, -0.035f, 40_000f)
            shadowMode = RenderQueue.ShadowMode.Receive
        }
    }

    private fun buildVrmmOrthoTiles(assets: AssetManager, scenery: XPlaneSceneryPackage): Node {
        val node = Node("VRMM Ortho4XP tiles")
        var loadedTiles = 0
        scenery.nearestOrthoTiles(28).forEach { tile ->
            val texture = scenery.loadOrthoTexture(assets, tile) ?: return@forEach
            val size = (tile.radiusMeters * 2f).coerceIn(1200f, 6200f)
            val center = scenery.localPoint(tile.center)
            node.attachChild(
                Geometry("VRMM ortho ${tile.textureEntry.substringAfterLast('/')}", Quad(size, size)).apply {
                    material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                        setBoolean("UseMaterialColors", true)
                        setColor("Diffuse", ColorRGBA.White)
                        setColor("Ambient", ColorRGBA(0.68f, 0.68f, 0.68f, 1f))
                        setColor("Specular", ColorRGBA.Black)
                        setFloat("Shininess", 1f)
                        setTexture("DiffuseMap", texture)
                    }
                    rotate(-FastMath.HALF_PI, 0f, 0f)
                    setLocalTranslation(center.x - size * 0.5f, 0.012f, center.z + size * 0.5f)
                    shadowMode = RenderQueue.ShadowMode.Receive
                },
            )
            loadedTiles++
        }
        if (loadedTiles == 0) {
            node.attachChild(buildVrmmFallbackIslands(assets, scenery))
        }
        return node
    }

    private fun buildVrmmFallbackIslands(assets: AssetManager, scenery: XPlaneSceneryPackage): Node {
        val node = Node("VRMM fallback islands")
        val sand = Texture2D(awtToJmeImage(sandTexture())).apply {
            setWrap(Texture.WrapMode.Repeat)
            minFilter = Texture.MinFilter.Trilinear
        }
        listOf(
            Triple(Vector3f(0f, 0f, 0f), Vector2f(860f, 3900f), 0f),
            Triple(Vector3f(-770f, 0f, -450f), Vector2f(380f, 980f), 0.12f),
            Triple(Vector3f(1050f, 0f, 320f), Vector2f(640f, 1700f), -0.08f),
            Triple(scenery.localPoint(XPlaneGeoPoint(4.175, 73.51)), Vector2f(760f, 2100f), 0.05f),
        ).forEachIndexed { index, (center, size, yaw) ->
            node.attachChild(
                Geometry("Male island fallback $index", JmeBox(size.x, 0.025f, size.y)).apply {
                    material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                        setBoolean("UseMaterialColors", true)
                        setColor("Diffuse", ColorRGBA(0.92f, 0.83f, 0.58f, 1f))
                        setColor("Ambient", ColorRGBA(0.54f, 0.48f, 0.34f, 1f))
                        setColor("Specular", ColorRGBA.Black)
                        setTexture("DiffuseMap", sand)
                    }
                    setLocalTranslation(center.x, 0.02f, center.z)
                    rotate(0f, yaw, 0f)
                    shadowMode = RenderQueue.ShadowMode.Receive
                },
            )
        }
        return node
    }

    private fun buildVrmmRunway(assets: AssetManager, scenery: XPlaneSceneryPackage): Node {
        val runway = scenery.runway
        val p1 = scenery.localPoint(runway.end1)
        val p2 = scenery.localPoint(runway.end2)
        val node = Node("VRMM runway ${runway.id1}/${runway.id2}")
        val asphalt = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
            setBoolean("UseMaterialColors", true)
            setColor("Diffuse", ColorRGBA(0.18f, 0.19f, 0.20f, 1f))
            setColor("Ambient", ColorRGBA(0.10f, 0.105f, 0.11f, 1f))
            setColor("Specular", ColorRGBA(0.04f, 0.04f, 0.04f, 1f))
            setFloat("Shininess", 3f)
        }
        val paint = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
            setColor("Color", ColorRGBA(0.93f, 0.93f, 0.86f, 1f))
        }
        node.attachChild(
            Geometry("VRMM runway asphalt", groundRectangleMesh(p1, p2, runway.widthMeters, 0.055f)).apply {
                material = asphalt
                shadowMode = RenderQueue.ShadowMode.Receive
            },
        )
        node.attachChild(
            Geometry("VRMM runway centerline", groundRectangleMesh(p1, p2, 1.15f, 0.075f)).apply {
                material = paint
                shadowMode = RenderQueue.ShadowMode.Off
            },
        )
        listOf(p1 to p2, p2 to p1).forEachIndexed { index, (near, far) ->
            val dir = far.subtract(near)
            dir.y = 0f
            dir.normalizeLocal()
            val barStart = near.add(dir.mult(84f))
            val barEnd = near.add(dir.mult(112f))
            node.attachChild(
                Geometry("VRMM threshold bar $index", groundRectangleMesh(barStart, barEnd, runway.widthMeters * 0.78f, 0.078f)).apply {
                    material = paint
                    shadowMode = RenderQueue.ShadowMode.Off
                },
            )
        }
        return node
    }

    private fun buildVrmmPavements(assets: AssetManager, scenery: XPlaneSceneryPackage): Node {
        val node = Node("VRMM apt.dat pavements")
        val material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
            setBoolean("UseMaterialColors", true)
            setColor("Diffuse", ColorRGBA(0.31f, 0.32f, 0.33f, 1f))
            setColor("Ambient", ColorRGBA(0.17f, 0.18f, 0.19f, 1f))
            setColor("Specular", ColorRGBA.Black)
            setFloat("Shininess", 2f)
        }
        scenery.pavements.take(90).forEachIndexed { index, polygon ->
            val mesh = pavementMesh(scenery, polygon.points, 0.045f) ?: return@forEachIndexed
            node.attachChild(
                Geometry("VRMM pavement $index", mesh).apply {
                    this.material = material
                    shadowMode = RenderQueue.ShadowMode.Receive
                },
            )
        }
        return node
    }

    private fun buildVrmmAirportObjects(assets: AssetManager, scenery: XPlaneSceneryPackage): Node {
        val node = Node("VRMM airport objects")
        val terminalMat = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
            setBoolean("UseMaterialColors", true)
            setColor("Diffuse", ColorRGBA(0.70f, 0.74f, 0.74f, 1f))
            setColor("Ambient", ColorRGBA(0.38f, 0.40f, 0.40f, 1f))
            setColor("Specular", ColorRGBA(0.06f, 0.06f, 0.06f, 1f))
            setFloat("Shininess", 6f)
        }
        val random = Random(73)
        listOf(
            XPlaneGeoPoint(4.1928, 73.5290) to Vector3f(42f, 8f, 18f),
            XPlaneGeoPoint(4.1908, 73.5290) to Vector3f(58f, 10f, 22f),
            XPlaneGeoPoint(4.1878, 73.5290) to Vector3f(46f, 7f, 16f),
            XPlaneGeoPoint(4.1992, 73.5286) to Vector3f(34f, 6f, 18f),
        ).forEachIndexed { index, (geo, halfExtents) ->
            val local = scenery.localPoint(geo)
            node.attachChild(
                Geometry("VRMM terminal block $index", JmeBox(halfExtents.x, halfExtents.y, halfExtents.z)).apply {
                    material = terminalMat
                    setLocalTranslation(local.x, halfExtents.y, local.z)
                    rotate(0f, random.nextFloat() * 0.16f - 0.08f, 0f)
                    shadowMode = RenderQueue.ShadowMode.CastAndReceive
                },
            )
        }
        return node
    }

    private fun groundRectangleMesh(a: Vector3f, b: Vector3f, width: Float, y: Float): Mesh {
        val dir = b.subtract(a)
        dir.y = 0f
        if (dir.lengthSquared() < 1e-6f) return Mesh()
        dir.normalizeLocal()
        val side = Vector3f(dir.z, 0f, -dir.x).multLocal(width * 0.5f)
        val p0 = a.add(side).apply { this.y = y }
        val p1 = a.subtract(side).apply { this.y = y }
        val p2 = b.subtract(side).apply { this.y = y }
        val p3 = b.add(side).apply { this.y = y }
        return groundMesh(listOf(p0, p1, p2, p3), intArrayOf(0, 1, 2, 0, 2, 3))
    }

    private fun pavementMesh(scenery: XPlaneSceneryPackage, points: List<XPlaneGeoPoint>, y: Float): Mesh? {
        if (points.size < 3) return null
        val vertices = points.map { scenery.localPoint(it).apply { this.y = y } }
        val indices = ArrayList<Int>()
        for (i in 1 until vertices.lastIndex) {
            indices += 0
            indices += i
            indices += i + 1
        }
        return groundMesh(vertices, indices.toIntArray())
    }

    private fun groundMesh(vertices: List<Vector3f>, indices: IntArray): Mesh {
        val positions = BufferUtils.createFloatBuffer(vertices.size * 3)
        val normals = BufferUtils.createFloatBuffer(vertices.size * 3)
        val texCoords = BufferUtils.createFloatBuffer(vertices.size * 2)
        vertices.forEach { p ->
            positions.put(p.x).put(p.y).put(p.z)
            normals.put(0f).put(1f).put(0f)
            texCoords.put(p.x * 0.01f).put(p.z * 0.01f)
        }
        positions.flip()
        normals.flip()
        texCoords.flip()
        val indexBuffer = BufferUtils.createIntBuffer(indices.size)
        indices.forEach(indexBuffer::put)
        indexBuffer.flip()
        return Mesh().apply {
            setBuffer(VertexBuffer.Type.Position, 3, positions)
            setBuffer(VertexBuffer.Type.Normal, 3, normals)
            setBuffer(VertexBuffer.Type.TexCoord, 2, texCoords)
            setBuffer(VertexBuffer.Type.Index, 3, indexBuffer)
            updateBound()
            setStatic()
        }
    }

    private fun buildTerrain(assets: AssetManager): Spatial {
        val tex = Texture2D(awtToJmeImage(proceduralGrass())).apply {
            setWrap(Texture.WrapMode.Repeat)
            minFilter = Texture.MinFilter.Trilinear
            anisotropicFilter = 4
        }
        val quad = Quad(30_000f, 30_000f)
        quad.scaleTextureCoordinates(Vector2f(520f, 520f))
        return Geometry("Terrain", quad).apply {
            material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                setBoolean("UseMaterialColors", true)
                setColor("Diffuse", ColorRGBA(0.92f, 0.95f, 0.86f, 1f))
                setColor("Ambient", ColorRGBA(0.62f, 0.66f, 0.56f, 1f))
                setColor("Specular", ColorRGBA.Black)
                setFloat("Shininess", 1f)
                setTexture("DiffuseMap", tex)
            }
            rotate(-FastMath.HALF_PI, 0f, 0f)
            setLocalTranslation(-15_000f, 0f, 15_000f)
            shadowMode = RenderQueue.ShadowMode.Receive
        }
    }

    private fun buildRunway(assets: AssetManager): Node {
        val node = Node("RunwayGroup")
        val tex = Texture2D(awtToJmeImage(runwayTexture())).apply {
            minFilter = Texture.MinFilter.Trilinear
            anisotropicFilter = 8
        }
        val quad = Quad(30f, 1400f)
        node.attachChild(
            Geometry("Runway", quad).apply {
                material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                    setBoolean("UseMaterialColors", true)
                    setColor("Diffuse", ColorRGBA.White)
                    setColor("Ambient", ColorRGBA(0.62f, 0.62f, 0.62f, 1f))
                    setColor("Specular", ColorRGBA(0.05f, 0.05f, 0.05f, 1f))
                    setFloat("Shininess", 4f)
                    setTexture("DiffuseMap", tex)
                }
                rotate(-FastMath.HALF_PI, 0f, 0f)
                setLocalTranslation(-15f, 0.04f, 700f)
                shadowMode = RenderQueue.ShadowMode.Receive
            },
        )
        // Edge lights.
        val lightMat = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
            setColor("Color", ColorRGBA(1f, 0.95f, 0.72f, 1f))
            setColor("GlowColor", ColorRGBA(0.9f, 0.82f, 0.4f, 1f))
        }
        var z = -660f
        while (z <= 660f) {
            listOf(-16.2f, 16.2f).forEach { x ->
                node.attachChild(
                    Geometry("RwyLight", Sphere(6, 6, 0.22f)).apply {
                        material = lightMat
                        setLocalTranslation(x, 0.25f, z)
                        shadowMode = RenderQueue.ShadowMode.Off
                    },
                )
            }
            z += 120f
        }
        return node
    }

    private fun buildApron(assets: AssetManager, environmentTextures: FsxEnvironmentTextureSource): Node {
        val node = Node("Apron")
        // Concrete pad west of the runway threshold area.
        node.attachChild(
            Geometry("ApronPad", Quad(120f, 160f).also { it.scaleTextureCoordinates(Vector2f(6f, 8f)) }).apply {
                material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                    setBoolean("UseMaterialColors", true)
                    setColor("Diffuse", ColorRGBA(0.52f, 0.52f, 0.54f, 1f))
                    setColor("Ambient", ColorRGBA(0.34f, 0.34f, 0.35f, 1f))
                    setColor("Specular", ColorRGBA.Black)
                    setFloat("Shininess", 1f)
                }
                rotate(-FastMath.HALF_PI, 0f, 0f)
                setLocalTranslation(-190f, 0.03f, 560f)
                shadowMode = RenderQueue.ShadowMode.Receive
            },
        )
        val hangarTexture = environmentTextures.texture("AirportHangars_Large.dds")
            ?: environmentTextures.texture("AirportHangars_Medium.dds")
        listOf(
            Triple(Vector3f(-150f, 0f, 440f), Vector3f(16f, 7f, 12f), 0f),
            Triple(Vector3f(-150f, 0f, 490f), Vector3f(16f, 7f, 12f), 0f),
            Triple(Vector3f(-145f, 0f, 545f), Vector3f(13f, 6f, 11f), 0.12f),
        ).forEachIndexed { i, (pos, size, yaw) ->
            node.attachChild(
                buildingBox(assets, "Hangar$i", size, hangarTexture, ColorRGBA(0.62f, 0.60f, 0.58f, 1f)).apply {
                    setLocalTranslation(pos.x, size.y, pos.z)
                    rotate(0f, yaw, 0f)
                },
            )
        }
        return node
    }

    private fun buildTown(assets: AssetManager, environmentTextures: FsxEnvironmentTextureSource): Node {
        val node = Node("Town")
        val walls = listOf(
            environmentTextures.texture("BLDA2.dds"),
            environmentTextures.texture("BLDC2.dds"),
            environmentTextures.texture("BLDE2.dds"),
            environmentTextures.texture("BLDG2.dds"),
        )
        val fallbacks = listOf(
            ColorRGBA(0.72f, 0.66f, 0.58f, 1f),
            ColorRGBA(0.64f, 0.60f, 0.62f, 1f),
            ColorRGBA(0.58f, 0.55f, 0.50f, 1f),
            ColorRGBA(0.70f, 0.63f, 0.55f, 1f),
        )
        val random = Random(20260716)
        repeat(40) { i ->
            val gx = 650f + random.nextFloat() * 900f
            val gz = -500f + random.nextFloat() * 1400f
            val w = 8f + random.nextFloat() * 14f
            val h = 5f + random.nextFloat() * 16f
            val d = 8f + random.nextFloat() * 14f
            val pick = random.nextInt(walls.size)
            node.attachChild(
                buildingBox(assets, "Town$i", Vector3f(w, h, d), walls[pick], fallbacks[pick]).apply {
                    setLocalTranslation(gx, h, gz)
                    rotate(0f, random.nextFloat() * FastMath.PI, 0f)
                },
            )
        }
        return node
    }

    private fun buildingBox(
        assets: AssetManager,
        name: String,
        halfExtents: Vector3f,
        texture: Texture2D?,
        fallback: ColorRGBA,
    ): Geometry =
        Geometry(name, JmeBox(halfExtents.x, halfExtents.y, halfExtents.z)).apply {
            material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                setBoolean("UseMaterialColors", true)
                if (texture != null) {
                    setColor("Diffuse", ColorRGBA.White)
                    setColor("Ambient", ColorRGBA(0.6f, 0.6f, 0.6f, 1f))
                    setTexture("DiffuseMap", texture)
                } else {
                    setColor("Diffuse", fallback)
                    setColor("Ambient", fallback.mult(0.6f))
                }
                setColor("Specular", ColorRGBA(0.05f, 0.05f, 0.05f, 1f))
                setFloat("Shininess", 4f)
            }
            shadowMode = RenderQueue.ShadowMode.CastAndReceive
        }

    private fun buildMountains(assets: AssetManager): Node {
        val node = Node("Mountains")
        val random = Random(87)
        repeat(14) { i ->
            val angle = i / 14f * FastMath.TWO_PI + random.nextFloat() * 0.3f
            val dist = 11_000f + random.nextFloat() * 7000f
            val height = 900f + random.nextFloat() * 1600f
            val radius = 2200f + random.nextFloat() * 2600f
            val shade = 0.75f + random.nextFloat() * 0.2f
            node.attachChild(
                Geometry("Mountain$i", Sphere(14, 18, radius)).apply {
                    material = Material(assets, "Common/MatDefs/Light/Lighting.j3md").apply {
                        setBoolean("UseMaterialColors", true)
                        setColor("Diffuse", ColorRGBA(0.30f * shade, 0.38f * shade, 0.34f * shade, 1f))
                        setColor("Ambient", ColorRGBA(0.22f * shade, 0.28f * shade, 0.30f * shade, 1f))
                        setColor("Specular", ColorRGBA.Black)
                        setFloat("Shininess", 1f)
                    }
                    setLocalScale(1f, height / radius, 1f)
                    setLocalTranslation(sin(angle) * dist, -radius * 0.12f, cos(angle) * dist)
                    shadowMode = RenderQueue.ShadowMode.Off
                },
            )
        }
        return node
    }

    private fun buildClouds(assets: AssetManager): Node {
        val node = Node("Clouds")
        val random = Random(4242)
        repeat(16) { i ->
            val cloud = Geometry("Cloud$i", Sphere(10, 14, 1f)).apply {
                material = Material(assets, "Common/MatDefs/Misc/Unshaded.j3md").apply {
                    setColor("Color", ColorRGBA(1f, 1f, 1f, 0.42f + random.nextFloat() * 0.2f))
                    additionalRenderState.blendMode = RenderState.BlendMode.Alpha
                    additionalRenderState.isDepthWrite = false
                }
                queueBucket = RenderQueue.Bucket.Transparent
                shadowMode = RenderQueue.ShadowMode.Off
            }
            val scale = 120f + random.nextFloat() * 260f
            cloud.setLocalScale(scale, scale * (0.16f + random.nextFloat() * 0.10f), scale * 0.8f)
            cloud.setLocalTranslation(
                -7000f + random.nextFloat() * 14_000f,
                950f + random.nextFloat() * 900f,
                -7000f + random.nextFloat() * 14_000f,
            )
            node.attachChild(cloud)
        }
        return node
    }

    private fun installPostFx(app: SimpleApplication, sun: DirectionalLight) {
        val fpp = FilterPostProcessor(app.assetManager)
        val samples = app.context.settings.samples
        if (samples > 0) runCatching { fpp.numSamples = samples }
        runCatching {
            fpp.addFilter(
                DirectionalLightShadowFilter(app.assetManager, 2048, 3).apply {
                    light = sun
                    shadowIntensity = 0.42f
                    lambda = 0.65f
                    edgeFilteringMode = EdgeFilteringMode.PCFPOISSON
                },
            )
        }
        runCatching {
            fpp.addFilter(
                FogFilter(ColorRGBA(0.66f, 0.74f, 0.84f, 1f), 1.1f, 16_000f),
            )
        }
        runCatching {
            fpp.addFilter(
                BloomFilter(BloomFilter.GlowMode.Objects).apply {
                    bloomIntensity = 0.65f
                    blurScale = 1.4f
                },
            )
        }
        runCatching { fpp.addFilter(FXAAFilter()) }
        app.viewPort.addProcessor(fpp)
    }

    /* ------------------------------------------------------------------ */
    /* Procedural textures                                                  */
    /* ------------------------------------------------------------------ */

    private fun proceduralGrass(): BufferedImage {
        val size = 256
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val random = Random(7)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val patch = (sin(x * 0.11f) + cos(y * 0.13f) + sin((x + y) * 0.05f)) * 0.5f
                val n = random.nextFloat()
                val g = (96 + patch * 22f + n * 26f).toInt().coerceIn(60, 150)
                val r = (g * 0.62f + n * 12f).toInt().coerceIn(30, 120)
                val b = (g * 0.42f).toInt().coerceIn(20, 90)
                img.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return img
    }

    private fun runwayTexture(): BufferedImage {
        val w = 256
        val h = 2048
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val random = java.util.Random(3)

        // Asphalt with noise.
        g.color = AwtColor(58, 60, 62)
        g.fillRect(0, 0, w, h)
        repeat(9000) {
            val shade = 48 + random.nextInt(26)
            g.color = AwtColor(shade, shade + 1, shade + 2)
            g.fillRect(random.nextInt(w), random.nextInt(h), 2, 2)
        }

        val paint = AwtColor(235, 235, 228)
        // Threshold piano keys, both ends.
        listOf(30, h - 78).forEach { y ->
            for (i in 0 until 8) {
                g.color = paint
                g.fillRect(14 + i * 30, y, 16, 48)
            }
        }
        // Runway designators 09 / 27.
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 72)
        g.color = paint
        g.drawString("09", w / 2 - 40, h - 110)
        val old = g.transform
        g.rotate(Math.PI, w / 2.0, 165.0)
        g.drawString("27", w / 2 - 40, 190)
        g.transform = old
        // Touchdown-zone bars.
        listOf(260, 330, h - 330, h - 400).forEach { y ->
            g.color = paint
            g.fillRect(26, y, 34, 42)
            g.fillRect(w - 60, y, 34, 42)
        }
        // Aiming point blocks.
        listOf(430, h - 500).forEach { y ->
            g.color = paint
            g.fillRect(40, y, 42, 72)
            g.fillRect(w - 82, y, 42, 72)
        }
        // Centreline dashes.
        g.color = paint
        g.stroke = BasicStroke(10f)
        var y = 150
        while (y < h - 150) {
            g.fillRect(w / 2 - 5, y, 10, 60)
            y += 120
        }
        // Edge lines.
        g.fillRect(4, 0, 5, h)
        g.fillRect(w - 9, 0, 5, h)
        g.dispose()
        return img
    }

    private fun maldivesWaterTexture(): BufferedImage {
        val size = 256
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val wave = sin(x * 0.11f + y * 0.025f) + cos(y * 0.09f) + sin((x - y) * 0.045f)
                val foam = if ((x + y * 3) % 53 < 2) 18 else 0
                val r = (24 + wave * 4f + foam * 0.2f).toInt().coerceIn(10, 60)
                val g = (124 + wave * 12f + foam).toInt().coerceIn(80, 180)
                val b = (150 + wave * 18f + foam).toInt().coerceIn(110, 220)
                img.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return img
    }

    private fun sandTexture(): BufferedImage {
        val size = 256
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val random = Random(214)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val grain = random.nextFloat() * 18f + sin((x + y) * 0.07f) * 7f
                val r = (198 + grain).toInt().coerceIn(150, 235)
                val g = (176 + grain * 0.72f).toInt().coerceIn(135, 215)
                val b = (114 + grain * 0.42f).toInt().coerceIn(80, 165)
                img.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return img
    }

    private fun awtToJmeImage(awt: BufferedImage): com.jme3.texture.Image {
        val width = awt.width
        val height = awt.height
        val buffer = BufferUtils.createByteBuffer(width * height * 4)
        for (row in height - 1 downTo 0) {
            for (x in 0 until width) {
                val p = awt.getRGB(x, row)
                buffer.put((p ushr 16 and 0xFF).toByte())
                buffer.put((p ushr 8 and 0xFF).toByte())
                buffer.put((p and 0xFF).toByte())
                buffer.put(255.toByte())
            }
        }
        buffer.flip()
        return com.jme3.texture.Image(com.jme3.texture.Image.Format.RGBA8, width, height, buffer, ColorSpace.sRGB)
    }
}

/**
 * FSX environment texture source: registers a compatible FSX texture archive
 * as a jME asset locator and serves DDS/BMP textures from it.
 */
private class FsxEnvironmentTextureSource(
    private val assets: AssetManager,
    private val environment: FsxEnvironmentPackage,
) {
    private val zipPath = environment.zipPath
    var available = false
        private set

    init {
        val path = zipPath
        if (path != null && environment.textureRoot.isNotBlank()) {
            runCatching {
                assets.registerLocator(path.toAbsolutePath().toString(), ZipLocator::class.java)
                available = true
            }
        }
    }

    fun texture(name: String): Texture2D? {
        if (!available) return null
        return runCatching {
            val key = TextureKey("${environment.textureRoot.trimEnd('/')}/$name", false)
            key.isGenerateMips = true
            (assets.loadTexture(key) as? Texture2D)?.apply {
                setWrap(Texture.WrapMode.Repeat)
                minFilter = Texture.MinFilter.Trilinear
            }
        }.getOrNull()
    }
}
