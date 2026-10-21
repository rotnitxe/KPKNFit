package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Invariantes del motor REAL sobre las trayectorias del arnés de usuarios sintéticos (paquete 0.3 de Rings v4).
 *
 * Ninguna prueba usa el reloj real: todo va con `nowOverrideMs`/`nowMs` explícitos y `ZoneId.of("UTC")`.
 * Fecha base lejana (2025-03-03 10:00 UTC): el anillo muscular global y el dashboard reflejan hoy «ACWR no disponible»
 * porque `applyMuscularAcwr` usa el reloj real (hasta que 1.4/1.7 lo inyecten); el ACWR que se comprueba aquí se calcula con
 * instante explícito. Reglas de las invariantes que hoy fallan: presupuesto medido + comentario `BUG CONOCIDO F-xx`.
 */
class AugeSyntheticUserInvariantsTest {

    private companion object {
        const val HOUR_MS = AugeSyntheticUserHarness.HOUR_MS
        const val DAY_MS = AugeSyntheticUserHarness.DAY_MS

        val full = AugeSyntheticUserHarness.isFullMode()
        val users: List<SyntheticUser> = AugeSyntheticUserHarness.invariantUsers()

        /** Trayectorias (historial + muestreo cada 6 h por la ruta de Home); se calculan una vez, en paralelo por usuario. */
        val trajectories: List<SyntheticTrajectory> by lazy {
            val t0 = System.nanoTime()
            val result = AugeSyntheticUserHarness.parallelMap(users) { AugeSyntheticUserHarness.trajectory(it) }
            println(
                "[SIM-INV] trayectorias: modo=${if (full) "completo" else "ligero"} usuarios=${users.size} " +
                    "muestras=${result.sumOf { it.samples.size }} sesiones=${result.sumOf { it.history.sessions.size }} " +
                    "tiempo=${(System.nanoTime() - t0) / 1_000_000} ms",
            )
            result.forEach { println("[SIM-INV] hash ${it.user.id} (semilla ${it.user.seed}): ${AugeSyntheticUserHarness.hashOf(it)}") }
            result
        }

        // ── Presupuestos medidos hoy (modo ligero y completo; ver comentarios en cada prueba) ──
        // Rango/suelos: 0 de 20.679 valores (ligero) y 0 de 222.650 (completo). Monotonía de recuperación: 0 de 9.216 / 99.840.
        // ACWR: 0 de 339 / 3.650 muestras.
        const val RANGE_VIOLATION_BUDGET = 0
        const val MONOTONIC_VIOLATION_BUDGET = 0
        const val ACWR_VIOLATION_BUDGET = 0

        /**
         * F-60 corregido en 2.2b2: los acumulados por canal eliminan las dos bajadas 66→65 al subir la carga
         * ×0,5 → ×0,75 en F2/tirón (muscular y V2 global). F2 sigue presente en ambos modos.
         */
        const val DRAIN_VIOLATION_BUDGET = 0

        /**
         * Divergencia máxima |vista previa − Home| medida hoy + 1 (F-23): 7 en el modo ligero (126 puntos; la sesión
         * hipotética pesada, anillo muscular global) y 6 en el completo (1.500 puntos); sesiones reales ≤ 4. El paquete 2.3
         * (cálculo único de la vista previa y de Home) la baja a ≤ 1.
         */
        const val PREVIEW_HOME_BUDGET = 8
    }

    private fun hoursOf(tr: SyntheticTrajectory, s: SimSample): String = "+${(s.tMs - tr.startMs) / HOUR_MS}h"

    // ─── 1. Rango, suelos fisiológicos, sin NaN ───────────────────────────────

