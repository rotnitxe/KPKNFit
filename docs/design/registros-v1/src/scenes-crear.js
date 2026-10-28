/* ================= CREAR PROGRAMA (v3: pantallas REALES de la app) ================= */
// Todo lo que se ve aquí sale de la app: renders de Compose (NewProgramRealRenderSheetTest y las hojas del importador),
// no imitaciones. Los «ni_*» son el asistente real con dos retoques de la instancia «Nuevo programa» (etiqueta de la
// cabecera y la fila plegada «Lo que ya sabemos»); «ni_13_listo» se compone con piezas reales del mismo asistente.
const realImg=(k,alt='')=>IMG[k]?`<img src="${IMG[k]}" alt="${alt}" draggable="false">`:`<div class="realmiss">Falta la captura ${k}</div>`;
// Pantalla completa: la captura entera cabe bajo la barra de estado (se reduce un poco, el fondo es negro igual).
const realFull=(k)=>`<div class="realfull">${realImg(k)}</div>`;
// Pantalla larga: se desplaza; `head` es una captura de cabecera que se queda fija arriba.
const realScroll=(k,head)=>`<div class="realscroll">${head?`<div class="realhead">${realImg(head)}</div>`:''}${realImg(k)}</div>`;
// Hoja real sobre la cabecera real de Programas.
const realSheet=(k,extra='')=>`<div class="realscroll">${realImg('e_cabecera_programas_360')}</div>${sheet(`<div class="realsh">${realImg(k)}</div>${extra}`)}`;
const REAL_TAG=['keep','Captura real'];
const INST_TAG=['new','Instancia «Nuevo programa»'];
const step=(id,k,title,p,tags=[REAL_TAG,INST_TAG])=>scene({tab:'crear',id,title,html:()=>realFull(k),notes:{tags,p:[].concat(p)}});

// ---- Punto de partida
scene({tab:'crear',id:'cr-new',title:'Nuevo programa · cuatro caminos',html:()=>realFull('pr_01_nuevo'),
 notes:{tags:[REAL_TAG,['keep','Ya implementada (P6b)']],
  p:['La hoja «Nuevo programa» tal como está hoy en la rama: <b>A tu medida</b>, <b>Del catálogo</b>, <b>Importar</b> y <b>Desde cero</b>. Inicio abrirá esta misma hoja.',
     'Las escenas siguientes recorren los cuatro caminos con pantallas reales. Todos terminan igual: «Listo para empezar», donde eliges si el programa empieza ya, va en cola o se guarda sin activar.']}});

// ---- A tu medida = el asistente de bienvenida, en su instancia
scene({tab:'crear',id:'cr-hoy',title:'Hoy, al llegar a KPKN: datos básicos',html:()=>realFull('wz_00_inicio'),
 notes:{tags:[REAL_TAG,['fix','Se salta en «Nuevo programa»']],
  p:['Así arranca hoy el asistente de bienvenida (capítulo «Datos básicos»: alias, nacimiento, altura y peso, sexo, grasa).',
     'En «Nuevo programa» este capítulo <b>no se vuelve a preguntar</b>: KPKN ya lo sabe. Se pliega en una sola fila «Lo que ya sabemos», editable con el lápiz, y el asistente empieza directamente en el capítulo Entreno (siguiente escena).']}});
step('cr-wz01','ni_01_experiencia','A tu medida · 1 · experiencia',
 ['Es el asistente real, paso a paso. Lo único que cambia la instancia: la cabecera dice <b>«NUEVO PROGRAMA · n/m»</b> en vez de «ENTRENO» y arriba aparece <b>«Lo que ya sabemos · Matías · 34 años · 1,78 m · 80,4 kg»</b> plegado (con el mismo check y lápiz que cualquier respuesta confirmada).',
  'La flecha vuelve al paso anterior; la X sale y guarda el borrador (al volver a «A tu medida» retoma donde estabas).']);
