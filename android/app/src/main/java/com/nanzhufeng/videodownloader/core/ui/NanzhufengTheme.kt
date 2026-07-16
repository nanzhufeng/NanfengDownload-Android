package com.nanzhufeng.videodownloader.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val PrussianBlue = Color(0xFF0D3A69)
val HermesOrange = Color(0xFFEB5C20)
val MarsGreen = Color(0xFF018B8D)
val WorkspaceBackground = Color(0xFFF4F7FB)
val WaitingYellow = Color(0xFFF9D46C)
val FailureRed = Color(0xFFC8161D)
val SuccessGreen = Color(0xFF168447)

private val NanzhufengColors = lightColorScheme(
    primary = PrussianBlue,
    onPrimary = Color.White,
    secondary = HermesOrange,
    onSecondary = Color.White,
    tertiary = MarsGreen,
    onTertiary = Color.White,
    background = WorkspaceBackground,
    onBackground = Color(0xFF101828),
    surface = Color.White,
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFEAF0F7),
    onSurfaceVariant = Color(0xFF475467),
    error = FailureRed,
)

@Composable
fun NanzhufengTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NanzhufengColors,
        typography = Typography(),
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(8.dp),
            extraLarge = RoundedCornerShape(8.dp),
        ),
        content = content,
    )
}
