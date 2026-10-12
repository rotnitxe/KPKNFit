package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.RecoveryLearningObservation
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Caracterización numérica del SESGO ACTUAL del aprendiz de τ (hallazgo F-10, P0-B) con los motores reales y el
 * arnés de usuarios sintéticos (paquete 0.3 de Rings v4). Documenta con números lo que el actualizador nuevo
 * (paquete 3.2, enchufado a la misma [SimLearner]) tendrá que batir. No cambia código de producción.
 *
 * Ninguna prueba usa el reloj real: todo va con instantes explícitos y `ZoneId.of("UTC")`.
 */
class AugeLearnerBiasCharacterizationTest {

    private companion object {
        const val HOUR_MS = AugeSyntheticUserHarness.HOUR_MS
        val settings: Settings = Settings() // ENTHUSIAST: el caso de los pines F0
        val db = AugeSyntheticUserHarness.exerciseDb
        val completionMs: Long = AugeSyntheticUserHarness.BASE_START_MS + 9L * HOUR_MS

        fun f1(v: Double): String = String.format(Locale.ROOT, "%.1f", v)
        fun f2(v: Double): String = String.format(Locale.ROOT, "%.2f", v)
    }

    private fun ex(id: String, sets: Int, reps: Int, kg: Double, rpe: Double, rest: Int): CompletedExercise =
        AugeSyntheticUserHarness.completedExercise(id, sets, reps, kg, rpe, rest, "inv")

    // ═══ D.1 Razones de inversión SIN ruido (anexo B del plan) ═════════════════

    private class Size(val name: String, val exercises: List<CompletedExercise>)

    private data class InversionRow(
        val channel: String,
        val tauTrue: Double,
        val size: String,
        val hours: Int,
        val battery: Int,
        val stress: Double,
        val x: Double?,
        val implied: Double?,
    ) {
        val ratio: Double? get() = implied?.div(tauTrue)
    }

    private fun muscleSizes() = listOf(
        Size("pequeña", listOf(ex("sim-banca", 3, 8, 70.0, 8.0, 120))),
        Size("media", listOf(ex("sim-banca", 4, 8, 70.0, 8.0, 120), ex("sim-inclinado", 4, 10, 26.0, 8.0, 120))),
        Size(
            "grande",
            listOf(
                ex("sim-banca", 6, 8, 70.0, 8.5, 120),
                ex("sim-inclinado", 5, 10, 26.0, 8.5, 120),
                ex("sim-aperturas", 4, 12, 18.0, 8.5, 90),
            ),
        ),
        Size(
            "muy grande",
            listOf(
                ex("sim-banca", 8, 8, 70.0, 9.0, 120),
                ex("sim-inclinado", 8, 10, 26.0, 9.0, 120),
                ex("sim-aperturas", 6, 12, 18.0, 9.0, 90),
            ),
        ),
    )

    private fun systemSizes() = listOf(
        Size("pequeña", listOf(ex("sim-sentadilla", 3, 6, 100.0, 7.5, 180))),
        Size("media", listOf(ex("sim-sentadilla", 5, 6, 100.0, 8.0, 180), ex("sim-rdl", 3, 8, 80.0, 8.0, 150))),
        Size(
            "grande",
            listOf(
                ex("sim-sentadilla", 6, 5, 120.0, 8.5, 180),
                ex("sim-rdl", 4, 8, 90.0, 8.5, 150),
                ex("sim-hiperextension", 3, 10, 20.0, 8.5, 90),
            ),
        ),
        Size(
            "muy grande",
            listOf(
                ex("sim-sentadilla", 8, 5, 120.0, 9.0, 180),
                ex("sim-rdl", 6, 8, 90.0, 9.0, 150),
                ex("sim-hiperextension", 4, 10, 20.0, 9.0, 90),
                ex("sim-militar", 4, 6, 40.0, 9.0, 120),
            ),
        ),
    )

    private val hoursList = listOf(8, 12, 24, 48)

    private fun logOf(name: String, exercises: List<CompletedExercise>): WorkoutLog =
        AugeSyntheticUserHarness.logFor("inv-$name", "Inversión", exercises, completionMs, 60, emptyList(), settings)

    private fun muscleRows(): List<InversionRow> {
        val rows = ArrayList<InversionRow>()
        muscleSizes().forEach { size ->
            val log = logOf("m-${size.name}", size.exercises)
            val impact = log.muscularImpactV2!!.perMuscle.getValue("Pectorales")
            listOf(24.0, 36.0, 48.0, 72.0).forEach { tau ->
                val cache = AugeAdaptiveCache(personalizedRecoveryHours = mapOf("pectorales" to tau))
                hoursList.forEach { h ->
                    val t = completionMs + h * HOUR_MS
                    val battery = AugeRecoveryEngine.getPerMuscleBatteries(
                        history = listOf(log),
                        wellbeing = null,
                        settings = settings,
                        exerciseDb = db,
                        adaptiveCache = cache,
                        nowOverrideMs = t,
                        zone = AugeSyntheticUserHarness.ZONE,
                    ).getValue("Pectorales").recoveryScore
                    // Lo que el ViewModel manda al aprendiz: sentido = batería del motor, estrés = immediateDrainPct del V2.
                    val obs = RecoveryLearningObservation("Pectorales", battery, battery, impact.immediateDrainPct, h.toDouble())
                    rows += InversionRow(
                        channel = "músculo",
                        tauTrue = tau,
                        size = size.name,
                        hours = h,
                        battery = battery,
                        stress = impact.immediateDrainPct,
                        x = impact.stressUnits / impact.capacityAtCompletion,
                        implied = AugeLearnerSimulator.impliedTauOrNull(obs),
                    )
                }
            }
        }
        return rows
    }

