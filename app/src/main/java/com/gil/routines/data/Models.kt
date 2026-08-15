package com.gil.routines.data

import org.json.JSONArray
import org.json.JSONObject

/** איך מטפלים בשיחה נכנסת כשהמצב פעיל */
enum class CallHandling { REJECT, SILENCE }

/**
 * פריצה בחיוג חוזר: אחרי [attempts] חיוגים מאותו מספר בתוך [windowMinutes] דקות,
 * ההשתקה מתבטלת והטלפון מצלצל כרגיל.
 */
data class Breakthrough(
    val enabled: Boolean = true,
    val attempts: Int = 3,
    val windowMinutes: Int = 10
)

data class CallConfig(
    val handling: CallHandling = CallHandling.REJECT,
    val sendSms: Boolean = true,
    val message: String = "לא זמין כרגע, אחזור אליך בהקדם.",
    val allowContacts: Boolean = true,
    val smsCooldownHours: Int = 4,
    val breakthrough: Breakthrough = Breakthrough()
)

/** דקות מחצות, 0..1439 — מאפשר דיוק של דקה ולא רק שעות עגולות */
typealias MinuteOfDay = Int

data class Mode(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val enabled: Boolean = false,
    val start: MinuteOfDay = 0,
    val end: MinuteOfDay = 0,
    /** 1=ראשון .. 7=שבת, בהתאם ל-java.util.Calendar.DAY_OF_WEEK */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val actions: Set<String> = emptySet(),
    val call: CallConfig = CallConfig()
) {
    /** נכון גם לחלון שחוצה חצות, למשל 22:45–06:30 */
    fun coversMinute(m: MinuteOfDay): Boolean = when {
        start == end -> false
        start < end -> m >= start && m < end
        else -> m >= start || m < end
    }

    /**
     * חלון שחוצה חצות שייך ליום שבו הוא התחיל.
     * שגרת שינה של "ראשון 22:45" ממשיכה לפעול בשתיים לפנות בוקר ביום שני.
     */
    fun isActiveAt(minuteOfDay: MinuteOfDay, dayOfWeek: Int): Boolean {
        if (!enabled || !coversMinute(minuteOfDay)) return false
        val owningDay = if (start <= end || minuteOfDay >= start) dayOfWeek else prevDay(dayOfWeek)
        return days.contains(owningDay)
    }

    private fun prevDay(d: Int) = if (d == 1) 7 else d - 1
}

object Actions {
    const val DND = "dnd"
    const val CALL_GUARD = "callreply"
    const val MUTE = "mute"
    const val BRIGHTNESS = "bright"
    const val AIRPLANE = "airplane"   // דורש WRITE_SECURE_SETTINGS
    const val GRAYSCALE = "gray"      // דורש WRITE_SECURE_SETTINGS
}

/* ── סריאליזציה ידנית ב-org.json, כדי לא לגרור תלות נוספת ── */

fun Mode.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("color", colorArgb)
    put("enabled", enabled)
    put("start", start)
    put("end", end)
    put("days", JSONArray(days.toList()))
    put("actions", JSONArray(actions.toList()))
    put("call", JSONObject().apply {
        put("handling", call.handling.name)
        put("sendSms", call.sendSms)
        put("message", call.message)
        put("allowContacts", call.allowContacts)
        put("cooldown", call.smsCooldownHours)
        put("btEnabled", call.breakthrough.enabled)
        put("btAttempts", call.breakthrough.attempts)
        put("btWindow", call.breakthrough.windowMinutes)
    })
}

fun modeFromJson(o: JSONObject): Mode {
    val c = o.optJSONObject("call") ?: JSONObject()
    return Mode(
        id = o.getString("id"),
        name = o.getString("name"),
        colorArgb = o.optLong("color", 0xFF4A4EBFL),
        enabled = o.optBoolean("enabled", false),
        start = o.optInt("start", 0),
        end = o.optInt("end", 0),
        days = o.optJSONArray("days").toIntSet(),
        actions = o.optJSONArray("actions").toStringSet(),
        call = CallConfig(
            handling = runCatching { CallHandling.valueOf(c.optString("handling", "REJECT")) }
                .getOrDefault(CallHandling.REJECT),
            sendSms = c.optBoolean("sendSms", true),
            message = c.optString("message", ""),
            allowContacts = c.optBoolean("allowContacts", true),
            smsCooldownHours = c.optInt("cooldown", 4),
            breakthrough = Breakthrough(
                enabled = c.optBoolean("btEnabled", true),
                attempts = c.optInt("btAttempts", 3),
                windowMinutes = c.optInt("btWindow", 10)
            )
        )
    )
}

private fun JSONArray?.toIntSet(): Set<Int> =
    if (this == null) emptySet() else (0 until length()).map { getInt(it) }.toSet()

private fun JSONArray?.toStringSet(): Set<String> =
    if (this == null) emptySet() else (0 until length()).map { getString(it) }.toSet()
