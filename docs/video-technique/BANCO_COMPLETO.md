# Banco completo: los 1.000 vídeos en todas las pruebas (VT11, D41)

Orden del usuario (20-oct): «Tenemos un banco de 1000 vídeos. Te ordeno a que los uses todos para las pruebas». Exige además que la
función sea USABLE y con la fiabilidad de las apps del mercado. El listón por métrica sale de `MERCADO_FIABILIDAD.md`, la
investigación del 20-oct.

## Qué hay
- **1.076 clips etiquetados** en `C:\kw\vt-bench\labels\<id>.json`: el banco del usuario
  (`artifacts\exercise-video-corpus-20261014\01_videos\`) y 76 públicos con licencia (`g*`, `r*` y `x*`, en `C:\kw\vt-bench\dl\`).
  - El banco del usuario: Kaggle 418, UCF101 480, wger 77 y YMove 25.
  - Cada etiqueta trae `usable`, `unusableReasons`, `suggestedProfile`, `view`, `people`, `reps` (con `acceptableRange`),
    `repTimesS` y `activeWindowS`.
- **Útiles: 513.** Kaggle 214, UCF 202, wger 30, YMove 18 y públicos 49.
  - Por vista: de lado 180, de frente 126, oblicua 108, de espaldas 55 y tumbado 44.
  - Por perfil: flexiones 105, banca 86, dominada y jalón 68, sentadilla 65, curl 42 y el resto por debajo de 25.
- **No útiles: 563.**
  - Ejercicio que v1 no analiza: 285.
  - Cámara en movimiento: 152.
  - Persona cortada: 32.
  - Sin ningún ciclo completo: 31.
  - Zancada caminando: 29.
  - Cortes de edición: unos 14.
  - Varias personas: 13.
  - No es el ejercicio indicado: 10.
- **Trazas actuales:** 513 (97 compatibles y 416 externas), extraídas a 12,5 o 15 Hz por el fallo que corrige M1. No sirven para la
  medición final: tras M1 se vuelve a extraer TODO.

## Diseño: todos los vídeos cuentan, cada uno para lo que sirve
| Grupo | Clips | Qué se mide | Fallo grave |
|---|---|---|---|
| G1 · útiles | 513 | serie exacta, dentro del rango y error; reparto ALTA/MEDIA/BAJA; precisión de cada nivel; por perfil, vista, fuente y fps | un número ofrecido (ALTA o MEDIA) que está mal |
| G2 · problemas con ejercicio soportado | 278 | si se abstiene: cuántas veces ofrece un número y cuántas acierta; la zancada caminando debe salir sin reps | ALTA con un número equivocado |
| G3 · ejercicio no soportado | 285 | que diga «aún no se analiza» y no ofrezca ningún número | cualquier número ofrecido |

- **La cifra comparable con el mercado** es G1 con encuadre bueno: la vista que admite el perfil, cuerpo entero y una persona. Es lo
  que pide el grabador de la app. Las demás se publican aparte, sin mezclarlas.
- **Con D40, MEDIA también ofrece «Usar ≈ N».** Por eso la precisión de MEDIA cuenta tanto como la de ALTA.

## Honestidad: desarrollo y examen
- **Antes de extraer** se congela un examen del 20 % del banco, por grupos: el vídeo original en Kaggle, el grupo `gXX` en UCF y el
  clip en el resto. Lleva su hash.
  - Ese 20 % TAMBIÉN se usa: es el examen final.
  - Nunca se mira clip a clip ni se ajusta con él, y se evalúa una sola vez, al final.
- **El 80 % restante es de desarrollo:** diagnóstico, arreglos y ajuste, con validación cruzada por grupos.
- **Por qué:** un motor ajustado con los mismos vídeos con los que se le califica parece mejor de lo que es. El número que se
  publica es el del examen.

## Listón (de `MERCADO_FIABILIDAD.md`, 20-oct)
| Métrica | Listón con encuadre bueno | Referencia publicada |
|---|---|---|
| Serie exacta, básicos | ≥ 95 % | Metric VBT en sentadilla: precisión y recall de 1,000; en banca, 0,959 y 0,985 (Trowell 2024) |
| Serie exacta, el resto de ejercicios admitidos | ≥ 90 % | — |
| Series exactas entre las que dicen «seguro» (ALTA) | ≥ 99 %, sin esconder la abstención | Qwik VBT, sin omisiones ni fantasmas en 589 reps; Metric VBT, 52 omisiones y 16 fantasmas (Renner 2024) |
| MCV con el disco calibrado | MAE ≤ 0,03 m/s y RMSE ≤ 0,04 m/s | Qwik VBT, RMSE de 0,01–0,04 m/s (Renner 2024) |
| Pérdida de velocidad | MAE ≤ 5 puntos porcentuales | — |
| Fases y TUT | MAE ≤ 0,15 s por fase y ≤ 0,20 s por repetición (provisional) | — |

- **Cualquier encuadre:** no hay garantía publicada que comparar. Se mide, y la app se abstiene cuando la captura no da para más.
- **Con D40, MEDIA** también ofrece el número. Su precisión se publica aparte, y el objetivo es que la serie exacta en MEDIA no baje
  del 90 %.
- **Lo que el banco no puede probar:** m/s, pérdida de velocidad y fases. Requieren el teléfono con una referencia, por ejemplo un
  desplazamiento conocido o un transductor.

## Pasos
1. **M1** (en marcha): la pose nunca baja de 20 Hz.
2. **VT11a, herramientas** (Codex, sin emulador; brief `briefs/VT11a.md`):
   - el manifiesto congelado de los 1.076 clips, con los grupos y el examen;
   - el conductor de la extracción por lotes, que puede reanudarse;
   - el evaluador del banco completo en el arnés JVM (`C:\kw\vt-jvm`), probado con las trazas viejas.
3. **APK de depuración** con M1, e **instalación en el AVD `Medium_Phone`**.
4. **Extracción** de los 1.076 clips en el emulador: lotes de 12 y unas 3–6 h. La lanza el orquestador.
   - Las trazas van a `C:\kw\vt-bench\full-20261020\traces\`, fuera del repo (D33).
   - Al terminar: apagar el emulador y borrar sus `*.qcow2` de datos, `snapshots` y `tmpAdbCmds`.
5. **Medición de partida** en el desarrollo (80 %), con un informe agregado: `BANCO_COMPLETO_RESULTADOS.md`.
6. **Rondas del motor** (como mucho 3), con Codex.
   - Cada una: arreglos → repetición en JVM sobre las trazas (minutos, sin volver a extraer) → informe.
   - Si un cambio toca la pose o el decodificador, hay que volver a extraer.
   - Prioridad, según el diagnóstico:
     - el recorte del final de la serie;
     - la ambigüedad (`n_amb`);
     - las vistas admitidas (fondos y dominadas de frente);
     - el curl con barra;
     - la pérdida de señal;
     - la cámara en movimiento y la zancada caminando.
7. **Examen final** con el 20 %, una sola vez, y comparación con el listón del mercado.
8. **Teléfono:** tiempo de análisis, calor, 30 y 60 fps reales y m/s con un desplazamiento conocido. El banco no tiene verdad de m/s
   ni de fases a nivel de fotograma.

## Recursos
- **Gradle:**
  - el arnés JVM, por `KPKN_SLOT_LANE=jvm` (1 GB);
  - el APK, por la cola Android;
  - nunca dos compilaciones Android mías a la vez.
- **Emulador:**
  - uno solo, `Medium_Phone`, con los vídeos subidos por lotes y borrados tras cada lote;
  - durante la extracción no arranca ningún otro.
- **Disco:** las trazas pesan poco, unos MB por clip. Los vídeos nunca entran en el repo.

## Manifiesto

Congelado por VT11a el 20-oct-2026, antes de la extracción nueva:

- Archivo externo: `C:/kw/vt-bench/full-20261020/manifest.json` (1.076 filas, versión 1).
- SHA-256 literal: `8168ab0909e3c93d4776af713c5fafda1ae91e82ba910a5aa9868b49bfeafb71`.
- Semilla: **20261020**. No se sobrescribe este manifiesto: cualquier corrección requiere otro nombre y otra versión.
- **471 grupos**: desarrollo **377 grupos / 895 clips**; examen **94 grupos / 181 clips**. El 20 % redondeado se aplica a grupos
  (94/471 = 19,96 %), por eso el porcentaje de clips del examen es distinto.

| Fuente | Clips totales | Grupos totales | Clips desarrollo | Clips examen | Grupos desarrollo | Grupos examen |
|---|---:|---:|---:|---:|---:|---:|
| Kaggle_WorkoutFitness | 418 | 22 | 371 | 47 | 18 | 4 |
| UCF101 | 480 | 271 | 382 | 98 | 217 | 54 |
| wger | 77 | 77 | 62 | 15 | 62 | 15 |
| YMove | 25 | 25 | 20 | 5 | 20 | 5 |
| pexels | 45 | 45 | 36 | 9 | 36 | 9 |
| pixabay | 14 | 14 | 11 | 3 | 11 | 3 |
| mixkit | 9 | 9 | 7 | 2 | 7 | 2 |
| commons | 8 | 8 | 6 | 2 | 6 | 2 |
| **Total** | **1.076** | **471** | **895** | **181** | **377** | **94** |

| Categoría | Clips totales | Clips desarrollo | Clips examen | Grupos presentes | Grupos desarrollo | Grupos examen |
|---|---:|---:|---:|---:|---:|---:|
| G1 · usable | 513 | 431 | 82 | 231 | 186 | 45 |
| G2 · problemas, perfil soportado | 278 | 232 | 46 | 128 | 103 | 25 |
| G3 · no soportado | 285 | 232 | 53 | 137 | 108 | 29 |

Los grupos de la tabla por categoría son incidencias: un mismo grupo puede contener G1 y G2, de modo que esas columnas no se
suman. Las fuentes sí forman una partición. Ningún grupo cruza desarrollo/examen ni los folds. Los folds de desarrollo 0–4
contienen respectivamente **77, 73, 77, 75 y 75 grupos**.

**Agrupación y estratificación.** Se leyeron `02_catalogo/clasificacion.csv` y `corpus` de las etiquetas. En Kaggle, las 418 filas
conservan `source_group=unknown`; `original_filename` identifica cada fragmento y `source_url` el dataset, sin una identidad fiable
del vídeo original previo al recorte. Se aplica la alternativa del brief: **un grupo por ejercicio de origen**, 22 en total,
sin tratar los nombres numerados como grabaciones independientes. En UCF se usa **clase + gXX**: gXX es el grupo de grabación dentro
de la clase y su numeración se reutiliza entre clases. En las demás fuentes se usa el id del clip, con prefijo de fuente.

Las cuotas de examen por fuente se redondean por restos mayores hasta 94 grupos. Dentro de cada fuente se estratifica por
**categoría × perfil sugerido**, usando la incidencia del grupo en todos sus estratos cuando es mixto: 512 sorteos con la semilla
fija y cambios que reducen el desequilibrio. No se utiliza ninguna predicción. Los estratos con pocos grupos no permiten un 20 %
exacto. Los folds se asignan también por grupos y estratos. El algoritmo está en el script externo `prepare_full_bank.py`; el
control local reprodujo las asignaciones sin reescribir el manifiesto. `partition-audit.json`, en la misma carpeta, conserva
los recuentos de cada estrato y fold.

**Integridad.** No faltó ninguna ruta de vídeo y no hubo hashes discrepantes: **0 ausentes / 0 diferencias**. Se contrastaron
los 1.000 SHA-256 de vídeo del catálogo; para los 76 públicos sin un hash previo independiente se calculó el SHA-256 ahora.
Todos los vídeos se leyeron por bloques. Cada fila conserva la ruta absoluta original, los SHA de vídeo y etiqueta (bytes
literales, sin normalizar CRLF), fuente, grupo, categoría, especificación de ejercicio, fold y examen. `input-audit.json` conserva
el resultado fuera del repo.

**Límite de extracción G3.** El `AnalyzeSpec` actual solo admite perfiles del enum soportado. Para obtener trazas de esos 285
vídeos, sus filas declaran `benchProfile=SQUAT` y `forcedProfileProbe=true`, conservando `profile=UNSUPPORTED`. Es un perfil de
transporte: las tasas G3 bajo perfil forzado se publican como diagnóstico y el rechazo real del resolver/UI queda
`NOT_VALIDATED`. Medirlo exige ampliar `AnalyzeFileBench`, fuera de los archivos autorizados para VT11a. Esto no se sustituye
por una abstención inferida de la etiqueta.

El manifiesto de validación histórica es **otro archivo**, `full-20261020/validation-old/manifest.json`: solo los 59 clips de ajuste
y los 416 externos, sin las 37 reservas antiguas ni x14. No cambia el manifiesto congelado ni ejecuta el examen nuevo.

## Extracción y medición de partida (21-oct)
- **Extracción en el Z Flip5, con GPU:** 1.076/1.076 clips completos, en segundo plano.
  - Las trazas están en `C:\kw\vt-bench\full-20261020-flip\traces\`.
  - Se usaron dos APK: 636 clips con `6698a485…` (M1) y 440 con `28e7b3d3…` (el mismo más el servicio `specialUse`, VT11c).
- **Partida oficial** (motor 6, desarrollo, 895 clips; informe en `full-20261020-flip\report-dev\`):
  - **G1 (431):** con salida el 62 %.
    - ALTA: 44, con el 88,6 % exactas.
    - MEDIA: 155, con el 80,6 % exactas.
    - BAJA: 69.
  - **Encuadre bueno (133):** ALTA 91 % y MEDIA 81 % exactas; el 26 % sale sin número.
  - **G2:** ofrece número en el 27 %, y el 25 % de esos números está mal.

## Rondas del motor: tres niveles de datos (D60, 21-oct)
- **Ajuste: `C:\kw\vt-tune\`.**
  - 720 clips de desarrollo, folds 0–3.
  - Es lo único que ve el agente que ajusta el motor. Ajusta con validación cruzada dejando un fold fuera.
- **Control ciego: `C:\kw\vt-bench\dev-check-20261021\`.**
  - 175 clips de desarrollo, fold 4.
  - Lo corre solo el orquestador tras cada ronda. Al agente solo le llegan cifras agregadas.
- **Examen: el banco completo.**
  - 181 clips, el 20 %.
  - Se evalúa una vez, al final.
- **Cómo están hechos los paquetes** (`C:\kw\vt-bench\make_dev_packages.py`):
  - Cada uno trae el manifiesto congelado con lo ajeno sellado: `exam=true`, sin etiqueta, trazas ni ruta de vídeo.
  - Lleva su propio hash y un `exam-evaluation.json` previo, que bloquea `EXAM=1`.
  - Las cifras por fold salen con `fold_baseline.py`.
- **Por qué:** con 1–3 rondas de ajuste sobre el mismo desarrollo, el fold ciego avisa del sobreajuste antes de gastar el examen, que
  es de un solo uso.
- **Ronda 1: M2** (brief `briefs/M2.md`).

## Examen final (21-oct, motor 9, una sola vez)
Ejecutado con `feat/vt-ui` @d6f0bd7bc sobre los 181 clips del examen (trazas del Z Flip5, GPU). Informe externo:
`C:\kw\vt-bench\full-20261020-flip\report-exam\`. El marcador `exam-evaluation.json` impide repetirlo.

| Medida | Examen | Listón |
|---|---|---|
| G1 (82): con número | 63 (76,8 %) | — |
| G1: exacto entre los que dan número | 45/63 (71,4 %) | — |
| ALTA: exactas | 8/10 (80 %; IC95 49–94 %) | ≥ 99 % |
| MEDIA: exactas | 22/22 (100 %; IC95 85–100 %) | ≥ 90 % |
| Encuadre bueno (28): con número / exacto entre salidas | 25 (89 %) / 18 (72 %) | — |
| Serie exacta, básicos (sin número = fallo) | 35/49 (71 %) | ≥ 95 % |
| Serie exacta, el resto | 10/33 (30 %) | ≥ 90 % |
| G2: equivocadas entre ofrecidas | 2/5 | — |

- **Las dos ALTA equivocadas** son dominadas de Kaggle (`kaggle_pull_up_1` y `_2`) con una repetición de menos (7 de 8 y 3 de 4):
  un error de borde sistemático de un grupo que estaba entero en el examen.
- **MEDIA** acierta todo; si se juntan desarrollo y examen, ALTA da 62/65 (95 %) y MEDIA 147/151 (97 %).
- **No se alcanza el listón del mercado** en la serie exacta ni en ALTA. Parte de la diferencia es del banco: clips de Kaggle y UCF
  cortados a mitad de serie, de 320×240 y con la cámara en movimiento. Pero no lo explica todo.
- El examen queda gastado. Lo siguiente se valida con datos nuevos: las grabaciones del usuario y MM-Fit.

## Partida M5 · 22-oct, motor 9 (preliminar)

Se repitió la partida de desarrollo con el motor9, sin abrir de nuevo el examen. No son resultados del motor10 ni una
validación cruzada de parámetros. Los bancos y las etiquetas originales permanecen inmutables; los informes quedan fuera del repo.

| Banco / partición | Procesados | G1 con salida | G1 FULL exactas entre salidas | G1 ALTA exactas | G1 MEDIA exactas |
|---|---:|---:|---:|---:|---:|
| Histórico, ajuste folds0–3 | 720/720 | 269/345 | 230/269 | 45/45 | 104/105 |
| Histórico, control desarrollo fold4 | 175/175 | 63/86 | 50/63 | 9/10 | 21/24 |
| MM-Fit OpenPose, ajuste folds0–3 | 509/509 | 403/405 | 331/403 | 309/358 | 6/11 |
| MM-Fit OpenPose, control fold4 | 107/107 | 89/89 | 65/89 | 64/81 | 1/4 |

Los controles son exclusivos del orquestador; el ajustador solo recibe agregados. La exactitud FULL se compara con el valor
puntual de la etiqueta. El indicador histórico de G2 «equivocadas entre ofrecidas» usa el rango permitido y no es la misma medida:
1/21 en ajuste histórico y1/4 en su control. MM-Fit no tiene G2 en estos paquetes; sus G3 de perfil forzado son diagnóstico,
con rechazo real `NOT_VALIDATED`, y quedan fuera de la selección y de las metas de precisión de G1.

MM-Fit OpenPose de ajuste, por ejercicio (17 sesiones/grupos; los folds siguen sesión mod5):

| Ejercicio | G1 | Con salida | FULL exactas / todas G1 |
|---|---:|---:|---:|
| Curl | 48 | 48 | 47/48 |
| Elevación | 49 | 49 | 49/49 |
| Tríceps | 52 | 52 | 0/52 |
| Flexiones | 53 | 53 | 50/53 |
| Press de hombro | 48 | 48 | 43/48 |
| Remo | 52 | 52 | 51/52 |
| Sentadilla | 52 | 52 | 50/52 |
| Zancada | 51 | 49 | 41/51 |

Informes de ajuste: `C:/kw/vt-tune-out/m5-baseline-20261022` y
`C:/kw/vt-mmfit/m5-out/openpose-baseline-tune-20261022`. Controles de partida:
`C:/kw/vt-m5-control/old-bank-baseline-20261022` y
`C:/kw/vt-mmfit/m5-out/openpose-baseline-control-20261022`.
El protocolo M5 selecciona con tres folds y mide en el retenido, con resultados separados por banco y denominadores explícitos;
un desglose de una única corrida por folds no acredita esa selección.

**MediaPipe pendiente por instrucción del usuario.** Se conservaron72/616 extracciones completas antes de aplazar el Z Flip.
En esos72 timings, análisis mediano114,6s para vídeo mediano21,57s, ratio mediano5,32. Son tiempos del lote parcial,
no una medición de fiabilidad ni prueba de cumplir el objetivo de tiempo real. La comparación OpenPose/MediaPipe de las494G1
y el QA físico se completan cuando el usuario regrese. No integrar motor10 ni cerrar la entrega con estos resultados preliminares.

### Diagnóstico M5 sobre ajuste, sin cambiar etiquetas

- **Bordes de tríceps:** la vuelta desde una posición extrema al inicio no demuestra que su ida ocurriera dentro del vídeo.
  El candidato ensaya advertir esa censura; no añade una FULL ni inventa fases para igualar la etiqueta. Debe medir el efecto
  sobre precisión y cobertura con CV; sus pruebas sintéticas no acreditan por sí solas una mejora en los bancos.
- **Zancada OpenPose:** las ocho salidas ALTA equivocadas de ajuste pierden veinte giros detectados, descartados por
  `nonrep_horizontal`, con amplitud comparable a los FULL conservados. El conteo usa cadera, pero el veto lateral usa el hombro
  que sustituye al disco. Se prepara un contrafactual por procedencia física de esa señal, conservando caminar, preparación
  y trayectoria de disco confirmado. No es un ajuste por clip ni una corrección de etiquetas.
- **Cobertura histórica:** de345 G1,76 sin salida:29 por ausencia de señal utilizable,19 sin ciclos válidos y28 por captura.
  BENCH concentra38. Los casos sin señal incluyen19 sin escala,9 sin señal válida (ambos BENCH) y1 sin pose.
  La captura rechaza26 recortes y2 desplazamientos comunes de pies. Quitar indiscriminadamente el veto no sostiene la meta:
  entre diez recortes estáticos con confianza cruda MEDIA, solo ocho FULL eran exactas. Un posible fallback angular de BENCH
  necesita estudiar evidencia fuerte y ciclos completos; no se baja la exigencia de escala o visibilidad para aumentar cobertura.

Estos diagnósticos son de desarrollo y no sustituyen la selección retenida, los controles nuevos ni la medición MediaPipe.
