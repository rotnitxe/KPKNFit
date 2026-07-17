import Foundation

public struct MacroNutrients: Codable, Equatable {
    public var calories: Double
    public var proteins: Double
    public var carbs: Double
    public var fats: Double
    
    public init(calories: Double, proteins: Double, carbs: Double, fats: Double) {
        self.calories = calories
        self.proteins = proteins
        self.carbs = carbs
        self.fats = fats
    }
}

public struct FoodLog: Codable, Identifiable, Equatable {
    public var id: String
    public var name: String
    public var quantityGrams: Double
    public var macros: MacroNutrients
    public var loggedAt: Date
    
    public init(id: String = UUID().uuidString, name: String, quantityGrams: Double, macros: MacroNutrients, loggedAt: Date = Date()) {
        self.id = id
        self.name = name
        self.quantityGrams = quantityGrams
        self.macros = macros
        self.loggedAt = loggedAt
    }
}
