# Registros v1 · propuesta para aprobar (27-oct-2026)

Lienzo de prototipos: competencias, crear programa («A tu medida» a pantalla completa), registro de comida (describir y
buscar, con todas sus hojas) e historias de competencia, cuerpo y nutrición. Reutiliza estilos, portadas y datos del lienzo
aprobado de Program Detail (`../program-detail-v1/src`).

- Construir: `node build.cjs` → `prototipos.html` (archivo local) y `artifact.html` (publicado).
- Ver una escena suelta: `prototipos.html?scene=<id>`; exportar una historia a 1080 × 1920: `prototipos.html?story=<clave>`.
- `historias/`: PNG exportados de las nueve historias.
- Fuentes en `src/`: `scenes-decisiones.js` (decisiones de producto y preguntas abiertas), `scenes-competencias.js`,
  `scenes-crear.js`, `scenes-comida.js`, `scenes-busqueda.js`, `scenes-historias.js`; piezas comunes en `base.js` y
  `extra.css`; datos sintéticos en `data.js`.

Cada escena lleva notas con qué cambia respecto a hoy (inventario de código del 27-oct) y qué fallos arregla.

## v3 (28-oct-2026): «Crear programa» con pantallas reales

- Las capturas salen de la app: `android-native/app/src/test/java/com/example/kpkn/render/NewProgramRealRenderSheetTest.kt`
  (con `KPKN_RENDER_DIR`) y las hojas del importador. Los PNG a tamaño completo quedan en `real/` (no se versionan).
- `instancia.py docs/design/registros-v1 android-native/app/src/main/res/font` genera `real-web/ni_*.jpg`: el asistente real
  con la cabecera «NUEVO PROGRAMA», la fila plegada «Lo que ya sabemos» y el cierre «Listo para empezar» compuesto con
  piezas reales. El resto de `real-web/` son las capturas reducidas a 393/411 px.
- `build.cjs` incrusta solo las capturas que nombran las escenas.
