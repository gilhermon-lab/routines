package com.gil.routines.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.gil.routines.data.Actions
import com.gil.routines.data.CallHandling
import com.gil.routines.data.Mode
import com.gil.routines.engine.RoutineEngine

/**
 * הלב של האפליקציה. אנדרואיד קורא ל-onScreenCall לפני שהטלפון מצלצל,
 * ומחכה לתשובה — לכן כל מה שכאן חייב להיות מהיר.
 */
class CallGuardService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // רק שיחות נכנסות. שיחות יוצאות עוברות דרך אותו callback בחלק מהמכשירים.
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondAllow(details); return
        }

        val mode = RoutineEngine.activeModes(this).firstOrNull { it.actions.contains(Actions.CALL_GUARD) }
        if (mode == null) { respondAllow(details); return }

        val number = details.handle?.schemeSpecificPart.orEmpty()
        if (number.isBlank()) { respondAllow(details); return }   // מספר חסוי — לא מנסים לחכם

        // אנשי קשר עוברים, אם המצב מתיר
        if (mode.call.allowContacts && isContact(number)) { respondAllow(details); return }

        // פריצה בחיוג חוזר
        val bt = mode.call.breakthrough
        if (bt.enabled) {
            val count = CallAttemptLog.recordAndCount(this, number, bt.windowMinutes)
            if (count >= bt.attempts) {
                CallAttemptLog.clear(this, number)
                respondAllow(details)
                return
            }
        }

        block(details, mode)
        maybeSendSms(mode, number)
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

    private fun maybeSendSms(mode: Mode, number: String) {
        if (!mode.call.sendSms || mode.call.message.isBlank()) return
        if (!hasPermission(Manifest.permission.SEND_SMS)) return
        if (!CallAttemptLog.shouldSendSms(this, number, mode.call.smsCooldownHours)) return

        runCatching {
            val sms = getSystemService(SmsManager::class.java)
            val parts = sms.divideMessage(mode.call.message)
            if (parts.size == 1) {
                sms.sendTextMessage(number, null, mode.call.message, null, null)
            } else {
                sms.sendMultipartTextMessage(number, null, parts, null, null)
            }
            CallAttemptLog.markSmsSent(this, number)
        }.onFailure { Log.w(TAG, "שליחת ההודעה נכשלה", it) }
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
