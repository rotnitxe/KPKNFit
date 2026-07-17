import SwiftUI

/// An animated fluid gradient orb meant to sit *behind* the Liquid Glass cards.
/// This gives the frosted glass a vibrant, glowing surface to refract.
public struct NeonOrbView: View {
    let color: Color
    @State private var animate = false
    
    public init(color: Color) {
        self.color = color
    }
    
    public var body: some View {
        Circle()
            .fill(color)
            .blur(radius: 40)
            .scaleEffect(animate ? 1.2 : 0.8)
            .offset(x: animate ? 20 : -20, y: animate ? -30 : 30)
            .opacity(0.4)
            .onAppear {
                withAnimation(Animation.easeInOut(duration: 4.0).repeatForever(autoreverses: true)) {
                    animate.toggle()
                }
            }
    }
}
