package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationStatus
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Caso de uso único del aprendizaje manual (paquete 3.3), sobre el motor real y el corpus del máster dorado.
 *
 * Todo con reloj y zona explícitos (UTC): el instante del gesto y el del log son datos de la prueba. Cubre la identidad
 * `(canal/músculo, ancla, valor, logId)`, una observación por objetivo, la misma base anterior para todos los objetivos,
 * el dedupe persistido ANTES de evaluar, las copias, el cierre a t≈0, el ajuste global `state_only`, la sesión relevante
 * y la fila de bienestar que se persiste con el caché en una sola transacción (modelada aquí sin Room).
 */
class ManualGestureLearningTest {

    private val casos: List<GoldenCase> by lazy { AugeGoldenCorpus.build(AugeGoldenCorpus.BASE_MS) }
    private val a01: GoldenCase get() = casos.first { it.id == "A01" }
    private val hora = AugeGoldenCorpus.HORA_MS

    /** 12 h después de la sesión de A01: la lectura de Energía, Columna y Cuádriceps está en su zona sensible. */
    private val instante: Long get() = a01.origin + 12L * hora

    private fun inputs(
        caso: GoldenCase = a01,
        nowMs: Long = instante,
        cache: AugeAdaptiveCache = AugeAdaptiveCache(),
        wellbeing: DailyWellbeingLog? = caso.wellbeing,
        history: List<WorkoutLog> = caso.history,
    ) = AugeSnapshotInputs(
        history = history,
        wellbeing = wellbeing,
        settings = caso.settings,
        exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
        nowMs = nowMs,
        zone = AugeGoldenCorpus.ZONE,
        adaptiveCache = cache,
    )

    private fun tauPrior(key: String): Double = exp(AugeAdaptiveCacheMigration.prior(key).mean)

    /** Lo que la persona vería si su τ verdadero fuese el doble del prior (señal clara, dentro de los límites de τ): una sensación claramente peor que la prevista. */
    private fun sensacion(
        key: String,
        base: AugeSnapshotInputs = inputs(),
        factor: Double = 2.0,
        nowMs: Long = instante,
    ): Int = SnapshotObservationModel(base, nowMs, ObservationScale.SHOWN)
        .predict(key, ln(tauPrior(key) * factor)).battery.roundToInt()

    private fun prediccionPrevia(base: AugeSnapshotInputs, key: String, nowMs: Long = instante): Double =
        SnapshotObservationModel(base, nowMs, ObservationScale.SHOWN)
            .predict(key, AugeAdaptiveCacheMigration.prior(key).mean).battery

    private fun planDe(
        base: AugeSnapshotInputs,
        observedAtMs: Long = instante,
        logId: String? = null,
        request: ManualRequest,
    ) = ManualGestureLearning.plan(base, observedAtMs, logId, request)

    private fun cuatroObjetivos(base: AugeSnapshotInputs = inputs()) = ManualRequest(
        neural = sensacion("cns", base),
        spinalDisplayed = sensacion("spinal", base),
        muscles = mapOf("Cuádriceps" to sensacion("Cuádriceps", base), "Glúteos" to sensacion("Glúteos", base)),
    )

    // ─── Una observación por objetivo, con la tupla canónica como id ──────────────────────────────

