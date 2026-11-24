package com.syxhen.cardndfixer.service

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import com.syxhen.cardndfixer.CarDndApplication

class CarCompanionService : CompanionDeviceService() {

    private val app: CarDndApplication
        get() = application as CarDndApplication

    override fun onCreate() {
        super.onCreate()
        app.diagnosticLogCollector.record(TAG, "Service onCreate (API ${Build.VERSION.SDK_INT})")
    }

    override fun onDestroy() {
        app.diagnosticLogCollector.record(TAG, "Service onDestroy")
        super.onDestroy()
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return

        val deviceName = app.companionManager.findDeviceName(event.associationId)
        app.diagnosticLogCollector.record(
            TAG,
            "onDevicePresenceEvent: event=${event.event}, associationId=${event.associationId}, device=$deviceName",
        )

        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED,
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            -> app.dndCycleCoordinator.onCarConnected(deviceName)

            DevicePresenceEvent.EVENT_BT_DISCONNECTED,
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED,
            -> app.dndCycleCoordinator.onCarDisconnected(deviceName)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) return

        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            associationInfo.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: associationInfo.deviceMacAddress?.toString()
                ?: "Car Bluetooth"
        } else {
            "Car Bluetooth"
        }
        app.diagnosticLogCollector.record(TAG, "onDeviceAppeared(AssociationInfo): device=$deviceName")
        app.dndCycleCoordinator.onCarConnected(deviceName)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) return

        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            associationInfo.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: associationInfo.deviceMacAddress?.toString()
                ?: "Car Bluetooth"
        } else {
            "Car Bluetooth"
        }
        app.diagnosticLogCollector.record(TAG, "onDeviceDisappeared(AssociationInfo): device=$deviceName")
        app.dndCycleCoordinator.onCarDisconnected(deviceName)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return

        val deviceName = app.companionManager.findDeviceNameByAddress(address)
        app.diagnosticLogCollector.record(TAG, "onDeviceAppeared(String): address=$address, device=$deviceName")
        app.dndCycleCoordinator.onCarConnected(deviceName)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceDisappeared(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return

        val deviceName = app.companionManager.findDeviceNameByAddress(address)
        app.diagnosticLogCollector.record(TAG, "onDeviceDisappeared(String): address=$address, device=$deviceName")
        app.dndCycleCoordinator.onCarDisconnected(deviceName)
    }

    companion object {
        private const val TAG = "CarCompanionService"
    }
}
