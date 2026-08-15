package com.gil.routines.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.gil.routines.data.AllowedContact

/**
 * בחירת איש קשר דרך הבורר של המערכת.
 * בוחרים ישירות שורת טלפון ולא איש קשר, כך שמקבלים שם ומספר בשאילתה אחת
 * ולא צריך להתמודד עם איש קשר שיש לו כמה מספרים.
 */
object ContactPicker {

    fun intent(): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    fun read(ctx: Context, uri: Uri?): AllowedContact? {
        if (uri == null) return null
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val name = c.getString(0).orEmpty().ifBlank { "ללא שם" }
                val number = c.getString(1).orEmpty()
                if (number.isBlank()) null else AllowedContact(name, number)
            }
        }.getOrNull()
    }
}
