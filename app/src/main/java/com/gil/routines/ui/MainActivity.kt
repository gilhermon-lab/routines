package com.gil.routines.ui

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gil.routines.call.CallLogStore
import com.gil.routines.data.*
import com.gil.routines.engine.ModeApplier
import com.gil.routines.engine.RoutineEngine
import com.gil.routines.engine.RoutineScheduler
import com.gil.routines.widget.ModeWidget
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RoutineScheduler.rescheduleAll(this)
        ModeApplier.applyCurrentState(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Lux.Brass,
                    onPrimary = Lux.Bg,
                    background = Lux.Bg,
                    surface = Lux.Surface,
                    onSurface = Lux.Text,
                    onBackground = Lux.Text
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RoutinesScreen()
                }
            }
        }
    }
}

/* ── איורים ── */

fun iconFor(id: String): ImageVector = when (id) {
    "sleep" -> Icons.Outlined.Bedtime
    "work" -> Icons.Outlined.Work
    "meeting" -> Icons.Outlined.Groups
    "drive" -> Icons.Outlined.DirectionsCar
    "focus" -> Icons.Outlined.CenterFocusStrong
    else -> Icons.Outlined.Schedule
}

@Composable
fun SectionHeader(icon: ImageVector, text: String, top: Int = 22) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = top.dp, bottom = 10.dp)
    ) {
        Icon(icon, null, tint = Lux.Brass, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Lux.Muted, letterSpacing = 2.sp)
        HorizontalDivider(color = Lux.Line, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun luxSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Lux.Bg,
    checkedTrackColor = Lux.Brass,
    checkedBorderColor = Lux.Brass,
    uncheckedThumbColor = Lux.Faint,
    uncheckedTrackColor = Lux.Surface,
    uncheckedBorderColor = Lux.Line
)

/* ────────────────────────────────────────────── */

@Composable
fun RoutinesScreen() {
    val ctx = LocalContext.current
    var modes by remember { mutableStateOf(ModeStore.load(ctx)) }
    var editing by remember { mutableStateOf<String?>(null) }
    var showPerms by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val active = remember(modes, tick) { RoutineEngine.activeModes(ctx) }
    val grantedCount = remember(tick) {
        Permissions.all.count { runCatching { it.isGranted(ctx) }.getOrDefault(false) }
    }

    fun mutate(id: String, f: (Mode) -> Mode) {
        modes = ModeStore.update(ctx, id, f)
        RoutineScheduler.rescheduleAll(ctx)
        ModeApplier.applyCurrentState(ctx)
        ModeWidget.refreshAll(ctx)
    }

    Surface(color = Lux.Bg, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 56.dp)
        ) {
            item {
                Text("שגרות", fontSize = 30.sp, fontWeight = FontWeight.Light,
                    color = Lux.Text, letterSpacing = 1.sp)
                Text("מצבים ושגרות", fontSize = 11.sp, color = Lux.Faint, letterSpacing = 3.sp)
                Spacer(Modifier.height(26.dp))
            }

            item { DayDial(modes, active) }

            item { SectionHeader(Icons.Outlined.Layers, "המצבים שלי") }

            items(modes, key = { it.id }) { m ->
                ModeRow(
                    mode = m,
                    live = active.any { it.id == m.id },
                    onToggle = { mutate(m.id) { it.copy(enabled = !it.enabled) } },
                    onOpen = { editing = m.id }
                )
                Spacer(Modifier.height(10.dp))
            }

            item {
                SectionHeader(Icons.Outlined.PhoneCallback, "שיחות שטופלו")
                CallLogCard(tick)
            }

            // ההרשאות יורדות לתחתית ונפתחות בחלון צף, כדי לא להעמיס על המסך הראשי
            item {
                Spacer(Modifier.height(26.dp))
                PermissionsTrigger(grantedCount) { showPerms = true }
                Spacer(Modifier.height(10.dp))
                TriggerRow(
                    Icons.Outlined.MonitorHeart, "בדיקת מערכת",
                    "מה חסם הודעה, ושליחת הודעת בדיקה"
                ) { showDiag = true }
            }
        }
    }

    editing?.let { id ->
        modes.find { it.id == id }?.let { mode ->
            ModeSheet(mode, { editing = null }) { updated -> mutate(id) { updated } }
        }
    }

    if (showPerms) {
        PermissionsSheet(onDismiss = { showPerms = false }, onChanged = { tick++ })
    }

    if (showDiag) {
        DiagnosticsSheet(onDismiss = { showDiag = false })
    }
}

