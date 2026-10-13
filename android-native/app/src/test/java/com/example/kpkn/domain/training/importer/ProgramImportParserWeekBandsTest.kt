package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H6c: calculadoras con bloques «3 @ · 121 · x 6» bajo filas de semanas en columnas (familia A de la colección). Las hojas
 * están escritas a mano y copian la disposición de las plantillas; los nombres son inventados o los de la hoja.
 */
class ProgramImportParserWeekBandsTest {

    @Test
    fun `doce semanas de bloques de tres celdas, con Bench Press y Día 1 sin avisos de fila`() {
        val sheet = ImportSheet(
            name = "12 Week",
            rows = listOf(
                listOf("To make your own copy: …"),
                listOf("", "", "", "The WeighTrainer"),
                listOf("", "", "", "", "", "12 Week Bench Press Peaking Program"),
                listOf("Enter your current 1RM:", "", "", "200"),
                listOf("Projected new 1RM:", "", "", "220"),
                listOf("", "Week 1:", "", "", "", "Week 2:", "", "", "", "Week 3:", ""),
                listOf("3 @", "121", "x 6", "", "4 @", "121", "x 8", "", "5 @", "132", "x 8"),
                listOf("", "Week 4:", "", "", "", "Week 5:", "", "", "", "Week 6:", ""),
                listOf("5 @", "143", "x 7", "", "5 @", "154", "x 6", "", "5 @", "165", "x 5-6"),
                listOf("", "Week 7:", "", "", "", "Week 8:", "", "", "", "Week 9:", ""),
                listOf("4 @", "132", "x 4", "", "5 @", "176", "x 5", "", "4 @", "187", "x 4"),
                listOf("", "Week 10:", "", "", "", "Week 11:", "", "", "", "Week 12:", ""),
                listOf("4 @", "198", "x 3", "", "3 @", "209", "x 2", "", "1 @", "220", "x 1"),
                listOf("", "NOTE: Do warm-up sets as you see fit before each working set."),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "12 Week")

        assertEquals((1..12).toList(), draft.weeks.map { it.index })
        draft.weeks.forEach { week -> assertEquals(listOf("Día 1"), week.days.map { it.name }) }
        val exercises = draft.weeks.flatMap { week -> week.days.flatMap { it.exercises } }
        assertEquals(listOf("Bench Press"), exercises.map { it.rawName }.distinct())
        val week1 = draft.weeks[0].days.single().exercises.single()
        assertEquals(3, week1.sets)
        assertEquals(6, week1.repsMin)
        assertEquals(6, week1.repsMax)
        assertEquals(121.0, week1.loadKg)
        val week6 = draft.weeks[5].days.single().exercises.single()
        assertEquals(5, week6.sets)
        assertEquals(5, week6.repsMin)
        assertEquals(6, week6.repsMax)
        assertEquals(165.0, week6.loadKg)
        assertTrue(draft.warnings.none { it.kind == ImportWarningKind.SKIPPED_ROW })
        assertTrue(draft.warnings.any { it.message == "Hoja «12 Week»: las cargas salen de un 1RM de 200; cámbialas si el tuyo es otro." })
    }

    @Test
    fun `tres semanas con Monday, Weds y Friday y solo la primera opcion`() {
        val sheet = ImportSheet(
            name = "1974",
            rows = listOf(
                listOf("", "", "", "The WeighTrainer"),
                listOf("", "", "", "", "USSR Yearbook 1974 Squat Routine"),
                listOf("Enter your current 1RM:", "", "", "400"),
                listOf("Option 1:"),
                listOf("", "", "Week 1:", "", "", "Week 2:", "", "", "Week 3:", ""),
                listOf("Monday:", "4 @", "300", "x 5", "3 @", "320", "x 5", "4 @", "300", "x 3"),
                listOf("Weds:", "5 @", "340", "x 5", "6 @", "340", "x 5", "3 @", "340", "x 3"),
                listOf("Friday:", "4 @", "240", "x 3", "4 @", "240", "x 3", "4 @", "240", "x 3"),
                listOf("Option 2: (attempt only…"),
                listOf("", "", "Week 1:", "", "", "Week 2:", "", "", "Week 3:", ""),
                listOf("Monday:", "3 @", "320", "x 5", "4 @", "300", "x 5", "4 @", "300", "x 3"),
                listOf("Weds:", "1-4 @", "360", "x 5", "5-6 @", "340", "x 3", "3 @", "320", "x 3"),
                listOf("Friday:", "4 @", "240", "x 3", "4 @", "240", "x 3", "4 @", "260", "x 3"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "1974")

        assertEquals(listOf(1, 2, 3), draft.weeks.map { it.index })
        assertEquals(listOf("Monday", "Weds", "Friday"), draft.weeks[0].days.map { it.name })
        assertEquals(listOf("Squat"), draft.weeks.flatMap { w -> w.days.flatMap { d -> d.exercises } }.map { it.rawName }.distinct())
        val mondayWeek1 = draft.weeks[0].days[0].exercises.single()
        assertEquals(4, mondayWeek1.sets)
        assertEquals(5, mondayWeek1.repsMin)
        assertEquals(300.0, mondayWeek1.loadKg)
        val mondayWeek2 = draft.weeks[1].days[0].exercises.single()
        assertEquals(3, mondayWeek2.sets)
        assertEquals(320.0, mondayWeek2.loadKg)
        assertTrue(draft.warnings.any { it.message == "Hoja «1974»: trae varias opciones; se leyó la primera." })
        assertTrue(draft.warnings.any { it.message == "Hoja «1974»: las cargas salen de un 1RM de 400; cámbialas si el tuyo es otro." })
    }

    @Test
    fun `la segunda carga del bloque es la buena y Round to 5 da libras`() {
        val sheet = ImportSheet(
            name = "sovpeak",
            rows = listOf(
                listOf("6 Week Soviet Peaking Program"),
                listOf("Enter your current 1RM:", "", "", "", "100", "", "", "Round to:", "5"),
                listOf("", "", "Week 1: Light Day", "", "", "", "", "Week 1: Heavy Day", ""),
                listOf("1 @", "40", "45", "x 8", "", "1 @", "40", "45", "x 8"),
                listOf("3 @", "70", "70", "x 6", "", "1 @", "75", "75", "x 5"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "sovpeak")

        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
        val week1 = draft.weeks.single()
        assertEquals(listOf("Light Day", "Heavy Day"), week1.days.map { it.name })
        assertEquals(listOf(45.0, 70.0), week1.days[0].exercises.map { it.loadKg })
        assertEquals(listOf(1, 3), week1.days[0].exercises.map { it.sets })
        assertEquals(listOf(45.0, 75.0), week1.days[1].exercises.map { it.loadKg })
    }

    @Test
    fun `una semana con dos bandas, días A y B, cada una con sus filas en orden`() {
        val sheet = ImportSheet(
            name = "6week",
            rows = listOf(
                listOf("The 6 Week Power Bench Press Program"),
                listOf("Enter your current 1RM:", "", "", "", "250"),
                listOf("Enter 2.5 for KG or 5 for LB", "", "", "", "5"),
                listOf("", "Week 1: A", "", "", "", "Week 1: B", ""),
                listOf("1 @", "115", "x 10", "", "1 @", "115", "x 10"),
                listOf("1 @", "165", "x 5", "", "1 @", "165", "x 5"),
                listOf("1 @", "190", "x 3", "", "1 @", "190", "x 3"),
                listOf("1 @", "215", "x 2", "", "3 @", "215", "x 3"),
                listOf("4 @", "240", "x 1"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "6week")

        assertEquals(listOf(1), draft.weeks.map { it.index })
        assertEquals(listOf("A", "B"), draft.weeks[0].days.map { it.name })
        val a = draft.weeks[0].days[0].exercises
        assertEquals(listOf(115.0, 165.0, 190.0, 215.0, 240.0), a.map { it.loadKg })
        assertEquals(listOf(1, 1, 1, 1, 4), a.map { it.sets })
        val b = draft.weeks[0].days[1].exercises
        assertEquals(listOf(115.0, 165.0, 190.0, 215.0), b.map { it.loadKg })
        assertEquals(listOf(1, 1, 1, 3), b.map { it.sets })
        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
    }

    @Test
    fun `Weeks 1 and 2 dan los mismos ejercicios en las dos semanas y sin levantamiento se avisa`() {
        val sheet = ImportSheet(
            name = "power2",
            rows = listOf(
                listOf("Classic Powerlifting Peaking Program"),
                listOf("Enter your current 1RM:", "", "", "", "405"),
                listOf("Weeks 1 & 2", "", "", "", "Weeks 3 & 4", "", "", "", "Weeks 5 & 6"),
                listOf("1 @", "230.445", "x 8", "", "1 @", "230.445", "x 8", "", "1 @", "256.77", "x 6"),
                listOf("3 @", "256.77", "x 14", "", "3 @", "286.335", "x 10", "", "1 @", "286.335", "x 4"),
                listOf("", "", "", "", "", "", "", "", "3 @", "314.28", "x 8"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "power2")

        assertEquals(listOf(1, 2, 3, 4, 5, 6), draft.weeks.map { it.index })
        for (weekIndex in 0..1) {
            val exercises = draft.weeks[weekIndex].days.single().exercises
            assertEquals(listOf("Levantamiento principal", "Levantamiento principal"), exercises.map { it.rawName })
            assertEquals(listOf(230.445, 256.77), exercises.map { it.loadKg })
            assertEquals(listOf(1, 3), exercises.map { it.sets })
        }
        assertTrue(draft.warnings.any { it.message == "Hoja «power2»: no dice qué levantamiento es; elige el ejercicio." })
        assertTrue(draft.warnings.any { it.message == "Hoja «power2»: las cargas salen de un 1RM de 405; cámbialas si el tuyo es otro." })
    }

    @Test
    fun `rango de series, AMRAP y sufijo unload de una semana`() {
        val sheet = ImportSheet(
            name = "Calc",
            rows = listOf(
                listOf("", "Week 1:", "", "", "", "Week 4: unload", ""),
                listOf("1-4 @", "100", "x 5", "", "3 @", "110", "x 6+"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Calc")

        val week1 = draft.weeks.first { it.index == 1 }.days.single().exercises.single()
        assertEquals(1, week1.sets)
        assertEquals("Series: 1-4.", week1.notes)
        val week4 = draft.weeks.first { it.index == 4 }.days.single().exercises.single()
        assertEquals(3, week4.sets)
        assertEquals(6, week4.repsMin)
        assertEquals("unload. AMRAP: última serie, 6 o más repeticiones.", week4.notes)
    }

    @Test
    fun `tabla normal, celda suelta y semanas sin bloques no son calculadora`() {
        val table = listOf(listOf("Exercise", "Sets", "Reps"), listOf("Squat", "3", "5"))
        assertNull(WeekBandBlocks.plan(table))
        assertNull(WeekBandBlocks.plan(listOf(listOf("Squat 3 @ 100 x 5"))))
        assertNull(WeekBandBlocks.plan(listOf(listOf("", "Week 1", "", "Week 2"))))

        val draft = ProgramImportParser.parseSheets(listOf(ImportSheet("Plan", table)), title = "Plan")

        val exercise = draft.weeks.single().days.single().exercises.single()
        assertEquals("Squat", exercise.rawName)
        assertEquals(3, exercise.sets)
        assertEquals(5, exercise.repsMin)
    }

    @Test
    fun `un bloque sin repeticiones se omite con su aviso y el resto de la fila se lee`() {
        val sheet = ImportSheet(
            name = "Calc",
            rows = listOf(
                listOf("", "Week 1:", "", "", "", "Week 2:"),
                listOf("1 @", "60", "60", "x failure", "3 @", "100", "x 5"),
            ),
        )

        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Calc")

        assertEquals(listOf(2), draft.weeks.map { it.index })
        assertEquals(listOf(100.0), draft.weeks.single().days.single().exercises.map { it.loadKg })
        assertTrue(
            draft.warnings.any {
                it.kind == ImportWarningKind.SKIPPED_ROW &&
                    it.message.endsWith("«Levantamiento principal»: AMRAP sin número de repeticiones; se omite")
            },
        )
    }

    @Test
    fun `Levantamiento principal no se reconoce con confianza y queda para elegir`() {
        assertNull(ImportExerciseAliases.lookup(WeekBandBlocks.DEFAULT_LIFT))
        val match = ExerciseNameMatcher(loadCatalog()).match(WeekBandBlocks.DEFAULT_LIFT)
        assertFalse(match.isConfident)
    }

    private fun loadCatalog(): ExerciseCatalogV2 {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        return ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }
}
