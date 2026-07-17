import SwiftUI

/// A Liquid Glass (Glassmorphism) modifier.
/// Creates a frosted glass effect with a sublte glowing border, 
/// mimicking light refraction on the edge of the glass.
public struct LiquidGlassModifier: ViewModifier {
    var cornerRadius: CGFloat
    var borderOpacity: Double
    var shadowRadius: CGFloat
    
    public func body(content: Content) -> some View {
        content
            .background(
                .ultraThinMaterial,
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(
                        LinearGradient(
                            colors: [
                                AppColors.glassStrokeLight,
                                .clear,
                                AppColors.glassStrokeDark
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
                    .blendMode(.overlay)
            )
            .shadow(color: .black.opacity(0.3), radius: shadowRadius, x: 0, y: shadowRadius / 2)
    }
}

public extension View {
    /// Applies the Liquid Glass aesthetic to any view.
    func liquidGlass(cornerRadius: CGFloat = 24, borderOpacity: Double = 0.5, shadowRadius: CGFloat = 10) -> some View {
        self.modifier(LiquidGlassModifier(cornerRadius: cornerRadius, borderOpacity: borderOpacity, shadowRadius: shadowRadius))
    }
}
