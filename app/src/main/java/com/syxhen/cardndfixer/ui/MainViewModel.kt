package com.syxhen.cardndfixer.ui

import android.Manifest
import android.app.Application
import android.companion.AssociationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syxhen.cardndfixer.BuildConfig
import com.syxhen.cardndfixer.CarDndApplication
import com.syxhen.cardndfixer.data.AppSettings
import com.syxhen.cardndfixer.data.EventLogEntry
import com.syxhen.cardndfixer.data.EventType
import com.syxhen.cardndfixer.diagnostics.DiagnosticLogSummary
import com.syxhen.cardndfixer.dnd.CarRuntimeStatus
import com.syxhen.cardndfixer.dnd.DndFilterLabels
import com.syxhen.cardndfixer.service.AssociatedCarDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SystemPermissionsState(
    val dndAccessGranted: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val companionSupported: Boolean = true,
    val associatedDevices: List<AssociatedCarDevice> = emptyList(),
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val runtimeStatus: CarRuntimeStatus = CarRuntimeStatus(),
    val eventLogs: List<EventLogEntry> = emptyList(),
    val systemState: SystemPermissionsState = SystemPermissionsState(),
    val diagnosticSummary: DiagnosticLogSummary = DiagnosticLogSummary(),
    val debugToolsEnabled: Boolean = BuildConfig.DEBUG_TOOLS_ENABLED,
    val appVersionLabel: String = BuildConfig.VERSION_NAME,
    val isPairingInProgress: Boolean = false,
    val statusMessage: String? = null,
) {
    val isReadyForAutomation: Boolean
        get() = systemState.dndAccessGranted &&
            systemState.bluetoothPermissionGranted &&
            systemState.associatedDevices.isNotEmpty()
}

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app: CarDndApplication = application as CarDndApplication
    private val preferences = app.preferencesRepository
    private val dndGateway = app.dndGateway
    private val companionManager = app.companionManager
    private val coordinator = app.dndCycleCoordinator
    private val diagnostics = app.diagnosticLogCollector

    private val systemState = MutableStateFlow(readSystemState())
    private val pairingInProgress = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    private val transientUiState = combine(
        systemState,
        diagnostics.summary,
        pairingInProgress,
        statusMessage,
    ) { sys, diagSummary, pairing, msg ->
        TransientBundle(sys, diagSummary, pairing, msg)
    }

    val uiState: StateFlow<MainUiState> = combine(
        preferences.settings,
        coordinator.runtimeStatus,
        preferences.eventLogs,
        transientUiState,
    ) { settings, runtime, logs, transient ->
        MainUiState(
            settings = settings,
            runtimeStatus = runtime,
            eventLogs = logs,
            systemState = transient.systemState,
            diagnosticSummary = transient.diagnosticSummary,
            debugToolsEnabled = BuildConfig.DEBUG_TOOLS_ENABLED,
            appVersionLabel = BuildConfig.VERSION_NAME,
            isPairingInProgress = transient.isPairingInProgress,
            statusMessage = transient.statusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(systemState = systemState.value),
    )

    fun refreshSystemState() {
        coordinator.refreshDndStatus()
        companionManager.ensureObservingAll()
        systemState.value = readSystemState()
    }

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutomationEnabled(enabled)
        }
    }

    fun setDisconnectDebounceSeconds(seconds: Int) {
        viewModelScope.launch {
            preferences.setDisconnectDebounceSeconds(seconds)
        }
    }

    fun setReEnableDelayMs(delayMs: Long) {
        viewModelScope.launch {
            preferences.setReEnableDelayMs(delayMs)
        }
    }

    fun setReEnableOnlyIfWasOn(onlyIfWasOn: Boolean) {
        viewModelScope.launch {
            preferences.setReEnableOnlyIfWasOn(onlyIfWasOn)
        }
    }

    fun setDiagnosticLoggingEnabled(enabled: Boolean) {
        diagnostics.setEnabled(enabled)
        viewModelScope.launch {
            preferences.setDiagnosticLoggingEnabled(enabled)
        }
    }

    fun runManualTestCycle() {
        coordinator.runManualTestCycle()
    }

    fun simulateCarConnect() {
        diagnostics.record("DebugSimulator", "Simulated car Bluetooth connect")
        coordinator.onCarConnected("Simulated Car")
    }

    fun simulateCarDisconnect() {
        diagnostics.record("DebugSimulator", "Simulated car Bluetooth disconnect")
        coordinator.onCarDisconnected("Simulated Car")
    }

    fun buildDiagnosticReport(): String {
        val current = uiState.value
        val devices = current.systemState.associatedDevices.map { dev ->
            if (dev.macAddress != null) "${dev.displayName} (${dev.macAddress})" else dev.displayName
        }
        return diagnostics.buildReportText(
            dndAccessGranted = current.systemState.dndAccessGranted,
            bluetoothGranted = current.systemState.bluetoothPermissionGranted,
            batteryOptimizationIgnored = current.systemState.batteryOptimizationIgnored,
            automationEnabled = current.settings.automationEnabled,
            disconnectDebounceSeconds = current.settings.disconnectDebounceSeconds,
            reEnableDelayMs = current.settings.reEnableDelayMs,
            reEnableOnlyIfWasOn = current.settings.reEnableOnlyIfWasOn,
            associatedDevices = devices,
            currentDndLabel = DndFilterLabels.describe(current.runtimeStatus.lastDndFilter),
        )
    }

    fun exportDiagnosticReportFile(): File =
        diagnostics.writeExportFile(buildDiagnosticReport())

    fun clearDiagnosticLogs() {
        diagnostics.clear()
        statusMessage.value = "Diagnostic log cleared."
    }

    fun showStatusMessage(message: String) {
        statusMessage.value = message
    }

    fun onPairingStarted() {
        pairingInProgress.value = true
    }

    fun onAssociationCreated(associationInfo: AssociationInfo) {
        pairingInProgress.value = false
        val observeResult = companionManager.startObserving(associationInfo)
        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            associationInfo.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: associationInfo.deviceMacAddress?.toString()
                ?: "Car Bluetooth"
        } else {
            "Car Bluetooth"
        }

        handleObservationResult(deviceName, observeResult)
    }

    fun onLegacyAssociationCreated(deviceName: String, macAddress: String) {
        pairingInProgress.value = false
        val observeResult = companionManager.startObserving(
            associationId = null,
            macAddress = macAddress,
        )
        handleObservationResult(deviceName, observeResult)
    }

    private fun handleObservationResult(deviceName: String, observeResult: Result<Unit>) {
        systemState.value = readSystemState()
        viewModelScope.launch {
            if (observeResult.isSuccess) {
                preferences.appendLog(
                    EventLogEntry(
                        timestampMillis = System.currentTimeMillis(),
                        title = "Paired $deviceName",
                        detail = "Observing Bluetooth connection and disconnect events via CompanionDeviceService.",
                        type = EventType.SUCCESS,
                    ),
                )
                statusMessage.value = "Paired $deviceName. Watching for car disconnects."
            } else {
                val reason = observeResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                preferences.appendLog(
                    EventLogEntry(
                        timestampMillis = System.currentTimeMillis(),
                        title = "Paired $deviceName (observation warning)",
                        detail = "Failed to start presence observation: $reason",
                        type = EventType.WARNING,
                    ),
                )
                statusMessage.value = "Paired $deviceName, but presence observation failed: $reason"
            }
        }
    }

    fun onPairingError(message: String) {
        pairingInProgress.value = false
        diagnostics.record("Pairing", "Error: $message")
        statusMessage.value = message
    }

    fun onPairingCancelled() {
        pairingInProgress.value = false
        refreshSystemState()
    }

    fun removeAssociatedDevice(device: AssociatedCarDevice) {
        val result = companionManager.disassociate(device)
        systemState.value = readSystemState()
        viewModelScope.launch {
            if (result.isSuccess) {
                preferences.appendLog(
                    EventLogEntry(
                        timestampMillis = System.currentTimeMillis(),
                        title = "Removed ${device.displayName}",
                        detail = "Stopped observing this car Bluetooth device.",
                        type = EventType.INFO,
                    ),
                )
                statusMessage.value = "Removed ${device.displayName}"
            } else {
                statusMessage.value = "Could not remove ${device.displayName}."
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            preferences.clearLogs()
        }
    }

    fun consumeStatusMessage() {
        statusMessage.value = null
    }

    private fun readSystemState(): SystemPermissionsState {
        val btGranted = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

        val powerManager = app.getSystemService(PowerManager::class.java)
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(app.packageName) == true

        return SystemPermissionsState(
            dndAccessGranted = dndGateway.isPolicyAccessGranted(),
            bluetoothPermissionGranted = btGranted,
            batteryOptimizationIgnored = batteryIgnored,
            companionSupported = companionManager.isSupported(),
            associatedDevices = companionManager.getAssociatedDevices(),
        )
    }

    private data class TransientBundle(
        val systemState: SystemPermissionsState,
        val diagnosticSummary: DiagnosticLogSummary,
        val isPairingInProgress: Boolean,
        val statusMessage: String?,
    )
}
