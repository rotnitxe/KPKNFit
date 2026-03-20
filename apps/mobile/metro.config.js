const path = require('path');
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const projectRoot = __dirname;
const workspaceRoot = path.resolve(projectRoot, '../..');

const config = {
  watchFolders: [workspaceRoot],
  transformer: {
    getTransformOptions: async () => ({
      transform: {
        experimentalImportSupport: false,
        inlineRequires: true,
      },
    }),
  },
  resolver: {
    ...getDefaultConfig(projectRoot).resolver,
    disableHierarchicalLookup: true,
    nodeModulesPaths: [
      path.resolve(projectRoot, 'node_modules'),
      path.resolve(workspaceRoot, 'node_modules'),
    ],
    extraNodeModules: {
      react: path.resolve(workspaceRoot, 'node_modules/react'),
      'react-native': path.resolve(workspaceRoot, 'node_modules/react-native'),
      '@kpkn/shared-types': path.resolve(workspaceRoot, 'packages/shared-types'),
      '@kpkn/shared-domain': path.resolve(workspaceRoot, 'packages/shared-domain'),
      '@kpkn/design-tokens': path.resolve(workspaceRoot, 'packages/design-tokens'),
    },
  },
};

process.env.RN_CSS_INTEROP_DISABLE = '1';

module.exports = mergeConfig(getDefaultConfig(projectRoot), config);
