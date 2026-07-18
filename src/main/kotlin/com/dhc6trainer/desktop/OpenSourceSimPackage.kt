package com.dhc6trainer.desktop

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal data class OpenSourceSimSnapshot(
    val jsbsim: JsbsimPackage?,
    val flightGear: FlightGearPackage?,
    val flightGearAircraft: FlightGearAircraftPackage?,
) {
    val hasAny: Boolean get() = jsbsim != null || flightGear != null || flightGearAircraft != null
    val primaryDhc6Profile: JsbsimDhc6Profile?
        get() = flightGearAircraft?.jsbsimProfile ?: jsbsim?.dhc6Profile
    val primaryStatus: String
        get() = flightGearAircraft?.statusBadge ?: jsbsim?.statusBadge ?: "Internal DHC-6 FDM"
    val settingsSummary: String
        get() = buildList {
            add(flightGearAircraft?.summary ?: "FlightGear DHC-6 aircraft not found. Place dhc6-master.zip in Downloads or set DHC6_FLIGHTGEAR_AIRCRAFT_ZIP.")
            add(jsbsim?.summary ?: "JSBSim not found. Place jsbsim-master.zip in Downloads or set DHC6_JSBSIM_ZIP.")
            add(flightGear?.summary ?: "FlightGear source not found. Place flightgear-next.zip in Downloads or set DHC6_FLIGHTGEAR_ZIP.")
        }.joinToString("\n\n")
}

internal data class JsbsimPackage(
    val zipPath: Path,
    val aircraftEntry: String,
    val engineEntry: String,
    val propellerEntry: String,
    val systemEntries: List<String>,
    val dhc6Profile: JsbsimDhc6Profile,
) {
    val licenseLabel: String = "LGPL-2.1 JSBSim source/data"
    val statusBadge: String = "JSBSim DHC-6 FDM"
    val summary: String =
        "$statusBadge loaded from ${zipPath.fileName} - ${dhc6Profile.engineCount} PT6A-27 engines, " +
            "${dhc6Profile.wingAreaFt2.clean()} sq ft wing, ${dhc6Profile.maxGrossWeightLb.clean()} lb max weight, " +
            "${systemEntries.size} DHC-6 system files. $licenseLabel."
}

internal data class JsbsimDhc6Profile(
    val aircraftName: String,
    val description: String,
    val wingAreaFt2: Float,
    val wingSpanFt: Float,
    val chordFt: Float,
    val emptyWeightLb: Float,
    val maxGrossWeightLb: Float,
    val stallSpeedKts: Float?,
    val cl0: Float?,
    val clAlphaPerRad: Float?,
    val cd0: Float?,
    val inducedDragK: Float?,
    val engineCount: Int,
    val engineMaxPowerHp: Float,
    val engineMilThrustLb: Float,
    val propDiameterIn: Float,
    val propBlades: Int,
    val propMinRpm: Float,
    val propMaxRpm: Float,
    val fuelCapacityLb: Float,
) {
    val averageMissionWeightLb: Float = (emptyWeightLb + maxGrossWeightLb) / 2f
    val totalMaxPowerHp: Float = engineMaxPowerHp * engineCount
    val totalStaticThrustLb: Float = engineMilThrustLb * engineCount
}

internal data class FlightGearPackage(
    val zipPath: Path,
    val sourceEntryCount: Int,
    val hasJsbsimBridge: Boolean,
    val hasTerraSync: Boolean,
    val hasBaseData: Boolean,
) {
    val licenseLabel: String = "GPL-2.0 FlightGear source"
    val statusBadge: String =
        if (hasBaseData) "FlightGear source/data" else "FlightGear source"
    val summary: String = buildString {
        append("$statusBadge loaded from ${zipPath.fileName} - $sourceEntryCount files. ")
        append(if (hasJsbsimBridge) "JSBSim bridge present. " else "JSBSim bridge not found. ")
        append(if (hasTerraSync) "TerraSync source present. " else "TerraSync source not found. ")
        append(if (hasBaseData) "Base aircraft/scenery data detected. " else "No FlightGear Base aircraft/scenery data detected. ")
        append(licenseLabel)
    }
}

/**
 * Read-only archive of a FlightGear aircraft: a zip file (legacy dhc6-master.zip
 * downloads) or an on-disk folder (the corrected/personalized aircraft vendored
 * at flight-sim/dhc6). Entry names use '/' separators and are rooted one level
 * above the aircraft folder (e.g. "dhc6/Models/dhc-6.ac"), matching zip layout.
 */
