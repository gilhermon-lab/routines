package com.gil.routines.data

import android.content.Context
import org.json.JSONArray

/** אחסון המצבים ב-SharedPreferences כ-JSON. פשוט, סינכרוני ומספיק לעומס הזה. */
object ModeStore {

    private const val PREFS = "routines_store"
    private const val KEY_MODES = "modes"

    fun load(ctx: Context): List<Mode> {
        val raw = prefs(ctx).getString(KEY_MODES, null) ?: return defaults()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { modeFromJson(arr.getJSONObject(it)) }
        }.getOrElse { defaults() }
    }

    fun save(ctx: Context, modes: List<Mode>) {
        val arr = JSONArray()
        modes.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_MODES, arr.toString()).apply()
    }

    fun update(ctx: Context, id: String, transform: (Mode) -> Mode): List<Mode> {
        val next = load(ctx).map { if (it.id == id) transform(it) else it }
        save(ctx, next)
        return next
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    fun defaults(): List<Mode> = listOf(
        Mode(
            id = "sleep", name = "שינה", colorArgb = 0xFF7B84D8L, enabled = true,
            start = hm(22, 45), end = hm(6, 30), days = setOf(1, 2, 3, 4, 5, 6, 7),
            actions = setOf(Actions.DND, Actions.CALL_GUARD, Actions.MUTE, Actions.BRIGHTNESS),
            call = CallConfig(
                handling = CallHandling.SILENCE,
                message = "אני ישן כרגע. אם זה דחוף — חייג שוב פעמיים ואתעורר.",
                allowContacts = true,
                contactPolicy = ContactPolicy.ALL,
                smsCooldownHours = 6,
                breakthrough = Breakthrough(true, 3, 10)
            ),
            screen = ScreenConfig(dimEnabled = true, brightnessPercent = 3, timeoutEnabled = true, timeoutSeconds = 15)
        ),
        Mode(
            id = "work", name = "עבודה", colorArgb = 0xFF58A6A6L, enabled = true,
            start = hm(8, 30), end = hm(17, 15), days = setOf(1, 2, 3, 4, 5),
            actions = setOf(Actions.DND),
            call = CallConfig(
                handling = CallHandling.REJECT,
                message = "אני בעבודה, אחזור אליך אחרי 17:00.",
                allowContacts = true,
                contactPolicy = ContactPolicy.ALL,
                smsCooldownHours = 4,
                breakthrough = Breakthrough(true, 3, 15)
            )
        ),
        Mode(
            id = "meeting", name = "ישיבה", colorArgb = 0xFFC9A24AL, enabled = true,
            start = hm(10, 0), end = hm(11, 30), days = setOf(1, 3, 5),
            actions = setOf(Actions.DND, Actions.CALL_GUARD, Actions.MUTE),
            call = CallConfig(
                handling = CallHandling.REJECT,
                message = "אני בישיבה כרגע ואחזור אליך בהקדם. אם דחוף — חייג שוב.",
                allowContacts = false,
                contactPolicy = ContactPolicy.NONE,
                smsCooldownHours = 2,
                breakthrough = Breakthrough(true, 2, 5)
            ),
            calendar = CalendarTrigger(enabled = true)
        ),
        Mode(
            id = "drive", name = "נהיגה", colorArgb = 0xFFC8815AL, enabled = false,
            start = hm(17, 30), end = hm(18, 15), days = setOf(1, 2, 3, 4, 5),
            actions = setOf(Actions.CALL_GUARD),
            call = CallConfig(
                handling = CallHandling.REJECT,
                message = "אני נוהג כרגע. אחזור אליך כשאעצור.",
                allowContacts = false,
                contactPolicy = ContactPolicy.NONE,
                smsCooldownHours = 1,
                breakthrough = Breakthrough(false, 3, 10)
            )
        ),
        Mode(
            id = "focus", name = "ריכוז", colorArgb = 0xFF9B85C9L, enabled = false,
            start = hm(14, 0), end = hm(16, 0), days = setOf(1, 2, 3, 4, 5),
            actions = setOf(Actions.DND, Actions.CALL_GUARD),
            call = CallConfig(
                handling = CallHandling.SILENCE,
                sendSms = false,
                message = "בבלוק ריכוז. אחזור אליך אחר כך.",
                allowContacts = false,
                contactPolicy = ContactPolicy.NONE,
                smsCooldownHours = 3,
                breakthrough = Breakthrough(true, 4, 20)
            )
        )
    )
}
