package com.dhc6trainer.desktop

import com.jme3.asset.AssetManager
import com.jme3.asset.plugins.FileLocator
import com.jme3.scene.Spatial
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal data class LocalAircraftModelReplacement(
    val aircraftId: String,
    val label: String,
    val fileName: String,
    val path: Path?,
) {
    val isCustom: Boolean get() = path != null
    val sourceLabel: String get() = if (isCustom) "custom GLB" else "trainer 3D model"

    fun load(assetManager: AssetManager): Spatial? {
        val modelPath = path ?: return null
        return runCatching {
            assetManager.registerLocator(
                modelPath.parent.toAbsolutePath().normalize().toString(),
                FileLocator::class.java,
            )
            assetManager.loadModel(modelPath.fileName.toString())
        }.getOrNull()
    }
}

internal object LocalAircraftModelLibrary {
    private const val SystemPropertyDirectory = "dhc6.aircraft.models.dir"
    private const val EnvironmentDirectory = "DHC6_AIRCRAFT_MODELS_DIR"

    private data class KnownReplacement(
        val aircraftId: String,
        val label: String,
        val fileName: String,
    )

    private val knownReplacements = listOf(
        KnownReplacement("dhc6-300-fsx-pad", "DHC-6-300", "dhc6-300.glb"),
        KnownReplacement("dhc6-400-fsx-pad", "DHC-6-400", "dhc6-400.glb"),
    )

    val preferredDirectory: Path
        get() {
            explicitDirectory()?.let { return it }
            val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
            return if (home != null) {
                Paths.get(home, "DHC-6 Trainer Desktop", "aircraft-models")
            } else {
                Paths.get("app-data", "aircraft-models").toAbsolutePath().normalize()
            }
        }

    fun replacementFor(aircraftId: String): LocalAircraftModelReplacement? {
        val known = knownReplacements.firstOrNull { it.aircraftId == aircraftId } ?: return null
        val path = candidateDirectories()
            .asSequence()
            .map { it.resolve(known.fileName) }
            .firstOrNull(Files::isRegularFile)
        return LocalAircraftModelReplacement(
            aircraftId = known.aircraftId,
            label = known.label,
            fileName = known.fileName,
            path = path,
        )
    }

    fun sourceLabel(aircraftId: String): String =
        replacementFor(aircraftId)?.sourceLabel ?: "trainer 3D model"

    fun settingsSummary(): String = buildString {
        append("Optional replacement folder: ")
        append(preferredDirectory.toAbsolutePath().normalize())
        append(". ")
        append(
            knownReplacements.joinToString(" | ") { known ->
                val replacement = replacementFor(known.aircraftId)
                if (replacement?.path != null) {
                    "${known.label}: custom GLB loaded from ${replacement.path}"
                } else {
                    "${known.label}: using the clean trainer 3D model; add ${known.fileName} to replace it"
                }
            },
        )
    }

    private fun explicitDirectory(): Path? =
        System.getProperty(SystemPropertyDirectory)
            ?.takeIf { it.isNotBlank() }
            ?.let(Paths::get)
            ?: System.getenv(EnvironmentDirectory)
                ?.takeIf { it.isNotBlank() }
                ?.let(Paths::get)

    private fun candidateDirectories(): List<Path> {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        val userDir = System.getProperty("user.dir")?.takeIf { it.isNotBlank() }
        return buildList {
            explicitDirectory()?.let(::add)
            userDir?.let { add(Paths.get(it, "app-data", "aircraft-models")) }
            userDir?.let { add(Paths.get(it, "aircraft-models")) }
            home?.let { add(Paths.get(it, "DHC-6 Trainer Desktop", "aircraft-models")) }
        }.distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
    }
}
