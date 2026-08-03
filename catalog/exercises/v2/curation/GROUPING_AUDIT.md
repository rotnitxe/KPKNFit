# Auditoría de agrupación y jerarquía del catálogo

Fecha: 2026-08-02  
Revisión: `v2-approved-2026-08-02-c`  
Hash canónico: `fafe5c70282c1a6f145dd2f47fbc4a6433566c1cb766096a4ecf1ecd37864b46`

## Criterio editorial aplicado

Una tarjeta solo se fusiona cuando todas las filas comparten patrón de
movimiento, objetivo principal y una identidad técnica reconocible. El cambio
de implemento, apoyo, postura o lateralidad se conserva como configuración
explícita. No se generan productos cartesianos: cada fila de la matriz tiene un
`configurationId`, perfil, metadata y descripción propios.

Se mantiene una variante como especialidad cuando cambia el patrón, el rango o
la demanda de forma sustantiva (déficit, pausa, isometría, Super ROM, método
nombrado o apoyo asimétrico que no pueda describirse honestamente como una
opción del padre).

## Resultado cuantitativo

- 257/257 filas de evidencia con decisión editorial.
- 88 familias y 180 definiciones (antes: 232 definiciones).
- 280 configuraciones materializadas, sin combinaciones implícitas.
- Reducción neta: 52 tarjetas independientes fusionadas en padres seguros.

## Segunda pasada de auditoría — 2026-08-02

Se revisaron de nuevo los cruces entre el catálogo y sus consumidores (plantillas
del sistema, biblioteca de protocolos y perfiles de rendimiento). Solo se
agruparon filas con el mismo patrón y una diferencia técnica que pueda
representarse como selección explícita. Las altas de esta pasada son
configuraciones materializadas; no son aliases ni combinaciones generadas en
runtime:

| Padre | Primer nivel | Segundo nivel (solo si está respaldado) |
| --- | --- | --- |
| Pullover | implemento: mancuernas, polea, máquina | — |
| Patada de tríceps | implemento: mancuernas, polea | bilateral/unilateral cuando existe evidencia |
| Hip thrust | implemento: barra, mancuernas, máquina | bilateral/unilateral cuando existe evidencia |
| Extensión de cuádriceps | — | bilateral/unilateral; no se inventan implementos alternativos |
| Elevación de talones | estación: de pie, donkey, prensa, sentado | implemento y bilateral/unilateral cuando existe evidencia |
| Press de hombros sentado | implemento: barra, máquina | — |
| Curl de muñeca sentado | implemento: barra, mancuernas | — |
| Aperturas inversas | implemento: máquina, polea | estación compatible |
| Elevación lateral | implemento | postura y lateralidad compatibles |
| Curl de bíceps | setup | implemento compatible con ese setup |
| Peso muerto rumano | implemento | apoyo de piernas compatible; se añadió sumo con mancuernas |

La ausencia de una opción en una familia es deliberada: si solo hay una fila
auditada para un implemento, no se crea un chip singleton; si una variante
cambia el patrón, el rango o la demanda de forma sustantiva, permanece como
especialidad separada. En particular, “Extensión de cuádriceps” expone solo
bilateral/unilateral porque el conjunto auditado actual solo respalda máquina.

La revisión también eliminó resoluciones ambiguas en consumidores: curl femoral
sentado/tumbado/de pie, pullover en máquina/polea/mancuernas, hip thrust,
patada de tríceps, elevación lateral, elevación de talones, press sentado y
curl de muñeca ahora apuntan a configuraciones v2 concretas con perfil y nombre
canónico. Ninguna plantilla puede volver a apuntar a una variante por nombre
visible ni a un ID que no esté en el asset aprobado.

## Padres fusionados y orden de chips

El orden de `optionAxes` es parte del contrato y se lee de izquierda a derecha:
primero la decisión más general; después, solo el siguiente nivel que todavía
tenga más de una posibilidad compatible.

