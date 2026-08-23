package com.gil.routines.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * הקראה קולית של הודעות נכנסות.
 *
 * המנוע מאותחל פעם אחת ונשאר חי, כי אתחול לוקח כמעט שנייה —
 * ובנהיגה עיכוב כזה בין הודעה להקראה מורגש מאוד.
 */
object VoiceReader {

    private const val TAG = "VoiceReader"
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    fun speak(ctx: Context, text: String) {
        if (text.isBlank()) return

        if (tts == null) {
            tts = TextToSpeech(ctx.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    runCatching {
                        val he = Locale("he", "IL")
                        val res = tts?.setLanguage(he)
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale.getDefault())
                        }
                        // ערוץ מדיה, כדי שההקראה תעבור למערכת השמע של הרכב
                        tts?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                    }.onFailure { Log.w(TAG, "init", it) }

                    while (pending.isNotEmpty()) say(pending.removeFirst())
                } else {
                    pending.clear()
                }
            }
        }

        if (ready) say(text) else pending.addLast(text)
    }

    private fun say(text: String) {
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "routines-" + System.currentTimeMillis())
        }.onFailure { Log.w(TAG, "speak", it) }
    }

    fun stop() = runCatching { tts?.stop() }
}
