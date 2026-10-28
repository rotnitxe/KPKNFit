# Refinamiento 02 · Decisiones del usuario (2026-10-27)

Respuestas literales del usuario a las dudas de inicio. Precedencia: estas decisiones > plan > prototipo > código.

| # | Tema | Decisión |
|---|---|---|
| 1 | Alto del marco en el Z Flip (360×880) | Tarjeta 312×394 justo bajo el título; roadmap anclado abajo como en el prototipo; el hueco sobrante queda entre tarjeta y roadmap. |
| 2 | Botón de registro en Tarjetas | «Registrar serie» / «Terminar descanso» viven en el roadmap flotante bajo la tarjeta, como las capturas 01 y 02. La tarjeta no lleva botón propio. |
| 3 | Minijuegos | «Acá no me gustó el prototipo. Haz tu propia propuesta mejorada, mejorando la calidad y jugabilidad de los juegos.» Primero se presenta la propuesta (mecánicas y estilo de los 3 juegos) para aprobación; se implementa tras el visto bueno. |
| 4 | Readiness y cierre | Manda lo nativo de `b3a057e13`: no se reestilan; solo se verifica estructura, campos y contratos en el dispositivo. |
| 5 | Lista con roadmap | No se implementa. Solo existe Lista sin roadmap. |
| 6 | Elección Tarjetas/Lista | Preferencia global: la última vista elegida se guarda y se usa en las sesiones siguientes (Tarjetas solo como valor inicial). |
| 7 | Evidencia 393×852 y 412×1007 | Render sheets Robolectric (`GraphicsMode.NATIVE`). El Flip cubre 360 en dispositivo real. |
| 8 | Accesibilidad nativa | Yo cambio font_scale 1.3 / TalkBack por adb en el Flip solo durante la prueba y restauro los valores originales. |
| 9 | Datos de prueba | Terminar/descartar la sesión en curso de KPKN Dev y crear dentro de Dev un programa de prueba con todos los tipos de ejercicio. Sin borrar datos ni desinstalar. |
| 10 | Pruebas físicas (encuadre, audio) | Bloque guiado al final de Encuadre y Voz, con guion de pasos, junto al usuario. |
| 11 | Quién escribe | Claude escribe la UI con paridad 1:1; subagentes solo para tests y tareas mecánicas. |
| 12 | Tope de QA | 3 ciclos build+instalación+captura por pantalla; si no coincide, se muestra nativo vs. referencia y decide el usuario. |
| 13a | Concurrencia | El área de sesión en vivo es exclusiva de esta tarea. El WIP ajeno (nutrición, etc.) no se toca. |
| 13b | Commits | Commit por área terminada con PASS, solo de rutas propias, en master, sin push. |

## Ampliación del alcance (mensaje del usuario, 2026-10-27)

| # | Tema | Decisión |
|---|---|---|
| 14 | Modo Voz | Rediseñar la vista: deja de ser una vista aparte; pasa a ser un elemento visual por encima de la vista por defecto, con formato animado/dinámico de los registros. Prototipo primero. |
| 15 | Cardio | Cardio entra en el alcance con un apartado dedicado y diferenciador. Entregar prototipos basados en el rediseño cardio v2 (`C:/kw/u5/docs/design/cardio-v2/live.html`, `transiciones.html`), incluida la transición «Ahora vamos con cardio». |
| 15a | Identidad de cardio | Identidad Ref02 (negro/carbón/crema, números en crema); el color queda solo en las zonas (cinta, perfil de bloques y halo tenue de la zona activa). |
| 15b | Ubicación | Espacio propio a pantalla completa, con transición deslizable en sesiones mixtas; conserva la cápsula del header de Ref02. |
| 15c | Código de `C:/kw/u5` | Decidir tras aprobar los prototipos: auditar u5 y proponer qué portar a master y qué rehacer. |

