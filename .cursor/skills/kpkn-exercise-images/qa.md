# QA de PNGs de técnica

No declaré listo un PNG porque “se ve bien” en el caption de `Read`. Ese caption se contagia del historial.

## Inspector limpio

Lanza un subagente **sin** mencionar el error. Adjunta solo las imágenes. Preguntas binarias:

```
A) Orientación del atleta respecto al pad/barra (pecho al pad / espalda al pad / otro)
B) Cuántas superficies de apoyo para los pies: 1 o 2+
C) Dónde tocan las suelas: placa integrada de la máquina, escalón/caja aparte, o piso
D) ¿Hay una segunda placa vacía detrás de los talones? sí/no
E) ¿La placa/máquina está deformada (bordes torcidos, derretida, cubo suelto)? sí/no
F) ¿Cómic (contornos gruesos) o ilustración semi-realista? ¿Logos/texto?
```

Si dos inspectores discrepan, recorta la zona de contacto y pregunta otra vez **solo** sobre ese recorte.

## Recortes

```
python .cursor/skills/kpkn-exercise-images/scripts/qa_crops.py <png> --out-dir assets/_qa
```

Compara recorte de pies contra la foto del usuario (si existe) y contra una máquina vacía real.

## Criterios de rechazo (P0)

- Dos plataformas / pies en el escalón de adelante
- Máquina que no es la del ejercicio
- Geometría derretida o cajón desconectado del marco
- Cómic si el usuario pidió menos caricatura
- Foto real si el usuario pidió ilustración
- Logos o texto
- Dos viñetas
- Pose distinta a la foto/autoridad del usuario

## Hack invertida (ejemplo trabajado)

Autoridad de técnica: foto de gym pecho-al-pad, sentadilla profunda, pies en **la placa inclinada única** al pie de los rieles a 45°.

Hack real: trineo en dos rieles diagonales, pad grande + hombreras + manijas, **una** pisadera rectangular rígida. No prensa, no palanca, no belt squat.

Error típico: pies en un peldaño plano frontal; la rampa de diamante queda vacía. “Atrás, en el respaldo inclinado” = esa pisadera, no el pad acolchado del pecho.

No adjuntar `exercise_sentadilla_hack_invertida.png` mala ni `hack_maquina` si esa también enseña dos pisaderas.

## Caption de Read

Úsalo para composición general. **No** para decidir si los pies están bien. La foto correcta del usuario a menudo la describe como “error”.
