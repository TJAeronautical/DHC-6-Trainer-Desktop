package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/* =====================================================================
   SimCockpitScreen - the interactive Twin Otter flight deck.

   100% Compose Canvas: no OpenGL, no jME, no native rendering, so it
   cannot crash the way the old free-flight scene did. Every gauge is
   driven by TwinOtterSimModel, so the panel reacts like a DHC-6:
   pull the prop levers back and torque rises, feather a dead prop and
   the windmill drag disappears, introduce fuel early and ITT spikes.
   ===================================================================== */

private object DeckColors {
    val PanelBg = Color(0xFF10151B)
    val PanelEdge = Color(0xFF2A3844)
    val GaugeFace = Color(0xFF060A0E)
    val GaugeRim = Color(0xFF3A4A58)
    val Tick = Color(0xFFB9C7D2)
    val NeedleL = Color(0xFFEAF4FF)
    val NeedleR = Color(0xFFFFB000)
    val GreenArc = Color(0xFF2ECC71)
    val RedLine = Color(0xFFFF3B30)
    val Amber = Color(0xFFFFB000)
    val LampRedOn = Color(0xFFFF3B30)
    val LampAmberOn = Color(0xFFFFB000)
    val LampOff = Color(0xFF232C34)
    val LeverPower = Color(0xFF9AA7B5)
    val LeverProp = Color(0xFF3E7BFA)
    val LeverFuel = Color(0xFFE04338)
}

@Composable
internal fun SimCockpitScreen() {
    val sim = remember { TwinOtterSimModel().also { it.resetReadyForTakeoff() } }
    var snap by remember { mutableStateOf(sim.snapshot) }

    var drill by remember { mutableStateOf(SimDrill.FREE_PRACTICE) }
    var drillRun by remember { mutableIntStateOf(0) }
    var failureInjected by remember { mutableStateOf(false) }
    var drillRecorded by remember { mutableStateOf(false) }

    // Fixed-timestep sim loop tied to the Compose frame clock.
    LaunchedEffect(sim) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) sim.update((now - last) / 1_000_000_000f)
                last = now
                snap = sim.snapshot
            }
        }
    }

    // Scripted failure injection + completion recording for the active drill.
    val gates = remember(drill) { drill.gates() }
    val gateStates = gates.map { it.passed(sim) }
    val allPassed = gateStates.isNotEmpty() && gateStates.all { it }
    LaunchedEffect(drill, drillRun, snap.elapsedSec) {
        val failure = drill.failure
        if (failure != null && !failureInjected && snap.elapsedSec >= drill.armFailureAfterSec) {
            sim.controls.failures.add(failure)
            failureInjected = true
        }
    }
    LaunchedEffect(allPassed, drill, drillRun) {
        if (allPassed && !drillRecorded && drill != SimDrill.FREE_PRACTICE) {
            DesktopProgressStore.record(
                AttemptType.MCC,
                "Sim: ${drill.title}",
                gates.size,
                gates.size,
                snap.elapsedSec,
            )
            drillRecorded = true
        }
    }

    fun startDrill(next: SimDrill) {
        drill = next
        drillRun++
        failureInjected = false
        drillRecorded = false
        next.setup(sim)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── Main flight deck ─────────────────────────────────────────────
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeckTopBar(snap, onMasterReset = { sim.controls.masterCautionReset = true })
            AnnunciatorGrid(snap)

            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EngineGaugeCluster(snap, Modifier.weight(1.32f))
                FlightInstrumentColumn(snap, Modifier.weight(0.62f))
            }

            PedestalPanel(sim, snap)
        }

        // ── Overhead + instructor panel ─────────────────────────────────
        SidePanel(
            sim = sim,
            snap = snap,
            drill = drill,
            gates = gates,
            gateStates = gateStates,
            allPassed = allPassed,
            failureInjected = failureInjected,
            onStartDrill = ::startDrill,
            modifier = Modifier.width(308.dp).fillMaxHeight(),
        )
    }
}

/* ===================================================================== */
/* Top bar: masters + digital flight data                                */
/* ===================================================================== */

