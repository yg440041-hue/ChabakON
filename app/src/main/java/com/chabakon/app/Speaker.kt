package com.chabakon.app

import android.content.Context
import android.speech.tts.TextToSpeech
import com.chabakon.app.vehicle.EventLog
import java.util.Locale

/** 카운트다운·정지 안내 음성. 한국어 음성이 없으면 로그만 남긴다. */
class Speaker(context: Context) {

    @Volatile private var ready = false
    private lateinit var tts: TextToSpeech

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

    fun shutdown() {
        runCatching { tts.stop(); tts.shutdown() }
    }
}
