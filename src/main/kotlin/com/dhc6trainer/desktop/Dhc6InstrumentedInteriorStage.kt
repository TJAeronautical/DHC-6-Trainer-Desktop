package com.dhc6trainer.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Paints a functional 2D instrument stack onto the 3D interior view. This is a
 * deliberate bridge step: the imported 3D cockpit can remain a sparse shell,
 * while the simulator still gets readable, animated DHC-6 instruments and
 * movable procedure controls driven by the same [DesktopCockpitSimState] as the
 * Legacy 2D cockpit.
 */
@Composable
internal fun Dhc6InstrumentedInteriorStage(
    state: DesktopCockpitSimState,
    selectedTarget: CockpitHitboxTarget?,
    onStateChange: (DesktopCockpitSimState) -> Unit,
    onSelectTarget: (CockpitHitboxTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val zones = remember { Instrumented3dControlZones }

    Box(modifier = modifier) {
        Dhc6InteriorStage(modifier = Modifier.fillMaxSize())
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state, zones) {
                    detectTapGestures { tap ->
                        val normalized = Offset(
                            x = tap.x / size.width.toFloat().coerceAtLeast(1f),
                            y = tap.y / size.height.toFloat().coerceAtLeast(1f),
                        )
                        zones.asReversed().firstOrNull { it.rect.contains(normalized) }?.let { zone ->
                            DefaultCockpitTargets.firstOrNull { it.id == zone.targetId }?.let(onSelectTarget)
                            onStateChange(zone.update(state))
                        }
                    }
                },
        ) {
            drawInstrumented3dOverlay(
                state = state,
                selectedTargetId = selectedTarget?.id,
                textMeasurer = textMeasurer,
            )
        }
    }
}

private data class Instrumented3dControlZone(
    val label: String,
    val targetId: String,
    val rect: Rect,
    val update: (DesktopCockpitSimState) -> DesktopCockpitSimState,
)

private val Instrumented3dControlZones = listOf(
    Instrumented3dControlZone("Flight instruments", "engine-instruments", Rect(0.150f, 0.530f, 0.650f, 0.770f)) { it },
    Instrumented3dControlZone("Annunciators", "annunciators", Rect(0.355f, 0.472f, 0.650f, 0.535f)) { it },
    Instrumented3dControlZone("Battery", "electrical-panel", Rect(0.725f, 0.585f, 0.755f, 0.675f)) {
        it.copy(batteryMaster = !it.batteryMaster)
    },
    Instrumented3dControlZone("Avionics", "electrical-panel", Rect(0.760f, 0.585f, 0.790f, 0.675f)) {
        it.copy(avionicsMaster = !it.avionicsMaster)
    },
    Instrumented3dControlZone("L gen", "electrical-panel", Rect(0.795f, 0.585f, 0.825f, 0.675f)) {
        it.copy(leftDcGenerator = !it.leftDcGenerator)
    },
    Instrumented3dControlZone("R gen", "electrical-panel", Rect(0.830f, 0.585f, 0.860f, 0.675f)) {
        it.copy(rightDcGenerator = !it.rightDcGenerator)
    },
    Instrumented3dControlZone("Fwd boost", "fuel-panel", Rect(0.695f, 0.705f, 0.735f, 0.795f)) {
        it.copy(fwdBoost1 = !it.fwdBoost1, fwdBoost2 = !it.fwdBoost2)
    },
    Instrumented3dControlZone("Aft boost", "fuel-panel", Rect(0.740f, 0.705f, 0.780f, 0.795f)) {
        it.copy(aftBoost1 = !it.aftBoost1, aftBoost2 = !it.aftBoost2)
    },
    Instrumented3dControlZone("Crossfeed", "fuel-panel", Rect(0.785f, 0.705f, 0.825f, 0.795f)) {
        it.copy(crossfeed = it.crossfeed.next())
    },
    Instrumented3dControlZone("L power", "power-levers", Rect(0.390f, 0.780f, 0.445f, 0.960f)) {
        it.copy(leftPower = it.leftPower.next())
    },
    Instrumented3dControlZone("R power", "power-levers", Rect(0.450f, 0.780f, 0.505f, 0.960f)) {
        it.copy(rightPower = it.rightPower.next())
    },
    Instrumented3dControlZone("L fuel", "fuel-panel", Rect(0.525f, 0.805f, 0.575f, 0.960f)) {
        it.copy(leftFuelLeverOn = !it.leftFuelLeverOn)
    },
    Instrumented3dControlZone("R fuel", "fuel-panel", Rect(0.580f, 0.805f, 0.630f, 0.960f)) {
        it.copy(rightFuelLeverOn = !it.rightFuelLeverOn)
    },
    Instrumented3dControlZone("Flaps", "flaps-hydraulic", Rect(0.645f, 0.805f, 0.700f, 0.960f)) {
        it.copy(flaps = it.flaps.next())
    },
    Instrumented3dControlZone("L fire", "fire-panel", Rect(0.290f, 0.468f, 0.345f, 0.548f)) {
        val pulled = !it.leftFireHandlePulled
        it.copy(leftFireHandlePulled = pulled, leftFuelLeverOn = if (pulled) false else it.leftFuelLeverOn)
    },
    Instrumented3dControlZone("R fire", "fire-panel", Rect(0.655f, 0.468f, 0.710f, 0.548f)) {
        val pulled = !it.rightFireHandlePulled
        it.copy(rightFireHandlePulled = pulled, rightFuelLeverOn = if (pulled) false else it.rightFuelLeverOn)
    },
)

