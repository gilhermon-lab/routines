package com.gil.routines.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.gil.routines.calendar.CalendarReader
import com.gil.routines.call.CallLogStore
import com.gil.routines.call.SmsSender
import com.gil.routines.data.Actions
import com.gil.routines.data.ModeStore
import com.gil.routines.engine.ModeApplier
import com.gil.routines.engine.RoutineEngine

private data class Check(val label: String, val ok: Boolean, val hint: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var number by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(false) }

    val active = remember { RoutineEngine.activeModes(ctx) }
    val guard = active.firstOrNull { it.actions.contains(Actions.CALL_GUARD) }
    val anyGuard = ModeStore.load(ctx).firstOrNull { it.actions.contains(Actions.CALL_GUARD) }

    fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val roleHeld = runCatching {
        ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }.getOrDefault(false)

    val checks = listOf(
        Check(
            "תפקיד סינון השיחות בידי האפליקציה", roleHeld,
            "בלי זה אנדרואיד לא מודיע לנו על שיחות בכלל. אשר בהרשאות."
        ),
        Check(
            "הרשאת שליחת הודעות", granted(Manifest.permission.SEND_SMS),
            "זו הסיבה הנפוצה ביותר לכך שלא נשלחת הודעה."
        ),
        Check(
            "הרשאת אנשי קשר", granted(Manifest.permission.READ_CONTACTS),
            "בלעדיה לא ניתן לזהות אנשי קשר ולא לבחור אותם לרשימת ההיתר."
        ),
        Check(
            "יש מצב פעיל עם סינון שיחות", guard != null,
            if (anyGuard == null) "אף מצב לא מוגדר לסנן שיחות."
            else "המצב \"${anyGuard.name}\" מסנן שיחות, אבל הוא לא פעיל עכשיו. הפעל אותו ידנית דרך \"עכשיו\"."
        ),
        Check(
            "המשוב האוטומטי דלוק במצב הפעיל", guard?.call?.sendSms == true,
            "המתג \"לשלוח הודעה למתקשר\" כבוי במצב הזה."
        ),
        Check(
            "האפליקציה פטורה מאופטימיזציית סוללה",
            runCatching {
                ctx.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(ctx.packageName)
            }.getOrDefault(false),
            "ב-ColorOS זו הסיבה השכיחה לכך ששגרות מפסיקות לפעול אחרי כמה שעות. " +
                "בנוסף, הפעל הפעלה אוטומטית: הגדרות ← אפליקציות ← שגרות ← שימוש בסוללה ← אפשר פעילות ברקע."
        )
    )

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
            Text("בדיקת מערכת", fontSize = 24.sp, fontWeight = FontWeight.Light, color = Lux.text)
            Text(
                "מה נדרש כדי שהודעה תישלח, ומה חסר כרגע.",
                fontSize = 12.sp, color = Lux.faint
            )
            Spacer(Modifier.height(6.dp))

            checks.forEach { c ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(Lux.bg, RoundedCornerShape(14.dp))
                        .border(1.dp, if (c.ok) Lux.ok.copy(alpha = 0.3f) else Lux.line, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (c.ok) Icons.Outlined.Check else Icons.Outlined.Close,
                        null, tint = if (c.ok) Lux.ok else Lux.brass,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(c.label, fontSize = 14.sp, color = Lux.text)
                        if (!c.ok) Text(c.hint, fontSize = 11.sp, color = Lux.muted, lineHeight = 17.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("מצב נוכחי", fontSize = 15.sp, color = Lux.text)
            Box(
                Modifier.fillMaxWidth()
                    .background(Lux.bg, RoundedCornerShape(14.dp))
                    .border(1.dp, Lux.line, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("נא לא להפריע כרגע: ${ModeApplier.dndFilterName(ctx)}",
                        fontSize = 12.sp, color = Lux.muted)
                    Text("הפעלה אחרונה: ${ModeApplier.lastTrace(ctx)}",
                        fontSize = 12.sp, color = Lux.muted, lineHeight = 18.sp)
                    val cals = runCatching { CalendarReader.calendars(ctx) }.getOrDefault(emptyList())
                    Text(
                        "יומנים שנמצאו: ${cals.size}" +
                            if (cals.isEmpty()) " — אין יומן מסונכרן, או שחסרה הרשאת יומן."
                            else "\n" + cals.joinToString("\n") { "· ${it.name} (${it.account})" },
                        fontSize = 12.sp, color = Lux.muted, lineHeight = 18.sp
                    )
                    Text(
                        "שיחות שהשירות ראה: ${CallLogStore.all(ctx).size}" +
                            if (CallLogStore.all(ctx).isEmpty())
                                " — אם זה 0 אחרי שיחת בדיקה, אנדרואיד לא מפנה אלינו שיחות בכלל."
                            else "",
                        fontSize = 12.sp, color = Lux.muted, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("שליחת הודעת בדיקה", fontSize = 15.sp, color = Lux.text)
            Text(
                "שולח SMS אמיתי דרך אותו קוד שרץ בדחיית שיחה. הזן מספר — למשל שלך.",
                fontSize = 11.sp, color = Lux.faint, lineHeight = 17.sp
            )

            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("מספר טלפון", color = Lux.muted) },
                singleLine = true,
                textStyle = TextStyle(textDirection = TextDirection.Ltr),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
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

            OutlinedButton(
                onClick = {
                    val text = guard?.call?.message?.takeIf { it.isNotBlank() }
                        ?: "בדיקה מאפליקציית שגרות."
                    SmsSender.send(ctx, number.trim(), text)
                        .onSuccess {
                            resultOk = true
                            result = "ההודעה נמסרה למערכת השליחה. בדוק שהיא הגיעה בפועל."
                        }
                        .onFailure {
                            resultOk = false
                            result = "נכשל: ${it.javaClass.simpleName} — ${it.message ?: "ללא פירוט"}"
                        }
                },
                enabled = number.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.brass),
                modifier = Modifier.fillMaxWidth()
            ) { Text("שלח הודעת בדיקה") }

            result?.let {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Lux.bg, RoundedCornerShape(12.dp))
                        .border(1.dp, if (resultOk) Lux.ok.copy(alpha = 0.4f) else Lux.brass.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(it, fontSize = 12.sp, color = if (resultOk) Lux.ok else Lux.brassSoft, lineHeight = 18.sp)
                }
            }
        }
    }
}
