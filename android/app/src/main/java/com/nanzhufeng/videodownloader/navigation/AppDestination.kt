package com.nanzhufeng.videodownloader.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
) {
    HOME("home", "主页", Icons.Outlined.Home, "nav-home"),
    HISTORY("history", "历史", Icons.Outlined.History, "nav-history"),
    SETTINGS("settings", "设置", Icons.Outlined.Settings, "nav-settings"),
}
