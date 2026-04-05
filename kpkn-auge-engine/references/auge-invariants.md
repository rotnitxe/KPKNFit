# AUGE Invariants

Use this file before editing formulas or thresholds. These are the kinds of details that silently change user-facing behavior when ported loosely.

## Fatigue and drain invariants

- Personalized battery tanks are derived from athlete capacity and must preserve relative proportions.
- Short rest should increase drain; long rest should reduce it.
- Accumulated sets should increase fatigue per set rather than keeping each set cost flat.
- Effective set detection must stay consistent with the source rules; warm-up or ineffective sets should not pollute fatigue/stress totals.
- Predicted session drain and completed session drain should stay conceptually aligned; do not make planned and completed math diverge without an explicit reason.

## Recovery invariants

- Muscle recovery depends on recovery profiles (`fast`, `medium`, `slow`, `heavy`) and muscle-to-profile mapping.
- Weighted sleep windows matter. The PWA uses a recent-nights weighted approach instead of a naive single-night read.
- Stress, sleep, nutrition, age, and gender modifiers all influence recovery speed or penalties.
- DOMS can cap apparent freshness even when decay math suggests a higher battery.
- Hours-to-recovery output should remain interpretable and tied to the same battery targets.

## Systemic / readiness invariants

- CNS/systemic fatigue is not the same as muscular fatigue.
- Readiness is a synthesis, not a direct mirror of one battery.
- Sleep banking and poor sleep should have distinct penalties/bonuses rather than collapsing into one linear adjustment.
- Work/study intensity and stress should remain part of the life-load component when the source uses them.

## Global battery invariants

- Muscular, CNS/CNC, and spinal batteries are separate surfaces and should not be merged.
- Global batteries should preserve their own half-life/decay assumptions rather than sharing one generic decay.
- Articular and structural systems are extensions, not replacements for muscular or spinal batteries.

## Volume and normalization invariants

- Muscle normalization and role multipliers can materially change AUGE outputs.
- If a muscle-name mapping changes, volume and recovery outputs can drift even when the formulas stay the same.
- Treat canonical muscle display IDs, category matching, and role multipliers as behavior-sensitive.

## Repo-specific examples to preserve deliberately

- Athlete capacity floors are behavior-sensitive and appear in both PWA and Kotlin.
- Weighted sleep handling is behavior-sensitive and should not be simplified into a last-night-only read.
- `AugeViewModel` wiring is not an invariant; the engine outputs are.
- Worker wrappers are not invariants; the computed outputs are.

## When you may intentionally change an invariant

Only do so when:

- the task explicitly requests an algorithm change, or
- the current Kotlin behavior is already acknowledged as intentionally different, and
- you document the change and validate its downstream effects.
