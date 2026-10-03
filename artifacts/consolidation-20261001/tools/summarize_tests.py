"""Archive Gradle unit-test XML results and print a compact JSON summary.

usage: python summarize_tests.py <label> [flavorTask=testBaseDebugUnitTest] [moduleDir]
Copies build/test-results/<flavorTask>/*.xml to consolidation/test-evidence/<label>/ and
writes summary.json (totals, per-class counts, failures with first message lines, skipped).
"""
import json, shutil, sys, hashlib
import xml.etree.ElementTree as ET
from pathlib import Path

C = Path(r"C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001")
label = sys.argv[1]
task = sys.argv[2] if len(sys.argv) > 2 else "testBaseDebugUnitTest"
module = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(r"C:\Users\valen\Documents\KPKNFit\android-native\app")
src = module / "build" / "test-results" / task
dst = C / "test-evidence" / label
dst.mkdir(parents=True, exist_ok=True)

totals = dict(tests=0, failures=0, errors=0, skipped=0, classes=0)
classes, failures, skipped = [], [], []
for f in sorted(src.glob("*.xml")):
    shutil.copy2(f, dst / f.name)
    root = ET.parse(f).getroot()
    t = int(root.get("tests", 0)); fl = int(root.get("failures", 0)); er = int(root.get("errors", 0)); sk = int(root.get("skipped", 0))
    totals["tests"] += t; totals["failures"] += fl; totals["errors"] += er; totals["skipped"] += sk; totals["classes"] += 1
    classes.append(dict(name=root.get("name"), tests=t, failures=fl, errors=er, skipped=sk, time=root.get("time")))
    for tc in root.iter("testcase"):
        bad = tc.find("failure")
        if bad is None:
            bad = tc.find("error")
        if bad is not None:
            msg = (bad.get("message") or bad.text or "").strip().splitlines()
            failures.append(dict(cls=tc.get("classname"), method=tc.get("name"), message="\n".join(msg[:6])[:900]))
        if tc.find("skipped") is not None:
            skipped.append(f"{tc.get('classname')}.{tc.get('name')}")

summary = dict(label=label, task=task, sourceDir=str(src), totals=totals, failures=failures, skipped=skipped, classes=classes)
(dst / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
print(json.dumps(dict(label=label, totals=totals, failureCount=len(failures),
                      failures=[f"{x['cls']}.{x['method']}" for x in failures[:40]],
                      skipped=skipped[:20], archive=str(dst)), indent=2, ensure_ascii=False))
