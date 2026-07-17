import SwiftUI

/// A component that draws three concentric progress rings representing the AUGE batteries.
public struct MyRingsView: View {
    // Progress values from 0.0 to 1.0
    var muscularProgress: Double
    var cnsProgress: Double
    var spinalProgress: Double
    
    // Ring configuration
    let lineWidth: CGFloat = 16
    let spacing: CGFloat = 6
    
    public init(muscularProgress: Double, cnsProgress: Double, spinalProgress: Double) {
        self.muscularProgress = muscularProgress
        self.cnsProgress = cnsProgress
        self.spinalProgress = spinalProgress
    }
    
    public var body: some View {
        ZStack {
            // Inner Ring: Spinal (Magenta)
            RingCircle(
                progress: spinalProgress,
                color: AppColors.neonMagenta,
                lineWidth: lineWidth,
                radiusOffset: (lineWidth * 2) + (spacing * 2)
            )
            
            // Middle Ring: CNS (Cyan)
            RingCircle(
                progress: cnsProgress,
                color: AppColors.neonCyan,
                lineWidth: lineWidth,
                radiusOffset: lineWidth + spacing
            )
            
            // Outer Ring: Muscular (Yellow)
            RingCircle(
                progress: muscularProgress,
                color: AppColors.neonYellow,
                lineWidth: lineWidth,
                radiusOffset: 0
            )
            
            // Center Content
            VStack(spacing: 2) {
                Text("\(Int((muscularProgress + cnsProgress + spinalProgress) / 3 * 100))%")
                    .font(.system(.largeTitle, design: .rounded))
                    .fontWeight(.black)
                    .foregroundColor(AppColors.textPrimary)
                
                Text("READY")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .tracking(3)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
        .padding(30) // Provide breathing room for the outer ring
    }
}

fileprivate struct RingCircle: View {
    var progress: Double
    var color: Color
    var lineWidth: CGFloat
    var radiusOffset: CGFloat
    
    @State private var animatedProgress: Double = 0
    
    var body: some View {
        ZStack {
            // Background track (darker, translucent)
            Circle()
                .stroke(Color.white.opacity(0.05), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .padding(radiusOffset)
            
            // Foreground progress (glow + solid color)
            Circle()
                .trim(from: 0, to: animatedProgress)
                .stroke(
                    color,
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .padding(radiusOffset)
                // Add a subtle glow matching the color
                .shadow(color: color.opacity(0.6), radius: 8, x: 0, y: 0)
        }
        .onAppear {
            withAnimation(.spring(response: 1.2, dampingFraction: 0.8, blendDuration: 0).delay(0.2)) {
                animatedProgress = progress
            }
        }
    }
}

#Preview {
    ZStack {
        AppColors.bgDeepBlack.ignoresSafeArea()
        MyRingsView(muscularProgress: 0.85, cnsProgress: 0.60, spinalProgress: 0.35)
            .frame(width: 280, height: 280)
            .liquidGlass(cornerRadius: 150) // Optional liquid glass circle backdrop
    }
}