    @Test
    fun unGestoDeCuatroObjetivosProduceCuatroIdsUnicosYCadaNSubeComoMaximoUno() {
        val base = inputs()
        val plan = planDe(base, request = cuatroObjetivos(base))
        val resultados = plan.evaluation.outcomes

        assertEquals(setOf("cns", "spinal", "cuádriceps", "glúteos"), resultados.map { it.key }.toSet())
        assertEquals("4 ids únicos", 4, resultados.map { it.observationId }.toSet().size)
        assertEquals("4 registros en el anillo", 4, plan.evaluation.cache.observations.size)
        for (r in resultados) {
            assertTrue("${r.key}: n sube como máximo 1 (${r.nBefore} → ${r.nAfter})", r.nAfter - r.nBefore in 0..1)
            assertNotEquals("${r.key} no es un duplicado", ManualTargetStatus.DUPLICATE, r.status)
        }
        // Cada id es EXACTAMENTE la tupla (canal/músculo, ancla, valor, logId).
        for (r in resultados) {
            val registro = plan.evaluation.cache.observations.single { it.id == r.observationId }
            assertEquals(
                AugeAdaptiveCacheMigration.observationId(r.key, instante, registro.y, r.relevantLogId),
                r.observationId,
            )
            assertEquals(instante, registro.anchorMs)
        }
        assertTrue("alguna observación debe haberse admitido", resultados.any { it.status == ManualTargetStatus.ADMITTED })
    }

    @Test
    fun todosLosObjetivosCompartenLaMismaBaseAnterior_ningunAnclaNiPosteriorEntraEnElHDeOtro() {
        val base = inputs()
        val request = cuatroObjetivos(base)
        val plan = planDe(base, request = request)

        // h de cada objetivo = lectura del motor con las entradas ANTERIORES (caché sin el gesto, sin anclas nuevas).
        for (r in plan.evaluation.outcomes) {
            val esperado = prediccionPrevia(base, r.key)
            assertEquals("h de ${r.key} sale de la base anterior", esperado, r.predicted ?: Double.NaN, 1e-9)
        }
        // Y el orden de los objetivos no cambia ninguna predicción ni ningún posterior.
        val invertido = planDe(
            base,
            request = ManualRequest(
                neural = request.neural,
                spinalDisplayed = request.spinalDisplayed,
                muscles = request.muscles.entries.reversed().associate { it.key to it.value },
            ),
        )
        assertEquals(
            plan.evaluation.outcomes.associate { it.key to it.predicted },
            invertido.evaluation.outcomes.associate { it.key to it.predicted },
        )
        assertEquals(plan.evaluation.cache.parameterStates, invertido.evaluation.cache.parameterStates)
    }

    @Test
    fun laColumnaAprendeConLaYMostradaYGuardaElEstadoInvertidoConLasEntradasPrevias() {
        val base = inputs()
        val visible = sensacion("spinal", base)
        val plan = planDe(base, request = ManualRequest(spinalDisplayed = visible))

        val core = AugeSnapshotComputer.compute(base.copy(nowMs = instante))
        assertEquals("el estado guarda la Columna cruda invertida", invertDisplayedSpine(core, visible), plan.accepted.spinalStored)
        val registro = plan.evaluation.cache.observations.single()
        assertEquals("el aprendiz recibe la y mostrada, no la cruda", visible.toDouble(), registro.y, 0.0)
        assertEquals("spinal", registro.parameterKey)
    }

    // ─── Idempotencia: el dedupe persistido se detecta ANTES de evaluar o reanclar ────────────────

    @Test
    fun laSegundaEntregaIdenticaAnadeCeroYLaTerceraTambien() {
        val base = inputs()
        val request = cuatroObjetivos(base)
        val primera = planDe(base, request = request)
        val tras1 = base.copy(adaptiveCache = primera.evaluation.cache)

        val segunda = planDe(tras1, request = request)
        val tercera = planDe(base.copy(adaptiveCache = segunda.evaluation.cache), request = request)

        assertTrue("la segunda entrega no acepta nada", segunda.accepted.isEmpty())
        assertEquals(setOf("cns", "spinal", "cuádriceps", "glúteos"), segunda.dropped.filterValues { it == "duplicado" }.keys)
        assertSame("sin cambios: el mismo caché", tras1.adaptiveCache, segunda.evaluation.cache)
        assertEquals(primera.evaluation.cache, tercera.evaluation.cache)
        assertEquals("1 registro por objetivo", 4, tercera.evaluation.cache.observations.size)
        assertEquals(
            primera.evaluation.cache.parameterStates.mapValues { it.value.n },
            tercera.evaluation.cache.parameterStates.mapValues { it.value.n },
        )
    }

