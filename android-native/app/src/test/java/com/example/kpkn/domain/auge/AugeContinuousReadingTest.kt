package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.channelScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Conformidad de la lectura continua (3.2a-1): el Double previo al redondeo debe cuantizar a la misma cifra visible
 * que publica el computador. Sin reloj real: el corpus fija instantes absolutos y zona UTC.
 */
class AugeContinuousReadingTest {

    private val casos: List<GoldenCase> by lazy { AugeGoldenCorpus.build(AugeGoldenCorpus.BASE_MS) }

    private fun inputs(
        caso: GoldenCase,
        nowMs: Long,
        cache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ) = AugeSnapshotInputs(
        history = caso.history,
        wellbeing = caso.wellbeing,
        settings = caso.settings,
        exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
        nowMs = nowMs,
        zone = AugeGoldenCorpus.ZONE,
        adaptiveCache = cache,
    )

    @Test
    fun quantizedContinuousMatchesVisibleIntegers() {
        var musculos = 0
        var musculosDistintos = 0
        var musculosEvidencia = 0
        var musculosEvidenciaDistintos = 0
        var maxMusculoEvidencia = 0
        var energias = 0
        var energiasDistintas = 0
        var energiasMostradas = 0
        var energiasMostradasDistintas = 0
        var maxEnergiaMostrada = 0
        var columnas = 0
        var columnasDistintas = 0
        var compuestas = 0
        var compuestasDistintas = 0
        var maxCompuesta = 0

        for (caso in casos) {
            for (instante in caso.instants) {
                val core = AugeSnapshotComputer.compute(inputs(caso, instante))
                val continuo = core.continuous

                // Etapa de un solo redondeo, salvo que la evidencia inicial ya haya compuesto el pilar
                // (D03/D04): ahí el entero publicado incorpora un segundo redondeo y se admite ±1.
                val conEvidencia = caso.settings.initialRecoveryEvidence != null
                for ((musculo, lectura) in continuo.perMuscle) {
                    val visible = core.perMuscle.getValue(musculo).recoveryScore
                    val diferencia = abs(AugeUtils.batteryPercent(lectura.exact) - visible)
                    if (conEvidencia) {
                        musculosEvidencia++
                        if (diferencia > 0) {
                            musculosEvidenciaDistintos++
                            maxMusculoEvidencia = maxOf(maxMusculoEvidencia, diferencia)
                        }
                    } else {
                        musculos++
                        if (diferencia != 0) musculosDistintos++
                    }
                }

                // Energía cruda y Columna cruda: mismo cálculo, sin la desaceleración/suelo de los anillos.
                val energiaCruda = AugeRecoveryEngine.calculateSystemicFatigue(
                    history = caso.history,
                    wellbeing = caso.wellbeing,
                    settings = caso.settings,
                    exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
                    nowOverride = instante,
                    zone = AugeGoldenCorpus.ZONE,
                ).first
                energias++
                if (AugeUtils.batteryPercent(continuo.energy.exact) != energiaCruda) energiasDistintas++

                // Energía mostrada: misma cadena que batteries.cnc (desaceleración → suelo → evidencia inicial).
                energiasMostradas++
                val difEnergiaMostrada = abs(
                    AugeUtils.batteryPercent(continuo.energyShown.exact) - core.batteries.cnc,
                )
                if (difEnergiaMostrada > 0) {
                    energiasMostradasDistintas++
                    maxEnergiaMostrada = maxOf(maxEnergiaMostrada, difEnergiaMostrada)
                }

                val columnaCruda = AugeRecoveryEngine.calculateSpinalBattery(
                    history = caso.history,
                    wellbeing = caso.wellbeing,
                    settings = caso.settings,
                    exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
                    nowOverride = instante,
                    zone = AugeGoldenCorpus.ZONE,
                )
                columnas++
                if (AugeUtils.batteryPercent(continuo.spineRaw.exact) != columnaCruda) columnasDistintas++

                // Etapas compuestas: Músculos global y Columna mostrada admiten ±1 punto.
                compuestas += 2
                val difMuscular = abs(
                    AugeUtils.batteryPercent(continuo.musclesGlobal.exact) - core.batteries.muscular,
                )
                if (difMuscular > 0) {
                    compuestasDistintas++
                    maxCompuesta = maxOf(maxCompuesta, difMuscular)
                }
                val columnaMostrada = core.dashboard.channelScore(RecoveryChannelId.STRUCTURE)
                val difColumna = abs(
                    AugeUtils.batteryPercent(continuo.spineShown.exact) - columnaMostrada,
                )
                if (difColumna > 0) {
                    compuestasDistintas++
                    maxCompuesta = maxOf(maxCompuesta, difColumna)
                }
            }
        }

        println(
            "[CONT] musculos=$musculos distintos=$musculosDistintos" +
                " musculosEvidencia=$musculosEvidencia distintos=$musculosEvidenciaDistintos" +
                " maxEvidencia=$maxMusculoEvidencia" +
                " | energia=$energias distintas=$energiasDistintas" +
                " | energiaMostrada=$energiasMostradas distintas=$energiasMostradasDistintas" +
                " max=$maxEnergiaMostrada" +
                " | columnaCruda=$columnas distintas=$columnasDistintas" +
                " | compuestas=$compuestas distintas=$compuestasDistintas max=$maxCompuesta",
        )
        assertEquals("Músculos: el Double debe cuantizar al entero visible", 0, musculosDistintos)
        assertTrue(
            "Músculos con evidencia inicial compuesta: |redondeo(exact) − entero| <= 1 (máx=$maxMusculoEvidencia)",
            maxMusculoEvidencia <= 1,
        )
        assertEquals("Energía cruda: el Double debe cuantizar al entero visible", 0, energiasDistintas)
        assertTrue(
            "Energía mostrada: |batteryPercent(exact) − batteries.cnc| <= 1 " +
                "(distintos=$energiasMostradasDistintas/$energiasMostradas, máx=$maxEnergiaMostrada)",
            maxEnergiaMostrada <= 1,
        )
        assertEquals("Columna cruda: el Double debe cuantizar al entero visible", 0, columnasDistintas)
        assertTrue("Etapas compuestas: |redondeo(exact) − entero| <= 1 (máx=$maxCompuesta)", maxCompuesta <= 1)
        assertTrue("No se evaluó ningún caso", musculos > 0 && energias > 0)
    }