internal interface FgAircraftArchive : AutoCloseable {
    fun entryNames(): List<String>
    fun readBytes(entryName: String): ByteArray?
    fun readText(entryName: String): String? = readBytes(entryName)?.toString(Charsets.UTF_8)
}

internal class ZipFgAircraftArchive(path: Path) : FgAircraftArchive {
    private val zip = ZipFile(path.toFile())
    private val byName = zip.entries().asSequence()
        .filterNot { it.isDirectory }
        .associateBy { it.name.replace('\\', '/') }

    override fun entryNames(): List<String> = byName.keys.toList()

    override fun readBytes(entryName: String): ByteArray? {
        val entry = byName[entryName]
            ?: byName.entries.firstOrNull { it.key.equals(entryName, ignoreCase = true) }?.value
            ?: return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    override fun close() = zip.close()
}

internal class DirFgAircraftArchive(private val root: Path) : FgAircraftArchive {
    private val prefix = root.fileName.toString()
    private val names: List<String> by lazy {
        val rootFile = root.toFile()
        rootFile.walkTopDown()
            .filter { it.isFile }
            .map { "$prefix/" + it.relativeTo(rootFile).path.replace('\\', '/') }
            .toList()
    }
    private val byLower: Map<String, String> by lazy { names.associateBy { it.lowercase() } }

    override fun entryNames(): List<String> = names

    override fun readBytes(entryName: String): ByteArray? {
        val actual = byLower[entryName.lowercase()] ?: return null
        val file = root.resolve(actual.removePrefix("$prefix/"))
        return runCatching { Files.readAllBytes(file) }.getOrNull()
    }

    override fun close() {}
}

internal data class FlightGearAircraftPackage(
    val id: String,
    val label: String,
    val zipPath: Path,
    val sourceIsDirectory: Boolean,
    val setEntry: String,
    val yasimEntry: String,
    val jsbsimEntry: String,
    val modelEntry: String,
    val visualEntries: List<String>,
    val acGeometryCount: Int,
    val cockpitAcGeometryCount: Int,
    val textureCount: Int,
    val xmlCount: Int,
    val nasalScriptCount: Int,
    val previewCount: Int,
    val storedPropertyCount: Int,
    val description: String,
    val flightModel: String,
    val jsbsimProfile: JsbsimDhc6Profile?,
) {
    val licenseLabel: String = "GPL-2.0 FlightGear aircraft"
    val statusBadge: String =
        if (sourceIsDirectory) "Personalized DHC-6 aircraft" else "FlightGear DHC-6 aircraft"

    fun openArchive(): FgAircraftArchive =
        if (sourceIsDirectory) DirFgAircraftArchive(zipPath) else ZipFgAircraftArchive(zipPath)

    val summary: String = buildString {
        append("$statusBadge loaded from ${zipPath.fileName} - ")
        append("$acGeometryCount AC3D models, $cockpitAcGeometryCount cockpit models, ")
        append("$textureCount textures, $xmlCount XML files, $nasalScriptCount Nasal scripts, ")
        append("$storedPropertyCount stored cockpit/system properties. ")
        append("Primary model $modelEntry, ${visualEntries.size} direct visual meshes, FDM $flightModel")
        jsbsimProfile?.let { profile ->
            append(", JSBSim ${profile.engineMaxPowerHp.clean()} hp PT6A-27 / ${profile.propDiameterIn.clean()} in prop data available")
        }
        append(". $licenseLabel.")
    }
}

internal object OpenSourceSimLibrary {
    private const val JsbsimSystemPropertyPath = "dhc6.jsbsim.zip"
    private const val JsbsimEnvironmentPath = "DHC6_JSBSIM_ZIP"
    private const val FlightGearSystemPropertyPath = "dhc6.flightgear.zip"
    private const val FlightGearEnvironmentPath = "DHC6_FLIGHTGEAR_ZIP"
    private const val FlightGearAircraftSystemPropertyPath = "dhc6.flightgear.aircraft.zip"
    private const val FlightGearAircraftEnvironmentPath = "DHC6_FLIGHTGEAR_AIRCRAFT_ZIP"

