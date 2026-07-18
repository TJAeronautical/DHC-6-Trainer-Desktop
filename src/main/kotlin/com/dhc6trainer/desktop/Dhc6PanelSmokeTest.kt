package com.dhc6trainer.desktop

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Headless verification of the 2D panel: composites the default (or saved)
 * panel layout to a PNG using the same resource paths the app renders from, so
 * the layout coordinates and that every instrument image resolves can be
 * eyeballed without launching the GUI. Run:
 *   gradlew runPanelSmokeTest
 */
fun main() {
    var failures = 0
    val layout = Dhc6UserData.loadPanelLayoutOrDefault()
    println("Layout: ${layout.canvasW.toInt()}x${layout.canvasH.toInt()}, ${layout.items.size} items")

    val img = BufferedImage(layout.canvasW.toInt(), layout.canvasH.toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.color = Color(0x1A, 0x20, 0x26)
    g.fillRect(0, 0, img.width, img.height)

    val loader = Thread.currentThread().contextClassLoader
    layout.items.forEach { item ->
        val ix = item.x.toInt(); val iy = item.y.toInt(); val iw = item.w.toInt(); val ih = item.h.toInt()
        when (item.kind) {
            PanelItemKind.IMAGE -> {
                val stream = loader?.getResourceAsStream(item.resourcePath)
                if (stream == null) {
                    println("MISSING  ${item.id} -> ${item.resourcePath}"); failures++
                    g.color = Color(0x33, 0x40, 0x4A); g.fillRect(ix, iy, iw, ih)
                    return@forEach
                }
                val tex = stream.use { ImageIO.read(it) }
                if (tex == null) { println("UNREADABLE ${item.id} -> ${item.resourcePath}"); failures++; return@forEach }
                g.drawImage(tex, ix, iy, iw, ih, null)
            }
            PanelItemKind.LABEL -> {
                g.color = Color(0x0C, 0x16, 0x1F); g.fillRect(ix, iy, iw, ih)
                g.color = Color(0x9F, 0xD6, 0xF0)
                g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, (ih * 0.5f).toInt().coerceAtLeast(8))
                val t = item.text.ifBlank { item.role }
                g.drawString(t, ix + (iw - g.fontMetrics.stringWidth(t)) / 2, iy + (ih * 0.68f).toInt())
            }
            PanelItemKind.SWITCH -> {
                g.color = Color(0x10, 0x18, 0x1F); g.fillRect(ix, iy, iw, ih)
                g.color = Color(0x3A, 0x46, 0x50); g.drawRect(ix, iy, iw, ih)
                g.color = Color(0xDC, 0xE7, 0xF0)
                g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, (ih * 0.18f).toInt().coerceAtLeast(7))
                g.drawString(item.role, ix + (iw - g.fontMetrics.stringWidth(item.role)) / 2, iy + (ih * 0.26f).toInt())
                val on = item.action != PanelAction.NONE
                g.color = if (on) Color(0x6B, 0xE6, 0x75) else Color(0x6B, 0x76, 0x80)
                g.fillRoundRect(ix + (iw * 0.4f).toInt(), iy + (ih * 0.42f).toInt(), (iw * 0.2f).toInt(), (ih * 0.36f).toInt(), 8, 8)
            }
            PanelItemKind.CB_PANEL -> {
                g.color = Color(0x14, 0x1A, 0x20); g.fillRect(ix, iy, iw, ih)
                g.color = Color(0x3A, 0x46, 0x50); g.drawRect(ix, iy, iw, ih)
                val titleH = (ih * 0.13f).toInt()
                g.color = Color(0x0C, 0x16, 0x1F); g.fillRect(ix, iy, iw, titleH)
                g.color = Color(0x9F, 0xD6, 0xF0)
                g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, (titleH * 0.6f).toInt().coerceAtLeast(8))
                g.drawString(item.role, ix + 8, iy + (titleH * 0.78f).toInt())
                val breakers = item.cbBreakers
                val cols = 6; val rows = (breakers.size + cols - 1) / cols
                val cellW = iw / cols; val cellH = if (rows > 0) (ih - titleH) / rows else ih
                g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, (cellH * 0.16f).toInt().coerceIn(6, 16))
                breakers.forEachIndexed { i, name ->
                    val c = i % cols; val r = i / cols
                    val cx = ix + c * cellW + cellW / 2; val cy = iy + titleH + r * cellH + (cellH * 0.34f).toInt()
                    val rad = (minOf(cellW, cellH) * 0.16f).toInt().coerceAtLeast(3)
                    g.color = Color(0x05, 0x09, 0x0D); g.fillOval(cx - rad, cy - rad, rad * 2, rad * 2)
                    g.color = Color(0x5A, 0x6A, 0x75); g.drawOval(cx - rad, cy - rad, rad * 2, rad * 2)
                    g.color = Color(0xB9, 0xC6, 0xD1)
                    g.drawString(name, cx - g.fontMetrics.stringWidth(name) / 2, cy + rad + (cellH * 0.18f).toInt())
                }
            }
        }
    }
    g.dispose()

    val out = File(System.getProperty("java.io.tmpdir"), "dhc6-panel-smoke/panel_default.png")
    out.parentFile.mkdirs()
    ImageIO.write(img, "png", out)
    println("Wrote $out")

    // JSON round-trip check.
    val json = layout.toJson()
    val reparsed = PanelLayout.fromJson(json)
    val ok = reparsed != null && reparsed.items.size == layout.items.size
    println("${if (ok) "OK  " else "FAIL"} JSON round-trip -> ${reparsed?.items?.size} items")
    if (!ok) failures++

    println(if (failures == 0) "PANEL CHECKS PASSED" else "$failures PANEL CHECK(S) FAILED")
    exitProcess(if (failures == 0) 0 else 1)
}
