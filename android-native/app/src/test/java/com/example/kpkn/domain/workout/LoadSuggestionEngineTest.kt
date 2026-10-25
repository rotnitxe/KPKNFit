package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadSuggestionEngineTest {

    @Test
    fun roundLoadSnapsToHalfKg() {
        assertEquals(0.0, LoadSuggestionEngine.roundLoad(0.0), 0.0)
        assertEquals(100.0, LoadSuggestionEngine.roundLoad(100.0), 0.0)
        assertEquals(100.5, LoadSuggestionEngine.roundLoad(100.4), 0.0)
        assertEquals(100.5, LoadSuggestionEngine.roundLoad(100.6), 0.0)
    }

}
