"""Copy the verified signed artifact to the two destinations authorized by the user."""
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

root = Path(__file__).resolve().parent
proof = json.loads((root / "release-verification.json").read_text(encoding="utf-8"))
source = Path(proof["apk"])


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


assert proof["signed_and_verified"] and proof["source_unchanged_during_build"]
assert sha(source) == proof["apk_sha256"]
filename = f'KPKNFit-release-FIRMADO-20261020-{proof["source_sha256"][:8]}.apk'
destinations = [Path(r"C:\Users\valen\Desktop"), Path(r"G:\Mi unidad")]
receipt = {"delivered_utc": datetime.now(timezone.utc).isoformat(), "apk_sha256": proof["apk_sha256"],
           "source_sha256": proof["source_sha256"], "destinations": []}
for folder in destinations:
    if not folder.is_dir():
        raise RuntimeError(f"Authorized destination is not mounted: {folder}")
    destination = folder / filename
    if destination.exists():
        assert sha(destination) == proof["apk_sha256"], "Will not overwrite a different existing APK"
    else:
        shutil.copy2(source, destination)
    assert sha(destination) == proof["apk_sha256"]
    assert destination.stat().st_size == proof["apk_bytes"]
    sidecar = destination.with_suffix(".verification.json")
    sidecar.write_text(json.dumps(proof, indent=2), encoding="utf-8")
    receipt["destinations"].append({"path": str(destination), "bytes": destination.stat().st_size,
                                    "sha256": sha(destination), "verification": str(sidecar)})
    (root / "delivery-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")

# Archive only the exact old unsigned artifact produced by this task, to avoid desktop ambiguity.
old = Path(r"C:\Users\valen\Desktop\KPKNFit-base-release-unsigned-0ba94859.apk")
old_hash = "ed2ea454a7c412cc6352f430f586c29f6ab19694352a215e61b7e3b9888a616f"
if old.is_file() and sha(old) == old_hash:
    archived = root / "previous-unsigned-0ba94859.apk"
    if not archived.exists():
        shutil.move(str(old), str(archived))
        assert sha(archived) == old_hash
        receipt["previous_unsigned_archived"] = str(archived)
(root / "delivery-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
print(json.dumps(receipt))
