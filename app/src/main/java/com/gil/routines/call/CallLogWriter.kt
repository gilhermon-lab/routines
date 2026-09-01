package com.gil.routines.call

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * רושם שיחה שנחסמה כ"שיחה שלא נענתה" ביומן של הטלפון.
 *
 * אנדרואיד רושם שיחות שנדחו על ידי שירות סינון בסוג BLOCKED,
 * ורוב החייגנים מסתירים את הסוג הזה. התוצאה היא שיחות שנעלמות
 * מבלי שהמשתמש יידע שהן היו.
 */
object CallLogWriter {

    private const val TAG = "CallLogWriter"

    fun writeMissed(ctx: Context, number: String, whenMillis: Long = System.currentTimeMillis()): Boolean {
        if (number.isBlank()) return false
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        return runCatching {
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, number)
                put(CallLog.Calls.DATE, whenMillis)
                put(CallLog.Calls.DURATION, 0L)
                put(CallLog.Calls.TYPE, CallLog.Calls.MISSED_TYPE)
                put(CallLog.Calls.NEW, 1)
                put(CallLog.Calls.IS_READ, 0)
            }
            ctx.contentResolver.insert(CallLog.Calls.CONTENT_URI, values) != null
        }.onFailure { Log.w(TAG, "write", it) }.getOrDefault(false)
    }
}
