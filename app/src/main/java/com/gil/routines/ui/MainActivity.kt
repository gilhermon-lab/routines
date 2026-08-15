package com.gil.routines.ui

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gil.routines.data.*
import com.gil.routines.engine.ModeApplier
import com.gil.routines.engine.RoutineEngine
import com.gil.routines.engine.RoutineScheduler
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoutineScheduler.rescheduleAll(this)
        ModeApplier.applyCurrentState(this)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF4A4EBF))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RoutinesScreen()
                }
            }
        }
    }
}

/* ────────────────────────────────────────────── */

@Composable
fun RoutinesScreen() {
    val ctx = LocalContext.current
    var modes by remember { mutableStateOf(ModeStore.load(ctx)) }
    var editing by remember { mutableStateOf<String?>(null) }
    var permTick by remember { mutableIntStateOf(0) }

    val active = remember(modes, permTick) { RoutineEngine.activeModes(ctx) }

    fun mutate(id: String, f: (Mode) -> Mode) {
        modes = ModeStore.update(ctx, id, f)
        RoutineScheduler.rescheduleAll(ctx)
        ModeApplier.applyCurrentState(ctx)
    }

    Surface(color = Color(0xFFE9E8F0)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                Text("שגרות", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
            }

            item { DayDial(modes, active) }

            item {
                SectionLabel("הרשאות במכשיר")
                PermissionsCard(onChanged = { permTick++ })
            }

            item { SectionLabel("המצבים שלי") }

            items(modes, key = { it.id }) { m ->
                ModeCard(
                    mode = m,
                    live = active.any { it.id == m.id },
                    onToggle = { mutate(m.id) { it.copy(enabled = !it.enabled) } },
                    onOpen = { editing = m.id }
                )
            }
        }
    }

    editing?.let { id ->
        modes.find { it.id == id }?.let { mode ->
            ModeSheet(
                mode = mode,
                onDismiss = { editing = null },
                onChange = { updated -> mutate(id) { updated } }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = Color(0xFF6B6C86), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

/* ── חוגת 24 שעות ── */

@Composable
fun DayDial(modes: List<Mode>, active: List<Mode>) {
    val now = ZonedDateTime.now()
    val nowMinute = now.hour * 60 + now.minute

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val d = size.minDimension
                val c = Offset(size.width / 2, size.height / 2)

                // שנתות כל שעה
                repeat(24) { h ->
                    val a = Math.toRadians(h / 24.0 * 360 - 90)
                    val outer = d * 0.46f
                    val inner = if (h % 6 == 0) d * 0.41f else d * 0.435f
                    drawLine(
                        color = if (h % 6 == 0) Color(0xFF9E9CB8) else Color(0xFFCFCDE0),
                        start = c + Offset((outer * Math.cos(a)).toFloat(), (outer * Math.sin(a)).toFloat()),
                        end = c + Offset((inner * Math.cos(a)).toFloat(), (inner * Math.sin(a)).toFloat()),
                        strokeWidth = if (h % 6 == 0) 3f else 1.5f
                    )
                }

                // קשת לכל מצב פעיל, בטבעת משלו
                modes.filter { it.enabled }.forEachIndexed { i, m ->
                    val r = d * 0.38f - i * d * 0.045f
                    val sweep = (((m.end - m.start) + 1440) % 1440) / 1440f * 360f
                    val startAngle = m.start / 1440f * 360f - 90f
                    val isLive = active.any { it.id == m.id }
                    drawArc(
                        color = Color(m.colorArgb.toInt()).copy(alpha = if (isLive) 1f else 0.4f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = if (isLive) 22f else 16f, cap = StrokeCap.Round)
                    )
                }

                // מחוג הזמן הנוכחי
                val na = Math.toRadians(nowMinute / 1440.0 * 360 - 90)
                drawLine(
                    color = Color(0xFF1B1D2A),
                    start = c + Offset((d * 0.14f * Math.cos(na)).toFloat(), (d * 0.14f * Math.sin(na)).toFloat()),
                    end = c + Offset((d * 0.47f * Math.cos(na)).toFloat(), (d * 0.47f * Math.sin(na)).toFloat()),
                    strokeWidth = 5f, cap = StrokeCap.Round
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt(nowMinute), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (active.isEmpty()) "אין מצב פעיל" else active.joinToString(" · ") { it.name },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (active.isEmpty()) Color(0xFFB0AFC4) else Color(active.first().colorArgb.toInt())
                )
            }
        }
    }
}

