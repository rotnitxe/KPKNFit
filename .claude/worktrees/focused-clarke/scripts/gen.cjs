const fs = require("fs");
function line(id,nm,desc,musc,subM,cat,typ,eq,frc,bp,chn,st,td,efc,cnc,ssc){
  return "  { id: '"+id+"', name: '"+nm+"', description: '"+desc+"', involvedMuscles: [{ muscle: '"+musc+"', role: 'primary', activation: 1.0 }], subMuscleGroup: '"+subM+"', category: '"+cat+"', type: '"+typ+"', equipment: '"+eq+"', force: '"+frc+"', bodyPart: '"+bp+"', chain: '"+chn+"', setupTime: "+st+", technicalDifficulty: "+td+", efc: "+efc+", cnc: "+cnc+", ssc: "+ssc+" }";
}
const ex = [
  ["db_exp3_bb_bench_ez","Press de Banca con Barra EZ","Press horizontal con barra curva","Pectoral Medio","Pectoral Medio","Hipertrofia","Basico","Barra","Empuje","upper","anterior",3,4,3.0,2.5,0.0],
  ["db_exp3_bb_incline_cambered","Press Inclinado Barra Camber","Barra camber inclinado","Pectoral Superior","Pectoral Superior","Hipertrofia","Basico","Barra","Empuje","upper","anterior",4,5,3.2,2.8,0.0],
];
fs.writeFileSync("exp3_bulk.txt", ex.map(e=>line(...e)).join(",\n"));
console.log("Done", ex.length);