    @Test
    fun batteries_stayInRange_andAboveThePhysiologicalFloor_withoutNaN() {
        val violations = ArrayList<String>()
        var values = 0
        trajectories.forEach { tr ->
            val floor = AugeUtils.physiologicalFloor(AugeSyntheticUserHarness.settingsFor(tr.user))
            tr.samples.forEach { s ->
                val where = "${tr.user.id} ${hoursOf(tr, s)}"
                fun inRange(label: String, v: Int) {
                    values++
                    if (v !in 0..100) violations += "$where $label=$v fuera de 0..100"
                }
                fun atLeast(label: String, v: Int, minimum: Int) {
                    values++
                    if (v < minimum) violations += "$where $label=$v < suelo $minimum"
                }
                s.perMuscle.forEach { (muscle, v) ->
                    inRange("músculo $muscle", v)
                    atLeast("músculo $muscle", v, floor.muscular)
                }
                inRange("muscular", s.muscular); atLeast("muscular", s.muscular, floor.muscular)
                inRange("energía", s.energy); atLeast("energía", s.energy, floor.cns)
                inRange("columna", s.spinal); atLeast("columna", s.spinal, floor.spinal)
                inRange("energía cruda", s.energyRaw)
                inRange("columna cruda", s.spinalRaw)
                inRange("dash muscular", s.dashMuscular); atLeast("dash muscular", s.dashMuscular, floor.muscular)
                inRange("dash energía", s.dashEnergy); atLeast("dash energía", s.dashEnergy, floor.cns)
                inRange("dash estructura", s.dashStructure); atLeast("dash estructura", s.dashStructure, floor.spinal)
                inRange("dash global", s.dashOverall)
                s.acwr?.let { if (!it.isFinite()) violations += "$where acwr=$it no es finito" }
            }
        }
        println("[SIM-INV] rango/suelos: valores comprobados=$values violaciones=${violations.size}")
        violations.take(10).forEach { println("[SIM-INV]   $it") }
        assertTrue(
            "Violaciones de rango/suelo: ${violations.size} (presupuesto $RANGE_VIOLATION_BUDGET). Primeras: ${violations.take(5)}",
            violations.size <= RANGE_VIOLATION_BUDGET,
        )
    }

    // ─── 2. Recuperación monótona entre muestras sin sesión nueva ─────────────

    @Test
    fun recovery_isMonotonic_betweenConsecutiveSamplesWithoutNewSession() {
        val violations = ArrayList<String>()
        var pairs = 0
        var comparisons = 0
        var increases = 0
        var maxDrop = 0
        trajectories.forEach { tr ->
            tr.samples.zipWithNext().forEach { (a, b) ->
                if (a.logCount != b.logCount) return@forEach // hubo sesión nueva entre ambas
                pairs++
                fun check(label: String, before: Int, after: Int) {
                    comparisons++
                    if (after > before) increases++
                    if (after < before) {
                        violations += "${tr.user.id} ${hoursOf(tr, a)}→${hoursOf(tr, b)} $label $before→$after"
                        maxDrop = max(maxDrop, before - after)
                    }
                }
                a.perMuscle.forEach { (muscle, v) -> check("músculo $muscle", v, b.perMuscle.getValue(muscle)) }
                check("muscular", a.muscular, b.muscular)
                check("energía", a.energy, b.energy)
                check("columna", a.spinal, b.spinal)
                check("energía cruda", a.energyRaw, b.energyRaw)
                check("columna cruda", a.spinalRaw, b.spinalRaw)
                check("dash muscular", a.dashMuscular, b.dashMuscular)
                check("dash energía", a.dashEnergy, b.dashEnergy)
                check("dash estructura", a.dashStructure, b.dashStructure)
                check("dash global", a.dashOverall, b.dashOverall)
            }
        }
        println("[SIM-INV] monotonía: pares sin sesión nueva=$pairs comparaciones=$comparisons subidas estrictas=$increases violaciones=${violations.size} caída máxima=$maxDrop")
        violations.take(15).forEach { println("[SIM-INV]   $it") }
        // Presupuesto medido hoy; si es > 0 lleva el comentario BUG CONOCIDO F-xx con la causa.
        assertTrue(
            "Violaciones de monotonía: ${violations.size} (presupuesto $MONOTONIC_VIOLATION_BUDGET). Primeras: ${violations.take(5)}",
            violations.size <= MONOTONIC_VIOLATION_BUDGET,
        )
        assertTrue("La prueba no comparó nada", comparisons > 0)
        assertTrue("La prueba es vacua: ninguna batería recuperó entre muestras", increases > 0)
    }

