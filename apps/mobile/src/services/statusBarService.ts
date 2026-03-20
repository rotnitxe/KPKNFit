import { Platform, StatusBar } from 'react-native';

export type MobileStatusBarStyle = 'light' | 'dark';

function mapStyle(style: MobileStatusBarStyle) {
  return style === 'light' ? 'light-content' : 'dark-content';
}

export function setStatusBarStyle(style: MobileStatusBarStyle, animated = true) {
  StatusBar.setBarStyle(mapStyle(style), animated);
}

export function setStatusBarHidden(hidden: boolean, animation: 'none' | 'fade' | 'slide' = 'fade') {
  StatusBar.setHidden(hidden, animation);
}

export function setStatusBarBackground(color: string, animated = true) {
  if (Platform.OS === 'android') {
    StatusBar.setBackgroundColor(color, animated);
  }
}

export function setStatusBarTransparent() {
  if (Platform.OS === 'android') {
    StatusBar.setTranslucent(true);
    StatusBar.setBackgroundColor('transparent', true);
  }
}