    fun loadAuto(): OpenSourceSimSnapshot =
        OpenSourceSimSnapshot(
            jsbsim = loadJsbsim(),
            flightGear = loadFlightGear(),
            flightGearAircraft = loadFlightGearAircraft(),
        )

    private fun loadJsbsim(): JsbsimPackage? {
        val zipPath = explicitPath(JsbsimSystemPropertyPath, JsbsimEnvironmentPath)
            ?.takeIf(Files::isRegularFile)
            ?: candidatePaths("jsbsim-master.zip").firstOrNull(Files::isRegularFile)
            ?: return null
        return runCatching {
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .map { it.name.replace('\\', '/') }
                    .toList()
                val aircraftEntry = entries.firstOrNull { it.endsWith("/aircraft/DHC6/DHC6.xml", ignoreCase = true) }
                    ?: entries.firstOrNull { it.equals("aircraft/DHC6/DHC6.xml", ignoreCase = true) }
                    ?: return null
                val engineEntry = entries.firstOrNull { it.endsWith("/aircraft/DHC6/Engines/PT6A-27.xml", ignoreCase = true) }
                    ?: return null
                val propellerEntry = entries.firstOrNull { it.endsWith("/aircraft/DHC6/Engines/Propeller.xml", ignoreCase = true) }
                    ?: return null
                val systemEntries = entries
                    .filter { it.contains("/aircraft/DHC6/Systems/", ignoreCase = true) && it.endsWith(".xml", ignoreCase = true) }
                    .sorted()
                val aircraftText = zip.readText(aircraftEntry)
                val engineText = zip.readText(engineEntry)
                val propellerText = zip.readText(propellerEntry)
                JsbsimPackage(
                    zipPath = zipPath,
                    aircraftEntry = aircraftEntry,
                    engineEntry = engineEntry,
                    propellerEntry = propellerEntry,
                    systemEntries = systemEntries,
                    dhc6Profile = parseJsbsimDhc6Profile(aircraftText, engineText, propellerText),
                )
            }
        }.getOrNull()
    }

