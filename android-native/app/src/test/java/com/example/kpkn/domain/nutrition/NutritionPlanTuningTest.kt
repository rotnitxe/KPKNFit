package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Afinado del plan ([PlanTuning]): acoplamiento kcal ↔ macros ↔ ritmo en los dos sentidos, límites de los
 * deslizadores, zonas de ritmo, avisos con los umbrales de [buildNutritionRiskFlags] (por sexo), el
 * `hardStop` y los casos sin EER o sin peso. JVM puro.
 */
class NutritionPlanTuningTest {

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private fun context(
        weightKg: Double? = 80.0,
        eerKcal: Double? = 2650.0,
        direction: PlanDirection? = PlanDirection.DEFICIT,
        sex: EerSex? = EerSex.MALE,
    ) = PlanTuningContext(weightKg, eerKcal, direction, sex)

    private fun recommended(
        context: PlanTuningContext = context(),
        preset: WizardPacePreset = WizardPacePreset.MEDIUM,
    ): PlanTuning = requireNotNull(PlanTuning.recommended(context, preset)) { "sin recomendación para $context" }

    /** Plan con las kcal y los gramos dados, con la recomendación media de [profile] como base. */
    private fun planWith(
        profile: PlanTuningContext,
        kcal: Int,
        proteinG: Int = 160,
        carbsG: Int = 200,
        fatG: Int = 60,
    ): PlanTuning = recommended(profile).copy(values = PlanValues(kcal, proteinG, carbsG, fatG))

    private fun at(
        kcal: Int,
        weightKg: Double = 100.0,
        eerKcal: Double = 2800.0,
        direction: PlanDirection = PlanDirection.DEFICIT,
        sex: EerSex? = EerSex.MALE,
        proteinG: Int = 200,
        carbsG: Int = 250,
        fatG: Int = 80,
    ): PlanTuning = planWith(PlanTuningContext(weightKg, eerKcal, direction, sex), kcal, proteinG, carbsG, fatG)

    private fun assertAtwaterWithin(tolerance: Int, tuning: PlanTuning) {
        assertTrue(
            "kcal ${tuning.kcal} vs Atwater ${tuning.values.atwaterKcal}",
            abs(tuning.kcal - tuning.values.atwaterKcal) <= tolerance,
        )
    }

    // ─── Recomendación automática = la del motor ─────────────────────────────

    @Test
    fun `la recomendacion automatica coincide con el motor y con el editor en una cuadricula`() {
        var checked = 0
        for (sex in listOf(EerSex.MALE, EerSex.FEMALE)) {
            for (activity in EerActivity.entries) {
                for (weight in listOf(48.0, 62.5, 80.0, 101.3, 145.0)) {
                    for (direction in listOf(PlanDirection.DEFICIT, PlanDirection.MAINTENANCE, PlanDirection.SURPLUS)) {
                        val input = EerInput(ageYears = 31, heightCm = 172.0, weightKg = weight, sex = sex, activity = activity)
                        val recommendation = NutritionEnergyEngine.recommendPlan(input, direction)
                        val macros = requireNotNull(recommendation.macros)
                        val expected = PlanValues(
                            kcal = requireNotNull(recommendation.calorieTargetKcal),
                            proteinG = macros.proteinG.roundToInt().coerceAtLeast(0),
                            carbsG = macros.carbsG.roundToInt().coerceAtLeast(0),
                            fatG = macros.fatG.roundToInt().coerceAtLeast(0),
                        )
                        val context = PlanTuningContext(weight, recommendation.eerKcal, direction, sex)
                        assertEquals("$sex $activity $weight $direction", expected, automaticPlanValues(context))

                        // Y es la misma base que el editor muestra y guarda.
                        val editorBase = requireNotNull(
                            reviewedBaseOf(NutritionPlanEditorDraft(direction = direction), recommendation),
                        )
                        assertEquals(editorBase.caloriesKcal, expected.kcal)
                        assertEquals(editorBase.proteinG, expected.proteinG)
                        assertEquals(editorBase.carbsG, expected.carbsG)
                        assertEquals(editorBase.fatG, expected.fatG)
                        checked++
                    }
                }
            }
        }
        assertEquals(2 * 4 * 5 * 3, checked)
    }

    @Test
    fun `sin EER peso o direccion de plan no hay recomendacion`() {
        assertNull(automaticPlanValues(context(eerKcal = null)))
        assertNull(automaticPlanValues(context(weightKg = null)))
        assertNull(automaticPlanValues(context(weightKg = 0.0)))
        assertNull(automaticPlanValues(context(eerKcal = Double.NaN)))
        assertNull(automaticPlanValues(context(direction = null)))
        assertNull(automaticPlanValues(context(direction = PlanDirection.PROFESSIONAL)))
    }

    @Test
    fun `el preset medio es exactamente la recomendacion automatica`() {
        listOf(PlanDirection.DEFICIT, PlanDirection.SURPLUS, PlanDirection.MAINTENANCE).forEach { direction ->
            val ctx = context(direction = direction)
            assertEquals(automaticPlanValues(ctx), recommendedPlanValues(ctx, WizardPacePreset.MEDIUM))
        }
    }

    @Test
    fun `en mantenimiento ningun preset cambia la recomendacion`() {
        val ctx = context(direction = PlanDirection.MAINTENANCE)
        WizardPacePreset.entries.forEach { preset ->
            assertEquals(automaticPlanValues(ctx), recommendedPlanValues(ctx, preset))
        }
    }

    @Test
    fun `los presets ordenan las kcal de definir de mas a menos y las de volumen de menos a mas`() {
        val deficit = context(direction = PlanDirection.DEFICIT)
        val slow = recommendedPlanValues(deficit, WizardPacePreset.SLOW)!!.kcal
        val medium = recommendedPlanValues(deficit, WizardPacePreset.MEDIUM)!!.kcal
        val fast = recommendedPlanValues(deficit, WizardPacePreset.FAST)!!.kcal
        assertTrue("$slow > $medium > $fast", slow > medium && medium > fast)

        val surplus = context(direction = PlanDirection.SURPLUS)
        val slowUp = recommendedPlanValues(surplus, WizardPacePreset.SLOW)!!.kcal
        val mediumUp = recommendedPlanValues(surplus, WizardPacePreset.MEDIUM)!!.kcal
        val fastUp = recommendedPlanValues(surplus, WizardPacePreset.FAST)!!.kcal
        assertTrue("$slowUp < $mediumUp < $fastUp", slowUp < mediumUp && mediumUp < fastUp)
    }

