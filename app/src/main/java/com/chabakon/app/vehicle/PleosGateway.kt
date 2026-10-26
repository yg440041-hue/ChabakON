package com.chabakon.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatHeadrestHeightAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Pleos Connect Vehicle SDK(2.3.2) 를 사용하는 실제 차량 연결.
 * 모든 호출은 비동기(onComplete/onFailed)이며, 실패하면 로그에 남기고 앱은 계속 동작한다.
 */
class PleosGateway(context: Context) : VehicleGateway {

    override val isSimulation = false

    private val _state = MutableStateFlow(CarState())
    override val state: StateFlow<CarState> = _state

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    private val occupancy = ConcurrentHashMap<SeatPos, Boolean>()
    private val belts = ConcurrentHashMap<SeatPos, Boolean>()
    private val doorPos = ConcurrentHashMap<DoorValue, Int>()
    private val positions = ConcurrentHashMap<SeatKey, Int>()
    private val bounds = ConcurrentHashMap<SeatKey, IntRange>()
    private val hvacAreas = mutableListOf<SeatArea>()
    private val occAreas = mutableListOf<SeatArea>()
    private val lockAreas = mutableListOf<DoorArea>()
    private val fanAreas = ConcurrentHashMap<SeatArea, AreaBounds<Int>>()
    private val tempAreas = ConcurrentHashMap<SeatArea, AreaBounds<Float>>()
    private val windowBounds = ConcurrentHashMap<WindowValue, AreaBounds<Int>>()
    private val unregisterActions = mutableListOf<() -> Unit>()
    private var started = false

    override fun start() {
        if (started) return
        started = true
        try {
            vehicle.initialize()
            EventLog.add("Pleos 차량 서비스 연결 요청")
        } catch (e: Exception) {
            EventLog.add("차량 서비스 연결 실패: ${e.message}")
            return
        }
        // 서비스 바인딩이 끝날 시간을 잠깐 준 뒤 상태 구독을 시작한다.
        main.postDelayed({ setup() }, 800)
    }

    private fun fail(what: String): (Exception) -> Unit = { e ->
        EventLog.add("$what 실패: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun markConnected() {
        if (!_state.value.connected) {
            _state.update { it.copy(connected = true) }
            EventLog.add("차량 서비스 응답 확인")
        }
    }

    private fun setup() {
        setupGear()
        setupBrake()
        setupOccupancy()
        setupBelts()
        setupDoors()
        setupBattery()
        setupSeatAxis(SeatAxis.FORE_AFT)
        setupSeatAxis(SeatAxis.BACKREST)
        setupSeatAxis(SeatAxis.HEADREST)
        setupHvac()
        setupFanAndTemp()
        setupDoorLock()
        setupWindows()
    }

    // ---------------- 상태 구독 ----------------

    private fun setupGear() = safe("기어 상태 구독") {
        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState(
            onComplete = { g -> markConnected(); onGear(g) },
            onFailed = fail("기어 조회")
        )
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = onGear(gear)
            override fun onFailed(e: Exception) = fail("기어 구독")(e)
        }
        gear.registerGearSelection(listener)
        unregisterActions += { gear.unregisterGearSelection(listener) }
    }

