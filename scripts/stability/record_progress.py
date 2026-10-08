"""Update evidence traceability without converting partial work into launch acceptance."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'artifacts/stability-launch/requirements.json'
document = json.loads(TARGET.read_text(encoding='utf-8'))
evidence = {
    'A01': ['android-native/app/src/main/java/com/example/kpkn/data/backup/AppDataSessions.kt', 'gradle-portable-writers-retry-5.log'],
    'A02': ['tests-portable-writers-5/', 'android-native/app/src/main/java/com/example/kpkn/data/backup/PortableBackup.kt'],
    'A03': ['android-native/app/src/main/java/com/example/kpkn/data/backup/AppBootstrap.kt'],
    'A04': ['tests-portable-writers-5/', 'android-native/app/src/main/java/com/example/kpkn/data/backup/PortableBackup.kt'],
    'A07': ['tests-portable-writers-5/'],
    'B01': ['android-native/app/src/main/java/com/example/kpkn/data/backup/PersonalStorageManifest.kt', 'android-native/app/src/main/java/com/example/kpkn/data/sync/GuestSourceLedger.kt'],
    'B02': ['tests-portable-writers-5/', 'android-native/app/src/main/java/com/example/kpkn/data/account/ProfileStorage.kt'],
    'B03': ['android-native/app/src/main/java/com/example/kpkn/screens/account/AccountScreen.kt'],
    'B04': ['firebase-atomic-conflicts-15-final.log', 'web-deletion-local.json', 'web-deletion-local.jpg'],
    'B05': ['firebase-atomic-conflicts-15-final.log'],
    'B06': ['android-native/app/src/main/java/com/example/kpkn/data/db/DatabaseBackupHelper.kt'],
    'C01': ['android-native/app/src/main/java/com/example/kpkn/data/sync/RoomSyncRepository.kt'],
    'C02': ['firebase-atomic-conflicts-15-final.log'],
    'C03': ['android-native/app/src/main/java/com/example/kpkn/data/db/SyncJournalSchema.kt', 'tests-account-ui-portable-3/'],
    'C04': ['firebase-atomic-conflicts-15-final.log'],
    'C05': ['firebase-atomic-conflicts-15-final.log', 'tests-account-ui-portable-3/'],
    'C06': ['firebase-atomic-conflicts-15-final.log', 'tests-account-ui-portable-3/'],
    'C07': ['firebase-atomic-conflicts-15-final.log'],
    'C08': ['firebase-atomic-conflicts-15-final.log'],
    'C09': ['firebase-atomic-conflicts-15-final.log', 'android-native/app/src/main/java/com/example/kpkn/data/sync/GuestImportCoordinator.kt'],
    'C11': ['server-contract-tests.log', 'ios-native/KPKNContracts/'],
    'C12': ['android-native/app/src/main/java/com/example/kpkn/data/sync/PersonalRecordCodec.kt'],
    'E01': ['gradle-portable-writers-retry-5.log', 'emulator-resources-backup-5/'],
    'F01': ['android-native/app/src/main/java/com/example/kpkn/data/account/PlaySubscriptionRepository.kt'],
    'F02': ['android-native/app/src/main/java/com/example/kpkn/data/account/PlaySubscriptionRepository.kt'],
    'F03': ['firebase-atomic-conflicts-15-final.log'],
    'F04': ['firebase-atomic-conflicts-15-final.log'],
    'F05': ['firebase-atomic-conflicts-15-final.log'],
    'F07': ['android-native/app/src/main/java/com/example/kpkn/data/account/FirebaseEntitlementRepository.kt', 'android-native/app/src/main/java/com/example/kpkn/data/account/PlaySubscriptionRepository.kt'],
    'F08': ['tests-account-ui-portable-3/', 'web-deletion-local.json'],
    'G06': ['android-native/app/src/main/java/com/example/kpkn/data/backup/AppBootstrap.kt'],
    'G07': ['android-native/app/src/main/java/com/example/kpkn/data/db/KpknDatabase.kt', 'tests-real-v29-migration-9/', 'tests-context-contracts-16/'],
    'H01': ['android-native/app/src/main/res/values-en/account_strings.xml', 'web-deletion-local.json'],
    'H03': ['android-native/app/src/main/java/com/example/kpkn/ui/locale/LocaleManager.kt'],
    'H04': ['android-native/app/src/main/java/com/example/kpkn/screens/account/AdultAccessGate.kt'],
    'H05': ['android-native/app/src/main/java/com/example/kpkn/screens/account/AdultAccessGate.kt'],
    'I04': ['android-native/app/src/main/java/com/example/kpkn/data/diagnostics/CrashReportingController.kt', 'gradle-portable-writers-retry-5.log'],
    'I06': ['cloud-sync/web/privacy.html', 'cloud-sync/web/terms.html'],
    'J04': ['resource-optimization-complete.json', 'resource-optimization-cache.log', 'scripts/stability/optimize_resources.py'],
    'J06': ['native-packaging-debug-5.json'],
    'J07': ['.github/workflows/android-stability.yml'],
    'D-REMOVAL': ['review-apk-verification-current.json', 'emulator-resources-backup-5/'],
}
evidence.update({
    'A01': evidence['A01'] + ['tests-gps-maintenance-10/', 'tests-capability-transfer-settings-12/'],
    'A02': evidence['A02'] + ['tests-deletion-recovery-alias-13/'],
    'A05': ['tests-real-v29-migration-9/', 'tests-context-contracts-16/'],
    'A06': ['tests-deletion-recovery-alias-13/', 'android-native/app/src/test/java/com/example/kpkn/data/settings/DatabaseSnapshotTest.kt',
            'scripts/stability/verify_storage_inventory.py', 'storage-classification-17.json', 'storage-classification-tests-17-final.log'],
    'B01': evidence['B01'] + ['tests-gps-maintenance-10/', 'tests-deletion-recovery-alias-13/'],
    'B02': evidence['B02'] + ['tests-context-contracts-16/', 'tests-deletion-recovery-alias-13/'],
    'B04': evidence['B04'] + ['firebase-retired-operations-20.log', 'tests-deletion-recovery-alias-13/'],
    'B05': evidence['B05'] + ['firebase-retired-operations-20.log'],
    'C07': evidence['C07'] + ['firebase-retired-operations-20.log'],
    'C10': ['tests-capability-transfer-settings-12/', 'firebase-retired-operations-20.log', 'firebase-authenticated-range-21.log'],
    'E06': ['tests-gps-maintenance-10/'],
    'F06': ['tests-capability-transfer-settings-12/', 'android-native/app/src/main/java/com/example/kpkn/domain/sync/SyncCapabilities.kt'],
    'I05': ['DATA-SDK-INVENTORY.md'],
    'J11': ['gradle-full-jvm-debug-15.log', 'tests-full-jvm-15/', 'tests-context-contracts-16/'],
    'J12': ['tests-normalized-media-7/'],
    'D-REMOVAL': evidence['D-REMOVAL'] + ['review-apk-verification-10.json', 'emulator-gps-maintenance-10/', 'tests-context-contracts-16/'],
})
for key in ('C02', 'C04', 'C05', 'C06', 'C08', 'C09', 'F03', 'F04', 'F05'):
    evidence[key].append('firebase-retired-operations-20.log')
for row in document['requirements']:
    if row['id'] in evidence:
        row['evidence'] = list(dict.fromkeys(row.get('evidence', []) + evidence[row['id']]))
        if row['code'] == 'PENDIENTE': row['code'] = 'PARCIAL'
        # Evidence for a component is not verification of its whole requirement.
        if row['verification'] == 'PENDIENTE' and row['id'] in {'B02', 'B04', 'B05', 'C02', 'C03', 'C04', 'C05', 'C06', 'C07', 'C08', 'C09', 'J06'}:
            row['verification'] = 'PARCIAL'
document['workStatus'] = 'IN_PROGRESS_NOT_A_LAUNCH_CANDIDATE'
historical_notes = ['Android builds 10 and 12: 8 and 36 directed tests passed, respectively, with assembleDebug. API 36 UI verified only for APK 10.',
    'Server: 21 real local-emulator scenarios passed, including authenticated byte-range reads. Play provider is synthetic; no real purchase.',
    'Browser deletion: email/confirmation/reauth/wrong-password and purge verified locally in ES/EN.',
    'Room v30: actual v29 schema migration passed on API 26/34; historical media migration and normalized FTS hygiene passed in build 16.',
    'Full JVM build 15: 6806 tests, 42 failures and 2 voice omissions. Build 16: 62 directed tests, 10 failures; build 17: 101 tests with 14 onboarding failures; build 18 is in progress.',
    'Storage source gate passed for 45 schema tables and 70 direct storage calls; 3 negative-probe tests passed. Arbitrary alias and device writer QA remain separate.',
    'No successful full lint, release/R8, runtime 16 KB, real billing, complete two-device native flow, or beta evidence.',
    'All original A-J packages remain required except the approved replacement of D.']
# Preserve newer evidence recorded by later rounds. Historical summaries must not
# replace a successful full suite/lint or turn an old in-progress state into current status.
document['historicalEvidenceNotes'] = list(dict.fromkeys(document.get('historicalEvidenceNotes', []) + historical_notes))
document.setdefault('evidenceNotes', [])
TARGET.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
