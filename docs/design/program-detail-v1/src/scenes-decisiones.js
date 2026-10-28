/* ================= DECISIONES (documento) ================= */
const T=(rows)=>`<div class="dtable-wrap"><table class="dtable"><thead><tr><th>Elemento actual</th><th>Propuesta</th><th>Por qué</th></tr></thead><tbody>${rows.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td><td>${r[2]}</td></tr>`).join('')}</tbody></table></div>`;
const tg=(k,t)=>`<span class="tag ${k}">${t}</span>`;
scene({tab:'decisiones',id:'d-doc',title:'Decisiones de producto',doc:()=>`
<h2>Refinado tras tu revisión · 27 oct</h2>
${T([
 ['Pestañas dentro de la página e isla con el nombre al hacer scroll',tg('new','Cambiar')+'Barra flotante de tres piezas de vidrio: atrás · píldora de pestañas · ⋯. Siempre arriba; el contenido pasa por debajo, desenfocado.','Pedido tuyo. Las pestañas quedan a mano en cualquier punto del scroll.'],
 ['Módulo «Pendiente» teñido de amarillo entre la portada y la semana',tg('move','A la campana')+'Campana junto a la acción del día con el número en color músculo; abre una hoja de vidrio normal. Al hacer scroll, punto en ⋯ y «Pendiente» como su primera fila. La sesión afectada lleva una marca pequeña («+2,5 kg»).','No empuja la semana y usa los colores de KPKN.'],
 ['«Semana que se repite» (título, subtítulo, alcance, «ciclo N», recordatorio de descarga, aviso «Ciclo activo»)',tg('out','Sale')+'Ese tipo de programa se llama <b>rutina</b> y se navega como cualquier otro: «Semana 7», con flechas y la cinta. Las semanas especiales de sus ciclos se marcan en la cinta, sin texto.','Eran aclaraciones obvias que ocupaban espacio.'],
 ['10 portadas de píxeles en 3 paletas',tg('new','Cambiar')+'8 estilos generativos en 5 paletas (pestaña Portadas). Líneas finas sobre fondo oscuro; el color solo difuminado. Perfil, Espiral y Eco se dibujan con los datos del programa.','Pedido tuyo: portadas bonitas y modernas.'],
])}

<h2>Extras pedidos · 27 oct</h2>
${T([
 ['Compartir en historias',tg('new','Nuevo')+'Botón junto a la acción del día, en ⋯ del programa y en ⋯ de la lista. Imagen 9:16 con la portada de líneas, el nombre y tres plantillas: Semana (reparto por día), Bloques (fases y métodos) y Progreso (lo ganado, con 4 semanas de datos o más).','Directo a la cámara de historias de Instagram si está instalado; si no, el menú de compartir del sistema. Nunca incluye peso corporal, molestias ni notas.'],
 ['Resumen del mes',tg('new','Nuevo')+'Aviso en Inicio el día 1 («Tu octubre está listo») que abre 10 pantallas animadas a pantalla completa: constancia, trabajo total, fuerza, mejor marca, músculos, esfuerzo, a vigilar, lo que viene y tarjeta para compartir.','Usa los cálculos de Progreso acotados al mes natural. Se guarda en Progreso › «Resúmenes mensuales». Necesita al menos 4 sesiones en el mes.'],
])}

<h2>La idea en cuatro líneas</h2>
<p><b>Portada</b>: quién es el programa, en qué semana vas y qué toca hoy. Nada más. Lo que pide una decisión vive en la campana.</p>
<p><b>Semana</b>: una agenda. Cada día tiene su fecha a la izquierda y sus sesiones apiladas a la derecha, a la vista sin desplegar nada.</p>
<p><b>Progreso</b> (sustituye a Volumen): lo que pasó de verdad. Si subes, dónde estás estancado, cuánto trabajo hiciste y si cumples el plan.</p>
<p><b>Estructura</b>: editar el plan (bloques, semanas, calendario, fechas, historial) y su revisión de diseño (equilibrio y cobertura), que hoy está mezclada con el volumen.</p>