/* ── חוגת 24 שעות ── */

@Composable
fun DayDial(modes: List<Mode>, active: List<Mode>) {
    val now = ZonedDateTime.now()
    val nowMinute = now.hour * 60 + now.minute
    val ring = modes.filter { it.enabled }

    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.radialGradient(listOf(Lux.SurfaceHi, Lux.Bg)),
                RoundedCornerShape(28.dp)
            )
            .border(1.dp, Lux.Line, RoundedCornerShape(28.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val d = size.minDimension
            val c = Offset(size.width / 2, size.height / 2)

            // טבעת פליז חיצונית דקה
            drawCircle(Lux.BrassDim, radius = d * 0.485f, center = c, style = Stroke(width = 1.2f))

            repeat(24) { h ->
                val a = Math.toRadians(h / 24.0 * 360 - 90)
                val major = h % 6 == 0
                val outer = d * 0.455f
                val inner = if (major) d * 0.415f else d * 0.44f
                drawLine(
                    color = if (major) Lux.Brass else Lux.Line,
                    start = c + Offset((outer * Math.cos(a)).toFloat(), (outer * Math.sin(a)).toFloat()),
                    end = c + Offset((inner * Math.cos(a)).toFloat(), (inner * Math.sin(a)).toFloat()),
                    strokeWidth = if (major) 2.5f else 1.2f, cap = StrokeCap.Round
                )
            }

            ring.forEachIndexed { i, m ->
                val r = d * 0.365f - i * d * 0.048f
                val col = Color(m.colorArgb.toInt())
                val isLive = active.any { it.id == m.id }
                val sweep = (((m.end - m.start) + 1440) % 1440) / 1440f * 360f

                drawArc(
                    color = col.copy(alpha = 0.10f), startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2), style = Stroke(width = 6f)
                )
                drawArc(
                    color = col.copy(alpha = if (isLive) 1f else 0.45f),
                    startAngle = m.start / 1440f * 360f - 90f, sweepAngle = sweep,
                    useCenter = false, topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = if (isLive) 9f else 6f, cap = StrokeCap.Round)
                )
            }

            val na = Math.toRadians(nowMinute / 1440.0 * 360 - 90)
            drawLine(
                color = Lux.BrassSoft,
                start = c + Offset((d * 0.16f * Math.cos(na)).toFloat(), (d * 0.16f * Math.sin(na)).toFloat()),
                end = c + Offset((d * 0.46f * Math.cos(na)).toFloat(), (d * 0.46f * Math.sin(na)).toFloat()),
                strokeWidth = 2f, cap = StrokeCap.Round
            )
            drawCircle(
                Lux.BrassSoft, radius = 4f,
                center = c + Offset((d * 0.46f * Math.cos(na)).toFloat(), (d * 0.46f * Math.sin(na)).toFloat())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmt(nowMinute), fontSize = 44.sp, fontWeight = FontWeight.ExtraLight,
                color = Lux.Text, letterSpacing = 2.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                if (active.isEmpty()) "אין מצב פעיל" else active.joinToString(" · ") { it.name },
                fontSize = 12.sp, letterSpacing = 1.sp,
                color = if (active.isEmpty()) Lux.Faint else Lux.BrassSoft
            )
        }
    }
}

/* ── שורת מצב ── */

