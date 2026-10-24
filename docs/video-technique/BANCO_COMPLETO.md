# Banco completo: los 1.000 vídeos en todas las pruebas (VT11, D41)

**Estado vigente · 24-oct-2026:** las cifras finales OpenPose/MediaPipe/M5 y sus límites se conservan abajo sin repetir bancos, CV, controles ni examen. QA funcional original cerrado mediante evidencia identificada; no cambia las metas98/90 incumplidas ni el timing616 del motor9. El nuevo SQUAT10ALTA/CURL≈5BAJA son comprobaciones de UI con clips públicosTUNE, no muestras nuevas de aceptación física ni resultados ciegos. Ver QA_FASE5_RESULTADOS_20261023.md. Entrega/FFmaster diferidos por orden humana; no ejecutar anexos sin pedido.

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

Se repitió la partida de desarrollo con el motor 9, sin abrir de nuevo el examen. No son resultados del motor 10 ni una
validación cruzada de parámetros. Los bancos y las etiquetas originales permanecen inmutables; los informes quedan fuera del repo.

| Banco / partición | Procesados | G1 con salida | G1 FULL exactas entre salidas | G1 ALTA exactas | G1 MEDIA exactas |
|---|---:|---:|---:|---:|---:|
| Histórico, ajuste folds 0–3 | 720/720 | 269/345 | 230/269 | 45/45 | 104/105 |
| Histórico, control desarrollo fold 4 | 175/175 | 63/86 | 50/63 | 9/10 | 21/24 |
| MM-Fit OpenPose, ajuste folds 0–3 | 509/509 | 403/405 | 331/403 | 309/358 | 6/11 |
| MM-Fit OpenPose, control fold 4 | 107/107 | 89/89 | 65/89 | 64/81 | 1/4 |

Los controles son exclusivos del orquestador; el ajustador solo recibe agregados. La exactitud FULL se compara con el valor
puntual de la etiqueta. El indicador histórico de G2 «equivocadas entre ofrecidas» usa el rango permitido y no es la misma medida:
1/21 en ajuste histórico y 1/4 en su control. MM-Fit no tiene G2 en estos paquetes; sus G3 de perfil forzado son diagnóstico,
con rechazo real `NOT_VALIDATED`, y quedan fuera de la selección y de las metas de precisión de G1.

MM-Fit OpenPose de ajuste, por ejercicio (17 sesiones/grupos; los folds siguen sesión mod 5):

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

**MediaPipe en recuperación tras el regreso del usuario.** Se preservan 72/616 extracciones completas de la versión 50;
su veto GPU persistente impidió reanudar una medición válida. La versión 51 con B12 compiló y se instaló en `.dbg` sin borrar
datos ni preferencias. Se inició una generación homogénea nueva de los 616 clips en `C:/kw/vt-mmfit/phone-v51`, sin reutilizar
las 72 trazas anteriores. Su preflight aprobó GPU/Full/stride 1 y traza de 30,0003 Hz sobre fuente de 30 fps;
el primer lote publicó 12/616 recibos GPU completos, con térmico máximo 0 al inicio y al final. Su mediana de análisis fue
35,28 s, vídeo 20,33 s y ratio individual mediano 1,75: aún no cumple el objetivo ≤1×. Esta frecuencia acredita muestreo,
no velocidad de procesamiento. APK y procedencia
registrados en `APK_ATTESTATION.json`.
En las 72 mediciones antiguas, el análisis mediano fue de 114,6 s para vídeo mediano de 21,57 s, con ratio mediano de 5,32.
Son tiempos del lote parcial anterior, no fiabilidad ni prueba de tiempo real. La comparación OpenPose/MediaPipe de las 494 G1
y el QA físico siguen pendientes. No integrar motor 10 ni cerrar la entrega con estos resultados preliminares.

### Diagnóstico M5 sobre ajuste, sin cambiar etiquetas

- **Bordes de tríceps:** la vuelta desde una posición extrema al inicio no demuestra que su ida ocurriera dentro del vídeo.
  El candidato ensaya advertir esa censura; no añade una FULL ni inventa fases para igualar la etiqueta. Debe medir el efecto
  sobre precisión y cobertura con CV; sus pruebas sintéticas no acreditan por sí solas una mejora en los bancos.
