CAL2 · Motor del calibrador

CAL2 completado sobre CAL1 en `C:\kw\u11`, rama `feat/screens-v2`. El nuevo paquete `domain/calibration/` produce decisiones explicables con elegibilidad, capacidad C, referencia B y origen, cociente, factor, cargas antes/después, motivo, nota, alcance, versión del estimador y política aplicada.

Compilan producción y las pruebas unitarias. Pasan las 73 pruebas dirigidas de 9 clases, sin fallos, errores ni omitidas. El motor y sus pruebas están en el commit `0f882cb11`; este informe se registra en un commit documental separado, ambos en español y con `Co-Authored-By: Codex <noreply@openai.com>`.

API pública:

| API | Contrato |
|---|---|
| `CalibrationEngine.eligibility(source, policy)` | Devuelve `OK`, `NO_EFFORT`, `RIR_TOO_HIGH`, `BODYWEIGHT`, `NO_EXTERNAL_LOAD`, `PARTIALS_ONLY` o `REPS_OUT_OF_RANGE`. |
| `CalibrationEngine.calibrate(source, pendingSets, references, nowEpochMillis, scope, policy, equipment)` | Función pura: todos los datos y el reloj se reciben por parámetros. Devuelve `CalibrationDecision`. |
| `RecordedCalibrationSet` | Identidad, contexto, kg externos originales, reps completas válidas, parciales y RIR/fallo declarados. En lastre, los kg son únicamente el lastre. |
| `PendingCalibrationSet` | Carga base guardada, carga actual, reps/reserva objetivo y protecciones por serie hecha o fijada a mano. |
| `CalibrationContext` | Igualdad exacta de configuración, equipo/contexto de rendimiento, lateralidad, setup, modo y convención de carga. Se comprueba además el identificador del ejercicio al proponer/aplicar. |
| `CalibrationReferenceCandidates` y `ReferenceResolver.resolve(...)` | Referencias ya consultadas: última calibración aceptada vigente → e1RM contextual del historial → 1RM declarado → PR kg × reps → ninguna. |
| `sessionCapacityAdjustmentFactor(C, B, policy)` | Fórmula compartida con `WorkoutLoadSuggestionController.computeSessionImprovementAdjustment`, sin duplicar umbrales. |
| `CalibrationEquipment(incrementKg)` | Incremento positivo del material; por defecto 2,5 kg. Reutiliza `LoadSuggestionEngine.roundLoad`, cuyo salto anterior de 0,5 kg sigue siendo el predeterminado para sus consumidores existentes. |
| `CalibrationDecision.applyTo(sets)` | Proyección pura mediante asignación absoluta. Conserva la base y vuelve a comprobar ejercicio, contexto, protecciones y base original antes de actualizar la carga actual. |
| `CalibrationPolicy.defaults` / `CalibrationPolicySnapshot` | Reúne y conserva los umbrales aplicados: reps 3–10, RIR 0–1, factor fijo 0,95 con 0–2 reps, límites 0,97/1,025 y 0,92/1,05, vigencia de 42 días e incremento de 2,5 kg. Incluye el umbral de dos ciclos congelados reservado a CAL5. |

Decisiones de cálculo:

