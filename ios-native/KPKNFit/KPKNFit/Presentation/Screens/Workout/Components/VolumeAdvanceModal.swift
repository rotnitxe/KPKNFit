import SwiftUI

struct VolumeAdvanceModal: View {
    let advances: [MuscleAdvance]
    let onAccept: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ProtectedWorkoutBottomSheet(title: "Adelanto de volumen", onDismiss: onDismiss, showCloseButton: false) {
            Text("Completaste más series de las planificadas. Puedes descontar ese volumen en la próxima sesión de la semana que use los mismos músculos.")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.78))

            ForEach(advances) { advance in
                let surplusSets = max(Int(advance.deficitSets.rounded()), 1)
                VStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(advance.muscleName.isEmpty ? advance.muscleId : advance.muscleName)
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(.white)
                        Text("+\(surplusSets) series extra en esta sesión")
                            .font(.system(size: 12))
                            .foregroundColor(Color(red: 0.49, green: 0.83, blue: 0.99))
                            .fontWeight(.bold)
                        Text("Próxima sesión: \(advance.targetSessionName)")
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.65))
                        ForEach(advance.discountProposals) { proposal in
                            let discount = max(Int(proposal.discountSets.rounded()), 1)
                            Text("• \(proposal.exerciseName): −\(discount) \(discount == 1 ? "serie" : "series")")
                                .font(.system(size: 12))
                                .foregroundColor(.white.opacity(0.85))
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(red: 0.165, green: 0.165, blue: 0.165))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }
            }

            Button(action: onAccept) {
                Text("Aplicar descuento")
                    .fontWeight(.black)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.blue)
            .clipShape(RoundedRectangle(cornerRadius: 14))

            Button(action: onDismiss) {
                Text("Omitir por ahora")
                    .foregroundColor(.white.opacity(0.85))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(.white.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
}
