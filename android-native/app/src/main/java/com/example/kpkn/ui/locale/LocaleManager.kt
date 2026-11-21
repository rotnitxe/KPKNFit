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
    private const val KEY_SYSTEM_MANAGED = "system_locale_managed"

    private val _recreateEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Idioma guardado en memoria. Se rellena en la primera lectura del proceso
     * ([Application.attachBaseContext], antes de que StrictMode esté activo) y se
     * actualiza en [persist].
     *
     * Evita que cada [Activity.attachBaseContext] (arranque en frío y cada recreación)
     * vuelva a llamar a `Context.getSharedPreferences` desde el hilo principal: sobre el
     * ContextImpl nuevo de la Activity esa llamada resuelve la ruta del archivo con
     * `File.exists` sobre el directorio de datos (DiskReadViolation), aunque el archivo de
     * preferencias ya esté cargado; por eso precalentar el archivo en segundo plano no lo evita.
     */
    @Volatile
    private var cachedLanguage: String? = null

    @Volatile
    private var cachedSystemManaged: Boolean? = null

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
            markSystemManaged(context)
            // El sistema recrea la Activity automáticamente → no emitir recreateEvent aquí.
        } else {
            // API ≤ 32: la recreación debe ser disparada manualmente desde MainActivity.
            _recreateEvent.tryEmit(Unit)
        }
    }

    /**
     * Sincroniza el idioma persistido en Room al arrancar, sin pisar la preferencia
     * por-app administrada desde los Ajustes del sistema en Android 13+.
     */
    internal fun syncFromSavedSettings(context: Context, roomLanguage: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sysLocMgr = runCatching {
                @Suppress("NewApi")
                context.getSystemService(android.app.LocaleManager::class.java)
            }.getOrNull()
            val hasSystemLocale = runCatching { sysLocMgr?.applicationLocales?.isEmpty == false }.getOrDefault(false)
            if (hasSystemLocale || isSystemManaged(context)) {
                return
            }
            if (roomLanguage != LANGUAGE_SYSTEM) {
                applyAndPersist(context, roomLanguage)
            } else {
                persist(context, roomLanguage)
            }
            return
        }
        persist(context, roomLanguage)
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
        cachedLanguage = language
    }

    /**
     * Devuelve el código de idioma guardado ("system", "es", "en", …).
     * Seguro de llamar desde [attachBaseContext] antes de que Room esté listo.
     */
    fun getSavedLanguage(context: Context): String {
        cachedLanguage?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        cachedLanguage = saved
        if (cachedSystemManaged == null) {
            cachedSystemManaged = prefs.getBoolean(KEY_SYSTEM_MANAGED, false)
        }
        return saved
    }

    /** Solo para tests: simula un proceso nuevo (sin idioma en memoria). */
    internal fun clearCachedLanguageForTests() {
        cachedLanguage = null
        cachedSystemManaged = null
    }

    /**
     * Envuelve un [Context] con el locale persistido.
     * Llamar desde [Application.attachBaseContext] y [Activity.attachBaseContext].
     *
     * En API 33+ respeta los [android.app.LocaleManager.getApplicationLocales] gestionados por
     * el sistema si el usuario configuró el idioma desde la app o desde Ajustes de Android.
     */
    fun wrapContext(context: Context): Context {
        val language = resolveEffectiveLanguage(context)
        if (language == LANGUAGE_SYSTEM) return context
        return applyLocaleToContext(context, language)
    }

    /**
     * Devuelve el [Locale] efectivo para formateo de fechas/números.
     * Reemplaza todos los usos hardcodeados de Locale.forLanguageTag("es").
     */
    fun getEffectiveLocale(context: Context): Locale {
        val language = resolveEffectiveLanguage(context)
        return if (language == LANGUAGE_SYSTEM) Locale.getDefault()
        else Locale.forLanguageTag(language)
    }

    // ─── Interno ─────────────────────────────────────────────────────────────

    private fun resolveEffectiveLanguage(context: Context): String {
        val saved = getSavedLanguage(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sysLocMgr = runCatching {
                @Suppress("NewApi")
                context.getSystemService(android.app.LocaleManager::class.java)
            }.getOrNull()
            val appLocales = runCatching { sysLocMgr?.applicationLocales }.getOrNull()
            if (appLocales != null) {
                if (!appLocales.isEmpty) {
                    val tag = appLocales.get(0)?.toLanguageTag()?.takeIf { it.isNotBlank() }
                    if (tag != null) {
                        cachedLanguage = tag
                        return tag
                    }
                } else if (isSystemManaged(context)) {
                    return LANGUAGE_SYSTEM
                }
            }
        }
        return saved
    }

    private fun isSystemManaged(context: Context): Boolean {
        cachedSystemManaged?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val managed = prefs.getBoolean(KEY_SYSTEM_MANAGED, false)
        cachedSystemManaged = managed
        return managed
    }

    private fun markSystemManaged(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SYSTEM_MANAGED, true)
            .apply()
        cachedSystemManaged = true
    }

    private fun applyLocaleToContext(context: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
