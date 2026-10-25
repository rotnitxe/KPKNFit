package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.domain.equipment.EquipmentInventoryEditor
import com.example.kpkn.domain.equipment.EquipmentInventoryTemplate
import com.example.kpkn.domain.equipment.EquipmentMachineConfiguration
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.settings.equipment.EquipmentInventoryRoute
import com.example.kpkn.screens.settings.equipment.EquipmentInventoryStore
import com.example.kpkn.screens.settings.equipment.EquipmentInventoryViewModel
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme

/** Vista previa aislada: state=empty|commercial|error, fontScale, reducedMotion y widthDp. */
class EquipmentHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val commercial = EquipmentInventoryEditor.validate(EquipmentInventoryEditor.template(EquipmentInventoryTemplate.COMMERCIAL)).inventory!!
        val initial = when (intent.getStringExtra("state")) {
            "commercial" -> commercial
            "error" -> commercial.copy(barbellWeightKg = -5.0)
            else -> EquipmentInventory()
        }
        val store = MemoryEquipmentStore(initial)
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        setContent {
            KPKNTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides reduced) {
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize()) {
                            val model: EquipmentInventoryViewModel = viewModel {
                                EquipmentInventoryViewModel(store, loadConfigurations = {
                                    listOf(EquipmentMachineConfiguration("leg_press__machine", "Prensa · Máquina", "machine"))
                                })
                            }
                            EquipmentInventoryRoute(onBack = ::finish, viewModel = model)
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
}

private class MemoryEquipmentStore(initial: EquipmentInventory) : EquipmentInventoryStore {
    private var inventory = initial
    override suspend fun load(): EquipmentInventory = inventory
    override suspend fun save(inventory: EquipmentInventory) { this.inventory = inventory }
}
