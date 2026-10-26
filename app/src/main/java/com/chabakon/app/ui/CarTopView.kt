package com.chabakon.app.ui

import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.chabakon.app.R
import com.chabakon.app.vehicle.CarState
import com.chabakon.app.vehicle.SeatPos

/** 좌석 상태 → 테두리 색 */
private fun seatColor(v: Boolean?): Color = when (v) {
    false -> C.green   // 비어 있음
    true -> C.red      // 사람 있음
    null -> C.amber    // 센서 없음
}

/**
 * 3D 렌더 그림(car_interior.png, 644x1523) 위의 좌석 위치. 그림 크기 대비 0~1 비율 [left, top, right, bottom]
 * three.js 로 렌더할 때 좌석 모델을 화면에 투영해서 얻은 값이다.
 */
private val SEAT_BOX = mapOf(
    SeatPos.ROW1_LEFT to floatArrayOf(0.1661f, 0.4268f, 0.441f, 0.5962f),
    SeatPos.ROW1_RIGHT to floatArrayOf(0.5621f, 0.4268f, 0.8447f, 0.5962f),
    SeatPos.ROW2_LEFT to floatArrayOf(0.1739f, 0.5975f, 0.3929f, 0.7702f),
    SeatPos.ROW2_CENTER to floatArrayOf(0.3991f, 0.5975f, 0.6118f, 0.7702f),
    SeatPos.ROW2_RIGHT to floatArrayOf(0.618f, 0.5975f, 0.8323f, 0.7702f)
)
private const val CAR_IMG_RATIO = 644f / 1523f

/**
 * 위에서 내려다본 3D 차 실내.
 * - 2열 좌석 상태는 시트 테두리 색(초록/빨강/주황)
 * - moving 에 들어 있는 좌석은 빨간색으로 깜박인다 (평탄화·복귀 중)
 */
@Composable
fun CarTopView(car: CarState, modifier: Modifier = Modifier, moving: Set<SeatPos> = emptySet(), showLegend: Boolean = true) {
    val row2 = listOf(SeatPos.ROW2_LEFT, SeatPos.ROW2_CENTER, SeatPos.ROW2_RIGHT).associateWith { p ->
        if (car.row2Seats.isEmpty()) car.row2Occupied else car.row2Seats[p]
    }
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blinkA"
    )
    val rearMoving = moving.any { it.name.startsWith("ROW2") }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight().aspectRatio(CAR_IMG_RATIO, matchHeightConstraintsFirst = true)) {
                // 열린 트렁크 불빛 (그림 뒤)
                if (car.tailgateOpen == true) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRoundRect(
                            Brush.verticalGradient(listOf(C.amber.copy(alpha = 0.0f), C.amber.copy(alpha = 0.55f)),
                                startY = size.height * 0.86f, endY = size.height),
                            Offset(size.width * 0.12f, size.height * 0.86f), Size(size.width * 0.76f, size.height * 0.14f),
                            CornerRadius(30f)
                        )
                    }
                }
                Image(
                    painterResource(R.drawable.car_interior),
                    contentDescription = "차 실내",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(Modifier.matchParentSize()) {
                    fun box(p: SeatPos): Pair<Offset, Size> {
                        val b = SEAT_BOX.getValue(p)
                        return Offset(b[0] * size.width, b[1] * size.height) to
                            Size((b[2] - b[0]) * size.width, (b[3] - b[1]) * size.height)
                    }
                    // 2열 상태 테두리
                    row2.forEach { (p, v) ->
                        val (o, s) = box(p)
                        if (!rearMoving) drawStateRing(o, s, seatColor(v))
                    }
                    // 움직이는 좌석: 빨간색 깜박임
                    SEAT_BOX.keys.forEach { p ->
                        val isMoving = p in moving || (rearMoving && p.name.startsWith("ROW2"))
                        if (isMoving) {
                            val (o, s) = box(p)
                            drawMovingSeat(o, s, blink)
                        }
                    }
                    // 문 열림: 양옆 빨간 점선
                    if (car.doorOpen == true) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                        drawLine(C.red, Offset(size.width * 0.06f, size.height * 0.4f), Offset(size.width * 0.06f, size.height * 0.85f), 6f, pathEffect = dash)
                        drawLine(C.red, Offset(size.width * 0.94f, size.height * 0.4f), Offset(size.width * 0.94f, size.height * 0.85f), 6f, pathEffect = dash)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 트렁크는 열려도 닫혀도 상관없으므로, 열렸을 때만 알려 준다
            if (car.tailgateOpen == true) Text("트렁크 열림", color = C.amber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (car.doorOpen == true) Text("문 열림", color = C.red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (showLegend) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(C.green, "비어 있음")
                Legend(C.red, "사람 있음")
                Legend(C.amber, "센서 없음")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFFB4C1D2), fontSize = 15.sp)
    }
}

