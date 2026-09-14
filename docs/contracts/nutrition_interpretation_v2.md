# Natural meal interpretation contract, revision 2

This behavioral contract supersedes the question/automatic-acceptance policy in
`nutrition_interpretation_v1_golden.json`. The existing v1 telemetry schema is not
changed by this revision. Revision 2 does not require a Room migration or a change
to historical logged nutrient totals. Android is the current executable product;
iOS and backend implementations must not claim parity until these behaviors are
implemented and tested there.

## Meaning before quantity

- Preserve every consumed food mention and the attributes that identify it:
  species, composition, named brand, raw/cooked state and preparation.
- Food families support retrieval and portion priors; they are not interchangeable
  identities. Turkey breast is not chicken breast, and peanut paste is not pasta.
- Numeric words inside lexicalized food names are part of identity: `tres leches`
  is a dessert name, not three portions of milk. External quantities remain valid.
- A brand field that happens to equal a generic food term is not evidence that the
  user named a brand. Candidate options must satisfy the same food-head and product
  composition constraints as the selected result.
- Ingredient exclusions and product attributes have different scope. `sin leche`
  excludes milk; `leche sin lactosa` constrains the identity of the milk product.
- Parse numeric expressions, decimal comma/point and fractions before splitting
  list punctuation or conjunctions. `0,5 kg` and `0.5 kg` both mean 500 grams.
- Keep independent mentions distinct until compatible identities, attributes and
  amounts have been established. Reordering independent mentions must not alter
  totals, exclusions or cooking states.
- Preserve unquantified food text between or after quantified mentions. Explicit
  self-corrections replace the corrected mention, not unrelated foods.

## Quantity and nutritional basis

The reference weight of a nutrient profile is independent of the portion eaten.
Normalize nutrients using their declared reference weight and source; do not infer
that reference weight from a convenient serving size or from plausible calories.
For values declared per 100 g: consumed nutrient = value * eaten grams / 100.
For values genuinely declared per serving: divide by that serving's reference
weight instead. Legacy source data needs an explicit adapter, not a universal
replacement of all serving sizes with 100 g.

Quantity precedence is explicit weight/household measure, compatible confirmed
personal portion, then a food/state/context household prior. Context modifies the
estimated quantity, not the nutrient density. Half/one/two of the same household
measure scale proportionally; slices do not become whole pieces. Apply raw/cooked
conversions exactly once and record them. A reported cooking state must agree with
the selected profile or an explicit conversion.

The estimator adopts a 240 mL cup, 15 mL tablespoon and 5 mL teaspoon as canonical
volume references, following [FDA household metric equivalents](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/guidance-industry-guidelines-determining-metric-equivalents-household-measures).
These are model conventions, not a claim that every user's cup has that capacity.
An unspecified bowl remains a household portion prior. Consumed amounts are
canonical grams: convert both explicit mL and container volumes using the same
food-density rule. Convert a nutrient profile explicitly declared per mL using that
same density for its reference weight. A profile already declared per gram is not
converted again. New logged amounts in grams must be labelled grams; historical
amount/unit pairs and nutrient totals are preserved unchanged.

For dry rolled/quick oats, a measured cup is 81 g according to the
[USDA Food Buying Guide](https://foodbuyingguide.fns.usda.gov/AltText/Grains_Grams_Conversions1).
This is separate from a default breakfast portion or a cup of cooked oats.

## Templates and provenance

A partially matching template cannot return its entire ingredient list before
parsing the new description. Current quantities, identities and exclusions take
precedence. Only compatible information for mentions actually present may be
reused. Pack size in a product identity is not automatically the amount eaten.

Compare compatible local, custom and imported candidates before prioritizing
sources. Preserve meaningful alternatives when calculating ambiguity. Local
storage, plausible macros and a language-dataset match do not establish verified
provenance. Semantic examples may support vocabulary, not certify nutrients.

## Interpretation, uncertainty and confirmation

One interpretation per mention carries the selected identity, provenance, state,
quantity, central nutrients, ranges and pending material questions through
editing, UI and persistence. Consumers must not reparse a shortened label, choose
only the first mention or silently reconstruct a different interpretation.

An omitted household portion can be estimated without a mandatory question. An
unresolved identity or explicit-attribute conflict is material. Compare preparation
alternatives on the same eaten quantity; existing material-difference thresholds
are 50 kcal, 5 g protein or 5 g fat. Numeric ranking scores are not calibrated
probabilities of correctness. Finite calories alone cannot confirm identity.

Show one material question at a time outside collapsed advanced details. Allow a
natural-language option and an explicit uncertain-estimate choice without requiring
grams. The uncertain choice preserves ranges and does not train personal habits.
The user-visible food identity must describe the selected food. Portion resizing
always uses immutable base nutrients: large -> habitual -> large is idempotent.
The same invariant applies when the original description already specified a size.
An explicit nutrient correction updates that base; a later size edit must preserve
the corrected density. An inferred portion retains inferred provenance even when
its estimated grams are available. Selecting a subjective size does not declare a
measured weight or introduce a dry/cooked weight question by itself.

Quantity uncertainty and nutrient-density uncertainty are separate. Known grams do
not establish the composition of an unidentified food. A recipe assumption must be
visible and based on named source profiles; an unmatched estimate must not pretend
to have a verified nutritional reference or a zero-width nutrient interval.

## Saving and learning

Every active consumed mention must either have a valid saved interpretation or an
explicit user decision; no `mapNotNull`-style silent disappearance is permitted.
Report success and close the editor only after persistence succeeds. Preserve the
draft and expose retry on failure. Cancelling a dismiss gesture must restore the
visible editor with its draft intact. Reopened log rows must retain an estimate
indicator and saved nutrient range, rather than presenting the center as exact. Do not recalculate historical logs after catalog
updates.

Commit only explicitly confirmed learning dimensions after a successful food-log
write. Estimate acceptance does not confirm identity, preparation or exact portion.
Serialize calibration updates, and remove all related identity/portion associations
when the user asks to forget them. Discarding a draft must not train new habits.

## Evaluation

Test food identity, additions/omissions, quantities, nutrient arithmetic and required
clarifications separately. Maintain independently authored descriptions that are
not copied from the runtime semantic dataset. Check the complete parse -> resolve
-> interpret -> edit -> save path as well as component invariants. Test with empty
history and with previous templates/mappings. Passing a fixture corpus is not a
population-level accuracy estimate.

Reference methods: [USDA Foundation Foods, Weights](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/)
and [FAO/INFOODS food matching](https://www.fao.org/4/ap805e/ap805e.pdf).