    private fun systemRows(): List<InversionRow> {
        val rows = ArrayList<InversionRow>()
        systemSizes().forEach { size ->
            val log = logOf("s-${size.name}", size.exercises)
            val drain = AugeFatigueEngine.calculateCompletedSessionDrain(log.completedExercises, db, settings)
            val cache = AugeAdaptiveCache(cnsRecoveryHours = 36.0, spinalRecoveryHours = 52.0)
            hoursList.forEach { h ->
                val t = completionMs + h * HOUR_MS
                val muscles = AugeRecoveryEngine.getPerMuscleBatteries(
                    history = listOf(log),
                    wellbeing = null,
                    settings = settings,
                    exerciseDb = db,
                    adaptiveCache = cache,
                    nowOverrideMs = t,
                    zone = AugeSyntheticUserHarness.ZONE,
                )
                val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
                    history = listOf(log),
                    wellbeing = null,
                    settings = settings,
                    exerciseDb = db,
                    adaptiveCache = cache,
                    precomputedMuscles = muscles,
                    articularBatteries = emptyMap(),
                    nowOverrideMs = t,
                )
                val energyObs = RecoveryLearningObservation("cns", batteries.cnc, batteries.cnc, drain.cns.toDouble(), h.toDouble())
                val spinalObs = RecoveryLearningObservation("spinal", batteries.spinal, batteries.spinal, drain.spinal.toDouble(), h.toDouble())
                rows += InversionRow("Energía", 36.0, size.name, h, batteries.cnc, drain.cns.toDouble(), null, AugeLearnerSimulator.impliedTauOrNull(energyObs))
                rows += InversionRow("Columna", 52.0, size.name, h, batteries.spinal, drain.spinal.toDouble(), null, AugeLearnerSimulator.impliedTauOrNull(spinalObs))
            }
        }
        return rows
    }

    @Test
    fun inversionRatios_withoutNoise_showTheCurrentBias() {
        val rows = muscleRows() + systemRows()
        rows.forEach { r ->
            println(
                "[SIM-INV-RATIO] canal=${r.channel} tau=${f1(r.tauTrue)} tamaño=${r.size} h=${r.hours} batería=${r.battery} " +
                    "estrés=${f1(r.stress)}${r.x?.let { " X=${f2(it)}" } ?: ""} tauImplícito=${r.implied?.let { f1(it) } ?: "null"} " +
                    "cociente=${r.ratio?.let { f2(it) } ?: "null"}",
            )
        }
        // Resumen por canal y hora: mínimo–máximo del cociente τ_implícito/τ_verdadero.
        listOf("músculo", "Energía", "Columna").forEach { channel ->
            val line = hoursList.joinToString("  ") { h ->
                val ratios = rows.filter { it.channel == channel && it.hours == h }.mapNotNull { it.ratio }
                "${h}h: ×${f2(ratios.min())}–×${f2(ratios.max())}"
            }
            println("[SIM-INV-RATIO] RESUMEN $channel  $line")
        }

        val muscle = rows.filter { it.channel == "músculo" }
        val energy = rows.filter { it.channel == "Energía" }
        val spinal = rows.filter { it.channel == "Columna" }
        assertTrue("No se emitió ningún τ implícito", rows.all { it.implied != null })
        fun List<InversionRow>.ratios() = map { it.ratio!! }

        // Propiedades cualitativas que SÍ se cumplen hoy (anexo B del plan). Diferencias medidas con el plan. Cifras re-ancladas
        // por el redondeo de las baterías (WP 1.5): la lectura entera ya no trunca (99,7 ⇒ 100) y el τ implícito, muy sensible a
        // 1 punto cerca de la recuperación completa, baja; entre paréntesis lo medido antes.
        //  - músculo: el plan da ×1,16–3,45; medido ×1,01–2,78 (antes ×1,09–3,10; el implícito se topa en 200 h con τ=72). A 48 h la
        //    batería está saturada (98-100) y el mínimo baja a ×1,01 (τ=48, sesión media): por eso >1,0 siempre y >1,1 hasta 24 h.
        assertTrue("músculo: cociente mínimo ${muscle.ratios().min()}", muscle.ratios().all { it > 1.0 })
        assertTrue("músculo ≤24 h: cociente mínimo", muscle.filter { it.hours <= 24 }.ratios().all { it > 1.1 })
        //  - Energía: el plan da ×0,41–0,79 a 8-24 h; medido ×0,43–0,74 (antes ×0,45–0,91: la sesión pequeña a 24 h pasa de ×0,91 a
        //    ×0,69). A 48 h el sesgo CAMBIA de signo (×0,95–1,38): lo que el aprendiz hace con Energía depende del momento del gesto.
        assertTrue("Energía ≤24 h: cociente máximo", energy.filter { it.hours <= 24 }.ratios().all { it < 0.95 })
        assertTrue("Energía ≤12 h: cociente máximo", energy.filter { it.hours <= 12 }.ratios().all { it < 0.7 })
        assertTrue("Energía: cociente mínimo ${energy.ratios().min()}", energy.ratios().all { it > 0.4 })
        assertTrue("Energía 48 h: ya no subestima", energy.filter { it.hours == 48 }.ratios().all { it >= 0.95 })
        //  - Columna: el plan da ×1,0–1,8 a 8 h y ×1,1–1,3 a 24 h; medido ×1,00–3,46 (antes ×1,05–3,46: a 12 h con sesión media el
        //    cociente baja de ×1,35 a ×1,00). Dentro de [0,95; 2,0] a ≤12 h; a 24-48 h las sesiones con poca carga axial (estrés
        //    3-15 %) disparan el cociente (batería 99-100 con estrés minúsculo).
        assertTrue("Columna: cociente mínimo ${spinal.ratios().min()}", spinal.ratios().all { it > 0.95 })
        assertTrue("Columna ≤12 h fuera de [0,95; 2,0]", spinal.filter { it.hours <= 12 }.ratios().all { it in 0.95..2.0 })
    }

    // ═══ D.2 Bucle cerrado (plan 6.6) ═════════════════════════════════════════

    /** Valores medidos hoy (modo ligero, 60 usuarios, 30 gestos, motor real): MAE y sesgo firmado de τ, en horas. */
    private class Measured(val mae: Double, val bias: Double)

    private fun assertNear(label: String, measured: Double, expected: Double, relTol: Double = 0.25, absTol: Double = 1.0) {
        val tol = max(abs(expected) * relTol, absTol)
        assertTrue(
            "$label: medido ${f1(measured)} fuera de ${f1(expected)} ± ${f1(tol)} (margen ±25 %)",
            abs(measured - expected) <= tol,
        )
    }

    @Test
    fun closedLoop_fourScenarios_measureTheCurrentLearnerBias() {
        val users = AugeSyntheticUserHarness.learnerUsers()
        val t0 = System.nanoTime()
        val byScenario = AugeLearnerSimulator.runPopulation(users)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000
        val muscleParam = AugeLearnerSimulator.muscleParam("Pectorales")
        val cns = AugeLearnerSimulator.CNS
        val spinalParam = AugeLearnerSimulator.SPINAL
        val params = listOf(muscleParam, cns, spinalParam)
        println(
            "[SIM-BUCLE] usuarios=${users.size} gestos=30 modo=${if (AugeSyntheticUserHarness.isFullMode()) "completo" else "ligero"} " +
                "sesiones/semana=4 tiempo=$elapsedMs ms",
        )

        val tableByKey = HashMap<String, SimMetric>()
        val awayAll = HashMap<String, SimAway>()
        val plantaPct = HashMap<String, Double>()
        AugeLearnerSimulator.SCENARIOS.forEach { scenario ->
            val runs = byScenario.getValue(scenario.name)
            AugeLearnerSimulator.metrics(runs, scenario, params).forEach { m ->
                tableByKey["${scenario.name}|${m.param}|${m.n}"] = m
                println(
                    "[SIM-BUCLE] escenario=\"${m.scenario}\" parámetro=${m.param} n=${m.n} tauVerdadero=${f1(m.trueTau)} " +
                        "usuarios=${m.users} MAE=${f1(m.mae)} h (${f1(100.0 * m.mae / m.trueTau)} % del τ) sesgo=${f1(m.bias)} h " +
                        "obsMedias=${f1(m.meanObs)}",
                )
            }
            val groups = listOf(
                "pectorales" to { p: String -> p == muscleParam },
                "energía" to { p: String -> p == cns },
                "columna" to { p: String -> p == spinalParam },
                "todos los músculos" to { p: String -> p != cns && p != spinalParam },
                "todos los parámetros" to { _: String -> true },
            )
            groups.forEach { (label, include) ->
                val away = AugeLearnerSimulator.awayStats(runs, scenario, include)
                if (label == "todos los parámetros") awayAll[scenario.name] = away
                println(
                    "[SIM-BUCLE] escenario=\"${scenario.name}\" alejan[$label]: ${f1(away.pct)} % " +
                        "(${away.away} de ${away.counted} contadas, ${away.updates} actualizaciones)",
                )
            }
            val observed = SimChannel.entries.joinToString("  ") { ch ->
                val total = runs.sumOf { it.observations[ch] ?: 0 }
                val planta = runs.sumOf { it.plantaGuard[ch] ?: 0 }
                val pct = if (total == 0) 0.0 else 100.0 * planta / total
                plantaPct["${scenario.name}|$ch"] = pct
                "$ch: planta $planta de $total (${f1(pct)} %)"
            }
            println("[SIM-BUCLE] escenario=\"${scenario.name}\" guarda de planta (sentido == predicho ⇒ no se aprende): $observed")
            val updatesPerUser = runs.sumOf { it.updates.size }.toDouble() / runs.size
            println("[SIM-BUCLE] escenario=\"${scenario.name}\" actualizaciones de τ por usuario en 30 gestos: ${f1(updatesPerUser)}")
            // Cociente τ_implícito / τ_verdadero CON ruido, por tramo horario (explica por qué Energía casi no deriva).
            listOf("pectorales" to muscleParam, "energía" to cns, "columna" to spinalParam).forEach { (label, param) ->
                val buckets = AugeLearnerSimulator.impliedRatioByHours(runs, scenario) { it == param }
                println(
                    "[SIM-BUCLE] escenario=\"${scenario.name}\" cociente implícito/verdadero con ruido [$label]: " +
                        buckets.joinToString("  ") { "${it.label}: ×${f2(it.meanRatio)} (n=${it.updates})" },
                )
            }
        }

        // ── Cifras medidas hoy (n = 30 gestos), con margen ±25 % (±1 h en las pequeñas) ──
        // escenario → parámetro → (MAE, sesgo). Plan 6.6 (simulación Python simplificada): τ=defecto 24,3 h / +24,3 h;
        // ×1,25 → 30,2 h; ×0,75 → 16,9 h; σ=7 → 18,5 h. El motor real da cifras distintas: Pectorales solo recibe ~9,4
        // observaciones en 30 gestos (entra en el top-3 de estrés tras empuje y cuerpo completo).
        // Re-ancladas por el redondeo de las baterías (WP 1.5). Antes (τ = defecto): Pectorales 8,1/+7,9 · Energía 1,4/−0,3 ·
        // Columna 36,4/+36,4; ×1,25: 4,6/−2,6 · 6,4/−6,4 · 27,7; ×0,75: 18,4 · 7,0 · 51,8; σ=7: 8,5/+8,2 · 2,6/+2,0 · 39,6.
        // Ahora Columna deriva +24 h (parte del sesgo era leer 99 en lugar de 100 con la batería ya recuperada) y Energía pasa a
        // −3,6 h: los gestos a 30-60 h y a >60 h (×1,34 y ×2,06) caen más en la guarda de planta y pesan menos que los de ≤30 h (×0,70).
        val expected: Map<String, Map<String, Measured>> = mapOf(
            AugeLearnerSimulator.SCENARIOS[0].name to mapOf(
                muscleParam to Measured(7.3, 6.9), cns to Measured(3.8, -3.6), spinalParam to Measured(23.8, 23.8),
            ),
            AugeLearnerSimulator.SCENARIOS[1].name to mapOf(
                muscleParam to Measured(5.2, -4.4), cns to Measured(9.5, -9.5), spinalParam to Measured(14.1, 13.9),
            ),
            AugeLearnerSimulator.SCENARIOS[2].name to mapOf(
                muscleParam to Measured(17.4, 17.4), cns to Measured(3.3, 2.8), spinalParam to Measured(34.9, 34.9),
            ),
            AugeLearnerSimulator.SCENARIOS[3].name to mapOf(
                muscleParam to Measured(7.7, 7.2), cns to Measured(2.8, -1.3), spinalParam to Measured(25.7, 25.7),
            ),
        )
        expected.forEach { (scenarioName, perParam) ->
            perParam.forEach { (param, want) ->
                val got = tableByKey.getValue("$scenarioName|$param|30")
                assertNear("$scenarioName / $param MAE@30", got.mae, want.mae)
                assertNear("$scenarioName / $param sesgo@30", got.bias, want.bias, absTol = 1.5)
            }
        }
        val names = AugeLearnerSimulator.SCENARIOS.map { it.name }
        // Dirección del sesgo: con τ verdadero = defecto Pectorales y Columna se van hacia ARRIBA (se alejan de la verdad);
        // con τ verdadero más lento (×1,25) Columna sigue subiendo (no converge a 65 h, sube hasta ~79 h) y con τ más
        // rápido (×0,75) Pectorales y Energía quedan por encima de la verdad (el aprendiz no acorta lo suficiente).
        assertTrue(tableByKey.getValue("${names[0]}|$muscleParam|30").bias > 0.0)
        assertTrue(tableByKey.getValue("${names[0]}|$spinalParam|30").bias > 0.0)
        assertTrue(tableByKey.getValue("${names[1]}|$spinalParam|30").bias > 0.0)
        assertTrue(tableByKey.getValue("${names[2]}|$muscleParam|30").bias > 0.0)
        assertTrue(tableByKey.getValue("${names[2]}|$cns|30").bias > 0.0)
        // «con τ verdadero = defecto, el error medio tras 30 gestos supera el 15 % del τ» en Pectorales (15,2 %; antes 16,8 %) y
        // Columna (45,8 %; antes 70 %).
        assertTrue(tableByKey.getValue("${names[0]}|$muscleParam|30").let { it.mae / it.trueTau > 0.15 })
        assertTrue(tableByKey.getValue("${names[0]}|$spinalParam|30").let { it.mae / it.trueTau > 0.15 })
        // El usuario ruidoso no mejora al aprendiz: el error en Pectorales y Columna es ≥ que con σ=4.
        assertTrue(tableByKey.getValue("${names[3]}|$muscleParam|30").mae >= tableByKey.getValue("${names[0]}|$muscleParam|30").mae)
        assertTrue(tableByKey.getValue("${names[3]}|$spinalParam|30").mae >= tableByKey.getValue("${names[0]}|$spinalParam|30").mae)
        // Actualizaciones que alejan τ del verdadero (todos los parámetros): medido 64,8 / 44,3 / 58,9 / 64,8 % (antes del redondeo
        // 70,6 / 51,4 / 66,0 / 70,4 %; plan: ~57 %).
        listOf(64.8, 44.3, 58.9, 64.8).forEachIndexed { i, want ->
            assertNear("alejan[todos] ${names[i]}", awayAll.getValue(names[i]).pct, want, absTol = 3.0)
        }
        // Guarda de planta (sentido == predicho): ~27 % de las observaciones con σ=4 y ~25 % con σ=7 (antes ~10 % y ~5,5 %). Con la
        // batería redondeada un canal recuperado lee 100 tanto en la verdad como en lo predicho, y el ruido positivo se recorta en 100.
        val plantaSigma4 = mapOf(SimChannel.MUSCLE to 27.4, SimChannel.ENERGY to 26.5, SimChannel.SPINAL to 32.6)
        val plantaSigma7 = mapOf(SimChannel.MUSCLE to 24.2, SimChannel.ENERGY to 22.9, SimChannel.SPINAL to 28.9)
        SimChannel.entries.forEach { ch ->
            assertNear("planta σ=4 $ch", plantaPct.getValue("${names[0]}|$ch"), plantaSigma4.getValue(ch), absTol = 1.5)
            assertNear("planta σ=7 $ch", plantaPct.getValue("${names[3]}|$ch"), plantaSigma7.getValue(ch), absTol = 1.5)
        }
        // Cociente implícito/verdadero CON ruido por tramo horario (τ verdadero = defecto): Energía subestima a ≤30 h (×0,70) y
        // sobreestima después (×1,34 a 30-60 h, ×2,06 a >60 h), con un sesgo neto negativo; Columna sobreestima en todos
        // los tramos (×2,0 / ×3,2 / ×3,5); Pectorales solo recibe gestos a >30 h (tras empuje y cuerpo completo).
        val defaultRuns = byScenario.getValue(names[0])
        val energyBuckets = AugeLearnerSimulator.impliedRatioByHours(defaultRuns, AugeLearnerSimulator.SCENARIOS[0]) { it == cns }
        val spinalBuckets = AugeLearnerSimulator.impliedRatioByHours(defaultRuns, AugeLearnerSimulator.SCENARIOS[0]) { it == spinalParam }
        val chestBuckets = AugeLearnerSimulator.impliedRatioByHours(defaultRuns, AugeLearnerSimulator.SCENARIOS[0]) { it == muscleParam }
        assertTrue("Energía 8-30 h: ×${energyBuckets[0].meanRatio}", energyBuckets[0].meanRatio < 0.9)
        assertTrue("Energía >60 h: ×${energyBuckets[2].meanRatio}", energyBuckets[2].meanRatio > 1.2)
        assertTrue("Columna: sobreestima en todos los tramos ${spinalBuckets.map { it.meanRatio }}", spinalBuckets.all { it.meanRatio > 1.5 })
        assertEquals("Pectorales no recibe gestos a 8-30 h", 0, chestBuckets[0].updates)
        assertTrue("Pectorales 30-60 h: ×${chestBuckets[1].meanRatio}", chestBuckets[1].meanRatio > 1.4)
    }

    @Test
    fun closedLoop_longRun_comparesPectoralesAt30OwnObservations() {
        // Corrida larga (26 semanas, 100 gestos) con menos usuarios: permite medir Pectorales tras 8/16/30 observaciones
        // PROPIAS (comparable con «tras 30 obs» de la simulación del plan) y la deriva a 60 y 100 gestos.
        val full = AugeSyntheticUserHarness.isFullMode()
        val users = AugeSyntheticUserHarness.learnerUsers(count = if (full) 100 else 20, weeks = 26)
        val t0 = System.nanoTime()
        val byScenario = AugeLearnerSimulator.runPopulation(users, gestures = 100)
        val muscleParam = AugeLearnerSimulator.muscleParam("Pectorales")
        val params = listOf(muscleParam, AugeLearnerSimulator.CNS, AugeLearnerSimulator.SPINAL)
        println("[SIM-BUCLE-LARGO] usuarios=${users.size} gestos=100 tiempo=${(System.nanoTime() - t0) / 1_000_000} ms")
        val byKey = HashMap<String, SimMetric>()
        AugeLearnerSimulator.SCENARIOS.forEach { scenario ->
            val runs = byScenario.getValue(scenario.name)
            AugeLearnerSimulator.metricsByOwnObservations(runs, scenario, params).forEach { m ->
                byKey["${scenario.name}|${m.param}|obs${m.n}"] = m
                println(
                    "[SIM-BUCLE-LARGO] escenario=\"${scenario.name}\" parámetro=${m.param} tras ${m.n} observaciones propias " +
                        "tauVerdadero=${f1(m.trueTau)} usuarios=${m.users} MAE=${f1(m.mae)} h sesgo=${f1(m.bias)} h",
                )
            }
            AugeLearnerSimulator.metrics(runs, scenario, params, milestones = listOf(60, 100)).forEach { m ->
                byKey["${scenario.name}|${m.param}|g${m.n}"] = m
                println(
                    "[SIM-BUCLE-LARGO] escenario=\"${scenario.name}\" parámetro=${m.param} tras ${m.n} gestos " +
                        "tauVerdadero=${f1(m.trueTau)} MAE=${f1(m.mae)} h sesgo=${f1(m.bias)} h obsMedias=${f1(m.meanObs)}",
                )
            }
        }
        val default = AugeLearnerSimulator.SCENARIOS.first().name
        // Dirección tras 30 observaciones propias con τ verdadero = defecto: Pectorales y Columna siguen subiendo.
        assertTrue(byKey.getValue("$default|$muscleParam|obs30").bias > 0.0)
        assertTrue(byKey.getValue("$default|${AugeLearnerSimulator.SPINAL}|obs30").bias > 0.0)
        assertTrue(byKey.getValue("$default|${AugeLearnerSimulator.SPINAL}|g100").bias > 0.0)
        // Cifras medidas hoy (20 usuarios en ligero; con 100 usuarios en completo salen dentro del mismo margen ±25 %), re-ancladas
        // por el redondeo de las baterías (WP 1.5; entre paréntesis lo medido antes):
        // Pectorales tras 30 observaciones propias, τ=defecto: MAE 9,9 h / +9,5 h (antes 11,7 / +11,7; el plan, con su Python
        // simplificado, da 24,3 h / +24,3 h para un parámetro de 48 h); con τ verdadero ×0,75: +19,1 h (+20,8); Columna tras 30
        // observaciones +22,7 h (+35,1) y a 100 gestos +37,8 h (+54,6; no se estabiliza); Energía con τ verdadero ×1,25: −11,0 h
        // (−8,1; no converge a 45 h).
        val names = AugeLearnerSimulator.SCENARIOS.map { it.name }
        assertNear("pectorales obs30 MAE", byKey.getValue("${names[0]}|$muscleParam|obs30").mae, 9.9)
        assertNear("pectorales obs30 sesgo", byKey.getValue("${names[0]}|$muscleParam|obs30").bias, 9.5, absTol = 1.5)
        assertNear("columna obs30 sesgo", byKey.getValue("${names[0]}|spinal|obs30").bias, 22.7, absTol = 1.5)
        assertNear("columna g100 sesgo", byKey.getValue("${names[0]}|spinal|g100").bias, 37.8, absTol = 1.5)
        assertNear("pectorales ×0,75 obs30 sesgo", byKey.getValue("${names[2]}|$muscleParam|obs30").bias, 19.1, absTol = 1.5)
        assertNear("energía ×1,25 obs30 sesgo", byKey.getValue("${names[1]}|cns|obs30").bias, -11.0, absTol = 1.5)
    }

    @Test
    fun closedLoop_withoutNoiseAndTrueTauEqualToDefault_neverLearns() {
        // Control del propio bucle: sin ruido y con τ verdadero = defecto, lo «sentido» coincide con lo «predicho» en cada
        // canal (mismo motor, mismos τ), así que la guarda de planta bloquea todo y el aprendiz no se mueve.
        val scenario = SimScenario("sin ruido, tau = defecto", 1.0, 0.0, 1L)
        val users = AugeSyntheticUserHarness.learnerUsers(8)
        val runs = AugeLearnerSimulator.runPopulation(users, scenarios = listOf(scenario)).getValue(scenario.name)
        runs.forEach { run ->
            assertTrue("${run.userId}: sin ruido no debe haber actualizaciones (hubo ${run.updates.size})", run.updates.isEmpty())
            SimChannel.entries.forEach { ch ->
                assertEquals(
                    "${run.userId} $ch: todas las observaciones deben caer en la guarda de planta",
                    run.observations[ch] ?: 0,
                    run.plantaGuard[ch] ?: 0,
                )
            }
        }
        assertTrue("El control no generó observaciones", runs.sumOf { it.observations.values.sum() } > 0)
    }

    // ═══ D.3 Determinismo ═════════════════════════════════════════════════════

    private fun fingerprint(runs: Map<String, List<SimUserRun>>): List<Any> = runs.entries.flatMap { (name, list) ->
        list.map {
            listOf(name, it.userId, it.gestures, it.tauAtMilestone, it.obsAtMilestone, it.updates, it.observations, it.plantaGuard)
        }
    }

    @Test
    fun closedLoop_twoRunsOfTheSameScenario_areIdentical() {
        val users = AugeSyntheticUserHarness.learnerUsers(6)
        val scenarios = listOf(AugeLearnerSimulator.SCENARIOS.first(), AugeLearnerSimulator.SCENARIOS.last())
        val first = AugeLearnerSimulator.runPopulation(users, scenarios = scenarios)
        val second = AugeLearnerSimulator.runPopulation(users, scenarios = scenarios)
        assertEquals("Dos corridas del mismo escenario deben dar resultados idénticos", fingerprint(first), fingerprint(second))
        assertTrue("La corrida no produjo actualizaciones", first.values.flatten().sumOf { it.updates.size } > 0)
    }

    // ═══ Fidelidad de la réplica de producción (CurrentEmaLearner) ═════════════

    private fun logAfter(id: String, exercises: List<CompletedExercise>): WorkoutLog =
        AugeSyntheticUserHarness.logFor(id, "Réplica", exercises, completionMs, 60, emptyList(), settings)

    private fun gesture(log: WorkoutLog, hoursAfter: Double, observations: List<SimObservation>) = SimGesture(
        tiempoMs = completionMs + (hoursAfter * HOUR_MS).toLong(),
        observaciones = observations,
        historial = listOf(log),
        ajustes = settings,
        exerciseDb = db,
    )

    @Test
    fun currentEmaLearner_gates_matchTheViewModel() {
        val squat = logAfter("rep-sq", listOf(ex("sim-sentadilla", 4, 6, 100.0, 8.0, 180)))
        val benchOnly = logAfter("rep-bench", listOf(ex("sim-banca", 4, 8, 70.0, 8.0, 120)))
        val differs = listOf(
            SimObservation(SimChannel.ENERGY, "cns", 80, 90),
            SimObservation(SimChannel.SPINAL, "spinal", 70, 90),
            SimObservation(SimChannel.MUSCLE, "Cuádriceps", 60, 75),
        )

        // < 8 h desde la sesión: nada se aprende.
        CurrentEmaLearner().also { it.onGesture(gesture(squat, 4.0, differs)) }.let {
            assertEquals(AugeAdaptiveCache(), it.cache())
        }
        // > 14 días: nada se aprende.
        CurrentEmaLearner().also { it.onGesture(gesture(squat, 15 * 24.0, differs)) }.let {
            assertEquals(AugeAdaptiveCache(), it.cache())
        }
        // Guarda de planta: sentido == predicho en todos los canales.
        val same = differs.map { it.copy(sentido = it.predicho) }
        CurrentEmaLearner().also { it.onGesture(gesture(squat, 24.0, same)) }.let {
            assertEquals(AugeAdaptiveCache(), it.cache())
        }

        // Un gesto con Energía + Columna + un músculo = UNA observación global, y las funciones reales hacen el cálculo.
        val learner = CurrentEmaLearner()
        learner.onGesture(gesture(squat, 24.0, differs))
        val drain = AugeFatigueEngine.calculateCompletedSessionDrain(squat.completedExercises, db, settings)
        val (cns, spinal) = AugeAdaptiveEngine.updateSystemRecoveryHours(
            currentCnsTau = null,
            currentSpinalTau = null,
            cnsObservation = RecoveryLearningObservation("cns", 90, 80, drain.cns.toDouble(), 24.0),
            spinalObservation = RecoveryLearningObservation("spinal", 90, 70, drain.spinal.toDouble(), 24.0),
            totalObservations = 0,
        )
        val stress = squat.muscularImpactV2!!.perMuscle.getValue("Cuádriceps").immediateDrainPct
        val muscles = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
            current = emptyMap(),
            observation = RecoveryLearningObservation("Cuádriceps", 75, 60, stress, 24.0),
            totalObservations = 0,
        )
        val got = learner.cache()
        assertEquals(cns, got.cnsRecoveryHours)
        assertEquals(spinal, got.spinalRecoveryHours)
        assertEquals(muscles, got.personalizedRecoveryHours)
        assertEquals("Un gesto = una observación global", 1, got.totalObservations)
        assertEquals(completionMs + 24L * HOUR_MS, got.lastUpdatedMs)

        // Sin estímulo axial (banca sola) la Columna no aprende; Energía sí.
        val noAxial = CurrentEmaLearner()
        noAxial.onGesture(gesture(benchOnly, 24.0, listOf(differs[0], differs[1])))
        assertEquals(null, noAxial.cache().spinalRecoveryHours)
        assertTrue("Energía debe aprender", noAxial.cache().cnsRecoveryHours != null)

        // El contador global baja α de las siguientes observaciones: la 2.ª con el mismo dato mueve menos que la 1.ª.
        val twice = CurrentEmaLearner()
        val muscleOnly = listOf(SimObservation(SimChannel.MUSCLE, "Cuádriceps", 60, 75))
        twice.onGesture(gesture(squat, 24.0, muscleOnly))
        val afterFirst = twice.cache().personalizedRecoveryHours.getValue("cuádriceps")
        twice.onGesture(gesture(squat, 24.0, muscleOnly))
        assertEquals(2, twice.cache().totalObservations)
        assertTrue("τ debe seguir moviéndose en el mismo sentido", twice.cache().personalizedRecoveryHours.getValue("cuádriceps") != afterFirst)
    }

    // ═══ F-11 en el actualizador ACTUAL (BUG CONOCIDO, los arregla el paquete 3.2) ══

    @Test
    fun currentEmaLearner_alphaFollowsTheGlobalCounter_F11() {
        // BUG CONOCIDO F-11: α = max(0,05; min(0,5; 1,5/(n+1))) usa el contador GLOBAL de observaciones, no el del parámetro:
        // la 1.ª observación de un músculo tras 40 eventos de otros canales aprende al 5 % en vez de al 50 %.
        val squat = logAfter("f11-sq", listOf(ex("sim-sentadilla", 4, 6, 100.0, 8.0, 180)))
        val observations = listOf(SimObservation(SimChannel.MUSCLE, "Cuádriceps", 60, 75))
        fun deltaAfter(globalCount: Int): Double {
            val learner = CurrentEmaLearner(AugeAdaptiveCache(totalObservations = globalCount))
            learner.onGesture(gesture(squat, 24.0, observations))
            return learner.cache().personalizedRecoveryHours.getValue("cuádriceps") - 72.0
        }
        val fresh = deltaAfter(0)
        val late = deltaAfter(40)
        println("[SIM-F11] 1.ª observación de Cuádriceps: Δτ con contador 0 = ${f2(fresh)} h; con contador 40 = ${f2(late)} h (cociente ${f2(late / fresh)})")
        assertTrue("el aprendiz debe mover τ", fresh != 0.0)
        assertEquals("α(40)/α(0) = 0,05/0,5", 0.1, late / fresh, 1e-9)
    }

    @Test
    fun currentEmaLearner_hasNoDeadBand_F11() {
        // BUG CONOCIDO F-11: sin zona muerta, una diferencia de 1 punto entre lo sentido y lo predicho ya mueve τ.
        val squat = logAfter("f11-band", listOf(ex("sim-sentadilla", 4, 6, 100.0, 8.0, 180)))
        val learner = CurrentEmaLearner()
        learner.onGesture(gesture(squat, 24.0, listOf(SimObservation(SimChannel.MUSCLE, "Cuádriceps", 74, 75))))
        val tau = learner.cache().personalizedRecoveryHours["cuádriceps"]
        println("[SIM-F11] 1 punto de diferencia (74 sentido, 75 predicho): τ(Cuádriceps) 72,0 → ${tau?.let { f2(it) }}")
        assertTrue("1 punto debe mover τ (hoy no hay zona muerta)", tau != null && tau != 72.0)
    }

    @Test
    fun learnedTauBelow18_hasNoEffectOnMuscles_butEnergyAcceptsIt_F11() {
        // BUG CONOCIDO F-11: el aprendiz guarda τ en [12, 144] pero el motor muscular lo recorta a [18, 144]; Energía no recorta.
        val chestLog = logOf("f11-chest", muscleSizes()[1].exercises)
        val squatLog = logOf("f11-sys", systemSizes()[1].exercises)
        fun chest(tau: Double): Int = AugeRecoveryEngine.getPerMuscleBatteries(
            history = listOf(chestLog),
            wellbeing = null,
            settings = settings,
            exerciseDb = db,
            adaptiveCache = AugeAdaptiveCache(personalizedRecoveryHours = mapOf("pectorales" to tau)),
            nowOverrideMs = completionMs + 24L * HOUR_MS,
            zone = AugeSyntheticUserHarness.ZONE,
        ).getValue("Pectorales").recoveryScore
        fun energy(tau: Double): Int = AugeLearnerSimulator.systemBatteries(
            settings, db, listOf(squatLog), completionMs + 8L * HOUR_MS, AugeAdaptiveCache(cnsRecoveryHours = tau),
        ).cnc
        println("[SIM-F11] Pectorales a 24 h: τ=12 → ${chest(12.0)}, τ=18 → ${chest(18.0)}, τ=48 → ${chest(48.0)}; Energía a 8 h: τ=12 → ${energy(12.0)}, τ=18 → ${energy(18.0)}")
        assertEquals("τ=12 y τ=18 deben dar la misma batería muscular", chest(18.0), chest(12.0))
        assertTrue("τ=18 recupera más que τ=48", chest(18.0) > chest(48.0))
        assertTrue("Energía sí distingue τ=12 de τ=18", energy(12.0) > energy(18.0))
    }

    // ═══ Referencia «no aprender» y enchufe de otro aprendiz ═══════════════════

    private class FrozenLearner : SimLearner {
        override fun cache(): AugeAdaptiveCache = AugeAdaptiveCache()
        override fun onGesture(gesto: SimGesture) = Unit
    }

    @Test
    fun frozenLearner_plugsIntoTheSimulator_andGivesTheDoNothingReference() {
        // Un aprendiz que nunca aprende deja τ en el defecto: su error es exactamente |τ_verdadero − τ_defecto|. Es la
        // referencia que el actualizador nuevo (3.2) tiene que batir, y comprueba que SimLearner enchufa sin tocar el simulador.
        val scenario = AugeLearnerSimulator.SCENARIOS[1] // τ verdadero = defecto × 1,25
        val users = AugeSyntheticUserHarness.learnerUsers(6)
        val runs = AugeLearnerSimulator.runPopulation(users, scenarios = listOf(scenario), learnerFactory = { FrozenLearner() })
            .getValue(scenario.name)
        val params = listOf(AugeLearnerSimulator.muscleParam("Pectorales"), AugeLearnerSimulator.CNS, AugeLearnerSimulator.SPINAL)
        val metrics = AugeLearnerSimulator.metrics(runs, scenario, params)
        assertTrue(metrics.isNotEmpty())
        metrics.forEach { m ->
            val gap = AugeLearnerSimulator.defaultTau(m.param) - m.trueTau
            assertEquals("${m.param}@${m.n} MAE", abs(gap), m.mae, 1e-9)
            assertEquals("${m.param}@${m.n} sesgo", gap, m.bias, 1e-9)
        }
        assertTrue(runs.all { it.updates.isEmpty() })
        println("[SIM-F11] referencia aprendiz congelado ×1,25 @30: " + metrics.filter { it.n == 30 }.joinToString("  ") { "${it.param} MAE ${f1(it.mae)} h" })
    }

    @Test
    fun harnessUser_isReproducible_acrossCalls() {
        val user = SyntheticUser("R", AthleteType.ENTHUSIAST, 4, 0.0, 2, 4242L)
        val a = AugeSyntheticUserHarness.buildHistory(user)
        val b = AugeSyntheticUserHarness.buildHistory(user)
        assertEquals(a.logs.map { it.muscularImpactV2?.setInputHash }, b.logs.map { it.muscularImpactV2?.setInputHash })
        assertEquals(8, a.sessions.size)
        assertTrue("Cada log debe llevar V2", a.logs.all { it.muscularImpactV2 != null })
    }
}