private fun DrawScope.drawInstrumented3dOverlay(
    state: DesktopCockpitSimState,
    selectedTargetId: String?,
    textMeasurer: TextMeasurer,
) {
    val w = size.width
    val h = size.height
    val panelTop = h * 0.455f

    draw3dGlareshield(panelTop)
    draw3dAnnunciatorPanel(state, textMeasurer)
    draw3dPrimaryFlightInstruments(state, textMeasurer)
    draw3dEngineCluster(state, textMeasurer)
    draw3dElectricalSwitchPanel(state, textMeasurer)
    draw3dFuelSwitchPanel(state, textMeasurer)
    draw3dPedestalControls(state, textMeasurer)
    draw3dFireHandles(state, textMeasurer)
    draw3dSelectedTargetCue(selectedTargetId)
}

private fun DrawScope.draw3dGlareshield(panelTop: Float) {
    val w = size.width
    val h = size.height
    val glare = Path().apply {
        moveTo(w * 0.075f, panelTop)
        cubicTo(w * 0.225f, h * 0.405f, w * 0.775f, h * 0.405f, w * 0.925f, panelTop)
        lineTo(w * 0.990f, h * 0.965f)
        lineTo(w * 0.010f, h * 0.965f)
        close()
    }
    drawPath(
        path = glare,
        brush = Brush.verticalGradient(
            listOf(Color(0xEE252A2E), Color(0xF20A0D10), Color(0xFA020406)),
            startY = panelTop,
            endY = h * 0.965f,
        ),
    )
    drawPath(path = glare, color = Color(0xCC4C5962), style = Stroke(width = 2.4f))

    drawRoundRect(
        color = Color(0xEE11171C),
        topLeft = Offset(w * 0.118f, h * 0.522f),
        size = Size(w * 0.540f, h * 0.250f),
        cornerRadius = CornerRadius(20f, 20f),
    )
    drawRoundRect(
        color = Color(0xFF52616C),
        topLeft = Offset(w * 0.118f, h * 0.522f),
        size = Size(w * 0.540f, h * 0.250f),
        cornerRadius = CornerRadius(20f, 20f),
        style = Stroke(width = 2.2f),
    )
}

