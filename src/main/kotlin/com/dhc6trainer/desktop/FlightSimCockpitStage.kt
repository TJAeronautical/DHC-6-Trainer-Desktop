package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class Dhc6SimulatorView(val label: String) {
    InsideCockpit("Inside cockpit"),
    OutsideAircraft("Outside aircraft"),
}

internal const val PersonalizedDhc6AircraftRootPath = "flight-sim/dhc6"
internal const val PersonalizedDhc6InsideCockpitPath = "$PersonalizedDhc6AircraftRootPath/Pics/cockpit01.jpg"
internal const val PersonalizedDhc6OutsideAircraftPath = "$PersonalizedDhc6AircraftRootPath/Pics/wheels01.jpg"
private const val CockpitSpritePackRootPath = "cockpit/sprite_pack"

@Composable
internal fun FlightSimCockpitStage(
    variant: CockpitSpriteVariant,
    simView: Dhc6SimulatorView,
    selectedTarget: CockpitHitboxTarget,
    state: DesktopCockpitSimState,
    onStateChange: (DesktopCockpitSimState) -> Unit,
    onSelectTargetId: (String) -> Unit,
    zoom: Float,
    panX: Float,
    panY: Float,
    onPan: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    showStageChrome: Boolean = true,
) {
    // Continuous animation clock - only runs while an engine is turning, so the
    // scene stays static (and cheap) in cold-and-dark states.
    val engineOn = state.leftEngineRunning || state.rightEngineRunning
    var animTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(engineOn) {
        if (!engineOn) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> animTime = (now - start) / 1_000_000_000f }
        }
    }
    val cockpitSpritePack = remember(variant) {
        CockpitSpritePackCatalog.load(variant)
    }
    val cleanCockpitImage = remember(cockpitSpritePack.basePath) {
        DesktopImages.image(cockpitSpritePack.basePath)
    }
    val cockpitSpriteImages = remember(cockpitSpritePack) {
        cockpitSpritePack.entries.associate { entry -> entry.path to DesktopImages.image(entry.path) }
    }
    val outsideAircraftImage = remember {
        DesktopImages.image(PersonalizedDhc6OutsideAircraftPath)
    }
    val environmentPackage = remember {
        FsxEnvironmentLibrary.loadAuto()
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF02070B))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(18.dp))
            .pointerInput(state, simView, cockpitSpritePack, sceneScaleForPointer(zoom), panX, panY) {
                detectTapGestures { tapOffset ->
                    val scale = sceneScaleForPointer(zoom)
                    val sceneOffset = tapOffset.toSceneOffset(
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        sceneScale = scale,
                        panX = panX,
                        panY = panY,
                    )
                    val normalizedOffset = sceneOffset.toNormalizedOffset(
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                    )
                    val spriteHitId = if (simView == Dhc6SimulatorView.InsideCockpit) {
                        cockpitSpritePack.hitTest(
                            sceneOffset = sceneOffset,
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                        )
                    } else {
                        null
                    }
                    if (spriteHitId != null) {
                        // Every switch/lever sprite with simulated state toggles
                        // directly; the rest select their panel group.
                        targetIdForSprite(spriteHitId)?.let(onSelectTargetId)
                        spriteControlAction(spriteHitId)?.let { action -> onStateChange(action(state)) }
                        return@detectTapGestures
                    }
                    val control = FlightSimControlZones.firstOrNull { it.rect.contains(normalizedOffset) }
                    if (control != null) {
                        onSelectTargetId(control.targetId)
                        onStateChange(control.update(state))
                    } else {
                        FlightSimTargetRects.entries
                            .firstOrNull { it.value.contains(normalizedOffset) }
                            ?.key
                            ?.let(onSelectTargetId)
                    }
                }
            }
            .pointerInput(zoom) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Pan lives inside the scaled layer, so convert the screen
                    // drag to scene units to keep the image under the cursor.
                    val scale = sceneScaleForPointer(zoom)
                    onPan(dragAmount.x / scale, dragAmount.y / scale)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val sceneScale = sceneScaleForPointer(zoom)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = sceneScale, scaleY = sceneScale)
                .offset { IntOffset(panX.roundToInt(), panY.roundToInt()) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawFlightSimScene(
                    variant = variant,
                    simView = simView,
                    state = state,
                    selectedTargetId = selectedTarget.id,
                    time = animTime,
                    cleanCockpitImage = cleanCockpitImage,
                    cockpitSpritePack = cockpitSpritePack,
                    cockpitSpriteImages = cockpitSpriteImages,
                    outsideAircraftImage = outsideAircraftImage,
                    environmentPackage = environmentPackage,
                )
            }
        }

        if (showStageChrome) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .zIndex(20f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlightSimStageBadge("Click controls or drag to pan")
                FlightSimStageBadge(simView.label)
                FlightSimStageBadge(variant.label)
                if (simView == Dhc6SimulatorView.OutsideAircraft || environmentPackage.isAvailable) {
                    FlightSimStageBadge(environmentPackage.statusBadge)
                }
            }

            FlightSimReadoutStrip(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .zIndex(20f),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .zIndex(20f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlightSimStageBadge(selectedTarget.title)
                FlightSimStageBadge("pan ${panX.roundToInt()}, ${panY.roundToInt()}")
            }
        }
    }
}

@Composable
private fun FlightSimReadoutStrip(
    state: DesktopCockpitSimState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlightSimReadout("L TRQ", "${state.torquePercent(leftSide = true)}%")
        FlightSimReadout("R TRQ", "${state.torquePercent(leftSide = false)}%")
        FlightSimReadout("HYD", "${state.hydraulicPressurePsi}")
        FlightSimReadout("FLAP", state.flaps.label)
        FlightSimReadout("BUS", if (state.electricalBusPowered) "ON" else "OFF")
    }
}

@Composable
private fun FlightSimReadout(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xDD020B12))
            .border(BorderStroke(1.dp, Color(0xFF2D6D87)), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .widthIn(min = 56.dp),
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
private fun FlightSimStageBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xDD020B12))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFFD8E5F2),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

private val FlightSimTargetRects = mapOf(
    "annunciators" to Rect(0.37f, 0.485f, 0.63f, 0.555f),
    "engine-instruments" to Rect(0.20f, 0.555f, 0.63f, 0.720f),
    "electrical-panel" to Rect(0.675f, 0.555f, 0.905f, 0.730f),
    "fire-panel" to Rect(0.405f, 0.675f, 0.525f, 0.760f),
    "power-levers" to Rect(0.445f, 0.720f, 0.575f, 0.940f),
    "fuel-panel" to Rect(0.535f, 0.760f, 0.700f, 0.965f),
    "flaps-hydraulic" to Rect(0.655f, 0.735f, 0.800f, 0.930f),
)

private data class FlightSimControlZone(
    val targetId: String,
    val rect: Rect,
    val spriteIds: Set<String> = emptySet(),
    val update: (DesktopCockpitSimState) -> DesktopCockpitSimState,
) {
    fun handlesSprite(spriteId: String): Boolean = spriteId in spriteIds
}

private val FlightSimControlZones = listOf(
    FlightSimControlZone("electrical-panel", Rect(0.705f, 0.585f, 0.742f, 0.705f), setOf("BATTERY_MASTER")) {
        it.copy(batteryMaster = !it.batteryMaster)
    },
    FlightSimControlZone("electrical-panel", Rect(0.742f, 0.585f, 0.780f, 0.705f), setOf("AVIONICS_MASTER")) {
        it.copy(avionicsMaster = !it.avionicsMaster)
    },
    FlightSimControlZone("electrical-panel", Rect(0.779f, 0.585f, 0.818f, 0.705f), setOf("L_DC_GEN")) {
        it.copy(leftDcGenerator = !it.leftDcGenerator)
    },
    FlightSimControlZone("electrical-panel", Rect(0.816f, 0.585f, 0.856f, 0.705f), setOf("R_DC_GEN")) {
        it.copy(rightDcGenerator = !it.rightDcGenerator)
    },
    FlightSimControlZone("fire-panel", Rect(0.425f, 0.675f, 0.462f, 0.765f), setOf("FIRE_HANDLE_L", "FIRE_PUSH_SWITCH_L")) {
        val nextPulled = !it.leftFireHandlePulled
        it.copy(
            leftFireHandlePulled = nextPulled,
            leftFuelLeverOn = if (nextPulled) false else it.leftFuelLeverOn,
        )
    },
    FlightSimControlZone("fire-panel", Rect(0.468f, 0.675f, 0.508f, 0.765f), setOf("FIRE_HANDLE_R", "FIRE_PUSH_SWITCH_R")) {
        val nextPulled = !it.rightFireHandlePulled
        it.copy(
            rightFireHandlePulled = nextPulled,
            rightFuelLeverOn = if (nextPulled) false else it.rightFuelLeverOn,
        )
    },
    FlightSimControlZone("power-levers", Rect(0.445f, 0.735f, 0.495f, 0.940f), setOf("POWER_LEVER_L")) {
        it.copy(leftPower = it.leftPower.next())
    },
    FlightSimControlZone("power-levers", Rect(0.495f, 0.735f, 0.545f, 0.940f), setOf("POWER_LEVER_R")) {
        it.copy(rightPower = it.rightPower.next())
    },
    FlightSimControlZone("fuel-panel", Rect(0.550f, 0.790f, 0.600f, 0.930f), setOf("FUEL_LEVER_L", "FUEL_SOV_L")) {
        it.copy(leftFuelLeverOn = !it.leftFuelLeverOn)
    },
    FlightSimControlZone("fuel-panel", Rect(0.595f, 0.790f, 0.645f, 0.930f), setOf("FUEL_LEVER_R", "FUEL_SOV_R")) {
        it.copy(rightFuelLeverOn = !it.rightFuelLeverOn)
    },
    FlightSimControlZone("flaps-hydraulic", Rect(0.665f, 0.765f, 0.758f, 0.920f), setOf("FLAP_SELECTOR")) {
        it.copy(flaps = it.flaps.next())
    },
)

/**
 * Direct click action for a cockpit sprite: every switch, lever and handle
 * with simulated state responds; gauges and displays return null (select only).
 */
