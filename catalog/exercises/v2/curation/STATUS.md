# Estado de ejecución — catálogo de ejercicios

Fecha de corte: 2026-08-10 (curaduría editorial v7.2 — copy humano editorial)
Revisión: `v2-approved-2026-08-10-c`
Hash canónico compartido: `20ecd23cb4766c341236e09d336bf1c3d3db3041ec6d8b3dd568de124acc0aa5`

## Curaduría v7.2 (2026-08-10): estructura editorial humana aprobada

- Las 196 descripciones de definición se reescribieron a mano con la estructura
  aprobada por el dueño del producto: (1) introducción con el nombre del
  ejercicio, tipo de movimiento, músculos y marco editorial; (2) ejecución
  contada en tono editorial con el implemento principal integrado; (3)
  mención opcional de 1-2 alternativas con su efecto real y transición
  natural (sin listas robóticas de implementos); (4) veredicto dedicado.
- La descripción de definición abre con el nombre del ejercicio (R11 v7.2);
  las configuraciones siguen sin repetirlo y pasan a ser líneas de matiz:
  esencia del movimiento (escrita por ejercicio) + efecto real de los chips.
- Se relajaron dos gates: nombre canónico permitido en definiciones y formas
  reflexivas/descriptivas como "se ejecuta" permitidas (imperativos fuera).
- Verificación anti-reciclaje: 0 frases de 6+ palabras compartidas entre
  definiciones; 0 implementos mencionados que el ejercicio no tenga; 0
  imperativos; aperturas únicas en definiciones y configuraciones.

## Curaduría v7 (2026-08-10): copy humano editorial — descripciones reescritas

- `editorial_briefs.json` reescrito con calidad humana: 196 descripciones de
  definición con estructura de 3 frases (qué es, qué trabaja + implementos
  disponibles, veredicto dedicado) y 518 líneas de matiz por configuración
  (variante corta y dedicada por implemento/agarre). Se elimina el relleno
  "una diferencia concreta para repartir el esfuerzo durante la serie", los
  benefits duplicados y los veredictos reciclados.
- Cada descripción menciona al ejercicio sin repetir el nombre canónico, respeta
  los implementos reales de cada definición (82 con múltiples implementos) y
  aporta un veredicto tipo "gran constructor de..." dedicado y no genérico.
- El gate comprueba cobertura exacta, igualdad con el perfil, ausencia de
  boilerplate y unicidad de la primera frase (196 + 518 aperturas únicas).

## Curaduría v6 (2026-08-08): briefs dedicados por ejercicio y configuración

- `editorial_briefs.json` es la fuente autoral de las 196 definiciones y 518
  configuraciones. Cada opción tiene descripción, beneficios, técnica y
  justificación propios; no se deriva texto visible desde un patrón global.
- Se eliminó del flujo editorial la apertura repetida de los remos y de los
  demás patrones. El gate comprueba cobertura exacta, igualdad con el perfil,
  ausencia de boilerplate y unicidad de la primera frase.
- La pasada solo reemplaza copy y cues editoriales; conserva las fichas
  `muscleNotes` y `jointInvolvement` ya validadas.

## Curaduría v5.1 (2026-08-08): lectura accesible y tarjeta ordenada

- Se reescribieron las 196 descripciones de definición y las 518 fichas de
  configuración con frases más directas, sin repetir el nombre canónico dentro
  del cuerpo y sin presentar un simple cambio de implemento como una variante.
- Se normalizó la capitalización de descripciones, beneficios, técnica,
  músculos, articulaciones, acciones y etiquetas compactas.
- La tarjeta expandida ahora coloca primero los chips de opciones. Descripción,
  Técnica, Involucramiento Muscular e Involucramiento Articular son secciones
  independientes, cerradas por defecto y abiertas solo al tocarlas.
- El gate estricto bloquea nombres repetidos, textos visibles que comienzan en
  minúscula y revisiones de perfil desincronizadas.

## Curaduría v5 (2026-08-08): ficha específica por variante e involucramiento articular

