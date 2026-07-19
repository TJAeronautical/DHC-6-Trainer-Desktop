package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay

private val DesktopVersion = DesktopBuildInfo.VersionName

private enum class DesktopSection(
    val title: String,
    val subtitle: String,
    val icon: String,
    val iconVector: ImageVector,
) {
    DASHBOARD("Dashboard", "Training overview and quick launch", "H", Icons.Filled.Home),
    PROCEDURES("Checklists", "Full normal, abnormal and emergency procedures", "C", Icons.Filled.Checklist),
    QRH("QRH", "Memory items and immediate-action recall", "Q", Icons.Filled.MenuBook),
    STUDY("Flashcards", "Browse and review shared flashcard decks", "F", Icons.Filled.Style),
    DRILL("Drill", "Multiple choice A/B/C/D knowledge check", "D", Icons.Filled.Speed),
    MCCALLOUT("MCC Callout", "PF/PM crew callout trainer", "M", Icons.Filled.RecordVoiceOver),
    COCKPIT("Cockpit", "Interactive Twin Otter flight deck sim", "C", Icons.Filled.Flight),
    SYSTEMS("Technical Lab", "PT6A, electrical, fuel, hydraulic study", "S", Icons.Filled.Build),
    PERFORMANCE("Performance", "Takeoff, landing, climb planning", "P", Icons.Filled.Assessment),
    LOGBOOK("Debrief Logbook", "Local attempt history and debrief notes", "L", Icons.Filled.History),
    INSTRUCTOR("Instructor", "Corporate and instructor workflow", "I", Icons.Filled.SupportAgent),
    SETTINGS("Settings", "Desktop diagnostics and configuration", "G", Icons.Filled.Settings),
}

private enum class StudyMode { BROWSE, REVIEW }

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DHC-6 Trainer Desktop $DesktopVersion",
        state = WindowState(width = 1440.dp, height = 900.dp),
        icon = DesktopImages.painter("icons/dhc6_trainer.png"),
    ) {
        Dhc6DesktopTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Dhc6DesktopColors.BackgroundDeep,
            ) {
                var isDesktopActivated by remember { mutableStateOf(DesktopLicenseStore.isActivated()) }

                if (isDesktopActivated) {
                    DesktopApp()
                } else {
                    DesktopActivationScreen(
                        onActivated = { isDesktopActivated = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopApp() {
    var section by remember { mutableStateOf(DesktopSection.DASHBOARD) }
    val procedureSnapshot = remember { DesktopProcedureContentLoader.load() }
    val flashcardSnapshot = remember { DesktopFlashcardContentLoader.load() }
    val assetSnapshot = remember { DesktopAssetCatalog.load() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Dhc6DesktopColors.BackgroundDeep,
                        Dhc6DesktopColors.Background,
                        Dhc6DesktopColors.BackgroundDeep
                    )
                )
            )
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        NavigationRail(
            selected = section,
            onSelect = { section = it },
            procedureCount = procedureSnapshot.procedures.size,
            flashcardCount = flashcardSnapshot.totalCards,
        )

        MainShell(
            section = section,
            procedureSnapshot = procedureSnapshot,
            flashcardSnapshot = flashcardSnapshot,
            assetSnapshot = assetSnapshot,
            onNavigate = { section = it },
        )
    }
}

@Composable
private fun NavigationRail(
    selected: DesktopSection,
    onSelect: (DesktopSection) -> Unit,
    procedureCount: Int,
    flashcardCount: Int,
) {
    val navState = rememberLazyListState()
    val selectedIndex = DesktopSection.entries.indexOf(selected).coerceAtLeast(0)
    LaunchedEffect(selected) {
        val targetIndex = if (selectedIndex <= 4) 0 else (selectedIndex - 3)
        navState.animateScrollToItem(targetIndex)
    }

    Card(
        modifier = Modifier
            .width(326.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = DesktopImages.painter("icons/dhc6_trainer.png"),
                    contentDescription = "DHC-6 Trainer logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column{
                    Text("DHC-6 Trainer", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        DesktopBuildInfo.EditionName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Desktop $DesktopVersion", color = Dhc6DesktopColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Dhc6DesktopColors.Border,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = navState,
                userScrollEnabled = true,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp),
            ) {
                items(DesktopSection.entries) { item ->
                    NavButton(
                        item = item,
                        selected = item == selected,
                        onClick = { onSelect(item) },
                    )
                }
            }

            StatusFooter(procedureCount = procedureCount, flashcardCount = flashcardCount)
        }
    }
}

