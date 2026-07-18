package com.dhc6trainer.desktop

import com.jme3.asset.AssetManager
import com.jme3.bounding.BoundingBox
import com.jme3.material.Material
import com.jme3.math.ColorRGBA
import com.jme3.scene.Geometry
import com.jme3.scene.Spatial

/**
 * Gives the untextured DHC-6 GLB a clean aircraft livery. The exported model
 * ships with flat 0.8-grey PBR materials and no textures, and its parts are
 * generically named (Cube.NNN) so per-panel painting is not possible. Instead
 * apply a two-tone scheme by height — white upper surfaces, light-grey belly —
 * with a slight metallic sheen so the PBR light probe/sky reflects and it reads
 * as painted metal rather than clay.
 */
internal object Dhc6AircraftPaint {

    private fun whitePaint(assetManager: AssetManager) =
        Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md").apply {
            setColor("BaseColor", ColorRGBA(0.93f, 0.94f, 0.96f, 1f))
            setFloat("Metallic", 0.14f)
            setFloat("Roughness", 0.30f)
        }

    private fun bellyPaint(assetManager: AssetManager) =
        Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md").apply {
            setColor("BaseColor", ColorRGBA(0.50f, 0.53f, 0.58f, 1f))
            setFloat("Metallic", 0.20f)
            setFloat("Roughness", 0.42f)
        }

    fun applyLivery(assetManager: AssetManager, model: Spatial) {
        model.updateModelBound()
        model.updateGeometricState()
        val whole = model.worldBound as? BoundingBox
        val midY = whole?.center?.y ?: 0f
        val white = whitePaint(assetManager)
        val belly = bellyPaint(assetManager)
        runCatching {
            model.depthFirstTraversal { spatial ->
                val geom = spatial as? Geometry ?: return@depthFirstTraversal
                val gb = geom.worldBound as? BoundingBox
                val upper = (gb?.center?.y ?: midY) >= midY
                geom.material = if (upper) white else belly
            }
        }
    }
}