    @Test
    fun elReplayTrasSerializarYRecrearElViewModelEsIdentico() {
        val base = inputs()
        val request = cuatroObjetivos(base)
        val primera = planDe(base, request = request)
        val json = AugeAdaptiveCacheMigration.encodeAdaptiveCache(primera.evaluation.cache)
        val recreado = AugeAdaptiveCacheMigration.decodeAdaptiveCache(json).cache

        val replay = planDe(base.copy(adaptiveCache = recreado), request = request)

        assertTrue(replay.accepted.isEmpty())
        assertEquals(recreado, replay.evaluation.cache)
        assertEquals(primera.evaluation.cache.parameterStates, replay.evaluation.cache.parameterStates)
        assertEquals(primera.evaluation.cache.observations, replay.evaluation.cache.observations)
    }

    @Test
    fun unGestoAntiguoQueLlegaTrasOtroMasNuevoNoPisaElEstadoNiEnsena() {
        val base = inputs()
        val nuevo = planDe(base, observedAtMs = instante + hora, request = ManualRequest(neural = sensacion("cns", base)))
        val trasNuevo = base.copy(adaptiveCache = nuevo.evaluation.cache)

        // Reentrega del gesto anterior (otro valor, ancla anterior a la marca de agua): duplicado, no reancla.
        val viejo = planDe(trasNuevo, observedAtMs = instante, request = ManualRequest(neural = sensacion("cns", base, factor = 1.5)))

        assertEquals("duplicado", viejo.dropped["cns"])
        assertTrue(viejo.accepted.isEmpty())
        assertSame(trasNuevo.adaptiveCache, viejo.evaluation.cache)
    }

    // ─── Copias idénticas y estado vigente (V1/V2 fusionados) ─────────────────────────────────────

    @Test
    fun unaCopiaIdenticaAlEstadoVigenteNoCreaObservacionNiReancla() {
        val vigente = DailyWellbeingLog(
            id = "hoy", date = "2025-03-04",
            manualNeuralBattery = 61, manualNeuralAnchorMs = instante - hora, manualBatteryAnchorMs = instante - hora,
            manualMuscleBatteries = mapOf("Cuádriceps" to 55),
            manualMuscleAnchorMs = mapOf("Cuádriceps" to instante - hora),
            manualMuscleOverridesV2 = mapOf(
                "Glúteos" to ManualMuscleBatteryOverride(battery = 48, anchorEpochMs = instante - hora, automaticBatteryAtAnchor = 70),
            ),
        )
        val base = inputs(wellbeing = vigente)

        val plan = planDe(
            base,
            request = ManualRequest(neural = 61, muscles = mapOf("Cuádriceps" to 55, "Glúteos" to 48)),
        )

        assertTrue(plan.accepted.isEmpty())
        assertEquals(setOf("cns", "cuádriceps", "glúteos"), plan.dropped.keys)
        assertTrue(plan.dropped.values.all { it == "sin_cambio" })
        assertSame(base.adaptiveCache, plan.evaluation.cache)
        assertTrue(plan.evaluation.outcomes.isEmpty())
    }

    @Test
    fun soloSeAceptaLoQueRealmenteCambia() {
        val vigente = DailyWellbeingLog(
            id = "hoy", date = "2025-03-04",
            manualNeuralBattery = 61, manualNeuralAnchorMs = instante - hora, manualBatteryAnchorMs = instante - hora,
        )
        val base = inputs(wellbeing = vigente)

        val plan = planDe(base, request = ManualRequest(neural = 61, muscles = mapOf("Cuádriceps" to sensacion("Cuádriceps", base))))

        assertNull(plan.accepted.neural)
        assertEquals(setOf("Cuádriceps"), plan.accepted.muscles.keys)
        assertEquals(listOf("cuádriceps"), plan.evaluation.outcomes.map { it.key })
    }

