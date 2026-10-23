# QA de LQA1 en el emulador con las medidas del Z Flip 5 (2026-10-20)

- **Emulador:** AVD temporal `ZFlip5_QA`, API 36, 1080 × 2640 a 480 dpi (360 × 880 dp). Navegación por gestos, barra inferior de 24 dp.
  - **Diferencia con el teléfono:** barra de estado de **45 dp**, por el recorte de cámara del perfil pixel_7.
- **APK:** el de LQA1 (`cacde0aae`), copiado en `C:\kw\shots\emu-lqa1\apk`.
- **Registro:** `C:\kw\logs\emu-lqa1-instr.log`.
- **Capturas:** `C:\kw\shots\emu-lqa1\files\` y `sheet-*.png`.

## Pruebas: 59 ejecutadas, 55 correctas, 4 fallos

| Prueba | Fallo | Lectura |
|---|---|---|
| `SessionRevisionFlowDeviceTest` × 3 (`…LiveControlsKeepMovApprox…`, `…ThreeFacesKeepActionsClear…`, `…WeightImeKeepsFrame…`) | La fila `live-series-clouds` (35 dp) se ve con 23 dp (70 px de 105) | La fila queda **recortada por abajo**. El emulador tiene unos 12 dp menos de alto útil que el teléfono, por la barra de estado más alta, y la disposición actual del tercio inferior no tiene margen. LV-A (V1 + pantalla corta V3) debe resolverlo y añadir una prueba a 740–800 dp de alto. |
| `LiveHeaderSheetsRenderSheetTest#voice130` | `ComposeTimeoutException`: «Preparando controles de voz…» no desaparece en 5 s | `voice100` pasa. Probable lentitud del emulador al 130 %. Repetir en el teléfono antes de tocar nada. |

## Defectos visuales de LQA1 (B1–B10)

| # | Estado | Detalle |
|---|---|---|
| B1 | **Sigue** | Con la ficha desplazada (`review-set/10`), el título pequeño «Press de Banca Plano» se superpone al subtítulo «Pectorales · Barra · Empuje horizontal» mientras se desvanece. El cruce no es limpio. |
| B2 | Corregido | La fila «Así se ve un drop-set» ya no la tapa nada. |
| B3 | Corregido | La cadena del rest-pause tiene chevrón de continuación. La fila «Así se ve un rest-pause» se corta al borde sin desvanecido: detalle menor. |
| B4 | Corregido | Barra estrecha con «480 kg hoy» y un solo «Sin registro previo». |
| B5 | Corregido | Hitos y Notas quedan anclados arriba. |
| B6 | Corregido | La animación queda separada del texto. |
| B7 | Corregido | Verde, amarillo y naranja con leyenda y «Borde grueso: músculo elegido». |
| B8 | Corregido | La tarjeta sigue el estilo elegido (Torre o Pista). |
| B9 | Corregido | «de 4:09 · sugerido» y «Plan de sesión · Referencia del plan 3:00». |
| B10 | Corregido | Sin franja gris al pie. |
| Nuevo | **Defecto** | En la tarjeta de descanso (`rest-card`, `rest-track-card`), la línea «Plan de sesión · 3:00» queda cortada entre «Última serie» y «Siguiente». Viola la ley 1 (nada se corta ni se solapa). Puede agravarse por el alto del emulador. |

**Destino:** B1, la tarjeta de descanso y el margen del tercio inferior entran en el paquete LV-A (`PLAN_IMPLEMENTACION.md`). `voice130` se repite en el teléfono.

## Seguimiento F1 de la ficha de catálogo (23-oct-2026)

La captura C:\kw\shots\screens-v2-final-emu-20261023-c3\F1-cues-1792757772026551400.png muestra «Press Militar» fijo en [60,169][408,240] y «Tu historial» en [114,181][900,252]. El título fijo se dibujaba sobre el contenido del ScrollView al bajar la ficha; el fondo semitransparente dejaba ambos textos legibles a la vez. Este es el F1 de la ficha de catálogo y se registra aparte de B1, que describe la transición de título/subtítulo de la ficha en vivo.

La ficha ahora reserva progresivamente en el viewport desplazable el alto del encabezado fijo según aparece, incluido el inset superior. El historial y el resto del cuerpo empiezan debajo del título sin tapar contenido; la lista conserva su scroll y los controles existentes. LiveFichaHeadingRegressionTest comprueba que «Tu historial» queda debajo del encabezado en 740/880 dp y 100/130 % de escala, y que cerrar/añadir conservan áreas táctiles de al menos 48 × 48 dp.

El cambio está implementado en u19. No se ejecutó Gradle/JVM ni se hizo una nueva comprobación en emulador o teléfono; la evidencia visual descrita es la captura anterior.
