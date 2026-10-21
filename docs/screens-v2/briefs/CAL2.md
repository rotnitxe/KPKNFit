# CAL2 · Motor del calibrador (paquete 2 del plan)

Worktree `C:\kw\u11`, rama `feat/screens-v2`, sobre el resultado de CAL1. Lee primero:
- `C:\kw\u11\CLAUDE.md`;
- `docs/screens-v2/CALIBRADOR_V1.md` (§0, §2, §7 y la fila CAL2 de §6);
- `docs/screens-v2/reports/CAL1.md`;
- `docs/screens-v2/audits/RES1_calibrador_fuentes.md` §12;
- `docs/screens-v2/reports/PROTO_R6B_amrap_vivo.md`, con los números de la demo de V7 que el motor debe reproducir.

## Objetivo

Crear `domain/calibration/`: Kotlin puro, sin `android.*` ni Room. Recibe una serie calibradora registrada, las series pendientes del mismo ejercicio, la referencia y el material, y devuelve una `CalibrationDecision` completa y explicable. No toca la interfaz ni la persistencia; eso es CAL3.

## Contenido

- **`CalibrationPolicy`**: un único objeto con todos los umbrales de CALIBRADOR_V1 §7 y sus valores por defecto.
  - Reps de 3 a 10, con 10 como tope.
  - RIR de 0 a 1.
  - −5 % si se hacen 0–2 reps.
  - Zona muerta entre 0,97 y 1,025.
  - Factor entre 0,92 y 1,05.
  - 42 días de vigencia de la referencia.
- **Elegibilidad** (`eligibility`) con estos motivos: `OK`, `NO_EFFORT`, `RIR_TOO_HIGH`, `BODYWEIGHT`, `NO_EXTERNAL_LOAD`, `PARTIALS_ONLY` y `REPS_OUT_OF_RANGE`. Con lastre, C se calcula sobre el lastre declarado; con peso corporal o asistida no se calibra.
- **Capacidad C** con el estimador común de CAL1, sin bonus.
  - Reps al fallo = reps completas válidas + RIR declarado (0 o 1).
  - Con más de 10 reps se usa 10 y se marca la nota «usa más carga la próxima vez».
  - Con 0–2 reps no se calcula C: se aplica la bajada fija.
- **Referencia B** (`ReferenceResolver`). Recibe por parámetros los datos ya consultados, en este orden de prioridad:
  1. calibración aceptada del mismo contexto con menos de 42 días;
  2. e1RM contextual del historial;
  3. 1RM declarado;
  4. PR «kg × reps»;
  5. ninguna.
  - Contexto = configuración + equipo + lateralidad. Nunca mezcles contextos.
- **Factor**: reutiliza la fórmula de `computeSessionImprovementAdjustment`. Extráela a una función pura compartida y haz que el controlador la llame, para no duplicarla.
- **Cargas propuestas** para las series pendientes compatibles: con carga externa, sin hacer, no fijadas a mano y del mismo ejercicio y contexto.
  - Se calculan como `base × factor`, redondeando al incremento del material. Usa el redondeo que ya existe; si no lo hay, ponlo como parámetro con 2,5 kg por defecto.
  - Sin referencia: cargas desde C con las inversas existentes, según las reps y la reserva objetivo de cada serie.
  - Si el redondeo deja la carga igual, la decisión es «mantener».
  - Nunca se toca otro ejercicio.
- **`CalibrationDecision`** (modelo de dominio):
  - C, B y su origen;
  - `r`, el factor y el tipo (`RAISE`, `HOLD`, `LOWER`, `FIRST_REFERENCE`, `LOW_REPS_DROP`, `NOT_APPLIED`);
  - las series afectadas con su carga antes y después;
  - el motivo, la nota y el alcance;
  - `estimatorVersion` y la política aplicada.
  - Debe ser idempotente: aplicar una decisión sobre cargas ya ajustadas no vuelve a multiplicar. Parte siempre de la carga base guardada.

## Pruebas (tabla de casos con números, en `app/src/test/.../domain/calibration/`)

1. **Sube.** Con 69 kg × 10 a RIR 1 y B = 88, C coincide con la fórmula de CAL1. Comprueba qué da, documéntalo y recalcula la demo si difiere. S2–S4 pasan de 60 kg a 62,5 kg.
2. **Mantiene.** `r` entre 0,97 y 1,025 deja las cargas igual.
3. **Baja limitada.** Con `r` = 0,909 el factor queda en 0,92 y 60 kg pasa a 55 kg tras redondear.
4. **Primera referencia.** Sin B, propone cargas desde C y no muestra porcentaje de mejora.
5. **Un caso por cada motivo de no aplicación**, incluida la serie solo con parciales.
6. **0–2 reps.** Bajada del −5 %, con el redondeo.
7. **Más de 10 reps.** Usa 10 y añade la nota.
8. **Series protegidas.** Las series hechas y las fijadas a mano no cambian.
9. **Idempotencia.** Aplicar dos veces da lo mismo.
10. **Aislamiento.** Ninguna decisión incluye series de otro ejercicio ni de otro contexto.
11. **Factor compartido.** El controlador y el motor dan el mismo factor para los mismos C y B.

## Reglas

- Las de CAL1: Gradle solo con `gradle_slot.sh` en primer plano, sin `--stop` ni matar java, sin teléfono, nada fuera de `C:\kw\u11`, sin push y con commits que lleven `Co-Authored-By: Codex <noreply@openai.com>`.
- Ejecuta `:app:compileBaseDebugKotlin` y `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.calibration.*"`, además de las pruebas del controlador de carga que toques.

## Informe

Escríbelo en `docs/screens-v2/reports/CAL2.md`: la API pública, los números de cada caso, las decisiones tomadas y lo que queda para CAL3.
