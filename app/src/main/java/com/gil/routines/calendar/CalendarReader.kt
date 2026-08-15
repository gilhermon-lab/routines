package com.gil.routines.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.gil.routines.data.CalendarTrigger
import com.gil.routines.data.normalizeTitle

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val type: String,
    val visible: Boolean,
    val syncing: Boolean
)

data class BusyEvent(val id: Long, val title: String, val begin: Long, val end: Long) {
    /** מזהה יציב למופע בודד, כדי לאפשר החלטה ידנית לאירוע אחד */
    val key: String get() = "$id:$begin"
}

/**
 * קורא אירועים מספק היומן של אנדרואיד.
 *
 * כל חשבון שמסונכרן למכשיר — Outlook, Exchange, Google — נגיש דרך אותו ספק,
 * ולכן אין צורך ב-API של מיקרוסופט ולא בחיבור רשת.
 *
 * נספרים אירועים שאינם יום שלם, שלא בוטלו, ושלא נדחו על ידי המשתמש.
 * הסינון האמיתי נעשה במילות המפתח ובאישור הידני, ולא בסטטוס הזמינות.
 */
object CalendarReader {

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun calendars(ctx: Context): List<CalendarInfo> {
        if (!hasPermission(ctx)) return emptyList()
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS
        )
        // בלי סינון על VISIBLE: יומנים ש-Outlook מייצא נוצרים לפעמים כלא־גלויים
        // באפליקציית היומן, ואז הם נעלמים מהרשימה למרות שהאירועים קיימים.
        return runCatching {
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, cols, null, null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC"
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            CalendarInfo(
                                id = c.getLong(0),
                                name = c.getString(1).orEmpty(),
                                account = c.getString(2).orEmpty(),
                                type = c.getString(3).orEmpty(),
                                visible = c.getInt(4) == 1,
                                syncing = c.getInt(5) == 1
                            )
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /** כל האירועים הרלוונטיים בטווח נתון, ממוינים לפי זמן התחלה */
    fun busyEvents(ctx: Context, calendarIds: Set<Long>, from: Long, to: Long): List<BusyEvent> {
        if (!hasPermission(ctx)) return emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, from)
        ContentUris.appendId(uri, to)

        val cols = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_ID
        )

        // אין סינון לפי "עסוק": מילות המפתח והאישור הידני מסננים טוב יותר,
        // וסימון "מותנה" על ישיבה אמיתית היה גורם לה ליפול.
        val where = buildString {
            append("${CalendarContract.Instances.ALL_DAY}=0")
            append(" AND ${CalendarContract.Instances.STATUS}!=${CalendarContract.Events.STATUS_CANCELED}")
            append(" AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS}!=${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}")
        }

        return runCatching {
            ctx.contentResolver.query(uri.build(), cols, where, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val calId = c.getLong(3)
                            if (calendarIds.isEmpty() || calendarIds.contains(calId)) {
                                add(
                                    BusyEvent(
                                        id = c.getLong(4),
                                        title = c.getString(0).orEmpty().ifBlank { "ללא כותרת" },
                                        begin = c.getLong(1),
                                        end = c.getLong(2)
                                    )
                                )
                            }
                        }
                    }
                }
        }.getOrNull().orEmpty()
    }

    /**
     * האם האירוע מפעיל את המצב.
     * סדר ההכרעה: החלטה ידנית קודמת לכל, ורק אחריה כלל מילות המפתח.
     */
    fun matches(trigger: CalendarTrigger, e: BusyEvent): Boolean {
        if (trigger.forcedOff.contains(e.key)) return false
        if (trigger.forcedOn.contains(e.key)) return true
        if (!trigger.requireKeyword || trigger.keywords.isEmpty()) return true
        val title = normalizeTitle(e.title)
        return trigger.keywords.any { w ->
            val k = normalizeTitle(w)
            k.isNotBlank() && title.contains(k)
        }
    }

    /** כל האירועים בטווח שעונים לכללי הטריגר */
    fun matching(ctx: Context, t: CalendarTrigger, from: Long, to: Long): List<BusyEvent> =
        busyEvents(ctx, t.calendarIds, from, to).filter { matches(t, it) }

    /** האירוע המפעיל שמתרחש ברגע זה, אם יש */
    fun activeNow(ctx: Context, t: CalendarTrigger, now: Long = System.currentTimeMillis()): BusyEvent? =
        matching(ctx, t, now - 12 * 3_600_000L, now + 60_000L)
            .firstOrNull { now >= it.begin && now < it.end }

    /** האירוע המפעיל הבא, לתצוגה */
    fun nextMatching(ctx: Context, t: CalendarTrigger, now: Long = System.currentTimeMillis()): BusyEvent? =
        matching(ctx, t, now, now + 7 * 24 * 3_600_000L).firstOrNull { it.begin > now }

    /** כל האירועים הקרובים — גם מי שלא עונה לכללים, לצורך בחירה ידנית */
    fun upcomingAll(ctx: Context, t: CalendarTrigger, now: Long = System.currentTimeMillis()): List<BusyEvent> =
        busyEvents(ctx, t.calendarIds, now, now + 7 * 24 * 3_600_000L).take(40)

    /** גבולות זמן שדורשים התעוררות ב-24 השעות הקרובות */
    fun upcomingBoundaries(ctx: Context, t: CalendarTrigger, now: Long = System.currentTimeMillis()): List<Long> {
        val events = matching(ctx, t, now, now + 24 * 3_600_000L)
        return (events.map { it.begin } + events.map { it.end })
            .filter { it > now }
            .distinct()
            .sorted()
            .take(20)
    }
}
