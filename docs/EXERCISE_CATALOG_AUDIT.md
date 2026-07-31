# Auditoría del catálogo de ejercicios

Fecha: 2026-07-31  
Fuente: `android-native/app/src/main/assets/exercise_database.json`  
Método: revisión semántica manual + aplicación de taxonomía del plan (sin clustering automático).

## Principio universal

| Clase | Tratamiento |
|-------|-------------|
| Canónico de familia | 1 entrada + chips del patrón |
| `KEEP_SPECIALTY` | Entrada propia (Spoto, Floor, Pin, Pendlay, Seal, Gironda, Gorilla, Renegado, Zottman, …) |
| Dimensión de chip | Nunca fila nueva |
| Duplicado / cartesiano | Merge + alias |
| Nonsense | Eliminar + alias |
| Exótico ejecutable | Conservar |

## Oleada P0 — Remo T

| id actual | Decisión |
|-----------|----------|
| `back_remo_barra_t_ancho` | MERGE → `back_remo_barra_t` (Libre, Amplio, Prono) |
| `back_remo_barra_t_cerrado` | MERGE → (Libre, Cerrado, Prono) |
| `back_remo_barra_t_pecho_apoyado_ancho` | MERGE → (En Máquina, Amplio, Prono) |
| `back_remo_barra_t_pecho_apoyado_cerrado` | MERGE → (En Máquina, Cerrado, Prono) |
| `back_remo_barra_t_maquina_ancho` | MERGE → (En Máquina, Amplio, Prono) |
| `back_remo_barra_t_maquina_cerrado` | MERGE → (En Máquina, Cerrado, Prono) |
| `back_remo_barra_t_maquina_pecho_apoyado_ancho` | MERGE → (En Máquina, Amplio, Prono) |
| `back_remo_barra_t_maquina_pecho_apoyado_cerrado` | MERGE → (En Máquina, Cerrado, Prono) |

**Canónico:** `back_remo_barra_t` — Remo en Barra T  
**Chips:** `station` (libre\|maquina), `grip_width` (wide\|medium\|close), `grip_orientation` (prono\|supino\|neutro)

## Oleada P0 — Press banca

| id actual | Decisión |
|-----------|----------|
| `tren_superior_press_banca_plano_barra` | CANÓNICO renombrado → Press de Banca con Barra |
| `tren_superior_press_banca_inclinado_barra` | MERGE → ángulo Inclinado, Libre |
| `tren_superior_press_banca_declinado_barra` | MERGE → ángulo Declinado, Libre |
| `tren_superior_press_pecho_maquina_smith` | MERGE → trayectoria Smith, Plano |
| `tren_superior_press_inclinado_smith` | MERGE → Smith, Inclinado |
| `tren_superior_press_banca_plano_mancuernas` | CANÓNICO → Press de Banca con Mancuernas |
| `tren_superior_press_banca_inclinado_mancuernas` | MERGE → Inclinado |
| `tren_superior_press_banca_declinado_mancuernas` | MERGE → Declinado |
| `tren_superior_press_spoto_barra` | KEEP_SPECIALTY (sale del VG padre; sin chest_pause) |
| `tren_superior_floor_press_barra` | KEEP_SPECIALTY |
| `tren_superior_floor_press_mancuernas` | KEEP_SPECIALTY |
| `tren_superior_press_banca_cadenas` | KEEP_SPECIALTY |
| `tren_superior_press_pecho_maquina_convergente` | CANÓNICO máquina (no absorbido) |
| `tren_superior_press_inclinado_maquina_convergente` | CHIP_OF máquina convergente o MERGE ángulo — default MERGE ángulo Inclinado en convergente |

**Chips Press Barra:** `bar_path` (libre\|smith), `bench_angle` (flat\|incline\|decline), `grip_width` (close\|medium\|wide)  
**Chips Press Mancuernas:** `bench_angle`, `grip_width`  
**Eliminado:** aspecto `chest_pause` (relleno)

## Oleada P0 — Otros remos (resumen)

