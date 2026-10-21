# Plan de implementación · Sesión en vivo, editor, catálogo y calibrador (screens-v2)

Fecha: 2026-10-20. Rama: `feat/screens-v2` en `C:\kw\u11`, nacida de master `8ac57c9d4`.

El usuario aprobó todas las recomendaciones y todos los diseños del lienzo. Ordenó pasar a implementar.

## Fuentes

- **Diseños:** lienzo https://claude.ai/artifact/1sjmv6uyMRxEYBHNqFqbyA (versión 24, 71 tableros). La copia local de los tableros está en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\*.dc.html` y sus informes, en `INFORME_R*.txt`.
- **Decisiones:** `DECISIONES_PROTOTIPOS_2026-10-20.md`.
- **Motores:**
  - `audits/AUD1_motores_editor.md`: Impacto/Columna, Sugerencias y Crear ejercicio.
  - `audits/AUD2_bugs_editor_catalogo.md`: bugs. Lo está arreglando AUD2.
  - `CALIBRADOR_V1.md`, con `audits/AUD3_amrap.md`, `INV1_amrap_programas.md` y `RES1_calibrador_fuentes.md`.
- **Mapa de código del editor y del catálogo:** `maps/MAPA_editor_catalogo.md`.

## Reglas de trabajo (todas las olas)

- **Escriben agentes Codex** (`gpt-6.1-sol`, esfuerzo máximo), de uno en uno en `C:\kw\u11`. El orquestador escribe briefs y documentación, revisa diffs, ejecuta pruebas filtradas e integra.
- **Gradle** solo con `gradle_slot.sh`, en primer plano. Nunca `--stop` ni matar java. Tareas `...BaseDebug...`.
- **Leyes de diseño:**
  1. Refinar, no quitar. Ninguna función desaparece; si cambia de sitio, se migra.
  2. Nada inventado. Cada opción y cada número sale del código o de una decisión anotada.
  3. Aspecto del prototipo con contenido de la app.
  4. Tarjeta en vivo fija de 312 × 425 en el Z Flip. Fichas de serie de 85 × 76 iguales en todas partes. Nada fuera de la tarjeta.
  5. RIR/RPE parte del mínimo. Deslizador de esfuerzo aprobado (fino, con destello).
- **Pruebas:** cada paquete compila (`:app:compileBaseDebugKotlin` y `androidTest` si toca la interfaz) y pasa sus pruebas nuevas o afectadas. Al cerrar cada ola, regresión filtrada del área y QA en el Z Flip (`.dbg`, regla de 20 min).
- **Commits** con `Co-Authored-By: Codex <noreply@openai.com>`. Sin push sin orden del usuario.
- **Esquema de Room congelado desde el 21-oct.** El usuario usa con datos personales un APK `.dbg` de integración (`C:\kw\qa-todo`, rama `qa/todo-20261021`) que lleva:
  - calibración 29→30 (`CALIBRATION_MIGRATION_29_30`, `CalibrationDecisionEntity` tal como están en `a913d3742`);
  - nutrición 30→31 (`nutritionDayStateMigration(30)`, de `feat/macrofactor-parity`);
  - versión 31.

  Reglas:
  1. Nadie edita esa entidad, esa migración ni `schemas/30.json`.
  2. Todo cambio de esquema nuevo va en una migración de la 32 en adelante, después de fusionar con master.
  3. Al fusionar en master se mantiene ese orden (calibración 30, nutrición 31). Si hay que cambiarlo, antes se avisa a la sesión de técnica por vídeo, porque ajusta el APK.
  4. Las builds `.dbg` de esta rama (v30) no se instalan en el teléfono: el downgrade de Room rompería el arranque.

## Olas y paquetes (en este orden)

