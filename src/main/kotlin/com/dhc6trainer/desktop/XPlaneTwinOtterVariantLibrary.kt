package com.dhc6trainer.desktop

import com.jme3.asset.AssetManager
import com.jme3.material.Material
import com.jme3.material.RenderState
import com.jme3.math.ColorRGBA
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Mesh
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.VertexBuffer
import com.jme3.texture.Image
import com.jme3.texture.Texture
import com.jme3.texture.Texture2D
import com.jme3.texture.image.ColorSpace
import com.jme3.util.BufferUtils
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile
import javax.imageio.ImageIO

internal data class XPlaneTwinOtterVariantPackage(
    val id: String,
    val label: String,
    val zipPath: Path,
    val rootEntry: String,
    val exteriorObjectEntries: List<String>,
    val cockpitObjectEntry: String?,
    val textureCount: Int,
    val objectCount: Int,
) {
    val statusBadge: String = "$label OBJ8"
    val summary: String =
        "$label loaded from ${zipPath.fileName} - $objectCount objects, $textureCount textures"
}

internal data class XPlaneLoadedAircraft(
    val root: Node,
    val exteriorNodes: List<Spatial>,
    val interiorNodes: List<Spatial>,
    val cockpitNode: Spatial,
)

internal object XPlaneTwinOtterVariantLibrary {
    private data class KnownPackage(
        val id: String,
        val label: String,
        val fileNames: List<String>,
        val preferredObjects: List<String>,
    )

    private val knownPackages = listOf(
        KnownPackage(
            id = "dhc63s-fp",
            label = "DHC-6-300 float",
            fileNames = listOf(
                "DHC63S Twin Otter FP_1.zip",
                "DHC63S Twin Otter FP v1141 (1).zip",
            ),
            preferredObjects = listOf(
                "objects/dhc6.obj",
                "objects/bluntnose.obj",
                "objects/cabin.obj",
                "objects/seats.obj",
                "objects/RearDoorsFloat.obj",
                "objects/floats-Seaplane.obj",
                "objects/EngStruts.obj",
                "objects/Glass_INN.obj",
                "objects/glass.obj",
            ),
        ),
        KnownPackage(
            id = "dhc63l-lp",
            label = "DHC-6-300 long nose",
            fileNames = listOf(
                "DHC63L Twin Otter LP_1.zip",
                "DHC63L Twin Otter LP v1141.zip",
            ),
            preferredObjects = listOf(
                "objects/dhc6.obj",
                "objects/longnose.obj",
                "objects/cabin.obj",
                "objects/seats.obj",
                "objects/RearDoors.obj",
                "objects/gear.obj",
                "objects/EngStruts.obj",
                "objects/Glass_INN.obj",
                "objects/glass.obj",
            ),
        ),
        KnownPackage(
            id = "dhc61-t",
            label = "DHC-6-100 tundra",
            fileNames = listOf(
                "DHC61 Twin Otter T_1.zip",
                "DHC61 Twin Otter T v1141.zip",
            ),
            preferredObjects = listOf(
                "objects/dhc6.obj",
                "objects/bluntnose.obj",
                "objects/cabin.obj",
                "objects/seats.obj",
                "objects/RearDoorsFloat.obj",
                "objects/gear_tundra.obj",
                "objects/EngStruts.obj",
                "objects/Glass_INN.obj",
                "objects/glass.obj",
            ),
        ),
    )

    fun loadAuto(): List<XPlaneTwinOtterVariantPackage> =
        knownPackages.mapNotNull { known ->
            known.fileNames
                .flatMap(::candidatePaths)
                .firstOrNull { Files.isRegularFile(it) }
                ?.let { loadPackage(known, it) }
        }

