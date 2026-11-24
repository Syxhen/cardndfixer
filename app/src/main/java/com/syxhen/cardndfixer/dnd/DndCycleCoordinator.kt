package com.syxhen.cardndfixer.dnd

import android.app.NotificationManager
import com.syxhen.cardndfixer.data.EventLogEntry
import com.syxhen.cardndfixer.data.EventType
import com.syxhen.cardndfixer.data.SettingsStore
import com.syxhen.cardndfixer.diagnostics.DiagnosticSink
import com.syxhen.cardndfixer.diagnostics.NoOpDiagnosticSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed interface CycleStatus {
    data object Idle : CycleStatus
    data class WaitingDebounce(val deviceName: String, val seconds: Int) : CycleStatus
    data class Cycling(val message: String) : CycleStatus
}

data class CarRuntimeStatus(
    val connectedDeviceName: String? = null,
    val cycleStatus: CycleStatus = CycleStatus.Idle,
    val lastDndFilter: Int = NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
)

class DndCycleCoordinator(
    private val dndGateway: DndGateway,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val diagnostics: DiagnosticSink = NoOpDiagnosticSink,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _runtimeStatus = MutableStateFlow(
        CarRuntimeStatus(lastDndFilter = safeCurrentFilter()),
    )
    val runtimeStatus: StateFlow<CarRuntimeStatus> = _runtimeStatus.asStateFlow()

    private var activeDisconnectJob: Job? = null

    fun refreshDndStatus() {
        _runtimeStatus.value = _runtimeStatus.value.copy(
            lastDndFilter = safeCurrentFilter(),
        )
    }

    fun onCarConnected(deviceName: String): Job = scope.launch {
        val cancelledPending = activeDisconnectJob?.isActive == true
        activeDisconnectJob?.cancel()
        activeDisconnectJob = null

        val currentFilter = safeCurrentFilter()
        diagnostics.record(
            TAG,
            "onCarConnected(device=$deviceName, cancelledPending=$cancelledPending, currentFilter=$currentFilter)",
        )

        _runtimeStatus.value = _runtimeStatus.value.copy(
            connectedDeviceName = deviceName,
            cycleStatus = CycleStatus.Idle,
            lastDndFilter = currentFilter,
        )

        val detail = if (cancelledPending) {
            "Reconnected before disconnect wait finished; cancelled pending DND cycle."
        } else {
            "Current DND: ${DndFilterLabels.describe(currentFilter)}"
        }
        settingsStore.appendLog(
            EventLogEntry(
                timestampMillis = clock(),
                title = "Car connected ($deviceName)",
                detail = detail,
                type = EventType.INFO,
            ),
        )
    }

    fun onCarDisconnected(deviceName: String): Job {
        activeDisconnectJob?.cancel()
        val job = scope.launch {
            _runtimeStatus.value = _runtimeStatus.value.copy(
                connectedDeviceName = null,
            )

            val settings = settingsStore.settings.first()
            diagnostics.record(
                TAG,
                "onCarDisconnected(device=$deviceName, automation=${settings.automationEnabled}, debounce=${settings.disconnectDebounceSeconds}s, delay=${settings.reEnableDelayMs}ms, onlyIfWasOn=${settings.reEnableOnlyIfWasOn})",
            )

            if (!settings.automationEnabled) {
                settingsStore.appendLog(
                    EventLogEntry(
                        timestampMillis = clock(),
                        title = "Car disconnected ($deviceName)",
                        detail = "Automation is paused; skipped DND cycle.",
                        type = EventType.INFO,
                    ),
                )
                return@launch
            }

            try {
                if (settings.disconnectDebounceSeconds > 0) {
                    _runtimeStatus.value = _runtimeStatus.value.copy(
                        cycleStatus = CycleStatus.WaitingDebounce(
                            deviceName = deviceName,
                            seconds = settings.disconnectDebounceSeconds,
                        ),
                    )
                    diagnostics.record(TAG, "Waiting ${settings.disconnectDebounceSeconds}s disconnect debounce")
                    delay(settings.disconnectDebounceSeconds.seconds)
                }

                executeCycle(
                    triggerLabel = "Car disconnected ($deviceName)",
                    reEnableDelayMs = settings.reEnableDelayMs,
                    reEnableOnlyIfWasOn = settings.reEnableOnlyIfWasOn,
                )
            } catch (e: CancellationException) {
                diagnostics.record(TAG, "Disconnect cycle cancelled for $deviceName")
                _runtimeStatus.value = _runtimeStatus.value.copy(
                    cycleStatus = CycleStatus.Idle,
                )
                throw e
            } finally {
                _runtimeStatus.value = _runtimeStatus.value.copy(
                    cycleStatus = CycleStatus.Idle,
                    lastDndFilter = safeCurrentFilter(),
                )
            }
        }
        activeDisconnectJob = job
        return job
    }

    fun runManualTestCycle(): Job {
        activeDisconnectJob?.cancel()
        val job = scope.launch {
            val settings = settingsStore.settings.first()
            diagnostics.record(
                TAG,
                "runManualTestCycle(delay=${settings.reEnableDelayMs}ms, onlyIfWasOn=${settings.reEnableOnlyIfWasOn})",
            )
            try {
                executeCycle(
                    triggerLabel = "Manual test cycle",
                    reEnableDelayMs = settings.reEnableDelayMs,
                    reEnableOnlyIfWasOn = settings.reEnableOnlyIfWasOn,
                )
            } finally {
                _runtimeStatus.value = _runtimeStatus.value.copy(
                    cycleStatus = CycleStatus.Idle,
                    lastDndFilter = safeCurrentFilter(),
                )
            }
        }
        activeDisconnectJob = job
        return job
    }

    private suspend fun executeCycle(
        triggerLabel: String,
        reEnableDelayMs: Long,
        reEnableOnlyIfWasOn: Boolean,
    ) {
        if (!dndGateway.isPolicyAccessGranted()) {
            diagnostics.record(TAG, "$triggerLabel: policy access not granted")
            settingsStore.appendLog(
                EventLogEntry(
                    timestampMillis = clock(),
                    title = triggerLabel,
                    detail = "Skipped: Do Not Disturb access is not granted.",
                    type = EventType.WARNING,
                ),
            )
            return
        }

        val initialFilter = dndGateway.getCurrentInterruptionFilter()
        val wasOn = DndFilterLabels.isDndActive(initialFilter)
        diagnostics.record(TAG, "$triggerLabel: initialFilter=$initialFilter (wasOn=$wasOn)")

        _runtimeStatus.value = _runtimeStatus.value.copy(
            cycleStatus = CycleStatus.Cycling("Disabling Do Not Disturb…"),
            lastDndFilter = initialFilter,
        )

        val disabled = dndGateway.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        val afterDisable = safeCurrentFilter()
        diagnostics.record(TAG, "$triggerLabel: setInterruptionFilter(ALL) -> ok=$disabled, readBack=$afterDisable")
        if (!disabled) {
            settingsStore.appendLog(
                EventLogEntry(
                    timestampMillis = clock(),
                    title = triggerLabel,
                    detail = "Failed to disable Do Not Disturb.",
                    type = EventType.WARNING,
                ),
            )
            return
        }

        _runtimeStatus.value = _runtimeStatus.value.copy(
            lastDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
        )

        val shouldReEnable = wasOn || !reEnableOnlyIfWasOn
        if (!shouldReEnable) {
            diagnostics.record(TAG, "$triggerLabel: DND was OFF and onlyIfWasOn=true; done")
            settingsStore.appendLog(
                EventLogEntry(
                    timestampMillis = clock(),
                    title = triggerLabel,
                    detail = "DND was OFF; left OFF without re-enabling.",
                    type = EventType.SUCCESS,
                ),
            )
            return
        }

        val delaySecondsText = String.format(Locale.US, "%.1fs", reEnableDelayMs / 1000.0)
        _runtimeStatus.value = _runtimeStatus.value.copy(
            cycleStatus = CycleStatus.Cycling("Waiting $delaySecondsText before re-enabling DND…"),
        )
        delay(reEnableDelayMs.milliseconds)

        val targetFilter = if (wasOn) {
            initialFilter
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }

        _runtimeStatus.value = _runtimeStatus.value.copy(
            cycleStatus = CycleStatus.Cycling("Re-enabling Do Not Disturb…"),
        )
        val reEnabled = dndGateway.setInterruptionFilter(targetFilter)
        val afterReEnable = safeCurrentFilter()
        diagnostics.record(
            TAG,
            "$triggerLabel: setInterruptionFilter($targetFilter) -> ok=$reEnabled, readBack=$afterReEnable",
        )
        _runtimeStatus.value = _runtimeStatus.value.copy(
            lastDndFilter = if (reEnabled) targetFilter else afterReEnable,
        )

        if (reEnabled) {
            settingsStore.appendLog(
                EventLogEntry(
                    timestampMillis = clock(),
                    title = triggerLabel,
                    detail = "Disabled DND, waited $delaySecondsText, re-enabled ${DndFilterLabels.describe(targetFilter)}.",
                    type = EventType.SUCCESS,
                ),
            )
        } else {
            settingsStore.appendLog(
                EventLogEntry(
                    timestampMillis = clock(),
                    title = triggerLabel,
                    detail = "Disabled DND, waited $delaySecondsText, but failed to re-enable DND.",
                    type = EventType.WARNING,
                ),
            )
        }
    }

    private fun safeCurrentFilter(): Int =
        runCatching { dndGateway.getCurrentInterruptionFilter() }
            .getOrDefault(NotificationManager.INTERRUPTION_FILTER_UNKNOWN)

    companion object {
        private const val TAG = "DndCycleCoordinator"
    }
}
