/* ---- rp-data.js ---- */
const RP_SPLITS=[{"id":"ul_x4","n":"Torso y pierna, 4 días","orig":"Upper / Lower x4","d":"Dos sesiones de torso y dos de pierna. Cada músculo dos veces por semana, con 48 a 72 h de recuperación.","t":["rec","bal"],"p":["Torso","Pierna","Descanso","Torso","Pierna","Descanso","Descanso"],"lv":"I","pro":["Frecuencia 2x/semana óptima","48-72h recuperación"],"con":["Requiere 4 días mínimos"],"old":"El estándar de oro. Equilibrio perfecto."},
{"id":"ppl_ul","n":"Empuje, tirón, pierna y torso","orig":"PPL + Upper/Lower","d":"Torso y pierna al empezar; luego empuje, tirón y pierna. Cinco días que combinan frecuencia y volumen.","t":["rec","av","bal"],"p":["Torso","Pierna","Descanso","Empuje","Tirón","Pierna","Descanso"],"lv":"I","pro":["Combina frecuencia UL con PPL","5 días manejables"],"con":["Coordinación compleja"],"old":"Híbrido de 5 días. Volumen y frecuencia."},
{"id":"fullbody_x3","n":"Cuerpo completo, 3 días","orig":"Full Body x3","d":"Tres sesiones de cuerpo completo con un día libre entre cada una. Cada músculo tres veces por semana.","t":["rec","af"],"p":["Cuerpo Completo A","Descanso","Cuerpo Completo B","Descanso","Cuerpo Completo C","Descanso","Descanso"],"lv":"P","pro":["Frecuencia 3x/semana","Ideal para aprendizaje motor"],"con":["Volumen por sesión limitado"],"old":"Alta frecuencia muscular con pocos días de entrenamiento."},
{"id":"ppl_x6","n":"Empuje, tirón y pierna, 6 días","orig":"Push Pull Legs x6","d":"Empuje, tirón y pierna, dos veces cada uno. El máximo volumen y la mayor fatiga: para quien ya recupera bien.","t":["af","av","at"],"p":["Empuje","Tirón","Pierna","Empuje","Tirón","Pierna","Descanso"],"lv":"A","pro":["Volumen máximo","Frecuencia 2x/semana"],"con":["6 días requeridos","Fatiga extrema"],"old":"Máximo volumen. Solo expertos."},
{"id":"ul_x6","n":"Torso y pierna, 6 días","orig":"Upper / Lower x6","d":"Torso y pierna, tres veces cada uno. Frecuencia 3 por músculo; hay que vigilar la fatiga.","t":["af","at","av"],"p":["Torso","Pierna","Torso","Pierna","Torso","Pierna","Descanso"],"lv":"A","pro":["Frecuencia 3x/semana por músculo"],"con":["Gestión de fatiga CRÍTICA","Solo avanzados"],"old":"Frecuencia 3 por grupo muscular."},
{"id":"ppl_arnold","n":"Empuje, tirón y pierna con énfasis","orig":"PPL + Arnold","d":"Empuje, tirón y pierna, más un día de pecho y espalda y otro de hombro y brazo. Mucho volumen y énfasis estético.","t":["av","bal","at"],"p":["Empuje","Tirón","Pierna","Pecho/Espalda","Hombro/Brazo","Pierna","Descanso"],"lv":"A","pro":["Énfasis estético","Brazos y hombros dedicados"],"con":["5-6 días requeridos","Volumen de empuje alto"],"old":"PPL + Pecho/Espalda + Hombro/Brazo."},
{"id":"phat_hybrid","n":"Torso, pierna y cuerpo completo","orig":"UL x2 + Cuerpo Completo","d":"Dos días de torso, dos de pierna y un cierre de cuerpo completo para los puntos débiles.","t":["bal","af"],"p":["Torso","Pierna","Descanso","Torso","Pierna","Cuerpo Completo","Descanso"],"lv":"I","pro":["Frecuencia 2-3x/semana","Día 6 para puntos débiles"],"con":["El día full body puede acumular mucha fatiga"],"old":"Híbrido de 5 días con un cierre full body para puntos débiles."},
{"id":"ant_post_x4","n":"Cadena anterior y posterior, 4 días","orig":"Anterior / Posterior x4","d":"Divide el cuerpo por cadenas: anterior y posterior, dos veces cada una.","t":["rec","bal"],"p":["Cadena Anterior","Cadena Posterior","Descanso","Cadena Anterior","Cadena Posterior","Descanso","Descanso"],"lv":"I","pro":["Enfoque funcional","Frecuencia 2x/semana"],"con":["Menos común"],"old":"Enfoque en cadenas musculares."},
{"id":"arnold_ul","n":"Estético y torso/pierna","orig":"Arnold + Upper / Lower","d":"Pecho y espalda, pierna, hombro y brazo, y cierre de torso y pierna. Casi todo a frecuencia 2.","t":["rec","bal","av"],"p":["Pecho/Espalda","Pierna","Descanso","Hombro/Brazo","Upper","Lower","Descanso"],"lv":"I","pro":["Frecuencia 2x para la mayoría de grupos","Buen balance entre estética y estructura","5 días con 2 descansos"],"con":["Requiere tolerancia media-alta","Puede cargar hombros y brazos si se abusa del volumen"],"old":"Híbrido estético con cierre Upper/Lower para frecuencia 2 y buen volumen."},
{"id":"ant_post_x6","n":"Cadena anterior y posterior, 6 días","orig":"Anterior / Posterior x6","d":"Cadena anterior y posterior, tres veces cada una. Frecuencia 3 por cadena y fatiga sistémica muy alta.","t":["af","at"],"p":["Cadena Anterior","Cadena Posterior","Cadena Anterior","Cadena Posterior","Cadena Anterior","Cadena Posterior","Descanso"],"lv":"A","pro":["Frecuencia 3x/semana por cadena"],"con":["Fatiga sistémica extrema"],"old":"Frecuencia agresiva por plano."},
{"id":"bro_split","n":"Un grupo por día","orig":"Bro Split Clásico","d":"Pecho, espalda, pierna, hombros y brazos: un grupo por día. Mucho volumen por sesión, pero cada músculo una vez por semana.","t":["bf","av"],"p":["Pecho","Espalda","Piernas","Hombros","Brazos","Descanso","Descanso"],"lv":"P","pro":["Volumen máximo por sesión","Foco mental óptimo"],"con":["Frecuencia 1x/semana SUBÓPTIMA","NO recomendado para naturales"],"old":"Un grupo muscular por día."},
{"id":"hybrid_fb_ap","n":"Cuerpo completo y cadenas","orig":"Híbrido Cuerpo Completo + Ant/Post","d":"Dos días de cuerpo completo, uno de cadena anterior y uno de posterior. Cuatro días con frecuencia y especificidad.","t":["bal"],"p":["Cuerpo Completo","Descanso","Cuerpo Completo","Descanso","Anterior","Posterior","Descanso"],"lv":"I","pro":["Combina frecuencia full body con especificidad","4 días manejables"],"con":["Coordinación compleja"],"old":"4 días: 2 cuerpo completo + 1 anterior + 1 posterior."},
{"id":"minimalist_x2","n":"Dos días, lo esencial","orig":"Minimalista x2","d":"Dos sesiones de cuerpo completo, separadas. La dosis mínima efectiva cuando casi no hay tiempo.","t":["bf","bal"],"p":["Full Body A","Descanso","Descanso","Full Body B","Descanso","Descanso","Descanso"],"lv":"P","pro":["Solo 2 días requeridos","Máxima eficiencia"],"con":["Progreso lento","Volumen limitado"],"old":"Dosis mínima efectiva."},
{"id":"weekend_warrior","n":"Fin de semana","orig":"Guerrero de Finde","d":"Solo sábado y domingo: torso con cuerpo completo y pierna con cuerpo completo. Para agendas extremas.","t":["bf"],"p":["Descanso","Descanso","Descanso","Descanso","Descanso","Torso/Full Body","Pierna/Full Body"],"lv":"I","pro":["Se adapta a agendas extremas","Socialmente sostenible"],"con":["Frecuencia 1x/semana","Volumen concentrado"],"old":"Solo fines de semana."},
{"id":"glute_focus","n":"Énfasis en glúteos","orig":"Especialización Glúteo","d":"Tres días de tren inferior con énfasis en glúteo, más torso ligero y hombros. Glúteo tres veces por semana.","t":["af","av"],"p":["Glúteo/Isquios","Torso Liviano","Descanso","Cuádriceps/Glúteo","Hombros/Abs","Glúteo Pump","Descanso"],"lv":"I","pro":["Frecuencia 3x/semana glúteo","Énfasis estético"],"con":["Desbalance potencial"],"old":"3 días de tren inferior."},
{"id":"beach_body","n":"Más torso","orig":"Torso Dominante","d":"Tres días de torso y una pierna de mantenimiento. Prioriza la estética del tren superior.","t":["av"],"p":["Pecho/Espalda","Pierna Mantenimiento","Descanso","Hombros/Brazos","Descanso","Upper Completo","Descanso"],"lv":"I","pro":["Énfasis estético","3 días de torso"],"con":["Desbalance torso/pierna"],"old":"Enfoque 'Beach Body'."},
{"id":"fullbody_x5","n":"Cuerpo completo, 5 días","orig":"Cuerpo Completo x5","d":"Cuerpo completo cinco días. Frecuencia máxima y fatiga extrema: solo para avanzados.","t":["af","at"],"p":["Cuerpo Completo","Cuerpo Completo","Cuerpo Completo","Cuerpo Completo","Cuerpo Completo","Descanso","Descanso"],"lv":"A","pro":["Frecuencia 5x/semana MÁXIMA","Ideal para fuerza"],"con":["Fatiga EXTREMA","Solo avanzados"],"old":"Alta frecuencia estilo noruego."},
{"id":"push_pull_x4","n":"Empuje y tirón, 4 días","orig":"Push / Pull x4","d":"Empuje con cuádriceps y tirón con isquios, dos veces cada uno. La pierna se integra dentro de dos días.","t":["bal"],"p":["Empuje + Cuádriceps","Tirón + Isquios","Descanso","Empuje + Cuádriceps","Tirón + Isquios","Descanso","Descanso"],"lv":"I","pro":["Frecuencia 2x/semana","Integración pierna natural"],"con":["Sesiones de empuje largas"],"old":"Simple y brutal."},
{"id":"texas_method","n":"Estilo Texas","orig":"Estilo Texas","d":"Tres días de ondulación: volumen, recuperación e intensidad, con sentadilla y banca.","t":["pl","bf"],"p":["Día Volumen (5x5)","Descanso","Día Recuperación","Descanso","Día Intensidad","Descanso","Descanso"],"lv":"I","pro":["Progresión lineal probada","Ideal para fuerza"],"con":["Solo 3 días","Frecuencia baja para hipertrofia"],"old":"Ondulación diaria."},
{"id":"smolov_base","n":"Smolov Jr. (especialización)","orig":"Smolov Jr. (especialización)","d":"Especialización brutal en un levantamiento, cuatro sesiones por semana. Solo ciclos cortos y con tolerancia alta.","t":["pl","af","at"],"p":["Sesión 1 (4x9)","Descanso","Sesión 2 (5x7)","Sesión 3 (7x5)","Descanso","Sesión 4 (10x3)","Descanso"],"lv":"A","pro":["Volumen EXTREMO concentrado","Ganancias de fuerza rápidas en un lift"],"con":["NO usar como split habitual","Fatiga extrema / riesgo de sobreentrenamiento","Solo élite o ciclos ≤3–4 semanas"],"old":"ADVERTENCIA: especialización brutal de un lift (p. ej. sentadilla) 4×/semana. NO es un split general ni recomendación por defecto. Solo ciclos cortos con alta tolerancia a fatiga."},
{"id":"pl_sbd_x3","n":"SBD Full Body x3","orig":"SBD Full Body x3","d":"Sentadilla, banca y peso muerto tres días por semana. Máxima especificidad y fatiga articular alta.","t":["pl","at"],"p":["SBD Día 1","Descanso","SBD Día 2","Descanso","SBD Día 3","Descanso","Descanso"],"lv":"A","pro":["Especificidad máxima en SBD","Técnica altamente practicada"],"con":["Fatiga articular alta","Solo powerlifters"],"old":"Alta especificidad."},
{"id":"pl_hf_bench","n":"PL: Bench Freq 4","orig":"PL: Bench Freq 4","d":"Sentadilla tres veces, banca cuatro y peso muerto dos. Especializa la banca; vigila los hombros.","t":["pl","af"],"p":["Sentadilla/Banca","Peso Muerto/Banca","Descanso","Sentadilla/Banca","Variante DL/Banca","Sentadilla/Accesorios","Descanso"],"lv":"A","pro":["Frecuencia 4x/semana banca","Ideal para especialización"],"con":["Fatiga de hombros crítica"],"old":"Sq x3, Bp x4, Dl x2."},
{"id":"pl_classic_4","n":"PL: Clásico 4 Días","orig":"PL: Clásico 4 Días","d":"Sentadilla con banca, peso muerto, banca de volumen y sentadilla con peso muerto. Base clásica de powerlifting.","t":["pl","bal"],"p":["Sentadilla/Banca","Peso Muerto","Descanso","Banca Volumen","Sentadilla/Peso Muerto","Descanso","Descanso"],"lv":"I","pro":["Balance clásico","4 días manejables","Ideal para intermedios"],"con":["Progreso puede estancarse"],"old":"Base sólida de powerlifting."},
{"id":"sheiko_3day","n":"Sheiko Clásico (3 Días)","orig":"Sheiko Clásico (3 Días)","d":"Sentadilla y banca, peso muerto y banca, y otra vez sentadilla y banca. Volumen muy alto y técnica refinada.","t":["pl","af","at"],"p":["Sentadilla/Banca","Descanso","Peso Muerto/Banca","Descanso","Sentadilla/Banca","Descanso","Descanso"],"lv":"A","pro":["Volumen ALTÍSIMO","Técnica altamente refinada"],"con":["Volumen BRUTAL","Solo avanzados"],"old":"Estilo soviético."},
{"id":"westside_conjugate","n":"Westside (Conjugado)","orig":"Westside (Conjugado)","d":"Esfuerzo máximo y esfuerzo dinámico, para tren inferior y superior. Cambias de variantes constantemente.","t":["pl","bal"],"p":["ME Lower","ME Upper","Descanso","DE Lower","DE Upper","Descanso","Descanso"],"lv":"A","pro":["Desarrollo fuerza/potencia","Variación constante"],"con":["Equipamiento específico","Curva de aprendizaje alta"],"old":"Método Louie Simmons."},
{"id":"cube_method","n":"Método Cubo","orig":"Método Cubo","d":"Rotas esfuerzos: pesado, explosivo y repeticiones, con un día de accesorios. Evita los estancamientos.","t":["pl","bal"],"p":["Día Pesado","Día Explosivo","Descanso","Día Repeticiones","Accesorios Hipertrofia","Descanso","Descanso"],"lv":"A","pro":["Variedad de estímulos","Previene estancamientos"],"con":["Coordinación compleja"],"old":"Rotación de esfuerzos."},
{"id":"531_bbb","n":"5/3/1 Boring But Big","orig":"5/3/1 Boring But Big","d":"Press militar, peso muerto, banca y sentadilla, uno por día. Progresión lenta y sostenible.","t":["pl","bal","bf"],"p":["Press Militar/Hombro","Peso Muerto/Espalda","Descanso","Press Banca/Pecho","Sentadilla/Pierna","Descanso","Descanso"],"lv":"I","pro":["Progresión lenta y sostenible","Ideal para fuerza a largo plazo"],"con":["Progreso MUY lento"],"old":"El clásico de Wendler."},
{"id":"madcow_5x5","n":"Madcow 5x5","orig":"Madcow 5x5","d":"Volumen, recuperación e intensidad en tres días. Progresión lineal para intermedios.","t":["pl","bal"],"p":["Volumen (5x5)","Descanso","Recuperación (Light)","Descanso","Intensidad (1x3/1x5)","Descanso","Descanso"],"lv":"I","pro":["Progresión lineal probada","3 días manejables"],"con":["Progreso se estanca en avanzados"],"old":"Progresión lineal avanzada."},
{"id":"korte_3x3","n":"Korte 3x3","orig":"Korte 3x3","d":"Solo sentadilla, banca y peso muerto en tres sesiones: volumen, técnica e intensidad. Sin accesorios.","t":["pl","af"],"p":["SBD (Volumen)","Descanso","SBD (Técnica)","Descanso","SBD (Intensidad)","Descanso","Descanso"],"lv":"A","pro":["Especificidad MÁXIMA en SBD"],"con":["Cero accesorios","Riesgo de desbalances"],"old":"Escuela alemana. Solo SBD."},
{"id":"nsuns_4day","n":"nSuns 4 días","orig":"nSuns 4 días","d":"Banca con press militar, sentadilla con sumo, banca cerrada y peso muerto con frontal. Volumen alto de 5/3/1.","t":["pl","av"],"p":["Banca/OHP","Sentadilla/Sumo","Descanso","Banca/Cerrado","Peso muerto/Frontal","Descanso","Descanso"],"lv":"A","pro":["Volumen alto de 5/3/1","4 días manejables"],"con":["Fatiga alta","Solo avanzados"],"old":"T1+T2 por sesión: banca/OHP, sentadilla/sumo, banca/cerrado, peso muerto/frontal."},
{"id":"ul_arms","n":"Upper / Lower + Brazos","orig":"Upper / Lower + Brazos","d":"Torso y pierna dos veces, y un día dedicado a brazos y hombros.","t":["av"],"p":["Torso","Pierna","Descanso","Torso","Pierna","Brazos/Hombros","Descanso"],"lv":"I","pro":["Día dedicado de brazos","4 días base + 1 especializado"],"con":["5 días requeridos"],"old":"Estructura UL + especialización brazos."},
{"id":"heavy_light","n":"Pesado / Liviano x3","orig":"Pesado / Liviano x3","d":"Tres sesiones de cuerpo completo: pesada, liviana y moderada. Ondulas la intensidad para recuperar bien.","t":["af","bal"],"p":["Cuerpo Completo Pesado","Descanso","Cuerpo Completo Liviano","Descanso","Cuerpo Completo Moderado","Descanso","Descanso"],"lv":"I","pro":["Ondulación de cargas clara","Recuperación óptima"],"con":["Requiere autorregular bien la intensidad"],"old":"Ondulación de cuerpo completo en 3 días."},
{"id":"ppl_x3","n":"Push / Pull / Legs x3","orig":"Push / Pull / Legs x3","d":"Un día de empuje, uno de tirón y uno de pierna. Muy fácil de organizar; cada patrón, una vez por semana.","t":["bal","bf"],"p":["Empuje","Descanso","Tirón","Descanso","Pierna","Descanso","Descanso"],"lv":"P","pro":["Muy fácil de organizar","Sesiones específicas sin exceso de días"],"con":["Frecuencia 1x por patrón si no se rota semanalmente"],"old":"PPL minimalista de 3 días para frecuencia moderada y buena recuperación."},
{"id":"fullbody_x4","n":"Cuerpo Completo x4","orig":"Cuerpo Completo x4","d":"Cuatro sesiones de cuerpo completo, de dos en dos. El puente entre el x3 y el x5.","t":["af","bal"],"p":["Cuerpo Completo A","Cuerpo Completo B","Descanso","Cuerpo Completo C","Cuerpo Completo D","Descanso","Descanso"],"lv":"I","pro":["Frecuencia alta sin entrenar todos los días","Buen puente entre x3 y x5"],"con":["Puede ser exigente si todas las sesiones son pesadas"],"old":"Alta frecuencia con volumen distribuido en cuatro sesiones."},
{"id":"ul_fb_x3","n":"Upper / Lower + Cuerpo Completo","orig":"Upper / Lower + Cuerpo Completo","d":"Torso, pierna y cuerpo completo en tres días. Frecuencia 2 práctica si solo tienes tres días.","t":["bal"],"p":["Torso","Descanso","Pierna","Descanso","Cuerpo Completo","Descanso","Descanso"],"lv":"P","pro":["Frecuencia 2x práctica","Ideal para agendas de 3 días"],"con":["El día full body debe mantenerse controlado"],"old":"Tres días: torso, pierna y cuerpo completo para cubrir frecuencia 2 sin mucho calendario."}];

