package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/**
 * Un suplemento de «mi pila» (Pulido v1 · SUPLEMENTOS): lo que la persona toma cada día. Vive como lista en `Settings.supplementPila`
 * (JSON con valores por defecto; Room no se toca). Cada toma NO se guarda aquí: es un `NutritionLog` cuyo alimento lleva el id
 * `sup:<id>` (ver `SupplementEngine.takeFoodId`), así el historial, las rachas y los totales salen de los registros de siempre.
 *
 * @property id id del catálogo (`creatina`, `cafeina`…) o de uno propio (`propio-…`).
 * @property dose dosis habitual: lo que registra un toque, en la unidad del suplemento.
 * @property moment índice 0..6 de `SUPPLEMENT_MOMENTS` (al despertar … antes de dormir).
 * @property doses tomas al día; el objetivo diario es `dose × doses`.
 * @property reminder hora `HH:mm` del recordatorio diario, o null si no hay aviso.
 */
@Serializable
data class SupplementPilaItem(
    val id: String,
    val dose: Double,
    val moment: Int = 1,
    val doses: Int = 1,
    val reminder: String? = null,
    /** Datos de la persona para suplementos propios; los valores antiguos siguen siendo válidos. */
    val customName: String? = null,
    val customUnit: String? = null,
    val notes: String = "",
)
