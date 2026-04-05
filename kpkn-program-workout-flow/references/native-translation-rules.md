# Native Translation Rules

## Preserve vs Adapt

### Preserve exactly when possible

- program hierarchy and save semantics
- session content and set-level intent
- workout logging outcomes
- rest-time meaning
- finish-workout data payloads
- history continuity
- route argument meaning

### Adapt aggressively for Android

- page layout
- content density
- drawer placement
- editing affordances
- button placement
- keyboard behavior
- sheet and dialog usage
- progress feedback and motion

### Drop or defer unless explicitly requested

- hover-only interactions
- desktop side-panel complexity
- CSS-era visual flourishes
- drag-and-drop if a simpler mobile interaction already solves the task
- zombie PWA behavior that no longer maps to current product intent

## Common Translation Patterns

| PWA shape | Native Android shape |
| --- | --- |
| Wide editor panel | Screen section or bottom sheet |
| Dense data table | Cards, rows, expandable sections |
| Web confirmation | `AlertDialog` |
| Framer-motion drawer | `ModalBottomSheet` or simple screen transition |
| Large sticky desktop toolbar | `TopAppBar` plus focused actions |
| Hover hint | Explicit inline label or supporting text |

## Program Editor Guidance

- Keep the wizard focused, fast, and mobile-first.
- Use the advanced editor only for real structural edits, not for basic creation.
- Prefer section switching, cards, and inline editors over copying desktop control surfaces.
- If the PWA exposes more knobs than Android currently needs, port only the knobs required for correctness or the requested scope.

## Session Editor Guidance

- Optimize for rapid exercise and set editing on a phone.
- Prefer expandable exercise rows, bottom sheets, and narrow numeric inputs over web-like tables.
- Keep exercise-picking, set editing, and part editing visibly separate so the user understands what they are changing.
- If a PWA affordance exists only to work around web modal friction, replace it with a simpler native flow.

## Workout Guidance

- Optimize for active use during training, not for parity with a desktop dashboard.
- Prioritize large primary actions, low-friction set logging, timer visibility, and fast exercise switching.
- Avoid deep nesting or multi-step modals in the middle of logging a set.
- Keep post-set and finish-workout flows compact unless the user explicitly requests richer feedback capture.

## Imperfect-Codebase Heuristics

- If the current Kotlin flow already works and only part of it is rough, improve the rough part instead of redesigning everything.
- If the PWA has legacy behavior that exists because it accumulated over time, do not assume it belongs in Android.
- If the Android code is monolithic, extract only the touched concern rather than trying to finish a full cleanup as a side quest.
- If current architecture is imperfect but understandable, preserve the pattern during a migration task and document the constraint.