    private fun loadFlightGear(): FlightGearPackage? {
        val zipPath = explicitPath(FlightGearSystemPropertyPath, FlightGearEnvironmentPath)
            ?.takeIf(Files::isRegularFile)
            ?: candidatePaths("flightgear-next.zip").firstOrNull(Files::isRegularFile)
            ?: candidatePaths("flightgear.zip").firstOrNull(Files::isRegularFile)
            ?: return null
        return runCatching {
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .map { it.name.replace('\\', '/') }
                    .toList()
                FlightGearPackage(
                    zipPath = zipPath,
                    sourceEntryCount = entries.size,
                    hasJsbsimBridge = entries.any { it.contains("/src/FDM/JSBSim/", ignoreCase = true) },
                    hasTerraSync = entries.any { it.contains("/scripts/python/TerraSync/", ignoreCase = true) },
                    hasBaseData = entries.any { it.contains("/Aircraft/", ignoreCase = true) || it.contains("/Scenery/", ignoreCase = true) },
                )
            }
        }.getOrNull()
    }

    private fun loadFlightGearAircraft(): FlightGearAircraftPackage? {
        // The corrected/personalized aircraft vendored with this trainer
        // (src/main/resources/flight-sim/dhc6) is the primary source; legacy
        // dhc6-master.zip downloads remain a fallback.
        val correctedDir = vendoredDhc6Dir()?.takeIf { java.io.File(it, "dhc6-set.xml").isFile }
        if (correctedDir != null) {
            parseFlightGearAircraft(correctedDir.toPath(), sourceIsDirectory = true, label = "Personalized DHC-6")
                ?.let { return it }
        }
        val zipPath = explicitPath(FlightGearAircraftSystemPropertyPath, FlightGearAircraftEnvironmentPath)
            ?.takeIf(Files::isRegularFile)
            ?: candidatePaths("dhc6-master.zip").firstOrNull(Files::isRegularFile)
            ?: return null
        return parseFlightGearAircraft(zipPath, sourceIsDirectory = false, label = "FlightGear DHC-6")
    }

    /** Locates the vendored flight-sim/dhc6 aircraft directory when running from source. */
    private fun vendoredDhc6Dir(): java.io.File? {
        var dir: java.io.File? = System.getProperty("user.dir")?.let { java.io.File(it).absoluteFile }
        while (dir != null) {
            val candidate = java.io.File(dir, "src/main/resources/flight-sim/dhc6")
            if (java.io.File(candidate, "dhc6-set.xml").isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }

    private fun parseFlightGearAircraft(
        sourcePath: Path,
        sourceIsDirectory: Boolean,
        label: String,
    ): FlightGearAircraftPackage? =
        runCatching {
            val archive: FgAircraftArchive =
                if (sourceIsDirectory) DirFgAircraftArchive(sourcePath) else ZipFgAircraftArchive(sourcePath)
            archive.use { arc ->
                val entries = arc.entryNames()
                val setEntry = entries.firstOrNull { it.endsWith("/dhc6-set.xml", ignoreCase = true) }
                    ?: return null
                val yasimEntry = entries.firstOrNull { it.endsWith("/dhc6.xml", ignoreCase = true) }
                    ?: return null
                val jsbsimEntry = entries.firstOrNull { it.endsWith("/dhc6jsb.xml", ignoreCase = true) }
                    ?: return null
                val engineEntry = entries.firstOrNull { it.endsWith("/Engines/PT6A-27.xml", ignoreCase = true) }
                    ?: return null
                val propellerEntry = entries.firstOrNull { it.endsWith("/Engines/HARTZELL-T10282H.xml", ignoreCase = true) }
                    ?: entries.firstOrNull { it.endsWith("/Engines/Propeller.xml", ignoreCase = true) }
                    ?: return null
                val modelEntry = entries.firstOrNull { it.endsWith("/Models/DHC6.xml", ignoreCase = true) }
                    ?: return null
                val visualEntries = listOf(
                    "/Models/dhc-6.ac",
                    "/Models/Cabin/cabin.ac",
                    "/Models/Flightdeck/flightdeck.ac",
                    "/Models/wheels.ac",
                    "/Models/floats.ac",
                ).mapNotNull { suffix -> entries.firstOrNull { it.endsWith(suffix, ignoreCase = true) } }
                val aircraftDataEntry = entries.firstOrNull { it.endsWith("/dhc6-aircraft-data.xml", ignoreCase = true) }
                val setText = arc.readText(setEntry) ?: return null
                val yasimText = arc.readText(yasimEntry) ?: return null
                val jsbsimText = arc.readText(jsbsimEntry) ?: return null
                val engineText = arc.readText(engineEntry) ?: return null
                val propellerText = arc.readText(propellerEntry) ?: return null
                val aircraftDataText = aircraftDataEntry?.let(arc::readText)
                val description = parseXml(setText).firstText("description")?.trim()
                    ?: "de Havilland Canada DHC-6-300 Twin Otter"
                FlightGearAircraftPackage(
                    id = "flightgear-dhc6",
                    label = label,
                    zipPath = sourcePath,
                    sourceIsDirectory = sourceIsDirectory,
                    setEntry = setEntry,
                    yasimEntry = yasimEntry,
                    jsbsimEntry = jsbsimEntry,
                    modelEntry = modelEntry,
                    visualEntries = visualEntries,
                    acGeometryCount = entries.count { it.endsWith(".ac", ignoreCase = true) },
                    cockpitAcGeometryCount = entries.count {
                        it.contains("/Models/Flightdeck/", ignoreCase = true) && it.endsWith(".ac", ignoreCase = true)
                    },
                    textureCount = entries.count { it.endsWithImageExtension() },
                    xmlCount = entries.count { it.endsWith(".xml", ignoreCase = true) },
                    nasalScriptCount = entries.count { it.endsWith(".nas", ignoreCase = true) },
                    previewCount = entries.count {
                        it.contains("/Pics/", ignoreCase = true) && it.endsWithImageExtension()
                    },
                    storedPropertyCount = aircraftDataText?.let { parseXml(it).getElementsByTagName("path").length } ?: 0,
                    description = description,
                    flightModel = parseXml(setText).firstText("flight-model")?.trim()
                        ?: parseXml(yasimText).documentElement.tagName,
                    jsbsimProfile = parseJsbsimDhc6Profile(jsbsimText, engineText, propellerText),
                )
            }
        }.getOrNull()

    private fun parseJsbsimDhc6Profile(
        aircraftText: String,
        engineText: String,
        propellerText: String,
    ): JsbsimDhc6Profile {
        val aircraft = parseXml(aircraftText)
        val engine = parseXml(engineText)
        val propeller = parseXml(propellerText)
        val root = aircraft.documentElement
        val engineCount = aircraft.getElementsByTagName("engine").length.coerceAtLeast(2)
        return JsbsimDhc6Profile(
            aircraftName = root.getAttribute("name").ifBlank { "DHC-6" },
            description = aircraft.firstText("description")?.trim().orEmpty().ifBlank { "DeHavilland DHC-6" },
            wingAreaFt2 = aircraft.firstFloat("wingarea") ?: 422.5f,
            wingSpanFt = aircraft.firstFloat("wingspan") ?: 65f,
            chordFt = aircraft.firstFloat("chord") ?: 6.5f,
            emptyWeightLb = aircraft.firstFloat("emptywt") ?: 8100f,
            maxGrossWeightLb = commentFloat(aircraftText, "max weight") ?: 12_500f,
            stallSpeedKts = commentFloat(aircraftText, "stall speed"),
            cl0 = commentFloat(aircraftText, "CL-0"),
            clAlphaPerRad = commentFloat(aircraftText, "CL-alpha"),
            cd0 = commentFloat(aircraftText, "CD-0"),
            inducedDragK = commentFloat(aircraftText, "K"),
            engineCount = engineCount,
            engineMaxPowerHp = engine.firstFloat("maxpower") ?: 680f,
            engineMilThrustLb = engine.firstFloat("milthrust") ?: 1523.2f,
            propDiameterIn = propeller.firstFloat("diameter") ?: 94f,
            propBlades = propeller.firstFloat("numblades")?.toInt()?.coerceAtLeast(1) ?: 4,
            propMinRpm = propeller.firstFloat("minrpm") ?: 1993.57f,
            propMaxRpm = propeller.firstFloat("maxrpm") ?: 2345.38f,
            fuelCapacityLb = aircraft.sumFloats("capacity").takeIf { it > 0f } ?: 2581f,
        )
    }

    private fun explicitPath(systemProperty: String, environmentVariable: String): Path? =
        System.getProperty(systemProperty)
            ?.takeIf { it.isNotBlank() }
            ?.let(Paths::get)
            ?: System.getenv(environmentVariable)
                ?.takeIf { it.isNotBlank() }
                ?.let(Paths::get)

    private fun candidatePaths(fileName: String): List<Path> {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        val userDir = System.getProperty("user.dir")?.takeIf { it.isNotBlank() }
        return buildList {
            add(Paths.get(fileName))
            userDir?.let { add(Paths.get(it, fileName)) }
            userDir?.let { add(Paths.get(it, "desktop-app", "src", "main", "resources", "flight-sim", "open-source", fileName)) }
            home?.let { add(Paths.get(it, "Downloads", fileName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", fileName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", "ZIP Files", "Files", "Desktop", fileName)) }
        }.distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
    }
}

private fun String.endsWithImageExtension(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
        lower.endsWith(".dds") || lower.endsWith(".rgb")
}

private fun ZipFile.readText(entryName: String): String {
    val entry = getEntry(entryName)
        ?: entries().asSequence().first { it.name.equals(entryName, ignoreCase = true) }
    return getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
}

private fun parseXml(text: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
        runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
    }
    return factory.newDocumentBuilder()
        .parse(InputSource(StringReader(text)))
        .apply { documentElement.normalize() }
}

private fun Document.firstText(tagName: String): String? =
    getElementsByTagName(tagName)
        .item(0)
        ?.textContent

private fun Document.firstFloat(tagName: String): Float? =
    firstText(tagName)
        ?.trim()
        ?.toFloatOrNull()

private fun Document.sumFloats(tagName: String): Float =
    getElementsByTagName(tagName)
        .asElements()
        .sumOf { it.textContent.trim().toDoubleOrNull() ?: 0.0 }
        .toFloat()

private fun org.w3c.dom.NodeList.asElements(): Sequence<Element> = sequence {
    for (index in 0 until length) {
        (item(index) as? Element)?.let { yield(it) }
    }
}

private fun commentFloat(text: String, label: String): Float? {
    val pattern = Regex("""(?im)\b${Regex.escape(label)}\s*:\s*([-+]?\d+(?:\.\d+)?)""")
    return pattern.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
}

private fun Float.clean(): String =
    if (this % 1f == 0f) toInt().toString() else String.format(Locale.US, "%.1f", this)
