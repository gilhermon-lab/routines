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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
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
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gil.routines.BuildConfig
import com.gil.routines.calendar.CalendarReader
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
            var themeMode by remember { mutableStateOf(ThemePrefs.load(this)) }
            var minuteTick by remember { mutableStateOf(java.time.LocalTime.now()) }

            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(60_000)
                    minuteTick = java.time.LocalTime.now()
                }
            }

            val dimming = remember(minuteTick, themeMode) {
                RoutineEngine.activeModes(this@MainActivity).any { it.screen.dimEnabled }
            }
            val night = when (themeMode) {
                ThemeMode.DAY -> false
                ThemeMode.NIGHT -> true
                ThemeMode.AUTO -> dimming || minuteTick.hour >= 19 || minuteTick.hour < 7
            }

            SideEffect { Lux.palette = if (night) LuxNight else LuxDay }

            MaterialTheme(
                colorScheme = if (night) darkColorScheme(
                    primary = LuxNight.brass, onPrimary = LuxNight.bg,
                    background = LuxNight.bg, surface = LuxNight.surface,
                    onSurface = LuxNight.text, onBackground = LuxNight.text
                ) else lightColorScheme(
                    primary = LuxDay.brass, onPrimary = Color.White,
                    background = LuxDay.bg, surface = LuxDay.surface,
                    onSurface = LuxDay.text, onBackground = LuxDay.text
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RoutinesScreen(
                        themeMode = themeMode,
                        onThemeChange = {
                            themeMode = it
                            ThemePrefs.save(this@MainActivity, it)
                        }
                    )
                }
            }
        }
    }
}

/** למה המצב פעיל כרגע — או למה לא */
data class ModeStatus(val live: Boolean, val label: String, val detail: String, val manual: Boolean)

