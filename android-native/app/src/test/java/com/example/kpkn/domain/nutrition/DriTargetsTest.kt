package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class DriTargetsTest {
    @Test fun `cada una de las 240 filas coincide en ambos bordes de edad`() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/dri/DRI_ADULTOS.csv")).use { it.readBytes() }
        assertEquals(DriTargets.CSV_SHA256, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val rows = bytes.toString(Charsets.UTF_8).lineSequence().drop(1).filter { it.isNotBlank() }.map(::csv).toList()
        assertEquals(240, rows.size)
        rows.forEach { c ->
            val key = NutrientKey.entries.single { it.csvName == c[0] }
            assertEquals(c[1], key.unit)
            val sex = if (c[2] == "Hombre") DriSex.MALE else DriSex.FEMALE
            val stage = when { "embarazo" in c[3] -> DriStage.PREGNANT; "lactancia" in c[3] -> DriStage.LACTATING; else -> DriStage.ADULT }
            val group = c[3].substringBefore(" (")
            val ages = if (group.startsWith(">")) listOf(71, 95) else group.split('-').map { it.toInt() }
            ages.forEach { age ->
                val value = requireNotNull(DriTargets.lookup(key, sex, age, stage))
                assertEquals(c[4], value.kind.name)
                assertEquals(c[5].toDouble(), value.value, 0.0)
                if (c[6] == "ND") assertNull(value.upperLimit) else {
                    assertEquals(c[6].substringBefore(' ').toDouble(), value.upperLimit!!, 0.0)
                    assertEquals(c[6].substringAfter(' '), value.upperLimitUnit)
                }
                assertEquals(c[8], value.sourceUrl)
            }
        }
    }
    @Test fun `solo los limites de ingesta total alertan y sodio usa CDRR no AI`() {
        NutrientKey.entries.forEach { key ->
            val target = requireNotNull(DriTargets.lookup(key, DriSex.FEMALE, 31))
            assertFalse(target.exceedsKnownTotal(null))
            assertFalse(target.exceedsKnownTotal(Double.NaN))
            target.upperLimit?.let { ul -> assertEquals(key.name, target.scope == UpperLimitScope.TOTAL, target.exceedsKnownTotal(ul * 2)) }
        }
        val sodium = DriTargets.lookup(NutrientKey.SODIUM, DriSex.MALE, 30)!!
        assertEquals(1500.0, sodium.value, 0.0)
        assertFalse(sodium.exceedsKnownTotal(1600.0))
        assertFalse(sodium.exceedsKnownTotal(2300.0))
        assertTrue(sodium.exceedsKnownTotal(2300.1))
    }
    @Test fun `sin edad sexo o adulto compatible no se inventan referencias`() {
        assertNull(DriTargets.lookup(NutrientKey.CALCIUM, null, 30))
        assertNull(DriTargets.lookup(NutrientKey.CALCIUM, DriSex.MALE, null))
        assertNull(DriTargets.lookup(NutrientKey.CALCIUM, DriSex.FEMALE, 18))
        assertNull(DriTargets.lookup(NutrientKey.IRON, DriSex.FEMALE, 51, DriStage.PREGNANT))
        val settings = Settings(age = 31, userVitals = UserVitals(gender = Gender.FEMALE))
        assertNull(DriTargets.profileFor(settings, null))
        fun plan(sex: String) = NutritionPlan(calculationSnapshot = NutritionPlanCalculationSnapshot("test", "test", inputs = mapOf("sex" to sex), calculatedAt = ""))
        assertEquals(DriProfile(31, DriSex.MALE), DriTargets.profileFor(settings, plan("MALE")))
        assertNull(DriTargets.profileFor(settings, plan("AVERAGE")))
        assertNull(DriTargets.profileFor(settings.copy(userVitals = settings.userVitals.copy(pregnancyLactation = PregnancyLactation.PREGNANT)), plan("FEMALE")))
    }
    private fun csv(line: String): List<String> {
        val fields = mutableListOf<String>(); val field = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when { c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields += field.toString(); field.setLength(0) }
                else -> field.append(c) }
            i++
        }
        return fields + field.toString()
    }
}
