package com.example.kpkn.ui.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

/**
 * LocaleManager — gestión centralizada del idioma de la app.
 *
 * Estrategia:
 *  - Android 13+ (API 33+): utiliza la API de sistema [android.app.LocaleManager] para per-app
 *    language preference. El sistema recrea la activity automáticamente.
 *  - Android ≤ 12 (API ≤ 32): persiste la elección en SharedPreferences y envuelve los
 *    contextos en [attachBaseContext] con [wrapContext]. Emite [recreateEvent] para que
 *    MainActivity llame a [Activity.recreate()].
 *
 * Uso:
 *  1. Llamar [wrapContext] desde [Application.attachBaseContext] y [Activity.attachBaseContext].
 *  2. Observar [recreateEvent] en MainActivity y llamar a [recreate()] al recibir un evento.
 *  3. Llamar [applyAndPersist] al cambiar idioma desde SettingsViewModel.
 */
object LocaleManager {

    /** Código especial: respetar el idioma del sistema operativo. */
    const val LANGUAGE_SYSTEM = "system"

    private const val PREFS_NAME = "kpkn_locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private val _recreateEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emite un evento cuando la Activity debe recrearse (solo API ≤ 32).
     * Colectar desde MainActivity con lifecycleScope.
     */
    val recreateEvent: SharedFlow<Unit> = _recreateEvent.asSharedFlow()

    // ─── API pública ──────────────────────────────────────────────────────────

    /**
     * Aplica el idioma elegido Y lo persiste en SharedPreferences.
     *
     * - API 33+: delega en [android.app.LocaleManager]; el sistema recrea la Activity.
     * - API ≤ 32: guarda en prefs y emite [recreateEvent] para que MainActivity recree.
     */
    fun applyAndPersist(context: Context, language: String) {
        persist(context, language)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            @Suppress("NewApi")
            val sysLocMgr = context.getSystemService(android.app.LocaleManager::class.java)
            val localeList = if (language == LANGUAGE_SYSTEM) {
                android.os.LocaleList.getEmptyLocaleList()
            } else {
                android.os.LocaleList.forLanguageTags(language)
            }
            sysLocMgr?.applicationLocales = localeList
            // El sistema recrea la Activity automáticamente → no emitir recreateEvent aquí.
        } else {
            // API ≤ 32: la recreación debe ser disparada manualmente desde MainActivity.
            _recreateEvent.tryEmit(Unit)
        }
    }

    /**
     * Persiste el idioma elegido en SharedPreferences (sin aplicarlo ni recrear).
     * Útil al inicializar desde Room antes de que la UI cargue.
     */
    fun persist(context: Context, language: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /**
     * Devuelve el código de idioma guardado ("system", "es", "en", …).
     * Seguro de called desde [attachBaseContext] antes de que Room esté listo.
     */
    fun getSavedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM

    /**
     * Envuelve un [Context] con el locale persistido.
     * Llamar desde [Application.attachBaseContext] y [Activity.attachBaseContext].
     *
     * En API 33+ el sistema ya gestiona el locale de la Activity; aplicamos igualmente
     * el wrapper para que el contexto de Application tenga el locale correcto.
     */
    fun wrapContext(context: Context): Context {
        val language = getSavedLanguage(context)
        if (language == LANGUAGE_SYSTEM) return context
        return applyLocaleToContext(context, language)
    }

    /**
     * Devuelve el [Locale] efectivo para formateo de fechas/números.
     * Reemplaza todos los usos hardcodeados de Locale.forLanguageTag("es").
     */
    fun getEffectiveLocale(context: Context): Locale {
        val language = getSavedLanguage(context)
        return if (language == LANGUAGE_SYSTEM) Locale.getDefault()
        else Locale.forLanguageTag(language)
    }

    // ─── Interno ─────────────────────────────────────────────────────────────

    private fun applyLocaleToContext(context: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