- **Zancada OpenPose:** las ocho salidas ALTA equivocadas de ajuste pierden veinte giros detectados, descartados por
  `nonrep_horizontal`, con amplitud comparable a los FULL conservados. El conteo usa cadera, pero el veto lateral usa el hombro
  que sustituye al disco. Se prepara un contrafactual por procedencia física de esa señal, conservando caminar, preparación
  y trayectoria de disco confirmado. No es un ajuste por clip ni una corrección de etiquetas.
- **Cobertura histórica:** de 345 G1, 76 sin salida: 29 por ausencia de señal utilizable, 19 sin ciclos válidos y 28 por captura.
  BENCH concentra 38. Los casos sin señal incluyen 19 sin escala, 9 sin señal válida (ambos BENCH) y 1 sin pose.
  La captura rechaza 26 recortes y 2 desplazamientos comunes de pies. Quitar indiscriminadamente el veto no sostiene la meta:
  entre diez recortes estáticos con confianza cruda MEDIA, solo ocho FULL eran exactas. Un posible fallback angular de BENCH
  necesita estudiar evidencia fuerte y ciclos completos; no se baja la exigencia de escala o visibilidad para aumentar cobertura.

Estos diagnósticos son de desarrollo y no sustituyen la selección retenida, los controles nuevos ni la medición MediaPipe.

### M5 · CV preliminar de la guarda de borde

Ejecutada en `e1268d050` (motor 10, candidato aislado), con rejilla 0/0,5/0,75/1 periodos, entrenamiento de tres folds y
medición del retenido. Las cuatro rotaciones eligieron **1 periodo**. Se procesaron las 1.229 filas de ajuste (509 MM-Fit y720
históricas) para cada variante; sus predicciones retenidas son únicas. La variante0 reproduce los agregados del motor9.

| Banco G1 / medida | Guarda0 (motor9 equivalente) | Guarda1 (retenido en las cuatro rotaciones) |
|---|---:|---:|
| MM-Fit: ALTA FULL exactas | 309/358 (86,3 %) | 308/327 (94,2 %) |
| MM-Fit: MEDIA FULL exactas | 6/11 (54,5 %) | 6/8 (75 %) |
| MM-Fit: ofrecidas ALTA/MEDIA | 369/405 | 335/405 |
| Histórico: ALTA FULL exactas | 45/45 | 44/44 |
| Histórico: MEDIA FULL exactas | 104/105 | 103/104 |
| Histórico: ofrecidas ALTA/MEDIA | 150/345 | 148/345 |

La guarda cambia confianza, no añade repeticiones: FULL exactas/salidas permanecen331/403 en MM-Fit y230/269 en histórico.
G2 histórico conserva1/21 equivocadas entre ofrecidas. La mejora de precisión procede de abstenerse de ofrecer resultados
inciertos; también deja de ofrecer una serie exacta MM-Fit y dos históricas. No es una mejora de exactitud del conteo.
Las metas98/90 siguen **incumplidas en MM-Fit**. Por ejercicio, siguen por debajo del95 % de series G1 exactas OHP43/48 y
flexiones50/53; remo51/52 y sentadilla50/52 superan solo la meta puntual, sin demostrar generalización ni el contrato de eventos/IC.
Tríceps conserva0/52 FULL exactas y cuatro ALTA equivocadas; no queda resuelto por bajar la confianza de otros casos.

Informe externo `C:/kw/vt-mmfit/m5-out/openpose-cv-boundary-20261022/M5_CV.json`, SHA-256
`3fd7f9fc101a6ba187bac49415bc0091638c2c4e7a7731487b000019cf158a16`; `RUN_CONTEXT.json` fija commits/hashes y
`PROFILE_AGGREGATES.json` desglosa todas las G1 por ejercicio incluyendo abstenciones. Log `C:/kw/logs/vt-m5-cv-boundary-20261022-2.log`.
No se evaluaron controles del candidato. La selección es preliminar OpenPose y el default del código permanece0 hasta la
selección con MediaPipe completo. El siguiente experimento es el veto horizontal por procedencia física; no se fusiona motor10.

