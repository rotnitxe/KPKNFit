import SwiftUI

// ─── SectionHeader ───────────────────────────────────────────────────────────

public struct SectionHeader: View {
    let title: String
    
    public init(_ title: String) {
        self.title = title
    }
    
    public var body: some View {
        Text(title.uppercased())
            .font(.system(size: 14, weight: .black)) // labelLarge equivalent
            .tracking(1)
            .padding(.bottom, 8)
            .foregroundColor(.white)
    }
}

// ─── SwipeToDeleteCard ───────────────────────────────────────────────────────

public struct SwipeToDeleteCard<Content: View>: View {
    let onDelete: () -> Void
    let content: Content
    
    @State private var offsetX: CGFloat = 0
    @State private var isDeleting = false
    
    let deleteThreshold: CGFloat = 80
    
    public init(onDelete: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.onDelete = onDelete
        self.content = content()
    }
    
    public var body: some View {
        ZStack(alignment: .trailing) {
            // Delete background (red background with trash icon)
            if offsetX < 0 {
                Rectangle()
                    .fill(Color.red)
                    .overlay(
                        HStack {
                            Spacer()
                            Image(systemName: "trash.fill")
                                .foregroundColor(.white)
                                .padding(.trailing, 24)
                        }
                    )
                    .cornerRadius(28)
            }
            
            // Foreground Content
            content
                .background(Color.black)
                .cornerRadius(28)
                .offset(x: offsetX)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            let translation = value.translation.width
                            // Only allow left swipe (negative translation)
                            if translation < 0 {
                                offsetX = max(translation, -120)
                            }
                        }
                        .onEnded { value in
                            if offsetX <= -deleteThreshold {
                                withAnimation(.easeOut) {
                                    offsetX = -300
                                }
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                    onDelete()
                                    offsetX = 0
                                }
                            } else {
                                withAnimation(.spring()) {
                                    offsetX = 0
                                }
                            }
                        }
                )
        }
        .clipShape(RoundedCornerShape(radius: 28))
    }
}

// Helper RoundedCornerShape
struct RoundedCornerShape: Shape {
    var radius: CGFloat = 28
    
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRoundedRect(in: rect, cornerSize: CGSize(width: radius, height: radius))
        return path
    }
}

// ─── EmptyStateView ──────────────────────────────────────────────────────────

public struct EmptyStateView: View {
    let title: String
    let subtitle: String
    var actionLabel: String? = nil
    var onAction: (() -> Void)? = nil
    
    public init(title: String, subtitle: String, actionLabel: String? = nil, onAction: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.actionLabel = actionLabel
        self.onAction = onAction
    }
    
    public var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.title3)
                .fontWeight(.black)
                .foregroundColor(.white)
            
            Text(subtitle)
                .font(.body)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.bottom, actionLabel != nil ? 16 : 0)
            
            if let actionLabel = actionLabel, let onAction = onAction {
                Button(action: onAction) {
                    Text(actionLabel.uppercased())
                        .font(.headline)
                        .fontWeight(.black)
                        .foregroundColor(.black)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white)
                        .cornerRadius(24)
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity, alignment: .center)
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack(spacing: 20) {
            SectionHeader("Mi Sección")
            
            SwipeToDeleteCard(onDelete: {}) {
                Text("Deslízame a la izquierda")
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color.gray.opacity(0.3))
            }
            
            EmptyStateView(
                title: "No hay datos",
                subtitle: "Prueba agregando algo nuevo",
                actionLabel: "Agregar",
                onAction: {}
            )
        }
        .padding()
    }
}