/* ---- rp-engine.js ---- */
/* ===== Motor de ejemplo: simula, en pequeño, el contrato de maps/reparto-motor.md (el real vive en Kotlin: SplitRedistributor) ===== */
const rpNorm=s=>s.normalize('NFD').replace(/[̀-ͯ]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
const RP_ATOMS=['CH','DF','DL','DR','TR','LA','UB','BI','FO','QU','HA','GL','CA','AD','ER','CO','NK'];
// Rótulos del informe del motor agrupados como los muestra la pantalla (SplitMuscles.focusLabel).
const RP_GROUPS=[['Pecho',['CH']],['Espalda',['LA','UB']],['Hombros',['DF','DL','DR']],['Bíceps',['BI']],['Tríceps',['TR']],['Cuádriceps',['QU']],['Isquios',['HA']],['Glúteos',['GL']],['Pantorrillas',['CA']],['Lumbar',['ER']],['Core',['CO']]];
const rpMax=(...ts)=>{const o={};ts.forEach(t=>Object.entries(t).forEach(([k,v])=>{o[k]=Math.max(o[k]||0,v)}));return o};
const F_PUSH={CH:1,DF:1,DL:1,TR:1,CO:.5},F_PULL={LA:1,UB:1,DR:1,BI:1,FO:.8,ER:.3,CO:.5},F_LEGS={QU:1,HA:1,GL:1,CA:1,AD:1,ER:.6,CO:.5};
const F_UPPER=rpMax(F_PUSH,F_PULL,{NK:.5});
const F_ANT={CH:1,DF:1,DL:.6,QU:1,CO:1,BI:1,TR:.4,NK:.5},F_POST={LA:1,UB:1,DR:1,HA:1,GL:1,CA:1,ER:1,AD:1,TR:1,BI:.4,FO:.8};
const F_CHEST={CH:1,TR:.5,DF:.6},F_BACK={LA:1,UB:1,DR:.7,BI:.5,ER:.5,FO:.4},F_SHO={DF:1,DL:1,DR:.9,UB:.4},F_ARMS={BI:1,TR:1,FO:1},F_ABS={CO:1};
const F_GLU={GL:1,HA:.5,AD:.5},F_QUAD={QU:1,GL:.5,CA:.4},F_HAM={HA:1,GL:.6,CA:.4};
const F_SQ={QU:1,GL:.9,AD:.6,ER:.5,CO:.5,HA:.4,CA:.3},F_BP={CH:1,TR:.9,DF:.9,DL:.4,CO:.3},F_DL={HA:1,GL:1,ER:1,UB:.8,FO:.7,LA:.6,CO:.5,QU:.4},F_OHP={DF:1,TR:.8,DL:.8,CH:.4,CO:.4};
const rpMean=(w,...a)=>a.reduce((s,k)=>s+(w[k]||0),0)/a.length;
// Port de DayFocusParser: qué músculos acoge un día según su etiqueta.
function rpFocus(label){
  const words=rpNorm(label).split(' ').filter(Boolean);
  const has=(...st)=>words.some(w=>st.some(s=>w===s||w.startsWith(s)));
  const ex=(...e)=>words.some(w=>e.includes(w));
  const parts=[];
  if(has('empuje','push'))parts.push(F_PUSH);
  if(has('tiron','pull','traccion','jalon'))parts.push(F_PULL);
  if(has('pierna','lower','inferior'))parts.push(F_LEGS);
  if(has('torso','upper','superior'))parts.push(F_UPPER);
  if(has('anterior'))parts.push(F_ANT);
  if(has('posterior'))parts.push(F_POST);
  if(has('pecho'))parts.push(F_CHEST);
  if(has('espalda'))parts.push(F_BACK);
  if(has('hombro'))parts.push(F_SHO);
  if(has('brazo'))parts.push(F_ARMS);
  if(ex('abs','core')||has('abdomen','abdominal'))parts.push(F_ABS);
  if(has('gluteo'))parts.push(F_GLU);
  if(has('cuadriceps'))parts.push(F_QUAD);
  if(has('isquio','femoral'))parts.push(F_HAM);
  if(has('sentadilla','squat'))parts.push(F_SQ);
  if(rpNorm(label).includes('peso muerto')||has('deadlift')||ex('dl'))parts.push(F_DL);
  if(has('banca','bench'))parts.push(F_BP);
  if(has('militar')||ex('ohp'))parts.push(F_OHP);
  if(ex('sbd'))parts.push(F_SQ,F_BP,F_DL);
  const full=has('full','fullbody')||(has('cuerpo')&&has('completo'));
  const acc=has('accesorio');
  const light=has('liviano','light','recuperacion','recovery','mantenimiento','pump','tecnica');
  const spec=rpMax(...parts);
  const generic=!Object.keys(spec).length&&!full&&!acc;
  const floor=!Object.keys(spec).length?0:full?.85:acc?.6:0;
  let w;
  if(!Object.keys(spec).length)w=Object.fromEntries(RP_ATOMS.map(a=>[a,1]));
  else if(floor<=0)w=spec;
  else w=Object.fromEntries(RP_ATOMS.map(a=>[a,Math.max(spec[a]||0,floor)]));
  const acceptsG=[];
  if(rpMean(w,'CH','DF','TR')>=.75)acceptsG.push('push');
  if(rpMean(w,'LA','UB','BI')>=.75)acceptsG.push('pull');
  if(rpMean(w,'QU','HA','GL')>=.75)acceptsG.push('legs');
  return {label,w,acc:acceptsG,floor,light,generic,full};
}
// Rol visual de un día (color e inicial): las reglas de la app (splitDayMark) y, para el resto, el grupo que domina su foco.
function rpRole(label){
  const k=rpNorm(label);
  if(!k||k==='descanso')return {k:'rest',ini:''};
  if(k.includes('empuje')||k.includes('push'))return {k:'push',ini:'E'};
  if(k.includes('tiron')||k.includes('pull'))return {k:'pull',ini:'Ti'};
  if(k.includes('pierna')||k.includes('legs'))return {k:'legs',ini:'Pi'};
  if(k.includes('torso')||k.includes('upper')||k.includes('superior'))return {k:'torso',ini:'T'};
  if(k.includes('lower')||k.includes('inferior'))return {k:'legs',ini:'Pi'};
  if(k.includes('anterior')||k.includes('posterior'))return {k:'mix',ini:k.includes('anterior')?'An':'Po'};
  if(k.includes('cuerpo completo')||k.includes('full body'))return {k:'full',ini:'CC'};
  const f=rpFocus(label),push=rpMean(f.w,'CH','DF','DL','TR'),pull=rpMean(f.w,'LA','UB','DR','BI'),legs=rpMean(f.w,'QU','HA','GL','CA');
  const raw=label.trim().replace(/^(d[ií]a|sesi[oó]n)\s+/i,'').replace(/[^A-Za-zÁÉÍÓÚáéíóúñÑ0-9]/g,'');
  const ini=raw.slice(0,2).replace(/^./,c=>c.toUpperCase());
  let kk='mix';
  if(f.generic)kk='mix';
  else if(legs>=.35&&push<.4&&pull<.4)kk='legs';
  else if(push>=.5&&pull<.4&&legs<.4)kk='push';
  else if(pull>=.5&&push<.4&&legs<.4)kk='pull';
  else if(push>=.4&&pull>=.4&&legs<.25)kk='torso';
  return {k:kk,ini};
}
const rpTrainDays=s=>s.p.filter(x=>rpRole(x).k!=='rest').length;
// Frecuencia por músculo SIN ejercicios: días en que el foco del día pide el músculo (≥ 0,75).
function rpSplitFreq(s){
  const days=s.p.filter(x=>rpRole(x).k!=='rest').map(rpFocus);
  const out={};
  RP_GROUPS.forEach(([g,at])=>{out[g]=days.filter(d=>Math.max(...at.map(a=>d.w[a]||0))>=.75&&!d.generic||d.generic&&d.full).length});
  return out;
}
const rpTitles=labels=>{const rep={};labels.forEach(l=>rep[l]=(rep[l]||0)+1);const seen={};return labels.map(l=>{if(rep[l]<2)return l;const o=seen[l]||0;seen[l]=o+1;return l+' '+String.fromCharCode(65+o)})};
// Sesión de ejemplo: ejercicios con series, repeticiones, RIR y descanso (todo viaja con el ejercicio).
// m = aporte por músculo (1 primario, 0,5 secundario); t = nivel (0 principal … 3 remate); pt = patrón pesado; ss = superserie.
const RP_EX={
 sq:{id:'sq',n:'Sentadilla trasera',s:4,r:'5–6',rir:2,rest:180,t:0,m:{QU:1,GL:.5,ER:.5},pt:'SQ'},
 prensa:{id:'prensa',n:'Prensa de piernas',s:3,r:'10–12',rir:1,rest:120,t:1,m:{QU:1,GL:.5}},
 rdl:{id:'rdl',n:'Peso muerto rumano',s:3,r:'8–10',rir:2,rest:150,t:1,m:{HA:1,GL:.5,ER:.5},pt:'HI'},
 curlf:{id:'curlf',n:'Curl femoral sentado',s:3,r:'10–12',rir:1,rest:90,t:2,m:{HA:1}},
 gem:{id:'gem',n:'Elevación de talones de pie',s:4,r:'12–15',rir:1,rest:60,t:3,m:{CA:1}},
 rueda:{id:'rueda',n:'Rueda abdominal',s:3,r:'8–10',rir:2,rest:60,t:3,m:{CO:1}},
 bp:{id:'bp',n:'Press banca con barra',s:4,r:'5–6',rir:2,rest:180,t:0,m:{CH:1,DF:.5,TR:.5},pt:'HP'},
 inc:{id:'inc',n:'Press inclinado con mancuernas',s:3,r:'8–10',rir:2,rest:120,t:1,m:{CH:1,DF:.5,TR:.5}},
 ohp:{id:'ohp',n:'Press militar sentado',s:3,r:'6–8',rir:2,rest:150,t:1,m:{DF:1,TR:.5,DL:.5},pt:'VP'},
 lat:{id:'lat',n:'Elevaciones laterales',s:3,r:'12–15',rir:1,rest:60,t:2,m:{DL:1},ss:'S1'},
 pushd:{id:'pushd',n:'Extensión de tríceps en polea',s:3,r:'10–12',rir:1,rest:60,t:2,m:{TR:1},ss:'S1'},
 aper:{id:'aper',n:'Aperturas en polea',s:2,r:'12–15',rir:1,rest:60,t:2,m:{CH:1}},
 dom:{id:'dom',n:'Dominadas lastradas',s:4,r:'5–6',rir:2,rest:180,t:0,m:{LA:1,BI:.5,UB:.5},pt:'PU'},
 remo:{id:'remo',n:'Remo con barra',s:4,r:'6–8',rir:2,rest:150,t:1,m:{LA:1,UB:1,BI:.5,ER:.5},pt:'PU'},
 jalon:{id:'jalon',n:'Jalón al pecho neutro',s:3,r:'10–12',rir:1,rest:90,t:1,m:{LA:1,BI:.5}},
 face:{id:'face',n:'Face pull',s:3,r:'15',rir:1,rest:60,t:2,m:{DR:1,UB:.5},ss:'S2'},
 martillo:{id:'martillo',n:'Curl martillo',s:3,r:'10–12',rir:1,rest:60,t:2,m:{BI:1,FO:.5},ss:'S2'},
 bici:{id:'bici',n:'Bicicleta suave · 15 min',s:0,r:'15 min',rir:0,rest:0,t:5,m:{},cardio:true},
 pm:{id:'pm',n:'Peso muerto convencional',s:3,r:'3–5',rir:2,rest:210,t:0,m:{HA:1,GL:1,ER:1,QU:.5},pt:'HI'},
 bulg:{id:'bulg',n:'Sentadilla búlgara',s:3,r:'8–10',rir:2,rest:120,t:1,m:{QU:1,GL:1}},
 hip:{id:'hip',n:'Hip thrust',s:3,r:'8–10',rir:2,rest:120,t:1,m:{GL:1,HA:.5}},
 ext:{id:'ext',n:'Extensión de cuádriceps',s:3,r:'12–15',rir:1,rest:60,t:2,m:{QU:1}},
 gems:{id:'gems',n:'Elevación de talones sentado',s:3,r:'12–15',rir:1,rest:60,t:3,m:{CA:1}},
};
const RP_BEFORE=[ // semana 5 de «Motor y fuerza»: lo que hay hoy (Personalizado, 4 días)
 {id:'s1',n:'Pierna A',wd:1,ex:['sq','prensa','rdl','curlf','gem','rueda']},
 {id:'s2',n:'Empuje',wd:2,ex:['bp','inc','ohp','lat','pushd','aper']},
 {id:'s3',n:'Tirón',wd:4,ex:['dom','remo','jalon','face','martillo','bici']},
 {id:'s4',n:'Pierna B',wd:5,ex:['pm','bulg','hip','ext','gems']},
];
const rpSec=e=>e.cardio?15*60:e.s*(45+e.rest)+90;
const rpMin=exs=>exs.length?Math.round((exs.reduce((s,e)=>s+rpSec(e),0)+180)/60):0;
const rpSets=e=>e.cardio?0:e.s;
function rpVolume(sessions){ // series semanales por grupo (primario 1, secundario 0,5)
  const v={};RP_GROUPS.forEach(([g])=>v[g]=0);
  sessions.forEach(ss=>ss.forEach(e=>RP_GROUPS.forEach(([g,at])=>{at.forEach(a=>{v[g]+=rpSets(e)*(e.m[a]||0)})})));
  return v;
}
// Frecuencia: sesiones en que el músculo suma al menos 2 series ponderadas.
function rpFreq(sessions){
  const f={};RP_GROUPS.forEach(([g,at])=>{f[g]=sessions.filter(ss=>ss.reduce((s,e)=>s+at.reduce((q,a)=>q+rpSets(e)*(e.m[a]||0),0),0)>=2).length});
  return f;
}
// Unidades que se mueven enteras: un ejercicio, una superserie completa o el cardio.
function rpUnits(sessions){
  const units=[];
  sessions.forEach((se,si)=>{
    const done=new Set();
    se.ex.forEach((id,pos)=>{
      const e=RP_EX[id];if(done.has(id))return;
      let exs=[e];
      if(e.ss){exs=se.ex.map(x=>RP_EX[x]).filter(x=>x.ss===e.ss);exs.forEach(x=>done.add(x.id))}
      const m={};exs.forEach(x=>Object.entries(x.m).forEach(([a,w])=>m[a]=(m[a]||0)+w));
      const prim=new Set();exs.forEach(x=>Object.entries(x.m).forEach(([a,w])=>{if(w>=1)prim.add(a)}));
      const fam={push:['CH','DF','DL','TR'],pull:['LA','UB','DR','BI','FO'],legs:['QU','HA','GL','CA','AD','ER'],core:['CO']};
      let grp='other',best=0;Object.entries(fam).forEach(([g,at])=>{const t=at.reduce((s,a)=>s+(m[a]||0),0);if(t>best){best=t;grp=g}});
      units.push({key:exs.map(x=>x.id).join('+'),exs,cardio:!!e.cardio,tier:Math.min(...exs.map(x=>x.t)),sec:exs.reduce((s,x)=>s+rpSec(x),0),m,prim,grp,
        heavy:new Set(exs.map(x=>x.pt).filter(Boolean)),origin:si,pos});
    });
  });
  return units;
}
// Reparto de las unidades entre los días del reparto: foco, mínimo de ejercicios, minutos parejos, mezcla y reglas duras (versión pequeña de DayAssigner).
function rpAffinity(u,day){
  if(u.cardio||!Object.keys(u.m).length)return .5;
  const sp=Object.entries(u.m).filter(([,w])=>w>=1),ss=Object.entries(u.m).filter(([,w])=>w<1);
  const fit=g=>{const t=g.reduce((s,[,w])=>s+w,0);return t?g.reduce((s,[a,w])=>s+w*(day.f.w[a]||0),0)/t:0};
  if(!sp.length)return fit(ss);
  return ss.length?.8*fit(sp)+.2*fit(ss):fit(sp);
}
function rpAssign(units,days,home){
  const n=units.length,d=days.length;
  const aff=units.map(u=>days.map(day=>rpAffinity(u,day)));
  const elig=aff.map((row,i)=>{const b=Math.max(...row);return row.map((a,j)=>b<.35||a>=b-.12-1e-9||(days[j].f.floor>0&&a>=days[j].f.floor-1e-9))});
  const tot=units.reduce((s,u)=>s+u.sec,0),mean=tot/d;
  const strengthN=units.filter(u=>!u.cardio).reduce((s,u)=>s+u.exs.length,0);
  const minEx=Math.min(3,Math.floor(strengthN/d));
  const adj=(a,b)=>{const x=Math.abs(days[a].wd-days[b].wd);return a!==b&&(x===1||x===6)};
  const cost=(as,complete)=>{
    const sec=Array(d).fill(0),cnt=Array(d).fill(0);let fit=0;
    units.forEach((u,i)=>{const j=as[i];if(j<0)return;fit+=1-aff[i][j];sec[j]+=u.sec;if(!u.cardio)cnt[j]+=u.exs.length});
    let bal=0;if(mean>0)sec.forEach(s=>{const r=(s-mean)/mean;bal+=r*r});
    let crowd=0,same=0,adjc=0,clash=0,next=0;
    for(let i=0;i<n;i++){const a=as[i];if(a<0)continue;for(let k=i+1;k<n;k++){const b=as[k];if(b<0)continue;
      const ov=(units[i].cardio||units[k].cardio)?0:[...units[i].prim].some(x=>units[k].prim.has(x))?1:(units[i].grp===units[k].grp&&units[i].tier<=1&&units[k].tier<=1?.3:0);
      const hv=[...units[i].heavy].some(x=>units[k].heavy.has(x));
      if(a===b){crowd+=ov;if(hv)clash++}else if(adj(a,b)){next+=ov;if(hv)adjc++}}}
    let t=12*fit+30*bal+1.5*crowd+3*adjc+2*next+60*clash;
    if(home)units.forEach((u,i)=>{if(as[i]>=0&&home[u.origin]!==undefined&&home[u.origin]!==as[i])t+=1.2});
    if(complete)for(let j=0;j<d;j++){if(cnt[j]<minEx)t+=120*(minEx-cnt[j]);if(cnt[j]===0&&strengthN>=d)t+=20}
    return t;
  };
  const as=Array(n).fill(-1);
  const order=units.map((_,i)=>i).sort((a,b)=>units[a].tier-units[b].tier||units[b].sec-units[a].sec||units[a].origin-units[b].origin||units[a].pos-units[b].pos);
  order.forEach(i=>{let best=-1,bc=1e18;for(let j=0;j<d;j++){if(!elig[i][j])continue;as[i]=j;const c=cost(as,false);if(c<bc-1e-9){bc=c;best=j}}as[i]=best>=0?best:0});
  const counts=()=>{const c=Array(d).fill(0);units.forEach((u,i)=>{if(!u.cardio)c[as[i]]+=u.exs.length});return c};
  for(let pass=0;pass<80;pass++){
    let cur=cost(as,true),bd=-1e-9,mv=null;const cn=counts();
    for(let i=0;i<n;i++){const from=as[i];for(let to=0;to<d;to++){if(to===from)continue;if(!(elig[i][to]||(cn[to]<minEx&&aff[i][to]>=.4)))continue;as[i]=to;const dl=cost(as,true)-cur;as[i]=from;if(dl<bd){bd=dl;mv=[i,to,-1]}}}
    for(let i=0;i<n;i++)for(let k=i+1;k<n;k++){const a=as[i],b=as[k];if(a===b||!elig[i][b]||!elig[k][a])continue;as[i]=b;as[k]=a;const dl=cost(as,true)-cur;as[i]=a;as[k]=b;if(dl<bd){bd=dl;mv=[i,b,k,a]}}
    if(!mv)break;as[mv[0]]=mv[1];if(mv[2]>=0)as[mv[2]]=mv[3];
  }
  const misfit=units.map((u,i)=>[u,i]).filter(([u,i])=>!u.cardio&&aff[i][as[i]]<.4).map(([u])=>u);
  return {as,aff,misfit};
}
// Aplica un reparto a la semana de ejemplo. weekdays: días elegidos (1 = lunes … 7 = domingo), tantos como días de entreno tiene el reparto.
function rpRun(split,weekdays,o={}){
  const labels=split.p.filter(x=>rpRole(x).k!=='rest');
  const titles=rpTitles(labels);
  const days=labels.map((l,i)=>({i,wd:weekdays[i],label:l,title:titles[i],f:rpFocus(l),role:rpRole(l)}));
  const before=o.before||RP_BEFORE;
  const locked=before.filter(s=>o.locked&&o.locked.includes(s.id));
  const free=before.filter(s=>!locked.includes(s));
  let units=rpUnits(free);
  let useDays=days;
  const lockedDays=[];
  if(locked.length){ // lo hecho se queda y ocupa el día del reparto que más se le parece
    const left=[...days];
    locked.forEach(s=>{const u=rpUnits([s]);let bj=0,bs=-1;left.forEach((day,j)=>{const a=u.reduce((x,q)=>x+rpAffinity(q,day)*q.sec,0)/u.reduce((x,q)=>x+q.sec,0);if(a>bs){bs=a;bj=j}});lockedDays.push({s,day:left[bj]});left.splice(bj,1)});
    const freeWd=weekdays.filter(w=>!locked.some(s=>s.wd===w)).sort((a,b)=>a-b);
    useDays=left.map((d,j)=>({...d,wd:freeWd[j]!==undefined?freeWd[j]:d.wd}));
    lockedDays.forEach(l=>{l.day={...l.day,wd:l.s.wd}});
  }
  // día «natural» de cada sesión de origen: el que más se le parece (y, a igual encaje, el más cercano a su día de hoy)
  const home={};
  {const us=free.map((s,k)=>({s,k,u:rpUnits([s])}));const pairs=[];
   us.forEach(({s,k,u})=>useDays.forEach((day,j)=>{const tot=u.reduce((x,q)=>x+q.sec,0)||1;const a=u.reduce((x,q)=>x+rpAffinity(q,day)*q.sec,0)/tot;pairs.push({k,j,a,d:Math.min(Math.abs(s.wd-day.wd),7-Math.abs(s.wd-day.wd))})}));
   pairs.sort((x,y)=>y.a-x.a+(Math.abs(y.a-x.a)<.03?0:0)||x.d-y.d);
   const usedD=new Set(),usedK=new Set();
   pairs.sort((x,y)=>(Math.abs(x.a-y.a)<.04?x.d-y.d:y.a-x.a));
   pairs.forEach(p=>{if(usedD.has(p.j)||usedK.has(p.k))return;usedD.add(p.j);usedK.add(p.k);home[free.indexOf(us[p.k].s)]=p.j});}
  const res=units.length&&useDays.length?rpAssign(units,useDays,home):{as:[],misfit:[]};
  const sess=useDays.map((day,j)=>{
    const us=units.filter((u,i)=>res.as[i]===j).sort((a,b)=>a.tier-b.tier||a.origin-b.origin||a.pos-b.pos);
    const exs=[];us.forEach(u=>u.exs.forEach(e=>exs.push({...e,from:before[u.origin].n,fromIdx:u.origin})));
    return {day,exs,locked:false};
  });
  lockedDays.forEach(({s,day})=>sess.push({day:{...day,title:s.n,keepName:true,role:rpRole(s.n)},exs:s.ex.map(id=>({...RP_EX[id],from:s.n,fromIdx:before.indexOf(s)})),locked:true}));
  sess.sort((a,b)=>a.day.wd-b.day.wd);
  sess.forEach(se=>{se.min=rpMin(se.exs);se.n=se.exs.filter(e=>!e.cardio).length});
  // ajuste al tiempo pedido: se quitan series a los remates y aislamientos, de una en una, del final hacia delante
  const trims=[];
  if(o.targetMin){sess.forEach(se=>{if(se.locked)return;let guard=60;while(se.min>o.targetMin*1.1&&guard--){const v=[...se.exs].reverse().find(e=>!e.cardio&&e.t>=2&&e.s>2);if(!v)break;v.s--;const t=trims.find(x=>x.id===v.id&&x.day===se.day.title);if(t)t.n++;else trims.push({id:v.id,name:v.n,day:se.day.title,n:1});se.min=rpMin(se.exs)}})}
  const afterSessions=sess.map(se=>se.exs);
  const beforeSessions=before.map(s=>s.ex.map(id=>({...RP_EX[id]})));
  return {sess,days,trims,misfit:res.misfit,volBefore:rpVolume(beforeSessions),volAfter:rpVolume(afterSessions),freqBefore:rpFreq(beforeSessions),freqAfter:rpFreq(afterSessions),lockedDays};
}
// Días de la semana para un reparto: los tuyos si el número cuadra; si no, los del propio reparto (su patrón desde el día de inicio), como hace hoy la app.
function rpPlaceDays(split,cur,start=1){
  const n=rpTrainDays(split);
  if(cur&&cur.length===n)return [...cur].sort((a,b)=>a-b);
  const out=[];split.p.forEach((l,i)=>{if(rpRole(l).k!=='rest')out.push(((start-1+i)%7)+1)});
  return out;
}
if(typeof module!=='undefined')module.exports={rpRun,rpPlaceDays,rpRole,rpFocus,rpSplitFreq,RP_BEFORE,RP_EX,rpTrainDays};

/* ---- rp-ui1.js ---- */
/* ===== Piezas de interfaz ===== */
Object.assign(P,{
 lock:'<rect x="5" y="10.5" width="14" height="9.5" rx="2.5"/><path d="M8 10.5V8a4 4 0 018 0v2.5"/>',
 undo:'<path d="M8.5 6.5L4 11l4.5 4.5"/><path d="M4.5 11H15a5 5 0 010 10h-3"/>',
 warn:'<path d="M12 4l9 15.5H3z"/><path d="M12 10v4.5M12 17.2v.01"/>',
 sliders2:'<path d="M5 7h9M18 7h1M5 17h3M12 17h7"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
});
const RPC={push:'var(--ene)',pull:'var(--col)',legs:'var(--mus)',torso:'#c9a8ff',full:'#f2eee6',mix:'#f2eee6',rest:'transparent'};
const RPL=['L','M','Mi','J','V','S','D'],RPS=['LUN','MAR','MIÉ','JUE','VIE','SÁB','DOM'],RPF=['lunes','martes','miércoles','jueves','viernes','sábado','domingo'];
const RPDATE=[3,4,5,6,7,8,9];
const RPLV={P:'Principiante',I:'Intermedio',A:'Avanzado'};
const RPTAG={rec:'Recomendado KPKN',af:'Alta frecuencia',bf:'Baja frecuencia',bal:'Balanceado',av:'Alto volumen',at:'Alta tolerancia',pl:'Powerlifting'};
const RP_CUR=[1,2,4,5];
const RPSH={split:'ul_x4',sess:null};
const rpS=id=>RP_SPLITS.find(s=>s.id===id);
const rpGo=id=>{state.scene=id;try{render()}catch(e){}};
const rpJoin=a=>a.length<2?a.join(''):a.slice(0,-1).join(', ')+' y '+a[a.length-1];
const rpDays=a=>rpJoin(a.map(d=>'el '+RPF[d-1]));
const rpTxt=s=>s.replace(/<[^>]+>/g,'');
const RP_MAIN=['Pecho','Espalda','Hombros','Bíceps','Tríceps','Cuádriceps','Isquios','Glúteos','Pantorrillas'];
function rpFreqHead(s){
  if(s.t.includes('pl'))return null;
  const f=rpSplitFreq(s),v=RP_MAIN.map(g=>f[g]).sort((a,b)=>a-b);
  return v[Math.floor(v.length/2)];
}
function rpStrip(pat,o={}){
  return `<div class="rp-strip ${o.sm?'sm':''}">${[0,1,2,3,4,5,6].map(i=>{const l=pat[i]||'Descanso';const r=rpRole(l);const wd=(((o.start||1)-1)+i)%7;
   return `<div><small>${RPL[wd]}</small>${r.k==='rest'?'<i class="rp-ring"></i>':`<i class="rp-dot" style="--c:${RPC[r.k]}">${r.ini}</i>`}</div>`}).join('')}</div>`;
}
const rpMeta=(s,o={})=>{
  const f=rpFreqHead(s),n=rpTrainDays(s);
  return `${o.rec&&s.t.includes('rec')?`<span class="rp-m rec">${ic('spark',13)}Recomendado KPKN</span>`:''}<span class="rp-m"><b>${n} ${n===1?'día':'días'}</b></span>${f?`<span class="rp-m"><b>${f}×</b> por músculo</span>`:''}<span class="rp-m">${RPLV[s.lv]}</span>`;
};
function rpCard(s,o={}){
  const n=rpTrainDays(s);
  const need=o.have&&n!==o.have?`<div class="rp-fit">${ic('info',14)}<span>Necesita ${n} días y tu semana tiene ${o.have}. Al elegirlo, el reparto propone los días.</span></div>`:'';
  return `<button class="rp-card ${o.sel?'sel':''} ${o.cur?'cur':''}" data-split="${s.id}" aria-label="${s.n}, ${n} días">
   ${o.sel?`<span class="rp-tick">${ic('check',15)}</span>`:''}
   <div class="rp-card-h"><div>${s.t.includes('rec')?`<div class="rp-rec">${ic('spark',12)}Recomendado KPKN</div>`:''}<strong>${s.n}</strong></div></div>
   <p>${s.d}</p>${rpStrip(s.p)}
   <div class="rp-meta">${rpMeta(s)}</div>${need}</button>`;
}
/* ---------- estado de la vista previa ---------- */
function rpCompute(st,id,days){
  const s=rpS(id);st.split=id;
  st.days=days||rpPlaceDays(s,st.cur,1);st.days0=st.days.slice();
  if(st.empty){rpFillEmpty(st,s);return}
  const res=rpRun(s,st.days,{locked:st.locked,before:st.before,targetMin:st.fit?st.tmin:null});
  st.res=res;
  st.sess=res.sess.map(se=>({...se,day:{...se.day},exs:se.exs.map(e=>({...e}))}));
  st.sel=null;st.dirty=false;
}
function rpMk(over={}){
  const st={split:'ul_x4',cur:RP_CUR.slice(),view:'after',sel:null,sheet:null,mode:'migrate',scope:'week',tab:'sesion',before:RP_BEFORE,locked:null,empty:false,tmin:null,fit:false,dec:{},wk:{n:5,range:'3–9 nov',dates:[3,4,5,6,7,8,9]},...over};
  rpCompute(st,st.split,over.days);
  return st;
}
function rpMove(st,from,to){
  const a=st.sess.find(s=>s.day.wd===from),b=st.sess.find(s=>s.day.wd===to);
  if(!a||a.locked||(b&&b.locked)||from===to)return false;
  if(b)b.day.wd=from;
  a.day.wd=to;st.sess.sort((x,y)=>x.day.wd-y.day.wd);st.dirty=true;
  return true;
}
function rpFocusLine(exs){
  const v={};RP_GROUPS.forEach(([g,at])=>{v[g]=exs.reduce((s,e)=>s+rpSets(e)*at.reduce((q,a)=>q+(e.m[a]||0),0),0)});
  const top=Object.entries(v).filter(([,x])=>x>0).sort((a,b)=>b[1]-a[1]).map(([g])=>g);
  const out=[];top.slice(0,3).forEach(g=>{if([...out,g].join(' · ').length<=26)out.push(g)});
  return out.join(' · ')||'Sin ejercicios';
}
function rpWarn(st){
  const out=[],s=rpS(st.split),n=rpTrainDays(s),had=st.cur.length;
  const wds=st.sess.map(x=>x.day.wd);
  if(st.empty)return [{k:'info',t:`Esta semana no tiene sesiones: el reparto crea las <b>${n} sesiones</b> con los ejercicios de las plantillas más compatibles. No hay nada que migrar.`}];
  if(st.locked){const done=st.sess.filter(x=>x.locked);out.push({k:'ok',t:`<b>${(s=>s[0].toUpperCase()+s.slice(1))(rpJoin(done.map(x=>RPF[x.day.wd-1])))}</b> ya ${done.length>1?'están hechos':'está hecho'}: se quedan como están y cuentan en el volumen de la semana. El reparto nuevo cubre ${rpDays(st.sess.filter(x=>!x.locked).map(x=>x.day.wd))}.`})}
  if(n!==had){
    const add=wds.filter(w=>!st.cur.includes(w)),rem=st.cur.filter(w=>!wds.includes(w));
    out.push({k:'warn',t:`Este reparto usa <b>${n} días</b> y tu semana tiene ${had}. ${add.length?'Añadimos '+rpDays(add)+'. ':''}${rem.length?'Quitamos '+rpDays(rem)+'. ':''}Arrastra una sesión si prefieres otro día.`});
  }
  if(st.empty)return out;
  const fb=st.res.freqBefore,fa=rpFreq(st.sess.map(x=>x.exs));
  RP_MAIN.filter(g=>fa[g]<fb[g]).forEach(g=>out.push({k:'warn',t:`Pierdes frecuencia en <b>${g.toLowerCase()}</b>: de ${fb[g]}× a ${fa[g]}× por semana.`}));
  const up=RP_MAIN.filter(g=>fa[g]>fb[g]);
  if(up.length)out.push({k:'ok',t:`Ganas frecuencia en <b>${rpJoin(up.map(g=>g.toLowerCase()))}</b>: cada uno se entrena más veces por semana con las mismas series.`});
  const sorted=[...st.sess].sort((a,b)=>a.day.wd-b.day.wd);
  const big=[['Pecho',['CH']],['Espalda',['LA','UB']],['Cuádriceps',['QU']],['Isquios',['HA']],['Glúteos',['GL']]];
  const vol=(se,at)=>se.exs.reduce((q,e)=>q+rpSets(e)*at.reduce((z,a)=>z+(e.m[a]||0),0),0);
  let shown=0;
  for(let i=0;i<sorted.length-1&&shown<2;i++){const a=sorted[i],b=sorted[i+1];if(b.day.wd-a.day.wd!==1)continue;
    const g=big.find(x=>vol(a,x[1])>=3&&vol(b,x[1])>=3);
    if(g){shown++;out.push({k:'warn',t:`<b>${a.day.title}</b> y <b>${b.day.title}</b> caen en días seguidos y los dos cargan ${g[0].toLowerCase()}: solo 24 h de descanso.`})}}
  sorted.filter(se=>!se.locked&&se.n>0&&se.n<3).forEach(se=>out.push({k:'warn',t:`<b>${se.day.title}</b> queda con ${se.n} ejercicios: no hay más que encajen con su foco.`}));
  const long=sorted.filter(se=>!se.locked&&st.tmin&&!st.fit&&se.min>st.tmin*1.1);
  if(long.length)out.push({k:'warn',t:`${rpJoin(long.map(se=>'<b>'+se.day.title+'</b>'))} ${long.length>1?'duran más de':'dura unos '+long[0].min+' min,'} ${long.length>1?st.tmin+' min, que es tu tiempo por sesión':'y tu tiempo por sesión es de '+st.tmin+' min'}.`,act:'Ver cómo ajustarlo'});
  return out;
}
/* ---------- vista previa ---------- */
const rpAvIcon={warn:'warn',bad:'warn',ok:'check',info:'info'};
function rpAgenda(st){
  const before=st.view==='before';
  const rows=[];
  for(let wd=1;wd<=7;wd++){
    const se=before?null:st.sess.find(x=>x.day.wd===wd);
    const bs=before?st.before.find(x=>x.wd===wd):null;
    const head=`<div class="dc"><small>${RPS[wd-1]}</small><b>${st.wk.dates[wd-1]}</b></div>`;
    if(!se&&!bs){rows.push(`<div class="rp-day rest" data-wd="${wd}">${head}<div class="rp-rest">Descanso</div></div>`);continue}
    if(bs){
      const exs=bs.ex.map(id=>RP_EX[id]);const r=rpRole(bs.n);
      rows.push(`<div class="rp-day" data-wd="${wd}">${head}<div class="rp-sess" style="--c:${RPC[r.k==='rest'?'mix':r.k]}"><div class="tx"><strong>${bs.n}</strong><div class="f">${rpFocusLine(exs)}</div><div class="n">${exs.filter(e=>!e.cardio).length} ejercicios · ${rpMin(exs)} min</div></div></div></div>`);continue;
    }
    const c=RPC[se.day.role.k==='rest'?'mix':se.day.role.k];
    const sel=st.sel===wd;
    const grip=se.locked?`<span class="rp-grip" style="color:var(--done)" aria-label="Hecha: no se mueve"><span class="mark ok">${ic('check',16)}</span></span>`:`<span class="rp-grip" data-grip aria-label="Arrastrar para cambiar de día">${ic('grip',20)}</span>`;
    const line=st.empty?`<div class="f">${se.tpl.name}</div><div class="n">${se.tpl.n} ejercicios de plantilla</div>`:`<div class="f">${rpFocusLine(se.exs)}</div><div class="n">${se.locked?'Hecha · se queda como está':`${se.n} ejercicios · ${se.min} min`}</div>`;
    rows.push(`<div class="rp-day ${st.today===wd?'today':''}" data-wd="${wd}"><div class="dc"><small>${RPS[wd-1]}</small><b>${st.wk.dates[wd-1]}</b></div>
     <div style="min-width:0"><div class="rp-sess ${sel?'sel':''} ${se.locked?'lock':''} ${st.empty?'empty':''}" data-sel="${wd}" style="--c:${c}" role="button" tabindex="0"><div class="tx"><strong>${se.day.title}${st.flash===wd?'':''}</strong>${line}</div>${grip}</div></div>
     ${sel?rpDetail(st,se):''}</div>`);
  }
  return `<div class="rp-agenda ${st.anim?'rp-anim':''}">${rows.join('')}</div>`;
}
function rpDetail(st,se){
  if(st.empty){
    const alts=se.tpl.alts;
    return `<div class="rp-detail"><div class="rp-ex" style="grid-template-columns:1fr"><b>${se.tpl.name}</b><span>${se.tpl.n} ejercicios · plantilla más compatible con ${se.day.label.toLowerCase()}</span></div>
     <div class="rp-moveto" style="border-top:0;margin-top:0;padding-top:0"><small>Otra plantilla</small>${alts.map((a,i)=>`<span class="chip ${i===0?'on':''}">${a}</span>`).join('')}</div></div>`;
  }
  let html='';let prev=null;
  se.exs.forEach(e=>{
    const rx=e.cardio?'15 min · ritmo suave':`${e.s} × ${e.r} · RIR ${e.rir} · ${Math.floor(e.rest/60)>0?`${(e.rest/60).toString().replace('.',',')} min`:`${e.rest} s`}`;
    const mv=!se.locked&&e.from!==se.day.title&&e.from;
    html+=`<div class="rp-ex ${e.ss?'rp-ss':''}"><b>${e.n}</b>${mv?`<em class="mv">de ${e.from}</em>`:'<em style="visibility:hidden">·</em>'}<span>${rx}${e.ss?' · superserie':''}</span></div>`;
  });
  const moveto=se.locked?'':`<div class="rp-moveto"><small>Mover a</small>${[1,2,3,4,5,6,7].map(d=>{const o=st.sess.find(x=>x.day.wd===d);return `<button data-moveto="${d}" class="${d===se.day.wd?'here':''}" ${o&&o.locked?'disabled':''} aria-label="${RPF[d-1]}${o&&!o.locked&&d!==se.day.wd?', intercambia con '+o.day.title:''}">${RPL[d-1]}</button>`}).join('')}</div>`;
  return `<div class="rp-detail">${html}${moveto}</div>`;
}
function rpVolume2(st){
  const after=rpVolume(st.sess.map(x=>x.exs)),fa=rpFreq(st.sess.map(x=>x.exs));
  const vb=st.res.volBefore,fb=st.res.freqBefore;
  const mx=Math.max(...Object.values(after),...Object.values(vb),1)*1.08;
  return `<div class="rp-vh"><span>Músculo</span><span></span><span>Series</span><span>Veces</span></div>`+RP_GROUPS.map(([g])=>{
    const a=after[g],b=vb[g],d=a-b;
    const fq=fb[g]===fa[g]?`${fa[g]}×`:`${fb[g]}→${fa[g]}×`;
    return `<div class="rp-vr"><span class="nm">${g}</span><span class="rp-bar"><i style="width:${(a/mx*100).toFixed(1)}%"></i><u style="left:${(b/mx*100).toFixed(1)}%"></u></span><span class="v">${a.toFixed(a%1?1:0).replace('.',',')}</span><span class="rp-fq ${fa[g]>fb[g]?'up':fa[g]<fb[g]?'dn':''}">${fq}</span></div>`;
  }).join('');
}
function rpPickStrip(st){
  const have=st.cur.length;
  const list=(have?RP_SPLITS.filter(s=>rpTrainDays(s)===have&&!s.t.includes('pl')):RP_SPLITS.filter(s=>s.t.includes('rec'))).filter(s=>s.id!==st.split).slice(0,6);
  const cur=rpS(st.split);
  const items=[cur,...list];
  return `<div class="rp-pick">${items.map(s=>`<button class="rp-pk ${s.id===st.split?'on':''}" data-split="${s.id}" aria-label="${s.n}"><strong>${s.n}</strong>${rpStrip(s.p,{sm:true})}<small>${rpFreqHead(s)?rpFreqHead(s)+'× por músculo':RPLV[s.lv]}</small></button>`).join('')}<button class="rp-pk all" data-act="todos">${ic('filter',20)}<small>Ver los ${RP_SPLITS.length}</small></button></div>`;
}
function rpCta(st,label='Revisar cambios',o={}){
  return `<div class="rp-cta pl-glass"><button class="rp-gh" data-act="opts" aria-label="Cómo aplicar: alcance y contenido de las sesiones">${ic('sliders2',22)}</button><button class="pill pri ${o.off?'off':''}" data-act="${o.act||'revisar'}">${label}</button></div>`;
}
const rpModeText=m=>m==='clean'?'Sesiones vacías':m==='prebuilt'?'Con plantillas':'Conservar y reubicar';
function rpScopeText(st){
  const w=st.scope==='several'?'Semanas '+rpJoin((st.weeksSel||[5]).map(String)):st.scope==='block'?'Todo el bloque':st.scope==='program'?'Todo el programa':'Solo esta semana';
  return w+' · '+rpModeText(st.mode);
}
function rpPreview(st,o={}){
  const s=rpS(st.split),n=rpTrainDays(s),w=rpWarn(st);
  const first=st.sess[0];
  return `<div class="topbar"><button class="fb glass" data-act="catalogo" aria-label="Atrás">${ic('back')}</button></div>
  <div class="scroll rp-page tb">
   <div class="rp-eye">Semana ${st.wk.n} · ${st.wk.range} · hoy: ${o.hoy||st.hoy||'Personalizado'}</div>
   <h1 class="rp-h1">${s.n}</h1>
   <p class="rp-lead">${s.d}</p>
   <div class="rp-meta">${rpMeta(s)}</div>
   <div class="mod" style="padding:14px 14px 10px">
    <div class="mod-h" style="margin-bottom:6px"><span class="ey">Tu semana</span><span class="rp-seg2" role="tablist"><button data-view="before" class="${st.view==='before'?'on':''}" role="tab">Antes</button><button data-view="after" class="${st.view==='after'?'on':''}" role="tab">Después</button></span></div>
    <div class="rp-hint"><span>${st.view==='before'?'Así está hoy tu semana.':'Arrastra una sesión por su asa, o tócala y elige un día.'}</span>${st.dirty&&st.view==='after'?`<button data-act="reset">Restablecer</button>`:''}</div>
    ${rpAgenda(st)}
    <button class="rp-scope" data-act="opts"><span>${rpScopeText(st)}</span><b>Cambiar</b></button>
   </div>
   ${w.length&&st.view==='after'?`<div class="rp-sec">Avisos<span>${w.length}</span></div><div class="mod" style="padding:12px 16px">${w.map(a=>`<div class="rp-av ${a.k}"><i>${ic(rpAvIcon[a.k]||'info',15)}</i><div class="tx">${a.t}${a.act?`<button data-act="decidir">${a.act}</button>`:''}</div></div>`).join('')}</div>`:''}
   <div class="rp-sec">Repartos para tu semana<span>${st.cur.length?st.cur.length+' días':'recomendados'}</span></div>
   ${rpPickStrip(st)}
   ${st.empty?'':`<div class="rp-sec">Volumen y frecuencia</div><div class="mod" style="padding:14px 16px 12px">${rpVolume2(st)}<div class="rp-legend"><span><i></i>después</span><span><u></u>antes</span><span>Los ejercicios se mueven enteros: las series no cambian.</span></div></div>`}
  </div>${st.empty?rpCta(st,'Aplicar reparto',{act:'aplicar'}):rpCta(st)}${st.sheet?rpSheetRoute(st):''}`;
}
/* interacción común: arrastrar sesiones, tocarlas, cambiar de reparto, abrir hojas */
function rpWire(ph,getSt,rerender,extra){
  const host=ph.querySelector('.rp-host');if(!host)return;
  host.addEventListener('click',e=>{
    const st=getSt();const t=e.target;
    const sp=t.closest('[data-split]');
    if(sp&&sp.closest('.rp-pick')){rpCompute(st,sp.dataset.split);RPSH.split=st.split;st.anim=true;rerender();st.anim=false;return}
    const vw=t.closest('[data-view]');if(vw){st.view=vw.dataset.view;rerender();return}
    const mt=t.closest('[data-moveto]');
    if(mt){const se=st.sess.find(x=>x.day.wd===st.sel);if(se&&rpMove(st,se.day.wd,+mt.dataset.moveto)){st.sel=+mt.dataset.moveto;st.anim=true;rerender();st.anim=false}return}
    const sl=t.closest('[data-sel]');
    if(sl&&!t.closest('[data-grip]')){const wd=+sl.dataset.sel;st.sel=st.sel===wd?null:wd;rerender();return}
    const ac=t.closest('[data-act]');
    if(ac){const a=ac.dataset.act;
      if(a==='opts'){st.sheet='opts';rerender();return}
      if(a==='reset'){rpCompute(st,st.split,st.days0||st.days);st.anim=true;rerender();st.anim=false;return}
      if(a==='cerrar'){st.sheet=null;rerender();return}
      if(a==='decidir'){st.sheet='dec';rerender();return}
      if(a==='catalogo'){rpGo('rp-catalogo');return}
      if(a==='todos'){rpGo('rp-catalogo');return}
      if(a==='revisar'){RPSH.split=st.split;RPSH.sess=st.sess;rpGo(st.locked?'rp-revision-curso':'rp-revision');return}
      if(extra&&extra(a,st,rerender,ac))return;
    }
    if(extra)extra(null,st,rerender,t);
  });
  host.addEventListener('pointerdown',e=>{
    const g=e.target.closest('[data-grip]');if(!g)return;
    const st=getSt();if(st.view==='before')return;
    const card=g.closest('.rp-sess'),rows=[...host.querySelectorAll('.rp-day')];
    const from=+card.dataset.sel;e.preventDefault();
    try{g.setPointerCapture(e.pointerId)}catch(_){}
    const y0=e.clientY;let over=null;card.classList.add('lift');
    const mv=ev=>{card.style.transform=`translateY(${ev.clientY-y0}px) scale(1.025)`;
      const r=rows.find(r=>{const b=r.getBoundingClientRect();return ev.clientY>=b.top&&ev.clientY<b.bottom});
      over=r?+r.dataset.wd:null;rows.forEach(x=>x.classList.toggle('rp-over',x===r&&+x.dataset.wd!==from))};
    const up=()=>{g.removeEventListener('pointermove',mv);g.removeEventListener('pointerup',up);g.removeEventListener('pointercancel',up);
      const ok=over&&over!==from&&rpMove(st,from,over);
      if(ok){st.sel=over;st.anim=true}rerender();st.anim=false};
    g.addEventListener('pointermove',mv);g.addEventListener('pointerup',up);g.addEventListener('pointercancel',up);
  });
}
/* un escena interactiva = estado + vista + cableado */
function rpScene(def){
  let st=null;
  const rerender=()=>{const host=document.querySelector('.rp-host');if(!host)return;const sc=host.querySelector('.scroll');const top=sc?sc.scrollTop:0;
    host.innerHTML=def.view(st);const sc2=host.querySelector('.scroll');if(sc2)sc2.scrollTop=top;if(def.afterRender)def.afterRender(host,st)};
  scene({id:def.id,tab:def.tab||'reparto',title:def.title,notes:def.notes,scrollTo:def.scrollTo,
    html:()=>{st=def.make();return `<div class="rp-host">${def.view(st)}</div>`},
    after:ph=>{if(!st)st=def.make();const host=ph.querySelector('.rp-host');
      const re=()=>{const h=ph.querySelector('.rp-host');const sc=h.querySelector('.scroll');const top=sc?sc.scrollTop:0;h.innerHTML=def.view(st);const sc2=h.querySelector('.scroll');if(sc2)sc2.scrollTop=top;if(def.afterRender)def.afterRender(h,st)};
      rpWire(ph,()=>st,re,def.extra);if(def.afterRender)def.afterRender(host,st);if(def.scrollTo){const sc=host.querySelector('.scroll');if(sc)sc.scrollTop=def.scrollTo}}});
}
/* ---------- hojas ---------- */
function rpSheetRoute(st){
  if(st.sheet==='opts')return rpOptsSheet(st);
  if(st.sheet==='dec')return rpDecSheet(st);
  return '';
}

/* ---- rp-ui2.js ---- */
/* ===== Catálogo ===== */
const RP_CURPAT=['Pierna A','Empuje','Descanso','Tirón','Pierna B','Descanso','Descanso'];
function rpCatFilter(st){
  const q=(st.q||'').trim().toLowerCase();
  return RP_SPLITS.filter(s=>(!st.tag||s.t.includes(st.tag))&&(st.days==='all'||rpTrainDays(s)===+st.days)&&(!q||[s.n,s.orig,s.d,...s.pro,...s.con].join(' ').toLowerCase().includes(q)));
}
function rpCatList(st){
  const list=rpCatFilter(st),have=st.cur.length;
  if(!list.length)return `<div class="rp-empty"><b>Sin resultados</b>No hay repartos con «${st.q||'esos filtros'}». Prueba con otra palabra o quita un filtro.</div>`;
  const pick=s=>st.mode!=='browse'&&st.picked.includes(s.id);
  const fit=list.filter(s=>rpTrainDays(s)===have),other=list.filter(s=>rpTrainDays(s)!==have);
  const grp=(t,n)=>`<div class="rp-sec" style="margin-top:14px">${t}<span>${n}</span></div>`;
  return (fit.length?grp(`Encajan con tus ${have} días`,fit.length)+fit.map(s=>rpCard(s,{sel:pick(s)})).join(''):'')
   +(other.length?grp(have&&fit.length?'Cambian tus días':'Piden otro número de días',other.length)+other.map(s=>rpCard(s,{sel:pick(s),have})).join(''):'');
}
function rpCustomModule(st){
  if(!st.custom)return `<div class="mod"><div class="mod-h"><span class="ey">Reparto propio</span></div><div style="font:600 calc(15px*var(--fs)) Inter;margin-top:10px">Crea tu propio reparto</div><div class="cap" style="margin:4px 0 14px">Parte de una semana en blanco y define qué hace cada día.</div><button class="pill pri" style="width:100%" data-act="crear">Crear reparto</button></div>`;
  const c=st.custom;
  return `<div class="mod"><div class="mod-h"><span class="ey">Reparto propio</span></div><div style="font:600 calc(15px*var(--fs)) Inter;margin-top:10px">${c.name}</div><div class="cap" style="margin:4px 0 14px">${rpTrainDays({p:c.p})} días a la semana</div>${rpStrip(c.p)}<div class="rp-btnrow" style="margin-top:14px"><button class="pill sec sm" data-act="crear">Editar</button><button class="pill sec sm" data-act="aplicar-propio">Aplicar</button></div></div>`;
}
function rpCatalog(st){
  const sel=st.mode!=='browse';
  const bar=st.mode==='compare'?`<div class="rp-cta pl-glass" style="padding:8px"><button class="pill pri ${st.picked.length===2?'':'off'}" style="flex:1" data-act="${st.picked.length===2?'comparar':'x'}">${st.picked.length===2?'Comparar 2':'Elige dos repartos'}</button></div>`
    :st.mode==='multi'&&st.picked.length?`<div class="rp-cta pl-glass" style="padding:8px"><button class="pill pri" style="flex:1" data-act="varios">${st.picked.length===1?'Aplicar 1 reparto':`Aplicar ${st.picked.length} repartos`}</button></div>`:'';
  return `<div class="scroll rp-page ${sel?'':'nocta'}" style="padding-top:52px;${sel?'padding-bottom:140px':''}">
   <div class="toprow"><button class="ib fill" data-act="atras" aria-label="Atrás">${ic('back')}</button><button class="ib fill" data-act="buscar" aria-label="Buscar">${ic('search')}</button></div>
   <h1 class="ptitle" style="font-size:calc(27px*var(--fs));margin:4px 0 0">Reparto</h1>
   <p class="rp-lead" style="margin:6px 0 14px">Elige cómo se reparten los grupos en la semana.</p>
   ${st.search||st.q?`<label class="rp-search">${ic('search',18)}<input type="text" placeholder="Busca un reparto o un músculo" aria-label="Buscar" value="${st.q||''}"></label>`:''}
   ${st.search||st.q?'':`<div class="mod tight rp-now" style="padding:14px 16px"><div class="tx"><span class="ey">Ahora · semana 5</span><strong style="margin-top:6px">Personalizado · 4 días</strong><div style="margin-top:12px">${rpStrip(RP_CURPAT)}</div></div></div>`}
   <div class="rp-filters">
    <div><span class="rp-lab">Días por semana</span><div class="rp-seg">${['all',2,3,4,5,6].map(d=>`<button data-days="${d}" class="${String(st.days)===String(d)?'on':''}">${d==='all'?'Todos':d}</button>`).join('')}</div></div>
    <div class="rp-chips" role="tablist" aria-label="Etiquetas"><button class="chip ${!st.tag?'on':''}" data-tag="">Todos</button>${Object.entries(RPTAG).map(([k,v])=>`<button class="chip ${st.tag===k?'on':''}" data-tag="${k}">${v}</button>`).join('')}</div>
   </div>
   ${st.tag==='custom'?rpCustomModule(st):`<div class="rp-list">${rpCatList(st)}</div>`}
   <div class="rp-btnrow"><button class="pill sec sm" data-act="modo-comparar">${st.mode==='compare'?'Cancelar':'Comparar'}</button><button class="pill sec sm" data-act="modo-propio" style="${st.tag==='custom'?'background:#f2eee614':''}">Personalizado</button></div>
   <div style="text-align:center;margin-top:4px"><button class="act faint" style="width:100%;justify-content:center" data-act="modo-varios">${st.mode==='multi'?'Cancelar selección':'Seleccionar varios'}</button></div>
  </div>${bar}${st.sheet==='cmp'?rpCompareSheet(rpS(st.picked[0]),rpS(st.picked[1])):''}${st.sheet==='multi'?rpMultiSheet(st):''}${st.sheet==='own'?rpOwnSheet(st):''}`;
}
function rpCatMake(over={}){return {cur:RP_CUR.slice(),tag:null,days:'all',q:'',search:false,mode:'browse',picked:[],sheet:null,custom:null,...over}}
function rpCatExtra(a,st,re,t){
  if(a==='buscar'){st.search=!st.search;re();const i=document.querySelector('.rp-search input');if(i)i.focus();return true}
  if(a==='modo-comparar'){st.mode=st.mode==='compare'?'browse':'compare';st.picked=[];re();return true}
  if(a==='modo-varios'){st.mode=st.mode==='multi'?'browse':'multi';st.picked=[];re();return true}
  if(a==='modo-propio'){st.tag=st.tag==='custom'?null:'custom';re();return true}
  if(a==='comparar'){st.sheet='cmp';re();return true}
  if(a==='varios'){st.sheet='multi';re();return true}
  if(a==='crear'){st.sheet='own';re();return true}
  if(a==='cerrar'){st.sheet=null;re();return true}
  if(a==='atras'){return true}
  if(!a&&t){
    const d=t.closest('[data-days]');if(d){st.days=d.dataset.days;re();return true}
    const g=t.closest('[data-tag]');if(g){st.tag=g.dataset.tag||null;re();return true}
    const c=t.closest('.rp-card');
    if(c){const id=c.dataset.split;
      if(st.mode==='browse'){RPSH.split=id;rpGo('rp-semana');return true}
      if(st.picked.includes(id))st.picked=st.picked.filter(x=>x!==id);else{if(st.mode==='compare'&&st.picked.length>=2)st.picked.shift();st.picked.push(id)}
      re();return true}
    if(t.closest('.rp-scrim'))return false;
  }
  return false;
}
function rpCatAfter(host,st){
  const ch=host.querySelector('.rp-chips'),on=ch&&ch.querySelector('.chip.on');if(on)ch.scrollLeft=Math.max(0,on.offsetLeft-24);
  const inp=host.querySelector('.rp-search input');
  if(inp){inp.oninput=()=>{st.q=inp.value;const l=host.querySelector('.rp-list');if(l)l.innerHTML=rpCatList(st)}}
}
/* hoja Comparar */
function rpCompareSheet(a,b){
  const pa=a.p,pb=b.p,diff=[];
  for(let i=0;i<7;i++){const x=rpRole(pa[i]||'Descanso').k==='rest'?'Descanso':pa[i],y=rpRole(pb[i]||'Descanso').k==='rest'?'Descanso':pb[i];if(rpNorm(x)!==rpNorm(y))diff.push(`${RPF[i][0].toUpperCase()+RPF[i].slice(1)}: de ${x.toLowerCase()} a ${y.toLowerCase()}`)}
  const fa=rpSplitFreq(a),fb=rpSplitFreq(b);
  const card=(s,i)=>`<div class="rp-mod"><strong style="font:600 calc(15px*var(--fs)) Inter"><span class="rp-n">${i}</span>${s.n}</strong><div class="cap" style="margin:4px 0 12px">${rpTrainDays(s)} días a la semana · ${RPLV[s.lv]}</div>${rpStrip(s.p)}</div>`;
  const pl=a.t.includes('pl')||b.t.includes('pl');
  return `<div class="pl-scrim" data-act="cerrar"></div><div class="pl-sheet rp-sheet" role="dialog" aria-label="Comparar repartos"><div class="rp-sh-t">Comparar repartos</div>
   ${card(a,1)}${card(b,2)}
   <div class="rp-mod"><span class="ey">Qué cambia</span><div style="font:600 calc(14.5px*var(--fs)) Inter">${diff.length===0?'Los dos repartos ocupan la semana igual.':diff.length===1?'Cambia 1 día de la semana.':`Cambian ${diff.length} días de la semana.`}</div>${diff.map(d=>`<div class="cap" style="margin-top:6px;color:var(--muted)">${d}</div>`).join('')}</div>
   ${pl?'':`<div class="rp-mod"><span class="ey">Veces por semana · por músculo</span><div class="rp-cmp"><span></span><span class="h"><span class="rp-n">1</span></span><span class="h"><span class="rp-n">2</span></span>${RP_MAIN.map(g=>{const x=fa[g],y=fb[g];return `<span class="c l">${g}</span><span class="c v ${x>y?'win':''}">${x}×</span><span class="c v ${y>x?'win':''}">${y}×</span>`}).join('')}</div><div class="rp-note">La frecuencia sale del foco de cada día del reparto: cuenta los días que piden ese músculo.</div></div>`}
  </div>`;
}
/* hoja Aplicar varios repartos */
function rpMultiSheet(st){
  const ids=st.picked.length?st.picked:['ul_x4','ppl_ul'];
  const sp=ids.map(rpS);
  const scope=st.mscope||'Bloque',mode=st.mmode||null;
  return `<div class="pl-scrim" data-act="cerrar"></div><div class="pl-sheet rp-sheet" role="dialog" aria-label="Aplicar varios repartos"><div class="rp-sh-t">Aplicar varios repartos</div>
   <p class="rp-sh-s" style="margin-top:-6px">Los repartos elegidos se reparten por las semanas destino en el mismo orden. Si hay más semanas que repartos, se repiten en ciclo.</p>
   <div class="rp-mod"><span class="ey">Repartos en orden</span>${sp.map((s,i)=>`<div style="padding:6px 0"><strong style="font:600 calc(14.5px*var(--fs)) Inter">${i+1} · ${s.n}</strong><div class="cap">${rpTrainDays(s)} días a la semana</div></div>`).join('')}</div>
   <div class="rp-mod"><span class="ey">Inicio de semana</span><div class="rp-seg">${RPL.map((d,i)=>`<button class="${i===0?'on':''}">${d}</button>`).join('')}</div></div>
   <div class="rp-mod"><span class="ey">Destino</span><div class="rp-seg">${['Varias','Bloque','Programa'].map(x=>`<button class="${x===scope?'on':''}">${x}</button>`).join('')}</div>
    <div class="rp-note">Bloque 2 · Fuerza: <b>semanas 4 y 5</b>. La 1.ª semana usa «${sp[0].n}» y la 2.ª, «${(sp[1]||sp[0]).n}».</div></div>
   <div class="rp-mod"><span class="ey">Contenido de las sesiones</span>
    ${[['migrate','Conservar y reubicar','Reparte los ejercicios de cada semana según el reparto que le toque, sin rehacer series.'],['clean','Sesiones vacías','Reemplaza las semanas destino por sesiones base vacías.'],['prebuilt','Con ejercicios de plantilla','Carga ejercicios desde la mejor plantilla compatible de cada reparto.']].map(([k,t,d])=>`<div class="rp-rad"><span class="rp-rd ${mode===k?'on':''}"></span><div class="tx"><strong>${t}</strong><span class="d">${d}</span></div></div>`).join('')}</div>
   <div class="rp-stk"><button class="pill pri ${mode?'':'off'}" style="width:100%;margin:0">${mode?'Revisar repartos':'Elige el contenido'}</button></div></div>`;
}
/* editor del reparto propio */
const RP_PRESETS=['Descanso','Torso','Pierna','Empuje','Tirón','Cuerpo completo'];
function rpOwnSheet(st){
  const o=st.own||(st.own={name:'Mi reparto',desc:'',pat:['Pierna','Empuje','Descanso','Tirón','Pierna','Descanso','Descanso'],open:2});
  const train=o.pat.filter(x=>rpRole(x).k!=='rest').length;
  const fq=rpSplitFreq({p:o.pat});const f=(()=>{const v=RP_MAIN.map(g=>fq[g]).sort((a,b)=>a-b);return v[4]})();
  return `<div class="pl-scrim" data-act="cerrar"></div><div class="pl-sheet rp-sheet" role="dialog" aria-label="Reparto propio"><div class="rp-sh-t">Reparto propio</div>
   <div class="rp-field"><small>Nombre</small><div>${o.name}</div></div>
   <div class="rp-field"><small>Descripción</small><div style="color:${o.desc?'var(--ink)':'var(--faint)'}">${o.desc||'Qué hace cada día y para quién es'}</div></div>
   <div class="rp-mod"><span class="ey">Inicio de semana</span><div class="rp-seg">${RPL.map((d,i)=>`<button class="${i===0?'on':''}">${d}</button>`).join('')}</div></div>
   <div class="rp-mod"><span class="ey">Distribución semanal</span>
    ${o.pat.map((l,i)=>`<div class="rp-cd"><b>${RPF[i][0].toUpperCase()+RPF[i].slice(1)}</b><button class="chip ${rpRole(l).k==='rest'?'':'on'}" data-own="${i}">${l}</button>${o.open===i?`<div class="opts">${RP_PRESETS.map(p=>`<button class="chip ${rpNorm(p)===rpNorm(l)?'on':''}" data-pick="${p}">${p}</button>`).join('')}</div>`:''}</div>`).join('')}
    <div style="margin-top:14px">${rpStrip(o.pat)}</div>
    <div class="rp-note"><b>${train} días</b> de entreno · cada grupo ${f}× por semana, según el foco de cada día.</div></div>
   <div class="rp-stk"><button class="pill pri" style="width:100%;margin:0" data-act="guardar-propio">Guardar</button></div></div>`;
}
/* ---------- escenas del catálogo ---------- */
const rpCatDef=(id,title,over,notes,more={})=>rpScene({id,title,notes,make:()=>rpCatMake(over),view:rpCatalog,extra:(a,st,re,t)=>{
  if(st.sheet==='own'){
    if(!a&&t){const c=t.closest('[data-own]');if(c){const i=+c.dataset.own;st.own.open=st.own.open===i?null:i;re();return true}
      const p=t.closest('[data-pick]');if(p){st.own.pat[st.own.open]=p.dataset.pick;st.own.open=null;re();return true}}
    if(a==='guardar-propio'){st.custom={name:st.own.name,p:st.own.pat};st.sheet=null;st.tag='custom';re();return true}
  }
  return rpCatExtra(a,st,re,t);
},afterRender:rpCatAfter,...more});

/* ---- rp-ui3.js ---- */
/* ===== Semana vacía ===== */
const RP_TPL={push:{name:'Push Day · PPL',n:6,alts:['Pectorales','Deltoides']},pull:{name:'Pull Day · PPL',n:6,alts:['Dorsales','Bíceps']},legs:{name:'Leg Day · Cuádriceps',n:6,alts:['Cuádriceps','Isquios']},torso:{name:'Torso A · Upper/Lower',n:7,alts:['Pectorales','Dorsales']},full:{name:'Full Body · Base',n:7,alts:['Cuerpo completo']},mix:{name:'Full Body · Base',n:7,alts:['Cuerpo completo']}};
function rpFillEmpty(st,s){
  const labels=s.p.filter(x=>rpRole(x).k!=='rest'),titles=rpTitles(labels);
  st.res={volBefore:{},volAfter:{},freqBefore:{},freqAfter:{},trims:[],misfit:[],sess:[],days:[]};
  st.sess=labels.map((l,i)=>({day:{i,wd:st.days[i],label:l,title:titles[i],role:rpRole(l)},exs:[],n:0,min:0,tpl:RP_TPL[rpRole(l).k]||RP_TPL.mix,locked:false}));
  st.sel=null;
}
/* ===== Decisiones ===== */
function rpDecisions(st){
  const out=[];
  st.sess.forEach(se=>se.exs.forEach(e=>{if(!se.locked&&(e.cardio||(e.t===3&&Object.keys(e.m).every(k=>k==='CO'))))out.push({ex:e,at:se.day.title,kind:e.cardio?'Cardio':'Core'})}));
  return out;
}
function rpPlaceEx(st,id,title){
  let ex=null;st.sess.forEach(se=>{const i=se.exs.findIndex(e=>e.id===id);if(i>=0){ex=se.exs.splice(i,1)[0]}});
  const to=st.sess.find(se=>se.day.title===title);if(ex&&to){to.exs.push(ex)}
  st.sess.forEach(se=>{se.min=rpMin(se.exs);se.n=se.exs.filter(e=>!e.cardio).length});
}
function rpDecSheet(st){
  const dec=rpDecisions(st);
  const names=st.sess.filter(se=>!se.locked).map(se=>se.day.title);
  const long=st.sess.filter(se=>!se.locked&&st.tmin&&(!st.fit&&se.min>st.tmin*1.1||st.fit&&st.res.trims.some(t=>t.day===se.day.title)));
  const trims=st.res.trims,rem=trims.reduce((s,t)=>s+t.n,0);
  return `<div class="pl-scrim" data-act="cerrar"></div><div class="pl-sheet rp-sheet" role="dialog" aria-label="Falta decidir"><div class="rp-sh-t">Antes de aplicar</div>
   <p class="rp-sh-s" style="margin-top:-6px">Estos ejercicios no piden un día concreto y algún día se pasa de tiempo. Ya tienen una propuesta: cámbiala si quieres.</p>
   <div class="rp-mod">${dec.map(d=>`<div class="rp-dec"><strong>${d.ex.n}<span class="rp-tag">propuesta</span></strong><p>${d.kind==='Cardio'?'El cardio no depende del músculo: va al día con menos carga.':'Es un remate de core: encaja en cualquier día.'} Ahora va en <b style="color:var(--ink)">${d.at}</b>.</p><div class="chips">${names.map(n=>`<button class="chip ${n===d.at?'on':''}" data-decpl="${d.ex.id}|${n}">${n}</button>`).join('')}</div></div>`).join('')}
    ${st.tmin?`<div class="rp-dec"><strong>Tiempo por sesión: ${st.tmin} min</strong><p>${long.length?`${rpJoin(long.map(se=>'<b style="color:var(--ink)">'+se.day.title+'</b>'))} ${st.fit?(long.length>1?'quedan dentro de tu tiempo':'queda dentro de tu tiempo'):(long.length>1?'se pasan':'se pasa')}.`:'Todas las sesiones caben.'} ${st.fit?`Se quitan ${rem} ${rem===1?'serie':'series'} de accesorios; los ejercicios principales no se tocan.`:'Puedes dejarlas así o quitar series de accesorios hasta que quepan.'}</p><div class="chips"><button class="chip ${!st.fit?'on':''}" data-fit="0">Dejarlo así</button><button class="chip ${st.fit?'on':''}" data-fit="1">Ajustar a ${st.tmin} min</button></div></div>`:''}
   </div>
   <div class="rp-stk"><button class="pill pri" style="width:100%;margin:0" data-act="cerrar">Listo</button></div></div>`;
}
/* ===== Hoja «Cómo aplicar» ===== */
const RP_WEEKS=[{n:1,done:1},{n:2,done:1},{n:3,done:1},{n:4,curso:1},{n:5},{n:6,dl:1}];
function rpOptsSheet(st){
  const scope=st.scope,mode=st.mode,hasSess=!st.empty,adv=!!st.advanced,cal=!!st.calendarized;
  const E=st.sess.reduce((s,x)=>s+x.exs.filter(e=>!e.cardio).length,0),cardio=st.sess.reduce((s,x)=>s+x.exs.filter(e=>e.cardio).length,0);
  const S=st.sess.reduce((s,x)=>s+x.exs.reduce((q,e)=>q+rpSets(e),0),0);
  const weeks=st.weeksSel||[5];
  const modes=[hasSess?['migrate','Conservar y reubicar','Reparte tus ejercicios entre los días del nuevo reparto, con sus series, repeticiones, RIR y descansos.']:null,['clean','Sesiones vacías','Crea la estructura de días del reparto sin ejercicios.'],['prebuilt','Con ejercicios de plantilla','Carga ejercicios desde la plantilla más compatible para cada día.']].filter(Boolean);
  const replace=hasSess&&mode!=='migrate';
  const n=st.sess.length;
  return `<div class="pl-scrim" data-act="cerrar"></div><div class="pl-sheet rp-sheet" role="dialog" aria-label="Cómo aplicar">
   <div><div class="rp-sh-t">Cómo aplicar</div><p class="rp-sh-s">${rpS(st.split).n}. Ninguna opción se aplica hasta que confirmes.</p></div>
   ${cal?`<div class="cap" style="margin-top:-4px">El inicio de la semana lo fija el calendario del programa.</div>`:`<div class="rp-mod"><span class="ey">Inicio de semana</span><div class="rp-seg">${RPL.map((d,i)=>`<button class="${i===0?'on':''}">${d}</button>`).join('')}</div></div>`}
   <div class="rp-mod"><span class="ey">Alcance</span><div class="rp-seg">${[['week','Semana'],['several','Varias'],['block','Bloque'],['program','Programa']].map(([k,t])=>`<button data-scope="${k}" class="${scope===k?'on':''}">${t}</button>`).join('')}</div>
    ${scope==='several'?`<div class="rp-wk" style="margin-top:14px">${RP_WEEKS.map(w=>`<button class="rp-wc ${weeks.includes(w.n)?'on':''} ${w.n===5?'now':''} ${w.done||w.curso?'done':''} ${w.dl&&!weeks.includes(w.n)?'dl':''}" data-wk="${w.n}" ${w.done||w.curso?'disabled':''} aria-label="Semana ${w.n}${w.done?', ya hecha':w.curso?', en curso':w.dl?', descarga':''}">${w.n}</button>`).join('')}</div>
     <div class="rp-note"><b>Semanas ${rpJoin(weeks.slice().sort().map(String))}.</b> ${weeks.includes(6)?'La 6 es de descarga: conserva su mitad de series. ':''}Las semanas con los mismos ejercicios repiten el reparto; si traen otros, se calcula aparte. La 4 está en curso: cámbiala desde su propia semana para respetar lo hecho.</div>`:''}
    ${scope==='block'?`<div class="rp-note">Todas las semanas del <b>Bloque 2 · Fuerza</b> (4 y 5). La 4 está en curso y se salta.</div>`:''}
    ${scope==='program'?`<div class="rp-note">Las 6 semanas del programa. Las 3 ya hechas y la 4, en curso, no se tocan.</div>`:''}</div>
   ${adv?`<div class="rp-mod"><span class="ey">Programa por bloques</span><div class="rp-seg">${[['one','Un reparto'],['per','Por bloque']].map(([k,t])=>`<button data-adv="${k}" class="${(st.perBlock?'per':'one')===k?'on':''}">${t}</button>`).join('')}</div>
    ${st.perBlock?`<div class="rp-note" style="margin-top:12px">Se aplicará el reparto elegido a todas las semanas de cada bloque.</div>${[['Bloque 1 · Base','Torso y pierna, 4 días','Semanas 1 a 3 · ya hechas'],['Bloque 2 · Fuerza','Torso y pierna, 4 días','Semanas 4 y 5'],['Bloque 3 · Descarga','Cuerpo completo, 3 días','Semana 6 · conserva su mitad de series']].map(([b,s,n],i)=>`<div style="margin-top:14px"><div class="cap">${b}</div><button class="pill sec sm" style="width:100%;margin-top:6px;justify-content:space-between;${i===0?'opacity:.45':''}">${s}${ic('down',16)}</button><div class="cap" style="margin-top:4px">${n}</div></div>`).join('')}`:''}</div>`:''}
   <div class="rp-mod"><span class="ey">Contenido de las sesiones</span>
    ${modes.map(([k,t,d])=>`<button class="rp-rad" data-mode="${k}"><span class="rp-rd ${mode===k?'on':''}"></span><div class="tx"><strong>${t}</strong><span class="d">${d}</span>${mode===k&&k==='migrate'?`<span class="n">${E} ejercicios y ${S} series pasan a ${n} sesiones del reparto${cardio?', más '+cardio+' de cardio':''}. Antes de aplicar ves el resultado.</span>`:''}</div></button>`).join('')}</div>
   ${mode==='prebuilt'&&hasSess?`<div class="rp-mod"><span class="ey">Plantillas por día</span>${st.sess.slice(0,2).map(se=>{const t=RP_TPL[se.day.role.k]||RP_TPL.mix;return `<div style="padding:8px 0"><div style="font:500 calc(13px*var(--fs)) Inter">${se.day.title} → ${t.name} · ${t.n} ejercicios</div><div class="chips" style="display:flex;gap:6px;margin-top:8px;flex-wrap:wrap">${t.alts.map((a,i)=>`<span class="chip ${i===0?'on':''}" style="height:32px">${a}</span>`).join('')}</div></div>`}).join('')}</div>`:''}
   ${replace?`<div class="rp-imp"><span class="ey" style="color:var(--danger)">Impacto</span><strong style="margin-top:8px">Se reemplazarán ${n} sesiones</strong><p>En ${scope==='week'?'1 semana':scope==='several'?weeks.length+' semanas':scope==='block'?'1 semana':'2 semanas'}. Las sesiones actuales se sustituyen por las del reparto nuevo. Podrás deshacerlo justo después.</p><button class="rp-rad" style="padding:0;border:0;min-height:44px" data-act="aceptar"><span class="rp-ck ${st.accept?'on':''}">${st.accept?ic('check',15):''}</span><div class="tx"><strong style="font-size:calc(13.5px*var(--fs))">Entiendo que se reemplazarán estas sesiones</strong></div></button></div>`:''}
   <div class="rp-stk"><button class="pill pri ${replace&&!st.accept?'off':''}" style="width:100%;margin:0" data-act="${replace&&st.accept?'confirmar':replace?'x':'cerrar'}">${replace?`Reemplazar ${n} sesiones`:'Listo'}</button></div>
  </div>${st.confirm?`<div class="pl-scrim" style="z-index:62"></div><div class="rp-dlg" style="top:auto;bottom:260px;z-index:63"><h3>Confirmar reemplazo</h3><p>Se reemplazarán ${n} sesiones en ${scope==='week'?'1 semana':'2 semanas'}. Después de aplicar podrás deshacerlo desde el aviso o desde ⋯ de la semana.</p><div class="acts"><button data-act="no">Cancelar</button><button class="pr" data-act="si">Sí, reemplazar</button></div></div>`:''}`;
}
function rpOptsExtra(a,st,re,t){
  if(!a&&t){
    const sc=t.closest('[data-scope]');if(sc){st.scope=sc.dataset.scope;if(st.scope==='several')st.weeksSel=st.weeksSel||[5,6];re();return true}
    const md=t.closest('[data-mode]');if(md){st.mode=md.dataset.mode;st.accept=false;re();return true}
    const wk=t.closest('[data-wk]');if(wk&&!wk.disabled){const n=+wk.dataset.wk;const w=st.weeksSel||[5];st.weeksSel=w.includes(n)?w.filter(x=>x!==n):[...w,n];if(!st.weeksSel.length)st.weeksSel=[n];re();return true}
    const ad=t.closest('[data-adv]');if(ad){st.perBlock=ad.dataset.adv==='per';re();return true}
    const dp=t.closest('[data-decpl]');if(dp){const [id,to]=dp.dataset.decpl.split('|');rpPlaceEx(st,id,to);re();return true}
    const ft=t.closest('[data-fit]');if(ft){st.fit=ft.dataset.fit==='1';rpCompute(st,st.split,st.days);re();return true}
  }
  if(a==='aceptar'){st.accept=!st.accept;re();return true}
  if(a==='confirmar'){st.confirm=true;re();return true}
  if(a==='no'){st.confirm=false;re();return true}
  if(a==='si'){st.confirm=false;st.sheet=null;RPSH.split=st.split;rpGo('rp-aplicado');return true}
  return false;
}
/* ===== Revisión: antes y después ===== */
const rpEx2=e=>e.cardio?'15 min · ritmo suave':`${e.s} × ${e.r} · RIR ${e.rir} · ${e.rest>=60?(e.rest/60).toString().replace('.',',')+' min':e.rest+' s'}`;
function rpStats(st){
  const all=st.sess.flatMap(se=>se.exs.map(e=>({e,se})));
  const strength=all.filter(x=>!x.e.cardio);
  const moved=all.filter(x=>!x.se.locked&&x.e.from!==x.se.day.title).length;
  const sets=strength.reduce((s,x)=>s+x.e.s,0);
  const setsBefore=st.before.reduce((s,b)=>s+b.ex.reduce((q,id)=>q+RP_EX[id].s,0),0);
  return {E:strength.length,S:sets,Sb:setsBefore,moved,cardio:all.length-strength.length};
}
function rpReview(st){
  const s=rpS(st.split),k=rpStats(st),trim=st.res.trims.reduce((a,t)=>a+t.n,0),dec=rpDecisions(st);
  const body=st.tab==='musculo'?rpReviewMuscle(st):rpReviewSessions(st);
  const lock=st.locked?st.sess.filter(x=>x.locked).length:0;
  return `<div class="topbar"><button class="fb glass" data-act="volver" aria-label="Volver a la vista previa">${ic('back')}</button></div>
  <div class="scroll rp-page tb"><div class="rp-eye">Semana ${st.wk.n} · ${s.n}</div>
   <h1 class="rp-h1">Revisar cambios</h1>
   <p class="rp-lead" style="font:700 calc(18px*var(--fs))/1.25 Syne;letter-spacing:-.3px;color:var(--ink);margin-bottom:0">${trim?`Se recortan ${trim} ${trim===1?'serie':'series'} para que quepan en ${st.tmin} min.`:`No pierdes nada: ${k.E} ejercicios y ${k.S} series pasan al nuevo reparto.`}</p>
   <div class="rp-sum"><div><b>${k.moved}</b><span>cambian de sesión</span></div><div><b>${k.E}</b><span>ejercicios${k.cardio?' y '+k.cardio+' de cardio':''}</span></div><div><b>${k.S}</b><span>series${k.S===k.Sb?', las mismas':`, antes ${k.Sb}`}</span></div></div>
   ${lock?`<div class="rp-note" style="margin:8px 2px 0">${ic('check',14)} <b>${lock} ${lock===1?'sesión hecha':'sesiones hechas'}</b> se quedan tal cual y cuentan en el volumen de la semana.</div>`:''}
   ${dec.length||(st.tmin&&!st.fit&&st.sess.some(se=>!se.locked&&se.min>st.tmin*1.1))?`<div class="rp-sec">Con propuesta</div><div class="mod" style="padding:12px 16px">${dec.map(d=>`<div class="rp-av"><i>${ic('info',15)}</i><div class="tx"><b>${d.ex.n}</b> va en ${d.at}.</div></div>`).join('')}${st.tmin&&!st.fit&&st.sess.some(se=>!se.locked&&se.min>st.tmin*1.1)?`<div class="rp-av warn"><i>${ic('warn',15)}</i><div class="tx">Alguna sesión pasa de tus ${st.tmin} min.</div></div>`:''}<button class="act" style="margin-top:2px" data-act="decidir">Revisar y cambiar ${ic('fwd',14)}</button></div>`:''}
   <div style="margin:18px 0 6px" class="rp-seg" role="tablist"><button data-rvtab="sesion" class="${st.tab==='sesion'?'on':''}" role="tab">Por sesión</button><button data-rvtab="musculo" class="${st.tab==='musculo'?'on':''}" role="tab">Por músculo</button></div>
   ${body}
  </div>
  <div class="rp-cta pl-glass"><button class="rp-gh" data-act="volver">Volver</button><button class="pill pri" data-act="aplicar">Aplicar reparto</button></div>${st.sheet==='dec'?rpDecSheet(st):''}${st.sheet==='opts'?rpOptsSheet(st):''}`;
}
function rpReviewSessions(st){
  const olds=st.before;
  const cnt=olds.map(o=>{const m={};st.sess.forEach(se=>se.exs.forEach(e=>{if(e.from===o.n)m[se.day.title]=(m[se.day.title]||0)+1}));return {o,m}});
  const col=t=>RPC[(st.sess.find(se=>se.day.title===t)||{day:{role:{k:'mix'}}}).day.role.k==='rest'?'mix':(st.sess.find(se=>se.day.title===t)||{day:{role:{k:'mix'}}}).day.role.k];
  const map=`<div class="mod" style="padding:14px 16px 16px"><div class="mod-h"><span class="ey">De dónde sale cada sesión</span></div><div class="rp-map">${cnt.map(({o,m})=>{const tot=Object.values(m).reduce((a,b)=>a+b,0);const same=Object.keys(m).length===1&&Object.keys(m)[0]===o.n;
    return `<div><div class="rp-mp"><b>${o.n}<small>${tot} ${tot===1?'ejercicio':'ejercicios'}${st.locked&&st.locked.includes(o.id)?' · hecha':''}</small></b><div class="rp-mpb">${Object.entries(m).map(([t,n])=>`<i style="flex:${n};background:${col(t)}" title="${t}">${n}</i>`).join('')}</div></div><div class="rp-mpl" style="margin-left:102px">${same?'Se queda como estaba':'→ '+Object.entries(m).map(([t,n])=>`${t} ${n}`).join(' · ')}</div></div>`}).join('')}</div></div>`;
  const mods=st.sess.map(se=>{
    const c=RPC[se.day.role.k==='rest'?'mix':se.day.role.k];
    const from={};se.exs.forEach(e=>{if(e.from!==se.day.title)from[e.from]=(from[e.from]||0)+1});
    const stay=se.exs.filter(e=>e.from===se.day.title).length;
    const fr=se.locked?'Hecha · se queda como está':Object.keys(from).length?`${stay?stay+' siguen aquí · ':''}vienen ${rpJoin(Object.entries(from).map(([t,n])=>`${n} de ${t}`))}`:'Sigue como estaba';
    return `<div class="mod" style="padding:14px 16px 8px"><div class="rp-rv-h" style="--c:${c}"><i></i><strong>${se.day.title}</strong><span>${RPS[se.day.wd-1][0]+RPS[se.day.wd-1].slice(1).toLowerCase()} ${st.wk.dates[se.day.wd-1]} · ${se.n} ej · ${se.min} min</span></div><div class="rp-from">${fr}</div>
     ${(()=>{let html='',i=0;const row=e=>{const mv=!se.locked&&e.from!==se.day.title;return `<div class="rp-rvx ${mv?'mv':''} ${se.locked?'lk':''}"><b>${e.n}</b><span>${rpEx2(e)}</span>${mv?`<em>de ${e.from}</em>`:se.locked?`<em>Hecha</em>`:''}</div>`};
       while(i<se.exs.length){const e=se.exs[i];if(e.ss){const g=[];while(i<se.exs.length&&se.exs[i].ss===e.ss){g.push(se.exs[i]);i++}html+=`<div class="rp-ssg"><small>Superserie · van juntos</small>${g.map(row).join('')}</div>`}else{html+=row(e);i++}}return html})()}</div>`;
  }).join('');
  return `<div class="rp-anim" style="margin-top:14px">${map}</div><div class="rp-sec" style="margin-bottom:4px">Cada sesión</div><p class="rp-note" style="margin:0 4px 12px">Las series, repeticiones, RIR y descansos de cada ejercicio no cambian.</p>${mods}`;
}
function rpReviewMuscle(st){
  const after=rpVolume(st.sess.map(x=>x.exs)),fa=rpFreq(st.sess.map(x=>x.exs)),vb=st.res.volBefore,fb=st.res.freqBefore;
  const f=n=>(+n).toFixed(n%1?1:0).replace('.',',');
  return `<div class="mod" style="padding:14px 16px 10px;margin-top:14px"><div class="rp-tab"><span class="h">Músculo</span><span class="h">Antes</span><span class="h">Después</span><span class="h">Veces/sem.</span>
   ${RP_GROUPS.map(([g])=>{const d=after[g]-vb[g];return `<span class="c l">${g}</span><span class="c">${f(vb[g])}</span><span class="c ${d<-.01?'dn':''}">${f(after[g])}</span><span class="c ${fa[g]>fb[g]?'ok':fa[g]<fb[g]?'dn':''}">${fb[g]===fa[g]?fa[g]+'×':fb[g]+'→'+fa[g]+'×'}</span>`}).join('')}</div>
   <div class="rp-note">Series semanales por músculo (principal cuenta 1 y secundario 0,5, como en el volumen de la app). ${st.res.trims.length?'Las diferencias vienen de las series recortadas para tu tiempo.':'Son las mismas porque los ejercicios se mueven enteros.'}</div></div>`;
}
function rpReviewExtra(a,st,re,t){
  if(!a&&t){
    const tb=t.closest('[data-rvtab]');if(tb){st.tab=tb.dataset.rvtab;re();return true}
    const dp=t.closest('[data-decpl]');if(dp){const [id,to]=dp.dataset.decpl.split('|');rpPlaceEx(st,id,to);re();return true}
    const ft=t.closest('[data-fit]');if(ft){st.fit=ft.dataset.fit==='1';rpCompute(st,st.split,st.days);re();return true}
  }
  if(a==='volver'){rpGo(st.locked?'rp-en-curso':'rp-semana');return true}
  if(a==='aplicar'){RPSH.split=st.split;rpGo('rp-aplicado');return true}
  return false;
}
/* ===== Aplicado ===== */
function rpApplied(st){
  const k=rpStats(st),und=st.undone;
  const days=st.sess.map(se=>`<div class="day"><div class="dcol"><small>${RPS[se.day.wd-1]}</small><b>${st.wk.dates[se.day.wd-1]}</b></div><div class="dsess"><div class="sess"><div class="tx"><strong>${und?(RP_BEFORE.find(b=>b.wd===se.day.wd)||{n:se.day.title}).n:se.day.title}</strong><div class="ln">${und?(()=>{const b=RP_BEFORE.find(x=>x.wd===se.day.wd);const e=b.ex.map(i=>RP_EX[i]);return e.filter(x=>!x.cardio).length+' ejercicios · '+rpMin(e)+' min'})():se.n+' ejercicios · '+se.min+' min'}</div></div></div></div></div>`).join('');
  return `<div class="scroll rp-page" style="filter:brightness(.55)"><div class="rp-eye">Motor y fuerza · semana 5</div><h1 class="rp-h1">Semana 5</h1><div class="agenda">${days}</div></div>
  <div class="pl-scrim" data-act="listo"></div><div class="pl-sheet rp-sheet" role="dialog" aria-label="${und?'Reparto deshecho':'Reparto aplicado'}">
   ${und?`<div class="rp-ok" style="background:#ffffff10;color:var(--ink)">${ic('undo',26)}</div><div class="rp-sh-t">Reparto deshecho</div><p class="rp-sh-s">Tu semana 5 volvió a como estaba: Personalizado, 4 días (lunes, martes, jueves y viernes), con las mismas series.</p>`
   :`<div class="rp-ok">${ic('check',28)}</div><div class="rp-sh-t">Reparto aplicado</div><p class="rp-sh-s">${rpS(st.split).n} está en tu semana 5.</p>
   <div class="rp-mod"><div class="rp-stats"><div><span>Ejercicios repartidos</span><b>${k.E} en ${st.sess.length} sesiones</b></div><div><span>Series</span><b>${k.S} · ${k.S===k.Sb?'las mismas':'antes '+k.Sb}</b></div><div><span>Cambian de sesión</span><b>${k.moved}</b></div><div><span>Días de entreno</span><b>${rpJoin(st.sess.map(s=>RPF[s.day.wd-1].slice(0,3)))}</b></div></div></div>
   <div class="rp-note" style="margin-top:-4px">Puedes deshacerlo desde el ⋯ de la semana hasta que edites sus sesiones.</div>`}
   <div class="rp-stk"><div style="display:flex;gap:8px"><button class="pill sec" style="flex:1;margin:0" data-act="${und?'rehacer':'deshacer'}">${ic(und?'repeat':'undo',17)}${und?'Rehacer':'Deshacer'}</button><button class="pill pri" style="flex:1;margin:0" data-act="listo">Listo</button></div></div></div>`;
}
function rpWeekBehind(st){return ''}
/* ===== Rechazo ===== */
function rpRefusal(st){
  const s=rpS(st.split);
  return `<div class="topbar"><button class="fb glass" data-act="catalogo" aria-label="Atrás">${ic('back')}</button></div>
  <div class="scroll rp-page tb"><div class="rp-eye">Semana 6 · 10–16 nov · con competición</div><h1 class="rp-h1">${s.n}</h1><p class="rp-lead">${s.d}</p>
   <div class="rp-ban">${ic('warn',22)}<div><strong>La semana incluye una competición.</strong><p>No se pueden repartir los ejercicios de una semana con competición: mover sus sesiones rompería la preparación del día de competencia. El resto de tus semanas sí.</p><button data-act="opts">Aplicar con sesiones vacías</button></div></div>
   <div class="mod" style="padding:14px 14px 10px;opacity:.5"><div class="mod-h" style="margin-bottom:8px"><span class="ey">Tu semana</span></div>${[['LUN',10,'Torso A'],['MAR',11,'Pierna A'],['JUE',13,'Torso B'],['DOM',16,'Open regional']].map(([d,n,t])=>`<div class="rp-day"><div class="dc"><small>${d}</small><b>${n}</b></div><div class="rp-sess" style="--c:${t==='Open regional'?'var(--ene)':'var(--mus)'}"><div class="tx"><strong>${t}</strong><div class="f">${t==='Open regional'?'Competición · 16 nov':'Descarga · mitad de series'}</div></div></div></div>`).join('')}</div>
   <div class="rp-sec">Otros repartos<span>también se bloquean</span></div><p class="rp-lead" style="margin-top:-2px">Cambiar de reparto en esta semana queda desactivado con cualquier reparto, hasta que pase la competición.</p>
  </div>${rpCta(st,'Revisar cambios',{off:true,act:'x'})}${st.sheet==='opts'?rpOptsSheet(st):''}`;
}
/* ===== Plan de autor ===== */
function rpAuthor(st){
  return rpPreview(st)+`<div class="pl-scrim" style="z-index:62"></div><div class="rp-dlg" style="z-index:63;top:190px"><h3>Este programa trae su reparto de autor</h3><p>Si lo adaptas, cambia su estructura original. Reubicamos los ejercicios y mantenemos tu volumen semanal. Lo ves todo antes de aplicar y puedes deshacerlo.</p><div class="acts"><button data-act="cerrar">Mantener mi reparto</button><button class="pr" data-act="cerrar">Adaptar</button></div></div>`;
}
/* ===== Calculando ===== */
function rpCalc(st){
  const s=rpS(st.split);
  return `<div class="topbar"><button class="fb glass" aria-label="Atrás">${ic('back')}</button></div>
  <div class="scroll rp-page tb"><div class="rp-eye">Semana 5 · 3–9 nov · hoy: Personalizado</div><h1 class="rp-h1">${s.n}</h1><p class="rp-lead">${s.d}</p>
   <div class="mod" style="padding:14px"><div class="mod-h" style="margin-bottom:12px"><span class="ey">Tu semana</span><span class="cap" style="display:inline-flex;gap:8px;align-items:center"><i class="rp-spin"></i>Adaptando tu semana…</span></div>
    ${[0,1,2,3,4,5,6].map(i=>`<div class="rp-day ${[2,5,6].includes(i)?'rest':''}"><div class="dc"><small>${RPS[i]}</small><b>${RPDATE[i]}</b></div>${[2,5,6].includes(i)?'<div class="rp-rest">Descanso</div>':'<div class="rp-sk" style="height:68px"></div>'}</div>`).join('')}</div>
  </div>${rpCta(st,'Revisar cambios',{off:true,act:'x'})}`;
}

/* ---- rp-ui4.js ---- */
/* ===== Escenas ===== */
const rpPrevExtra=(a,st,re,t)=>{
  if(a==='aplicar'){RPSH.split=st.split;rpGo('rp-aplicado');return true}
  return rpOptsExtra(a,st,re,t);
};
const RP_CURSO=[
 {id:'s1',n:'Pierna A',wd:1,ex:['sq','prensa','rdl','curlf','gem','rueda']},
 {id:'s2',n:'Empuje',wd:2,ex:['bp','inc','ohp','lat','pushd','aper']},
 {id:'s3',n:'Tirón',wd:3,ex:['dom','remo','jalon','face','martillo','bici']},
 {id:'s4',n:'Pierna B',wd:5,ex:['pm','bulg','hip','ext','gems']},
];
/* --- Hoy: la página y la hoja tal como son --- */
const RP_OLDC={legs:'var(--mus)',push:'var(--ene)',pull:'var(--col)',torso:'#c9a8ff',full:'#f2eee6',mix:'#f2eee6'};
const RP_OLDD={push:'var(--mus)',pull:'var(--col)',legs:'var(--ene)',torso:'#c9a8ff',full:'#f2eee6',mix:'#f2eee6'};
function rpHoyCard(s,on){
  const roles=s.p.map(x=>rpRole(x)).filter(r=>r.k!=='rest');
  return `<div class="pcard" style="min-height:78px;padding:12px;border-radius:24px">${radio(on)}<div class="tx"><strong>${s.orig}</strong><span>${roles.length} días</span><div class="focus" style="margin-top:8px;gap:5px">${roles.map(r=>`<i style="width:22px;height:6px;border-radius:3px;background:${RP_OLDC[r.k]}"></i>`).join('')}</div></div></div>`;
}
scene({tab:'reparto',id:'rp-hoy',title:'Hoy · la página de repartos',html:()=>`<div class="scroll" style="padding-top:52px"><div class="toprow"><button class="ib fill" aria-label="Atrás">${ic('back')}</button><button class="ib fill" aria-label="Buscar">${ic('search')}</button></div>
  <h1 class="ptitle" style="font-size:calc(26px*var(--fs))">Reparto</h1><p class="body" style="margin:6px 0 12px">Elige cómo se reparten los grupos en la semana.</p>
  <div style="display:flex;gap:6px;margin-bottom:12px;flex-wrap:wrap">${['Todos','Recomendado KPKN','Alta frecuencia','Baja frecuencia','Balanceado','Alto volumen','Alta tolerancia','Powerlifting'].map((x,i)=>`<span class="chip ${i===0?'on':''}">${x}</span>`).join('')}</div>
  ${['ul_x4','ppl_ul','fullbody_x3','ppl_x6'].map((id,i)=>rpHoyCard(rpS(id),false)).join('')}
  <div style="display:flex;gap:8px;margin-top:6px"><button class="pill sec sm" style="flex:1">Comparar</button><button class="pill sec sm" style="flex:1">Personalizado</button></div>
  <div style="text-align:center"><button class="act faint" style="width:100%;justify-content:center">Seleccionar varios</button></div></div>
  <div class="rp-ann r" style="left:190px;top:340px;max-width:150px">Sin descripción</div>
  <div class="rp-ann r" style="left:222px;top:430px;max-width:130px">Sin ver tus sesiones</div>`,
 notes:{tags:[['keep','Lo que existe hoy']],p:['La página «Reparto» de hoy, tal como la ve quien abre ⋯ de la semana → «Cambiar reparto». Cada tarjeta enseña el nombre, los días y una barra por día de entreno.',
  '<b>Lo que falla:</b> no hay descripción (solo se lee tocando la tarjeta, ya dentro de la hoja de aplicar), no se ve qué músculo cae cada día, ni para quién es, ni qué pasa con las series que ya tienes. No hay forma de saber cuáles encajan con tus días, y nada resalta cuál es el actual (el radio solo se marca si el reparto está en el catálogo, y el tuyo es «Personalizado»).'],
  list:['Los colores de las barras no son los de la hoja: aquí pierna es coral y empuje amarillo; en el diagrama de la hoja, pierna es amarilla y empuje coral.','Las etiquetas ocupan tres líneas antes del primer reparto.','«Comparar», «Personalizado» y «Seleccionar varios» quedan al final de una lista de 35.'],listTitle:'Qué corregimos en las escenas siguientes'}});
scene({tab:'reparto',id:'rp-hoy-hoja',title:'Hoy · la hoja «Aplicar reparto»',html:()=>`<div class="scroll" style="padding-top:52px;filter:brightness(.5)"><h1 class="ptitle" style="font-size:calc(26px*var(--fs))">Reparto</h1></div><div class="pl-scrim"></div>
  <div class="pl-sheet rp-sheet" style="max-height:94%"><div class="rp-sh-t">Aplicar reparto</div><p class="rp-sh-s" style="margin-top:-6px">Elige el alcance y cómo se crean las sesiones. Ninguna opción se aplica hasta que confirmes.</p>
   <div class="rp-mod"><strong style="font:600 calc(15px*var(--fs)) Inter">Upper / Lower x4</strong><div class="cap" style="margin:4px 0 14px">4 días a la semana</div>
    <div class="rp-strip" style="gap:0">${[0,1,2,3,4,5,6].map(i=>{const l=rpS('ul_x4').p[i],r=rpRole(l);return `<div><small style="font-size:calc(11px*var(--fs))">${RPL[i]}</small>${r.k==='rest'?'<i class="rp-ring" style="width:36px;height:36px"></i>':`<i class="rp-dot" style="--c:${RP_OLDD[r.k]};width:36px;height:36px;font-size:calc(12px*var(--fs))">${r.ini}</i>`}</div>`}).join('')}</div>
    <div class="cap" style="margin-top:10px">T Torso · Pi Pierna</div><p class="body" style="margin:12px 0 0">El estándar de oro. Equilibrio perfecto.</p>
    <div style="font:600 calc(14px*var(--fs)) Inter;margin-top:12px">Beneficios</div><div class="cap">• Frecuencia 2x/semana óptima</div><div class="cap">• 48-72h recuperación</div>
    <div style="font:600 calc(14px*var(--fs)) Inter;margin-top:12px">Precauciones</div><div class="cap">• Requiere 4 días mínimos</div></div>
   <div class="rp-mod"><span class="ey">Contenido de las sesiones</span>${[['Conservar y reubicar','Mantiene los ejercicios existentes e intenta alinearlos con los días del nuevo reparto.'],['Sesiones vacías','Crea la estructura de días del reparto sin ejercicios.'],['Con ejercicios de plantilla','Carga ejercicios desde la plantilla más compatible para cada día.']].map(([t,d])=>`<div class="rp-rad"><span class="rp-rd"></span><div class="tx"><strong>${t}</strong><span class="d">${d}</span></div></div>`).join('')}</div>
   <button class="pill pri off" style="width:100%;margin:0;background:#f2eee62e;color:#f2eee680">Elige el contenido</button></div>
  <div class="rp-ann r" style="left:190px;top:384px;max-width:150px">La descripción, solo aquí</div>
  <div class="rp-ann r" style="left:150px;top:722px;max-width:190px">Aplica sin ver el resultado</div>`,
 notes:{tags:[['keep','Lo que existe hoy']],p:['La hoja que abre cualquier tarjeta: diagrama, descripción y ventajas, inicio de semana, alcance, contenido de las sesiones y el botón. Es la única que explica el reparto.',
  '<b>«Conservar y reubicar» es poco de fiar:</b> mueve cada sesión entera al día que más se le parece. Con 3 sesiones y 4 días, 4 con 2 días, o sesiones que mezclan empuje y tirón, las sesiones se amontonan en un día o quedan días vacíos; ningún ejercicio se reparte. Y no hay vista previa: el botón aplica y ya.'],
  list:['El modo no viene elegido: hay que marcar una opción antes de poder aplicar.','La nota del motor dice solo «Se conservarán N sesiones»: no cuenta qué cambia.','No hay «Deshacer»: el aviso de reemplazo dice que no se puede deshacer automáticamente.'],listTitle:'Defectos'}});
/* --- Catálogo --- */
rpCatDef('rp-catalogo','Elegir reparto · con descripciones',{},{tags:[['new','Descripción, frecuencia y nivel en cada tarjeta'],['new','Filtro por días'],['new','Tu semana arriba'],['keep','Etiquetas, búsqueda, comparar, varios, propio'],['move','Colores unificados']],
 p:['Cada tarjeta responde a lo que querías saber antes de tocar: <b>qué es</b> (una frase), <b>cuántos días</b> pide, <b>cuántas veces por semana entrena cada músculo</b> y <b>para quién es</b>. La tira de siete casillas es la del diagrama de la hoja de hoy, ahora con la letra del día encima.',
    'Arriba, «Ahora» recuerda cómo está tu semana. Los repartos se agrupan en <b>los que encajan con tus 4 días</b> y los que piden otro número: estos avisan de ello en la propia tarjeta. Tocar una tarjeta abre tu semana con ese reparto (siguiente escena), no una hoja.'],
 list:['Descripciones: propuesta nueva para los 35 (hoy los textos son frases sueltas como «Simple y brutal.»). Salen solo del patrón, las ventajas y las precauciones de cada reparto. Hay que aprobarlas.','Veces por semana: se calcula del foco de cada día (el mismo mapa de músculos del motor). Los de powerlifting no la llevan: sus días se definen por levantamiento.','Nivel: es el campo «dificultad» que ya existe y hoy no se enseña.','Nombres: los de lenguaje llano que ya usa el asistente («Torso y pierna, 4 días»), no «Upper / Lower x4».','Colores: empuje amarillo, tirón azul, pierna coral, torso lila, cuerpo completo crema, en la tira, las tarjetas y la agenda. Los días que hoy salían todos crema («Pecho», «Hombro/Brazo») toman el color de su grupo.','Un reparto «Actual» no se marca con radio: tu reparto es Personalizado y se ve en «Ahora».'],listTitle:'Qué es nuevo y por qué'});
rpCatDef('rp-buscar','Buscar y sin resultados',{search:true,q:'glúteo',days:'all'},{tags:[['keep','Busca en nombre, descripción, ventajas y precauciones'],['keep','Estado «Sin resultados»']],
 p:['La lupa abre el campo; con texto escrito se queda abierto. Busca en el nombre, la descripción, las ventajas y las precauciones, como hoy. Prueba a cambiar la palabra por «xyz» en el prototipo.','Con filtros que no dejan ningún reparto, el texto de vacío dice qué hacer. La lista se actualiza al teclear.']});
rpCatDef('rp-seleccion','Comparar: elegir dos',{mode:'compare',picked:['ul_x4','ppl_ul']},{tags:[['keep','Modo comparar y selección múltiple']],
 p:['En modo comparar la tarjeta se marca con un filete de tinta y un check; el tercero sustituye al primero. La barra flotante dice «Elige dos repartos» y se activa con dos («Comparar 2»). «Seleccionar varios» funciona igual, con «Aplicar N repartos».','Mismo comportamiento que hoy. Solo cambia que la tarjeta ahora trae descripción y tira de días.']});
rpCatDef('rp-comparar','Comparar: hoja',{mode:'compare',picked:['ul_x4','ppl_ul'],sheet:'cmp'},{tags:[['keep','Los dos repartos y «Qué cambia»'],['new','Veces por semana por músculo']],
 p:['Igual que hoy: los dos repartos con su tira, y «Qué cambia» con el número de días de la semana que cambian y de qué a qué. Se añade una tabla con <b>cuántas veces por semana</b> entrena cada músculo cada reparto, marcando en verde el que más.','Si uno de los dos es de powerlifting, la tabla no se muestra (sus días no reparten músculos).']});
rpCatDef('rp-varios','Aplicar varios repartos',{mode:'multi',picked:['ul_x4','ppl_ul'],sheet:'multi'},{tags:[['keep','Misma hoja'],['new','Contenido sin rehacer series']],
 p:['La hoja de siempre: repartos en orden, inicio de semana, destino (Varias, Bloque, Programa) y contenido. Los repartos se reparten por las semanas destino en el mismo orden y en ciclo.','Cambia solo el texto de «Conservar y reubicar»: cada semana reparte sus ejercicios según el reparto que le toca. Con varias semanas no hay vista previa semana a semana: «Revisar repartos» enseña la revisión de la primera y un resumen del resto.']});
rpCatDef('rp-propio-vacio','Reparto propio · crear',{tag:'custom'},{tags:[['keep','Módulo «Reparto propio»']],
 p:['La pastilla «Personalizado» sustituye la lista por el módulo del reparto propio: sin reparto guardado ofrece crearlo; con uno, su nombre, su tira y «Editar» y «Aplicar».']});
rpCatDef('rp-propio','Reparto propio · editor',{sheet:'own'},{tags:[['keep','Nombre, descripción, inicio y siete días'],['new','Vista de los días y de las veces por semana']],
 p:['El editor de siempre: cada día es un chip que despliega Descanso, Torso, Pierna, Empuje, Tirón y Cuerpo completo (justo los que la tira colorea). «Guardar» solo se activa con nombre y al menos un día de entreno.','Nuevo y barato: debajo de los días, la tira se actualiza al elegir y dice cuántos días entrena y a qué frecuencia queda cada músculo. Así ves que «Pierna / Empuje / Tirón / Pierna» deja los brazos a 1×.']});
/* --- Vista previa --- */
const rpPrevView=st=>rpPreview(st);
rpScene({id:'rp-semana',title:'Tu semana con el reparto',make:()=>rpMk({split:RPSH.split||'ul_x4'}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['new','Vista previa interactiva'],['new','Antes / Después'],['new','Arrastrar para mover sesiones'],['new','Volumen y frecuencia antes/después']],
 p:['Es la pantalla nueva entre «elegir» y «aplicar». Arriba, qué es el reparto. Debajo, <b>tu semana de siete días</b> con las sesiones tal como quedarían, calculadas con tus ejercicios reales. «Antes / Después» alterna con cómo está hoy.',
    '<b>Pruébalo:</b> toca otro reparto en «Repartos para tu semana» y la semana se rehace; toca una sesión para ver sus ejercicios (los que cambian de sesión llevan «de Empuje») y elegir día en «Mover a»; o arrastra su asa a otro día. A un día libre se muda; a uno ocupado, se intercambian (como hoy). Los avisos y la frecuencia se recalculan al soltar.'],
 list:['El volumen no cambia: cada ejercicio se mueve entero, con sus series, repeticiones, RIR y descansos. Lo que cambia es la frecuencia: aquí cada músculo pasa a entrenarse 2× en vez de 1×.','La barra de abajo abre «Cómo aplicar» (alcance y contenido, la hoja de hoy) y «Revisar cambios» lleva a la revisión antes de tocar nada.','Las superseries viajan juntas; el cardio se coloca en el día con menos carga y se puede cambiar (aviso «Antes de aplicar»).','No se parte de cero: el asistente de bienvenida ya tiene este tablero («Así queda tu semana»: siete filas, asa para arrastrar, «Repartos para tu semana», «Restablecer») sobre el mismo motor de reparto. Aquí se reutiliza, con lo que le faltaba al programa: antes/después, avisos, volumen y revisión.','Es una semana de ejemplo del programa «Motor y fuerza» (datos sintéticos; el motor de la vista es una simulación del real).'],listTitle:'Qué hace'}});
