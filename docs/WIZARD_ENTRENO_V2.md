# Wizard de alta · módulo de Entreno v2

Rediseño del bloque **Entreno** del wizard de alta (`screens/onboarding/`, `domain/onboarding/`, `domain/training/`).
Rama de trabajo: `feat/wizard-entreno-v2`. Este documento es el **contrato** de la ola: lo que ve la persona, qué
datos escribe cada paso, qué lee el motor y qué se elimina. Los briefs de los agentes apuntan aquí.

> Regla de oro del usuario: **nada de botones vacíos ni de UI de relleno**. Toda opción nueva escribe un dato real
> del borrador y ese dato cambia lo que genera el motor (programas, sesiones, calentamientos, activación).

## 0. Principios de diseño (no negociables)

1. **Símbolos e ilustraciones animadas, nunca tarjetas.** Las opciones visuales (lugares, material, músculos,
   objetivos) son dibujos que se tocan directamente sobre la página negra continua. La selección se expresa con el
   propio dibujo: se enciende, se mueve, gana color de módulo y un trazo de «hecho». Sin cajas, sin bordes con
   brillo, sin radios de lista (ver `docs/WIZARD_PAGINA_LARGA.md`, reglas 1–9).
2. **Estética KPKN / Liquid Glass**, no «Android Compose genérico»: Syne en lo importante, Inter en lectura,
   tinta cálida `#F2EEE6` sobre negro, acentos de módulo (músculo `#F49A6E`, columna `#8FB2FF`, energía `#F7CF73`,
   mente `#C9B8FF`, ok `#43D18C`). Vidrio real solo donde ya existe (cabecera, botón, overlays con desenfoque).
3. **Menos preguntas, mejores defaults.** Lo que se puede deducir no se pregunta; lo que se pregunta se responde
   con un toque. Todo es editable después (el wizard sugiere, la persona confirma).
4. **Movimiento con sentido**: una animación explica la opción (un dominada en el parque, una mancuerna que sube)
   y se respeta «reducir movimiento» (`wizardReducedMotion()` → cuadro final estático).
5. **Texto mínimo**: títulos de pregunta ≤ 44 caracteres, subtítulos ≤ 100 (`SetupStepCopyRulesTest`). Castellano
   neutro y sin género gramatical cuando se pueda («¿Qué día llegas con más energía?»).

## 1. Flujo nuevo del bloque Entreno

| # | Paso (`SetupStepId`) | Pregunta | Control (símbolos) | Se salta cuando |
|---|---|---|---|---|
| 1 | `EXPERIENCE` | ¿Cuánta experiencia tienes? | opciones (sin cambios) | — |
| 2 | `EQUIPMENT` | ¿Dónde entrenas? | 3 escenas animadas: **Gimnasio · En casa · En espacios públicos** (multi) | — |
| 3 | `AVAILABILITY` | ¿Con qué material entrenas? | cuadrícula de símbolos animados de implementos | — (gimnasio: todo lo habitual ya viene marcado) |
| 4 | `GOAL` | ¿Cuál es tu objetivo? | generales (3) + específicos (7, condicionados al material) | — |
| 5 | `FRESH_DAY` *(nuevo)* | ¿Qué día llegas con más energía? | 7 días en fila, uno encendido | — |
| 6 | `WEEKDAYS` | ¿Qué días puedes entrenar? | calendario semanal (1–7 días) con inicio de semana | — |
| 7 | `SESSION_TIME` | ¿Cuánto tiempo tienes por sesión? | dial de reloj (20–180 min) | — |
| 8 | `CARDIO_TYPE`, `CARDIO_TIME` | cardio | (sin cambios de fondo) | el objetivo no incluye cardio |
| 9 | `VOLUME_TECHNIQUE` | ¿Cómo sientes tu técnica? | opciones | **experiencia = «Estoy empezando»** |
| 10 | `VOLUME_CONSISTENCY`, `VOLUME_STRENGTH`, `VOLUME_MOBILITY` | calibración | opciones | — |
| 11 | `CAPABILITIES` *(nuevo)* | ¿Qué ejercicios ya te salen? | 3–4 símbolos con nivel (nada / pocos / varios) | el material no hace falta para peso corporal y no es calistenia ni novato |
| 12 | `PRIORITIES` | ¿Qué músculos quieres mejorar más? | símbolos de músculo (los populares); «Omitir» claro arriba | — (omitible) |
| 13 | `TRAINING_MAX` / `TRAINING_MARKS` | ¿Conoces tus marcas? | regla deslizante kg/lb por levantamiento | novato, o disciplina que no usa marcas |
| 14 | `PLAN` | tu programa | overlay animado «preparando…» → revelado / carrusel | — |
| 15 | `WEEK_LAYOUT` *(nuevo)* | Así queda tu semana | sesiones arrastrables entre días + «adaptar a un reparto» | — |

Se **eliminan de la ruta** (el enum se conserva para leer borradores viejos): `DAYS`, `STYLE`, `SPLIT`,
`AUTOREGULATION`, `AUTOREGULATION_CONFIRM`, `WARMUPS`, `TRAINING_REVIEW`.

## 2. Detalle por paso

*(se completa a medida que cada paquete aterriza)*
