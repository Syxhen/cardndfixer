package com.syxhen.cardndfixer.data

import com.syxhen.cardndfixer.BuildConfig

enum class UpdateChannel {
    STABLE,
    BETA,
}

data class AppSettings(
    val automationEnabled: Boolean = true,
    val disconnectDebounceSeconds: Int = DEFAULT_DISCONNECT_DEBOUNCE_SECONDS,
    val reEnableDelayMs: Long = DEFAULT_RE_ENABLE_DELAY_MS,
    val reEnableOnlyIfWasOn: Boolean = true,
    val diagnosticLoggingEnabled: Boolean = BuildConfig.DEBUG_TOOLS_ENABLED,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
) {
    companion object {
        const val DEFAULT_DISCONNECT_DEBOUNCE_SECONDS = 3
        const val MIN_DISCONNECT_DEBOUNCE_SECONDS = 0
        const val MAX_DISCONNECT_DEBOUNCE_SECONDS = 15

        const val DEFAULT_RE_ENABLE_DELAY_MS = 1500L
        const val MIN_RE_ENABLE_DELAY_MS = 500L
        const val MAX_RE_ENABLE_DELAY_MS = 10_000L
    }
}

enum class EventType {
    INFO,
    SUCCESS,
    WARNING,
}

data class EventLogEntry(
    val timestampMillis: Long,
    val title: String,
    val detail: String,
    val type: EventType,
)