private fun DrawScope.drawStateRing(o: Offset, s: Size, color: Color) {
    // 선 없이, 시트 위에 색이 은은하게 번지는 빛
    val r = CornerRadius(s.width * 0.2f)
    drawRoundRect(color.copy(alpha = 0.10f), Offset(o.x - 10f, o.y - 10f), Size(s.width + 20f, s.height + 20f), CornerRadius(s.width * 0.28f))
    drawRoundRect(
        Brush.radialGradient(
            listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0.16f)),
            center = Offset(o.x + s.width / 2f, o.y + s.height / 2f),
            radius = maxOf(s.width, s.height) * 0.7f
        ),
        o, s, r
    )
}

private fun DrawScope.drawMovingSeat(o: Offset, s: Size, blink: Float) {
    val r = CornerRadius(s.width * 0.2f)
    drawRoundRect(C.red.copy(alpha = 0.18f * blink), Offset(o.x - 14f, o.y - 14f), Size(s.width + 28f, s.height + 28f), CornerRadius(s.width * 0.28f))
    drawRoundRect(C.red.copy(alpha = 0.2f + 0.5f * blink), o, s, r)
}
/** 멈추는 방법: 급할 때 바로 보이도록 오른쪽에 큰 볼록 버튼 3개 (테두리 선 없음) */
@Composable
fun StopMethods(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("멈추는 방법", color = Color(0xFFFF8A8A), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StopRow(Icons.Filled.TouchApp, "화면 터치", "아무 곳이나 누르기", Modifier.weight(1f))
            StopRow(Icons.Filled.Stop, "정지 버튼", "빨간 버튼 누르기", Modifier.weight(1f))
            StopRow(CarDoorIcon, "문 열기", "아무 문이나 열기", Modifier.weight(1f))
        }
    }
}

/** 옆에서 본 자동차 문 (창문 + 손잡이) */
private val CarDoorIcon: ImageVector by lazy {
    ImageVector.Builder("CarDoor", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(
                // 문 바깥 (앞쪽 위가 비스듬한 창틀) + 창문(뚫림) + 손잡이(뚫림)
                "M2.5 11.5 L10 3 H19.5 A1.5 1.5 0 0 1 21 4.5 V19 A2 2 0 0 1 19 21 H4.5 A2 2 0 0 1 2.5 19 Z " +
                    "M5.6 10.6 L11 4.9 H18.4 A0.6 0.6 0 0 1 19 5.5 V10.6 Z " +
                    "M13.6 13.2 H17.6 A0.8 0.8 0 0 1 17.6 14.8 H13.6 A0.8 0.8 0 0 1 13.6 13.2 Z"
            ),
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd
        )
        .build()
}

@Composable
private fun StopRow(icon: ImageVector, label: String, sub: String, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val base = Color(0xFF3E2329)
    val lip = 7.dp
    Box(modifier.fillMaxWidth()) {
        // 버튼 두께 (아래쪽)
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = lip)
                .clip(shape)
                .background(base.darken(0.55f))
        )
        // 버튼 윗면
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = lip)
                .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to base.lighten(0.16f),
                        0.4f to base.lighten(0.05f),
                        0.7f to base,
                        1f to base.darken(0.2f)
                    )
                )
        ) {
            // 위쪽 광택
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 볼록한 빨간 배지
                Box(
                    Modifier
                        .size(76.dp)
                        .shadow(10.dp, CircleShape, ambientColor = C.red, spotColor = C.red)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                0f to C.red.lighten(0.45f),
                                0.45f to C.red,
                                1f to C.red.darken(0.35f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 5.dp)
                            .size(width = 48.dp, height = 24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)))
                    )
                    Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(label, color = C.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(sub, color = Color(0xFFE2C4C8), fontSize = 18.sp)
                }
            }
        }
    }
}