private fun spriteControlAction(spriteId: String): ((DesktopCockpitSimState) -> DesktopCockpitSimState)? =
    when (spriteId) {
        "BATTERY_MASTER" -> { s -> s.copy(batteryMaster = !s.batteryMaster) }
        "AVIONICS_MASTER" -> { s -> s.copy(avionicsMaster = !s.avionicsMaster) }
        "L_DC_GEN" -> { s -> s.copy(leftDcGenerator = !s.leftDcGenerator) }
        "R_DC_GEN" -> { s -> s.copy(rightDcGenerator = !s.rightDcGenerator) }
        "FWD_BOOST_PUMP" -> { s -> s.copy(fwdBoost1 = !s.fwdBoost1) }
        "AFT_BOOST_PUMP" -> { s -> s.copy(aftBoost1 = !s.aftBoost1) }
        "STBY_BOOST_PUMP_FWD" -> { s -> s.copy(fwdBoost2 = !s.fwdBoost2) }
        "STBY_BOOST_PUMP_AFT" -> { s -> s.copy(aftBoost2 = !s.aftBoost2) }
        "FUEL_LEVER_L", "FUEL_SOV_L", "EMERG_FUEL_SHUTOFF_L" -> { s -> s.copy(leftFuelLeverOn = !s.leftFuelLeverOn) }
        "FUEL_LEVER_R", "FUEL_SOV_R", "EMERG_FUEL_SHUTOFF_R" -> { s -> s.copy(rightFuelLeverOn = !s.rightFuelLeverOn) }
        "FUEL_SELECTOR" -> { s -> s.copy(crossfeed = s.crossfeed.next()) }
        "POWER_LEVER_L" -> { s -> s.copy(leftPower = s.leftPower.next()) }
        "POWER_LEVER_R" -> { s -> s.copy(rightPower = s.rightPower.next()) }
        "FLAP_SELECTOR" -> { s -> s.copy(flaps = s.flaps.next()) }
        "FIRE_DETECT_TEST" -> { s -> s.copy(fireDetectionArmed = !s.fireDetectionArmed) }
        "FIRE_HANDLE_L", "FIRE_PUSH_SWITCH_L" -> { s ->
            val nextPulled = !s.leftFireHandlePulled
            s.copy(
                leftFireHandlePulled = nextPulled,
                leftFuelLeverOn = if (nextPulled) false else s.leftFuelLeverOn,
            )
        }
        "FIRE_HANDLE_R", "FIRE_PUSH_SWITCH_R" -> { s ->
            val nextPulled = !s.rightFireHandlePulled
            s.copy(
                rightFireHandlePulled = nextPulled,
                rightFuelLeverOn = if (nextPulled) false else s.rightFuelLeverOn,
            )
        }
        else -> null
    }

private fun targetIdForSprite(spriteId: String): String? = when {
    spriteId in setOf("MASTER_CAUTION", "MASTER_CAUTION_LEFT", "MASTER_CAUTION_RIGHT", "MASTER_WARNING_LEFT", "MASTER_WARNING_RIGHT", "CAUT_LT_TEST") ->
        "annunciators"
    spriteId in setOf(
        "TORQUE_GAUGE_L",
        "TORQUE_GAUGE_R",
        "T5_GAUGE_L",
        "T5_GAUGE_R",
        "NG_GAUGE_L",
        "NG_GAUGE_R",
        "NP_GAUGE_L",
        "NP_GAUGE_R",
        "OIL_PRESS_GAUGE_L",
        "OIL_PRESS_GAUGE_R",
        "OIL_TEMP_GAUGE_L",
        "OIL_TEMP_GAUGE_R",
        "FUEL_FLOW_GAUGE_L",
        "FUEL_FLOW_GAUGE_R",
        "PFD_LEFT",
        "PFD_RIGHT",
        "MFD_CENTRE_DISPLAY",
    ) -> "engine-instruments"
    spriteId in setOf(
        "BATTERY_MASTER",
        "AVIONICS_MASTER",
        "L_DC_GEN",
        "R_DC_GEN",
        "BUS_TIE",
        "STBY_BATTERY",
        "INVERTER_SWITCH",
        "AVIONICS_CIRCUIT_BREAKER_PANEL",
    ) || spriteId.endsWith("_CB") -> "electrical-panel"
    spriteId in setOf("FIRE_HANDLE_L", "FIRE_HANDLE_R", "FIRE_PUSH_SWITCH_L", "FIRE_PUSH_SWITCH_R", "FIRE_DETECT_TEST") ->
        "fire-panel"
    spriteId in setOf("POWER_LEVER_L", "POWER_LEVER_R", "PROP_LEVER_L", "PROP_LEVER_R") ->
        "power-levers"
    spriteId in setOf(
        "FUEL_LEVER_L",
        "FUEL_LEVER_R",
        "FUEL_SOV_L",
        "FUEL_SOV_R",
        "FUEL_SELECTOR",
        "FWD_BOOST_PUMP",
        "AFT_BOOST_PUMP",
        "STBY_BOOST_PUMP_FWD",
        "STBY_BOOST_PUMP_AFT",
        "EMERG_FUEL_SHUTOFF_L",
        "EMERG_FUEL_SHUTOFF_R",
    ) -> "fuel-panel"
    spriteId in setOf("FLAP_SELECTOR", "HYD_OIL_PUMP_CB") -> "flaps-hydraulic"
    else -> null
}

private fun String.spriteIdsForTarget(): Set<String> = when (this) {
    "annunciators" -> setOf("MASTER_CAUTION", "MASTER_CAUTION_LEFT", "MASTER_CAUTION_RIGHT", "MASTER_WARNING_LEFT", "MASTER_WARNING_RIGHT", "CAUT_LT_TEST")
    "engine-instruments" -> setOf(
        "TORQUE_GAUGE_L",
        "TORQUE_GAUGE_R",
        "T5_GAUGE_L",
        "T5_GAUGE_R",
        "NG_GAUGE_L",
        "NG_GAUGE_R",
        "NP_GAUGE_L",
        "NP_GAUGE_R",
        "OIL_PRESS_GAUGE_L",
        "OIL_PRESS_GAUGE_R",
        "OIL_TEMP_GAUGE_L",
        "OIL_TEMP_GAUGE_R",
        "FUEL_FLOW_GAUGE_L",
        "FUEL_FLOW_GAUGE_R",
        "FUEL_QUANTITY_GAUGE_L",
        "FUEL_QUANTITY_GAUGE_R",
        "AI",
        "ASI",
        "ALT",
        "VSI",
        "HSI",
        "DI",
        "TURN_SLIP",
        "STBY_INSTR",
        "PFD_LEFT",
        "PFD_RIGHT",
        "MFD_CENTRE_DISPLAY",
        "FMS_SYSTEM",
    )
    "electrical-panel" -> setOf(
        "BATTERY_MASTER",
        "AVIONICS_MASTER",
        "L_DC_GEN",
        "R_DC_GEN",
        "BUS_TIE",
        "STBY_BATTERY",
        "INVERTER_SWITCH",
        "AVIONICS_CIRCUIT_BREAKER_PANEL",
    )
    "fire-panel" -> setOf("FIRE_HANDLE_L", "FIRE_HANDLE_R", "FIRE_PUSH_SWITCH_L", "FIRE_PUSH_SWITCH_R", "FIRE_DETECT_TEST")
    "power-levers" -> setOf("POWER_LEVER_L", "POWER_LEVER_R", "PROP_LEVER_L", "PROP_LEVER_R")
    "fuel-panel" -> setOf(
        "FUEL_LEVER_L",
        "FUEL_LEVER_R",
        "FUEL_SOV_L",
        "FUEL_SOV_R",
        "FUEL_SELECTOR",
        "FWD_BOOST_PUMP",
        "AFT_BOOST_PUMP",
        "STBY_BOOST_PUMP_FWD",
        "STBY_BOOST_PUMP_AFT",
        "EMERG_FUEL_SHUTOFF_L",
        "EMERG_FUEL_SHUTOFF_R",
    )
    "flaps-hydraulic" -> setOf("FLAP_SELECTOR", "HYD_OIL_PUMP_CB")
    else -> emptySet()
}

private fun DesktopCockpitSimState.activeCueSpriteIds(): Set<String> = buildSet {
    if (batteryMaster) add("BATTERY_MASTER")
    if (avionicsMaster) add("AVIONICS_MASTER")
    if (leftDcGenerator) add("L_DC_GEN")
    if (rightDcGenerator) add("R_DC_GEN")
    if (fwdBoost1) add("FWD_BOOST_PUMP")
    if (aftBoost1) add("AFT_BOOST_PUMP")
    if (fwdBoost2) add("STBY_BOOST_PUMP_FWD")
    if (aftBoost2) add("STBY_BOOST_PUMP_AFT")
    if (leftFuelLeverOn) add("FUEL_LEVER_L")
    if (rightFuelLeverOn) add("FUEL_LEVER_R")
    if (crossfeed != CockpitCrossfeedPosition.NORMAL) add("FUEL_SELECTOR")
    if (fireDetectionArmed) add("FIRE_DETECT_TEST")
    if (leftFireHandlePulled) {
        add("FIRE_HANDLE_L")
        add("FIRE_PUSH_SWITCH_L")
    }
    if (rightFireHandlePulled) {
        add("FIRE_HANDLE_R")
        add("FIRE_PUSH_SWITCH_R")
    }
    if (leftPower != CockpitPowerLeverPosition.IDLE) add("POWER_LEVER_L")
    if (rightPower != CockpitPowerLeverPosition.IDLE) add("POWER_LEVER_R")
    if (flaps != CockpitFlapSetting.UP) add("FLAP_SELECTOR")
}

// Full zoom range for panel study: pan compensates once the scene exceeds the
// viewport, so the ceiling is only bounded by texture sharpness.
private fun sceneScaleForPointer(zoom: Float): Float =
    (0.95f * zoom).coerceIn(0.72f, 8f)

private fun Offset.toSceneOffset(
    viewportWidth: Float,
    viewportHeight: Float,
    sceneScale: Float,
    panX: Float,
    panY: Float,
): Offset {
    // The scene layer applies scale about the viewport centre with the pan
    // offset INSIDE the scaled layer: screen = centre + scale * (scene + pan - centre).
    val centerX = viewportWidth / 2f
    val centerY = viewportHeight / 2f
    return Offset(
        x = ((x - centerX) / sceneScale) + centerX - panX,
        y = ((y - centerY) / sceneScale) + centerY - panY,
    )
}

private fun Offset.toNormalizedOffset(
    viewportWidth: Float,
    viewportHeight: Float,
): Offset = Offset(
    x = (x / viewportWidth.coerceAtLeast(1f)).coerceIn(0f, 1f),
    y = (y / viewportHeight.coerceAtLeast(1f)).coerceIn(0f, 1f),
)

private enum class CockpitSpriteLayer(val folder: String) {
    Control("controls"),
    Visual("visuals"),
}

private data class CockpitSpriteEntry(
    val id: String,
    val layer: CockpitSpriteLayer,
    val path: String,
    val renderRect: Rect,
    val hitboxRect: Rect,
)