| Canónico | Absorbe | Chips | Specialty aparte |
|----------|---------|-------|------------------|
| Remo con Barra | ancho/cerrado, EZ, neutra | grip_width, grip_orientation, implement | Pendlay |
| Remo en Smith | ancho/cerrado | grip_width, grip_orientation | — |
| Remo en Máquina | ancho/cerrado | grip_width, grip_orientation, laterality | — |
| Remo en Polea | baja/media/alta × ancho/cerrado/uni | cable_height, grip_width, grip_orientation, laterality | — |
| Remo Gironda | ancho/cerrado/uni | grip_width, grip_orientation, laterality | KEEP_SPECIALTY (siempre polea) |
| Remo con Mancuernas | (+ KB implement) | grip_width, grip_orientation, laterality, implement | pecho apoyado = canónico hermano |
| Seal / Gorilla / Renegado / Invertido / Banda | ver plan | mínimos | KEEP_SPECIALTY donde aplique |

## Nonsense P0

Curl Martillo + Barra Recta o Barra EZ (cualquier setup) → eliminar; alias al martillo con mancuernas/neutro/polea del mismo setup.

## Estado de aplicación

Ver sección “Aplicado” al final tras cada oleada de cleanup.

## Aplicado — oleada P0 (2026-07-31)

- Filas antes: 848
- Filas después: 795
- Eliminadas/fusionadas: 62
- Aliases nuevos: 62

### Detalle

