package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface

/** Lo que se ve al abrir la pestaña Buscar sin escribir nada (lienzo s-start): los alimentos recientes y los cuatro atajos. */
@Composable
fun LoggerSearchStart(
    recents: List<LoggerRecentFood>,
    favoriteIds: Set<String>,
    onAddRecent: (LoggerRecentFood) -> Unit,
    onOpenRecent: (LoggerRecentFood) -> Unit,
    onScan: () -> Unit,
    onQuickEntry: () -> Unit,
    onRecipes: () -> Unit,
    onTemplates: () -> Unit,
    recipeCount: Int,
    modifier: Modifier = Modifier,
    /** Alterna el favorito de un reciente. Sin él no se pinta la estrella de las filas. */
    onToggleFavorite: ((LoggerRecentFood) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (recents.isNotEmpty()) {
            LoggerPageGroup("Recientes")
            LoggerTightModule {
                recents.forEachIndexed { index, recent ->
                    val hasId = recent.food.id.isNotBlank()
                    LoggerSearchRow(
                        name = recent.name,
                        caption = recent.caption,
                        kcal = recent.kcal,
                        favorite = hasId && recent.food.id in favoriteIds,
                        onToggleFavorite = if (hasId && onToggleFavorite != null) ({ onToggleFavorite(recent) }) else null,
                        onAdd = { onAddRecent(recent) },
                        onOpen = { onOpenRecent(recent) },
                        divider = index > 0,
                    )
                }
            }
        }
        LoggerPageGroup("Atajos")
        Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoggerShortcut(LoggerIcons.Barcode, "Escanear", "Código de barras", onScan, Modifier.weight(1f).fillMaxHeight())
                LoggerShortcut(LoggerIcons.Bolt, "Entrada rápida", "Solo calorías y macros", onQuickEntry, Modifier.weight(1f).fillMaxHeight())
            }
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoggerShortcut(KpknLineIcons.Book, "Recetas", loggerRecipesCaption(recipeCount), onRecipes, Modifier.weight(1f).fillMaxHeight())
                LoggerShortcut(LoggerIcons.Copy, "Plantillas", "Comidas guardadas", onTemplates, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** El pie del atajo «Recetas»: «Tus 4 recetas», «Tu receta» con una, «Ninguna todavía» sin ninguna. */
internal fun loggerRecipesCaption(count: Int): String = when {
    count <= 0 -> "Ninguna todavía"
    count == 1 -> "Tu receta"
    else -> "Tus $count recetas"
}

/** `.mod.tight`: módulo de 24 dp de radio con 12 dp de margen arriba y abajo y 14 a los lados, para filas de resultado. */
@Composable
internal fun LoggerTightModule(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KpknSurface.module, shape)
            .border(1.dp, KpknSurface.moduleBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

/** `.opt` de 92 dp de alto como mínimo: círculo de 36 dp con el icono, el título y su pie. */
@Composable
private fun LoggerShortcut(icon: ImageVector, title: String, caption: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.031f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.063f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = LoggerColors.ink)
        }
        Text(text = title, style = LoggerText.act.copy(fontSize = 14.sp, lineHeight = 20.sp), color = LoggerColors.ink)
        Text(text = caption, style = LoggerText.cap, color = LoggerColors.faint)
    }
}

/**
 * Filas de carga mientras se busca (lienzo s-loading `.skel`): [rows] bloques de 56 dp con 16 dp de radio y 8 dp de hueco, con un brillo
 * que los cruza cada 1,2 s. Con movimiento reducido el brillo se queda quieto.
 */
@Composable
fun LoggerSearchSkeleton(modifier: Modifier = Modifier, rows: Int = 5) {
    val reduced = wizardReducedMotion()
    val shift = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "skeletonShift",
        )
    }
    val low = Color.White.copy(alpha = 0.031f)
    val high = Color.White.copy(alpha = 0.071f)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics { contentDescription = "Buscando…" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rows) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .drawBehind {
                        // `background-size: 200%`: el degradado mide dos veces el ancho y se repite; el desplazamiento de un periodo cierra el ciclo.
                        val period = size.width * 2f
                        val start = (shift?.value ?: 0f) * period
                        drawRect(
                            Brush.horizontalGradient(
                                colors = listOf(low, high, low),
                                startX = start,
                                endX = start + period,
                                tileMode = TileMode.Repeated,
                            ),
                        )
                    },
            )
        }
    }
}