### M5 · Zancada POSE con ciclos completos observados

El veto horizontal genérico de `1e2717f31` se rechazó: aunque pasó 602 pruebas JVM, redujo las FULL exactas históricas
de 230/269 a 229/269. El candidato acotado `7032c5895` restaura ese veto salvo en zancadas POSE con ida y retorno
observados, valles con reposo interior, al menos tres excursiones comparables y una vecina a la cadencia de la serie.
Reutiliza umbrales existentes y no depende de clips, etiquetas ni fuentes de bancos. Sus dos clases de regresión suman
36 pruebas correctas, incluido el negativo de preparación inicial seguido de una pausa larga.

La CV real de cuatro folds volvió a producir 1.229 predicciones retenidas y eligió guarda 1 en las cuatro rotaciones:

| Banco G1 / medida | Candidato acotado, guarda 0 | Candidato acotado, guarda 1 retenida |
|---|---:|---:|
| MM-Fit: FULL exactas / salidas | 340/404 | 340/404 |
| MM-Fit: ALTA FULL exactas | 318/359 | 317/328 (96,6 %) |
| MM-Fit: MEDIA FULL exactas | 6/11 | 6/8 (75 %) |
| MM-Fit: ofrecidas ALTA/MEDIA | 370/405 | 336/405 |
| Histórico: FULL exactas / salidas | 230/269 | 230/269 |
| Histórico: ALTA FULL exactas | 45/45 | 44/44 |
| Histórico: MEDIA FULL exactas | 104/105 | 103/104 |
| Histórico: ofrecidas ALTA/MEDIA | 150/345 | 148/345 |

Con guarda 0, el histórico reproduce los agregados del motor 9; MM-Fit gana nueve FULL exactas y una salida frente
a 331/403. La guarda 1 conserva el coste de abstención documentado arriba. G2 histórico permanece en 1/21 ofrecidas
equivocadas. Las metas MM-Fit 98/90 siguen incumplidas; el agregado entre bancos no oculta ese resultado.
No se han abierto controles del candidato ni se ha integrado motor 10. Se conserva default 0 hasta repetir selección
con MediaPipe completo. Informe `C:/kw/vt-mmfit/m5-out/openpose-cv-lunge-observed-20261022/M5_CV.json`, SHA-256
`5579cba256c1235411382f3cf2858168c30eaa02882fd44aeb25f257bea31b08`, con `RUN_CONTEXT.json` de procedencia;
log `C:/kw/logs/vt-m5-cv-lunge-observed-20261022.log`, corrida correcta en 3m17s.



### MediaPipe v51 · medición completa del 23-oct-2026

La extracción nueva cerró el 23-oct a las 09:31:55 UTC: 616/616 en 52 lotes, más un preflight, sin fallos ni pausas.
Los 616 recibos/timings registran GPU, modelo Full, stride 1 y temperatura máxima 1. El banco separado es
`C:/kw/vt-mmfit/bank-mp-v51`, con SHA-256 de manifiesto
`8b2b509f24425ba5d702e71d7657875acb6a8cb2ea868cf00df46a281d178242`.
Conserva los 1.848 archivos originales y las etiquetas byte por byte; MediaPipe usa sidecars nuevos. La medición ejecutó el
motor 9 en `C:/kw/vt-mmfit/report-mp-v51-motor9-20261023`: 616/616 procesados, 494 G1 y 122 G3, con `EXAM=0`; no se
abrió ni consumió un examen nuevo. Log `C:/kw/logs/vt-mmfit-v51-motor9-20261023.log` y XML/contexto preservados en
`C:/kw/logs/vt-mmfit-v51-motor9-20261023-results`. Duración 1m54s; dos pruebas tuvieron sus fallos históricos esperados,
sin errores adicionales.

Comparación de G1 con la salida OpenPose ya existente (proxy), sin volver a calcularla. Cada par de FULL indica
exactas/salidas; cada par ALTA indica exactas/ofrecidas:

