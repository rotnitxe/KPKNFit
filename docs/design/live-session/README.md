# Sesión en vivo · programa de diseño

Continuación de [PHASE0-MAPA.md](./PHASE0-MAPA.md). La Fase 0 fijó de dónde sale cada elemento; estas fases fijan **cómo se ve, cómo se comporta y qué se acepta** antes de tocar `android-native/`.

## Estado

| Fase | Documento | Pregunta que responde | Estado |
|---|---|---|---|
| 0 | [PHASE0-MAPA.md](./PHASE0-MAPA.md) | ¿De qué línea del código sale cada cosa? | cerrada |
| 1 | [FASE1-SISTEMA.md](./FASE1-SISTEMA.md) | ¿Qué tokens, medidas y movimiento son verdad? | entregada |
| 2 | [FASE2-ESQUELETO.md](./FASE2-ESQUELETO.md) | ¿Cómo se apila la pantalla y qué chrome es permanente? | entregada |
| 3 | [FASE3-TARJETA.md](./FASE3-TARJETA.md) | ¿Qué hay en la cara frontal, en qué orden y en qué estados? | entregada |
| 4 | [FASE4-CARAS.md](./FASE4-CARAS.md) | ¿Qué hay detrás y encima de la tarjeta? | entregada |
| 5 | [FASE5-PREPARACION.md](./FASE5-PREPARACION.md) | ¿Cómo se ve movilidad, aproximación y cardio? | entregada |
| 6 | [FASE6-DESCANSO.md](./FASE6-DESCANSO.md) | ¿Cómo se ve el descanso, el tiempo extra y el feedback? | entregada |
| 7 | [FASE7-HOJAS.md](./FASE7-HOJAS.md) | ¿Qué hojas existen, qué contienen y cómo se cierran? | entregada |
| 8 | [FASE8-MESA-Y-CONTRATO.md](./FASE8-MESA-Y-CONTRATO.md) | ¿La mesa es viable y cómo se implementa sin inventar? | entregada |

## Prototipo

- Enlace de revisión: [artifacts.commandcode.ai/u81aw0eslollscyw](https://artifacts.commandcode.ai/u81aw0eslollscyw) (privado; caduca 7 días después de la última publicación).
- `prototipo.html` — pieza única, sin dependencias locales, para revisar y compartir.
- `proto/` — fuente: `index.html`, `kpkn.css`, `scenes.js`, `app.js` y `build_standalone.py`.

El prototipo tiene tres capas de lectura:

1. **Fidelidad** — cada estado lleva una insignia: `Exacto`, `Derivado` o `Brecha`.
2. **Anotaciones** — pines sobre el teléfono con la medida y la línea de código que la justifica.
3. **Especificación** — panel derecho con propósito, medidas, copy literal, estados y criterios de aceptación.

## Reglas de fidelidad

| Insignia | Significado | Uso |
|---|---|---|
| **Exacto** | El estado existe en el worktree `post-rings` y sus valores salen de código Compose. | Se puede implementar tal cual. |
| **Derivado** | No es una pantalla nueva: se compone con piezas existentes, como la mesa. | Requiere aprobación explícita; no introduce acciones ni datos nuevos. |
| **Brecha** | El código no lo tiene, lo tiene como stub o está muerto. | **No se dibuja como si existiera.** Se documenta la brecha y su sustituto real. |

## Lo que este programa no hace

- No inventa acciones que no estén en la Fase 0 o en las hojas reales.
- No cambia la altura de la tarjeta ni introduce estados que la alarguen.
- No usa un color, radio o desenfoque que no exista en los tokens.
- No convierte la mesa en una pantalla nueva.
- No aprueba el modo dios: en el worktree es un stub (`GodModeChrome.kt:6-25`), y sus acciones viven en `SeriesOptionsSheet`.

## Criterio de aprobación

Se puede decir «OK, llevemos esto a KPKN» cuando las siguientes respuestas sean sí:

1. ¿Cada estado del prototipo tiene una referencia al código o está marcado como derivado/brecha?
2. ¿La tarjeta conserva 470 dp de alto en todos los estados y el registro nunca la empuja?
3. ¿Los siete modos de serie (REPS, AMRAP, calibrador, RM, solo RPE, tiempo, distancia, personalizado) se distinguen sin leer el subtítulo?
4. ¿La mesa usa solo filas, etiquetas y acciones que ya existen?
5. ¿Las hojas destructivas confirman y las que se pueden deshacer ofrecen deshacer?
6. ¿Los datos sin referencia muestran «—» y nunca un valor inventado?
7. ¿La implementación cabe en los archivos existentes sin crear una segunda arquitectura?

## Qué falta decidir (Fase 8, §6)

- Si la mesa entra en esta entrega o en una segunda.
- Si el relator se queda con 107 dp o se recoloca al aprobar la mesa.
- Si el modo dios se retira del todo o se implementa de verdad.
- Si el «cluster set: próximamente» se oculta hasta que exista.
- Si las etiquetas sub-tag necesitan un límite visible.
- Si el descanso adaptativo se explica con una línea o con una hoja.