private fun DrawScope.draw3dPrimaryFlightInstruments(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val r = min(w, h) * 0.043f
    val torqueAverage = (state.torquePercent(true) + state.torquePercent(false)) / 2f
    val airspeed = if (state.leftEngineRunning || state.rightEngineRunning) (72f + torqueAverage * 1.10f).coerceIn(0f, 165f) else 0f
    val altitude = if (state.leftEngineRunning || state.rightEngineRunning) 1180f + torqueAverage * 18f + state.flaps.degrees * 9f else 0f
    val vsi = when {
        !state.leftEngineRunning && !state.rightEngineRunning -> 0f
        torqueAverage > 70f -> 1100f
        torqueAverage > 45f -> 250f
        else -> -450f
    }
    val roll = ((state.torquePercent(true) - state.torquePercent(false)) * 0.35f).coerceIn(-18f, 18f)
    val pitch = when {
        torqueAverage > 75f -> 7f
        torqueAverage > 45f -> 2f
        else -> -2f
    }

    val centers = listOf(
        Offset(w * 0.205f, h * 0.600f),
        Offset(w * 0.305f, h * 0.600f),
        Offset(w * 0.405f, h * 0.600f),
        Offset(w * 0.205f, h * 0.710f),
        Offset(w * 0.305f, h * 0.710f),
        Offset(w * 0.405f, h * 0.710f),
    )

    drawAnalogGauge(centers[0], r, "ASI", airspeed / 165f, "${airspeed.roundToInt()}", textMeasurer)
    drawAttitudeGauge(centers[1], r, rollDegrees = roll, pitchDegrees = pitch, textMeasurer = textMeasurer)
    drawAnalogGauge(centers[2], r, "ALT", (altitude % 10000f) / 10000f, "${altitude.roundToInt()}", textMeasurer)
    drawAnalogGauge(centers[3], r, "VSI", ((vsi + 2000f) / 4000f).coerceIn(0f, 1f), "${vsi.roundToInt()}", textMeasurer)
    drawCompassGauge(centers[4], r, headingDegrees = 285f, textMeasurer = textMeasurer)
    drawAnalogGauge(centers[5], r, "FLAP", state.flaps.degrees / 37.5f, state.flaps.label, textMeasurer)
}

private fun DrawScope.draw3dEngineCluster(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val r = min(w, h) * 0.034f
    val leftTorque = state.torquePercent(true).toFloat()
    val rightTorque = state.torquePercent(false).toFloat()
    val leftItt = if (state.leftEngineRunning) (410f + leftTorque * 3.2f).coerceAtMost(730f) else 0f
    val rightItt = if (state.rightEngineRunning) (410f + rightTorque * 3.2f).coerceAtMost(730f) else 0f
    val leftNg = if (state.leftEngineRunning) (52f + leftTorque * 0.44f).coerceAtMost(101f) else 0f
    val rightNg = if (state.rightEngineRunning) (52f + rightTorque * 0.44f).coerceAtMost(101f) else 0f
    val leftRpm = if (state.leftEngineRunning) 1800f + leftTorque * 2.2f else 0f
    val rightRpm = if (state.rightEngineRunning) 1800f + rightTorque * 2.2f else 0f

    drawRoundRect(
        color = Color(0xEE0B1015),
        topLeft = Offset(w * 0.465f, h * 0.545f),
        size = Size(w * 0.170f, h * 0.205f),
        cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = Color(0xFF52616C),
        topLeft = Offset(w * 0.465f, h * 0.545f),
        size = Size(w * 0.170f, h * 0.205f),
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 1.8f),
    )
    drawCockpitText(textMeasurer, "ENGINE", w * 0.465f, h * 0.552f, w * 0.170f, min(w, h) * 0.014f, Color(0xFFDCE7F0), center = true)

    val centers = listOf(
        Offset(w * 0.505f, h * 0.605f),
        Offset(w * 0.592f, h * 0.605f),
        Offset(w * 0.505f, h * 0.690f),
        Offset(w * 0.592f, h * 0.690f),
    )
    drawAnalogGauge(centers[0], r, "TQ L", leftTorque / 100f, leftTorque.roundToInt().toString(), textMeasurer)
    drawAnalogGauge(centers[1], r, "TQ R", rightTorque / 100f, rightTorque.roundToInt().toString(), textMeasurer)
    drawDualNeedleGauge(centers[2], r, "ITT", leftItt / 800f, rightItt / 800f, textMeasurer)
    drawDualNeedleGauge(centers[3], r, "NG/RPM", leftNg / 105f, rightRpm / 2200f, textMeasurer)

    drawEngineStatusStrip(state, textMeasurer)
}

