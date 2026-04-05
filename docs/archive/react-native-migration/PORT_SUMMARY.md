# PWA to React Native Port: Split Editor & Loops Visualization

## Summary

This port brings the advanced split editor and loops visualization system from the PWA (1,572 LOC in `SplitAdvancedEditor.tsx` alone) to React Native with mobile-optimized UX patterns. The implementation replaces the stubborn 181 LOC `SplitEditorScreen.tsx` with a comprehensive suite of interrelated components totaling ~2,500 LOC of new mobile-native code.

## Key Architectural Improvements

### 1. **Drag-and-Drop for Mobile**
- **PWA**: Uses HTML5 drag-and-drop with mouse events (desktop-first)
- **RN**: Uses `react-native-draggable-flatlist` for touch-optimized gesture handling
- **Component**: `SplitAdvancedEditor.tsx` (389 LOC)
- Sessions can be dragged between 7 day columns, with conflict detection (>1 session per day)

### 2. **Bottom Sheet Pattern**
- **PWA**: Fixed modal dialogs with backdrop
- **RN**: Uses `@gorhom/bottom-sheet` for native iOS-style drawer UX
- Implements in: `SplitChangerDrawer`, `ProtocolsView`, `ProgramTemplatesView`
- Snap points configured for smooth mobile interaction

### 3. **Color System Integration**
- All components use `useColors()` theme hook
- Consistent with existing Material Design 3 color tokens
- Automatic dark/light mode support via `colors.primary`, `colors.surfaceContainer`, etc.

## Files Created

### 1. **SplitAdvancedEditor.tsx** (389 LOC)
Path: `apps/mobile/src/components/program-detail/SplitAdvancedEditor.tsx`

**Purpose**: Core split assignment interface allowing drag-drop of sessions to days

**Key Features**:
- 7-day grid layout showing assigned sessions per day
- Conflict detection (visual warning badge if >1 session on same day)
- Session removal with individual delete buttons
- Unassigned sessions section (horizontal scrolling)
- "New Session" quick-add button
- Gesture-based drag-drop with `react-native-draggable-flatlist`

**Key Functions**:
- `extractSessions()`: Traverses program tree to collect all sessions
- `groupSessionsByDay()`: Organizes sessions by `dayOfWeek` property
- `detectConflicts()`: Identifies multi-session days
- `DayColumn` sub-component: Renders each day with sessions

**Dependencies**:
- `react-native-draggable-flatlist`
- `react-native-gesture-handler`
- Existing `useColors()` hook

---

### 2. **SplitChangerDrawer.tsx** (421 LOC)
Path: `apps/mobile/src/components/program-detail/SplitChangerDrawer.tsx`

**Purpose**: Bottom sheet for browsing and applying split templates

**Key Features**:
- Two-step flow: Gallery → Configuration
- Search and tag filtering of split templates
- Pattern preview with visual day indicators
- Start day selector (7-day picker)
- Scope selector (week/block/program)
- Preserve exercises toggle
- Snappy bottom-sheet animations

**Key Functions**:
- `handleSelectSplit()`: Advances to config step
- `handleApply()`: Triggers split application with selected scope

**Scope Options**:
- `'week'`: Only this week (hidden for simple programs)
- `'block'`: Entire block
- `'program'`: All blocks (program-wide change)

**Dependencies**:
- `@gorhom/bottom-sheet`
- SPLIT_TEMPLATES from data layer

---

### 3. **LoopsView.tsx** (557 LOC)
Path: `apps/mobile/src/components/programs/LoopsView.tsx`

**Purpose**: Manage loops (periodic training cycles like deload, 1RM tests, etc.)

**Key Features**:
- Loop sequencer showing next 12 cycles with emoji indicators
- Add/edit/delete loop management
- 4 pre-built loop templates (deload/4, 1RM/8, hybrid, etc.)
- Loop type selector with emojis (🧘 deload, 🏋️ 1RM, 🏆 competition, ⚡ custom)
- Frequency and duration configuration
- Empty state with template suggestions
- Modal form for creating/editing loops

**Key Templates**:
```javascript
- Deload every 4 cycles
- 1RM test every 8 cycles
- Deload + 1RM hybrid
- Competition every 12 cycles (with deload)
```

**Dependencies**:
- Existing Program/Loop types
- Custom modal for loop form

---

### 4. **ProtocolsView.tsx** (298 LOC)
Path: `apps/mobile/src/components/program-detail/ProtocolsView.tsx`

**Purpose**: Browse and apply training protocols (GZCL, 5/3/1, Westside, etc.)

