/**
 * Este archivo se importa al inicio de cada test que toca SQLite.
 * Reemplaza el getMobileDatabase() real por el mock in-memory.
 */
import { getMockDatabase, resetMockDatabase } from './mockDatabase';

// Mock del módulo de base de datos
jest.mock('../../storage/mobileDatabase', () => {
  const { getMockDatabase: getMockDb } = require('./mockDatabase');
  return {
    getMobileDatabase: () => getMockDb(),
    initMobileDatabase: jest.fn(),
    DatabaseOpenError: class extends Error {
      constructor(msg: string) {
        super(msg);
        this.name = 'DatabaseOpenError';
      }
    },
  };
});

// Re-exportamos para conveniencia en los tests
export { getMockDatabase, resetMockDatabase, getMockTableRows } from './mockDatabase';
