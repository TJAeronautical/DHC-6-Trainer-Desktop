package com.dhc6trainer.desktop

import com.jme3.asset.AssetManager
import com.jme3.asset.TextureKey
import com.jme3.asset.plugins.ZipLocator
import com.jme3.math.Vector3f
import com.jme3.texture.Texture
import com.jme3.texture.Texture2D
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.sqrt

internal data class XPlaneGeoPoint(val lat: Double, val lon: Double)

internal data class XPlaneRunway(
    val id1: String,
    val id2: String,
    val widthMeters: Float,
    val end1: XPlaneGeoPoint,
    val end2: XPlaneGeoPoint,
) {
    val center: XPlaneGeoPoint =
        XPlaneGeoPoint((end1.lat + end2.lat) * 0.5, (end1.lon + end2.lon) * 0.5)
}

internal data class XPlanePavementPolygon(
    val name: String,
    val points: List<XPlaneGeoPoint>,
)

internal data class XPlaneOrthoTile(
    val terrainEntry: String,
    val textureEntry: String,
    val center: XPlaneGeoPoint,
    val radiusMeters: Float,
)

internal data class XPlaneSceneryPackage(
    val id: String,
    val title: String,
    val sourceZipPath: Path,
    val airportZipPath: Path,
    val orthoZipPath: Path,
    val rootEntry: String,
    val runway: XPlaneRunway,
    val pavements: List<XPlanePavementPolygon>,
    val orthoTiles: List<XPlaneOrthoTile>,
) {
    private val metersPerLat = 111_320.0
    private val metersPerLon = 111_320.0 * cos(Math.toRadians(runway.center.lat))

    val statusBadge: String =
        "VRMM X-Plane scenery - ${orthoTiles.size} ortho tiles, ${pavements.size} pavements"

    fun localPoint(point: XPlaneGeoPoint): Vector3f {
        val eastMeters = (point.lon - runway.center.lon) * metersPerLon
        val northMeters = (point.lat - runway.center.lat) * metersPerLat
        return Vector3f(eastMeters.toFloat(), 0f, (-northMeters).toFloat())
    }

    fun distanceFromRunwayCenter(tile: XPlaneOrthoTile): Float {
        val p = localPoint(tile.center)
        return sqrt(p.x * p.x + p.z * p.z)
    }

    fun nearestOrthoTiles(maxTiles: Int = 24): List<XPlaneOrthoTile> =
        orthoTiles
            .distinctBy { it.textureEntry.lowercase() }
            .sortedBy(::distanceFromRunwayCenter)
            .take(maxTiles)

    fun registerOrthoLocator(assetManager: AssetManager): Boolean =
        runCatching {
            assetManager.registerLocator(orthoZipPath.toAbsolutePath().toString(), ZipLocator::class.java)
            true
        }.getOrDefault(false)

    fun loadOrthoTexture(assetManager: AssetManager, tile: XPlaneOrthoTile): Texture2D? =
        runCatching {
            val key = TextureKey(tile.textureEntry, false)
            key.isGenerateMips = true
            (assetManager.loadTexture(key) as? Texture2D)?.apply {
                setWrap(Texture.WrapMode.EdgeClamp)
                minFilter = Texture.MinFilter.Trilinear
                magFilter = Texture.MagFilter.Bilinear
                anisotropicFilter = 8
            }
        }.getOrNull()
}

internal object XPlaneSceneryLibrary {
    private const val SystemPropertyPath = "dhc6.vrmm.scenery.zip"
    private const val EnvironmentPath = "DHC6_VRMM_SCENERY_ZIP"
    private const val VrmmZipName = "Male_Maldives-VRMM_airport-City-Ortho_2_6_3_6qbPz.zip"
    private const val AirportZipName = "Male_Maldives-VRMM_airport-City_2_6_3.zip"
    private const val OrthoZipName = "zOrtho_Male_Maldives.zip"

    fun loadAuto(): XPlaneSceneryPackage? {
        val sourceZip = candidatePaths().firstOrNull { Files.isRegularFile(it) } ?: return null
        return runCatching {
            val cacheDir = sceneryCacheDir()
            Files.createDirectories(cacheDir)
            val airportZip = extractNestedZip(sourceZip, AirportZipName, cacheDir.resolve(AirportZipName))
            val orthoZip = extractNestedZip(sourceZip, OrthoZipName, cacheDir.resolve(OrthoZipName))
            val airportData = parseAirportData(airportZip) ?: return null
            val orthoTiles = parseOrthoTiles(orthoZip)
            XPlaneSceneryPackage(
                id = "vrmm-male",
                title = "Velana Intl / Male Maldives",
                sourceZipPath = sourceZip,
                airportZipPath = airportZip,
                orthoZipPath = orthoZip,
                rootEntry = "Male_Maldives-VRMM_airport-City-Ortho_2_6_3",
                runway = airportData.runway,
                pavements = airportData.pavements,
                orthoTiles = orthoTiles,
            )
        }.getOrNull()
    }

