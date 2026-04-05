package com.example.kpkn.domain.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaupolicanAtlasTest {

    @Test
    fun `front atlas covers expected muscles and metadata`() {
        val atlas = CaupolicanAtlasRepository.frontAtlas

        assertEquals(AtlasSide.FRONT, atlas.side)
        assertEquals("caupolican_front", atlas.imageSpec.expectedResourceName)
        assertEquals(1338, atlas.imageSpec.expectedWidth)
        assertEquals(3200, atlas.imageSpec.expectedHeight)

        val expectedMuscles = setOf(
            "Cuello",
            "Deltoides",
            "Pectorales",
            "Bíceps",
            "Antebrazo",
            "Abdomen",
            "Core",
            "Aductores",
            "Cuádriceps",
            "Pantorrillas",
        )

        assertEquals(expectedMuscles, atlas.bindings.map { it.muscleName }.toSet())
        assertTrue(atlas.landmarks.isNotEmpty())
        assertTrue(atlas.silhouette.points.size >= 3)
        assertFalse(
            CaupolicanAtlasRepository.validateStructure(atlas).issues.any { issue ->
                issue.code in setOf(
                    "binding_region_missing",
                    "region_without_subzones",
                    "subzone_invalid_polygon",
                    "subzone_out_of_bounds",
                )
            }
        )
    }

    @Test
    fun `back atlas covers expected muscles and metadata`() {
        val atlas = CaupolicanAtlasRepository.backAtlas

        assertEquals(AtlasSide.BACK, atlas.side)
        assertEquals("caupolican_back", atlas.imageSpec.expectedResourceName)
        assertEquals(1376, atlas.imageSpec.expectedWidth)
        assertEquals(3024, atlas.imageSpec.expectedHeight)

        val expectedMuscles = setOf(
            "Cuello",
            "Trapecio",
            "Deltoides",
            "Dorsales",
            "Tríceps",
            "Antebrazo",
            "Erectores Espinales",
            "Glúteos",
            "Isquiosurales",
            "Pantorrillas",
        )

        assertEquals(expectedMuscles, atlas.bindings.map { it.muscleName }.toSet())
        assertTrue(atlas.landmarks.isNotEmpty())
        assertTrue(atlas.silhouette.points.size >= 3)
        assertFalse(
            CaupolicanAtlasRepository.validateStructure(atlas).issues.any { issue ->
                issue.code in setOf(
                    "binding_region_missing",
                    "region_without_subzones",
                    "subzone_invalid_polygon",
                    "subzone_out_of_bounds",
                )
            }
        )
    }

    @Test
    fun `atlas editing helpers update add and remove points`() {
        val atlas = CaupolicanAtlasRepository.frontAtlas
        val originalRegion = atlas.findRegionsForMuscle("Pectorales").first()
        val originalSubzone = originalRegion.subzones.first()
        val originalPoint = originalSubzone.points.first()

        val movedAtlas = atlas.withUpdatedPoint(
            regionId = originalRegion.id,
            subzoneId = originalSubzone.id,
            pointIndex = 0,
            newPoint = NormalizedPoint(0.40f, 0.20f),
        )
        val movedPoint = movedAtlas.findRegionsForMuscle("Pectorales").first().subzones.first().points.first()

        assertNotEquals(originalPoint, movedPoint)
        assertEquals(0.40f, movedPoint.x)
        assertEquals(0.20f, movedPoint.y)

        val addedAtlas = movedAtlas.withAddedPoint(
            regionId = originalRegion.id,
            subzoneId = originalSubzone.id,
            newPoint = NormalizedPoint(0.41f, 0.21f),
        )
        assertEquals(
            originalSubzone.points.size + 1,
            addedAtlas.findRegionsForMuscle("Pectorales").first().subzones.first().points.size,
        )

        val removedAtlas = addedAtlas.withRemovedPoint(
            regionId = originalRegion.id,
            subzoneId = originalSubzone.id,
            pointIndex = 0,
        )
        assertEquals(
            originalSubzone.points.size,
            removedAtlas.findRegionsForMuscle("Pectorales").first().subzones.first().points.size,
        )
    }
}
