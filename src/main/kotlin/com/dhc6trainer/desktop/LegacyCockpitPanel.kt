package com.dhc6trainer.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The Legacy DHC-6 Twin Otter cockpit as the 2D drill panel: the full cockpit
 * poster (overhead + circuit-breaker panels + main instrument panel + pedestal)
 * with animated, clickable control sprites, rendered by [FlightSimCockpitStage].
 * Scroll to zoom, drag to pan. Selecting a control reports its target to the
 * inspector via [onSelectTarget].
 */
@Composable
internal fun LegacyCockpitPanel(
    state: DesktopCockpitSimState,
    onStateChange: (DesktopCockpitSimState) -> Unit,
    onSelectTarget: (CockpitHitboxTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var selectedTarget by remember { mutableStateOf(DefaultCockpitTargets.first()) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (dy != 0f) {
                                    zoom = (zoom * if (dy < 0f) 1.12f else 0.89f).coerceIn(0.5f, 8f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                },
        ) {
            FlightSimCockpitStage(
                variant = CockpitSpriteVariant.Legacy,
                simView = Dhc6SimulatorView.InsideCockpit,
                selectedTarget = selectedTarget,
                state = state,
                onStateChange = onStateChange,
                onSelectTargetId = { id ->
                    DefaultCockpitTargets.firstOrNull { it.id == id }?.let {
                        selectedTarget = it
                        onSelectTarget(it)
                    }
                },
                zoom = zoom,
                panX = panX,
                panY = panY,
                onPan = { dx, dy -> panX += dx; panY += dy },
                modifier = Modifier.fillMaxSize(),
                showStageChrome = false,
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelChip("Fit") { zoom = 1f; panX = 0f; panY = 0f }
            PanelChip("-") { zoom = (zoom - 0.2f).coerceAtLeast(0.5f) }
            PanelChip("${(zoom * 100).toInt()}%") { zoom = 1f; panX = 0f; panY = 0f }
            PanelChip("+") { zoom = (zoom + 0.2f).coerceAtMost(8f) }
            PanelChip("Max") { zoom = 8f }
        }
    }
}
