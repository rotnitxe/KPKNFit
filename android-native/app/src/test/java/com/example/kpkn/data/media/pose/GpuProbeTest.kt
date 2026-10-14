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

    private val comfortable = DeviceLoad(isLowRamDevice = false, totalMemBytes = 8L shl 30, thermalStatus = 0)
    private val es32 = 0x30002

    private fun probe(
        store: GpuProbeStore,
        versionCode: Int = 10,
        glEs: Int = es32,
        load: () -> DeviceLoad = { comfortable },
    ) = GpuProbe(store, versionCode, glEs, load)

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
        probe.confirmGpuWorks()
        assertNull(store.values[GpuProbe.KEY_PROBE])
        assertFalse(probe.isVetoed)
        // Otro arranque: sin marca, la GPU vuelve a estar disponible.
        assertTrue(probe(store).beginGpuAttempt().useGpu)
    }

    @Test
    fun aMarkerLeftByACrashVetoesTheGpuForThatVersion() {
        val store = MemoryStore()
        assertTrue(probe(store).beginGpuAttempt().useGpu) // el proceso muere aquí: la marca queda escrita
        assertEquals(10, store.values[GpuProbe.KEY_PROBE])

        val restarted = probe(store)
        val decision = restarted.beginGpuAttempt()
        assertFalse(decision.useGpu)
        assertTrue(decision.reason!!.contains("vetada"))
        assertTrue(restarted.isVetoed)
        assertEquals(10, store.values[GpuProbe.KEY_VETOED])
        assertNull(store.values[GpuProbe.KEY_PROBE])

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
        val probe = probe(store, versionCode = 10)
        assertTrue(probe.beginGpuAttempt().useGpu)
        assertNull(store.values[GpuProbe.KEY_VETOED])
        assertFalse(probe.isVetoed)
        assertEquals(10, store.values[GpuProbe.KEY_PROBE])
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
        val probe = probe(store)
        assertTrue(probe.beginGpuAttempt().useGpu)
        // Un segundo intento en el mismo proceso no toma la marca del primero por la de un arranque muerto.
        assertTrue(probe.beginGpuAttempt().useGpu)
        assertFalse(probe.isVetoed)
        assertEquals(10, store.values[GpuProbe.KEY_PROBE])
        probe.confirmGpuWorks()
        assertNotNull(store.values[GpuProbe.KEY_PROBE])
        probe.abandonGpuAttempt()
        assertNull(store.values[GpuProbe.KEY_PROBE])
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
        assertTrue(line, line.contains("stride=1"))
    }
}
