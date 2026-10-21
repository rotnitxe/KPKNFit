# QA de LV-A en el emulador con las medidas del Z Flip 5 (2026-10-21)

- **Emulador:** `ZFlip5_QA`, 1080 × 2640 a 480 dpi, barra de estado de 45 dp y navegación de 24 dp.
- **APK:** el de LV-A (`449bab59a`), copiado en `C:\kw\shots\emu-lva\apk`.
- **Registros:** `C:\kw\logs\emu-lva-instr*.log` y `emu-lva-story.log`.
- **Capturas:** `C:\kw\shots\emu-lva\files\` y `sheet-*.png`.
- **Renders de Robolectric:** `C:\kw\shots\emu-lva\renders\`.

## Pruebas instrumentadas: 65 ejecutadas, 62 correctas

| Prueba | Resultado | Lectura |
|---|---|---|
| `WorkoutCockpitDeviceTest` | Pasa (6/6) al lanzarla con su paquete real, `screens.workout.components` | El primer fallo fue un error del nombre de clase en el comando del orquestador |
| `SessionRevisionFlowDeviceTest#exerciseMenuOpensRmAndQuickRecords…` | Intermitente | Falló una vez (en 30 s no se registró la serie desde el formulario rápido) y pasó al repetirla. En las capturas, la hoja «Ultrarrápido» se queda en «Registrando…» mientras el descanso ya está abierto detrás: la hoja tarda en cerrarse tras guardar |
| `SessionRevisionFlowDeviceTest#storyFromTheHeaderOpensTheExistingStory…` | **Falla siempre** (3 de 3) | Tras tocar «Historia» en la cabecera, el nodo `story-sheet` existe pero `story-templates` no se ve. En los fotogramas, ninguna hoja aparece en pantalla y la línea sigue en «0/4 · S1» después de registrar con el formulario rápido. En el otro recorrido (`06-live-after-canonical-record`) sí marca 1/4 |

## Revisión visual

**Bien:**
- isla V1 con preparación, S1–S4 y ✓;
- pantalla de 760 dp completa (`19-live-reduced-height-760`);
- B1 corregido: la ficha desplazada ya no superpone el título;
- tarjeta de descanso con «de 4:09 · sugerido / Plan de sesión · 3:00» sin cortes;
- notas migradas hasta el resumen final;
- las siete hojas migradas.

**Detalles:**
- La calculadora RM muestra decimales con punto («112.5», «e1RM: 112.5 kg») en vez de coma.
- El panel «Drenaje de …» tras registrar tapa la parte inferior de la tarjeta y la línea mientras está visible: comprobar que es temporal y no tapa el ✓.

**Destino:** el paquete corto LV-AF (`briefs/LV-AF.md`), después de LV-B.
