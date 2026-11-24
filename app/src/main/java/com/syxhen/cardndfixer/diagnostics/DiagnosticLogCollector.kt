package com.syxhen.cardndfixer.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.syxhen.cardndfixer.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DiagnosticLogSummary(
    val lineCount: Int = 0,
    val sizeBytes: Long = 0L,
)

interface DiagnosticSink {
    fun record(tag: String, message: String)
}

object NoOpDiagnosticSink : DiagnosticSink {
    override fun record(tag: String, message: String) = Unit
}

class DiagnosticLogCollector(
    private val context: Context,
    initialEnabled: Boolean = BuildConfig.DEBUG_TOOLS_ENABLED,
) : DiagnosticSink {

    private val lock = Any()
    private val logDir: File = File(context.filesDir, "diagnostics")
    private val logFile: File = File(logDir, LOG_FILE_NAME)

    @Volatile
    private var enabled: Boolean = initialEnabled

    private val _summary = MutableStateFlow(readSummary())
    val summary: StateFlow<DiagnosticLogSummary> = _summary.asStateFlow()

    fun setEnabled(isEnabled: Boolean) {
        val previous = enabled
        enabled = isEnabled
        if (isEnabled && !previous) {
            record("Diagnostics", "Diagnostic log collection enabled")
        }
    }

    override fun record(tag: String, message: String) {
        if (BuildConfig.DEBUG_TOOLS_ENABLED) {
            Log.d(tag, message)
        }
        if (!enabled) return

        val timestamp = TIMESTAMP_FORMATTER.format(
            Instant.now().atZone(ZoneId.systemDefault()),
        )
        val line = "$timestamp [$tag] ${redactMacAddresses(message)}\n"

        synchronized(lock) {
            runCatching {
                if (!logDir.exists()) logDir.mkdirs()
                logFile.appendText(line)
                trimIfOversizedLocked()
            }
            _summary.value = readSummaryLocked()
        }
    }

    fun buildReportText(
        dndAccessGranted: Boolean,
        bluetoothGranted: Boolean,
        batteryOptimizationIgnored: Boolean,
        automationEnabled: Boolean,
        disconnectDebounceSeconds: Int,
        reEnableDelayMs: Long,
        reEnableOnlyIfWasOn: Boolean,
        associatedDevices: List<String>,
        currentDndLabel: String,
    ): String {
        val header = buildString {
            appendLine("=== cardndfixer diagnostic report ===")
            appendLine("Generated: ${TIMESTAMP_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}, build=${BuildConfig.BUILD_TYPE}, pkg=${BuildConfig.APPLICATION_ID})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("Permissions: dndAccess=$dndAccessGranted, bluetoothConnect=$bluetoothGranted, batteryUnrestricted=$batteryOptimizationIgnored")
            appendLine("Settings: automation=$automationEnabled, disconnectDebounce=${disconnectDebounceSeconds}s, reEnableDelay=${reEnableDelayMs}ms, onlyIfWasOn=$reEnableOnlyIfWasOn, logging=$enabled")
            appendLine("Current DND: $currentDndLabel")
            val deviceList = if (associatedDevices.isEmpty()) {
                "none"
            } else {
                associatedDevices.joinToString(", ") { redactMacAddresses(it) }
            }
            appendLine("Associated devices: $deviceList")
            appendLine("=== Trace ===")
        }

        val body = synchronized(lock) {
            if (logFile.exists()) {
                redactMacAddresses(logFile.readText())
            } else {
                "(no diagnostic entries recorded yet)\n"
            }
        }
        return header + body
    }

    fun writeExportFile(reportText: String): File {
        synchronized(lock) {
            if (!logDir.exists()) logDir.mkdirs()
            val exportFile = File(logDir, EXPORT_FILE_NAME)
            exportFile.writeText(reportText)
            return exportFile
        }
    }

    fun clear() {
        synchronized(lock) {
            if (logFile.exists()) {
                logFile.delete()
            }
            _summary.value = DiagnosticLogSummary()
        }
    }

    private fun readSummary(): DiagnosticLogSummary = synchronized(lock) { readSummaryLocked() }

    private fun readSummaryLocked(): DiagnosticLogSummary {
        if (!logFile.exists()) return DiagnosticLogSummary()
        val lines = runCatching { logFile.readLines().size }.getOrDefault(0)
        return DiagnosticLogSummary(
            lineCount = lines,
            sizeBytes = logFile.length(),
        )
    }

    private fun trimIfOversizedLocked() {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return
        val lines = logFile.readLines()
        val kept = lines.takeLast(MAX_RETAINED_LINES)
        logFile.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    companion object {
        private const val LOG_FILE_NAME = "cardndfixer-diagnostics.log"
        private const val EXPORT_FILE_NAME = "cardndfixer-report.txt"
        private const val MAX_LOG_BYTES = 128 * 1024L
        private const val MAX_RETAINED_LINES = 400

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        private val MAC_REGEX =
            Regex("([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}):[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}")

        fun redactMacAddresses(input: String): String =
            MAC_REGEX.replace(input) { match -> "${match.groupValues[1]}:**:**:**" }
    }
}
