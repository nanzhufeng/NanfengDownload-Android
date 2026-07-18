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

val MintWorkspace = Color(0xFFE6EAE7)
val ForestGreen = Color(0xFF168A4A)
val SelectedSage = Color(0xFFE5F4EA)
val WarmOrange = Color(0xFFFF5A1F)
val QualityPurple = Color(0xFF7552B8)
val StorageOchre = Color(0xFFB96812)
val WorkbenchBorder = Color(0xFFD7DED9)
val SecondaryText = Color(0xFF5F6C64)

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
    onBackground = Color(0xFF16231B),
    surface = Color.White,
    onSurface = Color(0xFF16231B),
    surfaceVariant = SelectedSage,
    onSurfaceVariant = SecondaryText,
    error = FailureRed,
)

private val NanzhufengTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
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
