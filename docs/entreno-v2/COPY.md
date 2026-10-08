# Textos del módulo de Entreno v2

Voz: cercana, calmada y directa; español neutro **sin género gramatical** (nada de «listo/a», «fresco/a»: se reformula). Mismos nombres para las mismas
cosas en todo el módulo: **programa** (lo que se entrena; «plan» queda para nutrición), **sesión**, **semana**, **material** (los implementos),
**lugar** (gimnasio, casa, espacios públicos). Títulos de pregunta ≤ 44 caracteres y subtítulos ≤ 100 (`SetupStepCopyRulesTest`). Un subtítulo da contexto útil,
nunca instrucciones de interfaz. Tono: éxito = celebra sin exagerar; aviso = claro y accionable; error = empático con salida.

## Pasos (título · subtítulo)
| Paso | Título | Subtítulo |
|---|---|---|
| `EQUIPMENT` | ¿Dónde entrenas? | Elige uno o varios lugares. |
| `AVAILABILITY` | ¿Con qué material entrenas? | Marca lo que tienes y lo que quieres usar. |
| `GOAL` | ¿Cuál es tu objetivo? | Elige un perfil general o una disciplina. |
| `FRESH_DAY` | ¿Qué día llegas con más energía? | Tu sesión más fuerte caerá ese día. |
| `WEEKDAYS` | ¿Qué días puedes entrenar? | Entre 1 y 7. El programa se adapta a tu semana. |
| `SESSION_TIME` | ¿Cuánto tiempo tienes por sesión? | Es un rango: el programa se ajusta a ti. |
| `CAPABILITIES` | ¿Qué ejercicios ya te salen? | Así elegimos variantes a tu medida. |
| `PRIORITIES` | ¿Qué músculos priorizas? | Elige hasta 5. Puedes omitir este paso. |
| `TRAINING_MAX` | ¿Conoces tus marcas? | Con una basta. Sin marcas, el programa sigue siendo válido. |
| `PLAN` (general) | Tu programa a medida | Armado con tu material, tus días y tu tiempo. |
| `PLAN` (disciplina) | Elige tu programa | Elige el que más te guste. Podrás modificarlo después. |
| `WEEK_LAYOUT` | Así queda tu semana | Mueve las sesiones a los días que prefieras. |

## Textos de los controles
- **Lugares**: Gimnasio · En casa · En espacios públicos. Con ≥ 2 elegidos: «Después podrás elegir dónde entrenas cada día.» Validación: «Elige al menos un lugar.»
- **Material**: pie con gimnasio: «En el gimnasio ya contamos con lo habitual. Desmarca lo que no quieras usar.»; solo casa/parque: «Marca solo lo que tienes a mano.»; exclusivo: «Solo peso corporal» (nota: «Entrenas con tu cuerpo.»). Etiquetas de `EquipmentSymbolId.label` (incluye **Smith/Multipower**).
- **Objetivo**: tramos «Generales» y «Disciplinas» (leyenda: «Dependen de tu material»). Razones de bloqueo (una línea, sin culpar): Powerlifting «Necesita barra, rack y banco.» · Powerbuilding «Necesita barra, rack y banco, o mancuernas.» · Culturismo «Necesita mancuernas, barra, poleas o máquinas.» ·
  Calistenia «Necesita barra de dominadas o anillas.» · Halterofilia «Necesita barra y rack.» · Strongman «Necesita barra y mancuernas o kettlebell.» · Armwrestling «Necesita mancuernas, poleas, bandas, barra o kettlebell.» Acción al tocar un perfil bloqueado: «Cambiar mi material».
- **Día con más energía**: nota «También será el primer día de tu semana.»
- **Calendario**: contador «1 día por semana» / «N días por semana»; «La semana empieza el jueves»; marca del día fuerte «Tu sesión más fuerte»; con varios lugares «¿Dónde entrenas ese día?».
  Aviso cuando el día con más energía se quita de los de entreno (una nota bajo el calendario): «Sin entrenar el martes, tu sesión más fuerte pasa al jueves.» La sesión más fuerte cae en el primer día de entreno posterior de la semana (dando la vuelta); es la regla del generador (`WeekPlanner.mainDay`), y los planes de autor no usan el día con más energía. Iniciales de los días: L M Mi J V S D (el miércoles es «Mi»: una X suelta no se lee como un día).
