import SwiftUI

struct ProtectedWorkoutBottomSheet<Content: View>: View {
    let title: String
    let onDismiss: () -> Void
    var showCloseButton: Bool = true
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text(title)
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(.white)
                Spacer()
                if showCloseButton {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .foregroundColor(.white)
                    }
                }
            }
            content
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
        .frame(maxHeight: .infinity, alignment: .top)
    }
}
