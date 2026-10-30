# Pulido v1 · Encargo común para los prototipos (29-oct-2026)

## 1. Qué estás haciendo
El usuario revisó la app Android KPKN Fit en su Galaxy Z Flip5 (pantalla principal ≈ 360 × 880 dp) y mandó una lista de ~26
imperfectos de diseño y UX. Para los rediseños pidió **prototipos para aprobar ANTES de implementar**. Tú construyes los de UN
paquete (tu brief: `briefs/<paquete>.md`) como escenas del lienzo `docs/design/pulido-v1/` (HTML autocontenido). Cuando él los
apruebe se implementarán en Kotlin/Compose **al pie de la letra**: lo que dibujes se construirá y lo que no dibujes se eliminará
de la app. Por eso el prototipo es el contrato.

Trabajas en el worktree `C:\kw\pulido` (rama `feat/pulido-v1`). Todo el código Kotlin vive en
`C:\kw\pulido\android-native\app\src\main\java\com\example\kpkn\` — léelo para que el prototipo sea fiel a lo que la app hace hoy.

## 2. Reglas firmes (del usuario)
1. **Listón**: «nivel top mundial», superar en diseño a Lyfta, MacroFactor Workouts y Hevy. Sé estricto contigo: si algo se ve
   apenas «correcto», no sirve. Decide con criterio y deja anotada cada decisión.
2. **Paridad 1:1** («AL PIE DE LA LETRA, 99,999 % IGUAL»): cubre TODAS las instancias —estado normal, vacío, error, cargando, hojas,
   menús, confirmaciones, desactivado, texto al 130 %—. Nada que el usuario vaya a ver en la app puede faltar en el prototipo.
3. **Refinar, no quitar**: parte de lo que la app hace HOY y mejora cómo se presenta. Si propones quitar, mover o añadir una
   función, márcalo en las notas (`out`, `move`, `new`, `ask`) con la razón. Se quita sin discutir solo lo que tu brief dice que el
   usuario mandó eliminar.
4. **No inventes contenido**: textos, opciones, nombres y datos salen del código real. Lo que sea propuesta tuya va con etiqueta
   `new` y se explica. Lo que dependa de un motor que no existe, se dice.
5. **Las mejoras son EL SISTEMA**: nada de interruptores «beta» ni opciones activables para tu propia propuesta.
6. **Sin scroll para lo importante** cuando el brief lo pida; el scroll solo para listas repetibles.
7. **Sin relleno**: cada elemento gana su sitio; sin tarjetas dentro de tarjetas; contenedores con criterio (los módulos de datos
   llevan contenedor; lo editorial y la navegación van sobre el negro continuo); sin texto suelto; sin arte gigante en bloques de
   datos; sin emojis.
8. Iconos de línea KPKN (retícula 24, trazo 1,75, extremos redondos) con `ic('nombre',tamaño)`; los que falten, con
   `Object.assign(P,{nombre:'<path d="…"/>'})`. Español de la app (tú, tono cercano), coma decimal.

## 3. Lenguaje visual vigente
Tokens en `docs/design/program-detail-v1/src/styles.css` (`:root`) y piezas comunes en `src/pulido.css` (léelos).
- Lienzo negro `#000`; módulos `#111`; hojas, menús y píldoras en gris plano `#1B1B1D` con sombra real (`.pl-sheet`, `.pl-flat`);
  vidrio (`.pl-glass`, `backdrop-filter` real) SOLO en islas flotantes (barras superior/inferior) para que el contenido pase por debajo.
- Crema `--ink #f2eee6` = acento y botón primario (`.pl-btn`); `--muted`, `--faint`, `--dim` para lo secundario. Color solo con
  significado: cardio `--col #8fb2ff`, energía `--ene #f7cf73`, músculo/coral `--mus #f49a6e`, hecho `--done #43d18c`, peligro `--danger #ff9b92`.
- Tipografía: **Syne** 700/800 para títulos y cifras de impacto; **Inter** 400/500/600 para UI y texto. En selectores y listas de
  cifras usa Inter con `tabular-nums lining-nums` (`.pl-num`): Syne tiene cifras de distinta altura y se ve desalineada.
