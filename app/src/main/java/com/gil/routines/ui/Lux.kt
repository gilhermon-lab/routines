package com.gil.routines.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** שתי פלטות: ביום צבעים בולטים על נייר בהיר, בלילה דיו כהה */
data class Palette(
    val bg: Color, val surface: Color, val surfaceHi: Color, val line: Color,
    val text: Color, val muted: Color, val faint: Color,
    val brass: Color, val brassSoft: Color, val brassDim: Color,
    val ok: Color, val dark: Boolean
)

val LuxNight = Palette(
    bg = Color(0xFF0E1014), surface = Color(0xFF171A21), surfaceHi = Color(0xFF1E222B),
    line = Color(0xFF2A2F3A), text = Color(0xFFF2F0EC), muted = Color(0xFF8B8FA0),
    faint = Color(0xFF5C6172), brass = Color(0xFFC9A24A), brassSoft = Color(0xFFD8C08A),
    brassDim = Color(0xFF6E5A2C), ok = Color(0xFF6FBF95), dark = true
)

val LuxDay = Palette(
    bg = Color(0xFFF3F0E9), surface = Color(0xFFFFFFFF), surfaceHi = Color(0xFFFAF7F1),
    line = Color(0xFFE0D9CB), text = Color(0xFF14161B), muted = Color(0xFF5A5E6E),
    faint = Color(0xFF8A8E9C), brass = Color(0xFF9A7418), brassSoft = Color(0xFFB98F2C),
    brassDim = Color(0xFFD8C89E), ok = Color(0xFF2C7A5A), dark = false
)

enum class ThemeMode { AUTO, DAY, NIGHT }

/**
 * הפלטה הפעילה.
 *
 * מבוססת על mutableStateOf רגיל ולא על CompositionLocal עם getters של Compose —
 * הגרסה הקודמת נכשלה בקומפילציה, וזו פשוטה יותר ומשיגה את אותה תגובתיות.
 */
object Lux {
    var palette by mutableStateOf(LuxNight)

    val bg get() = palette.bg
    val surface get() = palette.surface
    val surfaceHi get() = palette.surfaceHi
    val line get() = palette.line
    val text get() = palette.text
    val muted get() = palette.muted
    val faint get() = palette.faint
    val brass get() = palette.brass
    val brassSoft get() = palette.brassSoft
    val brassDim get() = palette.brassDim
    val ok get() = palette.ok
}

object ThemePrefs {
    private const val PREFS = "routines_theme"
    private const val KEY = "mode"

    fun load(ctx: Context): ThemeMode = runCatching {
        ThemeMode.valueOf(
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "AUTO") ?: "AUTO"
        )
    }.getOrDefault(ThemeMode.AUTO)

    fun save(ctx: Context, mode: ThemeMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}
