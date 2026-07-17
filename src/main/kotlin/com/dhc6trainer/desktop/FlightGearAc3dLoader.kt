package com.dhc6trainer.desktop

import com.jme3.asset.AssetManager
import com.jme3.material.Material
import com.jme3.material.RenderState
import com.jme3.math.ColorRGBA
import com.jme3.math.Vector2f
import com.jme3.math.Vector3f
import com.jme3.renderer.queue.RenderQueue
import com.jme3.scene.Geometry
import com.jme3.scene.Mesh
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.VertexBuffer
import com.jme3.texture.Texture
import com.jme3.texture.Texture2D
import com.jme3.texture.image.ColorSpace
import com.jme3.util.BufferUtils
import java.awt.image.BufferedImage
import java.util.zip.ZipFile
import javax.imageio.ImageIO

internal object FlightGearAc3dLoader {
    fun loadAircraft(assetManager: AssetManager, aircraftPackage: FlightGearAircraftPackage): Spatial? =
        runCatching {
            ZipFile(aircraftPackage.zipPath.toFile()).use { zip ->
                val entriesByLower = zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .associateBy { it.name.replace('\\', '/').lowercase() }
                val textureCache = HashMap<String, Texture2D?>()
                val root = Node(aircraftPackage.label)
                aircraftPackage.visualEntries.forEach { entryName ->
                    loadAcInto(root, assetManager, zip, entriesByLower, textureCache, entryName)
                }
                // Instruments and breaker panels are separate models placed by
                // the flight deck's model XML; load that tree statically so the
                // panel is populated with real 3D gauges.
                runCatching {
                    val deckXml = aircraftPackage.visualEntries
                        .firstOrNull { it.endsWith("flightdeck.ac", ignoreCase = true) }
                        ?.replace(Regex("\\.ac$", RegexOption.IGNORE_CASE), ".xml")
                    if (deckXml != null && entriesByLower.containsKey(deckXml.lowercase())) {
                        val zipRootPrefix = deckXml.substringBefore("Models/")
                        val budget = intArrayOf(160)
                        loadModelXmlTree(
                            parent = root,
                            assetManager = assetManager,
                            zip = zip,
                            entriesByLower = entriesByLower,
                            textureCache = textureCache,
                            xmlEntry = deckXml,
                            zipRootPrefix = zipRootPrefix,
                            depth = 0,
                            budget = budget,
                            loadOwnGeometry = false,
                        )
                    }
                }
                root.updateModelBound()
                root.updateGeometricState()
                root.takeIf { it.children.isNotEmpty() }
            }
        }.getOrNull()

    private fun loadAcInto(
        parent: Node,
        assetManager: AssetManager,
        zip: ZipFile,
        entriesByLower: Map<String, java.util.zip.ZipEntry>,
        textureCache: HashMap<String, Texture2D?>,
        entryName: String,
    ): Node? {
        val text = zip.readEntryText(entryName) ?: return null
        val acFile = Ac3dParser(text).parse()
        val node = Ac3dSceneBuilder(
            assetManager = assetManager,
            zip = zip,
            entriesByLower = entriesByLower,
            textureCache = textureCache,
            sourceEntry = entryName,
            materials = acFile.materials,
        ).build(acFile.root)
        if (node.children.isEmpty()) return null
        node.name = entryName.substringAfterLast('/')
        parent.attachChild(node)
        return node
    }

