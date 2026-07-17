package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jme3.app.SimpleApplication
import com.jme3.bounding.BoundingBox
import com.jme3.bounding.BoundingSphere
import com.jme3.material.Material
import com.jme3.math.ColorRGBA
import com.jme3.math.FastMath
import com.jme3.math.Vector3f
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.shape.Box as JmeBox
import com.jme3.scene.shape.Cylinder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/* =====================================================================
   Cockpit3dStage - real-time 3D Twin Otter simulator cockpit.

   Loads the converted Blender simulator model (twin_otter_cockpit.glb)
   into the shared flight-sim rendering kit: PBR light probe, shadows,
   SSAO, bloom and FXAA, plus an outdoor apron scene (sky dome, grass,
   runway strip) visible around the cockpit.

   The cockpit tab now uses the guaranteed Compose simulator canvas for the
   playable view, so a missing GLB or black native frame cannot hide the 3D
   section. The native scene specs remain below for future converted models.
   ===================================================================== */

internal const val TwinOtterCockpitModelPath = "assets/models/cockpit/twin_otter_cockpit.glb"

internal enum class CockpitObservationSeat(val label: String, val lateralOffset: Float) {
    LeftSeat("Left seat", -0.34f),
    RightSeat("Right seat", 0.34f),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Cockpit3dStage(
    variant: CockpitSpriteVariant,
    simView: Dhc6SimulatorView,
    observationSeat: CockpitObservationSeat,
    selectedTarget: CockpitHitboxTarget,
    state: DesktopCockpitSimState,
    onStateChange: (DesktopCockpitSimState) -> Unit,
    onSelectTargetId: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewYawDegrees by remember(simView, observationSeat) {
        mutableFloatStateOf(if (simView == Dhc6SimulatorView.OutsideAircraft) -28f else 0f)
    }
    var viewPitchDegrees by remember(simView, observationSeat) { mutableFloatStateOf(0f) }
    var viewZoom by remember(simView, observationSeat) { mutableFloatStateOf(1f) }
    var animTime by remember { mutableFloatStateOf(0f) }
    val engineOn = state.leftEngineRunning || state.rightEngineRunning
    LaunchedEffect(engineOn) {
        if (!engineOn) {
            animTime = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> animTime = (now - start) / 1_000_000_000f }
        }
    }
    val environmentPackage = remember {
        FsxEnvironmentLibrary.loadAuto()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF02070B))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(18.dp))
            .pointerInput(simView, viewYawDegrees, state) {
                detectTapGestures { tapOffset ->
                    val normalizedOffset = Offset(
                        x = tapOffset.x / size.width.toFloat().coerceAtLeast(1f),
                        y = tapOffset.y / size.height.toFloat().coerceAtLeast(1f),
                    )
                    cockpit3dControlAt(normalizedOffset, simView, viewYawDegrees)?.let { control ->
                        onSelectTargetId(control.targetId)
                        onStateChange(control.update(state))
                    }
                }
            }
            .pointerInput(simView) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    viewYawDegrees = (viewYawDegrees + dragAmount.x * 0.24f).normalizedHeadingDegrees()
                    viewPitchDegrees = (viewPitchDegrees - dragAmount.y * 0.12f).coerceIn(-58f, 58f)
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) {
                    viewZoom = (viewZoom + delta * 0.08f).coerceIn(0.70f, 2.35f)
                }
            },
    ) {
        Cockpit3dPilotView(
            variant = variant,
            simView = simView,
            observationSeat = observationSeat,
            selectedTarget = selectedTarget,
            state = state,
            environmentPackage = environmentPackage,
            yawDegrees = viewYawDegrees,
            pitchDegrees = viewPitchDegrees,
            viewZoom = viewZoom,
            time = animTime,
            modifier = Modifier.fillMaxSize(),
        )
        Cockpit3dBadge(
            text = cockpit3dBadgeText(
                variant = variant,
                simView = simView,
                observationSeat = observationSeat,
                yawDegrees = viewYawDegrees,
                zoom = viewZoom,
                environmentPackage = environmentPackage,
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
    }
}

private fun cockpit3dBadgeText(
    variant: CockpitSpriteVariant,
    simView: Dhc6SimulatorView,
    observationSeat: CockpitObservationSeat,
    yawDegrees: Float,
    zoom: Float,
    environmentPackage: FsxEnvironmentPackage,
): String {
    val viewLabel = if (simView == Dhc6SimulatorView.InsideCockpit) observationSeat.label else "Exterior orbit"
    val environmentLabel = if (simView == Dhc6SimulatorView.InsideCockpit) {
        if (environmentPackage.isAvailable) environmentPackage.statusBadge else "Pilot-eye env"
    } else {
        environmentPackage.statusBadge
    }
    return "360 sim  -  $viewLabel  -  HDG ${yawDegrees.normalizedHeadingDegrees().roundToInt()}  -  $environmentLabel"
}

@Composable
private fun Cockpit3dPilotView(
    variant: CockpitSpriteVariant,
    simView: Dhc6SimulatorView,
    observationSeat: CockpitObservationSeat,
    selectedTarget: CockpitHitboxTarget,
    state: DesktopCockpitSimState,
    environmentPackage: FsxEnvironmentPackage,
    yawDegrees: Float,
    pitchDegrees: Float,
    viewZoom: Float,
    time: Float,
    modifier: Modifier = Modifier,
) {
    val insideCockpitImage = remember {
        DesktopImages.image(PersonalizedDhc6InsideCockpitPath)
    }
    val outsideAircraftImage = remember {
        DesktopImages.image(PersonalizedDhc6OutsideAircraftPath)
    }
    Canvas(modifier) {
        drawPilotPanoramaWorld(
            simView = simView,
            state = state,
            environmentPackage = environmentPackage,
            yawDegrees = yawDegrees,
            pitchDegrees = pitchDegrees,
            time = time,
        )
        if (simView == Dhc6SimulatorView.OutsideAircraft) {
            drawPilotAircraftExterior(outsideAircraftImage, state)
            drawPilotExteriorHud(state)
        } else {
            drawPilotSeatView(
                variant = variant,
                cockpitImage = insideCockpitImage,
                state = state,
                observationSeat = observationSeat,
                selectedTarget = selectedTarget,
                yawDegrees = yawDegrees,
                pitchDegrees = pitchDegrees,
                zoom = viewZoom,
            )
        }
        drawPilotViewVignette()
    }
}

private data class Cockpit3dControlZone(
    val label: String,
    val targetId: String,
    val rect: Rect,
    val update: (DesktopCockpitSimState) -> DesktopCockpitSimState,
)

private val Cockpit3dForwardControlZones = listOf(
    Cockpit3dControlZone("Battery", "electrical-panel", Rect(0.700f, 0.610f, 0.735f, 0.735f)) {
        it.copy(batteryMaster = !it.batteryMaster)
    },
    Cockpit3dControlZone("Avionics", "electrical-panel", Rect(0.738f, 0.610f, 0.775f, 0.735f)) {
        it.copy(avionicsMaster = !it.avionicsMaster)
    },
    Cockpit3dControlZone("L gen", "electrical-panel", Rect(0.778f, 0.610f, 0.815f, 0.735f)) {
        it.copy(leftDcGenerator = !it.leftDcGenerator)
    },
    Cockpit3dControlZone("R gen", "electrical-panel", Rect(0.818f, 0.610f, 0.855f, 0.735f)) {
        it.copy(rightDcGenerator = !it.rightDcGenerator)
    },
    Cockpit3dControlZone("Fwd boost", "fuel-panel", Rect(0.395f, 0.560f, 0.455f, 0.655f)) {
        it.copy(fwdBoost1 = !it.fwdBoost1, fwdBoost2 = !it.fwdBoost2)
    },
    Cockpit3dControlZone("Aft boost", "fuel-panel", Rect(0.458f, 0.560f, 0.515f, 0.655f)) {
        it.copy(aftBoost1 = !it.aftBoost1, aftBoost2 = !it.aftBoost2)
    },
    Cockpit3dControlZone("Crossfeed", "fuel-panel", Rect(0.540f, 0.545f, 0.602f, 0.650f)) {
        it.copy(crossfeed = it.crossfeed.next())
    },
    Cockpit3dControlZone("L fire", "fire-panel", Rect(0.420f, 0.665f, 0.468f, 0.775f)) {
        val pulled = !it.leftFireHandlePulled
        it.copy(leftFireHandlePulled = pulled, leftFuelLeverOn = if (pulled) false else it.leftFuelLeverOn)
    },
    Cockpit3dControlZone("R fire", "fire-panel", Rect(0.472f, 0.665f, 0.520f, 0.775f)) {
        val pulled = !it.rightFireHandlePulled
        it.copy(rightFireHandlePulled = pulled, rightFuelLeverOn = if (pulled) false else it.rightFuelLeverOn)
    },
    Cockpit3dControlZone("L power", "power-levers", Rect(0.440f, 0.730f, 0.500f, 0.945f)) {
        it.copy(leftPower = it.leftPower.next())
    },
    Cockpit3dControlZone("R power", "power-levers", Rect(0.502f, 0.730f, 0.560f, 0.945f)) {
        it.copy(rightPower = it.rightPower.next())
    },
    Cockpit3dControlZone("L fuel", "fuel-panel", Rect(0.580f, 0.740f, 0.630f, 0.935f)) {
        it.copy(leftFuelLeverOn = !it.leftFuelLeverOn)
    },
    Cockpit3dControlZone("R fuel", "fuel-panel", Rect(0.632f, 0.740f, 0.682f, 0.935f)) {
        it.copy(rightFuelLeverOn = !it.rightFuelLeverOn)
    },
    Cockpit3dControlZone("Flaps", "flaps-hydraulic", Rect(0.690f, 0.695f, 0.755f, 0.900f)) {
        it.copy(flaps = it.flaps.next())
    },
)

private fun cockpit3dControlAt(
    normalizedOffset: Offset,
    simView: Dhc6SimulatorView,
    yawDegrees: Float,
): Cockpit3dControlZone? {
    if (simView != Dhc6SimulatorView.InsideCockpit) return null
    if (abs(yawDegrees.signedHeadingDegrees()) > 78f) return null
    return Cockpit3dForwardControlZones.asReversed().firstOrNull { it.rect.contains(normalizedOffset) }
}

private fun DrawScope.drawPilotPanoramaWorld(
    simView: Dhc6SimulatorView,
    state: DesktopCockpitSimState,
    environmentPackage: FsxEnvironmentPackage,
    yawDegrees: Float,
    pitchDegrees: Float,
    time: Float,
) {
    if (simView == Dhc6SimulatorView.OutsideAircraft &&
        drawPilotHdEnvironment(environmentPackage, yawDegrees, pitchDegrees)
    ) {
        return
    }

    val w = size.width
    val h = size.height
    val motion = ((state.torquePercent(true) + state.torquePercent(false)) / 200f).coerceIn(0f, 1f)
    val horizonY = (h * (0.44f + pitchDegrees / 145f - motion * 0.020f)).coerceIn(h * 0.18f, h * 0.78f)

    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF0D4F96),
                0.50f to Color(0xFF3795D9),
                0.88f to Color(0xFFB8DAED),
                1.00f to Color(0xFFE9F4FA),
            ),
            endY = horizonY,
        ),
        size = Size(w, horizonY),
    )

    val sunX = xForHeading(targetHeading = 42f, currentHeading = yawDegrees, width = w)
    if (sunX != null) {
        val sun = Offset(sunX, horizonY * 0.20f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xAAFFF2CC), Color(0x33FFE7A0), Color.Transparent),
                center = sun,
                radius = w * 0.13f,
            ),
            radius = w * 0.13f,
            center = sun,
        )
        drawCircle(Color(0xFFFFF7DF), radius = w * 0.020f, center = sun)
    }

    listOf(12f, 76f, 145f, 220f, 306f).forEachIndexed { index, heading ->
        xForHeading(heading + time * (1.5f + index * 0.3f), yawDegrees, w)?.let { cloudX ->
            drawPilotCloud(cloudX, horizonY * (0.25f + (index % 3) * 0.13f), w * (0.040f + index % 2 * 0.016f), 0.72f)
        }
    }

    val groundTop = horizonY
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF6A9750), Color(0xFF426C34), Color(0xFF263D24)),
            startY = groundTop,
            endY = h,
        ),
        topLeft = Offset(0f, groundTop),
        size = Size(w, h - groundTop),
    )

    if (environmentPackage.isAvailable) {
        drawPilotTerrainTexture(yawDegrees, horizonY)
    }
    drawPilotDirectionalRidge(yawDegrees, horizonY)
    if (environmentPackage.isAvailable) {
        drawPilotAutogenStrip(yawDegrees, horizonY)
    }

    val runwayDelta = angularDeltaDegrees(0f, yawDegrees)
    if (abs(runwayDelta) < 76f) {
        drawPilotRunwayPerspective(horizonY, runwayDelta / 76f)
    }

    val rampDelta = angularDeltaDegrees(180f, yawDegrees)
    if (abs(rampDelta) < 88f) {
        drawPilotRampAndHangars(horizonY, rampDelta / 88f)
    }
}