<h2>Program Detail · portada</h2>
${T([
 ['«6 semanas · Músculo» y «Por bloques · 3 bloques · 6 semanas»',tg('move','Fusionar')+'Una línea: «Músculo · 6 semanas · 3 bloques».','Repite «6 semanas» y «Por bloques» no aporta.'],
 ['Barra gris de segmentos + «Semana 1 de 6 · Acumulación»',tg('new','Cambiar')+'Cinta de fases: cada semana con el color de su fase; la actual, más alta.','Se lee la forma del programa de un vistazo.'],
 ['Botón lápiz junto a Pausar',tg('out','Sale')+'«Editar programa» queda en ⋯.','Duplicado: ⋯ ya lo tiene.'],
 ['Módulo «Sobre este programa» (procedencia, notas, «Ver más», «Ver cómo funciona»)',tg('move','A ⋯')+'Hoja «Sobre el programa»: descripción, cómo progresa, material y, al final, el origen.','No se consulta cada día; la procedencia es relleno en la portada.'],
 ['Módulo «Progreso del programa» (fase, «Semana 1 de 6», cumplimiento, sesiones, decisiones)',tg('move','A Progreso'),'Repite la cabecera. Sus cifras viven en la pestaña Progreso.'],
 ['Hasta 8 avisos en módulos separados: continuación, revisión de bloque, check-in semanal, sesiones perdidas o inactividad, banner de transición y 1RM, propuestas de autorregulación, progresiones nativas (con insignia en otras pestañas), TM que falta',tg('move','Fusionar')+'Campana «Pendiente» con su hoja (ver Refinado).','Hoy pueden apilarse 3 o 4 módulos antes de llegar a la semana. Cada fila abre la misma hoja de hoy: la lógica no cambia.'],
 ['Competición',tg('keep','Se queda')+'Módulo compacto: cuenta atrás, intentos y sus acciones.','Cambia lo que haces esas semanas.'],
 ['«Temporada» (objetivo calórico y mantenimiento)',tg('out','Sale')+'Sale de Program Detail (aprobado).','Es dato de Nutrición; aquí no se puede actuar sobre él.'],
 ['«Próxima semana · series según el plan»',tg('move','A Progreso')+'Progreso › Volumen con alcance «Semana 5».','Duplica el volumen planificado.'],
])}

<h2>Program Detail · Semana</h2>
${T([
 ['Módulo «Fases» (una fila de círculos por bloque)',tg('move','A hoja')+'Navegador «‹ Semana 4 ›» con cinta fina; al tocar el título, hoja «Semanas» con las mismas filas, «+» y pulsación larga.','Ocupaba media pantalla antes del primer día.'],
 ['Panel de TM por ejercicio («TM 100 kg», «Editar TM», «Sin procedencia registrada»)',tg('move','A Estructura')+'Estructura › «Cargas de referencia». Si falta un TM, sale en «Pendiente».','No se edita cada semana; «Sin procedencia registrada» en cada fila es relleno.'],
 ['Propuestas de autorregulación dentro de la Semana',tg('move','A Pendiente'),'Una sola entrada para todo lo que hay que decidir.'],
 ['Cabecera de semana con nombre, descripción, lápiz y «Cambiar reparto»',tg('move','Reordenar')+'Título en el navegador; editar, reparto, copiar, pegar y añadir semana en ⋯ de la semana.','Cuatro controles sueltos para tareas poco frecuentes.'],
 ['Día = fila de 64 dp plegada; hay que desplegarla para ver la sesión',tg('new','Cambiar')+'Agenda: la sesión se ve sin tocar. Varias sesiones se apilan con «1 de 2» y el total del día. Descansos seguidos en una línea.','Lo que pediste: ordenada y con varias sesiones por día.'],
 ['Tres cifras de la semana',tg('new','Nuevo')+'Sesiones hechas, series hechas/planificadas, tiempo total.','Datos que ya existen; hoy no se muestran juntos.'],
])}

