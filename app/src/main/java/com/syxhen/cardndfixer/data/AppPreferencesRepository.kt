package com.syxhen.cardndfixer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syxhen.cardndfixer.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.carDndDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "car_dnd_fixer_prefs",
)

interface SettingsStore {
    val settings: Flow<AppSettings>
    val eventLogs: Flow<List<EventLogEntry>>

    suspend fun setAutomationEnabled(enabled: Boolean)
    suspend fun setDisconnectDebounceSeconds(seconds: Int)
    suspend fun setReEnableDelayMs(delayMs: Long)
    suspend fun setReEnableOnlyIfWasOn(onlyIfWasOn: Boolean)
    suspend fun setDiagnosticLoggingEnabled(enabled: Boolean)
    suspend fun appendLog(entry: EventLogEntry)
    suspend fun clearLogs()
}

class AppPreferencesRepository(
    private val context: Context,
) : SettingsStore {

    override val settings: Flow<AppSettings> = context.carDndDataStore.data.map { prefs ->
        AppSettings(
            automationEnabled = prefs[KEY_AUTOMATION_ENABLED] ?: true,
            disconnectDebounceSeconds = (prefs[KEY_DISCONNECT_DEBOUNCE_SECONDS]
                ?: AppSettings.DEFAULT_DISCONNECT_DEBOUNCE_SECONDS)
                .coerceIn(
                    AppSettings.MIN_DISCONNECT_DEBOUNCE_SECONDS,
                    AppSettings.MAX_DISCONNECT_DEBOUNCE_SECONDS,
                ),
            reEnableDelayMs = (prefs[KEY_RE_ENABLE_DELAY_MS]
                ?: AppSettings.DEFAULT_RE_ENABLE_DELAY_MS)
                .coerceIn(
                    AppSettings.MIN_RE_ENABLE_DELAY_MS,
                    AppSettings.MAX_RE_ENABLE_DELAY_MS,
                ),
            reEnableOnlyIfWasOn = prefs[KEY_RE_ENABLE_ONLY_IF_WAS_ON] ?: true,
            diagnosticLoggingEnabled = prefs[KEY_DIAGNOSTIC_LOGGING_ENABLED]
                ?: BuildConfig.DEBUG_TOOLS_ENABLED,
        )
    }

    override val eventLogs: Flow<List<EventLogEntry>> = context.carDndDataStore.data.map { prefs ->
        decodeLogs(prefs[KEY_EVENT_LOGS].orEmpty())
    }

    override suspend fun setAutomationEnabled(enabled: Boolean) {
        context.carDndDataStore.edit { prefs ->
            prefs[KEY_AUTOMATION_ENABLED] = enabled
        }
    }

    override suspend fun setDisconnectDebounceSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            AppSettings.MIN_DISCONNECT_DEBOUNCE_SECONDS,
            AppSettings.MAX_DISCONNECT_DEBOUNCE_SECONDS,
        )
        context.carDndDataStore.edit { prefs ->
            prefs[KEY_DISCONNECT_DEBOUNCE_SECONDS] = clamped
        }
    }

    override suspend fun setReEnableDelayMs(delayMs: Long) {
        val clamped = delayMs.coerceIn(
            AppSettings.MIN_RE_ENABLE_DELAY_MS,
            AppSettings.MAX_RE_ENABLE_DELAY_MS,
        )
        context.carDndDataStore.edit { prefs ->
            prefs[KEY_RE_ENABLE_DELAY_MS] = clamped
        }
    }

    override suspend fun setReEnableOnlyIfWasOn(onlyIfWasOn: Boolean) {
        context.carDndDataStore.edit { prefs ->
            prefs[KEY_RE_ENABLE_ONLY_IF_WAS_ON] = onlyIfWasOn
        }
    }

    override suspend fun setDiagnosticLoggingEnabled(enabled: Boolean) {
        context.carDndDataStore.edit { prefs ->
            prefs[KEY_DIAGNOSTIC_LOGGING_ENABLED] = enabled
        }
    }

    override suspend fun appendLog(entry: EventLogEntry) {
        context.carDndDataStore.edit { prefs ->
            val current = decodeLogs(prefs[KEY_EVENT_LOGS].orEmpty())
            val updated = (listOf(entry) + current).take(MAX_LOG_ENTRIES)
            prefs[KEY_EVENT_LOGS] = encodeLogs(updated)
        }
    }

    override suspend fun clearLogs() {
        context.carDndDataStore.edit { prefs ->
            prefs.remove(KEY_EVENT_LOGS)
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 25
        private const val RECORD_SEPARATOR = "\u001E"
        private const val FIELD_SEPARATOR = "\u001F"

        private val KEY_AUTOMATION_ENABLED = booleanPreferencesKey("automation_enabled")
        private val KEY_DISCONNECT_DEBOUNCE_SECONDS = intPreferencesKey("disconnect_debounce_seconds")
        private val KEY_RE_ENABLE_DELAY_MS = longPreferencesKey("re_enable_delay_ms")
        private val KEY_RE_ENABLE_ONLY_IF_WAS_ON = booleanPreferencesKey("re_enable_only_if_was_on")
        private val KEY_DIAGNOSTIC_LOGGING_ENABLED = booleanPreferencesKey("diagnostic_logging_enabled")
        private val KEY_EVENT_LOGS = stringPreferencesKey("event_logs")

        internal fun encodeLogs(entries: List<EventLogEntry>): String =
            entries.joinToString(RECORD_SEPARATOR) { entry ->
                listOf(
                    entry.timestampMillis.toString(),
                    entry.type.name,
                    entry.title.replace(RECORD_SEPARATOR, " ").replace(FIELD_SEPARATOR, " "),
                    entry.detail.replace(RECORD_SEPARATOR, " ").replace(FIELD_SEPARATOR, " "),
                ).joinToString(FIELD_SEPARATOR)
            }

        internal fun decodeLogs(raw: String): List<EventLogEntry> {
            if (raw.isBlank()) return emptyList()
            return raw.split(RECORD_SEPARATOR).mapNotNull { record ->
                val parts = record.split(FIELD_SEPARATOR)
                if (parts.size < 4) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val type = runCatching { EventType.valueOf(parts[1]) }.getOrDefault(EventType.INFO)
                EventLogEntry(
                    timestampMillis = timestamp,
                    type = type,
                    title = parts[2],
                    detail = parts[3],
                )
            }
        }
    }
}
