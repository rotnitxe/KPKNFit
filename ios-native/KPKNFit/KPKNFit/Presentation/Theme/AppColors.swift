import SwiftUI

public enum AppColors {
    // Backgrounds
    public static let bgDeepBlack = Color(red: 0.03, green: 0.03, blue: 0.03)
    public static let bgAbsoluteBlack = Color.black
    
    // Neon Accents (High Contrast Palette)
    public static let neonYellow = Color(red: 0.88, green: 1.0, blue: 0.0) // E0FF00
    public static let neonCyan = Color(red: 0.0, green: 0.94, blue: 1.0) // 00F0FF
    public static let neonMagenta = Color(red: 1.0, green: 0.0, blue: 0.89) // FF00E3
    public static let neonOrange = Color(red: 1.0, green: 0.35, blue: 0.0) // FF5900
    
    // Glass Metrics
    public static let glassStrokeLight = Color.white.opacity(0.4)
    public static let glassStrokeDark = Color.white.opacity(0.1)
    
    // Typography
    public static let textPrimary = Color.white
    public static let textSecondary = Color.white.opacity(0.6)
}
