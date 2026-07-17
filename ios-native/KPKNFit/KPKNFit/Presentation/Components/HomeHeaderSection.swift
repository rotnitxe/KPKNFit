import SwiftUI

/// Swift translation of HomeHeaderSection.kt
public struct HomeHeaderSection: View {
    let greeting: String
    let userName: String
    
    public init(greeting: String, userName: String) {
        self.greeting = greeting
        self.userName = userName
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("\(greeting),\n\(userName)!")
                .font(.system(size: 36, weight: .black)) // displaySmall equivalent
                .lineSpacing(4)
                .tracking(-1)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
        .padding(.top, 16)
        .padding(.bottom, 20)
    }
}

// ─── Header Icons ──────────────────────────────────────────────────────────

private struct ThreeRingsHeaderIcon: View {
    let colors = [
        Color(red: 1.0, green: 0.32, blue: 0.32), // 0xFFFF5252
        Color(red: 0.26, green: 0.54, blue: 1.0),  // 0xFF448AFF
        Color(red: 1.0, green: 0.84, blue: 0.25)   // 0xFFFFD740
    ]
    
    var body: some View {
        Canvas { context, size in
            let r = size.minDimension / 6.0
            let s = r * 1.6
            let cy = size.height / 2.0
            let cx = size.width / 2.0
            
            let centers = [
                CGPoint(x: cx - s, y: cy),
                CGPoint(x: cx, y: cy),
                CGPoint(x: cx + s, y: cy)
            ]
            
            for i in 0..<centers.count {
                let rect = CGRect(
                    x: centers[i].x - r,
                    y: centers[i].y - r,
                    width: r * 2.0,
                    height: r * 2.0
                )
                context.stroke(
                    Path(ellipseIn: rect),
                    with: .color(colors[i]),
                    lineWidth: 1.2
                )
            }
        }
    }
}

private struct SingleRingHeaderIcon: View {
    var body: some View {
        Canvas { context, size in
            let r = size.minDimension / 2.5
            let center = CGPoint(x: size.width / 2.0, y: size.height / 2.0)
            let rect = CGRect(
                x: center.x - r,
                y: center.y - r,
                width: r * 2.0,
                height: r * 2.0
            )
            context.stroke(
                Path(ellipseIn: rect),
                with: .color(Color(red: 0.26, green: 0.54, blue: 1.0)),
                lineWidth: 1.2
            )
        }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack {
            HomeHeaderSection(greeting: "Buenos días", userName: "Carlos")
            HStack(spacing: 20) {
                ThreeRingsHeaderIcon()
                    .frame(width: 48, height: 48)
                SingleRingHeaderIcon()
                    .frame(width: 48, height: 48)
            }
        }
    }
}
