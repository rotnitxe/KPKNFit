
// hooks/useMuscleRecovery.ts
import { useState, useEffect, useRef } from 'react';
import { useAppState } from '../contexts/AppContext';
import { calculateMuscleBatteryAsync } from '../services/computeWorkerService';
import { MuscleRecoveryStatus } from '../types';

interface UseMuscleRecoveryReturn {
    recoveryData: MuscleRecoveryStatus[];
    isLoading: boolean;
}

const TARGET_MUSCLES = [
    'Pectorales', 'Dorsales', 'Deltoides', 'Bíceps', 'Tríceps',
    'Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas', 'Abdomen', 'Espalda Baja'
];

export const useMuscleRecovery = (): UseMuscleRecoveryReturn => {
    const { history, exerciseList, isAppLoading, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs } = useAppState();

    const [recoveryData, setRecoveryData] = useState<MuscleRecoveryStatus[]>([]);
    const [isComputing, setIsComputing] = useState(false);
    const versionRef = useRef(0);

    useEffect(() => {
        if (isAppLoading || !history || !exerciseList) {
            setRecoveryData([]);
            return;
        }

        const version = ++versionRef.current;
        setIsComputing(true);

        const computeAll = async () => {
            try {
                const results = await Promise.all(
                    TARGET_MUSCLES.map(muscleName =>
                        calculateMuscleBatteryAsync(
                            muscleName, history, exerciseList, sleepLogs,
                            settings, muscleHierarchy, postSessionFeedback, waterLogs,
                            dailyWellbeingLogs || [], nutritionLogs || []
                        ).then(batteryData => ({
                            muscleId: muscleName.toLowerCase().replace(/\s/g, '-'),
                            muscleName,
                            ...batteryData
                        } as MuscleRecoveryStatus))
                    )
                );

                if (versionRef.current === version) {
                    setRecoveryData(results.sort((a, b) => a.recoveryScore - b.recoveryScore));
                }
            } catch {
                // Fallback: if worker fails, data stays at previous value
            } finally {
                if (versionRef.current === version) {
                    setIsComputing(false);
                }
            }
        };

        computeAll();
    }, [history, exerciseList, isAppLoading, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs]);

    return { recoveryData, isLoading: isAppLoading || isComputing };
};
