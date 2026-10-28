# Resultado de la ola Nutrición v2 (28-oct-2026)

Entrega de la ola pedida el 27-oct: entender mejor lo que se escribe al registrar una comida, un catálogo en
español, porciones, cocción y presentación bien interpretadas, micronutrientes y, sobre todo, **saber con
certeza si el sistema nuevo es mejor**. Plan aprobado: `C:\Users\valen\.claude\plans\soft-riding-boot.md`.
Decisiones tomadas sin consultarte durante la noche: [DECISIONES_NOCHE.md](DECISIONES_NOCHE.md).

## En corto

- **Sí, es mejor, y por mucho.** En el examen sellado (TEST, 809 descripciones y 1.299 menciones que ningún
  agente vio), el intérprete v2 acierta la mención completa (alimento + gramos + estado y forma) el **49,1 %**
  de las veces, contra el **20,8 %** de master: **+28,3 pp [IC 95 %: +25,4; +31,6]**.
- **Se equivoca en silencio mucho menos.** De lo que acepta sin preguntar, master tiene mal el 53,1 % y v2 el
  24,6 % (−28,6 pp [−32,8; −24,2]). Por comida, master deja pasar 0,73 errores silenciosos y v2 0,21.
- **Micronutrientes:** el 96 % de las menciones de v2 trae el núcleo completo (fibra, azúcar, sodio, potasio y
  31 nutrientes de mediana); master, 0 %. La hoja «Nutrientes del día» los muestra agrupados con metas DRI y
  topes de la OMS.
- **El precio:** v2 pide confirmar más. Bloquea el guardado en 1,67 menciones por comida hasta que tocas
  «Lo estimado» o eliges otra porción; master solo en 0,19. Es la regla de confianza (N23); se puede aflojar.
- **En la app queda detrás de un interruptor, apagado** (Ajustes › Nutrición › «Intérprete de comidas v2
  (beta)»). El predeterminado no cambia sin tu visto bueno (D4, N19).

## Qué cambia en la app

| Dónde | Qué |
|---|---|
| Ajustes › Nutrición | Interruptor «Intérprete de comidas v2 (beta)» (apagado). Con él encendido, el registrador usa v2 y vuelve al intérprete actual si v2 no reconoce nada (4 de 1.350 descripciones del examen). |
| Ajustes › Nutrición | «Fuentes y licencias de los datos»: USDA FoodData Central (CC0), ANSES-Ciqual 2025 (Licence Ouverte 2.0) y Open Food Facts (ODbL/DbCL), con su texto completo. |
| Registrar comida | Con v2: tarjetas con la ficha elegida y sus gramos; si algo no es seguro, la tarjeta pide confirmar con un toque («Lo estimado (N g)», otra porción o elegir la ficha). |
| Hoja «Nutrientes del día» | 33 nutrientes en 5 grupos (carbohidratos, grasas, vitaminas, minerales, otros), metas DRI, topes de guía (grasa saturada < 10 % y trans < 1 % de la energía, azúcares añadidos < 10 %, cafeína ≤ 400 mg), nota de la niacina y pie de fuentes. «Sin dato» nunca se cuenta como 0. |
| Guardado | Cada registro guarda una instantánea de sus nutrientes desde el paquete del catálogo (F4-B); el historial no se recalcula (D7). |

## Examen sellado: TEST (una sola corrida, 28-oct 05:46)

A = master `c4491bc83`; B = v2 `f6de40a13` (exportación del vocabulario `b43cc853…`). Bootstrap pareado por
descripción, 2.000 remuestreos. Regla de aceptación: IC inferior de ΔM3 > 0, IC superior de ΔM4 ≤ +0,5 pp y ningún
fenómeno con ≥ 30 menciones cae más de 3 pp. **Veredicto: ACEPTA** (las tres condiciones).