rpScene({id:'rp-mas-dias',title:'Necesita más días',make:()=>rpMk({split:'ppl_ul'}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['new','Aviso de días'],['ask','El reparto puede cambiar tus días']],
 p:['«Empuje, tirón, pierna y torso» entrena 5 días y tu semana tiene 4. En lugar de bloquear, el reparto <b>propone los días del propio reparto</b> (lunes, martes, jueves, viernes y sábado), explica qué añade y qué quita, y deja arrastrar cualquier sesión.',
    'Hoy la app ya cambia los días de entreno al aplicar un reparto (los fija su patrón). La diferencia es que ahora se ve antes y se puede ajustar.'],
 list:['Hay un día nuevo sin sesión de origen: el motor la llena con los ejercicios que mejor encajan con su foco; ninguna sesión queda con menos de 3 ejercicios si hay de dónde sacar.','Si faltan ejercicios para todos los días, el aviso lo dice («algunos días quedan cortos»).']}});
rpScene({id:'rp-menos-dias',title:'Sobra un día · y no caben en el tiempo',make:()=>rpMk({split:'fullbody_x3',tmin:75}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['new','Aviso de días'],['new','Aviso de tiempo por sesión'],['ask','Ajustar al tiempo por sesión']],
 p:['«Cuerpo completo, 3 días» usa 3 de tus 4 días: los mismos ejercicios caben en una sesión menos y las sesiones pasan de unos 60 a entre 75 y 86 min. El aviso lo dice y ofrece <b>«Ver cómo ajustarlo»</b>: dejarlo así o quitar series de accesorios hasta que quepan en tus 75 min (los ejercicios principales no se tocan).',
    'También cambia la frecuencia: cada músculo sube a 3×, y si alguno bajase respecto a hoy saldría el aviso «Pierdes frecuencia en…» solo.'],
 list:['Quitar series es lo único que cambia el volumen; por eso es una decisión, no un efecto escondido.','Tu tiempo por sesión viene del perfil (75 min en este ejemplo). Si ni quitando accesorios cabe, el aviso lo dice y no se tocan los principales.'],listTitle:'Detalle'}});
