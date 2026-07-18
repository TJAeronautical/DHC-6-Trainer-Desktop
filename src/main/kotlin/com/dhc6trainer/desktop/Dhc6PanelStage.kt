package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Maps between the panel canvas space and the on-screen viewport. The canvas is
 * fit (letterboxed) into the viewport at [baseScale], then scaled by [zoom] and
 * shifted by pan; the whole thing is drawn about the viewport centre.
 */
internal data class PanelViewTransform(
    val originX: Float,
    val originY: Float,
    val scale: Float,
) {
    fun canvasToScreen(cx: Float, cy: Float): Offset = Offset(originX + cx * scale, originY + cy * scale)
    fun screenToCanvas(sx: Float, sy: Float): Offset = Offset((sx - originX) / scale, (sy - originY) / scale)

    companion object {
        fun compute(viewportW: Float, viewportH: Float, layout: PanelLayout, zoom: Float, panX: Float, panY: Float): PanelViewTransform {
            val baseScale = min(viewportW / layout.canvasW, viewportH / layout.canvasH)
            val scale = baseScale * zoom
            val originX = (viewportW - layout.canvasW * scale) / 2f + panX
            val originY = (viewportH - layout.canvasH * scale) / 2f + panY
            return PanelViewTransform(originX, originY, scale)
        }
    }
}

/** Topmost item under a canvas-space point, or null. */
internal fun PanelLayout.hitTest(canvas: Offset): PanelItem? =
    items.asReversed().firstOrNull { it.contains(canvas) }

internal fun PanelItem.contains(canvas: Offset): Boolean =
    canvas.x >= x && canvas.x <= x + w && canvas.y >= y && canvas.y <= y + h

/**
 * Shared draw routine for both the runtime panel and the editor. Draws the
 * metallic background, every item's image, active-state cue rings, and the
 * selection outline.
 */
internal fun DrawScope.drawPanel(
    layout: PanelLayout,
    state: DesktopCockpitSimState,
    view: PanelViewTransform,
    selectedId: String?,
    imageFor: (PanelItem) -> ImageBitmap?,
) {
    // Panel face.
    val topLeft = view.canvasToScreen(0f, 0f)
    drawRect(
        color = Color(0xFF1A2026),
        topLeft = topLeft,
        size = Size(layout.canvasW * view.scale, layout.canvasH * view.scale),
    )

    layout.items.forEach { item ->
        val img = imageFor(item)
        val pos = view.canvasToScreen(item.x, item.y)
        val w = (item.w * view.scale)
        val h = (item.h * view.scale)
        if (img != null) {
            val draw: DrawScope.() -> Unit = {
                drawImage(
                    image = img,
                    dstOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                    dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High,
                )
            }
            if (item.rot != 0f) rotate(item.rot, pivot = Offset(pos.x + w / 2f, pos.y + h / 2f)) { draw() } else draw()
        } else {
            // Missing image: placeholder box so the item is still selectable.
            drawRect(Color(0xFF33404A), topLeft = pos, size = Size(w, h))
        }

        // Active-state cue (green, fire = red).
        if (item.action != PanelAction.NONE && panelStateActive(state, item)) {
            val cue = if (item.stateKey.contains("fire", true)) Color(0xFFFF5C5C) else Color(0xFF6BE675)
            drawRect(color = cue.copy(alpha = 0.16f), topLeft = pos, size = Size(w, h))
            drawRect(color = cue, topLeft = pos, size = Size(w, h), style = Stroke(width = 2.5f))
        }

        // Selection outline.
        if (item.id == selectedId) {
            drawRect(color = Color(0xFF55C7FF), topLeft = pos, size = Size(w, h), style = Stroke(width = 3f))
        }
    }
}

/**
 * Runtime (non-editing) instrument panel: renders the layout, lets the user
 * click controls to actuate the shared [DesktopCockpitSimState] and select an
 * item for the inspector. Zoom via the +/- controls, drag to pan.
 */
@Composable
internal fun Dhc6PanelStage(
    layout: PanelLayout,
    state: DesktopCockpitSimState,
    onStateChange: (DesktopCockpitSimState) -> Unit,
    selectedId: String?,
    onSelect: (PanelItem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val images = remember(layout) { HashMap<String, ImageBitmap?>() }
    fun imageFor(item: PanelItem): ImageBitmap? = images.getOrPut(item.image) { DesktopImages.image(item.resourcePath) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF02070B), RoundedCornerShape(18.dp))
                .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(18.dp))
                .pointerInput(layout, zoom, panX, panY, state) {
                    detectTapGestures { tap ->
                        val view = PanelViewTransform.compute(size.width.toFloat(), size.height.toFloat(), layout, zoom, panX, panY)
                        val canvas = view.screenToCanvas(tap.x, tap.y)
                        val hit = layout.hitTest(canvas)
                        onSelect(hit)
                        if (hit != null) onStateChange(applyPanelAction(state, hit))
                    }
                }
                .pointerInput(layout, zoom) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        panX += drag.x
                        panY += drag.y
                    }
                },
        ) {
            val view = PanelViewTransform.compute(size.width, size.height, layout, zoom, panX, panY)
            drawPanel(layout, state, view, selectedId, ::imageFor)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelChip("Fit") { zoom = 1f; panX = 0f; panY = 0f }
            PanelChip("-") { zoom = (zoom - 0.2f).coerceAtLeast(0.4f) }
            PanelChip("+") { zoom = (zoom + 0.2f).coerceAtMost(8f) }
            PanelChip("Max") { zoom = 8f }
        }
    }
}

@Composable
internal fun PanelChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Black, color = if (selected) Color.White else Color(0xFF55C7FF), maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF2D9FE0),
            containerColor = Color(0xFF061F31),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color(0xFF23607B),
            selectedBorderColor = Color(0xFF55C7FF),
        ),
    )
}
