package com.gil.routines.ui

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val unlocks: String,
    val isGranted: (Context) -> Boolean,
    /** null כשאין מסך לפתוח — כמו ההרשאה שניתנת רק ב-ADB */
    val intentFor: ((Context) -> Intent)? = null,
    val runtimePermissions: Array<String> = emptyArray(),
    val adbCommand: String? = null
)

object Permissions {

    const val ADB_SECURE =
        "adb shell pm grant com.gil.routines android.permission.WRITE_SECURE_SETTINGS"

    val all: List<PermItem> = listOf(
        PermItem(
            id = "callrole",
            title = "סינון שיחות",
            subtitle = "בקשה מתוך האפליקציה",
            unlocks = "דחיית שיחות והשתקתן",
            isGranted = { ctx ->
                val rm = ctx.getSystemService(RoleManager::class.java)
                rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            },
            intentFor = { ctx ->
                ctx.getSystemService(RoleManager::class.java)
                    .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            }
        ),
        PermItem(
            id = "sms",
            title = "שליחת הודעות ואנשי קשר",
            subtitle = "הרשאות רגילות",
            unlocks = "המשוב האוטומטי וחריגת אנשי קשר",
            isGranted = { ctx ->
                granted(ctx, Manifest.permission.SEND_SMS) &&
                    granted(ctx, Manifest.permission.READ_CONTACTS)
            },
            runtimePermissions = arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE
            )
        ),
        PermItem(
            id = "bt",
            title = "בלוטות'",
            subtitle = "הרשאה רגילה",
            unlocks = "הפעלת מצב בחיבור למערכת הרכב",
            isGranted = { ctx ->
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                    granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)
            },
            runtimePermissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        ),
        PermItem(
            id = "calendar",
            title = "קריאת היומן",
            subtitle = "הרשאה רגילה",
            unlocks = "הפעלת מצב לפי אירועים · ניהול היומנים במכשיר",
            isGranted = { ctx -> granted(ctx, Manifest.permission.READ_CALENDAR) },
            runtimePermissions = arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        ),
        PermItem(
            id = "summary",
            title = "התראות ויומן שיחות",
            subtitle = "הרשאות רגילות",
            unlocks = "סיכום מי ניסה להשיג בסיום מצב",
            isGranted = { ctx ->
                val notif = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
                notif && granted(ctx, Manifest.permission.READ_CALL_LOG)
            },
            runtimePermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CALL_LOG)
            else arrayOf(Manifest.permission.READ_CALL_LOG)
        ),
        PermItem(
            id = "dnd",
            title = "גישה ל\"נא לא להפריע\"",
            subtitle = "מסך הגדרות של אנדרואיד",
            unlocks = "נא לא להפריע",
            isGranted = { ctx ->
                ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
            },
            intentFor = { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
        ),
        PermItem(
            id = "write",
            title = "שינוי הגדרות מערכת",
            subtitle = "מסך הגדרות של אנדרואיד",
            unlocks = "בהירות מסך",
            isGranted = { ctx -> Settings.System.canWrite(ctx) },
            intentFor = { ctx ->
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
            }
        ),
        PermItem(
            id = "exact",
            title = "התראות מדויקות",
            subtitle = "מסך הגדרות של אנדרואיד",
            unlocks = "מעבר מדויק בין מצבים",
            isGranted = { ctx ->
                val am = ctx.getSystemService(android.app.AlarmManager::class.java)
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                    am.canScheduleExactAlarms()
            },
            intentFor = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
        ),
        PermItem(
            id = "battery",
            title = "פעילות חופשית ברקע",
            subtitle = "קריטי ב-ColorOS — בלי זה השגרות ייעצרו",
            unlocks = "מעבר אמין בין מצבים לאורך היום",
            isGranted = { ctx ->
                ctx.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(ctx.packageName)
            },
            intentFor = { ctx ->
                @Suppress("BatteryLife")
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.packageName)
                )
            }
        ),
        PermItem(
            id = "secure",
            title = "הגדרות מוגנות",
            subtitle = "פקודה אחת מהמחשב",
            unlocks = "מצב טיסה · גווני אפור",
            isGranted = { ctx ->
                granted(ctx, Manifest.permission.WRITE_SECURE_SETTINGS)
            },
            adbCommand = ADB_SECURE
        )
    )

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
}
