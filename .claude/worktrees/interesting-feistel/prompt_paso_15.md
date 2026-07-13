# Prompt para IA Obrera - Paso 15 (Settings & Wellbeing)

**Objetivo:** Refactorizar las pantallas de Settings y Progress para cumplir con la arquitectura React Native de KPKN Fit.

**Archivos a modificar:**
1. `apps/mobile/src/screens/Settings/SettingsScreen.tsx`
2. `apps/mobile/src/screens/Progress/ProgressScreen.tsx`

**REGLAS CRÍTICAS (DEBES SEGUIRLAS O EL CÓDIGO SERÁ RECHAZADO):**
1. **PROHIBIDO EL USO DE COLORES TAILWIND**: No uses `text-kpkn-*`, `bg-kpkn-*`, `border-white/10`, ni colores genéricos como `bg-red-500`.
2. **USO OBLIGATORIO DE useColors()**: Todos los colores deben venir del hook `useColors()` de `@/theme`.
   - Ejemplo: `color: colors.onSurface`, `backgroundColor: colors.surface`.
3. **STYLE SHEET NATIVO**: Mueve todos los estilos a un objeto `StyleSheet.create` al final del archivo. Usa arreglos de estilos para combinar estilos estáticos con dinámicos: `style={[styles.container, { backgroundColor: colors.surface }]}`.
4. **COMPONENTES NATIVOS**: Asegúrate de usar `View`, `Text`, `TouchableOpacity`, `ScrollView`, `TextInput` y `StyleSheet` de `react-native`.
5. **NADA DE TRUNCATE**: Usa la prop `numberOfLines={1}` en componentes `Text` en lugar de clases de truncado de CSS.
6. **M3 SEMANTIC TOKENS**:
   - `colors.primary`: Acentos principales.
   - `colors.onSurface`: Texto principal.
   - `colors.onSurfaceVariant`: Texto secundario / muted.
   - `colors.outlineVariant`: Bordes de tarjetas.
   - `colors.surfaceContainer`: Fondos de tarjetas elevadas.
   - `colors.error`: Estados de error/lesiones/peligro.

**Entregable:** Proporciona el código completo de ambos archivos siguiendo estas instrucciones al pie de la letra.
