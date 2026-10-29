package com.chabakon.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.chabakon.app.ChabakViewModel
import com.chabakon.app.FlatPhase
import com.chabakon.app.FlatStep
import com.chabakon.app.R
import com.chabakon.app.Screen
import com.chabakon.app.SENSOR_ITEMS
import com.chabakon.app.StepStatus
import com.chabakon.app.UiState
import com.chabakon.app.vehicle.CarState
import com.chabakon.app.vehicle.SeatPos
import com.chabakon.app.vehicle.SimGateway
import kotlinx.coroutines.delay

object C {
    val bg = Color(0xFF0D1118)
    val bar = Color(0xFF0A0E14)
    val card = Color(0xFF151C25)
    val line = Color(0xFF243040)
    val text = Color(0xFFEEF1F5)
    val sub = Color(0xFF8F9BAB)
    val amber = Color(0xFFFFB547)
    val onAmber = Color(0xFF1B1205)
    val green = Color(0xFF4FD69C)
    val greenBg = Color(0xFF16352A)
    val red = Color(0xFFFF5D5D)
    val redBg = Color(0xFF2A1114)
    val gray = Color(0xFF2A3442)
}

// ============================================================ 루트

@Composable
fun ChabakApp(vm: ChabakViewModel) {
    val s by vm.ui.collectAsState()
    val logs by vm.log.collectAsState()
    var showLog by remember { mutableStateOf(false) }
    var showSim by remember { mutableStateOf(false) }
    var hereRect by remember { mutableStateOf(Rect.Zero) }
    val currentHere by rememberUpdatedState(hereRect)
    // '멈춰 (테스트)' 버튼 자리: 화면 터치 정지에서 빼서 음성 정지로 멈추게
    var voiceTestRect by remember { mutableStateOf(Rect.Zero) }
    val currentVoiceTest by rememberUpdatedState(voiceTestRect)
    val active = s.touchStopActive
    // 차량 화면의 뒤로가기도 홈으로
    BackHandler(enabled = s.canGoHome()) { vm.go(Screen.HOME) }

    Box(
        Modifier
            .fillMaxSize()
            // 위쪽 가운데에서 퍼지는 은은한 빛
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFF1F2B3C),
                        0.55f to Color(0xFF0A0E15),
                        1f to Color(0xFF05070B),
                        center = Offset(size.width / 2f, -size.height * 0.1f),
                        radius = size.width * 0.75f
                    )
                )
            }
            // 작동 중에는 '여기까지' 버튼을 뺀 화면 어디를 눌러도 즉시 정지
            .pointerInput(active) {
                if (!active) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val down = ev.changes.firstOrNull { it.pressed && !it.previousPressed }
                        if (down != null && !currentHere.contains(down.position) && !currentVoiceTest.contains(down.position)) {
                            vm.emergencyStop("화면 터치")
                        }
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                s = s,
                onHome = { vm.go(Screen.HOME) },
                onToggleSim = { vm.setSimulation(!s.simulation) },
                onSimPanel = { showSim = !showSim },
                onLog = { showLog = !showLog },
                onSettings = { vm.openSettings(true) }
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(28.dp)
            ) {
                when (s.screen) {
                    Screen.HOME -> HomeScreen(s, vm)
                    Screen.CHECK -> CheckScreen(s, vm)
                    Screen.FLATTEN -> FlattenScreen(s, vm, onVoiceTestBounds = { voiceTestRect = it }) { hereRect = it }
                    Screen.SLEEP -> SleepScreen(s, vm)
                    Screen.MORNING -> MorningScreen(s, vm)
                }
            }
        }
        if (active) {
            // 작동 중: 선 대신 화면 가장자리에 붉은 빛이 은은하게 스며듦
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val e = 48.dp.toPx()
                        val r = C.red.copy(alpha = 0.45f)
                        drawRect(Brush.verticalGradient(listOf(r, Color.Transparent), endY = e), size = Size(size.width, e))
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, r), startY = size.height - e, endY = size.height),
                            topLeft = Offset(0f, size.height - e), size = Size(size.width, e)
                        )
                        drawRect(Brush.horizontalGradient(listOf(r, Color.Transparent), endX = e), size = Size(e, size.height))
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, r), startX = size.width - e, endX = size.width),
                            topLeft = Offset(size.width - e, 0f), size = Size(e, size.height)
                        )
                    }
            )
        }
        if (showSim) {
            vm.simGateway?.let { sim ->
                SimPanel(
                    sim, s.car, Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    onSunrise = { showSim = false; vm.go(Screen.SLEEP); vm.startSunrise() }
                ) { showSim = false }
            }
        }
        if (showLog) {
            LogPanel(logs, Modifier.align(Alignment.BottomStart).padding(24.dp)) { showLog = false }
        }
        if (s.settingsOpen) {
            SettingsDialog(
                s,
                onDismiss = { vm.openSettings(false) },
                onForgot = { vm.openReset(true) }
            ) { cur, enabled, newPin -> vm.updateSecurity(cur, enabled, newPin) }
        }
        if (s.resetOpen) {
            ResetPinDialog(s, onDismiss = { vm.openReset(false) }) { vm.resetPin() }
        }
        if (s.nightLightOn) {
            NightLightOverlay(s) { vm.dismissNightLight() }
        }
        s.toast?.let { msg ->
            ToastBar(msg, Modifier.align(Alignment.TopCenter).padding(top = 96.dp)) { vm.dismissToast() }
        }
    }
}

