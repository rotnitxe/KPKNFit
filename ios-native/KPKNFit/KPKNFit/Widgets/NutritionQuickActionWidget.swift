import SwiftUI
import WidgetKit

// MARK: — Timeline Entry

struct NutritionQuickActionEntry: TimelineEntry {
    let date: Date
}

// MARK: — Timeline Provider

struct NutritionQuickActionProvider: TimelineProvider {
    typealias Entry = NutritionQuickActionEntry

    func placeholder(in context: Context) -> NutritionQuickActionEntry {
        NutritionQuickActionEntry(date: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (NutritionQuickActionEntry) -> Void) {
        completion(NutritionQuickActionEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NutritionQuickActionEntry>) -> Void) {
        let entry = NutritionQuickActionEntry(date: Date())
        let timeline = Timeline(entries: [entry], policy: .never)
        completion(timeline)
    }
}

// MARK: — Widget View

struct NutritionQuickActionWidgetEntryView: View {
    var entry: NutritionQuickActionEntry

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: "fork.knife")
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.white)

            Text("Nutricion")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)

            Spacer().frame(height: 6)

            HStack(spacing: 8) {
                Link(destination: deepLink(path: "nutrition/action/openFoodLog")) {
                    Text("Log")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.2))
                        .cornerRadius(8)
                }

                Link(destination: deepLink(path: "nutrition/action/openSearch")) {
                    Text("Buscar")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.2))
                        .cornerRadius(8)
                }
            }
        }
        .containerBackground(for: .widget) {
            Color(red: 0x0C / 255, green: 0x7A / 255, blue: 0x6D / 255)
        }
    }

    private func deepLink(path: String) -> URL {
        URL(string: "kpkn://\(path)")!
    }
}

// MARK: — Widget Configuration

struct NutritionQuickActionWidget: Widget {
    let kind: String = "NutritionQuickActionWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(
            kind: kind,
            provider: NutritionQuickActionProvider()
        ) { entry in
            NutritionQuickActionWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Nutricion")
        .description("Acceso rapido a registro y busqueda de alimentos.")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: — Previews

#Preview(as: .systemSmall) {
    NutritionQuickActionWidget()
} timeline: {
    NutritionQuickActionEntry(date: Date())
}
