import Foundation
import SQLite3

internal enum DatabaseBackupHelper {

    static func createSnapshot() throws -> String {
        let db = KpknDatabase.instance()
        let internalDb = db.internalDb
        sqlite3_exec(internalDb.handle, "PRAGMA wal_checkpoint(FULL)", nil, nil, nil)

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dbFile = docs.appendingPathComponent("kpkn.db")
        guard FileManager.default.fileExists(atPath: dbFile.path) else {
            throw NSError(domain: "DatabaseBackupHelper", code: 1, userInfo: [NSLocalizedDescriptionKey: "Base de datos no encontrada para respaldar"])
        }

        let snapshotDir = docs.appendingPathComponent("snapshots")
        try FileManager.default.createDirectory(at: snapshotDir, withIntermediateDirectories: true)

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        formatter.locale = Locale.current
        let timestamp = formatter.string(from: Date())
        let snapshotFile = snapshotDir.appendingPathComponent("kpkn_snapshot_\(timestamp).db")

        try copyFile(from: dbFile, to: snapshotFile)
        return snapshotFile.lastPathComponent
    }

    static func listSnapshots() -> [URL] {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let snapshotDir = docs.appendingPathComponent("snapshots")
        guard FileManager.default.fileExists(atPath: snapshotDir.path) else { return [] }
        let files = (try? FileManager.default.contentsOfDirectory(at: snapshotDir, includingPropertiesForKeys: nil)) ?? []
        return files
            .filter { $0.lastPathComponent.hasPrefix("kpkn_snapshot_") && $0.pathExtension == "db" }
            .sorted { $0.lastPathComponent > $1.lastPathComponent }
    }

    static func restoreSnapshot(snapshotFile: URL) throws {
        guard FileManager.default.fileExists(atPath: snapshotFile.path) else {
            throw NSError(domain: "DatabaseBackupHelper", code: 2, userInfo: [NSLocalizedDescriptionKey: "Archivo de snapshot no encontrado"])
        }

        KpknDatabase.closeInstance()

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let destDbFile = docs.appendingPathComponent("kpkn.db")

        try? FileManager.default.removeItem(at: docs.appendingPathComponent("kpkn.db-wal"))
        try? FileManager.default.removeItem(at: docs.appendingPathComponent("kpkn.db-shm"))

        try copyFile(from: snapshotFile, to: destDbFile)
    }

    static func deleteSnapshot(snapshotFile: URL) -> Bool {
        guard FileManager.default.fileExists(atPath: snapshotFile.path) else { return false }
        return (try? FileManager.default.removeItem(at: snapshotFile)) != nil
    }

    private static func copyFile(from source: URL, to dest: URL) throws {
        let data = try Data(contentsOf: source)
        try data.write(to: dest, options: .atomic)
    }
}