private fun DrawScope.drawEngineStatusStrip(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val topLeft = Offset(w * 0.468f, h * 0.752f)
    val cellW = w * 0.039f
    val cellH = h * 0.024f
    val statuses = listOf(
        "L FUEL" to state.leftFuelPressure,
        "R FUEL" to state.rightFuelPressure,
        "L GEN" to state.leftGeneratorOnline,
        "R GEN" to state.rightGeneratorOnline,
    )
    statuses.forEachIndexed { index, (label, on) ->
        val left = topLeft.x + index * (cellW + w * 0.004f)
        drawRoundRect(
            color = if (on) Color(0xFF183C28) else Color(0xFF2C2113),
            topLeft = Offset(left, topLeft.y),
            size = Size(cellW, cellH),
            cornerRadius = CornerRadius(5f, 5f),
        )
        drawRoundRect(
            color = if (on) Color(0xFF69E481) else Color(0xFFFFB15C),
            topLeft = Offset(left, topLeft.y),
            size = Size(cellW, cellH),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = 1f),
        )
        drawCockpitText(textMeasurer, label, left, topLeft.y + cellH * 0.25f, cellW, cellH * 0.42f, if (on) Color(0xFF69E481) else Color(0xFFFFB15C), center = true)
    }
}

private fun DrawScope.draw3dAnnunciatorPanel(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val left = w * 0.360f
    val top = h * 0.474f
    val width = w * 0.285f
    val height = h * 0.052f
    drawRoundRect(Color(0xEE05080B), Offset(left, top), Size(width, height), CornerRadius(8f, 8f))
    drawRoundRect(Color(0xFF36424A), Offset(left, top), Size(width, height), CornerRadius(8f, 8f), style = Stroke(width = 1.4f))

    val annunciators = listOf(
        "L FUEL" to !state.leftFuelPressure,
        "R FUEL" to !state.rightFuelPressure,
        "L GEN" to (state.leftEngineRunning && !state.leftGeneratorOnline),
        "R GEN" to (state.rightEngineRunning && !state.rightGeneratorOnline),
        "FIRE L" to state.leftEngineFire,
        "FIRE R" to state.rightEngineFire,
    )
    val cols = 6
    annunciators.forEachIndexed { index, (label, active) ->
        val cellLeft = left + width * 0.018f + index * width * 0.160f
        val cellTop = top + height * 0.22f
        val cellSize = Size(width * 0.140f, height * 0.58f)
        val fire = label.contains("FIRE")
        drawRoundRect(
            color = when {
                active && fire -> Color(0xFF681F1B)
                active -> Color(0xFF6A4B12)
                else -> Color(0xFF151B20)
            },
            topLeft = Offset(cellLeft, cellTop),
            size = cellSize,
            cornerRadius = CornerRadius(4f, 4f),
        )
        drawRoundRect(
            color = if (active) (if (fire) Color(0xFFFF5C5C) else Color(0xFFFFC75A)) else Color(0xFF3E4A52),
            topLeft = Offset(cellLeft, cellTop),
            size = cellSize,
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1f),
        )
        drawCockpitText(
            textMeasurer,
            label,
            cellLeft,
            cellTop + cellSize.height * 0.30f,
            cellSize.width,
            cellSize.height * 0.33f,
            if (active) Color(0xFFFFF1C2) else Color(0xFF6E7880),
            center = true,
        )
    }
}

private fun DrawScope.draw3dElectricalSwitchPanel(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val left = w * 0.705f
    val top = h * 0.565f
    val width = w * 0.180f
    val height = h * 0.145f
    drawRoundRect(Color(0xEE10161A), Offset(left, top), Size(width, height), CornerRadius(12f, 12f))
    drawRoundRect(Color(0xFF54616A), Offset(left, top), Size(width, height), CornerRadius(12f, 12f), style = Stroke(width = 1.8f))
    drawCockpitText(textMeasurer, "ELECTRICAL", left, top + height * 0.070f, width, height * 0.120f, Color(0xFFDCE7F0), center = true)

    val switches = listOf(
        Triple("BAT", state.batteryMaster, 0.105f),
        Triple("AVN", state.avionicsMaster, 0.305f),
        Triple("L GEN", state.leftDcGenerator, 0.505f),
        Triple("R GEN", state.rightDcGenerator, 0.705f),
    )
    switches.forEach { (label, on, position) ->
        drawMiniToggle(
            center = Offset(left + width * position, top + height * 0.560f),
            height = height * 0.42f,
            on = on,
            label = label,
            textMeasurer = textMeasurer,
        )
    }
}