| Ejercicio | G1 | OpenPose FULL | MediaPipe FULL | OpenPose ALTA | MediaPipe ALTA |
|---|---:|---:|---:|---:|---:|
| CURL | 59 | 58/59 | 38/59 | 58/59 | 33/46 |
| LUNGE | 62 | 44/60 | 33/60 | 44/60 | 27/50 |
| OHP | 60 | 55/60 | 57/60 | 53/55 | 44/46 |
| PUSH_UP | 65 | 59/65 | 44/65 | 58/64 | 36/51 |
| RAISE | 56 | 55/56 | 43/56 | 50/50 | 43/53 |
| ROW | 64 | 63/64 | 48/63 | 48/48 | 9/12 |
| SQUAT | 64 | 62/64 | 56/62 | 62/63 | 46/47 |
| TRICEPS_EXTENDED_REST | 64 | 0/64 | 2/62 | 0/40 | 0/0, no estimable |

| G1 agregado | OpenPose | MediaPipe |
|---|---:|---:|
| Con salida | 492/494 (99,60 %) | 487/494 (98,58 %) |
| FULL exactas entre salidas | 396/492 (80,49 %) | 321/487 (65,91 %) |
| FULL exactas contando abstenciones como fallo | 396/494 (80,16 %) | 321/494 (64,98 %) |
| ALTA FULL exactas/ofrecidas | 373/439 (84,97 %) | 238/305 (78,03 %) |
| MEDIA FULL exactas/ofrecidas | 7/15 (46,67 %) | 52/69 (75,36 %) |

MediaPipe obtuvo 31/113 FULL exactas entre las 113 salidas BAJA y siete G1 sin salida. El rango aceptado de estas etiquetas coincide con la
exactitud FULL en esta medición; no equivale a una tolerancia de ±1 repetición. La comparación no demuestra mejora:
MediaPipe queda bajo las metas 98/90 y el OpenPose comparativo es un proxy, no una referencia física externa.

En las 616 trazas, la duración mediana del vídeo fue 23,7165 s (p95 34,867 s) y la del análisis 36,207885 s (p95
78,42257 s). El ratio análisis/vídeo tuvo mediana 1,5265409683, p95 3,2388709163, mínimo 1,2909352683 y máximo
7,0069082192. El análisis acumuló 24.867,84873 s frente a 14.915,03 s de vídeo. El total fue 50,885 ms por frame
en mediana (p95 107,96 ms); pose, 25,73 ms (p95 52,81 ms). El objetivo de ratio ≤1 todavía no se cumple. GPU y stride 1
no implican procesamiento en tiempo real; una densidad de trazas de 30 fps no mide throughput real a 30 fps.

No hay timing OpenPose comparable ni ground truth externo de velocidad, ROM o velocidad de barra. Mantener `≈` y 1RM
en beta. La CV M5 MediaPipe terminó (run 5977) en el worktree `ce16580d3`, usando solo tune folds 0–3 y `vt-tune`;
la CV por sí sola no aprobó el motor 10. El QA físico posterior y los controles congelados se documentan abajo. El
anexo competitivo permanece posentrega y no bloquea la entrega actual.



### CV M5 MediaPipe v51 · corrida completada

La CV real usó el arnés de ce16580d3: 10 pruebas PASS, 1.229 predicciones retenidas y selección de guarda 1,0 en las
cuatro rotaciones. El informe agregado es C:/kw/vt-mmfit/m5-out/mediapipe-v51-cv-7032-20261023/M5_CV.json,
SHA-256 a1d60b209c05fe5364589f670f4b92e53ddd36c443e68f42449052457200d0f0. Se usaron los folds 0–3 de tune MediaPipe y
vt-tune; no se abrieron controles.

| G1 MediaPipe tune | Guarda 0 | Guarda 1,0 retenida |
|---|---:|---:|
| Filas con salida | 401/405 | 401/405 |
| FULL exactas/salidas | 270/401 | 270/401 |
| ALTA exactas/ofrecidas | 203/249 | 202/235 (85,96 %) |
| MEDIA exactas/ofrecidas | 42/57 | 42/55 (76,36 %) |
| Ofrecidas ALTA/MEDIA | 306/405 | 290/405 |

