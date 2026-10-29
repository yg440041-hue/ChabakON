package com.chabakon.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import com.chabakon.app.vehicle.EventLog
import java.util.Locale

/** 카운트다운·정지 안내 음성. 한국어 음성이 없으면 로그만 남긴다. */
class Speaker(context: Context) {

    @Volatile private var ready = false
    private lateinit var tts: TextToSpeech
    /** 시트가 움직이는 동안 내는 경고음 (후진 경고음처럼) */
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts.setLanguage(Locale.KOREAN)
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ready) EventLog.add("한국어 음성 엔진이 없어 음성 안내를 생략해요")
            } else {
                EventLog.add("음성 엔진 초기화 실패")
            }
        }
    }

    fun say(text: String) {
        EventLog.add("🔊 $text")
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chabakon")
    }

    /** 짧은 삐 소리. 안내 음성이 나오는 중에는 건너뛴다 */
    fun beep() {
        if (ready && runCatching { tts.isSpeaking }.getOrDefault(false)) return
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
    }

    fun shutdown() {
        runCatching { tone?.release() }
        runCatching { tts.stop(); tts.shutdown() }
    }
}
