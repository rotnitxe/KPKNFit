# Arreglos v1 · Nutrición plana y Program Detail (29-oct-2026)

Propuesta **para aprobar** antes de tocar código. Responde a dos encargos del usuario del 29-oct:

- **Nutrición y su registro**: cifras que no caben, selector de tamaño con círculos, «Aporta» desordenado, gráficos de
  vitaminas y minerales «de Excel» y en arcoíris, «Cómo lo cocinaste» con símbolos y duplicado por «Variante» (que además
  desaparece al elegir «Al horno»), anillo de calorías apretado, avisos de color, «Enter para analizar» colapsado en
  vertical, hojas que aparecen de golpe; y la barra «¿Qué comiste?» de Inicio, que sale a cambio de un botón en la
  tarjeta «Nutrición de hoy».
- **Program Detail**: ficha visual (la del asistente) en lugar de las hojas de texto del catálogo y de «Sobre el
  programa»; barra flotante en vidrio con desenfoque; ⋯ junto al nombre del programa; «Tus últimas 4 semanas» como
  resumen de verdad en lugar del enlace a otro panel de volumen.

Lienzo publicado: https://claude.ai/artifact/YBycPadM4g1VTxWWTMMxQX

## Construir

```
node build.cjs
```

Genera `artifact.html` (sin esqueleto, para publicar) y `prototipos.html` (archivo completo). Reutiliza estilos, piezas
y datos de `../program-detail-v1/src` y `../registros-v1/src`, y las fuentes reales de `res/font`. Las capturas de hoy
del teléfono van en `hoy/*.jpg` (fuera de git); sin ellas el lienzo se construye igual, sin la comparación.

## Piezas

| Archivo | Qué tiene |
|---|---|
| `src/tabs.js` | Pestañas del lienzo (sustituyen a las de Registros al construir). |
| `src/arreglos.css` | Estilos nuevos: Nutrición plana (`np-*`), Program Detail (`pd-*`) y la ficha (`pf-*`). |
| `src/scenes-reglas.js` | Reglas, mapa «lo que pediste → lo que cambia», lo que sale y lo que hay que aprobar. |
| `src/scenes-nutricion.js` | Registro, hoja de un alimento (interactiva), huella de vitaminas y minerales, Inicio. |
| `src/scenes-programa.js` | Barra de vidrio, ⋯ junto al nombre, entrada y página de 4 semanas, fichas de plan. |

## Pendiente de aprobar

1. Vitaminas y minerales: huella radial (recomendada) o cápsulas.
2. Macros en tres tonos de tinta.
3. «Con mantequilla» sale (hoy cuenta como «poco» aceite).
4. La ficha del asistente en el catálogo («Configurar este plan») y en «Sobre el programa» (solo lectura).
5. «Tus últimas 4 semanas» = las 4 semanas de calendario que terminan hoy.

Nada de esto está implementado. Al aprobarse, se implementa 1:1 y lo que el lienzo no contempla se elimina.
