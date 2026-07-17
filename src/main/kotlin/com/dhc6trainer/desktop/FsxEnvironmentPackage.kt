package com.dhc6trainer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

internal data class FsxEnvironmentPackage(
    val id: String,
    val title: String,
    val sourceLabel: String,
    val zipPath: Path?,
    val textureRoot: String,
    val previewEntry: String,
    val previewImage: ImageBitmap?,
    val previewImages: List<ImageBitmap>,
    val textureCount: Int,
    val imageCount: Int,
    val installerCount: Int,
) {
    val isAvailable: Boolean get() = zipPath != null && (previewImage != null || textureCount > 0)
    val hasInstallablePayload: Boolean get() = zipPath != null && installerCount > 0 && textureCount == 0
    val statusBadge: String
        get() = when {
            !isAvailable -> "Default scenery"
            id == "fsx-egkk" -> "EGKK HD env"
            id == "fsx-enag" -> "ENAG HD env"
            else -> "FSX HD env"
        }
    val summary: String
        get() = if (isAvailable) {
            if (hasInstallablePayload) {
                "$title loaded from local FSX package - $imageCount previews, installer payload retained safely"
            } else {
                "$title loaded from local FSX package - $textureCount textures, $imageCount previews"
            }
        } else {
            "Default generated scenery active. Place egkkfrx.zip or fsx_enag.zip in Downloads, or set DHC6_FSX_ENVIRONMENT_ZIP to auto-load an FSX environment."
        }
}

internal object FsxEnvironmentLibrary {
    private const val GenericSystemPropertyPath = "dhc6.fsx.environment.zip"
    private const val GenericEnvironmentPath = "DHC6_FSX_ENVIRONMENT_ZIP"
    private const val LegacyEnagSystemPropertyPath = "dhc6.fsx.enag.zip"
    private const val LegacyEnagEnvironmentPath = "DHC6_FSX_ENAG_ZIP"

    private val knownPackages = listOf(
        KnownEnvironment(
            id = "fsx-egkk",
            title = "FSX Gatwick Free HD Environment",
            fileName = "egkkfrx.zip",
            textureRoot = "",
            previewCandidates = (1..20).map { "Screenshots/screen-$it.jpg" },
        ),
        KnownEnvironment(
            id = "fsx-enag",
            title = "FSX Enhanced Autogen World",
            fileName = "fsx_enag.zip",
            textureRoot = "Install Autogen Textures/Texture",
            previewCandidates = listOf(
                "Images/screen-1.jpeg",
                "Images/screen-3.jpeg",
                "Images/screen-4.jpeg",
                "Images/screen-5.jpeg",
                "Images/screen-6.jpeg",
                "Images/screen-7.jpeg",
                "Images/screen-2.jpeg",
            ),
        ),
    )

    fun loadAuto(): FsxEnvironmentPackage {
        val candidate = candidatePaths().firstOrNull { Files.isRegularFile(it.second) }
        if (candidate == null) {
            return FsxEnvironmentPackage(
                id = "default",
                title = "Generated training scenery",
                sourceLabel = "Procedural fallback",
                zipPath = null,
                textureRoot = "",
                previewEntry = "",
                previewImage = null,
                previewImages = emptyList(),
                textureCount = 0,
                imageCount = 0,
                installerCount = 0,
            )
        }

        val known = candidate.first
        val zipPath = candidate.second
        val discoveredPreviews = discoverImageEntries(zipPath)
        val previewEntries = (known.previewCandidates + discoveredPreviews).distinct()
        val previewPairs = previewEntries.map { entry -> entry to DesktopImages.imageFromZip(zipPath, listOf(entry)).second }
        val previewImages = previewPairs.mapNotNull { it.second }
        val previewEntry = previewPairs.firstOrNull { it.second != null }?.first.orEmpty()
        val previewImage = previewImages.firstOrNull()
        val counts = countPackageAssets(zipPath, known.textureRoot)
        return FsxEnvironmentPackage(
            id = known.id,
            title = known.title,
            sourceLabel = zipPath.fileName.toString(),
            zipPath = zipPath,
            textureRoot = known.textureRoot,
            previewEntry = previewEntry,
            previewImage = previewImage,
            previewImages = previewImages,
            textureCount = counts.textureCount,
            imageCount = counts.imageCount,
            installerCount = counts.installerCount,
        )
    }

    private fun candidatePaths(): List<Pair<KnownEnvironment, Path>> {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        val userDir = System.getProperty("user.dir")?.takeIf { it.isNotBlank() }
        return buildList {
            System.getProperty(GenericSystemPropertyPath)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(knownForPath(Paths.get(it)) to Paths.get(it)) }
            System.getenv(GenericEnvironmentPath)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(knownForPath(Paths.get(it)) to Paths.get(it)) }
            System.getProperty(LegacyEnagSystemPropertyPath)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(knownPackages.first { known -> known.id == "fsx-enag" } to Paths.get(it)) }
            System.getenv(LegacyEnagEnvironmentPath)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(knownPackages.first { known -> known.id == "fsx-enag" } to Paths.get(it)) }
            knownPackages.forEach { known ->
                add(known to Paths.get(known.fileName))
                userDir?.let { add(known to Paths.get(it, known.fileName)) }
                userDir?.let {
                    add(known to Paths.get(it, "desktop-app", "src", "main", "resources", "flight-sim", known.id, known.fileName))
                }
                home?.let { add(known to Paths.get(it, "Downloads", known.fileName)) }
                home?.let { add(known to Paths.get(it, "OneDrive", "Desktop", "My App Data", known.fileName)) }
                home?.let { add(known to Paths.get(it, "OneDrive", "Desktop", "My App Data", "ZIP Files", "Files", "Desktop", known.fileName)) }
            }
        }.distinctBy { it.second.toAbsolutePath().normalize().toString().lowercase() }
    }

    private fun knownForPath(path: Path): KnownEnvironment {
        val fileName = path.fileName?.toString().orEmpty()
        return knownPackages.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
            ?: KnownEnvironment(
                id = "fsx-custom",
                title = "Custom FSX Environment",
                fileName = fileName.ifBlank { "custom.zip" },
                textureRoot = "Install Autogen Textures/Texture",
                previewCandidates = emptyList(),
            )
    }

    private fun discoverImageEntries(zipPath: Path): List<String> = runCatching {
        ZipFile(zipPath.toFile()).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { name ->
                    val lower = name.lowercase()
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")
                }
                .take(16)
                .toList()
        }
    }.getOrDefault(emptyList())

    private fun countPackageAssets(zipPath: Path, textureRoot: String): PackageCounts = runCatching {
        val texturePrefix = textureRoot.trim('/').lowercase().let { if (it.isBlank()) "" else "$it/" }
        ZipFile(zipPath.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            PackageCounts(
                textureCount = entries.count { entry ->
                    val name = entry.name.lowercase()
                    texturePrefix.isNotBlank() && name.startsWith(texturePrefix) &&
                        (name.endsWith(".dds") || name.endsWith(".bmp"))
                },
                imageCount = entries.count { entry ->
                    val name = entry.name.lowercase()
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif")
                },
                installerCount = entries.count { entry -> entry.name.endsWith(".exe", ignoreCase = true) },
            )
        }
    }.getOrDefault(PackageCounts(textureCount = 0, imageCount = 0, installerCount = 0))

    private data class KnownEnvironment(
        val id: String,
        val title: String,
        val fileName: String,
        val textureRoot: String,
        val previewCandidates: List<String>,
    )

    private data class PackageCounts(
        val textureCount: Int,
        val imageCount: Int,
        val installerCount: Int,
    )
}