| Padre | Ejes, de general a particular |
| --- | --- |
| Peso muerto rumano | `implement` → `stance` |
| Buenos días | `implement` → `load_position` → `posture` |
| Aperturas de pecho | `implement` → `station` → `support_angle` |
| Aperturas inversas | `implement` → `station` |
| Abducción/Aducción de cadera | `implement` → `station` → `laterality` |
| Sentadilla búlgara | `implement` → `load_position` |
| Curl de bíceps | `setup` → `implement` |
| Elevación lateral | `implement` → `posture` → `laterality` |
| Elevaciones posteriores | `setup` |
| Press de banca / Floor press | `implement` |
| JM press / Press California / Tate press | `implement` |
| Press Arnold / Press Z | `implement` |
| Extensión Katana | `implement` → `laterality` |
| Remo con pecho apoyado / Remo Seal | `implement` |
| Sentadilla sissy | `implement` → `load_position` |
| Peso muerto | `implement` → `stance` |
| Curl femoral | `implement` → `station` → `laterality` |
| Glute-ham raise | `load` |
| Sentadilla belt | `laterality` |
| Sentadilla pendular | `laterality` |
| Flexiones de brazos | `support_angle` |
| Jalón al pecho | `implement` |
| Elevación de talones | `station` → `implement` → `laterality` |
| Extensión de tríceps por encima de la cabeza | `implement` |
| Extensión de tríceps cruzada | `implement` → `laterality` |

En la segunda pasada, Elevación de talones queda ordenada como
`station` → `implement` → `laterality`: la estación (de pie, donkey, prensa o
sentado) determina primero el cambio técnico principal; el implemento y la
lateralidad solo aparecen cuando existen alternativas compatibles. Esta regla
no se aplica mecánicamente a todos los padres: cada familia conserva el orden
que explica mejor qué modifica realmente su ejecución.

En el Peso muerto rumano se materializan barra bilateral, barra sumo y
mancuernas B-stance. No se inventa una configuración de máquina o barra hexagonal
porque no existe una fila de evidencia aprobada para RDL con esos implementos.
La elección de implemento aparece primero; al elegir barra, el segundo nivel es
el apoyo de piernas. Las variantes de déficit y Zercher siguen fuera del padre.

En los grupos ampliados se aplicó el mismo criterio conservador: el primer chip
representa el cambio que más altera la ejecución (implemento, estación o carga),
y el segundo chip solo aparece cuando todavía existe más de una opción compatible.
Así, el Peso muerto separa barra hexagonal de barra recta antes de ofrecer postura;
el Curl femoral separa implemento y estación antes de lateralidad; y las
Flexiones solo exponen el ángulo de apoyo, sin convertir métodos como Sphinx en
combinaciones artificiales del padre.

Las etiquetas también son contextuales: el eje estable `stance` se presenta como
`Apoyo de piernas` en Peso muerto rumano (bilateral/B-stance) y como `Postura` en
Peso muerto (convencional/sumo). Ningún ID (`seated_machine`, `floor_sliders`,
`hex_bar`, etc.) llega a la UI; cada valor tiene una etiqueta española explícita.

## Separaciones deliberadas

- Peso muerto convencional, sumo, hexagonal, piernas rígidas y todos los
  déficits no se mezclan con Peso muerto rumano.
- Copenhagen dinámica e isométrica permanecen separadas.
- Elevación lateral Super ROM permanece separada de la elevación lateral
  estándar.
- Búlgara Zercher, Jefferson y Somersault permanecen como especialidades.
- Press Spoto, press con cadenas, squeeze press, Push Press y métodos
  nombrados de remo o curl no se absorben por compartir solo la palabra
  “press”, “remo” o “curl”.

## Protecciones contra regresiones

1. El generador usa una whitelist y un orden editorial explícito; nunca ordena
   ejes alfabéticamente ni deriva configuraciones por nombre.
2. El gate bloquea ejes duplicados, ejes singleton, firmas repetidas,
   descripciones duplicadas/instruccionales y cualquier orden jerárquico que no
   coincida con esta auditoría.
3. El repositorio filtra las configuraciones por las selecciones actuales y
   solo devuelve al UI los ejes ya elegidos más el siguiente nivel resoluble.
   Un valor incompatible no se muestra como chip seleccionable.
4. Los padres con opciones no arrancan con la configuración por defecto
   seleccionada: el usuario elige primero el nivel general. Solo una edición de
   un ejercicio existente hidrata la selección exacta persistida.
5. Android, iOS y backend reciben el mismo JSON canónico y el mismo SHA-256;
   una divergencia bloquea el compilador.
