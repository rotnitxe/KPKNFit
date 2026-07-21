package com.example.kpkn.domain.exercises

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VariantPreferenceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun loadAspectDefaults(variantGroupId: String): Map<String, String> {
        val raw = prefs.getString("aspect_defaults_$variantGroupId", null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveAspectDefaults(variantGroupId: String, aspects: Map<String, String>) {
        prefs.edit().putString("aspect_defaults_$variantGroupId", json.encodeToString(aspects)).apply()
    }

    fun loadLastVariant(variantGroupId: String): String? {
        return prefs.getString("last_variant_$variantGroupId", null)
    }

    fun saveLastVariant(variantGroupId: String, variantId: String) {
        prefs.edit().putString("last_variant_$variantGroupId", variantId).apply()
    }

    companion object {
        private const val PREFS_NAME = "variant_preferences"

        @Volatile
        private var instance: VariantPreferenceStore? = null

        fun getInstance(context: Context): VariantPreferenceStore {
            return instance ?: synchronized(this) {
                instance ?: VariantPreferenceStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
