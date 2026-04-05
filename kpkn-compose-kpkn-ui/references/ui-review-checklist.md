# KPKN Compose UI Review Checklist

Use this checklist before saying a Compose surface is done.

## Product fit

- Does the screen feel like KPKN?
- Does it preserve the intended user job from the PWA or existing product?
- Is the Android structure better for mobile than a direct web clone?

## Hierarchy

- Is the primary action obvious?
- Is the most important information visible early?
- Do sections feel intentional instead of equally weighted?

## Visual quality

- Are shape, spacing, and typography coherent with nearby KPKN screens?
- Does the screen avoid generic placeholder-card aesthetics?
- Are accents or gradients deliberate instead of noisy?

## Copy

- Is product-facing copy in Spanish where expected?
- Are labels short and clear?
- Are domain terms consistent with the rest of the app?

## Ergonomics

- Are touch targets comfortable?
- Is the scroll flow manageable on a phone?
- Are bottom actions reachable?

## Compose hygiene

- Is the screen split into reasonable composables?
- Is business logic kept out of leaf UI?
- Are shared components only shared when reuse is real?

## Flow completeness

- Does the screen handle empty, loading, and error states if needed?
- Are save, dismiss, continue, and back behaviors clear?
- If it came from the PWA, are important controls still present somewhere in the Android flow?

## Review risk flags

- Generic Material defaults replaced too much of KPKN's character
- Visual density became too high for mobile
- Important actions were hidden behind gestures only
- Screen-specific code was prematurely forced into `ui/components/`
- A local UI task widened into a global theme redesign
