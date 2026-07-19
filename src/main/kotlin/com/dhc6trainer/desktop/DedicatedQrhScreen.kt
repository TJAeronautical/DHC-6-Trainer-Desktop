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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class QrhMode(val label: String) {
    CATEGORIES("CATEGORIES"), ALL("ALL ITEMS"), FAVORITES("FAVORITES")
}

private data class QrhGroup(
    val title: String,
    val symbol: String,
    val accent: Color,
    val keywords: List<String>,
)

private val QrhGroups = listOf(
    QrhGroup("Engine Fire", "F", Color(0xFFE53935), listOf("engine fire", "fire engine", "fire in flight")),
    QrhGroup("Engine Failure", "!", Color(0xFFFF8A00), listOf("engine failure", "engine fail", "flameout", "single engine", "engine shutdown")),
    QrhGroup("Fuel", "D", Color(0xFFF0B429), listOf("fuel", "boost pump", "crossfeed")),
    QrhGroup("Electrical", "E", Color(0xFF247BFF), listOf("electrical", "generator", "battery", "bus", "inverter")),
    QrhGroup("Flight Controls", "C", Color(0xFF19C3D6), listOf("flight control", "rudder", "aileron", "elevator", "flap", "trim")),
    QrhGroup("Pressurization", "P", Color(0xFF9C6ADE), listOf("pressur", "cabin altitude", "decompression")),
    QrhGroup("Ice & Rain", "I", Color(0xFF64C8FF), listOf("ice", "icing", "de-ice", "anti-ice", "rain", "windshield")),
    QrhGroup("Miscellaneous", "M", Color(0xFF8FA7B5), emptyList()),
)

private fun qrhGroup(procedure: ProcedureSummary): QrhGroup {
    val text = "${procedure.rawName} ${procedure.drillName} ${procedure.context} ${procedure.id}".lowercase()
    return QrhGroups.dropLast(1).firstOrNull { group ->
        group.keywords.any { keyword -> keyword in text }
    } ?: QrhGroups.last()
}

