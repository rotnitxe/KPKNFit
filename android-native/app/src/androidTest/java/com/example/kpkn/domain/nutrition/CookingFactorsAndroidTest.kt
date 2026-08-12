package com.example.kpkn.domain.nutrition

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.regex.PatternSyntaxException
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CookingFactorsAndroidTest {

    @Test
    fun androidResolvesLocalFoodWithoutRegexSyntaxException() {
        val isLiquid = try {
            isLikelyLiquid("porción grande papas fritas")
        } catch (error: PatternSyntaxException) {
            throw AssertionError("Android must resolve the local food without a regex syntax error", error)
        }

        assertFalse(isLiquid)
    }
}
