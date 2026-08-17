package com.gil.routines.ui

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val label: String, val packageName: String)

object InstalledApps {
    fun launchable(ctx: Context): List<AppEntry> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        ctx.packageManager.queryIntentActivities(intent, 0)
            .map {
                AppEntry(
                    it.loadLabel(ctx.packageManager).toString(),
                    it.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
    }.getOrDefault(emptyList())
}

/** בחירת אפליקציה שתיפתח כשהמצב נדלק */
@Composable
fun AppPickerDialog(accent: Color, onDismiss: () -> Unit, onPick: (AppEntry?) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { InstalledApps.launchable(ctx) } }

    val list = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Lux.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("בחירת אפליקציה", fontSize = 20.sp, fontWeight = FontWeight.Light, color = Lux.text)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("חיפוש", color = Lux.muted) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lux.brass, unfocusedBorderColor = Lux.line,
                        focusedTextColor = Lux.text, unfocusedTextColor = Lux.text,
                        cursorColor = Lux.brass
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                Box(Modifier.weight(1f)) {
                    if (apps == null) {
                        Text("טוען…", fontSize = 13.sp, color = Lux.faint, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(list, key = { it.packageName }) { app ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(Lux.bg, RoundedCornerShape(12.dp))
                                        .border(1.dp, Lux.line, RoundedCornerShape(12.dp))
                                        .clickable { onPick(app) }
                                        .padding(horizontal = 12.dp, vertical = 11.dp)
                                ) {
                                    Column {
                                        Text(app.label, fontSize = 14.sp, color = Lux.text)
                                        Text(app.packageName, fontSize = 10.sp, color = Lux.faint)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Lux.muted)
                    ) { Text("ביטול", fontSize = 14.sp) }

                    OutlinedButton(
                        onClick = { onPick(null) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                    ) { Text("ללא אפליקציה", fontSize = 14.sp) }
                }
            }
        }
    }
}