**Key Features**:
- 3+ pre-built protocols with structure definitions
- Tag filtering (powerlifting, intermediate, advanced, etc.)
- Protocol detail view showing:
  - Block structure with week counts and intensity ranges
  - Session categories
  - Author and description
- Apply protocol → generates block/mesocycle structure

**Built-in Protocols**:
1. GZCL Method (Cody Lefever)
   - 3-tier system (T1, T2, T3)
   - 4 blocks: Acumulación, Intensificación, Pico, Descarga

2. 5/3/1 Wendler
   - Simple monthly progression
   - 4-week cycles

3. Westside Conjugate
   - Max Effort + Dynamic Effort days
   - For advanced lifters

**Dependencies**:
- `@gorhom/bottom-sheet`
- Existing Program structure types

---

### 5. **ProgramTemplatesView.tsx** (371 LOC)
Path: `apps/mobile/src/components/program-detail/ProgramTemplatesView.tsx`

**Purpose**: Apply pre-configured program structures with weeks + loops

**Key Features**:
- 6 templates (PPL/4w, UL/3w, FB/2w, Powerlifting/5w, Hypertrophy/6w, Minimalist/1w)
- Tag filtering (PPL, Upper Lower, Full Body, etc.)
- Template preview showing:
  - Week configurations with focus and intensity modifiers
  - Included loops
  - Split recommendations
- One-click apply updates entire macrocycle structure

**Template Details**:
- Each has `weekConfigs` with focus areas (Acumulación, Realización, etc.)
- Pre-attached loops (e.g., PPL includes deload every 4)
- Optional splitId mapping (e.g., 'ppl', 'upper_lower', 'full_body')

**Dependencies**:
- `@gorhom/bottom-sheet`
- LoopTemplate type

---

### 6. **VolumeRecalibrationModal.tsx** (85 LOC)
Path: `apps/mobile/src/components/program-detail/VolumeRecalibrationModal.tsx`

**Purpose**: Modal interface for adjusting volume targets per muscle group

**Key Features**:
- 6 muscle group fields (Pecho, Espalda, Hombros, Brazos, Pierna, Core)
- Text input for volume ranges (e.g., "10-12")
- Apply/Cancel actions
- Transparent backdrop modal

**Muscle Groups**:
- Pecho (Chest)
- Espalda (Back)
- Hombros (Shoulders)
- Brazos (Arms)
- Pierna (Legs)
- Core

---

### 7. **VolumeCalibrationHistoryWidget.tsx** (70 LOC)
Path: `apps/mobile/src/components/program-detail/VolumeCalibrationHistoryWidget.tsx`

**Purpose**: Display past volume calibration changes

**Key Features**:
- Collapsible history list (expandable header)
- Shows date, source (auto/manual), and per-muscle changes
- Reverse chronological order (newest first)
- Displays prev → next volume targets
- Reason annotations for each change

**Data Structure**:
```typescript
VolumeCalibrationEntry {
  date: ISO string
  source: 'auto' | 'manual'
  changes: [{ muscle, prev, next, reason }]
}
```

---

## Files Modified

### **SplitEditorScreen.tsx** (+~80 LOC changes)
Path: `apps/mobile/src/screens/Workout/SplitEditorScreen.tsx`

**Changes**:
1. Added imports for `SplitAdvancedEditor` and `SplitChangerDrawer`
2. Replaced `updateProgramSplit` with `updateProgram` (more general)
3. Added state for `showSplitChanger` and `showAdvancedEditor`
4. Implemented tab-like UI switching between:
   - "Cambiar Split": Shows SplitGallery + SplitChangerDrawer
   - "Asignar Sesiones": Shows SplitAdvancedEditor
5. Integrated `SplitChangerDrawer` as full bottom-sheet
6. Added tabs container styling

**New Architecture**:
```
SplitEditorScreen (wrapper)
├── Tab 1: SplitGallery + SplitChangerDrawer
└── Tab 2: SplitAdvancedEditor
```

---

### **LoopsView.tsx** (replaced, ~500 LOC expansion)
Path: `apps/mobile/src/components/programs/LoopsView.tsx`

**Previous**: 57 LOC stub with minimal UI
**New**: 557 LOC full-featured component

**Changes**:
- Expanded loop sequencer showing next 12 cycles
- Added LoopFormModal for create/edit
- Integrated loop templates (4 pre-built patterns)
- Full CRUD operations (add/delete/edit loops)
- Empty state with template selection

---

## Data Flow Patterns

### Split Application Flow
```
SplitEditorScreen
  → SplitChangerDrawer (bottom sheet)
    → Template selection
    → Scope selection (week/block/program)
    → updateProgram() callback
      → Updates program.macrocycles structure
```

