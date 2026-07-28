package com.example.kpkn.screens.sessioneditor

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class RuleTemplate(
    val id: String,
    val name: String,
    val defaults: SessionEditorRuleDefaults,
    val createdAtMs: Long,
    val isFactory: Boolean = false,
)

/**
 * Persistencia local de plantillas de reglas del editor de sesión.
 * Solo defaults concretos (sin ruleLimits / Avanzado).
 */
class RuleTemplateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadAll(): List<RuleTemplate> {
        val raw = prefs.getString(KEY_TEMPLATES, null)
        if (raw.isNullOrBlank()) {
            val seeded = factoryPresets()
            persist(seeded)
            return seeded
        }
        return try {
            json.decodeFromString<List<RuleTemplate>>(raw).ifEmpty {
                val seeded = factoryPresets()
                persist(seeded)
                seeded
            }
        } catch (_: Exception) {
            val seeded = factoryPresets()
            persist(seeded)
            seeded
        }
    }

    fun saveAsTemplate(name: String, defaults: SessionEditorRuleDefaults): RuleTemplate {
        val trimmed = name.trim().ifBlank { "Plantilla" }
        val template = RuleTemplate(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            defaults = defaults,
            createdAtMs = System.currentTimeMillis(),
            isFactory = false,
        )
        val next = loadAll() + template
        persist(next)
        return template
    }

    fun rename(id: String, name: String): List<RuleTemplate> {
        val trimmed = name.trim().ifBlank { return loadAll() }
        val next = loadAll().map { t ->
            if (t.id == id) t.copy(name = trimmed) else t
        }
        persist(next)
        return next
    }

    fun delete(id: String): List<RuleTemplate> {
        val next = loadAll().filterNot { it.id == id }
        persist(next.ifEmpty { factoryPresets() })
        return loadAll()
    }

    fun replaceAll(templates: List<RuleTemplate>) {
        persist(templates.ifEmpty { factoryPresets() })
    }

    private fun persist(templates: List<RuleTemplate>) {
        prefs.edit().putString(KEY_TEMPLATES, json.encodeToString(templates)).apply()
    }

    companion object {
        private const val PREFS_NAME = "session_editor_rule_templates"
        private const val KEY_TEMPLATES = "templates_v1"

        @Volatile
        private var instance: RuleTemplateStore? = null

        fun getInstance(context: Context): RuleTemplateStore {
            return instance ?: synchronized(this) {
                instance ?: RuleTemplateStore(context.applicationContext).also { instance = it }
            }
        }

        fun factoryPresets(): List<RuleTemplate> {
            val now = System.currentTimeMillis()
            return listOf(
                RuleTemplate(
                    id = "factory_alto_volumen",
                    name = "Alto volumen",
                    defaults = SessionEditorRuleDefaults(
                        setCount = 4,
                        reps = 12,
                        rpe = 7.5,
                        normalRestSeconds = 75,
                        betweenSidesRestSeconds = 15,
                        supersetBetweenRestSeconds = 45,
                        supersetRoundRestSeconds = 90,
                        applyToNewItems = true,
                        intensityType = DefaultIntensityType.RPE,
                    ),
                    createdAtMs = now,
                    isFactory = true,
                ),
                RuleTemplate(
                    id = "factory_fuerza",
                    name = "Fuerza",
                    defaults = SessionEditorRuleDefaults(
                        setCount = 5,
                        reps = 5,
                        rpe = 8.5,
                        normalRestSeconds = 180,
                        betweenSidesRestSeconds = 30,
                        supersetBetweenRestSeconds = 90,
                        supersetRoundRestSeconds = 180,
                        applyToNewItems = true,
                        intensityType = DefaultIntensityType.RPE,
                    ),
                    createdAtMs = now + 1,
                    isFactory = true,
                ),
                RuleTemplate(
                    id = "factory_tiempo_corto",
                    name = "Tiempo corto",
                    defaults = SessionEditorRuleDefaults(
                        setCount = 3,
                        reps = 10,
                        rpe = 8.0,
                        normalRestSeconds = 60,
                        betweenSidesRestSeconds = 0,
                        supersetBetweenRestSeconds = 30,
                        supersetRoundRestSeconds = 75,
                        applyToNewItems = true,
                        intensityType = DefaultIntensityType.RIR,
                    ),
                    createdAtMs = now + 2,
                    isFactory = true,
                ),
            )
        }
    }
}
