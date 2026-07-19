package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SessionCompleteScreen(
    title: String,
    modeLabel: String,
    correct: Int,
    total: Int,
    elapsedSeconds: Int,
    promptsUsed: Int? = null,
    focusItems: List<String> = emptyList(),
    onReview: (() -> Unit)? = null,
    onRetry: () -> Unit,
    onHome: () -> Unit,
) {
    val pct = if (total > 0) correct * 100 / total else 0
    val grade = when {
        pct >= 90 -> "EXCELLENT"
        pct >= 75 -> "GOOD JOB"
        pct >= 60 -> "KEEP PRACTISING"
        else -> "REVIEW REQUIRED"
    }
    val accent = when {
        pct >= 90 -> Dhc6DesktopColors.Green
        pct >= 75 -> Dhc6DesktopColors.Gold
        else -> Dhc6DesktopColors.Red
    }
    val timeText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    val incorrect = (total - correct).coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Card(
            modifier = Modifier.weight(0.9f).fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
            colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("SESSION COMPLETE", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text(title.cleanDisplay(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, textAlign = TextAlign.Center)
                Text(modeLabel.uppercase(), color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                    Canvas(Modifier.size(190.dp)) {
                        val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(Color(0xFF16273A), -90f, 360f, false, style = stroke)
                        drawArc(accent, -90f, 360f * (pct / 100f), false, style = stroke)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pct%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 44.sp)
                        Text(grade, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultFact("$correct / $total", "Correct")
                    ResultFact("$incorrect", "Incorrect")
                    ResultFact(timeText, "Time")
                    promptsUsed?.let { ResultFact("$it", "Prompts") }
                }
            }
        }

        Card(
            modifier = Modifier.weight(1.1f).fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
            colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("DEBRIEF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(
                    "Only recorded session facts are shown. Review missed or prompted items against the approved training material.",
                    color = Dhc6DesktopColors.TextSecondary,
                    lineHeight = 20.sp,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                    colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("RECORDED OUTCOME", color = accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(
                            if (incorrect == 0) "All assessed items were correct." else "$incorrect assessed item${if (incorrect == 1) "" else "s"} require review.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text("REVIEW FOCUS", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val itemsToShow = focusItems.distinct().take(8)
                    if (itemsToShow.isEmpty()) {
                        item { ReviewFocusRow(if (incorrect == 0) "Maintain proficiency with another session." else "Review the incorrect or prompted sequence items.") }
                    } else {
                        items(itemsToShow) { ReviewFocusRow(it) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    onReview?.let { review ->
                        OutlinedButton(onClick = review, modifier = Modifier.weight(1f).height(50.dp)) {
                            Text("REVIEW", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                    ) {
                        Text("TRY AGAIN", color = Color.White, fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f).height(50.dp)) {
                        Text("HOME", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultFact(value: String, label: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp).widthIn(min = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(label, color = Dhc6DesktopColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReviewFocusRow(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("•", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black)
            Text(text.cleanDisplay(), color = Dhc6DesktopColors.TextSecondary, modifier = Modifier.weight(1f), lineHeight = 19.sp)
        }
    }
}