    private fun candidatePaths(fileName: String): List<Path> {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        val userDir = System.getProperty("user.dir")?.takeIf { it.isNotBlank() }
        return buildList {
            add(Paths.get(fileName))
            userDir?.let { add(Paths.get(it, fileName)) }
            home?.let { add(Paths.get(it, "Downloads", fileName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", fileName)) }
            home?.let { add(Paths.get(it, "OneDrive", "Desktop", "My App Data", "ZIP Files", "Files", "Desktop", fileName)) }
        }.distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
    }

    private fun loadPackage(
        known: KnownPackage,
        zipPath: Path,
    ): XPlaneTwinOtterVariantPackage? = runCatching {
        ZipFile(zipPath.toFile()).use { zip ->
            val entries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filterNot { it.startsWith("__MACOSX/") }
                .toList()
            val root = entries.firstOrNull()?.substringBefore('/') ?: return null
            val objects = entries.filter { it.endsWith(".obj", ignoreCase = true) }
            val textures = entries.count { name ->
                name.endsWith(".png", ignoreCase = true) ||
                    name.endsWith(".jpg", ignoreCase = true) ||
                    name.endsWith(".jpeg", ignoreCase = true)
            }
            val exterior = known.preferredObjects
                .map { "$root/$it" }
                .filter { it in entries }
            XPlaneTwinOtterVariantPackage(
                id = known.id,
                label = known.label,
                zipPath = zipPath,
                rootEntry = root,
                exteriorObjectEntries = exterior,
                cockpitObjectEntry = "$root/Twin Otter_cockpit.obj".takeIf { it in entries },
                textureCount = textures,
                objectCount = objects.size,
            )
        }
    }.getOrNull()
}

internal object XPlaneObj8Loader {
    private data class ObjVertex(
        val x: Float,
        val y: Float,
        val z: Float,
        val nx: Float,
        val ny: Float,
        val nz: Float,
        val u: Float,
        val v: Float,
    )

    private data class ObjMeshData(
        val textureName: String?,
        val vertices: List<ObjVertex>,
        val indices: List<Int>,
    )

    fun loadAircraft(
        assetManager: AssetManager,
        variantPackage: XPlaneTwinOtterVariantPackage,
    ): XPlaneLoadedAircraft? =
        runCatching {
            ZipFile(variantPackage.zipPath.toFile()).use { zip ->
                val root = Node("X-Plane ${variantPackage.label}")
                val textureCache = HashMap<String, Texture2D?>()
                val exteriorNodes = ArrayList<Spatial>()
                val interiorNodes = ArrayList<Spatial>()
                variantPackage.exteriorObjectEntries.forEach { entry ->
                    runCatching { loadObject(assetManager, zip, entry, textureCache) }
                        .getOrNull()
                        ?.let { spatial ->
                            root.attachChild(spatial)
                            if (entry.endsWith("/cabin.obj", ignoreCase = true)) {
                                interiorNodes += spatial
                            } else {
                                exteriorNodes += spatial
                            }
                        }
                }
                val cockpit = variantPackage.cockpitObjectEntry
                    ?.let { entry ->
                        runCatching { loadObject(assetManager, zip, entry, textureCache) }.getOrNull()
                    }
                    ?: return@use null
                root.attachChild(cockpit)
                XPlaneLoadedAircraft(root, exteriorNodes, interiorNodes, cockpit)
            }
        }.getOrNull()

    private fun loadObject(
        assetManager: AssetManager,
        zip: ZipFile,
        entryName: String,
        textureCache: MutableMap<String, Texture2D?>,
    ): Spatial? {
        val entry = zip.getEntry(entryName) ?: return null
        val text = zip.getInputStream(entry).bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
        val data = parseObj8(text) ?: return null
        if (data.vertices.isEmpty() || data.indices.isEmpty()) return null

        val mesh = buildMesh(data.vertices, data.indices)
        val texture = data.textureName
            ?.let { resolveTextureEntry(entryName, it) }
            ?.let { textureEntry ->
                textureCache.getOrPut(textureEntry.lowercase()) {
                    loadPngTexture(zip, textureEntry)
                }
            }
        return Geometry(entryName.substringAfterLast('/'), mesh).apply {
            material = buildMaterial(assetManager, texture)
            if (texture != null) {
                queueBucket = RenderQueue.Bucket.Transparent
            }
            shadowMode = RenderQueue.ShadowMode.CastAndReceive
        }
    }

    private fun parseObj8(text: String): ObjMeshData? {
        var textureName: String? = null
        val vertices = ArrayList<ObjVertex>()
        val indexPool = ArrayList<Int>()
        val drawIndices = ArrayList<Int>()

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split(' ', '\t').filter { it.isNotBlank() }
            when (parts.firstOrNull()) {
                "TEXTURE" -> textureName = parts.getOrNull(1)
                "VT" -> {
                    if (parts.size >= 9) {
                        vertices += ObjVertex(
                            x = parts[1].toFloatOrNull() ?: 0f,
                            y = parts[2].toFloatOrNull() ?: 0f,
                            z = parts[3].toFloatOrNull() ?: 0f,
                            nx = parts[4].toFloatOrNull() ?: 0f,
                            ny = parts[5].toFloatOrNull() ?: 1f,
                            nz = parts[6].toFloatOrNull() ?: 0f,
                            u = parts[7].toFloatOrNull() ?: 0f,
                            v = parts[8].toFloatOrNull() ?: 0f,
                        )
                    }
                }
                "IDX" -> parts.getOrNull(1)?.toIntOrNull()?.let(indexPool::add)
                "IDX10" -> parts.drop(1).forEach { value ->
                    value.toIntOrNull()?.let(indexPool::add)
                }
                "TRIS" -> {
                    val start = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val count = parts.getOrNull(2)?.toIntOrNull() ?: return@forEach
                    if (start >= 0 && count > 0 && start + count <= indexPool.size) {
                        drawIndices += indexPool.subList(start, start + count)
                    }
                }
            }
        }
        return ObjMeshData(textureName, vertices, drawIndices)
    }

    private fun buildMesh(vertices: List<ObjVertex>, indices: List<Int>): Mesh {
        val remap = HashMap<Int, Int>()
        val order = ArrayList<Int>()
        indices.forEach { index ->
            remap.getOrPut(index) {
                order += index
                order.size - 1
            }
        }

        val positions = BufferUtils.createFloatBuffer(order.size * 3)
        val normals = BufferUtils.createFloatBuffer(order.size * 3)
        val texCoords = BufferUtils.createFloatBuffer(order.size * 2)
        order.forEach { index ->
            val v = vertices[index.coerceIn(0, vertices.lastIndex)]
            positions.put(v.x).put(v.y).put(v.z)
            normals.put(v.nx).put(v.ny).put(v.nz)
            texCoords.put(v.u).put(1f - v.v)
        }

        val indexBuffer = BufferUtils.createIntBuffer(indices.size)
        indices.forEach { index -> indexBuffer.put(remap.getValue(index)) }
        return Mesh().apply {
            setBuffer(VertexBuffer.Type.Position, 3, positions)
            setBuffer(VertexBuffer.Type.Normal, 3, normals)
            setBuffer(VertexBuffer.Type.TexCoord, 2, texCoords)
            setBuffer(VertexBuffer.Type.Index, 3, indexBuffer)
            updateBound()
            setStatic()
        }
    }

    private fun buildMaterial(assetManager: AssetManager, texture: Texture2D?): Material =
        Material(assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
            setBoolean("UseMaterialColors", true)
            setColor("Diffuse", if (texture == null) ColorRGBA(0.82f, 0.82f, 0.78f, 1f) else ColorRGBA.White)
            setColor("Ambient", if (texture == null) ColorRGBA(0.42f, 0.42f, 0.40f, 1f) else ColorRGBA(0.72f, 0.72f, 0.72f, 1f))
            setColor("Specular", ColorRGBA(0.12f, 0.12f, 0.12f, 1f))
            setFloat("Shininess", 12f)
            if (texture != null) {
                setTexture("DiffuseMap", texture)
                additionalRenderState.blendMode = RenderState.BlendMode.Alpha
            }
            additionalRenderState.faceCullMode = RenderState.FaceCullMode.Off
        }

    private fun loadPngTexture(zip: ZipFile, entryName: String): Texture2D? {
        val entry = zip.getEntry(entryName) ?: return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val awt = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        return Texture2D(awtToJmeImage(awt)).apply {
            setWrap(Texture.WrapMode.Repeat)
            magFilter = Texture.MagFilter.Bilinear
            minFilter = Texture.MinFilter.Trilinear
            anisotropicFilter = 4
        }
    }

    private fun awtToJmeImage(awt: BufferedImage): Image {
        val width = awt.width
        val height = awt.height
        val buffer = BufferUtils.createByteBuffer(width * height * 4)
        for (row in height - 1 downTo 0) {
            for (x in 0 until width) {
                val p = awt.getRGB(x, row)
                buffer.put((p ushr 16 and 0xFF).toByte())
                buffer.put((p ushr 8 and 0xFF).toByte())
                buffer.put((p and 0xFF).toByte())
                buffer.put((p ushr 24 and 0xFF).toByte())
            }
        }
        buffer.flip()
        return Image(Image.Format.RGBA8, width, height, buffer, ColorSpace.sRGB)
    }

    private fun resolveTextureEntry(objectEntry: String, textureName: String): String {
        val normalized = textureName.replace('\\', '/')
        return if (normalized.contains('/')) {
            "${variantRoot(objectEntry)}/$normalized"
        } else {
            "${objectEntry.substringBeforeLast('/', "")}/$normalized"
        }
    }

    private fun variantRoot(entryName: String): String = entryName.substringBefore('/')
}