<h2>Program Detail · Volumen → Progreso</h2>
${T([
 ['Series por músculo (planificadas) con zonas y desglose',tg('keep','Se queda')+'Por defecto <b>hecho</b>; conmutador «Hecho / Plan».','Para progreso importa lo hecho; el plan se sigue viendo.'],
 ['Invitación «Sin calibrar», botón «Calibrar», módulo «Tu volumen · Objetivos de planificación»',tg('out','Sale')+'Una fila «Rangos: perfil del asistente» que abre la hoja «Rangos de volumen».','Ver la asesoría de abajo.'],
 ['Resumen (series totales, músculos activos, más cargado, reparto)',tg('move','Repartir')+'Series totales en las cifras de Progreso; reparto a Revisión del plan.','«Más cargado» y «Músculos activos» no ayudan a decidir.'],
 ['Tarjeta de análisis (balance y cobertura, músculos olvidados, empuje/tirón, quad/posterior, top volumen, reps por músculo, directo/indirecto, patrones, unilateralidad, estabilidad)',tg('move','A Estructura')+'Estructura › «Revisión del plan»: primero los avisos («No hay trabajo directo de pantorrillas»), después las proporciones.','Evalúan el diseño del plan, no tu progreso.'],
 ['«Ver lo que has hecho en 4 semanas»',tg('keep','Se queda')+'Enlace al final de Volumen.',''],
 ['Molestias y su relación con ejercicios',tg('keep','Se queda')+'Módulo propio en Progreso.',''],
 ['—',tg('new','Nuevo')+'Fuerza por e1RM con estado (sube, estancado, baja), trabajo total por semana frente al plan, tonelaje, cumplimiento por sesión, RIR medio frente al plan, decisiones en línea de tiempo.','Lo que pediste: progreso, estancamiento y trabajo total.'],
])}

<h2>Lista de programas y crear</h2>
${T([
 ['Tarjeta grande «Crear mi programa a medida» siempre visible',tg('move','A «Nuevo»')+'Primera opción de la hoja «Nuevo programa».','Empuja la lista hacia abajo aunque ya tengas programa.'],
 ['Tarjeta activa: «N semanas · M sesiones»',tg('new','Cambiar')+'Semana actual, cinta de fases, «Hoy: sesión» y «Ver programa».','Es lo que buscas al entrar.'],
 ['Programa encolado repetido en la cola y en la lista',tg('out','Arreglar')+'Solo en «En cola».','Fallo actual.'],
 ['Pulsación larga → diálogo «Editar / Añadir a la cola / Archivar / Cancelar»',tg('move','A ⋯')+'Hoja con iconos; la pulsación larga sigue funcionando.','Nadie descubre la pulsación larga.'],
 ['Deslizar para borrar, sin confirmación, también el activo',tg('out','Sale')+'Borrar en ⋯ con confirmación.','Borrado accidental.'],
 ['Archivados inaccesibles si todo está archivado',tg('out','Arreglar')+'Fila «Archivados · N» siempre que haya alguno.','Fallo actual: no se puede restaurar.'],
 ['Hoja «Nuevo programa»: botón «Crear desde cero» + tarjeta «Desde cero» + «Importar» + filtros + planes',tg('move','Reordenar')+'Cuatro caminos: A tu medida, Catálogo, Importar, Desde cero. El catálogo pasa a página propia con filtros.','Dos botones hacen lo mismo y la lista de planes no cabe en una hoja.'],
 ['Filtro «Procedencia» al mismo nivel que Perfil, Días y Duración',tg('move','A filtros')+'Dentro de la hoja de filtros.','Interesa a pocos.'],
 ['«Desde cero» crea «Nuevo programa N» sin preguntar',tg('ask','Para decidir')+'Hoja corta: nombre, modalidad y portada → «Crear y abrir».','El texto promete nombre y no lo pide; el número se repite.'],
 ['TM: «Más tarde»',tg('new','Texto')+'«Crear sin TM».','Es lo que hace.'],
])}

