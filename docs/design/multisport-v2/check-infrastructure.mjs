import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url)),read=file=>fs.readFileSync(path.join(root,file),'utf8');
const checks=[];
function passed(name,condition){assert(condition,name);checks.push(name);}
class Element{
 constructor(tag='div'){this.tagName=tag.toUpperCase();this.dataset={};this.style={cssText:'',setProperty(){}};this.children=[];this.listeners={};this.attributes={};this.classList={add(){},remove(){},toggle(){}};this.textContent='';this.hidden=false;this.innerHTML='';}
 append(...children){for(const child of children){child.parentElement=this;this.children.push(child);}}
 replaceChildren(...children){this.children=[];this.innerHTML='';this.append(...children);}
 remove(){this.removed=true;if(this.parentElement)this.parentElement.children=this.parentElement.children.filter(c=>c!==this);}
 setAttribute(k,v){this.attributes[k]=String(v);}
 getAttribute(k){return Object.hasOwn(this.attributes,k)?this.attributes[k]:null;}
 addEventListener(type,fn){(this.listeners[type]||=[]).push(fn);}
 dispatch(type,event={}){for(const fn of this.listeners[type]||[])fn(event);}
 querySelector(){return null;}
 querySelectorAll(){return[];}
 matches(selector){return selector==='[data-field]'&&'field' in this.dataset;}
 closest(selector){let node=this;while(node){if(selector==='button'&&node.tagName==='BUTTON')return node;if(selector==='[data-scene]'&&node.dataset.scene)return node;if(selector==='[data-preview-retry]'&&'previewRetry' in node.dataset)return node;node=node.parentElement;}return null;}
}
function application(preview=false,saved={sports:['swimming']}){
 const nodes=new Map(),win=new Element('window'),counts={reads:0,writes:0};
 const node=id=>{if(!nodes.has(id))nodes.set(id,new Element());return nodes.get(id);};
 const app=node('app'),scroll={children:[{}],textContent:'Vista inicializada',scrollTop:0};
 app.querySelector=selector=>selector==='.app-scroll'&&app.innerHTML.includes('app-scroll')?scroll:null;
 const styles=['kpkn.css','app.css'].map(name=>Object.assign(new Element('link'),{href:'http://prototype.test/'+name,sheet:{}}));
 const events=new Element('document');
 const doc={body:new Element('body'),documentElement:new Element('html'),getElementById:node,querySelector:selector=>selector==='.app-scroll'?scroll:null,querySelectorAll:selector=>selector==='link[rel="stylesheet"]'?styles:[],addEventListener:(...args)=>events.addEventListener(...args),dispatch:(...args)=>events.dispatch(...args),createElement:tag=>new Element(tag),fonts:{ready:Promise.resolve(),check:()=>true}};
 const loc={hash:'#wizard-sports',search:preview?'?preview=1&previewToken=qa-token':'',href:'http://prototype.test/index.html'+(preview?'?preview=1&previewToken=qa-token':'')+'#wizard-sports',protocol:'http:',origin:'http://prototype.test'};
 const messages=[];win.parent={postMessage:data=>messages.push(data)};
 const ctx=vm.createContext({document:doc,window:win,location:loc,URL,URLSearchParams,structuredClone,localStorage:{getItem(){counts.reads++;return JSON.stringify(saved);},setItem(){counts.writes++;}},setInterval(){return 1;},setTimeout,clearTimeout,requestAnimationFrame:fn=>fn(),console});
 return{ctx,doc,win,loc,app,counts,messages,run:code=>vm.runInContext(code,ctx)};
}
function loadApplication(env){for(const file of ['kpkn.js','scenarios.js','screens.js','sports.js','new-core.js','app.js'])vm.runInContext(read(file),env.ctx,{filename:'http://prototype.test/'+file});}
const preview=application(true);loadApplication(preview);preview.run('save()');
passed('Preview neither reads nor writes editable localStorage',preview.counts.reads===0&&preview.counts.writes===0);
passed('Preview uses its own initial state',preview.run("P.sports.join(',')==='strength,running'"));
const editable=application(false);loadApplication(editable);
passed('Normal review retains existing storage behavior',editable.counts.reads===1&&editable.counts.writes>0&&editable.run("P.sports[0]==='swimming'"));

