"""User-facing wrapper that turns catalog validation failures into concise errors."""

from __future__ import annotations

import sys

from compile_exercise_catalog_v2 import main


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as exc:
        print(f"catalog-v2 validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