| Métrica | master | v2 | Δ (v2 − master) | IC 95 % |
|---|---|---|---|---|
| **M3 Mención correcta** (principal) | 20,8 % | **49,1 %** | +28,3 pp | [+25,4; +31,6] |
| M1 Identidad | 25,3 % | 48,4 % | +23,1 pp | [+20,1; +26,3] |
| M2 Gramos (dada la identidad) | 70,1 % | 78,9 % | +8,7 pp | [+3,2; +14,0] |
| **M4 Error silencioso** | 53,1 % | **24,6 %** | −28,6 pp | [−32,8; −24,2] |
| M5 Cobertura (ficha real) | 48,6 % | 97,5 % | +48,9 pp | [+45,7; +52,3] |
| M6 Preguntas: precisión | 16,5 % | 27,8 % | +11,3 pp | [+7,6; +15,2] |
| M6 Preguntas: recobrado | 51,2 % | 55,9 % | +4,7 pp | [−4,8; +14,2] |
| M7 Comida dentro de ±20 % kcal | 34,4 % | 49,1 % | +14,7 pp | [+10,4; +18,8] |
| M8 Micronutrientes (núcleo completo) | 0 % | 96,3 % | +96,3 pp | [+95,3; +97,1] |
| Menciones de más por comida | 1,29 | 0,80 | −0,49 | [−0,59; −0,38] |
| Latencia p50 / p95 (PC) | 128 / 975 ms | 0,12 / 3,4 ms | | |

La app con el interruptor encendido (v2 y, si no reconoce nada, el actual) da lo mismo: M3 49,1 % y M4 24,7 %.

### Por fenómeno (TEST, M3)

| Fenómeno | Menciones | master | v2 | Δ [IC 95 %] |
|---|---|---|---|---|
| F01 Alimento suelto | 66 | 27,5 % | 50,7 % | +23,2 pp [+7,5; +38,7] |
| F02 Masa o volumen explícito | 143 | 22,1 % | 46,4 % | +24,3 pp [+15,7; +33,1] |
| F03 Conteo y tamaño | 416 | 25,9 % | 57,9 % | +32,0 pp [+26,8; +37,4] |
| F04 Medida casera | 289 | 22,5 % | 52,4 % | +29,9 pp [+23,8; +36,2] |
| F05 Envase y fracción | 110 | 17,5 % | 52,1 % | +34,6 pp [+24,9; +44,3] |
| F06 Cocción / estado | 288 | 19,2 % | 42,0 % | +22,7 pp [+17,0; +28,4] |
| F07 Presentación / forma | 129 | 19,1 % | 53,4 % | +34,4 pp [+24,8; +43,7] |
| F08 Plato regional | 374 | 14,3 % | 46,9 % | +32,7 pp [+27,1; +38,2] |
| F09 Composición «X con Y» | 766 | 21,3 % | 44,2 % | +22,9 pp [+19,3; +26,5] |
| F10 Exclusión frente a atributo | 67 | 19,5 % | 43,2 % | +23,7 pp [+12,3; +35,6] |
| F11 Marca o producto | 81 | 14,8 % | 48,3 % | +33,5 pp [+22,0; +46,3] |
| F12 Corrección o referencia | 44 | 18,4 % | 37,5 % | +19,1 pp [+5,2; +33,3] |
| F13 Faltas de tipeo o dictado | 143 | 20,3 % | 40,5 % | +20,3 pp [+11,4; +29,4] |
| F14 Regionalismo | 213 | 20,7 % | 44,5 % | +23,8 pp [+16,2; +31,5] |
| F15 Bebida o alcohol | 306 | 21,9 % | 59,8 % | +38,0 pp [+32,0; +43,8] |
| F16 Suplemento | 33 | 27,3 % | 62,8 % | +35,5 pp [+19,6; +51,2] |
| F17 Texto extenso o con ruido | 123 | 15,4 % | 55,3 % | +39,9 pp [+31,5; +48,9] |
| F18 Inglés o mezcla de idiomas | 132 | 11,2 % | 45,1 % | +33,9 pp [+25,4; +43,1] |

Por idioma: español 22,0 → 49,6 % (+27,6 pp [+24,5; +31,0]); **inglés 5,6 → 45,8 %** (+40,2 pp [+31,1; +50,4]);
mezcla 28,6 → 42,9 % (31 menciones, IC incluye 0). Por estilo (módulo B): sin puntuación 61,7 %, formal 52,5 %,
texto con ruido 54,6 %, inglés coloquial 46,7 %, faltas en los nombres 38,8 % (el más flojo).

### Cómo se llegó (DEV, 539 descripciones visibles)

| Paso | M3 | M4 | Commit |
|---|---|---|---|
| master | 21,0 % | 51,0 % | `c4491bc83` |
| Parser v2 inicial (F2) | 40,2 % | 48,9 % | `fc6d7c94b` |
| Ronda 1: gramática, resolutor, léxico y vocabulario | 53,2 % | 20,7 % | `fa14c0558`…`c5edc22d9` |
| Ronda 2: datos (porciones, piezas enteras, envases) | **55,4 %** | **19,5 %** | `f6de40a13` |

