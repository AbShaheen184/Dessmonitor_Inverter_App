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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import androidx.compose.runtime.livedata.observeAsState
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import com.dessmonitor.smartess.ui.components.FloatingNavigationBar
import com.dessmonitor.smartess.ui.components.GlassSurface
import com.dessmonitor.smartess.ui.components.NavigationItem
import kotlinx.coroutines.launch

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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "DessMonitor",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                NavigationDrawerItem(
                    label = { Text("Inverter Parameters") },
                    selected = false,
                    icon = { Icon(Icons.Default.SettingsInputComponent, null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Themes") },
                    selected = false,
                    icon = { Icon(Icons.Default.Palette, null) },
                    onClick = { /* TODO: Implement */ },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("App Settings") },
                    selected = false,
                    icon = { Icon(Icons.Default.AppSettingsAlt, null) },
                    onClick = { /* TODO: Implement */ },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "v2.2.0",
                    modifier = Modifier.padding(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    ) {
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
                        },
                        onMenuClick = { scope.launch { drawerState.open() } }
                    ) 
                }
                composable(Screen.Analysis.route) { 
                    AnalysisScreen(
                        repository = repository,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    ) 
                }
                composable(Screen.Trends.route) { 
                    TrendsScreen(
                        repository = repository,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    ) 
                }
                composable(Screen.History.route) { 
                    HistoryScreen(
                        repository = repository,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    ) 
                }
                composable(Screen.Alarms.route) { 
                    AlarmsScreen(
                        repository = repository,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    ) 
                }
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
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            FloatingNavigationBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                items = items.map { NavigationItem(it.route, it.title, it.icon) },
                currentRoute = currentRoute,
                onItemClick = { route ->
                    navController.navigate(route) {
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
