import SwiftUI

enum WorkoutContinuityPhase {
    case currentExercise
    case superset
    case nextExercise
    case nextBlock
    case sessionFinish
}

struct WorkoutContinuityState {
    let phase: WorkoutContinuityPhase
    let eyebrow: String
    let title: String
    let body: String
    let progressLabel: String
    let nextExerciseName: String?
    let nextSetLabel: String?
    let accentHex: String?
    let feedbackPrompt: String?
}

struct WorkoutContinuityTransitionTarget {
    let key: String
    let eyebrow: String
    let title: String
    let body: String
    let accentHex: String?
}

func buildWorkoutContinuityState(
    session: Session,
    visibleExercises: [Exercise],
    currentExerciseIdx: Int,
    currentSetIdx: Int,
    feedbackPrompt: String? = nil
) -> WorkoutContinuityState? {
    guard visibleExercises.indices.contains(currentExerciseIdx) else { return nil }
    let currentExercise = visibleExercises[currentExerciseIdx]
    let currentPart = session.partForExercise(currentExercise.id)
    let visibleParts = session.visibleParts(visibleExercises)
    let currentPartIndex = currentPart.flatMap { part in
        visibleParts.firstIndex(where: { $0.id == part.id })
    }
    let blockLabel: String = {
        if let part = currentPart, let idx = currentPartIndex {
            return "Bloque \(idx + 1) de \(visibleParts.count)"
        } else if currentPart != nil {
            return "Bloque activo"
        } else {
            return "Sesion activa"
        }
    }()
    let progressLabel = "Ejercicio \(currentExerciseIdx + 1) de \(visibleExercises.count)"
    let currentSetNumber = currentSetIdx + 1

    if let supersetTarget = nextSupersetHop(visibleExercises, currentIdx: currentExerciseIdx, currentSetIdx: currentSetIdx) {
        let nextExercise = visibleExercises[supersetTarget.0]
        return WorkoutContinuityState(
            phase: .superset,
            eyebrow: blockLabel,
            title: "Empalma con \(nextExercise.name)",
            body: "Superset en la misma ronda. Despues de cerrar aqui, sigues con Serie \(supersetTarget.1 + 1).",
            progressLabel: progressLabel,
            nextExerciseName: nextExercise.name,
            nextSetLabel: "Serie \(supersetTarget.1 + 1)",
            accentHex: currentPart?.color,
            feedbackPrompt: feedbackPrompt
        )
    }

    let lastSetIndex = max(currentExercise.sets.count - 1, 0)
    if currentSetIdx < lastSetIndex {
        let remainingSets = lastSetIndex - currentSetIdx
        return WorkoutContinuityState(
            phase: .currentExercise,
            eyebrow: blockLabel,
            title: remainingSets == 1
                ? "Ultima serie para cerrar \(currentExercise.name)"
                : "Quedan \(remainingSets) series en \(currentExercise.name)",
            body: "Mantienes el foco en \(currentPart?.name ?? session.name). Ahora vas por Serie \(currentSetNumber).",
            progressLabel: progressLabel,
            nextExerciseName: currentExercise.name,
            nextSetLabel: "Serie \(currentSetNumber + 1)",
            accentHex: currentPart?.color,
            feedbackPrompt: feedbackPrompt
        )
    }

    let nextExercise = visibleExercises[safe: currentExerciseIdx + 1]
    if nextExercise == nil {
        return WorkoutContinuityState(
            phase: .sessionFinish,
            eyebrow: blockLabel,
            title: "Cierras \(currentPart?.name ?? session.name)",
            body: "Esta es la ultima estacion de la sesion. Al completar la serie, pasas al cierre final.",
            progressLabel: progressLabel,
            accentHex: currentPart?.color,
            feedbackPrompt: feedbackPrompt
        )
    }

    let nextPart = session.partForExercise(nextExercise!.id)
    let entersNewBlock = currentPart?.id != nextPart?.id && nextPart != nil
    return WorkoutContinuityState(
        phase: entersNewBlock ? .nextBlock : .nextExercise,
        eyebrow: entersNewBlock ? "Cambio de bloque" : blockLabel,
        title: entersNewBlock ? "Se abre \(nextPart!.name)" : "Sigue con \(nextExercise!.name)",
        body: entersNewBlock
            ? "Cierras \(currentExercise.name) y entras al siguiente bloque con una transicion limpia."
            : "Despues de esta serie, el flujo continua sin salir del ritmo actual.",
        progressLabel: progressLabel,
        nextExerciseName: nextExercise?.name,
        nextSetLabel: "Serie 1",
        accentHex: nextPart?.color ?? currentPart?.color,
        feedbackPrompt: feedbackPrompt
    )
}

