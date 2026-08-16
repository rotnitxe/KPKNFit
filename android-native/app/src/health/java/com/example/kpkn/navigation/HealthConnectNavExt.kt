package com.example.kpkn.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.kpkn.features.healthconnect.HealthConnectRepository
import com.example.kpkn.features.healthconnect.HealthConnectScreen
import com.example.kpkn.navigation.KpknRoute

fun NavGraphBuilder.addHealthConnectRoute(navController: NavHostController) {
    composable(KpknRoute.HealthConnect.route) {
        val context = LocalContext.current
        val healthConnectRepo = remember {
            HealthConnectRepository.getInstance(context)
        }
        HealthConnectScreen(
            onBack = { navController.popBackStack() },
            onRequestPermissions = {
                val intent = healthConnectRepo.createPermissionRequestIntent()
                context.startActivity(intent)
            }
        )
    }
}

fun healthConnectRouteAvailable(): Boolean = true
