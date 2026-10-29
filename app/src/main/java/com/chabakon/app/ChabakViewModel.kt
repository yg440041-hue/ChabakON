package com.chabakon.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chabakon.app.vehicle.CarState
import com.chabakon.app.vehicle.EventLog
import com.chabakon.app.vehicle.Move
import com.chabakon.app.vehicle.PleosGateway
import com.chabakon.app.vehicle.SeatAxis
import com.chabakon.app.vehicle.SeatKey
import com.chabakon.app.vehicle.SeatPos
import com.chabakon.app.vehicle.SimGateway
import com.chabakon.app.vehicle.VehicleGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

enum class Screen(val title: String) {
    HOME("홈"),
    CHECK("안전 점검"),
    FLATTEN("평탄화"),
    SLEEP("잠자리 모드"),
    MORNING("굿모닝 복귀")
}

enum class StepStatus { WAIT, RUNNING, HELD, DONE, CONFIRMED, SKIPPED }

/** HELD: '여기까지'로 멈추고 계속하기/다음 단계를 기다리는 중 */
enum class FlatPhase { IDLE, COUNTDOWN, RUNNING, HELD, PAUSED, REVERTING, DONE }

data class FlatStep(
    val title: String,
    val keys: List<SeatKey>,
    val move: Move,
    val status: StepStatus = StepStatus.WAIT,
    val progress: Float = 0f,
    val note: String = "",
    /** 복귀 단계: 좌석별 목표 위치(평탄화 전 위치). null 이면 평탄화 단계 */
    val targets: Map<SeatKey, Int>? = null,
    /** 복귀 단계: 저장 위치가 없을 때 쓸 운전석 메모리 번호 */
    val memorySlot: Int? = null
)

data class SleepPrefs(
    val climate: Boolean = true,
    val window: Boolean = true,
    val seatHeat: Boolean = false,
    val doorLock: Boolean = true,
    val tempC: Float = 22f,
    val floorPct: Int = 30,
    val alarm: Boolean = false,
    /** 기상 알람 시각 (24시간제 시 0~23, 분 0~59) */
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    /** 화면 취침등 (앰비언트 밝기 제어가 SDK 에 없어 차량 화면으로 대체) */
    val nightLight: Boolean = true,
    /** 화면 취침등 밝기 (10~60%) */
    val nightLevel: Int = 30
) {
    /** '전체 선택' 대상 4개 항목(앰비언트는 SDK 미지원이라 제외) 중 켜진 개수 */
    val onCount: Int get() = listOf(climate, window, nightLight, seatHeat, doorLock).count { it }
}

data class CheckItem(val title: String, val sub: String, val ok: Boolean?)

/** 점검 목록 앞쪽의 차량 센서 항목 수 (2열·벨트·기어·옆문). 그 뒤는 어린이 보호 잠금 */
const val SENSOR_ITEMS = 4

/** 시트 한 단계가 최소로 걸리는 시간(ms). 너무 빨리 끝나 멈출 틈이 없지 않도록 */
const val STEP_MIN_MS = 4000L

/** 시트가 이만큼(90%) 가기 전에 멈추면 '끼임'으로 보고 즉시 정지한다 */
const val JAM_LIMIT = 0.9f

data class UiState(
    val screen: Screen = Screen.HOME,
    val simulation: Boolean = false,
    val car: CarState = CarState(),
    val manualConfirm: Boolean = false,
    /** 좌석 사이·발밑에 아이, 반려동물, 물건이 없는지 눈으로 확인했는지 */
    val areaClear: Boolean = false,
    /** 음성 정지('멈춰')를 쓸 수 있는지 */
    val voiceReady: Boolean = false,
    /** 진단용: 마지막으로 들은 말 (디버그 빌드 화면에만 표시) */
    val voiceHeard: String = "",
    val childUnlocked: Boolean = false,
    /** 어린이 보호 잠금 사용 여부 (기본 켜짐) */
    val childLockEnabled: Boolean = true,
    /** 기본 PIN(0000)을 아직 쓰고 있는지 */
    val defaultPin: Boolean = true,
    /** 설정·PIN 창 / PIN 초기화 창이 열려 있는지 */
    val settingsOpen: Boolean = false,
    val resetOpen: Boolean = false,
    val steps: List<FlatStep> = emptyList(),
    val phase: FlatPhase = FlatPhase.IDLE,
    /** 진행 화면이 '운전 자세 복귀' 인지 (false 면 평탄화) */
    val returning: Boolean = false,
    val countdown: Int = 0,
    val stopReason: String? = null,
    val prefs: SleepPrefs = SleepPrefs(),
    val sleepApplied: Boolean = false,
    /** 평탄화를 했고 아직 운전 자세로 돌아가지 않음 (굿모닝 복귀 사용 가능) */
    val campOn: Boolean = false,
    /** 평탄화를 끝까지 마친 상태 (다시 평탄화할 필요 없음) */
    val flatDone: Boolean = false,
    /** 잠자리 모드를 켰다가 끈 적이 있는지 ('잠자리 모드 꺼짐' 표시용) */
    val sleepEnded: Boolean = false,
    /** 마지막으로 차량에 보낸 잠자리 설정 요약 */
    val lastApplied: String? = null,
    /** 마지막으로 차량에 적용한 잠자리 설정 (변경 여부 표시용) */
    val appliedPrefs: SleepPrefs? = null,
    val fanReduced: Boolean = false,
    val condensationFixed: Boolean = false,
    val ratePerHour: Float = 0f,
    val rateMeasured: Boolean = false,
    val toast: String? = null,
    /** 화면 취침등이 떠 있는지 */
    val nightLightOn: Boolean = false,
    /** 일출 화면 진행률(0~1). null 이면 일출 중이 아님 */
    val sunrise: Float? = null
) {
    fun checkItems(): List<CheckItem> = listOf(
        CheckItem("2열 비어 있음", "착석 센서", car.row2Occupied?.let { !it }),
        CheckItem("안전벨트 해제", "전 좌석", car.beltBuckled?.let { !it }),
        CheckItem("기어 P", "정차 중", car.gearPark),
        CheckItem("옆문 닫힘", "트렁크는 열어도 돼요", car.doorOpen?.let { !it }),
        if (childLockEnabled) {
            CheckItem(
                "어린이 보호 잠금 해제",
                if (defaultPin) "기본 PIN 0000 · 'PIN 설정'에서 변경" else "해제 후 5분 유지",
                childUnlocked
            )
        } else {
            CheckItem("어린이 보호 잠금", "사용 안 함", true)
        },
        CheckItem("좌석 사이·발밑 비어 있음", "아이, 반려동물, 물건", areaClear)
    )

    /** 차량에서 확인할 수 없는(센서 없음) 항목이 있는지 */
    fun hasUnknown(): Boolean = checkItems().take(SENSOR_ITEMS).any { it.ok == null }

    fun canStart(): Boolean {
        val items = checkItems()
        if (items.any { it.ok == false }) return false
        if (hasUnknown() && !manualConfirm) return false
        return true
    }

    /** 배터리 1시간 소모량(%). 실측값이 없으면 켜 둔 항목 기준 추정값 */
    fun effectiveRate(): Float {
        if (ratePerHour > 0f) return ratePerHour
        return (if (prefs.climate) 1.7f else 0.4f) + (if (prefs.seatHeat) 0.5f else 0f)
    }

    /** 하한선까지 버틸 수 있는 시간 */
    fun hoursLeft(): Float? {
        val b = car.batteryPct ?: return null
        return ((b - prefs.floorPct) / effectiveRate()).coerceAtLeast(0f)
    }

    val touchStopActive: Boolean
        get() = phase == FlatPhase.COUNTDOWN || phase == FlatPhase.RUNNING || phase == FlatPhase.REVERTING
}