rpScene({id:'rp-en-curso',title:'Semana en curso · lo hecho se queda',make:()=>rpMk({split:'ul_x4',before:RP_CURSO,cur:[1,2,3,5],locked:['s1','s2'],wk:{n:4,range:'27 oct–2 nov',dates:[27,28,29,30,31,1,2]},hoy:'Personalizado',today:3}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['new','Sesiones hechas bloqueadas'],['ask','Regla de qué día cubre lo hecho']],
 p:['La semana 4 ya tiene lunes (Pierna A) y martes (Empuje) hechos. <b>Lo hecho no se mueve ni se reparte:</b> conserva su día, su nombre y su historial, y cuenta en el volumen de la semana. El reparto nuevo solo reorganiza lo que falta: tirón y pierna B, sobre miércoles y viernes.',
    'Cada sesión hecha ocupa el día del reparto que más se le parece (Pierna A → «Pierna A»; Empuje → «Torso A»), y los días restantes del reparto («Torso B», «Pierna B») se colocan en tus días libres, en orden.'],
 list:['Las sesiones hechas muestran un check y no tienen asa ni «Mover a».','Los días pasados no se ofrecen para sesiones nuevas.','Hoy el motor no sabe qué sesiones están hechas y las reescribiría; esta regla exige pasarle los ids de las sesiones completadas (ver maps/reparto-motor.md, R-LOCK).','Semanas enteras ya hechas no se pueden elegir en «Varias» (ver «Cómo aplicar»).'],listTitle:'Detalle'}});
