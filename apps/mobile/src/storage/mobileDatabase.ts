import { open, type QuickSQLiteConnection } from 'react-native-quick-sqlite';

const DATABASE_NAME = 'kpkn_mobile.db';

let connection: QuickSQLiteConnection | null = null;

/**
 * Error específico para fallos de apertura de SQLite.
 * Permite que callers distingan un fallo de DB de cualquier otro error en tiempo de arranque.
 */
export class DatabaseOpenError extends Error {
  constructor(originalMessage: string) {
    super(`SQLITE_OPEN_FAILED: No se pudo abrir la base de datos de la app. ${originalMessage}`);
    this.name = 'DatabaseOpenError';
  }
}

function bootstrapDatabase(db: QuickSQLiteConnection) {
  db.execute('PRAGMA journal_mode = WAL;');
  db.execute('PRAGMA foreign_keys = ON;');

  db.execute(`
    CREATE TABLE IF NOT EXISTS app_meta (
      key TEXT PRIMARY KEY NOT NULL,
      value TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
  `);

  db.execute(`
    CREATE TABLE IF NOT EXISTS domain_payloads (
      domain TEXT PRIMARY KEY NOT NULL,
      payload_json TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
  `);

  db.execute(`
    CREATE TABLE IF NOT EXISTS nutrition_logs (
      id TEXT PRIMARY KEY NOT NULL,
      description TEXT NOT NULL,
      created_at TEXT NOT NULL,
      totals_json TEXT NOT NULL,
      analysis_json TEXT NOT NULL
    );
  `);

  db.execute(`
    CREATE INDEX IF NOT EXISTS idx_nutrition_logs_created_at
    ON nutrition_logs(created_at DESC);
  `);

  db.execute(`
    CREATE TABLE IF NOT EXISTS workout_logs_local (
      id TEXT PRIMARY KEY NOT NULL,
      date TEXT NOT NULL,
      program_name TEXT NOT NULL,
      session_name TEXT NOT NULL,
      exercise_count INTEGER NOT NULL,
      completed_set_count INTEGER NOT NULL,
      duration_minutes INTEGER
    );
  `);

  db.execute(`
    CREATE INDEX IF NOT EXISTS idx_workout_logs_local_date
    ON workout_logs_local(date DESC);
  `);

  // Tabla aislada para smoke tests — no contamina nutrition_logs con datos de prueba.
  db.execute(`
    CREATE TABLE IF NOT EXISTS smoke_test_logs (
      id TEXT PRIMARY KEY NOT NULL,
      description TEXT NOT NULL,
      created_at TEXT NOT NULL,
      totals_json TEXT NOT NULL,
      analysis_json TEXT NOT NULL
    );
  `);
}

/**
 * Abre o devuelve la conexión existente a la base de datos SQLite.
 *
 * @throws {DatabaseOpenError} Si SQLite no puede abrir o crear la base de datos.
 *   El error debe capturarse en bootstrap para presentar UX de recuperación al usuario.
 */
export function getMobileDatabase(): QuickSQLiteConnection {
  if (!connection) {
    try {
      connection = open({ name: DATABASE_NAME });
      bootstrapDatabase(connection);
    } catch (error) {
      // Asegurar que la variable quede null para que el siguiente intento (retry) lo pueda reabrir.
      connection = null;
      const message = error instanceof Error ? error.message : 'Error desconocido';
      throw new DatabaseOpenError(message);
    }
  }

  return connection;
}