    @Test
    fun `cada preset da el ritmo semanal de su tabla`() {
        val ctx = context()
        WizardPacePreset.entries.forEach { preset ->
            val tuning = recommended(ctx, preset)
            val expectedKg = -(paceRateFor(PlanDirection.DEFICIT, preset)!! * 80.0)
            assertEquals("$preset", expectedKg, tuning.weeklyChangeKg!!, 0.002)
        }
    }

    @Test
    fun `la semilla solo existe cuando el preset difiere de lo que el motor aplica`() {
        val ctx = context()
        assertNull(presetSeedValues(ctx, WizardPacePreset.MEDIUM))
        assertEquals(recommendedPlanValues(ctx, WizardPacePreset.SLOW), presetSeedValues(ctx, WizardPacePreset.SLOW))
        assertEquals(recommendedPlanValues(ctx, WizardPacePreset.FAST), presetSeedValues(ctx, WizardPacePreset.FAST))
        // Sin recomendación posible, sin semilla.
        assertNull(presetSeedValues(context(eerKcal = null), WizardPacePreset.FAST))
        assertNull(presetSeedValues(context(direction = PlanDirection.MAINTENANCE), WizardPacePreset.FAST))
    }

    // ─── Macros → kcal (Atwater) ────────────────────────────────────────────

    @Test
    fun `mover un macro recalcula las kcal con Atwater y no toca los otros dos`() {
        val base = recommended()
        val v = base.values

        val protein = base.withProtein(v.proteinG + 25)
        assertEquals(v.proteinG + 25, protein.proteinG)
        assertEquals(v.carbsG, protein.carbsG)
        assertEquals(v.fatG, protein.fatG)
        assertEquals(protein.values.atwaterKcal, protein.kcal)
        assertEquals(base.values.atwaterKcal + 100, protein.kcal)

        val carbs = base.withCarbs(v.carbsG - 40)
        assertEquals(v.carbsG - 40, carbs.carbsG)
        assertEquals(v.proteinG, carbs.proteinG)
        assertEquals(v.fatG, carbs.fatG)
        assertEquals(carbs.values.atwaterKcal, carbs.kcal)

        val fat = base.withFat(v.fatG + 10)
        assertEquals(v.fatG + 10, fat.fatG)
        assertEquals(v.proteinG, fat.proteinG)
        assertEquals(v.carbsG, fat.carbsG)
        assertEquals(fat.values.atwaterKcal, fat.kcal)
    }

    @Test
    fun `una cadena de ediciones de macros siempre deja las kcal en Atwater exacto`() {
        var tuning = recommended()
        listOf(
            { t: PlanTuning -> t.withProtein(180) },
            { t: PlanTuning -> t.withCarbs(150) },
            { t: PlanTuning -> t.withFat(55) },
            { t: PlanTuning -> t.withProtein(120) },
            { t: PlanTuning -> t.withFat(70) },
        ).forEach { step ->
            tuning = step(tuning)
            assertEquals(tuning.values.atwaterKcal, tuning.kcal)
        }
    }

    @Test
    fun `mover macros mueve el ritmo mostrado en el sentido esperado`() {
        val base = recommended()
        val more = base.withCarbs(base.carbsG + 50) // +200 kcal sobre la suma Atwater de la base
        assertEquals(base.values.atwaterKcal + 200, more.kcal)
        assertTrue(more.weeklyChangeKg!! > base.weeklyChangeKg!!)
        assertEquals(
            (more.kcal - base.kcal) * 7.0 / 7700.0,
            more.weeklyChangeKg!! - base.weeklyChangeKg!!,
            1e-12,
        )
        val less = base.withCarbs(base.carbsG - 50)
        assertTrue(less.weeklyChangeKg!! < base.weeklyChangeKg!!)
    }

    @Test
    fun `la energia maxima frena el macro que sube pero no impide bajar`() {
        val base = recommended() // definir: techo = EER (2650)
        val ceiling = base.limits.ceilingKcal
        assertEquals(2650, ceiling)
        val huge = base.withCarbs(10_000)
        assertTrue("kcal ${huge.kcal} ≤ techo $ceiling", huge.values.atwaterKcal <= ceiling)
        assertEquals(huge.reachableMaxOf(PlanMacro.CARBS), huge.carbsG)

        // Un valor propio por encima del techo no se puede subir más, pero sí bajar.
        val above = base.copy(values = PlanValues(3400, 160, 400, 120)) // Atwater 3320 > 2650
        assertEquals(400, above.withCarbs(450).carbsG)
        assertEquals(300, above.withCarbs(300).carbsG)
    }

    @Test
    fun `un macro por debajo de su minimo no sube por encima del techo de energia`() {
        val base = recommended(context(weightKg = 95.0, eerKcal = 3000.0)) // techo = EER (3000); grasas mínimas 48 g
        assertEquals(3000, base.limits.ceilingKcal)
        assertEquals(48, base.limits.fat.minG)

        // Hidratos al máximo con las grasas en 44 g (< 48): la suma Atwater está justo en el techo.
        val full = base.copy(values = PlanValues(3000, 85, 566, 44))
        assertEquals(3000, full.values.atwaterKcal)
        // Subirlas hasta su mínimo pasaría del techo (+36 kcal): se queda donde estaba, y lo mismo lo alcanzable.
        assertEquals(44, full.withFat(100).fatG)
        assertEquals(3000, full.withFat(100).values.atwaterKcal)
        assertEquals(44, full.reachableMaxOf(PlanMacro.FAT))

        // Con algo de energía libre, tocarlo lo sube hasta donde da (aquí 46 g de los 48 del mínimo).
        val little = base.copy(values = PlanValues(2976, 85, 560, 44))
        assertEquals(46, little.reachableMaxOf(PlanMacro.FAT))
        assertEquals(46, little.withFat(10).fatG)
        assertTrue(little.withFat(10).values.atwaterKcal <= 3000)

        // Con energía de sobra, tocarlo lo lleva al mínimo en cuanto se toca.
        val room = base.copy(values = PlanValues(2600, 85, 466, 44))
        assertEquals(48, room.withFat(10).fatG)
        assertEquals(88, room.withFat(500).fatG)
    }

