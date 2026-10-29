# Post-RINGS · v2 aprobada

Contrato de implementación. Lo que no esté aquí no se toca.
Prototipo navegable: `prototipo.html`.

**Regla de alcance:** el cambio se limita a los cuatro puntos del encargo — módulo de
métricas de Inicio, hoja de preparación, hojas de cierre y su detalle. Todo lo demás
de la app se queda exactamente como está. Nada de refactors colaterales.

**Regla de copy:** fuera las aclaraciones obvias. Nada de «"Ya no" no borra el registro»,
«Del plan. Lo registrado se ve al cerrar», «Tu respuesta no cambia tus cargas» ni
«Autoinforme de 0 a 10». Si una etiqueta necesita una frase para explicarse, la etiqueta
está mal.

---

## 1. Inicio · «Métricas de tu entreno»

### 1.1 Estructura

```
┌─ KpknModule ────────────────────────────────────┐
│ MÉTRICAS DE TU ENTRENO            Historial ›   │
│ [ Series | Tonelaje | Esfuerzo ]   ← switch     │
│                                                 │
│ 128 series                        +12           │
│                                   vs. 4 sem.    │
│        ▲        ▲                               │
│  ▁     ▅   ▅    █   ← barras de 4 semanas       │
│ S−3   S−2  S−1  AHORA                           │
│ Cuádriceps 21,0 series · referencia 20          │
│ ─────────────────────────────────────────────── │
│ SUEÑO  ENERGÍA  ESTRÉS  AGUJETAS                │
│ 4/5    7/10     2/5     3/5                     │
│ ▮▮▮▮▯  ▮▮▮▮▮▮▮▯▯▯ ▮▮▯▯▯ ▮▮▮▯▯                │
│ ─────────────────────────────────────────────── │
│ PROGRESO                            Ver los 5 › │
│ Sentadilla trasera   ~~~~~   +2,5               │
│ Peso muerto rumano   ~~~~~   +1,8               │
└─────────────────────────────────────────────────┘
```

### 1.2 Switch de métrica

Tres estados, control segmentado compacto de 30 dp:

| Estado | Serie | Unidad | Delta |
|---|---|---|---|
| **Series** | series efectivas por semana | `series` | vs. bloque anterior de 4 semanas |
| **Tonelaje** | kg por semana | `kg · 4 sem.` | ídem |
| **Esfuerzo** | suma semanal de RPE × minutos | `u.a. · 4 sem.` | ídem |

El estado por defecto es **Series**. Cambiar de métrica reinicia la semana seleccionada.

### 1.3 Zonas de volumen (solo en Series)

Cada barra se colorea y marca según el **peor caso por músculo** de esa semana:

| Zona | Color | Símbolo |
|---|---|---|
| Por debajo de MEV | gris `white 20 %` | ninguno |
| MEV – MRV | crema `#F2EEE6` | ninguno |
| Por encima de MRV | ámbar `#FFA15C` | triángulo ▲ de 8×6 dp sobre la barra |

Al tocar una barra, el titular muestra esa semana y debajo aparece la frase concreta:
`Cuádriceps 21,0 series · referencia 20`. Cuando la semana está dentro de rango, la frase
no aparece. En Tonelaje y Esfuerzo no hay zonas: no existen referencias para esas métricas.

**Tabla de referencias** (series ponderadas por semana). Es una **heurística de
programación**, no una medición fisiológica ni un umbral clínico:

| Músculo | MEV | MAV | MRV |
|---|---|---|---|
| Cuello | 2 | 4–8 | 12 |
| Trapecio | 4 | 12–20 | 26 |
| Deltoides | 8 | 16–22 | 26 |
| Pectorales | 8 | 12–20 | 22 |
| Bíceps | 8 | 14–20 | 26 |
| Tríceps | 6 | 10–14 | 18 |
| Antebrazo | 2 | 6–10 | 14 |
| Dorsales | 10 | 14–22 | 25 |
| Erectores Espinales | 4 | 8–14 | 16 |
| Core | 4 | 10–16 | 20 |
| Abdomen | 6 | 16–20 | 25 |
| Glúteos | 4 | 8–16 | 20 |
| Aductores | 4 | 8–12 | 16 |
| Cuádriceps | 8 | 12–18 | 20 |
| Isquiosurales | 6 | 10–16 | 20 |
| Pantorrillas | 8 | 12–20 | 20 |
| Romboides | 4 | 8–14 | 18 |

