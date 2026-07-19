package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class ChecklistMode { NORMAL, NON_NORMAL }

@Composable
internal fun DedicatedChecklistScreen(snapshot: ProcedureLibrarySnapshot) {
    var mode by remember { mutableStateOf(ChecklistMode.NORMAL) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ProcedureSummary?>(null) }

    val visible = remember(snapshot.procedures, mode, query) {
        val base = when (mode) {
            ChecklistMode.NORMAL -> snapshot.procedures.filter { it.category == ProcedureCategory.NORMAL }
            ChecklistMode.NON_NORMAL -> snapshot.procedures.filter { it.category != ProcedureCategory.NORMAL }
        }
        val q = query.trim()
        if (q.isBlank()) base else base.filter {
            it.rawName.contains(q, true) ||
                it.context.contains(q, true) ||
                it.steps.any { step -> step.action.contains(q, true) || step.reference.orEmpty().contains(q, true) }
        }
    }

    LaunchedEffect(mode, visible) {
        if (selected !in visible) selected = visible.firstOrNull()
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.width(390.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChecklistTabs(mode, snapshot) { mode = it }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search procedures or checklist items") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (mode == ChecklistMode.NORMAL) "NORMAL CHECKLISTS" else "ABNORMAL / EMERGENCY",
                    color = Dhc6DesktopColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("${visible.size} procedures", color = Dhc6DesktopColors.TextSecondary, fontSize = 11.sp)
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (visible.isEmpty()) {
                    item { EmptyState("No matching checklists", "Try a procedure title, context, action, or reference.") }
                } else {
                    items(visible, key = { it.id }) { procedure ->
                        ChecklistProcedureRow(procedure, procedure == selected) { selected = procedure }
                    }
                }
            }
        }
        ChecklistProcedureDetail(selected, Modifier.weight(1f))
    }
}

@Composable
private fun ChecklistTabs(mode: ChecklistMode, snapshot: ProcedureLibrarySnapshot, onSelect: (ChecklistMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Dhc6DesktopColors.SurfaceDark, RoundedCornerShape(14.dp)),
    ) {
        listOf(
            ChecklistMode.NORMAL to "NORMAL  ${snapshot.normalCount}",
            ChecklistMode.NON_NORMAL to "NON-NORMAL  ${snapshot.abnormalCount + snapshot.emergencyCount}",
        ).forEach { (tab, label) ->
            val selected = tab == mode
            val accent = if (tab == ChecklistMode.NORMAL) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red
            Box(
                Modifier.weight(1f)
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) accent else Dhc6DesktopColors.TextSubtle, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChecklistProcedureRow(procedure: ProcedureSummary, selected: Boolean, onClick: () -> Unit) {
    val accent = checklistAccent(procedure.category)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).background(accent.copy(alpha = 0.17f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(procedure.category.name.take(1), color = accent, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(procedure.rawName.cleanDisplay(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${procedure.stepCount} items · ${procedure.context.cleanDisplay()}",
                    color = Dhc6DesktopColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Dhc6DesktopColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ChecklistProcedureDetail(procedure: ProcedureSummary?, modifier: Modifier = Modifier) {
    DetailCard(modifier) {
        if (procedure == null) {
            EmptyState("Select a checklist", "Choose a procedure from the list to inspect its actions and references.")
            return@DetailCard
        }
        val accent = checklistAccent(procedure.category)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryPill(procedure.category)
            Spacer(Modifier.width(10.dp))
            Text(procedure.context.cleanDisplay().uppercase(), color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("${procedure.stepCount} ITEMS", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text(procedure.rawName.cleanDisplay(), color = Color.White, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
        Text(
            "${procedure.memoryCount} memory · ${procedure.flowCount} flow · ${procedure.variants.joinToString(" / ").cleanDisplay()}",
            color = Dhc6DesktopColors.TextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Dhc6DesktopColors.BorderSoft)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(procedure.steps) { step -> ChecklistActionRow(step, accent) }
            item {
                Spacer(Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                    colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TRAINING NOTE", color = Dhc6DesktopColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("Use the approved AFM, QRH, and operator checklist for operational use.", color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistActionRow(step: ProcedureStep, accent: Color) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((step.number ?: "•").toString(), color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                Text(step.action.cleanDisplay(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
                val sub = listOfNotNull(step.crewRole?.takeIf { it.isNotBlank() }, step.intent?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub.cleanDisplay(), color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!step.reference.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                Canvas(Modifier.width(72.dp).height(16.dp)) {
                    val pitch = 7.dp.toPx()
                    repeat((size.width / pitch).toInt()) { i -> drawCircle(Color(0xFF4A6878), 1.5.dp.toPx(), Offset(i * pitch, size.height * 0.62f)) }
                }
                Spacer(Modifier.width(8.dp))
                Text(step.reference.cleanDisplay().uppercase(), color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 70.dp, max = 180.dp))
            }
        }
        if (step.requiresConfirmation == true) {
            Text("CHALLENGE / RESPONSE CONFIRM", color = Dhc6DesktopColors.Accent, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 30.dp, bottom = 5.dp))
        }
        HorizontalDivider(color = Dhc6DesktopColors.Border.copy(alpha = 0.28f))
    }
}

private fun checklistAccent(category: ProcedureCategory): Color = when (category) {
    ProcedureCategory.NORMAL -> Dhc6DesktopColors.Green
    ProcedureCategory.ABNORMAL -> Dhc6DesktopColors.Gold
    ProcedureCategory.EMERGENCY -> Dhc6DesktopColors.Red
}
