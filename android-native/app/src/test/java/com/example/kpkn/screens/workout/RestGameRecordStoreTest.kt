package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.workout.RestGameRecordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El récord del minijuego del descanso (LV6, F8) es un entero local: sube, nunca baja y sobrevive al cerrar la pantalla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RestGameRecordStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences(RestGameRecordStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theRecordStartsAtZeroAndOnlyGoesUp() {
        val store = RestGameRecordStore.from(context)
        assertEquals(0, store.best())
        assertTrue(store.submit(7))
        assertEquals(7, store.best())
        assertFalse("un resultado peor no baja el récord", store.submit(3))
        assertFalse("igualarlo no cuenta como récord", store.submit(7))
        assertEquals(7, store.best())
        assertTrue(store.submit(12))
        assertEquals(12, store.best())
    }

    @Test
    fun theRecordIsKeptBetweenOpeningsOfTheScreen() {
        RestGameRecordStore.from(context).submit(9)
        assertEquals(9, RestGameRecordStore.from(context).best())
    }

    @Test
    fun theRecordLivesInItsOwnFileNotInTheWorkoutData() {
        RestGameRecordStore.from(context).submit(4)
        val prefs = context.getSharedPreferences(RestGameRecordStore.FILE_NAME, Context.MODE_PRIVATE)
        assertEquals(setOf(RestGameRecordStore.KEY_BEST), prefs.all.keys)
    }
}
