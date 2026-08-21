import Foundation

/// Paridad de `BlockTransitionEngine.kt` — decisión al cerrar un bloque COMPLEX.
public enum BlockTransitionEngine {
    public enum DecisionKind: String {
        case advanceNextBlock = "ADVANCE_NEXT_BLOCK"
        case insertDeload = "INSERT_DELOAD"
        case propose1RMTest = "PROPOSE_1RM_TEST"
        case holdIncomplete = "HOLD_INCOMPLETE"
    }

    public struct TransitionContext {
        public var cumulativeFatigue: Double
        public var readinessScore: Int
        public var mesocycleStressEma: Double
        public var overtrainedMuscles: [String]
        public var augeAutoDeloadEnabled: Bool

        public init(
            cumulativeFatigue: Double = 0,
            readinessScore: Int = 70,
            mesocycleStressEma: Double = 0,
            overtrainedMuscles: [String] = [],
            augeAutoDeloadEnabled: Bool = false
        ) {
            self.cumulativeFatigue = cumulativeFatigue
            self.readinessScore = readinessScore
            self.mesocycleStressEma = mesocycleStressEma
            self.overtrainedMuscles = overtrainedMuscles
            self.augeAutoDeloadEnabled = augeAutoDeloadEnabled
        }
    }

    public struct TransitionDecision {
        public let kind: DecisionKind
        public let message: String
        public let nextBlockId: String?
        public let updatedProgram: Program?
    }

    public static func orderedBlocks(in program: Program) -> [Block] {
        program.macrocycles.flatMap { $0.blocks }
    }

    public static func evaluate(
        program: Program,
        completedBlockId: String,
        blockSessionsComplete: Bool,
        context: TransitionContext = TransitionContext()
    ) -> TransitionDecision {
        guard program.structure == .COMPLEX else {
            return TransitionDecision(
                kind: .holdIncomplete,
                message: "La transición por bloques solo aplica a programas COMPLEX.",
                nextBlockId: nil,
                updatedProgram: nil
            )
        }
        guard let block = orderedBlocks(in: program).first(where: { $0.id == completedBlockId }) else {
            return TransitionDecision(kind: .holdIncomplete, message: "Bloque no encontrado.", nextBlockId: nil, updatedProgram: nil)
        }
        guard blockSessionsComplete else {
            return TransitionDecision(
                kind: .holdIncomplete,
                message: "Hay sesiones incompletas en el bloque «\(block.name)».",
                nextBlockId: nil,
                updatedProgram: nil
            )
        }

        let blocks = orderedBlocks(in: program)
        let pos = blocks.firstIndex(where: { $0.id == completedBlockId }) ?? 0
        let next = pos + 1 < blocks.count ? blocks[pos + 1] : nil
        let goal = block.goal ?? .CUSTOM

        if goal == .REALIZATION || goal == .PEAK {
            return TransitionDecision(
                kind: .propose1RMTest,
                message: "Bloque de realización/pico completado. Propón un test de 1RM.",
                nextBlockId: next?.id,
                updatedProgram: nil
            )
        }

        let suggestDeload = (context.augeAutoDeloadEnabled && context.cumulativeFatigue > 75 && context.readinessScore < 40)
            || context.mesocycleStressEma >= 75
            || !context.overtrainedMuscles.isEmpty

        if suggestDeload && next?.goal != .DELOAD && next?.goal != .TAPER {
            return TransitionDecision(
                kind: .insertDeload,
                message: "Gate AUGE / estrés alto: insertar descarga antes de continuar.",
                nextBlockId: nil,
                updatedProgram: nil
            )
        }

        return TransitionDecision(
            kind: .advanceNextBlock,
            message: next == nil
                ? "Último bloque completado. Macrociclo finalizado."
                : "Avanzar a «\(next!.name)» con nueva prescripción.",
            nextBlockId: next?.id,
            updatedProgram: nil
        )
    }
}
