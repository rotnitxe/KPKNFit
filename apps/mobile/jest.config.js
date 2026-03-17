/** @type {import('jest').Config} */
module.exports = {
  preset: 'react-native',
  testPathIgnorePatterns: [
    '/node_modules/',
    '/android/',
    '/ios/',
    '/__tests__/mocks/',
    '/__tests__/fixtures/',
    'src/__tests__/mocks/',
    'src/__tests__/fixtures/',
  ],
  transformIgnorePatterns: [
    'node_modules/(?!(react-native|@react-native|@react-navigation|@notifee|nativewind|react-native-.*)/)',
  ],
  moduleNameMapper: {
    '^@kpkn/shared-types$': '<rootDir>/../../packages/shared-types/src/index.ts',
    '^@kpkn/shared-domain$': '<rootDir>/../../packages/shared-domain/src/index.ts',
    '^@kpkn/design-tokens$': '<rootDir>/../../packages/design-tokens/src/index.ts',
  },
  setupFiles: ['./jest.setup.js'],

  // ── Coverage ────────────────────────────────────────────────────────────────
  // V8 coverage provider bypasses babel-plugin-istanbul, which crashes on
  // Windows due to a minimatch v9 incompatibility in test-exclude.
  coverageProvider: 'v8',
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
    '!src/__tests__/**',
    '!src/**/index.ts',          // barrel re-exports
  ],
  coveragePathIgnorePatterns: [
    '/node_modules/',
    '/android/',
    '/ios/',
    '/__tests__/',
    '/__mocks__/',
  ],
  coverageThreshold: {
    global: {
      statements: 25,
      branches: 15,
      functions: 20,
      lines: 25,
    },
  },
};