package com.dessmonitor.smartess.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import androidx.compose.runtime.livedata.observeAsState
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import com.dessmonitor.smartess.ui.components.GlassSurface

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Analysis : Screen("analysis", "Analysis", Icons.Default.Timeline)
    object Trends : Screen("trends", "Trends", Icons.Default.Assessment)
    object History : Screen("history", "History", Icons.Default.History)
    object Alarms : Screen("alarms", "Alarms", Icons.Default.Notifications)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: DeviceRepository) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Home,
        Screen.Analysis,
        Screen.Trends,
        Screen.History,
        Screen.Alarms
    )
    
    val routeToIndex = mapOf(
        Screen.Home.route to 0,
        Screen.Analysis.route to 1,
        Screen.Trends.route to 2,
        Screen.History.route to 3,
        Screen.Alarms.route to 4
    )

    val devices by repository.devices.observeAsState(emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    )
                )
            )
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val initialIndex = routeToIndex[initialState.destination.route] ?: 0
                val targetIndex = routeToIndex[targetState.destination.route] ?: 0
                if (targetIndex > initialIndex) {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
                } else {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
                }
            },
            exitTransition = {
                val initialIndex = routeToIndex[initialState.destination.route] ?: 0
                val targetIndex = routeToIndex[targetState.destination.route] ?: 0
                if (targetIndex > initialIndex) {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
                } else {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
                }
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
            }
        ) {
            composable(Screen.Home.route) { 
                InverterHomeScreen(
                    repository = repository,
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onTrendsClick = { sensor: String ->
                        repository.showSensorInTrends(sensor)
                        navController.navigate(Screen.Trends.route)
                    }
                ) 
            }
            composable(Screen.Analysis.route) { AnalysisScreen(repository) }
            composable(Screen.Trends.route) { TrendsScreen(repository) }
            composable(Screen.History.route) { HistoryScreen(repository) }
            composable(Screen.Alarms.route) { AlarmsScreen(repository) }
            composable(Screen.Settings.route) {
                val device = devices.firstOrNull()
                if (device != null) {
                    SettingsScreen(
                        repository = repository,
                        device = device,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("No device available")
                    }
                }
            }
        }

        // Floating Glass Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            GlassSurface(
                cornerRadius = 32.dp,
                containerColor = Color.Black.copy(alpha = 0.85f)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.height(72.dp),
                    windowInsets = WindowInsets(0.dp)
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                            selected = currentRoute == screen.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                selectedTextColor = Color.White,
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
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
        }
    }
}