@Composable
internal fun DedicatedQrhScreen(snapshot: ProcedureLibrarySnapshot) {
    val procedures = remember(snapshot.procedures) {
        snapshot.procedures.filter { it.category != ProcedureCategory.NORMAL }
    }
    var mode by remember { mutableStateOf(QrhMode.CATEGORIES) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<QrhGroup?>(null) }
    var selected by remember { mutableStateOf<ProcedureSummary?>(null) }
    var favorites by remember { mutableStateOf(emptySet<String>()) }

    val visible = remember(procedures, mode, query, group, favorites) {
        val base = when (mode) {
            QrhMode.CATEGORIES -> group?.let { selectedGroup -> procedures.filter { qrhGroup(it) == selectedGroup } }.orEmpty()
            QrhMode.ALL -> procedures
            QrhMode.FAVORITES -> procedures.filter { it.id in favorites }
        }
        val needle = query.trim().lowercase()
        if (needle.isBlank()) base else procedures.filter {
            needle in it.rawName.lowercase() || needle in it.context.lowercase() || needle in qrhGroup(it).title.lowercase()
        }
    }

    LaunchedEffect(mode, group, query, favorites) {
        if (mode == QrhMode.CATEGORIES && group == null && query.isBlank()) {
            selected = null
        } else if (selected !in visible) {
            selected = visible.firstOrNull()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dhc6DesktopColors.SurfaceDark),
        ) {
            QrhMode.entries.forEach { item ->
                val active = item == mode
                Box(
                    modifier = Modifier.weight(1f).clickable {
                        mode = item
                        group = null
                    }.background(if (active) Dhc6DesktopColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.label, color = if (active) Dhc6DesktopColors.Accent else Dhc6DesktopColors.TextSubtle, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search QRH procedures", color = Dhc6DesktopColors.TextSecondary) },
        )
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                if (mode == QrhMode.CATEGORIES && group == null && query.isBlank()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(QrhGroups) { item ->
                            QrhGroupRow(item, procedures.count { qrhGroup(it) == item }) {
                                group = item
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (group != null) {
                            Text("<", color = Dhc6DesktopColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable { group = null }.padding(end = 10.dp))
                        }
                        Text(
                            group?.title ?: if (mode == QrhMode.FAVORITES) "FAVORITES" else if (query.isNotBlank()) "SEARCH RESULTS" else "ALL QRH ITEMS",
                            color = Dhc6DesktopColors.TextMuted,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (visible.isEmpty()) {
                        QrhEmpty(if (mode == QrhMode.FAVORITES) "No favorites yet" else "No procedures found")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(visible) { procedure ->
                                QrhItemRow(
                                    procedure = procedure,
                                    selected = procedure == selected,
                                    favorite = procedure.id in favorites,
                                    onClick = { selected = procedure },
                                )
                            }
                        }
                    }
                }
            }

            QrhProcedureDetail(
                procedure = selected,
                favorite = selected?.id?.let { it in favorites } == true,
                onFavorite = {
                    val id = selected?.id
                    if (id != null) favorites = if (id in favorites) favorites - id else favorites + id
                },
                modifier = Modifier.weight(1.12f),
            )
        }
    }
}

@Composable
private fun QrhGroupRow(group: QrhGroup, count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QrhSymbol(group)
            Column(Modifier.weight(1f)) {
                Text(group.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("$count items", color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp)
            }
            Text(">", color = Dhc6DesktopColors.TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun QrhItemRow(procedure: ProcedureSummary, selected: Boolean, favorite: Boolean, onClick: () -> Unit) {
    val group = qrhGroup(procedure)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) group.accent else Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QrhSymbol(group, 34)
            Column(Modifier.weight(1f)) {
                Text(procedure.rawName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${procedure.stepCount} items - ${group.title}", color = Dhc6DesktopColors.TextSecondary, fontSize = 11.sp)
            }
            if (favorite) Text("*", color = Dhc6DesktopColors.Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun QrhSymbol(group: QrhGroup, size: Int = 42) {
    Box(
        modifier = Modifier.size(size.dp).background(group.accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(group.symbol, color = group.accent, fontWeight = FontWeight.Black, fontSize = if (size < 40) 14.sp else 17.sp)
    }
}

@Composable
private fun QrhProcedureDetail(procedure: ProcedureSummary?, favorite: Boolean, onFavorite: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
    ) {
        if (procedure == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { QrhEmpty("Select a QRH category or procedure") }
        } else {
            val group = qrhGroup(procedure)
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title.uppercase(), color = group.accent, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(if (favorite) "★" else "☆", color = if (favorite) Dhc6DesktopColors.Gold else Dhc6DesktopColors.TextSubtle,
                        fontSize = 25.sp, modifier = Modifier.clickable(onClick = onFavorite))
                }
                Spacer(Modifier.height(8.dp))
                Text(procedure.rawName, color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                Text("QUICK REFERENCE PROCEDURE", color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(13.dp))
                HorizontalDivider(color = Dhc6DesktopColors.BorderSoft)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 24.dp)) {
                    items(procedure.steps) { step -> QrhStep(step, group.accent) }
                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("Training reference only. Use the approved AFM/QRH and operator procedures for operational use.", color = Dhc6DesktopColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QrhStep(step: ProcedureStep, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${step.number ?: "-"}", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        Text(step.action, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        val reference = step.reference
        if (!reference.isNullOrBlank()) {
            Text("......", color = Dhc6DesktopColors.BorderSoft, modifier = Modifier.padding(horizontal = 8.dp))
            Text(reference.uppercase(), color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 70.dp))
        }
    }
}

@Composable
private fun QrhEmpty(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(28.dp)) {
        Text("QRH", color = Dhc6DesktopColors.TextSubtle, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text(message, color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
