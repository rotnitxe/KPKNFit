module.exports = {
  dependencies: {
    // Exclusion explícita de plugins y librerías de Capacitor
    // Evita que el autolinker de React Native intente evaluarlos y cause fallos en Gradle,
    // ya que asumen estar dentro del contexto de Capacitor (con project(':capacitor-android')).
    'capacitor-widget-bridge': { platforms: { android: null, ios: null } },
    '@capacitor/app': { platforms: { android: null, ios: null } },
    '@capacitor/browser': { platforms: { android: null, ios: null } },
    '@capacitor/camera': { platforms: { android: null, ios: null } },
    '@capacitor/core': { platforms: { android: null, ios: null } },
    '@capacitor/filesystem': { platforms: { android: null, ios: null } },
    '@capacitor/geolocation': { platforms: { android: null, ios: null } },
    '@capacitor/haptics': { platforms: { android: null, ios: null } },
    '@capacitor/keyboard': { platforms: { android: null, ios: null } },
    '@capacitor/local-notifications': { platforms: { android: null, ios: null } },
    '@capacitor/network': { platforms: { android: null, ios: null } },
    '@capacitor/preferences': { platforms: { android: null, ios: null } },
    '@capacitor/screen-orientation': { platforms: { android: null, ios: null } },
    '@capacitor/share': { platforms: { android: null, ios: null } },
    '@capacitor/splash-screen': { platforms: { android: null, ios: null } },
    '@capacitor/status-bar': { platforms: { android: null, ios: null } },
    '@capacitor-community/keep-awake': { platforms: { android: null, ios: null } },
    '@capacitor-community/native-audio': { platforms: { android: null, ios: null } },
    '@capacitor-community/text-to-speech': { platforms: { android: null, ios: null } },
    '@capawesome/capacitor-app-update': { platforms: { android: null, ios: null } }
  }
};
