package com.gil.routines.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.content.ContextCompat
import com.gil.routines.data.Actions
import com.gil.routines.data.CallHandling
import com.gil.routines.data.ContactPolicy
import com.gil.routines.data.normalizeNumber
import com.gil.routines.data.Mode
import com.gil.routines.engine.RoutineEngine

/**
 * הלב של האפליקציה. אנדרואיד קורא ל-onScreenCall לפני שהטלפון מצלצל,
 * ומחכה לתשובה — לכן כל מה שכאן חייב להיות מהיר.
 */
class CallGuardService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()

        // חלק מהמכשירים מדווחים DIRECTION_UNKNOWN בזמן הסינון, לכן פוסלים
        // רק שיחה יוצאת ודאית ולא כל מה שאינו נכנס ודאי.
        if (details.callDirection == Call.Details.DIRECTION_OUTGOING) {
            respondAllow(details); return
        }

        val mode = RoutineEngine.activeModes(this).firstOrNull { it.actions.contains(Actions.CALL_GUARD) }
        if (mode == null) {
            // נרשם בכל זאת — כך היומן מוכיח שהשירות בכלל נקרא
            log(number.ifBlank { "מספר חסוי" }, "—", "צלצל — אין מצב פעיל עם סינון", false)
            respondAllow(details); return
        }

        if (number.isBlank()) {
            log("מספר חסוי", mode.name, "צלצל — אין מספר לזיהוי", false)
            respondAllow(details); return
        }

        // מי מורשה לצלצל למרות שהמצב פעיל
        when (mode.call.contactPolicy) {
            ContactPolicy.ALL -> if (isContact(number)) {
                log(number, mode.name, "צלצל — איש קשר", false); respondAllow(details); return
            }
            ContactPolicy.FAVORITES -> if (isFavorite(number)) {
                log(number, mode.name, "צלצל — איש קשר מועדף", false); respondAllow(details); return
            }
            ContactPolicy.LIST -> {
                val key = normalizeNumber(number)
                val hit = mode.call.allowed.firstOrNull { it.key == key && it.key.isNotBlank() }
                if (hit != null) {
                    log(number, mode.name, "צלצל — ${hit.name} ברשימת ההיתר", false)
                    respondAllow(details); return
                }
            }
            ContactPolicy.NONE -> Unit
        }

        // פריצה בחיוג חוזר
        val bt = mode.call.breakthrough
        if (bt.enabled) {
            val count = CallAttemptLog.recordAndCount(this, number, bt.windowMinutes)
            if (count >= bt.attempts) {
                CallAttemptLog.clear(this, number)
                log(number, mode.name, "צלצל — פריצה אחרי $count חיוגים", false)
                respondAllow(details)
                return
            }
        }

        block(details, mode)

        // רשומה גלויה ביומן, כי הרשומה שאנדרואיד יוצר מסומנת "חסום" ומוסתרת
        if (mode.call.logAsMissed) CallLogWriter.writeMissed(this, number)

        val sent = maybeSendSms(mode, number)
        val what = if (mode.call.handling == CallHandling.SILENCE) "הושתקה" else "נדחתה"
        log(number, mode.name, what, sent)
    }

    private fun log(number: String, modeName: String, outcome: String, smsSent: Boolean) {
        runCatching {
            CallLogStore.add(
                this,
                CallEvent(System.currentTimeMillis(), number, modeName, outcome, smsSent)
            )
        }
    }

    private fun respondAllow(details: Call.Details) {
        respondToCall(details, CallResponse.Builder().build())
    }

    private fun block(details: Call.Details, mode: Mode) {
        val silence = mode.call.handling == CallHandling.SILENCE
        val response = CallResponse.Builder()
            .setDisallowCall(!silence)      // דחייה ממש מנתקת; השתקה משאירה את השיחה חיה
            .setRejectCall(!silence)
            .setSilenceCall(silence)
            .setSkipCallLog(false)          // תמיד רוצים לדעת מי ניסה
            .setSkipNotification(silence)
            .build()
        respondToCall(details, response)
    }

    /** מחזיר האם ההודעה אכן נשלחה, כדי שהיומן ישקף את האמת */
    private fun maybeSendSms(mode: Mode, number: String): Boolean {
        if (!mode.call.sendSms || mode.call.message.isBlank()) return false
        if (!hasPermission(Manifest.permission.SEND_SMS)) return false
        if (!CallAttemptLog.shouldSendSms(this, number, mode.call.smsCooldownHours)) return false

        return SmsSender.send(this, number, mode.call.message)
            .onSuccess { CallAttemptLog.markSmsSent(this, number) }
            .onFailure { Log.w(TAG, "שליחת ההודעה נכשלה", it) }
            .isSuccess
    }

    /** מועדף = מסומן בכוכב באפליקציית אנשי הקשר */
    private fun isFavorite(number: String): Boolean {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.STARRED), null, null, null)
                ?.use { it.moveToFirst() && it.getInt(0) == 1 } ?: false
        }.getOrDefault(false)
    }

    /** מספרים מגיעים בפורמטים שונים, ולכן PhoneLookup ולא השוואת מחרוזות */
    private fun isContact(number: String): Boolean {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    private fun Context.hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    companion object { private const val TAG = "CallGuard" }
}