private fun DrawScope.draw3dFuelSwitchPanel(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val left = w * 0.675f
    val top = h * 0.695f
    val width = w * 0.185f
    val height = h * 0.125f
    drawRoundRect(Color(0xEE10161A), Offset(left, top), Size(width, height), CornerRadius(12f, 12f))
    drawRoundRect(Color(0xFF54616A), Offset(left, top), Size(width, height), CornerRadius(12f, 12f), style = Stroke(width = 1.8f))
    drawCockpitText(textMeasurer, "FUEL", left, top + height * 0.070f, width, height * 0.150f, Color(0xFFDCE7F0), center = true)

    val switches = listOf(
        Triple("FWD", state.fwdBoost1 || state.fwdBoost2, 0.170f),
        Triple("AFT", state.aftBoost1 || state.aftBoost2, 0.410f),
        Triple(state.crossfeed.label.uppercase(), state.crossfeed != CockpitCrossfeedPosition.NORMAL, 0.670f),
    )
    switches.forEach { (label, on, position) ->
        drawMiniToggle(
            center = Offset(left + width * position, top + height * 0.600f),
            height = height * 0.46f,
            on = on,
            label = label,
            textMeasurer = textMeasurer,
        )
    }
}

private fun DrawScope.draw3dPedestalControls(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val pedestal = Rect(w * 0.365f, h * 0.765f, w * 0.715f, h * 0.975f)
    drawRoundRect(Color(0xF00A0D11), pedestal.topLeft, pedestal.size, CornerRadius(18f, 18f))
    drawRoundRect(Color(0xFF58656F), pedestal.topLeft, pedestal.size, CornerRadius(18f, 18f), style = Stroke(width = 2f))
    drawCockpitText(textMeasurer, "PEDESTAL / QUADRANT", pedestal.left, pedestal.top + pedestal.height * 0.045f, pedestal.width, pedestal.height * 0.075f, Color(0xFFDCE7F0), center = true)

    drawLeverSlot(
        x = w * 0.418f,
        top = h * 0.815f,
        bottom = h * 0.948f,
        knobProgress = powerLeverProgress(state.leftPower),
        knobColor = Color(0xFF22272B),
        label = "PWR L",
        textMeasurer = textMeasurer,
    )
    drawLeverSlot(
        x = w * 0.475f,
        top = h * 0.815f,
        bottom = h * 0.948f,
        knobProgress = powerLeverProgress(state.rightPower),
        knobColor = Color(0xFF22272B),
        label = "PWR R",
        textMeasurer = textMeasurer,
    )
    drawLeverSlot(
        x = w * 0.550f,
        top = h * 0.825f,
        bottom = h * 0.950f,
        knobProgress = if (state.leftFuelLeverOn) 1f else 0f,
        knobColor = if (state.leftFuelLeverOn) Color(0xFFB91F1F) else Color(0xFF651919),
        label = "FUEL L",
        textMeasurer = textMeasurer,
    )
    drawLeverSlot(
        x = w * 0.603f,
        top = h * 0.825f,
        bottom = h * 0.950f,
        knobProgress = if (state.rightFuelLeverOn) 1f else 0f,
        knobColor = if (state.rightFuelLeverOn) Color(0xFFB91F1F) else Color(0xFF651919),
        label = "FUEL R",
        textMeasurer = textMeasurer,
    )
    drawLeverSlot(
        x = w * 0.668f,
        top = h * 0.825f,
        bottom = h * 0.950f,
        knobProgress = state.flaps.degrees / 37.5f,
        knobColor = Color(0xFF4A5E6C),
        label = "FLAPS",
        textMeasurer = textMeasurer,
    )
}