private fun DrawScope.drawPilotTerrainTexture(yawDegrees: Float, horizonY: Float) {
    val w = size.width
    val h = size.height
    val groundBottom = h
    val groundHeight = (groundBottom - horizonY).coerceAtLeast(1f)

    repeat(8) { row ->
        val t = (row + 1) / 9f
        val y = horizonY + groundHeight * t
        val alpha = 0.10f + t * 0.12f
        drawLine(
            color = Color(0xFFB5C98A).copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(w, y + groundHeight * 0.018f),
            strokeWidth = 1.2f + t * 2.2f,
        )
    }

    listOf(35f, 116f, 214f, 306f).forEachIndexed { index, heading ->
        xForHeading(heading, yawDegrees, w)?.let { fieldX ->
            val nearWidth = w * (0.18f + (index % 2) * 0.04f)
            val farWidth = w * 0.035f
            val topY = horizonY + groundHeight * (0.10f + (index % 3) * 0.035f)
            val bottomY = groundBottom
            val offset = angularDeltaDegrees(heading, yawDegrees) / 102f
            val path = Path().apply {
                moveTo(fieldX - farWidth, topY)
                lineTo(fieldX + farWidth, topY)
                lineTo(fieldX + nearWidth - offset * w * 0.16f, bottomY)
                lineTo(fieldX - nearWidth - offset * w * 0.16f, bottomY)
                close()
            }
            drawPath(
                path,
                Color(
                    if (index % 2 == 0) 0xFF5D8C46 else 0xFF4F7D3E,
                ).copy(alpha = 0.22f),
            )
        }
    }

    listOf(62f, 158f, 258f).forEach { heading ->
        xForHeading(heading, yawDegrees, w)?.let { roadX ->
            val delta = angularDeltaDegrees(heading, yawDegrees) / 102f
            val road = Path().apply {
                moveTo(roadX - w * 0.010f, horizonY + groundHeight * 0.18f)
                lineTo(roadX + w * 0.010f, horizonY + groundHeight * 0.18f)
                lineTo(roadX + w * (0.055f - delta * 0.10f), h)
                lineTo(roadX - w * (0.055f + delta * 0.10f), h)
                close()
            }
            drawPath(road, Color(0xFF3E4840).copy(alpha = 0.34f))
            drawPath(road, Color(0xFFB7B7A0).copy(alpha = 0.18f), style = Stroke(width = 1.4f))
        }
    }
}

