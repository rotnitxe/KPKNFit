package com.example.kpkn.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

/** Keep old links usable after retiring the provider; retained body measurements remain accessible. */
fun NavGraphBuilder.addHealthConnectRoute(navController: NavHostController) {
    composable(KpknRoute.HealthConnect.route) {
        LaunchedEffect(Unit) {
            navController.navigate(KpknRoute.BodyProgress.route) {
                popUpTo(KpknRoute.HealthConnect.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
