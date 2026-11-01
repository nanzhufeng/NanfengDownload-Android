package com.nanzhufeng.videodownloader.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        modifier = modifier.shadow(
            elevation = 6.dp,
            shape = MaterialTheme.shapes.medium,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.04f),
            spotColor = Color.Black.copy(alpha = 0.05f),
        ),
        color = tone.containerColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Shared centered title, with symmetric space for optional brand artwork. */
@Composable
fun WorkbenchPageHeader(title: String, expanded: Boolean, leading: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            style = if (expanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (leading != null) Box(Modifier.align(Alignment.CenterStart).size(48.dp), contentAlignment = Alignment.Center) { leading() }
    }
}

/** Filled fields establish hierarchy through surface and focus color rather than a hard frame. */
@Composable
fun workbenchFieldColors(container: Color = FieldSurface): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = FieldSurface,
    unfocusedContainerColor = container,
    disabledContainerColor = FieldSurface,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = ForestGreen,
)

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
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let { image -> { Icon(image, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = ForestGreen,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
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
        PlatformIconTreatment.PORNHUB_ORANGE -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("PH", color = Color(0xFFE58A00), style = MaterialTheme.typography.labelSmall)
        }
        PlatformIconTreatment.GENERIC_WEB -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("WEB", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
        }
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
        PlatformIconTreatment.BILIBILI_BLUE -> PlatformImage(
            resource = R.drawable.platform_bilibili,
            tint = Color(0xFF00AEEC),
            contentDescription = contentDescription,
            modifier = modifier,
        )
        PlatformIconTreatment.XIAOHONGSHU_RED -> PlatformImage(
            resource = R.drawable.platform_xiaohongshu,
            tint = Color(0xFFFF2442),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

internal enum class PlatformIconTreatment {
    YOUTUBE_RED,
    DOUYIN_LAYERED,
    TIKTOK_MONOCHROME,
    BILIBILI_BLUE,
    XIAOHONGSHU_RED,
    PORNHUB_ORANGE,
    GENERIC_WEB,
}

internal fun DownloadPlatform.iconTreatment(): PlatformIconTreatment = when (this) {
    DownloadPlatform.YOUTUBE -> PlatformIconTreatment.YOUTUBE_RED
    DownloadPlatform.DOUYIN -> PlatformIconTreatment.DOUYIN_LAYERED
    DownloadPlatform.TIKTOK -> PlatformIconTreatment.TIKTOK_MONOCHROME
    DownloadPlatform.BILIBILI -> PlatformIconTreatment.BILIBILI_BLUE
    DownloadPlatform.XIAOHONGSHU -> PlatformIconTreatment.XIAOHONGSHU_RED
    DownloadPlatform.PORNHUB -> PlatformIconTreatment.PORNHUB_ORANGE
    DownloadPlatform.GENERIC -> PlatformIconTreatment.GENERIC_WEB
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