    @Test
    fun `los limites de los deslizadores no cambian al mover los macros`() {
        val base = recommended()
        val moved = base.withProtein(200).withCarbs(120).withFat(45)
        assertEquals(base.limits, moved.limits)
    }

    @Test
    fun `los limites de proteina grasas e hidratos salen del peso y de la energia`() {
        val base = recommended() // 80 kg, definir, EER 2650, base ≈ 2210 kcal
        val limits = base.limits
        // Proteína: 0,8–3,0 g/kg.
        assertEquals(64, limits.protein.minG)
        assertEquals(240, limits.protein.maxG)
        // Grasas: mayor entre 0,5 g/kg (40) y el 15 % de la base; hasta el 45 % de la base.
        assertEquals(40, limits.fat.minG)
        assertEquals(floorToInt(0.45 * base.baseline.kcal / 9.0), limits.fat.maxG)
        // Hidratos: de 0 a lo que deja el techo con proteína y grasas mínimas.
        assertEquals(0, limits.carbs.minG)
        assertEquals(floorToInt((2650 - 4 * 64 - 9 * 40) / 4.0), limits.carbs.maxG)
    }

    @Test
    fun `las grasas minimas suben con la energia cuando el 15 por ciento supera medio gramo por kilo`() {
        // 60 kg → 0,5 g/kg = 30 g; base de 3000 kcal → 15 % = 50 g.
        val tuning = PlanTuning.of(
            context(weightKg = 60.0, eerKcal = 3000.0, direction = PlanDirection.MAINTENANCE),
            PlanValues(3000, 120, 400, 90),
        )
        assertEquals(50, tuning.limits.fat.minG)
    }

    @Test
    fun `el techo de energia depende de la direccion`() {
        val eer = 2650.0
        assertEquals(2650, PlanTuning.of(context(direction = PlanDirection.DEFICIT), PlanValues(2200, 160, 220, 60)).limits.ceilingKcal)
        assertEquals(2800, PlanTuning.of(context(direction = PlanDirection.MAINTENANCE), PlanValues(2650, 160, 300, 70)).limits.ceilingKcal)
        assertEquals(3150, PlanTuning.of(context(direction = PlanDirection.SURPLUS), PlanValues(2900, 160, 350, 80)).limits.ceilingKcal)
        assertEquals(
            calorieBoundsFor(PlanDirection.SURPLUS, eer)!!.last,
            PlanTuning.of(context(direction = null), PlanValues(2650, 160, 300, 70)).limits.ceilingKcal,
        )
    }

    @Test
    fun `sin EER el techo sale de la base y sin peso los limites usan un peso nominal`() {
        val noEer = PlanTuning.of(context(eerKcal = null), PlanValues(2100, 165, 180, 65))
        assertEquals(3600, noEer.limits.ceilingKcal)
        val small = PlanTuning.of(context(eerKcal = null), PlanValues(1500, 100, 140, 40))
        assertEquals(3500, small.limits.ceilingKcal)

        val noWeight = PlanTuning.of(context(weightKg = null), PlanValues(2100, 165, 180, 65))
        assertEquals(56, noWeight.limits.protein.minG) // 0,8 × 70
        assertEquals(210, noWeight.limits.protein.maxG) // 3,0 × 70
        assertNull(noWeight.proteinPerKg)
    }

    @Test
    fun `los limites por kilo coinciden con la aritmetica exacta para todos los pesos`() {
        // EER enorme para que la energía máxima no recorte nada: solo cuenta el peso.
        for (tenths in 300..2500) {
            val weight = tenths / 10.0
            val ctx = PlanTuningContext(weight, 90_000.0, PlanDirection.MAINTENANCE, EerSex.MALE)
            val limits = PlanTuning.of(ctx, PlanValues(2200, 150, 250, 70)).limits
            val proteinMin = BigDecimal(tenths).multiply(BigDecimal("0.08")).setScale(0, RoundingMode.CEILING).toInt()
            val proteinMax = BigDecimal(tenths).multiply(BigDecimal("0.3")).setScale(0, RoundingMode.FLOOR).toInt()
            val fatMinByWeight = BigDecimal(tenths).multiply(BigDecimal("0.05")).setScale(0, RoundingMode.CEILING).toInt()
            assertEquals("proteína mínima con $weight kg", proteinMin, limits.protein.minG)
            assertEquals("proteína máxima con $weight kg", proteinMax, limits.protein.maxG)
            // 15 % de 2.200 kcal = 330 kcal = 36,67 g → 37 g.
            assertEquals("grasa mínima con $weight kg", maxOf(fatMinByWeight, 37), limits.fat.minG)
        }
    }

    @Test
    fun `un valor fuera de rango se lleva al rango en cuanto se toca`() {
        val own = PlanTuning.of(context(), PlanValues(1500, 40, 180, 50)) // 40 g < 0,8 g/kg (64)
        assertEquals(40, own.proteinG)
        assertEquals(64, own.withProtein(50).proteinG)
        assertEquals(100, own.withProtein(100).proteinG)
    }

    @Test
    fun `todos los rangos son validos para pesos extremos`() {
        listOf(30.0, 45.0, 70.0, 120.0, 250.0).forEach { weight ->
            listOf(PlanDirection.DEFICIT, PlanDirection.MAINTENANCE, PlanDirection.SURPLUS, null).forEach { direction ->
                val ctx = PlanTuningContext(weight, 1500.0 + weight * 12, direction, EerSex.FEMALE)
                val tuning = PlanTuning.of(ctx, PlanValues(1800, 100, 200, 60))
                val limits = tuning.limits
                PlanMacro.entries.forEach { macro ->
                    val range = limits.of(macro)
                    assertTrue("$weight $direction $macro $range", range.minG <= range.maxG && range.minG >= 0)
                    // Tocar con cualquier valor nunca lanza ni sale del rango alcanzable.
                    listOf(-50, 0, 10_000).forEach { grams ->
                        val moved = tuning.withMacro(macro, grams)
                        assertTrue(moved.values.gramsOf(macro) >= minOf(range.minG, tuning.values.gramsOf(macro)))
                    }
                }
            }
        }
    }