- **Tiempo**: lectura «75 min» y «1 h 15 min»; atajos 30 · 45 · 60 · 90 · 120; pista dinámica: ≤ 30 min «Con poco tiempo vamos a lo esencial.» · ≥ 90 min «Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.»
- **Capacidades**: niveles «Aún no» · «Algunas» · «Varias»; pie «Sin presión: siempre podrás cambiarlo.»
- **Músculos**: «Omitir» arriba a la derecha (texto discreto, no botón); etiqueta de preselección «Sugerido»; tope «Máximo 5 músculos.»
- **Marcas**: «No la sé»; unidad «kg · lb»; ayuda «Tu mejor levantamiento de una repetición, o una estimación.»
- **Semana (tablero de siete filas)**: guía «Arrastra una sesión por su asa, o tócala y elige un día.» · mientras se lleva «Suéltala sobre un día. Fuera de la semana se cancela.» / «Suéltala en martes.» · con una elegida «Toca el día al que quieres mover «Torso A».» · día libre «Descanso» (TalkBack: «Martes, descanso»; con una sesión elegida ofrece «Mover aquí la sesión elegida»).
- **Reparto**: «Adaptar mi programa a este reparto» · «Restablecer» · pie «Puedes cambiar todo esto cuando quieras desde tu programa.» Aviso en planes de autor: «Este programa trae su reparto de autor. Si lo adaptas, cambia su estructura original.»
  Confirmación: «¿Adaptar tu programa a «Torso y pierna»?» / «Reubicamos los ejercicios y mantenemos tu volumen semanal.» → «Adaptar» / «Mantener mi reparto».

## Revelado del programa
- Overlay general: **«Estamos preparando tu programa personalizado»** — etapas «Tu material», «Tus días», «Tu tiempo», «Tus músculos», «Tus ejercicios».
- Overlay disciplina: **«Seleccionando programas para tu disciplina»** — etapas «Tu disciplina», «Tu material», «Tus días», «Tu nivel», «Los mejores programas».
- Resultado: «Tu programa está listo» · acciones «Elegir este programa», «Ver detalles», «Otra versión» · frase fija «Podrás modificarlo libremente después.»
- Error: «No pudimos preparar tu programa. Tus respuestas siguen guardadas.» → «Reintentar».
- Insignias de las portadas: «Hecho a tu medida», «Se adapta a ti», «Versión inicial», o el autor («Jim Wendler»).

## Notas honestas del generador (accionables, nunca alarmistas)
- Sin tracción: «Sin barra de dominadas ni bandas no hay ejercicios de tracción. Añade una barra o unas bandas para completar tu semana.»
- Sin empuje vertical: «Con tu material no hay press por encima de la cabeza. Unas mancuernas o una barra lo completan.»
- Poco tiempo: «Con {N} min vamos a lo esencial: los ejercicios principales y lo justo de accesorios.»
- Mucho tiempo: «Con {N} min añadimos aproximaciones, movilidad y descansos más largos en los ejercicios pesados.»
- Versión inicial por disciplina (se rotulan así hasta que el catálogo tenga sus levantamientos): Calistenia «Aún faltan progresiones avanzadas como el muscle-up o el equilibrio en manos.» · Halterofilia «Aún no incluimos arranque ni dos tiempos: trabajamos la fuerza y la potencia que los sostienen.» ·
  Strongman «Sin yugo, piedras ni trineo: trabajamos fuerza base y acarreos con tu material.» · Armwrestling «Sin trabajo de mesa: entrenamos antebrazo, agarre, bíceps y espalda.»

## Reglas de estilo
Verbos de acción en los botones («Elegir este programa», no «Aceptar»). Sin signos de exclamación salvo en el resultado. Números con unidad pegada a su palabra («60 min», «75 kg»). «Tu/tus» en vez de «el usuario». Evita «recomendado» salvo en la insignia.
