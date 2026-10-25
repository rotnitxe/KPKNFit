package com.example.kpkn.domain.nutrition.checkin

import com.example.kpkn.data.models.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CheckinPreferencesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Test fun `el JSON anterior mantiene el inicio de semana por defecto y ningun modulo oculto`() {
        assertEquals(CheckinPreferences(), json.decodeFromString<Settings>("{}").nutritionCheckin)
    }
    @Test fun `dia introduccion y omisiones sobreviven a guardar y leer los ajustes`() {
        val preferences = CheckinPreferences(3, true, setOf(CheckinModule.PARTIAL_DAYS, CheckinModule.WEIGH_IN))
        val decoded = json.decodeFromString<Settings>(json.encodeToString(Settings(nutritionCheckin = preferences)))
        assertEquals(preferences, decoded.nutritionCheckin)
        assertFalse(decoded.nutritionCheckin.dismissedModules.contains(CheckinModule.TARGET_UPDATE))
    }
}
