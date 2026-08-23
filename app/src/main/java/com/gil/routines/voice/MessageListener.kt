package com.gil.routines.voice

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gil.routines.engine.RoutineEngine

/**
 * מקריא הודעות מאפליקציות כמו וואטסאפ.
 *
 * אין API לקרוא הודעות של אפליקציות אחרות, ולכן קוראים את ההתראה שלהן.
 * זו הדרך היחידה, וזו גם הסיבה שנדרשת הרשאת גישה להתראות.
 */
class MessageListener : NotificationListenerService() {

    private val recent = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val mode = RoutineEngine.activeModes(this).firstOrNull { it.voice.readApps } ?: return
        if (sbn.packageName == packageName) return
        if (!mode.voice.packages.contains(sbn.packageName)) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (body.isBlank()) return

        // אותה הודעה מתעדכנת כמה פעמים — מקריאים פעם אחת
        val key = sbn.packageName + "|" + title + "|" + body
        val now = System.currentTimeMillis()
        recent.entries.removeAll { now - it.value > 60_000 }
        if (recent.put(key, now) != null) return

        val text = if (mode.voice.includeSender && title.isNotBlank()) "הודעה מ$title. $body" else body
        VoiceReader.speak(this, text)
    }
}
