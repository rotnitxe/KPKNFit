# KPKN multideporte v2: inventario de revisión

Solo ampliaciones. Home, diario nutricional, Cuerpo, Training y tarjeta de serie existentes no forman parte de la galería.

Abrir `gallery.html` o `index.html#wizard-sports`. Cada ruta funciona mediante enlaces relativos y `file://`; las fuentes y recursos son locales.

## Reinicio y casos

- En escritorio: **Reiniciar estados locales** en el panel de revisión. Reinicia todos los escenarios al estado inicial, sin archivos ni red.
- En móvil: agregar `?reset=1` al abrir `index.html` o borrar el almacenamiento de este origen. El reinicio por URL se aplica una vez y conserva el hash.
- **Cargar caso fuerza + running** restablece el caso piloto explícito: fuerza intermedia, running inicial, cuatro ventanas, dos apoyos por deporte y clase fija. No se presenta como resultado de otra selección.
- Caso natación sola: seleccionar solo Natación, configurar ventanas/sede Piscina y activar la sede. Prioridad/objetivos/revisión no muestran fuerza o running.
- Fuerza + MMA: seleccionar ambas disciplinas; elegir experiencia/meta/prioridad; declarar nombre/día/horas y vincular la clase a MMA en el editor del compromiso. Sin vínculo la revisión bloquea y ofrece definir la clase. Con clase de 90 min, dos apoyos de fuerza de 65 + 70 min y una clase suman 225 min, una sola vez. MMA solo conserva la clase y no genera apoyos ni contacto.
- Powerlifting sin barra: ruta dedicada; al elegir un camino independiente se carga intención Powerlifting con sede Casa. Preparación transferible conserva el objetivo pero no se llama powerlifting en la semana. Buscar acceso conserva la restricción hasta añadir una sede equipada.

## Rutas y controles

