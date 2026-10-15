package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Lo que dibuja el icono de un tipo de cardio: un icono de línea de `KpknLineIcons`, o un glifo de patrón. */
internal sealed interface CardioTypeIconSpec {
    data class Line(val icon: ImageVector) : CardioTypeIconSpec
    data class Pattern(val pattern: ExercisePattern) : CardioTypeIconSpec
}

/**
 * Un icono por tipo de cardio (ER2b). Cada máquina tiene el suyo: un icono de línea si encaja, o un glifo de patrón cuando ninguno encaja
 * (el SkiErg tira hacia abajo como el patrón de tracción; el trineo empuja como el de empuje horizontal). Solo la elíptica no tiene icono
 * propio: usa el genérico, el latido del cardio, en vez de dibujar uno nuevo. Las cintas comparten el corredor y las bicis, la bici.
 */
internal fun cardioTypeIconSpec(type: CardioType): CardioTypeIconSpec = when (type) {
    CardioType.TREADMILL, CardioType.CURVED_TREADMILL -> CardioTypeIconSpec.Line(KpknLineIcons.Run)
    CardioType.RUN_OUTDOOR -> CardioTypeIconSpec.Line(KpknLineIcons.Route)
    CardioType.WALK -> CardioTypeIconSpec.Line(KpknLineIcons.Walk)
    CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR -> CardioTypeIconSpec.Line(KpknLineIcons.Bike)
    CardioType.AIR_BIKE -> CardioTypeIconSpec.Line(KpknLineIcons.Flame)
    CardioType.ROW_MACHINE -> CardioTypeIconSpec.Line(KpknLineIcons.Row)
    CardioType.STAIR_CLIMBER -> CardioTypeIconSpec.Line(KpknLineIcons.Stairs)
    CardioType.SKI_ERG -> CardioTypeIconSpec.Pattern(ExercisePattern.PULL_VERTICAL)
    CardioType.SLED -> CardioTypeIconSpec.Pattern(ExercisePattern.PRESS_HORIZONTAL)
    CardioType.ELLIPTICAL -> CardioTypeIconSpec.Line(KpknLineIcons.Pulse)
}

/** Icono de la máquina de un cardio, de [size] y en [tint]. Sin texto: el nombre va al lado. */
@Composable
internal fun CardioTypeIcon(type: CardioType, tint: Color, size: Dp = 22.dp) {
    when (val spec = cardioTypeIconSpec(type)) {
        is CardioTypeIconSpec.Line -> Icon(
            imageVector = spec.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
        is CardioTypeIconSpec.Pattern -> PatternGlyph(
            pattern = spec.pattern,
            accent = tint,
            modifier = Modifier.size(size),
        )
    }
}
