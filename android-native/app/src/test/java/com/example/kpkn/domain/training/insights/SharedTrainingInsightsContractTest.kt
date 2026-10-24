package com.example.kpkn.domain.training.insights

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SharedTrainingInsightsContractTest {
    @Test fun `shared cross platform fixtures define units context provenance and deduplication`() {
        val relative="docs/research/training-evidence/fixtures/insights-v1.json"
        val file=listOf(File(relative),File("../$relative"),File("../../$relative")).first { it.isFile }
        val json=Json { ignoreUnknownKeys=true }
        for (element in json.parseToJsonElement(file.readText()).jsonObject.getValue("cases").jsonArray) {
            val case=element.jsonObject
            val current=json.decodeFromJsonElement<TrainingFact>(case.getValue("current"))
            val history=case.getValue("history").jsonArray.map { json.decodeFromJsonElement<TrainingFact>(it) }
            val result=TrainingInsightsEngine.compare(current,history,case.getValue("nowMs").jsonPrimitive.long)
            val name=case.getValue("name").jsonPrimitive.content
            assertEquals(name,case.getValue("expectedPreviousIds").jsonArray.map { it.jsonPrimitive.content },result.previous.map { it.sourceId })
            val expected=case.getValue("expectedMedian").let { if(it==JsonNull) null else it.jsonPrimitive.double }
            if(expected==null) assertNull(name,result.median) else assertEquals(name,expected,result.median!!,0.0)
        }
    }
}
