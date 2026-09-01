package com.gil.routines.data

import org.json.JSONArray
import org.json.JSONObject

/** איך מטפלים בשיחה נכנסת כשהמצב פעיל */
enum class CallHandling { REJECT, SILENCE }

/** מי רשאי לצלצל למרות שהמצב פעיל */
enum class ContactPolicy { ALL, FAVORITES, LIST, NONE }

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
    /**
     * לרשום ביומן השיחות כשיחה שלא נענתה.
     * אנדרואיד רושם שיחות שנדחו כסוג "חסום", והחייגן מסתיר אותו.
     */
    val logAsMissed: Boolean = true,
    val breakthrough: Breakthrough = Breakthrough()
)

/** טריגר יומן: המצב נדלק בזמן אירוע שמסומן "בעסוק" */
data class CalendarTrigger(
    val enabled: Boolean = false,
    /** ריק = כל היומנים המסונכרנים */
    val calendarIds: Set<Long> = emptySet(),
    /** דורש שכותרת האירוע תכיל אחת מהמילים; ריק = כל אירוע עסוק */
    val keywords: List<String> = listOf("ישיבה", "ישיבת", "פ\"ע", "פ\"א"),
    val requireKeyword: Boolean = true,
    /** החלטות ידניות לאירוע בודד, לפי מזהה: "eventId:begin" */
    val forcedOn: Set<String> = emptySet(),
    val forcedOff: Set<String> = emptySet()
)

/** משווים בלי גרשיים ובלי רווחים — פ"ע, פ״ע ו-פ ע צריכים להיחשב זהים */
fun normalizeTitle(s: String): String =
    s.filterNot { it in setOf('"', '\u05F4', '\u2019', '\'', '\u05F3', ' ', '\u200f', '\u200e') }
        .lowercase()

/**
 * הגדרות מסך למצב. מה שדורש WRITE_SECURE_SETTINGS מסומן במפורש.
 */
data class ScreenConfig(
    val dimEnabled: Boolean = false,
    /** אחוז בהירות, 1..100 */
    val brightnessPercent: Int = 5,
    val disableAdaptive: Boolean = true,
    val timeoutEnabled: Boolean = false,
    /** שניות עד כיבוי מסך */
    val timeoutSeconds: Int = 15,
    val nightLight: Boolean = false,   // דורש WRITE_SECURE_SETTINGS
    val grayscale: Boolean = false,    // דורש WRITE_SECURE_SETTINGS
    /** לסיים את המצב ברגע שהשעון המעורר של המכשיר מצלצל */
    val endOnAlarm: Boolean = false
)

/** טריגר חיבור: המצב נדלק כשמתחברים למכשיר בלוטות' מסוים, למשל מערכת הרכב */
data class BtTrigger(
    val enabled: Boolean = false,
    /** כתובות MAC של מכשירים מזווגים */
    val addresses: Set<String> = emptySet()
)

/** הקראה קולית של הודעות — נועד בעיקר לנהיגה */
data class VoiceConfig(
    val readSms: Boolean = false,
    val readApps: Boolean = false,
    /** אילו אפליקציות מוקראות. ריק = ברירת המחדל שלמטה. */
    val packages: Set<String> = setOf("com.whatsapp", "org.telegram.messenger"),
    /** להקריא את שם השולח לפני תוכן ההודעה */
    val includeSender: Boolean = true
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
    val calendar: CalendarTrigger = CalendarTrigger(),
    val bluetooth: BtTrigger = BtTrigger(),
    val voice: VoiceConfig = VoiceConfig(),
    /** האם לוח הזמנים בכלל רלוונטי. בנהיגה, למשל, אין שעות קבועות. */
    val useSchedule: Boolean = true,
    /** מצב רכב של אנדרואיד — ביצרנים מסוימים זה מה שמפעיל את ממשק הנהיגה המובנה */
    val carMode: Boolean = false,
    /** אפליקציה שתיפתח כשהמצב נדלק, למשל ניווט */
    val launchPackage: String? = null,
    val launchLabel: String? = null,
    val screen: ScreenConfig = ScreenConfig(),
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
        if (!enabled || !useSchedule) return false
        if (!coversMinute(minuteOfDay)) return false
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
    put("useSchedule", useSchedule)
    put("carMode", carMode)
    put("launchPkg", launchPackage ?: JSONObject.NULL)
    put("launchLabel", launchLabel ?: JSONObject.NULL)
    put("voiceSms", voice.readSms)
    put("voiceApps", voice.readApps)
    put("voiceSender", voice.includeSender)
    put("voicePkgs", JSONArray(voice.packages.toList()))
    put("btEnabled", bluetooth.enabled)
    put("btAddrs", JSONArray(bluetooth.addresses.toList()))
    put("calEnabled", calendar.enabled)
    put("calIds", JSONArray(calendar.calendarIds.toList()))
    put("scr", JSONObject().apply {
        put("dim", screen.dimEnabled)
        put("pct", screen.brightnessPercent)
        put("adap", screen.disableAdaptive)
        put("toEn", screen.timeoutEnabled)
        put("toSec", screen.timeoutSeconds)
        put("night", screen.nightLight)
        put("gray", screen.grayscale)
        put("endAlarm", screen.endOnAlarm)
    })
    put("calWords", JSONArray(calendar.keywords))
    put("calRequire", calendar.requireKeyword)
    put("calOn", JSONArray(calendar.forcedOn.toList()))
    put("calOff", JSONArray(calendar.forcedOff.toList()))
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
        put("logMissed", call.logAsMissed)
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
        screen = (o.optJSONObject("scr") ?: JSONObject()).let { sc ->
            ScreenConfig(
                dimEnabled = sc.optBoolean("dim", false),
                brightnessPercent = sc.optInt("pct", 5).coerceIn(1, 100),
                disableAdaptive = sc.optBoolean("adap", true),
                timeoutEnabled = sc.optBoolean("toEn", false),
                timeoutSeconds = sc.optInt("toSec", 15),
                nightLight = sc.optBoolean("night", false),
                grayscale = sc.optBoolean("gray", false),
                endOnAlarm = sc.optBoolean("endAlarm", false)
            )
        },
        useSchedule = o.optBoolean("useSchedule", true),
        carMode = o.optBoolean("carMode", false),
        launchPackage = if (o.isNull("launchPkg")) null else o.optString("launchPkg").ifBlank { null },
        launchLabel = if (o.isNull("launchLabel")) null else o.optString("launchLabel").ifBlank { null },
        voice = VoiceConfig(
            readSms = o.optBoolean("voiceSms", false),
            readApps = o.optBoolean("voiceApps", false),
            includeSender = o.optBoolean("voiceSender", true),
            packages = (o.optJSONArray("voicePkgs") ?: JSONArray()).let { arr ->
                if (arr.length() == 0) VoiceConfig().packages
                else (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
        ),
        bluetooth = BtTrigger(
            enabled = o.optBoolean("btEnabled", false),
            addresses = (o.optJSONArray("btAddrs") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
        ),
        calendar = CalendarTrigger(
            enabled = o.optBoolean("calEnabled", false),
            calendarIds = (o.optJSONArray("calIds") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map { arr.getLong(it) }.toSet()
            },
            keywords = o.optJSONArray("calWords")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: CalendarTrigger().keywords,
            requireKeyword = o.optBoolean("calRequire", true),
            forcedOn = (o.optJSONArray("calOn") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            },
            forcedOff = (o.optJSONArray("calOff") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
        ),
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
            logAsMissed = c.optBoolean("logMissed", true),
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
