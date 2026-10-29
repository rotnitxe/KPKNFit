# Fase 1 · Sistema

Objetivo: congelar el vocabulario visual antes de dibujar una sola pantalla. Todo lo que aparece en las fases 2–8 sale de aquí; lo que no esté aquí no se usa.

Fuentes: `ui/design/KpknSurface.kt`, `ui/design/KpknType.kt`, `ui/components/KpknGlass.kt`, `screens/workout/components/WorkoutUiTokens.kt`, `docs/design/KPKN_UI_V2.md`.

## 1. Lienzo y geometría

| Constante | Valor | Nota |
|---|---|---|
| Lienzo de referencia | **393 × 852 dp** | El prototipo se dibuja a esta medida, sin escalar. |
| Ancho compacto | 360 dp | `ViewportAdaptMath.COMPACT_WIDTH_DP`. |
| Alto compacto | 800 dp | `WorkoutHeaderBar.kt:417`; por debajo, cabecera compacta. |
| Referencia probada | 411 × 891 | `ViewportAdapt`; el pager mide 411 × 520. |
| Barra de estado | 24 dp | Inset del sistema. La cabecera pinta debajo y lo respeta. |
| Cabecera | **119 dp** en 393 × 852 | No es una constante: la app la mide (`:516`). 10 + 48 + 3 + 27 + 7 + 24 de estado. |
| Tarjeta | **470 dp** de alto × 345 dp de ancho | `(393−48) = 345`; `345 / (312/425) = 469,95`. Máximo 358 dp (`LiveSessionCardFrame.kt:40-41`). |
| Relator | **107 dp** | `852 − 119 − 470 − (120 + 24) − 12`. La app lo calcula; nunca empuja la tarjeta. |
| Raíl inferior | **112 dp** + 8 dp + inset de navegación | `LiveSessionLineHeight = 112.dp` (`LiveSessionLine.kt:52`). |
| Pasarela lateral del pager | 24 dp por lado | `WorkoutV2Body.kt:1598`. |
| Separación entre tarjetas | 14 dp | `:1632`. |
| Radio de tarjeta | 28 dp | `SetExecutionCard.kt:1938`. |
| Radio interior | 20 dp | `WorkoutUiTokens.InnerCardShape`. |
| Radio de raíl | 26 dp | `LiveSessionLine.kt:184`. |
| Radio de hoja | 28 dp arriba | `KpknShape.sheet`. |

**Regla de oro:** la tarjeta mide 470 dp en todas las caras y estados. Si un aviso no cabe, se superpone o se recorta; no se alarga la tarjeta (`SetExecutionCard.kt:3412`).

## 2. Superficies

| Token | Valor | Uso |
|---|---|---|
| `canvas` | `#000000` | Fondo de pantalla. |
| `module` | `#111111` | Tarjeta de serie, tarjetas de preparación y descanso. |
| `modulePressed` | `#161616` | Módulo pulsado. |
| `moduleBorder` | blanco 7 % | Filete de 1 dp. |
| `inset` | `#1A1A1A` | Chips, campos, círculos de icono, píldoras. |
| `divider` | blanco 6 % | Separador interno. |
| Héroe de carga | `#1B1B1B` | `SetExecutionCard.kt:622`. No es `inset`; el comentario que dice `#1A1A1A` está obsoleto. |
| Raíl de sesión | `#E6161616` | Fondo translúcido; píldora `#202020`. |
| Botón de registro | `#F49A6E` | Al 38 % si está deshabilitado. |
| Techo de mesa | `#0D0D0D` | Solo `WorkoutRoadmapBar`; no se usa en la pantalla de tarjetas. |

**Vidrio v2:** isla con desenfoque 36 dp + tinte `#0A0A0A` al 55 %; hoja con 48 dp + `#0A0A0A` al 66 %; sin base opaca debajo. Respaldo sin blur: `#0E0E0E` al 96 % (`KpknGlass.kt`). En la sesión en vivo, la cabecera solo usa el velo `kpknCoverGlass` sobre la portada al 18 %.

## 3. Tinta y acentos

| Rol | Hex | Uso |
|---|---|---|
| `text` | `#F2EEE6` | Tinta principal. |
| `textMuted` | `#C4BFB6` | Secundario legible. |
| `textFaint` | `#8E8980` | Solo terciario; nunca una acción. |
| `ctaContent` | `#0B0B0B` | Texto sobre tinta. |
| Músculo / sesión | `#F49A6E` | Acento por defecto; botón de registro, cronómetro, AMRAP. |
| Energía | `#F7CF73` | Cardio, aproximación, PR. |
| Ok | `#43D18C` | Completado. |
| Columna | `#8FB2FF` | Recuperación, intervalos de descanso. |
| Mente | `#C9B8FF` | Reservado; no aparece en la sesión. |
| Peligro (texto) | `#FF9B92` | Fallo, molestia. |
| Error técnico | `#EF6F64` | Mensaje de validación y obturador de vídeo. |
| Volumen reemplazado | `#E53935` | Banda y borde de la serie. |
| Cardio | `#E0A13A` | Carrusel de cardio del roadmap. |

Tres rojos conviven y no son intercambiables: **`#FF9B92`** habla, **`#EF6F64`** corrige, **`#E53935`** marca volumen reemplazado.