| 16 | Prototipos iteración 03 | **Aprobados** por el usuario el 2026-10-27 (`docs/design/live-session-iteration-03/prototipos.html`): Cardio (16 estados), Modo Voz como capa (10) y Juegos (6). Pasan a ser referencia de implementación junto a `capturas-v2/`. Queda abierto: «Seguir jugando» como tiempo extra y qué portar de `C:/kw/u5`. |

| 17 | Alcance real (2026-10-27) | «TODAS LAS INSTANCIAS DEL PROTOTIPO SE TIENEN QUE APLICAR, aunque eso implique cambiar aspectos de la app»: hojas y menús (parciales/ayuda, tipo de carga, esfuerzo, historial, ⋯, tiempo, etiquetas, opciones de serie y ejercicio, configuración…) se rehacen con la hoja del prototipo. El header no lleva fondo: solo la cápsula de vidrio. |
| 18 | Más allá del prototipo | «Rediseña lo que hay que rediseñar si el prototipo olvidó más pantallas. El objetivo: simplificar, accesibilizar, limpiar y quitar densidad por decisiones interactivas y más visuales. Sin perder potencia.» Autoriza decidir el diseño de las pantallas no cubiertas, con ese criterio; se informa la lista al final. |
| 19 | Teléfono | «Si mi Z Flip registra actividad, te autorizo a interrumpirla para tus pruebas.» |

## Detalles menores aceptados por defecto

- A 412 de ancho el marco mide 364 dp (`412 − 48`), no 363.
- Son 23 capturas PNG + `manifest.json`.
- `CLAUDE.md` dice Room v30; la real es v37. No se modifica.
- Relator sin franja y guía hablada «antes de grabar + urgentes» quedan cerrados por el prompt.
- La `matriz.md` previa se rehace auditando desde cero.

| 20 | Isla del header (2026-10-27) | «La píldora superior con el título y el timer no respeta el diseño del artefacto». Se rehace con las medidas de `.phone > .island`: 60 dp (53 en pantallas bajas), radio 30, relleno 15/8, degradado 135° `#3A3A3AB0 → #202020A8`, borde `#FFFFFF21`, reflejo interior y sombra; título 13/600 y subtítulo 10 en gris tenue, una línea cada uno; cronómetro `.timer-pill` de 40 dp (radio 19, blanco al 3 %, icono de cronómetro de 15 dp y el tiempo transcurrido en tinta). |

## Decisiones autónomas de diseño (punto 18), para el informe final