    @Test
    fun unaAnclaPreviaDeEnergiaInfluyeEnSuPrediccionYNoEnLasDeOtrosCanales() {
        // Mismo bienestar de base en ambos mundos: solo difiere el ancla de Energía.
        val basal = DailyWellbeingLog(id = "hoy", date = "2025-03-03")
        val sinAncla = inputs(wellbeing = basal)
        val conAncla = inputs(
            wellbeing = basal.copy(
                manualNeuralBattery = 35, manualNeuralAnchorMs = a01.origin + 2L * hora, manualBatteryAnchorMs = a01.origin + 2L * hora,
            ),
        )

        val hSin = prediccionPrevia(sinAncla, "cns")
        val hCon = prediccionPrevia(conAncla, "cns")

        assertTrue("el ancla previa de Energía cambia h de Energía ($hSin vs $hCon)", abs(hSin - hCon) > 1.0)
        assertEquals("la ancla de Energía no toca Cuádriceps", prediccionPrevia(sinAncla, "Cuádriceps"), prediccionPrevia(conAncla, "Cuádriceps"), 1e-9)
        assertEquals("ni la Columna", prediccionPrevia(sinAncla, "spinal"), prediccionPrevia(conAncla, "spinal"), 1e-9)
    }

    // ─── Aprende con la predicción previa aunque el ancla nueva coincida con y ───────────────────

    @Test
    fun aprendeConLaPrediccionPreviaYNoConLaQueResultaDeAnclarLaNuevaBateria() {
        val base = inputs()
        val y = sensacion("cns", base)
        val bueno = planDe(base, request = ManualRequest(neural = y)).evaluation.outcomes.single()
        assertEquals(ManualTargetStatus.ADMITTED, bueno.status)
        assertTrue("h previa distinta de y por al menos la zona muerta (h=${bueno.predicted}, y=$y)", abs(y - (bueno.predicted ?: 0.0)) >= 3.0)

        // Implementación defectuosa: anclar primero y evaluar después → h ≈ y → zona muerta, nunca aprende.
        val tarde = inputs(
            wellbeing = DailyWellbeingLog(
                id = "hoy", date = "2025-03-03",
                manualNeuralBattery = y, manualNeuralAnchorMs = instante, manualBatteryAnchorMs = instante,
            ),
        )
        val defectuoso = ManualGestureLearning.evaluate(
            tarde,
            ManualGestureCommand(instante, null, listOf(ManualGestureTarget("cns", y))),
        ).outcomes.single()
        assertNotEquals("anclar → h=y → no aprende: eso es lo que se evita", ManualTargetStatus.ADMITTED, defectuoso.status)
        assertTrue(abs(y - (defectuoso.predicted ?: Double.NaN)) < 3.0)
    }

    @Test
    fun unaCorreccionRealCensuradaAnclaEstadoConRegistroUnicoYCeroCambiosDeTau() {
        val base = inputs()
        val h = prediccionPrevia(base, "cns").roundToInt()

        // Dentro de la zona muerta: el estado se ancla, el registro queda con su motivo y τ no se mueve.
        val plan = planDe(base, request = ManualRequest(neural = h + 1))

        assertEquals(h + 1, plan.accepted.neural)
        val r = plan.evaluation.outcomes.single()
        assertEquals(ManualTargetStatus.CENSORED, r.status)
        assertEquals("zona_muerta", r.reason)
        assertEquals(1, plan.evaluation.cache.observations.size)
        assertNull(plan.evaluation.cache.cnsRecoveryHours)
        assertEquals(0, r.nAfter - r.nBefore)
        assertEquals(r.tauBeforeHours ?: 0.0, r.tauAfterHours ?: 0.0, 0.0)
    }

    // ─── Cierre: el log persistido manda y a t≈0 solo ancla ───────────────────────────────────────

    private fun logDelCierre(): WorkoutLog = a01.history.last()