Vive en un único `VolumeLandmarks.kt` con `version = "rp-heuristic-v1"` y una nota de
procedencia. No se presenta como «seguro» ni «peligroso»: dice **referencia**.

### 1.4 Aviso de autorregulación en la sesión de hoy

Tarjeta dentro del módulo «Esta semana», debajo de la fila de sesión. 78 dp de alto:
un glifo de datos animado de 60×60 a la izquierda, la frase en dos líneas y una fila
inferior con puntos de avance y la acción. Rota cada 9 s; una línea de 2 dp en el borde
inferior marca el tiempo hasta el siguiente. Tocar la acción pasa al siguiente aviso.

| Prioridad | Disparador | Copy | Acción |
|---|---|---|---|
| 1 | Molestia del catálogo repetida ≥ 3 sesiones en un ejercicio de hoy | «La rodilla anterior apareció en tus últimas 3 sentadillas.» | Ver |
| 2 | Serie a ≤ 1 rep del fallo o RPE ≥ 9,5 en un ejercicio de hoy, en las últimas 72 h | «Hiciste peso muerto a 2 repeticiones del fallo el viernes.» | Ajustar |
| 3 | sRPE alto en ≥ 3 sesiones consecutivas | «Llevas 3 semanas con esfuerzos muy altos.» | Ver carga |
| 4 | Músculo de hoy por encima de su MRV en ≥ 2 semanas | «Glúteos lleva 2 semanas por encima de tu referencia.» | Ver volumen |
| 5 | Ejercicio estrella sin mejora de e1RM en ≥ 4 sesiones | «Sentadilla lleva 4 sesiones sin subir.» | Ver progreso |
| 6 | Retorno tras ≥ 10 días sin sesiones | «Volviste después de 12 días. Hoy conviene bajar volumen.» | Ajustar |

Nunca se muestran dos avisos a la vez. Si no hay ninguno, la tarjeta no existe.

**El glifo es el dato, no un icono genérico.** Cada aviso dibuja su propia evidencia en 24×24:

| Aviso | Glifo | Animación |
|---|---|---|
| Molestia repetida | tres puntos de sesión; el último, rojo | el último late cada 2,4 s |
| Cerca del fallo | barra al 90 % con tope punteado | la barra crece al entrar; el tope parpadea |
| Esfuerzo acumulado | tres barras ascendentes | crecen escalonadas en 620 ms |
| Volumen sobre MRV | barra que cruza la línea punteada de referencia | crece al entrar |
| Estancamiento | línea plana con continuación punteada | se dibuja de izquierda a derecha |
| Retorno tras pausa | tren de puntos con un hueco | los del hueco se apagan |

Colores por tipo: rojo `#FF9B92` (molestia), ámbar `#FFA15C` (carga, fallo, volumen) y
crema `#C4BFB6` (estancamiento, retorno). Al cambiar de aviso el glifo se redibuja y el
texto entra con un fundido y 5 dp de desplazamiento en 340 ms.

Nunca usa la palabra «sRPE» ni «ACWR». Habla de esfuerzo, carga y molestias.

---

## 2. Detalle · «Métricas de tu entreno»

Pantalla completa. Segmentado 1 / 4 / 12 semanas (los tres valores de `TrainingInsightsEngine`).

- **Carga**: línea con puntos (mismo lenguaje que `BodyTrendChart`), con la leyenda
  `MEV–MRV` / `Por encima de MRV`. Sin media, sin banda y sin umbral clínico.
- **Cómo respondes**: matriz 4 semanas × 4 filas — Sueño, Energía, Estrés y **Agujetas**.
  La celda de Agujetas muestra el **máximo de las zonas registradas** (no un índice).
- **Molestias contextuales**: cada fila lleva zona + contexto obtenido de la sesión en vivo:
  `Sentadilla trasera · serie 3 · RPE 8 · Semana 2 · 23 oct`. El contador de la derecha es el
  número de sesiones en que apareció. Las resueltas van en gris con 0.
