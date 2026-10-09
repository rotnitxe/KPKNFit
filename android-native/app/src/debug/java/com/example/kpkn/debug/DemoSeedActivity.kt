package com.example.kpkn.debug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * SOLO DEPURACIÓN. Siembra los datos de demostración de la presentación (Alex, entrenos, comidas, RINGS y recorridos
 * vistos) y abre Inicio. Requisito: la persona QA ya está activada por el arnés del alta (`WizardHarnessActivity` con
 * `persona=qa` y `activate=1500`). Es idempotente: se puede lanzar otra vez sin duplicar nada.
 */
class DemoSeedActivity : ComponentActivity() {

    private var status by mutableStateOf("Preparando los datos de demostración…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoSeedScreen(status) }
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { DemoDataSeeder.seed(application) }) {
                is DemoSeedResult.Done -> {
                    Log.i("DemoSeed", result.summary)
                    startActivity(Intent(this@DemoSeedActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
                is DemoSeedResult.Missing -> status = result.reason
            }
        }
    }
}

@Composable
private fun DemoSeedScreen(message: String) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(message, color = Color(0xFFF2EEE6), modifier = Modifier.padding(24.dp))
    }
}
