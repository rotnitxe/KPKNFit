#!/usr/bin/env python3
"""
Genera variantes de ejercicios faltantes por equipamiento.
Cubre: Smith, Polea, Máquina Convergente, Barra, Mancuernas, Kettlebell, Disco, Peso Corporal.

Ejecutar: python scripts/expand_exercise_variants.py
"""

import json
import re
from pathlib import Path

def slugify(name: str) -> str:
    s = name.lower().strip()
    s = re.sub(r"[^\w\s-]", "", s)
    s = re.sub(r"[-\s]+", "-", s)
    return s[:50] if s else "ejercicio"

def make_id(base: str, suffix: str) -> str:
    return f"ext_{slugify(base)}-{slugify(suffix)}"

def ex(name: str, equipment: str, type_: str, efc: float, ssc: float, body_part: str = "upper", force: str = "Otro") -> dict:
    base_id = slugify(name)
    eq_slug = slugify(equipment)[:20]
    return {
        "id": f"ext_{base_id}-{eq_slug}",
        "name": name,
        "equipment": equipment,
        "type": type_,
        "efc": efc,
        "ssc": ssc,
        "cnc": 2.5,
        "involvedMuscles": [],
        "category": "Hipertrofia",
        "force": force,
        "bodyPart": body_part,
        "chain": "anterior",
    }