private fun DrawScope.draw3dFireHandles(state: DesktopCockpitSimState, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    drawFireHandle(
        center = Offset(w * 0.318f, h * if (state.leftFireHandlePulled) 0.535f else 0.505f),
        pulled = state.leftFireHandlePulled || state.leftEngineFire,
        label = "L FIRE",
        textMeasurer = textMeasurer,
    )
    drawFireHandle(
        center = Offset(w * 0.683f, h * if (state.rightFireHandlePulled) 0.535f else 0.505f),
        pulled = state.rightFireHandlePulled || state.rightEngineFire,
        label = "R FIRE",
        textMeasurer = textMeasurer,
    )
}

private fun DrawScope.draw3dSelectedTargetCue(selectedTargetId: String?) {
    if (selectedTargetId == null) return
    Instrumented3dControlZones
        .filter { it.targetId == selectedTargetId }
        .forEach { zone ->
            val rect = zone.rect.toViewportRect(size)
            drawRoundRect(
                color = Color(0x2255C7FF),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = Color(0xFF55C7FF),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2.2f),
            )
        }
}

private fun DrawScope.drawAnalogGauge(
    center: Offset,
    radius: Float,
    label: String,
    normalizedValue: Float,
    valueLabel: String,
    textMeasurer: TextMeasurer,
) {
    val value = normalizedValue.coerceIn(0f, 1f)
    drawCircle(Color(0xFF020406), radius = radius * 1.08f, center = center)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF1C252D), Color(0xFF05080A)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
    drawCircle(Color(0xFF74818B), radius = radius, center = center, style = Stroke(width = radius * 0.055f))
    drawArc(
        color = Color(0xFF55C7FF),
        startAngle = 135f,
        sweepAngle = 270f * value,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.82f, center.y - radius * 0.82f),
        size = Size(radius * 1.64f, radius * 1.64f),
        style = Stroke(width = radius * 0.055f),
    )
    repeat(9) { tick ->
        val angle = 135f + tick / 8f * 270f
        val start = pointOnCircle(center, radius * 0.74f, angle)
        val end = pointOnCircle(center, radius * 0.88f, angle)
        drawLine(Color(0xFFDCE7F0), start, end, strokeWidth = if (tick % 2 == 0) radius * 0.035f else radius * 0.022f)
    }
    val needleAngle = 135f + value * 270f
    drawLine(Color(0xFFFFF3D0), center, pointOnCircle(center, radius * 0.66f, needleAngle), strokeWidth = radius * 0.045f)
    drawCircle(Color(0xFFDCE7F0), radius = radius * 0.075f, center = center)
    drawCockpitText(textMeasurer, label, center.x - radius, center.y - radius * 0.55f, radius * 2f, radius * 0.24f, Color(0xFFE7EEF4), center = true)
    drawCockpitText(textMeasurer, valueLabel, center.x - radius, center.y + radius * 0.42f, radius * 2f, radius * 0.25f, Color(0xFF9FD6F0), center = true)
}

private fun DrawScope.drawDualNeedleGauge(
    center: Offset,
    radius: Float,
    label: String,
    leftValue: Float,
    rightValue: Float,
    textMeasurer: TextMeasurer,
) {
    drawAnalogGauge(center, radius, label, 0f, "L/R", textMeasurer)
    val lAngle = 135f + leftValue.coerceIn(0f, 1f) * 270f
    val rAngle = 135f + rightValue.coerceIn(0f, 1f) * 270f
    drawLine(Color(0xFFFFF3D0), center, pointOnCircle(center, radius * 0.68f, lAngle), strokeWidth = radius * 0.038f)
    drawLine(Color(0xFFFF835C), center, pointOnCircle(center, radius * 0.58f, rAngle), strokeWidth = radius * 0.038f)
}

