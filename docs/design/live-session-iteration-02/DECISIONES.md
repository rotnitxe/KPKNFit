# KPKN · Sesión en vivo · Refinamiento 02 — Matriz de decisiones

Fecha: 2026-10-26. Base de diseño: `PLAN-PROVISIONAL.md` de la iteración 02.
Estado: decisiones de etapa 0 cerradas con el usuario antes de tocar Android.

## Fuente de diseño

| Artefacto | Ubicación canónica | SHA-256 |
|---|---|---|
| Prototipos interactivos | `C:/Users/valen/Desktop/KPKN-sesion-iteracion-02/prototipos.html` | `c2d59de0e3dd082a6d314233987d2f93ee827e904c7e2496db13e1efe503be64` |
| Plan | `C:/Users/valen/Desktop/KPKN-sesion-iteracion-02/PLAN-PROVISIONAL.md` | `6c3a3370de14e2b3ae92932acdb703483ad95059a72553ae02c1493cb0be3121` |
| QA web | `C:/Users/valen/Desktop/KPKN-sesion-iteracion-02/qa-v2.json` | `523c10d50d7112f3a30f6a8d4c27f9c448067de88597207558f378fb6eafdace` |
| Copia en repositorio | `docs/design/live-session-review-20261025/` | idéntica a la carpeta de escritorio |

Capturas de referencia: `capturas-v2/` (23 capturas y `manifest.json`). La copia del
repositorio está sin seguimiento; no se modifica ni se presenta como código nativo.

## Decisiones cerradas

| # | Decisión | Elección | Evidencia |
|---|---|---|---|
| D1 | Dirección de sesión | **Tarjetas** es la vista inicial. **Lista** queda aprobada y accesible desde el menú `⋯` de la sesión. **Foco** queda descartada. | Prototipo `view=cards` y `view=list`; inspector de comparación. |
| D2 | Roadmap en Lista | **Lista no lleva roadmap inferior.** El roadmap gris glass flotante se conserva solo en Tarjetas. | `PLAN-PROVISIONAL.md` contratos «Roadmap» y «Lista»; captura `06-superserie.png`. |
| D3 | Relator | **Se retira la franja permanente de narración genérica.** Se conservan asistencia contextual, sugerencias desplegables, conversación de voz, acciones y datos que las sustentan. | `PLAN-PROVISIONAL.md` «Relator: recomendación, pendiente de decisión»; escena `relator-context`. |
| D4 | Guía hablada | **Guía completa antes de grabar; durante la grabación solo mensajes urgentes**, deduplicados y con frecuencia limitada. No se declara guía continua. | `PLAN-PROVISIONAL.md` «Encuadre y control de grabación»; escena `framing`. |
| D5 | Selector de vistas | El cambio Tarjetas/Lista vive en el **menú de sesión `⋯`**; el header queda limpio. Ambas vistas comparten el mismo borrador. | Decisión del usuario 2026-10-26; prototipo comparte `state` entre vistas. |
| D6 | Acordeón de Lista | Un solo acordeón activo. La serie de turno se abre inicialmente; abrir otra cierra la anterior. El botón pequeño de la fila abre o valida el registro. | Contrato «Lista» del plan; capturas `01-tarjetas-lista.png` y `06-superserie.png`. |
| D7 | Descanso | Dos presentaciones del mismo estado: **tarjeta estándar** y **pantalla completa**. No hay isla como tercera presentación. | Contrato «Descanso: dos presentaciones del mismo estado». |
| D8 | Minijuegos | Propio estado; reiniciar el juego no reinicia el descanso; pausar el descanso congela el juego; el reloj mantiene prioridad al llegar a cero. | Tabla de minijuegos del plan; capturas `21`–`23`. |
| D9 | Identidad visual | Negro, carbón, crema y grises; Syne e Inter reales de KPKN; sin textos amarillos ni apariencia Material/Compose por defecto. | Contrato «Identidad»; capturas de la galería. |
| D10 | Persistencia | El rediseño no justifica una migración Room. Se conserva Room 36 y las historias publicadas. | `AGENTS.md`; `KpknDatabase.kt:69`. |

## Contratos de marco y densidad

- Marco fijo por viewport, no por contenido: 312 × 394 en 360 × 740; 345 × 470 en
  393 × 852; aproximadamente 363 × 488 en 412 × 1007.
- Sin scroll interno ni campos ocultos detrás del pie en ninguna variante de tarjeta.
- Lo esencial permanece visible. Notas, configuración y detalle largo se abren en
  hojas externas. Las aproximaciones adicionales se paginan dentro del marco.
- Con teclado, la edición extensa usa una superficie externa; el marco no crece.
- Áreas táctiles, foco, etiquetas, movimiento reducido y coste del blur se validan
  en QA nativo, no se deducen del prototipo web.

## Límites heredados del artefacto

El prototipo es HTML autocontenido con datos sintéticos. No demuestra captura real,
reconocimiento de voz, precisión biomecánica, persistencia ni rendimiento nativo.
`VERIFICACION.md` describe 372 combinaciones de tarjetas, 124 de Lista y 42 de
superficies completas; esa evidencia es web y no sustituye la QA Android.

## Decisiones aún no cerradas (no bloquean el arranque)

No quedan dudas de etapa 0 para empezar. Las siguientes propuestas del plan siguen
abiertas a revisión durante la implementación y no se declaran aprobadas:

- Mecánicas exactas de los tres minijuegos más allá de las reglas de la tabla.
- Versión final de la política de guía hablada después de medir FramingCheckController.
- Persistencia de historial de voz: no se añade sin contrato y retención definidos.