// ============================================================ 공통 부품

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = C.line,
    background: Color = C.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    // 테두리 선은 그리지 않는다. 색(초록/빨강/주황)은 판 안쪽에 은은하게 묻어나게만 한다.
    val tinted = border != C.line
    val top = if (tinted) lerp(background, border, 0.16f).lighten(0.04f) else background.lighten(0.08f)
    Column(
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = if (tinted) border.copy(alpha = 0.5f) else Color.Black)
            .clip(shape)
            // 어두운 유리판: 왼쪽 위가 살짝 밝고 아래로 갈수록 어두워짐
            .background(
                Brush.linearGradient(
                    listOf(top, background, background.darken(0.3f)),
                    start = Offset.Zero, end = Offset.Infinite
                )
            )
            // 위쪽 빛 반사
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                        endY = size.height * 0.46f
                    ),
                    size = Size(size.width, size.height * 0.46f)
                )
            }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        content = content
    )
}

fun Color.lighten(f: Float) = lerp(this, Color.White, f)
fun Color.darken(f: Float) = lerp(this, Color.Black, f)

@Composable
fun Title(text: String, size: TextUnit = 36.sp) =
    Text(text, color = C.text, fontSize = size, fontWeight = FontWeight.ExtraBold, letterSpacing = TitleTracking)

@Composable
fun Sub(text: String, size: TextUnit = 19.sp, color: Color = C.sub) =
    Text(text, color = color, fontSize = size)

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = C.amber,
    content: Color = C.onAmber,
    height: Dp = 96.dp,
    fontSize: TextUnit = 30.sp,
    onClick: () -> Unit
) {
    // 볼록한 젤리 버튼: 위쪽 광택 + 아래쪽 두께(립). 누르면 아래로 눌려 들어간다.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (enabled) container else C.gray
    val fg = if (enabled) content else C.sub
    val dark = base.luminance() < 0.2f
    val lipMax = 7.dp
    val lip by animateDpAsState(if (pressed) 2.dp else lipMax, label = "lip")
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.fillMaxWidth().height(height + lipMax)) {
        // 버튼 두께(아래쪽 벽)
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .align(Alignment.BottomCenter)
                .clip(shape)
                .background(base.darken(if (dark) 0.45f else 0.5f))
        )
        // 버튼 윗면
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = lipMax - lip)
                .shadow(if (enabled && !pressed) 12.dp else 3.dp, shape, ambientColor = base, spotColor = base)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to base.lighten(if (dark) 0.16f else 0.55f),
                        0.38f to base.lighten(if (dark) 0.06f else 0.12f),
                        0.62f to base,
                        1f to base.darken(0.18f)
                    )
                )
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.18f else 0.55f), Color.Transparent)), shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // 광택
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.36f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.45f), Color.Transparent)))
            )
            Text(text, color = fg, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, height: Dp = 78.dp, onClick: () -> Unit) =
    BigButton(text, modifier, container = Color(0xFF1B2430), content = C.text, height = height, fontSize = 24.sp, onClick = onClick)

@Composable
fun Chip(text: String, color: Color = C.sub, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .shadow(4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            // 테두리 선 없이, 상태 색이 살짝 밴 볼록한 알약 모양
            .background(
                Brush.verticalGradient(
                    listOf(lerp(Color(0xFF2A3546), color, 0.18f), lerp(Color(0xFF151C27), color, 0.06f))
                )
            )
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = C.text) {
    Column {
        Sub(label, 16.sp)
        Text(value, color = valueColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================ 상단 바

@Composable
fun TopBar(
    s: UiState,
    onHome: () -> Unit,
    onToggleSim: () -> Unit,
    onSimPanel: () -> Unit,
    onLog: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .shadow(10.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF121A25), Color(0xFF0A0F16))))
            .drawBehind {
                drawLine(Color(0xFF1E2A3A), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
            }
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (s.canGoHome()) HomeBackButton(onHome)
        Row(
            Modifier.clickable { onHome() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(painterResource(R.mipmap.ic_launcher), contentDescription = "차박ON", modifier = Modifier.size(46.dp))
            Text("차박", color = C.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("ON", color = C.amber, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Sub(if (s.screen == Screen.FLATTEN && s.returning) "운전 자세 복귀" else s.screen.title, 20.sp)
        Spacer(Modifier.weight(1f))
        val sdkText = when {
            s.simulation -> "시뮬레이션"
            s.car.connected -> "차량 연결됨"
            else -> "연결 중"
        }
        Chip(sdkText, if (s.simulation) C.amber else if (s.car.connected) C.green else C.sub, onToggleSim)
        if (s.simulation) Chip("가상 차량", C.amber, onSimPanel)
        Chip(
            when (s.car.gearPark) { true -> "기어 P"; false -> "주행 중"; null -> "기어 ?" },
            if (s.car.gearPark == true) C.green else C.sub
        )
        Chip("배터리 ${s.car.batteryPct?.let { "$it%" } ?: "?"}")
        Chip("로그", C.sub, onLog)
        Chip("PIN 설정", if (s.defaultPin && s.childLockEnabled) C.amber else C.sub, onSettings)
    }
}

/** 홈이 아닌 화면에서 홈으로 돌아갈 수 있는지 (시트가 움직이는 중에는 숨김) */
fun UiState.canGoHome(): Boolean =
    screen != Screen.HOME && !touchStopActive &&
        !(screen == Screen.FLATTEN && phase in setOf(FlatPhase.COUNTDOWN, FlatPhase.RUNNING, FlatPhase.HELD, FlatPhase.REVERTING))

/** 상단 왼쪽 뒤로가기(홈) 버튼 */
@Composable
fun HomeBackButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .height(54.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2C3846), Color(0xFF1A222D))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)), shape)
            .clickable { onClick() }
            .padding(start = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "홈으로", tint = C.amber, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text("홈", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** 받침에 따라 '로'/'으로'를 붙인다 (예: 화면 터치로, 정지 버튼으로) */
fun withRo(word: String): String {
    val ch = word.lastOrNull() ?: return word
    if (ch !in '가'..'힣') return word + "로"
    val jong = (ch - '가') % 28
    return word + if (jong == 0 || jong == 8) "로" else "으로"
}

// ============================================================ 홈

@Composable
fun HomeScreen(s: UiState, vm: ChabakViewModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        HomeCard(
            R.drawable.home_flatten,
            "차박 모드 시작",
            if (s.flatDone) "이미 평탄화되어 있어요" else "버튼 한 번으로 시트를 평평하게",
            if (s.flatDone) "평탄화 완료" else "시작하기",
            Modifier.weight(1f),
            enabled = !s.flatDone
        ) { vm.go(Screen.CHECK) }
        HomeCard(
            R.drawable.home_sleep,
            "잠자리 모드",
            "공조, 환기, 잠금을 한 번에",
            if (s.sleepApplied) "잠자리 모드 보기" else "설정하기",
            Modifier.weight(1f)
        ) { vm.go(Screen.SLEEP) }
        HomeCard(
            R.drawable.home_morning,
            "굿모닝 복귀",
            if (s.campOn) "시트를 운전 자세로 되돌리기" else "차박 모드 후에 쓸 수 있어요",
            if (s.campOn) "열기" else "차박 모드 먼저",
            Modifier.weight(1f),
            enabled = s.campOn
        ) { vm.go(Screen.MORNING) }
    }
}

@Composable
fun HomeCard(
    image: Int, title: String, desc: String, button: String, modifier: Modifier,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Panel(modifier.fillMaxHeight()) {
        // 그림(960x630 비율) → 바로 아래 제목·설명 → 남는 공간 → 버튼
        Image(
            painterResource(image),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(960f / 630f),
            contentScale = ContentScale.Fit,
            alpha = if (enabled) 1f else 0.35f
        )
        Spacer(Modifier.height(10.dp))
        // 그림과 같은 축(가운데)으로, 더 크고 또렷하게
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = Color(0xFFF7F9FC),
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = TitleTracking,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), 8f)
                )
            )
            Spacer(Modifier.height(10.dp))
            Text(
                desc,
                color = Color(0xFFB4C1D2),
                fontSize = 23.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        BigButton(button, enabled = enabled, onClick = onClick)
    }
}