    @Test
    fun elCierreACeroMediaYSieteCoNoventaYNueveHorasCambiaTauCeroVeces() {
        val log = logDelCierre()
        val logMs = AugeUtils.logDateMs(log)
        for (minutos in listOf(0L, 30L, 7L * 60 + 59)) {
            val observado = logMs + minutos * 60_000L
            val base = inputs(nowMs = observado)
            val y = sensacion("cns", base, nowMs = observado)
            val plan = planDe(
                base, observedAtMs = observado, logId = log.id,
                request = ManualRequest(
                    neural = y,
                    muscles = mapOf("Cuádriceps" to sensacion("Cuádriceps", base, nowMs = observado)),
                ),
            )

            assertFalse("a $minutos min el cierre ancla el estado", plan.accepted.isEmpty())
            for (r in plan.evaluation.outcomes) {
                assertEquals("a $minutos min ${r.key}", ManualTargetStatus.CENSORED, r.status)
                assertEquals("ventana", r.reason)
                assertEquals(log.id, r.relevantLogId)
                assertEquals(r.tauBeforeHours ?: 0.0, r.tauAfterHours ?: 0.0, 0.0)
                assertEquals(0, r.nAfter - r.nBefore)
            }
            assertNull(plan.evaluation.cache.cnsRecoveryHours)
            assertTrue(plan.evaluation.cache.personalizedRecoveryHours.isEmpty())
            assertEquals("el cierre no rescata una sesión anterior", 2, plan.evaluation.cache.observations.size)
        }
    }

    @Test
    fun aOchoHorasSobreLaSesionRelevanteConSenalSePermiteUnaActualizacion() {
        val log = logDelCierre()
        val observado = AugeUtils.logDateMs(log) + 8L * hora
        val base = inputs(nowMs = observado)
        val y = sensacion("cns", base, nowMs = observado)

        val plan = planDe(base, observedAtMs = observado, logId = log.id, request = ManualRequest(neural = y))

        val r = plan.evaluation.outcomes.single()
        assertEquals(8.0, r.hoursSinceLoad ?: Double.NaN, 1e-9)
        assertEquals(ManualTargetStatus.ADMITTED, r.status)
        assertEquals(1, r.nAfter - r.nBefore)
        assertNotNull(plan.evaluation.cache.cnsRecoveryHours)
        assertTrue("τ de Energía se alarga", (r.tauAfterHours ?: 0.0) > (r.tauBeforeHours ?: 0.0))
    }

    @Test
    fun elIdDelCierreUsaElLogPersistidoYNoElUltimoQueParezcaReciente() {
        val log = logDelCierre()
        val observado = AugeUtils.logDateMs(log)
        val extra = log.copy(id = "log-mas-reciente-en-apariencia", date = java.time.Instant.ofEpochMilli(observado + 60_000L).toString())
        val base = inputs(nowMs = observado, history = a01.history + extra)

        val plan = planDe(base, observedAtMs = observado, logId = log.id, request = ManualRequest(neural = 40))

        assertEquals(log.id, plan.evaluation.outcomes.single().relevantLogId)
        assertEquals(
            AugeAdaptiveCacheMigration.observationId("cns", observado, 40.0, log.id),
            plan.evaluation.outcomes.single().observationId,
        )
    }

    // ─── Sesión relevante por canal (Home) ────────────────────────────────────────────────────────

    @Test
    fun unaEdicionDePectoralesTrasUnCurlRecienteUsaLaUltimaSesionDePectorales() {
        val empuje = casos.first { it.id == "A02" }.history.last()
        val curl = casos.first { it.id == "A05" }.history.last()
        val ahora = a01.origin + 80L * hora
        val historial = listOf(
            empuje.copy(id = "empuje-hace-72h", date = java.time.Instant.ofEpochMilli(ahora - 72L * hora).toString()),
            curl.copy(id = "curl-hace-1h", date = java.time.Instant.ofEpochMilli(ahora - hora).toString()),
        )
        val base = inputs(caso = a01, nowMs = ahora, history = historial)

        val plan = planDe(
            base, observedAtMs = ahora,
            request = ManualRequest(neural = 40, muscles = mapOf("Pectorales" to 40)),
        )

        val pecho = plan.evaluation.outcome("Pectorales")
        val energia = plan.evaluation.outcome("cns")
        assertEquals("empuje-hace-72h", pecho?.relevantLogId)
        assertEquals(72.0, pecho?.hoursSinceLoad ?: Double.NaN, 1e-6)
        assertEquals("para Energía vale el último log global", "curl-hace-1h", energia?.relevantLogId)
        assertEquals("ventana", energia?.reason)
    }

