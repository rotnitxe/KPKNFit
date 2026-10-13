package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.data.models.DailyEnergyBalance
import com.example.kpkn.data.models.DailyEnergyStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionBalanceModuleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun noRepiteLaCifraDeLoComidoConEntreno() {
        rule.setContent {
            NutritionBalanceModule(
                loaded = true,
                balance = DailyEnergyBalance(
                    consumedKcal = 1066,
                    trainingBurnKcal = 320,
                    netKcal = 1066,
                    targetKcal = 2300,
                    deltaFromTarget = -1234,
                    status = DailyEnergyStatus.DEFICIT,
                ),
            )
        }

        // Lo comido aparece una vez; el entreno y lo que resta son cifras distintas.
        rule.onAllNodesWithContentDescription("1.066 kcal").assertCountEquals(1)
        rule.onNodeWithText("Comido").assertExists()
        rule.onNodeWithText("Entreno").assertExists()
        rule.onNodeWithText("Restante").assertExists()
    }

    @Test
    fun sinEntrenoSoloComidoYRestante() {
        rule.setContent {
            NutritionBalanceModule(
                loaded = true,
                balance = DailyEnergyBalance(consumedKcal = 1066, targetKcal = 2300, deltaFromTarget = -1234),
            )
        }

        rule.onNodeWithText("Entreno").assertDoesNotExist()
        rule.onNodeWithText("Comido").assertExists()
        rule.onNodeWithText("Restante").assertExists()
    }
}
