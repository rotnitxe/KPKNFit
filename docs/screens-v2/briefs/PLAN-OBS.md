# PLAN-OBS · Notas del relator para las reglas nuevas

**Worktree:** nuevo desde `feat/screens-v2` cuando la Fase 3 esté integrada (sugerido `C:\kw\u22`, rama `sv2/plan-obs`). Trabaja **solo** ahí.

## Qué leer

- `CLAUDE.md` y `docs/screens-v2/DISENO_CONTEXTO.md` (textos honestos y sin jerga).
- `docs/screens-v2/reports/PROG.md`, riesgo 8: las notas de `PlanObserver` siguen mostrando líneas genéricas para las reglas nuevas.
- `domain/relator/PlanObserver.kt`, `domain/training/AmrapProgramPreview.kt`, `domain/training/AuthoredProgressionEngine.kt`.
- `docs/screens-v2/CALIBRADOR_V1.md` §5 (reglas por programa).

## Qué construir

Las notas de `PlanObserver` deben reconocer las reglas nuevas de PROG y decir lo que de verdad hace cada una, en lugar de la línea genérica:

- `RepsOverStandardTm` (Juggernaut): TM según reps sobre el estándar y su tope;
- `StagedVolume` (GZCLP): etapa del T1/T2 y qué toca al cerrar;
- `ApreXrm` (APRE): XRM y serie 4;
- `CycleIncrement` (5/3/1 y otros): subida al cerrar ciclo o bloque, con la congelación si no se cumplen las reps.

Reutiliza `AmrapProgramPreview` para que el relator diga el mismo número que la vista previa; no dupliques la lógica.

**Leyes:**
- Refinar, no quitar: conserva las notas existentes que ya son correctas.
- Nada inventado: cada frase sale de la regla y del contexto reales.
- Sin jerga técnica en la interfaz; sí en el código.
- No toques el esquema de Room.

## Pruebas

- Una prueba por regla, con números, comprobando que la nota contiene el efecto real y no la línea genérica.
- Una prueba de que un AMRAP sin regla nueva sigue recibiendo la nota genérica correcta.
- Una prueba de que el número del relator coincide con `AmrapProgramPreview` para el mismo contexto.
- Dominio puro: usa el carril JVM (`bash /c/kw/tools/kpkn_jvm.sh <wt> "-PtestInclude=com/example/kpkn/domain/relator/**,com/example/kpkn/domain/training/**"`) y solo el carril Android si tocas alguna pantalla.

## Commits e informe

- Commits pequeños en español, `git add` solo de tus rutas, terminados en `Co-Authored-By: Qoder <noreply@anthropic.com>`.
- Informe `docs/screens-v2/reports/PLAN-OBS.md`: notas antes/después por regla, pruebas y riesgos.
