package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/* =====================================================================
   FreeFlightScreen - the flyable DHC-6 simulator view.

   Hosts the offscreen jME free-flight scene, captures keyboard flight
   controls, and overlays live cockpit instruments driven by the flight
   model telemetry.
   ===================================================================== */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FreeFlightScreen(
    modifier: Modifier = Modifier,
    session: FreeFlightSession = remember { FreeFlightSession() },
) {
    val jmeResult = remember(session) { JmeEngineHub.acquire(freeFlightSceneSpec(session)) }
    val focusRequester = remember { FocusRequester() }
    val pressedKeys = remember { mutableSetOf<Key>() }
    var telemetry by remember { mutableStateOf(Dhc6Telemetry()) }
    var cameraMode by remember { mutableStateOf(session.cameraMode) }
    var selectedVariantId by remember { mutableStateOf(session.selectedVariantId) }
    var showHelp by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }

    // Control integration + telemetry polling, synced to the UI frame clock.
    LaunchedEffect(session) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastNanos = now
                integrateKeyControls(session.controls, pressedKeys, dt)
                telemetry = session.telemetry
                cameraMode = session.cameraMode
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF02070B))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(18.dp))
            .onPreviewKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val first = pressedKeys.add(event.key)
                        if (first) handleDiscreteKey(event.key, session) { showHelp = !showHelp }
                        isFlightKey(event.key)
                    }
                    KeyEventType.KeyUp -> {
                        pressedKeys.remove(event.key)
                        isFlightKey(event.key)
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .onPointerEvent(PointerEventType.Press) {
                focusRequester.requestFocus()
                hasFocus = true
            }
            .focusable(),
    ) {
        jmeResult.fold(
            onSuccess = { scene3d ->
                JmeOffscreenView(
                    session = scene3d,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = "Starting flight simulator...",
                    blankFallback = {
                        FreeFlightVisualFallback(
                            environmentImage = session.environmentPackage.previewImage,
                            environmentLabel = session.environmentPackage.statusBadge,
                        )
                    },
                )
                scene3d.errorMessage.value?.let { error ->
                    FreeFlightBadge("3D error: $error", Modifier.align(Alignment.Center), warn = true)
                }
            },
            onFailure = { error ->
                FreeFlightBadge(
                    "3D renderer unavailable: ${error.message}",
                    Modifier.align(Alignment.Center),
                    warn = true,
                )
            },
        )

        // Top status row.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, top = 12.dp, end = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FreeFlightBadge("FREE FLIGHT")
            FreeFlightBadge(cameraMode.label + " view")
            FreeFlightBadge(session.sceneStatus)
            if (telemetry.paused) FreeFlightBadge("PAUSED", warn = true)
            if (!telemetry.enginesRunning) FreeFlightBadge("ENGINES OFF", warn = true)
        }

        if (session.availableAircraftOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 54.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.availableAircraftOptions.forEach { option ->
                    FreeFlightVariantChip(
                        label = option.label,
                        selected = selectedVariantId == option.id,
                    ) {
                        selectedVariantId = option.id
                        session.selectedVariantId = option.id
                    }
                }
            }
        }

        if (telemetry.stallWarning) {
            FreeFlightBadge(
                "STALL",
                Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
                warn = true,
            )
        }
        if (!hasFocus) {
            FreeFlightBadge(
                "Click the view to enable keyboard flight controls",
                Modifier.align(Alignment.Center).padding(top = 120.dp),
            )
        }

        if (cameraMode == FreeFlightCameraMode.Cockpit) {
            FreeFlightCockpitInstrumentPanel(
                telemetry = telemetry,
                throttle = session.controls.throttle,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                val compact = maxWidth < 900.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp),
                ) {
                    FlightInstrument("IAS", "${telemetry.iasKnots.roundToInt()} kt", compact)
                    FlightInstrument("ALT", "${telemetry.altitudeFt.roundToInt()} ft", compact)
                    FlightInstrument("VS", "${(telemetry.verticalSpeedFpm / 10).roundToInt() * 10} fpm", compact)
                    FlightInstrument("HDG", "${telemetry.headingDeg.roundToInt() % 360}", compact)
                    FlightInstrument("PITCH", "${telemetry.pitchDeg.roundToInt()}", compact)
                    FlightInstrument("BANK", "${telemetry.bankDeg.roundToInt()}", compact)
                    FlightInstrument("TRQ", "${telemetry.torquePercent.roundToInt()}%", compact)
                    FlightInstrument("FLAP", "${telemetry.flapsDeg.roundToInt()}", compact)
                    FlightInstrument(
                        if (telemetry.onGround) "GND" else "AIR",
                        if (telemetry.onGround) "ON" else "${telemetry.groundSpeedKt.roundToInt()} GS",
                        compact,
                    )
                    FlightInstrument("THR", "${(session.controls.throttle * 100).roundToInt()}%", compact)
                    if (session.controls.brakes) FreeFlightBadge("BRAKES", warn = true)
                }
            }
        }

        FreeFlightHelpToggle(
            expanded = showHelp,
            onClick = { showHelp = !showHelp },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
        if (showHelp) {
            FreeFlightHelpOverlay(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 104.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun FreeFlightVisualFallback(
    environmentImage: ImageBitmap?,
    environmentLabel: String,
) {
    Box(Modifier.fillMaxSize()) {
        if (environmentImage != null) {
            Image(
                bitmap = environmentImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000508)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C2230)),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Simulator preview", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(environmentLabel, color = Color(0xFF55C7FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "Live 3D view is recovering; controls and telemetry stay active.",
                color = Color(0xFFD8E5F2),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/* ---- Keyboard mapping ------------------------------------------------- */

private val flightKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.Z, Key.X, Key.PageUp, Key.PageDown, Key.F1, Key.F2, Key.F3, Key.F4,
    Key.F6, Key.F7, Key.B, Key.E, Key.C, Key.P, Key.H,
    Key.One, Key.Two, Key.Three, Key.MoveHome, Key.MoveEnd,
)

private fun isFlightKey(key: Key): Boolean = key in flightKeys

private fun handleDiscreteKey(key: Key, session: FreeFlightSession, onToggleHelp: () -> Unit) {
    val controls = session.controls
    when (key) {
        Key.F1 -> controls.throttle = 0f
        Key.F2 -> controls.throttle = (controls.throttle - 0.1f).coerceAtLeast(0f)
        Key.F3 -> controls.throttle = (controls.throttle + 0.1f).coerceAtMost(1f)
        Key.F4 -> controls.throttle = 1f
        Key.F6 -> controls.flapsIndex = (controls.flapsIndex - 1).coerceAtLeast(0)
        Key.F7 -> controls.flapsIndex = (controls.flapsIndex + 1).coerceAtMost(3)
        Key.B -> controls.brakes = !controls.brakes
        Key.E -> controls.enginesRunning = !controls.enginesRunning
        Key.P -> controls.paused = !controls.paused
        Key.C -> session.cameraMode = session.cameraMode.next()
        Key.H -> onToggleHelp()
        Key.One -> session.pendingReset = FreeFlightReset.Runway
        Key.Two -> session.pendingReset = FreeFlightReset.Final
        Key.Three -> session.pendingReset = FreeFlightReset.Cruise
        else -> Unit
    }
}

/** Smooth, auto-centering axis control from held keys; called every UI frame. */
private fun integrateKeyControls(controls: Dhc6FlightControls, pressed: Set<Key>, dt: Float) {
    if (dt <= 0f) return

    fun axis(current: Float, positive: Boolean, negative: Boolean, moveRate: Float, centerRate: Float): Float {
        val target = when {
            positive && !negative -> 1f
            negative && !positive -> -1f
            else -> 0f
        }
        val rate = if (target == 0f) centerRate else moveRate
        val delta = (target - current).coerceIn(-rate * dt, rate * dt)
        val next = current + delta
        return if (target == 0f && abs(next) < 0.02f) 0f else next
    }

    controls.elevator = axis(controls.elevator, Key.DirectionDown in pressed, Key.DirectionUp in pressed, 2.4f, 1.6f)
    controls.aileron = axis(controls.aileron, Key.DirectionRight in pressed, Key.DirectionLeft in pressed, 2.8f, 2.4f)
    controls.rudder = axis(controls.rudder, Key.X in pressed, Key.Z in pressed, 2.4f, 3.0f)

    if (Key.PageUp in pressed) controls.throttle = (controls.throttle + 0.45f * dt).coerceAtMost(1f)
    if (Key.PageDown in pressed) controls.throttle = (controls.throttle - 0.45f * dt).coerceAtLeast(0f)
    if (Key.MoveHome in pressed) controls.elevatorTrim = (controls.elevatorTrim + 0.35f * dt).coerceAtMost(1f)
    if (Key.MoveEnd in pressed) controls.elevatorTrim = (controls.elevatorTrim - 0.35f * dt).coerceAtLeast(-1f)
}

/* ---- Overlay widgets ---------------------------------------------------- */

@Composable
private fun FreeFlightCockpitInstrumentPanel(
    telemetry: Dhc6Telemetry,
    throttle: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xF2050B10))
            .border(BorderStroke(1.dp, Color(0xFF637482)), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlightDial("IAS", telemetry.iasKnots, 0f, 180f, "${telemetry.iasKnots.roundToInt()} KT")
        FlightDial("PITCH", telemetry.pitchDeg, -30f, 30f, "${telemetry.pitchDeg.roundToInt()} DEG")
        FlightDial("BANK", telemetry.bankDeg, -45f, 45f, "${telemetry.bankDeg.roundToInt()} DEG")
        FlightDial("ALT", telemetry.altitudeFt, 0f, 12_000f, "${telemetry.altitudeFt.roundToInt()} FT")
        FlightDial(
            "VSI",
            telemetry.verticalSpeedFpm,
            -2_000f,
            2_000f,
            "${(telemetry.verticalSpeedFpm / 10).roundToInt() * 10} FPM",
        )
        FlightDial("HDG", telemetry.headingDeg % 360f, 0f, 360f, "${telemetry.headingDeg.roundToInt() % 360}")
        FlightDial(
            "TRQ",
            telemetry.torquePercent,
            0f,
            110f,
            "${telemetry.torquePercent.roundToInt()}%",
            warning = telemetry.torquePercent > 90f,
        )
        FlightDial("THR", throttle * 100f, 0f, 100f, "${(throttle * 100).roundToInt()}%")
    }
}

@Composable
private fun FlightDial(
    label: String,
    value: Float,
    minimum: Float,
    maximum: Float,
    valueText: String,
    warning: Boolean = false,
) {
    val accent = if (warning) Color(0xFFFFB14A) else Color(0xFF55C7FF)
    Column(
        modifier = Modifier.widthIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Color(0xFFAFC0CD),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.47f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF010407), radius, center)
                drawCircle(Color(0xFF556874), radius, center, style = Stroke(width = 1.5f))
                drawArc(
                    color = accent.copy(alpha = 0.55f),
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                )
                repeat(11) { index ->
                    rotate(degrees = -130f + index * 26f, pivot = center) {
                        drawLine(
                            color = if (index % 5 == 0) Color.White else Color(0xFF728591),
                            start = Offset(center.x, center.y - radius * 0.82f),
                            end = Offset(center.x, center.y - radius * if (index % 5 == 0) 0.64f else 0.70f),
                            strokeWidth = if (index % 5 == 0) 2f else 1f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                val fraction = ((value - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
                rotate(degrees = -130f + fraction * 260f, pivot = center) {
                    drawLine(
                        color = accent,
                        start = center,
                        end = Offset(center.x, center.y - radius * 0.68f),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(accent, radius = 3.5f, center = center)
            }
            Text(
                text = valueText,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FlightInstrument(label: String, value: String, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xDD020B12))
            .border(BorderStroke(1.dp, Color(0xFF2D6D87)), RoundedCornerShape(10.dp))
            .padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 6.dp else 7.dp,
            )
            .widthIn(min = if (compact) 44.dp else 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label $value",
            color = Color(0xFFD8E5F2),
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun FreeFlightBadge(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (warn) Color(0xDD3B1111) else Color(0xDD020B12))
            .border(
                BorderStroke(1.dp, if (warn) Color(0xFFFF5C5C) else Color(0xFF55C7FF)),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (warn) Color(0xFFFFB4B4) else Color(0xFFD8E5F2),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun FreeFlightVariantChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                color = if (selected) Color.White else Color(0xFF55C7FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF2D9FE0),
            containerColor = Color(0xDD020B12),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color(0xFF23607B),
            selectedBorderColor = Color(0xFF55C7FF),
        ),
    )
}

@Composable
private fun FreeFlightHelpToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (expanded) Color(0xFF2D9FE0) else Color(0xDD020B12))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun FreeFlightHelpOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6041420))
            .border(BorderStroke(1.dp, Color(0xFF2D6D87)), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("FLIGHT CONTROLS", color = Color(0xFF55C7FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
        listOf(
            "Arrows - pitch / roll",
            "Z / X - rudder",
            "PgUp / PgDn - throttle",
            "F1-F4 - throttle presets",
            "F6 / F7 - flaps up / down",
            "B - brakes    E - engines",
            "C - camera view    P - pause",
            "1 - runway   2 - final   3 - cruise",
            "Drag - look around, scroll - zoom",
            "H - hide this panel",
        ).forEach { line ->
            Text(line, color = Color(0xFFD8E5F2), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