En DEV v2 queda en +34,4 pp [+30,6; +38,2] sobre master; en TEST, +28,3 pp. La distancia (55 % contra 49 %) es
el ajuste esperable a lo que se miró, y por eso TEST estaba sellado y se corrió una sola vez.

## Toques frente a errores silenciosos (la regla N23)

Contando solo lo que **bloquea el guardado** hasta que lo confirmas (TEST, por comida):

| | Confirmaciones | Errores silenciosos | M4 |
|---|---|---|---|
| master | 0,19 | 0,73 | 53,1 % |
| v2 (regla de confianza ≥ 0,8, la que quedó) | 1,67 | 0,21 | 24,6 % |

En DEV, sin la regla, v2 pediría 0,62 confirmaciones por comida pero dejaría pasar 0,77 errores silenciosos, más
que master. Con la regla solo en lo que pesa ≥ 50 kcal: 1,10 y 0,50. Elegí la regla completa porque pediste
registrar bien. Si prefieres menos toques, es una constante (`ACCEPT_MIN_CONFIDENCE` en `ResolverV2`). La mejor
salida sería un botón «Usar lo estimado en todo» (pendiente). El «1,6 preguntas por comida» de master en M6 incluye
avisos de cocción que no bloquean, así que no se compara con esta tabla.

## Qué hay dentro

- **Examen** (F0): 1.350 descripciones en estilos chileno, latinoamericano e inglés, con faltas, sin puntuación,
  formales y largas; oro doble con adjudicación; puntuador con bootstrap pareado (`scripts/nutrition_v2/exam/`).
  Línea base y veredicto master vs. Codex en [BASELINE.md](BASELINE.md): Codex empataba con master.
- **Paquete de catálogo** (F1a): SQLite de solo lectura con 38 nutrientes por ficha (USDA FNDDS/SR/Foundation,
  CIQUAL 2025 y Open Food Facts Chile). Se publica al almacenamiento interno con verificación de sha256, sin migración
  de Room.
- **Vocabulario en español** (F1b + rondas): 1.652 conceptos; 22.425 alias (13.043 en español, 4.592 en inglés y
  4.790 faltas típicas); 2.068 fichas por estado y forma (297 platos derivados de sus componentes); porciones caseras,
  pesos por unidad, piezas enteras y envases.
- **Parser v2** (F2): normalizador con números hablados, léxico por trie sobre el vocabulario, gramática de enlaces
  (cantidad → alimento, «X con Y», exclusiones frente a atributos, correcciones, distribución) y resolutor de estado,
  forma y cantidad (F3: fracciones, envases, reconstitución de polvos, plato con compañía, preguntas materiales). Todo
  determinista y en `domain/nutrition/v2/` (Kotlin puro, 212 tests).
- **Micronutrientes** (F4): 33 claves con «sin dato» ≠ 0, cobertura por energía, DRI de embarazo y lactancia, topes
  de guía y la hoja agrupada.
- **Integración** (F5): adaptador a las tarjetas actuales con la misma compuerta que el examen, interruptor, fuentes y
  licencias, y paquete y vocabulario dentro del APK.
- **Tamaño:** el APK crece unos 9,7 MB (paquete de catálogo 9,2 MB comprimido, vocabulario 0,5 MB y licencias). En el
  teléfono, el paquete ocupa 33 MB del almacenamiento interno, una sola generación (D2); se copia en segundo plano al
  primer arranque (unos 4 s en el emulador).

## QA en el emulador (28-oct, 06:20–07:30)

Emulador Pixel 9 Pro XL con renderizado por software y APK de depuración KPKN Dev (`com.example.kpkn.dbg`).

| Qué | Resultado |
|---|---|
| Instalación y primer arranque | El APK (unos 520 MB) se instala y arranca; el asistente de alta funciona. |
| Publicación del paquete de catálogo | **Fallaba en el dispositivo**: `PRAGMA quick_check` revisa la tabla FTS4 de alias y Android la rechaza en solo lectura («attempt to write a readonly database»). Sin paquete, los registros se guardaban sin micronutrientes. Robolectric no lo mostraba. Arreglado (N33) y verificado con el APK corregido: 4 s después de abrir la app el paquete queda en `files/nutrition_catalog/bd5319a45f529dbe.sqlite` (33 MB), sin advertencias. |
| Ajustes, registro con v2 y hoja de nutrientes en pantalla | **Sin recorrer.** El emulador se cierra solo poco después de abrir la app (8 intentos, casi siempre a los 15–75 s), sin volcado de fallo ni pánico del kernel y con memoria y disco de sobra; otra sesión lo vio el 27-oct. Lo cubren las pruebas de Robolectric: interruptor y Fuentes y licencias (F5-b), hoja agrupada (F4-C), carga del vocabulario desde el asset real y guardado con instantánea de nutrientes (F4-B). |

