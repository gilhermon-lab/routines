package com.gil.routines.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * שתי פלטות: יום ולילה.
 *
 * ביום הצבעים בולטים על נייר בהיר, בלילה הכל שוקע לדיו כהה.
 * המבנה זהה בשתיהן, כך שכל המסכים משתמשים באותם שמות שדות.
 */
data class Palette(
    val Bg: Color,
    val Surface: Color,
    val SurfaceHi: Color,
    val Line: Color,
    val Text: Color,
    val Muted: Color,
    val Faint: Color,
    val Brass: Color,
    val BrassSoft: Color,
    val BrassDim: Color,
    val Ok: Color,
    val dark: Boolean
)

val LuxNight = Palette(
    Bg = Color(0xFF0E1014),
    Surface = Color(0xFF171A21),
    SurfaceHi = Color(0xFF1E222B),
    Line = Color(0xFF2A2F3A),
    Text = Color(0xFFF2F0EC),
    Muted = Color(0xFF8B8FA0),
    Faint = Color(0xFF5C6172),
    Brass = Color(0xFFC9A24A),
    BrassSoft = Color(0xFFD8C08A),
    BrassDim = Color(0xFF6E5A2C),
    Ok = Color(0xFF6FBF95),
    dark = true
)

val LuxDay = Palette(
    Bg = Color(0xFFF3F0E9),
    Surface = Color(0xFFFFFFFF),
    SurfaceHi = Color(0xFFFAF7F1),
    Line = Color(0xFFE0D9CB),
    Text = Color(0xFF14161B),
    Muted = Color(0xFF5A5E6E),
    Faint = Color(0xFF8A8E9C),
    Brass = Color(0xFF9A7418),
    BrassSoft = Color(0xFFB98F2C),
    BrassDim = Color(0xFFD8C89E),
    Ok = Color(0xFF2C7A5A),
    dark = false
)

val LocalPalette = staticCompositionLocalOf { LuxNight }

/** נקרא בכל המסכים כ-Lux.Bg וכדומה, ומתחלף לבד לפי שעה או מצב פעיל */
val Lux: Palette
    @Composable get() = LocalPalette.current
