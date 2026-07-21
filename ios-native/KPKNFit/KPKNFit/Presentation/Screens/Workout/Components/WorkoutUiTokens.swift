import SwiftUI

enum WorkoutUiTokens {
    static let screenHorizontalPadding: CGFloat = 12
    static let cardShape = RoundedRectangle(cornerRadius: 28)
    static let innerCardShape = RoundedRectangle(cornerRadius: 20)
    static let chipShape = Capsule()
    static let dockShape = RoundedRectangle(cornerRadius: 28, style: .continuous)
    static let sectionGap: CGFloat = 12
    static let fieldGap: CGFloat = 8
    static let touchTargetMinSize: CGFloat = 48

    static func setCardColor() -> Color { Color(.systemGray6) }
    static func setInnerColor() -> Color { Color(.systemGray5) }
    static func setInnerHighestColor() -> Color { Color(.systemGray4) }
    static func dangerContainerColor() -> Color { Color.red.opacity(0.2) }
    static func successColor() -> Color { Color(red: 0.40, green: 0.73, blue: 0.42) }
    static func warningColor() -> Color { Color(red: 1.0, green: 0.84, blue: 0.25) }
}

struct WorkoutGlassSurface<Content: View>: View {
    var shape: AnyShape = AnyShape(WorkoutUiTokens.cardShape)
    var borderColor: Color = .white.opacity(0.08)
    @ViewBuilder let content: Content

    var body: some View {
        content
            .clipShape(shape)
            .background(Color(.systemGray6))
            .overlay(shape.stroke(borderColor, lineWidth: 1))
    }
}

struct WorkoutMetricChip: View {
    let label: String
    let value: String
    var containerColor: Color = Color(.systemGray4)
    var contentColor: Color = .white
    var badgeText: String? = nil
    var badgeColor: Color = .blue

    var body: some View {
        VStack(spacing: 2) {
            Text(label.uppercased())
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(contentColor.opacity(0.6))
            HStack(spacing: 4) {
                Text(value)
                    .font(.system(size: 17, weight: .black))
                    .foregroundColor(contentColor)
                if let badge = badgeText {
                    Text(badge)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(badgeColor)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(badgeColor.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .frame(minHeight: WorkoutUiTokens.touchTargetMinSize)
        .background(containerColor)
        .clipShape(WorkoutUiTokens.innerCardShape)
    }
}

struct WorkoutSectionTitle: View {
    let text: String
    var actionText: String? = nil
    var onActionClick: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(text)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white.opacity(0.9))
            Spacer()
            if let action = actionText, let onClick = onActionClick {
                Button(action: onClick) {
                    Text(action)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.blue)
                }
            }
        }
    }
}

struct WorkoutPrimaryActionButton: View {
    let text: String
    let onClick: () -> Void
    var enabled: Bool = true
    var containerColor: Color = .blue
    var contentColor: Color = .white
    var icon: String? = nil

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 8) {
                if let iconName = icon {
                    Image(systemName: iconName)
                }
                Text(text)
                    .font(.system(size: 17, weight: .black))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .frame(minHeight: 52)
            .background(enabled ? containerColor : Color(.systemGray4))
            .foregroundColor(enabled ? contentColor : .white.opacity(0.38))
            .clipShape(Capsule())
        }
        .disabled(!enabled)
    }
}

struct AnyShape: Shape {
    private let _path: (CGRect) -> Path

    init<S: Shape>(_ shape: S) {
        _path = { rect in shape.path(in: rect) }
    }

    func path(in rect: CGRect) -> Path {
        _path(rect)
    }
}
