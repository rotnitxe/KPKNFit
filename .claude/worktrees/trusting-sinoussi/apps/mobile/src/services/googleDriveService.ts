export interface DriveSyncStatus {
  connected: boolean;
  provider: 'google-drive';
  lastSyncAt: string | null;
  note: string;
}

export async function getDriveSyncStatus(): Promise<DriveSyncStatus> {
  return {
    connected: false,
    provider: 'google-drive',
    lastSyncAt: null,
    note: 'Integracion de Google Drive pendiente en RN.',
  };
}

export async function exportBackupToDrive(): Promise<{ ok: boolean; message: string }> {
  return {
    ok: false,
    message: 'Exportar backup a Drive aun no esta habilitado en RN.',
  };
}

