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
        val sinReferencia = setOf(NutrientKey.SUGARS, NutrientKey.ADDED_SUGARS, NutrientKey.SAT_FAT, NutrientKey.MONO_FAT, NutrientKey.POLY_FAT,
            NutrientKey.TRANS_FAT, NutrientKey.CHOLESTEROL, NutrientKey.EPA_DHA, NutrientKey.CAFFEINE, NutrientKey.ALCOHOL)
        sinReferencia.forEach { assertNull(it.name, DriTargets.lookup(it, DriSex.FEMALE, 31)) }
        (NutrientKey.entries - sinReferencia).forEach { key ->
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
        fun withStage(stage: PregnancyLactation, age: Int = 31) = settings.copy(age = age, userVitals = settings.userVitals.copy(age = age, pregnancyLactation = stage))
        // Decisión F4-7: embarazo y lactancia son una etapa de vida con tabla propia, sin mirar el sexo del plan.
        val pregnant = DriTargets.profileFor(withStage(PregnancyLactation.PREGNANT), plan("FEMALE"))
        assertEquals(DriProfile(31, DriSex.FEMALE, DriStage.PREGNANT), pregnant)
        assertEquals(27.0, DriTargets.lookup(NutrientKey.IRON, pregnant)!!.value, 0.0)
        val lactating = DriTargets.profileFor(withStage(PregnancyLactation.LACTATING), plan("MALE"))
        assertEquals(DriProfile(31, DriSex.FEMALE, DriStage.LACTATING), lactating)
        assertEquals(290.0, DriTargets.lookup(NutrientKey.IODINE, lactating)!!.value, 0.0)
        assertNull(DriTargets.profileFor(withStage(PregnancyLactation.PREGNANT, age = 18), plan("FEMALE")))
    }
    @Test fun `las 36 filas F4 de fosforo colina y ALA`() {
        val phosphorus = DriTargets.lookup(NutrientKey.PHOSPHORUS, DriSex.FEMALE, 75)!!
        assertEquals(700.0, phosphorus.value, 0.0); assertEquals(DriKind.RDA, phosphorus.kind)
        assertEquals(3000.0, phosphorus.upperLimit!!, 0.0); assertEquals("mg", phosphorus.upperLimitUnit)
        assertEquals(UpperLimitScope.TOTAL, phosphorus.scope)
        assertFalse(phosphorus.exceedsKnownTotal(3000.0)); assertTrue(phosphorus.exceedsKnownTotal(3000.1))
        assertEquals(3500.0, DriTargets.lookup(NutrientKey.PHOSPHORUS, DriSex.FEMALE, 25, DriStage.PREGNANT)!!.upperLimit!!, 0.0)
        assertEquals(4000.0, DriTargets.lookup(NutrientKey.PHOSPHORUS, DriSex.FEMALE, 40, DriStage.LACTATING)!!.upperLimit!!, 0.0)
        assertEquals(4000.0, DriTargets.lookup(NutrientKey.PHOSPHORUS, DriSex.MALE, 60)!!.upperLimit!!, 0.0)
        val choline = DriTargets.lookup(NutrientKey.CHOLINE, DriSex.MALE, 40)!!
        assertEquals(550.0, choline.value, 0.0); assertEquals(DriKind.AI, choline.kind); assertEquals(3500.0, choline.upperLimit!!, 0.0)
        assertEquals(UpperLimitScope.TOTAL, choline.scope)
        assertEquals(425.0, DriTargets.lookup(NutrientKey.CHOLINE, DriSex.FEMALE, 40)!!.value, 0.0)
        assertEquals(450.0, DriTargets.lookup(NutrientKey.CHOLINE, DriSex.FEMALE, 40, DriStage.PREGNANT)!!.value, 0.0)
        assertEquals(550.0, DriTargets.lookup(NutrientKey.CHOLINE, DriSex.FEMALE, 40, DriStage.LACTATING)!!.value, 0.0)
        val ala = DriTargets.lookup(NutrientKey.ALA, DriSex.FEMALE, 31, DriStage.LACTATING)!!
        assertEquals(1.3, ala.value, 0.0); assertNull(ala.upperLimit); assertEquals(UpperLimitScope.NONE, ala.scope)
        assertEquals(1.6, DriTargets.lookup(NutrientKey.ALA, DriSex.MALE, 80)!!.value, 0.0)
        assertEquals(1.1, DriTargets.lookup(NutrientKey.ALA, DriSex.FEMALE, 52)!!.value, 0.0)
        assertEquals(1.4, DriTargets.lookup(NutrientKey.ALA, DriSex.FEMALE, 20, DriStage.PREGNANT)!!.value, 0.0)
        var count = 0
        listOf(NutrientKey.PHOSPHORUS, NutrientKey.CHOLINE, NutrientKey.ALA).forEach { key ->
            val groups = listOf(DriSex.MALE to DriStage.ADULT, DriSex.FEMALE to DriStage.ADULT, DriSex.FEMALE to DriStage.PREGNANT, DriSex.FEMALE to DriStage.LACTATING)
            groups.forEach { (sex, stage) ->
                val ages = if (stage == DriStage.ADULT) listOf(19, 30, 31, 50, 51, 70, 71, 95) else listOf(19, 30, 31, 50)
                ages.forEach { age -> assertNotNull("$key $sex $stage $age", DriTargets.lookup(key, sex, age, stage)) }
            }
            count += 12
        }
        assertEquals(36, count)
    }
    @Test fun `unidad de cada clave coincide con la de su NutrientId y no hay ids repetidos`() {
        NutrientKey.entries.forEach { assertEquals(it.name, it.nutrientId.unit, it.unit.substringBefore(' ')) }
        val ids = NutrientKey.entries.map { it.nutrientId }
        assertEquals(ids.size, ids.toSet().size)
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
