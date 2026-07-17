import SwiftUI

/// Replicates the EXACT horizontal 3-ring layout from Android's HomeRingsSection.kt
public struct MyRingsView: View {
    var muscularProgress: Double
    var cnsProgress: Double
    var spinalProgress: Double
    
    // Android Colors
    let colorMuscular = Color(red: 1.0, green: 0.32, blue: 0.32) // 0xFFFF5252
    let colorSNC = Color(red: 0.26, green: 0.54, blue: 1.0)      // 0xFF448AFF
    let colorSpinal = Color(red: 1.0, green: 0.84, blue: 0.25)   // 0xFFFFD740
    
    public init(muscularProgress: Double, cnsProgress: Double, spinalProgress: Double) {
        self.muscularProgress = muscularProgress
        self.cnsProgress = cnsProgress
        self.spinalProgress = spinalProgress
    }
    
    public var body: some View {
        VStack(spacing: 8) {
            // Header
            HStack {
                Text("MIS RINGS")
                    .font(.title2)
                    .fontWeight(.black)
                    .tracking(1)
                    .foregroundColor(AppColors.textPrimary)
                
                Spacer()
                
                Text("¿Qué es esto?")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(AppColors.neonCyan)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 12)
            
            // 3 Rings Side-by-Side
            HStack(spacing: 24) {
                SingleRingColumn(
                    title: "MÚSCULOS",
                    progress: muscularProgress,
                    color: colorMuscular
                )
                
                SingleRingColumn(
                    title: "ENERGÍA",
                    progress: cnsProgress,
                    color: colorSNC
                )
                
                SingleRingColumn(
                    title: "COLUMNA",
                    progress: spinalProgress,
                    color: colorSpinal
                )
            }
            .padding(.horizontal, 16)
        }
    }
}

fileprivate struct SingleRingColumn: View {
    var title: String
    var progress: Double
    var color: Color
    
    @State private var animatedProgress: Double = 0
    let ringDiameter: CGFloat = 80
    let strokeWidth: CGFloat = 8
    
    var body: some View {
        VStack(spacing: 12) {
            // Semi-curved or top label
            Text(title)
                .font(.system(size: 10, weight: .bold, design: .rounded))
                .foregroundColor(color)
                .tracking(1)
            
            // Ring Canvas
            ZStack {
                Circle()
                    .stroke(color.opacity(0.15), style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round))
                    .frame(width: ringDiameter, height: ringDiameter)
                
                Circle()
                    .trim(from: 0, to: animatedProgress)
                    .stroke(color, style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: ringDiameter, height: ringDiameter)
                    // Added a subtle neon glow to maintain the premium Liquid Glass theme
                    .shadow(color: color.opacity(0.6), radius: 6, x: 0, y: 0)
            }
            .onAppear {
                withAnimation(.spring(response: 1.0, dampingFraction: 0.8, blendDuration: 0).delay(0.2)) {
                    animatedProgress = progress
                }
            }
            
            // Percentage Label
            Text("\(Int(progress * 100))%")
                .font(.system(size: 14, weight: .black, design: .rounded))
                .foregroundColor(color)
        }
    }
}

#Preview {
    ZStack {
        AppColors.bgDeepBlack.ignoresSafeArea()
        MyRingsView(muscularProgress: 0.88, cnsProgress: 0.65, spinalProgress: 0.40)
            .padding()
            .liquidGlass(cornerRadius: 24, borderOpacity: 0.3)
    }
}
