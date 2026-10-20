package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.*
import org.junit.Test

class CatalogAuditContractsTest {
    @Test fun fiveMemberSupersetIsRejectedInsteadOfSilentlyGroupingOnlyFour() {
        val request = CatalogLaunchRequest()
        val result = CatalogResult.success(request, List(5) { ExerciseMuscleInfo(id = "e$it", name = "E$it") },
            CatalogCommitAction.CREATE_SUPERSET, CatalogSupersetConfig())
        assertFalse(result.isValidFor(request))
    }

    @Test fun fourMemberSupersetRemainsValid() {
        val request = CatalogLaunchRequest()
        val result = CatalogResult.success(request, List(4) { ExerciseMuscleInfo(id = "e$it", name = "E$it") },
            CatalogCommitAction.CREATE_SUPERSET, CatalogSupersetConfig())
        assertTrue(result.isValidFor(request))
    }
}
