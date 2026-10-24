package com.example.kpkn.data.export

import com.example.kpkn.data.models.*
import com.example.kpkn.data.importer.CsvReader
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.*
import java.util.zip.ZipInputStream

class CsvDomainRowsTest {
    private val day = LocalDate.of(2026, 10, 24)
    private fun rows(bundle: CsvExportBundle, name: String) = CsvReader.parse(bundle.files.single { name in it.name }.bytes.toString(Charsets.UTF_8), name).rows
    @Test fun comidasConservanHoraLiteralEstadoDelDiaRecetaYFilaSana() {
        val foods = listOf(LoggedFood(id = "one", foodName = "Comida, especial", amount = 0.5, unit = "ración", calories = 100.0, recipeId = "recipe", recipeVersion = 2),
            LoggedFood(id = "bad", amount = 10.0, calories = Double.NaN))
        val input = CsvExportSnapshot(meals = listOf(NutritionLog(id = "meal", date = "2026-10-24T23:30:00-03:00", foods = foods)), dayMarks = mapOf(day to DayMark.PARTIAL))
        val result = CsvDomainRows.build(input, setOf(CsvExportDomain.MEALS), day)
        val row = rows(result, "comidas")[1]
        assertEquals("2026-10-24", row[0]); assertEquals("23:30", row[1]); assertEquals("Comida, especial", row[3])
        assertEquals("0.5", row[4]); assertEquals("ración", row[5]); assertEquals("parcial", row[13]); assertEquals("receta", row[14])
        assertEquals(1, result.issues.size); assertEquals(2, rows(result, "comidas").size)
    }
    @Test fun ayunoSinComidaSeExportaComoMarcaNoComoAlimentoInventado() {
        val input = CsvExportSnapshot(dayMarks = mapOf(day to DayMark.FASTING))
        val row = rows(CsvDomainRows.build(input, setOf(CsvExportDomain.MEALS), day), "comidas")[1]
        assertEquals("", row[3]); assertEquals("0", row[6]); assertEquals("", row[10]); assertEquals("ayuno", row[13])
    }
    @Test fun cuerpoUsaDiaLocalYUnidadesDeAjustesConservandoSI() {
        val observation = BodyObservation("weight", BodyMetric.WEIGHT, 80.0, "kg", timestampEpochMs = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli())
        val input = CsvExportSnapshot(observations = listOf(observation), weightUnit = WeightUnit.LBS, zone = ZoneId.of("America/Santiago"))
        val row = rows(CsvDomainRows.build(input, setOf(CsvExportDomain.BODY), day), "cuerpo")[1]
        assertEquals("2026-10-24", row[0]); assertEquals("22:30", row[1]); assertEquals("lb", row[4]); assertEquals("80", row[5])
        assertEquals(80.0 / 0.45359237, row[3].toDouble(), 1e-12)
    }
    @Test fun entrenosExportanSeriesRIRLadoYNotasSinActivarFormulas() {
        val exercise = CompletedExercise("e", "Press, inclinado", sets = listOf(CompletedSet("s", weight = 80.5, reps = 6, rir = 2, side = "left", notes = "=SUM(A1)")))
        val log = WorkoutLog("w", "p", "s", "Día de fuerza", "2026-10-25T01:30:00Z", 45, completedExercises = listOf(exercise))
        val input = CsvExportSnapshot(workouts = listOf(log), zone = ZoneId.of("America/Santiago"))
        val row = rows(CsvDomainRows.build(input, setOf(CsvExportDomain.WORKOUTS), day), "entrenos")[1]
        assertEquals("2026-10-24", row[0]); assertEquals("Press, inclinado", row[2]); assertEquals("80.5", row[4])
        assertEquals("2", row[6]); assertEquals("true", row[8]); assertEquals("'=SUM(A1)", row[9]); assertEquals("left", row[10])
    }
    @Test fun bibliotecaCreaDosCSVYZIPSoloConNombresPropios() {
        val recipe = RecipeData(listOf(RecipeIngredient(LoggedFood(foodName = "Arroz", amount = 100.0, calories = 100.0))), servings = 2, finalWeightGrams = 150.0)
        val input = CsvExportSnapshot(foods = listOf(FoodItem(id = "f", name = "Receta propia", recipe = recipe)), exercises = listOf(ExerciseMuscleInfo("e", "Ejercicio propio")))
        val bundle = CsvDomainRows.build(input, setOf(CsvExportDomain.LIBRARY), day)
        assertTrue(bundle.isZip); assertEquals("kpkn-export-2026-10-24.zip", bundle.suggestedName)
        assertEquals("2", rows(bundle, "alimentos-propios")[1][12]); assertEquals("150", rows(bundle, "alimentos-propios")[1][13])
        val bytes = ByteArrayOutputStream().also(bundle::writeTo).toByteArray()
        val names = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; names += entry.name; assertTrue(zip.readBytes().isNotEmpty()); zip.closeEntry() } }
        assertEquals(bundle.files.map { it.name }, names)
    }
    @Test fun dominioVacioEntregaSoloCabeceraYNoIncluyeComidasPlanificadas() {
        val input = CsvExportSnapshot(meals = listOf(NutritionLog(status = NutritionStatus.PLANNED, foods = listOf(LoggedFood(amount = 100.0, calories = 100.0)))))
        val bundle = CsvDomainRows.build(input, setOf(CsvExportDomain.MEALS), day)
        assertFalse(bundle.isZip); assertEquals(1, rows(bundle, "comidas").size)
        assertEquals("kpkn-comidas-2026-10-24.csv", bundle.suggestedName)
    }
}
