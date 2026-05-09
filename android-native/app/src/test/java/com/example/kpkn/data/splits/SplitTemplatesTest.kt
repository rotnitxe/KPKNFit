package com.example.kpkn.data.splits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTemplatesTest {

    @Test
    fun recommended_kpkn_contains_exact_expected_ids() {
        val ids = SPLIT_TEMPLATES
            .filter { it.tags.contains(SplitTag.RECOMENDADO_KPKN) }
            .map { it.id }
            .toSet()

        assertEquals(setOf("ul_x4", "ppl_ul", "fullbody_x3", "ant_post_x4", "arnold_ul"), ids)
    }

    @Test
    fun normal_templates_do_not_use_personalizado_tag() {
        val normalTemplates = SPLIT_TEMPLATES.filterNot { it.id == "custom" }
        assertTrue(normalTemplates.isNotEmpty())
        assertFalse(normalTemplates.any { it.tags.contains(SplitTag.PERSONALIZADO) })
    }

    @Test
    fun arnold_ul_exists() {
        assertNotNull(SPLIT_TEMPLATES.firstOrNull { it.id == "arnold_ul" })
    }

    @Test
    fun fullbody_x3_is_high_frequency_not_low_frequency() {
        val split = SPLIT_TEMPLATES.first { it.id == "fullbody_x3" }
        assertTrue(split.tags.contains(SplitTag.ALTA_FRECUENCIA))
        assertFalse(split.tags.contains(SplitTag.BAJA_FRECUENCIA))
    }
}
