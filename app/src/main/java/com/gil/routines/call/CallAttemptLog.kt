package com.gil.routines.call

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * זוכר מתי כל מספר חייג, וממתי נשלחה אליו הודעה אחרונה.
 * שני אלה מה שמאפשר גם את הפריצה בחיוג חוזר וגם את המרווח בין הודעות.
 */
object CallAttemptLog {

    private const val PREFS = "routines_calls"
    private const val KEY_ATTEMPTS = "attempts"
    private const val KEY_SMS = "last_sms"

    /** רושם חיוג ומחזיר כמה חיוגים היו מהמספר הזה בתוך החלון */
    fun recordAndCount(ctx: Context, number: String, windowMinutes: Int): Int {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMinutes * 60_000L
        val all = readAttempts(ctx)

        val kept = (all.optJSONArray(number) ?: JSONArray())
            .let { arr -> (0 until arr.length()).map { arr.getLong(it) } }
            .filter { it >= cutoff } + now

        all.put(number, JSONArray(kept))
        pruneEmpty(all, cutoff)
        prefs(ctx).edit().putString(KEY_ATTEMPTS, all.toString()).apply()
        return kept.size
    }

    /** מאפסים אחרי פריצה, כדי שהספירה לא תימשך אל השיחה הבאה */
    fun clear(ctx: Context, number: String) {
        val all = readAttempts(ctx)
        all.remove(number)
        prefs(ctx).edit().putString(KEY_ATTEMPTS, all.toString()).apply()
    }

    fun shouldSendSms(ctx: Context, number: String, cooldownHours: Int): Boolean {
        val last = readSms(ctx).optLong(number, 0L)
        return System.currentTimeMillis() - last >= cooldownHours * 3_600_000L
    }

    fun markSmsSent(ctx: Context, number: String) {
        val o = readSms(ctx)
        o.put(number, System.currentTimeMillis())
        prefs(ctx).edit().putString(KEY_SMS, o.toString()).apply()
    }

    private fun pruneEmpty(all: JSONObject, cutoff: Long) {
        val dead = all.keys().asSequence().filter { k ->
            val a = all.optJSONArray(k) ?: return@filter true
            a.length() == 0 || (0 until a.length()).all { a.getLong(it) < cutoff }
        }.toList()
        dead.forEach { all.remove(it) }
    }

    private fun readAttempts(ctx: Context) =
        runCatching { JSONObject(prefs(ctx).getString(KEY_ATTEMPTS, "{}")!!) }.getOrElse { JSONObject() }

    private fun readSms(ctx: Context) =
        runCatching { JSONObject(prefs(ctx).getString(KEY_SMS, "{}")!!) }.getOrElse { JSONObject() }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
