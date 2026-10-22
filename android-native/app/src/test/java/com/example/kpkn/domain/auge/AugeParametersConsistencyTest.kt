package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugePerformanceContext
import com.example.kpkn.data.models.AugeParameterState
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * WP 2.1 (F-61): cada parámetro numérico del modelo de recuperación (τ por perfil y por pilar, C por canal, τ por defecto de
 * Energía y Columna, K de 95 %, límites de τ) tiene UNA sola definición, `AugeParameters`, y el resto del código la lee.
 *
 * Las tablas de aquí están escritas a mano a propósito: son la especificación independiente del código. Sin reloj real
 * (`nowOverride` en cada llamada) y sin aleatoriedad.
 */
class AugeParametersConsistencyTest {

    // ─── El registro: valores, versión y alias ────────────────────────────────

    @Test
    fun elRegistroFijaLosValoresDelModelo() {
        assertEquals(2, AugeParameters.VERSION)
        assertEquals(24.0, AugeParameters.TAU_FAST_H, 0.0)
        assertEquals(48.0, AugeParameters.TAU_MEDIUM_H, 0.0)
        assertEquals(72.0, AugeParameters.TAU_SLOW_H, 0.0)
        assertEquals(96.0, AugeParameters.TAU_HEAVY_H, 0.0)
        assertEquals(90.0, AugeParameters.C_MUSCLE, 0.0)
        assertEquals(75.0, AugeParameters.C_ENERGY, 0.0)
        assertEquals(70.0, AugeParameters.C_SPINAL, 0.0)
        assertEquals(36.0, AugeParameters.DEFAULT_TAU_ENERGY_H, 0.0)
        assertEquals(52.0, AugeParameters.DEFAULT_TAU_SPINAL_H, 0.0)
        assertEquals(48.0, AugeParameters.DEFAULT_TAU_GLOBAL_MUSCLE_H, 0.0)
        assertEquals(K95_ESPERADA, AugeParameters.TAU_K_95, 0.0)
        assertEquals("2,9957 es ln 20 redondeado a 4 decimales", ln(20.0), AugeParameters.TAU_K_95, 1e-4)
        assertEquals(18.0, AugeParameters.TAU_MIN_H, 0.0)
        assertEquals(144.0, AugeParameters.TAU_MAX_H, 0.0)
        assertEquals(
            mapOf("fast" to 24.0, "medium" to 48.0, "slow" to 72.0, "heavy" to 96.0),
            AugeParameters.PROFILE_TAU_HOURS,
        )
    }

    @Test
    fun losNombresHeredadosSonAliasDelRegistro() {
        assertEquals(AugeParameters.TAU_K_95, AugeUtils.TAU_K_95, 0.0)
        assertEquals(AugeParameters.C_MUSCLE, InitialRecoveryV2Model.C_MUSCLE, 0.0)
        assertEquals(AugeParameters.C_ENERGY, InitialRecoveryV2Model.C_ENERGY, 0.0)
        assertEquals(AugeParameters.C_SPINAL, InitialRecoveryV2Model.C_SPINAL, 0.0)
        assertEquals(AugeParameters.DEFAULT_TAU_ENERGY_H, InitialRecoveryV2Model.TAU_ENERGY_HOURS, 0.0)
        assertEquals(AugeParameters.DEFAULT_TAU_SPINAL_H, InitialRecoveryV2Model.TAU_SPINAL_HOURS, 0.0)
        assertEquals(AugeParameters.DEFAULT_TAU_GLOBAL_MUSCLE_H, InitialRecoveryV2Model.TAU_GLOBAL_MUSCLE_HOURS, 0.0)
    }

    @Test
    fun varianzaPorDefectoDeAugeParameterStateCoincideConP0() {
        val state = AugeParameterState()
        assertEquals(0.1225, AugeParameters.LEARNER_P0, 1e-12)
        assertEquals(AugeParameters.LEARNER_P0, state.variance, 1e-12)
    }

    @Test
    fun laVersionTieneUnaLineaDeKdocQueDiceCuandoSubirla() {
        val fuente = File(raizDeFuentes(), "domain/auge/AugeParameters.kt").readText(Charsets.UTF_8)
        val kdoc = Regex("""/\*\*[^*]*[Ss]úbela[^*]*cambie[^*]*\*/\s*const val VERSION""")
        assertTrue("Falta el KDoc de VERSION con la regla «súbela cuando cambie …»", kdoc.containsMatchIn(fuente))
    }

    // ─── (a) τ por músculo ────────────────────────────────────────────────────

    @Test
    fun lasTablasDeTauPorPilarCoincidenConLaTablaEscritaAMano() {
        assertEquals(TAU_ESPERADO_POR_PILAR, AugeParameters.PILLAR_TAU_HOURS)
        assertEquals(TAU_ESPERADO_POR_PILAR, InitialRecoveryV2Model.PILLAR_TAU_HOURS)
        for ((pilar, tau) in TAU_ESPERADO_POR_PILAR) {
            assertEquals("baseTauHours($pilar)", tau, AugeParameters.baseTauHours(pilar), 0.0)
            assertEquals("defaultRecoveryHours($pilar)", tau, AugeAdaptiveEngine.defaultRecoveryHours(pilar.lowercase()), 0.0)
        }
    }

    @Test
    fun elMotorUsaElTauDelPerfilEnLosCuarentaMusculosConPerfil() {
        // A 24 h el warp muscular deja 12 h efectivas: con ESTRES = 750 los cuatro τ posibles dan 93 · 86 · 82 · 80,
        // así que la batería mostrada identifica el τ del motor sin ambigüedad.
        val candidatos = TAU_DEL_PERFIL_ANTES.values.toList()
        val fallos = mutableListOf<String>()
        for ((musculo, perfil) in PERFIL_DEL_MOTOR_ANTES) {
            val esperado = TAU_DEL_PERFIL_ANTES.getValue(perfil)
            val mostrado = puntuacionMusculo(musculo, horas = 24)
            val compatibles = candidatos.filter { tau ->
                AugeUtils.batteryPercent(bateriaRealMusculo(ESTRES, 24.0, tau)) == mostrado
            }
            if (compatibles != listOf(esperado)) {
                fallos += "$musculo ($perfil): el motor da $mostrado a 24 h; τ compatibles $compatibles; esperado $esperado"
            }
        }
        assertTrue("τ del motor distinto del perfil (${fallos.size}): $fallos", fallos.isEmpty())
    }

