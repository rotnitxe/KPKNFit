"""Opt-in latch for the retired, template-based catalog generators.

The scripts next to this file wrote exercise copy and anatomy from pattern-level
templates. That is exactly the vice the catalog re-curation removed: generic text,
sentences recycled across similar exercises, and muscle roles guessed from a
pattern. The single authoring surface is now the ficha
(``catalog/exercises/v2/curation/fichas/<familyId>.json``) copied into the source by
``scripts/catalog_v2_apply_fichas.py``.

They are kept only as an audit trail of how the 2026-08/09 catalog was built. They do
not produce a valid catalog any more (the compiler rejects the retired fields they
emit) and, run by accident, they would overwrite curated fichas with templates.
Every executable one therefore calls ``require_explicit_opt_in`` first.

To reproduce history on purpose:

    PowerShell:  $env:KPKN_LEGACY_GENERATORS_ACK = "overwrite-curated-fichas"
    POSIX:       export KPKN_LEGACY_GENERATORS_ACK=overwrite-curated-fichas
"""

from __future__ import annotations

import os
from pathlib import Path

ENV_VAR = "KPKN_LEGACY_GENERATORS_ACK"
ACK_VALUE = "overwrite-curated-fichas"


def require_explicit_opt_in(script_path: str) -> None:
    """Exit with an explanation unless the operator acknowledged the risk through the environment."""
    if os.environ.get(ENV_VAR) == ACK_VALUE:
        return
    name = Path(script_path).name
    raise SystemExit(
        f"{name} is a retired template-based generator and refuses to run.\n"
        "It would overwrite curated fichas with pattern-level templates and emit fields the compiler rejects.\n"
        "Author the exercise in catalog/exercises/v2/curation/fichas/<familyId>.json, then run:\n"
        "  python scripts/catalog_v2_apply_fichas.py --only-definitions <definitionId>[,<definitionId>...]\n"
        f"To reproduce history on purpose set {ENV_VAR}={ACK_VALUE} and run it again."
    )