- Tacto ≥ 44 px; texto ≥ 11 px (12–13 el secundario); debe aguantar 130 %.
- Movimiento: 160–320 ms, `cubic-bezier(.2,.8,.2,1)`; entradas con desplazamiento corto + opacidad; nada infantil; las animaciones
  largas tienen versión estática con sentido para `prefers-reduced-motion`.
- Referencias del estilo ya aprobado: `docs/design/arreglos-v1`, `program-detail-v1`, `registros-v1`; `live-session-iteration-03`
  (cardio, voz, juegos: propuesta del 27-oct); portadas de programa (`program-detail-v1/src/covers.js`: líneas y ondas abstractas).
- Tamaños: por defecto 360 × 880 (su Flip5). Comprueba también 360 × 740 y 412 × 915 y texto al 130 %.

## 4. Cómo se escribe un paquete
Tus archivos (no toques los de otros): `src/scenes-<prefijo>.js`, `src/<prefijo>.css` y `maps/<paquete>.md`. TODAS tus clases CSS,
`@keyframes` e ids de escena empiezan por tu prefijo. Tu archivo de escenas se ejecuta dentro de su propia función: no dependas de
variables de otros paquetes. Globales disponibles: `scene()`, `ic()`, `P`, `state`, `animateIn`, `rng`, los helpers de
`registros-v1/src/base.js` (`chips`, `tg`, `radio`, `opt`…) y los datos de `covers.js`/`data.js`.

```js
scene({
  id:'ed-serie-1',                 // único, con tu prefijo
  tab:'editor',                    // id de pestaña (src/tabs.js)
  title:'Serie · carga y reps',
  html:()=>`<div class="scroll">…</div>`,   // contenido del teléfono (la barra de estado y la de inicio ya existen)
  notes:{tags:[['new','propuesta'],['keep','ya existe']],   // out | move | new | keep | ask
         p:['Qué se ve y por qué.'],list:['Punto 1'],listTitle:'Detalle',
         img:'h07-cardio-tarjeta',imgCap:'Hoy, en tu teléfono.'},  // captura del usuario (hoy/*.jpg), opcional
  after:(phone)=>{ /* interacción: delegación de eventos dentro de `phone`, estado local */ }
});
```
Una escena = un estado de la pantalla (o una hoja o un menú). Las escenas **interactivas** (arrastrar, deslizar, alternar,
abrir/cerrar, contar) valen más que cinco capturas estáticas: usa `after` y CSS para que el prototipo RESPONDA como la app. El orden
en tu archivo es el del selector «Estado». Las capturas del usuario están en `docs/design/pulido-v1/hoy/*.jpg` (ábrelas con Read):
enseñan la pantalla de hoy y sus defectos; muéstralas junto a tu propuesta con `notes.img`. Si necesitas más ejemplos del motor,
mira `docs/design/arreglos-v1/src/scenes-*.js` y `live-session-iteration-03/src/app.js`.

## 5. Verificar (obligatorio; nada de Gradle)
```
cd C:\kw\pulido\docs\design\pulido-v1
node build.cjs --out=shots/<prefijo>                              # compila TODO a tu carpeta (si otro paquete falla, se omite y avisa)
node tools/shots.cjs check --build=shots/<prefijo> --prefix=<prefijo>      # errores, NaN, undefined en tus escenas
node tools/shots.cjs shot <id> --build=shots/<prefijo>            # PNG en shots/<id>.png (--size=360x740, --fs=1.3)
node tools/shots.cjs gallery <pestaña> --build=shots/<prefijo> --from=0 --count=4   # varias escenas lado a lado
```
Mira tus PNG con Read de verdad: busca texto cortado, solapes, desalineaciones, contrastes pobres, huecos muertos, cosas que se
salen del marco. Revisa 360 × 740 y 130 % en las escenas con más contenido. Para animaciones, comprueba también la captura final
(el estado de reposo). Tope: 3 ciclos de corrección por escena; lo que no cierre, anótalo en tu informe. No toques el Kotlin.

## 6. Entrega
- `maps/<paquete>.md` (≤ 120 líneas): (1) archivos/composables reales de la app que corresponden a cada escena; (2) inventario
  escena → pieza de la app; (3) tus propuestas y decisiones, con su razón; (4) qué cambios de motor o datos exigiría implementarlo;
  (5) dudas que de verdad necesite decidir el usuario.
- NO hagas commit. Informe final (≤ 200 palabras, español): escenas hechas, decisiones, lo que quedó flojo, lo que pide motor.
