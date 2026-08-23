package com.gil.routines.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** תרגום מספר לשם, וחיוג חזרה */
object PhoneNames {

    /** null אם המספר אינו באנשי הקשר */
    fun nameFor(ctx: Context, number: String): String? {
        if (number.isBlank()) return null
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0)?.ifBlank { null } else null }
        }.getOrNull()
    }

    /**
     * חיוג חזרה. אם יש הרשאת חיוג — מתקשר ישירות,
     * אחרת פותח את החייגן עם המספר מוכן, וזה עובד תמיד.
     */
    fun call(ctx: Context, number: String) {
        if (number.isBlank()) return
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val canCall = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { ctx.startActivity(intent) }.onFailure {
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
