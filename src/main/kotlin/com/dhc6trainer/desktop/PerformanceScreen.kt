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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
internal fun PerformanceScreen(assetSnapshot: DesktopAssetCatalogSnapshot) {
    var tab by remember { mutableStateOf("TAKEOFF") }
    var elevation by remember { mutableStateOf("500") }
    var oat by remember { mutableStateOf("15") }
    var weight by remember { mutableStateOf("10000") }
    var wind by remember { mutableStateOf("0") }
    var windDir by remember { mutableStateOf("Headwind") }
    var surface by remember { mutableStateOf("PAVED DRY") }
    var flaps by remember { mutableStateOf("10") }

    val elevInt = elevation.toIntOrNull() ?: 0
    val oatInt = oat.toIntOrNull() ?: 15
    val weightInt = weight.toIntOrNull() ?: 10000
    val windInt = wind.toIntOrNull() ?: 0
    val windKt = when (windDir) {
        "Headwind" -> windInt; "Tailwind" -> -windInt; else -> 0
    }

    val toResult = remember(elevInt, oatInt, weightInt, windKt, surface) {
        PerformanceEngine.computeTakeoff(elevInt, oatInt, weightInt, windKt, surface)
    }
    val ldgResult = remember(elevInt, oatInt, weightInt, windKt, flaps) {
        PerformanceEngine.computeLanding(elevInt, oatInt, weightInt, windKt, flaps)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

        // Section
        DetailCard(Modifier.weight(0.95f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item {
                    // Tab bar: TO / CLB / LDG
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("TAKEOFF", "CLIMB", "LANDING").forEach { t ->
                            val sel = t == tab
                            val shape = when (t) {
                                "TAKEOFF" -> RoundedCornerShape(
                                    topStart = 14.dp,
                                    bottomStart = 14.dp,
                                    topEnd = 0.dp,
                                    bottomEnd = 0.dp
                                )

                                "LANDING" -> RoundedCornerShape(
                                    topStart = 0.dp,
                                    bottomStart = 0.dp,
                                    topEnd = 14.dp,
                                    bottomEnd = 14.dp
                                )

                                else -> RoundedCornerShape(0.dp)
                            }
                            Box(
                                modifier = Modifier.weight(1f)
                                    .background(
                                        if (sel) Dhc6DesktopColors.AccentStrong else Dhc6DesktopColors.SurfaceDark,
                                        shape
                                    )
                                    .clickable { tab = t }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (t == "TAKEOFF") "TO" else if (t == "CLIMB") "CLB" else "LDG",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        when (tab) {
                            "TAKEOFF" -> "TAKEOFF DISTANCE"
                            "CLIMB" -> "CLIMB PLANNING"
                            else -> "LANDING DISTANCE"
                        },
                        color = Dhc6DesktopColors.Accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                    )
                }
                item {
                    OutlinedTextField(
                        elevation,
                        { elevation = it },
                        label = { Text("Airport Elevation") },
                        suffix = { Text("FT") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        oat,
                        { oat = it },
                        label = { Text("OAT") },
                        suffix = { Text("C") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        weight,
                        { weight = it },
                        label = { Text("Weight") },
                        suffix = { Text("LB") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            wind,
                            { wind = it },
                            label = { Text("Wind") },
                            suffix = { Text("KT") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Direction",
                                color = Dhc6DesktopColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            listOf("Headwind", "Tailwind", "Calm").forEach { dir ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { windDir = dir }) {
                                    Box(
                                        Modifier.size(14.dp).background(
                                            if (windDir == dir) Dhc6DesktopColors.Accent else Dhc6DesktopColors.Border,
                                            RoundedCornerShape(50)
                                        )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        dir,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    val surfaces = listOf("PAVED DRY", "WET", "GRASS", "GRAVEL")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Surface", color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            surfaces.forEach { sf ->
                                Card(
                                    modifier = Modifier.clickable { surface = sf },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (surface == sf) Dhc6DesktopColors.Accent else Dhc6DesktopColors.Border
                                    ),
                                    colors = CardDefaults.cardColors(containerColor = if (surface == sf) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
                                ) {
                                    Text(
                                        if (sf == "PAVED DRY") "PAVED" else sf,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 6.dp
                                        ),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }
                }
                if (tab != "TAKEOFF") item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Landing flaps", color = Dhc6DesktopColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("37.5", "20", "10").forEach { f ->
                                Card(
                                    modifier = Modifier.clickable { flaps = f },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (flaps == f) Dhc6DesktopColors.Accent else Dhc6DesktopColors.Border
                                    ),
                                    colors = CardDefaults.cardColors(containerColor = if (flaps == f) Dhc6DesktopColors.CardSelected else Dhc6DesktopColors.SurfaceDark),
                                ) {
                                    Text(
                                        f,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section
        DetailCard(Modifier.weight(0.95f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                if (tab == "TAKEOFF") {
                    item {
                        Text(
                            "Takeoff",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Both engines operating, paved dry unless noted.",
                            color = Dhc6DesktopColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PerfResultCard(
                                "ROLL",
                                "${"%,d".format(toResult.groundRollFt)} FT",
                                Modifier.weight(1f)
                            )
                            PerfResultCard(
                                "50 FT",
                                "${"%,d".format(toResult.distanceTo50Ft)} FT",
                                Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PerfResultCard("VLOF/V1", "${toResult.vLofKt} KT", Modifier.weight(1f))
                            PerfResultCard("V2", "${toResult.v2Kt} KT", Modifier.weight(1f))
                        }
                    }
                }
                if (tab == "CLIMB") {
                    item {
                        Text(
                            "Climb Planning",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Climb calculations are intentionally gated until AFM-backed tables are wired into the desktop model.",
                            color = Dhc6DesktopColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp,
                        )
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PerfResultCard("ELEV", "${"%,d".format(elevInt)} FT", Modifier.weight(1f))
                            PerfResultCard("OAT", "$oatInt C", Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PerfResultCard("WEIGHT", "${"%,d".format(weightInt)} LB", Modifier.weight(1f))
                            PerfResultCard("WIND", "${kotlin.math.abs(windKt)} KT ${if (windKt < 0) "TAIL" else if (windKt > 0) "HEAD" else "CALM"}", Modifier.weight(1f))
                        }
                    }
                }
                if (tab == "LANDING") {
                    item {
                        Text(
                            "Landing",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PerfResultCard(
                                "LDG 50 FT",
                                "${"%,d".format(ldgResult.distanceFrom50Ft)} FT",
                                Modifier.weight(1f)
                            )
                            PerfResultCard("VREF", "${ldgResult.vRefKt} KT", Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Dhc6DesktopColors.Gold.copy(alpha = 0.4f)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1500)),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "CAUTION",
                                color = Dhc6DesktopColors.Gold,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                            Text(
                                "Performance data is for planning purposes only.\nRefer to AFM Section 5 for limitations and details.",
                                color = Dhc6DesktopColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Text(
                                "Desktop asset set: ${assetSnapshot.allResourcePaths.size} indexed files",
                                color = Dhc6DesktopColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun PerfResultCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.BorderBright),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.CardSelected),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                label,
                color = Dhc6DesktopColors.TextSecondary,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
            )
        }
    }
}
