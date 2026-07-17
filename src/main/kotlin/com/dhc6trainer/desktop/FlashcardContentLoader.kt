package com.dhc6trainer.desktop

import java.io.File
import java.nio.charset.StandardCharsets

internal data class FlashcardReference(
    val source: String,
    val locator: String,
)

internal data class FlashcardItem(
    val id: String,
    val front: String,
    val back: String,
    val tags: List<String>,
    val appliesToVariant: String?,
    val references: List<FlashcardReference>,
)

internal data class FlashcardDeckSummary(
    val deckId: String,
    val deckName: String,
    val description: String,
    val systemId: String,
    val variant: String,
    val difficulty: String,
    val sourcePath: String,
    val cards: List<FlashcardItem>,
)

internal data class FlashcardLibrarySnapshot(
    val decks: List<FlashcardDeckSummary>,
    val loadNotes: List<String>,
) {
    val totalCards: Int get() = decks.sumOf { it.cards.size }
}

internal object DesktopFlashcardContentLoader {
    private const val resourceIndexPath = "desktop-flashcard-index.txt"

    fun load(): FlashcardLibrarySnapshot {
        val notes = mutableListOf<String>()
        val resourcePaths = loadResourceIndex()

        val loaded = when {
            resourcePaths.isNotEmpty() -> {
                notes += "Loaded flashcard index from packaged desktop resources."
                resourcePaths.mapNotNull { path ->
                    readResourceText(path)?.let { parseDeck(path, it) }
                }
            }
            else -> {
                notes += "Packaged flashcard index not found. Falling back to project file-system scan."
                loadFromFileSystem(notes)
            }
        }.sortedWith(compareBy<FlashcardDeckSummary> { it.systemId.lowercase() }.thenBy { it.deckName.lowercase() })

        if (loaded.isEmpty()) {
            notes += "No flashcards loaded. Confirm core-res/src/main/assets/flashcards exists before running processResources."
        }

        return FlashcardLibrarySnapshot(loaded, notes)
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
        val loader = Thread.currentThread().contextClassLoader ?: DesktopFlashcardContentLoader::class.java.classLoader
        return loader.getResourceAsStream(path)?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    private fun loadFromFileSystem(notes: MutableList<String>): List<FlashcardDeckSummary> {
        val userDir = File(System.getProperty("user.dir")).canonicalFile
        val candidates = listOf(
            File(userDir, "core-res/src/main/assets/flashcards"),
            File(userDir.parentFile ?: userDir, "core-res/src/main/assets/flashcards"),
            File(userDir, "../core-res/src/main/assets/flashcards"),
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
                parseDeck("assets/flashcards/$relative", file.readText())
            }
            .toList()
    }

    private fun parseDeck(path: String, json: String): FlashcardDeckSummary {
        return FlashcardDeckSummary(
            deckId = json.stringValue("deckId") ?: path.substringAfterLast('/').removeSuffix(".json"),
            deckName = json.stringValue("deckName") ?: path.substringAfterLast('/').removeSuffix(".json").replace('_', ' ').titleCase(),
            description = json.stringValue("description") ?: "DHC-6 study deck",
            systemId = json.stringValue("systemId") ?: "general",
            variant = json.stringValue("variant") ?: "BOTH",
            difficulty = json.stringValue("difficulty") ?: "STUDY",
            sourcePath = path,
            cards = parseCards(json),
        )
    }

    private fun parseCards(json: String): List<FlashcardItem> {
        val cardsArray = json.substringAfter("\"cards\"", "")
        if (cardsArray.isBlank()) return emptyList()

        return topLevelObjects(cardsArray).mapNotNull { block ->
            val id = block.stringValue("id") ?: return@mapNotNull null
            val front = block.stringValue("front") ?: return@mapNotNull null
            val back = block.stringValue("back") ?: return@mapNotNull null
            FlashcardItem(
                id = id,
                front = front,
                back = back,
                tags = block.stringArray("tags"),
                appliesToVariant = block.stringValue("appliesToVariant"),
                references = parseReferences(block),
            )
        }
    }

    private fun parseReferences(block: String): List<FlashcardReference> {
        val referencesSection = block.substringAfter("\"references\"", "")
        if (referencesSection.isBlank()) return emptyList()
        return topLevelObjects(referencesSection).mapNotNull { ref ->
            val source = ref.stringValue("source") ?: return@mapNotNull null
            val locator = ref.stringValue("locator") ?: ""
            FlashcardReference(source, locator)
        }
    }

    private fun topLevelObjects(text: String): List<String> {
        val startArray = text.indexOf('[')
        if (startArray < 0) return emptyList()
        val result = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escaped = false
        var objectStart = -1

        for (i in startArray until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            when (c) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        result += text.substring(objectStart, i + 1)
                        objectStart = -1
                    }
                }
                ']' -> if (depth == 0) return result
            }
        }
        return result
    }

    private fun String.stringValue(key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(this)?.groupValues?.getOrNull(1)?.unescapeJson()
    }

    private fun String.stringArray(key: String): List<String> {
        val pattern = Regex("\"$key\"\\s*:\\s*\\[(.*?)\\]", setOf(RegexOption.DOT_MATCHES_ALL))
        val raw = pattern.find(this)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
            .findAll(raw)
            .map { it.groupValues[1].unescapeJson() }
            .toList()
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
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.titlecase() } }
    }
}
