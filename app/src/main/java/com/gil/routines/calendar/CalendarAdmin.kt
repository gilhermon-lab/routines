package com.gil.routines.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
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

    /** מחיקה סופית. אם אפליקציית המקור עדיין מסנכרנת, היא עלולה ליצור אותם מחדש. */
    fun delete(ctx: Context, ids: Set<Long>): Pair<Int, Int> {
        if (!canWrite(ctx)) return 0 to ids.size
        var ok = 0
        var failed = 0
        ids.forEach { id ->
            val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)
            runCatching { ctx.contentResolver.delete(uri, null, null) }
                .onSuccess { if (it > 0) ok++ else failed++ }
                .onFailure { failed++ }
        }
        return ok to failed
    }

    /**
     * יומנים שנראים כפולים: אותו שם ואותו חשבון.
     * שם שונה באותו חשבון הוא תקין ולא נחשב כפילות.
     */
    fun duplicates(list: List<CalendarInfo>): List<CalendarInfo> {
        val seen = HashSet<String>()
        return list.filter { !seen.add(it.account + "|" + it.name) }
    }
}
