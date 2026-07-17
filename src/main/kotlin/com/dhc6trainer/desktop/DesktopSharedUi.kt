package com.dhc6trainer.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
internal fun DetailCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.Background),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterRow(
    allLabel: String,
    selectedAll: Boolean,
    onAll: () -> Unit,
    entries: List<Pair<String, Boolean>>,
    onEntry: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChipLike(allLabel, selectedAll, onAll)
        entries.forEachIndexed { index, entry ->
            FilterChipLike(entry.first, entry.second) { onEntry(index) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowLabels(labels: List<String>, emphasis: ProcedureCategory? = null) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        labels.forEachIndexed { index, label ->
            if (index == 0 && emphasis != null) CategoryPill(emphasis) else Badge(label, false)
        }
    }
}

@Composable
internal fun CategoryPill(category: ProcedureCategory) {
    val color = when (category) {
        ProcedureCategory.NORMAL -> Dhc6DesktopColors.Green
        ProcedureCategory.ABNORMAL -> Dhc6DesktopColors.Gold
        ProcedureCategory.EMERGENCY -> Dhc6DesktopColors.Red
    }
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Text(
            category.displayName.uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
internal fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            if (selected) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.Border
        ),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.AccentStrong else Dhc6DesktopColors.SurfaceDark),
    ) {
        Text(
            label,
            color = if (selected) Color.White else Dhc6DesktopColors.Accent,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun Badge(label: String, selected: Boolean) {
    Card(
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) Dhc6DesktopColors.BorderBright else Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = if (selected) Dhc6DesktopColors.Accent else Dhc6DesktopColors.SurfaceDark),
    ) {
        Text(
            label.cleanDisplay(),
            color = if (selected) Color.White else Dhc6DesktopColors.Accent,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun StatCard(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Dhc6DesktopColors.TextSecondary, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Text(caption, color = Dhc6DesktopColors.TextMuted, fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
internal fun QuickLaunchCard(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick).heightIn(min = 138.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(body, color = Dhc6DesktopColors.TextSecondary, lineHeight = 20.sp)
            }
            Text(action, color = Dhc6DesktopColors.Accent, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun InfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 125.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
            Text(body.cleanDisplay(), color = Dhc6DesktopColors.TextSecondary, lineHeight = 22.sp)
        }
    }
}

@Composable
internal fun EmptyState(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Dhc6DesktopColors.Border),
        colors = CardDefaults.cardColors(containerColor = Dhc6DesktopColors.SurfaceDark),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(body.cleanDisplay(), color = Dhc6DesktopColors.TextSecondary)
        }
    }
}
