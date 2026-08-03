#!/usr/bin/env python3
"""Descripciones amigables de curaduría v4 — definiciones y configuraciones.

Texto dirigido al usuario final: corto, con carácter y que invite a probar el
ejercicio. Evita jerga biomecánica (bisagra, patrón, cadena) y verbos
instruccionales bloqueados por el gate/compilador.
"""

DEF_DESC: dict[str, str] = {
    # ---- Remos ----
    "chest_supported_row": ("Remo apoyado en el banco con el pecho acostado: la espalda trabaja a fondo sin que la zona baja te frene. "
                            "Elige el agarre y el peso que quieras, que el banco hace el resto del trabajo de equilibrio."),
    "conventional_row": ("El remo de toda la vida: torso inclinado, pesos hacia la cintura y codos que viajan hacia atrás. "
                         "Construye una espalda ancha y fuerte mientras aprendes a cargar con la cadera."),
    "gironda_row": ("Remo al estilo Gironda: el torso se inclina hacia delante y hacia atrás siguiendo el peso, como un columpio "
                    "de espalda. Un movimiento exigente que trabaja a fondo los dorsales con mucho recorrido."),
    "pendlay_row": ("Remo explosivo desde el suelo: el peso parte de cero en cada repetición y vuelve a tocar el piso. "
                    "Pura fuerza de espalda, con una pizca de potencia que no encuentras en otros remos."),
    "seal_row": ("Remo acostado boca abajo sobre el banco, con los brazos colgando hacia el suelo. "
                 "Aisla la espalda al máximo y es de los pocos remos donde no puedes hacer trampa con el cuerpo."),
    "t_bar_row": ("Remo en barra T: la carga va anclada al suelo y tú haces el trabajo de subirla con la espalda. "
                  "Un clásico de los gimnasios serios para darle grosor a la espalda media."),
    # ---- Pecho ----
    "bench_press": ("El rey del pecho: acostado en el banco, bajas la barra al pecho y la empujas con todo. "
                    "Mide tu fuerza real de empuje, emociona como pocos ejercicios y nunca pasa de moda."),
    "decline_bench_press": ("Press en banco declinado, con la cabeza más baja que las caderas: ataca la parte baja del pecho "
                            "desde un ángulo que se siente distinto. Buen complemento para redondear el trabajo del press plano."),
    "incline_bench_press": ("Press en banco inclinado: el ángulo sube el foco a la parte alta del pecho y al hombro delantero. "
                            "La versión que más estética construye y la favorita para esculpir la parte de arriba."),
    "floor_press": ("Press de pecho acostado en el suelo, con los codos tocando el piso en cada repetición. "
                    "Reduce el recorrido, protege el hombro y te deja mover cargas pesadas con confianza."),
    "flat_chest_fly": ("Aperturas planas: abres y cierras los brazos en arco sobre el pecho, como abrazando un árbol grande. "
                       "Aísla el pectoral como ningún press y la sensación de tensión en el pecho es incomparable."),
    "incline_chest_fly": ("Aperturas en banco inclinado: el arco de los brazos se inclina y el foco sube a la parte alta del pecho. "
                          "Trabajo fino de apertura para definir y dar forma a la zona superior."),
    "decline_chest_fly": ("Aperturas en banco declinado: el arco baja y el pectoral inferior toma el protagonismo. "
                          "Ideal para completar el pecho con un estiramiento profundo que se siente de maravilla."),
    "reverse_pec_fly": ("Aperturas invertidas: llevas los brazos hacia atrás abriendo el pecho, en máquina Pec Deck, polea o "
                        "mancuernas. Es el ejercicio estrella para el deltoides posterior y la espalda alta: corrige postura "
                        "y da forma al hombro."),
    # ---- Core (definiciones) ----
    "core_plancha": ("Plancha abdominal: aguanta el cuerpo recto como una tabla apoyado en los antebrazos y las puntas de los pies. "
                     "El isométrico clásico que enciende el abdomen en serio, sin moverte un centímetro."),
    "core_dragon_flag_banco_plano": ("El ejercicio de las películas de artes marciales: boca arriba en el banco, subes el cuerpo "
                                     "entero hasta casi la vertical y desciendes con todo controlado. Fuerza de core extrema y mucha adrenalina."),
    "core_rueda_abdominal": ("Con la rueda en las manos, ruedas hacia delante estirando el cuerpo entero y vuelves con el abdomen "
                             "en llamas. Poco aparato, mucho respeto: uno de los ejercicios de core más exigentes."),
    "core_press_pallof": ("Press Pallof: de pie frente a la polea, llevas la cuerda de un lado a otro del pecho resistiendo el giro. "
                          "Enseña a tu abdomen a frenar rotaciones, algo que pagas con creces en sentadillas y pesos muertos."),
    "core_elevacion_piernas": ("Elevación de piernas colgado de la barra: subes las piernas mientras el abdomen trabaja para "
                               "mantenerte quieto. La versión más honesta y difícil del trabajo abdominal."),
    "core_inclinacion_lateral": ("Inclinación lateral: te doblas hacia un lado y vuelves, trabajando los oblicuos de forma aislada. "
                                 "El movimiento pequeño que define la cintura cuando se hace con paciencia."),
    "core_crunch_banco_declinado_lastrado_disco": ("Crunch en banco declinado: la cabeza queda más baja que las caderas y el "
                                                   "recorrido del abdomen se alarga. Puedes añadir un disco en el pecho cuando quieras más."),
    "core_crunch_en_polea_alta": ("Crunch de rodillas en polea alta: tiras de la cuerda desde la cabeza y crujes con tensión constante. "
                                  "El abdomen trabaja en todo el recorrido, no solo en la subida."),
    "core_crunch_maquina": ("Crunch en máquina: ajustas el peso, te sientas y crujes con el recorrido guiado. "
                            "La forma más cómoda de meter volumen de abdomen sin pensar en nada."),
    "core_crunch_suelo_peso_corporal": ("Crunch en el suelo: el ejercicio de abdomen más simple que existe y el que más gente "
                                        "domina mal. Rodillas dobladas, hombros hacia el techo y contracción limpia."),
    "core_lenador_polea": ("Leñador en polea: como partir leña, llevas la cuerda en diagonal de arriba abajo cruzando el cuerpo. "
                           "Oblicuos y abdomen completo rotando con potencia."),
    # ---- Bíceps (definiciones) ----
    "biceps_curl_bayesian": ("Curl bayesian: el brazo cuelga por detrás del tronco con el codo atrás y el bíceps bien estirado. "
                             "El curl más elegante del gimnasio: tensión constante y una contracción que se siente única."),
    "biceps_curl_crucifijo": ("Curl crucifijo en polea: el brazo parte cruzado frente al pecho y flexiona hacia el hombro. "
                              "Una variante original que estira el bíceps desde otro ángulo y sorprende a los brazos."),
    "biceps_curl_drag": ("Curl drag: en lugar de subir recto, el peso se arrastra pegado al cuerpo llevando los codos atrás. "
                         "Sensación totalmente distinta que aísla el bíceps como ningún otro curl."),
    "biceps_curl_sentado_banco_plano": ("Curl sentado en banco plano: el tronco apoyado elimina cualquier impulso y el bíceps "
                                        "trabaja solo. La versión limpia del curl clásico, perfecta para sentir la contracción."),
    "biceps_curl_superman": ("Curl superman en polea: los brazos parten desde atrás del cuerpo y flexionan contra la tensión. "
                             "El estiramiento inicial es enorme y el bíceps arranca en su punto más largo."),
    "biceps_curl_trx": ("Curl colgado del TRX: tu propio peso como carga y el cuerpo como contrapeso. "
                        "Equilibrio, core y bíceps en un mismo movimiento que se puede hacer en cualquier lado."),
    "biceps_curl_waiter": ("Curl de camarero: sujetas un disco en copa con las palmas hacia arriba y flexiones. "
                           "El bíceps y el antebrazo trabajan con un agarre distinto que refuerza la muñeca."),
    "biceps_curl_zottman": ("Curl Zottman: subes con la palma hacia arriba y bajas girándola hacia abajo. "
                            "La técnica de los clásicos del fisicoculturismo para trabajar bíceps y antebrazos en una sola serie."),
    "concentration_curl": ("Curl concentrado: el codo apoyado en el muslo y toda la atención en el pico del bíceps. "
                           "Aislamiento puro con la contracción más intensa que existe: se siente cada fibra."),
    "preacher_curl": ("Curl predicador: los brazos apoyados en el banco y el bíceps aislado desde el primer segundo. "
                      "Sin impulso, sin trampas: puro trabajo de contracción con el codo fijo."),
    "spider_curl": ("Curl araña: boca abajo en el banco inclinado con los brazos colgando, el bíceps se estira a fondo en cada "
                    "repetición. El rey del estiramiento para el brazo, favorito de los que buscan detalle."),
    "standing_biceps_curl": ("El curl de pie clásico: el movimiento de brazo más famoso del mundo. "
                             "Carga el bíceps con la postura natural, sin apoyos y con la libertad de siempre."),
    # ---- Peso muerto y cadera (definiciones) ----
    "conventional_deadlift": ("Peso muerto convencional: desde el suelo, con la barra pegada al cuerpo, levantas el peso empujando "
                              "con las piernas y abriendo la cadera. El ejercicio que construye fuerza de verdad en todo el cuerpo."),
    "sumo_deadlift": ("Peso muerto sumo: pies amplios y brazos dentro de las piernas, el recorrido se acorta y los glúteos y "
                      "aductores toman protagonismo. La variante preferida por muchos por su comodidad con cargas altas."),
    "good_morning": ("Buenos días: con la barra sobre los hombros, inclinas el torso hacia delante moviendo solo la cadera y "
                     "vuelves arriba. El ejercicio que fortalece toda la espalda baja y las piernas, exigente y gratificante."),
    "good_morning_seated": ("Buenos días sentado: la cadera queda apoyada en el banco y el trabajo se concentra en la parte "
                            "trasera de las piernas y la espalda baja. Menos peso y mucha más sensación de tensión."),
    "good_morning_zercher": ("Buenos días Zercher: la barra sujetada en los codos frente al pecho. Al quedar la carga más cerca "
                             "del cuerpo, el torso y la cadera trabajan con una exigencia distinta."),
    "romanian_deadlift": ("Peso muerto rumano: con las piernas casi rectas, deslizas la barra por los muslos hasta sentir el "
                          "estiramiento y subes empujando la cadera. El rey de los isquios y los glúteos."),
    "romanian_sumo_deadlift": ("Peso muerto rumano sumo: la postura amplia de pies con piernas casi rectas cambia el enfoque "
                               "hacia los glúteos y el interior de los muslos. Variante fresca del rumano clásico."),
    # ---- Cadera lateral (definiciones) ----
    "hip_abduction": ("Abducciones de pierna: separas la pierna hacia el lado contra resistencia y el glúteo medio hace el "
                      "trabajo. El ejercicio que da forma a la cadera, protege las rodillas y mejora tu estabilidad."),
    "hip_adduction": ("Aducciones de pierna: juntas la pierna hacia el centro contra resistencia y los aductores del muslo "
                      "interno se encienden. Clave para piernas equilibradas y una cadera estable."),
    "copenhagen_plank": ("Plancha Copenhague: lateral apoyado en antebrazo con la pierna de arriba en el banco, aguantando la "
                         "cadera elevada. Los aductores trabajan de una forma que no encontrarás en ningún otro ejercicio."),
    "copenhagen_plank_dynamic": ("Plancha Copenhague dinámica: como la clásica, pero bajando y subiendo la cadera con ritmo. "
                                 "La versión con movimiento que hace arder los aductores aún más rápido."),
    # ---- Glúteos e isquios (definiciones) ----
    "glutes_patada_gluteo": ("Patada de glúteo: empujas el talón hacia atrás y arriba contra resistencia, con el glúteo "
                             "haciendo todo el trabajo. El ejercicio favorito para sentir y dar forma al glúteo."),
    "glutes_patada_gluteo_lateral": ("Patada de glúteo lateral: llevas la pierna hacia el lado contra resistencia, enfocando "
                                     "el glúteo medio y el lateral de la cadera. Ideal para redondear el glúteo completo."),
    "glutes_patada_gluteo_polea_diagonal": ("Patada de glúteo en polea diagonal: el cable cruza el cuerpo y la tensión viaja "
                                            "con la pierna en diagonal. Variante que sorprende al glúteo con un ángulo nuevo."),
    "glutes_puente_gluteos": ("Puente de glúteos: boca arriba con las rodillas dobladas, empujas la cadera hacia el techo. "
                              "El ejercicio básico y brutal para sentir el glúteo trabajando desde el primer día."),
    "hip_thrust": ("Hip thrust: la espalda apoyada en el banco y la barra en la cadera, empujando el peso hacia arriba. "
                   "El rey indiscutible para construir glúteos con carga, el favorito de todos."),
    "glutes_hiperextension_45": ("Hiperextensiones a 45 grados para glúteos: en el banco inclinado, subes el cuerpo con la "
                                 "cadera y el glúteo dirige el movimiento. La variante del banco pensada para glúteo."),
    "glutes_hiperextension_45_zercher": ("Hiperextensión a 45 Zercher para glúteos: con la carga sujetada en los codos frente "
                                         "al pecho, el glúteo trabaja contra un peso que queda muy cómodo y controlado."),
    "reverse_hyper": ("Reverse hyper: acostado boca abajo, subes las piernas hacia atrás balanceando la cadera. "
                      "Descarga la espalda baja mientras trabaja el glúteo: el secreto de muchos atletas."),
    "glutes_frog_pumps": ("Frog pumps: boca arriba con las plantas de los pies juntas y las rodillas abiertas, bombeando la "
                          "cadera hacia arriba. Un ejercicio pequeño que enciende el glúteo de una forma sorprendente."),
    "glutes_clamshells_banda": ("Almejas con banda: acostado de lado, abres la rodilla superior contra la resistencia de la "
                                "banda. Pequeño en tamaño, enorme para activar el glúteo medio antes de entrenar."),
    "glutes_monster_walk_banda": ("Caminata del monstruo con banda: con la banda alrededor de las rodillas o tobillos, caminas "
                                  "de lado sin dejar que la banda te cierre. El calentamiento favorito para despertar el glúteo."),
    "glutes_step_up_gluteo": ("Step-up de glúteo: subes a un cajón empujando con una sola pierna, con el glúteo haciendo el "
                              "trabajo. Un ejercicio unilateral que corrige desequilibrios y enciende el glúteo."),
    "glutes_zancada_cruzada": ("Zancada cruzada: das un paso atrás y cruzado por detrás de la otra pierna, bajando en "
                               "diagonal. Trabaja glúteo y caderas con un ángulo distinto a la zancada normal."),
    "hams_peso_muerto_rumano_zercher": ("Peso muerto rumano Zercher: la barra en los codos frente al pecho mientras bajas "
                                        "con las piernas casi rectas. El isquio se estira igual y el torso trabaja de extra."),
    "hams_pull_through": ("Pull-through: de espaldas a la polea baja, pasas la cuerda entre las piernas y empujas la cadera "
                          "hacia atrás y adelante. El ejercicio que enseña a usar la cadera con los isquios de guía."),
    "hams_peso_muerto_convencional_deficit": ("Peso muerto convencional en déficit: subido a una plataforma, el recorrido es "
                                              "más largo desde el suelo. Isquios y glúteos trabajan con un estiramiento extra."),
    "hams_peso_muerto_piernas_rigidas_deficit": ("Peso muerto piernas rígidas en déficit: desde la plataforma y con las piernas "
                                                 "casi rectas, el estiramiento de los isquios llega al máximo. Muy exigente."),
    "hams_peso_muerto_sumo_deficit": ("Peso muerto sumo en déficit: postura amplia desde la plataforma, con más recorrido que "
                                      "el sumo normal. Glúteos y aductores con trabajo extra."),
    "hams_swing_kettlebell_dos_manos": ("Swing con kettlebell a dos manos: impulsas la campana entre las piernas y la lanzas "
                                        "hasta la altura del pecho con la cadera. Potencia, glúteos y mucha energía."),
    "hams_swing_kettlebell_unilateral": ("Swing con kettlebell a una mano: el impulso de cadera con una sola mano, con el "
                                         "torso aguantando el giro. Variante avanzada que añade estabilidad al swing clásico."),
    "stiff_leg_deadlift": ("Peso muerto piernas rígidas: piernas casi rectas y la barra bajando hasta el estiramiento máximo "
                           "de los isquios. El ejercicio más profundo para la parte trasera de la pierna."),
    "glute_ham_raise": ("Glute ham raise: la espinilla apoyada en la máquina, bajas el cuerpo extendido y subes con isquios y "
                        "glúteos. El ejercicio de los atletas serios, exigente de principio a fin."),
    # ---- Cuádriceps y piernas (definiciones) ----
    "front_squat": ("Sentadilla frontal: la barra descansa sobre los hombros delanteros y el torso queda más erguido. "
                    "Los cuádriceps trabajan a fondo mientras el core aguanta la barra."),
    "high_bar_back_squat": ("Sentadilla trasera con barra alta: la barra sobre los trapecios y el torso erguido, bajando "
                            "profundo. La sentadilla reina para cuádriceps y fuerza total."),
    "low_bar_back_squat": ("Sentadilla trasera con barra baja: la barra descansa más abajo y la cadera empuja hacia atrás. "
                           "Permite cargar más peso y reparte el trabajo entre cuádriceps y glúteos."),
    "quads_prensa_piernas": ("Prensa de piernas: sentado en la máquina, empujas la plataforma con los pies. "
                             "Carga pesada con la espalda protegida: la favorita para meter volumen de pierna."),
    "sumo_squat": ("Sentadilla sumo: pies amplios y puntas hacia fuera, bajando con la cadera. "
                   "Glúteos, aductores y cuádriceps trabajando juntos con un estímulo distinto."),
    "quads_sentadilla_hack": ("Sentadilla hack: la espalda apoyada en la máquina y el recorrido guiado hacia abajo. "
                              "Cuádriceps aislados con la espalda cuidada: la máquina de las piernas serias."),
    "quads_sentadilla_hack_invertida_maquina": ("Sentadilla invertida en máquina hack: de espaldas al aparato, el peso queda "
                                                "al frente y el trabajo se corre a la parte baja del cuádriceps."),
    "quads_sentadilla_v_squat": ("Sentadilla en máquina V-Squat: el cuerpo en ángulo fijo y la carga guiada detrás. "
                                 "Los cuádriceps trabajan con mucha profundidad y la espalda descansa."),
    "quads_sentadilla_v_squat_invertida_maquina": ("Sentadilla invertida en máquina V-Squat: la posición invertida cambia el "
                                                   "ángulo y el estímulo cae en el cuádriceps inferior."),
    "pendulum_squat": ("Sentadilla en máquina pendular: la plataforma se balancea y el cuerpo baja casi en caída libre "
                       "guiada. Una de las máquinas de cuádriceps más exigentes que existen."),
    "belt_squat": ("Sentadilla en máquina Belt Squat: la carga cuelga de la cintura con un cinturón, sin peso en la espalda. "
                   "La espalda se libera por completo y las piernas hacen todo el trabajo."),
    "quads_sentadilla_anderson": ("Sentadilla Anderson: desde la posición sentada en el suelo, sin rebote, te levantas con la "
                                  "barra. La sentadilla que elimina el impulso y construye fuerza pura desde cero."),
    "quads_sentadilla_anderson_frontal_barra_recta": ("Sentadilla Anderson frontal: la barra al frente, sentado en el suelo y "
                                                      "levantando sin impulso. Cuádriceps y core exigidos al máximo."),
    "quads_sentadilla_bazuca": ("Sentadilla bazuca: sentadilla con los pies muy juntos y las puntas hacia delante. "
                                "El foco se va al cuádriceps externo y a darle forma a las piernas."),
    "quads_sentadilla_copa": ("Sentadilla copa: sujetas una mancuerna o kettlebell frente al pecho como una copa. "
                              "La sentadilla perfecta para aprender, con el core trabajando de extra."),
    "quads_sentadilla_somersault": ("Sentadilla Somersault: la barra cruza los brazos por delante del pecho y baja "
                                    "profundo. Una variante rara que carga el torso y exige movilidad."),
    "quads_sentadilla_zercher_barra_recta": ("Sentadilla Zercher: la barra en los codos frente al pecho, bajando profundo. "
                                             "El core y los brazos aguantan la carga mientras los cuádriceps trabajan."),
    "quads_sentadilla_jefferson": ("Sentadilla Jefferson: de pie sobre la barra, agarras un extremo por delante y otro por "
                                   "detrás y bajas en diagonal. Un ejercicio raro, exigente y sorprendentemente bueno."),
    "quads_sentadilla_cosaca": ("Sentadilla cosaca: das un paso muy amplio al lado y bajas hacia un lado con la otra pierna "
                                "extendida. Movilidad, aductores y cuádriceps en un solo movimiento."),
    "sissy_squat": ("Sentadilla Sissy: rodillas al frente y torso hacia atrás, bajando en línea como si te inclinaras. "
                    "El ejercicio que aísla el cuádriceps con tu propio peso: pocas cosas queman tanto."),
    "quads_sentadilla_cajon": ("Sentadilla a cajón: bajas sentándote en un cajón y te levantas sin rebote. "
                               "Perfecta para aprender la profundidad y construir fuerza de cuádriceps con seguridad."),
    "quads_sentadilla_sumo_frontal": ("Sentadilla sumo frontal: la barra al frente con la postura amplia de sumo. "
                                      "Torso erguido, cuádriceps y aductores trabajando juntos."),
    "quads_sentadilla_sumo_zercher": ("Sentadilla sumo Zercher: la barra en los codos con la postura amplia. "
                                      "La combinación de sumo y Zercher: exigente y muy completa."),
    "quads_extension_cuadriceps": ("Extensión de cuádriceps en máquina: sentado, estiras las piernas contra la resistencia. "
                                   "El aislamiento clásico del cuádriceps, ideal para rematar las piernas."),
    "quads_extension_cuadriceps_pie_polea": ("Extensión de cuádriceps de pie en polea: el tobillo enganchado, estiras la "
                                             "rodilla contra la tensión del cable. Variante fina que sorprende al cuádriceps."),
    "forward_lunge": ("Zancada frontal: das un paso al frente y bajas con ambas rodillas. "
                      "Piernas, glúteos y equilibrio trabajando en un movimiento simple y efectivo."),
    "reverse_lunge": ("Zancada inversa: el paso va hacia atrás, más amable para la rodilla y muy estable. "
                      "La variante favorita de muchos para cargar peso con confianza."),
    "walking_lunge": ("Zancada caminando: das pasos al frente sin parar, avanzando por el gimnasio. "
                      "Piernas, glúteos y una resistencia mental que se entrena a la vez."),
    "step_up": ("Step-up a cajón: subes a un cajón empujando con una pierna y bajas con control. "
                "El ejercicio de pierna que corrige desequilibrios y enciende los glúteos."),
    "bulgarian_split_squat": ("Sentadilla búlgara: el pie trasero apoyado en un banco y bajando con la pierna delantera. "
                              "El ejercicio unilateral más temido y más efectivo para las piernas."),
    "bulgarian_zercher": ("Sentadilla búlgara Zercher: la búlgara con la carga en los codos frente al pecho. "
                          "El torso trabaja de extra mientras la pierna delantera hace el trabajo pesado."),
    "quads_sentadilla_bulgara_somersault": ("Sentadilla búlgara Somersault: el pie trasero en el banco y la barra cruzando "
                                            "los brazos. Variante rara de la búlgara que exige mucho equilibrio."),
    "quads_sentadilla_bulgara_jefferson": ("Sentadilla búlgara Jefferson: pie trasero en el banco y la barra en posición "
                                           "Jefferson. La combinación de dos ejercicios duros en uno."),
    "quads_sentadilla_pistola": ("Sentadilla pistola: en una sola pierna, bajas hasta el suelo con la otra extendida. "
                                 "Equilibrio, movilidad y fuerza: el ejercicio que lo demuestra todo."),
    "quads_sentadilla_pistola_asistida_trx": ("Sentadilla pistola asistida en TRX: sujetándote de las cuerdas, bajas a una "
                                              "pierna con apoyo. La vía para conquistar la pistola sin miedo."),
    "quads_step_up_cajon_frontal": ("Step-up frontal a cajón: subes con una pierna llevando el peso al frente. "
                                    "Cuádriceps a fondo y glúteo trabajando en la subida."),
    "quads_step_up_cajon_zercher": ("Step-up Zercher a cajón: la carga en los codos y el paso arriba. "
                                    "El core aguanta mientras la pierna sube con todo."),
    "quads_zancada_caminando_frontal_barra_recta": ("Zancada caminando con barra recta al frente: pasos adelante con la "
                                                    "barra en el pecho. Piernas y torso trabajando en movimiento."),
    "quads_zancada_caminando_zercher_barra_recta": ("Zancada caminando Zercher: pasos al frente con la barra en los codos. "
                                                    "La versión más exigente de la zancada caminando."),
    "quads_zancada_frontal_zercher": ("Zancada frontal Zercher: el paso al frente con la carga en los codos. "
                                      "Equilibrio, piernas y core en un mismo movimiento."),
    "quads_zancada_inversa_frontal": ("Zancada inversa frontal: el paso atrás con la barra al frente del pecho. "
                                      "Estable, amable con la rodilla y muy completa para las piernas."),
    "quads_zancada_inversa_maquina_hack": ("Zancada inversa en máquina hack: el paso atrás con el guiado de la hack. "
                                           "La estabilidad de la máquina con el trabajo de la zancada."),
    "quads_zancada_inversa_maquina_v_squat": ("Zancada inversa en máquina V-Squat: paso atrás con la plataforma guiada. "
                                              "Unilateral, profundo y con la espalda protegida."),
    "quads_zancada_inversa_zercher": ("Zancada inversa Zercher: el paso atrás con la barra en los codos. "
                                      "La combinación exigente que trabaja piernas y torso."),
    # ---- Isquios, pantorrillas y resto de pierna (definiciones) ----
    "lying_leg_curl": ("Curl de isquiosurales tumbado: boca abajo en la máquina, flexionas las piernas llevando los talones "
                       "al glúteo. El aislamiento clásico de la parte trasera de la pierna."),
    "seated_leg_curl": ("Curl de isquiosurales sentado: en la máquina sentada, flexionas las piernas contra la resistencia. "
                        "Trabaja los isquios en una postura cómoda y segura para la espalda."),
    "standing_leg_curl": ("Curl de isquiosurales de pie: de pie en la máquina, flexionas una pierna contra la resistencia. "
                          "El aislamiento unilateral perfecto para igualar ambos isquios."),
    "curl_isquios_con_balon": ("Curl de isquiosurales con balón: acostado, llevas el balón hacia el glúteo con las piernas. "
                               "Isquios y glúteos trabajando en casa sin ninguna máquina."),
    "curl_isquios_con_sliders": ("Curl de isquiosurales con sliders: los pies en los discos deslizantes y llevas el cuerpo "
                                 "hacia atrás flexionando las rodillas. La versión exigente para isquios en casa."),
    "calf_raise": ("Elevación de talones: subes a la punta de los pies y bajas con control, con la pantorrilla haciendo "
                   "todo el trabajo. El ejercicio pequeño que construye pantorrillas grandes."),
    "calves_tibial_anterior": ("Tibial anterior: llevas la punta del pie hacia arriba contra resistencia. "
                               "El músculo olvidado de la espinilla, clave para tobillos sanos y pantorrillas completas."),
    "quads_reverse_nordic_peso_corporal": ("Reverse Nordic curl: de rodillas, te inclinas hacia atrás sin doblar el cuerpo. "
                                           "El cuádriceps frena la caída: intenso, raro y sorprendentemente bueno."),
    "hams_curl_nordic_peso_corporal": ("Curl nórdico: de rodillas con los tobillos sujetos, bajas el cuerpo hacia delante "
                                       "frenando con los isquios. El ejercicio más duro para la parte trasera de la pierna."),
    "forearms_suspension_isometrica_barra_fija": ("Suspensión isométrica en barra fija: te cuelgas de la barra el mayor "
                                                  "tiempo posible. El ejercicio más simple y más honesto para el agarre."),
    "forearms_paseo_del_granjero": ("Paseo del granjero: caminas con un peso pesado en cada mano el mayor tiempo posible. "
                                    "Agarre, hombros, core y resistencia mental en un solo paseo."),
    # ---- Cuello (definiciones) ----
    "neck_extension_cuello": ("Extensiones de cuello: llevas la cabeza hacia atrás contra resistencia. "
                              "Fortalece la zona trasera del cuello, clave para deportes de contacto y postura."),
    "neck_flexion_cuello": ("Flexiones de cuello: llevas la cabeza hacia delante contra resistencia. "
                            "El trabajo de la zona frontal del cuello, complemento perfecto de las extensiones."),
    "neck_flexion_lateral_cuello": ("Flexión lateral de cuello: inclinas la cabeza hacia el lado contra resistencia. "
                                    "Fortalece los laterales del cuello para un cuello completo y fuerte."),
    # ---- Espalda baja (definiciones) ----
    "back_extension_lumbar": ("Hiperextensiones de espalda baja: en el banco, bajas el torso y subes con la espalda "
                              "trabajando. El ejercicio base para una espalda fuerte y sin molestias."),
    "back_superman_suelo": ("Superman en el suelo: boca abajo, levantas brazos y piernas del piso a la vez. "
                            "La espalda baja trabaja sin máquinas y el core también se enciende."),
    "back_hiperextension_45_zercher_espalda_baja": ("Hiperextensión a 45 Zercher para espalda baja: la carga en los codos "
                                                    "mientras subes el torso en el banco inclinado. Exigente y controlada."),
    "back_jefferson_curl": ("Jefferson curl: con la carga en las manos, doblas la espalda vértebra a vértebra hasta "
                            "redondearla y vuelves igual de lento. El ejercicio de movilidad más controvertido y más "
                            "respetado entre los que saben."),
    # ---- Muñeca y antebrazo (definiciones) ----
    "forearms_curl_muneca_sentado": ("Curl de muñeca: con los antebrazos apoyados, subes y bajas la muñeca con la palma "
                                     "hacia arriba. El ejercicio base para unos antebrazos fuertes y un agarre de acero."),
    "forearms_curl_muneca_inverso_sentado": ("Extensión de muñeca: con los antebrazos apoyados y las palmas hacia abajo, "
                                             "subes la mano contra resistencia. El complemento ideal para antebrazos "
                                             "equilibrados y muñecas sanas."),
    "forearms_curl_muneca_de_pie_tras_espalda_barra": ("Curl de muñeca de pie con la barra tras la espalda: los antebrazos "
                                                       "están extendidos y la muñeca flexiona contra la barra. Variante "
                                                       "clásica que estira y trabaja el antebrazo a fondo."),
    "forearms_enrollamiento_muneca_rodillo": ("Enrollamiento de muñeca con rodillo: enrollas y desenrollas una cuerda con "
                                              "peso girando la muñeca. El ejercicio de la vieja escuela para antebrazos "
                                              "brutales."),
    "forearms_pinza_de_discos": ("Pinza de discos: agarras dos discos lisos con los dedos y los sostienes el mayor tiempo "
                                 "posible. El ejercicio de fuerza de agarre más puro que existe."),
    # ---- Hombros (definiciones) ----
    "military_press": ("Press militar: de pie, empujas la barra desde los hombros hasta arriba de la cabeza. "
                       "El ejercicio rey para hombros fuertes y espalda alta sólida."),
    "seated_shoulder_press": ("Press de hombros sentado: el banco con respaldo fija el torso y aísla los hombros. "
                              "La opción cómoda para cargar peso sin balancear el cuerpo."),
    "arnold_press": ("Press Arnold: empiezas con las palmas hacia ti y vas girando mientras subes. "
                     "El press que trabaja el hombro completo en un solo movimiento, directo de Arnold."),
    "z_press": ("Press Z: sentado en el suelo sin respaldo, empujas la barra arriba. "
                "El core y la postura aguantan mientras los hombros empujan: exigente y revelador."),
    "deltoides_push_press": ("Push press: impulsas la barra con las piernas y terminas el empuje con los hombros. "
                             "Potencia de todo el cuerpo para mover cargas pesadas por encima de la cabeza."),
    "standing_lateral_raise": ("Elevaciones laterales de pie: subes los brazos a los costados hasta la altura del hombro. "
                               "El ejercicio que construye hombros anchos, el favorito de todos."),
    "seated_lateral_raise": ("Elevaciones laterales sentado: el torso fijo elimina el impulso y el hombro trabaja limpio. "
                             "La versión estricta de la elevación lateral."),
    "lateral_raise_super_rom": ("Elevaciones laterales Super ROM: subes los brazos más allá de la horizontal, pasando por "
                                "encima de la cabeza. La variante que lleva el hombro al límite de su recorrido."),
    "rear_delt_raise": ("Elevaciones posteriores: inclinado, abres los brazos hacia los lados trabajando el deltoides "
                        "trasero. El ejercicio que completa el hombro y corrige la postura."),
    "deltoides_elevaciones_frontales": ("Elevaciones frontales: subes los brazos al frente hasta la altura del hombro. "
                                        "El trabajo directo del deltoides anterior, simple y efectivo."),
    "deltoides_remo_menton": ("Remo al mentón: subes la barra pegada al cuerpo hasta la barbilla. "
                              "Trapecios y hombros trabajando juntos, con cuidado de no forzar el hombro."),
    "deltoides_y_raises_sentado_banco_inclinado": ("Y-raises en banco inclinado: acostado boca abajo, subes los brazos en "
                                                   "forma de Y. El ejercicio fino para la espalda alta y el hombro."),
    "deltoides_lu_raises": ("LU-raises: subes los brazos con los codos doblados en forma de L y U. "
                            "Un movimiento poco común que trabaja el manguito rotador y el hombro."),
    "deltoides_face_pull": ("Face pull: tiras de la cuerda hacia la cara abriendo los codos. "
                            "El ejercicio de la salud del hombro: deltoides posterior y espalda alta."),
    "deltoides_press_landmine_unilateral": ("Press landmine unilateral: una punta de la barra anclada al suelo y empujas "
                                            "con un brazo en diagonal. Fuerza de hombro con el torso estable."),
    # ---- Tríceps (definiciones) ----
    "triceps_pushdown": ("Extensión de tríceps: con los codos pegados al cuerpo, estiras los brazos hacia abajo. "
                         "El ejercicio básico y favorito para el tríceps, simple y muy efectivo."),
    "overhead_triceps_extension": ("Extensión de tríceps overhead: con los brazos arriba, flexionas y estiras detrás de "
                                   "la cabeza. Estira el tríceps a fondo: el ejercicio de la parte larga del brazo."),
    "crossbody_triceps_extension": ("Extensión de tríceps cruzada en polea alta: tiras del cable cruzando el cuerpo en "
                                    "diagonal. El tríceps trabaja con un ángulo que sorprende y quema."),
    "triceps_patada": ("Patada de tríceps: inclinado, estiras el brazo hacia atrás con el codo fijo. "
                       "El aislamiento clásico del tríceps, ideal para terminar el brazo."),
    "triceps_press_frances": ("Press francés: acostado, bajas la barra hacia la frente flexionando los codos. "
                              "El tríceps se estira a fondo en cada repetición: clásico y muy efectivo."),
    "triceps_extension_pjr_mancuerna": ("Extensión de tríceps PJR con mancuerna: un brazo a la vez con la mancuerna "
                                        "bajando hacia la cadera. Variante que combina estiramiento y aislamiento."),
    "triceps_flexiones_esfinge": ("Flexiones esfinge: como la flexión normal pero con los codos pegados y hacia atrás. "
                                  "El tríceps hace todo el trabajo con tu propio peso."),
    "triceps_fondos_entre_bancos": ("Fondos entre bancos: las manos en un banco y bajas el cuerpo flexionando los codos. "
                                    "Tríceps a tope con el peso corporal, fácil de escalar."),
    "triceps_press_maquina": ("Press de tríceps en máquina: empujas la palanca hacia abajo con el recorrido guiado. "
                              "La forma más cómoda de aislar el tríceps."),
    "triceps_rolling_extension": ("Rolling extension de tríceps: con la barra, extiendes los codos dejando que ruede por "
                                  "los muslos. Una variante rara que se siente fresca y exigente."),
    "triceps_extension": ("Extensión de tríceps en TRX: colgado de las cuerdas, estiras los brazos con tu peso. "
                          "El tríceps trabaja con equilibrio incluido, ideal para casa o viaje."),
    "katana_extension": ("Extensión Katana: la polea a un lado y tiras en diagonal abriendo el brazo como una katana. "
                         "Un movimiento moderno y exigente que enciende el tríceps desde otro ángulo."),
    "jm_press": ("JM Press: acostado, bajas la barra hasta el mentón con los codos pegados y empujas. "
                 "Un híbrido entre press y francés que carga el tríceps con fuerza bruta."),
    "california_press": ("Press California: acostado, bajas la barra al pecho y empujas con los codos hacia adentro. "
                         "El tríceps y el pecho trabajan juntos en un press con más tríceps."),
    "tate_press": ("Tate press: acostado con mancuernas, las bajas juntando los codos hacia el pecho. "
                   "Un movimiento poco común que aísla el tríceps con una sensación única."),
    # ---- Espalda, jalones y empujes horizontales (definiciones) ----
    "lat_pulldown": ("Jalón al pecho: sentado en la polea, tiras de la barra hacia el pecho. "
                     "El ejercicio más popular para los dorsales y la forma más fácil de empezar con la espalda."),
    "pull_up": ("Dominadas: te cuelgas de la barra y subes el pecho hasta ella. "
                "El ejercicio de espalda por excelencia: sin máquinas, sin excusas y muy gratificante."),
    "push_up": ("Flexiones de brazos: bajas y subes el cuerpo con los brazos, manteniéndolo recto. "
                "El ejercicio de pecho más simple y completo, disponible en cualquier parte."),
    "tren_superior_cruce_poleas": ("Cruce de poleas: de pie entre dos poleas, llevas las manos de los lados al centro. "
                                   "El pectoral se aísla con tensión constante y la altura de la polea cambia el enfoque."),
    "tren_superior_fondos": ("Fondos en paralelas: te suspendes entre las barras y bajas flexionando los codos. "
                             "Pecho y tríceps con el peso corporal, intensos y muy efectivos."),
    "tren_superior_press_banca_cadenas": ("Press de banca con cadenas: las cadenas cuelgan de la barra y la resistencia "
                                          "crece al subir. La tensión se ajusta al punto más fuerte del press."),
    "tren_superior_press_banda_resistencia": ("Press con banda de resistencia: la banda añade tensión variable al press. "
                                              "La resistencia crece arriba, justo donde el pecho está más fuerte."),
    "tren_superior_press_pecho_maquina_convergente": ("Press plano en máquina convergente: los brazos se juntan al empujar, "
                                                      "siguiendo el arco natural del pecho. Cómoda, segura y efectiva."),
    "tren_superior_press_inclinado_maquina_convergente": ("Press inclinado en máquina convergente: el ángulo sube el foco a "
                                                          "la parte alta del pecho con el guiado de la máquina."),
    "tren_superior_press_spoto_barra": ("Press Spoto: como el press de banca, pero haciendo una pausa justo antes del pecho. "
                                        "Elimina el rebote y trabaja el punto débil del empuje."),
    "tren_superior_press_unilateral_polea": ("Press unilateral en polea: empujas una sola mano contra la polea baja. "
                                             "Cada lado del pecho trabaja por separado y corriges desequilibrios."),
    "tren_superior_squeeze_press_mancuernas": ("Squeeze press: presionas las mancuernas juntas mientras empujas. "
                                               "El pectoral se contrae al máximo en cada repetición."),
    "back_band_pull_apart": ("Band pull-apart: sujetas la banda al frente y la separas hasta el pecho. "
                             "La espalda alta y los hombros se activan, perfecto para calentar y corregir postura."),
    "back_remo_banda": ("Remo en banda elástica: con la banda anclada, tiras hacia el torso. "
                        "El remo portable: fácil de hacer en casa o en el parque."),
    "back_remo_gorilla_mancuernas": ("Remo gorila: inclinado con las pesas colgando, tiras con la espalda y el torso "
                                     "balanceándose. El remo de la fuerza bruta, exigente y muy completo."),
    "back_remo_invertido": ("Remo invertido: colgado bajo una barra con el cuerpo recto, te acercas a ella. "
                            "La dominada horizontal: espalda trabajando con tu propio peso."),
    "back_remo_renegado_mancuernas": ("Remo renegado: en posición de plancha con mancuernas, remas un brazo a la vez. "
                                      "Espalda y core combatiendo el giro en cada repetición."),
    "back_encogimientos": ("Encogimientos: subes los hombros hacia las orejas con el peso en las manos. "
                           "El ejercicio directo de los trapecios, simple y efectivo."),
    "back_encogimientos_kelso": ("Kelso shrugs: encogimientos con los brazos por detrás del cuerpo, pegados a la espalda. "
                                 "La variante que enciende los trapecios desde un ángulo distinto."),
    "back_y_raises": ("Y-raises: subes los brazos en forma de Y desde el suelo o el banco. "
                      "La espalda alta y los hombros trabajando con un movimiento fino y elegante."),
    "back_dominadas_escapulares": ("Dominadas escapulares: te cuelgas de la barra y solo mueves los omóplatos, sin doblar "
                                   "los codos. El ejercicio de activación de la espalda que todo el mundo necesita."),
    "lying_pullover": ("Pullover en banca: acostado, llevas el peso por detrás de la cabeza y lo vuelves sobre el pecho. "
                       "El dorsal se estira a fondo: el ejercicio clásico de la espalda ancha."),
    "pullover": ("Pull over: acostado o de pie en polea, llevas el brazo por detrás de la cabeza y vuelves. "
                 "Dorsales y pecho trabajando en un arco largo y profundo."),
}

