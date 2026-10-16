package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.isCardio

/** Qué selector abre «Reemplazar» para un ejercicio (LV5): el del cardio para el cardio y el catálogo de fuerza para el resto. */
internal enum class ReplacePicker { STRENGTH_CATALOG, CARDIO }

/**
 * El selector de reemplazo que toca al ejercicio. Las opciones del ejercicio ya separaban el cardio, pero otras entradas (el relator)
 * abrían siempre el catálogo de fuerza y un ejercicio de cardio podía acabar convertido en uno de fuerza.
 */
internal fun replacePickerFor(exercise: Exercise?): ReplacePicker =
    if (exercise?.isCardio == true) ReplacePicker.CARDIO else ReplacePicker.STRENGTH_CATALOG
