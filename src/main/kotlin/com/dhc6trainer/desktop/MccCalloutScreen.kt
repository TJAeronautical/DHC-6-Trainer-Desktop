package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Data / enums
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

internal enum class MccCategory(
    val label: String,
    val description: String,
    val accent: Color,
    val icon: String,
) {
    STANDARD(
        "Standard Callouts", "Practice normal operation callouts",
        Color(0xFF22C55E), "N",
    ),
    ABNORMAL_EMG(
        "Abnormal & EMG", "Emergency & abnormal callout drill",
        Color(0xFFF0B429), "A",
    ),
    CREW_COORD(
        "Crew Coordination", "PF / PM call and response practice",
        Color(0xFF4DBBFF), "C",
    ),
}

private enum class MccPhase { HOME, SESSION, COMPLETE }

// Safe interim: AFM-only transcriptions and known placeholder shells are not
// operator MCC callout material. A future content-model revision should replace
// this allow/deny gate with explicit reviewed per-step callout data.
private val AfmOnlyProcedureNames = setOf(
    "Engine Failure Prior to Rotation",
    "Engine Failure Airborne, Prior to VMC",
    "Engine Failure Airborne, After VMC",
    "Engine Failure During Flight",
    "Engine Flameout",
    "Engine Fire on Ground",
    "Engine Fire in Flight",
    "Total Electrical Failure",
    "Uncommanded Feathering",
    "One Engine Inoperative Landing",
)

private fun ProcedureSummary.hasReviewedMccOverlay(): Boolean =
    !isPlaceholder && rawName !in AfmOnlyProcedureNames

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Single state holder avoids "assigned value never read" warnings that
// arise when multiple mutableStateOf delegates are written inside a local fun.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private data class MccState(
    val phase: MccPhase = MccPhase.HOME,
    val selectedCat: MccCategory = MccCategory.STANDARD,
    val selectedProcedureId: String? = null,
    val sessionSteps: List<ProcedureStep> = emptyList(),
    val procedureTitle: String = "",
    val currentStep: Int = 0,
    val score: Int = 0,
    val promptsUsed: Int = 0,
    val elapsed: Int = 0,
    val startMs: Long = 0L,
    val showResponsePicker: Boolean = false,
    val lastCorrect: Boolean? = null,
    val recorded: Boolean = false,
)

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Entry composable
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
internal fun MccCalloutScreen(procedureSnapshot: ProcedureLibrarySnapshot) {
    var s by remember { mutableStateOf(MccState()) }

    // Ticker runs while in SESSION phase. Stop TTS on any phase exit.
    LaunchedEffect(s.phase) {
        if (s.phase != MccPhase.SESSION) {
            DesktopTts.stop()
        }
        if (s.phase == MccPhase.SESSION) {
            val t0 = System.currentTimeMillis()
            s = s.copy(startMs = t0)
            while (s.phase == MccPhase.SESSION) {
                delay(1_000)
                s = s.copy(elapsed = ((System.currentTimeMillis() - t0) / 1_000).toInt())
            }
        }
    }

    // Record a finished session exactly once (lifetime counters + recent-attempts log).
    LaunchedEffect(s.phase) {
        if (s.phase == MccPhase.COMPLETE && !s.recorded) {
            DesktopProgressStore.record(
                AttemptType.MCC,
                s.procedureTitle,
                s.score,
                s.sessionSteps.size,
                s.elapsed,
            )
            s = s.copy(recorded = true)
        }
    }

    fun proceduresFor(cat: MccCategory): List<ProcedureSummary> {
        val eligible = procedureSnapshot.procedures.filter {
            it.hasReviewedMccOverlay()
        }
        return when (cat) {
            MccCategory.STANDARD -> eligible.filter {
                it.category == ProcedureCategory.NORMAL
            }
            MccCategory.ABNORMAL_EMG -> eligible.filter {
                it.category == ProcedureCategory.EMERGENCY ||
                    it.category == ProcedureCategory.ABNORMAL
            }
            MccCategory.CREW_COORD -> eligible
        }.filter {
            it.steps.withoutDuplicateVariantSteps().size >= 3
        }.sortedBy {
            it.rawName.cleanDisplay()
        }
    }

    fun startSession(cat: MccCategory, selectedProcedureId: String? = null) {
        val candidates = proceduresFor(cat)
        val proc = selectedProcedureId?.let { selectedId ->
            candidates.firstOrNull { it.id == selectedId }
        } ?: candidates.randomOrNull()
            ?: return

        s = MccState(
            phase = MccPhase.SESSION,
            selectedCat = cat,
            selectedProcedureId = proc.id,
            sessionSteps = proc.steps.withoutDuplicateVariantSteps().take(12),
            procedureTitle = proc.rawName,
        )
    }

    when {
        // â”€â”€ Response picker overlay â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        s.showResponsePicker && s.phase == MccPhase.SESSION -> {
            MccResponsePicker(
                step     = s.sessionSteps.getOrNull(s.currentStep),
                allSteps = s.sessionSteps,
                onSelect = { correct ->
                    val next = s.copy(
                        showResponsePicker = false,
                        score       = if (correct) s.score + 1 else s.score,
                        promptsUsed = if (!correct) s.promptsUsed + 1 else s.promptsUsed,
                        lastCorrect = correct,
                    )
                    s = if (next.currentStep + 1 >= next.sessionSteps.size)
                        next.copy(phase = MccPhase.COMPLETE)
                    else
                        next.copy(currentStep = next.currentStep + 1, lastCorrect = null)
                },
                onCancel = { s = s.copy(showResponsePicker = false) },
            )
        }

        // â”€â”€ Home â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        s.phase == MccPhase.HOME -> {
            val homeProcedures = proceduresFor(s.selectedCat)
            val selectedId = s.selectedProcedureId?.takeIf { id ->
                homeProcedures.any { it.id == id }
            }
            MccHomeView(
                selectedCat = s.selectedCat,
                onCatSelect = { category ->
                    s = s.copy(
                        selectedCat = category,
                        selectedProcedureId = null,
                    )
                },
                procedures = homeProcedures,
                selectedProcedureId = selectedId,
                onProcedureSelect = { id ->
                    s = s.copy(selectedProcedureId = id)
                },
                onStart = {
                    startSession(s.selectedCat, selectedId)
                },
                procedureCount = procedureSnapshot.procedures.count {
                    it.hasReviewedMccOverlay()
                },
                sessions = DesktopProgressStore.mccSessions(),
                bestPct = DesktopProgressStore.mccBest(),
            )
        }

