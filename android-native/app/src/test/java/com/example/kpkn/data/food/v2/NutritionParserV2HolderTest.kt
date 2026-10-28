package com.example.kpkn.data.food.v2

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionParserV2HolderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun cargaElVocabularioRealYResuelveDosMenciones() = runBlocking {
        val parser = NutritionParserV2Holder.parser(context)
        assertNotNull("el parser v2 debe cargarse del asset", parser)
        val mentions = parser!!.parse("2 huevos y una taza de arroz").mentions
        assertEquals(mentions.toString(), 2, mentions.size)
        mentions.forEach {
            assertNotNull("sin ficha: ${it.text}", it.recordId)
            assertTrue("sin gramos: ${it.text}", (it.grams ?: 0.0) > 0.0)
        }
    }

    @Test
    fun laSegundaLlamadaDevuelveLaMismaInstancia() = runBlocking {
        val first = NutritionParserV2Holder.parser(context)
        val second = NutritionParserV2Holder.parser(context)
        assertNotNull(first)
        assertSame(first, second)
    }
}
