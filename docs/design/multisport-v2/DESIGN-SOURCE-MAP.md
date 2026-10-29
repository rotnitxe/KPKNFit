# KPKN multideporte v2: fuentes, medidas y límites

## Alcance vigente: solo ampliaciones

La portada es `index.html#wizard-sports`. `gallery.html` contiene únicamente propuestas nuevas y explica **Qué se agrega** fuera del producto. El baseline de Home y su comparación son controles internos, excluidos de la galería. Las superficies actuales de Home, Training, Nutrición, Cuerpo y tarjeta de serie no son entregables nuevos.

Se descartaron como novedad los controles que el código ya contiene: días/minutos/lugares/material de `screens/onboarding/entreno/EntrenoWeekLayoutStep.kt`, RM de `screens/workout/components/ExerciseRmSheet.kt`, bloques/objetivos cardio de `screens/sessioneditor/components/CardioEditorCard.kt` y fases/GPS/manual de `screens/workout/CardioLiveCard.kt`.

Por eso el wizard añade **varios objetivos/experiencias/prioridades** y **ventanas inicio/fin/sede**, más una clase editable que puede quitarse. Running compara contexto aislado/tras fuerza, cycling propone brick con T2, y Performance Lab pone al centro una regla revisable y comparabilidad técnica. La referencia de e1RM queda plegada.

## Mapa de ampliaciones → implementación revisable

| Ampliación | Implementación | Contrato observable |
|---|---|---|
| Perfil deportivo múltiple | `new-core.js`: SPORT_META, wizardNew, profile | La selección se propaga por nivel, objetivo, prioridad y revisión; cero deportes bloquea; un deporte no presenta los demás como elegidos |
| Ventanas y sedes | `new-core.js`: planForSelection, windowMinutes; `app.js`: windows | Inicio y fin por día; sede por ventana; no se comprime la dosis para simular que cabe; faltas de recurso/sede se explican |
| Compromiso fijo editable | fixedCommitment, commitmentBlock | Nombre/día/inicio/fin editables; quitar/incluir cambia el plan y su total |
| Fuerza + MMA en el mismo wizard | SPORT_META.mma, classDiscipline, planForSelection | Experiencia/meta/prioridad de MMA propias. Vinculación humana explícita de la clase o externo/sin objetivo. Sin vínculo se bloquea y se pide definir la clase; con vínculo se conserva una vez y se colocan solo apoyos físicos, sin receta de combate |
| Powerlifting sin barra | powerlifting-access, hasCompetitionMaterial | Preparación transferible, buscar acceso u otro enfoque; sustitutos no se presentan como movimientos de competición |
| Planner compartido | week, compatibility, conflict, alternative | Bloques por disciplina, clases fijas, comparación de cruce y alternativa elegida; controles de mover además de arrastre |
| Regla de fuerza | new-core.js: strength | Dos cumplimientos comparables; incremento editable; cambio técnico impide aceptar automáticamente; referencias por variante separadas |
| Complex de WL | sports.js: weightlifting | Eslabón activo editable; carga compartida; reps/ronda y volumen se calculan de la cadena |
| Skills e isometría | skills | Árbol visual por variante; segundos guardados con su variante |
| Strongman | strongman | Medley con distancia/carga de cada implemento; completar un tramo suma una vez |
| Armwrestling | armwrestling | Lado, ángulo, sostén, carga y agarre conservados por registro |
| CrossFit | crossfit-amrap/emom/fortime | Tres estructuras y scoring distintos: rondas+reps, cumplimiento/minuto, tiempo o pendientes al cap |
| HYROX | hyrox | Ocho pares running/estación, dosis específicas y transición |
| Striking | striking | Timer de round y secuencia de combo; selección de golpe y registro de combo |
| Grappling | grappling | Mapa de posición/transición, historial de orden y rolling con resultado técnico |
| MMA | mma | Camp por fases y apoyos físicos alrededor de clases fijas |
| Running híbrido / brick | new-core.js: running, cycling | Contexto previo preservado; bici/T2/running separados; suma explícita del tiempo |
| Trail / remo / natación / triatlón | sports.js | Terreno/elevación; parcial /500 m; largos × piscina; disciplinas y T1/T2 en un evento |
| Escalada / pruebas | climbing, athletics | Intento, presa y encadene; mejor sprint por mínimo y mejor salto por máximo |
| Tenis / pádel / equipo | racket, team, points | Deuce/ventaja frente a punto de oro; tie-break y deshacer; minutos/acciones propios sin fabricar ubicaciones |
| Agregado y origen | summary, training-energy, import | Una sesión con varios bloques; actividad relacionada elige una fuente antes de sumar gasto; sin doble conteo automático |
| Sensores/Huawei | sensors, huawei | Fresco/antiguo/perdido separados, dato ausente sin inventar; archivo/manual sin promesa de compatibilidad |

Todas las vistas proceden del mismo renderer del prototipo; las miniaturas de `gallery.html` son iframes locales del escenario. `app.js` maneja estado local reiniciable. No hay llamadas de red, CDN ni servicios externos.

`boot.js` carga y comprueba las dependencias locales en orden; confirma contenido, ruta, estilos y fuentes antes de avisar que el renderer está listo. `gallery.js` limita a dos los inicios de previews cercanos al viewport, descarga los alejados y muestra carga/error/reintento. El modo preview no accede al estado editable guardado. Son cambios de infraestructura de revisión, sin modificar los componentes de `app.css` ni `kpkn.css`; detalle y regresiones en [INFRASTRUCTURE-QA.md](./INFRASTRUCTURE-QA.md).

## Tesis antes de construir

