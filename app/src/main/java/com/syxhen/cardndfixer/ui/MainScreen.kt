package com.syxhen.cardndfixer.ui

import android.app.NotificationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.syxhen.cardndfixer.service.AssociatedCarDevice
import com.syxhen.cardndfixer.ui.theme.CarDndTheme
import com.syxhen.cardndfixer.updater.AvailableUpdate
import com.syxhen.cardndfixer.updater.UpdateUiState
import java.io.File
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val topBarTitle: String,
    val selectedIconRes: Int,
    val unselectedIconRes: Int,
) {
    HOME(
        label = "Home",
        topBarTitle = "Car DND Fixer",
        selectedIconRes = R.drawable.ic_home_filled,
        unselectedIconRes = R.drawable.ic_home_outlined,
    ),
    SETTINGS(
        label = "Settings",
        topBarTitle = "Settings",
        selectedIconRes = R.drawable.ic_settings_filled,
        unselectedIconRes = R.drawable.ic_settings_outlined,
    ),
}

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
    onOpenUrl: (String) -> Unit,
    onClearLogs: () -> Unit,
    onConsumeStatusMessage: () -> Unit,
    initialTabIndex: Int = MainTab.HOME.ordinal,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(
        initialPage = initialTabIndex.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val currentTab = tabs[pagerState.currentPage]
    val homeListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()

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
                        Text(currentTab.topBarTitle)
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
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (selected) tab.selectedIconRes else tab.unselectedIconRes,
                                ),
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            when (tabs[page]) {
                MainTab.HOME -> {
                    HomeTab(
                        uiState = uiState,
                        listState = homeListState,
                        onToggleAutomation = onToggleAutomation,
                        onOpenDndSettings = onOpenDndSettings,
                        onRequestBluetoothPermission = onRequestBluetoothPermission,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onPairCarDevice = onPairCarDevice,
                        onRemoveCarDevice = onRemoveCarDevice,
                        onDisconnectDebounceChanged = onDisconnectDebounceChanged,
                        onReEnableDelayMsChanged = onReEnableDelayMsChanged,
                        onReEnableOnlyIfWasOnChanged = onReEnableOnlyIfWasOnChanged,
                        onRunManualTest = onRunManualTest,
                        onToggleDiagnosticLogging = onToggleDiagnosticLogging,
                        onShareDiagnosticLog = onShareDiagnosticLog,
                        onCopyDiagnosticLog = onCopyDiagnosticLog,
                        onClearDiagnosticLog = onClearDiagnosticLog,
                        onSimulateCarConnect = onSimulateCarConnect,
                        onSimulateCarDisconnect = onSimulateCarDisconnect,
                        onClearLogs = onClearLogs,
                    )
                }

                MainTab.SETTINGS -> {
                    SettingsTab(
                        uiState = uiState,
                        listState = settingsListState,
                        onSelectUpdateChannel = onSelectUpdateChannel,
                        onCheckForUpdates = onCheckForUpdates,
                        onDownloadUpdate = onDownloadUpdate,
                        onCancelUpdateDownload = onCancelUpdateDownload,
                        onInstallUpdate = onInstallUpdate,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        }
    }
}

private fun samplePreviewUiState(
    updateState: UpdateUiState = UpdateUiState.Idle,
): MainUiState = MainUiState(
    settings = AppSettings(
        automationEnabled = true,
        disconnectDebounceSeconds = 3,
        reEnableDelayMs = 1500L,
        reEnableOnlyIfWasOn = true,
        diagnosticLoggingEnabled = false,
        updateChannel = UpdateChannel.STABLE,
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
    appVersionLabel = "0.3.0",
    updateState = updateState,
    eventLogs = listOf(
        EventLogEntry(
            timestampMillis = 1_764_000_000_000L,
            title = "Car disconnected (Car Bluetooth)",
            detail = "Disabled DND, waited 1.5s, re-enabled On (Priority only).",
            type = EventType.SUCCESS,
        ),
    ),
)

@Preview(name = "Home Tab", showSystemUi = true)
@Composable
private fun MainScreenHomePreview() {
    CarDndTheme(dynamicColor = false) {
        MainScreen(
            uiState = samplePreviewUiState(),
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
            onOpenUrl = {},
            onClearLogs = {},
            onConsumeStatusMessage = {},
            initialTabIndex = MainTab.HOME.ordinal,
        )
    }
}

@Preview(name = "Settings Tab", showSystemUi = true)
@Composable
private fun MainScreenSettingsPreview() {
    CarDndTheme(dynamicColor = false) {
        MainScreen(
            uiState = samplePreviewUiState(
                updateState = UpdateUiState.UpToDate(
                    checkedChannel = UpdateChannel.STABLE,
                    latestTagName = "v0.2.0",
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
            onOpenUrl = {},
            onClearLogs = {},
            onConsumeStatusMessage = {},
            initialTabIndex = MainTab.SETTINGS.ordinal,
        )
    }
}