// ============================================================ 안전 점검

@Composable
fun CheckScreen(s: UiState, vm: ChabakViewModel) {
    var showPin by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        SeatMap(s.car, Modifier.width(380.dp).fillMaxHeight())
        SafetyChecklist(
            s, vm,
            title = "안전 점검",
            subtitle = "모두 통과하면 시작할 수 있어요",
            modifier = Modifier.weight(1f),
            onPin = { showPin = true }
        )
        Column(
            Modifier.width(400.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StopMethods(Modifier.weight(1f), voice = s.voiceReady)

            BigButton(if (s.flatDone) "이미 평탄화됨" else "평탄화 시작", enabled = s.canStart() && !s.flatDone) { vm.startFlatten() }
            GhostButton("취소") { vm.go(Screen.HOME) }
        }
    }
    if (showPin) {
        PinDialog(
            onDismiss = { showPin = false },
            defaultPin = s.defaultPin,
            onChangePin = { showPin = false; vm.openSettings(true) },
            onForgot = { showPin = false; vm.openReset(true) }
        ) { pin -> vm.tryPin(pin).also { if (it) showPin = false } }
    }
}

/** 차박 모드 시작 · 운전 자세 복귀에서 같이 쓰는 안전 점검 목록 */
@Composable
fun SafetyChecklist(
    s: UiState,
    vm: ChabakViewModel,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onPin: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Title(title)
                Sub(subtitle)
            }
            TextButton(onClick = { vm.refreshCar() }) {
                Text("↻ 다시 확인", color = C.amber, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            val items = s.checkItems()
            items.forEachIndexed { i, item ->
                val isArea = i == items.lastIndex
                CheckRow(
                    title = item.title,
                    sub = item.sub,
                    ok = item.ok,
                    action = when {
                        i == SENSOR_ITEMS && s.childLockEnabled && !s.childUnlocked -> "PIN 입력"
                        isArea && !s.areaClear -> "확인했어요"
                        else -> null
                    },
                    onAction = if (isArea) ({ vm.setAreaClear(true) }) else onPin
                )
                if (i < items.lastIndex) HorizontalDivider(color = C.line)
            }
        }
        if (s.hasUnknown()) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2A2416))
                    .clickable { vm.setManualConfirm(!s.manualConfirm) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = s.manualConfirm,
                    onCheckedChange = { vm.setManualConfirm(it) },
                    colors = CheckboxDefaults.colors(checkedColor = C.amber, checkmarkColor = C.onAmber)
                )
                Sub(
                    "센서가 없는 항목은 눈으로 확인했어요",
                    18.sp, Color(0xFFFFD59A)
                )
            }
        }
    }
}

@Composable
fun CheckRow(title: String, sub: String, ok: Boolean?, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val (mark, color, bg) = when (ok) {
            true -> Triple("✓", C.green, C.greenBg)
            false -> Triple("!", C.red, C.redBg)
            null -> Triple("?", C.amber, Color(0xFF2A2416))
        }
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(bg),
            contentAlignment = Alignment.Center
        ) { Text(mark, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 23.sp)
            Sub(sub, 16.sp)
        }
        if (action != null) {
            // 눌러야 하는 항목은 글자가 아니라 볼록한 버튼으로
            BigButton(action, Modifier.width(150.dp), height = 48.dp, fontSize = 19.sp, onClick = onAction)
        } else {
            Text(
                when (ok) { true -> "통과"; false -> "확인 필요"; null -> "센서 없음" },
                color = color, fontSize = 17.sp
            )
        }
    }
}

@Composable
fun SeatMap(car: CarState, modifier: Modifier) {
    CarTopView(car, modifier)
}