- Las 518 configuraciones tienen una descripción editorial propia que combina
  el movimiento, el implemento, la posición o agarre seleccionado, el beneficio
  de esa variante y una técnica breve; ya no se describe la opción como un mero
  cambio de implemento.
- Cada configuración incorpora `benefits`, `techniqueSummary` y
  `variantRationale`, además de cues de preparación, ejecución y errores
  frecuentes adaptados a sus ejes técnicos.
- `muscleNotes` se reescribió con el rol, la acción muscular y la consecuencia
  concreta de la variante. El nuevo `jointInvolvement` registra articulación,
  rol (principal, secundaria o estabilizadora), acciones y explicación
  biomecánica; usa los IDs canónicos de WikiLab y se replica en metadata rica.
- La revisión v6 queda protegida por compilador, gate editorial, backend y
  loader Android; el runtime Android y la copia de datos iOS comparten el hash
  canónico indicado arriba.

## Curaduría v4 (2026-08-03): descripciones amigables, involucramiento adaptativo y ejercicios nuevos

- **Descripciones reescritas para el usuario final** en las 196 definiciones y
  518 configuraciones: texto cercano, con carácter y que invita a probar el
  ejercicio. Fuera la jerga biomecánica (bisagra, patrón, cadena) y las
  plantillas genéricas. Regla L10.
- **Involucramiento muscular adaptativo real por chips**: remos con agarre
  amplio → trapecio/espalda alta, cerrado → dorsal/bíceps; dominadas supinas →
  bíceps protagonista, pronadas/neutras → estabilizador. Regla L11.
- **Ejercicios nuevos**: Curl Martillo y Curl Invertido (Barra H, Mancuernas,
  Polea, Máquina, Banda) y la familia Rotaciones de Antebrazo (Supinaciones y
  Pronaciones con Mancuerna y Polea). Implemento nuevo `h_bar` ("Barra H") con
  label en Android.
- Conteos nuevos: **96 familias / 196 definiciones / 518 configuraciones**.
- Gate: la palabra "todo" ya no se trata como placeholder (falso positivo del
  español en las descripciones nuevas).

## Correcciones posteriores al corte (-c, 2026-08-03)

- Eliminado "Curl de Bíceps Declinado" (duplicado funcional del Curl Bayesian).
- Title Case corregido: "Flexiones de Brazos", "Curl de Bíceps en TRX",
  "Tate Press".
- Peso Muerto Rumano y Peso Muerto Rumano Sumo: mismo set de implementos que el
  Convencional (se quitó `machine`; regla L9).
- UI picker v2: la descripción queda solo arriba de los chips (se eliminó la
  duplicada debajo) y cuando hay ≤7 chips en total, todos los ejes comparten una
  sola fila horizontal en vez de una fila vacía por eje.
- Conteos nuevos: 192 definiciones / 504 configuraciones (antes de la v4).

## Resultado del corte

El catálogo quedó generado desde fuente editorial determinista y es el único
catálogo que se empaqueta como runtime Android. El corte contiene:

- **96 familias, 196 definiciones y 518 configuraciones** enumeradas.
- Curaduría integral v2 aplicada: reestructura de bisagras (Convencional, Sumo,
  Rumano, Rumano Sumo, Piernas Rígidas, Buenos Días), remos (Convencional,
  Pendlay, Barra T, Gironda, Pecho Apoyado), curls de isquiosurales (Sentado,
  Tumbado, De Pie + sliders/balón/nórdico), presses de banca por ángulo,
  pullovers (de pie ≠ en banca), jalón con lateralidad, dominadas con agarre y
  amplitud, sentadillas (barra alta/baja, frontal, sumo, búlgara, sissy, hack),
  zancadas por dirección, presses de hombro por postura, bíceps por postura
  (Curl Araña, Bayesian, Concentrado, Sentado, Predicador, Crucifijo, Superman,
  Drag), aperturas por ángulo, Hip Thrust, Reverse Hyper único,
  Aducciones/Abducciones de Pierna.
