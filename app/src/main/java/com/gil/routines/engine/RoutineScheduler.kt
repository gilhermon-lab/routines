package com.gil.routines.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gil.routines.calendar.CalendarReader
import com.gil.routines.data.Mode
import com.gil.routines.data.ModeStore
import java.time.ZonedDateTime

/**
 * מתזמן התעוררות בכל התחלה וסיום של מצב.
 *
 * חשוב: הזמן מחושב כ-ZonedDateTime מלא ולא כשעה ביום, אחרת מעבר שעון קיץ
 * מזיז את כל השגרות בשעה.
 */
object RoutineScheduler {

    fun rescheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()

        ModeStore.load(ctx).filter { it.enabled }.forEach { mode ->
            schedule(ctx, am, mode, mode.start, now, isStart = true)
            schedule(ctx, am, mode, mode.end, now, isStart = false)

            if (mode.calendar.enabled) scheduleCalendar(ctx, am, mode)
        }
    }

    /**
     * אירועי יומן אינם חוזרים בשעה קבועה, ולכן מתזמנים כל גבול בנפרד
     * מתוך 24 השעות הקרובות. כל התעוררות מתזמנת מחדש את הבאות.
     */
    private fun scheduleCalendar(ctx: Context, am: AlarmManager, mode: Mode) {
        CalendarReader.upcomingBoundaries(ctx, mode.calendar)
            .forEachIndexed { i, millis ->
                val intent = Intent(ctx, RoutineAlarmReceiver::class.java)
                    .putExtra(EXTRA_MODE_ID, mode.id)
                val pi = PendingIntent.getBroadcast(
                    ctx, (mode.id + ":cal:" + i).hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
                if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
    }

    private fun schedule(
        ctx: Context, am: AlarmManager, mode: Mode,
        minuteOfDay: Int, now: ZonedDateTime, isStart: Boolean
    ) {
        val next = nextOccurrence(minuteOfDay, now)
        val code = (mode.id + if (isStart) ":start" else ":end").hashCode()

        val intent = Intent(ctx, RoutineAlarmReceiver::class.java).apply {
            putExtra(EXTRA_MODE_ID, mode.id)
            putExtra(EXTRA_IS_START, isStart)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val millis = next.toInstant().toEpochMilli()

        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            // בלי הרשאת התראה מדויקת: דיוק של דקות ספורות במקום שנייה
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun nextOccurrence(minuteOfDay: Int, now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
            .atStartOfDay(now.zone)
            .plusMinutes(minuteOfDay.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    const val EXTRA_MODE_ID = "mode_id"
    const val EXTRA_IS_START = "is_start"
}
