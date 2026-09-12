package com.github.opscalehub.avacore

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.opscalehub.avacore.service.VoiceRegistry

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvVoiceResult: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // One representative sample per bundled voice — exercises number
    // expansion / punctuation pauses for Persian, plain sentences for the
    // languages that don't need the custom NLP pipeline.
    private val sampleTextFor: Map<String, String> = mapOf(
        "fa" to "سلام! این موتور بازگوکننده آوا است. " +
            "امروز ۱۲ خرداد ۱۴۰۴ است، دمای هوا ۳۵ درجه و رطوبت ۲۰٪ می‌باشد.",
        "en" to "Hello! This is the Ava voice engine speaking English. " +
            "Today is September the twelfth, and it's thirty five degrees outside.",
        "sv" to "Hej! Det här är Ava-rösten som talar svenska. " +
            "Idag är det tolfte september och det är trettiofem grader ute.",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvVoiceResult = findViewById(R.id.tvVoiceResult)
        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)
        val btnBatteryOptimization = findViewById<Button>(R.id.btnBatteryOptimization)
        val voiceButtonsContainer = findViewById<LinearLayout>(R.id.voiceButtonsContainer)

        btnOpenSettings.setOnClickListener {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        btnBatteryOptimization.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        buildVoiceButtons(voiceButtonsContainer)
        initializeTts()
    }

    /** One button per bundled voice, in registry order (Persian first). */
    private fun buildVoiceButtons(container: LinearLayout) {
        container.removeAllViews()
        for (voice in VoiceRegistry.VOICES) {
            val button = Button(this).apply {
                text = "🔊 ${voice.voiceName}"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 8 }
                setOnClickListener { speakVoice(voice.voiceName) }
            }
            container.addView(button)
        }
    }

    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) {
                Log.e("MainActivity", "TTS initialization failed")
                return@TextToSpeech
            }
            // English/Swedish aren't bundled in the APK — the first request
            // for either kicks off a one-time on-device download
            // (ModelDownloader) that can easily outlast the framework's own
            // synthesis wait, so that first tap fails with onError() rather
            // than hanging. Surface that as a "try again" hint instead of a
            // silent/confusing failure.
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        tvVoiceResult.text = "⏳ Not cached yet — downloading this voice in the background. " +
                            "Wait a few seconds and tap again."
                    }
                }
            })
        }
    }

    /**
     * Selects an exact bundled [voiceName] via setVoice() (not the deprecated
     * locale-based setLanguage()) and speaks that voice's sample phrase.
     * Reports the setVoice() return code and resulting active voice — this is
     * exactly the diagnostic that found the setVoice()-silently-fails bug, now
     * available at a tap instead of needing adb logcat.
     */
    private fun speakVoice(voiceName: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            tvVoiceResult.text = "⏳ TTS engine not ready yet."
            return
        }
        val voiceModel = VoiceRegistry.voiceForName(voiceName)
        if (voiceModel == null) {
            tvVoiceResult.text = "❌ Unknown voice: $voiceName"
            return
        }
        val androidVoice: Voice? = engine.voices?.firstOrNull { it.name == voiceName }
        if (androidVoice == null) {
            tvVoiceResult.text = "❌ setVoice($voiceName) — engine did not report this voice via getVoices(). " +
                "Is AvaCore selected as the system TTS engine?"
            return
        }
        val setResult = engine.setVoice(androidVoice)
        val activeVoiceName = engine.voice?.name
        if (setResult != TextToSpeech.SUCCESS || activeVoiceName != voiceName) {
            tvVoiceResult.text = "❌ setVoice($voiceName) -> $setResult ; active voice is now $activeVoiceName"
            Toast.makeText(this, "setVoice() failed for $voiceName", Toast.LENGTH_SHORT).show()
            return
        }

        val sample = sampleTextFor[voiceModel.lang] ?: "Hello, this is $voiceName."
        val speakResult = engine.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "sample_$voiceName")
        tvVoiceResult.text = if (speakResult == TextToSpeech.SUCCESS) {
            "✅ Speaking $voiceName"
        } else {
            "❌ speak() returned $speakResult for $voiceName"
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)

        val defaultEngine = Settings.Secure.getString(contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        val isSelected = defaultEngine == packageName

        val statusText = StringBuilder()
        if (isSelected) {
            statusText.append("✅ AvaCore is the active TTS engine.\n")
        } else {
            statusText.append("❌ AvaCore is NOT the active engine.\n")
        }

        if (isIgnoringBattery) {
            statusText.append("✅ Battery restrictions are disabled.")
        } else {
            statusText.append("⚠️ Battery optimization is active.")
        }

        tvStatus.text = statusText.toString()
        tvStatus.setTextColor(if (isSelected && isIgnoringBattery) Color.GREEN else Color.YELLOW)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