rpScene({id:'rp-vacia',title:'Semana sin sesiones',make:()=>rpMk({split:'ul_x4',empty:true,cur:[],before:[],mode:'prebuilt',wk:{n:7,range:'17–23 nov',dates:[17,18,19,20,21,22,23]},hoy:'sin sesiones'}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['keep','Sesiones vacías o de plantilla'],['new','Plantilla propuesta por día'],['ask','Por defecto, ejercicios de plantilla']],
 p:['Una semana nueva sin sesiones no tiene nada que migrar: «Conservar y reubicar» desaparece (como hoy) y quedan «Sesiones vacías» y «Con ejercicios de plantilla». La vista previa enseña los días del reparto y, tocando una sesión, <b>qué plantilla le tocaría</b> y las alternativas, lo mismo que la tarjeta «Plantillas por día» de la hoja de hoy.',
    'Propuesta: en una semana vacía el modo viene en «Con ejercicios de plantilla» (hoy hay que elegirlo a mano). Se cambia desde «Cómo aplicar».'],
 list:['Los nombres de plantilla son los reales de la app («Torso A · Upper/Lower», «Leg Day · Cuádriceps»); los recuentos de ejercicios son de ejemplo.','Sin ejercicios no hay volumen ni frecuencia que comparar: ese módulo no aparece.','Si un día no tiene plantilla compatible, se dice en esa sesión («Sin plantilla compatible») y bloquea «Con plantilla» pero no «Vacías».'],listTitle:'Detalle'}});
