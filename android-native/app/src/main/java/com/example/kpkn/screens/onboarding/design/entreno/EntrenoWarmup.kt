package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFontFamilyResolver
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Lo que los pasos de Entreno arman la primera vez que se pintan (los doce músculos como trazos SVG que hay que leer, los
 * dibujos de cada implemento, lugar y objetivo, y las tipografías de la marca) cuesta decenas de milisegundos, y con el paso
 * siguiente asomando bajo el activo esa primera vez caía en el cuadro en que la persona pulsaba «Continuar». Se prepara en un
 * hilo de fondo, una sola vez por proceso, en cuanto aparece el primer paso del bloque: de ahí al de los músculos pasan
 * muchas preguntas.
 */

/** Prepara en segundo plano los dibujos y las tipografías de los pasos de Entreno (ver el comentario del archivo). */
internal object EntrenoWarmup {
    @Volatile
    private var done = false

    /** Construye los dibujos que los pasos piden perezosamente. Seguro desde cualquier hilo y barato la segunda vez. */
    fun prepareArt() {
        if (done) return
        for (muscle in MuscleSymbol.entries) MuscleArt.shape(muscle)
        MuscleArt.silhouette(BodyView.FRONT)
        MuscleArt.silhouette(BodyView.BACK)
        for (id in EquipmentSymbolId.entries) equipmentArt(id)
        for (place in TrainingPlace.entries) placeArt(place)
        for (profile in TrainingGoalProfile.entries) goalArt(profile)
        done = true
    }
}

/** Lanza la preparación de [EntrenoWarmup] la primera vez que un paso de Entreno entra en pantalla. */
@Composable
internal fun EntrenoWarmupEffect() {
    val fonts = LocalFontFamilyResolver.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { EntrenoWarmup.prepareArt() }
            runCatching {
                fonts.preload(WizardFonts.display)
                fonts.preload(WizardFonts.body)
            }
        }
    }
}
