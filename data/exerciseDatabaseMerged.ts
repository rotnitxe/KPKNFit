// data/exerciseDatabaseMerged.ts
// Fusión: Tren Inferior + Tren Superior desde exerciseDatabaseCentral (fuentes: PDFs TREN INFERIOR, TREN SUPERIOR).
// Base de datos única, sin fusión con exerciseDatabase legacy.

import { ExerciseMuscleInfo } from '../types';
import { LOWER_BODY_EXERCISES, UPPER_BODY_EXERCISES, ULTIMO_LOTE_EXERCISES } from './exerciseDatabaseCentral';

/** Mapa de IDs antiguos (tren inferior) -> IDs nuevos (exerciseDatabaseCentral) para retrocompatibilidad */
const LOWER_BODY_ID_ALIASES: Record<string, string> = {
  db_squat_high_bar: 'tren_inferior_sentadilla_barra_alta',
  db_squat_low_bar: 'tren_inferior_sentadilla_barra_baja',
  db_front_squat: 'tren_inferior_sentadilla_frontal',
  db_goblet_squat: 'tren_inferior_sentadilla_goblet_mancuerna',
  db_zercher_squat: 'tren_inferior_sentadilla_zercher',
  db_ssb_squat: 'tren_inferior_sentadilla_safety_squat_bar',
  db_cambered_squat: 'tren_inferior_sentadilla_cambered_bar',
  db_jefferson_squat: 'tren_inferior_sentadilla_jefferson',
  db_box_squat: 'tren_inferior_sentadilla_cajon_barra',
  db_deadlift: 'tren_inferior_peso_muerto_convencional',
  db_sumo_deadlift: 'tren_inferior_peso_muerto_sumo',
  db_semi_sumo_deadlift: 'tren_inferior_peso_muerto_convencional',
  db_romanian_deadlift: 'tren_inferior_peso_muerto_rumano',
  db_stiff_leg_deadlift: 'tren_inferior_peso_muerto_piernas_rigidas',
  db_deficit_deadlift: 'tren_inferior_peso_muerto_deficit',
  db_rack_pull: 'tren_inferior_rack_pull',
  db_trap_bar_deadlift: 'tren_inferior_peso_muerto_barra_hexagonal',
  db_good_mornings: 'tren_inferior_buenos_dias_pie',
  db_hyperextensions: 'tren_inferior_hiperextension_45',
  db_reverse_hyper: 'tren_inferior_reverse_hyper',
  db_cable_pull_through: 'tren_inferior_pull_through',
  db_kettlebell_swing: 'tren_inferior_swing_2_manos',
  db_bodyweight_hip_thrust: 'tren_inferior_hip_thrust_unilateral_peso',
  db_leg_press_45: 'tren_inferior_prensa_45',
  db_single_leg_press: 'tren_inferior_prensa_unilateral',
  db_hack_squat: 'tren_inferior_sentadilla_hack_maquina',
  db_barbell_hack_squat: 'tren_inferior_sentadilla_hack_barra',
  db_pendulum_squat: 'tren_inferior_sentadilla_pendulo',
  db_belt_squat: 'tren_inferior_sentadilla_belt_squat',
  db_leg_extension: 'tren_inferior_extension_cuadriceps',
  db_leg_curl_seated: 'tren_inferior_curl_femoral_sentado',
  db_leg_curl_lying: 'tren_inferior_curl_femoral_tumbado',
  db_nordic_curl: 'tren_inferior_curl_nordico',
  db_reverse_nordic: 'tren_inferior_extension_inversa_nordica',
  db_bulgarian_split_squat: 'tren_inferior_bulgara_mancuernas',
  db_lunges_walking: 'tren_inferior_zancada_caminando_mancuernas',
  db_reverse_lunge: 'tren_inferior_zancada_inversa_mancuernas',
  db_step_up: 'tren_inferior_subida_cajon_mancuernas',
  db_hip_thrust: 'tren_inferior_hip_thrust_barra',
  db_standing_calf_raise: 'tren_inferior_elevacion_talones_pie_maquina',
  db_seated_calf_raise: 'tren_inferior_elevacion_talones_sentado',
  db_sled_push: 'tren_inferior_empuje_trineo',
  db_sled_pull: 'tren_inferior_arrastre_trineo',
  db_box_jump: 'tren_inferior_salto_cajon',
  db_adductor_machine: 'tren_inferior_aduccion_cadera_maquina',
  db_abductor_machine: 'tren_inferior_abduccion_cadera_maquina',
};

/** Mapa de IDs antiguos (tren superior) -> IDs nuevos (exerciseDatabaseCentral) para retrocompatibilidad */
const UPPER_BODY_ID_ALIASES: Record<string, string> = {
  db_bench_press_tng: 'tren_superior_press_banca_plano_barra',
  db_bench_press_paused: 'tren_superior_press_banca_plano_barra',
  db_incline_bench_press: 'tren_superior_press_banca_inclinado_barra',
  db_dumbbell_bench_press: 'tren_superior_press_banca_plano_mancuernas',
  db_incline_dumbbell_press: 'tren_superior_press_banca_inclinado_mancuernas',
  db_dips: 'tren_superior_fondos_paralelas',
  db_push_up: 'tren_superior_flexiones_clasicas',
  db_cable_crossover: 'tren_superior_cruce_poleas_altas',
  db_pull_up: 'tren_superior_dominadas_pronas',
  db_chin_up: 'tren_superior_dominadas_supinas',
  db_barbell_row: 'tren_superior_remo_inclinado_prono_barra',
  db_dumbbell_row: 'tren_superior_remo_una_mano_mancuerna',
  db_lat_pulldown: 'tren_superior_jalon_pecho_prono',
  db_seated_cable_row: 'tren_superior_remo_sentado_polea_baja',
  db_overhead_press: 'tren_superior_press_militar_pie_barra',
  db_dumbbell_shoulder_press: 'tren_superior_press_hombros_sentado_mancuernas',
  db_lateral_raise: 'tren_superior_elevaciones_laterales_mancuernas',
  db_barbell_curl: 'tren_superior_curl_biceps_barra_recta',
  db_triceps_pushdown: 'tren_superior_extension_triceps_polea_cuerda',
  db_skull_crusher: 'tren_superior_press_frances_barra_ez',
};