    // ─── Ritmo → kcal → macros ──────────────────────────────────────────────

    @Test
    fun `el control de ritmo solo existe al definir o al hacer volumen con EER y peso`() {
        assertNotNull(recommended(context(direction = PlanDirection.DEFICIT)).paceControl)
        assertNotNull(recommended(context(direction = PlanDirection.SURPLUS)).paceControl)
        assertNull(recommended(context(direction = PlanDirection.MAINTENANCE)).paceControl)
        assertNull(PlanTuning.of(context(eerKcal = null), PlanValues(2100, 165, 180, 65)).paceControl)
        assertNull(PlanTuning.of(context(weightKg = null), PlanValues(2100, 165, 180, 65)).paceControl)
        assertNull(PlanTuning.of(context(direction = null), PlanValues(2100, 165, 180, 65)).paceControl)
        assertNull(PlanTuning.of(context(direction = PlanDirection.PROFESSIONAL), PlanValues(2100, 165, 180, 65)).paceControl)
    }

    @Test
    fun `las muescas son los presets en kg por semana y el maximo sale de los limites de calorias`() {
        val deficit = requireNotNull(recommended().paceControl)
        assertEquals(PlanDirection.DEFICIT, deficit.direction)
        assertEquals(listOf(WizardPacePreset.SLOW, WizardPacePreset.MEDIUM, WizardPacePreset.FAST), deficit.notches.map { it.preset })
        assertDoublesClose(listOf(0.2, 0.4, 0.64), deficit.notches.map { it.kgPerWeek })
        // EER 2650 → suelo 1750 (EER − 900): 900 kcal/día = 0,818 kg/sem.
        assertEquals(900 * 7.0 / 7700.0, deficit.maxKgPerWeek, 1e-9)

        val surplus = requireNotNull(recommended(context(direction = PlanDirection.SURPLUS)).paceControl)
        assertEquals(500 * 7.0 / 7700.0, surplus.maxKgPerWeek, 1e-9)
        assertDoublesClose(listOf(0.12, 0.2, 0.32), surplus.notches.map { it.kgPerWeek })
    }

    @Test
    fun `una muesca que no cabe en el rango no se ofrece`() {
        // 130 kg: el preset rápido (0,8 % = 1,04 kg/sem) supera lo que permiten los límites (0,818).
        val heavy = recommended(context(weightKg = 130.0, eerKcal = 3400.0))
        val control = requireNotNull(heavy.paceControl)
        assertEquals(listOf(WizardPacePreset.SLOW, WizardPacePreset.MEDIUM), control.notches.map { it.preset })
        // Y la recomendación del preset rápido se queda en el límite.
        val fast = recommended(context(weightKg = 130.0, eerKcal = 3400.0), WizardPacePreset.FAST)
        assertEquals(calorieBoundsFor(PlanDirection.DEFICIT, 3400.0)!!.first, fast.kcal)
    }

    @Test
    fun `mover el ritmo fija las kcal en EER menos el ajuste y reescala los tres macros`() {
        val base = recommended()
        val moved = base.withPaceRate(0.6) // 0,6 kg/sem = 660 kcal/día
        assertEquals((2650.0 - 0.6 * 7700.0 / 7.0).roundToInt(), moved.kcal)
        assertTrue(moved.kcal < base.kcal)
        // Los tres macros bajan en proporción y la suma Atwater cuadra con las kcal (±2).
        assertTrue(moved.proteinG < base.proteinG && moved.carbsG < base.carbsG && moved.fatG < base.fatG)
        assertAtwaterWithin(2, moved)
        val ratio = moved.kcal.toDouble() / base.values.atwaterKcal
        assertEquals(base.proteinG * ratio, moved.proteinG.toDouble(), 1.0)
        assertEquals(base.fatG * ratio, moved.fatG.toDouble(), 1.0)
    }

    @Test
    fun `ritmo y kcal son coherentes en los dos sentidos`() {
        val base = recommended()
        // Ritmo → kcal → ritmo mostrado.
        listOf(0.0, 0.1, 0.25, 0.4, 0.64, 0.8).forEach { magnitude ->
            val moved = base.withPaceRate(magnitude)
            assertEquals("ritmo $magnitude", -magnitude, moved.weeklyChangeKg!!, 0.0006)
        }
        // Kcal (por macros) → ritmo: el mismo delta de kcal da el mismo delta de ritmo.
        val shifted = base.withFat(base.fatG + 11) // +99 kcal sobre la suma Atwater de la base
        assertEquals(base.values.atwaterKcal + 99, shifted.kcal)
        assertEquals(
            (shifted.kcal - base.kcal) * 7.0 / 7700.0,
            shifted.weeklyChangeKg!! - base.weeklyChangeKg!!,
            1e-12,
        )
    }

    @Test
    fun `el ritmo de volumen sube las kcal por encima del mantenimiento`() {
        val surplus = recommended(context(direction = PlanDirection.SURPLUS))
        val moved = surplus.withPaceRate(0.3)
        assertEquals((2650.0 + 0.3 * 7700.0 / 7.0).roundToInt(), moved.kcal)
        assertEquals(0.3, moved.weeklyChangeKg!!, 0.0006)
        assertEquals(1.0, moved.weeklyPercentBodyWeight!! / (0.3 / 80.0 * 100.0), 0.01)
    }

    @Test
    fun `el ritmo se limita a los limites de calorias`() {
        val base = recommended()
        val tooFast = base.withPaceRate(10.0)
        assertEquals(calorieBoundsFor(PlanDirection.DEFICIT, 2650.0)!!.first, tooFast.kcal)
        val negative = base.withPaceRate(-1.0)
        assertEquals(2650, negative.kcal) // 0 kg/sem = mantenimiento
        assertSame(base, base.withPaceRate(Double.NaN))

        val surplus = recommended(context(direction = PlanDirection.SURPLUS))
        assertEquals(calorieBoundsFor(PlanDirection.SURPLUS, 2650.0)!!.last, surplus.withPaceRate(10.0).kcal)
    }

