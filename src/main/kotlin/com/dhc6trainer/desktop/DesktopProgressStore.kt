package com.dhc6trainer.desktop

import java.util.prefs.Preferences

/** Drill activity types recorded to the local logbook. */
internal enum class AttemptType { MCC, DRILL }

/** One recorded training attempt. */
internal data class DrillAttempt(
    val type: AttemptType,
    val title: String,
    val correct: Int,
    val total: Int,
    val elapsedSec: Int,
    val epochMillis: Long,
) {
    val pct: Int get() = if (total > 0) correct * 100 / total else 0
}

/**
 * Local-only progress store for MCC and Drill sessions.
 *
 * Mirrors the persistence approach already used by DesktopLicenseStore
 * (java.util.prefs.Preferences), so there is no new dependency and no files
 * to manage. Keeps lifetime counters (sessions / best %) plus a capped list
 * of recent attempts for the Logbook. All data stays on the local machine.
 */
internal object DesktopProgressStore {
    private const val NodeName = "com.dhc6trainer.desktop.progress"
    private const val KeyAttempts = "attempts"
    private const val KeyMccSessions = "mcc.sessions"
    private const val KeyMccBest = "mcc.best"
    private const val KeyDrillSessions = "drill.sessions"
    private const val KeyDrillBest = "drill.best"

    private const val MaxAttempts = 40       // ~40 short records stays well under the 8 KB pref-value limit
    private const val FS =
        "\u001F"          // field separator (unit separator, never appears in titles)
    private const val RS = "\n"              // record separator

    private val prefs: Preferences = Preferences.userRoot().node(NodeName)

    private fun sanitize(s: String): String =
        s.replace('\u001F', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(80)

    fun record(type: AttemptType, title: String, correct: Int, total: Int, elapsedSec: Int) {
        val cleanTotal = total.coerceAtLeast(0)
        val cleanCorrect = correct.coerceIn(0, cleanTotal)
        val cleanElapsed = elapsedSec.coerceAtLeast(0)
        val cleanTitle = sanitize(title.ifBlank { type.name })
        val epoch = System.currentTimeMillis()
        val line = listOf(
            type.name,
            cleanTitle,
            cleanCorrect.toString(),
            cleanTotal.toString(),
            cleanElapsed.toString(),
            epoch.toString(),
        ).joinToString(FS)

        val updated = (listOf(line) + loadRaw()).take(MaxAttempts)
        prefs.put(KeyAttempts, updated.joinToString(RS))

        val pct = if (cleanTotal > 0) cleanCorrect * 100 / cleanTotal else 0
        when (type) {
            AttemptType.MCC -> {
                prefs.putInt(KeyMccSessions, prefs.getInt(KeyMccSessions, 0) + 1)
                if (pct > prefs.getInt(KeyMccBest, 0)) prefs.putInt(KeyMccBest, pct)
            }
            AttemptType.DRILL -> {
                prefs.putInt(KeyDrillSessions, prefs.getInt(KeyDrillSessions, 0) + 1)
                if (pct > prefs.getInt(KeyDrillBest, 0)) prefs.putInt(KeyDrillBest, pct)
            }
        }
        prefs.safeFlush()
    }

    private fun loadRaw(): List<String> =
        prefs.get(KeyAttempts, "").split(RS).filter { it.isNotBlank() }

    fun recentAttempts(limit: Int = MaxAttempts): List<DrillAttempt> =
        loadRaw().mapNotNull(::parse).take(limit)

    private fun parse(line: String): DrillAttempt? {
        val p = line.split(FS)
        if (p.size < 6) return null
        return runCatching {
            DrillAttempt(
                type = AttemptType.valueOf(p[0]),
                title = p[1],
                correct = p[2].toInt(),
                total = p[3].toInt(),
                elapsedSec = p[4].toInt(),
                epochMillis = p[5].toLong(),
            )
        }.getOrNull()
    }

    fun mccSessions(): Int = prefs.getInt(KeyMccSessions, 0)
    fun mccBest(): Int = prefs.getInt(KeyMccBest, 0)
    fun drillSessions(): Int = prefs.getInt(KeyDrillSessions, 0)
    fun drillBest(): Int = prefs.getInt(KeyDrillBest, 0)
    fun clear() {
        prefs.remove(KeyAttempts)
        prefs.remove(KeyMccSessions)
        prefs.remove(KeyMccBest)
        prefs.remove(KeyDrillSessions)
        prefs.remove(KeyDrillBest)
        prefs.safeFlush()
    }

    private fun Preferences.safeFlush() {
        runCatching { flush() }
    }
}
