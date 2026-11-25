package com.syxhen.cardndfixer.ui

import android.app.NotificationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.syxhen.cardndfixer.R
import com.syxhen.cardndfixer.data.AppSettings
import com.syxhen.cardndfixer.data.EventLogEntry
import com.syxhen.cardndfixer.data.EventType
import com.syxhen.cardndfixer.data.UpdateChannel
import com.syxhen.cardndfixer.diagnostics.DiagnosticLogSummary
import com.syxhen.cardndfixer.dnd.CarRuntimeStatus
import com.syxhen.cardndfixer.dnd.CycleStatus
import com.syxhen.cardndfixer.dnd.DndFilterLabels
import com.syxhen.cardndfixer.service.AssociatedCarDevice
import com.syxhen.cardndfixer.ui.theme.CarDndTheme
import com.syxhen.cardndfixer.updater.AvailableUpdate
import com.syxhen.cardndfixer.updater.UpdateUiState
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onToggleAutomation: (Boolean) -> Unit,
    onOpenDndSettings: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onPairCarDevice: () -> Unit,
    onRemoveCarDevice: (AssociatedCarDevice) -> Unit,
    onDisconnectDebounceChanged: (Int) -> Unit,
    onReEnableDelayMsChanged: (Long) -> Unit,
    onReEnableOnlyIfWasOnChanged: (Boolean) -> Unit,
    onRunManualTest: () -> Unit,
    onToggleDiagnosticLogging: (Boolean) -> Unit,
    onShareDiagnosticLog: () -> Unit,
    onCopyDiagnosticLog: () -> Unit,
    onClearDiagnosticLog: () -> Unit,
    onSimulateCarConnect: () -> Unit,
    onSimulateCarDisconnect: () -> Unit,
    onSelectUpdateChannel: (UpdateChannel) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AvailableUpdate) -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onOpenReleaseUrl: (String) -> Unit,
    onClearLogs: () -> Unit,
    onConsumeStatusMessage: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeStatusMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Car DND Fixer")
                        if (uiState.debugToolsEnabled) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ) {
                                Text(
                                    text = "DEBUG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "status_card") {
                StatusOverviewCard(
                    uiState = uiState,
                    onToggleAutomation = onToggleAutomation,
                )
            }

            item(key = "setup_card") {
                SetupChecklistCard(
                    systemState = uiState.systemState,
                    onOpenDndSettings = onOpenDndSettings,
                    onRequestBluetoothPermission = onRequestBluetoothPermission,
                    onOpenBatterySettings = onOpenBatterySettings,
                )
            }

            item(key = "car_devices_card") {
                CarDevicesCard(
                    devices = uiState.systemState.associatedDevices,
                    companionSupported = uiState.systemState.companionSupported,
                    bluetoothGranted = uiState.systemState.bluetoothPermissionGranted,
                    isPairingInProgress = uiState.isPairingInProgress,
                    onPairCarDevice = onPairCarDevice,
                    onRemoveCarDevice = onRemoveCarDevice,
                )
            }

            item(key = "cycle_settings_card") {
                CycleSettingsCard(
                    settings = uiState.settings,
                    dndAccessGranted = uiState.systemState.dndAccessGranted,
                    isCycling = uiState.runtimeStatus.cycleStatus != CycleStatus.Idle,
                    onDisconnectDebounceChanged = onDisconnectDebounceChanged,
                    onReEnableDelayMsChanged = onReEnableDelayMsChanged,
                    onReEnableOnlyIfWasOnChanged = onReEnableOnlyIfWasOnChanged,
                    onRunManualTest = onRunManualTest,
                )
            }

            item(key = "diagnostics_card") {
                DiagnosticsCard(
                    loggingEnabled = uiState.settings.diagnosticLoggingEnabled,
                    summary = uiState.diagnosticSummary,
                    debugToolsEnabled = uiState.debugToolsEnabled,
                    appVersionLabel = uiState.appVersionLabel,
                    onToggleLogging = onToggleDiagnosticLogging,
                    onShareLog = onShareDiagnosticLog,
                    onCopyLog = onCopyDiagnosticLog,
                    onClearLog = onClearDiagnosticLog,
                    onSimulateCarConnect = onSimulateCarConnect,
                    onSimulateCarDisconnect = onSimulateCarDisconnect,
                )
            }

            item(key = "updates_card") {
                UpdatesCard(
                    selectedChannel = uiState.settings.updateChannel,
                    appVersionLabel = uiState.appVersionLabel,
                    updateState = uiState.updateState,
                    onSelectChannel = onSelectUpdateChannel,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onCancelDownload = onCancelUpdateDownload,
                    onInstallUpdate = onInstallUpdate,
                    onOpenReleaseUrl = onOpenReleaseUrl,
                )
            }

            item(key = "activity_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.eventLogs.isNotEmpty()) {
                        TextButton(onClick = onClearLogs) {
                            Text("Clear")
                        }
                    }
                }
            }

            if (uiState.eventLogs.isEmpty()) {
                item(key = "empty_log") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Text(
                            text = "No events yet. Pair your car Bluetooth or tap \"Test DND Cycle Now\" to verify it works on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(
                    items = uiState.eventLogs,
                    key = { "${it.timestampMillis}_${it.title}" },
                ) { entry ->
                    ActivityLogRow(entry = entry)
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatusOverviewCard(
    uiState: MainUiState,
    onToggleAutomation: (Boolean) -> Unit,
) {
    val activeAndReady = uiState.settings.automationEnabled && uiState.isReadyForAutomation
    val containerColor = when {
        activeAndReady -> MaterialTheme.colorScheme.primaryContainer
        !uiState.settings.automationEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        activeAndReady -> MaterialTheme.colorScheme.onPrimaryContainer
        !uiState.settings.automationEnabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_directions_car),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                !uiState.settings.automationEnabled -> "Automation paused"
                                uiState.isReadyForAutomation -> "Watching for car disconnect"
                                else -> "Setup required"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = uiState.runtimeStatus.connectedDeviceName?.let {
                                "Connected to $it"
                            } ?: "No car currently connected",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = uiState.settings.automationEnabled,
                    onCheckedChange = onToggleAutomation,
                    modifier = Modifier.semantics {
                        contentDescription = "Enable automatic Do Not Disturb cycling on car disconnect"
                    },
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_do_not_disturb_on),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Current DND: ${DndFilterLabels.describe(uiState.runtimeStatus.lastDndFilter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            when (val cycle = uiState.runtimeStatus.cycleStatus) {
                CycleStatus.Idle -> Unit
                is CycleStatus.WaitingDebounce -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${cycle.deviceName} disconnected — waiting ${cycle.seconds}s before cycling DND…",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                is CycleStatus.Cycling -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = cycle.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupChecklistCard(
    systemState: SystemPermissionsState,
    onOpenDndSettings: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Permissions & Background Reliability",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            ChecklistRow(
                title = "Do Not Disturb access",
                subtitle = "Required to disable and re-enable DND automatically.",
                granted = systemState.dndAccessGranted,
                actionLabel = "Grant",
                onAction = onOpenDndSettings,
            )

            HorizontalDivider()

            ChecklistRow(
                title = "Bluetooth permission",
                subtitle = "Required to pair and observe your car's Bluetooth head unit.",
                granted = systemState.bluetoothPermissionGranted,
                actionLabel = "Allow",
                onAction = onRequestBluetoothPermission,
            )

            HorizontalDivider()

            ChecklistRow(
                title = "Battery optimization",
                subtitle = "Recommended: allow unrestricted background activity so disconnect wake-ups are not delayed.",
                granted = systemState.batteryOptimizationIgnored,
                actionLabel = "Settings",
                onAction = onOpenBatterySettings,
            )
        }
    }
}

@Composable
private fun ChecklistRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(
                if (granted) R.drawable.ic_check_circle else R.drawable.ic_warning,
            ),
            contentDescription = if (granted) "Granted" else "Action needed",
            tint = if (granted) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun CarDevicesCard(
    devices: List<AssociatedCarDevice>,
    companionSupported: Boolean,
    bluetoothGranted: Boolean,
    isPairingInProgress: Boolean,
    onPairCarDevice: () -> Unit,
    onRemoveCarDevice: (AssociatedCarDevice) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bluetooth),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Watched Car Bluetooth",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (devices.isEmpty()) {
                Text(
                    text = "Associate your car's Bluetooth head unit via Companion Device Manager. Android will wake this app automatically when your car connects or disconnects—including Android Auto sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                devices.forEachIndexed { index, device ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            device.macAddress?.let { mac ->
                                Text(
                                    text = mac,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { onRemoveCarDevice(device) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = "Remove ${device.displayName}",
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = onPairCarDevice,
                enabled = companionSupported && bluetoothGranted && !isPairingInProgress,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            ) {
                if (isPairingInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching for car Bluetooth…")
                } else {
                    Text(if (devices.isEmpty()) "Pair Car Bluetooth" else "Pair Another Car")
                }
            }
        }
    }
}

