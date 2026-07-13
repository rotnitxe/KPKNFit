import React, { useEffect, useState } from 'react';
import { Session } from '../../types';
import { getSessionMusclesWithBatteries } from '../../utils/sessionMusclesForBattery';
import { getSessionArticularBatteries } from '../../utils/sessionArticularBatteries';
import { useAppState } from '../../contexts/AppContext';
import {
    getPerMuscleBatteries,
    getStructuralReadinessForMuscles,
    type StructuralReadinessBreakdown,
} from '../../services/auge';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';

const getBarColor = (value: number): string => {
    if (value >= 75) return '#10b981';
    if (value >= 50) return '#f59e0b';
    return '#78716c';
};

function getReadinessMessage(
    muscleAvg: number,
    articularAvg: number,
    combinedAvg: number,
    cns: number,
    spinal: number
): string {
    const bottleneck = Math.min(cns, spinal, combinedAvg);

    if (combinedAvg >= 80 && cns >= 70 && spinal >= 70) {
        return 'Todo bien. La lectura muscular y la estructural van alineadas para la sesiГіn de hoy.';
    }

    if (muscleAvg - articularAvg > 15) {
        return 'Muscularmente vas mejor que a nivel articular o tendinoso. Mira la media combinada antes de apretar con cargas altas.';
    }

    if (combinedAvg >= 70 && bottleneck < combinedAvg - 12) {
        if (cns < spinal) return 'La zona local va bien, pero el SNC viene mГЎs atrasado. Si hoy cuesta acelerar, baja un poco la intensidad.';
        return 'La zona local va bien, pero la espalda o la columna siguen algo cargadas. Calienta bien y cuida la tГ©cnica.';
    }

    if (combinedAvg >= 55) {
        if (bottleneck >= 65) return 'Recuperando. Puedes entrenar normal; si notas que cuesta, baja un poco el volumen.';
        return 'La zona objetivo sigue recuperando. Entrena tranquilo y cuida la tГ©cnica.';
    }

    if (combinedAvg >= 40) {
        return 'La media combinada sigue baja. Un dГ­a mГЎs suave o descansar podrГ­a venir bien; si entrenas, reduce el coste estructural.';
    }

    return 'La zona objetivo tiene poca baterГ­a. Si entrenas, baja series o peso. Si puedes, descansar es mejor.';
}

interface SessionReadinessBlockProps {
    session: Session;
    compact?: boolean;
}

