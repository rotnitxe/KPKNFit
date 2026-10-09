package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.training.PatternFamily
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.split.SplitTestSupport.LEGS
import com.example.kpkn.domain.training.split.SplitTestSupport.PULL
import com.example.kpkn.domain.training.split.SplitTestSupport.PUSH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cambios radicales de reparto (torso/pierna ↔ cuerpo completo ↔ empuje/tirón/pierna) sobre los programas de referencia.
 * Invariantes del motor: ningún día con dos pesados del mismo patrón; en cuerpo completo, pierna, empuje y tirón antes
 * que cualquier accesorio; compuestos antes que aislamientos; cada día dentro del 110 % del tiempo pedido o con recorte
 * declarado; y los mismos ejercicios salvo recortes y fusiones declarados.
 */
class SplitRadicalConversionTest {

    private val resolver = SplitTestSupport.resolver

    private data class Transition(val label: String, val program: Program, val splitId: String, val weekdays: List<Int>)

    /** Empuje, tirón y pierna dos veces: seis sesiones en seis días. */
    private fun ppl6(): Program = SplitTestSupport.program(
        listOf(
            SplitTestSupport.week(
                "w1",
                listOf(
                    SplitTestSupport.session("push1", "Empuje A", 1, PUSH),
                    SplitTestSupport.session("pull1", "Tirón A", 2, PULL),
                    SplitTestSupport.session("legs1", "Pierna A", 3, LEGS),
                    SplitTestSupport.session("push2", "Empuje B", 4, PUSH),
                    SplitTestSupport.session("pull2", "Tirón B", 5, PULL),
                    SplitTestSupport.session("legs2", "Pierna B", 6, LEGS),
                ),
            ),
        ),
        name = "Empuje, tirón y pierna x6",
    )

    private fun transitions(): List<Transition> = listOf(
        Transition("torso/pierna 4 → cuerpo completo 3", SplitTestSupport.upperLower4(), "fullbody_x3", SplitTestSupport.spreadDays(3)),
        Transition("cuerpo completo 3 → torso/pierna 4", SplitTestSupport.fullBody3(), "ul_x4", SplitTestSupport.spreadDays(4)),
        Transition("empuje/tirón/pierna 6 → torso/pierna 4", ppl6(), "ul_x4", SplitTestSupport.spreadDays(4)),
        Transition("torso/pierna 4 → empuje/tirón/pierna 6", SplitTestSupport.upperLower4(), "ppl_x6", SplitTestSupport.spreadDays(6)),
    )

    private fun redistribute(t: Transition, options: RedistributionOptions): RedistributionResult =
        SplitRedistributor.redistribute(
            program = t.program,
            split = SPLIT_TEMPLATES.first { it.id == t.splitId },
            weekdays = t.weekdays,
            resolver = resolver,
            options = options,
        )

    private fun daysOf(result: RedistributionResult): List<Session> =
        ProgramWeeks.effective(result.program)[0].week.sessions

    // ─── Rasgos de cada ejercicio ─────────────────────────────────────────────────────────────────

