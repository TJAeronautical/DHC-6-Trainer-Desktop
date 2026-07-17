package com.dhc6trainer.desktop

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

internal data class DesktopAssetActionResult(
    val success: Boolean,
    val message: String,
)

internal fun isDesktopOpenableAssetPath(path: String): Boolean {
    val clean = path.cleanDesktopAssetPath()
    if (!clean.startsWith("assets/")) return false

    val lower = clean.lowercase()
    return lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".svg") ||
        lower.endsWith(".pdf") ||
        lower.endsWith(".json") ||
        lower.endsWith(".txt") ||
        lower.endsWith(".glb") ||
        lower.endsWith(".gltf")
}

internal fun openPackagedDesktopAsset(path: String): DesktopAssetActionResult {
    val clean = path.cleanDesktopAssetPath()
    if (!isDesktopOpenableAssetPath(clean)) {
        return DesktopAssetActionResult(false, "No openable packaged asset path selected.")
    }

    if (!Desktop.isDesktopSupported()) {
        return DesktopAssetActionResult(false, "Desktop open action is not supported on this device.")
    }

    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.OPEN)) {
        return DesktopAssetActionResult(false, "Desktop open action is not available on this device.")
    }

    val loader = Thread.currentThread().contextClassLoader ?: DesktopAssetActionResult::class.java.classLoader
    val stream = loader.getResourceAsStream(clean)
        ?: DesktopAssetActionResult::class.java.classLoader.getResourceAsStream(clean)
        ?: return DesktopAssetActionResult(false, "Packaged asset not found in desktop resources: $clean")

    return runCatching {
        stream.use { input ->
            val cacheDir = File(System.getProperty("java.io.tmpdir"), "dhc6-trainer-desktop-assets").toPath()
            if (!cacheDir.exists()) cacheDir.createDirectories()

            val fileName = clean.substringAfterLast('/').ifBlank { "asset.bin" }.safeDesktopFileName()
            val target = cacheDir.resolve(fileName)
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            desktop.open(target.toFile())
            DesktopAssetActionResult(true, "Opened ${target.fileName} from packaged resources.")
        }
    }.getOrElse { error ->
        DesktopAssetActionResult(false, "Could not open $clean: ${error.message ?: error.javaClass.simpleName}")
    }
}

internal fun copyDesktopAssetPath(path: String): DesktopAssetActionResult {
    val clean = path.cleanDesktopAssetPath()
    return runCatching {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(clean), null)
        DesktopAssetActionResult(true, "Copied asset path.")
    }.getOrElse { error ->
        DesktopAssetActionResult(false, "Could not copy path: ${error.message ?: error.javaClass.simpleName}")
    }
}

private fun String.cleanDesktopAssetPath(): String =
    trim()
        .removePrefix("/")
        .replace('\\', '/')

private fun String.safeDesktopFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "asset.bin" }
