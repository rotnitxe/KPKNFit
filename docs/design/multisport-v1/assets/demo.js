(() => {
  const content = document.querySelector('#screen-content');
  if (!content) return;
  const byId = new Map((window.KPKN_SCENARIOS || []).map((scene) => [scene.id, scene]));
  const groups = Object.fromEntries((window.KPKN_SCENARIOS || []).map((scene) => [scene.id, scene.group]));
  const weekdays = ['Lun','Mar','Mié','Jue','Vie','Sáb','Dom'];
  const initial = () => ({
    scene:'home', history:[], sports:{strength:true,running:true}, priorities:['strength','running'], levels:{strength:'Intermedio',running:'Base'},
    days:['Mar','Jue','Sáb'], equipment:['Barra + rack','Mancuernas','Calle'], moveDay:'Mar', conflict:false, alternative:'Goblet squat · mancuerna',
    load:80,reps:5,liftSets:0,skillSets:0,armSets:0,runKm:5,runRpe:4,completed:false,
    components:[{name:'High pull',reps:2},{name:'Power clean',reps:1},{name:'Front squat',reps:1},{name:'Jerk',reps:1}],barWeight:85,
    skillIndex:0,holdSeconds:12,eventAttempts:0,climbAttempts:0,sprintAttempts:0,strikingRounds:6,grapplingRounds:5,roundMinutes:3,grapplingRoundMinutes:5,score:0,side:'Derecha',event:'Yugo',format:'AMRAP',selectedDay:'Mar',period:'30 días',points:[0,0],teamActions:0,mmaClasses:0,minutes:28,
    ftp:220,bikeMinutes:90,trailKm:18,ascent:900,rowDistance:2000,rowSplit:122,poolLength:25,swimMeters:1500,swimPace:120,triSwim:1.5,triBike:40,triRun:10,grade:'V4',sprintMeters:30,jumpCount:5,netPoints:7,teamAction:'Asistencia',teamGoals:1,teamAssists:2,teamShots:1,
    componentChoice:'Power jerk',eventLoad:180,carryMeters:20,pronationLoad:15,isoSec:8,amrapRounds:5,amrapReps:8,emomReps:10,emomMinutes:12,forTimeMinutes:13,goalDistance:10,goalPace:300,comboChoice:'Jab · cross · hook',position:'Guardia',positionTo:'Pase de guardia',mmaPhase:'Base técnica',campWeek:3,sprintType:'Sprint',tennisAces:3,tennisFirstServe:68,padelNetWinners:7,restSeconds:60,climbRest:180,sprintRest:180,jumpRest:120,jumpsPerSet:3,minTime:45,maxTime:70,
    statusMode:'Antiguo',importChoice:'',events:['SkiErg · 1.000 m','Sled push · 50 m','Sled pull · 50 m','Burpee broad jump · 80 m','Row · 1.000 m','Farmer carry · 200 m','Sandbag lunges · 100 m','Wall balls · 100 reps'],combos:['Jab · cross · hook'],transitions:['Guardia → pase'],focus:'Constancia',progressSource:'Manual + sesiones de ejemplo',toast:'',calorieCount:1640,bodyWeight:72.4
  });
  let state = initial();
  const safe = (value) => String(value).replace(/[&<>"']/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const sceneMeta = {
    home:['Home','Resumen del día'], profile:['Perfil deportivo','Disciplinas y prioridades'], wizard:['Planificación','Wizard multideporte'], week:['Training','Semana y conflictos'], alternatives:['Training','Alternativas por material'],
    hybrid:['Híbrido','Fuerza + running'], 'hybrid-strength':['Sesión 1 de 2','Fuerza'], 'hybrid-transition':['Cambio de modalidad','Fuerza → running'], 'hybrid-cardio':['Muestra de cardio','Carrera'], 'hybrid-summary':['Sesión completada','Resumen del híbrido'],
    training:['Training','Disciplinas y sesiones'], nutrition:['Nutrition','Resumen de hoy'], body:['Body','Tendencia corporal']
  };

  function goto(scene, push = true) {
    const id = byId.has(scene) || sceneMeta[scene] || ['training','nutrition','body'].includes(scene) ? scene : 'home';
    if (push && state.scene !== id) state.history.push(state.scene);
    state.scene = id;
    if (location.hash.slice(1) !== id) location.hash = id;
    render();
  }
  function module(title, body, opts = {}) {
    const cls = opts.alert ? 'module alert' : opts.tight ? 'module tight' : 'module';
    const action = opts.action ? `<button class="module-action" type="button" data-action="${safe(opts.action)}">${safe(opts.actionLabel || 'Editar')}</button>` : '';
    return `<section class="${cls}"><div class="module-header"><span class="module-label">${safe(title)}</span>${action}</div>${body}</section>`;
  }
  function primary(label, action, attrs = '') { return `<button type="button" class="action-button" data-action="${safe(action)}" ${attrs}>${label}</button>`; }
  function secondary(label, action, attrs = '') { return `<button type="button" class="action-button secondary" data-action="${safe(action)}" ${attrs}>${label}</button>`; }
  function field(label, name, value, unit, min = 0, max = 9999, step = 1) {
    const id = `f-${safe(name)}`;
    return `<label class="field-label" for="${id}">${safe(label)}${unit ? ` · ${safe(unit)}` : ''}</label><input class="input-field" id="${id}" type="number" inputmode="decimal" data-input="${safe(name)}" value="${safe(value)}" min="${min}" max="${max}" step="${step}">`;
  }
  function badge(text, type = '') { return `<span class="status-badge ${type}">${safe(text)}</span>`; }
  function row(label, value, note = '') { return `<div class="list-row"><span class="row-main">${safe(label)}${note ? `<small>${safe(note)}</small>` : ''}</span><span class="row-value">${safe(value)}</span></div>`; }
  function title(kicker, heading, subtitle, isProjection = false) {
    const projection = isProjection ? badge('Proyección','projection') : badge('Muestra');
    return `<p class="screen-kicker">${safe(kicker)}</p><h1 class="screen-title">${safe(heading)}</h1><p class="screen-subtitle">${safe(subtitle)}</p><div style="margin:-10px 0 14px">${projection}</div>`;
  }
  function chip(label, action, pressed, value = label) {
    return `<button type="button" class="chip" data-action="${safe(action)}" data-value="${safe(value)}" aria-pressed="${Boolean(pressed)}">${safe(label)}</button>`;
  }
  function stepper(label, value, action, unit = '') {
    return `<div class="control-row"><label>${safe(label)}</label><div class="step-control"><button type="button" data-action="step" data-field="${safe(action)}" data-delta="-1" aria-label="Disminuir ${safe(label)}">−</button><output>${safe(value)}${unit ? ` ${safe(unit)}` : ''}</output><button type="button" data-action="step" data-field="${safe(action)}" data-delta="1" aria-label="Aumentar ${safe(label)}">+</button></div></div>`;
  }
  function appHome() {
    const selected = state.days.slice(0, 3).join(' · ');
    const dates=[19,20,21,22,23,24,25];
    const selectedIndex=Math.max(0,weekdays.indexOf(state.selectedDay));
    const selectedDate=dates[selectedIndex];
    const hasSession=state.days.includes(state.selectedDay);
    const nextLabel=hasSession?`Próxima sesión · ${state.selectedDay} ${selectedDate}`:`${state.selectedDay} ${selectedDate} · descanso`;
    const nextActivity=hasSession?'Fuerza + running':'Día de recuperación';
    return `${title('DOMINGO 18 DE OCTUBRE','Buenos días, Kai','Semana híbrida')}
      ${module('Semana · 19–25 oct',`<div class="calendar-grid">${weekdays.map((day,i)=>`<button class="calendar-day" type="button" data-action="pick-home-day" data-value="${day}" aria-pressed="${state.selectedDay===day}"><strong>${day}</strong><span>${dates[i]}</span>${state.days.includes(day)?'<em></em>':''}</button>`).join('')}</div><div class="divider"></div><p class="module-label">DOMINGO 18 · DESCANSO</p><h2 style="font:700 21px/1.2 var(--font-display);margin:7px 0">${safe(nextActivity)}</h2><p class="tiny-note">${safe(nextLabel)}${hasSession?' · sentadilla y carrera suave':''}</p>${primary('Ver semana · '+safe(selected),'go-scene','data-scene="week"')}`)}
      ${module('TUS RINGS',`<h2 class="readiness-title">Listo para empujar</h2><svg class="readiness-orbits" viewBox="0 0 320 190" role="img" aria-label="Tres anillos de recuperación de muestra: músculo, energía y estructura"><defs><linearGradient id="ring-muscle" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#c89943"/><stop offset="1" stop-color="#ffe49a"/></linearGradient><linearGradient id="ring-energy" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#ce7352"/><stop offset="1" stop-color="#ffb28d"/></linearGradient><linearGradient id="ring-structure" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#4d6db8"/><stop offset="1" stop-color="#a9c3ff"/></linearGradient></defs><ellipse cx="160" cy="42" rx="62" ry="20" fill="none" stroke="url(#ring-muscle)" stroke-width="13"/><ellipse cx="160" cy="92" rx="103" ry="31" fill="none" stroke="url(#ring-energy)" stroke-width="13"/><ellipse cx="160" cy="147" rx="143" ry="38" fill="none" stroke="url(#ring-structure)" stroke-width="13"/></svg><div class="readiness-values"><div><strong>78</strong><span>Músculo</span></div><div><strong>64</strong><span>Energía</span></div><div><strong>88</strong><span>Estructura</span></div></div><p class="tiny-note">Indicadores AUGE · valores de muestra</p>`)}
      ${module('Sesión activa',`<div class="list-row"><span>Mar 20 · Fuerza + running<small>4 ejercicios · 5 km planificados</small></span>${badge(state.completed?'Completada':'Planificada',state.completed?'success':'')}</div>${primary('Abrir híbrido','go-scene','data-scene="hybrid"')}`)}
      ${secondary('Configurar disciplinas','go-scene','data-scene="profile"')}`;
  }
  function profileScreen() {
    const sports = [['strength','Fuerza'],['running','Running']];
    const names = {strength:'Fuerza',running:'Running'};
    const order = state.priorities.map((id,i)=>row(`${i+1}. ${names[id]}`,state.levels[id],`Prioridad ${i+1} · nivel editable`)).join('');
    return `${title('PERFIL DEPORTIVO','Tus disciplinas','Elige más de una y asigna nivel y prioridad por separado.')}
      ${module('Disciplinas activas',`<div class="chip-row">${sports.map(([id,name])=>chip(name,'toggle-sport',state.sports[id],id)).join('')}</div><p class="tiny-note">Mantén al menos una disciplina activa. El híbrido de muestra necesita Fuerza y Running.</p>`)}
      ${Object.entries(state.sports).filter(([,active])=>active).map(([id])=>module(names[id],`<label class="field-label" for="level-${id}">Nivel</label><select class="select-field" id="level-${id}" data-select="level-${id}">${['Base','Intermedio','Avanzado'].map(level=>`<option ${state.levels[id]===level?'selected':''}>${level}</option>`).join('')}</select><div class="control-row"><label>Prioridad en tu semana</label><div class="step-control"><button type="button" data-action="priority" data-sport="${id}" data-delta="-1" aria-label="Subir prioridad de ${names[id]}">↑</button><output>${state.priorities.indexOf(id)+1}</output><button type="button" data-action="priority" data-sport="${id}" data-delta="1" aria-label="Bajar prioridad de ${names[id]}">↓</button></div></div>`)).join('')}
      ${primary('Continuar al wizard','go-scene','data-scene="wizard"')}`;
  }
  function wizardScreen() {
    const gear = ['Barra + rack','Mancuernas','Banda','Calle','Cinta'];
    return `${title('PLANIFICACIÓN · 1 DE 3','Diseña tu semana','Disponibilidad, tiempo y material.')}
      ${module('Días disponibles',`<div class="chip-row">${weekdays.map(day=>chip(day,'toggle-day',state.days.includes(day),day)).join('')}</div><p class="tiny-note">${state.days.length} días seleccionados · el plan respeta la prioridad de cada disciplina.</p>`)}
      ${module('Material y lugar',`<div class="chip-row">${gear.map(item=>chip(item,'toggle-gear',state.equipment.includes(item),item)).join('')}</div>`)}
      ${module('Tiempo por sesión',`${stepper('Mínimo',45,'minTime','min')}${stepper('Máximo',70,'maxTime','min')}`)}
      ${module('Objetivo',`<div class="segmented" role="group" aria-label="Objetivo del bloque">${['Constancia','Rendimiento','Fuerza'].map(x=>`<button type="button" data-action="set-focus" data-value="${x}" aria-pressed="${state.focus===x}">${x}</button>`).join('')}</div><p class="tiny-note">Objetivo seleccionado: ${safe(state.focus)}. Ejemplo visual; no se genera una prescripción real.</p>`)}
      ${primary('Crear semana de ejemplo','go-scene','data-scene="week"')}${secondary('Volver al perfil','go-scene','data-scene="profile"')}`;
  }
  function weekScreen() {
    const moveDay = state.moveDay;
    const moveDate = 19 + Math.max(0,weekdays.indexOf(moveDay));
    const conflicting = moveDay === 'Jue';
    return `${title('TRAINING · SEMANA','Semana de Kai','Semana del 19 al 25 de octubre · fuerza y running.')}
      ${module('Plan semanal',`<div class="calendar-grid">${weekdays.map((day,i)=>`<button class="calendar-day" type="button" data-action="move-to-day" data-value="${day}" aria-pressed="${moveDay===day}"><strong>${day}</strong><span>${19+i}</span>${(day==='Jue'||day==='Sáb'||day===moveDay)?'<em></em>':''}</button>`).join('')}</div>${row('Fuerza + running',`${moveDay} ${moveDate}`,'Edita el día para ver conflictos')}${row('Running suave','Jue 22 · 18:00','Sesión existente')}${row('Fuerza técnica','Sáb 24 · 10:00','Material: mancuernas')}${conflicting?`<div class="module alert tight" role="status"><b>Conflicto · Jueves 22</b><p class="tiny-note">Ya hay running a las 18:00. Puedes mover la carrera suave o elegir otro día.</p>${secondary('Mover carrera suave al viernes','resolve-conflict')}</div>`:''}${primary(conflicting?'Resolver conflicto':'Revisar alternativas',conflicting?'resolve-conflict':'go-alternatives')}`)}
      ${module('Disponibilidad',`<p class="tiny-note">Material: ${safe(state.equipment.join(' · ')||'sin seleccionar')}. Días: ${safe(state.days.join(' · ')||'ninguno')}.</p>${secondary('Cambiar días y material','go-scene','data-scene="wizard"')}`)}
      ${secondary('Configurar perfil','go-scene','data-scene="profile"')}`;
  }
  function alternativesScreen() {
    const options = ['Goblet squat · mancuerna','Split squat · peso corporal','Sentadilla a caja · mancuerna'];
    return `${title('TRAINING · EQUIPO','Elige una alternativa','El rack no está disponible hoy. Cambia el ejercicio y revisa qué conserva la sesión.')}
      ${module('Ejercicio original',`<h2 style="font:700 18px var(--font-display);margin:0 0 4px">Sentadilla con barra</h2><p class="tiny-note">4 × 5 · objetivo de esfuerzo 7/10 · equipo no disponible</p>`,{alert:true})}
      ${module('Opciones compatibles',`${options.map((option,i)=>`<button type="button" class="list-row" style="width:100%;text-align:left;background:transparent;border-top:0;border-left:0;border-right:0;color:var(--ink)" data-action="choose-alt" data-value="${safe(option)}" aria-pressed="${state.alternative===option}"><span class="row-main">${safe(option)}<small>${i===0?'Mancuerna disponible · patrón de sentadilla':'Sin rack · variante de pierna'}</small></span><span class="row-value">${state.alternative===option?'✓':'Elegir'}</span></button>`).join('')}<p class="tiny-note">Seleccionada: ${safe(state.alternative)}.</p>`)}
      ${primary('Usar alternativa y registrar fuerza','go-scene','data-scene="hybrid-strength"')}${secondary('Volver a la semana','go-scene','data-scene="week"')}`;
  }
  function hybridStart() {
    return `${title('RECORRIDO INTERACTIVO','Fuerza + running','Configura el perfil, planifica la semana, registra fuerza y consulta la referencia de cardio aprobada.')}
      ${module('Plan de muestra',`<div class="metric-row"><strong class="metric">2</strong><span class="metric-unit">disciplinas</span></div>${row('Fuerza','Intermedio','Prioridad 1')}${row('Running','Base','Prioridad 2')}${row('Sesión mixta','≈ 65 min','Fuerza → carrera suave')}`)}
      ${primary('1 · Elegir disciplinas y prioridad','go-scene','data-scene="profile"')}
      ${module('Ruta',`<ol class="step-list"><li><span>Perfil deportivo</span><span>${state.sports.strength&&state.sports.running?'Listo':'Editar'}</span></li><li><span>Días y material</span><span>${state.days.length} días</span></li><li><span>Resolver conflicto y alternativa</span><span>Jueves · equipo</span></li><li><span>Registrar y cerrar sesión</span><span>${state.completed?'Completada':'Pendiente'}</span></li></ol>`,{tight:true})}
      ${secondary('Reiniciar estado de la maqueta','reset-demo')}`;
  }
  function strengthScreen(isHybrid = false) {
    const e1rm = Number(state.reps)>0 ? (Number(state.load)*(1+Number(state.reps)/30)).toFixed(1) : '—';
    const setsBody = `<div class="input-pair">${field('Carga','load',state.load,'kg',0,300,2.5)}${field('Repeticiones','reps',state.reps,'reps',1,20,1)}</div><div class="divider"></div><div class="metric-row"><strong class="metric" data-output="e1rm">${e1rm}</strong><span class="metric-unit">kg · e1RM Epley</span></div><p class="tiny-note">Estimación · carga × (1 + reps / 30)</p>${stepper('Series registradas',state.liftSets,'liftSets')}${primary('Registrar serie','add-set')}`;
    return `${title(isHybrid?'FUERZA · BLOQUE 1 DE 2':'FUERZA AVANZADA',isHybrid?'Sentadilla · alternativa':'Reglas y e1RM','Ajusta carga y repeticiones; cada toque agrega una serie a la muestra.',!isHybrid)}
      ${module('Serie de trabajo',setsBody)}
      ${module('Reglas de la serie',`${row('e1RM estimado',`${e1rm} kg`,'Epley · 1 + reps / 30')}${row('Volumen de muestra',`${state.liftSets*Number(state.load)*Number(state.reps)} kg`,'series registradas × carga × reps')}${row('Alternativa actual',state.alternative,'Mismo bloque')}`)}
      ${isHybrid?primary('Completar fuerza · pasar a running','go-scene','data-scene="hybrid-transition"'):secondary('Ver sesión híbrida','go-scene','data-scene="hybrid"')}`;
  }
  function transitionScreen() {
    return `${title('SESIÓN MIXTA · CAMBIO','Ahora vamos con running','La parte de fuerza quedó registrada en esta muestra. La referencia aprobada conserva el editor y el cardio en vivo.')}
      ${module('Fuerza → running',`<div class="metric-row"><strong class="metric">${state.liftSets}</strong><span class="metric-unit">series · ${safe(state.alternative)}</span></div><div class="divider"></div>${row('Siguiente','Carrera suave','5 km · RPE como guía si no hay FC')}${badge('Muestra local','projection')}`)}
      ${primary('Abrir referencia Cardio v2','open-cardio')}
      ${module('Continuar recorrido de muestra',`<p class="tiny-note">No se replica la pantalla de cardio. Al volver, puedes registrar una distancia ilustrativa y ver el resumen local.</p>${secondary('Volver al registro de carrera','go-scene','data-scene="hybrid-cardio"')}`)}
      ${secondary('Volver a fuerza','go-scene','data-scene="hybrid-strength"')}`;
  }
  function cardioSampleScreen() {
    return `${title('CARRERA · DATO ILUSTRATIVO','Registrar distancia','Vista de enlace al cardio aprobado. Este campo solo demuestra el cierre del recorrido de maqueta.')}
      ${module('Referencia Cardio v2',`<p class="tiny-note">La sesión en vivo, las zonas, el editor y las transiciones están en las páginas originales copiadas localmente.</p><a class="inline-link" href="references/cardio-v2/index.html">Abrir índice Cardio v2 →</a>`)}
      ${module('Dato de ejemplo',`${field('Distancia','runKm',state.runKm,'km',0,50,.1)}<label class="field-label" for="run-rpe">Esfuerzo percibido · RPE ${state.runRpe}/10</label><div class="range-row"><span>1</span><input id="run-rpe" type="range" min="1" max="10" value="${state.runRpe}" data-input="runRpe" aria-label="Esfuerzo percibido RPE"><span>10</span></div><p class="tiny-note">No hay conexión GPS, Health Connect ni sensor en este prototipo.</p>`)}
      ${primary('Guardar muestra y ver resumen','finish-hybrid')}${secondary('Volver a la transición','go-scene','data-scene="hybrid-transition"')}`;
  }
  function hybridSummary() {
    const e1rm=(Number(state.load)*(1+Number(state.reps)/30)).toFixed(1);
    return `${title('SESIÓN · RESUMEN','Buen trabajo, Kai','Resumen de demostración generado en memoria; al reiniciar, vuelve al estado inicial.')}
      ${module('Fuerza',`${row('Alternativa',state.alternative,`${state.liftSets} series · ${state.load} kg × ${state.reps}`)}${row('e1RM de ejemplo',`${e1rm} kg`,'Epley')}`)}
      ${module('Running',`${row('Distancia',`${state.runKm} km`,'Dato introducido manualmente')}${row('Esfuerzo',`RPE ${state.runRpe}/10`,'Sin sensor conectado')}`)}
      ${module('Híbrido',`<div class="metric-row"><strong class="metric">${Math.round(Number(state.liftSets)*4+Number(state.runKm))}</strong><span class="metric-unit">puntos de actividad de muestra</span></div>`,{tight:true})}
      ${primary('Volver a Home','go-scene','data-scene="home"')}${secondary('Reiniciar recorrido','reset-demo')}`;
  }

  function appTraining() {
    const grouped=[...new Set((window.KPKN_SCENARIOS||[]).filter(s=>!['Núcleo','App'].includes(s.group)).map(s=>s.group))];
    return `${title('TRAINING','Disciplinas y sesiones','Abre una escena específica o continúa el híbrido activo.')}
      ${module('Sesión prioritaria',`<h2 style="font:700 19px var(--font-display);margin:0 0 4px">Fuerza + running</h2><p class="tiny-note">Mar · fuerza primero · carrera después</p>${primary('Continuar híbrido','go-scene','data-scene="hybrid"')}`)}
      ${grouped.map(group=>module(group,(window.KPKN_SCENARIOS||[]).filter(s=>s.group===group).map(s=>`<a class="list-row" style="text-decoration:none" href="#${s.id}"><span>${safe(s.title)}<small>${safe(s.metric)}</small></span><span class="row-value">→</span></a>`).join(''),{tight:true})).join('')}`;
  }

  function selectControl(name,label,options,value) {
    return `<label class="field-label" for="sel-${safe(name)}">${safe(label)}</label><select class="select-field" id="sel-${safe(name)}" data-select="${safe(name)}">${options.map(option=>`<option value="${safe(option)}" ${value===option?'selected':''}>${safe(option)}</option>`).join('')}</select>`;
  }
  function screenProjection(sceneId) {
    const scene=byId.get(sceneId);
    if(!scene) return `${title('ESCENARIO','Pantalla no disponible','Vuelve a la galería para elegir otra escena.')}${secondary('Volver a Home','go-scene','data-scene="home"')}`;
    const heading=title(scene.group.toUpperCase(),scene.title,scene.detail,true);
    let panel='';
    switch(sceneId){
      case 'strength':
        return strengthScreen(false);
      case 'weightlifting': {
        const totalReps=state.components.reduce((sum,part)=>sum+Number(part.reps),0);
        const items=state.components.map((part,index)=>`<li><span>${safe(part.name)}</span><span class="step-control"><button type="button" data-action="component-step" data-index="${index}" data-delta="-1" aria-label="Quitar repetición de ${safe(part.name)}">−</button><output>${part.reps} rep</output><button type="button" data-action="component-step" data-index="${index}" data-delta="1" aria-label="Añadir repetición a ${safe(part.name)}">+</button></span></li>`).join('');
        panel=module('Complejo editable',`${field('Carga de barra','barWeight',state.barWeight,'kg',20,250,2.5)}<ol class="step-list">${items}</ol>${selectControl('componentChoice','Añadir componente',['Power jerk','Hang clean','Front squat','Clean pull'],state.componentChoice)}${secondary('Añadir componente','add-component')}<div class="divider"></div><div class="metric-row"><strong class="metric">${totalReps*Number(state.barWeight)}</strong><span class="metric-unit">kg de volumen</span></div><p class="tiny-note">${totalReps} repeticiones totales × ${state.barWeight} kg. El volumen se calcula una vez por repetición del complejo; no se vuelve a sumar por componente.</p>`);
        return `${heading}${panel}${secondary('Volver a Training','go-scene','data-scene="training"')}`;
      }
      case 'calisthenics': {
        const progressions=['Tuck','Advanced tuck','Straddle','Full'];
        panel=module('Skill · front lever',`<div class="segmented" role="group" aria-label="Progresión de la skill">${progressions.map((step,index)=>`<button type="button" data-action="skill-step" data-index="${index}" aria-pressed="${state.skillIndex===index}">${step}</button>`).join('')}</div>${stepper('Isometría',state.holdSeconds,'holdSeconds','s')}${stepper('Series completadas',state.skillSets,'skillSets','series')}<p class="tiny-note">Mantén la posición elegida durante ${state.holdSeconds} s. Progresión visual, no valoración técnica.</p>${primary('Registrar una isometría','add-hold')}`);
        return `${heading}${panel}${module('Siguiente progresión',`${row('Skill actual',progressions[state.skillIndex],`${state.skillSets} series · ${state.holdSeconds} s`)}${primary('Avanzar una progresión','advance-skill')}`)}${secondary('Volver a Training','go-scene','data-scene="training"')}`;
      }
      case 'strongman':
        panel=module('Evento',`${selectControl('event','Prueba',['Yugo','Farmer carry','Atlas stone'],state.event)}<div class="input-pair">${field('Carga','eventLoad',state.eventLoad,'kg',0,500,5)}${field('Distancia','carryMeters',state.carryMeters,'m',1,100,1)}</div>${row('Intentos registrados',state.eventAttempts,'Cada toque agrega un intento a esta muestra')}${primary('Registrar intento','add-attempt')}`);
        return `${heading}${panel}${module('Registro de evento',`${row(state.event,`${state.eventLoad} kg · ${state.carryMeters} m`,`Intentos: ${state.eventAttempts}`)}<p class="tiny-note">Los eventos comparten estructura, pero conservan el tipo de carga y la medida de cada prueba.</p>`)}`;
      case 'armwrestling':
        panel=module('Lateralidad y trabajo',`<div class="segmented" role="group" aria-label="Mano de trabajo">${['Derecha','Izquierda'].map(side=>`<button type="button" data-action="set-side" data-value="${side}" aria-pressed="${state.side===side}">${side}</button>`).join('')}</div>${field('Carga de pronación','pronationLoad',state.pronationLoad,'kg',0,100,1)}${stepper('Sostén isométrico',state.isoSec,'isoSec','s')}${stepper('Series',state.armSets,'armSets','series')}${primary('Registrar serie','add-arm-set')}`);
        return `${heading}${panel}${module('Técnica',`${row('Lado',state.side,'La selección se mantiene por ejercicio')}${row('Pronación',`${state.pronationLoad} kg · ${state.isoSec} s`,'Isometría de ejemplo')}`)}`;
      case 'crossfit': {
        const fmt=['AMRAP','EMOM','For Time'];
        panel=module('Formato y puntuación',`<div class="segmented" role="group" aria-label="Formato de WOD">${fmt.map(format=>`<button type="button" data-action="set-format" data-value="${format}" aria-pressed="${state.format===format}">${format}</button>`).join('')}</div>${state.format==='AMRAP'?`${stepper('Rondas',state.amrapRounds,'amrapRounds')}${stepper('Reps extra',state.amrapReps,'amrapReps')}`:state.format==='EMOM'?`${stepper('Reps del minuto',state.emomReps,'emomReps')}${stepper('Minutos',state.emomMinutes,'emomMinutes')}`:`${stepper('Minutos transcurridos',state.forTimeMinutes,'forTimeMinutes','min')}${stepper('Reps completadas',state.score,'score')}`}${row('Score actual',state.format==='AMRAP'?`${state.amrapRounds} rondas + ${state.amrapReps}`:state.format==='EMOM'?`${state.emomReps} reps/min · ${state.emomMinutes} min`:`${state.forTimeMinutes}:00 · ${state.score} reps`,'El formato cambia la forma de registrar')}${primary('Actualizar score','score-wod')}`);
        return `${heading}${panel}${module('Bloque de muestra',`${row('Trabajo','Thruster · 10 reps','Movimiento')}${row('Trabajo','Pull-up · 8 reps','Movimiento')}`)}`;
      }
      case 'hyrox': {
        panel=module('Estaciones y transiciones',`<ol class="step-list">${state.events.map((event,index)=>`<li><span>${safe(event)}</span><span><button class="small-button" type="button" data-action="move-event" data-index="${index}" data-delta="-1" aria-label="Subir ${safe(event)}">↑</button> <button class="small-button" type="button" data-action="move-event" data-index="${index}" data-delta="1" aria-label="Bajar ${safe(event)}">↓</button></span></li>`).join('')}</ol>${row('Distancia de carrera','8 × 1 km','Intercalada entre estaciones')}<p class="tiny-note">Mover una estación actualiza el orden del bloque de muestra.</p>`);
        return `${heading}${panel}${secondary('Restaurar orden de muestra','reset-events')}`;
      }
      case 'striking':
        panel=module('Rounds y combinaciones',`${stepper('Rounds',state.strikingRounds,'strikingRounds')}${stepper('Duración',state.roundMinutes,'roundMinutes','min')}${selectControl('comboChoice','Combinación para añadir',['Jab · cross · hook','Jab · jab · cross','Cross · hook · low kick'],state.comboChoice)}${secondary('Añadir combinación','add-combo')}<ol class="step-list">${state.combos.map(combo=>`<li><span>${safe(combo)}</span><span class="row-value">rounds 1–${state.strikingRounds}</span></li>`).join('')}</ol>`);
        return `${heading}${panel}${module('Pausa',`${stepper('Descanso entre rounds',60,'restSeconds','s')}`)}`;
      case 'grappling':
        panel=module('Posiciones y transiciones',`${selectControl('position','Posición inicial',['Guardia','Media guardia','Montada','De pie'],state.position)}${selectControl('positionTo','Transición',['Pase de guardia','Barrido','Back take','Takedown'],state.positionTo)}${secondary('Añadir transición','add-transition')}<ol class="step-list">${state.transitions.map(move=>`<li><span>${safe(move)}</span><span class="row-value">técnica</span></li>`).join('')}</ol>${stepper('Rounds de rolling',state.grapplingRounds,'grapplingRounds')}${stepper('Duración del round',state.grapplingRoundMinutes,'grapplingRoundMinutes','min')}`);
        return `${heading}${panel}${module('Rolling',`${row('Formato',`${state.grapplingRounds} × ${state.grapplingRoundMinutes} min`,'Tiempo de muestra')}`)}`;
      case 'mma':
        panel=module('Campamento',`${stepper('Semana del campamento',state.campWeek,'campWeek','/ 8')}${selectControl('mmaPhase','Foco de esta semana',['Base técnica','Intensificación','Taper'],state.mmaPhase)}<div class="divider"></div>${row('Lunes','Boxeo · 18:00','Clase fija')}${row('Miércoles','Wrestling · 19:00','Clase fija')}${row('Sábado','Sparring · 11:00','Clase fija')}${row('Clases registradas',state.mmaClasses,'Asistencias de esta muestra')}${primary('Marcar asistencia','add-class')}`);
        return `${heading}${panel}${module('Carga del campamento',`${row('Semana',`${state.campWeek||3} / 8`,state.mmaPhase)}<p class="tiny-note">Campamento de ejemplo; no asigna una carga real.</p>`)}`;
    }
    const profiles={
      running:{label:'Objetivo de carrera',fields:[['Distancia','goalDistance',10,'km',1,100,1],['Ritmo objetivo','goalPace',300,'s/km',180,600,5]],summary:()=>`${state.goalDistance} km · ${formatPace(state.goalPace)} min/km`,button:'Ajustar objetivo'},
      cycling:{label:'Perfil de potencia',fields:[['FTP','ftp',220,'W',50,500,5],['Duración de fondo','bikeMinutes',90,'min',10,360,5]],summary:()=>`FTP ${state.ftp} W · ${state.bikeMinutes} min`,button:'Guardar perfil'},
      trail:{label:'Objetivo de trail',fields:[['Distancia','trailKm',18,'km',1,120,1],['Desnivel positivo','ascent',900,'m',0,6000,50]],summary:()=>`${state.trailKm} km · +${state.ascent} m`,button:'Guardar objetivo'},
      rowing:{label:'Objetivo de remo',fields:[['Distancia','rowDistance',2000,'m',250,10000,250],['Split objetivo','rowSplit',122,'s / 500 m',60,240,1]],summary:()=>`${state.rowDistance} m · ${formatPace(state.rowSplit)} /500 m`,button:'Actualizar distancia'},
      swimming:{label:'Objetivo de piscina',fields:[['Largo de piscina','poolLength',25,'m',15,50,5],['Distancia','swimMeters',1500,'m',100,10000,100],['Ritmo objetivo','swimPace',120,'s / 100 m',60,300,5]],summary:()=>`${state.poolLength} m · ${state.swimMeters} m · ${formatPace(state.swimPace)} /100 m`,button:'Guardar objetivo'},
      triathlon:{label:'Metas por disciplina',fields:[['Natación','triSwim',1.5,'km',0.4,4,0.1],['Ciclismo','triBike',40,'km',10,180,5],['Running','triRun',10,'km',3,42,1]],summary:()=>`${state.triSwim} km · ${state.triBike} km · ${state.triRun} km`,button:'Ajustar reparto'}
    };
    if(profiles[sceneId]){
      const profile=profiles[sceneId];
      panel=module(profile.label,`${profile.fields.map(([label,name,value,unit,min,max,step])=>field(label,name,state[name]??value,unit,min,max,step)).join('')}<div class="divider"></div><div class="metric-row"><strong class="metric" style="font-size:19px">${safe(profile.summary())}</strong></div><p class="tiny-note">Perfil de ejemplo. Para editor y sesión en vivo, abre Cardio v2.</p>${primary(profile.button,'profile-update')}`);
      return `${heading}${panel}${module('Cardio aprobado',`<a class="inline-link" href="references/cardio-v2/index.html">Editor, zonas, live y transiciones · abrir Cardio v2 →</a><p class="tiny-note">Esta escena muestra objetivos por disciplina; el cardio en vivo se conserva en su maqueta aprobada.</p>`)}`;
    }
    switch(sceneId){
      case 'climbing':
        panel=module('Intentos y grado',`${selectControl('grade','Grado objetivo',['V3','V4','V5','6A','6B'],state.grade)}${stepper('Intentos',state.climbAttempts,'climbAttempts')}${stepper('Pausa entre intentos',180,'climbRest','s')}${primary('Registrar intento','add-attempt')}`);
        return `${heading}${panel}${module('Sesión',`${row('Estilo','Boulder','Muestra de entrenamiento')}${row('Grado',state.grade,`${state.climbAttempts} intentos registrados`)}`)}`;
      case 'speedjump':
        panel=module('Protocolo',`<div class="segmented" role="group" aria-label="Tipo de protocolo">${['Sprint','Saltos'].map(type=>`<button type="button" data-action="set-sprint-type" data-value="${type}" aria-pressed="${state.sprintType===type}">${type}</button>`).join('')}</div>${state.sprintType==='Sprint'?`${field('Distancia','sprintMeters',state.sprintMeters,'m',10,200,5)}${stepper('Repeticiones',state.sprintAttempts,'sprintAttempts')}${stepper('Recuperación completa',180,'sprintRest','s')}`:`${stepper('Intentos',state.jumpCount,'jumpCount')}${stepper('Saltos por serie',3,'jumpsPerSet')}${stepper('Pausa',120,'jumpRest','s')}`}${primary('Registrar intento','add-attempt')}`);
        return `${heading}${panel}${module('Resultado',`${row(state.sprintType,state.sprintType==='Sprint'?`${state.sprintMeters} m`:`${state.jumpCount} intentos`,`${state.sprintType==='Sprint'?state.sprintAttempts:state.jumpCount} registros`)}`)}`;
      case 'tennis':
        panel=module('Marcador de práctica',`<div class="match-points"><div><span class="module-label">TÚ</span><div class="point-score">${state.points[0]}</div></div><strong>·</strong><div><span class="module-label">RIVAL</span><div class="point-score">${state.points[1]}</div></div></div><div class="input-pair"><button class="action-button tonal" type="button" data-action="award-point" data-side="0">Sumar punto · tú</button><button class="action-button secondary" type="button" data-action="award-point" data-side="1">Sumar punto · rival</button></div><div class="divider"></div>${stepper('Aces',state.tennisAces,'tennisAces')}${field('Primer saque dentro','tennisFirstServe',state.tennisFirstServe,'%',0,100,1)}<p class="tiny-note">Marcador simplificado; no aplica reglas oficiales de torneo.</p>`);
        return `${heading}${panel}${module('Estadísticas del punto',`${row('Aces',state.tennisAces,'Saque directo')}${row('Primer saque',`${state.tennisFirstServe}%`,'Porcentaje de muestra')}`)}`;
      case 'padel':
        panel=module('Marcador y estadísticas',`<div class="match-points"><div><span class="module-label">TU PAREJA</span><div class="point-score">${state.points[0]}</div></div><strong>·</strong><div><span class="module-label">RIVAL</span><div class="point-score">${state.points[1]}</div></div></div><div class="input-pair"><button class="action-button tonal" type="button" data-action="award-point" data-side="0">Sumar punto · pareja</button><button class="action-button secondary" type="button" data-action="award-point" data-side="1">Sumar punto · rival</button></div>${stepper('Voleas ganadoras',state.netPoints,'netPoints')}${row('Marcador',`${state.points[0]}–${state.points[1]}`,'Puntos de práctica')}`);
        return `${heading}${panel}${secondary('Registrar volea ganadora','add-net-point')}`;
      case 'team':
        panel=module('Partido personal',`${stepper('Minutos jugados',state.minutes,'minutes','min')}${selectControl('teamAction','Acción individual',['Asistencia','Gol','Tiro','Recuperación'],state.teamAction)}${primary('Registrar acción','add-team-action')}<div class="divider"></div>${row('Goles',state.teamGoals,'Acciones personales')}${row('Asistencias',state.teamAssists,'Acciones personales')}${row('Tiros',state.teamShots,'Acciones personales')}`);
        return `${heading}${panel}${module('Participación',`${row('Tiempo en cancha',`${state.minutes} min`,'Registro personal')}${row('Acciones registradas',state.teamActions,'Registro individual')}`)}`;
      case 'sensors':
        panel=module('Estado simulado',`${selectControl('statusMode','Estado de señal',['Actualizado','Antiguo','Perdido'],state.statusMode)}${row('Último dato',state.statusMode==='Actualizado'?'hace 1 min':state.statusMode==='Antiguo'?'hace 2 h':'sin señal','Frecuencia cardíaca · muestra')}${row('Origen','Huawei Health','Compatibilidad por verificar')}${module('Compatibilidad',`<p class="tiny-note">No se solicita permiso ni se establece conexión. Esta vista solo enseña estados posibles; Huawei Health / Health Connect requiere validación de producto.</p>`,{tight:true})}${secondary('Cambiar estado de señal','cycle-sensor')}`);
        return `${heading}${panel}${module('Alternativa manual',`${row('Guía disponible','RPE','Sin sensor o con dato perdido')}${badge('Sin conexión real','warning')}`)}`;
      case 'import':
        panel=module('Revisión antes de importar',`${row('Origen','Archivo local · CSV','Vista previa')}${row('Actividad','Running · 5,0 km','18 oct · 08:15')}${row('Coincidencia','Posible duplicado','Ya existe una sesión de 5,0 km')}${selectControl('importChoice','Resolver duplicado',['','Conservar ambas','Combinar','Omitir archivo'],state.importChoice||'')}${primary('Aplicar decisión de muestra','resolve-import')}${state.importChoice?`<p class="tiny-note" role="status">Decisión local: ${safe(state.importChoice)}. No se escribió ningún archivo.</p>`:''}`);
        return `${heading}${panel}${module('Origen del dato',`${row('Selección','Manual del usuario','El prototipo no lee archivos')}`)}`;
      case 'progress':
        panel=module('Rango y procedencia',`<div class="segmented" role="group" aria-label="Período de progreso">${['30 días','90 días','1 año'].map(period=>`<button type="button" data-action="set-period" data-value="${period}" aria-pressed="${state.period===period}">${period}</button>`).join('')}</div><div class="chart-bars" aria-label="Tendencia de muestra en ${safe(state.period)}"><i style="height:38%"></i><i style="height:48%"></i><i style="height:42%"></i><i style="height:61%"></i><i style="height:71%"></i><i style="height:80%"></i></div>${row('Fuerza','e1RM sentadilla · 93 kg','Sesiones locales de ejemplo')}${row('Running','10 km · 50:00','Registro manual de ejemplo')}${row('Remo','2.000 m · 8:08','Importación simulada')}${row('Procedencia',state.progressSource,'Visible por deporte y métrica')}`);
        return `${heading}${panel}${module('Fuente visible',`${selectControl('progressSource','Procedencia mostrada',['Manual + sesiones de ejemplo','Manual','Importación simulada'],state.progressSource)}<p class="tiny-note">Sin datos reales, el valor se sustituye por “—”; esta pantalla usa datos ficticios etiquetados.</p>`)}`;
      case 'nutrition':
        panel=module('Ingesta de hoy',`<div class="metric-row"><strong class="metric">${state.calorieCount}</strong><span class="metric-unit">/ 2.200 kcal</span></div><div class="progress-track" style="margin:12px 0"><i style="width:${Math.min(100,state.calorieCount/22)}%"></i></div>${row('Proteína','118 / 140 g','Dato de ejemplo')}${row('Carbohidratos','176 / 250 g','Dato de ejemplo')}${row('Grasa','52 / 70 g','Dato de ejemplo')}${primary('Añadir registro de muestra','add-meal')}<p class="tiny-note">No se guarda en Nutrition real; control local de maqueta.</p>`);
        return `${heading}${panel}${secondary('Ir a Home','go-scene','data-scene="home"')}`;
      case 'body':
        panel=module('Tendencia corporal',`<div class="segmented" role="group" aria-label="Período corporal">${['30 días','90 días'].map(period=>`<button type="button" data-action="set-period" data-value="${period}" aria-pressed="${state.period===period}">${period}</button>`).join('')}</div><div class="chart-bars" aria-label="Gráfico de peso de muestra"><i style="height:77%"></i><i style="height:73%"></i><i style="height:68%"></i><i style="height:72%"></i><i style="height:63%"></i><i style="height:59%"></i></div><div class="metric-row"><strong class="metric">${state.bodyWeight.toFixed(1)}</strong><span class="metric-unit">kg · fuente manual</span></div>${row('Rango visible',state.period,'Puntos de muestra')}${primary('Registrar medición de muestra','add-body-weight')}<p class="tiny-note">Cada dato indica su procedencia; esta acción solo cambia la maqueta.</p>`);
        return `${heading}${panel}${secondary('Ir a Home','go-scene','data-scene="home"')}`;
      default:
        return `${heading}${module('Métrica de ejemplo',`<div class="metric-row"><strong class="metric">${safe(scene.metric)}</strong></div><p class="tiny-note">Control especializado de esta escena en modo proyección.</p>${primary(scene.action,'projection-action')}</div>`)}`;
    }
  }

  function formatPace(seconds) {
    const safeSeconds=Math.max(0,Math.round(Number(seconds)||0));
    return `${Math.floor(safeSeconds/60)}:${String(safeSeconds%60).padStart(2,'0')}`;
  }

  let renderedScene='';
  function render() {
    const scene=state.scene;
    const meta=sceneMeta[scene]||[byId.get(scene)?.title||'Escenario',byId.get(scene)?.group||'Proyección de producto'];
    const sceneInfo=byId.get(scene);
    const template = scene==='home'?appHome()
      : scene==='profile'?profileScreen()
      : scene==='wizard'?wizardScreen()
      : scene==='week'?weekScreen()
      : scene==='alternatives'?alternativesScreen()
      : scene==='hybrid'?hybridStart()
      : scene==='hybrid-strength'?strengthScreen(true)
      : scene==='hybrid-transition'?transitionScreen()
      : scene==='hybrid-cardio'?cardioSampleScreen()
      : scene==='hybrid-summary'?hybridSummary()
      : scene==='training'?appTraining()
      : screenProjection(scene);
    const scroll=renderedScene===scene?content.scrollTop:0;
    content.innerHTML=template+(state.toast?`<div class="toast" role="status">${safe(state.toast)}</div>`:'');
    content.scrollTop=scroll;
    renderedScene=scene;
    const topTitle=document.querySelector('#top-title');
    const topSubtitle=document.querySelector('#top-subtitle');
    if(topTitle) topTitle.textContent=meta[0];
    if(topSubtitle) topSubtitle.textContent=scene==='home'?'Resumen del día':'';
    const sideTitle=document.querySelector('#scene-index-title');
    const sideDetail=document.querySelector('#scene-index-detail');
    if(sideTitle) sideTitle.textContent=sceneInfo?.title||meta[0];
    if(sideDetail) sideDetail.textContent=sceneInfo?.detail||meta[1];
    const currentTab=scene==='home'?'home':scene==='nutrition'?'nutrition':scene==='body'?'body':'training';
    for(const button of document.querySelectorAll('.nav-tab')){
      if(button.dataset.tab===currentTab) button.setAttribute('aria-current','page');
      else button.removeAttribute('aria-current');
    }
  }

  function changeInput(input, rerender) {
    const name=input.dataset.input;
    const numeric=input.type==='number'||input.type==='range';
    state[name]=numeric?Number(input.value):input.value;
    if(name==='load'||name==='reps'){
      const e1rm=Number(state.reps)>0?(Number(state.load)*(1+Number(state.reps)/30)).toFixed(1):'—';
      const output=content.querySelector('[data-output="e1rm"]');
      if(output) output.textContent=e1rm;
    }
    if(rerender) render();
  }

  content.addEventListener('input',(event)=>{
    const input=event.target.closest('[data-input]');
    if(input) changeInput(input,false);
  });
  content.addEventListener('change',(event)=>{
    const input=event.target.closest('[data-input]');
    if(input) changeInput(input,true);
    const select=event.target.closest('[data-select]');
    if(select){
      if(select.dataset.select.startsWith('level-')) state.levels[select.dataset.select.slice(6)]=select.value;
      else state[select.dataset.select]=select.value;
      state.toast='Selección actualizada en esta maqueta.';
      render();
    }
  });
  document.addEventListener('click',(event)=>{
    const link=event.target.closest('a[href^="#"]');
    if(link){
      const id=link.getAttribute('href').slice(1);
      if(byId.has(id)){event.preventDefault();goto(id);return;}
    }
    const button=event.target.closest('button[data-action]');
    if(!button) return;
    const action=button.dataset.action;
    state.toast='';
    switch(action){
      case 'go-scene': goto(button.dataset.scene); return;
      case 'back': {
        const previous=state.history.pop();
        if(previous) goto(previous,false);
        else if(sceneMeta[state.scene]||byId.has(state.scene)) goto('home',false);
        else location.href='index.html';
        return;
      }
      case 'reset-demo': state=initial(); goto('home',false); return;
      case 'open-cardio': location.href='references/cardio-v2/live.html'; return;
      case 'go-alternatives': goto('alternatives'); return;
      case 'toggle-sport': {
        const sport=button.dataset.value;
        const active=Object.values(state.sports).filter(Boolean).length;
        if(state.sports[sport]&&active===1){state.toast='Mantén al menos una disciplina activa.';break;}
        state.sports[sport]=!state.sports[sport];
        if(state.sports[sport]&&!state.priorities.includes(sport)) state.priorities.push(sport);
        state.priorities=state.priorities.filter(id=>state.sports[id]);
        break;
      }
      case 'priority': {
        const id=button.dataset.sport, index=state.priorities.indexOf(id), next=index+Number(button.dataset.delta);
        if(index>=0&&next>=0&&next<state.priorities.length){[state.priorities[index],state.priorities[next]]=[state.priorities[next],state.priorities[index]];}
        break;
      }
      case 'toggle-day': {
        const day=button.dataset.value;
        state.days=state.days.includes(day)?state.days.filter(item=>item!==day):[...state.days,day];
        break;
      }
      case 'toggle-gear': {
        const gear=button.dataset.value;
        state.equipment=state.equipment.includes(gear)?state.equipment.filter(item=>item!==gear):[...state.equipment,gear];
        break;
      }
      case 'step': {
        const key=button.dataset.field, delta=Number(button.dataset.delta);
        const current=Number(state[key]||0);
        state[key]=Math.max(0,current+delta*(key==='load'||key==='barWeight'||key==='eventLoad'?2.5:1));
        break;
      }
      case 'set-focus': state.focus=button.dataset.value; break;
      case 'move-to-day': state.moveDay=button.dataset.value; state.conflict=state.moveDay==='Jue'; break;
      case 'resolve-conflict': state.moveDay='Vie'; state.conflict=false; state.toast='Conflicto resuelto: sesión mixta movida al viernes.'; break;
      case 'choose-alt': state.alternative=button.dataset.value; state.toast='Alternativa seleccionada.'; break;
      case 'add-set': state.liftSets+=1; state.toast='Serie registrada en el estado local.'; break;
      case 'add-arm-set': state.armSets+=1; state.toast='Serie de pronación registrada.'; break;
      case 'add-hold': state.skillSets+=1; state.toast='Isometría agregada a la muestra.'; break;
      case 'complete-strength': goto('hybrid-transition'); return;
      case 'finish-hybrid': state.completed=true; goto('hybrid-summary'); return;
      case 'component-step': {
        const index=Number(button.dataset.index), part=state.components[index];
        if(part) part.reps=Math.max(1,Number(part.reps)+Number(button.dataset.delta));
        break;
      }
      case 'add-component': {
        if(state.components.length<8){state.components.push({name:state.componentChoice,reps:1});state.toast=`${state.componentChoice} agregado al complejo.`;}
        else state.toast='Máximo de ocho componentes en esta maqueta.';
        break;
      }
      case 'skill-step': state.skillIndex=Number(button.dataset.index); break;
      case 'advance-skill': state.skillIndex=Math.min(3,state.skillIndex+1); state.toast='Progresión actualizada.'; break;
      case 'add-attempt':
        if(state.scene==='strongman') state.eventAttempts+=1;
        else if(state.scene==='climbing') state.climbAttempts+=1;
        else if(state.scene==='speedjump'&&state.sprintType==='Sprint') state.sprintAttempts+=1;
        else if(state.scene==='speedjump') state.jumpCount+=1;
        state.toast='Intento agregado.'; break;
      case 'set-side': state.side=button.dataset.value; break;
      case 'set-format': state.format=button.dataset.value; break;
      case 'score-wod':
        if(state.format==='AMRAP') state.amrapReps+=1;
        else if(state.format==='EMOM') state.emomReps+=1;
        else state.score+=1;
        state.toast=`Score ${state.format} actualizado.`; break;
      case 'move-event': {
        const index=Number(button.dataset.index), next=index+Number(button.dataset.delta);
        if(next>=0&&next<state.events.length){[state.events[index],state.events[next]]=[state.events[next],state.events[index]];}
        break;
      }
      case 'reset-events': state.events=['SkiErg · 1.000 m','Sled push · 50 m','Sled pull · 50 m','Burpee broad jump · 80 m','Row · 1.000 m','Farmer carry · 200 m','Sandbag lunges · 100 m','Wall balls · 100 reps']; break;
      case 'add-combo': state.combos.push(state.comboChoice); state.toast='Combinación añadida.'; break;
      case 'add-transition': state.transitions.push(`${state.position} → ${state.positionTo}`); state.toast='Transición técnica agregada.'; break;
      case 'add-class': state.mmaClasses+=1; state.toast='Asistencia marcada en la muestra.'; break;
      case 'set-sprint-type': state.sprintType=button.dataset.value; break;
      case 'award-point': state.points[Number(button.dataset.side)]+=1; state.toast='Marcador de práctica actualizado.'; break;
      case 'add-net-point': state.netPoints+=1; state.toast='Estadística de volea actualizada.'; break;
      case 'add-team-action':
        state.teamActions+=1;
        if(state.teamAction==='Gol') state.teamGoals+=1;
        else if(state.teamAction==='Asistencia') state.teamAssists+=1;
        else if(state.teamAction==='Tiro') state.teamShots+=1;
        state.toast=`Acción registrada: ${state.teamAction}.`; break;
      case 'cycle-sensor': {
        const modes=['Actualizado','Antiguo','Perdido'];
        state.statusMode=modes[(modes.indexOf(state.statusMode)+1)%modes.length];
        state.toast='Estado simulado cambiado.'; break;
      }
      case 'resolve-import':
        state.importChoice=state.importChoice||'Omitir archivo';
        state.toast=`Decisión de muestra: ${state.importChoice}. No se modificó ningún archivo.`; break;
      case 'set-period': state.period=button.dataset.value; break;
      case 'profile-update': state.toast='Perfil actualizado en esta maqueta.'; break;
      case 'projection-action': state.teamActions+=1; state.toast='Acción de muestra registrada.'; break;
      case 'add-meal': state.calorieCount+=250; state.toast='Registro de ejemplo agregado a Nutrition.'; break;
      case 'add-body-weight': state.bodyWeight=Math.max(0,state.bodyWeight-.1); state.toast='Medición de muestra agregada.'; break;
      case 'pick-home-day': state.selectedDay=button.dataset.value; break;
      default: state.toast='Acción no disponible en este escenario.';
    }
    render();
  });

  document.querySelectorAll('.nav-tab').forEach((button)=>button.addEventListener('click',()=>{
    const tab=button.dataset.tab;
    goto(tab==='training'?'training':tab);
  }));
  window.addEventListener('hashchange',()=>{
    let requested='home';
    try{requested=decodeURIComponent(location.hash.slice(1)||'home');}catch{}
    if(state.scene!==requested){state.scene=byId.has(requested)||sceneMeta[requested]?requested:'home';render();}
  });
  let route='home';
  try{route=decodeURIComponent(location.hash.slice(1)||'home');}catch{}
  state.scene=byId.has(route)||sceneMeta[route]?route:'home';
  render();
})();
