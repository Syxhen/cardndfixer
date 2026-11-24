package com.syxhen.cardndfixer.dnd

import android.app.NotificationManager
import com.syxhen.cardndfixer.data.AppPreferencesRepository
import com.syxhen.cardndfixer.data.AppSettings
import com.syxhen.cardndfixer.data.EventLogEntry
import com.syxhen.cardndfixer.data.EventType
import com.syxhen.cardndfixer.data.SettingsStore
import com.syxhen.cardndfixer.diagnostics.DiagnosticLogCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DndCycleCoordinatorTest {

    @Test
    fun disconnect_whenDndIsOn_disablesWaitsDelayAndReEnablesSameFilter() = runTest {
        val gateway = FakeDndGateway(
            policyGranted = true,
            initialFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        val store = FakeSettingsStore(
            AppSettings(
                automationEnabled = true,
                disconnectDebounceSeconds = 3,
                reEnableDelayMs = 2_000L,
                reEnableOnlyIfWasOn = true,
            ),
        )
        val coordinator = DndCycleCoordinator(
            dndGateway = gateway,
            settingsStore = store,
            scope = backgroundScope,
            clock = { 1000L },
        )

        coordinator.onCarDisconnected("My Car")
        runCurrent()

        // Still inside 3s debounce window
        advanceTimeBy(2_900L)
        assertTrue(gateway.appliedFilters.isEmpty())

        // Cross debounce threshold -> DND should be disabled now
        advanceTimeBy(200L)
        assertEquals(
            listOf(NotificationManager.INTERRUPTION_FILTER_ALL),
            gateway.appliedFilters,
        )

        // Still inside 2s re-enable delay -> should remain disabled
        advanceTimeBy(1_500L)
        assertEquals(
            listOf(NotificationManager.INTERRUPTION_FILTER_ALL),
            gateway.appliedFilters,
        )

        // Cross re-enable delay -> should restore INTERRUPTION_FILTER_PRIORITY
        advanceTimeBy(600L)
        assertEquals(
            listOf(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            ),
            gateway.appliedFilters,
        )
        assertEquals(EventType.SUCCESS, store.logs.first().type)
    }

    @Test
    fun disconnect_whenDndIsOffAndOnlyIfWasOn_doesNotReEnable() = runTest {
        val gateway = FakeDndGateway(
            policyGranted = true,
            initialFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        val store = FakeSettingsStore(
            AppSettings(
                automationEnabled = true,
                disconnectDebounceSeconds = 0,
                reEnableDelayMs = 1_500L,
                reEnableOnlyIfWasOn = true,
            ),
        )
        val coordinator = DndCycleCoordinator(
            dndGateway = gateway,
            settingsStore = store,
            scope = backgroundScope,
            clock = { 2000L },
        )

        coordinator.onCarDisconnected("My Car")
        advanceTimeBy(5_000L)

        assertEquals(
            listOf(NotificationManager.INTERRUPTION_FILTER_ALL),
            gateway.appliedFilters,
        )
        assertEquals(EventType.SUCCESS, store.logs.first().type)
    }

    @Test
    fun disconnect_whenDndIsOffAndAlwaysReEnable_reEnablesPriority() = runTest {
        val gateway = FakeDndGateway(
            policyGranted = true,
            initialFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        val store = FakeSettingsStore(
            AppSettings(
                automationEnabled = true,
                disconnectDebounceSeconds = 0,
                reEnableDelayMs = 1_000L,
                reEnableOnlyIfWasOn = false,
            ),
        )
        val coordinator = DndCycleCoordinator(
            dndGateway = gateway,
            settingsStore = store,
            scope = backgroundScope,
            clock = { 3000L },
        )

        coordinator.onCarDisconnected("My Car")
        advanceTimeBy(1_200L)

        assertEquals(
            listOf(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            ),
            gateway.appliedFilters,
        )
    }

    @Test
    fun reconnectDuringDebounce_cancelsPendingDisconnectCycle() = runTest {
        val gateway = FakeDndGateway(
            policyGranted = true,
            initialFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
        )
        val store = FakeSettingsStore(
            AppSettings(
                automationEnabled = true,
                disconnectDebounceSeconds = 5,
                reEnableDelayMs = 1_000L,
            ),
        )
        val coordinator = DndCycleCoordinator(
            dndGateway = gateway,
            settingsStore = store,
            scope = backgroundScope,
            clock = { 4000L },
        )

        coordinator.onCarDisconnected("My Car")
        advanceTimeBy(2_000L)
        coordinator.onCarConnected("My Car")
        advanceTimeBy(10_000L)

        assertTrue(gateway.appliedFilters.isEmpty())
        assertEquals("My Car", coordinator.runtimeStatus.value.connectedDeviceName)
    }

    @Test
    fun disconnect_whenAutomationPaused_skipsCycle() = runTest {
        val gateway = FakeDndGateway(
            policyGranted = true,
            initialFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        val store = FakeSettingsStore(
            AppSettings(
                automationEnabled = false,
                disconnectDebounceSeconds = 0,
                reEnableDelayMs = 500L,
            ),
        )
        val coordinator = DndCycleCoordinator(
            dndGateway = gateway,
            settingsStore = store,
            scope = backgroundScope,
            clock = { 5000L },
        )

        coordinator.onCarDisconnected("My Car")
        advanceTimeBy(2_000L)

        assertTrue(gateway.appliedFilters.isEmpty())
    }

    @Test
    fun disconnect_whenPolicyAccessMissing_logsWarningAndSkips() = runTest {
        val gateway = FakeDndGateway(
            policyGranted = false,
            initialFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        val store = FakeSettingsStore(
            AppSettings(
                automationEnabled = true,
                disconnectDebounceSeconds = 0,
                reEnableDelayMs = 500L,
            ),
        )
        val coordinator = DndCycleCoordinator(
            dndGateway = gateway,
            settingsStore = store,
            scope = backgroundScope,
            clock = { 6000L },
        )

        coordinator.onCarDisconnected("My Car")
        runCurrent()

        assertTrue(gateway.appliedFilters.isEmpty())
        assertEquals(EventType.WARNING, store.logs.first().type)
    }

    @Test
    fun manualTestCycle_skipsDebounceAndCyclesImmediately() = runTest {
        val gateway = FakeDndGateway(
            policyGranted = true,
            initialFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS,
        )
        val store = FakeSettingsStore(
            AppSettings(
                automationEnabled = false,
                disconnectDebounceSeconds = 10,
                reEnableDelayMs = 1_500L,
                reEnableOnlyIfWasOn = true,
            ),
        )
        val coordinator = DndCycleCoordinator(
            dndGateway = gateway,
            settingsStore = store,
            scope = backgroundScope,
            clock = { 7000L },
        )

        coordinator.runManualTestCycle()
        runCurrent()

        // Immediately disabled without waiting for the 10s disconnect debounce
        assertEquals(
            listOf(NotificationManager.INTERRUPTION_FILTER_ALL),
            gateway.appliedFilters,
        )

        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(
            listOf(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                NotificationManager.INTERRUPTION_FILTER_ALARMS,
            ),
            gateway.appliedFilters,
        )
    }

    @Test
    fun eventLogEncoding_roundTripsEntriesAccurately() {
        val original = listOf(
            EventLogEntry(
                timestampMillis = 123456789L,
                title = "Car disconnected (Mazda)",
                detail = "Disabled DND, waited 1.5s, re-enabled On (Priority only).",
                type = EventType.SUCCESS,
            ),
            EventLogEntry(
                timestampMillis = 123450000L,
                title = "Car connected (Mazda)",
                detail = "Current DND: On (Priority only)",
                type = EventType.INFO,
            ),
        )

        val encoded = AppPreferencesRepository.encodeLogs(original)
        val decoded = AppPreferencesRepository.decodeLogs(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun diagnosticMacRedaction_masksDeviceIdentifierBytes() {
        val raw = "Associated: My Car (AA:BB:CC:11:22:33) and aa:bb:cc:dd:ee:ff"
        val redacted = DiagnosticLogCollector.redactMacAddresses(raw)

        assertEquals(
            "Associated: My Car (AA:BB:CC:**:**:**) and aa:bb:cc:**:**:**",
            redacted,
        )
    }

    private class FakeDndGateway(
        private val policyGranted: Boolean,
        initialFilter: Int,
    ) : DndGateway {
        private var currentFilter = initialFilter
        val appliedFilters = mutableListOf<Int>()

        override fun isPolicyAccessGranted(): Boolean = policyGranted

        override fun getCurrentInterruptionFilter(): Int = currentFilter

        override fun setInterruptionFilter(filter: Int): Boolean {
            if (!policyGranted) return false
            currentFilter = filter
            appliedFilters.add(filter)
            return true
        }
    }

    private class FakeSettingsStore(
        initialSettings: AppSettings,
    ) : SettingsStore {
        private val settingsFlow = MutableStateFlow(initialSettings)
        private val logsFlow = MutableStateFlow<List<EventLogEntry>>(emptyList())

        val logs: List<EventLogEntry>
            get() = logsFlow.value

        override val settings: Flow<AppSettings> = settingsFlow
        override val eventLogs: Flow<List<EventLogEntry>> = logsFlow

        override suspend fun setAutomationEnabled(enabled: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(automationEnabled = enabled)
        }

        override suspend fun setDisconnectDebounceSeconds(seconds: Int) {
            settingsFlow.value = settingsFlow.value.copy(disconnectDebounceSeconds = seconds)
        }

        override suspend fun setReEnableDelayMs(delayMs: Long) {
            settingsFlow.value = settingsFlow.value.copy(reEnableDelayMs = delayMs)
        }

        override suspend fun setReEnableOnlyIfWasOn(onlyIfWasOn: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(reEnableOnlyIfWasOn = onlyIfWasOn)
        }

        override suspend fun setDiagnosticLoggingEnabled(enabled: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(diagnosticLoggingEnabled = enabled)
        }

        override suspend fun appendLog(entry: EventLogEntry) {
            logsFlow.value = listOf(entry) + logsFlow.value
        }

        override suspend fun clearLogs() {
            logsFlow.value = emptyList()
        }
    }
}
