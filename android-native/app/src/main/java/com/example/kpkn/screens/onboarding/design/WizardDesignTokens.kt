package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.R
import com.example.kpkn.ui.theme.Syne

/**
 * Bloques del wizard tradicional.
 *
 * El acento por bloque comunica contexto y progreso. Nunca recolorea el wizard
 * entero: el fondo, las tarjetas y el CTA se mantienen en toda la secuencia.
 */
enum class WizardBlock(val accent: Color) {
    BASICS(Color(0xFFFFA15C)),
    TRAINING(Color(0xFF74B5FF)),
    NUTRITION(Color(0xFF6EDB9A)),
    RINGS(Color(0xFFF0D36A)),
    REVIEW(Color(0xFF74B5FF)),
}

/**
 * Familias tipográficas del wizard, empaquetadas en `res/font` para funcionar offline.
 *
 *  - [display]: **Syne**, la tipografía de marca (SIL OFL 1.1). Solo para lo importante: títulos de paso,
 *    números grandes y héroes. Es expresiva y cansa en párrafos.
 *  - [body]: Inter (SIL OFL 1.1) para todo el texto de lectura: subtítulos, etiquetas, tarjetas, notas.
 *
 * Los pesos declarados son exactamente los empaquetados; ver `assets/fonts/FONT-NOTICE.txt`. No se
 * descarga ninguna fuente en ejecución.
 */
object WizardFonts {
    val display: FontFamily = Syne

    val body: FontFamily = FontFamily(
        Font(R.font.wizard_body_regular, FontWeight.Normal),
        Font(R.font.wizard_body_medium, FontWeight.Medium),
        Font(R.font.wizard_body_semibold, FontWeight.SemiBold),
    )
}

/**
 * Paleta neutral derivada del muestreo de píxeles de las 48 referencias de
 * `INSPO WIZARDS`: antracita sólido `#1F1F1F`, texto `#FBFBFB`, secundario gris,
 * tarjeta con hairline y CTA blanco fijo. El cursor de la regla de peso es verde
 * (mezcla medida `#356D4E`–`#467E5F`, verdadero ≈ `#3FBF6F`).
 *
 * El muestreo fue programático (píxeles y OCR), no inspección visual: estos
 * valores son el punto de partida y se calibran midiendo Compose en dispositivo
 * dentro de la puerta de aprobación visual.
 */
object WizardColors {
    /** Fondo negro pleno del wizard y de la bienvenida. */
    val background = Color(0xFF000000)

    /**
     * Texto en la tinta cálida de la marca (crema `#F2EEE6`, la del logo y la demo de bienvenida) y sus
     * grises cálidos: ya no es un blanco puro, así el wizard hereda el tono de KPKN.
     */
    val text = Color(0xFFF2EEE6)
    val textMuted = Color(0xFFC4BFB6)
    val textFaint = Color(0xFF8E8980)

    val cardFill = Color(0xFF262626)
    val cardBorder = Color(0xFF3E3E3E)
    val selectedBorder = Color(0xFFF2EEE6)
    val selectedBorderWidth = 2.dp
    val unselectedBorderWidth = 1.dp

    /** Radio derecho de la tarjeta seleccionada: relleno blanco con punto oscuro. */
    val markFill = Color(0xFFF2EEE6)
    val markDot = Color(0xFF1F1F1F)
    val markBorder = Color(0xFF6E6E6E)

    val cta = Color(0xFFF2EEE6)
    val ctaContent = Color(0xFF0B0B0B)
    val ctaDisabled = Color(0xFF2E2E2E)
    val ctaDisabledContent = Color(0xFF7A7A7A)

    val progressTrack = Color(0xFF2E2E2E)
    val progressFill = Color(0xFFF2EEE6)

    /** Verde de «hecho» de la marca: bloques completados en el progreso. */
    val done = Color(0xFF43D18C)

    /** Cursor de la regla de peso: verde, con la zona derecha sombreada. */
    val ruleCursor = Color(0xFF3FBF6F)
    val ruleTint = Color(0x1F3FBF6F)

    val danger = Color(0xFFFF9B92)
    val info = Color(0xFF74B5FF)

    /**
     * Cristal discreto: relleno blanco muy tenue y un único filete uniforme. Sin brillos en las
     * esquinas ni degradados de borde: el vidrio solo se nota por lo que desenfoca de lo que pasa
     * por detrás (cabecera y botón), no por adornos.
     */
    val glassFill = Color.White.copy(alpha = 0.06f)
    val glassBorder = Color.White.copy(alpha = 0.14f)

    /** Filete que separa las secciones y las filas-resumen de la página larga. */
    val divider = Color.White.copy(alpha = 0.10f)

    /** Resplandor al pulsar una opción de género: azul para lo masculino, violeta para lo femenino. */
    val genderMasculine = Color(0xFF4D8DFF)
    val genderFeminine = Color(0xFFB27CFF)
}

object WizardShapes {
    val card = RoundedCornerShape(18.dp)
    /**
     * CTA y píldoras: esquinas cortas (≈8–10 dp) como las referencias
     * `Workouts/p1·p3·p5`, no cápsula completa.
     */
    val cta = RoundedCornerShape(9.dp)
    val pill = RoundedCornerShape(8.dp)
    val panel = RoundedCornerShape(20.dp)
}

/**
 * Espaciado de partida. `gutter` ≈ 24 dp, `ctaHeight` ≈ 56 dp y `touchTarget`
 * ≥ 48 dp según la especificación visual; pendientes de calibrar en el gate.
 */