| G1 histórico vt-tune | Guarda 0 | Guarda 1,0 retenida |
|---|---:|---:|
| FULL exactas/salidas | 230/269 | 230/269 |
| ALTA exactas/ofrecidas | 45/45 | 44/44 |
| MEDIA exactas/ofrecidas | 104/105 | 103/104 |
| Ofrecidas ALTA/MEDIA | 150/345 | 148/345 |

La guarda 1,0 conserva las FULL exactas/salidas en ambos bancos y reduce las ofertas ALTA/MEDIA; no mejora el conteo exacto.
Las metas 98/90 siguen incumplidas en MediaPipe. La guarda 1 quedó congelada en el candidato aislado `2be0179b7`;
sus siete clases de regresión terminaron con 93 pruebas correctas, sin fallos ni omisiones. La selección CV por sí sola
no constituye aprobación de motor 10; los controles posteriores se registran abajo.

### M5 · controles posteriores a la congelación y decisión

El orquestador midió una sola vez el candidato `2be0179b7` en cada control de desarrollo, con EXAM=0 y sin comunicar
identificadores o resultados individuales al ajustador. La partida MediaPipe motor 9 se obtuvo del fold4 del informe ya
calculado; la partida histórica de desarrollo también se reutilizó. No se repitió el examen histórico.

| Control / G1 | Motor 9 | Candidato motor 10 |
|---|---:|---:|
| MediaPipe: salida | 88/89 | 88/89 |
| MediaPipe: FULL exactas/salidas | 60/88 | 67/88 |
| MediaPipe: ALTA exactas/ofrecidas | 44/58 (75,86 %) | 50/58 (86,21 %) |
| MediaPipe: MEDIA exactas/ofrecidas | 10/12 (83,33 %) | 11/12 (91,67 %) |
| MediaPipe: ofrecidas ALTA/MEDIA | 70/89 | 70/89 |
| Histórico: salida | 63/86 | 63/86 |
| Histórico: FULL exactas/salidas | 50/63 | 50/63 |
| Histórico: ALTA exactas/ofrecidas | 9/10 | 9/10 |
| Histórico: MEDIA exactas/ofrecidas | 21/24 | 21/24 |
| Histórico: ofrecidas ALTA/MEDIA | 34/86 | 34/86 |

En el control MediaPipe, zancada pasó de 3/11 a 10/11 FULL exactas: siete casos mejoraron y ninguno empeoró.
Los demás ejercicios conservaron sus FULL exactas y su cobertura. En el histórico, ninguna de las 86 G1 cambió su conteo
ni perdió salida; G2 pasó de 1/4 ofrecidas erróneas a 0/3. Es una mejora limitada, suficiente para integrar el candidato
según la regla de no regresión de Fase3; **no demuestra las metas 98/90 ni paridad competitiva**. No se retocaron parámetros
mirando los controles. La revisión independiente del código físico no encontró bloqueantes.

Los dos tests de control terminaron correctos. Logs `C:/kw/logs/vt-m5-frozen-control-mediapipe-20261023.log` y
`vt-m5-frozen-control-legacy-20261023.log`; XML/contextos en `C:/kw/logs/vt-m5-frozen-controls-20261023-results`.
SHA XML MediaPipe `ec6c2fa5bb45f5aa7a8cc3d40be1d9f812ddd9a336a884600bf8e11b8c711058`, histórico
`eebcede6b21f1d11a0bb2719e91b6e6184affa4846f2603b3cd004da735e5794`. Fuente congelada, hashes de manifiestos y
particiones constan en los contextos. El control histórico contiene175 filas de desarrollo seleccionadas por EXAM0;
sus901 referencias selladas carecen de archivos en este paquete y no se abrieron.

El descriptivo total del candidato combina las405 G1 retenidas de CV y las89 G1 del control; solo estas últimas son
control ciego. No es un nuevo ensayo independiente de494 series:

