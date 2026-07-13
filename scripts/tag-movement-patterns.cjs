// scripts/tag-movement-patterns.cjs
// Tags all exercises in exerciseDatabaseCentral.ts with movementPattern
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'data', 'exerciseDatabaseCentral.ts');

// ─── COMPLETE ID → MOVEMENT PATTERN MAPPING ───
const P = {
  EH: 'Empuje Horizontal',
  EV: 'Empuje Vertical',
  TH: 'Tirón Horizontal',
  TV: 'Tirón Vertical',
  SQ: 'Sentadilla',
  HI: 'Bisagra',
  EC: 'Empuje Cadera',
  FC: 'Flexión Codo',
  XC: 'Extensión Codo',
  FR: 'Flexión Rodilla',
  ET: 'Extensión Tobillo',
  AC: 'Abducción Cadera',
  AD: 'Aducción Cadera',
  FH: 'Flexión Hombro',
  EE: 'Elevación Escapular',
  GM: 'Agarre/Muñeca',
  CO: 'Core',
};

const MAP = {
  // ═══════════════════════════════════════════════════════
  // TREN INFERIOR - SENTADILLA (~63)
  // ═══════════════════════════════════════════════════════
  'tren_inferior_sentadilla_barra_alta': P.SQ,
  'tren_inferior_sentadilla_barra_baja': P.SQ,
  'tren_inferior_sentadilla_frontal': P.SQ,
  'tren_inferior_sentadilla_goblet_mancuerna': P.SQ,
  'tren_inferior_sentadilla_goblet_kettlebell': P.SQ,
  'tren_inferior_sentadilla_zercher': P.SQ,
  'tren_inferior_sentadilla_safety_squat_bar': P.SQ,
  'tren_inferior_sentadilla_cambered_bar': P.SQ,
  'tren_inferior_sentadilla_jefferson': P.SQ,
  'tren_inferior_sentadilla_cajon_barra': P.SQ,
  'tren_inferior_sentadilla_landmine': P.SQ,
  'tren_inferior_sentadilla_polea_baja': P.SQ,
  'tren_inferior_sentadilla_saco_arena': P.SQ,
  'tren_inferior_sentadilla_isometrica_pared': P.SQ,
  'tren_inferior_sentadilla_bandas': P.SQ,
  'tren_inferior_sentadilla_smith': P.SQ,
  'tren_inferior_sentadilla_hack_maquina': P.SQ,
  'tren_inferior_sentadilla_hack_barra': P.SQ,
  'tren_inferior_sentadilla_pendulo': P.SQ,
  'tren_inferior_sentadilla_belt_squat': P.SQ,
  'tren_inferior_prensa_45': P.SQ,
  'tren_inferior_prensa_horizontal': P.SQ,
  'tren_inferior_prensa_vertical': P.SQ,
  'tren_inferior_bulgara_mancuernas': P.SQ,
  'tren_inferior_bulgara_barra': P.SQ,
  'tren_inferior_bulgara_smith': P.SQ,
  'tren_inferior_bulgara_polea': P.SQ,
  'tren_inferior_split_estatico_barra': P.SQ,
  'tren_inferior_split_mancuernas': P.SQ,
  'tren_inferior_zancada_frontal_barra': P.SQ,
  'tren_inferior_zancada_inversa_barra': P.SQ,
  'tren_inferior_zancada_inversa_mancuernas': P.SQ,
  'tren_inferior_zancada_caminando_mancuernas': P.SQ,
  'tren_inferior_zancada_lateral_mancuerna': P.SQ,
  'tren_inferior_zancada_cruzada_mancuernas': P.SQ,
  'tren_inferior_zancada_smith': P.SQ,
  'tren_inferior_zancada_trx': P.SQ,
  'tren_inferior_subida_cajon_mancuernas': P.SQ,
  'tren_inferior_subida_cajon_barra': P.SQ,
  'tren_inferior_prensa_unilateral': P.SQ,
  'tren_inferior_sentadilla_pistol': P.SQ,
  'tren_inferior_sentadilla_pistol_kettlebell': P.SQ,
  'tren_inferior_sentadilla_patinador': P.SQ,
  'tren_inferior_extension_cuadriceps': P.SQ,
  'tren_inferior_extension_cuadriceps_unilateral': P.SQ,
  'tren_inferior_salto_cajon': P.SQ,
  'tren_inferior_salto_longitud': P.SQ,
  'tren_inferior_salto_caida': P.SQ,
  'tren_inferior_cargada_potencia': P.SQ,
  'tren_inferior_cargada_colgante': P.SQ,
  'tren_inferior_arrancada_potencia': P.SQ,
  'tren_inferior_arrancada_kettlebell': P.SQ,
  'tren_inferior_empuje_trineo': P.SQ,
  'tren_inferior_arrastre_trineo': P.SQ,

  // ═══════════════════════════════════════════════════════
  // TREN INFERIOR - BISAGRA (~40)
  // ═══════════════════════════════════════════════════════
  'tren_inferior_peso_muerto_convencional': P.HI,
  'tren_inferior_peso_muerto_sumo': P.HI,
  'tren_inferior_peso_muerto_rumano': P.HI,
  'tren_inferior_peso_muerto_rumano_mancuernas': P.HI,
  'tren_inferior_peso_muerto_piernas_rigidas': P.HI,
  'tren_inferior_peso_muerto_barra_hexagonal': P.HI,
  'tren_inferior_peso_muerto_deficit': P.HI,
  'tren_inferior_rack_pull': P.HI,
  'tren_inferior_peso_muerto_kettlebell': P.HI,
  'tren_inferior_peso_muerto_polea': P.HI,
  'tren_inferior_peso_muerto_saco_arena': P.HI,
  'tren_inferior_buenos_dias_pie': P.HI,
  'tren_inferior_buenos_dias_sentado': P.HI,
  'tren_inferior_buenos_dias_safety_bar': P.HI,
  'tren_inferior_buenos_dias_banda': P.HI,
  'tren_inferior_pull_through': P.HI,
  'tren_inferior_hiperextension_45': P.HI,
  'tren_inferior_hiperextension_silla_romana': P.HI,
  'tren_inferior_reverse_hyper': P.HI,
  'tren_inferior_rdl_1p_mancuerna': P.HI,
  'tren_inferior_rdl_1p_kettlebell': P.HI,
  'tren_inferior_rdl_1p_barra': P.HI,
  'tren_inferior_rdl_1p_polea': P.HI,
  'tren_inferior_rdl_b_stance_barra': P.HI,
  'tren_inferior_rdl_b_stance_mancuernas': P.HI,
  'tren_inferior_peso_muerto_maleta': P.HI,
  'tren_inferior_swing_2_manos': P.HI,
  'tren_inferior_swing_1_mano': P.HI,

  // ═══════════════════════════════════════════════════════
  // TREN INFERIOR - EMPUJE CADERA (~12)
  // ═══════════════════════════════════════════════════════
  'tren_inferior_hip_thrust_barra': P.EC,
  'tren_inferior_hip_thrust_maquina': P.EC,
  'tren_inferior_hip_thrust_smith': P.EC,
  'tren_inferior_hip_thrust_unilateral_peso': P.EC,
  'tren_inferior_hip_thrust_banda': P.EC,
  'tren_inferior_puente_gluteos_barra': P.EC,
  'tren_inferior_frog_pump': P.EC,
  'tren_inferior_elevacion_cadera_ghd': P.EC,

  // ═══════════════════════════════════════════════════════
  // TREN INFERIOR - FLEXIÓN RODILLA (~10)
  // ═══════════════════════════════════════════════════════
  'tren_inferior_curl_femoral_tumbado': P.FR,
  'tren_inferior_curl_femoral_sentado': P.FR,
  'tren_inferior_curl_femoral_pie_polea': P.FR,
  'tren_inferior_curl_femoral_balon': P.FR,
  'tren_inferior_glute_ham_raise': P.FR,
  'tren_inferior_curl_nordico': P.FR,

  // ═══════════════════════════════════════════════════════
  // TREN INFERIOR - EXTENSIÓN TOBILLO (~9)
  // ═══════════════════════════════════════════════════════
  'tren_inferior_elevacion_talones_pie_barra': P.ET,
  'tren_inferior_elevacion_talones_pie_maquina': P.ET,
  'tren_inferior_elevacion_talones_sentado': P.ET,
  'tren_inferior_elevacion_talones_prensa': P.ET,
  'tren_inferior_elevacion_talones_unilateral': P.ET,

  // ═══════════════════════════════════════════════════════
  // TREN INFERIOR - ABDUCCIÓN/ADUCCIÓN CADERA
  // ═══════════════════════════════════════════════════════
  'tren_inferior_abduccion_cadera_maquina': P.AC,
  'tren_inferior_abduccion_cadera_polea': P.AC,
  'tren_inferior_aduccion_cadera_maquina': P.AD,
  'tren_inferior_aduccion_cadera_polea': P.AD,
  'tren_inferior_caminata_lateral_banda': P.AC,

  // ═══════════════════════════════════════════════════════
  // TREN INFERIOR - EMPUJE CADERA (cargada/olímpicos = sentadilla)
  // ═══════════════════════════════════════════════════════

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - EMPUJE HORIZONTAL (~54)
  // ═══════════════════════════════════════════════════════
  'tren_superior_press_banca_plano_barra': P.EH,
  'tren_superior_press_banca_plano_mancuernas': P.EH,
  'tren_superior_press_banca_inclinado_barra': P.EH,
  'tren_superior_press_banca_inclinado_mancuernas': P.EH,
  'tren_superior_press_banca_declinado_barra': P.EH,
  'tren_superior_press_banca_declinado_mancuernas': P.EH,
  'tren_superior_press_banca_agarre_cerrado': P.EH,
  'tren_superior_press_pecho_maquina_convergente': P.EH,
  'tren_superior_press_pecho_maquina_smith': P.EH,
  'tren_superior_floor_press_barra': P.EH,
  'tren_superior_floor_press_mancuernas': P.EH,
  'tren_superior_flexiones_clasicas': P.EH,
  'tren_superior_flexiones_lastradas': P.EH,
  'tren_superior_flexiones_pies_elevados': P.EH,
  'tren_superior_flexiones_anillas': P.EH,
  'tren_superior_flexiones_diamante': P.EH,
  'tren_superior_press_unilateral_polea': P.EH,
  'tren_superior_press_spoto_barra': P.EH,
  'tren_superior_press_banda_resistencia': P.EH,
  'tren_superior_press_banca_cadenas': P.EH,
  'tren_superior_aperturas_planas_mancuernas': P.EH,
  'tren_superior_aperturas_inclinadas_mancuernas': P.EH,
  'tren_superior_aperturas_declinadas_mancuernas': P.EH,
  'tren_superior_aperturas_pec_deck': P.EH,
  'tren_superior_cruce_poleas_altas': P.EH,
  'tren_superior_cruce_poleas_bajas': P.EH,
  'tren_superior_aperturas_suelo_mancuernas': P.EH,
  'tren_superior_squeeze_press_mancuernas': P.EH,
  'tren_superior_aperturas_banda': P.EH,
  'tren_superior_fondos_paralelas': P.EH,
  'tren_superior_fondos_lastrados': P.EH,
  'tren_superior_fondos_anillas': P.EH,
  'tren_superior_fondos_entre_bancos': P.EH,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - EMPUJE VERTICAL (~24)
  // ═══════════════════════════════════════════════════════
  'tren_superior_press_militar_pie_barra': P.EV,
  'tren_superior_press_militar_sentado_barra': P.EV,
  'tren_superior_press_hombros_sentado_mancuernas': P.EV,
  'tren_superior_press_hombros_pie_mancuernas': P.EV,
  'tren_superior_press_arnold_mancuernas': P.EV,
  'tren_superior_press_hombros_maquina_convergente': P.EV,
  'tren_superior_press_hombros_maquina_smith': P.EV,
  'tren_superior_push_press_barra': P.EV,
  'tren_superior_press_tras_nuca_barra': P.EV,
  'tren_superior_press_landmine_un_brazo': P.EV,
  'tren_superior_press_kettlebell_un_brazo': P.EV,
  'tren_superior_press_z_barra': P.EV,
  'tren_superior_press_z_mancuernas': P.EV,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - TIRÓN VERTICAL (~26)
  // ═══════════════════════════════════════════════════════
  'tren_superior_dominadas_pronas': P.TV,
  'tren_superior_dominadas_supinas': P.TV,
  'tren_superior_dominadas_neutras': P.TV,
  'tren_superior_dominadas_lastradas': P.TV,
  'tren_superior_dominadas_anillas': P.TV,
  'tren_superior_jalon_pecho_prono': P.TV,
  'tren_superior_jalon_pecho_supino': P.TV,
  'tren_superior_jalon_pecho_neutro': P.TV,
  'tren_superior_jalon_tras_nuca': P.TV,
  'tren_superior_jalon_unilateral_polea': P.TV,
  'tren_superior_dominadas_asistidas': P.TV,
  'tren_superior_jalon_brazos_extendidos': P.TV,
  'tren_superior_pullover_mancuerna': P.TV,
  'tren_superior_pullover_barra': P.TV,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - TIRÓN HORIZONTAL (~43)
  // ═══════════════════════════════════════════════════════
  'tren_superior_remo_inclinado_prono_barra': P.TH,
  'tren_superior_remo_inclinado_supino_barra': P.TH,
  'tren_superior_remo_pendlay_barra': P.TH,
  'tren_superior_remo_barra_t_apoyado': P.TH,
  'tren_superior_remo_barra_t_libre': P.TH,
  'tren_superior_remo_una_mano_mancuerna': P.TH,
  'tren_superior_remo_mancuernas_banco_inclinado': P.TH,
  'tren_superior_remo_sentado_polea_baja': P.TH,
  'tren_superior_remo_unilateral_polea_baja': P.TH,
  'tren_superior_remo_maquina_convergente': P.TH,
  'tren_superior_remo_invertido_peso_corporal': P.TH,
  'tren_superior_remo_invertido_lastrado': P.TH,
  'tren_superior_remo_seal_barra': P.TH,
  'tren_superior_remo_meadows_mancuerna': P.TH,
  'tren_superior_remo_kettlebell': P.TH,
  'tren_superior_remo_banda': P.TH,
  'tren_superior_remo_menton_barra': P.TV,
  'tren_superior_remo_menton_polea': P.TV,
  'tren_superior_pajaros_inclinados_mancuernas': P.TH,
  'tren_superior_pajaros_pec_deck_inverso': P.TH,
  'tren_superior_elevaciones_posteriores_polea': P.TH,
  'tren_superior_face_pull_polea': P.TH,
  'tren_superior_face_pull_banda': P.TH,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - ELEVACIÓN ESCAPULAR (~7)
  // ═══════════════════════════════════════════════════════
  'tren_superior_encogimientos_barra': P.EE,
  'tren_superior_encogimientos_mancuernas': P.EE,
  'tren_superior_encogimientos_maquina_smith': P.EE,
  'tren_superior_encogimientos_barra_hexagonal': P.EE,
  'tren_superior_elevaciones_laterales_mancuernas': P.EE,
  'tren_superior_elevaciones_laterales_sentadas': P.EE,
  'tren_superior_elevaciones_laterales_polea_baja': P.EE,
  'tren_superior_elevaciones_laterales_maquina': P.EE,
  'tren_superior_elevaciones_laterales_banda': P.EE,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - FLEXIÓN HOMBRO (~5)
  // ═══════════════════════════════════════════════════════
  'tren_superior_elevaciones_frontales_barra': P.FH,
  'tren_superior_elevaciones_frontales_mancuernas': P.FH,
  'tren_superior_elevaciones_frontales_disco': P.FH,
  'tren_superior_elevaciones_frontales_polea': P.FH,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - FLEXIÓN CODO (~32)
  // ═══════════════════════════════════════════════════════
  'tren_superior_curl_biceps_barra_recta': P.FC,
  'tren_superior_curl_biceps_barra_ez': P.FC,
  'tren_superior_curl_biceps_alterno_mancuernas': P.FC,
  'tren_superior_curl_biceps_sentado_mancuernas': P.FC,
  'tren_superior_curl_martillo_mancuernas': P.FC,
  'tren_superior_curl_martillo_cuerda_polea': P.FC,
  'tren_superior_curl_predicador_barra_ez': P.FC,
  'tren_superior_curl_predicador_maquina': P.FC,
  'tren_superior_curl_inclinado_mancuernas': P.FC,
  'tren_superior_curl_arana_barra': P.FC,
  'tren_superior_curl_concentrado_mancuerna': P.FC,
  'tren_superior_curl_biceps_polea_baja': P.FC,
  'tren_superior_curl_biceps_polea_alta': P.FC,
  'tren_superior_curl_invertido_barra': P.FC,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - EXTENSIÓN CODO (~35)
  // ═══════════════════════════════════════════════════════
  'tren_superior_press_frances_barra_ez': P.XC,
  'tren_superior_press_frances_mancuernas': P.XC,
  'tren_superior_extension_triceps_polea_cuerda': P.XC,
  'tren_superior_extension_triceps_polea_barra': P.XC,
  'tren_superior_extension_triceps_agarre_inverso': P.XC,
  'tren_superior_extension_tras_nuca_polea_baja': P.XC,
  'tren_superior_extension_tras_nuca_mancuerna': P.XC,
  'tren_superior_extension_tras_nuca_unilateral': P.XC,
  'tren_superior_patada_triceps_mancuerna': P.XC,
  'tren_superior_patada_triceps_polea': P.XC,
  'tren_superior_tate_press_mancuernas': P.XC,

  // ═══════════════════════════════════════════════════════
  // TREN SUPERIOR - AGARRE/MUÑECA
  // ═══════════════════════════════════════════════════════
  'tren_superior_curl_muneca_supinacion': P.GM,
  'tren_superior_curl_muneca_pronacion': P.GM,
  'tren_superior_rodillo_muneca': P.GM,

  // ═══════════════════════════════════════════════════════
  // CUELLO (sin patrón de movimiento, dejar sin tag)
  // ═══════════════════════════════════════════════════════
  // nuevo_extension_cuello_isometrica → sin pattern
  // nuevo_flexion_lateral_cuello_banda → sin pattern
  // nuevo_lateralizacion_cuello_arnes → sin pattern

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - EMPUJE HORIZONTAL
  // ═══════════════════════════════════════════════════════
  'ultimo_flexiones_deficit': P.EH,
  'ultimo_press_banca_agarre_inverso': P.EH,
  'ultimo_hex_press_mancuernas': P.EH,
  'ultimo_cruces_polea_baja_ascendentes': P.EH,
  'ultimo_press_banca_tabla': P.EH,
  'ultimo_press_banca_suelo_puente': P.EH,
  'ultimo_press_larsen': P.EH,
  'ultimo_flexiones_arqueras': P.EH,
  'ultimo_press_svend_discos': P.EH,
  'ultimo_cruces_polea_media': P.EH,
  'ultimo_flexiones_diamante_pared': P.EH,
  'ultimo_press_banca_deficit_mancuernas': P.EH,
  'ultimo_press_pecho_convergente_excentrico': P.EH,
  'ultimo_cruces_pecho_cuffs': P.EH,
  'ultimo_aperturas_planas_cables': P.EH,
  'ultimo_press_banca_agarre_ancho': P.EH,
  'ultimo_flexiones_hindu': P.EH,
  'ultimo_flexiones_trx': P.EH,
  'ultimo_flexiones_planche': P.EH,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - EMPUJE VERTICAL
  // ═══════════════════════════════════════════════════════
  'ultimo_flexiones_pino_hspu': P.EV,
  'ultimo_press_savickas_mancuernas': P.EV,
  'ultimo_press_militar_1_brazo_barra': P.EV,
  'ultimo_press_hombros_cubano': P.EV,
  'ultimo_log_press': P.EV,
  'ultimo_press_javelin': P.EV,
  'ultimo_sotts_press': P.EV,
  'ultimo_press_eje': P.EV,
  'ultimo_press_landmine_rotacional': P.EV,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - TIRÓN VERTICAL
  // ═══════════════════════════════════════════════════════
  'ultimo_muscle_up_anillas': P.TV,
  'ultimo_pullover_polea_cuerda': P.TV,
  'ultimo_dominadas_agarre_comando': P.TV,
  'ultimo_dominadas_excentricas': P.TV,
  'ultimo_dominadas_escapulares': P.TV,
  'ultimo_jalon_pecho_brazo_recto_unilateral': P.TV,
  'ultimo_pullover_maquina': P.TV,
  'ultimo_jalon_pecho_unilateral': P.TV,
  'ultimo_dominadas_asistidas_pausa': P.TV,
  'ultimo_dominadas_1_brazo': P.TV,
  'ultimo_dominadas_toalla': P.TV,
  'ultimo_tiron_arrancada': P.TV,
  'ultimo_tiron_envion': P.TV,
  'ultimo_front_lever': P.TV,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - TIRÓN HORIZONTAL
  // ═══════════════════════════════════════════════════════
  'ultimo_remo_punta_tbar_apoyo': P.TH,
  'ultimo_remo_gironda_cuerda': P.TH,
  'ultimo_remo_helms_mancuernas': P.TH,
  'ultimo_remo_barra_esquina_1_brazo': P.TH,
  'ultimo_remo_renegado_mancuernas': P.TH,
  'ultimo_face_pull_sentado_polea': P.TH,
  'ultimo_pajaros_polea_alta': P.TH,
  'ultimo_pajaros_polea_cruzada': P.TH,
  'ultimo_pajaros_recostado_banco': P.TH,
  'ultimo_remo_ergometro': P.TH,
  'ultimo_traccion_facial_trx': P.TH,
  'ultimo_remo_invertido_pies_elevados': P.TH,
  'ultimo_band_pull_apart': P.TH,
  'ultimo_rope_pull': P.TH,
  'ultimo_remo_pendlay_eje': P.TH,
  'ultimo_remo_ilíaco_polea': P.TH,
  'ultimo_remo_unilateral_maquina_apoyo': P.TH,
  'ultimo_remo_kelso_apoyo': P.TH,
  'ultimo_remo_invertido_trx_giro': P.TH,
  'ultimo_remo_invertido_1_brazo': P.TH,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - SENTADILLA
  // ═══════════════════════════════════════════════════════
  'ultimo_paseo_granjero_mancuernas': P.EE,
  'ultimo_paseo_granjero_unilateral': P.EE,
  'ultimo_zancadas_zercher_barra': P.SQ,
  'ultimo_sentadilla_hack_invertida': P.SQ,
  'ultimo_sentadilla_copa_disco': P.SQ,
  'ultimo_sentadilla_anderson': P.SQ,
  'ultimo_sentadilla_espanola_banda': P.SQ,
  'ultimo_zancadas_pendulares_mancuernas': P.SQ,
  'ultimo_subidas_laterales_cajon': P.SQ,
  'ultimo_extensiones_cuadriceps_banda': P.SQ,
  'ultimo_salto_cuclillas': P.SQ,
  'ultimo_salto_tijera': P.SQ,
  'ultimo_sissy_squat_disco': P.SQ,
  'ultimo_sentadilla_trx': P.SQ,
  'ultimo_sentadilla_bulgara_deficit': P.SQ,
  'ultimo_sentadilla_hack_pausa': P.SQ,
  'ultimo_sentadilla_smith_pies_adelantados': P.SQ,
  'ultimo_sentadilla_overhead': P.SQ,
  'ultimo_sentadilla_zombie': P.SQ,
  'ultimo_sentadilla_hindu': P.SQ,
  'ultimo_arrancada_profunda': P.SQ,
  'ultimo_cargada_profunda': P.SQ,
  'ultimo_log_clean': P.SQ,
  'ultimo_paseo_pato': P.SQ,
  'ultimo_sprints_trineo': P.SQ,
  'ultimo_zancadas_saco_hombro': P.SQ,
  'ultimo_levantamiento_piedra_atlas': P.SQ,
  'ultimo_volteo_neumatico': P.SQ,
  'ultimo_snatch_balance': P.SQ,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - BISAGRA
  // ═══════════════════════════════════════════════════════
  'ultimo_buenos_dias_zercher': P.HI,
  'ultimo_peso_muerto_rumano_barra_trampa': P.HI,
  'ultimo_peso_muerto_bandas': P.HI,
  'ultimo_peso_muerto_rumano_deficit': P.HI,
  'ultimo_buenos_dias_1_pierna': P.HI,
  'ultimo_peso_muerto_zercher_suelo': P.HI,
  'ultimo_hiperextensiones_inversas': P.HI,
  'ultimo_peso_muerto_eje': P.HI,
  'ultimo_peso_muerto_neumaticos': P.HI,
  'ultimo_back_lever': P.HI,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - EMPUJE CADERA
  // ═══════════════════════════════════════════════════════
  'ultimo_puente_gluteo_1_pierna': P.EC,
  'ultimo_lenador_polea': P.EC,
  'ultimo_hip_thrust_1_pierna_mancuerna': P.EC,
  'ultimo_kas_glute_bridge_smith': P.EC,
  'ultimo_hip_thrust_unilateral_multipower': P.EC,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - FLEXIÓN CODO
  // ═══════════════════════════════════════════════════════
  'ultimo_curl_bayesian_polea': P.FC,
  'ultimo_curl_biceps_21s': P.FC,
  'ultimo_curl_arana_mancuernas': P.FC,
  'ultimo_curl_cruzado_mancuernas': P.FC,
  'ultimo_curl_pelicano_polea': P.FC,
  'ultimo_curl_predicador_unilateral_polea': P.FC,
  'ultimo_curl_recostado_banco_cables': P.FC,
  'ultimo_curl_estricto_pared': P.FC,
  'ultimo_curl_gironda': P.FC,
  'ultimo_curl_dual_polea_espalda': P.FC,
  'ultimo_curl_crucifijo_polea': P.FC,
  'ultimo_waiter_curl': P.FC,
  'ultimo_curl_apoyado_pecho_banco': P.FC,
  'ultimo_curl_concentrado_aire': P.FC,
  'ultimo_curl_biceps_toalla': P.FC,
  'ultimo_curl_predicador_invertido': P.FC,
  'ultimo_curl_biceps_polea_banco': P.FC,
  'ultimo_curl_zottman_polea': P.FC,
  'ultimo_curl_biceps_trx': P.FC,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - EXTENSIÓN CODO
  // ═══════════════════════════════════════════════════════
  'ultimo_rompecraneos_polea': P.XC,
  'ultimo_press_frances_declinado_ez': P.XC,
  'ultimo_extensiones_triceps_rodando_suelo': P.XC,
  'ultimo_extension_triceps_cruzada_polea': P.XC,
  'ultimo_press_frances_declinado_mancuernas': P.XC,
  'ultimo_extension_overhead_polea_espalda': P.XC,
  'ultimo_extensiones_katana_polea': P.XC,
  'ultimo_extension_triceps_crossbody': P.XC,
  'ultimo_press_california': P.XC,
  'ultimo_extension_pjr': P.XC,
  'ultimo_rolling_triceps_extensions': P.XC,
  'ultimo_rompecraneos_suelo': P.XC,
  'ultimo_press_jm_mancuernas': P.XC,
  'ultimo_extension_overhead_disco': P.XC,
  'ultimo_fondos_coreanos': P.XC,
  'ultimo_tate_press_polea': P.XC,
  'ultimo_extension_triceps_inversa_polea': P.XC,
  'ultimo_extensiones_triceps_trx': P.XC,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - FLEXIÓN RODILLA
  // ═══════════════════════════════════════════════════════
  'ultimo_curl_femoral_deslizante': P.FR,
  'ultimo_curl_femoral_inclinacion_frontal': P.FR,
  'ultimo_curl_femoral_unilateral_pie': P.FR,
  'ultimo_curl_isquios_mancuerna_tumbado': P.FR,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - EXTENSIÓN TOBILLO
  // ═══════════════════════════════════════════════════════
  'ultimo_elevacion_talones_excentrica_1_pierna': P.ET,
  'ultimo_elevacion_gemelos_burro': P.ET,
  'ultimo_elevacion_talones_sentado_unilateral': P.ET,
  'ultimo_elevacion_gemelos_hack': P.ET,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - ABDUCCIÓN CADERA
  // ═══════════════════════════════════════════════════════
  'ultimo_abduccion_cadera_banda_sentado': P.AC,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - FLEXIÓN HOMBRO
  // ═══════════════════════════════════════════════════════
  'ultimo_paseo_camarero': P.FH,
  'ultimo_marcha_overhead_unilateral': P.FH,
  'ultimo_elevaciones_frontales_polea_espalda': P.FH,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - ELEVACIÓN ESCAPULAR
  // ═══════════════════════════════════════════════════════
  'ultimo_elevaciones_y_polea': P.EE,
  'ultimo_encogimientos_tras_nuca': P.EE,
  'ultimo_paseo_granjero_barra_trampa': P.EE,
  'ultimo_elevaciones_laterales_recostado': P.EE,
  'ultimo_encogimientos_maquina_pausa': P.EE,
  'ultimo_encogimientos_cables_banco': P.EE,
  'ultimo_elevaciones_laterales_banco_inclinado': P.EE,
  'ultimo_elevaciones_y_tumbado': P.EE,
  'ultimo_transporte_escudo': P.EE,
  'ultimo_piedra_atlas_hombro': P.EE,
  'nuevo_farmer_walk_kettlebells': P.EE,
  'nuevo_lu_raises': P.EE,
  'nuevo_y_raises_mancuernas_tumbado': P.EE,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - AGARRE/MUÑECA
  // ═══════════════════════════════════════════════════════
  'ultimo_curl_muneca_tras_espalda': P.GM,
  'ultimo_curl_muneca_unilateral_banco': P.GM,
  'ultimo_extension_muneca_unilateral_banco': P.GM,
  'ultimo_curl_dedos_barra': P.GM,
  'ultimo_pinzas_discos': P.GM,
  'ultimo_pronacion_supinacion_mazo': P.GM,

  // ═══════════════════════════════════════════════════════
  // ÚLTIMOS - CORE
  // ═══════════════════════════════════════════════════════
  'ultimo_turkish_get_up': P.CO,
  'ultimo_giros_rusos_disco': P.CO,
  'ultimo_crunch_polea_alta': P.CO,
  'ultimo_elevacion_piernas_paralelas': P.CO,
  'ultimo_plancha_frontal': P.CO,
  'ultimo_bicho_muerto': P.CO,
  'ultimo_rotacion_externa_polea': P.CO,
  'ultimo_rotacion_interna_polea': P.CO,
  'ultimo_paseo_oso': P.CO,
  'ultimo_plancha_lateral': P.CO,
  'ultimo_plancha_toque_hombros': P.CO,
  'ultimo_abdominales_v': P.CO,
  'ultimo_elevacion_rodillas_colgado': P.CO,
  'ultimo_superman_suelo': P.CO,
  'ultimo_lanzamiento_balon_abajo': P.CO,
  'ultimo_lanzamiento_rotacional_balon': P.CO,
  'ultimo_crunch_maquina_declinada': P.CO,
  'ultimo_plancha_rodillo': P.CO,
  'ultimo_press_pallof_arrodillado': P.CO,
  'ultimo_giros_torso_maquina': P.CO,
  'ultimo_puente_luchador': P.CO,
  'ultimo_giro_cadera_saco_bulgaro': P.CO,
  'ultimo_suplex_saco_bulgaro': P.CO,
  'ultimo_paseo_caiman': P.CO,
  'ultimo_battle_ropes': P.CO,
  'ultimo_lanzamiento_lateral_balon': P.CO,
  'ultimo_elevacion_piernas_colgado_rotacion': P.CO,

  // ÚLTIMOS - CUELLO (sin patrón)
  // ultimo_flexion_cuello_arnes → sin pattern
  // ultimo_extension_cuello_arnes → sin pattern
  // ultimo_rotacion_cuello_banda → sin pattern
  // ultimo_flexion_cuello_isometrica → sin pattern

  // ═══════════════════════════════════════════════════════
  // NUEVOS
  // ═══════════════════════════════════════════════════════
  'nuevo_sentadilla_cosaca': P.SQ,
  'nuevo_curl_nordico_inverso': P.FR,
  'nuevo_buenos_dias_ssb_sentado': P.HI,
  'nuevo_curl_drag_barra': P.FC,
  'nuevo_curl_scott_maquina': P.FC,
  'nuevo_fondos_maquina_asistida': P.EH,
  'nuevo_jm_press_barra': P.XC,
  'nuevo_press_bradford': P.EV,
  'nuevo_remo_gorilla': P.TH,
  'nuevo_remo_hammer_strength': P.TH,
  'nuevo_jalon_v_cerrado': P.TV,
  'nuevo_cable_fly_inclinado': P.EH,
  'nuevo_pullover_declinado': P.TV,
  'nuevo_press_mancuerna_unilateral': P.EH,
  'nuevo_ab_rollout_parado': P.CO,
  'nuevo_bear_crawl_peso': P.CO,
  'nuevo_core_crunch_basico': P.CO,
  'nuevo_core_sit_up': P.CO,
  'nuevo_core_crunch_inverso': P.CO,
  'nuevo_core_crunch_bicicleta': P.CO,
  'nuevo_core_tijeras': P.CO,
  'nuevo_core_patadas_buceo': P.CO,
  'nuevo_core_toque_talones': P.CO,
  'nuevo_core_toque_puntas': P.CO,
  'nuevo_core_v_up': P.CO,
  'nuevo_core_elevacion_piernas_suelo': P.CO,
  'nuevo_core_captain_chair': P.CO,
  'nuevo_core_toes_to_bar': P.CO,
  'nuevo_core_l_sit': P.CO,
  'nuevo_core_hollow_body': P.CO,
  'nuevo_core_bird_dog': P.CO,
  'nuevo_core_dragon_flag': P.CO,
  'nuevo_core_mountain_climber': P.CO,

  // ÚLTIMOS - elevacion tibial (no es extensión tobillo exactamente, es dorsiflexión)
  // ultimo_elevacion_tibial_polea → sin pattern (dorsiflexión, no tiene patrón definido)
};