<h2>Asesoría: calibración de volumen</h2>
<div class="advice">
<div><div class="verdict">1 · ¿Existe «calibrar» un volumen recomendado?</div><h3>Existen rangos de referencia. Calibrar de verdad solo se puede con tus datos.</h3>
<p>Lo sólido: más series semanales por músculo dan más hipertrofia, con rendimientos decrecientes. La fuerza se satura antes. Lo dicen los metaanálisis de dosis y respuesta (Schoenfeld y col., 2017; Pelland y col., 2024).</p>
<p>Los rangos tipo mínimo efectivo y máximo recuperable (MEV, MAV, MRV, popularizados por Renaissance Periodization) son <b>heurísticas útiles</b>, no medidas. Ningún cuestionario predice cuántas series tolera una persona: la respuesta individual varía mucho, y la técnica o la movilidad no la predicen.</p>
<p>Lo que sí funciona es <b>ajustar con lo que pasa</b>: si a igual carga haces más repeticiones, cómo evoluciona el RIR, cómo te recuperas y si aparecen molestias, semana a semana. KPKN ya tiene esa base: el check-in semanal y las propuestas de rangos al cerrar un ciclo.</p>
<p>Lo que hace hoy la hoja «Calibrar»: suma cuatro respuestas de 1 a 3, elige entre 12 y 18 series según estilo y nivel, y escala una tabla por músculo. Mueve los rangos alrededor de un 20 %. Es un buen <b>punto de partida</b>, no una calibración.</p></div>
<div><div class="verdict">2 · ¿Hace falta si el asistente ya pregunta?</div><h3>No. Es la misma función con las mismas cinco preguntas.</h3>
<p>El asistente y la hoja de Program Detail llaman a <code>VolumeCalibrationEngine.calculate(estilo, técnica, constancia, fuerza, movilidad)</code>. Repetirlo solo puede dejar <b>dos perfiles distintos</b> para la misma persona según dónde respondió.</p>
<p><b>Recomendación:</b></p>
<p>1. Una sola fuente: el perfil del asistente, para todos tus programas.</p>
<p>2. En el programa, la hoja «Rangos de volumen» dice de dónde salen, deja elegir rangos propios por músculo (ya existe) y muestra las propuestas basadas en tus semanas (ya existe).</p>
<p>3. Si un programa no tiene perfil (importado, o el asistente se saltó), usa la referencia general y ofrece «Responder las preguntas», que abre <b>el mismo paso del asistente</b>, sin cuestionario aparte.</p>
<p>4. La palabra «calibrar» se reserva para el ajuste con tus datos. Para lo demás, «rangos».</p><p>5. Ojo: la primera pregunta de esa hoja («Objetivo principal») también <b>cambia la modalidad del programa</b>. Al quitarla, la modalidad pasa a «Editar programa», donde ya la tiene el editor de programas.</p></div>
</div>
<p>Se conserva el orden actual de prioridad: rangos propios del programa, después los del perfil y, si no hay, la referencia general (<code>effectiveRecommendations</code>).</p>

