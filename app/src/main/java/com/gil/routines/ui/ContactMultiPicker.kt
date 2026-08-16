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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gil.routines.data.AllowedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * בורר אנשי קשר עם בחירה מרובה.
 * הבורר של המערכת מאפשר בחירה אחת בכל פעם, ולכן הרשימה נבנית כאן.
 */
@Composable
fun ContactMultiPicker(
    already: List<AllowedContact>,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (List<AllowedContact>) -> Unit
) {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<AllowedContact>?>(null) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(already.map { it.key }) } }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { Contacts.all(ctx) }
    }

    val list = all.orEmpty().filter {
        query.isBlank() || it.name.contains(query, true) || it.number.contains(query)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Lux.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f)
        ) {
            Column(Modifier.padding(18.dp)) {

                Text("בחירת אנשי קשר", fontSize = 20.sp, fontWeight = FontWeight.Light, color = Lux.text)
                Text(
                    "מי שייבחר יצלצל גם כשהמצב פעיל.",
                    fontSize = 11.sp, color = Lux.faint
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("חיפוש", color = Lux.muted) },
                    singleLine = true,
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

                Spacer(Modifier.height(10.dp))

                Box(Modifier.weight(1f)) {
                    when {
                        all == null -> Text(
                            "טוען אנשי קשר…", fontSize = 13.sp, color = Lux.faint,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        list.isEmpty() -> Text(
                            if (all!!.isEmpty()) "לא נמצאו אנשי קשר. ודא שהרשאת אנשי הקשר אושרה."
                            else "אין תוצאה לחיפוש.",
                            fontSize = 13.sp, color = Lux.faint, modifier = Modifier.align(Alignment.Center)
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(list, key = { it.key }) { c ->
                                val on = selected.contains(c.key)
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(Lux.bg, RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            if (on) accent.copy(alpha = 0.55f) else Lux.line,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            if (on) selected.remove(c.key) else selected.add(c.key)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = on,
                                        onCheckedChange = {
                                            if (on) selected.remove(c.key) else selected.add(c.key)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = accent,
                                            uncheckedColor = Lux.faint,
                                            checkmarkColor = Lux.bg
                                        )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.name, fontSize = 14.sp, color = Lux.text)
                                        Text(c.number, fontSize = 11.sp, color = Lux.faint)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.muted)
                    ) { Text("ביטול", fontSize = 14.sp) }

                    Button(
                        onClick = {
                            val chosen = all.orEmpty().filter { selected.contains(it.key) }
                            onConfirm(chosen)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent, contentColor = Lux.bg
                        )
                    ) { Text("אישור (${selected.size})", fontSize = 14.sp) }
                }
            }
        }
    }
}
