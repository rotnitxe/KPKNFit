package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.domain.sessionassistant.SessionTotals

/** Las cuatro pestañas del asistente de sesión, en su orden: Métricas · Sugerencias · Plantillas · Reglas. */
enum class AssistantTab(val label: String) {
    METRICS("Métricas"),
    SUGGESTIONS("Sugerencias"),
    TEMPLATES("Plantillas"),
    RULES("Reglas"),
}

/** Los tres apartados de la pestaña Reglas: Valores · Límites · Tiempo. */
enum class RulesSection(val label: String) {
    VALUES("Valores"),
    LIMITS("Límites"),
    TIME("Tiempo"),
}

/** Dónde abrir el asistente (el anillo de minutos del editor abre Reglas › Tiempo). Se consume al leerlo. */
data class AssistantEntry(
    val tab: AssistantTab,
    val rules: RulesSection = RulesSection.VALUES,
)

/**
 * Una sugerencia que la persona aplicó o descartó: la tarjeta se pliega en una línea con «Deshacer» (`pulido:as-sug-aplicada`).
 * [before] es la sesión de la variante activa justo antes de aplicar; [stamp] es la marca de última modificación de la sesión
 * después de la acción: si la persona edita algo más, la marca cambia y la línea deja de ofrecerse (no se pisan sus cambios).
 */
data class AssistantSuggestionOutcome(
    val suggestionId: String,
    val kind: Kind,
    /** Línea de resumen de lo aplicado («2 cambios · −2 min»); vacía si se descartó. */
    val summary: String,
    val before: Session?,
    val variant: WeekVariant,
    val stamp: Long?,
    /** Posición que ocupaba entre las tarjetas, para plegarla en su sitio. */
    val position: Int,
) {
    enum class Kind { APPLIED, DISMISSED }
}

/** El estado de aplicar una plantilla dentro de la hoja: aplicando y aplicada (con el «Deshacer» de la sesión previa). */
sealed interface TemplateApplyProgress {
    val templateName: String

    data class Applying(
        override val templateName: String,
        val mode: SessionTemplateApplyMode,
        val currentExercises: Int,
        val templateExercises: Int,
    ) : TemplateApplyProgress

    data class Done(
        override val templateName: String,
        val mode: SessionTemplateApplyMode,
        val before: Session,
        val variant: WeekVariant,
        val stamp: Long?,
        val previous: SessionTotals,
        val now: SessionTotals,
        /** Lo que el motor avisa además (ejercicios que ya estaban, series añadidas por tu calibración). */
        val note: String,
    ) : TemplateApplyProgress
}
