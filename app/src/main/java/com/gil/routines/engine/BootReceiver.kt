package com.gil.routines.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** התראות לא שורדות אתחול — מתזמנים מחדש אחרי הפעלה ואחרי עדכון האפליקציה */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        RoutineScheduler.rescheduleAll(ctx)
        ModeApplier.applyCurrentState(ctx)
    }
}