- back_remo_barra_t_ancho → back_remo_barra_t {"station":"libre","grip_width":"wide","grip_orientation":"prono"} (Remo T)
- back_remo_barra_t_cerrado → back_remo_barra_t {"station":"libre","grip_width":"close","grip_orientation":"prono"} (Remo T)
- back_remo_barra_t_pecho_apoyado_ancho → back_remo_barra_t {"station":"maquina","grip_width":"wide","grip_orientation":"prono"} (Remo T)
- back_remo_barra_t_pecho_apoyado_cerrado → back_remo_barra_t {"station":"maquina","grip_width":"close","grip_orientation":"prono"} (Remo T)
- back_remo_barra_t_maquina_ancho → back_remo_barra_t {"station":"maquina","grip_width":"wide","grip_orientation":"prono"} (Remo T)
- back_remo_barra_t_maquina_cerrado → back_remo_barra_t {"station":"maquina","grip_width":"close","grip_orientation":"prono"} (Remo T)
- back_remo_barra_t_maquina_pecho_apoyado_ancho → back_remo_barra_t {"station":"maquina","grip_width":"wide","grip_orientation":"prono"} (Remo T)
- back_remo_barra_t_maquina_pecho_apoyado_cerrado → back_remo_barra_t {"station":"maquina","grip_width":"close","grip_orientation":"prono"} (Remo T)
- back_remo_barra_recta_ancho → back_remo_barra {"grip_width":"wide","grip_orientation":"prono","implement":"recta"} (Remo barra)
- back_remo_barra_recta_cerrado → back_remo_barra {"grip_width":"close","grip_orientation":"prono","implement":"recta"} (Remo barra)
- back_remo_barra_ez → back_remo_barra {"grip_width":"medium","grip_orientation":"prono","implement":"ez"} (Remo barra)
- back_remo_barra_neutra → back_remo_barra {"grip_width":"medium","grip_orientation":"neutro","implement":"neutra"} (Remo barra)
- back_remo_maquina_smith_ancho → back_remo_smith {"grip_width":"wide","grip_orientation":"prono"} (Remo Smith)
- back_remo_maquina_smith_cerrado → back_remo_smith {"grip_width":"close","grip_orientation":"prono"} (Remo Smith)
- back_remo_maquina_ancho → back_remo_maquina {"grip_width":"wide","grip_orientation":"prono","laterality":"bilateral"} (Remo máquina)
- back_remo_maquina_cerrado → back_remo_maquina {"grip_width":"close","grip_orientation":"prono","laterality":"bilateral"} (Remo máquina)
- back_remo_polea_baja_ancho → back_remo_polea {"cable_height":"baja","grip_width":"wide","grip_orientation":"prono","laterality":"bilateral"} (Remo polea)
- back_remo_polea_baja_cerrado → back_remo_polea {"cable_height":"baja","grip_width":"close","grip_orientation":"prono","laterality":"bilateral"} (Remo polea)
- back_remo_polea_baja_unilateral → back_remo_polea {"cable_height":"baja","grip_width":"medium","grip_orientation":"prono","laterality":"unilateral"} (Remo polea)
- back_remo_polea_media_ancho → back_remo_polea {"cable_height":"media","grip_width":"wide","grip_orientation":"prono","laterality":"bilateral"} (Remo polea)
- back_remo_polea_media_cerrado → back_remo_polea {"cable_height":"media","grip_width":"close","grip_orientation":"prono","laterality":"bilateral"} (Remo polea)
- back_remo_polea_media_unilateral → back_remo_polea {"cable_height":"media","grip_width":"medium","grip_orientation":"prono","laterality":"unilateral"} (Remo polea)
- back_remo_polea_alta_ancho → back_remo_polea {"cable_height":"alta","grip_width":"wide","grip_orientation":"prono","laterality":"bilateral"} (Remo polea)
- back_remo_polea_alta_cerrado → back_remo_polea {"cable_height":"alta","grip_width":"close","grip_orientation":"prono","laterality":"bilateral"} (Remo polea)
- back_remo_polea_alta_unilateral → back_remo_polea {"cable_height":"alta","grip_width":"medium","grip_orientation":"prono","laterality":"unilateral"} (Remo polea)
- back_remo_pecho_apoyado_polea_baja → back_remo_pecho_apoyado_polea {"grip_width":"medium","grip_orientation":"prono","cable_height":"baja","laterality":"bilateral"} (Remo pecho polea)
- back_remo_pecho_apoyado_polea_baja_unilateral → back_remo_pecho_apoyado_polea {"grip_width":"medium","grip_orientation":"prono","cable_height":"baja","laterality":"unilateral"} (Remo pecho polea)
- back_remo_pecho_apoyado_polea_media → back_remo_pecho_apoyado_polea {"grip_width":"medium","grip_orientation":"prono","cable_height":"media","laterality":"bilateral"} (Remo pecho polea)
- back_remo_pecho_apoyado_polea_media_unilateral → back_remo_pecho_apoyado_polea {"grip_width":"medium","grip_orientation":"prono","cable_height":"media","laterality":"unilateral"} (Remo pecho polea)
- back_remo_pecho_apoyado_polea_alta → back_remo_pecho_apoyado_polea {"grip_width":"medium","grip_orientation":"prono","cable_height":"alta","laterality":"bilateral"} (Remo pecho polea)
- back_remo_pecho_apoyado_polea_alta_unilateral → back_remo_pecho_apoyado_polea {"grip_width":"medium","grip_orientation":"prono","cable_height":"alta","laterality":"unilateral"} (Remo pecho polea)
- back_remo_gironda_ancho → back_remo_gironda {"grip_width":"wide","grip_orientation":"prono","laterality":"bilateral"} (Gironda)
- back_remo_gironda_cerrado → back_remo_gironda {"grip_width":"close","grip_orientation":"prono","laterality":"bilateral"} (Gironda)
- back_remo_gironda_unilateral → back_remo_gironda {"grip_width":"medium","grip_orientation":"prono","laterality":"unilateral"} (Gironda)
- back_remo_banda_ancho → back_remo_banda {"grip_width":"wide","grip_orientation":"prono"} (Remo banda)
- back_remo_banda_cerrado → back_remo_banda {"grip_width":"close","grip_orientation":"prono"} (Remo banda)
- back_remo_kettlebell → back_remo_mancuerna {"implement":"kettlebell","grip_width":"medium","grip_orientation":"prono","laterality":"bilateral"} (Remo DB/KB)
- back_remo_pecho_apoyado_kettlebell → back_remo_pecho_apoyado_mancuernas {"implement":"kettlebell"} (Remo pecho DB)
- back_remo_seal_kettlebell → back_remo_seal_mancuernas {"implement":"kettlebell"} (specialty implement)
- back_remo_gorilla_kettlebell → back_remo_gorilla_mancuernas {"implement":"kettlebell"} (specialty implement)
- back_remo_renegado_kettlebell → back_remo_renegado_mancuernas {"implement":"kettlebell"} (specialty implement)
- back_remo_invertido_barra_fija → back_remo_invertido {"equipment":"barra_fija"} (Invertido)
- back_remo_invertido_trx → back_remo_invertido {"equipment":"trx"} (Invertido)
- tren_superior_press_banca_plano_barra CANONICAL {"bar_path":"libre","bench_angle":"flat","grip_width":"medium"}
- tren_superior_press_banca_inclinado_barra → tren_superior_press_banca_plano_barra {"bar_path":"libre","bench_angle":"incline","grip_width":"medium"} (Press barra)
- tren_superior_press_banca_declinado_barra → tren_superior_press_banca_plano_barra {"bar_path":"libre","bench_angle":"decline","grip_width":"medium"} (Press barra)
- tren_superior_press_pecho_maquina_smith → tren_superior_press_banca_plano_barra {"bar_path":"smith","bench_angle":"flat","grip_width":"medium"} (Press barra)
- tren_superior_press_inclinado_smith → tren_superior_press_banca_plano_barra {"bar_path":"smith","bench_angle":"incline","grip_width":"medium"} (Press barra)
- tren_superior_press_banca_inclinado_mancuernas → tren_superior_press_banca_plano_mancuernas {"bench_angle":"incline","grip_width":"medium"} (Press DB)
- tren_superior_press_banca_declinado_mancuernas → tren_superior_press_banca_plano_mancuernas {"bench_angle":"decline","grip_width":"medium"} (Press DB)
- tren_superior_press_banca_plano_mancuernas CANONICAL
- tren_superior_press_spoto_barra KEEP_SPECIALTY
- tren_superior_press_banca_cadenas KEEP_SPECIALTY
- tren_superior_floor_press_barra KEEP_SPECIALTY
- tren_superior_floor_press_mancuernas KEEP_SPECIALTY
- tren_superior_press_inclinado_maquina_convergente → tren_superior_press_pecho_maquina_convergente {"bench_angle":"incline"} (Convergente)
- NONSENSE biceps_curl_de_pie_martillo_barra_recta → biceps_curl_de_pie_martillo_mancuernas
- NONSENSE biceps_curl_de_pie_martillo_barra_ez → biceps_curl_de_pie_martillo_mancuernas
- NONSENSE biceps_curl_sentado_banco_plano_martillo_barra_recta → biceps_curl_sentado_banco_plano_martillo_mancuernas
- NONSENSE biceps_curl_sentado_banco_plano_martillo_barra_ez → biceps_curl_sentado_banco_plano_martillo_mancuernas
- NONSENSE biceps_curl_inclinado_martillo_barra_recta → biceps_curl_inclinado_martillo_mancuernas
- NONSENSE biceps_curl_inclinado_martillo_barra_ez → biceps_curl_inclinado_martillo_mancuernas
- NONSENSE biceps_curl_predicador_martillo_barra_recta → biceps_curl_predicador_martillo_mancuernas
- NONSENSE biceps_curl_predicador_martillo_barra_ez → biceps_curl_predicador_martillo_mancuernas
- NONSENSE biceps_curl_arana_martillo_barra_recta → biceps_curl_arana_martillo_mancuernas
- NONSENSE biceps_curl_arana_martillo_barra_ez → biceps_curl_arana_martillo_mancuernas
- NONSENSE biceps_curl_drag_martillo_barra_recta → biceps_curl_drag_martillo_mancuernas
- NONSENSE biceps_curl_drag_martillo_barra_ez → biceps_curl_drag_martillo_mancuernas


