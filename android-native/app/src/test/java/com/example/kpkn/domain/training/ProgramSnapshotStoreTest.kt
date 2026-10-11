package com.example.kpkn.domain.training

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.preferences.programSnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `PreferenceStores.programSnapshots(context)` se invoca desde Compose (hilo principal). El
 * acceso a SharedPreferences (comprobación de directorios + carga del archivo) tiene que
 * ocurrir en el primer `list`/`push`/`restore`, que el ViewModel ejecuta en Dispatchers.IO.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramSnapshotStoreTest {

    private class CountingContext(base: Context) : ContextWrapper(base) {
        var preferencesRequests = 0

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            preferencesRequests++
            return super.getSharedPreferences(name, mode)
        }
    }

    private fun program(id: String) = Program(id = id, name = "Programa $id", structure = ProgramStructure.SIMPLE)

    @Test
    fun construction_does_not_touch_preferences_until_first_use() {
        val context = CountingContext(ApplicationProvider.getApplicationContext())

        val store = programSnapshotStore(context)
        assertEquals(0, context.preferencesRequests)

        assertTrue(store.list("prog-empty").isEmpty())
        assertEquals(1, context.preferencesRequests)
    }

    @Test
    fun push_list_and_restore_round_trip_with_a_single_preferences_lookup() {
        val context = CountingContext(ApplicationProvider.getApplicationContext())
        val store = programSnapshotStore(context)
        val original = program("prog-roundtrip")

        val afterFirst = store.push(original, "primera")
        val afterSecond = store.push(original.copy(name = "Renombrado"), "segunda")

        assertEquals(listOf("primera", "segunda"), afterSecond.map { it.reason })
        assertEquals(afterSecond.map { it.id }, store.list(original.id).map { it.id })
        assertEquals(original.name, store.restore(original.id, afterFirst.single().id)?.name)
        assertEquals(1, context.preferencesRequests)
    }
}