@Composable
fun ModeRow(mode: Mode, live: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val color = Color(mode.colorArgb.toInt())
    Row(
        Modifier.fillMaxWidth()
            .background(if (live) Lux.SurfaceHi else Lux.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, if (live) color.copy(alpha = 0.5f) else Lux.Line, RoundedCornerShape(20.dp))
            .clickable { onOpen() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(42.dp)
                .background(color.copy(alpha = if (live) 0.22f else 0.10f), RoundedCornerShape(14.dp))
                .border(1.dp, color.copy(alpha = if (live) 0.55f else 0.18f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(mode.id), null, tint = color, modifier = Modifier.size(21.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(mode.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Lux.Text)
            Text(
                "${fmt(mode.start)} — ${fmt(mode.end)}   ${dayLabels(mode.days)}",
                fontSize = 12.sp, color = Lux.Faint, letterSpacing = 0.5.sp
            )
            when (mode.manualOverride) {
                true -> Text("פעיל ידנית", fontSize = 11.sp, color = Lux.Brass, letterSpacing = 1.sp)
                false -> Text("מושהה עד סוף החלון", fontSize = 11.sp, color = Lux.Faint, letterSpacing = 1.sp)
                else -> if (live) Text("פעיל עכשיו", fontSize = 11.sp, color = color, letterSpacing = 1.sp)
            }
        }

        Switch(checked = mode.enabled, onCheckedChange = { onToggle() }, colors = luxSwitch())
    }
}

/* ── מסך עריכה ── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(mode: Mode, onDismiss: () -> Unit, onChange: (Mode) -> Unit) {
    val color = Color(mode.colorArgb.toInt())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Lux.Surface,
        contentColor = Lux.Text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())   // בלי זה התוכן הארוך נחתך
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(46.dp)
                        .background(color.copy(alpha = 0.18f), RoundedCornerShape(15.dp))
                        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(iconFor(mode.id), null, tint = color, modifier = Modifier.size(23.dp)) }
                Text(mode.name, fontSize = 24.sp, fontWeight = FontWeight.Light, color = Lux.Text)
            }

            SectionHeader(Icons.Outlined.Tune, "עכשיו", top = 14)
            SegmentedRow(
                listOf("לפי הלוח" to null, "דלוק" to true, "כבוי" to false),
                mode.manualOverride, color
            ) { onChange(mode.copy(manualOverride = it)) }

            SectionHeader(Icons.Outlined.Schedule, "מתי", top = 14)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("התחלה", mode.start, Modifier.weight(1f)) { onChange(mode.copy(start = it)) }
                TimeField("סיום", mode.end, Modifier.weight(1f)) { onChange(mode.copy(end = it)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("א" to 1, "ב" to 2, "ג" to 3, "ד" to 4, "ה" to 5, "ו" to 6, "ש" to 7)
                    .forEach { (label, d) ->
                        val on = mode.days.contains(d)
                        Box(
                            Modifier.weight(1f).height(42.dp)
                                .background(if (on) color.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .border(1.dp, if (on) color.copy(alpha = 0.55f) else Lux.Line, RoundedCornerShape(12.dp))
                                .clickable {
                                    onChange(mode.copy(days = if (on) mode.days - d else mode.days + d))
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (on) color else Lux.Faint, fontSize = 13.sp) }
                    }
            }

            SectionHeader(Icons.Outlined.PhoneCallback, "שיחות נכנסות", top = 14)

            ToggleRow("סינון שיחות במצב הזה", mode.actions.contains(Actions.CALL_GUARD)) {
                onChange(mode.copy(actions = if (it) mode.actions + Actions.CALL_GUARD else mode.actions - Actions.CALL_GUARD))
            }

            if (mode.actions.contains(Actions.CALL_GUARD)) {
                SegmentedRow(
                    listOf("לדחות מיד" to CallHandling.REJECT, "להשתיק בשקט" to CallHandling.SILENCE),
                    mode.call.handling, color
                ) { onChange(mode.copy(call = mode.call.copy(handling = it))) }

                ToggleRow("לשלוח הודעה למתקשר", mode.call.sendSms) {
                    onChange(mode.copy(call = mode.call.copy(sendSms = it)))
                }

                if (mode.call.sendSms) {
                    OutlinedTextField(
                        value = mode.call.message,
                        onValueChange = { onChange(mode.copy(call = mode.call.copy(message = it))) },
                        label = { Text("ההודעה שתישלח", color = Lux.Muted) },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lux.Brass,
                            unfocusedBorderColor = Lux.Line,
                            focusedTextColor = Lux.Text,
                            unfocusedTextColor = Lux.Text,
                            cursorColor = Lux.Brass
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${mode.call.message.length} תווים · ${(mode.call.message.length / 70) + 1} הודעות SMS",
                        fontSize = 11.sp, color = Lux.Faint
                    )
                }

                ToggleRow("לתת לאנשי קשר לצלצל", mode.call.allowContacts) {
                    onChange(mode.copy(call = mode.call.copy(allowContacts = it)))
                }

                SectionHeader(Icons.Outlined.NotificationsActive, "פריצה בחיוג חוזר", top = 14)
                ToggleRow("להפעיל פריצה", mode.call.breakthrough.enabled) {
                    onChange(mode.copy(call = mode.call.copy(breakthrough = mode.call.breakthrough.copy(enabled = it))))
                }
                if (mode.call.breakthrough.enabled) {
                    StepperRow("אחרי כמה חיוגים", mode.call.breakthrough.attempts, 2..6, "חיוגים") {
                        onChange(mode.copy(call = mode.call.copy(breakthrough = mode.call.breakthrough.copy(attempts = it))))
                    }
                    StepperRow("בתוך כמה זמן", mode.call.breakthrough.windowMinutes, 5..30, "דקות", 5) {
                        onChange(mode.copy(call = mode.call.copy(breakthrough = mode.call.breakthrough.copy(windowMinutes = it))))
                    }
                }

                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .background(Lux.Bg, RoundedCornerShape(14.dp))
                        .border(1.dp, Lux.Line, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        buildString {
                            append("שיחה ממספר לא מוכר ")
                            append(if (mode.call.handling == CallHandling.SILENCE) "תושתק בשקט" else "תידחה")
                            append(if (mode.call.sendSms) ", ותישלח ההודעה שלמעלה." else ", בלי הודעה.")
                            if (mode.call.allowContacts) append(" אנשי קשר יצלצלו כרגיל.")
                            if (mode.call.breakthrough.enabled) {
                                append(" ${mode.call.breakthrough.attempts} חיוגים תוך ")
                                append("${mode.call.breakthrough.windowMinutes} דקות יפרצו את ההשתקה.")
                            }
                        },
                        fontSize = 12.sp, color = Lux.Muted, lineHeight = 19.sp
                    )
                }
            }

            SectionHeader(Icons.Outlined.Tune, "פעולות נוספות", top = 14)
            ToggleRow("נא לא להפריע", mode.actions.contains(Actions.DND)) {
                onChange(mode.copy(actions = if (it) mode.actions + Actions.DND else mode.actions - Actions.DND))
            }
            ToggleRow("השתקת צלצול", mode.actions.contains(Actions.MUTE)) {
                onChange(mode.copy(actions = if (it) mode.actions + Actions.MUTE else mode.actions - Actions.MUTE))
            }
        }
    }
}

/* ── הרשאות: כפתור בתחתית + חלון צף ── */

@Composable
fun TriggerRow(icon: ImageVector, title: String, subtitle: String, tint: Color = Lux.Brass, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Lux.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, Lux.Line, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Lux.Text)
            Text(subtitle, fontSize = 12.sp, color = Lux.Faint)
        }
        Text("‹", fontSize = 22.sp, color = Lux.Faint)
    }
}

