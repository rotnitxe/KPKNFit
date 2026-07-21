import Foundation
import SwiftUI
import UIKit

struct WorkoutShareService {
    static func shareToInstagramStory(
        sessionName: String,
        completedExercises: [CompletedExercise] = [],
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int? = nil,
        previousVolume: Double? = nil,
        previousDurationMinutes: Int? = nil,
        previousBestEstimated1RM: Double? = nil,
        currentBestEstimated1RM: Double? = nil
    ) -> UIImage? {
        return renderMinimalStoryCard(
            sessionName: sessionName,
            completedExercises: completedExercises,
            durationMinutes: durationMinutes,
            totalVolume: totalVolume,
            totalSets: totalSets,
            previousTotalSets: previousTotalSets,
            previousVolume: previousVolume,
            previousDurationMinutes: previousDurationMinutes,
            previousBestEstimated1RM: previousBestEstimated1RM,
            currentBestEstimated1RM: currentBestEstimated1RM
        )
    }

    static func shareContent(
        sessionName: String,
        completedExercises: [CompletedExercise],
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int
    ) -> String {
        let exerciseLines = completedExercises
            .filter { $0.sets.contains { !$0.isWarmup } }
            .map { ex in
                let sets = ex.sets.filter { !$0.isWarmup }
                let summary = sets.map { set in
                    if let time = set.timeSeconds, time > 0 { return "\(time)s" }
                    return "\(set.reps)x\(set.weight.flatMap { $0 > 0 ? "\(toTrimmedNumberString($0))kg" : "BW" } ?? "BW")"
                }.joined(separator: ", ")
                return "• \(ex.exerciseName): \(summary)"
            }.joined(separator: "\n")

        return """
        🏋️ \(sessionName)
        📊 \(totalSets) series · \(toTrimmedNumberString(totalVolume))kg · \(durationMinutes)min
        
        \(exerciseLines)
        
        Compartido desde KPKN
        """
    }

    private static func renderMinimalStoryCard(
        sessionName: String,
        completedExercises: [CompletedExercise],
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int?,
        previousVolume: Double?,
        previousDurationMinutes: Int?,
        previousBestEstimated1RM: Double?,
        currentBestEstimated1RM: Double?
    ) -> UIImage? {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 1080, height: 1920))
        return renderer.image { ctx in
            let c = ctx.cgContext
            let rect = CGRect(x: 0, y: 0, width: 1080, height: 1920)
            c.setFillColor(UIColor(red: 0xF7/255, green: 0xF8/255, blue: 0xF4/255, alpha: 1).cgColor)
            c.fill(rect)

            let paragraphStyle = NSMutableParagraphStyle()
            paragraphStyle.alignment = .left

            let titleAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 56),
                .foregroundColor: UIColor(red: 0x11/255, green: 0x13/255, blue: 0x14/255, alpha: 1),
                .paragraphStyle: paragraphStyle
            ]

            let exerciseNames = completedExercises
                .filter { $0.sets.contains { !$0.isWarmup } }
                .map { $0.exerciseName.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
                .uniqued()
            let visibleNames = exerciseNames.count > 18
                ? Array(exerciseNames.prefix(17)) + ["+\(exerciseNames.count - 17) ejercicios mas"]
                : (exerciseNames.isEmpty ? ["Sin ejercicios registrados"] : Array(exerciseNames))

            let nameTextSize: CGFloat = visibleNames.count > 14 ? 30 : (visibleNames.count > 10 ? 33 : 36)
            let lineHeight: CGFloat = visibleNames.count > 14 ? 52 : (visibleNames.count > 10 ? 60 : 72)
            let nameAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: nameTextSize, weight: .medium),
                .foregroundColor: UIColor(red: 0x24/255, green: 0x29/255, blue: 0x2A/255, alpha: 1),
                .paragraphStyle: paragraphStyle
            ]

            let cardLeft: CGFloat = 112
            let cardWidth: CGFloat = 1080 - 224
            let cardHeight = min(max(260 + CGFloat(visibleNames.count) * lineHeight, 620), 1240)
            let cardTop = (1920 - cardHeight) / 2
            let cardRect = CGRect(x: cardLeft, y: cardTop, width: cardWidth, height: cardHeight)

            c.setFillColor(UIColor.white.cgColor)
            let path = UIBezierPath(roundedRect: cardRect, cornerRadius: 42)
            c.addPath(path.cgPath)
            c.fillPath()

            let contentLeft = cardLeft + 64
            let contentRight = cardLeft + cardWidth - 64

            ("ENTRENAMIENTO DE HOY" as NSString).draw(at: CGPoint(x: contentLeft, y: cardTop + 112), withAttributes: titleAttrs)

            let sessionAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 38),
                .foregroundColor: UIColor(red: 0x2A/255, green: 0x2F/255, blue: 0x30/255, alpha: 1)
            ]
            let sessionLabel = sessionName.isEmpty ? "Sesión" : sessionName
            (sessionLabel as NSString).draw(at: CGPoint(x: contentLeft, y: cardTop + 160), withAttributes: sessionAttrs)

            c.setStrokeColor(UIColor(red: 0x11/255, green: 0x13/255, blue: 0x14/255, alpha: 0.17).cgColor)
            c.setLineWidth(2)
            c.move(to: CGPoint(x: contentLeft, y: cardTop + 188))
            c.addLine(to: CGPoint(x: contentRight, y: cardTop + 188))
            c.strokePath()

            var rowY = cardTop + 252
            for name in visibleNames {
                c.setFillColor(UIColor(red: 0x11/255, green: 0x13/255, blue: 0x14/255, alpha: 1).cgColor)
                c.fillEllipse(in: CGRect(x: contentLeft + 2, y: rowY - 16, width: 12, height: 12))
                (name as NSString).draw(at: CGPoint(x: contentLeft + 32, y: rowY - 22), withAttributes: nameAttrs)
                rowY += lineHeight
            }
        }
    }
}

extension Sequence where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
