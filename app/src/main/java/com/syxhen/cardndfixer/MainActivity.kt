package com.syxhen.cardndfixer

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.syxhen.cardndfixer.ui.MainScreen
import com.syxhen.cardndfixer.ui.MainViewModel
import com.syxhen.cardndfixer.ui.theme.CarDndTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshSystemState()
        if (!granted) {
            viewModel.onPairingError("Bluetooth permission is needed to associate your car.")
        }
    }

    private val companionChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            viewModel.onPairingCancelled()
            return@registerForActivityResult
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            val device: BluetoothDevice? =
                result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            val mac = device?.address
            if (!mac.isNullOrBlank()) {
                val hasBt = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
                val name = if (hasBt) {
                    try {
                        device.name?.takeIf { it.isNotBlank() } ?: mac
                    } catch (_: SecurityException) {
                        mac
                    }
                } else {
                    mac
                }
                viewModel.onLegacyAssociationCreated(name, mac)
            } else {
                viewModel.refreshSystemState()
            }
        } else {
            viewModel.refreshSystemState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CarDndTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshSystemState()
                }

                MainScreen(
                    uiState = uiState,
                    onToggleAutomation = viewModel::setAutomationEnabled,
                    onOpenDndSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                    onRequestBluetoothPermission = {
                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    },
                    onOpenBatterySettings = {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                    onPairCarDevice = ::startCarAssociationFlow,
                    onRemoveCarDevice = viewModel::removeAssociatedDevice,
                    onDisconnectDebounceChanged = viewModel::setDisconnectDebounceSeconds,
                    onReEnableDelayMsChanged = viewModel::setReEnableDelayMs,
                    onReEnableOnlyIfWasOnChanged = viewModel::setReEnableOnlyIfWasOn,
                    onRunManualTest = viewModel::runManualTestCycle,
                    onToggleDiagnosticLogging = viewModel::setDiagnosticLoggingEnabled,
                    onShareDiagnosticLog = ::shareDiagnosticLog,
                    onCopyDiagnosticLog = ::copyDiagnosticLog,
                    onClearDiagnosticLog = viewModel::clearDiagnosticLogs,
                    onSimulateCarConnect = viewModel::simulateCarConnect,
                    onSimulateCarDisconnect = viewModel::simulateCarDisconnect,
                    onClearLogs = viewModel::clearLogs,
                    onConsumeStatusMessage = viewModel::consumeStatusMessage,
                )
            }
        }
    }

    private fun shareDiagnosticLog() {
        runCatching {
            val file = viewModel.exportDiagnosticReportFile()
            val uri = FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "cardndfixer diagnostic report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share diagnostic report"))
        }.onFailure { error ->
            viewModel.showStatusMessage(
                "Could not share diagnostic log: ${error.localizedMessage ?: "unknown error"}",
            )
        }
    }

    private fun copyDiagnosticLog() {
        val report = viewModel.buildDiagnosticReport()
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("cardndfixer diagnostic report", report))
        viewModel.showStatusMessage("Diagnostic report copied to clipboard.")
    }

    private fun startCarAssociationFlow() {
        val app = application as CarDndApplication
        viewModel.onPairingStarted()

        val result = app.companionManager.requestCarAssociation(
            executor = mainExecutor,
            callback = object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    launchChooser(intentSender)
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        launchChooser(chooserLauncher)
                    }
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    viewModel.onAssociationCreated(associationInfo)
                }

                override fun onFailure(error: CharSequence?) {
                    viewModel.onPairingError(
                        error?.toString()?.takeIf { it.isNotBlank() }
                            ?: "No car Bluetooth device was selected.",
                    )
                }
            },
        )

        result.onFailure { error ->
            viewModel.onPairingError(
                "Could not start car pairing: ${error.localizedMessage ?: "unknown error"}",
            )
        }
    }

    private fun launchChooser(intentSender: IntentSender) {
        runCatching {
            companionChooserLauncher.launch(
                IntentSenderRequest.Builder(intentSender).build(),
            )
        }.onFailure { error ->
            viewModel.onPairingError(
                "Could not open device chooser: ${error.localizedMessage ?: "unknown error"}",
            )
        }
    }
}
