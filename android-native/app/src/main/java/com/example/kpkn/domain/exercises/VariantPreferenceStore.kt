package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.storage.KeyValueStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Preferencias de variante por grupo (aspectos técnicos y última variante).
 * Las claves (`aspect_defaults_<id>`, `last_variant_<id>`) no se tocan: son las
 * que ya tienen guardadas los usuarios. El archivo de preferencias lo decide el
 * adaptador de `data/preferences`.
 */
class VariantPreferenceStore(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadAspectDefaults(variantGroupId: String): Map<String, String> {
        val raw = store.getString(aspectDefaultsKey(variantGroupId)) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveAspectDefaults(variantGroupId: String, aspects: Map<String, String>) {
        store.putString(aspectDefaultsKey(variantGroupId), json.encodeToString(aspects))
    }

    fun loadLastVariant(variantGroupId: String): String? {
        return store.getString(lastVariantKey(variantGroupId))
    }

    fun saveLastVariant(variantGroupId: String, variantId: String) {
        store.putString(lastVariantKey(variantGroupId), variantId)
    }

    companion object {
        fun aspectDefaultsKey(variantGroupId: String) = "aspect_defaults_$variantGroupId"

        fun lastVariantKey(variantGroupId: String) = "last_variant_$variantGroupId"
    }
}
