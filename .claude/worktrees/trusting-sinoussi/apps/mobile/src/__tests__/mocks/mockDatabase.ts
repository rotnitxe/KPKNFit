/**
 * Mock in-memory de SQLite para tests del pipeline de migración.
 * Simula las tablas app_meta, domain_payloads, nutrition_logs, 
 * workout_logs_local y smoke_test_logs.
 */
export type Row = Record<string, unknown>;

interface Table {
  columns: string[];
  rows: Row[];
}

const tables: Record<string, Table> = {};

function ensureTables() {
  if (!tables.app_meta) {
    tables.app_meta = { columns: ['key', 'value', 'updated_at'], rows: [] };
  }
  if (!tables.domain_payloads) {
    tables.domain_payloads = { columns: ['domain', 'payload_json', 'updated_at'], rows: [] };
  }
  if (!tables.nutrition_logs) {
    tables.nutrition_logs = {
      columns: ['id', 'description', 'created_at', 'totals_json', 'analysis_json'],
      rows: [],
    };
  }
  if (!tables.workout_logs_local) {
    tables.workout_logs_local = {
      columns: ['id', 'date', 'program_name', 'session_name', 'exercise_count', 'completed_set_count', 'duration_minutes'],
      rows: [],
    };
  }
  if (!tables.smoke_test_logs) {
    tables.smoke_test_logs = {
      columns: ['id', 'description', 'created_at', 'totals_json', 'analysis_json'],
      rows: [],
    };
  }
}

function parseInsertOrReplace(sql: string, params: unknown[]) {
  const match = sql.match(/INSERT\s+OR\s+REPLACE\s+INTO\s+(\w+)\s*\(([^)]+)\)/i);
  if (!match) return null;
  const tableName = match[1];
  const columns = match[2].split(',').map(c => c.trim());
  return { tableName, columns, params };
}

function parseSelect(sql: string, params: unknown[]) {
  const matchFrom = sql.match(/FROM\s+(\w+)/i);
  if (!matchFrom) return null;
  const tableName = matchFrom[1];
  const table = tables[tableName];
  if (!table) return { rows: { _array: [], length: 0 } };

  let filtered = [...table.rows];

  // Parsear WHERE clause simple
  const whereMatch = sql.match(/WHERE\s+(.+?)(?:\s+ORDER|\s+LIMIT|\s*$)/i);
  if (whereMatch) {
    const conditions = whereMatch[1];
    // WHERE key = ?
    const eqMatch = conditions.match(/(\w+)\s*=\s*\?/g);
    if (eqMatch) {
      let paramIdx = 0;
      for (const eq of eqMatch) {
        const colMatch = eq.match(/(\w+)\s*=/);
        const colName = colMatch ? colMatch[1] : null;
        if (colName && paramIdx < params.length) {
          const val = params[paramIdx++];
          filtered = filtered.filter(r => r[colName] === val);
        }
      }
    }
    // WHERE key IN (?, ?, ...)
    const inMatch = conditions.match(/(\w+)\s+IN\s*\(([^)]+)\)/i);
    if (inMatch) {
      const colName = inMatch[1];
      const placeholderCount = inMatch[2].split(',').length;
      const inValues = params.slice(0, placeholderCount);
      filtered = filtered.filter(r => inValues.includes(r[colName]));
    }
  }

  // Parsear ORDER BY ... DESC
  const orderMatch = sql.match(/ORDER\s+BY\s+(\w+)\s+(ASC|DESC)/i);
  if (orderMatch) {
    const col = orderMatch[1];
    const dir = orderMatch[2].toUpperCase();
    filtered.sort((a, b) => {
      const va = String(a[col] ?? '');
      const vb = String(b[col] ?? '');
      return dir === 'DESC' ? vb.localeCompare(va) : va.localeCompare(vb);
    });
  }

  // Parsear LIMIT
  const limitMatch = sql.match(/LIMIT\s+\?/i);
  if (limitMatch) {
    const limitVal = params[params.length - 1];
    if (typeof limitVal === 'number') {
      filtered = filtered.slice(0, limitVal);
    }
  }

  // Seleccionar columnas
  const selectMatch = sql.match(/SELECT\s+(.+?)\s+FROM/i);
  if (selectMatch && selectMatch[1].trim() !== '*') {
    const selectCols = selectMatch[1].split(',').map(c => c.trim());
    filtered = filtered.map(row => {
      const out: Row = {};
      for (const col of selectCols) {
        out[col] = row[col];
      }
      return out;
    });
  }

  return { rows: { _array: filtered, length: filtered.length } };
}