// ─── APPLY TO FILE ───
const content = fs.readFileSync(FILE, 'utf8');
let modified = content;
let tagged = 0;
let skipped = 0;

// Process in reverse order to preserve indices
const mkPositions = [];
const regex = /mk\(\s*\n?\s*'([^']+)'/g;
let m;
while ((m = regex.exec(content)) !== null) {
  mkPositions.push({ id: m[1], pos: m.index });
}

// Process from last to first
for (let i = mkPositions.length - 1; i >= 0; i--) {
  const { id, pos } = mkPositions[i];
  const pattern = MAP[id];
  if (!pattern) {
    skipped++;
    console.log(`SKIP (no pattern): ${id}`);
    continue;
  }

  // Find the opening '(' of mk(
  let openIdx = content.indexOf('(', pos);
  if (openIdx === -1 || openIdx - pos > 10) continue;

  // Track paren depth to find closing ')'
  let depth = 1;
  let j = openIdx + 1;
  while (j < content.length && depth > 0) {
    const ch = content[j];
    if (ch === '(') depth++;
    else if (ch === ')') depth--;
    // Skip string literals
    else if (ch === "'" || ch === '"' || ch === '`') {
      const quote = ch;
      j++;
      while (j < content.length && content[j] !== quote) {
        if (content[j] === '\\') j++; // skip escaped char
        j++;
      }
    }
    // Skip template literals
    else if (ch === '$' && content[j+1] === '{') {
      // template expression - track nested braces
      let braceDepth = 1;
      j += 2;
      while (j < content.length && braceDepth > 0) {
        if (content[j] === '{') braceDepth++;
        else if (content[j] === '}') braceDepth--;
        j++;
      }
      continue;
    }
    j++;
  }
  // j now points to the closing ')' + 1
  const closeParenIdx = j - 1;

  // Insert the pattern string before the closing ')'
  // Find the last non-whitespace before close paren
  const before = modified.substring(0, closeParenIdx);
  const after = modified.substring(closeParenIdx);

  // Add comma + pattern
  const insertStr = `,\n    '${pattern}'`;
  modified = before + insertStr + after;
  tagged++;
}

fs.writeFileSync(FILE, modified, 'utf8');
console.log(`\nDone! Tagged: ${tagged}, Skipped (no pattern): ${skipped}`);
console.log(`Total exercises: ${mkPositions.length}`);
