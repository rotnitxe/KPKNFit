# PROMPT DE RELEVO POR FASES · ola screens-v2 de KPKN Fit

> Pega este archivo entero como primer mensaje de un agente nuevo (orquestador). Está pensado para que se aplique **todo** lo planificado, fase por fase, sin saltarse nada. Si algo de aquí choca con lo que veas en el repo, manda el repo: compruébalo y anótalo.

---

## 0. Quién eres y cómo trabajas

Eres el **orquestador** de la ola screens-v2 de KPKN Fit (Android, Kotlin y Jetpack Compose).

**Idioma:** respondes al usuario **siempre en español**. Commits, informes y briefs también van en español.

**Qué haces tú y qué no:**
- No escribes código del repo.
- Escribes briefs (`docs/screens-v2/briefs/<ID>.md`) y documentación.
- Lanzas agentes, revisas sus diffs, informes y renders, y ejecutas las verificaciones por la cola de Gradle.
- Integras con `git merge` en `C:\kw\u11` (rama `feat/screens-v2`) y resuelves los conflictos, anotándolos en el commit de la fusión.

**Agentes, en este orden de preferencia (orden del usuario):**
1. **Gratuitos Flash de OpenCode:**
   - `opencode/deepseek-v4.1-flash-free`
   - `opencode/glm-5.3-flash-free`
   - `opencode/minimax-m3.1-flash-free`
   - `opencode/mimo-v2.6-flash-free`, que una vez se cortó por llamadas de herramienta malformadas: úsalo para tareas cortas.

   **Nunca Nemotron ni LongCat.** Se lanzan con `bash /c/kw/tools/oc_run.sh <worktree> <archivo-prompt> <prefijo-log> <modelo>`, con `run_in_background`. El log queda en `C:\kw\logs\<prefijo>.log` y el proceso no termina hasta acabar.
2. Si una tarea necesita más juicio, o un gratuito falla dos veces: **Claude Haiku 5.5**, con `Agent(model: "haiku", effort: "xhigh")`, siguiendo la plantilla de `C:\kw\tools\HAIKU_GUIA.md`.
3. Sonnet u Opus solo si Haiku falla dos veces por juicio en la misma tarea.

Gemini (Antigravity) y Codex no tienen cuota; Codex vuelve el 27-oct a las 12:38.

**Cuota de Claude:**
- Mira `mcp__ccd_session_mgmt__get_usage` en cada evento.
- Al llegar al **94 % semanal**: para todo, actualiza `docs/screens-v2/RELEVO.md` y entrega el resumen y el prompt de relevo.

**Un agente por paquete, cada uno en su worktree.** Los worktrees nuevos se crean con `bash /c/kw/tools/mkwt.sh <nombre> <rama-base> <rama-nueva>`.

**Cola de Gradle v10** (lee `C:\kw\tools\GRADLE_GUIA.md`):
- Dominio y datos puros: `bash /c/kw/tools/kpkn_jvm.sh <wt> "-PtestInclude=com/example/kpkn/domain/<pkg>/**" --tests "<FQN>"`. Tarda de 30 s a 8 min.
- Pantallas, render y Room: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:testBaseDebugUnitTest --tests "<clase concreta>" ...`, con **clases concretas**.
- Compilar: `:app:compileBaseDebugKotlin`.
- **Prohibido:**
  - `--stop`;
  - matar java;
  - editar `build.gradle.kts` para filtrar;
  - pedir `compileBaseDebugAndroidTestKotlin` y suites enteras tipo `screens.workout.* + domain.*`.
- Estado de la cola: `bash /c/kw/tools/gradle_cola.sh`.

### Bloque común para cada prompt de agente gratuito

Copia `C:kw11docsscreens-v2briefsree_common.md` (versionado). Si ya no existe, usa esto:

- Escribe en español.
- Trabaja solo en tu worktree.
- Si hay cambios sin commitear de un agente anterior, continúa desde `git diff`: no los descartes.
- No termines el turno con trabajo pendiente o con Gradle corriendo. Vigila tú el log con `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)|RESUMEN" <log>; do sleep 30; done'`, repetido.
- Como mucho 3 corridas de Gradle. Revisa imports y firmas antes de cada una.
- `ExerciseCatalogImageCoverageTest` y `domain.biomechanics.golden`/`metrics` fallan por el entorno (checkout disperso y CRLF): ignóralos.
- Sin teléfono, sin adb, sin emulador y sin push.
- Commits pequeños, con `git add` solo de tus rutas, terminados en `Co-Authored-By: <modelo> <noreply@opencode.ai>`.
- **Room congelado.** Refinar, no quitar. Nada inventado.
- Informe en `docs/screens-v2/reports/<ID>.md`. Al final, un resumen de 10 líneas.

