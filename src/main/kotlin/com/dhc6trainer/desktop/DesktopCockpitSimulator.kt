package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Stable
internal enum class CockpitPowerLeverPosition(val label: String, val torqueFactor: Float) {
    REVERSE("Reverse", 0.0f),
    IDLE("Idle", 0.08f),
    CRUISE("Cruise", 0.58f),
    CLIMB("Climb", 0.78f),
    MAX("Max", 0.96f);

    fun next(): CockpitPowerLeverPosition {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
}

@Stable
internal enum class CockpitFlapSetting(val label: String, val degrees: Float, val assetName: String) {
    UP("0 deg", 0f, "0"),
    TAKEOFF("10 deg", 10f, "10"),
    LANDING("37.5 deg", 37.5f, "default");

    fun next(): CockpitFlapSetting {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
}

@Stable
internal enum class CockpitCrossfeedPosition(val label: String, val assetName: String) {
    NORMAL("Normal", "default"),
    BOTH_FWD("Both fwd", "both_fwd"),
    BOTH_AFT("Both aft", "both_aft");

    fun next(): CockpitCrossfeedPosition {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
}

@Stable
internal data class DesktopCockpitSimState(
    val batteryMaster: Boolean = false,
    val avionicsMaster: Boolean = false,
    val leftDcGenerator: Boolean = false,
    val rightDcGenerator: Boolean = false,
    val fwdBoost1: Boolean = false,
    val aftBoost1: Boolean = false,
    val fwdBoost2: Boolean = false,
    val aftBoost2: Boolean = false,
    val leftFuelLeverOn: Boolean = false,
    val rightFuelLeverOn: Boolean = false,
    val crossfeed: CockpitCrossfeedPosition = CockpitCrossfeedPosition.NORMAL,
    val leftPower: CockpitPowerLeverPosition = CockpitPowerLeverPosition.IDLE,
    val rightPower: CockpitPowerLeverPosition = CockpitPowerLeverPosition.IDLE,
    val flaps: CockpitFlapSetting = CockpitFlapSetting.UP,
    val fireDetectionArmed: Boolean = false,
    val leftFireHandlePulled: Boolean = false,
    val rightFireHandlePulled: Boolean = false,
    val leftEngineFire: Boolean = false,
    val rightEngineFire: Boolean = false,
) {
    val leftFuelPressure: Boolean
        get() = leftFuelLeverOn && !leftFireHandlePulled &&
            ((fwdBoost1 || aftBoost1) || (crossfeed != CockpitCrossfeedPosition.NORMAL && (fwdBoost2 || aftBoost2)))

    val rightFuelPressure: Boolean
        get() = rightFuelLeverOn && !rightFireHandlePulled &&
            ((fwdBoost2 || aftBoost2) || (crossfeed != CockpitCrossfeedPosition.NORMAL && (fwdBoost1 || aftBoost1)))

    val leftEngineRunning: Boolean
        get() = leftFuelPressure && !leftFireHandlePulled

    val rightEngineRunning: Boolean
        get() = rightFuelPressure && !rightFireHandlePulled

    val leftGeneratorOnline: Boolean
        get() = leftDcGenerator && leftEngineRunning && !leftFireHandlePulled

    val rightGeneratorOnline: Boolean
        get() = rightDcGenerator && rightEngineRunning && !rightFireHandlePulled

    val electricalBusPowered: Boolean
        get() = batteryMaster || leftGeneratorOnline || rightGeneratorOnline

    val avionicsPowered: Boolean
        get() = electricalBusPowered && avionicsMaster

    val hydraulicPressurePsi: Int
        get() = when {
            leftEngineRunning || rightEngineRunning -> 2850
            electricalBusPowered -> 450
            else -> 0
        }

    fun torquePercent(leftSide: Boolean): Int {
        val power = if (leftSide) leftPower else rightPower
        val running = if (leftSide) leftEngineRunning else rightEngineRunning
        return if (running) (12f + power.torqueFactor * 82f).roundToInt() else 0
    }

    companion object {
        fun coldDark() = DesktopCockpitSimState()

        fun beforeStart() = DesktopCockpitSimState(
            batteryMaster = true,
            avionicsMaster = false,
            fwdBoost1 = true,
            aftBoost1 = true,
            fwdBoost2 = true,
            aftBoost2 = true,
            fireDetectionArmed = true,
        )

        fun takeoffConfig() = DesktopCockpitSimState(
            batteryMaster = true,
            avionicsMaster = true,
            leftDcGenerator = true,
            rightDcGenerator = true,
            fwdBoost1 = true,
            aftBoost1 = true,
            fwdBoost2 = true,
            aftBoost2 = true,
            leftFuelLeverOn = true,
            rightFuelLeverOn = true,
            leftPower = CockpitPowerLeverPosition.CLIMB,
            rightPower = CockpitPowerLeverPosition.CLIMB,
            flaps = CockpitFlapSetting.TAKEOFF,
            fireDetectionArmed = true,
        )

        fun fuelPressureLoss() = takeoffConfig().copy(
            fwdBoost1 = false,
            aftBoost1 = false,
            crossfeed = CockpitCrossfeedPosition.NORMAL,
            leftPower = CockpitPowerLeverPosition.CRUISE,
            rightPower = CockpitPowerLeverPosition.CRUISE,
        )

        fun engineFireMemory() = takeoffConfig().copy(
            rightEngineFire = true,
            rightPower = CockpitPowerLeverPosition.CLIMB,
            rightFireHandlePulled = false,
        )
    }
}

internal enum class CockpitSimScenario(
    val title: String,
    val focusTargetId: String,
    val objective: String,
) {
    FREE_PLAY(
        title = "Free play",
        focusTargetId = "power-levers",
        objective = "Explore cockpit controls and watch the live systems state change."
    ),
    BEFORE_START(
        title = "Before start",
        focusTargetId = "electrical-panel",
        objective = "Power the aircraft, arm protection, and keep fuel controls ready without bringing engines online."
    ),
    TAKEOFF_CONFIG(
        title = "Takeoff config",
        focusTargetId = "power-levers",
        objective = "Set a two-engine takeoff configuration with generators, boost pumps, flaps, and power aligned."
    ),
    FUEL_PRESSURE_LOSS(
        title = "Fuel pressure loss",
        focusTargetId = "fuel-panel",
        objective = "Recover left fuel pressure using boost pumps or crossfeed while monitoring annunciators."
    ),
    ENGINE_FIRE_MEMORY(
        title = "Engine fire memory",
        focusTargetId = "fire-panel",
        objective = "Run the right-engine fire memory items: idle, fuel off, and fire handle pulled."
    );

    fun seedState(): DesktopCockpitSimState = when (this) {
        FREE_PLAY -> DesktopCockpitSimState.beforeStart()
        BEFORE_START -> DesktopCockpitSimState.beforeStart()
        TAKEOFF_CONFIG -> DesktopCockpitSimState.takeoffConfig()
        FUEL_PRESSURE_LOSS -> DesktopCockpitSimState.fuelPressureLoss()
        ENGINE_FIRE_MEMORY -> DesktopCockpitSimState.engineFireMemory()
    }
}

@Composable
internal fun CockpitSimulatorPanel(
    selectedTarget: CockpitHitboxTarget,
    state: DesktopCockpitSimState,
    scenario: CockpitSimScenario,
    onStateChange: (DesktopCockpitSimState) -> Unit,
    onScenarioChange: (CockpitSimScenario) -> Unit,
    onSelectTargetId: (String) -> Unit,
) {
    val checks = scenarioChecks(scenario, state)
    val progress = checks.count { it.passed }.toFloat() / checks.size.coerceAtLeast(1)
    val controls = CockpitSimControls.filter { it.targetId == selectedTarget.id }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SimulatorPanelCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimulatorChip("Simulator", selected = true)
                SimulatorChip(scenario.title, selected = false)
            }
            Spacer(Modifier.height(14.dp))
            Text("Cockpit simulator", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(scenario.objective, color = Color(0xFFD8E5F2), fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            ScenarioSelector(
                scenario = scenario,
                onScenarioChange = { next ->
                    onScenarioChange(next)
                    onStateChange(next.seedState())
                    onSelectTargetId(next.focusTargetId)
                },
            )
        }

        SimulatorPanelCard {
            Text("Scenario gates", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                color = Color(0xFF55C7FF),
                trackColor = Color(0xFF12354B),
            )
            Spacer(Modifier.height(12.dp))
            checks.forEach { check ->
                ScenarioCheckRow(check = check, onSelectTargetId = onSelectTargetId)
                Spacer(Modifier.height(8.dp))
            }
        }

        LiveSystemsPanel(state)

        SimulatorPanelCard {
            Text(selectedTarget.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text("Interactive controls", color = Color(0xFF55C7FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            if (controls.isEmpty()) {
                Text(
                    "This target is read-only in the first simulator pass. Select electrical, fuel, power, flaps, or fire protection to manipulate cockpit controls.",
                    color = Color(0xFFD8E5F2),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            } else {
                controls.forEach { control ->
                    CockpitControlCard(control = control, state = state, onStateChange = onStateChange)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        AnnunciatorPanel(state, onSelectTargetId)
    }
}

@Composable
private fun ScenarioSelector(
    scenario: CockpitSimScenario,
    onScenarioChange: (CockpitSimScenario) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CockpitSimScenario.values().forEach { candidate ->
            FilterChip(
                selected = candidate == scenario,
                onClick = { onScenarioChange(candidate) },
                label = {
                    Text(
                        candidate.title,
                        color = if (candidate == scenario) Color.White else Color(0xFF55C7FF),
                        fontWeight = FontWeight.Black,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2D9FE0),
                    containerColor = Color(0xFF061F31),
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = candidate == scenario,
                    borderColor = Color(0xFF23607B),
                    selectedBorderColor = Color(0xFF55C7FF),
                ),
            )
        }
    }
}

@Composable
private fun ScenarioCheckRow(
    check: CockpitSimCheck,
    onSelectTargetId: (String) -> Unit,
) {
    Card(
        onClick = { onSelectTargetId(check.targetId) },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (check.passed) Color(0xFF4AD186) else Color(0xFF8A4C2B)),
        colors = CardDefaults.cardColors(containerColor = if (check.passed) Color(0xFF0D3328) else Color(0xFF33210D)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(check.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (check.passed) "Set" else "Action", color = if (check.passed) Color(0xFF4AD186) else Color(0xFFFFB56B), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun LiveSystemsPanel(state: DesktopCockpitSimState) {
    SimulatorPanelCard {
        Text("Live aircraft state", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        SystemMetricRow("Electrical bus", if (state.electricalBusPowered) "Powered" else "Dark", state.electricalBusPowered)
        SystemMetricRow("Avionics", if (state.avionicsPowered) "Online" else "Offline", state.avionicsPowered)
        SystemMetricRow("Left engine", engineText(state, leftSide = true), state.leftEngineRunning)
        SystemMetricRow("Right engine", engineText(state, leftSide = false), state.rightEngineRunning)
        SystemMetricRow("Hydraulic pressure", "${state.hydraulicPressurePsi} psi", state.hydraulicPressurePsi >= 2500)
        SystemMetricRow("Flaps", state.flaps.label, state.flaps == CockpitFlapSetting.TAKEOFF || state.flaps == CockpitFlapSetting.UP)
    }
}

@Composable
private fun SystemMetricRow(label: String, value: String, good: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFD8E5F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (good) Color(0xFF0D3328) else Color(0xFF33210D))
                .border(BorderStroke(1.dp, if (good) Color(0xFF4AD186) else Color(0xFFFFB56B)), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(value, color = if (good) Color(0xFF4AD186) else Color(0xFFFFB56B), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CockpitControlCard(
    control: CockpitSimControl,
    state: DesktopCockpitSimState,
    onStateChange: (DesktopCockpitSimState) -> Unit,
) {
    val assetPath = control.assetPath(state)
    Card(
        onClick = { onStateChange(control.advance(state)) },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF23607B)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF08283D)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CockpitControlArt(assetPath = assetPath)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(control.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(control.valueLabel(state), color = Color(0xFF55C7FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(control.description, color = Color(0xFFD8E5F2), fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (control.switchValue != null) {
                Switch(
                    checked = control.switchValue.invoke(state),
                    onCheckedChange = { onStateChange(control.advance(state)) },
                )
            } else {
                Text(control.actionLabel, color = Color(0xFF55C7FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CockpitControlArt(assetPath: String?) {
    val bitmap = assetPath?.let { DesktopImages.image(it) }
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF03131F))
            .border(BorderStroke(1.dp, Color(0xFF23607B)), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 48.dp),
            )
        } else {
            Text("SIM", color = Color(0xFF55C7FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AnnunciatorPanel(
    state: DesktopCockpitSimState,
    onSelectTargetId: (String) -> Unit,
) {
    val active = activeAnnunciators(state)
    SimulatorPanelCard {
        Text("Annunciators", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        if (active.isEmpty()) {
            Text("No active warnings or cautions.", color = Color(0xFFD8E5F2), fontSize = 13.sp)
        } else {
            active.forEach { annunciation ->
                Card(
                    onClick = { onSelectTargetId(annunciation.targetId) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, annunciation.level.color),
                    colors = CardDefaults.cardColors(containerColor = annunciation.level.background),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(annunciation.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text(annunciation.level.label, color = annunciation.level.color, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulatorPanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF23607B)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF061F31)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SimulatorChip(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFF155D7A) else Color(0xFF08283D))
            .border(BorderStroke(1.dp, if (selected) Color(0xFF55C7FF) else Color(0xFF23607B)), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .widthIn(min = 0.dp),
    ) {
        Text(label, color = Color(0xFF55C7FF), fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

private data class CockpitSimCheck(
    val label: String,
    val targetId: String,
    val passed: Boolean,
)

private data class CockpitSimControl(
    val label: String,
    val targetId: String,
    val description: String,
    val actionLabel: String,
    val switchValue: ((DesktopCockpitSimState) -> Boolean)? = null,
    val valueLabel: (DesktopCockpitSimState) -> String,
    val assetPath: (DesktopCockpitSimState) -> String?,
    val advance: (DesktopCockpitSimState) -> DesktopCockpitSimState,
)

private data class CockpitSimAnnunciation(
    val label: String,
    val level: AnnunciationLevel,
    val targetId: String,
)

private enum class AnnunciationLevel(val label: String, val color: Color, val background: Color) {
    WARNING("WARNING", Color(0xFFFF5C5C), Color(0xFF3B1111)),
    CAUTION("CAUTION", Color(0xFFFFB56B), Color(0xFF33210D)),
    ADVISORY("ADVISORY", Color(0xFF55C7FF), Color(0xFF08283D)),
}

private val CockpitSimControls = listOf(
    switchControl(
        label = "Battery master",
        targetId = "electrical-panel",
        description = "Feeds the main DC bus and enables electrical checks.",
        getter = { it.batteryMaster },
        setter = { state, value -> state.copy(batteryMaster = value) },
        asset = { "cockpit/source_exact/shared/battery_master/${if (it.batteryMaster) "on" else "off"}.png" },
    ),
    switchControl(
        label = "Avionics master",
        targetId = "electrical-panel",
        description = "Powers the avionics stack when the DC bus is alive.",
        getter = { it.avionicsMaster },
        setter = { state, value -> state.copy(avionicsMaster = value) },
        asset = { "cockpit/source_exact/legacy/avionics_master/${if (it.avionicsMaster) "on" else "off"}.png" },
    ),
    switchControl(
        label = "Left DC generator",
        targetId = "electrical-panel",
        description = "Comes online only when the left engine is running.",
        getter = { it.leftDcGenerator },
        setter = { state, value -> state.copy(leftDcGenerator = value) },
        asset = { "cockpit/source_exact/shared/vertical_toggle/${if (it.leftDcGenerator) "on" else "off"}.png" },
    ),
    switchControl(
        label = "Right DC generator",
        targetId = "electrical-panel",
        description = "Comes online only when the right engine is running.",
        getter = { it.rightDcGenerator },
        setter = { state, value -> state.copy(rightDcGenerator = value) },
        asset = { "cockpit/source_exact/shared/vertical_toggle/${if (it.rightDcGenerator) "on" else "off"}.png" },
    ),
    switchControl(
        label = "No. 1 fwd boost",
        targetId = "fuel-panel",
        description = "Restores pressure to the left-side fuel feed.",
        getter = { it.fwdBoost1 },
        setter = { state, value -> state.copy(fwdBoost1 = value) },
        asset = { "cockpit/source_exact/shared/boost_pumps/${if (it.fwdBoost1) "on" else "off"}.png" },
    ),
    switchControl(
        label = "No. 1 aft boost",
        targetId = "fuel-panel",
        description = "Backup pressure source for the left feed.",
        getter = { it.aftBoost1 },
        setter = { state, value -> state.copy(aftBoost1 = value) },
        asset = { "cockpit/source_exact/shared/boost_pumps/${if (it.aftBoost1) "on" else "off"}.png" },
    ),
    switchControl(
        label = "No. 2 fwd boost",
        targetId = "fuel-panel",
        description = "Restores pressure to the right-side fuel feed.",
        getter = { it.fwdBoost2 },
        setter = { state, value -> state.copy(fwdBoost2 = value) },
        asset = { "cockpit/source_exact/shared/boost_pumps/${if (it.fwdBoost2) "on" else "off"}.png" },
    ),
    switchControl(
        label = "No. 2 aft boost",
        targetId = "fuel-panel",
        description = "Backup pressure source for the right feed.",
        getter = { it.aftBoost2 },
        setter = { state, value -> state.copy(aftBoost2 = value) },
        asset = { "cockpit/source_exact/shared/boost_pumps/${if (it.aftBoost2) "on" else "off"}.png" },
    ),
    switchControl(
        label = "Left fuel lever",
        targetId = "fuel-panel",
        description = "Moves the left condition/fuel lever between neutral and fuel.",
        getter = { it.leftFuelLeverOn },
        setter = { state, value -> state.copy(leftFuelLeverOn = value) },
        asset = { "cockpit/source_exact/shared/fuel_lever_l/${if (it.leftFuelLeverOn) "fuel" else "neutral"}.png" },
    ),
    switchControl(
        label = "Right fuel lever",
        targetId = "fuel-panel",
        description = "Moves the right condition/fuel lever between neutral and fuel.",
        getter = { it.rightFuelLeverOn },
        setter = { state, value -> state.copy(rightFuelLeverOn = value) },
        asset = { "cockpit/source_exact/shared/fuel_lever_r/${if (it.rightFuelLeverOn) "fuel" else "neutral"}.png" },
    ),
    CockpitSimControl(
        label = "Crossfeed selector",
        targetId = "fuel-panel",
        description = "Cycles the modeled crossfeed state for asymmetric fuel-pressure drills.",
        actionLabel = "Cycle",
        valueLabel = { it.crossfeed.label },
        assetPath = { "cockpit/source_exact/shared/fuel_crossfeed/${it.crossfeed.assetName}.png" },
        advance = { it.copy(crossfeed = it.crossfeed.next()) },
    ),
    CockpitSimControl(
        label = "Left power lever",
        targetId = "power-levers",
        description = "Cycles left power from reverse/idle through cruise, climb, and max.",
        actionLabel = "Cycle",
        valueLabel = { "${it.leftPower.label} - ${it.torquePercent(leftSide = true)}%" },
        assetPath = { "cockpit/source_exact/shared/power_lever_l/default.png" },
        advance = { it.copy(leftPower = it.leftPower.next()) },
    ),
    CockpitSimControl(
        label = "Right power lever",
        targetId = "power-levers",
        description = "Cycles right power from reverse/idle through cruise, climb, and max.",
        actionLabel = "Cycle",
        valueLabel = { "${it.rightPower.label} - ${it.torquePercent(leftSide = false)}%" },
        assetPath = { "cockpit/source_exact/shared/power_lever_r/default.png" },
        advance = { it.copy(rightPower = it.rightPower.next()) },
    ),
    CockpitSimControl(
        label = "Flap selector",
        targetId = "flaps-hydraulic",
        description = "Cycles flaps through up, takeoff, and landing positions.",
        actionLabel = "Cycle",
        valueLabel = { it.flaps.label },
        assetPath = { "cockpit/source_exact/shared/flap_handle/${it.flaps.assetName}.png" },
        advance = { it.copy(flaps = it.flaps.next()) },
    ),
    switchControl(
        label = "Fire detection",
        targetId = "fire-panel",
        description = "Arms the modeled detection circuit for fire memory drills.",
        getter = { it.fireDetectionArmed },
        setter = { state, value -> state.copy(fireDetectionArmed = value) },
        asset = { "cockpit/source_exact/g950/fire_detection/${if (it.fireDetectionArmed) "armed" else "off"}.png" },
    ),
    switchControl(
        label = "Left fire handle",
        targetId = "fire-panel",
        description = "Pulls the left fire handle and removes left fuel/engine availability.",
        getter = { it.leftFireHandlePulled },
        setter = { state, value -> state.copy(leftFireHandlePulled = value, leftFuelLeverOn = if (value) false else state.leftFuelLeverOn) },
        asset = { "cockpit/source_exact/legacy/fire_handle_pull/${if (it.leftFireHandlePulled) "on" else "off"}.png" },
    ),
    switchControl(
        label = "Right fire handle",
        targetId = "fire-panel",
        description = "Pulls the right fire handle and removes right fuel/engine availability.",
        getter = { it.rightFireHandlePulled },
        setter = { state, value -> state.copy(rightFireHandlePulled = value, rightFuelLeverOn = if (value) false else state.rightFuelLeverOn) },
        asset = { "cockpit/source_exact/legacy/fire_handle_pull/${if (it.rightFireHandlePulled) "on" else "off"}.png" },
    ),
)

private fun switchControl(
    label: String,
    targetId: String,
    description: String,
    getter: (DesktopCockpitSimState) -> Boolean,
    setter: (DesktopCockpitSimState, Boolean) -> DesktopCockpitSimState,
    asset: (DesktopCockpitSimState) -> String?,
) = CockpitSimControl(
    label = label,
    targetId = targetId,
    description = description,
    actionLabel = "Toggle",
    switchValue = getter,
    valueLabel = { if (getter(it)) "On" else "Off" },
    assetPath = asset,
    advance = { state -> setter(state, !getter(state)) },
)

private fun scenarioChecks(scenario: CockpitSimScenario, state: DesktopCockpitSimState): List<CockpitSimCheck> = when (scenario) {
    CockpitSimScenario.FREE_PLAY -> listOf(
        CockpitSimCheck("Electrical bus modeled", "electrical-panel", state.electricalBusPowered),
        CockpitSimCheck("Any fuel pressure available", "fuel-panel", state.leftFuelPressure || state.rightFuelPressure),
        CockpitSimCheck("Any engine running", "power-levers", state.leftEngineRunning || state.rightEngineRunning),
    )
    CockpitSimScenario.BEFORE_START -> listOf(
        CockpitSimCheck("Battery master on", "electrical-panel", state.batteryMaster),
        CockpitSimCheck("Avionics protected/off", "electrical-panel", !state.avionicsMaster),
        CockpitSimCheck("Boost pumps available", "fuel-panel", state.fwdBoost1 && state.aftBoost1 && state.fwdBoost2 && state.aftBoost2),
        CockpitSimCheck("Fire detection armed", "fire-panel", state.fireDetectionArmed),
        CockpitSimCheck("Fuel levers neutral", "fuel-panel", !state.leftFuelLeverOn && !state.rightFuelLeverOn),
    )
    CockpitSimScenario.TAKEOFF_CONFIG -> listOf(
        CockpitSimCheck("Both engines running", "power-levers", state.leftEngineRunning && state.rightEngineRunning),
        CockpitSimCheck("Generators online", "electrical-panel", state.leftGeneratorOnline && state.rightGeneratorOnline),
        CockpitSimCheck("Avionics powered", "electrical-panel", state.avionicsPowered),
        CockpitSimCheck("Boost pumps on", "fuel-panel", state.fwdBoost1 && state.aftBoost1 && state.fwdBoost2 && state.aftBoost2),
        CockpitSimCheck("Flaps 10 deg", "flaps-hydraulic", state.flaps == CockpitFlapSetting.TAKEOFF),
        CockpitSimCheck("Power set climb or max", "power-levers", state.leftPower.ordinal >= CockpitPowerLeverPosition.CLIMB.ordinal && state.rightPower.ordinal >= CockpitPowerLeverPosition.CLIMB.ordinal),
    )
    CockpitSimScenario.FUEL_PRESSURE_LOSS -> listOf(
        CockpitSimCheck("Left fuel pressure restored", "fuel-panel", state.leftFuelPressure),
        CockpitSimCheck("Left engine stabilized", "power-levers", state.leftEngineRunning),
        CockpitSimCheck("Annunciator scan clean", "annunciators", activeAnnunciators(state).none { it.label.contains("BOOST PUMP 1") }),
        CockpitSimCheck("Crossfeed considered", "fuel-panel", state.crossfeed != CockpitCrossfeedPosition.NORMAL || state.fwdBoost1 || state.aftBoost1),
    )
    CockpitSimScenario.ENGINE_FIRE_MEMORY -> listOf(
        CockpitSimCheck("Right power lever idle/reverse", "power-levers", state.rightPower == CockpitPowerLeverPosition.IDLE || state.rightPower == CockpitPowerLeverPosition.REVERSE),
        CockpitSimCheck("Right fuel lever off", "fuel-panel", !state.rightFuelLeverOn),
        CockpitSimCheck("Right fire handle pulled", "fire-panel", state.rightFireHandlePulled),
        CockpitSimCheck("Fire detection armed", "fire-panel", state.fireDetectionArmed),
    )
}

private fun activeAnnunciators(state: DesktopCockpitSimState): List<CockpitSimAnnunciation> {
    val items = mutableListOf<CockpitSimAnnunciation>()
    if (!state.electricalBusPowered) {
        items += CockpitSimAnnunciation("DC BUS OFF", AnnunciationLevel.WARNING, "electrical-panel")
    }
    if (state.electricalBusPowered && state.leftEngineRunning && !state.leftGeneratorOnline) {
        items += CockpitSimAnnunciation("L GENERATOR", AnnunciationLevel.CAUTION, "electrical-panel")
    }
    if (state.electricalBusPowered && state.rightEngineRunning && !state.rightGeneratorOnline) {
        items += CockpitSimAnnunciation("R GENERATOR", AnnunciationLevel.CAUTION, "electrical-panel")
    }
    if (state.leftFuelLeverOn && !state.leftFuelPressure) {
        items += CockpitSimAnnunciation("BOOST PUMP 1 PRESS", AnnunciationLevel.CAUTION, "fuel-panel")
    }
    if (state.rightFuelLeverOn && !state.rightFuelPressure) {
        items += CockpitSimAnnunciation("BOOST PUMP 2 PRESS", AnnunciationLevel.CAUTION, "fuel-panel")
    }
    if (state.leftEngineFire && !state.leftFireHandlePulled) {
        items += CockpitSimAnnunciation("L ENGINE FIRE", AnnunciationLevel.WARNING, "fire-panel")
    }
    if (state.rightEngineFire && !state.rightFireHandlePulled) {
        items += CockpitSimAnnunciation("R ENGINE FIRE", AnnunciationLevel.WARNING, "fire-panel")
    }
    if (state.crossfeed != CockpitCrossfeedPosition.NORMAL) {
        items += CockpitSimAnnunciation("CROSSFEED ${state.crossfeed.label.uppercase()}", AnnunciationLevel.ADVISORY, "fuel-panel")
    }
    return items
}

private fun engineText(state: DesktopCockpitSimState, leftSide: Boolean): String {
    val running = if (leftSide) state.leftEngineRunning else state.rightEngineRunning
    val torque = state.torquePercent(leftSide)
    return if (running) "$torque% torque" else "Stopped"
}
