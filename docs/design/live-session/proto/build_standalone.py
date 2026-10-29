#!/usr/bin/env python3
"""Build a single-file version of the prototype for review and sharing.

Source of truth: proto/index.html, proto/kpkn.css, proto/scenes.js, proto/app.js.
Output: docs/design/live-session/prototipo.html
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "prototipo.html"

html = (HERE / "index.html").read_text(encoding="utf-8")
css = (HERE / "kpkn.css").read_text(encoding="utf-8")
scenes = (HERE / "scenes.js").read_text(encoding="utf-8")
app = (HERE / "app.js").read_text(encoding="utf-8")

html = html.replace(
    '<link rel="stylesheet" href="kpkn.css">',
    "<style>\n" + css + "\n</style>",
)
html = html.replace(
    '<script src="scenes.js"></script>',
    "<script>\n" + scenes + "\n</script>",
)
html = html.replace(
    '<script src="app.js"></script>',
    "<script>\n" + app + "\n</script>",
)

OUT.write_text(html, encoding="utf-8")
print(f"{OUT} · {OUT.stat().st_size:,} bytes")
