import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def git(repo, *args):
    return subprocess.check_output(
        ["git", "-c", "core.fsmonitor=false", "-C", str(repo), *args],
        text=True, encoding="utf-8"
    ).strip()


def collect(repo):
    android = repo / "android-native"
    files = set()
    for relative in ("app/src", "app/schemas", "gradle"):
        folder = android / relative
        if folder.exists():
            files.update(p for p in folder.rglob("*") if p.is_file())
    for relative in (
        "app/build.gradle.kts", "app/proguard-rules.pro", "build.gradle.kts",
        "settings.gradle.kts", "gradlew", "gradlew.bat", "gradle.properties",
    ):
        path = android / relative
        if path.is_file():
            files.add(path)
    result = []
    for path in sorted(files):
        if path.name.startswith(".env") or "keystore" in path.name.lower():
            raise RuntimeError("Private configuration unexpectedly appeared in source roots")
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
        result.append({"path": path.relative_to(repo).as_posix(),
                       "bytes": path.stat().st_size, "sha256": digest.hexdigest()})
    aggregate = hashlib.sha256(json.dumps(result, sort_keys=True).encode()).hexdigest()
    return {"captured_utc": datetime.now(timezone.utc).isoformat(),
            "repo": str(repo), "head": git(repo, "rev-parse", "HEAD"),
            "branch": git(repo, "branch", "--show-current"),
            "tracked_android_changes": git(repo, "diff", "--name-status", "HEAD", "--", "android-native"),
            "source_file_count": len(result), "source_sha256": aggregate, "files": result}


parser = argparse.ArgumentParser()
parser.add_argument("action", choices=("capture", "verify"))
parser.add_argument("--repo", required=True)
parser.add_argument("--output", required=True)
parser.add_argument("--baseline")
args = parser.parse_args()
result = collect(Path(args.repo))
if args.action == "verify":
    baseline = json.loads(Path(args.baseline).read_text(encoding="utf-8"))
    before = {item["path"]: item["sha256"] for item in baseline["files"]}
    after = {item["path"]: item["sha256"] for item in result["files"]}
    result["source_unchanged"] = before == after and baseline["head"] == result["head"]
    result["changed_paths"] = sorted(path for path in before.keys() | after.keys()
                                      if before.get(path) != after.get(path))
Path(args.output).write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps({key: result[key] for key in (
    "head", "branch", "source_file_count", "source_sha256", "source_unchanged", "changed_paths"
) if key in result}))
if args.action == "verify" and not result["source_unchanged"]:
    raise SystemExit(2)
