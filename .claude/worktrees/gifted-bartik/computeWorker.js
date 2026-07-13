"use strict";
(() => {
  // data/exerciseDatabaseCentral.ts
  function parseMuscle(m) {
    const idx = m.indexOf(":");
    if (idx === -1) return { muscle: m };
    return { muscle: m.substring(0, idx), emphasis: m.substring(idx + 1) };
  }
  function mk(id, name, desc, equipment, type, category, force, efc, cnc, ssc, ttc, primary, secondary, stabilizer, subMuscleGroup, bodyPart = "lower") {
    const involvedMuscles = [
      ...primary.map((m) => {
        const p = parseMuscle(m);
        return { muscle: p.muscle, role: "primary", activation: 1, ...p.emphasis ? { emphasis: p.emphasis } : {} };
      }),
      ...secondary.map((m) => {
        const p = parseMuscle(m);
        return { muscle: p.muscle, role: "secondary", activation: 0.5, ...p.emphasis ? { emphasis: p.emphasis } : {} };
      }),
      ...stabilizer.map((m) => {
        const p = parseMuscle(m);
        return { muscle: p.muscle, role: "stabilizer", activation: 0.4, ...p.emphasis ? { emphasis: p.emphasis } : {} };
      })
    ];
    const chain = bodyPart === "upper" ? force === "Tir\xF3n" ? "posterior" : force === "Empuje" ? "anterior" : "full" : force === "Bisagra" ? "posterior" : force === "Sentadilla" || force === "Extensi\xF3n" ? "anterior" : "full";
    return {
      id,
      name,
      description: desc,
      involvedMuscles,
      subMuscleGroup: subMuscleGroup ?? parseMuscle(primary[0]).muscle,
      category,
      type,
      equipment,
      force,
      bodyPart,
      chain,
      efc,
      cnc,
      ssc,
      ttc
    };
  }
  var LOWER_BODY_EXERCISES = [
    // ========== SENTADILLA ==========
    mk(
      "tren_inferior_sentadilla_barra_alta",
      "Sentadilla Trasera Barra Alta",
      "Sentadilla con barra en posici\xF3n alta sobre trapecios. Permite torso m\xE1s erguido y menor inclinaci\xF3n que la barra baja. \xC9nfasis en cu\xE1driceps con participaci\xF3n de gl\xFAteos e isquios.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Sentadilla",
      4.5,
      4.5,
      1.5,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Isquiosurales", "Aductores"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_sentadilla_barra_baja",
      "Sentadilla Trasera Barra Baja",
      "Sentadilla con barra en posici\xF3n baja (powerlifting). Mayor inclinaci\xF3n del torso y mayor demanda de cadera y espalda. Excelente para levantar cargas m\xE1ximas.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Sentadilla",
      4.8,
      5,
      1.8,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor", "Isquiosurales"],
      ["Aductores"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_sentadilla_frontal",
      "Sentadilla Frontal con Barra",
      "Barra sobre hombros delante. Torso muy erguido, m\xE1ximo \xE9nfasis en cu\xE1driceps. Requiere buena movilidad de tobillo y hombros.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Sentadilla",
      4.2,
      4.5,
      1.2,
      2.4,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Core", "Erectores Espinales", "Trapecio"]
    ),
    mk(
      "tren_inferior_sentadilla_goblet_mancuerna",
      "Sentadilla Goblet",
      "Mancuerna sostenida en el pecho. Ideal para aprender el patr\xF3n de sentadilla y desarrollar movilidad. Excelente para principiantes.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3,
      2.5,
      0.5,
      2,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Core"]
    ),
    mk(
      "tren_inferior_sentadilla_goblet_kettlebell",
      "Sentadilla Goblet con Kettlebell",
      "Kettlebell sostenido en posici\xF3n goblet. Similar a la versi\xF3n con mancuerna, \xFAtil para centros de fitness sin barras.",
      "Kettlebell",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3,
      2.5,
      0.5,
      2,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Core"]
    ),
    mk(
      "tren_inferior_sentadilla_zercher",
      "Sentadilla Zercher con Barra",
      "Barra en el hueco del codo. Exige gran estabilidad de core y transferencia de fuerza. Popular en strongman.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Sentadilla",
      4.6,
      4.8,
      1.9,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_sentadilla_safety_squat_bar",
      "Sentadilla con Safety Squat Bar",
      "Barra con agarres frontales que reduce estr\xE9s en hombros. Permite torso m\xE1s vertical. Muy usada en powerlifting para variar el est\xEDmulo.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Sentadilla",
      4.3,
      4,
      1.4,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_sentadilla_cambered_bar",
      "Sentadilla con Cambered Bar",
      "Barra con curvatura que desplaza el centro de gravedad. Aumenta el rango de movimiento y la demanda en cu\xE1driceps.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Sentadilla",
      4.6,
      4.5,
      1.6,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_sentadilla_jefferson",
      "Sentadilla Asim\xE9trica Jefferson",
      "Piernas a cada lado de la barra, agarre entre las piernas. Desaf\xEDa la estabilidad y trabaja ambas piernas de forma asim\xE9trica.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      4,
      4.5,
      1.6,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Aductores"],
      ["Core", "Erectores Espinales"]
    ),
    mk(
      "tren_inferior_sentadilla_cajon_barra",
      "Sentadilla al Caj\xF3n con Barra",
      "Sentadilla hasta tocar un caj\xF3n. Controla la profundidad y reduce la demanda exc\xE9ntrica. \xDAtil para aprender la t\xE9cnica.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Sentadilla",
      4.2,
      4.5,
      1.5,
      1.5,
      ["Gl\xFAteos:mayor", "Cu\xE1driceps"],
      ["Isquiosurales", "Aductores"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_sentadilla_landmine",
      "Sentadilla Landmine",
      "Barra anclada en esquina o landmine. Permite vector de fuerza diagonal. Menor carga axial que la sentadilla libre.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3,
      2.5,
      0.6,
      2,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Core"]
    ),
    mk(
      "tren_inferior_sentadilla_polea_baja",
      "Sentadilla en Polea Baja",
      "Tensi\xF3n constante desde polea baja. Menor carga axial, ideal para rehabilitaci\xF3n o variaci\xF3n de volumen.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      2.5,
      1.8,
      0.2,
      1.4,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Core"]
    ),
    mk(
      "tren_inferior_sentadilla_saco_arena",
      "Sentadilla con Saco de Arena",
      "Saco de arena sobre hombros o en posici\xF3n Zercher. Carga inestable que exige mayor estabilizaci\xF3n. Com\xFAn en entrenamiento funcional.",
      "Saco de arena",
      "Accesorio",
      "Resistencia",
      "Sentadilla",
      3.5,
      3,
      1,
      2,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_sentadilla_isometrica_pared",
      "Sentadilla Isom\xE9trica en Pared",
      "Espalda contra la pared, mantener posici\xF3n de sentadilla. Excelente para resistencia isom\xE9trica de cu\xE1driceps y trabajo de \xE1ngulo fijo.",
      "Peso Corporal",
      "Accesorio",
      "Resistencia",
      "Sentadilla",
      2,
      1.5,
      0.1,
      0.5,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      []
    ),
    mk(
      "tren_inferior_sentadilla_bandas",
      "Sentadilla con Bandas de Resistencia",
      "Bandas el\xE1sticas a\xF1aden resistencia al movimiento. Tensi\xF3n creciente en la extensi\xF3n. Ideal para calentamiento o circuito.",
      "Banda",
      "Accesorio",
      "Resistencia",
      "Sentadilla",
      2.5,
      2,
      0.5,
      1.8,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Core"]
    ),
    mk(
      "tren_inferior_sentadilla_smith",
      "Sentadilla en M\xE1quina Smith",
      "Barra guiada en trayectoria fija. Elimina demandas de estabilidad lateral. Permite enfocarse en la contracci\xF3n muscular.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.8,
      3,
      1.2,
      1.6,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      []
    ),
    mk(
      "tren_inferior_sentadilla_hack_maquina",
      "Sentadilla Hack en M\xE1quina",
      "Cuerpo inclinado hacia atr\xE1s, carga sobre hombros. A\xEDsla cu\xE1driceps con m\xEDnima participaci\xF3n de espalda.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.5,
      3,
      0.4,
      1.6,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      []
    ),
    mk(
      "tren_inferior_sentadilla_hack_barra",
      "Sentadilla Hack con Barra Libre",
      "Barra detr\xE1s de las piernas, estilo Hack cl\xE1sico. Desarrollo de cu\xE1driceps con participaci\xF3n de trapecio y antebrazo.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Sentadilla",
      4,
      3.8,
      1,
      2.4,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Trapecio", "Antebrazo"]
    ),
    mk(
      "tren_inferior_sentadilla_pendulo",
      "Sentadilla de P\xE9ndulo en M\xE1quina",
      "M\xE1quina pendular que altera la curva de resistencia. Permite m\xE1xima flexi\xF3n de rodilla con carga. Muy usada en culturismo.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.8,
      3.2,
      0.3,
      1.6,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      []
    ),
    mk(
      "tren_inferior_sentadilla_belt_squat",
      "Sentadilla Belt Squat",
      "Carga mediante cintur\xF3n en cadera. Cero compresi\xF3n vertebral. Ideal para trabajo de volumen sin fatiga espinal.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.5,
      2.5,
      0,
      1.6,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      []
    ),
    mk(
      "tren_inferior_prensa_45",
      "Prensa de Piernas a 45 Grados",
      "M\xE1quina de prensa inclinada. Mueve grandes cargas sin fatiga axial. Excelente para hipertrofia de cu\xE1driceps y gl\xFAteos.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.2,
      2.5,
      0.3,
      1.6,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Aductores"],
      []
    ),
    mk(
      "tren_inferior_prensa_horizontal",
      "Prensa de Piernas Horizontal",
      "Prensa con plataforma horizontal. Menor inclinaci\xF3n reduce participaci\xF3n de gl\xFAteos. Enfasis en cu\xE1driceps.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      2.8,
      2,
      0.1,
      1.6,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      []
    ),
    mk(
      "tren_inferior_prensa_vertical",
      "Prensa de Piernas Vertical",
      "Prensa vertical tipo V-Squat. Mayor rango de flexi\xF3n de rodilla. Participaci\xF3n significativa de isquiosurales.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3,
      2.5,
      0.2,
      1.6,
      ["Cu\xE1driceps", "Isquiosurales"],
      ["Gl\xFAteos:mayor"],
      []
    ),
    // ========== SENTADILLA UNILATERAL ==========
    mk(
      "tren_inferior_bulgara_mancuernas",
      "Sentadilla B\xFAlgara con Mancuernas",
      "Pie trasero elevado, descender en zancada. Excelente para cu\xE1driceps, gl\xFAteos y correcci\xF3n de asimetr\xEDas.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.8,
      3.5,
      0.8,
      2.2,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio", "Aductores"],
      ["Antebrazo"]
    ),
    mk(
      "tren_inferior_bulgara_barra",
      "Sentadilla B\xFAlgara con Barra",
      "B\xFAlgaro con barra. Mayor carga posible y demanda de estabilidad. Ideal para desarrollo de fuerza unilateral.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Sentadilla",
      4.2,
      4.2,
      1.2,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio", "Aductores"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_bulgara_smith",
      "Sentadilla B\xFAlgara en M\xE1quina Smith",
      "B\xFAlgaro en Smith. Elimina inestabilidad lateral. Permite concentrarse en la contracci\xF3n.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.5,
      2.8,
      0.6,
      1.6,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      []
    ),
    mk(
      "tren_inferior_bulgara_polea",
      "Sentadilla B\xFAlgara en Polea",
      "B\xFAlgaro con polea baja. Tensi\xF3n constante durante el movimiento. \xDAtil para bombeo y tiempo bajo tensi\xF3n.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.2,
      2.5,
      0.4,
      1.6,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio", "Antebrazo"]
    ),
    mk(
      "tren_inferior_split_estatico_barra",
      "Sentadilla Split Est\xE1tica con Barra",
      "Zancada fija con barra. Mayor estabilidad que b\xFAlgaro. Desarrollo de fuerza y control unilateral.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Sentadilla",
      3.8,
      3.8,
      1.2,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio", "Aductores"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_split_mancuernas",
      "Sentadilla Split con Mancuernas",
      "Zancada fija con mancuernas. Mayor libertad de movimiento que con barra. Ideal para hipertrofia unilateral.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.5,
      3,
      0.8,
      2.2,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio", "Aductores"],
      ["Antebrazo"]
    ),
    mk(
      "tren_inferior_zancada_frontal_barra",
      "Zancada Frontal con Barra",
      "Zancada hacia adelante con barra. \xC9nfasis en cu\xE1driceps de la pierna delantera. Requiere espacio y control.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Sentadilla",
      3.8,
      4,
      1,
      2.4,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_zancada_inversa_barra",
      "Zancada Inversa con Barra",
      "Zancada hacia atr\xE1s con barra. Menor estr\xE9s en rodilla que frontal. Buen trabajo de gl\xFAteo y cu\xE1driceps.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Sentadilla",
      3.8,
      3.8,
      1.2,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio", "Aductores"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_zancada_inversa_mancuernas",
      "Zancada Inversa con Mancuernas",
      "Zancada hacia atr\xE1s con mancuernas. Versi\xF3n m\xE1s accesible para aprender el patr\xF3n. \xC9nfasis en gl\xFAteo mayor.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.4,
      3,
      0.5,
      2.2,
      ["Gl\xFAteos:mayor", "Cu\xE1driceps"],
      ["Gl\xFAteos:medio", "Aductores"],
      ["Antebrazo"]
    ),
    mk(
      "tren_inferior_zancada_caminando_mancuernas",
      "Zancada Caminando con Mancuernas",
      "Zancadas sucesivas avanzando. Acondicionamiento y trabajo unilateral. Mayor demanda coordinativa.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.6,
      3.5,
      0.6,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Antebrazo"]
    ),
    mk(
      "tren_inferior_zancada_lateral_mancuerna",
      "Zancada Lateral con Mancuerna",
      "Zancada hacia el lado. Trabaja aductores, gl\xFAteo medio y cu\xE1driceps. \xDAtil para movilidad de cadera.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3,
      3,
      0.4,
      2.2,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Aductores", "Gl\xFAteos:medio"],
      ["Antebrazo"]
    ),
    mk(
      "tren_inferior_zancada_cruzada_mancuernas",
      "Zancada Cruzada con Mancuernas",
      "Zancada diagonal hacia atr\xE1s cruzando la l\xEDnea del cuerpo. \xC9nfasis en gl\xFAteo mayor y medio.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.2,
      3.2,
      0.5,
      2.2,
      ["Gl\xFAteos:mayor", "Cu\xE1driceps"],
      ["Gl\xFAteos:medio"],
      ["Antebrazo"]
    ),
    mk(
      "tren_inferior_zancada_smith",
      "Zancada en M\xE1quina Smith",
      "Zancada en Smith. Trayectoria guiada. Permite cargas altas con menor demanda de equilibrio.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3.5,
      3,
      0.6,
      1.6,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      []
    ),
    mk(
      "tren_inferior_zancada_trx",
      "Zancada en Sistema de Suspensi\xF3n",
      "Zancada con pie trasero en TRX. Mayor inestabilidad y demanda de core. Enfasis en estabilidad.",
      "TRX",
      "Accesorio",
      "Estabilidad",
      "Sentadilla",
      2.8,
      2.5,
      0.2,
      2,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Core"]
    ),
    mk(
      "tren_inferior_subida_cajon_mancuernas",
      "Subida al Caj\xF3n con Mancuernas",
      "Subir al caj\xF3n con una pierna. Trabajo unilateral de cu\xE1driceps, gl\xFAteos y gemelos. Ideal para potencia y control.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3,
      3,
      0.4,
      2.2,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio", "Pantorrillas:gastrocnemio"],
      ["Antebrazo"]
    ),
    mk(
      "tren_inferior_subida_cajon_barra",
      "Subida al Caj\xF3n con Barra",
      "Subir al caj\xF3n con barra. Mayor carga posible. Excelente para desarrollo de fuerza unilateral de pierna.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Sentadilla",
      3.5,
      3.8,
      0.8,
      2.4,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_prensa_unilateral",
      "Prensa de Piernas Unilateral",
      "Prensa a una pierna. Corrige asimetr\xEDas y permite rango completo por pierna. Seguro y efectivo.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Sentadilla",
      3,
      2.2,
      0.2,
      1.6,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      []
    ),
    mk(
      "tren_inferior_sentadilla_pistol",
      "Sentadilla Pistol",
      "Sentadilla a una pierna con la otra extendida. M\xE1xima demanda de movilidad, equilibrio y fuerza unilateral.",
      "Peso Corporal",
      "Accesorio",
      "Movilidad",
      "Sentadilla",
      3.5,
      4,
      0.2,
      2.5,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Core"],
      ["Core", "Gl\xFAteos:medio", "Pantorrillas:gastrocnemio"]
    ),
    mk(
      "tren_inferior_sentadilla_pistol_kettlebell",
      "Sentadilla Pistol con Kettlebell",
      "Pistol con kettlebell como contrapeso. El peso adelante ayuda a mantener el equilibrio. Mayor carga que peso corporal.",
      "Kettlebell",
      "Accesorio",
      "Fuerza",
      "Sentadilla",
      3.8,
      4.2,
      0.3,
      2.8,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Core"],
      ["Core", "Gl\xFAteos:medio", "Pantorrillas:gastrocnemio", "Antebrazo"]
    ),
    mk(
      "tren_inferior_sentadilla_patinador",
      "Sentadilla de Patinador",
      "Deslizamiento lateral tipo patinador. Trabajo de cu\xE1driceps, gl\xFAteos e isquios con componente din\xE1mico.",
      "Peso Corporal",
      "Accesorio",
      "Movilidad",
      "Sentadilla",
      3.2,
      3.8,
      0.2,
      2,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Isquiosurales", "Core"]
    ),
    // ========== BISAGRA DE CADERA ==========
    mk(
      "tren_inferior_peso_muerto_convencional",
      "Peso Muerto Convencional",
      "El rey de la cadena posterior. Levantar barra del suelo con piernas y brazos extendidos. Desarrolla gl\xFAteos, isquios y espalda.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Bisagra",
      5,
      5,
      2,
      2.4,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Cu\xE1driceps", "Dorsales"],
      ["Erectores Espinales", "Trapecio", "Antebrazo", "Core"]
    ),
    mk(
      "tren_inferior_peso_muerto_sumo",
      "Peso Muerto Sumo",
      "Pies muy anchos, manos dentro de las piernas. Mayor participaci\xF3n de aductores y cu\xE1driceps. Menor rango en espalda baja.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Bisagra",
      4.8,
      4.8,
      1.6,
      2.4,
      ["Gl\xFAteos:mayor", "Aductores"],
      ["Cu\xE1driceps", "Isquiosurales"],
      ["Erectores Espinales", "Trapecio", "Antebrazo", "Core"]
    ),
    mk(
      "tren_inferior_peso_muerto_rumano",
      "Peso Muerto Rumano",
      "Desde arriba, bajar hasta estirar isquios. \xC9nfasis en hipertrofia de isquios y gl\xFAteos. Alto estr\xE9s tendinoso (TTC).",
      "Barra",
      "B\xE1sico",
      "Hipertrofia",
      "Bisagra",
      4.2,
      4,
      1.8,
      4.3,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      ["Dorsales"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_peso_muerto_rumano_mancuernas",
      "Peso Muerto Rumano con Mancuernas",
      "RDL con mancuernas. Mayor rango de movimiento y libertad. Menor carga axial que con barra.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      3.8,
      3.5,
      1.2,
      3.2,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      [],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_peso_muerto_piernas_rigidas",
      "Peso Muerto Piernas R\xEDgidas",
      "Piernas casi rectas. M\xE1ximo estiramiento de isquios. Alto TTC. Requiere buena flexibilidad.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Bisagra",
      4.3,
      4,
      1.9,
      4.5,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      [],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_peso_muerto_barra_hexagonal",
      "Peso Muerto Barra Hexagonal",
      "Barra hexagonal (trap bar). H\xEDbrido entre sentadilla y peso muerto. Menor estr\xE9s lumbar, mayor participaci\xF3n de cu\xE1driceps.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Bisagra",
      4.5,
      4,
      1.4,
      2.4,
      ["Gl\xFAteos:mayor", "Cu\xE1driceps"],
      ["Isquiosurales"],
      ["Trapecio", "Erectores Espinales", "Antebrazo", "Core"]
    ),
    mk(
      "tren_inferior_peso_muerto_deficit",
      "Peso Muerto con D\xE9ficit",
      "Parado sobre plataforma. Aumenta rango de movimiento y dificultad en el despegue. Excelente para fuerza de arranque.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Bisagra",
      5,
      5,
      2,
      2.5,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Cu\xE1driceps"],
      ["Erectores Espinales", "Trapecio", "Antebrazo", "Core"]
    ),
    mk(
      "tren_inferior_rack_pull",
      "Rack Pull Desde Bloques",
      "Peso muerto parcial desde altura de rodillas. Sobrecarga bloqueo final. Ideal para espalda alta y trapecios.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Bisagra",
      4.5,
      4.5,
      2,
      2.4,
      ["Gl\xFAteos:mayor", "Trapecio"],
      ["Isquiosurales", "Dorsales"],
      ["Erectores Espinales", "Antebrazo"]
    ),
    mk(
      "tren_inferior_peso_muerto_kettlebell",
      "Peso Muerto con Kettlebell",
      "Peso muerto con kettlebell. \xDAtil para principiantes o como variaci\xF3n. Permite agarre neutro c\xF3modo.",
      "Kettlebell",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      3.5,
      3,
      1,
      2.2,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Cu\xE1driceps"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_peso_muerto_polea",
      "Peso Muerto en Polea Baja",
      "RDL o peso muerto con polea baja. Tensi\xF3n constante. Menor carga axial. Bueno para volumen de isquios.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      2.8,
      2.2,
      0.4,
      1.8,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      [],
      ["Erectores Espinales", "Antebrazo", "Core"]
    ),
    mk(
      "tren_inferior_peso_muerto_saco_arena",
      "Peso Muerto con Saco de Arena",
      "Peso muerto con saco. Carga inestable exige mayor estabilizaci\xF3n. Com\xFAn en entrenamiento funcional.",
      "Saco de arena",
      "Accesorio",
      "Resistencia",
      "Bisagra",
      3.5,
      3,
      1,
      2.2,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Cu\xE1driceps"],
      ["Erectores Espinales", "Antebrazo", "Core"]
    ),
    mk(
      "tren_inferior_buenos_dias_pie",
      "Buenos D\xEDas de Pie con Barra",
      "Barra en espalda, flexi\xF3n de cadera manteniendo piernas rectas. Excelente para isquios y espalda baja. Alto TTC.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Bisagra",
      4,
      3.8,
      2,
      4.3,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      [],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_buenos_dias_sentado",
      "Buenos D\xEDas Sentado con Barra",
      "Buenos d\xEDas en posici\xF3n sentada. A\xEDsla m\xE1s los erectores y reduce participaci\xF3n de isquios en el estiramiento.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      3.5,
      3,
      1.8,
      3.5,
      ["Erectores Espinales", "Gl\xFAteos:mayor"],
      ["Aductores"],
      ["Core"]
    ),
    mk(
      "tren_inferior_buenos_dias_safety_bar",
      "Buenos D\xEDas con Safety Squat Bar",
      "Buenos d\xEDas con SSB. La barra sujeta los hombros. Posici\xF3n m\xE1s c\xF3moda para algunos atletas.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Bisagra",
      4.1,
      3.5,
      1.9,
      4.3,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      [],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_buenos_dias_banda",
      "Buenos D\xEDas con Banda de Resistencia",
      "Buenos d\xEDas con banda. Resistencia variable. \xDAtil para calentamiento o circuito de resistencia.",
      "Banda",
      "Accesorio",
      "Resistencia",
      "Bisagra",
      2.5,
      2,
      0.5,
      1.8,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      [],
      ["Erectores Espinales"]
    ),
    mk(
      "tren_inferior_pull_through",
      "Pull-Through en Polea Baja",
      "Pasar cable entre las piernas con bisagra de cadera. Ense\xF1a la mec\xE1nica de extensi\xF3n de cadera. Tensi\xF3n constante en gl\xFAteos.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      2.5,
      2,
      0.3,
      1.6,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      [],
      ["Core"]
    ),
    mk(
      "tren_inferior_hiperextension_45",
      "Hiperextensi\xF3n Banco 45 Grados",
      "En banco inclinado 45\xB0. Extensi\xF3n de espalda enfocada en erectores, gl\xFAteos e isquios. Excelente accesorio.",
      "Peso Corporal",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      2.5,
      2,
      0.6,
      1.8,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      ["Erectores Espinales"],
      []
    ),
    mk(
      "tren_inferior_hiperextension_silla_romana",
      "Hiperextensi\xF3n en Silla Romana",
      "En m\xE1quina de hiperextensi\xF3n. Mayor rango que banco 45\xB0. Alto TTC en erectores. Usado en powerlifting.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      3.2,
      2.8,
      1.2,
      3.6,
      ["Erectores Espinales"],
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      []
    ),
    mk(
      "tren_inferior_reverse_hyper",
      "Reverse Hyper en M\xE1quina",
      "Extensi\xF3n de cadera con piernas. Descomprime la columna lumbar (SSC negativo). Santo grial de la cadena posterior.",
      "M\xE1quina",
      "Accesorio",
      "Movilidad",
      "Bisagra",
      2.8,
      2,
      -0.5,
      1.6,
      ["Gl\xFAteos:mayor", "Erectores Espinales"],
      ["Isquiosurales"],
      ["Core", "Antebrazo"]
    ),
    // ========== BISAGRA UNILATERAL ==========
    mk(
      "tren_inferior_rdl_1p_mancuerna",
      "Peso Muerto Rumano 1 Pierna con Mancuerna",
      "RDL a una pierna. M\xE1xima demanda de equilibrio y estabilidad. Excelente para gl\xFAteo medio y prevenci\xF3n de lesiones.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      3.5,
      3.8,
      0.8,
      3.2,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Erectores Espinales", "Antebrazo"]
    ),
    mk(
      "tren_inferior_rdl_1p_kettlebell",
      "Peso Muerto Rumano 1 Pierna con Kettlebell",
      "RDL unilateral con kettlebell. El mango permite mejor equilibrio. Similar beneficios al RDL 1 pierna con mancuerna.",
      "Kettlebell",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      3.5,
      3.8,
      0.8,
      3.2,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_rdl_1p_barra",
      "Peso Muerto Rumano 1 Pierna con Barra",
      "RDL unilateral con barra. Mayor carga posible. Requiere excelente equilibrio y control.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Bisagra",
      4.2,
      4.2,
      1.2,
      3.5,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Erectores Espinales", "Antebrazo"]
    ),
    mk(
      "tren_inferior_rdl_1p_polea",
      "Peso Muerto Rumano 1 Pierna en Polea",
      "RDL unilateral con polea. Tensi\xF3n constante. Permite variar el \xE1ngulo de tracci\xF3n.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      2.8,
      2.5,
      0.4,
      2.8,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Gl\xFAteos:medio"],
      ["Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_rdl_b_stance_barra",
      "Peso Muerto B-Stance con Barra",
      "RDL con postura B-stance (pie trasero ligeramente atr\xE1s y apoyado). Mayor carga que RDL 1 pierna puro. Alto TTC.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      4,
      4,
      1.5,
      4.3,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_rdl_b_stance_mancuernas",
      "Peso Muerto B-Stance con Mancuernas",
      "B-stance RDL con mancuernas. Menor carga axial. Bueno para volumen de isquios y gl\xFAteos.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Bisagra",
      3.6,
      3.5,
      1,
      3.2,
      ["Isquiosurales", "Gl\xFAteos:mayor"],
      ["Gl\xFAteos:medio"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_peso_muerto_maleta",
      "Peso Muerto Maleta a 1 Mano",
      "Peso muerto con carga solo en un lado. Desaf\xEDa el anti-lateral flexion del core. Fuerza de agarre y oblicuos.",
      "Mancuerna",
      "Accesorio",
      "Fuerza",
      "Bisagra",
      3.8,
      3.8,
      1.2,
      2.2,
      ["Gl\xFAteos:mayor", "Cu\xE1driceps"],
      ["Isquiosurales"],
      ["Core", "Antebrazo"]
    ),
    // ========== EMPUJE DE CADERA ==========
    mk(
      "tren_inferior_hip_thrust_barra",
      "Hip Thrust con Barra Libre",
      "Espalda alta en banco, extender cadera contra la barra. Rey del gl\xFAteo. M\xE1xima activaci\xF3n de gl\xFAteo mayor.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      3.8,
      3.5,
      0.5,
      2.4,
      ["Gl\xFAteos:mayor"],
      ["Isquiosurales", "Cu\xE1driceps"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_hip_thrust_maquina",
      "Hip Thrust en M\xE1quina",
      "Hip thrust en m\xE1quina dedicada. Menor setup. Enfasis en gl\xFAteo e isquios sin demanda axial.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.5,
      0.2,
      1.6,
      ["Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      []
    ),
    mk(
      "tren_inferior_hip_thrust_smith",
      "Hip Thrust en M\xE1quina Smith",
      "Hip thrust en Smith. Barra guiada. Permite concentrarse en la contracci\xF3n del gl\xFAteo.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.4,
      2.8,
      0.3,
      1.6,
      ["Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      []
    ),
    mk(
      "tren_inferior_hip_thrust_unilateral_peso",
      "Hip Thrust Unilateral con Peso Corporal",
      "Hip thrust a una pierna con peso corporal. Excelente para activaci\xF3n y correcci\xF3n de asimetr\xEDas.",
      "Peso Corporal",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3,
      3,
      0.1,
      2,
      ["Gl\xFAteos:mayor"],
      ["Isquiosurales", "Gl\xFAteos:medio"],
      ["Core"]
    ),
    mk(
      "tren_inferior_hip_thrust_banda",
      "Hip Thrust con Banda de Resistencia",
      "Hip thrust con banda. Resistencia variable. \xDAtil para activaci\xF3n, calentamiento o circuito.",
      "Banda",
      "Accesorio",
      "Resistencia",
      "Empuje",
      2.5,
      2,
      0.1,
      1.8,
      ["Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      ["Core"]
    ),
    mk(
      "tren_inferior_puente_gluteos_barra",
      "Puente de Gl\xFAteos en Suelo con Barra",
      "Puente de cadera con barra sobre la pelvis. Menor rango que hip thrust. Buen accesorio para gl\xFAteos.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3,
      2.5,
      0.2,
      2.4,
      ["Gl\xFAteos:mayor"],
      ["Isquiosurales"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_frog_pump",
      "Puente de Gl\xFAteos Frog Pump",
      "Puente con rodillas abiertas y plantas juntas. A\xEDsla gl\xFAteo mayor con \xE9nfasis en porci\xF3n inferior. Sin carga axial.",
      "Peso Corporal",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2,
      1.5,
      0,
      1,
      ["Gl\xFAteos:mayor"],
      ["Aductores"],
      []
    ),
    mk(
      "tren_inferior_elevacion_cadera_ghd",
      "Elevaci\xF3n de Cadera en M\xE1quina GHD",
      "Extensi\xF3n de cadera en Glute Ham Developer. Combina hip thrust con estiramiento de isquios. Excelente para cadena posterior.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.5,
      3,
      0.4,
      2,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      [],
      ["Erectores Espinales"]
    ),
    // ========== TRIPLE EXTENSIÓN ==========
    mk(
      "tren_inferior_cargada_potencia",
      "Cargada de Potencia",
      "Power clean. Triple extensi\xF3n de cadera, rodilla y tobillo. Desarrolla potencia y coordinaci\xF3n. Alto TTC.",
      "Barra",
      "B\xE1sico",
      "Potencia",
      "Salto",
      4.8,
      5,
      1.8,
      5,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Cu\xE1driceps", "Trapecio", "Pantorrillas:gastrocnemio", "Pantorrillas:s\xF3leo"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_cargada_colgante",
      "Cargada Colgante",
      "Power clean desde altura de rodillas. Enfasis en segunda tracci\xF3n. Menor demanda t\xE9cnica que cargada desde suelo.",
      "Barra",
      "B\xE1sico",
      "Potencia",
      "Salto",
      4.5,
      4.8,
      1.5,
      5,
      ["Gl\xFAteos:mayor", "Trapecio"],
      ["Isquiosurales", "Cu\xE1driceps", "Pantorrillas:gastrocnemio", "Pantorrillas:s\xF3leo"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_arrancada_potencia",
      "Arrancada de Potencia",
      "Power snatch. Arrancada recibida en sentadilla alta. M\xE1xima demanda de potencia y movilidad.",
      "Barra",
      "B\xE1sico",
      "Potencia",
      "Salto",
      4.8,
      5,
      1.6,
      5,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Cu\xE1driceps", "Deltoides", "Pantorrillas:gastrocnemio", "Pantorrillas:s\xF3leo"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_arrancada_kettlebell",
      "Arrancada con Kettlebell",
      "Arrancada unilateral con kettlebell. Desarrolla potencia y coordinaci\xF3n. Menor carga que con barra.",
      "Kettlebell",
      "Accesorio",
      "Potencia",
      "Salto",
      3.8,
      4.2,
      1,
      4.5,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      ["Deltoides", "Pantorrillas:gastrocnemio"],
      ["Erectores Espinales", "Core", "Antebrazo"]
    ),
    mk(
      "tren_inferior_swing_2_manos",
      "Swing con Kettlebell a 2 Manos",
      "Balanceo bal\xEDstico de cadera. Hinge y explosi\xF3n. Excelente para potencia, cardio y ense\xF1anza del patr\xF3n de bisagra.",
      "Kettlebell",
      "Accesorio",
      "Potencia",
      "Salto",
      3.5,
      3.5,
      0.8,
      3.5,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      [],
      ["Erectores Espinales", "Core", "Dorsales", "Antebrazo"]
    ),
    mk(
      "tren_inferior_swing_1_mano",
      "Swing con Kettlebell a 1 Mano",
      "Swing unilateral. A\xF1ade demanda anti-rotaci\xF3n en core. Mayor coordinaci\xF3n que swing a dos manos.",
      "Kettlebell",
      "Accesorio",
      "Potencia",
      "Salto",
      3.8,
      3.8,
      1,
      3.8,
      ["Gl\xFAteos:mayor", "Isquiosurales"],
      [],
      ["Core", "Erectores Espinales", "Antebrazo"]
    ),
    mk(
      "tren_inferior_salto_cajon",
      "Salto al Caj\xF3n",
      "Salto vertical sobre caj\xF3n. Desarrollo de potencia de piernas. Bajo impacto en aterrizaje.",
      "Peso Corporal",
      "Accesorio",
      "Potencia",
      "Salto",
      3,
      4,
      0.1,
      4.5,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Isquiosurales", "Pantorrillas:gastrocnemio", "Pantorrillas:s\xF3leo"],
      ["Core"]
    ),
    mk(
      "tren_inferior_salto_longitud",
      "Salto de Longitud",
      "Salto horizontal. Potencia en plano sagital. Mayor TTC que salto al caj\xF3n.",
      "Peso Corporal",
      "Accesorio",
      "Potencia",
      "Salto",
      3.2,
      4,
      0.2,
      5,
      ["Gl\xFAteos:mayor", "Cu\xE1driceps"],
      ["Isquiosurales", "Pantorrillas:gastrocnemio", "Pantorrillas:s\xF3leo"],
      ["Core"]
    ),
    mk(
      "tren_inferior_salto_caida",
      "Salto de Ca\xEDda",
      "Drop jump. Salto desde elevaci\xF3n. Ciclo estiramiento-acortamiento. Alto TTC y demanda neural.",
      "Peso Corporal",
      "Accesorio",
      "Potencia",
      "Salto",
      4,
      4.5,
      0.8,
      5,
      ["Cu\xE1driceps"],
      ["Gl\xFAteos:mayor", "Pantorrillas:gastrocnemio", "Pantorrillas:s\xF3leo"],
      ["Core"]
    ),
    mk(
      "tren_inferior_empuje_trineo",
      "Empuje de Trineo",
      "Empujar trineo o prowler. Potencia y acondicionamiento. Trabajo de triple extensi\xF3n en cadena cin\xE9tica cerrada.",
      "Trineo",
      "B\xE1sico",
      "Potencia",
      "Empuje",
      4.5,
      4,
      0.5,
      1.5,
      ["Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Pantorrillas:gastrocnemio", "Pantorrillas:s\xF3leo"],
      ["Core", "Deltoides"]
    ),
    mk(
      "tren_inferior_arrastre_trineo",
      "Arrastre de Trineo en Reversa",
      "Arrastrar trineo caminando hacia atr\xE1s. Cu\xE1driceps y vasto medial. Salud de rodilla y trabajo exc\xE9ntrico.",
      "Trineo",
      "Accesorio",
      "Fuerza",
      "Empuje",
      4,
      3,
      0.2,
      1.5,
      ["Cu\xE1driceps"],
      [],
      ["Core", "Antebrazo"]
    ),
    // ========== EXTENSIÓN DE RODILLA ==========
    mk(
      "tren_inferior_extension_cuadriceps",
      "Extensi\xF3n de Cu\xE1driceps en M\xE1quina",
      "Extensi\xF3n de rodilla en m\xE1quina. A\xEDsla cu\xE1driceps. Sin carga axial. Ideal para pre-agotamiento o rehabilitaci\xF3n.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      2.5,
      1.5,
      0,
      0.8,
      ["Cu\xE1driceps"],
      [],
      []
    ),
    mk(
      "tren_inferior_extension_cuadriceps_unilateral",
      "Extensi\xF3n de Cu\xE1driceps Unilateral",
      "Extensi\xF3n a una pierna. Corrige asimetr\xEDas. Menor carga total que bilateral.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      2.2,
      1.5,
      0,
      0.8,
      ["Cu\xE1driceps"],
      [],
      []
    ),
    mk(
      "tren_inferior_extension_inversa_nordica",
      "Extensi\xF3n Inversa N\xF3rdica",
      "Descender controlando con cu\xE1driceps (curl n\xF3rdico invertido). Exc\xE9ntrico puro. Alto TTC. Requiere pareja o anclaje.",
      "Peso Corporal",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      3.5,
      3,
      0.2,
      3.6,
      ["Cu\xE1driceps"],
      [],
      ["Core", "Gl\xFAteos:mayor"]
    ),
    // ========== FLEXIÓN DE RODILLA ==========
    mk(
      "tren_inferior_curl_femoral_tumbado",
      "Curl Femoral Tumbado en M\xE1quina",
      "Curl acostado. A\xEDsla isquiosurales en flexi\xF3n de rodilla. Est\xE1ndar de hipertrofia de isquios.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Flexi\xF3n",
      2.5,
      1.5,
      0,
      0.8,
      ["Isquiosurales"],
      ["Pantorrillas:gastrocnemio"],
      []
    ),
    mk(
      "tren_inferior_curl_femoral_sentado",
      "Curl Femoral Sentado en M\xE1quina",
      "Curl sentado. Menor estiramiento que tumbado. Enfasis en porci\xF3n inferior de isquios.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      1.5,
      0,
      0.8,
      ["Isquiosurales"],
      ["Pantorrillas:gastrocnemio"],
      []
    ),
    mk(
      "tren_inferior_curl_femoral_pie_polea",
      "Curl Femoral de Pie en Polea Baja",
      "Curl con polea baja de pie. Permite rango completo y estiramiento. Alternativa cuando no hay m\xE1quina de curl.",
      "Polea",
      "Aislamiento",
      "Hipertrofia",
      "Flexi\xF3n",
      2,
      1.8,
      0,
      0.8,
      ["Isquiosurales"],
      ["Pantorrillas:gastrocnemio"],
      ["Core", "Gl\xFAteos:medio"]
    ),
    mk(
      "tren_inferior_curl_femoral_balon",
      "Curl Femoral Sobre Bal\xF3n Suizo",
      "Curl con piernas sobre fitball. Inestabilidad a\xF1ade demanda de core. Menor carga, mayor control.",
      "Bal\xF3n Medicinal",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.5,
      0.1,
      1.8,
      ["Isquiosurales"],
      ["Gl\xFAteos:mayor"],
      ["Core", "Erectores Espinales"]
    ),
    mk(
      "tren_inferior_glute_ham_raise",
      "Glute Ham Raise en M\xE1quina",
      "Extensi\xF3n de cadera y flexi\xF3n de rodilla en GHD. Ejercicio rey para isquios. Alto TTC.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      4,
      3.5,
      0.6,
      3.6,
      ["Isquiosurales"],
      ["Gl\xFAteos:mayor", "Pantorrillas:gastrocnemio"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_curl_nordico",
      "Curl N\xF3rdico",
      "Exc\xE9ntrico de isquios contra gravedad. Alto TTC. Excelente para prevenci\xF3n de lesiones de isquios. Requiere anclaje.",
      "Peso Corporal",
      "Accesorio",
      "Fuerza",
      "Flexi\xF3n",
      4.8,
      4,
      0.5,
      4.5,
      ["Isquiosurales"],
      ["Pantorrillas:gastrocnemio"],
      ["Gl\xFAteos:mayor", "Core"]
    ),
    // ========== FLEXIÓN PLANTAR ==========
    mk(
      "tren_inferior_elevacion_talones_pie_barra",
      "Elevaci\xF3n de Talones de Pie con Barra",
      "Gemelos de pie con barra. M\xE1ximo rango de estiramiento. Enfasis en gastrocnemio. Carga axial moderada.",
      "Barra",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      2.8,
      2.5,
      1.6,
      2.4,
      ["Pantorrillas:gastrocnemio"],
      ["Pantorrillas:s\xF3leo"],
      ["Erectores Espinales", "Core"]
    ),
    mk(
      "tren_inferior_elevacion_talones_pie_maquina",
      "Elevaci\xF3n de Talones de Pie en M\xE1quina",
      "Gemelos en m\xE1quina de pie. Carga guiada. Menor demanda de equilibrio que barra libre.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      2.5,
      2,
      1.5,
      1.6,
      ["Pantorrillas:gastrocnemio"],
      ["Pantorrillas:s\xF3leo"],
      ["Core"]
    ),
    mk(
      "tren_inferior_elevacion_talones_sentado",
      "Elevaci\xF3n de Talones Sentado en M\xE1quina",
      "Gemelos sentado. Rodillas flexionadas. A\xEDsla s\xF3leo. Cero carga axial.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      2,
      1.5,
      0,
      0.8,
      ["Pantorrillas:s\xF3leo"],
      [],
      []
    ),
    mk(
      "tren_inferior_elevacion_talones_prensa",
      "Elevaci\xF3n de Talones en Prensa",
      "Gemelos en prensa de piernas. Carga en prensa, extensi\xF3n plantar. Alternativa a m\xE1quina de gemelos.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      2.2,
      1.5,
      0.2,
      0.8,
      ["Pantorrillas:gastrocnemio"],
      ["Pantorrillas:s\xF3leo"],
      []
    ),
    mk(
      "tren_inferior_elevacion_talones_unilateral",
      "Elevaci\xF3n de Talones Unilateral con Mancuerna",
      "Gemelos a una pierna con mancuerna. Corrige asimetr\xEDas. Permite rango completo por pierna.",
      "Mancuerna",
      "Aislamiento",
      "Hipertrofia",
      "Extensi\xF3n",
      2.4,
      1.8,
      0.2,
      1.2,
      ["Pantorrillas:gastrocnemio"],
      ["Pantorrillas:s\xF3leo"],
      ["Antebrazo"]
    ),
    // ========== ABDUCCIÓN / ADUCCIÓN DE CADERA ==========
    mk(
      "tren_inferior_abduccion_cadera_maquina",
      "Abducci\xF3n de Cadera en M\xE1quina",
      "Separar piernas contra resistencia. A\xEDsla gl\xFAteo medio y menor. Importante para salud de cadera y rodilla.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Otro",
      2,
      1.2,
      0,
      0.8,
      ["Gl\xFAteos:medio"],
      ["Gl\xFAteos:medio"],
      []
    ),
    mk(
      "tren_inferior_abduccion_cadera_polea",
      "Abducci\xF3n de Cadera en Polea Baja",
      "Abducci\xF3n con cable. Permite diferentes \xE1ngulos y rango completo. Alternativa a m\xE1quina.",
      "Polea",
      "Aislamiento",
      "Hipertrofia",
      "Otro",
      2.2,
      1.5,
      0,
      0.8,
      ["Gl\xFAteos:medio"],
      ["Gl\xFAteos:medio"],
      ["Core", "Gl\xFAteos:medio"]
    ),
    mk(
      "tren_inferior_aduccion_cadera_maquina",
      "Aducci\xF3n de Cadera en M\xE1quina",
      "Juntar piernas contra resistencia. A\xEDsla aductores. Importante para estabilidad de cadera y prevenci\xF3n de lesiones.",
      "M\xE1quina",
      "Aislamiento",
      "Hipertrofia",
      "Otro",
      2,
      1.2,
      0,
      0.8,
      ["Aductores"],
      ["Aductores"],
      []
    ),
    mk(
      "tren_inferior_aduccion_cadera_polea",
      "Aducci\xF3n de Cadera en Polea Baja",
      "Aducci\xF3n con cable. Tensi\xF3n constante. Alternativa a m\xE1quina de aducci\xF3n.",
      "Polea",
      "Aislamiento",
      "Hipertrofia",
      "Otro",
      2.2,
      1.5,
      0,
      0.8,
      ["Aductores"],
      ["Aductores"],
      ["Core", "Gl\xFAteos:medio"]
    ),
    mk(
      "tren_inferior_caminata_lateral_banda",
      "Caminata Lateral con Minibanda",
      "Pasos laterales con banda en rodillas. Activa gl\xFAteo medio. Excelente para calentamiento y prevenci\xF3n.",
      "Banda",
      "Accesorio",
      "Movilidad",
      "Otro",
      2,
      1.5,
      0,
      1,
      ["Gl\xFAteos:medio"],
      ["Gl\xFAteos:medio"],
      []
    )
  ];
  var UPPER_BODY_EXERCISES = [
    // --- EMPUJE HORIZONTAL ---
    mk(
      "tren_superior_press_banca_plano_barra",
      "Press de Banca Plano con Barra",
      "Press plano cl\xE1sico de powerlifting. Pectorales, deltoides anterior y tr\xEDceps. Agarre medio a ancho.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      4.2,
      4,
      1.2,
      2.2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      ["B\xEDceps"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banca_plano_mancuernas",
      "Press de Banca Plano con Mancuernas",
      "Press con mancuernas permite mayor recorrido y estabilizaci\xF3n. Menor carga pero mayor est\xEDmulo de estiramiento.",
      "Mancuerna",
      "B\xE1sico",
      "Hipertrofia",
      "Empuje",
      3.8,
      3.5,
      1,
      2.2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      ["B\xEDceps"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banca_inclinado_barra",
      "Press de Banca Inclinado con Barra",
      "Banca inclinada 30-45\xB0. \xC9nfasis en porci\xF3n clavicular del pectoral y deltoides anterior.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      4,
      3.8,
      1.1,
      2.2,
      ["Pectorales:superior"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banca_inclinado_mancuernas",
      "Press de Banca Inclinado con Mancuernas",
      "Press inclinado con mancuernas. Mayor rango de movimiento y libertad articular.",
      "Mancuerna",
      "B\xE1sico",
      "Hipertrofia",
      "Empuje",
      3.6,
      3.2,
      1,
      2.2,
      ["Pectorales:superior"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banca_declinado_barra",
      "Press de Banca Declinado con Barra",
      "Banca declinada. \xC9nfasis en porci\xF3n esternal del pectoral. Favorece cargas m\xE1s pesadas en algunos atletas.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      4,
      3.8,
      1.1,
      2.2,
      ["Pectorales:inferior"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banca_declinado_mancuernas",
      "Press de Banca Declinado con Mancuernas",
      "Press declinado con mancuernas. Mayor estiramiento en la porci\xF3n inferior del pectoral.",
      "Mancuerna",
      "B\xE1sico",
      "Hipertrofia",
      "Empuje",
      3.6,
      3.2,
      1,
      2.2,
      ["Pectorales:inferior"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banca_agarre_cerrado",
      "Press de Banca con Agarre Cerrado",
      "Agarre estrecho aumenta participaci\xF3n del tr\xEDceps y reduce el brazo de palanca del pectoral.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Empuje",
      3.5,
      3.2,
      1,
      2,
      ["Tr\xEDceps", "Pectorales", "Deltoides:anterior"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_press_pecho_maquina_convergente",
      "Press de Pecho en M\xE1quina Convergente",
      "M\xE1quina con brazos convergentes. Estabilidad m\xE1xima y aislamiento del pectoral.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.8,
      0.8,
      2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_pecho_maquina_smith",
      "Press de Pecho en M\xE1quina Smith",
      "Press en Smith: trayectoria fija y menor demanda estabilizadora. \xDAtil para cargas pesadas.",
      "M\xE1quina",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      3.8,
      3.5,
      1,
      2.2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_floor_press_barra",
      "Floor Press con Barra",
      "Press tumbado en el suelo. Los codos tocan el suelo limitando el recorrido. Enfatiza tr\xEDceps y bloqueo.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Empuje",
      3.5,
      3.2,
      1,
      2,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_floor_press_mancuernas",
      "Floor Press con Mancuernas",
      "Floor press con mancuernas. Mayor libertad y trabajo estabilizador que con barra.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.8,
      0.9,
      2,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_flexiones_clasicas",
      "Flexiones de Brazos Cl\xE1sicas",
      "Flexiones est\xE1ndar en el suelo. Peso corporal como resistencia.",
      "Peso Corporal",
      "B\xE1sico",
      "Hipertrofia",
      "Empuje",
      3,
      2.5,
      0.8,
      1.8,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_flexiones_lastradas",
      "Flexiones de Brazos Lastradas",
      "Flexiones con peso adicional sobre la espalda. Progresi\xF3n de carga para atletas avanzados.",
      "Peso Corporal",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      3.5,
      3,
      0.9,
      1.8,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_flexiones_pies_elevados",
      "Flexiones con Pies Elevados",
      "Pies elevados sobre banco o caja. Mayor carga sobre la parte superior del pectoral.",
      "Peso Corporal",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.8,
      0.8,
      1.8,
      ["Pectorales:superior"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_flexiones_anillas",
      "Flexiones en Anillas",
      "Flexiones en anillas o TRX. Alta demanda estabilizadora y ajuste de dificultad por inclinaci\xF3n.",
      "TRX",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3,
      2.8,
      0.7,
      1.8,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_flexiones_diamante",
      "Flexiones Diamante",
      "Manos juntas formando un diamante. M\xE1ximo \xE9nfasis en tr\xEDceps.",
      "Peso Corporal",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.8,
      2.5,
      0.7,
      1.6,
      ["Tr\xEDceps", "Pectorales", "Deltoides:anterior"],
      [],
      ["Core"],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_press_unilateral_polea",
      "Press Unilateral en Polea",
      "Press con una mano en polea baja. Permite correcci\xF3n de desbalances y trabajo unilateral.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.8,
      2.5,
      0.7,
      2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_spoto_barra",
      "Press Spoto con Barra",
      "Variante de banca donde la barra hace pausa corta a 2-3 cm del pecho. Elimina rebote y trabaja punto muerto.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Empuje",
      4,
      3.8,
      1.2,
      2.2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banda_resistencia",
      "Press con Banda de Resistencia",
      "Press de banca con bandas para resistencia variable. Mayor tensi\xF3n en el punto de bloqueo.",
      "Banda",
      "Accesorio",
      "Fuerza",
      "Empuje",
      3.2,
      3,
      0.9,
      2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_press_banca_cadenas",
      "Press de Banca con Cadenas",
      "Press con cadenas colgando. Resistencia variable que aumenta en el bloqueo.",
      "Barra",
      "Accesorio",
      "Fuerza",
      "Empuje",
      4,
      3.8,
      1.1,
      2.2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    // --- ADUCCIÓN HORIZONTAL ---
    mk(
      "tren_superior_aperturas_planas_mancuernas",
      "Aperturas Planas con Mancuernas",
      "Aperturas en banco plano. Aislamiento del pectoral en aducci\xF3n horizontal. Buen estiramiento.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.5,
      2.2,
      0.6,
      1.8,
      ["Pectorales"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_aperturas_inclinadas_mancuernas",
      "Aperturas Inclinadas con Mancuernas",
      "Aperturas en banco inclinado. \xC9nfasis en porci\xF3n clavicular del pectoral.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.4,
      2,
      0.6,
      1.8,
      ["Pectorales:superior"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_aperturas_declinadas_mancuernas",
      "Aperturas Declinadas con Mancuernas",
      "Aperturas en banco declinado. \xC9nfasis en porci\xF3n esternal del pectoral.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.4,
      2,
      0.6,
      1.8,
      ["Pectorales:inferior"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_aperturas_pec_deck",
      "Aperturas en M\xE1quina Pec Deck",
      "Aperturas en m\xE1quina. Estabilidad m\xE1xima y aislamiento del pectoral.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.5,
      2.2,
      0.6,
      1.8,
      ["Pectorales"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_cruce_poleas_altas",
      "Cruce de Poleas Altas",
      "Cruce con poleas altas. El \xE1ngulo trabaja m\xE1s la porci\xF3n inferior del pectoral.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.4,
      2,
      0.6,
      1.8,
      ["Pectorales:inferior"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_cruce_poleas_bajas",
      "Cruce de Poleas Bajas",
      "Cruce con poleas bajas. El \xE1ngulo trabaja m\xE1s la porci\xF3n clavicular del pectoral.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.4,
      2,
      0.6,
      1.8,
      ["Pectorales:superior"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_aperturas_suelo_mancuernas",
      "Aperturas en el Suelo con Mancuernas",
      "Aperturas tumbado en el suelo. Recorrido limitado pero seguro para hombros.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2,
      1.8,
      0.5,
      1.6,
      ["Pectorales"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_squeeze_press_mancuernas",
      "Squeeze Press con Mancuernas",
      "Press con mancuernas manteniendo palmas enfrentadas y comprimiendo. Tensi\xF3n continua en pectoral.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3,
      2.5,
      0.7,
      2,
      ["Pectorales"],
      ["Deltoides:anterior", "Tr\xEDceps"],
      [],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_aperturas_banda",
      "Aperturas con Banda de Resistencia",
      "Aperturas con banda anclada. Resistencia variable y portabilidad.",
      "Banda",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.2,
      1.8,
      0.5,
      1.6,
      ["Pectorales"],
      ["Deltoides:anterior"],
      [],
      "Pectorales",
      "upper"
    ),
    // --- EMPUJE VERTICAL ---
    mk(
      "tren_superior_press_militar_pie_barra",
      "Press Militar de Pie con Barra",
      "Press de hombros de pie con barra. Patr\xF3n fundamental de empuje vertical. Deltoides, tr\xEDceps y core.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      4,
      3.8,
      1.2,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Trapecio", "Core"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_militar_sentado_barra",
      "Press Militar Sentado con Barra",
      "Press sentado reduce implicaci\xF3n del core y permite concentrarse en hombros.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      3.8,
      3.5,
      1.1,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_hombros_sentado_mancuernas",
      "Press de Hombros Sentado con Mancuernas",
      "Press sentado con mancuernas. Mayor rango y libertad que con barra.",
      "Mancuerna",
      "B\xE1sico",
      "Hipertrofia",
      "Empuje",
      3.5,
      3.2,
      1,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_hombros_pie_mancuernas",
      "Press de Hombros de Pie con Mancuernas",
      "Press de pie con mancuernas. Mayor activaci\xF3n del core como estabilizador.",
      "Mancuerna",
      "B\xE1sico",
      "Hipertrofia",
      "Empuje",
      3.6,
      3.2,
      1,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Trapecio", "Core"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_arnold_mancuernas",
      "Press Arnold con Mancuernas",
      "Press con rotaci\xF3n: empieza supino y termina prono. Trabaja todo el deltoides en un solo movimiento.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.4,
      3,
      0.9,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio", "Deltoides:posterior"],
      ["Tr\xEDceps"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_hombros_maquina_convergente",
      "Press de Hombros en M\xE1quina Convergente",
      "Press en m\xE1quina con brazos convergentes. Estabilidad y aislamiento.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.8,
      0.8,
      2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_hombros_maquina_smith",
      "Press de Hombros en M\xE1quina Smith",
      "Press de hombros en Smith. Trayectoria fija para cargas pesadas.",
      "M\xE1quina",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      3.6,
      3.2,
      1,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_push_press_barra",
      "Push Press con Barra",
      "Press con impulso de piernas. Permite mover m\xE1s peso y trabaja potencia.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      4,
      3.8,
      1.2,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps", "Cu\xE1driceps", "Gl\xFAteos:mayor"],
      ["Trapecio", "Core"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_tras_nuca_barra",
      "Press tras Nuca con Barra",
      "Press con barra detr\xE1s de la nuca. Mayor implicaci\xF3n del deltoides medio. Requiere buena movilidad.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.4,
      3,
      0.9,
      2.2,
      ["Deltoides:medio", "Deltoides:anterior"],
      ["Tr\xEDceps"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_landmine_un_brazo",
      "Press Landmine a un Brazo",
      "Press con barra en landmine a una mano. Patr\xF3n diagonal y trabajo unilateral.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.8,
      0.8,
      2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Core"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_kettlebell_un_brazo",
      "Press con Kettlebell a un Brazo",
      "Press con kettlebell unilateral. El agarre irregular aumenta demanda estabilizadora.",
      "Kettlebell",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3,
      2.6,
      0.8,
      2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Core", "Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_z_barra",
      "Press Z con Barra",
      "Press con barra Z o EZ. Agarre neutro que puede ser m\xE1s c\xF3modo para hombros.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.4,
      3,
      0.9,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_press_z_mancuernas",
      "Press Z con Mancuernas",
      "Press tipo Z con mancuernas. Similar al Arnold pero con menor rotaci\xF3n.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.8,
      0.8,
      2.2,
      ["Deltoides:anterior", "Deltoides:medio"],
      ["Tr\xEDceps"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_fondos_paralelas",
      "Fondos en Paralelas",
      "Fondos en barras paralelas. Pectorales, tr\xEDceps y deltoides anterior. Peso corporal.",
      "Peso Corporal",
      "B\xE1sico",
      "Hipertrofia",
      "Empuje",
      3.5,
      3,
      0.9,
      2,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_fondos_lastrados",
      "Fondos Lastrados",
      "Fondos en paralelas con peso adicional. Progresi\xF3n de carga.",
      "Peso Corporal",
      "B\xE1sico",
      "Fuerza",
      "Empuje",
      4,
      3.5,
      1,
      2,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_fondos_anillas",
      "Fondos en Anillas",
      "Fondos en anillas. Mayor inestabilidad y demanda t\xE9cnica.",
      "TRX",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      3.2,
      2.8,
      0.8,
      2,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    mk(
      "tren_superior_fondos_entre_bancos",
      "Fondos Entre Bancos",
      "Fondos con manos en bancos. Variante de fondos para principiantes o sin paralelas.",
      "Peso Corporal",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Pectorales"],
      ["Tr\xEDceps", "Deltoides:anterior"],
      ["Core"],
      "Pectorales",
      "upper"
    ),
    // --- TRACCIÓN VERTICAL ---
    mk(
      "tren_superior_dominadas_pronas",
      "Dominadas Pronas",
      "Dominadas con agarre prono. \xC9nfasis en dorsal ancho y b\xEDceps. Patr\xF3n fundamental de tracci\xF3n vertical.",
      "Peso Corporal",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      4.2,
      4,
      1.2,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_dominadas_supinas",
      "Dominadas Supinas",
      "Dominadas con agarre supino. Mayor participaci\xF3n del b\xEDceps.",
      "Peso Corporal",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      4,
      3.8,
      1.1,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_dominadas_neutras",
      "Dominadas Neutras",
      "Dominadas con agarre neutro. Posici\xF3n m\xE1s amigable para hombros.",
      "Peso Corporal",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      4,
      3.8,
      1.1,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_dominadas_lastradas",
      "Dominadas Lastradas",
      "Dominadas con peso adicional. Progresi\xF3n de carga.",
      "Peso Corporal",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      4.5,
      4.2,
      1.3,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_dominadas_anillas",
      "Dominadas en Anillas",
      "Dominadas en anillas. Mayor libertad de movimiento y estabilizaci\xF3n.",
      "TRX",
      "Accesorio",
      "Fuerza",
      "Tir\xF3n",
      4,
      3.8,
      1,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_jalon_pecho_prono",
      "Jal\xF3n al Pecho Prono en Polea",
      "Jal\xF3n al pecho con agarre prono. Simula dominadas con resistencia ajustable.",
      "Polea",
      "B\xE1sico",
      "Hipertrofia",
      "Tir\xF3n",
      3.8,
      3.5,
      1,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_jalon_pecho_supino",
      "Jal\xF3n al Pecho Supino en Polea",
      "Jal\xF3n con agarre supino. Mayor participaci\xF3n del b\xEDceps.",
      "Polea",
      "B\xE1sico",
      "Hipertrofia",
      "Tir\xF3n",
      3.6,
      3.2,
      1,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_jalon_pecho_neutro",
      "Jal\xF3n al Pecho Neutro en Polea",
      "Jal\xF3n con agarre neutro y barra en V. Posici\xF3n c\xF3moda para hombros.",
      "Polea",
      "B\xE1sico",
      "Hipertrofia",
      "Tir\xF3n",
      3.6,
      3.2,
      1,
      2.2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_jalon_tras_nuca",
      "Jal\xF3n tras Nuca en Polea",
      "Jal\xF3n llevando la barra detr\xE1s de la nuca. Mayor \xE9nfasis en dorsal. Requiere buena movilidad.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.5,
      3.2,
      1,
      2.2,
      ["Dorsales"],
      ["Dorsales", "B\xEDceps", "Trapecio"],
      ["Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_jalon_unilateral_polea",
      "Jal\xF3n Unilateral en Polea",
      "Jal\xF3n a una mano. Permite trabajo unilateral y correcci\xF3n de desbalances.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.2,
      2.8,
      0.8,
      2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_dominadas_asistidas",
      "Dominadas Asistidas en M\xE1quina",
      "Dominadas con asistencia de m\xE1quina. Ideal para principiantes que no pueden hacer dominadas.",
      "M\xE1quina",
      "B\xE1sico",
      "Hipertrofia",
      "Tir\xF3n",
      3.2,
      2.8,
      0.8,
      2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Dorsales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    // --- EXTENSIÓN DE HOMBRO ---
    mk(
      "tren_superior_jalon_brazos_extendidos",
      "Jal\xF3n Brazos Extendidos en Polea",
      "Pullover en polea alta con brazos extendidos. Extensi\xF3n de hombro enfocada en dorsal y serrato.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3,
      2.6,
      0.8,
      2,
      ["Dorsales", "Pectorales"],
      ["Tr\xEDceps"],
      ["Abdomen"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_pullover_mancuerna",
      "Pullover con Mancuerna",
      "Pullover cl\xE1sico con mancuerna. Extensi\xF3n de hombro trabajando dorsal, serrato y pectoral.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.2,
      2.8,
      0.8,
      2,
      ["Dorsales", "Pectorales"],
      ["Tr\xEDceps"],
      ["Abdomen"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_pullover_barra",
      "Pullover con Barra",
      "Pullover con barra. Similar al de mancuerna con mayor estabilidad.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3,
      2.6,
      0.8,
      2,
      ["Dorsales", "Pectorales"],
      ["Tr\xEDceps"],
      ["Abdomen"],
      "Dorsales",
      "upper"
    ),
    // --- TRACCIÓN HORIZONTAL ---
    mk(
      "tren_superior_remo_inclinado_prono_barra",
      "Remo Inclinado Prono con Barra",
      "Remo con barra, torso inclinado, agarre prono. Fundamental para espalda.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      4.2,
      4,
      1.2,
      2.4,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Erectores Espinales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_inclinado_supino_barra",
      "Remo Inclinado Supino con Barra",
      "Remo con barra y agarre supino. Mayor participaci\xF3n del b\xEDceps.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      4,
      3.8,
      1.1,
      2.4,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio:medio"],
      ["Erectores Espinales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_pendlay_barra",
      "Remo Pendlay con Barra",
      "Remo con barra apoyada en el suelo entre repeticiones. T\xE9cnica estricta y desarrollo de espalda.",
      "Barra",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      4.2,
      4,
      1.2,
      2.4,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Erectores Espinales", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_barra_t_apoyado",
      "Remo en Barra T Apoyado",
      "Remo en barra T con pecho apoyado. Reduce cheating y a\xEDsla la espalda.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.8,
      3.5,
      1,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_barra_t_libre",
      "Remo en Barra T Libre",
      "Remo en barra T sin apoyo. Mayor implicaci\xF3n del core.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      4,
      3.6,
      1.1,
      2.4,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Erectores Espinales", "Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_una_mano_mancuerna",
      "Remo a una Mano con Mancuerna",
      "Remo unilateral con mancuerna. Permite mayor rango y correcci\xF3n de desbalances.",
      "Mancuerna",
      "B\xE1sico",
      "Hipertrofia",
      "Tir\xF3n",
      4,
      3.6,
      1,
      2.4,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Erectores Espinales", "Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_mancuernas_banco_inclinado",
      "Remo con Mancuernas Banco Inclinado",
      "Remo con mancuernas apoyando el pecho en banco inclinado. Aislamiento de espalda.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.6,
      3.2,
      0.9,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_sentado_polea_baja",
      "Remo Sentado en Polea Baja",
      "Remo sentado con polea baja. Variedad de agarres y \xE1ngulos.",
      "Polea",
      "B\xE1sico",
      "Hipertrofia",
      "Tir\xF3n",
      3.8,
      3.4,
      1,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_unilateral_polea_baja",
      "Remo Unilateral en Polea Baja",
      "Remo a una mano en polea. Trabajo unilateral con resistencia constante.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.4,
      3,
      0.9,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_maquina_convergente",
      "Remo en M\xE1quina Convergente",
      "Remo en m\xE1quina con brazos convergentes. Estabilidad y aislamiento.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.6,
      3.2,
      0.9,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_invertido_peso_corporal",
      "Remo Invertido con Peso Corporal",
      "Remo con cuerpo colgado de barra baja. Progresi\xF3n hacia dominadas.",
      "Peso Corporal",
      "B\xE1sico",
      "Hipertrofia",
      "Tir\xF3n",
      3.2,
      2.8,
      0.8,
      2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Core", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_invertido_lastrado",
      "Remo Invertido Lastrado",
      "Remo invertido con peso adicional. Progresi\xF3n de carga.",
      "Peso Corporal",
      "B\xE1sico",
      "Fuerza",
      "Tir\xF3n",
      3.8,
      3.4,
      1,
      2,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Core", "Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_seal_barra",
      "Remo Seal con Barra",
      "Remo con barra en posici\xF3n horizontal, brazos extendidos. Rango \xFAnico.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.6,
      3.2,
      0.9,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_meadows_mancuerna",
      "Remo Meadows con Mancuerna",
      "Remo tipo Meadows con mancuerna y apoyo. \xC1ngulo diagonal caracter\xEDstico.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.6,
      3.2,
      0.9,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_kettlebell",
      "Remo con Kettlebell",
      "Remo unilateral con kettlebell. Agarres alternativos disponibles.",
      "Kettlebell",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      3.4,
      3,
      0.9,
      2.2,
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps", "Dorsales"],
      ["Deltoides:posterior", "Core"],
      "Dorsales",
      "upper"
    ),
    mk(
      "tren_superior_remo_banda",
      "Remo con Banda de Resistencia",
      "Remo con banda anclada. Portabilidad y resistencia variable.",
      "Banda",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Dorsales", "B\xEDceps"],
      ["Dorsales", "Trapecio"],
      ["Deltoides:posterior"],
      "Dorsales",
      "upper"
    ),
    // --- ABDUCCIÓN DE HOMBRO ---
    mk(
      "tren_superior_elevaciones_laterales_mancuernas",
      "Elevaciones Laterales con Mancuernas",
      "Abducci\xF3n de hombro para deltoides medio. Aislamiento cl\xE1sico.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      2.5,
      2.2,
      0.6,
      1.8,
      ["Deltoides:medio"],
      ["Deltoides:anterior", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_laterales_sentadas",
      "Elevaciones Laterales Sentadas",
      "Elevaciones laterales sentado. Reduce el uso del impulso.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      2.4,
      2,
      0.6,
      1.8,
      ["Deltoides:medio"],
      ["Deltoides:anterior", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_laterales_polea_baja",
      "Elevaciones Laterales en Polea Baja",
      "Elevaciones laterales con polea baja. Tensi\xF3n constante en todo el rango.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      2.4,
      2,
      0.6,
      1.8,
      ["Deltoides:medio"],
      ["Deltoides:anterior", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_laterales_maquina",
      "Elevaciones Laterales en M\xE1quina",
      "Abducci\xF3n en m\xE1quina. Estabilidad y aislamiento del deltoides medio.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      2.5,
      2.2,
      0.6,
      1.8,
      ["Deltoides:medio"],
      ["Deltoides:anterior", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_laterales_banda",
      "Elevaciones Laterales con Banda",
      "Elevaciones laterales con banda. Resistencia variable y portabilidad.",
      "Banda",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      2.2,
      1.8,
      0.5,
      1.6,
      ["Deltoides:medio"],
      ["Deltoides:anterior", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_remo_menton_barra",
      "Remo al Ment\xF3n con Barra",
      "Remo alto con barra. Deltoides medio, trapecio y porci\xF3n larga del b\xEDceps. Cuidado con hombros.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      3.2,
      2.8,
      0.8,
      2,
      ["Deltoides:medio", "Trapecio"],
      ["Deltoides:anterior", "B\xEDceps", "Antebrazo"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_remo_menton_polea",
      "Remo al Ment\xF3n en Polea",
      "Remo alto en polea. Similar al de barra con resistencia constante.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      3,
      2.6,
      0.8,
      2,
      ["Deltoides:medio", "Trapecio"],
      ["Deltoides:anterior", "B\xEDceps", "Antebrazo"],
      [],
      "Deltoides",
      "upper"
    ),
    // --- FLEXIÓN DE HOMBRO ---
    mk(
      "tren_superior_elevaciones_frontales_barra",
      "Elevaciones Frontales con Barra",
      "Flexi\xF3n de hombros con barra. Deltoides anterior principalmente.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["Deltoides:anterior"],
      ["Pectorales:superior"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_frontales_mancuernas",
      "Elevaciones Frontales con Mancuernas",
      "Flexi\xF3n de hombros con mancuernas. Permite trabajo alterno o simult\xE1neo.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.4,
      2,
      0.6,
      1.8,
      ["Deltoides:anterior"],
      ["Pectorales:superior"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_frontales_disco",
      "Elevaciones Frontales con Disco",
      "Flexi\xF3n de hombros sosteniendo un disco. Variante de elevaciones frontales.",
      "Disco",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.4,
      2,
      0.6,
      1.8,
      ["Deltoides:anterior"],
      ["Pectorales:superior"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_frontales_polea",
      "Elevaciones Frontales en Polea",
      "Flexi\xF3n de hombros con polea baja. Tensi\xF3n constante.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.4,
      2,
      0.6,
      1.8,
      ["Deltoides:anterior"],
      ["Pectorales:superior"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_frontales_cuerda_polea",
      "Elevaciones Frontales con Cuerda en Polea",
      "Flexi\xF3n de hombros con agarre de cuerda. Permite posici\xF3n neutra de mu\xF1eca.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.4,
      2,
      0.6,
      1.8,
      ["Deltoides:anterior"],
      ["Pectorales:superior"],
      ["Trapecio"],
      "Deltoides",
      "upper"
    ),
    // --- ABDUCCIÓN HORIZONTAL (posterior) ---
    mk(
      "tren_superior_pajaros_inclinados_mancuernas",
      "P\xE1jaros Inclinados con Mancuernas",
      "Abducci\xF3n horizontal con torso inclinado. Aislamiento del deltoides posterior.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      2.5,
      2.2,
      0.6,
      1.8,
      ["Deltoides:posterior"],
      ["Dorsales", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_pajaros_pec_deck_inverso",
      "P\xE1jaros en M\xE1quina Pec Deck Inverso",
      "Abducci\xF3n horizontal en m\xE1quina. Deltoides posterior con estabilidad m\xE1xima.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      2.5,
      2.2,
      0.6,
      1.8,
      ["Deltoides:posterior"],
      ["Dorsales", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_elevaciones_posteriores_polea",
      "Elevaciones Posteriores en Polea a un Brazo",
      "Abducci\xF3n horizontal unilateral en polea. Deltoides posterior.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Tir\xF3n",
      2.4,
      2,
      0.6,
      1.8,
      ["Deltoides:posterior"],
      ["Dorsales", "Trapecio"],
      [],
      "Deltoides",
      "upper"
    ),
    // --- ROTACIÓN EXTERNA ---
    mk(
      "tren_superior_face_pull_polea",
      "Face Pull en Polea Alta",
      "Tracci\xF3n hacia la cara con rotaci\xF3n externa. Salud del manguito rotador y deltoides posterior.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Rotaci\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Deltoides:posterior", "Dorsales"],
      ["Dorsales", "Trapecio:medio"],
      ["B\xEDceps"],
      "Deltoides",
      "upper"
    ),
    mk(
      "tren_superior_face_pull_banda",
      "Face Pull con Banda de Resistencia",
      "Face pull con banda. Versi\xF3n portable para entrenar rotadores externos.",
      "Banda",
      "Accesorio",
      "Hipertrofia",
      "Rotaci\xF3n",
      2.4,
      2,
      0.6,
      1.6,
      ["Deltoides:posterior", "Dorsales"],
      ["Dorsales", "Trapecio:medio"],
      [],
      "Deltoides",
      "upper"
    ),
    // --- ELEVACIÓN ESCAPULAR ---
    mk(
      "tren_superior_encogimientos_barra",
      "Encogimientos de Hombros con Barra",
      "Elevaci\xF3n escapular con barra. Aislamiento del trapecio superior.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Trapecio:superior"],
      [],
      [],
      "Trapecio",
      "upper"
    ),
    mk(
      "tren_superior_encogimientos_mancuernas",
      "Encogimientos de Hombros con Mancuernas",
      "Elevaci\xF3n escapular con mancuernas. Mayor rango que con barra.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.6,
      2.2,
      0.6,
      1.8,
      ["Trapecio:superior"],
      [],
      [],
      "Trapecio",
      "upper"
    ),
    mk(
      "tren_superior_encogimientos_maquina_smith",
      "Encogimientos de Hombros en M\xE1quina Smith",
      "Encogimientos en Smith. Trayectoria fija para cargas pesadas.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Trapecio:superior"],
      [],
      [],
      "Trapecio",
      "upper"
    ),
    mk(
      "tren_superior_encogimientos_barra_hexagonal",
      "Encogimientos con Barra Hexagonal",
      "Encogimientos con barra hexagonal o trap bar. C\xF3modo para mu\xF1ecas y hombros.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Empuje",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Trapecio:superior"],
      [],
      [],
      "Trapecio",
      "upper"
    ),
    // --- FLEXIÓN DE CODO (bíceps) ---
    mk(
      "tren_superior_curl_biceps_barra_recta",
      "Curl de B\xEDceps con Barra Recta",
      "Curl cl\xE1sico con barra recta. B\xEDceps y braquial.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      3,
      2.6,
      0.7,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_biceps_barra_ez",
      "Curl de B\xEDceps con Barra EZ",
      "Curl con barra EZ. Angulaci\xF3n reduce estr\xE9s en mu\xF1ecas.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_biceps_alterno_mancuernas",
      "Curl de B\xEDceps Alterno con Mancuernas",
      "Curl alterno con mancuernas. Permite supinaci\xF3n completa y trabajo unilateral.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_biceps_sentado_mancuernas",
      "Curl de B\xEDceps Sentado con Mancuernas",
      "Curl sentado con mancuernas. Elimina impulso del torso.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_martillo_mancuernas",
      "Curl Martillo con Mancuernas",
      "Curl con agarre neutro. Mayor \xE9nfasis en braquial y braquiorradial.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["B\xEDceps:braquial", "Antebrazo", "B\xEDceps"],
      [],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_martillo_cuerda_polea",
      "Curl Martillo con Cuerda en Polea",
      "Curl martillo en polea con cuerda. Tensi\xF3n constante.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["B\xEDceps:braquial", "Antebrazo", "B\xEDceps"],
      [],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_predicador_barra_ez",
      "Curl Predicador con Barra EZ",
      "Curl en banco predicador. Brazo apoyado reduce impulso. Buen aislamiento.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_predicador_maquina",
      "Curl Predicador en M\xE1quina",
      "Curl predicador en m\xE1quina. Estabilidad m\xE1xima.",
      "M\xE1quina",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_inclinado_mancuernas",
      "Curl Inclinado con Mancuernas",
      "Curl en banco inclinado. Mayor estiramiento del b\xEDceps en la posici\xF3n inicial.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_arana_barra",
      "Curl Ara\xF1a con Barra",
      "Curl sobre banco inclinado con brazos colgando. Aislamiento extremo del b\xEDceps.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_concentrado_mancuerna",
      "Curl Concentrado con Mancuerna",
      "Curl con codo apoyado contra el muslo. Aislamiento cl\xE1sico del b\xEDceps.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["B\xEDceps"],
      ["B\xEDceps:braquial"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_biceps_polea_baja",
      "Curl de B\xEDceps en Polea Baja",
      "Curl en polea baja. Tensi\xF3n constante en todo el rango.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_biceps_polea_alta",
      "Curl de B\xEDceps en Polea Alta",
      "Curl con poleas altas y barra o agarre. \xC1ngulo diferente de resistencia.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.4,
      2,
      0.6,
      1.8,
      ["B\xEDceps", "B\xEDceps:braquial"],
      ["Antebrazo"],
      [],
      "B\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_curl_invertido_barra",
      "Curl Invertido con Barra",
      "Curl con agarre prono. M\xE1ximo \xE9nfasis en braquiorradial y braquial.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Antebrazo", "B\xEDceps:braquial"],
      ["B\xEDceps"],
      [],
      "Antebrazo",
      "upper"
    ),
    // --- EXTENSIÓN DE CODO (tríceps) ---
    mk(
      "tren_superior_press_frances_barra_ez",
      "Press Franc\xE9s Tumbado con Barra EZ",
      "Extensi\xF3n de codo tumbado con barra EZ. Aislamiento del tr\xEDceps.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      3.2,
      2.8,
      0.8,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_press_frances_mancuernas",
      "Press Franc\xE9s Tumbado con Mancuernas",
      "Press franc\xE9s con mancuernas. Mayor rango y libertad que con barra.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      3,
      2.6,
      0.7,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_extension_triceps_polea_cuerda",
      "Extensi\xF3n de Tr\xEDceps en Polea Alta con Cuerda",
      "Extensiones en polea alta con agarre de cuerda. Extensi\xF3n completa del tr\xEDceps.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_extension_triceps_polea_barra",
      "Extensi\xF3n de Tr\xEDceps en Polea Alta con Barra",
      "Extensiones en polea alta con barra recta o EZ.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_extension_triceps_agarre_inverso",
      "Extensi\xF3n de Tr\xEDceps en Polea con Agarre Inverso",
      "Extensiones con agarre inverso. Variante que enfatiza la porci\xF3n medial.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_extension_tras_nuca_polea_baja",
      "Extensi\xF3n tras Nuca en Polea Baja",
      "Extensiones tras nuca con polea baja. Rango de movimiento \xFAnico.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_extension_tras_nuca_mancuerna",
      "Extensi\xF3n tras Nuca con una Mancuerna",
      "Extensiones tras nuca con mancuerna a dos manos.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_extension_tras_nuca_unilateral",
      "Extensi\xF3n tras Nuca Unilateral",
      "Extensiones tras nuca a una mano. Trabajo unilateral del tr\xEDceps.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_patada_triceps_mancuerna",
      "Patada de Tr\xEDceps con Mancuerna",
      "Patada de tr\xEDceps unilateral con mancuerna. Extensi\xF3n de codo con brazo estabilizado.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_patada_triceps_polea",
      "Patada de Tr\xEDceps en Polea",
      "Patada de tr\xEDceps en polea. Tensi\xF3n constante.",
      "Polea",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.6,
      2.2,
      0.6,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    mk(
      "tren_superior_tate_press_mancuernas",
      "Tate Press con Mancuernas",
      "Tate press: extensi\xF3n con mancuernas y codos separados. \xC9nfasis en tr\xEDceps.",
      "Mancuerna",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      2.8,
      2.4,
      0.7,
      1.8,
      ["Tr\xEDceps"],
      [],
      [],
      "Tr\xEDceps",
      "upper"
    ),
    // --- FLEXIÓN DE MUÑECA ---
    mk(
      "tren_superior_curl_muneca_supinacion",
      "Curl de Mu\xF1eca en Supinaci\xF3n",
      "Flexi\xF3n de mu\xF1eca con antebrazo en supinaci\xF3n. Flexores de la mu\xF1eca.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Flexi\xF3n",
      1.8,
      1.5,
      0.4,
      1.2,
      ["Antebrazo"],
      ["Antebrazo"],
      [],
      "Antebrazo",
      "upper"
    ),
    // --- EXTENSIÓN DE MUÑECA ---
    mk(
      "tren_superior_curl_muneca_pronacion",
      "Curl de Mu\xF1eca en Pronaci\xF3n",
      "Extensi\xF3n de mu\xF1eca con antebrazo en pronaci\xF3n. Extensores de la mu\xF1eca.",
      "Barra",
      "Accesorio",
      "Hipertrofia",
      "Extensi\xF3n",
      1.8,
      1.5,
      0.4,
      1.2,
      ["Antebrazo"],
      [],
      [],
      "Antebrazo",
      "upper"
    ),
    // --- FLEXO-EXTENSIÓN (rodillo) ---
    mk(
      "tren_superior_rodillo_muneca",
      "Rodillo de Mu\xF1eca",
      "Ejercicio con rodillo para antebrazos. Flexi\xF3n y extensi\xF3n de mu\xF1eca con resistencia.",
      "Rodillo",
      "Accesorio",
      "Hipertrofia",
      "Otro",
      2,
      1.6,
      0.5,
      1.4,
      ["Antebrazo"],
      [],
      [],
      "Antebrazo",
      "upper"
    )
  ];
  var ULTIMO_LOTE_EXERCISES = [
    // --- Empuje Horizontal ---
    mk("ultimo_flexiones_deficit", "Flexiones en D\xE9ficit", "Flexiones con manos elevadas para mayor rango. Pectoral, tr\xEDceps y deltoides.", "Peso Corporal", "Accesorio", "Hipertrofia", "Empuje", 2.5, 2, 0.1, 2.2, ["Pectorales"], ["Tr\xEDceps", "Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_muscle_up_anillas", "Muscle Up en Anillas", "Transici\xF3n de dominada a fondo en anillas. Potencia y coordinaci\xF3n.", "Peso Corporal", "B\xE1sico", "Potencia", "Tir\xF3n", 4.5, 4.8, 0.5, 4, ["Dorsales", "Pectorales", "B\xEDceps", "Tr\xEDceps"], [], ["Core", "Antebrazo"], "Dorsales", "upper"),
    mk("ultimo_flexiones_pino_hspu", "Flexiones Haciendo el Pino (HSPU)", "Press de hombros en posici\xF3n de pino. Deltoides y tr\xEDceps.", "Peso Corporal", "B\xE1sico", "Fuerza", "Empuje", 4, 4.5, 1.8, 3.5, ["Deltoides:anterior", "Deltoides:medio"], ["Tr\xEDceps"], ["Core", "Trapecio"], "Deltoides", "upper"),
    mk("ultimo_paseo_granjero_mancuernas", "Paseo del Granjero con Mancuernas", "Caminar sosteniendo mancuernas a los lados. Antebrazo, trapecio y core.", "Mancuerna", "B\xE1sico", "Fuerza", "Otro", 4.5, 4, 2.5, 2.5, ["Antebrazo", "Trapecio"], ["Core"], ["Cu\xE1driceps"], "Trapecio", "full"),
    mk("ultimo_paseo_granjero_unilateral", "Paseo del Granjero Unilateral", "Caminar con una mancuerna. Estabilidad de core y correcci\xF3n de desbalances.", "Mancuerna", "Accesorio", "Estabilidad", "Otro", 3.8, 3.5, 1.5, 2, ["Core", "Antebrazo"], ["Trapecio"], ["Gl\xFAteos:medio"], "Core", "full"),
    mk("ultimo_turkish_get_up", "Levantamiento Turco (Turkish Get Up)", "Secuencia compleja con kettlebell. Core, hombros y movilidad.", "Kettlebell", "B\xE1sico", "Estabilidad", "Otro", 3.5, 4.5, 1.5, 2, ["Core", "Deltoides:anterior"], ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Pectorales", "Isquiosurales"], "Core", "full"),
    // --- Sentadilla Unilateral ---
    mk("ultimo_zancadas_zercher_barra", "Zancadas Zercher con Barra", "Zancadas con barra en flexi\xF3n de codos. Cu\xE1driceps y gl\xFAteos.", "Barra", "Accesorio", "Fuerza", "Sentadilla", 4, 4.2, 1.5, 2.6, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Core"], ["Antebrazo", "B\xEDceps"], "Cu\xE1driceps", "lower"),
    mk("ultimo_buenos_dias_zercher", "Buenos D\xEDas Zercher", "Bisagra de cadera con barra Zercher. Isquios y gl\xFAteos.", "Barra", "Accesorio", "Hipertrofia", "Bisagra", 3.8, 3.8, 1.6, 2.5, ["Isquiosurales", "Gl\xFAteos:mayor"], ["Core"], ["B\xEDceps"], "Isquiosurales", "lower"),
    // --- Abducción / Extensión Hombro ---
    mk("ultimo_elevaciones_y_polea", "Elevaciones en y con Polea Baja", "Abducci\xF3n en Y para trapecio inferior y deltoides medio.", "Polea", "Aislamiento", "Movilidad", "Otro", 1.8, 1.6, 0.1, 1.5, ["Trapecio:inferior", "Deltoides:medio"], ["Deltoides:posterior"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_pullover_polea_cuerda", "Pullover en Polea Alta con Cuerda", "Extensi\xF3n de hombro en polea. Dorsal y tr\xEDceps cabeza larga.", "Polea", "Aislamiento", "Hipertrofia", "Tir\xF3n", 2, 1.8, 0.2, 1.6, ["Dorsales", "Tr\xEDceps"], [], ["Core"], "Dorsales", "upper"),
    mk("ultimo_puente_gluteo_1_pierna", "Puente de Gl\xFAteo a 1 Pierna", "Empuje de cadera unilateral. Gl\xFAteo mayor e isquios.", "Peso Corporal", "Accesorio", "Hipertrofia", "Extensi\xF3n", 2.5, 2.5, 0.1, 1.5, ["Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core"], "Gl\xFAteos", "lower"),
    // --- Rotación Tronco / Core ---
    mk("ultimo_lenador_polea", "Le\xF1ador en Polea Alta (Woodchopper)", "Rotaci\xF3n de tronco con polea. Core y potencia.", "Polea", "Accesorio", "Potencia", "Rotaci\xF3n", 2.8, 3, 0.8, 1.8, ["Core"], ["Deltoides"], ["Gl\xFAteos:mayor"], "Core", "full"),
    mk("ultimo_giros_rusos_disco", "Giros Rusos con Disco", "Rotaci\xF3n de tronco con disco. Core y resistencia.", "Disco", "Accesorio", "Resistencia", "Rotaci\xF3n", 2.2, 2, 1, 1.5, ["Core"], ["Core"], [], "Core", "full"),
    mk("ultimo_crunch_polea_alta", "Crunch Abdominal en Polea Alta", "Flexi\xF3n de tronco contra polea. Core.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 2, 1.8, 0.8, 1.2, ["Core"], [], [], "Core", "full"),
    mk("ultimo_elevacion_piernas_paralelas", "Elevaci\xF3n de Piernas en Paralelas", "Elevaci\xF3n de piernas colgado. Core y psoas.", "M\xE1quina", "Accesorio", "Hipertrofia", "Flexi\xF3n", 2.5, 2.2, 0.4, 1.5, ["Core"], [], ["Dorsales", "Tr\xEDceps"], "Core", "full"),
    mk("ultimo_plancha_frontal", "Plancha Frontal (Plank)", "Isom\xE9trico anti-extensi\xF3n. Core y estabilidad.", "Peso Corporal", "Accesorio", "Resistencia", "Anti-Extensi\xF3n", 2, 1.8, 0.5, 1, ["Core"], ["Deltoides:anterior"], ["Gl\xFAteos:mayor", "Cu\xE1driceps"], "Core", "full"),
    mk("ultimo_bicho_muerto", "Bicho Muerto (Dead Bug)", "Anti-extensi\xF3n din\xE1mico. Core y estabilidad.", "Peso Corporal", "Accesorio", "Estabilidad", "Anti-Extensi\xF3n", 1.8, 2, 0.2, 1, ["Core"], ["Core"], [], "Core", "full"),
    // --- Sentadilla / Tren inferior ---
    mk("ultimo_sentadilla_hack_invertida", "Sentadilla Hack Invertida", "Sentadilla en m\xE1quina hack invertida. Cu\xE1driceps y gl\xFAteos.", "M\xE1quina", "Accesorio", "Hipertrofia", "Sentadilla", 3.8, 3.2, 0.8, 2, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_elevacion_tibial_polea", "Elevaci\xF3n Tibial en Polea Baja", "Flexi\xF3n dorsal de tobillo. Tibial anterior.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1.2, 0, 1.2, ["Pantorrillas"], [], ["Core"], "Pantorrillas", "lower"),
    mk("ultimo_paseo_granjero_barra_trampa", "Paseo del Granjero con Barra Trampa", "Caminar con barra hexagonal. Trapecio y antebrazo.", "Barra", "B\xE1sico", "Fuerza", "Empuje", 4.8, 4.5, 2.2, 2.8, ["Trapecio", "Antebrazo"], ["Core"], ["Gl\xFAteos:mayor"], "Trapecio", "full"),
    // --- Tracción Horizontal ---
    mk("ultimo_remo_punta_tbar_apoyo", "Remo en Punta (T-Bar) con Apoyo al Pecho", "Remo en barra T con pecho apoyado. Dorsal y romboides.", "M\xE1quina", "Accesorio", "Hipertrofia", "Tir\xF3n", 2.8, 2.2, 0.1, 1.6, ["Dorsales"], ["Deltoides:posterior", "B\xEDceps"], [], "Dorsales", "upper"),
    // --- Empuje Horizontal ---
    mk("ultimo_press_banca_agarre_inverso", "Press de Banca con Agarre Inverso", "Press con agarre supino. Pectoral superior y tr\xEDceps.", "Barra", "Accesorio", "Hipertrofia", "Empuje", 3.2, 3.5, 0.2, 2.4, ["Pectorales:superior", "Tr\xEDceps"], ["Deltoides:anterior"], ["Dorsales", "Antebrazo"], "Pectorales", "upper"),
    mk("ultimo_hex_press_mancuernas", "Hex Press con Mancuernas", "Press con mancuernas comprimidas. Pectoral y tr\xEDceps.", "Mancuerna", "Aislamiento", "Hipertrofia", "Empuje", 2.2, 2, 0.1, 1.8, ["Pectorales"], ["Deltoides:anterior", "Tr\xEDceps"], ["Antebrazo"], "Pectorales", "upper"),
    // --- Aducción ---
    mk("ultimo_cruces_polea_baja_ascendentes", "Cruces en Polea Baja (Ascendentes)", "Aperturas con poleas bajas, trayectoria ascendente. Pectoral superior.", "Polea", "Aislamiento", "Hipertrofia", "Empuje", 1.8, 1.5, 0, 1.5, ["Pectorales:superior"], ["Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    // --- Tracción Vertical ---
    mk("ultimo_dominadas_agarre_comando", "Dominadas con Agarre Comando (Mixto)", "Dominadas alternando prono-supino. Dorsal y b\xEDceps.", "Peso Corporal", "Accesorio", "Fuerza", "Tir\xF3n", 3.8, 3.8, 0.2, 2.4, ["Dorsales"], ["B\xEDceps", "B\xEDceps:braquial"], ["Trapecio", "Core"], "Dorsales", "upper"),
    // --- Empuje Vertical ---
    mk("ultimo_press_savickas_mancuernas", "Press Savickas (Z-Press) con Mancuernas", "Press sentado en el suelo. Sin apoyo de espalda. Deltoides y tr\xEDceps.", "Mancuerna", "Accesorio", "Fuerza", "Empuje", 3.5, 3.8, 1.2, 2.2, ["Deltoides:anterior"], ["Tr\xEDceps"], ["Core"], "Deltoides", "upper"),
    // --- Tracción Horizontal ---
    mk("ultimo_remo_gironda_cuerda", "Remo Gironda con Cuerda", "Remo con cuerda en polea. Dorsal y romboides.", "Polea", "Accesorio", "Hipertrofia", "Tir\xF3n", 2.5, 2, 0.5, 1.5, ["Dorsales"], ["B\xEDceps", "Deltoides:posterior"], ["Core"], "Dorsales", "upper"),
    // --- Flexión / Extensión Codo ---
    mk("ultimo_curl_bayesian_polea", "Curl Bayesian en Polea Baja", "Curl con \xE9nfasis en cabeza larga del b\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.4, 0.1, 1.5, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_rompecraneos_polea", "Rompecr\xE1neos (Skullcrusher) en Polea", "Extensi\xF3n de tr\xEDceps en polea. Tr\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.8, 1.5, 0.1, 1.8, ["Tr\xEDceps"], ["Deltoides:anterior"], ["Core"], "Tr\xEDceps", "upper"),
    // --- Flexión Plantar ---
    mk("ultimo_elevacion_gemelos_burro", "Elevaci\xF3n de Gemelos Tipo Burro (Donkey)", "Flexi\xF3n plantar en m\xE1quina tipo burro. Gemelos.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2.5, 2, 0.5, 2.2, ["Pantorrillas:gastrocnemio"], ["Pantorrillas:s\xF3leo"], ["Core"], "Pantorrillas", "lower"),
    // --- Sentadilla / Bisagra / Empuje cadera ---
    mk("ultimo_sentadilla_copa_disco", "Sentadilla en Copa con Disco", "Sentadilla goblet con disco. Cu\xE1driceps y gl\xFAteos.", "Disco", "Accesorio", "Hipertrofia", "Sentadilla", 2.5, 2, 0.5, 1.8, ["Cu\xE1driceps"], ["Gl\xFAteos:mayor"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_sentadilla_anderson", "Sentadilla Anderson (Desde Pines)", "Sentadilla iniciando desde pines bajos. Sin rebote.", "Barra", "B\xE1sico", "Fuerza", "Sentadilla", 4.8, 4.8, 1.8, 2.5, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_sentadilla_espanola_banda", "Sentadilla Espa\xF1ola", "Sentadilla con banda de resistencia. Cu\xE1driceps.", "Banda", "Accesorio", "Hipertrofia", "Sentadilla", 2.8, 2, 0.2, 2, ["Cu\xE1driceps"], ["Gl\xFAteos:mayor"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_zancadas_pendulares_mancuernas", "Zancadas Pendulares con Mancuernas", "Zancadas alternando piernas. Cu\xE1driceps y gl\xFAteos.", "Mancuerna", "Accesorio", "Hipertrofia", "Sentadilla", 3.8, 3.5, 0.6, 2.6, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core", "Antebrazo"], "Cu\xE1driceps", "lower"),
    mk("ultimo_subidas_laterales_cajon", "Subidas Laterales al Caj\xF3n", "Subida lateral unilateral con mancuernas.", "Mancuerna", "Accesorio", "Hipertrofia", "Sentadilla", 3.2, 3, 0.4, 2.2, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Gl\xFAteos:medio"], ["Core", "Antebrazo"], "Cu\xE1driceps", "lower"),
    mk("ultimo_peso_muerto_rumano_barra_trampa", "Peso Muerto Rumano con Barra Trampa", "RDL con barra hexagonal. Isquios y gl\xFAteos.", "Barra", "B\xE1sico", "Fuerza", "Bisagra", 4.5, 4, 1.6, 2.8, ["Isquiosurales", "Gl\xFAteos:mayor"], ["Cu\xE1driceps"], ["Core", "Antebrazo"], "Isquiosurales", "lower"),
    mk("ultimo_peso_muerto_bandas", "Peso Muerto con Bandas El\xE1sticas", "Deadlift con bandas. Gl\xFAteos e isquios.", "Banda", "Accesorio", "Potencia", "Bisagra", 3.5, 3.8, 1, 2, ["Gl\xFAteos:mayor", "Isquiosurales"], ["Cu\xE1driceps"], ["Core"], "Gl\xFAteos", "lower"),
    mk("ultimo_hip_thrust_1_pierna_mancuerna", "Hip Thrust a una Pierna con Mancuerna", "Empuje de cadera unilateral. Gl\xFAteo mayor.", "Mancuerna", "Accesorio", "Hipertrofia", "Extensi\xF3n", 2.8, 2.5, 0.1, 1.6, ["Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core"], "Gl\xFAteos", "lower"),
    mk("ultimo_curl_femoral_deslizante", "Curl Femoral Deslizante (Sliders)", "Curl de isquios con sliders. Isquiosurales.", "Slider", "Accesorio", "Hipertrofia", "Flexi\xF3n", 2.5, 2, 0.1, 2, ["Isquiosurales"], ["Pantorrillas:gastrocnemio"], ["Core"], "Isquiosurales", "lower"),
    mk("ultimo_extensiones_cuadriceps_banda", "Extensiones de Cu\xE1driceps con Banda", "Extensi\xF3n de rodilla con banda. Cu\xE1driceps.", "Banda", "Aislamiento", "Resistencia", "Extensi\xF3n", 1.5, 1.2, 0, 1.2, ["Cu\xE1driceps"], [], [], "Cu\xE1driceps", "lower"),
    mk("ultimo_elevacion_talones_excentrica_1_pierna", "Elevaci\xF3n de Talones Exc\xE9ntrica a 1 Pierna", "Calf raise exc\xE9ntrico unilateral. Gemelos.", "Peso Corporal", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.8, 1.5, 0, 1.8, ["Pantorrillas:gastrocnemio"], ["Pantorrillas:s\xF3leo"], [], "Pantorrillas", "lower"),
    mk("ultimo_abduccion_cadera_banda_sentado", "Abducci\xF3n de Cadera con Banda Sentado", "Abducci\xF3n de cadera en silla. Gl\xFAteo medio.", "Banda", "Aislamiento", "Resistencia", "Otro", 1.5, 1.2, 0, 0.8, ["Gl\xFAteos:medio"], ["Gl\xFAteos:medio"], [], "Gl\xFAteos", "lower"),
    // --- Más empuje horizontal / vertical / tracción ---
    mk("ultimo_press_banca_tabla", "Press de Banca con Tabla (Board Press)", "Press con tabla que limita el recorrido. Tr\xEDceps y pectoral.", "Barra", "Accesorio", "Fuerza", "Empuje", 3.8, 3.8, 0.3, 2.4, ["Tr\xEDceps", "Pectorales"], ["Deltoides:anterior"], ["Dorsales", "Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_press_banca_suelo_puente", "Press de Banca en Suelo con Puente", "Floor press con arco lumbar. Pectoral y tr\xEDceps.", "Barra", "Accesorio", "Fuerza", "Empuje", 3.6, 3.5, 0.5, 2.2, ["Pectorales"], ["Tr\xEDceps", "Deltoides:anterior"], ["Core", "Gl\xFAteos:mayor"], "Pectorales", "upper"),
    mk("ultimo_press_larsen", "Press Larsen", "Press de banca sin apoyo de pies. Sin arco.", "Barra", "Accesorio", "Fuerza", "Empuje", 3.5, 3.8, 0.1, 2.5, ["Pectorales"], ["Tr\xEDceps", "Deltoides:anterior"], [], "Pectorales", "upper"),
    mk("ultimo_flexiones_arqueras", "Flexiones Arqueras", "Flexiones con desplazamiento lateral. Pectoral unilateral.", "Peso Corporal", "Accesorio", "Hipertrofia", "Empuje", 2.8, 2.5, 0.1, 2.2, ["Pectorales"], ["Tr\xEDceps", "Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_press_svend_discos", "Press Svend con Discos", "Compresi\xF3n de discos entre palmas. Pectoral.", "Disco", "Aislamiento", "Hipertrofia", "Empuje", 1.8, 1.5, 0, 1.2, ["Pectorales"], ["Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_cruces_polea_media", "Cruces de Polea Media", "Aperturas con poleas a media altura. Pectoral.", "Polea", "Aislamiento", "Hipertrofia", "Empuje", 1.8, 1.5, 0, 1.5, ["Pectorales"], ["Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_press_militar_1_brazo_barra", "Press Militar a un Brazo con Barra", "OHP unilateral con barra. Estabilidad.", "Barra", "Accesorio", "Estabilidad", "Empuje", 3.5, 4, 1.2, 2.4, ["Deltoides:anterior", "Deltoides:medio"], ["Tr\xEDceps"], ["Core", "Antebrazo"], "Deltoides", "upper"),
    mk("ultimo_press_hombros_cubano", "Press de Hombros Cubano", "Press con rotaci\xF3n para movilidad. Deltoides posterior.", "Mancuerna", "Accesorio", "Movilidad", "Empuje", 2, 2.2, 0.4, 2, ["Deltoides:posterior"], ["Trapecio"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_elevaciones_laterales_recostado", "Elevaciones Laterales Recostado", "Lateral raise tumbado de lado. Deltoides medio.", "Mancuerna", "Aislamiento", "Hipertrofia", "Otro", 1.6, 1.5, 0, 1.2, ["Deltoides:medio"], ["Trapecio"], [], "Deltoides", "upper"),
    mk("ultimo_dominadas_excentricas", "Dominadas Exc\xE9ntricas", "Dominadas enfatizando la fase negativa.", "Peso Corporal", "Accesorio", "Fuerza", "Tir\xF3n", 3.5, 3.5, 0.2, 3, ["Dorsales"], ["B\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_dominadas_escapulares", "Dominadas Escapulares", "Depresi\xF3n escapular colgado. Trapecio y romboides.", "Peso Corporal", "Accesorio", "Movilidad", "Tir\xF3n", 2, 2, 0.2, 1.8, ["Trapecio", "Dorsales"], ["Dorsales"], ["Core", "Antebrazo"], "Trapecio", "upper"),
    mk("ultimo_jalon_pecho_brazo_recto_unilateral", "Jal\xF3n al Pecho con Brazo Recto Unilateral", "Pullover unilateral en polea. Dorsal.", "Polea", "Aislamiento", "Hipertrofia", "Tir\xF3n", 1.8, 1.5, 0.1, 1.2, ["Dorsales"], ["Tr\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_remo_helms_mancuernas", "Remo Helms con Mancuernas", "Remo con pecho apoyado. Dorsal y romboides.", "Mancuerna", "Accesorio", "Hipertrofia", "Tir\xF3n", 2.8, 2.5, 0.1, 1.8, ["Dorsales"], ["B\xEDceps", "Deltoides:posterior"], [], "Dorsales", "upper"),
    mk("ultimo_remo_barra_esquina_1_brazo", "Remo con Barra en Esquina a un Brazo", "Remo unilateral con barra en esquina.", "Barra", "Accesorio", "Hipertrofia", "Tir\xF3n", 3, 2.8, 0.5, 1.8, ["Dorsales"], ["B\xEDceps", "Dorsales"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_remo_renegado_mancuernas", "Remo Renegado con Mancuernas", "Remo con apoyo a una mano. Estabilidad de core.", "Mancuerna", "Accesorio", "Estabilidad", "Tir\xF3n", 3.2, 3.5, 0.6, 2, ["Dorsales", "Core"], ["Dorsales", "B\xEDceps"], ["Pectorales", "Tr\xEDceps"], "Dorsales", "upper"),
    mk("ultimo_face_pull_sentado_polea", "Face Pull Sentado en Polea", "Rotaci\xF3n externa sentado. Deltoides posterior.", "Polea", "Accesorio", "Hipertrofia", "Rotaci\xF3n", 1.6, 1.5, 0, 1.2, ["Deltoides:posterior"], ["Trapecio", "Dorsales"], [], "Deltoides", "upper"),
    mk("ultimo_pajaros_polea_alta", "P\xE1jaros en Polea Alta", "Abducci\xF3n horizontal con polea alta. Deltoides posterior.", "Polea", "Aislamiento", "Hipertrofia", "Tir\xF3n", 1.5, 1.4, 0, 1.2, ["Deltoides:posterior"], ["Dorsales"], ["Core"], "Deltoides", "upper"),
    // --- Curls, extensiones, más variantes ---
    mk("ultimo_curl_biceps_21s", "Curl de B\xEDceps 21s", "Curl en 3 fases de 7 repeticiones. B\xEDceps.", "Barra", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 2.2, 1.8, 0.2, 2, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_curl_arana_mancuernas", "Curl Ara\xF1a con Mancuernas", "Curl sobre banco inclinado con mancuernas. B\xEDceps.", "Mancuerna", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.8, 1.5, 0, 1.8, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Antebrazo"], "B\xEDceps", "upper"),
    mk("ultimo_curl_cruzado_mancuernas", "Curl de B\xEDceps Cruzado", "Curl cruzando el cuerpo. Braquial.", "Mancuerna", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.5, 0.1, 1.4, ["B\xEDceps:braquial"], ["B\xEDceps"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_press_frances_declinado_ez", "Press Franc\xE9s Declinado con Barra EZ", "Skullcrusher en banco declinado. Tr\xEDceps.", "Barra", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2, 1.8, 0, 2.2, ["Tr\xEDceps"], ["Deltoides:anterior"], [], "Tr\xEDceps", "upper"),
    mk("ultimo_extensiones_triceps_rodando_suelo", "Extensiones de Tr\xEDceps Rodando en Suelo", "Extensi\xF3n de tr\xEDceps con mancuernas rodando. Tr\xEDceps.", "Mancuerna", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2.2, 2, 0.1, 2.4, ["Tr\xEDceps"], [], [], "Tr\xEDceps", "upper"),
    mk("ultimo_flexiones_diamante_pared", "Flexiones Diamante en Pared", "Flexiones diamante contra pared. Tr\xEDceps y pectoral.", "Peso Corporal", "Aislamiento", "Resistencia", "Empuje", 1.2, 1, 0, 1, ["Tr\xEDceps", "Pectorales"], ["Deltoides:anterior"], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_paseo_camarero", "Paseo del Camarero", "Caminar con mancuerna sobre el hombro. Estabilidad.", "Mancuerna", "Accesorio", "Estabilidad", "Otro", 3, 3.5, 1, 2, ["Deltoides:anterior", "Core"], ["Trapecio"], ["Tr\xEDceps"], "Core", "full"),
    mk("ultimo_marcha_overhead_unilateral", "Marcha Overhead Unilateral", "Caminar con kettlebell overhead. Estabilidad.", "Kettlebell", "Accesorio", "Estabilidad", "Otro", 3.5, 3.8, 1.2, 2.2, ["Deltoides:anterior", "Core"], ["Trapecio"], ["Tr\xEDceps"], "Core", "full"),
    mk("ultimo_rotacion_externa_polea", "Rotaci\xF3n Externa de Hombro en Polea Baja", "Rotaci\xF3n externa para manguito. Movilidad.", "Polea", "Aislamiento", "Movilidad", "Rotaci\xF3n", 1.2, 1.5, 0, 1.2, [], ["Deltoides:posterior"], [], "Deltoides", "upper"),
    mk("ultimo_rotacion_interna_polea", "Rotaci\xF3n Interna de Hombro en Polea Baja", "Rotaci\xF3n interna para manguito. Movilidad.", "Polea", "Aislamiento", "Movilidad", "Rotaci\xF3n", 1.2, 1.5, 0, 1.2, [], ["Pectorales"], [], "Deltoides", "upper"),
    mk("ultimo_flexion_cuello_arnes", "Flexi\xF3n de Cuello con Arn\xE9s", "Flexi\xF3n cervical con arn\xE9s. Cuello.", "Arn\xE9s", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1.2, 0.5, 1.2, [], [], [], "Trapecio", "upper"),
    mk("ultimo_extension_cuello_arnes", "Extensi\xF3n de Cuello con Arn\xE9s", "Extensi\xF3n cervical con arn\xE9s. Cuello y trapecio.", "Arn\xE9s", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.5, 1.2, 0.5, 1.2, ["Trapecio"], [], [], "Trapecio", "upper"),
    mk("ultimo_encogimientos_tras_nuca", "Encogimientos de Hombros por Detr\xE1s", "Shrugs con barra tras nuca. Trapecio.", "Barra", "Aislamiento", "Hipertrofia", "Empuje", 2.4, 2, 1.2, 1.8, ["Trapecio"], [], ["Core", "Antebrazo"], "Trapecio", "upper"),
    mk("ultimo_curl_muneca_tras_espalda", "Curl de Mu\xF1eca por Detr\xE1s de la Espalda", "Flexi\xF3n de mu\xF1eca con barra tras espalda. Antebrazo.", "Barra", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.2, 1, 0, 1.2, ["Antebrazo"], [], [], "Antebrazo", "upper"),
    mk("ultimo_paseo_oso", "Paseo del Oso", "Locomoci\xF3n en cuadrupedia. Core y hombros.", "Peso Corporal", "B\xE1sico", "Resistencia", "Otro", 3.5, 3.5, 0.8, 2.5, ["Core", "Deltoides:anterior"], ["Cu\xE1driceps", "Pectorales"], ["Tr\xEDceps"], "Core", "full"),
    mk("ultimo_plancha_lateral", "Plancha Lateral", "Anti-flexi\xF3n lateral. Core y oblicuos.", "Peso Corporal", "Accesorio", "Resistencia", "Anti-Flexi\xF3n", 1.8, 1.8, 0.2, 1, ["Core"], ["Deltoides:anterior"], ["Gl\xFAteos:medio"], "Core", "full"),
    mk("ultimo_plancha_toque_hombros", "Plancha con Toque de Hombros", "Plancha con rotaci\xF3n de hombros. Estabilidad.", "Peso Corporal", "Accesorio", "Estabilidad", "Anti-Rotaci\xF3n", 2.2, 2.5, 0.3, 1.5, ["Core", "Deltoides:anterior"], ["Pectorales"], ["Gl\xFAteos:mayor"], "Core", "full"),
    mk("ultimo_abdominales_v", "Abdominales en V (V-ups)", "Flexi\xF3n de tronco en V. Core y psoas.", "Peso Corporal", "Accesorio", "Hipertrofia", "Flexi\xF3n", 2, 2, 0.5, 1.2, ["Core"], ["Cu\xE1driceps"], [], "Core", "full"),
    mk("ultimo_elevacion_rodillas_colgado", "Elevaci\xF3n de Rodillas Colgado", "Knee raise colgado. Core y psoas.", "Peso Corporal", "Accesorio", "Hipertrofia", "Flexi\xF3n", 2.5, 2.2, 0.4, 1.4, ["Core"], ["Antebrazo"], ["Dorsales"], "Core", "full"),
    mk("ultimo_hiperextensiones_inversas", "Hiperextensiones Inversas en Banco Plano", "Extensi\xF3n de tronco boca abajo. Gl\xFAteos y core.", "Peso Corporal", "Accesorio", "Hipertrofia", "Extensi\xF3n", 2, 1.8, 0.2, 1.2, ["Gl\xFAteos:mayor", "Core"], ["Isquiosurales"], [], "Gl\xFAteos", "full"),
    mk("ultimo_superman_suelo", "Superman en el Suelo", "Extensi\xF3n de espalda isom\xE9trica. Core y gl\xFAteos.", "Peso Corporal", "Accesorio", "Resistencia", "Extensi\xF3n", 1.5, 1.5, 0.5, 1, ["Core", "Gl\xFAteos:mayor"], ["Trapecio"], [], "Core", "full"),
    mk("ultimo_lanzamiento_balon_abajo", "Lanzamiento de Bal\xF3n Medicinal Hacia Abajo", "Potencia core. Lanzar bal\xF3n al suelo.", "Bal\xF3n Medicinal", "B\xE1sico", "Potencia", "Otro", 3.5, 4, 0.5, 2.5, ["Dorsales", "Core"], ["Pectorales", "Tr\xEDceps"], ["Cu\xE1driceps"], "Core", "full"),
    mk("ultimo_lanzamiento_rotacional_balon", "Lanzamiento Rotacional de Bal\xF3n a Pared", "Rotaci\xF3n explosiva con bal\xF3n. Core y deltoides.", "Bal\xF3n Medicinal", "Accesorio", "Potencia", "Rotaci\xF3n", 3.2, 3.8, 0.6, 2.2, ["Core", "Deltoides:anterior"], ["Pectorales"], ["Gl\xFAteos:mayor"], "Core", "full"),
    mk("ultimo_salto_cuclillas", "Salto en Cuclillas (Squat Jump)", "Triple extensi\xF3n. Potencia de piernas.", "Peso Corporal", "Accesorio", "Potencia", "Salto", 3, 3.5, 0.8, 3.5, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Pantorrillas:gastrocnemio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_salto_tijera", "Salto de Tijera (Split Jump)", "Salto alternando piernas. Potencia.", "Peso Corporal", "Accesorio", "Potencia", "Salto", 3.2, 3.8, 0.6, 3.5, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Pantorrillas:gastrocnemio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_sissy_squat_disco", "P\xE9ndulo Sissy con Disco", "Sissy squat con disco. Cu\xE1driceps.", "Disco", "Accesorio", "Hipertrofia", "Sentadilla", 2.8, 2.2, 0.2, 3.6, ["Cu\xE1driceps"], [], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_remo_ergometro", "Remo en Erg\xF3metro", "Remo en m\xE1quina de remo. Resistencia full body.", "M\xE1quina", "B\xE1sico", "Resistencia", "Tir\xF3n", 4, 3.5, 1, 2, ["Dorsales", "Cu\xE1driceps"], ["Core", "Isquiosurales"], ["Antebrazo", "B\xEDceps"], "Dorsales", "full"),
    mk("ultimo_sentadilla_trx", "Sentadilla con Salto en TRX", "Sentadilla con TRX para asistencia. Potencia.", "TRX", "Accesorio", "Potencia", "Sentadilla", 2.8, 3, 0.2, 2.5, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Pantorrillas:gastrocnemio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_traccion_facial_trx", "Tracci\xF3n Facial con TRX", "Face pull con TRX. Deltoides posterior.", "TRX", "Accesorio", "Movilidad", "Rotaci\xF3n", 1.8, 1.6, 0.1, 1.2, ["Deltoides:posterior"], ["Trapecio"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_dominadas_asistidas_pausa", "Dominadas Asistidas en M\xE1quina con Pausa en Estiramiento", "Dominadas asistidas con pausa abajo. Dorsal.", "M\xE1quina", "Accesorio", "Hipertrofia", "Tir\xF3n", 2.5, 2, 0, 2, ["Dorsales"], ["B\xEDceps"], [], "Dorsales", "upper"),
    mk("ultimo_press_banca_deficit_mancuernas", "Press de Banca con Mancuernas en D\xE9ficit", "Press en banco estrecho para mayor rango. Pectoral.", "Mancuerna", "Accesorio", "Hipertrofia", "Empuje", 3.5, 3.2, 0.2, 2.8, ["Pectorales"], ["Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_press_pecho_convergente_excentrico", "Press de Pecho en M\xE1quina Convergente (\xC9nfasis Exc\xE9ntrico)", "Press convergente con \xE9nfasis en negativa. Pectoral.", "M\xE1quina", "Accesorio", "Hipertrofia", "Empuje", 2.8, 2.2, 0, 2, ["Pectorales"], ["Tr\xEDceps"], [], "Pectorales", "upper"),
    mk("ultimo_cruces_pecho_cuffs", "Cruces de Pecho en Polea con Cuffs", "Aperturas con cuffs en polea. Pectoral.", "Polea", "Aislamiento", "Hipertrofia", "Empuje", 1.8, 1.4, 0, 1.8, ["Pectorales"], [], ["Core"], "Pectorales", "upper"),
    mk("ultimo_aperturas_planas_cables", "Aperturas Planas con Cables y Banco", "Cable flyes en banco plano. Pectoral.", "Polea", "Aislamiento", "Hipertrofia", "Empuje", 1.8, 1.5, 0, 2, ["Pectorales"], ["Deltoides:anterior"], [], "Pectorales", "upper"),
    mk("ultimo_press_hombros_smith_sin_respaldo", "Press de Hombros en M\xE1quina Smith Sentado sin Respaldo", "OHP en Smith sin apoyo. Deltoides y core.", "M\xE1quina", "Accesorio", "Fuerza", "Empuje", 3.4, 3, 1.2, 2, ["Deltoides:anterior"], ["Tr\xEDceps"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_elevaciones_frontales_polea_espalda", "Elevaciones Frontales en Polea Baja con Cuerda (De Espaldas)", "Flexi\xF3n de hombro con polea a la espalda. Deltoides anterior.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1.2, 0, 1.4, ["Deltoides:anterior"], ["Pectorales:superior"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_pajaros_polea_cruzada", "P\xE1jaros en Polea Cruzada Inversa", "Abducci\xF3n horizontal en polea cruzada. Deltoides posterior.", "Polea", "Aislamiento", "Hipertrofia", "Tir\xF3n", 1.5, 1.2, 0, 1.2, ["Deltoides:posterior"], ["Dorsales"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_pajaros_recostado_banco", "P\xE1jaros Recostado de Lado en Banco Inclinado", "Rear delt fly tumbado de lado. Deltoides posterior.", "Mancuerna", "Aislamiento", "Hipertrofia", "Tir\xF3n", 1.4, 1, 0, 1.2, ["Deltoides:posterior"], ["Trapecio"], [], "Deltoides", "upper"),
    mk("ultimo_sentadilla_bulgara_deficit", "Sentadilla B\xFAlgara en D\xE9ficit Profundo", "B\xFAlgaras en plataforma. Cu\xE1driceps y gl\xFAteos.", "Mancuerna", "Accesorio", "Hipertrofia", "Sentadilla", 4, 3.8, 0.6, 3, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Gl\xFAteos:medio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_sentadilla_hack_pausa", "Sentadilla Hack Profunda con Pausa Abajo", "Hack squat con pausa en el fondo. Cu\xE1driceps.", "M\xE1quina", "Accesorio", "Hipertrofia", "Sentadilla", 3.8, 3.2, 0.4, 3.2, ["Cu\xE1driceps"], ["Gl\xFAteos:mayor"], [], "Cu\xE1driceps", "lower"),
    mk("ultimo_sentadilla_smith_pies_adelantados", "Sentadilla en M\xE1quina Smith con Pies Adelantados", "Sentadilla Smith con pies adelante. Cu\xE1driceps.", "M\xE1quina", "Accesorio", "Hipertrofia", "Sentadilla", 3.5, 2.8, 0.5, 2.5, ["Cu\xE1driceps"], ["Gl\xFAteos:mayor"], [], "Cu\xE1driceps", "lower"),
    mk("ultimo_peso_muerto_rumano_deficit", "Peso Muerto Rumano en D\xE9ficit Profundo", "RDL desde plataforma. Mayor rango. Isquios.", "Barra", "Accesorio", "Hipertrofia", "Bisagra", 4.5, 4, 1.8, 3.5, ["Isquiosurales", "Gl\xFAteos:mayor"], ["Core"], ["Antebrazo"], "Isquiosurales", "lower"),
    mk("ultimo_hiperextension_45_gluteo", "Hiperextensi\xF3n 45 Grados Enfocada en Gl\xFAteo", "Hiperextensi\xF3n con espalda redonda. Gl\xFAteos.", "Peso Corporal", "Aislamiento", "Hipertrofia", "Bisagra", 2, 1.5, 0.2, 1.5, ["Gl\xFAteos:mayor"], ["Isquiosurales"], [], "Gl\xFAteos", "lower"),
    mk("ultimo_kas_glute_bridge_smith", "Kas Glute Bridge en M\xE1quina Smith", "Hip thrust en Smith. Gl\xFAteo mayor.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 3, 2.2, 0.1, 1.5, ["Gl\xFAteos:mayor"], [], [], "Gl\xFAteos", "lower"),
    mk("ultimo_hip_thrust_unilateral_multipower", "Hip Thrust Unilateral en M\xE1quina Multipower", "Hip thrust a una pierna en multipower. Gl\xFAteos.", "M\xE1quina", "Accesorio", "Hipertrofia", "Extensi\xF3n", 3, 2.5, 0.1, 1.6, ["Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core"], "Gl\xFAteos", "lower"),
    mk("ultimo_curl_femoral_inclinacion_frontal", "Curl Femoral Sentado con Inclinaci\xF3n Frontal M\xE1xima", "Curl de isquios en m\xE1quina inclinada. Isquios.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 2.8, 1.8, 0, 2.5, ["Isquiosurales"], [], [], "Isquiosurales", "lower"),
    mk("ultimo_curl_femoral_unilateral_pie", "Curl Femoral Unilateral de Pie en M\xE1quina", "Curl de isquios a una pierna. Isquios.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 2.2, 1.6, 0, 1.5, ["Isquiosurales"], [], ["Core"], "Isquiosurales", "lower"),
    mk("ultimo_extension_cuadriceps_unilateral_pausa", "Extensi\xF3n de Cu\xE1driceps Unilateral con Pausa Isom\xE9trica", "Extensi\xF3n de cu\xE1driceps con pausa. Cu\xE1driceps.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2.2, 1.5, 0, 2, ["Cu\xE1driceps"], [], [], "Cu\xE1driceps", "lower"),
    mk("ultimo_elevacion_gemelos_prensa", "Elevaci\xF3n de Gemelos en Prensa de Piernas", "Calf raise en prensa con pausa exc\xE9ntrica. Gemelos.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2.2, 1.5, 0.1, 2.8, ["Pantorrillas:gastrocnemio"], ["Pantorrillas:s\xF3leo"], [], "Pantorrillas", "lower"),
    mk("ultimo_elevacion_talones_sentado_unilateral", "Elevaci\xF3n de Gemelos Sentado Unilateral con Mancuerna", "Calf raise sentado a una pierna. S\xF3leo.", "Mancuerna", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.8, 1.2, 0, 1.5, ["Pantorrillas:s\xF3leo"], [], [], "Pantorrillas", "lower"),
    mk("ultimo_extension_triceps_cruzada_polea", "Extensi\xF3n de Tr\xEDceps Cruzada en Polea Alta", "Extensiones cruzadas en polea. Tr\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.6, 1.2, 0, 1.8, ["Tr\xEDceps"], [], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_press_frances_declinado_mancuernas", "Press Franc\xE9s Declinado con Mancuernas", "Skullcrusher declinado con mancuernas. Tr\xEDceps.", "Mancuerna", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2, 1.6, 0, 2.5, ["Tr\xEDceps"], [], [], "Tr\xEDceps", "upper"),
    mk("ultimo_extension_overhead_polea_espalda", "Extensi\xF3n Overhead en Polea Baja de Espaldas", "Extensi\xF3n de tr\xEDceps de espaldas a la polea. Cabeza larga.", "Polea", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.8, 1.5, 0.1, 2, ["Tr\xEDceps"], [], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_curl_pelicano_polea", "Curl Pel\xEDcano en Polea", "Curl inclinado hacia adelante en polea. B\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.8, 1.4, 0, 2, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_curl_predicador_unilateral_polea", "Curl Predicador Unilateral en Polea", "Curl predicador a una mano en polea. B\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.2, 0, 1.6, ["B\xEDceps"], ["B\xEDceps:braquial"], [], "B\xEDceps", "upper"),
    mk("ultimo_curl_recostado_banco_cables", "Curl de B\xEDceps Recostado en Banco Inclinado (Cables)", "Curl en banco inclinado con polea. B\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1, 0, 1.8, ["B\xEDceps"], ["B\xEDceps:braquial"], [], "B\xEDceps", "upper"),
    mk("ultimo_crunch_maquina_declinada", "Crunch Abdominal en M\xE1quina Declinada con Peso", "Crunch en banco declinado. Core.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 2.2, 1.8, 0.5, 1.5, ["Core"], [], [], "Core", "full"),
    mk("ultimo_plancha_rodillo", "Plancha con Desplazamiento de Rodillo (Ab Wheel)", "Anti-extensi\xF3n con rueda abdominal. Core.", "Rueda", "Accesorio", "Fuerza", "Anti-Extensi\xF3n", 3.2, 3.2, 1, 2.5, ["Core"], ["Dorsales"], ["Tr\xEDceps"], "Core", "full"),
    mk("ultimo_press_pallof_arrodillado", "Press Pallof Arrodillado", "Anti-rotaci\xF3n arrodillado. Core.", "Polea", "Accesorio", "Estabilidad", "Anti-Rotaci\xF3n", 1.5, 1.8, 0.1, 1, ["Core"], [], ["Gl\xFAteos:mayor"], "Core", "full"),
    mk("ultimo_giros_torso_maquina", "Giros de Torso en M\xE1quina Sentada", "Rotaci\xF3n de tronco en m\xE1quina. Core.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Rotaci\xF3n", 1.8, 1.2, 0.4, 1.2, ["Core"], [], [], "Core", "full"),
    mk("ultimo_curl_muneca_unilateral_banco", "Curl de Mu\xF1eca Unilateral Apoyado en Banco", "Flexi\xF3n de mu\xF1eca a una mano. Antebrazo.", "Mancuerna", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.2, 1, 0, 1.5, ["Antebrazo"], [], [], "Antebrazo", "upper"),
    mk("ultimo_extension_muneca_unilateral_banco", "Extensi\xF3n de Mu\xF1eca Unilateral Apoyado en Banco", "Extensi\xF3n de mu\xF1eca a una mano. Antebrazo.", "Mancuerna", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.2, 1, 0, 1.5, ["Antebrazo"], [], [], "Antebrazo", "upper"),
    mk("ultimo_encogimientos_maquina_pausa", "Encogimientos de Hombros en M\xE1quina Smith (Pausa Isom\xE9trica)", "Shrugs en Smith con pausa. Trapecio.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Empuje", 2.2, 1.8, 0.8, 1.5, ["Trapecio"], [], ["Antebrazo"], "Trapecio", "upper"),
    mk("ultimo_encogimientos_cables_banco", "Encogimientos de Hombros con Cables y Banco", "Shrugs con polea y banco inclinado. Trapecio.", "Polea", "Aislamiento", "Hipertrofia", "Empuje", 1.8, 1.4, 0, 1.2, ["Trapecio"], [], [], "Trapecio", "upper"),
    mk("ultimo_pullover_maquina", "Pullover en M\xE1quina (Nautilus Style)", "Extensi\xF3n de hombro en m\xE1quina. Dorsal.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Tir\xF3n", 2.2, 1.6, 0, 1.8, ["Dorsales"], ["Tr\xEDceps", "Pectorales"], [], "Dorsales", "upper"),
    mk("ultimo_remo_invertido_pies_elevados", "Remo Invertido con Pies Elevados y Chaleco Lastrado", "Remo invertido con piernas elevadas. Dorsal.", "Peso Corporal", "Accesorio", "Hipertrofia", "Tir\xF3n", 3.5, 3, 0.4, 2.5, ["Dorsales"], ["B\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_press_hombros_unilateral_polea", "Press de Hombros Unilateral en Polea (Rodilla Apoyada)", "OHP a una mano en polea. Deltoides.", "Polea", "Accesorio", "Hipertrofia", "Empuje", 2.4, 2.2, 0.5, 1.8, ["Deltoides:anterior"], ["Tr\xEDceps"], ["Core", "Gl\xFAteos:mayor"], "Deltoides", "upper"),
    // --- Más curls, extensiones, variantes avanzadas ---
    mk("ultimo_curl_estricto_pared", "Curl Estricto Contra la Pared (Strict Curl)", "Curl con espalda contra pared. Sin impulso.", "Barra", "Accesorio", "Fuerza", "Flexi\xF3n", 2.5, 2.5, 0.8, 2, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Antebrazo"], "B\xEDceps", "upper"),
    mk("ultimo_curl_gironda", "Curl Gironda (Perfect Curl)", "Curl con t\xE9cnica Gironda. B\xEDceps.", "Barra", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.8, 1.6, 0.2, 1.8, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_curl_dual_polea_espalda", "Curl Dual en Polea Baja de Espaldas", "Curl de espaldas a la polea. M\xE1ximo estiramiento.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.4, 0, 2, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_curl_crucifijo_polea", "Curl Crucifijo en Polea Alta", "Curl con poleas altas. Cabeza corta del b\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.5, 0.1, 1.6, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_waiter_curl", "Waiter's Curl (disco o mancuerna por el plato)", "Curl sosteniendo por el disco. Braquiorradial.", "Mancuerna", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1.2, 0.1, 1.5, ["B\xEDceps"], ["Antebrazo"], ["Core"], "Antebrazo", "upper"),
    mk("ultimo_curl_apoyado_pecho_banco", "Curl Apoyado en el Pecho en Banco Inclinado", "Curl con brazos apoyados en banco. B\xEDceps.", "Mancuerna", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1.2, 0, 1.5, ["B\xEDceps"], ["B\xEDceps:braquial"], [], "B\xEDceps", "upper"),
    mk("ultimo_curl_concentrado_aire", "Curl Concentrado al Aire (Estilo Arnold)", "Curl concentrado sin apoyo. B\xEDceps.", "Mancuerna", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.4, 0.4, 1.4, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core", "Antebrazo"], "B\xEDceps", "upper"),
    mk("ultimo_curl_biceps_toalla", "Curl de B\xEDceps con Toalla", "Curl con agarre en toalla. Fuerza de agarre.", "Kettlebell", "Aislamiento", "Fuerza", "Flexi\xF3n", 2, 1.8, 0, 2.2, ["B\xEDceps"], ["Antebrazo"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_curl_predicador_invertido", "Curl Predicador Invertido", "Curl predicador con agarre prono. Braquial.", "Barra", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.4, 0, 1.6, ["B\xEDceps:braquial"], ["Antebrazo", "B\xEDceps"], ["Antebrazo"], "B\xEDceps", "upper"),
    mk("ultimo_curl_biceps_polea_banco", "Curl de B\xEDceps Unilateral en Polea (Pecho Apoyado en Banco)", "Curl en polea con pecho apoyado. B\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1.2, 0, 1.4, ["B\xEDceps"], ["B\xEDceps:braquial"], [], "B\xEDceps", "upper"),
    mk("ultimo_extensiones_katana_polea", "Extensiones Katana en Polea", "Extensiones cruzadas por detr\xE1s. Cabeza larga tr\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.6, 1.5, 0, 2.2, ["Tr\xEDceps"], [], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_extension_triceps_crossbody", "Extensi\xF3n de Tr\xEDceps Cruzada en Polea Media (Crossbody)", "Extensiones cruzadas. Cabeza lateral tr\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.5, 1.4, 0, 1.8, ["Tr\xEDceps"], [], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_press_california", "Press California", "Press h\xEDbrido para tr\xEDceps. Barra.", "Barra", "Accesorio", "Fuerza", "Extensi\xF3n", 3.2, 3, 0.2, 2.8, ["Tr\xEDceps"], ["Pectorales"], ["Deltoides:anterior"], "Tr\xEDceps", "upper"),
    mk("ultimo_extension_pjr", "Extensi\xF3n PJR (PJR Pullover/Extension)", "Extensi\xF3n PJR para cabeza larga. Mancuerna.", "Mancuerna", "Accesorio", "Hipertrofia", "Extensi\xF3n", 2.5, 2.2, 0.1, 3, ["Tr\xEDceps"], ["Dorsales"], ["Pectorales"], "Tr\xEDceps", "upper"),
    mk("ultimo_rolling_triceps_extensions", "Rolling Triceps Extensions", "Extensiones con mancuernas rodando. Tr\xEDceps.", "Mancuerna", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2.2, 2, 0, 2.5, ["Tr\xEDceps"], [], ["Antebrazo"], "Tr\xEDceps", "upper"),
    mk("ultimo_rompecraneos_suelo", "Rompecr\xE1neos en el Suelo (Floor Skullcrusher con Pausa)", "Skullcrusher en suelo con pausa. Tr\xEDceps.", "Barra", "Aislamiento", "Fuerza", "Extensi\xF3n", 2.4, 2.2, 0, 2.4, ["Tr\xEDceps"], [], [], "Tr\xEDceps", "upper"),
    mk("ultimo_press_jm_mancuernas", "Press JM con Mancuernas", "Press JM para tr\xEDceps. Mancuernas.", "Mancuerna", "Accesorio", "Hipertrofia", "Extensi\xF3n", 2.4, 2.2, 0.1, 2.6, ["Tr\xEDceps"], ["Pectorales", "Deltoides:anterior"], ["Antebrazo"], "Tr\xEDceps", "upper"),
    mk("ultimo_extension_overhead_disco", "Extensi\xF3n Overhead con Disco (Sentado)", "Extensi\xF3n de tr\xEDceps con disco. Tr\xEDceps.", "Disco", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.8, 1.5, 0.2, 2, ["Tr\xEDceps"], [], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_fondos_coreanos", "Fondos Coreanos (Korean Dips)", "Fondos con inclinaci\xF3n. Tr\xEDceps y deltoides.", "Peso Corporal", "Accesorio", "Estabilidad", "Empuje", 3.5, 3.8, 0.4, 3.5, ["Tr\xEDceps", "Deltoides:anterior"], ["Pectorales"], ["Dorsales", "Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_tate_press_polea", "Tate Press de Pie en Polea", "Tate press con cables cruzados. Tr\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.6, 1.4, 0, 1.6, ["Tr\xEDceps"], [], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_extension_triceps_inversa_polea", "Extensi\xF3n Unilateral de Tr\xEDceps Inversa en Polea", "Extensiones inversas sin agarre. Tr\xEDceps.", "Polea", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 1.4, 1.2, 0, 1.4, ["Tr\xEDceps"], [], [], "Tr\xEDceps", "upper"),
    mk("ultimo_curl_dedos_barra", "Curl de Dedos con Barra (Finger Curls)", "Flexi\xF3n de dedos. Antebrazo.", "Barra", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.4, 1.2, 0, 1.5, ["Antebrazo"], [], [], "Antebrazo", "upper"),
    mk("ultimo_pinzas_discos", "Pinzas con Discos (Plate Pinches Isom\xE9tricos)", "Agarre isom\xE9trico de discos. Antebrazo.", "Disco", "Aislamiento", "Resistencia", "Otro", 1.5, 1.5, 0, 1.6, ["Antebrazo"], [], [], "Antebrazo", "upper"),
    mk("ultimo_pronacion_supinacion_mazo", "Pronaci\xF3n/Supinaci\xF3n con Mazo o Mancuerna Asim\xE9trica", "Rotaci\xF3n de antebrazo. Movilidad.", "Mancuerna", "Aislamiento", "Movilidad", "Otro", 1.2, 1.2, 0, 1.5, ["Antebrazo"], ["Antebrazo"], [], "Antebrazo", "upper"),
    mk("ultimo_curl_zottman_polea", "Curl Zottman en Polea Baja con Cuerda", "Curl Zottman en polea. B\xEDceps y antebrazo.", "Polea", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.6, 1.5, 0.1, 1.5, ["B\xEDceps", "Antebrazo"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_sentadilla_overhead", "Sentadilla de Arrancada (Overhead Squat)", "Sentadilla con barra overhead. Movilidad.", "Barra", "B\xE1sico", "Movilidad", "Sentadilla", 4, 4.5, 1.8, 2.5, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Deltoides:anterior", "Trapecio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_buenos_dias_1_pierna", "Buenos D\xEDas a una Pierna", "Bisagra unilateral. Isquios y gl\xFAteos.", "Barra", "Accesorio", "Estabilidad", "Bisagra", 3.5, 3.8, 1, 2, ["Isquiosurales", "Gl\xFAteos:mayor"], ["Gl\xFAteos:medio"], ["Core"], "Isquiosurales", "lower"),
    mk("ultimo_band_pull_apart", "Band Pull-Apart", "Abducci\xF3n horizontal con banda. Deltoides posterior.", "Banda", "Aislamiento", "Movilidad", "Tir\xF3n", 1.2, 1.2, 0, 1, ["Deltoides:posterior", "Dorsales"], ["Trapecio"], [], "Deltoides", "upper"),
    mk("ultimo_log_press", "Log Press (Press con Tronco)", "Press con tronco de strongman. Potencia.", "Tronco", "B\xE1sico", "Potencia", "Empuje", 4.5, 4.8, 1.8, 3, ["Deltoides:anterior", "Tr\xEDceps"], ["Pectorales:superior"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_press_javelin", "Press Javelin (Barra a una Mano)", "Press con barra a una mano. Estabilidad.", "Barra", "Accesorio", "Estabilidad", "Empuje", 3.2, 4, 0.8, 2, ["Deltoides:anterior", "Deltoides:medio"], ["Tr\xEDceps"], ["Core", "Antebrazo"], "Deltoides", "upper"),
    mk("ultimo_levantamiento_piedra_atlas", "Levantamiento de Piedra Atlas", "Atlas stone lift. Full body.", "Piedra", "B\xE1sico", "Fuerza", "Bisagra", 5, 4.8, 2.5, 3.5, ["Gl\xFAteos:mayor", "Isquiosurales"], ["Dorsales", "B\xEDceps"], ["Core"], "Gl\xFAteos", "full"),
    mk("ultimo_volteo_neumatico", "Volteo de Neum\xE1tico (Tire Flip)", "Tire flip. Triple extensi\xF3n.", "Neum\xE1tico", "B\xE1sico", "Potencia", "Salto", 4.8, 4.5, 2, 3, ["Gl\xFAteos:mayor", "Cu\xE1driceps"], ["Deltoides:anterior", "Pectorales"], ["Core"], "Gl\xFAteos", "full"),
    mk("ultimo_elevacion_gemelos_hack", "Elevaci\xF3n de Talones en M\xE1quina Hack", "Calf raise en hack. Gemelos.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2.5, 2, 0.5, 2, ["Pantorrillas:gastrocnemio"], ["Pantorrillas:s\xF3leo"], ["Core"], "Pantorrillas", "lower"),
    mk("ultimo_curl_isquios_mancuerna_tumbado", "Curl de Isquiosurales con Mancuerna Tumbado", "Curl femoral con mancuerna. Isquios.", "Mancuerna", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 2, 1.8, 0, 1.5, ["Isquiosurales"], ["Pantorrillas:gastrocnemio"], ["Core"], "Isquiosurales", "lower"),
    mk("ultimo_paseo_pato", "Paseo del Pato (Duck Walk)", "Locomoci\xF3n en cuclillas. Cu\xE1driceps.", "Peso Corporal", "B\xE1sico", "Resistencia", "Otro", 3.5, 3, 0.5, 2.5, ["Cu\xE1driceps"], ["Gl\xFAteos:mayor"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_extensiones_triceps_trx", "Extensiones de Tr\xEDceps en TRX", "Extensiones en TRX. Tr\xEDceps y estabilidad.", "TRX", "Accesorio", "Estabilidad", "Extensi\xF3n", 2.2, 2.5, 0.2, 2, ["Tr\xEDceps"], ["Deltoides:anterior"], ["Core"], "Tr\xEDceps", "upper"),
    mk("ultimo_curl_biceps_trx", "Curl de B\xEDceps en TRX", "Curl en TRX. B\xEDceps y estabilidad.", "TRX", "Accesorio", "Estabilidad", "Flexi\xF3n", 2, 2.2, 0.1, 1.8, ["B\xEDceps"], ["B\xEDceps:braquial"], ["Core"], "B\xEDceps", "upper"),
    mk("ultimo_flexiones_planche", "Flexiones en Plancha (Planche Push-ups)", "Flexiones con inclinaci\xF3n hacia planche. Fuerza.", "Peso Corporal", "B\xE1sico", "Fuerza", "Empuje", 4.5, 4.8, 0.5, 3.5, ["Deltoides:anterior", "Pectorales"], ["Tr\xEDceps"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_dominadas_1_brazo", "Dominadas a un Brazo (OAP)", "Dominadas a una mano. M\xE1xima fuerza.", "Peso Corporal", "B\xE1sico", "Fuerza", "Tir\xF3n", 4.8, 5, 0.2, 4, ["Dorsales", "B\xEDceps"], ["B\xEDceps:braquial", "Antebrazo"], ["Core", "Antebrazo"], "Dorsales", "upper"),
    mk("ultimo_front_lever", "Front Lever (Isom\xE9trico)", "Front lever isom\xE9trico. Dorsal.", "Peso Corporal", "Accesorio", "Estabilidad", "Tir\xF3n", 4, 4.5, 0.5, 3, ["Dorsales"], ["Pectorales", "Tr\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_back_lever", "Back Lever (Isom\xE9trico)", "Back lever isom\xE9trico. Pectoral y deltoides.", "Peso Corporal", "Accesorio", "Estabilidad", "Extensi\xF3n", 3.8, 4.2, 0.5, 3.5, ["Pectorales", "Deltoides:anterior"], ["B\xEDceps"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_lanzamiento_lateral_balon", "Lanzamiento Lateral de Bal\xF3n Medicinal", "Rotaci\xF3n con bal\xF3n. Core.", "Bal\xF3n Medicinal", "Accesorio", "Potencia", "Rotaci\xF3n", 3, 3.5, 0.8, 2, ["Core"], ["Deltoides:anterior"], ["Gl\xFAteos:mayor"], "Core", "full"),
    mk("ultimo_elevacion_piernas_colgado_rotacion", "Elevaci\xF3n de Piernas Colgado con Rotaci\xF3n", "Leg raise con rotaci\xF3n. Core.", "Peso Corporal", "Accesorio", "Hipertrofia", "Flexi\xF3n", 2.6, 2.5, 0.5, 1.6, ["Core"], ["Core"], ["Dorsales", "Antebrazo"], "Core", "full"),
    mk("ultimo_remo_invertido_1_brazo", "Remo Invertido a un Brazo", "Remo invertido unilateral en anillas. Dorsal.", "TRX", "Accesorio", "Fuerza", "Tir\xF3n", 3.5, 3.8, 0.2, 2.5, ["Dorsales"], ["B\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_press_banca_agarre_ancho", "Press de Banca con Agarre Muy Ancho (Wide Grip)", "Press con agarre amplio. Pectoral.", "Barra", "Accesorio", "Hipertrofia", "Empuje", 3.6, 3.5, 0.3, 2.6, ["Pectorales"], ["Deltoides:anterior"], ["Dorsales", "Core"], "Pectorales", "upper"),
    mk("ultimo_arrancada_profunda", "Arrancada Profunda (Squat Snatch)", "Snatch completo. Potencia ol\xEDmpica.", "Barra", "B\xE1sico", "Potencia", "Salto", 5, 5, 1.8, 5, ["Gl\xFAteos:mayor", "Cu\xE1driceps"], ["Isquiosurales", "Deltoides:anterior"], ["Core"], "Gl\xFAteos", "full"),
    mk("ultimo_cargada_profunda", "Cargada Profunda (Squat Clean)", "Clean completo. Potencia ol\xEDmpica.", "Barra", "B\xE1sico", "Potencia", "Salto", 5, 5, 2, 4.8, ["Gl\xFAteos:mayor", "Cu\xE1driceps"], ["Isquiosurales", "Trapecio"], ["Core"], "Gl\xFAteos", "full"),
    mk("ultimo_jerk_tijera", "Jerk en Tijera (Split Jerk)", "Jerk con salto a tijera. Potencia.", "Barra", "B\xE1sico", "Potencia", "Empuje", 4.5, 4.8, 1.5, 4, ["Cu\xE1driceps", "Deltoides:anterior"], ["Tr\xEDceps", "Gl\xFAteos:mayor"], ["Core"], "Deltoides", "full"),
    mk("ultimo_tiron_arrancada", "Tir\xF3n de Arrancada (Snatch Pull)", "Tir\xF3n de arrancada. Fuerza de tracci\xF3n.", "Barra", "Accesorio", "Fuerza", "Salto", 4.2, 4.5, 1.5, 3, ["Gl\xFAteos:mayor", "Trapecio"], ["Isquiosurales", "Cu\xE1driceps"], ["Core"], "Gl\xFAteos", "full"),
    mk("ultimo_tiron_envion", "Tir\xF3n de Envi\xF3n (Clean Pull)", "Tir\xF3n de clean. Fuerza de tracci\xF3n.", "Barra", "Accesorio", "Fuerza", "Salto", 4.2, 4.5, 1.6, 3, ["Gl\xFAteos:mayor", "Trapecio"], ["Isquiosurales", "Cu\xE1driceps"], ["Core"], "Gl\xFAteos", "full"),
    mk("ultimo_sotts_press", "Sotts Press (Press tras Nuca en Sentadilla Profunda)", "OHP en sentadilla profunda. Movilidad.", "Barra", "Accesorio", "Movilidad", "Empuje", 3.8, 4.5, 1.5, 3.5, ["Deltoides:anterior", "Deltoides:medio"], ["Tr\xEDceps"], ["Core", "Cu\xE1driceps"], "Deltoides", "full"),
    mk("ultimo_sentadilla_zombie", "Sentadilla Zombie (Frontal con Brazos Extendidos)", "Front squat con brazos al frente. Estabilidad.", "Barra", "Accesorio", "Estabilidad", "Sentadilla", 4, 4.2, 1.8, 2.5, ["Cu\xE1driceps"], ["Gl\xFAteos:mayor"], ["Core", "Deltoides:anterior"], "Cu\xE1driceps", "lower"),
    mk("ultimo_snatch_balance", "Ca\xEDda de Arrancada (Snatch Balance)", "Snatch balance. Potencia.", "Barra", "Accesorio", "Potencia", "Sentadilla", 4.2, 4.8, 1.5, 4.5, ["Cu\xE1driceps", "Deltoides:anterior"], ["Tr\xEDceps"], ["Core"], "Cu\xE1driceps", "full"),
    mk("ultimo_peso_muerto_zercher_suelo", "Peso Muerto Zercher Desde el Suelo", "Deadlift Zercher. Gl\xFAteos e isquios.", "Barra", "B\xE1sico", "Fuerza", "Bisagra", 4.8, 4.5, 2, 2.8, ["Gl\xFAteos:mayor", "Isquiosurales"], ["Cu\xE1driceps", "B\xEDceps"], ["Core"], "Gl\xFAteos", "lower"),
    mk("ultimo_dominadas_toalla", "Dominadas con Toalla (Grip Pull-ups)", "Dominadas agarrando toalla. Fuerza de agarre.", "Toalla", "Accesorio", "Fuerza", "Tir\xF3n", 4.2, 4, 0.2, 2.5, ["Dorsales", "Antebrazo"], ["B\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_puente_luchador", "Puente de luchador isom\xE9trico (Wrestler's Bridge)", "Puente cervical. Cuello y estabilidad.", "Peso Corporal", "Accesorio", "Estabilidad", "Extensi\xF3n", 2.5, 3, 2.5, 2, [], ["Gl\xFAteos:mayor"], ["Core"], "Trapecio", "upper"),
    mk("ultimo_flexiones_hindu", "Flexiones Hind\xFA (Dive Bombers)", "Flexiones con arco. Pectoral y hombros.", "Peso Corporal", "Accesorio", "Resistencia", "Empuje", 2.5, 2.5, 0.4, 2, ["Pectorales", "Deltoides:anterior"], ["Tr\xEDceps"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_sentadilla_hindu", "Sentadilla Hind\xFA", "Sentadilla con talones elevados y brazos din\xE1micos. Cu\xE1driceps.", "Peso Corporal", "Accesorio", "Resistencia", "Sentadilla", 2.8, 2.5, 0.2, 2.5, ["Cu\xE1driceps"], ["Pantorrillas:gastrocnemio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_giro_cadera_saco_bulgaro", "Giro de Cadera con Saco B\xFAlgaro (Spin)", "Rotaci\xF3n con saco. Core y potencia.", "Saco de arena", "Accesorio", "Potencia", "Rotaci\xF3n", 3.8, 4, 1, 2.8, ["Core", "Deltoides:anterior"], ["Antebrazo"], ["Gl\xFAteos:mayor", "Cu\xE1driceps"], "Core", "full"),
    mk("ultimo_suplex_saco_bulgaro", "Suplex con Saco B\xFAlgaro", "Suplex con saco. Extensi\xF3n de tronco.", "Saco de arena", "Accesorio", "Potencia", "Extensi\xF3n", 4, 4.2, 1.2, 3, ["Core", "Gl\xFAteos:mayor"], ["Isquiosurales"], ["Dorsales"], "Gl\xFAteos", "full"),
    mk("ultimo_log_clean", "Cargada con Cilindro (Log Clean)", "Clean con log. Potencia.", "Tronco", "B\xE1sico", "Potencia", "Salto", 4.8, 4.8, 1.8, 3.5, ["Gl\xFAteos:mayor", "Isquiosurales"], ["Cu\xE1driceps", "Trapecio"], ["Core", "B\xEDceps"], "Gl\xFAteos", "full"),
    mk("ultimo_transporte_escudo", "Transporte de Escudo (Husafell Stone Carry)", "Carry con piedra Husafell. Full body.", "Escudo", "B\xE1sico", "Fuerza", "Otro", 4.8, 4.5, 2.5, 2.5, ["Core", "Antebrazo"], ["Cu\xE1driceps"], ["Gl\xFAteos:mayor", "Dorsales"], "Core", "full"),
    mk("ultimo_peso_muerto_eje", "Peso Muerto con Eje (Axle Deadlift)", "Deadlift con axle bar. Fuerza de agarre.", "Eje", "B\xE1sico", "Fuerza", "Bisagra", 4.8, 4.8, 1.6, 2.8, ["Gl\xFAteos:mayor", "Isquiosurales"], ["Antebrazo"], ["Core"], "Gl\xFAteos", "lower"),
    mk("ultimo_press_eje", "Press con Eje (Axle Press)", "OHP con axle bar. Potencia.", "Eje", "B\xE1sico", "Potencia", "Empuje", 4.2, 4.5, 1.2, 2.6, ["Deltoides:anterior", "Tr\xEDceps"], ["Deltoides:medio"], ["Core", "Antebrazo"], "Deltoides", "upper"),
    mk("ultimo_piedra_atlas_hombro", "Piedra Atlas al Hombro (Stone To Shoulder)", "Atlas stone to shoulder. Full body.", "Piedra", "B\xE1sico", "Potencia", "Bisagra", 5, 4.8, 2.5, 3.8, ["Gl\xFAteos:mayor", "Isquiosurales"], ["Dorsales", "Cu\xE1driceps"], ["Core"], "Gl\xFAteos", "full"),
    mk("ultimo_paseo_caiman", "Paseo del Caim\xE1n (Alligator Crawl)", "Reptar en cuadrupedia. Core y pectoral.", "Peso Corporal", "B\xE1sico", "Resistencia", "Otro", 3.8, 3.5, 0.8, 2.5, ["Core", "Pectorales"], ["Deltoides:anterior", "Tr\xEDceps"], ["Cu\xE1driceps"], "Core", "full"),
    mk("ultimo_press_landmine_rotacional", "Press Landmine Rotacional a un Brazo", "Press landmine con rotaci\xF3n. Potencia.", "Barra", "Accesorio", "Potencia", "Empuje", 3.2, 3.5, 0.8, 2, ["Deltoides:anterior", "Pectorales"], ["Tr\xEDceps"], ["Core"], "Deltoides", "upper"),
    mk("ultimo_sprints_trineo", "Sprints Resistidos con Trineo", "Sprint con trineo. Potencia de piernas.", "Trineo", "B\xE1sico", "Potencia", "Salto", 4.5, 4.5, 0.5, 3.5, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Pantorrillas:gastrocnemio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_battle_ropes", "Ondas con Cuerdas de Batalla (Battle Ropes)", "Battle ropes. Resistencia de hombros y core.", "Cuerdas", "Accesorio", "Resistencia", "Tir\xF3n", 3.5, 3.2, 0.4, 2, ["Deltoides:anterior", "Core"], ["Dorsales"], ["Cu\xE1driceps"], "Deltoides", "full"),
    mk("ultimo_rope_pull", "Tir\xF3n de Cuerda Pesada Sentado (Rope Pull)", "Tirar cuerda sentado. Dorsal y antebrazo.", "Otro", "Accesorio", "Fuerza", "Tir\xF3n", 3.5, 3, 0.5, 2, ["Dorsales", "Antebrazo"], ["B\xEDceps", "Dorsales"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_flexion_cuello_isometrica", "Flexi\xF3n de Cuello Isom\xE9trica Contra Resistencia", "Flexi\xF3n cervical isom\xE9trica. Cuello.", "Banda", "Aislamiento", "Estabilidad", "Flexi\xF3n", 1.5, 1.5, 1, 1, [], [], ["Core"], "Trapecio", "upper"),
    mk("ultimo_rotacion_cuello_banda", "Rotaci\xF3n de Cuello con Banda El\xE1stica", "Rotaci\xF3n cervical con banda. Cuello.", "Banda", "Aislamiento", "Resistencia", "Rotaci\xF3n", 1.2, 1.5, 1, 1, [], [], [], "Trapecio", "upper"),
    mk("ultimo_remo_pendlay_eje", "Remo Pendlay con Eje (Axle Pendlay Row)", "Pendlay row con axle. Dorsal.", "Eje", "B\xE1sico", "Fuerza", "Tir\xF3n", 4.2, 4.2, 1.5, 2.8, ["Dorsales"], ["Antebrazo", "B\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_zancadas_saco_hombro", "Zancadas con Saco de Arena al Hombro Asim\xE9trico", "Zancadas con sandbag. Estabilidad.", "Saco de arena", "Accesorio", "Estabilidad", "Sentadilla", 3.8, 4, 1.2, 2.5, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Gl\xFAteos:medio"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("ultimo_peso_muerto_neumaticos", "Peso Muerto con Neum\xE1ticos (Silver Dollar Deadlift)", "Deadlift supram\xE1ximo con neum\xE1ticos. Gl\xFAteos.", "Neum\xE1tico", "B\xE1sico", "Fuerza", "Bisagra", 5, 4.8, 2, 3, ["Gl\xFAteos:mayor", "Isquiosurales"], ["Trapecio"], ["Core", "Antebrazo"], "Gl\xFAteos", "lower"),
    mk("ultimo_remo_il\xEDaco_polea", "Remo Il\xEDaco Unilateral en Polea Alta", "Remo unilateral en polea alta. Dorsal.", "Polea", "Aislamiento", "Hipertrofia", "Tir\xF3n", 2, 1.8, 0.1, 1.5, ["Dorsales"], ["B\xEDceps:braquial"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_remo_unilateral_maquina_apoyo", "Remo Unilateral en Polea con Pecho Apoyado", "Remo con pecho apoyado en m\xE1quina. Dorsal.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Tir\xF3n", 2.2, 1.8, 0, 1.4, ["Dorsales"], ["Dorsales"], [], "Dorsales", "upper"),
    mk("ultimo_remo_kelso_apoyo", "Remo Kelso con Pecho Apoyado", "Remo Kelso. Romboides y trapecio.", "Mancuerna", "Aislamiento", "Hipertrofia", "Tir\xF3n", 2, 1.5, 0, 1.4, ["Dorsales", "Trapecio:medio"], ["Deltoides:posterior"], [], "Dorsales", "upper"),
    mk("ultimo_jalon_pecho_unilateral", "Jal\xF3n al Pecho Unilateral", "Jal\xF3n a una mano. Dorsal.", "Polea", "Aislamiento", "Hipertrofia", "Tir\xF3n", 2.2, 2, 0.1, 1.5, ["Dorsales"], ["B\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_flexiones_trx", "Flexiones en TRX", "Flexiones con TRX. Estabilidad.", "TRX", "Accesorio", "Estabilidad", "Empuje", 2.6, 2.8, 0.1, 2, ["Pectorales"], ["Tr\xEDceps", "Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    mk("ultimo_remo_invertido_trx_giro", "Remo Invertido en TRX con Giro (Agarre D)", "Remo invertido con giro en TRX. Dorsal.", "TRX", "Accesorio", "Resistencia", "Tir\xF3n", 2.2, 2, 0.1, 1.6, ["Dorsales"], ["B\xEDceps", "Deltoides:posterior"], ["Core"], "Dorsales", "upper"),
    mk("ultimo_elevaciones_laterales_polea_cuffs", "Elevaciones Laterales en Polea Cruzada con Cuffs", "Lateral raise con cuffs en polea. Deltoides medio.", "Polea", "Aislamiento", "Hipertrofia", "Otro", 1.6, 1.4, 0, 1.5, ["Deltoides:medio"], [], ["Core"], "Deltoides", "upper"),
    mk("ultimo_elevaciones_laterales_banco_inclinado", "Elevaciones Laterales en Banco Inclinado (Pecho Apoyado)", "Lateral raise con pecho apoyado. Deltoides medio.", "Mancuerna", "Aislamiento", "Hipertrofia", "Otro", 1.4, 1.2, 0, 1.2, ["Deltoides:medio"], ["Trapecio"], [], "Deltoides", "upper"),
    mk("ultimo_elevaciones_y_tumbado", "Elevaciones en Y Tumbado Boca Abajo", "Y raise tumbado. Deltoides medio y trapecio inferior.", "Otro", "Aislamiento", "Movilidad", "Otro", 1.5, 1.2, 0, 1.2, ["Deltoides:medio", "Trapecio:inferior"], ["Deltoides:posterior"], [], "Deltoides", "upper"),
    // --- EJERCICIOS FALTANTES (adición) ---
    mk("nuevo_sentadilla_pistol", "Sentadilla Pistol (Una Pierna)", "Sentadilla completa a una pierna. M\xE1xima demanda de movilidad, fuerza y equilibrio.", "Peso Corporal", "B\xE1sico", "Fuerza", "Sentadilla", 4, 4.2, 0.4, 3, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("nuevo_sentadilla_cosaca", "Sentadilla Cosaca", "Sentadilla lateral profunda con una pierna extendida. Movilidad de cadera y aductores.", "Peso Corporal", "Accesorio", "Movilidad", "Sentadilla", 3.2, 3, 0.3, 2.5, ["Cu\xE1driceps", "Aductores"], ["Gl\xFAteos:mayor"], ["Core"], "Cu\xE1driceps", "lower"),
    mk("nuevo_prensa_unilateral", "Prensa de Piernas Unilateral", "Prensa a una pierna para trabajo de desbalances y mayor rango.", "M\xE1quina", "Accesorio", "Hipertrofia", "Sentadilla", 3.5, 2.8, 0.4, 2.2, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Isquiosurales"], [], "Cu\xE1driceps", "lower"),
    mk("nuevo_extension_cuadriceps_unilateral", "Extensi\xF3n de Cu\xE1driceps Unilateral en M\xE1quina", "Extensi\xF3n a una pierna para enfoque y correcci\xF3n de asimetr\xEDa.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Extensi\xF3n", 2, 1.4, 0, 2, ["Cu\xE1driceps"], [], [], "Cu\xE1driceps", "lower"),
    mk("nuevo_curl_nordico_inverso", "Curl N\xF3rdico Inverso (Reverse Nordic)", "Extensi\xF3n de rodilla exc\xE9ntrica con peso corporal. Cu\xE1driceps, especialmente recto femoral.", "Peso Corporal", "Accesorio", "Hipertrofia", "Extensi\xF3n", 2.8, 2.5, 0.1, 3, ["Cu\xE1driceps:recto femoral"], [], ["Core"], "Cu\xE1driceps", "lower"),
    mk("nuevo_step_up_barra", "Step-Up con Barra", "Subida al caj\xF3n con barra sobre trapecios. Cu\xE1driceps y gl\xFAteos unilateral.", "Barra", "Accesorio", "Fuerza", "Sentadilla", 3.8, 3.5, 1.2, 2.4, ["Cu\xE1driceps", "Gl\xFAteos:mayor"], ["Isquiosurales"], ["Core", "Erectores Espinales"], "Cu\xE1driceps", "lower"),
    mk("nuevo_buenos_dias_ssb_sentado", "Buenos D\xEDas Sentado con SSB", "Bisagra de cadera sentado con barra SSB. Erectores y isquiosurales.", "Barra", "Accesorio", "Hipertrofia", "Bisagra", 3.5, 3.2, 1.8, 2, ["Isquiosurales", "Erectores Espinales"], ["Gl\xFAteos:mayor"], ["Core"], "Isquiosurales", "lower"),
    mk("nuevo_curl_drag_barra", "Curl Drag con Barra", "Curl arrastrando la barra hacia arriba por el torso. Mayor \xE9nfasis en cabeza larga del b\xEDceps.", "Barra", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.8, 1.5, 0.2, 1.6, ["B\xEDceps"], ["Deltoides:anterior"], ["Antebrazo"], "B\xEDceps", "upper"),
    mk("nuevo_curl_scott_maquina", "Curl en M\xE1quina Scott", "Curl predicador en m\xE1quina guiada. Aislamiento m\xE1ximo del b\xEDceps.", "M\xE1quina", "Aislamiento", "Hipertrofia", "Flexi\xF3n", 1.5, 1.2, 0, 1.4, ["B\xEDceps"], [], [], "B\xEDceps", "upper"),
    mk("nuevo_fondos_maquina_asistida", "Fondos en M\xE1quina Asistida", "Fondos con asistencia de m\xE1quina. Ideal para principiantes.", "M\xE1quina", "Accesorio", "Hipertrofia", "Empuje", 2.5, 2, 0.1, 1.8, ["Pectorales"], ["Tr\xEDceps", "Deltoides:anterior"], [], "Pectorales", "upper"),
    mk("nuevo_jm_press_barra", "JM Press con Barra", "H\xEDbrido entre press cerrado y press franc\xE9s. Tr\xEDceps con carga pesada.", "Barra", "Accesorio", "Fuerza", "Extensi\xF3n", 3.2, 3, 0.2, 2.8, ["Tr\xEDceps"], ["Pectorales", "Deltoides:anterior"], ["Antebrazo"], "Tr\xEDceps", "upper"),
    mk("nuevo_press_bradford", "Press Bradford", "Press alternando delante y detr\xE1s de la cabeza. Movilidad y resistencia de hombros.", "Barra", "Accesorio", "Movilidad", "Empuje", 2.5, 2.8, 0.8, 2.2, ["Deltoides:anterior", "Deltoides:medio"], ["Tr\xEDceps"], ["Core"], "Deltoides", "upper"),
    mk("nuevo_lateral_raise_lean_away", "Elevaci\xF3n Lateral Lean Away (Inclinado)", "Lateral raise inclinado al costado. Mayor rango y tensi\xF3n en deltoides medio.", "Mancuerna", "Aislamiento", "Hipertrofia", "Otro", 1.5, 1.3, 0, 1.5, ["Deltoides:medio"], [], [], "Deltoides", "upper"),
    mk("nuevo_lu_raises", "Lu Raises", "Elevaci\xF3n frontal + lateral combinada. Popularizada por Lu Xiaojun.", "Mancuerna", "Aislamiento", "Hipertrofia", "Otro", 1.8, 1.5, 0, 1.5, ["Deltoides:anterior", "Deltoides:medio"], [], ["Core"], "Deltoides", "upper"),
    mk("nuevo_remo_gorilla", "Remo Gorilla con Mancuernas", "Remo alterno desde posici\xF3n de deadlift con dos mancuernas en el suelo.", "Mancuerna", "Accesorio", "Hipertrofia", "Tir\xF3n", 3.2, 3, 0.8, 1.8, ["Dorsales"], ["B\xEDceps", "Trapecio"], ["Core", "Erectores Espinales"], "Dorsales", "upper"),
    mk("nuevo_remo_hammer_strength", "Remo en M\xE1quina Hammer Strength", "Remo en m\xE1quina Hammer con movimiento convergente. Dorsales y trapecio.", "M\xE1quina", "Accesorio", "Hipertrofia", "Tir\xF3n", 2.8, 2.2, 0, 1.6, ["Dorsales"], ["B\xEDceps", "Trapecio:medio"], [], "Dorsales", "upper"),
    mk("nuevo_jalon_v_cerrado", "Jal\xF3n en V (Agarre Cerrado)", "Jal\xF3n con agarre V neutro cerrado. Dorsales con gran rango.", "Polea", "Accesorio", "Hipertrofia", "Tir\xF3n", 2.8, 2.2, 0.1, 1.5, ["Dorsales"], ["B\xEDceps"], ["Core"], "Dorsales", "upper"),
    mk("nuevo_cable_fly_inclinado", "Cable Fly Inclinado", "Apertura en polea con banco inclinado. Pectoral superior con tensi\xF3n constante.", "Polea", "Aislamiento", "Hipertrofia", "Empuje", 1.8, 1.5, 0, 1.8, ["Pectorales:superior"], ["Deltoides:anterior"], [], "Pectorales", "upper"),
    mk("nuevo_pullover_declinado", "Pullover en Banco Declinado con Mancuerna", "Pullover en banco declinado. Mayor estiramiento del dorsal y pectoral.", "Mancuerna", "Aislamiento", "Hipertrofia", "Tir\xF3n", 2.2, 1.8, 0.1, 2, ["Dorsales", "Pectorales"], ["Tr\xEDceps"], [], "Dorsales", "upper"),
    mk("nuevo_press_mancuerna_unilateral", "Press de Banca con Mancuerna Unilateral", "Press plano con una mancuerna. Trabajo de estabilidad y anti-rotaci\xF3n.", "Mancuerna", "Accesorio", "Estabilidad", "Empuje", 3, 2.8, 0.2, 2, ["Pectorales"], ["Tr\xEDceps", "Deltoides:anterior"], ["Core"], "Pectorales", "upper"),
    mk("nuevo_ab_rollout_parado", "Ab Rollout de Pie", "Rueda abdominal desde posici\xF3n de pie. M\xE1xima exigencia de core.", "Rueda", "B\xE1sico", "Fuerza", "Anti-Extensi\xF3n", 3.5, 3.5, 1, 2.5, ["Abdomen", "Core"], ["Dorsales"], ["Erectores Espinales"], "Core", "full"),
    mk("nuevo_y_raises_mancuernas_tumbado", "Y-Raises con Mancuernas Tumbado", "Y-raise en banco inclinado boca abajo. Trapecio inferior y deltoides.", "Mancuerna", "Aislamiento", "Movilidad", "Otro", 1.5, 1.2, 0, 1.2, ["Trapecio:inferior", "Deltoides:medio"], ["Deltoides:posterior"], [], "Trapecio", "upper"),
    mk("nuevo_farmer_walk_kettlebells", "Farmer Walk con Kettlebells", "Caminata con kettlebells. Agarre, core y estabilidad.", "Kettlebell", "B\xE1sico", "Fuerza", "Otro", 4, 3.8, 2, 2.5, ["Antebrazo", "Trapecio"], ["Core"], ["Cu\xE1driceps"], "Trapecio", "full"),
    mk("nuevo_bear_crawl_peso", "Bear Crawl con Chaleco Lastrado", "Desplazamiento en cuadrupedia con peso adicional. Core y hombros.", "Peso Corporal", "Accesorio", "Resistencia", "Otro", 3.2, 3, 0.5, 2, ["Core", "Deltoides:anterior"], ["Pectorales", "Cu\xE1driceps"], ["Tr\xEDceps"], "Core", "full")
  ];

  // data/exerciseDatabaseMerged.ts
  var LOWER_BODY_ID_ALIASES = {
    db_squat_high_bar: "tren_inferior_sentadilla_barra_alta",
    db_squat_low_bar: "tren_inferior_sentadilla_barra_baja",
    db_front_squat: "tren_inferior_sentadilla_frontal",
    db_goblet_squat: "tren_inferior_sentadilla_goblet_mancuerna",
    db_zercher_squat: "tren_inferior_sentadilla_zercher",
    db_ssb_squat: "tren_inferior_sentadilla_safety_squat_bar",
    db_cambered_squat: "tren_inferior_sentadilla_cambered_bar",
    db_jefferson_squat: "tren_inferior_sentadilla_jefferson",
    db_box_squat: "tren_inferior_sentadilla_cajon_barra",
    db_deadlift: "tren_inferior_peso_muerto_convencional",
    db_sumo_deadlift: "tren_inferior_peso_muerto_sumo",
    db_semi_sumo_deadlift: "tren_inferior_peso_muerto_convencional",
    db_romanian_deadlift: "tren_inferior_peso_muerto_rumano",
    db_stiff_leg_deadlift: "tren_inferior_peso_muerto_piernas_rigidas",
    db_deficit_deadlift: "tren_inferior_peso_muerto_deficit",
    db_rack_pull: "tren_inferior_rack_pull",
    db_trap_bar_deadlift: "tren_inferior_peso_muerto_barra_hexagonal",
    db_good_mornings: "tren_inferior_buenos_dias_pie",
    db_hyperextensions: "tren_inferior_hiperextension_45",
    db_reverse_hyper: "tren_inferior_reverse_hyper",
    db_cable_pull_through: "tren_inferior_pull_through",
    db_kettlebell_swing: "tren_inferior_swing_2_manos",
    db_bodyweight_hip_thrust: "tren_inferior_hip_thrust_unilateral_peso",
    db_leg_press_45: "tren_inferior_prensa_45",
    db_single_leg_press: "tren_inferior_prensa_unilateral",
    db_hack_squat: "tren_inferior_sentadilla_hack_maquina",
    db_barbell_hack_squat: "tren_inferior_sentadilla_hack_barra",
    db_pendulum_squat: "tren_inferior_sentadilla_pendulo",
    db_belt_squat: "tren_inferior_sentadilla_belt_squat",
    db_leg_extension: "tren_inferior_extension_cuadriceps",
    db_leg_curl_seated: "tren_inferior_curl_femoral_sentado",
    db_leg_curl_lying: "tren_inferior_curl_femoral_tumbado",
    db_nordic_curl: "tren_inferior_curl_nordico",
    db_reverse_nordic: "tren_inferior_extension_inversa_nordica",
    db_bulgarian_split_squat: "tren_inferior_bulgara_mancuernas",
    db_lunges_walking: "tren_inferior_zancada_caminando_mancuernas",
    db_reverse_lunge: "tren_inferior_zancada_inversa_mancuernas",
    db_step_up: "tren_inferior_subida_cajon_mancuernas",
    db_hip_thrust: "tren_inferior_hip_thrust_barra",
    db_standing_calf_raise: "tren_inferior_elevacion_talones_pie_maquina",
    db_seated_calf_raise: "tren_inferior_elevacion_talones_sentado",
    db_sled_push: "tren_inferior_empuje_trineo",
    db_sled_pull: "tren_inferior_arrastre_trineo",
    db_box_jump: "tren_inferior_salto_cajon",
    db_adductor_machine: "tren_inferior_aduccion_cadera_maquina",
    db_abductor_machine: "tren_inferior_abduccion_cadera_maquina"
  };
  var UPPER_BODY_ID_ALIASES = {
    db_bench_press_tng: "tren_superior_press_banca_plano_barra",
    db_bench_press_paused: "tren_superior_press_banca_plano_barra",
    db_incline_bench_press: "tren_superior_press_banca_inclinado_barra",
    db_dumbbell_bench_press: "tren_superior_press_banca_plano_mancuernas",
    db_incline_dumbbell_press: "tren_superior_press_banca_inclinado_mancuernas",
    db_dips: "tren_superior_fondos_paralelas",
    db_push_up: "tren_superior_flexiones_clasicas",
    db_cable_crossover: "tren_superior_cruce_poleas_altas",
    db_pull_up: "tren_superior_dominadas_pronas",
    db_chin_up: "tren_superior_dominadas_supinas",
    db_barbell_row: "tren_superior_remo_inclinado_prono_barra",
    db_dumbbell_row: "tren_superior_remo_una_mano_mancuerna",
    db_lat_pulldown: "tren_superior_jalon_pecho_prono",
    db_seated_cable_row: "tren_superior_remo_sentado_polea_baja",
    db_overhead_press: "tren_superior_press_militar_pie_barra",
    db_dumbbell_shoulder_press: "tren_superior_press_hombros_sentado_mancuernas",
    db_lateral_raise: "tren_superior_elevaciones_laterales_mancuernas",
    db_barbell_curl: "tren_superior_curl_biceps_barra_recta",
    db_triceps_pushdown: "tren_superior_extension_triceps_polea_cuerda",
    db_skull_crusher: "tren_superior_press_frances_barra_ez"
  };
  function enrichWithOperationalData(ex) {
    const hasCore = ex.involvedMuscles?.some(
      (m) => ["Core", "Abdomen", "Espalda Baja", "Recto Abdominal", "Transverso Abdominal"].includes(m.muscle) && (m.activation || 0) >= 0.3
    );
    const isCompound = ex.type === "B\xE1sico" && ["Barra", "Peso Corporal"].includes(ex.equipment || "");
    const isPull = ex.force === "Tir\xF3n" || ex.force === "Bisagra";
    const isHeavyPull = isPull && (ex.equipment === "Barra" || ex.subMuscleGroup?.toLowerCase().includes("dorsal"));
    return {
      ...ex,
      averageRestSeconds: ex.averageRestSeconds ?? (ex.type === "B\xE1sico" ? 120 : ex.type === "Aislamiento" ? 60 : 90),
      coreInvolvement: ex.coreInvolvement ?? (hasCore ? isCompound ? "high" : "medium" : "low"),
      bracingRecommended: ex.bracingRecommended ?? (isCompound && (ex.force === "Sentadilla" || ex.force === "Bisagra" || ex.force === "Empuje")),
      strapsRecommended: ex.strapsRecommended ?? (isHeavyPull && (ex.name?.toLowerCase().includes("peso muerto") || ex.name?.toLowerCase().includes("remo") || ex.name?.toLowerCase().includes("dominada") || ex.name?.toLowerCase().includes("jal\xF3n"))),
      bodybuildingScore: ex.bodybuildingScore ?? (ex.category === "Hipertrofia" ? ex.type === "B\xE1sico" ? 8 : ex.type === "Aislamiento" ? 7 : 7.5 : 6)
    };
  }
  function removeExactDuplicates(list) {
    const seen = /* @__PURE__ */ new Map();
    for (const ex of list) {
      const key = JSON.stringify({
        id: ex.id,
        name: ex.name,
        involvedMuscles: ex.involvedMuscles?.map((m) => ({ muscle: m.muscle, role: m.role, activation: m.activation })).sort((a, b) => a.muscle.localeCompare(b.muscle)),
        equipment: ex.equipment,
        type: ex.type
      });
      if (!seen.has(key)) seen.set(key, ex);
    }
    return Array.from(seen.values());
  }
  function removeDuplicateNames(list) {
    const seenNames = /* @__PURE__ */ new Set();
    const aliasMap = /* @__PURE__ */ new Map();
    const result = [];
    for (const ex of list) {
      const nameKey = ex.name.toLowerCase().trim();
      if (seenNames.has(nameKey)) {
        const first = result.find((e) => e.name.toLowerCase().trim() === nameKey);
        if (first) aliasMap.set(ex.id, first.id);
        continue;
      }
      seenNames.add(nameKey);
      result.push(ex);
    }
    return { deduplicated: result, aliasMap };
  }
  var merged = [...UPPER_BODY_EXERCISES, ...LOWER_BODY_EXERCISES, ...ULTIMO_LOTE_EXERCISES];
  var exactDeduped = removeExactDuplicates(merged);
  var { deduplicated: nameDeduped, aliasMap: nameAliasMap } = removeDuplicateNames(exactDeduped);
  var fullAliasMap = new Map(nameAliasMap);
  for (const [oldId, newId] of Object.entries(LOWER_BODY_ID_ALIASES)) {
    if (!fullAliasMap.has(oldId)) fullAliasMap.set(oldId, newId);
  }
  for (const [oldId, newId] of Object.entries(UPPER_BODY_ID_ALIASES)) {
    if (!fullAliasMap.has(oldId)) fullAliasMap.set(oldId, newId);
  }
  var EXERCISE_ID_ALIASES = fullAliasMap;
  var FULL_EXERCISE_LIST = nameDeduped.map(enrichWithOperationalData);

  // utils/exerciseIndex.ts
  function buildExerciseIndex(exerciseList) {
    const byId = /* @__PURE__ */ new Map();
    const byName = /* @__PURE__ */ new Map();
    for (const ex of exerciseList) {
      byId.set(ex.id, ex);
      byName.set(ex.name.toLowerCase(), ex);
    }
    for (const [aliasId, canonicalId] of EXERCISE_ID_ALIASES) {
      const canonical = byId.get(canonicalId);
      if (canonical) byId.set(aliasId, canonical);
    }
    return { byId, byName };
  }
  function findExercise(index, idOrDbId, name) {
    if (idOrDbId) {
      const byId = index.byId.get(idOrDbId);
      if (byId) return byId;
    }
    if (name) {
      return index.byName.get(name.toLowerCase());
    }
    return void 0;
  }
  function findExerciseByPartialName(index, name) {
    if (!name || name.trim().length < 4) return void 0;
    const normalized = name.toLowerCase().trim();
    let best;
    let bestLen = 0;
    for (const [dbName, info] of index.byName) {
      const dbBase = dbName.replace(/\s*\([^)]*\)/g, "").trim();
      const normDb = dbBase.toLowerCase();
      if (normDb.includes(normalized) || normalized.includes(normDb)) {
        if (dbBase.length > bestLen) {
          best = info;
          bestLen = dbBase.length;
        }
      }
    }
    return best;
  }
  function findExerciseWithFallback(index, idOrDbId, name) {
    const exact = findExercise(index, idOrDbId, name);
    if (exact) return exact;
    return findExerciseByPartialName(index, name);
  }

  // data/inferMusclesFromName.ts
  function inferInvolvedMuscles(name, equipment, force, bodyPart) {
    const n = name.toLowerCase();
    const eq = (equipment || "").toLowerCase();
    if (n.includes("sentadilla")) {
      if (n.includes("frontal") || n.includes("front squat")) {
        return [
          { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
          { muscle: "Core", role: "secondary", activation: 0.8 },
          { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 },
          { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }
        ];
      }
      if (n.includes("zercher")) {
        return [
          { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
          { muscle: "Core", role: "primary", activation: 0.9 },
          { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
          { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
        ];
      }
      if (n.includes("hack")) {
        return [
          { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
          { muscle: "Gl\xFAteos", role: "secondary", activation: 0.4 },
          { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.3 }
        ];
      }
      if (n.includes("goblet")) {
        return [
          { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
          { muscle: "Core", role: "secondary", activation: 0.7 },
          { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
        ];
      }
      if (n.includes("b\xFAlgara") || n.includes("bulgar")) {
        return [
          { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
          { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 },
          { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
        ];
      }
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
        { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 },
        { muscle: "Core", role: "stabilizer", activation: 0.4 }
      ];
    }
    if (n.includes("prensa") && (n.includes("pierna") || n.includes("piernas"))) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
        { muscle: "Isquiosurales", role: "stabilizer", activation: 0.3 }
      ];
    }
    if (n.includes("zancada") || n.includes("lunge")) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 },
        { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
      ];
    }
    if (n.includes("subida") && n.includes("caj\xF3n")) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 }
      ];
    }
    if (n.includes("extensi\xF3n") && (n.includes("cu\xE1driceps") || n.includes("cuadriceps"))) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("peso muerto") || n.includes("deadlift")) {
      return [
        { muscle: "Erectores Espinales", role: "primary", activation: 1 },
        { muscle: "Isquiosurales", role: "primary", activation: 0.9 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 },
        { muscle: "Dorsales", role: "secondary", activation: 0.5 },
        { muscle: "Trapecio", role: "stabilizer", activation: 0.5 }
      ];
    }
    if (n.includes("rumano") || n.includes("rdl") || n.includes("stiff")) {
      return [
        { muscle: "Isquiosurales", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 },
        { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
      ];
    }
    if (n.includes("buenos d\xEDas") || n.includes("good morning")) {
      return [
        { muscle: "Isquiosurales", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 },
        { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
      ];
    }
    if (n.includes("rack pull")) {
      return [
        { muscle: "Erectores Espinales", role: "primary", activation: 1 },
        { muscle: "Trapecio", role: "primary", activation: 0.9 },
        { muscle: "Dorsales", role: "secondary", activation: 0.6 },
        { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }
      ];
    }
    if (n.includes("hip thrust") || n.includes("empuje cadera")) {
      return [
        { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
        { muscle: "Isquiosurales", role: "secondary", activation: 0.5 },
        { muscle: "Core", role: "stabilizer", activation: 0.4 }
      ];
    }
    if (n.includes("puente") && n.includes("gl\xFAteo")) {
      return [
        { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
        { muscle: "Isquiosurales", role: "secondary", activation: 0.4 }
      ];
    }
    if (n.includes("pull-through") || n.includes("pull through")) {
      return [
        { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
        { muscle: "Isquiosurales", role: "secondary", activation: 0.6 },
        { muscle: "Core", role: "stabilizer", activation: 0.5 }
      ];
    }
    if (n.includes("curl") && (n.includes("femoral") || n.includes("isquio") || n.includes("n\xF3rdico") || n.includes("nordic"))) {
      return [
        { muscle: "Isquiosurales", role: "primary", activation: 1 },
        { muscle: "Gemelos", role: "secondary", activation: 0.4 }
      ];
    }
    if (n.includes("ghr") || n.includes("glute ham")) {
      return [
        { muscle: "Isquiosurales", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
        { muscle: "Gemelos", role: "stabilizer", activation: 0.4 }
      ];
    }
    if (n.includes("hiperextensi\xF3n") || n.includes("hiperextension")) {
      return [
        { muscle: "Erectores Espinales", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
        { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
      ];
    }
    if (n.includes("reverse hyper")) {
      return [
        { muscle: "Erectores Espinales", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 }
      ];
    }
    if (n.includes("gemelo") || n.includes("pantorrilla") || n.includes("calf") || n.includes("s\xF3leo") || n.includes("soleo")) {
      return [
        { muscle: "Pantorrillas", role: "primary", activation: 1 },
        { muscle: "Pantorrillas", role: "secondary", activation: 0.8 }
      ];
    }
    if (n.includes("kettlebell swing") || n.includes("swing")) {
      return [
        { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
        { muscle: "Isquiosurales", role: "secondary", activation: 0.6 },
        { muscle: "Core", role: "secondary", activation: 0.6 },
        { muscle: "Deltoides Anterior", role: "stabilizer", activation: 0.4 }
      ];
    }
    if (n.includes("press") && (n.includes("banca") || n.includes("pecho") || n.includes("bench"))) {
      return [
        { muscle: "Pectorales", role: "primary", activation: 1 },
        { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
        { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 },
        { muscle: "Trapecio", role: "stabilizer", activation: 0.3 }
      ];
    }
    if (n.includes("apertura") || n.includes("fly") || n.includes("cruces")) {
      return [
        { muscle: "Pectorales", role: "primary", activation: 1 },
        { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }
      ];
    }
    if (n.includes("flexi\xF3n") || n.includes("flexion") || n.includes("push-up") || n.includes("push up")) {
      return [
        { muscle: "Pectorales", role: "primary", activation: 1 },
        { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
        { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 },
        { muscle: "Abdomen", role: "stabilizer", activation: 0.5 }
      ];
    }
    if (n.includes("fondo") && !n.includes("entre bancos")) {
      return [
        { muscle: "Pectorales", role: "primary", activation: 1 },
        { muscle: "Tr\xEDceps", role: "secondary", activation: 0.8 },
        { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 }
      ];
    }
    if (n.includes("dominada") || n.includes("pull-up") || n.includes("chin-up")) {
      return [
        { muscle: "Dorsales", role: "primary", activation: 1 },
        { muscle: "B\xEDceps", role: "secondary", activation: 0.6 },
        { muscle: "Core", role: "stabilizer", activation: 0.4 }
      ];
    }
    if (n.includes("remo") || n.includes("row")) {
      return [
        { muscle: "Dorsales", role: "primary", activation: 1 },
        { muscle: "Trapecio", role: "secondary", activation: 0.7 },
        { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }
      ];
    }
    if (n.includes("jal\xF3n") || n.includes("pulldown") || n.includes("lat pulldown")) {
      return [
        { muscle: "Dorsales", role: "primary", activation: 1 },
        { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }
      ];
    }
    if (n.includes("face pull") || n.includes("tir\xF3n a la cara")) {
      return [
        { muscle: "Deltoides Posterior", role: "primary", activation: 1 },
        { muscle: "Trapecio", role: "secondary", activation: 0.7 }
      ];
    }
    if (n.includes("press") && (n.includes("militar") || n.includes("hombro") || n.includes("overhead") || n.includes("ohp"))) {
      return [
        { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
        { muscle: "Tr\xEDceps", role: "secondary", activation: 0.7 },
        { muscle: "Deltoides Lateral", role: "secondary", activation: 0.5 },
        { muscle: "Core", role: "stabilizer", activation: 0.5 }
      ];
    }
    if (n.includes("elevaci\xF3n") && (n.includes("lateral") || n.includes("laterales"))) {
      return [
        { muscle: "Deltoides Lateral", role: "primary", activation: 1 },
        { muscle: "Trapecio", role: "stabilizer", activation: 0.4 }
      ];
    }
    if (n.includes("elevaci\xF3n") && (n.includes("frontal") || n.includes("front"))) {
      return [
        { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
        { muscle: "Pectorales", role: "secondary", activation: 0.4 }
      ];
    }
    if (n.includes("curl") && (n.includes("b\xEDceps") || n.includes("biceps") || n.includes("martillo") || n.includes("predicador") || n.includes("concentrado") || n.includes("ara\xF1a") || n.includes("inclinado") || n.includes("polea") || n.includes("arrastre"))) {
      return [
        { muscle: "B\xEDceps", role: "primary", activation: 1 },
        { muscle: "Antebrazo", role: "secondary", activation: 0.5 },
        { muscle: "Antebrazo", role: "stabilizer", activation: 0.4 }
      ];
    }
    if (n.includes("extensi\xF3n") && n.includes("tr\xEDceps")) {
      return [
        { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("press franc\xE9s") || n.includes("skullcrusher") || n.includes("skull crusher")) {
      return [
        { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
        { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }
      ];
    }
    if (n.includes("extensi\xF3n trasnuca") || n.includes("overhead extension")) {
      return [
        { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("patada") && n.includes("tr\xEDceps")) {
      return [
        { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("fondos entre bancos") || n.includes("bench dip")) {
      return [
        { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
        { muscle: "Pectorales", role: "secondary", activation: 0.5 }
      ];
    }
    if (n.includes("extensi\xF3n tate") || n.includes("tate press")) {
      return [
        { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("curl") && n.includes("mu\xF1eca")) {
      return [
        { muscle: "Antebrazo", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("plancha") || n.includes("plank")) {
      return [
        { muscle: "Abdomen", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("rueda abdominal") || n.includes("ab wheel")) {
      return [
        { muscle: "Abdomen", role: "primary", activation: 1 },
        { muscle: "Dorsales", role: "stabilizer", activation: 0.5 }
      ];
    }
    if (n.includes("press pallof") || n.includes("pallof")) {
      return [
        { muscle: "Abdomen", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("le\xF1ador") || n.includes("woodchop")) {
      return [
        { muscle: "Abdomen", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("elevaci\xF3n") && n.includes("pierna")) {
      return [
        { muscle: "Abdomen", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("crunch") || n.includes("abdominal")) {
      return [
        { muscle: "Abdomen", role: "primary", activation: 1 }
      ];
    }
    if (n.includes("paseo") || n.includes("carry") || n.includes("granjero") || n.includes("farmers")) {
      return [
        { muscle: "Antebrazo", role: "primary", activation: 1 },
        { muscle: "Trapecio", role: "primary", activation: 0.9 },
        { muscle: "Core", role: "secondary", activation: 0.7 }
      ];
    }
    if (n.includes("yoke") || n.includes("atlas") || n.includes("tire") || n.includes("neum\xE1tico") || n.includes("sandbag") || n.includes("barril") || n.includes("keg")) {
      return [
        { muscle: "Erectores Espinales", role: "primary", activation: 1 },
        { muscle: "Core", role: "primary", activation: 0.9 },
        { muscle: "Trapecio", role: "secondary", activation: 0.7 }
      ];
    }
    if (n.includes("log press")) {
      return [
        { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
        { muscle: "Tr\xEDceps", role: "secondary", activation: 0.7 },
        { muscle: "Core", role: "stabilizer", activation: 0.6 }
      ];
    }
    if (n.includes("estiramiento") || n.includes("movilidad") || n.includes("rotaci\xF3n") || n.includes("cat cow") || n.includes("thread the needle") || n.includes("dislocat")) {
      if (n.includes("cadera") || n.includes("hip") || eq.includes("cadera")) {
        return [{ muscle: "Gl\xFAteos", role: "primary", activation: 0.5 }];
      }
      if (n.includes("hombro") || n.includes("shoulder")) {
        return [{ muscle: "Deltoides Anterior", role: "primary", activation: 0.5 }];
      }
      if (n.includes("tobillo") || n.includes("ankle")) {
        return [{ muscle: "Pantorrillas", role: "primary", activation: 0.5 }];
      }
      if (n.includes("mu\xF1eca") || n.includes("wrist")) {
        return [{ muscle: "Antebrazo", role: "primary", activation: 0.5 }];
      }
      if (n.includes("cu\xE1driceps") || n.includes("quad")) {
        return [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.5 }];
      }
      return [
        { muscle: "Erectores Espinales", role: "primary", activation: 0.5 },
        { muscle: "Core", role: "secondary", activation: 0.4 }
      ];
    }
    if (bodyPart === "lower") {
      if (force === "Sentadilla") {
        return [
          { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
          { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
        ];
      }
      if (force === "Bisagra") {
        return [
          { muscle: "Isquiosurales", role: "primary", activation: 1 },
          { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
        ];
      }
    }
    if (bodyPart === "upper" && (n.includes("press") || n.includes("empuje"))) {
      return [
        { muscle: "Pectorales", role: "primary", activation: 1 },
        { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
        { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }
      ];
    }
    if (bodyPart === "upper" && (n.includes("remo") || n.includes("tir\xF3n") || n.includes("curl"))) {
      return [
        { muscle: "Dorsales", role: "primary", activation: 1 },
        { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }
      ];
    }
    return [
      { muscle: "Core", role: "primary", activation: 0.8 },
      { muscle: "Erectores Espinales", role: "secondary", activation: 0.5 }
    ];
  }

  // services/fatigueService.ts
  var WEEKLY_CNS_FATIGUE_REFERENCE = 4e3;
  var getDynamicAugeMetrics = (info, customName) => {
    let efc = info?.efc || (info?.type === "B\xE1sico" ? 4 : info?.type === "Accesorio" ? 2.5 : 1.5);
    let ssc = info?.ssc ?? info?.axialLoadFactor ?? (info?.type === "B\xE1sico" ? 1 : 0.1);
    let cnc = info?.cnc || (info?.type === "B\xE1sico" ? 4 : info?.type === "Accesorio" ? 2.5 : 1.5);
    if (!info) return { efc, ssc, cnc };
    if (info.efc !== void 0 && info.cnc !== void 0 && info.ssc !== void 0) {
      return { efc: info.efc, ssc: info.ssc, cnc: info.cnc };
    }
    const name = (customName || info.name).toLowerCase();
    if (name.includes("peso muerto") || name.includes("deadlift")) {
      efc = 5;
      ssc = 2;
      cnc = 5;
      if (name.includes("rumano") || name.includes("rdl")) {
        efc = 4.2;
        ssc = 1.8;
        cnc = 4;
      }
      if (name.includes("sumo")) {
        efc = 4.8;
        ssc = 1.6;
        cnc = 4.8;
      }
    } else if (name.includes("sentadilla") || name.includes("squat")) {
      efc = 4.5;
      ssc = 1.5;
      cnc = 4.5;
      if (name.includes("frontal") || name.includes("front")) {
        efc = 4.2;
        ssc = 1.2;
        cnc = 4.5;
      }
      if (name.includes("b\xFAlgara") || name.includes("bulgarian")) {
        efc = 3.8;
        ssc = 0.8;
        cnc = 3.5;
      }
      if (name.includes("hack")) {
        efc = 3.5;
        ssc = 0.4;
        cnc = 3;
      }
    } else if (name.includes("press militar") || name.includes("ohp")) {
      efc = 4;
      ssc = 1.5;
      cnc = 4.2;
    } else if (name.includes("press banca") || name.includes("bench press")) {
      efc = 3.8;
      ssc = 0.3;
      cnc = 3.8;
    } else if (name.includes("dominada") || name.includes("pull-up")) {
      efc = 4;
      ssc = 0.2;
      cnc = 4;
    } else if (name.includes("remo") || name.includes("row")) {
      efc = 4.2;
      ssc = 1.6;
      cnc = 4;
      if (name.includes("seal") || name.includes("pecho apoyado")) {
        efc = 3.2;
        ssc = 0.1;
        cnc = 2.5;
      }
    } else if (name.includes("hip thrust") || name.includes("puente")) {
      efc = 3.5;
      ssc = 0.5;
      cnc = 3;
    } else if (name.includes("clean") || name.includes("snatch")) {
      efc = 4.8;
      ssc = 1.8;
      cnc = 5;
    }
    if (name.includes("mancuerna") || info.equipment === "Mancuerna") {
      cnc = Math.min(5, cnc + 0.2);
      ssc = Math.max(0, ssc - 0.2);
    } else if (name.includes("smith") || name.includes("multipower")) {
      cnc = Math.max(1, cnc - 0.5);
      efc = Math.max(1, efc - 0.2);
    } else if (name.includes("polea") || name.includes("cable") || info.equipment === "Polea") {
      cnc = Math.max(1, cnc - 0.3);
      efc = Math.min(5, efc + 0.2);
    }
    if (name.includes("pausa") || name.includes("paused")) {
      cnc = Math.min(5, cnc + 0.3);
      efc = Math.min(5, efc + 0.5);
    }
    if (name.includes("d\xE9ficit") || name.includes("deficit")) {
      ssc = Math.min(2, ssc + 0.2);
      efc = Math.min(5, efc + 0.3);
    }
    if (name.includes("parcial") || name.includes("rack pull") || name.includes("block")) {
      ssc = Math.min(2, ssc + 0.2);
      efc = Math.max(1, efc - 0.2);
    }
    return { efc, ssc, cnc };
  };
  var getEffectiveRPE = (set) => {
    let baseRpe = 7;
    if (set.completedRPE !== void 0) baseRpe = set.completedRPE;
    else if (set.targetRPE !== void 0) baseRpe = set.targetRPE;
    else if (set.completedRIR !== void 0) baseRpe = 10 - set.completedRIR;
    else if (set.targetRIR !== void 0) baseRpe = 10 - set.targetRIR;
    if (set.isFailure || set.performanceMode === "failure" || set.intensityMode === "failure" || set.isAmrap) {
      baseRpe = Math.max(baseRpe, 11);
    }
    let techniqueBonus = 0;
    if (set.dropSets && set.dropSets.length > 0) techniqueBonus += set.dropSets.length * 1.5;
    if (set.restPauses && set.restPauses.length > 0) techniqueBonus += set.restPauses.length * 1;
    if (set.partialReps && set.partialReps > 0) techniqueBonus += 0.5;
    if (techniqueBonus > 0 && baseRpe < 10) baseRpe = 10;
    return baseRpe + techniqueBonus;
  };
  var calculatePersonalizedBatteryTanks = (settings) => {
    let baseMuscular = 300;
    let baseCns = 250;
    let baseSpinal = 4e3;
    const level = settings?.athleteScore?.profileLevel || "Advanced";
    const levelMult = level === "Beginner" ? 0.8 : 1.2;
    const style = (settings?.athleteScore?.trainingStyle || settings?.athleteType || "Bodybuilder").toLowerCase();
    let cnsMult = 1, muscMult = 1, spineMult = 1;
    if (style.includes("powerlift")) {
      cnsMult = 1.3;
      spineMult = 1.4;
      muscMult = 0.9;
    } else if (style.includes("bodybuild") || style.includes("aesthetics")) {
      cnsMult = 0.9;
      spineMult = 0.9;
      muscMult = 1.3;
    } else {
      cnsMult = 1.15;
      spineMult = 1.15;
      muscMult = 1.15;
    }
    return {
      muscularTank: baseMuscular * levelMult * muscMult,
      cnsTank: baseCns * levelMult * cnsMult,
      spinalTank: baseSpinal * levelMult * spineMult
    };
  };
  var calculateSetBatteryDrain = (set, info, tanks, accumulatedSetsForMuscle = 0, restTime = 90) => {
    const auge = getDynamicAugeMetrics(info, set.exerciseName || info?.name);
    const rpe = getEffectiveRPE(set);
    const reps = set.completedReps || set.targetReps || set.reps || 10;
    const isCompound = info?.type === "B\xE1sico" || info?.tier === "T1";
    let repsCnsMult = 1, repsMuscMult = 1, repsSpineMult = 1;
    if (reps <= 4) {
      if (isCompound) {
        repsCnsMult = 1.8;
        repsSpineMult = 1.6;
        repsMuscMult = 0.7;
      } else {
        repsCnsMult = 1.2;
        repsSpineMult = 0.1;
        repsMuscMult = 0.8;
      }
    } else if (reps >= 16) {
      repsCnsMult = 0.7;
      repsSpineMult = 0.5;
      repsMuscMult = 1.4;
    }
    let intensityMult = 1;
    if (rpe >= 11) intensityMult = 1.8;
    else if (rpe >= 10) intensityMult = 1.5;
    else if (rpe >= 9) intensityMult = 1.15;
    else if (rpe >= 8) intensityMult = 1;
    else if (rpe >= 6) intensityMult = 0.7;
    else intensityMult = 0.4;
    let junkVolumeMult = 1;
    if (accumulatedSetsForMuscle >= 6) {
      junkVolumeMult = 1 + (accumulatedSetsForMuscle - 5) * 0.35;
    }
    let restFactor = 1;
    if (restTime <= 45) restFactor = 1.3;
    else if (restTime >= 180) restFactor = 0.85;
    const partialReps = set.partialReps || 0;
    const junkVolumeFromPartials = partialReps > 0 ? 1 + partialReps * 0.2 : 1;
    const rawMuscular = auge.efc * repsMuscMult * intensityMult * junkVolumeMult * restFactor * junkVolumeFromPartials * 8;
    const rawCns = auge.cnc * repsCnsMult * intensityMult * restFactor * 6;
    const weightFactor = set.weight ? set.weight * 0.05 : auge.efc * 2;
    const rawSpinal = auge.ssc * repsSpineMult * intensityMult * weightFactor * 4;
    return {
      muscularDrainPct: rawMuscular / tanks.muscularTank * 100,
      cnsDrainPct: rawCns / tanks.cnsTank * 100,
      spinalDrainPct: rawSpinal / tanks.spinalTank * 100
    };
  };
  var calculatePredictedSessionDrain = (session, exerciseList, settings) => {
    const tanks = calculatePersonalizedBatteryTanks(settings);
    const exIndex = buildExerciseIndex(exerciseList);
    let totalCnsPct = 0;
    let totalMuscularPct = 0;
    let totalSpinalPct = 0;
    const muscleVolumeMap = {};
    const exercises = session.parts ? session.parts.flatMap((p) => p.exercises) : session.exercises;
    exercises?.forEach((ex) => {
      const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.name ?? ex.exerciseName);
      const name = ex.name ?? ex.exerciseName ?? "";
      const primaryMuscle = info?.involvedMuscles?.find((m) => m.role === "primary")?.muscle || inferInvolvedMuscles(name, ex.equipment ?? "", "Otro", "upper")[0]?.muscle || "Core";
      let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;
      ex.sets?.forEach((s) => {
        if (s.type === "warmup") return;
        accumulatedSets += 1;
        const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, ex.restTime || 90);
        totalMuscularPct += drain.muscularDrainPct;
        totalCnsPct += drain.cnsDrainPct;
        totalSpinalPct += drain.spinalDrainPct;
      });
      muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });
    return {
      cnsDrain: Math.round(Math.min(100, totalCnsPct)),
      muscleBatteryDrain: Math.round(Math.min(100, totalMuscularPct)),
      spinalDrain: Math.round(Math.min(100, totalSpinalPct)),
      totalSpinalScore: Math.round(totalSpinalPct * 10)
      // Valor de referencia extra
    };
  };
  var calculateSetStress = (set, info, restTime = 90) => {
    const defaultTanks = calculatePersonalizedBatteryTanks({});
    const drain = calculateSetBatteryDrain(set, info, defaultTanks, 0, restTime);
    return drain.muscularDrainPct;
  };
  var isSetEffective = (set) => {
    const rpe = getEffectiveRPE(set);
    if (rpe > 6) return true;
    const rir = set.completedRIR ?? set.targetRIR;
    if (rir !== void 0 && rir < 4) return true;
    if (set.isFailure || set.performanceMode === "failure" || set.intensityMode === "failure") return true;
    if (set.intensityMode === "solo_rm") return true;
    if (set.restPauses?.length > 0 && set.restPauses.some((rp) => (rp.reps ?? 0) > 0)) return true;
    if (set.dropSets?.length > 0 && set.dropSets.some((ds) => (ds.reps ?? 0) > 0)) return true;
    if (set.isAmrap) return true;
    return rpe >= 6;
  };
  var calculateCompletedSessionStress = (completedExercises, exerciseList) => {
    const tanks = calculatePersonalizedBatteryTanks({});
    let totalStress = 0;
    const muscleVolumeMap = {};
    const exIndex = buildExerciseIndex(exerciseList);
    completedExercises.forEach((ex) => {
      const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
      const primaryMuscle = info?.involvedMuscles?.find((m) => m.role === "primary")?.muscle || inferInvolvedMuscles(ex.exerciseName ?? "", "", "Otro", "upper")[0]?.muscle || "Core";
      let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;
      ex.sets.forEach((s) => {
        if (s.type === "warmup") return;
        accumulatedSets += 1;
        const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, 90);
        totalStress += drain.cnsDrainPct + drain.muscularDrainPct + drain.spinalDrainPct;
      });
      muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });
    return totalStress;
  };

  // services/nutritionRecoveryService.ts
  function computeNutritionRecoveryMultiplier(input) {
    const {
      nutritionLogs,
      settings,
      stressLevel = 3,
      hoursWindow = 48
    } = input;
    const factors = [];
    let multiplier = 1;
    const now = Date.now();
    const windowStart = now - hoursWindow * 36e5;
    const recentLogs = nutritionLogs.filter((n) => new Date(n.date).getTime() > windowStart);
    const calorieGoal = settings.dailyCalorieGoal;
    const proteinGoal = settings.dailyProteinGoal || 150;
    if (recentLogs.length === 0) {
      const fallback = settings.calorieGoalObjective || "maintenance";
      if (fallback === "deficit") {
        multiplier = 1.25;
        factors.push("Sin datos recientes; asumiendo d\xE9ficit seg\xFAn objetivo.");
      } else if (fallback === "surplus") {
        multiplier = 0.95;
        factors.push("Sin datos recientes; asumiendo super\xE1vit seg\xFAn objetivo.");
      }
      return {
        recoveryTimeMultiplier: multiplier,
        status: fallback,
        factors
      };
    }
    let totalCal = 0, totalProtein = 0;
    recentLogs.forEach((log) => {
      (log.foods || []).forEach((f) => {
        totalCal += f.calories || 0;
        totalProtein += f.protein || 0;
      });
    });
    const daysInWindow = Math.max(1, hoursWindow / 24);
    const avgCalories = totalCal / daysInWindow;
    const avgProtein = totalProtein / daysInWindow;
    const calRatio = calorieGoal && calorieGoal > 0 ? avgCalories / calorieGoal : 1;
    const proteinRatio = proteinGoal > 0 ? avgProtein / proteinGoal : 1;
    let status = "maintenance";
    if (calRatio < 0.9) {
      status = "deficit";
      const deficitSeverity = 1 - calRatio;
      multiplier = 1 + deficitSeverity * 1.2;
      factors.push(`D\xE9ficit cal\xF3rico (~${Math.round((1 - calRatio) * 100)}%). Recursos limitados para reparaci\xF3n.`);
      if (proteinRatio < 0.7) {
        multiplier *= 1.1;
        factors.push("Prote\xEDna insuficiente agrava el d\xE9ficit.");
      }
    } else if (calRatio <= 1.1) {
      status = "maintenance";
      if (proteinRatio < 0.8) {
        multiplier = 1.05;
        factors.push("Prote\xEDna por debajo del objetivo; ligera penalizaci\xF3n.");
      } else {
        factors.push("Mantenimiento cal\xF3rico. Recuperaci\xF3n est\xE1ndar.");
      }
    } else {
      status = "surplus";
      const surplusPct = (calRatio - 1) * 100;
      if (proteinRatio < 0.6) {
        multiplier = 1.05;
        factors.push("Super\xE1vit sin suficiente prote\xEDna. La s\xEDntesis muscular est\xE1 limitada.");
      } else if (proteinRatio < 0.8) {
        const baseBenefit = surplusPct < 15 ? 0.92 : surplusPct < 25 ? 0.88 : 0.92;
        multiplier = baseBenefit;
        factors.push(`Super\xE1vit moderado (~${Math.round(surplusPct)}%) con prote\xEDna sub\xF3ptima. Beneficio limitado.`);
      } else {
        if (surplusPct < 8) {
          multiplier = 0.96;
          factors.push(`Super\xE1vit ligero (~${Math.round(surplusPct)}%). Peque\xF1a mejora en recuperaci\xF3n.`);
        } else if (surplusPct < 18) {
          multiplier = 0.86;
          factors.push(`Super\xE1vit \xF3ptimo (~${Math.round(surplusPct)}%). Recuperaci\xF3n acelerada.`);
        } else if (surplusPct < 30) {
          multiplier = 0.9;
          factors.push(`Super\xE1vit alto (~${Math.round(surplusPct)}%). Beneficio decreciente.`);
        } else {
          multiplier = 0.96;
          factors.push(`Super\xE1vit muy alto (~${Math.round(surplusPct)}%). Rendimientos decrecientes; no acelera m\xE1s.`);
        }
      }
      if (stressLevel >= 4 && multiplier < 1) {
        multiplier = Math.min(1, multiplier + 0.06);
        factors.push("Estr\xE9s elevado reduce parte del beneficio nutricional.");
      }
    }
    return {
      recoveryTimeMultiplier: Math.max(0.6, Math.min(1.6, multiplier)),
      status,
      factors
    };
  }

  // utils/dateUtils.ts
  function getLocalDateString(d) {
    const date = d ?? /* @__PURE__ */ new Date();
    const y = date.getFullYear();
    const m = (date.getMonth() + 1).toString().padStart(2, "0");
    const day = date.getDate().toString().padStart(2, "0");
    return `${y}-${m}-${day}`;
  }
  function getDatePartFromString(dateStr) {
    if (!dateStr) return getLocalDateString();
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) return dateStr;
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return getLocalDateString();
    return getLocalDateString(d);
  }
  function parseDateStringAsLocal(dateStr) {
    if (!dateStr) return /* @__PURE__ */ new Date();
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
      const [y, m, d2] = dateStr.split("-").map(Number);
      return new Date(y, m - 1, d2);
    }
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return /* @__PURE__ */ new Date();
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }

  // data/initialMuscleGroupDatabase.ts
  var INITIAL_MUSCLE_GROUP_DATA = [
    // --- CATEGORÍAS PRINCIPALES ---
    {
      id: "pectoral",
      name: "Pectoral",
      description: "El pectoral mayor y menor son los principales m\xFAsculos del pecho, responsables de la aducci\xF3n, flexi\xF3n y rotaci\xF3n interna del brazo.",
      importance: {
        movement: "Fundamental para todos los movimientos de empuje, como empujar una puerta o levantar algo del suelo.",
        health: "Un pectoral fuerte y flexible contribuye a una buena postura y a la salud del hombro."
      },
      volumeRecommendations: { mev: "10", mav: "12-20", mrv: "22" },
      coverImage: "https://images.pexels.com/photos/4162489/pexels-photo-4162489.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2",
      aestheticImportance: 'El pectoral define la parte frontal del torso. Un pecho desarrollado aporta anchura y profundidad al upper body, creando la silueta en "V". La porci\xF3n superior (clavicular) es especialmente visible con camiseta y aporta definici\xF3n al escote.'
    },
    {
      id: "espalda",
      name: "Espalda",
      description: "Un complejo grupo de m\xFAsculos que incluye los dorsales, trapecios, romboides y erectores espinales. Son cruciales para la postura y los movimientos de tracci\xF3n.",
      importance: {
        movement: "Esencial para tirar de objetos, trepar y mantener una postura erguida.",
        health: "Una espalda fuerte es la base de una columna vertebral sana y previene el dolor lumbar."
      },
      volumeRecommendations: { mev: "10", mav: "14-22", mrv: "25" },
      coverImage: "https://images.pexels.com/photos/14878278/pexels-photo-14878278.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2"
    },
    {
      id: "hombros",
      name: "Hombros",
      description: "Los deltoides son el principal m\xFAsculo del hombro, dividido en tres cabezas (anterior, lateral y posterior). Son responsables de la abducci\xF3n, flexi\xF3n y extensi\xF3n del brazo.",
      importance: {
        movement: "Permiten levantar los brazos en todas las direcciones. Cruciales para la mayor\xEDa de movimientos del tren superior.",
        health: "Unos hombros fuertes y estables son vitales para prevenir lesiones, especialmente en la articulaci\xF3n del hombro, que es muy m\xF3vil."
      },
      volumeRecommendations: { mev: "8", mav: "12-20", mrv: "25" }
    },
    {
      id: "deltoides",
      name: "Deltoides",
      description: "El m\xFAsculo deltoides es el que da la forma redondeada al hombro. Est\xE1 compuesto por tres cabezas: anterior, lateral y posterior, cada una con una funci\xF3n distinta.",
      importance: { movement: "Responsable de levantar el brazo en todas las direcciones (flexi\xF3n, abducci\xF3n, extensi\xF3n). Esencial para cualquier movimiento por encima de la cabeza.", health: "Un desarrollo equilibrado de las tres cabezas es crucial para la salud y estabilidad de la articulaci\xF3n del hombro, previniendo lesiones por desbalances." },
      volumeRecommendations: { mev: "8", mav: "12-20", mrv: "25" },
      aestheticImportance: 'Los deltoides dan la forma redondeada al hombro y son clave para la anchura visual del cuerpo. Un desarrollo equilibrado de las tres cabezas (anterior, lateral, posterior) crea hombros completos y evita desbalances. La cabeza lateral es la m\xE1s visible y aporta la mayor parte de la "anchura" de hombros.'
    },
    {
      id: "brazos",
      name: "Brazos",
      description: "Compuestos principalmente por el b\xEDceps y el tr\xEDceps. El b\xEDceps flexiona el codo, mientras que el tr\xEDceps lo extiende.",
      importance: {
        movement: "Involucrados en casi todas las acciones de empuje (tr\xEDceps) y tracci\xF3n (b\xEDceps).",
        health: "Contribuyen a la salud y estabilidad de las articulaciones del codo y hombro."
      },
      volumeRecommendations: { mev: "8", mav: "14-20", mrv: "25" }
    },
    {
      id: "piernas",
      name: "Piernas",
      description: "El grupo muscular m\xE1s grande del cuerpo, incluye cu\xE1driceps, isquiotibiales, gl\xFAteos y gemelos. Responsables de la locomoci\xF3n y la producci\xF3n de fuerza.",
      importance: {
        movement: "Fundamentales para caminar, correr, saltar y levantar objetos pesados. Son el motor principal del cuerpo.",
        health: "Unas piernas fuertes mejoran el metabolismo, la salud cardiovascular y la estabilidad de las rodillas y caderas."
      },
      volumeRecommendations: { mev: "8", mav: "12-18", mrv: "20" }
    },
    {
      id: "abdomen",
      name: "Abdomen",
      description: "Incluye el recto abdominal y los oblicuos. Son responsables de la flexi\xF3n y rotaci\xF3n del tronco.",
      importance: {
        movement: "Permiten doblar el torso y girar. Son clave en la transferencia de fuerza entre el tren superior e inferior.",
        health: "Un abdomen fuerte ayuda a proteger la columna lumbar y a mantener una buena postura."
      },
      volumeRecommendations: { mev: "0-4", mav: "10-16", mrv: "20" }
    },
    {
      id: "core",
      name: "Core",
      description: "El Core es un complejo de m\xFAsculos que estabilizan la columna y la pelvis. No solo incluye los abdominales, sino tambi\xE9n la espalda baja, gl\xFAteos y el transverso abdominal. Su funci\xF3n principal es la estabilidad y la transferencia de fuerzas.",
      importance: {
        movement: "Es la base de todo movimiento. Un core fuerte permite transferir eficientemente la fuerza desde el tren inferior al superior, aumentando el rendimiento en todos los levantamientos y deportes.",
        health: "Un core estable es la mejor protecci\xF3n contra el dolor de espalda baja y las lesiones relacionadas con la inestabilidad de la columna."
      },
      volumeRecommendations: { mev: "4-6", mav: "10-16", mrv: "20" }
    },
    // --- MÚSCULOS ESPECÍFICOS ---
    // Pecho
    { id: "pectoral-superior", name: "Pectoral Superior", description: "La porci\xF3n clavicular del pectoral, responsable de la flexi\xF3n y aducci\xF3n del hombro en \xE1ngulos elevados.", importance: { movement: "Crucial para empujes inclinados y levantar objetos por encima de la cabeza.", health: "Un desarrollo equilibrado con el resto del pectoral es clave para la est\xE9tica y la salud del hombro." }, volumeRecommendations: { mev: "4-6", mav: "10-12", mrv: "15" }, origin: "Mitad medial de la clav\xEDcula", insertion: "Cresta del tub\xE9rculo mayor del h\xFAmero", mechanicalFunctions: ["Flexi\xF3n del hombro", "Aducci\xF3n horizontal", "Rotaci\xF3n interna"], relatedJoints: ["glenohumeral"], relatedTendons: [], commonInjuries: [{ name: "Tir\xF3n pectoral", description: "Desgarro de las fibras del pectoral por sobrecarga.", riskExercises: ["db_bench_press_tng", "db_dips"], contraindications: ["Evitar cargas m\xE1ximas en fase aguda"], returnProgressions: ["Press con banda", "Flexiones", "Progresi\xF3n a press"] }], movementPatterns: ["horizontal-push", "vertical-push"] },
    { id: "pectoral-medio", name: "Pectoral Medio", description: "La porci\xF3n esternal del pectoral, la m\xE1s grande y fuerte. Es la principal responsable de la aducci\xF3n horizontal del brazo (abrazar).", importance: { movement: "Motor principal en el press de banca plano y otros empujes horizontales.", health: "Fundamental para la fuerza de empuje general." }, volumeRecommendations: { mev: "6", mav: "10-16", mrv: "20" }, origin: "Estern\xF3n y cart\xEDlagos costales", insertion: "Cresta del tub\xE9rculo mayor del h\xFAmero", mechanicalFunctions: ["Aducci\xF3n horizontal", "Flexi\xF3n del hombro", "Rotaci\xF3n interna"], relatedJoints: ["glenohumeral"], relatedTendons: [], commonInjuries: [{ name: "Tir\xF3n pectoral", description: "Desgarro de las fibras del pectoral.", riskExercises: ["db_bench_press_tng"], contraindications: ["Evitar cargas m\xE1ximas"], returnProgressions: ["Progresi\xF3n gradual"] }], movementPatterns: ["horizontal-push"] },
    { id: "pectoral-inferior", name: "Pectoral Inferior", description: "La porci\xF3n abdominal del pectoral. Se encarga de la aducci\xF3n y depresi\xF3n del hombro.", importance: { movement: "Clave en movimientos de empuje declinado y fondos en paralelas.", health: "Contribuye al contorno inferior del pecho." }, volumeRecommendations: { mev: "2-4", mav: "6-10", mrv: "12" } },
    // Espalda
    { id: "dorsal-ancho", name: "Dorsal Ancho", description: 'El m\xFAsculo m\xE1s grande de la espalda, responsable de la aducci\xF3n, extensi\xF3n y rotaci\xF3n interna del brazo. Da la "amplitud" a la espalda.', importance: { movement: "Es el motor principal en movimientos de tracci\xF3n vertical como las dominadas y la escalada.", health: "Contribuye a la estabilidad de la columna y la articulaci\xF3n del hombro." }, volumeRecommendations: { mev: "8", mav: "12-18", mrv: "22" }, aestheticImportance: 'El dorsal ancho es el motor principal de la anchura de espalda. Da la forma en "V" al torso: cuanto m\xE1s desarrollado, m\xE1s anchura visual y mejor proporci\xF3n con la cintura. Es uno de los m\xFAsculos m\xE1s impactantes en la est\xE9tica del cuerpo.', origin: "Ap\xF3fisis espinosas T7-L5, cresta il\xEDaca, costillas inferiores", insertion: "Surco intertubercular del h\xFAmero", mechanicalFunctions: ["Aducci\xF3n del hombro", "Extensi\xF3n del hombro", "Rotaci\xF3n interna", "Depresi\xF3n escapular"], relatedJoints: ["glenohumeral", "escapulotoracica"], relatedTendons: [], commonInjuries: [{ name: "Tir\xF3n de dorsal", description: "Desgarro de las fibras del dorsal.", riskExercises: ["db_pull_up", "db_lat_pulldown"], returnProgressions: ["Remo con banda", "Progresi\xF3n a dominadas"] }], movementPatterns: ["vertical-pull", "horizontal-pull"] },
    { id: "redondo-mayor", name: "Redondo Mayor", description: 'A menudo llamado "el peque\xF1o ayudante del dorsal", asiste en la aducci\xF3n y extensi\xF3n del hombro.', importance: { movement: "Trabaja en sinergia con el dorsal en todos los movimientos de tracci\xF3n.", health: "Contribuye a la salud y movilidad de la esc\xE1pula." }, volumeRecommendations: { mev: "0", mav: "4-8", mrv: "12" } },
    { id: "trapecio", name: "Trapecio", description: "M\xFAsculo grande y superficial que se extiende desde el cr\xE1neo hasta la mitad de la espalda. Se divide en porciones superior, media e inferior, cada una con funciones distintas.", importance: { movement: "Responsable de elevar, retraer y deprimir la esc\xE1pula. Esencial para la estabilidad del hombro y movimientos de tracci\xF3n.", health: "Unos trapecios fuertes y equilibrados son clave para una buena postura y la prevenci\xF3n de dolores de cuello y hombros." }, volumeRecommendations: { mev: "4", mav: "12-20", mrv: "25" } },
    { id: "trapecio-superior", name: "Trapecio Superior", description: "Responsable de la elevaci\xF3n de la esc\xE1pula (encoger los hombros).", importance: { movement: "Clave para sostener cargas pesadas (paseo del granjero, peso muerto) y en movimientos por encima de la cabeza.", health: "A menudo est\xE1 sobreactivo y tenso; es importante equilibrarlo con el fortalecimiento del trapecio inferior." }, volumeRecommendations: { mev: "0", mav: "8-12", mrv: "20" } },
    { id: "trapecio-medio", name: "Trapecio Medio", description: "Responsable de la retracci\xF3n escapular (juntar los om\xF3platos).", importance: { movement: "Fundamental para la estabilidad en todos los movimientos de remo y press de banca.", health: "Esencial para una buena postura y la salud del hombro, contrarrestando la protracci\xF3n de hombros." }, volumeRecommendations: { mev: "6", mav: "12-16", mrv: "20" } },
    { id: "trapecio-inferior", name: "Trapecio Inferior", description: "Responsable de la depresi\xF3n de la esc\xE1pula (bajar los om\xF3platos).", importance: { movement: "Act\xFAa como estabilizador en presses por encima de la cabeza.", health: "Crucial para la salud del hombro y una correcta mec\xE1nica escapular. A menudo es un m\xFAsculo d\xE9bil." }, volumeRecommendations: { mev: "4", mav: "8-12", mrv: "15" } },
    { id: "romboides", name: "Romboides", description: "M\xFAsculos ubicados entre la columna y la esc\xE1pula. Su funci\xF3n principal es la retracci\xF3n escapular.", importance: { movement: "Trabajan junto al trapecio medio en todos los movimientos de remo.", health: "Vitales para una postura erguida y la salud del hombro." }, volumeRecommendations: { mev: "6", mav: "12-16", mrv: "20" } },
    { id: "erectores-espinales", name: "Erectores Espinales", description: "Grupo de m\xFAsculos que recorren la columna vertebral. Su funci\xF3n es extender la columna y mantener una postura erguida.", importance: { movement: "Fundamentales para la estabilizaci\xF3n en sentadillas, pesos muertos y cualquier levantamiento pesado.", health: "Una musculatura espinal fuerte es la mejor defensa contra el dolor de espalda baja." }, volumeRecommendations: { mev: "0", mav: "4-8", mrv: "12" } },
    // Hombros
    { id: "deltoides-anterior", name: "Deltoides Anterior", description: "La cabeza frontal del hombro. Es responsable de la flexi\xF3n y rotaci\xF3n interna del brazo.", importance: { movement: "Principal motor en los presses verticales y actor secundario en los presses horizontales.", health: "Suele estar sobre-desarrollado en comparaci\xF3n con las otras cabezas, lo que puede causar desbalances posturales." }, volumeRecommendations: { mev: "0-4", mav: "6-10", mrv: "12" }, origin: "Tercio lateral de la clav\xEDcula", insertion: "Tub\xE9rculo deltoideo del h\xFAmero", mechanicalFunctions: ["Flexi\xF3n del hombro", "Rotaci\xF3n interna", "Aducci\xF3n horizontal"], relatedJoints: ["glenohumeral"], relatedTendons: [], commonInjuries: [{ name: "Impingement", description: "Suele asociarse a desbalances con el deltoides posterior.", returnProgressions: ["Face pulls", "Press con agarre neutro"] }], movementPatterns: ["horizontal-push", "vertical-push"] },
    { id: "deltoides-lateral", name: "Deltoides Lateral", description: "La cabeza media del hombro. Es responsable de la abducci\xF3n del brazo (levantarlo hacia el lado).", importance: { movement: 'Permite levantar objetos hacia los lados y es el principal contribuyente a la "anchura" de los hombros.', health: "Un desarrollo equilibrado es clave para la est\xE9tica y la salud del hombro." }, volumeRecommendations: { mev: "6-8", mav: "12-20", mrv: "25" } },
    { id: "deltoides-posterior", name: "Deltoides Posterior", description: "La cabeza trasera del hombro. Se encarga de la extensi\xF3n y rotaci\xF3n externa del brazo.", importance: { movement: "Fundamental en movimientos de tracci\xF3n horizontal (remos, face pulls).", health: "Crucial para una buena postura (contrarresta los hombros adelantados) y la estabilidad de la articulaci\xF3n del hombro." }, volumeRecommendations: { mev: "6-8", mav: "12-20", mrv: "25" } },
    { id: "supraespinoso", name: "Supraespinoso", description: "M\xFAsculo del manguito rotador ubicado en la fosa supraespinosa. Inicia la abducci\xF3n del hombro (primeros 15\xB0) y estabiliza la cabeza humeral.", importance: { movement: "Clave en la elevaci\xF3n del brazo y la estabilidad del hombro. Muy susceptible a tendinopat\xEDa e impingement.", health: "Su tend\xF3n es el m\xE1s afectado en el s\xEDndrome subacromial." }, volumeRecommendations: { mev: "0", mav: "4-8", mrv: "12" }, origin: "Fosa supraespinosa de la esc\xE1pula", insertion: "Tub\xE9rculo mayor del h\xFAmero", mechanicalFunctions: ["Abducci\xF3n del hombro", "Estabilizaci\xF3n glenohumeral"], relatedJoints: ["glenohumeral"], relatedTendons: ["tendon-supraespinoso"], commonInjuries: [{ name: "Tendinopat\xEDa del supraespinoso", description: "Degeneraci\xF3n del tend\xF3n por sobreuso o impingement.", riskExercises: ["db_bench_press_tng", "db_overhead_press"], returnProgressions: ["Rotaciones externas", "Face pulls", "Progresi\xF3n a press"] }] },
    { id: "infraespinoso", name: "Infraespinoso", description: "M\xFAsculo del manguito rotador en la fosa infraespinosa. Principal rotador externo del hombro.", importance: { movement: "Fundamental para la rotaci\xF3n externa y la estabilidad posterior del hombro.", health: "Desbalances con el subescapular pueden causar inestabilidad." }, volumeRecommendations: { mev: "0", mav: "4-8", mrv: "12" }, origin: "Fosa infraespinosa de la esc\xE1pula", insertion: "Tub\xE9rculo mayor del h\xFAmero", mechanicalFunctions: ["Rotaci\xF3n externa del hombro", "Estabilizaci\xF3n glenohumeral"], relatedJoints: ["glenohumeral"], relatedTendons: ["tendon-infraespinoso"], commonInjuries: [{ name: "Tendinopat\xEDa del infraespinoso", description: "Suele asociarse a desbalances de rotadores.", riskExercises: ["db_bench_press_tng"], returnProgressions: ["Rotaciones externas", "Face pulls"] }] },
    // Brazos
    { id: "b\xEDceps", name: "B\xEDceps", description: "El b\xEDceps braquial, ubicado en la parte frontal del brazo. Est\xE1 compuesto por una cabeza larga y una corta, y su funci\xF3n principal es la flexi\xF3n del codo y la supinaci\xF3n del antebrazo.", importance: { movement: "Esencial para todos los movimientos de tracci\xF3n y para levantar objetos.", health: "Contribuye a la estabilidad de la articulaci\xF3n del codo." }, volumeRecommendations: { mev: "8", mav: "14-20", mrv: "22" }, aestheticImportance: 'El b\xEDceps es el m\xFAsculo m\xE1s visible del brazo en flexi\xF3n y uno de los m\xE1s populares en culturismo. El "pico" del b\xEDceps (cabeza larga) y el grosor (cabeza corta) definen la est\xE9tica del brazo. Contribuye a la simetr\xEDa y proporci\xF3n del tren superior.', origin: "Cabeza larga: tub\xE9rculo supraglenoideo. Cabeza corta: ap\xF3fisis coracoidea", insertion: "Tuberosidad radial", mechanicalFunctions: ["Flexi\xF3n del codo", "Supinaci\xF3n del antebrazo", "Flexi\xF3n del hombro (cabeza larga)"], relatedJoints: ["codo", "glenohumeral"], relatedTendons: ["tendon-b\xEDceps", "tendon-b\xEDceps-largo"], commonInjuries: [{ name: "Tendinopat\xEDa del b\xEDceps", description: "Dolor en el tend\xF3n del b\xEDceps.", riskExercises: ["db_barbell_curl"], returnProgressions: ["Curl martillo", "Progresi\xF3n a curl"] }], movementPatterns: ["horizontal-pull", "vertical-pull"] },
    { id: "cabeza-larga-b\xEDceps", name: "Cabeza Larga (B\xEDceps)", description: "Porci\xF3n externa del b\xEDceps. Es m\xE1s activa cuando el brazo est\xE1 extendido detr\xE1s del cuerpo (estiramiento).", importance: { movement: 'Contribuye al "pico" del b\xEDceps. Se enfatiza en ejercicios como el curl inclinado.', health: "Juega un rol importante en la estabilizaci\xF3n de la articulaci\xF3n del hombro." }, volumeRecommendations: { mev: "4", mav: "8-12", mrv: "15" } },
    { id: "cabeza-corta-b\xEDceps", name: "Cabeza Corta (B\xEDceps)", description: "Porci\xF3n interna del b\xEDceps. Se activa m\xE1s cuando el brazo est\xE1 por delante del cuerpo.", importance: { movement: "A\xF1ade grosor al b\xEDceps. Se enfatiza en ejercicios como el curl predicador.", health: "Potente flexor del codo." }, volumeRecommendations: { mev: "4", mav: "8-12", mrv: "15" } },
    { id: "braquial", name: "Braquial", description: "M\xFAsculo ubicado debajo del b\xEDceps. Es un flexor puro del codo, independientemente de la posici\xF3n del antebrazo.", importance: { movement: "Contribuye significativamente a la fuerza de flexi\xF3n del codo, especialmente en agarres neutros o pronos.", health: "Un braquial desarrollado a\xF1ade grosor al brazo." }, volumeRecommendations: { mev: "4-6", mav: "8-12", mrv: "15" } },
    { id: "braquiorradial", name: "Braquiorradial", description: "M\xFAsculo prominente en la parte superior y externa del antebrazo. Flexiona el codo, especialmente con un agarre neutro.", importance: { movement: "Potente flexor del codo, clave en ejercicios como el curl martillo.", health: "Contribuye a la fuerza de agarre y la estabilidad de la mu\xF1eca." }, volumeRecommendations: { mev: "2-4", mav: "6-10", mrv: "12" } },
    { id: "tr\xEDceps", name: "Tr\xEDceps", description: "El tr\xEDceps braquial ocupa toda la parte posterior del brazo y tiene tres cabezas (larga, lateral, medial). Es el principal extensor del codo.", importance: { movement: "Es el m\xFAsculo principal en todos los movimientos de empuje (presses). Constituye aproximadamente 2/3 de la masa del brazo.", health: "Fundamental para la estabilidad del codo y la fuerza de bloqueo." }, volumeRecommendations: { mev: "6", mav: "10-14", mrv: "18" }, aestheticImportance: 'El tr\xEDceps representa ~2/3 de la masa del brazo. Da grosor y anchura vista de lado. La forma de "herradura" (cabeza lateral) es muy visible y la cabeza larga aporta masa en la parte posterior. Un tr\xEDceps desarrollado es clave para brazos completos.' },
    { id: "cabeza-larga-tr\xEDceps", name: "Cabeza Larga (Tr\xEDceps)", description: "La \xFAnica cabeza del tr\xEDceps que cruza la articulaci\xF3n del hombro. Se estira y trabaja mejor cuando el brazo est\xE1 por encima de la cabeza.", importance: { movement: "Clave en extensiones sobre la cabeza (press franc\xE9s). A\xF1ade la mayor parte de la masa a la parte posterior del brazo.", health: "Ayuda en la extensi\xF3n y aducci\xF3n del hombro." }, volumeRecommendations: { mev: "3-4", mav: "6-10", mrv: "12" } },
    { id: "cabeza-lateral-tr\xEDceps", name: "Cabeza Lateral (Tr\xEDceps)", description: 'Ubicada en la parte exterior del brazo, da la forma de "herradura" al tr\xEDceps.', importance: { movement: "Muy activa en todos los movimientos de press y extensiones con agarre prono.", health: "Potente extensor del codo." }, volumeRecommendations: { mev: "2-3", mav: "4-6", mrv: "9" } },
    { id: "cabeza-medial-tr\xEDceps", name: "Cabeza Medial (Tr\xEDceps)", description: "Ubicada debajo de las otras dos cabezas, es activa en casi todos los movimientos de extensi\xF3n del codo.", importance: { movement: "Trabaja en todos los rangos de movimiento, pero se enfatiza con agarres supinos (invertidos).", health: "Proporciona estabilidad al codo." }, volumeRecommendations: { mev: "1-2", mav: "3-5", mrv: "7" } },
    { id: "antebrazo", name: "Antebrazo", description: "Conjunto de m\xFAsculos responsables de los movimientos de la mu\xF1eca y los dedos, cruciales para la fuerza de agarre.", importance: { movement: "Fundamental para sostener la barra o mancuernas en casi todos los ejercicios.", health: "Un agarre fuerte es un indicador de salud general y previene lesiones." }, volumeRecommendations: { mev: "2", mav: "6-12", mrv: "16" } },
    { id: "flexores-de-antebrazo", name: "Flexores de Antebrazo", description: "Grupo de m\xFAsculos en la cara interna del antebrazo, responsables de la flexi\xF3n de la mu\xF1eca y los dedos.", importance: { movement: "Fundamentales para la fuerza de agarre (grip strength).", health: "Un agarre fuerte es crucial para progresar en casi todos los ejercicios de espalda y peso muerto." }, volumeRecommendations: { mev: "2-4", mav: "8-12", mrv: "16" } },
    { id: "extensores-de-antebrazo", name: "Extensores de Antebrazo", description: "Grupo de m\xFAsculos en la cara externa del antebrazo, responsables de la extensi\xF3n de la mu\xF1eca y los dedos.", importance: { movement: "Estabilizan la mu\xF1eca en los movimientos de empuje.", health: "Equilibrar la fuerza con los flexores es clave para prevenir lesiones como el codo de tenista." }, volumeRecommendations: { mev: "2-4", mav: "8-12", mrv: "16" } },
    // Piernas
    { id: "cu\xE1driceps", name: "Cu\xE1driceps", description: "Grupo de cuatro m\xFAsculos en la parte frontal del muslo. Su funci\xF3n principal es la extensi\xF3n de la rodilla.", importance: { movement: "Esencial para levantarse de una silla, subir escaleras, correr y saltar.", health: "Unos cu\xE1driceps fuertes son vitales para la estabilidad y salud de la articulaci\xF3n de la rodilla." }, volumeRecommendations: { mev: "6", mav: "10-15", mrv: "18" }, origin: "Ilion (recto femoral), f\xE9mur (vastos)", insertion: "Tuberosidad tibial v\xEDa tend\xF3n rotuliano", mechanicalFunctions: ["Extensi\xF3n de rodilla", "Flexi\xF3n de cadera (recto femoral)"], relatedJoints: ["rodilla", "cadera"], relatedTendons: ["tendon-rotuliano", "tendon-cu\xE1driceps"], commonInjuries: [{ name: "Tendinopat\xEDa rotuliana", description: "Dolor en el tend\xF3n por debajo de la r\xF3tula.", riskExercises: ["db_squat_high_bar"], returnProgressions: ["Sentadilla isom\xE9trica", "Exc\xE9ntricos", "Progresi\xF3n"] }], movementPatterns: ["squat", "extension", "lunge"] },
    { id: "vasto-lateral", name: "Vasto Lateral", description: 'La porci\xF3n externa del cu\xE1driceps. Contribuye significativamente al "barrido" o anchura de la pierna.', importance: { movement: "Potente extensor de rodilla.", health: "Ayuda a estabilizar la r\xF3tula." }, volumeRecommendations: { mev: "4", mav: "8-12", mrv: "15" } },
    { id: "vasto-medial", name: "Vasto Medial", description: 'La porci\xF3n interna del cu\xE1driceps, con forma de "l\xE1grima".', importance: { movement: "Extensor de rodilla, especialmente crucial en los \xFAltimos grados de extensi\xF3n.", health: "Fundamental para la estabilidad y el seguimiento correcto de la r\xF3tula." }, volumeRecommendations: { mev: "4", mav: "8-12", mrv: "15" } },
    { id: "recto-femoral", name: "Recto Femoral", description: "M\xFAsculo \xFAnico del cu\xE1driceps que cruza dos articulaciones: la rodilla y la cadera.", importance: { movement: "Extiende la rodilla y tambi\xE9n flexiona la cadera.", health: "Su flexibilidad es importante para una correcta mec\xE1nica de la pelvis." }, volumeRecommendations: { mev: "3", mav: "6-10", mrv: "12" } },
    { id: "isquiosurales", name: "Isquiosurales", description: "Grupo de m\xFAsculos en la parte posterior del muslo. Son responsables de la flexi\xF3n de la rodilla y la extensi\xF3n de la cadera.", importance: { movement: "Cruciales para correr, esprintar y en la fase de ascenso de las sentadillas y pesos muertos.", health: "La flexibilidad y fuerza de los isquiotibiales son vitales para la salud de la rodilla y la espalda baja." }, volumeRecommendations: { mev: "4-6", mav: "8-12", mrv: "16" } },
    { id: "b\xEDceps-femoral", name: "B\xEDceps Femoral", description: "Parte externa de los isquiosurales, con una cabeza larga y una corta. Extiende la cadera y flexiona la rodilla.", importance: { movement: "Potente motor en la extensi\xF3n de cadera y crucial para la desaceleraci\xF3n al correr.", health: "Suele ser propenso a lesiones por desgarro en deportes explosivos." }, volumeRecommendations: { mev: "2-3", mav: "4-6", mrv: "8" } },
    { id: "semitendinoso", name: "Semitendinoso", description: "Parte interna de los isquiosurales. Extiende la cadera, flexiona la rodilla y ayuda en la rotaci\xF3n interna de la tibia.", importance: { movement: "Trabaja en conjunto con los otros isquiosurales en todos sus movimientos principales.", health: "Contribuye a la estabilidad medial de la rodilla." }, volumeRecommendations: { mev: "1-2", mav: "3-5", mrv: "7" } },
    { id: "semimembranoso", name: "Semimembranoso", description: "El m\xE1s profundo de los isquiosurales internos. Extiende la cadera, flexiona la rodilla y rota internamente la tibia.", importance: { movement: "Potente extensor de cadera y flexor de rodilla.", health: "Proporciona estabilidad a la parte posterior e interna de la rodilla." }, volumeRecommendations: { mev: "1-2", mav: "3-5", mrv: "7" } },
    { id: "aductores", name: "Aductores", description: "Grupo de m\xFAsculos en la parte interna del muslo. Su funci\xF3n principal es la aducci\xF3n (acercar la pierna al centro del cuerpo).", importance: { movement: "Clave en la sentadilla profunda, cambios de direcci\xF3n y patadas.", health: "Importantes estabilizadores de la pelvis y la rodilla." }, volumeRecommendations: { mev: "2-4", mav: "6-10", mrv: "14" } },
    { id: "gl\xFAteos", name: "Gl\xFAteos", description: "Compuesto por el gl\xFAteo mayor, medio y menor. Es uno de los m\xFAsculos m\xE1s fuertes del cuerpo, responsable de la extensi\xF3n y rotaci\xF3n externa de la cadera.", importance: { movement: "Motor principal para la extensi\xF3n de cadera en movimientos como correr, saltar y levantar pesos muertos.", health: "Unos gl\xFAteos fuertes y activos son cruciales para prevenir el dolor de espalda baja y mejorar la estabilidad p\xE9lvica." }, volumeRecommendations: { mev: "4-6", mav: "10-16", mrv: "20" } },
    { id: "gl\xFAteo-mayor", name: "Gl\xFAteo Mayor", description: "El m\xFAsculo m\xE1s grande y fuerte del cuerpo. Su funci\xF3n principal es la extensi\xF3n de la cadera.", importance: { movement: "Potencia movimientos como sentadillas, peso muerto, hip thrusts y sprints.", health: "Esencial para la estabilidad p\xE9lvica y la fuerza general." }, volumeRecommendations: { mev: "4", mav: "8-12", mrv: "16" }, aestheticImportance: "Los gl\xFAteos son uno de los grupos m\xE1s visibles y deseados est\xE9ticamente. Un gl\xFAteo mayor desarrollado aporta forma, redondez y proyecci\xF3n trasera. Define la silueta inferior y la proporci\xF3n cadera-cintura.", origin: "Ilion, sacro, c\xF3ccix", insertion: "Tuberosidad gl\xFAtea del f\xE9mur y tracto iliotibial", mechanicalFunctions: ["Extensi\xF3n de cadera", "Rotaci\xF3n externa de cadera", "Abducci\xF3n (porci\xF3n superior)"], relatedJoints: ["cadera", "sacroiliaca"], relatedTendons: [], commonInjuries: [{ name: "S\xEDndrome del piramidal", description: "Puede confundirse con dolor gl\xFAteo.", returnProgressions: ["Hip thrust", "Puente de gl\xFAteo"] }], movementPatterns: ["hinge", "squat", "extension"] },
    { id: "gl\xFAteo-medio", name: "Gl\xFAteo Medio", description: "Ubicado en la parte lateral de la cadera, es el principal abductor de la misma.", importance: { movement: "Estabiliza la pelvis al caminar, correr o estar sobre una pierna.", health: 'Un gl\xFAteo medio d\xE9bil est\xE1 asociado con dolor de rodilla y espalda baja ("valgo de rodilla").' }, volumeRecommendations: { mev: "2", mav: "6-10", mrv: "12" } },
    { id: "gl\xFAteo-menor", name: "Gl\xFAteo Menor", description: "Ubicado debajo del gl\xFAteo medio, asiste en la abducci\xF3n y estabilizaci\xF3n de la cadera.", importance: { movement: "Trabaja en conjunto con el gl\xFAteo medio para la estabilidad p\xE9lvica.", health: "Contribuye a la salud de la articulaci\xF3n de la cadera." }, volumeRecommendations: { mev: "0", mav: "4-8", mrv: "10" } },
    { id: "pantorrillas", name: "Pantorrillas", description: "Grupo muscular en la parte posterior de la pierna inferior, compuesto por el gastrocnemio y el s\xF3leo.", importance: { movement: "Responsables de la flexi\xF3n plantar (ponerse de puntillas), esencial para caminar, correr y saltar.", health: "La flexibilidad del s\xF3leo es clave para una buena dorsiflexi\xF3n de tobillo, necesaria para una sentadilla profunda." }, volumeRecommendations: { mev: "6-8", mav: "12-16", mrv: "20" } },
    { id: "gastrocnemio", name: "Gastrocnemio", description: "La parte m\xE1s visible y superficial de la pantorrilla. Es un m\xFAsculo biarticular que cruza la rodilla y el tobillo.", importance: { movement: "Potente flexor plantar, especialmente con la pierna extendida. Contribuye a la potencia en saltos.", health: "Suele acortarse, lo que puede afectar la movilidad del tobillo." }, volumeRecommendations: { mev: "4-6", mav: "8-12", mrv: "16" }, origin: "C\xF3ndilos femorales (cabeza medial y lateral)", insertion: "Calc\xE1neo v\xEDa tend\xF3n de Aquiles", mechanicalFunctions: ["Flexi\xF3n plantar", "Flexi\xF3n de rodilla"], relatedJoints: ["tobillo", "rodilla"], relatedTendons: ["tendon-aquiles"], commonInjuries: [{ name: "Tendinopat\xEDa aqu\xEDlea", description: "Degeneraci\xF3n del tend\xF3n de Aquiles.", riskExercises: ["Elevaci\xF3n de talones"], returnProgressions: ["Exc\xE9ntricos de s\xF3leo", "Progresi\xF3n"] }], movementPatterns: ["extension", "jump"] },
    { id: "s\xF3leo", name: "S\xF3leo", description: "M\xFAsculo ubicado debajo del gastrocnemio. Es un potente flexor plantar.", importance: { movement: "Trabaja principalmente cuando la rodilla est\xE1 flexionada (ej. elevaci\xF3n de talones sentado). Es clave para la resistencia al correr.", health: "Su flexibilidad es fundamental para una dorsiflexi\xF3n de tobillo adecuada." }, volumeRecommendations: { mev: "4-6", mav: "8-12", mrv: "16" } },
    // Abdomen
    { id: "recto-abdominal", name: "Recto Abdominal", description: 'El "six-pack". Su funci\xF3n principal es la flexi\xF3n de la columna vertebral (hacer un crunch).', importance: { movement: "Permite flexionar el tronco.", health: "Estabiliza la pelvis y protege los \xF3rganos internos." }, volumeRecommendations: { mev: "0", mav: "10-16", mrv: "20" } },
    { id: "oblicuos", name: "Oblicuos", description: "M\xFAsculos a los lados del abdomen (interno y externo). Responsables de la inclinaci\xF3n lateral y rotaci\xF3n del tronco.", importance: { movement: "Clave en deportes que implican lanzamientos, giros o golpes.", health: "Act\xFAan como un cors\xE9 natural, protegiendo la columna de fuerzas de rotaci\xF3n." }, volumeRecommendations: { mev: "0", mav: "8-12", mrv: "16" } },
    { id: "transverso-abdominal", name: "Transverso Abdominal", description: "El m\xFAsculo m\xE1s profundo del abdomen, que act\xFAa como una faja natural para el tronco.", importance: { movement: 'No produce movimiento, pero es el estabilizador m\xE1s importante del core. Se activa al "meter la guata" o prepararse para un golpe.', health: "Fundamental para la estabilidad de la columna lumbar y la transferencia de fuerza entre las extremidades." }, volumeRecommendations: { mev: "0", mav: "6-10", mrv: "12" } },
    // Otros
    { id: "serrato-anterior", name: "Serrato Anterior", description: "M\xFAsculo ubicado en la parte lateral del t\xF3rax, sobre las costillas. Es responsable de la protracci\xF3n de la esc\xE1pula (alejarla de la columna).", importance: { movement: "Esencial para los movimientos de empuje como los presses y para la mec\xE1nica correcta del hombro al levantar el brazo.", health: 'Un serrato d\xE9bil es una causa com\xFAn de "esc\xE1pula alada" y puede contribuir al dolor de hombro.' }, volumeRecommendations: { mev: "0", mav: "6-10", mrv: "14" } },
    { id: "tibial-anterior", name: "Tibial Anterior", description: "M\xFAsculo en la parte frontal de la espinilla. Es el principal responsable de la dorsiflexi\xF3n (levantar la punta del pie).", importance: { movement: "Crucial para caminar y correr sin tropezar. Act\xFAa como desacelerador al aterrizar de un salto.", health: "Un tibial anterior fuerte ayuda a equilibrar las fuerzas en la parte inferior de la pierna, previniendo la periostitis tibial (shin splints)." }, volumeRecommendations: { mev: "0", mav: "6-10", mrv: "12" } },
    // Nuevos Músculos del Core Profundo
    {
      id: "mult\xEDfidos",
      name: "Mult\xEDfidos",
      description: "Son una serie de m\xFAsculos peque\xF1os y profundos que se extienden a lo largo de la columna vertebral. Act\xFAan como estabilizadores segmentarios, proporcionando rigidez y control a cada v\xE9rtebra individualmente.",
      importance: {
        movement: "No son motores primarios del movimiento, sino estabilizadores finos que se activan antes de cualquier movimiento del tronco o las extremidades para proteger la columna.",
        health: "Una atrofia o disfunci\xF3n de los mult\xEDfidos est\xE1 fuertemente asociada con el dolor de espalda baja cr\xF3nico. Su entrenamiento es clave para la rehabilitaci\xF3n y prevenci\xF3n de lesiones espinales."
      },
      volumeRecommendations: { mev: "N/A", mav: "N/A", mrv: "N/A" }
    },
    {
      id: "suelo-p\xE9lvico",
      name: "Suelo P\xE9lvico",
      description: "Es un conjunto de m\xFAsculos y ligamentos en forma de hamaca que cierran la base de la pelvis. Sostiene los \xF3rganos p\xE9lvicos y gestiona las presiones intraabdominales.",
      importance: {
        movement: 'Trabaja en sinergia con el diafragma y el transverso abdominal para crear una "caja" estable que transfiere fuerza eficientemente (ej. en una sentadilla pesada).',
        health: "Fundamental para la continencia urinaria y fecal, la funci\xF3n sexual y el soporte de los \xF3rganos. Su debilidad o hipertonicidad puede causar una variedad de problemas."
      },
      volumeRecommendations: { mev: "N/A", mav: "N/A", mrv: "N/A" }
    },
    {
      id: "diafragma",
      name: "Diafragma",
      description: "Es el principal m\xFAsculo de la respiraci\xF3n, un gran domo muscular situado debajo de los pulmones. Al contraerse, desciende y permite la inhalaci\xF3n.",
      importance: {
        movement: "Juega un rol crucial en la creaci\xF3n de presi\xF3n intraabdominal (Maniobra de Valsalva), que estabiliza la columna vertebral durante levantamientos pesados.",
        health: 'Una respiraci\xF3n diafragm\xE1tica correcta mejora la oxigenaci\xF3n, reduce el estr\xE9s y promueve una funci\xF3n \xF3ptima del core. Es el "techo" del cilindro del core.'
      },
      volumeRecommendations: { mev: "N/A", mav: "N/A", mrv: "N/A" }
    }
  ];

  // data/articularBatteryConfig.ts
  var ARTICULAR_BATTERIES = [
    {
      id: "shoulder",
      label: "Hombro",
      shortLabel: "Hombro",
      jointIds: ["glenohumeral"],
      tendonIds: ["tendon-supraespinoso", "tendon-infraespinoso", "tendon-b\xEDceps-largo"]
    },
    {
      id: "elbow",
      label: "Codo y Antebrazo",
      shortLabel: "Codo",
      jointIds: ["codo", "mu\xF1eca", "radiocubital-proximal"],
      tendonIds: ["tendon-b\xEDceps", "tendon-tr\xEDceps", "tendon-flexores-mu\xF1eca", "tendon-extensores-mu\xF1eca"]
    },
    {
      id: "knee",
      label: "Rodilla",
      shortLabel: "Rodilla",
      jointIds: ["rodilla"],
      tendonIds: ["tendon-rotuliano", "tendon-cu\xE1driceps", "tendon-isquiotibiales"]
    },
    {
      id: "hip",
      label: "Cadera",
      shortLabel: "Cadera",
      jointIds: ["cadera", "sacroiliaca"],
      tendonIds: ["tendon-iliopsoas"]
    },
    {
      id: "ankle",
      label: "Tobillo",
      shortLabel: "Tobillo",
      jointIds: ["tobillo"],
      tendonIds: ["tendon-aquiles"]
    }
  ];
  var MUSCLE_NAME_TO_ID = {};
  for (const mg of INITIAL_MUSCLE_GROUP_DATA) {
    MUSCLE_NAME_TO_ID[mg.name.toLowerCase()] = mg.id;
    if (mg.name.includes(" ")) {
      const parts = mg.name.split(" ");
      MUSCLE_NAME_TO_ID[parts[0].toLowerCase()] = mg.id;
    }
  }
  var ALIASES = {
    "pectoral": "pectoral-medio",
    "pecho": "pectoral-medio",
    "pectorales": "pectoral-medio",
    "dorsal": "dorsal-ancho",
    "dorsales": "dorsal-ancho",
    "lats": "dorsal-ancho",
    "deltoides": "deltoides-lateral",
    "hombro": "deltoides-lateral",
    "deltoides anterior": "deltoides-anterior",
    "deltoides lateral": "deltoides-lateral",
    "deltoides posterior": "deltoides-posterior",
    "b\xEDceps": "b\xEDceps",
    "biceps": "b\xEDceps",
    "braquial": "braquial",
    "braquiorradial": "braquiorradial",
    "tr\xEDceps": "tr\xEDceps",
    "triceps": "tr\xEDceps",
    "cu\xE1driceps": "cu\xE1driceps",
    "cuadriceps": "cu\xE1driceps",
    "quads": "cu\xE1driceps",
    "recto femoral": "recto-femoral",
    "vasto": "vasto-lateral",
    "isquiosurales": "isquiosurales",
    "isquiotibiales": "isquiosurales",
    "hamstrings": "isquiosurales",
    "b\xEDceps femoral": "b\xEDceps-femoral",
    "gl\xFAteos": "gl\xFAteo-mayor",
    "gluteos": "gl\xFAteo-mayor",
    "gl\xFAteo": "gl\xFAteo-mayor",
    "gluteo mayor": "gl\xFAteo-mayor",
    "gl\xFAteo medio": "gl\xFAteo-medio",
    "pantorrillas": "gastrocnemio",
    "gemelos": "gastrocnemio",
    "gastrocnemio": "gastrocnemio",
    "s\xF3leo": "s\xF3leo",
    "soleo": "s\xF3leo",
    "abdomen": "recto-abdominal",
    "abdominal": "recto-abdominal",
    "oblicuos": "oblicuos",
    "core": "recto-abdominal",
    "trapecio": "trapecio",
    "trapecios": "trapecio",
    "erectores": "erectores-espinales",
    "espalda baja": "erectores-espinales",
    "lumbar": "erectores-espinales",
    "antebrazo": "antebrazo",
    "flexores": "flexores-de-antebrazo",
    "extensores": "extensores-de-antebrazo",
    "aductores": "aductores",
    "cuerpo completo": "cu\xE1driceps"
    // fallback para full-body
  };
  function resolveMuscleId(muscleName) {
    const lower = muscleName.toLowerCase().trim();
    if (ALIASES[lower]) return ALIASES[lower];
    if (MUSCLE_NAME_TO_ID[lower]) return MUSCLE_NAME_TO_ID[lower];
    for (const [alias, id] of Object.entries(ALIASES)) {
      if (lower.includes(alias) || alias.includes(lower)) return id;
    }
    for (const mg of INITIAL_MUSCLE_GROUP_DATA) {
      if (lower.includes(mg.name.toLowerCase()) || mg.name.toLowerCase().includes(lower)) return mg.id;
    }
    return null;
  }
  function getArticularBatteriesForExercise(info, muscleGroupData = INITIAL_MUSCLE_GROUP_DATA) {
    const result = {
      shoulder: 0,
      elbow: 0,
      knee: 0,
      hip: 0,
      ankle: 0
    };
    if (!info) return result;
    const muscleMap = /* @__PURE__ */ new Map();
    for (const mg of muscleGroupData) {
      muscleMap.set(mg.id, {
        id: mg.id,
        relatedJoints: mg.relatedJoints ?? [],
        relatedTendons: mg.relatedTendons ?? []
      });
    }
    const seenJoints = /* @__PURE__ */ new Set();
    const seenTendons = /* @__PURE__ */ new Set();
    for (const { muscle, role, activation = 1 } of info.involvedMuscles ?? []) {
      const weight = role === "primary" ? 1 : role === "secondary" ? 0.6 : 0.3;
      const muscleId = resolveMuscleId(muscle);
      if (!muscleId) continue;
      const data = muscleMap.get(muscleId);
      if (!data) continue;
      for (const j of data.relatedJoints) {
        if (seenJoints.has(j)) continue;
        seenJoints.add(j);
        for (const ab of ARTICULAR_BATTERIES) {
          if (ab.jointIds.includes(j)) {
            result[ab.id] = Math.max(result[ab.id], weight * (activation ?? 1));
            break;
          }
        }
      }
      for (const t of data.relatedTendons) {
        if (seenTendons.has(t)) continue;
        seenTendons.add(t);
        for (const ab of ARTICULAR_BATTERIES) {
          if (ab.tendonIds.includes(t)) {
            result[ab.id] = Math.max(result[ab.id], weight * (activation ?? 1) * 1.2);
            break;
          }
        }
      }
    }
    if (Object.values(result).every((v) => v === 0)) {
      const bodyPart = info.bodyPart ?? "full";
      const force = info.force ?? "Otro";
      if (bodyPart === "upper" || bodyPart === "full") {
        if (force === "Empuje" || force === "Tir\xF3n") {
          result.shoulder = 0.8;
          result.elbow = 0.6;
        }
        if (force === "Flexi\xF3n" || force === "Extensi\xF3n") result.elbow = 0.9;
      }
      if (bodyPart === "lower" || bodyPart === "full") {
        if (force === "Sentadilla") {
          result.knee = 0.8;
          result.hip = 0.6;
        }
        if (force === "Bisagra") {
          result.hip = 0.8;
          result.knee = 0.4;
        }
        if (force === "Salto") {
          result.knee = 0.9;
          result.ankle = 0.8;
        }
      }
    }
    for (const k of Object.keys(result)) {
      result[k] = Math.min(1, result[k]);
    }
    return result;
  }

  // services/ttcService.ts
  var TTC_MAX = 5;
  function getBaseTTC(info) {
    if (!info) return 1;
    const name = (info.name || "").toLowerCase();
    const cat = info.category;
    const force = info.force;
    const type = info.type;
    if (cat === "Potencia" && force === "Salto") return 3;
    if (name.includes("snatch") || name.includes("arrancada") || name.includes("arranque")) return 3;
    if (name.includes("clean") && (name.includes("power") || name.includes("cargada"))) return 3;
    if (name.includes("jerk") || name.includes("envi\xF3n")) return 3;
    if (name.includes("salto") || name.includes("jump") || name.includes("box")) return 3;
    if (cat === "Pliometr\xEDa") return 3;
    if (type === "B\xE1sico" || info.tier === "T1") return 2;
    if (type === "Accesorio" && (info.bodyPart === "full" || info.bodyPart === "lower")) return 2;
    return 1;
  }
  function getEquipmentMod(info) {
    if (!info) return 1;
    const eq = info.equipment;
    if (eq === "M\xE1quina" || eq === "Polea") return 0.8;
    if (eq === "Peso Corporal" || eq === "Banda" || eq === "TRX" || eq === "Slider") return 1;
    if (eq === "Barra" || eq === "Mancuerna" || eq === "Kettlebell" || eq === "Disco" || eq === "Saco de arena" || eq === "Trineo" || eq === "Arn\xE9s" || eq === "Piedra" || eq === "Neum\xE1tico" || eq === "Tronco" || eq === "Escudo" || eq === "Eje")
      return 1.2;
    return 1;
  }
  function getContractionMod(info, setName) {
    if (!info) return 1;
    const name = ((info.name || "") + " " + (setName || "")).toLowerCase();
    const cat = info.category;
    const force = info.force;
    if (cat === "Pliometr\xEDa" || force === "Salto") return 2;
    if (name.includes("salto") || name.includes("jump") || name.includes("bound") || name.includes("hop"))
      return 2;
    if (name.includes("n\xF3rdico") || name.includes("nordic") || name.includes("curl n\xF3rdico")) return 1.8;
    if (name.includes("exc\xE9ntrico") || name.includes("eccentric") || name.includes("exc\xE9ntrica"))
      return 1.8;
    if (cat === "Estabilidad" || cat === "Movilidad") {
      if (name.includes("plancha") || name.includes("plank")) return 0.5;
      if (name.includes("hold") || name.includes("isom\xE9tric") || name.includes("pared") || name.includes("wall sit"))
        return 0.5;
    }
    if (name.includes("plancha") || name.includes("plank")) return 0.5;
    if (name.includes("wall sit") || name.includes("sentadilla isom\xE9trica")) return 0.5;
    if (name.includes("isom\xE9tric")) return 0.5;
    return 1;
  }
  function calculateTTC(info, setName) {
    if (!info) return 0;
    if (info.ttc != null && info.ttc > 0) {
      return Math.min(TTC_MAX, info.ttc);
    }
    const base = getBaseTTC(info);
    const equipmentMod = getEquipmentMod(info);
    const contractionMod = getContractionMod(info, setName);
    return Math.min(TTC_MAX, base * equipmentMod * contractionMod);
  }
  function calculateSetTendonDrain(set, info, articularWeights) {
    const ttc = calculateTTC(info, set.exerciseName);
    if (ttc <= 0) return { shoulder: 0, elbow: 0, knee: 0, hip: 0, ankle: 0 };
    const reps = set.completedReps ?? set.targetReps ?? 10;
    const rpe = getEffectiveRPE(set);
    const intensityMult = rpe >= 10 ? 1.4 : rpe >= 8 ? 1 : rpe >= 6 ? 0.7 : 0.4;
    const repsFactor = Math.min(1.5, 0.1 + reps / 15);
    const baseDrain = ttc * repsFactor * intensityMult * 2.5;
    const result = {
      shoulder: 0,
      elbow: 0,
      knee: 0,
      hip: 0,
      ankle: 0
    };
    const totalWeight = Object.values(articularWeights).reduce((a, b) => a + b, 0) || 1;
    for (const id of Object.keys(result)) {
      const w = articularWeights[id] || 0;
      result[id] = baseDrain * (w / totalWeight);
    }
    return result;
  }

  // services/tendonRecoveryService.ts
  var clamp = (v, min, max) => Math.min(max, Math.max(min, v));
  var safeExp = (v) => {
    const r = Math.exp(v);
    return isNaN(r) || !isFinite(r) ? 0 : r;
  };
  var TENDON_CAPACITY_BASE = 80;
  var TENDON_RECOVERY_HIGH_TTC = 60;
  var TENDON_RECOVERY_STD = 48;
  var CUMULATIVE_PENALTY = 0.1;
  function calculateArticularBatteries(history, exerciseList, muscleGroupData = INITIAL_MUSCLE_GROUP_DATA, _settings) {
    const now = Date.now();
    const exIndex = buildExerciseIndex(exerciseList);
    const tenDaysMs = 10 * 24 * 3600 * 1e3;
    const relevantLogs = history.filter((l) => now - new Date(l.date).getTime() < tenDaysMs).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
    const capacity = TENDON_CAPACITY_BASE;
    const result = {
      shoulder: makeInitialState(),
      elbow: makeInitialState(),
      knee: makeInitialState(),
      hip: makeInitialState(),
      ankle: makeInitialState()
    };
    for (const id of Object.keys(result)) {
      result[id] = { ...result[id], accumulatedStress: 0 };
    }
    const lastDrainTime = {
      shoulder: 0,
      elbow: 0,
      knee: 0,
      hip: 0,
      ankle: 0
    };
    for (const log of relevantLogs) {
      const logTime = new Date(log.date).getTime();
      const hoursSince = Math.max(0, (now - logTime) / 36e5);
      const sessionDrain = {
        shoulder: 0,
        elbow: 0,
        knee: 0,
        hip: 0,
        ankle: 0
      };
      for (const ex of log.completedExercises) {
        const info = findExerciseWithFallback(
          exIndex,
          ex.exerciseDbId,
          ex.exerciseName
        );
        const articularWeights = getArticularBatteriesForExercise(
          info,
          muscleGroupData
        );
        if (Object.values(articularWeights).every((w) => w === 0)) continue;
        for (const s of ex.sets) {
          if (s.type === "warmup" || s.isIneffective) continue;
          const drain = calculateSetTendonDrain(s, info, articularWeights);
          for (const id of Object.keys(drain)) {
            sessionDrain[id] += drain[id];
          }
        }
      }
      const recoveryWindowMs = 72 * 3600 * 1e3;
      for (const id of Object.keys(sessionDrain)) {
        if (sessionDrain[id] > 0 && lastDrainTime[id] > 0 && logTime - lastDrainTime[id] < recoveryWindowMs) {
          sessionDrain[id] *= 1 + CUMULATIVE_PENALTY;
        }
        if (sessionDrain[id] > 0) lastDrainTime[id] = logTime;
      }
      const totalTTC = Object.values(sessionDrain).reduce((a, b) => a + b, 0);
      const drainedCount = Object.values(sessionDrain).filter((v) => v > 0).length;
      const avgTTC = drainedCount > 0 ? totalTTC / drainedCount : 0;
      const recoveryHours = avgTTC > 3 ? TENDON_RECOVERY_HIGH_TTC : TENDON_RECOVERY_STD;
      const k = 2 / recoveryHours;
      for (const id of Object.keys(sessionDrain)) {
        const stress = sessionDrain[id];
        if (stress <= 0) continue;
        const remaining = stress * safeExp(-k * hoursSince);
        result[id].accumulatedStress += remaining;
      }
    }
    for (const id of Object.keys(result)) {
      const acc = result[id].accumulatedStress;
      const battery = clamp(100 - acc / capacity * 100, 0, 100);
      result[id].recoveryScore = Math.round(battery);
      result[id].status = battery < 40 ? "exhausted" : battery < 85 ? "recovering" : "optimal";
      const target = 90;
      if (battery < target && acc > 0) {
        const targetStress = (100 - target) / 100 * capacity;
        const k = 2 / TENDON_RECOVERY_STD;
        result[id].estimatedHoursToRecovery = Math.round(
          Math.max(0, -Math.log(targetStress / acc) / k)
        );
      } else {
        result[id].estimatedHoursToRecovery = 0;
      }
    }
    return result;
  }
  function makeInitialState() {
    return {
      recoveryScore: 100,
      estimatedHoursToRecovery: 0,
      status: "optimal",
      accumulatedStress: 0
    };
  }

  // services/recoveryService.ts
  var RECOVERY_PROFILES = {
    "fast": 24,
    // Recuperación rápida (Bíceps, Hombro Lateral, Gemelo)
    "medium": 48,
    // Estándar (Pecho, Espalda Alta)
    "slow": 72,
    // Grandes grupos/daño alto (Cuádriceps, Glúteos)
    "heavy": 96
    // Sistémico/Axial (Erectores, Isquiosurales)
  };
  var MUSCLE_PROFILE_MAP = {
    "B\xEDceps": "fast",
    "Tr\xEDceps": "fast",
    "Deltoides": "fast",
    "Deltoides Anterior": "fast",
    "Deltoides Lateral": "fast",
    "Deltoides Posterior": "fast",
    "Pantorrillas": "fast",
    "Abdomen": "fast",
    "Antebrazo": "fast",
    "Pectorales": "medium",
    "Dorsales": "medium",
    "Hombros": "medium",
    "Trapecio": "medium",
    "Cu\xE1driceps": "slow",
    "Gl\xFAteos": "slow",
    "Aductores": "medium",
    "Isquiosurales": "heavy",
    "Erectores Espinales": "heavy",
    "Core": "medium"
  };
  var ATHLETE_CAPACITY_FLOORS = {
    "enthusiast": 500,
    "hybrid": 650,
    "calisthenics": 600,
    "bodybuilder": 1e3,
    "powerbuilder": 1100,
    "powerlifter": 1200,
    "weightlifter": 1e3,
    "parapowerlifter": 1100
  };
  var clamp2 = (val, min, max) => Math.min(max, Math.max(min, val));
  var safeExp2 = (val) => {
    const res = Math.exp(val);
    return isNaN(res) || !isFinite(res) ? 0 : res;
  };
  var MUSCLE_CATEGORY_MAP = {
    "Pectorales": ["pectoral", "pecho"],
    "Dorsales": ["dorsal", "dorsales", "redondo mayor", "espalda alta", "lats", "romboides"],
    "Deltoides": ["deltoides", "hombro", "delts"],
    "Deltoides Anterior": ["deltoides anterior", "deltoide anterior", "anterior"],
    "Deltoides Lateral": ["deltoides lateral", "deltoide lateral", "lateral", "medio"],
    "Deltoides Posterior": ["deltoides posterior", "deltoide posterior", "posterior"],
    "B\xEDceps": ["b\xEDceps", "biceps", "braquial", "braquiorradial", "antebrazo"],
    "Tr\xEDceps": ["tr\xEDceps", "triceps"],
    "Cu\xE1driceps": ["cu\xE1driceps", "cuadriceps", "recto femoral", "vasto", "quads"],
    "Isquiosurales": ["isquiosurales", "isquiotibiales", "b\xEDceps femoral", "semitendinoso", "semimembranoso", "femoral", "hamstrings"],
    "Gl\xFAteos": ["gl\xFAteo", "gluteo", "glutes"],
    "Pantorrillas": ["pantorrilla", "gemelo", "gastrocnemio", "s\xF3leo", "soleo", "calves"],
    "Abdomen": ["abdomen", "abdominal", "oblicuo", "recto abdominal", "core", "transverso", "abs", "flexores de cadera", "iliopsoas"],
    "Trapecio": ["trapecio"],
    "Erectores Espinales": ["erector", "espinal", "lumbar", "espalda baja", "cuadrado lumbar", "lower back"]
  };
  var isMuscleInGroup = (specificMuscle, targetCategory) => {
    const specific = specificMuscle.toLowerCase();
    const target = targetCategory.toLowerCase();
    if (specific === target) return true;
    const keywords = MUSCLE_CATEGORY_MAP[target];
    if (keywords) {
      return keywords.some((k) => specific.includes(k));
    }
    return specific.includes(target) || target.includes(specific);
  };
  var calculateUserWorkCapacity = (history, muscleName, exerciseList, settings, idx) => {
    const now = Date.now();
    const fourWeeksAgo = now - 28 * 24 * 60 * 60 * 1e3;
    const recentLogs = history.filter((log) => new Date(log.date).getTime() > fourWeeksAgo);
    const baseFloor = ATHLETE_CAPACITY_FLOORS[settings.athleteType || "enthusiast"] || 500;
    if (recentLogs.length === 0) return baseFloor;
    const index = idx || buildExerciseIndex(exerciseList);
    let totalStress = 0;
    recentLogs.forEach((log) => {
      log.completedExercises.forEach((ex) => {
        const info = findExerciseWithFallback(index, ex.exerciseDbId, ex.exerciseName);
        if (!info) return;
        const involvement = info.involvedMuscles.find((m) => isMuscleInGroup(m.muscle, muscleName));
        if (involvement) {
          const stress = ex.sets.reduce((acc, s) => acc + calculateSetStress(s, info, 90), 0);
          totalStress += stress * (involvement.activation ?? 1);
        }
      });
    });
    const weeklyAvg = totalStress / 4;
    const calculatedCapacity = weeklyAvg * 1.8;
    return clamp2(Math.max(calculatedCapacity, baseFloor), 500, 3500);
  };
  var calculateMuscleBattery = (muscleName, history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback = [], waterLogs = [], dailyWellbeingLogs = [], nutritionLogs = []) => {
    const now = Date.now();
    const exIndex = buildExerciseIndex(exerciseList);
    const muscleCapacity = calculateUserWorkCapacity(history, muscleName, exerciseList, settings, exIndex);
    let profileKey = "medium";
    for (const [key, val] of Object.entries(MUSCLE_PROFILE_MAP)) {
      if (isMuscleInGroup(key, muscleName)) {
        profileKey = val;
        break;
      }
    }
    const baseRecoveryTime = RECOVERY_PROFILES[profileKey];
    let recoveryTimeMultiplier = 1;
    const todayStr = getLocalDateString();
    const recentWellbeing = dailyWellbeingLogs.find((l) => l.date === todayStr) || dailyWellbeingLogs[dailyWellbeingLogs.length - 1];
    if (settings.algorithmSettings?.augeEnableNutritionTracking !== false) {
      const nutritionResult = computeNutritionRecoveryMultiplier({
        nutritionLogs,
        settings,
        stressLevel: recentWellbeing?.stressLevel ?? 3,
        hoursWindow: 48
      });
      recoveryTimeMultiplier *= nutritionResult.recoveryTimeMultiplier;
    } else if (settings.calorieGoalObjective === "deficit") {
      recoveryTimeMultiplier *= 1.25;
    }
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) {
      recoveryTimeMultiplier *= 1.4;
    }
    let wSleep = 7.5;
    if (settings.algorithmSettings?.augeEnableSleepTracking !== false) {
      const safeSleepLogs = sleepLogs || [];
      const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
      const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);
      if (sortedSleep.length > 0) {
        wSleep = (sortedSleep[0]?.duration || 7) * 0.5 + (sortedSleep[1]?.duration || 7) * 0.3 + (sortedSleep[2]?.duration || 7) * 0.2;
      }
      if (wSleep < 6) recoveryTimeMultiplier *= 1.5;
      else if (wSleep < 7) recoveryTimeMultiplier *= 1.2;
      else if (wSleep >= 8.5) recoveryTimeMultiplier *= 0.8;
      else if (wSleep >= 7.5) recoveryTimeMultiplier *= 0.9;
    }
    const age = settings.userVitals?.age || 25;
    if (age > 35) {
      const agePenalty = (age - 35) * 0.01;
      recoveryTimeMultiplier *= 1 + agePenalty;
    }
    const gender = settings.userVitals?.gender || "male";
    if (gender === "female" || gender === "transfemale") {
      recoveryTimeMultiplier *= 0.85;
    }
    const realRecoveryTime = baseRecoveryTime * Math.max(0.5, recoveryTimeMultiplier);
    let accumulatedFatigue = 0;
    let lastSessionDate = 0;
    let effectiveSetsCount = 0;
    const relevantHistory = history.filter((log) => now - new Date(log.date).getTime() < 10 * 24 * 3600 * 1e3);
    relevantHistory.forEach((log) => {
      const logTime = new Date(log.date).getTime();
      const hoursSince = Math.max(0, (now - logTime) / 36e5);
      let sessionMuscleStress = 0;
      log.completedExercises.forEach((ex) => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const involvedMuscles = info?.involvedMuscles ?? inferInvolvedMuscles(ex.exerciseName ?? "", ex.equipment ?? "", "Otro", "upper");
        for (const muscleInvolvement of involvedMuscles) {
          if (!isMuscleInGroup(muscleInvolvement.muscle, muscleName)) continue;
          const rawStress = ex.sets.reduce((acc, s) => {
            if (s.type === "warmup" || s.isIneffective) return acc;
            if (!isSetEffective(s)) return acc;
            return acc + calculateSetStress(s, info ?? void 0, 90);
          }, 0);
          let roleMultiplier = 0;
          switch (muscleInvolvement.role) {
            case "primary":
              roleMultiplier = 1;
              break;
            case "secondary":
              roleMultiplier = 0.5;
              break;
            case "stabilizer":
              roleMultiplier = 0.15;
              break;
            default:
              roleMultiplier = 0.1;
          }
          const activationFactor = muscleInvolvement.activation || 1;
          sessionMuscleStress += rawStress * roleMultiplier * activationFactor;
          if (hoursSince <= 168 && (muscleInvolvement.role === "primary" || muscleInvolvement.role === "secondary" && activationFactor > 0.6)) {
            effectiveSetsCount += ex.sets.filter((s) => !(s.type === "warmup" || s.isIneffective) && isSetEffective(s)).length;
          }
        }
      });
      if (sessionMuscleStress > 0) {
        const k = 2.9957 / Math.max(1, realRecoveryTime);
        const remainingStress = sessionMuscleStress * safeExp2(-k * hoursSince);
        accumulatedFatigue += remainingStress;
        if (logTime > lastSessionDate) lastSessionDate = logTime;
      }
    });
    let batteryPercentage = 100 - accumulatedFatigue / muscleCapacity * 100;
    batteryPercentage = clamp2(batteryPercentage, 0, 100);
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const contextLog = wellbeingArray.find((l) => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];
    let backgroundCap = 100;
    const workIntensity = contextLog?.workIntensity || settings.userVitals?.workIntensity || "light";
    const stressLevel = contextLog?.stressLevel || 3;
    if (workIntensity === "high") backgroundCap -= 10;
    if (stressLevel === 5) backgroundCap -= 10;
    else if (stressLevel === 4) backgroundCap -= 2;
    batteryPercentage = Math.min(batteryPercentage, backgroundCap);
    if (accumulatedFatigue <= 0.1 && (recentWellbeing?.doms || 0) <= 2) {
      batteryPercentage = 100;
    }
    const muscleDeltas = settings.batteryCalibration?.muscleDeltas || {};
    const myDelta = muscleDeltas[muscleName] ?? 0;
    if (myDelta !== 0) {
      batteryPercentage = clamp2(batteryPercentage + myDelta, 0, 100);
    }
    let domsCap = 100;
    if (recentWellbeing && recentWellbeing.doms > 2) {
      const wellbeingDoms = recentWellbeing.doms;
      if (wellbeingDoms === 5) domsCap = Math.min(domsCap, 20);
      else if (wellbeingDoms === 4) domsCap = Math.min(domsCap, 50);
      else if (wellbeingDoms === 3) domsCap = Math.min(domsCap, 85);
    }
    const recentLogsWithDiscomfort = history.filter(
      (l) => now - new Date(l.date).getTime() < 48 * 36e5 && l.discomforts && l.discomforts.length > 0
    );
    recentLogsWithDiscomfort.forEach((log) => {
      const isRelated = log.discomforts?.some((d) => isMuscleInGroup(d, muscleName));
      if (isRelated) domsCap = Math.min(domsCap, 50);
    });
    const recentFeedback = postSessionFeedback.filter((f) => now - new Date(f.date).getTime() < 72 * 3600 * 1e3).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())[0];
    if (recentFeedback?.feedback) {
      const feedbackEntry = Object.entries(recentFeedback.feedback).find(([k]) => isMuscleInGroup(k, muscleName));
      if (feedbackEntry) {
        const [_, data] = feedbackEntry;
        const hoursSinceFeedback = (now - new Date(recentFeedback.date).getTime()) / 36e5;
        let localDomsCap = 100;
        if (data.doms === 5) {
          localDomsCap = 10 + hoursSinceFeedback * 1.5;
        } else if (data.doms === 4) {
          localDomsCap = 40 + hoursSinceFeedback * 1;
        } else if (data.doms === 3) {
          localDomsCap = 70 + hoursSinceFeedback * 0.5;
        }
        domsCap = Math.min(domsCap, localDomsCap);
      }
    }
    batteryPercentage = Math.min(batteryPercentage, domsCap);
    const logsWithMuscleBattery = history.filter((l) => l.muscleBatteries && Object.keys(l.muscleBatteries).length > 0).filter((l) => now - new Date(l.date).getTime() < 10 * 24 * 3600 * 1e3).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    for (const log of logsWithMuscleBattery) {
      const reported = Object.entries(log.muscleBatteries).find(([key]) => isMuscleInGroup(key, muscleName));
      if (!reported) continue;
      const [_, observedBattery] = reported;
      const hoursSince = (now - new Date(log.date).getTime()) / 36e5;
      const tau = realRecoveryTime;
      const recoveryFactor = 1 - safeExp2(-hoursSince / Math.max(12, tau * 1.5));
      const recoveredBattery = Math.min(100, observedBattery + (100 - observedBattery) * recoveryFactor);
      const blendWeight = hoursSince < 48 ? 0.8 : hoursSince < 96 ? 0.5 : 0.25;
      batteryPercentage = clamp2(recoveredBattery * blendWeight + batteryPercentage * (1 - blendWeight), 0, 100);
      break;
    }
    batteryPercentage = clamp2(batteryPercentage, 0, 100);
    let status = "optimal";
    if (batteryPercentage < 40) status = "exhausted";
    else if (batteryPercentage < 85) status = "recovering";
    let hoursToRecovery = 0;
    const targetPercentage = Math.min(90, backgroundCap);
    if (batteryPercentage < targetPercentage && accumulatedFatigue > 0) {
      const k = 2.9957 / realRecoveryTime;
      const targetFatigue = (100 - targetPercentage) * muscleCapacity / 100;
      if (accumulatedFatigue > targetFatigue) {
        hoursToRecovery = -Math.log(targetFatigue / accumulatedFatigue) / k;
      }
    }
    return {
      recoveryScore: Math.round(batteryPercentage),
      effectiveSets: effectiveSetsCount,
      hoursSinceLastSession: lastSessionDate > 0 ? Math.round((now - lastSessionDate) / 36e5) : -1,
      estimatedHoursToRecovery: Math.round(Math.max(0, hoursToRecovery)),
      status
    };
  };
  var calculateSystemicFatigue = (history, sleepLogs, dailyWellbeingLogs, exerciseList, settings) => {
    const now = Date.now();
    const exIndex = buildExerciseIndex(exerciseList);
    const last7DaysLogs = history.filter((l) => now - new Date(l.date).getTime() < 7 * 24 * 3600 * 1e3);
    let cnsLoad = 0;
    last7DaysLogs.forEach((log) => {
      const daysAgo = (now - new Date(log.date).getTime()) / (24 * 3600 * 1e3);
      const recencyMultiplier = Math.max(0.1, Math.exp(-0.4 * daysAgo));
      let sessionCNS = 0;
      log.completedExercises.forEach((ex) => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const { cnc } = getDynamicAugeMetrics(info, ex.exerciseName);
        ex.sets.forEach((s) => {
          const stress = calculateSetStress(s, info, 90);
          const sncRatio = cnc / 5;
          let loadMultiplier = 1;
          if (info?.calculated1RM && s.weight) {
            if (s.weight / info.calculated1RM >= 0.9) loadMultiplier = 1.3;
          }
          sessionCNS += stress * sncRatio * loadMultiplier;
        });
      });
      if ((log.duration || 0) > 75 * 60) sessionCNS *= 1.15;
      if ((log.duration || 0) > 90 * 60) sessionCNS *= 1.25;
      cnsLoad += sessionCNS * recencyMultiplier;
    });
    const normalizedGymFatigue = clamp2(cnsLoad / WEEKLY_CNS_FATIGUE_REFERENCE * 100, 0, 100);
    let sleepPenalty = 0;
    if (settings?.algorithmSettings?.augeEnableSleepTracking !== false) {
      const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
      const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);
      const wSleep = sortedSleep.length > 0 ? (sortedSleep[0]?.duration || 7.5) * 0.5 + (sortedSleep[1]?.duration || 7.5) * 0.3 + (sortedSleep[2]?.duration || 7.5) * 0.2 : 7.5;
      if (wSleep < 4.5) sleepPenalty = 40;
      else if (wSleep < 5.5) sleepPenalty = 25;
      else if (wSleep < 6.5) sleepPenalty = 15;
      else if (wSleep >= 8.5) sleepPenalty = -15;
      else if (wSleep > 7.5) sleepPenalty = -5;
    }
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const wellbeing = wellbeingArray.find((l) => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];
    let lifeStressPenalty = 0;
    if (wellbeing) {
      if (wellbeing.stressLevel >= 4) lifeStressPenalty += 15;
      else if (wellbeing.stressLevel === 3) lifeStressPenalty += 5;
      if (wellbeing.workIntensity === "high" || wellbeing.studyIntensity === "high") {
        lifeStressPenalty += 10;
      }
    }
    const totalFatigue = normalizedGymFatigue + sleepPenalty + lifeStressPenalty;
    const cnsBattery = clamp2(100 - totalFatigue, 0, 100);
    const calcLifeScore = (sPen, lPen) => Math.max(0, sPen + lPen);
    return {
      total: Math.round(cnsBattery),
      gym: Math.round(normalizedGymFatigue),
      life: Math.round(calcLifeScore(sleepPenalty, lifeStressPenalty))
    };
  };
  var calculateDailyReadiness = (sleepLogs, dailyWellbeingLogs, settings, cnsBattery) => {
    let recoveryTimeMultiplier = 1;
    const diagnostics = [];
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find((l) => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];
    const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
    const lastSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime())[0];
    const sleepHours = lastSleep?.duration || 7.5;
    if (sleepHours < 6) {
      recoveryTimeMultiplier *= 1.5;
      diagnostics.push("Falta de sue\xF1o detectada (<6h). Tu recarga est\xE1 severamente frenada hoy.");
    }
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) {
      recoveryTimeMultiplier *= 1.4;
      diagnostics.push("Tus niveles altos de estr\xE9s est\xE1n liberando cortisol, bloqueando la recuperaci\xF3n del sistema nervioso.");
    }
    if (settings.calorieGoalObjective === "deficit") {
      recoveryTimeMultiplier *= 1.3;
      diagnostics.push("Al estar en d\xE9ficit cal\xF3rico, tienes recursos limitados para reparar tejido da\xF1ado.");
    }
    let status = "green";
    let recommendation = "Est\xE1s en condiciones \xF3ptimas. Tienes luz verde para buscar r\xE9cords personales o tirar pesado.";
    const isDeficit = settings.calorieGoalObjective === "deficit";
    if (isDeficit) {
      recommendation = "En r\xE9gimen de d\xE9ficit: prioriza mantener masa muscular. Evita volumen excesivo o ir al fallo en cada serie.";
    }
    if (cnsBattery < 40 || recoveryTimeMultiplier >= 1.8) {
      status = "red";
      recommendation = isDeficit ? "D\xE9ficit + fatiga alta. Riesgo de p\xE9rdida muscular. Descanso o sesi\xF3n muy ligera. Prioriza prote\xEDna y sue\xF1o." : "Tu sistema nervioso no est\xE1 listo. Tu falta de sue\xF1o/estr\xE9s est\xE1 frenando tu recarga. Considera descanso total o una sesi\xF3n muy ligera de movilidad.";
    } else if (cnsBattery < 70 || recoveryTimeMultiplier >= 1.3) {
      status = "yellow";
      recommendation = isDeficit ? "D\xE9ficit activo. Reduce volumen o RPE hoy para poder recuperarte y proteger tu masa muscular." : "Tienes fatiga residual o factores externos en contra. Cambia el trabajo pesado por t\xE9cnica, o reduce tu volumen planificado al 50%.";
    }
    if (diagnostics.length === 0) {
      diagnostics.push("Tus h\xE1bitos de las \xFAltimas 24 hrs fueron excelentes. La s\xEDntesis de recuperaci\xF3n est\xE1 a tope.");
    }
    return {
      status,
      // 'green', 'yellow', 'red'
      stressMultiplier: parseFloat(recoveryTimeMultiplier.toFixed(2)),
      cnsBattery,
      diagnostics,
      recommendation
    };
  };
  var calculateGlobalBatteries = (history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList) => {
    const now = Date.now();
    const tanks = calculatePersonalizedBatteryTanks(settings);
    let cnsHalfLife = 28;
    let muscHalfLife = 40;
    let spinalHalfLife = 72;
    const auditLogs = { cns: [], muscular: [], spinal: [] };
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find((l) => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];
    if (settings?.algorithmSettings?.augeEnableNutritionTracking !== false) {
      const nutritionResult = computeNutritionRecoveryMultiplier({
        nutritionLogs,
        settings,
        stressLevel: recentWellbeing?.stressLevel ?? 3,
        hoursWindow: 48
      });
      const nutMult = nutritionResult.recoveryTimeMultiplier;
      muscHalfLife *= nutMult;
      if (nutritionResult.status === "deficit") {
        auditLogs.muscular.push({ icon: "\u{1F4C9}", label: "D\xE9ficit Cal\xF3rico (Recarga Lenta)", val: "", type: "info" });
      } else if (nutritionResult.status === "surplus") {
        auditLogs.muscular.push({ icon: "\u{1F680}", label: "Super\xE1vit Cal\xF3rico (Recarga Acelerada)", val: "", type: "info" });
      } else if (nutritionResult.factors.some((f) => f.includes("Prote\xEDna"))) {
        auditLogs.muscular.push({ icon: "\u{1F969}", label: "Prote\xEDna sub\xF3ptima", val: "", type: "info" });
      }
    } else if (settings?.calorieGoalObjective === "deficit") {
      muscHalfLife *= 1.25;
      auditLogs.muscular.push({ icon: "\u{1F4C9}", label: "R\xE9gimen D\xE9ficit (Recarga m\xE1s lenta)", val: "", type: "info" });
    }
    let cnsPenalty = 0;
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) {
      cnsPenalty += 12;
      auditLogs.cns.push({ icon: "\u{1F92F}", label: "Alto Estr\xE9s Reportado", val: -12, type: "penalty" });
    }
    if (settings?.algorithmSettings?.augeEnableSleepTracking !== false) {
      const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
      const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);
      let wSleep = 7.5;
      if (sortedSleep.length > 0) wSleep = (sortedSleep[0]?.duration || 7.5) * 0.5 + (sortedSleep[1]?.duration || 7.5) * 0.3 + (sortedSleep[2]?.duration || 7.5) * 0.2;
      if (wSleep < 6) {
        cnsPenalty += 18;
        auditLogs.cns.push({ icon: "\u{1F971}", label: "Deuda de Sue\xF1o Cr\xEDtica", val: -18, type: "penalty" });
      } else if (wSleep >= 8.5) {
        cnsPenalty -= 10;
        auditLogs.cns.push({ icon: "\u{1F6CC}", label: "Sue\xF1o Profundo (Bonus)", val: "+10", type: "bonus" });
      }
    }
    const exIndex = buildExerciseIndex(exerciseList);
    let cnsFatigue = 0, muscFatigue = 0, spinalFatigue = 0;
    const sevenDaysAgo = now - 7 * 24 * 3600 * 1e3;
    const recentLogs = history.filter((l) => new Date(l.date).getTime() > sevenDaysAgo);
    recentLogs.forEach((log) => {
      let logCns = 0, logMusc = 0, logSpinal = 0;
      const hoursAgo = (now - new Date(log.date).getTime()) / 36e5;
      log.completedExercises.forEach((ex) => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        ex.sets.forEach((s, idx) => {
          const drain = calculateSetBatteryDrain(s, info, tanks, idx, 90);
          logCns += drain.cnsDrainPct;
          logMusc += drain.muscularDrainPct;
          logSpinal += drain.spinalDrainPct;
        });
      });
      const cnsDecay = logCns * Math.exp(-(Math.LN2 / cnsHalfLife) * hoursAgo);
      const muscDecay = logMusc * Math.exp(-(Math.LN2 / muscHalfLife) * hoursAgo);
      const spinalDecay = logSpinal * Math.exp(-(Math.LN2 / spinalHalfLife) * hoursAgo);
      cnsFatigue += cnsDecay;
      muscFatigue += muscDecay;
      spinalFatigue += spinalDecay;
      if (hoursAgo < 72) {
        if (cnsDecay > 3) auditLogs.cns.push({ icon: "\u{1F3CB}\uFE0F", label: `Sesi\xF3n: ${log.sessionName}`, val: -Math.round(cnsDecay), type: "workout" });
        if (muscDecay > 3) auditLogs.muscular.push({ icon: "\u{1F3CB}\uFE0F", label: `Sesi\xF3n: ${log.sessionName}`, val: -Math.round(muscDecay), type: "workout" });
        if (spinalDecay > 3) auditLogs.spinal.push({ icon: "\u{1F3CB}\uFE0F", label: `Sesi\xF3n: ${log.sessionName}`, val: -Math.round(spinalDecay), type: "workout" });
      }
    });
    const calib = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: "" };
    let cnsDelta = calib.cnsDelta || 0;
    let muscDelta = calib.muscularDelta || 0;
    let spinalDelta = calib.spinalDelta || 0;
    if (calib.lastCalibrated) {
      const calibHours = (now - new Date(calib.lastCalibrated).getTime()) / 36e5;
      const calibDecay = Math.max(0, 1 - calibHours / 72);
      cnsDelta *= calibDecay;
      muscDelta *= calibDecay;
      spinalDelta *= calibDecay;
    }
    if (Math.abs(cnsDelta) > 1) auditLogs.cns.push({ icon: "\u{1F9E0}", label: "Auto-Calibraci\xF3n (T\xFA)", val: Math.round(cnsDelta) > 0 ? `+${Math.round(cnsDelta)}` : Math.round(cnsDelta), type: cnsDelta > 0 ? "bonus" : "penalty" });
    if (Math.abs(muscDelta) > 1) auditLogs.muscular.push({ icon: "\u{1F9E0}", label: "Auto-Calibraci\xF3n (T\xFA)", val: Math.round(muscDelta) > 0 ? `+${Math.round(muscDelta)}` : Math.round(muscDelta), type: muscDelta > 0 ? "bonus" : "penalty" });
    if (Math.abs(spinalDelta) > 1) auditLogs.spinal.push({ icon: "\u{1F9E0}", label: "Auto-Calibraci\xF3n (T\xFA)", val: Math.round(spinalDelta) > 0 ? `+${Math.round(spinalDelta)}` : Math.round(spinalDelta), type: spinalDelta > 0 ? "bonus" : "penalty" });
    const finalCns = Math.min(100, Math.max(0, 100 - cnsFatigue - cnsPenalty + cnsDelta));
    const finalMusc = Math.min(100, Math.max(0, 100 - muscFatigue + muscDelta));
    const finalSpinal = Math.min(100, Math.max(0, 100 - spinalFatigue + spinalDelta));
    let verdict = "Todos tus sistemas est\xE1n \xF3ptimos. Es un buen d\xEDa para buscar r\xE9cords personales (PRs).";
    if (finalCns < 30) verdict = "Tu sistema nervioso est\xE1 frito. NO intentes 1RMs hoy. Prioriza m\xE1quinas y reduce el RPE.";
    else if (finalSpinal < 35) verdict = "Tu columna y tejido axial est\xE1n sobrecargados. Evita el Peso Muerto o Sentadillas Libres hoy.";
    else if (finalMusc < 30) verdict = "Alta fatiga muscular residual. Aseg\xFArate de comer suficiente prote\xEDna y haz rutinas de bombeo.";
    else if (cnsPenalty > 10) verdict = "Tu falta de sue\xF1o/estr\xE9s est\xE1 limitando tu potencial hoy. Autorregula tu peso y no vayas al fallo.";
    const articularBatteries = calculateArticularBatteries(history, exerciseList, void 0, settings);
    return {
      cns: Math.round(finalCns),
      muscular: Math.round(finalMusc),
      spinal: Math.round(finalSpinal),
      auditLogs,
      verdict,
      articularBatteries
    };
  };

  // utils/calculations.ts
  var getWeekId = (date, startWeekOn) => {
    const d = new Date(date.getTime());
    d.setHours(0, 0, 0, 0);
    const day = d.getDay();
    let diff = day - startWeekOn;
    if (diff < 0) diff += 7;
    d.setDate(d.getDate() - diff);
    return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, "0")}-${d.getDate().toString().padStart(2, "0")}`;
  };

  // services/analysisService.ts
  var MUSCLE_ROLE_MULTIPLIERS = HYPERTROPHY_ROLE_MULTIPLIERS ?? { primary: 1, secondary: 0.5, stabilizer: 0, neutralizer: 0 };
  var createChildToParentMap = (hierarchy) => {
    const map = /* @__PURE__ */ new Map();
    if (!hierarchy || !hierarchy.bodyPartHierarchy) return map;
    for (const bodyPart in hierarchy.bodyPartHierarchy) {
      const subgroups = hierarchy.bodyPartHierarchy[bodyPart];
      for (const item of subgroups) {
        if (typeof item === "object" && item !== null) {
          const subgroupName = Object.keys(item)[0];
          const children = Object.values(item)[0];
          if (Array.isArray(children) && subgroupName) {
            children.forEach((child) => map.set(child, subgroupName));
          }
        }
      }
    }
    return map;
  };
  var calculateAverageVolumeForWeeks = (weeks, exerciseList, muscleHierarchy, mode = "complex") => {
    if (weeks.length === 0) return [];
    const exIndex = buildExerciseIndex(exerciseList);
    const allMuscleTotals = {};
    const childToParentMap = createChildToParentMap(muscleHierarchy);
    const getDisplayGroup = (muscle) => childToParentMap.get(muscle) || muscle;
    weeks.forEach((week) => {
      week.sessions.forEach((session) => {
        const exercises = session.parts && session.parts.length > 0 ? session.parts.flatMap((p) => p.exercises) : session.exercises;
        const sessionFreqImpact = /* @__PURE__ */ new Map();
        exercises.forEach((exercise) => {
          const exerciseData = findExercise(exIndex, exercise.exerciseDbId, exercise.name);
          if (!exerciseData || !exerciseData.involvedMuscles) return;
          const effectiveSets = exercise.sets.filter(isSetEffective).length;
          if (effectiveSets === 0) return;
          const isDirectEffective = (s) => {
            if (!isSetEffective(s)) return false;
            const rpe = s.rpe || s.completedRPE || s.targetRPE;
            const rir = s.rir ?? s.completedRIR ?? s.targetRIR;
            if (s.isFailure || s.intensityMode === "failure" || s.isAmrap || s.performanceMode === "failed") return true;
            if (rpe !== void 0 && rpe >= 6) return true;
            if (rir !== void 0 && rir <= 4) return true;
            if (rpe === void 0 && rir === void 0) return true;
            return false;
          };
          const hasDirectEffectiveSets = exercise.sets.some(isDirectEffective);
          const highestRolePerGroup = /* @__PURE__ */ new Map();
          exerciseData.involvedMuscles.forEach((m) => {
            if (!m || !m.muscle) return;
            const group = getDisplayGroup(m.muscle);
            const role = m.role ?? "primary";
            const currentFreq = sessionFreqImpact.get(group) || { direct: 0, indirect: 0 };
            if ((role === "primary" || role === "secondary") && hasDirectEffectiveSets) {
              const impactVal = MUSCLE_ROLE_MULTIPLIERS[role] ?? 0.5;
              currentFreq.direct = Math.max(currentFreq.direct, impactVal);
            } else if (role === "stabilizer" || role === "neutralizer") {
              currentFreq.indirect = 1;
            }
            sessionFreqImpact.set(group, currentFreq);
            if (mode === "simple" && role !== "primary") return;
            const multiplier = mode === "simple" ? 1 : MUSCLE_ROLE_MULTIPLIERS[role] ?? 0.5;
            const existing = highestRolePerGroup.get(group);
            if (!existing || existing.maxMultiplier < multiplier) {
              highestRolePerGroup.set(group, {
                maxMultiplier: multiplier,
                bestRole: role
              });
            }
          });
          highestRolePerGroup.forEach((data, groupName) => {
            if (!allMuscleTotals[groupName]) {
              allMuscleTotals[groupName] = { totalVol: 0, direct: /* @__PURE__ */ new Map(), indirect: /* @__PURE__ */ new Map(), freqDirect: 0, freqIndirect: 0 };
            }
            allMuscleTotals[groupName].totalVol += effectiveSets * data.maxMultiplier;
            if (data.bestRole === "primary") {
              allMuscleTotals[groupName].direct.set(exercise.name, (allMuscleTotals[groupName].direct.get(exercise.name) || 0) + effectiveSets);
            } else if (mode === "complex") {
              const existing = allMuscleTotals[groupName].indirect.get(exercise.name);
              const percentageEquivalent = data.maxMultiplier * 100;
              if (!existing || existing.act < percentageEquivalent) {
                allMuscleTotals[groupName].indirect.set(exercise.name, { sets: (existing?.sets || 0) + effectiveSets, act: percentageEquivalent });
              }
            }
          });
        });
        sessionFreqImpact.forEach((impact, groupName) => {
          if (!allMuscleTotals[groupName]) {
            allMuscleTotals[groupName] = { totalVol: 0, direct: /* @__PURE__ */ new Map(), indirect: /* @__PURE__ */ new Map(), freqDirect: 0, freqIndirect: 0 };
          }
          allMuscleTotals[groupName].freqDirect += impact.direct;
          if (impact.direct === 0) {
            allMuscleTotals[groupName].freqIndirect += impact.indirect;
          }
        });
      });
    });
    return Object.entries(allMuscleTotals).filter(([muscleGroup]) => muscleGroup !== "General").map(([muscleGroup, data]) => ({
      muscleGroup,
      displayVolume: Math.round(data.totalVol / weeks.length * 10) / 10,
      totalSets: Math.round(data.totalVol / weeks.length),
      frequency: Math.round(data.freqDirect / weeks.length * 10) / 10,
      indirectFrequency: Math.round(data.freqIndirect / weeks.length * 10) / 10,
      avgRestDays: null,
      directExercises: Array.from(data.direct.entries()).map(([name, sets]) => ({ name, sets: Math.round(sets / weeks.length * 10) / 10 })),
      indirectExercises: Array.from(data.indirect.entries()).map(([name, info]) => ({ name, sets: Math.round(info.sets / weeks.length * 10) / 10, activationPercentage: info.act })),
      avgIFI: null,
      recoveryStatus: "N/A"
    })).filter((v) => v.displayVolume > 0 || v.indirectFrequency > 0).sort((a, b) => b.displayVolume - a.displayVolume);
  };
  var calculateACWR = (history, settings, exerciseList) => {
    if (history.length < 7) {
      return { acwr: 0, interpretation: "Datos insuficientes", color: "text-slate-400" };
    }
    const today = /* @__PURE__ */ new Date();
    const stressByDay = {};
    history.forEach((log) => {
      const dateStr = getDatePartFromString(log.date);
      const stress = log.sessionStressScore ?? calculateCompletedSessionStress(log.completedExercises, exerciseList);
      stressByDay[dateStr] = (stressByDay[dateStr] || 0) + stress;
    });
    const getDailyStress = (date) => {
      return stressByDay[getLocalDateString(date)] || 0;
    };
    let acuteLoad = 0;
    for (let i = 0; i < 7; i++) {
      const date = new Date(today);
      date.setDate(today.getDate() - i);
      acuteLoad += getDailyStress(date);
    }
    const weeklyLoads = [];
    for (let week = 0; week < 4; week++) {
      let weeklyLoad = 0;
      for (let day = 0; day < 7; day++) {
        const date = new Date(today);
        date.setDate(today.getDate() - week * 7 - day);
        weeklyLoad += getDailyStress(date);
      }
      weeklyLoads.push(weeklyLoad);
    }
    const chronicLoad = weeklyLoads.reduce((sum, load) => sum + load, 0) / 4;
    if (chronicLoad < 10) return { acwr: 0, interpretation: "Carga baja", color: "text-sky-400" };
    const acwr = acuteLoad / chronicLoad;
    const { interpretation, color } = classifyACWR(acwr);
    return { acwr: parseFloat(acwr.toFixed(2)), interpretation, color };
  };
  var calculateWeeklyTonnageComparison = (history, settings) => {
    const today = /* @__PURE__ */ new Date();
    const currentWeekId = getWeekId(today, settings.startWeekOn);
    const currentWeekStartDate = parseDateStringAsLocal(currentWeekId);
    const prevWeekDate = new Date(currentWeekStartDate);
    prevWeekDate.setDate(prevWeekDate.getDate() - 7);
    const previousWeekId = getWeekId(prevWeekDate, settings.startWeekOn);
    let current = 0;
    let previous = 0;
    history.forEach((log) => {
      const logDate = parseDateStringAsLocal(getDatePartFromString(log.date));
      const logWeekId = getWeekId(logDate, settings.startWeekOn);
      const volume = log.completedExercises.reduce((total, ex) => total + ex.sets.reduce((setTotal, s) => {
        const weight = s.weight || 0;
        const reps = s.completedReps || 0;
        const duration = s.completedDuration || 0;
        if (duration > 0) return setTotal + duration * (weight > 0 ? weight : 1);
        return setTotal + (weight + (ex.useBodyweight ? settings.userVitals.weight || 0 : 0)) * reps;
      }, 0), 0);
      if (logWeekId === currentWeekId) current += volume;
      else if (logWeekId === previousWeekId) previous += volume;
    });
    return { current: Math.round(current), previous: Math.round(previous) };
  };

  // services/computeWorkerService.ts
  var worker = null;
  var requestId = 0;
  var pending = /* @__PURE__ */ new Map();
  function getWorker() {
    if (worker) return worker;
    if (typeof Worker === "undefined") return null;
    try {
      worker = new Worker("./computeWorker.js");
      worker.addEventListener("message", (e) => {
        const { id, result, error } = e.data;
        const req = pending.get(id);
        if (!req) return;
        pending.delete(id);
        if (error) req.reject(new Error(error));
        else req.resolve(result);
      });
      worker.addEventListener("error", () => {
        pending.forEach((req) => req.reject(new Error("Worker error")));
        pending.clear();
        worker = null;
      });
      return worker;
    } catch {
      return null;
    }
  }
  function callWorker(fn, args) {
    const w = getWorker();
    if (!w) return Promise.reject(new Error("Worker unavailable"));
    const id = String(++requestId);
    return new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject });
      w.postMessage({ id, fn, args });
    });
  }
  function withWorkerFallback(fn, fnName) {
    return (...args) => {
      const w = getWorker();
      if (!w) {
        try {
          return Promise.resolve(fn(...args));
        } catch (err) {
          return Promise.reject(err);
        }
      }
      return callWorker(fnName, args);
    };
  }
  var calculateMuscleBatteryAsync = withWorkerFallback(
    calculateMuscleBattery,
    "calculateMuscleBattery"
  );
  var calculateGlobalBatteriesAsync = withWorkerFallback(
    calculateGlobalBatteries,
    "calculateGlobalBatteries"
  );
  var calculateSystemicFatigueAsync = withWorkerFallback(
    calculateSystemicFatigue,
    "calculateSystemicFatigue"
  );
  var calculateDailyReadinessAsync = withWorkerFallback(
    calculateDailyReadiness,
    "calculateDailyReadiness"
  );
  var calculatePredictedSessionDrainAsync = withWorkerFallback(
    calculatePredictedSessionDrain,
    "calculatePredictedSessionDrain"
  );
  var calculateCompletedSessionStressAsync = withWorkerFallback(
    calculateCompletedSessionStress,
    "calculateCompletedSessionStress"
  );
  var calculateACWRAsync = withWorkerFallback(
    calculateACWR,
    "calculateACWR"
  );
  var calculateAverageVolumeForWeeksAsync = withWorkerFallback(
    calculateAverageVolumeForWeeks,
    "calculateAverageVolumeForWeeks"
  );
  var calculateWeeklyTonnageComparisonAsync = withWorkerFallback(
    calculateWeeklyTonnageComparison,
    "calculateWeeklyTonnageComparison"
  );

  // services/auge.ts
  var HYPERTROPHY_ROLE_MULTIPLIERS = {
    primary: 1,
    secondary: 0.5,
    stabilizer: 0,
    neutralizer: 0
  };
  var classifyACWR = (acwr) => {
    if (acwr < 0.8) return { interpretation: "Sub-entrenando", color: "text-sky-400" };
    if (acwr > 1.5) return { interpretation: "Alto Riesgo", color: "text-[#FF2E43]" };
    if (acwr > 1.3) return { interpretation: "Zona de Riesgo", color: "text-[#FFD600]" };
    return { interpretation: "Zona Segura", color: "text-[#00FF9D]" };
  };

  // workers/computeWorker.ts
  var FUNCTIONS = {
    calculateMuscleBattery,
    calculateGlobalBatteries,
    calculateSystemicFatigue,
    calculateDailyReadiness,
    calculatePredictedSessionDrain,
    calculateCompletedSessionStress,
    calculateACWR,
    calculateAverageVolumeForWeeks,
    calculateWeeklyTonnageComparison
  };
  self.addEventListener("message", (e) => {
    const { id, fn, args } = e.data;
    try {
      const handler = FUNCTIONS[fn];
      if (!handler) {
        self.postMessage({ id, error: `Unknown function: ${fn}` });
        return;
      }
      const result = handler(...args);
      self.postMessage({ id, result });
    } catch (err) {
      self.postMessage({ id, error: err.message });
    }
  });
})();