- **Progreso · 5 más pesados**: los cinco ejercicios con mayor carga registrada (e1RM), cada
  uno con sparkline y delta. Debajo, `Ver todos ›` lleva a la pantalla de progreso por
  ejercicio que ya existe. En Inicio se muestran solo dos y el enlace dice `Ver los 5 ›`.

**Dato que falta para el contexto de molestias:** `ExerciseDiscomfortReport` no guarda en qué
serie se reportó. Hay que añadir `reportedAfterSetIndex: Int?` al registrar la molestia durante
la sesión. Para el histórico sin ese campo se muestra lo que sí existe (ejercicio, semana, RPE,
número de series del ejercicio).

---

## 3. Hoja «Antes de entrenar»

Sin scroll. La hoja mide lo que mide su contenido (`height: wrap`, techo 94 %).

```
────
ANTES DE ENTRENAR
¿Cómo llegas hoy?              ← una sola línea, se ajusta si no cabe
Fuerza · Pierna · Semana 1

ENERGÍA                          7
[0][1][2][3][4][5][6][7][8][9][10]

SUEÑO                            4
[1][2][3][4][5]

ESTRÉS                           2
[1][2][3][4][5]

AGUJETAS                  0–5 por zona
Glúteos      ●●○○○   Cuádriceps ●●●●○
Isquios      ●○○○○   Erectores  ○○○○○

MOLESTIAS                    Ninguna
[ Buscar molestia                 ]
[Sin molestias][Rodilla anterior][Lumbar][Hombro anterior]…

[ Habilitar modo de voz ]   ( ✓ )
```

- **Título**: 24 sp, `maxLines = 1`, tamaño ajustado hasta que quepa. «hoy» nunca baja.
- **Escalas**: 30 dp de alto, celdas iguales, la elegida en crema y las anteriores al 13 %.
  Energía 0–10; sueño y estrés 1–5.
- **Agujetas**: 0–5 por zona, solo los músculos con más series del plan de hoy (4–6) más
  los que el usuario añada. Sin scroll: dos columnas.
- **Molestias**: el catálogo existente (`DISCOMFORT_CATALOG_BY_ID`) con su buscador. Chips de
  las más probables primero; el buscador filtra el resto. «Sin molestias» es excluyente.
- **Sin campo de nota.**
- **FAB de 64 dp con ✓** a la derecha; a su izquierda, la píldora de voz.
- **Voz**: al tocar se abre el selector con los dos modos ya existentes
  (`VoiceCaptureMode.HANDS_FREE` y `MUSIC`). La primera vez que se activa se pide permiso de
  micrófono y se explica qué escucha; después el botón solo alterna y muestra el modo activo.
  El estado se guarda en `Settings.voiceCaptureMode`.

### 3.1 Cobertura del DOMS: por zona, no global

Recomendación: **por zona, limitado a los músculos de la sesión de hoy**.

1. El DOMS es local. Un valor global mezcla regiones y no permite decidir qué tocar hoy.
2. La literatura de monitorización subjetiva (Saw et al. 2016; Beattie et al. 2017) respalda
   las medidas subjetivas por su sensibilidad, pero un ítem global tipo Hooper solo se
   justifica cuando el cuestionario debe ser mínimo. Aquí ya hay tres escalas: añadir 4–6
   toques localizados cuesta poco y aporta mucho más.
3. El modelo ya tiene DOMS por músculo: `PostSessionFeedback.muscleFeedback[doms]` (1–5).
   Usar la misma unidad antes y después de la sesión permite comparar sin inventar nada.
4. El DOMS no mide daño ni recuperación. Se guarda 0–5 por zona y **no se convierte en score**.
   En la matriz semanal se muestra el máximo de las zonas, rotulado como valor máximo, no
   como índice global.

Persistencia: `muscleDoms: Map<String,Int>` viaja en el payload de wellbeing
(`training_observations`, `kind = "wellbeing"`). El campo global `DailyWellbeingLog.doms`
queda nulo: no se deriva un máximo para no fabricar un índice.

---

## 4. Hojas «Cerrar sesión»

Tres pestañas con `KpknSegmentedControl` (Resumen por defecto), pie fijo con guardar.

