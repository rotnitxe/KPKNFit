# Pulido v1 · prototipos para aprobar (29-oct-2026)

Respuesta a los 26 puntos de diseño y UX que el usuario mandó el 29-oct-2026 sobre la app de Android. **Propuesta para aprobar antes de implementar**: nada de los
rediseños está en el código todavía (los arreglos directos sí, escritos sin compilar: ver la pestaña «Resumen» del lienzo).

Lienzo publicado (privado): https://claude.ai/artifact/KQ9mtYKTbKPNAw83mikN4V — 13 pestañas, más de 300 escenas, interactivas donde tiene sentido.
Enlaces a una pestaña: el de arriba + `#resumen`, `#editor`, `#asistente`, `#catalogo`, `#cardio-editor`, `#vivo-tarjeta`, `#roadmap`, `#vivo-resto`, `#cardio-vivo`,
`#tecnica`, `#nutricion`, `#suplementos`, `#ajustes`, `#reparto`.

## Construir

```
node build.cjs                          # artifact.html (sin esqueleto, para publicar) y prototipos.html (archivo completo)
node build.cjs --out=shots/<prefijo>    # a otra carpeta (lo usan los agentes para no pisarse)
node tools/shots.cjs check              # ejecuta cada escena: errores, NaN, undefined, ids repetidos
node tools/shots.cjs list               # todas las escenas
node tools/shots.cjs shot <id>          # PNG de una escena (Chrome headless de Playwright; --size=360x740 --fs=1.3)
node tools/shots.cjs gallery <pestaña> --from=0 --count=5 --cols=5     # varias escenas lado a lado
```

Reutiliza el motor de escenas de `../registros-v1/src` y los estilos de `../program-detail-v1/src`; las fuentes son las reales de la app (`res/font`). Cada
`src/scenes-*.js` se envuelve en su propia función y cada `src/<prefijo>.css` usa su prefijo. Las capturas del teléfono del usuario van en `hoy/` (fuera de git).

## Paquetes

| Prefijo | Pestaña | Puntos | Mapa |
|---|---|---|---|
| `ed` | Editor | 3 bis, 10, 12, 12 bis | `maps/editor.md` |
| `as` | Asistente | 4 | `maps/asistente.md` |
| `ct` | Plantillas y métodos | 3 | `maps/catalogo.md` |
| `ce` | Cardio · editor | 5–9, 11, 26 | `maps/cardio-editor.md`, `maps/cardio-avanzado.md` |
| `lv` | En vivo · tarjeta y Roadmap | 13, 14 (pestaña `roadmap`), 15 | `maps/vivo-tarjeta.md` |
| `lx` | En vivo · más | 16–19 | `maps/vivo-resto.md` |
| `cv` | Cardio en vivo | 25 | `maps/cardio-vivo.md` |
| `tc` | Técnica por vídeo | 2 | `maps/tecnica.md` |
| `nc` | Comida y Cuerpo | 20, 21 | `maps/nutricion.md` |
| `sp` | Suplementos | 24 | `maps/suplementos.md` |
| `aj` | Ajustes | 22 | `maps/ajustes.md`, `maps/ajustes-inventario.md` |
| `rp` | Reparto | 23 | `maps/reparto.md`, `maps/reparto-motor.md` |
| `rs` | Resumen | todos | (documentos dentro del lienzo) |

`MATRIZ.md`: punto → paquete. `BRIEF_COMUN.md` y `briefs/`: los encargos a los agentes (con las palabras literales del usuario). Cada mapa termina con las dudas
que el paquete le deja al usuario.

## Límites

Datos de muestra donde no hay datos reales (p. ej. el volumen por músculo del asistente). Voz, GPS, pulso, vídeo y suplementos están simulados. No sustituye la QA
nativa (insets, fontScale, TalkBack, coste del desenfoque). Al aprobar: implementar 1:1 y eliminar lo que el lienzo no contempla.
