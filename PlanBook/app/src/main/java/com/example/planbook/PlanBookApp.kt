package com.example.planbook

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.planbook.ui.screens.PlanBookScreen
import com.example.planbook.ui.screens.ReviewScreen
import com.example.planbook.ui.screens.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object PlanBook : Screen("planbook", "计划本", Icons.Default.CheckCircle)
    data object Review : Screen("review", "复盘", Icons.Default.CalendarMonth)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

@Composable
fun PlanBookApp() {
    val navController = rememberNavController()
    val items = listOf(Screen.PlanBook, Screen.Review, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.PlanBook.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.PlanBook.route) { PlanBookScreen() }
            composable(Screen.Review.route) { ReviewScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
