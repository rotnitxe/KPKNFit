import Foundation

enum DatasetKnowledgeContext {
    struct ContextProfile {
        let count: Int
        let typicalGrams: [Double]
        let typicalKcal: [Double]
        let typicalProtein: [Double]
        let typicalFats: [Double]
        let typicalCarbs: [Double]
    }

    struct MacroRange {
        let count: Int
        let kcalMin: Double
        let kcalMax: Double
        let kcalMedian: Double
        let proteinMin: Double
        let proteinMax: Double
        let proteinMedian: Double
        let fatsMin: Double
        let fatsMax: Double
        let fatsMedian: Double
        let carbsMin: Double
        let carbsMax: Double
        let carbsMedian: Double
    }

    static let contextKeywords: [String: [String]] = [
        "CASINO": ["casino", "cafeteria", "comedor", "buffet del", "menu del dia"],
        "POST_ENTRENO": ["post-entreno", "post entreno", "post-entrenamiento", "recuperacion", "post-sentadillas", "post-pecho", "post-espalda", "post-pierna"],
        "POWERBUILDER": ["powerbuilder", "power builder", "volumen extremo", "masa extrema"],
        "ABUELA_CHILENA": ["abuela chilena", "abuela", "contundente", "plato hondo", "tazon grande", "plato rebosante"],
        "OFICINA": ["oficina", "escritorio", "trabajo", "reunion", "break de oficina"],
        "ESTUDIANTE": ["estudiante", "universidad", "facultad", "campus", "barato", "corto de lucas", "sobrevivencia"],
        "CONDOMINIO": ["condominio", "edificio", "departamento", "casa"],
        "SNACK": ["snack", "colacion", "merendola", "merienda", "tentempie", "piscolabis", "refrigerio"],
        "DESAYUNO": ["desayuno", "desayunar", "manana"],
        "ALMUERZO": ["almuerzo", "almorzar", "mediodia", "tarde"],
        "CENA": ["cena", "cenar", "noche", "nocturno", "antes de dormir"],
    ]

    static let intensifierKeywords: [String: [String]] = [
        "GIGANTE": ["gigante", "gigantesca", "gigantesco", "enorme", "descomunal", "bestial"],
        "GENEROSO": ["generoso", "generosa", "generosos", "generosas"],
        "COLMADO": ["colmado", "colmada", "colmados", "colmadas", "hasta el borde", "rebosante", "rebosantes", "lleno", "llena", "repleto"],
        "GRANDE": ["grande", "grandes", "gran"],
        "PEQUENO": ["pequeno", "pequena", "pequenas", "pequenos", "chico", "chica", "chicos", "chicas"],
        "FINO": ["fino", "fina", "finos", "finas", "delgado", "delgada", "delgados", "delgadas"],
        "GRUESO": ["grueso", "gruesa", "gruesos", "gruesas", "gordo", "gorda"],
    ]

