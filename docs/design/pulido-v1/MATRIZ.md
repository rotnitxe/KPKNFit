# Pulido v1 · matriz de los puntos del usuario (29-oct-2026)

Worktree `C:\kw\pulido`, rama `feat/pulido-v1` (desde master c9d3b2801). Lienzo: `docs/design/pulido-v1/` (`node build.cjs`). Reglas de la ola: ver `BRIEF_COMUN.md`
(prototipos) e `briefs/impl-COMUN.md` (código). NADA de Gradle hasta que el usuario lo pida; los rediseños se implementan 1:1 solo tras aprobar los prototipos.

Tipos: **D** = arreglo directo (sin prototipo; el usuario no pidió aprobarlo) · **P** = prototipo para aprobar antes de implementar · **D+P** = arreglo directo ya y diseño en el lienzo.

| # | Lo que pidió | Tipo | Paquete (prefijo) / agente |
|---|---|---|---|
| 1 | Quitar la pantalla «Cargando sesión de entrenamiento» | D | K1 `impl-workout-screen` |
| 2 | Analizador de técnica sin scroll, datos sobre el vídeo, «analizando» en primer plano | P | `tecnica` (tc) |
| 3 | «Aplicar plantilla o método» como catálogo | P | `catalogo` (ct) |
| 3b | Series del editor: carga «Externa» + menú, reps/rangos, intensidad integrada, descanso, acciones | P | `editor` (ed) |
| 4 | Asistente de sesión: Métricas, Sugerencias, Plantillas y Reglas | P | `asistente` (as) |
| 5 | Miniaturas de «Añadir cardio» que representen cada tipo | P (+ código al aprobar) | `cardio-editor` (ce) |
| 6 | Tarjeta de configuración de cardio | P | `cardio-editor` (ce) |
| 7 | «Avanzado» del cardio: quitar lo que no se usa | auditoría ya; código al aprobar | `cardio-editor` (ce) |
| 8 | Selector de duración | P | `cardio-editor` (ce) |
| 9 | Pulsación larga y ficha propias del cardio | P | `cardio-editor` (ce) |
| 10 | Borrar por deslizamiento y arrastrar para ordenar | P | `editor` (ed) |
| 11 | Tarjetas de cardio y «Añadir cardio» dentro de la tarjeta | P | `cardio-editor` (ce) |
| 12 | Píldora superior del editor en vidrio | D (K4) + P | K4 `impl-glass-fab` + `editor` (ed) |
| 12b | Barra inferior (Añadir / Asistente / Reglas / minutos) | P | `editor` (ed) |
| 13 | Reps en carrusel + esfuerzo que se enciende hacia el fallo | P | `vivo-tarjeta` (lv) |
| 14 | Roadmap inferior rediseñado a la misma altura | P | `vivo-tarjeta` (lv) |
| 15 | ROM conectado (editor → tarjeta con slider → serie) | D (K2) + P | K2 `impl-rom` + `vivo-tarjeta` (lv) |
| 16 | Cronómetro de verdad + temporizador | P | `vivo-resto` (lx) |
| 17 | «Expandir» en el modo visual de la tarjeta | D | K2 `impl-rom` |
| 18 | Hoja de sesión completada sin scroll | P | `vivo-resto` (lx) |
| 19 | Modo Lista: desvanecido en los extremos y píldora flotante | D (K1) + P | K1 + `vivo-resto` (lx) |
| 20 | Dictado de comida que funcione | D (K3) + P (estados) | K3 `impl-voz-nutricion` + `nutricion` (nc) |
| 21 | Nueva medición: botón flotante, esquinas, diseño de la hoja | D (K4) + P | K4 + `nutricion` (nc) |
| 22 | Ajustes: solo lo estrictamente necesario | P | `ajustes` (aj) |
| 23 | Reparto: descripciones, vista previa interactiva y motor de migración | P (+ motor al aprobar) | `reparto` (rp) |
| 24 | Suplementos: más, con registro rápido (también desde Inicio) | P | `suplementos` (sp) |
| 25 | Cardio en vivo: rediseño total | P | `cardio-vivo` (cv) |
| 26 | «Al inicio / Al final» del editor | P | `cardio-editor` (ce) |

## Estado (se actualiza a mano)
- 29-oct 22:40: los 12 prototipos entregados y revisados con capturas (318 escenas, 0 problemas en `check`); lienzo publicado: https://claude.ai/artifact/KQ9mtYKTbKPNAw83mikN4V
- Los 4 arreglos de código (K1 carga + Lista, K2 ROM + Expandir, K3 dictado de comida, K4 vidrio del editor + botón de Cuerpo) escritos, releídos por un revisor de compilación (1 error corregido) y sin compilar; sin commit.
- Siguiente: el usuario aprueba o corrige el lienzo; después, implementar 1:1 por paquetes (lo que el lienzo no tiene, se elimina), compilar y probar cuando él lo pida.
