Escribí únicamente los cinco archivos V y verifiqué sus renders e interacciones locales.

**1. Crítica del tercio inferior actual**

| Problema | Gravedad | Corrección |
|---|---|---|
| Stepper, FAB y roadmap parecen piezas independientes | Alta | Conectar navegación y registro en una isla |
| El cockpit consume espacio permanente | Alta | Sustituirlo por hojas con destinos explícitos |
| Voz compite visualmente con registrar | Media | Moverla a la cabecera |
| «MOV / APROX» exige interpretar abreviaturas | Media | Iconos claros y nombres accesibles |
| Muchas series dificultan mantener la orientación | Media | Centrar el paso activo y comprimir los lejanos |
| Falta margen para incorporar historial | Media | Fila única adaptable, con iconos en la pantalla corta |

**2. Tableros entregados**

| Archivo | Contenido y mejora | Tamaño |
|---|---|---:|
| [V1-PropuestaLinea.dc.html](C:/Users/valen/AppData/Local/Temp/claude/C--Users-valen-Documents-KPKNFit/c23e2d1d-bbd9-499d-b02a-2d07d2b09e95/scratchpad/kpkn_editor_design/project/V1-PropuestaLinea.dc.html) | Preparación, series, ejercicios plegados y registro conectados en una isla. | 29 961 B |
| [V2-IdeaUsuario.dc.html](C:/Users/valen/AppData/Local/Temp/claude/C--Users-valen-Documents-KPKNFit/c23e2d1d-bbd9-499d-b02a-2d07d2b09e95/scratchpad/kpkn_editor_design/project/V2-IdeaUsuario.dc.html) | Tu disposición: stepper separado, FAB naranja de 60 px integrado en el roadmap, voz arriba e historial junto a Modo visual. | 29 960 B |
| [V3-PantallaCorta.dc.html](C:/Users/valen/AppData/Local/Temp/claude/C--Users-valen-Documents-KPKNFit/c23e2d1d-bbd9-499d-b02a-2d07d2b09e95/scratchpad/kpkn_editor_design/project/V3-PantallaCorta.dc.html) | Ambas variantes en teléfonos de 360 × 740, conservando la tarjeta y adaptando las acciones secundarias. | 30 519 B |
| [V4-RutaSesion.dc.html](C:/Users/valen/AppData/Local/Temp/claude/C--Users-valen-Documents-KPKNFit/c23e2d1d-bbd9-499d-b02a-2d07d2b09e95/scratchpad/kpkn_editor_design/project/V4-RutaSesion.dc.html) | Hoja de ruta con reloj, métricas, ritmo, RINGS y selección de ejercicios o superseries. | 9 584 B |
| [V5-MigracionCockpit.dc.html](C:/Users/valen/AppData/Local/Temp/claude/C--Users-valen-Documents-KPKNFit/c23e2d1d-bbd9-499d-b02a-2d07d2b09e95/scratchpad/kpkn_editor_design/project/V5-MigracionCockpit.dc.html) | Mapa seleccionable de las siete funciones, con una mini vista de cada destino y lo nuevo señalado. | 21 720 B |

Todos cumplen los límites de archivo.

**3. Recomendación**

Recomiendo **V1**: expresa mejor la relación ejercicio → paso → registro y reduce la fragmentación visual. Su conjunto inferior ocupa 112 px frente a los 128 px de V2 en la pantalla alta. V2 conserva una separación más familiar entre series y ejercicios y ejecuta fielmente tu disposición.

**4. Migración del cockpit**

| Función | Destino | Qué conserva |
|---|---|---|
| Sesión | Roadmap → Ruta — **hoja nueva** | Reloj, métricas, ritmo, RINGS y selección |
| Hitos | Cabecera ⋯ → Hitos — **hoja nueva** | Hitos recientes y ejercicios estrella |
| Fotos | Cabecera ⋯ → Álbum existente — **acceso nuevo** | Cámara, Galería, pendientes, reintentos, selección, vista previa, análisis y borrar con Deshacer |
| Temporizador | Cabecera ⋯ — **hoja nueva** | Iniciar, Pausar y Reiniciar |
| Calculadora RM | Cabecera ⋯ — **hoja nueva** | Cálculo y tabla de 1 a 10 RM |
| Calorías | Cabecera ⋯ — **hoja nueva** | Estimación y avisos cuando faltan datos |
| Notas | Cabecera ⋯ → Notas y tareas — **hoja nueva** | Borrador, guardado, última nota y tareas; la nota continúa al resumen final |

«Historia» en cabecera es un **acceso nuevo** a la función existente del resumen final. «Historial» queda junto a Modo visual.

**5. Valores y comportamientos contrastados**

Rutas relativas a `screens/workout/`, salvo donde se indica:

| Código | Evidencia aplicada |
|---|---|
| `WorkoutScreen.kt:315`, `:1331` | Cockpit compacto de 78 dp y montaje del componente |
| `LiveSessionCardFrame.kt:16`, `:26` | Margen horizontal de 48 dp; proporción que da aproximadamente 312 × 425 dp |
| `IntensityCarouselLogic.kt:44`, `:58` | RIR 10 → 0 y Fallo; RPE 0 → 10 en pasos de 0,5 y Fallo |
| `components/SetExecutionCard.kt:2261` | Esfuerzo obligatorio, inicialmente sin marcar |
| `WorkoutV2Body.kt:1552`, `:1560` | Añadir series en fuerza y pulsación larga en series normales |
| `WorkoutSetPager.kt:895`, `:920` | Desplazamiento de 480 ms y gesto de 56 dp entre ejercicios |
| `components/CockpitSessionSummary.kt:68`, `:84`, `:88`, `:125` | Métricas, ritmo, RINGS y selección de ruta |
| `components/WorkoutSessionCockpit.kt:247`, `:590`, `:685` | Hasta 12 hitos, controles del temporizador y notas con tareas |
| `WorkoutContextComponents.kt:554`; `domain/calculations/Calculations.kt:60` | Tabla RM e híbrido usado por la calculadora |

La verificación en Chromium, con runtime local de prueba, confirmó tarjeta fija de **312 × 425**, objetivos táctiles **≥ 44 px**, filas sin salto, ausencia de solapamientos y enlaces existentes. Pasaron los flujos de registro, 14 series, compresión, pulsación larga, alcances de técnica, lados y rondas, retorno a V2, temporizador, RM, notas, tareas y Deshacer de medios.