    @Test
    fun `sin control de ritmo mover el ritmo no hace nada`() {
        val maintenance = recommended(context(direction = PlanDirection.MAINTENANCE))
        assertSame(maintenance, maintenance.withPaceRate(0.3))
        val noEer = PlanTuning.of(context(eerKcal = null), PlanValues(2100, 165, 180, 65))
        assertSame(noEer, noEer.withPaceRate(0.3))
        assertSame(noEer, noEer.withPreset(WizardPacePreset.FAST))
    }

    @Test
    fun `un cero manual de hidratos sobrevive al cambio de ritmo`() {
        val keto = PlanTuning(
            context(),
            baseline = PlanValues(2200, 160, 0, 175), // Atwater 2215
            values = PlanValues(2200, 160, 0, 175),
        )
        val moved = keto.withPaceRate(0.6)
        assertEquals(0, moved.carbsG)
        assertTrue(abs(moved.kcal - moved.values.atwaterKcal) <= 3)
    }

    @Test
    fun `una muesca aplica el mismo plan que la recomendacion de ese preset`() {
        val base = recommended()
        WizardPacePreset.entries.forEach { preset ->
            val viaNotch = base.withPreset(preset)
            val viaRecommendation = recommended(preset = preset)
            assertEquals("kcal $preset", viaRecommendation.kcal, viaNotch.kcal)
            // Misma cuenta de reescalado desde la misma base: los gramos coinciden (±1 por el redondeo de la base).
            assertTrue(abs(viaRecommendation.proteinG - viaNotch.proteinG) <= 1)
            assertTrue(abs(viaRecommendation.carbsG - viaNotch.carbsG) <= 1)
            assertTrue(abs(viaRecommendation.fatG - viaNotch.fatG) <= 1)
        }
    }

    @Test
    fun `la proyeccion es el ritmo por las semanas`() {
        val tuning = recommended().withPaceRate(0.45)
        assertEquals(tuning.weeklyChangeKg!! * 8, tuning.projectedChangeKg(8)!!, 1e-9)
        assertNull(PlanTuning.of(context(eerKcal = null), PlanValues(2100, 165, 180, 65)).projectedChangeKg(8))
    }

    // ─── Restablecer ────────────────────────────────────────────────────────

    @Test
    fun `restablecer vuelve a la recomendacion y isEdited lo refleja`() {
        val base = recommended()
        assertFalse(base.isEdited)
        val edited = base.withProtein(base.proteinG + 30).withPaceRate(0.7)
        assertTrue(edited.isEdited)
        val back = edited.reset()
        assertEquals(base.values, back.values)
        assertFalse(back.isEdited)
    }

    @Test
    fun `volver a mano a los mismos numeros tambien deja de estar editado`() {
        val base = recommended()
        val roundTrip = base.withProtein(base.proteinG + 5).withProtein(base.proteinG)
        // La suma Atwater de la recomendación puede diferir unas kcal de su total (cada macro se redondea
        // por separado): al volver a los mismos gramos vuelven también las kcal de la base.
        assertEquals(base.values, roundTrip.values)
        assertFalse(roundTrip.isEdited)
        val viaThree = base.withFat(base.fatG + 3).withCarbs(base.carbsG - 4).withFat(base.fatG).withCarbs(base.carbsG)
        assertEquals(base.values, viaThree.values)
    }

    // ─── Zonas de ritmo ─────────────────────────────────────────────────────

    @Test
    fun `las zonas al perder cortan en 0,5 1,0 y 1,5 kg por semana`() {
        // 100 kg, EER 2800: 1 kg/sem = 1100 kcal/día.
        assertEquals(PaceZone.NONE, at(2800).paceZone)
        assertEquals(PaceZone.NONE, at(2800 - 54).paceZone)
        assertEquals(PaceZone.SUSTAINABLE, at(2800 - 55).paceZone) // 0,05 kg/sem
        assertEquals(PaceZone.SUSTAINABLE, at(2250).paceZone) // −0,5 exacto
        assertEquals(PaceZone.DEMANDING, at(2249).paceZone)
        assertEquals(PaceZone.DEMANDING, at(1700).paceZone) // −1,0 exacto
        assertEquals(PaceZone.AGGRESSIVE, at(1699).paceZone)
        assertEquals(PaceZone.AGGRESSIVE, at(1150).paceZone) // −1,5 exacto
        assertEquals(PaceZone.EXTREME, at(1149).paceZone)
    }

    @Test
    fun `las zonas al ganar cortan en 0,25 0,5 y 0,75 kg por semana`() {
        val up = PlanDirection.SURPLUS
        assertEquals(PaceZone.SUSTAINABLE, at(3075, direction = up).paceZone) // +0,25 exacto
        assertEquals(PaceZone.DEMANDING, at(3076, direction = up).paceZone)
        assertEquals(PaceZone.DEMANDING, at(3350, direction = up).paceZone) // +0,5 exacto
        assertEquals(PaceZone.AGGRESSIVE, at(3351, direction = up).paceZone)
        assertEquals(PaceZone.AGGRESSIVE, at(3625, direction = up).paceZone) // +0,75 exacto
        assertEquals(PaceZone.EXTREME, at(3626, direction = up).paceZone)
    }

    @Test
    fun `sin EER o sin peso la zona es NONE y no hay ritmo`() {
        val noEer = PlanTuning.of(context(eerKcal = null), PlanValues(900, 80, 50, 40))
        assertNull(noEer.weeklyChangeKg)
        assertNull(noEer.weeklyPercentBodyWeight)
        assertEquals(PaceZone.NONE, noEer.paceZone)
        val noWeight = PlanTuning.of(context(weightKg = null), PlanValues(900, 80, 50, 40))
        assertNull(noWeight.weeklyChangeKg)
        assertEquals(PaceZone.NONE, noWeight.paceZone)
    }

    @Test
    fun `el ritmo semanal en porcentaje del peso es el real del motor`() {
        val tuning = at(2250) // −0,5 kg/sem con 100 kg
        assertEquals(-0.5, tuning.weeklyPercentBodyWeight!!, 1e-9)
        assertEquals(realRateFor(2250, 2800.0, 100.0)!! * 100.0, tuning.weeklyPercentBodyWeight!!, 1e-12)
        assertEquals(weeklyChangeFor(GoalMetric.WEIGHT, 2250, 2800.0, 100.0), tuning.weeklyChangeKg!!, 1e-12)
    }