@Composable
private fun NavButton(
    item: DesktopSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) Dhc6DesktopColors.CardSelected else Color.Transparent
    val border = if (selected) Dhc6DesktopColors.BorderBright else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 0.dp, border),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (selected) Dhc6DesktopColors.AccentStrong else Dhc6DesktopColors.SurfaceMedium,
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.iconVector,
                    contentDescription = item.title,
                    tint = if (selected) Color.White else Dhc6DesktopColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(
                    item.subtitle,
                    color = Dhc6DesktopColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusFooter(procedureCount: Int, flashcardCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "DESKTOP READY",
                color = Dhc6DesktopColors.Accent,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
            Text(
                "$procedureCount procedures - $flashcardCount cards",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Offline companion for DHC-6 Trainer. Content loads from shared procedure and asset packs.",
                color = Dhc6DesktopColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun MainShell(
    section: DesktopSection,
    procedureSnapshot: ProcedureLibrarySnapshot,
    flashcardSnapshot: FlashcardLibrarySnapshot,
    assetSnapshot: DesktopAssetCatalogSnapshot,
    onNavigate: (DesktopSection) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            HeaderBar(section.title, section.subtitle)

            Spacer(Modifier.height(16.dp))

            when (section) {
                DesktopSection.DASHBOARD -> DashboardScreen(procedureSnapshot, flashcardSnapshot, assetSnapshot, onNavigate)
                DesktopSection.PROCEDURES -> ProceduresScreen(procedureSnapshot)
                DesktopSection.QRH -> QrhScreen(procedureSnapshot)
                DesktopSection.STUDY -> StudyScreen(flashcardSnapshot)
                DesktopSection.MCCALLOUT -> MccCalloutScreen(procedureSnapshot)
                DesktopSection.DRILL -> DrillScreen(flashcardSnapshot)

                DesktopSection.COCKPIT -> CockpitScreen()
                DesktopSection.SYSTEMS -> SystemsLabScreen(assetSnapshot)
                DesktopSection.PERFORMANCE -> PerformanceScreen(assetSnapshot)
                DesktopSection.LOGBOOK -> LogbookScreen(onNavigate)
                DesktopSection.INSTRUCTOR -> InstructorScreen(onNavigate)
                DesktopSection.SETTINGS -> SettingsScreen(procedureSnapshot, flashcardSnapshot, assetSnapshot)
            }
        }
    }
}

@Composable
private fun HeaderBar(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column{
            Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Dhc6DesktopColors.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Badge("Offline $DesktopVersion", selected = false)
            Badge(DesktopBuildInfo.BuildLabel, selected = true)
        }
    }
}