const selectors=application(false);loadApplication(selectors);selectors.loc.hash='#wizard-availability';selectors.run('render()');
function editField(env,key,value,tag='select',attributes={},insideSheet=false){
 const input=new Element(tag);input.dataset.field=key;input.value=String(value);input.type=tag==='select'?'select-one':'number';
 for(const [name,limit] of Object.entries(attributes))input.setAttribute(name,limit);
 if(insideSheet)input.closest=selector=>selector==='.sheet'?{}:null;
 env.doc.dispatch('input',{target:input});env.doc.dispatch('change',{target:input});
}
editField(selectors,'windowDay',4);
passed('Real input/change handler keeps window day four finite and renders its fields',selectors.run('P.windowDay===4')&&selectors.app.innerHTML.includes('data-field="windows.4.start"')&&!selectors.app.innerHTML.includes('NaN'));
editField(selectors,'classDay',1,'select',{},true);
passed('Real input handler keeps commitment day one and updates its declared day',selectors.run("P.classDay===1&&fixedCommitment().day===1&&commitmentBlock().includes('Martes')"));
editField(selectors,'poolLength',50);
passed('Another numeric select keeps its value without min/max attributes',selectors.run('P.poolLength===50'));
editField(selectors,'armAngle',200,'input',{min:'0',max:'180'});
const boundedAngle=selectors.run('P.armAngle');
editField(selectors,'armAngle',95,'input',{min:'invalid',max:''});
passed('Numeric inputs enforce valid bounds and ignore missing or invalid bounds',boundedAngle===180&&selectors.run('P.armAngle===95'));
const recovered=application(false,{windowDay:NaN,classDay:null,homeDay:-1,sports:['strength','mma'],objectives:{strength:'muscle',mma:'camp'},levels:{mma:'advanced'},priority:'mma'});loadApplication(recovered);recovered.loc.hash='#wizard-availability';recovered.run('render()');
passed('Persisted invalid day selectors recover without resetting valid sports or objectives',recovered.run("P.windowDay===INITIAL.windowDay&&P.classDay===INITIAL.classDay&&P.homeDay===INITIAL.homeDay&&P.sports.join(',')==='strength,mma'&&P.objectives.strength==='muscle'&&P.objectives.mma==='camp'&&P.levels.mma==='advanced'&&P.priority==='mma'")&&!recovered.app.innerHTML.includes('undefined'));
const validDays=application(false,{windowDay:4,classDay:1,homeDay:0});loadApplication(validDays);
passed('Valid persisted day selections remain unchanged',validDays.run('P.windowDay===4&&P.classDay===1&&P.homeDay===0'));

async function bootstrap({missing='',foreignError=false,fontFailure=false,missingStyle=false}={}){
 const env=application(true),loaded=[];
 if(fontFailure)env.doc.fonts.check=()=>false;
 if(missingStyle)env.doc.querySelectorAll=()=>[];
 env.doc.body.append=script=>{const file=script.src.slice(2);loaded.push(file);Promise.resolve().then(()=>{
  if(file===missing){script.onerror();return;}
  try{vm.runInContext(read(file),env.ctx,{filename:'http://prototype.test/'+file});if(foreignError&&file==='kpkn.js')env.win.dispatch('error',{error:new Error('MutationObserver from instrumentation'),message:'MutationObserver from instrumentation',filename:''});script.onload();}
  catch(error){env.win.dispatch('error',{error,message:error.message,filename:'http://prototype.test/'+file});script.onload();}
 });};
 await vm.runInContext(read('boot.js'),env.ctx,{filename:'http://prototype.test/boot.js'});
 return{...env,loaded};
}
const boot=await bootstrap({foreignError:true});
passed('Bootstrap verifies dependencies in order',boot.loaded.join(',')==='kpkn.js,scenarios.js,screens.js,sports.js,new-core.js,app.js');
passed('Ready handshake requires renderer content and route',boot.messages.some(m=>m.status==='ready'&&m.scene==='wizard-sports'&&m.token==='qa-token')&&boot.doc.documentElement.dataset.rendererReady==='true');
passed('Foreign error without own source does not replace renderer',!boot.doc.documentElement.dataset.bootError&&boot.app.querySelector('.app-scroll'));
boot.win.dispatch('error',{error:new Error('Owned failure'),message:'Owned failure',filename:'http://prototype.test/app.js'});
passed('Own-source failure is exposed without technical product copy',boot.messages.some(m=>m.status==='error')&&boot.doc.documentElement.dataset.rendererReady==='false'&&!boot.app.children.map(c=>c.children.map(n=>n.textContent).join(' ')).join(' ').includes('app.js'));
const missing=await bootstrap({missing:'scenarios.js'});
passed('Missing scenarios stops before app instead of ReferenceError',!missing.loaded.includes('app.js')&&missing.messages.some(m=>m.status==='error'&&m.detail.includes('scenarios.js')));
const missingFont=await bootstrap({fontFailure:true});
passed('Ready handshake rejects unresolved brand fonts',!missingFont.messages.some(m=>m.status==='ready')&&missingFont.messages.some(m=>m.status==='error'));
const missingStyle=await bootstrap({missingStyle:true});
passed('Ready handshake rejects missing design stylesheet',!missingStyle.messages.some(m=>m.status==='ready')&&missingStyle.messages.some(m=>m.status==='error'));

