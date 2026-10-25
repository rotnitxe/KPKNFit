package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.Program
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BlockReviewJsonTest {
    @Test fun historical_programs_keep_empty_review_and_continuation_defaults() {
        val p = Json.decodeFromString<Program>("""{"id":"p","name":"P"}""")
        assertTrue(p.blockReviews.isEmpty())
        assertNull(p.pendingBlockReview)
        assertNull(p.pendingContinuation)
    }
    @Test fun reviews_and_pending_decisions_roundtrip_with_exact_run_identity() {
        val review = BlockReview("b1","Base",2,4,3,75,status = BlockReviewStatus.STABLE_RECORDS,
            summary = "3 de 4 registradas",programRunId = "run",cycleNumber = 3)
        val p = Program("p","P",blockReviews = listOf(review),
            pendingBlockReview = BlockReviewProposal(review, NextBlockAdjustment("b2",1,0.9,1.0,"Revisar",
                programRunId = "run",cycleNumber = 3)),
            pendingContinuation = ContinuationProposal(42,programRunId = "run",cycleNumber = 3))
        assertEquals(p,Json.decodeFromString<Program>(Json.encodeToString(p)))
    }
}
