/* ================= RESUMEN · TUS 26 PUNTOS, LO ARREGLADO, MIS DECISIONES Y LO QUE TE TOCA ================= */
// Tipo: D = arreglo directo ya escrito en el código (sin compilar) · P = prototipo para aprobar · D+P = las dos cosas.
const RS_PUNTOS=[
 ['1','Quitar la pantalla «Cargando sesión de entrenamiento»','D','Código'],
 ['2','Análisis de técnica sin scroll, con los datos sobre el vídeo y «analizando» en primer plano','P','Técnica por vídeo'],
 ['3','«Aplicar plantilla o método» como un catálogo','P','Plantillas y métodos'],
 ['3 bis','Series del editor: «Externa» por defecto con menú, reps/rangos, intensidad integrada, descanso y acciones','P','Editor'],
 ['4','Asistente de sesión: Métricas, Sugerencias, Plantillas y Reglas','P','Asistente'],
 ['5','Miniaturas del cardio que representen cada máquina o lugar','P','Cardio · editor'],
 ['6','Tarjeta de configuración de cardio, menos densa','P','Cardio · editor'],
 ['7','«Avanzado» del cardio: fuera lo que no se usa','Auditoría + P','Cardio · editor'],
 ['8','Selector de duración','P','Cardio · editor'],
 ['9','Pulsación larga y ficha propias del cardio','P','Cardio · editor'],
 ['10','Borrar por deslizamiento y arrastrar para ordenar, bien animados','P','Editor'],
 ['11','Tarjetas de cardio y fuera «Añadir cardio» de dentro de la tarjeta','P','Cardio · editor'],
 ['12','Píldora superior del editor en vidrio con desenfoque','D+P','Código · Editor'],
 ['12 bis','Barra inferior: Añadir, Asistente, Reglas y minutos estimados','P','Editor'],
 ['13','Repeticiones en carrusel y esfuerzo que se enciende hacia el fallo','P','En vivo · tarjeta'],
 ['14','Roadmap inferior, segunda propuesta: A «Calma» (112 dp), B «Con ruta visible» (132 dp) y la «Ruta»','P','Roadmap (pestaña propia)'],
 ['15','ROM conectado: editor → tarjeta con slider → serie','D+P','Código · En vivo · tarjeta'],
 ['16','Cronómetro de verdad y temporizador','P','En vivo · más'],
 ['17','«Expandir» en el modo visual de la tarjeta','D (+ contrato)','Código · En vivo · más'],
 ['18','Hoja de sesión completada sin scroll','P','En vivo · más'],
 ['19','Modo Lista: desvanecido en los extremos y píldora flotante','D+P','Código · En vivo · más'],
 ['20','Dictado de voz del registro de comida','D+P','Código · Comida y Cuerpo'],
 ['21','Nueva medición: botón flotante, esquinas y diseño de la hoja','D+P','Código · Comida y Cuerpo'],
 ['22','Ajustes: solo lo estrictamente necesario','P','Ajustes'],
 ['23','Reparto: descripciones, vista previa interactiva y motor de migración','P','Reparto'],
 ['24','Suplementos: más opciones y registro rápido, también desde Inicio','P','Suplementos'],
 ['25','Cardio en vivo: rediseño total','P','Cardio en vivo'],
 ['26','«Al inicio / Al final» del editor','P','Cardio · editor'],
];
const rsPill=t=>`<span class="rs-pill ${t==='D'?'d':t.startsWith('D+')||t.startsWith('D (')?'dp':'p'}">${t}</span>`;
scene({tab:'resumen',id:'rs-puntos',title:'Tus 26 puntos',doc:()=>`
 <h2>Qué se hizo con cada punto</h2>
 <p>Dos tipos de trabajo. Lo que pediste arreglar sin más (<span class="rs-pill d">D</span>) ya está escrito en el código, pero <b>sin compilar</b> (tu orden del 28 de octubre: nada de Gradle hasta que lo pidas). Lo que pediste ver antes de implementar (<span class="rs-pill p">P</span>) está en las demás pestañas de este lienzo: <b>12 paquetes y más de 300 escenas</b>, interactivas donde tiene sentido y con datos de muestra donde no hay datos reales (por ejemplo, las cifras de volumen por músculo del asistente). Cuando lo apruebes se implementa 1:1 y lo que el prototipo no tenga se elimina de la app. <span class="rs-pill dp">D+P</span> son las dos cosas: el defecto se arregla ya y el diseño nuevo queda para aprobar.</p>
 <div class="dtable-wrap"><table class="dtable"><thead><tr><th>#</th><th>Lo que pediste</th><th>Tipo</th><th>Dónde verlo</th></tr></thead><tbody>
 ${RS_PUNTOS.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td><td>${rsPill(r[2])}</td><td>${r[3]}</td></tr>`).join('')}
 </tbody></table></div>`});

scene({tab:'resumen',id:'rs-protocolo',title:'Cómo se garantiza el 1:1',doc:()=>`
 <h2>Cómo se garantiza que la app sea igual que este lienzo</h2>
 <p>Dijiste que no te fiabas de recibir un 1:1 porque los agentes de código nunca aplican algunas animaciones y sombras. Es cierto, y esto es por qué pasaba y qué cambia. Lo que sigue son herramientas y puertas que se pueden comprobar, no promesas. El texto completo está en <code>docs/design/pulido-v1/PROTOCOLO_1A1.md</code>.</p>
 <div class="rs-grid">
  <div><b>Por qué se perdían</b><p>Solo estaba escrito lo que se ve en una captura: las animaciones, los estados de pulsación y las sombras sutiles no figuraban en ninguna lista. La matriz de paridad se hacía a mano y cada pantalla improvisaba su sombra y su curva (el lienzo aprobado tiene 101 sombras distintas, 22 curvas y 91 duraciones). Y la verificación era estática y declarativa.</p></div>
  <div><b>1 · Inventario automático</b><p>Una herramienta lee el CSS real del lienzo y saca <b>más de 700 filas</b>: sombras, desenfoques, degradados, resplandores, animaciones, transiciones, transformaciones y movimiento hecho en JavaScript. Cada fila obliga a poner el archivo y la línea de la app que lo hace, o una desviación registrada. No se pueden borrar.</p></div>
  <div><b>2 · Cimientos antes de las pantallas</b><p>Primero se implementa una vez el catálogo de sombras, vidrio, resplandores, curvas y duraciones, y se calibra comparando su hoja de render con la misma hoja del lienzo. Después los paquetes solo usan esas piezas: nadie inventa una sombra ni una curva por su cuenta.</p></div>
  <div><b>3 · Pasadas con agentes distintos</b><p>Cada paquete pasa por estructura, profundidad (solo sombras, vidrio y degradados), movimiento (solo animaciones y transiciones) y una auditoría independiente. Quien hace una pasada no audita la suya.</p></div>
  <div><b>4 · Cuatro puertas de cierre</b><p>Matriz completa con evidencia · render de todas las escenas comparado píxel a píxel con el lienzo · fotogramas de cada animación (0, 25, 50 y 100 %) comparados con los del lienzo · revisión en tu Flip con captura y vídeo, hecha por mí y no por quien lo implementó.</p></div>
  <div><b>5 · Desviaciones a la vista</b><p>Lo que Android no puede hacer igual se registra con captura lado a lado y se te enseña. No llamo «1:1» a nada que tenga una desviación.</p></div>
 </div>
 <h2>Límites reales</h2>
 <p>Android no tiene <code>box-shadow</code> con desenfoque y extensión arbitrarios: se dibuja en capas y se calibra, y queda muy cerca pero no es matemáticamente idéntico. El vidrio es Haze (radio y tinte equivalentes, no iguales) y en Android anterior al 12 cae a un velo; en tu Flip desenfoca. Las 101 sombras se agrupan en presets por equivalencia visual y esa tabla se te enseña antes de implementar.</p>
 <h2>Qué hace falta de ti</h2>
 <p>Luz verde para Gradle por la cola (compilar, hojas de render y tests de las clases tocadas): sin eso no existen las puertas de render y de fotogramas, y no puedo prometer el 1:1. Y el teléfono, con la regla de 20 minutos, al cerrar cada oleada.</p>`});

scene({tab:'resumen',id:'rs-directos',title:'Lo ya escrito en el código',doc:()=>`
 <h2>Arreglos directos, escritos y sin compilar</h2>
 <p>Cuatro agentes los escribieron y otro releyó los 31 archivos tocados como si fuera el compilador (encontró un error de API y lo corrigió). Nadie ha ejecutado Gradle, tests ni el teléfono: tu orden. Cuando lo pidas, lo primero es compilar y pasar las pruebas del área.</p>
 <div class="dtable-wrap"><table class="dtable"><thead><tr><th>#</th><th>Qué cambia</th><th>Lo que hay que mirar en el teléfono</th></tr></thead><tbody>
  <tr><td>1</td><td>Desaparece «Cargando sesión de entrenamiento». Mientras abre se ve un armazón quieto de la propia sesión (sin giro, texto ni botón). Si abrir falla de verdad: aviso «No se pudo abrir la sesión» con Reintentar y Volver.</td><td>No se cargó antes de navegar (habría tocado todas las entradas a la sesión): si abrir tarda, verás el armazón un instante.</td></tr>
  <tr><td>12</td><td>Atrás, día y ⋯ del editor son tres islas de vidrio con desenfoque real; la lista pasa por debajo. Atrás y ⋯ pasan a círculos de 48 dp (decisión del agente).</td><td>Que el desenfoque se vea de verdad y que el arrastre para ordenar siga acertando con el nuevo hueco superior.</td></tr>
  <tr><td>15</td><td>«Registrar ROM» es una opción por ejercicio en el editor; con ella activa, la tarjeta (y la fila de la Lista) muestran el slider de ROM en el hueco de «Parciales, ayuda y ROM» sin cambiar su tamaño; la hoja de detalle comparte el mismo estado; el historial y el contexto dicen «· ROM 80 %». El slider de la tarjeta era código muerto.</td><td>Ningún cálculo de dominio lee todavía el ROM (comparabilidad, volumen efectivo): hoy se registra y se muestra. Va a una banda de 26 dp; el prototipo dibuja 36.</td></tr>
  <tr><td>17</td><td>Vuelve el botón «Expandir» (y «Plegar») en la cara de Modo visual de la tarjeta.</td><td>Todos los estados de esa cara (sin vídeo, grabando, con análisis).</td></tr>
  <tr><td>19</td><td>Modo Lista: desvanecido de 28 dp pegado a los bordes reales de la pantalla; la cápsula superior es vidrio con desenfoque; ya no hay hueco del dock debajo.</td><td>Que el borde inferior quede bien sobre la barra de navegación y con teclado. Anula a propósito la decisión 47 del Refinamiento 02.</td></tr>
  <tr><td>20</td><td>El micrófono de «Registrar comida» dicta de verdad. Fallaba por tres cosas: cualquier error se mostraba como «No te entendí», el reconocedor forzaba es-CL sin respaldo, y no había botón de parar ni se lanzaba el análisis al terminar. Ahora: permiso y servicio comprobados, español de tu región, reconocedor del dispositivo primero y el del sistema después, texto en vivo en el campo, parar sin perder lo oído, mensajes por tipo de error y análisis al final. El error queda en el registro de diagnóstico.</td><td>Con el servicio de voz real del Flip5 (la causa más probable era el idioma forzado). El tope de dictado es 25 s en el código y 15 s en el prototipo: se iguala al implementar.</td></tr>
  <tr><td>21</td><td>«Nueva medición» es un botón flotante idéntico al de Nutrición (salen los «+» de la cabecera y de la isla; los del estado vacío se quedan). Las esquinas negras de la regla de peso eran un degradado del color de fondo sobre el vidrio de la hoja: ahora es una máscara transparente.</td><td>Ese cambio también afecta a la regla del asistente de bienvenida: míralo allí también.</td></tr>
 </tbody></table></div>`});

scene({tab:'resumen',id:'rs-decisiones',title:'Decisiones que tomé por ti',doc:()=>`
 <h2>Lo que decidí mientras trabajaba</h2>
 <p>No te pregunté nada para no frenar la entrega (tu regla del 28 de octubre). Cada decisión sigue la opción más recomendable para la app; si quieres otra, se cambia antes de implementar.</p>
 <div class="dtable-wrap"><table class="dtable"><thead><tr><th>Área</th><th>Decisión</th></tr></thead><tbody>
  <tr><td>Editor</td><td>Barra inferior: anillo de minutos + «Añadir» partido (toque = catálogo, flecha = ejercicio / cardio / grupo) + Asistente como icono con insignia. «Reglas» pasa a ser la 4.ª pestaña del Asistente y tocar los minutos abre Asistente › Reglas › Tiempo. «Eliminar serie» gana «Deshacer». RIR de 0 a 5. Los kg de corporal, lastre y asistido siguen estimándose con tu récord.</td></tr>
  <tr><td>Asistente</td><td>Pestañas en línea fina con indicador. «Aplicar» solo en Valores. Sugerencias gana «Deshacer» y «Descartar». Volumen por músculo: se mantiene la cifra de siempre. Equilibrio, frecuencia y fatiga no se construyen ahora (no hay motor). Los dos motores de tiempo se unen más adelante.</td></tr>
  <tr><td>Catálogo</td><td>El texto honesto: «Crear una copia» (marcada, la segura) o «Sustituir mi programa». Coan–Philippi, Smolov y Smolov Jr se quedan con aviso (el motor no sabe «sumar a tu plan»). Nombres editoriales. Sin volumen por músculo en la ficha. «Deshacer» solo tras sustituir.</td></tr>
  <tr><td>Cardio · editor</td><td>«Avanzado» desaparece entero (la auditoría muestra que ritmo, FC, vatios, velocidad, inclinación y RPM solo funcionan a medias; el chip HIIT/SIT solo cambiaba el RPE). Los avisos Beeps / Voz / Vibra se guardan por cardio también en Intervalos. Ritmo solo donde tiene sentido en min/km. «Al inicio / Al final» pasa al rótulo «CARDIO · AL FINAL» con una flecha. El «+» del rótulo y «Cardio» en «Añadir» crean otro cardio.</td></tr>
  <tr><td>En vivo · tarjeta</td><td>ROM: vale para esta sesión, con un interruptor «Guardar también en el programa». Rango 10–100 % con zonas en 65 y 95. Sin entrada directa de repeticiones. El destello del esfuerzo solo en la tarjeta. Roadmap: primera propuesta rechazada («aplastado»); la nueva es A de 112 dp (la altura de hoy, dos bandas) y B de 132 dp en pantallas de 820 dp o más (con tira de segmentos), más la hoja «Ruta»; lo que mide el código es 112 dp y no los 138 de la decisión anterior.</td></tr>
  <tr><td>Cardio en vivo</td><td>La tercera página se llama «Datos» (antes «Resumen»). Sin «Conectar reloj» (solo existe en el flavor health). Los parciales por km no se muestran por ahora. Terminar cardio sigue siendo pausa → Terminar.</td></tr>
  <tr><td>Técnica</td><td>El detalle completo tiene scroll interno a altura máxima (lo clave se ve sin él). «Cancelar análisis» descarta. Sin detección de vídeo oscuro (el motor solo dice «No vimos a nadie»). La lectura de velocidad va siempre con los datos. «Reanalizar» visible cuando no hay repeticiones.</td></tr>
  <tr><td>Comida · Cuerpo</td><td>Valor atípico: más de 2,5 kg en 7 días o menos. El botón de Cuerpo no se compacta al hacer scroll (igual que el de Nutrición). Permiso del micrófono: «no guarda el audio».</td></tr>
  <tr><td>Suplementos</td><td>Aviso de techo con «Registrar igual» (no bloquea). Etiquetas de evidencia sí. Proteína en polvo cuenta como suplemento y suma a macros. La pila vive en Ajustes (Room está congelado). Inicio: máximo 3 botones y «+N más».</td></tr>
  <tr><td>Ajustes</td><td>Sale «Descanso por defecto» (queda 90 s fijo donde un ejercicio no trae el suyo) y «Iniciar descanso al registrar una serie» (queda siempre activo). «Mi material» sale de Ajustes y el dato se queda en el asistente. «Unidades» y «Recordatorios» entran porque ya tienen motor. Voz con valores fijos. La copia .db pide confirmación y borrar todo exige mantener pulsado.</td></tr>
  <tr><td>Reparto</td><td>Se conecta el motor que ya existe (<code>SplitRedistributor</code>), hoy solo usado por el asistente de bienvenida. Antes/Después con arrastre, revisión por sesión y por músculo, y Deshacer.</td></tr>
  <tr><td>En vivo · más</td><td>Cronómetro y temporizador viven dentro de la hoja del tiempo de sesión (pestañas Sesión · Cronómetro · Temporizador). «Guardar imagen» pasa a solo icono para que «Compartir en historias» quepa entero. La cápsula de vidrio solo en Lista; Tarjetas sigue plana. El anillo de Duración sin objetivo es una vuelta de reloj de 60 min.</td></tr>
 </tbody></table></div>`});

scene({tab:'resumen',id:'rs-aprobar',title:'Lo que te toca confirmar',doc:()=>`
 <h2>Cosas que solo tú puedes decidir</h2>
 <p>Marcadas con mi recomendación. Lo no listado aquí lo decidí yo (pestaña anterior).</p>
 <div class="rs-ask" style="border-left-color:var(--done)"><b>0 · Roadmap: DECIDIDO · B en tu Flip y A de respaldo</b><p>Elegiste B (132 dp con la tira de segmentos) y yo dejo A (112 dp, dos bandas) como respaldo en pantallas de menos de 820 dp de alto. La «Ruta» (la hoja con todos los ejercicios) es común. En A se pierde el arrastre con vibración del riel de puntos de hoy; en B se conserva en la tira.</p></div>
 <div class="rs-ask"><b>1 · ROM: ¿cuenta para récords y progresión?</b><p>Hoy solo se registra y se muestra. Para que «cuente de verdad» habría que excluir de PR y progresión las series con ROM menor de 90 % y leerlo en la evaluación de series. Recomiendo anotar y mostrar ahora, y decidir la exclusión cuando veas tus propios datos.</p></div>
 <div class="rs-ask"><b>2 · Suplementos: ¿los 14 o menos?</b><p>El catálogo propuesto trae creatina, cafeína, proteína, beta-alanina, citrulina, electrolitos, vitamina D, omega-3, magnesio, zinc, multivitamínico, ashwagandha, hierro y melatonina. Recomiendo quitar ashwagandha, hierro y melatonina (seguridad y evidencia): quedarían 11.</p></div>
 <div class="rs-ask"><b>3 · Ajustes: «Recordatorios» como pantalla nueva</b><p>El motor existe completo (entrenar, comidas, dormir) y nadie puede activarlo hoy. Recomiendo la pantalla. Si no la quieres, esos recordatorios siguen sin existir.</p></div>
 <div class="rs-ask"><b>4 · Ajustes: «Mi material»</b><p>La pantalla sale y el dato se queda en el asistente (el motor lo usa en más de 12 sitios para redondear cargas a tus discos). Lo que pierdes: corregir un solo disco sin reabrir el asistente.</p></div>
 <div class="rs-ask"><b>5 · Textos nuevos que debes leer</b><p>Las fichas de las 12 modalidades de cardio (escena «Textos de las 12 fichas», con cifras de RPE y avisos de seguridad) y las 35 descripciones de los repartos las redactó un agente; no salen del código.</p></div>
 <div class="rs-ask"><b>6 · Asistente solo con icono en la barra inferior</b><p>Un círculo con la insignia de sugerencias, junto al anillo de minutos. Alternativa: texto «Asistente» y sin anillo. Recomiendo el icono: era justo lo que se veía como texto suelto.</p></div>
 <div class="rs-ask"><b>7 · Reparto: ¿motor completo?</b><p>El agente estima unos 10 días de motor y 9 de pantallas (bloqueo de sesiones hechas, término de estabilidad, día preferido entre semanas, decisiones, recorte por tiempo y Deshacer). Se puede ir por fases: primero conectar el motor existente con vista previa y Deshacer.</p></div>
 <div class="rs-ask"><b>8 · Catálogo: «Crear una copia» por defecto</b><p>Es lo que hace el motor hoy cuando el programa tiene sesiones y no sustituyes, y no lo decía la hoja. Cambia el texto prometido.</p></div>
 <div class="rs-ask"><b>9 · Técnica: scroll en el detalle completo</b><p>Pediste cero scroll para ver la información; lo clave está sobre el vídeo y el detalle tiene scroll interno. Si prefieres partirlo en dos alturas, se rehace.</p></div>
 <div class="rs-ask"><b>10 · Ya hecho y distinto de lo que tenía el código</b><p>Roadmap de 112 dp (no 138), ROM en banda de 36 dp (en el código está en 26) y tope de dictado de 25 s (en el prototipo 15): al implementar se alinean al prototipo que apruebes.</p></div>`});

scene({tab:'resumen',id:'rs-motor',title:'Lo que exigirá el motor',doc:()=>`
 <h2>Qué pide implementar cada paquete más allá de la pantalla</h2>
 <p>Room sigue congelado en la versión 31: lo nuevo va en JSON del plan o en Ajustes.</p>
 <div class="dtable-wrap"><table class="dtable"><thead><tr><th>Paquete</th><th>Trabajo de motor o datos</th></tr></thead><tbody>
  <tr><td>Catálogo</td><td>Copia de seguridad también al rellenar (para que «Deshacer» funcione); aplicar un método a un programa vacío sin crear copia; derivar la progresión de todos los métodos (hoy solo dos); que el nombre de la copia use <code>displayName</code>.</td></tr>
  <tr><td>Asistente</td><td>«Deshacer» y «Descartar» en Sugerencias; lista de lo que aporta cada ejercicio a un músculo (hoy solo existe para tres) y volumen de la semana por músculo, con una sola fuente de cálculo; el informe hoy devuelve vacíos el veredicto y los umbrales; rellenar <code>TemplatePreview</code>; arreglar el preset «Tiempo corto» (RPE 8,0 guardado como RIR).</td></tr>
  <tr><td>Cardio · editor</td><td>Guardar Beeps / Voz / Vibra también en Intervalos; opcional <code>supportsPace</code> en el catálogo; sacar del editor el panel «Avanzado» sin migrar datos.</td></tr>
  <tr><td>Cardio en vivo</td><td>Quitar el <code>dockBottomClearance</code> del cardio en <code>WorkoutV2Body</code>; ocultar el título del host durante las transiciones; Canvas de las ondas.</td></tr>
  <tr><td>Tarjeta en vivo</td><td>Leer <code>rom</code> en la evaluación de series (comparabilidad y volumen efectivo); conectar <code>trackRom</code> desde la hoja en vivo (ya hecho en el arreglo directo).</td></tr>
  <tr><td>En vivo · más</td><td>Estado del reloj libre en el ViewModel con guardado de estado; vibración y sonido arbitrados con el descanso; resumen de cardio en el cierre; unificar el separador de miles de <code>finishNumber</code>; <code>WorkoutFinishPages.kt</code> parece código muerto.</td></tr>
  <tr><td>Técnica</td><td>Cancelar análisis; fases y fotograma en curso en el estado del análisis; escala para la velocidad en m/s.</td></tr>
  <tr><td>Suplementos</td><td>No hay modelo: cada toma es una comida con creatina o cafeína. Hace falta guardar la pila (en Ajustes), las tomas como entradas con nutrientes, recordatorios de suplementos y la notificación con acción «Tomar».</td></tr>
  <tr><td>Ajustes</td><td>Dejar de leer <code>restTimerDefaultSeconds</code> y <code>autoStartRestAfterRecording</code>; pantallas de Unidades y Recordatorios; borrar los cuatro archivos de «Mi material»; el código muerto <code>SettingsListItems.kt</code>.</td></tr>
  <tr><td>Reparto</td><td>Bloqueo de sesiones hechas, término de estabilidad y día preferido, fachada <code>plan()</code>, decisiones y recorte por tiempo opcional, <code>Program.lastSplitChange</code> para deshacer. Unos 10 días de motor y 9 de pantallas.</td></tr>
  <tr><td>Editor</td><td>Rehacer los gestos de borrar y ordenar; opcional «Deshacer» al borrar una serie.</td></tr>
 </tbody></table></div>`});
