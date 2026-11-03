package com.example.core.security

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sensitive-data-safe in-memory diagnostic logger.
 *
 * Security Rules:
 * - Never writes to external storage or network.
 * - Redacts URLs, emails, phone numbers, and long numeric sequences automatically.
 * - Retains at most 80 ring-buffer events in RAM for local user diagnostics.
 */
object SecureLogger {
    private const val MAX_ENTRIES = 80
    private val urlPattern = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val emailPattern = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
    private val digitRunPattern = Regex("""\b\d{4,}\b""")

    data class LogEntry(
        val timestamp: String,
        val module: String,
        val message: String,
        val isWarning: Boolean = false
    )

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun info(module: String, message: String) {
        append(module, message, isWarning = false)
    }

    fun warn(module: String, message: String) {
        append(module, message, isWarning = true)
    }

    fun sanitizeMessage(raw: String): String {
        return raw
            .replace(urlPattern, "[REDACTED_URL]")
            .replace(emailPattern, "[REDACTED_EMAIL]")
            .replace(digitRunPattern, "[REDACTED_NUM]")
            .take(220)
    }

    private fun append(module: String, rawMessage: String, isWarning: Boolean) {
        val safeModule = module.take(28)
        val safeMessage = sanitizeMessage(rawMessage)
        val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = LogEntry(
            timestamp = timeFmt,
            module = safeModule,
            message = safeMessage,
            isWarning = isWarning
        )
        synchronized(this) {
            val updated = (_entries.value + entry).takeLast(MAX_ENTRIES)
            _entries.value = updated
        }
    }

    fun clear() {
        synchronized(this) {
            _entries.value = emptyList()
        }
    }
}
