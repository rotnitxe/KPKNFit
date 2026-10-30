package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.frame.EdListIcons

/** `.ed-lnk`: «＋ Añadir ejercicio» al pie de la lista: 48 dp de alto, 6 dp de aire, icono de 20 en tinta, 10 de separación y texto de 14,5/600. */
@Composable
internal fun EdFooterLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EdListIcons.Plus, contentDescription = null, tint = EdColors.ink, modifier = Modifier.size(20.dp))
        Text(text, style = edText(14.5f, FontWeight.SemiBold, EdColors.ink, lineHeight = 18.1f), maxLines = 1)
    }
}

/** `.ed-pillb`: la píldora «Nuevo grupo»: 52 dp de alto, radio 26, filete `#f2eee63d` y texto de 15/600 centrado. */
@Composable
internal fun EdFooterPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(1.dp, Color(0x3DF2EEE6), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = edText(15f, FontWeight.SemiBold, EdColors.ink, lineHeight = 18.75f), maxLines = 1)
    }
}
