import SwiftUI

/// Swift translation of KpknIcons.kt custom canvas-drawn icons.
public struct DumbbellIcon: View {
    let tint: Color
    var size: CGFloat = 24
    
    public init(tint: Color, size: CGFloat = 24) {
        self.tint = tint
        self.size = size
    }
    
    public var body: some View {
        Canvas { context, size in
            let sw: CGFloat = 2.0
            let h = size.height / 2.0
            let w = size.width
            
            // Central bar
            context.stroke(
                Path { path in
                    path.move(to: CGPoint(x: 4, y: h))
                    path.addLine(to: CGPoint(x: w - 4, y: h))
                },
                with: .color(tint),
                lineWidth: sw
            )
            
            // Left weight
            let leftRect = CGRect(x: 0, y: h - 6, width: 4, height: 12)
            context.fill(
                Path(roundedRect: leftRect, cornerSize: CGSize(width: 1, height: 1)),
                with: .color(tint)
            )
            
            // Right weight
            let rightRect = CGRect(x: w - 4, y: h - 6, width: 4, height: 12)
            context.fill(
                Path(roundedRect: rightRect, cornerSize: CGSize(width: 1, height: 1)),
                with: .color(tint)
            )
        }
        .frame(width: size, height: size)
    }
}

public struct NutritionIcon: View {
    let tint: Color
    var size: CGFloat = 24
    
    public init(tint: Color, size: CGFloat = 24) {
        self.tint = tint
        self.size = size
    }
    
    public var body: some View {
        Canvas { context, size in
            // Apple body
            let applePath = Path { path in
                path.move(to: CGPoint(x: 12, y: 8.1))
                path.addCurve(
                    to: CGPoint(x: 6.1, y: 8.6),
                    control1: CGPoint(x: 10.5, y: 6.8),
                    control2: CGPoint(x: 7.7, y: 6.7)
                )
                path.addCurve(
                    to: CGPoint(x: 7.7, y: 20.0),
                    control1: CGPoint(x: 3.8, y: 11.2),
                    control2: CGPoint(x: 5.0, y: 17.5)
                )
                path.addCurve(
                    to: CGPoint(x: 12.0, y: 20.2),
                    control1: CGPoint(x: 9.0, y: 21.2),
                    control2: CGPoint(x: 10.4, y: 20.2)
                )
                path.addCurve(
                    to: CGPoint(x: 16.3, y: 20.0),
                    control1: CGPoint(x: 13.6, y: 20.2),
                    control2: CGPoint(x: 15.0, y: 21.2)
                )
                path.addCurve(
                    to: CGPoint(x: 17.9, y: 8.6),
                    control1: CGPoint(x: 19.0, y: 17.5),
                    control2: CGPoint(x: 20.2, y: 11.2)
                )
                path.addCurve(
                    to: CGPoint(x: 12, y: 8.1),
                    control1: CGPoint(x: 16.3, y: 6.7),
                    control2: CGPoint(x: 13.5, y: 6.8)
                )
                path.closeSubpath()
            }
            
            // Scale and draw path based on canvas size
            let scaleX = size.width / 24.0
            let scaleY = size.height / 24.0
            let scaledApple = applePath.applying(CGAffineTransform(scaleX: scaleX, y: scaleY))
            context.fill(scaledApple, with: .color(tint))
            
            // Stem
            let stemPath = Path { path in
                path.move(to: CGPoint(x: 12 * scaleX, y: 7.5 * scaleY))
                path.addLine(to: CGPoint(x: 13.3 * scaleX, y: 4.2 * scaleY))
            }
            context.stroke(
                stemPath,
                with: .color(tint),
                style: StrokeStyle(lineWidth: 1.7 * scaleX, lineCap: .round)
            )
            
            // Leaf
            let leafPath = Path { path in
                path.move(to: CGPoint(x: 14.2, y: 5.2))
                path.addCurve(
                    to: CGPoint(x: 19.5, y: 4.1),
                    control1: CGPoint(x: 15.5, y: 3.4),
                    control2: CGPoint(x: 18.1, y: 3.2)
                )
                path.addCurve(
                    to: CGPoint(x: 14.2, y: 5.2),
                    control1: CGPoint(x: 18.5, y: 5.9),
                    control2: CGPoint(x: 16.1, y: 6.9)
                )
                path.closeSubpath()
            }
            let scaledLeaf = leafPath.applying(CGAffineTransform(scaleX: scaleX, y: scaleY))
            context.fill(scaledLeaf, with: .color(tint))
        }
        .frame(width: size, height: size)
    }
}

