package com.example.kpkn.domain.training.quality

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.training.CardioPreference
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.EquipmentKeys
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.SYMBOL_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.SymbolEquipmentKeys
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineTestSupport
import com.example.kpkn.domain.training.periodization.Horizon
import com.example.kpkn.domain.training.periodization.ProgramVariant
import com.example.kpkn.domain.training.periodization.RoutinePeriodizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.TreeMap

/**
 * F-02 · Red de «slop» en sesiones: cada sesión de cada semana de cada programa que la app puede producir pasa por
 * [SessionSlopRules.violations]; además, a nivel receta, cuenta los accesorios de relleno repetidos más de 2 días por semana.
 *
 * El techo [CEILING] es un trinquete (`total <= CEILING`): la línea base se midió en master el 27-oct (ola 0); cada ola
 * lo baja al total real y la ola 5 lo deja en 0.
 * Informe: `build/reports/session-slop/report.txt` (directorio de trabajo del test).
 */
class ProgramSessionSlopAuditTest {

    private data class Finding(val rule: String, val program: String, val week: String, val session: String, val detail: String)

    private class Audit {
        val findings = ArrayList<Finding>()
        val unavailable = ArrayList<String>()
        var programs = 0
        var sessions = 0

        fun weeksOf(program: Program): List<ProgramWeek> = AuthoredPlanFixtures.weeksOf(program)

        fun audit(label: String, program: Program) {
            programs++
            weeksOf(program).forEachIndexed { index, week ->
                val weekLabel = week.name.ifBlank { "Semana ${index + 1}" }
                week.sessions.forEach { session ->
                    sessions++
                    SessionSlopRules.violations(session).forEach { v ->
                        findings += Finding(v.rule.name, label, weekLabel, v.sessionName, v.detail)
                    }
                }
            }
        }

        fun auditFiller(label: String, recipe: TrainingPlanRecipe) {
            recipe.weeks.forEach { week ->
                val daysByConfiguration = LinkedHashMap<String, MutableSet<String>>()
                week.days.forEachIndexed { dayIndex, day ->
                    day.slots
                        .filter { it.role == SlotRole.T3_ACCESSORY && it.source == SlotSource.KPKN_DEFAULT }
                        .forEach { slot -> daysByConfiguration.getOrPut(slot.lift.configurationId) { LinkedHashSet() } += day.id ?: "${dayIndex + 1}:${day.label}" }
                }
                daysByConfiguration.filterValues { it.size > FILLER_WEEKLY_CAP }.forEach { (configuration, days) ->
                    findings += Finding(
                        FILLER_OVER_WEEKLY_CAP,
                        label,
                        week.weekName.ifBlank { "Semana ${week.weekNumber}" },
                        "receta",
                        "$configuration en ${days.size} días: $days",
                    )
                }
            }
        }
    }

    @Test
    fun `ninguna sesion de ningun programa tiene slop`() {
        val audit = Audit()
        auditAuthoredPlans(audit)
        auditNativePlans(audit)
        auditRoutines(audit)
        val report = render(audit)
        val dir = File("build/reports/session-slop").apply { mkdirs() }
        File(dir, "report.txt").writeText(report)
        println(report.lineSequence().take(60).joinToString("\n"))
        val total = audit.findings.size
        assertTrue(
            "Violaciones de slop en sesiones: total $total > techo $CEILING (ver build/reports/session-slop/report.txt)",
            total <= CEILING,
        )
        // F-48: ningún programa muestra nombres internos de ejercicio (el cardio sale con su rótulo en español).
        val internalNames = audit.findings.filter { it.rule == SlopRule.INTERNAL_NAME.name }
        assertEquals("Nombres internos visibles: ${internalNames.take(3)}", 0, internalNames.size)
    }

    /**
     * E7: TODO el material confirmado (categorías, todas las llaves del subpanel y todas las de símbolo, GHD y rueda
     * abdominal incluidas). Es el material con el que toda receta `protocol:*` debe materializarse.
     */
    private val everything = AuthoredPlanFixtures.Gear(
        "E7-todo-el-material",
        EquipmentAvailability(
            categories = EquipmentCategory.entries.toSet(),
            apparatus = (EFFECTIVE_EQUIPMENT_KEYS.map { it.key } + SYMBOL_EQUIPMENT_KEYS.map { it.key })
                .associateWith { ApparatusPresence.PRESENT },
            supports = listOf(
                EquipmentKeys.BENCH_FLAT, EquipmentKeys.BENCH_ADJUSTABLE, EquipmentKeys.SQUAT_RACK, EquipmentKeys.PREACHER_BENCH,
                EquipmentKeys.PULLUP_BAR, EquipmentKeys.DIP_BARS, EquipmentKeys.LOW_BAR_SUPPORT, EquipmentKeys.EZ_BAR,
                SymbolEquipmentKeys.GHD, SymbolEquipmentKeys.AB_WHEEL, SymbolEquipmentKeys.RINGS, SymbolEquipmentKeys.PLYO_BOX,
                SymbolEquipmentKeys.DECLINE_BENCH, SymbolEquipmentKeys.HYPEREXTENSION_BENCH,
            ).associateWith { ApparatusPresence.PRESENT },
        ),
    )