CFG_DESC: dict[str, str] = {
    # ---- Remo con Pecho Apoyado ----
    "chest_supported_row__dumbbells__wide": ("Con mancuernas y agarre amplio, la espalda alta y el trapecio entran a trabajar con ganas. "
                                             "El banco te deja exprimir el remo sin cargar la zona baja."),
    "chest_supported_row__kettlebell__wide": ("Con kettlebell y agarre amplio, el peso cuelga más bajo y el estiramiento de la espalda alta "
                                              "se siente al empezar cada tirón. Distinto, exigente y muy completo."),
    "chest_supported_row__machine__wide": ("En máquina con agarre amplio, el recorrido viene guiado y puedes cargar peso de verdad sin "
                                           "distracciones. Perfecta para meter volumen de espalda en serio."),
    "chest_supported_row__cable__wide__high": ("Polea alta con agarre amplio: el ángulo del tirón llega desde arriba y la espalda alta "
                                               "aguanta tensión constante. Excelente para finalizar la sesión de espalda."),
    "chest_supported_row__cable__wide__mid": ("Polea media con agarre amplio: tensión pareja en todo el recorrido y la espalda alta "
                                              "protagonizando el tirón. Una forma suave y constante de trabajar los dorsales."),
    "chest_supported_row__cable__wide__low": ("Polea baja con agarre amplio: el tirón sale de abajo y el trapecio junto a la espalda alta "
                                              "reciben el estímulo principal. Cambio de ángulo que se agradece."),
    "chest_supported_row__dumbbells__medium": ("Con mancuernas y agarre medio, el trabajo se reparte entre el dorsal y la espalda media "
                                               "de forma equilibrada. El remo más completo y natural de la familia."),
    "chest_supported_row__kettlebell__medium": ("Con kettlebell y agarre medio, cada tirón arranca desde un estiramiento profundo del "
                                                "dorsal. La campana añade un matiz de agarre que sienta de maravilla."),
    "chest_supported_row__machine__medium": ("En máquina con agarre medio, la carga va directa a la espalda media y al dorsal sin "
                                             "desgaste innecesario. Ideal para acumular series con buena técnica."),
    "chest_supported_row__cable__medium__high": ("Polea alta con agarre medio: el tirón diagonal trabaja el dorsal con tensión "
                                                 "constante. Buen cambio de estímulo para mitad de sesión."),
    "chest_supported_row__cable__medium__mid": ("Polea media con agarre medio: la tensión no se suelta en ningún punto del recorrido "
                                                "y el dorsal se mantiene activo siempre. Muy cómodo para series largas."),
    "chest_supported_row__cable__medium__low": ("Polea baja con agarre medio: el remo clásico en polea, con tensión constante desde el "
                                                "primer tirón. La base de muchas rutinas de espalda por una razón."),
    "chest_supported_row__dumbbells__close": ("Con mancuernas y agarre cerrado, los codos viajan pegados al cuerpo y el dorsal se "
                                              "estira más en cada repetición. Sensación profunda en la parte baja de la espalda."),
    "chest_supported_row__kettlebell__close": ("Con kettlebell y agarre cerrado, la espalda media trabaja con mucho recorrido y el "
                                               "dorsal siente el estiramiento completo. Un remo exigente que rinde bien."),
    "chest_supported_row__machine__close": ("En máquina con agarre cerrado, los codos pegados hacen que el dorsal trabaje como aislado. "
                                            "Perfecta para concentrar el esfuerzo en la espalda sin pensar en nada más."),
    "chest_supported_row__cable__close__high": ("Polea alta con agarre cerrado: el tirón llega desde arriba y el dorsal queda tenso en "
                                                "todo momento. Buen ejercicio para rematar la sesión de espalda."),
    "chest_supported_row__cable__close__mid": ("Polea media con agarre cerrado: tensión continua con los codos pegados, poniendo el "
                                               "foco en el dorsal. Variación fina que se siente al final de la serie."),
    "chest_supported_row__cable__close__low": ("Polea baja con agarre cerrado: el tirón arranca desde abajo y el dorsal dirige todo el "
                                               "recorrido. Un clásico que nunca decepciona para la espalda media."),
    # ---- Remo Convencional ----
    "conventional_row__barbell": ("Con barra, el remo más clásico de todos: cargas pesadas, torso firme y codos hacia atrás. "
                                  "La base de cualquier espalda seria en el gimnasio."),
    "conventional_row__dumbbells": ("Con mancuernas, cada lado trabaja por separado y corriges desequilibrios de espalda. "
                                    "Además puedes apoyar la rodilla en el banco y centrarte en el tirón."),
    "conventional_row__machine": ("En máquina, el recorrido guiado te deja concentrarte en la contracción de la espalda. "
                                  "La opción más estable para acumular volumen sin distracciones."),
    "conventional_row__smith_machine": ("En Smith, la barra sube en línea recta y solo te queda empujar con la espalda. "
                                        "Perfecto para progresar con peso sin preocuparte por el equilibrio."),
    "conventional_row__kettlebell": ("Con kettlebell, el remo se vuelve más dinámico y el agarre también trabaja. "
                                     "Una variante que mezcla espalda y fuerza de agarre en cada repetición."),
    "conventional_row__cable": ("En polea, la tensión es constante de arriba a abajo del recorrido y la espalda no descansa. "
                                "Ideal para cerrar la sesión con un buen bombeo de dorsales."),
    # ---- Remo Pendlay ----
    "pendlay_row__barbell": ("Con barra y cada repetición desde el suelo: explosión, control y espalda a tope. "
                             "El remo de los que entrenan fuerza de verdad."),
    "pendlay_row__dumbbells": ("Con mancuernas, la salida desde el suelo es más libre y corriges lado a lado. "
                               "Poco habitual, muy exigente y brutal para la espalda."),
    "pendlay_row__machine": ("En máquina, puedes reproducir la explosividad del pendlay sin la parte de equilibrio. "
                             "Una forma segura de entrenar la espalda con potencia."),
    "pendlay_row__smith_machine": ("En Smith, la barra sube guiada y el movimiento explosivo queda más controlado. "
                                   "Ideal para aprender el pendlay o cargar algo más de peso."),
    "pendlay_row__kettlebell": ("Con kettlebell, el tirón desde el suelo se siente natural y el agarre suma trabajo. "
                                "Variante completa para espalda y antebrazos."),
    "pendlay_row__cable": ("En polea, la tensión constante cambia el pendlay: menos explosión, más tensión sostenida. "
                           "Un final de sesión excelente para los dorsales."),
    # ---- Remo Gironda ----
    "gironda_row__wide": ("Agarre amplio para el columpio Gironda: la espalda alta toma protagonismo con cada vaivén. "
                          "Movimiento amplio que se siente muy distinto al remo tradicional."),
    "gironda_row__medium": ("Agarre medio para el Gironda: el reparto entre dorsal y espalda media queda equilibrado. "
                            "La puerta de entrada perfecta a este estilo de remo."),
    "gironda_row__close": ("Agarre cerrado para el Gironda: el dorsal trabaja con máximo recorrido en cada balanceo. "
                           "La variante más profunda de la familia Gironda."),
    # ---- Remo Seal ----
    "seal_row__barbell": ("Con barra acostado en el banco: aislamiento total de la espalda y cero trampas. "
                          "De los remos más honestos que existen, con una contracción que se siente clarísima."),
    "seal_row__dumbbells": ("Con mancuernas acostado en el banco: cada brazo trabaja solo y el dorsal se estira a fondo. "
                            "Perfecto para sentir la espalda de verdad y corregir desequilibrios."),
    # ---- Remo en Barra T ----
    "t_bar_row__t_bar__wide": ("Barra T con agarre amplio: la carga anclada y las manos abiertas reparten el trabajo a la espalda alta. "
                               "El remo de gimnasio serio con el que se construye grosor."),
    "t_bar_row__machine__wide": ("Máquina de barra T con agarre amplio: el guiado te permite cargar peso sin pensar en equilibrio. "
                                 "La vía más cómoda hacia una espalda alta potente."),
    "t_bar_row__t_bar__medium": ("Barra T con agarre medio: dorsal y espalda media reparten el trabajo en el tirón clásico. "
                                 "El agarre de siempre, el ejercicio de siempre, la espalda de siempre."),
    "t_bar_row__machine__medium": ("Máquina de barra T con agarre medio: recorrido guiado y tensión constante en la espalda media. "
                                   "Rápida de montar y muy productiva para volumen."),
    "t_bar_row__t_bar__close": ("Barra T con agarre cerrado: los codos pegados llevan el foco al dorsal con más recorrido. "
                                "La variante que más profundo se siente en la espalda."),
    "t_bar_row__machine__close": ("Máquina de barra T con agarre cerrado: el dorsal trabaja aislado y el banco sujeta el pecho. "
                                  "Cómoda, segura y muy efectiva para la espalda media."),
    # ---- Press de Banca Plano ----
    "bench_press__barbell": ("Con barra, mueves la mayor cantidad de peso y lo compruebas en cada sesión: los números no mienten. "
                             "El press plano clásico, puro y duro."),
    "bench_press__dumbbells": ("Con mancuernas, cada brazo trabaja por su cuenta y el recorrido es más natural para el hombro. "
                               "La opción favorita para equilibrio, estética y seguridad."),
    "bench_press__smith_machine": ("En Smith, la barra va guiada y puedes exprimir el pecho sin preocuparte por el equilibrio. "
                                   "Ideal para series pesadas en solitario con total confianza."),
    "bench_press__machine": ("En máquina, el recorrido fijo te deja concentrarte en empujar con el pecho a tope. "
                             "La versión más cómoda para acumular volumen sin desgaste."),
    "bench_press__cable": ("En polea, la tensión constante llega hasta arriba del empuje y el pecho no descansa jamás. "
                           "Un cambio de estímulo brutal para el final de la rutina."),
    "bench_press__kettlebell": ("Con kettlebell, el press se vuelve más inestable y el trabajo de estabilidad se dispara. "
                                "Variante poco común que exige concentración y enciende el pecho."),
    # ---- Press de Banca Declinado ----
    "decline_bench_press__barbell": ("Barra en banco declinado: el ángulo apunta directo a la parte baja del pecho. "
                                     "Complemento perfecto para el press plano con carga libre."),
    "decline_bench_press__dumbbells": ("Mancuernas en banco declinado: el trabajo cae a la parte inferior del pectoral con "
                                       "recorrido libre. Variante fina para dar forma al pecho."),
    "decline_bench_press__smith_machine": ("Smith en banco declinado: barra guiada y ángulo bajo para aislar el pectoral inferior. "
                                           "Cómoda para cargar peso y sentir la zona baja."),
    "decline_bench_press__machine": ("Máquina declinada: recorrido fijo y foco total en la parte baja del pecho. "
                                     "La opción más limpia para rematar el pectoral inferior."),
    "decline_bench_press__cable": ("Polea en banco declinado: tensión constante que mantiene la parte baja del pecho ardiendo. "
                                   "Variante original que se agradece a mitad de rutina."),
    "decline_bench_press__kettlebell": ("Kettlebell en banco declinado: inestabilidad extra y trabajo enfocado en el pecho bajo. "
                                        "Para quien busca salir de la zona cómoda."),
    # ---- Press de Banca Inclinado ----
    "incline_bench_press__barbell": ("Barra en banco inclinado: el empuje sube el foco a la parte alta del pecho y al hombro. "
                                     "El press que más redondea el torso visto de frente."),
    "incline_bench_press__dumbbells": ("Mancuernas en banco inclinado: la parte alta del pecho trabaja con recorrido libre y "
                                       "natural. El favorito para dar forma a la clavícula."),
    "incline_bench_press__smith_machine": ("Smith en banco inclinado: barra guiada, ángulo alto y foco en el pectoral superior. "
                                           "Segura para progresar con peso en solitario."),
    "incline_bench_press__machine": ("Máquina inclinada: el recorrido fijo lleva la tensión directa a la parte alta del pecho. "
                                     "Perfecta para terminar el día de pecho sin distracciones."),
    "incline_bench_press__cable": ("Polea en banco inclinado: tensión constante en la zona alta del pecho en todo el empuje. "
                                   "Un remate fino que se siente desde la primera serie."),
    "incline_bench_press__kettlebell": ("Kettlebell en banco inclinado: el ángulo alto más la inestabilidad encienden el pecho "
                                        "superior. Variante exigente para rutinas distintas."),
    # ---- Floor Press ----
    "floor_press__barbell": ("Barra desde el suelo: los codos tocan piso, el recorrido se acorta y el pecho aguanta cargas "
                             "pesadas con hombros felices. Un press plano camuflado de seguro."),
    "floor_press__dumbbells": ("Mancuernas desde el suelo: mismo recorrido corto y protector, con cada brazo trabajando por "
                               "separado. Ideal para ganar fuerza de pecho cuidando el hombro."),
    # ---- Aperturas ----
    "flat_chest_fly__dumbbells": ("Mancuernas en aperturas planas: el arco amplio estira el pecho en cada bajada. "
                                  "La sensación de abrir el pecho al máximo no tiene comparación."),
    "flat_chest_fly__cable": ("Polea en aperturas planas: la tensión nunca se suelta y el pectoral trabaja de punta a punta. "
                              "El clásico de los gimnasios para un pecho definido."),
    "flat_chest_fly__machine": ("Máquina de aperturas: recorrido guiado y tensión constante en el pecho. "
                                "La opción más cómoda para concentrarte solo en abrir y cerrar."),
    "incline_chest_fly__dumbbells": ("Mancuernas en apertura inclinada: el arco se eleva y la parte alta del pecho toma el mando. "
                                     "Trabajo fino para definir la zona superior."),
    "incline_chest_fly__cable": ("Polea en apertura inclinada: el ángulo alto con tensión continua esculpe la parte alta. "
                                 "Una combinación que sienta el pecho desde el primer tirón."),
    "incline_chest_fly__machine": ("Máquina inclinada de aperturas: el guiado sube el foco a la zona alta sin esfuerzo extra. "
                                   "Fácil de cargar y muy efectiva para definir."),
    "decline_chest_fly__dumbbells": ("Mancuernas en apertura declinada: el arco baja y el pectoral inferior se estira a fondo. "
                                     "La variante que completa la parte baja del pecho."),
    "decline_chest_fly__cable": ("Polea en apertura declinada: tensión constante en el pectoral inferior de punta a punta. "
                                 "Cambio de ángulo que se siente como una novedad refrescante."),
    "decline_chest_fly__machine": ("Máquina declinada de aperturas: el guiado apunta directo a la parte baja del pecho. "
                                   "Cómoda, estable y muy efectiva para completar el pecho."),
    # ---- Aperturas Inversas ----
    "reverse_pec_fly__machine__bilateral": ("Máquina Pec Deck invertida con ambos brazos: el deltoides posterior y la espalda alta "
                                            "trabajan juntos en cada apertura. El ejercicio más popular para corregir postura."),
    "reverse_pec_fly__machine__unilateral": ("Máquina Pec Deck invertida de un lado a la vez: aísla cada deltoides posterior y "
                                             "corrige desequilibrios entre hombros. Muy útil para quien entrena asimétrico."),
    "reverse_pec_fly__cable__bilateral": ("Polea cruzada con ambos brazos: la tensión continua hace arder el deltoides posterior. "
                                          "Variante fina para completar el trabajo del hombro."),
    "reverse_pec_fly__cable__unilateral": ("Polea con un solo brazo: cada hombro posterior trabaja por separado con tensión "
                                           "constante. Perfecto para igualar ambos lados."),
    "reverse_pec_fly__dumbbells__bilateral": ("Mancuernas con ambos brazos: apertura inversa clásica, con estiramiento al frente. "
                                              "El movimiento de corrección postural por excelencia."),
    "reverse_pec_fly__dumbbells__unilateral": ("Mancuerna con un solo brazo: deltoides posterior aislado con máximo control. "
                                               "Excelente para centrarse en un lado a la vez."),
    # ---- Core (configuraciones) ----
    "core_plancha__default": ("Versión isométrica clásica: el cuerpo recto como una tabla y el abdomen apretado durante toda la "
                              "serie. Simple de aprender, demoledor para el core y perfecto para empezar."),
    "core_dragon_flag_banco_plano__default": ("Versión completa del dragon flag: desde acostado subes el cuerpo entero sin doblar "
                                              "las rodillas y desciendes con freno. La joya de la corona del trabajo abdominal."),
    "core_rueda_abdominal__default": ("Rueda desde las rodillas o de pie: extiendes el cuerpo y vuelves con el abdomen cargado. "
                                      "Un movimiento pequeño con un resultado enorme para la zona media."),
    "core_press_pallof__default": ("Polea a la altura del pecho y cuerpo firme: llevas la cuerda al frente resistiendo el giro. "
                                   "El ejercicio que enseña a tu core a no girarse cuando no debe."),
    "core_elevacion_piernas__default": ("Colgado de la barra, subes las piernas rectas o dobladas hasta la cintura. "
                                        "El abdomen trabaja de verdad porque no puede hacer trampa con el impulso."),
    "core_inclinacion_lateral__default": ("De pie o sentado, te inclinas hacia el lado cargando el oblicuo. "
                                          "Movimiento corto, sensación larga: ideal para terminar el día de abdomen."),
    "core_crunch_banco_declinado_lastrado_disco__default": ("En el banco declinado el abdomen trabaja contra la gravedad en cada "
                                                            "crunch y el disco en el pecho añade el extra que quieras."),
    "core_crunch_en_polea_alta__default": ("De rodillas frente a la polea alta, tiras de la cuerda desde la frente y crujes. "
                                           "La tensión constante hace que el abdomen trabaje en la subida y en la vuelta."),
    "core_crunch_maquina__default": ("Sentado en la máquina con el peso ajustado, crujes con el recorrido guiado. "
                                     "Rápida de usar, cómoda para cargar y muy efectiva para el abdomen."),
    "core_crunch_suelo_peso_corporal__default": ("El crunch básico del suelo: rodillas dobladas, mirada al techo y contracción "
                                                 "limpia del abdomen. La base de todo y el más difícil de hacer bien."),
    "core_lenador_polea__default": ("Polea alta y movimiento de leñador: tiras en diagonal cruzando el cuerpo de arriba abajo. "
                                    "Oblicuos y abdomen completo rotando con potencia en cada repetición."),
    # ---- Bíceps (configuraciones) ----
    "biceps_curl_bayesian__dumbbells__supinated": ("Mancuerna con palma hacia arriba: el brazo cuelga detrás del tronco y el "
                                                   "bíceps estirado hace todo el trabajo. El clásico de los que aman el detalle."),
    "biceps_curl_bayesian__dumbbells__neutral": ("Mancuerna con agarre neutro: el braquial y el antebrazo se suman al bíceps "
                                                 "en el estiramiento bayesian. Variación que cambia el sabor del ejercicio."),
    "biceps_curl_bayesian__dumbbells__pronated": ("Mancuerna con palma hacia abajo: el trabajo se corre al braquial y al "
                                                  "antebrazo con el brazo colgando atrás. La versión más exigente del bayesian."),
    "biceps_curl_bayesian__cable__supinated": ("Polea con palma hacia arriba: la tensión no se suelta ni un instante y el bíceps "
                                               "queda cargado en todo el arco. El bayesian definitivo en versión polea."),
    "biceps_curl_bayesian__cable__neutral": ("Polea con agarre neutro: tensión constante repartida entre bíceps y antebrazo. "
                                             "Una variante cómoda que se siente igual de exigente."),
    "biceps_curl_bayesian__cable__pronated": ("Polea con palma hacia abajo: el braquial y el antebrazo aguantan la tensión en el "
                                              "punto más estirado. Para brazos que ya no se asustan con nada."),
    "biceps_curl_crucifijo__default": ("En polea, el brazo cruza el pecho y sube flexionando: el estiramiento del bíceps es "
                                       "distinto y la tensión constante lo hace arder. Variante original para cambiar."),
    "biceps_curl_drag__barbell__supinated": ("Barra con palmas arriba arrastrada pegada al cuerpo: los codos van atrás y el "
                                             "bíceps trabaja con la contracción más pura. Un clásico de los entendidos."),
    "biceps_curl_sentado_banco_plano__dumbbells": ("Mancuernas sentado en banco plano: tronco firme y cada brazo por su cuenta, "
                                                   "sin impulso posible. Ideal para corregir desequilibrios de bíceps."),
    "biceps_curl_sentado_banco_plano__cable": ("Polea sentado en banco plano: la tensión constante se suma al tronco fijo. "
                                               "El resultado: un bíceps cargado de principio a fin."),
    "biceps_curl_superman__default": ("En polea con los brazos detrás del cuerpo: el estiramiento inicial del bíceps es enorme "
                                     "y la tensión acompaña toda la subida. Un curl que se siente como ningún otro."),
    "biceps_curl_trx__supinated": ("Colgado del TRX con palmas arriba: tu peso carga el bíceps y el core aguanta el cuerpo. "
                                   "Equilibrio y brazo a la vez, ideal para entrenar en cualquier sitio."),
    "biceps_curl_waiter__plate": ("Con un disco en copa: la palma mira al techo y el bíceps sube el peso con el antebrazo firme. "
                                  "El ejercicio de camarero que fortalece la muñeca mientras trabaja el brazo."),
    "biceps_curl_zottman__dumbbells": ("Mancuernas con la doble fase: subes supinando y bajas pronando con resistencia. "
                                       "Bíceps en la subida, antebrazos en la bajada: dos ejercicios en uno."),
    "concentration_curl__dumbbells": ("Mancuerna con el codo en el muslo: la contracción del bíceps se siente clarísima y el "
                                      "impulso es imposible. El favorito para esculpir el pico del brazo."),
    "concentration_curl__cable": ("Polea con el codo apoyado: tensión constante en el pico del bíceps con aislamiento total. "
                                  "La versión pulida del concentrado clásico."),
    "preacher_curl__barbell": ("Barra en banco predicador: el codo fijo y la palma arriba, sin espacio para el impulso. "
                               "El bíceps trabaja contra la gravedad en su recorrido completo."),
    "preacher_curl__ez_bar": ("Barra EZ en el predicador: el agarre girado alivia las muñecas y la tensión permanece total. "
                              "La opción cómoda para series pesadas de predicador."),
    "preacher_curl__dumbbells": ("Mancuernas en el predicador: cada brazo por separado con el codo fijo en el banco. "
                                 "Perfecto para igualar ambos bíceps con aislamiento total."),
    "preacher_curl__machine": ("Máquina predicador: el recorrido guiado te deja exprimir el bíceps sin pensar en nada más. "
                               "La forma más cómoda de hacer predicador con carga."),
    "spider_curl__dumbbells__supinated": ("Mancuernas boca abajo en el banco con palmas arriba: el bíceps estirado hace todo el "
                                          "recorrido. El spider clásico, intenso y elegante."),
    "spider_curl__dumbbells__neutral": ("Mancuernas boca abajo con agarre neutro: el braquial se suma y el estiramiento se siente "
                                        "igual de profundo. Variación que reparte el trabajo del brazo."),
    "spider_curl__dumbbells__pronated": ("Mancuernas boca abajo con palmas abajo: el braquial y el antebrazo protagonizan con el "
                                         "brazo colgando. La variante más exigente de la familia spider."),
    "spider_curl__cable__supinated": ("Polea boca abajo en el banco: la tensión constante se suma al estiramiento máximo del "
                                      "bíceps. La combinación favorita de los que buscan quemar."),
    "spider_curl__cable__neutral": ("Polea con agarre neutro en el banco: tensión continua repartida entre bíceps y antebrazo. "
                                    "Una variante fina que deja el brazo bien cargado."),
    "spider_curl__cable__pronated": ("Polea con palmas abajo en el banco: el antebrazo y el braquial aguantan la tensión en el "
                                     "punto más estirado. Para sesiones de brazo que se recuerdan."),
    "spider_curl__barbell__supinated": ("Barra con palmas arriba en el banco: el bíceps estirado carga el peso de ambos brazos "
                                        "a la vez. El spider más puro, en su versión de barra."),
    "spider_curl__barbell__neutral": ("Barra con agarre neutro en el banco: el braquial reparte el trabajo y el estiramiento "
                                      "permanece profundo. Variación sólida para el brazo."),
    "spider_curl__barbell__pronated": ("Barra con palmas abajo en el banco: el antebrazo y el braquial hacen el trabajo pesado "
                                       "con el brazo estirado. La variante más exigente con barra."),
    "standing_biceps_curl__barbell": ("Barra recta de pie: el curl clásico del mundo del fitness, con la mayor cantidad de peso "
                                      "posible y la postura más natural."),
    "standing_biceps_curl__ez_bar": ("Barra EZ de pie: el agarre girado cuida las muñecas y la tensión permanece total. "
                                     "La opción cómoda para entrenar bíceps con frecuencia."),
    "standing_biceps_curl__dumbbells": ("Mancuernas de pie: cada brazo trabaja por separado y puedes girar la palma al subir. "
                                        "El curl más versátil, ideal para equilibrio y forma."),
    "standing_biceps_curl__cable": ("Polea de pie: la tensión constante carga el bíceps en toda la subida y la bajada. "
                                    "Excelente para cerrar la sesión de brazo con un buen bombeo."),
    # ---- Peso muerto y cadera (configuraciones) ----
    "conventional_deadlift__barbell__bilateral": ("Barra con ambas piernas: el peso muerto más puro que existe, desde el suelo "
                                                  "con las dos manos. El movimiento que más fuerza construye."),
    "conventional_deadlift__barbell__unilateral": ("Barra en apoyo de una sola pierna: el equilibrio se convierte en protagonista "
                                                   "y la cadera trabaja a fondo. Ideal para corregir desequilibrios."),
    "conventional_deadlift__smith_machine__bilateral": ("Smith con ambas piernas: la barra sube guiada y puedes concentrarte en "
                                                        "empujar sin pensar en el equilibrio. Segura para series pesadas."),
    "conventional_deadlift__smith_machine__unilateral": ("Smith en una pierna: el guiado te permite trabajar un lado a la vez "
                                                         "con estabilidad. Perfecto para igualar piernas y cadera."),
    "conventional_deadlift__dumbbells__bilateral": ("Mancuernas con ambas piernas: las pesas viajan por fuera de las rodillas y "
                                                    "el recorrido es más libre. La versión amable del peso muerto."),
    "conventional_deadlift__dumbbells__unilateral": ("Mancuerna en una pierna: equilibrio, isquios y glúteo trabajando juntos. "
                                                     "El ejercicio de corrección de desequilibrios por excelencia."),
    "conventional_deadlift__hex_bar__bilateral": ("Barra hexagonal con ambas piernas: el cuerpo queda dentro de la carga y el "
                                                  "recorrido es más vertical. Cómoda, segura y muy efectiva."),
    "conventional_deadlift__hex_bar__unilateral": ("Barra hexagonal en una pierna: la carga estable y el trabajo de equilibrio de "
                                                   "la cadera. Variante exigente que se siente en el glúteo."),
    "sumo_deadlift__barbell": ("Barra en postura sumo: pies amplios, manos dentro y el recorrido más corto hacia arriba. "
                               "La elección de muchos para cargar pesado con comodidad."),
    "sumo_deadlift__dumbbells": ("Mancuernas en postura sumo: la carga a los costados con la base amplia. "
                                 "Variante accesible que mantiene el estímulo de glúteos y piernas."),
    "good_morning__barbell__bilateral": ("Barra sobre los hombros con ambas piernas: la inclinación clásica de cadera hacia "
                                         "delante. El buenos días de toda la vida, exigente y productivo."),
    "good_morning__barbell__unilateral": ("Barra en una sola pierna: el equilibrio añade trabajo a la cadera y la espalda baja. "
                                          "Avanzado, profundo y muy revelador de desequilibrios."),
    "good_morning__safety_bar__bilateral": ("Barra de seguridad sobre los hombros: los agarres frontales hacen la posición más "
                                            "cómoda y estable. La versión moderna del buenos días clásico."),
    "good_morning__safety_bar__unilateral": ("Barra de seguridad en una pierna: comodidad del agarre más el reto del equilibrio. "
                                             "Una combinación exigente para la cadena posterior."),
    "good_morning__smith_machine__bilateral": ("Smith con ambas piernas: la barra guiada te permite ir profundo con confianza. "
                                               "Perfecto para aprender el movimiento o cargar más peso."),
    "good_morning__smith_machine__unilateral": ("Smith en una pierna: el guiado sostiene el equilibrio y la cadera trabaja a fondo. "
                                                "Variante avanzada para corregir asimetrías."),
    "good_morning__machine__bilateral": ("Máquina de buenos días: la cadera apoya en la almohadilla y el recorrido viene guiado. "
                                         "La opción más segura para sentir el trabajo trasero."),
    "good_morning__machine__unilateral": ("Máquina en una pierna: el guiado cuida la postura mientras el lado trabajado hace "
                                          "todo el esfuerzo. Ideal para equilibrar ambos lados."),
    "good_morning__cable__bilateral": ("Polea baja con ambas piernas: la tensión constante acompaña toda la inclinación. "
                                       "Variante suave que mantiene el estímulo sin cargar la espalda."),
    "good_morning__cable__unilateral": ("Polea en una pierna: tensión continua y equilibrio en cada repetición. "
                                        "Un buenos días fino para trabajar la cadera de lado a lado."),
    "good_morning_seated__barbell": ("Barra sentado en el banco: la cadera fija y la tensión directa en los isquios. "
                                     "Menos peso que de pie, pero la sensación es inconfundible."),
    "good_morning_seated__smith_machine": ("Smith sentado: la barra guiada y la postura fija del banco. "
                                           "Cómodo para progresar con seguridad en el movimiento."),
    "good_morning_seated__safety_bar": ("Barra de seguridad sentado: agarre cómodo y cadera fija en el banco. "
                                        "La variante más agradable para trabajar los isquios sentado."),
    "good_morning_zercher__default": ("Barra en los codos, torso inclinándose hacia delante con la carga pegada al pecho. "
                                      "El torso trabaja de más y la sensación en la cadera es distinta a cualquier buenos días."),
    "romanian_deadlift__barbell__bilateral": ("Barra con ambas piernas: el rumano clásico deslizando la barra por los muslos. "
                                              "Isquios y glúteos cargados con la máxima tensión."),
    "romanian_deadlift__barbell__unilateral": ("Barra en una pierna: el rumano con equilibrio, el isquio estirado a fondo y el "
                                               "glúteo trabajando en cada repetición. Avanzado y muy completo."),
    "romanian_deadlift__smith_machine__bilateral": ("Smith con ambas piernas: el guiado te deja estirar los isquios con total "
                                                    "seguridad. Perfecto para series exigentes sin pensarlo."),
    "romanian_deadlift__smith_machine__unilateral": ("Smith en una pierna: estabilidad del guiado más el reto del apoyo único. "
                                                     "Variante fina para corregir desequilibrios."),
    "romanian_deadlift__dumbbells__bilateral": ("Mancuernas con ambas piernas: la carga viaja por delante de los muslos con "
                                                "recorrido libre. La versión accesible del rumano."),
    "romanian_deadlift__dumbbells__unilateral": ("Mancuerna en una pierna: el estiramiento del isquio se siente en cada bajada. "
                                                 "El favorito para equilibrio y fuerza trasera."),
    "romanian_deadlift__hex_bar__bilateral": ("Barra hexagonal con ambas piernas: carga estable y estiramiento profundo de "
                                              "isquios. Cómoda, segura y muy efectiva."),
    "romanian_deadlift__hex_bar__unilateral": ("Barra hexagonal en una pierna: la estabilidad de la carga más el trabajo de "
                                               "equilibrio. Variante exigente para la cadena posterior."),
    "romanian_sumo_deadlift__barbell__bilateral": ("Barra en postura sumo con ambas piernas: el estiramiento de isquios con la "
                                                   "base amplia cambia el foco hacia los glúteos."),
    "romanian_sumo_deadlift__barbell__unilateral": ("Barra sumo en una pierna: la base amplia y el apoyo único exigen estabilidad "
                                                    "y cargan el glúteo. Variante avanzada y muy completa."),
    "romanian_sumo_deadlift__smith_machine__bilateral": ("Smith sumo con ambas piernas: el guiado cuida la postura amplia y el "
                                                         "estiramiento de isquios. Segura para cargar peso."),
    "romanian_sumo_deadlift__smith_machine__unilateral": ("Smith sumo en una pierna: estabilidad y trabajo profundo del glúteo "
                                                          "lado a lado. Ideal para corregir desequilibrios."),
    "romanian_sumo_deadlift__dumbbells__bilateral": ("Mancuernas en postura sumo: la base amplia y la carga a los costados. "
                                                     "Versión accesible del rumano sumo."),
    "romanian_sumo_deadlift__dumbbells__unilateral": ("Mancuerna sumo en una pierna: equilibrio, glúteo y estiramiento profundo. "
                                                      "Un ejercicio que se siente en cada repetición."),
    "romanian_sumo_deadlift__hex_bar__bilateral": ("Barra hexagonal sumo: la carga estable con la base amplia de pies. "
                                                   "Cómoda y muy efectiva para glúteos e isquios."),
    "romanian_sumo_deadlift__hex_bar__unilateral": ("Barra hexagonal sumo en una pierna: estabilidad de la carga y reto de "
                                                    "equilibrio a la vez. Variante exigente para la cadena posterior."),
    # ---- Cadera lateral (configuraciones) ----
    "hip_abduction__seated__machine__bilateral": ("Máquina sentado con ambas piernas: abres y cierras contra las almohadillas "
                                                  "con el glúteo medio aislado. El clásico de las máquinas de cadera."),
    "hip_abduction__seated__machine__unilateral": ("Máquina sentado de un lado a la vez: aísla cada glúteo medio y corrige "
                                                   "desequilibrios entre caderas. Muy útil para quien entrena asimétrico."),
    "hip_abduction__standing__cable__bilateral": ("Polea de pie con ambas piernas: la tensión constante acompaña la apertura y "
                                                  "el glúteo medio trabaja todo el recorrido. Variante fina y efectiva."),
    "hip_abduction__standing__cable__unilateral": ("Polea de pie con una pierna: el glúteo medio trabaja aislado con tensión "
                                                   "continua. Ideal para igualar ambos lados de la cadera."),
    "hip_abduction__standing__band__bilateral": ("Banda de pie con ambas piernas: la resistencia elástica aumenta en el punto "
                                                 "más amplio. Sencilla de montar y muy efectiva para calentar o rematar."),
    "hip_abduction__standing__band__unilateral": ("Banda en una pierna: aperturas contra la resistencia elástica lado a lado. "
                                                  "Perfecta para activar el glúteo medio antes de entrenar."),
    "hip_adduction__seated__machine__bilateral": ("Máquina sentado con ambas piernas: cierras las almohadillas juntando los "
                                                  "muslos y los aductores se encienden. El clásico del muslo interno."),
    "hip_adduction__seated__machine__unilateral": ("Máquina sentado de un lado a la vez: cada aductor trabaja por separado y "
                                                   "corriges desequilibrios. Útil para piernas parejas."),
    "hip_adduction__standing__cable__bilateral": ("Polea de pie con ambas piernas: juntas las piernas contra la tensión del "
                                                  "cable. Variante fina que mantiene el aductor cargado."),
    "hip_adduction__standing__cable__unilateral": ("Polea de pie con una pierna: el aductor trabaja aislado con tensión "
                                                   "constante. Perfecto para centrarse en un lado."),
    "hip_adduction__standing__band__bilateral": ("Banda de pie con ambas piernas: juntas las piernas contra la resistencia "
                                                 "elástica. Fácil de llevar a cualquier sitio."),
    "hip_adduction__standing__band__unilateral": ("Banda en una pierna: aducciones contra la banda lado a lado. "
                                                  "Ideal para activar el muslo interno antes de las sentadillas."),
    "copenhagen_plank__default": ("Versión estática: pierna superior en el banco y cadera arriba todo el tiempo. "
                                  "Los aductores arden con una tensión que no se parece a nada."),
    "copenhagen_plank_dynamic__default": ("Versión dinámica: subes y bajas la cadera con ritmo entre el banco y el suelo. "
                                          "El movimiento añade trabajo extra a los aductores y al core."),
    # ---- Glúteos e isquios (configuraciones) ----
    "glutes_patada_gluteo__cable": ("Polea en el tobillo: empujas el talón atrás y arriba contra la tensión constante. "
                                    "El glúteo trabaja en todo el recorrido, sin descansos."),
    "glutes_patada_gluteo__dumbbells": ("Mancuerna detrás de la rodilla: la patada clásica con carga libre. "
                                        "Sencilla de montar y muy efectiva para sentir el glúteo."),
    "glutes_patada_gluteo__band": ("Banda en el tobillo: la resistencia elástica sube al estirar la pierna. "
                                   "Perfecta para activar el glúteo en casa o como remate."),
    "glutes_patada_gluteo_lateral__cable": ("Polea lateral: abres la pierna hacia el lado con tensión constante en el glúteo "
                                            "medio. La versión pulida de la patada lateral."),
    "glutes_patada_gluteo_lateral__dumbbells": ("Mancuerna en la cadera o el tobillo: apertura lateral con carga libre. "
                                                "Fácil de hacer en cualquier gimnasio y muy directa al glúteo."),
    "glutes_patada_gluteo_lateral__band": ("Banda alrededor de los tobillos: abres la pierna contra la resistencia elástica. "
                                           "El clásico de las rutinas de glúteo en casa."),
    "glutes_puente_gluteos__bilateral__barbell": ("Barra en la cadera con ambas piernas: el puente clásico con carga. "
                                                  "Glúteo trabajando a fondo desde el primer día."),
    "glutes_puente_gluteos__unilateral__barbell": ("Barra en la cadera con una pierna: el glúteo de un lado hace todo el "
                                                   "trabajo y el equilibrio suma reto. Avanzado y muy efectivo."),
    "glutes_puente_gluteos__bilateral__dumbbells": ("Mancuerna en la cadera con ambas piernas: carga cómoda y recorrido libre. "
                                                    "La puerta de entrada perfecta al puente con peso."),
    "glutes_puente_gluteos__unilateral__dumbbells": ("Mancuerna con una pierna: el glúteo trabaja solo y el equilibrio se "
                                                     "entrena de regalo. Ideal para corregir desequilibrios."),
    "glutes_puente_gluteos__bilateral__smith_machine": ("Smith en la cadera con ambas piernas: la barra guiada sube en línea "
                                                        "recta y el glúteo se concentra en empujar. Muy cómodo con peso."),
    "glutes_puente_gluteos__unilateral__smith_machine": ("Smith con una pierna: el guiado sostiene la barra y el lado trabajado "
                                                         "hace el esfuerzo. Variante fina para igualar glúteos."),
    "hip_thrust__bilateral__barbell": ("Barra en la cadera con ambas piernas: el hip thrust clásico, con la espalda en el banco "
                                       "y el glúteo empujando el peso. El ejercicio rey del glúteo."),
    "hip_thrust__unilateral__barbell": ("Barra con una pierna: el glúteo de un lado trabaja solo y el equilibrio se suma. "
                                        "La versión avanzada para quien ya domina el clásico."),
    "hip_thrust__bilateral__smith_machine": ("Smith con ambas piernas: la barra guiada te deja exprimir el glúteo sin pensar "
                                             "en el equilibrio. Perfecto para series pesadas."),
    "hip_thrust__unilateral__smith_machine": ("Smith con una pierna: guiado estable y trabajo unilateral del glúteo. "
                                              "Ideal para corregir desequilibrios con carga."),
    "hip_thrust__bilateral__machine": ("Máquina de hip thrust con ambas piernas: la almohadilla y el guiado hacen el resto. "
                                       "La opción más cómoda para cargar el glúteo con seguridad."),
    "hip_thrust__unilateral__machine": ("Máquina con una pierna: cada glúteo trabaja por separado con el guiado de la máquina. "
                                        "Perfecta para igualar ambos lados."),
    "hip_thrust__bilateral__band": ("Banda sobre la cadera con ambas piernas: la resistencia crece al empujar arriba. "
                                    "Fácil de montar en cualquier sitio y muy exigente al final de la serie."),
    "hip_thrust__unilateral__band": ("Banda con una pierna: el glúteo trabaja solo contra la resistencia elástica. "
                                     "Variante sencilla y muy efectiva para rematar."),
    "glutes_hiperextension_45__dumbbells": ("Mancuerna en el pecho en el banco a 45: subes con la cadera y el glúteo dirige "
                                            "el movimiento. La variante accesible para sentir el glúteo."),
    "glutes_hiperextension_45__barbell": ("Barra en la espalda en el banco a 45: el glúteo empuja el peso con la cadera. "
                                          "La versión fuerte de la hiperextensión para glúteo."),
    "glutes_hiperextension_45__plate": ("Disco en el pecho en el banco a 45: carga ligera y control total del recorrido. "
                                        "Ideal para aprender a usar el glúteo en el banco."),
    "glutes_hiperextension_45__smith_machine": ("Smith en el banco a 45: la barra guiada acompaña el movimiento y puedes "
                                                "cargar con confianza. Variante segura y productiva."),
    "glutes_hiperextension_45_zercher__default": ("Carga en los codos frente al pecho en el banco a 45: el peso queda cerca "
                                                  "del cuerpo y el glúteo hace el trabajo. Exigente y muy controlado."),
    "reverse_hyper__machine": ("En la máquina de reverse hyper: subes las piernas balanceando la cadera con la carga colgando. "
                               "Descarga la espalda y enciende el glúteo a la vez."),
    "glutes_frog_pumps__default": ("Plantas de los pies juntas y rodillas abiertas: bombeas la cadera hacia arriba y el "
                                   "glúteo arde. Un movimiento pequeño con una sensación enorme."),
    "glutes_clamshells_banda__default": ("Acostado de lado con la banda en las rodillas: abres la rodilla superior contra la "
                                         "resistencia. El activador perfecto del glúteo medio antes de entrenar."),
    "glutes_monster_walk_banda__default": ("Banda en los tobillos y pasos laterales: caminas de lado sin dejar que la banda "
                                           "cierre las piernas. El calentamiento que hace llorar a los glúteos."),
    "glutes_step_up_gluteo__default": ("Subes a un cajón empujando con una pierna, con el glúteo protagonizando. "
                                       "Unilateral, exigente y perfecto para corregir desequilibrios."),
    "glutes_zancada_cruzada__default": ("El paso cruza por detrás de la otra pierna y bajas en diagonal. "
                                        "Glúteo y cadera trabajan con un ángulo que la zancada normal no toca."),
    "hams_peso_muerto_rumano_zercher__default": ("Barra en los codos bajando con las piernas casi rectas: el isquio se estira "
                                                 "a fondo y el torso aguanta la carga al frente. Exigente y muy completo."),
    "hams_pull_through__default": ("De espaldas a la polea baja, la cuerda pasa entre las piernas y la cadera empuja atrás y "
                                   "adelante. El ejercicio que enseña la cadera a trabajar con los isquios."),
    "hams_peso_muerto_convencional_deficit__default": ("Desde la plataforma, la barra parte más abajo y el recorrido crece. "
                                                       "Isquios y glúteos con un estiramiento extra desde el primer tirón."),
    "hams_peso_muerto_piernas_rigidas_deficit__default": ("Plataforma más piernas casi rectas: el estiramiento de los isquios "
                                                          "llega al límite. El peso muerto más profundo que existe."),
    "hams_peso_muerto_sumo_deficit__default": ("Postura amplia desde la plataforma: más recorrido que el sumo normal y más "
                                               "trabajo para glúteos y aductores."),
    "hams_swing_kettlebell_dos_manos__default": ("Impulsas la campana entre las piernas y la lanzas al pecho con la cadera. "
                                                 "Potencia, glúteos y un subidón de energía en cada repetición."),
    "hams_swing_kettlebell_unilateral__default": ("La campana viaja con una sola mano y el torso aguanta el giro. "
                                                  "Variante avanzada que suma estabilidad al swing clásico."),
    "stiff_leg_deadlift__bilateral__barbell": ("Barra con ambas piernas casi rectas: el estiramiento de los isquios marca el "
                                               "recorrido. El peso muerto de piernas rígidas clásico."),
    "stiff_leg_deadlift__unilateral__barbell": ("Barra en una pierna casi recta: el isquio estirado a fondo y el equilibrio "
                                                "en cada repetición. Avanzado y muy revelador."),
    "stiff_leg_deadlift__bilateral__smith_machine": ("Smith con ambas piernas: el guiado te deja bajar hasta el estiramiento "
                                                     "máximo con seguridad. Ideal para series profundas."),
    "stiff_leg_deadlift__unilateral__smith_machine": ("Smith en una pierna: estabilidad del guiado y estiramiento unilateral "
                                                      "del isquio. Perfecto para corregir desequilibrios."),
    "stiff_leg_deadlift__bilateral__dumbbells": ("Mancuernas con ambas piernas: la carga a los costados con recorrido libre. "
                                                 "La versión accesible del peso muerto de piernas rígidas."),
    "stiff_leg_deadlift__unilateral__dumbbells": ("Mancuerna en una pierna: el estiramiento del isquio se siente en cada "
                                                  "bajada. El favorito para isquios y equilibrio."),
    "stiff_leg_deadlift__bilateral__hex_bar": ("Barra hexagonal con ambas piernas: carga estable y estiramiento profundo. "
                                               "Cómoda, segura y muy efectiva para los isquios."),
    "stiff_leg_deadlift__unilateral__hex_bar": ("Barra hexagonal en una pierna: la carga estable más el reto del equilibrio. "
                                                "Variante exigente para la parte trasera de la pierna."),
    "glute_ham_raise__default": ("Espinillas apoyadas y cuerpo bajando extendido: isquios y glúteo frenan y suben el peso. "
                                 "El ejercicio de los atletas serios, brutal y adictivo."),
    # ---- Cuádriceps y piernas (configuraciones) ----
    "front_squat__barbell": ("Barra al frente sobre los hombros: el torso erguido y los cuádriceps a fondo. "
                             "La sentadilla frontal clásica, exigente y elegante."),
    "front_squat__smith_machine": ("Smith con la barra al frente: el guiado te permite enfocarte en los cuádriceps sin "
                                   "preocuparte por el equilibrio. Segura para progresar."),
    "front_squat__dumbbells": ("Mancuernas sobre los hombros: la versión accesible de la sentadilla frontal. "
                               "Cómoda de cargar y muy amable para aprender la postura."),
    "front_squat__kettlebell": ("Kettlebells en los hombros: el rack con campanas exige estabilidad y trabajan los "
                                "cuádriceps igual de profundo. Variante original."),
    "front_squat__cable": ("Polea baja con la barra al frente: la tensión constante acompaña toda la sentadilla. "
                           "Variante suave que mantiene el estímulo de cuádriceps."),
    "high_bar_back_squat__barbell": ("Barra sobre los trapecios: la sentadilla reina, bajando profundo con el torso erguido. "
                                     "Cuádriceps y fuerza total en el movimiento más completo."),
    "high_bar_back_squat__smith_machine": ("Smith con barra alta: el guiado te deja bajar profundo con seguridad. "
                                           "Perfecta para aprender la sentadilla o cargar pesado en solitario."),
    "high_bar_back_squat__safety_bar": ("Barra de seguridad con barra alta: los agarres frontales hacen la posición más "
                                        "cómoda y estable. La versión moderna que adoran los que cuidan los hombros."),
    "low_bar_back_squat__barbell": ("Barra baja en la espalda: la cadera empuja atrás y puedes cargar más peso. "
                                    "La elección de los levantadores para sentadillas pesadas."),
    "low_bar_back_squat__smith_machine": ("Smith con barra baja: el guiado sostiene la barra y tú empujas con la cadera. "
                                          "Segura para series pesadas de sentadilla."),
    "low_bar_back_squat__safety_bar": ("Barra de seguridad con barra baja: comodidad del agarre y postura de barra baja. "
                                       "La opción estable para sentadillas profundas."),
    "quads_prensa_piernas__bilateral": ("Prensa con ambos pies: empujas la plataforma con toda la planta. "
                                        "La forma más cómoda de cargar mucho peso en las piernas."),
    "quads_prensa_piernas__unilateral": ("Prensa con una pierna: un lado trabaja solo y corriges desequilibrios. "
                                         "Variante exigente que se siente en el cuádriceps de inmediato."),
    "quads_sentadilla_hack__machine": ("Máquina hack: espalda apoyada y recorrido guiado, el cuádriceps aislado. "
                                       "La versión más popular y la que más peso permite con seguridad."),
    "quads_sentadilla_hack__barbell": ("Hack con barra por detrás: sin máquina, el equilibrio suma trabajo al cuádriceps. "
                                       "Variante exigente para quien busca algo distinto."),
    "quads_sentadilla_hack__smith_machine": ("Smith en posición hack: el guiado sostiene la barra y las piernas empujan. "
                                             "Un punto intermedio cómodo entre barra y máquina."),
    "sumo_squat__barbell": ("Barra con pies amplios: la sentadilla sumo clásica con la carga en la espalda. "
                            "Glúteos y aductores trabajando con los cuádriceps."),
    "sumo_squat__dumbbells": ("Mancuernas en postura sumo: la carga a los costados con la base amplia. "
                              "Accesible y muy efectiva para glúteos y piernas."),
    "sumo_squat__kettlebell": ("Kettlebell en postura sumo: la campana al frente o en copa con pies amplios. "
                               "Variante dinámica que suma agarre al trabajo de pierna."),
    "belt_squat__bilateral": ("Máquina Belt Squat con ambas piernas: la carga cuelga de la cintura y la espalda descansa. "
                              "Sentadillas profundas sin peso sobre los hombros."),
    "belt_squat__unilateral": ("Belt Squat con una pierna: la carga cuelga de la cintura y un lado hace todo el trabajo. "
                               "Ideal para corregir desequilibrios de pierna con seguridad."),
    "pendulum_squat__bilateral": ("Máquina pendular con ambas piernas: el balanceo guiado lleva el cuádriceps a fondo. "
                                  "Exigente, profunda y adictiva."),
    "pendulum_squat__unilateral": ("Pendular con una pierna: un lado a la vez con el guiado de la máquina. "
                                   "La forma más seria de igualar ambas piernas."),
    "quads_extension_cuadriceps__machine__bilateral": ("Máquina con ambas piernas: estiras las rodillas contra la resistencia. "
                                                       "El aislamiento clásico del cuádriceps, simple y efectivo."),
    "quads_extension_cuadriceps__machine__unilateral": ("Máquina con una pierna: cada cuádriceps trabaja solo y corriges "
                                                        "desequilibrios. La versión fina de la extensión."),
    "quads_extension_cuadriceps_pie_polea__bilateral": ("Polea con ambas piernas: el tobillo enganchado y la rodilla "
                                                        "estirando contra la tensión. Variante fina y constante."),
    "quads_extension_cuadriceps_pie_polea__unilateral": ("Polea con una pierna: extensión unilateral con tensión continua. "
                                                         "Perfecta para aislar un cuádriceps a la vez."),
    "forward_lunge__barbell": ("Barra en la espalda y paso al frente: la zancada clásica con carga. "
                               "Piernas y glúteos trabajando con estabilidad."),
    "forward_lunge__smith_machine": ("Smith y paso al frente: el guiado da seguridad y puedes concentrarte en la bajada. "
                                     "Ideal para aprender la zancada con peso."),
    "forward_lunge__dumbbells": ("Mancuernas a los costados y paso al frente: la versión más equilibrada y cómoda. "
                                 "La favorita para series largas de zancada."),
    "forward_lunge__kettlebell": ("Kettlebells y paso al frente: el agarre suma trabajo al ejercicio. "
                                  "Variante completa para piernas y antebrazos."),
    "forward_lunge__cable": ("Polea y paso al frente: la tensión constante añade exigencia a la zancada. "
                             "Un cambio de estímulo que se siente en cada repetición."),
    "reverse_lunge__barbell": ("Barra y paso atrás: la zancada inversa con carga, estable y amable con la rodilla. "
                               "Perfecta para cargar peso con confianza."),
    "reverse_lunge__smith_machine": ("Smith y paso atrás: el guiado sostiene la barra mientras bajas. "
                                     "Segura y cómoda para series pesadas."),
    "reverse_lunge__dumbbells": ("Mancuernas y paso atrás: equilibrio natural y trabajo completo de pierna. "
                                 "La versión más popular de la zancada inversa."),
    "reverse_lunge__kettlebell": ("Kettlebells y paso atrás: la carga al costado suma agarre y estabilidad. "
                                  "Variante completa y original."),
    "reverse_lunge__cable": ("Polea y paso atrás: la tensión constante acompaña la bajada y la subida. "
                             "Una zancada inversa fina para cambiar el estímulo."),
    "walking_lunge__barbell": ("Barra en la espalda y pasos al frente: la zancada caminando clásica, avanzando por el "
                               "gimnasio. Piernas, glúteos y mucha fuerza mental."),
    "walking_lunge__smith_machine": ("Smith y pasos al frente: el guiado da estabilidad mientras avanzas. "
                                     "Cómoda para caminar con carga sin miedo."),
    "walking_lunge__dumbbells": ("Mancuernas y pasos al frente: la versión más equilibrada para caminar con zancadas. "
                                 "La favorita para series largas."),
    "walking_lunge__kettlebell": ("Kettlebells y pasos al frente: el agarre trabaja mientras las piernas avanzan. "
                                  "Variante completa para todo el cuerpo."),
    "walking_lunge__cable": ("Polea y pasos al frente: la tensión constante hace cada zancada más exigente. "
                             "Una caminata que se recuerda al día siguiente."),
    "step_up__barbell": ("Barra en la espalda y subida al cajón: fuerza y estabilidad en cada paso. "
                         "El step-up clásico con carga."),
    "step_up__smith_machine": ("Smith y subida al cajón: el guiado cuida el equilibrio mientras subes. "
                               "Segura para cargar peso en el step-up."),
    "step_up__dumbbells": ("Mancuernas y subida al cajón: la versión más equilibrada y cómoda del step-up. "
                           "La favorita para trabajar piernas y glúteos."),
    "step_up__kettlebell": ("Kettlebell y subida al cajón: el agarre suma trabajo al movimiento. "
                            "Variante completa para pierna y antebrazos."),
    "step_up__cable": ("Polea y subida al cajón: la tensión constante añade exigencia a cada paso. "
                       "Un step-up fino para variar la rutina de pierna."),
    "sissy_squat__barbell": ("Barra en el pecho o la espalda con rodillas al frente: el cuádriceps aislado con carga. "
                             "La versión pesada del sissy, exigente de verdad."),
    "sissy_squat__machine": ("Máquina sissy: los tobillos sujetos y el guiado te dejan bajar profundo. "
                             "La forma más cómoda de exprimir el cuádriceps."),
    "sissy_squat__smith_machine": ("Smith sissy: el guiado de la barra acompaña la inclinación del torso. "
                                   "Segura para progresar en el movimiento."),
    "sissy_squat__dumbbells": ("Mancuerna en el pecho: el sissy con carga ligera y mucho control. "
                               "Ideal para aprender el movimiento quemando cuádriceps."),
    "sissy_squat__plate": ("Disco en el pecho: carga ligera para dominar la inclinación del sissy. "
                           "Perfecto para empezar con el cuádriceps ardiendo."),
    "bulgarian_split_squat__barbell": ("Barra y pie trasero en el banco: la búlgara clásica con carga. "
                                       "El ejercicio unilateral más efectivo para las piernas."),
    "bulgarian_split_squat__smith_machine": ("Smith y pie trasero en el banco: el guiado da seguridad en la bajada. "
                                             "Cómoda para cargar peso en la búlgara."),
    "bulgarian_split_squat__machine": ("Máquina de búlgara: el guiado y el apoyo fijo hacen el ejercicio más estable. "
                                       "La opción cómoda para series exigentes."),
    "bulgarian_split_squat__dumbbells": ("Mancuernas y pie trasero en el banco: la versión más equilibrada y popular. "
                                         "La favorita de todos para trabajar la pierna."),
    "bulgarian_split_squat__cable": ("Polea y pie trasero en el banco: la tensión constante se suma a la búlgara. "
                                     "Variante fina que mantiene el estímulo al máximo."),
    "bulgarian_split_squat__kettlebell": ("Kettlebell y pie trasero en el banco: el agarre suma trabajo al ejercicio. "
                                          "Variante completa para pierna y antebrazos."),
    # ---- Isquios, pantorrillas y resto de pierna (configuraciones) ----
    "lying_leg_curl__bilateral__machine": ("Máquina tumbado con ambas piernas: los talones suben al glúteo contra la "
                                           "resistencia. El aislamiento clásico de los isquios."),
    "lying_leg_curl__unilateral__machine": ("Máquina tumbado con una pierna: cada isquio trabaja solo y corriges "
                                            "desequilibrios. La versión fina del curl tumbado."),
    "lying_leg_curl__bilateral__cable": ("Polea tumbado con ambas piernas: la tensión constante sube los talones con los "
                                         "isquios cargados. Variante exigente y muy completa."),
    "lying_leg_curl__unilateral__cable": ("Polea tumbado con una pierna: tensión continua en un isquio a la vez. "
                                          "Perfecto para igualar ambos lados."),
    "lying_leg_curl__bilateral__dumbbells": ("Mancuerna entre los pies tumbado: sujeta el peso con los pies y flexiona. "
                                             "Una forma original de hacer curl de isquios en casa."),
    "lying_leg_curl__unilateral__dumbbells": ("Mancuerna con un pie: el isquio de una pierna hace todo el trabajo. "
                                              "Variante accesible y muy directa."),
    "seated_leg_curl__bilateral__machine": ("Máquina sentado con ambas piernas: flexionas contra la almohadilla con la "
                                            "espalda cómoda. El curl de isquios más seguro."),
    "seated_leg_curl__unilateral__machine": ("Máquina sentado con una pierna: cada isquio trabaja por separado. "
                                             "Ideal para corregir desequilibrios sentado."),
    "seated_leg_curl__bilateral__cable": ("Polea sentado con ambas piernas: la tensión constante acompaña la flexión. "
                                          "Variante fina para los isquios sentado."),
    "seated_leg_curl__unilateral__cable": ("Polea sentado con una pierna: tensión continua en un isquio a la vez. "
                                           "Perfecto para centrarse en cada lado."),
    "standing_leg_curl__bilateral__machine": ("Máquina de pie con ambas piernas: flexionas contra la resistencia estando "
                                              "de pie. Trabajo limpio y cómodo de los isquios."),
    "standing_leg_curl__unilateral__machine": ("Máquina de pie con una pierna: aislamiento total de un isquio. "
                                               "La opción favorita para igualar ambos lados."),
    "standing_leg_curl__bilateral__cable": ("Polea de pie con ambas piernas: los tobillos enganchados flexionan contra la "
                                            "tensión. Variante constante y exigente."),
    "standing_leg_curl__unilateral__cable": ("Polea de pie con una pierna: tensión continua en un isquio a la vez. "
                                             "Ideal para un trabajo fino de la parte trasera."),
    "curl_isquios_con_balon__default": ("Acostado con el balón entre los pies: llevas el balón hacia el glúteo flexionando "
                                        "las rodillas. Isquios y glúteos ardiendo sin máquinas."),
    "curl_isquios_con_sliders__default": ("Pies en los sliders y cuerpo extendido: flexionas las rodillas deslizando los "
                                          "pies hacia ti. La versión exigente del curl en casa."),
    "calf_raise__bilateral__machine": ("Máquina de pantorrillas con ambas piernas: subes y bajas con la espalda apoyada. "
                                       "La forma más popular de trabajar las pantorrillas."),
    "calf_raise__unilateral__machine": ("Máquina con una pierna: cada pantorrilla trabaja sola y corriges desequilibrios. "
                                        "La versión fina de la elevación de talones."),
    "calf_raise__bilateral__barbell": ("Barra en la espalda con ambas piernas: subes a la punta de los pies con la carga. "
                                       "El clásico de pantorrillas del gimnasio."),
    "calf_raise__unilateral__barbell": ("Barra en una pierna: la pantorrilla de un lado hace todo el trabajo. "
                                        "Avanzado y muy efectivo para igualar gemelos."),
    "calf_raise__bilateral__smith_machine": ("Smith con ambas piernas: la barra guiada sobre los hombros y subes a la punta "
                                             "de los pies. Cómoda y segura para cargar peso."),
    "calf_raise__unilateral__smith_machine": ("Smith con una pierna: un gemelo trabaja solo con la barra estable. "
                                              "Perfecta para pantorrillas parejas."),
    "calf_raise__bilateral__cable": ("Polea con ambas piernas: la tensión constante sube los talones con la pantorrilla "
                                     "cargada. Variante fina y suave con la espalda."),
    "calf_raise__unilateral__cable": ("Polea con una pierna: tensión continua en una pantorrilla a la vez. "
                                      "Ideal para un trabajo fino y equilibrado."),
    "calves_tibial_anterior__default": ("Con banda o disco sobre los pies: llevas la punta hacia arriba contra resistencia. "
                                        "La espinilla trabaja a fondo para tobillos fuertes."),
    "quads_reverse_nordic_peso_corporal__default": ("De rodillas, te inclinas hacia atrás frenando con los cuádriceps y "
                                                    "vuelves. El reverse nordic: intenso, raro y muy efectivo."),
    "hams_curl_nordic_peso_corporal__default": ("De rodillas con los tobillos sujetos, bajas el cuerpo al frente frenando "
                                                "con los isquios. El rey de la parte trasera de la pierna."),
    "forearms_suspension_isometrica_barra_fija__default": ("Cuélgate de la barra y aguanta el mayor tiempo posible. "
                                                           "Sin florituras: agarre, antebrazos y resistencia pura."),
    "forearms_paseo_del_granjero__dumbbells": ("Mancuernas pesadas en cada mano y a caminar: aguanta el paseo sin soltar. "
                                               "El paseo del granjero clásico con mancuernas."),
    "forearms_paseo_del_granjero__kettlebell": ("Kettlebells en cada mano y a caminar: el agarre sufre un poco más. "
                                                "Variante exigente del paseo del granjero."),
    "forearms_paseo_del_granjero__plate": ("Discos en cada mano y a caminar: los discos lisos ponen el agarre a prueba. "
                                           "El paseo que más trabaja los dedos."),
    "forearms_paseo_del_granjero__hex_bar": ("Barra hexagonal cargada y a caminar: la barra en el centro reparte el peso. "
                                             "El paseo más cómodo para cargas muy pesadas."),
    # ---- Cuello (configuraciones) ----
    "neck_extension_cuello__cable": ("Arnés o polea en la cabeza: llevas la cabeza atrás contra la tensión. "
                                     "El cuello trabaja con resistencia constante."),
    "neck_extension_cuello__plate": ("Disco en la nuca: subes la cabeza contra el peso del disco. "
                                     "La forma simple y clásica de fortalecer el cuello."),
    "neck_flexion_cuello__cable": ("Arnés o polea: llevas la cabeza hacia delante contra la tensión. "
                                   "El trabajo frontal del cuello con resistencia constante."),
    "neck_flexion_cuello__plate": ("Disco en la frente: flexionas la cabeza contra el peso. "
                                   "La versión clásica para la parte frontal del cuello."),
    "neck_flexion_lateral_cuello__default": ("De pie o acostado, inclinas la cabeza hacia el lado contra resistencia. "
                                             "Los laterales del cuello trabajan para un cuello completo."),
    # ---- Espalda baja (configuraciones) ----
    "back_extension_lumbar__default": ("En el banco de hiperextensiones: bajas el torso y subes con la espalda trabajando. "
                                       "El ejercicio base para una espalda fuerte."),
    "back_superman_suelo__default": ("Boca abajo en el suelo: levantas brazos y piernas a la vez y aguantas un momento. "
                                     "Espalda baja y core trabajando sin máquinas."),
    "back_hiperextension_45_zercher_espalda_baja__default": ("Carga en los codos en el banco a 45: subes el torso con la "
                                                             "espalda baja haciendo el trabajo. Exigente y controlada."),
    "back_jefferson_curl__barbell": ("Barra en las manos y espalda redondeando vértebra a vértebra: baja lento y sube "
                                     "lento. El Jefferson curl clásico para movilidad y fuerza."),
    "back_jefferson_curl__dumbbells": ("Mancuernas en las manos: el peso libre acompaña la curvatura de la espalda. "
                                       "Versión accesible del Jefferson curl."),
    "back_jefferson_curl__smith_machine": ("Smith: la barra guiada desciende con la espalda redondeada con seguridad. "
                                           "Una forma controlada de explorar el movimiento."),
    "back_jefferson_curl__cable": ("Polea baja: la tensión constante acompaña el redondeo de la espalda. "
                                   "Variante suave del Jefferson curl."),
    # ---- Muñeca y antebrazo (configuraciones) ----
    "forearms_curl_muneca_sentado__dumbbells": ("Mancuernas con los antebrazos apoyados: subes la muñeca con la palma "
                                                "arriba. La versión más cómoda y popular del curl de muñeca."),
    "forearms_curl_muneca_sentado__barbell": ("Barra con los antebrazos apoyados: las dos muñecas suben juntas contra la "
                                              "barra. El curl de muñeca clásico del gimnasio."),
    "forearms_curl_muneca_sentado__ez_bar": ("Barra EZ con los antebrazos apoyados: el agarre girado alivia la muñeca y "
                                             "el trabajo permanece total. La opción cómoda."),
    "forearms_curl_muneca_sentado__cable": ("Polea con los antebrazos apoyados: la tensión constante mantiene la muñeca "
                                            "cargada en todo el recorrido. Variante fina y efectiva."),
    "forearms_curl_muneca_inverso_sentado__cable": ("Polea con las palmas abajo: subes la mano contra la tensión del cable. "
                                                    "La extensión de muñeca con resistencia constante."),
    "forearms_curl_muneca_inverso_sentado__dumbbells": ("Mancuernas con las palmas abajo: subes la muñeca contra el peso. "
                                                        "La versión cómoda y popular de la extensión de muñeca."),
    "forearms_curl_muneca_inverso_sentado__barbell": ("Barra con las palmas abajo: las dos muñecas suben juntas contra la "
                                                      "barra. El clásico para el antebrazo posterior."),
    "forearms_curl_muneca_inverso_sentado__ez_bar": ("Barra EZ con las palmas abajo: agarre cómodo y trabajo directo del "
                                                     "antebrazo. La opción amable con las muñecas."),
    "forearms_curl_muneca_de_pie_tras_espalda_barra__default": ("Barra tras la espalda con los antebrazos extendidos: "
                                                                "flexionas la muñeca contra la barra. Estiramiento y "
                                                                "trabajo profundo del antebrazo."),
    "forearms_enrollamiento_muneca_rodillo__default": ("Rodillo con cuerda y peso: enrollas la cuerda girando las muñecas "
                                                       "hasta arriba y bajas lento. El ejercicio de la vieja escuela."),
    "forearms_pinza_de_discos__default": ("Dos discos lisos entre los dedos y el pulgar: aguanta el mayor tiempo posible. "
                                          "La fuerza de agarre más pura que existe."),
    # ---- Hombros (configuraciones) ----
    "military_press__barbell": ("Barra desde los hombros hasta arriba: el press militar clásico de pie. "
                                "El ejercicio rey de la fuerza de hombro."),
    "military_press__dumbbells": ("Mancuernas desde los hombros: cada brazo trabaja por separado y el recorrido es más "
                                  "natural. La versión equilibrada del press militar."),
    "military_press__smith_machine": ("Smith desde los hombros: la barra guiada te deja empujar sin preocuparte por el "
                                      "equilibrio. Segura para cargar peso de pie."),
    "military_press__machine": ("Máquina de press militar: el recorrido fijo aísla los hombros por completo. "
                                "La opción cómoda para meter volumen de hombro."),
    "military_press__cable": ("Polea desde los hombros: la tensión constante acompaña todo el empuje. "
                              "Variante fina que mantiene el hombro cargado."),
    "military_press__kettlebell": ("Kettlebell desde los hombros: el empuje se vuelve más inestable y el core trabaja. "
                                   "Variante exigente para hombros y estabilidad."),
    "seated_shoulder_press__barbell": ("Barra sentado con respaldo: el torso fijo y los hombros haciendo todo el empuje. "
                                       "El press sentado clásico con barra."),
    "seated_shoulder_press__dumbbells": ("Mancuernas sentado: el respaldo fija el torso y cada brazo trabaja solo. "
                                         "La versión más popular del press de hombros."),
    "seated_shoulder_press__smith_machine": ("Smith sentado: barra guiada y respaldo firme, aislar el hombro es sencillo. "
                                             "Cómoda para progresar con peso."),
    "seated_shoulder_press__machine": ("Máquina sentada: el guiado lleva el trabajo directo al hombro. "
                                       "La opción más segura y cómoda del press sentado."),
    "seated_shoulder_press__cable": ("Polea sentado: la tensión constante mantiene el hombro activo en todo el empuje. "
                                     "Variante fina para terminar el día de hombro."),
    "seated_shoulder_press__kettlebell": ("Kettlebell sentado: el empuje inestable suma trabajo al core. "
                                          "Variante exigente y muy completa."),
    "arnold_press__dumbbells": ("Mancuernas con el giro de Arnold: subes girando las palmas hasta arriba. "
                                "El press completo del hombro, directo del legendario."),
    "arnold_press__kettlebell": ("Kettlebells con el giro de Arnold: el movimiento rotatorio con campanas exige más "
                                 "estabilidad. Variante avanzada del press de Arnold."),
    "arnold_press__cable": ("Polea con el giro de Arnold: la tensión constante acompaña el giro y el empuje. "
                            "La versión pulida del press de Arnold."),
    "z_press__barbell": ("Barra sentado en el suelo: sin respaldo, el core aguanta y los hombros empujan. "
                         "El press Z más puro, exigente y revelador."),
    "z_press__ez_bar": ("Barra EZ sentado en el suelo: agarre cómodo y misma exigencia de postura. "
                        "La opción amable del press Z."),
    "z_press__dumbbells": ("Mancuernas sentado en el suelo: cada brazo trabaja solo con el torso libre. "
                           "El press Z con el mejor equilibrio entre brazos."),
    "z_press__kettlebell": ("Kettlebell sentado en el suelo: el empuje inestable suma trabajo al core. "
                            "La variante más exigente del press Z."),
    "standing_lateral_raise__dumbbells": ("Mancuernas a los costados: subes hasta la altura del hombro con los brazos "
                                          "ligeramente abiertos. La elevación lateral clásica y más popular."),
    "standing_lateral_raise__cable": ("Polea lateral: la tensión constante mantiene el hombro cargado en todo el arco. "
                                      "La versión fina que no deja descansar al deltoides."),
    "standing_lateral_raise__machine": ("Máquina de elevaciones: el recorrido guiado aísla el deltoides lateral. "
                                        "Cómoda y efectiva para rematar el hombro."),
    "standing_lateral_raise__kettlebell": ("Kettlebell a los costados: el agarre suma trabajo y el movimiento se siente "
                                           "distinto. Variante original de la elevación lateral."),
    "seated_lateral_raise__dumbbells": ("Mancuernas sentado: el torso fijo elimina el impulso y el hombro trabaja limpio. "
                                        "La elevación estricta, la favorita de los que buscan detalle."),
    "seated_lateral_raise__cable": ("Polea sentado: tensión constante y sin impulso posible. "
                                    "El hombro lateral arde con la versión pulida."),
    "seated_lateral_raise__machine": ("Máquina sentado: el guiado lleva el trabajo directo al deltoides lateral. "
                                      "La opción más cómoda del press lateral sentado."),
    "seated_lateral_raise__kettlebell": ("Kettlebell sentado: el agarre suma estabilidad extra al movimiento estricto. "
                                         "Variante exigente del press lateral sentado."),
    "lateral_raise_super_rom__cable": ("Polea con recorrido completo: subes más allá del hombro, por encima de la cabeza. "
                                       "El deltoides lateral trabaja en su rango máximo."),
    "lateral_raise_super_rom__dumbbells": ("Mancuernas con recorrido completo: subes por encima de la cabeza en cada "
                                           "repetición. La versión clásica de la Super ROM."),
    "lateral_raise_super_rom__machine": ("Máquina con recorrido completo: el guiado acompaña el arco hasta arriba. "
                                         "La forma cómoda de llevar el hombro al límite."),
    "rear_delt_raise__dumbbells": ("Mancuernas inclinado: abres los brazos hacia los lados con el torso paralelo al suelo. "
                                   "El deltoides posterior trabajando a fondo."),
    "rear_delt_raise__cable": ("Polea posterior: la tensión constante abre los brazos y el hombro trasero queda cargado. "
                               "La versión fina de las elevaciones posteriores."),
    "rear_delt_raise__machine": ("Máquina de deltoides posterior: el guiado aísla la espalda alta del hombro. "
                                 "Cómoda y muy efectiva para completar el hombro."),
    "deltoides_elevaciones_frontales__cable": ("Polea al frente: subes los brazos contra la tensión constante. "
                                               "El deltoides anterior cargado en todo el arco."),
    "deltoides_elevaciones_frontales__barbell": ("Barra al frente: subes la barra hasta la altura del hombro. "
                                                 "La elevación frontal clásica, con ambas manos a la vez."),
    "deltoides_elevaciones_frontales__dumbbells": ("Mancuernas al frente: subes cada brazo o ambos a la altura del hombro. "
                                                   "La versión más popular de la elevación frontal."),
    "deltoides_elevaciones_frontales__kettlebell": ("Kettlebell al frente: el agarre suma trabajo y el hombro empuja. "
                                                    "Variante original de la elevación frontal."),
    "deltoides_remo_menton__default": ("Barra pegada al cuerpo hasta la barbilla: trapecios y hombros trabajando juntos. "
                                       "Clásico de los hombros, con el agarre medio para cuidar la articulación."),
    "deltoides_y_raises_sentado_banco_inclinado__default": ("Boca abajo en el banco inclinado, subes los brazos en forma "
                                                            "de Y. La espalda alta y el hombro trabajan con mucha "
                                                            "elegancia."),
    "deltoides_lu_raises__default": ("Brazos en L y luego en U mientras subes: el manguito rotador y el hombro en "
                                     "movimiento. Un ejercicio poco común que cuida y fortalece el hombro."),
    "deltoides_face_pull__default": ("Cuerda a la altura de la cara: tiras abriendo los codos hacia los lados. "
                                     "El ejercicio que cuida tus hombros mientras fortalece la espalda alta."),
    "deltoides_press_landmine_unilateral__default": ("Barra anclada al suelo y empuje en diagonal con un brazo. "
                                                     "El hombro trabaja con el torso estable y sin forzar."),
    "deltoides_push_press__default": ("Impulso de piernas y empuje de hombros: la barra vuela hasta arriba. "
                                      "Potencia de todo el cuerpo en un movimiento explosivo."),
    # ---- Tríceps (configuraciones) ----
    "triceps_pushdown__bilateral__cable": ("Polea alta con ambas manos: estiras los brazos hacia abajo con los codos "
                                           "pegados. El ejercicio de tríceps más popular y efectivo."),
    "triceps_pushdown__unilateral__cable": ("Polea alta con una mano: cada tríceps trabaja por separado con tensión "
                                            "constante. Ideal para igualar ambos brazos."),
    "triceps_pushdown__bilateral__machine": ("Máquina de tríceps con ambas manos: la palanca baja guiada y el tríceps "
                                             "aislado. La opción más cómoda del pushdown."),
    "triceps_pushdown__unilateral__machine": ("Máquina con una mano: un tríceps a la vez con el guiado de la palanca. "
                                              "Perfecto para corregir desequilibrios."),
    "triceps_pushdown__bilateral__band": ("Banda anclada arriba con ambas manos: la resistencia crece al estirar. "
                                          "Tríceps cargado en cualquier sitio con una banda."),
    "triceps_pushdown__unilateral__band": ("Banda con una mano: tensión progresiva en un tríceps a la vez. "
                                           "Variante sencilla y muy efectiva para rematar."),
    "overhead_triceps__barbell": ("Barra detrás de la cabeza: bajas y subes con los codos apuntando arriba. "
                                  "El press francés overhead clásico con barra."),
    "overhead_triceps__machine": ("Máquina overhead: el guiado acompaña el recorrido detrás de la cabeza. "
                                  "La opción cómoda y segura del tríceps overhead."),
    "overhead_triceps__dumbbells": ("Mancuerna o mancuernas detrás de la cabeza: el estiramiento del tríceps es enorme. "
                                    "La versión favorita del overhead para la mayoría."),
    "overhead_triceps__cable": ("Polea overhead: la tensión constante estira y carga el tríceps en todo el recorrido. "
                                "La versión pulida del tríceps detrás de la cabeza."),
    "crossbody_triceps__cable__bilateral": ("Polea alta cruzando el cuerpo con ambas manos: tiras en diagonal hacia el "
                                            "lado contrario. El tríceps trabaja con un ángulo fresco."),
    "crossbody_triceps__cable__unilateral": ("Polea alta con una mano cruzando el cuerpo: aislamiento total con tensión "
                                             "constante. Perfecto para centrarse en un brazo."),
    "triceps_patada__dumbbells__bilateral": ("Mancuernas inclinado con ambas manos: estiras los brazos atrás con el codo "
                                             "fijo. La patada de tríceps clásica."),
    "triceps_patada__cable__bilateral": ("Polea inclinado con ambas manos: la tensión constante acompaña la patada. "
                                         "La versión fina que no deja descansar al tríceps."),
    "triceps_patada__dumbbells__unilateral": ("Mancuerna con una mano apoyado en el banco: un tríceps a la vez con el "
                                              "codo firme. Ideal para igualar ambos brazos."),
    "triceps_press_frances__dumbbells": ("Mancuernas acostado: bajas hacia la frente flexionando los codos. "
                                         "El francés más equilibrado y cómodo."),
    "triceps_press_frances__barbell": ("Barra acostado: bajas la barra hacia la frente y estiras. "
                                       "El press francés clásico con carga completa."),
    "triceps_press_frances__ez_bar": ("Barra EZ acostado: el agarre girado cuida los codos y las muñecas. "
                                      "La variante más popular del press francés."),
    "triceps_press_frances__cable": ("Polea acostado: la tensión constante estira el tríceps en todo el recorrido. "
                                     "La versión fina del francés."),
    "triceps_press_frances__kettlebell": ("Kettlebell acostado: la campana baja hacia la frente con un agarre distinto. "
                                          "Variante original y exigente."),
    "jm_press__barbell": ("Barra bajando hasta el mentón con codos pegados: el JM press original, cargado de tríceps. "
                          "El híbrido de press y francés que construye fuerza."),
    "jm_press__ez_bar": ("Barra EZ bajando al mentón: el agarre girado cuida los codos con el mismo estímulo. "
                         "La variante cómoda del JM press."),
    "jm_press__dumbbells": ("Mancuernas bajando al mentón: cada brazo trabaja por separado. "
                            "La versión equilibrada del JM press."),
    "jm_press__smith_machine": ("Smith bajando al mentón: la barra guiada te permite cargar peso con seguridad. "
                                "El JM press con el recorrido estable."),
    "jm_press__cable": ("Polea bajando al mentón: la tensión constante acompaña el movimiento. "
                        "La versión fina del JM press."),
    "california_press__barbell": ("Barra bajando al pecho con codos hacia dentro: el press California clásico. "
                                  "El pecho y el tríceps trabajan juntos."),
    "california_press__ez_bar": ("Barra EZ con codos hacia dentro: agarre cómodo y mismo trabajo de pecho y tríceps. "
                                 "La variante amable del California."),
    "california_press__dumbbells": ("Mancuernas con codos hacia dentro: cada brazo trabaja solo. "
                                    "La versión equilibrada del press California."),
    "tate_press__dumbbells": ("Mancuernas acostado juntando los codos: bajas y subes apretando las pesas. "
                              "El Tate press clásico, con el tríceps aislado."),
    "tate_press__cable": ("Polea acostado juntando los codos: la tensión constante hace el movimiento más exigente. "
                          "La versión fina del Tate press."),
    "katana_extension__cable__bilateral": ("Polea a un lado con ambas manos: tiras en diagonal abriendo como una katana. "
                                           "El tríceps se enciende desde un ángulo moderno."),
    "katana_extension__cable__unilateral": ("Polea con una mano en diagonal: cada tríceps trabaja por separado. "
                                            "Ideal para centrarse en un brazo a la vez."),
    "katana_extension__band__bilateral": ("Banda anclada a un lado con ambas manos: la resistencia crece al estirar en "
                                          "diagonal. La versión portátil de la katana."),
    "triceps_extension__default": ("Colgado del TRX con los codos extendiendo: tu peso carga el tríceps y el core "
                                   "aguanta el cuerpo. Ideal para casa o viaje."),
    "triceps_extension_pjr_mancuerna__default": ("Mancuerna bajando hacia la cadera con un brazo: el tríceps se estira y "
                                                 "se aísla a la vez. La variante que los entendidos usan."),
    "triceps_flexiones_esfinge__default": ("Flexiones con los codos pegados y apuntando atrás: el tríceps hace el trabajo "
                                           "completo. Pura fuerza con el peso corporal."),
    "triceps_fondos_entre_bancos__default": ("Manos en el banco y cuerpo bajando: el tríceps sube y baja todo el peso. "
                                             "Fácil de escalar y muy exigente."),
    "triceps_press_maquina__default": ("Máquina de tríceps: empujas la palanca con el recorrido guiado. "
                                       "La forma más cómoda de aislar el tríceps."),
    "triceps_rolling_extension__default": ("Barra que rueda por los muslos mientras extiendes: una variante rara con una "
                                           "sensación fresca. El tríceps trabaja con recorrido completo."),
    # ---- Espalda, jalones y empujes (configuraciones) ----
    "lat_pulldown__bilateral__cable": ("Polea alta con ambas manos: tiras de la barra hacia el pecho. "
                                       "El jalón clásico que construye dorsales."),
    "lat_pulldown__unilateral__cable": ("Polea alta con una mano: cada dorsal trabaja por separado con tensión constante. "
                                        "Ideal para corregir desequilibrios."),
    "lat_pulldown__bilateral__machine": ("Máquina de jalón con ambas manos: el guiado hace el recorrido más estable. "
                                         "La opción cómoda para cargar espalda."),
    "lat_pulldown__unilateral__machine": ("Máquina con una mano: un dorsal a la vez con el guiado. "
                                          "Perfecto para igualar ambos lados."),
    "lat_pulldown__bilateral__band": ("Banda anclada arriba con ambas manos: la resistencia crece al estirar. "
                                      "Jalón portable para casa o parque."),
    "lat_pulldown__unilateral__band": ("Banda con una mano: tensión progresiva en un dorsal a la vez. "
                                       "Variante sencilla y muy efectiva."),
    "pull_up__pronated__wide": ("Agarre amplio y palmas al frente: la dominada clásica, con la espalda alta tomando "
                                "protagonismo. La favorita para la espalda ancha."),
    "pull_up__pronated__medium": ("Agarre medio y palmas al frente: el punto equilibrado entre fuerza y espalda. "
                                  "La dominada de referencia para la mayoría."),
    "pull_up__pronated__close": ("Agarre cerrado y palmas al frente: el dorsal se estira más con los codos bajos. "
                                 "La variante profunda para la espalda media."),
    "pull_up__supinated__wide": ("Agarre amplio y palmas hacia ti: el bíceps ayuda más con la palma supina. "
                                 "Chin-up amplio que combina espalda y brazo."),
    "pull_up__supinated__medium": ("Agarre medio y palmas hacia ti: el bíceps trabaja de más y la subida se siente más "
                                   "fuerte. La dominada favorita de muchos."),
    "pull_up__supinated__close": ("Agarre cerrado y palmas hacia ti: la chin-up por excelencia, con el bíceps cargado. "
                                  "La mejor vía para progresar hacia la dominada."),
    "pull_up__neutral__wide": ("Agarre amplio con palmas enfrentadas: el punto medio cómodo para el hombro. "
                               "Variante estable de la dominada amplia."),
    "pull_up__neutral__medium": ("Agarre medio con palmas enfrentadas: equilibrio entre espalda y brazo con hombros "
                                 "cómodos. La versión más amable de la dominada."),
    "pull_up__neutral__close": ("Agarre cerrado con palmas enfrentadas: el dorsal trabaja con mucho recorrido. "
                                "Variante profunda y cómoda para el hombro."),
    "push_up__flat": ("Flexiones con el cuerpo recto y las manos al ancho del pecho: el clásico completo. "
                      "Pecho, tríceps y core trabajando con tu propio peso."),
    "push_up__feet_elevated": ("Flexiones con los pies elevados: el ángulo sube el foco a la parte alta del pecho. "
                               "La variante que intensifica el clásico sin pesas."),
    "tren_superior_cruce_poleas__high": ("Polea alta: el cruce desde arriba enfoca la parte baja del pecho. "
                                         "La altura que dibuja el pectoral inferior."),
    "tren_superior_cruce_poleas__mid": ("Polea media: el cruce desde el centro reparte el trabajo por todo el pecho. "
                                        "La altura equilibrada y más popular."),
    "tren_superior_cruce_poleas__low": ("Polea baja: el cruce desde abajo enfoca la parte alta del pecho. "
                                        "La altura que esculpe la zona superior."),
    "tren_superior_fondos__default": ("Entre paralelas, bajas flexionando los codos hasta donde puedas y subes. "
                                      "Pecho y tríceps con el peso corporal: intensos de verdad."),
    "tren_superior_press_banca_cadenas__default": ("Cadenas colgando de la barra: la resistencia crece al subir y se "
                                                   "aligera al bajar. La tensión perfecta para el press."),
    "tren_superior_press_banda_resistencia__default": ("Bandas atadas a la barra: tensión extra arriba, justo donde "
                                                       "empujas más. El press con resistencia variable."),
    "tren_superior_press_pecho_maquina_convergente__default": ("Máquina convergente: los brazos se juntan siguiendo el "
                                                               "arco del pecho. Cómoda, segura y efectiva."),
    "tren_superior_press_inclinado_maquina_convergente__default": ("Máquina convergente inclinada: el ángulo sube el foco "
                                                                   "a la parte alta del pecho. La versión fina del "
                                                                   "press en máquina."),
    "tren_superior_press_spoto_barra__default": ("Pausa corta a unos centímetros del pecho en cada repetición: sin rebote "
                                                 "y con el punto débil trabajado. El press para romper estancamientos."),
    "tren_superior_press_unilateral_polea__default": ("Una mano contra la polea baja: el pecho trabaja lado a lado y "
                                                      "corriges desequilibrios. Press unilateral con tensión constante."),
    "tren_superior_squeeze_press_mancuernas__default": ("Mancuernas apretadas entre sí mientras empujas: la contracción "
                                                        "del pecho es máxima. Un press con sensación única."),
    "back_band_pull_apart__default": ("Banda al frente y separación hacia el pecho: la espalda alta se activa al instante. "
                                      "El calentamiento perfecto de espalda y hombros."),
    "back_remo_banda__default": ("Banda anclada y tirón hacia el torso: el remo que cabe en cualquier mochila. "
                                 "Espalda trabajando en casa o en el parque."),
    "back_remo_gorilla_mancuernas__dumbbells": ("Mancuernas colgando y tirón con balanceo del torso: el remo gorila "
                                                "clásico. Fuerza bruta de espalda con mancuernas."),
    "back_remo_gorilla_mancuernas__kettlebell": ("Kettlebells colgando y tirón con balanceo: el agarre suma trabajo al "
                                                 "remo gorila. Variante completa para espalda y antebrazos."),
    "back_remo_gorilla_mancuernas__cable": ("Polea y tirón con balanceo: la tensión constante cambia el remo gorila. "
                                            "La versión fina del remo de la fuerza bruta."),
    "back_remo_invertido__default": ("Colgado bajo la barra con el cuerpo recto, acercas el pecho a ella. "
                                     "La dominada horizontal: espalda con tu propio peso y fácil de progresar."),
    "back_remo_renegado_mancuernas__dumbbells": ("Mancuernas en plancha y remo de un brazo: el core combate el giro. "
                                                 "Espalda y abdomen en un mismo ejercicio."),
    "back_remo_renegado_mancuernas__kettlebell": ("Kettlebells en plancha y remo de un brazo: la campana baja el centro de "
                                                  "gravedad y exige más. La variante avanzada del renegado."),
    "back_encogimientos__dumbbells": ("Mancuernas a los costados y hombros hacia arriba: el encogimiento clásico. "
                                      "La forma más popular de trabajar los trapecios."),
    "back_encogimientos__smith_machine": ("Smith y hombros hacia arriba: la barra guiada te permite cargar pesado. "
                                          "Cómoda para encogimientos con mucha carga."),
    "back_encogimientos__cable": ("Polea y hombros hacia arriba: la tensión constante mantiene el trapecio cargado. "
                                  "La versión fina del encogimiento."),
    "back_encogimientos__kettlebell": ("Kettlebells y hombros hacia arriba: el agarre suma trabajo al movimiento. "
                                       "Variante completa de los encogimientos."),
    "back_encogimientos__barbell": ("Barra al frente y hombros hacia arriba: el encogimiento clásico con barra. "
                                    "La opción de los que buscan cargar peso de verdad."),
    "back_encogimientos_kelso__barbell": ("Barra por detrás del cuerpo: el Kelso shrug clásico con los brazos atrás. "
                                          "El trapecio trabaja desde un ángulo distinto."),
    "back_encogimientos_kelso__machine": ("Máquina con los brazos detrás: el guiado hace el recorrido estable. "
                                          "La opción cómoda del Kelso shrug."),
    "back_encogimientos_kelso__cable": ("Polea por detrás: la tensión constante acompaña el encogimiento trasero. "
                                        "La versión fina del Kelso."),
    "back_encogimientos_kelso__kettlebell": ("Kettlebells por detrás: el agarre suma trabajo y el trapecio se enciende. "
                                             "Variante completa del Kelso shrug."),
    "back_encogimientos_kelso__smith_machine": ("Smith por detrás: la barra guiada con los brazos atrás y mucha carga. "
                                                "Segura y muy efectiva para el trapecio."),
    "back_y_raises__default": ("Subes los brazos en forma de Y desde el suelo: la espalda alta y el hombro trabajan con "
                               "elegancia. Un ejercicio fino para la postura."),
    "back_dominadas_escapulares__default": ("Colgado de la barra moviendo solo los omóplatos: la espalda se activa sin "
                                            "doblarte. El ejercicio de preparación que todo el mundo debería hacer."),
    "lying_pullover__dumbbells": ("Mancuerna acostado llevándola detrás de la cabeza: el dorsal se estira a fondo. "
                                  "La versión más popular del pullover."),
    "lying_pullover__barbell": ("Barra acostado detrás de la cabeza: el arco largo con carga completa. "
                                "El pullover clásico con barra."),
    "lying_pullover__kettlebell": ("Kettlebell acostado detrás de la cabeza: la campana se siente distinta en el arco. "
                                   "Variante original del pullover."),
    "lying_pullover__cable": ("Polea acostado: la tensión constante estira el dorsal en todo el recorrido. "
                              "La versión fina del pullover."),
    "lying_pullover__machine": ("Máquina de pullover: el guiado acompaña el arco detrás de la cabeza. "
                                "La opción cómoda y segura del ejercicio."),
    "pullover__bilateral__cable": ("Polea alta con ambas manos: el arco largo deja el dorsal estirado a fondo. "
                                   "El pullover de polea clásico."),
    "pullover__unilateral__cable": ("Polea con una mano: cada dorsal trabaja por separado con tensión constante. "
                                    "Ideal para corregir desequilibrios."),
    "pullover__bilateral__machine": ("Máquina con ambas manos: el guiado hace el arco estable y seguro. "
                                     "La opción cómoda del pullover."),
    "pullover__unilateral__machine": ("Máquina con una mano: un dorsal a la vez con el guiado. "
                                      "Perfecto para centrarse en cada lado."),
    # ---- Faltantes: déficits y especialidades ----
    "hams_peso_muerto_rumano_deficit__default": ("Desde la plataforma, la barra baja por los muslos con más recorrido. "
                                                 "El rumano con estiramiento extra para los isquios."),
    "hams_peso_muerto_rumano_sumo_deficit__default": ("Postura sumo desde la plataforma: el rumano sumo con más recorrido "
                                                      "y los glúteos trabajando de extra."),
    "glutes_patada_gluteo_polea_diagonal__default": ("Polea baja con el cable cruzando el cuerpo: la patada viaja en "
                                                     "diagonal con tensión constante. Un ángulo que sorprende al glúteo."),
    "quads_sentadilla_anderson__default": ("Desde sentado en el suelo sin rebote, te levantas con la barra. "
                                           "La sentadilla que elimina el impulso y construye fuerza pura."),
    "quads_sentadilla_anderson_frontal_barra_recta__default": ("Barra al frente y salida desde el suelo sin rebote: los "
                                                               "cuádriceps y el core exigen fuerza desde cero."),
    "quads_sentadilla_bazuca__default": ("Pies juntos y puntas al frente: el foco va al cuádriceps externo. "
                                         "La sentadilla que da forma a las piernas."),
    "quads_sentadilla_copa__default": ("Mancuerna o kettlebell al pecho como una copa: la sentadilla perfecta para "
                                       "aprender, con el core trabajando de extra."),
    "quads_sentadilla_hack_invertida_maquina__default": ("De espaldas a la hack: la carga queda al frente y el cuádriceps "
                                                         "bajo trabaja a fondo. Variante invertida y exigente."),
    "quads_sentadilla_somersault__default": ("La barra cruza los brazos por delante y bajas profundo: una variante rara "
                                             "que carga el torso y exige movilidad."),
    "quads_sentadilla_v_squat__default": ("Máquina V-Squat: el cuerpo en ángulo fijo y la carga guiada detrás. "
                                          "Cuádriceps profundo con la espalda descansando."),
    "quads_sentadilla_v_squat_invertida_maquina__default": ("V-Squat invertida: la posición cambia el ángulo y el estímulo "
                                                            "cae en el cuádriceps inferior."),
    "quads_sentadilla_zercher_barra_recta__default": ("Barra en los codos y sentadilla profunda: el core y los brazos "
                                                      "aguantan mientras los cuádriceps trabajan."),
    "quads_sentadilla_jefferson__default": ("De pie sobre la barra, un extremo al frente y otro atrás: bajas en diagonal. "
                                            "Un ejercicio raro, exigente y sorprendentemente bueno."),
    "quads_sentadilla_cajon__default": ("Bajas sentándote en el cajón y subes sin rebote: profundidad segura y fuerza de "
                                        "cuádriceps. El maestro de la sentadilla."),
    "quads_sentadilla_sumo_frontal__default": ("Barra al frente con la postura amplia de sumo: torso erguido y piernas "
                                               "trabajando juntas."),
    "quads_sentadilla_sumo_zercher__default": ("Barra en los codos con postura sumo: la combinación exigente que trabaja "
                                               "piernas y torso."),
    "quads_sentadilla_cosaca__default": ("Paso amplio al lado y bajada lateral con la otra pierna extendida: movilidad y "
                                         "fuerza de piernas en un movimiento elegante."),
    "quads_sentadilla_bulgara_somersault__default": ("Pie trasero en el banco y barra cruzando los brazos: la búlgara con "
                                                     "un extra de equilibrio. Avanzada y exigente."),
    "quads_sentadilla_pistola__default": ("En una sola pierna bajas hasta el suelo con la otra extendida: equilibrio, "
                                          "movilidad y fuerza. El ejercicio que lo demuestra todo."),
    "quads_sentadilla_pistola_asistida_trx__default": ("Sujetándote del TRX, bajas a una pierna con apoyo: la vía segura "
                                                       "para conquistar la pistola."),
    "quads_step_up_cajon_frontal__default": ("Subida al cajón con el peso al frente: el cuádriceps hace el trabajo y el "
                                             "glúteo acompaña."),
    "quads_step_up_cajon_zercher__default": ("Carga en los codos y subida al cajón: el core aguanta mientras la pierna "
                                             "sube con todo."),
    "quads_zancada_caminando_frontal_barra_recta__default": ("Pasos al frente con la barra en el pecho: piernas y torso "
                                                             "trabajando en movimiento."),
    "quads_zancada_caminando_zercher_barra_recta__default": ("Pasos al frente con la barra en los codos: la versión más "
                                                             "exigente de la zancada caminando."),
    "quads_zancada_frontal_zercher__default": ("Paso al frente con la carga en los codos: equilibrio, piernas y core en un "
                                               "mismo movimiento."),
    "quads_zancada_inversa_frontal__default": ("Paso atrás con la barra al frente del pecho: estable, amable con la rodilla "
                                               "y muy completa."),
    "quads_zancada_inversa_maquina_hack__default": ("Paso atrás con el guiado de la hack: la estabilidad de la máquina con "
                                                    "el trabajo de la zancada."),
    "quads_zancada_inversa_maquina_v_squat__default": ("Paso atrás con la plataforma V-Squat guiada: unilateral, profunda y "
                                                       "con la espalda protegida."),
    "quads_zancada_inversa_zercher__default": ("Paso atrás con la barra en los codos: la combinación exigente que trabaja "
                                               "piernas y torso."),
    "quads_sentadilla_bulgara_jefferson__default": ("Pie trasero en el banco y barra en posición Jefferson: dos ejercicios "
                                                    "duros combinados en uno."),
    "bulgarian_zercher__barbell__zercher": ("Barra en los codos con el pie trasero en el banco: la búlgara con el torso "
                                            "trabajando de extra. Exigente y muy completa."),
}

