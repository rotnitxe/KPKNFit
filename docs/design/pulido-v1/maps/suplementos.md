# Mapa · SUPLEMENTOS (punto 24) · prefijo `sp`

Archivos: `src/scenes-sp.js`, `src/sp.css`. Ruta de la app: `android-native/app/src/main/java/com/example/kpkn/`.

## 1. Lo que hace la app HOY
- `screens/nutrition/design/NutritionSupplementsModule.kt`: módulo «Suplementos» con (a) cafeína del día como arco frente a su máximo
  (`CaffeineLimitEngine`: 6 mg/kg con tope 400; 200 mg embarazo/lactancia; menores 2,5 mg/kg; franja útil 160–250 mg) y (b) una fila check de
  creatina («x de y g · racha»). Tocar el check registra 5 g rápidos (`NutritionViewModel.quickAddCreatine`, comida «Creatina Monohidrato»,
  id `gen106`, tentempié) o abre el seguimiento si ya está hecha.
- `screens/nutrition/components/SupplementTrackingComponents.kt` (`CreatineSaturationOverlay`) y `domain/nutrition/CreatineSaturationEngine.kt`:
  hoja de pauta de carga/gradual; estado en `Settings.creatineTracking` (protocolo, fecha, `onboardingSeen`).
- **No existe** modelo ni tabla de suplementos: una toma es un `NutritionLog` con un `FoodItem` que lleva `creatineG`/`caffeineMg`
  (`data/models/NutritionModels.kt`). Cafeína y creatina son solo columnas de la comida.
- **No hay recordatorios de suplementos.** Sí hay de comida (`domain/nutrition/NutritionReminderPlanner.kt`, alarma diaria a hora de reloj).
- Huella: `domain/nutrition/DriTargets.kt` (límites superiores; Magnesio/Folato/Niacina/Vit. E solo-suplemento) y `nutrients/NutrientId.kt`
  (Vit. D, Magnesio, Zinc, Hierro, EPA+DHA y cafeína ya existen como nutrientes de la instantánea de `LoggedFood`).
- Inicio: `screens/home/` (tarjeta «Nutrición de hoy»; hoy sin nada de suplementos).

## 2. Inventario escena → pieza
| Escena | Pieza actual / destino |
|---|---|
| `sp-modulo`, `sp-vacio`, `sp-hora`, `sp-completo`, `sp-techo` | `NutritionSupplementsModule` (reescrito) |
| `sp-aviso`, `sp-ajustar` | hojas nuevas (`KpknSheet`) |
| `sp-anadir` | hoja nueva con catálogo (14 + «crear uno propio») |
| `sp-alta`, `sp-propio`, `sp-edicion`, `sp-baja` | pantalla/ruta nueva de configuración |
| `sp-detalle`, `sp-detalle-cafeina` | pantalla nueva de detalle (historial 30 días) |
| `sp-creatina` | `CreatineSaturationOverlay` (mismos textos, gris plano vigente) |
| `sp-inicio`, `sp-inicio-hecho`, `sp-rapida` | tarjeta «Nutrición de hoy» de Inicio + hoja |
| `sp-notif` | notificación con acciones (`services/nutrition/`) |

Las escenas son **un mismo mini-app interactivo**: tocar registra con toast «Deshacer», mantener 0,5 s abre el ajuste (se puede subir la dosis
hasta pasar de 400 mg), «+» abre el catálogo, el nombre abre el detalle, el engranaje abre la configuración, «Dejar de tomar» confirma.

## 3. Propuestas y decisiones
- **new · Pila**: lista de suplementos que tomas cada día, ordenada por momento (7 momentos: al despertar … antes de dormir). Un toque = dosis habitual.
- **keep · Cafeína y creatina**: siguen como totales del día (barras finas en tinta, como el resto de Nutrición). **out**: el medidor de arco.
  Razón: unificar con el lenguaje de arreglos-v1 y dejar sitio a la pila. La cafeína sigue mostrando el total de comidas+suplementos aunque no haya pila.
- **new · Registro**: un toque con la dosis visible en el botón (evita registrar a ciegas); mantener = ajustar dosis (−/+, ½·Habitual·×2) y hora
  (ahora, −30 min, −1 h, esta mañana). Deshacer en toast 5 s y «Quitar» en el detalle. Teclado/accesibilidad: el botón también responde a un toque normal de lector de pantalla.
