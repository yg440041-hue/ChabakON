package com.chabakon.app.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 차박ON에서 다루는 좌석 위치 */
enum class SeatPos(val label: String) {
    ROW1_LEFT("운전석"),
    ROW1_RIGHT("동승석"),
    ROW2_LEFT("2열 왼쪽"),
    ROW2_CENTER("2열 가운데"),
    ROW2_RIGHT("2열 오른쪽")
}

/** 좌석에서 움직이는 축 */
enum class SeatAxis(val label: String) {
    FORE_AFT("앞뒤 위치"),
    BACKREST("등받이 각도"),
    HEADREST("헤드레스트 높이")
}

/** Pleos MoveControl 과 같은 이름으로 맞춘 이동 명령 */
enum class Move { FORWARD, BACKWARD, STOP }

data class SeatKey(val seat: SeatPos, val axis: SeatAxis)

/** 앱이 판단에 쓰는 차량 상태. null 은 '이 차량에서 확인 불가' 를 뜻한다. */
data class CarState(
    val connected: Boolean = false,
    val gearPark: Boolean? = null,
    /** 브레이크 페달을 밟고 있는지 (PIN 초기화 본인 확인용) */
    val brakePressed: Boolean? = null,
    val row2Occupied: Boolean? = null,
    /** 2열 좌석별 착석 상태 (좌석별 원인 확인용) */
    val row2Seats: Map<SeatPos, Boolean> = emptyMap(),
    val beltBuckled: Boolean? = null,
    val doorOpen: Boolean? = null,
    val tailgateOpen: Boolean? = null,
    val batteryPct: Int? = null,
    val positions: Map<SeatKey, Int> = emptyMap(),
    val bounds: Map<SeatKey, IntRange> = emptyMap()
)

/** 실제 차량(Pleos Vehicle SDK)과 시뮬레이션이 같은 모양으로 동작하도록 묶은 인터페이스 */
interface VehicleGateway {
    val isSimulation: Boolean
    val state: StateFlow<CarState>
    fun start()
    fun release()
    /** 착석·벨트 등 현재 값을 다시 읽어 온다 */
    fun refresh()
    fun moveSeat(key: SeatKey, move: Move)
    fun stopAllSeats()
    fun setClimate(on: Boolean, tempC: Float, fanLevel: Int)
    fun setFanLevel(level: Int)
    fun setRearWindowVent(open: Boolean)
    fun setRearSeatHeat(level: Int)
    fun lockDoors(lock: Boolean)
    fun recallDriverMemory(slot: Int)
}

/** 화면 아래 '로그' 패널에 보여 줄 기록 */
object EventLog {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    val lines = MutableStateFlow<List<String>>(emptyList())

    fun add(message: String) {
        val line = "${fmt.format(Date())}  $message"
        synchronized(this) {
            lines.value = (lines.value + line).takeLast(80)
        }
        android.util.Log.d("ChabakON", message)
    }
}
