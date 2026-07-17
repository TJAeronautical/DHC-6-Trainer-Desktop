package com.dhc6trainer.desktop

import com.jme3.asset.AssetInfo
import com.jme3.asset.AssetLoader
import com.jme3.asset.AssetManager
import com.jme3.material.Material
import com.jme3.material.RenderState
import com.jme3.math.ColorRGBA
import com.jme3.math.FastMath
import com.jme3.math.Vector3f
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Mesh
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.VertexBuffer
import com.jme3.scene.shape.Box as JmeBox
import com.jme3.scene.shape.Cylinder
import com.jme3.scene.shape.Sphere
import com.jme3.texture.Texture
import com.jme3.texture.Texture2D
import com.jme3.util.BufferUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val KennBorekAircraftResourceRoot =
    "flight-sim/dhc6-kennborek-x/Simobjects/airplanes/dhc6_kennborek_x"
internal const val KennBorekTundraMdlPath =
    "$KennBorekAircraftResourceRoot/model.tundra/dhc6_tundra.mdl"
internal const val KennBorekSkiMdlPath =
    "$KennBorekAircraftResourceRoot/model.ski/dh6_twinSN_skis.mdl"

/** Real-world DHC-6 wingspan in meters, used to scale MDL model units. */
private const val Dhc6WingspanMeters = 19.812f

/**
 * jME loader for FS2002/FS2004-style RIFF MDL8 containers used by the bundled
 * Kenn Borek aircraft. Parses the compiled BGL chunk's new-style opcode lists
 * (0xB7 texture list, 0xB6 material list, 0xB5 vertex list, 0xB8 set-material,
 * 0xB9 indexed triangle draws) into textured jME meshes with the original
 * livery bitmaps. Falls back to a simple primitive stand-in if parsing fails.
 */
class FsxMdlNativeLoader : AssetLoader {
    override fun load(assetInfo: AssetInfo): Any {
        val bytes = assetInfo.openStream().use { it.readBytes() }
        val sourceName = assetInfo.key.name
        val parsed = runCatching { FsxMdlParser.parse(bytes) }.getOrNull()
        if (parsed != null && parsed.batches.isNotEmpty()) {
            return FsxMdlMeshBuilder.build(assetInfo.manager, parsed, sourceName)
        }
        return FsxMdlFallbackNode.build(assetInfo.manager, sourceName)
    }
}

internal object FsxMdlLoaderRegistry {
    fun register(assetManager: AssetManager) {
        runCatching { assetManager.registerLoader(FsxMdlNativeLoader::class.java, "mdl") }
    }
}

/* =====================================================================
   Binary parsing
   ===================================================================== */

internal class FsxMdlVertex(
    val x: Float, val y: Float, val z: Float,
    val nx: Float, val ny: Float, val nz: Float,
    val u: Float, val v: Float,
)

internal class FsxMdlMaterial(
    val diffuse: ColorRGBA,
    val specular: ColorRGBA,
    val emissive: ColorRGBA,
    val power: Float,
)

internal class FsxMdlBatch(
    val materialIndex: Int,
    val textureIndex: Int,
    val indices: MutableList<Int> = mutableListOf(),
)

internal class FsxMdlParsed(
    val textureNames: List<String>,
    val materials: List<FsxMdlMaterial>,
    val vertices: List<FsxMdlVertex>,
    val batches: List<FsxMdlBatch>,
)

internal object FsxMdlParser {

