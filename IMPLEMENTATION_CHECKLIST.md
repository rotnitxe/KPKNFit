# Implementation Checklist: PWA Split Editor & Loops Port

## Pre-Flight Checks

- [ ] Install required packages:
  ```bash
  npm install react-native-draggable-flatlist
  npm install react-native-gesture-handler
  npm install @gorhom/bottom-sheet
  ```

- [ ] Ensure gesture handler is configured at app entry:
  ```typescript
  import { GestureHandlerRootView } from 'react-native-gesture-handler';

  // Wrap your app:
  <GestureHandlerRootView style={{ flex: 1 }}>
    {/* your app */}
  </GestureHandlerRootView>
  ```

- [ ] Verify `useColors()` hook is available in theme system
- [ ] Verify `useProgramStore()` has `updateProgram()` method
- [ ] Verify all icon imports exist in `components/icons`

## Component Integration Steps

### Phase 1: Core Components
- [ ] Add `SplitAdvancedEditor.tsx` to `components/program-detail/`
- [ ] Add `SplitChangerDrawer.tsx` to `components/program-detail/`
- [ ] Add `ProtocolsView.tsx` to `components/program-detail/`
- [ ] Add `ProgramTemplatesView.tsx` to `components/program-detail/`
- [ ] Add `VolumeRecalibrationModal.tsx` to `components/program-detail/`
- [ ] Add `VolumeCalibrationHistoryWidget.tsx` to `components/program-detail/`

### Phase 2: LoopsView Replacement
- [ ] Back up existing `LoopsView.tsx` from `components/programs/`
- [ ] Replace with new expanded version (557 LOC)
- [ ] Test backward compatibility with existing imports

### Phase 3: Screen Integration
- [ ] Update `SplitEditorScreen.tsx` with new imports
- [ ] Add tab switching logic
- [ ] Test navigation back/save flow
- [ ] Verify bottom sheet opens/closes properly

### Phase 4: Store Integration
- [ ] Ensure `programStore.ts` has:
  - `updateProgram(program)` method (general update)
  - `updateProgramSplit()` for split template application
  - Handle array mutations for loops

### Phase 5: Testing
- [ ] Test on iOS simulator
- [ ] Test on Android emulator
- [ ] Test drag-drop on real device (iOS/Android)
- [ ] Verify dark mode colors
- [ ] Check responsive layout on tablets

## Feature Checklist

### Split Advanced Editor
- [ ] Render 7-day grid layout
- [ ] Extract and display all sessions
- [ ] Group sessions by `dayOfWeek`
- [ ] Detect conflicts (multiple sessions per day)
- [ ] Display conflict warning badge
- [ ] Drag-drop sessions between days
- [ ] Remove session functionality
- [ ] "Add new session" button
- [ ] Unassigned sessions horizontal list

### Split Changer Drawer
- [ ] Bottom sheet renders on open
- [ ] Step 1: Gallery view
  - [ ] Search filter working
  - [ ] Tag filters working
  - [ ] Current split highlighted
- [ ] Step 2: Configuration view
  - [ ] Split preview shows
  - [ ] Start day selector works (all 7 days)
  - [ ] Scope selector (week/block/program)
  - [ ] Preserve exercises toggle
  - [ ] Apply button triggers callback
  - [ ] Back button returns to gallery

### Loops View
- [ ] Render loop list or empty state
- [ ] Sequencer shows next 12 cycles
- [ ] Sequencer cycles show correct loop activation
- [ ] Add loop button opens modal
- [ ] Edit loop button opens modal with data
- [ ] Delete loop removes from list
- [ ] Template buttons show template modal
- [ ] Template application adds loops
- [ ] Loop types display correct emojis

### Protocols View
- [ ] Protocol list renders
- [ ] Tag filtering works
- [ ] Protocol card shows emoji, name, description
- [ ] Click opens bottom sheet detail view
- [ ] Detail shows structure (blocks, weeks, intensity)
- [ ] Detail shows session categories
- [ ] Apply protocol updates program.macrocycles
- [ ] Back button closes detail

### Program Templates View
- [ ] Template gallery renders
- [ ] Tag filtering works
- [ ] Template card shows stats (weeks, loops, split)
- [ ] Click opens bottom sheet preview
- [ ] Preview shows week structure
- [ ] Preview shows included loops
- [ ] Apply template updates program structure
- [ ] Verify loops are added correctly

### Volume Controls
- [ ] Recalibration modal opens
- [ ] All 6 muscle groups display
- [ ] Input fields accept volume ranges
- [ ] Apply saves to program
- [ ] History widget shows past calibrations
- [ ] History expands/collapses

## Data Flow Verification

### Program Persistence
- [ ] `updateProgram()` called after split change
- [ ] `updateProgram()` called after loop add/edit/delete
- [ ] `updateProgram()` called after protocol apply
- [ ] `updateProgram()` called after template apply
- [ ] Changes persist across app restart