private data class CockpitSpritePack(
    val variant: CockpitSpriteVariant,
    val basePath: String,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val entries: List<CockpitSpriteEntry>,
) {
    private val controlEntriesById = entries
        .filter { it.layer == CockpitSpriteLayer.Control }
        .associateBy { it.id }
    private val entriesById = entries.groupBy { it.id }

    fun activeEntries(): List<CockpitSpriteEntry> = entries

    fun controlEntry(id: String): CockpitSpriteEntry? = controlEntriesById[id]

    fun entriesFor(id: String): List<CockpitSpriteEntry> = entriesById[id].orEmpty()

    fun hitTest(
        sceneOffset: Offset,
        viewportWidth: Float,
        viewportHeight: Float,
    ): String? {
        val bounds = cockpitSpriteBounds(viewportWidth, viewportHeight, canvasWidth, canvasHeight)
        if (!bounds.contains(sceneOffset)) return null
        val canvasOffset = Offset(
            x = ((sceneOffset.x - bounds.left) / bounds.width.coerceAtLeast(1f)) * canvasWidth,
            y = ((sceneOffset.y - bounds.top) / bounds.height.coerceAtLeast(1f)) * canvasHeight,
        )
        return entries
            .asReversed()
            .firstOrNull { it.layer == CockpitSpriteLayer.Control && it.hitboxRect.contains(canvasOffset) }
            ?.id
            ?: entries
                .asReversed()
                .firstOrNull { it.layer == CockpitSpriteLayer.Visual && it.hitboxRect.contains(canvasOffset) }
                ?.id
    }
}

private object CockpitSpritePackCatalog {
    fun load(variant: CockpitSpriteVariant): CockpitSpritePack {
        val folder = variant.spritePackFolder
        val manifestPath = "$CockpitSpritePackRootPath/$folder/${folder}_manifest.csv"
        val lines = readResourceLines(manifestPath)
        val entries = lines.drop(1).mapNotNull { line -> line.toCockpitSpriteEntry(folder) }
        val firstDataRow = lines.drop(1).firstOrNull()?.splitCsv().orEmpty()
        val canvasWidth = firstDataRow.getOrNull(2)?.toFloatOrNull() ?: if (variant == CockpitSpriteVariant.G950) 3744f else 3748f
        val canvasHeight = firstDataRow.getOrNull(1)?.toFloatOrNull() ?: 5276f
        return CockpitSpritePack(
            variant = variant,
            basePath = "$CockpitSpritePackRootPath/$folder/base/${folder}_cockpit_base_clean.png",
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            entries = entries,
        )
    }

    private fun readResourceLines(path: String): List<String> {
        val loader = Thread.currentThread().contextClassLoader
        val stream = loader?.getResourceAsStream(path)
            ?: CockpitSpritePackCatalog::class.java.classLoader.getResourceAsStream(path)
            ?: CockpitSpritePackCatalog::class.java.getResourceAsStream("/$path")
        return stream?.bufferedReader()?.use { it.readLines() }.orEmpty()
    }
}

private val CockpitSpriteVariant.spritePackFolder: String
    get() = when (this) {
        CockpitSpriteVariant.Legacy -> "legacy"
        CockpitSpriteVariant.G950 -> "g950"
    }

private fun String.toCockpitSpriteEntry(folder: String): CockpitSpriteEntry? {
    val columns = splitCsv()
    if (columns.getOrNull(26) != "compiled") return null
    val layer = when (columns.getOrNull(4)) {
        "control" -> CockpitSpriteLayer.Control
        "visual" -> CockpitSpriteLayer.Visual
        else -> return null
    }
    val id = columns.getOrNull(6)?.takeIf { it.isNotBlank() } ?: return null
    val hitboxW = columns.getOrNull(7)?.toFloatOrNull() ?: return null
    val hitboxX = columns.getOrNull(8)?.toFloatOrNull() ?: return null
    val hitboxY = columns.getOrNull(9)?.toFloatOrNull() ?: return null
    val hitboxH = columns.getOrNull(5)?.toFloatOrNull() ?: return null
    val renderH = columns.getOrNull(11)?.toFloatOrNull() ?: hitboxH
    val renderW = columns.getOrNull(12)?.toFloatOrNull() ?: hitboxW
    val renderX = columns.getOrNull(13)?.toFloatOrNull() ?: hitboxX
    val renderY = columns.getOrNull(14)?.toFloatOrNull() ?: hitboxY
    val sourceFile = columns.getOrNull(24)?.takeIf { it.isNotBlank() } ?: "$id.png"
    val stem = sourceFile.substringBeforeLast(".")
    val fileName = "${stem}__${renderW.roundToInt()}x${renderH.roundToInt()}.png"
    val path = "$CockpitSpritePackRootPath/$folder/rendered_trimmed/${layer.folder}/$fileName"
    return CockpitSpriteEntry(
        id = id,
        layer = layer,
        path = path,
        renderRect = Rect(renderX, renderY, renderX + renderW, renderY + renderH),
        hitboxRect = Rect(hitboxX, hitboxY, hitboxX + hitboxW, hitboxY + hitboxH),
    )
}

private fun String.splitCsv(): List<String> = split(',').map { it.trim() }

private fun cockpitSpriteBounds(
    viewportWidth: Float,
    viewportHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
): Rect {
    val scale = min(
        viewportWidth / canvasWidth.coerceAtLeast(1f),
        viewportHeight / canvasHeight.coerceAtLeast(1f),
    )
    val width = canvasWidth * scale
    val height = canvasHeight * scale
    val left = (viewportWidth - width) / 2f
    val top = (viewportHeight - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/* =====================================================================
   Scene rendering
   ===================================================================== */

private fun DrawScope.drawFlightSimScene(
    variant: CockpitSpriteVariant,
    simView: Dhc6SimulatorView,
    state: DesktopCockpitSimState,
    selectedTargetId: String,
    time: Float,
    cleanCockpitImage: ImageBitmap?,
    cockpitSpritePack: CockpitSpritePack,
    cockpitSpriteImages: Map<String, ImageBitmap?>,
    outsideAircraftImage: ImageBitmap?,
    environmentPackage: FsxEnvironmentPackage,
) {
    if (simView == Dhc6SimulatorView.OutsideAircraft) {
        val environmentImage = environmentPackage.previewImage
        if (environmentImage != null) {
            drawImageCover(environmentImage)
            drawEnvironmentLoadedOverlay(environmentPackage)
        } else if (outsideAircraftImage != null) {
            drawImageCover(outsideAircraftImage)
        } else {
            drawOutsideWorld(state, time)
            drawRunway(size.height * 0.42f, time)
        }
        drawExteriorSimOverlay(state, time)
        drawVignette()
        return
    }

    if (cleanCockpitImage != null) {
        drawCleanSpriteCockpit(
            baseImage = cleanCockpitImage,
            spritePack = cockpitSpritePack,
            spriteImages = cockpitSpriteImages,
            state = state,
            selectedTargetId = selectedTargetId,
        )
        return
    }

    drawOutsideWorld(state, time)
    drawPropellerDiscs(state, time)
    drawWindshieldGlass()
    drawWindshieldFrame()
    drawCockpitShell()
    drawPanelFloodLight(state)
    drawInstrumentPanel(variant, state)
    drawCenterPedestal(state)
    drawYokes()
    drawVignette()
    drawSelectedTarget(selectedTargetId)
    drawInteractionHints(selectedTargetId)
}

/* ---- Outside world -------------------------------------------------- */

private fun DrawScope.drawOutsideWorld(state: DesktopCockpitSimState, time: Float) {
    val w = size.width
    val h = size.height
    val leftTorque = state.torquePercent(leftSide = true) / 100f
    val rightTorque = state.torquePercent(leftSide = false) / 100f
    val motion = ((leftTorque + rightTorque) / 2f).coerceIn(0f, 1f)
    val horizonY = h * (0.42f - motion * 0.025f)

    // Sky - layered gradient, brightest just above the horizon
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF1E5FA8),
                0.55f to Color(0xFF3A94D6),
                0.88f to Color(0xFF9FCBEA),
                1.00f to Color(0xFFD8E9F5),
            ),
            startY = 0f,
            endY = horizonY,
        ),
        size = Size(w, horizonY),
    )

    // Sun with radial glow, upper right
    val sunCenter = Offset(w * 0.78f, h * 0.075f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xE6FFF6DC), Color(0x66FFE9B0), Color(0x00FFE9B0)),
            center = sunCenter,
            radius = w * 0.11f,
        ),
        radius = w * 0.11f,
        center = sunCenter,
    )
    drawCircle(Color(0xFFFFF8E4), radius = w * 0.020f, center = sunCenter)

    // Drifting cumulus
    val drift = time * w * 0.006f
    drawCloud(wrapX(w * 0.16f + drift, w), h * 0.115f, w * 0.075f, 0.85f)
    drawCloud(wrapX(w * 0.46f + drift * 0.7f, w), h * 0.195f, w * 0.055f, 0.65f)
    drawCloud(wrapX(w * 0.68f + drift * 0.85f, w), h * 0.090f, w * 0.048f, 0.55f)
    drawCloud(wrapX(w * 0.90f + drift * 0.6f, w), h * 0.220f, w * 0.062f, 0.70f)

    // Far mountain range - hazy blue
    val farMountain = Path().apply {
        moveTo(0f, horizonY + h * 0.02f)
        lineTo(w * 0.08f, horizonY - h * 0.050f)
        lineTo(w * 0.19f, horizonY + h * 0.005f)
        lineTo(w * 0.30f, horizonY - h * 0.070f)
        lineTo(w * 0.43f, horizonY + h * 0.010f)
        lineTo(w * 0.57f, horizonY - h * 0.060f)
        lineTo(w * 0.72f, horizonY + h * 0.008f)
        lineTo(w * 0.86f, horizonY - h * 0.052f)
        lineTo(w, horizonY - h * 0.010f)
        lineTo(w, horizonY + h * 0.06f)
        lineTo(0f, horizonY + h * 0.06f)
        close()
    }
    drawPath(farMountain, Color(0xFF8CA6BC))

    // Near mountain range - darker, sharper
    val mountain = Path().apply {
        moveTo(0f, horizonY + h * 0.04f)
        lineTo(w * 0.11f, horizonY - h * 0.035f)
        lineTo(w * 0.22f, horizonY + h * 0.025f)
        lineTo(w * 0.36f, horizonY - h * 0.055f)
        lineTo(w * 0.51f, horizonY + h * 0.03f)
        lineTo(w * 0.68f, horizonY - h * 0.045f)
        lineTo(w * 0.83f, horizonY + h * 0.025f)
        lineTo(w, horizonY - h * 0.018f)
        lineTo(w, horizonY + h * 0.08f)
        lineTo(0f, horizonY + h * 0.08f)
        close()
    }
    drawPath(mountain, Color(0xFF64705F))
    // Sun-side ridge highlight
    drawPath(mountain, Brush.horizontalGradient(listOf(Color(0x00FFFFFF), Color(0x2EFFE9B0)), w * 0.4f, w))

    // Atmospheric haze band hugging the horizon
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x00E8F1F8), Color(0x59E8F1F8), Color(0x00E8F1F8)),
            startY = horizonY - h * 0.045f,
            endY = horizonY + h * 0.055f,
        ),
        topLeft = Offset(0f, horizonY - h * 0.045f),
        size = Size(w, h * 0.10f),
    )

    // Terrain
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF5C8A45),
                0.45f to Color(0xFF3F6231),
                1.00f to Color(0xFF20351F),
            ),
            startY = horizonY,
            endY = h * 0.78f,
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h * 0.42f),
    )
    // Perspective field strips
    fieldStrip(w * 0.02f, w * 0.30f, horizonY, h, Color(0x2CE8DFA0))
    fieldStrip(w * 0.30f, w * 0.44f, horizonY, h, Color(0x1F203818))
    fieldStrip(w * 0.62f, w * 0.78f, horizonY, h, Color(0x2CB9CF8A))
    fieldStrip(w * 0.84f, w * 0.99f, horizonY, h, Color(0x24203818))

    drawRunway(horizonY, time)
}

