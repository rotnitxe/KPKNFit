package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.R

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
 * Familias tipográficas candidatas, empaquetadas en `res/font` para funcionar
 * offline.
 *
 * Archivo e Inter (ambas SIL Open Font License 1.1) son **candidatas**, no
 * familias aprobadas: la familia definitiva se fija en la puerta de aprobación
 * visual. Los pesos declarados aquí son exactamente los empaquetados; ver
 * `assets/fonts/FONT-NOTICE.txt`. No se descarga ninguna fuente en ejecución.
 */
object WizardFonts {
    val display: FontFamily = FontFamily(
        Font(R.font.wizard_display_regular, FontWeight.Normal),
        Font(R.font.wizard_display_semibold, FontWeight.SemiBold),
        Font(R.font.wizard_display_bold, FontWeight.Bold),
    )

    val body: FontFamily = FontFamily(
        Font(R.font.wizard_body_regular, FontWeight.Normal),
        Font(R.font.wizard_body_medium, FontWeight.Medium),
        Font(R.font.wizard_body_semibold, FontWeight.SemiBold),
    )
}

/**
 * Paleta observada en las referencias de `INSPO WIZARDS`: antracita sólido, texto
 * blanco, secundario gris, tarjeta seleccionada con borde blanco y CTA blanco.
 *
 * Son valores de partida, no medidas extraídas de los JPG. Se calibran midiendo
 * Compose en dispositivo dentro de la puerta de aprobación visual.
 */
object WizardColors {
    /** Fondo antracita sólido (negro cálido). Sin degradados. */
    val background = Color(0xFF0F1216)
    val text = Color(0xFFF4F6F8)
    val textMuted = Color(0xFFAEB7C4)
    val textFaint = Color(0xFF7B8593)

    val cardFill = Color(0xFF12161B)
    val cardBorder = Color(0xFF2E3540)
    val selectedBorder = Color(0xFFFFFFFF)
    val selectedBorderWidth = 2.dp
    val unselectedBorderWidth = 1.dp

    /** Radio derecho de la tarjeta seleccionada: relleno blanco con punto oscuro. */
    val markFill = Color(0xFFFFFFFF)
    val markDot = Color(0xFF0F1216)
    val markBorder = Color(0xFF5A6472)

    val cta = Color(0xFFFFFFFF)
    val ctaContent = Color(0xFF0B0E12)
    val ctaDisabled = Color(0xFF232830)
    val ctaDisabledContent = Color(0xFF6B7480)

    val progressTrack = Color(0xFF252B33)
    val progressFill = Color(0xFFF4F6F8)

    /** Cursor de la regla de peso: verde, con la zona derecha sombreada. */
    val ruleCursor = Color(0xFF6EDB9A)
    val ruleTint = Color(0x336EDB9A)

    val danger = Color(0xFFFF9B92)
    val info = Color(0xFF74B5FF)
}

object WizardShapes {
    val card = RoundedCornerShape(18.dp)
    val cta = RoundedCornerShape(19.dp)
    val pill = RoundedCornerShape(15.dp)
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
    val touchTarget = 48.dp
    val cardGap = 12.dp
    val sectionGap = 20.dp
    val titleGap = 12.dp
    val hairline = 2.dp
}

/**
 * Escala tipográfica. La pregunta vive en 28–32 sp y el cuerpo en 15–16 sp;
 * se comprueban con textos largos, cifras, unidades y acentos en español.
 */
object WizardTypography {
    val question = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
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
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
    )

    val header = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    val cta = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

    /** Valor grande de peso. */
    val measure = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    )

    val wheelValue = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    )

    val wheelValueNeighbour = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Normal,
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
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    )
}

/**
 * Política de movimiento del wizard. Nombres genéricos: ya no pertenecen al chat
 * retirado (`wizChatReducedMotion` se conserva mientras existan sus callers).
 */
@Composable
fun wizardReducedMotion(): Boolean = LocalView.current.context.contentResolver.let { resolver ->
    runCatching {
        android.provider.Settings.Global.getFloat(
            resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}
