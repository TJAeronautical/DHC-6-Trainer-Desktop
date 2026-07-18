package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class DragMode { NONE, MOVE, RESIZE, PAN }

/**
 * In-app visual panel editor: place instrument/control art from the palette,
 * drag to move, drag the corner handle to resize, set each control's role and
 * drill binding, and save. Writes to the user's writable layout file so the
 * runtime panel (and the 3D cockpit) pick it up.
 */
@Composable
internal fun Dhc6PanelEditor(
    initialLayout: PanelLayout,
    onLayoutChange: (PanelLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf(initialLayout) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Drag to move · drag the blue corner to resize") }
    var addCounter by remember { mutableStateOf(0) }

    val layoutState = rememberUpdatedState(layout)
    val selectedIdState = rememberUpdatedState(selectedId)
    val dragMode = remember { mutableStateOf(DragMode.NONE) }
    val dragId = remember { mutableStateOf<String?>(null) }
    val images = remember { HashMap<String, ImageBitmap?>() }
    fun imageFor(item: PanelItem): ImageBitmap? = images.getOrPut(item.image) { DesktopImages.image(item.resourcePath) }

    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    val zoomState = rememberUpdatedState(zoom)
    val panXState = rememberUpdatedState(panX)
    val panYState = rememberUpdatedState(panY)

    fun apply(newLayout: PanelLayout) { layout = newLayout; onLayoutChange(newLayout) }
    val selected = layout.items.firstOrNull { it.id == selectedId }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- Palette ----
        Column(Modifier.width(190.dp).fillMaxHeight()) {
            Text("Instruments", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Dhc6PanelAssets.instruments) { asset ->
                    PaletteEntry(asset) {
                        addCounter++
                        val id = "item_${asset.displayName}_$addCounter"
                        val item = PanelItem(
                            id = id,
                            image = "instruments/${asset.file}",
                            x = layout.canvasW / 2f - 90f,
                            y = layout.canvasH / 2f - 90f,
                            w = 180f, h = 180f,
                            role = asset.displayName,
                        )
                        apply(layout.addItem(item))
                        selectedId = id
                    }
                }
            }
        }

        // ---- Canvas ----
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF02070B), RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(14.dp))
                    // Scroll wheel zooms about the viewport centre.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (dy != 0f) {
                                        zoom = (zoom * if (dy < 0f) 1.12f else 0.89f).coerceIn(0.4f, 8f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val view = PanelViewTransform.compute(size.width.toFloat(), size.height.toFloat(), layoutState.value, zoomState.value, panXState.value, panYState.value)
                            val canvas = view.screenToCanvas(tap.x, tap.y)
                            selectedId = layoutState.value.hitTest(canvas)?.id
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val view = PanelViewTransform.compute(size.width.toFloat(), size.height.toFloat(), layoutState.value, zoomState.value, panXState.value, panYState.value)
                                val sel = layoutState.value.items.firstOrNull { it.id == selectedIdState.value }
                                if (sel != null) {
                                    val br = view.canvasToScreen(sel.x + sel.w, sel.y + sel.h)
                                    if ((start - br).getDistance() < 30f) {
                                        dragMode.value = DragMode.RESIZE; dragId.value = sel.id; return@detectDragGestures
                                    }
                                }
                                val hit = layoutState.value.hitTest(view.screenToCanvas(start.x, start.y))
                                if (hit != null) {
                                    selectedId = hit.id; dragMode.value = DragMode.MOVE; dragId.value = hit.id
                                } else {
                                    // Empty space: pan the view.
                                    dragMode.value = DragMode.PAN; dragId.value = null
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (dragMode.value == DragMode.PAN) {
                                    panX += amount.x; panY += amount.y
                                    return@detectDragGestures
                                }
                                val view = PanelViewTransform.compute(size.width.toFloat(), size.height.toFloat(), layoutState.value, zoomState.value, panXState.value, panYState.value)
                                val dx = amount.x / view.scale
                                val dy = amount.y / view.scale
                                val id = dragId.value ?: return@detectDragGestures
                                val item = layoutState.value.items.firstOrNull { it.id == id } ?: return@detectDragGestures
                                when (dragMode.value) {
                                    DragMode.MOVE -> apply(layoutState.value.withItem(item.copy(x = item.x + dx, y = item.y + dy)))
                                    DragMode.RESIZE -> apply(layoutState.value.withItem(item.copy(
                                        w = (item.w + dx).coerceAtLeast(24f),
                                        h = (item.h + dy).coerceAtLeast(24f),
                                    )))
                                    else -> Unit
                                }
                            },
                            onDragEnd = { dragMode.value = DragMode.NONE; dragId.value = null },
                        )
                    },
            ) {
                val view = PanelViewTransform.compute(size.width, size.height, layoutState.value, zoomState.value, panXState.value, panYState.value)
                drawPanel(layoutState.value, DesktopCockpitSimState.beforeStart(), view, selectedIdState.value, ::imageFor)
                selected?.let {
                    val br = view.canvasToScreen(it.x + it.w, it.y + it.h)
                    drawCircle(Color(0xFF55C7FF), radius = 11f, center = br)
                    drawCircle(Color(0xFF02070B), radius = 5f, center = br)
                }
            }

            // Zoom controls + hint.
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PanelChip("Fit") { zoom = 1f; panX = 0f; panY = 0f }
                PanelChip("-") { zoom = (zoom - 0.2f).coerceAtLeast(0.4f) }
                PanelChip("${(zoom * 100).roundToInt()}%") { zoom = 1f; panX = 0f; panY = 0f }
                PanelChip("+") { zoom = (zoom + 0.2f).coerceAtMost(8f) }
                PanelChip("Max") { zoom = 8f }
            }
            Text(
                "scroll = zoom · drag empty space = pan",
                color = Dhc6DesktopColors.TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            )
        }

        // ---- Inspector ----
        Column(Modifier.width(230.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Properties", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            if (selected == null) {
                Text("Click an item to edit it, or add one from the palette.", color = Dhc6DesktopColors.TextMuted, fontSize = 12.sp)
            } else {
                OutlinedTextField(
                    value = selected.role,
                    onValueChange = { apply(layout.withItem(selected.copy(role = it))) },
                    label = { Text("Role / label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledDropdown("Action", selected.action.name, PanelAction.entries.map { it.name }) { picked ->
                    apply(layout.withItem(selected.copy(action = PanelAction.valueOf(picked))))
                }
                LabeledDropdown("Drill binding", selected.stateKey.ifBlank { "(none)" }, Dhc6PanelAssets.stateKeys.map { it.ifBlank { "(none)" } }) { picked ->
                    val key = if (picked == "(none)") "" else picked
                    apply(layout.withItem(selected.copy(stateKey = key)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallBtn("W-") { apply(layout.withItem(selected.copy(w = (selected.w - 10).coerceAtLeast(24f)))) }
                    SmallBtn("W+") { apply(layout.withItem(selected.copy(w = selected.w + 10))) }
                    SmallBtn("H-") { apply(layout.withItem(selected.copy(h = (selected.h - 10).coerceAtLeast(24f)))) }
                    SmallBtn("H+") { apply(layout.withItem(selected.copy(h = selected.h + 10))) }
                }
                OutlinedButton(
                    onClick = { apply(layout.removeItem(selected.id)); selectedId = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Delete item", color = Dhc6DesktopColors.Red, fontWeight = FontWeight.Black) }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Dhc6DesktopColors.BorderSoft)
            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    status = if (Dhc6UserData.writePanelLayout(layout.toJson())) "Saved to ~/.dhc6trainer/panel_layout.json" else "Save failed"
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
            ) { Text("Save panel", fontWeight = FontWeight.Black) }

            OutlinedButton(
                onClick = { apply(Dhc6UserData.loadPanelLayoutOrDefault()); selectedId = null; status = "Reloaded saved layout" },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            ) { Text("Reload saved", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black) }

            OutlinedButton(
                onClick = { Dhc6UserData.deletePanelLayout(); apply(PanelLayout.default()); selectedId = null; status = "Reverted to default layout" },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            ) { Text("Revert to default", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black) }

            Spacer(Modifier.height(4.dp))
            Text(status, color = Dhc6DesktopColors.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${layout.items.size} items", color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PaletteEntry(asset: PanelInstrumentAsset, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color(0xFF061F31), RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, Color(0xFF23607B)), RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val bmp = remember(asset.file) { DesktopImages.image(asset.resourcePath) }
        Box(Modifier.size(34.dp).background(Color(0xFF02070B), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = asset.file, modifier = Modifier.size(30.dp), filterQuality = FilterQuality.High)
            }
        }
        Text(asset.displayName, color = Dhc6DesktopColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

@Composable
private fun LabeledDropdown(label: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text(current, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt, fontSize = 12.sp) }, onClick = { onPick(opt); open = false })
                }
            }
        }
    }
}

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, color = Dhc6DesktopColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
