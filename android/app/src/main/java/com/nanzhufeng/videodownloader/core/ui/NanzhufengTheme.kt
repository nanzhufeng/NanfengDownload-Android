package com.nanzhufeng.videodownloader.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val MintWorkspace = Color(0xFFF1F8F4)
val ForestGreen = Color(0xFF1E6A45)
val SelectedSage = Color(0xFFDCEFE3)
val WarmOrange = Color(0xFFE86E2F)
val QualityPurple = Color(0xFF7250B5)
val StorageOchre = Color(0xFFB36A16)

val PrussianBlue = ForestGreen
val HermesOrange = WarmOrange
val MarsGreen = QualityPurple
val WorkspaceBackground = MintWorkspace
val WaitingYellow = StorageOchre
val FailureRed = Color(0xFFC8161D)
val SuccessGreen = ForestGreen

private val NanzhufengColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    secondary = WarmOrange,
    onSecondary = Color.White,
    tertiary = QualityPurple,
    onTertiary = Color.White,
    background = MintWorkspace,
    onBackground = Color(0xFF101828),
    surface = Color.White,
    onSurface = Color(0xFF101828),
    surfaceVariant = SelectedSage,
    onSurfaceVariant = ForestGreen,
    error = FailureRed,
)

private val NanzhufengTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun NanzhufengTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NanzhufengColors,
        typography = NanzhufengTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