| # | Paquete | Contenido | Tableros o fuente |
|---|---|---|---|
| 0 | **AUD2** (en curso) | Bugs del editor y del catálogo, S1–S4, con pruebas | AUD2 |
| 1 | **CAL1 · Contrato AMRAP** | `SetPurpose`, mínimo efectivo, alcance, preservar AMRAP al editar, importador «+», separar el TM de `reference1RM`, estimador común sin bonus | CALIBRADOR_V1 §1, §4 y §6 |
| 2 | **CAL2 · Motor del calibrador** | `domain/calibration/` puro, sin propagación a otros ejercicios | CALIBRADOR_V1 §2 |
| 3 | **LV-A · Tercio inferior y cockpit** | Línea de sesión V1 con ✓ integrado, ruta de la sesión (V4), migración de cada función del cockpit (V5), voz e historial arriba, pantalla corta | V1, V3, V4, V5 |
| 4 | **LV-B · Tarjetas por modo** | Tarjeta en vivo para RM (V6), Solo RPE (V8), Tiempo (V9), Distancia (V10) y Personalizado (V11), deslizador de esfuerzo aprobado y ROM sin cambiar el alto (B8b) | V6, V8–V11, B8b |
| 5 | **CAL3 · Calibrador en vivo** | Room v30 `calibration_decisions`, resultado en la tarjeta (V7), Aplicar/Conservar/Revertir, referencia para la sesión siguiente, resumen final | V7, CALIBRADOR_V1 §3 |
| 6 | **LV-C · Aproximación y movilidad en vivo** | Tarjetas de 312 × 425 con todo dentro, con páginas internas | H5, H6 |
| 7 | **ED-A · Marco del editor** | Sin RIR global (A1); Fuerza y Cardio pendientes con colocación (A2, A2b); arrastre libre entre grupos y superseries (A3); borrar con deshacer (A4); semana (A5); un solo aviso (A6); superserie dentro de su grupo (C1) | Main, A2–A6, C1 |
| 8 | **ED-B · Ejercicio y series** | Ejercicio abierto con lateralidad en ⋯ (B1, B8); foco de serie solo con lo de la serie y técnicas tras «¿Vas más allá del fallo?» (B2); modo RM con reps, % y peso enlazados y referencia de 1RM/PR (B3); focos por modo (B3b–B3f, incluido **CAL4**); modos (B4); unilateral L/R (B5); descanso (B7); diálogos (B9) | B1–B9, B3b–B3f |
| 9 | **ED-C · Aproximación y movilidad en el editor** | Fichas iguales a las series, todo dentro del ejercicio | H1–H4, B6, D9 |
| 10 | **ED-D · Cardio** | Continuo, series, intervalos y selector; rondas y superseries | C2–C7 |
| 11 | **ED-E · Hojas y motores** | Acciones rápidas, Impacto con Columna correcta y cardio en los totales (AUD1 I1/I2/I4), Sugerencias rediseñadas con máximo 3 avisos (AUD1 S1–S5), plantillas con búsqueda primero (D4), reglas, tiempo, versiones y transferir | D1–D8, AUD1 §1–2 |
| 12 | **CAT-A · Catálogo** | Añadir con aporte a series efectivas por rol (E1), altura de polea en el sitio (E2), selección, superserie, reemplazar, vacíos y errores, elegir técnica | E1–E6, E8, E9 |
| 13 | **CAT-B · Crear ejercicio y fichas** | Creador rediseñado, motor de parecidos unificado con la búsqueda, descripción desde datos confirmados, imágenes (AUD1 E1–E7), fichas F1 y F2 | E7, F1, F2, AUD1 §3 |
| 14 | **G · Otros editores** | Editar series y cardio en vivo, importador, cambiar en el asistente | G1–G4 |
| 15 | **CAL5 · Programas** | Reglas fieles de 5/3/1, nSuns, Juggernaut, GZCLP, J&T, Cube, Lilliebridge y Rippler; plantillas `power-*`; test de 1RM; vista previa por programa | CALIBRADOR_V1 §5 |
| 16 | **CAL5b · TM** | Referencia por ejercicio, TM visible en vivo, en el editor y en el detalle (con press militar), rematerialización automática, petición en la preparación | CALIBRADOR_V1 §5b |
| 17 | **CAL6 · APRE** | Protocolo APRE 3/6/10 y acción «Bloque APRE» | CALIBRADOR_V1 §4 y §5 |
| 18 | **CAL7 · Punta a punta** | Recorridos completos y caracterización de RINGS | CALIBRADOR_V1 §6 |

**QA en el teléfono:**
- Tras 6, sesión en vivo completa.
- Tras 11, editor.
- Tras 14, catálogo y otros editores.
- Tras 18, calibrador y programas.

**Pendiente de antes:** QA de LQA1 en el Z Flip, en cuanto el teléfono esté libre.

## Después

Program Detail, Nutrición e Inicio (orden fijo del usuario). Al cerrar estas olas, se pregunta al usuario por la integración en master, el push y el APK.