fun modeStatus(ctx: android.content.Context, m: Mode): ModeStatus {
    val now = java.time.ZonedDateTime.now()
    val minute = now.hour * 60 + now.minute
    val day = now.dayOfWeek.value % 7 + 1
    val bySchedule = m.copy(manualOverride = null).isActiveAt(minute, day)
    val event = if (m.calendar.enabled) {
        runCatching { CalendarReader.activeNow(ctx, m.calendar) }.getOrNull()
    } else null

    return when {
        m.manualOverride == true ->
            ModeStatus(true, "פעיל ידנית", "יישאר פעיל עד שתכבה אותו", true)
        m.manualOverride == false ->
            ModeStatus(false, "מושהה ידנית", "יחזור לפעול לפי הלוח בסוף החלון", true)
        !m.enabled ->
            ModeStatus(false, "כבוי", "המצב מושבת לגמרי", false)
        event != null ->
            ModeStatus(true, "פעיל לפי יומן", event.title, false)
        bySchedule ->
            ModeStatus(true, "פעיל לפי הלוח", fmt(m.start) + " — " + fmt(m.end), false)
        else ->
            ModeStatus(false, "לא פעיל כרגע", "החלון הבא: " + fmt(m.start) + " — " + fmt(m.end), false)
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
        Icon(icon, null, tint = Lux.brass, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Lux.muted, letterSpacing = 2.sp)
        HorizontalDivider(color = Lux.line, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun luxSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Lux.bg,
    checkedTrackColor = Lux.brass,
    checkedBorderColor = Lux.brass,
    uncheckedThumbColor = Lux.faint,
    uncheckedTrackColor = Lux.surface,
    uncheckedBorderColor = Lux.line
)

/* ────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val ctx = LocalContext.current
    var modes by remember { mutableStateOf(ModeStore.load(ctx)) }
    var editing by remember { mutableStateOf<String?>(null) }
    var showPerms by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex < 2 } }
    var tick by remember { mutableIntStateOf(0) }

    // הרשאות משתנות גם מחוץ לאפליקציה, לכן סופרים מחדש בכל חזרה למסך
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

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

    Scaffold(
        containerColor = Lux.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "שגרות", fontSize = 22.sp, fontWeight = FontWeight.Light,
                            color = Lux.text, letterSpacing = 1.sp
                        )
                        Text(
                            if (active.isEmpty()) "אין מצב פעיל"
                            else active.joinToString(" · ") { it.name },
                            fontSize = 11.sp,
                            color = if (active.isEmpty()) Lux.faint else Lux.brass
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "הגדרות", tint = Lux.brass)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Lux.bg,
                    titleContentColor = Lux.text,
                    actionIconContentColor = Lux.brass
                )
            )
        },
        floatingActionButton = {
            if (!atTop) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = Lux.surfaceHi,
                    contentColor = Lux.brass,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "חזרה למעלה")
                }
            }
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(pad).navigationBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp)
        ) {
            item {
                // ישיבות היום, לציור על החוגה
                val todaySpans = remember(modes, tick) {
                    val zone = java.time.ZoneId.systemDefault()
                    val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone)
                    val from = startOfDay.toInstant().toEpochMilli()
                    val to = startOfDay.plusDays(1).toInstant().toEpochMilli()

                    modes.filter { it.calendar.enabled }
                        .flatMap { m -> CalendarReader.matching(ctx, m.calendar, from, to) }
                        .map { e ->
                            val b = java.time.Instant.ofEpochMilli(e.begin).atZone(zone)
                            val en = java.time.Instant.ofEpochMilli(e.end).atZone(zone)
                            val bMin = (b.hour * 60 + b.minute).toFloat()
                            val eMin = if (en.toLocalDate() != b.toLocalDate()) 1440f
                                       else (en.hour * 60 + en.minute).toFloat()
                            bMin to eMin.coerceAtLeast(bMin + 5f)
                        }
                }
                DayDial(modes, active, todaySpans)
                if (todaySpans.isNotEmpty()) {
                    Text(
                        "${todaySpans.size} ישיבות היום מסומנות על החוגה",
                        fontSize = 11.sp, color = Lux.faint,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

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

        }
    }

    editing?.let { id ->
        modes.find { it.id == id }?.let { mode ->
            ModeSheet(
                mode = mode,
                onCancel = { editing = null },
                onSave = { updated ->
                    mutate(id) { updated }
                    editing = null          // חוזרים ללוח הראשי אחרי שמירה
                }
            )
        }
    }

    if (showPerms) {
        PermissionsSheet(onDismiss = { showPerms = false }, onChanged = { tick++ })
    }

    if (showDiag) {
        DiagnosticsSheet(onDismiss = { showDiag = false })
    }

    if (showSettings) {
        SettingsSheet(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            grantedCount = grantedCount,
            onPermissions = { showSettings = false; showPerms = true },
            onDiagnostics = { showSettings = false; showDiag = true },
            onDismiss = { showSettings = false }
        )
    }
}

/* ── חוגת 24 שעות ── */

@Composable
fun DayDial(modes: List<Mode>, active: List<Mode>, meetingSpans: List<Pair<Float, Float>> = emptyList()) {
    val now = ZonedDateTime.now()
    val nowMinute = now.hour * 60 + now.minute
    val ring = modes.filter { it.enabled }

    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.radialGradient(listOf(Lux.surfaceHi, Lux.bg)),
                RoundedCornerShape(28.dp)
            )
            .border(1.dp, Lux.line, RoundedCornerShape(28.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val d = size.minDimension
            val c = Offset(size.width / 2, size.height / 2)

            // טבעת פליז חיצונית דקה
            drawCircle(Lux.brassDim, radius = d * 0.485f, center = c, style = Stroke(width = 1.2f))

            repeat(24) { h ->
                val a = Math.toRadians(h / 24.0 * 360 - 90)
                val major = h % 6 == 0
                val outer = d * 0.455f
                val inner = if (major) d * 0.415f else d * 0.44f
                drawLine(
                    color = if (major) Lux.brass else Lux.line,
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
                    color = col.copy(alpha = 0.16f), startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2), style = Stroke(width = 9f)
                )
                drawArc(
                    color = col.copy(alpha = if (isLive) 1f else 0.72f),
                    startAngle = m.start / 1440f * 360f - 90f, sweepAngle = sweep,
                    useCenter = false, topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = if (isLive) 13f else 10f, cap = StrokeCap.Round)
                )
            }

            // טבעת הישיבות: קשת דקה לכל ישיבה מאושרת היום
            meetingSpans.forEach { (fromMin, toMin) ->
                val r = d * 0.425f
                drawArc(
                    color = Lux.brass,
                    startAngle = fromMin / 1440f * 360f - 90f,
                    sweepAngle = ((toMin - fromMin) / 1440f * 360f).coerceAtLeast(2f),
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }

            val na = Math.toRadians(nowMinute / 1440.0 * 360 - 90)
            drawLine(
                color = Lux.brassSoft,
                start = c + Offset((d * 0.16f * Math.cos(na)).toFloat(), (d * 0.16f * Math.sin(na)).toFloat()),
                end = c + Offset((d * 0.46f * Math.cos(na)).toFloat(), (d * 0.46f * Math.sin(na)).toFloat()),
                strokeWidth = 2f, cap = StrokeCap.Round
            )
            drawCircle(
                Lux.brassSoft, radius = 4f,
                center = c + Offset((d * 0.46f * Math.cos(na)).toFloat(), (d * 0.46f * Math.sin(na)).toFloat())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmt(nowMinute), fontSize = 44.sp, fontWeight = FontWeight.ExtraLight,
                color = Lux.text, letterSpacing = 2.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                if (active.isEmpty()) "אין מצב פעיל" else active.joinToString(" · ") { it.name },
                fontSize = 12.sp, letterSpacing = 1.sp,
                color = if (active.isEmpty()) Lux.faint else Lux.brassSoft
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
            .background(if (live) Lux.surfaceHi else Lux.surface, RoundedCornerShape(20.dp))
            .border(1.dp, if (live) color.copy(alpha = 0.5f) else Lux.line, RoundedCornerShape(20.dp))
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
            Text(mode.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Lux.text)
            Text(
                "${fmt(mode.start)} — ${fmt(mode.end)}   ${dayLabels(mode.days)}",
                fontSize = 12.sp, color = Lux.faint, letterSpacing = 0.5.sp
            )
            val st = modeStatus(LocalContext.current, mode)
            Text(
                st.label, fontSize = 11.sp, letterSpacing = 1.sp,
                color = when {
                    st.manual -> Lux.brass
                    st.live -> color
                    else -> Lux.faint
                }
            )
        }

        Switch(checked = mode.enabled, onCheckedChange = { onToggle() }, colors = luxSwitch())
    }
}

/* ── מסך עריכה ── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(mode: Mode, onCancel: () -> Unit, onSave: (Mode) -> Unit) {
    val ctx = LocalContext.current
    // טיוטה מקומית: כל עריכה נוגעת בה בלבד, ורק "שמירה" מחילה אותה על המצב האמיתי.
    // זה גם מה שמנע קודם מהמסך הראשי להציג ערכים ישנים.
    var draft by remember(mode.id) { mutableStateOf(mode) }
    val color = Color(draft.colorArgb.toInt())
    val dirty = draft != mode

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Lux.surface,
        contentColor = Lux.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxHeight(0.94f)) {

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
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
                    ) { Icon(iconFor(draft.id), null, tint = color, modifier = Modifier.size(23.dp)) }
                    Text(draft.name, fontSize = 24.sp, fontWeight = FontWeight.Light, color = Lux.text)
                }

                // מצב נוכחי ומקורו — ידני, לוח או יומן
                val st = modeStatus(ctx, draft)
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            if (st.live) color.copy(alpha = 0.12f) else Lux.bg,
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (st.live) color.copy(alpha = 0.5f) else Lux.line,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(10.dp).background(
                            if (st.live) color else Lux.faint,
                            RoundedCornerShape(5.dp)
                        )
                    )
                    Column(Modifier.weight(1f)) {
                        Text(st.label, fontSize = 14.sp, color = Lux.text)
                        Text(st.detail, fontSize = 11.sp, color = Lux.muted, lineHeight = 16.sp)
                    }
                }

                SectionHeader(Icons.Outlined.Tune, "עכשיו", top = 14)
                SegmentedRow(
                    listOf("לפי הלוח" to null, "דלוק" to true, "כבוי" to false),
                    draft.manualOverride, color
                ) { draft = draft.copy(manualOverride = it) }

                SectionHeader(Icons.Outlined.Schedule, "מתי", top = 14)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField("התחלה", draft.start, Modifier.weight(1f)) { draft = draft.copy(start = it) }
                    TimeField("סיום", draft.end, Modifier.weight(1f)) { draft = draft.copy(end = it) }
                }

                // קיצורים לבחירת ימים
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "כל יום" to setOf(1, 2, 3, 4, 5, 6, 7),
                        "ימי חול" to setOf(1, 2, 3, 4, 5),
                        "סוף שבוע" to setOf(6, 7)
                    ).forEach { (label, days) ->
                        val on = draft.days == days
                        Box(
                            Modifier.weight(1f).height(36.dp)
                                .background(if (on) color.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(11.dp))
                                .border(1.dp, if (on) color.copy(alpha = 0.6f) else Lux.line, RoundedCornerShape(11.dp))
                                .clickable { draft = draft.copy(days = days) },
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (on) color else Lux.muted, fontSize = 12.sp) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("א" to 1, "ב" to 2, "ג" to 3, "ד" to 4, "ה" to 5, "ו" to 6, "ש" to 7)
                        .forEach { (label, d) ->
                            val on = draft.days.contains(d)
                            Box(
                                Modifier.weight(1f).height(42.dp)
                                    .background(if (on) color.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(12.dp))
                                    .border(1.dp, if (on) color.copy(alpha = 0.55f) else Lux.line, RoundedCornerShape(12.dp))
                                    .clickable {
                                        draft = draft.copy(days = if (on) draft.days - d else draft.days + d)
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = if (on) color else Lux.faint, fontSize = 13.sp) }
                        }
                }

                if (draft.days.isEmpty()) {
                    Text(
                        "לא נבחר אף יום — המצב לא יופעל לפי הלוח.",
                        fontSize = 11.sp, color = Lux.brass
                    )
                }

                // ── יומן ──
                SectionHeader(Icons.Outlined.CalendarMonth, "יומן", top = 14)

                ToggleRow("להפעיל לפי אירועים ביומן", draft.calendar.enabled) {
                    draft = draft.copy(calendar = draft.calendar.copy(enabled = it))
                }

                if (draft.calendar.enabled) {
                    if (!CalendarReader.hasPermission(ctx)) {
                        Text(
                            "חסרה הרשאת יומן. אשר אותה במסך ההרשאות שבתחתית הלוח הראשי.",
                            fontSize = 11.sp, color = Lux.brass, lineHeight = 17.sp
                        )
                    } else {
                        val cals = remember { CalendarReader.calendars(ctx) }
                        Text(
                            "נספרים אירועים שאינם יום שלם ושלא דחית. הסינון נעשה לפי המילים שלמטה ולפי הבחירה הידנית.",
                            fontSize = 11.sp, color = Lux.faint, lineHeight = 17.sp
                        )

                        var calsOpen by remember { mutableStateOf(draft.calendar.calendarIds.isEmpty()) }

                        if (cals.isEmpty()) {
                            Text(
                                "לא נמצא יומן מסונכרן במכשיר. ודא שיומן Outlook מסונכרן ליומן של הטלפון.",
                                fontSize = 11.sp, color = Lux.brass, lineHeight = 17.sp
                            )
                        } else {
                            // כותרת מתקפלת — הרשימה ארוכה ולא צריך אותה פתוחה תמיד
                            CollapsibleHeader(
                                title = "יומנים",
                                summary = if (draft.calendar.calendarIds.isEmpty())
                                    "כל היומנים (${cals.size})"
                                else "${draft.calendar.calendarIds.size} מתוך ${cals.size} נבחרו",
                                open = calsOpen,
                                accent = color
                            ) { calsOpen = !calsOpen }

                            if (calsOpen) cals.forEach { cal ->
                                val on = draft.calendar.calendarIds.contains(cal.id)
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(Lux.bg, RoundedCornerShape(12.dp))
                                        .border(1.dp, if (on) color.copy(alpha = 0.5f) else Lux.line, RoundedCornerShape(12.dp))
                                        .clickable {
                                            val ids = draft.calendar.calendarIds
                                            draft = draft.copy(
                                                calendar = draft.calendar.copy(
                                                    calendarIds = if (on) ids - cal.id else ids + cal.id
                                                )
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cal.name, fontSize = 13.sp, color = Lux.text)
                                        Text(cal.account, fontSize = 10.sp, color = Lux.faint)
                                        if (!cal.visible || !cal.syncing) {
                                            Text(
                                                buildString {
                                                    if (!cal.visible) append("מוסתר ביומן")
                                                    if (!cal.visible && !cal.syncing) append(" · ")
                                                    if (!cal.syncing) append("סנכרון כבוי")
                                                },
                                                fontSize = 10.sp, color = Lux.brass
                                            )
                                        }
                                    }
                                    if (on) Text("נבחר", fontSize = 11.sp, color = color)
                                }
                            }

                            // ── מילות מפתח ──
                            Spacer(Modifier.height(6.dp))
                            ToggleRow("רק אירועים שהכותרת שלהם מכילה מילה", draft.calendar.requireKeyword) {
                                draft = draft.copy(calendar = draft.calendar.copy(requireKeyword = it))
                            }

                            if (draft.calendar.requireKeyword) {
                                var newWord by remember { mutableStateOf("") }

                                draft.calendar.keywords.chunked(3).forEach { rowWords ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowWords.forEach { w ->
                                        Row(
                                            Modifier
                                                .background(color.copy(alpha = 0.16f), RoundedCornerShape(99.dp))
                                                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
                                                .clickable {
                                                    draft = draft.copy(
                                                        calendar = draft.calendar.copy(
                                                            keywords = draft.calendar.keywords - w
                                                        )
                                                    )
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(w, fontSize = 12.sp, color = color)
                                            Spacer(Modifier.width(6.dp))
                                            Text("×", fontSize = 13.sp, color = Lux.faint)
                                        }
                                    }
                                }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newWord,
                                        onValueChange = { newWord = it },
                                        label = { Text("מילה נוספת", color = Lux.muted) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Lux.brass,
                                            unfocusedBorderColor = Lux.line,
                                            focusedTextColor = Lux.text,
                                            unfocusedTextColor = Lux.text,
                                            cursorColor = Lux.brass
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val w = newWord.trim()
                                            if (w.isNotBlank() && !draft.calendar.keywords.contains(w)) {
                                                draft = draft.copy(
                                                    calendar = draft.calendar.copy(
                                                        keywords = draft.calendar.keywords + w
                                                    )
                                                )
                                            }
                                            newWord = ""
                                        },
                                        enabled = newWord.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.brass)
                                    ) { Text("הוספה", fontSize = 13.sp) }
                                }

                                Text(
                                    "ההשוואה מתעלמת מגרשיים ומרווחים, כך ש-פ\"ע ו-פ״ע נחשבים זהים.",
                                    fontSize = 10.sp, color = Lux.faint, lineHeight = 15.sp
                                )
                            }

                            // ── בחירה ידנית לאירוע בודד ──
                            val upcoming = remember(draft.calendar) {
                                CalendarReader.upcomingAll(ctx, draft.calendar)
                            }
                            if (upcoming.isNotEmpty()) {
                                var eventsOpen by remember { mutableStateOf(false) }
                                val chosen = upcoming.count { CalendarReader.matches(draft.calendar, it) }

                                Spacer(Modifier.height(6.dp))
                                CollapsibleHeader(
                                    title = "אירועים קרובים",
                                    summary = "$chosen מתוך ${upcoming.size} יפעילו את המצב",
                                    open = eventsOpen,
                                    accent = color
                                ) { eventsOpen = !eventsOpen }

                                if (eventsOpen) Text(
                                    "לחיצה על אירוע קובעת אותו ידנית, בלי קשר למילות המפתח.",
                                    fontSize = 10.sp, color = Lux.faint
                                )

                                val fmtDay = remember { SimpleDateFormat("EEE HH:mm", Locale("he")) }
                                if (eventsOpen) upcoming.take(12).forEach { ev ->
                                    val on = CalendarReader.matches(draft.calendar, ev)
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .background(Lux.bg, RoundedCornerShape(12.dp))
                                            .border(
                                                1.dp,
                                                if (on) color.copy(alpha = 0.5f) else Lux.line,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                val c0 = draft.calendar
                                                draft = draft.copy(
                                                    calendar = if (on)
                                                        c0.copy(
                                                            forcedOff = c0.forcedOff + ev.key,
                                                            forcedOn = c0.forcedOn - ev.key
                                                        )
                                                    else
                                                        c0.copy(
                                                            forcedOn = c0.forcedOn + ev.key,
                                                            forcedOff = c0.forcedOff - ev.key
                                                        )
                                                )
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = on,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = color,
                                                uncheckedColor = Lux.faint,
                                                checkmarkColor = Lux.bg
                                            )
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(ev.title, fontSize = 13.sp, color = Lux.text)
                                            Text(
                                                fmtDay.format(Date(ev.begin)),
                                                fontSize = 10.sp, color = Lux.faint
                                            )
                                        }
                                    }
                                }
                            }

                            val next = remember(draft.calendar) {
                                CalendarReader.nextMatching(ctx, draft.calendar)
                            }
                            val nowEv = remember(draft.calendar) {
                                CalendarReader.activeNow(ctx, draft.calendar)
                            }
                            Text(
                                when {
                                    nowEv != null -> "כרגע: ${nowEv.title}"
                                    next != null -> "הבא: ${next.title} בשעה " +
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next.begin))
                                    else -> "אין אירוע קרוב שעונה לתנאים"
                                },
                                fontSize = 12.sp, color = Lux.brassSoft
                            )
                        }
                    }
                }

                SectionHeader(Icons.Outlined.PhoneCallback, "שיחות נכנסות", top = 14)

                ToggleRow("סינון שיחות במצב הזה", draft.actions.contains(Actions.CALL_GUARD)) {
                    draft = draft.copy(actions = if (it) draft.actions + Actions.CALL_GUARD else draft.actions - Actions.CALL_GUARD)
                }

                if (draft.actions.contains(Actions.CALL_GUARD)) {
                    SegmentedRow(
                        listOf("לדחות מיד" to CallHandling.REJECT, "להשתיק בשקט" to CallHandling.SILENCE),
                        draft.call.handling, color
                    ) { draft = draft.copy(call = draft.call.copy(handling = it)) }

                    ToggleRow("לשלוח הודעה למתקשר", draft.call.sendSms) {
                        draft = draft.copy(call = draft.call.copy(sendSms = it))
                    }

                    if (draft.call.sendSms) {
                        OutlinedTextField(
                            value = draft.call.message,
                            onValueChange = { draft = draft.copy(call = draft.call.copy(message = it)) },
                            label = { Text("ההודעה שתישלח", color = Lux.muted) },
                            minLines = 2,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Lux.brass,
                                unfocusedBorderColor = Lux.line,
                                focusedTextColor = Lux.text,
                                unfocusedTextColor = Lux.text,
                                cursorColor = Lux.brass
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${draft.call.message.length} תווים · ${(draft.call.message.length / 70) + 1} הודעות SMS",
                            fontSize = 11.sp, color = Lux.faint
                        )
                    }

                    SectionHeader(Icons.Outlined.PersonAdd, "מי בכל זאת יצלצל", top = 14)
                    SegmentedRow(
                        listOf(
                            "כל אנשי הקשר" to ContactPolicy.ALL,
                            "רשימה נבחרת" to ContactPolicy.LIST,
                            "אף אחד" to ContactPolicy.NONE
                        ),
                        draft.call.contactPolicy, color
                    ) { draft = draft.copy(call = draft.call.copy(contactPolicy = it)) }

                    if (draft.call.contactPolicy == ContactPolicy.LIST) {
                        var showPicker by remember { mutableStateOf(false) }

                        if (showPicker) {
                            ContactMultiPicker(
                                already = draft.call.allowed,
                                accent = color,
                                onDismiss = { showPicker = false },
                                onConfirm = { chosen ->
                                    draft = draft.copy(call = draft.call.copy(allowed = chosen))
                                    showPicker = false
                                }
                            )
                        }

                        var contactsOpen by remember { mutableStateOf(false) }
                        CollapsibleHeader(
                            title = "אנשי קשר מאושרים",
                            summary = if (draft.call.allowed.isEmpty()) "הרשימה ריקה"
                                      else "${draft.call.allowed.size} ברשימה",
                            open = contactsOpen,
                            accent = color
                        ) { contactsOpen = !contactsOpen }

                        if (contactsOpen) draft.call.allowed.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(Lux.bg, RoundedCornerShape(12.dp))
                                    .border(1.dp, Lux.line, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontSize = 14.sp, color = Lux.text)
                                    Text(c.number, fontSize = 11.sp, color = Lux.faint)
                                }
                                TextButton(onClick = {
                                    draft = draft.copy(
                                        call = draft.call.copy(allowed = draft.call.allowed - c)
                                    )
                                }) { Text("הסרה", color = Lux.faint, fontSize = 12.sp) }
                            }
                        }

                        if (contactsOpen) OutlinedButton(
                            onClick = { showPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.brass)
                        ) {
                            Text(
                                if (draft.call.allowed.isEmpty()) "בחירת אנשי קשר"
                                else "עריכת הרשימה (${draft.call.allowed.size})",
                                fontSize = 13.sp
                            )
                        }

                        if (contactsOpen && draft.call.allowed.isEmpty()) {
                            Text(
                                "הרשימה ריקה — כרגע אף אחד לא יעבור.",
                                fontSize = 11.sp, color = Lux.brass
                            )
                        }
                    }

                    SectionHeader(Icons.Outlined.NotificationsActive, "פריצה בחיוג חוזר", top = 14)
                    ToggleRow("להפעיל פריצה", draft.call.breakthrough.enabled) {
                        draft = draft.copy(call = draft.call.copy(breakthrough = draft.call.breakthrough.copy(enabled = it)))
                    }
                    if (draft.call.breakthrough.enabled) {
                        StepperRow("אחרי כמה חיוגים", draft.call.breakthrough.attempts, 2..6, "חיוגים") {
                            draft = draft.copy(call = draft.call.copy(breakthrough = draft.call.breakthrough.copy(attempts = it)))
                        }
                        StepperRow("בתוך כמה זמן", draft.call.breakthrough.windowMinutes, 5..30, "דקות", 5) {
                            draft = draft.copy(call = draft.call.copy(breakthrough = draft.call.breakthrough.copy(windowMinutes = it)))
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Lux.bg, RoundedCornerShape(14.dp))
                            .border(1.dp, Lux.line, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            buildString {
                                append("שיחה ממספר לא מוכר ")
                                append(if (draft.call.handling == CallHandling.SILENCE) "תושתק בשקט" else "תידחה")
                                append(if (draft.call.sendSms) ", ותישלח ההודעה שלמעלה." else ", בלי הודעה.")
                                when (draft.call.contactPolicy) {
                                    ContactPolicy.ALL -> append(" כל אנשי הקשר יצלצלו כרגיל.")
                                    ContactPolicy.LIST -> append(
                                        if (draft.call.allowed.isEmpty()) " אף אחד לא ברשימת ההיתר."
                                        else " יעברו: " + draft.call.allowed.joinToString(", ") { it.name } + "."
                                    )
                                    ContactPolicy.NONE -> append(" גם אנשי קשר ייחסמו.")
                                }
                                if (draft.call.breakthrough.enabled) {
                                    append(" ${draft.call.breakthrough.attempts} חיוגים תוך ")
                                    append("${draft.call.breakthrough.windowMinutes} דקות יפרצו את ההשתקה.")
                                }
                            },
                            fontSize = 12.sp, color = Lux.muted, lineHeight = 19.sp
                        )
                    }
                }

                // ── מסך ──
                SectionHeader(Icons.Outlined.Brightness4, "מסך", top = 14)

                ToggleRow("החשכת מסך", draft.screen.dimEnabled) {
                    draft = draft.copy(screen = draft.screen.copy(dimEnabled = it))
                }

                if (draft.screen.dimEnabled) {
                    Text(
                        "בהירות: ${draft.screen.brightnessPercent}%",
                        fontSize = 12.sp, color = Lux.muted
                    )
                    Slider(
                        value = draft.screen.brightnessPercent.toFloat(),
                        onValueChange = {
                            draft = draft.copy(screen = draft.screen.copy(brightnessPercent = it.toInt().coerceIn(1, 100)))
                        },
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = color,
                            activeTrackColor = color,
                            inactiveTrackColor = Lux.line
                        )
                    )
                    ToggleRow("לכבות בהירות אוטומטית", draft.screen.disableAdaptive) {
                        draft = draft.copy(screen = draft.screen.copy(disableAdaptive = it))
                    }
                    Text(
                        "הערכים המקוריים נשמרים ומוחזרים כשהמצב מסתיים.",
                        fontSize = 10.sp, color = Lux.faint
                    )
                }

                ToggleRow("לקצר זמן כיבוי מסך", draft.screen.timeoutEnabled) {
                    draft = draft.copy(screen = draft.screen.copy(timeoutEnabled = it))
                }
                if (draft.screen.timeoutEnabled) {
                    StepperRow("כיבוי אחרי", draft.screen.timeoutSeconds, 15..120, "שניות", 15) {
                        draft = draft.copy(screen = draft.screen.copy(timeoutSeconds = it))
                    }
                }

                ToggleRow("מסנן אור כחול", draft.screen.nightLight) {
                    draft = draft.copy(screen = draft.screen.copy(nightLight = it))
                }
                ToggleRow("גווני אפור", draft.screen.grayscale) {
                    draft = draft.copy(screen = draft.screen.copy(grayscale = it))
                }
                if (draft.screen.nightLight || draft.screen.grayscale) {
                    Text(
                        "שתי אלה דורשות את הרשאת ADB המוגנת. בלעדיה הן פשוט לא יקרו.",
                        fontSize = 10.sp, color = Lux.brass, lineHeight = 15.sp
                    )
                }

                SectionHeader(Icons.Outlined.Tune, "פעולות נוספות", top = 14)
                ToggleRow("נא לא להפריע", draft.actions.contains(Actions.DND)) {
                    draft = draft.copy(actions = if (it) draft.actions + Actions.DND else draft.actions - Actions.DND)
                }
                ToggleRow("השתקת צלצול", draft.actions.contains(Actions.MUTE)) {
                    draft = draft.copy(actions = if (it) draft.actions + Actions.MUTE else draft.actions - Actions.MUTE)
                }
                Spacer(Modifier.height(4.dp))

                Spacer(Modifier.height(16.dp))
            }

            // סרגל פעולות קבוע — תמיד נראה, לא נגלל עם התוכן
            Row(
                Modifier.fillMaxWidth()
                    .background(Lux.surfaceHi)
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.muted)
                ) { Text("ביטול", fontSize = 15.sp) }

                Button(
                    onClick = { onSave(draft) },
                    enabled = dirty,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lux.brass,
                        contentColor = Lux.bg,
                        disabledContainerColor = Lux.line,
                        disabledContentColor = Lux.faint
                    )
                ) { Text(if (dirty) "שמירה" else "אין שינויים", fontSize = 15.sp) }
            }
        }
    }
}

/* ── הגדרות ── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    grantedCount: Int,
    onPermissions: () -> Unit,
    onDiagnostics: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Lux.surface,
        contentColor = Lux.text,
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
            Text("הגדרות", fontSize = 24.sp, fontWeight = FontWeight.Light, color = Lux.text)

            SectionHeader(Icons.Outlined.Brightness4, "תצוגה", top = 12)
            SegmentedRow(
                listOf(
                    "אוטומטי" to ThemeMode.AUTO,
                    "יום" to ThemeMode.DAY,
                    "לילה" to ThemeMode.NIGHT
                ),
                themeMode, Lux.brass
            ) { onThemeChange(it) }
            Text(
                "אוטומטי: בהיר עד 19:00, כהה אחריו, וכהה בכל מצב שמחשיך את המסך.",
                fontSize = 11.sp, color = Lux.faint, lineHeight = 16.sp
            )

            SectionHeader(Icons.Outlined.Security, "מערכת", top = 12)
            PermissionsTrigger(grantedCount) { onPermissions() }
            Spacer(Modifier.height(2.dp))
            TriggerRow(
                Icons.Outlined.MonitorHeart, "בדיקת מערכת",
                "מה חסם הודעה, ושליחת הודעת בדיקה"
            ) { onDiagnostics() }

            Spacer(Modifier.height(10.dp))
            Text(
                "בנייה ${BuildConfig.BUILD_STAMP}",
                fontSize = 10.sp, color = Lux.faint
            )
        }
    }
}

/* ── הרשאות: כפתור בתחתית + חלון צף ── */

@Composable
fun TriggerRow(icon: ImageVector, title: String, subtitle: String, tint: Color = Lux.brass, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Lux.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Lux.line, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Lux.text)
            Text(subtitle, fontSize = 12.sp, color = Lux.faint)
        }
        Text("‹", fontSize = 22.sp, color = Lux.faint)
    }
}