    /**
     * Razón de ser: con τ ±2 % la Energía exacta cambia, aunque los enteros publicados coincidan.
     * A01 tiene carga reciente en todos sus instantes; a más τ, más carga residual y menos batería.
     */
    @Test
    fun finiteDifferenceAtTwoPercentTauSurvivesRounding() {
        val caso = casos.first { it.id == "A01" }
        val tau = 36.0
        val cacheBajo = AugeAdaptiveCache(cnsRecoveryHours = tau * 0.98)
        val cacheAlto = AugeAdaptiveCache(cnsRecoveryHours = tau * 1.02)
        var huboEnterosIguales = false
        var instantesConSensibilidad = 0

        for (instante in caso.instants) {
            val exactoBajo = AugeSnapshotComputer.compute(inputs(caso, instante, cacheBajo)).continuous.energy.exact
            val exactoAlto = AugeSnapshotComputer.compute(inputs(caso, instante, cacheAlto)).continuous.energy.exact
            val diferencia = exactoAlto - exactoBajo
            assertTrue("diferencia no finita en t=$instante", diferencia.isFinite())

            val enteroBajo = AugeRecoveryEngine.calculateSystemicFatigue(
                history = caso.history,
                wellbeing = caso.wellbeing,
                settings = caso.settings,
                exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
                adaptiveCache = cacheBajo,
                nowOverride = instante,
                zone = AugeGoldenCorpus.ZONE,
            ).first
            val enteroAlto = AugeRecoveryEngine.calculateSystemicFatigue(
                history = caso.history,
                wellbeing = caso.wellbeing,
                settings = caso.settings,
                exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
                adaptiveCache = cacheAlto,
                nowOverride = instante,
                zone = AugeGoldenCorpus.ZONE,
            ).first
            println(
                "[CONT-TAU] t=${(instante - caso.origin) / AugeGoldenCorpus.HORA_MS}h " +
                    "exactoBajo=$exactoBajo exactoAlto=$exactoAlto Δ=$diferencia enteros=$enteroBajo/$enteroAlto",
            )

            // En t=0 la recuperación es independiente de τ (horas desde la sesión = 0).
            if (instante > caso.origin) {
                assertTrue("A01 t=$instante: la Energía exacta no cambió con τ", exactoAlto != exactoBajo)
                assertTrue("A01 t=$instante: a más τ debe haber menos batería (Δ=$diferencia)", diferencia < 0.0)
                instantesConSensibilidad++
            }
            if (enteroBajo == enteroAlto && exactoAlto != exactoBajo) huboEnterosIguales = true
        }

        assertTrue("Ningún instante mostró sensibilidad", instantesConSensibilidad > 0)
        assertTrue("La diferencia finita existió pero el redondeo la ocultó en todos los enteros", huboEnterosIguales)
    }

    @Test
    fun atFloorAndBlockedFlagsOnFloorAndDomsCases() {
        val ahora = AugeGoldenCorpus.BASE_MS
        val ajustes = Settings()

        // DOMS 5: el tope (20) queda por debajo del suelo de ENTHUSIAST (22), así que manda el suelo.
        val conDoms = AugeSnapshotComputer.compute(
            AugeSnapshotInputs(
                history = emptyList(),
                wellbeing = DailyWellbeingLog(id = "doms", date = "2025-03-03", doms = 5),
                settings = ajustes,
                exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
                nowMs = ahora,
                zone = AugeGoldenCorpus.ZONE,
            ),
        )
        val sueloDoms = conDoms.continuous.perMuscle.getValue("Cuádriceps")
        assertEquals("El suelo fisiológico de ENTHUSIAST es 22", 22.0, sueloDoms.exact, 1e-9)
        assertTrue("atalogado en el suelo", sueloDoms.atFloor)
        assertTrue("el tope de DOMS impide que τ mande", sueloDoms.blocked)
        assertEquals(22, conDoms.perMuscle.getValue("Cuádriceps").recoveryScore)

        // Sin carga ni molestias: ni suelo ni bloqueo.
        val fresca = AugeSnapshotComputer.compute(
            AugeSnapshotInputs(
                history = emptyList(),
                wellbeing = null,
                settings = ajustes,
                exerciseDb = AugeGoldenCorpus.EXERCISE_DB,
                nowMs = ahora,
                zone = AugeGoldenCorpus.ZONE,
            ),
        )
        val sinNada = fresca.continuous.perMuscle.getValue("Cuádriceps")
        assertEquals(100.0, sinNada.exact, 1e-9)
        assertFalse("100 no está en el suelo", sinNada.atFloor)
        assertFalse("sin DOMS ni molestia no hay bloqueo", sinNada.blocked)
    }
}
