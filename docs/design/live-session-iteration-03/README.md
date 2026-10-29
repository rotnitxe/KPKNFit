# KPKN · Sesión en vivo · Iteración 03 (propuesta para revisión)

27 de octubre de 2026. Amplía el Refinamiento 02 con tres áreas que el usuario pidió revisar antes de implementar:

1. **Cardio con espacio propio** y transiciones entre fuerza y cardio.
2. **Modo Voz como capa** sobre la vista por defecto, con registros animados.
3. **Minijuegos de descanso** en versión propia (el usuario descartó la mecánica del prototipo 02).

Abre `prototipos.html` (autocontenido, con las fuentes reales de KPKN). Para recomponerlo: `node build.cjs`. Fuentes en `src/`.

URL directas: `prototipos.html?tab=cardio&scene=c-trans-in`, `?tab=voz&scene=v-record`, `?tab=juegos&scene=g-picker`. Selectores de pantalla (360 × 740, 393 × 852, 412 × 1007), texto 100 % / 130 % y movimiento reducido.

## Decisiones de partida (del usuario, 27-oct)

- Cardio con **identidad Ref02**: negro, carbón y crema; números en crema. El color queda **solo en las zonas**: cinta, perfil de bloques y la corona tenue del anillo.
- Cardio vive en un **espacio propio a pantalla completa**, con transición deslizable en sesiones mixtas, y conserva la cápsula del header.
- Base: el rediseño cardio v2 del 16-oct (`C:/kw/u5/docs/design/cardio-v2/live.html` y `transiciones.html`). Ese código sigue en `C:/kw/u5`, sin integrar en master; qué se porta se decide después de aprobar estos prototipos.
- Modo Voz deja de ser una vista aparte: es un elemento visual por encima de la vista por defecto, con registros animados y dinámicos.
- Juegos: propuesta propia que mejore calidad y jugabilidad; se implementa solo tras aprobación.

## Qué contiene

| Área | Estados |
|---|---|
| Cardio · transiciones | Descanso que anuncia cardio; «Ahora vamos con cardio» (coreografía + deslizar); «Ahora vamos con fuerza». |
| Cardio · espacio | Cuenta 3-2-1; trabajo en zona; por encima de la zona; sin pulso (guía RPE); recuperación; exterior por distancia con GPS; páginas Plan y Resumen; pausa; bloqueo con mantener; cardio terminado con RPE real obligatorio. |
| Cardio · inicio | Sesión solo cardio (sin transición); cardio en la vista Lista. |
| Modo Voz | Cápsula escuchando; frase → valores que vuelan a la tarjeta y registro; captura ambigua por revisar; registros expandidos; editar captura; pausa; sobre Lista; comandos en descanso; comandos de vídeo con estados del grabador; KPKN habla (guía). |
| Juegos | Selector; Salta las mancuernas; Entre las pesas; Escuadrón retro; descanso en pausa congela el juego; el descanso llega a cero. |

## Decisiones abiertas (marcadas en las notas del prototipo)

- Juegos: «Seguir jugando» tras acabar el descanso cuenta como tiempo extra visible. Propuesta, no decisión.
- Cardio: la página Resumen muestra solo datos medidos; la energía siempre con «≈».

## Límites

Datos sintéticos. Voz, GPS, pulso y grabación simulados. No sustituye QA nativo (fontScale, TalkBack, insets, coste del blur).