---

## Oleada P1 — Jalón al Pecho

| id actual | Decisión | Chips destino |
|-----------|----------|---------------|
| `back_jalon_pecho_polea_ancho` | MERGE → `back_jalon_pecho_polea` | `grip_width=wide` |
| `back_jalon_pecho_polea_cerrado` | MERGE | `grip_width=close` |
| `back_jalon_pecho_polea_unilateral` | MERGE | `laterality=unilateral` |
| `back_jalon_neutro_polea` | MERGE | `grip_orientation=neutro` |
| `back_jalon_pecho_maquina_ancho` | MERGE → `back_jalon_pecho_maquina` | `grip_width=wide` |
| `back_jalon_pecho_maquina_cerrado` | MERGE | `grip_width=close` |
| `back_jalon_banda_ancho` | MERGE → `back_jalon_banda` | `grip_width=wide` |
| `back_jalon_banda_cerrado` | MERGE | `grip_width=close` |

**Canónicos:**
1. `back_jalon_pecho_polea` — chips: `grip_orientation` (prono/supino/neutro), `grip_width` (wide/medium/close), `laterality` (bi/uni).
2. `back_jalon_pecho_maquina` — chips: `grip_width`, `grip_orientation` (prono/cerrado; neutro si la máquina lo permite).
3. `back_jalon_banda` — chips: `grip_width` (wide/close).

