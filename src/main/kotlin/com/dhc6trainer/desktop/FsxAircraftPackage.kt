package com.dhc6trainer.desktop

import com.jme3.asset.AssetManager
import com.jme3.asset.plugins.ZipLocator
import com.jme3.scene.Spatial
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

internal data class FsxAircraftPackage(
    val id: String,
    val label: String,
    val zipPath: Path,
    val modelEntry: String,
    val modelEntries: List<String>,
    val textureFolders: List<String>,
    val aircraftRoot: String,
    val aircraftCfgText: String?,
    val textureCount: Int,
    val nativeModelSupported: Boolean,
) {
    val statusBadge: String =
        if (nativeModelSupported) "$label FSX MDL" else "$label legacy MDL"
    val summary: String =
        "$label loaded from ${zipPath.fileName} - ${modelEntries.size} models, $textureCount textures"

    fun loadModel(assetManager: AssetManager): Spatial? =
        runCatching {
            if (!nativeModelSupported) return@runCatching null
            assetManager.registerLocator(zipPath.toAbsolutePath().toString(), ZipLocator::class.java)
            FsxAircraftTextureRegistry.register(this)
            assetManager.loadModel(modelEntry)
        }.getOrNull()

    internal fun readTextureBytes(sourceName: String, textureName: String): ByteArray? {
        val normalizedSource = sourceName.replace('\\', '/').lowercase()
        val folders = textureFoldersFor(normalizedSource)
        ZipFile(zipPath.toFile()).use { zip ->
            val entriesByLowerName = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .associateBy { it.name.replace('\\', '/').lowercase() }
            folders.forEach { folder ->
                val entryName = "$folder/$textureName"
                val entry = zip.getEntry(entryName)
                    ?: entriesByLowerName[entryName.lowercase()]
                    ?: return@forEach
                return zip.getInputStream(entry).use { it.readBytes() }
            }
        }
        return null
    }

    private fun textureFoldersFor(normalizedSource: String): List<String> {
        val preferred = when {
            normalizedSource.contains("model.300/") -> "texture.300"
            normalizedSource.contains("model.100") -> "texture.100"
            else -> null
        }
        return buildList {
            if (preferred != null) {
                textureFolders.firstOrNull { it.substringAfterLast('/').equals(preferred, ignoreCase = true) }
                    ?.let(::add)
            }
            textureFolders.forEach { if (it !in this) add(it) }
        }
    }
}

internal object FsxAircraftPackageLibrary {
    private const val SystemPropertyPath = "dhc6.fsx.aircraft.zip"
    private const val EnvironmentPath = "DHC6_FSX_AIRCRAFT_ZIP"

    private data class KnownFsxAircraft(
        val id: String,
        val label: String,
        val fileName: String,
        val preferredModelNames: List<String>,
        val preferredTextureSuffixes: List<String>,
    )

    private val knownPackages = listOf(
        KnownFsxAircraft(
            id = "dhc6-300-fsx-pad",
            label = "DHC-6-300 FSX",
            fileName = "FSX_De_Havilland_DHC6-300_Twin_Otter_Two_aircraft_package.zip",
            preferredModelNames = listOf("dh6w_X54.mdl"),
            preferredTextureSuffixes = listOf("texture.BVVK", "texture.BIHO"),
        ),
        KnownFsxAircraft(
            id = "dhc6-400-fsx-pad",
            label = "DHC-6-400 FSX",
            fileName = "dhc6_400_x.zip",
            preferredModelNames = listOf("dh6_400_fix2-2.mdl"),
            preferredTextureSuffixes = listOf("texture.CXAnew", "texture.BVVK", "texture.rcmp", "texture.rnp", "texture.zimex"),
        ),
        KnownFsxAircraft(
            id = "air-alpes-fsx",
            label = "DHC-6 Air Alpes",
            fileName = "DHC-6AirAlpes.zip",
            preferredModelNames = listOf("model.300/DHC-6.mdl", "model.100/DHC-6.mdl", "model.100 skis/DHC-6.mdl"),
            preferredTextureSuffixes = listOf("texture.300", "texture.100"),
        ),
    )

    fun loadAuto(): FsxAircraftPackage? {
        val explicitPath = explicitPath()
        if (explicitPath != null && Files.isRegularFile(explicitPath)) {
            loadPackage(
                known = knownPackages.firstOrNull { it.fileName.equals(explicitPath.fileName.toString(), ignoreCase = true) }
                    ?: genericKnown(explicitPath.fileName.toString()),
                zipPath = explicitPath,
            )?.let { return it }
        }

        val packages = knownPackages.mapNotNull { known ->
            candidatePaths(known.fileName)
                .firstOrNull { Files.isRegularFile(it) }
                ?.let { loadPackage(known, it) }
        }
        return packages.firstOrNull { it.nativeModelSupported } ?: packages.firstOrNull()
    }