    fun parse(bytes: ByteArray): FsxMdlParsed {
        if (bytes.size < 12 || fourCc(bytes, 0) != "RIFF") {
            throw IOException("Not a RIFF FS model.")
        }
        val riffType = fourCc(bytes, 8)
        if (riffType != "MDL8") {
            throw IOException("RIFF type $riffType; expected FS MDL8.")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var offset = 12
        var bglOff = -1
        var bglSize = 0
        while (offset + 8 <= bytes.size) {
            val id = fourCc(bytes, offset)
            val size = buf.getInt(offset + 4)
            if (size < 0 || offset + 8 + size > bytes.size) break
            if (id == "BGL ") {
                bglOff = offset + 8
                bglSize = size
            }
            offset = offset + 8 + size + (size and 1)
        }
        if (bglOff < 0) throw IOException("No BGL chunk found.")
        val end = bglOff + bglSize

        // Locate the texture list, material list, and vertex list. They are
        // laid out consecutively near the start of the BGL stream; validate
        // each candidate strictly so control-flow bytes cannot false-match.
        var textures = emptyList<String>()
        var materials = emptyList<FsxMdlMaterial>()
        var vertices = emptyList<FsxMdlVertex>()
        var vertexListEnd = -1

        var p = bglOff
        while (p + 8 < end) {
            val op = buf.getShort(p).toInt() and 0xFFFF
            if (op == 0xB7 && textures.isEmpty()) {
                val n = buf.getShort(p + 2).toInt() and 0xFFFF
                if (n in 1..512 && p + 8 + n * 80 <= end) {
                    textures = readTextureList(bytes, p + 8, n)
                    p += 8 + n * 80
                    continue
                }
            }
            if (op == 0xB6 && materials.isEmpty() && textures.isNotEmpty()) {
                val n = buf.getShort(p + 2).toInt() and 0xFFFF
                if (n in 1..4096 && p + 8 + n * 68 <= end) {
                    materials = readMaterialList(buf, p + 8, n)
                    p += 8 + n * 68
                    continue
                }
            }
            if (op == 0xB5 && vertices.isEmpty() && materials.isNotEmpty()) {
                val n = buf.getShort(p + 2).toInt() and 0xFFFF
                if (n in 1..65535 && p + 8 + n * 32 <= end && plausibleVertices(buf, p + 8, n)) {
                    vertices = readVertexList(buf, p + 8, n)
                    vertexListEnd = p + 8 + n * 32
                    break
                }
            }
            p += 2
        }
        if (vertices.isEmpty()) throw IOException("No vertex list found in BGL stream.")

        val batches = readDrawCalls(
            buf = buf,
            start = vertexListEnd,
            end = end,
            vertexCount = vertices.size,
            materialCount = materials.size,
            textureCount = textures.size,
        )
        return FsxMdlParsed(textures, materials, vertices, batches)
    }

    private fun readTextureList(bytes: ByteArray, offset: Int, count: Int): List<String> =
        (0 until count).map { i ->
            val nameOff = offset + i * 80 + 16
            var len = 0
            while (len < 64 && bytes[nameOff + len].toInt() != 0) len++
            String(bytes, nameOff, len, Charsets.US_ASCII).trim()
        }

    private fun readMaterialList(buf: ByteBuffer, offset: Int, count: Int): List<FsxMdlMaterial> =
        (0 until count).map { i ->
            val o = offset + i * 68
            fun c(idx: Int) = ColorRGBA(
                buf.getFloat(o + idx * 4).coerceIn(0f, 1f),
                buf.getFloat(o + idx * 4 + 4).coerceIn(0f, 1f),
                buf.getFloat(o + idx * 4 + 8).coerceIn(0f, 1f),
                buf.getFloat(o + idx * 4 + 12).coerceIn(0f, 1f),
            )
            FsxMdlMaterial(
                diffuse = c(0),
                specular = c(8),
                emissive = c(12),
                power = buf.getFloat(o + 64).coerceIn(0f, 128f),
            )
        }

    private fun readVertexList(buf: ByteBuffer, offset: Int, count: Int): List<FsxMdlVertex> =
        (0 until count).map { i ->
            val o = offset + i * 32
            FsxMdlVertex(
                x = buf.getFloat(o), y = buf.getFloat(o + 4), z = buf.getFloat(o + 8),
                nx = buf.getFloat(o + 12), ny = buf.getFloat(o + 16), nz = buf.getFloat(o + 20),
                u = buf.getFloat(o + 24), v = buf.getFloat(o + 28),
            )
        }

    private fun plausibleVertices(buf: ByteBuffer, offset: Int, count: Int): Boolean {
        val sample = minOf(count, 16)
        for (i in 0 until sample) {
            val o = offset + i * 32
            for (f in 0 until 8) {
                val v = buf.getFloat(o + f * 4)
                if (v.isNaN() || v.isInfinite() || FastMath.abs(v) > 1_000_000f) return false
            }
            // Normal should be roughly unit length.
            val nx = buf.getFloat(o + 12)
            val ny = buf.getFloat(o + 16)
            val nz = buf.getFloat(o + 20)
            val lenSq = nx * nx + ny * ny + nz * nz
            if (lenSq < 0.5f || lenSq > 2.0f) return false
        }
        return true
    }

    /**
     * Scans the remainder of the stream for validated set-material (0xB8) and
     * indexed-triangle-draw (0xB9) records, accumulating triangles grouped by
     * (material, texture). Unknown control-flow bytes are skipped 2 at a time;
     * strict validation of each record keeps false positives out.
     */
    private fun readDrawCalls(
        buf: ByteBuffer,
        start: Int,
        end: Int,
        vertexCount: Int,
        materialCount: Int,
        textureCount: Int,
    ): List<FsxMdlBatch> {
        val batches = LinkedHashMap<Long, FsxMdlBatch>()
        var curMat = 0
        var curTex = -1
        var p = start
        while (p + 8 <= end) {
            val op = buf.getShort(p).toInt() and 0xFFFF
            if (op == 0xB8) {
                val m = buf.getShort(p + 2).toInt() and 0xFFFF
                val t = buf.getShort(p + 4).toInt() and 0xFFFF
                if (m < materialCount && (t < textureCount || t == 0xFFFF)) {
                    curMat = m
                    curTex = if (t == 0xFFFF) -1 else t
                    p += 6
                    continue
                }
            }
            if (op == 0xB9) {
                val base = buf.getShort(p + 2).toInt() and 0xFFFF
                val vCount = buf.getShort(p + 4).toInt() and 0xFFFF
                val iCount = buf.getShort(p + 6).toInt() and 0xFFFF
                if (iCount > 0 && iCount % 3 == 0 && vCount > 0 &&
                    base + vCount <= vertexCount && p + 8 + iCount * 2 <= end
                ) {
                    var valid = true
                    for (i in 0 until iCount) {
                        val idx = buf.getShort(p + 8 + i * 2).toInt() and 0xFFFF
                        if (idx >= vCount) {
                            valid = false
                            break
                        }
                    }
                    if (valid) {
                        val key = (curMat.toLong() shl 32) or (curTex.toLong() and 0xFFFFFFFFL)
                        val batch = batches.getOrPut(key) { FsxMdlBatch(curMat, curTex) }
                        for (i in 0 until iCount) {
                            batch.indices += base + (buf.getShort(p + 8 + i * 2).toInt() and 0xFFFF)
                        }
                        p += 8 + iCount * 2
                        continue
                    }
                }
            }
            p += 2
        }
        return batches.values.toList()
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)
}

/* =====================================================================
   Mesh + material construction
   ===================================================================== */

private object FsxMdlMeshBuilder {

