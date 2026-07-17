package com.dhc6trainer.desktop

/**
 * Headless verification of the Full Simulator integration: FGDesktop workspace
 * detection, personalized DHC-6 source resolution and deployment state. Does
 * NOT start fgfs. Run with:
 *   gradlew runFlightGearSmokeTest
 * Pass -Dsmoke.deploy=true to also deploy the personalized DHC-6 into the
 * workspace (this replaces app-data\aircraft\dhc6 and prunes other aircraft).
 */
fun main() {
    var failures = 0

    fun check(label: String, ok: Boolean, detail: String) {
        if (!ok) failures++
        println("${if (ok) "OK   " else "FAIL "} $label -> $detail")
    }

    val root = FlightGearInstallation.root
    check("FGDesktop workspace", root != null, root?.absolutePath ?: "not found")
    check(
        "simulator executable built",
        FlightGearInstallation.fgfsBuilt(),
        "install/bin/${FlightGearInstallation.simulatorExecutableName()}",
    )
    check("fgdata present", FlightGearInstallation.fgdataPresent(), "deps/fgdata/defaults.xml")

    val sourceDir = Dhc6AircraftDeployment.sourceDir
    check("personalized DHC-6 source", Dhc6AircraftDeployment.sourceAvailable(), sourceDir?.absolutePath ?: "classpath jar")
    val sourceRevision = Dhc6AircraftDeployment.sourceRevision()
    check("source revision", sourceRevision != null, sourceRevision ?: "missing .revision")

    val variants = Dhc6AircraftDeployment.variants()
    check("DHC-6 variants", variants.isNotEmpty(), variants.joinToString(", "))
    check("variants are DHC-6-only", variants.all { it.startsWith("dhc6") }, variants.joinToString(", "))

    // Free-flight aircraft: the corrected/personalized DHC-6 must be found and
    // its AC3D models (exterior + flight deck with instruments) must build.
    val aircraft = OpenSourceSimLibrary.loadAuto().flightGearAircraft
    check("free-flight aircraft package", aircraft != null, aircraft?.statusBadge ?: "not found")
    if (aircraft != null) {
        check("loaded from corrected source", aircraft.sourceIsDirectory, aircraft.zipPath.toString())
        check(
            "visual models resolved",
            aircraft.visualEntries.size >= 3,
            aircraft.visualEntries.joinToString(", ") { it.substringAfterLast('/') },
        )
        val assets = com.jme3.asset.DesktopAssetManager(true)
        val model = FlightGearAc3dLoader.loadAircraft(assets, aircraft)
        var geometries = 0
        var flightdeckFound = false
        model?.depthFirstTraversal { spatial ->
            if (spatial is com.jme3.scene.Geometry) geometries++
            if (spatial.name?.startsWith("flightdeck", ignoreCase = true) == true) flightdeckFound = true
        }
        check("AC3D scene built", model != null && geometries > 0, "$geometries geometries")
        check("flight deck (inside view panel) present", flightdeckFound, "node 'flightdeck.ac'")

        // -Dsmoke.probe=true: dump top-level children and untextured geometry
        // bounds to identify meshes that obstruct the cockpit view.
        if (System.getProperty("smoke.probe") == "true" && model is com.jme3.scene.Node) {
            model.updateGeometricState()
            println("      top-level children:")
            model.children.forEach { child ->
                val bound = child.worldBound
                println("        ${child.name} -> $bound")
            }
            println("      untextured geometries with extent > 0.3:")
            model.depthFirstTraversal { spatial ->
                val geom = spatial as? com.jme3.scene.Geometry ?: return@depthFirstTraversal
                val hasTexture = geom.material?.getTextureParam("DiffuseMap") != null
                val bound = geom.worldBound as? com.jme3.bounding.BoundingBox ?: return@depthFirstTraversal
                val extent = maxOf(bound.xExtent, bound.yExtent, bound.zExtent)
                if (!hasTexture && extent > 0.3f) {
                    var path = geom.name ?: "?"
                    var parent = geom.parent
                    while (parent != null && parent !== model) {
                        path = "${parent.name}/$path"
                        parent = parent.parent
                    }
                    println("        $path center=${bound.center} extent=$extent")
                }
            }
        }
    }

    if (System.getProperty("smoke.deploy") == "true") {
        val error = Dhc6AircraftDeployment.sync { copied -> println("      ... $copied files copied") }
        check("deployment", error == null, error ?: "deployed revision ${Dhc6AircraftDeployment.deployedRevision()}")
        val aircraftRoot = Dhc6AircraftDeployment.deployedDir()?.parentFile
        val contents = aircraftRoot?.listFiles()?.map { it.name }.orEmpty()
        check("sim aircraft dir is DHC-6-only", contents == listOf("dhc6"), contents.joinToString(", "))
    }
    println("deployment currently needed: ${Dhc6AircraftDeployment.syncNeeded()}")

    if (failures > 0) {
        println("$failures check(s) failed")
        kotlin.system.exitProcess(1)
    }
    println("Full Simulator integration checks passed")
}
