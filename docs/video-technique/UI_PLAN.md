# Técnica en vídeo · ronda de interfaz e integración (UI, 20-oct)

Pedido del usuario (20-oct): «Otro agente ya avanzó bastante. Verifica sus cambios y mira lo que puede faltar. También haz una buena
implementación a nivel de UI, y que esté correctamente integrado en la sesión en vivo, en el modo visual y también que esté como
función en el perfil.»

Base: `master` @ `8ac57c9d4`, del que salió el APK firmado «KPKN Pre Release 17» (20-oct). Rama de integración de esta ronda:
`feat/vt-ui` (worktree `.claude/worktrees/video-technique-rep-counter-ee5b0d`). Carril del agente: `C:\kw\vt-a`.

## Verificación de lo que hizo Codex (16–19 oct)

Integrado en `master` (`2fc6f38a5` y arreglos `06510ac22`, `0ba94859a`):
- **VT9a:** tendencias por ejercicio y comparar dos vídeos.
- **VT9b:** hub «Técnica en vídeo» con importación de la galería, «Analizar vídeos anteriores (N)» y ajustes persistentes. Rutas
  registradas y entrada desde el Perfil.
- **VT8b:** propuestas de reps en la tarjeta activa y en el descanso. «Usar» cambia el borrador; «Corregir» pasa por la puerta de
  voz. «Ver análisis» desde el álbum, el cockpit y el Modo visual. El paso 1 pendiente de VT8a está hecho (`startPreview`).
- **VT10:** banco congelado (59 de ajuste y 38 de reserva) y diagnóstico del corpus.
- **QA:** R8 y emulador con cámara sintética, y el APK firmado.

Problemas encontrados en esta revisión:
1. **Ley 1 rota.** La fila del vídeo (`WorkoutVideoRepsSuggestion`) se inserta entre las reps y el esfuerzo de la tarjeta, así que
   la tarjeta CRECE cuando llega un análisis. Es texto plano con acciones de texto: el «%» se parte en otra línea y no tiene
   jerarquía.
2. **Modo visual sin análisis.** Las miniaturas de vídeo no dicen nada del análisis, y la línea de abajo siempre dice «Se guarda en
   el álbum…». Además, en «Vídeo», el botón de grabar abre OTRA pantalla completa (`TechniqueRecorderScreen`, negra y con su propia
   cabecera) en vez de la vista expandida del Modo visual (G3): se siente como salir de la sesión.
3. **Un análisis FALLIDO se ve como «no concluyente»**, sin «reintentar».
4. **Perfil:** la tarjeta «Técnica en vídeo» es un `TextButton` de Material con una frase, sin ningún dato.
5. **Hub:** funciona, pero es mínimo: sin vídeos recientes ni estado, y con la jerarquía de una lista de ajustes.
6. **Voseo en mensajes de captura:** 22 textos («Podés reintentar») en `WorkoutMediaCaptureController` y `WorkoutMediaRepository`.
7. **Precisión (motor, fuera de esta ronda).** La reserva FALLÓ (SQUAT 2/4, ROW 3/5) y NINGUNA salida llega a confianza ALTA. Por eso
   «Usar» y «Corregir» de un toque no aparecen nunca en la práctica: la persona verá «≈ N» o «no concluyente». Queda como pista
   aparte (ver `ESTADO.md`).

## Decisiones de esta ronda
- **D35. Ley 1 también para el vídeo.** El análisis nunca añade filas a la tarjeta de la serie.
  - En el anverso, el resultado vive en un chip compacto en la línea de «REPS», a la derecha.
  - Sus acciones se abren en un menú anclado, encima de todo.
- **D36. El Modo visual es donde se graba y se revisa el vídeo de la serie.**
  - Las miniaturas llevan el estado del análisis.
  - La línea inferior pasa a ser la del análisis, con la acción dentro de la frase (ley 3).
  - En «Vídeo», grabar y «Expandir» abren el grabador de técnica CON EL ASPECTO de la vista expandida (G3): crece desde la tarjeta y se
    pliega a ella.
