import React, { useEffect } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar, View, Text, TouchableOpacity, StyleSheet, ActivityIndicator, AppState } from 'react-native';
import notifee, { EventType } from '@notifee/react-native';
import { useShallow } from 'zustand/react/shallow';
import { AppNavigator } from './src/navigation/AppNavigator';
import { useBootstrapStore } from './src/stores/bootstrapStore';
import { ThemeProvider, useTheme } from './src/theme';
import { backgroundModule } from './src/modules/background';
import {
  rescheduleCoreNotificationsFromStorage,
  syncNotificationPermissionState,
} from './src/services/mobileNotificationService';
import { navigateFromExternalTarget } from './src/navigation/navigationRef';
import { ErrorBoundary } from './src/components/ErrorBoundary';

function getNotificationTarget(value: unknown) {
  return typeof value === 'string' ? value : undefined;
}

import { CaupolicanIcon } from './src/components/CaupolicanIcon';

// Create inner component that can use useTheme
function AppContent() {
  const { colors, isDark } = useTheme();
  const { status, error, retry, bootstrap } = useBootstrapStore(useShallow(state => ({
    status: state.status,
    error: state.error,
    bootstrap: state.bootstrap,
    retry: state.retry,
  })));

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  return (
    <>
      <StatusBar
        barStyle={isDark ? 'light-content' : 'dark-content'}
        backgroundColor={colors.background}
      />
      {status === 'booting' && (
        <View style={[styles.centerContainer, { backgroundColor: colors.background }]}>
          <CaupolicanIcon size={120} color={colors.primary} style={{ marginBottom: 12 }} />
          <Text style={[styles.brandText, { color: colors.onSurface }]}>KPKN</Text>
          <ActivityIndicator size="small" color={colors.primary} style={{ marginTop: 24 }} />
          <Text style={[styles.bootingText, { color: colors.onSurfaceVariant }]}>Cargando experiencia...</Text>
        </View>
      )}
      {status === 'failed' && (
        <View style={[styles.centerContainer, { backgroundColor: colors.background }]}>
          <Text style={[styles.errorTitle, { color: colors.onSurface }]}>No se pudo iniciar la app</Text>
          <Text style={[styles.errorDetail, { color: colors.error }]}>
            {error ?? 'Ocurrió un error inesperado durante el arranque.'}
          </Text>
          <TouchableOpacity
            style={[styles.retryButton, {
              backgroundColor: colors.surfaceContainer,
              borderColor: colors.outline
            }]}
            onPress={() => void retry()}
            accessibilityLabel="Reintentar inicio de la aplicación"
          >
            <Text style={[styles.retryButtonText, { color: colors.onSurface }]}>Reintentar</Text>
          </TouchableOpacity>
        </View>
      )}
      {status === 'ready' && (
        <ErrorBoundary>
          <AppNavigator />
        </ErrorBoundary>
      )}
    </>
  );
}

export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider initialDark={false}>
          <AppContent />
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  centerContainer: {
    flex: 1,
    backgroundColor: '#0A0A0A',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 16,
  },
  bootingText: {
    color: '#888888',
    fontSize: 12,
    fontWeight: '500',
    fontFamily: 'System',
    marginTop: 8,
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  brandText: {
    fontSize: 32,
    fontWeight: '900',
    letterSpacing: 4,
  },
  errorTitle: {
    color: '#FFFFFF',
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
  },
  errorDetail: {
    color: '#FF6060',
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 20,
    fontFamily: 'System',
  },
  retryButton: {
    marginTop: 16,
    backgroundColor: '#1A1A1A',
    borderWidth: 1,
    borderColor: '#333333',
    borderRadius: 12,
    paddingHorizontal: 32,
    paddingVertical: 14,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
});