object WizardSpacing {
    val gutter = 24.dp
    val gutterCompact = 20.dp
    val ctaHeight = 56.dp
    /** Height calibrated to the reference CTA in the full-screen setup wizard. */
    val wizardCtaHeight = 70.dp
    val touchTarget = 48.dp
    val cardGap = 12.dp
    val sectionGap = 20.dp
    val titleGap = 12.dp
    val hairline = 2.dp

    /** Aire sobre la etiqueta de cada sección de la página larga. */
    val sectionPadTop = 28.dp
    /** Aire bajo el control de cada sección de la página larga. */
    val sectionPadBottom = 32.dp
    /** Alto base de la fila-resumen: el mismo en todos los pasos confirmados. Lleva la etiqueta y hasta dos líneas de valor. */
    val summaryRowHeight = 72.dp

    /** Líneas del valor de una fila-resumen: lo que no cabe en ellas se corta en la última palabra entera. */
    const val SUMMARY_VALUE_LINES = 2

    /**
     * Alto de la fila-resumen para una escala de fuente dada. Es una función SOLO de la escala (nunca
     * del contenido), así todas las filas siguen midiendo lo mismo y el deslizado puede usar una
     * fórmula cerrada; con letra grande la fila crece lo justo para que no recorte el texto.
     * Etiqueta (18 dp de interlineado) y dos líneas de valor (21 dp cada una) crecen con la letra; el aire de arriba
     * y de abajo, no.
     */
    fun summaryRowHeightFor(fontScale: Float): Dp = maxOf(summaryRowHeight, (60f * fontScale + 12f).dp)

    /** Alto mínimo visible del paso siguiente bajo el activo; si sobra pantalla, asoma hasta el borde. */
    val peekHeight = 150.dp
    /** Diámetro del botón de confirmar. */
    val dockButton = 64.dp
    /** Alto de las píldoras de cristal de la cabecera. */
    val headerPill = 46.dp
}

/**
 * Escala tipográfica. La pregunta vive en 28–32 sp y el cuerpo en 15–16 sp;
 * se comprueban con textos largos, cifras, unidades y acentos en español.
 */
object WizardTypography {
    val question = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
        lineBreak = LineBreak.Heading,
    )

    val questionCompact = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    )

    val body = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    )

    val bodySmall = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    val cardTitle = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )

    val cardSubtitle = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    // ── Roles de la página larga ────────────────────────────────────────────
    // Un único conjunto de roles para TODOS los pasos. Ningún paso fija un
    // tamaño propio: si necesita otro, se añade un rol aquí. Mínimo 13 sp.

    /** Etiqueta sobre el título de cada sección: «PASO 2 · DATOS BÁSICOS». Va en mayúsculas. */
    val eyebrow = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    )

    /** Título de cada paso: el mismo tamaño y peso en todo el wizard, alineado a la izquierda. */
    val stepTitle = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.28).sp,
        lineBreak = LineBreak.Heading,
    )

    /** Subtítulo de cada paso: una frase, bajo el título. */
    val stepSubtitle = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** Etiqueta de un control o de un campo (Alias, Fecha de nacimiento, Altura, Peso…). */
    val controlLabel = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )

    /** Valor numérico o de entrada grande (edad, fecha, altura, peso). */
    val controlValue = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    )

    /** Nota al pie de un control: ayudas, rangos, avisos pequeños. */
    val note = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    val header = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    /** Top-bar heading scaled to the reference at equal screen width. */
    val wizardTopBar = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
    )

    /**
     * Héroe de la pantalla de hitos ("GET STARTED" en las referencias), título
     * grande en display bold y subtítulo en cuerpo.
     */
    val heroTitle = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.6).sp,
        lineBreak = LineBreak.Heading,
    )

    val heroSubtitle = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    )

    val cta = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

    /** CTA label used by wizard steps (the welcome CTA keeps [cta]). */
    val wizardCta = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
    )

    /** Measurement-unit labels sized to the p3/p5 control proportions. */
    val measureUnit = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    )

    /**
     * Valor grande de peso calibrado a la altura de glifo del valor central en
     * `Workouts/p5.jpg`, comparando referencia y captura actual a igual ancho.
     */
    val measure = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.5).sp,
    )

    /** Valor central de la rueda: grande pero contenido, como la referencia. */
    val wheelValue = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    )

    val wheelValueNeighbour = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    )

    val milestoneTitle = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )

    val milestoneBody = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val caption = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

/** Duraciones del movimiento de la página larga. */
object WizardMotion {
    /** Deslizado del paso siguiente, plegado del anterior y enfoque del que asoma: todo con la misma duración. */
    const val SlideMillis = 520
}

/**
 * Anula «reducir movimiento» (la escala de animaciones del sistema) solo desde el arnés de depuración y las pruebas, que no
 * deben tocar los ajustes de un teléfono real. Sin valor, manda el sistema.
 */
val LocalWizardReducedMotion = compositionLocalOf<Boolean?> { null }

/**
 * Política de movimiento del wizard. Nombres genéricos: ya no pertenecen al chat
 * retirado (`wizChatReducedMotion` se conserva mientras existan sus callers).
 */
@Composable
fun wizardReducedMotion(): Boolean = LocalWizardReducedMotion.current ?: LocalView.current.context.contentResolver.let { resolver ->
    runCatching {
        android.provider.Settings.Global.getFloat(
            resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}
