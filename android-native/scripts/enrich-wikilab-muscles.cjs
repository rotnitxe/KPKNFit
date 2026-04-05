const fs = require('fs')
const path = require('path')

const filePath = path.resolve(__dirname, '../app/src/main/assets/wikilab/muscles.json')
const muscles = JSON.parse(fs.readFileSync(filePath, 'utf8'))

const updates = {
  'pectoral-inferior': {
    description:
      'Porcion abdominal del pectoral mayor. Predomina en aduccion horizontal y extension del hombro desde posiciones elevadas, con alto aporte en fondos y presses declinados.',
    importanceMovement:
      'Clave en empujes con vector descendente y en fases de cierre del brazo hacia el tronco.',
    importanceHealth:
      'Su desarrollo equilibra la mecanica anterior del hombro cuando se combina con trabajo de espalda y manguito.',
    origin: 'Esternon inferior y aponeurosis del oblicuo externo',
    insertion: 'Cresta del tuberculo mayor del humero',
    mechanicalFunctions: ['Aduccion horizontal', 'Extension del hombro desde flexion', 'Rotacion interna'],
    relatedJoints: ['glenohumeral', 'escapulotoracica'],
    relatedTendons: []
  },
  'redondo-mayor': {
    description:
      'Sinergista potente del dorsal ancho en extension y aduccion del hombro. Aporta control posterior de hombro en patrones de tiron.',
    importanceMovement:
      'Mejora eficiencia de dominadas, jalones y remos al asistir la aduccion del humero.',
    importanceHealth:
      'Contribuye a estabilidad glenohumeral posterior y a repartir carga en tirones pesados.',
    origin: 'Angulo inferior y borde lateral de la escapula',
    insertion: 'Labio medial del surco intertubercular del humero',
    mechanicalFunctions: ['Aduccion del hombro', 'Extension del hombro', 'Rotacion interna'],
    relatedJoints: ['glenohumeral', 'escapulotoracica'],
    relatedTendons: []
  },
  trapecio: {
    mechanicalFunctions: ['Elevacion escapular', 'Retraccion escapular', 'Depresion escapular', 'Rotacion superior de escapula'],
    relatedJoints: ['escapulotoracica', 'acromioclavicular', 'esternoclavicular', 'columna-cervical'],
    relatedTendons: []
  },
  'trapecio-superior': {
    origin: 'Linea nucal superior, ligamento nucal',
    insertion: 'Tercio lateral de la clavicula y acromion',
    mechanicalFunctions: ['Elevacion escapular', 'Rotacion superior escapular', 'Extension cervical asistida'],
    relatedJoints: ['escapulotoracica', 'acromioclavicular', 'columna-cervical'],
    relatedTendons: []
  },
  'trapecio-medio': {
    origin: 'Apofisis espinosas C7-T5',
    insertion: 'Acromion y espina de la escapula',
    mechanicalFunctions: ['Retraccion escapular', 'Estabilidad escapular en tirones'],
    relatedJoints: ['escapulotoracica', 'acromioclavicular'],
    relatedTendons: []
  },
  'trapecio-inferior': {
    origin: 'Apofisis espinosas T6-T12',
    insertion: 'Porcion medial de la espina escapular',
    mechanicalFunctions: ['Depresion escapular', 'Rotacion superior escapular', 'Control overhead'],
    relatedJoints: ['escapulotoracica', 'acromioclavicular'],
    relatedTendons: []
  },
  romboides: {
    origin: 'Apofisis espinosas C7-T5',
    insertion: 'Borde medial de la escapula',
    mechanicalFunctions: ['Retraccion escapular', 'Rotacion inferior escapular', 'Estabilidad postural toracica'],
    relatedJoints: ['escapulotoracica', 'columna-toracica'],
    relatedTendons: []
  },
  'erectores-espinales': {
    origin: 'Sacro, cresta iliaca, apofisis espinosas lumbares',
    insertion: 'Costillas y apofisis transversas/espinosas toracicas y cervicales',
    mechanicalFunctions: ['Extension de columna', 'Anti-flexion del tronco', 'Estabilidad segmentaria bajo carga'],
    relatedJoints: ['columna-lumbar', 'columna-toracica', 'sacroiliaca'],
    relatedTendons: []
  },
  'deltoides-lateral': {
    description:
      'Cabeza media del deltoides, principal responsable de la abduccion del hombro en plano escapular.',
    origin: 'Acromion de la escapula',
    insertion: 'Tuberculo deltoideo del humero',
    mechanicalFunctions: ['Abduccion del hombro', 'Estabilizacion glenohumeral durante elevacion'],
    relatedJoints: ['glenohumeral', 'escapulotoracica'],
    relatedTendons: []
  },
  'deltoides-posterior': {
    description:
      'Cabeza posterior del deltoides, protagonista en extension horizontal y rotacion externa del hombro.',
    origin: 'Espina de la escapula',
    insertion: 'Tuberculo deltoideo del humero',
    mechanicalFunctions: ['Extension del hombro', 'Abduccion horizontal', 'Rotacion externa'],
    relatedJoints: ['glenohumeral', 'escapulotoracica'],
    relatedTendons: []
  },
  'cabeza-larga-bíceps': {
    mechanicalFunctions: ['Flexion de codo', 'Supinacion', 'Flexion de hombro', 'Estabilizacion anterior glenohumeral'],
    relatedJoints: ['codo', 'glenohumeral'],
    relatedTendons: ['tendon-bíceps-largo']
  },
  'cabeza-corta-bíceps': {
    mechanicalFunctions: ['Flexion de codo', 'Supinacion', 'Aduccion del hombro asistida'],
    relatedJoints: ['codo', 'glenohumeral'],
    relatedTendons: ['tendon-bíceps']
  },
  braquial: {
    origin: 'Cara anterior distal del humero',
    insertion: 'Tuberosidad del cubito',
    mechanicalFunctions: ['Flexion de codo pura'],
    relatedJoints: ['codo'],
    relatedTendons: []
  },
  braquiorradial: {
    origin: 'Cresta supracondilea lateral del humero',
    insertion: 'Apofisis estiloides del radio',
    mechanicalFunctions: ['Flexion de codo en agarre neutro', 'Asistencia en pronosupinacion'],
    relatedJoints: ['codo', 'radiocubital-proximal'],
    relatedTendons: []
  },
  tríceps: {
    origin: 'Tuberculo infraglenoideo y cara posterior del humero',
    insertion: 'Olecranon del cubito',
    mechanicalFunctions: ['Extension de codo', 'Extension de hombro (cabeza larga)'],
    relatedJoints: ['codo', 'glenohumeral'],
    relatedTendons: ['tendon-tríceps']
  },
  'cabeza-larga-tríceps': {
    origin: 'Tuberculo infraglenoideo de la escapula',
    insertion: 'Olecranon del cubito',
    mechanicalFunctions: ['Extension de codo', 'Extension de hombro', 'Aduccion de hombro asistida'],
    relatedJoints: ['codo', 'glenohumeral'],
    relatedTendons: ['tendon-tríceps']
  },
  'cabeza-lateral-tríceps': {
    origin: 'Cara posterior del humero por encima del surco radial',
    insertion: 'Olecranon del cubito',
    mechanicalFunctions: ['Extension de codo de alta fuerza'],
    relatedJoints: ['codo'],
    relatedTendons: ['tendon-tríceps']
  },
  'cabeza-medial-tríceps': {
    origin: 'Cara posterior del humero por debajo del surco radial',
    insertion: 'Olecranon del cubito',
    mechanicalFunctions: ['Extension de codo en todo el rango'],
    relatedJoints: ['codo'],
    relatedTendons: ['tendon-tríceps']
  },
  'flexores-de-antebrazo': {
    mechanicalFunctions: ['Flexion de muñeca', 'Flexion de dedos', 'Agarre de fuerza'],
    relatedJoints: ['muñeca', 'codo'],
    relatedTendons: ['tendon-flexores-muñeca']
  },
  'extensores-de-antebrazo': {
    mechanicalFunctions: ['Extension de muñeca', 'Extension de dedos', 'Estabilidad de agarre'],
    relatedJoints: ['muñeca', 'codo'],
    relatedTendons: ['tendon-extensores-muñeca']
  },
  'vasto-lateral': {
    origin: 'Trocanter mayor y linea aspera lateral del femur',
    insertion: 'Tendon cuadricipital hacia rotula y tuberosidad tibial',
    mechanicalFunctions: ['Extension de rodilla', 'Estabilidad lateral patelofemoral'],
    relatedJoints: ['rodilla'],
    relatedTendons: ['tendon-cuádriceps', 'tendon-rotuliano']
  },
  'vasto-medial': {
    origin: 'Linea intertrocanterica y linea aspera medial del femur',
    insertion: 'Tendon cuadricipital hacia rotula y tuberosidad tibial',
    mechanicalFunctions: ['Extension de rodilla', 'Control medial de rotula'],
    relatedJoints: ['rodilla'],
    relatedTendons: ['tendon-cuádriceps', 'tendon-rotuliano']
  },
  'recto-femoral': {
    origin: 'Espina iliaca anteroinferior',
    insertion: 'Tendon cuadricipital hacia rotula y tuberosidad tibial',
    mechanicalFunctions: ['Extension de rodilla', 'Flexion de cadera'],
    relatedJoints: ['rodilla', 'cadera'],
    relatedTendons: ['tendon-cuádriceps', 'tendon-rotuliano']
  },
  isquiosurales: {
    origin: 'Tuberosidad isquiatica (excepto cabeza corta del biceps femoral)',
    insertion: 'Tibia proximal y cabeza del perone segun porcion',
    mechanicalFunctions: ['Extension de cadera', 'Flexion de rodilla', 'Control excéntrico en sprint'],
    relatedJoints: ['cadera', 'rodilla'],
    relatedTendons: ['tendon-isquiotibiales']
  },
  'bíceps-femoral': {
    mechanicalFunctions: ['Extension de cadera (cabeza larga)', 'Flexion de rodilla', 'Rotacion externa de tibia'],
    relatedJoints: ['cadera', 'rodilla'],
    relatedTendons: ['tendon-isquiotibiales']
  },
  semitendinoso: {
    mechanicalFunctions: ['Extension de cadera', 'Flexion de rodilla', 'Rotacion interna de tibia'],
    relatedJoints: ['cadera', 'rodilla'],
    relatedTendons: ['tendon-isquiotibiales']
  },
  semimembranoso: {
    mechanicalFunctions: ['Extension de cadera', 'Flexion de rodilla', 'Rotacion interna de tibia'],
    relatedJoints: ['cadera', 'rodilla'],
    relatedTendons: ['tendon-isquiotibiales']
  },
  aductores: {
    origin: 'Rama pubica e isquiatica',
    insertion: 'Linea aspera y tuberculo del aductor en femur',
    mechanicalFunctions: ['Aduccion de cadera', 'Estabilidad frontal de pelvis', 'Asistencia en extension de cadera'],
    relatedJoints: ['cadera', 'rodilla'],
    relatedTendons: []
  },
  'glúteo-medio': {
    origin: 'Cara externa del ilion',
    insertion: 'Trocanter mayor del femur',
    mechanicalFunctions: ['Abduccion de cadera', 'Estabilidad pélvica monopodal', 'Rotacion interna/externa segun fibras'],
    relatedJoints: ['cadera', 'rodilla'],
    relatedTendons: []
  },
  'glúteo-menor': {
    origin: 'Ilion entre lineas gluteas anterior e inferior',
    insertion: 'Cara anterior del trocanter mayor',
    mechanicalFunctions: ['Abduccion de cadera', 'Rotacion interna', 'Estabilidad pélvica'],
    relatedJoints: ['cadera'],
    relatedTendons: []
  },
  sóleo: {
    origin: 'Linea del soleo en tibia y cabeza del perone',
    insertion: 'Calcaneo via tendon de Aquiles',
    mechanicalFunctions: ['Flexion plantar', 'Control postural en apoyo', 'Bomba venosa de pantorrilla'],
    relatedJoints: ['tobillo', 'subtalar'],
    relatedTendons: ['tendon-aquiles']
  },
  'recto-abdominal': {
    mechanicalFunctions: ['Flexion de tronco', 'Retroversion pélvica', 'Control anti-extension'],
    relatedJoints: ['columna-lumbar', 'columna-toracica'],
    relatedTendons: []
  },
  oblicuos: {
    mechanicalFunctions: ['Rotacion de tronco', 'Inclinacion lateral', 'Anti-rotacion', 'Control respiratorio asistido'],
    relatedJoints: ['columna-lumbar', 'columna-toracica'],
    relatedTendons: []
  },
  'transverso-abdominal': {
    mechanicalFunctions: ['Compresion abdominal', 'Estabilidad lumbopélvica', 'Control de presion intraabdominal'],
    relatedJoints: ['columna-lumbar', 'sacroiliaca'],
    relatedTendons: []
  },
  'serrato-anterior': {
    mechanicalFunctions: ['Protraccion escapular', 'Rotacion superior escapular', 'Adherencia escapular a parrilla costal'],
    relatedJoints: ['escapulotoracica', 'glenohumeral'],
    relatedTendons: []
  },
  'tibial-anterior': {
    mechanicalFunctions: ['Dorsiflexion de tobillo', 'Inversion del pie', 'Control de apoyo del talon'],
    relatedJoints: ['tobillo', 'subtalar'],
    relatedTendons: []
  },
  multífidos: {
    mechanicalFunctions: ['Estabilidad segmentaria vertebral', 'Control anti-rotacion local', 'Extension lumbar asistida'],
    relatedJoints: ['columna-lumbar', 'columna-toracica'],
    relatedTendons: []
  },
  'suelo-pélvico': {
    mechanicalFunctions: ['Soporte visceral', 'Control de presion intraabdominal', 'Sinergia respiratoria con diafragma'],
    relatedJoints: ['sacroiliaca', 'columna-lumbar'],
    relatedTendons: []
  },
  diafragma: {
    mechanicalFunctions: ['Inspiracion', 'Control de presion intraabdominal', 'Estabilidad del core profunda'],
    relatedJoints: ['columna-toracica', 'columna-lumbar'],
    relatedTendons: []
  },
  cuello: {
    mechanicalFunctions: ['Flexion cervical', 'Extension cervical', 'Rotacion cervical', 'Inclinacion lateral'],
    relatedJoints: ['columna-cervical', 'columna-toracica'],
    relatedTendons: []
  }
}

const byId = Object.fromEntries(muscles.map((m) => [m.id, m]))
for (const [id, patch] of Object.entries(updates)) {
  if (byId[id]) Object.assign(byId[id], patch)
}

fs.writeFileSync(filePath, JSON.stringify(muscles, null, 2) + '\n', 'utf8')
console.log('muscles enriched:', Object.keys(updates).length)
