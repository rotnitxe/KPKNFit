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

## Correcciones del usuario en la QA del Flip5 (28/29-oct-2026)

Sustituyen a las decisiones anteriores donde chocan.

| # | Tema | Decisión |
|---|---|---|
| 26 | Juegos en la tarjeta minimizada | El juego ocupa casi toda la tarjeta. El reloj del descanso es una fila fina (‹, anillo, tiempo, −15 · pausa · +15), sin la píldora que se colapsaba en vertical. En la tarjeta, el selector enseña nombre y récord (la descripción la lee el lector de pantalla). A pantalla completa sigue la píldora `.gtop` de la iteración 03. |
| 27 | Lista | Sin el título grande del ejercicio en curso (repetía el primero y se quedaba fijo); el micrófono y las etiquetas siguen. Textos dos puntos mayores (la Lista se leía como una planilla). Fundido a negro arriba y abajo. La fila activa y el acordeón que abre solo siguen el paso activo de la sesión: durante la preparación no se abre ninguna serie efectiva. |
| 28 | Hojas, avisos y píldoras | Gris oscuro plano (#1B1B1D) con filete tenue y sombra REAL de elevación de la plataforma; nada de vidrio ni desenfoque (sustituye el desenfoque de la cabecera). Aplica a KpknSheet (y por tanto a todas las LiveSheet), KpknAlertDialog, LiveAlert, la cápsula de la cabecera, el roadmap y la cápsula de voz. |
| 29 | «Antes de entrenar» | Rehecha con el kit de hojas y el `readiness` del prototipo: ✕, escalas con cifra en Syne, molestia muscular en recuadros que se tocan, molestias previas en píldoras con buscador, voz y «Confirmar y continuar»; mide lo que mide su contenido. Se retiró «Notas y disciplina» (abría un velo amarillo). Anula «readiness nativo intocable». |
| 30 | Textos de las hojas | Un punto por encima del prototipo (cuerpo 14, secundario 13, rótulos 11). |
| 31 | Raíl de series | Ventana de 7 nodos (6 + ›) que sigue a la serie en curso; la línea llega al borde cuando quedan puntos fuera; puntos algo mayores (radio 10, número 9 sp). |
| 32 | Tarjeta | Título del ejercicio a 23 sp. La tarjeta y su pila se centran en el hueco entre el título y el roadmap (anula la decisión 1). Cada tarjeta de la pila proyecta su sombra real. |
| 33 | Pase entre tarjetas | La que sale se desvanece a mitad del gesto, sin inclinarse ni quedar translúcida encima de la siguiente (anula la animación de la decisión 22); asentado de 240 ms y pases programáticos de 260 ms. |
| 34 | Acortar sesión | Es el sistema ultrarrápido sin nombrarlo: propone por tipo de ejercicio drop-set, rest-pause o menos series, descansos más cortos y, en cardio, menos tiempo o quitarlo; mínima configuración manual. |

## Lista: correcciones del usuario en el Flip5 (29-oct-2026)

Sustituyen al prototipo (capturas 01, 07 y 08 de la Lista) y a la decisión 27 donde chocan. Numeración propia (L) para no pisar las de otros carriles.

| # | Tema | Decisión |
|---|---|---|
| L1 | Cabecera | Fuera por completo el bloque de contexto de arriba («Barra» + micrófono y su hueco). Anula lo que la decisión 27 conservaba (micrófono y etiquetas en la Lista); siguen en la isla y en Opciones del ejercicio. |
| L2 | Jerarquía | El nombre de cada ejercicio es lo principal: Syne 17 (el resto, 11–15), con el avance «Movilidad 2/2 · Aprox. 1/3 · Series 1/3» debajo, ✓ al terminar, pliegue y ⋯. |
| L3 | Preparación dentro del ejercicio | Se quita el bloque «PREPARACIÓN» de arriba (duplicaba A1–A3). Cada ejercicio agrupa, en orden: MOVILIDAD, APROXIMACIONES (con «Trabajo · 82,5 kg») y SERIES EFECTIVAS, cada uno con su avance. En superserie, la preparación de cada miembro va al principio del bloque, rotulada A1/A2. |
| L4 | Unidades y coma | Toda carga dice kg: «32,5 kg × 8», también en las series efectivas («82,5 kg × 8») y en la referencia («Anterior · 80 kg × 8 · RIR 2»). Coma decimal es-CL en todo lo que se lee y se escribe en la Lista (los campos aceptan coma o punto). El porcentaje se normaliza (0,4 o 40 → «40 %»). |
| L5 | Registrar aproximaciones | Tocar la fila abre su editor con los kg (por defecto, la carga sugerida por `getWarmupSuggestedWeight`, ya redondeada al material, la misma de la tarjeta y la voz) y las reps; registra con `completeWarmupStep(…, usedWeightKg, reportedReps)`. Ya hecha, el editor muestra lo registrado y «Actualizar» corrige con `reportWarmupStep`; «Marcar pendiente» la desmarca. El ✓ de una pendiente registra en el acto con lo sugerido. Sin carga de trabajo no se inventa: «— kg» y el campo vacío. |
| L6 | Lo registrado se queda | Movilidad, aproximaciones y series registradas siguen en su sitio, atenuadas y con ✓, y se pueden reabrir y editar. |
| L7 | Transiciones | Aparecer, registrar, plegar y desplegar: `animateItem` (fundido 220/160 ms y recolocación 280 ms), `animateContentSize` y `liveEnter/liveExit/liveContentSwap` de LiveMotion. Con el movimiento reducido, corte inmediato. |
| L8 | Descanso en la Lista | El de aproximación o movilidad se ve desplegado (la tarjeta del descanso, con «Minimizar») o minimizado (fila compacta con reloj, «+15 s», «Terminar» y desplegar), tras la fila que lo abrió. El de serie efectiva, minimizado, queda en la Lista tras la serie recién registrada (antes desaparecía); desplegado sigue siendo la pantalla completa. «Descanso cumplido» minimizado, igual. Usa el mismo `isRestMinimized` que la tarjeta y Atrás. |
| L9 | Ejercicio terminado | Al registrar la última serie, ese ejercicio se pliega y se despliega el siguiente; al abrir la Lista, los ya terminados aparecen plegados. El acordeón salta a lo siguiente pendiente (movilidad, aproximación o serie, también del ejercicio siguiente). Los ejercicios por empezar siguen desplegados. |
| L10 | Separación | Cada ejercicio es una superficie plana #141414 con radio 20 (sin vidrio) y 14 dp entre ejercicios; filas con filete tenue. |
| L11 | Autodesplazamiento | Lo que toca ahora (descanso que aparece, editor abierto o fila activa) se lleva a la vista con `animateScrollToItem`, con la fila anterior encima como contexto, solo si no se ve entero y nunca mientras la persona arrastra. |
| L12 | Modo visual | En la Lista abre directamente la vista expandida de la tarjeta (la misma superposición de `SetCardExerciseMediaBack`); al plegarla queda la cara de 350 dp con «Ampliar» para volver a abrirla. |
| L13 | Toques | Dentro de la Lista la zona táctil no se agranda más allá de lo dibujado (sin el mínimo de 48 dp de Compose, que llevaba un toque en un hueco al campo de la fila vecina) y cada fila y editor absorbe los toques que no caen en un control. Los controles miden 40–48 dp. |

## Correcciones de la madrugada del 29-oct (usuario y QA en el Flip5)

Decididas sin preguntar, por orden del usuario («no me hagas preguntas, decide por mí»).

| # | Tema | Decisión |
|---|---|---|
| 35 | Modo Voz | La cápsula es una capa viva, igual que en la iteración 03 (v-listen, v-record, v-review, v-paused, v-speak, v-rest, v-list): transcribe en vivo con los parciales reales de Vosk, convierte la frase en fichas que vuelan a su campo (carga, reps y esfuerzo de la tarjeta; fila en la Lista; reloj en el descanso), se reduce a recibo con «Deshacer» y «Editar», y muestra lo que dice KPKN (el texto real del TTS). Superficie gris plana con sombra real (decisión 28), no vidrio. Con la cápsula a la vista, la serie dictada espera como mucho 2 s a que lleguen los valores antes de registrarse. El reconocedor nativo de respaldo no da parciales: allí no hay transcripción en vivo. |
| 36 | Modos de voz | Música: «Micrófono del teléfono: la música no pierde calidad, pero acércalo para hablar.» Manos libres: «Micrófono de los auriculares: registras lejos del teléfono; la música suena a llamada.» |
| 37 | Kg de las aproximaciones | Tarjeta y Lista enseñan siempre la carga en kg. Si el material no está acreditado (sin discos ni barra configurados), el motor sigue dejándola pendiente, y la interfaz muestra el % sobre la carga de trabajo con el redondeo de la app (`getWarmupDisplayWeight`); se corrige antes de registrar. La voz sigue usando solo la carga alcanzable. |
| 38 | Kg editables en la tarjeta | La tarjeta de aproximaciones tiene un editor de la aproximación elegida (kg en Syne con − / + y reps), y el botón del roadmap registra con esos valores. Al volver a una registrada se ven sus kg y reps. |
| 39 | Toques que atravesaban la pila | La tarjeta actual absorbe los toques de su superficie y las tarjetas de debajo no reciben ninguno. Antes, una zona vacía de la aproximación caía en el campo de carga de la serie apilada detrás. |
| 40 | Descanso de preparación | Admite vista expandida (pantalla completa) y minimizada, y −15 s · pausa · +15 s funcionan, en la tarjeta y en la Lista. En el carrusel, «Terminar descanso» queda solo en el roadmap, como en la captura 02 del prototipo. En la Lista, que no tiene roadmap, la tarjeta lo conserva. |
| 41 | Botón del roadmap en preparación | «Registrar movimiento N», «Registrar aproximación AN», «Ir a la aproximación», «Ir a las series». Si no cabe, el texto se reduce hasta 10 sp en vez de cortarse (antes salía «Continuar con» a secas). |
| 42 | Volver a la preparación | El salto inicial a la serie efectiva se corrige en el navegador de pasos: una sesión nueva arranca en la movilidad o en la primera aproximación. El paginador ya no bloquea el regreso a la movilidad cuando el destino cambia (‹ o MOV desde una serie). |
| 43 | Cardio | El reloj circular pasa a un recuadro a todo el ancho con el tiempo ajustado al hueco y una figura animada según la modalidad (corre, camina, pedalea, rema…), al ritmo de la zona. «Manual» pasa a «Añadir pulso», que abre una hoja con validación de 40 a 230 lpm. En cardio no hay roadmap. «Saltar paso» no se pinta cuando no se puede saltar. |
| 44 | Interfaz vacía | Fuera «Calorías» del menú y «Gasto» de Recorrido: no hay método compatible de energía en vivo y nunca podían mostrar un dato. En su lugar, el tiempo transcurrido. Los récords de los juegos se guardan también en la tarjeta. Los chips de la calculadora RM llevan esa carga a la serie. «Revisar descanso sugerido» solo aparece cuando hay un descanso. La ✕ de «Configurar serie» con cambios vuelve a mostrar la hoja. «Listo» de la etiqueta en Setup cierra la hoja. Fuera «Cluster set: próximamente». Las filas informativas ya no responden al toque. Los controles de descanso que no se pueden usar no se pintan. |
| 45 | Cifra de carga larga | Con 5 cifras o más, la carga de la tarjeta se reduce en vez de montarse sobre «kg» y sobre − / +. |
| 46 | Alto de la tarjeta | Los 36 dp extra son solo para la tarjeta estrecha (360 de ancho) en pantallas de 820 dp de alto o más, como el Flip. A 393 × 852 el roadmap tapaba el pie. |
| 47 | Fundido de la Lista | Las filas no se ven sobre la barra de estado ni alrededor de la isla: aparecen fundidas desde el borde inferior de la isla. |
