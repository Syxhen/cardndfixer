package com.syxhen.cardndfixer.dnd

import android.app.NotificationManager
import android.content.Context

class AndroidDndGateway(
    context: Context,
) : DndGateway {

    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    override fun isPolicyAccessGranted(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted

    override fun getCurrentInterruptionFilter(): Int =
        notificationManager.currentInterruptionFilter

    override fun setInterruptionFilter(filter: Int): Boolean {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return false
        }
        return runCatching {
            notificationManager.setInterruptionFilter(filter)
            true
        }.getOrDefault(false)
    }
}