    private fun candidatePaths(): List<Path> {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        val userDir = System.getProperty("user.dir")?.takeIf { it.isNotBlank() }
        return buildList {
            System.getProperty(SystemPropertyPath)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(Paths.get(it)) }
            System.getenv(EnvironmentPath)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(Paths.get(it)) }
            add(Paths.get(VrmmZipName))
            userDir?.let { add(Paths.get(it, VrmmZipName)) }
            home?.let { add(Paths.get(it, "Downloads", VrmmZipName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", VrmmZipName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", "ZIP Files", "Files", "Desktop", VrmmZipName)) }
        }.distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
    }

    private fun sceneryCacheDir(): Path {
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        val base = localAppData?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("java.io.tmpdir"), "DHC-6 Trainer")
        return base.resolve("DHC-6 Trainer").resolve("scenery-cache").resolve("vrmm")
    }

    private fun extractNestedZip(sourceZip: Path, nestedFileName: String, target: Path): Path {
        ZipFile(sourceZip.toFile()).use { outer ->
            val entry = outer.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith("/$nestedFileName") }
                ?: error("Missing $nestedFileName in ${sourceZip.fileName}")
            if (Files.isRegularFile(target) && Files.size(target) == entry.size) {
                return target
            }
            Files.createDirectories(target.parent)
            outer.getInputStream(entry).use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        }
    }

    private fun parseAirportData(airportZip: Path): AirportData? =
        ZipFile(airportZip.toFile()).use { zip ->
            val aptEntry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith("Earth nav data/apt.dat") }
                ?: return null
            val text = zip.getInputStream(aptEntry).bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
            parseAptDat(text)
        }

    private fun parseAptDat(text: String): AirportData? {
        var runway: XPlaneRunway? = null
        val pavements = ArrayList<XPlanePavementPolygon>()
        var activePolygon: MutableList<XPlaneGeoPoint>? = null
        var polygonIndex = 0

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(Regex("\\s+"))
            when (parts.firstOrNull()) {
                "100" -> {
                    val width = parts.getOrNull(1)?.toFloatOrNull() ?: return@forEach
                    val id1 = parts.getOrNull(8) ?: "18"
                    val end1Lat = parts.getOrNull(9)?.toDoubleOrNull() ?: return@forEach
                    val end1Lon = parts.getOrNull(10)?.toDoubleOrNull() ?: return@forEach
                    val id2 = parts.getOrNull(17) ?: "36"
                    val end2Lat = parts.getOrNull(18)?.toDoubleOrNull() ?: return@forEach
                    val end2Lon = parts.getOrNull(19)?.toDoubleOrNull() ?: return@forEach
                    runway = XPlaneRunway(
                        id1 = id1,
                        id2 = id2,
                        widthMeters = width,
                        end1 = XPlaneGeoPoint(end1Lat, end1Lon),
                        end2 = XPlaneGeoPoint(end2Lat, end2Lon),
                    )
                }
                "110" -> activePolygon = ArrayList()
                "111", "112" -> {
                    val lat = parts.getOrNull(1)?.toDoubleOrNull()
                    val lon = parts.getOrNull(2)?.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        activePolygon?.add(XPlaneGeoPoint(lat, lon))
                    }
                }
                "113" -> {
                    val lat = parts.getOrNull(1)?.toDoubleOrNull()
                    val lon = parts.getOrNull(2)?.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        activePolygon?.add(XPlaneGeoPoint(lat, lon))
                    }
                    val polygon = activePolygon
                    if (polygon != null && polygon.size >= 3) {
                        pavements += XPlanePavementPolygon("VRMM pavement ${++polygonIndex}", polygon.toList())
                    }
                    activePolygon = null
                }
            }
        }

        val parsedRunway = runway ?: return null
        return AirportData(parsedRunway, pavements)
    }

    private fun parseOrthoTiles(orthoZip: Path): List<XPlaneOrthoTile> =
        ZipFile(orthoZip.toFile()).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".ter", ignoreCase = true) }
                .filterNot { it.name.contains("_sea_overlay", ignoreCase = true) }
                .mapNotNull { entry ->
                    val text = zip.getInputStream(entry).bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
                    parseTerrain(entry.name, text)
                }
                .toList()
        }

    private fun parseTerrain(entryName: String, text: String): XPlaneOrthoTile? {
        var center: XPlaneGeoPoint? = null
        var radiusMeters = 2500f
        var textureEntry: String? = null

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val parts = line.split(Regex("\\s+"))
            when (parts.firstOrNull()) {
                "LOAD_CENTER" -> {
                    val lat = parts.getOrNull(1)?.toDoubleOrNull()
                    val lon = parts.getOrNull(2)?.toDoubleOrNull()
                    val radius = parts.getOrNull(3)?.toFloatOrNull()
                    if (lat != null && lon != null) {
                        center = XPlaneGeoPoint(lat, lon)
                    }
                    if (radius != null) {
                        radiusMeters = radius
                    }
                }
                "BASE_TEX_NOWRAP", "BASE_TEX" -> {
                    textureEntry = parts.getOrNull(1)?.let { textureRef ->
                        val root = entryName.substringBefore('/')
                        val fileName = textureRef.substringAfterLast('/')
                        "$root/textures/$fileName"
                    }
                }
            }
        }

        val parsedCenter = center ?: return null
        val parsedTexture = textureEntry ?: return null
        return XPlaneOrthoTile(entryName, parsedTexture, parsedCenter, radiusMeters)
    }

    private data class AirportData(
        val runway: XPlaneRunway,
        val pavements: List<XPlanePavementPolygon>,
    )
}