- Curaduría v3 (revisión -c) añade y corrige: barra de seguridad (`safety_bar`)
  en Buenos Días y sentadillas traseras; **glúteo medio** (`gluteus_medius`)
  como músculo diferenciado (se agrupa con "Glúteos" en el cálculo de volumen);
  Cruce de Poleas con altura de polea; Aperturas Inversas con Máquina Pec Deck,
  Polea y Mancuernas; elevaciones laterales/posteriores/frontales reorganizadas;
  Super ROM unificada; Extensión de Tríceps (polea alta/máquina/banda);
  Extensiones/Flexiones de Cuello fusionadas; elevación de talones
  (máquina/barra/smith/polea × lateralidad); dominadas con perfiles musculares
  adaptativos por agarre y amplitud; eliminación de duplicados (curl inclinado,
  press de hombros de pie, plancha Copenhagen isométrica, hiperextensiones
  redundantes, Super ROM duplicada) y renombres Title Case sin relleno.
- `muscleNotes` y `jointInvolvement` completas en las 518 configuraciones: una
  explicación por músculo y articulación, sin huérfanos ni faltantes, validada
  por compilador, gate, backend y loader Android.
- Equivalencias fijas por rol: Principal 1.0 / Secundario 0.5 / Estabilizador
  0.4; no se guardan números en el JSON, UI y contadores derivan del rol.
- Eje condicional `pulley_height` soportado en compilador, gate, backend y
  loader Android (obligatorio donde `implement=cable`; prohibido en el resto;
  en definiciones de polea fija como Cruce de Poleas el eje `implement` es
  implícito y queda exento del chequeo de singleton).
- Chips limitados a ejes declarados por cada padre; solo aparece el siguiente
  nivel compatible. No se generan productos cartesianos ni se mezclan
  revisiones, definiciones o configuraciones.
- Variantes que cambian el patrón o la demanda (déficit, Zercher, métodos
  nombrados, isometrías) permanecen como especialidades separadas.
- Cada configuración materializada aporta su propia descripción contextual
  (≥40 chars, no instruccional, distinta entre configs del mismo padre).

## Artefactos y paridad

- Fuente agregada: `source/catalog_v2.json` (canónica, reconstruida por
  `scripts/merge_catalog_v2_families.py` desde `source/families/`).
- Guía editorial: `curation/EDITORIAL_GUIDE.md` (reglas R1-R11 y L1-L12).
- Runtime Android: `android-native/app/src/main/assets/exercise_catalog_v2.json`.
- Runtime iOS: `ios-native/KPKNFit/KPKNFit/exercise_catalog_v2.json` (copia de
  datos idéntica; la paridad de código iOS queda pendiente por falta de
  toolchain Apple en esta máquina).
- Backend: `backend/exercises_catalog_v2.py` valida revisión, hash, estructura,
  metadata, identidad exacta, `muscleNotes`, `jointInvolvement` y la ficha
  editorial.
- El compilador y el verificador comparan los tres artefactos mediante el mismo
  hash canónico; cualquier divergencia hace fallar el proceso.

## Gates ejecutados (corte curado)

- `python scripts/catalog_v2_gate.py --strict` → `status=READY`.
- `python scripts/compile_exercise_catalog_v2_cli.py --check` → 196 definiciones,
  518 configuraciones, hash canónico coincidente.
- `python scripts/compile_exercise_catalog_v2_cli.py --write` → asset Android
  regenerado (y copia idéntica a iOS).
- Backend: pruebas Python del catálogo → `OK`.
- Android: `testBaseDebugUnitTest` y `testHealthDebugUnitTest` → 0 failures.

## Regla de mantenimiento

Toda modificación futura debe regenerar fuente, runtime y hash en un solo corte,
ejecutar el gate estricto, las pruebas Android/backend y la inspección del APK.
No se admite reintroducir v1, aliases globales, resolución por nombre o chips
implícitos. Una definición sin metadata completa, configuración por defecto,
`muscleNotes`, `jointInvolvement` y la ficha editorial completas o decisión
editorial explícita debe bloquear el build.