@Composable
fun SeatBox(text: String, color: Color, modifier: Modifier) {
    Box(
        modifier
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .border(3.dp, color, RoundedCornerShape(16.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

@Composable
fun PinDialog(
    onDismiss: () -> Unit,
    defaultPin: Boolean = false,
    onChangePin: (() -> Unit)? = null,
    onForgot: (() -> Unit)? = null,
    onSubmit: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    ChabakDialog("어린이 보호 잠금 해제", onDismiss, confirm = "확인", onConfirm = { wrong = !onSubmit(pin) }) {
        DialogText(if (defaultPin) "PIN 4자리를 입력하세요 (기본 0000)" else "PIN 4자리를 입력하세요")
        PinField(pin, "PIN") { pin = it; wrong = false }
        if (wrong) DialogText("PIN이 틀렸어요", C.red, bold = true)
        Row {
            onChangePin?.let { LinkButton("PIN 바꾸기", onClick = it) }
            onForgot?.let { LinkButton("PIN을 잊었어요", Color(0xFFB4C1D2), onClick = it) }
        }
    }
}

// ============================================================ 공통 대화상자 (유리판 + 젤리 버튼, 큰 글씨)

@Composable
fun ChabakDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: String? = null,
    onConfirm: () -> Unit = {},
    dismiss: String = "취소",
    width: Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(Modifier.width(width), border = C.amber.copy(alpha = 0.6f), background = Color(0xFF18212C)) {
            Text(title, color = C.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = TitleTracking)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GhostButton(dismiss, Modifier.weight(1f), height = 72.dp, onClick = onDismiss)
                if (confirm != null) BigButton(confirm, Modifier.weight(1f), height = 72.dp, fontSize = 26.sp, onClick = onConfirm)
            }
        }
    }
}

/** 큰 숫자 4자리 PIN 입력칸 */
@Composable
fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
        label = { Text(label, fontSize = 20.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 32.sp, letterSpacing = 12.sp, color = C.text),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.amber,
            unfocusedBorderColor = Color(0xFF4A5868),
            focusedLabelColor = C.amber,
            unfocusedLabelColor = Color(0xFFB4C1D2),
            cursorColor = C.amber,
            focusedContainerColor = Color(0xFF0F151D),
            unfocusedContainerColor = Color(0xFF0F151D)
        )
    )
}

@Composable
fun DialogText(text: String, color: Color = Color(0xFFD5DDE7), size: TextUnit = 22.sp, bold: Boolean = false) =
    Text(text, color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@Composable
fun LinkButton(text: String, color: Color = C.amber, onClick: () -> Unit) =
    TextButton(onClick = onClick) { Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold) }

// ============================================================ 평탄화

@Composable
fun FlattenScreen(
    s: UiState,
    vm: ChabakViewModel,
    onVoiceTestBounds: (Rect) -> Unit = {},
    onHereBounds: (Rect) -> Unit
) {
    val anyHere = s.phase == FlatPhase.RUNNING && s.steps.any { it.status == StepStatus.RUNNING }
    if (!anyHere) SideEffect { onHereBounds(Rect.Zero) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Title(
                when (s.phase) {
                    FlatPhase.COUNTDOWN -> if (s.returning) "곧 운전 자세로 돌아가요" else "곧 움직여요"
                    FlatPhase.RUNNING -> if (s.returning) "운전 자세로 복귀 중" else "평탄화 중"
                    FlatPhase.HELD -> "멈춤"
                    FlatPhase.PAUSED -> "정지됨"
                    FlatPhase.REVERTING -> "원위치 중"
                    FlatPhase.DONE -> if (s.returning) "운전 자세 복귀 완료" else "평탄화 완료"
                    FlatPhase.IDLE -> "평탄화"
                }
            )
            Sub(if (s.phase == FlatPhase.HELD) "'계속하기'를 누르면 이어서 움직여요" else "'여기까지'를 누르면 그 자리에서 멈춰요")
            Panel(Modifier.fillMaxWidth()) {
                s.steps.forEachIndexed { i, st ->
                    StepRow(
                        index = i,
                        step = st,
                        showHere = st.status == StepStatus.RUNNING && s.phase == FlatPhase.RUNNING,
                        held = st.status == StepStatus.HELD && s.phase == FlatPhase.HELD,
                        onHere = { vm.hereOk() },
                        onContinue = { vm.continueStep() },
                        onBounds = onHereBounds
                    )
                }
            }
        }
        // 지금 움직이는(카운트다운 중에는 곧 움직일) 시트
        val activeStep = when (s.phase) {
            FlatPhase.RUNNING -> s.steps.firstOrNull { it.status == StepStatus.RUNNING }
            FlatPhase.COUNTDOWN -> s.steps.firstOrNull { it.status == StepStatus.WAIT && (it.keys.isNotEmpty() || it.memorySlot != null) }
            else -> null
        }
        val movingSeats = activeStep?.let { st ->
            if (st.memorySlot != null) setOf(SeatPos.ROW1_LEFT) else st.keys.map { it.seat }.toSet()
        } ?: emptySet()
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CarTopView(s.car, Modifier.fillMaxWidth().weight(1f), moving = movingSeats, showLegend = false)
            Spacer(Modifier.height(12.dp))
            when (s.phase) {
                FlatPhase.COUNTDOWN -> {
                    Text("${s.countdown}", color = C.amber, fontSize = 110.sp, fontWeight = FontWeight.Black)
                    Sub("뒷좌석을 확인하세요", 24.sp, Color(0xFFFFD59A))
                }
                FlatPhase.RUNNING -> {
                    Sub("화면을 누르면 멈춰요", 24.sp, Color(0xFFFFD59A))
                    Sub("문이 열려도 멈춰요", 20.sp)
                }
                FlatPhase.HELD -> {
                    Sub("시트를 멈췄어요", 24.sp, Color(0xFFFFD59A))
                    Spacer(Modifier.height(14.dp))
                    GhostButton("이 위치로 다음 단계", Modifier.width(360.dp), 72.dp) { vm.nextStep() }
                }
                FlatPhase.PAUSED -> Panel(border = C.red, background = C.redBg) {
                    Text("정지됨", color = Color(0xFFFF8A8A), fontSize = 40.sp, fontWeight = FontWeight.Black)
                    // '화면 터치'만 쓰면 '눌러서 계속'으로 오해할 수 있어 '…로 멈췄어요'로 쓴다
                    s.stopReason?.let { Sub("${withRo(it)} 멈췄어요", 20.sp, C.text) }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GhostButton("계속", Modifier.width(220.dp), 70.dp) { vm.resume() }
                        if (s.returning) {
                            GhostButton("아침 화면으로", Modifier.width(220.dp), 70.dp) { vm.go(Screen.MORNING) }
                        } else {
                            GhostButton("원위치", Modifier.width(220.dp), 70.dp) { vm.revert() }
                        }
                    }
                }
                FlatPhase.REVERTING -> Sub("원위치 중", 24.sp)
                FlatPhase.DONE -> Panel(border = C.green, background = Color(0xFF0F1A15)) {
                    if (s.returning) {
                        Text("운전 자세 복귀 완료", color = Color(0xFF7FE0B4), fontSize = 40.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(18.dp))
                        BigButton("홈으로", Modifier.width(460.dp), height = 80.dp) { vm.go(Screen.HOME) }
                    } else {
                        Text("평탄화 완료", color = Color(0xFF7FE0B4), fontSize = 40.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(18.dp))
                        BigButton("잠자리 모드로", Modifier.width(460.dp), height = 80.dp) { vm.go(Screen.SLEEP) }
                    }
                }
                FlatPhase.IDLE -> GhostButton("안전 점검으로", Modifier.width(360.dp)) { vm.go(Screen.CHECK) }
            }
        }
        Column(
            Modifier.width(380.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigButton(
                "즉시 정지",
                enabled = s.touchStopActive,
                container = C.red,
                content = Color.White,
                height = 130.dp,
                fontSize = 36.sp
            ) { vm.emergencyStop("정지 버튼") }
            val debuggable = (LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (s.voiceReady) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (s.touchStopActive) C.red else C.sub,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("\"멈춰\"", color = if (s.touchStopActive) C.text else C.sub, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                // 진단용: 앱이 들은 말 (디버그 빌드에서만)
                if (debuggable && s.voiceHeard.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "들은 말: ${s.voiceHeard}",
                        color = C.sub, fontSize = 17.sp,
                        maxLines = 2, textAlign = TextAlign.Center,
                        modifier = Modifier.width(340.dp)
                    )
                }
            }
            // 마이크 없이 시험하는 버튼: Android Studio에서 실행한(디버그) 빌드에서만 보임
            if (debuggable) {
                Spacer(Modifier.height(22.dp))
                GhostButton(
                    "멈춰 (테스트)",
                    Modifier.width(260.dp).onGloballyPositioned { onVoiceTestBounds(it.boundsInRoot()) },
                    height = 64.dp
                ) { vm.testVoiceStop() }
            } else {
                LaunchedEffect(Unit) { onVoiceTestBounds(Rect.Zero) }
            }
        }
    }
}