### State Management
- [ ] Bottom sheet state (`isOpen`) managed correctly
- [ ] Form state (selected split, scope, etc.) local
- [ ] Program mutations use spread operator (immutable)
- [ ] UUIDs generated for new objects (loops, blocks, sessions)

### UI State
- [ ] Correct tab is highlighted in SplitEditorScreen
- [ ] Bottom sheets snap to correct heights
- [ ] Empty states display when appropriate
- [ ] Loading states (if needed) display

## Performance Checks

- [ ] FlatList used for loops list (not ScrollView)
- [ ] FlatList used for protocols list
- [ ] FlatList used for templates list
- [ ] Memoized selectors for filtered data
- [ ] No unnecessary re-renders (test with React DevTools)
- [ ] Drag-drop is smooth (60 fps on device)

## Styling & UX

- [ ] All colors from `useColors()` hook
- [ ] Dark mode automatically supported
- [ ] Material Design 3 token usage consistent
- [ ] Touch targets ≥ 48pt (iOS guidelines)
- [ ] 16pt padding/margins (design system)
- [ ] Border radius 8-20pt (consistent rounding)
- [ ] Font weights: 600/700/800/900 only

### Color Tokens to Use
```typescript
colors.primary              // Main action color
colors.onPrimary           // Text on primary
colors.primaryContainer    // Background for primary-themed areas
colors.onPrimaryContainer

colors.secondary           // Secondary actions
colors.onSecondary
colors.secondaryContainer
colors.onSecondaryContainer

colors.error              // Destructive actions
colors.errorContainer
colors.onErrorContainer

colors.surface            // Default background
colors.surfaceContainer   // Elevated surface
colors.surfaceContainerHigh

colors.onSurface          // Main text
colors.onSurfaceVariant   // Secondary text
colors.outlineVariant     // Borders
```

## Documentation

- [ ] Component prop interfaces documented
- [ ] Key functions commented (extractSessions, groupSessionsByDay, etc.)
- [ ] Usage examples added to component headers
- [ ] Type definitions clear and typed

## Troubleshooting

### "Module not found" errors
- [ ] Check all import paths are absolute (`apps/mobile/src/...`)
- [ ] Verify icon names match in icons component
- [ ] Verify SPLIT_TEMPLATES import path

### Bottom sheet not rendering
- [ ] Check `@gorhom/bottom-sheet` version
- [ ] Verify snap points array: `[0.01, 0.9]` or similar
- [ ] Ensure parent has flex layout
- [ ] Check for conflicting modal overlays

### Drag-drop not working
- [ ] Verify `GestureHandlerRootView` wraps component
- [ ] Check for conflicting pan responders
- [ ] Test on real device (simulator may be slow)
- [ ] Ensure `react-native-draggable-flatlist` is installed

### Colors not changing with theme
- [ ] Verify `useColors()` hook is called in component
- [ ] Check color property names (case-sensitive)
- [ ] Verify theme context is provided at app root
- [ ] Force app reload after theme change

### Loops not saving
- [ ] Check `updateProgram()` is called
- [ ] Verify program.loops array is initialized
- [ ] Check for immutability violations (no direct mutations)
- [ ] Verify localStorage/persistence is working

## Rollout Plan

### Stage 1: Feature Flag (Optional)
```typescript
const ENABLE_SPLIT_EDITOR_V2 = __DEV__; // Enable in dev only
// Gate components behind flag
{ENABLE_SPLIT_EDITOR_V2 && <SplitAdvancedEditor />}
```

### Stage 2: Soft Launch
- [ ] Release to internal testers
- [ ] Gather feedback on UX/performance
- [ ] Fix critical bugs

### Stage 3: Public Release
- [ ] Full testing suite passes
- [ ] Analytics tracking added
- [ ] Help documentation written
- [ ] Release notes prepared

## Post-Launch Monitoring

- [ ] Track component render times
- [ ] Monitor bottom sheet animation performance
- [ ] Check for memory leaks (long sessions editing)
- [ ] Monitor crash reports
- [ ] Track user engagement with new features

---

## Quick Start Command

```bash
# Verify all files present
ls -la apps/mobile/src/components/program-detail/
ls -la apps/mobile/src/components/programs/LoopsView.tsx
ls -la apps/mobile/src/screens/Workout/SplitEditorScreen.tsx

# Run tests
npm test -- SplitAdvancedEditor
npm test -- SplitEditorScreen

# Build for iOS
npm run ios

# Build for Android
npm run android
```

---

## Emergency Rollback

If issues arise, revert with:
```bash
git checkout HEAD~1 \
  apps/mobile/src/screens/Workout/SplitEditorScreen.tsx \
  apps/mobile/src/components/programs/LoopsView.tsx
# Remove new files:
rm apps/mobile/src/components/program-detail/*.tsx
```