    @Test
    fun sinSesionRelevanteElEstadoSeAncla_conRegistroUnicoYMotivo() {
        val base = inputs(history = emptyList())

        val plan = planDe(base, request = ManualRequest(neural = 55))

        val r = plan.evaluation.outcomes.single()
        assertEquals(ManualTargetStatus.CENSORED, r.status)
        assertEquals(ManualGestureKeys.REASON_NO_LOAD, r.reason)
        assertNull(r.relevantLogId)
        assertEquals(55, plan.accepted.neural)
        assertEquals(1, plan.evaluation.cache.observations.size)
        assertNull(plan.evaluation.cache.cnsRecoveryHours)
        // El replay es un duplicado: la marca de agua la fijó el registro.
        val replay = planDe(base.copy(adaptiveCache = plan.evaluation.cache), request = ManualRequest(neural = 55))
        assertEquals("duplicado", replay.dropped["cns"])
    }

    // ─── Ajuste muscular global: solo estado ──────────────────────────────────────────────────────

    @Test
    fun elAjusteMuscularGlobalEsSoloEstadoYNoRepartePorLosTreceTau() {
        val base = inputs()

        val plan = planDe(base, request = ManualRequest(muscular = 64))

        assertEquals(64, plan.accepted.muscular)
        val r = plan.evaluation.outcomes.single()
        assertEquals(ManualTargetStatus.STATE_ONLY, r.status)
        assertEquals(ManualGestureKeys.REASON_STATE_ONLY, r.reason)
        val cache = plan.evaluation.cache
        assertEquals("un registro de auditoría", 1, cache.observations.size)
        assertEquals(ManualGestureKeys.MUSCULAR_STATE_ONLY, cache.observations.single().parameterKey)
        assertEquals(AugeObservationStatus.CENSORED, cache.observations.single().outcome.status)
        assertEquals("state_only", cache.observations.single().outcome.reason)
        assertTrue("sin estado de parámetro: no existe un τ global", cache.parameterStates.isEmpty())
        assertTrue(cache.personalizedRecoveryHours.isEmpty())
        assertNull(cache.cnsRecoveryHours)
        assertNull(cache.spinalRecoveryHours)
        // La clave canónica no se reasigna a otro parámetro y el replay es un duplicado.
        assertEquals(ManualGestureKeys.MUSCULAR_STATE_ONLY, ManualGestureKeys.canonical(ManualGestureKeys.MUSCULAR_STATE_ONLY))
        assertEquals(cache, AugeAdaptiveCacheMigration.normalize(cache))
        val replay = planDe(base.copy(adaptiveCache = cache), request = ManualRequest(muscular = 64))
        assertEquals("duplicado", replay.dropped[ManualGestureKeys.MUSCULAR_STATE_ONLY])
    }

    // ─── Fila de bienestar ────────────────────────────────────────────────────────────────────────

    private val ancla = 1_760_000_000_000L

    private fun filaDelAlta() = DailyWellbeingLog(
        id = "alta", date = "2026-10-21", sleepQuality = 4, stressLevel = 2, doms = 2, motivation = 5, sleepHours = 7.5,
        manualNeuralBattery = 70, manualMuscleBatteries = mapOf("Pectorales" to 80), manualBatteryAnchorMs = ancla,
        notes = "nota del alta", preWorkoutDiscomforts = listOf("rodilla"),
        source = WellbeingSource.ONBOARDING_INITIAL, capturedFields = setOf("energy", "muscle_batteries"),
    )

