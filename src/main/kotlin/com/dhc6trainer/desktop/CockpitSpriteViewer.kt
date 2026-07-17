package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.io.InputStream
import kotlin.math.roundToInt

@Stable
enum class CockpitSpriteVariant(val label: String) {
    Legacy("Legacy"),
    G950("G950")
}

@Stable
data class CockpitHitboxTarget(
    val id: String,
    val title: String,
    val area: String,
    val purpose: String,
    val legacyRect: Rect,
    val g950Rect: Rect = legacyRect,
)

@Stable
data class CockpitSpriteAsset(
    val variant: CockpitSpriteVariant,
    val path: String,
    val bitmap: ImageBitmap?,
)

private val DefaultCockpitTargets = listOf(
    CockpitHitboxTarget(
        id = "power-levers",
        title = "Power lever quadrant",
        area = "Centre pedestal",
        purpose = "Power lever, beta/reverse, rejected-takeoff and landing-roll flow awareness.",
        legacyRect = Rect(0.430f, 0.205f, 0.610f, 0.390f),
        g950Rect = Rect(0.430f, 0.205f, 0.610f, 0.390f),
    ),
    CockpitHitboxTarget(
        id = "fuel-panel",
        title = "Fuel panel / boost pump area",
        area = "Fuel controls",
        purpose = "Boost pump, selector and crossfeed awareness for fuel-system drills.",
        legacyRect = Rect(0.405f, 0.565f, 0.615f, 0.635f),
        g950Rect = Rect(0.420f, 0.565f, 0.620f, 0.645f),
    ),
    CockpitHitboxTarget(
        id = "electrical-panel",
        title = "Electrical DC generation panel",
        area = "Overhead electrical",
        purpose = "Battery, DC generators, avionics master and bus-power state for start and abnormal flows.",
        legacyRect = Rect(0.275f, 0.585f, 0.420f, 0.690f),
        g950Rect = Rect(0.265f, 0.600f, 0.410f, 0.700f),
    ),
    CockpitHitboxTarget(
        id = "engine-instruments",
        title = "Engine instruments",
        area = "Main panel",
        purpose = "Torque, T5, Ng/Np, oil pressure and fuel-flow scan target.",
        legacyRect = Rect(0.370f, 0.500f, 0.585f, 0.645f),
        g950Rect = Rect(0.405f, 0.500f, 0.585f, 0.610f),
    ),
    CockpitHitboxTarget(
        id = "annunciators",
        title = "Annunciator / CAS focus",
        area = "Alerting",
        purpose = "CAS/annunciator recognition and procedure-to-panel highlighting.",
        legacyRect = Rect(0.430f, 0.462f, 0.595f, 0.510f),
        g950Rect = Rect(0.045f, 0.465f, 0.940f, 0.530f),
    ),
    CockpitHitboxTarget(
        id = "flaps-hydraulic",
        title = "Flap / hydraulic control focus",
        area = "Hydraulic and flap controls",
        purpose = "Flap selection, hydraulic pressure, brake/steering and landing-abnormal flow review.",
        legacyRect = Rect(0.420f, 0.715f, 0.595f, 0.835f),
        g950Rect = Rect(0.420f, 0.700f, 0.595f, 0.825f),
    ),
    CockpitHitboxTarget(
        id = "fire-panel",
        title = "Fire protection panel",
        area = "Emergency controls",
        purpose = "Fire pull handles, detection, bottle discharge and memory-item confirmation.",
        legacyRect = Rect(0.405f, 0.445f, 0.610f, 0.500f),
        g950Rect = Rect(0.385f, 0.450f, 0.615f, 0.510f),
    ),
)
object DesktopCockpitSpriteCatalog {
    fun load(variant: CockpitSpriteVariant): CockpitSpriteAsset {
        val candidates = resourceCandidates(variant)
        for (path in candidates) {
            val bitmap = runCatching {
                val stream = openResource(path) ?: return@runCatching null
                stream.use { it.readAllBytes().decodeToImageBitmap() }
            }.getOrNull()
            if (bitmap != null) {
                return CockpitSpriteAsset(variant = variant, path = path, bitmap = bitmap)
            }
        }
        return CockpitSpriteAsset(variant = variant, path = candidates.firstOrNull().orEmpty(), bitmap = null)
    }

