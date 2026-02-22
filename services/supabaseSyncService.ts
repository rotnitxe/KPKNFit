/**
 * Supabase Sync Service – Bidirectional sync with timestamp-based conflict resolution.
 * Replaces the Google Drive manual backup flow.
 *
 * DB schema (user_data table):
 *   id         UUID PRIMARY KEY DEFAULT uuid_generate_v4()
 *   user_id    UUID REFERENCES auth.users(id) ON DELETE CASCADE
 *   data_key   TEXT NOT NULL        -- e.g. 'programs', 'history', 'settings'
 *   data       JSONB NOT NULL
 *   updated_at TIMESTAMPTZ DEFAULT NOW()
 *   UNIQUE(user_id, data_key)
 *
 * RLS policies:
 *   SELECT: auth.uid() = user_id
 *   INSERT: auth.uid() = user_id
 *   UPDATE: auth.uid() = user_id
 */
import { supabase } from './supabaseService';
import { storageService } from './storageService';

export type SyncKey = 'programs' | 'history' | 'settings' | 'bodyProgress' |
    'nutritionLogs' | 'sleepLogs' | 'exerciseList' | 'skippedLogs' | 'activeProgramState';

const SYNC_KEYS: SyncKey[] = [
    'programs', 'history', 'settings', 'bodyProgress',
    'nutritionLogs', 'sleepLogs', 'exerciseList', 'skippedLogs', 'activeProgramState',
];

interface SyncRecord {
    data_key: string;
    data: any;
    updated_at: string;
}

function getLocalTimestamp(key: string): number {
    const raw = localStorage.getItem(`kpkn_sync_ts_${key}`);
    return raw ? parseInt(raw, 10) : 0;
}

function setLocalTimestamp(key: string, ts: number) {
    localStorage.setItem(`kpkn_sync_ts_${key}`, String(ts));
}

async function getLocalData(key: SyncKey): Promise<any> {
    const keyMap: Record<SyncKey, string> = {
        programs: 'yourprime-programs',
        history: 'yourprime-history',
        settings: 'yourprime-settings',
        bodyProgress: 'yourprime-bodyProgress',
        nutritionLogs: 'yourprime-nutritionLogs',
        sleepLogs: 'yourprime-sleepLogs',
        exerciseList: 'yourprime-exerciseList',
        skippedLogs: 'yourprime-skippedLogs',
        activeProgramState: 'yourprime-activeProgramState',
    };
    return storageService.get(keyMap[key]);
}

async function setLocalData(key: SyncKey, data: any): Promise<void> {
    const keyMap: Record<SyncKey, string> = {
        programs: 'yourprime-programs',
        history: 'yourprime-history',
        settings: 'yourprime-settings',
        bodyProgress: 'yourprime-bodyProgress',
        nutritionLogs: 'yourprime-nutritionLogs',
        sleepLogs: 'yourprime-sleepLogs',
        exerciseList: 'yourprime-exerciseList',
        skippedLogs: 'yourprime-skippedLogs',
        activeProgramState: 'yourprime-activeProgramState',
    };
    await storageService.set(keyMap[key], data);
}


/**
 * Push all local data to Supabase (upsert).
 * Sets updated_at to now.
 */
export async function pushToCloud(): Promise<{ pushed: string[]; errors: string[] }> {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) throw new Error('Not authenticated');

    const pushed: string[] = [];
    const errors: string[] = [];
    const now = Date.now();

    for (const key of SYNC_KEYS) {
        try {
            const localData = await getLocalData(key);
            if (localData === null || localData === undefined) continue;

            const { error } = await supabase.from('user_data').upsert({
                user_id: user.id,
                data_key: key,
                data: localData,
                updated_at: new Date(now).toISOString(),
            }, { onConflict: 'user_id,data_key' });

            if (error) {
                errors.push(`${key}: ${error.message}`);
            } else {
                setLocalTimestamp(key, now);
                pushed.push(key);
            }
        } catch (e: any) {
            errors.push(`${key}: ${e.message}`);
        }
    }

    return { pushed, errors };
}


/**
 * Pull all data from Supabase and overwrite local if remote is newer.
 * Uses timestamp comparison for conflict resolution.
 */
export async function pullFromCloud(): Promise<{ pulled: string[]; skipped: string[]; errors: string[] }> {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) throw new Error('Not authenticated');

    const { data: records, error } = await supabase
        .from('user_data')
        .select('data_key, data, updated_at')
        .eq('user_id', user.id);

    if (error) throw error;

    const pulled: string[] = [];
    const skipped: string[] = [];
    const errors: string[] = [];

    for (const record of (records as SyncRecord[] || [])) {
        const key = record.data_key as SyncKey;
        if (!SYNC_KEYS.includes(key)) continue;

        try {
            const remoteTs = new Date(record.updated_at).getTime();
            const localTs = getLocalTimestamp(key);

            if (remoteTs > localTs) {
                await setLocalData(key, record.data);
                setLocalTimestamp(key, remoteTs);
                pulled.push(key);
            } else {
                skipped.push(key);
            }
        } catch (e: any) {
            errors.push(`${key}: ${e.message}`);
        }
    }

    return { pulled, skipped, errors };
}


/**
 * Full bidirectional sync:
 * 1. Pull remote records
 * 2. For each key, compare timestamps
 * 3. If remote is newer → overwrite local
 * 4. If local is newer → push to remote
 * 5. If equal → skip
 */
export async function bidirectionalSync(): Promise<{
    pulledKeys: string[];
    pushedKeys: string[];
    errors: string[];
}> {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) throw new Error('Not authenticated');

    const { data: records, error } = await supabase
        .from('user_data')
        .select('data_key, data, updated_at')
        .eq('user_id', user.id);

    if (error) throw error;

    const remoteMap = new Map<string, SyncRecord>();
    for (const r of (records as SyncRecord[] || [])) {
        remoteMap.set(r.data_key, r);
    }

    const pulledKeys: string[] = [];
    const pushedKeys: string[] = [];
    const errors: string[] = [];
    const now = Date.now();

    for (const key of SYNC_KEYS) {
        try {
            const localData = await getLocalData(key);
            const localTs = getLocalTimestamp(key);
            const remote = remoteMap.get(key);
            const remoteTs = remote ? new Date(remote.updated_at).getTime() : 0;

            if (remote && remoteTs > localTs) {
                await setLocalData(key, remote.data);
                setLocalTimestamp(key, remoteTs);
                pulledKeys.push(key);
            } else if (localData !== null && localData !== undefined && localTs > remoteTs) {
                const { error: upsertErr } = await supabase.from('user_data').upsert({
                    user_id: user.id,
                    data_key: key,
                    data: localData,
                    updated_at: new Date(now).toISOString(),
                }, { onConflict: 'user_id,data_key' });

                if (upsertErr) {
                    errors.push(`push ${key}: ${upsertErr.message}`);
                } else {
                    setLocalTimestamp(key, now);
                    pushedKeys.push(key);
                }
            }
        } catch (e: any) {
            errors.push(`${key}: ${e.message}`);
        }
    }

    return { pulledKeys, pushedKeys, errors };
}


/**
 * Get the last sync timestamp for display purposes.
 */
export function getLastSyncTime(): Date | null {
    let maxTs = 0;
    for (const key of SYNC_KEYS) {
        const ts = getLocalTimestamp(key);
        if (ts > maxTs) maxTs = ts;
    }
    return maxTs > 0 ? new Date(maxTs) : null;
}
