package com.gil.routines.call

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** רשומה אחת ביומן: מה קרה לשיחה ולמה */
data class CallEvent(
    val timeMillis: Long,
    val number: String,
    val modeName: String,
    val outcome: String,
    val smsSent: Boolean
)

/**
 * יומן קצר של השיחות שטופלו. קיים כדי שאפשר יהיה לראות בעיניים
 * שההגדרות באמת נתפסו — ולא רק להניח.
 */
object CallLogStore {

    private const val PREFS = "routines_calllog"
    private const val KEY = "events"
    private const val MAX = 30

    fun add(ctx: Context, e: CallEvent) {
        val arr = read(ctx)
        val next = JSONArray()
        next.put(JSONObject().apply {
            put("t", e.timeMillis)
            put("n", e.number)
            put("m", e.modeName)
            put("o", e.outcome)
            put("s", e.smsSent)
        })
        for (i in 0 until minOf(arr.length(), MAX - 1)) next.put(arr.get(i))
        prefs(ctx).edit().putString(KEY, next.toString()).apply()
    }

    fun all(ctx: Context): List<CallEvent> {
        val arr = read(ctx)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CallEvent(
                timeMillis = o.optLong("t"),
                number = o.optString("n"),
                modeName = o.optString("m"),
                outcome = o.optString("o"),
                smsSent = o.optBoolean("s")
            )
        }
    }

    fun clear(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()

    private fun read(ctx: Context) =
        runCatching { JSONArray(prefs(ctx).getString(KEY, "[]")!!) }.getOrElse { JSONArray() }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
