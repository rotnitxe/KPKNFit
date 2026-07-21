import SwiftUI

private enum VisualPattern: String {
    case HINGE, SQUAT, HORIZONTAL_PUSH, HORIZONTAL_PULL, VERTICAL_PUSH, VERTICAL_PULL, LUNGE, ROTATION, CARRY, GENERIC
}

private enum Segment: String {
    case TORSO, UPPER_ARM, FOREARM, THIGH, SHIN, CORE
}

private enum BiomechPlane: String, CaseIterable {
    case SAGITTAL
    case FRONTAL
    case TRANSVERSE
    
    var label: String {
        switch self {
        case .SAGITTAL: return "Sagital"
        case .FRONTAL: return "Frontal"
        case .TRANSVERSE: return "Transversal"
        }
    }
}

private enum AnimSpeed: String, CaseIterable {
    case SLOW, NORMAL, FAST
    
    var label: String {
        switch self {
        case .SLOW: return "Lento"
        case .NORMAL: return "Normal"
        case .FAST: return "Rápido"
        }
    }
    
    var durationMs: Int {
        switch self {
        case .SLOW: return 3900
        case .NORMAL: return 2600
        case .FAST: return 1700
        }
    }
}

private enum VisualLegend: String, CaseIterable {
    case ALL, GRAVITY, GROUND_REACTION, TORQUE, LEVER_ARM
    
    var label: String {
        switch self {
        case .ALL: return "Todo"
        case .GRAVITY: return "Gravedad"
        case .GROUND_REACTION: return "Reacción"
        case .TORQUE: return "Torque"
        case .LEVER_ARM: return "Palanca"
        }
    }
    
    var description: String {
        switch self {
        case .ALL: return "Muestra todos los vectores y referencias mecánicas al mismo tiempo."
        case .GRAVITY: return "Flecha roja: dirección de la carga hacia abajo sobre el cuerpo."
        case .GROUND_REACTION: return "Flecha verde: fuerza que devuelve el suelo desde el apoyo."
        case .TORQUE: return "Flecha turquesa: momento rotacional dominante del gesto."
        case .LEVER_ARM: return "Línea punteada: brazo de palanca entre el eje y la carga."
        }
    }
    
    var color: Color {
        switch self {
        case .ALL: return Color(hex: 0x455A64)
        case .GRAVITY: return Color(hex: 0xE53935)
        case .GROUND_REACTION: return Color(hex: 0x43A047)
        case .TORQUE: return Color(hex: 0x00A6A6)
        case .LEVER_ARM: return Color(hex: 0x5E35B1)
        }
    }
}

private struct Pose {
    let torsoDeg: Float
    let upperArmDeg: Float
    let foreArmDeg: Float
    let thighDeg: Float
    let shinDeg: Float
    init(_ torsoDeg: Float, _ upperArmDeg: Float, _ foreArmDeg: Float, _ thighDeg: Float, _ shinDeg: Float) {
        self.torsoDeg = torsoDeg; self.upperArmDeg = upperArmDeg; self.foreArmDeg = foreArmDeg
        self.thighDeg = thighDeg; self.shinDeg = shinDeg
    }
}

private struct VisualSpec {
    let pattern: VisualPattern
    let accent: Color
    let title: String
    let subtitle: String
    let highlight: Set<Segment>
}

internal func ExerciseBiomechVisual(exercise: ExerciseMuscleInfo) -> some View {
    let spec = specForExercise(exercise)
    return BiomechVisualCardView(spec: spec)
}

internal func MuscleBiomechVisual(muscleId: String, muscleName: String, color: Color) -> some View {
    let spec = specForMuscle(muscleId, muscleName, color)
    return BiomechVisualCardView(spec: spec)
}

internal func JointBiomechVisual(jointType: String, jointName: String? = nil) -> some View {
    let spec = specForJoint(jointType, jointName)
    return BiomechVisualCardView(spec: spec)
}

internal func PatternBiomechVisual(patternId: String, patternName: String) -> some View {
    let spec = specForPattern(patternId, patternName)
    return BiomechVisualCardView(spec: spec)
}

private struct BiomechVisualCardView: View {
    let spec: VisualSpec
    
    @State private var plane: BiomechPlane = .SAGITTAL
    @State private var speed: AnimSpeed = .NORMAL
    @State private var activeLegend: VisualLegend = .ALL
    @State private var tooltipLegend: VisualLegend? = nil
    
