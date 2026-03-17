// Mock react-native-reanimated
jest.mock('react-native-reanimated', () => {
  const React = require('react');
  const { View } = require('react-native');
  return {
    default: {
      View: View,
      Text: View,
      ScrollView: View,
      Image: View,
      createAnimatedComponent: (c) => c,
    },
    createAnimatedComponent: (c) => c,
    useSharedValue: (val) => ({ value: val }),
    useAnimatedStyle: () => ({}),
    withSpring: (val) => val,
    withTiming: (val) => val,
    withDelay: (_, val) => val,
    withRepeat: (val) => val,
    withSequence: (...vals) => vals[0],
    cancelAnimation: () => {},
    runOnJS: (fn) => fn,
    runOnWorklet: (fn) => fn,
    interpolate: (val) => val,
    Extrapolate: { CLAMP: 'clamp' },
    FadeIn: { duration: () => ({}) },
    FadeOut: { duration: () => ({}) },
    SlideInRight: { duration: () => ({}) },
    SlideOutLeft: { duration: () => ({}) },
    Layout: { springify: () => ({}) },
  };
});
// Mock react-native-worklets
jest.mock('react-native-worklets', () => ({
  Worklets: {
    createContext: jest.fn(),
    createSharedValue: (val) => ({ value: val }),
    createRunOnJS: (fn) => fn,
    createRunOnWorklet: (fn) => fn,
  },
  createSerializable: (val) => val,
}));
// Mock react-native-gesture-handler
jest.mock('react-native-gesture-handler', () => {
  return {
    GestureHandlerRootView: ({ children }) => children,
    Swipeable: ({ children }) => children,
    DrawerLayout: ({ children }) => children,
    State: {},
    PanGestureHandler: ({ children }) => children,
    TapGestureHandler: ({ children }) => children,
    FlingGestureHandler: ({ children }) => children,
    ForceTouchGestureHandler: ({ children }) => children,
    LongPressGestureHandler: ({ children }) => children,
    PinchGestureHandler: ({ children }) => children,
    RotationGestureHandler: ({ children }) => children,
    RawButton: ({ children }) => children,
    BaseButton: ({ children }) => children,
    RectButton: ({ children }) => children,
    BorderlessButton: ({ children }) => children,
    NativeViewGestureHandler: ({ children }) => children,
  };
});
// Mock react-native-safe-area-context
jest.mock('react-native-safe-area-context', () => {
  const React = require('react');
  return {
    SafeAreaProvider: ({ children }) => children,
    SafeAreaView: ({ children }) => children,
    useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 0, left: 0 }),
  };
});
// Mock react-native-mmkv (storage usado por toda la app)
jest.mock('react-native-mmkv', () => {
  const store = new Map();
  return {
    MMKV: jest.fn().mockImplementation(() => ({
      set: jest.fn((key, value) => store.set(key, value)),
      getString: jest.fn(key => store.get(key)),
      getBoolean: jest.fn(key => store.get(key)),
      getNumber: jest.fn(key => store.get(key)),
      delete: jest.fn(key => store.delete(key)),
      contains: jest.fn(key => store.has(key)),
      getAllKeys: jest.fn(() => [...store.keys()]),
      clearAll: jest.fn(() => store.clear()),
    })),
  };
});
// Mock react-native-quick-sqlite (base de datos SQLite)
jest.mock('react-native-quick-sqlite', () => {
  const { getMockDatabase } = require('./src/__tests__/mocks/mockDatabase');
  return {
    open: jest.fn(() => getMockDatabase()),
  };
});
// Mock @notifee/react-native
jest.mock('@notifee/react-native', () => ({
  __esModule: true,
  default: {
    onForegroundEvent: jest.fn(() => jest.fn()),
    getInitialNotification: jest.fn(() => Promise.resolve(null)),
    requestPermission: jest.fn(() => Promise.resolve({ authorizationStatus: 1 })),
    createChannel: jest.fn(() => Promise.resolve('')),
    displayNotification: jest.fn(() => Promise.resolve('')),
    cancelAllNotifications: jest.fn(() => Promise.resolve()),
    getTriggerNotificationIds: jest.fn(() => Promise.resolve([])),
    getNotificationSettings: jest.fn(() => Promise.resolve({ authorizationStatus: 1 })),
    createChannels: jest.fn(() => Promise.resolve()),
    getChannels: jest.fn(() => Promise.resolve([])),
    getChannel: jest.fn(() => Promise.resolve(null)),
    deleteChannel: jest.fn(() => Promise.resolve()),
    cancelNotification: jest.fn(() => Promise.resolve()),
    createTriggerNotification: jest.fn(() => Promise.resolve('')),
  },
  EventType: { PRESS: 1 },
  AuthorizationStatus: { AUTHORIZED: 1, DENIED: 0 },
  TriggerType: { TIMESTAMP: 0 },
  AndroidImportance: { DEFAULT: 3, HIGH: 4, LOW: 2, MIN: 1, NONE: 0 },
}));