@Composable
fun StepRow(
    index: Int, step: FlatStep, showHere: Boolean, onHere: () -> Unit, onBounds: (Rect) -> Unit,
    held: Boolean = false, onContinue: () -> Unit = {}
) {
    val done = step.status == StepStatus.DONE || step.status == StepStatus.CONFIRMED
    val running = step.status == StepStatus.RUNNING || step.status == StepStatus.HELD
    val dim = step.status == StepStatus.WAIT || step.status == StepStatus.SKIPPED
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(50))
                .background(if (done) C.greenBg else if (running) C.amber else Color(0xFF1F2833)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else "${index + 1}",
                color = if (done) C.green else if (running) C.onAmber else C.sub,
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(step.title, color = if (dim) C.sub else C.text, fontSize = 23.sp)
            Spacer(Modifier.height(6.dp))
            // 중간에 확정한 단계는 초록(완료) 대신 주황으로 남겨 '끝까지 간 것'과 구분
            StepBar(step.progress, step.status == StepStatus.DONE, step.status == StepStatus.RUNNING)
            if (step.note.isNotEmpty()) Sub(step.note, 15.sp, if (step.status == StepStatus.CONFIRMED) C.green else C.sub)
        }
        // 버튼 자리는 모든 줄에 항상 비워 둔다 (단계가 넘어갈 때 막대 길이가 튀지 않게)
        Box(Modifier.width(130.dp), contentAlignment = Alignment.CenterEnd) {
            if (held) {
                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = C.amber, contentColor = C.onAmber)
                ) { Text("계속하기", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold) }
            }
            if (showHere) {
                Button(
                    onClick = onHere,
                    modifier = Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2416), contentColor = Color(0xFFFFD59A))
                ) { Text("여기까지", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** 진행 막대: 부드럽게 차오르고, 작동 중에는 빛이 왼쪽에서 오른쪽으로 흐른다 */
@Composable
fun StepBar(progress: Float, done: Boolean, running: Boolean) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "bar")
    val flow by rememberInfiniteTransition(label = "flow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "flowA"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulseA"
    )
    val base = if (done) C.green else C.amber
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(Color(0xFF1F2833), cornerRadius = r)
        // 둥근 끝보다 짧으면 찌그러져 보이므로, 막대 높이만큼은 차야 그린다
        val w = size.width * p
        if (w >= size.height) {
            drawRoundRect(
                Brush.verticalGradient(listOf(base.lighten(0.35f), base, base.darken(0.2f))),
                size = Size(w, size.height), cornerRadius = r
            )
            if (running && w >= size.height) {
                // 흐르는 빛
                val band = size.width * 0.22f
                val x = -band + (w + band) * flow
                clipRect(right = w) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            startX = x, endX = x + band
                        ),
                        topLeft = Offset(x, 0f), size = Size(band, size.height)
                    )
                }
                // 끝에서 빛나는 점
                drawCircle(Color.White.copy(alpha = 0.9f * pulse), radius = size.height * 0.42f, center = Offset(w - size.height / 2f, size.height / 2f))
                drawCircle(base.copy(alpha = 0.35f * pulse), radius = size.height * 1.1f, center = Offset(w - size.height / 2f, size.height / 2f))
            }
        }
        if (running && w < size.height) {
            // 막 시작했을 때: 왼쪽 끝에서 점만 깜박임
            drawCircle(base.copy(alpha = 0.9f * pulse), radius = size.height * 0.42f, center = Offset(size.height / 2f, size.height / 2f))
        }
    }
}

