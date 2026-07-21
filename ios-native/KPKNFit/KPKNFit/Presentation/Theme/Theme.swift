import SwiftUI

enum AppThemeMode {
    case highContrast
}

private let highContrastColorScheme: (Color, Color, Color, Color, Color, Color, Color, Color, Color, Color, Color) = (
    Color.yellow,
    Color.cyan,
    Color.magenta,
    Color.black,
    Color.black,
    Color.black,
    Color.black,
    Color.black,
    Color.white,
    Color.white,
    Color(hex: 0x333333)
)

struct KPKNTheme: ViewModifier {
    let themeMode: AppThemeMode

    func body(content: Content) -> some View {
        content
            .preferredColorScheme(.dark)
    }
}

extension View {
    func kpknTheme(_ mode: AppThemeMode = .highContrast) -> some View {
        modifier(KPKNTheme(themeMode: mode))
    }
}
