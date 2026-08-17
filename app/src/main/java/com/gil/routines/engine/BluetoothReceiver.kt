package com.gil.routines.engine

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gil.routines.data.ModeStore
import com.gil.routines.widget.ModeWidget

/**
 * חיבור וניתוק של מכשיר בלוטות' — למשל מערכת השמע של הרכב.
 *
 * זה הטריגר הנכון לנהיגה: אין לה שעות קבועות, אבל יש לה רגע התחלה
 * ורגע סיום ברורים לגמרי.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }

        val address = runCatching {
            @Suppress("DEPRECATION")
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            device?.address
        }.getOrNull() ?: return

        var changed = false
        ModeStore.load(ctx)
            .filter { it.enabled && it.bluetooth.enabled && it.bluetooth.addresses.contains(address) }
            .forEach { m ->
                // חיבור מדליק, ניתוק מחזיר ללוח הזמנים במקום לנעול על כבוי
                ModeStore.update(ctx, m.id) {
                    it.copy(manualOverride = if (connected) true else null)
                }
                changed = true
            }

        if (changed) {
            ModeApplier.applyCurrentState(ctx)
            RoutineScheduler.rescheduleAll(ctx)
            ModeWidget.refreshAll(ctx)
        }
    }
}