step('cr-wz02','ni_02_lugar','A tu medida · 2 · dónde entrenas','Mismo paso y mismos símbolos de línea del alta.');
step('cr-wz03','ni_03_material','A tu medida · 3 · material','Igual que en el alta. Si el material ya se respondió antes, viene marcado y basta con confirmar.');
step('cr-wz04','ni_04_objetivo','A tu medida · 4 · objetivo','Perfiles generales y disciplinas, con los mismos símbolos. Las disciplinas dependen del material.');
step('cr-wz05','ni_05_compites','A tu medida · 5 · ¿compites?',
 ['Igual que en el alta. Única novedad: con «Sí, tengo fecha», además de armar el programa hacia ese día, se crea la competencia como <b>próxima</b> en Competencias (pestaña Competencias de este lienzo).']);
step('cr-wz06','ni_06_energia','A tu medida · 6 · día con más energía','Tu sesión más fuerte caerá ese día.');
step('cr-wz07','ni_07_dias','A tu medida · 7 · qué días','Días de la semana, con el día fuerte marcado y el inicio de semana.');
step('cr-wz08','ni_08_tiempo','A tu medida · 8 · tiempo por sesión','El programa se ajusta al tiempo.');
step('cr-wz08b','ni_08b_tecnica','A tu medida · prioridades · técnica','Los pasos de prioridades aparecen según el objetivo, igual que en el alta.');
step('cr-wz08c','ni_08c_constancia','A tu medida · prioridades · constancia','Igual que en el alta.');
step('cr-wz08d','ni_08d_fuerza','A tu medida · prioridades · fuerza','Igual que en el alta.');
step('cr-wz08e','ni_08e_movilidad','A tu medida · prioridades · movilidad','Igual que en el alta.');
step('cr-wz09','ni_09_musculos','A tu medida · músculos que priorizas','Hasta cinco, con las figuras de líneas del asistente. Se puede omitir.');
step('cr-wz10','ni_10_marcas','A tu medida · tus marcas','1RM o mejor serie, con la regla deslizante. Se puede omitir; si el programa usa porcentajes, salen de aquí.');
step('cr-wz11','ni_11_plan_propuestas','A tu medida · elige tu programa',
 ['El paso PLAN real: la propuesta «Para ti · Hecho a tu medida» con su portada de líneas, otras versiones al deslizar, «Ver detalles» y «Elegir».']);
step('cr-wz12','ni_12_semana','A tu medida · así queda tu semana','Las sesiones reales del programa elegido; se arrastran por el asa o se tocan para cambiarlas de día.');
scene({tab:'crear',id:'cr-listo',title:'Todos los caminos · listo para empezar',html:()=>realFull('ni_13_listo'),
 notes:{tags:[['new','Cierre de la instancia'],['fix','Nada se activa sin preguntar']],
  p:['Sustituye a «Revisión y activación» del alta. Está <b>compuesto con piezas reales</b> del asistente: la cabecera, las filas plegadas con check y lápiz, las filas de opción con su radio y el botón redondo de confirmar. Solo el texto es nuevo.',
     'Si ya hay un programa en curso: <b>Empezar ahora</b> (pausa el actual), <b>Ponerlo en cola</b> (por defecto) o <b>Guardarlo sin activar</b>. Si no hay ninguno, la opción por defecto es «Empezar ahora».',
     'Lo usan los cuatro caminos: asistente, catálogo, importar y desde cero. Hoy un plan del catálogo se activa sin preguntar y el resto no se activa.']}});

// ---- Del catálogo
scene({tab:'crear',id:'cr-cat1',title:'Del catálogo · la lista',html:()=>realFull('pr_02_catalogo'),
 notes:{tags:[REAL_TAG,['keep','Ya implementada (P6b)']],
  p:['«Del catálogo» abre la página de catálogo real: filtros por objetivo, días y duración, y cada plan con su portada, días, semanas, nivel y fuente (Plan KPKN, Original fiel…).']}});
scene({tab:'crear',id:'cr-cat2',title:'Del catálogo · cómo funciona',html:()=>realFull('pr_03_como_funciona'),
 notes:{tags:[REAL_TAG],
  p:['Al tocar un plan: «Qué haces», «Tu semana tipo», «Fuente» y «Glosario». <b>Configurar este plan</b> abre el asistente (siguiente escena).']}});
