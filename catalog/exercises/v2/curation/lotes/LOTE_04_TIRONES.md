# Lote 4: tirones. Informe para aprobación

Fecha: 2026-10-03 · 20 definiciones · 74 configuraciones · estado: **aplicado el
2026-10-03 con el OK del usuario.** Decisiones:
- se aprueban los 9 escalados de músculos principales;
- el remo en banda pasa a tener el dorsal como dominante y el trapecio como secundario;
- se aceptan las recomendaciones de las preguntas abiertas: las 4 variantes dudosas se
  quedan, los erectores son estabilizadores en los remos libres, el deltoides es secundario
  con agarre amplio, el Band Pull-Apart pasa a `horizontal_abduction`, la polea media del
  pecho apoyado lleva almohadilla casi vertical, no hay remo en banda sentado y el Kelso con
  mancuernas queda anotado para después.
SHA canónico tras el land: `49273e8e…`.

## Resumen

- Las 20 definiciones pasan de LEGACY a CURATED:
  - remos libres: convencional, Pendlay y Gorilla;
  - remos con apoyo o en máquina: pecho apoyado, barra T y Gironda;
  - tirones con peso corporal, banda y polea: remo invertido, renegado, remo en banda, Band
    Pull-Apart y Face Pull;
  - tracción vertical: jalón al pecho y dominada en rack;
  - los tres Pull Over: en banca, de pie en polea y sentado en máquina;
  - escápula: dominadas escapulares, encogimientos, Kelso Shrugs e Y-Raises.
- Lint conjunto: **0 errores y 0 avisos**, con el patrón nuevo del Kelso. 220 citas de 65 fuentes distintas, todas verificadas.
- Mismo proceso que los lotes 2 y 3, con 5 autores y 3 revisores limpios:
  - los revisores contrastaron las cifras con los resúmenes y, cuando hizo falta, con el
    texto completo;
  - una ronda de corrección cerró 8 errores y unas 70 mejoras.
- Patrones aprobados en el plan: el Kelso pasa al patrón nuevo `scapular_retraction` y los
  Y-Raises a `shoulder_abduction_diagonal`.
- `forearm_rotation` (pronación y supinación) se crea en el lote 11, junto con las fichas que
  lo usan. Un test exige que la ontología liste solo patrones que el catálogo usa.

## Errores graves corregidos

- **Los remos no cumplían su propia regla.** Romboides, deltoides, trapecio, dorsal y bíceps
  deben trabajar en todo remo. El remo con pecho apoyado tenía 30 fallos: bíceps y romboides
  como estabilizadores con agarre amplio, y sin trapecio con agarre cerrado. Al Gorilla, al
  renegado y a la barra T les faltaban músculos.
- **Face Pull y Band Pull-Apart:**
  - el hombro figuraba como «extensión y aducción», cuando es abducción horizontal (más
    rotación externa en el Face Pull);
  - el Face Pull tenía el dorsal como secundario.
- **Kelso Shrugs:**
  - era un «encogimiento» (elevación de la escápula) cuando es una retracción;
  - el texto describía la barra por detrás del cuerpo.
- **Y-Raises:** tenían el dorsal como principal y el bíceps y los erectores como secundarios.
  Los tres son incorrectos.
- **Dominadas escapulares:** el bíceps figuraba como secundario aunque el codo no se mueve, y
  faltaba el antebrazo, que sostiene el cuerpo colgado.
- **Dominada en rack:**
  - el bíceps era solo estabilizador;
  - faltaba el antebrazo;
  - la postura estaba mal descrita: el tronco va vertical, con los talones elevados.
- **Pull Over:** los tres tenían el codo como flexión, y el ángulo del codo no cambia.
- **Textos falsos:**
  - «la kettlebell baja el centro de gravedad» (renegado);
  - «mancuernas colgando y balanceo» (Gorilla);
  - «la máquina concentra todo el esfuerzo» (convencional);
  - el mito del Pull Over como «expansor de la caja torácica»;
  - el remo en banda descrito sentado cuando la imagen lo muestra de pie.
- **Plantillas y clichés vetados:**
  - «la trayectoria permanece estable», «exige respeto», «sin concesiones», «tensión viva»;
  - frases prohibidas como «vitamina» o «reorganizado».

## Para tu decisión: cambian los músculos principales en configuraciones que usa la app

El músculo dominante, el primero de la lista, no cambia en ninguna configuración. Lo que
cambia es el conjunto de principales, y cada principal cuenta una serie directa completa.

