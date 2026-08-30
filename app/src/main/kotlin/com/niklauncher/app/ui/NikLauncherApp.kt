package com.niklauncher.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.niklauncher.app.R
import com.niklauncher.app.ui.screen.InstancesScreen
import com.niklauncher.app.ui.screen.SettingsScreen
import com.niklauncher.app.ui.screen.VersionsScreen

private enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    INSTANCES("instances", R.string.nav_instances, Icons.Filled.Widgets),
    VERSIONS("versions", R.string.nav_versions, Icons.Filled.ViewList),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun NikLauncherApp(viewModel: LauncherViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Keep a single copy of each tab and restore its
                                // scroll position, so switching tabs is cheap.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.INSTANCES.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.INSTANCES.route) { InstancesScreen(viewModel) }
            composable(Destination.VERSIONS.route) { VersionsScreen(viewModel) }
            composable(Destination.SETTINGS.route) { SettingsScreen(viewModel) }
        }
    }
}
