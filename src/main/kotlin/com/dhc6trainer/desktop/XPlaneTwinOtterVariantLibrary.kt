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
import java.util.zip.ZipEntry
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
    const val preferredVariantId = "dhc63l-lp"

    private data class KnownPackage(
        val id: String,
        val label: String,
        val fileNames: List<String>,
        val preferredObjects: List<String>,
    )

    private val knownPackages = listOf(
        KnownPackage(
            id = preferredVariantId,
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
            home?.let {
                add(
                    Paths.get(
                        it,
                        "OneDrive",
                        "Desktop",
                        "My App Data",
                        fileName,
                    ),
                )
            }
            home?.let {
                add(
                    Paths.get(
                        it,
                        "OneDrive",
                        "Desktop",
                        "My App Data",
                        "ZIP Files",
                        "Files",
                        "Desktop",
                        fileName,
                    ),
                )
            }
        }.distinctBy {
            it.toAbsolutePath()
                .normalize()
                .toString()
                .lowercase()
        }
    }

    private fun loadPackage(
        known: KnownPackage,
        zipPath: Path,
    ): XPlaneTwinOtterVariantPackage? =
        runCatching {
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries()
                    .asSequence()
                    .filterNot { it.isDirectory }
                    .map { it.name }
                    .filterNot { it.startsWith("__MACOSX/") }
                    .toList()

                val root =
                    entries.firstOrNull()
                        ?.substringBefore('/')
                        ?: return null

                val entryByLowerName =
                    entries.associateBy { it.lowercase() }

                val objects =
                    entries.filter {
                        it.endsWith(".obj", ignoreCase = true)
                    }

                val textures =
                    entries.count { name ->
                        name.endsWith(".png", ignoreCase = true) ||
                                name.endsWith(".jpg", ignoreCase = true) ||
                                name.endsWith(".jpeg", ignoreCase = true)
                    }

                val exterior =
                    known.preferredObjects.mapNotNull {
                        entryByLowerName["$root/$it".lowercase()]
                    }

                val cockpitEntry =
                    entryByLowerName[
                        "$root/Twin Otter_cockpit.obj".lowercase()
                    ]

                XPlaneTwinOtterVariantPackage(
                    id = known.id,
                    label = known.label,
                    zipPath = zipPath,
                    rootEntry = root,
                    exteriorObjectEntries = exterior,
                    cockpitObjectEntry = cockpitEntry,
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
        val regularIndices: List<Int>,
        val cockpitIndices: List<Int>,
    )

    fun loadAircraft(
        assetManager: AssetManager,
        variantPackage: XPlaneTwinOtterVariantPackage,
    ): XPlaneLoadedAircraft? =
        runCatching {
            ZipFile(variantPackage.zipPath.toFile()).use { zip ->
                val root = Node("X-Plane ${variantPackage.label}")

                val entryIndex =
                    zip.entries()
                        .asSequence()
                        .filterNot { it.isDirectory }
                        .associateBy {
                            normalizeZipPath(it.name).lowercase()
                        }

                val textureCache =
                    HashMap<String, Texture2D?>()

                val exteriorNodes =
                    ArrayList<Spatial>()

                val interiorNodes =
                    ArrayList<Spatial>()

                variantPackage.exteriorObjectEntries.forEach { entry ->
                    runCatching {
                        loadObject(
                            assetManager,
                            zip,
                            entryIndex,
                            entry,
                            textureCache,
                        )
                    }.getOrNull()
                        ?.let { spatial ->
                            root.attachChild(spatial)

                            if (
                                entry.endsWith(
                                    "/cabin.obj",
                                    ignoreCase = true,
                                ) ||
                                entry.endsWith(
                                    "/seats.obj",
                                    ignoreCase = true,
                                )
                            ) {
                                interiorNodes += spatial
                            } else {
                                exteriorNodes += spatial
                            }
                        }
                }

                val cockpit =
                    variantPackage.cockpitObjectEntry
                        ?.let { entry ->
                            runCatching {
                                loadObject(
                                    assetManager,
                                    zip,
                                    entryIndex,
                                    entry,
                                    textureCache,
                                )
                            }.getOrNull()
                        }
                        ?: return@use null

                root.attachChild(cockpit)

                XPlaneLoadedAircraft(
                    root,
                    exteriorNodes,
                    interiorNodes,
                    cockpit,
                )
            }
        }.getOrNull()

    private fun loadObject(
        assetManager: AssetManager,
        zip: ZipFile,
        entryIndex: Map<String, ZipEntry>,
        entryName: String,
        textureCache: MutableMap<String, Texture2D?>,
    ): Spatial? {
        val entry =
            entryIndex[normalizeZipPath(entryName).lowercase()]
                ?: return null

        val text =
            zip.getInputStream(entry)
                .bufferedReader(Charsets.ISO_8859_1)
                .use { it.readText() }

        val data = parseObj8(text)

        if (
            data.vertices.isEmpty() ||
            (
                    data.regularIndices.isEmpty() &&
                            data.cockpitIndices.isEmpty()
                    )
        ) {
            return null
        }

        val node =
            Node(entryName.substringAfterLast('/'))

        val textureEntry =
            data.textureName?.let {
                resolveTextureEntry(
                    entryName,
                    it,
                    entryIndex,
                )
            }

        val texture =
            textureEntry?.let { resolved ->
                textureCache.getOrPut(
                    resolved.lowercase(),
                ) {
                    loadPngTexture(
                        zip,
                        entryIndex,
                        resolved,
                    )
                }
            }

        if (data.regularIndices.isNotEmpty()) {
            node.attachChild(
                buildGeometry(
                    assetManager = assetManager,
                    name = "${node.name}-surface",
                    vertices = data.vertices,
                    indices = data.regularIndices,
                    texture = texture,
                    transparent = isTransparentObject(
                        entryName,
                        textureEntry,
                    ),
                ),
            )
        }

        if (data.cockpitIndices.isNotEmpty()) {
            val panelEntry =
                resolveCockpitPanelEntry(
                    entryName,
                    entryIndex,
                )

            val panelTexture =
                panelEntry?.let { resolved ->
                    textureCache.getOrPut(
                        resolved.lowercase(),
                    ) {
                        loadPngTexture(
                            zip,
                            entryIndex,
                            resolved,
                        )
                    }
                }

            node.attachChild(
                buildGeometry(
                    assetManager = assetManager,
                    name = "${node.name}-panel",
                    vertices = data.vertices,
                    indices = data.cockpitIndices,
                    texture = panelTexture ?: texture,
                    transparent = false,
                ),
            )
        }

        return node
    }

    private fun buildGeometry(
        assetManager: AssetManager,
        name: String,
        vertices: List<ObjVertex>,
        indices: List<Int>,
        texture: Texture2D?,
        transparent: Boolean,
    ): Geometry =
        Geometry(
            name,
            buildMesh(vertices, indices),
        ).apply {
            material =
                buildMaterial(
                    assetManager,
                    texture,
                    transparent,
                )

            if (transparent) {
                queueBucket =
                    RenderQueue.Bucket.Transparent
            }

            shadowMode =
                RenderQueue.ShadowMode.CastAndReceive
        }

    private fun parseObj8(text: String): ObjMeshData {
        var textureName: String? = null
        var cockpitRegion = false

        val vertices =
            ArrayList<ObjVertex>()

        val indexPool =
            ArrayList<Int>()

        val regularIndices =
            ArrayList<Int>()

        val cockpitIndices =
            ArrayList<Int>()

        text.lineSequence().forEach { raw ->
            val line = raw.trim()

            if (
                line.isEmpty() ||
                line.startsWith("#")
            ) {
                return@forEach
            }

            val parts =
                line.split(' ', '\t')
                    .filter { it.isNotBlank() }

            when (parts.firstOrNull()) {
                "TEXTURE" -> {
                    textureName = parts.getOrNull(1)
                }

                "VT" -> {
                    if (parts.size >= 9) {
                        vertices += ObjVertex(
                            x =
                                parts[1]
                                    .toFloatOrNull()
                                    ?: 0f,
                            y =
                                parts[2]
                                    .toFloatOrNull()
                                    ?: 0f,
                            z =
                                parts[3]
                                    .toFloatOrNull()
                                    ?: 0f,
                            nx =
                                parts[4]
                                    .toFloatOrNull()
                                    ?: 0f,
                            ny =
                                parts[5]
                                    .toFloatOrNull()
                                    ?: 1f,
                            nz =
                                parts[6]
                                    .toFloatOrNull()
                                    ?: 0f,
                            u =
                                parts[7]
                                    .toFloatOrNull()
                                    ?: 0f,
                            v =
                                parts[8]
                                    .toFloatOrNull()
                                    ?: 0f,
                        )
                    }
                }

                "IDX" -> {
                    parts.getOrNull(1)
                        ?.toIntOrNull()
                        ?.let(indexPool::add)
                }

                "IDX10" -> {
                    parts.drop(1).forEach { value ->
                        value.toIntOrNull()
                            ?.let(indexPool::add)
                    }
                }

                "ATTR_cockpit",
                "ATTR_cockpit_region",
                    -> {
                    cockpitRegion = true
                }

                "ATTR_no_cockpit" -> {
                    cockpitRegion = false
                }

                "TRIS" -> {
                    val start =
                        parts.getOrNull(1)
                            ?.toIntOrNull()
                            ?: return@forEach

                    val count =
                        parts.getOrNull(2)
                            ?.toIntOrNull()
                            ?: return@forEach

                    if (
                        start >= 0 &&
                        count > 0 &&
                        start + count <= indexPool.size
                    ) {
                        val target =
                            if (cockpitRegion) {
                                cockpitIndices
                            } else {
                                regularIndices
                            }

                        target +=
                            indexPool.subList(
                                start,
                                start + count,
                            )
                    }
                }
            }
        }

        return ObjMeshData(
            textureName,
            vertices,
            regularIndices,
            cockpitIndices,
        )
    }

    private fun buildMesh(
        vertices: List<ObjVertex>,
        indices: List<Int>,
    ): Mesh {
        val remap = HashMap<Int, Int>()
        val order = ArrayList<Int>()

        indices.forEach { index ->
            remap.getOrPut(index) {
                order += index
                order.size - 1
            }
        }

        val positions =
            BufferUtils.createFloatBuffer(
                order.size * 3,
            )

        val normals =
            BufferUtils.createFloatBuffer(
                order.size * 3,
            )

        val texCoords =
            BufferUtils.createFloatBuffer(
                order.size * 2,
            )

        order.forEach { index ->
            val vertex =
                vertices[
                    index.coerceIn(
                        0,
                        vertices.lastIndex,
                    )
                ]

            positions
                .put(vertex.x)
                .put(vertex.y)
                .put(vertex.z)

            normals
                .put(vertex.nx)
                .put(vertex.ny)
                .put(vertex.nz)

            texCoords
                .put(vertex.u)
                .put(1f - vertex.v)
        }

        val indexBuffer =
            BufferUtils.createIntBuffer(
                indices.size,
            )

        indices.forEach { index ->
            indexBuffer.put(
                remap.getValue(index),
            )
        }

        return Mesh().apply {
            setBuffer(
                VertexBuffer.Type.Position,
                3,
                positions,
            )
            setBuffer(
                VertexBuffer.Type.Normal,
                3,
                normals,
            )
            setBuffer(
                VertexBuffer.Type.TexCoord,
                2,
                texCoords,
            )
            setBuffer(
                VertexBuffer.Type.Index,
                3,
                indexBuffer,
            )
            updateBound()
            setStatic()
        }
    }

    private fun buildMaterial(
        assetManager: AssetManager,
        texture: Texture2D?,
        transparent: Boolean,
    ): Material =
        Material(
            assetManager,
            "Common/MatDefs/Light/Lighting.j3md",
        ).apply {
            setBoolean(
                "UseMaterialColors",
                true,
            )

            setColor(
                "Diffuse",
                if (texture == null) {
                    ColorRGBA(
                        0.82f,
                        0.82f,
                        0.78f,
                        1f,
                    )
                } else {
                    ColorRGBA.White
                },
            )

            setColor(
                "Ambient",
                if (texture == null) {
                    ColorRGBA(
                        0.42f,
                        0.42f,
                        0.40f,
                        1f,
                    )
                } else {
                    ColorRGBA(
                        0.72f,
                        0.72f,
                        0.72f,
                        1f,
                    )
                },
            )

            setColor(
                "Specular",
                ColorRGBA(
                    0.12f,
                    0.12f,
                    0.12f,
                    1f,
                ),
            )

            setFloat(
                "Shininess",
                12f,
            )

            if (texture != null) {
                setTexture(
                    "DiffuseMap",
                    texture,
                )
            }

            if (transparent) {
                additionalRenderState.blendMode =
                    RenderState.BlendMode.Alpha

                additionalRenderState.isDepthWrite =
                    false
            }

            additionalRenderState.faceCullMode =
                RenderState.FaceCullMode.Off
        }

    private fun loadPngTexture(
        zip: ZipFile,
        entryIndex: Map<String, ZipEntry>,
        entryName: String,
    ): Texture2D? {
        val entry =
            entryIndex[
                normalizeZipPath(entryName).lowercase()
            ] ?: return null

        val bytes =
            zip.getInputStream(entry)
                .use { it.readBytes() }

        val awt =
            ImageIO.read(
                ByteArrayInputStream(bytes),
            ) ?: return null

        return Texture2D(
            awtToJmeImage(awt),
        ).apply {
            setWrap(Texture.WrapMode.Repeat)

            magFilter =
                Texture.MagFilter.Bilinear

            minFilter =
                Texture.MinFilter.Trilinear

            anisotropicFilter = 4
        }
    }

    private fun awtToJmeImage(
        awt: BufferedImage,
    ): Image {
        val width = awt.width
        val height = awt.height

        val buffer =
            BufferUtils.createByteBuffer(
                width * height * 4,
            )

        for (row in height - 1 downTo 0) {
            for (x in 0 until width) {
                val pixel =
                    awt.getRGB(x, row)

                buffer.put(
                    (
                            pixel ushr 16 and 0xFF
                            ).toByte(),
                )

                buffer.put(
                    (
                            pixel ushr 8 and 0xFF
                            ).toByte(),
                )

                buffer.put(
                    (
                            pixel and 0xFF
                            ).toByte(),
                )

                buffer.put(
                    (
                            pixel ushr 24 and 0xFF
                            ).toByte(),
                )
            }
        }

        buffer.flip()

        return Image(
            Image.Format.RGBA8,
            width,
            height,
            buffer,
            ColorSpace.sRGB,
        )
    }

    private fun resolveTextureEntry(
        objectEntry: String,
        textureName: String,
        entryIndex: Map<String, ZipEntry>,
    ): String? {
        val objectDirectory =
            objectEntry.substringBeforeLast(
                '/',
                "",
            )

        val root =
            variantRoot(objectEntry)

        val normalizedTexture =
            textureName.replace(
                '\\',
                '/',
            )

        val candidates =
            listOf(
                "$objectDirectory/$normalizedTexture",
                "$root/$normalizedTexture",
            )

        return candidates.firstNotNullOfOrNull { candidate ->
            entryIndex[
                normalizeZipPath(candidate).lowercase()
            ]?.name
        }
    }

    private fun resolveCockpitPanelEntry(
        objectEntry: String,
        entryIndex: Map<String, ZipEntry>,
    ): String? {
        val root =
            variantRoot(objectEntry)

        return listOf(
            "$root/cockpit_3d/-PANELS-/Panel.png",
            "$root/cockpit/-panels-/panel.png",
        ).firstNotNullOfOrNull { candidate ->
            entryIndex[
                normalizeZipPath(candidate).lowercase()
            ]?.name
        }
    }

    private fun isTransparentObject(
        objectEntry: String,
        textureEntry: String?,
    ): Boolean =
        objectEntry.contains(
            "glass",
            ignoreCase = true,
        ) ||
                textureEntry?.contains(
                    "glass",
                    ignoreCase = true,
                ) == true

    private fun normalizeZipPath(
        path: String,
    ): String {
        val parts =
            ArrayList<String>()

        path.replace('\\', '/')
            .split('/')
            .forEach { part ->
                when (part) {
                    "",
                    ".",
                        -> Unit

                    ".." -> {
                        if (parts.isNotEmpty()) {
                            parts.removeAt(
                                parts.lastIndex,
                            )
                        }
                    }

                    else -> {
                        parts += part
                    }
                }
            }

        return parts.joinToString("/")
    }

    private fun variantRoot(
        entryName: String,
    ): String =
        entryName.substringBefore('/')
}
