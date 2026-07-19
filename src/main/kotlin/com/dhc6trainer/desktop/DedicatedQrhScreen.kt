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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.prefs.Preferences

private enum class QrhTab(val label: String) {
    CATEGORIES("CATEGORIES"), ALL("ALL ITEMS"), FAVORITES("FAVORITES")
}

private data class QrhCategory(
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val keywords: List<String>,
)

private val qrhCategories = listOf(
    QrhCategory("Engine Fire", Icons.Filled.MenuBook, Color(0xFFE53935), listOf("engine fire", "fire engine", "fire in flight")),
    QrhCategory("Engine Failure", Icons.Filled.Speed, Color(0xFFFF8A00), listOf("engine failure", "engine fail", "engine shutdown", "single engine", "flameout")),
    QrhCategory("Fuel", Icons.Filled.Flight, Color(0xFFF0B429), listOf("fuel", "boost pump", "crossfeed")),
    QrhCategory("Electrical", Icons.Filled.Build, Color(0xFF247BFF), listOf("electrical", "generator", "battery", "bus", "inverter")),
    QrhCategory("Flight Controls", Icons.Filled.Checklist, Color(0xFF19C3D6), listOf("flight control", "rudder", "aileron", "elevator", "flap", "trim")),
    QrhCategory("Pressurization", Icons.Filled.Settings, Color(0xFF9C6ADE), listOf("pressur", "cabin altitude", "decompression")),
    QrhCategory("Ice & Rain", Icons.Filled.Flight, Color(0xFF64C8FF), listOf("ice", "icing", "de-ice", "anti-ice", "rain", "windshield")),
    QrhCategory("Miscellaneous", Icons.Filled.MenuBook, Color(0xFF8FA7B5), emptyList()),
)

private object QrhFavoriteStore {
    private val prefs = Preferences.userRoot().node("com/dhc6trainer/desktop/qrh")
    fun load(): Set<String> = prefs.get("favorites", "").split('|').filter(String::isNotBlank).toSet()
    fun save(ids: Set<String>) = prefs.put("favorites", ids.sorted().joinToString("|"))
}

private fun categoryFor(procedure: ProcedureSummary): QrhCategory {
    val haystack = "${procedure.rawName} ${procedure.drillName} ${procedure.context} ${procedure.id}".lowercase()
    return qrhCategories.dropLast(1).firstOrNull { category ->
        category.keywords.any { keyword -> keyword in haystack }
    } ?: qrhCategories.last()
}

@Composable
internal fun DedicatedQrhScreen(snapshot: ProcedureLibrarySnapshot) {
    val qrhItems = remember(snapshot.procedures) {
        snapshot.procedures.filter { it.category != ProcedureCategory.NORMAL }
    }
    var tab by remember { mutableStateOf(QrhTab.CATEGORIES) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<QrhCategory?>(null) }
    var selected by remember { mutableStateOf<ProcedureSummary?>(null) }
    var favorites by remember { mutableStateOf(QrhFavoriteStore.load()) }

    val visibleItems = remember(qrhItems, tab, query, selectedCategory, favorites) {
        val source = when (tab) {
            QrhTab.CATEGORIES -> selectedCategory?.let { category -> qrhItems.filter { categoryFor(it) == category } }.orEmpty()
            QrhTab.ALL -> qrhItems
            QrhTab.FAVORITES -> qrhItems.filter { it.id in favorites }
        }
        val needle = query.trim().lowercase()
        if (needle.isBlank()) source else source.filter {
            needle in it.rawName.lowercase() || needle in it.context.lowercase() || needle in categoryFor(it).title.lowercase()
        }
    }

    LaunchedEffect(tab, selectedCategory, query, favorites) {
        if (tab != QrhTab.CATEGORIES || selectedCategory != null) {
            if (selected !in visibleItems) selected = visibleItems.firstOrNull()
        } else {
            selected = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dhc6DesktopColors.SurfaceDark),
        ) {
            QrhTab.entries.forEach { item ->
                val active = item == tab
                Box(
                    modifier = Modifier.weight(1f).clickable {
                        tab = item
                        selectedCategory = null
                    }.background(if (active) Dhc6DesktopColors.Accent.copy(alpha = .16f) else Color.Transparent)
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
            leadingIcon = { Icon(Icons.Filled.Search, "Search QRH", tint = Dhc6DesktopColors.Accent) },
            placeholder = { Text("Search QRH procedures", color = Dhc6DesktopColors.TextSubtle) },
        )
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                if (tab == QrhTab.CATEGORIES && selectedCategory == null && query.isBlank()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(qrhCategories) { category ->
                            val count = qrhItems.count { categoryFor(it) == category }
                            QrhCategoryRow(category, count) { selectedCategory = category }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedCategory != null) {
                            Text("‹", color = Dhc6DesktopColors.Accent, fontSize = 28.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable { selectedCategory = null }.padding(end = 8.dp))
                        }
                        Text(
                            selectedCategory?.title ?: when (tab) {
                                QrhTab.CATEGORIES -> "SEARCH RESULTS"
                                QrhTab.ALL -> "ALL QRH ITEMS"
                                QrhTab.FAVORITES -> "FAVORITES"
                            },
                            color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (visibleItems.isEmpty()) {
                        QrhEmpty(if (tab == QrhTab.FAVORITES) "No favorites yet" else "No procedures found")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(visibleItems, key = { it.id }) { procedure ->
                                QrhProcedureRow(procedure, procedure == selected, procedure.id in favorites) { selected = procedure }
                            }
                        }
                    }
                }
            }
            QrhDetailPane(
                procedure = selected,
                favorite = selected?.id in favorites,
                onToggleFavorite = {
                    val id = selected?.id ?: return@QrhDetailPane
                    favorites = if (id in favorites) favorites - id else favorites + id
                    QrhFavoriteStore.save(favorites)
                },
                modifier = Modifier.weight(1.12f),
            )
        }
    }
}

