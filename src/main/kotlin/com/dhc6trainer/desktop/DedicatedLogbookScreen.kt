package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogbookFilter { ALL, MCC, DRILL }

@Composable
internal fun DedicatedLogbookScreen(onStartMcc: () -> Unit, onStartDrill: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(LogbookFilter.ALL) }
    var selected by remember { mutableStateOf<DrillAttempt?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val attempts = remember(refresh) { DesktopProgressStore.recentAttempts() }
    val shown = remember(attempts, filter) {
        attempts.filter {
            filter == LogbookFilter.ALL ||
                (filter == LogbookFilter.MCC && it.type == AttemptType.MCC) ||
                (filter == LogbookFilter.DRILL && it.type == AttemptType.DRILL)
        }
    }
    val average = if (shown.isEmpty()) 0 else shown.map { it.pct }.average().toInt()
    val weak = attempts.groupBy { it.title }
        .map { (title, records) -> Triple(title, records.map { it.pct }.average().toInt(), records.size) }
        .sortedBy { it.second }.take(4)

    LaunchedEffect(shown) { if (selected !in shown) selected = shown.firstOrNull() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogbookSummary("SESSIONS", attempts.size.toString(), Modifier.weight(1f))
            LogbookSummary("AVERAGE", if (shown.isEmpty()) "-" else "$average%", Modifier.weight(1f))
            LogbookSummary("MCC BEST", if (DesktopProgressStore.mccSessions() == 0) "-" else "${DesktopProgressStore.mccBest()}%", Modifier.weight(1f))
            LogbookSummary("DRILL BEST", if (DesktopProgressStore.drillSessions() == 0) "-" else "${DesktopProgressStore.drillBest()}%", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogbookFilter.entries.forEach { option -> FilterChipLike(option.name, filter == option) { filter = option } }
                Badge("${shown.size} shown", false)
            }
            OutlinedButton(onClick = { confirmClear = true }, enabled = attempts.isNotEmpty()) {
                Text("CLEAR HISTORY", color = Dhc6DesktopColors.Red, fontWeight = FontWeight.Black)
            }
        }
        if (attempts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
            ) {
                Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("No sessions recorded yet", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Complete an MCC Callout or Drill session. Scores, elapsed time and focus areas stay on this computer.", color = Dhc6DesktopColors.TextSecondary)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onStartMcc, colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong)) { Text("START MCC", color = Color.White, fontWeight = FontWeight.Black) }
                        OutlinedButton(onClick = onStartDrill) { Text("START DRILL", color = Color.White, fontWeight = FontWeight.Black) }
                    }
                }
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.15f).fillMaxHeight(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                    colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
                ) {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ATTEMPT HISTORY", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(shown, key = { it.epochMillis }) { attempt -> AttemptRow(attempt, selected?.epochMillis == attempt.epochMillis) { selected = attempt } }
                        }
                    }
                }
                Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AttemptDetail(selected, Modifier.weight(1f))
                    if (weak.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                            colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("FOCUS AREAS", color = Dhc6DesktopColors.Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                weak.forEach { (title, avg, count) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(title.cleanDisplay(), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$avg% · $count", color = if (avg < 70) Dhc6DesktopColors.Red else Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local history?") },
            text = { Text("This permanently removes all recorded MCC and Drill attempts from this computer.") },
            confirmButton = {
                Button(onClick = { DesktopProgressStore.clear(); refresh++; selected = null; confirmClear = false }, colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.Red)) {
                    Text("CLEAR", color = Color.White, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun LogbookSummary(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Dhc6DesktopColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AttemptRow(attempt: DrillAttempt, selected: Boolean, onClick: () -> Unit) {
    val accent = if (attempt.type == AttemptType.MCC) Dhc6DesktopColors.Accent else Dhc6DesktopColors.Green
    val date = SimpleDateFormat("dd MMM · HH:mm", Locale.US).format(Date(attempt.epochMillis))
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(58.dp).background(accent.copy(alpha = .16f), RoundedCornerShape(8.dp)).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(attempt.type.name, color = accent, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(attempt.title.cleanDisplay(), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(date, color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp)
            }
            Text("${attempt.pct}%", color = if (attempt.pct >= 70) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AttemptDetail(attempt: DrillAttempt?, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
    ) {
        if (attempt == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("Select an attempt", "Choose a recorded session to inspect its factual debrief.") }
        } else {
            val time = "%02d:%02d".format(attempt.elapsedSec / 60, attempt.elapsedSec % 60)
            val date = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.US).format(Date(attempt.epochMillis))
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Badge(attempt.type.name, true); Badge(date, false) }
                Text(attempt.title.cleanDisplay(), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailFact("SCORE", "${attempt.correct}/${attempt.total}", Modifier.weight(1f))
                    DetailFact("RESULT", "${attempt.pct}%", Modifier.weight(1f))
                    DetailFact("TIME", time, Modifier.weight(1f))
                }
                Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft), colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("DEBRIEF", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(if (attempt.pct >= 90) "Strong result. Maintain proficiency with periodic repetition." else "Review the missed or prompted items against approved training material before repeating this session.", color = Dhc6DesktopColors.TextSecondary, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Dhc6DesktopColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}
