package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private enum class PerfPhase { TAKEOFF, CLIMB, LANDING }

@Composable
internal fun DedicatedPerformanceScreen(assetSnapshot: DesktopAssetCatalogSnapshot) {
    var phase by remember { mutableStateOf(PerfPhase.TAKEOFF) }
    var elevation by remember { mutableStateOf("500") }
    var oat by remember { mutableStateOf("15") }
    var weight by remember { mutableStateOf("10000") }
    var wind by remember { mutableStateOf("0") }
    var runway by remember { mutableStateOf("3000") }
    var windMode by remember { mutableStateOf("HEADWIND") }
    var surface by remember { mutableStateOf("PAVED DRY") }
    var flaps by remember { mutableStateOf("37.5") }

    val elev = elevation.toIntOrNull(); val temp = oat.toIntOrNull(); val mass = weight.toIntOrNull()
    val windValue = wind.toIntOrNull(); val runwayFt = runway.toIntOrNull()
    val valid = elev != null && temp != null && mass != null && windValue != null && runwayFt != null &&
        elev in -1000..15000 && temp in -60..60 && mass in 5000..13000 && windValue in 0..40 && runwayFt in 500..15000
    val signedWind = when (windMode) { "HEADWIND" -> windValue ?: 0; "TAILWIND" -> -(windValue ?: 0); else -> 0 }
    val takeoff = if (valid) PerformanceEngine.computeTakeoff(elev!!, temp!!, mass!!, signedWind, surface) else null
    val landing = if (valid) PerformanceEngine.computeLanding(elev!!, temp!!, mass!!, signedWind, flaps) else null

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        DetailCard(Modifier.width(430.dp)) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { PerfPhaseTabs(phase) { phase = it } }
                item { Text("AIRFIELD & AIRCRAFT", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                item { PerfInput("Airport elevation", elevation, "FT") { elevation = it } }
                item { PerfInput("Outside air temperature", oat, "°C") { oat = it } }
                item { PerfInput("Aircraft weight", weight, "LB") { weight = it } }
                item { PerfInput("Available runway", runway, "FT") { runway = it } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { PerfInput("Wind", wind, "KT") { wind = it } }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Wind component", color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp)
                            listOf("HEADWIND", "TAILWIND", "CALM").forEach { mode -> PerfSelectRow(mode, windMode == mode) { windMode = mode } }
                        }
                    }
                }
                if (phase == PerfPhase.TAKEOFF) item { PerfChoices("Runway surface", listOf("PAVED DRY", "WET", "GRASS", "GRAVEL"), surface) { surface = it } }
                if (phase == PerfPhase.LANDING) item { PerfChoices("Landing flap", listOf("37.5", "20", "10"), flaps) { flaps = it } }
                item {
                    val tone = if (valid) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red
                    val message = if (valid) "Inputs are within the supported planning range." else "Use numeric values within: elevation -1,000–15,000 ft, OAT -60–60°C, weight 5,000–13,000 lb, wind 0–40 kt and runway 500–15,000 ft."
                    Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, tone), colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = .08f))) {
                        Text(message, color = Dhc6DesktopColors.TextSecondary, modifier = Modifier.padding(13.dp), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }

        DetailCard(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(phase.name, color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text(when (phase) { PerfPhase.TAKEOFF -> "Takeoff performance"; PerfPhase.CLIMB -> "Climb planning"; PerfPhase.LANDING -> "Landing performance" }, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("DHC-6 planning output from the existing desktop performance model.", color = Dhc6DesktopColors.TextSecondary)
                }
                if (!valid) item { EmptyState("Check the inputs", "Results appear when all values are inside the supported ranges.") }
                if (valid && phase == PerfPhase.TAKEOFF && takeoff != null) {
                    item { PerfResultGrid(listOf("GROUND ROLL" to "${"%,d".format(takeoff.groundRollFt)} FT", "TO 50 FT" to "${"%,d".format(takeoff.distanceTo50Ft)} FT", "VLOF / V1" to "${takeoff.vLofKt} KT", "V2" to "${takeoff.v2Kt} KT")) }
                    item { PerfMargin(runwayFt!!, takeoff.distanceTo50Ft) }
                }
                if (valid && phase == PerfPhase.LANDING && landing != null) {
                    item { PerfResultGrid(listOf("FROM 50 FT" to "${"%,d".format(landing.distanceFrom50Ft)} FT", "VREF" to "${landing.vRefKt} KT")) }
                    item { PerfMargin(runwayFt!!, landing.distanceFrom50Ft) }
                }
                if (valid && phase == PerfPhase.CLIMB) item {
                    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Dhc6DesktopColors.Gold), colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Gold.copy(alpha = .08f))) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("AFM DATA REQUIRED", color = Dhc6DesktopColors.Gold, fontWeight = FontWeight.Black)
                            Text("Climb-rate and obstacle-clearance outputs remain unavailable until approved AFM-backed tables are integrated. No estimated climb figure is generated.", color = Dhc6DesktopColors.TextSecondary, lineHeight = 20.sp)
                            Text("${"%,d".format(elev)} ft · $temp°C · ${"%,d".format(mass)} lb · ${abs(signedWind)} kt ${if (signedWind < 0) "tailwind" else if (signedWind > 0) "headwind" else "calm"}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Dhc6DesktopColors.Gold.copy(alpha = .45f)), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1500))) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("PLANNING USE ONLY", color = Dhc6DesktopColors.Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            Text("Confirm operational calculations against the approved AFM, company data and current runway conditions.", color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp)
                            Text("${assetSnapshot.allResourcePaths.size} indexed desktop assets", color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun PerfPhaseTabs(selected: PerfPhase, onSelect: (PerfPhase) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Dhc6DesktopColors.SurfaceDark, RoundedCornerShape(14.dp))) {
        PerfPhase.entries.forEach { phase ->
            Box(Modifier.weight(1f).background(if (phase == selected) Dhc6DesktopColors.AccentStrong else Color.Transparent, RoundedCornerShape(14.dp)).clickable { onSelect(phase) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(phase.name, color = if (phase == selected) Color.White else Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
    }
}
@Composable private fun PerfInput(label: String, value: String, suffix: String, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, suffix = { Text(suffix) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
@Composable private fun PerfSelectRow(label: String, selected: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).background(if (selected) Dhc6DesktopColors.Accent else Dhc6DesktopColors.Border, RoundedCornerShape(50))); Spacer(Modifier.width(6.dp)); Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun PerfChoices(title: String, choices: List<String>, selected: String, onSelect: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(title, color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { choices.forEach { value -> Card(Modifier.clickable { onSelect(value) }, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, if (value == selected) Dhc6DesktopColors.Accent else Dhc6DesktopColors.Border), colors = CardDefaults.cardColors(containerColor = if (value == selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark)) { Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) } } } } }
@Composable private fun PerfResultGrid(values: List<Pair<String, String>>) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { values.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { (label, value) -> StatCard(label, value, "Calculated planning value", Modifier.weight(1f)) }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } } }
@Composable private fun PerfMargin(runway: Int, required: Int) { val margin = runway - required; val safe = margin >= 0; val tone = if (safe) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red; Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, tone), colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = .08f))) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(if (safe) "RUNWAY MARGIN" else "RUNWAY DEFICIT", color = tone, fontWeight = FontWeight.Black, fontSize = 11.sp); Text("${"%,d".format(abs(margin))} FT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 32.sp); Text("Available ${"%,d".format(runway)} ft · Required ${"%,d".format(required)} ft", color = Dhc6DesktopColors.TextSecondary) } } }