@Composable
fun PermissionsTrigger(granted: Int, onClick: () -> Unit) {
    val complete = granted == Permissions.all.size
    Row(
        Modifier.fillMaxWidth()
            .background(Lux.surface, RoundedCornerShape(18.dp))
            .border(1.dp, if (complete) Lux.ok.copy(alpha = 0.4f) else Lux.line, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.Security, null, tint = if (complete) Lux.ok else Lux.brass,
            modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("הרשאות במכשיר", fontSize = 15.sp, color = Lux.text)
            Text(
                if (complete) "הכל מאושר" else "$granted מתוך ${Permissions.all.size} אושרו",
                fontSize = 12.sp, color = if (complete) Lux.ok else Lux.faint
            )
        }
        Text("‹", fontSize = 22.sp, color = Lux.faint)
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
        containerColor = Lux.surface,
        contentColor = Lux.text,
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
            Text("הרשאות במכשיר", fontSize = 24.sp, fontWeight = FontWeight.Light, color = Lux.text)
            Text(
                "כל אחת נדרשת פעם אחת בלבד, ונשארת גם אחרי הפעלה מחדש.",
                fontSize = 12.sp, color = Lux.faint
            )
            Spacer(Modifier.height(6.dp))

            Permissions.all.forEach { p ->
                val granted = remember(tick, p.id) { runCatching { p.isGranted(ctx) }.getOrDefault(false) }
                Column(
                    Modifier.fillMaxWidth()
                        .background(Lux.bg, RoundedCornerShape(16.dp))
                        .border(1.dp, if (granted) Lux.ok.copy(alpha = 0.35f) else Lux.line, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(p.title, fontSize = 15.sp, color = Lux.text)
                    Text(p.subtitle, fontSize = 11.sp, color = Lux.faint)
                    Text("פותח: ${p.unlocks}", fontSize = 11.sp, color = Lux.faint)

                    p.adbCommand?.takeIf { !granted }?.let { cmd ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            cmd, fontSize = 10.sp, color = Lux.brassSoft, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                                .background(Color.Black, RoundedCornerShape(10.dp))
                                .border(1.dp, Lux.line, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        )
                        TextButton(onClick = { copyText(ctx, cmd) }) {
                            Text("העתקת הפקודה", color = Lux.brass, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (granted) {
                        Text("אושר", color = Lux.ok, fontSize = 12.sp, letterSpacing = 1.sp)
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.brass)
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
            .background(Lux.surface, RoundedCornerShape(20.dp))
            .border(1.dp, Lux.line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var logOpen by remember { mutableStateOf(false) }

        if (events.isEmpty()) {
            Text(
                "עדיין לא טופלה שום שיחה. אחרי הראשונה יופיע כאן מה בדיוק קרה איתה.",
                fontSize = 12.sp, color = Lux.faint, lineHeight = 19.sp
            )
        } else {
            CollapsibleHeader(
                title = "שיחות אחרונות",
                summary = "${events.size} רשומות · האחרונה ב-${clock.format(Date(events.first().timeMillis))}",
                open = logOpen
            ) { logOpen = !logOpen }

            if (logOpen) events.take(6).forEach { e ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.number, fontSize = 14.sp, color = Lux.text)
                        Text(clock.format(Date(e.timeMillis)), fontSize = 12.sp, color = Lux.faint)
                    }
                    Text(
                        "${e.outcome} · ${e.modeName}" + if (e.smsSent) " · נשלחה הודעה" else "",
                        fontSize = 12.sp, color = Lux.muted
                    )
                }
            }
            if (logOpen) TextButton(onClick = { CallLogStore.clear(ctx); events = emptyList() }) {
                Text("ניקוי היומן", color = Lux.faint, fontSize = 12.sp)
            }
        }
    }
}

/* ── רכיבים משותפים ── */

/** כותרת אחידה לכל רשימה שאפשר לקפל: שם, סיכום קצר, ומתג הרחבה */
@Composable
fun CollapsibleHeader(
    title: String,
    summary: String,
    open: Boolean,
    accent: Color = Lux.brass,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Lux.surfaceHi, RoundedCornerShape(12.dp))
            .border(1.dp, Lux.line, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = Lux.text)
            Text(summary, fontSize = 11.sp, color = Lux.faint)
        }
        Text(if (open) "הסתרה" else "הרחבה", fontSize = 12.sp, color = accent)
    }
}

@Composable
private fun TimeField(label: String, minutes: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier
            .background(Lux.bg, RoundedCornerShape(14.dp))
            .border(1.dp, Lux.line, RoundedCornerShape(14.dp))
            .clickable {
                TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
            }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = Lux.faint, letterSpacing = 1.sp)
        Text(fmt(minutes), fontSize = 20.sp, fontWeight = FontWeight.Light, color = Lux.text)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Lux.text, modifier = Modifier.weight(1f))
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
                    .border(1.dp, if (on) color.copy(alpha = 0.6f) else Lux.line, RoundedCornerShape(13.dp))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) color else Lux.muted, fontSize = 13.sp)
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
        Text(label, fontSize = 14.sp, color = Lux.text, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value - step >= range.first) onChange(value - step) }) {
            Text("−", color = Lux.brass, fontSize = 18.sp)
        }
        Text("$value $unit", fontSize = 13.sp, color = Lux.text)
        TextButton(onClick = { if (value + step <= range.last) onChange(value + step) }) {
            Text("+", color = Lux.brass, fontSize = 18.sp)
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