El pie es idéntico en las tres pestañas y lleva, sobre el botón, el interruptor
**Compartir al guardar**: al activarlo, guardar la sesión abre la hoja de compartir con la
historia ya elegida. El estado vive con la sesión y no se reinicia al cambiar de pestaña.

### 4.1 Resumen

```
SESIÓN COMPLETADA
Fuerza · Pierna
[ Resumen | Progreso | Anotaciones ]

SERIES            TONELAJE
34                12.480 kg
                  +840 kg
DURACIÓN          sRPE
94 min            8

VOLUMEN POR MÚSCULO            Ver todos ›
Glúteos      ███████████  12,5
…

PROGRESO            vs. última vez
★ Sentadilla trasera  ~~~~  +2,5
Peso muerto rumano    ~~~~  +1,8
Prensa de piernas     ~~~~  =
```

- Cuatro cifras: series, tonelaje (con delta frente a la última vez que se hizo esta misma
  sesión), duración y **sRPE** en lugar de RIR medio.
- Volumen por músculo de la sesión, directo en crema e indirecto en gris.
- Progreso: los **3 principales más los marcados como ejercicio estrella** (★), sin repetir.

**sRPE y RIR.** El valor mostrado es la sesión en escala 0–10: `sessionEffortRpe` declarado;
si no existe, la media de RPE de las series. Internamente **todo se habla en RPE**:
`rpe = 10 - rir` se calcula al leer, nunca se reescribe el RIR que el usuario registró ni se
cambia lo que ve en su historial. La conversión vive en un único
`RpeConversion.fromRir(rir)` documentado; el registro sigue siendo del atleta.

### 4.2 Progreso / Hitos

- **Tonelaje de la sesión**: línea con los últimos 8 registros de esa misma sesión, con la
  referencia punteada de la vez anterior. Encabezado con el delta (`+840 kg`).
- **Ejercicios**: todos los de la sesión, cada uno con nombre, mejor serie, e1RM, sparkline
  compacto y delta. Tocar una fila abre el detalle de progreso que ya existe.
- **Hitos**: solo dos tipos reales, `pr_e1rm` y `star_goal_reached`. Nada de «subiste
  tonelaje» ni «completaste la sesión». Cada tarjeta muestra el valor nuevo y el anterior.

### 4.3 Anotaciones

Nota de la sesión, tareas (`sessionChecklist` con añadir, marcar y borrar), esfuerzo 0–10
(autoinforme) y el acceso a fotos e historia.

### 4.4 Hoja «Compartir»

`KpknSheet` con altura ajustada al contenido.

```
Compartir                              ×
[ General | Ejercicio | Fotos ]
┌───────────────────┐
│  vista previa 9:16 │
└───────────────────┘
INCLUIR
[Mejoras en kg] [Duración] [Intensidad]
[ Guardar imagen ]   [ Compartir ]
```

- La vista previa sale del mismo `renderWorkoutStory` que produce el PNG. No hay maqueta
  paralela: lo que se ve es lo que se comparte.
- El selector de plantilla pasa de carrusel de vecinos (74×132) a segmentado: la vista previa
  gana tamaño y la hoja se entiende de un vistazo.
- `Incluir` solo muestra las opciones que aplican a la plantilla activa (`improvements`,
  `duration`, `intensity`).
- `Compartir` usa `WorkoutShareService`: intent `ADD_TO_STORY` de Instagram si está
  instalado y, si falla, el selector de apps con WhatsApp incluido. `Guardar imagen` escribe
  en Imágenes/KPKN.
- El permiso de almacenamiento solo se pide en API ≤ 28.

### 4.5 Diseño de la historia

Lienzo lógico 360 × 640 renderizado a 1080 × 1920 (×3). Negro y crema, sin RINGS, sin
cristal y sin degradados de color. Una sola pieza de color: el verde de las mejoras.

```
KPKN                              MAR 13 OCT
────────────────────────────────────────────
FUERZA · PIERNA
Sesión de pierna
• PR e1RM · 112,5 kg

SERIES          TONELAJE
34              12.480 kg
DURACIÓN        sRPE
94 min          8
────────────────────────────────────────────
EJERCICIOS
Sentadilla trasera     100×8      +2,5
Peso muerto rumano     140×5      +1,8
Prensa de piernas     214×10         =
Hip thrust            120×10      +4,0
Curl femoral           55×12         =
TONELAJE · 6 SESIONES
            ~~~~~~~~~~~~
KPKN · SEMANA 1                    13 OCT
```