    static let contextProfiles: [String: ContextProfile] = [
        "CASINO": ContextProfile(count: 510, typicalGrams: [180, 200, 250, 300, 150, 100, 80, 120, 60, 90], typicalKcal: [350, 420, 280, 310, 380, 450, 320, 290, 360, 400], typicalProtein: [15, 18, 12, 20, 22, 16, 14, 19, 17, 21], typicalFats: [12, 15, 10, 18, 20, 14, 11, 16, 13, 17], typicalCarbs: [45, 55, 35, 40, 50, 60, 42, 38, 48, 52]),
        "POST_ENTRENO": ContextProfile(count: 116, typicalGrams: [200, 250, 300, 180, 220, 280, 150, 190, 240, 260], typicalKcal: [500, 600, 700, 450, 550, 650, 480, 520, 580, 620], typicalProtein: [40, 50, 60, 35, 45, 55, 38, 42, 48, 52], typicalFats: [10, 15, 20, 8, 12, 18, 9, 11, 14, 16], typicalCarbs: [50, 60, 70, 45, 55, 65, 48, 52, 58, 62]),
        "POWERBUILDER": ContextProfile(count: 46, typicalGrams: [300, 350, 400, 280, 320, 380, 250, 290, 340, 360], typicalKcal: [800, 900, 1000, 750, 850, 950, 780, 820, 880, 920], typicalProtein: [60, 70, 80, 55, 65, 75, 58, 62, 68, 72], typicalFats: [25, 30, 35, 22, 28, 33, 23, 26, 29, 31], typicalCarbs: [80, 90, 100, 75, 85, 95, 78, 82, 88, 92]),
        "SNACK": ContextProfile(count: 200, typicalGrams: [30, 40, 50, 25, 35, 45, 20, 28, 38, 42], typicalKcal: [150, 200, 250, 120, 180, 220, 130, 160, 190, 210], typicalProtein: [8, 10, 12, 6, 9, 11, 7, 8, 10, 11], typicalFats: [5, 8, 10, 4, 7, 9, 5, 6, 8, 9], typicalCarbs: [15, 20, 25, 12, 18, 22, 14, 16, 19, 21]),
        "DESAYUNO": ContextProfile(count: 150, typicalGrams: [200, 250, 300, 180, 220, 280, 150, 190, 240, 260], typicalKcal: [350, 400, 450, 300, 380, 420, 320, 360, 390, 410], typicalProtein: [15, 18, 22, 12, 16, 20, 13, 15, 17, 19], typicalFats: [10, 12, 15, 8, 11, 14, 9, 10, 12, 13], typicalCarbs: [40, 50, 60, 35, 45, 55, 38, 42, 48, 52]),
        "ALMUERZO": ContextProfile(count: 200, typicalGrams: [250, 300, 350, 220, 280, 320, 200, 240, 290, 310], typicalKcal: [500, 600, 700, 450, 550, 650, 480, 520, 580, 620], typicalProtein: [30, 35, 40, 25, 32, 38, 28, 30, 34, 36], typicalFats: [15, 20, 25, 12, 18, 22, 14, 16, 19, 21], typicalCarbs: [50, 60, 70, 45, 55, 65, 48, 52, 58, 62]),
        "CENA": ContextProfile(count: 120, typicalGrams: [200, 250, 300, 180, 220, 280, 150, 190, 240, 260], typicalKcal: [400, 450, 500, 350, 420, 480, 370, 400, 440, 460], typicalProtein: [25, 30, 35, 20, 28, 32, 22, 25, 29, 31], typicalFats: [12, 15, 18, 10, 14, 16, 11, 12, 15, 16], typicalCarbs: [40, 50, 60, 35, 45, 55, 38, 42, 48, 52]),
        "OFICINA": ContextProfile(count: 9, typicalGrams: [30, 40, 50, 25, 35, 45, 20, 28, 38], typicalKcal: [150, 200, 250, 120, 180, 220, 130, 160, 190], typicalProtein: [5, 8, 10, 4, 7, 9, 5, 6, 8], typicalFats: [5, 8, 10, 4, 7, 9, 5, 6, 8], typicalCarbs: [15, 20, 25, 12, 18, 22, 14, 16, 19]),
        "ESTUDIANTE": ContextProfile(count: 4, typicalGrams: [200, 250, 300, 180], typicalKcal: [300, 400, 500, 280], typicalProtein: [12, 15, 20, 10], typicalFats: [8, 12, 15, 7], typicalCarbs: [30, 40, 50, 28]),
        "ABUELA_CHILENA": ContextProfile(count: 11, typicalGrams: [300, 350, 400, 280, 320, 380, 250, 290, 340, 360], typicalKcal: [600, 700, 800, 550, 650, 750, 580, 620, 680, 720], typicalProtein: [30, 35, 40, 25, 32, 38, 28, 30, 34, 36], typicalFats: [20, 25, 30, 18, 22, 28, 19, 21, 24, 26], typicalCarbs: [60, 70, 80, 55, 65, 75, 58, 62, 68, 72]),
        "CONDOMINIO": ContextProfile(count: 1, typicalGrams: [250], typicalKcal: [400], typicalProtein: [15], typicalFats: [12], typicalCarbs: [40]),
    ]

    static let macroRanges: [String: MacroRange] = [
        "MACRO_CALC": MacroRange(count: 3737, kcalMin: 50, kcalMax: 1600, kcalMedian: 465, proteinMin: 3, proteinMax: 92, proteinMedian: 46, fatsMin: 0, fatsMax: 110, fatsMedian: 12, carbsMin: 1, carbsMax: 190, carbsMedian: 55),
        "DATABASE_LOOKUP": MacroRange(count: 3747, kcalMin: 10, kcalMax: 500, kcalMedian: 130, proteinMin: 0.5, proteinMax: 35, proteinMedian: 13, fatsMin: 0, fatsMax: 45, fatsMedian: 2, carbsMin: 0, carbsMax: 80, carbsMedian: 15),
        "GENERAL": MacroRange(count: 8722, kcalMin: 1, kcalMax: 1600, kcalMedian: 350, proteinMin: 0, proteinMax: 92, proteinMedian: 30, fatsMin: 0, fatsMax: 110, fatsMedian: 10, carbsMin: 0, carbsMax: 190, carbsMedian: 45),
        "DESCRIBE": MacroRange(count: 1791, kcalMin: 100, kcalMax: 1600, kcalMedian: 550, proteinMin: 5, proteinMax: 80, proteinMedian: 40, fatsMin: 5, fatsMax: 80, fatsMedian: 15, carbsMin: 10, carbsMax: 150, carbsMedian: 50),
        "QUESTION": MacroRange(count: 1408, kcalMin: 10, kcalMax: 500, kcalMedian: 150, proteinMin: 0.5, proteinMax: 35, proteinMedian: 15, fatsMin: 0, fatsMax: 45, fatsMedian: 3, carbsMin: 0, carbsMax: 80, carbsMedian: 18),
    ]
}
