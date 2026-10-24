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

        let snapshot = try KpknDatabaseInternal(filePath: snapshotFile.path)
        defer { snapshot.close() }
        try backup(from: internalDb.handle, to: snapshot.handle)
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

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let staging = docs.appendingPathComponent("restore-\(UUID().uuidString).db")
        defer {
            for suffix in ["", "-wal", "-shm"] { try? FileManager.default.removeItem(atPath: staging.path + suffix) }
        }
        // Migrate a private copy. Failure leaves both the source backup and active DB untouched.
        try copyFile(from: snapshotFile, to: staging)
        let candidate = try KpknDatabaseInternal(filePath: staging.path)
        do {
            let version = try candidate.getUserVersion()
            guard (1...22).contains(version), try candidate.runMigrations(currentVersion: version) == 22 else {
                throw NSError(domain: "DatabaseBackupHelper", code: 3, userInfo: [NSLocalizedDescriptionKey: "Versión de respaldo no compatible; se conserva la base activa."])
            }
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(candidate.handle, "PRAGMA quick_check", -1, &statement, nil) == SQLITE_OK else { throw NSError(domain: "DatabaseBackupHelper", code: 4) }
            defer { sqlite3_finalize(statement) }
            guard sqlite3_step(statement) == SQLITE_ROW, let result=sqlite3_column_text(statement,0), String(cString:result) == "ok" else { throw NSError(domain: "DatabaseBackupHelper", code: 5) }
            try candidate.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        } catch { candidate.close(); throw error }
        defer { candidate.close() }
        _ = try createSnapshot() // Recoverable active state before the atomic SQLite transaction.
        let active = KpknDatabase.instance().internalDb
        guard try active.getUserVersion() == 22 else {
            throw NSError(domain: "DatabaseBackupHelper", code: 6,
                userInfo: [NSLocalizedDescriptionKey: "La base activa no tiene el esquema esperado."])
        }
        guard try schemaSignature(candidate.handle) == schemaSignature(active.handle) else {
            throw NSError(domain: "DatabaseBackupHelper", code: 9,
                userInfo: [NSLocalizedDescriptionKey: "El esquema del respaldo no coincide; se conserva la base activa."])
        }
        // Keep the active connection and DAO identities. SQLite rolls back an incomplete
        // backup; replacing the file would strand already-created repositories on old handles.
        try backup(from: candidate.handle, to: active.handle)

    }

    static func deleteSnapshot(snapshotFile: URL) -> Bool {
        guard FileManager.default.fileExists(atPath: snapshotFile.path) else { return false }
        return (try? FileManager.default.removeItem(at: snapshotFile)) != nil
    }

    private static func schemaSignature(_ handle: OpaquePointer?) throws -> [String] {
        var tables: OpaquePointer?
        guard sqlite3_prepare_v2(handle,
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            -1, &tables, nil) == SQLITE_OK else { throw NSError(domain: "DatabaseBackupHelper", code: 10) }
        defer { sqlite3_finalize(tables) }
        var result: [String] = []
        var status = sqlite3_step(tables)
        while status == SQLITE_ROW {
            guard let raw = sqlite3_column_text(tables, 0) else { throw NSError(domain: "DatabaseBackupHelper", code: 11) }
            let table = String(cString: raw)
            result.append(table)
            let quoted = table.replacingOccurrences(of: "\"", with: "\"\"")
            var columns: OpaquePointer?
            guard sqlite3_prepare_v2(handle, "PRAGMA table_info(\"\(quoted)\")", -1, &columns, nil) == SQLITE_OK else {
                throw NSError(domain: "DatabaseBackupHelper", code: 12)
            }
            defer { sqlite3_finalize(columns) }
            var columnStatus = sqlite3_step(columns)
            while columnStatus == SQLITE_ROW {
                result.append((0..<6).map { index in
                    sqlite3_column_text(columns, Int32(index)).map { String(cString: $0) } ?? "<null>"
                }.joined(separator: "|"))
                columnStatus = sqlite3_step(columns)
            }
            guard columnStatus == SQLITE_DONE else { throw NSError(domain: "DatabaseBackupHelper", code: 13) }
            status = sqlite3_step(tables)
        }
        guard status == SQLITE_DONE else { throw NSError(domain: "DatabaseBackupHelper", code: 14) }
        return result
    }

    private static func backup(from source: OpaquePointer?, to destination: OpaquePointer?) throws {
        guard let source, let destination,
              let operation = sqlite3_backup_init(destination, "main", source, "main") else {
            throw NSError(domain: "DatabaseBackupHelper", code: 7,
                userInfo: [NSLocalizedDescriptionKey: "No se pudo iniciar una copia consistente."])
        }
        // A single step commits only a complete copy. BUSY/LOCKED/error finishes with
        // rollback, without retry loops or overwriting the external snapshot.
        let result = sqlite3_backup_step(operation, -1)
        let finished = sqlite3_backup_finish(operation)
        guard result == SQLITE_DONE, finished == SQLITE_OK else {
            throw NSError(domain: "DatabaseBackupHelper", code: Int(result),
                userInfo: [NSLocalizedDescriptionKey: "No se pudo completar la restauración; se conserva la base activa."])
        }
    }

    private static func copyFile(from source: URL, to dest: URL) throws {
        let data = try Data(contentsOf: source)
        guard data.prefix(16) == Data("SQLite format 3\0".utf8) else {
            throw NSError(domain: "DatabaseBackupHelper", code: 8,
                userInfo: [NSLocalizedDescriptionKey: "El archivo no es un respaldo SQLite."])
        }
        try data.write(to: dest, options: .atomic)
    }
}