    // ─── Avisos ─────────────────────────────────────────────────────────────

    @Test
    fun `las calorias bajas avisan con los umbrales de cada sexo`() {
        fun warnings(kcal: Int, sex: EerSex?) =
            at(kcal, direction = PlanDirection.MAINTENANCE, eerKcal = kcal.toDouble(), sex = sex).warnings

        // Hombre y sexo desconocido: suave < 1500, duro < 1200.
        listOf<EerSex?>(EerSex.MALE, null).forEach { sex ->
            assertTrue(PlanWarning.LOW_CALORIES_SOFT in warnings(1499, sex))
            assertTrue(PlanWarning.LOW_CALORIES_SOFT !in warnings(1500, sex))
            assertTrue(PlanWarning.LOW_CALORIES_SOFT in warnings(1200, sex))
            assertTrue(PlanWarning.LOW_CALORIES_HARD in warnings(1199, sex))
            assertTrue(PlanWarning.LOW_CALORIES_SOFT !in warnings(1199, sex)) // la dura sustituye a la suave
        }
        // Mujer: suave < 1200, dura < 1000.
        assertTrue(PlanWarning.LOW_CALORIES_SOFT in warnings(1199, EerSex.FEMALE))
        assertTrue(PlanWarning.LOW_CALORIES_SOFT !in warnings(1200, EerSex.FEMALE))
        assertTrue(PlanWarning.LOW_CALORIES_SOFT in warnings(1000, EerSex.FEMALE))
        assertTrue(PlanWarning.LOW_CALORIES_HARD in warnings(999, EerSex.FEMALE))
        assertTrue(PlanWarning.LOW_CALORIES_SOFT !in warnings(999, EerSex.FEMALE))
    }

    @Test
    fun `los umbrales de calorias coinciden con buildNutritionRiskFlags para cada kcal y sexo`() {
        for (female in listOf(true, false)) {
            val gender = if (female) Gender.FEMALE else Gender.MALE
            val sex = if (female) EerSex.FEMALE else EerSex.MALE
            for (kcal in 700..2000) {
                val flags = buildNutritionRiskFlags(
                    RiskInput(
                        settings = NutritionInput(70.0, 170.0, 30, gender),
                        calorieTarget = kcal,
                        goalMetric = GoalMetric.WEIGHT,
                        goalValue = 70.0,
                        weeklyChangeKg = 0.0,
                    ),
                )
                val ours = at(kcal, weightKg = 70.0, eerKcal = kcal.toDouble(), direction = PlanDirection.MAINTENANCE, sex = sex).warnings
                val label = "kcal=$kcal female=$female"
                assertEquals(label, flags.any { it.code == "calories_extreme_low" }, PlanWarning.LOW_CALORIES_HARD in ours)
                assertEquals(label, flags.any { it.code == "calories_low" }, PlanWarning.LOW_CALORIES_SOFT in ours)
            }
        }
    }

    @Test
    fun `los umbrales de ritmo coinciden con buildNutritionRiskFlags al perder y al ganar`() {
        // 100 kg y EER 4000 dejan sitio a todo el recorrido sin tocar los avisos de calorías.
        for (kcal in 3900 downTo 1500 step 7) {
            val ours = at(kcal, eerKcal = 4000.0, direction = PlanDirection.DEFICIT).warnings
            val change = abs((kcal - 4000.0) * 7.0 / 7700.0)
            val flags = buildNutritionRiskFlags(
                RiskInput(
                    settings = NutritionInput(100.0, 180.0, 30, Gender.MALE),
                    calorieTarget = 3000,
                    goalMetric = GoalMetric.WEIGHT,
                    goalValue = 90.0,
                    weeklyChangeKg = change,
                    calorieGoal = CalorieGoal.LOSE,
                ),
            )
            val label = "pérdida kcal=$kcal (${"%.3f".format(change)} kg/sem)"
            assertEquals(label, flags.any { it.code == "pace_extreme" }, PlanWarning.PACE_EXTREME in ours)
            assertEquals(label, flags.any { it.code == "pace_aggressive" }, PlanWarning.PACE_AGGRESSIVE in ours)
        }
        for (kcal in 4100..5200 step 5) {
            val ours = at(kcal, eerKcal = 4000.0, direction = PlanDirection.SURPLUS).warnings
            val change = (kcal - 4000.0) * 7.0 / 7700.0
            val flags = buildNutritionRiskFlags(
                RiskInput(
                    settings = NutritionInput(100.0, 180.0, 30, Gender.MALE),
                    calorieTarget = 3000,
                    goalMetric = GoalMetric.WEIGHT,
                    goalValue = 110.0,
                    weeklyChangeKg = change,
                    calorieGoal = CalorieGoal.GAIN,
                ),
            )
            val label = "ganancia kcal=$kcal (${"%.3f".format(change)} kg/sem)"
            assertEquals(label, flags.any { it.code == "pace_gain_extreme" }, PlanWarning.PACE_EXTREME in ours)
            assertEquals(label, flags.any { it.code == "pace_gain_aggressive" }, PlanWarning.PACE_AGGRESSIVE in ours)
        }
    }

    @Test
    fun `el hardStop sigue el criterio de buildNutritionRiskFlags`() {
        // Calorías duras y pérdida extrema detienen; una ganancia extrema avisa pero no detiene.
        assertTrue(at(1100, eerKcal = 1500.0, direction = PlanDirection.MAINTENANCE).hardStop)
        assertTrue(at(1149).hardStop) // −1,5+ kg/sem y < 1200 kcal
        assertTrue(at(1600, eerKcal = 3500.0).hardStop) // pérdida > 1,5 kg/sem con calorías «normales»
        assertFalse(at(1700).hardStop) // −1,0 exacto: solo exigente
        assertFalse(at(1699).hardStop) // agresivo, no extremo
        val extremeGain = at(3626, direction = PlanDirection.SURPLUS)
        assertTrue(PlanWarning.PACE_EXTREME in extremeGain.warnings)
        assertFalse(extremeGain.hardStop)
        assertFalse(recommended().hardStop)
    }

