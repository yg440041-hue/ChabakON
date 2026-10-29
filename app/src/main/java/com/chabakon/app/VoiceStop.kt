package com.chabakon.app

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.chabakon.app.vehicle.EventLog
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 음성 정지: 시트가 움직이는 동안 "멈춰"를 들으면 즉시 정지한다.
 *
 * 1순위: Pleos SpeechToText SDK (차량 마이크, On-device 엔진, 앱 등록 불필요)
 * 2순위: Pleos 음성 서비스를 쓸 수 없는 환경이면 안드로이드 기본 음성 인식
 *
 * 한 번 말하면 인식이 끝나므로, 작동 중에는 끝날 때마다 다시 듣기를 시작한다.
 */
class VoiceStop(
    context: Context,
    private val onEngine: (Engine) -> Unit,
    private val onStop: (heard: String) -> Unit,
    /** 진단용: 지금 들은 말 / 마이크 상태를 화면에 보여주기 */
    private val onHeard: (String) -> Unit = {}
) {
    enum class Engine(val label: String) { NONE("없음"), PLEOS("Pleos 음성 인식"), ANDROID("안드로이드 음성 인식") }

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile var engine = Engine.NONE
        private set

    /** 지금 듣고 있어야 하는지 (시트 작동 중) */
    private var wanted = false
    private var listening = false
    private var errorsInRow = 0
    private var lastError: String? = null

    // 진단 기록 (한 번 작동할 때마다 초기화, 작동이 끝나면 한 줄로 요약)
    private var sessions = 0
    private var heardCount = 0
    private var errorCount = 0
    /** 소리는 들었지만 아무 말도 못 알아들은 횟수 (안드로이드 NO_MATCH / 시간 초과) */
    private var noMatch = 0
    private var maxLevel = 0.0
    private var lastHeard = ""
    private var micNoticed = false
    /** Pleos가 소리는 받는데 글자를 못 내는 시간(ms) */
    private var loudMs = 0L
    private var lastLoudAt = 0L
    private var switchTried = false
    private var pleosEvents = 0
    /** Pleos가 실제로 인식을 시작했는지 (onStartedRecognition) */
    private var pleosStarted = false
    private var pleosRetries = 0
    private var readyRequests = 0

    private fun resetStats() {
        sessions = 0; heardCount = 0; errorCount = 0; noMatch = 0; maxLevel = 0.0; lastHeard = ""; micNoticed = false
        loudMs = 0L; lastLoudAt = 0L; pleosEvents = 0; pleosRetries = 0; readyRequests = 0
    }

    private fun logSummary() {
        val mic = if (maxLevel > 0.0) "최대 %.1f".format(maxLevel) else "없음"
        EventLog.add("음성 요약(${engine.label}): 듣기 ${sessions}번, 들은 말 ${heardCount}개, 오류 ${errorCount}번, 못 알아들음 ${noMatch}번, 마이크 소리 $mic")
    }

    /** 마이크 소리 크기 기록. 처음 소리가 들어오면 한 번 알려 준다 */
    private fun onLevel(level: Double, threshold: Double) {
        if (level > maxLevel) maxLevel = level
        if (!micNoticed && level > threshold) {
            micNoticed = true
            EventLog.add("마이크에 소리가 들어와요")
            onHeard("(마이크 소리 들어옴)")
        }
        // (에뮬레이터의 안드로이드 음성 인식은 한국어를 못 알아들어서, 소리만으로 엔진을 바꾸지 않는다)
    }

    /** 인식된 문장 기록 (같은 문장 반복은 생략) */
    private fun noteHeard(text: String?, final: Boolean) {
        val t = text?.trim().orEmpty()
        if (t.isEmpty() || t == lastHeard) return
        lastHeard = t
        heardCount++
        EventLog.add(if (final) "들은 말: \"$t\"" else "듣는 중: \"$t\"")
        onHeard(t)
    }

    private var stt: SpeechToText? = null
    private var recognizer: SpeechRecognizer? = null

    // ------------------------------------------------------------ 준비

    fun init() {
        runCatching {
            val s = SpeechToText(ctx, Mode.ON_DEVICE)
            stt = s
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post { onPleosReady(s) }
                }

                override fun onFailure(errorType: ErrorType) {
                    main.post {
                        EventLog.add("Pleos 음성 인식을 쓸 수 없어요 ($errorType)")
                        useAndroid()
                    }
                }
            })
        }.onFailure { e ->
            EventLog.add("Pleos 음성 인식 초기화 실패: ${e.message}")
            main.post { useAndroid() }
        }
        // 음성 서비스가 응답하지 않는 환경 대비
        main.postDelayed({ if (engine == Engine.NONE) useAndroid() }, 6000)
    }

    private fun onPleosReady(s: SpeechToText) {
        if (engine != Engine.NONE) return
        val ok = runCatching {
            s.addListener(pleosListener)
            s.setLocale(Locale.KOREA)
        }
        if (ok.isFailure) {
            EventLog.add("Pleos 음성 인식 설정 실패: ${ok.exceptionOrNull()?.message}")
            useAndroid()
            return
        }
        setEngine(Engine.PLEOS)
        if (wanted) startListening()
    }

    private fun setEngine(e: Engine) {
        engine = e
        errorsInRow = 0
        EventLog.add("음성 정지 준비: ${e.label}")
        onEngine(e)
    }

    private fun useAndroid() {
        if (engine == Engine.ANDROID) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            if (engine == Engine.PLEOS) {
                // 대신 쓸 엔진이 없으면 Pleos를 그대로 쓴다
                EventLog.add("안드로이드 음성 인식이 없어서 Pleos 음성 인식을 계속 써요")
                if (wanted) startListening()
            } else {
                EventLog.add("이 기기에는 음성 인식 서비스가 없어 음성 정지를 쓸 수 없어요")
                onEngine(Engine.NONE)
            }
            return
        }
        releasePleos()
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            EventLog.add("마이크 권한이 없어요. 앱을 다시 켜서 마이크를 허용해 주세요")
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply { setRecognitionListener(androidListener) }
        setEngine(Engine.ANDROID)
        if (wanted) startListening()
    }

    // ------------------------------------------------------------ 켜기 / 끄기

    /** 시트 작동 중일 때 true */
    fun setActive(on: Boolean) {
        main.post {
            if (wanted != on) {
                wanted = on
                if (on) {
                    errorsInRow = 0
                    resetStats()
                    if (engine == Engine.NONE) EventLog.add("음성 인식 엔진이 없어 듣지 못해요")
                    onHeard("")
                    startListening()
                } else {
                    stopListening()
                    if (engine != Engine.NONE) logSummary()
                }
            }
        }
    }

    private fun startListening() {
        if (!wanted || listening) return
        sessions++
        if (sessions == 1) EventLog.add("듣기 시작 (${engine.label}${if (engine == Engine.PLEOS && feedMode) ", 앱 마이크로 전달" else ""})")
        when (engine) {
            Engine.PLEOS -> if (feedMode && hasMicPermission()) {
                startFeed()
            } else {
                pleosStarted = false
                runCatching { stt?.request() }
                    .onSuccess {
                        listening = true
                        main.removeCallbacks(pleosWatch)
                        main.postDelayed(pleosWatch, 5000)
                    }
                    .onFailure { e -> onRecognizerError("request 실패: ${e.message}") }
            }

            Engine.ANDROID -> runCatching {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer?.startListening(intent)
            }.onSuccess { listening = true }
                .onFailure { e -> onRecognizerError("startListening 실패: ${e.message}") }

            Engine.NONE -> Unit
        }
    }

    /** 요청하고 5초가 지나도 Pleos가 아무 응답(준비됨, 마이크 소리)이 없으면: 한 번 다시 요청, 그래도 안 되면 안드로이드로 */
    private val pleosWatch = Runnable {
        if (!wanted || engine != Engine.PLEOS || pleosStarted) return@Runnable
        if (pleosRetries < 1) {
            pleosRetries++
            EventLog.add("Pleos가 응답하지 않아 다시 요청해요")
            runCatching { stt?.stop() }
            listening = false
            startListening()
        } else if (!switchTried) {
            switchTried = true
            EventLog.add("Pleos가 응답하지 않아 안드로이드 음성 인식을 써 봐요")
            stopListening()
            useAndroid()
        }
    }

    private fun stopListening() {
        main.removeCallbacks(restart)
        main.removeCallbacks(pleosWatch)
        if (!listening) return
        listening = false
        when (engine) {
            Engine.PLEOS -> if (feeding) stopFeed() else runCatching { stt?.stop() }
            Engine.ANDROID -> runCatching { recognizer?.cancel() }
            Engine.NONE -> Unit
        }
    }

    private val restart = Runnable { startListening() }

    /** 한 번의 인식이 끝나면 작동 중인 동안 다시 듣는다 */
    private fun onRecognitionEnded() {
        // 앱 마이크 전달 중에는 한 문장이 끝나도 녹음을 계속한다
        if (feeding) return
        listening = false
        if (wanted) {
            main.removeCallbacks(restart)
            main.postDelayed(restart, 250)
        }
    }

    private fun onRecognizerError(what: String) {
        listening = false
        errorsInRow++
        errorCount++
        if (what != lastError) {
            EventLog.add("음성 인식 오류: $what")
            lastError = what
        }
        // Pleos 음성 인식이 계속 실패하면 안드로이드 기본 인식으로 바꿔 본다
        if (engine == Engine.PLEOS && errorsInRow >= 3) {
            EventLog.add("Pleos 음성 인식이 계속 실패해 안드로이드 음성 인식으로 바꿔요")
            useAndroid()
            return
        }
        if (wanted && errorsInRow < 20) {
            main.removeCallbacks(restart)
            main.postDelayed(restart, 800)
        }
    }

    // ------------------------------------------------------------ 결과 판단

    private fun check(text: String?, final: Boolean = true) {
        if (!wanted || text.isNullOrBlank()) return
        noteHeard(text, final)
        val t = text.replace(" ", "").replace(".", "").replace("!", "").replace("?", "").lowercase()
        // 짧게 외친 말은 "멈춰"를 비슷한 발음으로 잘못 알아들은 경우도 정지로 본다 (예: "엄청", "멈처")
        val shortCry = t.length <= 4 && SOUND_ALIKE.any { t.contains(it) }
        if (KEYWORDS.any { t.contains(it) } || shortCry) {
            EventLog.add("음성 인식: \"$text\"")
            wanted = false
            stopListening()
            logSummary()
            onStop(text)
        }
    }

    /** Pleos 상태 이벤트를 처음 몇 번만 로그에 남긴다 (진단용) */
    private fun notePleos(what: String) {
        if (pleosEvents < 6) { pleosEvents++; EventLog.add("Pleos: $what") }
    }

    private val pleosListener = object : ResultListener {
        override fun onReady() {
            // '준비됨' = 마이크로 듣고 있음. '인식 시작'은 사람이 말을 시작해야 온다
            main.post { pleosStarted = true; notePleos("듣는 중 (준비됨)") }
        }
        override fun onStartedRecognition() {
            main.post { errorsInRow = 0; pleosStarted = true; notePleos("인식 시작") }
        }
        override fun onUpdated(stt: String, completed: Boolean) {
            main.post {
                // 작동이 끝난 뒤 늦게 도착한 결과도 진단용으로 남긴다 (정지는 하지 않음)
                if (!wanted && completed && stt.isNotBlank()) EventLog.add("늦게 들은 말: \"${stt.trim()}\"")
                check(stt, completed)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) {
            main.post { pleosStarted = true; onLevel(rms, 1.0) }
        }
        override fun onEndedRecognition() {
            main.post { notePleos("인식 끝") ; onRecognitionEnded() }
        }
        override fun onError(errorType: ErrorType) {
            main.post { onRecognizerError(errorType.name) }
        }
    }

    private val androidListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { errorsInRow = 0 }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            onLevel(rmsdB.toDouble(), 3.0)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { check(it, final = false) }
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            list.firstOrNull()?.let { check(it, final = true) }
            // 첫 번째 후보가 아니어도 다른 후보에 '멈춰'가 있으면 멈춤
            list.drop(1).forEach { alt -> if (wanted && KEYWORDS.any { alt.replace(" ", "").contains(it) }) check(alt) }
            onRecognitionEnded()
        }

        override fun onError(error: Int) {
            when (error) {
                // 아무 말도 안 했을 때: 오류가 아니라 다시 듣기
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> { noMatch++; onRecognitionEnded() }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    listening = false
                    EventLog.add("마이크 권한이 없어 음성 정지를 쓸 수 없어요")
                }
                else -> onRecognizerError("안드로이드 오류 코드 $error")
            }
        }
    }

    // ------------------------------------------------------------ 정리

    // ------------------------------------------------------------ 앱 마이크 → Pleos 전달 (에뮬레이터용)

    /**
     * 에뮬레이터에서는 Pleos 음성 서비스가 PC 마이크 소리를 받지 못한다(마이크 크기가 계속 잡음 수준).
     * 앱은 녹음할 수 있으므로, 앱이 16kHz 모노 PCM으로 녹음해 sendAudio()로 Pleos에 넘긴다.
     * (ON_DEVICE 모드 전용 기능. 실제 차량에서는 차량 마이크를 쓰는 request()를 쓴다)
     */
    private val feedMode: Boolean = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
        Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") || Build.PRODUCT.contains("sdk")

    @Volatile private var feeding = false
    private var feedThread: Thread? = null

    private fun hasMicPermission() =
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startFeed() {
        if (feeding) { listening = true; return }
        feeding = true
        listening = true
        pleosStarted = true
        feedThread = Thread {
            val rate = 16_000
            val chunkBytes = 3_200 // 100ms
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            // 차량용 안드로이드는 입력 종류마다 연결된 마이크가 다를 수 있어서, 소리가 전혀 없으면 다음 입력으로 바꿔 본다
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "음성 인식용",
                MediaRecorder.AudioSource.MIC to "기본 마이크",
                MediaRecorder.AudioSource.DEFAULT to "기본값",
                MediaRecorder.AudioSource.VOICE_COMMUNICATION to "통화용"
            )
            var srcIndex = 0
            val buf = ByteArray(chunkBytes)
            while (feeding && srcIndex < sources.size) {
                val (src, srcName) = sources[srcIndex]
                val rec = runCatching {
                    AudioRecord(src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, chunkBytes * 8))
                }.getOrNull()
                if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec?.release()
                    main.post { EventLog.add("앱 마이크 입력($srcName)을 열 수 없어요") }
                    srcIndex++
                    continue
                }
                rec.startRecording()
                main.post { EventLog.add("앱 마이크 입력: $srcName") }
                val pre = ArrayDeque<ByteArray>()
                var inSpeech = false
                var quiet = 0
                var speechChunks = 0
                var chunks = 0
                var loudest = 0.0
                var switchSource = false
                // 주변 소음 크기를 계속 재서, 소음보다 확실히 큰 소리만 말소리로 본다
                var noise = 300.0
                while (feeding) {
                    var read = 0
                    while (read < chunkBytes && feeding) {
                        val n = rec.read(buf, read, chunkBytes - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read < chunkBytes) continue
                    val chunk = buf.copyOf()
                    val level = rms(chunk)
                    chunks++
                    if (level > loudest) loudest = level
                    main.post { onLevel(level, 600.0) }
                    // 2초 동안 완전한 무음(디지털 0 근처)이면 이 입력에는 마이크가 연결되지 않은 것
                    if (chunks == 20 && loudest < SILENT_LEVEL && srcIndex < sources.size - 1) {
                        val l = loudest
                        main.post { EventLog.add("'$srcName' 입력에 소리가 없어요(최대 %.1f). 다른 입력으로 바꿔요".format(l)) }
                        switchSource = true
                        break
                    }
                    val startLevel = max(SPEECH_LEVEL, noise * 3)
                    val endLevel = max(SPEECH_LEVEL * 0.6, noise * 2)
                    if (!inSpeech) {
                        noise = noise * 0.9 + level * 0.1
                        // 말이 시작되기 전 0.3초도 같이 보내서 첫 소리가 잘리지 않게
                        pre.addLast(chunk)
                        if (pre.size > 3) pre.removeFirst()
                        if (level > startLevel) {
                            inSpeech = true; quiet = 0; speechChunks = 0
                            pre.forEach { sendPcm(it) }
                            pre.clear()
                        }
                    } else {
                        sendPcm(chunk)
                        speechChunks++
                        quiet = if (level < endLevel) quiet + 1 else 0
                        // 0.4초 조용하거나 2초가 지나면 한 문장으로 끊어 바로 인식시킨다 ("멈춰"는 짧은 말)
                        if (quiet >= 4 || speechChunks >= 20) {
                            runCatching { stt?.completeAudioSend() }
                            inSpeech = false
                        }
                    }
                }
                if (inSpeech) runCatching { stt?.completeAudioSend() }
                runCatching { rec.stop() }
                rec.release()
                if (switchSource) srcIndex++ else break
            }
            if (feeding && srcIndex >= sources.size) {
                feeding = false
                main.post { onRecognizerError("앱 마이크를 열 수 없어요") }
            }
        }.apply { isDaemon = true; name = "ChabakVoiceFeed"; start() }
    }

    private fun sendPcm(bytes: ByteArray) {
        runCatching { stt?.sendAudio(bytes) }.onFailure { e ->
            main.post { EventLog.add("Pleos로 소리 전달 실패: ${e.message}") }
        }
    }

    private fun stopFeed() {
        feeding = false
        feedThread = null
    }

    private fun rms(b: ByteArray): Double {
        var sum = 0.0
        var i = 0
        while (i < b.size - 1) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xff)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        return sqrt(sum / (b.size / 2))
    }

    private fun releasePleos() {
        stopFeed()
        val s = stt ?: return
        runCatching { s.stop() }
        runCatching { s.removeListener(pleosListener) }
        runCatching { s.release() }
        stt = null
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        wanted = false
        listening = false
        releasePleos()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    companion object {
        /** 이 말이 들어 있으면 정지 (띄어쓰기 무시) */
        val KEYWORDS = listOf("멈춰", "멈춤", "멈추", "정지", "스톱", "스탑", "그만", "잠깐", "stop")

        /** 짧게 외쳤을 때만 정지로 보는, "멈춰"와 발음이 비슷한 말 (Pleos가 실제로 이렇게 알아들은 예) */
        val SOUND_ALIKE = listOf("엄청", "엄춰", "멈처", "멈쳐", "머춰", "범춰", "멈출", "멈쳐요")

        /** 앱 마이크 전달에서 말소리로 보는 소리 크기 (16비트 PCM RMS) */
        const val SPEECH_LEVEL = 500.0

        /** 이보다 작으면 마이크가 연결되지 않은 완전한 무음으로 본다 */
        const val SILENT_LEVEL = 8.0
    }
}
