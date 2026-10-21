# LV3 · La tarjeta de la serie, más compacta y limpia (puntos 4 —solo los botones—, 5.1, 5.2, 5.3 y 5.5)

Rama y worktree: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `f6da01f52`). Lee antes `LV_COMUN.md`.

Archivos: `screens/workout/components/SetExecutionCard.kt` y `screens/workout/WorkoutSetPager.kt` (las flechas).

## 5.1. La carga como «héroe» de la tarjeta
- **Hoy:** la carga es una sub-tarjeta con márgenes encima de la tarjeta.
- **Ahora:** la parte de arriba de la tarjeta ES la zona de la carga:
  - una superficie un tono más clara (por ejemplo `#1A1A1A` sobre el `#111111` de la tarjeta) a todo el ancho;
  - pegada a las esquinas de arriba de la tarjeta, con su mismo radio;
  - una división limpia con la parte de abajo, más oscura (reps e intensidad);
  - sin doble borde ni márgenes laterales.
- Gana altura para la tarjeta.

## 5.2. Los chips de carga (Anterior · sugerida · +2,5)
- **Hoy:** son anchos y altos, se apilan en vertical y el del medio es más pequeño.
- **Ahora:**
  - los tres en una fila horizontal, con el mismo ancho (`weight(1f)`), el mismo alto (≤ 44 dp) y la misma forma, 8 dp entre ellos;
  - la etiqueta pequeña y la cifra en una línea, o en dos como mucho dentro de esos 44 dp;
  - al 130 % no se cortan las cifras.

## 5.3. La cifra que se escribe
- **Hoy:** la carga (por ejemplo «102,5») sale enorme y en cursiva.
- **Ahora:** la misma tipografía y el mismo tamaño que las cifras de reps e intensidad de la misma tarjeta, con el mismo estilo de
  marcador vacío.

## 5.5. Las flechas para pasar de serie
- **Hoy:** son grandes y ocupan espacio de la tarjeta.
- **Ahora:**
  - chevrons de 20–24 dp sobre un círculo de 32 dp, tenue;
  - el objetivo táctil sigue siendo de 48 dp con el relleno;
  - no quitan ancho a la tarjeta.

## 4. Los dos botones de abajo, compactos
- «Opciones avanzadas» → **«Avanzado»** y «Ver ejercicio/Fotos» → **«Modo visual»**:
  - píldoras de una línea, de 36 dp de alto;
  - no alargan la tarjeta y no quedan bajo el ✓ flotante;
  - lo que abren NO cambia en este paquete: su interior lo rediseñamos después, con la propuesta aprobada.
- Cambia los textos en `SetExecutionCard.kt` (~L2389-2394), en `SetCardTechniqueBack.kt` (~L189) y en `SetCardExerciseMediaBack.kt`
  (~L199), y el comentario de relleno de LR1a2 (~L681-697).

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*`.
- **Nuevas:**
  - los tres chips miden lo mismo;
  - la cifra escrita usa el estilo de las reps;
  - los textos «Avanzado» y «Modo visual»;
  - la tarjeta es más baja que antes (mide la altura en la hoja de render y anótala).
- **Hoja de render** `render/LiveSessionRefineRenderSheetTest`, al 100 % y al 130 %: la tarjeta con la carga vacía, con «102,5» escrito,
  y con las flechas visibles.

## Permitido y prohibido
- **Permitido:**
  - `SetExecutionCard.kt` y `WorkoutSetPager.kt` (solo las flechas);
  - los dos textos de los reversos;
  - archivos nuevos y pruebas.
- **Prohibido:**
  - el selector de RIR/RPE/fallo (punto 5.4: va con una propuesta aparte);
  - el interior de «Avanzado» y de «Modo visual»;
  - el relator (LV2) y la cabecera y la hoja de estado previo (LV1);
  - la barra inferior, el cockpit y las hojas (LV4);
  - `WorkoutViewModel`.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv3 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "*LiveSessionRefineRenderSheet*" > /c/kw/logs/lv3-<n>.log 2>&1`

Tope: 4 corridas.

## Commits
- `style(workout): la carga como héroe de la tarjeta, chips iguales en fila y la cifra con el estilo de las reps (LV3)`.
- `style(workout): flechas compactas y los botones «Avanzado» y «Modo visual» de una línea (LV3)`.