private fun wrapX(x: Float, w: Float): Float {
    val span = w * 1.3f
    var v = x % span
    if (v < 0f) v += span
    return v - w * 0.15f
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, s: Float, alpha: Float) {
    val cloud = Color.White.copy(alpha = 0.55f * alpha)
    val shade = Color(0xFFCBDCEA).copy(alpha = 0.40f * alpha)
    drawOval(shade, topLeft = Offset(cx - s * 1.05f, cy - s * 0.18f), size = Size(s * 2.1f, s * 0.62f))
    drawOval(cloud, topLeft = Offset(cx - s, cy - s * 0.30f), size = Size(s * 2f, s * 0.66f))
    drawOval(cloud, topLeft = Offset(cx - s * 0.55f, cy - s * 0.58f), size = Size(s * 1.1f, s * 0.8f))
    drawOval(cloud, topLeft = Offset(cx - s * 0.05f, cy - s * 0.45f), size = Size(s * 0.9f, s * 0.66f))
}

private fun DrawScope.fieldStrip(x0: Float, x1: Float, horizonY: Float, h: Float, color: Color) {
    val w = size.width
    val cx = w / 2f
    // Fan the strip outward from the vanishing area for cheap perspective
    fun spread(x: Float, t: Float): Float = cx + (x - cx) * (0.35f + 0.65f * t)
    val strip = Path().apply {
        moveTo(spread(x0, 0f), horizonY + h * 0.045f)
        lineTo(spread(x1, 0f), horizonY + h * 0.045f)
        lineTo(spread(x1, 1f), h * 0.76f)
        lineTo(spread(x0, 1f), h * 0.76f)
        close()
    }
    drawPath(strip, color)
}

private fun DrawScope.drawRunway(horizonY: Float, time: Float) {
    val w = size.width
    val h = size.height
    val topY = horizonY + h * 0.05f
    val bottomY = h * 0.900f

    fun leftX(t: Float) = w * (0.455f + (0.210f - 0.455f) * t)
    fun rightX(t: Float) = w * (0.545f + (0.790f - 0.545f) * t)
    fun y(t: Float) = topY + (bottomY - topY) * t

    val runway = Path().apply {
        moveTo(leftX(0f), y(0f))
        lineTo(rightX(0f), y(0f))
        lineTo(rightX(1f), y(1f))
        lineTo(leftX(1f), y(1f))
        close()
    }
    // Asphalt with distance shading
    drawPath(
        runway,
        Brush.verticalGradient(
            listOf(Color(0xFF4A5054), Color(0xFF33383B), Color(0xFF26292C)),
            startY = topY,
            endY = bottomY,
        ),
    )
    // Edge lines
    drawLine(Color.White.copy(alpha = 0.82f), Offset(leftX(0f), y(0f)), Offset(leftX(1f), y(1f)), 3f)
    drawLine(Color.White.copy(alpha = 0.82f), Offset(rightX(0f), y(0f)), Offset(rightX(1f), y(1f)), 3f)

    // Threshold bars ("piano keys") at the far end
    run {
        val t = 0.045f
        val lx = leftX(t)
        val rx = rightX(t)
        val barY = y(t)
        val barH = h * 0.014f
        val count = 8
        val span = rx - lx
        val barW = span / (count * 1.7f)
        for (i in 0 until count) {
            val bx = lx + span * (0.06f + i * 0.122f)
            drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(bx, barY), size = Size(barW, barH))
        }
    }

    // Touchdown-zone side bars
    listOf(0.22f, 0.34f).forEachIndexed { index, t ->
        val stripeW = w * (0.010f + index * 0.004f)
        val stripeH = h * (0.016f + index * 0.006f)
        val inset = 0.16f
        val lx = leftX(t)
        val rx = rightX(t)
        val span = rx - lx
        drawRect(Color.White.copy(alpha = 0.8f), Offset(lx + span * inset, y(t)), Size(stripeW, stripeH))
        drawRect(Color.White.copy(alpha = 0.8f), Offset(rx - span * inset - stripeW, y(t)), Size(stripeW, stripeH))
    }
    // Aiming-point blocks
    run {
        val t = 0.47f
        val lx = leftX(t)
        val rx = rightX(t)
        val span = rx - lx
        val blockW = span * 0.10f
        val blockH = h * 0.035f
        drawRect(Color.White.copy(alpha = 0.88f), Offset(lx + span * 0.17f, y(t)), Size(blockW, blockH))
        drawRect(Color.White.copy(alpha = 0.88f), Offset(rx - span * 0.17f - blockW, y(t)), Size(blockW, blockH))
    }

    // Centreline dashes
    repeat(7) { index ->
        val yy = horizonY + h * (0.10f + index * 0.095f)
        val dashHeight = h * (0.028f + index * 0.008f)
        drawLine(
            color = Color.White.copy(alpha = 0.88f),
            start = Offset(w * 0.50f, yy),
            end = Offset(w * 0.50f, yy + dashHeight),
            strokeWidth = 4f + index,
            cap = StrokeCap.Round,
        )
    }

    // Runway edge lights with glow, spaced in perspective
    for (i in 0 until 7) {
        val t = 0.10f + i * 0.135f
        val r = 1.6f + t * 3.4f
        val flicker = 0.88f + 0.12f * sin(time * 2.2f + i)
        listOf(leftX(t) - r * 2f, rightX(t) + r * 2f).forEach { lx ->
            drawCircle(Color(0xFFFFF2C0).copy(alpha = 0.28f * flicker), radius = r * 2.4f, center = Offset(lx, y(t)))
            drawCircle(Color(0xFFFFF8DC).copy(alpha = 0.95f * flicker), radius = r, center = Offset(lx, y(t)))
        }
    }

    // PAPI - two white over two red, left of the touchdown zone
    run {
        val t = 0.50f
        val px = leftX(t) - w * 0.055f
        val py = y(t)
        val r = w * 0.0042f
        for (i in 0 until 4) {
            val color = if (i < 2) Color(0xFFFFFFFF) else Color(0xFFFF4040)
            val cx = px + i * r * 3.2f
            drawCircle(color.copy(alpha = 0.30f), radius = r * 2.1f, center = Offset(cx, py))
            drawCircle(color, radius = r, center = Offset(cx, py))
        }
    }
}

private fun DrawScope.drawExteriorSimOverlay(
    state: DesktopCockpitSimState,
    time: Float,
) {
    val w = size.width
    val h = size.height
    val running = state.leftEngineRunning || state.rightEngineRunning
    val pulse = if (running) 0.55f + 0.45f * sin(time * 8f).coerceIn(-1f, 1f) else 0f
    val hudRect = Rect(w * 0.035f, h * 0.050f, w * 0.330f, h * 0.260f)
    drawRoundRect(
        color = Color(0xCC020B12),
        topLeft = hudRect.topLeft,
        size = hudRect.size,
        cornerRadius = CornerRadius(18f, 18f),
    )
    drawRoundRect(
        color = Color(0xFF55C7FF),
        topLeft = hudRect.topLeft,
        size = hudRect.size,
        cornerRadius = CornerRadius(18f, 18f),
        style = Stroke(width = 2.5f),
    )
    val barTop = hudRect.top + hudRect.height * 0.55f
    val barW = hudRect.width * 0.30f
    listOf(
        "L" to state.torquePercent(true) / 100f,
        "R" to state.torquePercent(false) / 100f,
    ).forEachIndexed { index, (_, value) ->
        val left = hudRect.left + hudRect.width * (0.12f + index * 0.42f)
        drawRoundRect(Color(0xFF152733), Offset(left, barTop), Size(barW, hudRect.height * 0.16f), CornerRadius(6f, 6f))
        drawRoundRect(
            color = Color(0xFF6BE675).copy(alpha = if (running) 0.78f + pulse * 0.22f else 0.45f),
            topLeft = Offset(left, barTop),
            size = Size(barW * value.coerceIn(0f, 1f), hudRect.height * 0.16f),
            cornerRadius = CornerRadius(6f, 6f),
        )
    }
}