    @Test
    fun `la gravedad de los avisos es la de los avisos de riesgo`() {
        assertEquals(RiskSeverity.DANGER, PlanWarning.LOW_CALORIES_HARD.severity)
        assertEquals(RiskSeverity.DANGER, PlanWarning.PACE_EXTREME.severity)
        assertEquals(RiskSeverity.WARNING, PlanWarning.LOW_CALORIES_SOFT.severity)
        assertEquals(RiskSeverity.WARNING, PlanWarning.PACE_AGGRESSIVE.severity)
        assertEquals(RiskSeverity.WARNING, PlanWarning.LOW_PROTEIN.severity)
        assertEquals(RiskSeverity.WARNING, PlanWarning.LOW_FAT.severity)
    }

    @Test
    fun `los avisos salen de mas a menos graves`() {
        val extreme = at(1000, proteinG = 90, carbsG = 40, fatG = 53) // calorías duras + ritmo extremo + proteína baja
        val warnings = extreme.warnings
        assertEquals(
            listOf(PlanWarning.LOW_CALORIES_HARD, PlanWarning.PACE_EXTREME, PlanWarning.LOW_PROTEIN),
            warnings,
        )
        assertTrue(warnings.zipWithNext().all { (a, b) -> a.severity.ordinal >= b.severity.ordinal })
    }

    @Test
    fun `la proteina baja solo avisa en deficit`() {
        // 100 kg: 1,2 g/kg = 120 g.
        assertTrue(PlanWarning.LOW_PROTEIN in at(2200, proteinG = 119).warnings)
        assertTrue(PlanWarning.LOW_PROTEIN !in at(2200, proteinG = 120).warnings)
        // En mantenimiento o volumen no avisa por tener poca proteína.
        assertTrue(PlanWarning.LOW_PROTEIN !in at(2800, direction = PlanDirection.MAINTENANCE, proteinG = 80).warnings)
        assertTrue(PlanWarning.LOW_PROTEIN !in at(3200, direction = PlanDirection.SURPLUS, proteinG = 80).warnings)
        // Un déficit de verdad (bajo el mantenimiento más 50 kcal) lo activa aunque la dirección sea otra.
        assertTrue(PlanWarning.LOW_PROTEIN in at(2500, direction = PlanDirection.MAINTENANCE, proteinG = 80).warnings)
        assertTrue(PlanWarning.LOW_PROTEIN !in at(2760, direction = PlanDirection.MAINTENANCE, proteinG = 80).warnings)
    }

    @Test
    fun `sin EER la proteina baja usa la direccion elegida y sin peso no avisa`() {
        val noEer = PlanTuning.of(context(eerKcal = null, direction = PlanDirection.DEFICIT), PlanValues(1800, 70, 200, 60))
        assertTrue(PlanWarning.LOW_PROTEIN in noEer.warnings) // 70/80 = 0,88 g/kg
        val noEerKeep = PlanTuning.of(context(eerKcal = null, direction = PlanDirection.MAINTENANCE), PlanValues(1800, 70, 200, 60))
        assertTrue(PlanWarning.LOW_PROTEIN !in noEerKeep.warnings)
        val noWeight = PlanTuning.of(context(weightKg = null), PlanValues(1800, 10, 200, 60))
        assertTrue(PlanWarning.LOW_PROTEIN !in noWeight.warnings)
    }

    @Test
    fun `las grasas bajo el 20 por ciento de la energia avisan`() {
        // Atwater = 4·150 + 4·300 + 9·g
        fun fatShare(g: Int): Double = 9.0 * g / (4 * 150 + 4 * 300 + 9 * g)
        assertTrue(fatShare(45) < 0.20) // 405 / 2205 = 18,4 %
        assertTrue(fatShare(55) > 0.20) // 495 / 2295 = 21,6 %
        assertTrue(PlanWarning.LOW_FAT in at(2205, proteinG = 150, carbsG = 300, fatG = 45).warnings)
        assertTrue(PlanWarning.LOW_FAT !in at(2295, proteinG = 150, carbsG = 300, fatG = 55).warnings)
        // Sin energía no hay porcentaje que avisar.
        assertTrue(PlanWarning.LOW_FAT !in PlanTuning.of(context(), PlanValues(0, 0, 0, 0)).warnings)
    }

    @Test
    fun `la recomendacion del motor no dispara ningun aviso para un perfil normal`() {
        listOf(PlanDirection.DEFICIT, PlanDirection.MAINTENANCE, PlanDirection.SURPLUS).forEach { direction ->
            listOf(WizardPacePreset.SLOW, WizardPacePreset.MEDIUM).forEach { preset ->
                val tuning = recommended(context(direction = direction), preset)
                assertTrue("$direction $preset → ${tuning.warnings}", tuning.warnings.isEmpty())
            }
        }
    }

    // ─── Porcentajes y derivados ────────────────────────────────────────────

    @Test
    fun `los porcentajes de energia suman siempre 100`() {
        for (p in listOf(0, 1, 37, 120, 163, 250)) {
            for (c in listOf(0, 5, 99, 250, 411)) {
                for (f in listOf(0, 7, 55, 70, 133)) {
                    val (a, b, d) = percentsOfEnergy(p, c, f)
                    val energy = 4 * p + 4 * c + 9 * f
                    if (energy == 0) {
                        assertEquals(Triple(0, 0, 0), Triple(a, b, d))
                    } else {
                        assertEquals("$p/$c/$f", 100, a + b + d)
                        // Cada porcentaje queda a menos de 1 punto del exacto.
                        assertTrue(abs(a - 400.0 * p / (4 * p + 4 * c + 9 * f) * 1.0) <= 1.0 + 1e-9)
                    }
                }
            }
        }
    }

    @Test
    fun `kcal por macro y porcentajes del plan`() {
        val tuning = PlanTuning.of(context(), PlanValues(2300, 160, 250, 70))
        assertEquals(640, tuning.proteinKcal)
        assertEquals(1000, tuning.carbsKcal)
        assertEquals(630, tuning.fatKcal)
        assertEquals(2270, tuning.values.atwaterKcal)
        assertEquals(100, tuning.proteinPercent + tuning.carbsPercent + tuning.fatPercent)
        assertEquals(28, tuning.proteinPercent)
        assertEquals(44, tuning.carbsPercent)
        assertEquals(28, tuning.fatPercent)
        assertEquals(2.0, tuning.proteinPerKg!!, 1e-9)
    }

