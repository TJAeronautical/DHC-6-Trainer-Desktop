package com.dhc6trainer.desktop

import com.jme3.asset.DesktopAssetManager
import com.jme3.asset.plugins.ClasspathLocator
import com.jme3.scene.Geometry
import com.jme3.scene.Spatial

/**
 * Headless verification of every model/asset pipeline the app uses, without
 * opening a window or GL context. Run with:
 *   gradlew :desktop-app:runAssetSmokeTest
 */
fun main() {
    var failures = 0

    fun check(label: String, block: () -> String?) {
        val result = runCatching(block)
        val detail = result.getOrNull()

        when {
            result.isFailure -> {
                failures++
                val error = result.exceptionOrNull()
                val causes = generateSequence(error) { it.cause }
                    .joinToString(" <- ") {
                        "${it.javaClass.simpleName}: ${it.message}"
                    }

                println("FAIL  $label -> $causes")

                if (System.getProperty("smoke.stacktrace") != null) {
                    error?.printStackTrace()
                }
            }

            detail == null -> {
                failures++
                println("FAIL  $label -> returned null")
            }

            else -> println("OK    $label -> $detail")
        }
    }

    com.jme3.util.BufferUtils.setTrackDirectMemoryEnabled(true)

    val assets = DesktopAssetManager(true).apply {
        registerLocator("/", ClasspathLocator::class.java)
        registerLoader(CachingGltfLoader::class.java, "gltf")
        registerLoader(CachingGlbLoader::class.java, "glb")
        FsxMdlLoaderRegistry.register(this)
    }

    println("=== Systems Lab GLB models ===")

    val allGlbPaths = object {}.javaClass.classLoader
        .getResourceAsStream("desktop-asset-index.txt")
        ?.bufferedReader()
        ?.readLines()
        .orEmpty()
        .filter {
            it.startsWith("assets/models/") &&
                    it.endsWith(".glb")
        }

    val onlyFilter = System.getProperty("smoke.only")

    val glbPaths = if (onlyFilter.isNullOrBlank()) {
        allGlbPaths
    } else {
        allGlbPaths.filter {
            it.contains(onlyFilter, ignoreCase = true)
        }
    }

    if (glbPaths.isEmpty()) {
        println("FAIL  no GLB entries found in desktop-asset-index.txt")
        failures++
    }

    val directPool = java.lang.management.ManagementFactory
        .getPlatformMXBeans(java.lang.management.BufferPoolMXBean::class.java)
        .firstOrNull { it.name == "direct" }

    glbPaths.forEach { path ->
        check(path.substringAfterLast('/')) {
            val metrics = run {
                val before = directPool?.memoryUsed ?: 0L
                val model = assets.loadModel(path)
                val after = directPool?.memoryUsed ?: 0L
                val geometryCount = countGeometries(model)

                var meshBytes = 0L
                var biggest = 0L
                var biggestDesc = ""

                model.depthFirstTraversal { spatial ->
                    val geometry = spatial as? Geometry
                        ?: return@depthFirstTraversal

                    for (vertexBuffer in geometry.mesh.bufferList) {
                        val data = vertexBuffer.data ?: continue
                        val bytes =
                            data.capacity().toLong() *
                                    vertexBuffer.format.componentSize

                        meshBytes += bytes

                        if (bytes > biggest) {
                            biggest = bytes
                            biggestDesc =
                                "${geometry.name}/${vertexBuffer.bufferType} " +
                                        "cap=${data.capacity()} " +
                                        "fmt=${vertexBuffer.format} " +
                                        "comps=${vertexBuffer.numComponents}"
                        }
                    }
                }

                if (meshBytes > 100_000_000L) {
                    println(
                        "      mesh buffers total " +
                                "${meshBytes / 1_048_576L} MB; " +
                                "biggest: $biggestDesc",
                    )
                }

                if ((directPool?.memoryUsed ?: 0L) > 1_000_000_000L) {
                    val memoryReport = StringBuilder()
                    com.jme3.util.BufferUtils.printCurrentDirectMemory(
                        memoryReport,
                    )

                    println(
                        "      jME tracked direct memory:\n$memoryReport",
                    )

                    runCatching {
                        val field =
                            com.jme3.util.BufferUtils::class.java
                                .getDeclaredField("trackedBuffers")

                        field.isAccessible = true

                        val trackedBuffers =
                            field.get(null) as
                                    java.util.concurrent.ConcurrentHashMap<*, *>

                        val histogram =
                            HashMap<Pair<String, Int>, Int>()

                        for (info in trackedBuffers.values) {
                            val sizeField =
                                info.javaClass
                                    .getDeclaredField("size")
                                    .apply {
                                        isAccessible = true
                                    }

                            val typeField =
                                info.javaClass
                                    .getDeclaredField("type")
                                    .apply {
                                        isAccessible = true
                                    }

                            val size = sizeField.getInt(info)
                            val type =
                                (typeField.get(info) as Class<*>).simpleName

                            histogram.merge(
                                type to size,
                                1,
                                Int::plus,
                            )
                        }

                        histogram.entries
                            .sortedByDescending {
                                it.key.second.toLong() * it.value
                            }
                            .take(12)
                            .forEach { (key, count) ->
                                val totalMegabytes =
                                    key.second.toLong() *
                                            count /
                                            1_048_576L

                                println(
                                    "      ${key.first} " +
                                            "size=${key.second} " +
                                            "x$count = $totalMegabytes MB",
                                )
                            }
                    }.onFailure {
                        println("      histogram failed: $it")
                    }
                }

                System.gc()
                Thread.sleep(150)

                val retainedWithModel =
                    directPool?.memoryUsed ?: 0L

                AssetLoadMetrics(
                    geometryCount = geometryCount,
                    directMemoryIncrease = after - before,
                    retainedWithModel = retainedWithModel,
                )
            }

            /*
             * The loaded model existed only inside the run block above.
             * At this point there is no local strong reference to it.
             */
            assets.clearCache()
            System.gc()
            Thread.sleep(150)

            val retainedAfterFree =
                directPool?.memoryUsed ?: 0L

            "${metrics.geometryCount} geometries, " +
                    "direct +${metrics.directMemoryIncrease / 1_048_576L} MB, " +
                    "retained ${metrics.retainedWithModel / 1_048_576L} MB, " +
                    "after free ${retainedAfterFree / 1_048_576L} MB"
        }
    }

    check("Technical Lab model coverage") {
        val catalog = DesktopAssetCatalog.load()

        val displayedPaths = catalog.systemGroups.flatMapTo(hashSetOf()) {
            it.matchedAssets
        }

        val realSystemModels = allGlbPaths.filter {
            it.startsWith("assets/models/systems_lab/") &&
                    !it.contains("/placeholders/") &&
                    !it.contains(
                        "placeholder",
                        ignoreCase = true,
                    )
        }

        val missing = realSystemModels.filterNot(
            displayedPaths::contains,
        )

        require(missing.isEmpty()) {
            "not displayed: ${missing.joinToString()}"
        }

        "${realSystemModels.size}/${realSystemModels.size} " +
                "real GLB models assigned across " +
                "${catalog.systemGroups.size} groups"
    }

    check("Desktop-owned simulator variants") {
        val session = FreeFlightSession()
        val options = session.availableAircraftOptions

        require(
            options.any {
                it.id == FreeFlightDhc6Variant.Wheels.aircraftId
            },
        ) {
            "Wheels option missing"
        }

        require(
            options.any {
                it.id == FreeFlightDhc6Variant.Floats.aircraftId
            },
        ) {
            "Floats option missing"
        }

        val expectedDefault =
            session.xPlaneAircraftPackages
                .firstOrNull {
                    it.id ==
                            XPlaneTwinOtterVariantLibrary.preferredVariantId
                }
                ?.id
                ?: session.xPlaneAircraftPackages
                    .firstOrNull()
                    ?.id
                ?: FreeFlightDhc6Variant.Wheels.aircraftId

        require(session.selectedVariantId == expectedDefault) {
            "$expectedDefault is not the default"
        }

        "${options.first().label} default; " +
                options.joinToString { it.label }
    }

    println()
    println("=== Local simulator packages (Downloads detection) ===")

    check("FSX environment package") {
        val environment = FsxEnvironmentLibrary.loadAuto()
        "${environment.id}: ${environment.summary}"
    }

    check("Open-source sim snapshot") {
        val snapshot = OpenSourceSimLibrary.loadAuto()

        buildString {
            append("jsbsim=${snapshot.jsbsim != null}")
            append(", flightgear=${snapshot.flightGear != null}")
            append(
                ", fgAircraft=" +
                        "${snapshot.flightGearAircraft != null}",
            )

            snapshot.primaryDhc6Profile?.let { profile ->
                append(
                    " | profile: ${profile.aircraftName}, " +
                            "${profile.totalMaxPowerHp} hp total, " +
                            "wing ${profile.wingAreaFt2} ft2",
                )
            }
        }
    }

    check("FlightGear DHC-6 AC3D geometry") {
        val aircraft =
            OpenSourceSimLibrary.loadAuto().flightGearAircraft
                ?: return@check "not present (skipped)"

        val model =
            FlightGearAc3dLoader.loadAircraft(assets, aircraft)
                ?: throw IllegalStateException(
                    "AC3D load returned null",
                )

        (model as? com.jme3.scene.Node)
            ?.children
            ?.forEach { child ->
                val bounds =
                    child.worldBound as?
                            com.jme3.bounding.BoundingBox

                if (bounds != null) {
                    println(
                        "      ${child.name}: " +
                                "centre(%.2f %.2f %.2f) " +
                                "extents(%.2f %.2f %.2f)"
                                    .format(
                                        bounds.center.x,
                                        bounds.center.y,
                                        bounds.center.z,
                                        bounds.xExtent,
                                        bounds.yExtent,
                                        bounds.zExtent,
                                    ),
                    )
                }
            }

        "${countGeometries(model)} geometries from " +
                "${aircraft.visualEntries.size} .ac files"
    }

    check("Personalized DHC-6 airframe variants") {
        FreeFlightDhc6Variant.entries.joinToString {
            "${it.aircraftId}=${it.label}"
        }
    }

    val localFsxAircraft =
        FsxAircraftPackageLibrary.loadAllAuto()

    check("Local FSX Twin Otter reference packs") {
        if (localFsxAircraft.isEmpty()) {
            "not present (skipped)"
        } else {
            localFsxAircraft.joinToString {
                "${it.id}: " +
                        "native=${it.nativeModelSupported}, " +
                        "liveries=${it.liveryTitles.size}"
            }
        }
    }

    localFsxAircraft
        .filter {
            it.nativeModelSupported &&
                    it.id != "air-alpes-fsx"
        }
        .forEach { aircraft ->
            check("${aircraft.label} local reference data") {
                val visual =
                    LocalAircraftModelLibrary.sourceLabel(
                        aircraft.id,
                    )

                "${aircraft.liveryTitles.size} liveries, " +
                        "${aircraft.panelConfigCount} panel configs, " +
                        "visual=$visual"
            }
        }

    val localXPlaneAircraft =
        XPlaneTwinOtterVariantLibrary.loadAuto()

    check("Local X-Plane Twin Otter interiors") {
        if (localXPlaneAircraft.isEmpty()) {
            "not present (skipped)"
        } else {
            localXPlaneAircraft.joinToString {
                "${it.id}: " +
                        "cockpit=${it.cockpitObjectEntry != null}, " +
                        "objects=${it.objectCount}"
            }
        }
    }

    localXPlaneAircraft.forEach { aircraft ->
        check("${aircraft.label} OBJ8 geometry") {
            val loaded =
                XPlaneObj8Loader.loadAircraft(assets, aircraft)
                    ?: throw IllegalStateException(
                        "OBJ8 load returned null",
                    )

            val cockpitGeometries =
                countGeometries(loaded.cockpitNode)

            val exteriorGeometries =
                countGeometries(loaded.root) -
                        cockpitGeometries

            var texturedGeometries = 0

            loaded.root.depthFirstTraversal { spatial ->
                val geometry = spatial as? Geometry
                    ?: return@depthFirstTraversal

                if (
                    geometry.material
                        .getTextureParam("DiffuseMap") != null
                ) {
                    texturedGeometries++
                }
            }

            require(loaded.interiorNodes.size >= 2) {
                "cabin or seat geometry missing"
            }

            require(cockpitGeometries >= 2) {
                "cockpit surface or instrument panel geometry missing"
            }

            require(
                texturedGeometries >=
                        exteriorGeometries +
                        cockpitGeometries,
            ) {
                "untextured OBJ8 geometry detected"
            }

            "$exteriorGeometries exterior, " +
                    "$cockpitGeometries cockpit, " +
                    "${loaded.interiorNodes.size} cabin/seat objects, " +
                    "$texturedGeometries textured"
        }
    }

    check("X-Plane VRMM scenery package") {
        val scenery =
            XPlaneSceneryLibrary.loadAuto()
                ?: return@check "not present (skipped)"

        "${scenery.statusBadge}, " +
                "${scenery.pavements.size} pavements"
    }

    check("JSBSim-derived flight model params") {
        val snapshot = OpenSourceSimLibrary.loadAuto()

        val params =
            snapshot.primaryDhc6Profile
                ?.let(Dhc6Params::fromJsbsimProfile)
                ?: Dhc6Params.fromBundledAircraftCfg()

        params.sourceLabel
    }

    println()

    println(
        if (failures == 0) {
            "ALL CHECKS PASSED"
        } else {
            "$failures CHECK(S) FAILED"
        },
    )

    if (failures > 0) {
        kotlin.system.exitProcess(1)
    }
}

private data class AssetLoadMetrics(
    val geometryCount: Int,
    val directMemoryIncrease: Long,
    val retainedWithModel: Long,
)

private fun countGeometries(model: Spatial): Int {
    var count = 0

    model.depthFirstTraversal {
        if (it is Geometry) {
            count++
        }
    }

    return count
}