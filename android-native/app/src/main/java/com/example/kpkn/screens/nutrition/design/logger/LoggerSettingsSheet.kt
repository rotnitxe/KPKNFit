package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Texto de privacidad del registro: todo se calcula en el teléfono, puede equivocarse y se corrige a mano. Español neutro, sin voseo y
 * con menos de 300 caracteres (lo comprueba una prueba). La hoja de ajustes enseña solo su primera frase.
 */
internal const val LoggerPrivacyText =
    "Todo se calcula en tu teléfono: no se envían tus comidas a servidores ni se usan APIs o IA externas. " +
        "La estimación puede equivocarse en coincidencias y calorías. " +
        "Si algo no cuadra, corrígelo con «Corregir» o ajusta los gramos y los macros a mano."

/** La primera frase de [LoggerPrivacyText]: la línea que cierra la hoja de ajustes (F-D3). */
internal val LoggerPrivacyFirstSentence: String = LoggerPrivacyText.substringBefore(". ") + "."

/**
 * Ajustes del registro (lienzo s-settings): una hoja con tres filas de icono y subtítulo —«Mis medidas de casa», «Favoritos y recetas» y
 * «Olvidar lo aprendido» (en rojo)— y, al pie, una línea pequeña de privacidad. Cada fila cierra la hoja ([onDismiss]) y luego actúa; el
 * olvido lo confirma quien llama con [LoggerForgetDialog].
 */
@Composable
fun LoggerSettingsSheet(
    onMeasures: () -> Unit,
    onFavoritesAndRecipes: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            LoggerSheetTitle(title = "Ajustes del registro")
            LoggerMenuRow(
                icon = LoggerIcons.Ruler,
                title = "Mis medidas de casa",
                subtitle = "Cuánto es tu taza, tu plato o tu cucharón",
                divider = false,
                onClick = {
                    onDismiss()
                    onMeasures()
                },
            )
            LoggerMenuRow(
                icon = LoggerIcons.Star,
                title = "Favoritos y recetas",
                subtitle = "Ordenar y editar",
                onClick = {
                    onDismiss()
                    onFavoritesAndRecipes()
                },
            )
            LoggerMenuRow(
                icon = KpknLineIcons.History,
                title = "Olvidar lo aprendido",
                subtitle = "Las porciones y elecciones que KPKN recuerda de ti",
                danger = true,
                onClick = {
                    onDismiss()
                    onForget()
                },
            )
            Text(
                text = LoggerPrivacyFirstSentence,
                style = LoggerText.cap,
                color = LoggerColors.faint,
                modifier = Modifier.padding(top = 14.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}
