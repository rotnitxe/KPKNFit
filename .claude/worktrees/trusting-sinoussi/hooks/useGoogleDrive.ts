import { useState, useEffect, useCallback, useMemo } from 'react';
import * as driveService from '../services/googleDriveService';
// FIX: Changed to a type-only import to break circular dependency with types.ts
import type { GoogleUserProfile, Settings, ToastData, UseGoogleDriveReturn } from '../types';
import { storageService } from '../services/storageService';

interface UseGoogleDriveProps {
  onLoad: (data: any) => void;
  settings: Settings;
  addToast: (message: string, type?: ToastData['type'], title?: string, duration?: number) => void;
}

function useGoogleDrive({ onLoad, settings, addToast }: UseGoogleDriveProps): UseGoogleDriveReturn {
  const { googleClientId } = settings;
  const isSupported = useMemo(() => !!googleClientId && !googleClientId.startsWith('YOUR'), [googleClientId]);

  const [isSignedIn, setIsSignedIn] = useState(false);
  const [isAuthLoading, setIsAuthLoading] = useState(true);
  const [isSyncing, setIsSyncing] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [user, setUser] = useState<GoogleUserProfile | null>(null);
  const [lastSyncDate, setLastSyncDate] = useState<string | null>(null);

  const updateAuthStatus = useCallback(async (tokenResponse: any) => {
    setIsAuthLoading(true);
    if (tokenResponse && tokenResponse.access_token) {
      window.gapi.client.setToken(tokenResponse);
      const profile = await driveService.getProfile();
      setUser(profile);
      setIsSignedIn(true);
      const syncDate = await driveService.getLastSyncDate();
      setLastSyncDate(syncDate);
    } else {
      setUser(null);
      setIsSignedIn(false);
    }
    setIsAuthLoading(false);
  }, []);
  
  useEffect(() => {
    if (!isSupported) {
        setIsAuthLoading(false);
        return;
    };
    
    const init = async () => {
      await driveService.initializeGisClient(updateAuthStatus, googleClientId);
    };
    init();

  }, [isSupported, updateAuthStatus, googleClientId]);
  
  const signIn = () => {
    driveService.handleSignIn();
  };

  const signOut = () => {
    driveService.handleSignOut();
    setUser(null);
    setIsSignedIn(false);
  };

  const syncToDrive = useCallback(async () => {
    if (!isSignedIn) {
      addToast("Debes iniciar sesión para sincronizar.", 'danger');
      return;
    }
    setIsSyncing(true);
    try {
      const dataToSync = {
        programs: await storageService.get('programs') || [],
        history: await storageService.get('history') || [],
        settings: await storageService.get('yourprime-settings') || {},
        'body-progress': await storageService.get('body-progress') || [],
        'nutrition-logs': await storageService.get('nutrition-logs') || [],
        'skipped-logs': await storageService.get('skipped-logs') || [],
      };
      await driveService.uploadData(JSON.stringify(dataToSync));
      const syncDate = await driveService.getLastSyncDate();
      setLastSyncDate(syncDate);
      addToast('Sincronización con Google Drive completada.', 'success');
    } catch (e: any) {
      console.error(e);
      addToast(`Error al sincronizar: ${e.message}`, 'danger');
    } finally {
      setIsSyncing(false);
    }
  }, [isSignedIn, addToast]);

  const loadFromDrive = useCallback(async () => {
    if (!isSignedIn) {
      addToast("Debes iniciar sesión para cargar datos.", 'danger');
      return;
    }
    if (!window.confirm("¿Estás seguro? Esto sobrescribirá tus datos locales con los datos de Google Drive.")) {
      return;
    }
    setIsLoading(true);
    try {
      const data = await driveService.downloadData();
      onLoad(data);
    } catch (e: any) {
      console.error(e);
      addToast(`Error al cargar datos: ${e.message}`, 'danger');
    } finally {
      setIsLoading(false);
    }
  }, [isSignedIn, onLoad, addToast]);

  return { isSupported, isSignedIn, isAuthLoading, isSyncing, isLoading, user, lastSyncDate, signIn, signOut, syncToDrive, loadFromDrive };
}

export default useGoogleDrive;