function gallery(){
 const grid=new Element(),tabs=new Element(),win=new Element('window'),cards=new Map(),timers=new Map(),rafs=[];let timer=0,observer;
 const ctx=vm.createContext({window:win,location:{protocol:'http:',origin:'http://prototype.test'},URLSearchParams,Date,console,setTimeout:fn=>{timers.set(++timer,fn);return timer;},clearTimeout:id=>timers.delete(id),requestAnimationFrame:fn=>rafs.push(fn),IntersectionObserver:class{constructor(callback){this.callback=callback;observer=this;}observe(){}},document:{getElementById:id=>id==='gallery-grid'?grid:tabs,createElement:tag=>{const el=new Element(tag);if(tag==='iframe')el.contentWindow={};return el;}}});
 vm.runInContext(read('scenarios.js'),ctx);const scenes=JSON.parse(vm.runInContext('JSON.stringify(SCENARIOS)',ctx));
 scenes.forEach((scene,i)=>{
  const card=new Element('article'),box=new Element(),host=new Element(),status=new Element(),title=new Element('strong'),copy=new Element('p'),retry=new Element('button');
  card.dataset.scene=scene.id;card.dataset.group=scene.group;retry.dataset.previewRetry='';retry.hidden=true;box.top=i<3?100:5000+i*700;
  box.getBoundingClientRect=()=>({top:box.top,bottom:box.top+623,height:card.hidden?0:623});
  status.append(title,copy,retry);box.append(host,status);card.append(box);
  status.querySelector=s=>s==='strong'?title:s==='p'?copy:retry;box.querySelector=s=>s==='.preview-host'?host:status;card.querySelector=()=>box;
  cards.set(scene.id,{card,box,host,status,retry});
 });
 grid.querySelector=selector=>cards.get(selector.match(/data-scene="([^"]+)"/)[1]).card;
 win.innerHeight=800;vm.runInContext(read('gallery.js'),ctx,{filename:'gallery.js'});
 const flush=()=>{while(rafs.length)rafs.shift()();};flush();
 const liveFrame=id=>cards.get(id).host.children.find(f=>!f.removed);
 function message(id,status,options={}){const frame=liveFrame(id),query=new URLSearchParams(frame.src.split('?')[1].split('#')[0]);win.dispatch('message',{origin:'http://prototype.test',source:options.source||frame.contentWindow,data:{type:'kpkn-preview',status,token:options.token||query.get('previewToken'),scene:id,detail:options.detail||'',stage:'app.js'}});}
 return{grid,cards,scenes,observer,timers,message,liveFrame,flush};
}
const gal=gallery(),[first,second,third]=gal.scenes.map(s=>s.id);
passed('Gallery starts only two near previews out of 46',gal.scenes.length===46&&gal.grid.dataset.active==='2'&&gal.grid.dataset.maxActiveSeen==='2'&&gal.grid.dataset.limit==='2'&&gal.grid.dataset.frames==='2');
gal.liveFrame(first).dispatch('load');
passed('Document load alone does not mark preview ready',gal.cards.get(first).box.dataset.state==='loading'&&gal.grid.dataset.active==='2');
gal.message(first,'ready',{token:'wrong'});
passed('Handshake rejects mismatched token',gal.cards.get(first).box.dataset.state==='loading');
gal.message(first,'ready',{source:{}});
passed('Handshake rejects another frame source',gal.cards.get(first).box.dataset.state==='loading');
gal.message(first,'ready');
passed('Confirmed renderer releases one slot while preserving cap two',gal.cards.get(first).box.dataset.state==='ready'&&gal.cards.get(third).box.dataset.state==='loading'&&gal.grid.dataset.active==='2'&&gal.grid.dataset.maxActiveSeen==='2');
gal.message(second,'error',{detail:'SCENARIOS initialization failed'});
passed('Failed preview shows retry and retains QA detail',gal.cards.get(second).box.dataset.state==='error'&&!gal.cards.get(second).retry.hidden&&gal.cards.get(second).box.dataset.errorDetail.includes('SCENARIOS')&&!gal.liveFrame(second));
gal.grid.dispatch('click',{target:gal.cards.get(second).retry});
passed('Retry creates a new bounded attempt',gal.cards.get(second).box.dataset.state==='loading'&&gal.cards.get(second).box.dataset.attempts==='2'&&gal.grid.dataset.active==='2');
gal.cards.get(first).box.top=5000;gal.observer.callback([{target:gal.cards.get(first).box,isIntersecting:false}]);
passed('Preview outside visible zone is unloaded with explicit idle state',gal.cards.get(first).box.dataset.state==='idle'&&!gal.liveFrame(first));
const timeout=gal.timers.values().next().value;timeout();
passed('Unconfirmed initialization becomes a visible retry state',Array.from(gal.cards.values()).some(c=>c.box.dataset.state==='error'&&!c.retry.hidden)&&Number(gal.grid.dataset.maxActiveSeen)<=2);
const failedCatalog=new Element(),catalogRetry=new Element('button');
vm.runInNewContext(read('gallery.js'),{document:{getElementById:id=>id==='gallery-grid'?failedCatalog:catalogRetry},location:{reload(){}}});
passed('Gallery missing catalog has a visible retry instead of ReferenceError',failedCatalog.innerHTML.includes('No se pudo cargar el catálogo')&&catalogRetry.listeners.click?.length===1);