| Ejercicio | G1 | FULL exactas/salidas | ALTA exactas/ofrecidas | MEDIA exactas/ofrecidas |
|---|---:|---:|---:|---:|
| CURL | 59 | 38/59 | 33/46 | 5/12 |
| LUNGE | 62 | 49/62 | 42/47 | 4/4 |
| OHP | 60 | 57/60 | 43/45 | 3/4 |
| PUSH_UP | 65 | 44/65 | 36/48 | 6/6 |
| RAISE | 56 | 43/56 | 43/49 | 0/1 |
| ROW | 64 | 48/63 | 9/11 | 25/29 |
| SQUAT | 64 | 56/62 | 46/47 | 10/11 |
| TRICEPS_EXTENDED_REST | 64 | 2/62 | 0/0, no estimable | 0/0, no estimable |
| Total | 494 | 337/489 (68,92 %) | 252/293 (86,01 %) | 53/67 (79,10 %) |

El total ofrece ALTA/MEDIA en360/494 series y deja cinco sin salida. Tríceps sigue sin resolverse; flexiones, curl y otros
perfiles siguen lejos del listón. Se requieren mejores señales/segmentación y validación independiente antes de afirmar
fiabilidad de mercado. Los límites físicos, de parciales y de velocidad no se resuelven con esta tabla.
Agregado derivado `C:/kw/vt-m5-control/aggregate-acceptance-2be0179b7-20261023-v2.json`, SHA
`3d5bf7fb4a0d00bea967016b80380629815cd168b7100859e8c8d7101a6662cc`. La versión2 corrige el provisional no utilizado:
`predictedFull=0` también puede representar SIN_SALIDA; la cobertura exige una salida ALTA/MEDIA/BAJA.

La comprobación de dominio congelado posterior cubrió82 clases y603 pruebas:589 correctas,14 omisiones opcionales,
sin fallos ni errores, SUCCESS en2m17s. Se excluyeron expresamente los dos métodos del examen histórico.
Evidencia `C:/kw/logs/vt-m5-frozen-domain-integration-20261023-results`; no sustituye la comprobación Android/R8
ni el QA del APK que se vaya a entregar. El anexo competitivo permanece posentrega.

### QA del dispositivo · actualización 23-oct-2026

La medición de 616 clips y sus tiempos agregados corresponde al motor 9 con extracción MediaPipe GPU/Full. No se ejecutó
el banco de 616 con motor 10. Las mediciones posteriores de motor 10 son muestras puntuales del flujo del teléfono y no
reemplazan ni se agregan a ese baseline. Una pasada FULL en un clip oscuro fue una muestra de integración; dos aperturas
de análisis anteriores usaron `RECOMPUTE` desde sidecars (93 ms y 34 ms, cero cuadros decodificados o evaluados por pose)
y reabrir el primero no creó otro análisis.

En grabaciones reales con la preferencia condicional de 60 fps activada y desactivada se midieron ~30,0101 fps por PTS.
Con la preferencia activada, Camera3 ya mostraba `[30 30]` antes de REC y los rangos AE normales anunciados llegaban a 30; esto respalda
un fallback previo al sensor en esta configuración, no 60 fps.
El límite de un minuto cerró una grabación sin pulsar detener. En el hub se probaron las calibraciones 45/40/35 cm y
`Otro=42 cm`, con persistencia y restauración a 45 cm. No se verificó `PLATE_ONLY` con un disco identificable.

La aceptación física sigue parcial. El importador del hub abre Photo Picker y su selección continúa pendiente; DocumentsUI
ya probó una ruta distinta. El tercer TUNE CURL ya mostró +1 parcial estable, barra 2 tramada y ROM 89°, con confianza propia ALTA
inferida del formato frente a MEDIA global. No apareció zona ni porcentaje de recorrido; se revisa si falta el dato o su
presentación, sin asumir un fallo ni aprobarlo. El 84 % anterior era velocidad relativa, no parcial. Sin
referencia física externa, se mantiene `≈` y el 1RM en beta. No se extrapola a 616 la rapidez de una pasada de teléfono ni
se declara aprobado el flujo completo.
