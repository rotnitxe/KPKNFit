export type SyncKey =
  | 'programs'
  | 'history'
  | 'settings'
  | 'bodyProgress'
  | 'nutritionLogs'
  | 'wellbeing';

let lastSyncTime: Date | null = null;

export async function pushToCloud(): Promise<{ pushed: string[]; errors: string[] }> {
  lastSyncTime = new Date();
  return {
    pushed: [],
    errors: ['Supabase sync no está configurado en esta build RN.'],
  };
}

export async function pullFromCloud(): Promise<{ pulled: string[]; skipped: string[]; errors: string[] }> {
  lastSyncTime = new Date();
  return {
    pulled: [],
    skipped: ['programs', 'history', 'settings', 'bodyProgress', 'nutritionLogs', 'wellbeing'],
    errors: ['Supabase sync no está configurado en esta build RN.'],
  };
}

export async function bidirectionalSync(): Promise<{
  pushed: string[];
  pulled: string[];
  skipped: string[];
  errors: string[];
}> {
  const [pushResult, pullResult] = await Promise.all([pushToCloud(), pullFromCloud()]);
  return {
    pushed: pushResult.pushed,
    pulled: pullResult.pulled,
    skipped: pullResult.skipped,
    errors: [...pushResult.errors, ...pullResult.errors],
  };
}

export function getLastSyncTime(): Date | null {
  return lastSyncTime;
}