| Ruta | Qué se agrega | Control y cambio observable | Reinicio |
|---|---|---|---|
| [wizard-sports](./index.html#wizard-sports) | Multiselección propagada a objetivos, experiencia, prioridades y propuesta. | Desmarcar todos bloquea Continuar; elegir solo Natación elimina fuerza/running de los pasos siguientes. | Reiniciar estados locales |
| [wizard-levels](./index.html#wizard-levels) | Experiencia independiente para cada disciplina seleccionada. | Cambiar nivel de un deporte; el otro conserva su nivel. | Reiniciar estados locales |
| [wizard-objectives](./index.html#wizard-objectives) | Una meta propia por deporte, superando el objetivo único. | Cambiar una meta y verla conservada en prioridad y revisión. | Reiniciar estados locales |
| [wizard-priority](./index.html#wizard-priority) | Orden para resolver recursos compartidos entre varios objetivos. | Elegir otra prioridad y conservar todas las disciplinas. | Reiniciar estados locales |
| [wizard-availability](./index.html#wizard-availability) | Intervalos inicio/fin y sede por día, compartidos entre deportes, más clases fijas. | Editar un intervalo y su sede; revisar si la propuesta cabe. | Reiniciar estados locales |
| [wizard-equipment](./index.html#wizard-equipment) | Material ligado al lugar real de cada ventana; validación específica de powerlifting. | Desactivar gimnasio y quitar barra/rack en casa; abrir caminos de powerlifting. | Reiniciar estados locales |
| [powerlifting-access](./index.html#powerlifting-access) | Restricción explícita y caminos que conservan la intención sin afirmar equivalencia. | Elegir preparación transferible, acceso a material u otro enfoque; la revisión cambia de nombre y restricción. | Reiniciar estados locales |
| [wizard-review](./index.html#wizard-review) | Propuesta derivada de deportes, ventanas, sedes, material y prioridad; solicitudes sin hueco visibles. | Aplicar caso viable; solicitudes sin ventana bloquean aplicación. | Reiniciar estados locales |
| [profile](./index.html#profile) | Objetivos, experiencia y prioridad independientes conviven en el perfil deportivo. | Editar selección; comprobar objetivos y prioridad conservados. | Reiniciar estados locales |
| [week](./index.html#week) | Movimiento entre ventanas/sedes; compatibilidad multideporte y compromisos fijos. | Mover Fuerza A al miércoles desde menú; abrir comparación de cruce. | Reiniciar estados locales |
| [compatibility](./index.html#compatibility) | Comparación de proximidad horaria que separa cruce de recuperación. | Alternar semana/mismo día; observar separación real. | Reiniciar estados locales |
| [conflict](./index.html#conflict) | Solapamiento temporal explícito y alternativa que conserva la clase. | Abrir otros huecos desde comparación de 15 minutos. | Reiniciar estados locales |
| [alternative](./index.html#alternative) | Cercanía a la siguiente disciplina y margen de preparación. | Cambiar opción y confirmar; la semana refleja día y hora. | Reiniciar estados locales |
| [session](./index.html#session) | Identidad de bloques y origen de métricas dentro de una sesión combinada. | Abrir regla de fuerza y relevo hacia running sin duplicar sesión. | Reiniciar estados locales |
| [transition](./index.html#transition) | Continuidad del plan híbrido y preparación del segundo deporte. | Deslizar al final o usar acceso de un toque. | Reiniciar estados locales |
| [running](./index.html#running) | Comparación contextual aislado/tras fuerza y registro del bloque anterior. | Cambiar contexto y conservar origen de los registros. | Reiniciar estados locales |
| [cycling](./index.html#cycling) | Par de disciplinas con transición T2 y reparto de tiempo. | Editar T2; observar total del brick. | Reiniciar estados locales |
| [summary](./index.html#summary) | Agregación por modalidad en una sesión, con actividades relacionadas. | Abrir origen del gasto y elegir una fuente. | Reiniciar estados locales |
| [training-energy](./index.html#training-energy) | Conciliación manual/importada del mismo bloque antes del agregado. | Elegir 180/185 kcal del mismo running; total 500/505, sin duplicación. | Reiniciar estados locales |
| [progress](./index.html#progress) | Progreso separado por disciplina y unidad, sin score universal. | Alternar fuerza/running y tocar una observación. | Reiniciar estados locales |
| [strength](./index.html#strength) | Regla de dos cumplimientos, criterio técnico y comparación de variantes; e1RM como referencia secundaria. | Modificar incremento y registrar cumplimiento; propuesta usa el valor elegido. | Reiniciar estados locales |
| [weightlifting](./index.html#weightlifting) | Cadena con dosis por eslabón, carga compartida y rondas. | Editar reps de un eslabón; volumen se recalcula. | Reiniciar estados locales |
| [skills](./index.html#skills) | Progresiones visuales e isometrías en segundos. | Elegir variante y registrar segundos; aparece intento específico. | Reiniciar estados locales |
| [strongman](./index.html#strongman) | Carries/implementos con cargas y distancias propias. | Marcar tramo; distancia suma una vez, progreso hasta 100 m. | Reiniciar estados locales |
| [armwrestling](./index.html#armwrestling) | Lateralidad, ángulo, agarre y duración isométrica. | Cambiar lado/ángulo y guardar sostén específico. | Reiniciar estados locales |
| [crossfit-amrap](./index.html#crossfit-amrap) | Score AMRAP con rondas completas y reps parciales. | Sumar 18 reps desde 3+12 para llegar a 4+0. | Reiniciar estados locales |
| [crossfit-emom](./index.html#crossfit-emom) | Cumplimiento por minuto y ciclo trabajo/descanso. | Marcar minuto; cambia cumplimiento sin score AMRAP. | Reiniciar estados locales |
| [crossfit-fortime](./index.html#crossfit-fortime) | Tiempo al completar o reps pendientes al cap. | Llegar al cap con 18 pendientes; completar reps permite tiempo final. | Reiniciar estados locales |
| [hyrox](./index.html#hyrox) | Ocho pares carrera/estación con dosis/transiciones propias. | Avanzar estación; cambian unidad y próximo tramo. | Reiniciar estados locales |
| [striking](./index.html#striking) | Rounds y combinaciones conectadas con registro. | Tocar golpe, registrar combo, iniciar/pausar round. | Reiniciar estados locales |
| [grappling](./index.html#grappling) | Secuencia de posiciones y rolling con resultado técnico. | Tocar posiciones; cadena conserva orden y guardar añade rolling. | Reiniciar estados locales |
| [mma](./index.html#mma) | Camp por fases y apoyos alrededor de clases fijas. | Cambiar semana; cambian fase/apoyos, abrir clase fija. | Reiniciar estados locales |
| [trail](./index.html#trail) | Elevación, ascenso y contexto montaña/asfalto. | Tocar cumbre/descenso; cambian elevación y contexto. | Reiniciar estados locales |
| [rowing](./index.html#rowing) | Ritmo /500 m, paladas e historial de intervalos. | Cambiar parcial/distancia; tiempo se calcula y guarda. | Reiniciar estados locales |
| [swimming](./index.html#swimming) | Largos, longitud de piscina, estilo y bloques. | Cambiar 25/50 m: 8 largos = 200/400 m. | Reiniciar estados locales |
| [triathlon](./index.html#triathlon) | Tres disciplinas y dos transiciones sin actividad extra duplicada. | Recorrer Natación → T1 → Bici → T2 → Running. | Reiniciar estados locales |
| [climbing](./index.html#climbing) | Vía, grado, presa e intento/encadene separados. | Elegir presa y guardar; encadenar marca presa 7 completa. | Reiniciar estados locales |
| [athletics](./index.html#athletics) | Mejor marca por criterio mínimo/máximo y protocolo. | Sprint menor o salto mayor actualiza mejor marca. | Reiniciar estados locales |
| [tennis](./index.html#tennis) | Marcador con deuce, ventaja, tie-break y deshacer. | Desde deuce dos puntos ganan juego; Deshacer restaura. | Reiniciar estados locales |
| [padel](./index.html#padel) | Punto de oro y registro por pareja. | Desde 40–40 un punto gana juego; Deshacer revierte. | Reiniciar estados locales |
| [team](./index.html#team) | Minutos/acciones propios y carga minutos × RPE. | Sumar acción y cambiar minutos; carga se recalcula. | Reiniciar estados locales |
| [sensors](./index.html#sensors) | Procedencia/frescura en flujo multideporte. | Cambiar a manual; FC actual pasa a ausente. | Reiniciar estados locales |
| [sensor-stale](./index.html#sensor-stale) | Último valor etiquetado por antigüedad. | Registrar RPE sin convertir última lectura en señal actual. | Reiniciar estados locales |
| [sensor-lost](./index.html#sensor-lost) | Continuidad sin interpolar señal perdida. | Registrar RPE conservando FC actual ausente. | Reiniciar estados locales |
| [huawei](./index.html#huawei) | Archivo/manual sin prometer conexión no verificada. | Abrir importación o manual; sin conexión ficticia. | Reiniciar estados locales |
| [import](./index.html#import) | Candidato/original vinculados al mismo bloque híbrido. | Omitir conserva una; otra actividad permite revisar doble conteo. | Reiniciar estados locales |

## Comprobaciones ejecutadas

- Renderizado local de 46 vistas sin excepción, NaN o undefined en el HTML.
- 36 contratos de cálculo/estado comprobados por `check-prototype.mjs`, detallados en `QA-LOCAL.json`.
- `node --check` debe ejecutarse sobre cada archivo JavaScript. El resultado de este script no equivale a prueba de interacción de navegador.
- El orquestador revisa navegador, dimensiones y controles por separado. Este documento no afirma PASS visual ni aprobación humana.
- Sin cambios nativos, Gradle, instalación, adb ni prueba física en este paquete.

## Base interna

`baseline.html` y `comparison.html` son controles internos con captura nativa a 448×997,333 CSS px. Están excluidos de galería y portada. La comparación inicial mejoró el ritmo del módulo y corrigió el hueco de la torre; queda una diferencia menor en halo. No se afirma equivalencia pixel-perfect.
