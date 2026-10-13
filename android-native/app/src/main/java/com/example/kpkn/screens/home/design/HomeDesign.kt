package com.example.kpkn.screens.home.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import java.time.LocalDate
import java.util.Locale

/*
 * Piezas compartidas de Home (ola Home v1): etiqueta de sección, píldoras, entrada de cada sección una vez por proceso y
 * el aire entre secciones. Son composables sin estado: reciben datos simples y lambdas.
 */

/** Aire vertical entre las secciones de la página de Home. */
val HomeSectionGap: Dp = 40.dp

/**
 * Estilos de Home que no tienen rol propio en [WizardTypography]: salen de un rol (misma familia y peso) y solo cambia el
 * tamaño que pide el diseño. Nada por debajo de 13 sp.
 */
internal object HomeTypography {
    /** Etiqueta de sección y fecha: el rol `eyebrow` a 13 sp (el rol mide 12, bajo el mínimo). */
    val eyebrow: TextStyle = WizardTypography.eyebrow.copy(fontSize = 13.sp, lineHeight = 18.sp)

    /** Número del día de «Tu semana»: Syne 700 a 17 sp. */
    val dayNumber: TextStyle = WizardTypography.measure.copy(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp)

    /** Inicial del botón de perfil: Syne 700 a 18 sp. */
    val avatarInitial: TextStyle = WizardTypography.measure.copy(fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.sp)

    /** Kcal comidas de «Nutrición de hoy»: Syne ExtraBold a 34 sp. */
    val kcalFigure: TextStyle = WizardTypography.question.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp)

    /** Último peso de «Cuerpo»: Syne ExtraBold a 40 sp. */
    val weightFigure: TextStyle = WizardTypography.question.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp)

    /** Cifra de un músculo en la rejilla de RINGS: Syne 700 a 15 sp. */
    val ringValueSmall: TextStyle = WizardTypography.measure.copy(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp)

    /** Título de un póster de programa: Syne ExtraBold a 17 sp. */
    val posterTitle: TextStyle = WizardTypography.question.copy(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp)

    /** Título del concepto de «Aprende»: Syne 700 a 20 sp. */
    val conceptTitle: TextStyle = WizardTypography.measure.copy(fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = 0.sp)

    /** Saludo de la barra superior y cifra de sus mini-widgets: Syne 700 a 16 sp. */
    val barTitle: TextStyle = WizardTypography.measure.copy(fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.sp)

    /** Etiquetas pequeñas de la barra superior: Inter 600 a 13 sp. */
    val barLabel: TextStyle = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold)

    /** Píldora «ACTIVO» de un póster: Inter 700 a 13 sp. */
    val posterPill: TextStyle = WizardTypography.note.copy(fontWeight = FontWeight.Bold)

    /** Porcentaje de un músculo en su chip (Home v1.1, I10a): el rol `caption` en SemiBold, con cifras tabulares. */
    val chipValue: TextStyle = KpknType.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "'tnum' 1")

    /** Dosis de un ejercicio del héroe (Home v1.1, I10a): el cuerpo de `KpknType` con cifras tabulares, para que las columnas cuadren. */
    val dose: TextStyle = KpknType.body.copy(fontFeatureSettings = "'tnum' 1")
}

/**
 * Etiqueta de sección de Home: el eyebrow en mayúsculas y en `textMuted`, con una [action] opcional a la derecha, alineada a la
 * base (p. ej. «Ver todos», que debe medir al menos 48 dp). Con [centerAction], la acción va centrada con la etiqueta (p. ej. un
 * botón circular de 48 dp, que por la base quedaría por encima del texto).
 */
@Composable
fun HomeSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    centerAction: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WizardSpacing.gutter),
        verticalAlignment = if (centerAction) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        Text(
            text = text.uppercase(Locale("es")),
            style = HomeTypography.eyebrow,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (action != null) action()
    }
}

/** Acción de texto de una etiqueta de sección («Ver todos»): en tinta y de 48 dp de alto como mínimo. */
@Composable
fun HomeSectionAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = WizardSpacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Text(text = text, style = WizardTypography.controlLabel, color = WizardColors.text)
    }
}

/**
 * Píldora principal de Home: envoltorio de `KpknPrimaryPill` (56 dp, redondeo completo, tinta y texto negro). Conserva la firma y
 * la etiqueta de prueba [tag].
 */
@Composable
fun HomePrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    KpknPrimaryPill(text = text, onClick = onClick, modifier = modifier, tag = tag)
}

/**
 * Píldora secundaria de Home: envoltorio de `KpknSecondaryPill` (56 dp, redondeo completo, borde en tinta al 24 %). Conserva la
 * firma y la etiqueta de prueba [tag].
 */
@Composable
fun HomeSecondaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    KpknSecondaryPill(text = text, onClick = onClick, modifier = modifier, tag = tag)
}

/**
 * Entrada de una sección de Home: la de [kpknEntrance] en el grupo «home». La PRIMERA vez que el proceso compone la sección [index],
 * se funde y sube 12 dp; después no se repite. Misma conducta que antes.
 */
fun Modifier.homeEntrance(index: Int): Modifier = kpknEntrance(group = "home", index = index)

/** Guarda una fecha en `rememberSaveable` como texto ISO (`LocalDate` no es parcelable). */
val HomeDateSaver: Saver<LocalDate, String> = Saver(
    save = { it.toString() },
    restore = { LocalDate.parse(it) },
)