// â”€â”€ Session â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        s.phase == MccPhase.SESSION -> {
            // Speak PM call whenever step changes
            val currentCallText = s.sessionSteps.getOrNull(s.currentStep)?.action?.cleanDisplay() ?: ""
            LaunchedEffect(s.currentStep) {
                if (currentCallText.isNotBlank()) DesktopTts.speak(currentCallText)
            }
            MccSessionView(
            cat            = s.selectedCat,
            procedureTitle = s.procedureTitle,
            steps          = s.sessionSteps,
            currentStep    = s.currentStep,
            score          = s.score,
            elapsed        = s.elapsed,
            lastCorrect    = s.lastCorrect,
            onShowResponse = { s = s.copy(showResponsePicker = true) },
            onBack         = {
                if (s.currentStep > 0)
                    s = s.copy(currentStep = s.currentStep - 1, lastCorrect = null)
            },
            onNext         = {
                if (s.currentStep + 1 < s.sessionSteps.size)
                    s = s.copy(currentStep = s.currentStep + 1, lastCorrect = null)
                else
                    s = s.copy(phase = MccPhase.COMPLETE)
            },
            onEnd          = { s = s.copy(phase = MccPhase.COMPLETE) },
        )
        }  // end SESSION block â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        else -> MccCompleteView(
            procedureTitle = s.procedureTitle,
            correct        = s.score,
            total          = s.sessionSteps.size,
            elapsed        = s.elapsed,
            prompts        = s.promptsUsed,
            onReview       = { s = s.copy(currentStep = 0, lastCorrect = null, phase = MccPhase.SESSION) },
            onTryAgain = { startSession(s.selectedCat, s.selectedProcedureId) },
            onHome = {
                s = MccState(
                    selectedCat = s.selectedCat,
                    selectedProcedureId = s.selectedProcedureId,
                )
            },
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Home view
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun MccHomeView(
    selectedCat: MccCategory,
    onCatSelect: (MccCategory) -> Unit,
    procedures: List<ProcedureSummary>,
    selectedProcedureId: String?,
    onProcedureSelect: (String?) -> Unit,
    onStart: () -> Unit,
    procedureCount: Int,
    sessions: Int,
    bestPct: Int,
) {
    var procedureMenuExpanded by remember(selectedCat) { mutableStateOf(false) }
    val selectedProcedure = procedures.firstOrNull {
        it.id == selectedProcedureId
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // â”€â”€ Hero â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Card(
            shape  = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0A2540), Color(0xFF05121E), Color(0xFF0D1E30))
                        )
                    )
                    .padding(28.dp),
            ) {
                Column {
                    Text(
                        "MCC CALLOUT TRAINER",
                        color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Master Cockpit Callouts for DHC-6 Series 300",
                        color = Dhc6DesktopColors.TextSecondary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MccPill("$procedureCount procedures", Dhc6DesktopColors.Accent)
                        MccPill("PF / PM drill",             Color(0xFF22C55E))
                        MccPill("Offline",                   Dhc6DesktopColors.Gold)
                    }
                }
                Image(
                    painter = DesktopImages.painter("icons/dhc6_trainer.png"),
                    contentDescription = "DHC-6 Trainer logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(118.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }
        }

        // â”€â”€ Category cards â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MccCategory.entries.forEach { cat ->
                MccCategoryCard(
                    cat = cat,
                    selected = cat == selectedCat,
                    onClick = { onCatSelect(cat) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Box(Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    procedureMenuExpanded = true
                },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    selectedCat.accent.copy(alpha = 0.65f),
                ),
                colors = CardDefaults.cardColors(
                    containerColor = Dhc6DesktopColors.SurfaceDark,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "PROCEDURE SELECTION",
                            color = selectedCat.accent,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                        )
                        Text(
                            selectedProcedure?.rawName?.cleanDisplay()
                                ?: "Random eligible procedure",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            maxLines = 1,
                        )
                        Text(
                            "${procedures.size} reviewed procedures available. " +
                                "AFM-only procedures are excluded from MCC/TTS.",
                            color = Dhc6DesktopColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        "SELECT",
                        color = selectedCat.accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                    )
                }
            }

            DropdownMenu(
                expanded = procedureMenuExpanded,
                onDismissRequest = { procedureMenuExpanded = false },
                modifier = Modifier.width(700.dp).heightIn(max = 280.dp),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Random eligible procedure",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    onClick = {
                        onProcedureSelect(null)
                        procedureMenuExpanded = false
                    },
                )
                procedures.forEach { procedure ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                procedure.rawName.cleanDisplay(),
                                maxLines = 1,
                            )
                        },
                        onClick = {
                            onProcedureSelect(procedure.id)
                            procedureMenuExpanded = false
                        },
                    )
                }
            }
        }

        // Progress + Start        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(22.dp),
                border   = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                colors   = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "PROGRESS OVERVIEW",
                        color = Dhc6DesktopColors.Accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Overall Progress",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            if (sessions > 0) "$bestPct%" else "-",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (bestPct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Dhc6DesktopColors.Accent,
                        trackColor = Dhc6DesktopColors.Border,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Sessions: $sessions",
                            color = Dhc6DesktopColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                        Text(
                            "Best: ${if (sessions > 0) "$bestPct%" else "-"}",
                            color = Dhc6DesktopColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Button(
                onClick = onStart,
                enabled = procedures.isNotEmpty(),
                modifier = Modifier.weight(1f).height(108.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Dhc6DesktopColors.AccentStrong,
                ),
            ) {
                Text(
                    if (selectedProcedure == null) {
                        "START RANDOM\nSESSION"
                    } else {
                        "START SELECTED\nPROCEDURE"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                )
            }
        }
    }

