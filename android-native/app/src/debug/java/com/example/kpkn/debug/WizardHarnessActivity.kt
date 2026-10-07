package com.example.kpkn.debug

import android.app.Application
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupWizardMode
import com.example.kpkn.screens.onboarding.SetupWizardScreen
import com.example.kpkn.screens.onboarding.realSetupWizardPersistence
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SOLO DEPURACIÓN (no se integra): abre el asistente de alta REAL (`SetupWizardScreen`, modo solo entreno) con un borrador de
 * prueba ya colocado en el paso que se pida, para revisar el bloque de Entreno sin recorrer los datos básicos.
 *
 * El borrador se construye caminando la ruta de verdad (ver `buildHarnessDraft`): los pasos anteriores quedan confirmados y
 * plegados en su fila-resumen, el paso pedido queda activo y el siguiente asoma. Todo lo demás es el asistente de siempre:
 * se toca, se desliza y se confirma como en la app. Corre con el applicationId `.dbg`, así que su base de datos no es la
 * de la app real; aun así NUNCA llega a «Activar» (el alta de solo entreno termina en un hito antes de la revisión).
 *
 * Extras (`adb shell am start … --es start AVAILABILITY`), siempre a través de `emu_run.py` o `phone_run.py`:
 *  - `start` (nombre de `SetupStepId`, por defecto `EXPERIENCE`): el paso activo. Los pasos anteriores se dan por contestados
 *    con la persona elegida; los posteriores quedan sin tocar.
 *  - `persona` (`gym` por defecto, `home`, `park`, `multi`, `all`): con qué datos se contestaron los pasos anteriores.
 *  - `answers` (`clave=valor/clave=valor`, sin espacios ni `;`): datos del paso activo ya elegidos, sin confirmarlo. Claves: `places=GYM,HOME`,
 *    `material=BARBELL,RACK` o `material=+RINGS,-BARBELL`, `goal=POWERLIFTING`, `fresh=4`, `days=1,3,5`, `startday=4`,
 *    `dayplaces=3:HOME,6:PUBLIC`, `minutes=75`.
 *  - `width` (dp): simula un teléfono de ese ancho escalando la densidad (360 reproduce el del usuario; el emulador mide 448).
 *  - `fontscale` (decimal): escala de letra (1.3 = 130 %).
 *  - `reset` (booleano, `true` por defecto): reconstruye el borrador en cada arranque; con `false` reabre el que hubiera.
 *
 * Las marcas de prueba (`setup-continue`, `setup-place-GYM`, …) salen como `resource-id`, así `uiautomator` encuentra cada
 * control. El movimiento reducido NO se simula aquí: es el ajuste real del sistema (`animator_duration_scale`).
 */
class WizardHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // El teléfono de pruebas suele estar bloqueado y con la pantalla apagada: la actividad se ve igualmente.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
        // Igual que MainActivity: la app es siempre oscura, con iconos claros fijos en las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        initRepositories()
        val config = HarnessConfig.from(intent)
        setContent { HarnessRoot(config = config, onClose = { finish() }) }
    }

    /** Lo mismo que hace `MainActivity` antes de pintar: el asistente espera a que `ProgramRepository` esté listo. */
    private fun initRepositories() {
        runCatching {
            ProgramRepository.init(this)
            CompetitionRepository.init(this)
            AugeRepository.getInstance(this)
            NutritionRepository.init(this)
            CustomExerciseRepository.initialize(this)
            WorkoutMediaRepository.init(this)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { com.example.kpkn.data.exercises.initializeExerciseDatabase(this@WizardHarnessActivity) }
            runCatching { WikiLabRepository.initialize(this@WizardHarnessActivity, KpknDatabase.getInstance(this@WizardHarnessActivity)) }
            runCatching { com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache.getOrLoad(this@WizardHarnessActivity) }
        }
    }
}

/** Lo que pide el intent, ya interpretado. */
private class HarnessConfig(
    val start: SetupStepId,
    val persona: HarnessPersona,
    val answers: String,
    val widthDp: Float,
    val fontScale: Float,
    val reset: Boolean,
) {
    companion object {
        fun from(intent: Intent): HarnessConfig = HarnessConfig(
            start = intent.getStringExtra("start")
                ?.let { name -> SetupStepId.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
                ?: SetupStepId.EXPERIENCE,
            persona = HarnessPersona.of(intent.getStringExtra("persona")),
            answers = intent.getStringExtra("answers").orEmpty(),
            widthDp = intent.numberExtra("width"),
            fontScale = intent.numberExtra("fontscale"),
            reset = intent.getBooleanExtra("reset", true),
        )
    }
}

/** Un extra numérico, venga como entero, decimal o texto (`--ei`, `--ef`, `--es`); 0 si no está. */
private fun Intent.numberExtra(key: String): Float = when (val value = extras?.get(key)) {
    is Number -> value.toFloat()
    is String -> value.toFloatOrNull() ?: 0f
    else -> 0f
}

/** Identificador del borrador del arnés: propio, para no tocar el borrador canónico del asistente. */
private const val HARNESS_DRAFT_ID = "setup-harness-w"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HarnessRoot(config: HarnessConfig, onClose: () -> Unit) {
    val context = LocalContext.current
    var draftId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(config) { draftId = seedHarnessDraft(context.applicationContext as Application, config) }

    // Para simular un teléfono más estrecho se escala la densidad: el mismo ancho en píxeles pasa a tener menos dp.
    val base = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = remember(base, screenWidthDp, config) {
        val scaled = if (config.widthDp > 0f) screenWidthDp * base.density / config.widthDp else base.density
        Density(density = scaled, fontScale = if (config.fontScale > 0f) config.fontScale else base.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides density) {
        KPKNTheme {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .semantics { testTagsAsResourceId = true },
            ) {
                draftId?.let { id ->
                    SetupWizardScreen(
                        mode = SetupWizardMode.TRAINING_ONLY,
                        draftId = id,
                        onDone = onClose,
                        onCancel = onClose,
                        showIntro = false,
                    )
                }
            }
        }
    }
}

/** Deja el borrador del arnés escrito en la base y devuelve su id. Con `reset` lo reconstruye (descartando el anterior). */
private suspend fun seedHarnessDraft(app: Application, config: HarnessConfig): String = withContext(Dispatchers.IO) {
    val persistence = realSetupWizardPersistence(app)
    if (config.reset) persistence.discard(HARNESS_DRAFT_ID)
    if (persistence.load(HARNESS_DRAFT_ID) == null) {
        // Revisión en segundos: siempre mayor que la de cualquier borrador anterior del arnés.
        val revision = (System.currentTimeMillis() / 1000L).toInt()
        val draft = buildHarnessDraft(HARNESS_DRAFT_ID, config.persona, config.start, config.answers, revision)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        persistence.save(HARNESS_DRAFT_ID, json.encodeToString(draft), revision.toLong(), PersonalizedPlanCatalog.REVISION)
    }
    HARNESS_DRAFT_ID
}
