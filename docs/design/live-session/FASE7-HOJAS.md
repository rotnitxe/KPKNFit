# Fase 7 · Hojas y acciones

Objetivo: inventariar todo lo que se abre sobre la sesión, con su disparador, su contenido y su forma de cerrarse. Una hoja que no esté aquí no se dibuja.

Fuentes: `LiveSessionSheets.kt`, `HeaderSheet.kt`, `ExerciseOptionsSheet.kt`, `WorkoutExerciseSheets.kt`, `WorkoutStructureSheetsHost.kt`, `UltraFastRecordSheet.kt`, `UltraFastSheet.kt`, `LiveReorderSheet.kt`, `ExerciseAlbumSheet.kt`, `WorkoutSessionAlbumSheet.kt`, `ExerciseRmSheet.kt`, `DiscomfortSheet.kt`, `SessionTimeSheet.kt`, `LiveSessionNotesAndMilestones.kt`, `WorkoutHistorySheetContent.kt`, `WorkoutOverlayHost.kt`, `WorkoutDrawerHost.kt`.

## 1. Host de hojas

| Propiedad | Valor |
|---|---|
| Alto | 76 % del viewport; Ruta 84 %; Reordenar 88 %; Álbum 93 %; Molestia 96 %; Editar series 100 % |
| Cabecera | título de 20/26 + botón `Cerrar {título}` |
| Estado | `SaveableStateProvider` por destino: cambiar de hoja no pierde el scroll |
| Cierre | scrim, botón, gesto o atrás; las hojas destructivas pueden bloquear atrás |
| Regla | una hoja a la vez; abrir otra cierra la anterior sin apilar |
| Seguridad | si el ejercicio asociado desaparece, la hoja se cierra sola (`closeDrawersWithoutExercise`) |

### 1.1 Destinos del menú

| Destino | Título | Contenido |
|---|---|---|
| `MENU` | `Sesión` | 7 destinos: `Ruta de la sesión`, `Hitos`, `Fotos y vídeos`, `Temporizador`, `Calculadora RM`, `Calorías`, `Notas y tareas` |
| `ROUTE` | `Ruta de la sesión` | Resumen + lista de bloques |
| `MILESTONES` | `Hitos` | Hitos reales + ejercicios estrella |
| `PHOTOS` | `Fotos y vídeos` | Redirige al álbum de sesión |
| `TIMER` | `Temporizador` | Reloj global, iniciar/pausar, reiniciar |
| `RM` | `Calculadora RM` | Peso, lastre, asistencia, reps, e1RM, tabla |
| `CALORIES` | `Calorías` | Energía registrada + nota |
| `NOTES` | `Notas y tareas` | Nota de sesión + checklist |

## 2. Tabla completa