    @Test
    fun elMotorUsaElTauDelPlanEnLosTrecePilares() {
        // 24 h: Bíceps, Tríceps, Deltoides, Pantorrillas, Abdomen · 48 h: Pectorales, Dorsales, Trapecio, Core ·
        // 72 h: Cuádriceps, Glúteos · 96 h: Isquiosurales, Erectores Espinales.
        val candidatos = listOf(24.0, 48.0, 72.0, 96.0)
        val fallos = mutableListOf<String>()
        for ((pilar, esperado) in TAU_ESPERADO_POR_PILAR) {
            val mostrado = puntuacionMusculo(pilar, horas = 24)
            val compatibles = candidatos.filter { tau ->
                AugeUtils.batteryPercent(bateriaRealMusculo(ESTRES, 24.0, tau)) == mostrado
            }
            if (compatibles != listOf(esperado)) {
                fallos += "$pilar: el motor da $mostrado a 24 h; τ compatibles $compatibles; esperado $esperado"
            }
        }
        assertTrue("τ del motor distinto de la tabla del plan (${fallos.size}): $fallos", fallos.isEmpty())
    }

    @Test
    fun profileOfYBaseTauHoursSiguenLaReglaDelMotorDeAntes() {
        val nombres = PERFIL_DEL_MOTOR_ANTES.map { it.first } +
            listOf("Gluteo Mayor", "Soleo", "BICEPS", "  pectorales  ", "cuadriceps", "Tibial Anterior", "Hombro", "Foo", "")
        for (nombre in nombres) {
            assertEquals("profileOf(«$nombre»)", perfilDeAntes(nombre), AugeParameters.profileOf(nombre))
            assertEquals("baseTauHours(«$nombre»)", tauDeAntes(nombre), AugeParameters.baseTauHours(nombre), 0.0)
        }
        assertEquals(PERFIL_DEL_MOTOR_ANTES.map { it.first }, AugeParameters.PROFILED_MUSCLES)
    }

    @Test
    fun losTrecePilaresYLasGuardasSonUnaSolaListaEnElMismoOrden() {
        assertEquals(PILARES_EN_ORDEN, AugeParameters.PILLARS)
        assertEquals(PILARES_EN_ORDEN, AugeRecoveryEngine.pillarMuscles)
        assertEquals(PILARES_EN_ORDEN, InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS)
        assertEquals(PILARES_EN_ORDEN, AugeParameters.PILLAR_TAU_HOURS.keys.toList())
        assertEquals(listOf("Erectores Espinales", "Dorsales", "Trapecio"), AugeParameters.SPINE_GUARD_MUSCLES)
        assertEquals(AugeParameters.SPINE_GUARD_MUSCLES, AugeRecoveryEngine.SPINE_GUARD_MUSCLES)
    }

    @Test
    fun elConjuntoCompletoDeBateriasPorMusculoNoCambia() {
        // Los 13 pilares y, después, el pilar de cada músculo con perfil, sin repetir: lo que arma BATTERY_MUSCLES.
        val esperado = (PILARES_EN_ORDEN + PERFIL_DEL_MOTOR_ANTES.map { getAugeMusclePillarId(it.first) }).distinct()
        val real = AugeRecoveryEngine.getPerMuscleBatteries(
            history = emptyList(), wellbeing = null, settings = Settings(),
            nowOverrideMs = ORIGEN, zone = ZoneId.of("UTC"),
        ).keys.toList()
        assertEquals(esperado, real)
        assertEquals(PILARES_EN_ORDEN, real.take(PILARES_EN_ORDEN.size))
    }

    @Test
    fun laMediaVidaDeInterferenciaSeDerivaDelRegistroSinCambiarSusValores() {
        val campo = InterferenceEngine::class.java.getDeclaredField("MUSCLE_HALF_LIFE").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val real = campo.get(null) as Map<String, Double>
        assertEquals(MEDIA_VIDA_ANTES.keys.toList(), real.keys.toList())
        assertEquals(MEDIA_VIDA_ANTES, real)
        for ((musculo, horas) in real) {
            assertEquals("media vida de $musculo", AugeParameters.baseTauHours(musculo), horas, 0.0)
        }
    }

    @Test
    fun lasDiferenciasDelAprendizSonExactamenteLasDeclaradas() {
        // Los once nombres con perfil que no estaban en la tabla del aprendiz dejan su respaldo de 48 h.
        val cambian = PERFIL_DEL_MOTOR_ANTES.map { it.first }.filter { musculo ->
            aprendizDeAntes(musculo) != AugeAdaptiveEngine.defaultRecoveryHours(musculo.lowercase())
        }
        assertEquals(DIFERENCIAS_DEL_APRENDIZ.keys.toList(), cambian)
        for ((musculo, antesYDespues) in DIFERENCIAS_DEL_APRENDIZ) {
            assertEquals("antes, $musculo", antesYDespues.first, aprendizDeAntes(musculo), 0.0)
            assertEquals("después, $musculo", antesYDespues.second, AugeAdaptiveEngine.defaultRecoveryHours(musculo.lowercase()), 0.0)
        }

        // El aprendiz recibe ids de pilar (PerformanceTauLearner y las baterías por músculo): lo que puede ver es el conjunto
        // de BATTERY_MUSCLES. De los once músculos afectados solo «Costales» es su propio id; los otros diez se resuelven a un
        // pilar cuyo τ no cambia.
        val queVe = (PILARES_EN_ORDEN + PERFIL_DEL_MOTOR_ANTES.map { getAugeMusclePillarId(it.first) }).toSet()
        val alcanzables = cambian.filter { it in queVe }
        assertEquals(listOf("Costales"), alcanzables)
        for (musculo in cambian - alcanzables.toSet()) {
            val pilar = getAugeMusclePillarId(musculo)
            assertEquals(
                "el pilar $pilar de $musculo conserva su τ",
                aprendizDeAntes(pilar),
                AugeAdaptiveEngine.defaultRecoveryHours(pilar.lowercase()),
                0.0,
            )
        }
    }

