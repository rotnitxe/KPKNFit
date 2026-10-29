package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknSurface
import java.util.Locale

/**
 * Una fila de ⋯ del programa (m-program): su icono, su título y su subtítulo (o nada). [group] es la etiqueta del grupo que empieza en esta
 * fila («Programa», «Registro»); null si sigue el grupo anterior. [onClick] se ejecuta después de cerrar la hoja.
 */
internal data class ProgramMenuItem(
    val icon: ImageVector,
    val label: String,
    val detail: String? = null,
    val group: String? = null,
    val onClick: () -> Unit,
)

/** La cabecera de la hoja: la portada, el nombre y la línea de estado («Activo · semana 4 de 6»). */
internal data class ProgramMenuHeader(
    val name: String,
    val statusLine: String,
    val identity: CoverIdentity?,
    val photoUrl: String?,
    val bundled: ImageBitmap?,
)

/**
 * Hoja ⋯ del programa (lienzo v6, m-program): la hoja de vidrio de siempre con la portada, el nombre y el estado arriba, y debajo las filas
 * con icono en círculo tonal, título y subtítulo, agrupadas con su etiqueta. Tocar fuera la cierra.
 */
@Composable
internal fun ProgramMoreMenu(header: ProgramMenuHeader, items: List<ProgramMenuItem>, onDismiss: () -> Unit) {
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                ProgramPoster(
                    identity = header.identity,
                    photoUrl = header.photoUrl,
                    bundled = header.bundled,
                    animate = false,
                    width = 84.dp,
                    height = 104.dp,
                    radius = 18.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = header.name,
                        style = MenuSheetTitleStyle,
                        color = WizardColors.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                    Text(text = header.statusLine, style = MenuCapStyle, color = WizardColors.textFaint, maxLines = 1)
                }
            }
            items.forEachIndexed { index, item ->
                item.group?.let { group ->
                    Text(
                        text = group.uppercase(Locale("es")),
                        style = MenuGroupStyle,
                        color = WizardColors.textFaint,
                        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                ProgramMenuRow(item = item, divider = index > 0, onDismiss = onDismiss)
            }
        }
    }
}

/** Una fila (`.mi`): 54 dp como mínimo, filete arriba salvo la primera, icono de 19 dp en un círculo de 38 dp, título y subtítulo. */
@Composable
private fun ProgramMenuRow(item: ProgramMenuItem, divider: Boolean, onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (divider) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        onDismiss()
                        item.onClick()
                    },
                )
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.047f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = WizardColors.textMuted)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.label, style = MenuRowTitleStyle, color = WizardColors.text)
                item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Spacer(Modifier.height(2.dp))
                    Text(text = detail, style = MenuCapStyle, color = WizardColors.textFaint)
                }
            }
        }
    }
}

/**
 * El subtítulo de «Pendiente» en ⋯: los avisos en una frase («Propuesta de ajuste, check-in de la semana y programa terminado»), el
 * primero con mayúscula y el resto en minúscula.
 */
internal fun pendingMenuDetail(titles: List<String>): String {
    val parts = titles.map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { index, title ->
        if (index == 0) title else title.replaceFirstChar { it.lowercase(Locale("es")) }
    }
    return when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " y " + parts.last()
    }
}

/** «Qué cambió KPKN y por qué · 12»; sin decisiones, sin la cuenta. */
internal fun decisionsMenuDetail(count: Int): String =
    if (count > 0) "Qué cambió KPKN y por qué · $count" else "Qué cambió KPKN y por qué"

/** El título de la hoja (`.sheet h3`): Syne Bold 21 sp con −0,4 sp. */
private val MenuSheetTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.4).sp,
)

/** El título de una fila (`.mi strong`): Inter SemiBold 14,5 sp. */
private val MenuRowTitleStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)

/** Subtítulos y estado (`.cap`, `.mi span`): Inter 12 sp. */
private val MenuCapStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

/** La etiqueta de grupo (`.mgroup`): Inter SemiBold 10,5 sp con 1,3 sp de espaciado. */
private val MenuGroupStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.3.sp,
)

/** «Decisiones del sistema»: la chispa de cuatro puntas del lienzo (`spark`). KpknLineIcons no la trae. */
internal val MoreMenuSparkIcon: ImageVector = lineIcon("KpknLine.ProgramSpark", listOf("M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z"))

/** Un icono de línea del lienzo: trazo de 1,75 dp con extremos y uniones redondeados, retícula de 24. */
internal fun lineIcon(name: String, paths: List<String>): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    for (d in paths) {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

/** «Copiar la semana N»: dos hojas superpuestas (`copy` del lienzo). KpknLineIcons no lo trae. */
internal val EmptyWeekCopyIcon: ImageVector = lineIcon(
    "KpknLine.ProgramCopy",
    listOf("M11 8h6a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-6a3 3 0 0 1-3-3v-6a3 3 0 0 1 3-3z", "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"),
)