## Oleada P1 — Dominadas

| id actual | Decisión | Chips destino |
|-----------|----------|---------------|
| `back_dominadas_pronas` | CANÓNICO `back_dominadas` | `grip_type=prono`, `load_type=bodyweight` |
| `back_dominadas_supinas` | MERGE | `grip_type=supino` |
| `back_dominadas_neutras` | MERGE | `grip_type=neutro` |
| `back_dominadas_lastradas` | MERGE | `load_type=loaded` |
| `back_dominadas_asistidas_maquina` | MERGE | `load_type=assisted_machine` |
| `back_dominadas_asistidas_banda` | MERGE | `load_type=assisted_band` |
| `back_dominadas_anillas` | KEEP_SPECIALTY o chip `grip_type` | Setup inestable = identidad propia. Default: chip dentro de canónico si se quiere unificar. |
| `back_dominadas_escapulares` | KEEP_SPECIALTY | Movimiento parcial (solo escápula), no es dominada completa. |
| `biceps_dominadas_supinas_cerradas` | MERGE en `back_dominadas` | `grip_type=supino`, `grip_width=close` |
| `biceps_dominadas_supinas_lastradas` | MERGE en `back_dominadas` | `grip_type=supino`, `load_type=loaded` |

**Nota:** Dominadas supinas en familia bíceps se unifican en `back_dominadas`. No duplicar fila.

## Oleada P1 — Curl de Bíceps (matriz completa)

| Canónico | Absorbe | Chips | Notas |
|----------|---------|-------|-------|
| `biceps_curl_de_pie` | Todos `de_pie_*` excepto martillo+barra/EZ | `grip_type` (supino/martillo/inverso/), `implement`, `laterality` | — |
| `biceps_curl_sentado_banco_plano` | Todos `sentado_*` | igual | — |
| `biceps_curl_inclinado` | Todos `inclinado_*` | igual | — |
| `biceps_curl_declinado` | Todos `declinado_*` | `grip_type`, `implement` (solo mancuernas) | Barra fija en declinado = NONSENSE si existe. |
| `biceps_curl_predicador` | Todos `predicador_*` | `grip_type`, `implement` | — |
| `biceps_curl_arana` | Todos `arana_*` | `grip_type`, `implement` | — |
| `biceps_curl_drag` | Todos `drag_*` | `grip_type`, `implement` | — |
| `biceps_curl_bayesian` | Todos `bayesian_*` | `grip_type`, `implement`, `laterality` | Siempre polea/tracción. |
| `biceps_curl_concentrado` | Todos `concentrado_*` | `grip_type`, `implement`, `laterality` | — |
| `biceps_curl_superman` | Todos `superman_*` | `grip_type`, `laterality` | Siempre polea. |
| `biceps_curl_crucifijo` | Todos `crucifijo_*` | `grip_type`, `laterality` | Siempre polea. |
| `biceps_curl_zottman_mancuernas` | KEEP_SPECIALTY | `implement` (mancuernas) | Identidad propia. |
| `biceps_curl_trx` | KEEP_SPECIALTY o merge en `de_pie` con `implement=trx` | — | Implemento inusual; default canónico propio. |

