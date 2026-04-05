# Compose Translation Rules for KPKN

Use this file when translating a PWA screen into Compose.

## Translate intent, not scaffolding

Start by identifying:

- the main user job
- the most important information
- the core actions
- which states matter

Only then choose the Compose structure.

## Common web-to-Compose translations

### Web drawer or side panel

Translate into:

- `ModalBottomSheet`
- dedicated mobile step
- full screen editor when the content is too dense

Do not squeeze a desktop side panel into a narrow inline column.

### Wide tabs or segmented desktop panels

Translate into:

- segmented controls
- pagers
- section switching
- nested screens if the flow becomes clearer

### Data table

Translate into:

- grouped cards
- stat rows
- compact expandable sections
- list items with clear labels

### Hover or tooltip help

Translate into:

- inline support text
- secondary labels
- explicit info icon or sheet

### Dense editor

Translate into:

- sections
- progressive disclosure
- stepper or wizard if needed
- one "save" model the user can understand

## Screen splitting heuristics

Split the flow when:

- the user cannot see the primary action clearly
- the amount of configuration exceeds a comfortable phone screen
- one step depends on a prior choice
- the web surface relied on width more than depth

Keep it as one screen when:

- the task is short
- context switching would hurt more than help
- the screen benefits from seeing all sections together

## Compose structure heuristics

- Use `LazyColumn` for long screens.
- Use feature-local section composables to keep screens readable.
- Keep one screen-level state owner.
- Keep leaf composables mostly presentational.
- Prefer explicit callbacks over hidden global state access.

## Content hierarchy heuristics

Always ask:

- What should the user notice first?
- What should they do next?
- What can be collapsed?
- What can wait until after the primary action?

## Interaction heuristics

- Primary action should be obvious.
- Secondary actions should not compete visually with the main job.
- Destructive actions should be visible but not dominant.
- Gestures should be optional enhancements, not the only path.

## Visual equivalence rules

Keep equivalent:

- domain meaning
- task completion
- state coverage
- priority of information
- critical metrics

Allow to differ:

- exact card arrangement
- exact spacing rhythm
- exact container choice
- exact tab/drawer pattern
- non-essential decorative elements

## Red flags

- the Compose version needs horizontal scrolling because the PWA did
- the screen has too many tiny chips and buttons in one row
- the user must scroll past decorative hero content to reach the task
- business logic is added to a card because the PWA mixed it there
- the UI uses many wrappers just to mimic CSS structure
