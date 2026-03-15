import React, { useEffect } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar, View, Text, TouchableOpacity, StyleSheet, ActivityIndicator, AppState } from 'react-native';
import notifee, { EventType } from '@notifee/react-native';
import { useShallow } from 'zustand/react/shallow';
import { AppNavigator } from './src/navigation/AppNavigator';
import { useBootstrapStore } from './src/stores/bootstrapStore';
import { backgroundModule } from './src/modules/background';
import {
  rescheduleCoreNotificationsFromStorage,
  syncNotificationPermissionState,
} from './src/services/mobileNotificationService';
import { navigateFromExternalTarget } from './src/navigation/navigationRef';

function getNotificationTarget(value: unknown) {
  return typeof value === 'string' ? value : undefined;
}

export default function App() {
  const { status, error, bootstrap, retry } = useBootstrapStore(useShallow(state => ({
    status: state.status,
    error: state.error,
    bootstrap: state.bootstrap,
    retry: state.retry,
  })));

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  useEffect(() => {
    if (status !== 'ready') return;

    void backgroundModule.schedulePeriodicSync().catch(error => {
      console.warn('[background] No se pudo programar el sync periódico.', error);
    });

    void rescheduleCoreNotificationsFromStorage().catch(error => {
      console.warn('[notifications] No se pudieron reprogramar los recordatorios al iniciar.', error);
    });
  }, [status]);

  useEffect(() => {
    if (status !== 'ready') return undefined;

    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState !== 'active') return;

      void syncNotificationPermissionState().catch(error => {
        console.warn('[notifications] No se pudo refrescar el permiso al volver a la app.', error);
      });
    });

    return () => {
      subscription.remove();
    };
  }, [status]);

  useEffect(() => {
    if (status !== 'ready') return undefined;

    const unsubscribe = notifee.onForegroundEvent(({ type, detail }) => {
      if (type !== EventType.PRESS) return;
      navigateFromExternalTarget(getNotificationTarget(detail.notification?.data?.screen));
    });

    void notifee.getInitialNotification().then(initialNotification => {
      navigateFromExternalTarget(getNotificationTarget(initialNotification?.notification?.data?.screen));
    });

    return () => {
      unsubscribe();
    };
  }, [status]);

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <StatusBar barStyle="light-content" backgroundColor="#000000" />
        {status === 'booting' && (
          <View style={styles.centerContainer}>
            <ActivityIndicator size="large" color="#FFFFFF" />
            <Text style={styles.bootingText}>Iniciando KPKN…</Text>
          </View>
        )}
        {status === 'failed' && (
          <View style={styles.centerContainer}>
            <Text style={styles.errorTitle}>No se pudo iniciar la app</Text>
            <Text style={styles.errorDetail}>
              {error ?? 'Ocurrió un error inesperado durante el arranque.'}
            </Text>
            <TouchableOpacity
              style={styles.retryButton}
              onPress={() => void retry()}
              accessibilityLabel="Reintentar inicio de la aplicación"
            >
              <Text style={styles.retryButtonText}>Reintentar</Text>
            </TouchableOpacity>
          </View>
        )}
        {status === 'ready' && <AppNavigator />}
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
    fontSize: 14,
    fontFamily: 'System',
    marginTop: 12,
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
