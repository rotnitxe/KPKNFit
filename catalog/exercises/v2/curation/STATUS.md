# Estado de ejecución — catálogo de ejercicios

Fecha de corte: 2026-08-02 (curaduría integral v3)
Revisión: `v2-approved-2026-08-02-c`
Hash canónico compartido: `02e0954512d23729ff15efe13bfd9cce00309769d1e8fb450e2344708f14b3cf`

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
- `muscleNotes` completas en las 518 configuraciones: una nota por músculo
  listado (sin huérfanos ni faltantes), validada por compilador, gate y backend.
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
- Guía editorial: `curation/EDITORIAL_GUIDE.md` (reglas R1-R8 y L1-L8).
- Runtime Android: `android-native/app/src/main/assets/exercise_catalog_v2.json`.
- Runtime iOS: `ios-native/KPKNFit/KPKNFit/exercise_catalog_v2.json` (copia de
  datos idéntica; la paridad de código iOS queda pendiente por falta de
  toolchain Apple en esta máquina).
- Backend: `backend/exercises_catalog_v2.py` valida revisión, hash, estructura,
  metadata, identidad exacta y `muscleNotes`.
- El compilador y el verificador comparan los tres artefactos mediante el mismo
  hash canónico; cualquier divergencia hace fallar el proceso.

## Gates ejecutados (corte curado)

- `python scripts/catalog_v2_gate.py --strict` → `status=READY`.
- `python scripts/compile_exercise_catalog_v2_cli.py --check` → 196 definiciones,
  518 configuraciones, hash canónico coincidente.
- `python scripts/compile_exercise_catalog_v2_cli.py --write` → asset Android
  regenerado (y copia idéntica a iOS).
- Backend: 7 pruebas Python → `OK`.
- Android: `testBaseDebugUnitTest` y `testHealthDebugUnitTest` → 0 failures.

## Regla de mantenimiento

Toda modificación futura debe regenerar fuente, runtime y hash en un solo corte,
ejecutar el gate estricto, las pruebas Android/backend y la inspección del APK.
No se admite reintroducir v1, aliases globales, resolución por nombre o chips
implícitos. Una definición sin metadata completa, configuración por defecto,
`muscleNotes` completas o decisión editorial explícita debe bloquear el build.
