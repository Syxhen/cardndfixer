package com.syxhen.cardndfixer.service

import android.Manifest
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.syxhen.cardndfixer.diagnostics.DiagnosticSink
import com.syxhen.cardndfixer.diagnostics.NoOpDiagnosticSink
import java.util.concurrent.Executor

data class AssociatedCarDevice(
    val id: Int?,
    val displayName: String,
    val macAddress: String?,
) {
    val stableKey: String
        get() = id?.let { "id:$it" } ?: "mac:${macAddress.orEmpty()}"
}

class CarCompanionManager(
    private val context: Context,
    private val diagnostics: DiagnosticSink = NoOpDiagnosticSink,
) {
    private val companionDeviceManager: CompanionDeviceManager? =
        context.getSystemService(CompanionDeviceManager::class.java)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    fun isSupported(): Boolean = companionDeviceManager != null

    fun getAssociatedDevices(): List<AssociatedCarDevice> {
        val manager = companionDeviceManager ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.myAssociations.map { it.toAssociatedCarDevice() }
        } else {
            @Suppress("DEPRECATION")
            manager.associations.map { mac ->
                AssociatedCarDevice(
                    id = null,
                    displayName = resolveBondedDeviceName(mac) ?: mac,
                    macAddress = mac,
                )
            }
        }
    }

    fun findDeviceName(associationId: Int): String {
        val manager = companionDeviceManager ?: return DEFAULT_DEVICE_NAME
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val match = manager.myAssociations.firstOrNull { it.id == associationId }
            return match?.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: match?.deviceMacAddress?.toString()
                ?: DEFAULT_DEVICE_NAME
        }
        return DEFAULT_DEVICE_NAME
    }

    fun findDeviceNameByAddress(macAddress: String): String {
        val existing = getAssociatedDevices().firstOrNull {
            it.macAddress.equals(macAddress, ignoreCase = true)
        }
        return existing?.displayName
            ?: resolveBondedDeviceName(macAddress)
            ?: macAddress.ifBlank { DEFAULT_DEVICE_NAME }
    }

    fun requestCarAssociation(
        executor: Executor,
        callback: CompanionDeviceManager.Callback,
    ): Result<Unit> {
        val manager = companionDeviceManager
            ?: return Result.failure(IllegalStateException("CompanionDeviceManager is unavailable on this device."))

        val bluetoothFilter = BluetoothDeviceFilter.Builder().build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(bluetoothFilter)
            .setSingleDevice(false)
            .build()

        diagnostics.record(TAG, "Starting CompanionDeviceManager association request (API ${Build.VERSION.SDK_INT})")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.associate(request, executor, callback)
            } else {
                @Suppress("DEPRECATION")
                manager.associate(request, callback, Handler(Looper.getMainLooper()))
            }
        }
    }

    fun startObserving(associationInfo: AssociationInfo): Result<Unit> =
        startObserving(
            associationId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                associationInfo.id
            } else {
                null
            },
            macAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                associationInfo.deviceMacAddress?.toString()
            } else {
                null
            },
        )

    fun startObserving(associationId: Int?, macAddress: String?): Result<Unit> {
        val manager = companionDeviceManager
            ?: return Result.failure(IllegalStateException("CompanionDeviceManager is unavailable."))

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && associationId != null) {
                val request = ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(associationId)
                    .build()
                manager.startObservingDevicePresence(request)
                diagnostics.record(TAG, "Started presence observation via ObservingDevicePresenceRequest (id=$associationId)")
            } else {
                val address = macAddress
                    ?: error("Associated device has no MAC address for presence observation.")
                @Suppress("DEPRECATION")
                manager.startObservingDevicePresence(address)
                diagnostics.record(TAG, "Started presence observation via legacy MAC address ($address)")
            }
        }.onFailure { err ->
            diagnostics.record(TAG, "Failed to start presence observation: ${err.message}")
        }
    }

    fun stopObserving(associationId: Int?, macAddress: String?): Result<Unit> {
        val manager = companionDeviceManager
            ?: return Result.failure(IllegalStateException("CompanionDeviceManager is unavailable."))

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && associationId != null) {
                val request = ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(associationId)
                    .build()
                manager.stopObservingDevicePresence(request)
                diagnostics.record(TAG, "Stopped presence observation (id=$associationId)")
            } else if (!macAddress.isNullOrBlank()) {
                @Suppress("DEPRECATION")
                manager.stopObservingDevicePresence(macAddress)
                diagnostics.record(TAG, "Stopped presence observation via legacy MAC ($macAddress)")
            }
        }
    }

    fun ensureObservingAll(): Int {
        val devices = getAssociatedDevices()
        var count = 0
        for (device in devices) {
            if (startObserving(device.id, device.macAddress).isSuccess) {
                count++
            }
        }
        if (devices.isNotEmpty()) {
            diagnostics.record(TAG, "Ensured presence observation for $count/${devices.size} associated device(s)")
        }
        return count
    }

    fun disassociate(device: AssociatedCarDevice): Result<Unit> {
        val manager = companionDeviceManager
            ?: return Result.failure(IllegalStateException("CompanionDeviceManager is unavailable."))

        return runCatching {
            stopObserving(device.id, device.macAddress)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && device.id != null) {
                manager.disassociate(device.id)
            } else {
                val address = device.macAddress
                    ?: error("Cannot disassociate device without MAC address on API ${Build.VERSION.SDK_INT}.")
                @Suppress("DEPRECATION")
                manager.disassociate(address)
            }
            diagnostics.record(TAG, "Disassociated device ${device.displayName}")
        }
    }

    private fun resolveBondedDeviceName(macAddress: String): String? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        return runCatching {
            bluetoothManager?.adapter?.bondedDevices
                ?.firstOrNull { it.address.equals(macAddress, ignoreCase = true) }
                ?.name
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun AssociationInfo.toAssociatedCarDevice(): AssociatedCarDevice {
        val mac = deviceMacAddress?.toString()
        val label = displayName?.toString()?.takeIf { it.isNotBlank() }
            ?: mac?.let { resolveBondedDeviceName(it) }
            ?: mac
            ?: DEFAULT_DEVICE_NAME
        return AssociatedCarDevice(
            id = id,
            displayName = label,
            macAddress = mac,
        )
    }

    companion object {
        private const val TAG = "CarCompanionManager"
        private const val DEFAULT_DEVICE_NAME = "Car Bluetooth"
    }
}
