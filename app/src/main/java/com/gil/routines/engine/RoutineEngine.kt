package com.gil.routines.engine

import android.content.Context
import com.gil.routines.data.Mode
import com.gil.routines.data.ModeStore
import java.time.ZonedDateTime

/** מחשב אילו מצבים פעילים ברגע נתון */
object RoutineEngine {

    fun activeModes(ctx: Context, at: ZonedDateTime = ZonedDateTime.now()): List<Mode> {
        val minute = at.hour * 60 + at.minute
        val day = at.dayOfWeek.value % 7 + 1     // ISO (שני=1) → Calendar (ראשון=1)
        return ModeStore.load(ctx).filter { it.isActiveAt(minute, day) }
    }
}