step('cr-cat3','ni_07_dias','Del catálogo · el asistente, solo lo que falta',
 ['«Configurar este plan» abre <b>el mismo asistente</b> en la instancia «Nuevo programa», con el plan ya elegido y el objetivo resuelto: solo pregunta lo que el plan necesita y KPKN no sabe (días, tiempo, material). Lo ya conocido sale plegado arriba.',
  'Después: «Así queda tu semana», los máximos si el plan usa porcentajes y «Listo para empezar».']);
scene({tab:'crear',id:'cr-cat4',title:'Del catálogo · máximos de entrenamiento',html:()=>realFull('pr_05_maximos'),
 notes:{tags:[REAL_TAG,['keep','Ya implementada (P6b)']],
  p:['Para planes con porcentajes (5/3/1, etc.): 1RM por levantamiento con la regla del asistente; el TM sale al 90 %. «Crear sin TM» deja las cargas vacías. Luego, «Listo para empezar».']}});

// ---- Importar
scene({tab:'crear',id:'cr-imp1',title:'Importar · de dónde',html:()=>realSheet('a_tres_simbolos_foto_411'),
 notes:{tags:[REAL_TAG],p:['Tres fuentes: elegir archivo (Excel, CSV, Word, texto o PDF), pegar texto o una foto de la rutina (hacerla o elegirla de la galería).']}});
scene({tab:'crear',id:'cr-imp2',title:'Importar · leyendo',html:()=>realSheet('c_progreso_cancelar_411'),
 notes:{tags:[REAL_TAG],p:['Mientras lee: progreso por páginas y «Cancelar». Al terminar abre la revisión.']}});
scene({tab:'crear',id:'cr-imp3',title:'Importar · revisión',html:()=>realFull('a_vista_previa_barra_360'),
 notes:{tags:[REAL_TAG],p:['La revisión real: título, unidad de las cargas (kg o lb), semanas y días con cada ejercicio reconocido o propio, y la barra «Añadir ejercicio» / «Guardar programa».']}});
scene({tab:'crear',id:'cr-imp4',title:'Importar · avisos',html:()=>realFull('c_avisos_desplegados_360'),
 notes:{tags:[REAL_TAG],p:['Todo lo que no se pudo leer bien, con la fila de origen y una acción por aviso (Añadir como ejercicio, Revisar, Entendido, Ignorar).']}});
scene({tab:'crear',id:'cr-imp5',title:'Importar · lo que falta decidir',html:()=>realScroll('c_review_three_problems'),
 notes:{tags:[REAL_TAG],p:['Deshacer tras quitar algo, avisos pendientes y decisiones que faltan («Falta decidir «Remo con barra»»).']}});
scene({tab:'crear',id:'cr-imp6',title:'Importar · ajustar un ejercicio',html:()=>realScroll('a_panel_kg_rpe'),
 notes:{tags:[REAL_TAG],p:['El panel real para ajustar un ejercicio importado: propio o del catálogo, series y repeticiones, carga, esfuerzo, descanso y notas.']}});
scene({tab:'crear',id:'cr-imp7',title:'Importar · menú de la semana',html:()=>realScroll('b_menu_semana'),
 notes:{tags:[REAL_TAG],p:['Duplicar semana, añadir día o quitar la semana, directamente en la revisión.']}});
scene({tab:'crear',id:'cr-imp8',title:'Importar · guardado',html:()=>realSheet('d_review_ready','<p class="cap" style="margin:14px 2px 0">Después: «Listo para empezar».</p>'),
 notes:{tags:[REAL_TAG],p:['Confirmación al guardar («Listo: 1 semana · 2 días · 2 ejercicios») y paso a «Listo para empezar», el mismo cierre que el asistente.']}});

// ---- Desde cero
scene({tab:'crear',id:'cr-cero',title:'Desde cero',html:()=>realFull('pr_04_desde_cero'),
 notes:{tags:[REAL_TAG,['keep','Ya implementada (P6b)']],
  p:['Nombre (sugerido), modalidad y portada de líneas. «Crear y abrir» lleva al programa vacío para armar semanas y sesiones; si hay uno en curso, antes pasa por «Listo para empezar».']}});
