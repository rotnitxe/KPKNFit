package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** `.searchf`: Inter 15 en tinta. */
private val SearchTextStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
)

/**
 * Campo de búsqueda del registro (lienzo `sfield` / `.searchf`): 52 dp con redondeo completo, la lupa a la izquierda y, dentro del
 * propio campo, el botón del código de barras ([onScan]). La búsqueda sale sola al escribir; la acción «Buscar» del teclado solo lo
 * esconde.
 */
@Composable
fun LoggerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Busca un alimento",
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(LoggerColors.card, shape)
            .border(1.dp, Color(0x1CFFFFFF), shape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(KpknLineIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = LoggerColors.faint)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Buscar un alimento" },
            singleLine = true,
            textStyle = SearchTextStyle.copy(color = LoggerColors.ink),
            cursorBrush = SolidColor(LoggerColors.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(text = placeholder, style = SearchTextStyle, color = LoggerColors.dim, maxLines = 1)
                    inner()
                }
            },
        )
        LoggerIconButton(icon = LoggerIcons.Barcode, contentDescription = "Escanear código", onClick = onScan)
    }
}