@Composable
private fun DeckTopBar(snap: TwinOtterSnapshot, onMasterReset: () -> Unit) {
    val blink = snap.elapsedSec % 2 == 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.PanelBg)
            .border(BorderStroke(1.dp, DeckColors.PanelEdge), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MasterLamp(
            label = "WARN",
            lit = snap.masterWarning && blink,
            color = DeckColors.LampRedOn,
            onClick = onMasterReset,
        )
        MasterLamp(
            label = "CAUT",
            lit = snap.masterCaution,
            color = DeckColors.LampAmberOn,
            onClick = onMasterReset,
        )
        Spacer(Modifier.width(4.dp))
        DigitalReadout("IAS", "${snap.iasKt.roundToInt()} kt")
        DigitalReadout("ALT", "${snap.altitudeFt.roundToInt()} ft")
        DigitalReadout("V/S", "${snap.verticalSpeedFpm.roundToInt()} fpm")
        DigitalReadout("FLAP", "${snap.flapsDeg.compactDeg()}")
        DigitalReadout("BUS", if (snap.busPowered) "%.1fV".format(snap.busVolts) else "OFF")
        Spacer(Modifier.weight(1f))
        StatusChip(if (snap.onGround) "ON GROUND" else "AIRBORNE", snap.onGround)
        if (snap.stallWarning) StatusChip("STALL", false, DeckColors.LampRedOn)
        if (snap.autofeatherActive) StatusChip("A/F SELECT", true, DeckColors.GreenArc)
    }
}

private fun Float.compactDeg(): String =
    if (this % 1f == 0f) "${toInt()}°" else "%.1f°".format(this)