    private fun explicitPath(): Path? =
        System.getProperty(SystemPropertyPath)
            ?.takeIf { it.isNotBlank() }
            ?.let { Paths.get(it) }
            ?: System.getenv(EnvironmentPath)
                ?.takeIf { it.isNotBlank() }
                ?.let { Paths.get(it) }

    private fun candidatePaths(fileName: String): List<Path> {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        val userDir = System.getProperty("user.dir")?.takeIf { it.isNotBlank() }
        return buildList {
            add(Paths.get(fileName))
            userDir?.let { add(Paths.get(it, fileName)) }
            home?.let { add(Paths.get(it, "Downloads", fileName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", fileName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", "ZIP Files", "Files", "Desktop", fileName)) }
        }.distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
    }

    private fun loadPackage(known: KnownFsxAircraft, zipPath: Path): FsxAircraftPackage? =
        runCatching {
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .map { it.name.replace('\\', '/') }
                    .toList()
                val aircraftCfgEntry = entries.firstOrNull { it.endsWith("aircraft.cfg", ignoreCase = true) }
                val aircraftRoot = aircraftCfgEntry?.substringBeforeLast('/', "") ?: ""
                val modelEntries = entries
                    .filter { it.endsWith(".mdl", ignoreCase = true) }
                    .filter { aircraftRoot.isEmpty() || it.startsWith("$aircraftRoot/", ignoreCase = true) }
                    .sortedWith(compareBy<String> { modelRank(known, it) }.thenBy { it.lowercase() })
                val textureFolders = entries
                    .filter { it.endsWith(".bmp", ignoreCase = true) }
                    .map { it.substringBeforeLast('/') }
                    .filter { it.substringAfterLast('/').startsWith("texture", ignoreCase = true) }
                    .distinct()
                    .sortedWith(compareBy<String> { textureRank(known, it) }.thenBy { it.lowercase() })
                val preferredModel = modelEntries.firstOrNull() ?: return null
                val aircraftCfg = aircraftCfgEntry?.let(zip::getEntry)
                    ?.let { entry -> zip.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) } }
                FsxAircraftPackage(
                    id = known.id,
                    label = known.label,
                    zipPath = zipPath,
                    modelEntry = preferredModel,
                    modelEntries = modelEntries,
                    textureFolders = textureFolders,
                    aircraftRoot = aircraftRoot,
                    aircraftCfgText = aircraftCfg,
                    textureCount = entries.count { it.endsWith(".bmp", ignoreCase = true) },
                    nativeModelSupported = isNativeMdl8(zip, preferredModel),
                )
            }
        }.getOrNull()

    private fun genericKnown(fileName: String): KnownFsxAircraft =
        KnownFsxAircraft(
            id = fileName.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
            label = fileName.substringBeforeLast('.'),
            fileName = fileName,
            preferredModelNames = emptyList(),
            preferredTextureSuffixes = emptyList(),
        )

    private fun isNativeMdl8(zip: ZipFile, modelEntry: String): Boolean {
        val entry = zip.getEntry(modelEntry) ?: return false
        val header = ByteArray(12)
        zip.getInputStream(entry).use { input ->
            if (input.read(header) < header.size) return false
        }
        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val type = String(header, 8, 4, Charsets.US_ASCII)
        return riff == "RIFF" && type == "MDL8"
    }

    private fun modelRank(known: KnownFsxAircraft, name: String): Int {
        val normalized = name.lowercase()
        val preferred = known.preferredModelNames.indexOfFirst { preferred ->
            normalized.endsWith(preferred.lowercase())
        }
        return if (preferred >= 0) preferred else 100
    }

    private fun textureRank(known: KnownFsxAircraft, folder: String): Int {
        val suffix = folder.substringAfterLast('/').lowercase()
        val preferred = known.preferredTextureSuffixes.indexOfFirst { suffix == it.lowercase() }
        return if (preferred >= 0) preferred else 100
    }
}

internal object FsxAircraftTextureRegistry {
    private val packagesByModelEntry = ConcurrentHashMap<String, FsxAircraftPackage>()

    fun register(aircraftPackage: FsxAircraftPackage) {
        aircraftPackage.modelEntries.forEach { modelEntry ->
            packagesByModelEntry[modelEntry.lowercase()] = aircraftPackage
        }
    }

    fun readTextureBytes(sourceName: String, textureName: String): ByteArray? {
        val normalized = sourceName.replace('\\', '/').lowercase()
        val aircraftPackage = packagesByModelEntry[normalized]
            ?: packagesByModelEntry.entries.firstOrNull { normalized.endsWith(it.key) }?.value
            ?: return null
        return aircraftPackage.readTextureBytes(normalized, textureName)
    }
}