    @Test
    fun lasObservacionesRealesDelAprendizIncluyenCostalesEntreLosGruposAdicionales() {
        val fichas = PERFIL_DEL_MOTOR_ANTES.mapIndexed { indice, (musculo, _) ->
            val id = "musculo-$indice"
            id to ExerciseMuscleInfo(
                id = id, name = "Referencia $indice", equipment = "barra", efc = 3.0, cnc = 2.0, ssc = 0.1,
                axialLoadFactor = 0.0,
                involvedMuscles = listOf(InvolvedMuscle(musculo, MuscleRole.PRIMARY, volumeContribution = 1.0)),
            )
        }.toMap()
        val ejercicios = fichas.map { (id, ficha) ->
            CompletedExercise(exerciseId = id, exerciseDbId = id, canonicalExerciseId = id, exerciseName = ficha.name)
        }
        val claves = ejercicios.map { requireNotNull(PerformanceTauLearner.primaryAugePillarFor(it, fichas)) }.toSet()
        val baterias = AugeRecoveryEngine.getPerMuscleBatteries(
            history = emptyList(), wellbeing = null, settings = Settings(), nowOverrideMs = ORIGEN, zone = ZoneId.of("UTC"),
        )
        assertEquals(baterias.keys, claves)
        assertEquals(
            setOf("Antebrazo", "Costales", "Redondo Menor", "Infraespinoso", "Supraespinoso", "Subescapular",
                "Serrato Anterior", "Aductores", "Cuello", "Psoas"),
            claves - PILARES_EN_ORDEN.toSet(),
        )

        fun sesion(id: String, horasAtras: Long, peso: Double): WorkoutLog {
            val iso = Instant.ofEpochMilli(ORIGEN - horasAtras * HORA_MS).toString()
            val base = sesionV2("Costales", ESTRES)
            val impacto = requireNotNull(base.muscularImpactV2)
            return base.copy(
                id = id, date = iso,
                completedExercises = ejercicios.map { ejercicio ->
                    ejercicio.copy(sets = listOf(CompletedSet(id = "$id-${ejercicio.exerciseId}", weight = peso, reps = 5, rpe = 8.0)))
                },
                muscularImpactV2 = impacto.copy(
                    completionInstantIso = iso,
                    perMuscle = claves.associateWith { impacto.perMuscle.getValue("Costales") },
                    involvedVolumeMuscles = claves,
                ),
                performanceContext = AugePerformanceContext(sessionStartTimeMs = ORIGEN - horasAtras * HORA_MS, isDeload = false, deloadOrigin = "hierarchy_normal"),
            )
        }
        val resultado = PerformanceTauLearner.observations(
            PerformanceTauInput(
                historyWithoutToday = listOf(sesion("h3", 18L * 24, 80.0), sesion("h2", 12L * 24, 80.0), sesion("h1", 48, 80.0)),
                today = sesion("hoy", 0, 72.0), nowMs = ORIGEN, exerciseDb = fichas,
                predictedEnergy = 100, predictedStructure = 100, predictedMuscles = claves.associateWith { 70 },
                manual = PerformanceTauManualTouches(energy = true, structure = true),
            ),
        )
        val obsKeys = resultado.observations.map { it.parameterKey }.toSet()
        val expectedKeys = claves.map { it.lowercase() }.toSet()
        assertEquals(expectedKeys, obsKeys)
        // 3.4: el puente aplica en el EKF v4, registra observaciones y sella lastPerformanceLearnLogId.
        val nuevo = PerformanceTauLearner.applyToCache(AugeAdaptiveCache(), resultado, "hoy", ORIGEN)
        assertEquals("hoy", nuevo.lastPerformanceLearnLogId)
        assertTrue(nuevo.observations.isNotEmpty())
    }

    @Test
    fun elMotorConservaElRespaldoSinPerfilYLosLimitesDelTauAprendido() {
        assertCercano("Sin perfil: τ medio", puntuacionMusculo("Tibial Anterior", 24), bateriaRealMusculo(ESTRES, 24.0, 48.0))
        for ((aprendido, limitado) in listOf(6.0 to 18.0, 18.0 to 18.0, 144.0 to 144.0, 200.0 to 144.0)) {
            val estado = AugeRecoveryEngine.calculateMuscleBattery(
                muscleName = "Pectorales", history = listOf(sesionV2("Pectorales", ESTRES)), wellbeing = null,
                settings = Settings(), precomputedCapacity = CAPACIDAD, nowOverride = ORIGEN + 24 * HORA_MS,
                adaptiveCache = AugeAdaptiveCache(personalizedRecoveryHours = mapOf("pectorales" to aprendido)),
            )
            assertCercano("τ aprendido $aprendido limitado a $limitado", estado.recoveryScore, bateriaRealMusculo(ESTRES, 24.0, limitado))
        }
    }

    // ─── (b) C y τ por defecto de cada canal ──────────────────────────────────

    @Test
    fun elMusculoUsaCMuscleYElTauDelPerfilEnVariasHoras() {
        val fallos = mutableListOf<String>()
        for ((pilar, tau) in TAU_ESPERADO_POR_PILAR) {
            for (horas in HORAS_DE_PRUEBA) {
                val mano = bateriaRealMusculo(ESTRES, horas.toDouble(), tau, AugeParameters.C_MUSCLE, AugeParameters.TAU_K_95)
                val motor = puntuacionMusculo(pilar, horas)
                if (abs(motor - mano) > TOLERANCIA) fallos += "$pilar a $horas h: motor $motor, a mano ${fmt(mano)}"
            }
        }
        assertTrue("El músculo se aparta de 100·exp(−R/C) (${fallos.size}): $fallos", fallos.isEmpty())
        // Sensibilidad: con otra C o con otro τ el resultado a mano cambiaría en más de un punto; si no, la prueba no distingue.
        val c = AugeParameters.C_MUSCLE
        assertTrue(abs(bateriaRealMusculo(ESTRES, 0.0, 48.0, c * 1.2) - bateriaRealMusculo(ESTRES, 0.0, 48.0, c)) > 1.0)
        assertTrue(abs(bateriaRealMusculo(ESTRES, 24.0, 60.0, c) - bateriaRealMusculo(ESTRES, 24.0, 48.0, c)) > 1.0)
    }