// ============================================================ 잠자리 · 배터리

@Composable
fun SleepScreen(s: UiState, vm: ChabakViewModel) {
    val p = s.prefs
    var showAlarmPicker by remember { mutableStateOf(false) }
    if (showAlarmPicker) {
        AlarmPickerDialog(p.alarmHour, p.alarmMinute, onDismiss = { showAlarmPicker = false }) { h, m ->
            vm.setPrefs(p.copy(alarmHour = h, alarmMinute = m, alarm = true))
            showAlarmPicker = false
        }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(
            Modifier.width(560.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Title("잠자리 모드")
            // 잠자리 모드를 꺼 둔 상태에서는 모든 항목을 꺼진 것으로 보여 주고 잠근다.
            // 저장된 '내 잠자리 설정'은 그대로 두었다가 다시 켜면 돌아온다.
            val off = s.sleepEnded && !s.sleepApplied
            if (off) Sub("다시 켜면 이전 설정으로 돌아와요", 16.sp, C.amber)
            Panel(Modifier.fillMaxWidth()) {
                val count = if (off) 0 else p.onCount
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !off) { vm.setAll(count < 5) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("전체 선택", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Sub("$count/5 켜짐", 15.sp)
                    }
                    TriStateCheckbox(
                        state = when (count) { 5 -> ToggleableState.On; 0 -> ToggleableState.Off; else -> ToggleableState.Indeterminate },
                        onClick = { vm.setAll(count < 5) },
                        enabled = !off,
                        colors = CheckboxDefaults.colors(checkedColor = C.amber, checkmarkColor = C.onAmber)
                    )
                }
                HorizontalDivider(color = Color(0xFF3A4758))
                ToggleRow("공조", "${p.tempC.toInt()}°C", p.climate && !off, enabled = !off) { vm.setPrefs(p.copy(climate = it)) }
                ToggleRow("창문 환기", "결로 방지", p.window && !off, enabled = !off) { vm.setPrefs(p.copy(window = it)) }
                ToggleRow(
                    "화면 취침등",
                    if (p.nightLight) "밝기 ${p.nightLevel}%" else "꺼짐",
                    p.nightLight && !off,
                    enabled = !off
                ) { vm.setPrefs(p.copy(nightLight = it)) }
                if (p.nightLight && !off) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = p.nightLevel.toFloat(),
                            onValueChange = { vm.setPrefs(p.copy(nightLevel = (Math.round(it / 10f) * 10).coerceIn(10, 60))) },
                            valueRange = 10f..60f,
                            steps = 4,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = C.amber, activeTrackColor = C.amber)
                        )
                        if (s.sleepApplied) {
                            Spacer(Modifier.width(12.dp))
                            Chip("취침등 켜기", C.amber) { vm.showNightLight() }
                        }
                    }
                }
                ToggleRow("2열 시트 열선", if (p.seatHeat) "1단계" else "꺼짐", p.seatHeat && !off, enabled = !off) { vm.setPrefs(p.copy(seatHeat = it)) }
                ToggleRow("도어 잠금", "모든 문", p.doorLock && !off, enabled = !off) { vm.setPrefs(p.copy(doorLock = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sub("온도 ${p.tempC.toInt()}°C", 18.sp)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = p.tempC,
                    onValueChange = { vm.setPrefs(p.copy(tempC = Math.round(it).toFloat())) },
                    valueRange = 18f..26f,
                    steps = 7,
                    enabled = !off,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = C.amber, activeTrackColor = C.amber)
                )
            }
            BigButton(
                when {
                    s.sleepApplied -> "잠자리 모드 중"
                    s.sleepEnded -> "잠자리 모드 꺼짐"
                    else -> "잠자리 모드 켜기"
                },
                height = 84.dp,
                container = when {
                    s.sleepApplied -> C.greenBg
                    s.sleepEnded -> C.gray
                    else -> C.amber
                },
                content = when {
                    s.sleepApplied -> C.green
                    s.sleepEnded -> C.text
                    else -> C.onAmber
                }
            ) { vm.toggleSleep() }
            if (!p.window) Sub("환기를 끄면 결로가 생길 수 있어요", 16.sp, C.amber)
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Sub("배터리 ${p.floorPct}%까지 남은 시간", 18.sp)
            val h = s.hoursLeft()
            Text(
                if (h == null) "배터리 정보 없음" else "약 ${h.toInt()}시간",
                color = Color(0xFF7FE0B4), fontSize = 80.sp, fontWeight = FontWeight.Black
            )
            Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                KeyValue("현재", s.car.batteryPct?.let { "$it%" } ?: "?")
                KeyValue("시간당 소모", "약 ${"%.1f".format(s.effectiveRate())}%" + if (s.rateMeasured) " (실측)" else " (추정)")
                KeyValue("자동 절전", if (s.fanReduced) "풍량 낮춤" else "없음")
            }
            Panel(Modifier.fillMaxWidth()) {
                Sub("배터리", 16.sp)
                Spacer(Modifier.height(8.dp))
                BatteryBar(s.car.batteryPct ?: 0, p.floorPct)
                Spacer(Modifier.height(8.dp))
                Sub("빨간 선에 가까워지면 풍량을 자동으로 낮춰요", 15.sp)
            }
        }

        Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Text("배터리 하한선 ${p.floorPct}%", color = C.amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = p.floorPct.toFloat(),
                    onValueChange = { vm.setPrefs(p.copy(floorPct = (Math.round(it / 5f) * 5).coerceIn(20, 50))) },
                    valueRange = 20f..50f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = C.amber, activeTrackColor = C.amber)
                )
                HorizontalDivider(color = C.line)
                ToggleRow(
                    "기상 알람",
                    if (p.alarm) "${formatAlarm(p.alarmHour, p.alarmMinute)}" else "꺼짐",
                    p.alarm
                ) { vm.setPrefs(p.copy(alarm = it)) }
                if (p.alarm) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1B2430))
                            .clickable { showAlarmPicker = true }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatAlarm(p.alarmHour, p.alarmMinute), color = C.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("변경", color = C.amber, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            BigButton("일어났어요", height = 88.dp) { vm.wakeUp() }
        }
    }
}