| Cambio | Configuraciones protegidas | Uso en la app | Recomiendo |
|---|---|---|---|
| Remos con agarre amplio: entra el trapecio como segundo principal | `chest_supported_row__dumbbells__wide`, `t_bar_row__t_bar__wide`, `t_bar_row__machine__wide` | 2 plantillas de powerlifting (`pl-sbd-1` 2×8, `pl-classic-1` 3×8), con 2 y 3 series directas de trapecio, lejos del tope; las otras dos, solo en el mapa de ids heredados y en un test del selector | Sí: L11 de la guía (agarre amplio = espalda alta) y EMG (Padovan 2025, Vasconcelos 2023) |
| Jalón al pecho: el trapecio pasa de principal a secundario | 4 de las 6 configuraciones del jalón | muchas plantillas; cada serie suma 0,5 de trapecio en vez de 1 | Sí, para igualarlo con las dominadas y el jalón con agarre cerrado; evidencia dividida (Buonsenso 2025 a favor, Lehman 2004 lo mide casi igual que el dorsal) |
| Dominadas escapulares: entra el trapecio | `back_dominadas_escapulares__default` | solo un test de imagen | Sí: el trapecio inferior es el que deprime la escápula |
| Y-Raises: sale el dorsal y entra el deltoides | `back_y_raises__default` | solo un test de imagen | Sí: el dorsal lo prohíbe la regla; el deltoides posterior y el medio pasan del 50 % |
| Remo en banda: el dorsal pasa a dominante y el trapecio a secundario (propuesta del revisor, aprobada y aplicada) | `back_remo_banda__default` | hueco de remo de los perfiles nativos, junto a remos que ya tienen el dorsal primero | Sí, por coherencia con el resto de remos; ningún estudio separa trapecio y dorsal en este gesto |

## Cambios de anatomía (volumen)

- **Antebrazo** entra como estabilizador casi en todo el lote, porque el agarre sostiene la
  carga o el cuerpo. En la dominada en rack y en las dominadas escapulares lo exige la regla.
- **Isquiosurales y glúteo mayor** entran como estabilizadores en las bisagras sin apoyo:
  convencional, Pendlay, Gorilla, barra T libre, Kelso con barra e Y-Raises. Datos de
  Fenwick 2009 (texto completo): bíceps femoral 12,5 % y glúteo 19 %. En el remo invertido
  son estabilizadores y los isquiosurales llegan al 25,7 %.
- **Erectores:**
  - salen del remo con pecho apoyado (criterio confirmado);
  - quedan estabilizadores en los remos libres, en el remo invertido (antes secundarios) y
    en el remo en banda (antes secundarios);
  - en los Y-Raises pasan de secundarios a estabilizadores.
- **Core** pasa a secundario en el renegado, por la antirrotación (García-Vaquero 2012; está
  en tus decisiones), y entra como estabilizador en el remo invertido, el jalón y la barra T
  libre.
- **Pull Over en máquina:** salen el core y la muñeca (L6), y el tríceps baja a
  estabilizador porque el codo va descargado.
- **Deltoides** entra como secundario en los tres Pull Over (Teixeira 2022 y Pezarat-Correia
  2020). En el de banca sube de estabilizador a secundario.
- Tabla completa por configuración en `LOTE_04_ANATOMIA.md`.

## Decisiones del coordinador (puedes revertirlas)

- **Face Pull:** el trapecio queda secundario, con el deltoides como único principal. Está en
  más de 20 plantillas y protocolos con 3 o 4 series de 15; como principal dispararía las
  series directas de trapecio.
- **Band Pull-Apart:** el deltoides queda secundario. El trapecio domina en el estudio que
  lo midió (Fukunaga 2022: trapecio medio 61,5 %, deltoides posterior 48,3 %).
- **Técnica elegida:**
  - el Gironda es el remo sentado en polea baja con el tronco quieto: es el uso hispano y
    coincide con la imagen; la marca de Gironda documenta otro, de pie e inclinado;
  - la dominada en rack es la versión DC: talones en el respaldo de un banco, tronco
    vertical y cadera por debajo de los pies;
  - el jalón en máquina es la de palancas con discos, no una estación de cable;
  - el jalón con banda se hace de rodillas;
  - los Y-Raises se hacen de pie con la cadera flexionada (la versión en banco inclinado es
    su hermana del lote 8);
  - el Kelso con barra se hace de pie e inclinado sin apoyo, y el de polea, sentado.
- **Fichas ajenas:**
  - en el remo foca, la cita de Fenwick 2009 decía «remo invertido con torso soportado»,
    cuando en el estudio se hace colgado; corregida;
  - en las dominadas, el prohibido de la imagen ya no llama remo invertido a apoyar los pies
    en un banco;
  - el jalón con agarre cerrado (alta de la sesión de programas) queda alineado con el
    jalón: mismos estabilizadores y la retracción escapular.
- **Reglas** (`anatomy_rules.json`), con las propuestas de la sesión de programas:
  - el press cerrado y el jalón cerrado entran en sus reglas de sinergistas;
  - herencias nuevas: la sentadilla con pausa hereda de la de barra alta y el press cerrado
    del press de banca;
  - `scapular_retraction` entra en la regla del codo inmóvil.
  Todo pasa la auditoría.

## Preguntas abiertas (con mi recomendación)

1. **Variantes dudosas.** Tu regla es que si existen y se pueden hacer, se quedan; los
   autores las describieron con honestidad. Las cuatro son:
   - el Pendlay en máquina (una palanca que vuelve a su tope) y en polea (la pila se apoya
     entre repeticiones);
   - el Gorilla en polea (pierde el apoyo en el suelo);
   - el remo convencional en máquina (una palanca de pie sin cojín).
   Recomiendo mantener las cuatro.
