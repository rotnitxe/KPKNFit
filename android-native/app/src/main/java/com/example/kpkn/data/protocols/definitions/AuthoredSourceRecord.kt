package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass

/**
 * Ficha editorial de una fuente consultada (§10.1 regla 4).
 *
 * [PlanProvenance] (contrato aceptado del paquete B) no tiene campo dedicado
 * para la fecha de consulta ni para las reglas efectivas de la fuente, así que
 * esos dos datos viven aquí, en la entrada del catálogo, y se reflejan en
 * [PlanProvenance.sourceEdition] para que el programa conservado en JSON los
 * mantenga aunque cambie el catálogo.
 *
 * [consultedOn] es SIEMPRE la fecha de consulta de la fuente (2026-09-28 para
 * PHUL/PHAT), nunca la fecha de la edición publicada.
 */
data class AuthoredSourceRecord(
    /** Id editorial del plan publicado (p. ej. `original:phul-ms-2021-r1`). */
    val planId: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val author: String,
    /** Edición/fecha publicada de la fuente (p. ej. «edición actualizada 2021-05-26»). */
    val edition: String,
    /** Fecha en que se consultó la fuente para auditar la fidelidad. */
    val consultedOn: String,
    /** Identidad de la receta/revisión de origen citada. */
    val sourceRecipeRevision: String,
    /** Reglas efectivas que la tabla fuente sí publica (sin defaults KPKN). */
    val effectiveRules: List<String>,
    /** Defaults operativos mostrados como «Configuración inicial KPKN». */
    val kpknDefaults: List<String>,
) {
    /** Valor para [PlanProvenance.sourceEdition]: edición + consulta, separados por « · ». */
    val editionWithConsult: String get() = "$edition · fuente consultada $consultedOn"
}

/**
 * Fichas de las dos fuentes de autor de §10/§18.1 (paquete E). La consulta
 * registrada es la del plan (2026-09-28); los artículos son gratuitos y solo
 * se registran datos de programación y atribución (§10.1 regla 6).
 */
object AuthoredSources {
    const val CONSULTED_ON = "2026-09-28"

    const val PHUL_URL = "https://www.muscleandstrength.com/workouts/phul-workout"
    const val PHAT_URL =
        "https://biolayne.com/articles/training/phat-power-hypertrophy-adaptive-training/"

    val phul = AuthoredSourceRecord(
        planId = "original:phul-ms-2021-r1",
        sourceTitle = "PHUL workout",
        sourceUrl = PHUL_URL,
        author = "Brandon Campbell",
        edition = "Muscle & Strength, edición actualizada 2021-05-26",
        consultedOn = CONSULTED_ON,
        sourceRecipeRevision = "PHUL M&S 2021 · receta r1",
        effectiveRules = listOf(
            "4 días por semana (lunes, martes, jueves, viernes) con microciclo repetido durante 12 semanas.",
            "Rangos publicados 3–4×3–5 / 6–10 / 8–12 / 10–15 según ejercicio; la fuente exige al menos una repetición en reserva y permite sustituir ejercicios accesorios.",
            "Los principales definidos (banca, sentadilla, peso muerto, sentadilla frontal y press) no se sustituyen dentro de la versión Original.",
            "Sin porcentajes de 1RM/TM ni periodización porcentual en la tabla.",
        ),
        kpknDefaults = listOf(
            "Descansos: 180 s en banca/sentadilla/peso muerto/remo pesado, 120 s en otros compuestos y 90 s en aislamientos (la tabla no publica descansos).",
            "Elección inicial dentro de rangos: mínimo de series y RIR 2.",
            "Calentamiento: preset operativo KPKN (3 min generales + aproximación técnica del primer patrón), no publicado por Campbell.",
            "Nombres traducidos al español; cada slot conserva el canonicalName del catálogo.",
            "Regla de progreso inicial: recomendación KPKN §12.4, no un incremento escrito por Campbell.",
        ),
    )

    val phat = AuthoredSourceRecord(
        planId = "original:phat-biolayne-2016-r1",
        sourceTitle = "PHAT: Power Hypertrophy Adaptive Training",
        sourceUrl = PHAT_URL,
        author = "Layne Norton",
        edition = "Biolayne, publicado el 2016-05-30",
        consultedOn = CONSULTED_ON,
        sourceRecipeRevision = "PHAT Biolayne 2016 · ejemplo publicado r1",
        effectiveRules = listOf(
            "5 días (1, 2, 4, 5, 6) descansando 3 y 7; el artículo lo presenta como ejemplo para personas adaptadas a alta frecuencia y volumen.",
            "Tres bloques SPEED 6×3 al 65–70 % de la carga habitual de 3–5 repeticiones del MISMO ejercicio, al inicio de los tres días de hipertrofia.",
            "Pesados 3–5 con descanso largo; hipertrofia con descansos de 1–2 min; RIR 2 inicial en semanas 1–4 y RIR 1–2 después.",
            "La descarga sugerida cada 6–12 semanas se muestra como guía de fuente: la ventana publicada aquí son 6 semanas, sin semana 7 fabricada.",
        ),
        kpknDefaults = listOf(
            "Descansos: 240 s en los pesados 3–5, 120 s en compuestos de hipertrofia y 90 s en aislamientos y SPEED (la tabla no publica descansos por ejercicio).",
            "Elección inicial dentro de rangos: mínimo de series, SPEED al 65 % (rango 65–70 %) y RIR 2 en semanas 1–4 / RIR 1 en semanas 5–6 (rango publicado 1–2).",
            "Calentamiento: preset operativo KPKN, no publicado en el artículo.",
            "Nombres traducidos al español; cada slot conserva el canonicalName del catálogo.",
            "La rotación de principales cada 2–3 semanas que propone la fuente queda como opción explícita, no automatizada.",
        ),
    )

    fun of(planId: String): AuthoredSourceRecord? = when (planId) {
        phul.planId -> phul
        phat.planId -> phat
        else -> null
    }
}

/** Procedencia ORIGINAL de una receta de autor (§10.1). */
fun AuthoredSourceRecord.toOriginalProvenance(recipeId: String): PlanProvenance = PlanProvenance(
    planId = planId,
    recipeId = recipeId,
    revision = 1,
    category = PlanProvenanceClass.ORIGINAL,
    technicalOrigin = CatalogSource.PROTOCOL,
    sourceTitle = sourceTitle,
    sourceUrl = sourceUrl,
    sourceAuthor = author,
    sourceEdition = editionWithConsult,
    slotChanges = emptyList(),
    operationalDefaults = kpknDefaults.map { note ->
        val scope = when {
            note.startsWith("Descansos") -> com.example.kpkn.data.protocols.KpknOperationalDefaultScope.REST
            note.startsWith("Elección") -> com.example.kpkn.data.protocols.KpknOperationalDefaultScope.INITIAL_CHOICE
            note.startsWith("Calentamiento") -> com.example.kpkn.data.protocols.KpknOperationalDefaultScope.WARMUP
            note.startsWith("Nombres") -> com.example.kpkn.data.protocols.KpknOperationalDefaultScope.NAMING
            else -> com.example.kpkn.data.protocols.KpknOperationalDefaultScope.OTHER
        }
        com.example.kpkn.data.protocols.KpknOperationalDefault(scope, note)
    },
)
