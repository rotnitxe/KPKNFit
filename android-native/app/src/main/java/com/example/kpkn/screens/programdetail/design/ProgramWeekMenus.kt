package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.Composable
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.time.LocalDate
import java.util.Locale

/*
 * Los ⋯ de la pestaña Semana como hojas del lienzo v6: el de la semana (m-week) y el de una sesión (m-session). Sin estado: cada fila cierra
 * la hoja y llama a su acción; lo que va null no se pinta.
 */

/** «Intensificación · 29 oct – 2 nov»: la fase del bloque y el rango de fechas de la semana; lo que falte no se pinta. Null sin nada. */
internal fun weekMenuSubtitle(phase: String?, dateRange: String?): String? =
    listOfNotNull(phase?.takeIf { it.isNotBlank() }, dateRange?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null }

/**
 * «Miércoles 29 · 5 ejercicios · ≈ 48 min»: el día (con el número si tiene fecha), los ejercicios y la duración estimada de una sesión.
 */
internal fun sessionMenuSubtitle(dayName: String?, date: LocalDate?, exercises: Int, minutes: Int?): String {
    val day = dayName?.trim()?.takeIf { it.isNotEmpty() }?.let { name -> date?.let { "$name ${it.dayOfMonth}" } ?: name }
    val count = "$exercises ${if (exercises == 1) "ejercicio" else "ejercicios"}"
    return listOfNotNull(day, count, minutes?.takeIf { it > 0 }?.let { "≈ $it min" }).joinToString(" · ")
}

/**
 * El ⋯ de la semana (m-week): «Nombre y nota de la semana» y «Cambiar reparto»; «Copiar» con copiar y pegar; «Semanas» con añadir una
 * después y eliminar. [pasteLabel] es «Pegar la semana 3 aquí» (null si no hay nada copiado o es esta misma).
 */
@Composable
internal fun ProgramWeekMenuSheet(
    title: String,
    sub: String?,
    onEditMeta: (() -> Unit)?,
    onChangeSplit: (() -> Unit)?,
    onCopy: () -> Unit,
    pasteLabel: String?,
    onPaste: () -> Unit,
    onAddAfter: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = title, sub = sub)
        var first = true
        onEditMeta?.let {
            LienzoMenuRow(icon = KpknLineIcons.Edit, title = "Nombre y nota de la semana", onClick = run(it), divider = !first, tag = "week_menu_edit")
            first = false
        }
        onChangeSplit?.let {
            LienzoMenuRow(icon = LienzoDumbbellIcon, title = "Cambiar reparto", sub = "Qué entrena cada día", onClick = run(it), divider = !first, tag = "week_menu_split")
            first = false
        }
        LienzoGroup("Copiar")
        LienzoMenuRow(icon = EmptyWeekCopyIcon, title = "Copiar esta semana", onClick = run(onCopy), divider = false, tag = "week_menu_copy")
        if (pasteLabel != null) {
            LienzoMenuRow(
                icon = LienzoPasteIcon,
                title = pasteLabel,
                sub = "Si ya tiene sesiones, eliges cuáles reemplazar",
                onClick = run(onPaste),
                tag = "week_menu_paste",
            )
        }
        LienzoGroup("Semanas")
        LienzoMenuRow(icon = KpknLineIcons.Plus, title = "Añadir una semana después", onClick = run(onAddAfter), divider = false, tag = "week_menu_add")
        onDelete?.let {
            LienzoMenuRow(icon = KpknLineIcons.Trash, title = "Eliminar esta semana", sub = "Quita sus sesiones", onClick = run(it), danger = true, tag = "week_menu_delete")
        }
    }
}

/**
 * El ⋯ de una sesión (m-session): «Editar sesión», «Mover a otro día», «Restaurar desde el plan» (solo si la sesión está editada y se puede
 * restaurar) y «Eliminar sesión», que pide confirmación.
 */
@Composable
internal fun ProgramSessionMenuSheet(
    title: String,
    sub: String?,
    onEdit: () -> Unit,
    onMove: (() -> Unit)?,
    onRestore: (() -> Unit)?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = title, sub = sub)
        LienzoMenuRow(icon = KpknLineIcons.Edit, title = "Editar sesión", sub = "Ejercicios, series, cargas y orden", onClick = run(onEdit), divider = false)
        onMove?.let { LienzoMenuRow(icon = KpknLineIcons.Calendar, title = "Mover a otro día", sub = "También puedes arrastrarla", onClick = run(it)) }
        onRestore?.let {
            LienzoMenuRow(
                icon = KpknLineIcons.Repeat,
                title = "Restaurar desde el plan",
                sub = "Deshace tu edición en esta sesión; el historial se conserva",
                onClick = run(it),
            )
        }
        LienzoMenuRow(icon = KpknLineIcons.Trash, title = "Eliminar sesión", onClick = run(onDelete), danger = true)
    }
}

/** El nombre del día con mayúscula inicial («Miércoles»). */
internal fun capitalizedDayName(name: String): String = name.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
