package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.split.DayRole
import com.example.kpkn.domain.training.split.StripCell
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.design.lineIcon
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState

/*
 * REPARTO · piezas comunes del flujo (punto 23 de «Pulido v1»): la paleta de roles del lienzo, los estilos de texto de `rp.css`, la tira de
 * siete casillas, el módulo, el encabezado de sección y los iconos propios. Las sombras y el vidrio salen de `ui/design/depth`.
 */

// NO APLICA · filas de `paridad/rp.md` que estas pantallas no dibujan: la regla es de la hoja base del lienzo y el marcado de las escenas del reparto no la usa
// (comprobado con `grep` en `scenes-rp.js`: sin `tok`, `ribbon`, `wkc`, `split7`, `st-week`, `wr-bars`, `sess now`, `draw go`, `write`). rp-S01, rp-V01 y rp-G01
// (`.glass` base) los anula `.topbar .glass` (rp-S14, rp-V03, rp-G07). rp-S04, rp-S12, rp-S13, rp-S15, rp-G04, rp-G05, rp-G06, rp-A01, rp-A02, rp-A03 y rp-A11 son de esa hoja base.
// rp-S10 y rp-A09 (`.rp-snack`) no salen en ninguna escena: «Deshacer» vive en la hoja de resumen y en el ⋯ de la semana. rp-S05 (`.rp-ann`), rp-S16 y rp-J03 son de las
// escenas «hoy», que no se implementan.
/** Los colores de `rp.css`: cada rol de día tiene el suyo (empuje amarillo, tirón azul, pierna coral, torso lila, el resto crema). */
internal object RpColors {
    val push = Color(0xFFF7CF73)
    val pull = Color(0xFF8FB2FF)
    val legs = Color(0xFFF49A6E)
    val torso = Color(0xFFC9A8FF)
    val ink = Color(0xFFF2EEE6)
    val done = Color(0xFF43D18C)
    val danger = Color(0xFFFF9B92)
    val warn = Color(0xFFF7CF73)
    val dim = Color(0xFF5F5C57)

    /** `#111` de lo que va sobre un color de tinta. */
    val onInk = Color(0xFF111111)

    /** Sesión elegida y tarjeta marcada (`#151515`). */
    val selected = Color(0xFF151515)

    /** Detalle desplegado y pista de los segmentados (`#0d0d0d`). */
    val well = Color(0xFF0D0D0D)

    /** Opción elegida del segmentado (`#272727`). */
    val segOn = Color(0xFF272727)

    /** Filete de las tarjetas (`#ffffff12`). */
    val line = Color(0x12FFFFFF)

    /** Fondo de una etiqueta tonal (`#ffffff0d`). */
    val tonal = Color(0x0DFFFFFF)

    fun of(role: DayRole): Color = when (role) {
        DayRole.PUSH -> push
        DayRole.PULL -> pull
        DayRole.LEGS -> legs
        DayRole.TORSO -> torso
        DayRole.FULL_BODY, DayRole.MIX -> ink
        DayRole.REST -> Color.Transparent
    }
}

internal object RpType {
    private val body = WizardFonts.body
    private val display = WizardFonts.display