    // ─── 3. ACWR nulo con historial corto o disperso ──────────────────────────

    @Test
    fun acwr_isNullWhileTheHistoryIsShortOrHasFewSessions() {
        val violations = ArrayList<String>()
        var nulls = 0
        var nonNulls = 0
        trajectories.forEach { tr ->
            tr.samples.forEach { s ->
                val window = tr.history.sessions.filter {
                    it.completionMs >= s.tMs - 28L * DAY_MS && it.completionMs < s.tMs &&
                        (it.log.muscularImpactV2?.perMuscle?.values?.sumOf { m -> m.stressUnits } ?: 0.0) > 0.0
                }
                val expectNull = window.size < 4 || (s.tMs - window.minOf { it.completionMs }) < 14L * DAY_MS
                if (s.acwr == null) nulls++ else nonNulls++
                if (expectNull && s.acwr != null) {
                    violations += "${tr.user.id} ${hoursOf(tr, s)} ACWR=${s.acwr} con ${window.size} sesiones / <14 días"
                }
                if (!expectNull && s.acwr == null) {
                    violations += "${tr.user.id} ${hoursOf(tr, s)} ACWR nulo con ${window.size} sesiones y ≥14 días"
                }
            }
        }
        println("[SIM-INV] ACWR: muestras nulas=$nulls no nulas=$nonNulls violaciones=${violations.size}")
        violations.take(10).forEach { println("[SIM-INV]   $it") }
        assertTrue(
            "Violaciones del contrato de ACWR: ${violations.size} (presupuesto $ACWR_VIOLATION_BUDGET). Primeras: ${violations.take(5)}",
            violations.size <= ACWR_VIOLATION_BUDGET,
        )
        assertTrue("La prueba es vacua: ACWR siempre nulo", nonNulls > 0)
        assertTrue("La prueba es vacua: ACWR nunca nulo", nulls > 0)
    }

    // ─── 4. Monotonía del drenaje: series, RPE y carga ────────────────────────

    private fun withSets(exercises: List<CompletedExercise>, n: Int): List<CompletedExercise> = exercises.map { ex ->
        ex.copy(sets = List(n) { i -> ex.sets[min(i, ex.sets.size - 1)].copy(id = "${ex.exerciseId}-n$i") })
    }

    private fun withRpe(exercises: List<CompletedExercise>, rpe: Double): List<CompletedExercise> = exercises.map { ex ->
        ex.copy(sets = ex.sets.map { it.copy(rpe = rpe, actualIntensityValue = rpe, isFailure = false) })
    }

    private fun withLoad(exercises: List<CompletedExercise>, factor: Double): List<CompletedExercise> = exercises.map { ex ->
        ex.copy(sets = ex.sets.map { it.copy(weight = it.weight * factor) })
    }