    fun build(assetManager: AssetManager, parsed: FsxMdlParsed, sourceName: String): Spatial {
        val root = Node("FSX MDL ${sourceName.substringAfterLast('/')}")

        // Uniform scale: map the widest span (wingtips) to the real wingspan.
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        for (v in parsed.vertices) {
            if (v.x < minX) minX = v.x
            if (v.x > maxX) maxX = v.x
        }
        val span = (maxX - minX).coerceAtLeast(1f)
        val scale = Dhc6WingspanMeters / span

        val textureCache = HashMap<Int, Texture2D?>()
        for (batch in parsed.batches) {
            if (batch.indices.isEmpty()) continue
            val mesh = buildMesh(parsed.vertices, batch.indices, scale)
            val texName = parsed.textureNames.getOrNull(batch.textureIndex)
            val geomName = "fsx-tex-" + (texName?.substringBeforeLast('.') ?: "mat${batch.materialIndex}")
            val geom = Geometry(geomName, mesh)
            val material = parsed.materials.getOrNull(batch.materialIndex)
            val texture = textureCache.getOrPut(batch.textureIndex) {
                texName?.let { loadAircraftTexture(sourceName, it) }
            }
            geom.material = buildMaterial(assetManager, material, texture)
            val diffuseAlpha = material?.diffuse?.a ?: 1f
            if (diffuseAlpha < 0.98f) {
                geom.queueBucket = RenderQueue.Bucket.Transparent
            }
            root.attachChild(geom)
        }
        root.shadowMode = RenderQueue.ShadowMode.CastAndReceive
        return root
    }

