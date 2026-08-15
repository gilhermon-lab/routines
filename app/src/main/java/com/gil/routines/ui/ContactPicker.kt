package com.gil.routines.ui

import android.content.Context
import android.provider.ContactsContract
import com.gil.routines.data.AllowedContact
import com.gil.routines.data.normalizeNumber

/**
 * קריאת אנשי הקשר מהמכשיר.
 * שולפים שורות טלפון ולא אנשי קשר, כך שאיש קשר עם כמה מספרים
 * מופיע פעם אחת לכל מספר ואין עמימות לגבי מה נחסם.
 */
object Contacts {

    fun all(ctx: Context): List<AllowedContact> {
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { c ->
                val seen = HashSet<String>()
                buildList {
                    while (c.moveToNext()) {
                        val name = c.getString(0).orEmpty().ifBlank { "ללא שם" }
                        val number = c.getString(1).orEmpty()
                        val key = normalizeNumber(number)
                        if (number.isNotBlank() && key.isNotBlank() && seen.add(key)) {
                            add(AllowedContact(name, number))
                        }
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
