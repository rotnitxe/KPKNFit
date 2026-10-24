package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Illustrative registered results, deliberately independent of user data or a readiness model. */
@Composable
internal fun WelcomeResultsScene(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ejemplo", color = Color(0xFF82D7AB), fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        Text("Carrera · 5 km", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))
        Text("Sesión anterior · 30 min", color = Color.White, fontSize = 18.sp)
        Text("Sesión reciente · 29 min", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Text("Resultados ilustrativos, no tus registros", color = Color.LightGray, fontSize = 14.sp)
    }
}
