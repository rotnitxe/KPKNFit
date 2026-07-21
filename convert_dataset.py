#!/usr/bin/env python3
"""Convert DatasetKnowledge.kt to DatasetKnowledge.swift - proper parsing"""

import re

kt_path = "/Users/imacmantra/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/domain/Nutrition/DatasetKnowledge.kt"
swift_path = "/Users/imacmantra/Documents/KPKNFit/ios-native/KPKNFit/KPKNFit/Domain/Nutrition/DatasetKnowledge.swift"

with open(kt_path, "r") as f:
    content = f.read()

def find_closing_paren(text, start):
    depth = 0
    i = start
    while i < len(text):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                return text[start:i+1], i+1
        i += 1
    return text[start:], len(text)

def find_closing_brace(text, start):
    depth = 0
    i = start
    while i < len(text):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return text[start:i+1], i+1
        i += 1
    return text[start:], len(text)

def split_top_level(text, sep=','):
    """Split at top-level commas (not inside quotes or parens)."""
    parts = []
    depth = 0
    in_string = False
    current = ""
    for ch in text:
        if ch == '"' and (not current or current[-1] != '\\'):
            in_string = not in_string
        if not in_string:
            if ch == '(' or ch == '[' or ch == '{':
                depth += 1
            elif ch == ')' or ch == ']' or ch == '}':
                depth -= 1
        if ch == sep and depth == 0 and not in_string:
            parts.append(current.strip())
            current = ""
        else:
            current += ch
    if current.strip():
        parts.append(current.strip())
    return parts

def extract_init_body(content, func_name):
    """Extract the body of an init function."""
    pattern = rf'private fun {func_name}\(\): .*? = (mapOf|listOf|setOf)\('
    m = re.search(pattern, content)
    if not m:
        return None, None
    paren_start = m.end() - 1
    block, _ = find_closing_paren(content, paren_start)
    # Remove outer parens
    body = block[1:-1].strip()
    collection_type = m.group(1)
    return collection_type, body

# Parse all init function data
pattern = r'private fun (init_\w+)\(\): .*? = (mapOf|listOf|setOf)\('
init_fns = {}
for m in re.finditer(pattern, content):
    name = m.group(1)
    ctype, body = extract_init_body(content, name)
    if body is not None:
        init_fns[name] = (ctype, body)

output = []
output.append("import Foundation\n\n")
output.append("enum DatasetKnowledge {\n")

# Metadata
output.append("    static let DATASET_SIZE = 19405\n")
output.append("    static let VOCABULARY_SIZE = 1000\n")
output.append("    static let TRIPLET_COUNT = 800\n\n")

# TYPE_COUNTS
output.append("    // ─── Type Distribution ─────────────────────────────────────────────────\n\n")
output.append("    static let TYPE_COUNTS: [String: Int] = [\n")
output.append('        "MACRO_CALC": 3737,\n')
output.append('        "DATABASE_LOOKUP": 3747,\n')
output.append('        "GENERAL": 8722,\n')
output.append('        "DESCRIBE": 1791,\n')
output.append('        "QUESTION": 1408\n')
output.append("    ]\n\n")

# PortionTriplet struct
output.append("""    struct PortionTriplet {
        let food: String
        let grams: Double
        let frequency: Int

        init(_ food: String, _ grams: Double, _ frequency: Int) {
            self.food = food
            self.grams = grams
            self.frequency = frequency
        }
    }

""")

# ContextProfile struct
output.append("""    struct ContextProfile {
        let count: Int
        let typicalGrams: [Double]
        let typicalKcal: [Double]
        let typicalProtein: [Double]
        let typicalFats: [Double]
        let typicalCarbs: [Double]
    }

""")

# MacroRange struct
output.append("""    struct MacroRange {
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

""")

def format_map_entries(body):
    """Format mapOf body entries, preserving string values."""
    entries = split_top_level(body)
    result = []
    for entry in entries:
        if not entry.strip():
            continue
        # Convert "key" to "value" -> "key": "value"
        entry = re.sub(r'^"([^"]*)"\s+to\s+"(.*)"$', r'"\1": "\2"', entry)
        # Convert "key" to number -> "key": number
        entry = re.sub(r'^"([^"]*)"\s+to\s+(\d+(?:\.\d+)?)$', r'"\1": \2', entry)
        # Convert "key" to IDENTIFIER -> "key": IDENTIFIER
        entry = re.sub(r'^"([^"]*)"\s+to\s+([A-Za-z_]\w*(?:\(.*\))?)$', r'"\1": \2', entry)
        result.append("        " + entry)
    return '\n'.join(result)

def format_list_entries(body):
    """Format listOf body entries."""
    entries = split_top_level(body)
    result = []
    for entry in entries:
        if not entry.strip():
            continue
        result.append("        " + entry.strip())
    return '\n'.join(result)

def format_set_entries(body):
    return format_list_entries(body)

# === TFIDF_TOKEN_INDEX ===
combined = []
for i in range(6):
    name = f"init_tfidf_token_index_{i}"
    if name in init_fns:
        ctype, body = init_fns[name]
        combined.append(body)
all_body = ','.join(combined)
output.append("    // ─── TF-IDF Token Index ────────────────────────────────────────────────\n\n")
output.append(f"    static let TFIDF_TOKEN_INDEX: [String: String] = [\n{format_map_entries(all_body)}\n    ]\n\n")

# === TFIDF_TRIGRAM_INDEX ===
combined = []
for i in range(6):
    name = f"init_tfidf_trigram_index_{i}"
    if name in init_fns:
        ctype, body = init_fns[name]
        combined.append(body)
