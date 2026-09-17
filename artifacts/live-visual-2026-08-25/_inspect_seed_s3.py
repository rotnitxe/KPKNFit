#!/usr/bin/env python3
"""Patch seed to start at set index 2 (S3) and mark readiness done if possible."""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

OUT = Path(__file__).resolve().parent
src = OUT / "kpkn-seed-long-isthmus.db"
dst = OUT / "kpkn-seed-long-s3.db"
dst.write_bytes(src.read_bytes())
con = sqlite3.connect(dst)
cur = con.cursor()
tables = [r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table'")]
print("tables", tables)
for t in tables:
    if any(k in t.lower() for k in ("ongoing", "workout", "session", "prep", "readiness")):
        cols = [d[1] for d in cur.execute(f"PRAGMA table_info({t})")]
        n = cur.execute(f"SELECT count(*) FROM {t}").fetchone()[0]
        print(f"{t} n={n} cols={cols}")

# Typical KPKN ongoing table
for t in tables:
    if "ongoing" in t.lower():
        rows = cur.execute(f"SELECT * FROM {t}").fetchall()
        cols = [d[1] for d in cur.execute(f"PRAGMA table_info({t})")]
        print("sample", cols)
        for row in rows[:2]:
            d = dict(zip(cols, row))
            for k, v in d.items():
                if isinstance(v, str) and len(v) > 120:
                    d[k] = v[:120] + "..."
            print(json.dumps(d, default=str)[:2000])
con.close()
print("wrote", dst)