private fun DrawScope.drawPilotHdEnvironment(
    environmentPackage: FsxEnvironmentPackage,
    yawDegrees: Float,
    pitchDegrees: Float,
): Boolean {
    val images = environmentPackage.previewImages.ifEmpty {
        environmentPackage.previewImage?.let { listOf(it) }.orEmpty()
    }
    if (images.isEmpty()) return false

    val heading = yawDegrees.normalizedHeadingDegrees()
    val segment = 360f / images.size.coerceAtLeast(1)
    val position = heading / segment
    val index = floor(position).toInt().floorMod(images.size)
    val withinSegment = position - floor(position)
    val image = images[index]
    val panX = (0.5f - withinSegment) * size.width * 0.42f
    val panY = (pitchDegrees / 58f).coerceIn(-1f, 1f) * size.height * 0.18f

    drawPilotImageCover(
        image = image,
        zoom = 1.16f,
        panX = panX,
        panY = panY,
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x22000000), Color.Transparent, Color(0x33000000)),
            startY = 0f,
            endY = size.height,
        ),
        size = size,
    )
    return true
}

private fun DrawScope.drawPilotSeatView(
    variant: CockpitSpriteVariant,
    cockpitImage: ImageBitmap?,
    state: DesktopCockpitSimState,
    observationSeat: CockpitObservationSeat,
    selectedTarget: CockpitHitboxTarget,
    yawDegrees: Float,
    pitchDegrees: Float,
    zoom: Float,
) {
    val signedYaw = yawDegrees.signedHeadingDegrees()
    val forwardAmount = (1f - abs(signedYaw) / 86f).coerceIn(0f, 1f)
    if (forwardAmount > 0f) {
        if (cockpitImage != null) {
            val seatShift = observationSeat.lateralOffset * size.width * 0.16f
            drawPilotImageCover(
                image = cockpitImage,
                zoom = zoom.coerceIn(0.86f, 2.20f),
                panX = seatShift - signedYaw / 86f * size.width * 0.34f,
                panY = pitchDegrees / 58f * size.height * 0.20f,
            )
        } else {
            drawPilotPropDiscs(state)
            drawPilotWindshield()
            drawPilotTrainerPanel(state)
        }
        drawPilotPanelStateGlow(state)
        drawPilot3dControlHotspots(selectedTarget.id, state, forwardAmount)
        return
    }

    val rearAmount = (abs(signedYaw) - 122f) / 58f
    if (rearAmount > 0f) {
        drawPilotRearCabinView(state, variant, rearAmount.coerceIn(0f, 1f))
    } else {
        drawPilotSideWindowView(observationSeat, signedYaw)
    }
}