- **new · Catálogo (justificación)**: ISSN para rendimiento (creatina, cafeína, proteína, beta-alanina, citrulina) y NIH ODS/EFSA para micronutrientes
  (vit. D, omega-3, magnesio, zinc, hierro, multivitamínico, electrolitos); ashwagandha y melatonina por uso frecuente y sueño/estrés. Cada uno con
  unidad, dosis típica, momento por defecto, techo (si hay fuente) y etiqueta de evidencia (sólida / moderada / según necesidad / limitada).
  Excluidos: BCAA, glutamina, quemadores, potenciadores hormonales, mezclas de marca.
- **new · Aviso de techo** (no bloqueo): cafeína 400 mg, vit. D 100 µg, magnesio 350 mg (solo suplemento, como `UpperLimitScope.SUPPLEMENTS_ONLY`),
  zinc 40 mg, hierro 45 mg, EPA+DHA 5 g. «Registrar igual» siempre disponible.
- **new · Inicio**: franja «Suplementos x de y» dentro de «Nutrición de hoy», máximo 3 botones de pendientes + «+N más» que abre la lista; el anillo y
  la proteína se actualizan al registrar proteína (≈ 4,4 kcal/g, estimado); línea «Cafeína de hoy» si hay cafeína en la pila. Sin pila no se muestra.
- **new · Historial corto**: 7 cápsulas en el módulo y 30 días en el detalle; racha de creatina se conserva.
- **new · Recordatorio** opcional por suplemento, con acciones «Tomar 1 g» / «Más tarde».
- **keep · Seguimiento de creatina** (carga/gradual) como fila en detalle y edición.
- Baja = sale de la pila; el historial se queda (cada toma ya es una comida).
- Seguridad: una sola frase general (embarazo, lactancia, medicación → consulta) y el techo con su fuente; sin diagnósticos ni consejos médicos.
- Animación de confirmación: el círculo del suplemento rebota (0,6 s) y las cifras cuentan; versión sin movimiento con `prefers-reduced-motion`.

## 4. Qué exige implementarlo
- **Datos**: definición de suplemento (id de catálogo o propio, nombre, unidad, dosis, tomas, momento, hora de recordatorio, objetivo, notas, orden, activo).
  Room está congelado (30 calibración / 31 nutrición): recomiendo guardarlo como lista serializada en `Settings` (como `creatineTracking`) y mantener
  cada toma como `NutritionLog` (sin tabla nueva). Hay que añadir un marcador de «toma de suplemento X» al `FoodItem`/log para sacar historial,
  rachas y 7/30 días (hoy solo se infiere por `creatineG > 0` o `caffeineMg > 0`).
- **Motor**: catálogo con valores de nutrientes por unidad para alimentar la instantánea (`NutrientId`: VITAMIN_D, MAGNESIUM, ZINC, IRON, EPA_DHA, CAFFEINE,
  PROTEIN/ENERGY). Multivitamínico y electrolitos requieren que el usuario rellene la etiqueta. Comprobación de techo diario por suplemento.
- **Recordatorios**: reutilizar `NutritionReminderPlanner` (una alarma por suplemento con recordatorio) y un canal de notificación con acciones.
- **UI**: reescribir `NutritionSupplementsModule`; hoja de ajuste; hoja de catálogo; pantalla de configuración; pantalla de detalle; franja en Inicio y su hoja.
- Tests: techo por suplemento, racha, ordenación por momento, migración de `creatineTracking`.

## 5. Dudas para el usuario (`ask`)
1. **Alcance del catálogo**: ¿los 14 propuestos o recortar (p. ej. sin ashwagandha, hierro y melatonina)?
2. **Aviso de techo**: ¿avisar y dejar «Registrar igual» (propuesto) o bloquear?
3. **Etiquetas de evidencia** («Evidencia sólida/moderada/limitada»): ¿te gustan o sobran?
4. **Suplementos propios**: ¿que se puedan rellenar los nutrientes de la etiqueta (o escanearla con el lector de códigos) para que sumen a la huella?
5. **Notificación**: ¿botón «Tomar» que registra sin abrir la app, o solo abrir la app?
6. **Proteína en polvo**: ¿cuenta como suplemento (propuesto, suma a macros) o sigue siendo una comida?
7. **Inicio**: ¿máximo 3 botones + «+N más» (propuesto) o una fila horizontal desplazable?