/* --- Revisión --- */
const rpRevMake=(o={})=>{const st=rpMk({split:RPSH.split||'ul_x4',tab:'sesion',...o});if(RPSH.sess&&RPSH.split===st.split&&!o.fresh&&!o.locked)st.sess=RPSH.sess.map(se=>({...se,day:{...se.day}}));return st};
rpScene({id:'rp-revision',title:'Revisar cambios · por sesión',make:()=>rpRevMake({split:'ul_x4'}),view:rpReview,extra:rpReviewExtra,
 notes:{tags:[['new','Revisión antes de aplicar'],['new','Ejercicios movidos resaltados'],['new','Mapa de dónde sale cada sesión']],
 p:['La pantalla que faltaba: <b>qué va a pasar con cada ejercicio antes de aplicar nada</b>. Arriba, el veredicto («No pierdes nada: 22 ejercicios y 70 series») y tres cifras. El mapa muestra, por cada sesión de hoy, a qué sesiones nuevas se reparten sus ejercicios.',
    'Debajo, cada sesión nueva con sus ejercicios tal cual: series, repeticiones, RIR y descanso no cambian. Los que cambian de sesión llevan un filete coral y «de Empuje»; las superseries viajan juntas.'],
 list:['«Aplicar reparto» aplica (siguiente escena). «Volver» regresa a la vista previa sin cambiar nada.','«Con propuesta» (arriba) resume las decisiones del motor: el cardio va en el día con menos carga, el core en cualquier día. Se pueden cambiar.','La sesión principal y los nombres («Torso A», «Pierna B») siguen la lógica de hoy.'],listTitle:'Detalle'}});