## Oleada P1 — Hip Thrust / Puente / Frog

| Canónico | Absorbe | Chips |
|----------|---------|-------|
| `glutes_hip_thrust` | Todos los bilaterales y unilaterales por equipo (barra/mancuerna/KB/disco/polea/banda/maquina/smith) | `laterality` (bi/uni), `equipment` (lista completa) |
| `glutes_puente_gluteos` | Puentes en suelo bi/uni por equipo | `equipment`, `laterality` |
| `glutes_frog_pumps` | Frog Pumps bi/uni | `equipment` (peso_corporal/disco) |

**Nota:** Hip Thrust ≠ Puente (suelo vs banco/esplenio). Mantener separados.

## Oleada P1 — Face Pull

| Canónico | Absorbe | Chips |
|----------|---------|-------|
| `deltoides_face_pull` | `polea`, `banda`, `polea_unilateral` | `equipment` (polea/banda), `laterality` (bi/uni) |

## Oleada P1 — Encogimientos

| Canónico | Absorbe | Chips |
|----------|---------|-------|
| `back_encogimientos` | Todos los shrugs por equipo; incluir tras nuca como chip | `equipment`, `bar_position` (front/behind_neck donde aplica) |
| `back_encogimientos_kelso` | Todos los Kelso (plano/inclinado × equipos) | `equipment`, `bench_angle` (flat/incline) |

## Oleada P1 — Elevación de Talones

| Canónico | Absorbe | Chips |
|----------|---------|-------|
| `calves_elevacion_talones_de_pie` | Todos los `de_pie` bi/uni (libre/máquina/smith/hack/v-squat/belt) | `laterality`, `equipment`, `station` (libre/smith/hack/v_squat/belt) |
| `calves_elevacion_talones_sentado` | Todos los `sentado` bi/uni | `laterality`, `equipment` |
| `calves_elevacion_talones_donkey` | Todos los donkey bi/uni | `laterality` |
| `calves_elevacion_talones_prensa` | Todos los en prensa (45/horizontal/vertical × bi/uni) | `press_angle` (45/horizontal/vertical), `laterality` |
| `calves_elevacion_tibial_anterior` | Todos los tibial anterior | `laterality`, `equipment` |

**Nonsense:** Talones en prensa con barra fija → no existe biomecánicamente; si aparece, mergear en `bodyweight` dentro del canónico prensa.


## Oleada P2 — Press de Hombros / Laterales

| Canónico | Absorbe | Chips | KEEP_SPECIALTY |
|----------|---------|-------|----------------|
| `deltoides_press_militar_de_pie` | `barra_recta`, `barra_ez`, `barra_neutra` | `implement` (recta/ez/neutra) | — |
| `deltoides_press_hombros_de_pie` | `mancuernas`, `kettlebell`, `banda`, `polea` | `equipment`, `laterality` (bi/uni polea) | — |
| `deltoides_press_hombros_sentado` | `barra_recta`, `barra_ez`, `barra_neutra`, `mancuernas`, `kettlebell`, `maquina`, `smith`, `polea` | `equipment`, `laterality` | — |
| `deltoides_press_arnold` | `mancuernas`, `kettlebell`, `polea` | `equipment` | ✅ Identidad propia (rotación). |
| `deltoides_push_press` | `barra_recta`, `mancuernas`, `kettlebell` | `equipment` | ✅ Identidad propia (impulso piernas). |
| `deltoides_press_landmine_unilateral` | — | — | ✅ Setup landmine. |
| `deltoides_press_z` | `barra_ez`, `barra_recta`, `mancuernas`, `kettlebell` | `equipment` | ✅ Búsqueda por nombre espera card propia. |
| `deltoides_elevaciones_laterales_de_pie` | `mancuernas`, `kettlebell`, `banda`, `polea`, `maquina`, `barra_ez`, `barra_recta` | `equipment`, `laterality` | — |
| `deltoides_elevaciones_laterales_sentado` | `mancuernas`, `kettlebell`, `polea`, `maquina` | `equipment`, `laterality` | — |
| `deltoides_elevaciones_laterales_inclinadas` | `mancuerna` | `laterality` | — |
| `deltoides_elevaciones_laterales_acostado` | `mancuernas`, `polea_cruzada`, `polea_unilateral` | `equipment`, `laterality` | — |
| `deltoides_elevaciones_laterales_super_rom` | `mancuernas`, `polea`, `polea_unilateral` | `equipment`, `laterality` | ✅ Identidad propia (Super ROM). |

