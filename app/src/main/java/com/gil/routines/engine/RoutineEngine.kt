package com.gil.routines.engine

import android.content.Context
import com.gil.routines.calendar.CalendarReader
import com.gil.routines.data.Mode
import com.gil.routines.data.ModeStore
import java.time.ZonedDateTime

/** מחשב אילו מצבים פעילים ברגע נתון */
object RoutineEngine {

    fun activeModes(ctx: Context, at: ZonedDateTime = ZonedDateTime.now()): List<Mode> {
        val minute = at.hour * 60 + at.minute
        val day = at.dayOfWeek.value % 7 + 1     // ISO (שני=1) → Calendar (ראשון=1)
        val nowMillis = at.toInstant().toEpochMilli()

        return ModeStore.load(ctx).filter { m ->
            m.manualOverride?.let { return@filter it }      // עקיפה ידנית גוברת על הכל
            if (!m.enabled) return@filter false
            if (m.isActiveAt(minute, day)) return@filter true

            // אירוע יומן שמסומן "בעסוק" מדליק את המצב גם מחוץ לשעות
            m.calendar.enabled && CalendarReader.activeNow(ctx, m.calendar, nowMillis) != null
        }
    }
}
