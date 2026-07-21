import Foundation

// MARK: - ProgramHelpers

/// Returns the absolute week index (0-based) across all blocks and mesocycles.
func getAbsoluteWeekIndex(program: Program, weekId: String) -> Int {
    var idx = 0
    for macro in program.macrocycles {
        for block in macro.blocks {
            for meso in block.mesocycles {
                for week in meso.weeks {
                    if week.id == weekId { return idx }
                    idx += 1
                }
            }
        }
    }
    return 0
}

/// Total weeks across the full program.
func getTotalWeeks(program: Program) -> Int {
    program.macrocycles.reduce(0) { macroAcc, macro in
        macroAcc + macro.blocks.reduce(0) { blockAcc, block in
            blockAcc + block.mesocycles.reduce(0) { mesoAcc, meso in
                mesoAcc + meso.weeks.count
            }
        }
    }
}

/// Day name (Monday=1 ... Sunday=7).
func getDayName(dayOfWeek: Int) -> String {
    switch dayOfWeek {
    case 1: return "Lunes"
    case 2: return "Martes"
    case 3: return "Miércoles"
    case 4: return "Jueves"
    case 5: return "Viernes"
    case 6: return "Sábado"
    case 7: return "Domingo"
    default: return "Día \(dayOfWeek)"
    }
}

/// Short day name.
func getShortDayName(dayOfWeek: Int) -> String {
    switch dayOfWeek {
    case 1: return "Lun"
    case 2: return "Mar"
    case 3: return "Mié"
    case 4: return "Jue"
    case 5: return "Vie"
    case 6: return "Sáb"
    case 7: return "Dom"
    default: return "D\(dayOfWeek)"
    }
}

/// Current day of week as 1–7 (Mon–Sun), matching PWA convention.
func getCurrentDayOfWeek() -> Int {
    let jsDayMap = [7, 1, 2, 3, 4, 5, 6] // JS 0=Sun → 7; 1=Mon → 1
    let cal = Calendar.current
    let jsDay = cal.component(.weekday, from: Date()) - 1 // .sunday=1 → 0
    return jsDayMap[jsDay]
}

/// Today's date string as ISO yyyy-MM-dd.
func getTodayDateString() -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.locale = Locale.current
    return formatter.string(from: Date())
}

/// Counts total exercises in a session (handles parts).
func getSessionExerciseCount(_ session: Session) -> Int {
    if !session.parts.isEmpty {
        return session.parts.reduce(0) { $0 + $1.exercises.count }
    }
    return session.exercises.count
}