    @State private var phase: Float = 0
    @State private var pulse: Float = 0.5
    @State private var timer: Timer? = nil
    @State private var lastUpdate: Date = Date()
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(spec.title)
                .font(.system(size: 14, weight: .black))
                .foregroundColor(.white)
            
            Text(spec.subtitle)
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.7))
            
            HStack {
                HStack(spacing: 6) {
                    ForEach(BiomechPlane.allCases, id: \.rawValue) { option in
                        Button(action: { plane = option }) {
                            Text(option.label)
                                .font(.system(size: 11))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(plane == option ? Color.white.opacity(0.15) : Color.clear)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(0.2), lineWidth: 1))
                                .clipShape(RoundedRectangle(cornerRadius: 4))
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(.white)
                    }
                }
                Spacer()
                HStack(spacing: 6) {
                    ForEach(AnimSpeed.allCases, id: \.rawValue) { option in
                        Button(action: { speed = option }) {
                            Text(option.label)
                                .font(.system(size: 11))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(speed == option ? Color.white.opacity(0.15) : Color.clear)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(0.2), lineWidth: 1))
                                .clipShape(RoundedRectangle(cornerRadius: 4))
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(.white)
                    }
                }
            }
            
            BiomechCanvasView(spec: spec, phase: $phase, pulse: $pulse, plane: plane, legend: activeLegend)
                .frame(height: 230)
                .clipShape(RoundedRectangle(cornerRadius: 14))
            
            VStack(alignment: .leading, spacing: 8) {
                Text("Leyenda interactiva")
                    .font(.system(size: 14, weight: .bold))
                
                FlowLayout(spacing: 6) {
                    ForEach(VisualLegend.allCases, id: \.rawValue) { item in
                        Button(action: { activeLegend = item }) {
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(item.color)
                                    .frame(width: 10, height: 10)
                                Text(item.label)
                                    .font(.system(size: 11))
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(activeLegend == item ? Color.white.opacity(0.15) : Color.clear)
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(0.2), lineWidth: 1))
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(.white)
                        .onLongPressGesture(minimumDuration: 0.5) {
                            tooltipLegend = item
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) {
                                tooltipLegend = nil
                            }
                        }
                    }
                }
                
                if let tip = tooltipLegend {
                    Text(tip.description)
                        .font(.system(size: 11))
                        .foregroundColor(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .background(Color(hex: 0x303030))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                
                Text(activeLegend.description)
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.7))
            }
            .padding(10)
            .background(Color(hex: 0x1A1A1A))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .padding(14)
        .background(Color(hex: 0x141414))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .onAppear { startAnimation() }
        .onDisappear { timer?.invalidate() }
        .onChange(of: speed) { _ in startAnimation() }
    }
    
    private func startAnimation() {
        timer?.invalidate()
        phase = 0
        pulse = 0.5
        lastUpdate = Date()
        timer = Timer.scheduledTimer(withTimeInterval: 0.016, repeats: true) { _ in
            let dt = Float(Date().timeIntervalSince(lastUpdate))
            lastUpdate = Date()
            let speedFactor = 1000.0 / Float(speed.durationMs)
            phase = (phase + dt * speedFactor * 0.6).truncatingRemainder(dividingBy: 1.0)
            pulse = 0.5 + 0.5 * sin(Float(Date().timeIntervalSince1970) * 2.0 * Float.pi / (Float(speed.durationMs) * 0.00072))
        }
    }
}