export const SessionReadinessBlock: React.FC<SessionReadinessBlockProps> = ({
    session,
    compact = false,
}) => {
    const {
        history,
        exerciseList,
        sleepLogs,
        settings,
        muscleHierarchy,
        postSessionFeedback,
        waterLogs,
        dailyWellbeingLogs,
        nutritionLogs,
        isAppLoading,
    } = useAppState();

    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [globalBatteries, setGlobalBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);

    useEffect(() => {
        if (isAppLoading || !history || !exerciseList?.length) return;
        calculateGlobalBatteriesAsync(history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList)
            .then(setGlobalBatteries)
            .catch(() => {});
    }, [history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading]);

    useEffect(() => {
        if (isAppLoading || !history || !exerciseList?.length) return;
        try {
            const hierarchy = muscleHierarchy || { bodyPartHierarchy: {}, specialCategories: {}, muscleToBodyPart: {} };
            const pm = getPerMuscleBatteries(
                history,
                exerciseList,
                sleepLogs || [],
                settings,
                hierarchy,
                postSessionFeedback || [],
                waterLogs || [],
                dailyWellbeingLogs || [],
                nutritionLogs || []
            );
            setPerMuscle(pm);
        } catch {
            setPerMuscle({});
        }
    }, [history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs, isAppLoading]);

    const sessionMuscles = getSessionMusclesWithBatteries(session, exerciseList ?? [], perMuscle);
    const articularBatteries = globalBatteries?.articularBatteries
        ? getSessionArticularBatteries(session, exerciseList ?? [], globalBatteries.articularBatteries)
        : [];
    const structuralReadiness: StructuralReadinessBreakdown[] = globalBatteries?.articularBatteries
        ? getStructuralReadinessForMuscles(
            Object.fromEntries(sessionMuscles.map((muscle) => [muscle.id, muscle.battery])),
            globalBatteries.articularBatteries,
            sessionMuscles.map((muscle) => muscle.id)
        )
        : sessionMuscles.map((muscle) => ({
            muscleId: muscle.id,
            muscleLabel: muscle.label,
            muscleBattery: muscle.battery,
            articularBattery: muscle.battery,
            combinedBattery: muscle.battery,
            limitingBattery: muscle.battery,
            relatedArticularIds: [],
            relatedArticularLabels: [],
        }));

    if (structuralReadiness.length === 0 && articularBatteries.length === 0) return null;

    const cns = globalBatteries ? Math.round(globalBatteries.cns) : 80;
    const spinal = globalBatteries ? Math.round(globalBatteries.spinal) : 85;
    const muscleAvg = Math.round(structuralReadiness.reduce((sum, item) => sum + item.muscleBattery, 0) / structuralReadiness.length);
    const articularAvg = Math.round(structuralReadiness.reduce((sum, item) => sum + item.articularBattery, 0) / structuralReadiness.length);
    const combinedAvg = Math.round(structuralReadiness.reduce((sum, item) => sum + item.combinedBattery, 0) / structuralReadiness.length);
    const message = getReadinessMessage(muscleAvg, articularAvg, combinedAvg, cns, spinal);

    return (
        <div className={compact ? 'mb-3' : 'mb-4'}>
            <div className="mb-2 flex items-center justify-between">
                <span className="text-[9px] font-black uppercase tracking-[0.12em] text-[#49454F]">
                    Readiness local
                </span>
                <span className="text-[9px] font-mono text-[#49454F]">
                    M {muscleAvg}% | T {articularAvg}% | C {combinedAvg}%
                </span>
            </div>

            <div className={`flex flex-wrap gap-1.5 ${compact ? 'mb-2' : 'mb-2.5'}`}>
                {structuralReadiness.map((muscle) => (
                    <MuscleChip key={muscle.muscleId} muscle={muscle} compact={compact} />
                ))}
            </div>

            {articularBatteries.length > 0 && (
                <div className={`flex flex-wrap gap-1.5 ${compact ? 'mb-2' : 'mb-2.5'}`}>
                    <span className="w-full text-[8px] uppercase tracking-wider text-[#49454F]">Tejido y articulaciones</span>
                    {articularBatteries.map((articular) => (
                        <span key={articular.id} className={`inline-flex items-center gap-1 ${compact ? 'py-0.5' : 'py-1'}`}>
                            <span
                                className="h-1 w-1 shrink-0 rounded-full"
                                style={{ backgroundColor: getBarColor(articular.battery) }}
                            />
                            <span className={`${compact ? 'text-[8px]' : 'text-[9px]'} text-[#49454F]`}>{articular.shortLabel}</span>
                            <span className={`tabular-nums ${compact ? 'text-[8px]' : 'text-[9px]'} text-zinc-600`}>{articular.battery}%</span>
                        </span>
                    ))}
                </div>
            )}

            <p className="text-xs leading-relaxed text-[#49454F]">
                {message}
            </p>
        </div>
    );
};

const MuscleChip: React.FC<{ muscle: StructuralReadinessBreakdown; compact?: boolean }> = ({
    muscle,
    compact,
}) => {
    const color = getBarColor(muscle.combinedBattery);

    return (
        <span className={`inline-flex items-center gap-1.5 ${compact ? 'py-0.5' : 'py-1'}`}>
            <span className="h-1 w-1 shrink-0 rounded-full" style={{ backgroundColor: color }} />
            <span className={`max-w-[78px] truncate ${compact ? 'text-[9px]' : 'text-[10px]'} text-[#49454F]`}>
                {muscle.muscleLabel}
            </span>
            <span className={`tabular-nums ${compact ? 'text-[8px]' : 'text-[9px]'} text-[#49454F]`}>
                {muscle.combinedBattery}%
            </span>
            <span className={`tabular-nums ${compact ? 'text-[7px]' : 'text-[8px]'} text-zinc-500`}>
                M {muscle.muscleBattery} | T {muscle.articularBattery}
            </span>
            <span className={`h-1 shrink-0 overflow-hidden rounded-full bg-white/[0.08] ${compact ? 'w-6' : 'w-8'}`}>
                <span
                    className="block h-full rounded-full transition-all duration-300"
                    style={{ width: `${muscle.combinedBattery}%`, backgroundColor: color }}
                />
            </span>
        </span>
    );
};
