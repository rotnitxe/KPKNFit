/* ================= DECISIONES ================= */
const T=(rows,h=['Hoy','Propuesta','Por qué'])=>`<div class="dtable-wrap"><table class="dtable"><thead><tr>${h.map(x=>`<th>${x}</th>`).join('')}</tr></thead><tbody>${rows.map(r=>`<tr>${r.map(c=>`<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
scene({tab:'decisiones',id:'d-all',title:'Decisiones',doc:()=>`
<h2>1 · Competencias: qué tipo de registro</h2>
<p>Hoy hay un asistente de cuatro pasos solo para powerlifting que crea el resultado desde cero (con 100 kg de relleno en cada intento), un archivo dentro de Perfil que mezcla próximas y pasadas, una planificación de intentos y un «modo competición» escondidos en el programa, y ninguna forma de borrar, archivar ni compartir.</p>
<div class="advice"><div><div class="verdict">Recomendado</div><h3>Una ficha, tres momentos</h3><p>Cada competencia es <b>una sola ficha</b> que pasa por <b>próxima</b> (la apuntas: fecha, lugar, categoría, pesaje, intentos y avisos), <b>hoy</b> (modo competición que va guardando cada intento) y <b>resultado</b> (lo celebras y lo compartes). Si no la apuntaste antes, «Ya competí» registra el resultado directamente en un minuto.</p></div>
<div><div class="verdict">Por qué así</div><h3>Liviano y alegre</h3><p>El resultado son tres toques por levantamiento porque los pesos ya vienen del plan. Sin podio también hay celebración (tus marcas). El diario se queda en una escala de caras y dos líneas opcionales: lo justo para recordarlo sin que pese.</p></div></div>
${T([
 ['Archivo en Perfil + ruta muerta + aviso en Inicio + módulo en el programa',tg('new','Página «Competencias»')+' con próxima arriba e historial debajo','Un solo sitio; se llega desde Perfil, Inicio y la portada del programa'],
 ['Solo powerlifting','Powerlifting, '+tg('new','un solo levantamiento')+' y '+tg('new','otra disciplina')+' con resultado libre','El asistente de bienvenida ya pregunta «¿Compites?» en seis disciplinas'],
 ['Registro con 100 kg de relleno y ciclo de 4 estados','Pesos desde el plan o tus marcas; un toque alterna válido/nulo','Dos toques por intento era lo más lento del flujo'],
 ['Planificar intentos y modo competición dentro del programa',tg('move','Pasan a la competencia'),'Son de la competencia; el programa enlaza con ella'],
 ['Puesto obligatorio; «4+» se muestra como «4.º»','Opcional; puesto exacto; trofeos como extras','Fallo de presentación y bloqueo innecesario'],
 ['Diario completo sin pantalla que además se borra al reabrir','Caras + «Lo mejor» + «Para la próxima»; nunca se borra','Usa campos que ya existen'],
 ['Sin borrar ni archivar; «Archivar sesión y registro» borra para siempre',tg('fix','⋯ con Archivar y Eliminar reales'),'Pérdida de datos con un nombre engañoso'],
 ['Total que no baja si un válido pasa a nulo',tg('fix','Se recalcula siempre'),'Fallo de cálculo'],
 ['Avisos activos e invisibles; hora nunca pedida','Interruptores y hora del pesaje','El aviso posterior al resultado nunca salta'],
 ['Fotos que quedan huérfanas al quitarlas','Se borran con la foto o la competencia','Espacio del teléfono'],
 ['Sin compartir',tg('new','Historias + vídeo de 6 s'),'Lo pediste'],
])}
<h2>2 · Crear un programa</h2>
${T([
 ['Inicio abre otra hoja (biblioteca vieja, sin «A tu medida»)','Inicio abre la misma hoja «Nuevo» de cuatro caminos','Dos entradas distintas para lo mismo'],
 ['«A tu medida» reutiliza el asistente de bienvenida en modo solo entreno',tg('new','Pantalla completa propia')+': cinco capítulos, propuesta con su porqué y activación','Mismo motor y mismas preguntas: no hay lógica nueva'],
 ['Pide género y % de grasa aunque no haya nutrición','«Ya sabemos» muestra lo conocido; solo pregunta lo que falte para entrenar','Esos datos solo sirven para la nutrición'],
 ['Cada camino activa distinto (uno sin preguntar)',tg('new','Una sola pantalla «Listo para empezar»')+': ahora, en cola o guardar','Nada se activa sin que lo decidas'],
 ['«Configurar este plan» lleva al asistente entero','Abre «A tu medida» con objetivo y días ya puestos','Menos preguntas repetidas'],
 ['Calibración y ficha de protocolo inalcanzables en Programas e Inicio',tg('out','Se retira el código muerto'),'Ya decidido en el lienzo anterior (sale «Calibrar»)'],
 ['Días 1–7 en el asistente y 1–6 desde el catálogo','1–7 en todos','Incoherencia'],
])}
<h2>3 · Registro de comida</h2>
<p>El motor es local y sin conexión, y se queda así. Lo que cambia es cómo se ve y se toca: una animación al analizar que muestra lo que de verdad pasa, tarjetas por alimento con sus dudas dentro, ajustes en la unidad natural y ningún texto técnico.</p>
${T([
 ['«Interpretar comida» / «Actualizar y buscar» (que no busca)','Un solo botón «Analizar» («Volver a analizar» si cambias el texto)','Verbo engañoso'],
 ['Puntos y frases rotatorias genéricas',tg('new','Plato de líneas + palabras que se subrayan al reconocerse'),'Se ve qué está entendiendo'],
 ['Solo se ve la primera duda; chips nunca marcados','Todas las dudas en su tarjeta; respuesta marcada; respuesta por defecto','Dudas ocultas y sin estado'],
 ['Guardar bloqueado si hay dudas','Se guarda con la estimación por defecto de cada duda','Menos fricción, misma transparencia (≈ en la tarjeta)'],
 ['Deslizador de 1 g con tope fijo al abrir','−/+ en la unidad natural; gramos o raciones a un toque','Defecto visual del pulgar al final'],
 ['±5 kcal que no hace nada con 0 kcal','Cifras de macros editables al tocarlas','Botón sin efecto'],
 ['Favoritos, recetas y plantillas apilados sobre las pestañas',tg('move','«Repetir» y atajos de Buscar'),'El campo vuelve a ser lo primero'],
 ['Búsqueda con botón, sin carga ni errores; dos líneas por resultado','Busca al escribir, filas de carga, una línea por resultado, filtros','Más rápido y legible'],
 ['Sin ficha de alimento',tg('new','Ficha')+' con cantidad, macros y nutrientes','Hoy solo se veían tras añadir'],
 ['«Olvidar lo aprendido» borra la comida en curso sin preguntar',tg('fix','Confirmación; no toca el borrador'),'Pérdida de datos'],
 ['Hora como texto «HH:mm»; elegir día reinicia la hora','Atajos, calendario y rueda de hora','Defecto de entrada'],
 ['Avisos con «dataset» y nombres de excepción; icono de información en errores',tg('fix','Qué se entendió y qué hacer'),'Jerga técnica'],
 ['«Agregar» y «Añadir» mezclados','Siempre «Añadir»','Consistencia'],
])}
<h2>4 · Historias</h2>
${T([
 ['Competencia: sin compartir','Total, Intentos, Foto y Próxima; imagen o '+tg('new','vídeo de 6 s')+' con la felicitación','Lo pediste; el vídeo usa el mismo dibujo'],
 ['Cuerpo: solo exportación CSV','Cambio y Medidas; '+tg('keep','sin peso absoluto por defecto'),'Privacidad'],
 ['Nutrición: sin compartir','Semana, Macros y Racha; nunca qué comiste','Datos que ya se calculan'],
],['Hoy','Propuesta','Notas'])}
<h2>5 · Para decidir</h2>
<div class="advice"><div><div class="verdict">Pregunta 1</div><h3>Fotos de progreso del cuerpo</h3><p>La app no guarda fotos del cuerpo (solo de entrenos). Una historia «antes y después» necesita esa función: cámara con guía de pose, privacidad y comparación. ¿La añadimos en otra ronda?</p></div>
<div><div class="verdict">Pregunta 2</div><h3>Vídeo de la historia</h3><p>Exportar vídeo (MP4 grabado cuadro a cuadro) suma trabajo. Recomiendo hacerlo solo para la competencia, que es donde la celebración importa. ¿De acuerdo, o también para el resumen del mes?</p></div></div>`});
