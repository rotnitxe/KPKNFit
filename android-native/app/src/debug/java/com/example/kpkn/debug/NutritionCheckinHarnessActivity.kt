package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.checkin.*
import com.example.kpkn.domain.nutrition.expenditure.*
import com.example.kpkn.screens.nutrition.checkin.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.*
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Flujo real en memoria. Extras: state, fontScale, reducedMotion y widthDp. */
class NutritionCheckinHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val name = intent.getStringExtra("state") ?: "ready"
        val today = LocalDate.of(2026, 10, 19)
        val days = (1L..56L).reversed().map { ago ->
            DayObservation(today.minusDays(ago), when {
                name == "paused" && ago <= 8 -> null
                name == "partial_days" && ago == 2L -> 300.0
                name == "partial_days" && ago == 4L -> null
                else -> 2400.0
            })
        }
        val source = MutableStateFlow(CheckinContext(
            plan = NutritionPlan(id = "vista-previa", calorieTarget = if (name == "proposal_nochange") 2400 else 2100,
                weeklyChangeKg = 0.0, direction = PlanDirection.MAINTENANCE, isActive = true),
            weekStartDay = DayOfWeek.MONDAY, days = days,
            weights = days.map { WeightObservation(it.date, 80.0) }, initialKcal = 2400.0, restingKcal = 1650.0,
            preferences = CheckinPreferences(introductionSeen = name != "ready"),
        ))
        val memory = MemoryCheckinStore(name == "apply_error") { target ->
            source.value = source.value.copy(plan = source.value.plan?.copy(calorieTarget = target))
        }
        val controller = NutritionCheckinController(lifecycleScope, CheckinDependencies(source, memory,
            savePreferences = { transform -> source.value = source.value.copy(preferences = transform(source.value.preferences)) },
            markDay = { date, mark, _ -> source.value = source.value.copy(days = source.value.days.map { if (it.date == date) it.copy(mark = mark) else it }) },
            today = { today },
        ))
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        setContent { KPKNTheme {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides reduced) {
                val current by controller.state.collectAsState()
                var started by remember { mutableStateOf(name != "ready") }
                val selected = when (name) {
                    "paused" -> CheckinModule.LOGGING_BREAK
                    "partial_days" -> CheckinModule.PARTIAL_DAYS
                    "proposal_change", "proposal_nochange", "apply_error" -> CheckinModule.TARGET_UPDATE
                    else -> null
                }
                var entered by remember { mutableStateOf(false) }
                LaunchedEffect(current.loading) {
                    if (!current.loading && !entered) {
                        entered = true
                        val index = selected?.let { current.steps.indexOf(it) }?.coerceAtLeast(0) ?: 0
                        repeat(index) { controller.next().join() }
                        if (name == "apply_error") controller.accept().join()
                    }
                }
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = width.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!started) NutritionCheckinCard({ started = true })
                        else NutritionCheckinContent(current, { controller.next() }, controller::back,
                            { controller.accept() }, { controller.decline() }, { controller.postpone() },
                            { controller.dismissModule(it) }, { controller.markPartial(it) }, { controller.keepComplete(it) },
                            { controller.confirmFasting(it) }, { finish() }, { controller.refresh() })
                        CheckinDaySetting(current.checkinDay, { controller.setCheckinDay(it) })
                    }
                }
            }
        } }
    }

    @Suppress("DEPRECATION") private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
}

/** Repositorio exclusivo del arnés: nunca abre Room. */
private class MemoryCheckinStore(private val failApply: Boolean, private val updateTarget: (Int) -> Unit) : CheckinStore {
    private val mutable = MutableStateFlow<List<CheckinRecord>>(emptyList())
    override val records = mutable.asStateFlow()
    override suspend fun apply(plan: CheckinPlan) {
        if (mutable.value.any { it.weekKey == plan.weekKey && it.status == CheckinDecision.APPLIED }) return
        put(CheckinRecord(plan.weekKey, CheckinDecision.PENDING))
        check(!failApply) { "Fallo de prueba al guardar." }
        updateTarget(requireNotNull(plan.proposal).afterKcal)
        put(CheckinRecord(plan.weekKey, CheckinDecision.APPLIED, appliedPlanId = plan.basePlan.id))
    }
    override suspend fun decline(weekKey: LocalDate, proposal: CheckinProposal?) { put(CheckinRecord(weekKey, CheckinDecision.DECLINED)) }
    override suspend fun postpone(weekKey: LocalDate, today: LocalDate, proposal: CheckinProposal?) { put(CheckinRecord(weekKey, CheckinDecision.POSTPONED, today.plusDays(1))) }
    private fun put(record: CheckinRecord) { mutable.value = mutable.value.filterNot { it.weekKey == record.weekKey } + record }
}