- Se usa `calculateHybrid1RM` de CAL1, versión 2, sin bonus. El tope de 10 se aplica a las reps completas realizadas antes de sumar la reserva declarada: `min(completas, 10) + RIR`. Fallo equivale a RIR 0. Las 0–2 reps realizadas activan la bajada fija incluso con RIR 1.
- Las reps completas de CAL1 ya excluyen las parciales. El motor recibe ambas por separado; no resta dos veces las parciales ni las suma a la capacidad. Cero completas con parciales es `PARTIALS_ONLY`; cero completas sin parciales y con esfuerzo declarado permite la bajada fija.
- Corporal devuelve `BODYWEIGHT`; asistida devuelve `NO_EXTERNAL_LOAD`. Las cargas nulas, negativas o no finitas se rechazan como `NO_EXTERNAL_LOAD`. Reps/parciales negativos o reserva negativa devuelven `REPS_OUT_OF_RANGE`; superar 10 reps se admite con tope y nota.
- Las calibraciones aceptadas requieren antigüedad estrictamente menor de 42 días. Se descartan fechas futuras y capacidades aceptadas/históricas de una versión distinta del estimador actual. El adaptador debe aportar e1RM contextual recalculado con CAL1, no volumen ni una evaluación archivada con bonus. Las referencias declaradas también necesitan el contexto exacto; no se recibe TM.
- Con B, cada carga propuesta sale de `baseLoadKg × factor`. Con factor 1 se conserva incluso una base fuera del salto del material. Sin B, se usa `calculateWeightFrom1RM(C, repsObjetivo + RIRObjetivo)` y después se redondea; las series sin objetivo válido se omiten. No se inventa porcentaje de mejora.
- Si el redondeo elimina todos los cambios, el tipo es `HOLD`, también en primera referencia y bajada fija. El tope porcentual se aplica antes del redondeo, sin un segundo recorte de los kg resultantes.
- `NEXT_SESSIONS` entrega C como propuesta de referencia, sin cargas afectadas hoy. Con 0–2 reps no hay C que proponer: devuelve `NOT_APPLIED` con explicación. Aceptar y guardar esa propuesta corresponde a CAL3.

Tabla de casos comprobados, con incremento de 2,5 kg salvo indicación:

| Caso | Entrada y números | Resultado |
|---|---|---|
| 1. Sube, demo corregida | 69 × 10, RIR 1; reps al fallo 11; C = 94,3; B = 88; r = 1,0715909; mejora medida +7,1591 %; factor 1,05. | `RAISE`: S2–S4, 60 × 1,05 = 63 → **62,5 kg**. |
| 2. Mantiene | Misma serie, B = 94,3; r = 1. También r = 0,97001 / 0,99 / 1,02499. | Factor 1; 60 → **60 kg**. Una base de 61 kg sigue en 61. |
| 3. Baja limitada | 60 × 10, RIR 0; C = 80; B = 88; r = 0,9090909; factor 0,92. | `LOWER`: 60 × 0,92 = 55,2 → **55 kg**. |
| 4. Primera referencia | 69 × 10, RIR 1; C = 94,3; sin B. Objetivos 12/RIR2, 8/RIR1 y 5/RIR0. | Inversas 64,3 / 73,3 / 83,8 → **65 / 72,5 / 85 kg**; r y porcentaje nulos. |
| 5a. Sin esfuerzo | 69 × 10, RIR ausente, sin fallo. | `NO_EFFORT`, `NOT_APPLIED`, factor 1, sin C ni cambios. |
| 5b. Reserva alta | 69 × 10, RIR 2 o 3. | `RIR_TOO_HIGH`, sin cambios. |
| 5c. Corporal | Modo `BODYWEIGHT`. | `BODYWEIGHT`, sin cambios. |
| 5d. Sin carga externa | Asistida; carga 0, −1, NaN o infinita; lastre 0. | `NO_EXTERNAL_LOAD`, sin cambios. |
| 5e. Solo parciales | 0 completas y 5 parciales. | `PARTIALS_ONLY`, sin cambios. |
| 5f. Datos fuera de rango | −1 completa, −1 parcial o RIR −1. | `REPS_OUT_OF_RANGE`, sin cambios. |
| 6. De 0 a 2 reps | 69 kg, RIR 1, con 0, 1 o 2 completas. | Sin C; factor 0,95; 60 × 0,95 = 57 → **57,5 kg**. También funciona sin B. |
| 7. Más de 10 reps | 69 × 11/15/Int.MAX_VALUE; RIR 0 o 1. | Usa 10 completas: C = **92 / 94,3 kg**; nota «usa más carga la próxima vez». |
| 8. Protegidas | S2 hecha, S3 fijada a mano, S4 libre. | Solo S4: **60 → 62,5 kg**; se excluye también la propia calibradora. |
| 9. Idempotencia | Aplicar dos veces, recalcular con carga actual 62,5 o editar la calibradora a 70 × 10, RIR 1. | Base guardada **60 kg**; resultado **62,5 kg**, sin multiplicaciones acumuladas. |
| 10. Aislamiento | Otro ejercicio o distinta configuración, equipo, lado, setup, modo o convención. | No aparece en la decisión ni cambia al aplicar, incluso si coincide el ID de la serie. |
| 11. Factor compartido | Controlador y motor reciben los mismos C/B, con subida, igualdad, bajada, umbrales y ausencia de B. | Factores idénticos. |
| Umbrales inclusivos | C/B = 102,5/100 y 97/100. | Factores **1,025 y 0,97**; justo dentro de la zona muerta, factor 1. |
| Redondeo sin cambio | Base 10 kg con factor 1,05, 0,92 o 0,95; primera referencia con base 65. | `HOLD`, sin variar la carga. |
| Parciales separadas | 69 × 3 completas + 7 parciales, RIR 0. | C = **73,1 kg**, calculada solo con las 3 completas. |
| Fallo | 69 × 10, sin RIR pero con fallo. | C = **92 kg**; con B = 88, factor **1,0454545**. |
| Lastre | 20 kg añadidos × 5, RIR 0; sin B; objetivo 12/RIR2. | C = **22,5 kg de lastre**; propuesta **15 kg de lastre**. |
| Referencias | Aceptada 100, historial 90, declarado 88, PR 60 × 10 = 80. | Orígenes en ese orden; exactamente 42 días, fecha futura o versión antigua hacen continuar al siguiente origen. |
| Material y política | Factor 1,05, base 60, incremento 1. Política alternativa con tope 1,03 e incremento 1. | **63 kg** y **62 kg**, respectivamente; decisión conserva la política usada. |
| Próximas sesiones | 69 × 10, RIR 1; con B o sin B. | C propuesta **94,3 kg**; S2–S4 hoy siguen en **60 kg**. |