    private fun setupBrake() = safe("브레이크 구독") {
        val brake = vehicle.getBrake()
        brake.getCurrentBrakePedalStatus(
            onComplete = { v -> _state.update { it.copy(brakePressed = v) } },
            onFailed = fail("브레이크 조회")
        )
        val l = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                _state.update { it.copy(brakePressed = status) }
            }
            override fun onFailed(e: Exception) = fail("브레이크 구독")(e)
        }
        brake.registerBrakePedalStatus(l)
        unregisterActions += { brake.unregisterBrakePedalStatus(l) }
    }

    private fun onGear(g: VehicleGear?) {
        _state.update { it.copy(gearPark = g?.let { v -> v == VehicleGear.GEAR_PARK }) }
    }

    private fun setupOccupancy() = safe("착석 감지 구독") {
        val seat = vehicle.getSeat()
        seat.checkSeatOccupancyCapabilities(
            onComplete = { areas: List<SeatArea> ->
                markConnected()
                val rear = areas.filter { it.toSeatPos()?.name?.startsWith("ROW2") == true }
                EventLog.add("착석 감지 지원 좌석: ${rear.mapNotNull { it.toSeatPos()?.label }}")
                synchronized(occAreas) { occAreas.clear(); occAreas.addAll(rear) }
                rear.forEach { area ->
                    val pos = area.toSeatPos() ?: return@forEach
                    seat.getCurrentSeatOccupancy(
                        area = area,
                        onComplete = { v -> setOcc(pos, v) },
                        onFailed = fail("${pos.label} 착석 조회")
                    )
                    val l = object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupancy: Boolean?) = setOcc(pos, occupancy)
                        override fun onFailed(e: Exception) = fail("착석 구독")(e)
                    }
                    seat.registerSeatOccupancy(area, l)
                    unregisterActions += { seat.unregisterSeatOccupancy(area, l) }
                }
            },
            onFailed = fail("착석 감지 지원 확인")
        )
    }

    /**
     * SDK 2.3.2 는 원시 착석값이 1 일 때 true 를 돌려준다.
     * 그런데 Android 차량 표준(VehicleSeatOccupancyState)은 0=알 수 없음, 1=비어 있음, 2=사람 있음 이고,
     * Pleos Connect 에뮬레이터(v2.1.0)도 이 표준대로 값을 보낸다(실측 확인).
     * 따라서 SDK 의 true 는 '비어 있음' 으로, false 는 '사람 있음 또는 알 수 없음' 으로 해석한다.
     * 확실히 비어 있을 때만 통과시키는 안전 쪽 해석이다.
     */
    private val occupancyFollowsVhalStandard = true

    private fun setOcc(pos: SeatPos, sdkValue: Boolean?) {
        val v: Boolean? = when {
            sdkValue == null -> null
            occupancyFollowsVhalStandard -> !sdkValue
            else -> sdkValue
        }
        if (v == null) occupancy.remove(pos) else occupancy[pos] = v
        val label = when (v) { true -> "사람 있음"; false -> "비어 있음"; null -> "알 수 없음" }
        EventLog.add("${pos.label} 착석: $label")
        _state.update {
            it.copy(
                row2Occupied = if (occupancy.isEmpty()) null else occupancy.values.any { b -> b },
                row2Seats = HashMap(occupancy)
            )
        }
    }

    override fun refresh() = safe("다시 확인") {
        val seat = vehicle.getSeat()
        val areas = synchronized(occAreas) { occAreas.toList() }
        EventLog.add("착석 상태 다시 읽기 (${areas.size}개 좌석)")
        areas.forEach { area ->
            val pos = area.toSeatPos() ?: return@forEach
            seat.getCurrentSeatOccupancy(area = area, onComplete = { v -> setOcc(pos, v) }, onFailed = fail("${pos.label} 착석 조회"))
        }
        vehicle.getGear().getCurrentGearSelectionState(onComplete = { g -> onGear(g) }, onFailed = fail("기어 조회"))
    }

    private fun setupBelts() = safe("안전벨트 구독") {
        val seat = vehicle.getSeat()
        seat.checkSeatBeltBuckledCapabilities(
            onComplete = { areas: List<SeatArea> ->
                areas.forEach { area ->
                    val pos = area.toSeatPos() ?: return@forEach
                    seat.getCurrentSeatBeltBuckledState(
                        area = area,
                        onComplete = { v -> setBelt(pos, v) },
                        onFailed = fail("${pos.label} 벨트 조회")
                    )
                    val l = object : SeatBeltBuckledStateListener {
                        override fun onSeatBeltBuckledStateUpdated(state: Boolean?) = setBelt(pos, state)
                        override fun onFailed(e: Exception) = fail("벨트 구독")(e)
                    }
                    seat.registerSeatBeltBuckledState(area, l)
                    unregisterActions += { seat.unregisterSeatBeltBuckledState(area, l) }
                }
            },
            onFailed = fail("안전벨트 지원 확인")
        )
    }

    private fun setBelt(pos: SeatPos, v: Boolean?) {
        if (v == null) belts.remove(pos) else belts[pos] = v
        _state.update { it.copy(beltBuckled = if (belts.isEmpty()) null else belts.values.any { b -> b }) }
    }

    private fun setupDoors() = safe("도어 구독") {
        val door = vehicle.getDoor()
        door.checkDoorPositionCapabilities(
            onComplete = { map: Map<DoorArea, AreaBounds<Int>> ->
                map.keys.forEach { area ->
                    val dv = area.values.firstOrNull() ?: return@forEach
                    door.getCurrentDoorPosition(
                        area = area,
                        onComplete = { p -> setDoor(dv, p) },
                        onFailed = fail("도어 조회")
                    )
                    val l = object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) = setDoor(dv, pos)
                        override fun onFailed(e: Exception) = fail("도어 구독")(e)
                    }
                    door.registerDoorPosition(area, l)
                    unregisterActions += { door.unregisterDoorPosition(area, l) }
                }
            },
            onFailed = fail("도어 지원 확인")
        )
    }

    private fun setDoor(dv: DoorValue, p: Int?) {
        if (p == null) doorPos.remove(dv) else doorPos[dv] = p
        val side = doorPos.filterKeys { it != DoorValue.REAR && it != DoorValue.HOOD }
        _state.update {
            it.copy(
                doorOpen = if (side.isEmpty()) null else side.values.any { v -> v > 0 },
                tailgateOpen = doorPos[DoorValue.REAR]?.let { v -> v > 0 }
            )
        }
    }

    private fun setupBattery() = safe("배터리 구독") {
        val ev = vehicle.getEvBattery()
        ev.getEvBatteryStateOfCharge(
            onComplete = { v -> markConnected(); _state.update { it.copy(batteryPct = v) } },
            onFailed = fail("배터리 조회")
        )
        val l = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                _state.update { it.copy(batteryPct = value) }
            }
            override fun onFailed(e: Exception) = fail("배터리 구독")(e)
        }
        ev.registerEvBatteryStateOfCharge(l)
        unregisterActions += { ev.unregisterEvBatteryStateOfCharge(l) }
    }

    private fun setupSeatAxis(axis: SeatAxis) = safe("${axis.label} 지원 확인") {
        val seat = vehicle.getSeat()
        val onBounds: (Map<SeatArea, AreaBounds<Int>>) -> Unit = { map ->
            markConnected()
            EventLog.add("${axis.label} 지원 좌석: ${map.keys.mapNotNull { it.toSeatPos()?.label }}")
            map.forEach { (area, b) ->
                val pos = area.toSeatPos() ?: return@forEach
                val key = SeatKey(pos, axis)
                bounds[key] = b.minValue..b.maxValue
                subscribeSeatPosition(area, key)
            }
            _state.update { it.copy(bounds = HashMap(bounds)) }
        }
        when (axis) {
            SeatAxis.FORE_AFT -> seat.checkSeatForeAftCapabilities(onComplete = onBounds, onFailed = fail("앞뒤 위치 지원 확인"))
            SeatAxis.BACKREST -> seat.checkSeatBackrestAngleCapabilities(onComplete = onBounds, onFailed = fail("등받이 지원 확인"))
            SeatAxis.HEADREST -> seat.checkSeatHeadrestHeightCapabilities(onComplete = onBounds, onFailed = fail("헤드레스트 지원 확인"))
        }
    }

    private fun setPos(key: SeatKey, p: Int?) {
        if (p == null) positions.remove(key) else positions[key] = p
        _state.update { it.copy(positions = HashMap(positions)) }
    }

    private fun subscribeSeatPosition(area: SeatArea, key: SeatKey) {
        val seat = vehicle.getSeat()
        val onValue: (Int?) -> Unit = { v -> setPos(key, v) }
        when (key.axis) {
            SeatAxis.FORE_AFT -> {
                seat.getCurrentSeatForeAftPosition(area = area, onComplete = onValue, onFailed = fail("앞뒤 위치 조회"))
                val l = object : SeatForeAftPositionListener {
                    override fun onSeatForeAftPositionUpdated(position: Int?) = onValue(position)
                    override fun onFailed(e: Exception) = fail("앞뒤 위치 구독")(e)
                }
                seat.registerSeatForeAftPosition(area, l)
                unregisterActions += { seat.unregisterSeatForeAftPosition(area, l) }
            }
            SeatAxis.BACKREST -> {
                seat.getCurrentSeatBackrestAnglePosition(area = area, onComplete = onValue, onFailed = fail("등받이 조회"))
                val l = object : SeatBackrestAnglePositionListener {
                    override fun onSeatBackrestAnglePositionUpdated(position: Int?) = onValue(position)
                    override fun onFailed(e: Exception) = fail("등받이 구독")(e)
                }
                seat.registerSeatBackrestAnglePosition(area, l)
                unregisterActions += { seat.unregisterSeatBackrestAnglePosition(area, l) }
            }
            SeatAxis.HEADREST -> {
                seat.getCurrentSeatHeadrestHeightAftPosition(area = area, onComplete = onValue, onFailed = fail("헤드레스트 조회"))
                val l = object : SeatHeadrestHeightAftPositionListener {
                    override fun onSeatHeadrestHeightAftPositionUpdated(position: Int?) = onValue(position)
                    override fun onFailed(e: Exception) = fail("헤드레스트 구독")(e)
                }
                seat.registerSeatHeadrestHeightAftPosition(area, l)
                unregisterActions += { seat.unregisterSeatHeadrestHeightAftPosition(area, l) }
            }
        }
    }

    private fun setupHvac() = safe("공조 지원 확인") {
        vehicle.getHVAC().checkHvacPowerCapabilities(
            onComplete = { areas: List<SeatArea> ->
                synchronized(hvacAreas) { hvacAreas.clear(); hvacAreas.addAll(areas) }
                EventLog.add("공조 제어 영역 ${areas.size}개")
            },
            onFailed = fail("공조 지원 확인")
        )
    }

    private fun setupDoorLock() = safe("도어 잠금 지원 확인") {
        vehicle.getDoor().checkDoorLockCapabilities(
            onComplete = { areas: List<DoorArea> ->
                synchronized(lockAreas) { lockAreas.clear(); lockAreas.addAll(areas) }
                EventLog.add("도어 잠금 지원 영역 ${areas.size}개")
            },
            onFailed = fail("도어 잠금 지원 확인")
        )
    }

    private fun setupFanAndTemp() = safe("풍량·온도 지원 확인") {
        val hvac = vehicle.getHVAC()
        hvac.checkHvacFanSpeedCapabilities(
            onComplete = { map: Map<SeatArea, AreaBounds<Int>> ->
                fanAreas.clear()
                fanAreas.putAll(map)
                EventLog.add("풍량 제어 영역 ${map.size}개")
            },
            onFailed = fail("풍량 지원 확인")
        )
        hvac.checkHvacTemperatureCapabilities(
            onComplete = { map: Map<SeatArea, AreaBounds<Float>> ->
                tempAreas.clear()
                tempAreas.putAll(map)
                EventLog.add("온도 제어 영역 ${map.size}개")
            },
            onFailed = fail("온도 지원 확인")
        )
    }

    private fun setupWindows() = safe("창문 지원 확인") {
        vehicle.getWindow().checkWindowPositionCapabilities(
            onComplete = { map: Map<WindowArea, AreaBounds<Int>> ->
                map.forEach { (area, b) -> area.values.firstOrNull()?.let { windowBounds[it] = b } }
                EventLog.add("창문 제어 지원: ${windowBounds.keys}")
            },
            onFailed = fail("창문 지원 확인")
        )
    }

    // ---------------- 제어 ----------------

    override fun moveSeat(key: SeatKey, move: Move) = safe("${key.seat.label} ${key.axis.label} 이동") {
        val area = SeatArea(key.seat.toSeatValue())
        val mc = MoveControl.valueOf(move.name)
        val seat = vehicle.getSeat()
        when (key.axis) {
            SeatAxis.FORE_AFT -> seat.moveSeatForeAft(area = area, value = mc, onFailed = fail("앞뒤 이동"))
            SeatAxis.BACKREST -> seat.moveSeatBackrestAngle(area = area, value = mc, onFailed = fail("등받이 이동"))
            SeatAxis.HEADREST -> seat.moveSeatHeadrestHeight(area = area, value = mc, onFailed = fail("헤드레스트 이동"))
        }
    }

    override fun stopAllSeats() {
        val keys = HashSet(bounds.keys)
        listOf(SeatPos.ROW1_LEFT, SeatPos.ROW1_RIGHT).forEach { p ->
            SeatAxis.values().forEach { a -> keys.add(SeatKey(p, a)) }
        }
        keys.forEach { moveSeat(it, Move.STOP) }
        EventLog.add("모든 시트 정지 명령")
    }

    private fun climateAreas(): List<SeatArea> = synchronized(hvacAreas) {
        if (hvacAreas.isEmpty()) listOf(SeatArea(SeatValue.ROW_1_LEFT)) else hvacAreas.toList()
    }

    override fun setClimate(on: Boolean, tempC: Float, fanLevel: Int) = safe("공조 설정") {
        val hvac = vehicle.getHVAC()
        climateAreas().forEach { area ->
            hvac.setHvacPower(area = area, value = on, onFailed = fail("공조 전원"))
        }
        if (on) {
            tempAreas.forEach { (area, b) ->
                val t = tempC.coerceIn(b.minValue, b.maxValue)
                hvac.setHvacTemperature(area = area, value = t, unit = TemperatureUnit.CELSIUS, onFailed = fail("온도 설정"))
            }
            setFanOnly(fanLevel)
        }
        EventLog.add(if (on) "공조 켜기: ${tempC}°C, 풍량 $fanLevel" else "공조 끄기")
    }

    override fun setFanLevel(level: Int) = safe("풍량 조절") {
        setFanOnly(level)
        EventLog.add("풍량 $level 단계로 조절")
    }

    private fun setFanOnly(level: Int) {
        val hvac = vehicle.getHVAC()
        if (fanAreas.isEmpty()) {
            EventLog.add("이 차량은 풍량 제어를 지원하지 않아요")
            return
        }
        fanAreas.forEach { (area, b) ->
            val v = level.coerceIn(b.minValue, b.maxValue)
            hvac.setHvacFanSpeed(area = area, value = v, onFailed = fail("풍량 설정"))
        }
    }

    override fun setRearWindowVent(open: Boolean) = safe("창문 환기") {
        val window = vehicle.getWindow()
        val targets = listOf(WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
        var done = 0
        targets.forEach { wv ->
            val b = windowBounds[wv] ?: return@forEach
            val span = b.maxValue - b.minValue
            val value = if (open) b.minValue + maxOf(1, span * 8 / 100) else b.minValue
            window.setWindowPosition(area = WindowArea(wv), value = value, onFailed = fail("창문 위치"))
            done++
        }
        EventLog.add(if (done == 0) "이 차량은 2열 창문 제어를 지원하지 않아요" else if (open) "2열 창문 살짝 열기" else "2열 창문 닫기")
    }

    override fun setRearSeatHeat(level: Int) = safe("시트 열선") {
        val seat = vehicle.getSeat()
        listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).forEach { v ->
            seat.setHvacSeatTemperature(area = SeatArea(v), value = level, onFailed = fail("시트 열선"))
        }
        EventLog.add("2열 시트 열선 $level 단계")
    }

    override fun lockDoors(lock: Boolean) = safe("도어 잠금") {
        val door = vehicle.getDoor()
        val areas = synchronized(lockAreas) { lockAreas.toList() }
        if (areas.isEmpty()) {
            EventLog.add("이 차량은 도어 잠금 제어를 지원하지 않아요")
        } else {
            areas.forEach { area -> door.setDoorLock(area = area, value = lock, onFailed = fail("도어 잠금")) }
            EventLog.add(if (lock) "지원되는 도어 ${areas.size}곳 잠금" else "도어 잠금 해제")
        }
    }

    override fun recallDriverMemory(slot: Int) = safe("시트 메모리") {
        vehicle.getSeat().selectSeatMemory(area = SeatArea(SeatValue.ROW_1_LEFT), value = slot, onFailed = fail("시트 메모리"))
        EventLog.add("운전석 메모리 $slot 번 호출")
    }

    override fun release() {
        unregisterActions.forEach { runCatching { it() } }
        unregisterActions.clear()
        runCatching { vehicle.release() }
        started = false
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            EventLog.add("$what 오류: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------------- 좌석 변환 ----------------

    private fun SeatArea.toSeatPos(): SeatPos? = when (values.firstOrNull()) {
        SeatValue.ROW_1_LEFT -> SeatPos.ROW1_LEFT
        SeatValue.ROW_1_RIGHT -> SeatPos.ROW1_RIGHT
        SeatValue.ROW_2_LEFT -> SeatPos.ROW2_LEFT
        SeatValue.ROW_2_CENTER -> SeatPos.ROW2_CENTER
        SeatValue.ROW_2_RIGHT -> SeatPos.ROW2_RIGHT
        else -> null
    }

    private fun SeatPos.toSeatValue(): SeatValue = when (this) {
        SeatPos.ROW1_LEFT -> SeatValue.ROW_1_LEFT
        SeatPos.ROW1_RIGHT -> SeatValue.ROW_1_RIGHT
        SeatPos.ROW2_LEFT -> SeatValue.ROW_2_LEFT
        SeatPos.ROW2_CENTER -> SeatValue.ROW_2_CENTER
        SeatPos.ROW2_RIGHT -> SeatValue.ROW_2_RIGHT
    }
}