    @Test
    fun lasHorasParaRecuperarseDelMusculoUsanCMuscle() {
        // Pectorales (τ 48 h) recién entrenados: carga residual 750; el objetivo (90 % de batería) es −C·ln 0,9 · capacidad / 100.
        val estado = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = listOf(sesionV2("Pectorales", ESTRES)), wellbeing = null,
            settings = Settings(), precomputedCapacity = CAPACIDAD, nowOverride = ORIGEN,
        )
        fun horasAMano(c: Double): Int {
            val objetivo = -c * ln(0.9) * CAPACIDAD / 100.0
            val k = AugeParameters.TAU_K_95 / AugeParameters.baseTauHours("Pectorales")
            return max(0.0, -ln(objetivo / ESTRES) / k).toInt()
        }
        assertEquals(horasAMano(AugeParameters.C_MUSCLE), estado.hoursToRecovery)
        assertTrue(horasAMano(AugeParameters.C_MUSCLE * 1.2) != horasAMano(AugeParameters.C_MUSCLE))
    }

    @Test
    fun laEnergiaUsaCEnergyYElTauPorDefectoEnVariasHoras() {
        for (horas in HORAS_DE_PRUEBA) {
            val motor = AugeRecoveryEngine.calculateSystemicFatigue(
                history = historiaDeCarreras(), wellbeing = null, settings = Settings(), nowOverride = ORIGEN + horas * HORA_MS,
            ).first
            assertCercano("Energía a $horas h", motor, energiaAMano(horas.toDouble()))
        }
        assertTrue("A 0 h la Energía debe estar lejos de 100 y del piso", energiaAMano(0.0) in 40.0..90.0)
        val c = AugeParameters.C_ENERGY
        val tau = AugeParameters.DEFAULT_TAU_ENERGY_H
        assertTrue(abs(energiaAMano(0.0, c = c * 1.2) - energiaAMano(0.0)) > 1.0)
        assertTrue(abs(energiaAMano(24.0, tau = tau * 1.25) - energiaAMano(24.0)) > 1.0)
    }

    @Test
    fun laColumnaUsaCSpinalYElTauPorDefectoEnVariasHoras() {
        for (horas in HORAS_DE_PRUEBA) {
            val motor = AugeRecoveryEngine.calculateSpinalBattery(
                history = historiaDeCarreras(), wellbeing = null, settings = Settings(), nowOverride = ORIGEN + horas * HORA_MS,
            )
            assertCercano("Columna a $horas h", motor, columnaAMano(horas.toDouble()))
        }
        assertTrue("A 0 h la Columna debe estar lejos de 100 y del piso", columnaAMano(0.0) in 40.0..90.0)
        val c = AugeParameters.C_SPINAL
        val tau = AugeParameters.DEFAULT_TAU_SPINAL_H
        assertTrue(abs(columnaAMano(0.0, c = c * 1.2) - columnaAMano(0.0)) > 1.0)
        assertTrue(abs(columnaAMano(24.0, tau = tau * 1.25) - columnaAMano(24.0)) > 1.0)
    }

    @Test
    fun laLeyDeAnclaConservaElValorYUsaElTauPorDefectoDeCadaCanal() {
        // Con ancla, batería(t) = 100·(ancla/100)^d(t): C se cancela entre la inversión y la ley directa, así que a 0 h
        // devuelve el valor anclado solo si ambas copias de C son la misma; a más horas manda el τ por defecto del canal.
        val ancla = 60
        val bienestar = DailyWellbeingLog(
            id = "ancla", date = "2026-09-12",
            manualNeuralBattery = ancla, manualSpinalBattery = ancla,
            manualMuscleBatteries = mapOf("Pectorales" to ancla),
            manualBatteryAnchorMs = ORIGEN,
        )
        val k95 = AugeParameters.TAU_K_95
        fun ley(restante: Double): Double = 100.0 * (ancla / 100.0).pow(restante)
        fun energia(h: Double, tau: Double = AugeParameters.DEFAULT_TAU_ENERGY_H) = ley(exp(-k95 / tau * h))
        fun columna(h: Double, tau: Double = AugeParameters.DEFAULT_TAU_SPINAL_H) =
            ley(exp(-k95 / tau * AugeUtils.getSpinalRecoveryHours(h)))
        fun musculo(h: Double, tau: Double = AugeParameters.baseTauHours("Pectorales")) =
            ley(exp(-k95 / tau * AugeUtils.getSigmoidalHours(h)))

        for (horas in HORAS_DE_PRUEBA) {
            val ahora = ORIGEN + horas * HORA_MS
            val h = horas.toDouble()
            assertCercano(
                "Energía anclada a $horas h",
                AugeRecoveryEngine.calculateSystemicFatigue(
                    history = emptyList(), wellbeing = bienestar, settings = Settings(), nowOverride = ahora,
                ).first,
                energia(h),
            )
            assertCercano(
                "Columna anclada a $horas h",
                AugeRecoveryEngine.calculateSpinalBattery(
                    history = emptyList(), wellbeing = bienestar, settings = Settings(), nowOverride = ahora,
                ),
                columna(h),
            )
            assertCercano(
                "Músculo anclado a $horas h",
                AugeRecoveryEngine.calculateMuscleBattery(
                    muscleName = "Pectorales", history = emptyList(), wellbeing = bienestar, settings = Settings(),
                    precomputedCapacity = CAPACIDAD, nowOverride = ahora,
                ).recoveryScore,
                musculo(h),
            )
        }
        assertTrue(abs(energia(24.0, tau = AugeParameters.DEFAULT_TAU_ENERGY_H * 1.25) - energia(24.0)) > 1.0)
        assertTrue(abs(columna(24.0, tau = AugeParameters.DEFAULT_TAU_SPINAL_H * 1.25) - columna(24.0)) > 1.0)
        assertTrue(abs(musculo(24.0, tau = 60.0) - musculo(24.0)) > 1.0)
    }

    @Test
    fun elAnclaDelAnilloMuscularGlobalUsaElTauPorDefectoDelRegistro() {
        // Sin τ aprendidos, el ajuste manual del anillo muscular decae con DEFAULT_TAU_GLOBAL_MUSCLE_H y el warp muscular.
        val ancla = 35
        val bienestar = DailyWellbeingLog(
            id = "ancla-global", date = "2026-09-12", manualMuscularBattery = ancla, manualBatteryAnchorMs = ORIGEN,
        )
        val k95 = AugeParameters.TAU_K_95
        // El anillo muscular manual no pasa por la desaceleración ni por el ACWR: batería = 100 − (100 − ancla)·exp(−k·warp(h)).
        fun anilloAMano(horas: Double, tau: Double = AugeParameters.DEFAULT_TAU_GLOBAL_MUSCLE_H): Double =
            100.0 - (100.0 - ancla) * exp(-k95 / tau * AugeUtils.getSigmoidalHours(horas))
        val fallos = mutableListOf<String>()
        for (horas in HORAS_DE_PRUEBA) {
            val global = AugeRecoveryEngine.calculateGlobalBatteries(
                history = emptyList(), wellbeing = bienestar, settings = Settings(),
                precomputedMuscles = emptyMap(), nowOverrideMs = ORIGEN + horas * HORA_MS,
            ).muscular
            val mano = anilloAMano(horas.toDouble())
            if (abs(global - mano) > TOLERANCIA) fallos += "a $horas h: anillo $global, a mano ${fmt(mano)}"
        }
        assertTrue("El anillo muscular manual se aparta de su ley (${fallos.size}): $fallos", fallos.isEmpty())
        assertTrue(abs(anilloAMano(24.0, tau = AugeParameters.DEFAULT_TAU_GLOBAL_MUSCLE_H * 1.25) - anilloAMano(24.0)) > 1.0)
    }

    // ─── (c) guarda estructural: ningún parámetro del modelo fuera del registro ─

    @Test
    fun ningunParametroDelModeloViveFueraDelRegistro() {
        val hallazgos = archivosEscaneados(raizDeFuentes()).flatMap { hallazgosEn(it.name, it.readText(Charsets.UTF_8)) }
        val sinPermiso = hallazgos.filterNot { h -> PERMITIDOS.any { it.cubre(h) } }
        assertTrue(
            "Quedan ${sinPermiso.size} copias de parámetros del modelo fuera de AugeParameters.kt " +
                "(archivo:línea [regla] texto):\n" + sinPermiso.joinToString("\n") { " - $it" },
            sinPermiso.isEmpty(),
        )
        // Una excepción que ya no tiene hallazgo es una excepción vieja: se borra para que la lista no mienta.
        val sinUso = PERMITIDOS.filter { p -> hallazgos.none { p.cubre(it) } }
        assertTrue(
            "Excepciones sin uso, bórralas: " + sinUso.joinToString { "${it.archivo} [${it.regla}] «${it.contiene}»" },
            sinUso.isEmpty(),
        )
    }

    @Test
    fun lasReglasDelEscaneoDetectanLasFormasQueBuscan() {
        fun reglas(texto: String): Set<String> = hallazgosEn("Muestra.kt", texto).map { it.regla }.toSet()

        assertTrue("mapa-de-tau" in reglas("    private val MUSCLE_PROFILE_MAP = mapOf("))
        assertTrue("mapa-de-tau" in reglas("    private val RECOVERY_PROFILES = mapOf("))
        assertTrue("mapa-de-tau" in reglas("    val PILLAR_TAU_HOURS: Map<String, Double> = mapOf("))
        assertTrue("mapa-de-tau" in reglas("    private val MUSCLE_HALF_LIFE: Map<String, Double> = mapOf("))
        assertTrue("tau-por-clave" in reglas("        \"fast\"   to 24.0,"))
        assertTrue("tau-por-clave" in reglas("            \"biceps\" -> 24.0"))
        assertTrue("tau-por-clave" in reglas("        \"Pectorales\" to 48.0, \"Dorsales\" to 48.0,"))
        assertTrue("constante-k95" in reglas("        val impliedTau = 2.9957 / k"))
        assertTrue("constante-k95" in reglas("        val k = ln(20.0) / tau"))
        assertTrue("c-en-el-exponente" in reglas("        val p = clamp(100.0 * (1.0 - safeExp(-rawFatiguePct / 90.0)), 0.0, 100.0)"))
        assertTrue("c-en-el-exponente" in reglas("        val drop = 100.0 * (1.0 - exp(-rawPct / 70.0))"))
        assertTrue("c-en-el-exponente" in reglas("        val n = 100.0 * (1.0 - safeExp(-rawGymPct / 75.0))"))
        assertTrue("c-en-la-inversa" in reglas("            val impliedRawGymPct = -75.0 * ln(1.0 - penalty / 100.0)"))
        assertTrue("c-en-la-inversa" in reglas("            val target = -90.0 * ln(0.9) * capacity / 100.0"))
        assertTrue("tau-por-defecto" in reglas("        val baseTau = adaptiveCache.cnsRecoveryHours ?: 36.0"))
        assertTrue("tau-por-defecto" in reglas("        val tauHours = adaptiveCache.spinalRecoveryHours ?: 52.0"))
        assertTrue("tau-por-defecto" in reglas("                ?: 48.0"))
        assertTrue("limites-de-tau" in reglas("        val t = clamp(adaptiveHours ?: perfil ?: 48.0, 18.0, 144.0)"))
        assertTrue("limites-de-tau" in reglas("        return current + (muscleKey to clamp(newTau, 12.0, 144.0))"))
        assertTrue(
            "lista-de-pilares" in reglas(
                "    private val PILLAR_MUSCLES = listOf(\n        \"Pectorales\", \"Dorsales\", \"Deltoides\", \"Bíceps\", \"Tríceps\",\n    )",
            ),
        )
        assertTrue("lista-de-pilares" in reglas("    internal val SPINE_GUARD_MUSCLES = listOf(\"Erectores Espinales\", \"Dorsales\", \"Trapecio\")"))

        // Lo que NO debe saltar: el registro, los comentarios y las cadenas con «//».
        assertTrue(reglas("        val c = AugeParameters.C_MUSCLE * AugeParameters.TAU_K_95").isEmpty())
        assertTrue(reglas("        val t = clamp(x, AugeParameters.TAU_MIN_H, AugeParameters.TAU_MAX_H)").isEmpty())
        assertTrue(reglas("    val PILLAR_TAU_HOURS: Map<String, Double> = AugeParameters.PILLAR_TAU_HOURS").isEmpty())
        assertTrue(reglas("        // tau = 2.9957 / k, exp(-rawPct / 70.0), ?: 36.0").isEmpty())
        assertTrue(reglas("    /** C de la columna: `exp(-rawPct / 70.0)`, `-90.0 * ln(0.9)` y `?: 52.0`. */").isEmpty())
        assertTrue(reglas("    /*\n     * RECOVERY_PROFILES = mapOf(\n     * 2.9957 y ?: 48.0\n     */\n    val x = 1").isEmpty())
        assertTrue(reglas("        val u = \"https://ejemplo.org/a\" // 2.9957 ?: 36.0").isEmpty())
    }

    @Test
    fun elEscaneoLeeLasFuentesDeAugeYNoElRegistro() {
        val nombres = archivosEscaneados(raizDeFuentes()).map { it.name }
        assertTrue("El escaneo leyó pocos archivos (${nombres.size})", nombres.size >= 25)
        for (esperado in listOf(
            "AugeRecoveryEngine.kt", "AugeAdaptiveEngine.kt", "AugeUtils.kt", "InitialRecoveryEvidencePolicy.kt",
            "InterferenceEngine.kt", "AugeViewModel.kt", "InitialRecoveryEvidence.kt", "RingAdjustMath.kt",
        )) {
            assertTrue("El escaneo no incluye $esperado", esperado in nombres)
        }
        assertTrue("El registro no debe escanearse a sí mismo", "AugeParameters.kt" !in nombres)
    }

    // ─── Fixtures y fórmulas a mano ───────────────────────────────────────────

    /** Sesión con impacto V2 congelado en [ORIGEN]: el músculo recibe [estres] y la capacidad al cierre es [CAPACIDAD]. */
    private fun sesionV2(musculo: String, estres: Double): WorkoutLog {
        val iso = Instant.ofEpochMilli(ORIGEN).toString()
        return WorkoutLog(
            id = "v2-$musculo", programId = "p", sessionId = "s", sessionName = "Referencia",
            date = iso, durationMinutes = 60,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 30,
                perMuscle = mapOf(
                    musculo to MuscleSessionImpactV2(
                        stressUnits = estres, capacityAtCompletion = CAPACIDAD, immediateDrainPct = estres / 10.0,
                        directStressUnits = estres, indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf(musculo),
                setInputHash = "h", contextHash = "c",
            ),
        )
    }

    /** Batería mostrada del músculo [horas] h después de la sesión V2, sin ancla y sin τ aprendido (caché vacío). */
    private fun puntuacionMusculo(musculo: String, horas: Int): Int =
        AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = musculo,
            history = listOf(sesionV2(musculo, ESTRES)),
            wellbeing = null,
            settings = Settings(),
            precomputedCapacity = CAPACIDAD,
            nowOverride = ORIGEN + horas * HORA_MS,
        ).recoveryScore

    /**
     * Batería REAL (Double) del músculo con la fórmula del motor: `100·exp(−R/C)`, R = % de fatiga restante (como
     * `BatteryRoundingTest`). Por defecto usa la C y la K escritas a mano en esta prueba.
     */
    private fun bateriaRealMusculo(
        estres: Double,
        horas: Double,
        tau: Double,
        c: Double = C_MUSCULO_ESPERADA,
        k95: Double = K95_ESPERADA,
    ): Double {
        val k = k95 / tau
        val restante = estres * exp(-k * AugeUtils.getSigmoidalHours(horas))
        // Con ≤ 0,1 unidades de carga residual el motor fija 100 exacto.
        if (restante <= 0.1) return 100.0
        val crudoPct = restante / CAPACIDAD * 100.0
        return 100.0 * exp(-crudoPct / c)
    }

    private val detallesCarrera = CardioDetails(type = CardioType.RUN_OUTDOOR, targetDurationSeconds = DURACION_CARRERA_S)

    /** Carrera de 70 min a RPE 9 sin bloques: la carga de Energía y Columna se puede reproducir con piezas públicas del motor. */
    private fun sesionCarrera(indice: Int): WorkoutLog = WorkoutLog(
        id = "carrera-$indice", programId = "p", sessionId = "s", sessionName = "Carrera",
        date = Instant.ofEpochMilli(ORIGEN).toString(), durationMinutes = DURACION_CARRERA_S / 60,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "run", exerciseName = "Carrera", exerciseDbId = "run", restTime = 0,
                cardioDetails = detallesCarrera,
                sets = listOf(
                    CompletedSet(id = "run-$indice", weight = 0.0, reps = 0, rpe = RPE_CARRERA, timeSeconds = DURACION_CARRERA_S),
                ),
            ),
        ),
    )

    /** Tres carreras idénticas a la vez: triplican la carga y dejan Energía y Columna lejos de 100 y del piso. */
    private fun historiaDeCarreras(): List<WorkoutLog> = List(CARRERAS) { sesionCarrera(it) }

    /**
     * Energía de las carreras [horas] h después, a mano: R = Σ(drenaje·0,85·e^(−k·h)) / capacidad · 100, batería = 100·exp(−R/C).
     * La capacidad del canal, `máx(80; tanque·1,15)`, se escribe suelta aquí como especificación; el motor la lee de
     * [AugeParameters.energyCapacity].
     */
    private fun energiaAMano(
        horas: Double,
        c: Double = AugeParameters.C_ENERGY,
        tau: Double = AugeParameters.DEFAULT_TAU_ENERGY_H,
    ): Double {
        val ajustes = Settings()
        val tanques = AugeFatigueEngine.calculatePersonalizedBatteryTanks(ajustes)
        val drenaje = CardioRingDrainEngine.drain(detallesCarrera, DURACION_CARRERA_S, RPE_CARRERA, ajustes)
        val tope = (100 - AugeUtils.physiologicalFloor(ajustes).cns).coerceAtLeast(5).toDouble()
        val porSesion = AugeUtils.applySessionSoftCap(drenaje.cns * AugeUtils.SESSION_CONSERVATION_FACTOR, 0.0, tope)
        val carga = CARRERAS * porSesion * exp(-AugeParameters.TAU_K_95 / tau * horas)
        val capacidad = max(80.0, tanques.cns * 1.15)
        return 100.0 * exp(-(carga / capacidad * 100.0) / c)
    }

    /** Columna de las carreras [horas] h después, a mano; el warp espinal añade 18 h gradualmente entre las 12 y las 24 h. */
    private fun columnaAMano(
        horas: Double,
        c: Double = AugeParameters.C_SPINAL,
        tau: Double = AugeParameters.DEFAULT_TAU_SPINAL_H,
    ): Double {
        val ajustes = Settings()
        val tanques = AugeFatigueEngine.calculatePersonalizedBatteryTanks(ajustes)
        val drenaje = CardioRingDrainEngine.drain(detallesCarrera, DURACION_CARRERA_S, RPE_CARRERA, ajustes)
        val tope = (100 - AugeUtils.physiologicalFloor(ajustes).spinal).coerceAtLeast(5).toDouble()
        val porSesion = AugeUtils.applySessionSoftCap(drenaje.spinal * AugeUtils.SESSION_CONSERVATION_FACTOR, 0.0, tope)
        val carga = CARRERAS * porSesion * exp(-AugeParameters.TAU_K_95 / tau * AugeUtils.getSpinalRecoveryHours(horas))
        val capacidad = max(70.0, tanques.spinal * 0.02)
        return 100.0 * exp(-(carga / capacidad * 100.0) / c)
    }

    /** El motor muestra el entero más cercano: la diferencia con la batería real a mano no pasa de medio punto. */
    private fun assertCercano(mensaje: String, motor: Int, mano: Double) {
        assertTrue("$mensaje: motor $motor, a mano ${fmt(mano)}", abs(motor - mano) <= TOLERANCIA)
    }

    private fun fmt(v: Double): String = "%.4f".format(Locale.ROOT, v)

    // ─── La regla de antes, escrita a mano ────────────────────────────────────

    private fun normalizarComoElMotor(s: String): String = s
        .lowercase().trim()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")

    /** `MUSCLE_PROFILE_MAP.entries.firstOrNull { normKey(it.key) == normKey(muscleName) }?.value` de antes de 2.1. */
    private fun perfilDeAntes(musculo: String): String? =
        PERFIL_DEL_MOTOR_ANTES.firstOrNull { normalizarComoElMotor(it.first) == normalizarComoElMotor(musculo) }?.second

    /** `RECOVERY_PROFILES[profileKey ?: "medium"] ?: 48.0` de antes de 2.1. */
    private fun tauDeAntes(musculo: String): Double = TAU_DEL_PERFIL_ANTES[perfilDeAntes(musculo) ?: "medium"] ?: 48.0

    /** Lo que daba `AugeAdaptiveEngine.defaultRecoveryHours` antes de 2.1 (48 h para todo lo que no estaba en su `when`). */
    private fun aprendizDeAntes(musculo: String): Double = APRENDIZ_ANTES[normalizarComoElMotor(musculo)] ?: 48.0

    // ─── Escaneo de fuentes ───────────────────────────────────────────────────

    private class Regla(val id: String, val descripcion: String, val patron: Regex)

    private class Hallazgo(val archivo: String, val linea: Int, val regla: String, val texto: String) {
        override fun toString() = "$archivo:$linea [$regla] $texto"
    }

    /** Excepción documentada del escaneo: la línea de [archivo] que contiene [contiene] puede incumplir [regla]; [motivo] dice por qué. */
    private class Permitido(val archivo: String, val regla: String, val contiene: String, val motivo: String) {
        fun cubre(h: Hallazgo) = h.archivo == archivo && h.regla == regla && contiene in h.texto
    }

    private fun raizDeFuentes(): File {
        val relativo = "src/main/java/com/example/kpkn"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, relativo), File(dir, "app/$relativo")).firstOrNull { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("No encuentro $relativo subiendo desde ${File("").absolutePath}")
    }

    /** Los archivos .kt de `domain/auge` salvo el registro, más los llamadores externos que también tenían copias. */
    private fun archivosEscaneados(raiz: File): List<File> {
        val auge = File(raiz, "domain/auge").listFiles { f -> f.isFile && f.extension == "kt" }
            .orEmpty().filter { it.name != "AugeParameters.kt" }.sortedBy { it.name }
        val extras = ARCHIVOS_EXTRA.map { File(raiz, it) }
        extras.forEach { check(it.isFile) { "El escaneo espera encontrar $it: si se movió, actualiza ARCHIVOS_EXTRA" } }
        return auge + extras
    }

    private fun hallazgosEn(nombre: String, texto: String): List<Hallazgo> {
        val limpio = sinComentarios(texto)
        val lineas = texto.split('\n')
        return REGLAS.flatMap { regla ->
            regla.patron.findAll(limpio).map { m ->
                val linea = limpio.substring(0, m.range.first).count { it == '\n' } + 1
                Hallazgo(nombre, linea, regla.id, lineas.getOrNull(linea - 1)?.trim().orEmpty())
            }.toList()
        }.distinctBy { Triple(it.archivo, it.linea, it.regla) }
    }

    /**
     * Quita comentarios de línea y de bloque (KDoc incluido) respetando cadenas, cadenas crudas y literales de carácter.
     * Los comentarios se sustituyen por espacios y los saltos de línea se conservan: los números de línea no se mueven.
     */
    private fun sinComentarios(texto: String): String {
        val salida = StringBuilder(texto.length)
        val n = texto.length
        var i = 0
        while (i < n) {
            val c = texto[i]
            val d = if (i + 1 < n) texto[i + 1] else '\u0000'
            when {
                c == '/' && d == '/' -> {
                    while (i < n && texto[i] != '\n') { salida.append(' '); i++ }
                }
                c == '/' && d == '*' -> {
                    var profundidad = 0
                    while (i < n) {
                        val a = texto[i]
                        val b = if (i + 1 < n) texto[i + 1] else '\u0000'
                        if (a == '/' && b == '*') { profundidad++; salida.append("  "); i += 2 }
                        else if (a == '*' && b == '/') { profundidad--; salida.append("  "); i += 2; if (profundidad == 0) break }
                        else { salida.append(if (a == '\n') '\n' else ' '); i++ }
                    }
                }
                c == '"' && texto.startsWith("\"\"\"", i) -> {
                    val cierre = texto.indexOf("\"\"\"", i + 3)
                    val fin = if (cierre < 0) n else cierre + 3
                    salida.append(texto, i, fin)
                    i = fin
                }
                c == '"' -> {
                    salida.append(c); i++
                    while (i < n && texto[i] != '"' && texto[i] != '\n') {
                        if (texto[i] == '\\' && i + 1 < n) { salida.append(texto[i]); i++ }
                        salida.append(texto[i]); i++
                    }
                    if (i < n && texto[i] == '"') { salida.append('"'); i++ }
                }
                c == '\'' -> {
                    val cierre = texto.indexOf('\'', if (d == '\\') i + 3 else i + 2)
                    val fin = if (cierre in 0..(i + 8)) cierre + 1 else i + 1
                    salida.append(texto, i, fin)
                    i = fin
                }
                else -> { salida.append(c); i++ }
            }
        }
        return salida.toString()
    }

    private companion object {
        val ORIGEN: Long = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        const val HORA_MS = 3_600_000L

        /** Horas tras la sesión o el ancla en que se compara el motor con la fórmula a mano. */
        val HORAS_DE_PRUEBA = listOf(0, 6, 12, 18, 24, 36, 48)
        const val CAPACIDAD = 2_600.0
        const val ESTRES = 750.0

        /** Medio punto de redondeo del motor, más un resquicio de coma flotante. */
        const val TOLERANCIA = 0.5 + 1e-9

        const val CARRERAS = 3
        const val DURACION_CARRERA_S = 70 * 60
        const val RPE_CARRERA = 9.0

        /** C del músculo (90) y K de 95 % (2,9957), escritas a mano: la especificación contra la que se compara el motor. */
        const val C_MUSCULO_ESPERADA = 90.0
        const val K95_ESPERADA = 2.9957

        /** τ base de cada pilar según el plan, escrito a mano a propósito: no se calcula de ningún mapa del código. */
        val TAU_ESPERADO_POR_PILAR: Map<String, Double> = linkedMapOf(
            "Pectorales" to 48.0, "Dorsales" to 48.0, "Deltoides" to 24.0, "Bíceps" to 24.0, "Tríceps" to 24.0,
            "Cuádriceps" to 72.0, "Isquiosurales" to 96.0, "Glúteos" to 72.0, "Pantorrillas" to 24.0,
            "Abdomen" to 24.0, "Trapecio" to 48.0, "Erectores Espinales" to 96.0, "Core" to 48.0,
        )
        val PILARES_EN_ORDEN: List<String> = TAU_ESPERADO_POR_PILAR.keys.toList()

        /** τ de cada perfil tal como estaba en `AugeRecoveryEngine.RECOVERY_PROFILES` antes de 2.1. */
        val TAU_DEL_PERFIL_ANTES: Map<String, Double> =
            linkedMapOf("fast" to 24.0, "medium" to 48.0, "slow" to 72.0, "heavy" to 96.0)

        /** Músculo → perfil, copia del `MUSCLE_PROFILE_MAP` del motor antes de 2.1 (mismo orden). */
        val PERFIL_DEL_MOTOR_ANTES: List<Pair<String, String>> = listOf(
            "Bíceps" to "fast", "Tríceps" to "fast", "Deltoides" to "fast",
            "Deltoides Anterior" to "fast", "Deltoides Lateral" to "fast", "Deltoides Posterior" to "fast",
            "Pantorrillas" to "fast", "Abdomen" to "fast", "Antebrazo" to "fast",
            "Gemelos" to "fast", "Sóleo" to "fast", "Oblicuos" to "fast", "Costales" to "fast",
            "Pectorales" to "medium", "Dorsales" to "medium", "Hombros" to "medium", "Trapecio" to "medium",
            "Romboide" to "medium", "Redondo Menor" to "medium", "Redondo Mayor" to "medium",
            "Infraespinoso" to "medium", "Supraespinoso" to "medium", "Subescapular" to "medium",
            "Serrato Anterior" to "medium",
            "Cuádriceps" to "slow", "Glúteos" to "slow",
            "Glúteo Mayor" to "slow", "Glúteo Medio" to "slow", "Glúteo Menor" to "slow",
            "Aductores" to "medium", "Tensor Fascia Lata" to "medium",
            "Isquiosurales" to "heavy", "Bíceps Femoral" to "heavy", "Semitendinoso" to "heavy",
            "Semimembranoso" to "heavy", "Erectores Espinales" to "heavy", "Core" to "medium",
            "Cuello" to "medium", "Psoas" to "medium", "Lumbar" to "heavy",
        )

        /** Lo que daba el `when` de `AugeAdaptiveEngine.defaultRecoveryHours` antes de 2.1 (claves ya normalizadas). */
        val APRENDIZ_ANTES: Map<String, Double> = mapOf(
            "biceps" to 24.0, "triceps" to 24.0, "deltoides" to 24.0, "deltoides anterior" to 24.0,
            "deltoides lateral" to 24.0, "deltoides posterior" to 24.0, "pantorrillas" to 24.0, "abdomen" to 24.0,
            "antebrazo" to 24.0,
            "pectorales" to 48.0, "dorsales" to 48.0, "hombros" to 48.0, "trapecio" to 48.0, "aductores" to 48.0,
            "core" to 48.0, "cuello" to 48.0,
            "cuadriceps" to 72.0, "gluteos" to 72.0,
            "isquiosurales" to 96.0, "erectores espinales" to 96.0,
        )

        /**
         * Diferencia declarada de 2.1: los once músculos con perfil que el aprendiz trataba como 48 h y ahora reciben el τ del
         * motor (antes → después). Solo «Costales» llega al aprendiz con su propio nombre; los demás se resuelven a un pilar.
         */
        val DIFERENCIAS_DEL_APRENDIZ: Map<String, Pair<Double, Double>> = linkedMapOf(
            "Gemelos" to (48.0 to 24.0), "Sóleo" to (48.0 to 24.0), "Oblicuos" to (48.0 to 24.0), "Costales" to (48.0 to 24.0),
            "Glúteo Mayor" to (48.0 to 72.0), "Glúteo Medio" to (48.0 to 72.0), "Glúteo Menor" to (48.0 to 72.0),
            "Bíceps Femoral" to (48.0 to 96.0), "Semitendinoso" to (48.0 to 96.0), "Semimembranoso" to (48.0 to 96.0),
            "Lumbar" to (48.0 to 96.0),
        )

        /** Media vida de `InterferenceEngine.MUSCLE_HALF_LIFE` antes de 2.1 (mismo orden). */
        val MEDIA_VIDA_ANTES: Map<String, Double> = linkedMapOf(
            "Bíceps" to 24.0, "Tríceps" to 24.0, "Deltoides" to 24.0,
            "Deltoides Anterior" to 24.0, "Deltoides Lateral" to 24.0, "Deltoides Posterior" to 24.0,
            "Pantorrillas" to 24.0, "Abdomen" to 24.0, "Antebrazo" to 24.0,
            "Pectorales" to 48.0, "Dorsales" to 48.0, "Hombros" to 48.0, "Trapecio" to 48.0,
            "Core" to 48.0, "Aductores" to 48.0, "Cuello" to 48.0,
            "Cuádriceps" to 72.0, "Glúteos" to 72.0,
            "Isquiosurales" to 96.0, "Erectores Espinales" to 96.0,
        )

        /** Archivos de fuera de `domain/auge` que también tenían copias de τ por defecto o de la lista de pilares. */
        val ARCHIVOS_EXTRA = listOf(
            "screens/auge/AugeViewModel.kt", "data/models/InitialRecoveryEvidence.kt", "ui/components/rings/RingAdjustMath.kt",
        )

        val REGLAS = listOf(
            Regla(
                "mapa-de-tau", "mapa de τ por perfil o por músculo",
                Regex("""\b(?:RECOVERY_PROFILES|MUSCLE_PROFILE_MAP|PILLAR_TAU_HOURS|MUSCLE_HALF_LIFE)\b[^=\n]*=\s*mapOf\s*\("""),
            ),
            Regla(
                "tau-por-clave", "texto → 24 · 48 · 72 · 96 h (τ de perfil escrito a mano)",
                Regex(""""[^"\n]+"\s*(?:to|->)\s*(?:24|48|72|96)\.0\b"""),
            ),
            Regla(
                "constante-k95", "2,9957 o ln(20) escritos a mano",
                Regex("""\b2\.9957\b|\bln\(\s*20(?:\.0)?\s*\)"""),
            ),
            Regla(
                "c-en-el-exponente", "C del canal dentro de exp(−x / 90 · 75 · 70)",
                Regex("""\b(?:safeExp|exp)\(\s*-\s*[A-Za-z_]\w*\s*/\s*(?:90|75|70)(?:\.0)?\s*\)"""),
            ),
            Regla(
                "c-en-la-inversa", "C del canal en −90 · 75 · 70 · ln(…)",
                Regex("""-\s*(?:90|75|70)\.0\s*\*\s*(?:kotlin\.math\.)?ln\("""),
            ),
            Regla(
                "tau-por-defecto", "τ por defecto de un canal escrito tras «?:» (36 · 48 · 52 h)",
                Regex("""\?:\s*(?:36|48|52)\.0\b"""),
            ),
            Regla(
                "limites-de-tau", "límites de τ (12 o 18 … 144 h) escritos en un clamp",
                Regex("""\bclamp\([^()]*,\s*(?:12|18)\.0\s*,\s*144\.0\s*\)"""),
            ),
            Regla(
                "lista-de-pilares", "lista de los 13 pilares o de las guardas de Columna escrita a mano",
                Regex(
                    """listOf\(\s*"Pectorales"\s*,\s*"Dorsales"\s*,\s*"Deltoides"\s*,""" +
                        """|listOf\(\s*"Erectores Espinales"\s*,\s*"Dorsales"\s*,\s*"Trapecio"\s*\)""",
                ),
            ),
        )

        /**
         * Lo que se decide dejar fuera del registro. Cada entrada nombra archivo, regla y un trozo de la línea, y da el motivo.
         * Las tres excepciones de los límites del aprendiz EMA de `AugeAdaptiveEngine` se borraron en 3.3 junto con el EMA
         * (los límites del EKF viven en `AugeParameters.LEARNER_TAU_*`): hoy no queda ninguna copia permitida.
         */
        val PERMITIDOS = emptyList<Permitido>()
    }
}
