package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
    var showHelp by remember { mutableStateOf(true) }
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
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
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
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 54.dp),
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

        // Instrument strip.
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FlightInstrument("IAS", "${telemetry.iasKnots.roundToInt()} kt")
            FlightInstrument("ALT", "${telemetry.altitudeFt.roundToInt()} ft")
            FlightInstrument("VS", "${(telemetry.verticalSpeedFpm / 10).roundToInt() * 10} fpm")
            FlightInstrument("HDG", "${telemetry.headingDeg.roundToInt() % 360}")
            FlightInstrument("PITCH", "${telemetry.pitchDeg.roundToInt()}")
            FlightInstrument("BANK", "${telemetry.bankDeg.roundToInt()}")
            FlightInstrument("TRQ", "${telemetry.torquePercent.roundToInt()}%")
            FlightInstrument("FLAP", "${telemetry.flapsDeg.roundToInt()}")
            FlightInstrument(if (telemetry.onGround) "GND" else "AIR", if (telemetry.onGround) "ON" else "${telemetry.groundSpeedKt.roundToInt()} GS")
        }

        // Throttle/controls state, bottom right.
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FlightInstrument("THR", "${(session.controls.throttle * 100).roundToInt()}%")
            if (session.controls.brakes) FreeFlightBadge("BRAKES", warn = true)
        }

        if (showHelp) {
            FreeFlightHelpOverlay(Modifier.align(Alignment.TopEnd).padding(12.dp))
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
private fun FlightInstrument(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xDD020B12))
            .border(BorderStroke(1.dp, Color(0xFF2D6D87)), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .widthIn(min = 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label $value",
            color = Color(0xFFD8E5F2),
            fontSize = 11.sp,
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