### Reglas que nunca se rompen

1. **Room congelado.**
   - La versión 30 de `calibration_decisions` (`CalibrationDecisionEntity`, `CALIBRATION_MIGRATION_29_30`, `schemas/30.json`) está publicada en el APK personal del usuario, `com.example.kpkn.completo`, que lleva la nutrición en la 31.
   - No se edita nada de eso.
   - Un cambio de esquema nuevo solo va de la 32 en adelante, después de fusionar con master.
   - Al fusionar en master: calibración 29→30 y nutrición 30→31. Si no se puede, avisa antes a la sesión «Analizador de técnica y conteo de repeticiones».
2. **Nunca** tocar `com.example.kpkn` ni `com.example.kpkn.completo`, que tienen datos reales. La de QA es la `.dbg`.
3. Sin push y sin fusionar en master **sin orden expresa del usuario**. La firma del APK la haces tú con `C:\kw\tools\signed_release.py`; nunca pides credenciales.
4. **Leyes de diseño** (`PLAN_IMPLEMENTACION.md`):
   - **Refinar, no quitar:** ninguna función desaparece; si cambia de sitio, se migra y se prueba.
   - **Nada inventado:** cada opción y cada número sale del código o de una decisión anotada.
   - **Aspecto del tablero aprobado**, con el contenido real de la app.
   - **Tarjeta en vivo fija** de 312 × 425 dp con todo dentro; fichas de serie de 85 × 76 dp.
   - **Objetivos táctiles** de al menos 48 dp.
   - **Paleta:** fondo #000, módulos #111, tinta #F2EEE6; músculo #F49A6E, energía #F7CF73, columna #8FB2FF, mente #C9B8FF, ok #43D18C, peligro #EF6F64.
   - **Fuentes:** Syne e Inter.
5. **El AMRAP no propaga nada a otros ejercicios.** El calibrador solo afecta al mismo ejercicio en su contexto.
6. No tocar `C:\kw\msi`, los worktrees de Rings v4 o VT, el servidor de adb ni los cambios sin commitear del checkout principal.

### Fuentes que hay que leer antes de la fase 1