    @Test
    fun `las kcal propias incoherentes se conservan hasta que se mueve un macro`() {
        val tuning = PlanTuning.of(context(), PlanValues(2300, 160, 250, 70)) // Atwater 2270 ≠ 2300
        assertEquals(2300, tuning.kcal)
        val moved = tuning.withProtein(170)
        assertEquals(2310, moved.kcal) // al tocar, el total se rederiva: 4·170 + 4·250 + 9·70
        // Volver a los gramos de la base devuelve sus kcal propias.
        assertEquals(tuning.values, moved.withProtein(160).values)
    }

    // ─── Caminata aleatoria de ediciones ────────────────────────────────────

    @Test
    fun `una caminata aleatoria de ediciones respeta los invariantes del plan`() {
        val random = kotlin.random.Random(20261006)
        val contexts = listOf(
            context(),
            context(weightKg = 55.0, eerKcal = 1750.0, sex = EerSex.FEMALE),
            context(weightKg = 120.0, eerKcal = 3300.0, direction = PlanDirection.SURPLUS),
            context(weightKg = 70.0, eerKcal = 2400.0, direction = PlanDirection.MAINTENANCE, sex = null),
            context(weightKg = 95.0, eerKcal = 3000.0, direction = PlanDirection.DEFICIT, sex = EerSex.MALE),
        )
        repeat(250) {
            var tuning = recommended(contexts.random(random))
            val limits = tuning.limits
            repeat(40) {
                val before = tuning
                val op = random.nextInt(7)
                var step = ""
                tuning = when (op) {
                    0 -> {
                        val grams = random.nextInt(-20, 420)
                        step = "withProtein($grams)"
                        before.withProtein(grams)
                    }
                    1 -> {
                        val grams = random.nextInt(-20, 720)
                        step = "withCarbs($grams)"
                        before.withCarbs(grams)
                    }
                    2 -> {
                        val grams = random.nextInt(-20, 220)
                        step = "withFat($grams)"
                        before.withFat(grams)
                    }
                    3 -> {
                        val rate = random.nextDouble(-0.3, 1.3)
                        step = "withPaceRate($rate)"
                        before.withPaceRate(rate)
                    }
                    4 -> {
                        val preset = WizardPacePreset.entries.random(random)
                        step = "withPreset($preset)"
                        before.withPreset(preset)
                    }
                    5 -> {
                        step = "reset()"
                        before.reset()
                    }
                    else -> {
                        val macro = PlanMacro.entries.random(random)
                        val grams = random.nextInt(0, 400)
                        step = "withMacro($macro, $grams)"
                        before.withMacro(macro, grams)
                    }
                }
                val v = tuning.values
                val where = "${before.context} · $step: ${before.values} → $v (techo ${limits.ceilingKcal})"
                assertTrue("sin negativos: $where", v.proteinG >= 0 && v.carbsG >= 0 && v.fatG >= 0 && v.kcal >= 0)
                assertEquals("los límites no se mueven", limits, tuning.limits)
                assertEquals(before.baseline, tuning.baseline)
                // La energía queda bajo el techo (el rescalado admite unos pocos kcal de redondeo).
                assertTrue("Atwater ${v.atwaterKcal} > techo ${limits.ceilingKcal}: $where", v.atwaterKcal <= limits.ceilingKcal + 3)
                // Mover un macro a mano jamás sube la suma por encima del techo, ni por encima de donde estaba si ya lo superaba.
                if (op == 0 || op == 1 || op == 2 || op == 6) {
                    assertTrue(
                        "un macro movido sube la energía de ${before.values.atwaterKcal} a ${v.atwaterKcal}: $where",
                        v.atwaterKcal <= maxOf(before.values.atwaterKcal, limits.ceilingKcal),
                    )
                }

                // Avisos, zona y hardStop se explican entre sí.
                val warnings = tuning.warnings
                val zone = tuning.paceZone
                assertEquals(zone == PaceZone.EXTREME, PlanWarning.PACE_EXTREME in warnings)
                assertEquals(zone == PaceZone.AGGRESSIVE, PlanWarning.PACE_AGGRESSIVE in warnings)
                assertFalse(PlanWarning.LOW_CALORIES_HARD in warnings && PlanWarning.LOW_CALORIES_SOFT in warnings)
                val loss = (tuning.weeklyChangeKg ?: 0.0) < 0.0
                assertEquals(
                    PlanWarning.LOW_CALORIES_HARD in warnings || (zone == PaceZone.EXTREME && loss),
                    tuning.hardStop,
                )
                assertEquals(v == tuning.baseline, !tuning.isEdited)
                tuning.weeklyChangeKg?.let { assertTrue(it.isFinite()) }

                // Los porcentajes de energía suman 100 siempre que haya energía.
                val percents = tuning.proteinPercent + tuning.carbsPercent + tuning.fatPercent
                assertEquals(if (v.atwaterKcal > 0) 100 else 0, percents)

                // Un macro movido a mano sale del oráculo: el valor limitado a su rango (o a lo que la energía deja si
                // ya estaba por debajo del mínimo) y a la energía; los otros dos no se tocan.
                if (op == 0 || op == 1 || op == 2) {
                    val macro = PlanMacro.entries[op]
                    val requested = tuning.values.gramsOf(macro)
                    val range = limits.of(macro)
                    val reach = before.reachableMaxOf(macro)
                    assertTrue("$macro=$requested fuera de ${minOf(range.minG, reach)}..$reach: $where", requested in minOf(range.minG, reach)..reach)
                    PlanMacro.entries.filter { it != macro }.forEach { other ->
                        assertEquals(before.values.gramsOf(other), v.gramsOf(other))
                    }
                }
            }
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun floorToInt(value: Double): Int = kotlin.math.floor(value).toInt()

    private fun assertDoublesClose(expected: List<Double>, actual: List<Double>, tolerance: Double = 1e-9) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, tolerance) }
    }

}
