package com.example.kpkn.screens.programs.importer

import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportSharedCatalogTest {
    private val request = CatalogLaunchRequest(requestId = "import-test", selectionMode = CatalogSelectionMode.SINGLE)

    @Test
    fun acceptsAnExactConfigurationOrARealPersonalIdentity() {
        val result = CatalogResult(requestId = request.requestId, contractVersion = 2, selectionMode = request.selectionMode,
            selectedExerciseIds = listOf("definition"), selectedConfigurationIds = listOf("config-1"))
        assertEquals("config-1", importCatalogIdentity(request, result))
        assertEquals("custom:123", importCatalogIdentity(request, result.copy(
            selectedExerciseIds = listOf("custom:123"), selectedConfigurationIds = listOf(""))))
    }

    @Test
    fun rejectsCanceledStaleAndUnidentifiedSelections() {
        val result = CatalogResult(requestId = request.requestId, contractVersion = 2, selectionMode = request.selectionMode,
            selectedExerciseIds = listOf("legacy-without-identity"), selectedConfigurationIds = listOf(""))
        assertNull(importCatalogIdentity(request, result))
        assertNull(importCatalogIdentity(request, result.copy(canceled = true)))
        assertNull(importCatalogIdentity(request, result.copy(requestId = "otro", selectedConfigurationIds = listOf("config-1"))))
    }
}
