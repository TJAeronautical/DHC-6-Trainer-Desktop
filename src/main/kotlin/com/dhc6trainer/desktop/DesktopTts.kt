package com.dhc6trainer.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// DesktopTts — offline text-to-speech for the MCC Callout trainer
//
// Uses Windows built-in System.Speech via a non-blocking PowerShell subprocess.
// No external dependencies. No javax.speech. Falls back silently on macOS/Linux.
//
// Usage:
//   LaunchedEffect(step) { DesktopTts.speak(step.action) }
//   DesktopTts.stop()   // call on phase change / session end
// ─────────────────────────────────────────────────────────────────────────────

internal object DesktopTts {

    // Volume 0-100, rate -10..10 (0 = normal).  Caller can adjust via Settings later.
    var volume: Int = 80
    var rate:   Int = 0
    var enabled: Boolean = true

    private val isWindows: Boolean
        get() = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    // Actively running speech process — killed on stop() or next speak()
    @Volatile private var currentProcess: Process? = null

    /**
     * Speak [text] asynchronously. Must be called from a coroutine (e.g. LaunchedEffect).
     * Cancels any currently running speech first.
     */
    suspend fun speak(text: String) {
        if (!enabled || text.isBlank()) return
        withContext(Dispatchers.IO) {
            stop()
            val safe = sanitise(text)
            if (isWindows) speakWindows(safe) else speakFallback(safe)
        }
    }

    /** Immediately kill the current speech process (non-blocking). */
    fun stop() {
        currentProcess?.destroyForcibly()
        currentProcess = null
    }

    // ── Windows: System.Speech via PowerShell (built into all Win10/11 installs) ──

    private fun speakWindows(text: String) {
        // Inline PowerShell: creates SpeechSynthesizer, sets volume+rate, speaks sync.
        // -WindowStyle Hidden prevents a console flash.
        val ps = """
            Add-Type -AssemblyName System.Speech
            ${'$'}s = New-Object System.Speech.Synthesis.SpeechSynthesizer
            ${'$'}s.Volume = $volume
            ${'$'}s.Rate   = $rate
            ${'$'}s.Speak("$text")
        """.trimIndent()

        val cmd = arrayOf(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle", "Hidden",
            "-Command", ps,
        )
        runCatching {
            currentProcess = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
        }
        // Swallow any error — TTS is best-effort, not critical path.
    }

    // ── macOS/Linux: 'say' / 'espeak' if available ───────────────────────────

    private fun speakFallback(text: String) {
        val isMac   = System.getProperty("os.name", "").contains("Mac",   ignoreCase = true)
        val cmd     = if (isMac) arrayOf("say", text)
                      else       arrayOf("espeak", "-s", "150", text)
        runCatching {
            currentProcess = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
        }
    }

    // ── Sanitise text before injecting into PowerShell string ────────────────
    // Remove chars that would break the inline PS string or cause injection.
    private fun sanitise(text: String): String =
        text
            .replace("\"", " ")    // kills PS string terminator
            .replace("`",  " ")    // kills PS escape character
            .replace("$",  " ")    // kills PS variable expansion (already escaped above but belt+braces)
            .replace(";",  ",")    // kills PS statement separator
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .take(300)             // hard cap — no runaway TTS on large steps
}
