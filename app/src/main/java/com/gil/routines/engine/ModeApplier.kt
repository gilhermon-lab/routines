package com.gil.routines.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.gil.routines.data.Actions

/**
 * מיישם את מצב המכשיר לפי המצבים הפעילים.
 * כל פעולה עטופה ב-runCatching: הרשאה חסרה לא אמורה להפיל את השאר.
 */
object ModeApplier {

    private const val TAG = "ModeApplier"

    fun applyCurrentState(ctx: Context) {
        val active = RoutineEngine.activeModes(ctx)
        val wanted = active.flatMap { it.actions }.toSet()

        applyDnd(ctx, wanted.contains(Actions.DND))
        applyMute(ctx, wanted.contains(Actions.MUTE))
        applyBrightness(ctx, wanted.contains(Actions.BRIGHTNESS))
        applyGrayscale(ctx, wanted.contains(Actions.GRAYSCALE))
        applyAirplane(ctx, wanted.contains(Actions.AIRPLANE))
    }

    private fun applyDnd(ctx: Context, on: Boolean) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return@runCatching
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }.onFailure { Log.w(TAG, "DND", it) }

    private fun applyMute(ctx: Context, on: Boolean) = runCatching {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.ringerMode = if (on) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
    }.onFailure { Log.w(TAG, "mute", it) }

    private fun applyBrightness(ctx: Context, on: Boolean) = runCatching {
        if (!Settings.System.canWrite(ctx)) return@runCatching
        Settings.System.putInt(
            ctx.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, if (on) 12 else 140)
    }.onFailure { Log.w(TAG, "brightness", it) }

    /* ── השתיים הבאות עובדות רק אחרי:
       adb shell pm grant com.gil.routines android.permission.WRITE_SECURE_SETTINGS ── */

    private fun applyGrayscale(ctx: Context, on: Boolean) = runCatching {
        Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", if (on) 1 else 0)
        Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer", if (on) 0 else -1)
    }.onFailure { Log.w(TAG, "grayscale — חסרה WRITE_SECURE_SETTINGS?", it) }

    private fun applyAirplane(ctx: Context, on: Boolean) = runCatching {
        Settings.Global.putInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (on) 1 else 0)
        ctx.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
            .putExtra("state", on))
    }.onFailure { Log.w(TAG, "airplane — חסרה WRITE_SECURE_SETTINGS?", it) }
}
