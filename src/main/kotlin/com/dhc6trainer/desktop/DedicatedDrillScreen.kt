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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

@Composable
internal fun DedicatedDrillScreen(snapshot: FlashcardLibrarySnapshot) {
    var selectedDeck by remember { mutableStateOf(snapshot.decks.firstOrNull()) }
    var cards by remember { mutableStateOf(selectedDeck?.cards.orEmpty().shuffled()) }
    var questionIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var startMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsed by remember { mutableIntStateOf(0) }
    var recorded by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var missedItems by remember { mutableStateOf(emptyList<String>()) }

    fun reset(deck: FlashcardDeckSummary? = selectedDeck) {
        selectedDeck = deck
        cards = deck?.cards.orEmpty().shuffled()
        questionIndex = 0
        score = 0
        selectedAnswer = null
        startMs = System.currentTimeMillis()
        elapsed = 0
        recorded = false
        completed = false
        missedItems = emptyList()
    }

    LaunchedEffect(startMs, completed) {
        while (!completed) {
            delay(1_000)
            elapsed = ((System.currentTimeMillis() - startMs) / 1_000).toInt()
        }
    }

    val card = cards.getOrNull(questionIndex)
    val correct = card?.back?.cleanDisplay().orEmpty()
    val options = remember(cards, questionIndex) {
        val current = cards.getOrNull(questionIndex) ?: return@remember emptyList()
        val answer = current.back.cleanDisplay()
        val distractors = cards.asSequence()
            .filter { it != current }
            .map { it.back.cleanDisplay() }
            .filter { it.isNotBlank() && it != answer }
            .distinct()
            .shuffled()
            .take(3)
            .toList()
        (listOf(answer) + distractors).shuffled()
    }

    if (completed && selectedDeck != null) {
    SessionCompleteScreen(
        title = selectedDeck!!.deckName,
        modeLabel = "Knowledge Drill",
        correct = score,
        total = cards.size,
        elapsedSeconds = elapsed,
        focusItems = missedItems,
        onReview = {
            completed = false
            questionIndex = 0
            selectedAnswer = null
            score = 0
            startMs = System.currentTimeMillis()
            elapsed = 0
            recorded = false
            missedItems = emptyList()
        },
        onRetry = { reset(selectedDeck) },
        onHome = { reset(null) },
    )
    return
}

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        DrillDeckRail(snapshot, selectedDeck) { reset(it) }

        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
            colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
        ) {
            if (card == null || selectedDeck == null || cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Select a deck to begin", "Choose a flashcard deck from the left-hand rail.")
                }
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DrillHeader(questionIndex, cards.size, elapsed, score)
                    LinearProgressIndicator(
                        progress = { ((questionIndex + 1f) / cards.size.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = Dhc6DesktopColors.Accent,
                        trackColor = Dhc6DesktopColors.Border,
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selectedDeck!!.deckName.cleanDisplay().uppercase(), color = Dhc6DesktopColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(card.front.cleanDisplay(), color = Color.White, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    val rows = options.chunked(2)
                    rows.forEachIndexed { rowIndex, rowOptions ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowOptions.forEachIndexed { colIndex, option ->
                                val optionIndex = rowIndex * 2 + colIndex
                                DrillAnswerCard(
                                    label = ('A'.code + optionIndex).toChar().toString(),
                                    text = option,
                                    selected = selectedAnswer == optionIndex,
                                    answered = selectedAnswer != null,
                                    correct = option == correct,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (selectedAnswer == null) {
                                        selectedAnswer = optionIndex
                                        val nextScore = score + if (option == correct) 1 else 0
                                        score = nextScore
                                        if (option != correct) missedItems = missedItems + card.front.cleanDisplay()
                                        if (questionIndex == cards.lastIndex && !recorded) {
                                            DesktopProgressStore.record(
                                                AttemptType.DRILL,
                                                selectedDeck!!.deckName.cleanDisplay(),
                                                nextScore,
                                                cards.size,
                                                elapsed,
                                            )
                                            recorded = true
                                        }
                                    }
                                }
                            }
                            if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    if (selectedAnswer != null) {
                        val isRight = options.getOrNull(selectedAnswer!!) == correct
                        DrillFeedback(isRight, correct, card)
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Score $score / ${questionIndex + 1}", color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    if (questionIndex < cards.lastIndex) {
                                        questionIndex++
                                        selectedAnswer = null
                                    } else completed = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                            ) {
                                Text(if (questionIndex < cards.lastIndex) "NEXT QUESTION" else "RESTART DRILL", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrillDeckRail(snapshot: FlashcardLibrarySnapshot, selected: FlashcardDeckSummary?, onSelect: (FlashcardDeckSummary) -> Unit) {
    Column(Modifier.width(300.dp).fillMaxHeight()) {
        Text("DRILL DECKS", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(snapshot.decks, key = { it.deckId }) { deck ->
                val active = deck == selected
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(deck) },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.Border),
                    colors = CardDefaults.cardColors(containerColor = if (active) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(deck.deckName.cleanDisplay(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${deck.cards.size} questions · ${deck.systemId.uppercase().cleanDisplay()}", color = Dhc6DesktopColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrillHeader(index: Int, total: Int, elapsed: Int, score: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("QUESTION ${index + 1} OF $total", color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("Knowledge Drill", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DrillStat("TIME", "%02d:%02d".format(elapsed / 60, elapsed % 60))
            DrillStat("SCORE", "$score/${index + 1}")
        }
    }
}

@Composable
private fun DrillStat(label: String, value: String) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Dhc6DesktopColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DrillAnswerCard(label: String, text: String, selected: Boolean, answered: Boolean, correct: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border = when {
        answered && correct -> Dhc6DesktopColors.Green
        answered && selected -> Dhc6DesktopColors.Red
        else -> Dhc6DesktopColors.Border
    }
    val background = when {
        answered && correct -> Color(0xFF0F3D22)
        answered && selected -> Color(0xFF3D0F0F)
        else -> Dhc6DesktopColors.SurfaceDark
    }
    Card(
        modifier = modifier.height(112.dp).clickable(enabled = !answered, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (answered && (selected || correct)) 2.dp else 1.dp, border),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).background(Dhc6DesktopColors.SurfaceMedium, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text(label, color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black)
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DrillFeedback(isRight: Boolean, correct: String, card: FlashcardItem) {
    val accent = if (isRight) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red
    val explanation = card.references.firstOrNull()?.let { "Reference: ${it.source.cleanDisplay()} ${it.locator.cleanDisplay()}" }
        ?: card.tags.take(3).joinToString(" · ").ifBlank { "Review the related flashcard deck before continuing." }
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (isRight) "Correct!" else "Incorrect", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
            if (!isRight) Text("Correct answer: $correct", color = Color.White, fontWeight = FontWeight.Bold)
            Text(explanation, color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