@Composable
private fun QrhCategoryRow(category: QrhCategory, count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).background(category.accent.copy(alpha = .18f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(category.icon, category.title, tint = category.accent, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(category.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("$count items", color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp)
            }
            Text("›", color = Dhc6DesktopColors.TextMuted, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun QrhProcedureRow(procedure: ProcedureSummary, selected: Boolean, favorite: Boolean, onClick: () -> Unit) {
    val category = categoryFor(procedure)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) category.accent else Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).background(category.accent.copy(alpha = .18f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(category.icon, null, tint = category.accent, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(procedure.rawName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${procedure.stepCount} items · ${category.title}", color = Dhc6DesktopColors.TextSecondary, fontSize = 11.sp)
            }
            if (favorite) Icon(Icons.Filled.Star, "Favorite", tint = Dhc6DesktopColors.Gold, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun QrhDetailPane(procedure: ProcedureSummary?, favorite: Boolean, onToggleFavorite: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxSize(), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Dhc6DesktopColors.Border), colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background)) {
        if (procedure == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { QrhEmpty("Select a QRH category or procedure") }
            return@Card
        }
        val category = categoryFor(procedure)
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.title.uppercase(), color = category.accent, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Star, if (favorite) "Remove favorite" else "Add favorite", tint = if (favorite) Dhc6DesktopColors.Gold else Dhc6DesktopColors.TextSubtle,
                    modifier = Modifier.size(25.dp).clickable(onClick = onToggleFavorite))
            }
            Spacer(Modifier.height(8.dp))
            Text(procedure.rawName, color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
            Text("QUICK REFERENCE PROCEDURE", color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = Dhc6DesktopColors.BorderSoft)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp, bottom = 24.dp)) {
                items(procedure.steps) { step -> QrhStepRow(step, category.accent) }
                item {
                    Spacer(Modifier.height(10.dp))
                    Text("Refer to the approved AFM/QRH and operator procedures for operational use.", color = Dhc6DesktopColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun QrhStepRow(step: ProcedureStep, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${step.number ?: "•"}", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        Text(step.action, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        if (!step.reference.isNullOrBlank()) {
            Text("······", color = Dhc6DesktopColors.BorderSoft, modifier = Modifier.padding(horizontal = 8.dp))
            Text(step.reference.uppercase(), color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 70.dp))
        }
    }
}

@Composable
private fun QrhEmpty(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(28.dp)) {
        Icon(Icons.Filled.MenuBook, null, tint = Dhc6DesktopColors.TextSubtle, modifier = Modifier.size(42.dp))
        Text(message, color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