private fun DrawScope.drawEnvironmentLoadedOverlay(environmentPackage: FsxEnvironmentPackage) {
    if (!environmentPackage.isAvailable) return
    val w = size.width
    val h = size.height
    val strip = Rect(w * 0.035f, h * 0.800f, w * 0.965f, h * 0.920f)
    drawRoundRect(
        color = Color(0x33020B12),
        topLeft = strip.topLeft,
        size = strip.size,
        cornerRadius = CornerRadius(18f, 18f),
    )
    drawRoundRect(
        color = Color(0x6655C7FF),
        topLeft = strip.topLeft,
        size = strip.size,
        cornerRadius = CornerRadius(18f, 18f),
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawCleanSpriteCockpit(
    baseImage: ImageBitmap,
    spritePack: CockpitSpritePack,
    spriteImages: Map<String, ImageBitmap?>,
    state: DesktopCockpitSimState,
    selectedTargetId: String,
) {
    drawRect(Color.Black, size = size)
    val bounds = cockpitSpriteBounds(
        viewportWidth = size.width,
        viewportHeight = size.height,
        canvasWidth = spritePack.canvasWidth,
        canvasHeight = spritePack.canvasHeight,
    )
    drawImage(
        image = baseImage,
        dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
        dstSize = IntSize(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.High,
    )
    spritePack.activeEntries().forEach { entry ->
        spriteImages[entry.path]?.let { sprite ->
            drawCockpitSprite(sprite, entry, spritePack, bounds, state)
        }
    }
    drawSpriteStateCues(spritePack, bounds, state)
    drawSelectedSpriteTarget(spritePack, bounds, selectedTargetId)
    drawSpriteInteractionHints(spritePack, bounds, selectedTargetId)
}

private fun DrawScope.drawCockpitSprite(
    image: ImageBitmap,
    entry: CockpitSpriteEntry,
    spritePack: CockpitSpritePack,
    bounds: Rect,
    state: DesktopCockpitSimState,
) {
    val rect = entry.renderRect.toViewportRect(spritePack, bounds)
    // Actuate the control: translate its handle/lever along its throw so it
    // visibly moves when toggled, like the sim's 2D panel.
    val offset = spriteActuationOffset(entry.id, state, rect.width, rect.height)
    drawImage(
        image = image,
        dstOffset = IntOffset((rect.left + offset.x).roundToInt(), (rect.top + offset.y).roundToInt()),
        dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.High,
    )
}

/**
 * How far (viewport px) to translate a control's handle sprite from its
 * authored rest position given the current state, so switches/levers visibly
 * actuate. Sprites are authored at their "on/forward" extreme, so OFF states
 * slide the handle back toward its rest; toggles nudge up/down like a flipped
 * switch. Unmapped controls do not move.
 */
private fun spriteActuationOffset(id: String, state: DesktopCockpitSimState, w: Float, h: Float): Offset {
    fun flip(on: Boolean) = if (on) Offset(0f, -h * 0.16f) else Offset.Zero
    return when (id) {
        // Fuel levers: knob authored up (ON); slide down to cutoff when OFF.
        "FUEL_LEVER_L", "FUEL_SOV_L", "EMERG_FUEL_SHUTOFF_L" ->
            if (state.leftFuelLeverOn) Offset.Zero else Offset(0f, h * 0.62f)
        "FUEL_LEVER_R", "FUEL_SOV_R", "EMERG_FUEL_SHUTOFF_R" ->
            if (state.rightFuelLeverOn) Offset.Zero else Offset(0f, h * 0.62f)
        // Fire handles: pull outward when actuated.
        "FIRE_HANDLE_L", "FIRE_PUSH_SWITCH_L" ->
            if (state.leftFireHandlePulled) Offset(-w * 0.30f, 0f) else Offset.Zero
        "FIRE_HANDLE_R", "FIRE_PUSH_SWITCH_R" ->
            if (state.rightFireHandlePulled) Offset(w * 0.30f, 0f) else Offset.Zero
        // Flap selector rides down its gate with more flap.
        "FLAP_SELECTOR" -> Offset(
            0f,
            h * when (state.flaps) {
                CockpitFlapSetting.UP -> 0f
                CockpitFlapSetting.TAKEOFF -> 0.35f
                CockpitFlapSetting.LANDING -> 0.70f
            },
        )
        // Power / prop levers advance forward (up) with the lever position.
        "POWER_LEVER_L" -> Offset(0f, -h * powerFrac(state.leftPower))
        "POWER_LEVER_R" -> Offset(0f, -h * powerFrac(state.rightPower))
        "BATTERY_MASTER" -> flip(state.batteryMaster)
        "AVIONICS_MASTER" -> flip(state.avionicsMaster)
        "L_DC_GEN" -> flip(state.leftDcGenerator)
        "R_DC_GEN" -> flip(state.rightDcGenerator)
        "BUS_TIE" -> flip(state.crossfeed != CockpitCrossfeedPosition.NORMAL)
        "FWD_BOOST_PUMP" -> flip(state.fwdBoost1 || state.fwdBoost2)
        "AFT_BOOST_PUMP" -> flip(state.aftBoost1 || state.aftBoost2)
        "IGNITION_ARM" -> flip(state.fireDetectionArmed)
        else -> Offset.Zero
    }
}

private fun powerFrac(pos: CockpitPowerLeverPosition): Float = when (pos) {
    CockpitPowerLeverPosition.REVERSE -> 0f
    CockpitPowerLeverPosition.IDLE -> 0.10f
    CockpitPowerLeverPosition.CRUISE -> 0.45f
    CockpitPowerLeverPosition.CLIMB -> 0.70f
    CockpitPowerLeverPosition.MAX -> 0.92f
}

private fun DrawScope.drawSpriteStateCues(
    spritePack: CockpitSpritePack,
    bounds: Rect,
    state: DesktopCockpitSimState,
) {
    state.activeCueSpriteIds().forEach { id ->
        spritePack.controlEntry(id)?.let { entry ->
            val rect = entry.hitboxRect.toViewportRect(spritePack, bounds).expanded(4f)
            val cueColor = if (id.startsWith("FIRE")) Color(0xFFFF5C5C) else Color(0xFF6BE675)
            drawRoundRect(
                color = cueColor.copy(alpha = 0.18f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = cueColor.copy(alpha = 0.92f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2.2f),
            )
        }
    }
}

private fun DrawScope.drawSelectedSpriteTarget(
    spritePack: CockpitSpritePack,
    bounds: Rect,
    selectedTargetId: String,
) {
    val rect = selectedTargetId
        .spriteIdsForTarget()
        .flatMap { spritePack.entriesFor(it) }
        .map { it.hitboxRect }
        .unionOrNull()
        ?.toViewportRect(spritePack, bounds)
        ?.expanded(8f)
        ?: return drawSelectedTarget(selectedTargetId)

    drawRoundRect(
        color = Color(0x3355C7FF),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = Color(0xFF55C7FF),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 3f),
    )
}

private fun DrawScope.drawSpriteInteractionHints(
    spritePack: CockpitSpritePack,
    bounds: Rect,
    selectedTargetId: String,
) {
    FlightSimControlZones
        .filter { it.targetId == selectedTargetId }
        .forEach { zone ->
            zone.spriteIds
                .mapNotNull { spritePack.controlEntry(it) }
                .forEach { entry ->
                    val rect = entry.hitboxRect.toViewportRect(spritePack, bounds).expanded(3f)
                    drawRoundRect(
                        color = Color(0xFFBFEAFF),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = CornerRadius(10f, 10f),
                        style = Stroke(width = 2f),
                    )
                    drawCircle(
                        color = Color(0x8855C7FF),
                        radius = 5.5f,
                        center = rect.center,
                    )
                }
        }
}

private fun Rect.toViewportRect(spritePack: CockpitSpritePack, bounds: Rect): Rect {
    val scaleX = bounds.width / spritePack.canvasWidth.coerceAtLeast(1f)
    val scaleY = bounds.height / spritePack.canvasHeight.coerceAtLeast(1f)
    return Rect(
        left = bounds.left + left * scaleX,
        top = bounds.top + top * scaleY,
        right = bounds.left + right * scaleX,
        bottom = bounds.top + bottom * scaleY,
    )
}

private fun Rect.expanded(delta: Float): Rect = Rect(
    left = left - delta,
    top = top - delta,
    right = right + delta,
    bottom = bottom + delta,
)

private fun List<Rect>.unionOrNull(): Rect? {
    if (isEmpty()) return null
    return drop(1).fold(first()) { acc, rect ->
        Rect(
            left = min(acc.left, rect.left),
            top = min(acc.top, rect.top),
            right = max(acc.right, rect.right),
            bottom = max(acc.bottom, rect.bottom),
        )
    }
}

private fun DrawScope.drawImageCover(image: ImageBitmap) {
    val dstW = size.width.roundToInt().coerceAtLeast(1)
    val dstH = size.height.roundToInt().coerceAtLeast(1)
    val srcRatio = image.width.toFloat() / image.height.toFloat()
    val dstRatio = dstW.toFloat() / dstH.toFloat()
    val srcW: Int
    val srcH: Int
    val srcX: Int
    val srcY: Int
    if (srcRatio > dstRatio) {
        srcH = image.height
        srcW = (srcH * dstRatio).roundToInt().coerceIn(1, image.width)
        srcX = ((image.width - srcW) / 2f).roundToInt()
        srcY = 0
    } else {
        srcW = image.width
        srcH = (srcW / dstRatio).roundToInt().coerceIn(1, image.height)
        srcX = 0
        srcY = ((image.height - srcH) / 2f).roundToInt()
    }
    drawImage(
        image = image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(dstW, dstH),
        filterQuality = FilterQuality.High,
    )
}

/* ---- Propellers & glass ---------------------------------------------- */

private fun DrawScope.drawPropellerDiscs(state: DesktopCockpitSimState, time: Float) {
    val w = size.width
    val h = size.height
    listOf(
        Triple(w * 0.155f, state.leftEngineRunning, state.torquePercent(leftSide = true)),
        Triple(w * 0.845f, state.rightEngineRunning, state.torquePercent(leftSide = false)),
    ).forEach { (cx, running, torque) ->
        if (!running) return@forEach
        val cy = h * 0.315f
        val radius = h * 0.155f
        // Translucent prop disc
        drawCircle(Color(0x14E8F4FF), radius = radius, center = Offset(cx, cy))
        drawCircle(Color(0x22C9E6FA), radius = radius, center = Offset(cx, cy), style = Stroke(width = radius * 0.05f))
        // Blade shimmer - three streaks sweeping with power
        val speed = 9f + torque * 0.22f
        val angle = time * speed
        repeat(3) { blade ->
            val a = angle + blade * (2f * Math.PI.toFloat() / 3f)
            val tip = Offset(cx + cos(a) * radius * 0.96f, cy + sin(a) * radius * 0.96f)
            drawLine(
                color = Color(0x30DCEBF7),
                start = Offset(cx, cy),
                end = tip,
                strokeWidth = radius * 0.10f,
                cap = StrokeCap.Round,
            )
        }
        // Spinner
        drawCircle(Color(0xFF23282D), radius = radius * 0.14f, center = Offset(cx, cy))
        drawCircle(Color(0x66FFFFFF), radius = radius * 0.05f, center = Offset(cx - radius * 0.03f, cy - radius * 0.04f))
    }
}

private fun DrawScope.drawWindshieldGlass() {
    val w = size.width
    val h = size.height
    // Subtle blue glass tint over the window area
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x14BFE2FF), Color(0x00BFE2FF)),
            startY = 0f,
            endY = h * 0.30f,
        ),
        size = Size(w, h * 0.50f),
    )
    // Diagonal reflection streaks
    fun streak(x0: Float, width: Float, alpha: Float) {
        val path = Path().apply {
            moveTo(w * x0, 0f)
            lineTo(w * (x0 + width), 0f)
            lineTo(w * (x0 + width - 0.10f), h * 0.50f)
            lineTo(w * (x0 - 0.10f), h * 0.50f)
            close()
        }
        drawPath(path, Color.White.copy(alpha = alpha))
    }
    streak(0.30f, 0.045f, 0.045f)
    streak(0.38f, 0.018f, 0.03f)
    streak(0.66f, 0.05f, 0.04f)
}