@Composable
private fun MasterLamp(label: String, lit: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (lit) color else DeckColors.LampOff)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.7f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (lit) Color.Black else color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DigitalReadout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Dhc6DesktopColors.TextSubtle, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(value, color = Color(0xFF7FE7A2), fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StatusChip(label: String, good: Boolean, override: Color? = null) {
    val color = override ?: if (good) DeckColors.GreenArc else DeckColors.Amber
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, color), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

/* ===================================================================== */
/* Annunciator grid                                                      */
/* ===================================================================== */

private val AnnunciatorLamps: List<Pair<String, Boolean>> = listOf(
    "L ENG FIRE" to true, "R ENG FIRE" to true, "STALL" to true,
    "L HOT START" to true, "R HOT START" to true,
    "DC BUS OFF" to false, "L DC GEN" to false, "R DC GEN" to false,
    "L FUEL PRESS" to false, "R FUEL PRESS" to false,
    "L OIL PRESS" to false, "R OIL PRESS" to false,
    "L TORQUE" to false, "R TORQUE" to false,
    "L AUTOFEATHER" to false, "R AUTOFEATHER" to false,
    "CROSSFEED OPEN" to false, "BETA RANGE" to false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnnunciatorGrid(snap: TwinOtterSnapshot) {
    val active = remember(snap.annunciators) { snap.annunciators.map { it.label }.toSet() }
    FlowRow(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeckColors.PanelBg)
            .border(BorderStroke(1.dp, DeckColors.PanelEdge), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AnnunciatorLamps.forEach { (label, warning) ->
            val lit = label in active
            val litColor = if (warning) DeckColors.LampRedOn else DeckColors.LampAmberOn
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (lit) litColor else DeckColors.LampOff)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (lit) Color.Black else Color(0xFF54646F),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

/* ===================================================================== */
/* Engine gauges                                                         */
/* ===================================================================== */

@Composable
private fun EngineGaugeCluster(snap: TwinOtterSnapshot, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.PanelBg)
            .border(BorderStroke(1.dp, DeckColors.PanelEdge), RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
            DualGauge(
                label = "TORQUE", unit = "PSI",
                min = 0f, max = 60f, redline = TwinOtterSimModel.TorqueRedlinePsi,
                greenFrom = 10f, greenTo = 50f,
                valueL = snap.left.torquePsi, valueR = snap.right.torquePsi,
                fmt = { "%.1f".format(it) },
                modifier = Modifier.weight(1f),
            )
            DualGauge(
                label = "PROP RPM", unit = "NP",
                min = 0f, max = 2400f, redline = TwinOtterSimModel.NpMax,
                greenFrom = 1600f, greenTo = 2200f,
                valueL = snap.left.propRpm, valueR = snap.right.propRpm,
                fmt = { "${it.roundToInt()}" },
                modifier = Modifier.weight(1f),
            )
            DualGauge(
                label = "ITT", unit = "°C",
                min = 0f, max = 1100f, redline = TwinOtterSimModel.IttRedlineC,
                greenFrom = 400f, greenTo = 695f,
                valueL = snap.left.ittC, valueR = snap.right.ittC,
                fmt = { "${it.roundToInt()}" },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
            DualGauge(
                label = "GAS GEN", unit = "% NG",
                min = 0f, max = 110f, redline = 101.5f,
                greenFrom = 52f, greenTo = 96.5f,
                valueL = snap.left.ngPercent, valueR = snap.right.ngPercent,
                fmt = { "%.1f".format(it) },
                modifier = Modifier.weight(1f),
            )
            DualGauge(
                label = "FUEL FLOW", unit = "PPH",
                min = 0f, max = 700f, redline = null,
                greenFrom = 80f, greenTo = 580f,
                valueL = snap.left.fuelFlowPph, valueR = snap.right.fuelFlowPph,
                fmt = { "${it.roundToInt()}" },
                modifier = Modifier.weight(1f),
            )
            DualGauge(
                label = "OIL PRESS", unit = "PSI",
                min = 0f, max = 120f, redline = null,
                greenFrom = 80f, greenTo = 100f,
                valueL = snap.left.oilPressPsi, valueR = snap.right.oilPressPsi,
                fmt = { "${it.roundToInt()}" },
                modifier = Modifier.weight(1f),
            )
        }
        // Per-engine status strip.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            EngineStatusTag("L", snap.left)
            EngineStatusTag("R", snap.right)
        }
    }
}

@Composable
private fun EngineStatusTag(side: String, e: SimEngineSnapshot) {
    val text = when {
        e.fire -> "FIRE"
        e.starting -> "STARTING"
        e.autofeathered -> "AUTOFEATHERED"
        e.feathered && !e.running -> "SECURED / FEATHERED"
        e.feathered -> "FEATHERED"
        e.running -> "RUNNING"
        e.propRpm > 200f -> "WINDMILLING"
        else -> "SHUT DOWN"
    }
    val color = when {
        e.fire -> DeckColors.LampRedOn
        e.running && !e.feathered -> DeckColors.GreenArc
        e.starting -> DeckColors.Amber
        else -> Dhc6DesktopColors.TextSubtle
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("ENG $side", color = Dhc6DesktopColors.TextSubtle, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DualGauge(
    label: String,
    unit: String,
    min: Float,
    max: Float,
    redline: Float?,
    greenFrom: Float,
    greenTo: Float,
    valueL: Float,
    valueR: Float,
    fmt: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Column(modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val d = min(size.width, size.height)
            val radius = d / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawGaugeFace(center, radius, min, max, redline, greenFrom, greenTo, measurer, label, unit)
            drawNeedle(center, radius, min, max, valueL, DeckColors.NeedleL, 0.80f, 3.4f)
            drawNeedle(center, radius, min, max, valueR, DeckColors.NeedleR, 0.62f, 3.4f)
            drawCircle(DeckColors.GaugeRim, radius * 0.07f, center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("L ${fmt(valueL)}", color = DeckColors.NeedleL, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("R ${fmt(valueR)}", color = DeckColors.NeedleR, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

private const val GaugeStartDeg = 135f
private const val GaugeSweepDeg = 270f

private fun gaugeAngleDeg(min: Float, max: Float, value: Float): Float {
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    return GaugeStartDeg + GaugeSweepDeg * frac
}

private fun DrawScope.drawGaugeFace(
    center: Offset,
    radius: Float,
    min: Float,
    max: Float,
    redline: Float?,
    greenFrom: Float,
    greenTo: Float,
    measurer: TextMeasurer,
    label: String,
    unit: String,
) {
    drawCircle(DeckColors.GaugeFace, radius, center)
    drawCircle(DeckColors.GaugeRim, radius, center, style = Stroke(width = radius * 0.045f))

    val arcRect = androidx.compose.ui.geometry.Rect(
        center.x - radius * 0.86f, center.y - radius * 0.86f,
        center.x + radius * 0.86f, center.y + radius * 0.86f,
    )
    // Green operating band.
    val gStart = gaugeAngleDeg(min, max, greenFrom)
    val gEnd = gaugeAngleDeg(min, max, greenTo)
    drawArc(
        DeckColors.GreenArc,
        startAngle = gStart,
        sweepAngle = gEnd - gStart,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = radius * 0.05f, cap = StrokeCap.Butt),
    )
    // Redline tick.
    if (redline != null) {
        val a = Math.toRadians(gaugeAngleDeg(min, max, redline).toDouble())
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(
            DeckColors.RedLine,
            center + dir * (radius * 0.74f),
            center + dir * (radius * 0.94f),
            strokeWidth = radius * 0.055f,
        )
    }
    // Major ticks.
    val ticks = 10
    for (t in 0..ticks) {
        val a = Math.toRadians((GaugeStartDeg + GaugeSweepDeg * t / ticks).toDouble())
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(
            DeckColors.Tick,
            center + dir * (radius * 0.78f),
            center + dir * (radius * 0.90f),
            strokeWidth = radius * 0.02f,
        )
    }
    // Label + unit.
    val labelLayout = measurer.measure(
        label,
        TextStyle(color = DeckColors.Tick, fontSize = (radius * 0.11f).sp, fontWeight = FontWeight.Black),
    )
    drawText(
        labelLayout,
        topLeft = Offset(center.x - labelLayout.size.width / 2f, center.y + radius * 0.28f),
    )
    val unitLayout = measurer.measure(
        unit,
        TextStyle(color = Dhc6DesktopColors.TextSubtle, fontSize = (radius * 0.10f).sp, fontWeight = FontWeight.Bold),
    )
    drawText(
        unitLayout,
        topLeft = Offset(center.x - unitLayout.size.width / 2f, center.y + radius * 0.46f),
    )
}

private fun DrawScope.drawNeedle(
    center: Offset,
    radius: Float,
    min: Float,
    max: Float,
    value: Float,
    color: Color,
    lengthFrac: Float,
    width: Float,
) {
    val a = Math.toRadians(gaugeAngleDeg(min, max, value).toDouble())
    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
    drawLine(
        color,
        center - dir * (radius * 0.10f),
        center + dir * (radius * lengthFrac),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

/* ===================================================================== */
/* Flight instruments                                                    */
/* ===================================================================== */

@Composable
private fun FlightInstrumentColumn(snap: TwinOtterSnapshot, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.PanelBg)
            .border(BorderStroke(1.dp, DeckColors.PanelEdge), RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SingleGauge(
            label = "AIRSPEED", unit = "KT",
            min = 0f, max = 200f, redline = 170f,
            greenFrom = 58f, greenTo = 160f,
            value = snap.iasKt,
            fmt = { "${it.roundToInt()}" },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        SingleGauge(
            label = "ALTITUDE", unit = "FT x100",
            min = 0f, max = 100f, redline = null,
            greenFrom = 0f, greenTo = 0f,
            value = (snap.altitudeFt / 100f) % 100f,
            fmt = { "${snap.altitudeFt.roundToInt()}" },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        SingleGauge(
            label = "VERT SPEED", unit = "FPM",
            min = -2000f, max = 2000f, redline = null,
            greenFrom = 0f, greenTo = 0f,
            value = snap.verticalSpeedFpm.coerceIn(-2000f, 2000f),
            fmt = { "${it.roundToInt()}" },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DigitalReadout("PITCH", "%.1f°".format(snap.pitchDeg))
            DigitalReadout("AOA", "%.1f°".format(snap.aoaDeg))
            DigitalReadout("HYD", "${snap.hydraulicPsi.roundToInt()}")
        }
    }
}

@Composable
private fun SingleGauge(
    label: String,
    unit: String,
    min: Float,
    max: Float,
    redline: Float?,
    greenFrom: Float,
    greenTo: Float,
    value: Float,
    fmt: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val d = min(size.width, size.height)
            val radius = d / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawGaugeFace(center, radius, min, max, redline, greenFrom, greenTo, measurer, label, unit)
            drawNeedle(center, radius, min, max, value, DeckColors.NeedleL, 0.80f, 3.6f)
            drawCircle(DeckColors.GaugeRim, radius * 0.07f, center)
        }
        Text(fmt(value), color = Color(0xFF7FE7A2), fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

/* ===================================================================== */
/* Pedestal: levers, flaps, pitch                                        */
/* ===================================================================== */

@Composable
private fun PedestalPanel(sim: TwinOtterSimModel, snap: TwinOtterSnapshot) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.PanelBg)
            .border(BorderStroke(1.dp, DeckColors.PanelEdge), RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Power levers with beta/reverse zone below idle.
        LeverPair(
            title = "POWER",
            color = DeckColors.LeverPower,
            valueL = (sim.controls.powerLever[0] + 0.3f) / 1.3f,
            valueR = (sim.controls.powerLever[1] + 0.3f) / 1.3f,
            zoneFrac = 0.3f / 1.3f,
            zoneLabel = "REV",
            onChangeL = { sim.controls.powerLever[0] = it * 1.3f - 0.3f },
            onChangeR = { sim.controls.powerLever[1] = it * 1.3f - 0.3f },
            modifier = Modifier.weight(1.05f),
        )
        LeverPair(
            title = "PROP",
            color = DeckColors.LeverProp,
            valueL = sim.controls.propLever[0],
            valueR = sim.controls.propLever[1],
            zoneFrac = TwinOtterSimModel.PropFeatherDetent,
            zoneLabel = "FTHR",
            onChangeL = { sim.controls.propLever[0] = it },
            onChangeR = { sim.controls.propLever[1] = it },
            modifier = Modifier.weight(1.05f),
        )
        // Fuel (condition) levers: two-position.
        Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
            PedestalTitle("FUEL")
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FuelLever("L", sim.controls.fuelLever[0]) {
                    sim.controls.fuelLever[0] = !sim.controls.fuelLever[0]
                }
                FuelLever("R", sim.controls.fuelLever[1]) {
                    sim.controls.fuelLever[1] = !sim.controls.fuelLever[1]
                }
            }
        }
        // Flaps.
        Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
            PedestalTitle("FLAPS")
            Spacer(Modifier.height(6.dp))
            SimFlapDetents.forEachIndexed { i, deg ->
                val sel = sim.controls.flapsIndex == i
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Dhc6DesktopColors.AccentStrong else DeckColors.LampOff)
                        .clickable { sim.controls.flapsIndex = i }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        deg.compactDeg(),
                        color = if (sel) Color.White else Dhc6DesktopColors.TextSubtle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        // Pitch + brakes.
        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
            PedestalTitle("PITCH CMD  ${"%.1f°".format(sim.controls.pitchCommandDeg)}")
            Slider(
                value = sim.controls.pitchCommandDeg,
                onValueChange = { sim.controls.pitchCommandDeg = it },
                valueRange = -5f..15f,
                colors = SliderDefaults.colors(
                    thumbColor = Dhc6DesktopColors.Accent,
                    activeTrackColor = Dhc6DesktopColors.AccentStrong,
                    inactiveTrackColor = DeckColors.LampOff,
                ),
            )
            Text(
                "Nose down  <->  nose up",
                color = Dhc6DesktopColors.TextSubtle,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(8.dp))
            val brakes = sim.controls.brakes
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (brakes) Dhc6DesktopColors.Red.copy(alpha = 0.35f) else DeckColors.LampOff)
                    .border(
                        BorderStroke(1.dp, if (brakes) Dhc6DesktopColors.Red else DeckColors.PanelEdge),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { sim.controls.brakes = !sim.controls.brakes }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (brakes) "PARK BRAKE SET" else "BRAKES OFF",
                    color = if (brakes) Color(0xFFFF8A80) else Dhc6DesktopColors.TextSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (snap.onGround) "Rotate: >64 kt + pitch up" else "Flying - pitch trades speed for climb",
                color = Dhc6DesktopColors.TextSubtle,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun PedestalTitle(text: String) {
    Text(text, color = DeckColors.Tick, fontSize = 11.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun LeverPair(
    title: String,
    color: Color,
    valueL: Float,
    valueR: Float,
    zoneFrac: Float,
    zoneLabel: String,
    onChangeL: (Float) -> Unit,
    onChangeR: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PedestalTitle(title)
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VerticalLever("L", valueL, color, zoneFrac, zoneLabel, onChangeL)
            VerticalLever("R", valueR, color, zoneFrac, zoneLabel, onChangeR)
        }
    }
}

@Composable
private fun VerticalLever(
    side: String,
    value: Float,
    color: Color,
    zoneFrac: Float,
    zoneLabel: String,
    onChange: (Float) -> Unit,
) {
    var heightPx by remember { mutableStateOf(1f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .width(44.dp)
                .weight(1f)
                .padding(vertical = 4.dp)
                .pointerInput(Unit) {
                    // Absolute positioning: the knob follows the pointer, so no
                    // state is captured across recompositions.
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        onChange((1f - change.position.y / heightPx).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onChange((1f - offset.y / heightPx).coerceIn(0f, 1f))
                    }
                },
        ) {
            heightPx = size.height
            val trackX = size.width / 2f
            // Track.
            drawLine(
                DeckColors.PanelEdge,
                Offset(trackX, 0f),
                Offset(trackX, size.height),
                strokeWidth = 7f,
                cap = StrokeCap.Round,
            )
            // Special zone (reverse / feather) at the bottom of the travel.
            drawLine(
                DeckColors.RedLine.copy(alpha = 0.65f),
                Offset(trackX, size.height * (1f - zoneFrac)),
                Offset(trackX, size.height),
                strokeWidth = 7f,
                cap = StrokeCap.Round,
            )
            // Knob.
            val knobY = size.height * (1f - value.coerceIn(0f, 1f))
            drawRoundRect(
                color = color,
                topLeft = Offset(trackX - size.width * 0.38f, knobY - 9f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.76f, 18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
        }
        Text(
            if (value <= zoneFrac + 0.001f && zoneFrac > 0f) zoneLabel else side,
            color = if (value <= zoneFrac + 0.001f && zoneFrac > 0f) DeckColors.RedLine else Dhc6DesktopColors.TextSubtle,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun FuelLever(side: String, on: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(44.dp)
                .weight(1f)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (on) DeckColors.LeverFuel.copy(alpha = 0.30f) else DeckColors.LampOff)
                .border(
                    BorderStroke(1.dp, if (on) DeckColors.LeverFuel else DeckColors.PanelEdge),
                    RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onToggle),
            contentAlignment = if (on) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(DeckColors.LeverFuel),
            )
        }
        Text(
            "$side ${if (on) "ON" else "OFF"}",
            color = if (on) DeckColors.LeverFuel else Dhc6DesktopColors.TextSubtle,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/* ===================================================================== */
/* Side panel: overhead systems + instructor station                      */
/* ===================================================================== */

private enum class SidePanelTab { OVERHEAD, INSTRUCTOR }

@Composable
private fun SidePanel(
    sim: TwinOtterSimModel,
    snap: TwinOtterSnapshot,
    drill: SimDrill,
    gates: List<SimDrillGate>,
    gateStates: List<Boolean>,
    allPassed: Boolean,
    failureInjected: Boolean,
    onStartDrill: (SimDrill) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(SidePanelTab.INSTRUCTOR) }
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.PanelBg)
            .border(BorderStroke(1.dp, DeckColors.PanelEdge), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SidePanelTab.entries.forEach { t ->
                val sel = t == tab
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Dhc6DesktopColors.AccentStrong else DeckColors.LampOff)
                        .clickable { tab = t }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (t == SidePanelTab.OVERHEAD) "OVERHEAD" else "INSTRUCTOR",
                        color = if (sel) Color.White else Dhc6DesktopColors.TextSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                SidePanelTab.OVERHEAD -> OverheadPanel(sim, snap)
                SidePanelTab.INSTRUCTOR -> InstructorPanel(
                    sim, snap, drill, gates, gateStates, allPassed, failureInjected, onStartDrill,
                )
            }
        }
    }
}

@Composable
private fun OverheadPanel(sim: TwinOtterSimModel, snap: TwinOtterSnapshot) {
    PanelSectionLabel("ELECTRICAL")
    SimToggleRow("Battery master", sim.controls.batteryMaster) { sim.controls.batteryMaster = it }
    SimToggleRow("Avionics master", sim.controls.avionicsMaster) { sim.controls.avionicsMaster = it }
    SimToggleRow("L DC generator", sim.controls.generator[0]) { sim.controls.generator[0] = it }
    SimToggleRow("R DC generator", sim.controls.generator[1]) { sim.controls.generator[1] = it }

    PanelSectionLabel("FUEL")
    SimToggleRow("L fwd boost pump", sim.controls.boostPumpFwd[0]) { sim.controls.boostPumpFwd[0] = it }
    SimToggleRow("L aft boost pump", sim.controls.boostPumpAft[0]) { sim.controls.boostPumpAft[0] = it }
    SimToggleRow("R fwd boost pump", sim.controls.boostPumpFwd[1]) { sim.controls.boostPumpFwd[1] = it }
    SimToggleRow("R aft boost pump", sim.controls.boostPumpAft[1]) { sim.controls.boostPumpAft[1] = it }
    SimToggleRow("Crossfeed open", sim.controls.crossfeedOpen) { sim.controls.crossfeedOpen = it }

    PanelSectionLabel("ENGINE START")
    SimToggleRow(
        "L starter" + if (snap.left.starting) " (cranking)" else "",
        sim.controls.starter[0],
    ) { sim.controls.starter[0] = it }
    SimToggleRow(
        "R starter" + if (snap.right.starting) " (cranking)" else "",
        sim.controls.starter[1],
    ) { sim.controls.starter[1] = it }
    Text(
        "Starter cranks Ng to ~25%. Move the fuel lever to ON above 12% Ng - earlier means a hot start.",
        color = Dhc6DesktopColors.TextSubtle,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )

    PanelSectionLabel("PROPELLER / FIRE")
    SimToggleRow("Autofeather armed", sim.controls.autofeatherArmed) { sim.controls.autofeatherArmed = it }
    FireHandleRow("L FIRE HANDLE", snap.left.fire, sim.controls.fireHandlePulled[0]) {
        sim.controls.fireHandlePulled[0] = !sim.controls.fireHandlePulled[0]
    }
    FireHandleRow("R FIRE HANDLE", snap.right.fire, sim.controls.fireHandlePulled[1]) {
        sim.controls.fireHandlePulled[1] = !sim.controls.fireHandlePulled[1]
    }
}

@Composable
private fun InstructorPanel(
    sim: TwinOtterSimModel,
    snap: TwinOtterSnapshot,
    drill: SimDrill,
    gates: List<SimDrillGate>,
    gateStates: List<Boolean>,
    allPassed: Boolean,
    failureInjected: Boolean,
    onStartDrill: (SimDrill) -> Unit,
) {
    PanelSectionLabel("AIRCRAFT STATE")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallActionButton("Cold + dark", Modifier.weight(1f)) { sim.resetColdAndDark() }
        SmallActionButton("Ready T/O", Modifier.weight(1f)) { sim.resetReadyForTakeoff() }
        SmallActionButton("Cruise", Modifier.weight(1f)) { sim.resetCruise() }
    }

    PanelSectionLabel("MCC DRILL")
    SimDrill.entries.forEach { candidate ->
        val sel = candidate == drill
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (sel) Dhc6DesktopColors.AccentStrong else DeckColors.LampOff)
                .clickable { onStartDrill(candidate) }
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                candidate.title,
                color = if (sel) Color.White else Dhc6DesktopColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }

    Text(
        drill.briefing,
        color = Dhc6DesktopColors.TextSecondary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
    if (drill.failure != null) {
        val status = when {
            failureInjected -> "Failure ACTIVE: ${drill.failure.label}"
            else -> "Failure arms at T+${drill.armFailureAfterSec}s (now T+${snap.elapsedSec}s)"
        }
        Text(
            status,
            color = if (failureInjected) DeckColors.LampRedOn else DeckColors.Amber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }

    PanelSectionLabel("DRILL GATES  ${gateStates.count { it }}/${gates.size}")
    gates.forEachIndexed { i, gate ->
        val passed = gateStates.getOrElse(i) { false }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(if (passed) Color(0xFF0D3328) else DeckColors.LampOff)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(gate.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(gate.hint, color = Dhc6DesktopColors.TextSubtle, fontSize = 9.sp)
            }
            Text(
                if (passed) "PASS" else "OPEN",
                color = if (passed) DeckColors.GreenArc else DeckColors.Amber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
    if (allPassed && drill != SimDrill.FREE_PRACTICE) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DeckColors.GreenArc.copy(alpha = 0.2f))
                .border(BorderStroke(1.dp, DeckColors.GreenArc), RoundedCornerShape(8.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "DRILL COMPLETE - recorded to logbook (T+${snap.elapsedSec}s)",
                color = DeckColors.GreenArc,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }

    PanelSectionLabel("MANUAL FAILURES")
    SimFailure.entries.forEach { failure ->
        val active = failure in sim.controls.failures
        SimToggleRow(failure.label, active) {
            if (it) sim.controls.failures.add(failure) else sim.controls.failures.remove(failure)
        }
    }
}

@Composable
private fun PanelSectionLabel(text: String) {
    Text(
        text,
        color = Dhc6DesktopColors.Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SimToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) Color(0xFF10344A) else DeckColors.LampOff)
            .clickable { onChange(!checked) }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier
                .size(width = 30.dp, height = 15.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) DeckColors.GreenArc else Color(0xFF3A4650)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(2.dp)
                    .size(11.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun FireHandleRow(label: String, fireActive: Boolean, pulled: Boolean, onToggle: () -> Unit) {
    val blinkBg = fireActive && !pulled
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    blinkBg -> DeckColors.LampRedOn.copy(alpha = 0.35f)
                    pulled -> Color(0xFF3B1111)
                    else -> DeckColors.LampOff
                }
            )
            .border(
                BorderStroke(1.dp, if (blinkBg || pulled) DeckColors.LampRedOn else DeckColors.PanelEdge),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (blinkBg) Color.White else DeckColors.LampRedOn,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            if (pulled) "PULLED" else "IN",
            color = if (pulled) DeckColors.LampRedOn else Dhc6DesktopColors.TextSubtle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SmallActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DeckColors.LampOff)
            .border(BorderStroke(1.dp, Dhc6DesktopColors.Border), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Dhc6DesktopColors.Accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

/* ===================================================================== */
/* Dashboard preview card (replaces the old offscreen-GL 3D panel)        */
/* ===================================================================== */

@Composable
internal fun SimCockpitPreviewCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderBright),
        colors = CardDefaults.cardColors(containerColor = DeckColors.PanelBg),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("FLIGHT DECK SIM", color = Dhc6DesktopColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp)) {
                val r = min(size.height / 2f, size.width / 7f)
                for (g in 0..2) {
                    val c = Offset(size.width * (0.2f + 0.3f * g), size.height / 2f)
                    drawCircle(DeckColors.GaugeFace, r, c)
                    drawCircle(DeckColors.GaugeRim, r, c, style = Stroke(width = 2.5f))
                    val a = Math.toRadians((150.0 + 85.0 * g))
                    drawLine(
                        DeckColors.NeedleL,
                        c,
                        c + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r * 0.75f),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text(
                "Interactive Twin Otter cockpit - PT6A gauges, MCC drills",
                color = Dhc6DesktopColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}