    /**
     * Statically instantiates a FlightGear model XML: loads its own `<path>`
     * geometry and recursively places `<model>` children at their `<offsets>`.
     * FG offsets are x-m aft, y-m right, z-m up; in jME space that becomes
     * (y-m, z-m, x-m). Models guarded by a `<condition>` are variant/optional
     * extras and are skipped. Animations are ignored (static pose).
     */
    private fun loadModelXmlTree(
        parent: Node,
        assetManager: AssetManager,
        zip: ZipFile,
        entriesByLower: Map<String, java.util.zip.ZipEntry>,
        textureCache: HashMap<String, Texture2D?>,
        xmlEntry: String,
        zipRootPrefix: String,
        depth: Int,
        budget: IntArray,
        loadOwnGeometry: Boolean = true,
    ) {
        if (depth > 3 || budget[0] <= 0) return
        val text = zip.readEntryText(xmlEntry) ?: return
        val doc = runCatching { parseModelXml(text) }.getOrNull() ?: return
        val propertyList = doc.documentElement ?: return
        val xmlDir = xmlEntry.substringBeforeLast('/', "")

        if (loadOwnGeometry) {
            firstChildText(propertyList, "path")?.trim()?.let { ownPath ->
                resolveModelPath(ownPath, xmlDir, zipRootPrefix, entriesByLower)?.let { resolved ->
                    if (resolved.endsWith(".ac", ignoreCase = true)) {
                        budget[0]--
                        runCatching { loadAcInto(parent, assetManager, zip, entriesByLower, textureCache, resolved) }
                    }
                }
            }
        }

        val children = propertyList.childNodes
        for (i in 0 until children.length) {
            val model = children.item(i) as? org.w3c.dom.Element ?: continue
            if (model.tagName != "model") continue
            if (firstChildElement(model, "condition") != null) continue
            val path = firstChildText(model, "path")?.trim() ?: continue
            val resolved = resolveModelPath(path, xmlDir, zipRootPrefix, entriesByLower) ?: continue
            if (budget[0] <= 0) return

            val holder = Node(firstChildText(model, "name") ?: resolved.substringAfterLast('/'))
            firstChildElement(model, "offsets")?.let { offsets ->
                fun value(tag: String) = firstChildText(offsets, tag)?.trim()?.toFloatOrNull() ?: 0f
                holder.setLocalTranslation(value("y-m"), value("z-m"), value("x-m"))
                val pitch = value("pitch-deg") * com.jme3.math.FastMath.DEG_TO_RAD
                val heading = value("heading-deg") * com.jme3.math.FastMath.DEG_TO_RAD
                val roll = value("roll-deg") * com.jme3.math.FastMath.DEG_TO_RAD
                if (pitch != 0f || heading != 0f || roll != 0f) {
                    val rotation = com.jme3.math.Quaternion().fromAngles(0f, -heading, 0f)
                    rotation.multLocal(com.jme3.math.Quaternion().fromAngles(-pitch, 0f, 0f))
                    rotation.multLocal(com.jme3.math.Quaternion().fromAngles(0f, 0f, -roll))
                    holder.localRotation = rotation
                }
            }

            budget[0]--
            val loaded = runCatching {
                if (resolved.endsWith(".xml", ignoreCase = true)) {
                    loadModelXmlTree(
                        parent = holder,
                        assetManager = assetManager,
                        zip = zip,
                        entriesByLower = entriesByLower,
                        textureCache = textureCache,
                        xmlEntry = resolved,
                        zipRootPrefix = zipRootPrefix,
                        depth = depth + 1,
                        budget = budget,
                    )
                    holder.children.isNotEmpty()
                } else {
                    loadAcInto(holder, assetManager, zip, entriesByLower, textureCache, resolved) != null
                }
            }.getOrDefault(false)
            if (loaded) parent.attachChild(holder)
        }
    }

    private fun resolveModelPath(
        path: String,
        xmlDir: String,
        zipRootPrefix: String,
        entriesByLower: Map<String, java.util.zip.ZipEntry>,
    ): String? {
        val normalized = path.replace('\\', '/').trimStart('/')
        val candidates = buildList {
            // FG data-root absolute path: Aircraft/dhc6/Models/... -> zip root.
            val afterAircraft = Regex("^Aircraft/[^/]+/(.*)$").find(normalized)?.groupValues?.get(1)
            if (afterAircraft != null) add(zipRootPrefix + afterAircraft)
            if (xmlDir.isNotBlank()) add("$xmlDir/$normalized")
            add(zipRootPrefix + normalized)
        }
        return candidates.firstOrNull { entriesByLower.containsKey(it.lowercase()) }
    }

    private fun parseModelXml(text: String): org.w3c.dom.Document {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder()
            .parse(org.xml.sax.InputSource(java.io.StringReader(text)))
    }