| Hoja | Disparador | Título / subtítulo | Contenido literal | Acciones / cierre | Vacío / alto |
|---|---|---|---|---|---|
| **Menú** | `⋯` cabecera | `Sesión` | `Ruta de la sesión`, `Hitos`, `Fotos y vídeos`, `Temporizador`, `Calculadora RM`, `Calorías`, `Notas y tareas` | Abre destino; Fotos cierra y abre álbum | 76 % |
| **Ruta** | fila de ruta del raíl | `Ruta de la sesión` | `Series hechas/total`, `Tonelaje`, `RIR medio`, `Gasto ≈`; proyección `A este ritmo terminas en…`; bloques con `✓`, `en curso`, `rondas`, `series` + `S{n} L/R · kg · calibrada`; `Opciones de {título}` | Seleccionar bloque; `Añadir ejercicio` | 84 % |
| **Hitos** | Menú | `Hitos` | `Hitos de la sesión`; `Ejercicios estrella`; tarjeta con `label`, ejercicio, detalle; estrella `{e1RM}/{meta} kg` + barra | Solo lectura | `Todavía no hay hitos. Aparecen con PRs reales vs historial o meta estrella.`, 76 % |
| **Notas y tareas** | Menú | `Notas y tareas` | `Notas de sesión`; `Anotaciones generales de la sesión…`; `Guardar nota`; `Nueva tarea`; `Añadir`; checklist con check + `Eliminar` | `Guardar` deshabilitado si vacío; `Añadir` ídem | `Checklist vacío. Añade objetivos para esta sesión.`, 76 % |
| **Temporizador** | Menú | `Temporizador` | `mm:ss`; `Iniciar` / `Pausar`; `Reiniciar`; `Independiente del descanso entre series` | Cierra sin perder tiempo | 76 % |
| **Calorías** | Menú | `Calorías` | `Energía registrada`; `≈ N kcal`; nota al pie | Solo cerrar | Si no hay resumen, solo cabecera + nota, 76 % |
| **Calculadora RM** | Menú | `Calculadora RM` | `Peso (kg)`, `Lastre (kg)`, `Asistencia (kg)`, `Repeticiones`, `e1RM`, `e1RM equiv.`, `Peso corporal (solo cálculo)`, `Tabla RM`, chips `N RM · kg` | Cierra el host | Sin resultado no hay tabla, 76 % |
| **Tiempo de sesión** | chip de tiempo | `Tiempo de sesión` | `Ultrarrápido activo`, `Deshacer Ultrarrápido`, `Modo ultrarrápido`, `de N min`, `Sin objetivo`, `En marcha`, `DURACIÓN OBJETIVO`, `30/45/60/90 min`, `Asignar tiempo`, `−5/+5/+15`, `Quitar`, `Solo esta vez` / `Permanente`, `AVISOS`: `Nada`, `15 y 5 min`, `Ritmo`, `Por ejercicio` | `Proponer`, `Asignar`, `Quitar`, `Aplicar`, `Revertir`; picker `Duración de sesión` / `Horas : minutos` | Wrap |
| **Ultrarrápido (preview)** | `Modo ultrarrápido` | `Ultrarrápido` | `Duración estándar → con Ultrarrápido`; `Ahorras ~S (P% más rápida)`; `AJUSTES POR EJERCICIO`; tiles `Series reducidas`, `Drop-set`, `Rest-pause`, `Sin cambio`, `Complejo protegido`, `Básico protegido`; `Permitir técnica intensa`; `SUPERSERIES PROPUESTAS` | `Cancelar` / `Aplicar`; `Reversible: deshaz desde el aviso o el menú de tiempo.` | `Calculando…` |
| **Ultrarrápido (registro)** | serie rápida | `Ultrarrápido` / `{nombre} · N series pendientes` | `Normal` / `Drop-set` / `Rest-pause`; filas `S{n} · {kg} × {reps} · {estado}`; `Carga`, `Reps`, `RIR`, `Esfuerzo`; aviso `Marca tu esfuerzo antes de registrar.` | `Registrar S{n}` / `Registrando…`; habilitado con peso + reps + RIR | No soportado: `Volver a la sesión` + `Usa la tarjeta de serie…` |
| **Opciones de ejercicio (fuerza)** | long-press del carrusel | `{ejercicio}` / dosis + hecho | `Reemplazar`, `Reordenar`, `Crear superserie`, `Historial`, `Ultrarrápido`, `Notas`, `Etiquetas`, `Editar`, `Agregar ejercicio`, `Calculadora RM` | Cada acción cierra y abre destino | — |
| **Opciones de ejercicio (cardio)** | long-press | `{ejercicio}` | `Editar cardio`, `Reemplazar` | — | — |
| **Opciones de superserie** | long-press | `Superserie` | `Ir a la superserie`, `Rondas`, `Agregar de sesión`, `Agregar del catálogo`, `Miembros`, `Disolver superserie` | — | — |
| **Opciones de serie** | long-press del paso | `Serie N · {kg}×{reps}·RIR` | `Editar`, `Añadir`, `Saltar`, `Borrar`; `Tipo de serie`: `Normal`, `Drop-set`, `Rest-pause`; alcance `Solo S{n}` / `Desde S{n} hasta el final` | `Cancelar` / `Aplicar`; hechas no marcables | — |
| **Config AMRAP** | toggle AMRAP de la tarjeta | `Configurar serie AMRAP` | `Repeticiones mínimas` (`Sin mínimo`), `Objetivo de la serie` (`Llegar al fallo` / `Reservar reps`), `RIR` | `Aplicar` / cerrar | — |
| **Etiquetas** | `Etiquetas` del menú o chip | `Etiquetas` / `Nueva etiqueta` / `{etiqueta}` | `Las etiquetas aparecen…`; `Mantén pulsada…`; `Crear etiqueta nueva`; `Nombre`, `Máquina o marca`, `Carga base (kg)`, `Notas`; `ETIQUETAS SUGERIDAS`; `Etiqueta personalizada`; `Subetiquetas`; `Añadir sub-etiqueta`; `Reiniciar historial`, `Eliminar etiqueta` | `Listo` aplica; `Crear`, `Guardar nombre`, `Ver más`, `Cancelar` | `Este ejercicio aún no tiene etiquetas.` |
| **Setup** | contexto del ejercicio | `Setup` / `{ejercicio}` | Etiquetas + perfil/setup de serie | `Listo` | `Este ejercicio no tiene series.` |
| **Reordenar** | Menú u opciones | `Orden de la sesión` | `Arrastra desde el asa`, `Marca los ejercicios`, `N ejercicios marcados`; filas con asa, hecho/fijo/en curso; lote `Superserie`, `Omitir`, `Quitar`, `Ultrarrápido` | `Listo`, `Seleccionar`, subir/bajar | Fijo no arrastrable; 88 % |
| **Álbum de ejercicio** | `Ver álbum` o icono | `Álbum` / `{sujeto} · N registros` | Filtros `Todo/Fotos/Vídeos`; `Seleccionar`; filas `Serie N · kg × reps`, `RIR`, `Vídeo`, `hh:mm`, `Marca`; papelera; `Borrar (N)`; barra `Foto borrada` + `Deshacer` | Borrado diferido; cerrar hace flush | `Todavía no hay fotos ni vídeos de este ejercicio.`, 93 % |
| **Álbum de sesión** | Menú o relator | `Álbum` / `Esta sesión` | Igual + captura | Igual | `Todavía no hay fotos ni vídeos en esta sesión.` |
| **Consulta de RM** | opciones del ejercicio | `Consulta de RM` / `{ejercicio}` | `PR ESTIMADO`, `{est} kg`, `{peso} kg × {reps} · Hoy/{fecha}`, `Rango de la sesión`, filas `N RM` | `Listo` | `Todavía no hay una referencia de RM / Registra una serie válida…`, 90 % |
| **Ficha de molestia** | chip de molestia | `{título}` / `{zona}` | Explicación, `CAUSAS COMUNES`, `REGISTROS ANTERIORES`, `QUÉ PUEDES HACER`, escena | `Reportar molestia` / `Quitar molestia` | 96 % |
| **Reportar molestias (fallo)** | fallo de ejecución | `Reportar molestias` / `{ejercicio}` | `¿Tuviste alguna molestia…?`, buscador, chips + `Detalle`, `Escribe para buscar…`, `No se encontraron resultados…`, `Sin molestias`, `Guardar` | Atrás no cierra | Sin lista hasta buscar |
| **Historial** | opciones del ejercicio | `Historial` | `Mostrando primero: {tag}`; grupos `Esta semana`, `Semana pasada`, `Este mes`, `{Mes año}`; tarjeta `fecha`, `tag`, notas, `kg × reps · RPE/RIR`, parciales, `❌ Fallida` + motivo | Cierra | `Sin historial registrado` |
| **Nota del ejercicio** | opciones | `Nota del ejercicio` / `{ejercicio}` | Campo de 3–6 líneas | `Guardar` / `Cancelar` | Vacío permitido |
| **Rondas y descansos** | opciones de superserie | `Rondas y descansos` | `Rondas`, `Descanso entre ejercicios (s)`, `Descanso post-ronda (s)` | `Guardar` / `Cancelar` | — |
| **Crear superserie** | opciones o reordenar | `Crear superserie` / `Agregar a superserie` | `Selecciona ejercicios pendientes…`, `Rondas`, `Entre ejercicios (s)`, `Después de la ronda (s)`, `Origen`, máx. 4 | `Crear` / `Actualizar` (≥2), `Desde catálogo`, `Cancelar` | Sin candidatos: aviso |
| **Editar series / cardio** | opciones | `Editar series` / `Editar cardio` / `{ejercicio}` | Cuerpo de edición + `SmartLoadDialog` | `Confirmar cambios`; `X`/atrás descarta | 100 % |
| **Persistencia** | tras reemplazar/añadir/reordenar | `Persistencia de reemplazo` / `{acción}` | Detalle; `Solo esta vez`; `Reemplazar en todo el programa` / `Guardar en esta semana` / `Aplicar a todo el bloque` | `Confirmar` / cerrar | — |
| **Quitar confirmación** | `Quitar` | `Quitar de la sesión` | `¿Quitar «{n}»…?` / `¿Quitar N ejercicios…?`; aviso si hay series hechas; `Puedes deshacerlo justo después.` | `Quitar` (danger) / `Cancelar` | — |
| **Salir** | atrás o menú | `¿Qué deseas hacer?` | `Puedes pausar y continuar…` | `Continuar entrenando`, `Terminar hasta acá`, `Pausar y salir`, `Abandonar sin guardar` | — |
| **Finish** | terminar sesión | `SESIÓN COMPLETADA` / `{nombre}` + `{min} min · {n} ejercicios` | Tabs `Resumen`, `Progreso`, `Anotaciones`; métricas; volumen por músculo; PRs; fotos; notas; tareas; esfuerzo | `Guardar sesión`, `Volver a la sesión`; bloqueado si sesión vacía | 94 % |