function enrichWithOperationalData(ex: ExerciseMuscleInfo): ExerciseMuscleInfo {
  const hasCore = ex.involvedMuscles?.some(m =>
    ['Core', 'Abdomen', 'Espalda Baja', 'Recto Abdominal', 'Transverso Abdominal'].includes(m.muscle) && (m.activation || 0) >= 0.3
  );
  const isCompound = ex.type === 'Básico' && ['Barra', 'Peso Corporal'].includes(ex.equipment || '');
  const isPull = ex.force === 'Tirón' || ex.force === 'Bisagra';
  const isHeavyPull = isPull && (ex.equipment === 'Barra' || ex.subMuscleGroup?.toLowerCase().includes('dorsal'));

  return {
    ...ex,
    averageRestSeconds: ex.averageRestSeconds ?? (ex.type === 'Básico' ? 120 : ex.type === 'Aislamiento' ? 60 : 90),
    coreInvolvement: ex.coreInvolvement ?? (hasCore ? (isCompound ? 'high' as const : 'medium' as const) : 'low' as const),
    bracingRecommended: ex.bracingRecommended ?? (isCompound && (ex.force === 'Sentadilla' || ex.force === 'Bisagra' || ex.force === 'Empuje')),
    strapsRecommended: ex.strapsRecommended ?? (isHeavyPull && (ex.name?.toLowerCase().includes('peso muerto') || ex.name?.toLowerCase().includes('remo') || ex.name?.toLowerCase().includes('dominada') || ex.name?.toLowerCase().includes('jalón'))),
    bodybuildingScore: ex.bodybuildingScore ?? (ex.category === 'Hipertrofia' ? (ex.type === 'Básico' ? 8 : ex.type === 'Aislamiento' ? 7 : 7.5) : 6),
  };
}

/** Elimina duplicados exactos */
function removeExactDuplicates(list: ExerciseMuscleInfo[]): ExerciseMuscleInfo[] {
  const seen = new Map<string, ExerciseMuscleInfo>();
  for (const ex of list) {
    const key = JSON.stringify({
      id: ex.id,
      name: ex.name,
      involvedMuscles: ex.involvedMuscles?.map(m => ({ muscle: m.muscle, role: m.role, activation: m.activation })).sort((a, b) => a.muscle.localeCompare(b.muscle)),
      equipment: ex.equipment,
      type: ex.type,
    });
    if (!seen.has(key)) seen.set(key, ex);
  }
  return Array.from(seen.values());
}

/**
 * Elimina duplicados por nombre (case-insensitive). Mantiene la primera aparición.
 * Devuelve el mapa de IDs eliminados -> ID canónico para lookups retrocompatibles.
 */
function removeDuplicateNames(list: ExerciseMuscleInfo[]): { deduplicated: ExerciseMuscleInfo[]; aliasMap: Map<string, string> } {
  const seenNames = new Set<string>();
  const aliasMap = new Map<string, string>();
  const result: ExerciseMuscleInfo[] = [];

  for (const ex of list) {
    const nameKey = ex.name.toLowerCase().trim();
    if (seenNames.has(nameKey)) {
      const first = result.find((e) => e.name.toLowerCase().trim() === nameKey);
      if (first) aliasMap.set(ex.id, first.id);
      continue;
    }
    seenNames.add(nameKey);
    result.push(ex);
  }

  return { deduplicated: result, aliasMap };
}

// Combinar: tren superior, tren inferior, último lote (fuentes: PDFs centrales + ÚLTIMO LOTE)
const merged: ExerciseMuscleInfo[] = [...UPPER_BODY_EXERCISES, ...LOWER_BODY_EXERCISES, ...ULTIMO_LOTE_EXERCISES];

const exactDeduped = removeExactDuplicates(merged);
const { deduplicated: nameDeduped, aliasMap: nameAliasMap } = removeDuplicateNames(exactDeduped);

// Fusionar alias de nombres con alias de IDs antiguos (tren inferior + tren superior)
const fullAliasMap = new Map<string, string>(nameAliasMap);
for (const [oldId, newId] of Object.entries(LOWER_BODY_ID_ALIASES)) {
  if (!fullAliasMap.has(oldId)) fullAliasMap.set(oldId, newId);
}
for (const [oldId, newId] of Object.entries(UPPER_BODY_ID_ALIASES)) {
  if (!fullAliasMap.has(oldId)) fullAliasMap.set(oldId, newId);
}

/** Mapa de IDs de ejercicios eliminados o migrados -> ID canónico para lookups */
export const EXERCISE_ID_ALIASES: Map<string, string> = fullAliasMap;

export const FULL_EXERCISE_LIST: ExerciseMuscleInfo[] = nameDeduped.map(enrichWithOperationalData);
