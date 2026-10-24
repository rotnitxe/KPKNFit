#!/usr/bin/env bash
# Foundation-only shared contract; does not replace an iOS/Xcode build.
set -euo pipefail
if ! command -v swiftc >/dev/null; then
    echo 'PENDIENTE: este host no dispone de swiftc; ejecutar este contrato en un host macOS autorizado.' >&2
    exit 2
fi
training_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
training_temp="$(mktemp -d)"
trap 'rm -rf "$training_temp"' EXIT
python3 - "$training_repo" "$training_temp" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1]);dest=Path(sys.argv[2]);source=(root/'ios-native/KPKNFit/KPKNFit/Domain/Training/VolumeCalculator.swift').read_text()
start=source.index('internal struct TrainingEvidenceFact:')
end=source.index('internal enum TrainingHistoryProjection',start)
(dest/'Facts.swift').write_text('import Foundation\n'+source[start:end])
PY
cat > "$training_temp/main.swift" <<'SWIFT'
import Foundation
struct Contract: Decodable { let cases: [Case] }
struct Case: Decodable {
    let name: String
    let nowMs: Int64
    let current: TrainingEvidenceFact
    let history: [TrainingEvidenceFact]
    let expectedPreviousIds: [String]
    let expectedMedian: Double?
}
let fixture=try JSONDecoder().decode(Contract.self,from:Data(contentsOf:URL(fileURLWithPath:CommandLine.arguments[1])))
for item in fixture.cases {
    let previous=TrainingInsightsEngine.previous(item.current,history:item.history,nowMs:item.nowMs)
    precondition(previous.map(\.sourceId)==item.expectedPreviousIds,item.name+": previous sources")
    precondition(TrainingInsightsEngine.median(previous)==item.expectedMedian,item.name+": median")
}
print("\(fixture.cases.count) shared Swift contracts passed; iOS build is a separate gate")
SWIFT
swiftc "$training_temp/Facts.swift" "$training_temp/main.swift" -o "$training_temp/contracts"
"$training_temp/contracts" "$training_repo/docs/research/training-evidence/fixtures/insights-v1.json"