/* ── כרטיס מצב ── */

@Composable
fun ModeCard(mode: Mode, live: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val color = Color(mode.colorArgb.toInt())
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
            .border(1.dp, if (live) color else Color(0xFFDAD8E6), RoundedCornerShape(18.dp))
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(mode.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${fmt(mode.start)}–${fmt(mode.end)} · ${dayLabels(mode.days)}",
                    fontSize = 12.sp, color = Color(0xFF6B6C86)
                )
                if (live) Text("● פעיל עכשיו", fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
            }
            Switch(checked = mode.enabled, onCheckedChange = { onToggle() })
        }
    }
}

/* ── מסך עריכה ── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(mode: Mode, onDismiss: () -> Unit, onChange: (Mode) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(mode.name, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("התחלה", mode.start, Modifier.weight(1f)) { onChange(mode.copy(start = it)) }
                TimeField("סיום", mode.end, Modifier.weight(1f)) { onChange(mode.copy(end = it)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("א" to 1, "ב" to 2, "ג" to 3, "ד" to 4, "ה" to 5, "ו" to 6, "ש" to 7)
                    .forEach { (label, d) ->
                        val on = mode.days.contains(d)
                        Box(
                            Modifier.weight(1f).height(40.dp)
                                .background(
                                    if (on) Color(mode.colorArgb.toInt()) else Color.White,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(1.dp, Color(0xFFDAD8E6), RoundedCornerShape(10.dp))
                                .clickable {
                                    val days = if (on) mode.days - d else mode.days + d
                                    onChange(mode.copy(days = days))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (on) Color.White else Color(0xFF6B6C86), fontSize = 13.sp)
                        }
                    }
            }

            HorizontalDivider()
            Text("שיחות נכנסות", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF6B6C86))

            ToggleRow("סינון שיחות במצב הזה", mode.actions.contains(Actions.CALL_GUARD)) {
                val a = if (it) mode.actions + Actions.CALL_GUARD else mode.actions - Actions.CALL_GUARD
                onChange(mode.copy(actions = a))
            }

            if (mode.actions.contains(Actions.CALL_GUARD)) {
                SegmentedRow(
                    options = listOf("לדחות מיד" to CallHandling.REJECT, "להשתיק בשקט" to CallHandling.SILENCE),
                    selected = mode.call.handling,
                    color = Color(mode.colorArgb.toInt())
                ) { onChange(mode.copy(call = mode.call.copy(handling = it))) }

                ToggleRow("לשלוח הודעה למתקשר", mode.call.sendSms) {
                    onChange(mode.copy(call = mode.call.copy(sendSms = it)))
                }

                if (mode.call.sendSms) {
                    OutlinedTextField(
                        value = mode.call.message,
                        onValueChange = { onChange(mode.copy(call = mode.call.copy(message = it))) },
                        label = { Text("ההודעה שתישלח") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${mode.call.message.length} תווים · ${(mode.call.message.length / 70) + 1} הודעות SMS",
                        fontSize = 11.sp, color = Color(0xFF8A8BA3)
                    )
                }

                ToggleRow("לתת לאנשי קשר לצלצל", mode.call.allowContacts) {
                    onChange(mode.copy(call = mode.call.copy(allowContacts = it)))
                }

                HorizontalDivider()
                ToggleRow("פריצה בחיוג חוזר", mode.call.breakthrough.enabled) {
                    onChange(mode.copy(call = mode.call.copy(breakthrough = mode.call.breakthrough.copy(enabled = it))))
                }

                if (mode.call.breakthrough.enabled) {
                    StepperRow(
                        "אחרי כמה חיוגים", mode.call.breakthrough.attempts, 2..6, "חיוגים"
                    ) {
                        onChange(mode.copy(call = mode.call.copy(breakthrough = mode.call.breakthrough.copy(attempts = it))))
                    }
                    StepperRow(
                        "בתוך כמה זמן", mode.call.breakthrough.windowMinutes, 5..30, "דקות", step = 5
                    ) {
                        onChange(mode.copy(call = mode.call.copy(breakthrough = mode.call.breakthrough.copy(windowMinutes = it))))
                    }
                    Text(
                        "${mode.call.breakthrough.attempts} חיוגים מאותו מספר תוך " +
                            "${mode.call.breakthrough.windowMinutes} דקות יבטלו את ההשתקה והטלפון יצלצל.",
                        fontSize = 12.sp, color = Color(0xFF6B6C86)
                    )
                }
            }

            ToggleRow("נא לא להפריע", mode.actions.contains(Actions.DND)) {
                val a = if (it) mode.actions + Actions.DND else mode.actions - Actions.DND
                onChange(mode.copy(actions = a))
            }
            ToggleRow("השתקת צלצול", mode.actions.contains(Actions.MUTE)) {
                val a = if (it) mode.actions + Actions.MUTE else mode.actions - Actions.MUTE
                onChange(mode.copy(actions = a))
            }
        }
    }
}

@Composable
private fun TimeField(label: String, minutes: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(onClick = {
        TimePickerDialog(
            ctx,
            { _, h, m -> onPick(h * 60 + m) },
            minutes / 60, minutes % 60, true      // true = פורמט 24 שעות
        ).show()
    }, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = Color(0xFF6B6C86))
            Text(fmt(minutes), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<Pair<String, T>>, selected: T, color: Color, onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(42.dp)
                    .background(if (on) color else Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFDAD8E6), RoundedCornerShape(12.dp))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color.White else Color(0xFF1B1D2A), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, unit: String, step: Int = 1, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value - step >= range.first) onChange(value - step) }) { Text("−") }
        Text("$value $unit", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { if (value + step <= range.last) onChange(value + step) }) { Text("+") }
    }
}

/* ── כרטיס הרשאות ── */

