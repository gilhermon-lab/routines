package com.gil.routines.data

import org.json.JSONArray
import org.json.JSONObject

/** איך מטפלים בשיחה נכנסת כשהמצב פעיל */
enum class CallHandling { REJECT, SILENCE }

/** מי רשאי לצלצל למרות שהמצב פעיל */
enum class ContactPolicy { ALL, LIST, NONE }

/** איש קשר ברשימת ההיתר. השם נשמר לתצוגה בלבד; ההשוואה על המספר. */
data class AllowedContact(val name: String, val number: String) {
    /** משווים לפי הספרות האחרונות — מספרים מגיעים בפורמטים שונים */
    val key: String get() = normalizeNumber(number)
}

fun normalizeNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length > 9) digits.takeLast(9) else digits
}

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
    val allowContacts: Boolean = true,          // נשמר לתאימות עם נתונים ישנים
    val contactPolicy: ContactPolicy = ContactPolicy.ALL,
    val allowed: List<AllowedContact> = emptyList(),
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
    val call: CallConfig = CallConfig(),
    /**
     * עקיפה ידנית של לוח הזמנים.
     * null = לפי הלוח, true = דלוק עכשיו בלי קשר לשעה, false = כבוי עכשיו.
     */
    val manualOverride: Boolean? = null
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
        manualOverride?.let { return it }          // עקיפה ידנית גוברת על הכל
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
    put("override", manualOverride ?: JSONObject.NULL)
    put("call", JSONObject().apply {
        put("handling", call.handling.name)
        put("sendSms", call.sendSms)
        put("message", call.message)
        put("allowContacts", call.allowContacts)
        put("contactPolicy", call.contactPolicy.name)
        put("allowed", JSONArray().apply {
            call.allowed.forEach { c ->
                put(JSONObject().apply { put("n", c.name); put("p", c.number) })
            }
        })
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
        manualOverride = if (o.isNull("override")) null else o.optBoolean("override"),
        call = CallConfig(
            handling = runCatching { CallHandling.valueOf(c.optString("handling", "REJECT")) }
                .getOrDefault(CallHandling.REJECT),
            sendSms = c.optBoolean("sendSms", true),
            message = c.optString("message", ""),
            allowContacts = c.optBoolean("allowContacts", true),
            contactPolicy = runCatching {
                ContactPolicy.valueOf(c.optString("contactPolicy", ""))
            }.getOrElse {
                // מיגרציה מהמתג הישן
                if (c.optBoolean("allowContacts", true)) ContactPolicy.ALL else ContactPolicy.NONE
            },
            allowed = (c.optJSONArray("allowed") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    AllowedContact(o.optString("n"), o.optString("p"))
                }
            },
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
