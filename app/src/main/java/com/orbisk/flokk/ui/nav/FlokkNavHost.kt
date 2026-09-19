package com.orbisk.flokk.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.orbisk.flokk.ui.connect.ConnectionFormScreen
import com.orbisk.flokk.ui.connections.ConnectionsScreen
import com.orbisk.flokk.ui.workspace.WorkspaceScreen

private const val ROUTE_CONNECTIONS = "connections"
private const val ROUTE_CONNECT_FORM = "connect/{profileId}"
private const val ROUTE_WORKSPACE = "workspace"
private const val NEW_PROFILE_SENTINEL = "new"

@Composable
fun FlokkNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ROUTE_CONNECTIONS,
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
    ) {
        composable(ROUTE_CONNECTIONS) {
            ConnectionsScreen(
                // Kept on the back stack, so the workspace screen's back arrow returns here.
                onOpenWorkspaces = { navController.navigate(ROUTE_WORKSPACE) { launchSingleTop = true } },
                onAddConnection = { navController.navigate("connect/$NEW_PROFILE_SENTINEL") },
                onEditConnection = { profileId -> navController.navigate("connect/$profileId") },
            )
        }
        composable(
            route = ROUTE_CONNECT_FORM,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            ConnectionFormScreen(
                profileId = profileId?.takeIf { it != NEW_PROFILE_SENTINEL },
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(ROUTE_WORKSPACE) {
            WorkspaceScreen(onBack = { navController.popBackStack() })
        }
    }
}