private fun DrawScope.drawPilotPanelStateGlow(state: DesktopCockpitSimState) {
    if (!state.electricalBusPowered && !state.leftEngineFire && !state.rightEngineFire) return
    val color = when {
        state.leftEngineFire || state.rightEngineFire -> Color(0xFFFF4C4C)
        state.avionicsPowered -> Color(0xFF6BE675)
        else -> Color(0xFFFFC75A)
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * 0.50f, size.height * 0.70f),
            radius = size.width * 0.38f,
        ),
        center = Offset(size.width * 0.50f, size.height * 0.70f),
        radius = size.width * 0.38f,
    )
}

private fun DrawScope.drawPilot3dControlHotspots(
    selectedTargetId: String,
    state: DesktopCockpitSimState,
    alpha: Float,
) {
    Cockpit3dForwardControlZones.forEach { zone ->
        val rect = zone.rect.toViewportRect(size)
        val active = zone.isActiveIn(state)
        val selected = zone.targetId == selectedTargetId
        if (!active && !selected) return@forEach
        val color = when {
            zone.targetId == "fire-panel" && active -> Color(0xFFFF5C5C)
            active -> Color(0xFF6BE675)
            else -> Color(0xFF55C7FF)
        }.copy(alpha = alpha)
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(12f, 12f),
        )
        drawRoundRect(
            color = color.copy(alpha = 0.95f),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = if (selected) 3.0f else 2.0f),
        )
    }
}

private fun Cockpit3dControlZone.isActiveIn(state: DesktopCockpitSimState): Boolean = when (label) {
    "Battery" -> state.batteryMaster
    "Avionics" -> state.avionicsMaster
    "L gen" -> state.leftDcGenerator
    "R gen" -> state.rightDcGenerator
    "Fwd boost" -> state.fwdBoost1 || state.fwdBoost2
    "Aft boost" -> state.aftBoost1 || state.aftBoost2
    "Crossfeed" -> state.crossfeed != CockpitCrossfeedPosition.NORMAL
    "L fire" -> state.leftFireHandlePulled || state.leftEngineFire
    "R fire" -> state.rightFireHandlePulled || state.rightEngineFire
    "L power" -> state.leftPower != CockpitPowerLeverPosition.IDLE
    "R power" -> state.rightPower != CockpitPowerLeverPosition.IDLE
    "L fuel" -> state.leftFuelLeverOn
    "R fuel" -> state.rightFuelLeverOn
    "Flaps" -> state.flaps != CockpitFlapSetting.UP
    else -> false
}

private fun DrawScope.drawPilotSideWindowView(
    observationSeat: CockpitObservationSeat,
    signedYaw: Float,
) {
    val w = size.width
    val h = size.height
    val sideIsRight = signedYaw > 0f
    val seatColor = if (observationSeat == CockpitObservationSeat.LeftSeat) Color(0xFF4B6672) else Color(0xFF51616B)
    val windowRect = if (sideIsRight) {
        Rect(w * 0.42f, h * 0.10f, w * 0.94f, h * 0.64f)
    } else {
        Rect(w * 0.06f, h * 0.10f, w * 0.58f, h * 0.64f)
    }
    val cockpitShell = Color(0xCC050A0E)
    drawRect(cockpitShell, topLeft = Offset(0f, 0f), size = Size(w, windowRect.top))
    drawRect(cockpitShell, topLeft = Offset(0f, windowRect.bottom), size = Size(w, h - windowRect.bottom))
    drawRect(cockpitShell, topLeft = Offset(0f, windowRect.top), size = Size(windowRect.left, windowRect.height))
    drawRect(cockpitShell, topLeft = Offset(windowRect.right, windowRect.top), size = Size(w - windowRect.right, windowRect.height))
    drawRoundRect(
        color = Color(0xFF26353D),
        topLeft = windowRect.topLeft,
        size = windowRect.size,
        cornerRadius = CornerRadius(22f, 22f),
        style = Stroke(width = 12f),
    )
    drawRoundRect(
        color = Color(0x20BFEAFF),
        topLeft = windowRect.topLeft,
        size = windowRect.size,
        cornerRadius = CornerRadius(22f, 22f),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF2D3C43), Color(0xFF11191E))),
        topLeft = Offset(w * 0.18f, h * 0.62f),
        size = Size(w * 0.64f, h * 0.35f),
        cornerRadius = CornerRadius(26f, 26f),
    )
    drawRoundRect(
        color = seatColor,
        topLeft = Offset(if (sideIsRight) w * 0.17f else w * 0.61f, h * 0.52f),
        size = Size(w * 0.18f, h * 0.42f),
        cornerRadius = CornerRadius(22f, 22f),
    )
    drawLine(Color(0xFF405761), Offset(w * 0.50f, h * 0.08f), Offset(w * 0.50f, h * 0.84f), 10f)
}