    private fun buildMesh(vertices: List<FsxMdlVertex>, indices: List<Int>, scale: Float): Mesh {
        // Compact: keep only referenced vertices, remap indices.
        val remap = HashMap<Int, Int>()
        val order = ArrayList<Int>()
        for (idx in indices) {
            remap.getOrPut(idx) {
                order += idx
                order.size - 1
            }
        }
        val positions = BufferUtils.createFloatBuffer(order.size * 3)
        val normals = BufferUtils.createFloatBuffer(order.size * 3)
        val texCoords = BufferUtils.createFloatBuffer(order.size * 2)
        for (idx in order) {
            val v = vertices[idx]
            // FS: X right, Y up, Z forward. jME: X right, Y up, Z toward viewer.
            positions.put(v.x * scale).put(v.y * scale).put(-v.z * scale)
            normals.put(v.nx).put(v.ny).put(-v.nz)
            // DirectX V origin is top; GL is bottom.
            texCoords.put(v.u).put(1f - v.v)
        }
        val indexBuffer = BufferUtils.createIntBuffer(indices.size)
        // Z was mirrored, so reverse the winding of each triangle.
        var i = 0
        while (i + 2 < indices.size) {
            indexBuffer.put(remap.getValue(indices[i + 2]))
            indexBuffer.put(remap.getValue(indices[i + 1]))
            indexBuffer.put(remap.getValue(indices[i]))
            i += 3
        }
        return Mesh().apply {
            setBuffer(VertexBuffer.Type.Position, 3, positions)
            setBuffer(VertexBuffer.Type.Normal, 3, normals)
            setBuffer(VertexBuffer.Type.TexCoord, 2, texCoords)
            setBuffer(VertexBuffer.Type.Index, 3, indexBuffer)
            updateBound()
            setStatic()
        }
    }

    private fun buildMaterial(
        assetManager: AssetManager,
        mdlMaterial: FsxMdlMaterial?,
        texture: Texture2D?,
    ): Material {
        val diffuse = mdlMaterial?.diffuse ?: ColorRGBA(0.8f, 0.8f, 0.8f, 1f)
        val specular = mdlMaterial?.specular ?: ColorRGBA(0.2f, 0.2f, 0.2f, 1f)
        val shininess = (mdlMaterial?.power ?: 16f).coerceIn(1f, 96f)
        return Material(assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
            setBoolean("UseMaterialColors", true)
            setColor("Diffuse", if (texture != null) ColorRGBA(1f, 1f, 1f, diffuse.a) else diffuse)
            setColor(
                "Ambient",
                if (texture != null) {
                    ColorRGBA(0.75f, 0.75f, 0.75f, 1f)
                } else {
                    ColorRGBA(diffuse.r * 0.75f, diffuse.g * 0.75f, diffuse.b * 0.75f, 1f)
                },
            )
            setColor("Specular", specular)
            setFloat("Shininess", shininess)
            if (texture != null) {
                setTexture("DiffuseMap", texture)
            }
            if (diffuse.a < 0.98f) {
                additionalRenderState.blendMode = RenderState.BlendMode.Alpha
                additionalRenderState.isDepthWrite = false
            }
        }
    }

    /** Livery texture folders, in preference order. */
    private val textureFolders = listOf(
        "$KennBorekAircraftResourceRoot/texture.kennborek_t",
        "$KennBorekAircraftResourceRoot/texture.kennborek_w",
    )

    private fun loadAircraftTexture(sourceName: String, name: String): Texture2D? {
        FsxAircraftTextureRegistry.readTextureBytes(sourceName, name)
            ?.let(::textureFromBytes)
            ?.let { return it }

        for (folder in textureFolders) {
            val bytes = readResourceBytes("$folder/$name") ?: continue
            textureFromBytes(bytes)?.let { return it }
        }
        return null
    }

    private fun textureFromBytes(bytes: ByteArray): Texture2D? {
        val image = FsxTextureDecoder.decode(bytes) ?: return null
        return Texture2D(image).apply {
            setWrap(Texture.WrapMode.Repeat)
            magFilter = Texture.MagFilter.Bilinear
            minFilter = Texture.MinFilter.Trilinear
            anisotropicFilter = 4
        }
    }