@Composable
private fun CycleSettingsCard(
    settings: AppSettings,
    dndAccessGranted: Boolean,
    isCycling: Boolean,
    onDisconnectDebounceChanged: (Int) -> Unit,
    onReEnableDelayMsChanged: (Long) -> Unit,
    onReEnableOnlyIfWasOnChanged: (Boolean) -> Unit,
    onRunManualTest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "DND Cycle Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Column {
                val delaySeconds = settings.reEnableDelayMs / 1000f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Re-enable delay",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f s", delaySeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "How long to keep Do Not Disturb OFF before turning it back ON.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = delaySeconds,
                    onValueChange = { raw ->
                        val snappedHalfSeconds = (raw * 2f).roundToInt() * 500L
                        onReEnableDelayMsChanged(snappedHalfSeconds)
                    },
                    valueRange = 0.5f..10f,
                    steps = 18,
                )
            }

            HorizontalDivider()

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Wait after disconnect",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${settings.disconnectDebounceSeconds} s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "Debounce delay after Bluetooth disconnects so brief dropouts or Android Auto teardown finish first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.disconnectDebounceSeconds.toFloat(),
                    onValueChange = { onDisconnectDebounceChanged(it.roundToInt()) },
                    valueRange = AppSettings.MIN_DISCONNECT_DEBOUNCE_SECONDS.toFloat()..
                        AppSettings.MAX_DISCONNECT_DEBOUNCE_SECONDS.toFloat(),
                    steps = AppSettings.MAX_DISCONNECT_DEBOUNCE_SECONDS -
                        AppSettings.MIN_DISCONNECT_DEBOUNCE_SECONDS - 1,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Only re-enable if DND was ON",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Leave DND off when disconnecting if it wasn't active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.reEnableOnlyIfWasOn,
                    onCheckedChange = onReEnableOnlyIfWasOnChanged,
                    modifier = Modifier.semantics {
                        contentDescription = "Only re-enable Do Not Disturb if it was already on"
                    },
                )
            }

            Button(
                onClick = onRunManualTest,
                enabled = dndAccessGranted && !isCycling,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isCycling) "Cycling DND…" else "Test DND Cycle Now")
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    loggingEnabled: Boolean,
    summary: DiagnosticLogSummary,
    debugToolsEnabled: Boolean,
    appVersionLabel: String,
    onToggleLogging: (Boolean) -> Unit,
    onShareLog: () -> Unit,
    onCopyLog: () -> Unit,
    onClearLog: () -> Unit,
    onSimulateCarConnect: () -> Unit,
    onSimulateCarDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bug_report),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Diagnostics & Log Collector",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "v$appVersionLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Collect diagnostic logs",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (summary.lineCount > 0) {
                            "${summary.lineCount} entries recorded (${formatBytes(summary.sizeBytes)}). MAC addresses are redacted on export."
                        } else {
                            "Records service lifecycle and DND state transitions locally for troubleshooting. MAC addresses are redacted on export."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = loggingEnabled,
                    onCheckedChange = onToggleLogging,
                    modifier = Modifier.semantics {
                        contentDescription = "Collect diagnostic logs"
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onShareLog,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = onCopyLog,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
                if (summary.lineCount > 0) {
                    TextButton(onClick = onClearLog) {
                        Text("Clear")
                    }
                }
            }

            if (debugToolsEnabled) {
                HorizontalDivider()
                Text(
                    text = "Debug Simulator",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Simulate CompanionDeviceService events without connecting to a car.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onSimulateCarConnect,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Sim Connect")
                    }
                    FilledTonalButton(
                        onClick = onSimulateCarDisconnect,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Sim Disconnect")
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesCard(
    selectedChannel: UpdateChannel,
    appVersionLabel: String,
    updateState: UpdateUiState,
    onSelectChannel: (UpdateChannel) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AvailableUpdate) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onOpenReleaseUrl: (String) -> Unit,
) {
    val isBusy = updateState is UpdateUiState.Checking || updateState is UpdateUiState.Downloading

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_system_update),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Updates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "Installed v$appVersionLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Release channel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val channels = UpdateChannel.entries
                    channels.forEachIndexed { index, channel ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = channels.size,
                            ),
                            enabled = updateState !is UpdateUiState.Downloading,
                            selected = selectedChannel == channel,
                            onClick = { onSelectChannel(channel) },
                            label = {
                                Text(
                                    when (channel) {
                                        UpdateChannel.STABLE -> "Stable"
                                        UpdateChannel.BETA -> "Beta"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    text = when (selectedChannel) {
                        UpdateChannel.STABLE ->
                            "Tracks official stable GitHub releases only."
                        UpdateChannel.BETA ->
                            "Includes GitHub pre-releases (beta/RC) and newer stable releases."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = onCheckForUpdates,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (updateState is UpdateUiState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking GitHub...")
                } else {
                    Text("Check for updates")
                }
            }

            when (updateState) {
                UpdateUiState.Idle,
                UpdateUiState.Checking,
                -> Unit

                is UpdateUiState.UpToDate -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            val channelLabel = if (updateState.checkedChannel == UpdateChannel.BETA) {
                                "Beta"
                            } else {
                                "Stable"
                            }
                            val latestSuffix = updateState.latestTagName?.let { " (latest: $it)" }.orEmpty()
                            Text(
                                text = "You're up to date on $channelLabel$latestSuffix.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                is UpdateUiState.Available -> {
                    UpdateAvailablePanel(
                        update = updateState.update,
                        onDownload = { onDownloadUpdate(updateState.update) },
                        onOpenReleaseUrl = { onOpenReleaseUrl(updateState.update.htmlUrl) },
                    )
                }

                is UpdateUiState.Downloading -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Downloading ${updateState.update.tagName}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                val sizeLabel = if (updateState.totalBytes > 0L) {
                                    "${formatBytes(updateState.downloadedBytes)} / ${formatBytes(updateState.totalBytes)}"
                                } else {
                                    formatBytes(updateState.downloadedBytes)
                                }
                                Text(
                                    text = sizeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val progress = updateState.progress
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = onCancelDownload) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                is UpdateUiState.ReadyToInstall -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check_circle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = buildString {
                                        append("Ready to install ${updateState.update.tagName}")
                                        if (updateState.verifiedSha256) {
                                            append(" (SHA-256 verified)")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { onInstallUpdate(updateState.apkFile) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Install update")
                                }
                                TextButton(onClick = { onOpenReleaseUrl(updateState.update.htmlUrl) }) {
                                    Text("GitHub")
                                }
                            }
                        }
                    }
                }

                is UpdateUiState.Error -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_warning),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailablePanel(
    update: AvailableUpdate,
    onDownload: () -> Unit,
    onOpenReleaseUrl: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Update available: ${update.tagName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (update.isPrerelease) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (update.isPrerelease) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ) {
                    Text(
                        text = if (update.isPrerelease) "BETA" else "STABLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (update.apkAsset.sizeBytes > 0L) {
                Text(
                    text = "${update.apkAsset.name} • ${formatBytes(update.apkAsset.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (update.releaseNotes.isNotBlank()) {
                Text(
                    text = update.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download APK")
                }
                TextButton(onClick = onOpenReleaseUrl) {
                    Text("View on GitHub")
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun ActivityLogRow(entry: EventLogEntry) {
    val timeText = remember(entry.timestampMillis) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
        Instant.ofEpochMilli(entry.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    val containerColor = when (entry.type) {
        EventType.SUCCESS -> MaterialTheme.colorScheme.surfaceContainerLow
        EventType.WARNING -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        EventType.INFO -> MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Car DND Fixer Dashboard", showSystemUi = true)
@Composable
private fun MainScreenPreview() {
    CarDndTheme(dynamicColor = false) {
        MainScreen(
            uiState = MainUiState(
                settings = AppSettings(
                    automationEnabled = true,
                    disconnectDebounceSeconds = 3,
                    reEnableDelayMs = 1500L,
                    reEnableOnlyIfWasOn = true,
                    diagnosticLoggingEnabled = false,
                ),
                runtimeStatus = CarRuntimeStatus(
                    connectedDeviceName = "Car Bluetooth",
                    cycleStatus = CycleStatus.Idle,
                    lastDndFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                ),
                systemState = SystemPermissionsState(
                    dndAccessGranted = true,
                    bluetoothPermissionGranted = true,
                    batteryOptimizationIgnored = true,
                    associatedDevices = listOf(
                        AssociatedCarDevice(
                            id = 1,
                            displayName = "Car Bluetooth",
                            macAddress = "AA:BB:CC:11:22:33",
                        ),
                    ),
                ),
                diagnosticSummary = DiagnosticLogSummary(lineCount = 12, sizeBytes = 1480L),
                debugToolsEnabled = false,
                appVersionLabel = "0.2.0-beta.1",
                eventLogs = listOf(
                    EventLogEntry(
                        timestampMillis = 1_764_000_000_000L,
                        title = "Car disconnected (Car Bluetooth)",
                        detail = "Disabled DND, waited 1.5s, re-enabled On (Priority only).",
                        type = EventType.SUCCESS,
                    ),
                ),
            ),
            onToggleAutomation = {},
            onOpenDndSettings = {},
            onRequestBluetoothPermission = {},
            onOpenBatterySettings = {},
            onPairCarDevice = {},
            onRemoveCarDevice = {},
            onDisconnectDebounceChanged = {},
            onReEnableDelayMsChanged = {},
            onReEnableOnlyIfWasOnChanged = {},
            onRunManualTest = {},
            onToggleDiagnosticLogging = {},
            onShareDiagnosticLog = {},
            onCopyDiagnosticLog = {},
            onClearDiagnosticLog = {},
            onSimulateCarConnect = {},
            onSimulateCarDisconnect = {},
            onSelectUpdateChannel = {},
            onCheckForUpdates = {},
            onDownloadUpdate = {},
            onCancelUpdateDownload = {},
            onInstallUpdate = {},
            onOpenReleaseUrl = {},
            onClearLogs = {},
            onConsumeStatusMessage = {},
        )
    }
}
