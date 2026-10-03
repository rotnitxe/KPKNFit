package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WP-N1: the food vocabulary behind [SemanticPortionRetriever.repairToken] is memoized per snapshot
 * instance. A newly installed snapshot must switch vocabulary at once; repeated calls must be stable.
 */
class RepairTokenVocabularyCacheTest {

    @Test
    fun `repairToken follows the installed snapshot and stays stable on repeated calls`() {
        val original = SemanticPortionRetriever.currentSnapshot()
        try {
            SemanticPortionRetriever.install(DatasetTestHarness.snapshotFor("lentejas", 200.0))
            val first = SemanticPortionRetriever.repairToken("lentejaz")
            assertEquals("lentejas", first)
            assertEquals(first, SemanticPortionRetriever.repairToken("lentejaz"))

            SemanticPortionRetriever.install(DatasetTestHarness.snapshotFor("garbanzos", 200.0))
            assertNull("vocabulary of the previous snapshot leaked", SemanticPortionRetriever.repairToken("lentejaz"))
            assertEquals("garbanzos", SemanticPortionRetriever.repairToken("garbanzoz"))
        } finally {
            DatasetTestHarness.restore(original)
        }
    }
}