    private fun readResourceBytes(path: String): ByteArray? {
        val loader = Thread.currentThread().contextClassLoader
        val stream = loader?.getResourceAsStream(path)
            ?: FsxMdlMeshBuilder::class.java.classLoader.getResourceAsStream(path)
            ?: FsxMdlMeshBuilder::class.java.getResourceAsStream("/$path")
        return stream?.use { it.readBytes() }
    }
}

/* =====================================================================
   Primitive fallback (used only when BGL parsing fails)
   ===================================================================== */

private object FsxMdlFallbackNode {
    fun build(assetManager: AssetManager, sourceName: String): Spatial {
        val isSki = sourceName.contains("ski", ignoreCase = true)
        val root = Node("FSX MDL fallback ${sourceName.substringAfterLast('/')}")
        val red = material(assetManager, ColorRGBA(0.86f, 0.09f, 0.03f, 1f), 18f)
        val white = material(assetManager, ColorRGBA(0.92f, 0.92f, 0.88f, 1f), 16f)
        val dark = material(assetManager, ColorRGBA(0.02f, 0.03f, 0.04f, 1f), 10f)
        val glass = material(assetManager, ColorRGBA(0.15f, 0.34f, 0.42f, 1f), 28f)
        val metal = material(assetManager, ColorRGBA(0.54f, 0.56f, 0.55f, 1f), 32f)

        root.attachBox("Fuselage white", Vector3f(1.62f, 0.22f, 0.22f), Vector3f(0f, 0.14f, 0f), white)
        root.attachBox("Fuselage red stripe", Vector3f(1.50f, 0.045f, 0.235f), Vector3f(0.05f, 0.17f, 0f), red)
        root.attachSphere("Nose", 0.25f, Vector3f(-1.72f, 0.14f, 0f), white, scale = Vector3f(1.15f, 0.85f, 0.85f))
        root.attachBox("Cockpit glazing", Vector3f(0.28f, 0.12f, 0.235f), Vector3f(-1.35f, 0.33f, 0f), glass)
        root.attachBox("Tail boom", Vector3f(0.48f, 0.13f, 0.14f), Vector3f(1.67f, 0.23f, 0f), white)
        root.attachBox("Main wing", Vector3f(0.18f, 0.035f, 1.95f), Vector3f(-0.28f, 0.48f, 0f), white)
        root.attachBox("Horizontal stabilizer", Vector3f(0.18f, 0.03f, 0.78f), Vector3f(1.92f, 0.48f, 0f), white)
        root.attachBox("Vertical stabilizer", Vector3f(0.18f, 0.50f, 0.05f), Vector3f(1.95f, 0.75f, 0f), red)

        listOf(-0.72f, 0.72f).forEachIndexed { index, z ->
            root.attachBox("PT6 nacelle ${index + 1}", Vector3f(0.30f, 0.12f, 0.13f), Vector3f(-0.42f, 0.31f, z), red)
            root.attachCylinder("Prop disc ${index + 1}", 0.34f, 0.012f, Vector3f(-0.76f, 0.31f, z), dark, rotateY = true)
        }
        if (isSki) {
            listOf(-0.45f, 0.45f).forEachIndexed { index, z ->
                root.attachBox("Landing ski ${index + 1}", Vector3f(0.58f, 0.025f, 0.075f), Vector3f(-0.18f, -0.22f, z), metal)
            }
        } else {
            listOf(
                Vector3f(-0.92f, -0.16f, 0f),
                Vector3f(-0.05f, -0.18f, -0.52f),
                Vector3f(-0.05f, -0.18f, 0.52f),
            ).forEachIndexed { index, pos ->
                root.attachCylinder("Tundra wheel ${index + 1}", 0.12f, 0.07f, pos, dark, rotateX = true)
            }
        }
        root.shadowMode = RenderQueue.ShadowMode.CastAndReceive
        return root
    }

    private fun material(assetManager: AssetManager, diffuse: ColorRGBA, shininess: Float): Material =
        Material(assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
            setBoolean("UseMaterialColors", true)
            setColor("Diffuse", diffuse)
            setColor("Ambient", ColorRGBA(diffuse.r * 0.36f, diffuse.g * 0.36f, diffuse.b * 0.36f, 1f))
            setColor("Specular", ColorRGBA(0.34f, 0.34f, 0.34f, 1f))
            setFloat("Shininess", shininess)
        }

    private fun Node.attachBox(name: String, extents: Vector3f, translation: Vector3f, mat: Material) {
        attachChild(
            Geometry(name, JmeBox(extents.x, extents.y, extents.z)).apply {
                setLocalTranslation(translation)
                material = mat
            },
        )
    }

    private fun Node.attachSphere(
        name: String,
        radius: Float,
        translation: Vector3f,
        mat: Material,
        scale: Vector3f = Vector3f(1f, 1f, 1f),
    ) {
        attachChild(
            Geometry(name, Sphere(16, 24, radius)).apply {
                setLocalTranslation(translation)
                setLocalScale(scale)
                material = mat
            },
        )
    }

    private fun Node.attachCylinder(
        name: String,
        radius: Float,
        depth: Float,
        translation: Vector3f,
        mat: Material,
        rotateX: Boolean = false,
        rotateY: Boolean = false,
    ) {
        attachChild(
            Geometry(name, Cylinder(2, 36, radius, depth, true)).apply {
                if (rotateX) rotate(FastMath.HALF_PI, 0f, 0f)
                if (rotateY) rotate(0f, FastMath.HALF_PI, 0f)
                setLocalTranslation(translation)
                material = mat
            },
        )
    }
}
