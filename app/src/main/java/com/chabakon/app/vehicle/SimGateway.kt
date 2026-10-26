package com.chabakon.app.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 에뮬레이터에 차량 데이터가 없을 때도 흐름을 시연할 수 있는 가상 차량.
 * 모든 좌석 축은 0~100 범위를 가지며, FORWARD 는 값이 커지는 방향이다.
 */
class SimGateway : VehicleGateway {

    override val isSimulation = true

    private val initialPositions: Map<SeatKey, Int> = mapOf(
        SeatKey(SeatPos.ROW1_LEFT, SeatAxis.FORE_AFT) to 40,
        SeatKey(SeatPos.ROW1_RIGHT, SeatAxis.FORE_AFT) to 45,
        SeatKey(SeatPos.ROW1_LEFT, SeatAxis.BACKREST) to 35,
        SeatKey(SeatPos.ROW1_RIGHT, SeatAxis.BACKREST) to 30,
        SeatKey(SeatPos.ROW1_LEFT, SeatAxis.HEADREST) to 70,
        SeatKey(SeatPos.ROW1_RIGHT, SeatAxis.HEADREST) to 65,
        SeatKey(SeatPos.ROW2_LEFT, SeatAxis.BACKREST) to 20,
        SeatKey(SeatPos.ROW2_RIGHT, SeatAxis.BACKREST) to 20
    )

    private val _state = MutableStateFlow(
        CarState(
            connected = true,
            gearPark = true,
            brakePressed = false,
            row2Occupied = false,
            row2Seats = mapOf(SeatPos.ROW2_LEFT to false, SeatPos.ROW2_RIGHT to false),
            beltBuckled = false,
            doorOpen = false,
            tailgateOpen = true,
            batteryPct = 78,
            positions = initialPositions,
            bounds = initialPositions.keys.associateWith { 0..100 }
        )
    )
    override val state: StateFlow<CarState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val moving = HashMap<SeatKey, Move>()
    private var ticker: Job? = null

    override fun start() {
        EventLog.add("시뮬레이션 차량으로 실행 중")
        ticker = scope.launch {
            while (isActive) {
                delay(80)
                tick()
            }
        }
    }

    private fun tick() {
        if (moving.isEmpty()) return
        val pos = HashMap(_state.value.positions)
        val finished = mutableListOf<SeatKey>()
        moving.forEach { (key, move) ->
            val cur = pos[key] ?: return@forEach
            val next = when (move) {
                Move.FORWARD -> cur + 2
                Move.BACKWARD -> cur - 2
                Move.STOP -> cur
            }.coerceIn(0, 100)
            pos[key] = next
            if (next == 0 || next == 100 || move == Move.STOP) finished += key
        }
        finished.forEach { moving.remove(it) }
        _state.update { it.copy(positions = pos) }
    }

    override fun moveSeat(key: SeatKey, move: Move) {
        if (key !in _state.value.positions) return
        if (move == Move.STOP) moving.remove(key) else moving[key] = move
    }

    override fun stopAllSeats() {
        moving.clear()
        EventLog.add("[시뮬] 모든 시트 정지")
    }

    override fun setClimate(on: Boolean, tempC: Float, fanLevel: Int) =
        EventLog.add(if (on) "[시뮬] 공조 켜기: ${tempC}°C, 풍량 $fanLevel" else "[시뮬] 공조 끄기")

    override fun setFanLevel(level: Int) = EventLog.add("[시뮬] 풍량 $level 단계")
    override fun setRearWindowVent(open: Boolean) = EventLog.add(if (open) "[시뮬] 2열 창문 살짝 열기" else "[시뮬] 2열 창문 닫기")
    override fun setRearSeatHeat(level: Int) = EventLog.add("[시뮬] 2열 시트 열선 $level 단계")
    override fun lockDoors(lock: Boolean) = EventLog.add(if (lock) "[시뮬] 모든 도어 잠금" else "[시뮬] 도어 잠금 해제")
    override fun recallDriverMemory(slot: Int) = EventLog.add("[시뮬] 운전석 메모리 $slot 번 호출")

    // ---- 시뮬레이션 패널에서 바꾸는 값 ----
    fun setRow2Occupied(v: Boolean) = _state.update {
        it.copy(row2Occupied = v, row2Seats = mapOf(SeatPos.ROW2_LEFT to v, SeatPos.ROW2_RIGHT to false))
    }

    override fun refresh() = EventLog.add("[시뮬] 상태 다시 읽기")
    fun setBelt(v: Boolean) = _state.update { it.copy(beltBuckled = v) }
    fun setGearPark(v: Boolean) = _state.update { it.copy(gearPark = v) }
    fun setBrake(v: Boolean) = _state.update { it.copy(brakePressed = v) }
    fun setDoorOpen(v: Boolean) = _state.update { it.copy(doorOpen = v) }
    fun setTailgate(v: Boolean) = _state.update { it.copy(tailgateOpen = v) }
    fun setBattery(v: Int) = _state.update { it.copy(batteryPct = v) }

    override fun release() {
        ticker?.cancel()
        scope.cancel()
    }
}
