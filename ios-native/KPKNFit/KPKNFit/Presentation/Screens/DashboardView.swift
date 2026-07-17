import SwiftUI

struct DashboardView: View {
    @State private var neuralBattery: Double = 82.0
    @State private var workoutTimeToday: String = "45 mins"
    
    // Sample muscle status
    let muscles = [
        MuscleStatus(name: "Pectorales", score: 90, icon: "bolt.fill", color: .green),
        MuscleStatus(name: "Cuádriceps", score: 45, icon: "exclamationmark.triangle.fill", color: .orange),
        MuscleStatus(name: "Bíceps", score: 85, icon: "bolt.fill", color: .green),
        MuscleStatus(name: "Lumbar", score: 20, icon: "xmark.octagon.fill", color: .red)
    ]
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Top Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("KPKN FIT")
                            .font(.system(.title3, design: .monospaced))
                            .fontWeight(.black)
                            .tracking(2)
                            .foregroundColor(.primary)
                        
                        Text("ATHLETE STATUS REPORT")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    
                    Image(systemName: "person.crop.circle.fill")
                        .resizable()
                        .frame(width: 36, height: 36)
                        .foregroundColor(.accentColor)
                }
                .padding(.horizontal)
                .padding(.top, 16)
                
                // Neural Battery Card (Glassmorphism look)
                VStack(spacing: 16) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("SNC Neural Battery")
                                .font(.headline)
                                .fontWeight(.bold)
                            Text("Systemic fatigue levels")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Text("\(Int(neuralBattery))%")
                            .font(.system(.title, design: .rounded))
                            .fontWeight(.black)
                            .foregroundColor(batteryColor(neuralBattery))
                    }
                    
                    // Battery Bar
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color(UIColor.secondarySystemFill))
                                .frame(height: 8)
                            Capsule()
                                .fill(
                                    LinearGradient(
                                        gradient: Gradient(colors: [batteryColor(neuralBattery), batteryColor(neuralBattery).opacity(0.6)]),
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .frame(width: geo.size.width * CGFloat(neuralBattery / 100.0), height: 8)
                        }
                    }
                    .frame(height: 8)
                    
                    HStack {
                        Label("Optimal Readiness", systemImage: "checkmark.circle.fill")
                            .font(.caption2)
                            .foregroundColor(.green)
                        Spacer()
                        Text("TTC: 4h 30m")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(16)
                .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 4)
                .padding(.horizontal)
                
                // Muscle Recovery Grid
                VStack(alignment: .leading, spacing: 12) {
                    Text("MUSCULAR RECOVERY STATUS")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.secondary)
                        .padding(.horizontal)
                    
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                        ForEach(muscles) { muscle in
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Image(systemName: muscle.icon)
                                        .foregroundColor(muscle.color)
                                    Spacer()
                                    Text("\(muscle.score)%")
                                        .font(.system(.subheadline, design: .rounded))
                                        .fontWeight(.bold)
                                        .foregroundColor(muscle.color)
                                }
                                
                                Text(muscle.name)
                                    .font(.subheadline)
                                    .fontWeight(.bold)
                                
                                Text(muscle.score >= 80 ? "Fresh" : muscle.score >= 40 ? "Recovering" : "Exhausted")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                            .padding()
                            .background(Color(UIColor.secondarySystemBackground))
                            .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal)
                }
                
                // Nutrition Macros Card
                VStack(alignment: .leading, spacing: 16) {
                    Text("NUTRITION MONITOR")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.secondary)
                    
                    HStack(spacing: 20) {
                        MacroRing(name: "Proteína", current: 120, target: 160, color: .blue)
                        MacroRing(name: "Carbos", current: 180, target: 240, color: .orange)
                        MacroRing(name: "Grasas", current: 55, target: 70, color: .yellow)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(16)
                .padding(.horizontal)
                
            }
            .padding(.bottom, 32)
        }
        .background(Color(UIColor.systemBackground).ignoresSafeArea())
    }
    
    private func batteryColor(_ score: Double) -> Color {
        if score >= 80 { return .green }
        if score >= 50 { return .orange }
        return .red
    }
}

struct MuscleStatus: Identifiable {
    let id = UUID()
    let name: String
    let score: Int
    let icon: String
    let color: Color
}

struct MacroRing: View {
    let name: String
    let current: Double
    let target: Double
    let color: Color
    
    var progress: Double {
        return min(current / target, 1.0)
    }
    
    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.15), lineWidth: 6)
                    .frame(width: 60, height: 60)
                
                Circle()
                    .trim(from: 0.0, to: CGFloat(progress))
                    .stroke(color, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .frame(width: 60, height: 60)
                    .rotationEffect(Angle(degrees: -90))
                
                Text("\(Int(progress * 100))%")
                    .font(.caption)
                    .fontWeight(.bold)
            }
            
            Text(name)
                .font(.caption2)
                .fontWeight(.bold)
            
            Text("\(Int(current))g / \(Int(target))g")
                .font(.system(size: 8, weight: .semibold, design: .monospaced))
                .foregroundColor(.secondary)
        }
    }
}

#Preview {
    DashboardView()
}