2. **Erectores en remos libres:** recomiendo dejarlos como estabilizadores, como en el peso
   muerto rumano y las dominadas (R7). Fenwick los mide altos, pero en isometría.
3. **Deltoides como principal con agarre amplio:** recomiendo que no. El id agrupa las tres
   porciones y sumaría series directas de hombro.
4. **Band Pull-Apart como `horizontal_abduction`:** es el patrón de las aperturas inversas y
   su anatomía ya lo cumple. Recomiendo que sí. El Face Pull se queda como tirón: en ese
   patrón perdería el bíceps, y su codo sí se dobla.
5. **Kelso con mancuernas,** la versión más habitual, que hoy no existe. Recomiendo
   anotarla para una pasada de variantes: es una configuración nueva, con su id, los
   conteos de los tests y su imagen. Así no alargo este lote.
6. **Remo con pecho apoyado en polea media:** la ficha pone el banco a 45°, que con el cable
   horizontal se parece a un remo alto; la imagen actual muestra una almohadilla casi
   vertical. Recomiendo la almohadilla casi vertical: es la máquina estándar y no hay que
   regenerar la imagen.
7. **Remo en banda sentado** con la banda en los pies, como configuración aparte: recomiendo
   que no por ahora.

## Cola de imágenes

- **Faltan 18 pares** (ejercicio por implemento):
  - remo con pecho apoyado con kettlebell;
  - remo convencional con polea, kettlebell, máquina y Smith;
  - Pendlay con polea, mancuernas, kettlebell y máquina;
  - barra T libre;
  - Gorilla en polea;
  - jalón con banda;
  - dominada en rack;
  - los cinco implementos del Kelso.
  Si apruebas el Kelso con mancuernas, se suma uno más.
- **Fallan 17 imágenes existentes:**
  - Remos libres (6): convencional con barra y con mancuernas, Pendlay con barra y en Smith,
    y Gorilla con mancuernas y con kettlebell. Todas muestran la salida y no el punto alto,
    con el torso demasiado erguido. El Gorilla, además, sale en zancada.
  - Remo con pecho apoyado (3):
    - con mancuernas, a medio tirón;
    - en máquina, una máquina inventada;
    - en polea media, tirando con una sola mano.
  - Barra T en máquina, en la posición de salida.
  - Gironda, con el tronco echado atrás.
  - Renegado con mancuernas y con kettlebell, con el tronco girado hacia la cámara.
  - Encogimiento en polea: el atleta de lado, el cable en un extremo de la barra y una sola
    mano.
  - Jalón en máquina: es una estación de cable.
  - Pull Over tumbado con polea: está a medio arco.
  - Pull Over en máquina: sin almohadillas y con las manos cerradas.
- **Pasan 15:**
  - remo invertido, remo en banda, Band Pull-Apart y Face Pull;
  - dominadas escapulares;
  - encogimientos con barra, mancuernas, kettlebell y Smith;
  - Y-Raises;
  - jalón en polea;
  - Pull Over en banca con mancuerna, barra y kettlebell, y Pull Over de pie.
- El generador ya tiene brief visual para los 20 ejercicios y todos sus implementos.

## Código que entra con el lote

- **Patrón `scapular_retraction`:**
  - entra en la ontología (Python y `AprendeOntology.kt`, sin chip de WikiLab porque no hay
    ficha de ese gesto);
  - `CompositionTaxonomy` lo clasifica como tirón horizontal;
  - tiene la etiqueta «Retracción escapular» en el selector;
  - el conteo de patrones pasa de 62 a 63 en los tres tests que lo fijan.
- **`EmphasisEngine`:** remos, jalones, dominadas, pullovers y Kelso dejan de mostrar
  «Deltoides Anterior» y pasan a posterior. El remo al mentón pasa a lateral. Tiene dos
  tests nuevos.
- **Herramienta:** `catalog_v2_sources.py` acepta `--proof <archivo>`, para que los autores
  verifiquen contra un registro privado sin tocar `catalog/`. Tiene un test que comprueba
  que el registro compartido no se modifica.

## Aviso

El historial guardado conserva los músculos viejos de estos ejercicios. Los gráficos de
volumen por músculo tendrán un corte en la fecha del cambio, sobre todo en el trapecio
(jalón y remos con agarre amplio) y en el antebrazo, que ahora cuenta como estabilizador.

## Fuera de alcance (anotado para la pasada estructural)

- Los tres Pull Over son extensión del hombro con el codo fijo, no tracción vertical. La
  ontología no tiene ese patrón.
- La familia de dominadas no está alineada en romboides y deltoides: los tiene la dominada
  en rack, no la dominada. Cambiar la dominada (piloto aprobado) queda para la pasada
  estructural.
- El nombre canónico es «Remo Gorilla», con doble ele; los nombres no cambian en este plan.
