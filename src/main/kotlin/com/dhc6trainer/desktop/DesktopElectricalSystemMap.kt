package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Electrical Technical Lab metadata.
 *
 * This is deliberately schematic/CAS-first. There is no real electrical GLB in the
 * packaged model set yet, so the UI exposes the best 2D electrical references and
 * the G950/Legacy CAS message libraries without pretending a 3D model exists.
 */
internal data class DesktopElectricalCasSource(
    val variant: String,
    val expectedPath: String,
    val auditedMessageCount: Int,
)

internal data class DesktopElectricalReferenceProfile(
    val title: String,
    val status: String,
    val preferredDiagramNames: List<String>,
    val casSources: List<DesktopElectricalCasSource>,
    val futureModelNote: String,
)

private val electricalReferenceProfile = DesktopElectricalReferenceProfile(
    title = "Electrical system references",
    status = "Electrical model pending - using 2D schematic and CAS libraries",
    preferredDiagramNames = listOf(
        "electrical_g950.png",
        "electrical_g950.jpg",
        "electrical_g950.svg",
        "electrical_g950.json",
        "electrical_legacy.png",
        "electrical_legacy.jpg",
        "electrical_legacy.svg",
        "electrical_legacy.json",
        "electrical.png",
        "electrical_system.png",
        "electrical_schematic.png",
    ),
    casSources = listOf(
        DesktopElectricalCasSource(
            variant = "G950",
            expectedPath = "assets/cas-library/electrical_g950.json",
            auditedMessageCount = 56,
        ),
        DesktopElectricalCasSource(
            variant = "Legacy",
            expectedPath = "assets/cas-library/electrical_legacy.json",
            auditedMessageCount = 57,
        ),
    ),
    futureModelNote = "Add a real electrical bus/battery/generator GLB later; until then, prefer electrical_g950/electrical_legacy diagrams plus CAS message references.",
)

internal fun SystemAssetGroup.isElectricalSystemGroup(): Boolean {
    val haystack = buildString {
        append(family.lowercase())
        append(' ')
        append(name.lowercase())
        append(' ')
        append(description.lowercase())
        append(' ')
        append(matchedAssets.joinToString(" ").lowercase())
    }
    return listOf("electrical", "generator", "battery", "bus", "cas-library/electrical").any { haystack.contains(it) }
}

internal fun SystemAssetGroup.preferredElectricalDiagramPath(): String? = electricalDiagramCandidates().firstOrNull()

internal fun SystemAssetGroup.electricalDiagramCandidates(): List<String> {
    if (!isElectricalSystemGroup()) return emptyList()

    val orderedPreferred = electricalReferenceProfile.preferredDiagramNames.mapNotNull { preferredName ->
        matchedAssets.firstOrNull { it.endsWith(preferredName, ignoreCase = true) }
    }

    val broadMatches = matchedAssets.filter { path ->
        path.contains("electrical", ignoreCase = true) &&
            !path.contains("cas-library", ignoreCase = true) &&
            (path.endsWith(".png", ignoreCase = true) ||
                path.endsWith(".jpg", ignoreCase = true) ||
                path.endsWith(".jpeg", ignoreCase = true) ||
                path.endsWith(".svg", ignoreCase = true) ||
                path.endsWith(".json", ignoreCase = true))
    }

    return (orderedPreferred + broadMatches).distinct().take(6)
}

internal fun SystemAssetGroup.electricalCasSources(): List<DesktopElectricalCasSource> =
    if (isElectricalSystemGroup()) electricalReferenceProfile.casSources else emptyList()

@Composable
internal fun ElectricalSystemReferenceCard(group: SystemAssetGroup) {
    if (!group.isElectricalSystemGroup()) return

    val profile = remember { electricalReferenceProfile }
    val preferredDiagram = remember(group.matchedAssets, group.family, group.name) { group.preferredElectricalDiagramPath() }
    val diagrams = remember(group.matchedAssets, group.family, group.name) { group.electricalDiagramCandidates() }
    val casSources = remember(group.matchedAssets, group.family, group.name) { group.electricalCasSources() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderBright),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("electrical", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("schematic + CAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Text(profile.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(profile.status, color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            Text(
                "Preferred diagram: ${preferredDiagram?.substringAfterLast('/') ?: "electrical_g950 / electrical_legacy pending"}",
                color = Dhc6DesktopColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (diagrams.isNotEmpty()) {
                Text(
                    "Diagram candidates: ${diagrams.joinToString { it.substringAfterLast('/') }}",
                    color = Dhc6DesktopColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            casSources.forEach { source ->
                Text(
                    "CAS ${source.variant}: ${source.expectedPath.substringAfterLast('/')} (${source.auditedMessageCount} audited messages)",
                    color = Dhc6DesktopColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(profile.futureModelNote, color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