private fun DrawScope.drawAttitudeGauge(
    center: Offset,
    radius: Float,
    rollDegrees: Float,
    pitchDegrees: Float,
    textMeasurer: TextMeasurer,
) {
    drawCircle(Color(0xFF020406), radius = radius * 1.08f, center = center)
    drawCircle(Color(0xFF1D6FAE), radius = radius, center = center)
    drawArc(
        color = Color(0xFF8A5D35),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius + pitchDegrees * radius * 0.035f),
        size = Size(radius * 2f, radius * 2f),
    )
    rotate(rollDegrees, pivot = center) {
        drawLine(
            color = Color.White,
            start = Offset(center.x - radius * 0.62f, center.y + pitchDegrees * radius * 0.030f),
            end = Offset(center.x + radius * 0.62f, center.y + pitchDegrees * radius * 0.030f),
            strokeWidth = radius * 0.045f,
        )
        drawLine(Color.White, Offset(center.x - radius * 0.26f, center.y - radius * 0.16f), Offset(center.x - radius * 0.10f, center.y - radius * 0.16f), strokeWidth = radius * 0.035f)
        drawLine(Color.White, Offset(center.x + radius * 0.10f, center.y - radius * 0.16f), Offset(center.x + radius * 0.26f, center.y - radius * 0.16f), strokeWidth = radius * 0.035f)
    }
    drawCircle(Color(0xFF74818B), radius = radius, center = center, style = Stroke(width = radius * 0.055f))
    drawCockpitText(textMeasurer, "ATT", center.x - radius, center.y + radius * 0.45f, radius * 2f, radius * 0.23f, Color(0xFFE7EEF4), center = true)
}

private fun DrawScope.drawCompassGauge(center: Offset, radius: Float, headingDegrees: Float, textMeasurer: TextMeasurer) {
    drawCircle(Color(0xFF020406), radius = radius * 1.08f, center = center)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1A242B), Color(0xFF05080A)), center, radius), radius = radius, center = center)
    drawCircle(Color(0xFF74818B), radius = radius, center = center, style = Stroke(width = radius * 0.055f))
    listOf("N", "E", "S", "W").forEachIndexed { index, label ->
        val angle = index * 90f - headingDegrees - 90f
        val p = pointOnCircle(center, radius * 0.62f, angle)
        drawCockpitText(textMeasurer, label, p.x - radius * 0.15f, p.y - radius * 0.12f, radius * 0.30f, radius * 0.24f, Color(0xFFE7EEF4), center = true)
    }
    val lubber = Path().apply {
        moveTo(center.x, center.y - radius * 0.78f)
        lineTo(center.x - radius * 0.09f, center.y - radius * 0.55f)
        lineTo(center.x + radius * 0.09f, center.y - radius * 0.55f)
        close()
    }
    drawPath(lubber, Color(0xFFFFF3D0))
    drawCockpitText(textMeasurer, "HDG", center.x - radius, center.y + radius * 0.42f, radius * 2f, radius * 0.23f, Color(0xFF9FD6F0), center = true)
}

private fun DrawScope.drawMiniToggle(center: Offset, height: Float, on: Boolean, label: String, textMeasurer: TextMeasurer) {
    val w = height * 0.42f
    val body = Rect(center.x - w, center.y - height * 0.55f, center.x + w, center.y + height * 0.35f)
    drawRoundRect(Color(0xFF05080B), body.topLeft, body.size, CornerRadius(w * 0.35f, w * 0.35f))
    drawRoundRect(if (on) Color(0xFF6BE675) else Color(0xFF56616A), body.topLeft, body.size, CornerRadius(w * 0.35f, w * 0.35f), style = Stroke(width = 1.5f))
    val knobY = if (on) body.top + body.height * 0.18f else body.top + body.height * 0.72f
    drawLine(
        color = if (on) Color(0xFF6BE675) else Color(0xFFDCE7F0),
        start = Offset(center.x, body.bottom - body.height * 0.12f),
        end = Offset(center.x + if (on) w * 0.30f else -w * 0.22f, knobY),
        strokeWidth = w * 0.32f,
    )
    drawCircle(if (on) Color(0xFFB9FFBF) else Color(0xFFDCE7F0), radius = w * 0.26f, center = Offset(center.x + if (on) w * 0.30f else -w * 0.22f, knobY))
    drawCockpitText(textMeasurer, label, center.x - height * 0.58f, body.bottom + height * 0.12f, height * 1.16f, height * 0.18f, Color(0xFFDCE7F0), center = true)
}