@Composable
private fun MccCategoryCard(
    cat: MccCategory, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick).heightIn(min = 116.dp),
        shape    = RoundedCornerShape(22.dp),
        border   = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cat.accent else Dhc6DesktopColors.Border),
        colors   = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(cat.accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(cat.icon, color = cat.accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Text(cat.label,       color = Color.White,                     fontWeight = FontWeight.Black,  fontSize = 17.sp, lineHeight = 22.sp)
            Text(cat.description, color = Dhc6DesktopColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Session view
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun MccSessionView(
    cat: MccCategory,
    procedureTitle: String,
    steps: List<ProcedureStep>,
    currentStep: Int,
    score: Int,
    elapsed: Int,
    lastCorrect: Boolean?,
    onShowResponse: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
) {
    val step     = steps.getOrNull(currentStep)
    val timeFmt  = "%02d:%02d".format(elapsed / 60, elapsed % 60)
    val scorePct = if (currentStep > 0) score * 100 / currentStep else 0

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(
                    "SESSION: ${procedureTitle.uppercase()}",
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp,
                )
                Text(
                    "${currentStep + 1} / ${steps.size}  -  Score $scorePct%  -  $timeFmt",
                    color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // TTS mute toggle
                var ttsOn by remember { mutableStateOf(DesktopTts.enabled) }
                OutlinedButton(onClick = {
                    DesktopTts.enabled = !DesktopTts.enabled
                    ttsOn = DesktopTts.enabled
                    if (!DesktopTts.enabled) DesktopTts.stop()
                }) {
                    Text(
                        if (ttsOn) "CALLOUTS ON" else "CALLOUTS OFF",
                        color = if (ttsOn) Dhc6DesktopColors.Accent else Dhc6DesktopColors.TextMuted,
                        fontWeight = FontWeight.Black, fontSize = 13.sp,
                    )
                }
                OutlinedButton(onClick = onEnd) {
                    Text("END SESSION", color = Dhc6DesktopColors.Red, fontWeight = FontWeight.Black)
                }
            }
        }

        // â”€â”€ PF / PM tab bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row {
            Box(
                modifier = Modifier.weight(1f)
                    .background(Dhc6DesktopColors.AccentStrong, RoundedCornerShape(topStart = 14.dp, topEnd = 0.dp))
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("PF (YOU)", color = Color.White, fontWeight = FontWeight.Black) }
            Box(
                modifier = Modifier.weight(1f)
                    .background(Dhc6DesktopColors.SurfaceDark, RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp))
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("PM", color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Black) }
        }

        // â”€â”€ Split pane â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

            // PM call card
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape    = RoundedCornerShape(0.dp, 20.dp, 20.dp, 20.dp),
                border   = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                colors   = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = DesktopImages.painter(
                                when (cat) {
                                    MccCategory.ABNORMAL_EMG -> "cockpit/source_exact/g950/annunciators/warning.png"
                                    MccCategory.CREW_COORD   -> "cockpit/source_exact/g950/annunciators/caution.png"
                                    MccCategory.STANDARD     -> "cockpit/source_exact/g950/annunciators/default.png"
                                }
                            ),
                            contentDescription = cat.label + " annunciator",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(34.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Text("PM CALLS", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                    if (step != null) {
                        Text(
                            step.action.cleanDisplay(),
                            color = Dhc6DesktopColors.Gold, fontWeight = FontWeight.Black,
                            fontSize = 26.sp, lineHeight = 34.sp,
                        )
                        step.reference?.takeIf { it.isNotBlank() }?.let {
                            Text(it.cleanDisplay(), color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // PF response card
            val responseAccent = when (lastCorrect) {
                true  -> Dhc6DesktopColors.Green
                false -> Dhc6DesktopColors.Red
                null  -> Dhc6DesktopColors.Border
            }
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape    = RoundedCornerShape(20.dp),
                border   = BorderStroke(if (lastCorrect != null) 2.dp else 1.dp, responseAccent),
                colors   = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("YOUR RESPONSE", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    when (lastCorrect) {
                        true -> {
                            Card(
                                shape  = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A2A14)),
                                border = BorderStroke(1.dp, Dhc6DesktopColors.Green),
                            ) {
                                Text(
                                    "EXPECTED RESPONSE",
                                    color = Dhc6DesktopColors.Green, fontWeight = FontWeight.Black, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                            Text(step?.action?.cleanDisplay() ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 28.sp)
                        }
                        false -> {
                            Card(
                                shape  = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A)),
                                border = BorderStroke(1.dp, Dhc6DesktopColors.Red),
                            ) {
                                Text(
                                    "INCORRECT - review this step",
                                    color = Dhc6DesktopColors.Red, fontWeight = FontWeight.Black, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                            Text(step?.action?.cleanDisplay() ?: "", color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
                        }
                        null -> {
                            Button(
                                onClick  = onShowResponse,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                            ) {
                                Text("Select Response", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        // â”€â”€ Step dots + nav â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        MccStepDots(total = steps.size, current = currentStep)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("BACK", color = Color.White, fontWeight = FontWeight.Black)
            }
            Button(
                onClick  = onNext,
                modifier = Modifier.weight(2f),
                colors   = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
            ) {
                Text("NEXT", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MccStepDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total.coerceAtMost(14)) { i ->
            val active = i == current
            val done   = i < current
            Box(
                modifier = Modifier
                    .size(if (active) 18.dp else 11.dp)
                    .background(
                        when { active -> Dhc6DesktopColors.Accent; done -> Dhc6DesktopColors.Green; else -> Dhc6DesktopColors.Border },
                        RoundedCornerShape(50),
                    )
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Response picker overlay
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun MccResponsePicker(
    step: ProcedureStep?,
    allSteps: List<ProcedureStep>,
    onSelect: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    if (step == null) return
    val correct = step.action.cleanDisplay()
    val options = remember(key1 = step) {
        val distractors = allSteps.filter { it != step && it.action.isNotBlank() }
            .shuffled().take(3).map { it.action.cleanDisplay() }
        (listOf(correct) + distractors).shuffled()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xDD010D18)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.65f),
            shape    = RoundedCornerShape(28.dp),
            border   = BorderStroke(1.dp, Dhc6DesktopColors.Border),
            colors   = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Surface),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("SELECT YOUR RESPONSE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                HorizontalDivider(color = Dhc6DesktopColors.Border)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(options) { opt ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(opt == correct) },
                            shape    = RoundedCornerShape(16.dp),
                            border   = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                            colors   = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
                        ) {
                            Text(
                                opt,
                                color = Color.White, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(16.dp).fillMaxWidth(), lineHeight = 22.sp,
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCEL", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Session complete view
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun MccCompleteView(
    procedureTitle: String,
    correct: Int,
    total: Int,
    elapsed: Int,
    prompts: Int,
    onReview: () -> Unit,
    onTryAgain: () -> Unit,
    onHome: () -> Unit,
) {
    SessionCompleteScreen(
        title = procedureTitle,
        modeLabel = "MCC Callout",
        correct = correct,
        total = total,
        elapsedSeconds = elapsed,
        promptsUsed = prompts,
        focusItems = if (prompts > 0) listOf("$prompts prompted or incorrect response${if (prompts == 1) "" else "s"} require review.") else emptyList(),
        onReview = onReview,
        onRetry = onTryAgain,
        onHome = onHome,
    )
}

@Composable
private fun MccStat(value: String, label: String) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text(label, color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MccBar(label: String, progress: Float, valueStr: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label,    color = Color.White,                    fontWeight = FontWeight.SemiBold, modifier = Modifier.width(160.dp))
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color      = Dhc6DesktopColors.Green,
            trackColor = Dhc6DesktopColors.Border,
        )
        Text(valueStr, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun MccPill(label: String, color: Color) {
    Card(
        shape  = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.40f)),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
