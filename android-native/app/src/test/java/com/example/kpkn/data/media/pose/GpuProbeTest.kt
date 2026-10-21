package com.example.kpkn.data.media.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuProbeTest {
    /** Un «disco» que sobrevive a los procesos: cada [probe] nuevo es un arranque nuevo de la app. */
    private class MemoryStore : GpuProbeStore {
        val values = HashMap<String, Int>()

        override fun getInt(key: String): Int? = values[key]

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private class FakeExitHistory(private val cause: GpuExitCause?) : GpuExitHistory {
        val queriedPids = ArrayList<Int>()

        override fun causeOf(pid: Int): GpuExitCause? {
            queriedPids += pid
            return cause
        }
    }

    private val comfortable = DeviceLoad(isLowRamDevice = false, totalMemBytes = 8L shl 30, thermalStatus = 0)
    private val es32 = 0x30002

    private fun probe(
        store: GpuProbeStore,
        versionCode: Int = 10,
        glEs: Int = es32,
        history: GpuExitHistory = FakeExitHistory(GpuExitCause.NATIVE_CRASH),
        pid: Int = 123,
        load: () -> DeviceLoad = { comfortable },
    ) = GpuProbe(store, versionCode, glEs, load, history, pid)

    @Test
    fun anOldGlesGoesToCpuWithoutTouchingTheStore() {
        val store = MemoryStore()
        val decision = probe(store, glEs = 0x30000).beginGpuAttempt()
        assertFalse(decision.useGpu)
        assertTrue(decision.reason!!.contains("3.0"))
        assertTrue(store.values.isEmpty())
        // GLES 3.1 justo sí vale.
        assertTrue(probe(MemoryStore(), glEs = 0x30001).beginGpuAttempt().useGpu)
        assertFalse(probe(MemoryStore(), glEs = 0x20000).beginGpuAttempt().useGpu)
    }

    @Test
    fun theMarkerIsWrittenBeforeTheGpuAndRemovedAfterTheFirstDetection() {
        val store = MemoryStore()
        val probe = probe(store)
        val decision = probe.beginGpuAttempt()
        assertTrue(decision.useGpu)
        assertNull(decision.reason)
        assertEquals(10, store.values[GpuProbe.KEY_PROBE])
        assertEquals(123, store.values[GpuProbe.KEY_PROBE_PID])
        probe.confirmGpuWorks()
        assertNull(store.values[GpuProbe.KEY_PROBE])
        assertNull(store.values[GpuProbe.KEY_PROBE_PID])
        assertFalse(probe.isVetoed)
        // Otro arranque: sin marca, la GPU vuelve a estar disponible.
        assertTrue(probe(store).beginGpuAttempt().useGpu)
    }

    @Test
    fun aMarkerLeftByANativeCrashVetoesTheGpuForThatVersion() {
        val store = MemoryStore()
        assertTrue(probe(store).beginGpuAttempt().useGpu) // el proceso muere aquí: la marca queda escrita
        assertEquals(10, store.values[GpuProbe.KEY_PROBE])

        val history = FakeExitHistory(GpuExitCause.NATIVE_CRASH)
        val restarted = probe(store, history = history, pid = 456)
        val decision = restarted.beginGpuAttempt()
        assertFalse(decision.useGpu)
        assertTrue(decision.reason!!.contains("vetada"))
        assertTrue(restarted.isVetoed)
        assertEquals(10, store.values[GpuProbe.KEY_VETOED])
        assertNull(store.values[GpuProbe.KEY_PROBE])
        assertNull(store.values[GpuProbe.KEY_PROBE_PID])
        assertEquals(listOf(123), history.queriedPids)

        // El veto se guarda: los arranques siguientes ni lo intentan.
        val third = probe(store)
        assertFalse(third.beginGpuAttempt().useGpu)
        assertTrue(third.isVetoed)
        assertNull(store.values[GpuProbe.KEY_PROBE])
    }

    @Test
    fun aNewVersionCodeTriesTheGpuAgain() {
        val store = MemoryStore()
        probe(store, versionCode = 10).beginGpuAttempt()
        assertFalse(probe(store, versionCode = 10).beginGpuAttempt().useGpu) // vetada en la 10
        val next = probe(store, versionCode = 11)
        assertFalse(next.isVetoed)
        assertTrue(next.beginGpuAttempt().useGpu)
        assertEquals(11, store.values[GpuProbe.KEY_PROBE])
    }

    @Test
    fun aMarkerOfAnOlderVersionIsDroppedWithoutAVeto() {
        val store = MemoryStore()
        store.values[GpuProbe.KEY_PROBE] = 9
        store.values[GpuProbe.KEY_PROBE_PID] = 99
        store.values[GpuProbe.KEY_STRIKES] = 1
        val history = FakeExitHistory(GpuExitCause.NATIVE_CRASH)
        val probe = probe(store, versionCode = 10, history = history)
        assertTrue(probe.beginGpuAttempt().useGpu)
        assertNull(store.values[GpuProbe.KEY_VETOED])
        assertFalse(probe.isVetoed)
        assertEquals(10, store.values[GpuProbe.KEY_PROBE])
        assertEquals(123, store.values[GpuProbe.KEY_PROBE_PID])
        assertEquals(0, store.values[GpuProbe.KEY_STRIKES] ?: 0)
        assertTrue("el historial de otro APK no se consulta", history.queriedPids.isEmpty())
    }

    private fun assertNonGpuExitAllowsAnotherAttempt(cause: GpuExitCause) {
        val store = MemoryStore()
        probe(store).beginGpuAttempt()
        store.values[GpuProbe.KEY_STRIKES] = 1
        val history = FakeExitHistory(cause)
        val restarted = probe(store, history = history, pid = 456)

        assertTrue(restarted.beginGpuAttempt().useGpu)
        assertFalse(restarted.isVetoed)
        assertNull(store.values[GpuProbe.KEY_VETOED])
        assertEquals(0, store.values[GpuProbe.KEY_STRIKES] ?: 0)
        assertEquals(listOf(123), history.queriedPids)
        assertEquals("el intento viejo se sustituyó por el del proceso nuevo", 456, store.values[GpuProbe.KEY_PROBE_PID])
        assertTrue(restarted.diagnostics.contains("ultimaCausa=$cause"))
        restarted.confirmGpuWorks()
        assertNull(store.values[GpuProbe.KEY_PROBE])
        assertNull(store.values[GpuProbe.KEY_PROBE_PID])
    }

    @Test
    fun aJavaCrashSuchAsTheForegroundServiceTimeoutDoesNotVetoTheGpu() {
        assertNonGpuExitAllowsAnotherAttempt(GpuExitCause.JAVA_CRASH)
    }

    @Test
    fun aSystemOrUserExitDoesNotVetoTheGpu() {
        assertNonGpuExitAllowsAnotherAttempt(GpuExitCause.OTHER)
    }

    private fun assertTwoUnknownExitsVetoTheGpu(cause: GpuExitCause?) {
        val store = MemoryStore()
        val history = FakeExitHistory(cause)
        probe(store, history = history, pid = 111).beginGpuAttempt()

        val first = probe(store, history = history, pid = 222)
        assertTrue("un solo aviso permite otro intento", first.beginGpuAttempt().useGpu)
        assertFalse(first.isVetoed)
        assertEquals(1, store.values[GpuProbe.KEY_STRIKES])

        val second = probe(store, history = history, pid = 333)
        assertFalse(second.beginGpuAttempt().useGpu)
        assertTrue(second.isVetoed)
        assertEquals(2, store.values[GpuProbe.KEY_STRIKES])
        assertEquals(listOf(111, 222), history.queriedPids)
        assertNull(store.values[GpuProbe.KEY_PROBE])
        assertNull(store.values[GpuProbe.KEY_PROBE_PID])
    }

    @Test
    fun twoConsecutiveUnknownExitsVetoTheGpuButTheFirstDoesNot() {
        assertTwoUnknownExitsVetoTheGpu(null)
    }

    @Test
    fun anAnrAlsoCountsAsAnUnknownExit() {
        assertTwoUnknownExitsVetoTheGpu(GpuExitCause.ANR)
    }

    @Test
    fun aSuccessfulDetectionResetsTheUnknownExitSequence() {
        val store = MemoryStore()
        val history = FakeExitHistory(null)
        probe(store).beginGpuAttempt()
        val restarted = probe(store, history = history)
        assertTrue(restarted.beginGpuAttempt().useGpu)
        assertEquals(1, store.values[GpuProbe.KEY_STRIKES])

        restarted.confirmGpuWorks()
        assertEquals(0, store.values[GpuProbe.KEY_STRIKES] ?: 0)
        restarted.beginGpuAttempt() // Un cierre desconocido posterior empieza otra secuencia.
        val next = probe(store, history = history)
        assertTrue(next.beginGpuAttempt().useGpu)
        assertFalse(next.isVetoed)
        assertEquals(1, store.values[GpuProbe.KEY_STRIKES])
    }

    @Test
    fun aNewVersionStartsItsOwnUnknownExitSequenceAfterAnOlderVersionWasVetoed() {
        val store = MemoryStore()
        val history = FakeExitHistory(null)
        probe(store, history = history).beginGpuAttempt()
        assertTrue(probe(store, history = history).beginGpuAttempt().useGpu)
        assertFalse(probe(store, history = history).beginGpuAttempt().useGpu)
        assertEquals(2, store.values[GpuProbe.KEY_STRIKES])
        assertNull(store.values[GpuProbe.KEY_PROBE])

        assertTrue(probe(store, versionCode = 11, history = history).beginGpuAttempt().useGpu)
        assertEquals(0, store.values[GpuProbe.KEY_STRIKES] ?: 0)
        val first = probe(store, versionCode = 11, history = history)
        assertTrue("el APK nuevo también permite su primer aviso", first.beginGpuAttempt().useGpu)
        assertFalse(first.isVetoed)
        assertEquals(1, store.values[GpuProbe.KEY_STRIKES])
        assertFalse(probe(store, versionCode = 11, history = history).beginGpuAttempt().useGpu)
        assertEquals(11, store.values[GpuProbe.KEY_VETOED])
    }

    @Test
    fun aNewVersionClearsOlderStrikesEvenWhenTheLastAttemptWasAbandoned() {
        val store = MemoryStore()
        val history = FakeExitHistory(null)
        probe(store).beginGpuAttempt()
        val restarted = probe(store, history = history)
        restarted.beginGpuAttempt()
        restarted.abandonGpuAttempt()
        assertEquals(1, store.values[GpuProbe.KEY_STRIKES])
        assertNull(store.values[GpuProbe.KEY_PROBE])

        assertTrue(probe(store, versionCode = 11, history = history).beginGpuAttempt().useGpu)
        assertEquals(0, store.values[GpuProbe.KEY_STRIKES] ?: 0)
    }

    @Test
    fun aLegacyMarkerWithoutAPidCountsAsUnknownWithoutConsultingHistory() {
        val store = MemoryStore()
        store.values[GpuProbe.KEY_PROBE] = 10
        val history = FakeExitHistory(GpuExitCause.NATIVE_CRASH)
        val restarted = probe(store, history = history)

        assertTrue(restarted.beginGpuAttempt().useGpu)
        assertEquals(1, store.values[GpuProbe.KEY_STRIKES])
        assertFalse(restarted.isVetoed)
        assertTrue(history.queriedPids.isEmpty())

        // Dos marcas de un APK que no guardaba pid siguen la misma política de dos avisos.
        store.values.remove(GpuProbe.KEY_PROBE_PID)
        assertFalse(probe(store, history = history).beginGpuAttempt().useGpu)
        assertEquals(2, store.values[GpuProbe.KEY_STRIKES])
        assertTrue(history.queriedPids.isEmpty())
    }

    @Test
    fun anExistingVetoForThisVersionSurvivesEvenWithoutANativeExitInHistory() {
        val store = MemoryStore()
        store.values[GpuProbe.KEY_VETOED] = 10
        val history = FakeExitHistory(GpuExitCause.OTHER)

        assertFalse(probe(store, history = history).beginGpuAttempt().useGpu)
        assertEquals(10, store.values[GpuProbe.KEY_VETOED])
        assertTrue(history.queriedPids.isEmpty())
    }

    @Test
    fun abandoningAnAttemptClearsTheMarkerWithoutAVeto() {
        val store = MemoryStore()
        val probe = probe(store)
        assertTrue(probe.beginGpuAttempt().useGpu)
        probe.abandonGpuAttempt() // la GPU lanzó una excepción atrapada: el proceso sigue vivo
        assertTrue(store.values.isEmpty())
        assertFalse(probe.isVetoed)
        assertTrue(probe.beginGpuAttempt().useGpu)
        // Cerrar un intento que no está abierto no rompe nada.
        probe.confirmGpuWorks()
        probe.confirmGpuWorks()
        probe.abandonGpuAttempt()
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun withSeveralAttemptsAtOnceTheMarkerLivesUntilTheLastOneEnds() {
        val store = MemoryStore()
        val history = FakeExitHistory(GpuExitCause.NATIVE_CRASH)
        val probe = probe(store, history = history)
        assertTrue(probe.beginGpuAttempt().useGpu)
        // Un segundo intento en el mismo proceso no toma la marca del primero por la de un arranque muerto.
        assertTrue(probe.beginGpuAttempt().useGpu)
        assertFalse(probe.isVetoed)
        assertEquals(10, store.values[GpuProbe.KEY_PROBE])
        assertEquals(123, store.values[GpuProbe.KEY_PROBE_PID])
        assertTrue(history.queriedPids.isEmpty())
        probe.confirmGpuWorks()
        assertNotNull(store.values[GpuProbe.KEY_PROBE])
        probe.abandonGpuAttempt()
        assertNull(store.values[GpuProbe.KEY_PROBE])
        assertNull(store.values[GpuProbe.KEY_PROBE_PID])
    }

    @Test
    fun theDegradedModeDependsOnRamAndTemperature() {
        val gb = 1L shl 30
        assertEquals(1, GpuProbe.strideFor(DeviceLoad(false, 6 * gb, 0)))
        assertEquals(1, GpuProbe.strideFor(DeviceLoad(false, 3 * gb, 1))) // justo 3 GB y calor ligero
        assertEquals(2, GpuProbe.strideFor(DeviceLoad(true, 8 * gb, 0))) // isLowRamDevice
        assertEquals(2, GpuProbe.strideFor(DeviceLoad(false, 3 * gb - 1, 0))) // menos de 3 GB
        assertEquals(2, GpuProbe.strideFor(DeviceLoad(false, 2 * gb, -1)))
        assertEquals(2, GpuProbe.strideFor(DeviceLoad(false, 8 * gb, GpuProbe.THERMAL_MODERATE)))
        assertEquals(2, GpuProbe.strideFor(DeviceLoad(false, 8 * gb, 4))) // CRITICAL
        assertEquals(1, GpuProbe.strideFor(DeviceLoad(false, 8 * gb, GpuProbe.THERMAL_MODERATE - 1))) // LIGHT
        assertEquals(1, GpuProbe.strideFor(DeviceLoad(false, 8 * gb, -1))) // estado térmico desconocido
        assertEquals(1, GpuProbe.strideFor(DeviceLoad(false, 0L, 0))) // RAM desconocida
    }

    @Test
    fun theRecommendedStrideFollowsTheLoadAtThatMoment() {
        var load = comfortable
        val probe = probe(MemoryStore()) { load }
        assertEquals(1, probe.recommendedPoseStride)
        load = comfortable.copy(thermalStatus = 3)
        assertEquals(2, probe.recommendedPoseStride)
        load = comfortable.copy(thermalStatus = 0)
        assertEquals(1, probe.recommendedPoseStride)
    }

    @Test
    fun theDiagnosticsLineMentionsTheStateOfTheProbe() {
        val store = MemoryStore()
        val probe = probe(store)
        probe.beginGpuAttempt()
        val line = probe.diagnostics
        assertTrue(line, line.contains("glEs=3.2"))
        assertTrue(line, line.contains("versionCode=10"))
        assertTrue(line, line.contains("vetada=false"))
        assertTrue(line, line.contains("marca=10"))
        assertTrue(line, line.contains("avisos=0"))
        assertTrue(line, line.contains("ultimaCausa=null"))
        assertTrue(line, line.contains("stride=1"))
    }

    @Test
    fun theDiagnosticsIncludeTheLastExitCauseAndTheUnknownExitCount() {
        val store = MemoryStore()
        probe(store).beginGpuAttempt()
        val restarted = probe(store, history = FakeExitHistory(GpuExitCause.ANR))
        restarted.beginGpuAttempt()

        val line = restarted.diagnostics
        assertTrue(line, line.contains("avisos=1"))
        assertTrue(line, line.contains("ultimaCausa=ANR"))
    }
}
