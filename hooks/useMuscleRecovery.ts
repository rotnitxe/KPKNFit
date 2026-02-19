
// hooks/useMuscleRecovery.ts
import { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { calculateMuscleBattery } from '../services/recoveryService';
import { MuscleRecoveryStatus } from '../types';

interface UseMuscleRecoveryReturn {
    recoveryData: MuscleRecoveryStatus[];
    isLoading: boolean;
}

export const useMuscleRecovery = (): UseMuscleRecoveryReturn => {
    // FIX: Added waterLogs to destruction
    const { history, exerciseList, isAppLoading, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs } = useAppState();

    const isLoading = isAppLoading;

    const recoveryData = useMemo<MuscleRecoveryStatus[]>(() => {
        if (isLoading || !history || !exerciseList) {
            return [];
        }

        // Definimos los grupos musculares que queremos trackear en la batería
        const targetMuscles = [
            'Pectorales', 'Dorsales', 'Deltoides', 'Bíceps', 'Tríceps', 
            'Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas', 'Abdomen', 'Espalda Baja'
        ];

        const allStatuses = targetMuscles.map(muscleName => {
            // FIX: Added waterLogs to function call
            const batteryData = calculateMuscleBattery(
                muscleName, 
                history, 
                exerciseList, 
                sleepLogs, 
                settings,
                muscleHierarchy,
                postSessionFeedback,
                waterLogs
            );
            return {
                muscleId: muscleName.toLowerCase().replace(/\s/g, '-'),
                muscleName: muscleName,
                ...batteryData
            } as MuscleRecoveryStatus;
        });

        return allStatuses.sort((a, b) => a.recoveryScore - b.recoveryScore);

    }, [history, exerciseList, isLoading, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs]);

    return { recoveryData, isLoading };
};