private fun DrawScope.drawPilotRearCabinView(
    state: DesktopCockpitSimState,
    variant: CockpitSpriteVariant,
    amount: Float,
) {
    val w = size.width
    val h = size.height
    drawRect(Color(0x6603080C), size = size)
    listOf(
        Rect(w * 0.055f, h * 0.18f, w * 0.230f, h * 0.50f),
        Rect(w * 0.770f, h * 0.18f, w * 0.945f, h * 0.50f),
    ).forEach { window ->
        drawRoundRect(
            color = Color(0xAA081017),
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(18f, 18f),
        )
        drawRoundRect(
            color = Color(0x28BFEAFF),
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(18f, 18f),
        )
        drawRoundRect(
            color = Color(0xFF26353D),
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 7f),
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF455761), Color(0xFF1B252B))),
        topLeft = Offset(w * 0.16f, h * 0.10f),
        size = Size(w * 0.68f, h * 0.74f),
        cornerRadius = CornerRadius(34f, 34f),
    )
    drawRoundRect(
        color = Color(0xFF0C1216),
        topLeft = Offset(w * 0.34f, h * 0.18f),
        size = Size(w * 0.32f, h * 0.34f),
        cornerRadius = CornerRadius(16f, 16f),
    )
    val seatTint = if (variant == CockpitSpriteVariant.G950) Color(0xFF53646D) else Color(0xFF4F6874)
    listOf(w * 0.24f, w * 0.58f).forEach { left ->
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(seatTint, Color(0xFF253139))),
            topLeft = Offset(left, h * (0.45f - amount * 0.04f)),
            size = Size(w * 0.18f, h * 0.34f),
            cornerRadius = CornerRadius(20f, 20f),
        )
    }
    if (state.electricalBusPowered) {
        drawCircle(Color(0x22FFC75A), radius = w * 0.23f, center = Offset(w * 0.50f, h * 0.28f))
    }
}

private fun DrawScope.drawPilotDirectionalRidge(yawDegrees: Float, horizonY: Float) {
    val w = size.width
    val h = size.height
    val mountainHeadings = listOf(270f, 302f, 330f, 22f, 55f)
    mountainHeadings.forEachIndexed { index, heading ->
        xForHeading(heading, yawDegrees, w)?.let { peakX ->
            val width = w * (0.18f + (index % 2) * 0.05f)
            val peakY = horizonY - h * (0.040f + index % 3 * 0.018f)
            val path = Path().apply {
                moveTo(peakX - width, horizonY + h * 0.070f)
                lineTo(peakX, peakY)
                lineTo(peakX + width, horizonY + h * 0.080f)
                close()
            }
            drawPath(path, Color(0xFF718EA0).copy(alpha = 0.82f))
        }
    }
}

private fun DrawScope.drawPilotAutogenStrip(yawDegrees: Float, horizonY: Float) {
    val w = size.width
    val h = size.height
    listOf(78f, 112f, 148f, 202f).forEachIndexed { group, heading ->
        xForHeading(heading, yawDegrees, w)?.let { groupX ->
            repeat(7) { index ->
                val bh = h * (0.035f + (index % 3) * 0.014f)
                val bw = w * 0.012f
                val x = groupX + (index - 3) * bw * 1.55f
                val y = horizonY + h * 0.030f - bh
                drawRect(
                    color = Color(0xFF2F3E44).copy(alpha = 0.70f),
                    topLeft = Offset(x, y),
                    size = Size(bw, bh),
                )
                if ((index + group) % 2 == 0) {
                    drawRect(
                        color = Color(0xFFFFD27A).copy(alpha = 0.55f),
                        topLeft = Offset(x + bw * 0.28f, y + bh * 0.28f),
                        size = Size(bw * 0.28f, bh * 0.16f),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPilotRunwayPerspective(horizonY: Float, lateral: Float) {
    val w = size.width
    val h = size.height
    val centerTop = w * (0.50f - lateral * 0.33f)
    val centerBottom = w * (0.50f - lateral * 0.82f)
    val topY = horizonY + h * 0.045f
    val bottomY = h * 0.94f
    val topHalf = w * 0.030f
    val bottomHalf = w * (0.280f + abs(lateral) * 0.08f)
    val runway = Path().apply {
        moveTo(centerTop - topHalf, topY)
        lineTo(centerTop + topHalf, topY)
        lineTo(centerBottom + bottomHalf, bottomY)
        lineTo(centerBottom - bottomHalf, bottomY)
        close()
    }
    drawPath(
        runway,
        Brush.verticalGradient(listOf(Color(0xFF62676A), Color(0xFF313538), Color(0xFF202326)), topY, bottomY),
    )
    drawLine(Color.White.copy(alpha = 0.82f), Offset(centerTop - topHalf, topY), Offset(centerBottom - bottomHalf, bottomY), 3f)
    drawLine(Color.White.copy(alpha = 0.82f), Offset(centerTop + topHalf, topY), Offset(centerBottom + bottomHalf, bottomY), 3f)
    repeat(8) { index ->
        val t = 0.12f + index * 0.105f
        val y = topY + (bottomY - topY) * t
        val x = centerTop + (centerBottom - centerTop) * t
        drawLine(
            color = Color.White.copy(alpha = 0.86f),
            start = Offset(x, y),
            end = Offset(x, y + h * 0.032f * (0.4f + t)),
            strokeWidth = 2.4f + index * 0.38f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPilotRampAndHangars(horizonY: Float, lateral: Float) {
    val w = size.width
    val h = size.height
    val baseX = w * (0.50f - lateral * 0.52f)
    val y = horizonY + h * 0.12f
    drawRect(
        color = Color(0xFF4C4F50),
        topLeft = Offset(baseX - w * 0.26f, y),
        size = Size(w * 0.52f, h * 0.18f),
    )
    repeat(3) { index ->
        val left = baseX - w * 0.22f + index * w * 0.15f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF7D878A), Color(0xFF394146))),
            topLeft = Offset(left, y - h * 0.10f),
            size = Size(w * 0.12f, h * 0.10f),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }
}

private fun Rect.toViewportRect(size: Size): Rect = Rect(
    left = left * size.width,
    top = top * size.height,
    right = right * size.width,
    bottom = bottom * size.height,
)

private fun Float.normalizedHeadingDegrees(): Float {
    val wrapped = this % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

private fun Float.signedHeadingDegrees(): Float {
    val heading = normalizedHeadingDegrees()
    return if (heading > 180f) heading - 360f else heading
}

private fun angularDeltaDegrees(targetHeading: Float, currentHeading: Float): Float {
    val raw = (targetHeading.normalizedHeadingDegrees() - currentHeading.normalizedHeadingDegrees() + 540f) % 360f
    return raw - 180f
}

private fun xForHeading(targetHeading: Float, currentHeading: Float, width: Float): Float? {
    val delta = angularDeltaDegrees(targetHeading, currentHeading)
    if (abs(delta) > 102f) return null
    return width * 0.5f + (delta / 102f) * width * 0.67f
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private fun DrawScope.drawPilotOutsideWorld(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    val motion = ((state.torquePercent(true) + state.torquePercent(false)) / 200f).coerceIn(0f, 1f)
    val horizonY = h * (0.405f - motion * 0.018f)

    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF155AA0),
                0.55f to Color(0xFF3D9BDA),
                0.88f to Color(0xFFB6D7EA),
                1.00f to Color(0xFFE6F2F8),
            ),
            endY = horizonY,
        ),
        size = Size(w, horizonY),
    )

    val sun = Offset(w * 0.80f, h * 0.080f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xAAFFF3D0), Color(0x33FFE9B0), Color.Transparent),
            center = sun,
            radius = w * 0.13f,
        ),
        center = sun,
        radius = w * 0.13f,
    )
    drawCircle(Color(0xFFFFF6DE), radius = w * 0.020f, center = sun)

    drawPilotCloud(w * 0.13f, h * 0.13f, w * 0.070f, 0.82f)
    drawPilotCloud(w * 0.42f, h * 0.18f, w * 0.055f, 0.58f)
    drawPilotCloud(w * 0.72f, h * 0.16f, w * 0.060f, 0.68f)
    drawPilotCloud(w * 0.92f, h * 0.22f, w * 0.052f, 0.48f)

    val farRidge = Path().apply {
        moveTo(0f, horizonY + h * 0.025f)
        lineTo(w * 0.11f, horizonY - h * 0.040f)
        lineTo(w * 0.25f, horizonY + h * 0.010f)
        lineTo(w * 0.39f, horizonY - h * 0.060f)
        lineTo(w * 0.55f, horizonY + h * 0.012f)
        lineTo(w * 0.71f, horizonY - h * 0.050f)
        lineTo(w * 0.88f, horizonY + h * 0.010f)
        lineTo(w, horizonY - h * 0.020f)
        lineTo(w, horizonY + h * 0.085f)
        lineTo(0f, horizonY + h * 0.085f)
        close()
    }
    drawPath(farRidge, Color(0xFF88A3B5))

    val nearRidge = Path().apply {
        moveTo(0f, horizonY + h * 0.055f)
        lineTo(w * 0.14f, horizonY - h * 0.018f)
        lineTo(w * 0.29f, horizonY + h * 0.048f)
        lineTo(w * 0.43f, horizonY - h * 0.032f)
        lineTo(w * 0.59f, horizonY + h * 0.042f)
        lineTo(w * 0.75f, horizonY - h * 0.026f)
        lineTo(w, horizonY + h * 0.030f)
        lineTo(w, horizonY + h * 0.120f)
        lineTo(0f, horizonY + h * 0.120f)
        close()
    }
    drawPath(nearRidge, Color(0xFF5F7258))

    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF69994F), Color(0xFF416D35), Color(0xFF233C24)),
            startY = horizonY,
            endY = h * 0.85f,
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY),
    )

    drawPilotRunway(horizonY)
}

