const path = require('path');

module.exports = {
  input: 'dist/esm/index.js',
  output: [
    {
      file: 'dist/plugin.cjs.js',
      format: 'cjs',
      sourcemap: true,
    },
    {
      file: 'dist/plugin.js',
      format: 'es',
      sourcemap: true,
    },
  ],
  external: ['@capacitor/core'],
  plugins: [],
};