<h2>Menús escondidos</h2>
${T([
 ['Dos menús de semana distintos para el mismo gesto (Semana: copiar y pegar; Estructura: editar, copiar, pegar aquí y eliminar) + lápiz y «Cambiar reparto» sueltos',tg('move','Unificar')+'Un solo ⋯ de semana en las dos pestañas.','El mismo gesto hace cosas distintas según la pestaña.'],
 ['⋯ «Historial» que solo cambia a la pestaña Estructura',tg('out','Arreglar')+'«Versiones guardadas» abre la hoja de verdad.','Hoy promete algo que no hace.'],
 ['Menú del día con una sola opción, que no hace nada en programas con fechas',tg('move','Ampliar')+'Añadir sesión, cambiar la fecha y empezar la semana este día.','Fallo y opciones dispersas.'],
 ['Fecha del día como texto «AAAA-MM-DD» sin validar',tg('new','Cambiar')+'Calendario de mes.','Se puede escribir cualquier cosa.'],
 ['⋯ de la sesión con solo «Eliminar sesión»',tg('ask','Para decidir')+'Añadir «Mover a otro día» y «Restaurar desde el plan».','Mover hoy solo se puede arrastrando.'],
 ['Diálogo automático «Calibrar volumen del programa» al abrir un programa',tg('out','Sale'),'Repite el asistente (ver asesoría).'],
 ['«Más tarde: el programa espera tu decisión» y «Omitir» en el banner del test',tg('move','A la hoja')+'«Omitir» dentro de «Registrar tus máximos»; cerrar ya es «más tarde».',''],
 ['«No volver a preguntar» del check-in: interruptor siempre apagado que desactiva para siempre',tg('new','Cambiar')+'Acción con confirmación, reversible en ajustes.','Hoy no se puede deshacer.'],
 ['Diálogos con piel de Material (aviso de calibrar, eliminar competición, insignia de progresiones, chips del cambio de ejercicio, competición y check-in)',tg('new','Cambiar')+'Hojas y controles KPKN.','Un solo lenguaje visual.'],
])}

<h2>Lógica nueva que esta propuesta necesita</h2>
${T([
 ['Estado de fuerza (sube, estancado, baja)','Estancado: el mejor e1RM de las 3 últimas sesiones del ejercicio no supera en más de un 1 % al de las 3 anteriores, con al menos 3 semanas de datos. Baja: cae más de un 2,5 %. Menos de 4 sesiones: «Sin tendencia».','Motor puro en <code>domain/training</code> con pruebas; umbrales ajustables.'],
 ['Trabajo por semana','Series de trabajo hechas frente a planificadas, tonelaje (solo carga externa), repeticiones y tiempo.','Agregación de registros que ya existen.'],
 ['RIR medio frente al plan','Media del RIR registrado de la semana frente al pedido.','Solo con RIR registrado; si no hay, «—».'],
 ['Orden de «Pendiente»','Bloqueantes → propuestas del motor → preguntas → futuro.','Solo orden de presentación.'],
])}

<h2>Fallos encontrados de paso</h2>
<p><b>Lista:</b> si todo está archivado, sale «Comienza Hoy» y no deja restaurar. «Nuevo programa N» puede repetir nombre. Un programa en cola sale dos veces. Deslizar borra sin confirmar, incluso el activo. En Programas hay dos hojas de calibración que nunca se abren.</p>
<p><b>Detalle:</b> lo copiado de una semana se pierde al cambiar de pestaña. «Añadir semana» tiene dos implementaciones (Semana y Estructura) que pueden dar nombres distintos. La hoja de pegar no recibe la semana destino. «Eliminar sesión» sin bloque o semana cierra sin borrar ni avisar. Las hojas de revisión de bloque y de continuación se quedan en blanco si la propuesta llega vacía. «Elegir catálogo» abre la hoja de «Repetir el programa». Las notas de «Calibrar» no se guardan en el programa. Las descripciones de los estilos de la hoja «Calibrar» nunca se pintan.</p>
<p>Estos arreglos van en el mismo paquete que su pantalla; no hace falta que los decidas.</p>

<h2>Aprobado el 27 oct</h2>
<p>Todo lo anterior, con las siete recomendaciones: «Temporada» sale de Program Detail; sale el cuestionario «Calibrar» y su aviso automático, queda «Rangos» y la modalidad pasa a «Editar programa»; «Activar programa» en el ⋯ de la lista; «Desde cero» pide nombre; regla de estancamiento de 3 contra 3 sesiones (+1 % / −2,5 %); la revisión del plan va a Estructura; «Mover a otro día» y «Restaurar» en el ⋯ de la sesión.</p>
<p>El resto de pestañas muestra cada pantalla y cada menú con estos cambios aplicados.</p>`});