Reglas de composición:

- Fondo `#17130F → #0B0A09 → #050505` con un resplandor cálido muy tenue arriba. Si el
  programa tiene portada, entra a sangre con velo negro al 62 % y degradado a negro en el
  tercio inferior; el texto nunca cae sobre la zona clara.
- Syne para título y cifras; Inter para el resto. Como máximo tres tamaños por historia.
- Hairlines al 10 % para separar bloques. Sin cajas ni sombras.
- El badge de PR solo existe si hay un `pr_e1rm` real. Nada de «buen trabajo» inventado.
- La lista se corta a 5 ejercicios: se omiten antes que encoger la letra.
- Pie con programa y semana a la izquierda; fecha a la derecha.
- Plantilla **Ejercicio**: cifra grande, serie × reps, RIR, 1RM estimado y gráfico con fechas.
- Plantilla **Fotos**: una foto grande o rejilla 2×2 con el mismo cromo y el tonelaje al pie.

---

## 5. Cambios de datos

1. `DailyWellbeingLog` gana `muscleDoms: Map<String,Int> = emptyMap()` (0–5 por zona).
   `energy` ya estaba previsto en v1 (0–10).
2. `ExerciseDiscomfortReport` gana `reportedAfterSetIndex: Int? = null`.
3. `TrainingObservationRepository.recordWellbeing(...)` escribe `kind = "wellbeing"` con
   `preserve(IGNORE)`, idempotente por `sourceIdentity = "wellbeing:<fecha>"`.
4. Nuevo, en `domain/training/insights/` (Kotlin puro):
   - `VolumeLandmarks` + `volumeZone(muscle, weeklySets)`.
   - `CompletedMuscleVolume.sessionSets(log)` desde `CompletedExercise.effectiveMuscles`.
   - `TrainingResponseSummary.build(logs, wellbeing, nowMs)`.
   - `SessionCueEngine.cues(logs, todaySession, nowMs)` con las seis reglas y su prioridad.
5. `RpeConversion.fromRir`. Sin migración de datos.

Nada más: sin tabla nueva, sin motor fisiológico.

---

## 6. Otros puntos que siguen pendientes (fuera del encargo, no tocar ahora)

Se mantienen de la v1: unificar el texto de las hojas al crema de marca, borrar
`WorkoutFinishPages.kt` (UI muerta), borrar `HomeRingRange/HomeRingSparkline` y los roles
tipográficos sin uso, limpiar `feedbacks` no pintados, retirar rutas `settings/auge` y
`rings/mis-rings`, y mover los literales a `strings.xml` al implementar.

Se harán en un pase separado y con su propia aprobación.

---

## 7. Criterios de aceptación

- Ninguna pantalla nueva usa `FilterChip`, `Slider` ni `MaterialTheme.colorScheme.surfaceVariant`.
- Todo texto ≥ 12 sp; toda cifra en Syne con `tnum`.
- La hoja de preparación no scrollea a 393×852 con escala de fuente 1.0.
- «¿Cómo llegas hoy?» ocupa una línea; «hoy» nunca baja.
- El switch de métricas cambia barras, unidad, delta y nota de zona sin recargar la pantalla.
- Las zonas de volumen solo aparecen en Series.
- Los hitos solo pueden ser `pr_e1rm` o `star_goal_reached`.
- La conversión RIR→RPE no altera ningún valor almacenado.
- Con `wizardReducedMotion()` no hay barras animadas ni respiración del punto.
- Ningún copy explica lo que la propia interfaz ya dice.
- El interruptor «Compartir al guardar» está visible en las tres pestañas y su estado se
  conserva al cambiarlas.
- La vista previa de la historia se genera con `renderWorkoutStory`; ninguna maqueta aparte.
- La historia no contiene anillos, baterías ni porcentajes de recuperación.
- El aviso de Inicio cambia de glifo y de copy en el mismo gesto, y la línea de progreso se
  reinicia con cada aviso.
