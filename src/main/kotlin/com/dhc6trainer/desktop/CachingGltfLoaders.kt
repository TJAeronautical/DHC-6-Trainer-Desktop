package com.dhc6trainer.desktop

import com.jme3.asset.AssetInfo
import com.jme3.scene.plugins.gltf.GlbLoader
import com.jme3.scene.plugins.gltf.GltfLoader
import com.jme3.texture.Texture2D

/* =====================================================================
   Image-caching glTF/GLB loaders.

   jME's stock GltfLoader decodes the referenced image from scratch for
   EVERY material texture reference. Blender exports with hundreds of
   materials sharing one atlas (e.g. beta_backup.glb: 771 references to
   two 1024x1024 images) then allocate gigabytes of direct buffer memory
   for identical pixel data and OOM the JVM. These subclasses decode each
   image once per load and hand out shallow texture clones (clones share
   the pixel data but keep independent sampler/wrap settings).
   ===================================================================== */

class CachingGlbLoader : GlbLoader() {
    private val imageCache = HashMap<Long, Texture2D>()

    override fun load(assetInfo: AssetInfo): Any {
        imageCache.clear()
        try {
            return super.load(assetInfo)
        } finally {
            imageCache.clear()
        }
    }

    override fun readImage(sourceIndex: Int, flip: Boolean): Texture2D =
        readImageCached(imageCache, sourceIndex, flip) { super.readImage(sourceIndex, flip) }
}

class CachingGltfLoader : GltfLoader() {
    private val imageCache = HashMap<Long, Texture2D>()

    override fun load(assetInfo: AssetInfo): Any {
        imageCache.clear()
        try {
            return super.load(assetInfo)
        } finally {
            imageCache.clear()
        }
    }

    override fun readImage(sourceIndex: Int, flip: Boolean): Texture2D =
        readImageCached(imageCache, sourceIndex, flip) { super.readImage(sourceIndex, flip) }
}

private inline fun readImageCached(
    cache: HashMap<Long, Texture2D>,
    sourceIndex: Int,
    flip: Boolean,
    decode: () -> Texture2D,
): Texture2D {
    val key = (sourceIndex.toLong() shl 1) or (if (flip) 1L else 0L)
    val cached = cache[key]
    if (cached != null) {
        return cached.clone() as Texture2D
    }
    val texture = decode()
    cache[key] = texture
    // Hand out a clone so per-material sampler settings never mutate the
    // cached original.
    return texture.clone() as Texture2D
}
