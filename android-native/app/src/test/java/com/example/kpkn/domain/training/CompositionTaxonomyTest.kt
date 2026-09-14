package com.example.kpkn.domain.training

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class CompositionTaxonomyTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Test
    fun every_catalog_movement_pattern_has_a_family() {
        val catalogIds = CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .map { it.profile.movementPatternId }
            .toSet()
        val missing = catalogIds.filter { CompositionTaxonomy.familyOf(it) == null }
        assertTrue(
            "Taxonomía incompleta para: $missing",
            missing.isEmpty(),
        )
        assertTrue(CompositionTaxonomy.knownPatternIds().isNotEmpty())
    }
}
