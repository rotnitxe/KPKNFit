# Ola K · Correcciones casi finales del asistente (2026-10-12)

Se suma a `docs/wizard-v3/BRIEF_COMUN.md`, que sigue mandando: entorno, Gradle por `C:/kw/tools/gradle_slot.sh` en segundo plano,
pruebas mínimas, hojas de render, diseño no negociable, commit por rutas y sin push. Cada paquete tiene su brief `K<n>.md`.

## Lo que pidió el usuario (sus palabras, resumidas solo en la forma)
1. **Músculos a priorizar:** Bíceps, Tríceps, Antebrazos y Espalda se ven bastante mal y no representan bien el músculo. Hay que
   mejorarlos y unir Bíceps y Tríceps en «Brazos», con un símbolo de brazo, que es más simple de representar.
2. **Variantes principales de Powerbuilding:** se despliegan de forma demasiado engorrosa. No debe ser una lista larga vertical, sino un
   mosaico o imágenes de los patrones que, al tocarlos, desplieguen las variantes. Debe sentirse visual e interactivo; no hace falta
   dibujar cada variante.
3. **Portadas de «Elige tu programa»:** siguen siendo demasiado genéricas. Cada una debe tener su identidad y verse hermosa; no hace falta
   que todas sigan una misma lógica visual.
4. **Lista de ejercicios de cada día:** fuera las miniaturas. Al tocar un ejercicio se abre un overlay con su información:
   - su IMAGEN del catálogo; si no la hay, «Sin imagen» (otro agente las rellenará);
   - la descripción;
   - la implicación muscular y articular.
   Todo DEBE RESPETAR EL CATÁLOGO. NO SE CREAN DESCRIPCIONES ADICIONALES.
5. **«¿Cuál es tu peso objetivo?»:** al elegir el peso con la regla, el botón para seguir no funciona y no deja avanzar.
6. **«¿Quieres añadir tus últimos pesajes?»:** más visual y menos «formulario»; cada pesaje puede llevar % de grasa y % de músculo; «no
   añadir» pasa a ser un botón «Omitir» mejor colocado.
7. **Intro de RINGS** («Tus RINGS, tu recuperación de un vistazo»):
   - Debe ser un overlay animado, NO dentro del asistente, como las demás animaciones.
   - Arranca con «Un último paso para comenzar. Necesitamos conocer tu nivel de fatiga y el estado de tus músculos para tus RINGS».
   - Sigue con la animación de los RINGS y qué es cada uno.
   - Termina con invitaciones animadas a mover cosas, para entender cómo se calibra cada anillo.
   - VISUAL E INTERACTIVO. No debe sentirse como un paso, sino como la introducción a los RINGS.
8. **«¿Cuántas series hiciste por grupo?»:** nada de desplegar en vertical. Mejor uso del espacio, una lista más ligera a la vista y las
   miniaturas de brazo arregladas, que hoy se ven raras.
9. **«¿Cómo sentiste la intensidad?»:** las 4 barras de «Suave» a «Muy dura» son una pésima decisión de diseño. Pasa a un deslizador
   que, hacia «Muy dura», enciende un fuego o destello de fuego: una imagen que se transforma con el deslizador.
10. **«Quiero ajustar mis músculos a mano»:** la lista se siente muy pesada. Quizá cada músculo sin deslizador y, al tocarlo, que
    aparezca el deslizador; mejor desplegado, con criterio de UI.
11. **«Revisión y activación»:** es una de las partes más críticas y feas. NO debe ser un paso, sino un overlay interactivo con el
    resumen de todo: más motivador y con un mensaje por apartado. Hoy parecen pantallazos estáticos, aburridos y mal diseñados.
12. **«Cómo funciona»:** abre una hoja LARGUÍSIMA y densa con la descripción y la semana. Es horrenda y hay que eliminarla. En el resumen
    del punto 11 debe haber una tarjeta animada con el resumen del programa elegido.

## Reglas de la ola
- **Diseño:** símbolos e ilustraciones animadas sobre la página negra, sin tarjetas, en los pasos.
  - Los overlays (puntos 4, 7 y 11) son superficies de vidrio u hojas a pantalla completa, como `ModuleCompleteOverlay` y
    `WizardGlassNote`.
  - La «tarjeta animada del programa» del punto 12 la pidió el usuario: es la excepción y vive dentro del overlay.
- **Textos:** español neutro y sin género gramatical; títulos ≤ 44 caracteres; subtítulos ≤ 100.
- **Etiquetas de prueba:** las que ya existían se conservan salvo que el brief diga otra cosa. Si una prueba deja de valer porque el
  diseño cambia (p. ej. ahora hay que tocar antes de ver un deslizador), adáptala y explícalo en el informe.
- **`androidTest`:** no se ejecuta. Si tu cambio rompe su compilación o un texto que busca, arréglalo.
- **Ruta y revisión:** solo el paquete que lo diga toca la ruta (`SetupStepGraph`, `SetupWizardModels`) o `REVISION`.
- **Visuales:**
  - Hoja de render con `KPKN_RENDER_DIR=C:/kw/shots/v3/<paquete>`.
  - Mira cada PNG con Read y haz como mucho 3 rondas.
  - El orquestador revisa los PNG antes de integrar y prueba en el teléfono del usuario.
