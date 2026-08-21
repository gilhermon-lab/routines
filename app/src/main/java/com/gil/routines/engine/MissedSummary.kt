package com.gil.routines.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gil.routines.R
import com.gil.routines.call.CallLogStore
import com.gil.routines.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * סיכום מי ניסה להשיג אותך בזמן שהמצב היה פעיל.
 *
 * מוצג ברגע שהמצב מסתיים, כי זה הרגע שבו המידע הזה שווה משהו —
 * לא בזמן שהטלפון אמור להיות שקט.
 */
object MissedSummary {

    private const val CHANNEL = "missed_summary"
    private const val NOTIF_ID = 4711

    fun notifyForWindow(ctx: Context, modeName: String, since: Long) {
        val entries = collect(ctx, since)
        if (entries.isEmpty()) return
        if (!canNotify(ctx)) return

        ensureChannel(ctx)

        val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
        val lines = entries.take(6).map { "${clock.format(Date(it.time))}  ${it.who} — ${it.what}" }

        val tap = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle("${entries.size} ניסיונות בזמן \"$modeName\"")
        lines.forEach { style.addLine(it) }
        if (entries.size > lines.size) style.setSummaryText("ועוד ${entries.size - lines.size}")

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${entries.size} ניסיונות בזמן \"$modeName\"")
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        runCatching {
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        }
    }

    data class Entry(val time: Long, val who: String, val what: String)

    /** מאחדים את מה שאנחנו חסמנו עם שיחות שלא נענו ביומן המערכת */
    private fun collect(ctx: Context, since: Long): List<Entry> {
        val ours = runCatching {
            CallLogStore.all(ctx)
                .filter { it.timeMillis >= since }
                .map { Entry(it.timeMillis, it.number, it.outcome) }
        }.getOrDefault(emptyList())

        val system = if (hasPermission(ctx, Manifest.permission.READ_CALL_LOG)) {
            runCatching { systemMissed(ctx, since) }.getOrDefault(emptyList())
        } else emptyList()

        // מספר שכבר מופיע אצלנו לא נספר פעמיים
        val known = ours.map { it.who }.toSet()
        return (ours + system.filterNot { known.contains(it.who) })
            .sortedByDescending { it.time }
    }

    private fun systemMissed(ctx: Context, since: Long): List<Entry> {
        val cols = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME)
        val where = "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.DATE}>=?"
        return ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, cols, where, arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < 20) {
                    val number = c.getString(0).orEmpty()
                    val name = c.getString(2)
                    add(Entry(c.getLong(1), name?.ifBlank { number } ?: number, "לא נענתה"))
                }
            }
        }.orEmpty()
    }

    private fun canNotify(ctx: Context) =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            hasPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)

    private fun hasPermission(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(ctx: Context) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "סיכום בסיום מצב", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "מי ניסה להשיג אותך בזמן שהמצב היה פעיל"
                }
            )
        }
    }
}