# Normalización de ids escritos con el orden de ejes equivocado: el catálogo
# ordena laterality antes que implement y pulley_height antes que grip_width.
_ID_FIX = {
    "chest_supported_row__cable__wide__high": "chest_supported_row__cable__high__wide",
    "chest_supported_row__cable__wide__mid": "chest_supported_row__cable__mid__wide",
    "chest_supported_row__cable__wide__low": "chest_supported_row__cable__low__wide",
    "chest_supported_row__cable__medium__high": "chest_supported_row__cable__high__medium",
    "chest_supported_row__cable__medium__mid": "chest_supported_row__cable__mid__medium",
    "chest_supported_row__cable__medium__low": "chest_supported_row__cable__low__medium",
    "chest_supported_row__cable__close__high": "chest_supported_row__cable__high__close",
    "chest_supported_row__cable__close__mid": "chest_supported_row__cable__mid__close",
    "chest_supported_row__cable__close__low": "chest_supported_row__cable__low__close",
    "reverse_pec_fly__machine__bilateral": "reverse_pec_fly__bilateral__machine",
    "reverse_pec_fly__machine__unilateral": "reverse_pec_fly__unilateral__machine",
    "reverse_pec_fly__cable__bilateral": "reverse_pec_fly__bilateral__cable",
    "reverse_pec_fly__cable__unilateral": "reverse_pec_fly__unilateral__cable",
    "reverse_pec_fly__dumbbells__bilateral": "reverse_pec_fly__bilateral__dumbbells",
    "reverse_pec_fly__dumbbells__unilateral": "reverse_pec_fly__unilateral__dumbbells",
    "conventional_deadlift__barbell__bilateral": "conventional_deadlift__bilateral__barbell",
    "conventional_deadlift__barbell__unilateral": "conventional_deadlift__unilateral__barbell",
    "conventional_deadlift__smith_machine__bilateral": "conventional_deadlift__bilateral__smith_machine",
    "conventional_deadlift__smith_machine__unilateral": "conventional_deadlift__unilateral__smith_machine",
    "conventional_deadlift__dumbbells__bilateral": "conventional_deadlift__bilateral__dumbbells",
    "conventional_deadlift__dumbbells__unilateral": "conventional_deadlift__unilateral__dumbbells",
    "conventional_deadlift__hex_bar__bilateral": "conventional_deadlift__bilateral__hex_bar",
    "conventional_deadlift__hex_bar__unilateral": "conventional_deadlift__unilateral__hex_bar",
    "good_morning__barbell__bilateral": "good_morning__bilateral__barbell",
    "good_morning__barbell__unilateral": "good_morning__unilateral__barbell",
    "good_morning__safety_bar__bilateral": "good_morning__bilateral__safety_bar",
    "good_morning__safety_bar__unilateral": "good_morning__unilateral__safety_bar",
    "good_morning__smith_machine__bilateral": "good_morning__bilateral__smith_machine",
    "good_morning__smith_machine__unilateral": "good_morning__unilateral__smith_machine",
    "good_morning__machine__bilateral": "good_morning__bilateral__machine",
    "good_morning__machine__unilateral": "good_morning__unilateral__machine",
    "good_morning__cable__bilateral": "good_morning__bilateral__cable",
    "good_morning__cable__unilateral": "good_morning__unilateral__cable",
    "romanian_deadlift__barbell__bilateral": "romanian_deadlift__bilateral__barbell",
    "romanian_deadlift__barbell__unilateral": "romanian_deadlift__unilateral__barbell",
    "romanian_deadlift__smith_machine__bilateral": "romanian_deadlift__bilateral__smith_machine",
    "romanian_deadlift__smith_machine__unilateral": "romanian_deadlift__unilateral__smith_machine",
    "romanian_deadlift__dumbbells__bilateral": "romanian_deadlift__bilateral__dumbbells",
    "romanian_deadlift__dumbbells__unilateral": "romanian_deadlift__unilateral__dumbbells",
    "romanian_deadlift__hex_bar__bilateral": "romanian_deadlift__bilateral__hex_bar",
    "romanian_deadlift__hex_bar__unilateral": "romanian_deadlift__unilateral__hex_bar",
    "romanian_sumo_deadlift__barbell__bilateral": "romanian_sumo_deadlift__bilateral__barbell",
    "romanian_sumo_deadlift__barbell__unilateral": "romanian_sumo_deadlift__unilateral__barbell",
    "romanian_sumo_deadlift__smith_machine__bilateral": "romanian_sumo_deadlift__bilateral__smith_machine",
    "romanian_sumo_deadlift__smith_machine__unilateral": "romanian_sumo_deadlift__unilateral__smith_machine",
    "romanian_sumo_deadlift__dumbbells__bilateral": "romanian_sumo_deadlift__bilateral__dumbbells",
    "romanian_sumo_deadlift__dumbbells__unilateral": "romanian_sumo_deadlift__unilateral__dumbbells",
    "romanian_sumo_deadlift__hex_bar__bilateral": "romanian_sumo_deadlift__bilateral__hex_bar",
    "romanian_sumo_deadlift__hex_bar__unilateral": "romanian_sumo_deadlift__unilateral__hex_bar",
    "glutes_puente_gluteos__barbell__bilateral": "glutes_puente_gluteos__bilateral__barbell",
    "glutes_puente_gluteos__barbell__unilateral": "glutes_puente_gluteos__unilateral__barbell",
    "glutes_puente_gluteos__dumbbells__bilateral": "glutes_puente_gluteos__bilateral__dumbbells",
    "glutes_puente_gluteos__dumbbells__unilateral": "glutes_puente_gluteos__unilateral__dumbbells",
    "glutes_puente_gluteos__smith_machine__bilateral": "glutes_puente_gluteos__bilateral__smith_machine",
    "glutes_puente_gluteos__smith_machine__unilateral": "glutes_puente_gluteos__unilateral__smith_machine",
    "hip_thrust__barbell__bilateral": "hip_thrust__bilateral__barbell",
    "hip_thrust__barbell__unilateral": "hip_thrust__unilateral__barbell",
    "hip_thrust__smith_machine__bilateral": "hip_thrust__bilateral__smith_machine",
    "hip_thrust__smith_machine__unilateral": "hip_thrust__unilateral__smith_machine",
    "hip_thrust__machine__bilateral": "hip_thrust__bilateral__machine",
    "hip_thrust__machine__unilateral": "hip_thrust__unilateral__machine",
    "hip_thrust__band__bilateral": "hip_thrust__bilateral__band",
    "hip_thrust__band__unilateral": "hip_thrust__unilateral__band",
    "stiff_leg_deadlift__barbell__bilateral": "stiff_leg_deadlift__bilateral__barbell",
    "stiff_leg_deadlift__barbell__unilateral": "stiff_leg_deadlift__unilateral__barbell",
    "stiff_leg_deadlift__smith_machine__bilateral": "stiff_leg_deadlift__bilateral__smith_machine",
    "stiff_leg_deadlift__smith_machine__unilateral": "stiff_leg_deadlift__unilateral__smith_machine",
    "stiff_leg_deadlift__dumbbells__bilateral": "stiff_leg_deadlift__bilateral__dumbbells",
    "stiff_leg_deadlift__dumbbells__unilateral": "stiff_leg_deadlift__unilateral__dumbbells",
    "stiff_leg_deadlift__hex_bar__bilateral": "stiff_leg_deadlift__bilateral__hex_bar",
    "stiff_leg_deadlift__hex_bar__unilateral": "stiff_leg_deadlift__unilateral__hex_bar",
}

DEF_DESC.update({
    "hams_peso_muerto_rumano_deficit": ("Peso muerto rumano en déficit: subido a una plataforma, la barra baja por los "
                                        "muslos con más recorrido. El rumano clásico con un estiramiento extra de isquios."),
    "hams_peso_muerto_rumano_sumo_deficit": ("Peso muerto rumano sumo en déficit: la postura amplia desde la plataforma, "
                                             "con más recorrido y los glúteos trabajando de extra."),
})

CFG_DESC = {_ID_FIX.get(key, key): value for key, value in CFG_DESC.items()}
