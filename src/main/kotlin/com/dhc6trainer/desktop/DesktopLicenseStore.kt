package com.dhc6trainer.desktop

import java.security.MessageDigest
import java.util.Locale
import java.util.prefs.Preferences

internal object DesktopLicenseStore {
    private const val NodeName = "com.dhc6trainer.desktop.license"
    private const val KeyActivated = "activated"
    private const val KeyEmail = "email"
    private const val KeyLicense = "licenseKey"

    private const val ProductPrefix = "DHC6-DESKTOP-169"
    private const val ProductSecret = "DHC6-TRAINER-DESKTOP-LOCAL-ACTIVATION-2026-V1"

    private val prefs: Preferences =
        Preferences.userRoot().node(NodeName)

    fun isActivated(): Boolean {
        return prefs.getBoolean(KeyActivated, false) &&
            prefs.get(KeyLicense, "").isValidDesktopLicenseKey()
    }

    fun activate(email: String, licenseKey: String): ActivationResult {
        val cleanEmail = email.trim()
        val cleanKey = licenseKey.trim().uppercase(Locale.US)

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return ActivationResult(false, "Enter the email address used for desktop access.")
        }

        if (!cleanKey.isValidDesktopLicenseKey()) {
            return ActivationResult(
                false,
                "Invalid desktop license key. Enter the exact key issued for this DHC-6 Trainer Desktop build."
            )
        }

        prefs.putBoolean(KeyActivated, true)
        prefs.put(KeyEmail, cleanEmail)
        prefs.put(KeyLicense, cleanKey)
        prefs.safeFlush()

        return ActivationResult(true, "Desktop activation complete.")
    }

    fun deactivate() {
        prefs.remove(KeyActivated)
        prefs.remove(KeyEmail)
        prefs.remove(KeyLicense)
        prefs.safeFlush()
    }

    fun savedEmail(): String = prefs.get(KeyEmail, "")

    private fun String.isValidDesktopLicenseKey(): Boolean {
        val key = trim().uppercase(Locale.US)
        val parts = key.split("-")

        if (parts.size != 5) return false

        val prefix = parts.take(3).joinToString("-")
        if (prefix != ProductPrefix) return false

        val customerCode = parts[3]
        val suppliedChecksum = parts[4]

        if (!customerCode.matches(Regex("[A-Z0-9]{4,18}"))) return false
        if (!suppliedChecksum.matches(Regex("[A-F0-9]{10}"))) return false

        val expectedChecksum = expectedChecksumFor(customerCode)

        return suppliedChecksum == expectedChecksum
    }

    private fun expectedChecksumFor(customerCode: String): String {
        val payload = "$customerCode:$ProductPrefix:$ProductSecret"
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { "%02X".format(it) }.take(10)
    }

    private fun Preferences.safeFlush() {
        runCatching { flush() }
    }
}

internal data class ActivationResult(
    val success: Boolean,
    val message: String,
)