private fun DrawScope.drawWindshieldFrame() {
    val w = size.width
    val h = size.height
    val frame = Color(0xFF06090D)

    val leftPillar = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.135f, 0f)
        lineTo(w * 0.295f, h * 0.505f)
        lineTo(w * 0.210f, h * 0.620f)
        lineTo(w * 0.020f, h * 0.355f)
        close()
    }
    val rightPillar = Path().apply {
        moveTo(w, 0f)
        lineTo(w * 0.865f, 0f)
        lineTo(w * 0.705f, h * 0.505f)
        lineTo(w * 0.790f, h * 0.620f)
        lineTo(w * 0.980f, h * 0.355f)
        close()
    }
    val centerPost = Path().apply {
        moveTo(w * 0.488f, 0f)
        lineTo(w * 0.512f, 0f)
        lineTo(w * 0.525f, h * 0.505f)
        lineTo(w * 0.475f, h * 0.505f)
        close()
    }

    drawPath(leftPillar, frame)
    drawPath(leftPillar, Brush.horizontalGradient(listOf(Color(0x33303B44), Color(0x00000000)), 0f, w * 0.2f))
    drawPath(rightPillar, frame)
    drawPath(rightPillar, Brush.horizontalGradient(listOf(Color(0x00000000), Color(0x33303B44)), w * 0.8f, w))
    drawPath(centerPost, frame)
    drawLine(Color(0x552F3A43), Offset(w * 0.50f, 0f), Offset(w * 0.50f, h * 0.50f), 2f)

    drawRoundRect(
        color = Color(0xFF1B242A),
        topLeft = Offset(w * 0.080f, h * 0.050f),
        size = Size(w * 0.840f, h * 0.535f),
        cornerRadius = CornerRadius(w * 0.025f, w * 0.025f),
        style = Stroke(width = 16f),
    )
    drawLine(Color(0xFF202C32), Offset(w * 0.095f, h * 0.055f), Offset(w * 0.295f, h * 0.505f), 10f)
    drawLine(Color(0xFF202C32), Offset(w * 0.905f, h * 0.055f), Offset(w * 0.705f, h * 0.505f), 10f)
}

/* ---- Cockpit structure ------------------------------------------------ */

private fun DrawScope.drawCockpitShell() {
    val w = size.width
    val h = size.height

    val glare = Path().apply {
        moveTo(w * 0.095f, h * 0.485f)
        cubicTo(w * 0.250f, h * 0.440f, w * 0.750f, h * 0.440f, w * 0.905f, h * 0.485f)
        lineTo(w, h * 0.665f)
        lineTo(w, h)
        lineTo(0f, h)
        lineTo(0f, h * 0.665f)
        close()
    }
    drawPath(glare, Color(0xFF101317))
    drawPath(glare, Brush.verticalGradient(listOf(Color(0xFF262A2D), Color(0xFF070A0D)), h * 0.45f, h))
    // Glareshield leading-edge highlight
    val glareEdge = Path().apply {
        moveTo(w * 0.095f, h * 0.487f)
        cubicTo(w * 0.250f, h * 0.442f, w * 0.750f, h * 0.442f, w * 0.905f, h * 0.487f)
    }
    drawPath(glareEdge, Color(0x50AFC4D4), style = Stroke(width = 3f, cap = StrokeCap.Round))

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF41464B), Color(0xFF191C20))),
        topLeft = Offset(w * 0.110f, h * 0.545f),
        size = Size(w * 0.780f, h * 0.205f),
        cornerRadius = CornerRadius(18f, 18f),
    )
    drawRoundRect(
        color = Color(0xFF0D0F12),
        topLeft = Offset(w * 0.118f, h * 0.553f),
        size = Size(w * 0.764f, h * 0.190f),
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 3f),
    )
    // Panel fasteners
    val screwR = w * 0.0028f
    listOf(
        Offset(w * 0.124f, h * 0.560f), Offset(w * 0.876f, h * 0.560f),
        Offset(w * 0.124f, h * 0.736f), Offset(w * 0.876f, h * 0.736f),
        Offset(w * 0.500f, h * 0.560f),
    ).forEach { c ->
        drawCircle(Color(0xFF52585E), radius = screwR, center = c)
        drawLine(Color(0xFF1A1D20), Offset(c.x - screwR * 0.7f, c.y), Offset(c.x + screwR * 0.7f, c.y), 1.4f)
    }
}

private fun DrawScope.drawPanelFloodLight(state: DesktopCockpitSimState) {
    if (!state.electricalBusPowered) return
    val w = size.width
    val h = size.height
    // Warm instrument flood over the main panel when the bus is alive
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x24FFCE8A), Color(0x00FFCE8A)),
            center = Offset(w * 0.5f, h * 0.64f),
            radius = w * 0.42f,
        ),
        radius = w * 0.42f,
        center = Offset(w * 0.5f, h * 0.64f),
    )
}

/* ---- Instrument panel -------------------------------------------------- */

private fun DrawScope.drawInstrumentPanel(
    variant: CockpitSpriteVariant,
    state: DesktopCockpitSimState,
) {
    val w = size.width
    val h = size.height
    val powered = state.electricalBusPowered

    drawAnnunciatorRow(state)

    if (variant == CockpitSpriteVariant.G950) {
        drawGlassScreen(Rect(w * 0.175f, h * 0.565f, w * 0.375f, h * 0.705f), powered, state, leftSide = true)
        drawGlassScreen(Rect(w * 0.625f, h * 0.565f, w * 0.825f, h * 0.705f), powered, state, leftSide = false)
        drawEngineStack(Rect(w * 0.395f, h * 0.560f, w * 0.605f, h * 0.710f), state)
    } else {
        drawRoundGauge(w * 0.220f, h * 0.605f, h * 0.036f, 0.58f, Color(0xFFBFEAFF))
        drawRoundGauge(w * 0.315f, h * 0.605f, h * 0.036f, 0.42f, Color(0xFFD6ECFF))
        drawRoundGauge(w * 0.410f, h * 0.605f, h * 0.036f, state.torquePercent(true) / 100f, Color(0xFF6BE675))
        drawRoundGauge(w * 0.505f, h * 0.605f, h * 0.036f, state.torquePercent(false) / 100f, Color(0xFF6BE675))
        drawRoundGauge(w * 0.600f, h * 0.605f, h * 0.036f, if (state.hydraulicPressurePsi > 2500) 0.78f else 0.22f, Color(0xFFFFD166))
    }

    drawElectricalPanel(state)
    drawFirePanel(state)
}

private fun DrawScope.drawAnnunciatorRow(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    val x = w * 0.360f
    val y = h * 0.500f
    val lampW = w * 0.045f
    val lampH = h * 0.026f
    val lamps = listOf(
        state.leftEngineFire && !state.leftFireHandlePulled,
        state.rightEngineFire && !state.rightFireHandlePulled,
        state.leftEngineRunning && !state.leftGeneratorOnline,
        state.rightEngineRunning && !state.rightGeneratorOnline,
        state.leftFuelLeverOn && !state.leftFuelPressure,
        state.rightFuelLeverOn && !state.rightFuelPressure,
    )

    lamps.forEachIndexed { index, active ->
        val color = when {
            index <= 1 && active -> Color(0xFFFF3030)
            active -> Color(0xFFFFB23A)
            else -> Color(0xFF1A252B)
        }
        val topLeft = Offset(x + index * lampW * 1.12f, y)
        if (active) {
            // Lamp bloom
            drawRoundRect(
                color = color.copy(alpha = 0.35f),
                topLeft = Offset(topLeft.x - 3f, topLeft.y - 3f),
                size = Size(lampW + 6f, lampH + 6f),
                cornerRadius = CornerRadius(7f, 7f),
            )
        }
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = Size(lampW, lampH),
            cornerRadius = CornerRadius(5f, 5f),
        )
        drawRoundRect(
            color = Color(0xFF0A0E11),
            topLeft = topLeft,
            size = Size(lampW, lampH),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = 1.6f),
        )
        // Lens highlight
        drawRoundRect(
            color = Color.White.copy(alpha = if (active) 0.30f else 0.08f),
            topLeft = Offset(topLeft.x + lampW * 0.10f, topLeft.y + lampH * 0.12f),
            size = Size(lampW * 0.8f, lampH * 0.24f),
            cornerRadius = CornerRadius(3f, 3f),
        )
    }
}

