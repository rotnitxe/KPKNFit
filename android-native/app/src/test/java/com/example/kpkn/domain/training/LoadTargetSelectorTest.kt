package com.example.kpkn.domain.training

import com.example.kpkn.domain.training.LoadTargetSelector.Change
import com.example.kpkn.domain.training.LoadTargetSelector.Direction
import com.example.kpkn.domain.training.LoadTargetSelector.SetEvidence
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Barbell
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Dumbbells
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Machine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** T4: el selector propone el par (repeticiones, carga) sobre cargas montables; dominio puro, sin Android. */
class LoadTargetSelectorTest {

    private fun sets(loadKg: Double, reps: Int, rir: Int?, count: Int = 3) = List(count) { SetEvidence(loadKg, reps, rir) }

    private fun select(
        evidence: List<SetEvidence>,
        currentKg: Double,
        repMin: Int,
        repMax: Int,
        direction: Direction,
        loads: List<Double>,
        targetRir: Int = 2,
    ) = LoadTargetSelector.select(
        sets = evidence,
        currentLoadKg = currentKg,
        repMin = repMin,
        repMax = repMax,
        targetRir = targetRir,
        direction = direction,
        availableLoads = loads,
    )

    // ─── Los casos del encargo ───────────────────────────────────────────────

    @Test
    fun `mancuernas de 2 en 2, 20 kg por 12 con rango 8 a 12, sube a 22 con las repeticiones que mantienen el esfuerzo`() {
        val dumbbells = Dumbbells((1..20).map { it * 2.0 })
        val target = select(sets(20.0, 12, 2), 20.0, 8, 12, Direction.INCREASE, dumbbells.ladder(20.0, true, 6))!!

        assertEquals(22.0, target.kg, 1e-9)
        assertEquals(Change.INCREASE, target.change)
        assertTrue("nunca 22 kg por 12: salen ${target.reps}", target.reps in 8..9)
        assertEquals("con la reserva objetivo", 2, target.reserve)
    }

    @Test
    fun `maquina de 5 en 5 kg, 50 kg por 10 con reserva 2, mantiene y no sale del rango de la maquina`() {
        val machine = Machine(minKg = 5.0, maxKg = 100.0, incrementKg = 5.0)
        val target = select(sets(50.0, 10, 2), 50.0, 8, 10, Direction.INCREASE, machine.ladder(50.0, true, 6))!!

        assertEquals(Change.KEEP, target.change)
        assertEquals(50.0, target.kg, 1e-9)
        assertTrue("al menos las repeticiones de la última vez", target.reps >= 10)
        assertTrue(machine.isMountable(target.kg))

        // Con un rango más ancho (8 a 12) tampoco compensa el salto de 10 %: sigue en 50 kg y sube repeticiones.
        val wide = select(sets(50.0, 10, 2), 50.0, 8, 12, Direction.INCREASE, machine.ladder(50.0, true, 6))!!
        assertEquals(Change.KEEP, wide.change)
        assertTrue(wide.reps >= 10)
    }

    @Test
    fun `maquina de 5 en 5 kg, 50 kg por 12 en rango 8 a 12, el salto a 55 kg cabe y se propone con 8 repeticiones`() {
        val machine = Machine(minKg = 5.0, maxKg = 100.0, incrementKg = 5.0)
        val target = select(sets(50.0, 12, 2), 50.0, 8, 12, Direction.INCREASE, machine.ladder(50.0, true, 6))!!

        assertEquals(55.0, target.kg, 1e-9)
        assertEquals(8, target.reps)
    }

    @Test
    fun `barra con discos limitados, solo propone cargas formables y mantiene en el tope del material`() {
        val barbell = Barbell(barKg = 20.0, plates = listOf(20.0 to 1, 10.0 to 1, 5.0 to 1, 2.5 to 1, 1.25 to 2))
        val ladder = barbell.ladder(60.0, true, 6)
        val target = select(sets(60.0, 12, 2), 60.0, 8, 12, Direction.INCREASE, ladder)!!

        assertTrue("propuesta formable: ${target.kg}", barbell.isMountable(target.kg))
        assertTrue(target.kg > 60.0)

        // Sin ninguna carga mayor montable (tope del material) el selector se queda en la actual.
        val atCap = select(sets(60.0, 12, 2), 60.0, 8, 12, Direction.INCREASE, emptyList())!!
        assertEquals(Change.KEEP, atCap.change)
        assertEquals(60.0, atCap.kg, 1e-9)

        // Bajando, nunca por debajo de la barra vacía.
        val down = select(sets(22.5, 4, 2), 22.5, 8, 12, Direction.REDUCE, barbell.ladder(22.5, false, 6))!!
        assertTrue(down.kg >= 20.0 - 1e-9)
        assertTrue(barbell.isMountable(down.kg))
    }

    @Test
    fun `bajada con la maquina de 5 en 5, elige la carga mas pesada que deja el rango con la reserva objetivo`() {
        val machine = Machine(minKg = 5.0, maxKg = 100.0, incrementKg = 5.0)
        val target = select(sets(50.0, 5, 0), 50.0, 8, 12, Direction.REDUCE, machine.ladder(50.0, false, 6))!!

        assertEquals(Change.REDUCE, target.change)
        assertEquals(40.0, target.kg, 1e-9)
        assertTrue(target.reps in 8..12)
    }

    // ─── Estimación ──────────────────────────────────────────────────────────