    private fun openResource(path: String): InputStream? {
        val loader = Thread.currentThread().contextClassLoader
        return loader.getResourceAsStream(path)
            ?: DesktopCockpitSpriteCatalog::class.java.classLoader.getResourceAsStream(path)
            ?: DesktopCockpitSpriteCatalog::class.java.getResourceAsStream("/$path")
    }

    private fun resourceCandidates(variant: CockpitSpriteVariant): List<String> {
        val manifestPaths = readCockpitManifest()

        val fullPanelManifestPaths = manifestPaths
            .filter { path -> isVariantMatch(path, variant) }
            .filterNot { path -> isComponentSpritePath(path) }
            .filter { path -> isLikelyFullPanelPath(path) }

        val names = when (variant) {
            CockpitSpriteVariant.Legacy -> listOf(
                "cockpit_legacy.png",
                "legacy_cockpit.png",
                "full_cockpit_legacy.png",
                "legacy_full_cockpit.png",
                "panel_legacy.png",
                "source_exact_legacy.png",
                "source-exact-legacy.png",
                "legacy.png"
            )
            CockpitSpriteVariant.G950 -> listOf(
                "cockpit_g950.png",
                "g950_cockpit.png",
                "full_cockpit_g950.png",
                "g950_full_cockpit.png",
                "panel_g950.png",
                "source_exact_g950.png",
                "source-exact-g950.png",
                "g950.png"
            )
        }

        val roots = listOf(
            "",
            "cockpit/",
            "cockpit/source_exact/",
            "cockpit/source_exact/${variant.name.lowercase()}/",
            "cockpit/source-exact/",
            "cockpit/source-exact/${variant.name.lowercase()}/",
            "assets/cockpit/",
            "assets/cockpit/source_exact/",
            "assets/cockpit/source_exact/${variant.name.lowercase()}/"
        )

        val namedCandidates = roots.flatMap { root -> names.map { "$root$it" } }

        return (namedCandidates + fullPanelManifestPaths).distinct()
    }

    private fun isVariantMatch(path: String, variant: CockpitSpriteVariant): Boolean {
        val lower = path.lowercase()
        return when (variant) {
            CockpitSpriteVariant.Legacy -> lower.contains("legacy") || lower.contains("classic") || lower.contains("steam")
            CockpitSpriteVariant.G950 -> lower.contains("g950") || lower.contains("garmin")
        }
    }

    private fun isComponentSpritePath(path: String): Boolean {
        val lower = path.lowercase()
        return listOf(
            "/annunciators/",
            "/avionics_master/",
            "/avionics/",
            "/cas-library/",
            "/instruments/",
            "/autofeather",
            "/switch",
            "/switches/",
            "/selector",
            "/selectors/",
            "/lights/",
            "/buttons/",
            "/knobs/",
            "/levers/",
            "/quadrants/",
            "/gauges/"
        ).any { marker -> lower.contains(marker) }
    }

    private fun isLikelyFullPanelPath(path: String): Boolean {
        val lower = path.lowercase()
        val fileName = lower.substringAfterLast("/")
        return (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) &&
            (
                fileName.contains("full") ||
                fileName.contains("cockpit") ||
                fileName.contains("flight_deck") ||
                fileName.startsWith("panel_") ||
                fileName.startsWith("source_exact") ||
                fileName.startsWith("source-exact")
            )
    }

    private fun readCockpitManifest(): List<String> {
        val stream = openResource("cockpit-source-exact-index.txt") ?: return emptyList()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { path ->
                    val lower = path.lowercase()
                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                }
                .toList()
        }
    }
}

internal enum class CockpitStageMode { FreeFlight, Panel2d }

