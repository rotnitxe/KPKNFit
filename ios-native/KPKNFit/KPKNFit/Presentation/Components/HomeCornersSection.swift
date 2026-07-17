import SwiftUI

/// Swift translation of HomeCornersSection.kt
public struct HomeCornersSection: View {
    let onNavigate: (String) -> Void
    
    public init(onNavigate: @escaping (String) -> Void) {
        self.onNavigate = onNavigate
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader("Rincones")
            
            Spacer().frame(height: 4)
            
            // Powerlifter Corner
            CornerCard(
                title: "Powerlifter Corner",
                subtitle: "Federaciones, historial y competiciones.",
                icon: AnyView(
                    PowerlifterCornerIcon(
                        tint: Color.white.opacity(0.15),
                        size: 32
                    )
                ),
                onClick: { onNavigate("powerlifter-corner") }
            )
            
            Spacer().frame(height: 12)
            
            // Enciclopedia
            CornerCard(
                title: "Enciclopedia",
                subtitle: "Ciencia del entrenamiento y biomecánica.",
                icon: AnyView(
                    WikiIcon(
                        tint: Color.white.opacity(0.15),
                        size: 32
                    )
                ),
                onClick: { onNavigate("wiki-home") }
            )
        }
        .padding(.horizontal, 24)
    }
}

// ─── Corner Card ────────────────────────────────────────────────────────────

private struct CornerCard: View {
    let title: String
    let subtitle: String
    let icon: AnyView
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 16) {
                // Icon container
                Box {
                    Box {
                        icon
                    }
                    .frame(width: 64, height: 64)
                    .background(Color.white.opacity(0.08)) // surfaceVariant copy
                    .cornerRadius(20)
                }
                .frame(width: 64, height: 64)
                
                // Text content
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                    
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(Color.white.opacity(0.5))
                        .multilineTextAlignment(.leading)
                }
                
                Spacer()
                
                // Arrow forward
                Image(systemName: "arrow.right")
                    .foregroundColor(Color.white.opacity(0.3))
            }
            .padding(20)
            .background(Color.white.opacity(0.05)) // surfaceVariant copy with alpha 0.3
            .cornerRadius(28)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// Simple Box container helper
private struct Box<Content: View>: View {
    let content: Content
    
    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    var body: some View {
        ZStack {
            content
        }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        HomeCornersSection(onNavigate: { _ in })
    }
}