@Composable
fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) C.text else C.sub, fontSize = 21.sp)
            Sub(sub, 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.onAmber,
                checkedTrackColor = C.amber,
                uncheckedThumbColor = C.sub,
                uncheckedTrackColor = C.gray
            )
        )
    }
}

@Composable
fun BatteryBar(pct: Int, floor: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1F2833))
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(if (pct <= floor + 3) C.amber else C.green)
        )
        Row(Modifier.fillMaxSize()) {
            if (floor > 0) Spacer(Modifier.weight(floor.toFloat()))
            Box(Modifier.width(4.dp).fillMaxHeight().background(C.red))
            if (floor < 100) Spacer(Modifier.weight((100 - floor).toFloat()))
        }
    }
}

// ============================================================ 아침

@Composable
fun MorningScreen(s: UiState, vm: ChabakViewModel) {
    var showPin by remember { mutableStateOf(false) }
    if (showPin) {
        PinDialog(
            onDismiss = { showPin = false },
            defaultPin = s.defaultPin,
            onChangePin = { showPin = false; vm.openSettings(true) },
            onForgot = { showPin = false; vm.openReset(true) }
        ) { pin -> vm.tryPin(pin).also { if (it) showPin = false } }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        SeatMap(s.car, Modifier.width(380.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Text("좋은 아침이에요", color = C.text, fontSize = 44.sp, fontWeight = FontWeight.Black)
                KeyValue("배터리", s.car.batteryPct?.let { "$it%" } ?: "?")
                KeyValue("자동 절전", if (s.fanReduced) "풍량 낮춤" else "없음")
            }
            SafetyChecklist(
                s, vm,
                title = "출발 전 안전 점검",
                subtitle = "뒷자리에 누운 사람이 없는지 확인하세요",
                modifier = Modifier.fillMaxWidth(),
                onPin = { showPin = true }
            )
            when (s.phase) {
                FlatPhase.COUNTDOWN -> Text("${s.countdown}초 뒤 움직여요", color = C.amber, fontSize = 26.sp)
                FlatPhase.REVERTING -> Text("운전 자세로 돌아가는 중…", color = C.amber, fontSize = 26.sp)
                FlatPhase.PAUSED -> Text(s.stopReason?.let { "${withRo(it)} 멈췄어요" } ?: "멈췄어요", color = C.red, fontSize = 24.sp)
                else -> {}
            }
        }
        Column(
            Modifier.width(400.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 평탄화 전 안전 점검 화면과 같은 '멈추는 방법' (음성 포함)
            StopMethods(Modifier.weight(1f), voice = s.voiceReady)
            BigButton("운전 자세로 복귀", enabled = !s.touchStopActive && s.canStart()) {
                if (!vm.returnToDrive()) showPin = true
            }
            GhostButton("조금 더 쉬기") { vm.go(Screen.SLEEP) }
        }
    }
}

// ============================================================ PIN 초기화 (브레이크 페달 + 5초 길게 누르기)

@Composable
fun ResetPinDialog(s: UiState, onDismiss: () -> Unit, onReset: () -> Boolean) {
    val brake by rememberUpdatedState(s.car.brakePressed == true)
    var progress by remember { mutableStateOf(0f) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ChabakDialog("PIN 초기화", onDismiss) {
                DialogText("브레이크를 밟은 채로 아래 버튼을 5초간 누르세요")
                DialogText("PIN이 0000으로 돌아가요", Color(0xFFB4C1D2), 19.sp)
                DialogText(
                    if (brake) "브레이크 확인됨" else "브레이크를 밟으세요",
                    if (brake) C.green else C.amber, 24.sp, bold = true
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (brake) Color(0xFF2A2416) else C.gray)
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                msg = null
                                val job = scope.launch {
                                    for (i in 1..50) {
                                        if (!brake) {
                                            msg = "브레이크를 밟은 채로 누르세요"
                                            progress = 0f
                                            return@launch
                                        }
                                        delay(100)
                                        progress = i / 50f
                                    }
                                    if (!onReset()) msg = "브레이크가 확인되지 않았어요"
                                }
                                tryAwaitRelease()
                                if (progress < 1f) {
                                    job.cancel()
                                    progress = 0f
                                }
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(C.amber.copy(alpha = 0.5f))
                    )
                    Text("5초간 누르기", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                msg?.let { DialogText(it, C.red, bold = true) }
    }
}

// ============================================================ 기상 알람 시각 선택 (휴대폰 알람처럼)

fun formatAlarm(hour: Int, minute: Int): String {
    val ampm = if (hour < 12) "오전" else "오후"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$ampm $h12:${minute.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmPickerDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    ChabakDialog("기상 알람 시각", onDismiss, confirm = "저장", onConfirm = { onConfirm(state.hour, state.minute) }, width = 600.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Color(0xFF0F151D),
                    selectorColor = C.amber,
                    clockDialSelectedContentColor = C.onAmber,
                    clockDialUnselectedContentColor = C.text,
                    periodSelectorSelectedContainerColor = C.amber,
                    periodSelectorSelectedContentColor = C.onAmber,
                    periodSelectorUnselectedContentColor = C.text,
                    periodSelectorBorderColor = Color(0xFF4A5868),
                    timeSelectorSelectedContainerColor = C.amber,
                    timeSelectorSelectedContentColor = C.onAmber,
                    timeSelectorUnselectedContainerColor = Color(0xFF0F151D),
                    timeSelectorUnselectedContentColor = C.text
                )
            )
        }
    }
}

