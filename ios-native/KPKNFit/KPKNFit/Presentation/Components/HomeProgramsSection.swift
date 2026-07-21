import SwiftUI

private let CardDark = Color(hex: 0x1C1C1E)

/// Swift translation of HomeProgramsSection.kt
public struct HomeProgramsSection: View {
    let programs: [Program]
    let activeProgramId: String?
    let onProgramClick: (String) -> Void
    let onCreateProgram: () -> Void
    
    public init(
        programs: [Program],
        activeProgramId: String?,
        onProgramClick: @escaping (String) -> Void,
        onCreateProgram: @escaping () -> Void
    ) {
        self.programs = programs
        self.activeProgramId = activeProgramId
        self.onProgramClick = onProgramClick
        self.onCreateProgram = onCreateProgram
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader("Tus Programas")
                .padding(.horizontal, 24)
            
            if programs.isEmpty {
                EmptyProgramsCard(onClick: onCreateProgram)
                    .padding(.horizontal, 24)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(programs) { program in
                            ProgramCard(
                                program: program,
                                isActive: program.id == activeProgramId,
                                onClick: { onProgramClick(program.id) }
                            )
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }
        }
    }
}

// ─── Program Card ────────────────────────────────────────────────────────────

private struct ProgramCard: View {
    let program: Program
    let isActive: Bool
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 0) {
                ZStack {
                    // Linear Gradient cover background
                    LinearGradient(
                        colors: programCardCoverColors(program.coverImage),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    
                    // Dark overlay (0.38)
                    Color.black.opacity(0.38)
                    
                    // Card content
                    VStack(alignment: .leading, spacing: 0) {
                        // Top Row: Active Pill + Mode
                        HStack(alignment: .top) {
                            if isActive {
                                Text("ACTIVO")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(AppColors.neonYellow.opacity(0.92)) // primary color equivalent
                                    .cornerRadius(999)
                            }
                            
                            Spacer()
                            
                            Text(programModeLabel(program.mode))
                                .font(.system(size: 9))
                                .foregroundColor(Color.white.opacity(0.64))
                                .lineLimit(1)
                        }
                        
                        Spacer()
                        
                        // Bottom Column: Name + Weeks
                        VStack(alignment: .leading, spacing: 2) {
                            Text(program.name)
                                .font(.system(size: 14, weight: .black))
                                .foregroundColor(.white)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                            
                            Text("\(max(program.totalProgramWeeks, 1)) semanas")
                                .font(.system(size: 10))
                                .foregroundColor(Color.white.opacity(0.62))
                        }
                    }
                    .padding(14)
                }
                .frame(width: 176, height: 112)
                .cornerRadius(22)
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// Helper to determine cover colors
private func programCardCoverColors(_ coverValue: String?) -> [Color] {
    switch coverValue {
    case "gradient://lagoon":
        return [Color(hex: 0x0D1B2A), Color(hex: 0x1B4965), Color(hex: 0x5FA8D3)]
    case "gradient://velvet":
        return [Color(hex: 0x1C1024), Color(hex: 0x5B2A86), Color(hex: 0x5FA8D3)] // Note Velvet has different ending in original
    case "gradient://forest":
        return [Color(hex: 0x102A1F), Color(hex: 0x2D6A4F), Color(hex: 0x95D5B2)]
    default:
        return [Color(hex: 0x20110F), Color(hex: 0x8D3D2E), Color(hex: 0xE08E45)]
    }
}

private func programModeLabel(_ mode: ProgramMode) -> String {
    switch mode {
    case .POWERLIFTING: return "Powerlifting"
    case .POWERBUILDING: return "Powerbuilding"
    case .HYPERTROPHY: return "Hipertrofia"
    }
}

// ─── Empty Programs Card ──────────────────────────────────────────────────────

private struct EmptyProgramsCard: View {
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .center, spacing: 4) {
                Text("Crea tu primer programa")
                    .font(.body)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("La Home se llenará con tus sesiones y métricas cuando lo actives.")
                    .font(.caption)
                    .foregroundColor(Color.white.opacity(0.5))
                    .multilineTextAlignment(.center)
            }
            .padding(.vertical, 28)
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity, alignment: .center)
            .background(CardDark)
            .cornerRadius(22)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack(spacing: 20) {
            HomeProgramsSection(
                programs: [
                    Program(id: "1", name: "Ripped to Shreds", coverImage: "gradient://lagoon", mode: .HYPERTROPHY),
                    Program(id: "2", name: "Max Power 5x5", coverImage: "gradient://velvet", mode: .POWERLIFTING)
                ],
                activeProgramId: "1",
                onProgramClick: { _ in },
                onCreateProgram: {}
            )
            
            HomeProgramsSection(
                programs: [],
                activeProgramId: nil,
                onProgramClick: { _ in },
                onCreateProgram: {}
            )
        }
    }
}