# ========== VARIANTES FALTANTES POR AUDITORÍA ==========
VARIANTES = [
    # --- PECHO ---
    ex("Press de Banca", "Smith (Plano)", "C", 3.2, 0.1, "upper", "Empuje"),
    ex("Press de Banca", "Smith (Inclinado)", "C", 3.0, 0.1, "upper", "Empuje"),
    ex("Press de Banca", "Smith (Declinado)", "C", 3.0, 0.1, "upper", "Empuje"),
    ex("Press de Banca", "Polea Baja (Unilateral)", "C", 2.5, 0.0, "upper", "Empuje"),
    ex("Press de Banca", "Polea Baja (Bilateral)", "C", 2.8, 0.0, "upper", "Empuje"),
    ex("Press de Banca", "Kettlebell (Floor Press)", "C", 3.0, 0.2, "upper", "Empuje"),
    ex("Press de Pecho", "Máquina Smith", "C", 3.0, 0.1, "upper", "Empuje"),
    ex("Press de Pecho", "Máquina Convergente", "C", 3.2, 0.1, "upper", "Empuje"),
    ex("Aperturas", "Polea Baja", "A", 1.8, 0.0, "upper", "Empuje"),
    ex("Aperturas", "Polea Alta", "A", 1.8, 0.0, "upper", "Empuje"),
    ex("Aperturas", "Kettlebell", "A", 1.5, 0.1, "upper", "Empuje"),
    ex("Flexiones", "Con Disco", "C", 2.8, 0.1, "upper", "Empuje"),
    ex("Flexiones", "Mancuernas (Agarre)", "C", 2.5, 0.1, "upper", "Empuje"),
    ex("Flexiones", "Kettlebell (Agarre)", "C", 2.5, 0.1, "upper", "Empuje"),
    ex("Fondos", "Máquina (Asistida)", "C", 2.5, 0.0, "upper", "Empuje"),
    ex("Fondos", "Máquina (Lastrada)", "C", 3.5, 0.2, "upper", "Empuje"),

    # --- HOMBROS ---
    ex("Press Militar", "Smith", "C", 3.5, 0.8, "upper", "Empuje"),
    ex("Press Militar", "Polea Baja", "C", 2.8, 0.5, "upper", "Empuje"),
    ex("Press Militar", "Máquina Convergente", "C", 3.5, 0.2, "upper", "Empuje"),
    ex("Press Militar", "Kettlebell", "C", 3.0, 0.8, "upper", "Empuje"),
    ex("Press de Hombros", "Smith", "C", 3.2, 0.6, "upper", "Empuje"),
    ex("Press de Hombros", "Máquina Smith", "C", 3.0, 0.1, "upper", "Empuje"),
    ex("Elevaciones Laterales", "Smith (Unilateral)", "A", 1.4, 0.0, "upper", "Tirón"),
    ex("Elevaciones Laterales", "Kettlebell", "A", 1.5, 0.2, "upper", "Tirón"),
    ex("Elevaciones Frontales", "Polea", "A", 1.5, 0.1, "upper", "Tirón"),
    ex("Elevaciones Frontales", "Barra", "A", 1.6, 0.2, "upper", "Tirón"),
    ex("Elevaciones Frontales", "Kettlebell", "A", 1.5, 0.2, "upper", "Tirón"),
    ex("Elevaciones Frontales", "Disco", "A", 1.5, 0.2, "upper", "Tirón"),
    ex("Face Pull", "Banda", "A", 1.2, 0.0, "upper", "Tirón"),
    ex("Face Pull", "Mancuernas", "A", 1.4, 0.0, "upper", "Tirón"),
    ex("Pájaros", "Polea Cruzada", "A", 1.6, 0.0, "upper", "Tirón"),
    ex("Pájaros", "Polea Baja", "A", 1.5, 0.0, "upper", "Tirón"),
    ex("Pájaros", "Kettlebell", "A", 1.5, 0.1, "upper", "Tirón"),
    ex("Remo al Mentón", "Smith", "C", 2.8, 0.3, "upper", "Tirón"),
    ex("Remo al Mentón", "Mancuernas", "C", 2.8, 0.4, "upper", "Tirón"),
    ex("Remo al Mentón", "Kettlebell", "C", 2.5, 0.4, "upper", "Tirón"),

    # --- ESPALDA ---
    ex("Remo con Barra", "Smith", "C", 3.5, 0.8, "upper", "Tirón"),
    ex("Remo con Barra", "Polea Baja", "C", 2.8, 0.3, "upper", "Tirón"),
    ex("Remo con Mancuerna", "Kettlebell", "C", 2.8, 0.5, "upper", "Tirón"),
    ex("Remo con Mancuerna", "Polea", "C", 2.5, 0.2, "upper", "Tirón"),
    ex("Remo", "Máquina Convergente", "C", 3.0, 0.1, "upper", "Tirón"),
    ex("Remo", "Máquina Smith", "C", 2.8, 0.1, "upper", "Tirón"),
    ex("Jalón al Pecho", "Máquina Convergente", "C", 2.5, 0.0, "upper", "Tirón"),
    ex("Jalón al Pecho", "Barra Recta", "C", 2.8, 0.1, "upper", "Tirón"),
    ex("Dominadas", "Con Kettlebell", "C", 4.2, 0.3, "upper", "Tirón"),
    ex("Dominadas", "Con Disco", "C", 4.2, 0.3, "upper", "Tirón"),
    ex("Dominadas", "Asistidas (Máquina)", "C", 2.5, 0.0, "upper", "Tirón"),
    ex("Pull-Over", "Máquina", "A", 2.5, 0.0, "upper", "Tirón"),
    ex("Pull-Over", "Barra", "A", 2.2, 0.2, "upper", "Tirón"),
    ex("Encogimientos", "Smith", "A", 2.5, 1.0, "upper", "Tirón"),
    ex("Encogimientos", "Polea", "A", 2.2, 0.8, "upper", "Tirón"),
    ex("Encogimientos", "Kettlebell", "A", 2.2, 0.8, "upper", "Tirón"),
    ex("Encogimientos", "Peso Corporal", "A", 1.5, 0.5, "upper", "Tirón"),

    # --- PIERNAS: SENTADILLAS ---
    ex("Sentadilla Trasera", "Smith", "C", 3.5, 0.5, "lower", "Sentadilla"),
    ex("Sentadilla Trasera", "Polea (Belt Squat)", "C", 3.2, 0.0, "lower", "Sentadilla"),
    ex("Sentadilla Frontal", "Smith", "C", 3.8, 0.6, "lower", "Sentadilla"),
    ex("Sentadilla Frontal", "Polea", "C", 3.0, 0.2, "lower", "Sentadilla"),
    ex("Sentadilla Goblet", "Kettlebell", "C", 2.5, 0.3, "lower", "Sentadilla"),
    ex("Sentadilla", "Peso Corporal", "C", 2.0, 0.2, "lower", "Sentadilla"),
    ex("Sentadilla Búlgara", "Barra", "C", 3.8, 0.9, "lower", "Sentadilla"),
    ex("Sentadilla Búlgara", "Kettlebell", "C", 3.2, 0.5, "lower", "Sentadilla"),
    ex("Sentadilla Búlgara", "Peso Corporal", "C", 2.5, 0.3, "lower", "Sentadilla"),
    ex("Zancada", "Barra (Trasera)", "C", 3.8, 0.8, "lower", "Sentadilla"),
    ex("Zancada", "Barra (Frontal)", "C", 3.5, 0.6, "lower", "Sentadilla"),
    ex("Zancada", "Mancuernas", "C", 3.2, 0.5, "lower", "Sentadilla"),
    ex("Zancada", "Kettlebell", "C", 3.0, 0.5, "lower", "Sentadilla"),
    ex("Zancada", "Polea", "C", 2.8, 0.2, "lower", "Sentadilla"),
    ex("Zancada", "Peso Corporal", "C", 2.5, 0.4, "lower", "Sentadilla"),
    ex("Subida al Cajón", "Barra", "C", 3.5, 0.5, "lower", "Sentadilla"),
    ex("Subida al Cajón", "Kettlebell", "C", 3.0, 0.4, "lower", "Sentadilla"),
    ex("Subida al Cajón", "Peso Corporal", "C", 2.5, 0.3, "lower", "Sentadilla"),
    ex("Prensa de Piernas", "Smith (Pies Altos)", "C", 3.0, 0.2, "lower", "Sentadilla"),
    ex("Prensa de Piernas", "Máquina Convergente", "C", 3.0, 0.1, "lower", "Sentadilla"),
    ex("Extensiones Cuádriceps", "Polea", "A", 1.4, 0.0, "lower", "Extensión"),
    ex("Extensiones Cuádriceps", "Peso Corporal (Sissy)", "A", 2.0, 0.1, "lower", "Extensión"),

    # --- PIERNAS: BISAGRA ---
    ex("Peso Muerto", "Smith", "C", 4.2, 1.2, "lower", "Bisagra"),
    ex("Peso Muerto Rumano", "Polea", "C", 3.2, 0.8, "lower", "Bisagra"),
    ex("Peso Muerto Rumano", "Kettlebell", "C", 3.2, 1.0, "lower", "Bisagra"),
    ex("Peso Muerto Rumano", "Mancuernas", "C", 3.5, 1.2, "lower", "Bisagra"),
    ex("Peso Muerto Rumano", "Peso Corporal", "C", 2.0, 0.3, "lower", "Bisagra"),
    ex("Buenos Días", "Smith", "C", 3.5, 1.5, "lower", "Bisagra"),
    ex("Buenos Días", "Peso Corporal", "C", 2.0, 0.5, "lower", "Bisagra"),
    ex("Hip Thrust", "Mancuernas", "C", 3.0, 0.3, "lower", "Bisagra"),
    ex("Hip Thrust", "Kettlebell", "C", 2.8, 0.3, "lower", "Bisagra"),
    ex("Hip Thrust", "Polea", "C", 2.8, 0.2, "lower", "Bisagra"),
    ex("Hip Thrust", "Peso Corporal", "C", 2.0, 0.1, "lower", "Bisagra"),
    ex("Curl Femoral", "Polea", "A", 1.8, 0.0, "lower", "Flexión"),
    ex("Curl Femoral", "Peso Corporal (Nordic)", "C", 4.5, 0.5, "lower", "Flexión"),
    ex("Pull-Through", "Polea Alta", "A", 2.5, 0.2, "lower", "Bisagra"),
    ex("Puente de Glúteos", "Barra", "A", 2.5, 0.1, "lower", "Bisagra"),
    ex("Puente de Glúteos", "Mancuerna", "A", 2.2, 0.1, "lower", "Bisagra"),
    ex("Puente de Glúteos", "Kettlebell", "A", 2.2, 0.1, "lower", "Bisagra"),
    ex("Puente de Glúteos", "Peso Corporal", "A", 2.0, 0.1, "lower", "Bisagra"),

    # --- PIERNAS: PANTORRILLAS ---
    ex("Pantorrilla de Pie", "Smith", "A", 1.5, 0.4, "lower", "Extensión"),
    ex("Pantorrilla de Pie", "Barra", "A", 1.5, 0.5, "lower", "Extensión"),
    ex("Pantorrilla de Pie", "Mancuerna", "A", 1.4, 0.4, "lower", "Extensión"),
    ex("Pantorrilla de Pie", "Peso Corporal", "A", 1.2, 0.3, "lower", "Extensión"),
    ex("Pantorrilla Sentado", "Mancuerna", "A", 1.2, 0.0, "lower", "Extensión"),
    ex("Pantorrilla Sentado", "Barra", "A", 1.3, 0.0, "lower", "Extensión"),
    ex("Pantorrilla", "Polea", "A", 1.4, 0.2, "lower", "Extensión"),

    # --- BRAZOS: BÍCEPS ---
    ex("Curl con Barra", "Smith", "A", 1.8, 0.2, "upper", "Tirón"),
    ex("Curl con Barra", "Polea Baja", "A", 1.6, 0.1, "upper", "Tirón"),
    ex("Curl con Mancuernas", "Kettlebell", "A", 1.5, 0.1, "upper", "Tirón"),
    ex("Curl Martillo", "Polea", "A", 1.5, 0.0, "upper", "Tirón"),
    ex("Curl Martillo", "Kettlebell", "A", 1.5, 0.1, "upper", "Tirón"),
    ex("Curl Martillo", "Barra", "A", 1.6, 0.2, "upper", "Tirón"),
    ex("Curl Predicador", "Polea", "A", 1.7, 0.0, "upper", "Tirón"),
    ex("Curl Predicador", "Máquina", "A", 1.6, 0.0, "upper", "Tirón"),
    ex("Curl Concentrado", "Polea", "A", 1.2, 0.0, "upper", "Tirón"),
    ex("Curl Concentrado", "Kettlebell", "A", 1.2, 0.0, "upper", "Tirón"),
    ex("Curl 21", "Barra", "A", 1.8, 0.2, "upper", "Tirón"),
    ex("Curl Inverso", "Barra", "A", 1.6, 0.2, "upper", "Tirón"),
    ex("Curl Inverso", "Polea", "A", 1.5, 0.1, "upper", "Tirón"),

    # --- BRAZOS: TRÍCEPS ---
    ex("Press Francés", "Polea", "A", 2.0, 0.1, "upper", "Empuje"),
    ex("Press Francés", "Mancuerna", "A", 2.0, 0.1, "upper", "Empuje"),
    ex("Press Francés", "Kettlebell", "A", 1.8, 0.1, "upper", "Empuje"),
    ex("Extensión Tríceps", "Smith (Trasnuca)", "A", 2.0, 0.2, "upper", "Empuje"),
    ex("Extensión Tríceps", "Mancuerna (Unilateral)", "A", 1.5, 0.1, "upper", "Empuje"),
    ex("Extensión Tríceps", "Kettlebell", "A", 1.5, 0.1, "upper", "Empuje"),
    ex("Patada de Tríceps", "Polea", "A", 1.3, 0.0, "upper", "Empuje"),
    ex("Patada de Tríceps", "Kettlebell", "A", 1.2, 0.0, "upper", "Empuje"),
    ex("Fondos entre Bancos", "Peso Corporal", "A", 2.0, 0.1, "upper", "Empuje"),
    ex("Fondos entre Bancos", "Lastrados", "A", 2.5, 0.2, "upper", "Empuje"),
    ex("Extensión Tate", "Kettlebell", "A", 1.6, 0.1, "upper", "Empuje"),
    ex("JM Press", "Barra", "A", 2.5, 0.2, "upper", "Empuje"),
    ex("JM Press", "Smith", "A", 2.3, 0.1, "upper", "Empuje"),

    # --- CORE ---
    ex("Crunch", "Peso Corporal", "A", 1.5, 0.2, "full", "Flexión"),
    ex("Crunch", "Con Disco", "A", 1.8, 0.3, "full", "Flexión"),
    ex("Crunch", "Polea Alta", "A", 1.8, 0.3, "full", "Flexión"),
    ex("Crunch", "Máquina", "A", 1.7, 0.2, "full", "Flexión"),
    ex("Giros Rusos", "Disco", "A", 1.8, 0.3, "full", "Rotación"),
    ex("Giros Rusos", "Kettlebell", "A", 1.8, 0.3, "full", "Rotación"),
    ex("Giros Rusos", "Peso Corporal", "A", 1.5, 0.2, "full", "Rotación"),
    ex("Plancha", "Peso Corporal", "A", 1.5, 0.1, "full", "Anti-Extensión"),
    ex("Plancha", "Con Disco", "A", 1.6, 0.1, "full", "Anti-Extensión"),
    ex("Plancha Lateral", "Peso Corporal", "A", 1.5, 0.1, "full", "Anti-Rotación"),
    ex("Plancha Lateral", "Con Disco", "A", 1.6, 0.1, "full", "Anti-Rotación"),
    ex("Dead Bug", "Peso Corporal", "A", 1.2, 0.0, "full", "Anti-Extensión"),
    ex("Dead Bug", "Con Disco", "A", 1.3, 0.0, "full", "Anti-Extensión"),
    ex("Pallof Press", "Polea", "A", 1.5, 0.1, "full", "Anti-Rotación"),
    ex("Pallof Press", "Banda", "A", 1.5, 0.1, "full", "Anti-Rotación"),
    ex("Elevación de Piernas", "Peso Corporal", "C", 2.5, 0.2, "full", "Flexión"),
    ex("Elevación de Piernas", "Con Disco", "C", 2.8, 0.3, "full", "Flexión"),
    ex("Sit-Up", "Peso Corporal", "A", 1.5, 0.4, "full", "Flexión"),
    ex("Sit-Up", "Con Disco", "A", 1.8, 0.5, "full", "Flexión"),
    ex("Leñador", "Polea Alta", "C", 2.0, 0.4, "full", "Rotación"),
    ex("Leñador", "Polea Baja", "C", 2.0, 0.4, "full", "Rotación"),

    # --- CARGAS / CARRY ---
    ex("Paseo del Granjero", "Mancuernas", "C", 3.5, 1.5, "full", "Otro"),
    ex("Paseo del Granjero", "Kettlebell", "C", 3.5, 1.5, "full", "Otro"),
    ex("Paseo del Granjero", "Trap Bar", "C", 4.0, 1.8, "full", "Otro"),
    ex("Paseo del Camarero", "Kettlebell", "C", 2.8, 1.2, "full", "Otro"),
    ex("Paseo del Camarero", "Disco", "C", 2.5, 1.0, "full", "Otro"),
    ex("Suitcase Carry", "Kettlebell", "C", 3.0, 1.0, "full", "Anti-Rotación"),
    ex("Suitcase Carry", "Mancuerna", "C", 3.0, 1.0, "full", "Anti-Rotación"),

    # --- GLÚTEOS / ABDUCTORES ---
    ex("Abducción de Cadera", "Polea", "A", 1.4, 0.0, "lower", "Otro"),
    ex("Abducción de Cadera", "Banda", "A", 1.2, 0.0, "lower", "Otro"),
    ex("Abducción de Cadera", "Peso Corporal", "A", 1.2, 0.0, "lower", "Otro"),
    ex("Patada de Glúteo", "Polea", "A", 1.8, 0.1, "lower", "Extensión"),
    ex("Patada de Glúteo", "Máquina", "A", 1.6, 0.0, "lower", "Extensión"),
    ex("Patada de Glúteo", "Peso Corporal", "A", 1.2, 0.0, "lower", "Extensión"),
    ex("Hip Abduction", "Máquina", "A", 1.2, 0.0, "lower", "Otro"),
    ex("Hip Adduction", "Máquina", "A", 1.2, 0.0, "lower", "Otro"),
    ex("Hip Adduction", "Polea", "A", 1.2, 0.0, "lower", "Otro"),

    # --- OLÍMPICOS / POTENCIA ---
    ex("Cargada de Potencia", "Barra", "C", 4.8, 1.8, "full", "Otro"),
    ex("Cargada de Potencia", "Kettlebell", "C", 3.5, 1.2, "full", "Otro"),
    ex("Arrancada", "Barra", "C", 4.8, 1.6, "full", "Otro"),
    ex("Arrancada", "Kettlebell", "C", 3.5, 1.0, "full", "Otro"),
    ex("Push Press", "Mancuernas", "C", 3.8, 1.2, "upper", "Empuje"),
    ex("Push Press", "Kettlebell", "C", 3.5, 1.0, "upper", "Empuje"),
    ex("Push Jerk", "Barra", "C", 4.5, 1.4, "upper", "Empuje"),
    ex("Push Jerk", "Kettlebell", "C", 3.8, 1.0, "upper", "Empuje"),
    ex("High Pull", "Kettlebell", "C", 3.5, 1.0, "upper", "Tirón"),
    ex("High Pull", "Mancuernas", "C", 3.2, 0.8, "upper", "Tirón"),
]

def main():
    root = Path(__file__).resolve().parent.parent
    ext_path = root / "data" / "exerciseDatabaseExtended.json"
    
    # Cargar existentes
    if ext_path.exists():
        with open(ext_path, "r", encoding="utf-8") as f:
            existing = json.load(f)
    else:
        existing = []
    
    existing_ids = {e["id"] for e in existing}
    added = []
    for v in VARIANTES:
        # Asegurar ID único
        base_id = v["id"]
        c = 1
        while v["id"] in existing_ids:
            v["id"] = f"{base_id}_{c}"
            c += 1
        existing_ids.add(v["id"])
        added.append(v)
    
    merged = existing + added
    with open(ext_path, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)
    
    print(f"Base existente: {len(existing)} ejercicios")
    print(f"Añadidas {len(added)} variantes nuevas")
    print(f"Total: {len(merged)} ejercicios -> {ext_path}")

if __name__ == "__main__":
    main()
