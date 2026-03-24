# Plan Maestro de Migración PRO+ (PWA → React Native)

> **VISIÓN "MILLIONAIRE"**: Esta no es una simple migración 1:1. Es una evolución arquitectónica. El objetivo es que la aplicación resultante en React Native no solo iguale a la PWA, sino que la supere en rendimiento, fluidez y robustez aprovechando las capacidades nativas exclusivas (JSI, MMKV, SQLite, Reanimated y módulos de IA en dispositivo). Solo migraremos el código *activamente utilizado*, eliminando la deuda técnica.

---

## 🚀 PASO 1: Poda de Código Muerto y Mapa de Dependencias Reales

### 🧠 Contexto (Explicación Humana)
Antes de mudar muebles a una mansión nueva, botamos la basura. La PWA tiene componentes y servicios que existen pero nunca se importan, o funciones legadas que ya no se usan. Este paso asegura que solo migremos el "Happy Path" y las lógicas nucleares comprobadas. 

### 🤖 Prompt Técnico PRO+ (Para la IA)
```text
**Rol**: Arquitecto de Software React Native Senior.
**Tarea**: Realizar un análisis de Abstract Syntax Tree (AST) o un tracing profundo de imports partiendo desde `App.tsx` y los entry points de rutas principales del PWA (`c:\Users\valen\Downloads\kpkn-fit-(beta-test)\`).

**Acciones obligatorias**:
1. Mapea todas las exportaciones (funciones, componentes, hooks, servicios) que **SE UTILIZAN** en la ejecución real de la PWA.
2. Genera una lista negra estricta de archivos/funciones "huérfanas" (dead code) para **NO** migrarlas a React Native.
3. Extrae las dependencias compartidas reales entre `packages/shared-domain` y el código legacy, asegurando que `apps/mobile` solo consuma los módulos verificados.
4. Entrega el resultado como un archivo `MIGRATION_WHITELIST.md` que contenga un árbol exacto de jerarquía jerárquica de qué archivos PWA deben ser mapeados a RN, omitiendo el resto.
```

---

## 🚀 PASO 2: Arquitectura de Persistencia Native-First (Micro-Databases)

### 🧠 Contexto (Explicación Humana)
La PWA usa IndexedDB/localStorage. En una app nativa nivel Dios, cargar bases de datos inmensas de alimentos, WikiLab o ejercicios en memoria RAM de JS causa cierres inesperados (OOM crashes) y bajones de FPS. Vamos a particionar el almacenamiento: estado rápido a **MMKV** (lectura en milisegundos) y catálogos estáticos pesados a **SQLite** o motores locales optimizados en lugar de hidratar JSONs enormes en memoria, asegurando que el estado del usuario viva independiente de los catálogos.

### 🤖 Prompt Técnico PRO+ (Para la IA)
```text
**Rol**: Ingeniero de Datos en React Native.
**Tarea**: Reimaginación del motor de persistencia y bases de datos estáticas en `apps/mobile`.

**Acciones obligatorias**:
1. Implementa **react-native-mmkv** como storage adapter para las tiendas de Zustand críticas (`settingsStore`, `bodyStore`, `wellbeingStore`, `authStore`), reemplazando las implementaciones puente o AsyncStorage lento.
2. Diseña un pipeline de carga para las bases de datos masivas (Alimentos desde `packages/shared-domain`, Ejercicios, WikiLab). Si los JSON pesan más de 3MB, implementa un motor basado en `react-native-quick-sqlite` o un índice cargado dinámicamente que permita búsquedas instantáneas sin bloquear el JS Thread.
3. Asegura que los stores pesados (`workoutStore` con historial largo) tengan paginación local en disco.
4. Entrega el código de los nuevos Adapters y actualiza el archivo de inicialización principal (`bootstrapStore.ts` o equivalente) para manejar la carga asíncrona robusta. Revisa y evita regreciones en el mapeo clave-valor heredado.
```

---

## 🚀 PASO 3: Migración Matemática del Sistema AUGE (Thread-Safe)

### 🧠 Contexto (Explicación Humana)
El sistema AUGE (baterías, fatiga, recuperación) es el cerebro científico de la app. En PWA usábamos un Web Worker para que los cálculos matemáticos de cientos de sesiones no congelaran la pantalla. En React Native, migraremos esto a **JSI** o, como mínimo, un `InteractionManager` con batches asíncronos para que los cálculos masivos ocurran en un sub-hilo mientras la interfaz corre a 120 FPS ininterrumpidos.

### 🤖 Prompt Técnico PRO+ (Para la IA)
```text
**Rol**: Especialista en Rendimiento y Algoritmia React Native.
**Tarea**: Migración 1:1 de los algoritmos de `services/auge.ts` y relacionados (`fatigueService.ts`, `recoveryService.ts`, `volumeCalculator.ts`) garantizando que el UI Thread de React Native no se bloquee jamás.

**Acciones obligatorias**:
1. Analiza el Web Worker original completo de la PWA. Traslada cada fórmula (decaimientos exponenciales, supercompensación, MRV/MEV) a utilidades matemáticas tipadas en `apps/mobile/src/services/`.
2. Implementa un puente asíncrono robusto (ej. usando `InteractionManager.runAfterInteractions()` iterativo o un Worklet de `react-native-reanimated`) en `computeWorkerService.ts` de la app móvil.
3. Crea un test de estrés (benchmark) simulando un usuario con 500 sesiones de historial. El cálculo de "Readiness" diario debe resolverse en background emitiendo un estado "loading" sin tirar ni un solo frame de la UI.
4. Genera una batería de test Jest (`npm run mobile:test`) asegurando que los mismos inputs producen el mismo JSON de output que en la PWA.
```

