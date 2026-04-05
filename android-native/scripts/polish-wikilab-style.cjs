const fs = require('fs')
const path = require('path')

const filePath = path.resolve(__dirname, '../app/src/main/assets/wikilab/muscles.json')
const muscles = JSON.parse(fs.readFileSync(filePath, 'utf8'))

const updates = {
  espalda: {
    origin: 'Apofisis espinosas toracicas/lumbares, fascia toracolumbar, cresta iliaca y costillas segun subgrupo',
    insertion: 'Humero, escapula y estructuras fasciales toracicas segun subgrupo'
  },
  hombros: {
    origin: 'Clavicula, acromion y espina escapular (deltoides) + fosas escapulares (manguito)',
    insertion: 'Tuberculo deltoideo y tuberculos del humero segun porcion'
  },
  brazos: {
    origin: 'Escapula y humero segun flexores/extensores del codo',
    insertion: 'Radio y olecranon/cubito segun musculo'
  },
  piernas: {
    origin: 'Pelvis, femur y tibia/perone segun subgrupo del tren inferior',
    insertion: 'Femur, tibia, perone y calcaneo segun funcion'
  },
  abdomen: {
    origin: 'Cartilagos costales, pubis y fascia toracolumbar segun porcion',
    insertion: 'Linea alba, costillas y pelvis segun subgrupo'
  },
  core: {
    origin: 'Caja toracica, pelvis y columna lumbar/toracica',
    insertion: 'Linea alba, costillas, pelvis y estructuras vertebrales'
  },
  trapecio: {
    origin: 'Occipital, ligamento nucal y apofisis espinosas cervicales/toracicas',
    insertion: 'Clavicula, acromion y espina escapular'
  },
  antebrazo: {
    origin: 'Epicondilos humerales y superficies proximales de radio/cubito',
    insertion: 'Metacarpos, falanges y radio distal segun musculo'
  },
  glúteos: {
    origin: 'Ilion, sacro y fascia toracolumbar',
    insertion: 'Femur proximal y tracto iliotibial'
  },
  pantorrillas: {
    origin: 'Condilos femorales (gastrocnemio) y tibia/perone (soleo)',
    insertion: 'Calcaneo via tendon de Aquiles'
  },
  'recto-abdominal': {
    origin: 'Cresta y sinfisis pubica',
    insertion: 'Cartilagos costales 5-7 y apofisis xifoides'
  },
  oblicuos: {
    origin: 'Costillas inferiores, fascia toracolumbar y cresta iliaca',
    insertion: 'Linea alba, pubis y costillas segun porcion'
  },
  'transverso-abdominal': {
    origin: 'Fascia toracolumbar, cresta iliaca y cartilagos costales inferiores',
    insertion: 'Linea alba y pubis'
  },
  'serrato-anterior': {
    origin: 'Costillas 1-8/9',
    insertion: 'Borde medial de la escapula (cara anterior)'
  },
  'tibial-anterior': {
    origin: 'Condilo lateral y cara lateral de la tibia',
    insertion: 'Cuneiforme medial y base del primer metatarsiano'
  },
  multífidos: {
    origin: 'Sacro y apofisis mamilares/transversas lumbares y toracicas',
    insertion: 'Apofisis espinosas de vertebras superiores'
  },
  'suelo-pélvico': {
    origin: 'Pubis, arco tendinoso y espinas isquiaticas',
    insertion: 'Cocix, rafe anococcigeo y fascia perineal'
  },
  diafragma: {
    origin: 'Apofisis xifoides, costillas inferiores y vertebras lumbares',
    insertion: 'Tendon central diafragmatico'
  },
  cuello: {
    origin: 'Esternon, clavicula y vertebras cervicales/toracicas superiores',
    insertion: 'Cranio, mandibula y vertebras cervicales'
  },

  'pectoral-medio': {
    importanceHealth:
      'Una porcion esternal fuerte mejora la transferencia de fuerza en empujes horizontales y ayuda a repartir tensiones en el hombro anterior cuando se combina con trabajo de traccion.'
  },
  'trapecio-superior': {
    description:
      'Porcion superior del trapecio, responsable de elevar y asistir la rotacion superior de la escapula durante gestos por encima de la cabeza.'
  },
  'trapecio-medio': {
    description:
      'Porcion media del trapecio especializada en retraccion escapular y estabilidad dorsal durante tirones horizontales.',
    importanceHealth:
      'Un trapecio medio funcional reduce proyeccion anterior de hombros y mejora la salud escapular en usuarios con alto volumen de empuje.'
  },
  'trapecio-inferior': {
    description:
      'Porcion inferior del trapecio implicada en depresion y rotacion superior escapular con fuerte rol de control en movimientos overhead.',
    importanceMovement:
      'Mejora la mecanica de presses verticales y tracciones al optimizar orientacion escapular en amplitudes altas.',
    importanceHealth:
      'Su fortalecimiento disminuye compensaciones cervicales y ayuda a preservar espacio subacromial en tareas por encima de la cabeza.'
  },
  romboides: {
    importanceMovement:
      'Contribuye a fijar la escapula contra la parrilla costal para permitir tirones mas eficientes y controlados.',
    importanceHealth:
      'Su activacion adecuada reduce la tendencia a hombros protraidos y mejora la higiene postural toracica.'
  },
  'deltoides-anterior': {
    description:
      'Cabeza frontal del deltoides, dominante en flexion de hombro y muy activa en presses verticales y horizontales.'
  },
  supraespinoso: {
    description:
      'Musculo del manguito rotador que inicia la abduccion del hombro y contribuye al centrado humeral durante elevaciones.',
    importanceHealth:
      'Su correcta dosificacion de carga y control escapular reduce riesgo de impingement y dolor subacromial recurrente.'
  },
  infraespinoso: {
    description:
      'Musculo posterior del manguito rotador, principal rotador externo y estabilizador del hombro en tareas de empuje/tiron.',
    importanceHealth:
      'Fortalecerlo mejora el equilibrio con rotadores internos y ayuda a prevenir inestabilidad anterior del hombro.'
  },
  bíceps: {
    importanceHealth:
      'Su desarrollo equilibrado con braquial y triceps mejora la salud del codo y la tolerancia del tendon de la cabeza larga en tirones.'
  },
  'cabeza-larga-bíceps': {
    description:
      'Porcion lateral del biceps que cruza hombro y codo; gana protagonismo en posiciones de hombro extendido como curls inclinados.',
    importanceMovement:
      'Aporta supinacion potente y contribuye al pico visual del biceps cuando se trabaja en longitudes largas.',
    importanceHealth:
      'Su control de carga protege la corredera bicipital y reduce irritacion anterior de hombro en volumen alto de tiron.',
    origin: 'Tuberculo supraglenoideo de la escapula',
    insertion: 'Tuberosidad radial y aponeurosis bicipital'
  },
  'cabeza-corta-bíceps': {
    description:
      'Porcion medial del biceps, activa en flexion de codo y supinacion con el brazo mas anterior respecto al torso.',
    importanceMovement:
      'Contribuye al grosor del brazo y a la fuerza de flexion en rangos medios con buena estabilidad escapular.',
    importanceHealth:
      'Un desarrollo equilibrado con la cabeza larga ayuda a distribuir tension en codo y hombro durante tirones.',
    origin: 'Apofisis coracoides de la escapula',
    insertion: 'Tuberosidad radial y aponeurosis bicipital'
  },
  braquial: {
    importanceMovement:
      'Aporta torque consistente de flexion de codo sin depender de la posicion de pronosupinacion del antebrazo.',
    importanceHealth:
      'Su fortalecimiento reduce sobrecarga relativa del biceps y mejora tolerancia del codo en altas repeticiones.'
  },
  braquiorradial: {
    importanceMovement:
      'Clave para fuerza de tiron con agarres neutros o semipronados, especialmente en remos y curls martillo.',
    importanceHealth:
      'Mejora estabilidad de muñeca/codo y ayuda a tolerar mejor volumen de agarre en tracciones pesadas.'
  },
  tríceps: {
    importanceHealth:
      'Un triceps fuerte y progresado de forma controlada mejora la estabilidad del codo y reduce dolor en bloqueos repetitivos.'
  },
  'cabeza-larga-tríceps': {
    importanceMovement:
      'Aumenta su participacion en extensiones por encima de la cabeza y aporta extension de hombro como sinergista.',
    importanceHealth:
      'Su entrenamiento en longitudes largas ayuda a repartir carga del codo y mejorar estabilidad posterior de hombro.'
  },
  'cabeza-lateral-tríceps': {
    description:
      'Porcion externa del triceps, visible en la forma de herradura y muy implicada en esfuerzos de extension de codo con alta intensidad.',
    importanceMovement:
      'Aporta potencia de bloqueo en presses y fondos, especialmente en los ultimos grados de extension.',
    importanceHealth:
      'Un desarrollo progresivo reduce sensibilidad del tendon del triceps ante cargas repetidas de empuje.'
  },
  'cabeza-medial-tríceps': {
    importanceMovement:
      'Contribuye de forma constante a la extension de codo en todo el recorrido, incluyendo cargas moderadas.',
    importanceHealth:
      'Proporciona soporte articular al codo y ayuda a distribuir la carga entre las tres cabezas del triceps.'
  },
  'flexores-de-antebrazo': {
    origin: 'Epicondilo medial del humero y ulna proximal',
    insertion: 'Falanges y bases metacarpianas palmares',
    importanceMovement:
      'Generan agarre y control de barra/mancuerna en tirones, carries y levantamientos prolongados.'
  },
  'extensores-de-antebrazo': {
    origin: 'Epicondilo lateral del humero',
    insertion: 'Bases metacarpianas dorsales y expansiones digitales',
    importanceMovement:
      'Contrabalancean flexores durante agarre fuerte y estabilizan muñeca en empujes y levantamientos.'
  },
  'vasto-lateral': {
    importanceMovement:
      'Aporta extension potente de rodilla en patrones dominantes de rodilla y contribuye al control lateral de la rotula.',
    importanceHealth:
      'Su fuerza adecuada mejora tolerancia femoropatelar cuando se combina con gluteo medio y vasto medial.'
  },
  'vasto-medial': {
    description:
      'Porcion medial del cuadriceps con papel relevante en extension final de rodilla y control de seguimiento rotuliano.'
  },
  'recto-femoral': {
    description:
      'Porcion biarticular del cuadriceps que participa en extension de rodilla y flexion de cadera, sensible a volumen alto de sprint/sentadilla.',
    importanceMovement:
      'Aporta fuerza en transiciones cadera-rodilla y mejora aceleracion cuando se entrena en longitudes amplias.'
  },
  'bíceps-femoral': {
    origin: 'Tuberosidad isquiatica (cabeza larga) y linea aspera femoral (cabeza corta)'
  },
  semitendinoso: {
    origin: 'Tuberosidad isquiatica'
  },
  semimembranoso: {
    origin: 'Tuberosidad isquiatica'
  },
  aductores: {
    importanceMovement:
      'Aportan estabilidad frontal y potencia en cambios de direccion, sentadilla profunda y fases de apoyo unilateral.',
    importanceHealth:
      'Su fuerza y movilidad reducen sobrecarga de pubis y ayudan a estabilizar pelvis y rodilla en deportes de campo.'
  },
  'glúteo-mayor': {
    description:
      'Musculo mas voluminoso del cuerpo y gran extensor de cadera, decisivo en potencia de bisagra, sprint y salto.',
    importanceHealth:
      'Su activacion adecuada reduce sobrecarga lumbar y mejora control pélvico en patrones de alta demanda.'
  },
  'glúteo-medio': {
    description:
      'Abductor principal de cadera con fuerte rol de estabilidad frontal durante apoyo unilateral y desplazamientos laterales.',
    importanceMovement:
      'Mantiene pelvis estable en marcha, carrera y zancadas, evitando colapso de cadera en apoyo monopodal.'
  },
  'glúteo-menor': {
    description:
      'Abductor profundo de cadera que colabora con gluteo medio en estabilidad pélvica y control de rotacion femoral.',
    importanceMovement:
      'Apoya el control de cadera en tareas unilaterales y cambios de direccion con carga.',
    importanceHealth:
      'Su funcion reduce valgo dinamico y ayuda a proteger rodilla y region lumbar en gestos repetitivos.'
  },
  gastrocnemio: {
    importanceHealth:
      'Mantener su longitud y fuerza mejora dorsiflexion funcional y disminuye carga excesiva sobre tendon de Aquiles.'
  },
  sóleo: {
    description:
      'Musculo profundo de la pantorrilla, altamente resistente, esencial para control postural y flexion plantar sostenida.',
    importanceMovement:
      'Aporta propulsion y control en apoyo con rodilla flexionada, clave en carrera y cambios de ritmo.'
  },
  'recto-abdominal': {
    description:
      'Musculo superficial del abdomen con papel en flexion de tronco y control anti-extension en tareas de carga.',
    importanceMovement:
      'Contribuye a mantener rigidez anterior del tronco durante sentadillas, carries y ejercicios por encima de la cabeza.',
    importanceHealth:
      'Su fuerza adecuada mejora control pélvico y reduce extension lumbar excesiva en actividades diarias y deportivas.'
  },
  oblicuos: {
    origin: 'Costillas 5-12, cresta iliaca y fascia toracolumbar',
    insertion: 'Linea alba, pubis y costillas inferiores',
    importanceMovement:
      'Mejoran transferencia de fuerza rotacional y anti-rotacional en gestos atleticos y patrones unilaterales.'
  },
  'transverso-abdominal': {
    description:
      'Musculo abdominal profundo con orientacion horizontal, principal responsable de compresion del cilindro abdominal.',
    importanceHealth:
      'Su activacion coordinada con diafragma y suelo pélvico es clave para estabilidad lumbar y control de carga.'
  },
  'serrato-anterior': {
    origin: 'Costillas 1-8/9 en su cara lateral',
    insertion: 'Borde medial anterior de la escapula',
    importanceMovement:
      'Facilita protraccion y rotacion superior escapular, esenciales para presses y movimientos overhead eficientes.',
    importanceHealth:
      'Su fortalecimiento reduce escapula alada y mejora estabilidad subacromial en elevaciones repetidas.'
  },
  'tibial-anterior': {
    description:
      'Dorsiflexor principal del tobillo y colaborador en inversion, esencial para despeje del pie durante la fase de oscilacion.',
    importanceMovement:
      'Permite contacto de talon controlado y mejora mecanica de carrera, marcha y frenado anterior del pie.',
    importanceHealth:
      'Su fuerza adecuada reduce riesgo de tropiezos y ayuda a equilibrar cargas con el complejo aquileo-sural.'
  },
  cuello: {
    origin: 'Esternon, clavicula y procesos vertebrales cervicales/toracicos altos',
    insertion: 'Occipital, mastoides, mandibula y vertebras cervicales',
    importanceMovement:
      'Aporta control cefalico en aceleraciones, contacto y levantamientos, mejorando estabilidad global del eje cervical.'
  }
}

const byId = Object.fromEntries(muscles.map((m) => [m.id, m]))
let applied = 0
for (const [id, patch] of Object.entries(updates)) {
  if (byId[id]) {
    Object.assign(byId[id], patch)
    applied += 1
  }
}

fs.writeFileSync(filePath, JSON.stringify(muscles, null, 2) + '\n', 'utf8')
console.log('style polish applied to muscles:', applied)
