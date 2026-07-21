import Foundation

struct InferredExerciseMetrics {
    let efc: Double
    let cnc: Double
    let ssc: Double
    let suggestedMuscles: [InvolvedMuscle]
    let suggestedBodyPart: String
    let suggestedChain: String
}

func inferExerciseMetrics(
    type: String,
    force: String,
    equipment: String,
    category: String,
    isAxialLoaded: Bool,
    exerciseName: String
) -> InferredExerciseMetrics {
    var baseEfc = 2.0
    var baseCnc = 2.0

    if type.caseInsensitiveCompare("Aislamiento") == .orderedSame {
        baseEfc = 1.5
        baseCnc = 1.5
    } else {
        switch force {
        case "Sentadilla": baseEfc = 4.0; baseCnc = 4.0
        case "Bisagra": baseEfc = 4.5; baseCnc = 4.5
        case "Empuje", "Tirón", "Tiron": baseEfc = 3.2; baseCnc = 3.2
        case "Anti-Extensión", "Anti-Extension", "Anti-Flexión", "Anti-Flexion", "Anti-Rotación", "Anti-Rotacion", "Flexión", "Flexion":
            baseEfc = 2.0; baseCnc = 2.5
        default: baseEfc = 2.5; baseCnc = 2.5
        }
    }

    let (eqEfcMult, eqCncMult): (Double, Double)
    switch equipment {
    case "Barra": eqEfcMult = 1.0; eqCncMult = 1.2
    case "Mancuerna": eqEfcMult = 0.9; eqCncMult = 1.1
    case "Máquina", "Maquina", "Polea": eqEfcMult = 0.8; eqCncMult = 0.6
    case "Peso Corporal": eqEfcMult = 0.8; eqCncMult = 0.8
    default: eqEfcMult = 1.0; eqCncMult = 1.0
    }

    var efc = baseEfc * eqEfcMult
    var cnc = baseCnc * eqCncMult

    if category.caseInsensitiveCompare("Fuerza") == .orderedSame || category.caseInsensitiveCompare("Potencia") == .orderedSame {
        cnc += 0.5
    }

    let ssc: Double
    if !isAxialLoaded {
        ssc = 0.2
    } else if equipment == "Barra" {
        ssc = 1.5
    } else if equipment == "Mancuerna" {
        ssc = 1.0
    } else {
        ssc = 0.8
    }

    efc = max(0.5, min(5.0, efc))
    cnc = max(0.5, min(5.0, cnc))
    let sscClamped = max(0.0, min(2.0, ssc))

    let normalizedForce = force.lowercased()
    let normalizedName = exerciseName.lowercased()
    let muscles: [InvolvedMuscle]

    if normalizedForce.contains("sentadilla") || normalizedName.contains("sentadilla") || normalizedName.contains("squat") {
        muscles = [
            InvolvedMuscle(muscle: "Cuádriceps", role: .PRIMARY, volumeContribution: 1.0),
            InvolvedMuscle(muscle: "Glúteos", role: .SECONDARY, volumeContribution: 0.5),
            InvolvedMuscle(muscle: "Core", role: .STABILIZER, volumeContribution: 0.4),
        ]
    } else if normalizedForce.contains("bisagra") || normalizedName.contains("deadlift") || normalizedName.contains("rumano") || normalizedName.contains("peso muerto") {
        muscles = [
            InvolvedMuscle(muscle: "Glúteos", role: .PRIMARY, volumeContribution: 1.0),
            InvolvedMuscle(muscle: "Isquiosurales", role: .SECONDARY, volumeContribution: 0.5),
            InvolvedMuscle(muscle: "Erectores Espinales", role: .STABILIZER, volumeContribution: 0.4),
        ]
    } else if normalizedForce.contains("empuje") || normalizedName.contains("press") {
        muscles = [
            InvolvedMuscle(muscle: "Pectorales", role: .PRIMARY, volumeContribution: 1.0),
            InvolvedMuscle(muscle: "Deltoides", role: .SECONDARY, volumeContribution: 0.5),
            InvolvedMuscle(muscle: "Tríceps", role: .SECONDARY, volumeContribution: 0.5),
            InvolvedMuscle(muscle: "Core", role: .STABILIZER, volumeContribution: 0.4),
        ]
    } else if normalizedForce.contains("tirón") || normalizedForce.contains("tiron") || normalizedName.contains("remo") || normalizedName.contains("dominada") {
        muscles = [
            InvolvedMuscle(muscle: "Dorsales", role: .PRIMARY, volumeContribution: 1.0),
            InvolvedMuscle(muscle: "Bíceps", role: .SECONDARY, volumeContribution: 0.5),
            InvolvedMuscle(muscle: "Trapecio", role: .SECONDARY, volumeContribution: 0.5),
            InvolvedMuscle(muscle: "Core", role: .STABILIZER, volumeContribution: 0.4),
        ]
    } else if normalizedName.contains("curl") {
        muscles = [
            InvolvedMuscle(muscle: "Bíceps", role: .PRIMARY, volumeContribution: 1.0),
            InvolvedMuscle(muscle: "Antebrazo", role: .SECONDARY, volumeContribution: 0.5),
        ]
    } else if normalizedName.contains("triceps") || normalizedName.contains("tríceps") {
        muscles = [
            InvolvedMuscle(muscle: "Tríceps", role: .PRIMARY, volumeContribution: 1.0),
            InvolvedMuscle(muscle: "Deltoides", role: .SECONDARY, volumeContribution: 0.5),
        ]
    } else {
        muscles = [
            InvolvedMuscle(muscle: "Core", role: .PRIMARY, volumeContribution: 1.0),
            InvolvedMuscle(muscle: "Erectores Espinales", role: .STABILIZER, volumeContribution: 0.4),
        ]
    }

    let lowerMuscles: Set<String> = ["Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores"]
    let upperMuscles: Set<String> = ["Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Antebrazo", "Trapecio"]

    let bodyPart: String
    if muscles.contains(where: { lowerMuscles.contains($0.muscle) }) {
        bodyPart = "lower"
    } else if muscles.contains(where: { upperMuscles.contains($0.muscle) }) {
        bodyPart = "upper"
    } else {
        bodyPart = "core"
    }

    let chain: String
    if normalizedForce.contains("sentadilla") || normalizedForce.contains("empuje") {
        chain = "anterior"
    } else if normalizedForce.contains("bisagra") || normalizedForce.contains("tirón") || normalizedForce.contains("tiron") {
        chain = "posterior"
    } else {
        chain = "full"
    }

    return InferredExerciseMetrics(
        efc: efc,
        cnc: cnc,
        ssc: sscClamped,
        suggestedMuscles: muscles,
        suggestedBodyPart: bodyPart,
        suggestedChain: chain
    )
}
