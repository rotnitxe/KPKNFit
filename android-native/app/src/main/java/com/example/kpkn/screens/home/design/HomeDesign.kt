package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay
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

    /** Título de la sesión del día (el héroe): Syne ExtraBold a 44 sp. Si no cabe en dos líneas, el héroe lo encoge hasta 30 sp. */
    val heroTitle: TextStyle = WizardTypography.question.copy(fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.8).sp)

    /** Nombre de un ejercicio en el héroe: Inter 500 a 15 sp. */
    val heroLine: TextStyle = WizardTypography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)

    /** Dosis de un ejercicio en el héroe: Inter 500 a 14 sp, con cifras tabulares para que las columnas cuadren. */
    val heroDoseText: TextStyle = WizardTypography.bodySmall.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    )

    /** Porcentaje dentro del anillo de «Cómo llegan tus músculos»: Syne 700 a 14 sp. */
    val gaugeFigure: TextStyle = WizardTypography.measure.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp)

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
 * Píldora principal de Home: 56 dp de alto, fondo de tinta y texto negro. Es un botón (`Role.Button`); [tag] es la etiqueta de
 * prueba, si la hay.
 */
@Composable
fun HomePrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    HomePill(text = text, onClick = onClick, modifier = modifier, tag = tag, filled = true)
}

/**
 * Píldora secundaria de Home: 56 dp de alto, borde de 1 dp en tinta al 30 %, fondo transparente y texto en tinta. Es un botón
 * (`Role.Button`); [tag] es la etiqueta de prueba, si la hay.
 */
@Composable
fun HomeSecondaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    HomePill(text = text, onClick = onClick, modifier = modifier, tag = tag, filled = false)
}

@Composable
private fun HomePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    tag: String?,
    filled: Boolean,
) {
    val shape = WizardShapes.cta
    val base = modifier
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .height(56.dp)
        .clip(shape)
    Box(
        modifier = (if (filled) base.background(WizardColors.text) else base.border(1.dp, WizardColors.text.copy(alpha = 0.30f), shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = WizardTypography.cta,
            color = if (filled) WizardColors.ctaContent else WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Entrada de una sección de Home. La PRIMERA vez que el proceso compone la sección [index], se funde de 0 a 1 y sube 12 dp
 * hasta 0, en 420 ms con `FastOutSlowInEasing` y con retardo `index × 50 ms`. Después no se repite, ni al volver a Home. Con
 * movimiento reducido no hay entrada. Va con `graphicsLayer`, así que no recompone por cuadro.
 */
fun Modifier.homeEntrance(index: Int): Modifier = composed {
    val reduced = wizardReducedMotion()
    // La plaza se consume aunque haya movimiento reducido: así la siguiente vez tampoco anima.
    val first = remember(index) { HomeEntranceMemory.firstTime(index) }
    val animate = first && !reduced
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val liftPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(animate) {
        if (animate) {
            delay(index * 50L)
            progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }
    Modifier.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * liftPx
    }
}

/** Secciones de Home que ya han entrado en este proceso: la entrada es una sola vez por proceso. */
internal object HomeEntranceMemory {
    private val entered = HashSet<Int>()

    /** true solo la primera vez que se pide [index] en el proceso. */
    @Synchronized
    fun firstTime(index: Int): Boolean = entered.add(index)
}

/** Guarda una fecha en `rememberSaveable` como texto ISO (`LocalDate` no es parcelable). */
val HomeDateSaver: Saver<LocalDate, String> = Saver(
    save = { it.toString() },
    restore = { LocalDate.parse(it) },
)
