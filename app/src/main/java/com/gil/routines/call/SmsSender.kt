package com.gil.routines.call

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * שליחת SMS עם נפילה אחורה לפי גרסת אנדרואיד.
 * SmsManager הפך לשירות מערכת רק ב-API 31; מתחת לזה חייבים getDefault.
 */
object SmsSender {

    fun manager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    /** מחזיר Result כדי שהשגיאה תגיע למסך האבחון ולא תיבלע בלוג */
    fun send(ctx: Context, number: String, text: String): Result<Unit> = runCatching {
        require(number.isNotBlank()) { "מספר ריק" }
        require(text.isNotBlank()) { "הודעה ריקה" }

        val sms = manager(ctx)
        val parts = sms.divideMessage(text)
        if (parts.size == 1) {
            sms.sendTextMessage(number, null, text, null, null)
        } else {
            sms.sendMultipartTextMessage(number, null, parts, null, null)
        }
    }
}
