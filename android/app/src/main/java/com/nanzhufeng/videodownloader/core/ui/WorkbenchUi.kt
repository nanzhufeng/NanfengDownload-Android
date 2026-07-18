package com.nanzhufeng.videodownloader.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.R
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
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, tone.borderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
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
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = ForestGreen,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
fun PlatformIcon(
    platform: DownloadPlatform,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    when (platform.iconTreatment()) {
        PlatformIconTreatment.YOUTUBE_RED -> PlatformImage(
            resource = R.drawable.platform_youtube,
            tint = Color(0xFFFF0000),
            contentDescription = contentDescription,
            modifier = modifier,
        )
        PlatformIconTreatment.DOUYIN_LAYERED -> DouyinPlatformMark(contentDescription, modifier)
        PlatformIconTreatment.TIKTOK_MONOCHROME -> PlatformImage(
            resource = R.drawable.platform_tiktok,
            tint = Color(0xFF161823),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

internal enum class PlatformIconTreatment {
    YOUTUBE_RED,
    DOUYIN_LAYERED,
    TIKTOK_MONOCHROME,
}

internal fun DownloadPlatform.iconTreatment(): PlatformIconTreatment = when (this) {
    DownloadPlatform.YOUTUBE -> PlatformIconTreatment.YOUTUBE_RED
    DownloadPlatform.DOUYIN -> PlatformIconTreatment.DOUYIN_LAYERED
    DownloadPlatform.TIKTOK -> PlatformIconTreatment.TIKTOK_MONOCHROME
}

@Composable
private fun PlatformImage(
    resource: Int,
    tint: Color,
    contentDescription: String,
    modifier: Modifier,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(24.dp),
    )
}

@Composable
private fun DouyinPlatformMark(contentDescription: String, modifier: Modifier) {
    Surface(
        modifier = modifier.size(PlatformMarkSize).clip(RoundedCornerShape(6.dp)),
        color = Color(0xFF10111A),
        shape = RoundedCornerShape(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LayeredDouyinGlyph(Color(0xFF25F4EE), (-1.2).dp, null)
            LayeredDouyinGlyph(Color(0xFFFE2C55), 1.2.dp, null)
            LayeredDouyinGlyph(Color.White, 0.dp, contentDescription)
        }
    }
}

@Composable
private fun LayeredDouyinGlyph(tint: Color, xOffset: Dp, contentDescription: String?) {
    Image(
        painter = painterResource(R.drawable.platform_tiktok),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(DouyinGlyphSize).offset(x = xOffset),
    )
}

internal val PlatformMarkSize = 24.dp
internal val DouyinGlyphSize = 12.dp

internal fun AppCardTone.containerColor(): Color = Color.White

internal fun AppCardTone.borderColor(): Color = WorkbenchBorder