@Composable
fun PermissionsTrigger(granted: Int, onClick: () -> Unit) {
    val complete = granted == Permissions.all.size
    Row(
        Modifier.fillMaxWidth()
            .background(Lux.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, if (complete) Lux.Ok.copy(alpha = 0.4f) else Lux.Line, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.Security, null, tint = if (complete) Lux.Ok else Lux.Brass,
            modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("הרשאות במכשיר", fontSize = 15.sp, color = Lux.Text)
            Text(
                if (complete) "הכל מאושר" else "$granted מתוך ${Permissions.all.size} אושרו",
                fontSize = 12.sp, color = if (complete) Lux.Ok else Lux.Faint
            )
        }
        Text("‹", fontSize = 22.sp, color = Lux.Faint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSheet(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { tick++; onChanged() }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++; onChanged() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Lux.Surface,
        contentColor = Lux.Text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("הרשאות במכשיר", fontSize = 24.sp, fontWeight = FontWeight.Light, color = Lux.Text)
            Text(
                "כל אחת נדרשת פעם אחת בלבד, ונשארת גם אחרי הפעלה מחדש.",
                fontSize = 12.sp, color = Lux.Faint
            )
            Spacer(Modifier.height(6.dp))

            Permissions.all.forEach { p ->
                val granted = remember(tick, p.id) { runCatching { p.isGranted(ctx) }.getOrDefault(false) }
                Column(
                    Modifier.fillMaxWidth()
                        .background(Lux.Bg, RoundedCornerShape(16.dp))
                        .border(1.dp, if (granted) Lux.Ok.copy(alpha = 0.35f) else Lux.Line, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(p.title, fontSize = 15.sp, color = Lux.Text)
                    Text(p.subtitle, fontSize = 11.sp, color = Lux.Faint)
                    Text("פותח: ${p.unlocks}", fontSize = 11.sp, color = Lux.Faint)

                    p.adbCommand?.takeIf { !granted }?.let { cmd ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            cmd, fontSize = 10.sp, color = Lux.BrassSoft, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                                .background(Color.Black, RoundedCornerShape(10.dp))
                                .border(1.dp, Lux.Line, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        )
                        TextButton(onClick = { copyText(ctx, cmd) }) {
                            Text("העתקת הפקודה", color = Lux.Brass, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (granted) {
                        Text("אושר", color = Lux.Ok, fontSize = 12.sp, letterSpacing = 1.sp)
                    } else {
                        val opener = p.intentFor
                        OutlinedButton(
                            onClick = {
                                when {
                                    p.runtimePermissions.isNotEmpty() -> runtimeLauncher.launch(p.runtimePermissions)
                                    opener != null -> settingsLauncher.launch(opener(ctx))
                                }
                            },
                            enabled = p.runtimePermissions.isNotEmpty() || opener != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.Brass)
                        ) { Text("אישור", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

/* ── יומן שיחות ── */

@Composable
fun CallLogCard(refreshKey: Int) {
    val ctx = LocalContext.current
    var events by remember(refreshKey) { mutableStateOf(CallLogStore.all(ctx)) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        Modifier.fillMaxWidth()
            .background(Lux.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, Lux.Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (events.isEmpty()) {
            Text(
                "עדיין לא טופלה שום שיחה. אחרי הראשונה יופיע כאן מה בדיוק קרה איתה.",
                fontSize = 12.sp, color = Lux.Faint, lineHeight = 19.sp
            )
        } else {
            events.take(6).forEach { e ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.number, fontSize = 14.sp, color = Lux.Text)
                        Text(clock.format(Date(e.timeMillis)), fontSize = 12.sp, color = Lux.Faint)
                    }
                    Text(
                        "${e.outcome} · ${e.modeName}" + if (e.smsSent) " · נשלחה הודעה" else "",
                        fontSize = 12.sp, color = Lux.Muted
                    )
                }
            }
            TextButton(onClick = { CallLogStore.clear(ctx); events = emptyList() }) {
                Text("ניקוי היומן", color = Lux.Faint, fontSize = 12.sp)
            }
        }
    }
}

/* ── רכיבים משותפים ── */

@Composable
private fun TimeField(label: String, minutes: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier
            .background(Lux.Bg, RoundedCornerShape(14.dp))
            .border(1.dp, Lux.Line, RoundedCornerShape(14.dp))
            .clickable {
                TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
            }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = Lux.Faint, letterSpacing = 1.sp)
        Text(fmt(minutes), fontSize = 20.sp, fontWeight = FontWeight.Light, color = Lux.Text)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Lux.Text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = luxSwitch())
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<Pair<String, T>>, selected: T, color: Color, onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(44.dp)
                    .background(if (on) color.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(13.dp))
                    .border(1.dp, if (on) color.copy(alpha = 0.6f) else Lux.Line, RoundedCornerShape(13.dp))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) color else Lux.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String, value: Int, range: IntRange, unit: String, step: Int = 1, onChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Lux.Text, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value - step >= range.first) onChange(value - step) }) {
            Text("−", color = Lux.Brass, fontSize = 18.sp)
        }
        Text("$value $unit", fontSize = 13.sp, color = Lux.Text)
        TextButton(onClick = { if (value + step <= range.last) onChange(value + step) }) {
            Text("+", color = Lux.Brass, fontSize = 18.sp)
        }
    }
}

private fun copyText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("adb", text))
}

fun fmt(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

fun dayLabels(days: Set<Int>): String {
    val names = mapOf(1 to "א", 2 to "ב", 3 to "ג", 4 to "ד", 5 to "ה", 6 to "ו", 7 to "ש")
    return days.sorted().mapNotNull { names[it] }.joinToString(" ")
}
