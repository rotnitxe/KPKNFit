/**
 * @format
 */

import 'react-native-gesture-handler';
import './src/styles/global.css';
import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);