La demo V7 original calculaba 92 kg al omitir el RIR. Con el contrato de CAL2, el híbrido común usa su rama de 11 reps: `69 × (1 + 11/30) = 94,3 kg`. La mejora medida es +7,1591 %, con factor limitado a +5 %. Su ejemplo de subida conserva 62,5 kg; la primera referencia para 12 reps/RIR2 cambia de 62,5 a 65 kg. Para demostrar mantener se usa B = 94,3; para reproducir r = 0,9090909 se usa 60 × 10 a RIR 0 y B = 88. Estos números corregidos quedan en este informe y en pruebas ejecutables; el informe histórico del prototipo y sus artefactos se conservan.

Validación ejecutada mediante el wrapper en primer plano:

```bash
bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native \
  "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" \
  :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest \
  --tests 'com.example.kpkn.domain.calibration.*' \
  --tests '*Cal2LoadAdjustmentParityTest' \
  --tests '*Cal1TrainingMaxImprovementTest' \
  --tests '*WorkoutLoadSuggestionIncrementalTest' \
  --tests '*LoadSuggestionEngineTest' \
  --tests '*Cal1CapacityContractTest'
```

Resultado: `BUILD SUCCESSFUL in 10m 54s`, después de 135 s de espera de ranura. La selección incluye 59 pruebas de `domain.calibration`, una de paridad con el controlador y 13 regresiones existentes de capacidad, sugerencias, TM y actualización incremental. Los XML de `android-native/app/build/test-results/testBaseDebugUnitTest/` confirman 73 pruebas, 9 clases y cero fallos, errores u omitidas. `git diff --check` pasa; la revisión de imports confirma dominio sin Android ni Room. No se ejecutó la batería completa.

Pendiente para CAL3: adaptar los datos consultados/registrados a esta API, guardar de forma durable la carga base y las decisiones en Room v30, enganchar el registro real y el panel V7, resolver Aplicar/Conservar/Revertir, recuperar tras reinicio, consumir únicamente referencias aceptadas en sesiones posteriores y mostrar el resumen final. `applyTo` es una operación pura sobre los datos recibidos; no sustituye esa integración ni persiste una aceptación. Las reglas de programas gestionados siguen correspondiendo a CAL5/CAL5b.

El paquete nuevo no importa Android ni Room ni hace consultas o acceso a archivos. No se han modificado la interfaz, la persistencia ni las reglas de programas. Todo el trabajo se limita a `C:\kw\u11`; sin teléfono, adb, `--stop`, procesos Java terminados, push ni modificaciones de `artifacts/` o auditorías.