class ChabakViewModel(app: Application) : AndroidViewModel(app) {

    private val store = app.getSharedPreferences("chabakon", Context.MODE_PRIVATE)
    private val speaker = Speaker(app)

    private val _ui = MutableStateFlow(
        UiState(
            simulation = store.getBoolean("sim", false),
            prefs = loadPrefs(),
            childLockEnabled = store.getBoolean("lock_on", true),
            defaultPin = store.getString("pin", "0000") == "0000",
            campOn = store.getBoolean("camp_on", false) || store.contains("seat_memo"),
            flatDone = store.getBoolean("flat_done", false)
        )
    )
    val ui: StateFlow<UiState> = _ui
    val log: StateFlow<List<String>> = EventLog.lines

    private var gateway: VehicleGateway = createGateway(_ui.value.simulation)
    private var collectJob: Job? = null
    private var runJob: Job? = null
    private var guardJob: Job? = null
    private var relockJob: Job? = null

    /** 평탄화 시작 전 좌석 위치(원위치용) */
    private val startPos = HashMap<SeatKey, Int>()
    /** 축마다 FORWARD 명령이 값을 키우는 방향인지(움직임을 보고 학습) */
    private val forwardUp = HashMap<SeatKey, Boolean>()
    @Volatile private var hereRequested = false
    private val batterySamples = ArrayList<Pair<Long, Int>>()

    val simGateway: SimGateway? get() = gateway as? SimGateway

    /** 이번 작동에서 실제로 움직인 적이 있는 단계 (끼임 판단용) */
    private val movedSteps = HashSet<String>()
    private var revertMoved = false

    /** 음성 정지: 시트 작동 중 '멈춰'를 들으면 즉시 정지 */
    private val voice = VoiceStop(
        app,
        onEngine = { e -> _ui.update { it.copy(voiceReady = e != VoiceStop.Engine.NONE) } },
        onStop = { emergencyStop("음성 명령") },
        onHeard = { t -> _ui.update { it.copy(voiceHeard = t) } }
    )

    init {
        loadSeatMemo()
        attachGateway()
        voice.init()
        // 시트가 움직이는 동안(카운트다운 포함)만 듣는다
        viewModelScope.launch {
            _ui.map { it.touchStopActive }.distinctUntilChanged().collect { voice.setActive(it) }
        }
        // 시트가 움직이는 동안 주변 사람이 알 수 있게 경고음
        viewModelScope.launch {
            while (isActive) {
                delay(900)
                val s = _ui.value
                if ((s.phase == FlatPhase.RUNNING || s.phase == FlatPhase.REVERTING) && s.countdown == 0) speaker.beep()
            }
        }
    }

    // ---------------------------------------------------------------- 평탄화 전 위치 저장 (앱을 다시 켜도 아침에 복귀 가능)

    private fun encode(map: Map<SeatKey, Any>): String =
        map.entries.joinToString(";") { "${it.key.seat.name}:${it.key.axis.name}=${it.value}" }

    private fun decodeEntries(raw: String?): List<Pair<SeatKey, String>> =
        raw.orEmpty().split(";").mapNotNull { e ->
            val (k, v) = e.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            val (seat, axis) = k.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { SeatKey(SeatPos.valueOf(seat), SeatAxis.valueOf(axis)) to v }.getOrNull()
        }

    private fun saveSeatMemo() {
        store.edit()
            .putString("seat_memo", encode(startPos))
            .putString("seat_dir", encode(forwardUp))
            .putBoolean("camp_on", true)
            .apply()
        if (!_ui.value.campOn) _ui.update { it.copy(campOn = true) }
    }

    private fun loadSeatMemo() {
        decodeEntries(store.getString("seat_memo", null)).forEach { (k, v) -> v.toIntOrNull()?.let { startPos[k] = it } }
        decodeEntries(store.getString("seat_dir", null)).forEach { (k, v) -> forwardUp[k] = v.toBoolean() }
        if (startPos.isNotEmpty()) EventLog.add("저장된 평탄화 전 시트 위치 ${startPos.size}개 불러옴")
    }

    private fun clearSeatMemo() {
        store.edit().remove("seat_memo").remove("seat_dir").remove("camp_on").remove("flat_done").apply()
        _ui.update { it.copy(campOn = false, flatDone = false) }
    }

    private fun createGateway(sim: Boolean): VehicleGateway =
        if (sim) SimGateway() else PleosGateway(getApplication<Application>())