    @Test
    fun `la serie mas floja manda en la estimacion`() {
        val mixed = listOf(SetEvidence(50.0, 12, 2), SetEvidence(50.0, 10, 2), SetEvidence(50.0, 11, 2))
        assertEquals(70.0, LoadTargetSelector.exposureE1rm(mixed), 0.05)
    }

    @Test
    fun `sin reserva registrada se estima con reserva cero, como el modelo de fuerza`() {
        val withoutRir = LoadTargetSelector.exposureE1rm(listOf(SetEvidence(50.0, 10, null)))
        val withZero = LoadTargetSelector.exposureE1rm(listOf(SetEvidence(50.0, 10, 0)))
        assertEquals(withZero, withoutRir, 1e-9)
    }

    @Test
    fun `sin datos que estimar no hay propuesta y quien llama conserva su comportamiento`() {
        val loads = listOf(22.0, 24.0)
        assertNull(select(emptyList(), 20.0, 8, 12, Direction.INCREASE, loads))
        assertNull(select(sets(20.0, 0, 2), 20.0, 8, 12, Direction.INCREASE, loads))
        assertNull(select(sets(20.0, 12, 2), 0.0, 8, 12, Direction.INCREASE, loads))
        assertNull(select(sets(20.0, 12, 2), 20.0, 12, 8, Direction.INCREASE, loads))
        assertNull(select(sets(20.0, 12, 2), 20.0, 0, 12, Direction.INCREASE, loads))
    }

    // ─── Texto de reserva ────────────────────────────────────────────────────

    @Test
    fun `si el rango obliga a quedarse corto la reserva que se enseña es la esperada`() {
        // Solo cabe una carga mucho mas pesada (mantener esta no es posible bajando): la reserva sale de la esperada.
        val onlyHeavier = select(sets(50.0, 5, 0), 50.0, 8, 12, Direction.REDUCE, emptyList())!!
        assertEquals(Change.KEEP, onlyHeavier.change)
        assertEquals(8, onlyHeavier.reps)
        assertTrue("esperada por debajo de la objetivo: ${onlyHeavier.expectedRir}", onlyHeavier.expectedRir < 2.0)
        assertEquals(onlyHeavier.expectedRir.let { Math.round(it).toInt().coerceAtLeast(0) }, onlyHeavier.reserve)
    }

    // ─── Determinismo y estabilidad ──────────────────────────────────────────

    @Test
    fun `los mismos datos dan siempre la misma propuesta`() {
        val loads = Dumbbells((1..20).map { it * 2.0 }).ladder(20.0, true, 6)
        val first = select(sets(20.0, 12, 2), 20.0, 8, 12, Direction.INCREASE, loads)
        val second = select(sets(20.0, 12, 2), 20.0, 8, 12, Direction.INCREASE, loads.reversed())
        assertNotNull(first)
        assertEquals("el orden de las cargas no importa", first, second)
    }

    @Test
    fun `con el e1RM constante la carga nunca sube y baja`() {
        // Un levantador cuya fuerza no cambia, sobre mancuernas de 2 en 2, durante 40 sesiones.
        val scenario = LoadTargetSimulationSupport.Scenario(
            name = "constante",
            stock = Dumbbells((1..20).map { it * 2.0 }),
            repMin = 8, repMax = 12, targetRir = 2,
            startLoadKg = 20.0, startE1rmKg = 31.0, weeklyGain = 0.0,
            noise = LoadTargetSimulationSupport.Noise.NONE,
        )
        (1L..6L).forEach { seed ->
            val result = LoadTargetSimulationSupport.run(scenario, LoadTargetSimulationSupport.Policy.PAIRED, seed, weeks = 20)
            assertEquals("semilla $seed: subir-bajar-subir", 0, result.oscillations)
        }
    }

    // ─── Propiedades sobre material al azar (semilla fija) ───────────────────

    @Test
    fun `nunca propone una carga fuera del material ni repeticiones fuera del rango ni contra la direccion`() {
        val random = Random(20261021L)
        val stocks = listOf(
            Dumbbells((1..20).map { it * 2.0 }),
            Dumbbells((1..16).map { it * 2.5 }),
            Machine(minKg = 5.0, maxKg = 120.0, incrementKg = 5.0),
            Barbell(20.0, listOf(20.0 to 2, 10.0 to 1, 5.0 to 1, 2.5 to 1, 1.25 to 2)),
        )
        repeat(400) {
            val stock = stocks[random.nextInt(stocks.size)]
            val current = (stock.ladder(0.0, true, 40).drop(random.nextInt(8))).firstOrNull() ?: return@repeat
            val repMin = 3 + random.nextInt(8)
            val repMax = repMin + random.nextInt(8)
            val reps = repMin + random.nextInt(repMax - repMin + 1)
            val rir = random.nextInt(4)
            val increase = random.nextBoolean()
            val loads = stock.ladder(current, increase, 6)
            val target = select(
                sets(current, reps, rir), current, repMin, repMax,
                if (increase) Direction.INCREASE else Direction.REDUCE, loads, targetRir = 1 + random.nextInt(3),
            ) ?: return@repeat
            assertTrue("montable ${target.kg}", target.kg == current || stock.isMountable(target.kg))
            assertTrue("repeticiones ${target.reps} en $repMin..$repMax", target.reps in repMin..repMax)
            if (increase) assertTrue(target.kg >= current - 1e-9) else assertTrue(target.kg <= current + 1e-9)
            assertTrue(target.kg == current || target.kg in loads)
        }
    }
}
