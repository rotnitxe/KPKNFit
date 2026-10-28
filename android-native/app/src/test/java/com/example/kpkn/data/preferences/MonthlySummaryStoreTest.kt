package com.example.kpkn.data.preferences

import com.example.kpkn.domain.storage.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlySummaryStoreTest {

    /** Almacén en memoria: lo que se guarda se lee igual, sin Android. */
    private class MemoryKeyValueStore(private val values: MutableMap<String, String> = HashMap()) : KeyValueStore {
        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun putStringDurably(key: String, value: String): Boolean {
            values[key] = value
            return true
        }
    }

    @Test
    fun sinNadaGuardadoNoHayResumenesVistos() {
        assertTrue(MonthlySummaryStore(MemoryKeyValueStore()).seenKeys().isEmpty())
    }

    @Test
    fun marcarDosClavesYRepetirUnaDejaDosYPersisteEnElAlmacen() {
        val raw = MemoryKeyValueStore()
        val store = MonthlySummaryStore(raw)
        store.markSeen("p1|2026-09")
        store.markSeen("p1|2026-08")
        store.markSeen("p1|2026-09")

        assertEquals(2, store.seenKeys().size)
        assertEquals(setOf("p1|2026-09", "p1|2026-08"), store.seenKeys())
        // Otro almacén sobre el mismo archivo ve lo mismo.
        assertEquals(setOf("p1|2026-09", "p1|2026-08"), MonthlySummaryStore(raw).seenKeys())
    }

    @Test
    fun lasLineasVaciasNoCuentanComoClaves() {
        val raw = MemoryKeyValueStore(mutableMapOf("seen" to "p1|2026-09\n\np1|2026-08\n"))
        assertEquals(setOf("p1|2026-09", "p1|2026-08"), MonthlySummaryStore(raw).seenKeys())
    }
}