function parseDelete(sql: string, params: unknown[]) {
  const match = sql.match(/DELETE\s+FROM\s+(\w+)\s+WHERE\s+(.+)/i);
  if (!match) return null;
  const tableName = match[1];
  const conditions = match[2];
  return { tableName, conditions, params };
}

function parseUpdate(sql: string, params: unknown[]) {
  const match = sql.match(/UPDATE\s+(\w+)\s+SET\s+(.+?)\s+WHERE\s+(.+)/i);
  if (!match) return null;
  const tableName = match[1];
  const setClause = match[2];
  const whereClause = match[3];
  return { tableName, setClause, whereClause, params };
}

function executeSQL(sql: string, params: unknown[] = []) {
  ensureTables();
  const trimmed = sql.trim();
  const upper = trimmed.toUpperCase();

  if (upper.startsWith('INSERT OR REPLACE')) {
    const parsed = parseInsertOrReplace(sql, params);
    if (parsed) {
      const table = tables[parsed.tableName];
      if (table) {
        const pkCol = parsed.columns[0];
        const pkVal = parsed.params[0];
        const existingIdx = table.rows.findIndex(r => r[pkCol] === pkVal);
        const newRow: Row = {};
        parsed.columns.forEach((col, i) => { newRow[col] = parsed.params[i]; });
        if (existingIdx >= 0) {
          table.rows[existingIdx] = newRow;
        } else {
          table.rows.push(newRow);
        }
      }
    }
    return { rows: { _array: [], length: 0 } };
  }

  if (upper.startsWith('SELECT')) {
    return parseSelect(sql, params);
  }

  if (upper.startsWith('DELETE')) {
    const parsed = parseDelete(sql, params);
    if (parsed) {
      const table = tables[parsed.tableName];
      if (table) {
        const whereMatch = parsed.conditions.match(/(\w+)\s*=\s*\?/);
        if (whereMatch) {
          const colName = whereMatch[1];
          const val = params[0];
          table.rows = table.rows.filter(r => r[colName] !== val);
        }
      }
    }
    return { rows: { _array: [], length: 0 } };
  }

  if (upper.startsWith('UPDATE')) {
    const parsed = parseUpdate(sql, params);
    if (parsed) {
      const table = tables[parsed.tableName];
      if (table) {
        // UPDATE table SET col = ? WHERE id = ?
        const setMatch = parsed.setClause.match(/(\w+)\s*=\s*\?/);
        const whereMatch = parsed.whereClause.match(/(\w+)\s*=\s*\?/);
        if (setMatch && whereMatch) {
          const setCol = setMatch[1];
          const whereCol = whereMatch[1];
          const setVal = params[0];
          const whereVal = params[1];
          table.rows = table.rows.map(r => {
            if (r[whereCol] === whereVal) {
              return { ...r, [setCol]: setVal };
            }
            return r;
          });
        }
      }
    }
    return { rows: { _array: [], length: 0 } };
  }

  return { rows: { _array: [], length: 0 } };
}

const mockDb = {
  execute: jest.fn((sql: string, params?: unknown[]) => executeSQL(sql, params ?? [])),
  transaction: jest.fn(async (cb: (tx: { execute: (sql: string, params?: unknown[]) => any }) => Promise<void>) => {
    const tx = {
      execute: jest.fn((sql: string, params?: unknown[]) => executeSQL(sql, params ?? [])),
    };
    await cb(tx);
  }),
  close: jest.fn(),
};

export function getMockDatabase() {
  return mockDb;
}

export function resetMockDatabase() {
  for (const key of Object.keys(tables)) {
    tables[key].rows = [];
  }
  mockDb.execute.mockClear();
  mockDb.transaction.mockClear();
}

export function getMockTableRows(tableName: string): Row[] {
  ensureTables();
  return tables[tableName]?.rows ?? [];
}