    /** `.rp-eye`: 10,5 / 600 / 1,4 de espaciado, en mayúsculas. */
    val eye = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 1.4.sp)

    /** `.rp-h1`: Syne ExtraBold 25 / 1,06, −0,8. */
    val h1 = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, lineHeight = 26.5.sp, letterSpacing = (-0.8).sp, lineBreak = LineBreak.Heading)

    /** `.ptitle` a 27 sp (la página «Reparto»). */
    val pageTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 28.35.sp, letterSpacing = (-0.8).sp)

    /** `.rp-lead`: 13,5 con 1,45. */
    val lead = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.575.sp)

    /** `.rp-m`: 12 / 500; el trozo en negrita, 600. */
    val meta = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)

    /** `.rp-card .rp-m`: 11,5. */
    val metaCard = meta.copy(fontSize = 11.5.sp, lineHeight = 15.sp)

    /** `.rp-sec`: 11 / 600 / 1,2, mayúsculas; el contador, 12 / 500 sin espaciado. */
    val section = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)
    val sectionCount = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)

    /** `.ey`: 11 / 600 / 1,2, mayúsculas. */
    val ey = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)

    /** `.rp-card-h strong`: Syne Bold 17 / 1,15, −0,3. */
    val cardTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 19.55.sp, letterSpacing = (-0.3).sp)

    /** `.rp-card p`: 13 / 1,42. */
    val cardBody = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.46.sp)

    /** `.rp-rec`: 10,5 / 600 / 1,1, mayúsculas. */
    val recommended = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 1.1.sp)

    /** `.rp-fit`: 12 / 1,35. */
    val fit = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.2.sp)

    /** Inicial dentro de la casilla de un día: 11 / 700. */
    val dot = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp)
    val dotSmall = dot.copy(fontSize = 9.5.sp, lineHeight = 12.sp)

    /** Letra del día sobre la casilla: 10 / 600 / 0,4. */
    val stripLetter = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.4.sp)

    /** `.rp-day .dc small` 10 / 600 / 1; `b`: Syne Bold 19 / 1,1. */
    val dayName = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.sp)
    val dayNumber = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 20.9.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")
    val dayNumberRest = dayNumber.copy(fontSize = 15.sp, lineHeight = 16.5.sp)

    /** `.rp-sess strong`: Syne Bold 15,5 / 1,2, −0,2; `.f` 12; `.n` 11,5. */
    val sessionTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = 18.6.sp, letterSpacing = (-0.2).sp)
    val sessionFocus = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 15.sp)
    val sessionDetail = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 14.sp, fontFeatureSettings = "'tnum' 1")

    /** `.rp-rest`: 13. */
    val rest = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp)

    /** `.rp-ex b` 13,5 / 600; `span` 11,5; `em` 10,5 / 600. */
    val exerciseName = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 17.sp)
    val exerciseDetail = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 14.sp, fontFeatureSettings = "'tnum' 1")
    val tag = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 13.sp)

    /** Textos de 12 / 12,5 / 13 de la página. */
    val small = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.8.sp)
    val smallStrong = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp)
    val note = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.125.sp)
    val notice = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.46.sp)

    /** `.rp-pk strong`: Syne Bold 13,5 / 1,18, −0,2; `small`: 11 / 500. */
    val pickTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, lineHeight = 15.93.sp, letterSpacing = (-0.2).sp)
    val pickCaption = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)

    /** `.rp-scope`: 12,5 / 500 y «Cambiar» en 600. */
    val scope = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 15.sp)
    val scopeAction = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 15.sp)

    /** `.rp-seg2 button`: 12 / 600. */
    val seg2 = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val seg = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")

    /** Filas de volumen. */
    val volName = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)
    val volValue = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")
    val volFreq = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, lineHeight = 14.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")
    val volHead = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.8.sp)
    val legend = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 13.sp)

    /** `.act`: 13 / 600. */
    val act = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)

    /** `.rp-ssg small`: 9,5 / 600 / 1, mayúsculas; `.rp-tab .c`: 13. */
    val superset = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, lineHeight = 12.sp, letterSpacing = 1.sp)
    val volCell = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")

    /** Revisión. */
    val summaryNumber = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 23.1.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")
    val summaryLabel = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 14.95.sp)
    val verdict = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.5.sp, letterSpacing = (-0.3).sp)
    val reviewTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 19.2.sp)
    val reviewMeta = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 15.sp, fontFeatureSettings = "'tnum' 1")
    val mapName = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)
    val mapSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.3.sp)
    val mapPart = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, lineHeight = 14.sp)

    /** Hojas y diálogos. */
    val sheetTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.15.sp, letterSpacing = (-0.4).sp)
    val sheetSub = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.85.sp)
    val dialogTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 23.sp, letterSpacing = (-0.3).sp)
    val dialogBody = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.25.sp)
    val dialogButton = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val decisionTitle = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)
    val decisionBody = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.75.sp)
    val proposal = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, lineHeight = 12.sp, letterSpacing = 0.8.sp)
    val statLabel = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 18.sp)
    val statValue = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")
    val banner = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)
    val bannerBody = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.125.sp)
    val pill = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val ghost = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp)
}

/** La forma de una tarjeta de reparto (24 dp) y de las sesiones (20 dp). */
internal val RpCardShape = RoundedCornerShape(24.dp)
internal val RpSessionShape = RoundedCornerShape(20.dp)

/** El filete interior de la casilla de descanso: `inset 0 0 0 1px #ffffff1f` (el catálogo trae `#ffffff14`; la fila de la matriz manda). */
private val RingSpec = KpknShadow.BordeInterior.spec.copyLayer(0) { it.copy(color = Color(0x1FFFFFFF)) }

/** Texto con trozos en negrita (`<b>`): la negrita es 600 y va en tinta. */
internal fun rpAnnotated(rich: RpRich, boldColor: Color = WizardColors.text): AnnotatedString = buildAnnotatedString {
    rich.forEach { span ->
        if (span.bold) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = boldColor)) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}

@Composable
internal fun RpRichText(rich: RpRich, style: TextStyle, color: Color, modifier: Modifier = Modifier, boldColor: Color = WizardColors.text) {
    Text(text = rpAnnotated(rich, boldColor), style = style, color = color, modifier = modifier)
}

/** El módulo del lienzo (`.mod`): `module`, filete de 1 dp, 24 dp de radio. */
@Composable
internal fun RpModule(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RpCardShape)
            .background(KpknSurface.module, RpCardShape)
            .border(1.dp, RpColors.line, RpCardShape)
            .padding(1.dp)
            .padding(padding),
        content = content,
    )
}