public struct WikiIcon: View {
    let tint: Color
    var size: CGFloat = 24
    
    public init(tint: Color, size: CGFloat = 24) {
        self.tint = tint
        self.size = size
    }
    
    public var body: some View {
        Text("W")
            .font(.system(size: size * 0.8, weight: .black, design: .serif))
            .foregroundColor(tint)
            .frame(width: size, height: size)
    }
}

public struct IntertwinedRingsIcon: View {
    let tint: Color
    var size: CGFloat = 24
    
    public init(tint: Color, size: CGFloat = 24) {
        self.tint = tint
        self.size = size
    }
    
    public var body: some View {
        Canvas { context, size in
            let r = size.width / 5.0
            let center = CGPoint(x: size.width / 2.0, y: size.height / 2.0)
            let sw: CGFloat = 2.0
            
            // Left ring
            let leftRect = CGRect(x: center.x - r * 0.6 - r, y: center.y - r, width: r * 2, height: r * 2)
            context.stroke(Path(ellipseIn: leftRect), with: .color(tint), lineWidth: sw)
            
            // Right ring
            let rightRect = CGRect(x: center.x + r * 0.6 - r, y: center.y - r, width: r * 2, height: r * 2)
            context.stroke(Path(ellipseIn: rightRect), with: .color(tint), lineWidth: sw)
        }
        .frame(width: size, height: size)
    }
}

public struct SingleRingIconView: View {
    let tint: Color
    var size: CGFloat = 24
    
    public init(tint: Color, size: CGFloat = 24) {
        self.tint = tint
        self.size = size
    }
    
    public var body: some View {
        Canvas { context, size in
            let w = size.width
            let r = w / 2.0 - 3.0
            let center = CGPoint(x: w / 2.0, y: size.height / 2.0)
            
            // Outer ring
            let outerRect = CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)
            context.stroke(Path(ellipseIn: outerRect), with: .color(tint), lineWidth: 2.0)
            
            // Inner circle
            let innerR = r * 0.5
            let innerRect = CGRect(x: center.x - innerR, y: center.y - innerR, width: innerR * 2, height: innerR * 2)
            context.fill(Path(ellipseIn: innerRect), with: .color(tint))
        }
        .frame(width: size, height: size)
    }
}

public struct PowerlifterCornerIcon: View {
    let tint: Color
    var size: CGFloat = 24
    
    public init(tint: Color, size: CGFloat = 24) {
        self.tint = tint
        self.size = size
    }
    
    public var body: some View {
        Canvas { context, size in
            let r = min(size.width, size.height) / 2.0 - 1.0
            let center = CGPoint(x: size.width / 2.0, y: size.height / 2.0)
            
            let rect = CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)
            
            // Draw 280 degree arc representing a stylized "C"
            var path = Path()
            path.addArc(
                center: center,
                radius: r,
                startAngle: .degrees(40),
                endAngle: .degrees(320),
                clockwise: false
            )
            
            context.stroke(
                path,
                with: .color(tint),
                style: StrokeStyle(lineWidth: 2.5, lineCap: .round)
            )
        }
        .frame(width: size, height: size)
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        HStack(spacing: 20) {
            DumbbellIcon(tint: .white)
            NutritionIcon(tint: .white)
            WikiIcon(tint: .white)
            IntertwinedRingsIcon(tint: .white)
            SingleRingIconView(tint: .white)
            PowerlifterCornerIcon(tint: .white)
        }
    }
}