- **D37. El descanso** usa la misma pieza compacta que el Modo visual, sin cambiar la altura de la tarjeta de descanso.
- **D38. Perfil:** «Técnica en vídeo» pasa a ser un módulo con contenido (últimos vídeos con su estado y su número de vídeos
  analizados) que abre el hub. El hub gana una cabecera con resumen y una tira de vídeos recientes.

- **D39. El modo degradado nunca baja la pose de 20 Hz.**
  - El diagnóstico del 20-oct (`INFORME_CONFIANZA_20261020.md`) encontró la causa principal del «cero ALTA»: con poca RAM o con el
    móvil caliente, el stride 2 se aplicaba también a 25 y 30 fps. Eso deja 12,5 y 15 Hz → `LOW_FPS` → BAJA, aunque la puntuación
    sea 1.
  - En el banco, sin ese veto, las ALTA de ajuste pasan de 5 a 11, todas en rango.
  - La regla nueva: degradar solo si la pose sigue en ≥ 24 Hz (20 Hz más un 20 % de margen).
  - No se toca ningún umbral, peso ni fórmula de confianza. La reserva está gastada: la validación de precisión queda pendiente de
    una reserva nueva o del teléfono.
  - El resto de las propuestas del informe (DIP frontal, CURL con barra, ventana del banco y desglose publicado) esperan datos
    nuevos.

- **D40. Con confianza MEDIA también se ofrece aplicar el número** (el usuario, 20-oct: «Ofrecer "Usar ≈ 8"»).
  - En el menú del chip (UI-2) aparecen «Usar ≈ 8» o «Corregir a ≈ 7», un toque más y nunca automático. El «≈» sigue visible.
  - En el descanso (UI-3) pasa lo mismo: «corregir» dentro de la frase.
  - «Marcar fallo» sigue siendo solo con ALTA.
  - Con BAJA, solo «ver por qué».
  - Motivo: el usuario exige que la función sea USABLE, como las apps que muestran el conteo y dejan corregirlo.
- **D41. Banco completo:** todas las pruebas usan los 1.000 vídeos del banco (el usuario, 20-oct: «Tenemos un banco de 1000
  vídeos. Te ordeno a que los uses todos para las pruebas»). El plan está en `BANCO_COMPLETO.md`.

## Paquetes (Codex Sol 6.1 Max desde el 20-oct; antes, Antigravity con Gemini 3.8 Flash High. Uno a la vez por carril)
| Paquete | Pantalla | Brief |
|---|---|---|
| UI-1a | Modo visual: estado del análisis (miniaturas y línea), «reintentar» y textos sin voseo | `briefs/UI1a.md` |
| UI-1b | Modo visual: el grabador con el aspecto de la vista expandida (G3), creciendo desde la tarjeta | `briefs/UI1b.md` |
| UI-2 | Anverso de la tarjeta: chip del vídeo en la línea de REPS y menú anclado; fuera la fila que crece | `briefs/UI2.md` |
| UI-3 | Descanso (tarjeta y pantalla completa): la misma pieza compacta | `briefs/UI3.md` |
| UI-4 | Perfil (módulo con datos) y hub (cabecera y vídeos recientes) | `briefs/UI4.md` |
| M1 | Motor (carril `C:\kw\vt-b`): el modo degradado ya no baja la pose de 20 Hz (D39) | `briefs/M1.md` |
| VT11a | Banco completo (carril `C:\kw\vt-c`): manifiesto congelado, conductor de la extracción y evaluador (D41) | `briefs/VT11a.md` |

El orquestador revisa cada entrega: diff, pruebas, PNG y emulador (`Medium_Phone`, con cámara virtual) o teléfono. Integra en
`feat/vt-ui` con `--no-ff` y, al final, propone la integración en `master`.
