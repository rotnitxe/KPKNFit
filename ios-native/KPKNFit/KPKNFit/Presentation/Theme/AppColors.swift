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

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex & 0xFF0000) >> 16) / 255.0
        let g = Double((hex & 0x00FF00) >> 8) / 255.0
        let b = Double(hex & 0x0000FF) / 255.0
        self.init(red: r, green: g, blue: b, opacity: alpha)
    }
    
    static func batteryColor(for score: Int) -> Color {
        if score >= 80 {
            return Color(hex: 0x22C55E)
        } else if score >= 50 {
            return Color(hex: 0xFACC15)
        } else {
            return Color(hex: 0xFFEF4444) // Wait, 0xFFEF4444 in hex is EF4444
        }
    }
}
