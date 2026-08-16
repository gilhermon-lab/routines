package com.gil.routines.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * שתי פלטות: יום ולילה.
 * ביום הצבעים בולטים על נייר בהיר, בלילה הכל שוקע לדיו כהה.
 */
data class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val brass: Color,
    val brassSoft: Color,
    val brassDim: Color,
    val ok: Color,
    val dark: Boolean
)

val LuxNight = Palette(
    bg = Color(0xFF0E1014),
    surface = Color(0xFF171A21),
    surfaceHi = Color(0xFF1E222B),
    line = Color(0xFF2A2F3A),
    text = Color(0xFFF2F0EC),
    muted = Color(0xFF8B8FA0),
    faint = Color(0xFF5C6172),
    brass = Color(0xFFC9A24A),
    brassSoft = Color(0xFFD8C08A),
    brassDim = Color(0xFF6E5A2C),
    ok = Color(0xFF6FBF95),
    dark = true
)

val LuxDay = Palette(
    bg = Color(0xFFF3F0E9),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFFAF7F1),
    line = Color(0xFFE0D9CB),
    text = Color(0xFF14161B),
    muted = Color(0xFF5A5E6E),
    faint = Color(0xFF8A8E9C),
    brass = Color(0xFF9A7418),
    brassSoft = Color(0xFFB98F2C),
    brassDim = Color(0xFFD8C89E),
    ok = Color(0xFF2C7A5A),
    dark = false
)

val LocalPalette = staticCompositionLocalOf { LuxNight }

/**
 * נקרא כ-Lux.bg וכדומה. עטוף באובייקט בכוונה — זה הדפוס ש-Compose עצמו
 * משתמש בו ב-MaterialTheme, ובניגוד לתכונה ברמת קובץ הוא מתקמפל נכון.
 */
object Lux {
    val bg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bg
    val surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
    val surfaceHi: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceHi
    val line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
    val text: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.text
    val muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
    val faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
    val brass: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.brass
    val brassSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.brassSoft
    val brassDim: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.brassDim
    val ok: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ok
}
