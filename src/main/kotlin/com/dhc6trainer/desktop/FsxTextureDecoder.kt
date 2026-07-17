package com.dhc6trainer.desktop

import com.jme3.texture.Image
import com.jme3.texture.image.ColorSpace
import com.jme3.util.BufferUtils
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * Decoder for Flight Simulator "extended" BMP textures.
 *
 * FS2002/FS2004 aircraft textures are BMP containers whose pixel payload is
 * DXT1/DXT3 compressed (the BITMAPINFOHEADER biCompression field carries the
 * 'DXT1'/'DXT3' FourCC instead of BI_RGB). Plain 8/16/24/32-bit BMPs are also
 * used for small masks. This decodes any of them into an RGBA8 jME [Image].
 *
 * Row order: FS stores the DXT payload bottom-up (a vertically flipped DDS),
 * which is exactly the row order OpenGL expects, so blocks are emitted in file
 * order and the model's DirectX V coordinates are flipped at mesh build time.
 */
internal object FsxTextureDecoder {
    private const val FOURCC_DXT1 = 0x31545844
    private const val FOURCC_DXT3 = 0x33545844

    fun decode(bytes: ByteArray): Image? = runCatching {
        if (bytes.size < 54 || bytes[0] != 'B'.code.toByte() || bytes[1] != 'M'.code.toByte()) {
            return@runCatching null
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val dataOffset = buf.getInt(10)
        val width = buf.getInt(18)
        val height = buf.getInt(22)
        val compression = buf.getInt(30)
        if (width <= 0 || height == 0) return@runCatching null

        when (compression) {
            FOURCC_DXT1 -> decodeDxt(bytes, dataOffset, width, Math.abs(height), alphaBlock = false)
            FOURCC_DXT3 -> decodeDxt(bytes, dataOffset, width, Math.abs(height), alphaBlock = true)
            else -> decodeViaImageIo(bytes)
        }
    }.getOrNull()

    private fun decodeDxt(bytes: ByteArray, offset: Int, width: Int, height: Int, alphaBlock: Boolean): Image? {
        val blockSize = if (alphaBlock) 16 else 8
        val blocksX = (width + 3) / 4
        val blocksY = (height + 3) / 4
        if (offset + blocksX * blocksY * blockSize > bytes.size) return null

        val out = BufferUtils.createByteBuffer(width * height * 4)
        val rgba = ByteArray(width * height * 4)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val colors = IntArray(4)

        var src = offset
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var alphaBits = 0L
                if (alphaBlock) {
                    alphaBits = buf.getLong(src)
                    src += 8
                }
                val c0 = buf.getShort(src).toInt() and 0xFFFF
                val c1 = buf.getShort(src + 2).toInt() and 0xFFFF
                val lookup = buf.getInt(src + 4)
                src += 8

                val r0 = (c0 ushr 11 and 0x1F) * 255 / 31
                val g0 = (c0 ushr 5 and 0x3F) * 255 / 63
                val b0 = (c0 and 0x1F) * 255 / 31
                val r1 = (c1 ushr 11 and 0x1F) * 255 / 31
                val g1 = (c1 ushr 5 and 0x3F) * 255 / 63
                val b1 = (c1 and 0x1F) * 255 / 31
                colors[0] = argb(255, r0, g0, b0)
                colors[1] = argb(255, r1, g1, b1)
                if (alphaBlock || c0 > c1) {
                    colors[2] = argb(255, (2 * r0 + r1) / 3, (2 * g0 + g1) / 3, (2 * b0 + b1) / 3)
                    colors[3] = argb(255, (r0 + 2 * r1) / 3, (g0 + 2 * g1) / 3, (b0 + 2 * b1) / 3)
                } else {
                    colors[2] = argb(255, (r0 + r1) / 2, (g0 + g1) / 2, (b0 + b1) / 2)
                    colors[3] = 0
                }

                for (ty in 0 until 4) {
                    val y = by * 4 + ty
                    if (y >= height) break
                    for (tx in 0 until 4) {
                        val x = bx * 4 + tx
                        if (x >= width) continue
                        val sel = lookup ushr ((ty * 4 + tx) * 2) and 0x3
                        val c = colors[sel]
                        val a = if (alphaBlock) {
                            val nibble = (alphaBits ushr ((ty * 4 + tx) * 4) and 0xF).toInt()
                            nibble * 255 / 15
                        } else {
                            c ushr 24 and 0xFF
                        }
                        val dst = (y * width + x) * 4
                        rgba[dst] = (c ushr 16 and 0xFF).toByte()
                        rgba[dst + 1] = (c ushr 8 and 0xFF).toByte()
                        rgba[dst + 2] = (c and 0xFF).toByte()
                        rgba[dst + 3] = a.toByte()
                    }
                }
            }
        }
        out.put(rgba)
        out.flip()
        return Image(Image.Format.RGBA8, width, height, out, ColorSpace.sRGB)
    }

    private fun decodeViaImageIo(bytes: ByteArray): Image? {
        val awt = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val width = awt.width
        val height = awt.height
        val out = BufferUtils.createByteBuffer(width * height * 4)
        // AWT rows are top-down; emit bottom-up to match the DXT path and GL.
        for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                val p = awt.getRGB(x, y)
                out.put((p ushr 16 and 0xFF).toByte())
                out.put((p ushr 8 and 0xFF).toByte())
                out.put((p and 0xFF).toByte())
                out.put((p ushr 24 and 0xFF).toByte())
            }
        }
        out.flip()
        return Image(Image.Format.RGBA8, width, height, out, ColorSpace.sRGB)
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
