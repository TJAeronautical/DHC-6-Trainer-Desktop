package com.dhc6trainer.desktop

internal enum class DesktopCockpitVariant {
    LEGACY,
    G950,
    BOTH
}

internal data class CockpitAssetTarget(
    val title: String,
    val variant: DesktopCockpitVariant,
    val zone: String,
    val description: String,
    val linkedFlow: String,
)

internal data class SystemAssetGroup(
    val name: String,
    val family: String,
    val description: String,
    val matchedAssets: List<String>,
)

internal data class PerformancePlanningMode(
    val title: String,
    val description: String,
    val status: String,
    val primaryResult: String,
    val secondaryResult: String,
    val nextWiring: String,
)

internal data class DesktopAssetCatalogSnapshot(
    val allResourcePaths: List<String>,
    val cockpitAssetCount: Int,
    val systemsAssetCount: Int,
    val cockpitTargets: List<CockpitAssetTarget>,
    val systemGroups: List<SystemAssetGroup>,
    val performanceModes: List<PerformancePlanningMode>,
)

internal object DesktopAssetCatalog {
    fun load(): DesktopAssetCatalogSnapshot {
        val resourcePaths = readResourceIndex()
        val cockpitAssetCount = resourcePaths.count { path ->
            path.contains("cockpit", ignoreCase = true) ||
                path.contains("hitbox", ignoreCase = true) ||
                path.contains("panel", ignoreCase = true)
        }
        val systemsAssetCount = resourcePaths.count { path ->
            path.contains("systems/", ignoreCase = true) ||
                path.contains("pt6", ignoreCase = true) ||
                path.contains("propeller", ignoreCase = true)
        }

        return DesktopAssetCatalogSnapshot(
            allResourcePaths = resourcePaths,
            cockpitAssetCount = cockpitAssetCount,
            systemsAssetCount = systemsAssetCount,
            cockpitTargets = cockpitTargets(),
            systemGroups = systemGroups(resourcePaths),
            performanceModes = performanceModes(),
        )
    }