private fun DrawScope.drawPilotImageCover(
    image: ImageBitmap,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
) {
    val dstW = size.width.roundToInt().coerceAtLeast(1)
    val dstH = size.height.roundToInt().coerceAtLeast(1)
    val baseScale = max(dstW / image.width.toFloat(), dstH / image.height.toFloat())
    val drawScale = baseScale * zoom.coerceIn(1f, 3f)
    val imageW = (image.width * drawScale).roundToInt().coerceAtLeast(1)
    val imageH = (image.height * drawScale).roundToInt().coerceAtLeast(1)
    val maxPanX = max(0f, (imageW - dstW) / 2f)
    val maxPanY = max(0f, (imageH - dstH) / 2f)
    val clampedPanX = panX.coerceIn(-maxPanX, maxPanX)
    val clampedPanY = panY.coerceIn(-maxPanY, maxPanY)
    val dstX = ((dstW - imageW) / 2f + clampedPanX).roundToInt()
    val dstY = ((dstH - imageH) / 2f + clampedPanY).roundToInt()

    drawImage(
        image = image,
        dstOffset = IntOffset(dstX, dstY),
        dstSize = IntSize(imageW, imageH),
        filterQuality = FilterQuality.High,
    )
}

private fun DrawScope.drawPilotExteriorHud(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    val hud = androidx.compose.ui.geometry.Rect(w * 0.040f, h * 0.120f, w * 0.305f, h * 0.310f)
    drawRoundRect(
        color = Color(0xCC020B12),
        topLeft = hud.topLeft,
        size = hud.size,
        cornerRadius = CornerRadius(16f, 16f),
    )
    drawRoundRect(
        color = Color(0xFF55C7FF),
        topLeft = hud.topLeft,
        size = hud.size,
        cornerRadius = CornerRadius(16f, 16f),
        style = Stroke(width = 2.3f),
    )
    val lTorque = state.torquePercent(true) / 100f
    val rTorque = state.torquePercent(false) / 100f
    listOf(lTorque, rTorque).forEachIndexed { index, value ->
        val left = hud.left + hud.width * (0.13f + index * 0.43f)
        val top = hud.top + hud.height * 0.58f
        val barW = hud.width * 0.30f
        val barH = hud.height * 0.14f
        drawRoundRect(Color(0xFF152733), Offset(left, top), Size(barW, barH), CornerRadius(5f, 5f))
        drawRoundRect(
            color = Color(0xFF6BE675),
            topLeft = Offset(left, top),
            size = Size(barW * value.coerceIn(0f, 1f), barH),
            cornerRadius = CornerRadius(5f, 5f),
        )
    }
}