    // ─── 1 + 4 · Planes de autor, propios con receta y plantillas ──────────────────────────────

    private fun auditAuthoredPlans(audit: Audit) {
        val gears = listOf(AuthoredPlanFixtures.fullGym, AuthoredPlanFixtures.dumbbellsAndBench, AuthoredPlanFixtures.gymWithoutRack, everything)
        PersonalizedPlanCatalog.entries().filter { it.recipe != null }.forEach { entry ->
            audit.auditFiller("receta ${entry.id}", requireNotNull(entry.recipe))
            gears.forEach { gear ->
                val label = "${entry.id} · ${gear.label}"
                val program = try {
                    AuthoredPlanFixtures.prepare(entry.id, gear)
                } catch (t: Throwable) {
                    audit.unavailable += "$label: ${t.javaClass.simpleName}: ${t.message?.lineSequence()?.firstOrNull().orEmpty().take(160)}"
                    return@forEach
                }
                audit.audit(label, program)
            }
        }
    }

    // ─── 2 · Planes propios nativos (misma llamada que CoverageFixtures.materializer, sin screens.onboarding) ───

    private fun auditNativePlans(audit: Audit) {
        val personalizer = SimpleCyclePersonalizer(
            InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog).also { runBlocking { it.load() } },
        )
        val gears = listOf(AuthoredPlanFixtures.fullGym, AuthoredPlanFixtures.dumbbellsAndBench)
        NativeProfileKind.entries.forEach { kind ->
            CatalogLevel.entries.forEach { level ->
                (2..6).forEach { days ->
                    gears.forEach { gear ->
                        val label = "${kind.entryId} · ${level.name.lowercase()} · $days d · ${gear.label}"
                        val result = try {
                            personalizer.personalize(
                                programId = "slop-${kind.name.lowercase()}-${level.name.lowercase()}-$days-${gear.label}",
                                input = PersonalizerInput(
                                    catalogEntryId = kind.entryId,
                                    focus = TrainingFocus.FULL_BODY,
                                    frequency = days,
                                    weekdays = NativeProfileCalendars.DEFAULT_WEEKDAYS.getValue(days),
                                    equipment = emptySet(),
                                    level = level,
                                    availableMinutes = NATIVE_MINUTES,
                                    cardio = if (kind == NativeProfileKind.COMPLETE_ATHLETE) CardioPreference(CardioType.WALK, 10) else null,
                                ),
                                options = TrainingOptions(availability = gear.availability),
                            )
                        } catch (t: Throwable) {
                            audit.unavailable += "$label: ${t.javaClass.simpleName}: ${t.message?.lineSequence()?.firstOrNull().orEmpty().take(160)}"
                            return@forEach
                        }
                        val program = result.program
                        if (program == null) {
                            audit.unavailable += "$label: ${result.report.reasonCode}: ${result.report.limitations.joinToString("; ").take(160)}"
                            return@forEach
                        }
                        audit.audit(label, program)
                    }
                }
            }
        }
    }

    // ─── 3 · Programas a medida: semana base y periodizador ────────────────────────────────────

    private fun auditRoutines(audit: Audit) {
        RoutineTestSupport.profiles.forEach { profile ->
            RoutineMode.entries.forEach { mode ->
                RoutineTestSupport.levels.forEach { level ->
                    ROUTINE_DAYS.forEach { days ->
                        ROUTINE_MINUTES.forEach { minutes ->
                            val request = RoutineTestSupport.request(profile, mode, level, days, minutes)
                            val label = "a medida · ${profile.id} · ${mode.name} · ${level.name} · $days d · $minutes min"
                            val routine = try {
                                RoutineGenerator.generate(request)
                            } catch (t: Throwable) {
                                return@forEach // la semana base que lanza ya la vigila RoutineGeneratorSweepTest
                            }
                            audit.audit(label, routine.program)
                            listOf(ProgramVariant.BLOCKS to 6, ProgramVariant.PERIODIZED to 12).forEach { (variant, weeks) ->
                                val periodized = try {
                                    RoutinePeriodizer.build(request, routine.program, variant, Horizon.Weeks(weeks))
                                } catch (t: Throwable) {
                                    audit.unavailable += "$label · ${variant.name} $weeks sem: ${t.javaClass.simpleName}: ${t.message?.lineSequence()?.firstOrNull().orEmpty().take(160)}"
                                    return@forEach
                                }
                                audit.audit("$label · ${variant.name} $weeks sem", periodized.program)
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Informe ───────────────────────────────────────────────────────────────────────────────

    /** Clase de la etiqueta del programa: receta, protocol, original, adapted, native o «a medida». */
    private fun classOf(label: String): String =
        if (label.startsWith("receta ")) "receta" else label.substringBefore(" ·").substringBefore(":")

    private fun render(audit: Audit): String {
        val byRule = TreeMap<String, Int>()
        audit.findings.forEach { byRule[it.rule] = (byRule[it.rule] ?: 0) + 1 }
        return buildString {
            appendLine("[session-slop] programas revisados: ${audit.programs}; sesiones revisadas: ${audit.sessions}")
            appendLine("[session-slop] violaciones totales: ${audit.findings.size} (techo actual: $CEILING)")
            appendLine("[session-slop] por regla:")
            (SlopRule.entries.map { it.name } + FILLER_OVER_WEEKLY_CAP).forEach { rule -> appendLine("[session-slop]   $rule: ${byRule[rule] ?: 0}") }
            appendLine("[session-slop] por clase de programa (violaciones):")
            audit.findings.groupingBy { classOf(it.program) }.eachCount().toSortedMap()
                .forEach { (kind, count) -> appendLine("[session-slop]   $kind: $count") }
            appendAggregate(audit.findings)
            appendLine("[session-slop] primeras ${REPORT_SAMPLE} violaciones (regla · programa · semana · sesión · detalle):")
            audit.findings.take(REPORT_SAMPLE).forEach { f -> appendLine("  ${f.rule} · ${f.program} · ${f.week} · ${f.session} · ${f.detail}") }
            appendLine("[session-slop] muestra por regla (hasta $RULE_SAMPLE cada una):")
            audit.findings.groupBy { it.rule }.toSortedMap().forEach { (rule, list) ->
                list.take(RULE_SAMPLE).forEach { f -> appendLine("  $rule · ${f.program} · ${f.week} · ${f.session} · ${f.detail}") }
            }
            appendLine("[session-slop] programas por programa (violaciones):")
            audit.findings.groupingBy { it.program }.eachCount().entries.sortedByDescending { it.value }.take(REPORT_SAMPLE)
                .forEach { (program, count) -> appendLine("  $count · $program") }
            appendLine("[session-slop] planes no disponibles (${audit.unavailable.size}):")
            audit.unavailable.forEach { appendLine("  $it") }
        }
    }

    /** Id del programa sin nivel, días ni material: «native:x-v2», «protocol:y», «receta protocol:z», «a medida · MODO [· VARIANTE]». */
    private fun programIdOf(label: String): String {
        if (label.startsWith("receta ")) return label
        val parts = label.split(" · ")
        if (parts.first() != "a medida") return parts.first()
        val variant = parts.last().takeIf { it.startsWith("BLOCKS ") || it.startsWith("PERIODIZED ") }?.substringBefore(" ")
        return listOfNotNull("a medida", parts.getOrNull(2), variant).joinToString(" · ")
    }

    private val repeatCount = Regex(" ×[0-9]+")

    private data class AggregateKey(val kind: String, val program: String, val rule: String, val detail: String)

    /** Agrupa por (clase, programa, regla, detalle sin «×N») con recuento y un ejemplo completo; ordena por recuento. */
    private fun StringBuilder.appendAggregate(findings: List<Finding>) {
        val groups = findings.groupBy { f -> AggregateKey(classOf(f.program), programIdOf(f.program), f.rule, f.detail.replace(repeatCount, "")) }
        appendLine("[session-slop] agregado: ${groups.size} grupos (clase · programa · regla · detalle normalizado → recuento; ejemplo):")
        groups.entries.sortedWith(compareByDescending<Map.Entry<AggregateKey, List<Finding>>> { it.value.size }.thenBy { it.key.toString() })
            .forEach { (key, list) ->
                val example = list.first()
                appendLine("  ${list.size} · ${key.kind} · ${key.program} · ${key.rule} · ${key.detail}")
                appendLine("      ejemplo: ${example.program} · ${example.week} · ${example.session} · ${example.detail}")
            }
    }

    private companion object {
        /** Techo (trinquete) tras F-02b: 2238 con 3 materiales en master; 2464 al sumar E7; 1280 tras F-10/F-11; F-15 (identidad propia de las apariciones de autor) lo baja más. La ola 5 debe dejarlo en 0. */
        const val CEILING = 162
        const val FILLER_OVER_WEEKLY_CAP = "FILLER_OVER_WEEKLY_CAP"
        const val FILLER_WEEKLY_CAP = 2
        const val REPORT_SAMPLE = 40
        const val RULE_SAMPLE = 10
        const val NATIVE_MINUTES = 75
        val ROUTINE_DAYS = (2..6).toList()
        val ROUTINE_MINUTES = listOf(45, 75)
    }
}