@Composable
private fun DashboardScreen(
    procedureSnapshot: ProcedureLibrarySnapshot,
    flashcardSnapshot: FlashcardLibrarySnapshot,
    assetSnapshot: DesktopAssetCatalogSnapshot,
    onNavigate: (DesktopSection) -> Unit,
) {
    val latestAttempt = remember { DesktopProgressStore.recentAttempts(limit = 1).firstOrNull() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dhc6Space.xl),
        contentPadding = PaddingValues(bottom = Dhc6Space.xxxl),
    ) {
        // ── Welcome hero (mirrors "Welcome, Pilot" mockup) ────────────────────
        item {
            Card(
                shape = RoundedCornerShape(Dhc6Radius.xxl),
                border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Dhc6Gradients.HeroBanner)
                        .padding(Dhc6Space.xxxl),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Dhc6Space.md),
                        ) {
                            Text(
                                "Welcome, Pilot",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = Dhc6Type.DisplayLg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "DHC-6 Series 300 Twin Otter",
                                color = Dhc6DesktopColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = Dhc6Type.TitleSm,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(Dhc6Space.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Dhc6Space.sm)) {
                                DashPill("${procedureSnapshot.procedures.size} procedures", Dhc6DesktopColors.Red)
                                DashPill("${flashcardSnapshot.totalCards} flashcards", Dhc6DesktopColors.Accent)
                                DashPill("${assetSnapshot.systemGroups.size} systems", Dhc6DesktopColors.Green)
                            }
                        }
                        // Aircraft preview from cockpit sim
                        SimCockpitPreviewCard(
                            onOpen = { onNavigate(DesktopSection.COCKPIT) },
                            modifier = Modifier.width(320.dp).height(160.dp),
                        )
                    }
                }
            }
        }

        // ── Primary tile grid: 2×2 (QRH / Drill / Checklists / Performance) ──
        //   Matches the four large cards in the "Welcome, Pilot" mockup.
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dhc6Space.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DashQuickCard(
                    title = "QRH",
                    subtitle = "Quick Reference\nHandbook",
                    accent = Dhc6DesktopColors.Red,
                    iconVector = Icons.Filled.MenuBook,
                    onClick = { onNavigate(DesktopSection.QRH) },
                    modifier = Modifier.weight(1f),
                )
                DashQuickCard(
                    title = "Drill",
                    subtitle = "Practice\nQuestions",
                    accent = Dhc6DesktopColors.Accent,
                    iconVector = Icons.Filled.Speed,
                    onClick = { onNavigate(DesktopSection.DRILL) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dhc6Space.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DashQuickCard(
                    title = "Checklists",
                    subtitle = "Normal & Emergency\nChecklists",
                    accent = Dhc6DesktopColors.Green,
                    iconVector = Icons.Filled.Checklist,
                    onClick = { onNavigate(DesktopSection.PROCEDURES) },
                    modifier = Modifier.weight(1f),
                )
                DashQuickCard(
                    title = "Performance",
                    subtitle = "Takeoff, Landing &\nClimb Data",
                    accent = Dhc6DesktopColors.Gold,
                    iconVector = Icons.Filled.Assessment,
                    onClick = { onNavigate(DesktopSection.PERFORMANCE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Secondary tile row: MCC Callout + Flashcards ─────────────────────
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dhc6Space.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DashQuickCard(
                    title = "MCC Callout",
                    subtitle = "Master Cockpit Callouts\nfor DHC-6 Series 300",
                    accent = Dhc6DesktopColors.AccentBlue,
                    iconVector = Icons.Filled.RecordVoiceOver,
                    onClick = { onNavigate(DesktopSection.MCCALLOUT) },
                    modifier = Modifier.weight(1f),
                )
                DashQuickCard(
                    title = "Flashcards",
                    subtitle = "Study decks by\nsystem and topic",
                    accent = Dhc6DesktopColors.Warning,
                    iconVector = Icons.Filled.Style,
                    onClick = { onNavigate(DesktopSection.STUDY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Stat strip: same content, tighter styling ────────────────────────
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dhc6Space.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatCard("Procs", procedureSnapshot.procedures.size.toString(), "Loaded", Modifier.weight(1f))
                StatCard("Norm", procedureSnapshot.normalCount.toString(), "Norm", Modifier.weight(1f))
                StatCard("Abn", procedureSnapshot.abnormalCount.toString(), "Abn", Modifier.weight(1f))
                StatCard("Emerg", procedureSnapshot.emergencyCount.toString(), "Emerg", Modifier.weight(1f))
                StatCard("Cards", flashcardSnapshot.totalCards.toString(), "Cards", Modifier.weight(1f))
                StatCard(
                    "Ckpt",
                    assetSnapshot.cockpitTargets.size.toString(),
                    "Assets",
                    Modifier.weight(1f),
                )
            }
        }

        // ── Recent Activity ──────────────────────────────────────────────────
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "RECENT ACTIVITY",
                    color = Dhc6DesktopColors.TextMuted,
                    fontWeight = FontWeight.Black,
                    fontSize = Dhc6Type.LabelMd,
                )
                Text(
                    "VIEW ALL",
                    color = Dhc6DesktopColors.Accent,
                    fontWeight = FontWeight.Black,
                    fontSize = Dhc6Type.LabelMd,
                    modifier = Modifier.clickable { onNavigate(DesktopSection.LOGBOOK) },
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    onNavigate(if (latestAttempt == null) DesktopSection.MCCALLOUT else DesktopSection.LOGBOOK)
                },
                shape = RoundedCornerShape(Dhc6Radius.lg),
                border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
            ) {
                Row(
                    Modifier.padding(Dhc6Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Dhc6Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (cardColor, activityIcon) = when (latestAttempt?.type) {
                        AttemptType.MCC -> Dhc6DesktopColors.Accent to Icons.Filled.RecordVoiceOver
                        AttemptType.DRILL -> Dhc6DesktopColors.Green to Icons.Filled.Speed
                        null -> Dhc6DesktopColors.Gold to Icons.Filled.Flight
                    }
                    Box(
                        Modifier.size(44.dp).background(
                            cardColor.copy(alpha = 0.18f),
                            RoundedCornerShape(Dhc6Radius.md),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = activityIcon,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            latestAttempt?.title?.cleanDisplay() ?: "Start a training session",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = Dhc6Type.BodyLg,
                        )
                        Text(
                            latestAttempt?.let { "${it.correct}/${it.total} - ${it.pct}% - ${it.type.name}" }
                                ?: "MCC callouts and drills will be recorded in the debrief logbook.",
                            color = Dhc6DesktopColors.TextSecondary,
                            fontSize = Dhc6Type.BodySm,
                        )
                    }
                    Text(
                        latestAttempt?.let {
                            java.text.SimpleDateFormat("dd MMM", java.util.Locale.US)
                                .format(java.util.Date(it.epochMillis))
                        } ?: "Ready",
                        color = Dhc6DesktopColors.TextMuted,
                        fontSize = Dhc6Type.BodyXs,
                    )
                }
            }
        }

        // ── Sync status footer ───────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dhc6Radius.lg),
                border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
            ) {
                Row(
                    Modifier.padding(Dhc6Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Dhc6Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(Dhc6DesktopColors.Green, RoundedCornerShape(50)))
                    Text(
                        "SYNC STATUS",
                        color = Dhc6DesktopColors.TextMuted,
                        fontWeight = FontWeight.Black,
                        fontSize = Dhc6Type.LabelSm,
                    )
                    Text(
                        "Up to date - Offline-first - ${procedureSnapshot.procedures.size} procedures - ${flashcardSnapshot.totalCards} cards",
                        color = Dhc6DesktopColors.TextSecondary,
                        fontSize = Dhc6Type.BodySm,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Desktop $DesktopVersion",
                        color = Dhc6DesktopColors.TextMuted,
                        fontSize = Dhc6Type.BodyXs,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashPill(label: String, color: Color) {
    Card(
        shape  = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.40f)),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun DashQuickCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back-compat overload: existing call sites pass a single-letter `icon`
    // string; forward to a placeholder star icon so nothing breaks visually
    // during Step 1 migration. Update those call sites to the vector overload
    // in a follow-up patch.
    DashQuickCard(
        title = title,
        subtitle = subtitle,
        accent = accent,
        iconVector = Icons.Filled.Star,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun DashQuickCard(
    title: String,
    subtitle: String,
    accent: Color,
    iconVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(56.dp)
                    .background(accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                subtitle,
                color = Dhc6DesktopColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun CockpitPreviewCard() {
    Card(
        modifier = Modifier.width(320.dp).height(150.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderBright),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Overlay),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("MCC / COCKPIT", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("Desktop $DesktopVersion", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text("Cockpit simulator is live with scenarios, system state, annunciators, hitbox focus, and component-sprite controls.", color = Color.White, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("PF", true)
                Badge("PM", false)
                Badge("DRILL", false)
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Checklist screen matches the tabbed desktop procedure design.
// NORMAL / EMERGENCY tab bar, category icon rows, and dot-leader detail pane.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private enum class ChecklistTab { NORMAL, EMERGENCY }

@Composable
private fun QrhScreen(snapshot: ProcedureLibrarySnapshot) {
    DedicatedQrhScreen(snapshot)
}

@Composable
private fun ProceduresScreen(
    snapshot: ProcedureLibrarySnapshot,
    initialTab: ChecklistTab = ChecklistTab.NORMAL,
) {
    var tab      by remember(initialTab) { mutableStateOf(initialTab) }
    var selected by remember { mutableStateOf<ProcedureSummary?>(null) }

    val tabItems = remember(tab, snapshot.procedures) {
        when (tab) {
            ChecklistTab.NORMAL    -> snapshot.procedures.filter { it.category == ProcedureCategory.NORMAL }
            ChecklistTab.EMERGENCY -> snapshot.procedures.filter {
                it.category == ProcedureCategory.EMERGENCY || it.category == ProcedureCategory.ABNORMAL
            }
        }
    }

    LaunchedEffect(tab) { selected = tabItems.firstOrNull() }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {

        // â”€â”€ Left: tab bar + item list â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Column(Modifier.weight(1f)) {

            // NORMAL / EMERGENCY tab bar
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Dhc6DesktopColors.SurfaceDark),
            ) {
                ChecklistTab.entries.forEach { t ->
                    val sel    = t == tab
                    val accent = if (t == ChecklistTab.NORMAL) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (sel) accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { tab = t }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (t == ChecklistTab.NORMAL) "NORMAL" else "EMERGENCY",
                            color      = if (sel) accent else Dhc6DesktopColors.TextSubtle,
                            fontWeight = FontWeight.Black,
                            fontSize   = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                if (tab == ChecklistTab.NORMAL) "NORMAL PROCEDURES" else "ABNORMAL / EMERGENCY PROCEDURES",
                color      = Dhc6DesktopColors.TextMuted,
                fontWeight = FontWeight.Black,
                fontSize   = 11.sp,
            )
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier            = Modifier.fillMaxSize(),
            ) {
                items(tabItems) { proc ->
                    ChecklistItemRow(proc, selected == proc) { selected = proc }
                }
            }
        }

        // â”€â”€ Right: detail pane â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        ChecklistDetailPane(selected, Modifier.weight(1.1f))
    }
}

@Composable
private fun ChecklistItemRow(
    procedure: ProcedureSummary,
    selected:  Boolean,
    onClick:   () -> Unit,
) {
    val accent = when (procedure.category) {
        ProcedureCategory.NORMAL    -> Dhc6DesktopColors.Green
        ProcedureCategory.ABNORMAL  -> Dhc6DesktopColors.Gold
        ProcedureCategory.EMERGENCY -> Dhc6DesktopColors.Red
    }
    val icon = when (procedure.category) {
        ProcedureCategory.NORMAL    -> "N"
        ProcedureCategory.ABNORMAL  -> "A"
        ProcedureCategory.EMERGENCY -> "E"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border   = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else Dhc6DesktopColors.Border),
        colors   = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Row(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp)
                    .background(accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    procedure.rawName.cleanDisplay(),
                    color      = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize   = 16.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    "${procedure.stepCount} items",
                    color    = Dhc6DesktopColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(
                ">",
                color = Dhc6DesktopColors.TextMuted,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun ChecklistDetailPane(procedure: ProcedureSummary?, modifier: Modifier = Modifier) {
    DetailCard(modifier) {
        if (procedure == null) {
            EmptyState("Select a checklist", "Choose a procedure from the list to view its steps.")
            return@DetailCard
        }
        val accent = when (procedure.category) {
            ProcedureCategory.NORMAL    -> Dhc6DesktopColors.Green
            ProcedureCategory.ABNORMAL  -> Dhc6DesktopColors.Gold
            ProcedureCategory.EMERGENCY -> Dhc6DesktopColors.Red
        }
        val catLabel = procedure.category.displayName.uppercase()

        // â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Card(
                shape  = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = accent),
            ) {
                Text(
                    catLabel,
                    color      = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize   = 11.sp,
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text(
                "${procedure.stepCount} of ${procedure.stepCount}",
                color    = Dhc6DesktopColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            procedure.rawName.cleanDisplay(),
            color      = Color.White,
            fontSize   = 26.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 32.sp,
        )
        Text("PROCEDURE", color = Dhc6DesktopColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        // â”€â”€ Steps â€” dot-leader numbered format â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier            = Modifier.fillMaxSize(),
        ) {
            items(procedure.steps) { step ->
                ChecklistStepRow(step, accent)
            }
            item {
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                    colors   = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("NOTE", color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(
                            "Refer to AFM Section 3 for additional information.",
                            color    = Dhc6DesktopColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistStepRow(step: ProcedureStep, accent: Color) {
    Column {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${step.number ?: "*"}",
                color      = Dhc6DesktopColors.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                modifier   = Modifier.width(28.dp),
            )
            Text(
                step.action.cleanDisplay(),
                color      = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                modifier   = Modifier.weight(1f),
                lineHeight = 21.sp,
            )
            if (!step.reference.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.Canvas(Modifier.width(70.dp).height(16.dp)) {
                    val dotR  = 1.5.dp.toPx()
                    val pitch = dotR * 2 + 5.dp.toPx()
                    val count = (size.width / pitch).toInt().coerceAtLeast(0)
                    repeat(count) { i ->
                        drawCircle(
                            Color(0xFF4A6878),
                            dotR,
                            center = Offset(i * pitch, size.height * 0.6f),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    step.reference.cleanDisplay().uppercase(),
                    color      = accent,
                    fontWeight = FontWeight.Black,
                    fontSize   = 14.sp,
                    modifier   = Modifier.widthIn(min = 60.dp),
                    textAlign  = TextAlign.End,
                )
            }
        }
        if (step.requiresConfirmation == true) {
            Row(Modifier.padding(start = 28.dp, bottom = 3.dp)) {
                Text(
                    "CONFIRM",
                    color = Dhc6DesktopColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
        HorizontalDivider(color = Dhc6DesktopColors.Border.copy(alpha = 0.28f))
    }
}


@Composable
private fun StudyScreen(snapshot: FlashcardLibrarySnapshot) {
    var mode by remember { mutableStateOf(StudyMode.BROWSE) }
    var query by remember { mutableStateOf("") }
    var selectedDeck by remember { mutableStateOf(snapshot.decks.firstOrNull()) }
    var cardIndex by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val decks = remember(snapshot.decks, query) {
        val trimmedQuery = query.trim()
        snapshot.decks.filter { deck -> deck.matchesFlashcardQuery(trimmedQuery) }
    }

    LaunchedEffect(decks) {
        if (selectedDeck == null || selectedDeck !in decks) {
            selectedDeck = decks.firstOrNull()
            cardIndex = 0
            revealed = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun handleNext() {
        val size = selectedDeck?.cards?.size ?: 0
        if (size > 0) { cardIndex = (cardIndex + 1) % size; revealed = false }
    }
    fun handlePrev() {
        val size = selectedDeck?.cards?.size ?: 0
        if (size > 0) { cardIndex = if (cardIndex - 1 < 0) size - 1 else cardIndex - 1; revealed = false }
    }

    Row(
        Modifier.fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || mode != StudyMode.REVIEW) return@onKeyEvent false
                when (event.key) {
                    Key.Spacebar -> { if (!revealed) revealed = true else handleNext(); true }
                    Key.DirectionRight -> { handleNext(); true }
                    Key.DirectionLeft -> { handlePrev(); true }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChipLike("Browse", mode == StudyMode.BROWSE) { mode = StudyMode.BROWSE }
                FilterChipLike("Review", mode == StudyMode.REVIEW) { mode = StudyMode.REVIEW }
                Badge("${snapshot.decks.size} decks", false)
                Badge("${decks.size} shown", false)
                Badge("${snapshot.totalCards} cards", true)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search decks, card text, tags, or system") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                if (decks.isEmpty()) {
                    item {
                        EmptyState("No matching decks", "Try a system, procedure term, card answer, tag, or reference.")
                    }
                } else {
                    items(decks) { deck ->
                        FlashcardDeckRow(deck, selectedDeck == deck) {
                            selectedDeck = deck
                            cardIndex = 0
                            revealed = false
                        }
                    }
                }
            }
        }
        FlashcardDetailPane(
            deck = selectedDeck,
            mode = mode,
            cardIndex = cardIndex,
            revealed = revealed,
            onReveal = { revealed = true },
            onNext = { handleNext() },
            onPrev = { handlePrev() },
            modifier = Modifier.weight(0.95f)
        )
    }
}

private fun FlashcardDeckSummary.matchesFlashcardQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return deckName.contains(query, ignoreCase = true) ||
        description.contains(query, ignoreCase = true) ||
        systemId.contains(query, ignoreCase = true) ||
        variant.contains(query, ignoreCase = true) ||
        difficulty.contains(query, ignoreCase = true) ||
        cards.any { card ->
            card.front.contains(query, ignoreCase = true) ||
                card.back.contains(query, ignoreCase = true) ||
                card.tags.any { it.contains(query, ignoreCase = true) } ||
                card.references.any {
                    it.source.contains(query, ignoreCase = true) ||
                        it.locator.contains(query, ignoreCase = true)
                }
        }
}

@Composable
private fun FlashcardDeckRow(deck: FlashcardDeckSummary, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(deck.systemId.uppercase().cleanDisplay(), false)
                Badge(deck.variant.uppercase().cleanDisplay(), false)
                Badge(deck.difficulty.uppercase().cleanDisplay(), false)
            }

            Text(
                deck.deckName.cleanDisplay(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                "${deck.cards.size} cards - ${deck.systemId.uppercase().cleanDisplay()} - ${deck.difficulty.uppercase().cleanDisplay()}",
                color = Dhc6DesktopColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FlashcardDetailPane(
    deck: FlashcardDeckSummary?,
    mode: StudyMode,
    cardIndex: Int,
    revealed: Boolean,
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailCard(modifier) {
        if (deck == null) {
            EmptyState("No deck selected", "Select a flashcard deck to browse or review.")
            return@DetailCard
        }

        val card = deck.cards.getOrNull(cardIndex)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Badge(mode.name, true)
            Badge(deck.systemId.uppercase().cleanDisplay(), false)
            Badge(deck.variant.uppercase().cleanDisplay(), false)
            Badge("${cardIndex + 1}/${deck.cards.size.coerceAtLeast(1)}", false)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            deck.deckName.cleanDisplay(),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 31.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            "${deck.systemId.uppercase().cleanDisplay()} deck - ${deck.cards.size} cards - ${deck.difficulty.uppercase().cleanDisplay()}",
            color = Dhc6DesktopColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Deck", deck.cards.size.toString(), "JSON", Modifier.weight(1f))
            StatCard("Card", (cardIndex + 1).toString(), "Sel", Modifier.weight(1f))
            StatCard("Tags", (card?.tags?.size ?: 0).toString(), "Tags", Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        if (card == null) {
            EmptyState("No cards in deck", deck.sourcePath.cleanDisplay())
        } else if (mode == StudyMode.REVIEW) {
            ReviewCard(card, revealed, onReveal, onPrev, onNext)
        } else {
            BrowseCardList(deck)
        }
    }
}

@Composable
private fun ReviewCard(
    card: FlashcardItem,
    revealed: Boolean,
    onReveal: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.dp, Dhc6DesktopColors.BorderBright),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.CardSelected),
        modifier = Modifier.fillMaxWidth().heightIn(min = 230.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("FRONT", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(card.front.cleanDisplay(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            if (revealed) {
                HorizontalDivider(color = Dhc6DesktopColors.Border)
                Text("BACK", color = Dhc6DesktopColors.Green, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text(card.back.cleanDisplay(), color = Dhc6DesktopColors.TextSecondary, fontSize = 18.sp, lineHeight = 24.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPrev) { Text("Previous", color = Color.White) }
                Button(onClick = if (revealed) onNext else onReveal, colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong)) {
                    Text(if (revealed) "Next Card" else "Reveal Answer", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun BrowseCardList(deck: FlashcardDeckSummary) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(deck.cards) { card ->
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        card.front.cleanDisplay(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        "${deck.systemId.uppercase().cleanDisplay()} deck - ${deck.cards.size} cards",
                        color = Dhc6DesktopColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private enum class CockpitTab { FLIGHT_DECK, PANEL_EXPLORER }

@Composable
private fun CockpitScreen() {
    var tab by remember { mutableStateOf(CockpitTab.FLIGHT_DECK) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChipLike("Flight deck sim", tab == CockpitTab.FLIGHT_DECK) { tab = CockpitTab.FLIGHT_DECK }
            FilterChipLike("Panel explorer", tab == CockpitTab.PANEL_EXPLORER) { tab = CockpitTab.PANEL_EXPLORER }
        }
        when (tab) {
            CockpitTab.FLIGHT_DECK -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { SimCockpitScreen() }
            CockpitTab.PANEL_EXPLORER -> CockpitSpriteAndHitboxViewer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
@Composable
private fun SystemsLabScreen(assetSnapshot: DesktopAssetCatalogSnapshot) {
    var selected by remember { mutableStateOf(assetSnapshot.systemGroups.firstOrNull()) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.05f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge("${assetSnapshot.systemsAssetCount} indexed system assets", selected = true)
                Badge("${assetSnapshot.systemGroups.size} groups", selected = false)
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(assetSnapshot.systemGroups) { group ->
                    SystemGroupRow(group, selected == group) { selected = group }
                }
            }
        }

        DetailCard(Modifier.weight(0.95f)) {
            if (selected == null) {
                EmptyState("No system selected", "Select a system group to inspect its desktop asset wiring.")
                return@DetailCard
            }
            val group = selected!!
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge(group.family, selected = true)
                Badge("${group.matchedAssets.size} matched assets", selected = false)
            }
            Spacer(Modifier.height(16.dp))
            Text(group.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(group.description, color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            Spacer(Modifier.height(18.dp))
            // Engine group: tabbed 3D viewer + interactive cross-section diagram
            // All other groups: 3D viewer only (real GLB or PT6A-27 procedural fallback)
            if (group.family == "Engine") {
                var viewerTab by remember(group) { mutableStateOf(0) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clickable { viewerTab = 0 }) { Badge("3D Model", selected = viewerTab == 0) }
                    Box(Modifier.clickable { viewerTab = 1 }) { Badge("Cross-section", selected = viewerTab == 1) }
                }
                Spacer(Modifier.height(10.dp))
                when (viewerTab) {
                    0 -> SystemGlbViewer(group = group)
                    1 -> Pt6aEngineViewer()
                }
            } else {
                SystemGlbViewer(group = group)
            }
            Spacer(Modifier.height(18.dp))
            Text("Matched asset paths", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                val paths = group.matchedAssets.ifEmpty { listOf("No matching packaged asset path found yet. The viewer slot is ready for the next asset restoration/wiring pass.") }
                items(paths) { path -> AssetPathRow(path) }
            }
        }
    }
}

@Composable
private fun SystemGroupRow(group: SystemAssetGroup, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(group.family, selected = selected)
                Badge("${group.matchedAssets.size} assets", selected = false)
            }
            Text(group.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(group.description, color = Dhc6DesktopColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AssetPathRow(path: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Text(path.cleanDisplay(), color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(12.dp))
    }
}

// Performance screen lives in PerformanceScreen.kt.

@Composable
private fun LogbookScreen(onNavigate: (DesktopSection) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val attempts = remember(refresh) { DesktopProgressStore.recentAttempts() }
    val mccSessions = remember(refresh) { DesktopProgressStore.mccSessions() }
    val drillSessions = remember(refresh) { DesktopProgressStore.drillSessions() }
    val mccBest = remember(refresh) { DesktopProgressStore.mccBest() }
    val drillBest = remember(refresh) { DesktopProgressStore.drillBest() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // Summary tiles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LogbookStat("Total sessions", "${mccSessions + drillSessions}", Modifier.weight(1f))
            LogbookStat(
                "MCC best",
                if (mccSessions > 0) "$mccBest%" else "-",
                Modifier.weight(1f)
            )
            LogbookStat(
                "Drill best",
                if (drillSessions > 0) "$drillBest%" else "-",
                Modifier.weight(1f)
            )
        }

        if (attempts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No sessions recorded yet", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        "Finish an MCC callout or a Drill session and your attempts, scores and weak areas will appear here. Records stay on this computer.",
                        color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp,
                    )
                    Button(
                        onClick = { onNavigate(DesktopSection.MCCALLOUT) },
                        colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                    ) { Text("Start a callout drill", color = Color.White, fontWeight = FontWeight.Black) }
                }
            }
            return@Column
        }

        // Weak areas - lowest average score by title (min 1 attempt).
        val weak = remember(refresh) {
            attempts.groupBy { it.title }
                .map { (title, list) -> Triple(title, list.map { it.pct }.average().toInt(), list.size) }
                .sortedBy { it.second }
                .take(3)
        }
        if (weak.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
                colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "DEBRIEF - FOCUS AREAS",
                        color = Dhc6DesktopColors.Gold,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                    weak.forEach { (title, avg, n) ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "$avg% avg  -  $n",
                                color = if (avg < 70) Dhc6DesktopColors.Red else Dhc6DesktopColors.TextSecondary,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Recent attempts
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("RECENT ATTEMPTS", color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
            OutlinedButton(onClick = { DesktopProgressStore.clear(); refresh++ }) {
                Text("Clear history", color = Dhc6DesktopColors.Red, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(attempts) { a ->
                val date = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date(a.epochMillis))
                val time = "%02d:%02d".format(a.elapsedSec / 60, a.elapsedSec % 60)
                val tagColor = if (a.type == AttemptType.MCC) Dhc6DesktopColors.Accent else Color(0xFF22C55E)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                    colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(54.dp).background(tagColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Text(if (a.type == AttemptType.MCC) "MCC" else "DRILL", color = tagColor, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(a.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "$date  -  $time",
                                color = Dhc6DesktopColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "${a.correct}/${a.total}  (${a.pct}%)",
                            color = if (a.pct >= 70) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red,
                            fontWeight = FontWeight.Black, fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogbookStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
        }
    }
}

@Composable
private fun InstructorScreen(onNavigate: (DesktopSection) -> Unit) {
    val cards = listOf(
        "Class overview" to "Trainee list, current phase and assigned procedure packs.",
        "Session monitor" to "Live or imported MCC/cockpit drill results.",
        "Reports" to "Exportable progress summaries.",
        "Content control" to "Future corporate/operator content sets.",
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0A2540),
                                    Color(0xFF061828),
                                    Color(0xFF0F2030),
                                    Color(0xFF05121E),
                                )
                            )
                        )
                        .padding(26.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Instructor Tools", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 34.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Instructor mode is intentionally read-only for now. It defines the future layout for trainees, sessions, reports and desktop debrief tools.", color = Dhc6DesktopColors.TextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = { onNavigate(DesktopSection.DASHBOARD) }, colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong)) {
                                Text("Dashboard", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.width(24.dp))
                        CockpitPreviewCard()
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                cards.take(2).forEach { (heading, text) ->
                    InfoCard(heading, text, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                cards.drop(2).take(3).forEach { (heading, text) ->
                    InfoCard(heading, text, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    procedures: ProcedureLibrarySnapshot,
    flashcards: FlashcardLibrarySnapshot,
    assetSnapshot: DesktopAssetCatalogSnapshot,
) {
    val recentAttempts = remember { DesktopProgressStore.recentAttempts() }
    val totalSessions = remember {
        DesktopProgressStore.mccSessions() + DesktopProgressStore.drillSessions()
    }
    val openSourceSim = remember { OpenSourceSimLibrary.loadAuto() }
    val loaderNotes = (procedures.loadNotes + flashcards.loadNotes)
        .distinct()
        .joinToString("\n")
        .ifBlank { "Packaged desktop indexes loaded without additional notes." }
    val readiness = listOf(
        "Procedures: ${procedures.procedures.size}",
        "Flashcards: ${flashcards.totalCards}",
        "Cockpit targets: ${assetSnapshot.cockpitTargets.size}",
        "Systems assets: ${assetSnapshot.systemsAssetCount}",
        "Recent attempts: ${recentAttempts.size}",
    ).joinToString("\n")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
        item {
            InfoCard(
                "Desktop build",
                "DHC-6 Trainer Desktop $DesktopVersion. ${DesktopBuildInfo.EditionName}, ${DesktopBuildInfo.BuildLabel.lowercase()}, offline-first Windows Compose build.",
                Modifier.fillMaxWidth()
            )
        }
        item {
            InfoCard(
                "Content loader status",
                "${procedures.procedures.size} procedures, ${flashcards.totalCards} flashcards, ${assetSnapshot.allResourcePaths.size} indexed packaged resources loaded from desktop-local assets.",
                Modifier.fillMaxWidth()
            )
        }
        item {
            InfoCard(
                "Loader notes",
                loaderNotes,
                Modifier.fillMaxWidth()
            )
        }
        item {
            InfoCard(
                "Asset wiring",
                "Cockpit simulator: ${assetSnapshot.cockpitTargets.size} targets with live electrical, fuel, power, flap, and fire-control state. Systems lab: ${assetSnapshot.systemGroups.size} groups / ${assetSnapshot.systemsAssetCount} system assets.",
                Modifier.fillMaxWidth()
            )
        }
        item {
            InfoCard(
                "Open-source simulator packages",
                openSourceSim.settingsSummary,
                Modifier.fillMaxWidth()
            )
        }
        item {
            InfoCard(
                "Training data",
                "$totalSessions recorded sessions, ${recentAttempts.size} retained recent attempts. Progress is stored locally on this computer.",
                Modifier.fillMaxWidth()
            )
        }
        item {
            InfoCard(
                "Readiness checks",
                readiness,
                Modifier.fillMaxWidth()
            )
        }
    }
}

// Shared UI components moved to DesktopSharedUi.kt in PC v1.6.8.

@Composable
private fun DrillScreen(snapshot: FlashcardLibrarySnapshot) {
    var selectedDeck    by remember { mutableStateOf(snapshot.decks.firstOrNull()) }
    var sessionCards    by remember { mutableStateOf(selectedDeck?.cards.orEmpty().shuffled()) }
    var questionIndex   by remember { mutableIntStateOf(0) }
    var score           by remember { mutableIntStateOf(0) }
    var answered        by remember { mutableStateOf<Int?>(null) }
    var recorded        by remember { mutableStateOf(false) }
    var startMs         by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsed         by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedDeck) {
        sessionCards = selectedDeck?.cards.orEmpty().shuffled()
        questionIndex = 0; score = 0; answered = null; recorded = false
        startMs = System.currentTimeMillis(); elapsed = 0
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(timeMillis = 1_000); elapsed = ((System.currentTimeMillis() - startMs) / 1_000).toInt() }
    }

    val cards   = sessionCards
    val card    = cards.getOrNull(questionIndex)
    val correct = card?.back?.cleanDisplay() ?: ""

    val options = remember(questionIndex, selectedDeck?.deckId, sessionCards) {
        val currentCard = sessionCards.getOrNull(questionIndex) ?: return@remember emptyList()
        val ans = currentCard.back.cleanDisplay()
        val distractors = sessionCards
            .filter { it != currentCard && it.back.isNotBlank() }
            .map { it.back.cleanDisplay() }
            .filter { it.isNotBlank() && it != ans }
            .distinct()
            .shuffled()
            .take(3)
        (listOf(ans) + distractors).shuffled()
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

        // Section
        Column(Modifier.weight(0.85f)) {
            Image(
                painter = DesktopImages.painter("icons/dhc6_trainer.png"),
                contentDescription = "DHC-6 Trainer logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(10.dp))
            Text("Select Deck", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(snapshot.decks) { deck ->
                    val sel = deck == selectedDeck
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedDeck = deck
                            sessionCards = deck.cards.shuffled()
                            questionIndex = 0
                            score = 0
                            answered = null
                            recorded = false
                            startMs = System.currentTimeMillis()
                            elapsed = 0
                        },
                        shape  = RoundedCornerShape(14.dp),
                        border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.Border),
                        colors = CardDefaults.cardColors(containerColor = if (sel) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(deck.deckName.cleanDisplay(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${deck.cards.size} cards - ${deck.systemId.uppercase().cleanDisplay()}", color = Dhc6DesktopColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section
        Card(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            shape    = RoundedCornerShape(24.dp),
            border   = BorderStroke(1.dp, Dhc6DesktopColors.Border),
            colors   = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
        ) {
            if (selectedDeck == null || cards.isEmpty() || card == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Select a deck to begin", "All decks are available on the left.")
                }
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header row
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(
                            "QUESTION ${questionIndex + 1} OF ${cards.size}",
                            color = Dhc6DesktopColors.TextMuted, fontWeight = FontWeight.Black, fontSize = 12.sp,
                        )
                        Text("%02d:%02d".format(elapsed / 60, elapsed % 60), color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress   = { ((questionIndex + 1f) / cards.size.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color      = Dhc6DesktopColors.Accent,
                        trackColor = Dhc6DesktopColors.Border,
                    )
                    Text(card.front.cleanDisplay(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp, lineHeight = 28.sp)
                    Spacer(Modifier.height(4.dp))

                    // A/B/C/D options
                    val labels = listOf("A", "B", "C", "D")
                    options.forEachIndexed { i, option ->
                        val isCorrect  = option == correct
                        val isAnswered = answered != null
                        val isSelected = answered == i
                        val bg = when {
                            isAnswered && isCorrect                -> Color(0xFF0F3D22)
                            isAnswered && isSelected -> Color(0xFF3D0F0F)
                            else -> Dhc6DesktopColors.SurfaceDark
                        }
                        val bc = when {
                            isAnswered && isCorrect                -> Dhc6DesktopColors.Green
                            isAnswered && isSelected -> Dhc6DesktopColors.Red
                            else -> Dhc6DesktopColors.Border
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isAnswered) {
                                val nextScore = score + if (isCorrect) 1 else 0
                                answered = i
                                score = nextScore
                                if (questionIndex == cards.lastIndex && !recorded) {
                                    DesktopProgressStore.record(
                                        AttemptType.DRILL,
                                        selectedDeck?.deckName?.cleanDisplay() ?: "Drill",
                                        nextScore,
                                        cards.size,
                                        elapsed,
                                    )
                                    recorded = true
                                }
                            },
                            shape    = RoundedCornerShape(14.dp),
                            border   = BorderStroke(if (isAnswered && (isCorrect || isSelected)) 2.dp else 1.dp, bc),
                            colors   = CardDefaults.cardColors(containerColor = bg),
                        ) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(30.dp).background(Dhc6DesktopColors.SurfaceMedium, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Text(labels.getOrElse(i) { "$i" }, color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black)
                                }
                                Text(option, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), lineHeight = 20.sp)
                                if (isAnswered && isCorrect) Text("Correct", color = Dhc6DesktopColors.Green, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Feedback + next
                    if (answered != null) {
                        val isRight = options.getOrNull(answered!!) == correct
                        Card(
                            shape  = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isRight) Color(0xFF0A2A14) else Color(0xFF2A0A0A)),
                            border = BorderStroke(1.dp, if (isRight) Dhc6DesktopColors.Green else Dhc6DesktopColors.Red),
                        ) {
                            Text(
                                if (isRight) "Correct!" else "Incorrect. Answer: $correct",
                                color = if (isRight) Dhc6DesktopColors.Green else Color.White,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp), lineHeight = 20.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Score: $score / ${questionIndex + 1}", color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    if (questionIndex + 1 < cards.size) { questionIndex++; answered = null }
                                    else {
                                        sessionCards = selectedDeck?.cards.orEmpty().shuffled()
                                        questionIndex = 0
                                        score = 0
                                        answered = null
                                        recorded = false
                                        startMs = System.currentTimeMillis()
                                        elapsed = 0
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Dhc6DesktopColors.AccentStrong),
                            ) {
                                Text(if (questionIndex + 1 < cards.size) "NEXT" else "RESTART", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun String.cleanDisplay(): String {
    return this
        .replace('\u00A0', ' ')
        .replace('\u2013', '-')
        .replace('\u2014', '-')
        .replace('\u2022', '-')
        .replace(Regex("\\s+"), " ")
        .trim()
}
