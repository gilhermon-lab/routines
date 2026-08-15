package com.gil.routines.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gil.routines.data.ModeStore
import com.gil.routines.widget.ModeWidget
import java.time.ZonedDateTime

/** נקרא בהתחלה ובסיום של כל מצב: מפעיל את הפעולות ומתזמן את המופע הבא */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        clearStaleOverrides(ctx)
        ModeApplier.applyCurrentState(ctx)
        RoutineScheduler.rescheduleAll(ctx)
        ModeWidget.refreshAll(ctx)
    }

    /**
     * "כבוי ידנית" נועד לבטל את החלון הנוכחי בלבד.
     * ברגע שהחלון נגמר מעצמו, המצב חוזר לפעול לפי הלוח.
     */
    private fun clearStaleOverrides(ctx: Context) {
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        ModeStore.load(ctx).filter { it.manualOverride == false }.forEach { m ->
            if (!m.coversMinute(minute)) {
                ModeStore.update(ctx, m.id) { it.copy(manualOverride = null) }
            }
        }
    }
}
