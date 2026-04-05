# Translation Principles

Use this reference when a PWA feature must be turned into a good Android experience rather than copied literally.

## Preserve vs translate

### Preserve directly

- domain formulas and scoring
- business rules and validation
- feature intent
- high-value information hierarchy
- Spanish copy and terminology unless the task says otherwise
- meaning of settings, toggles, and tracked metrics

### Translate intentionally

- layout shape
- interaction model
- navigation boundaries
- animation style
- density and disclosure level
- platform affordances for editing, searching, filtering, and confirming actions

## Common web-to-Android translations

| Web/PWA pattern | Native Android/Compose translation |
|---|---|
| left sidebar + large content panel | scaffold + top app bar + tabs/sections/sheets |
| hover affordance | visible action, supporting text, or deliberate long-press |
| fixed modal | `ModalBottomSheet` or `AlertDialog` |
| desktop table | grouped cards, lists, stepper rows, or editable sections |
| CSS drag/drop | native drag, reorder, explicit move actions, or simplified flow |
| infinite inline controls | progressive disclosure with sections and secondary screens |
| wide dashboard grid | vertical feed with prioritized cards |
| tooltip-heavy UI | inline helper text or contextual secondary row |
| localStorage draft | `ViewModel` state + DataStore/Room when persistence matters |
| route fragment/view toggles | explicit navigation destination or tab state |

## Translation heuristics by feature type

### Dashboards

- Keep the most important insight visible above the fold.
- Reduce side-by-side comparisons unless they are essential.
- Prefer one hero metric plus supporting cards instead of reproducing every widget at equal weight.

### Wizards and onboarding

- Split complex onboarding into screen-sized steps.
- Show a single primary action.
- Keep validation inline and immediate.
- Do not force the desktop wizard structure if a native stepped flow is clearer.

### Editors

- Convert large editor canvases into sectional editing.
- Use sheets for pickers and scoped adjustments.
- Keep destructive actions explicit and separated from save.
- Favor summary rows with drill-down over full-detail forms everywhere at once.

### Active workflows

- For workout/session flows, optimize for speed, thumb reach, and low cognitive load.
- Keep current step, next action, and progress obvious.
- Avoid dense configuration controls in the active path.

## Anti-cloning signals

If any of these appear in the migration, stop and redesign:

- Compose tree mirrors the PWA component tree one-for-one.
- Large amounts of UI state are carried over because the web component had them.
- Screen depends on desktop width assumptions.
- Multiple side panels or nested drawers survive unchanged.
- The port feels like a screenshot recreation instead of a native screen.
- User needs two hands or excessive precision for a task that should be quick.

## Product tone reminder

KPKN is not a generic fitness app. Preserve the product's confident, data-rich, athlete-oriented tone, but let the native app express that through Compose patterns, strong hierarchy, and focused interactions.