## Oleada P2 — Tríceps

| Canónico / KEEP_SPECIALTY | Chips | Notas |
|---------------------------|-------|-------|
| Tríceps Pushdown | `equipment` (barra recta/EZ/cuerda), `grip` (prono/supino), `laterality` | Polea unilateral → chip `laterality`. |
| Tríceps Overhead | `equipment` (mancuerna/banda/polea), `laterality` | — |
| Tríceps Patada | `equipment` (polea/banda), `laterality` | — |
| Tríceps Press Francés | `bench_angle` (flat/incline/decline/floor), `equipment`, `laterality` | No absorber en Press de pecho. |
| Tríceps Press Máquina Selectorizada | — | CANÓNICO separado; no absorbe en pushdown ni francés. |
| Tríceps JM Press | `equipment` | ✅ KEEP_SPECIALTY. |
| Tríceps Tate Press | `equipment` | ✅ KEEP_SPECIALTY. |
| Tríceps Press California | `equipment` | ✅ KEEP_SPECIALTY. |
| Tríceps PJR | `equipment` | ✅ KEEP_SPECIALTY. |
| Tríceps Katana | `laterality` | ✅ KEEP_SPECIALTY. |
| Tríceps Crossbody | `laterality` | ✅ KEEP_SPECIALTY o hermano de patada. |

## Oleada P3 — Principios (Sentadilla / RDL / Peso Muerto / Zancada)

**No se hacen merges agresivos.** Se aplican principios universales:

1. **Canónico por patrón motor real:** Back Squat ≠ Front Squat ≠ Sumo Squat ≠ Sissy ≠ Hack ≠ V-Squat. Cada uno es canónico propio.
2. **Chips dentro del canónico:** `equipment`, `stance`, `laterality`, `load_position`, `deficit` según sea coherente.
3. **KEEP_SPECIALTY (test mental):** Anderson Squat, Zercher, Sissy, Jefferson, Pistol, Cossack, Búlgara (ya canónico).
4. **Nonsense:** Cualquier combinación biomecánicamente imposible (Pistol con barra recta, etc.).

| Familia | Canónicos propios | Chips típicos |
|---------|-------------------|---------------|
| Sentadilla trasera | `leg_back_squat` | `equipment` (barra alta/baja/SSB/DB/KB/smith), `stance` (normal/sumo) |
| Sentadilla frontal | `leg_front_squat` | `equipment`, `racked_position` (frontal/strap) |
| RDL Rumano | `leg_rdl` | `equipment` (barra/DB/KB/smith/polea), `stance` (conv/sumo/B-stance), `laterality` |
| Peso Muerto Convencional | `leg_deadlift` | `equipment`, `deficit` (yes/no) |
| Zancada (estática/caminando/inversa) | `leg_lunge_*` | `equipment`, `load_position`, `laterality` |

---

*Audit actualizado con familias P1–P3 completas. Referencia cruzada: `EXERCISE_CATALOG_REFINED_PLAN.md`.*


## Aplicado — oleada P1b Curl de Bíceps

- Filas antes: 782
- Filas después: 636
- Eliminadas/fusionadas: 157
- Aliases nuevos: 157

### Detalle

