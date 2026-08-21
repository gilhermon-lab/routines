package com.gil.routines.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * ניהול היומנים שבמכשיר.
 *
 * אפליקציות מסוימות מזריקות מאות יומנים לספק היומן, וזה הופך את הבחירה
 * לבלתי אפשרית. כאן אפשר להסתיר אותם — פעולה הפיכה — או למחוק לגמרי.
 */
object CalendarAdmin {

    fun canWrite(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** קיבוץ לפי חשבון — כך רואים מיד מי אחראי ל-600 היומנים */
    fun byAccount(ctx: Context): Map<String, List<CalendarInfo>> =
        CalendarReader.calendars(ctx).groupBy { it.account.ifBlank { "ללא חשבון" } }

    /**
     * הסתרה מהתצוגה וכיבוי הסנכרון. הפיכה לחלוטין —
     * זו הדרך הנכונה "למחוק" יומן שאולי תרצה בחזרה.
     */
    fun setHidden(ctx: Context, ids: Set<Long>, hidden: Boolean): Int {
        if (!canWrite(ctx)) return 0
        var ok = 0
        ids.forEach { id ->
            runCatching {
                val values = ContentValues().apply {
                    put(CalendarContract.Calendars.VISIBLE, if (hidden) 0 else 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, if (hidden) 0 else 1)
                }
                val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)
                if (ctx.contentResolver.update(uri, values, null, null) > 0) ok++
            }
        }
        return ok
    }

    /**
     * מחיקה סופית.
     *
     * ספק היומן של אנדרואיד מרשה למחוק יומנים רק למי שמזוהה כמתאם סנכרון,
     * ולכן מוסיפים את CALLER_IS_SYNCADAPTER יחד עם פרטי החשבון.
     * בלי זה המחיקה נכשלת בשקט וזה נראה כאילו כלום לא קרה.
     */
    fun delete(ctx: Context, calendars: List<CalendarInfo>): Pair<Int, Int> {
        if (!canWrite(ctx)) return 0 to calendars.size
        var ok = 0
        var failed = 0

        calendars.forEach { cal ->
            val asSync = runCatching {
                ctx.contentResolver.delete(
                    syncAdapterUri(cal.account, cal.type),
                    "${CalendarContract.Calendars._ID}=?",
                    arrayOf(cal.id.toString())
                )
            }.getOrDefault(0)

            if (asSync > 0) { ok++; return@forEach }

            // נפילה אחורה לניסיון רגיל, ליומנים מקומיים שאינם שייכים לחשבון
            val plain = runCatching {
                ctx.contentResolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, cal.id),
                    null, null
                )
            }.getOrDefault(0)

            if (plain > 0) ok++ else failed++
        }
        return ok to failed
    }

    private fun syncAdapterUri(account: String, type: String): Uri =
        CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, type)
            .build()

    /** כמה יומנים נשארו לחשבון — לאימות אחרי מחיקה */
    fun countFor(ctx: Context, account: String): Int =
        CalendarReader.calendars(ctx).count { it.account == account }

    /**
     * יומנים שנראים כפולים: אותו שם ואותו חשבון.
     * שם שונה באותו חשבון הוא תקין ולא נחשב כפילות.
     */
    fun duplicates(list: List<CalendarInfo>): List<CalendarInfo> {
        val seen = HashSet<String>()
        return list.filter { !seen.add(it.account + "|" + it.name) }
    }
}
