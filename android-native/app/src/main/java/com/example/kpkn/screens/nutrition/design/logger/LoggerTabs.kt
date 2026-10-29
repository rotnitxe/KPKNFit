package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons

private val TabStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)

/**
 * Pestañas del registro (lienzo `seg2`): «Describir» con el lápiz y «Buscar» con la lupa, en una cápsula oscura de 4 dp de margen. La
 * elegida va en `#262626` y tinta; la otra, en gris tenue. Elegir una solo cambia lo de debajo: el borrador se conserva.
 */
@Composable
fun LoggerTabs(
    activeTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(shape)
            .background(Color(0xFF0D0D0D), shape)
            .border(1.dp, Color.White.copy(alpha = 0.063f), shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LoggerTab("Describir", KpknLineIcons.Edit, activeTab == 0, { onTabSelect(0) }, Modifier.weight(1f))
        LoggerTab("Buscar", KpknLineIcons.Search, activeTab == 1, { onTabSelect(1) }, Modifier.weight(1f))
    }
}

@Composable
private fun LoggerTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val fill by animateColorAsState(if (selected) Color(0xFF262626) else Color.Transparent, tween(180), label = "loggerTabFill")
    val ink = if (selected) LoggerColors.ink else LoggerColors.faint
    Box(
        modifier = modifier
            .height(38.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(fill, RoundedCornerShape(19.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = ink)
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = TabStyle, color = ink, maxLines = 1)
        }
    }
}
