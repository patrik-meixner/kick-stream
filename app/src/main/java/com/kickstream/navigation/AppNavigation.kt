package com.kickstream.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kickstream.ui.home.HomeScreen
import com.kickstream.ui.login.LoginScreen
import com.kickstream.ui.player.PlayerScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val PLAYER = "player/{channelSlug}"

    fun player(channelSlug: String) = "player/$channelSlug"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onChannelSelected = { slug ->
                    navController.navigate(Routes.player(slug))
                },
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("channelSlug") { type = NavType.StringType }),
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("channelSlug") ?: return@composable
            PlayerScreen(
                channelSlug = slug,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
