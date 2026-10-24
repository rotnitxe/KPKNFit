package com.example.kpkn.data.db

import com.example.kpkn.data.compatibility.LegacyExerciseCoefficientImport
import com.example.kpkn.data.compatibility.LegacyTrainingBackup
import com.example.kpkn.data.compatibility.LegacyTrainingImport
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LegacyExerciseCoefficientImportTest {
    @Test fun customOriginalsPrecisionAndNotesSurviveAndPurgeIsIdempotent() {
        val raw="""{"id":"custom:anillas","name":"Rings","efc":4.25,"cnc":3,"ssc":0.5,"ttc":2,"axialLoadFactor":0.75,"fatigueTier":"ALTA","technicalDifficulty":7.125,"notes":"No borres efc ni AUGE de mi nota","selectedOptions":{"implement":"rings"}}"""
        val clean=LegacyExerciseCoefficientImport.customExercise(raw)
        assertEquals(clean,LegacyExerciseCoefficientImport.customExercise(clean))
        val value=Json.parseToJsonElement(clean).jsonObject
        assertEquals("custom:anillas",value.getValue("id").jsonPrimitive.content)
        assertEquals("Rings",value.getValue("name").jsonPrimitive.content)
        assertEquals(7.125,value.getValue("technicalDifficulty").jsonPrimitive.double,0.0)
        assertEquals("No borres efc ni AUGE de mi nota",value.getValue("notes").jsonPrimitive.content)
        assertEquals("rings",value.getValue("selectedOptions").jsonObject.getValue("implement").jsonPrimitive.content)
        for(key in listOf("efc","cnc","ssc","ttc","axialLoadFactor","fatigueTier")) assertFalse(value.containsKey(key))
    }
    @Test fun profileSnapshotKeepsAuthoredIdentityAndMethodWhileDiscardingKnownDerivedBlock() {
        val snapshot="""{"catalogRevision":"original","capturedAtEpochMs":123,"resolvedProfile":{"efc":4,"technicalDifficulty":7.125,"richMetadata":{"fatigue":{"cnc":3},"programming":{"fatigueCost":"high","role":"primary"},"identity":{"configurationId":"rings-a"}}}}"""
        val clean=Json.parseToJsonElement(LegacyExerciseCoefficientImport.snapshot(snapshot)).jsonObject
        assertEquals("original",clean.getValue("catalogRevision").jsonPrimitive.content)
        val profile=clean.getValue("resolvedProfile").jsonObject
        assertFalse(profile.containsKey("efc"));assertEquals(7.125,profile.getValue("technicalDifficulty").jsonPrimitive.double,0.0)
        val rich=profile.getValue("richMetadata").jsonObject
        assertFalse(rich.containsKey("fatigue"));assertFalse(rich.getValue("programming").jsonObject.containsKey("fatigueCost"))
        assertEquals("primary",rich.getValue("programming").jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("rings-a",rich.getValue("identity").jsonObject.getValue("configurationId").jsonPrimitive.content)
    }
    @Test fun backupImportCannotReintroduceCoefficientsAndDoesNotRewriteOriginalInput() {
        val raw="""{"schemaVersion":6,"customExercises":[{"id":"custom","name":"Anillas","efc":4,"technicalDifficulty":7.125,"notes":"efc en nota"}]}"""
        val old=raw.toByteArray()
        val clean=LegacyTrainingBackup.normalize(raw)
        val custom=Json.parseToJsonElement(clean).jsonObject.getValue("customExercises").jsonArray.single().jsonObject
        assertFalse(custom.containsKey("efc"));assertEquals("efc en nota",custom.getValue("notes").jsonPrimitive.content)
        assertArrayEquals(old,raw.toByteArray())
        assertEquals(clean,LegacyTrainingBackup.normalize(clean))
    }
    @Test fun malformedSnapshotAbortsRatherThanDroppingTheOriginalRecord() {
        val raw="""{"completedExercises":[{"exerciseId":"original","resolvedProfileSnapshotJson":"corrupt"}]}"""
        assertThrows(Exception::class.java) { LegacyTrainingImport.log(raw) }
        assertTrue(raw.contains("original"))
    }
}