@Composable
fun PermissionsCard(onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { tick++; onChanged() }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++; onChanged() }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Permissions.all.forEach { p ->
            val granted = remember(tick, p.id) { runCatching { p.isGranted(ctx) }.getOrDefault(false) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (granted) Color(0xFFF4FBF7) else Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${p.subtitle} · פותח: ${p.unlocks}", fontSize = 11.sp, color = Color(0xFF8A8BA3))

                    p.adbCommand?.takeIf { !granted }?.let { cmd ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            cmd, fontSize = 10.sp, color = Color(0xFFE9E8F0),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFF1B1D2A), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        )
                        TextButton(onClick = { copy(ctx, cmd) }) { Text("העתקת הפקודה") }
                    }

                    Spacer(Modifier.height(6.dp))
                    if (granted) {
                        Text("אושר", color = Color(0xFF2C7A5A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        OutlinedButton(onClick = {
                            val opener = p.intentFor
                            when {
                                p.runtimePermissions.isNotEmpty() -> runtimeLauncher.launch(p.runtimePermissions)
                                opener != null -> settingsLauncher.launch(opener(ctx))
                            }
                        }, enabled = p.runtimePermissions.isNotEmpty() || p.intentFor != null) {
                            Text("אישור")
                        }
                    }
                }
            }
        }
    }
}

/* ── עזרים ── */

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("adb", text))
}

fun fmt(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

fun dayLabels(days: Set<Int>): String {
    val names = mapOf(1 to "א", 2 to "ב", 3 to "ג", 4 to "ד", 5 to "ה", 6 to "ו", 7 to "ש")
    return days.sorted().mapNotNull { names[it] }.joinToString("")
}
