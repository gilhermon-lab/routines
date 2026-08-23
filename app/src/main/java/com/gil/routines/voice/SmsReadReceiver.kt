package com.gil.routines.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.gil.routines.engine.RoutineEngine

/** מקריא SMS נכנס כשמצב עם הקראה פעיל */
class SmsReadReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val mode = RoutineEngine.activeModes(ctx).firstOrNull { it.voice.readSms } ?: return

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull() ?: return

        // הודעה ארוכה מגיעה בכמה חלקים — מאחדים לפי שולח
        val bySender = messages.filterNotNull().groupBy { it.originatingAddress.orEmpty() }

        bySender.forEach { (number, parts) ->
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            val who = if (mode.voice.includeSender) MessageNames.display(ctx, number) else null
            val text = if (who != null) "הודעה מ$who. $body" else "הודעה חדשה. $body"
            VoiceReader.speak(ctx, text)
        }
    }
}
