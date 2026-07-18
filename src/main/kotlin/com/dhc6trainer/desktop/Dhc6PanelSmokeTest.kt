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
        val stream = loader?.getResourceAsStream(item.resourcePath)
        if (stream == null) {
            println("MISSING  ${item.id} -> ${item.resourcePath}")
            failures++
            g.color = Color(0x33, 0x40, 0x4A)
            g.fillRect(item.x.toInt(), item.y.toInt(), item.w.toInt(), item.h.toInt())
            return@forEach
        }
        val tex = stream.use { ImageIO.read(it) }
        if (tex == null) {
            println("UNREADABLE ${item.id} -> ${item.resourcePath}")
            failures++
            return@forEach
        }
        g.drawImage(tex, item.x.toInt(), item.y.toInt(), item.w.toInt(), item.h.toInt(), null)
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