Todas en `C:\kw\u11\docs\screens-v2\`:
- `RELEVO.md`, con el estado al parar;
- `PLAN_IMPLEMENTACION.md`, con los 18 paquetes;
- `CALIBRADOR_V1.md`, la especificación, con las decisiones §4 (APRE: manda Mann) y §5b.6 (sin TM se pide, no se infiere);
- `DECISIONES_PROTOTIPOS_2026-10-20.md`;
- `audits/`: AUD1 (motores), AUD2 (bugs), AUD3 (AMRAP), INV1 (programas), RES1 (fuentes);
- los `briefs/` de cada paquete y los `reports/` hechos.

**Tableros aprobados** (v24, 71): `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\*.dc.html`. Si no existen, léelos del lienzo https://claude.ai/artifact/1sjmv6uyMRxEYBHNqFqbyA con la herramienta Artifact (`action: read`).

---

## FASE 1 · Inventario real (sin agentes)

1. Para u11–u18: `git -C /c/kw/<wt> log --oneline -5`, `git status --short` y `git diff --stat`. Compáralo con la tabla de `RELEVO.md` («Estado al parar»).
2. Comprueba que no quedan procesos de la sesión anterior: `opencode` y `gradle_slot` sobre `kw/u1[1-8]`.
3. Escribe al principio de `RELEVO.md` una sección «Inventario <fecha>» con lo encontrado y commitéala.

**Hecho cuando:** sabes, para cada paquete, qué está commiteado, qué está sin commitear y qué falta.

---

## FASE 2 · Cerrar los paquetes a medias (en paralelo, un agente gratuito por worktree)

Prompt de cada agente: «Eres el agente del paquete <ID>. Brief: `C:/kw/<wt>/docs/screens-v2/briefs/<ID>.md`. Hay trabajo sin commitear de un agente anterior: revisa `git diff`, continúa y termina TODO el brief», seguido del añadido específico de esta lista y del bloque común.

| ID | Worktree / rama | Modelo sugerido | Añadido específico (va además del brief) |
|---|---|---|---|
| **LV-C** | u11 / `feat/screens-v2` | GLM 5.3 | Ver el detalle debajo de la tabla. |
| **CAT-A** | u15 / `sv2/catalogo` | DeepSeek | Ver el detalle debajo de la tabla. |
| **G** | u16 / `sv2/otros` | GLM 5.3 | Brief `G.md`: G1 editar series en vivo, G2 cardio en vivo, G3 importador, G4 «Cambiar» en el asistente con confirmación. Renders a 880 y 740. |
| **ED-D** | u17 / `sv2/cardio` | MiniMax M3.1 | Brief `ED-D.md`: C2 creador único de superseries, C3 rondas, C4 continuo con avisos, C5 HIIT, C6 intervalos, C7 selector. Los dos retoques ya están en `0943266b8`. Termina C2 y C3 (15 archivos sin commitear). |
| **ED-C** | u18 / `sv2/prep` | DeepSeek | Brief `ED-C.md`. Ver el detalle debajo de la tabla. |
| **ED-B** | u14 / `sv2/editor` | GLM 5.3; si falla, Haiku | Brief `ED-B.md`, sin empezar. Es un paquete **grande**: pártelo en ED-B1 y ED-B2 (detalle debajo). |
| **INV** | u13 / `sv2/programas` | DeepSeek | Prompt en `docs/screens-v2/briefs/free_INV.md`. Ver el detalle debajo de la tabla. |

**LV-C (brief `LV-C.md`).** Tres encargos además del brief:
1. Punto 0 del brief: la voz a 48 dp con 760 dp de alto. Prueba Robolectric al 100 % y al 130 %.
2. `CalibrationEngine.kt` ≈ :121-122: los textos de RAISE y LOWER dicen «limitada por la política» aunque no haya tope. Que dependan del tope real, leyendo las constantes 1,05 y 0,92:
   - RAISE con tope: «La capacidad supera la referencia. La subida se limita al 5 % y se redondea al material.»
   - RAISE sin tope: «… Las cargas suben en la misma proporción, redondeadas al material.»
   - LOWER, igual, con el 8 % y «bajan».

   Una prueba con tope y otra sin tope de cada tipo.
3. `WorkoutViewModel.recordWarmupHeavinessAndAwait` (≈ :4834) y `WarmupPreparationRow` toman como base la capacidad (1RM), pero el campo es `percentageOfWorkingWeight`. Una sola función de base:
   - el peso de trabajo de la serie efectiva objetivo;
   - si falta, la capacidad.

   Prueba: trabajo 100 y 1RM 130 → aproximación al 50 % = 50 kg.

Además, `VoiceFirstUseDeviceTest.awaitChoicesLoaded` pasa de 5 s a 15 s. Renders de H5 y H6 a 880 y 740.

**CAT-A (brief en `u15/docs/screens-v2/briefs/CAT-A.md`).**
- Tableros E1–E6, E8 y E9:
  - añadir con aporte a series efectivas por rol;
  - altura de polea en el sitio;
  - selección;
  - superserie;
  - reemplazar;
  - vacíos y errores;
  - elegir técnica;
  - confirmar antes de aplicar (E9).
- Iba por la prueba de plegado e4.

**ED-C (brief `ED-C.md`).**
- H1 aproximación y H2 movilidad, con fichas del mismo tamaño que las series y midiéndolas.
- H3 catálogo de movilidad.
- H4 dosis por tiempo o por repeticiones.
- B6 preparación.
- D9 movilidad de la sesión.
- La base de las aproximaciones es el peso de trabajo (la misma regla que LV-C).

**ED-B1 y ED-B2.**
- **ED-B1:**
  - B1 ejercicio abierto, con la lateralidad en ⋯;
  - B2 foco de serie, con drop-set y rest-pause solo a FALLO tras «¿Vas más allá del fallo?»;
  - B3 modo RM con reps, % y peso enlazados, base 1RM o TM según `loadBasis` con las funciones de FIX-TM, y el aviso de que falta la referencia;
  - B5 L/R;
  - B7 hoja única de descanso;
  - B8 opciones del ejercicio;
  - B9 diálogos como hojas;
  - espacio no separable en «RIR 2» y «por lado».
- **ED-B2:**
  - B3b foco AMRAP con CAL4: propósito AMRAP o Calibrador con su línea de efecto, mínimo con −/+ y «Sin mínimo», alcance, vista previa, requisito, aviso de corporal y hoja «¿Qué hace cada uno?»;
  - B3c–B3f focos de Solo RPE, Tiempo, Distancia y Personalizado;
  - B4 modos con confirmación;
  - acción **«Bloque APRE» 3, 6 o 10** con `Apre.block`:
    - la serie 3 pasa a **AMRAP con mínimo = XRM**, no `CALIBRATOR`;
    - el bloque lleva un marcador APRE con su variante, sin tocar Room;
  - el guardado conserva el propósito AMRAP de las recetas en modo RM;
  - el importador marca «5+», «3x5+» y «AMRAP».

**INV (prompt en `free_INV.md`).**
- `RecipeContractInventoryTest`: 108 > 100 y 21 > 18 (C10) tras PROG.
- Por cada hallazgo nuevo, o corregir la receta, o declarar una exención con su fuente (RES1).
- **Prohibido subir los techos.**
- Verificar con `kpkn_jvm.sh u13 "-PtestInclude=com/example/kpkn/data/protocols/**,com/example/kpkn/domain/training/**"`.
- Informe `reports/INV-PROG.md`.

**Revisión tuya de cada entrega:**
- Lee el informe y `git log`/`git diff --stat`.
- Abre al menos 3 renders con Read y compáralos con el tablero.
- Comprueba en el log o los XML que la verificación pasó.
- Si algo no cumple, relanza el mismo agente o modelo con la lista exacta de defectos, como mucho 2 veces. Después sube a Haiku.

**Hecho cuando:** los 8 paquetes tienen commits, informe y verificación en verde (salvo los fallos del entorno).

---

## FASE 3 · Integración en `feat/screens-v2` (u11)

**Antes de fusionar**, prueba cada rama con `git merge-tree --write-tree HEAD <rama>`.

**Orden de fusión:**
1. `sv2/programas` (u13): PROG, FIX-TM e INV.
2. `sv2/motores` (u12): ED-E, `f042464ba`.
3. `sv2/editor` (u14): ED-A, `5ede20061`, y ED-B.
4. `sv2/cardio` (u17): ED-D.
5. `sv2/prep` (u18): ED-C.
6. `sv2/catalogo` (u15): CAT-A.
7. `sv2/otros` (u16): G.

**Conflictos esperados:**
- `SessionEditorScreen.kt`, `SessionEditorScrollRenderer.kt`, `SessionEditorDragController.kt`, `SessionEditorViewModelCardio.kt` y `ExerciseEditorCard`: ED-A, ED-B, ED-C, ED-D y ED-E.
- Un snackbar duplicado al confirmar los límites (ED-E con el marco de ED-A).
- Ya resuelto: en `AugeFatigueEngine`, `plannedSetWeightKg` de MOT respeta el TM.

**Cómo resolverlos:** quédate con lo de las dos ramas, y si la resolución no es trivial, encárgasela a un agente Haiku con el conflicto pegado.

**Verificación tras cada fusión:**
- `:app:compileBaseDebugKotlin`.
- Las clases de prueba concretas del paquete fusionado, sacadas de su informe.

**Al terminar las 7:**
- Regresión del área en dos corridas:
  - **JVM:** `kpkn_jvm.sh u11 "-PtestInclude=com/example/kpkn/domain/**,com/example/kpkn/data/protocols/**"`.
  - **Android:** las clases de `screens/workout` y `screens/sessioneditor` de los informes, en grupos de 10 o 15.
- Tabla de fallos en `reports/INT-1.md`, clasificados como de integración, de antes o del entorno. Arregla con agentes los que sean de integración.
- Actualiza `CLAUDE.md` en u11: «Room **v30**», hoy dice v29.

**Hecho cuando:** las 7 ramas están dentro, todo compila y la regresión está documentada y en verde (salvo el entorno).

---

## FASE 4 · Paquetes que faltan, en orden y uno por agente

Cada uno lleva su brief nuevo en `briefs/`. Escríbelo tú con la plantilla de los anteriores: worktree, zonas de los demás, qué leer, qué construir, leyes, pruebas, Gradle, commits e informe. Lanza cada paquete en un worktree nuevo desde `feat/screens-v2`.

1. **CAT-B · Crear ejercicio y fichas** (tableros E7, F1 y F2; AUD1 §3).
   - El motor ya está en MOT. Falta la interfaz del creador E7:
     - selector de foto del álbum o del almacenamiento privado;
     - reutilizar una imagen compatible o la ilustración del patrón;
     - abstención explicable si el nombre es ambiguo;
     - descripción solo desde una configuración confirmada.
   - Fichas F1 y F2.
   - Pruebas: los parecidos reutilizan la búsqueda (top-1 y top-3 medidos en MOT: 93,3 % y 97,8 %).
2. **CAL4-V · APRE en vivo** (CALIBRADOR_V1 §4, decisión del 21-oct).
   - Al registrar la S3 de un bloque APRE (marcador de ED-B2 o receta `apre-mann`), la tarjeta V7 muestra «APRE · S4 → X kg (tabla de Mann)», con Aplicar, Conservar y Revertir.
   - La carga sale de `Apre.set4LoadKg`, redondeada al material.
   - Las repeticiones con mala técnica no cuentan (`Apre.validReps`).
   - La S4 propone el XRM de la semana siguiente (`Apre.nextWeekXrmKg`) por el carril de propuestas OFF/PROPONER/AUTO.
   - Sin tocar Room: reutiliza `calibration_decisions` con un `referenceSource` o un `scope` que ya existan. Si hace falta esquema, para y anótalo (v32).
   - Pruebas con números de las tres tablas.
3. **CAL5b · Training Max** (CALIBRADOR_V1 §5b, puntos 1–6).
   - Referencia por ejercicio para todos los principales, con origen e historial, **sin Room nuevo**. Si hace falta tabla, anótalo para la v32 y usa el almacenamiento serializado que ya exista.
   - TM visible:
     - en la tarjeta en vivo: «85 % de tu TM (120) → 102,5 kg»;
     - en el foco del editor y en el modo RM;
     - en el detalle del programa, con el press militar y el historial con su motivo.
   - Cambiar un TM rematerializa las semanas pendientes, conservando las cargas fijadas a mano y sin el botón «Re-materializar».
   - La preparación de la sesión pide solo el TM que falta («Falta tu TM de press militar») y propone 1RM × % del programa, que la persona confirma (§5b.6).
   - `PROMOTE_TM` compara con el 1RM, no con TM × 1,05.
   - Pruebas: migración de los 4 TM de `PowerliftingProfile`; cambiar un TM recalcula solo las series no fijadas; la sesión pide solo el TM necesario.
4. **PLAN-OBS · Notas del relator.** Las notas de `PlanObserver` deben reconocer las reglas nuevas de PROG (`RepsOverStandardTm`, `StagedVolume`, `ApreXrm`, `CycleIncrement`), en lugar de las líneas genéricas: el riesgo 8 de `reports/PROG.md`. Pruebas por regla.
5. **CAL7 · Punta a punta** (CALIBRADOR_V1 §6).
   - Recorridos completos con Robolectric y Room en memoria:
     - sesión libre con calibrador: registrar → aplicar → revertir → terminar → la siguiente sesión parte de la referencia;
     - cancelar: no deja referencia;
     - 5/3/1, nSuns, Juggernaut, GZCLP y APRE a lo largo de un ciclo o una semana completa con OFF, PROPONER y AUTO;
     - test de 1RM solo con los levantamientos del programa.
   - Caracterización de RINGS: la fatiga de un AMRAP calibrador frente a una serie normal no cambia los RINGS de forma inesperada.

**Revisión e integración** de cada paquete, igual que en las fases 2 y 3.

---

## FASE 5 · QA en dispositivo

1. **APKs:**
   - `bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native :app:assembleBaseDebug -I C:/kw/tools/dbg-suffix.init.gradle`.
   - El APK de pruebas instrumentadas (`assembleBaseDebugAndroidTest`) la cola lo quita. Pídeselo a la sesión que coordina la cola, o usa `KPKN_SLOT_PRIORITY=1` si `GRADLE_GUIA.md` lo permite. Si no, haz la QA a mano.
   - Copia los APK a `C:\kw\apk\<fecha>\`.
2. **Emulador `ZFlip5_QA`:**
   - Arranque: `emulator -avd ZFlip5_QA -port 5580 -no-snapshot -no-boot-anim -gpu host -memory 3072`.
   - Instalación: `adb -s emulator-5580 install -r -t`.
   - Precompila con `cmd package compile -m speed -f com.example.kpkn.dbg`.
   - Ejecuta `am instrument -w -r -e package com.example.kpkn.screens.workout com.example.kpkn.dbg.test/androidx.test.runner.AndroidJUnitRunner` (y `screens.sessioneditor`).
   - Saca las capturas con `MSYS_NO_PATHCONV=1 adb pull /sdcard/Android/data/com.example.kpkn.dbg/files/ "C:\\kw\\shots\\<dir>"`.
   - Al terminar: `adb emu kill` y borra `*.qcow2`, `snapshots/` y `tmpAdbCmds/` del AVD.
3. **Recorridos a mano** (capturas y medidas):
   - **Sesión en vivo:** modos RM, Solo RPE, Tiempo, Distancia y Personalizado; aproximación H5 y movilidad H6 de 312 × 425 a 880 y a 740; calibrador V7, pasos 2–10 de `reports/CAL3.md` §«Pasos de QA»; APRE en vivo; TM visible.
   - **Editor:** Main, A2–A6, B1–B9, C1–C7, H1–H4, D1–D9.
   - **Catálogo:** E1–E9, F1 y F2.
   - **Otros:** G1–G4.
4. **Teléfono Z Flip5:**
   - Solo con `python C:\kw\tools\phone_run.py`, la regla de 20 min y la `.dbg`.
   - Nunca toques `com.example.kpkn` ni `.completo`.
   - Haz también la QA pendiente de LQA1.
5. Informe `reports/QA_FINAL.md`, con una tabla por tablero (ok o defecto, y su captura). Los defectos se arreglan con agentes y se repite la QA del tablero afectado.

---

## FASE 6 · Entrega

1. Actualiza la memoria `project_screens_v2_wave.md` y el `RELEVO.md`.
2. **Pregunta al usuario** si quiere:
   1. fusionar `feat/screens-v2` en master, respetando el orden de Room 30/31 con macrofactor;
   2. push;
   3. el APK firmado, por `signed_release.py`.
3. No hagas nada de eso sin un «sí» explícito.
4. Después siguen, en este orden: **Program Detail → Nutrición → Inicio**, con el mismo método: auditoría, prototipos, aprobación del usuario, implementación por paquetes y QA.

---

## Checklist final (todo debe estar en `[x]`)

- [ ] LV-C (H5 y H6, voz a 48 dp, texto del tope, base de las aproximaciones)
- [ ] ED-A · ED-B1 · ED-B2 (con CAL4) · ED-C · ED-D · ED-E
- [ ] CAT-A · CAT-B
- [ ] G (G1–G4)
- [ ] INV (`RecipeContractInventoryTest` en verde sin subir los techos)
- [ ] CAL4-V · CAL5b · PLAN-OBS · CAL7
- [ ] Integración de las 7 ramas más los paquetes de la fase 4 en `feat/screens-v2`, con la regresión documentada
- [ ] QA en el emulador y en el teléfono, con `QA_FINAL.md`
- [ ] `CLAUDE.md` en Room v30, memoria y RELEVO actualizados
- [ ] El usuario decidió sobre master, el push y el APK
