# Arquitectura Zustand en KPKN

## ¿Qué es Zustand?

Zustand es una librería de gestión de estado para React: ligera, sin boilerplate y con soporte para persistencia y middleware. En KPKN reemplaza el antiguo uso de `useState` + `useLocalStorage` + Context API para el estado global.

## Stores en la app

| Store | Persistencia | Contenido |
|-------|--------------|-----------|
| **settingsStore** | ✅ IndexedDB | Preferencias, onboarding (hasSeenWelcome), tema, unidades, API keys |
| **programStore** | ✅ IndexedDB | Programas de entrenamiento, programa activo |
| **workoutStore** | ✅ IndexedDB | Historial de entrenamientos, sesión en curso, cola de sync |
| **bodyStore** | ✅ IndexedDB | Progreso corporal, análisis BodyLab, datos biomecánicos |
| **nutritionStore** | ✅ IndexedDB | Logs de nutrición, despensa, base de alimentos, plan IA |
| **wellbeingStore** | ✅ IndexedDB | Sueño, agua, bienestar diario, cuestionarios, tareas |
| **exerciseStore** | ✅ IndexedDB | Lista de ejercicios, playlists, jerarquía muscular |
| **mealTemplateStore** | ✅ IndexedDB | Plantillas de comidas |
| **uiStore** | ❌ En memoria | Vista actual, modales, IDs de edición, toasts, triggers |
| **authStore** | ❌ En memoria | Usuario Supabase, sesión, autenticación |

## Flujo de datos

```
┌─────────────────────────────────────────────────────────────┐
│  AppProvider (AppContext.tsx)                                │
│  - Suscribe a TODOS los stores Zustand                       │
│  - Expone state + dispatch vía useAppState / useAppDispatch  │
│  - Bridge para componentes que no usan stores directamente   │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ useAppState()   │  │ useSettingsStore │  │ useUIStore      │
│ (Context)       │  │ (directo)       │  │ (directo)       │
│ Para componentes│  │ Para componentes│  │ Para componentes│
│ que necesitan   │  │ que solo usan   │  │ que solo usan   │
│ muchos datos    │  │ settings         │  │ UI state        │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Persistencia (IndexedDB)

- **storageAdapter**: Mapea cada campo del store a una clave en IndexedDB.
- **createPersistMultiKeyStorage**: Envuelve el adapter con `createJSONStorage` de Zustand para compatibilidad con la API de persist.
- Los datos se guardan en `storageService` (IndexedDB + fallback a Capacitor Preferences para migración).

## Middleware usado

- **persist**: Persiste el estado en storage asíncrono.
- **immer**: Permite mutaciones "in-place" en los updaters (`set(state => { state.foo = x })`).
- **partialize**: Define qué campos se persisten (p. ej. excluir `_hasHydrated`).

## Hidratación

Cada store persistido tiene `_hasHydrated: boolean`. La app espera a que todos estén hidratados antes de mostrar contenido (`isAppLoading`). Esto evita flashes de pantallas de bienvenida o datos vacíos.