rpScene({id:'rp-revision-musculo',title:'Revisar cambios · por músculo',make:()=>rpRevMake({split:'ul_x4',tab:'musculo'}),view:rpReview,extra:rpReviewExtra,
 notes:{tags:[['new','Volumen conservado por músculo']],
 p:['La misma revisión, por músculo: series semanales antes y después (iguales, porque los ejercicios se mueven enteros) y <b>cuántas veces por semana</b> se entrena cada uno. Una frecuencia que baja se pinta en rojo; una que sube, en verde.',
    'El motor ya calcula este informe (series por músculo con el criterio del volumen de la app); la pantalla los agrupa como «Pecho», «Espalda», «Hombros»… para que no sean 17 filas.']}});
rpScene({id:'rp-decisiones',title:'Antes de aplicar · decisiones',make:()=>rpRevMake({split:'fullbody_x3',tmin:75,fresh:true,sheet:'dec'}),view:rpReview,extra:rpReviewExtra,
 notes:{tags:[['new','Ejercicios sin día natural'],['new','Ajuste al tiempo por sesión'],['ask','Decisión del usuario']],
 p:['Lo que el motor no puede decidir solo, con <b>una propuesta ya puesta</b> (aplicar nunca se bloquea): el cardio y los remates de core no dependen del músculo, y las sesiones se pasan de tu tiempo. Cada decisión es una fila de chips; al cambiarla, la revisión se recalcula.',
    '«Ajustar a 75 min» quita series a accesorios y aislamientos, de una en una y sin bajar de 2; los principales y las superseries no se tocan. Es lo único que cambia el volumen y queda escrito en la revisión.'],
 list:['El ejercicio que se quedaría sin día porque su músculo no encaja (el motor lo llama «no encaja del todo») sale con esta misma fila.','Si nada necesita decisión, «Con propuesta» no aparece.'],listTitle:'Detalle'}});
