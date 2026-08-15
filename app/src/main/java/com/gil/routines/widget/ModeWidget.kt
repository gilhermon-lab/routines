package com.gil.routines.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gil.routines.R
import com.gil.routines.data.ModeStore
import com.gil.routines.engine.ModeApplier
import com.gil.routines.engine.RoutineEngine

/**
 * ווידג'ט מסך הבית להפעלה וכיבוי מיידיים של מצב, בלי קשר ללוח הזמנים.
 * לחיצה קצרה מחליפה מצב, לחיצה ארוכה על הכותרת פותחת את האפליקציה.
 */
class ModeWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_TOGGLE) {
            toggle(ctx)
            refreshAll(ctx)
        }
    }

    private fun toggle(ctx: Context) {
        val mode = ModeStore.load(ctx).find { it.id == TARGET_MODE } ?: return
        val liveNow = RoutineEngine.activeModes(ctx).any { it.id == TARGET_MODE }

        // כיבוי של מצב שהודלק ידנית מחזיר ללוח הזמנים, ולא נועל אותו על כבוי לנצח
        val next: Boolean? = when {
            mode.manualOverride == true -> null
            liveNow -> false
            else -> true
        }
        ModeStore.update(ctx, TARGET_MODE) { it.copy(manualOverride = next, enabled = true) }
        ModeApplier.applyCurrentState(ctx)
    }

    private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val mode = ModeStore.load(ctx).find { it.id == TARGET_MODE }
        val live = RoutineEngine.activeModes(ctx).any { it.id == TARGET_MODE }
        val manual = mode?.manualOverride != null

        val views = RemoteViews(ctx.packageName, R.layout.widget_mode).apply {
            setTextViewText(R.id.widget_title, mode?.name ?: "ישיבה")
            setTextViewText(
                R.id.widget_state,
                when {
                    live && manual -> "פעיל ידנית"
                    live -> "פעיל לפי הלוח"
                    manual -> "כבוי ידנית"
                    else -> "כבוי"
                }
            )
            setInt(
                R.id.widget_root, "setBackgroundResource",
                if (live) R.drawable.widget_bg_on else R.drawable.widget_bg_off
            )
            // טקסט כהה על פליז, בהיר על דיו
            setTextColor(R.id.widget_title, if (live) 0xFF14161B.toInt() else 0xFFF2F0EC.toInt())
            setTextColor(R.id.widget_state, if (live) 0xCC14161B.toInt() else 0xFF8B8FA0.toInt())

            // כל שטח הווידג'ט מחליף מצב — כולל הכותרת, שקודם פתחה את האפליקציה
            setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(ctx))
            setOnClickPendingIntent(R.id.widget_title, togglePendingIntent(ctx))
            setOnClickPendingIntent(R.id.widget_state, togglePendingIntent(ctx))
        }
        mgr.updateAppWidget(id, views)
    }

    private fun togglePendingIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, ModeWidget::class.java).setAction(ACTION_TOGGLE)
        return PendingIntent.getBroadcast(
            ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_TOGGLE = "com.gil.routines.WIDGET_TOGGLE"
        const val TARGET_MODE = "meeting"

        /** נקרא גם מהאפליקציה, כדי שהווידג'ט יתעדכן אחרי שינוי במסך */
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ModeWidget::class.java))
            if (ids.isNotEmpty()) {
                ModeWidget().onUpdate(ctx, mgr, ids)
            }
        }
    }
}
