package com.dhc6trainer.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class CockpitTab(val label: String) {
    PANEL_2D("Panel (2D)"),
    INSIDE_3D("Inside (3D)"),
    OUTSIDE_3D("Outside (3D)"),
    EDIT("Edit panel"),
}

/**
 * The DHC-6 cockpit workspace: a 2D instrument panel for drill training (built
 * from X-Plane Twin Otter art, authorable in the Edit tab), plus inside/outside
 * 3D views. Replaces the former FlightGear-linked cockpit.
 */
@Composable
internal fun Dhc6CockpitScreen(modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(CockpitTab.PANEL_2D) }
    var layout by remember { mutableStateOf(Dhc6UserData.loadPanelLayoutOrDefault()) }
    var simState by remember { mutableStateOf(DesktopCockpitSimState.beforeStart()) }
    var selected by remember { mutableStateOf<PanelItem?>(null) }

    Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitTab.entries.forEach { entry ->
                    FilterChipLike(entry.label, tab == entry) { tab = entry }
                }
            }

            val stageModifier = Modifier.fillMaxWidth().weight(1f)
            when (tab) {
                CockpitTab.PANEL_2D -> Dhc6PanelStage(
                    layout = layout,
                    state = simState,
                    onStateChange = { simState = it },
                    selectedId = selected?.id,
                    onSelect = { selected = it },
                    modifier = stageModifier,
                )
                CockpitTab.EDIT -> Dhc6PanelEditor(
                    initialLayout = layout,
                    onLayoutChange = { layout = it },
                    modifier = stageModifier,
                )
                CockpitTab.INSIDE_3D -> Dhc6InteriorStage(stageModifier)
                CockpitTab.OUTSIDE_3D -> Dhc6ExteriorStage(stageModifier)
            }
        }

        // Inspector.
        Column(
            Modifier.weight(0.72f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailCard {
                Text("Selected control", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                val sel = selected
                if (sel == null) {
                    Text(
                        "Click an instrument or control on the panel to identify it and see its state.",
                        color = Dhc6DesktopColors.TextMuted, fontSize = 13.sp,
                    )
                } else {
                    InspectorRow("Role", sel.role.ifBlank { sel.id })
                    InspectorRow("Action", sel.action.name)
                    if (sel.stateKey.isNotBlank()) {
                        InspectorRow("Binding", sel.stateKey)
                        InspectorRow("State", panelStateLabel(simState, sel) ?: "-")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(sel.image, color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp)
                }
            }

            DetailCard {
                Text("Panel state", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                InspectorRow("Battery", if (simState.batteryMaster) "ON" else "OFF")
                InspectorRow("L fuel / R fuel", "${simState.leftFuelLeverOn.onOff()} / ${simState.rightFuelLeverOn.onOff()}")
                InspectorRow("L fire / R fire", "${simState.leftFireHandlePulled.pulled()} / ${simState.rightFireHandlePulled.pulled()}")
                InspectorRow("Flaps", simState.flaps.label)
                InspectorRow("Bus powered", if (simState.electricalBusPowered) "YES" else "NO")
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Dhc6DesktopColors.BorderSoft)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { simState = DesktopCockpitSimState.beforeStart(); selected = null },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                ) {
                    Text("Reset to cold & dark", fontWeight = FontWeight.Black)
                }
            }

            if (tab == CockpitTab.PANEL_2D) {
                DetailCard {
                    Text("Drill training", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This panel is built from the X-Plane DHC-6 Twin Otter. Use the Edit panel tab " +
                            "to place and label instruments/controls yourself, then drill by clicking them.",
                        color = Dhc6DesktopColors.TextMuted, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { tab = CockpitTab.EDIT }, shape = RoundedCornerShape(12.dp)) {
                        Text("Open panel editor", fontWeight = FontWeight.Black, color = Dhc6DesktopColors.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Dhc6DesktopColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CockpitPlaceholder(title: String, body: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        DetailCard {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Dhc6DesktopColors.TextMuted, fontSize = 13.sp)
        }
    }
}

private fun Boolean.onOff() = if (this) "ON" else "OFF"
private fun Boolean.pulled() = if (this) "PULLED" else "SET"
