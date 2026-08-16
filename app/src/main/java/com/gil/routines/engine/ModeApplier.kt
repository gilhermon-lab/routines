package com.gil.routines.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.gil.routines.data.Actions
import com.gil.routines.data.ScreenConfig

/**
 * מיישם את מצב המכשיר לפי המצבים הפעילים.
 *
 * שני כללים מנחים:
 * 1. מחזירים לקדמותו רק מה שאנחנו שינינו, אחרת נכבה DND שהמשתמש הדליק בעצמו.
 * 2. ערכי מסך מקוריים נשמרים לפני השינוי ומוחזרים בסיום המצב.
 */
object ModeApplier {

    private const val TAG = "ModeApplier"
    private const val PREFS = "routines_applied"
    private const val OWNS_DND = "owns_dnd"
    private const val OWNS_MUTE = "owns_mute"
    private const val SAVED_BRIGHTNESS = "saved_brightness"
    private const val SAVED_BRIGHTNESS_MODE = "saved_brightness_mode"
    private const val SAVED_TIMEOUT = "saved_timeout"
    private const val LAST = "last_trace"

    fun applyCurrentState(ctx: Context) {
        val active = RoutineEngine.activeModes(ctx)
        val wanted = active.flatMap { it.actions }.toSet()
        val trace = StringBuilder()
        trace.append(if (active.isEmpty()) "אין מצב פעיל" else "פעיל: " + active.joinToString(", ") { it.name })

        applyDnd(ctx, wanted.contains(Actions.DND), trace)
        applyMute(ctx, wanted.contains(Actions.MUTE), trace)

        // אם כמה מצבים פעילים, הראשון שמבקש שינוי מסך קובע
        applyScreen(ctx, active.firstOrNull { it.screen.let { s ->
            s.dimEnabled || s.timeoutEnabled || s.nightLight || s.grayscale
        } }?.screen, trace)

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
            trace.append(" · DND: אין הרשאה"); return@runCatching
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

    /* ── מסך ── */

    private fun applyScreen(ctx: Context, want: ScreenConfig?, trace: StringBuilder) {
        val canWrite = runCatching { Settings.System.canWrite(ctx) }.getOrDefault(false)
        val p = prefs(ctx)

        // בהירות
        if (canWrite) {
            if (want?.dimEnabled == true) {
                if (!p.contains(SAVED_BRIGHTNESS)) {
                    runCatching {
                        p.edit()
                            .putInt(SAVED_BRIGHTNESS, Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
                            .putInt(SAVED_BRIGHTNESS_MODE, Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
                            .apply()
                    }
                }
                runCatching {
                    if (want.disableAdaptive) {
                        Settings.System.putInt(
                            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        )
                    }
                    val value = (want.brightnessPercent.coerceIn(1, 100) * 255 / 100).coerceAtLeast(1)
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
                    trace.append(" · בהירות ${want.brightnessPercent}%")
                }
            } else if (p.contains(SAVED_BRIGHTNESS)) {
                runCatching {
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, p.getInt(SAVED_BRIGHTNESS, 128))
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt(SAVED_BRIGHTNESS_MODE, 1))
                    trace.append(" · בהירות הוחזרה")
                }
                p.edit().remove(SAVED_BRIGHTNESS).remove(SAVED_BRIGHTNESS_MODE).apply()
            }

            // זמן כיבוי מסך
            if (want?.timeoutEnabled == true) {
                if (!p.contains(SAVED_TIMEOUT)) {
                    runCatching {
                        p.edit().putInt(
                            SAVED_TIMEOUT,
                            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
                        ).apply()
                    }
                }
                runCatching {
                    Settings.System.putInt(
                        ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                        want.timeoutSeconds * 1000
                    )
                    trace.append(" · כיבוי מסך ${want.timeoutSeconds}ש")
                }
            } else if (p.contains(SAVED_TIMEOUT)) {
                runCatching {
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, p.getInt(SAVED_TIMEOUT, 30000))
                }
                p.edit().remove(SAVED_TIMEOUT).apply()
            }
        } else if (want?.dimEnabled == true) {
            trace.append(" · מסך: חסרה הרשאת שינוי הגדרות")
        }

        // אלה דורשים WRITE_SECURE_SETTINGS ולכן נכשלים בשקט בלעדיה
        runCatching {
            Settings.Secure.putInt(ctx.contentResolver, "night_display_activated", if (want?.nightLight == true) 1 else 0)
        }.onFailure { Log.w(TAG, "night light", it) }

        runCatching {
            val on = want?.grayscale == true
            Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", if (on) 1 else 0)
            Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer", if (on) 0 else -1)
        }.onFailure { Log.w(TAG, "grayscale", it) }
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