    @Test
    fun drain_isMonotonic_inSets_rpe_andLoad() {
        val db = AugeSyntheticUserHarness.exerciseDb
        val violations = ArrayList<String>()
        var chains = 0
        var comparisons = 0

        // Fuentes: las sesiones de las trayectorias más el usuario F2 del modo completo (POWERLIFTER, 3/sem; basta su 1.ª
        // semana: la 1.ª sesión de cada plantilla no depende del resto), que reproduce la violación medida en ambos modos.
        val f2 = AugeSyntheticUserHarness.fullUsers()[1]
        val sources: List<Pair<SyntheticUser, List<SyntheticSession>>> = trajectories.map { it.user to it.history.sessions } +
            (if (users.none { it.id == f2.id }) listOf(f2 to AugeSyntheticUserHarness.buildHistory(f2.copy(weeks = 1)).sessions) else emptyList())

        sources.forEach { (user, sessions) ->
            val settings = AugeSyntheticUserHarness.settingsFor(user)
            val iso = "2025-03-03T19:00:00Z"
            // Una base por plantilla disponible del usuario.
            val bases = sessions.groupBy { it.template }.values.map { it.first() }
            bases.forEach { base ->
                // Normaliza a RPE 8 y 4 series por ejercicio: la cadena parte siempre del mismo punto.
                val prototype = withRpe(withSets(base.log.completedExercises, 4), 8.0)
                val chainsOf: List<Pair<String, List<List<CompletedExercise>>>> = listOf(
                    "series" to listOf(2, 3, 4, 5, 6, 8).map { withSets(prototype, it) },
                    "RPE" to listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0).map { withRpe(prototype, it) },
                    "carga" to listOf(0.5, 0.75, 1.0, 1.25, 1.5).map { withLoad(prototype, it) },
                )
                chainsOf.forEach { (factor, chain) ->
                    chains++
                    val drains = chain.map { AugeFatigueEngine.calculateCompletedSessionDrain(it, db, settings) }
                    val impacts = chain.map {
                        MuscularSessionImpactEngine.evaluate(
                            completedExercises = it,
                            completionInstantIso = iso,
                            exerciseDb = db,
                            settings = settings,
                        )
                    }
                    for (i in 0 until chain.size - 1) {
                        val where = "${user.id} ${base.template} más $factor paso $i→${i + 1}"
                        fun check(label: String, a: Number, b: Number) {
                            comparisons++
                            if (b.toDouble() + 1e-9 < a.toDouble()) violations += "$where $label ${a}→${b}"
                        }
                        check("drenaje SNC", drains[i].cns, drains[i + 1].cns)
                        check("drenaje muscular", drains[i].muscular, drains[i + 1].muscular)
                        check("drenaje columna", drains[i].spinal, drains[i + 1].spinal)
                        check("V2 global", impacts[i].globalMuscularDrain, impacts[i + 1].globalMuscularDrain)
                        impacts[i].perMuscle.forEach { (muscle, impact) ->
                            val next = impacts[i + 1].perMuscle[muscle] ?: return@forEach
                            check("V2 $muscle immediateDrainPct", impact.immediateDrainPct, next.immediateDrainPct)
                            check("V2 $muscle stressUnits", impact.stressUnits, next.stressUnits)
                        }
                    }
                }
            }
        }
        println("[SIM-INV] monotonía del drenaje: cadenas=$chains comparaciones=$comparisons violaciones=${violations.size}")
        violations.take(15).forEach { println("[SIM-INV]   $it") }
        // Cada canal amortigua su propio acumulado: ninguna subida de carga reduce el drenaje de otro canal.
        assertEquals(
            "Violaciones de monotonía del drenaje: ${violations.size} (presupuesto $DRAIN_VIOLATION_BUDGET). Primeras: ${violations.take(5)}",
            DRAIN_VIOLATION_BUDGET, violations.size,
        )
        assertTrue("La prueba no comparó nada", comparisons > 0)
    }

    // ─── 5. Determinismo y 6. Invariancia al tiempo absoluto ──────────────────

    @Test
    fun sameSeed_sameHash_andDifferentSeed_differentHash() {
        val user = users.first()
        val baseHash = AugeSyntheticUserHarness.hashOf(trajectories.first())
        val again = AugeSyntheticUserHarness.hashOf(AugeSyntheticUserHarness.trajectory(user))
        println("[SIM-INV] hash ${user.id} semilla=${user.seed}: $baseHash")
        assertEquals("Dos corridas con la misma semilla deben dar el mismo hash", baseHash, again)

        val short = user.copy(weeks = 2)
        val otherSeed = short.copy(seed = short.seed + 1)
        val hashA = AugeSyntheticUserHarness.hashOf(AugeSyntheticUserHarness.trajectory(short))
        val hashB = AugeSyntheticUserHarness.hashOf(AugeSyntheticUserHarness.trajectory(otherSeed))
        assertNotEquals("Otra semilla debe producir otra trayectoria (el hash no puede ser constante)", hashA, hashB)
    }

    @Test
    fun shiftingAllDatesBy364Days_keepsTheSameHash() {
        val user = users.first()
        val baseHash = AugeSyntheticUserHarness.hashOf(trajectories.first())
        val shifted = AugeSyntheticUserHarness.trajectory(
            user,
            startMs = AugeSyntheticUserHarness.BASE_START_MS + AugeSyntheticUserHarness.SHIFT_MS,
        )
        val shiftedHash = AugeSyntheticUserHarness.hashOf(shifted)
        println("[SIM-INV] hash desplazado +364 d ${user.id}: $shiftedHash")
        assertEquals(
            "Mismas trayectorias con fechas +364 días deben dar el mismo hash (un reloj escondido lo rompería)",
            baseHash,
            shiftedHash,
        )
    }

    // ─── 7. Paridad vista previa ↔ Home ───────────────────────────────────────

    @Test
    fun previewVsHome_divergence_isMeasured_andBudgeted() {
        val t0 = System.nanoTime()
        // Todas las sesiones con al menos 2 previas (la fatiga previa es lo que separa ambas rutas), en su versión real y
        // con dos variantes hipotéticas sobre el mismo historial: pesada (×1,5 series, RPE +1) y ligera (×0,6, RPE −1).
        val perTrajectory = AugeSyntheticUserHarness.parallelMap(trajectories) { tr ->
            tr.history.sessions.indices.filter { it >= 2 }.flatMap { index ->
                ParityVariant.entries.map { variant -> AugeSyntheticUserHarness.previewVsHome(tr.history, index, variant) }
            }
        }
        val all = perTrajectory.flatten()
        ParityVariant.entries.forEach { variant ->
            val rows = all.filter { it.variant == variant }
            rows.filter { it.variant == ParityVariant.ACTUAL }.takeLast(6).forEach { d ->
                println(
                    "[SIM-PARIDAD] usuario=${d.userId} sesión=${d.sessionIndex} plantilla=${d.template.label} " +
                        "sesiones10d=${d.sessionsIn10Days} maxMúsculo=${d.muscleDiffs[d.worstMuscle] ?: 0}(${d.worstMuscle}) " +
                        "muscular=${d.muscular} energía=${d.energy} columnaCruda=${d.spinalRaw} estructura=${d.structure}",
                )
            }
            val worst = rows.maxByOrNull { it.maxMuscleAbs }
            println(
                "[SIM-PARIDAD] RESUMEN variante=${variant.label} puntos=${rows.size} " +
                    "máx|Δ| por músculo=${rows.maxOfOrNull { it.maxMuscleAbs } ?: 0}" +
                    "(${worst?.worstMuscle} ${worst?.userId}#${worst?.sessionIndex} ${worst?.template?.label}) " +
                    "muscular=${rows.maxOfOrNull { abs(it.muscular) } ?: 0} energía=${rows.maxOfOrNull { abs(it.energy) } ?: 0} " +
                    "columnaCruda=${rows.maxOfOrNull { abs(it.spinalRaw) } ?: 0} estructura=${rows.maxOfOrNull { abs(it.structure) } ?: 0} " +
                    "global=${rows.maxOfOrNull { it.maxAbs } ?: 0} " +
                    "Δmúsculo>1 en ${rows.count { it.maxMuscleAbs > 1 }} de ${rows.size}",
            )
        }
        val overall = all.maxOfOrNull { it.maxAbs } ?: 0
        // Signo medio: vista previa − Home (negativo = la vista previa muestra MENOS batería que Home).
        val signedMuscle = all.flatMap { it.muscleDiffs.values }
        println(
            "[SIM-PARIDAD] RESUMEN GLOBAL puntos=${all.size} máx|Δ|=$overall " +
                "Δ medio por músculo=${String.format(java.util.Locale.ROOT, "%.2f", signedMuscle.average())} " +
                "tiempo=${(System.nanoTime() - t0) / 1_000_000} ms",
        )
        assertTrue("No se midió ningún punto de paridad", all.isNotEmpty())
        // BUG CONOCIDO F-23: la vista previa resta lineal por músculo, usa un log previo sin V2 y no coincide con Home.
        // Presupuesto = máximo medido hoy + 1; el paquete 2.3 (AugeSnapshotComputer único) lo baja a ≤ 1.
        assertTrue(
            "Divergencia preview↔Home $overall > presupuesto $PREVIEW_HOME_BUDGET",
            overall <= PREVIEW_HOME_BUDGET,
        )
    }

    // ─── Base de ejercicios heterogénea ───────────────────────────────────────

    @Test
    fun exerciseDb_isHeterogeneous_andCoversTheThirteenPillars() {
        val db = AugeSyntheticUserHarness.exerciseDb
        assertTrue("efc/cnc/ssc deben variar entre ejercicios", db.values.map { it.efc }.toSet().size >= 8)
        assertTrue(db.values.map { it.cnc }.toSet().size >= 8)
        assertTrue(db.values.map { it.ssc }.toSet().size >= 8)
        assertTrue("debe haber compuestos con axial 1,0 y presses sin axial", db.values.any { it.axialLoadFactor == 1.0 } && db.values.any { it.axialLoadFactor == 0.0 })
        val squat = db.getValue("sim-sentadilla")
        val bench = db.getValue("sim-banca")
        assertEquals(listOf(4.2, 4.5, 1.0, 1.0), listOf(squat.efc, squat.cnc, squat.ssc, squat.axialLoadFactor))
        assertEquals(listOf(3.2, 3.5, 0.35, 0.0), listOf(bench.efc, bench.cnc, bench.ssc, bench.axialLoadFactor))
        // Los 13 pilares aparecen en los V2 reales del historial (no solo en la ficha).
        val pillarsInV2 = trajectories.flatMap { tr -> tr.history.logs.flatMap { it.muscularImpactV2?.perMuscle?.keys.orEmpty() } }.toSet()
        AugeLearnerSimulator.PILLARS.forEach { pillar ->
            assertTrue("El pilar $pillar no aparece en ningún V2 (aparecen: $pillarsInV2)", pillar in pillarsInV2)
        }
    }

    // ─── 8. Conformidad de la ruta rápida del simulador con la ruta de Home ───

    @Test
    fun simulatorFastPath_matchesTheHomeRoute() {
        val tr = trajectories.first()
        val settings = AugeSyntheticUserHarness.settingsFor(tr.user)
        val db = AugeSyntheticUserHarness.exerciseDb
        val caches = listOf(AugeAdaptiveCache(), AugeLearnerSimulator.trueCache(1.25), AugeLearnerSimulator.trueCache(0.75))
        val muscles = listOf("Pectorales", "Cuádriceps", "Dorsales", "Erectores Espinales", "Tríceps")
        var checks = 0
        val mismatches = ArrayList<String>()
        val picks = tr.samples.indices.filter { it >= 20 && it % 18 == 0 }
        picks.forEach { idx ->
            val t = tr.samples[idx].tMs
            caches.forEachIndexed { c, cache ->
                val home = AugeSyntheticUserHarness.sample(tr.user, tr.history.logs, t, cache = cache, includeRaw = false)
                val causal = tr.history.logsUpTo(t)
                muscles.forEach { muscle ->
                    val cap = AugeLearnerSimulator.muscleCapacity(settings, db, muscle, causal, t)
                    val fast = AugeLearnerSimulator.muscleBattery(settings, db, muscle, causal, t, cache, cap)
                    checks++
                    if (fast != home.perMuscle.getValue(muscle)) mismatches += "caché $c +${(t - tr.startMs) / HOUR_MS}h $muscle fast=$fast home=${home.perMuscle[muscle]}"
                }
                val system = AugeLearnerSimulator.systemBatteries(settings, db, causal, t, cache)
                checks += 2
                if (system.cnc != home.energy) mismatches += "caché $c +${(t - tr.startMs) / HOUR_MS}h energía fast=${system.cnc} home=${home.energy}"
                if (system.spinal != home.spinal) mismatches += "caché $c +${(t - tr.startMs) / HOUR_MS}h columna fast=${system.spinal} home=${home.spinal}"
            }
        }
        println("[SIM-INV] conformidad ruta rápida: instantes=${picks.size} comparaciones=$checks discrepancias=${mismatches.size}")
        mismatches.take(10).forEach { println("[SIM-INV]   $it") }
        assertTrue("La ruta rápida del simulador no coincide con Home: ${mismatches.take(5)}", mismatches.isEmpty())
        assertTrue("La prueba no comparó nada", checks > 0)
    }
}
