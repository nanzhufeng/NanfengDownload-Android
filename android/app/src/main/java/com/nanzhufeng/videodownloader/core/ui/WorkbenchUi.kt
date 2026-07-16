package com.nanzhufeng.videodownloader.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform

enum class AppCardTone {
    NEUTRAL,
    MINT,
    ORANGE,
    PURPLE,
    OCHRE,
}

@Composable
fun WorkbenchCard(
    tone: AppCardTone,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = tone.containerColor()),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(title: String, summary: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SelectedFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SelectedSage,
            selectedLabelColor = ForestGreen,
        ),
    )
}

@Composable
fun PlatformIcon(
    platform: DownloadPlatform,
    contentDescription: String,
) {
    Icon(
        imageVector = platform.icon(),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AppCardTone.containerColor(): Color = when (this) {
    AppCardTone.NEUTRAL -> MaterialTheme.colorScheme.surface
    AppCardTone.MINT -> MintWorkspace
    AppCardTone.ORANGE -> Color(0xFFFFEEE7)
    AppCardTone.PURPLE -> Color(0xFFF0EAFA)
    AppCardTone.OCHRE -> Color(0xFFFFF0DB)
}

private fun DownloadPlatform.icon(): ImageVector = when (this) {
    DownloadPlatform.YOUTUBE -> Icons.Filled.PlayCircle
    DownloadPlatform.DOUYIN -> Icons.Filled.VideoLibrary
    DownloadPlatform.TIKTOK -> Icons.Filled.SmartDisplay
}