    private fun firstChildElement(parent: org.w3c.dom.Element, tag: String): org.w3c.dom.Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? org.w3c.dom.Element ?: continue
            if (child.tagName == tag) return child
        }
        return null
    }

    private fun firstChildText(parent: org.w3c.dom.Element, tag: String): String? =
        firstChildElement(parent, tag)?.textContent

    private fun ZipFile.readEntryText(entryName: String): String? {
        val entry = getEntry(entryName) ?: return null
        return getInputStream(entry).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }
}

private class Ac3dParser(text: String) {
    private val lines = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    private var index = 0
    private val materials = mutableListOf<AcMaterialDef>()

    fun parse(): AcFile {
        while (index < lines.size) {
            val tokens = tokenize(lines[index])
            when {
                tokens.isEmpty() -> index++
                tokens[0].equals("MATERIAL", ignoreCase = true) -> {
                    materials += parseMaterial(tokens)
                    index++
                }
                tokens[0].equals("OBJECT", ignoreCase = true) -> {
                    val root = readObject(tokens.getOrElse(1) { "world" })
                    return AcFile(materials.ifEmpty { listOf(AcMaterialDef()) }, root)
                }
                else -> index++
            }
        }
        return AcFile(materials.ifEmpty { listOf(AcMaterialDef()) }, AcObject("world"))
    }

    private fun readObject(type: String): AcObject {
        val obj = AcObject(type = type)
        index++
        while (index < lines.size) {
            val tokens = tokenize(lines[index])
            if (tokens.isEmpty()) {
                index++
                continue
            }
            when (tokens[0].lowercase()) {
                "name" -> {
                    obj.name = tokens.getOrNull(1).orEmpty()
                    index++
                }
                "data" -> {
                    index += 2
                }
                "loc" -> {
                    obj.location = Vector3f(tokens.floatAt(1), tokens.floatAt(2), tokens.floatAt(3))
                    index++
                }
                "rot" -> {
                    obj.rotation = FloatArray(9) { i -> tokens.floatAt(i + 1, if (i % 4 == 0) 1f else 0f) }
                    index++
                }
                "texture" -> {
                    obj.textureName = tokens.getOrNull(1).orEmpty()
                    index++
                }
                "texrep" -> {
                    obj.texRep = Vector2f(tokens.floatAt(1, 1f), tokens.floatAt(2, 1f))
                    index++
                }
                "texoff" -> {
                    obj.texOff = Vector2f(tokens.floatAt(1), tokens.floatAt(2))
                    index++
                }
                "crease", "url", "subdiv" -> index++
                "numvert" -> readVertices(obj, tokens.intAt(1))
                "numsurf" -> readSurfaces(obj, tokens.intAt(1))
                "kids" -> {
                    val count = tokens.intAt(1)
                    index++
                    repeat(count) {
                        if (index < lines.size) {
                            val childTokens = tokenize(lines[index])
                            if (childTokens.firstOrNull()?.equals("OBJECT", ignoreCase = true) == true) {
                                obj.children += readObject(childTokens.getOrElse(1) { "poly" })
                            }
                        }
                    }
                    return obj
                }
                "object" -> return obj
                else -> index++
            }
        }
        return obj
    }

    private fun readVertices(obj: AcObject, count: Int) {
        index++
        repeat(count) {
            val tokens = tokenize(lines.getOrElse(index) { "" })
            obj.vertices += Vector3f(tokens.floatAt(0), tokens.floatAt(1), tokens.floatAt(2))
            index++
        }
    }

    private fun readSurfaces(obj: AcObject, count: Int) {
        index++
        repeat(count) {
            val surfTokens = tokenize(lines.getOrElse(index) { "" })
            index++
            if (surfTokens.firstOrNull()?.equals("SURF", ignoreCase = true) != true) return@repeat
            val matTokens = tokenize(lines.getOrElse(index) { "" })
            val matIndex = if (matTokens.firstOrNull()?.equals("mat", ignoreCase = true) == true) matTokens.intAt(1) else 0
            index++
            val refsTokens = tokenize(lines.getOrElse(index) { "" })
            val refCount = if (refsTokens.firstOrNull()?.equals("refs", ignoreCase = true) == true) refsTokens.intAt(1) else 0
            index++
            val refs = ArrayList<AcRef>(refCount)
            repeat(refCount) {
                val tokens = tokenize(lines.getOrElse(index) { "" })
                refs += AcRef(tokens.intAt(0), tokens.floatAt(1), tokens.floatAt(2))
                index++
            }
            if (refs.size >= 3) obj.surfaces += AcSurface(matIndex, refs)
        }
    }