// ============================================================ 화면 취침등 · 일출 화면

/**
 * Pleos SDK 에 앰비언트 밝기 설정 API 가 없어, 차량 화면 자체를 무드등으로 쓴다.
 * 앱 창의 밝기를 낮추고 따뜻한 색을 깔며, 일출 때는 서서히 밝게 한다.
 */
@Composable
fun NightLightOverlay(s: UiState, onTouch: () -> Unit) {
    val sun = s.sunrise
    val level = s.prefs.nightLevel / 100f
    val context = LocalContext.current
    val brightness = if (sun != null) 0.05f + 0.95f * sun else 0.02f + level * 0.3f

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    SideEffect {
        (context as? Activity)?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = brightness
            w.attributes = lp
        }
    }

    val warm = Color(0xFFFF9A3C)
    val dawn = Color(0xFFFFE2B0)
    val glow = if (sun != null) lerp(warm, dawn, sun) else warm
    val alpha = (if (sun != null) level + (1f - level) * sun else level).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(Brush.verticalGradient(listOf(Color.Black, glow.copy(alpha = alpha))))
            .clickable { onTouch() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (sun != null) {
                Text(
                    "좋은 아침이에요",
                    color = Color.White.copy(alpha = 0.3f + 0.7f * sun),
                    fontSize = 64.sp, fontWeight = FontWeight.Black
                )
                Sub("기상 알람", 22.sp, Color.White.copy(alpha = 0.6f))
            } else {
                val h = s.hoursLeft()
                Text("화면 취침등", color = warm.copy(alpha = 0.5f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Sub(
                    if (h == null) "화면을 누르면 돌아가요"
                    else "남은 시간 약 ${h.toInt()}시간",
                    18.sp, Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}

// ============================================================ 설정 (어린이 보호 잠금)

@Composable
fun SettingsDialog(
    s: UiState,
    onDismiss: () -> Unit,
    onForgot: () -> Unit,
    onSave: (currentPin: String, enabled: Boolean, newPin: String) -> String?
) {
    var enabled by remember { mutableStateOf(s.childLockEnabled) }
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ChabakDialog("어린이 보호 잠금", onDismiss, confirm = "저장", onConfirm = { error = onSave(current, enabled, newPin) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DialogText("어린이 보호 잠금 사용", C.text, 24.sp, bold = true)
                DialogText("시트를 움직일 때 PIN 확인", Color(0xFFB4C1D2), 19.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it },
                modifier = Modifier.scale(1.3f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = C.onAmber,
                    checkedTrackColor = C.amber,
                    uncheckedThumbColor = Color(0xFFB4C1D2),
                    uncheckedTrackColor = C.gray,
                    uncheckedBorderColor = Color(0xFF6A7888)
                )
            )
        }
        HorizontalDivider(color = Color(0xFF2E3A4A))
        DialogText("현재 PIN" + if (s.defaultPin) " (기본 0000)" else "", C.text, 21.sp, bold = true)
        PinField(current, "현재 PIN") { current = it; error = null }
        PinField(newPin, "새 PIN (바꿀 때만)") { newPin = it; error = null }
        error?.let { DialogText(it, C.red, bold = true) }
        LinkButton("PIN을 잊었어요", onClick = onForgot)
    }
}

// ============================================================ 시뮬레이션 · 로그 · 토스트

@Composable
fun SimPanel(sim: SimGateway, car: CarState, modifier: Modifier, onSunrise: () -> Unit, onClose: () -> Unit) {
    Panel(modifier.width(440.dp), border = C.amber, background = Color(0xF0101620)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가상 차량", color = C.amber, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("닫기", color = C.sub) }
        }
        ToggleRow("2열에 사람 있음", "작동 중이면 즉시 정지", car.row2Occupied == true) { sim.setRow2Occupied(it) }
        ToggleRow("안전벨트 채움", "점검 실패 테스트", car.beltBuckled == true) { sim.setBelt(it) }
        ToggleRow("기어 P", "끄면 시작 불가", car.gearPark == true) { sim.setGearPark(it) }
        ToggleRow("브레이크 페달 밟음", "PIN 초기화 테스트용", car.brakePressed == true) { sim.setBrake(it) }
        ToggleRow("문 열림", "작동 중이면 즉시 정지", car.doorOpen == true) { sim.setDoorOpen(it) }
        ToggleRow("테일게이트 열림", "", car.tailgateOpen == true) { sim.setTailgate(it) }
        Sub("배터리 ${car.batteryPct ?: 0}%", 16.sp)
        Slider(
            value = (car.batteryPct ?: 0).toFloat(),
            onValueChange = { sim.setBattery(Math.round(it)) },
            valueRange = 5f..100f,
            colors = SliderDefaults.colors(thumbColor = C.amber, activeTrackColor = C.amber)
        )
        TextButton(onClick = onSunrise) {
            Text("일출 화면 시연 (기상 알람 흉내)", color = C.amber, fontSize = 18.sp)
        }
    }
}

@Composable
fun LogPanel(lines: List<String>, modifier: Modifier, onClose: () -> Unit) {
    Panel(modifier.width(920.dp).height(380.dp), background = Color(0xF00A0E14)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("로그", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("닫기", color = C.sub) }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            lines.takeLast(80).reversed().forEach { Text(it, color = C.sub, fontSize = 15.sp) }
        }
    }
}

@Composable
fun ToastBar(message: String, modifier: Modifier, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(6000)
        onDismiss()
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3222), Color(0xFF221D14))))
            .clickable { onDismiss() }
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = C.text, fontSize = 20.sp)
    }
}
