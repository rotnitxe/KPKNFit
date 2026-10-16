package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType

/** Etiquetas de prueba de la tarjeta «Reemplazando», de la barra de abajo y de su botón (H1, LV5). */
internal const val CatalogReplacingCardTag = "catalog_replacing_card"
internal const val CatalogReplaceBarTag = "catalog_replace_bar"
internal const val CatalogReplaceButtonTag = "catalog_replace_button"

/**
 * Lo que enseña la tarjeta fija «Reemplazando» (H1): el ejercicio actual con su miniatura y «patrón · músculo · dosis del plan».
 * [mainMuscleId] no se pinta: es el músculo del filtro «Mismo músculo».
 */
internal data class CatalogReplacingCardModel(
    val name: String,
    val subtitle: String?,
    val thumbnailResId: Int?,
    val pattern: ExercisePattern,
    val mainMuscleId: String?,
)

/**
 * La tarjeta fija de arriba del catálogo de reemplazo (H1): «REEMPLAZANDO», el nombre del ejercicio actual y su línea de contexto, con
 * la misma miniatura de 48 dp que las filas. Sin estado y sin acciones: solo recuerda qué se está reemplazando.
 */
@Composable
internal fun CatalogReplacingCard(model: CatalogReplacingCardModel, modifier: Modifier = Modifier) {
    val subtitle = model.subtitle
    KpknModule(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CatalogReplacingCardTag),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatalogRowThumbnail(thumbnailResId = model.thumbnailResId, pattern = model.pattern)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "REEMPLAZANDO",
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                Text(
                    text = model.name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * La barra de abajo del catálogo de reemplazo (H1): encima, la nota de lo que se mantiene del plan («Se mantienen las series del plan:
 * 4 × 4–6 · RIR 2», la regla del bug 3) y, debajo, el botón que confirma lo elegido. Sin nada elegido el botón va apagado. Se funde
 * con el negro de la página para que la lista pase por detrás. Sin estado: [onConfirm] lo decide quien la usa.
 */
@Composable
internal fun CatalogReplaceBar(
    note: String?,
    buttonText: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CatalogReplaceBarTag)
            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black)))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        KpknPrimaryPill(
            text = buttonText,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            tag = CatalogReplaceButtonTag,
            enabled = enabled,
        )
    }
}
