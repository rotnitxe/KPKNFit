package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Fila de un ejercicio del catálogo (SE6a, SESSION_EDITOR_V1 §4). A la izquierda, la miniatura de 48 dp con radio 12: la imagen del
 * catálogo si la hay, o el glifo de su patrón. Luego, el nombre en `bodyStrong` (una línea), «Pectorales · Barra» en `caption` `textMuted`
 * y, si hay búsqueda, las coincidencias en `caption` `textFaint`. A la derecha, el círculo de selección.
 *
 * Sin estado. Tocar el texto llama a [onClick] (quien la usa despliega las variantes o abre la ficha); el círculo, a [onToggleSelection].
 * Mide 64 dp como mínimo: con la línea de coincidencias crece. [expandable] y [expanded] solo dan el estado a accesibilidad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CatalogExerciseRow(
    name: String,
    subtitle: String?,
    matchLabel: String?,
    thumbnailResId: Int?,
    pattern: ExercisePattern,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** Foto de portada de un ejercicio propio (miniatura del almacén privado de medios); tiene prioridad sobre la imagen y el glifo. */
    thumbnailFile: java.io.File? = null,
    onOpenFicha: (() -> Unit)? = null,
    selectionOrder: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .semantics {
                    if (expandable) stateDescription = if (expanded) "Desplegado" else "Plegado"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogRowThumbnail(thumbnailResId = thumbnailResId, pattern = pattern, thumbnailFile = thumbnailFile)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
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
                if (!matchLabel.isNullOrBlank()) {
                    Text(
                        text = matchLabel,
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (onOpenFicha != null) {
            KpknIconButton(
                icon = KpknLineIcons.Info,
                contentDescription = "Ver ficha de $name",
                onClick = onOpenFicha,
            )
        }
        CatalogSelectionCircle(
            selected = selected,
            label = if (selected) "Quitar $name de la selección" else "Añadir $name a la selección",
            onToggle = onToggleSelection,
            selectionOrder = selectionOrder,
        )
    }
}

/**
 * Variantes de una definición, desplegadas bajo su fila (SE6a): un `KpknChip` por valor, con el elegido en tinta, y «Ver ficha» para
 * abrir la ficha de hoy. Va sangrada hasta el nombre. Sin estado: [onSelect] cambia la variante como hasta ahora.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogVariantChips(
    values: List<String>,
    labelOf: (String) -> String,
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onOpenFicha: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 60.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // En filas que saltan de línea: al 130 % ninguna variante queda cortada al borde.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Cada chip ya tiene 48 dp de zona táctil: entre filas basta con eso.
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            values.forEach { value ->
                KpknChip(
                    text = labelOf(value),
                    selected = value == selectedValue,
                    onClick = { onSelect(value) },
                )
            }
        }
        KpknTextAction(text = "Ver ficha", onClick = onOpenFicha)
    }
}

/**
 * Miniatura de 48 dp con radio 12 (SE6a): la imagen del catálogo con aire, o el glifo del patrón sobre `inset`. Es decorativa: el nombre
 * de la fila ya la dice.
 */
@Composable
internal fun CatalogRowThumbnail(thumbnailResId: Int?, pattern: ExercisePattern, thumbnailFile: java.io.File? = null) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KpknSurface.inset),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailFile != null) {
            coil.compose.AsyncImage(
                model = thumbnailFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (thumbnailResId != null) {
            Image(
                painter = painterResource(thumbnailResId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            PatternGlyph(pattern = pattern, accent = SymbolPalette.musculo, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * Círculo de selección (SE6a): vacío con anillo `textFaint` de 1,5 dp, o relleno de tinta con un `Check` negro cuando está elegido. Su zona
 * táctil mide 48 dp y lleva [label] para accesibilidad.
 */
@Composable
private fun CatalogSelectionCircle(selected: Boolean, label: String, onToggle: () -> Unit, selectionOrder: Int?) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag("catalog_row_select")
            .semantics {
                contentDescription = label
                if (selected && selectionOrder != null) stateDescription = "Orden $selectionOrder"
            }
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (selected) WizardColors.text else Color.Transparent, CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (selected) Color.Transparent else WizardColors.textFaint,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                if (selectionOrder != null) {
                    Text(text = "$selectionOrder", style = KpknType.bodyStrong, color = WizardColors.ctaContent)
                } else Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = WizardColors.ctaContent,
                )
            }
        }
    }
}