private fun DrawScope.drawGlassScreen(
    rect: Rect,
    powered: Boolean,
    state: DesktopCockpitSimState,
    leftSide: Boolean,
) {
    // Bezel with screws
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF2E3338), Color(0xFF14171A)), rect.top, rect.bottom),
        topLeft = Offset(rect.left - 4f, rect.top - 4f),
        size = Size(rect.width + 8f, rect.height + 8f),
        cornerRadius = CornerRadius(16f, 16f),
    )
    drawRoundRect(
        color = Color(0xFF0A0D10),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(14f, 14f),
    )
    val screenColor = if (powered) Color(0xFF082837) else Color(0xFF05080A)
    drawRoundRect(
        color = screenColor,
        topLeft = Offset(rect.left + 6f, rect.top + 6f),
        size = Size(rect.width - 12f, rect.height - 12f),
        cornerRadius = CornerRadius(10f, 10f),
    )
    if (!powered) {
        // Dead-glass reflection
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0x14FFFFFF), Color(0x00FFFFFF)),
                start = rect.topLeft,
                end = Offset(rect.left + rect.width * 0.5f, rect.bottom),
            ),
            topLeft = Offset(rect.left + 6f, rect.top + 6f),
            size = Size(rect.width - 12f, rect.height - 12f),
            cornerRadius = CornerRadius(10f, 10f),
        )
        return
    }

    val center = rect.center
    val inner = Rect(rect.left + 12f, rect.top + 12f, rect.right - 12f, rect.bottom - 12f)
    val horizonSplit = rect.top + 12f + inner.height * 0.42f

    // Attitude sphere - sky and ground with gradient depth
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF1E77B5), Color(0xFF4AA3D8)), inner.top, horizonSplit),
        topLeft = Offset(inner.left, inner.top),
        size = Size(inner.width, horizonSplit - inner.top),
    )
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF8A6437), Color(0xFF5E4423)), horizonSplit, inner.bottom),
        topLeft = Offset(inner.left, horizonSplit),
        size = Size(inner.width, inner.height * 0.38f),
    )
    // Horizon line + pitch ladder
    drawLine(Color.White, Offset(inner.left + 6f, horizonSplit), Offset(inner.right - 6f, horizonSplit), 2.5f)
    listOf(-2, -1, 1, 2).forEach { step ->
        val ly = horizonSplit - step * inner.height * 0.075f
        val halfLen = inner.width * (if (step % 2 == 0) 0.14f else 0.08f)
        drawLine(
            Color.White.copy(alpha = 0.85f),
            Offset(center.x - halfLen, ly),
            Offset(center.x + halfLen, ly),
            1.6f,
        )
    }
    // Aircraft reference symbol
    drawLine(Color(0xFFFFD24A), Offset(center.x - inner.width * 0.14f, horizonSplit), Offset(center.x - inner.width * 0.05f, horizonSplit), 4f, cap = StrokeCap.Round)
    drawLine(Color(0xFFFFD24A), Offset(center.x + inner.width * 0.05f, horizonSplit), Offset(center.x + inner.width * 0.14f, horizonSplit), 4f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFFFD24A), radius = 3f, center = Offset(center.x, horizonSplit))

    // Airspeed / altitude tapes
    fun tape(left: Float) {
        val tapeW = inner.width * 0.13f
        drawRect(Color(0xB3050E14), Offset(left, inner.top), Size(tapeW, inner.height * 0.80f))
        for (i in 0..5) {
            val ty = inner.top + i * inner.height * 0.152f
            drawLine(Color.White.copy(alpha = 0.7f), Offset(left, ty), Offset(left + tapeW * 0.28f, ty), 1.4f)
        }
        val pointerY = inner.top + inner.height * 0.38f
        drawRect(Color(0xFF0A0E11), Offset(left, pointerY - 7f), Size(tapeW, 14f))
        drawRect(Color(0xFF55C7FF), Offset(left, pointerY - 7f), Size(tapeW, 14f), style = Stroke(width = 1.5f))
    }
    tape(inner.left)
    tape(inner.right - inner.width * 0.13f)

    // Heading strip
    drawRect(Color(0xB3050E14), Offset(inner.left + inner.width * 0.2f, inner.bottom - inner.height * 0.135f), Size(inner.width * 0.6f, inner.height * 0.115f))
    for (i in 0..8) {
        val hx = inner.left + inner.width * (0.24f + i * 0.065f)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(hx, inner.bottom - inner.height * 0.13f), Offset(hx, inner.bottom - inner.height * (if (i % 2 == 0) 0.085f else 0.10f)), 1.3f)
    }
    drawLine(Color(0xFFE879F9), Offset(center.x, inner.bottom - inner.height * 0.135f), Offset(center.x, inner.bottom - inner.height * 0.02f), 2f)

    // Torque readout gauge
    drawRoundGauge(center.x, rect.bottom - rect.height * 0.17f, rect.height * 0.105f, state.torquePercent(leftSide) / 100f, Color(0xFF6BE675))

    // Screen glass reflection sweep
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF), Color(0x0DFFFFFF)),
            start = rect.topLeft,
            end = Offset(rect.right, rect.bottom),
        ),
        topLeft = Offset(rect.left + 6f, rect.top + 6f),
        size = Size(rect.width - 12f, rect.height - 12f),
        cornerRadius = CornerRadius(10f, 10f),
    )
}

private fun DrawScope.drawEngineStack(rect: Rect, state: DesktopCockpitSimState) {
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF171D23), Color(0xFF0C1015)), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(12f, 12f),
    )
    drawRoundRect(
        color = Color(0xFF2A343D),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 2f),
    )
    val barW = rect.width * 0.20f
    val values = listOf(
        state.torquePercent(true) / 100f,
        state.torquePercent(false) / 100f,
        if (state.leftFuelPressure) 0.82f else 0.18f,
        if (state.rightFuelPressure) 0.82f else 0.18f,
    )
    values.forEachIndexed { index, value ->
        val left = rect.left + rect.width * 0.10f + index * barW * 1.12f
        val top = rect.top + rect.height * 0.12f
        val height = rect.height * 0.72f
        drawRoundRect(Color(0xFF232A31), Offset(left, top), Size(barW, height), CornerRadius(6f, 6f))
        // Scale ticks
        for (tick in 1..3) {
            val ty = top + height * tick / 4f
            drawLine(Color(0x33FFFFFF), Offset(left, ty), Offset(left + barW, ty), 1.2f)
        }
        val accent = if (value > 0.5f) Color(0xFF6BE675) else Color(0xFFFFB23A)
        val fillH = height * value
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(accent, lerp(accent, Color.Black, 0.45f)),
                top + height - fillH,
                top + height,
            ),
            topLeft = Offset(left, top + height - fillH),
            size = Size(barW, fillH),
            cornerRadius = CornerRadius(6f, 6f),
        )
        // Value cap line
        drawLine(
            Color.White.copy(alpha = 0.85f),
            Offset(left + 1.5f, top + height - fillH),
            Offset(left + barW - 1.5f, top + height - fillH),
            2.4f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawElectricalPanel(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    val rect = Rect(w * 0.690f, h * 0.565f, w * 0.875f, h * 0.715f)

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF2B3035), Color(0xFF1B1F23)), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = Color(0xFF3B4248),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 1.5f),
    )
    val switches = listOf(
        state.batteryMaster,
        state.avionicsMaster,
        state.leftDcGenerator,
        state.rightDcGenerator,
    )
    switches.forEachIndexed { index, on ->
        val x = rect.left + rect.width * (0.18f + index * 0.20f)
        // Switch guard plate
        drawRoundRect(
            color = Color(0xFF15181B),
            topLeft = Offset(x - 13f, rect.top + rect.height * 0.30f),
            size = Size(26f, rect.height * 0.55f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawToggle(x, rect.top + rect.height * 0.48f, on)
        val lampCenter = Offset(x, rect.top + rect.height * 0.20f)
        if (on) drawCircle(Color(0x596BE675), radius = 9f, center = lampCenter)
        drawCircle(if (on) Color(0xFF6BE675) else Color(0xFF421B1B), radius = 5f, center = lampCenter)
        drawCircle(Color(0xFF0A0E11), radius = 5f, center = lampCenter, style = Stroke(width = 1.2f))
    }
}

private fun DrawScope.drawFirePanel(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    val rect = Rect(w * 0.405f, h * 0.675f, w * 0.525f, h * 0.750f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF1B2025), Color(0xFF101418)), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(10f, 10f),
    )
    drawRoundRect(
        color = Color(0xFF3A2226),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 1.5f),
    )
    drawFireHandle(rect.left + rect.width * 0.30f, rect.center.y, state.leftFireHandlePulled, state.leftEngineFire)
    drawFireHandle(rect.left + rect.width * 0.70f, rect.center.y, state.rightFireHandlePulled, state.rightEngineFire)
}

/* ---- Pedestal & controls ---------------------------------------------- */

private fun DrawScope.drawCenterPedestal(state: DesktopCockpitSimState) {
    val w = size.width
    val h = size.height
    val pedestal = Path().apply {
        moveTo(w * 0.405f, h * 0.710f)
        lineTo(w * 0.595f, h * 0.710f)
        lineTo(w * 0.660f, h)
        lineTo(w * 0.340f, h)
        close()
    }
    drawPath(pedestal, Brush.verticalGradient(listOf(Color(0xFF34383D), Color(0xFF0E1114)), h * 0.70f, h))
    drawPath(pedestal, Color(0xFF06080B), style = Stroke(width = 3f))
    // Side highlight
    drawLine(Color(0x40AFC4D4), Offset(w * 0.405f, h * 0.712f), Offset(w * 0.340f, h), 2f)

    // Power quadrant slots
    listOf(w * 0.470f, w * 0.520f).forEach { cx ->
        drawRoundRect(
            color = Color(0xFF07090C),
            topLeft = Offset(cx - 5f, h * 0.905f - 96f),
            size = Size(10f, 100f),
            cornerRadius = CornerRadius(5f, 5f),
        )
    }
    // Fuel lever slots
    listOf(w * 0.575f, w * 0.620f).forEach { cx ->
        drawRoundRect(
            color = Color(0xFF07090C),
            topLeft = Offset(cx - 4f, h * 0.875f - 58f),
            size = Size(8f, 62f),
            cornerRadius = CornerRadius(4f, 4f),
        )
    }
    // Flap gate arc
    drawArc(
        color = Color(0xFF07090C),
        startAngle = -60f,
        sweepAngle = 85f,
        useCenter = false,
        topLeft = Offset(w * 0.705f - 55f, h * 0.850f - 55f),
        size = Size(110f, 110f),
        style = Stroke(width = 9f, cap = StrokeCap.Round),
    )

    drawPowerLever(w * 0.470f, h * 0.905f, state.leftPower)
    drawPowerLever(w * 0.520f, h * 0.905f, state.rightPower)
    drawFuelLever(w * 0.575f, h * 0.875f, state.leftFuelLeverOn)
    drawFuelLever(w * 0.620f, h * 0.875f, state.rightFuelLeverOn)
    drawFlapLever(w * 0.705f, h * 0.850f, state.flaps)
}

private fun DrawScope.drawYokes() {
    val w = size.width
    val h = size.height
    drawYoke(w * 0.255f, h * 0.820f)
    drawYoke(w * 0.745f, h * 0.820f)
}

private fun DrawScope.drawYoke(cx: Float, cy: Float) {
    // Column with shading
    drawLine(Color(0xFF0B0E11), Offset(cx, cy - 42f), Offset(cx, cy + 45f), 12f, cap = StrokeCap.Round)
    drawLine(Color(0x552F3A43), Offset(cx - 2.5f, cy - 40f), Offset(cx - 2.5f, cy + 42f), 2.5f, cap = StrokeCap.Round)
    // Horn
    drawRoundRect(
        color = Color(0xFF12161A),
        topLeft = Offset(cx - 58f, cy - 36f),
        size = Size(116f, 72f),
        cornerRadius = CornerRadius(32f, 32f),
        style = Stroke(width = 14f),
    )
    drawRoundRect(
        color = Color(0x33404A52),
        topLeft = Offset(cx - 58f, cy - 36f),
        size = Size(116f, 72f),
        cornerRadius = CornerRadius(32f, 32f),
        style = Stroke(width = 4f),
    )
    // Hub
    drawCircle(Color(0xFF2A2F33), radius = 15f, center = Offset(cx, cy))
    drawCircle(Color(0xFF43494E), radius = 15f, center = Offset(cx, cy), style = Stroke(width = 2f))
    drawCircle(Color(0x66FFFFFF), radius = 4f, center = Offset(cx - 4f, cy - 5f))
}