    @Test
    fun laFilaConservaProcedenciaYSoloCambiaLoQueTocaElGesto() {
        val alta = filaDelAlta()

        val fila = applyManualGestureToRow(
            base = alta, newId = "nuevo", date = alta.date, accepted = ManualAccepted(neural = 60), anchorMs = ancla + 5,
            sourceSessionId = null, automaticAtAnchor = { null }, zone = AugeGoldenCorpus.ZONE,
        )

        assertEquals(
            alta.copy(
                manualNeuralBattery = 60, manualBatteryAnchorMs = ancla + 5, manualNeuralAnchorMs = ancla + 5,
                manualMuscleAnchorMs = mapOf("Pectorales" to ancla),
            ),
            fila,
        )
    }

    @Test
    fun unMusculoEscribeV2ConElLogDelCierreYLaPrediccionSupervisadaSinInventarUnCien() {
        val alta = filaDelAlta()

        val conPista = applyManualGestureToRow(
            alta, "nuevo", alta.date, ManualAccepted(muscles = mapOf("Pectorales" to 40)), ancla, "log-1",
            automaticAtAnchor = { 90 }, zone = AugeGoldenCorpus.ZONE,
        )
        assertEquals(
            ManualMuscleBatteryOverride(battery = 40, anchorEpochMs = ancla, sourceSessionId = "log-1", automaticBatteryAtAnchor = 90),
            conPista.manualMuscleOverridesV2.getValue("Pectorales"),
        )
        assertTrue("al tocar un músculo se vacía su V1", conPista.manualMuscleBatteries.isEmpty())

        val sinPista = applyManualGestureToRow(
            alta, "nuevo", alta.date, ManualAccepted(muscles = mapOf("Pectorales" to 40)), ancla, null,
            automaticAtAnchor = { null }, zone = AugeGoldenCorpus.ZONE,
        )
        assertEquals("sin predicción se iguala al valor declarado, no a 100", 40, sinPista.manualMuscleOverridesV2.getValue("Pectorales").automaticBatteryAtAnchor)
        assertNull(sinPista.manualMuscleOverridesV2.getValue("Pectorales").sourceSessionId)

        val previa = applyManualGestureToRow(
            sinPista, "nuevo", alta.date, ManualAccepted(muscles = mapOf("Pectorales" to 30)), ancla + 10, null,
            automaticAtAnchor = { null }, zone = AugeGoldenCorpus.ZONE,
        )
        assertEquals("se conserva la predicción que ya guardaba el ancla", 40, previa.manualMuscleOverridesV2.getValue("Pectorales").automaticBatteryAtAnchor)
    }

    @Test
    fun unaFilaNuevaTomaElIdYLaFechaQueSePidenYLosCanalesTienenAnclaPropia() {
        val fila = applyManualGestureToRow(
            base = null, newId = "id-nuevo", date = "2026-10-21",
            accepted = ManualAccepted(neural = 50, spinalStored = 44, muscular = 30), anchorMs = ancla, sourceSessionId = null,
            automaticAtAnchor = { null }, zone = AugeGoldenCorpus.ZONE,
        )

        assertEquals("id-nuevo", fila.id)
        assertEquals("2026-10-21", fila.date)
        assertEquals(50, fila.manualNeuralBattery)
        assertEquals(44, fila.manualSpinalBattery)
        assertEquals(30, fila.manualMuscularBattery)
        assertEquals(ancla, fila.manualNeuralAnchorMs)
        assertEquals(ancla, fila.manualSpinalAnchorMs)
        assertEquals(ancla, fila.manualMuscularAnchorMs)
    }

