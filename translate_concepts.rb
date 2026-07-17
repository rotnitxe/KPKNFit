require 'fileutils'

kt_file = 'android-native/app/src/main/java/com/example/kpkn/data/wikilab/TrainingConceptsData.kt'
swift_dir = 'ios-native/KPKNFit/KPKNFit/Data/Models'
FileUtils.mkdir_p(swift_dir)
swift_file = "#{swift_dir}/TrainingConceptsData.swift"

lines = File.readlines(kt_file)
output = []
in_enum = false
in_data_class = false
in_database = false
list_depth = 0

lines.each do |line|
  stripped = line.strip
  
  if line.start_with?('package ') || line.start_with?('import ')
    next
  elsif line.start_with?('data class TrainingConcept')
    in_data_class = true
    next
  elsif in_data_class
    if stripped == ')'
      in_data_class = false
    end
    next
  elsif line.start_with?('enum class ConceptCategory')
    in_enum = true
    next
  elsif in_enum
    if stripped == '}'
      in_enum = false
    end
    next
  elsif line.start_with?('val TRAINING_CONCEPTS_DATABASE')
    in_database = true
    output << 'public let TRAINING_CONCEPTS_DATABASE: [TrainingConcept] = ['
    list_depth += 1
    next
  elsif in_database
    # Replace Kotlin syntax in this line
    line_translated = line.dup
    
    # Track list depth
    if line_translated.include?('listOf(')
      list_depth += line_translated.scan('listOf(').length
      line_translated.gsub!('listOf(', '[')
    end
    
    if line_translated.include?('emptyList()')
      line_translated.gsub!('emptyList()', '[]')
    end
    
    line_translated.gsub!(/(\b\w+)\s*=\s*/, '\1: ')
    
    # Replace closing parenthesis with brackets if we are in list_depth
    if list_depth > 0
      # If line ends with ), or )
      if line_translated.end_with?("),\n") || line_translated.end_with?("),\r\n")
        line_translated.sub!(/\),\n$/, "],\n")
        line_translated.sub!(/\),\r\n$/, "],\r\n")
        list_depth -= 1
      elsif line_translated.strip == '),'
        line_translated.gsub!('),', '],')
        list_depth -= 1
      elsif line_translated.strip == ')'
        line_translated.gsub!(')', ']')
        list_depth -= 1
      end
    end
    
    # Check if the list of database is closing
    if stripped == ')' && list_depth == 0
      line_translated = "]\n"
      in_database = false
    end
    output << line_translated
  else
    # Outside database, let's translate functions if any
    if line.start_with?('fun getConceptCategories()')
      output << "public func getConceptCategories() -> [ConceptCategory] {\n"
      output << "    Array(Set(TRAINING_CONCEPTS_DATABASE.map { $0.category })).sorted { $0.rawValue < $1.rawValue }\n"
      output << "}\n"
    elsif line.start_with?('fun searchConcepts(query: String)')
      output << "public func searchConcepts(query: String) -> [TrainingConcept] {\n"
      output << "    if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return TRAINING_CONCEPTS_DATABASE }\n"
      output << "    let q = query.lowercased()\n"
      output << "    return TRAINING_CONCEPTS_DATABASE.filter { concept in\n"
      output << "        concept.name.lowercased().contains(q) ||\n"
      output << "        concept.shortDescription.lowercased().contains(q) ||\n"
      output << "        concept.definition.lowercased().contains(q) ||\n"
      output << "        concept.category.label.lowercased().contains(q)\n"
      output << "    }\n"
      output << "}\n"
      in_data_class = true # Skip rest of implementation
    elsif in_data_class && stripped == '}'
      in_data_class = false
      next
    elsif in_data_class
      next
    else
      output << line
    end
  end
end

header = <<~SWIFT
import SwiftUI

public enum ConceptCategory: String, CaseIterable {
    case LOAD_MANAGEMENT
    case INTENSITY
    case FATIGUE
    case HYPERTROPHY
    case MOVEMENT
    case EQUIPMENT
    case QUALITIES
    case PERIODIZATION

    public var label: String {
        switch self {
        case .LOAD_MANAGEMENT: return "Gestión de Carga"
        case .INTENSITY: return "Intensidad y Esfuerzo"
        case .FATIGUE: return "Fatiga y Recuperación"
        case .HYPERTROPHY: return "Mecanismos de Hipertrofia"
        case .MOVEMENT: return "Mecánica del Movimiento"
        case .EQUIPMENT: return "Equipamiento y Medios"
        case .QUALITIES: return "Cualidades Físicas"
        case .PERIODIZATION: return "Periodización"
        }
    }

    public var color: Color {
        switch self {
        case .LOAD_MANAGEMENT: return Color(hex: 0xE53935)
        case .INTENSITY: return Color(hex: 0xFFFF8F00)
        case .FATIGUE: return Color(hex: 0x9C27B0)
        case .HYPERTROPHY: return Color(hex: 0x1E88E5)
        case .MOVEMENT: return Color(hex: 0x43A047)
        case .EQUIPMENT: return Color(hex: 0x00897B)
        case .QUALITIES: return Color(hex: 0x5C6BC0)
        case .PERIODIZATION: return Color(hex: 0x795548)
        }
    }

    public var icon: String {
        switch self {
        case .LOAD_MANAGEMENT: return "barbell"
        case .INTENSITY: return "flame"
        case .FATIGUE: return "recovery"
        case .HYPERTROPHY: return "muscle"
        case .MOVEMENT: return "movement"
        case .EQUIPMENT: return "equipment"
        case .QUALITIES: return "qualities"
        case .PERIODIZATION: return "calendar"
        }
    }
}

public struct TrainingConcept: Identifiable {
    public let id: String
    public let name: String
    public let category: ConceptCategory
    public let shortDescription: String
    public let definition: String
    public let practicalApplication: String
    public let keyPoints: [String]
    public let relatedConcepts: [String]
    public let examples: [String]
    public let commonMistakes: [String]

    public init(
        id: String,
        name: String,
        category: ConceptCategory,
        shortDescription: String,
        definition: String,
        practicalApplication: String,
        keyPoints: [String],
        relatedConcepts: [String] = [],
        examples: [String] = [],
        commonMistakes: [String] = []
    ) {
        self.id = id
        self.name = name
        self.category = category
        self.shortDescription = shortDescription
        self.definition = definition
        self.practicalApplication = practicalApplication
        self.keyPoints = keyPoints
        self.relatedConcepts = relatedConcepts
        self.examples = examples
        self.commonMistakes = commonMistakes
    }
}
SWIFT

File.write(swift_file, header + "\n" + output.join)
puts "Successfully translated TrainingConceptsData.kt to Swift!"
