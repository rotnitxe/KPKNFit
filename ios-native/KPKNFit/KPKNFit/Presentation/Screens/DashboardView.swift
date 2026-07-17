import SwiftUI

struct DashboardView: View {
    @State private var neuralBattery: Double = 82.0
    @State private var workoutTimeToday: String = "45 mins"
    
    // Sample muscle status
    let muscles = [
        MuscleStatus(name: "Pectorales", score: 90, icon: "bolt.fill", color: AppColors.neonCyan),
        MuscleStatus(name: "Cuádriceps", score: 45, icon: "exclamationmark.triangle.fill", color: AppColors.neonYellow),
        MuscleStatus(name: "Bíceps", score: 85, icon: "bolt.fill", color: AppColors.neonCyan),
        MuscleStatus(name: "Lumbar", score: 20, icon: "xmark.octagon.fill", color: AppColors.neonMagenta)
    ]
    
    var body: some View {
        ZStack {
            // 1. Absolute Black Base
            AppColors.bgDeepBlack
                .ignoresSafeArea()
            
            // 2. Liquid Glass Neon Orbs
            GeometryReader { geo in
                NeonOrbView(color: AppColors.neonMagenta)
                    .frame(width: 300, height: 300)
                    .position(x: geo.size.width * 0.8, y: geo.size.height * 0.2)
                
                NeonOrbView(color: AppColors.neonCyan)
                    .frame(width: 250, height: 250)
                    .position(x: geo.size.width * 0.1, y: geo.size.height * 0.6)
            }
            .ignoresSafeArea()
            
            // 3. Foreground Content
            ScrollView {
                VStack(spacing: 24) {
                    // Top Header
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("KPKN FIT")
                                .font(.system(.title3, design: .monospaced))
                                .fontWeight(.black)
                                .tracking(2)
                                .foregroundColor(AppColors.textPrimary)
                            
                            Text("ATHLETE STATUS REPORT")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(AppColors.textSecondary)
                        }
                        Spacer()
                        
                        Image(systemName: "person.crop.circle.fill")
                            .resizable()
                            .frame(width: 36, height: 36)
                            .foregroundColor(AppColors.neonYellow)
                    }
                    .padding(.horizontal)
                    .padding(.top, 16)
                    
                    // Neural Battery Card (Liquid Glass)
                    VStack(spacing: 16) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("SNC Neural Battery")
                                    .font(.headline)
                                    .fontWeight(.bold)
                                    .foregroundColor(AppColors.textPrimary)
                                Text("Systemic fatigue levels")
                                    .font(.caption)
                                    .foregroundColor(AppColors.textSecondary)
                            }
                            Spacer()
                            Text("\(Int(neuralBattery))%")
                                .font(.system(.title, design: .rounded))
                                .fontWeight(.black)
                                .foregroundColor(AppColors.neonCyan)
                        }
                        
                        // Battery Bar
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.white.opacity(0.1))
                                    .frame(height: 8)
                                Capsule()
                                    .fill(
                                        LinearGradient(
                                            gradient: Gradient(colors: [AppColors.neonCyan, AppColors.neonCyan.opacity(0.6)]),
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
                                .foregroundColor(AppColors.neonCyan)
                            Spacer()
                            Text("TTC: 4h 30m")
                                .font(.caption2)
                                .foregroundColor(AppColors.textSecondary)
                        }
                    }
                    .padding()
                    .liquidGlass(cornerRadius: 24, borderOpacity: 0.3)
                    .padding(.horizontal)
                    
                    // Muscle Recovery Grid
                    VStack(alignment: .leading, spacing: 12) {
                        Text("MUSCULAR RECOVERY STATUS")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(AppColors.textSecondary)
                            .padding(.horizontal)
                        
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                            ForEach(muscles, id: \.name) { muscle in
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Image(systemName: muscle.icon)
                                            .foregroundColor(muscle.color)
                                        Spacer()
                                        Text("\(muscle.score)%")
                                            .font(.subheadline)
                                            .fontWeight(.bold)
                                            .foregroundColor(AppColors.textPrimary)
                                    }
                                    
                                    Text(muscle.name)
                                        .font(.caption)
                                        .fontWeight(.medium)
                                        .foregroundColor(AppColors.textSecondary)
                                }
                                .padding(16)
                                .liquidGlass(cornerRadius: 16, borderOpacity: 0.2)
                            }
                        }
                        .padding(.horizontal)
                    }
                    
                    // Action Button
                    Button(action: {
                        // Action
                    }) {
                        HStack {
                            Text("START TODAY's SESSION")
                                .fontWeight(.bold)
                            Image(systemName: "arrow.right")
                        }
                        .foregroundColor(.black)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(AppColors.neonYellow)
                        .cornerRadius(16)
                    }
                    .padding()
                    
                    Spacer(minLength: 40)
                }
            }
        }
    }
}

// Data Model
struct MuscleStatus {
    let name: String
    let score: Int
    let icon: String
    let color: Color
}

#Preview {
    DashboardView()
}