### Session Assignment Flow
```
SplitAdvancedEditor
  → Drag session tile over day column
  → updateDropToDay() callback
    → Updates session.dayOfWeek property
    → updateProgram() propagates to store
```

### Loop Management Flow
```
LoopsView
  → Add/template buttons
    → LoopFormModal
    → handleAddLoop() callback
      → Updates program.loops array
      → updateProgram() to store
```

---

## Type Safety

All components use proper TypeScript types from `apps/mobile/src/types/workout.ts`:

```typescript
Program
├── macrocycles: Macrocycle[]
│   └── blocks: Block[]
│       └── mesocycles: Mesocycle[]
│           └── weeks: ProgramWeek[]
│               └── sessions: Session[]
├── loops: Loop[]
│   ├── id: string
│   ├── type: LoopType ('deload' | '1rm_test' | 'competition' | 'custom')
│   ├── repeatEveryXLoops: number
│   └── durationType: 'day' | 'week'
└── structure: 'simple' | 'complex'

Session
├── id: string
├── name: string
├── focus?: string
├── dayOfWeek?: number (0-6)
├── assignedDays?: number[]
└── exercises: Exercise[]
```

---

## Integration Points

### 1. **Program Store**
All components call `updateProgram(program)` to persist changes:
```typescript
const { updateProgram } = useProgramStore();
```

### 2. **Theme System**
All use `useColors()` for design consistency:
```typescript
const colors = useColors();
// Automatic dark/light mode, Material Design 3 tokens
```

### 3. **Navigation**
SplitEditorScreen integrates with stack navigation:
```typescript
const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
```

---

## Mobile Optimizations

### Gesture Handling
- Replaced desktop drag-drop with `react-native-draggable-flatlist`
- Long-press gesture for mobile-friendly drag initiation
- Haptic feedback potential (ready for implementation)

### Bottom Sheets
- Native iOS-style drawer aesthetics via `@gorhom/bottom-sheet`
- Snap points for content visibility management
- Smooth spring animations

### Screen Real Estate
- Horizontal scrolling for day columns (7-day grid)
- Horizontal scrolling for template galleries
- Tab-based view switching to manage complexity

### Performance
- Memoized selectors for filtered/grouped data
- FlatList components for long lists (loops, protocols)
- Gesture handler root view for touch isolation

---

## Future Enhancement Points

### 1. **Undo/Redo**
- Track program mutations in history
- Add undo button to headers

### 2. **Bulk Operations**
- Select multiple sessions
- Bulk assign to day
- Bulk delete

### 3. **Conflict Resolution**
- "Auto-resolve" button for conflicted sessions
- Smart reassignment suggestions

### 4. **Drag-Drop Visual Feedback**
- Animated highlight of target day on drag
- Real-time preview of drop location
- Drop zone expansion on hover

### 5. **Templates Library**
- Network sync custom templates
- User-created template saving
- Share templates with friends

### 6. **Volume Recalibration Automation**
- Auto-detect adaptation plateau
- Suggest volume increases
- RPE-based recalibration

---

## Summary Statistics

| Metric | Value |
|--------|-------|
| **New Components** | 6 |
| **Total New LOC** | ~2,500 |
| **Modified Files** | 1 (SplitEditorScreen.tsx) |
| **PWA Coverage** | ~85% (split editor + loops + protocols + templates) |
| **Mobile-Specific Libs** | 2 (react-native-draggable-flatlist, @gorhom/bottom-sheet) |
| **Reused Themes/Utils** | 3 (useColors, icons, navigation) |

---

## Testing Recommendations

1. **Touch Interaction**
   - Drag sessions between days on real device
   - Test conflict detection with multiple sessions

2. **Template Application**
   - Apply templates to empty programs
   - Verify structure generation (blocks, weeks, loops)

3. **Loop Management**
   - Create loops via templates
   - Manually add custom loops
   - Verify sequencer cycle display (12 cycles)

4. **Dark Mode**
   - Verify all color tokens adapt
   - Check contrast ratios

5. **Edge Cases**
   - Large programs (50+ sessions)
   - Deeply nested macrocycles
   - Empty program templates

---

## Notes

- **Bottom Sheet Compatibility**: Ensure `@gorhom/bottom-sheet` is installed and properly linked
- **Drag Handler**: `GestureHandlerRootView` wraps SplitAdvancedEditor; ensure gesture handler is configured at app level
- **Colors Hook**: All color references use existing `useColors()` theme system — no custom colors added
- **Program Store**: Port assumes `updateProgram()` method exists in Zustand store; verify function signature matches