/** `.rp-sec`: el rótulo de una sección con su contador a la derecha; 22 dp por encima y 10 por debajo (o lo que diga [top]). */
@Composable
internal fun RpSectionHeader(label: String, trailing: String? = null, top: Dp = 22.dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = top, bottom = 10.dp).height(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(java.util.Locale("es")), style = RpType.section, color = WizardColors.textFaint, maxLines = 1)
        if (trailing != null) Text(trailing, style = RpType.sectionCount, color = WizardColors.textFaint, maxLines = 1)
    }
}

/** Una etiqueta `.rp-m`: [bold] va en tinta y 600; [rest] el resto. */
@Composable
internal fun RpMetaChip(
    bold: String?,
    rest: String?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    icon: ImageVector? = null,
    background: Color = RpColors.tonal,
    contentColor: Color = WizardColors.textMuted,
    boldColor: Color = WizardColors.text,
) {
    val shape = RoundedCornerShape(if (small) 12.dp else 14.dp)
    Row(
        modifier = modifier
            .height(if (small) 24.dp else 28.dp)
            .clip(shape)
            .background(background, shape)
            .padding(horizontal = if (small) 9.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = boldColor, modifier = Modifier.size(13.dp))
        val style = if (small) RpType.metaCard else RpType.meta
        Text(
            text = buildAnnotatedString {
                if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = boldColor)) { append(bold) }
                if (bold != null && rest != null) append(" ")
                if (rest != null) append(rest)
            },
            style = style,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** La tira de siete casillas (`.rp-strip`): una letra por día, una casilla de color con su inicial o un anillo en el descanso. */
@Composable
internal fun RpStrip(cells: List<StripCell>, modifier: Modifier = Modifier, small: Boolean = false) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.forEach { cell ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (!small) Text(cell.letter, style = RpType.stripLetter, color = WizardColors.textFaint, maxLines = 1)
                val dot = if (small) 22.dp else 30.dp
                if (cell.mark.role == DayRole.REST) {
                    // rp-S08 (anillo del descanso: valor exacto de la fila)
                    Box(Modifier.size(dot).kpknInset(RingSpec, CircleShape))
                } else {
                    // rp-T03: el fondo de la casilla cambia en 240 ms con la curva estandar
                    val fill by animateKpknColorAsState(RpColors.of(cell.mark.role), KpknMotion.D240, KpknMotion.Estandar)
                    Box(
                        modifier = Modifier.size(dot).clip(CircleShape).background(fill, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(cell.mark.initial, style = if (small) RpType.dotSmall else RpType.dot, color = RpColors.onInk, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
    }
}

/** Una fila en blanco del ancho de [width]; sirve para separar sin afectar al flujo. */
@Composable
internal fun RpGap(width: Dp = 0.dp, height: Dp = 0.dp) {
    Spacer(Modifier.width(width).height(height))
}

/** El borde de 1 dp que lleva el vidrio de las islas del lienzo (`#ffffff1f`). */
internal val RpGlassStroke = BorderStroke(1.dp, Color(0x1FFFFFFF))

/** Un círculo con el fondo [color] (los botones tonales de la barra). */
internal fun Modifier.rpCircle(size: Dp, color: Color, shape: Shape = CircleShape): Modifier =
    this.size(size).clip(shape).background(color, shape)

// ─── Iconos propios del lienzo (`P.*`): KpknLineIcons no los trae ──────────────────────────────

internal val RpSparkIcon: ImageVector = lineIcon("KpknLine.RpSpark", listOf("M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z"))
internal val RpWarnIcon: ImageVector = lineIcon("KpknLine.RpWarn", listOf("M12 4l9 15.5H3z", "M12 10v4.5", "M12 17.2v.01"))
internal val RpUndoIcon: ImageVector = lineIcon("KpknLine.RpUndo", listOf("M8.5 6.5L4 11l4.5 4.5", "M4.5 11H15a5 5 0 010 10h-3"))
internal val RpRedoIcon: ImageVector = lineIcon("KpknLine.RpRedo", listOf("M5 10V8a3 3 0 013-3h11l-3-3", "M19 14v2a3 3 0 01-3 3H5l3 3"))
internal val RpSlidersIcon: ImageVector = lineIcon(
    "KpknLine.RpSliders",
    listOf("M5 7h9", "M18 7h1", "M5 17h3", "M12 17h7", "M18 7a2 2 0 1 1-4 0 2 2 0 0 1 4 0z", "M12 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0z"),
)
internal val RpFilterIcon: ImageVector = lineIcon("KpknLine.RpFilter", listOf("M4 6h16", "M7 12h10", "M10 18h4"))
internal val RpInfoIcon: ImageVector = lineIcon("KpknLine.RpInfo", listOf("M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0z", "M12 11v5", "M12 8v.01"))
