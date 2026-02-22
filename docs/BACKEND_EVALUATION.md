# Evaluacion Backend: Supabase vs Firebase para KPKN

## Estado Actual

KPKN es una PWA client-only. No existe backend propio.

- **Social feed** (`services/socialService.ts`): mock completo con datos hardcoded.
  Funciones declaradas: auth, feed, likes, comments, share, leaderboards.
- **Sync** (`hooks/useGoogleDrive.ts` + `services/googleDriveService.ts`): sincronizacion
  manual unidireccional via Google Drive API. Sin resolucion de conflictos.
- **Push notifications**: solo locales via Capacitor. No server-triggered.

## Necesidades Identificadas

| Necesidad                      | Prioridad | Notas                                               |
|-------------------------------|-----------|-----------------------------------------------------|
| Social feed (posts, likes)    | Media     | socialService.ts ya define la API completa como mock |
| Auth (Google, Apple)          | Media     | Prerequisito para social y sync multi-device         |
| Sync multi-device             | Alta      | Google Drive es fragil y manual                      |
| Leaderboards                  | Baja      | Requiere datos agregados de multiples usuarios       |
| Push notifications (server)   | Baja      | Actualmente solo locales                             |

## Comparativa

### Firebase

**Ventajas:**
- Firestore: real-time sync nativo, offline-first con cache local
- Firebase Auth: Google, Apple, email en minutos
- Cloud Functions: logica server-side en Node.js/TypeScript
- FCM: push notifications integradas con Capacitor
- Hosting: deploy estatico gratis
- Ecosistema maduro, documentacion extensa

**Desventajas:**
- Vendor lock-in fuerte (dificil migrar)
- Costos impredecibles a escala (pago por lectura/escritura)
- Queries limitadas (no JOINs, no full-text search nativo)
- Firestore schema-less puede ser problematico para datos complejos como programas

**Costo estimado (1000 usuarios activos):** $0-25/mes (capa gratuita generosa)

### Supabase

**Ventajas:**
- PostgreSQL completo (JOINs, indexes, full-text search)
- Auth integrado (Google, Apple, email)
- Realtime subscriptions (similar a Firestore)
- Edge Functions (Deno/TypeScript)
- Row Level Security (RLS) para permisos granulares
- Open source, self-hosteable, sin vendor lock-in
- Almacenamiento de archivos (fotos de progreso)

**Desventajas:**
- Offline-first menos maduro que Firestore (no tiene cache local nativo)
- Menos plugins Capacitor listos para usar
- Realtime requiere mas configuracion manual
- Comunidad mas pequena

**Costo estimado (1000 usuarios activos):** $0-25/mes (capa gratuita: 500MB DB, 1GB storage)

### Cloudflare Workers + D1

**Ventajas:**
- Latencia ultra-baja (edge computing)
- D1: SQLite distribuido
- Costos muy bajos
- Workers en JS/TS nativo

**Desventajas:**
- Sin auth integrado (hay que implementarlo)
- Sin realtime nativo
- D1 todavia en desarrollo activo
- Mas trabajo manual

## Recomendacion

**Supabase** es la mejor opcion para KPKN por las siguientes razones:

1. **PostgreSQL** encaja mejor con el modelo de datos complejo de KPKN (programas con
   macrociclos/bloques/mesociclos, ejercicios con musculos involucrados, etc.). Firebase
   Firestore obligaria a desnormalizar excesivamente.

2. **Sin vendor lock-in**: al ser open source, se puede migrar a PostgreSQL autohosteado
   si los costos crecen.

3. **Row Level Security**: ideal para social feed (cada usuario solo ve/edita sus datos,
   pero puede ver posts publicos de otros).

4. **Edge Functions en TypeScript**: compatible con el stack actual.

5. **Storage integrado**: para fotos de progreso y meal photos.

### Hoja de ruta sugerida

**Fase 1 - Auth + Sync (reemplaza Google Drive):**
- Supabase Auth con Google provider
- Tabla `user_data` con JSON de sync (programas, historial, settings)
- Sync bidireccional con timestamps para resolucion de conflictos
- Migrar datos existentes de Google Drive

**Fase 2 - Social:**
- Tablas: `posts`, `comments`, `likes`, `follows`
- Realtime subscriptions para feed
- Edge Function para notificaciones
- Reemplazar mock de socialService.ts

**Fase 3 - Avanzado:**
- Leaderboards con materialized views
- Push notifications server-triggered via Supabase + FCM
- Analytics anonimizados para mejorar algoritmos AUGE

### Integracion tecnica minima

Para conectar Supabase a KPKN, se necesita:

```
npm install @supabase/supabase-js
```

Y un servicio wrapper:

```typescript
// services/supabaseService.ts
import { createClient } from '@supabase/supabase-js';

const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_ANON_KEY
);

export { supabase };
```

El resto de la integracion es reemplazar las llamadas mock en `socialService.ts`
y las llamadas a Google Drive en `useGoogleDrive.ts` con queries a Supabase.
