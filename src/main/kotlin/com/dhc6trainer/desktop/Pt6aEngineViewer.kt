package com.dhc6trainer.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Interactive PT6A-27 free-turbine turboprop engine cross-section viewer.
 *
 * Renders a proportionally sized, color-coded section diagram that the user
 * clicks to explore. Each section's description is grounded in the FCTM
 * (HBTS-001) and Technical DHC-6 aircraft information chapter.
 *
 * Built with standard Compose layout (Box/Row) — no Canvas, no 3D library,
 * no external dependencies.
 */

private data class EngineSection(
    val name: String,
    val tag: String,
    val color: Color,
    val weight: Float,    // proportional width in the diagram row
    val heightFrac: Float, // fraction of max section height (1.0 = tallest)
    val desc: String,
)

private val SECTIONS = listOf(
    EngineSection(
        name = "Propeller shaft",
        tag = "Output",
        color = Color(0xFF667788),
        weight = 0.14f,
        heightFrac = 0.15f,
        desc = "Output shaft from the two-stage reduction gearbox to the constant-speed propeller. " +
            "The prop lever controls feather, normal flight, and reverse range via the propeller " +
            "governor and beta valve. Propeller speed is measured as NP.",
    ),
    EngineSection(
        name = "Reduction gearbox",
        tag = "Gearbox",
        color = Color(0xFF1A6E7A),
        weight = 0.12f,
        heightFrac = 0.72f,
        desc = "Two-stage planetary reduction gearbox. Reduces the free power turbine output to " +
            "propeller speed (ratio approximately 15:1). Houses the propeller brake, tachometer " +
            "generator, and the torque sensor (torquemeter) that drives the torque pressure indicator.",
    ),
    EngineSection(
        name = "Axial compressor (3 stages)",
        tag = "Compressor",
        color = Color(0xFF2288CC),
        weight = 0.16f,
        heightFrac = 0.60f,
        desc = "Three axial compressor stages. Air enters at the rear of the engine and flows " +
            "forward through the axial stages before reaching the centrifugal impeller. " +
            "Combined with the centrifugal stage, the overall compression ratio is approximately 6.3:1.",
    ),
    EngineSection(
        name = "Centrifugal compressor",
        tag = "Compressor",
        color = Color(0xFF44AAEE),
        weight = 0.10f,
        heightFrac = 0.68f,
        desc = "Single centrifugal compressor stage. Receives air from the axial stages and " +
            "accelerates it radially outward into the diffuser, then into the reverse-flow " +
            "annular combustion chamber. The compressor section is driven by the compressor turbine.",
    ),
    EngineSection(
        name = "Combustion chamber",
        tag = "Combustion",
        color = Color(0xFFDD7722),
        weight = 0.16f,
        heightFrac = 1.0f,
        desc = "Reverse-flow annular combustion chamber with 14 fuel nozzles. Hot gases reverse " +
            "direction (flow rearward) to reach the turbine stages. This reverse-flow layout keeps " +
            "the engine compact and places the propeller shaft at the front, shortening the drive train.",
    ),
    EngineSection(
        name = "Compressor turbine (CT)",
        tag = "Gas generator",
        color = Color(0xFF33AA55),
        weight = 0.09f,
        heightFrac = 0.58f,
        desc = "Single-stage compressor turbine. Extracts energy from the hot gas to drive the " +
            "compressor and engine accessories via the gas generator (NG) shaft. NG speed is the " +
            "primary engine power reference and is limited to 101.5% for take-off and max continuous.",
    ),
    EngineSection(
        name = "Power turbine (PT)",
        tag = "Free turbine",
        color = Color(0xFF88CC33),
        weight = 0.09f,
        heightFrac = 0.52f,
        desc = "Single-stage free power turbine. Mechanically independent of the gas generator " +
            "section \u2014 the 'free' in free turbine. Drives the propeller through the reduction " +
            "gearbox via a separate concentric shaft. Speed measured as NP. Max NP: 96% (red radial).",
    ),
    EngineSection(
        name = "Exhaust section",
        tag = "Exhaust",
        color = Color(0xFF8899AA),
        weight = 0.14f,
        heightFrac = 0.42f,
        desc = "Exhaust gases exit rearward after passing through the power turbine. The exhaust " +
            "stack incorporates the T5 (ITT) thermocouple ring for inter-turbine temperature " +
            "measurement. T5 is limited to 725\u00B0C for take-off and max continuous operation.",
    ),
)

private val MaxSectionHeight = 160.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Pt6aEngineViewer() {
    var selected by remember { mutableIntStateOf(-1) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Engine diagram card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF010E18)),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "PT6A-27 CROSS-SECTION",
                    color = Dhc6DesktopColors.Accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                )
                Text(
                    "\u2190 FRONT (propeller)          REAR (intake) \u2192",
                    color = Dhc6DesktopColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Section row
                Box(
                    modifier = Modifier.fillMaxWidth().height(MaxSectionHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    // Shaft line (behind sections)
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        thickness = 4.dp,
                        color = Color(0xFF334455),
                    )

                    // Sections
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SECTIONS.forEachIndexed { i, sec ->
                            val isSelected = selected == i
                            val borderColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else Color.Transparent,
                                animationSpec = tween(200),
                                label = "border",
                            )
                            val sectionHeight = MaxSectionHeight * sec.heightFrac

                            Box(
                                modifier = Modifier
                                    .weight(sec.weight)
                                    .height(sectionHeight)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        // Machined-metal shading: highlight band, body colour, shadowed base
                                        Brush.verticalGradient(
                                            0.00f to lerp(sec.color, Color.White, 0.35f),
                                            0.18f to lerp(sec.color, Color.White, 0.10f),
                                            0.55f to sec.color,
                                            1.00f to lerp(sec.color, Color.Black, 0.45f),
                                        ),
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, borderColor, RoundedCornerShape(8.dp))
                                        else Modifier,
                                    )
                                    .clickable { selected = if (selected == i) -1 else i },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    sec.name.split(" ").first(),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Legend
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SECTIONS.forEachIndexed { i, sec ->
                        val isSelected = selected == i
                        val bg = sec.color.copy(alpha = if (isSelected) 0.35f else 0.15f)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(bg)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, Color.White, RoundedCornerShape(999.dp))
                                    else Modifier,
                                )
                                .clickable { selected = if (selected == i) -1 else i }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(sec.name, color = sec.color, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Info panel
        val info = if (selected in SECTIONS.indices) SECTIONS[selected] else null
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (info != null) Dhc6DesktopColors.SurfaceDark else Dhc6DesktopColors.Overlay,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                if (info != null) {
                    Text(
                        info.tag.uppercase(),
                        color = info.color,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                    )
                    Text(
                        info.name,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        info.desc,
                        color = Dhc6DesktopColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                    )
                } else {
                    Text(
                        "PT6A-27 overview",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The PT6A-27 is a free-turbine, reverse-flow turboprop producing 620 SHP " +
                            "(flat-rated). Air enters at the rear, flows forward through a three-stage " +
                            "axial plus one centrifugal compressor, reverses through an annular combustion " +
                            "chamber, then passes rearward through the compressor turbine and power turbine. " +
                            "The power turbine drives the propeller through a two-stage reduction gearbox " +
                            "at the front. Click any section above to explore it.",
                        color = Dhc6DesktopColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}
