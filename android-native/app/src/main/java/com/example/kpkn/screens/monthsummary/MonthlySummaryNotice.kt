package com.example.kpkn.screens.monthsummary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.progress.MonthlyReport
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknType
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/** Meses en español en minúsculas, para no depender de la configuración regional del dispositivo. */
private val noticeMonthNames = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** Portada del aviso: líneas en carbón con la semilla 5. */
private val noticeCoverSpec = LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, 5)

/** Título del aviso: «Tu octubre está listo». */
internal fun monthlyNoticeTitle(month: YearMonth): String = "Tu ${noticeMonthNames[month.monthValue - 1]} está listo"

/**
 * Cifras del mes para el aviso: «14 sesiones, 42,4 t y una marca nueva.»; sin marca, «14 sesiones y 42,4 t.». Las toneladas van con un
 * decimal y coma, como en el resumen.
 */
internal fun monthlyNoticeLine(report: MonthlyReport): String {
    val sessions = if (report.sessions == 1) "1 sesión" else "${report.sessions} sesiones"
    val tonnes = "${tonnesText(report.tonnageKg)} t"
    return if (report.bestMark != null) "$sessions, $tonnes y una marca nueva." else "$sessions y $tonnes."
}

/** Toneladas con un decimal y coma, con el mismo redondeo que el resumen del mes: 42,4 y 0,5. */
private fun tonnesText(kg: Double): String {
    val tenths = (kg / 1000.0 * 10).roundToLong()
    return "${tenths / 10},${abs(tenths % 10)}"
}

/**
 * Aviso «Tu <mes> está listo» de Inicio: caja de esquina con la portada de líneas arriba y, debajo, el kicker, el título, las cifras
 * y «Ver». La cruz lo descarta. [onOpen] abre la pantalla completa del resumen.
 */
@Composable
internal fun MonthlySummaryNotice(
    title: String,
    line: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corner = RoundedCornerShape(24.dp)
    val titleStyle = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(corner)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.08f), corner)
            .testTag("resumen_mes_aviso"),
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            LineCoverArt(
                spec = noticeCoverSpec,
                modifier = Modifier.matchParentSize(),
                animate = true,
                reduced = wizardReducedMotion(),
            )
            // Degradado a negro en la mitad inferior: la portada se funde con el texto de debajo.
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .semantics { contentDescription = "Descartar" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onDismiss,
                    )
                    .testTag("resumen_mes_descartar"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = WizardColors.textFaint,
                )
            }
        }
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "NUEVO · RESUMEN DEL MES",
                style = KpknType.eyebrow,
                color = SymbolPalette.musculo,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = title, style = titleStyle, color = WizardColors.text)
            Spacer(Modifier.height(6.dp))
            Text(text = line, style = KpknType.caption, color = WizardColors.textMuted)
            Spacer(Modifier.height(14.dp))
            KpknCompactPill(
                text = "Ver",
                onClick = onOpen,
                modifier = Modifier.testTag("resumen_mes_ver"),
            )
        }
    }
}
