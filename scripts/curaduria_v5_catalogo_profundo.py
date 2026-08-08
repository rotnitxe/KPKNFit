#!/usr/bin/env python3
"""Curaduría v5: copy editorial profunda y ficha articular por configuración.

La fuente de revisión es ``catalog/exercises/v2/source/families``. Esta pasada
no genera combinaciones nuevas: solo enriquece cada configuración ya aprobada
con texto específico para sus ejes, notas musculares contextualizadas y una
lista explícita de articulaciones implicadas.

Después de ejecutar este script:

    python scripts/merge_catalog_v2_families.py
    python scripts/compile_exercise_catalog_v2_cli.py --write
    python scripts/catalog_v2_gate.py --strict

El script es deliberadamente determinista para que la curaduría pueda
revisarse y repetirse sin editar a mano el asset de runtime.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
REVISION = "v2-approved-2026-08-08-b"
ONTOLOGY = "wikilab-v3-2026-08-08"


EQUIPMENT_LABELS = {
    "barbell": "barra",
    "dumbbells": "mancuernas",
    "machine": "máquina",
    "cable": "polea",
    "bodyweight": "peso corporal",
    "plate": "disco",
    "band": "banda elástica",
    "kettlebell": "kettlebell",
    "ez_bar": "barra EZ",
    "h_bar": "barra H",
    "hex_bar": "barra hexagonal",
    "smith_machine": "máquina Smith",
    "safety_bar": "barra de seguridad",
    "t_bar": "barra T",
    "trx": "TRX",
    "sliders": "deslizadores",
    "ghd": "GHD",
    "ab_wheel": "rueda abdominal",
    "wrist_roller": "rodillo de muñeca",
}

OPTION_LABELS = {
    "grip_type": {"neutral": "agarre neutro", "pronated": "agarre prono", "supinated": "agarre supino"},
    "grip_width": {"close": "agarre cerrado", "medium": "agarre medio", "wide": "agarre amplio"},
    "laterality": {"bilateral": "trabajo bilateral", "unilateral": "trabajo unilateral"},
    "stance": {"bilateral": "apoyo bilateral", "unilateral": "apoyo unilateral"},
    "station": {"seated": "posición sentada", "standing": "posición de pie"},
    "pulley_height": {"high": "polea alta", "mid": "polea media", "low": "polea baja"},
    "support_angle": {"flat": "apoyo plano", "feet_elevated": "pies elevados"},
}

MUSCLE_LABELS = {
    "pectoralis": "el pectoral",
    "deltoid": "el deltoides",
    "triceps": "el tríceps",
    "biceps": "el bíceps",
    "forearm": "el antebrazo",
    "latissimus_dorsi": "el dorsal",
    "trapezius": "el trapecio",
    "rhomboids": "los romboides",
    "erector_spinae": "los erectores espinales",
    "hamstrings": "los isquiosurales",
    "gluteus_maximus": "el glúteo mayor",
    "gluteus_medius": "el glúteo medio",
    "quadriceps": "los cuádriceps",
    "calves": "la pantorrilla",
    "tibialis_anterior": "el tibial anterior",
    "hip_flexors": "los flexores de cadera",
    "adductors": "los aductores",
    "abdominals": "el recto abdominal",
    "core": "el core",
    "neck": "la musculatura cervical",
    "tensor_fasciae_latae": "el tensor de la fascia lata",
}

JOINT_LABELS = {
    "glenohumeral": "hombro",
    "acromioclavicular": "articulación acromioclavicular",
    "esternoclavicular": "articulación esternoclavicular",
    "escapulotoracica": "articulación escapulotorácica",
    "codo": "codo",
    "radiocubital-proximal": "radiocubital proximal",
    "muñeca": "muñeca",
    "columna-cervical": "columna cervical",
    "columna-toracica": "columna torácica",
    "columna-lumbar": "columna lumbar",
    "sacroiliaca": "articulación sacroilíaca",
    "cadera": "cadera",
    "rodilla": "rodilla",
    "tobillo": "tobillo",
    "subtalar": "articulación subastragalina",
}

JOINT_TENDONS = {
    "glenohumeral": ["tendon-supraespinoso", "tendon-infraespinoso", "tendon-bíceps-largo"],
    "codo": ["tendon-bíceps", "tendon-tríceps"],
    "rodilla": ["tendon-rotuliano", "tendon-cuádriceps", "tendon-isquiotibiales"],
    "tobillo": ["tendon-aquiles"],
    "cadera": ["tendon-iliopsoas"],
    "muñeca": ["tendon-flexores-muñeca", "tendon-extensores-muñeca"],
}

ROLE_LABELS = {
    "PRIMARY": "Principal",
    "SECONDARY": "Secundaria",
    "STABILIZER": "Estabilizadora",
}


def sentence_case(value: str) -> str:
    """Normalize the first visible letter without changing Spanish sentence case."""
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    if not text:
        return text
    for index, character in enumerate(text):
        if character.isalpha():
            return text[:index] + character.upper() + text[index + 1:]
    return text


def polished_case(value: str) -> str:
    """Capitalize the beginning of every visible sentence or labeled clause."""
    text = sentence_case(value)
    return re.sub(
        r"([.!?;:]\s+)([a-záéíóúüñ])",
        lambda match: match.group(1) + match.group(2).upper(),
        text,
    )


def title_label(value: str) -> str:
    """Use readable title casing for chips and compact anatomical labels."""
    words = re.split(r"(\s+|[-/])", str(value or "").strip())
    return "".join(
        word[:1].upper() + word[1:] if word and not word.isspace() and word not in {"-", "/"} else word
        for word in words
    )


def accessible_anatomy(value: str) -> str:
    """Remove jargon that belongs in internal analysis, not in user-facing copy."""
    replacements = {
        "torque": "carga",
        "vector": "dirección de la resistencia",
        "palanca": "posición",
        "ventaja mecánica": "ayuda",
        "centro de masa": "centro del cuerpo",
        "lumbopélvica": "de la zona media",
        "lumbopélvico": "de la zona media",
        "costopélvico": "entre costillas y pelvis",
        "húmero": "brazo",
        "humeral": "del brazo",
        "radiocubital": "del antebrazo",
        "hiperextensión": "arqueo excesivo",
    }
    result = value
    for source, replacement in replacements.items():
        result = re.sub(rf"\b{re.escape(source)}\b", replacement, result, flags=re.IGNORECASE)
    return polished_case(result)


def without_canonical_name(value: str, canonical_name: str) -> str:
    """Keep the card title as the only place where the canonical name appears."""
    replacements = {
        "rueda abdominal": "esta rueda",
        "inclinación lateral": "inclinación del tronco",
        "tibial anterior": "parte frontal de la pierna",
    }
    name_key = canonical_name.casefold().strip()
    replacement = replacements.get(name_key, "este movimiento")
    return re.sub(rf"(?<!\w){re.escape(canonical_name)}(?!\w)", replacement, value, flags=re.IGNORECASE)


PATTERN_GROUPS = {
    "horizontal_push": {"horizontal_push"},
    "horizontal_pull": {"horizontal_pull"},
    "vertical_push": {"vertical_push", "diagonal_push"},
    "vertical_pull": {"vertical_pull", "vertical_pull_abduction"},
    "elbow_flexion": {"elbow_flexion"},
    "elbow_extension": {"elbow_extension"},
    "shoulder_raise": {"shoulder_abduction", "shoulder_abduction_diagonal", "shoulder_abduction_full_rom", "shoulder_flexion", "horizontal_abduction"},
    "hip_hinge": {"hip_hinge", "hip_hinge_lengthened", "hip_hinge_deficit", "romanian_deadlift", "romanian_deadlift_deficit", "deadlift", "biarticular_lengthened"},
    "explosive_hinge": {"hip_hinge_explosive"},
    "knee_dominant": {"knee_dominant", "knee_dominant_lengthened", "knee_extension", "knee_hip_dominant", "knee_hip_extension", "knee_hip_flexion", "eccentric_knee_flexion"},
    "unilateral_knee": {"unilateral_knee_dominant", "unilateral_knee_dominant_asymmetric", "knee_dominant_asymmetric", "lateral_knee_dominant"},
    "hip_extension": {"hip_extension", "hip_extension_abduction", "hip_extension_external_rotation", "unilateral_hip_dominant"},
    "hip_abduction": {"hip_abduction", "hip_abduction_extension", "hip_abduction_external_rotation", "hip_abduction_stability"},
    "hip_adduction": {"hip_adduction", "hip_adduction_dynamic"},
    "hip_flexion": {"hip_flexion"},
    "ankle": {"ankle_dorsiflexion", "plantar_flexion"},
    "trunk_anti": {"anti_extension_isometric", "anti_extension_pelvic_control", "anti_extension_trunk", "anti_rotation_trunk", "isometric_grip", "pinch_grip"},
    "trunk_flexion": {"trunk_flexion", "spinal_flexion"},
    "trunk_extension": {"spinal_extension"},
    "trunk_rotation": {"trunk_rotation"},
    "trunk_lateral": {"lateral_trunk_flexion"},
    "neck": {"neck_extension", "neck_flexion", "neck_lateral_flexion"},
    "wrist": {"wrist_extension", "wrist_flexion", "wrist_flexion_extension"},
    "scapula": {"scapular_depression", "scapular_elevation"},
}

PATTERN_COPY = {
    "horizontal_push": {"summary": "Empujas la carga hacia delante del pecho y estiras los brazos para trabajar pecho y tríceps.", "technique": "Apoya bien la espalda, deja las manos sobre los codos y mueve la carga sin rebotes.", "benefit": "trabajar pecho y tríceps con una referencia sencilla para progresar", "action": "empuje del brazo y extensión del codo", "mistake": "Abrir demasiado los codos, rebotar la carga o doblar la muñeca hacia atrás."},
    "horizontal_pull": {"summary": "Llevas las manos hacia el cuerpo para trabajar la espalda y la parte posterior del hombro.", "technique": "Deja el pecho apoyado o el torso firme, tira con los codos y vuelve sin impulso.", "benefit": "dar grosor a la espalda y mantener un movimiento fácil de repetir", "action": "tirón del brazo y flexión del codo", "mistake": "Tirar solo con las manos, encoger los hombros o balancear el cuerpo."},
    "vertical_push": {"summary": "Empujas la carga por encima de la cabeza para trabajar hombros y tríceps.", "technique": "Mantén las manos sobre los codos, aprieta el abdomen y sube sin arquear la espalda.", "benefit": "desarrollar los hombros y completar el empuje con ayuda del tríceps", "action": "empuje del brazo por encima de la cabeza y extensión del codo", "mistake": "Arquear la zona lumbar, usar las piernas para impulsarte o bajar detrás de la cabeza sin control."},
    "vertical_pull": {"summary": "Bajas la carga hacia el pecho o el cuerpo para trabajar dorsales y espalda alta.", "technique": "Mantén el pecho abierto, baja los codos y deja que la carga vuelva de forma controlada.", "benefit": "fortalecer dorsales y espalda alta con una resistencia fácil de ajustar", "action": "tirón hacia abajo y flexión del codo", "mistake": "Balancear el cuerpo, encoger los hombros o convertir el tirón en un simple movimiento de brazos."},
    "elbow_flexion": {"summary": "Acercas la mano al hombro sin mover demasiado el brazo para trabajar bíceps y braquial.", "technique": "Deja el brazo quieto, mueve el codo y evita ayudarte con la espalda.", "benefit": "trabajar los flexores del codo con un recorrido claro y fácil de comparar", "action": "flexión del codo y giro del antebrazo según el agarre", "mistake": "Llevar el hombro hacia delante, balancear el tronco o perder la posición de la muñeca."},
    "elbow_extension": {"summary": "Estiras el codo para trabajar el tríceps y completar el empuje del brazo.", "technique": "Mantén el brazo superior estable y extiende el codo sin abrirlo hacia los lados.", "benefit": "cargar el tríceps de forma directa y ajustar la dificultad con precisión", "action": "extensión del codo", "mistake": "Mover el hombro, separar el codo o usar el tronco para ganar recorrido."},
    "shoulder_raise": {"summary": "Elevas el brazo para trabajar una zona concreta del hombro sin convertir el movimiento en un encogimiento.", "technique": "Sube en el plano elegido, mantén el cuello relajado y detente antes de perder el control.", "benefit": "dirigir el trabajo a una parte del hombro con una carga moderada y controlable", "action": "elevación del brazo y acompañamiento de la escápula", "mistake": "Subir con impulso, encoger los hombros o forzar una amplitud que cambie la trayectoria."},
    "hip_hinge": {"summary": "Llevas la cadera hacia atrás y vuelves a ponerte de pie para trabajar glúteos e isquiosurales.", "technique": "Mantén la carga cerca, flexiona un poco las rodillas y mueve el tronco junto con la cadera.", "benefit": "fortalecer la cadena posterior con una progresión de carga muy clara", "action": "extensión de cadera y control de la rodilla", "mistake": "Redondear la zona lumbar, alejar la carga o convertir la bisagra en una sentadilla."},
    "explosive_hinge": {"summary": "Impulsas la carga con una extensión rápida de cadera para entrenar potencia.", "technique": "La fuerza nace de los pies y la cadera; los brazos acompañan y la espalda no termina arqueada.", "benefit": "desarrollar potencia de cadera y coordinación con una carga moderada", "action": "extensión rápida de cadera y control del tronco", "mistake": "Convertir el gesto en una sentadilla, levantar la carga solo con los brazos o arquear la espalda."},
    "knee_dominant": {"summary": "Flexionas y estiras las rodillas para trabajar sobre todo los cuádriceps.", "technique": "Mantén el pie completo apoyado y deja que la rodilla apunte hacia los dedos.", "benefit": "acumular trabajo de cuádriceps graduando profundidad, apoyo y carga", "action": "flexión y extensión de la rodilla con ayuda de cadera y tobillo", "mistake": "Dejar caer las rodillas hacia dentro, perder el apoyo del pie o inclinar demasiado el tronco."},
    "unilateral_knee": {"summary": "Una pierna hace la mayor parte del trabajo mientras mantienes el equilibrio y el control de la pelvis.", "technique": "Apoya bien el pie de trabajo, mantén la pelvis nivelada y usa la pierna libre solo como apoyo si hace falta.", "benefit": "trabajar cada pierna por separado y detectar diferencias de fuerza o control", "action": "extensión de una pierna y control del tobillo y la pelvis", "mistake": "Impulsarte con la pierna libre, dejar caer la pelvis o perder la línea de la rodilla."},
    "hip_extension": {"summary": "Extiendes la cadera para llevar la pelvis hacia delante y trabajar glúteos e isquiosurales.", "technique": "Termina con el cuerpo alineado y no busques altura arqueando la zona lumbar.", "benefit": "concentrar el trabajo en la extensión de cadera sin exigir demasiado a la rodilla", "action": "extensión de cadera y control de la pelvis", "mistake": "Hiperextender la zona lumbar, abrir demasiado las costillas o mover la carga con rebote."},
    "hip_abduction": {"summary": "Separas la pierna de la línea media para trabajar el glúteo medio y la estabilidad lateral de la cadera.", "technique": "Mantén la pelvis nivelada, mueve la pierna sin girarla y detente antes de inclinar el tronco.", "benefit": "fortalecer la cadera lateral y mejorar el control de la pelvis", "action": "separación de la pierna y estabilidad pélvica", "mistake": "Inclinar el tronco, girar la pelvis o lanzar la pierna más allá del rango útil."},
    "hip_adduction": {"summary": "Acercas la pierna a la línea media para trabajar la cara interna del muslo.", "technique": "Mantén el tronco estable, acerca la pierna sin tirón y vuelve conservando tensión.", "benefit": "fortalecer aductores y control de la cadera en un recorrido localizado", "action": "acercamiento de la pierna y estabilidad de la pelvis", "mistake": "Cerrar la pierna con impulso, despegar la pelvis o girar la cadera."},
    "hip_flexion": {"summary": "Acercas el muslo al tronco para trabajar los flexores de cadera.", "technique": "Mantén la pelvis estable, mueve la pierna sin inclinar el tronco y vuelve con control.", "benefit": "trabajar la flexión de cadera con una resistencia sencilla de dosificar", "action": "flexión de cadera y estabilidad de la pelvis", "mistake": "Inclinar el tronco, redondear la espalda o levantar la pierna con impulso."},
    "ankle": {"summary": "Mueves el pie desde el tobillo para fortalecer la pantorrilla o el tibial anterior.", "technique": "Mueve el pie de forma completa y pausada, sin rebotar ni dejar que la rodilla haga el trabajo.", "benefit": "mejorar la fuerza del tobillo y la transferencia hacia la pierna", "action": "movimiento del pie desde el tobillo y control del apoyo", "mistake": "Rebotar, girar los pies o mover la rodilla en vez del tobillo."},
    "trunk_anti": {"summary": "Evitas que la carga te arquee, te gire o te incline; el trabajo principal es mantenerte firme.", "technique": "Junta costillas y pelvis, respira y evita que la espalda cambie de forma durante el recorrido.", "benefit": "mejorar la capacidad del core para transferir fuerza sin movimientos innecesarios", "action": "resistencia del tronco frente a extensión, giro o inclinación", "mistake": "Arquear la espalda, girar con la carga o perder la tensión al final."},
    "trunk_flexion": {"summary": "Acercas el torso y la pelvis para trabajar el abdomen.", "technique": "Mueve el tronco de forma controlada, sin tirar del cuello ni balancear las piernas.", "benefit": "fortalecer el abdomen con una resistencia fácil de progresar", "action": "flexión controlada del tronco", "mistake": "Tirar del cuello, hacer el movimiento desde la cadera o usar rebote."},
    "trunk_extension": {"summary": "Vuelves a enderezar el torso para trabajar la espalda baja y los glúteos.", "technique": "Sube hasta quedar alineado y no busques más altura arqueando la zona lumbar.", "benefit": "fortalecer la cadena posterior con un recorrido que se puede graduar", "action": "extensión del tronco y la cadera", "mistake": "Buscar altura con hiperextensión, mover solo la zona lumbar o perder el apoyo."},
    "trunk_rotation": {"summary": "Giras el torso de forma controlada para trabajar la zona media y coordinarla con la cadera.", "technique": "Gira desde el tronco, mantén la carga cerca y deja que la pelvis acompañe sin perder el apoyo.", "benefit": "mejorar la producción y el control de fuerza al girar", "action": "giro controlado del tronco", "mistake": "Girar solo desde la zona lumbar, lanzar la carga o perder la base de apoyo."},
    "trunk_lateral": {"summary": "Inclinas el torso hacia un lado y vuelves para trabajar los músculos laterales del abdomen.", "technique": "Mantén la pelvis quieta, usa un recorrido cómodo y vuelve sin rebote.", "benefit": "fortalecer la resistencia lateral del tronco y la estabilidad de la pelvis", "action": "inclinación lateral controlada del tronco", "mistake": "Girar en vez de inclinar, desplazar la pelvis o acelerar la vuelta."},
    "neck": {"summary": "Mueves o sostienes la cabeza contra una resistencia para trabajar el cuello.", "technique": "Usa un recorrido pequeño y fluido, sin tirones ni ayuda de los hombros.", "benefit": "mejorar la capacidad de la musculatura cervical con una carga graduable", "action": "movimiento o estabilización de la columna cervical", "mistake": "Usar velocidad, llevar la cabeza al extremo del recorrido o encoger los hombros."},
    "wrist": {"summary": "Flexionas o extiendes la muñeca para trabajar el antebrazo.", "technique": "Apoya o fija el antebrazo, mueve solo la muñeca y deja los dedos relajados.", "benefit": "desarrollar el antebrazo y la tolerancia de la muñeca con una carga localizada", "action": "flexión o extensión de la muñeca", "mistake": "Levantar el antebrazo, apretar demasiado los dedos o mover el codo."},
    "scapula": {"summary": "Subes o bajas los hombros sin doblar los codos para mejorar el control de la espalda alta.", "technique": "Deja que la escápula se mueva, mantén el cuello largo y no conviertas el ejercicio en un balanceo.", "benefit": "mejorar el control de la cintura escapular para empujes y tirones", "action": "movimiento de la escápula y estabilidad del hombro", "mistake": "Encoger el cuello, doblar los codos o balancear el tronco."},
}

FALLBACK_COPY = {
    "summary": "Mueves la carga siguiendo el gesto principal del ejercicio y repartes el esfuerzo entre las articulaciones que lo coordinan.",
    "technique": "Mantén el apoyo estable, usa un recorrido cómodo y evita que el impulso sustituya al movimiento objetivo.",
    "benefit": "desarrollar el grupo muscular objetivo con una técnica fácil de repetir",
    "action": "movimiento principal del ejercicio y estabilidad de las articulaciones cercanas",
    "mistake": "Acortar el recorrido, perder la posición inicial o usar impulso para superar la parte difícil.",
}


MUSCLE_ACTIONS = {
    "pectoralis": "aproxima el brazo al centro y comparte la flexión del hombro",
    "deltoid": "mueve el húmero en el plano que marca el ejercicio y centra el hombro",
    "triceps": "extiende el codo y completa la fase de empuje",
    "biceps": "flexiona el codo y, con agarre supino, ayuda a supinar el antebrazo",
    "forearm": "transmite el agarre y mantiene la muñeca alineada con la resistencia",
    "latissimus_dorsi": "extiende o aproxima el brazo al tronco desde la espalda",
    "trapezius": "coordina la posición de la escápula y reparte la fuerza en la cintura escapular",
    "rhomboids": "acerca y fija las escápulas para que el brazo tenga una base estable",
    "erector_spinae": "sostiene la columna frente a la flexión y transmite fuerza entre pelvis y tronco",
    "hamstrings": "extiende la cadera y frena el descenso cuando la cadera se flexiona",
    "gluteus_maximus": "extiende la cadera y estabiliza la pelvis en la subida",
    "gluteus_medius": "separa el muslo y evita que la pelvis caiga durante el apoyo",
    "quadriceps": "extiende la rodilla y absorbe la carga al descender",
    "calves": "produce o controla la flexión plantar para transferir fuerza al suelo",
    "tibialis_anterior": "eleva el antepié y regula el avance de la tibia sobre el pie",
    "hip_flexors": "acerca el muslo al tronco y ayuda a orientar la pelvis",
    "adductors": "aproxima el muslo y estabiliza la cadera frente a fuerzas laterales",
    "abdominals": "acerca costillas y pelvis o resiste la extensión del tronco",
    "core": "mantiene costillas, pelvis y columna como una unidad frente a fuerzas externas",
    "neck": "mueve o fija la columna cervical mientras el tronco ofrece una base estable",
    "tensor_fasciae_latae": "asiste la abducción y ayuda a estabilizar la cadera en el plano frontal",
}


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def pattern_group(pattern: str) -> str:
    for group, patterns in PATTERN_GROUPS.items():
        if pattern in patterns:
            return group
    return "fallback"


def option_label(axis: str, value: str) -> str:
    return OPTION_LABELS.get(axis, {}).get(value, value.replace("_", " "))


def variant_label(options: dict[str, str], equipment: str) -> str:
    labels = [title_label(EQUIPMENT_LABELS.get(equipment, equipment))]
    labels.extend(title_label(option_label(axis, value)) for axis, value in options.items() if axis != "implement")
    return " · ".join(dict.fromkeys(label for label in labels if label))


def equipment_rationale(equipment: str, group: str) -> tuple[str, str]:
    values = {
        "barbell": ("La barra reparte la carga entre ambos lados y ofrece una referencia estable para progresar.", "progresión bilateral y una carga fácil de comparar"),
        "dumbbells": ("Las mancuernas permiten que cada lado encuentre su propio camino y ayudan a detectar diferencias.", "libertad de movimiento y trabajo de simetría entre lados"),
        "cable": ("La polea mantiene tensión durante buena parte del recorrido y permite cambiar desde dónde tira.", "tensión más constante y ajuste fino de la dirección"),
        "machine": ("La máquina guía el movimiento y reduce la exigencia de equilibrio, para que puedas concentrarte en la zona que trabaja.", "más estabilidad para acercarte al esfuerzo sin gastar tanto en equilibrio"),
        "smith_machine": ("La máquina Smith guía la barra y da una base estable, aunque deja menos libertad para elegir el camino.", "estabilidad guiada y un recorrido fácil de repetir"),
        "band": ("La banda se siente más ligera al comienzo y ofrece más resistencia cuando se estira.", "un final más exigente y una opción fácil de transportar"),
        "bodyweight": ("Con el peso corporal, el ángulo y el apoyo son los que hacen el ejercicio más fácil o más difícil.", "ajuste de dificultad con tu propio cuerpo y control global"),
        "kettlebell": ("La kettlebell se siente compacta en la mano y exige cuidar el agarre cuando la carga cambia de posición.", "una carga cercana al cuerpo y una coordinación más dinámica"),
        "hex_bar": ("La barra hexagonal deja la carga a los lados del cuerpo y facilita empujar el suelo.", "una carga centrada y una salida cómoda desde el suelo"),
        "safety_bar": ("La barra de seguridad apoya la carga cerca del centro y deja las manos más libres.", "más comodidad para el hombro y otra sensación de apoyo"),
        "ez_bar": ("La barra EZ coloca las manos en un ángulo intermedio que suele resultar cómodo para muñeca y codo.", "un agarre amable para acumular trabajo de brazos"),
        "h_bar": ("La barra H mantiene las manos enfrentadas y reduce la necesidad de girar la muñeca.", "un trabajo de brazo estable y cómodo para el agarre"),
        "t_bar": ("La barra T fija la carga y ofrece una referencia clara para llevar los codos hacia atrás.", "un tirón estable para dar grosor a la espalda"),
        "plate": ("El disco ofrece un agarre sencillo y una carga corta, fácil de colocar cerca del cuerpo.", "un estímulo simple de dosificar y fácil de sentir"),
        "trx": ("El TRX convierte tu cuerpo en la carga y el ángulo en el regulador de dificultad.", "control global y ajuste inmediato de la intensidad"),
        "ghd": ("El GHD fija los apoyos y deja que la cadera o el tronco trabajen con un recorrido amplio.", "un recorrido largo con una referencia clara de posición"),
        "ab_wheel": ("La rueda hace el ejercicio más exigente cuanto más lejos llevas las manos del cuerpo.", "trabajo del core contra el arqueo con una progresión muy visible"),
        "wrist_roller": ("El rodillo mantiene la mano y el antebrazo trabajando durante todo el giro.", "resistencia prolongada para agarre y muñeca"),
    }
    return values.get(equipment, (f"El implemento {EQUIPMENT_LABELS.get(equipment, equipment)} cambia el apoyo y la sensación de carga.", "una configuración con una demanda de estabilidad propia"))


def equipment_technique(equipment: str) -> str:
    return {
        "barbell": "Mantén la barra centrada y las manos a una distancia simétrica.",
        "dumbbells": "Deja que cada mancuerna siga su propio camino y termina con ambos lados en una posición parecida.",
        "cable": "Mantén el cable tenso y deja que la mano siga la dirección del anclaje.",
        "machine": "Usa los apoyos de la máquina como referencia y no busques el recorrido con impulso.",
        "smith_machine": "Deja que la barra siga su guía y acomoda el cuerpo al recorrido fijo.",
        "band": "Mantén la banda con tensión desde el comienzo y nota cómo aumenta al estirarse.",
        "bodyweight": "Mueve el cuerpo como una unidad y ajusta la dificultad cambiando el ángulo de apoyo.",
        "kettlebell": "Mantén el asa alineada con la mano y evita que la carga se aleje sin necesidad.",
        "hex_bar": "Mantén las manos a los lados y deja que la carga suba cerca de las piernas.",
        "safety_bar": "Acomoda el torso a los apoyos de la barra y deja la carga cerca del centro.",
        "ez_bar": "Deja que el ángulo de las manos acompañe la línea del antebrazo.",
        "h_bar": "Mantén las manos enfrentadas y la muñeca alineada con el codo.",
        "t_bar": "Usa el soporte como referencia y orienta el pecho o el torso hacia él.",
        "plate": "Mantén el disco cerca del cuerpo y no cambies el agarre durante la repetición.",
        "trx": "Mantén las correas tensas y cambia el ángulo del cuerpo para ajustar la dificultad.",
        "ghd": "Usa los apoyos del GHD como referencia y mueve el cuerpo desde la cadera o el tronco.",
        "ab_wheel": "Avanza con el cuerpo en bloque y detente antes de que la espalda se arquee.",
        "wrist_roller": "Gira el rodillo con los dedos y la muñeca sin levantar el antebrazo.",
    }.get(equipment, "Mantén la carga alineada con la articulación que realiza el movimiento.")


def axis_rationale(axis: str, value: str, group: str) -> tuple[str, str, str]:
    text = OPTION_LABELS.get(axis, {}).get(value, value.replace("_", " "))
    if axis == "grip_type":
        values = {
            "supinated": ("El agarre supino coloca las palmas hacia ti y suele dar más ayuda al bíceps.", "más participación del bíceps y una posición cómoda para tirar", "Mantén las palmas hacia ti y mueve los codos sin perder la muñeca.") ,
            "pronated": ("El agarre prono coloca las palmas hacia delante y suele dejar más trabajo a la espalda y al antebrazo.", "más protagonismo de la espalda y del antebrazo", "Mantén las palmas hacia delante y el antebrazo alineado con la carga."),
            "neutral": ("El agarre neutro deja las palmas enfrentadas y suele resultar cómodo para hombro, codo y muñeca.", "un reparto equilibrado y una sensación cómoda para el codo", "Mantén las palmas enfrentadas y sigue la dirección natural del antebrazo."),
        }
        return values[value]
    if axis == "grip_width":
        values = {
            "wide": ("El agarre amplio deja los codos más abiertos y aumenta el trabajo de la espalda alta y el hombro posterior.", "más trabajo de espalda alta y una trayectoria de brazo más abierta", "Mantén los codos a la anchura elegida sin encoger los hombros."),
            "medium": ("El agarre medio busca un equilibrio entre recorrido del hombro y facilidad para mover la carga.", "un punto medio versátil para progresar y comparar cargas", "Deja las manos alineadas con los antebrazos y no acortes el recorrido."),
            "close": ("El agarre cerrado acerca los codos al cuerpo y suele favorecer el dorsal y el bíceps.", "más recorrido con los brazos junto al cuerpo y mayor ayuda del bíceps", "Lleva los codos cerca del costado sin adelantar el hombro."),
        }
        return values[value]
    if axis in {"laterality", "stance"}:
        if value == "unilateral":
            return ("Trabajar un lado a la vez permite detectar diferencias de fuerza y control.", "corregir asimetrías y exigir más estabilidad al lado que trabaja", "Completa el recorrido de un lado mientras la pelvis y el tronco siguen orientados hacia la carga.")
        return ("Trabajar con ambos lados reparte la carga y ofrece una base más estable para progresar.", "mover más carga total con menos exigencia de equilibrio lateral", "Comparte el recorrido entre ambos lados y conserva un apoyo simétrico.")
    if axis == "station":
        if value == "seated":
            return ("Sentarte reduce el trabajo de equilibrio y da más apoyo a la pelvis o al tronco.", "concentrarte más en el segmento que mueve la carga", "Usa el apoyo para sostener el tronco mientras se mueve la articulación objetivo.")
        return ("Trabajar de pie exige organizar los pies y llevar la fuerza desde el suelo hasta la zona que trabaja.", "sumar coordinación y estabilidad global al estímulo local", "Crea una base firme con los pies y deja que el tronco acompañe sin impulso.")
    if axis == "pulley_height":
        values = {
            "high": ("La polea alta tira hacia abajo y cambia el ejercicio cuando la resistencia empieza por encima del hombro.", "más tensión al comienzo del recorrido", "Sigue una diagonal descendente sin perder la posición del hombro."),
            "mid": ("La polea media coloca la resistencia a la altura del torso y reparte la dificultad de forma equilibrada.", "una sensación de tensión más uniforme", "Mantén la mano y el codo en el mismo plano al cruzar el torso."),
            "low": ("La polea baja tira desde el suelo y aumenta la dificultad cuando la cadera o el hombro se alejan del anclaje.", "más tensión cuando el músculo está estirado", "Deja que la resistencia tire desde abajo sin inclinar el cuerpo para crear recorrido.")
        }
        return values[value]
    if axis == "support_angle":
        if value == "feet_elevated":
            return ("Elevar los pies lleva más carga hacia la parte superior del cuerpo y hace el ejercicio más difícil.", "más trabajo relativo de hombros, pecho alto y core", "Forma una línea larga con el cuerpo y evita que la cintura se hunda." )
        return ("El apoyo plano reparte la carga y ofrece la referencia más sencilla para aprender el recorrido.", "una progresión base con menor exigencia", "Crea una base firme con manos y pies y mueve el cuerpo como una unidad.")
    return (f"La opción {text} cambia el apoyo o la dirección de la carga sin convertirlo en otro ejercicio.", f"un matiz de estímulo propio de {text}", f"Conserva la posición {text} durante todo el recorrido y deja que la carga siga su dirección natural.")


def variant_parts(options: dict[str, str], equipment: str, group: str) -> tuple[str, str, str, list[str]]:
    equipment_text, equipment_benefit = equipment_rationale(equipment, group)
    rationale = [equipment_text]
    benefits = [equipment_benefit]
    technique = []
    for axis, value in options.items():
        if axis == "implement":
            continue
        clause, benefit, cue = axis_rationale(axis, value, group)
        rationale.append(clause)
        benefits.append(benefit)
        technique.append(cue)
    return " ".join(rationale), "; ".join(dict.fromkeys(benefits)), " ".join(technique), benefits


def muscle_variant_detail(muscle: str, options: dict[str, str], equipment: str, group: str) -> str:
    if options.get("grip_type") == "supinated" and muscle in {"biceps", "forearm"}:
        return "El agarre supino da más ayuda al bíceps y suele hacer más evidente el trabajo cerca del hombro."
    if options.get("grip_type") == "pronated" and muscle == "biceps":
        return "El agarre prono le da menos ayuda y deja al bíceps como asistente de la flexión."
    if options.get("grip_width") == "wide" and muscle in {"trapezius", "rhomboids", "deltoid"}:
        return "El agarre amplio deja los codos más abiertos y aumenta su participación al llevarlos hacia atrás."
    if options.get("grip_width") == "close" and muscle in {"latissimus_dorsi", "biceps"}:
        return "El agarre cerrado acerca el codo al cuerpo y favorece un recorrido más largo junto al costado."
    if group == "horizontal_pull" and muscle == "latissimus_dorsi":
        width = options.get("grip_width")
        if width == "wide":
            return "El agarre amplio lleva el codo hacia fuera y deja que la espalda alta comparta más el trabajo."
        if width == "close":
            return "El agarre cerrado lleva el codo junto al cuerpo y permite trabajar el dorsal con más recorrido."
        return "El agarre medio equilibra el recorrido del hombro y la facilidad para cargar el tirón."
    if group == "vertical_pull" and muscle == "latissimus_dorsi":
        width = options.get("grip_width")
        grip = options.get("grip_type")
        return f"La combinación de {option_label('grip_type', grip) if grip else 'agarre'} y {option_label('grip_width', width) if width else 'anchura elegida'} cambia cuánto se acerca el codo al cuerpo."
    if options.get("pulley_height") and muscle in {"pectoralis", "deltoid", "latissimus_dorsi", "triceps"}:
        return f"La {option_label('pulley_height', options['pulley_height'])} cambia dónde se siente más la carga durante el recorrido."
    if options.get("laterality") == "unilateral" or options.get("stance") == "unilateral":
        return "La versión unilateral exige que ese lado produzca y estabilice sin que el otro robe el recorrido."
    if options.get("station") == "seated" and muscle in {"erector_spinae", "core", "abdominals"}:
        return "El apoyo sentado reduce su función de equilibrio y deja más atención en la transmisión de fuerza."
    if equipment in {"machine", "smith_machine"} and muscle in {"stabilizer", "erector_spinae", "core"}:
        return "El guiado externo reduce la demanda de estabilización y concentra su papel en mantener la posición."
    if equipment == "cable":
        return "La tensión de la polea mantiene su participación durante una parte amplia del recorrido."
    if equipment == "band":
        return "La banda se vuelve más exigente al final y hace más visible su participación en ese tramo."
    return f"En la configuración {variant_label(options, equipment)}, la posición y la dirección de la carga cambian cuánto aporta dentro del movimiento."


def joint_templates(group: str) -> list[tuple[str, str, list[str], str]]:
    templates = {
        "horizontal_push": [
            ("glenohumeral", "PRIMARY", ["aducción horizontal", "flexión del hombro"], "El hombro aproxima el brazo y recibe la mayor parte del torque del empuje."),
            ("codo", "SECONDARY", ["extensión"], "El codo extiende el brazo y completa el desplazamiento de la carga."),
            ("escapulotoracica", "STABILIZER", ["retracción y estabilidad escapular"], "La escápula ofrece una base estable para que el húmero empuje sin perder centrado."),
            ("muñeca", "STABILIZER", ["estabilidad en extensión"], "La muñeca transmite la fuerza de la mano al antebrazo y evita que la barra se desplace."),
        ],
        "horizontal_pull": [
            ("glenohumeral", "PRIMARY", ["extensión", "aducción del hombro"], "El hombro lleva el brazo hacia el tronco y concentra la dirección del tirón."),
            ("escapulotoracica", "PRIMARY", ["retracción escapular"], "La escápula se acerca y se fija sobre el tórax para sostener la tracción."),
            ("codo", "SECONDARY", ["flexión"], "El codo acerca la mano y comparte la fuerza sin sustituir al movimiento de espalda."),
            ("muñeca", "STABILIZER", ["estabilidad del agarre"], "La muñeca mantiene el contacto con el implemento para que la fuerza llegue al codo."),
        ],
        "vertical_push": [
            ("glenohumeral", "PRIMARY", ["flexión o abducción"], "El hombro eleva el brazo y recibe el torque principal del empuje vertical."),
            ("escapulotoracica", "SECONDARY", ["rotación superior"], "La escápula acompaña la elevación y preserva espacio para el movimiento del húmero."),
            ("codo", "SECONDARY", ["extensión"], "El codo termina de alejar la carga del cuerpo y eleva la altura final."),
            ("columna-lumbar", "STABILIZER", ["estabilidad lumbopélvica"], "La columna lumbar resiste la extensión para que el empuje no se convierta en arqueo."),
        ],
        "vertical_pull": [
            ("glenohumeral", "PRIMARY", ["aducción y extensión"], "El hombro acerca el brazo al costado y dirige el cuerpo o la carga hacia la barra."),
            ("escapulotoracica", "PRIMARY", ["depresión y rotación"], "La escápula desciende y se organiza para que el hombro tenga una base de tirón."),
            ("codo", "SECONDARY", ["flexión"], "El codo cierra el ángulo y transfiere la tracción desde la mano hacia la espalda."),
            ("muñeca", "STABILIZER", ["estabilidad del agarre"], "La muñeca sostiene la posición de la mano ante el peso corporal o la polea."),
        ],
        "elbow_flexion": [
            ("codo", "PRIMARY", ["flexión"], "El codo es el eje móvil principal y recibe la fuerza que acerca la mano al hombro."),
            ("radiocubital-proximal", "SECONDARY", ["pronación o supinación"], "La radiocubital proximal orienta la palma y cambia la ventaja de bíceps, braquial y antebrazo."),
            ("glenohumeral", "STABILIZER", ["centrado del húmero"], "El hombro fija el brazo para que la flexión no se transforme en un balanceo."),
            ("muñeca", "STABILIZER", ["alineación"], "La muñeca conserva la línea de carga y evita que el agarre se convierta en el movimiento principal."),
        ],
        "elbow_extension": [
            ("codo", "PRIMARY", ["extensión"], "El codo abre el brazo y concentra el trabajo mecánico del tríceps."),
            ("glenohumeral", "SECONDARY", ["posición del hombro"], "El hombro fija el brazo o lo coloca detrás de la cabeza según la variante."),
            ("muñeca", "STABILIZER", ["alineación"], "La muñeca transmite la presión sin doblarse bajo el implemento."),
        ],
        "shoulder_raise": [
            ("glenohumeral", "PRIMARY", ["abducción o flexión"], "El hombro eleva el húmero y determina el plano de la variante."),
            ("escapulotoracica", "SECONDARY", ["rotación superior"], "La escápula acompaña la elevación para repartir el movimiento y la carga."),
            ("acromioclavicular", "SECONDARY", ["ajuste escapular"], "La articulación acromioclavicular permite que la escápula cambie de orientación durante la elevación."),
            ("codo", "STABILIZER", ["extensión relativa"], "El codo mantiene una palanca consistente para que el hombro sea quien defina el gesto."),
        ],
        "hip_hinge": [
            ("cadera", "PRIMARY", ["flexión y extensión"], "La cadera recibe el torque principal y vuelve a extenderse para elevar el tronco y la carga."),
            ("rodilla", "SECONDARY", ["flexión ligera y estabilidad"], "La rodilla ajusta la altura de la cadera y comparte la salida sin dominar el gesto."),
            ("columna-lumbar", "STABILIZER", ["resistencia a la flexión"], "La columna lumbar transmite la carga y resiste que el tronco se redondee."),
            ("sacroiliaca", "STABILIZER", ["transferencia pelvis-tronco"], "La articulación sacroilíaca enlaza la fuerza de piernas y tronco mientras la pelvis cambia de ángulo."),
        ],
        "explosive_hinge": [
            ("cadera", "PRIMARY", ["extensión explosiva"], "La cadera genera la velocidad que proyecta la carga sin depender de los brazos."),
            ("columna-lumbar", "STABILIZER", ["rigidez del tronco"], "La columna lumbar mantiene una base firme para que la potencia nazca de la cadera."),
            ("rodilla", "SECONDARY", ["extensión coordinada"], "La rodilla acompaña la salida y conecta el empuje del suelo con la extensión de cadera."),
            ("tobillo", "STABILIZER", ["transferencia al suelo"], "El tobillo transmite la fuerza al suelo y conserva la base durante el cambio de dirección."),
        ],
        "knee_dominant": [
            ("rodilla", "PRIMARY", ["flexión y extensión"], "La rodilla produce la mayor parte de la subida y recibe el torque dominante."),
            ("cadera", "SECONDARY", ["flexión y extensión"], "La cadera acompaña la profundidad y ayuda a salir sin que la rodilla pierda su línea."),
            ("tobillo", "STABILIZER", ["dorsiflexión y control"], "El tobillo permite que la tibia avance y mantiene el pie conectado al suelo."),
            ("columna-lumbar", "STABILIZER", ["estabilidad del tronco"], "La columna lumbar resiste la flexión para que la carga no colapse el torso."),
        ],
        "unilateral_knee": [
            ("rodilla", "PRIMARY", ["flexión y extensión unilateral"], "La rodilla de trabajo produce la subida y absorbe la mayor parte de la carga."),
            ("cadera", "PRIMARY", ["extensión y control frontal"], "La cadera mantiene la pelvis nivelada y evita que la pierna se desplace hacia dentro."),
            ("tobillo", "SECONDARY", ["equilibrio y dorsiflexión"], "El tobillo ajusta el apoyo de una sola pierna y absorbe cambios pequeños de equilibrio."),
            ("sacroiliaca", "STABILIZER", ["transferencia unilateral"], "La pelvis transfiere la fuerza entre ambos lados mientras la pierna libre permanece disponible."),
        ],
        "hip_extension": [
            ("cadera", "PRIMARY", ["extensión"], "La cadera lleva la pelvis hacia delante y concentra la salida del movimiento."),
            ("rodilla", "SECONDARY", ["estabilidad"], "La rodilla ofrece una base firme para que el gesto ocurra sobre todo en la cadera."),
            ("columna-lumbar", "STABILIZER", ["alineación"], "La columna lumbar transmite la extensión sin convertirse en el origen del recorrido."),
            ("sacroiliaca", "STABILIZER", ["control pélvico"], "La articulación sacroilíaca coordina pelvis y tronco al final de la extensión."),
        ],
        "hip_abduction": [
            ("cadera", "PRIMARY", ["abducción"], "La cadera separa el muslo y dirige la fuerza del movimiento lateral."),
            ("sacroiliaca", "SECONDARY", ["estabilidad pélvica"], "La pelvis conserva su orientación para que la pierna se mueva sin inclinar el tronco."),
            ("rodilla", "STABILIZER", ["alineación del miembro inferior"], "La rodilla mantiene el fémur orientado y evita que la abducción termine en torsión."),
        ],
        "hip_adduction": [
            ("cadera", "PRIMARY", ["aducción"], "La cadera acerca el muslo a la línea media y concentra la acción de los aductores."),
            ("sacroiliaca", "SECONDARY", ["control de pelvis"], "La pelvis ofrece una base estable mientras la pierna cruza hacia dentro."),
            ("rodilla", "STABILIZER", ["alineación"], "La rodilla acompaña el trayecto del fémur sin convertirse en el eje del gesto."),
        ],
        "hip_flexion": [
            ("cadera", "PRIMARY", ["flexión"], "La cadera acerca el muslo al tronco y recibe la mayor parte de la resistencia."),
            ("columna-lumbar", "STABILIZER", ["estabilidad lumbopélvica"], "La columna lumbar conserva la pelvis organizada para que el flexor no tire del tronco."),
            ("rodilla", "STABILIZER", ["posición del miembro"], "La rodilla acompaña el ángulo de la pierna sin robar el movimiento a la cadera."),
        ],
        "ankle": [
            ("tobillo", "PRIMARY", ["flexión plantar o dorsiflexión"], "El tobillo mueve el pie y recibe la carga principal del ejercicio."),
            ("subtalar", "SECONDARY", ["ajuste del apoyo"], "La articulación subastragalina adapta el pie al suelo y conserva el contacto estable."),
            ("rodilla", "STABILIZER", ["transmisión"], "La rodilla mantiene la pierna organizada para que el movimiento no suba de nivel."),
        ],
        "trunk_anti": [
            ("columna-lumbar", "PRIMARY", ["anti-extensión o anti-rotación"], "La columna lumbar resiste el movimiento que la carga intenta imponer."),
            ("sacroiliaca", "SECONDARY", ["control de pelvis"], "La pelvis conecta las fuerzas del suelo y del tronco sin perder orientación."),
            ("columna-toracica", "STABILIZER", ["posición torácica"], "La columna torácica aporta una base rígida para la acción del core."),
        ],
        "trunk_flexion": [
            ("columna-lumbar", "PRIMARY", ["flexión controlada"], "La columna lumbar participa en el cierre del tronco dentro del rango elegido."),
            ("columna-toracica", "SECONDARY", ["flexión torácica"], "La columna torácica acompaña la aproximación de costillas y pelvis."),
            ("cadera", "STABILIZER", ["posición pélvica"], "La cadera fija la pelvis para que el abdomen no compense con las piernas."),
        ],
        "trunk_extension": [
            ("columna-lumbar", "PRIMARY", ["extensión"], "La columna lumbar vuelve a extenderse con una carga progresiva sobre la cadena posterior."),
            ("cadera", "SECONDARY", ["extensión"], "La cadera ayuda a enderezar el tronco y reparte la salida con los glúteos."),
            ("sacroiliaca", "STABILIZER", ["control pélvico"], "La pelvis regula la transición entre flexión y extensión sin perder la base."),
        ],
        "trunk_rotation": [
            ("columna-toracica", "PRIMARY", ["rotación"], "La columna torácica aporta la mayor parte del giro útil del tronco."),
            ("columna-lumbar", "STABILIZER", ["limitación de rotación"], "La columna lumbar limita el giro accesorio y protege la transferencia de fuerza."),
            ("cadera", "SECONDARY", ["acompañamiento"], "La cadera acompaña la rotación para que el tronco no gire aislado sobre la pelvis."),
        ],
        "trunk_lateral": [
            ("columna-lumbar", "PRIMARY", ["flexión lateral"], "La columna lumbar aproxima el costado al tiempo que resiste el exceso de inclinación."),
            ("sacroiliaca", "SECONDARY", ["estabilidad pélvica"], "La pelvis mantiene la base y evita que la inclinación salga de todo el cuerpo."),
            ("columna-toracica", "STABILIZER", ["alineación"], "La columna torácica acompaña el arco sin convertirlo en una rotación."),
        ],
        "neck": [
            ("columna-cervical", "PRIMARY", ["flexión, extensión o inclinación"], "La columna cervical ejecuta el movimiento y recibe la carga específica del ejercicio."),
            ("columna-toracica", "STABILIZER", ["base torácica"], "La columna torácica ofrece una base para que el cuello no mueva todo el tronco."),
        ],
        "wrist": [
            ("muñeca", "PRIMARY", ["flexión o extensión"], "La muñeca genera el movimiento y concentra la resistencia en el antebrazo."),
            ("codo", "SECONDARY", ["estabilidad"], "El codo fija la longitud de la palanca para que la mano no arrastre el brazo entero."),
            ("radiocubital-proximal", "STABILIZER", ["orientación del antebrazo"], "La radiocubital proximal orienta el antebrazo y permite que la muñeca trabaje en su plano."),
        ],
        "scapula": [
            ("escapulotoracica", "PRIMARY", ["elevación o depresión"], "La escápula se desplaza sobre el tórax y concentra la acción del ejercicio."),
            ("glenohumeral", "STABILIZER", ["centrado del hombro"], "El hombro mantiene la cabeza humeral organizada mientras la escápula se mueve."),
            ("columna-cervical", "STABILIZER", ["posición cervical"], "La columna cervical conserva un cuello largo y evita que el trapecio tire de la cabeza."),
        ],
    }
    return templates.get(group, [("columna-lumbar", "STABILIZER", ["estabilidad del tronco"], "La columna lumbar ofrece una base estable para transmitir la fuerza del ejercicio.")])


def joint_variant_modifier(joint_id: str, options: dict[str, str], equipment: str) -> str:
    if options.get("grip_type") == "supinated" and joint_id in {"codo", "radiocubital-proximal"}:
        return "El agarre supino aumenta la demanda de supinación y favorece la flexión del codo."
    if options.get("grip_type") == "pronated" and joint_id in {"codo", "radiocubital-proximal"}:
        return "El agarre prono limita la supinación y reparte más trabajo hacia braquial y antebrazo."
    if options.get("grip_width") == "wide" and joint_id in {"glenohumeral", "escapulotoracica"}:
        return "La amplitud abierta coloca el brazo más separado del tronco y cambia el recorrido escapular."
    if options.get("grip_width") == "close" and joint_id in {"glenohumeral", "codo"}:
        return "La amplitud cerrada acerca el codo al cuerpo y reduce la apertura del hombro."
    if options.get("laterality") == "unilateral" or options.get("stance") == "unilateral":
        return "La versión unilateral exige que esta articulación estabilice diferencias de carga entre ambos lados."
    if options.get("station") == "seated" and joint_id in {"columna-lumbar", "sacroiliaca", "cadera"}:
        return "El apoyo sentado reduce la demanda de equilibrio y cambia el papel de esta articulación hacia la transmisión."
    if options.get("pulley_height") == "high" and joint_id in {"glenohumeral", "escapulotoracica"}:
        return "La polea alta dirige la fuerza hacia abajo y modifica el tramo inicial del arco del hombro."
    if options.get("pulley_height") == "low" and joint_id in {"glenohumeral", "cadera"}:
        return "La polea baja aumenta la tensión cuando la articulación se aleja del anclaje."
    if equipment in {"machine", "smith_machine"} and joint_id in {"columna-lumbar", "cadera", "rodilla"}:
        return "El guiado externo limita las desviaciones y vuelve más repetible la línea de carga."
    if equipment == "cable":
        return "La tensión continua mantiene la articulación expuesta durante una porción amplia del recorrido."
    return "La posición exacta y la línea de resistencia determinan cuánto se mueve y cuánto estabiliza esta articulación."


def build_joints(group: str, options: dict[str, str], equipment: str) -> list[dict[str, Any]]:
    result = []
    for joint_id, role, actions, base_note in joint_templates(group):
        modifier = joint_variant_modifier(joint_id, options, equipment)
        role_label = ROLE_LABELS[role]
        note = accessible_anatomy(f"{role_label}: {base_note} {modifier}")
        result.append({
            "jointId": joint_id,
            "role": role,
            "actions": [title_label(action) for action in actions],
            "note": note,
        })
    return result


def build_muscle_notes(profile: dict[str, Any], options: dict[str, str], equipment: str, group: str) -> list[dict[str, str]]:
    notes = []
    for role_key, field in (("PRIMARY", "primaryMuscles"), ("SECONDARY", "secondaryMuscles"), ("STABILIZER", "stabilizerMuscles")):
        for muscle in profile[field]:
            label = MUSCLE_LABELS.get(muscle, muscle.replace("_", " "))
            action = MUSCLE_ACTIONS.get(muscle, "participa en la producción o estabilización de la fuerza")
            detail = muscle_variant_detail(muscle, options, equipment, group)
            role_label = ROLE_LABELS[role_key]
            note = accessible_anatomy(
                f"{role_label}: {label} {action}. {detail} Su función define cuánto contribuye la serie al estímulo muscular total."
            )
            notes.append({"muscleId": muscle, "note": note})
    return notes


def setup_text(name: str, options: dict[str, str], equipment: str, group: str) -> str:
    label = variant_label(options, equipment)
    posture = ", ".join(title_label(option_label(axis, value)) for axis, value in options.items() if axis in {"station", "support_angle"})
    if group in {"hip_hinge", "explosive_hinge"}:
        return f"Posición inicial: {label}; pies firmes, carga cercana y cadera preparada para desplazarse hacia atrás."
    if group in {"knee_dominant", "unilateral_knee"}:
        return f"Posición inicial: {label}; pie de apoyo completo, pelvis orientada y rodilla libre para seguir la línea del pie."
    if group in {"horizontal_pull", "vertical_pull"}:
        return f"Posición inicial: {label}; hombros organizados, agarre firme y torso preparado para recibir el tirón."
    if group in {"horizontal_push", "vertical_push", "shoulder_raise"}:
        return f"Posición inicial: {label}; apoyo estable, muñeca sobre el antebrazo y hombro dentro de un rango cómodo."
    if group in {"elbow_flexion", "elbow_extension", "wrist"}:
        return f"Posición inicial: {label}; brazo superior o antebrazo fijado según el ejercicio y muñeca alineada."
    if posture:
        return f"Posición inicial: {label}; {posture} y una base estable para separar el movimiento objetivo de las compensaciones."
    return f"Posición inicial: {label}; base estable y recorrido disponible antes de aplicar la resistencia."


def copy_for(definition: dict[str, Any], configuration: dict[str, Any]) -> tuple[str, list[str], str, str, list[dict[str, Any]], list[dict[str, str]]]:
    profile = configuration["profile"]
    options = configuration.get("selectedOptions", {})
    equipment = profile["equipmentId"]
    group = pattern_group(profile["movementPatternId"])
    spec = PATTERN_COPY.get(group, FALLBACK_COPY)
    variant_text, variant_benefit, option_technique, _ = variant_parts(options, equipment, group)
    label = variant_label(options, equipment)
    technique = f"{spec['technique']} {equipment_technique(equipment)} {option_technique}".strip()
    benefits = [
        without_canonical_name(polished_case(spec["benefit"]), definition["canonicalName"]),
        without_canonical_name(polished_case(f"La variante {label} aporta {variant_benefit}."), definition["canonicalName"]),
    ]
    description = without_canonical_name(
        accessible_anatomy(f"{spec['summary']} {variant_text} Esta configuración aporta {variant_benefit}."),
        definition["canonicalName"],
    )
    # The catalogue gate reserves these verbs for coaching fields. Keep the
    # description factual without accidentally turning its technique sentence
    # into an imperative.
    replacements = {
        "mantén": "conserva",
        "mantener": "conservar",
        "configura": "prepara",
        "adopta": "asume",
        "controla": "regula",
        "asegura": "refuerza",
        "evita": "reduce",
        "sigue": "recorre",
        "selecciona": "elige",
        "ejecuta": "realiza",
    }
    for source, replacement in replacements.items():
        description = re.sub(rf"\b{source}\b", replacement, description, flags=re.IGNORECASE)
    joints = build_joints(group, options, equipment)
    muscle_notes = build_muscle_notes(profile, options, equipment, group)
    return description, benefits, technique, spec["mistake"], joints, muscle_notes


def update_configuration(family: dict[str, Any], definition: dict[str, Any], configuration: dict[str, Any]) -> None:
    profile = configuration["profile"]
    description, benefits, technique, mistake, joints, muscle_notes = copy_for(definition, configuration)
    options = configuration.get("selectedOptions", {})
    equipment = profile["equipmentId"]
    group = pattern_group(profile["movementPatternId"])
    label = variant_label(options, equipment)
    spec = PATTERN_COPY.get(group, FALLBACK_COPY)
    variant_text, variant_benefit, option_technique, _ = variant_parts(options, equipment, group)

    profile["description"] = description
    profile["catalogRevision"] = REVISION
    profile["benefits"] = benefits
    profile["techniqueSummary"] = technique
    profile["variantRationale"] = variant_text
    profile["muscleNotes"] = muscle_notes
    profile["jointInvolvement"] = joints
    profile["setupCues"] = [setup_text(definition["canonicalName"], options, equipment, group)]
    profile["executionCues"] = [technique]
    profile["commonMistakes"] = [mistake]

    rich = profile["richMetadata"]
    rich["editorial"] = {
        "description": description,
        "benefits": benefits,
        "technique": technique,
        "variantRationale": variant_text,
    }
    rich["anatomy"]["primaryMuscles"] = profile["primaryMuscles"]
    rich["anatomy"]["secondaryMuscles"] = profile["secondaryMuscles"]
    rich["anatomy"]["stabilizerMuscles"] = profile["stabilizerMuscles"]
    rich["anatomy"]["jointActions"] = list(dict.fromkeys(action for joint in joints for action in joint["actions"]))
    rich["anatomy"]["jointInvolvement"] = joints
    rich["biomechanics"]["relevantJoints"] = [joint["jointId"] for joint in joints]
    rich["biomechanics"]["relevantTendons"] = list(dict.fromkeys(tendon for joint in joints for tendon in JOINT_TENDONS.get(joint["jointId"], [])))
    rich["programming"]["objectives"] = [
        f"Desarrollar {MUSCLE_LABELS.get(profile['primaryMuscles'][0], profile['primaryMuscles'][0])} mediante {definition['canonicalName']} en la variante {label}.",
        f"Priorizar {spec['benefit']} con la línea de resistencia elegida.",
    ]
    rich["replacement"]["preservesIntent"] = [
        f"Conserva {spec['action']} y el objetivo de {MUSCLE_LABELS.get(profile['primaryMuscles'][0], profile['primaryMuscles'][0])}.",
        f"La sustitución debe respetar las articulaciones relevantes: {', '.join(JOINT_LABELS[j['jointId']] for j in joints[:3])}.",
    ]
    rich["coaching"]["setup"] = profile["setupCues"]
    rich["coaching"]["execution"] = profile["executionCues"]
    rich["coaching"]["commonMistakes"] = profile["commonMistakes"]
    rich["coaching"]["cues"] = [f"Clave: {option_technique or spec['technique']}"]
    rich["coaching"]["progressions"] = [f"Aumentar la carga o el ángulo solo cuando {spec['benefit']} siga siendo perceptible sin compensaciones."]
    rich["coaching"]["regressions"] = [f"Reducir la carga o el rango y conservar {spec['action']} como referencia técnica."]
    rich["coaching"]["relevantMobility"] = [f"Movilidad de {JOINT_LABELS[joint['jointId']]} según tolerancia y rango disponible." for joint in joints[:2]]
    rich["identity"]["catalogRevision"] = REVISION
    rich["identity"]["familyId"] = family["id"]
    rich["identity"]["definitionId"] = definition["id"]
    rich["identity"]["configurationId"] = configuration["id"]
    rich["identity"]["canonicalName"] = definition["canonicalName"]
    rich["identity"]["searchTerms"] = definition.get("searchTerms", [])
    rich["display"]["displayName"] = definition["canonicalName"]
    rich["display"]["displaySummary"] = configuration["displaySummary"]
    rich["display"]["selectedOptions"] = options
    evidence = configuration.setdefault("evidence", {})
    refs = [ref for ref in evidence.get("evidenceRefs", []) if "catalog-v5" not in ref]
    evidence["evidenceRefs"] = refs + ["editorial:catalog-v5-profundidad-articular-2026-08-08"]
    evidence["rationale"] = "Revisada en la curaduría v5: texto, músculo y articulaciones resueltos para la configuración exacta."


def definition_description(definition: dict[str, Any]) -> str:
    """Write an exercise-level description useful before choosing chips."""
    profile = definition["configurations"][0]["profile"]
    group = pattern_group(profile["movementPatternId"])
    spec = PATTERN_COPY.get(group, FALLBACK_COPY)
    primary = [MUSCLE_LABELS.get(muscle, muscle) for muscle in profile.get("primaryMuscles", [])[:2]]
    target = " y ".join(primary) if primary else "la musculatura objetivo"
    equipment = list(dict.fromkeys(
        EQUIPMENT_LABELS.get(
            configuration["profile"].get("equipmentId"),
            configuration["profile"].get("equipmentId", "implemento"),
        )
        for configuration in definition["configurations"]
    ))
    if len(equipment) > 1:
        equipment_text = ", ".join(equipment[:-1]) + " y " + equipment[-1]
    else:
        equipment_text = equipment[0]
    return without_canonical_name(
        accessible_anatomy(
            f"{spec['summary']} El objetivo principal es desarrollar {target} mediante {spec['action']}. "
            f"Puedes elegir entre {equipment_text}; cada opción cambia el apoyo o la sensación de carga, "
            "pero conserva la intención del ejercicio."
        ),
        definition["canonicalName"],
    )


def update_family(payload: dict[str, Any]) -> None:
    family = payload["family"]
    payload["catalogRevision"] = REVISION
    payload["ontologyRevision"] = ONTOLOGY
    family["evidence"]["rationale"] = "Familia revisada con copy por configuración y ontología articular enlazable a WikiLab."
    for definition in family["definitions"]:
        definition["description"] = definition_description(definition)
        definition["evidence"]["rationale"] = "Definición revisada; sus configuraciones tienen copy editorial, técnica y ficha articular propias."
        for configuration in definition["configurations"]:
            update_configuration(family, definition, configuration)
            configuration["profile"]["richMetadata"]["identity"]["kind"] = definition["kind"]
            configuration["profile"]["richMetadata"]["identity"]["performanceProfileId"] = configuration["profile"]["performanceProfileId"]
            configuration["profile"]["richMetadata"]["evidenceConfidence"] = configuration["evidence"].get("confidence", "MEDIUM")
        definition["evidence"]["evidenceRefs"] = ["editorial:catalog-v5-profundidad-articular-2026-08-08"]
    family["evidence"]["evidenceRefs"] = ["editorial:catalog-v5-profundidad-articular-2026-08-08"]


def main() -> int:
    files = sorted(FAMILIES.glob("*.json"))
    if not files:
        raise SystemExit(f"No family files found under {FAMILIES}")
    total = 0
    for path in files:
        payload = json.loads(path.read_text(encoding="utf-8"))
        update_family(payload)
        # Atomic replacement is more reliable on Windows when the catalog is
        # watched by an editor, indexer, or cloud-sync process.
        temporary_path = path.with_name(f".{path.name}.codex-tmp")
        temporary_path.write_bytes(canonical_json(payload))
        temporary_path.replace(path)
        total += sum(len(definition["configurations"]) for definition in payload["family"]["definitions"])
    print(f"revision={REVISION}")
    print(f"ontology={ONTOLOGY}")
    print(f"families={len(files)} configurations={total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
