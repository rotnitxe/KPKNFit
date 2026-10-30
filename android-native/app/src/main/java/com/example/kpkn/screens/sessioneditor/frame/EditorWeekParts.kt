package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.EdEyebrowStyle
import com.example.kpkn.screens.sessioneditor.components.edText
import com.example.kpkn.ui.design.KpknSurface
import java.util.Locale

/*
 * Piezas de la hoja de la semana (`.ed-box`, `.ed-radio`, `.ed-ta` de `ed.css`, Pulido v1 · EDITOR · A2, escena `ed-dia`).
 */

/** `.ed-box`: módulo de 22 dp de radio, filete #ffffff12 y 6 dp de aire arriba y abajo por 14 a los lados (la caja de los días lleva 10 × 12). */
@Composable
internal fun EdBox(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KpknSurface.module, shape)
            .border(1.dp, Color(0x12FFFFFF), shape)
            .padding(padding),
        content = content,
    )
}

/** `.ed-box .ed-eyebrow`: la ceja de la caja (11 dp, 600, mayúsculas) con 10 dp arriba y 4 abajo. */
@Composable
internal fun EdBoxEyebrow(text: String) {
    Text(
        text = text.uppercase(Locale("es")),
        style = EdEyebrowStyle,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

/** `.ed-radio`: fila de 52 dp con radio de 20 (anillo de 2 dp, punto de 9 si está elegida), título de 14/600 y línea de 12/500 en `faint`. */
@Composable
internal fun EdRadioRow(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (selected) EdColors.ink else Color(0x40FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(9.dp).background(EdColors.ink, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = edText(14f, FontWeight.SemiBold, EdColors.ink, lineHeight = 17.5f))
            if (subtitle != null) Text(subtitle, style = edText(12f, FontWeight.Medium, EdColors.faint, lineHeight = 15.6f))
        }
    }
}

/** `.ed-ta`: acción de texto de la caja, 44 dp de alto, 13,5/600 en `muted` (o el color dado). */
@Composable
internal fun EdBoxAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = EdColors.muted,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = edText(13.5f, FontWeight.SemiBold, color, lineHeight = 17f))
    }
}