private fun DrawScope.drawPilotCloud(cx: Float, cy: Float, s: Float, alpha: Float) {
    val shade = Color(0xFFBDD4E4).copy(alpha = 0.35f * alpha)
    val cloud = Color.White.copy(alpha = 0.55f * alpha)
    drawOval(shade, Offset(cx - s * 1.1f, cy - s * 0.12f), Size(s * 2.2f, s * 0.55f))
    drawOval(cloud, Offset(cx - s, cy - s * 0.30f), Size(s * 2f, s * 0.62f))
    drawOval(cloud, Offset(cx - s * 0.48f, cy - s * 0.55f), Size(s * 0.95f, s * 0.72f))
    drawOval(cloud, Offset(cx + s * 0.05f, cy - s * 0.42f), Size(s * 0.78f, s * 0.58f))
}

private fun DrawScope.drawPilotRunway(horizonY: Float) {
    val w = size.width
    val h = size.height
    val topY = horizonY + h * 0.052f
    val bottomY = h * 0.91f

    fun leftX(t: Float) = w * (0.468f + (0.218f - 0.468f) * t)
    fun rightX(t: Float) = w * (0.532f + (0.782f - 0.532f) * t)
    fun y(t: Float) = topY + (bottomY - topY) * t

    val runway = Path().apply {
        moveTo(leftX(0f), y(0f))
        lineTo(rightX(0f), y(0f))
        lineTo(rightX(1f), y(1f))
        lineTo(leftX(1f), y(1f))
        close()
    }
    drawPath(
        runway,
        Brush.verticalGradient(
            listOf(Color(0xFF565C61), Color(0xFF363A3D), Color(0xFF25282B)),
            startY = topY,
            endY = bottomY,
        ),
    )
    drawLine(Color.White.copy(alpha = 0.82f), Offset(leftX(0f), y(0f)), Offset(leftX(1f), y(1f)), 3f)
    drawLine(Color.White.copy(alpha = 0.82f), Offset(rightX(0f), y(0f)), Offset(rightX(1f), y(1f)), 3f)
    repeat(7) { index ->
        val t = 0.12f + index * 0.125f
        drawLine(
            color = Color.White.copy(alpha = 0.86f),
            start = Offset(w * 0.5f, y(t)),
            end = Offset(w * 0.5f, y((t + 0.045f).coerceAtMost(1f))),
            strokeWidth = 3f + index * 0.6f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPilotAircraftExterior(
    aircraftSprite: ImageBitmap?,
    state: DesktopCockpitSimState,
) {
    if (aircraftSprite == null) return

    val w = size.width
    val h = size.height
    val engineMotion = ((state.torquePercent(true) + state.torquePercent(false)) / 200f).coerceIn(0f, 1f)
    val aircraftW = w * (0.360f + engineMotion * 0.040f)
    val aircraftH = aircraftW * aircraftSprite.height / aircraftSprite.width
    val left = w * 0.315f
    val top = h * 0.305f

    drawOval(
        color = Color(0x33000000),
        topLeft = Offset(left + aircraftW * 0.08f, top + aircraftH * 0.82f),
        size = Size(aircraftW * 0.70f, aircraftH * 0.18f),
    )
    drawImage(
        image = aircraftSprite,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(aircraftW.roundToInt().coerceAtLeast(1), aircraftH.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.High,
    )
}

private fun DrawScope.drawPilotPropDiscs(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    listOf(
        Triple(w * 0.125f, state.leftEngineRunning, state.torquePercent(true)),
        Triple(w * 0.875f, state.rightEngineRunning, state.torquePercent(false)),
    ).forEachIndexed { index, (cx, running, torque) ->
        if (!running) return@forEachIndexed
        val cy = h * 0.31f
        val radius = h * 0.17f
        drawCircle(Color(0x13E9F6FF), radius = radius, center = Offset(cx, cy))
        repeat(12) { blade ->
            val phase = blade / 12f
            val alpha = (0.06f + torque.coerceIn(0, 100) / 100f * 0.08f) * (0.65f + 0.35f * sin(phase * 6.28f + index))
            drawLine(
                Color.White.copy(alpha = alpha),
                Offset(cx, cy - radius * 0.95f),
                Offset(cx, cy + radius * 0.95f),
                strokeWidth = 1.6f,
            )
        }
    }
}

private fun DrawScope.drawPilotWindshield() {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.linearGradient(
            listOf(Color(0x10FFFFFF), Color.Transparent, Color(0x14000000)),
            start = Offset(w * 0.2f, 0f),
            end = Offset(w * 0.8f, h * 0.55f),
        ),
    )

    val leftWall = Path().apply {
        moveTo(0f, h * 0.02f)
        lineTo(w * 0.105f, h * 0.08f)
        lineTo(w * 0.185f, h * 0.48f)
        lineTo(w * 0.055f, h * 0.68f)
        lineTo(0f, h * 0.62f)
        close()
    }
    val rightWall = Path().apply {
        moveTo(w, h * 0.02f)
        lineTo(w * 0.895f, h * 0.08f)
        lineTo(w * 0.815f, h * 0.48f)
        lineTo(w * 0.945f, h * 0.68f)
        lineTo(w, h * 0.62f)
        close()
    }
    val centerPost = Path().apply {
        moveTo(w * 0.485f, 0f)
        lineTo(w * 0.515f, 0f)
        lineTo(w * 0.525f, h * 0.50f)
        lineTo(w * 0.475f, h * 0.50f)
        close()
    }
    val frame = Color(0xEE070C10)
    drawPath(leftWall, frame)
    drawPath(rightWall, frame)
    drawPath(centerPost, frame)
    drawLine(Color(0xFF1E2A31), Offset(w * 0.14f, h * 0.07f), Offset(w * 0.26f, h * 0.50f), 9f)
    drawLine(Color(0xFF1E2A31), Offset(w * 0.86f, h * 0.07f), Offset(w * 0.74f, h * 0.50f), 9f)
}

private fun DrawScope.drawPilotTrainerPanel(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    val panelTop = h * 0.43f

    val glare = Path().apply {
        moveTo(w * 0.070f, panelTop)
        cubicTo(w * 0.24f, h * 0.38f, w * 0.76f, h * 0.38f, w * 0.930f, panelTop)
        lineTo(w, h * 0.70f)
        lineTo(w, h)
        lineTo(0f, h)
        lineTo(0f, h * 0.70f)
        close()
    }
    drawPath(glare, Brush.verticalGradient(listOf(Color(0xFF202428), Color(0xFF06090C)), panelTop, h))

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF40464A), Color(0xFF15191D))),
        topLeft = Offset(w * 0.115f, h * 0.545f),
        size = Size(w * 0.770f, h * 0.230f),
        cornerRadius = CornerRadius(18f, 18f),
    )
    drawRoundRect(
        color = Color(0xFF0D1114),
        topLeft = Offset(w * 0.123f, h * 0.555f),
        size = Size(w * 0.754f, h * 0.210f),
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 3f),
    )

    if (state.electricalBusPowered) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x24FFCE8A), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.64f),
                radius = w * 0.42f,
            ),
            center = Offset(w * 0.5f, h * 0.64f),
            radius = w * 0.42f,
        )
    }
}

