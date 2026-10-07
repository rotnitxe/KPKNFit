package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.SessionClockDial
import kotlinx.coroutines.delay

/** Dónde arranca el reloj mientras no hay valor: no es una respuesta hasta que la persona lo mueve o toca un atajo. */
internal const val DEFAULT_DIAL_MINUTES = 60

/** Opacidad del reloj mientras su valor es solo el de arranque (igual que la lectura de la grasa corporal sin declarar). */
private const val UNDECLARED_ALPHA = 0.5f

/**
 * Cuánto se espera tras el último cambio del reloj antes de escribirlo en el borrador. El reloj avisa en CADA muesca
 * (de 5 en 5 minutos) mientras se arrastra y cada escritura relanza el barrido de programas, así que se escribe una vez
 * cuando el dedo se detiene; un atajo o una acción de accesibilidad (un solo cambio) se escribe al instante siguiente.
 */
internal const val SESSION_WRITE_DEBOUNCE_MS = 220L

/**
 * SESSION_TIME · «¿Cuánto tiempo tienes por sesión?»: el dial de reloj, de 20 a 180 minutos de 5 en 5.
 *
 * Lee `state.draft.minutesPerSession` y escribe SOLO con `vm.setSessionMinutes(minutes)`, que redondea al múltiplo de 5
 * más cercano dentro del rango. No hay campo de texto: el valor llega siempre del dial. Mientras no hay valor declarado el
 * dial arranca en [DEFAULT_DIAL_MINUTES] atenuado: es una posición de salida, no una respuesta, y el check sigue
 * apagado hasta que la persona lo mueve o toca un atajo. La pista de COPY («Con poco tiempo…», «Con más tiempo…») sigue
 * al valor.
 */
@Composable
internal fun EntrenoSessionTimeStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val declared = state.draft.minutesPerSession
    // Lo que el dial acaba de pedir y el borrador todavía no tiene; mientras exista, es lo que se ve (el dedo manda).
    var pending by remember { mutableStateOf<Int?>(null) }
    val shown = pending ?: declared ?: DEFAULT_DIAL_MINUTES

    // Una escritura cuando el dedo se detiene, no una por muesca.
    LaunchedEffect(pending) {
        val value = pending ?: return@LaunchedEffect
        delay(SESSION_WRITE_DEBOUNCE_MS)
        vm.setSessionMinutes(value)
    }
    // El borrador ya trae lo pedido: el valor visible vuelve a ser el suyo.
    LaunchedEffect(pending, declared) {
        if (pending != null && pending == declared) pending = null
    }
    // Si el paso se cierra con un cambio sin escribir, no se pierde.
    val flush by rememberUpdatedState {
        pending?.takeIf { it != declared }?.let(vm::setSessionMinutes)
    }
    DisposableEffect(Unit) { onDispose { flush() } }

    Column(modifier = Modifier.fillMaxWidth()) {
        SessionClockDial(
            minutes = shown,
            onMinutesChange = { pending = it },
            modifier = Modifier.alpha(if (declared == null && pending == null) UNDECLARED_ALPHA else 1f),
            range = EntrenoStepValues.SESSION_MINUTES_MIN..EntrenoStepValues.SESSION_MINUTES_MAX,
            step = EntrenoStepValues.SESSION_MINUTES_STEP,
        )
        EntrenoStepNote(sessionTimeHint(pending ?: declared))
    }
}

/** La pista que acompaña al tiempo: con poco se va a lo esencial, con mucho se suman aproximaciones y descansos. */
internal fun sessionTimeHint(minutes: Int?): String? = when {
    minutes == null -> null
    minutes <= 30 -> "Con poco tiempo vamos a lo esencial."
    minutes >= 90 -> "Con más tiempo sumamos aproximaciones, movilidad y descansos más largos."
    else -> null
}
