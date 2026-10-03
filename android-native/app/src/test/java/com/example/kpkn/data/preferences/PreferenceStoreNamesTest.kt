package com.example.kpkn.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D2.4 · Los almacenes de `domain/` ya no conocen `SharedPreferences`, pero los
 * usuarios tienen sus copias recuperables y preferencias de variante en archivos
 * y claves concretos. Si cambia cualquiera de estos nombres las pierden sin
 * ningún error: esta prueba los fija leyendo las preferencias reales.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PreferenceStoreNamesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun program(id: String) = Program(id = id, name = "Programa $id", structure = ProgramStructure.SIMPLE)

    @Test
    fun theFileNamesAreTheOnesUsersAlreadyHave() {
        assertEquals("program_structure_snapshots", PreferenceFiles.PROGRAM_SNAPSHOTS)
        assertEquals("variant_preferences", PreferenceFiles.VARIANT_PREFERENCES)
    }

    @Test
    fun programSnapshotsAreWrittenToTheSameFileAndKey() {
        val store = programSnapshotStore(context)
        store.push(program("prog-names"), "prueba")

        val raw = context.getSharedPreferences("program_structure_snapshots", Context.MODE_PRIVATE)
            .getString("program_prog-names", null)
        assertNotNull("la copia debe estar en program_structure_snapshots / program_<id>", raw)
        assertTrue(raw!!.contains("prueba"))
    }

    @Test
    fun programSnapshotsWrittenByTheOldCodeAreStillRead() {
        val oldJson = programSnapshotStore(context).push(program("prog-legacy"), "antigua")
        // Otro almacén nuevo sobre el mismo archivo lee lo que dejó el anterior.
        val reopened = programSnapshotStore(context).list("prog-legacy")
        assertEquals(oldJson.map { it.id }, reopened.map { it.id })
        assertEquals("antigua", reopened.single().reason)
    }

    @Test
    fun onlyTheLastTenSnapshotsAreKept() {
        val store = programSnapshotStore(context)
        repeat(12) { index -> store.push(program("prog-max"), "copia-$index") }

        val kept = store.list("prog-max")
        assertEquals(10, kept.size)
        assertEquals("copia-2", kept.first().reason)
        assertEquals("copia-11", kept.last().reason)
    }

    @Test
    fun variantPreferencesUseTheSameFileAndKeys() {
        val store = variantPreferenceStore(context)
        store.saveAspectDefaults("grupo-1", mapOf("agarre" to "neutro"))
        store.saveLastVariant("grupo-1", "variante-9")

        val prefs = context.getSharedPreferences("variant_preferences", Context.MODE_PRIVATE)
        val aspects = prefs.getString("aspect_defaults_grupo-1", null)
        assertNotNull("aspectos en variant_preferences / aspect_defaults_<id>", aspects)
        assertTrue(aspects!!.contains("agarre") && aspects.contains("neutro"))
        assertEquals("variante-9", prefs.getString("last_variant_grupo-1", null))
    }

    @Test
    fun variantPreferencesWrittenByTheOldCodeAreStillRead() {
        context.getSharedPreferences("variant_preferences", Context.MODE_PRIVATE).edit()
            .putString("aspect_defaults_grupo-2", """{"altura":"alta"}""")
            .putString("last_variant_grupo-2", "variante-3")
            .commit()

        val store = variantPreferenceStore(context)
        assertEquals(mapOf("altura" to "alta"), store.loadAspectDefaults("grupo-2"))
        assertEquals("variante-3", store.loadLastVariant("grupo-2"))
        assertNull(store.loadLastVariant("grupo-inexistente"))
        assertTrue(store.loadAspectDefaults("grupo-inexistente").isEmpty())
    }

    @Test
    fun theSharedInstancesAreStable() {
        assertTrue(PreferenceStores.programSnapshots(context) === PreferenceStores.programSnapshots(context))
        assertTrue(PreferenceStores.variantPreferences(context) === PreferenceStores.variantPreferences(context))
    }
}