private fun DrawScope.drawPowerLever(cx: Float, baseY: Float, power: CockpitPowerLeverPosition) {
    val range = 92f
    val value = power.ordinal.toFloat() / (CockpitPowerLeverPosition.values().lastIndex).coerceAtLeast(1)
    val knobY = baseY - value * range
    drawLine(Color(0xFF0A0C0F), Offset(cx, baseY), Offset(cx, knobY), 7f, cap = StrokeCap.Round)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFD7DEE3), Color(0xFF8B959C)), knobY - 22f, knobY + 22f),
        topLeft = Offset(cx - 10f, knobY - 22f),
        size = Size(20f, 44f),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRoundRect(
        color = Color(0xFF3E464C),
        topLeft = Offset(cx - 10f, knobY - 22f),
        size = Size(20f, 44f),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.6f),
    )
    // Grip lines
    for (i in 0..2) {
        val gy = knobY - 8f + i * 8f
        drawLine(Color(0x59262C31), Offset(cx - 6f, gy), Offset(cx + 6f, gy), 1.8f)
    }
}

private fun DrawScope.drawFuelLever(cx: Float, baseY: Float, on: Boolean) {
    val knobY = if (on) baseY - 54f else baseY - 10f
    drawLine(Color(0xFF0A0C0F), Offset(cx, baseY), Offset(cx, knobY), 5f, cap = StrokeCap.Round)
    val knobColor = if (on) Color(0xFF5FD37A) else Color(0xFF9A3A34)
    drawCircle(knobColor.copy(alpha = 0.35f), radius = 13f, center = Offset(cx, knobY))
    drawCircle(knobColor, radius = 10f, center = Offset(cx, knobY))
    drawCircle(Color(0xFF0A0E11), radius = 10f, center = Offset(cx, knobY), style = Stroke(width = 1.5f))
    drawCircle(Color.White.copy(alpha = 0.35f), radius = 3f, center = Offset(cx - 3f, knobY - 3f))
}

private fun DrawScope.drawFlapLever(cx: Float, baseY: Float, flaps: CockpitFlapSetting) {
    val angle = when (flaps) {
        CockpitFlapSetting.UP -> -0.95f
        CockpitFlapSetting.TAKEOFF -> -0.40f
        CockpitFlapSetting.LANDING -> 0.35f
    }
    val end = Offset(cx + cos(angle) * 55f, baseY + sin(angle) * 55f)
    drawLine(Color(0xFF0A0C0F), Offset(cx, baseY), end, 7f, cap = StrokeCap.Round)
    drawCircle(Color(0x59FFD166), radius = 13f, center = end)
    drawCircle(Color(0xFFFFD166), radius = 10f, center = end)
    drawCircle(Color(0xFF0A0E11), radius = 10f, center = end, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawToggle(cx: Float, cy: Float, on: Boolean) {
    val top = if (on) cy - 22f else cy + 2f
    drawLine(Color(0xFF0A0C0F), Offset(cx, cy + 26f), Offset(cx, top), 5f, cap = StrokeCap.Round)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                if (on) Color(0xFFF2F6F8) else Color(0xFF7C838A),
                if (on) Color(0xFFB9C3CA) else Color(0xFF4E555B),
            ),
            top - 10f,
            top + 12f,
        ),
        topLeft = Offset(cx - 9f, top - 10f),
        size = Size(18f, 22f),
        cornerRadius = CornerRadius(7f, 7f),
    )
    drawRoundRect(
        color = Color(0xFF14181B),
        topLeft = Offset(cx - 9f, top - 10f),
        size = Size(18f, 22f),
        cornerRadius = CornerRadius(7f, 7f),
        style = Stroke(width = 1.4f),
    )
}

private fun DrawScope.drawFireHandle(cx: Float, cy: Float, pulled: Boolean, fire: Boolean) {
    val y = if (pulled) cy - 18f else cy
    val color = if (fire && !pulled) Color(0xFFFF3030) else Color(0xFFB32020)
    drawLine(Color(0xFF0A0C0F), Offset(cx, cy + 25f), Offset(cx, y), 5f, cap = StrokeCap.Round)
    if (fire && !pulled) {
        drawRoundRect(
            color = color.copy(alpha = 0.4f),
            topLeft = Offset(cx - 20f, y - 16f),
            size = Size(40f, 32f),
            cornerRadius = CornerRadius(10f, 10f),
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(lerp(color, Color.White, 0.18f), lerp(color, Color.Black, 0.30f)), y - 12f, y + 12f),
        topLeft = Offset(cx - 16f, y - 12f),
        size = Size(32f, 24f),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRoundRect(
        color = Color(0xFF0A0E11),
        topLeft = Offset(cx - 16f, y - 12f),
        size = Size(32f, 24f),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.5f),
    )
}

/* ---- Gauges ------------------------------------------------------------ */

private fun DrawScope.drawRoundGauge(
    cx: Float,
    cy: Float,
    radius: Float,
    value: Float,
    accent: Color,
) {
    val clamped = value.coerceIn(0f, 1f)
    val center = Offset(cx, cy)

    // Bezel - light top-left, dark bottom-right for a machined ring
    drawCircle(Color(0xFF31383F), radius = radius * 1.10f, center = center)
    drawArc(
        color = Color(0x59FFFFFF),
        startAngle = 180f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(cx - radius * 1.06f, cy - radius * 1.06f),
        size = Size(radius * 2.12f, radius * 2.12f),
        style = Stroke(width = radius * 0.07f, cap = StrokeCap.Round),
    )
    drawArc(
        color = Color(0x73000000),
        startAngle = 0f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(cx - radius * 1.06f, cy - radius * 1.06f),
        size = Size(radius * 2.12f, radius * 2.12f),
        style = Stroke(width = radius * 0.07f, cap = StrokeCap.Round),
    )

    // Face
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF14181E), Color(0xFF07090C)), center, radius),
        radius = radius,
        center = center,
    )

    // Colour range arcs: green / yellow / red
    fun rangeArc(from: Float, to: Float, color: Color) {
        drawArc(
            color = color,
            startAngle = 135f + 270f * from,
            sweepAngle = 270f * (to - from),
            useCenter = false,
            topLeft = Offset(cx - radius * 0.86f, cy - radius * 0.86f),
            size = Size(radius * 1.72f, radius * 1.72f),
            style = Stroke(width = radius * 0.055f),
        )
    }
    rangeArc(0.00f, 0.70f, Color(0xB34AD186))
    rangeArc(0.70f, 0.88f, Color(0xB3FFD166))
    rangeArc(0.88f, 1.00f, Color(0xB3FF5C5C))

    // Tick marks
    for (i in 0..10) {
        val frac = i / 10f
        val a = Math.toRadians((135f + 270f * frac).toDouble())
        val isMajor = i % 2 == 0
        val inner = radius * if (isMajor) 0.70f else 0.76f
        val outer = radius * 0.82f
        drawLine(
            Color.White.copy(alpha = if (isMajor) 0.85f else 0.5f),
            Offset(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner),
            Offset(cx + cos(a).toFloat() * outer, cy + sin(a).toFloat() * outer),
            if (isMajor) radius * 0.045f else radius * 0.028f,
            cap = StrokeCap.Round,
        )
    }

    // Value arc
    drawArc(
        color = accent,
        startAngle = 135f,
        sweepAngle = 270f * clamped,
        useCenter = false,
        topLeft = Offset(cx - radius * 0.60f, cy - radius * 0.60f),
        size = Size(radius * 1.20f, radius * 1.20f),
        style = Stroke(width = radius * 0.09f, cap = StrokeCap.Round),
    )

    // Needle with tail and hub
    val needleAngle = Math.toRadians((135f + 270f * clamped).toDouble())
    val tip = Offset(
        x = cx + cos(needleAngle).toFloat() * radius * 0.66f,
        y = cy + sin(needleAngle).toFloat() * radius * 0.66f,
    )
    val tail = Offset(
        x = cx - cos(needleAngle).toFloat() * radius * 0.16f,
        y = cy - sin(needleAngle).toFloat() * radius * 0.16f,
    )
    drawLine(Color(0x66000000), Offset(cx + radius * 0.02f, cy + radius * 0.03f), Offset(tip.x + radius * 0.02f, tip.y + radius * 0.03f), radius * 0.07f, cap = StrokeCap.Round)
    drawLine(Color.White, tail, tip, radius * 0.07f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFB9C3CA), radius = radius * 0.12f, center = center)
    drawCircle(Color(0xFF31383F), radius = radius * 0.055f, center = center)

    // Glass glint
    drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = 195f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(cx - radius * 0.55f, cy - radius * 0.55f),
        size = Size(radius * 1.1f, radius * 1.1f),
        style = Stroke(width = radius * 0.22f, cap = StrokeCap.Round),
    )
}

/* ---- Overlays ----------------------------------------------------------- */

private fun DrawScope.drawVignette() {
    val w = size.width
    val h = size.height
    val radius = max(w, h) * 0.78f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x00000000), Color(0x00000000), Color(0x59000000)),
            center = Offset(w / 2f, h * 0.55f),
            radius = radius,
        ),
        size = Size(w, h),
    )
}

private fun DrawScope.drawSelectedTarget(selectedTargetId: String) {
    val rect = FlightSimTargetRects[selectedTargetId] ?: return
    val target = Rect(
        left = rect.left * size.width,
        top = rect.top * size.height,
        right = rect.right * size.width,
        bottom = rect.bottom * size.height,
    )
    drawRoundRect(
        color = Color(0x4455C7FF),
        topLeft = target.topLeft,
        size = target.size,
        cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = Color(0xFF55C7FF),
        topLeft = target.topLeft,
        size = target.size,
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = 4f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.32f),
        topLeft = Offset(target.left + 6f, target.top + 6f),
        size = Size(target.width - 12f, 3f),
        cornerRadius = CornerRadius(2f, 2f),
    )
}

private fun DrawScope.drawInteractionHints(selectedTargetId: String) {
    FlightSimControlZones
        .filter { it.targetId == selectedTargetId }
        .forEach { zone ->
            val rect = Rect(
                left = zone.rect.left * size.width,
                top = zone.rect.top * size.height,
                right = zone.rect.right * size.width,
                bottom = zone.rect.bottom * size.height,
            )
            drawRoundRect(
                color = Color(0xFFBFEAFF),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 2.5f),
            )
            drawCircle(
                color = Color(0x8855C7FF),
                radius = 5.5f,
                center = rect.center,
            )
        }
}
