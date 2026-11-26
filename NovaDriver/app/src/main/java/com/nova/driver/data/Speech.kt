package com.nova.driver.data

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Spoken prompts (voice guidance and chat read-aloud) through the phone's TextToSpeech engine.
 * Audio is tagged as navigation guidance, so it ducks music the way Maps does. The engine starts on
 * first use; anything said before it is ready is spoken as soon as it is.
 */
object Speech {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<String, Boolean>>()
    private var counter = 0

    fun say(context: Context, text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val engine = tts ?: start(context)
        if (!ready) { pending += text to flush; return }
        speak(engine, text, flush)
    }

    fun stop() { tts?.stop() }

    private fun start(context: Context): TextToSpeech {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) { tts = null; pending.clear(); return@TextToSpeech }
            val lang = engine.isLanguageAvailable(Locale.getDefault())
            engine.language = if (lang >= TextToSpeech.LANG_AVAILABLE) Locale.getDefault() else Locale.ENGLISH
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            ready = true
            pending.toList().also { pending.clear() }.forEach { (t, f) -> speak(engine, t, f) }
        }
        tts = engine
        return engine
    }

    private fun speak(engine: TextToSpeech, text: String, flush: Boolean) {
        val clipped = text.take(TextToSpeech.getMaxSpeechInputLength() - 1)
        engine.speak(clipped, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "nova-${counter++}")
    }
}