## 4. Tipografía

Base: `KpknType` (Syne para cifras y títulos; Inter para leer). En la sesión viva hay overrides deliberados:

| Rol | Base | Override en vivo | Uso |
|---|---|---|---|
| `title` | Syne Bold 24/28 | **20/24** normal, **18/20** compacto | Título de la cabecera. |
| `display` | Syne ExtraBold 32/36 | — | No se usa en la tarjeta. |
| `metricXL` | Syne ExtraBold 40/44 | 34/44 en héroe; 44–48 en reloj de descanso | Cifra protagonista. |
| `metricL` | Syne Bold 28/32 | 23/28 en aproximación | ValoresL. |
| `metricM` | Syne Bold 18/22 | 15/18 en eslabones | ValoresM. |
| `eyebrow` | Inter SemiBold 13/18 · 1,2 sp | **10/14** en tarjeta, 9/12 en bandas | Kicker. |
| `caption` | Inter Regular 13/18 | **11/16** normal, 10/14 compacto | Subtítulo y pies. |
| `chip` | Inter SemiBold 13/16 | **12/16** cabecera, 11/14 pasos | Píldoras. |
| `action` | Inter SemiBold 14/20 | 11–13 según ancho | Acciones. |
| `bodyStrong` | Inter SemiBold 15/21 | 13/19 en movilidad | Nombres. |

Reglas:
- Cifras con `tnum`; unidades con `alignByBaseline()`, nunca en superíndice.
- Títulos en tipo frase y máximo una línea en cabecera (marquesina si desborda).
- Al 130 % de letra, una fila puede pasar a dos líneas; nada se solapa ni se corta a media palabra.
- El mínimo legible de la app es 11 sp (`WorkoutUiTokens.MinLabelSp`); las excepciones de 7–10 sp son etiquetas de estado del carril, no texto de lectura.

## 5. Forma y elevación

- Radios: tarjeta 28, interior 20, hoja 28 arriba, raíl 26, píldora 999.
- **Sin sombras** en módulos, tarjetas y raíl. La única sombra es la del pulgar del slider (4 dp) y el FAB de registro (10 dp).
- **Sin ripple de Material.** Pulsación con escala 0,97 y resorte (amortiguación 0,8).
- Dentro de un módulo no hay otra caja: se separa con aire o `divider`.

## 6. Movimiento

| Momento | Valor |
|---|---|
| Giro de tarjeta | `tween(420)` a 0/180°, cámara 18 × densidad; `snap()` si la página no está asentada. |
| Cambio de cara | La cara frontal baja a alpha 0 a los 90°. |
| Página vecina | `alpha = 1 − offset × 0,58`, acotado a `[0,38 · 1]`. |
| Pulsación | 0,97 con resorte; 0,95 en tarjetas del roadmap. |
| Undo | Visible 8 s; fundido 200 ms + deslizamiento 260/200. |
| Reloj de descanso | Vuelta 4,5 s (Torre/Pista) y 9 s (Espiral); aliento del tiempo extra 1,8 s. |
| Marquesina de título | 2500 ms + recorrido ≥1200 ms + 2500 ms. |
| God mode (stub) | Entrada 260 ms, salida 220 ms; no se dibuja. |
| Movimiento reducido | Solo fundidos; sin marquesina, sin partículas, sin giro. |

## 7. Accesibilidad

- Todo lo tocable mide 48 dp; en cabecera compacta, 44 dp de layout con zona táctil ampliada a 48.
- Contraste: tinta sobre módulo 15,6:1; `textMuted` 9:1; `textFaint` 5,1:1 (solo secundario).
- El estado de cada paso se anuncia: «Seleccionada, Completada, Omitida, 40 por ciento, Técnica de volumen reemplazado, calibrada».
- El slider de esfuerzo anuncia «Calidad técnica, N de 10, palabra»; el de esfuerzo, «RIR 2 · firme».
- El reloj de descanso habla: «Descanso: quedan 1 minuto y 12 segundos»; en tiempo extra, «Tiempo extra, 34 segundos».

## 8. Las diez reglas que no se negocian

1. La tarjeta no cambia de tamaño.
2. Un solo acento por sesión; el resto es tinta sobre negro.
3. Cero sombras decorativas y cero ripple.
4. Ninguna acción nueva: si no está en la Fase 0 o en una hoja real, no se dibuja.
5. El vacío se escribe «—»; nunca se inventa un cero.
6. La unidad comparte línea base con la cifra.
7. El rojo de volumen reemplazado no es el rojo de error.
8. La cabecera no tiene botón «atrás»; atrás es del sistema.
9. El texto de una acción dice exactamente lo que pasa («Registrar serie», no «OK»).
10. Si un estado necesita explicación, la explicación va en la especificación, no dentro de la tarjeta.

## 9. Criterio de aceptación

- [ ] Todos los valores del prototipo aparecen en esta fase o en la Fase 0.
- [ ] No hay ningún color fuera de la tabla §3.
- [ ] La tarjeta mide 470 × 345 en todas las escenas.
- [ ] Ningún estado usa sombra, degradado decorativo o ripple.
- [ ] Con movimiento reducido, el prototipo sigue siendo legible y estático.
