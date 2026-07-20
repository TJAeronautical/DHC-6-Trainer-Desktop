package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class SystemsLabTab(val label: String) {
    OVERVIEW("Overview"), MODEL("Model / schematic"), OPERATION("Normal operation"), FAILURES("Failure modes")
}

@Composable
internal fun DedicatedSystemsLabScreen(assetSnapshot: DesktopAssetCatalogSnapshot) {
    var selected by remember { mutableStateOf(assetSnapshot.systemGroups.firstOrNull()) }
    var tab by remember { mutableStateOf(SystemsLabTab.OVERVIEW) }

    LaunchedEffect(assetSnapshot.systemGroups) {
        if (selected == null || selected !in assetSnapshot.systemGroups) {
            selected = assetSnapshot.systemGroups.firstOrNull()
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SystemsLibraryRail(
            groups = assetSnapshot.systemGroups,
            selected = selected,
            assetCount = assetSnapshot.systemsAssetCount,
            onSelect = { selected = it; tab = SystemsLabTab.OVERVIEW },
            modifier = Modifier.weight(0.82f),
        )
        SystemsLearningPane(
            group = selected,
            selectedTab = tab,
            onTabSelected = { tab = it },
            modifier = Modifier.weight(1.18f),
        )
    }
}

@Composable
private fun SystemsLibraryRail(
    groups: List<SystemAssetGroup>, selected: SystemAssetGroup?, assetCount: Int,
    onSelect: (SystemAssetGroup) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF0A2A40), Color(0xFF071927), Color(0xFF04121D)))
                ).padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(38.dp).background(Dhc6DesktopColors.AccentStrong, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Build, null, tint = Color.White) }
                        Column {
                            Text("SYSTEMS LIBRARY", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            Text("DHC-6 Series 300", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                        }
                    }
                    Text(
                        "Select a system, inspect the visual model, then review operation and failure recognition.",
                        color = Dhc6DesktopColors.TextSecondary, lineHeight = 19.sp, fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge("${groups.size} systems", true)
                        Badge("$assetCount assets", false)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
        ) {
            items(groups) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(group) },
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(
                        if (group == selected) 2.dp else 1.dp,
                        if (group == selected) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.Border,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (group == selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(38.dp).background(
                                if (group == selected) Dhc6DesktopColors.AccentStrong else Dhc6DesktopColors.SurfaceMedium,
                                RoundedCornerShape(11.dp),
                            ), contentAlignment = Alignment.Center,
                        ) {
                            Text(group.family.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(group.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${group.matchedAssets.size} indexed assets", color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Dhc6DesktopColors.TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemsLearningPane(
    group: SystemAssetGroup?, selectedTab: SystemsLabTab,
    onTabSelected: (SystemsLabTab) -> Unit, modifier: Modifier = Modifier,
) {
    DetailCard(modifier) {
        if (group == null) {
            EmptyState("No system selected", "Choose a system from the library to start the lesson.")
            return@DetailCard
        }
        val content = remember(group.family) { systemsLearningContent(group.family) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(group.family, true)
            Badge("${group.matchedAssets.size} assets", false)
        }
        Spacer(Modifier.height(8.dp))
        Text(group.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 33.sp)
        Text(group.description, color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SystemsLabTab.entries.forEach { item -> FilterChipLike(item.label, selectedTab == item) { onTabSelected(item) } }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Dhc6DesktopColors.Border)
        Spacer(Modifier.height(14.dp))

        when (selectedTab) {
            SystemsLabTab.OVERVIEW -> OverviewTab(group, content)
            SystemsLabTab.MODEL -> ModelTab(group)
            SystemsLabTab.OPERATION -> OperationTab(content)
            SystemsLabTab.FAILURES -> FailureTab(content)
        }
    }
}

@Composable
private fun OverviewTab(group: SystemAssetGroup, content: SystemLearningContent) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { LessonCallout(Icons.Filled.Info, "LEARNING OBJECTIVE", "Understand before memorising", content.objective, Dhc6DesktopColors.Accent) }
        item { LessonSection("Major components", content.components) }
        item {
            LessonCallout(
                Icons.Filled.Build, "ASSET STATUS", "${group.matchedAssets.size} packaged references indexed",
                if (group.matchedAssets.isEmpty()) "The lesson is available now; visual assets can be added later without changing the training structure."
                else "Use Model / schematic to inspect packaged references and the safe-mode fallback.",
                Dhc6DesktopColors.Green,
            )
        }
        item { LessonCallout(Icons.Filled.ChevronRight, "QRH BRIDGE", "Procedure context", content.qrhBridge, Dhc6DesktopColors.Gold) }
    }
}

@Composable
private fun ModelTab(group: SystemAssetGroup) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            if (group.family == "Engine") { Pt6aEngineViewer(); Spacer(Modifier.height(12.dp)) }
            SystemGlbViewer(group)
        }
        if (group.matchedAssets.isNotEmpty()) {
            item { Text("INDEXED REFERENCES", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp) }
            items(group.matchedAssets.take(12)) { path ->
                Card(
                    shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                    colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
                ) {
                    Text(path.cleanDisplay(), color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun OperationTab(content: SystemLearningContent) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            LessonCallout(
                Icons.Filled.PlayCircle, "NORMAL OPERATION", "Follow the system flow",
                "Conceptual training only. Aircraft limitations and operator procedures remain authoritative.",
                Dhc6DesktopColors.Green,
            )
        }
        item { LessonSection("System sequence", content.operation) }
    }
}

@Composable
private fun FailureTab(content: SystemLearningContent) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            LessonCallout(
                Icons.Filled.ErrorOutline, "FAILURE RECOGNITION", "Identify the indication first",
                "Classify the failure, stabilise the aircraft, coordinate PF/PM duties, then confirm the applicable QRH procedure.",
                Dhc6DesktopColors.Red,
            )
        }
        item { LessonSection("Common failure themes", content.failures) }
        item { LessonSection("Recognition cues", content.recognition) }
        item { LessonCallout(Icons.Filled.ChevronRight, "PROCEDURE DISCIPLINE", "QRH and AFM remain controlling", content.qrhBridge, Dhc6DesktopColors.Gold) }
    }
}

@Composable
private fun LessonSection(title: String, entries: List<String>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
            entries.forEachIndexed { index, entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(24.dp).background(Dhc6DesktopColors.SurfaceMedium, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${index + 1}", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                    Text(entry, color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LessonCallout(icon: ImageVector, eyebrow: String, title: String, body: String, tone: Color) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, tone.copy(alpha = 0.48f)),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.09f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(17.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).background(tone.copy(alpha = 0.20f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tone, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(eyebrow, color = tone, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(body, color = Dhc6DesktopColors.TextSecondary, lineHeight = 20.sp, fontSize = 13.sp)
            }
        }
    }
}
