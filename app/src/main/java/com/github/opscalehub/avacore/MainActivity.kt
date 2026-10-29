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
import androidx.appcompat.widget.SwitchCompat
import com.github.opscalehub.avacore.service.AvaSettings
import com.github.opscalehub.avacore.service.VoiceRegistry

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvVoiceResult: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Formal text full of verbs and numbers on purpose: hearing it come out
    // as spoken Persian (with the Goftari switch on) is the point of the demo.
    private val sampleText = "سلام! من می‌خواهم امروز به خانه بروم، چون هوا خیلی سرد است. " +
        "امروز ۱۲ خرداد ۱۴۰۴ است و دمای هوا ۴ درجه است. " +
        "شما می‌توانید با من بیایید؟ نمی‌دانم آنها کجا می‌روند."

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

        val switchGoftari = findViewById<SwitchCompat>(R.id.switchGoftari)
        switchGoftari.isChecked = AvaSettings.goftariByDefault(this)
        switchGoftari.setOnCheckedChangeListener { _, on -> AvaSettings.setGoftariByDefault(this, on) }

        buildVoiceButtons(voiceButtonsContainer)
        initializeTts()
    }

    /** One button per bundled voice (today: the one Persian voice). */
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
            // The engine loads the voice in the background on first use; a tap
            // that beats it fails with onError() — say so instead of staying silent.
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        tvVoiceResult.text = "⏳ The voice is still loading — wait a few seconds and tap again."
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
        if (VoiceRegistry.voiceForName(voiceName) == null) {
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

        val speakResult = engine.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "sample_$voiceName")
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
