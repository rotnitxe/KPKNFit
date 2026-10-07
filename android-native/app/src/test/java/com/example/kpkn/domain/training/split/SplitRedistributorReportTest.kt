package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.CoverageFixtures
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.TrainingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Genera `build/reports/split-redistributor/before-after.txt`: cinco casos (programas sintéticos y reales) con los minutos
 * por día y el volumen semanal por músculo antes y después de adaptarlos a un reparto.
 */
class SplitRedistributorReportTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private val FULL_GYM = CoverageFixtures.legacyFixtures().first { it.id == "E6" }.availability
        private val DAY_NAMES = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    }

    private class SequentialIds : IdProvider {
        private var next = 0
        override fun newId(): String = "report_${++next}"
    }

    private data class Case(
        val title: String,
        val program: Program,
        val splitId: String,
        val weekdays: List<Int>,
        val allowAuthored: Boolean = false,
        val propagate: Boolean = false,
    )

    private fun nativeProgram(kind: NativeProfileKind, days: Int): Program {
        val result = CoverageFixtures.personalizer().personalize(
            programId = "report-${kind.sourceId}-$days",
            input = PersonalizerInput(
                catalogEntryId = kind.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = days,
                weekdays = CoverageFixtures.weekdays(days),
                equipment = emptySet(),
                level = CatalogLevel.INTERMEDIATE,
                availableMinutes = 90,
                cardio = null,
            ),
            options = TrainingOptions(availability = FULL_GYM),
        )
        return requireNotNull(result.program) { "${result.report.reasonCode}: ${result.report.limitations}" }
    }

    private fun phul(): Program = PlanMaterializer.materialize(
        program = Program(id = "phul-report", name = "PHUL", startDay = 1),
        recipe = AuthoredPhulPhatRecipes.phulOriginal,
        metadata = CatalogCompositionTestSupport.metadata,
        idProvider = SequentialIds(),
        strict = false,
    )

    private fun cases(): List<Case> = listOf(
        Case("PPL de 3 sesiones (sintético) → torso y pierna de 4 días", SplitTestSupport.ppl(), "ul_x4", listOf(1, 2, 4, 5)),
        Case("PPL de 3 sesiones (sintético) → torso y pierna de 2 días (fin de semana)", SplitTestSupport.ppl(), "weekend_warrior", listOf(6, 7)),
        Case("Plan propio Músculo de 3 días (real) → torso y pierna de 4 días", nativeProgram(NativeProfileKind.MUSCLE, 3), "ul_x4", listOf(1, 2, 4, 5), propagate = true),
        Case("Plan de autor PHUL de 4 días (real, con permiso) → cadena anterior y posterior", phul(), "ant_post_x4", listOf(1, 2, 4, 5), allowAuthored = true),
        Case("Un grupo por día de 5 sesiones (sintético) → empuje, tirón y pierna de 6 días", SplitTestSupport.broSplit5(), "ppl_x6", listOf(1, 2, 3, 4, 5, 6)),
    )

    private fun fixed(value: Double) = String.format(Locale.ROOT, "%.1f", value)

    private fun daysText(weekdays: List<Int>) = weekdays.joinToString(" ") { DAY_NAMES[it - 1] }

    @Test
    fun writes_the_before_and_after_report_for_five_cases() {
        val resolver = SplitTestSupport.resolver
        val text = StringBuilder()
        text.append("KPKN · redistribuidor de repartos · antes y después\n")
        text.append("=".repeat(60)).append('\n')
        text.append("Volumen en series semanales por músculo (primario 1, secundario 0,5, como el volumen de la app).\n")
        text.append("Minutos con el estimador común de sesiones (aproximación y movilidad incluidas).\n\n")

        val summaries = mutableListOf<String>()
        cases().forEachIndexed { index, case ->
            val split = SPLIT_TEMPLATES.first { it.id == case.splitId }
            val result = SplitRedistributor.redistribute(
                program = case.program,
                split = split,
                weekdays = case.weekdays,
                resolver = resolver,
                allowAuthoredRecipes = case.allowAuthored,
                options = RedistributionOptions(propagateToOtherWeeks = case.propagate),
            )
            assertTrue("${case.title}: ${result.reason}", result.compatible)
            val report = result.report
            text.append("Caso ${index + 1} · ${case.title}\n")
            text.append("  reparto: ${report.splitName} (${report.splitId}) · días: ${daysText(case.weekdays)}\n")
            text.append("  compatible: sí · desviación máxima de volumen: ${fixed(report.maxVolumeDeviation)} series\n")
            text.append("  Antes (${report.before.size} sesiones):\n")
            report.before.forEach { session ->
                val day = session.weekday?.let { DAY_NAMES[it - 1] } ?: "—"
                text.append(String.format(Locale.ROOT, "    %-26s %-4s %4d min  %2d ejercicios\n", session.title, day, session.minutes, session.exerciseCount))
            }
            text.append("  Después (${report.days.size} días):\n")
            report.days.forEach { day ->
                text.append(
                    String.format(
                        Locale.ROOT, "    %-26s %-4s %4d min  %2d ejercicios  %s\n",
                        day.title, DAY_NAMES[day.weekday - 1], day.minutes, day.exerciseCount, day.focus,
                    ),
                )
                text.append("        ").append(day.exercises.joinToString(" · ")).append('\n')
            }
            val minutes = report.days.map { it.minutes }
            text.append("  Minutos por día: ${minutes.joinToString(" / ")} (mín ${minutes.min()}, máx ${minutes.max()})\n")
            assertTrue("${case.title}: ${report.minutesRatio}", report.minutesRatio in 0.0..1.0)
            text.append("  Minutos parejos (corto / largo): ${fixed(report.minutesRatio)} · días con menos de 3 ejercicios: ${report.shortDayCount}\n")
            text.append("  Volumen semanal por músculo (antes → después):\n")
            (report.volumeBefore.keys + report.volumeAfter.keys).sorted().forEach { muscle ->
                text.append(
                    String.format(
                        Locale.ROOT, "    %-22s %6s → %6s\n",
                        muscle, fixed(report.volumeBefore[muscle] ?: 0.0), fixed(report.volumeAfter[muscle] ?: 0.0),
                    ),
                )
            }
            if (result.notes.isEmpty()) {
                text.append("  Notas: ninguna\n")
            } else {
                text.append("  Notas:\n")
                result.notes.forEach { text.append("    - ").append(it).append('\n') }
            }
            text.append('\n')
            assertEquals(0.0, report.maxVolumeDeviation, 1e-9)
            summaries += "Caso ${index + 1}: ${report.before.map { it.minutes }} min → $minutes min · desviación de volumen ${fixed(report.maxVolumeDeviation)}"
        }

        val file = File("build/reports/split-redistributor/before-after.txt")
        file.parentFile?.mkdirs()
        file.writeText(text.toString(), Charsets.UTF_8)
        println(text)
        println("RESUMEN\n" + summaries.joinToString("\n"))
        assertTrue(file.length() > 0)
        assertEquals(5, Regex("(?m)^Caso \\d · ").findAll(text).count())
    }
}