Visual: negro puro, tinta cálida y superficies nativas; la torre luminosa es la firma de KPKN y la información conserva aire, jerarquía y potencia.

Contenido: componentes KPKN nativos como contrato visual; perfil deportivo múltiple, wizard, planner y registros nuevos. Home / Training / Nutrition / Body permanecen como shell reconocible.

Interacción: continuidad al elegir el día y mover sesiones; cadenas y recorridos que expresan la estructura del deporte; hojas de experto y expansión con transición, con movimiento reducido completo.

## Referencia física

- Imagen real: `artifacts/app-consolidation-20261017/device/main-activity.png`, copiada sin alterar a `references/native-home.png`.
- 1344 × 2992 px a densidad 3 equivale a 448 × 997,333 CSS px.
- `comparison.html` presenta ambas superficies a ese tamaño. `baseline.html` no añade dispositivo, borde de teléfono ni cabecera de revisión dentro del producto.
- La representación de barra de estado (54 CSS px) y barra de gesto (24 CSS px) es una aproximación al área del sistema de la captura. No pertenece al contenido de Home.

## Mapa nativo → HTML

Rutas relativas a `android-native/app/src/main/java/com/example/kpkn/`.

| Fuente nativa | Componente HTML | Medidas y comportamiento conservados |
|---|---|---|
| `screens/home/design/HomeMasthead.kt` | `K.masthead`, `.masthead` | gutter 24; estado+20 arriba; avatar 48; saludo Syne 800 30/36; gap 6; programa Inter 600 15/20 a 18 de separación |
| `screens/home/design/HomeRhythm.kt` | `.week-module`, `.module` | 20 entre cabecera y módulo; 12 entre módulos |
| `screens/home/HomeScreen.kt` | `K.home` | semana y sesión en un único módulo; cabecera editorial al aire; ningún topbar artificial al iniciar |
| `ui/design/KpknSurface.kt` | tokens CSS | canvas #000; module #111; inset #1A1A1A; border blanco 7%; divider 6%; radio módulo 24 |
| `ui/design/KpknModule.kt` | `.module`, `.module-header` | interior 20 laterales, 18 arriba, 20 abajo; cabecera 48 con acción |
| `screens/home/design/HomeWeekStrip.kt` | `K.week`, `.day` | 7 columnas sin cajas; letra 13/18; número Syne 700 17/22 en círculo de 40 solo hoy/selección; marcas 6, descanso 8×2; subrayado 18×2, transición 420 ms |
| `screens/home/design/HomeSessionHero.kt` | `K.hero` | kicker 13/18; título Syne 700 24/28; siguiente Inter 15/21; gaps 8, 12 y 20; CTA 56 |
| `screens/home/HomeRingsSection.kt` | `K.rings`, `.tower-wrap` | cabecera con info, headline Syne 22/26, torre en área 300:230, leyenda Músculos/Energía/Columna |
| `ui/components/rings/KpknTowerRings.kt` | `K.tower` | TowerShapes exactas: inferior outer (cy70 rx38 ry12), inner (cy68 rx33 ry8.5); medio (55,28,9)/(53.5,24,6.5); superior (42,18,6.5)/(41,14.5,4.3). Bandas, hueco interior desplazado y degradados luminosos nativos |
| `ui/components/rings/KpknTowerRings.kt` | SVG de RINGS | estructura #5E7EC4→#B9CFFF; músculos #C7704A→#FFC2A3; energía #C9A04A→#FFE7A8; halo y destello 6 s; `null` = discontinuo y sin porcentaje |
| `ui/components/icons/KpknLineIcons.kt` | `K.icon` | paths exactos de Home, Training, Nutrition y Body; retícula 24, trazo 1.75 y extremos redondos. Body es cinta métrica vigente |
| `ui/components/KpknBottomBar.kt` | `K.nav` | 64 alto; iconos24; etiqueta Inter500 11/14; gap4 y punto4 con gap3; isla lateral12 y bottom32 incluyendo área del gesto |
| `ui/design/KpknTopIsland.kt` | islas y hojas | la isla superior solo pertenece a estados desplazados/contextuales; no sustituye la cabecera de Home |
| `ui/design/KpknButtons.kt` | `.pill`, `.icon-button` | CTA56, icon20 dentro de círculo40 con objetivo48; escala pulsación .97; secundaria tinta24% |
| `screens/onboarding/design/WizardDesignTokens.kt` | fuentes y tinta | Inter y Syne locales desde `res/font`; #F2EEE6, #C4BFB6, #8E8980; OFL copiada junto a fuentes |

## Fuentes de interacción ya aprobadas

`docs/screens-v1/proposals/sesion-en-vivo/README.md` identifica las propuestas aprobadas y las leyes posteriores: tarjeta de serie estable, intensidad obligatoria al mínimo, modo visual como expansión separada, cadenas de avanzado y resumen Hoy/Levantamientos/Notas. Se usa como contrato funcional y de composición de sesión; este paquete no altera esos archivos.

## Límites de evidencia

Este paquete es un prototipo HTML local. La captura de referencia sí es Android; la reconstrucción y las extensiones no son una app nativa instalada ni una aprobación del diseño. Las animaciones de halo y personaje son traducciones SVG/CSS revisables, no equivalencia de motor de render Compose. No se han corrido Gradle ni adb para este paquete.

`check-prototype.mjs` verifica render local y contratos de estado/cálculo; `QA-LOCAL.json` contiene los resultados. El navegador, la interacción y el overflow se revisan por el orquestador con su arnés separado. Ninguna comprobación de Node acredita aprobación visual o hardware.