    private fun readResourceIndex(): List<String> {
        val stream = Thread.currentThread().contextClassLoader
            ?.getResourceAsStream("desktop-asset-index.txt")
            ?: DesktopAssetCatalog::class.java.classLoader.getResourceAsStream("desktop-asset-index.txt")
            ?: return emptyList()

        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
        }
    }

    private fun matchAssets(paths: List<String>, vararg keywords: String): List<String> {
        return paths.filter { path -> keywords.any { key -> path.contains(key, ignoreCase = true) } }
            .distinct()
            .sortedWith(
                compareByDescending<String> { it.isRealSystemsLabModelPath() }
                    .thenByDescending { it.isSystemsLabModelPath() }
                    .thenByDescending { it.isImageAssetPath() }
                    .thenByDescending { it.isPreferredSystemAssetPath() }
                    .thenBy { it }
            )
            .take(24)
    }

    private fun String.isSystemsLabModelPath(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".glb") || lower.endsWith(".gltf")
    }

    private fun String.isRealSystemsLabModelPath(): Boolean {
        val lower = lowercase()
        return isSystemsLabModelPath() && lower.contains("assets/models/systems_lab/") && !lower.contains("/placeholders/") && !lower.contains("placeholder")
    }

    private fun String.isImageAssetPath(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }

    private fun String.isPreferredSystemAssetPath(): Boolean {
        val lower = lowercase()
        return lower.contains("assets/systems/") ||
            lower.contains("systems/posters") ||
            lower.contains("systems/maps") ||
            lower.contains("system") ||
            lower.contains("poster") ||
            lower.contains("map")
    }
    private fun cockpitTargets(): List<CockpitAssetTarget> = listOf(
        CockpitAssetTarget(
            title = "Power lever quadrant",
            variant = DesktopCockpitVariant.BOTH,
            zone = "Centre pedestal",
            description = "Large-screen target for power levers, beta/reverse awareness, and drill callout linking.",
            linkedFlow = "Engine failure, rejected takeoff, landing roll, and beta range awareness.",
        ),
        CockpitAssetTarget(
            title = "Fuel panel / boost pump area",
            variant = DesktopCockpitVariant.LEGACY,
            zone = "Overhead / fuel controls",
            description = "Target block for boost pumps, selectors, crossfeed and QRH fuel procedure linking.",
            linkedFlow = "Fuel low level, boost pump failure, crossfeed and engine flameout review.",
        ),
        CockpitAssetTarget(
            title = "Electrical DC generation panel",
            variant = DesktopCockpitVariant.LEGACY,
            zone = "Overhead electrical",
            description = "Desktop target for generator, battery, external power and bus-failure study.",
            linkedFlow = "DC generator failure, battery/external power and bus-tie review.",
        ),
        CockpitAssetTarget(
            title = "G950 PFD / MFD scan",
            variant = DesktopCockpitVariant.G950,
            zone = "Forward instrument panel",
            description = "Variant-specific avionics scan target for future G950 cockpit overlay and alert review.",
            linkedFlow = "CAS review, abnormal annunciations and navigation/display awareness.",
        ),
        CockpitAssetTarget(
            title = "Annunciator / CAS focus",
            variant = DesktopCockpitVariant.BOTH,
            zone = "Alerting system",
            description = "Target group for alert recognition, MCC callouts and procedure-to-panel highlighting.",
            linkedFlow = "CAS/annunciator callout trainer and QRH procedure entry selection.",
        ),
        CockpitAssetTarget(
            title = "Flap / hydraulic control focus",
            variant = DesktopCockpitVariant.BOTH,
            zone = "Hydraulic and flap controls",
            description = "Target block for flap selection, hydraulic pressure, brake/steering study and landing abnormal flows.",
            linkedFlow = "Hydraulic failure, flap abnormal and landing configuration review.",
        ),
    )

    private fun systemGroups(paths: List<String>): List<SystemAssetGroup> = listOf(
        SystemAssetGroup(
            name = "PT6A / Powerplant",
            family = "Engine",
            description = "PT6A sections, airflow, stations, FCU, starting, oil and torque-prop oil study assets.",
            matchedAssets = matchAssets(paths, "pt6", "engine", "powerplant", "fcu", "torque", "oil"),
        ),
        SystemAssetGroup(
            name = "Propeller / Beta / Autofeather",
            family = "Propeller",
            description = "Propeller governor, beta range, autofeather and CSU training references.",
            matchedAssets = matchAssets(paths, "prop", "beta", "autofeather", "csu", "governor"),
        ),
        SystemAssetGroup(
            name = "Fuel system",
            family = "Fuel",
            description = "Fuel tanks, boost pumps, selectors, crossfeed and fuel-flow procedure references.",
            matchedAssets = matchAssets(paths, "fuel", "tank", "boost", "crossfeed"),
        ),
        SystemAssetGroup(
            name = "Electrical system",
            family = "Electrical",
            description = "DC generation, battery, external power, current limiters, RCCB and bus maps.",
            matchedAssets = matchAssets(paths, "electrical", "generator", "battery", "bus", "rccb", "current"),
        ),
        SystemAssetGroup(
            name = "Hydraulics / Flaps / Brakes",
            family = "Hydraulics",
            description = "Hydraulic package, hand pump, brakes, steering, flap drive and associated QRH links.",
            matchedAssets = matchAssets(paths, "hydraulic", "flap", "brake", "steering", "hand_pump"),
        ),
        SystemAssetGroup(
            name = "Flight controls",
            family = "FlightControls",
            description = "Cable/pulley primary controls, control surfaces, trim/servo tabs and hydraulic flap references.",
            matchedAssets = matchAssets(
                paths,
                "flight_controls",
                "aileron",
                "elevator",
                "rudder",
                "trim"
            ),
        ),
        SystemAssetGroup(
            name = "Flight controls",
            family = "FlightControls",
            description = "Cable/pulley primary controls, control surfaces, trim/servo tabs and hydraulic flap references.",
            matchedAssets = matchAssets(paths, "flight_controls", "aileron", "elevator", "rudder", "trim"),
        ),
        SystemAssetGroup(
            name = "Fire protection",
            family = "Fire",
            description = "Detection, extinguishing, bottle/agent awareness and engine-fire QRH bridge.",
            matchedAssets = matchAssets(paths, "fire", "extinguish", "bottle", "detector"),
        ),
        SystemAssetGroup(
            name = "Landing gear / Tyres",
            family = "LandingGear",
            description = "Main gear, nosewheel, tyre handling and ground operations references for the Series 300.",
            matchedAssets = matchAssets(paths, "landing_gear", "nosewheel", "undercarriage"),
        ),
        SystemAssetGroup(
            name = "Bleed air / Pneumatics",
            family = "Pneumatics",
            description = "Engine bleed-air supply, wing and cowl anti-ice, cabin conditioning and pressurisation references.",
            matchedAssets = matchAssets(paths, "bleed", "pneumatic", "anti_ice", "conditioning"),
        ),
        SystemAssetGroup(
            name = "Aircraft variants",
            family = "Aircraft",
            description = "Full DHC-6 Series 300 model in wheels, floats and ski configurations for variant and systems overview.",
            matchedAssets = matchAssets(paths, "aircraft_variant", "dhc6_wheel", "dhc6_float", "dhc6_ski", "float_system"),
        ),
    )

    private fun performanceModes(): List<PerformancePlanningMode> = listOf(
        PerformancePlanningMode(
            title = "Takeoff",
            description = "Desktop input layout for elevation, OAT, wind, surface, weight and configuration.",
            status = "TO shell",
            primaryResult = "1,050 FT",
            secondaryResult = "2,450 FT",
            nextWiring = "Share Android takeoff calculation model, validation ranges and configuration warnings.",
        ),
        PerformancePlanningMode(
            title = "Landing",
            description = "Landing distance and VREF planning view with surface and wind input slots.",
            status = "LDG shell",
            primaryResult = "980 FT",
            secondaryResult = "2,100 FT",
            nextWiring = "Connect landing distance, VREF and abnormal flap/configuration variants.",
        ),
        PerformancePlanningMode(
            title = "Mass / balance",
            description = "Future payload and CG planning layout for desktop study and instructor review.",
            status = "W&B shell",
            primaryResult = "TBD",
            secondaryResult = "TBD",
            nextWiring = "Wire payload stations, CG envelope and exportable planning summary later.",
        ),
    )
}


