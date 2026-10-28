// Datos sintéticos de ejemplo (no son del usuario) para el lienzo «Registros v1».
const MEETS=[
  {id:'m3',name:'Open regional',fed:'AEP',date:'16 nov 2026',city:'Madrid',st:'next',days:18,cls:'-83 kg',div:'Open',eq:'Classic'},
  {id:'m2',name:'Copa de otoño',fed:'AEP',date:'21 sep 2026',city:'Toledo',st:'done',medal:'oro',place:1,total:552.5,pts:79.4,ptsF:'IPF GL',best:{S:200,B:135,D:217.5},pr:true,delta:'+12,5'},
  {id:'m1',name:'Clasificatorio primavera',fed:'AEP',date:'12 abr 2026',city:'Valencia',st:'done',medal:'bronce',place:3,total:540,pts:77.9,ptsF:'IPF GL',best:{S:195,B:132.5,D:212.5}},
  {id:'m0',name:'Novatos 2025',fed:'AEP',date:'9 nov 2025',city:'Madrid',st:'done',medal:'none',place:'5.º',total:505,pts:73.1,ptsF:'IPF GL',best:{S:180,B:122.5,D:202.5}}];
// Resultado del día: tres intentos por levantamiento. r: ok | no | x (pendiente) | skip.
const ATT={S:[[187.5,'ok'],[195,'ok'],[200,'ok']],B:[[127.5,'ok'],[132.5,'ok'],[135,'no']],D:[[205,'ok'],[212.5,'ok'],[217.5,'ok']]};
const LIFTN={S:'Sentadilla',B:'Press banca',D:'Peso muerto'};
const MEDAL={oro:{n:'Oro',c:'#f7cf73',c2:'#b8913a'},plata:{n:'Plata',c:'#d9d6d0',c2:'#8f8b84'},bronce:{n:'Bronce',c:'#e39a6a',c2:'#9a5b37'},none:{n:'Sin podio',c:'#8fb2ff',c2:'#4f6ea8'}};
// Registro de comida: «Dos huevos revueltos con dos tostadas, medio aguacate y un café con leche».
const MEAL_TEXT='Dos huevos revueltos con dos tostadas, medio aguacate y un café con leche';
const FOODS=[
  {n:'Huevos revueltos',q:'2 huevos · 110 g',k:182,p:13,c:2,f:14,ic:'egg',tok:'Dos huevos revueltos'},
  {n:'Pan tostado',q:'2 rebanadas · 60 g',k:158,p:5,c:29,f:2,ic:'bread',tok:'dos tostadas',ask:'oil'},
  {n:'Aguacate',q:'½ pieza · 70 g',k:112,p:1,c:6,f:10,ic:'avo',tok:'medio aguacate'},
  {n:'Café con leche',q:'1 taza · 240 ml',k:96,p:6,c:9,f:4,ic:'cup',tok:'un café con leche',ask:'milk',approx:true}];
const RECENT=[['Avena con plátano','Desayuno de ayer · 412 kcal'],['Pollo con arroz','Almuerzo del lunes · 640 kcal'],['Yogur griego','3 veces esta semana · 146 kcal']];
// Cuerpo y nutrición (12 semanas).
const BODY={w:[84.6,84.2,83.9,83.1,82.8,82.6,82.0,81.7,81.2,80.9,80.6,80.4],waist:[-0,-0.6,-1.1,-1.8,-2.2,-2.9,-3.4,-3.8,-4.4,-4.9,-5.6,-6.1]};
const NUTRI={days:[['L',96],['M',102],['X',88],['J',99],['V',104],['S',121],['D',95]],p:162,c:228,f:71,kcal:2240,target:2300,streak:23};