private fun DrawScope.drawPilotViewVignette() {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color(0x66000000)),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = w * 0.78f,
        ),
        size = Size(w, h),
    )
}

@Composable
private fun Cockpit3dBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .zIndex(20f)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xDD020B12))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFFD8E5F2),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/* ---- Scene spec -------------------------------------------------------- */

private fun cockpit3dSceneSpec(observationSeat: CockpitObservationSeat): JmeSceneSpec = JmeSceneSpec(
    initialYaw = 0.0f,
    initialPitch = 0.06f,
    initialDist = 1.15f,
    minDist = 0.25f,
    maxDist = 10f,
    autoRotate = false,
    lookAt = Vector3f(-0.18f, 0.28f, observationSeat.lateralOffset),
    eyeHeightBias = 0.24f,
    build = { app ->
        val keyLight = JmeFlightSimScene.installLightRig(app.rootNode)
        runCatching { app.rootNode.attachChild(JmeFlightSimScene.buildSkyDome(app.assetManager)) }
        runCatching { app.rootNode.attachChild(buildApron(app)) }
        runCatching { JmeFlightSimScene.installPostFx(app, keyLight) }

        val model = runCatching { app.assetManager.loadModel(TwinOtterCockpitModelPath) }.getOrNull()
        if (model != null) {
            prepareCockpitModel(app, model)
            model.shadowMode = RenderQueue.ShadowMode.CastAndReceive
            app.rootNode.attachChild(model)
        }
    },
)

/* ---- Model framing ------------------------------------------------ */

private fun prepareCockpitModel(app: SimpleApplication, model: Spatial) {
    model.setLocalTranslation(0f, 0f, 0f)
    model.updateModelBound()
    model.updateGeometricState()

    val bound = model.worldBound
    val center = bound?.center?.clone() ?: Vector3f.ZERO.clone()
    val radius = when (bound) {
        is BoundingBox -> max(max(bound.xExtent, bound.yExtent), bound.zExtent)
        is BoundingSphere -> bound.radius
        else -> 1f
    }.coerceAtLeast(0.05f)

    model.setLocalTranslation(center.negate())
    model.setLocalScale((2.1f / radius).coerceIn(0.01f, 30f))

    runCatching {
        model.depthFirstTraversal { spatial ->
            val geom = spatial as? Geometry ?: return@depthFirstTraversal
            if (geom.material == null) {
                geom.material = Material(app.assetManager, "Common/MatDefs/Light/PBRLighting.j3md").apply {
                    setColor("BaseColor", ColorRGBA(0.70f, 0.76f, 0.82f, 1f))
                    setFloat("Metallic", 0.35f)
                    setFloat("Roughness", 0.55f)
                }
            }
        }
    }

    model.updateModelBound()
    model.updateGeometricState()
}

/* ---- Outdoor apron -------------------------------------------------- */

private fun buildApron(app: SimpleApplication): Node {
    val apron = Node("CockpitApron")
    val floorY = -2.35f

    // Grass field
    apron.attachChild(
        Geometry("Grass", Cylinder(2, 48, 70f, 0.05f, true)).apply {
            material = Material(app.assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
                setBoolean("UseMaterialColors", true)
                setColor("Diffuse", ColorRGBA(0.16f, 0.26f, 0.13f, 1f))
                setColor("Ambient", ColorRGBA(0.08f, 0.13f, 0.07f, 1f))
                setColor("Specular", ColorRGBA.Black)
                setFloat("Shininess", 1f)
            }
            rotate(FastMath.HALF_PI, 0f, 0f)
            setLocalTranslation(0f, floorY - 0.05f, 0f)
            shadowMode = RenderQueue.ShadowMode.Receive
        },
    )

    // Runway strip running "ahead" of the cockpit
    apron.attachChild(
        Geometry("Runway", JmeBox(2.6f, 0.012f, 60f)).apply {
            material = Material(app.assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
                setBoolean("UseMaterialColors", true)
                setColor("Diffuse", ColorRGBA(0.16f, 0.17f, 0.18f, 1f))
                setColor("Ambient", ColorRGBA(0.08f, 0.085f, 0.09f, 1f))
                setColor("Specular", ColorRGBA(0.06f, 0.06f, 0.06f, 1f))
                setFloat("Shininess", 8f)
            }
            setLocalTranslation(0f, floorY, -25f)
            shadowMode = RenderQueue.ShadowMode.Receive
        },
    )

    // Centreline stripes
    for (i in 0 until 12) {
        apron.attachChild(
            Geometry("Stripe$i", JmeBox(0.09f, 0.013f, 1.4f)).apply {
                material = Material(app.assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
                    setBoolean("UseMaterialColors", true)
                    setColor("Diffuse", ColorRGBA(0.85f, 0.86f, 0.84f, 1f))
                    setColor("Ambient", ColorRGBA(0.42f, 0.43f, 0.42f, 1f))
                    setColor("Specular", ColorRGBA.Black)
                    setFloat("Shininess", 1f)
                }
                setLocalTranslation(0f, floorY + 0.004f, -6f - i * 4.6f)
                shadowMode = RenderQueue.ShadowMode.Off
            },
        )
    }
    return apron
}