@Composable
fun CockpitSpriteAndHitboxViewer(
    modifier: Modifier = Modifier,
) {
    var variant by remember { mutableStateOf(CockpitSpriteVariant.Legacy) }
    var selectedTarget by remember { mutableStateOf(DefaultCockpitTargets.first()) }
    var simulatorState by remember { mutableStateOf(DesktopCockpitSimState.beforeStart()) }
    var simulatorScenario by remember { mutableStateOf(CockpitSimScenario.BEFORE_START) }
    var stageMode by remember { mutableStateOf(CockpitStageMode.FreeFlight) }
    val freeFlightSession = remember { FreeFlightSession() }
    var freeFlightCamera by remember { mutableStateOf(freeFlightSession.cameraMode) }
    // The camera can also change from inside the sim (C key); mirror it so the
    // Inside/Outside chips stay in sync.
    LaunchedEffect(freeFlightSession) {
        while (true) {
            withFrameNanos { freeFlightCamera = freeFlightSession.cameraMode }
        }
    }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var simulatorFullScreen by remember { mutableStateOf(false) }
    var detailExpanded by remember { mutableStateOf(false) }
    var simulatorControlsExpanded by remember { mutableStateOf(false) }
    var targetsExpanded by remember { mutableStateOf(false) }

    val asset = remember(variant) { DesktopCockpitSpriteCatalog.load(variant) }
    val selectedRect by remember(variant, selectedTarget) {
        derivedStateOf {
            when (variant) {
                CockpitSpriteVariant.Legacy -> selectedTarget.legacyRect
                CockpitSpriteVariant.G950 -> selectedTarget.g950Rect
            }
        }
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(if (simulatorFullScreen) 0.dp else 16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(if (simulatorFullScreen) 1f else 1.85f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitChip("Legacy", selected = variant == CockpitSpriteVariant.Legacy) {
                        variant = CockpitSpriteVariant.Legacy
                        zoom = 1.0f
                        panX = 0f
                        panY = 0f
                    }
                    CockpitChip("G950", selected = variant == CockpitSpriteVariant.G950) {
                        variant = CockpitSpriteVariant.G950
                        zoom = 1.0f
                        panX = 0f
                        panY = 0f
                    }
                    CockpitChip("Free flight", selected = stageMode == CockpitStageMode.FreeFlight) {
                        stageMode = CockpitStageMode.FreeFlight
                    }
                    CockpitChip(
                        "Inside",
                        selected = stageMode == CockpitStageMode.FreeFlight &&
                            freeFlightCamera == FreeFlightCameraMode.Cockpit,
                    ) {
                        stageMode = CockpitStageMode.FreeFlight
                        freeFlightSession.cameraMode = FreeFlightCameraMode.Cockpit
                    }
                    CockpitChip(
                        "Outside",
                        selected = stageMode == CockpitStageMode.FreeFlight &&
                            freeFlightCamera != FreeFlightCameraMode.Cockpit,
                    ) {
                        stageMode = CockpitStageMode.FreeFlight
                        freeFlightSession.cameraMode = FreeFlightCameraMode.Chase
                    }
                    CockpitChip("2D panel", selected = stageMode == CockpitStageMode.Panel2d) {
                        stageMode = CockpitStageMode.Panel2d
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitChip(
                        label = if (simulatorFullScreen) "Exit full screen" else "Full screen",
                        selected = simulatorFullScreen,
                    ) {
                        simulatorFullScreen = !simulatorFullScreen
                    }
                }
            }

            val stageMinHeight = if (simulatorFullScreen) 620.dp else 440.dp
            val stageModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = stageMinHeight)
                .weight(1f)

            val flightSim2dStage: @Composable (Modifier) -> Unit = { stageMod ->
                FlightSimCockpitStage(
                    variant = variant,
                    simView = Dhc6SimulatorView.InsideCockpit,
                    selectedTarget = selectedTarget,
                    state = simulatorState,
                    onStateChange = { simulatorState = it },
                    onSelectTargetId = { targetId ->
                        DefaultCockpitTargets.firstOrNull { it.id == targetId }?.let { selectedTarget = it }
                    },
                    zoom = zoom,
                    panX = panX,
                    panY = panY,
                    onPan = { dx, dy ->
                        panX += dx
                        panY += dy
                    },
                    modifier = stageMod,
                    showStageChrome = false,
                )
            }

            when (stageMode) {
                CockpitStageMode.FreeFlight -> FreeFlightScreen(stageModifier, freeFlightSession)
                CockpitStageMode.Panel2d -> flightSim2dStage(stageModifier)
            }

            if (stageMode == CockpitStageMode.Panel2d) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitChip("Fit", selected = zoom == 0.85f && panX == 0f && panY == 0f) {
                        zoom = 0.85f
                        panX = 0f
                        panY = 0f
                    }
                    CockpitChip("100%", selected = zoom == 1.0f && panX == 0f && panY == 0f) {
                        zoom = 1.0f
                        panX = 0f
                        panY = 0f
                    }
                    CockpitChip("Pan reset", selected = false) {
                        panX = 0f
                        panY = 0f
                    }
                    CockpitChip("-", selected = false) {
                        zoom = (if (zoom > 2f) zoom - 0.5f else zoom - 0.15f).coerceAtLeast(0.70f)
                    }
                    CockpitChip("+", selected = false) {
                        zoom = (if (zoom >= 2f) zoom + 0.5f else zoom + 0.15f).coerceAtMost(8.0f)
                    }
                    CockpitChip("Max", selected = zoom == 8.0f) { zoom = 8.0f }
                }
            }

        }
        if (!simulatorFullScreen) {
            LazyColumn(
                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item {
                    CockpitExpandableGroup(
                        title = "Selected system",
                        subtitle = selectedTarget.title,
                        expanded = detailExpanded,
                        onToggle = { detailExpanded = !detailExpanded },
                    ) {
                        CockpitDetailPanel(
                            variant = variant,
                            selectedTarget = selectedTarget,
                            asset = asset,
                        )
                    }
                }

                item {
                    CockpitExpandableGroup(
                        title = "Simulator controls",
                        subtitle = simulatorScenario.title,
                        expanded = simulatorControlsExpanded,
                        onToggle = { simulatorControlsExpanded = !simulatorControlsExpanded },
                    ) {
                        CockpitSimulatorPanel(
                            selectedTarget = selectedTarget,
                            state = simulatorState,
                            scenario = simulatorScenario,
                            onStateChange = { simulatorState = it },
                            onScenarioChange = { simulatorScenario = it },
                            onSelectTargetId = { targetId ->
                                DefaultCockpitTargets.firstOrNull { it.id == targetId }?.let { selectedTarget = it }
                            },
                        )
                    }
                }

                item {
                    CockpitExpandableGroup(
                        title = "Cockpit targets",
                        subtitle = "${DefaultCockpitTargets.size} mapped areas",
                        expanded = targetsExpanded,
                        onToggle = { targetsExpanded = !targetsExpanded },
                    ) {
                        DefaultCockpitTargets.forEach { target ->
                            CockpitTargetCard(
                                target = target,
                                selected = target.id == selectedTarget.id,
                                onClick = { selectedTarget = target },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitImageStage(
    asset: CockpitSpriteAsset,
    selectedTarget: CockpitHitboxTarget,
    selectedRect: Rect,
    zoom: Float,
    panX: Float,
    panY: Float,
    onPan: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF061B2A))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPan(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val offsetX = panX.roundToInt()
        val offsetY = panY.roundToInt()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX, offsetY) }
                .zIndex(0f),
            contentAlignment = Alignment.Center,
        ) {
            val imageBoundsScale = (0.92f * zoom).coerceIn(0.70f, 1.0f)

            if (asset.bitmap != null) {
                Image(
                    bitmap = asset.bitmap,
                    contentDescription = "${asset.variant.label} cockpit sprite",
                    modifier = Modifier
                        .fillMaxWidth(imageBoundsScale)
                        .fillMaxHeight(imageBoundsScale),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                )
            } else {
                CockpitSpriteComposerStage(asset.variant, selectedTarget)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val visualWidth = size.width * (0.82f * zoom).coerceIn(0.55f, 0.98f)
                val visualHeight = size.height * (0.72f * zoom).coerceIn(0.50f, 0.92f)
                val left = (size.width - visualWidth) / 2f
                val top = (size.height - visualHeight) / 2f
                val rect = Rect(
                    offset = Offset(
                        left + selectedRect.left * visualWidth,
                        top + selectedRect.top * visualHeight
                    ),
                    size = Size(
                        (selectedRect.right - selectedRect.left) * visualWidth,
                        (selectedRect.bottom - selectedRect.top) * visualHeight
                    )
                )
                drawRect(
                    color = Color(0x6655C7FF),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = Color(0xFF55C7FF),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 4f),
                )
            }
        }

        CockpitStageBadge(
            text = if (asset.bitmap == null) "Overlay pinned - component board" else "Overlay pinned - drag image to pan",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .zIndex(20f),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .zIndex(20f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CockpitStageBadge("${selectedTarget.title} - ${(zoom * 100f).roundToInt()}%")
            CockpitStageBadge("pan ${panX.roundToInt()}, ${panY.roundToInt()}")
        }
    }
}


@Composable
private fun CockpitStageBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .zIndex(30f)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xDD020B12))
            .border(BorderStroke(1.dp, Color(0xFF55C7FF)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFFD8E5F2),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun CockpitExpandableGroup(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            onClick = onToggle,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (expanded) Color(0xFF55C7FF) else Color(0xFF23607B)),
            colors = CardDefaults.cardColors(
                containerColor = if (expanded) Color(0xFF0B344C) else Color(0xFF061F31)
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFFC7D7E6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                CockpitInfoChip(if (expanded) "Hide" else "Open")
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun CockpitDetailPanel(
    variant: CockpitSpriteVariant,
    selectedTarget: CockpitHitboxTarget,
    asset: CockpitSpriteAsset,
) {
        CockpitPanelCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitInfoChip(variant.label)
            CockpitInfoChip("Personalized DHC-6")
            }
        Spacer(Modifier.height(16.dp))
        Text(
            selectedTarget.title,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            selectedTarget.area,
            color = Color(0xFF55C7FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(18.dp))
        CockpitMiniBlock("Training purpose", selectedTarget.purpose)
        Spacer(Modifier.height(12.dp))
        CockpitMiniBlock(
            title = "Hitbox overlay",
            body = "Selected target highlight is active. ${DesktopBuildInfo.VersionName} renders a simulator-style cockpit scene and maps each selected system to the live panel."
        )
        Spacer(Modifier.height(12.dp))
        CockpitMiniBlock(
            title = "FlightGear aircraft asset",
            body = "$PersonalizedDhc6AircraftRootPath contains the independent desktop DHC-6 aircraft, including Wheels, Floats and Skis variants, cockpit models, systems, sounds and textures."
        )
    }
}

@Composable
private fun CockpitTargetCard(
    target: CockpitHitboxTarget,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(0xFF55C7FF) else Color(0xFF23607B)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF155D7A) else Color(0xFF061F31)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitInfoChip("Both variants")
                CockpitInfoChip(target.area)
            }
            Spacer(Modifier.height(9.dp))
            Text(target.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(target.purpose, color = Color(0xFFD8E5F2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CockpitPanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF23607B)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF061F31)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(22.dp),
            content = content,
        )
    }
}

@Composable
private fun CockpitMiniBlock(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF23607B)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF08283D)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(body, color = Color(0xFFD8E5F2), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CockpitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = FontWeight.Black,
                color = if (selected) Color.White else Color(0xFF55C7FF),
                maxLines = 1,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF2D9FE0),
            containerColor = Color(0xFF061F31),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color(0xFF23607B),
            selectedBorderColor = Color(0xFF55C7FF),
        ),
    )
}

@Composable
private fun CockpitInfoChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF08283D))
            .border(BorderStroke(1.dp, Color(0xFF23607B)), RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp)
            .widthIn(min = 0.dp),
    ) {
        Text(
            label,
            color = Color(0xFF55C7FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}