- MERGE biceps_curl_de_pie_supino_barra_recta → biceps_curl_de_pie {"grip_type":"supino","implement":"barra_recta","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supino_barra_ez → biceps_curl_de_pie {"grip_type":"supino","implement":"barra_ez","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supino_mancuernas → biceps_curl_de_pie {"grip_type":"supino","implement":"mancuernas","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supino_kettlebell → biceps_curl_de_pie {"grip_type":"supino","implement":"kettlebell","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supino_polea → biceps_curl_de_pie {"grip_type":"supino","implement":"polea","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supino_polea_unilateral → biceps_curl_de_pie {"grip_type":"supino","implement":"polea","laterality":"unilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supino_banda → biceps_curl_de_pie {"grip_type":"supino","implement":"banda","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supino_maquina → biceps_curl_de_pie {"grip_type":"supino","implement":"maquina","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_martillo_barra_neutra → biceps_curl_de_pie {"grip_type":"martillo","implement":"barra_neutra","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_martillo_mancuernas → biceps_curl_de_pie {"grip_type":"martillo","implement":"mancuernas","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_martillo_polea → biceps_curl_de_pie {"grip_type":"martillo","implement":"polea","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_martillo_polea_unilateral → biceps_curl_de_pie {"grip_type":"martillo","implement":"polea","laterality":"unilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_martillo_banda → biceps_curl_de_pie {"grip_type":"martillo","implement":"banda","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_martillo_maquina → biceps_curl_de_pie {"grip_type":"martillo","implement":"maquina","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_inverso_barra_recta → biceps_curl_de_pie {"grip_type":"inverso","implement":"barra_recta","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_inverso_barra_ez → biceps_curl_de_pie {"grip_type":"inverso","implement":"barra_ez","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_inverso_mancuernas → biceps_curl_de_pie {"grip_type":"inverso","implement":"mancuernas","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_inverso_polea → biceps_curl_de_pie {"grip_type":"inverso","implement":"polea","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_inverso_polea_unilateral → biceps_curl_de_pie {"grip_type":"inverso","implement":"polea","laterality":"unilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_inverso_banda → biceps_curl_de_pie {"grip_type":"inverso","implement":"banda","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_inverso_maquina → biceps_curl_de_pie {"grip_type":"inverso","implement":"maquina","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_de_pie_supinacion_mancuernas → biceps_curl_de_pie {"grip_type":"supinacion","implement":"mancuernas","laterality":"bilateral"} (Curl de_pie)
- MERGE biceps_curl_sentado_banco_plano_supino_barra_recta → biceps_curl_sentado_banco_plano {"grip_type":"supino","implement":"barra_recta","laterality":"bilateral"} (Curl sentado_banco_plano)
- MERGE biceps_curl_sentado_banco_plano_supino_barra_ez → biceps_curl_sentado_banco_plano {"grip_type":"supino","implement":"barra_ez","laterality":"bilateral"} (Curl sentado_banco_plano)
- MERGE biceps_curl_sentado_banco_plano_supino_mancuernas → biceps_curl_sentado_banco_plano {"grip_type":"supino","implement":"mancuernas","laterality":"bilateral"} (Curl sentado_banco_plano)
- MERGE biceps_curl_sentado_banco_plano_supino_polea → biceps_curl_sentado_banco_plano {"grip_type":"supino","implement":"polea","laterality":"bilateral"} (Curl sentado_banco_plano)
- MERGE biceps_curl_sentado_banco_plano_supino_polea_unilateral → biceps_curl_sentado_banco_plano {"grip_type":"supino","implement":"polea","laterality":"unilateral"} (Curl sentado_banco_plano)
- MERGE biceps_curl_sentado_banco_plano_supino_banda → biceps_curl_sentado_banco_plano {"grip_type":"supino","implement":"banda","laterality":"bilateral"} (Curl sentado_banco_plano)
- MERGE biceps_curl_sentado_banco_plano_martillo_barra_neutra → biceps_curl_sentado_banco_plano {"grip_type":"martillo","implement":"barra_neutra","laterality":"bilateral"} (Curl sentado_banco_plano)
- MERGE biceps_curl_sentado_banco_plano_martillo_mancuernas → biceps_curl_sentado_banco_plano {"grip_type":"martillo","implement":"mancuernas","laterality":"bilateral"} (Curl sentado_banco_plano)
... y 127 merges más
