package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.QuickEntryForm
import com.example.kpkn.screens.nutrition.reuse.CopyToSheet
import com.example.kpkn.screens.nutrition.reuse.QuickEntryOverlay
import com.example.kpkn.screens.nutrition.reuse.RepeatYesterdayDialog
import com.example.kpkn.screens.nutrition.reuse.TemplatesSheet
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

/**
 * SOLO DEPURACIÓN. Las pantallas de la reutilización del diario (N4) con DATOS EN MEMORIA: no escribe en la base real. Extras: `state` =
 * copy_sheet (por defecto) | repeat_yesterday | templates_empty | templates | quick_add | quick_add_error, más `fontScale`,
 * `reducedMotion` y `widthDp`. Las acciones (copiar, añadir, usar una plantilla, renombrar, borrar) corren la lógica real de `MealReuse`
 * sobre listas en memoria y dejan el resultado en el texto de arriba (etiqueta `harness-message`).
 * Lanzar: `--actions "launch:MealReuseHarnessActivity|--es|state|copy_sheet"`.
 */
class MealReuseHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val name = intent.getStringExtra("state") ?: "copy_sheet"
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        fun meal(id: String, date: LocalDate, type: MealType, vararg foods: LoggedFood) =
            NutritionLog(id = id, date = "${date}T12:00:00.000Z", mealType = type, foods = foods.toList())
        val breakfast = meal("demo-b", yesterday, MealType.BREAKFAST,
            LoggedFood(id = "d1", foodName = "Avena con leche", amount = 250.0, calories = 320.0, protein = 14.0, carbs = 48.0, fats = 8.0))
        val lunch = meal("demo-l", yesterday, MealType.LUNCH,
            LoggedFood(id = "d2", foodName = "Arroz con pollo", amount = 300.0, calories = 560.0, protein = 28.0, carbs = 70.0, fats = 12.0),
            LoggedFood(id = "d3", foodName = "Ensalada", amount = 120.0, calories = 60.0, protein = 2.0, carbs = 8.0, fats = 3.0))
        // «Base de datos» en memoria: lo que se copie se suma aquí y nada sale de esta actividad.
        val store = linkedMapOf<String, NutritionLog>().also { map -> listOf(breakfast, lunch).forEach { map[it.id] = it } }
        val todayMeal = meal("demo-hoy", today, MealType.SNACK, LoggedFood(id = "d4", foodName = "Manzana", amount = 180.0, calories = 95.0, carbs = 25.0))
        if (name == "repeat_yesterday") store[todayMeal.id] = todayMeal
        val seedTemplates = if (name == "templates") listOf(
            MealReuse.templateFromLog(lunch, "Almuerzo de la semana", "demo-t1", "2026-10-21T10:00:00Z")!!,
            MealReuse.templateFromLog(breakfast, "Desayuno de siempre", "demo-t2", "2026-10-20T10:00:00Z")!!,
        ) else emptyList()

        setContent {
            KPKNTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides reduced) {
                    var message by remember { mutableStateOf("Vista previa de N4 con datos en memoria.") }
                    var open by remember { mutableStateOf(true) }
                    var templates by remember { mutableStateOf<List<MealTemplate>>(seedTemplates) }
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = width.dp).fillMaxWidth().statusBarsPadding().padding(24.dp)) {
                            Text("Reutilización del diario · $name", style = KpknType.bodyStrong, color = WizardColors.text)
                            Spacer(Modifier.height(8.dp))
                            Text(message, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.padding(end = 8.dp))
                        }
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize()) {
                            if (open) when (name) {
                                "repeat_yesterday" -> {
                                    val decision = MealReuse.decideRepeat(store.values.toList(), today, today)
                                    val incoming = (decision as? MealReuse.RepeatDecision.NeedsConfirmation)?.plan?.copies?.size ?: 0
                                    val existing = (decision as? MealReuse.RepeatDecision.NeedsConfirmation)?.existingMeals ?: 0
                                    RepeatYesterdayDialog(
                                        existingMeals = existing, incomingMeals = incoming, sourceIsYesterday = true,
                                        onAdd = {
                                            val plan = (decision as MealReuse.RepeatDecision.NeedsConfirmation).plan
                                            val report = runBlocking { MealReuse.runPlan(plan, store.keys.toSet()) { store[it.id] = it } }
                                            message = MealReuse.reportMessage(report).text + " Registros en memoria: ${store.size}."
                                            open = false
                                        },
                                        onCancel = { message = "Cancelado: no se tocó nada."; open = false },
                                    )
                                }
                                "templates_empty", "templates" -> TemplatesSheet(
                                    templates = templates,
                                    onApply = { template ->
                                        message = "Cargaría ${MealReuse.templateDraftFoods(template).size} alimentos como borrador de «${template.name}»."
                                        open = false
                                    },
                                    rename = { template, newName -> templates = templates.map { if (it.id == template.id) it.copy(name = newName) else it } },
                                    delete = { template -> templates = templates.filterNot { it.id == template.id } },
                                    onDismiss = { open = false },
                                )
                                "quick_add", "quick_add_error" -> QuickEntryOverlay(
                                    initialMeal = MealType.LUNCH,
                                    onAdd = { food, meal ->
                                        message = "Agregada: ${food.foodName}, ${food.calories.toInt()} kcal (${meal.name}), rápida=${food.isQuickEntry}."
                                        open = false
                                    },
                                    onDismiss = { open = false },
                                    initialForm = if (name == "quick_add") {
                                        QuickEntryForm(name = "Café con leche", kcal = "120", protein = "4", carbs = "10", fats = "6")
                                    } else {
                                        QuickEntryForm(name = "Pizza", kcal = "900", protein = "10", carbs = "x", fats = "5")
                                    },
                                    showErrorsInitially = name == "quick_add_error",
                                )
                                else -> CopyToSheet(
                                    title = "Copiar día a…",
                                    subtitle = "2 comidas · ${MealReuse.shortDateEs(yesterday)} · 940 kcal",
                                    sources = listOf(breakfast, lunch),
                                    today = today,
                                    blocked = setOf(yesterday),
                                    busy = false,
                                    onConfirm = { dates ->
                                        val plan = MealReuse.planCopy(listOf(breakfast, lunch), dates, today)
                                        val report = runBlocking { MealReuse.runPlan(plan, store.keys.toSet()) { store[it.id] = it } }
                                        message = MealReuse.reportMessage(report).text + " Registros en memoria: ${store.size}."
                                        open = false
                                    },
                                    onDismiss = { open = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION") private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
}
