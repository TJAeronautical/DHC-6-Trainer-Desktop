package com.dhc6trainer.desktop

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal enum class ProcedureCategory(val displayName: String) {
    NORMAL("Normal"),
    ABNORMAL("Abnormal"),
    EMERGENCY("Emergency");

    companion object {
        fun fromPathOrValue(path: String, value: String?): ProcedureCategory {
            val normalized = "${path.lowercase()} ${value.orEmpty().lowercase()}"
            return when {
                "emergency" in normalized -> EMERGENCY
                "abnormal" in normalized -> ABNORMAL
                else -> NORMAL
            }
        }
    }
}

internal data class ProcedureStep(
    val number: Int?,
    val action: String,
    val crewRole: String?,
    val intent: String?,
    val requiresConfirmation: Boolean?,
    val reference: String?,
)

internal data class ProcedureSummary(
    val id: String,
    val rawName: String,
    val drillName: String,
    val category: ProcedureCategory,
    val context: String,
    val variants: Set<String>,
    val memoryCount: Int,
    val flowCount: Int,
    val stepCount: Int,
    val sourcePath: String,
    val steps: List<ProcedureStep>,
    /**
     * The `sourceNote` field carried through from the procedure JSON. Empty
     * for procedures that don't have one (rare — usually because the file
     * pre-dates the metadata convention). Preserved verbatim so the UI can
     * inspect it (e.g. for the placeholder-content warning banner).
     */
    val sourceNote: String,
) {
    /**
     * True when this procedure's `sourceNote` explicitly marks it as a
     * generic MCC callout shell — i.e. not real DHC-6 content. Surfaced in
     * the QRH detail view so users are not misled into treating placeholder
     * scaffolding as an approved procedure. See docs/CONTENT-AUDIT.md.
     */
    val isPlaceholder: Boolean
        get() = sourceNote.contains(
            "Generic MCC callout shell generated from procedure titles only",
            ignoreCase = false,
        )
}

internal data class ProcedureLibrarySnapshot(
    val procedures: List<ProcedureSummary>,
    val loadNotes: List<String>,
) {
    val normalCount: Int get() = procedures.count { it.category == ProcedureCategory.NORMAL }
    val abnormalCount: Int get() = procedures.count { it.category == ProcedureCategory.ABNORMAL }
    val emergencyCount: Int get() = procedures.count { it.category == ProcedureCategory.EMERGENCY }
}

internal object DesktopProcedureContentLoader {
    private const val resourceIndexPath = "desktop-procedure-index.txt"

    fun load(): ProcedureLibrarySnapshot {
        val notes = mutableListOf<String>()
        val resourcePaths = loadResourceIndex()

        val loaded = when {
            resourcePaths.isNotEmpty() -> {
                notes += "Loaded procedure index from packaged desktop resources."
                resourcePaths.mapNotNull { path ->
                    readResourceText(path)?.let { parseProcedure(path, it) }
                }
            }
            else -> {
                notes += "Packaged procedure index not found. Falling back to project file-system scan."
                loadFromFileSystem(notes)
            }
        }.sortedWith(
            compareBy<ProcedureSummary> { it.category.ordinal }
                .thenBy { it.rawName.lowercase() }
        )

        if (loaded.isEmpty()) {
            notes += "No procedures loaded. Confirm src/main/resources/assets/procedures exists before running processResources."
        }

        return ProcedureLibrarySnapshot(
            procedures = loaded,
            loadNotes = notes,
        )
    }

