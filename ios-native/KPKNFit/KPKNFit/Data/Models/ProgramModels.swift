import Foundation

// ─── Helper: find session exercises ───────────────────────────────────────────

public func findSessionExercises(program: Program, sessionId: String) -> [Exercise] {
    for macro in program.macrocycles {
        for block in macro.blocks {
            for meso in block.mesocycles {
                for week in meso.weeks {
                    if let session = week.sessions.first(where: { $0.id == sessionId }) {
                        if !session.parts.isEmpty {
                            return session.parts.flatMap { $0.exercises }
                        } else {
                            return session.exercises
                        }
                    }
                }
            }
        }
    }
    return []
}
