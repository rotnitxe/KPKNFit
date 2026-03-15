/**
 * @format
 */

import 'react-native-gesture-handler';
import './src/styles/global.css';
import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';
import runBackgroundSyncTask from './src/services/backgroundSyncTask';

AppRegistry.registerComponent(appName, () => App);
AppRegistry.registerHeadlessTask('KPKNBackgroundSync', () => runBackgroundSyncTask);
