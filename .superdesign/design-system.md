# KPKN Material 3 + Liquid Glass Design System

## Core Direction
- Visual language must match Home.tsx and components/home/HomeCardsSection.tsx exactly.
- Style: Material 3 surfaces + subtle Liquid Glass (professional, not playful).
- Typography: Roboto + system defaults from project tokens.
- Avoid oversized controls and excessive color usage.

## Color Tokens
- Use CSS vars from theme engine only: --md-sys-color-*
- Accent references from existing home cards:
  - Primary progress: var(--md-sys-color-primary)
  - Surface layers: var(--md-sys-color-surface-container-*)
  - Outline: var(--md-sys-color-outline-variant)

## Shape + Elevation
- Rounded containers: 24-36px depending on hierarchy.
- Hero and premium cards use subtle blur + layered borders.
- Keep shadows soft and low-contrast.

## Components to mimic
- Home section "Progreso físico y alimentación"
- Babushka-like macro rings + horizontal progress bars
- Dense but readable KPI cards

## Interaction
- Fast transitions (200-350ms)
- Minimal iconography; icons only when they add semantic value.

## Constraints
- Keep existing route/component names.
- Must remain mobile-first and desktop-safe.
