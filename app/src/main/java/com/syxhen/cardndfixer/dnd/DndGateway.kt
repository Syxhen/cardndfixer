package com.syxhen.cardndfixer.dnd

import android.app.NotificationManager

interface DndGateway {
    fun isPolicyAccessGranted(): Boolean
    fun getCurrentInterruptionFilter(): Int
    fun setInterruptionFilter(filter: Int): Boolean
}

object DndFilterLabels {
    fun describe(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "Off (All notifications)"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "On (Priority only)"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "On (Total silence)"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "On (Alarms only)"
        else -> "Unknown ($filter)"
    }

    fun isDndActive(filter: Int): Boolean =
        filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
}