    private fun attachGateway() {
        gateway.start()
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            gateway.state.collect { car ->
                _ui.update { it.copy(car = car) }
                val s = _ui.value
                if (s.phase == FlatPhase.RUNNING || s.phase == FlatPhase.REVERTING) {
                    if (car.doorOpen == true) emergencyStop("문 열림 감지")
                    else if (car.row2Occupied == true) emergencyStop("2열 착석 감지")
                }
            }
        }
    }

    fun setSimulation(on: Boolean) {
        if (on == _ui.value.simulation) return
        runJob?.cancel()
        runCatching { gateway.stopAllSeats() }
        gateway.release()
        gateway = createGateway(on)
        store.edit().putBoolean("sim", on).apply()
        startPos.clear()
        forwardUp.clear()
        _ui.update {
            it.copy(simulation = on, car = CarState(), steps = emptyList(), phase = FlatPhase.IDLE, stopReason = null)
        }
        EventLog.add(if (on) "시뮬레이션 모드로 전환" else "실제 차량(SDK) 모드로 전환")
        attachGateway()
    }

    fun go(screen: Screen) {
        if (_ui.value.touchStopActive) return
        // 홈으로 나가면 다음에 들어올 때 좌석 주변을 다시 확인하게
        _ui.update { it.copy(screen = screen, areaClear = if (screen == Screen.HOME) false else it.areaClear) }
    }

    fun setManualConfirm(v: Boolean) = _ui.update { it.copy(manualConfirm = v) }

    fun setAreaClear(v: Boolean) = _ui.update { it.copy(areaClear = v) }

    /** 마이크 없이 음성 정지 흐름을 시험하는 버튼용 (디버그 빌드에서만 보임) */
    fun testVoiceStop() {
        EventLog.add("음성 정지 테스트 버튼")
        emergencyStop("음성 명령")
    }

    /** '다시 확인': 센서 값을 새로 읽고, 눈으로 확인한 항목도 처음부터 다시 확인하게 한다 */
    fun refreshCar() {
        _ui.update { it.copy(areaClear = false, manualConfirm = false) }
        gateway.refresh()
    }

    // ---------------------------------------------------------------- 어린이 보호 잠금

    private fun storedPin(): String = store.getString("pin", "0000") ?: "0000"

    /** 시트를 움직이는 기능을 쓸 수 있는 상태인지 (잠금을 꺼 두었거나 풀려 있음) */
    fun isUnlocked(): Boolean = !_ui.value.childLockEnabled || _ui.value.childUnlocked

    /** PIN 이 맞으면 풀고, 5분 뒤(작동 중이면 끝난 뒤) 자동으로 다시 잠근다 */
    fun tryPin(pin: String): Boolean {
        val ok = pin == storedPin()
        if (!ok) return false
        _ui.update {
            it.copy(
                childUnlocked = true,
                toast = if (it.defaultPin) "기본 PIN 사용 중 · 'PIN 설정'에서 바꿔 주세요" else it.toast
            )
        }
        EventLog.add("어린이 보호 잠금 해제 (5분간 유지)")
        relockJob?.cancel()
        relockJob = viewModelScope.launch {
            delay(5 * 60 * 1000L)
            while (_ui.value.phase != FlatPhase.IDLE && _ui.value.phase != FlatPhase.DONE) delay(1000)
            relock("보호 잠금이 다시 켜졌어요")
        }
        return true
    }

    /**
     * PIN 초기화(0000). 차량에서만 가능한 본인 확인: 운전석에서 브레이크 페달을 밟은 채 버튼을 5초 누른다.
     * 뒷자리 아이는 페달에 닿기 어렵다는 점을 이용한다.
     */
    fun openSettings(open: Boolean) {
        if (open && _ui.value.touchStopActive) return
        _ui.update { it.copy(settingsOpen = open, resetOpen = if (open) false else it.resetOpen) }
    }

    fun openReset(open: Boolean) = _ui.update { it.copy(resetOpen = open, settingsOpen = if (open) false else it.settingsOpen) }

    fun resetPin(): Boolean {
        if (_ui.value.car.brakePressed != true) return false
        store.edit().putString("pin", "0000").apply()
        relockJob?.cancel()
        _ui.update {
            it.copy(
                defaultPin = true, childUnlocked = false, resetOpen = false,
                toast = "PIN이 0000으로 초기화됐어요"
            )
        }
        EventLog.add("어린이 보호 잠금 PIN 초기화 (브레이크 페달 확인)")
        return true
    }

    private fun relock(reason: String? = null) {
        relockJob?.cancel()
        if (!_ui.value.childLockEnabled || !_ui.value.childUnlocked) return
        _ui.update { it.copy(childUnlocked = false, manualConfirm = false) }
        EventLog.add(reason ?: "작동이 끝나 어린이 보호 잠금이 다시 켜졌어요")
        if (reason != null) _ui.update { it.copy(toast = reason) }
    }

    /**
     * 보호 잠금 설정 변경. 아이가 끌 수 없도록 현재 PIN 을 반드시 확인한다.
     * @return 오류 메시지, 성공하면 null
     */
    fun updateSecurity(currentPin: String, enabled: Boolean, newPin: String): String? {
        if (currentPin != storedPin()) return "현재 PIN이 틀렸어요"
        if (newPin.isNotEmpty() && (newPin.length != 4 || !newPin.all { it.isDigit() })) return "새 PIN은 숫자 4자리"
        val e = store.edit().putBoolean("lock_on", enabled)
        if (newPin.isNotEmpty()) e.putString("pin", newPin)
        e.apply()
        _ui.update {
            it.copy(
                childLockEnabled = enabled,
                childUnlocked = false,
                settingsOpen = false,
                defaultPin = (if (newPin.isNotEmpty()) newPin else currentPin) == "0000",
                toast = if (enabled) "저장했어요" else "보호 잠금을 껐어요"
            )
        }
        relockJob?.cancel()
        EventLog.add("보호 잠금 설정 변경: 사용=${enabled}, PIN 변경=${newPin.isNotEmpty()}")
        return null
    }

    fun dismissToast() = _ui.update { it.copy(toast = null) }

    // ---------------------------------------------------------------- 평탄화

    private fun buildSteps(): List<FlatStep> {
        val avail = _ui.value.car.bounds.keys
        fun keys(axis: SeatAxis, vararg seats: SeatPos) = seats.map { SeatKey(it, axis) }.filter { it in avail }
        return listOf(
            FlatStep("앞좌석 앞으로 이동", keys(SeatAxis.FORE_AFT, SeatPos.ROW1_LEFT, SeatPos.ROW1_RIGHT), Move.FORWARD),
            FlatStep("앞좌석 등받이 세우기", keys(SeatAxis.BACKREST, SeatPos.ROW1_LEFT, SeatPos.ROW1_RIGHT), Move.FORWARD),
            FlatStep("헤드레스트 내리기", keys(SeatAxis.HEADREST, SeatPos.ROW1_LEFT, SeatPos.ROW1_RIGHT), Move.BACKWARD),
            FlatStep("2열 등받이 접기", keys(SeatAxis.BACKREST, SeatPos.ROW2_LEFT, SeatPos.ROW2_RIGHT), Move.FORWARD)
        ).map { if (it.keys.isEmpty()) it.copy(note = "자동 미지원 · 손으로 해 주세요") else it }
    }

    fun startFlatten() {
        if (_ui.value.flatDone) { _ui.update { it.copy(toast = "이미 평탄화되어 있어요") }; return }
        val s = _ui.value
        if (!s.canStart() || s.touchStopActive) return
        val steps = buildSteps()
        if (steps.all { it.keys.isEmpty() }) {
            _ui.update {
                it.copy(toast = "시트 정보를 불러오는 중이거나, 자동 제어를 지원하지 않는 차량이에요")
            }
            return
        }
        // 이전 평탄화가 아직 복귀되지 않았으면 '처음 위치' 기록을 지우지 않는다 (굿모닝 복귀용)
        if (!s.campOn) startPos.clear()
        movedSteps.clear()
        _ui.update { it.copy(screen = Screen.FLATTEN, returning = false, steps = steps, phase = FlatPhase.COUNTDOWN, stopReason = null) }
        runJob?.cancel()
        runJob = viewModelScope.launch {
            countdown("3초 뒤 시트가 움직입니다. 뒷좌석을 확인하세요.")
            _ui.update { it.copy(phase = FlatPhase.RUNNING) }
            runSteps()
        }
    }

    private suspend fun countdown(message: String) {
        _ui.update { it.copy(phase = FlatPhase.COUNTDOWN) }
        speaker.say(message)
        for (n in 3 downTo 1) {
            _ui.update { it.copy(countdown = n) }
            delay(1000)
        }
        _ui.update { it.copy(countdown = 0) }
    }

    private suspend fun runSteps() {
        while (true) {
            val idx = _ui.value.steps.indexOfFirst { it.status == StepStatus.WAIT || it.status == StepStatus.RUNNING }
            if (idx < 0) break
            runStep(idx)
            if (!_ui.value.returning) saveSeatMemo()
            if (_ui.value.phase != FlatPhase.RUNNING) return
        }
        _ui.update { it.copy(phase = FlatPhase.DONE) }
        if (_ui.value.returning) {
            speaker.say("운전 자세로 돌아왔어요. 좋은 하루 보내세요.")
            EventLog.add("운전 자세 복귀 완료")
            startPos.clear()
            originPos.clear()
            clearSeatMemo()
            _ui.update { it.copy(sleepApplied = false) }
        } else {
            speaker.say("평탄화가 끝났어요. 매트를 깔고 잠자리 모드로 넘어가세요.")
            store.edit().putBoolean("flat_done", true).apply()
            _ui.update { it.copy(flatDone = true) }
            EventLog.add("평탄화 완료")
        }
        _ui.update { it.copy(areaClear = false) }
        relock()
    }

    /** 복귀 단계 시작 시점의 위치(진행률 계산용) */
    private val originPos = HashMap<SeatKey, Int>()

    /** 평탄화의 역순으로 운전 자세 복귀 단계를 만든다 */
    private fun buildReturnSteps(): List<FlatStep> {
        val front = listOf(SeatPos.ROW1_LEFT, SeatPos.ROW1_RIGHT)
        val rear = listOf(SeatPos.ROW2_LEFT, SeatPos.ROW2_RIGHT)
        fun group(title: String, axis: SeatAxis, seats: List<SeatPos>): FlatStep? {
            val keys = startPos.keys.filter { it.axis == axis && it.seat in seats }
            if (keys.isEmpty()) return null
            return FlatStep(title, keys, Move.STOP, targets = keys.associateWith { startPos.getValue(it) })
        }
        val steps = listOfNotNull(
            group("2열 등받이 세우기", SeatAxis.BACKREST, rear),
            group("헤드레스트 올리기", SeatAxis.HEADREST, front),
            group("앞좌석 등받이 원래 각도로", SeatAxis.BACKREST, front),
            group("앞좌석 원래 위치로 이동", SeatAxis.FORE_AFT, front)
        )
        if (steps.isNotEmpty()) return steps

        // 저장된 위치가 없을 때: 2열은 끝까지 세우고, 헤드레스트는 올리고, 앞좌석은 운전석 메모리로
        EventLog.add("저장된 평탄화 전 위치가 없어 기본 운전 자세 순서로 복귀")
        val avail = _ui.value.car.bounds.keys
        fun keys(axis: SeatAxis, vararg seats: SeatPos) = seats.map { SeatKey(it, axis) }.filter { it in avail }
        return listOfNotNull(
            keys(SeatAxis.BACKREST, SeatPos.ROW2_LEFT, SeatPos.ROW2_RIGHT).takeIf { it.isNotEmpty() }
                ?.let { FlatStep("2열 등받이 세우기", it, Move.BACKWARD) },
            keys(SeatAxis.HEADREST, SeatPos.ROW1_LEFT, SeatPos.ROW1_RIGHT).takeIf { it.isNotEmpty() }
                ?.let { FlatStep("헤드레스트 올리기", it, Move.FORWARD) },
            FlatStep("앞좌석 운전 자세로", emptyList(), Move.STOP, memorySlot = 1)
        )
    }

    /** 복귀 단계: 저장된 목표 위치까지 움직이며 막대를 채운다 */
    private suspend fun runTargetStep(i: Int, step: FlatStep) {
        val targets = step.targets ?: return
        val car0 = _ui.value.car
        step.keys.forEach { k -> if (k !in originPos) car0.positions[k]?.let { originPos[k] = it } }
        updateStep(i) { it.copy(status = StepStatus.RUNNING) }
        hereRequested = false
        step.keys.forEach { k ->
            val cur = car0.positions[k] ?: return@forEach
            val t = targets[k] ?: return@forEach
            if (abs(cur - t) <= 1) return@forEach
            val up = forwardUp[k] ?: true
            gateway.moveSeat(k, if ((cur < t) == up) Move.FORWARD else Move.BACKWARD)
        }
        EventLog.add("'${step.title}' 시작")

        var t0 = System.currentTimeMillis()
        var lastSig = signature(step.keys)
        var firstSig = lastSig
        var lastChange = t0
        var progressAtStart = _ui.value.steps[i].progress
        while (true) {
            delay(100)
            if (_ui.value.phase != FlatPhase.RUNNING) return
            val now = System.currentTimeMillis()
            val car = _ui.value.car
            step.keys.forEach { k ->
                val c = car.positions[k]
                val t = targets[k]
                if (c != null && t != null && abs(c - t) <= 1) gateway.moveSeat(k, Move.STOP)
            }
            val values = step.keys.mapNotNull { k ->
                val o = originPos[k] ?: return@mapNotNull null
                val c = car.positions[k] ?: return@mapNotNull null
                val t = targets[k] ?: return@mapNotNull null
                val total = abs(o - t).toFloat()
                if (total == 0f) 1f else (1f - abs(c - t) / total).coerceIn(0f, 1f)
            }
            val real = if (values.isEmpty()) ((now - t0) / 6000f).coerceIn(0f, 1f) else values.average().toFloat()
            val pace = progressAtStart + (1f - progressAtStart) * ((now - t0) / STEP_MIN_MS.toFloat()).coerceIn(0f, 1f)
            val p = if (signature(step.keys) == firstSig) pace else minOf(pace, maxOf(progressAtStart, real))
            updateStep(i) { it.copy(progress = p) }

            if (hereRequested) {
                hereRequested = false
                step.keys.forEach { gateway.moveSeat(it, Move.STOP) }
                if (!holdAt(i, p)) return
                // 계속하기: 목표 위치 쪽으로 다시 움직임
                val carN = _ui.value.car
                step.keys.forEach { k ->
                    val cur = carN.positions[k] ?: return@forEach
                    val tg = targets[k] ?: return@forEach
                    if (abs(cur - tg) <= 1) return@forEach
                    val up = forwardUp[k] ?: true
                    gateway.moveSeat(k, if ((cur < tg) == up) Move.FORWARD else Move.BACKWARD)
                }
                t0 = System.currentTimeMillis(); progressAtStart = p
                firstSig = signature(step.keys); lastSig = firstSig; lastChange = t0
                continue
            }

            // 위치를 알 수 있는 축이 하나도 없으면 시간 기준(약 6초)으로 진행
            val known = step.keys.filter { car.positions[it] != null && targets[it] != null }
            val allBack = known.isNotEmpty() && known.all { k -> abs(car.positions.getValue(k) - targets.getValue(k)) <= 1 }
            val sig = signature(step.keys)
            if (sig != lastSig) {
                lastSig = sig
                lastChange = now
            }
            if (sig != firstSig) movedSteps += step.title
            val stalled = sig.isNotEmpty() && now - lastChange > 1500 && now - t0 > 1500
            // 움직이던 시트가 목표 전에 멈춤 = 무언가 끼었을 수 있음
            if (stalled && !allBack && values.isNotEmpty() && step.title in movedSteps && values.min() < JAM_LIMIT) {
                jamStop(step.title, values.min())
                return
            }
            val minDone = now - t0 >= STEP_MIN_MS
            if (((allBack || p >= 0.99f || stalled) && minDone) || now - t0 > 20_000) {
                step.keys.forEach { gateway.moveSeat(it, Move.STOP) }
                updateStep(i) { it.copy(status = StepStatus.DONE, progress = 1f) }
                return
            }
        }
    }

    /** 복귀 단계: 저장 위치가 없을 때 운전석 메모리 호출 (약 4초 진행 표시) */
    private suspend fun runMemoryStep(i: Int, slot: Int) {
        updateStep(i) { it.copy(status = StepStatus.RUNNING) }
        hereRequested = false
        gateway.recallDriverMemory(slot)
        var t0 = System.currentTimeMillis()
        var progressAtStart = _ui.value.steps[i].progress
        while (true) {
            delay(100)
            if (_ui.value.phase != FlatPhase.RUNNING) return
            val p = maxOf(progressAtStart, ((System.currentTimeMillis() - t0) / 4000f).coerceIn(0f, 1f))
            updateStep(i) { it.copy(progress = p) }
            if (hereRequested) {
                hereRequested = false
                gateway.stopAllSeats()
                if (!holdAt(i, p)) return
                gateway.recallDriverMemory(slot)
                t0 = System.currentTimeMillis(); progressAtStart = p
                continue
            }
            if (p >= 1f) {
                updateStep(i) { it.copy(status = StepStatus.DONE, progress = 1f) }
                return
            }
        }
    }

    private fun updateStep(i: Int, f: (FlatStep) -> FlatStep) = _ui.update { s ->
        s.copy(steps = s.steps.mapIndexed { j, st -> if (j == i) f(st) else st })
    }

    private suspend fun runStep(i: Int) {
        val step = _ui.value.steps[i]
        step.memorySlot?.let { runMemoryStep(i, it); return }
        if (step.targets != null) {
            runTargetStep(i, step)
            return
        }
        if (step.keys.isEmpty()) {
            updateStep(i) { it.copy(status = StepStatus.SKIPPED) }
            return
        }
        val car0 = _ui.value.car
        step.keys.forEach { k -> if (k !in startPos) car0.positions[k]?.let { startPos[k] = it } }
        if (!_ui.value.returning) saveSeatMemo()
        updateStep(i) { it.copy(status = StepStatus.RUNNING) }
        hereRequested = false
        step.keys.forEach { gateway.moveSeat(it, step.move) }
        EventLog.add("'${step.title}' 시작")

        var t0 = System.currentTimeMillis()
        var lastSig = signature(step.keys)
        var firstSig = lastSig
        var lastChange = t0
        var progressAtStart = _ui.value.steps[i].progress
        while (true) {
            delay(100)
            if (_ui.value.phase != FlatPhase.RUNNING) return
            val now = System.currentTimeMillis()
            learnDirection(step)
            // 위치값이 한 번에 튀어도(에뮬레이터 등) 막대는 최소 STEP_MIN_MS 동안 차오르게
            val pace = progressAtStart + (1f - progressAtStart) * ((now - t0) / STEP_MIN_MS.toFloat()).coerceIn(0f, 1f)
            // 위치값이 아직 한 번도 안 바뀌었으면(에뮬레이터 등) 시간 기준으로 차오르게
            val moved = signature(step.keys) != firstSig
            val p = if (!moved) pace else minOf(pace, maxOf(progressAtStart, computeProgress(step, now - t0)))
            updateStep(i) { it.copy(progress = p) }

            if (hereRequested) {
                hereRequested = false
                step.keys.forEach { gateway.moveSeat(it, Move.STOP) }
                if (!holdAt(i, p)) return
                // 계속하기: 같은 방향으로 다시 움직임
                step.keys.forEach { gateway.moveSeat(it, step.move) }
                t0 = System.currentTimeMillis(); progressAtStart = p
                firstSig = signature(step.keys); lastSig = firstSig; lastChange = t0
                continue
            }

            val sig = signature(step.keys)
            if (sig != lastSig) {
                lastSig = sig
                lastChange = now
            }
            if (moved) movedSteps += step.title
            val stalled = sig.isNotEmpty() && now - lastChange > 1500 && now - t0 > 1500
            // 움직이던 시트가 끝에 닿기 전에 멈춤 = 무언가 끼었을 수 있음
            val realP = realProgress(step, worst = true)
            if (stalled && realP != null && step.title in movedSteps && realP < JAM_LIMIT) {
                jamStop(step.title, realP)
                return
            }
            val minDone = now - t0 >= STEP_MIN_MS
            if ((p >= 0.99f && minDone) || (stalled && minDone) || now - t0 > 20_000) {
                step.keys.forEach { gateway.moveSeat(it, Move.STOP) }
                val note = if (stalled && p < 0.05f) "움직임 없음" else ""
                updateStep(i) { it.copy(status = StepStatus.DONE, progress = 1f, note = note) }
                return
            }
        }
    }

    private fun signature(keys: List<SeatKey>): String {
        val pos = _ui.value.car.positions
        val values = keys.mapNotNull { pos[it] }
        return if (values.isEmpty()) "" else values.joinToString(",")
    }

    private fun learnDirection(step: FlatStep) {
        val pos = _ui.value.car.positions
        step.keys.forEach { k ->
            val start = startPos[k] ?: return@forEach
            val cur = pos[k] ?: return@forEach
            if (cur != start) forwardUp[k] = ((cur - start) > 0) == (step.move == Move.FORWARD)
        }
    }

    private fun computeProgress(step: FlatStep, elapsed: Long): Float =
        realProgress(step) ?: (elapsed / 6000f).coerceIn(0f, 1f)

    /** 실제 시트 위치로 본 진행률. 위치·범위 정보가 없으면 null */
    private fun realProgress(step: FlatStep, worst: Boolean = false): Float? {
        val car = _ui.value.car
        val values = step.keys.mapNotNull { k ->
            val start = startPos[k] ?: return@mapNotNull null
            val pos = car.positions[k] ?: return@mapNotNull null
            val b = car.bounds[k] ?: return@mapNotNull null
            val up = forwardUp[k] ?: true
            val increasing = if (step.move == Move.FORWARD) up else !up
            val target = if (increasing) b.last else b.first
            val total = (target - start).toFloat()
            if (total == 0f) 1f else ((pos - start) / total).coerceIn(0f, 1f)
        }
        return when {
            values.isEmpty() -> null
            worst -> values.min()   // 끼임 판단은 가장 덜 간 좌석 기준
            else -> values.average().toFloat()
        }
    }

    /** 끼임 감지: 즉시 정지하고 경고 */
    private fun jamStop(title: String, p: Float) {
        EventLog.add("끼임 감지: '$title' ${(p * 100).toInt()}%에서 시트가 더 움직이지 않음")
        emergencyStop("끼임 감지", "시트가 무언가에 막혀 멈췄어요. 좌석 주변을 확인한 뒤 선택하세요.")
    }


    /** '여기까지': 진행 중인 단계를 지금 위치에서 멈춘다 (계속하기 / 다음 단계 중 선택) */
    fun hereOk() {
        if (_ui.value.phase == FlatPhase.RUNNING) hereRequested = true
    }

    private var holdDecision: CompletableDeferred<Boolean>? = null

    /** 시트를 멈춘 채로 사용자의 선택을 기다린다. true = 계속하기, false = 다음 단계 */
    private suspend fun holdAt(i: Int, p: Float): Boolean {
        val pct = (p * 100).toInt()
        val title = _ui.value.steps[i].title
        updateStep(i) { it.copy(status = StepStatus.HELD, note = "${pct}%에서 멈춤") }
        _ui.update { it.copy(phase = FlatPhase.HELD) }
        speaker.say("멈췄어요. 계속하거나 다음 단계로 넘어가세요.")
        EventLog.add("'$title' ${pct}%에서 멈춤")
        val d = CompletableDeferred<Boolean>()
        holdDecision = d
        val cont = try { d.await() } finally { holdDecision = null }
        _ui.update { it.copy(phase = FlatPhase.RUNNING) }
        if (cont) {
            updateStep(i) { it.copy(status = StepStatus.RUNNING, note = "") }
            EventLog.add("'$title' 계속")
        } else {
            updateStep(i) { it.copy(status = StepStatus.CONFIRMED, note = "${pct}%에서 확정") }
            EventLog.add("'$title' ${pct}%에서 확정")
        }
        return cont
    }

    /** 멈춘 단계를 이어서 움직이기 */
    fun continueStep() {
        if (_ui.value.phase != FlatPhase.HELD) return
        if (!_ui.value.canStart()) {
            _ui.update { it.copy(toast = "안전 점검을 통과해야 계속할 수 있어요") }
            return
        }
        holdDecision?.complete(true)
    }

    /** 멈춘 위치를 확정하고 다음 단계로 */
    fun nextStep() {
        if (_ui.value.phase != FlatPhase.HELD) return
        if (!_ui.value.canStart()) {
            _ui.update { it.copy(toast = "안전 점검을 통과해야 계속할 수 있어요") }
            return
        }
        holdDecision?.complete(false)
    }

    /** 화면 터치 · 정지 버튼 · 문 열림 · 착석 감지 시 즉시 정지 */
    fun emergencyStop(reason: String, voice: String = "즉시 정지했어요. 뒷좌석을 확인한 뒤 선택하세요.") {
        val ph = _ui.value.phase
        if (ph != FlatPhase.RUNNING && ph != FlatPhase.COUNTDOWN && ph != FlatPhase.REVERTING) return
        runJob?.cancel()
        gateway.stopAllSeats()
        _ui.update { it.copy(phase = FlatPhase.PAUSED, stopReason = reason, countdown = 0) }
        speaker.say(voice)
        EventLog.add("즉시 정지: $reason")
    }

    fun resume() {
        if (_ui.value.phase != FlatPhase.PAUSED) return
        if (!_ui.value.canStart()) {
            _ui.update { it.copy(toast = "안전 점검을 통과해야 계속할 수 있어요") }
            return
        }
        if (revertPending) {
            // 원위치 중에 멈췄던 경우: 평탄화가 아니라 원위치를 이어서 한다
            revert(revertToHome)
            return
        }
        _ui.update { it.copy(phase = FlatPhase.RUNNING, stopReason = null) }
        runJob = viewModelScope.launch { runSteps() }
    }

    private var revertPending = false
    private var revertToHome = false

    fun revert(toHome: Boolean = false) {
        runJob?.cancel()
        revertPending = true
        revertToHome = toHome
        runJob = viewModelScope.launch {
            _ui.update { it.copy(phase = FlatPhase.REVERTING, stopReason = null) }
            speaker.say("원래 자세로 돌아갑니다.")
            doRevert()
            // 원위치 중 끼임으로 멈췄으면 그대로 멈춘 상태 유지
            if (_ui.value.phase != FlatPhase.REVERTING) return@launch
            revertPending = false
            revertMoved = false
            _ui.update {
                it.copy(
                    phase = FlatPhase.IDLE, steps = emptyList(),
                    screen = if (toHome) Screen.HOME else Screen.CHECK,
                    toast = "원위치 완료", areaClear = false
                )
            }
            relock()
        }
    }

    private suspend fun doRevert() {
        val keys = startPos.keys.toList()
        keys.forEach { k ->
            val cur = _ui.value.car.positions[k] ?: return@forEach
            val start = startPos[k] ?: return@forEach
            if (abs(cur - start) <= 1) return@forEach
            val needUp = cur < start
            val up = forwardUp[k] ?: true
            gateway.moveSeat(k, if (needUp == up) Move.FORWARD else Move.BACKWARD)
        }
        val t0 = System.currentTimeMillis()
        var lastSig = signature(keys)
        var lastChange = t0
        while (true) {
            delay(100)
            if (_ui.value.phase != FlatPhase.REVERTING) return
            val car = _ui.value.car
            keys.forEach { k ->
                val c = car.positions[k]
                val s = startPos[k]
                if (c != null && s != null && abs(c - s) <= 1) gateway.moveSeat(k, Move.STOP)
            }
            val allBack = keys.all { k ->
                val c = car.positions[k]
                val s = startPos[k]
                c == null || s == null || abs(c - s) <= 1
            }
            val now = System.currentTimeMillis()
            val sig = signature(keys)
            if (sig != lastSig) {
                lastSig = sig
                lastChange = now
                revertMoved = true
            }
            val stalled = now - lastChange > 1500 && now - t0 > 1500
            if (stalled && !allBack && revertMoved) {
                EventLog.add("끼임 감지: 원위치 중 시트가 더 움직이지 않음")
                emergencyStop("끼임 감지", "시트가 무언가에 막혀 멈췄어요. 좌석 주변을 확인한 뒤 선택하세요.")
                return
            }
            if (allBack || stalled || now - t0 > 25_000) break
        }
        gateway.stopAllSeats()
        startPos.clear()
        clearSeatMemo()
        EventLog.add("원래 자세 복귀 완료")
    }

    // ---------------------------------------------------------------- 잠자리 · 배터리

    /**
     * 잠자리 설정 변경. 잠자리 모드가 켜져 있으면 바뀐 항목만 즉시 차량에 반영한다.
     */
    fun setPrefs(p: SleepPrefs) {
        val old = _ui.value.prefs
        _ui.update { it.copy(prefs = p) }
        savePrefs(p)
        if (!_ui.value.sleepApplied) return

        val changes = mutableListOf<String>()
        if (p.climate != old.climate || p.tempC != old.tempC) {
            gateway.setClimate(p.climate, p.tempC, 2)
            changes += if (p.climate) "공조 ${p.tempC.toInt()}°C" else "공조 끔"
        }
        if (p.window != old.window) {
            gateway.setRearWindowVent(p.window)
            changes += if (p.window) "창문 환기 켬" else "창문 닫음"
        }
        if (p.seatHeat != old.seatHeat) {
            gateway.setRearSeatHeat(if (p.seatHeat) 1 else 0)
            changes += if (p.seatHeat) "열선 켬" else "열선 끔"
        }
        if (p.doorLock != old.doorLock && p.doorLock) {
            gateway.lockDoors(true)
            changes += "도어 잠금"
        }
        if (p.nightLight != old.nightLight) {
            // 켜기로 바꿔도 화면을 바로 어둡게 하지 않는다 ('취침등 켜기' 버튼으로 켬)
            if (!p.nightLight) _ui.update { it.copy(nightLightOn = false) }
            changes += if (p.nightLight) "화면 취침등 사용" else "화면 취침등 끔"
        }
        if (changes.isNotEmpty()) {
            val msg = changes.joinToString(" · ")
            EventLog.add("잠자리 설정 즉시 반영: $msg")
            _ui.update { it.copy(appliedPrefs = p, toast = msg) }
        }
    }

    fun setAll(on: Boolean) =
        setPrefs(_ui.value.prefs.copy(climate = on, window = on, nightLight = on, seatHeat = on, doorLock = on))

    /** 화면 취침등 지금 켜기 */
    fun showNightLight() {
        if (_ui.value.prefs.nightLight) _ui.update { it.copy(nightLightOn = true) }
    }

    /** 화면 취침등 끄기(화면 터치) */
    fun dismissNightLight() {
        if (_ui.value.sunrise != null) {
            // 일출 중 터치: 일출을 멈추고 잠자리 모드 화면으로
            sunriseJob?.cancel()
            EventLog.add("일출 화면 중지 · 잠자리 모드로 돌아감")
        }
        _ui.update { it.copy(nightLightOn = false, sunrise = null, screen = Screen.SLEEP) }
    }

    private var sunriseJob: Job? = null

    /** 기상 알람: 화면이 서서히 밝아지는 일출 화면 뒤 아침 화면으로 */
    fun startSunrise() {
        if (sunriseJob?.isActive == true) return
        sunriseJob = viewModelScope.launch {
            EventLog.add("기상 알람: 일출 화면 시작")
            val steps = 60
            val stepMs = if (_ui.value.simulation) 150L else 1_000L
            for (i in 0..steps) {
                _ui.update { it.copy(nightLightOn = true, sunrise = i / steps.toFloat()) }
                delay(stepMs)
            }
            _ui.update { it.copy(nightLightOn = false, sunrise = null) }
            wakeUp()
        }
    }

    fun applySleep() {
        val p = _ui.value.prefs
        gateway.setClimate(p.climate, p.tempC, 2)
        gateway.setRearWindowVent(p.window)
        gateway.setRearSeatHeat(if (p.seatHeat) 1 else 0)
        if (p.doorLock) gateway.lockDoors(true)
        EventLog.add(
            if (p.nightLight) "화면 취침등 켜기 (밝기 ${p.nightLevel}%) · 앰비언트 밝기 제어는 SDK 지원 시 연동"
            else "화면 취침등 꺼짐"
        )
        if (!p.window) EventLog.add("창문 환기가 꺼져 있어요 · 결로 주의")
        speaker.say("잠자리 모드를 켰어요. 편안한 밤 되세요.")
        val summary = listOf(
            if (p.climate) "공조 ${p.tempC.toInt()}°C" else "공조 끔",
            if (p.window) "창문 환기" else "창문 닫음",
            if (p.nightLight) "화면 취침등" else "취침등 끔",
            if (p.seatHeat) "열선 1단계" else "열선 끔",
            if (p.doorLock) "도어 잠금" else "도어 그대로"
        ).joinToString(" · ")
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date())
        _ui.update {
            it.copy(
                sleepApplied = true, sleepEnded = false, fanReduced = false, ratePerHour = 0f, rateMeasured = false,
                lastApplied = "$time 적용: $summary",
                appliedPrefs = p,
                nightLightOn = p.nightLight,
                toast = if (p.nightLight) null else "잠자리 모드 켜짐"
            )
        }
        startGuardian()
    }

    /** 잠자리 모드 버튼: 꺼져 있으면 켜고, 켜져 있으면 띈다 */
    fun toggleSleep() {
        if (_ui.value.sleepApplied) stopSleep() else applySleep()
    }

    /** 잠자리 모드 끄기: 공조·열선 끄고 창문 닫기 (도어 잠금은 그대로) */
    fun stopSleep() {
        guardJob?.cancel()
        sunriseJob?.cancel()
        val p = _ui.value.prefs
        if (p.climate) gateway.setClimate(false, p.tempC, 0)
        if (p.window) gateway.setRearWindowVent(false)
        if (p.seatHeat) gateway.setRearSeatHeat(0)
        EventLog.add("잠자리 모드 끄기 (공조·열선 끄기, 창문 닫기, 도어 잠금 유지)")
        _ui.update {
            it.copy(
                sleepApplied = false, sleepEnded = true, nightLightOn = false, sunrise = null,
                toast = "잠자리 모드 꺼짐"
            )
        }
    }

    /** 같은 날 같은 시각에 알람이 두 번 울리지 않도록 */
    private var lastAlarmKey = ""

    private fun startGuardian() {
        guardJob?.cancel()
        batterySamples.clear()
        guardJob = viewModelScope.launch {
            while (isActive) {
                val s = _ui.value
                val b = s.car.batteryPct
                if (b != null) {
                    val now = System.currentTimeMillis()
                    if (batterySamples.isEmpty() || batterySamples.last().second != b) batterySamples.add(now to b)
                    val first = batterySamples.first()
                    val spanH = (now - first.first) / 3_600_000f
                    val drop = first.second - b
                    val measured = spanH >= 1f / 6f && drop > 0
                    _ui.update { it.copy(ratePerHour = if (measured) drop / spanH else 0f, rateMeasured = measured) }
                    if (s.prefs.climate && !s.fanReduced && b <= s.prefs.floorPct + 3) {
                        gateway.setFanLevel(1)
                        _ui.update { it.copy(fanReduced = true, toast = "배터리 절약: 풍량을 낮췄어요") }
                    }
                }
                if (s.prefs.alarm) {
                    val c = Calendar.getInstance()
                    val key = "${c.get(Calendar.DAY_OF_YEAR)}-${s.prefs.alarmHour}:${s.prefs.alarmMinute}"
                    if (c.get(Calendar.HOUR_OF_DAY) == s.prefs.alarmHour &&
                        c.get(Calendar.MINUTE) == s.prefs.alarmMinute &&
                        s.screen == Screen.SLEEP && key != lastAlarmKey
                    ) {
                        lastAlarmKey = key
                        startSunrise()
                    }
                }
                delay(if (s.simulation) 2_000L else 30_000L)
            }
        }
    }

    fun wakeUp() {
        if (_ui.value.campOn) {
            _ui.update { it.copy(screen = Screen.MORNING) }
            speaker.say("좋은 아침이에요. 준비되면 운전 자세로 돌아갈까요?")
        } else {
            // 시트를 접지 않았으면 돌릴 것도 없으니 홈으로
            _ui.update { it.copy(screen = Screen.HOME) }
            speaker.say("좋은 아침이에요.")
        }
    }

    /** @return 보호 잠금 때문에 PIN 이 필요하면 false */
    fun returnToDrive(): Boolean {
        if (_ui.value.touchStopActive) return true
        if (!isUnlocked()) return false
        if (!_ui.value.canStart()) {
            _ui.update { it.copy(toast = "안전 점검을 통과해야 복귀할 수 있어요") }
            return true
        }
        guardJob?.cancel()
        runJob?.cancel()
        originPos.clear()
        movedSteps.clear()
        val steps = buildReturnSteps()
        _ui.update {
            it.copy(
                screen = Screen.FLATTEN, returning = true, steps = steps,
                phase = FlatPhase.COUNTDOWN, stopReason = null, nightLightOn = false
            )
        }
        runJob = viewModelScope.launch {
            countdown("3초 뒤 운전 자세로 돌아갑니다.")
            _ui.update { it.copy(phase = FlatPhase.RUNNING) }
            runSteps()
        }
        return true
    }

    // ---------------------------------------------------------------- 저장

    private fun loadPrefs() = SleepPrefs(
        climate = store.getBoolean("p_climate", true),
        window = store.getBoolean("p_window", true),
        seatHeat = store.getBoolean("p_seatHeat", false),
        doorLock = store.getBoolean("p_doorLock", true),
        tempC = store.getFloat("p_temp", 22f),
        floorPct = store.getInt("p_floor", 30),
        alarm = store.getBoolean("p_alarm", false),
        alarmHour = store.getInt("p_alarmHour", 7),
        alarmMinute = store.getInt("p_alarmMinute", 0),
        nightLight = store.getBoolean("p_night", true),
        nightLevel = store.getInt("p_nightLevel", 30)
    )

    private fun savePrefs(p: SleepPrefs) {
        store.edit()
            .putBoolean("p_climate", p.climate)
            .putBoolean("p_window", p.window)
            .putBoolean("p_seatHeat", p.seatHeat)
            .putBoolean("p_doorLock", p.doorLock)
            .putFloat("p_temp", p.tempC)
            .putInt("p_floor", p.floorPct)
            .putBoolean("p_alarm", p.alarm)
            .putInt("p_alarmHour", p.alarmHour)
            .putInt("p_alarmMinute", p.alarmMinute)
            .putBoolean("p_night", p.nightLight)
            .putInt("p_nightLevel", p.nightLevel)
            .apply()
    }

    override fun onCleared() {
        runJob?.cancel()
        guardJob?.cancel()
        gateway.release()
        voice.release()
        speaker.shutdown()
        super.onCleared()
    }
}