/* --- Cómo aplicar --- */
const rpOptMk=(o={})=>rpMk({split:'ul_x4',sheet:'opts',...o});
rpScene({id:'rp-opciones',title:'Cómo aplicar · una semana',make:()=>rpOptMk(),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['keep','Inicio de semana, alcance y contenido'],['new','Conservar y reubicar ya viene elegido'],['move','Sale de la ruta principal']],
 p:['La hoja de siempre, reducida a lo que se decide: <b>inicio de semana</b>, <b>alcance</b> (Semana, Varias, Bloque, Programa) y <b>contenido</b>. El diagrama, la descripción, las ventajas y las precauciones ya están en la tarjeta y en la vista previa, y el botón final está en «Revisar cambios».',
    '«Conservar y reubicar» viene marcado cuando la semana tiene sesiones, porque ya no es un riesgo: la nota dice cuántos ejercicios y series pasan y el resultado se ve antes de aplicar. Hoy hay que elegir un modo a mano.'],
 list:['El texto de «Conservar y reubicar» cambia: hoy promete «alinearlos con los días»; ahora, repartir ejercicios sin rehacer series.','Se mantienen las ventajas y precauciones: pasan a ser el texto de la tarjeta y de la vista previa («Beneficios y precauciones» como segunda línea de la tarjeta si se quiere más detalle: pregunta abajo).'],listTitle:'Detalle'}});
rpScene({id:'rp-opciones-varias',title:'Cómo aplicar · varias semanas',make:()=>rpOptMk({scope:'several',weeksSel:[5,6],calendarized:true}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['keep','Semanas como círculos'],['new','Semanas hechas y en curso bloqueadas'],['new','Qué pasa con descarga y semanas distintas']],
 p:['Los círculos de semana son los de hoy. Las semanas <b>ya hechas</b> (1 a 3) y la <b>en curso</b> (4) salen apagadas: no se pueden elegir. La 6 es de descarga y se enseña en azul: el reparto la conserva con su mitad de series.',
    'Programa con calendario: «El inicio de la semana lo fija el calendario del programa». El texto bajo los círculos explica que las semanas con los mismos ejercicios repiten el mismo reparto y las que traen otros se calculan aparte (como hace el motor).']}});
rpScene({id:'rp-opciones-bloques',title:'Cómo aplicar · programa por bloques',make:()=>rpOptMk({scope:'program',advanced:true,perBlock:true}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['keep','Un reparto / Por bloque'],['new','Aviso de bloques ya hechos y descarga']],
 p:['En un programa avanzado aparece «Programa por bloques»: «Un reparto» o «Por bloque», con un selector por bloque (se abre la lista de repartos, como hoy). Cada bloque dice qué semanas cubre.',
    'El bloque 1 ya está hecho y sale apagado; el 3 es de descarga y su nota recuerda que conserva la mitad de series.']}});
rpScene({id:'rp-sustituir',title:'Cómo aplicar · sustituir sesiones',make:()=>rpOptMk({mode:'clean'}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['keep','Impacto y aceptación'],['out','«No se puede deshacer»']],
 p:['Con «Sesiones vacías» o «Con ejercicios de plantilla» sobre una semana con sesiones, las 4 actuales se <b>reemplazan</b>: aparece el módulo de impacto (teñido de peligro) con la casilla de aceptación y el botón dice «Reemplazar 4 sesiones», desactivado hasta aceptar. Es la lógica de hoy.',
    'Cambia el texto: ya no dice que no se puede deshacer, porque ahora sí se puede (aviso con «Deshacer» al aplicar).']}});
rpScene({id:'rp-confirmar',title:'Cómo aplicar · confirmar reemplazo',make:()=>rpOptMk({mode:'clean',accept:true,confirm:true}),view:rpPrevView,extra:rpPrevExtra,
 notes:{tags:[['keep','Confirmación en cambios grandes']],
 p:['El diálogo de hoy («Confirmar reemplazo»), que solo sale en cambios grandes: más de una semana o más de 4 sesiones. «Cancelar» a la izquierda y la confirmación a la derecha.']}});
/* --- Aplicado --- */
rpScene({id:'rp-aplicado',title:'Reparto aplicado · deshacer',make:()=>rpMk({split:RPSH.split||'ul_x4'}),view:rpApplied,
 extra:(a,st,re)=>{if(a==='deshacer'){st.undone=true;re();return true}if(a==='rehacer'){st.undone=false;re();return true}if(a==='listo'){return true}return false},
 notes:{tags:[['new','Resumen de lo aplicado'],['new','Deshacer'],['ask','Cuánto dura «Deshacer»']],
 p:['Al aplicar, se vuelve a la semana y sube esta hoja corta: cuántos ejercicios y series se repartieron, cuántos cambiaron de sesión y qué días entrena ahora. <b>«Deshacer»</b> devuelve la semana exactamente a como estaba; «Volver a aplicarlo» la rehace. Pruébalo.',
    'Propuesta: «Deshacer» sigue en el ⋯ de la semana hasta que edites alguna de sus sesiones (la copia previa se guarda con el programa). Si prefieres solo mientras la hoja está abierta, es más sencillo pero menos amable.'],
 list:['Si el motor falla al aplicar, el diálogo de error de hoy («No se pudo aplicar el reparto» con el motivo) se queda igual y no toca el programa.'],listTitle:'Errores'}});
rpScene({id:'rp-deshecho',title:'Reparto deshecho',make:()=>rpMk({split:RPSH.split||'ul_x4',undone:true}),view:rpApplied,extra:(a,st,re)=>{if(a==='rehacer'){st.undone=false;re();return true}return false},
 notes:{tags:[['new','Deshacer']],p:['El estado tras «Deshacer»: la semana vuelve a ser «Personalizado», con los días y series de antes.']}});
/* --- Rechazos --- */
rpScene({id:'rp-rechazo',title:'Reparto no compatible con la semana',make:()=>rpMk({split:'ul_x4'}),view:rpRefusal,extra:rpPrevExtra,
 notes:{tags:[['new','El motivo se dice'],['keep','Sesiones vacías sigue disponible']],
 p:['Cuando el motor no puede repartir (aquí, la semana 6 trae la competición del 16 de nov), <b>dice por qué en una frase</b> y la vista previa no se calcula. «Revisar cambios» queda desactivado, pero «Sesiones vacías» y «Con plantilla» siguen funcionando: el aviso lo ofrece.',
    'Mensajes reales del motor, todos con este formato: «La semana incluye una competición.», «La semana tiene sesiones con variantes (B, C o D).», «Tu programa no tiene ejercicios para repartir.», «Hay menos ejercicios que días de entreno: 4 ejercicios para 5 días.», «Tu programa no tiene press de banca y este reparto lo necesita.», «Este reparto no está disponible.».'],
 list:['El rechazo de «No se pudo adaptar el programa a este reparto.» (error inesperado) guarda el detalle técnico aparte, no para la persona.'],listTitle:'Otros motivos'}});
rpScene({id:'rp-autor',title:'Plan de autor · aviso de estructura',make:()=>rpMk({split:'ul_x4'}),view:rpAuthor,extra:rpPrevExtra,
 notes:{tags:[['new','Permiso explícito'],['ask','Planes con receta de autor']],
 p:['Los planes con receta de autor (de catálogo o adaptados) traen su propio reparto. El motor los rechaza salvo permiso explícito; aquí el permiso es este diálogo, antes de la vista previa. «Mantener mi reparto» vuelve; «Adaptar» sigue con la vista previa y la revisión.',
    'Es el mismo aviso que ya usa el asistente de bienvenida («Este programa trae su reparto de autor. Si lo adaptas, cambia su estructura original.»), con sus botones «Adaptar» y «Mantener mi reparto».']}});
rpScene({id:'rp-calculando',title:'Calculando el reparto',make:()=>rpMk({split:'ul_x4'}),view:rpCalc,extra:()=>false,
 notes:{tags:[['new','Estado de carga']],p:['Mientras el motor reparte (cientos de milisegundos en un programa grande, fuera del hilo principal), la semana enseña esqueletos y el botón está apagado. En semanas con más de 60 ejercicios o varias semanas, el cálculo puede tardar más: el esqueleto ya lo cubre.']}});
