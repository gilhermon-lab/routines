package com.gil.routines.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * כרטיס ראשי לכל מצב: איור, מצב נוכחי, ופעולה אחת גדולה.
 *
 * האיור נבנה מקשתות ועיגולים בצבע המצב — אותה שפה של החוגה,
 * במקום איור דמות שהיה מתנגש עם שאר העיצוב.
 */
@Composable
fun ModeHero(
    modeId: String,
    title: String,
    status: ModeStatus,
    color: Color,
    onPrimary: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.06f))
                ),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Light, color = Lux.text)
            Text(status.label, fontSize = 13.sp, color = if (status.live) color else Lux.muted)
            Text(status.detail, fontSize = 11.sp, color = Lux.faint, lineHeight = 16.sp)

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPrimary,
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status.live) Lux.surfaceHi else color,
                    contentColor = if (status.live) Lux.text else Color.White
                )
            ) {
                Text(if (status.live) "כיבוי עכשיו" else "הפעלה עכשיו", fontSize = 14.sp)
            }
        }

        ModeArt(modeId, color, Modifier.size(96.dp))
    }
}

/** איור וקטורי פשוט לכל מצב */
@Composable
private fun ModeArt(modeId: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val d = size.minDimension
        val c = Offset(size.width / 2, size.height / 2)

        // הילה רכה מאחור
        drawCircle(color.copy(alpha = 0.14f), radius = d * 0.44f, center = c)

        when (modeId) {
            "sleep" -> {
                // סהר: עיגול מלא שממנו "נגזר" עיגול שני
                drawCircle(color, radius = d * 0.27f, center = c)
                drawCircle(
                    Color.Transparent, radius = d * 0.24f,
                    center = Offset(c.x + d * 0.13f, c.y - d * 0.09f),
                    blendMode = BlendMode.Clear
                )
                listOf(
                    Offset(c.x - d * 0.30f, c.y - d * 0.26f) to d * 0.030f,
                    Offset(c.x - d * 0.16f, c.y + d * 0.33f) to d * 0.020f,
                    Offset(c.x + d * 0.30f, c.y + d * 0.22f) to d * 0.025f
                ).forEach { (p, r) -> drawCircle(color.copy(alpha = 0.75f), radius = r, center = p) }
            }
            "meeting" -> {
                // שלוש קשתות סביב מרכז — אנשים סביב שולחן
                repeat(3) { i ->
                    val a = Math.toRadians(90.0 + i * 120)
                    val p = Offset(
                        c.x + (d * 0.26f * Math.cos(a)).toFloat(),
                        c.y + (d * 0.26f * Math.sin(a)).toFloat()
                    )
                    drawCircle(color, radius = d * 0.085f, center = p)
                }
                drawCircle(color.copy(alpha = 0.5f), radius = d * 0.12f, center = c, style = Stroke(width = d * 0.03f))
            }
            "drive" -> {
                drawArc(
                    color = color, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(c.x - d * 0.28f, c.y - d * 0.28f),
                    size = Size(d * 0.56f, d * 0.56f),
                    style = Stroke(width = d * 0.09f, cap = StrokeCap.Round)
                )
                drawCircle(color, radius = d * 0.07f, center = Offset(c.x - d * 0.22f, c.y + d * 0.20f))
                drawCircle(color, radius = d * 0.07f, center = Offset(c.x + d * 0.22f, c.y + d * 0.20f))
            }
            else -> {
                // ברירת מחדל: טבעות קונצנטריות
                listOf(0.30f to 0.9f, 0.21f to 0.6f, 0.12f to 0.35f).forEach { (rf, alpha) ->
                    drawCircle(
                        color.copy(alpha = alpha), radius = d * rf, center = c,
                        style = Stroke(width = d * 0.045f)
                    )
                }
            }
        }
    }
}
