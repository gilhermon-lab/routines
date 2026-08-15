package com.gil.routines.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.gil.routines.data.Actions

/**
 * מיישם את מצב המכשיר לפי המצבים הפעילים.
 *
 * כלל חשוב: האפליקציה מחזירה לקדמותו רק מה שהיא עצמה שינתה.
 * בלי זה כל קריאה הייתה מכבה "נא לא להפריע" שהמשתמש הדליק בעצמו,
 * וזה נראה בדיוק כמו הבהוב — נדלק ונכבה מיד.
 */
object ModeApplier {

    private const val TAG = "ModeApplier"
    private const val PREFS = "routines_applied"
    private const val OWNS_DND = "owns_dnd"
    private const val OWNS_MUTE = "owns_mute"
    private const val LAST = "last_trace"

    fun applyCurrentState(ctx: Context) {
        val active = RoutineEngine.activeModes(ctx)
        val wanted = active.flatMap { it.actions }.toSet()
        val trace = StringBuilder()
        trace.append(if (active.isEmpty()) "אין מצב פעיל" else "פעיל: " + active.joinToString(", ") { it.name })

        applyDnd(ctx, wanted.contains(Actions.DND), trace)
        applyMute(ctx, wanted.contains(Actions.MUTE), trace)
        applyBrightness(ctx, wanted.contains(Actions.BRIGHTNESS), trace)
        applyGrayscale(ctx, wanted.contains(Actions.GRAYSCALE))
        applyAirplane(ctx, wanted.contains(Actions.AIRPLANE))

        prefs(ctx).edit().putString(LAST, trace.toString()).apply()
    }

    fun lastTrace(ctx: Context): String = prefs(ctx).getString(LAST, "עדיין לא הופעל") ?: ""

    fun dndFilterName(ctx: Context): String = runCatching {
        when (ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "כבוי"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "עדיפות בלבד"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "שקט מוחלט"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "התראות בלבד"
            else -> "לא ידוע"
        }
    }.getOrDefault("אין גישה")

    private fun applyDnd(ctx: Context, want: Boolean, trace: StringBuilder) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            trace.append(" · DND: אין הרשאה")
            return@runCatching
        }

        val owns = prefs(ctx).getBoolean(OWNS_DND, false)
        val currentlyOn = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        when {
            want && !currentlyOn -> {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                prefs(ctx).edit().putBoolean(OWNS_DND, true).apply()
                trace.append(" · DND הודלק")
            }
            want -> trace.append(" · DND כבר דלוק")
            !want && owns -> {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                prefs(ctx).edit().putBoolean(OWNS_DND, false).apply()
                trace.append(" · DND כובה")
            }
            !want && currentlyOn -> trace.append(" · DND דלוק אך לא על ידינו — לא נגענו")
            else -> trace.append(" · DND כבוי")
        }
    }.onFailure { Log.w(TAG, "DND", it) }

    private fun applyMute(ctx: Context, want: Boolean, trace: StringBuilder) = runCatching {
        val am = ctx.getSystemService(AudioManager::class.java)
        val owns = prefs(ctx).getBoolean(OWNS_MUTE, false)

        if (want && am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            prefs(ctx).edit().putBoolean(OWNS_MUTE, true).apply()
            trace.append(" · צלצול הושתק")
        } else if (!want && owns) {
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            prefs(ctx).edit().putBoolean(OWNS_MUTE, false).apply()
            trace.append(" · צלצול הוחזר")
        }
    }.onFailure { Log.w(TAG, "mute", it) }

    private fun applyBrightness(ctx: Context, want: Boolean, trace: StringBuilder) = runCatching {
        if (!Settings.System.canWrite(ctx)) return@runCatching
        if (!want) return@runCatching
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 12)
        trace.append(" · בהירות הופחתה")
    }.onFailure { Log.w(TAG, "brightness", it) }

    private fun applyGrayscale(ctx: Context, on: Boolean) = runCatching {
        Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", if (on) 1 else 0)
        Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer", if (on) 0 else -1)
    }.onFailure { Log.w(TAG, "grayscale", it) }

    private fun applyAirplane(ctx: Context, on: Boolean) = runCatching {
        Settings.Global.putInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (on) 1 else 0)
        ctx.sendBroadcast(
            android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", on)
        )
    }.onFailure { Log.w(TAG, "airplane", it) }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
