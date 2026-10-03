package com.example.kpkn.ui.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * El idioma guardado se cachea en memoria para que `MainActivity.attachBaseContext` no vuelva
 * a llamar a `getSharedPreferences` en el hilo principal (DiskReadViolation de StrictMode).
 * Estos tests fijan que la caché no cambia el comportamiento: primera ejecución, proceso
 * nuevo con idioma guardado y cambio de idioma.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LocaleManagerTest {

    private lateinit var context: Context
    private val originalDefaultLocale: Locale = Locale.getDefault()

    // Mismo archivo y clave que usa LocaleManager (privados): se necesitan para simular un
    // proceso nuevo con un idioma ya guardado en disco.
    private fun localePrefs() = context.getSharedPreferences("kpkn_locale_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        localePrefs().edit().clear().commit()
        LocaleManager.clearCachedLanguageForTests()
    }

    @After
    fun tearDown() {
        LocaleManager.clearCachedLanguageForTests()
        Locale.setDefault(originalDefaultLocale)
    }

    @Test
    fun first_run_without_saved_language_follows_the_system_and_keeps_the_context() {
        assertEquals(LocaleManager.LANGUAGE_SYSTEM, LocaleManager.getSavedLanguage(context))
        assertSame(context, LocaleManager.wrapContext(context))
    }

    @Test
    fun new_process_reads_the_saved_language_once_and_then_serves_it_from_memory() {
        localePrefs().edit().putString("app_language", "en").commit()

        assertEquals("en", LocaleManager.getSavedLanguage(context))

        // Si la segunda lectura volviera a consultar las preferencias, vería "system".
        localePrefs().edit().clear().commit()
        assertEquals("en", LocaleManager.getSavedLanguage(context))
    }

    @Test
    fun persist_is_visible_immediately_and_is_written_to_preferences() {
        LocaleManager.persist(context, "en")
        assertEquals("en", LocaleManager.getSavedLanguage(context))

        LocaleManager.persist(context, "es")
        assertEquals("es", LocaleManager.getSavedLanguage(context))
        assertEquals("es", localePrefs().getString("app_language", null))
    }

    @Test
    fun wrapContext_follows_language_changes() {
        LocaleManager.persist(context, "en")
        assertEquals("en", LocaleManager.wrapContext(context).resources.configuration.locales.get(0).language)

        LocaleManager.persist(context, "es")
        assertEquals("es", LocaleManager.wrapContext(context).resources.configuration.locales.get(0).language)

        LocaleManager.persist(context, LocaleManager.LANGUAGE_SYSTEM)
        assertSame(context, LocaleManager.wrapContext(context))
    }

    @Test
    fun effective_locale_uses_the_saved_language_or_the_system_default() {
        LocaleManager.persist(context, "en")
        assertEquals("en", LocaleManager.getEffectiveLocale(context).language)

        LocaleManager.persist(context, LocaleManager.LANGUAGE_SYSTEM)
        assertEquals(Locale.getDefault(), LocaleManager.getEffectiveLocale(context))
    }
}