func buildWorkoutContinuityTransitionTarget(
    session: Session,
    visibleExercises: [Exercise],
    currentExerciseIdx: Int
) -> WorkoutContinuityTransitionTarget? {
    guard currentExerciseIdx > 0 else { return nil }
    let currentExercise = visibleExercises[safe: currentExerciseIdx]
    let previousExercise = visibleExercises[safe: currentExerciseIdx - 1]
    guard let current = currentExercise else { return nil }
    let currentPart = session.partForExercise(current.id)
    let previousPart = previousExercise.flatMap { session.partForExercise($0.id) }
    let entersNewBlock = currentPart?.id != previousPart?.id && currentPart != nil
    return WorkoutContinuityTransitionTarget(
        key: "\(current.id)_\(currentExerciseIdx)",
        eyebrow: entersNewBlock ? "Nuevo bloque" : "Siguiente estacion",
        title: entersNewBlock ? "Entras a \(currentPart!.name)" : "Ahora sigue \(current.name)",
        body: entersNewBlock
            ? "El flujo cambia de bloque, pero mantienes el ritmo de la sesion."
            : "La transicion se siente limpia: cambias de ejercicio sin perder contexto.",
        accentHex: currentPart?.color
    )
}

func pendingWorkoutFeedbackHandoffExercise(
    visibleExercises: [Exercise],
    completedSets: [String: CompletedSet],
    postExerciseFeedbackByExerciseId: [String: PostExerciseFeedback],
    loggedSetKey: String?,
    currentExerciseId: String?
) -> Exercise? {
    guard let sourceKey = loggedSetKey else { return nil }
    let loggedExercise = visibleExercises
        .sorted { $0.id.count > $1.id.count }
        .first { sourceKey == $0.id || sourceKey.hasPrefix("\($0.id)_") }
    guard let exercise = loggedExercise else { return nil }
    if exercise.id == currentExerciseId { return nil }
    if postExerciseFeedbackByExerciseId.keys.contains(exercise.id) { return nil }
    let isComplete = !exercise.sets.isEmpty && exercise.sets.indices.allSatisfy { setIdx in
        completedSets.keys.contains("\(exercise.id)_\(setIdx)") ||
        (exercise.isEffectivelyUnilateral() &&
         completedSets.keys.contains("\(exercise.id)_\(setIdx)_L") &&
         completedSets.keys.contains("\(exercise.id)_\(setIdx)_R"))
    }
    return isComplete ? exercise : nil
}

private func nextSupersetHop(
    _ exercises: [Exercise],
    currentIdx: Int,
    currentSetIdx: Int
) -> (Int, Int)? {
    guard let groupId = exercises[safe: currentIdx]?.supersetGroupRefOrLegacyId() else { return nil }
    let group = exercises.indices.filter { exercises[$0].supersetGroupRefOrLegacyId() == groupId }
    guard group.count > 1 else { return nil }
    guard let position = group.firstIndex(of: currentIdx) else { return nil }
    for groupPos in (position + 1)..<group.count {
        let candidateIdx = group[groupPos]
        if exercises[candidateIdx].sets.indices.contains(currentSetIdx) {
            return (candidateIdx, currentSetIdx)
        }
    }
    return nil
}

private extension Session {
    func partForExercise(_ exerciseId: String) -> SessionPart? {
        parts.first { $0.exercises.contains { $0.id == exerciseId } }
    }

    func visibleParts(_ visibleExercises: [Exercise]) -> [SessionPart] {
        let visibleIds = Set(visibleExercises.map { $0.id })
        return parts.filter { $0.exercises.contains { visibleIds.contains($0.id) } }
    }
}