const movement=application(false);loadApplication(movement);movement.run("K.sheet=(title,body)=>{window.lastSheet={title,body};};K.toast=text=>{window.lastToast=text;};");
const run=code=>movement.run(code);
run("P=structuredClone(INITIAL);P.className='Judo';P.classDay=4;P.classStart='20:00';P.classEnd='21:30';moveSheet('force-a')");
passed('Move sheet uses actual commitment instead of pilot label',movement.win.lastSheet.body.includes('Judo 20:00–21:30')&&!movement.win.lastSheet.body.includes('Grappling 19:00–20:00'));
function placement(sport,place,inventory,active=true,transfer=''){
 run(`P=structuredClone(INITIAL);P.classEnabled=false;P.available=[0,4];P.windows[4]={start:'18:00',end:'20:00',place:${JSON.stringify(place)}};P.activePlaces=${JSON.stringify(active?['gym',place]:['outside'])};P.places[${JSON.stringify(place)}]=${JSON.stringify(inventory)};P.powerliftingPath=${JSON.stringify(transfer)};P.calendar=[{id:'move',name:'Actividad',sport:${JSON.stringify(sport)},day:0,time:'18:00',min:45,place:'gym'}];P.moving='move';moveTo(4)`);
 return run("P.calendar.find(s=>s.id==='move').day");
}
passed('Move rejects inactive venue',placement('strength','gym',['bar','rack'],false)===0);
passed('Move rejects running without exterior',placement('running','home',['db'])===0);
passed('Move rejects swimming without pool',placement('swimming','pool',[])===0);
passed('Move rejects powerlifting without target bar and rack',placement('powerlifting','home',['db'])===0);
passed('Move preserves declared transferable preparation',placement('powerlifting','home',['db'],true,'transfer')===4);
run("P=structuredClone(INITIAL);P.activePlaces=['outside'];P.alternative=1;const before=JSON.stringify(P.calendar);action('apply-alternative',{dataset:{}});window.unchanged=before===JSON.stringify(P.calendar)");
passed('Alternative confirmation rejects inactive venue without mutation',movement.win.unchanged&&String(movement.win.lastToast).includes('no está activa'));
run("P=structuredClone(INITIAL);P.classEnabled=false;P.calendar=[{id:'move',name:'Running',sport:'running',day:1,time:'18:30',min:30,place:'outside'}];P.moving='move';P.alternative=1;window.reason=alternativeIssue(movingSession(),alternativeOptions()[1])");
passed('Alternative shares running target venue restrictions',String(movement.win.reason).includes('Exterior'));
run("P=structuredClone(INITIAL);P.classEnabled=false;P.calendar=[{id:'move',name:'Swim',sport:'swimming',day:0,time:'18:00',min:30,place:'pool'}];P.moving='move';P.alternative=1;P.activePlaces=['gym','pool'];window.reason=alternativeIssue(movingSession(),alternativeOptions()[1])");
passed('Alternative shares swimming target material restrictions',String(movement.win.reason).includes('piscina'));
run("P=structuredClone(INITIAL);P.classEnabled=false;P.calendar=[{id:'move',name:'PL',sport:'powerlifting',day:0,time:'18:00',min:45,place:'gym'}];P.moving='move';P.alternative=1;P.windows[4].place='home';P.activePlaces=['gym','home'];window.reason=alternativeIssue(movingSession(),alternativeOptions()[1]);P.powerliftingPath='transfer';window.transferReason=alternativeIssue(movingSession(),alternativeOptions()[1])");
passed('Alternative preserves declared preparation and labels its effective venue change',String(movement.win.reason).includes('barra')&&movement.win.transferReason===''&&run("V.alternative().includes('Sede: Casa.')&&V.alternative().includes('La actividad pasa de Gimnasio a Casa.')"));
const report={kind:'supplementary-preview-and-placement-infrastructure',checks,checkCount:checks.length,baseSportsContracts:36,scenes:46,browserQA:'Separate orchestrator browser verification required',originalReferenceErrorCause:'Not established by this check'};
fs.writeFileSync(path.join(root,'QA-INFRASTRUCTURE.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({scenes:46,baseSportsContracts:36,supplementaryChecks:checks.length,failures:0}));
