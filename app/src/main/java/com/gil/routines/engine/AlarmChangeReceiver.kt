package com.gil.routines.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * המשתמש שינה או ביטל את השכמה — מתזמנים מחדש,
 * אחרת נמשיך לחכות לזמן שכבר לא רלוונטי.
 */
class AlarmChangeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        RoutineScheduler.rescheduleAll(ctx)
    }
}