    @Test
    fun neutralizarLosDescartadosDevuelveLoGuardadoYDejaElRestoIntacto() {
        val guardada = DailyWellbeingLog(
            id = "hoy", date = "2026-10-21", manualNeuralBattery = 60, manualSpinalBattery = 50,
            manualMuscleBatteries = mapOf("Pectorales" to 55),
        )
        val entrante = guardada.copy(
            manualNeuralBattery = 40, manualSpinalBattery = 30, manualMuscularBattery = 20,
            manualMuscleBatteries = mapOf("Pectorales" to 45, "Dorsales" to 35), notes = "nota",
        )

        val neutral = neutralizeDroppedTargets(entrante, guardada, setOf("cns", "pectorales", ManualGestureKeys.MUSCULAR_STATE_ONLY))

        assertEquals(60, neutral.manualNeuralBattery)
        assertNull(neutral.manualMuscularBattery)
        assertEquals("Columna no estaba descartada", 30, neutral.manualSpinalBattery)
        assertEquals(mapOf("Pectorales" to 55, "Dorsales" to 35), neutral.manualMuscleBatteries)
        assertEquals("nota", neutral.notes)
        assertSame(entrante, neutralizeDroppedTargets(entrante, guardada, emptySet()))
    }

    // ─── Transacción bienestar + caché (modelada sin Room) ────────────────────────────────────────

    /** Dos «tablas» que solo cambian juntas: si el segundo upsert falla se revierte el primero. */
    private class FilaYCache(var bienestar: DailyWellbeingLog?, var cache: AugeAdaptiveCache) {
        fun commit(nuevaFila: DailyWellbeingLog, nuevoCache: AugeAdaptiveCache, fallaEnElSegundo: Boolean): Boolean {
            val filaAntes = bienestar
            bienestar = nuevaFila
            if (fallaEnElSegundo) {
                bienestar = filaAntes
                return false
            }
            cache = nuevoCache
            return true
        }
    }

    @Test
    fun unFalloEntreLosDosUpsertsNoDejaFilasParcialesYElReintentoCompletaExactamenteUna() {
        val base = inputs()
        val almacen = FilaYCache(null, base.adaptiveCache)
        val peticion = ManualRequest(neural = sensacion("cns", base))

        fun intentar(falla: Boolean): Boolean {
            val estado = base.copy(adaptiveCache = almacen.cache)
            val plan = planDe(estado, request = peticion)
            if (plan.accepted.isEmpty()) return true
            val fila = applyManualGestureToRow(
                almacen.bienestar, "hoy", "2025-03-04", plan.accepted, instante, null, { null }, AugeGoldenCorpus.ZONE,
            )
            return almacen.commit(fila, plan.evaluation.cache, fallaEnElSegundo = falla)
        }

        assertFalse(intentar(falla = true))
        assertNull("0 filas parcialmente actualizadas", almacen.bienestar)
        assertEquals("el caché sigue igual que antes", base.adaptiveCache, almacen.cache)

        assertTrue(intentar(falla = false))
        assertEquals(peticion.neural, almacen.bienestar?.manualNeuralBattery)
        assertEquals(1, almacen.cache.observations.size)

        assertTrue("reentrega: nada que añadir", intentar(falla = false))
        assertEquals(1, almacen.cache.observations.size)
    }

    // ─── Utilidades ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun elValorVigenteDeUnMusculoFusionaV1YV2() {
        val v1 = DailyWellbeingLog(id = "a", date = "2026-10-21", manualMuscleBatteries = mapOf("Pectorales" to 55))
        val v2 = DailyWellbeingLog(
            id = "b", date = "2026-10-21",
            manualMuscleOverridesV2 = mapOf("Pectorales" to ManualMuscleBatteryOverride(33, ancla, null, 70)),
        )

        assertEquals(55, effectiveMuscleValue(v1, "pectorales"))
        assertEquals(33, effectiveMuscleValue(v2, "pectorales"))
        assertEquals(33, effectiveMuscleValue(v1.copy(manualMuscleOverridesV2 = v2.manualMuscleOverridesV2), "pectorales"))
        assertNull(effectiveMuscleValue(v1, "dorsales"))
        assertNull(effectiveMuscleValue(null, "pectorales"))
    }
}
