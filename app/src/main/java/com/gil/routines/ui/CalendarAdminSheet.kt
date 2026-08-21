package com.gil.routines.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gil.routines.calendar.CalendarAdmin
import com.gil.routines.calendar.CalendarInfo

/**
 * ניהול היומנים במכשיר: הסתרה הפיכה, החזרה, ומחיקה סופית.
 * מקובץ לפי חשבון, כי שם מתגלה מי אחראי להצפה.
 */
@Composable
fun CalendarAdminDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val groups = remember(reload) { CalendarAdmin.byAccount(ctx) }
    val selected = remember { mutableStateListOf<Long>() }
    var openAccount by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var onlyHidden by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Lux.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
        ) {
            Column(Modifier.padding(18.dp)) {

                Text("ניהול יומנים", fontSize = 20.sp, fontWeight = FontWeight.Light, color = Lux.text)
                Text(
                    "הסתרה היא הפיכה. מחיקה אינה הפיכה, ואם אפליקציית המקור מסנכרנת — היא תיצור אותם מחדש.",
                    fontSize = 11.sp, color = Lux.faint, lineHeight = 16.sp
                )

                if (!CalendarAdmin.canWrite(ctx)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "חסרה הרשאת כתיבה ליומן. אשר אותה בהגדרות ← הרשאות.",
                        fontSize = 12.sp, color = Lux.brass
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = onlyHidden,
                        onClick = { onlyHidden = !onlyHidden },
                        label = { Text(if (onlyHidden) "מוסתרים בלבד" else "הכל", fontSize = 12.sp) }
                    )
                    Text(
                        "${groups.values.sumOf { it.size }} יומנים ב-${groups.size} חשבונות",
                        fontSize = 12.sp, color = Lux.muted,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { (account, cals) ->
                            val shown = if (onlyHidden) cals.filter { !it.visible } else cals
                            if (shown.isEmpty()) return@forEach

                            item(key = "h_$account") {
                                val allSelected = shown.all { selected.contains(it.id) }
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(Lux.surfaceHi, RoundedCornerShape(12.dp))
                                        .border(1.dp, Lux.line, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(
                                            Modifier.weight(1f).clickable {
                                                openAccount = if (openAccount == account) null else account
                                            }
                                        ) {
                                            Text(account, fontSize = 14.sp, color = Lux.text)
                                            Text(
                                                "${shown.size} יומנים · ${shown.count { !it.visible }} מוסתרים",
                                                fontSize = 11.sp, color = Lux.faint
                                            )
                                        }
                                        TextButton(onClick = {
                                            if (allSelected) selected.removeAll(shown.map { it.id })
                                            else shown.forEach { if (!selected.contains(it.id)) selected.add(it.id) }
                                        }) {
                                            Text(
                                                if (allSelected) "ביטול בחירה" else "בחר הכל",
                                                fontSize = 12.sp, color = Lux.brass
                                            )
                                        }
                                        Text(
                                            if (openAccount == account) "▾" else "▸",
                                            fontSize = 14.sp, color = Lux.faint
                                        )
                                    }
                                }
                            }

                            if (openAccount == account) {
                                items(shown, key = { it.id }) { cal ->
                                    CalRow(cal, selected.contains(cal.id)) {
                                        if (selected.contains(cal.id)) selected.remove(cal.id)
                                        else selected.add(cal.id)
                                    }
                                }
                            }
                        }
                    }
                }

                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = Lux.ok, lineHeight = 17.sp)
                }

                Spacer(Modifier.height(10.dp))
                Text("${selected.size} נבחרו", fontSize = 12.sp, color = Lux.muted)
                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val n = CalendarAdmin.setHidden(ctx, selected.toSet(), true)
                            note = "$n יומנים הוסתרו"; selected.clear(); reload++
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.brass)
                    ) { Text("הסתרה", fontSize = 13.sp) }

                    OutlinedButton(
                        onClick = {
                            val n = CalendarAdmin.setHidden(ctx, selected.toSet(), false)
                            note = "$n יומנים הוחזרו"; selected.clear(); reload++
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.ok)
                    ) { Text("החזרה", fontSize = 13.sp) }

                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.brass)
                    ) { Text("מחיקה", fontSize = 13.sp) }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "פתיחת הגדרות החשבונות — לכיבוי סנכרון היומן במקור",
                        color = Lux.brass, fontSize = 12.sp
                    )
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("סגירה", color = Lux.muted, fontSize = 13.sp)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Lux.surface,
            title = { Text("למחוק ${selected.size} יומנים?", color = Lux.text, fontSize = 17.sp) },
            text = {
                Text(
                    "הפעולה אינה הפיכה. כל האירועים ביומנים האלה יימחקו מהמכשיר. " +
                        "אם אפליקציית המקור עדיין מסנכרנת, היא עלולה ליצור אותם מחדש.",
                    color = Lux.muted, fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = groups.values.flatten().filter { selected.contains(it.id) }
                    val (ok, failed) = CalendarAdmin.delete(ctx, chosen)
                    val left = chosen.map { it.account }.distinct()
                        .sumOf { CalendarAdmin.countFor(ctx, it) }
                    note = buildString {
                        append("$ok נמחקו")
                        if (failed > 0) append(", $failed נכשלו")
                        append(" · נשארו $left בחשבונות האלה")
                        if (failed > 0) append("\nאם הכל נכשל, כבה את סנכרון היומן של החשבון בהגדרות המכשיר.")
                    }
                    selected.clear(); confirmDelete = false; reload++
                }) { Text("מחיקה", color = Lux.brass) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("ביטול", color = Lux.muted) }
            }
        )
    }
}

@Composable
private fun CalRow(cal: CalendarInfo, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Lux.bg, RoundedCornerShape(10.dp))
            .border(1.dp, Lux.line, RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked, onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Lux.brass, uncheckedColor = Lux.faint, checkmarkColor = Lux.bg
            )
        )
        Column(Modifier.weight(1f)) {
            Text(cal.name.ifBlank { "ללא שם" }, fontSize = 13.sp, color = Lux.text)
            if (!cal.visible || !cal.syncing) {
                Text(
                    buildString {
                        if (!cal.visible) append("מוסתר")
                        if (!cal.visible && !cal.syncing) append(" · ")
                        if (!cal.syncing) append("סנכרון כבוי")
                    },
                    fontSize = 10.sp, color = Lux.brass
                )
            }
        }
    }
}
