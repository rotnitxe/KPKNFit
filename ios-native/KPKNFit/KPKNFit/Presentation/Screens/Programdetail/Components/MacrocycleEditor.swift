import SwiftUI

/// MVP del MacrocycleEditor (paridad Android `MacrocycleEditor` fachada).
/// Muestra bloques, goal/scheme y preview de progresión; la mutación completa
/// del `Program` queda en el ViewModel del detalle (mismo patrón que Android).
public struct MacrocycleEditor: View {
    public let program: Program
    public var onSelectBlockGoal: (String, BlockGoal) -> Void = { _, _ in }
    public var onFocusWeek: (String, String) -> Void = { _, _ in }

    public init(
        program: Program,
        onSelectBlockGoal: @escaping (String, BlockGoal) -> Void = { _, _ in },
        onFocusWeek: @escaping (String, String) -> Void = { _, _ in }
    ) {
        self.program = program
        self.onSelectBlockGoal = onSelectBlockGoal
        self.onFocusWeek = onFocusWeek
    }

    public var body: some View {
        List {
            ForEach(program.macrocycles, id: \.id) { macro in
                Section(macro.name) {
                    ForEach(macro.blocks, id: \.id) { block in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(block.name).font(.headline)
                            Text(block.goal?.label ?? block.mesocycles.first?.goal.label ?? "Sin objetivo")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let scheme = block.progressionScheme {
                                Text("Progresión: \(scheme.rawValue)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            if let preview = BlockProgressionEngine.previewDiff(
                                block: block,
                                weekFrom: max(1, (block.mesocycles.first?.weeks.count ?? 1) - 1),
                                weekTo: max(1, block.mesocycles.first?.weeks.count ?? 1)
                            ) {
                                Text("Al superar: \(preview.summary)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            HStack {
                                Button("Acum.") { onSelectBlockGoal(block.id, .ACCUMULATION) }
                                Button("Intens.") { onSelectBlockGoal(block.id, .INTENSIFICATION) }
                                Button("Deload") { onSelectBlockGoal(block.id, .DELOAD) }
                            }
                            .buttonStyle(.bordered)
                            if let firstWeek = block.mesocycles.first?.weeks.first {
                                Button("Ir a semana") {
                                    onFocusWeek(block.id, firstWeek.id)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Macrociclo")
    }
}
