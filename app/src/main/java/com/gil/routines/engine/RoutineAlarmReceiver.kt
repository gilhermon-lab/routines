package com.gil.routines.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** נקרא בהתחלה ובסיום של כל מצב: מפעיל את הפעולות ומתזמן את המופע הבא */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ModeApplier.applyCurrentState(ctx)
        RoutineScheduler.rescheduleAll(ctx)
    }
}