private fun DrawScope.drawLeverSlot(
    x: Float,
    top: Float,
    bottom: Float,
    knobProgress: Float,
    knobColor: Color,
    label: String,
    textMeasurer: TextMeasurer,
) {
    val height = bottom - top
    val width = height * 0.16f
    val y = bottom - knobProgress.coerceIn(0f, 1f) * height
    drawRoundRect(Color(0xFF05080B), Offset(x - width * 0.35f, top), Size(width * 0.70f, height), CornerRadius(width * 0.28f, width * 0.28f))
    drawLine(Color(0xFF39454E), Offset(x, top + height * 0.05f), Offset(x, bottom - height * 0.05f), strokeWidth = width * 0.22f)
    drawLine(Color(0xFFBFC7CE), Offset(x, y + height * 0.040f), Offset(x, bottom), strokeWidth = width * 0.18f)
    drawRoundRect(knobColor, Offset(x - width * 0.75f, y - width * 0.52f), Size(width * 1.50f, width * 1.04f), CornerRadius(width * 0.35f, width * 0.35f))
    drawRoundRect(Color(0xFFDCE7F0), Offset(x - width * 0.75f, y - width * 0.52f), Size(width * 1.50f, width * 1.04f), CornerRadius(width * 0.35f, width * 0.35f), style = Stroke(width = 1.1f))
    drawCockpitText(textMeasurer, label, x - width * 1.6f, bottom + height * 0.035f, width * 3.2f, height * 0.075f, Color(0xFFDCE7F0), center = true)
}

private fun DrawScope.drawFireHandle(center: Offset, pulled: Boolean, label: String, textMeasurer: TextMeasurer) {
    val r = min(size.width, size.height) * 0.020f
    drawRoundRect(
        color = Color(0xFF080B0E),
        topLeft = Offset(center.x - r * 1.40f, center.y - r * 0.70f),
        size = Size(r * 2.80f, r * 1.40f),
        cornerRadius = CornerRadius(r * 0.30f, r * 0.30f),
    )
    drawRoundRect(
        color = if (pulled) Color(0xFFFF5C5C) else Color(0xFF7A1E1E),
        topLeft = Offset(center.x - r * 1.20f, center.y - r * 0.50f),
        size = Size(r * 2.40f, r),
        cornerRadius = CornerRadius(r * 0.26f, r * 0.26f),
    )
    drawRoundRect(
        color = Color(0xFFEAD8C2),
        topLeft = Offset(center.x - r * 1.20f, center.y - r * 0.50f),
        size = Size(r * 2.40f, r),
        cornerRadius = CornerRadius(r * 0.26f, r * 0.26f),
        style = Stroke(width = 1.2f),
    )
    drawCockpitText(textMeasurer, label, center.x - r * 2.2f, center.y + r * 0.90f, r * 4.4f, r * 0.62f, if (pulled) Color(0xFFFFC2B0) else Color(0xFFDCE7F0), center = true)
}

private fun DrawScope.drawCockpitText(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    width: Float,
    pxHeight: Float,
    color: Color,
    center: Boolean = false,
) {
    if (text.isBlank() || pxHeight < 4f || width <= 0f) return
    val result = textMeasurer.measure(
        text = text,
        style = TextStyle(color = color, fontSize = pxHeight.toSp(), fontWeight = FontWeight.Black),
        maxLines = 1,
    )
    val drawX = if (center) x + (width - result.size.width) / 2f else x
    drawText(result, color = color, topLeft = Offset(drawX, y))
}

private fun powerLeverProgress(position: CockpitPowerLeverPosition): Float = when (position) {
    CockpitPowerLeverPosition.REVERSE -> 0.06f
    CockpitPowerLeverPosition.IDLE -> 0.24f
    CockpitPowerLeverPosition.CRUISE -> 0.62f
    CockpitPowerLeverPosition.CLIMB -> 0.78f
    CockpitPowerLeverPosition.MAX -> 0.94f
}

private fun pointOnCircle(center: Offset, radius: Float, angleDegrees: Float): Offset {
    val radians = angleDegrees / 180f * PI.toFloat()
    return Offset(
        x = center.x + cos(radians) * radius,
        y = center.y + sin(radians) * radius,
    )
}

private fun Rect.toViewportRect(size: Size): Rect = Rect(
    left = left * size.width,
    top = top * size.height,
    right = right * size.width,
    bottom = bottom * size.height,
)