    private val HEAVY_PATTERNS = setOf(
        PatternFamily.SQUAT, PatternFamily.HINGE, PatternFamily.HORIZONTAL_PUSH,
        PatternFamily.VERTICAL_PUSH, PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL,
    )
    private val LEG = setOf(PatternFamily.SQUAT, PatternFamily.HINGE)
    private val PUSHES = setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH)
    private val PULLS = setOf(PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL)

    private fun traitsOf(exercise: Exercise) = requireNotNull(resolver.traitsOf(exercise)) { exercise.name }

    private fun minReps(exercise: Exercise): Int? =
        exercise.sets.mapNotNull { it.targetRepsRange?.max ?: it.targetReps }.minOrNull()

    /** Compuesto pesado: sentadilla, bisagra, empuje o tirón con barra, carga axial o pocas repeticiones (≤ 6). */
    private fun isHeavy(exercise: Exercise): Boolean {
        val t = traitsOf(exercise)
        val barbell = t.equipmentId in setOf("barbell", "smith_machine", "safety_bar", "hex_bar", "t_bar", "ez_bar")
        val lowReps = minReps(exercise)?.let { it <= 6 } == true
        return t.isCompound && t.pattern in HEAVY_PATTERNS && (barbell || t.axialLoad >= 0.5 || lowReps)
    }

    /** Clave del patrón pesado: los dos tirones cuentan como uno. */
    private fun heavyKey(exercise: Exercise): String {
        val pattern = traitsOf(exercise).pattern
        return if (pattern in PULLS) "TIRÓN" else pattern.toString()
    }

    /** Violaciones de las reglas duras y de orden de un día; vacía si el día cumple. */
    private fun violationsOf(session: Session): List<String> {
        val exercises = session.allExercises()
        val problems = mutableListOf<String>()
        exercises.filter { isHeavy(it) }.groupBy { heavyKey(it) }.forEach { (key, list) ->
            if (list.size > 1) problems += "dos pesados de $key: ${list.map { it.name }}"
        }
        val firstIsolation = exercises.indexOfFirst { !traitsOf(it).isCompound }
        val lastCompound = exercises.indexOfLast { traitsOf(it).isCompound }
        if (firstIsolation in 0..lastCompound) problems += "un aislamiento antes de un compuesto"
        if (session.scheduleLabel?.startsWith("Cuerpo Completo") == true) {
            val firstLeg = exercises.indexOfFirst { traitsOf(it).isCompound && traitsOf(it).pattern in LEG }
            val firstPush = exercises.indexOfFirst { traitsOf(it).isCompound && traitsOf(it).pattern in PUSHES }
            val firstPull = exercises.indexOfFirst { traitsOf(it).isCompound && traitsOf(it).pattern in PULLS }
            when {
                firstLeg < 0 || firstPush < 0 || firstPull < 0 ->
                    problems += "cuerpo completo sin pierna, empuje o tirón compuestos"
                firstIsolation in 0..maxOf(firstLeg, firstPush, firstPull) ->
                    problems += "un accesorio antes de los compuestos de pierna, empuje y tirón"
            }
        }
        return problems
    }

    // ─── Invariantes de reparto ───────────────────────────────────────────────────────────────────

    @Test
    fun every_radical_conversion_keeps_the_engine_rules_on_every_day() {
        val failures = mutableListOf<String>()
        transitions().forEach { t ->
            val result = redistribute(t, RedistributionOptions())
            if (!result.compatible) {
                failures += "${t.label}: ${result.reason}"
                return@forEach
            }
            val days = daysOf(result)
            if (days.map { it.dayOfWeek } != t.weekdays) failures += "${t.label}: días ${days.map { it.dayOfWeek }}"
            days.forEach { day -> violationsOf(day).forEach { failures += "${t.label} · ${day.name}: $it" } }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    /**
     * Incondicional para las cuatro transiciones del brief, con su reparto de destino. No se extiende a todos los repartos:
     * si un reparto tiene un único día que acoge piernas (ppl_x3, bro_split…), las dos bisagras caen ahí por foco.
     */
    fun two_heavy_hinges_never_share_a_day() {
        val failures = mutableListOf<String>()
        transitions().forEach { t ->
            val result = redistribute(t, RedistributionOptions())
            if (!result.compatible) {
                failures += "${t.label}: ${result.reason}"
                return@forEach
            }
            daysOf(result).forEach { day ->
                val hinges = day.allExercises().count { isHeavy(it) && traitsOf(it).pattern == PatternFamily.HINGE }
                if (hinges > 1) failures += "${t.label} · ${day.name} lleva $hinges bisagras pesadas"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun a_day_over_the_time_budget_is_trimmed_and_every_change_is_declared() {
        val target = 45
        val failures = mutableListOf<String>()
        var declaredDays = 0
        transitions().forEach { t ->
            val original = SplitTestSupport.exercisesOf(t.program).associateBy { it.id }
            val result = redistribute(t, RedistributionOptions(targetMinutes = target))
            if (!result.compatible) {
                failures += "${t.label}: ${result.reason}"
                return@forEach
            }
            val days = daysOf(result)
            val trims = result.report.setTrims.filter { it.weekIndex == 0 }
            val merges = result.report.exerciseMerges.filter { it.weekIndex == 0 }
            days.forEach { day ->
                val minutes = SessionDurationEstimator.estimate(day).totalMinutes
                val declared = trims.any { it.dayTitle == day.name } || merges.any { it.dayTitle == day.name }
                if (declared) declaredDays++
                if (minutes > target * DayTimeFit.TOLERANCE && !declared) {
                    failures += "${t.label} · ${day.name}: $minutes min sin recorte declarado"
                }
            }
            // Los mismos ejercicios salvo las fusiones; cada ejercicio pierde lo que el informe dice y gana lo de sus fusiones.
            val after = days.flatMap { it.allExercises() }.associateBy { it.id }
            val mergedAway = merges.map { it.removedExerciseId }.toSet()
            if (after.keys != original.keys - mergedAway) failures += "${t.label}: ejercicios distintos a lo declarado"
            original.keys.forEach { id ->
                if (id in mergedAway) return@forEach
                val before = original.getValue(id).sets.size
                val cut = trims.filter { it.exerciseId == id }.sumOf { it.removedSets }
                val gained = merges.count { it.keptExerciseId == id }
                val now = after[id]?.sets?.size ?: return@forEach
                if (now != before - cut + gained) failures += "${t.label} · $id: $before − $cut + $gained ≠ $now series"
                if (cut > 0 && now < DayTimeFit.MIN_SETS) failures += "${t.label} · $id: recortado por debajo de 2 series"
            }
        }
        assertEquals(emptyList<String>(), failures)
        assertTrue("al menos un día tuvo que recortarse", declaredDays > 0)
    }

    // ─── Orden dentro del día ─────────────────────────────────────────────────────────────────────

    @Test
    fun a_superset_led_by_an_isolation_does_not_rise_to_main_level_for_a_t1_member() {
        val curl = SplitTestSupport.exercise("ss-curl", CatalogIds.CURL, supersetId = "ss")
        val lat = SplitTestSupport.exercise("ss-lat", CatalogIds.LAT, supersetId = "ss").copy(slotRole = SlotRole.T1_MAIN)
        val isolationLead = supersetWeek("a", listOf(curl, lat))
        assertEquals(2, WeekGather.gather(isolationLead, resolver).single().tier)

        val compoundLead = supersetWeek(
            "b",
            listOf(lat.copy(id = "ss-lat-b"), curl.copy(id = "ss-curl-b")),
        )
        assertEquals(0, WeekGather.gather(compoundLead, resolver).single().tier)
    }

    private fun supersetWeek(id: String, members: List<Exercise>): ProgramWeek = SplitTestSupport.week(
        "w-$id",
        listOf(
            Session(
                id = id,
                name = "Brazos $id",
                dayOfWeek = 1,
                assignedDays = listOf(1),
                parts = listOf(SessionPart(id = "$id#part:0", name = "Accesorios", exercises = members)),
                supersetGroups = listOf(
                    SupersetGroup(id = "ss", exerciseOrder = members.map { it.id }, restBetweenExercises = 30, restAfterSuperset = 90),
                ),
            ),
        ),
    )
}
