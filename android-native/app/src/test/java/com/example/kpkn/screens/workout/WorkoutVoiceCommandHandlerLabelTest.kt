package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.discomfortLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * (c3) El handler de voz habla los labels del DISCOMFORT_CATALOG vía [discomfortLabel]:
 * sin mapa hardcodeado (el viejo mapa hacía caer "wrist_hand" al genérico "articulación").
 */
class WorkoutVoiceCommandHandlerLabelTest {

    @Test
    fun wrist_hand_uses_catalog_label() {
        assertEquals("Muñeca / mano", discomfortLabel("wrist_hand"))
    }

    @Test
    fun shoulder_anterior_uses_catalog_label() {
        assertEquals("Hombro anterior", discomfortLabel("shoulder_anterior"))
    }

    @Test
    fun knee_medial_uses_catalog_label() {
        assertEquals("Rodilla interna/externa", discomfortLabel("knee_medial"))
    }

    @Test
    fun lumbar_uses_catalog_label() {
        assertEquals("Lumbar", discomfortLabel("lumbar"))
    }

    @Test
    fun none_uses_catalog_label() {
        assertEquals("Sin molestias", discomfortLabel("none"))
    }

    @Test
    fun legacy_unknown_id_falls_back_to_id() {
        assertEquals("wrist", discomfortLabel("wrist"))
        assertEquals("hip", discomfortLabel("hip"))
    }
}
