# KPKN Visual Language

Use this reference when a screen needs to feel like KPKN instead of a generic Compose sample.

## Core feeling

KPKN should feel:

- strong
- mobile-native
- focused on training and readiness
- information-rich without becoming chaotic
- visually intentional rather than template-like

It should not feel:

- like a direct PWA screenshot port
- like a generic startup dashboard
- like a spreadsheet hidden inside cards
- like a temporary admin panel

## Visual signatures already present in the repo

### 1. Bold hero treatment

Seen in:

- `HomeHeaderSection.kt`
- `CompactHeroBanner.kt`
- `NutritionScreen.kt`

Characteristics:

- strong headline hierarchy
- high-value context near the top
- room for status chips or quick controls
- often a more expressive background than the rest of the screen

### 2. Rings and circular progress

Seen in:

- `HomeRingsSection.kt`
- nutrition macro-ring hero

Use rings when:

- the domain is inherently battery-, recovery-, or progress-oriented
- the circular metaphor clarifies status faster than rows of numbers

Do not use rings when:

- a plain stat row or bar would be clearer
- the metric does not benefit from the metaphor

### 3. Strong rounding and pill shapes

Common patterns:

- cards around 20-28dp corners
- pills for status, tabs, and compact selection
- circular icon buttons for navigation or secondary actions

### 4. Uppercase micro-labels

Use for:

- section tags
- state chips
- tiny KPI labels
- compact metadata

Do not use uppercase everywhere. Keep body text readable.

### 5. Spanish, domain-specific product voice

Good:

- "TUS RINGS"
- "Adherencia"
- "Enfoque"
- "Crear plan"

Bad:

- vague productivity-app language
- translated literal web copy that sounds awkward in native mobile UI

## Color strategy

Use color with meaning.

- Rings and readiness surfaces can justify stronger feature accents.
- Home and program surfaces can support richer hero accents or gradients.
- Neutral screens should still preserve one clear focal point.

Avoid:

- random multi-color decoration
- washed-out gray surfaces with no emphasis
- introducing a brand-new palette for one local change

## Typography strategy

The repo still relies partly on local typography choices instead of a fully evolved type system.

That means:

- match the nearest local screen first
- use weight and size changes deliberately
- keep titles clear and strong
- avoid verbose, tiny helper text blocks unless the domain truly needs explanation

## Section rhythm

Good KPKN screens usually:

- lead with a hero or high-value status block
- move into structured sections
- use spacing to separate jobs
- keep related controls visually grouped

Poor rhythm looks like:

- ten equal cards stacked with no hierarchy
- a long form with no sectional breaks
- too much decoration before the first useful action

## Screen archetypes

### Dashboard / home

- hero greeting or state
- high-value progress or recovery signals
- quick actions
- card groups below

### Editor / workflow

- obvious current step or context
- strong save or continue affordance
- section grouping
- supportive helper copy only where needed

### Detail screen

- hero identity block
- key metadata and status
- segmented or sectional content beneath

### Sheet / modal

- focused task only
- clear dismiss/save behavior
- not a full screen crammed into a bottom sheet

## Anti-patterns

- flat Material defaults with no KPKN character
- decorative gradients with no hierarchy benefit
- excessive visual noise around already-complex data
- giant headers that push the first useful control off-screen
- literal recreation of wide PWA layouts on mobile