private struct BiomechCanvasView: View {
    let spec: VisualSpec
    @Binding var phase: Float
    @Binding var pulse: Float
    let plane: BiomechPlane
    let legend: VisualLegend
    
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                drawBiomechScene(context: &context, size: size, spec: spec, phase: phase, pulse: pulse, plane: plane, legend: legend)
            }
            .background(
                LinearGradient(
                    colors: [spec.accent.opacity(0.14), Color.black.opacity(0.9)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
    }
}

private func drawBiomechScene(context: inout GraphicsContext, size: CGSize, spec: VisualSpec, phase: Float, pulse: Float, plane: BiomechPlane, legend: VisualLegend) {
    let neutral = Color(hex: 0x90A4AE)
    let accent = spec.accent
    let floorY = size.height * 0.84
    let baseCx = size.width * 0.5
    
    let pose = interpolatedPose(spec.pattern, phase)
    
    let xOffset: Float = {
        switch plane {
        case .SAGITTAL: return 0
        case .FRONTAL: return sin(phase * Float.pi * 2.0) * 18
        case .TRANSVERSE: return cos(phase * Float.pi * 2.0) * 22
        }
    }()
    let cx = Float(baseCx) + xOffset
    
    let hip = CGPoint(x: CGFloat(cx), y: floorY - size.height * 0.24)
    let shoulder = pointFrom(hip, size.height * 0.26, CGFloat(pose.torsoDeg))
    let head = pointFrom(shoulder, size.height * 0.10, CGFloat(pose.torsoDeg))
    
    let elbowFront = pointFrom(shoulder, size.height * 0.18, CGFloat(pose.upperArmDeg))
    let wristFront = pointFrom(elbowFront, size.height * 0.16, CGFloat(pose.foreArmDeg))
    let elbowBack = pointFrom(shoulder, size.height * 0.16, CGFloat(pose.upperArmDeg - 18))
    let wristBack = pointFrom(elbowBack, size.height * 0.14, CGFloat(pose.foreArmDeg - 16))
    
    let kneeFront = pointFrom(hip, size.height * 0.24, CGFloat(pose.thighDeg))
    let ankleFront = pointFrom(kneeFront, size.height * 0.21, CGFloat(pose.shinDeg))
    let kneeBack = pointFrom(hip, size.height * 0.23, CGFloat(pose.thighDeg + 12))
    let ankleBack = pointFrom(kneeBack, size.height * 0.20, CGFloat(pose.shinDeg + 10))
    
    // Background gradient
    var bgRect = Path(CGRect(origin: .zero, size: size))
    context.fill(bgRect, with: .linearGradient(
        Gradient(colors: [.clear, accent.opacity(0.06)]),
        startPoint: CGPoint(x: 0, y: size.height * 0.5),
        endPoint: CGPoint(x: 0, y: size.height)
    ))
    
    // Floor line
    var floorPath = Path()
    floorPath.move(to: CGPoint(x: 0, y: floorY))
    floorPath.addLine(to: CGPoint(x: size.width, y: floorY))
    context.stroke(floorPath, with: .color(neutral.opacity(0.45)), style: StrokeStyle(
        lineWidth: 2.5,
        dash: [16, 10],
        dashPhase: CGFloat(phase) * 60
    ))
    
    // Shadow segments (back)
    drawSegment(context: &context, from: shoulder, to: elbowBack, segment: .UPPER_ARM, spec: spec, accent: accent, neutral: neutral, shadow: true)
    drawSegment(context: &context, from: elbowBack, to: wristBack, segment: .FOREARM, spec: spec, accent: accent, neutral: neutral, shadow: true)
    drawSegment(context: &context, from: hip, to: kneeBack, segment: .THIGH, spec: spec, accent: accent, neutral: neutral, shadow: true)
    drawSegment(context: &context, from: kneeBack, to: ankleBack, segment: .SHIN, spec: spec, accent: accent, neutral: neutral, shadow: true)
    
    // Front segments
    drawSegment(context: &context, from: hip, to: shoulder, segment: .TORSO, spec: spec, accent: accent, neutral: neutral)
    drawSegment(context: &context, from: shoulder, to: elbowFront, segment: .UPPER_ARM, spec: spec, accent: accent, neutral: neutral)
    drawSegment(context: &context, from: elbowFront, to: wristFront, segment: .FOREARM, spec: spec, accent: accent, neutral: neutral)
    drawSegment(context: &context, from: hip, to: kneeFront, segment: .THIGH, spec: spec, accent: accent, neutral: neutral)
    drawSegment(context: &context, from: kneeFront, to: ankleFront, segment: .SHIN, spec: spec, accent: accent, neutral: neutral)
    
    // Core highlight
    if spec.highlight.contains(.CORE) {
        let coreCenter = CGPoint(x: (hip.x + shoulder.x) / 2, y: (hip.y + shoulder.y) / 2)
        let coreRadius = size.height * 0.07
        context.fill(Path(ellipseIn: CGRect(x: coreCenter.x - coreRadius, y: coreCenter.y - coreRadius, width: coreRadius * 2, height: coreRadius * 2)),
                     with: .color(accent.opacity(0.12 + 0.12 * CGFloat(pulse))))
    }
    
    // Head
    context.fill(Path(ellipseIn: CGRect(x: head.x - size.height * 0.05, y: head.y - size.height * 0.05, width: size.height * 0.10, height: size.height * 0.10)),
                 with: .color(Color(hex: 0x37474F)))
    context.fill(Path(ellipseIn: CGRect(x: head.x + 6 - size.height * 0.018, y: head.y - 4 - size.height * 0.018, width: size.height * 0.036, height: size.height * 0.036)),
                 with: .color(.white.opacity(0.12)))
    
    // Joints
    drawJoint(context: &context, center: shoulder, accent: accent, pulse: pulse, active: spec.highlight.contains(.TORSO))
    drawJoint(context: &context, center: elbowFront, accent: accent, pulse: pulse, active: spec.highlight.contains(.UPPER_ARM))
    drawJoint(context: &context, center: wristFront, accent: accent, pulse: pulse, active: spec.highlight.contains(.FOREARM))
    drawJoint(context: &context, center: hip, accent: accent, pulse: pulse, active: spec.highlight.contains(.CORE) || spec.highlight.contains(.TORSO))
    drawJoint(context: &context, center: kneeFront, accent: accent, pulse: pulse, active: spec.highlight.contains(.THIGH))
    drawJoint(context: &context, center: ankleFront, accent: accent, pulse: pulse, active: spec.highlight.contains(.SHIN))
    
    // Motion cues
    drawMotionCue(context: &context, pattern: spec.pattern, shoulder: shoulder, hip: hip, knee: kneeFront, accent: accent, phase: phase, pulse: pulse)
    
    // Force overlay
    drawForceOverlay(context: &context, pattern: spec.pattern, shoulder: shoulder, hip: hip, wrist: wristFront, ankle: ankleFront, accent: accent, phase: phase, plane: plane, legend: legend)
    
    // Lever overlay
    drawLeverOverlay(context: &context, pattern: spec.pattern, shoulder: shoulder, hip: hip, knee: kneeFront, ankle: ankleFront, accent: accent, phase: phase, legend: legend)
    
    // Plane hint
    drawPlaneHint(context: &context, size: size, plane: plane, accent: accent)
}

private func drawSegment(context: inout GraphicsContext, from: CGPoint, to: CGPoint, segment: Segment, spec: VisualSpec, accent: Color, neutral: Color, shadow: Bool = false) {
    let highlighted = spec.highlight.contains(segment)
    let color: Color = {
        if shadow { return neutral.opacity(0.34) }
        if highlighted { return accent }
        return neutral.opacity(0.75)
    }()
    let width: CGFloat = {
        if shadow { return 7 }
        if highlighted { return 10 }
        return 8
    }()
    
    if highlighted && !shadow {
        var glowPath = Path()
        glowPath.move(to: from)
        glowPath.addLine(to: to)
        context.stroke(glowPath, with: .color(accent.opacity(0.20)), style: StrokeStyle(lineWidth: width + 10, lineCap: .round))
    }
    
    var linePath = Path()
    linePath.move(to: from)
    linePath.addLine(to: to)
    context.stroke(linePath, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
}

private func drawJoint(context: inout GraphicsContext, center: CGPoint, accent: Color, pulse: Float, active: Bool) {
    let jointRadius: CGFloat = 8
    context.fill(Path(ellipseIn: CGRect(x: center.x - jointRadius, y: center.y - jointRadius, width: jointRadius * 2, height: jointRadius * 2)),
                 with: .color(Color(hex: 0x263238)))
    if active {
        let glowRadius: CGFloat = 12
        context.stroke(Path(ellipseIn: CGRect(x: center.x - glowRadius, y: center.y - glowRadius, width: glowRadius * 2, height: glowRadius * 2)),
                       with: .color(accent.opacity(0.35 + 0.20 * CGFloat(pulse))),
                       style: StrokeStyle(lineWidth: 3))
    }
}

private func drawMotionCue(context: inout GraphicsContext, pattern: VisualPattern, shoulder: CGPoint, hip: CGPoint, knee: CGPoint, accent: Color, phase: Float, pulse: Float) {
    func cueArc(center: CGPoint, radius: CGFloat, startDeg: CGFloat, sweep: CGFloat) {
        let startRad = startDeg * .pi / 180
        let endRad = (startDeg + sweep) * .pi / 180
        
        var arcPath = Path()
        arcPath.addArc(center: center, radius: radius, startAngle: Angle(radians: Double(startRad)), endAngle: Angle(radians: Double(endRad)), clockwise: sweep < 0)
        context.stroke(arcPath, with: .color(accent.opacity(0.45)), style: StrokeStyle(
            lineWidth: 3,
            dash: [10, 8],
            dashPhase: CGFloat(phase) * 60
        ))
        
        let dotAngle = (startDeg + sweep * CGFloat(phase)) * .pi / 180
        let dot = CGPoint(x: center.x + cos(dotAngle) * radius, y: center.y + sin(dotAngle) * radius)
        context.fill(Path(ellipseIn: CGRect(x: dot.x - 5, y: dot.y - 5, width: 10, height: 10)),
                     with: .color(accent.opacity(0.6 + 0.3 * CGFloat(pulse))))
    }
    
    switch pattern {
    case .HORIZONTAL_PUSH: cueArc(center: shoulder, radius: 46, startDeg: -35, sweep: 80)
    case .HORIZONTAL_PULL: cueArc(center: shoulder, radius: 46, startDeg: 160, sweep: -85)
    case .VERTICAL_PUSH: cueArc(center: shoulder, radius: 42, startDeg: -120, sweep: 65)
    case .VERTICAL_PULL: cueArc(center: shoulder, radius: 42, startDeg: -45, sweep: -65)
    case .HINGE: cueArc(center: hip, radius: 52, startDeg: -130, sweep: 70)
    case .SQUAT:
        cueArc(center: hip, radius: 50, startDeg: 210, sweep: 85)
        cueArc(center: knee, radius: 42, startDeg: 230, sweep: 72)
    case .LUNGE:
        cueArc(center: hip, radius: 50, startDeg: 210, sweep: 80)
        cueArc(center: knee, radius: 44, startDeg: 220, sweep: 70)
    case .ROTATION: cueArc(center: CGPoint(x: (shoulder.x + hip.x) / 2, y: (shoulder.y + hip.y) / 2), radius: 36, startDeg: 30, sweep: 300)
    case .CARRY: cueArc(center: shoulder, radius: 26, startDeg: 70, sweep: -140)
    case .GENERIC: cueArc(center: hip, radius: 46, startDeg: 220, sweep: 80)
    }
}

private func drawForceOverlay(context: inout GraphicsContext, pattern: VisualPattern, shoulder: CGPoint, hip: CGPoint, wrist: CGPoint, ankle: CGPoint, accent: Color, phase: Float, plane: BiomechPlane, legend: VisualLegend) {
    let gravitySelected = legend == .ALL || legend == .GRAVITY
    let reactionSelected = legend == .ALL || legend == .GROUND_REACTION
    let torqueSelected = legend == .ALL || legend == .TORQUE
    
    let gravityColor = Color(hex: 0xE53935).opacity(gravitySelected ? 0.82 : 0.18)
    let reactionColor = Color(hex: 0x43A047).opacity(reactionSelected ? 0.82 : 0.18)
    let torqueColor = accent.opacity(torqueSelected ? 0.88 : 0.18)
    
    func arrow(from: CGPoint, to: CGPoint, color: Color, dashed: Bool = false) {
        var linePath = Path()
        linePath.move(to: from)
        linePath.addLine(to: to)
        context.stroke(linePath, with: .color(color), style: StrokeStyle(
            lineWidth: 3.2, lineCap: .round,
            dash: dashed ? [10, 7] : [],
            dashPhase: dashed ? CGFloat(phase) * 40 : 0
        ))
        
        let vx = to.x - from.x
        let vy = to.y - from.y
        let len = sqrt(vx * vx + vy * vy)
        guard len > 1 else { return }
        let ux = vx / len
        let uy = vy / len
        let left = CGPoint(x: to.x - ux * 11 - uy * 6, y: to.y - uy * 11 + ux * 6)
        let right = CGPoint(x: to.x - ux * 11 + uy * 6, y: to.y - uy * 11 - ux * 6)
        var arrowPath = Path()
        arrowPath.move(to: to)
        arrowPath.addLine(to: left)
        arrowPath.move(to: to)
        arrowPath.addLine(to: right)
        context.stroke(arrowPath, with: .color(color), style: StrokeStyle(lineWidth: 3, lineCap: .round))
    }
    
    if gravitySelected || legend == .ALL {
        let centerX = (shoulder.x + hip.x) / 2
        let centerY = (shoulder.y + hip.y) / 2
        arrow(from: CGPoint(x: centerX, y: centerY - 26), to: CGPoint(x: centerX, y: centerY + 36), color: gravityColor)
    }
    
    if reactionSelected || legend == .ALL {
        arrow(from: CGPoint(x: ankle.x, y: ankle.y + 34), to: CGPoint(x: ankle.x, y: ankle.y - 24), color: reactionColor)
    }
    
    let torqueAnchor: CGPoint = {
        switch pattern {
        case .HORIZONTAL_PUSH, .HORIZONTAL_PULL, .VERTICAL_PUSH, .VERTICAL_PULL: return shoulder
        case .CARRY: return wrist
        default: return hip
        }
    }()
    
    let torqueLen: CGFloat = {
        switch plane {
        case .SAGITTAL: return 42
        case .FRONTAL: return 34
        case .TRANSVERSE: return 48
        }
    }()
    
    let torqueTo = CGPoint(
        x: torqueAnchor.x + torqueLen * cos(CGFloat(phase) * .pi * 2),
        y: torqueAnchor.y + torqueLen * sin(CGFloat(phase) * .pi * 2)
    )
    
    if torqueSelected || legend == .ALL {
        arrow(from: torqueAnchor, to: torqueTo, color: torqueColor, dashed: true)
    }
}

private func drawLeverOverlay(context: inout GraphicsContext, pattern: VisualPattern, shoulder: CGPoint, hip: CGPoint, knee: CGPoint, ankle: CGPoint, accent: Color, phase: Float, legend: VisualLegend) {
    let selected = legend == .ALL || legend == .LEVER_ARM
    guard selected else { return }
    
    let leverColor = accent.opacity(0.55)
    
    let base: CGPoint = {
        switch pattern {
        case .SQUAT, .LUNGE: return knee
        case .HINGE: return hip
        default: return shoulder
        }
    }()
    let load: CGPoint = {
        switch pattern {
        case .SQUAT, .LUNGE: return ankle
        case .HINGE: return shoulder
        default: return hip
        }
    }()
    
    var linePath = Path()
    linePath.move(to: base)
    linePath.addLine(to: load)
    context.stroke(linePath, with: .color(leverColor), style: StrokeStyle(
        lineWidth: 2.6,
        dash: [8, 8],
        dashPhase: CGFloat(phase) * 70
    ))
    
    let glowRadius: CGFloat = 12 + 5 * CGFloat(phase)
    context.stroke(Path(ellipseIn: CGRect(x: base.x - glowRadius, y: base.y - glowRadius, width: glowRadius * 2, height: glowRadius * 2)),
                   with: .color(leverColor.opacity(0.25)),
                   style: StrokeStyle(lineWidth: 2.5))
}

private func drawPlaneHint(context: inout GraphicsContext, size: CGSize, plane: BiomechPlane, accent: Color) {
    let x = size.width - 118
    let y: CGFloat = 18
    let bgRect = CGRect(x: x, y: y, width: 100, height: 46)
    context.fill(Path(roundedRect: bgRect, cornerRadius: 12), with: .color(Color(hex: 0x102027).opacity(0.15)))
    
    var linePath = Path()
    linePath.move(to: CGPoint(x: x + 14, y: y + 24))
    linePath.addLine(to: CGPoint(x: x + 86, y: y + 24))
    context.stroke(linePath, with: .color(accent.opacity(0.75)), style: StrokeStyle(lineWidth: 2.8))
    
    let dotX: CGFloat = {
        switch plane {
        case .SAGITTAL: return x + 28
        case .FRONTAL: return x + 50
        case .TRANSVERSE: return x + 72
        }
    }()
    context.fill(Path(ellipseIn: CGRect(x: dotX - 4.5, y: y + 24 - 4.5, width: 9, height: 9)), with: .color(accent))
}

private func pointFrom(_ origin: CGPoint, _ length: CGFloat, _ angleDeg: CGFloat) -> CGPoint {
    let rad = angleDeg * .pi / 180
    return CGPoint(x: origin.x + cos(rad) * length, y: origin.y + sin(rad) * length)
}

private func interpolatedPose(_ pattern: VisualPattern, _ t: Float) -> Pose {
    let a: Pose
    let b: Pose
    switch pattern {
    case .HORIZONTAL_PUSH: a = Pose(-86, -40, -12, 96, 88); b = Pose(-86, -8, 10, 98, 90)
    case .HORIZONTAL_PULL: a = Pose(-84, 6, -8, 96, 88); b = Pose(-84, -40, -35, 98, 90)
    case .VERTICAL_PUSH: a = Pose(-86, -86, -82, 98, 90); b = Pose(-86, -56, -48, 100, 92)
    case .VERTICAL_PULL: a = Pose(-84, -36, -22, 98, 90); b = Pose(-84, -76, -66, 100, 92)
    case .HINGE: a = Pose(-74, -20, 0, 120, 98); b = Pose(-48, -16, 4, 102, 88)
    case .SQUAT: a = Pose(-80, -20, -6, 112, 92); b = Pose(-60, -18, -4, 76, 56)
    case .LUNGE: a = Pose(-78, -22, -5, 112, 90); b = Pose(-62, -18, -3, 84, 60)
    case .ROTATION: a = Pose(-84, -24, -2, 102, 92); b = Pose(-78, -10, 18, 102, 92)
    case .CARRY: a = Pose(-86, -78, -82, 98, 88); b = Pose(-86, -76, -80, 108, 96)
    case .GENERIC: a = Pose(-84, -32, -12, 102, 90); b = Pose(-78, -16, -2, 94, 82)
    }
    return Pose(
        lerp(a.torsoDeg, b.torsoDeg, t),
        lerp(a.upperArmDeg, b.upperArmDeg, t),
        lerp(a.foreArmDeg, b.foreArmDeg, t),
        lerp(a.thighDeg, b.thighDeg, t),
        lerp(a.shinDeg, b.shinDeg, t)
    )
}

private func lerp(_ a: Float, _ b: Float, _ t: Float) -> Float { a + (b - a) * t }

// MARK: - Spec Factories

private func specForExercise(_ exercise: ExerciseMuscleInfo) -> VisualSpec {
    let name = exercise.name.lowercased()
    let force = exercise.force?.lowercased() ?? ""
    
    let pattern: VisualPattern
    let highlight: Set<Segment>
    let subtitle: String
    
    if name.contains("sentadilla") || name.contains("squat") {
        pattern = .SQUAT; highlight = [.CORE, .THIGH, .SHIN]; subtitle = "Modelo de flexión-extensión dominante con lectura de fuerza y palancas."
    } else if name.contains("peso muerto") || force.contains("bisagra") || name.contains("hinge") {
        pattern = .HINGE; highlight = [.TORSO, .CORE, .THIGH]; subtitle = "Modelo de bisagra de cadera y control espinal bajo carga."
    } else if force.contains("empuje") {
        pattern = .HORIZONTAL_PUSH; highlight = [.UPPER_ARM, .FOREARM, .CORE]; subtitle = "Modelo de empuje con transferencia desde tronco a extremidad superior."
    } else if force.contains("tir") {
        pattern = .HORIZONTAL_PULL; highlight = [.TORSO, .UPPER_ARM, .FOREARM]; subtitle = "Modelo de tracción con énfasis en cintura escapular."
    } else if name.contains("press militar") || name.contains("overhead") {
        pattern = .VERTICAL_PUSH; highlight = [.UPPER_ARM, .FOREARM, .CORE]; subtitle = "Modelo de empuje vertical y estabilidad central."
    } else if name.contains("dominada") || name.contains("jalon") || name.contains("pull") {
        pattern = .VERTICAL_PULL; highlight = [.TORSO, .UPPER_ARM, .FOREARM]; subtitle = "Modelo de tirón vertical con control escapular."
    } else if name.contains("zancada") || name.contains("lunge") {
        pattern = .LUNGE; highlight = [.CORE, .THIGH, .SHIN]; subtitle = "Modelo unilateral con transferencia de fuerza en apoyo asimétrico."
    } else {
        pattern = .GENERIC; highlight = [.CORE, .THIGH]; subtitle = "Modelo general del gesto dominante del ejercicio."
    }
    
    return VisualSpec(pattern: pattern, accent: Color(hex: 0x00A6A6), title: "Simulación mecánica", subtitle: subtitle, highlight: highlight)
}

private func specForMuscle(_ muscleId: String, _ muscleName: String, _ color: Color) -> VisualSpec {
    let id = muscleId.lowercased()
    let pattern: VisualPattern
    let highlight: Set<Segment>
    let subtitle: String
    
    if id.contains("pectoral") {
        pattern = .HORIZONTAL_PUSH; highlight = [.UPPER_ARM, .CORE]; subtitle = "Cómo participa este grupo muscular en un empuje horizontal."
    } else if id.contains("dorsal") || id.contains("espalda") || id.contains("trapecio") {
        pattern = .HORIZONTAL_PULL; highlight = [.TORSO, .UPPER_ARM, .FOREARM]; subtitle = "Cómo participa este grupo muscular en una tracción."
    } else if id.contains("deltoides") || id.contains("hombro") {
        pattern = .VERTICAL_PUSH; highlight = [.UPPER_ARM, .CORE]; subtitle = "Cómo se expresa este grupo en elevación y empuje vertical."
    } else if id.contains("glúte") || id.contains("isquio") || id.contains("erect") {
        pattern = .HINGE; highlight = [.CORE, .TORSO, .THIGH]; subtitle = "Cómo contribuye en bisagra de cadera y soporte posterior."
    } else if id.contains("cuádr") || id.contains("pantorr") || id.contains("pierna") {
        pattern = .SQUAT; highlight = [.THIGH, .SHIN, .CORE]; subtitle = "Cómo participa en patrones dominantes de rodilla."
    } else if id.contains("abdomen") || id.contains("core") {
        pattern = .ROTATION; highlight = [.CORE, .TORSO]; subtitle = "Cómo estabiliza y transmite fuerza en el tronco."
    } else {
        pattern = .GENERIC; highlight = [.CORE, .THIGH]; subtitle = "Patrón mecánico representativo de este grupo muscular."
    }
    
    return VisualSpec(pattern: pattern, accent: color, title: "Patrón biomecánico de \(muscleName)", subtitle: subtitle, highlight: highlight)
}

private func specForJoint(_ jointType: String, _ jointName: String?) -> VisualSpec {
    let pattern: VisualPattern
    let subtitle: String
    switch jointType.lowercased() {
    case "ball-socket": pattern = .ROTATION; subtitle = "Movilidad multiplanar, vectores de carga y control del centro articular."
    case "hinge": pattern = .HINGE; subtitle = "Movimiento de bisagra con lectura de palanca y línea de fuerza."
    default: pattern = .SQUAT; subtitle = "Flexión-extensión predominante con referencia de eje articular."
    }
    return VisualSpec(pattern: pattern, accent: Color(hex: 0x1E88E5),
                      title: "Mecánica de \(jointName ?? "la articulación")",
                      subtitle: subtitle, highlight: [.THIGH, .SHIN, .CORE])
}

private func specForPattern(_ patternId: String, _ patternName: String) -> VisualSpec {
    let id = patternId.lowercased()
    let pattern: VisualPattern
    let highlight: Set<Segment>
    let subtitle: String
    
    if id.contains("horizontal-push") {
        pattern = .HORIZONTAL_PUSH; highlight = [.UPPER_ARM, .FOREARM, .CORE]; subtitle = "Secuencia de empuje horizontal con lectura de torque y palanca."
    } else if id.contains("horizontal-pull") {
        pattern = .HORIZONTAL_PULL; highlight = [.TORSO, .UPPER_ARM, .FOREARM]; subtitle = "Secuencia de tracción horizontal con control escapular."
    } else if id.contains("vertical-push") {
        pattern = .VERTICAL_PUSH; highlight = [.UPPER_ARM, .FOREARM, .CORE]; subtitle = "Secuencia de empuje vertical y estabilidad del tronco."
    } else if id.contains("vertical-pull") {
        pattern = .VERTICAL_PULL; highlight = [.TORSO, .UPPER_ARM, .FOREARM]; subtitle = "Secuencia de tirón vertical con tracción dorsal."
    } else if id.contains("hinge") {
        pattern = .HINGE; highlight = [.TORSO, .CORE, .THIGH]; subtitle = "Secuencia de bisagra con dominancia de cadera."
    } else if id.contains("squat") {
        pattern = .SQUAT; highlight = [.THIGH, .SHIN, .CORE]; subtitle = "Secuencia dominante de rodilla con soporte del tronco."
    } else if id.contains("lunge") {
        pattern = .LUNGE; highlight = [.THIGH, .SHIN, .CORE]; subtitle = "Secuencia unilateral de estabilidad y producción de fuerza."
    } else if id.contains("rotation") || id.contains("anti-rotation") {
        pattern = .ROTATION; highlight = [.CORE, .TORSO]; subtitle = "Secuencia de rotación y control anti-rotacional del tronco."
    } else if id.contains("carry") {
        pattern = .CARRY; highlight = [.CORE, .TORSO, .UPPER_ARM]; subtitle = "Secuencia de marcha cargada con tensión global."
    } else {
        pattern = .GENERIC; highlight = [.CORE, .THIGH]; subtitle = "Secuencia mecánica general del patrón."
    }
    
    return VisualSpec(pattern: pattern, accent: Color(hex: 0x7E57C2),
                      title: "Simulación del patrón: \(patternName)",
                      subtitle: subtitle, highlight: highlight)
}

// MARK: - Flow Layout

private struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let width = proposal.width ?? .infinity
        var height: CGFloat = 0
        var x: CGFloat = 0
        var y: CGFloat = 0
        var maxHeight: CGFloat = 0
        
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > width {
                x = 0
                y += maxHeight + spacing
                maxHeight = 0
            }
            maxHeight = max(maxHeight, size.height)
            x += size.width + spacing
        }
        height = y + maxHeight
        return CGSize(width: width, height: height)
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var maxHeight: CGFloat = 0
        
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX {
                x = bounds.minX
                y += maxHeight + spacing
                maxHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            maxHeight = max(maxHeight, size.height)
            x += size.width + spacing
        }
    }
}
