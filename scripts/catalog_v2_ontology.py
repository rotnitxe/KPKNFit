"""Ontology identifiers shared by the catalog v2 tooling.

Single definition of the muscle ids, joint ids and anatomical roles that the
runtime catalog may reference. The applier, the gate and the quality audit all
import from here so a new id is added in exactly one place (and then in the
Kotlin/Swift ontologies, which are authoritative for the apps).
"""

from __future__ import annotations

ROLES = ("PRIMARY", "SECONDARY", "STABILIZER")

# Profile field that stores each role, in the order the runtime lists them.
MUSCLE_LISTS: tuple[tuple[str, str], ...] = (
    ("primaryMuscles", "PRIMARY"),
    ("secondaryMuscles", "SECONDARY"),
    ("stabilizerMuscles", "STABILIZER"),
)

MUSCLE_IDS = frozenset(
    {
        "abdominals", "adductors", "biceps", "calves", "core", "deltoid", "erector_spinae", "forearm",
        "gluteus_maximus", "gluteus_medius", "hamstrings", "hip_flexors", "latissimus_dorsi", "neck",
        "pectoralis", "quadriceps", "rhomboids", "tensor_fasciae_latae", "tibialis_anterior", "trapezius",
        "triceps",
    }
)

JOINT_IDS = frozenset(
    {
        "glenohumeral", "acromioclavicular", "esternoclavicular", "codo", "radiocubital-proximal", "muñeca",
        "columna-cervical", "columna-toracica", "columna-lumbar", "sacroiliaca", "cadera", "rodilla", "tobillo",
        "subtalar", "escapulotoracica",
    }
)

# Movement patterns the runtime knows how to place in a session (64 today).
# Mirror of `AprendeOntology.catalogPatternToWikiLab` (Kotlin); a test keeps both in sync.
# A ficha may only move an exercise to one of these. A new pattern needs the Kotlin
# ontology, `CompositionTaxonomy` and `AprendeCatalogAuditTest` first, and the user's approval.
MOVEMENT_PATTERN_IDS = frozenset(
    {
        "ankle_dorsiflexion", "anti_extension_isometric", "anti_extension_pelvic_control",
        "anti_extension_trunk", "anti_rotation_trunk", "biarticular_lengthened", "deadlift",
        "diagonal_push", "eccentric_knee_flexion", "elbow_extension", "elbow_flexion", "hip_abduction",
        "hip_abduction_extension", "hip_abduction_external_rotation", "hip_abduction_stability",
        "hip_adduction", "hip_adduction_dynamic", "hip_extension", "hip_extension_abduction",
        "hip_extension_external_rotation", "hip_flexion", "hip_hinge", "hip_hinge_deficit",
        "hip_hinge_explosive", "hip_hinge_lengthened", "horizontal_abduction", "horizontal_pull",
        "horizontal_push", "isometric_grip", "knee_dominant", "knee_dominant_asymmetric",
        "knee_dominant_lengthened", "knee_extension", "knee_flexion", "knee_hip_dominant",
        "knee_hip_extension", "knee_hip_flexion", "lateral_knee_dominant", "lateral_trunk_flexion",
        "neck_extension", "neck_flexion", "neck_lateral_flexion", "pinch_grip", "plantar_flexion",
        "romanian_deadlift", "romanian_deadlift_deficit", "scapular_depression", "scapular_elevation",
        "shoulder_abduction_diagonal", "shoulder_abduction_full_rom", "shoulder_flexion",
        "spinal_extension", "spinal_flexion", "trunk_flexion", "trunk_rotation", "unilateral_hip_dominant",
        "unilateral_knee_dominant", "unilateral_knee_dominant_asymmetric", "vertical_pull",
        "vertical_pull_abduction", "vertical_push", "wrist_extension", "wrist_flexion",
        "wrist_flexion_extension",
    }
)