    private fun parseMaterial(tokens: List<String>): AcMaterialDef {
        fun triple(key: String, fallback: ColorRGBA): ColorRGBA {
            val i = tokens.indexOfFirst { it.equals(key, ignoreCase = true) }
            return if (i >= 0 && i + 3 < tokens.size) {
                ColorRGBA(tokens.floatAt(i + 1), tokens.floatAt(i + 2), tokens.floatAt(i + 3), 1f)
            } else {
                fallback
            }
        }
        val transIndex = tokens.indexOfFirst { it.equals("trans", ignoreCase = true) }
        val shiIndex = tokens.indexOfFirst { it.equals("shi", ignoreCase = true) }
        return AcMaterialDef(
            name = tokens.getOrElse(1) { "Default" },
            diffuse = triple("rgb", ColorRGBA.White),
            ambient = triple("amb", ColorRGBA(0.35f, 0.35f, 0.35f, 1f)),
            emissive = triple("emis", ColorRGBA.Black),
            specular = triple("spec", ColorRGBA(0.08f, 0.08f, 0.08f, 1f)),
            shininess = if (shiIndex >= 0) tokens.floatAt(shiIndex + 1, 16f) else 16f,
            transparency = if (transIndex >= 0) tokens.floatAt(transIndex + 1) else 0f,
        )
    }
}