## 3. Reglas de las hojas

1. **Una a la vez.** Abrir una cierra la anterior; no hay pila de hojas.
2. **No atrapar.** Una hoja sin ejercicio asociado se cierra sola.
3. **No perder.** El scroll y el borrador sobreviven al cambio de destino.
4. **No duplicar.** `Saltar` y `Borrar` viven en `SeriesOptionsSheet`, no en una tarjeta de modo dios.
5. **Confirmar lo destructivo.** `Quitar`, `Abandonar sin guardar` y `Borrar` piden confirmación o dan deshacer.
6. **Deshacer lo reversible.** Álbum y eliminación de serie ofrecen `Deshacer`; al cerrar, el borrado se confirma.
7. **Deshabilitar con motivo.** `Guardar`/`Añadir` se apagan si falta el dato; nunca se inventa.
8. **Persistencia explícita.** Reemplazar/añadir/reordenar pregunta si es solo esta vez o afecta al programa.
9. **Atrás con intención.** Si hay una serie en curso, atrás no sale: avisa.
10. **La hoja no cambia la tarjeta.** El fondo se atenúa, no se redimensiona.
11. **El menú no inventa acciones.** `Sesión` son los siete destinos del enum; `Reordenar` vive en las opciones del ejercicio y `Salir` en el diálogo de atrás.

## 4. Criterio de aceptación

- [ ] Toda hoja del prototipo aparece en la tabla §2 con su disparador.
- [ ] Ninguna hoja inventa acciones fuera de las listadas.
- [ ] Las destructivas confirman o deshacen.
- [ ] Las hojas que dependen de un ejercicio se cierran si este desaparece.
- [ ] El estado se conserva al cambiar de destino.
- [ ] La hoja de salir ofrece las cuatro salidas reales.