| Pantalla / pieza | Decisión | Por qué |
|---|---|---|
| Cronómetro de la isla | Muestra solo el tiempo transcurrido. Ya no muestra «/ 98′» en rojo ni el anillo de progreso. Ante un aviso de ritmo, solo se tiñe el icono. El objetivo, el ritmo y los avisos viven en «Tiempo de sesión». | El prototipo solo muestra el reloj. El objetivo ocupaba ~100 dp y truncaba el título y el subtítulo. |
| Línea de referencia de la tarjeta | «Anterior 100 × 8 · RIR 2» con el enlace subrayado «Historial», no un icono. | Es lo que hace `.reference-line` del prototipo. |
| Recorrido de la sesión | Filas numeradas en Syne, «x/n series» y, bajo el ejercicio en curso, sus series en chips para ir a ellas. Añade «Reordenar pendientes» y «Añadir ejercicio». El anillo y las métricas (registros, tonelaje, RIR medio, gasto, ritmo) pasan a «Resumen», al final. | Prototipo `route`. Las métricas se conservan sin abrir la hoja con ellas. |
| Recorrido: opciones | «⋯» en cada fila, en lugar del ›, abre las opciones del ejercicio o de la superserie. | Es el patrón de la vista Lista. Sin él se perdía el acceso a las opciones. |
| Configurar serie | Hoja nueva con pestañas Objetivo, Carga y Detalle, más «Aplicar a» (esta serie o las pendientes del ejercicio). Si hay cambios sin guardar, pide confirmación antes de cerrar. El formato, la lateralidad y la carga inteligente quedan en «Todas las series del ejercicio», el editor completo. | Prototipo `set-config`. Antes «Configurar serie» abría el editor de todo el ejercicio. |
| Alertas de la sesión | `LiveAlert` (`.v2-alert`): tarjeta centrada con icono, título en Syne 26, acción principal ancha y secundaria en texto. Cubre: actualizar serie, quitar de la sesión, cambio de grupo, cambios sin guardar, terminar la técnica, confirmar cardio, molestia reportada, micrófono, sesión en curso y errores de inicio o cancelación. | Prototipo `alert`. |
| Sesión en curso | La acción principal es «Reanudar el que está en curso»; «Descartar y empezar este» pasa a secundaria, en rojo. | Prototipo `conflict`: lo seguro va primero. |
| Salir de la sesión | Hoja con «Continuar entrenando» como acción principal y tres filas: «Terminar hasta acá», «Pausar y salir» y «Abandonar sin guardar» (en rojo). | No estaba en el prototipo. Se pasa de cuatro botones de Material a filas que explican cada salida. |
| Alcance de los cambios | Reemplazo y cambios de estructura: «Solo esta vez», «Esta semana» o «Todo el bloque» en filas con icono, no en píldoras. | No estaba en el prototipo. Mismo lenguaje que el menú. |
| Superseries | Crear o agregar, y configurar rondas y descansos, en hojas con filas de formulario. Los ejercicios se eligen en filas con ✓ o +. | Prototipo `superset-settings`. |
| Acortar sesión | Actual y propuesta en cifras grandes, el cambio por ejercicio en notas y «Aplicar propuesta». | Prototipo `shorten`. |
| Hojas antiguas | `WorkoutDrawer` y `HeaderSheetFrame`/`HeaderSheetAction` se dibujan como la hoja del prototipo: asa, título en Syne, panel, velo y pie con filete. Las `KpknSheet` restantes de la sesión toman el panel y el velo. | Elimina el vidrio antiguo que rompía el prototipo, en todas las hojas a la vez. |
| AMRAP, nuevo setup, nota del ejercicio, técnica para lo pendiente | Hojas del kit, no Material (`OutlinedTextField`, `FilterChip`). | Coherencia con el prototipo. |

| 21 | Hueco tarjeta–roadmap (2026-10-27) | Variante **B · cola de tarjetas**: la A se descartó porque es relleno con datos que ya se ven de otra forma. Las dos tarjetas siguientes asoman apiladas bajo la actual, escalonadas 26 y 14 dp, más pequeñas, oscurecidas y con su serie rotulada en el borde. |
| 22 | Animación del gesto | Se cambia por una compatible con la pila: al deslizar hacia la siguiente, la actual sale hacia la izquierda inclinándose y desvaneciéndose y la siguiente sube de la pila; la anterior vuelve desde la izquierda por encima. El gesto (deslizar en horizontal) y la lógica del carrusel no cambian. |
| 23 | Roadmap | Raíl de series en lugar de la pista MOV/APR/SER/REST. Tocar un punto va a él, arrastrar recorre los puntos con un toque háptico cada uno, y la pulsación larga abre las opciones de la serie. Al registrar: barrido verde del botón, el punto se rellena y traza su ✓, la línea avanza, el contador rueda y crece el anillo de la sesión. El descanso es una píldora que se vacía. El roadmap pasa de 118 a 138 dp. |
| 24 | Línea bajo el nombre | Se omite todo dato que el nombre ya dice («Barra · Alta» en «Sentadilla Trasera con Barra Alta») y el «Etiquetas ⌄» sin etiquetas activas; si no queda nada, la línea no ocupa espacio. Las etiquetas siguen en Opciones del ejercicio. |
| 25 | Sin más prototipos | «Integra todo eso, no me mandes otros prototipos, ponte a implementar.» |