private class Ac3dSceneBuilder(
    private val assetManager: AssetManager,
    private val zip: ZipFile,
    private val entriesByLower: Map<String, java.util.zip.ZipEntry>,
    private val textureCache: MutableMap<String, Texture2D?>,
    private val sourceEntry: String,
    private val materials: List<AcMaterialDef>,
) {
    fun build(root: AcObject): Node {
        val node = Node("AC3D ${sourceEntry.substringAfterLast('/')}")
        appendObject(node, root, AcTransform.Identity)
        return node
    }

    private fun appendObject(parent: Node, obj: AcObject, parentTransform: AcTransform) {
        val transform = parentTransform.child(obj.location, obj.rotation)
        if (obj.type.equals("poly", ignoreCase = true) && obj.vertices.isNotEmpty() && obj.surfaces.isNotEmpty()) {
            buildGeometry(obj, transform)?.let(parent::attachChild)
        }
        obj.children.forEach { child -> appendObject(parent, child, transform) }
    }

    private fun buildGeometry(obj: AcObject, transform: AcTransform): Node? {
        val batches = linkedMapOf<AcBatchKey, AcMeshBatch>()
        obj.surfaces.forEach { surface ->
            val key = AcBatchKey(surface.materialIndex.coerceIn(0, materials.lastIndex), obj.textureName)
            val batch = batches.getOrPut(key) { AcMeshBatch() }
            for (i in 1 until surface.refs.lastIndex) {
                val refs = listOf(surface.refs[0], surface.refs[i], surface.refs[i + 1])
                val points = refs.mapNotNull { ref -> obj.vertices.getOrNull(ref.vertexIndex)?.let(transform::apply) }
                if (points.size != 3) continue
                val p0 = acToJme(points[0])
                val p1 = acToJme(points[1])
                val p2 = acToJme(points[2])
                val normal = p1.subtract(p0).cross(p2.subtract(p0))
                if (normal.lengthSquared() < 1e-8f) continue
                normal.normalizeLocal()
                listOf(p0, p1, p2).forEachIndexed { pointIndex, point ->
                    val ref = refs[pointIndex]
                    batch.positions += point
                    batch.normals += normal
                    batch.texCoords += Vector2f(
                        ref.u * obj.texRep.x + obj.texOff.x,
                        1f - (ref.v * obj.texRep.y + obj.texOff.y),
                    )
                    batch.indices += batch.positions.lastIndex
                }
            }
        }
        if (batches.isEmpty()) return null
        val node = Node(obj.name.ifBlank { "AC3D object" })
        batches.forEach { (key, batch) ->
            if (batch.positions.isEmpty()) return@forEach
            val materialDef = materials[key.materialIndex]
            node.attachChild(
                Geometry("${obj.name.ifBlank { "mesh" }}-${key.materialIndex}", batch.toMesh()).apply {
                    material = buildMaterial(materialDef, key.textureName)
                    shadowMode = RenderQueue.ShadowMode.CastAndReceive
                },
            )
        }
        return node.takeIf { it.children.isNotEmpty() }
    }

    private fun buildMaterial(materialDef: AcMaterialDef, textureName: String): Material =
        Material(assetManager, "Common/MatDefs/Light/Lighting.j3md").apply {
            setBoolean("UseMaterialColors", true)
            val alpha = (1f - materialDef.transparency).coerceIn(0.08f, 1f)
            setColor("Diffuse", ColorRGBA(materialDef.diffuse.r, materialDef.diffuse.g, materialDef.diffuse.b, alpha))
            setColor("Ambient", materialDef.ambient)
            setColor("Specular", materialDef.specular)
            setFloat("Shininess", materialDef.shininess.coerceIn(1f, 128f))
            val texture = loadTexture(textureName)
            if (texture != null) {
                setTexture("DiffuseMap", texture)
            }
            additionalRenderState.faceCullMode = RenderState.FaceCullMode.Off
            if (alpha < 0.98f) {
                additionalRenderState.blendMode = RenderState.BlendMode.Alpha
                additionalRenderState.isDepthWrite = false
            }
        }

    private fun loadTexture(textureName: String): Texture2D? {
        if (textureName.isBlank()) return null
        val normalized = textureName.replace('\\', '/')
        return textureCache.getOrPut("$sourceEntry::$normalized") {
            val baseDir = sourceEntry.substringBeforeLast('/', "")
            val direct = listOf(
                "$baseDir/$normalized",
                normalized,
                "dhc6-master/Models/$normalized",
                "dhc6-master/Models/Flightdeck/$normalized",
            ).map { it.trimStart('/') }
            val entry = direct.firstNotNullOfOrNull { entriesByLower[it.lowercase()] }
                ?: entriesByLower.entries.firstOrNull { it.key.endsWith("/${normalized.lowercase()}") }?.value
                ?: return@getOrPut null
            val awt = zip.getInputStream(entry).use(ImageIO::read) ?: return@getOrPut null
            Texture2D(awtToJmeImage(awt)).apply {
                setWrap(Texture.WrapMode.Repeat)
                minFilter = Texture.MinFilter.Trilinear
                anisotropicFilter = 4
            }
        }
    }

    private fun awtToJmeImage(awt: BufferedImage): com.jme3.texture.Image {
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
        return com.jme3.texture.Image(com.jme3.texture.Image.Format.RGBA8, width, height, buffer, ColorSpace.sRGB)
    }
}

private data class AcFile(
    val materials: List<AcMaterialDef>,
    val root: AcObject,
)

private data class AcMaterialDef(
    val name: String = "Default",
    val diffuse: ColorRGBA = ColorRGBA.White,
    val ambient: ColorRGBA = ColorRGBA(0.35f, 0.35f, 0.35f, 1f),
    val emissive: ColorRGBA = ColorRGBA.Black,
    val specular: ColorRGBA = ColorRGBA(0.08f, 0.08f, 0.08f, 1f),
    val shininess: Float = 16f,
    val transparency: Float = 0f,
)

private class AcObject(
    val type: String,
    var name: String = "",
    var location: Vector3f = Vector3f.ZERO,
    var rotation: FloatArray = AcTransform.IdentityRotation.copyOf(),
    var textureName: String = "",
    var texRep: Vector2f = Vector2f(1f, 1f),
    var texOff: Vector2f = Vector2f.ZERO,
    val vertices: MutableList<Vector3f> = mutableListOf(),
    val surfaces: MutableList<AcSurface> = mutableListOf(),
    val children: MutableList<AcObject> = mutableListOf(),
)

private data class AcSurface(
    val materialIndex: Int,
    val refs: List<AcRef>,
)

private data class AcRef(
    val vertexIndex: Int,
    val u: Float,
    val v: Float,
)

private data class AcBatchKey(
    val materialIndex: Int,
    val textureName: String,
)

