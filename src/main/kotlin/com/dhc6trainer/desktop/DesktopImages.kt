package com.dhc6trainer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Loads images bundled on the Java classpath (src/main/resources) into Compose
 * painters, caching the decoded bitmap.
 *
 * This is the non-deprecated replacement for
 * androidx.compose.ui.res.painterResource(String): the Compose deprecation note
 * advises loading classpath resources through the bitmap APIs. It reuses the
 * same getResourceAsStream(...).decodeToImageBitmap() pattern already used by
 * DesktopCockpitSpriteCatalog, so behavior is unchanged - only the warning goes away.
 */
internal object DesktopImages {
    private val cache = HashMap<String, ImageBitmap?>()
    private val externalCache = HashMap<String, ImageBitmap?>()
    private val blank: ImageBitmap by lazy { ImageBitmap(1, 1) }

    fun image(path: String): ImageBitmap? = cache.getOrPut(path) {
        runCatching {
            val loader = Thread.currentThread().contextClassLoader
            val stream = loader?.getResourceAsStream(path)
                ?: DesktopImages::class.java.classLoader.getResourceAsStream(path)
                ?: DesktopImages::class.java.getResourceAsStream("/$path")
            stream?.use { it.readAllBytes().decodeToImageBitmap() }
        }.getOrNull()
    }

    fun imageFromFile(path: Path): ImageBitmap? = externalCache.getOrPut("file:${path.toAbsolutePath()}") {
        runCatching {
            if (!Files.isRegularFile(path)) return@getOrPut null
            Files.newInputStream(path).use { it.readAllBytes().decodeToImageBitmap() }
        }.getOrNull()
    }

    fun imageFromZip(path: Path, entryCandidates: List<String>): Pair<String, ImageBitmap?> {
        val zipPath = path.toAbsolutePath().toString()
        entryCandidates.forEach { entryName ->
            val key = "zip:$zipPath!$entryName"
            if (externalCache.containsKey(key)) {
                return entryName to externalCache[key]
            }
            val image = runCatching {
                if (!Files.isRegularFile(path)) return@runCatching null
                ZipFile(path.toFile()).use { zip ->
                    val entry = zip.getEntry(entryName) ?: return@use null
                    zip.getInputStream(entry).use { it.readAllBytes().decodeToImageBitmap() }
                }
            }.getOrNull()
            externalCache[key] = image
            if (image != null) return entryName to image
        }
        return "" to null
    }

    fun painter(path: String): Painter = BitmapPainter(image(path) ?: blank)
}