    private fun loadResourceIndex(): List<String> {
        return readResourceText(resourceIndexPath)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && it.endsWith(".json") }
            ?.toList()
            .orEmpty()
    }

    private fun readResourceText(path: String): String? {
        val loader = Thread.currentThread().contextClassLoader ?: DesktopProcedureContentLoader::class.java.classLoader
        return loader.getResourceAsStream(path)?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    private fun loadFromFileSystem(notes: MutableList<String>): List<ProcedureSummary> {
        val userDir = File(System.getProperty("user.dir")).canonicalFile
        val candidates = listOf(
            File(userDir, "src/main/resources/assets/procedures"),
            File(userDir, "core-res/src/main/assets/procedures"),
            File(userDir.parentFile ?: userDir, "core-res/src/main/assets/procedures"),
            File(userDir, "../core-res/src/main/assets/procedures"),
        ).map { it.canonicalFile }.distinct()

        val root = candidates.firstOrNull { it.exists() && it.isDirectory }
        if (root == null) {
            notes += "File-system fallback failed. Checked: ${candidates.joinToString { it.path }}"
            return emptyList()
        }

        notes += "File-system fallback root: ${root.path}"

        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .mapNotNull { file ->
                val relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                parseProcedure("assets/procedures/$relative", file.readText())
            }
            .toList()
    }

    private fun parseProcedure(path: String, json: String): ProcedureSummary {
        val rawName = json.stringValue("rawName")
            ?: json.stringValue("name")
            ?: path.substringAfterLast('/').removeSuffix(".json").replace('_', ' ').titleCase()
        val drillName = json.stringValue("drillName") ?: rawName
        val categoryValue = json.stringValue("category")
        val category = ProcedureCategory.fromPathOrValue(path, categoryValue)
        val context = json.stringValue("context") ?: "General"
        val variants = Regex("\"([A-Z0-9_ -]+)\"\\s*:\\s*\\{")
            .findAll(json.substringAfter("\"variants\"", json))
            .map { it.groupValues[1].trim() }
            .filter { it in setOf("LEGACY", "G950", "BOTH", "OM-A", "OM-B", "OM-C") }
            .toSet()
            .ifEmpty { setOf("LEGACY") }

        val memoryCount = sectionActionCount(json, "memory")
        val flowCount = sectionActionCount(json, "flow")
        val steps = parseSteps(json)
        val sourceNote = json.stringValue("sourceNote").orEmpty()

        return ProcedureSummary(
            id = path.substringAfter("assets/procedures/").removeSuffix(".json"),
            rawName = rawName,
            drillName = drillName,
            category = category,
            context = context,
            variants = variants,
            memoryCount = memoryCount,
            flowCount = flowCount,
            stepCount = steps.size,
            sourcePath = path,
            steps = steps,
            sourceNote = sourceNote,
        )
    }

    private fun parseSteps(json: String): List<ProcedureStep> {
        val objectRegex = Regex("\\{\\s*\"stepNumber\"\\s*:\\s*(\\d+).*?\\}", RegexOption.DOT_MATCHES_ALL)
        return objectRegex.findAll(json)
            .mapNotNull { match ->
                val block = match.value
                val action = block.stringValue("action") ?: return@mapNotNull null
                ProcedureStep(
                    number = match.groupValues.getOrNull(1)?.toIntOrNull(),
                    action = action,
                    crewRole = block.stringValue("crewRole"),
                    intent = block.stringValue("intent"),
                    requiresConfirmation = block.booleanValue("requiresConfirmation"),
                    reference = block.stringValue("reference"),
                )
            }
            .toList()
    }

    private fun sectionActionCount(json: String, sectionName: String): Int {
        val startToken = "\"$sectionName\""
        val start = json.indexOf(startToken)
        if (start < 0) return 0

        val nextSectionCandidates = listOf("\"memory\"", "\"flow\"")
            .filterNot { it == startToken }
            .map { json.indexOf(it, start + startToken.length) }
            .filter { it > start }

        val end = nextSectionCandidates.minOrNull() ?: json.length
        val section = json.substring(start, end)
        return Regex("\"action\"\\s*:").findAll(section).count()
    }

    private fun String.stringValue(key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(this)?.groupValues?.getOrNull(1)?.unescapeJson()
    }

    private fun String.booleanValue(key: String): Boolean? {
        val pattern = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return pattern.find(this)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true)
    }

    private fun String.unescapeJson(): String {
        return this
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }

    private fun String.titleCase(): String {
        return split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}
