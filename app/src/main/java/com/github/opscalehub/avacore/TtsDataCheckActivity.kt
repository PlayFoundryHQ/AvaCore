package com.github.opscalehub.avacore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log

class TtsDataCheckActivity : Activity() {

    companion object {
        private const val TAG = "TtsDataCheckActivity"

        // ISO-3 lang-country pairs for every bundled voice, in the same order
        // as AvaTtsService.VOICES (Persian first — it's the primary language).
        private val AVAILABLE_LOCALES = arrayListOf("fas-IRN", "eng-USA", "swe-SWE")

        private val SAMPLE_TEXT = mapOf(
            "fas" to "این یک آزمایش از موتور بازگوکننده آوا است.",
            "eng" to "This is a test of the AvaCore speech engine.",
            "swe" to "Det här är ett test av AvaCore-taltjänsten.",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val resultIntent = Intent()

        Log.d(TAG, "onCreate: action=$action")

        when (action) {
            TextToSpeech.Engine.ACTION_CHECK_TTS_DATA -> {
                // Correctly-formed ISO-3 locales (lang-country), one per bundled
                // voice: Persian, English, Swedish.
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, AVAILABLE_LOCALES)
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())

                setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultIntent)
            }
            "android.speech.tts.engine.GET_SAMPLE_TEXT" -> {
                val lang = intent.getStringExtra("language")
                Log.d(TAG, "GET_SAMPLE_TEXT for language: $lang")

                val sampleText = SAMPLE_TEXT[lang] ?: SAMPLE_TEXT.getValue("fas")
                resultIntent.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleText)
                // The TTS settings screen only accepts the sample when the result
                // code is LANG_AVAILABLE; returning RESULT_OK makes it silently fall
                // back to its built-in English string (spoken by the wrong voice as
                // gibberish). This is the fix for that.
                setResult(TextToSpeech.LANG_AVAILABLE, resultIntent)
            }
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> {
                // Since it's offline and included, we just say okay
                setResult(RESULT_OK)
            }
            else -> setResult(RESULT_CANCELED)
        }

        finish()
    }
}
