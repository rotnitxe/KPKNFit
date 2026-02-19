// services/crashRecoveryService.ts
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';
import { OngoingWorkoutState } from '../types';

const SNAPSHOT_FILE = 'current_session_backup.json';

/**
 * Saves a snapshot of the current workout state to the device's disk.
 * This is an atomic operation that overwrites the previous snapshot.
 * @param data The current OngoingWorkoutState.
 */
export const saveSnapshot = async (data: OngoingWorkoutState | null): Promise<void> => {
    try {
        if (data === null) {
            // If the workout is finished, ensure the snapshot is deleted.
            await deleteSnapshot();
            return;
        }
        await Filesystem.writeFile({
            path: SNAPSHOT_FILE,
            data: JSON.stringify(data),
            directory: Directory.Data,
            encoding: Encoding.UTF8,
        });
    } catch (error) {
        console.error("CrashRecoveryService: Failed to save snapshot.", error);
    }
};

/**
 * Loads a workout state snapshot from the disk, if one exists.
 * @returns The parsed OngoingWorkoutState or null if no snapshot is found or if an error occurs.
 */
export const loadSnapshot = async (): Promise<OngoingWorkoutState | null> => {
    try {
        const { data } = await Filesystem.readFile({
            path: SNAPSHOT_FILE,
            directory: Directory.Data,
            encoding: Encoding.UTF8,
        });
        if (typeof data === 'string') {
            return JSON.parse(data) as OngoingWorkoutState;
        }
        return null;
    } catch (error) {
        // This is an expected error if the file doesn't exist, so we don't log it loudly.
        // console.log("CrashRecoveryService: No snapshot found to load.");
        return null;
    }
};

/**
 * Deletes the workout state snapshot from the disk.
 * This should be called on successful workout completion or explicit cancellation.
 */
export const deleteSnapshot = async (): Promise<void> => {
    try {
        await Filesystem.deleteFile({
            path: SNAPSHOT_FILE,
            directory: Directory.Data,
        });
    } catch (error) {
        // It's okay if the file doesn't exist, so we only log other errors.
        if ((error as any).message !== 'File does not exist') {
            console.error("CrashRecoveryService: Failed to delete snapshot.", error);
        }
    }
};
