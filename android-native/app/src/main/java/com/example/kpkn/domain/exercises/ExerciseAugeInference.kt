package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import kotlin.math.max
import kotlin.math.min

data class InferredExerciseMetrics(
    val efc: Double,
    val cnc: Double,
    val ssc: Double,
    val suggestedMuscles: List<InvolvedMuscle>,
    val suggestedBodyPart: String,
    val suggestedChain: String,
)

/**
 * Infiere métricas AUGE a partir de atributos estructurales del ejercicio (tipo, fuerza, equipo).
 *
 * @deprecated Esta función inventa valores cuando la DB no tiene datos. Para cálculos de drenaje
 * en runtime, usa [com.example.kpkn.domain.auge.AugeFatigueEngine.getDynamicAugeMetrics] con la
 * entrada de DB correspondiente, que retorna null en lugar de inventar. Esta función solo debe
 * usarse como prefill de sugerencias en la UI de creación de ejercicios (donde no hay DB id aún).
 */
@Deprecated(
    message = "Solo para prefill de UI en creación de ejercicios. " +
        "Para drain calculations, usa AugeFatigueEngine.getDynamicAugeMetrics que retorna null cuando la DB no tiene datos.",
)
fun inferExerciseMetrics(
    type: String,
    force: String,
    equipment: String,
    category: String,
    isAxialLoaded: Boolean,
    exerciseName: String,
): InferredExerciseMetrics {
    var baseEfc = 2.0
    var baseCnc = 2.0

    if (type.equals("Aislamiento", ignoreCase = true)) {
        baseEfc = 1.5
        baseCnc = 1.5
    } else {
        when (force) {
            "Sentadilla" -> {
                baseEfc = 4.0
                baseCnc = 4.0
            }

            "Bisagra" -> {
                baseEfc = 4.5
                baseCnc = 4.5
            }

            "Empuje", "Tirón", "Tiron" -> {
                baseEfc = 3.2
                baseCnc = 3.2
            }

            "Anti-Extensión", "Anti-Extension", "Anti-Flexión", "Anti-Flexion", "Anti-Rotación", "Anti-Rotacion", "Flexión", "Flexion" -> {
                baseEfc = 2.0
                baseCnc = 2.5
            }

            else -> {
                baseEfc = 2.5
                baseCnc = 2.5
            }
        }
    }

    val (eqEfcMult, eqCncMult) = when (equipment) {
        "Barra" -> 1.0 to 1.2
        "Mancuerna" -> 0.9 to 1.1
        "Máquina", "Maquina", "Polea" -> 0.8 to 0.6
        "Peso Corporal" -> 0.8 to 0.8
        else -> 1.0 to 1.0
    }

    var efc = baseEfc * eqEfcMult
    var cnc = baseCnc * eqCncMult

    if (category.equals("Fuerza", ignoreCase = true) || category.equals("Potencia", ignoreCase = true)) {
        cnc += 0.5
    }

    val ssc = when {
        !isAxialLoaded -> 0.2
        equipment == "Barra" -> 1.5
        equipment == "Mancuerna" -> 1.0
        else -> 0.8
    }

    efc = max(0.5, min(5.0, efc))
    cnc = max(0.5, min(5.0, cnc))
    val sscClamped = max(0.0, min(2.0, ssc))

    val normalizedForce = force.lowercase()
    val normalizedName = exerciseName.lowercase()
    val muscles = when {
        normalizedForce.contains("sentadilla") || normalizedName.contains("sentadilla") || normalizedName.contains("squat") -> listOf(
            InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Glúteos", MuscleRole.SECONDARY, 0.5),
            InvolvedMuscle("Core", MuscleRole.STABILIZER, 0.4),
        )

        normalizedForce.contains("bisagra") || normalizedName.contains("deadlift") || normalizedName.contains("rumano") || normalizedName.contains("peso muerto") -> listOf(
            InvolvedMuscle("Glúteos", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Isquiosurales", MuscleRole.SECONDARY, 0.5),
            InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER, 0.4),
        )

        normalizedForce.contains("empuje") || normalizedName.contains("press") -> listOf(
            InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Deltoides", MuscleRole.SECONDARY, 0.5),
            InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, 0.5),
            InvolvedMuscle("Core", MuscleRole.STABILIZER, 0.4),
        )

        normalizedForce.contains("tirón") || normalizedForce.contains("tiron") || normalizedName.contains("remo") || normalizedName.contains("dominada") -> listOf(
            InvolvedMuscle("Dorsales", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Bíceps", MuscleRole.SECONDARY, 0.5),
            InvolvedMuscle("Trapecio", MuscleRole.SECONDARY, 0.5),
            InvolvedMuscle("Core", MuscleRole.STABILIZER, 0.4),
        )

        normalizedName.contains("curl") -> listOf(
            InvolvedMuscle("Bíceps", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Antebrazo", MuscleRole.SECONDARY, 0.5),
        )

        normalizedName.contains("triceps") || normalizedName.contains("tríceps") -> listOf(
            InvolvedMuscle("Tríceps", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Deltoides", MuscleRole.SECONDARY, 0.5),
        )

        else -> listOf(
            InvolvedMuscle("Core", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER, 0.4),
        )
    }

    val bodyPart = when {
        muscles.any { it.muscle in setOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores") } -> "lower"
        muscles.any { it.muscle in setOf("Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Antebrazo", "Trapecio") } -> "upper"
        else -> "core"
    }

    val chain = when {
        normalizedForce.contains("sentadilla") || normalizedForce.contains("empuje") -> "anterior"
        normalizedForce.contains("bisagra") || normalizedForce.contains("tirón") || normalizedForce.contains("tiron") -> "posterior"
        else -> "full"
    }

    return InferredExerciseMetrics(
        efc = efc,
        cnc = cnc,
        ssc = sscClamped,
        suggestedMuscles = muscles,
        suggestedBodyPart = bodyPart,
        suggestedChain = chain,
    )
}