## Pruebas automáticas al cerrar (28-oct, 07:30–08:00)

| Corrida (Android, Robolectric) | Resultado |
|---|---|
| Repositorio, base y respaldo de nutrición, catálogo, inventario de assets, `NutritionViewModelTest` y el APK | 180 pruebas: 176 en verde. Las 4 que fallan no son de esta ola. Tres son de `NutritionCalendarForecastCoordinatorTest` (pronóstico al editar un programa) y fallan igual en master `c4491bc83`, sin esta rama. La cuarta, `NutritionDayStateBackupTest`, destapó un fallo de master: restaurar una copia de seguridad estaba roto (N34). |
| Las tres pruebas de restauración de copias, con el arreglo N34 | En verde: `NutritionDayStateBackupTest`, `DatabaseBackupHelperSnapshotTest` y `TrainingSnapshotRestoreTest` (5 de 5, incluido que la copia externa no cambia; esta última seguía esperando la versión 36 y la actualicé a 37). |
| Otras dos pruebas de la base | Siguen en rojo por la subida de master a Room 37, sin relación con esta ola. `GlobalFoodsFtsTriggerHygieneTest` espera la versión 36. `TrainingMigrationRoomValidationTest` arma Room con su propia lista de migraciones, que no trae la 36→37 (la app sí la tiene). |

## Lo que queda pendiente o flojo

1. **Probar en tu teléfono.** La QA de esta noche fue en el emulador (orden tuya): encontró y arregló un fallo grave (N33), pero el
   emulador no aguantó lo suficiente para recorrer en pantalla «registrar con v2 → guardar → Nutrientes del día». Falta hacerlo
   en el Flip5 con tus registros.
2. **Activar v2 por defecto** es tu decisión. Lo recomiendo, junto con el botón «Usar lo estimado en todo».
3. **Chips de estado y forma en la tarjeta** (parte visual de F3): la pregunta llega como las tarjetas de aclaración
   de hoy, sin chips nuevos.
4. **Micronutrientes tras editar:** `CatalogRecordLink` solo reconoce fichas `usda_` y `off_`. Al corregir gramos o
   ficha en un alimento de CIQUAL o en un plato derivado (`kpkn_`), el registro conserva macros pero pierde la
   instantánea de micronutrientes.
5. **Vocabulario:** la meta del plan era 8.000 conceptos y 40.000 alias; quedaron 1.652 y 22.425 (N5: conceptos
   completos antes que alias sueltos). La cola larga se encuentra por el buscador de siempre.
6. **Familias que aún fallan:** fracciones y tamaños de pizza, «la mitad del tarro», números con faltas («3 pedasos»),
   el número después del sustantivo, papas fritas de bolsa frente a las de plato, «un tinto» colombiano (café) y
   correcciones encadenadas (F12, 37,5 %).
7. v2 omite 29 de 1.299 menciones en TEST (master 8): a veces junta dos alimentos en uno.
8. **Fuera de esta ola:** master tiene 5 pruebas en rojo desde antes de esta ola: 3 de
   `NutritionCalendarForecastCoordinatorTest` (las del recálculo del pronóstico al editar un programa) y las 2 de la base
   fijadas a Room 36. No las investigué. La restauración de copias sí la arreglé (N34): con Room 37 estaba rota para
   toda copia nueva.

## Cómo reproducir

```bash
python -I scripts/nutrition_v2/exam/score.py --cases C:/kw/nutri-examen/cases/test.jsonl \
  --gold C:/kw/nutri-examen/sealed/gold-test.jsonl \
  --pred "C:/kw/nutri-examen/predictions/master@c4491bc83-20261028-0220.jsonl" \
  --pred-b "C:/kw/nutri-examen/predictions/final/v2@f6de40a13-20261028-0545.jsonl" \
  --split test --out-dir C:/kw/nutri-examen/reports/final-test
```

Las predicciones de v2 salen de `NutritionExamV2RunnerTest` por el carril JVM. Los ajustes van en
`C:/kw/nutri-examen/v2-runner.properties` (casos, exportación, nombre y carpeta de salida). La exportación se
reconstruye con `merge.py` y `export.py` desde `scripts/nutrition_v2/concepts/data/stage3`.
