/** @type {import('ts-jest').JestConfigWithTsJest} */
export default {
  testEnvironment: 'node',
  extensionsToTreatAsEsm: ['.ts'],
  transform: {
    '^.+\\.ts$': [
      'ts-jest',
      {
        useESM: true,
        tsconfig: {
          module: 'ESNext',
          moduleResolution: 'bundler',
          target: 'ESNext',
          strict: true,
          esModuleInterop: true,
          allowSyntheticDefaultImports: true,
          jsx: 'preserve',
          types: ['jest'],
          paths: {
            '@kpkn/shared-types': ['../shared-types/src/index.ts']
          }
        }
      }
    ]
  },
  moduleNameMapper: {
    '^@kpkn/shared-types$': '<rootDir>/../shared-types/src/index.ts'
  },
  testMatch: ['<rootDir>/tests/**/*.test.ts']
};