all_body = ','.join(combined)
output.append("    // ─── TF-IDF Trigram Index ──────────────────────────────────────────────\n\n")
output.append(f"    static let TFIDF_TRIGRAM_INDEX: [String: String] = [\n{format_map_entries(all_body)}\n    ]\n\n")

# === PORTION_TRIPLETS ===
combined = []
for i in range(6):
    name = f"init_portion_triplets_{i}"
    if name in init_fns:
        ctype, body = init_fns[name]
        combined.append(body)
all_body = ','.join(combined)
output.append("    // ─── Portion Triplets (food → average grams) ───────────────────────────\n\n")
output.append(f"    static let PORTION_TRIPLETS: [PortionTriplet] = [\n{format_list_entries(all_body)}\n    ]\n\n")

# === CONTEXT_PROFILES ===
# Extract directly from content
cp_match = re.search(r'val CONTEXT_PROFILES: Map<String, ContextProfile> = mapOf\(', content)
if cp_match:
    paren_start = cp_match.end() - 1
    block, _ = find_closing_paren(content, paren_start)
    body = block[1:-1]
    output.append("    // ─── Context Profiles ──────────────────────────────────────────────────\n\n")
    output.append("    static let CONTEXT_PROFILES: [String: ContextProfile] = [\n")
    for entry in split_top_level(body):
        if not entry.strip():
            continue
        entry = re.sub(r'^"([^"]*)"\s+to\s+', r'"\1": ', entry)
        entry = re.sub(r'listOf\(', '[', entry)
        output.append("        " + entry + ",\n")
    output.append("    ]\n\n")

# === MACRO_RANGES ===
mr_match = re.search(r'val MACRO_RANGES: Map<String, MacroRange> = mapOf\(', content)
if mr_match:
    paren_start = mr_match.end() - 1
    block, _ = find_closing_paren(content, paren_start)
    body = block[1:-1]
    output.append("    // ─── Macro Ranges by Type ──────────────────────────────────────────────\n\n")
    output.append("    static let MACRO_RANGES: [String: MacroRange] = [\n")
    for entry in split_top_level(body):
        if not entry.strip():
            continue
        entry = re.sub(r'^"([^"]*)"\s+to\s+', r'"\1": ', entry)
        output.append("        " + entry + ",\n")
    output.append("    ]\n\n")

# === VOCABULARY ===
combined = []
for i in range(7):
    name = f"init_vocabulary_{i}"
    if name in init_fns:
        ctype, body = init_fns[name]
        combined.append(body)
all_body = ','.join(combined)
output.append("    // ─── Vocabulary Set ────────────────────────────────────────────────────\n\n")
output.append(f"    static let VOCABULARY: Set<String> = [\n{format_set_entries(all_body)}\n    ]\n\n")

# === CONTEXT_KEYWORDS ===
ck_match = re.search(r'val CONTEXT_KEYWORDS: Map<String, List<String>> = mapOf\(', content)
if ck_match:
    paren_start = ck_match.end() - 1
    block, _ = find_closing_paren(content, paren_start)
    body = block[1:-1]
    output.append("    // ─── Context Keywords ──────────────────────────────────────────────────\n\n")
    output.append("    static let CONTEXT_KEYWORDS: [String: [String]] = [\n")
    for entry in split_top_level(body):
        if not entry.strip():
            continue
        entry = re.sub(r'^"([^"]*)"\s+to\s+listOf\(', r'"\1": [', entry)
        # Close the list - replace the final )) with ])
        # Count parens to handle nested
        entry = re.sub(r'\)$', ']', entry.rstrip())
        output.append("        " + entry + ",\n")
    output.append("    ]\n\n")

# === INTENSIFIER_KEYWORDS ===
ik_match = re.search(r'val INTENSIFIER_KEYWORDS: Map<String, List<String>> = mapOf\(', content)
if ik_match:
    paren_start = ik_match.end() - 1
    block, _ = find_closing_paren(content, paren_start)
    body = block[1:-1]
    output.append("    // ─── Intensifier Keywords ──────────────────────────────────────────────\n\n")
    output.append("    static let INTENSIFIER_KEYWORDS: [String: [String]] = [\n")
    for entry in split_top_level(body):
        if not entry.strip():
            continue
        entry = re.sub(r'^"([^"]*)"\s+to\s+listOf\(', r'"\1": [', entry)
        entry = re.sub(r'\)$', ']', entry.rstrip())
        output.append("        " + entry + ",\n")
    output.append("    ]\n\n")

# === INSTRUCTIONS ===
combined = []
for i in range(20):
    name = f"init_instructions_{i}"
    if name in init_fns:
        ctype, body = init_fns[name]
        combined.append(body)
all_body = ','.join(combined)
output.append("    // ─── Dataset Instructions (for semantic search) ────────────────────────\n\n")
output.append(f"    static let INSTRUCTIONS: [String] = [\n{format_list_entries(all_body)}\n    ]\n\n")

# === ENTRY_TYPES ===
combined = []
for i in range(20):
    name = f"init_entry_types_{i}"
    if name in init_fns:
        ctype, body = init_fns[name]
        combined.append(body)
all_body = ','.join(combined)
output.append("    // ─── Entry Types ───────────────────────────────────────────────────────\n\n")
output.append(f"    static let ENTRY_TYPES: [String] = [\n{format_list_entries(all_body)}\n    ]\n\n")

output.append("}\n")

with open(swift_path, "w") as f:
    f.writelines(output)

line_count = len(''.join(output).split('\n'))
print(f"Done! Written ~{line_count} lines to {swift_path}")
print(f"Init functions processed: {len(init_fns)}")
