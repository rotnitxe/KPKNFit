# AUD3 · AMRAP y AMRAP calibrador: ¿funcionan de verdad? (auditoría de solo lectura)

Worktree `C:\kw\u11` (rama `feat/screens-v2`). **No cambies código ni pruebas.** El único archivo que escribes es `docs/screens-v2/audits/AUD3_amrap.md`. Sin Gradle, sin adb, sin teléfono. Hay otro agente (AUD2) cambiando código del editor en este worktree: no lo toques.

Orden del usuario (2026-10-20): «Para AMRAP calibrador y AMRAPs, verifica la infraestructura y cómo funciona; no quiero que sea una función vacía, sobre todo para calibrador y AMRAP normal».

## Qué hay que responder, con evidencia (archivo:línea)

1. **Modelo y persistencia:** cómo se representa una serie AMRAP y una AMRAP calibrador (campos, enums, valores por defecto, migraciones de Room), qué se guarda al registrarla.
2. **Editor:** qué puede configurar la persona en cada una (mínimo de repeticiones, carga, intensidad…), qué validaciones hay y qué se muestra.
3. **Sesión en vivo:** cómo se registra (`SetExecutionCard.kt`, `WorkoutSetRecorder.kt` alrededor de :991 y vecinos): qué pasa si se queda por debajo del mínimo, si se supera, qué esfuerzo se pide.
4. **El calibrador de verdad:** ¿ajusta algo? ¿La carga sugerida de las series siguientes, la de la próxima sesión, el 1RM estimado, la carga inteligente, los RINGS? Sigue el dato extremo a extremo: desde que se registra hasta cada consumidor. Si la regla 1,10 / 0,90 solo produce un mensaje y no cambia nada, dilo claramente.
5. **AMRAP normal:** ¿alimenta el 1RM estimado, los récords, el historial, la progresión del programa (`BlockTransitionEngine`, progresiones automáticas), la carga inteligente? ¿O se registra como una serie más?
6. **Pruebas existentes** que cubren cada comportamiento, y huecos.
7. **Veredicto por función:** «completa», «parcial» (qué falta) o «vacía» (solo existe la etiqueta), con la lista de lo que haría falta para que sea completa y útil, ordenado por valor y con tamaño S/M/L.
8. Propuesta concreta de comportamiento para que las dos sean funciones reales y diferenciadas, basada en lo que la app ya calcula (no inventes fórmulas sin decirlo; si propones una, cita su fuente o razónala).

Tu último mensaje es un resumen de una página en español con el veredicto de cada función y el plan.
