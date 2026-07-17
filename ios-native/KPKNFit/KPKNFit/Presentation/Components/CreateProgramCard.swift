import SwiftUI

/// Swift translation of CreateProgramCard.kt
public struct CreateProgramCard: View {
    let onClick: () -> Void
    let primaryColor: Color = AppColors.neonYellow // Color.Yellow / Neon Yellow equivalent
    
    public init(onClick: @escaping () -> Void) {
        self.onClick = onClick
    }
    
    public var body: some View {
        Button(action: onClick) {
            ZStack {
                // Subtle radial glow effect in background
                Canvas { context, size in
                    let centerX = size.width / 2.0
                    let centerY = size.height / 2.0
                    let maxRadius = max(size.width, size.height) * 0.6
                    
                    let gradient = Gradient(colors: [
                        primaryColor.opacity(0.15),
                        .clear
                    ])
                    
                    context.fill(
                        Path(ellipseIn: CGRect(x: centerX - maxRadius, y: centerY - maxRadius, width: maxRadius * 2, height: maxRadius * 2)),
                        with: .radialGradient(gradient, center: CGPoint(x: centerX, y: centerY), startRadius: 0, endRadius: maxRadius)
                    )
                }
                .frame(height: 220)
                
                // Card content (Liquid Glass Effect)
                VStack(spacing: 16) {
                    Text("CREAR PROGRAMA")
                        .font(.system(size: 20, weight: .black)) // headlineSmall equivalent
                        .foregroundColor(primaryColor)
                        .tracking(2)
                    
                    Text("Para disfrutar de todas las funciones avanzadas de KPKN para planificar tu rutina, crear macrociclos enteros y funciones automáticas de sobrecarga progresiva crea tu primer programa de entrenamiento.")
                        .font(.system(size: 12)) // bodySmall equivalent
                        .multilineTextAlignment(.center)
                        .foregroundColor(Color.white.opacity(0.7))
                        .lineSpacing(4)
                }
                .padding(24)
                .frame(maxWidth: .infinity)
                .frame(height: 220)
                .background(
                    primaryColor.opacity(0.22)
                )
                .cornerRadius(28)
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        CreateProgramCard(onClick: {})
            .padding()
    }
}