---

## 🚀 PASO 4: The "Liquid Glass" UX Upgrade (Interfaz y Experiencia)

### 🧠 Contexto (Explicación Humana)
Una "app de millones" se SIENTE de millones. No basta con clonar los layouts de la PWA; en React Native debemos usar el motor nativo de animaciones (Reanimated 3) y manejo de gestos para lograr la estética dinámica e inmersiva que hemos definido como *"Material Liquid Glass White"*. Todo debe reaccionar al toque orgánicamente.

### 🤖 Prompt Técnico PRO+ (Para la IA)
```text
**Rol**: Experto en UI/UX y Animaciones Fluidas (Reanimated 3).
**Tarea**: Refactorización de componentes visuales clave (Onboarding, WorkoutSession, Dashboards de Nutrición y Progreso) bajo la estética "Liquid Glass" de la app de producción.

**Acciones obligatorias**:
1. Transforma componentes de navegación y feedback (Modales, Bottom Sheets, Drawers AUGE) para usar `react-native-reanimated` y `react-native-gesture-handler`. Se prohíbe el uso severo del Animated bridge estándar o re-renderizados pesados en Zustand para transiciones visuales de progreso de sesión.
2. Refactoriza el "Session Editor" y "Workout History": implementa listas nativas de alta performance (ej. `@shopify/flash-list`) en vez de FlatList tradicional para evitar memory warnings al scrollear historias largas o bases de datos de ejercicios.
3. Integra el hook nativo de colores M3 (`useColors()`) que obedece las reglas de `StyleSheet` y los `design-tokens`, erradicando remanentes mentales de TailwindCSS de la PWA en el workspace móvil.
4. Asegura integración perfecta del teclado nativo (`KeyboardAvoidingView` interactivo) para la inserción frenética de datos numéricos en RPE/RIR durante las series de gimnasio.
```

---

## 🚀 PASO 5: Módulo Nativo Integral de Nutrición e IA On-Device

### 🧠 Contexto (Explicación Humana)
En la PWA usábamos APIs externas u opciones limitadas. En la app móvil tenemos a FunctionGemma corriendo directamente en el procesador del teléfono Android. Migraremos la capa de nutrición asegurando que la IA en dispositivo, los catálogos locales chilenos de comida y el router de IA en la nube (Gemini/GPT) funcionen como un ecosistema híbrido perfecto.

### 🤖 Prompt Técnico PRO+ (Para la IA)
```text
**Rol**: Ingeniero de Integración IA Móvil.
**Tarea**: Consolidar el pipeline multiproveedor de IA (`aiService.ts`) y el modelo nativo (`services/localAiService.ts`) asegurando paridad total con los dominios compartidos de nutrición.

**Acciones obligatorias**:
1. Finaliza la integración de fallbacks: Flujo Primario (Local IA `kpkn-food-fg270m-v1` por native bridge java) -> Flujo Secundario (Gemini via Cloud) -> Flujo Terciario (Fallback heurístico de base de datos chilena en `packages/shared-domain`).
2. Migra e integra completamente `parseFreeFormNutrition()` para parseo robusto en el `RegisterFoodDrawer` nativo.
3. Revisa los stubs actuales en RN: `aiService.ts` debe contener todos los prompts, orquestación y parsers de respuesta JSON presentes en la PWA original (generación de rutinas, chat coach, etc.).
4. Diseña una interfaz tipada rigurosa en `shared-domain` que certifique que el output del Local IA y de Gemini coincidan al 100% en formato de Macros y Micronutrientes antes de impactar el persist storage.
```

---

## 🚀 PASO 6: Sync en Background y Data Export

### 🧠 Contexto (Explicación Humana)
La PWA sincronizaba cuando el navegador estaba abierto. Un usuario "PRO+" asume que si cambia el dispositivo, no perdió ni una flexión de brazo. Vamos a habilitar sincronización de fondo robusta hacia Supabase e implementaremos un flujo impecable hacia Google Drive nativo (usando APIs del sistema operativo Android), para que se sienta a prueba de balas.

### 🤖 Prompt Técnico PRO+ (Para la IA)
```text
**Rol**: Ingeniero Backend y Offline-First Native.
**Tarea**: Implementar sincronización resiliente y backup nativo de state JSON.

**Acciones obligatorias**:
1. Implementa `@react-native-google-signin/google-signin` acoplado al Google Drive REST API. Transforma toda la data centralizada de las micro-databases de Zustand en un snapshot comprimido en GZIP que se suba a la nube silenciosamente de forma calendarizada.
2. Expande el `supabaseSyncService.ts` de la móvil (actualmente muy pequeño vs la PWA). Escribe una lógica de sincronización offline-first: "Guarda local, encola mutation (WatermelonDB style o Zustand queue), empuja cambios a Supabase silenciosamente cuando hay red, manejo de conflictos last-write-wins".
3. Vincula el Background Sync nativo de Android (`WorkManager` asíncrono si es necesario o Fetch en background del OS) para asegurar push y updates de widgets sin abrir la aplicación.
```

---

> **Nota para IAs Operativas:** Cuando ejecuten este plan, no inventen componentes que no se requieren, apéguense a la arquitectura limpia de `apps/mobile/` y `packages/`. Recuerden siempre testear que los inputs generen salidas matemáticas **estrictamente idénticas** (Test de Paridad) a la PWA. El mandato del usuario no es solo migrar, es perfeccionar a un estado "PRO".