private class AcMeshBatch {
    val positions = mutableListOf<Vector3f>()
    val normals = mutableListOf<Vector3f>()
    val texCoords = mutableListOf<Vector2f>()
    val indices = mutableListOf<Int>()

    fun toMesh(): Mesh {
        val positionBuffer = BufferUtils.createFloatBuffer(positions.size * 3)
        val normalBuffer = BufferUtils.createFloatBuffer(normals.size * 3)
        val texCoordBuffer = BufferUtils.createFloatBuffer(texCoords.size * 2)
        val indexBuffer = BufferUtils.createIntBuffer(indices.size)
        positions.forEach { positionBuffer.put(it.x).put(it.y).put(it.z) }
        normals.forEach { normalBuffer.put(it.x).put(it.y).put(it.z) }
        texCoords.forEach { texCoordBuffer.put(it.x).put(it.y) }
        indices.forEach(indexBuffer::put)
        positionBuffer.flip()
        normalBuffer.flip()
        texCoordBuffer.flip()
        indexBuffer.flip()
        return Mesh().apply {
            setBuffer(VertexBuffer.Type.Position, 3, positionBuffer)
            setBuffer(VertexBuffer.Type.Normal, 3, normalBuffer)
            setBuffer(VertexBuffer.Type.TexCoord, 2, texCoordBuffer)
            setBuffer(VertexBuffer.Type.Index, 3, indexBuffer)
            updateBound()
            setStatic()
        }
    }
}

private data class AcTransform(
    val location: Vector3f,
    val rotation: FloatArray,
) {
    fun child(localLocation: Vector3f, localRotation: FloatArray): AcTransform =
        AcTransform(
            location = apply(localLocation),
            rotation = multiply(rotation, localRotation),
        )

    fun apply(point: Vector3f): Vector3f {
        val rotated = multiply(rotation, point)
        return rotated.addLocal(location)
    }

    companion object {
        val IdentityRotation = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        val Identity = AcTransform(Vector3f.ZERO, IdentityRotation)
    }
}

private fun multiply(matrix: FloatArray, point: Vector3f): Vector3f =
    Vector3f(
        matrix[0] * point.x + matrix[1] * point.y + matrix[2] * point.z,
        matrix[3] * point.x + matrix[4] * point.y + matrix[5] * point.z,
        matrix[6] * point.x + matrix[7] * point.y + matrix[8] * point.z,
    )

private fun multiply(a: FloatArray, b: FloatArray): FloatArray =
    floatArrayOf(
        a[0] * b[0] + a[1] * b[3] + a[2] * b[6],
        a[0] * b[1] + a[1] * b[4] + a[2] * b[7],
        a[0] * b[2] + a[1] * b[5] + a[2] * b[8],
        a[3] * b[0] + a[4] * b[3] + a[5] * b[6],
        a[3] * b[1] + a[4] * b[4] + a[5] * b[7],
        a[3] * b[2] + a[4] * b[5] + a[5] * b[8],
        a[6] * b[0] + a[7] * b[3] + a[8] * b[6],
        a[6] * b[1] + a[7] * b[4] + a[8] * b[7],
        a[6] * b[2] + a[7] * b[5] + a[8] * b[8],
    )

/**
 * FlightGear's DHC-6 .ac files are authored AC3D-native: +X aft (nose at -X),
 * +Y up, +Z lateral. jME wants X lateral, Y up, nose toward -Z. The mapping
 * (x,y,z) -> (-z, y, x) is a proper rotation (no mirroring), so winding and
 * computed normals stay consistent.
 */
private fun acToJme(point: Vector3f): Vector3f =
    Vector3f(-point.z, point.y, point.x)

private fun tokenize(line: String): List<String> =
    Regex(""""([^"]*)"|(\S+)""")
        .findAll(line)
        .map { it.groups[1]?.value ?: it.groups[2]?.value.orEmpty() }
        .toList()

private fun List<String>.floatAt(index: Int, fallback: Float = 0f): Float =
    getOrNull(index)?.toFloatOrNull() ?: fallback

private fun List<String>.intAt(index: Int, fallback: Int = 0): Int =
    getOrNull(index)?.toIntOrNull() ?: fallback
