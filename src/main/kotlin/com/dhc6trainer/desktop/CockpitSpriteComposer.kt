package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.InputStream

@Immutable
data class ComposedCockpitSprite(
    val path: String,
    val bitmap: ImageBitmap?,
    val label: String,
)

object DesktopCockpitSpriteComposer {

    fun loadSprites(variant: CockpitSpriteVariant, limit: Int = 18): List<ComposedCockpitSprite> {
        val manifest = readManifest()
        val lowerVariant = variant.name.lowercase()

        val preferred = manifest
            .filter { it.lowercase().contains("/$lowerVariant/") || it.lowercase().contains(lowerVariant) }
            .sortedWith(compareBy(::spriteRank, { it }))

        val fallback = manifest
            .filterNot { preferred.contains(it) }
            .sortedWith(compareBy(::spriteRank, { it }))

        return (preferred + fallback)
            .distinct()
            .take(limit)
            .map { path ->
                ComposedCockpitSprite(
                    path = path,
                    bitmap = runCatching {
                        val stream = openResource(path) ?: return@runCatching null
                        stream.use { it.readAllBytes().decodeToImageBitmap() }
                    }.getOrNull(),
                    label = path.substringAfterLast('/').substringBeforeLast('.').replace('_', ' '),
                )
            }
    }

    private fun spriteRank(path: String): Int {
        val lower = path.lowercase()
        return when {
            lower.contains("/instruments/") -> 0
            lower.contains("panel") -> 1
            lower.contains("annunciator") -> 2
            lower.contains("fire") -> 3
            lower.contains("fuel") -> 4
            lower.contains("avionics") -> 5
            lower.contains("default") -> 6
            else -> 20
        }
    }

    private fun readManifest(): List<String> {
        val stream = openResource(ManifestPath) ?: return emptyList()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { path ->
                    val lower = path.lowercase()
                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                }
                .toList()
        }
    }

    private fun openResource(path: String): InputStream? {
        val loader = Thread.currentThread().contextClassLoader
        return loader.getResourceAsStream(path)
            ?: DesktopCockpitSpriteComposer::class.java.classLoader.getResourceAsStream(path)
            ?: DesktopCockpitSpriteComposer::class.java.getResourceAsStream("/$path")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CockpitSpriteComposerStage(
    variant: CockpitSpriteVariant,
    selectedTarget: CockpitHitboxTarget,
    modifier: Modifier = Modifier,
) {
    val sprites = remember(variant) { DesktopCockpitSpriteComposer.loadSprites(variant) }
    val loadedSprites = remember(sprites) { sprites.filter { it.bitmap != null } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF061B2A))
            .border(BorderStroke(1.dp, Color(0xFF23607B)), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        if (loadedSprites.isEmpty()) {
            EmptyComposerState(variant, selectedTarget, sprites.size)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${variant.label} composer board",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    ComposerChip("${loadedSprites.size} sprites")
                    ComposerChip(selectedTarget.area)
                }
                Text(
                    text = "Component layout preview for source-exact sprite alignment. Selected target: ${selectedTarget.title}.",
                    color = Color(0xFFC7D7E6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF08283D))
                        .border(BorderStroke(1.dp, Color(0xFF23607B)), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        loadedSprites.take(16).forEachIndexed { index, sprite ->
                            ComposedSpriteTile(
                                sprite = sprite,
                                prominent = index < 4,
                            )
                        }
                    }

                    ComposerTargetOverlay(
                        target = selectedTarget,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposedSpriteTile(
    sprite: ComposedCockpitSprite,
    prominent: Boolean,
) {
    val tileWidth = if (prominent) 190.dp else 132.dp
    val tileHeight = if (prominent) 112.dp else 78.dp

    Column(
        modifier = Modifier
            .widthIn(min = tileWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF061F31))
            .border(BorderStroke(1.dp, Color(0xFF23607B)), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bitmap = sprite.bitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = sprite.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF020B12)),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF020B12)),
                contentAlignment = Alignment.Center,
            ) {
                Text("missing", color = Color(0xFFFFB25B), fontWeight = FontWeight.Black)
            }
        }
        Text(
            text = sprite.label.take(30),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}


@Composable
private fun ComposerChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF08283D))
            .border(BorderStroke(1.dp, Color(0xFF23607B)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFF55C7FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ComposerTargetOverlay(
    target: CockpitHitboxTarget,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC061B2A))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Selected hitbox",
                color = Color(0xFF55C7FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = target.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = target.area,
                color = Color(0xFFC7D7E6),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyComposerState(
    variant: CockpitSpriteVariant,
    selectedTarget: CockpitHitboxTarget,
    indexedCount: Int,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                variant.label.uppercase(),
                color = Color(0xFF55C7FF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No cockpit component PNG loaded",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Indexed manifest entries: $indexedCount. Selected target: ${selectedTarget.title}. Composer board is ready for source-exact PNGs.",
                color = Color(0xFFD8E5F2),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private const val ManifestPath = "cockpit-source-exact-index.txt"