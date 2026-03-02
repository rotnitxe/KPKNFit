// components/home/SessionReadinessBlock.tsx
// Músculos de la sesión con baterías + mensaje de preparación (amigable, no alarmante)

import React, { useState, useEffect } from 'react';
import { Session } from '../../types';
import { getSessionMusclesWithBatteries, SessionMuscleForBattery } from '../../utils/sessionMusclesForBattery';
import { useAppState } from '../../contexts/AppContext';
import { getPerMuscleBatteries } from '../../services/auge';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';

const getBarColor = (value: number): string => {
    if (value >= 75) return '#10b981';
    if (value >= 50) return '#f59e0b';
    return '#78716c';
};

function getReadinessMessage(
    muscleAvg: number,
    cns: number,
    spinal: number
): string {
    const bottleneck = Math.min(cns, spinal);
    const gap = muscleAvg - bottleneck;

    // Todo fresco
    if (muscleAvg >= 80 && cns >= 70 && spinal >= 70) {
        return 'Todo bien. Los músculos que entrenarás hoy están descansados y tu cuerpo está listo.';
    }

    // Músculos bien, energía o espalda algo cargadas
    if (muscleAvg >= 70 && gap > 15) {
        if (cns < spinal) return 'Los músculos están bien. Si notas que te cuesta más de lo normal con pesos pesados, baja un poco la intensidad.';
        return 'Los músculos están listos. La espalda podría ir algo cargada; calienta bien y escucha a tu cuerpo en los ejercicios pesados.';
    }

    // A medio camino
    if (muscleAvg >= 55) {
        if (bottleneck >= 65) return 'Recuperando. Puedes entrenar normal; si notas que cuesta, baja un poco el volumen.';
        return 'Algunos músculos siguen recuperando. Entrena tranquilo y cuida la técnica.';
    }

    // Bajo
    if (muscleAvg >= 40) {
        return 'Varios grupos aún se recuperan. Un día más suave o descansar podría venir bien; si te sientes bien, adelante.';
    }

    return 'Varios grupos con poca batería. Si entrenas, baja series o peso. Si puedes, un día de descanso puede ser lo mejor.';
}

interface SessionReadinessBlockProps {
    session: Session;
    compact?: boolean;
}

export const SessionReadinessBlock: React.FC<SessionReadinessBlockProps> = ({
    session,
    compact = false,
}) => {
    const { history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs, isAppLoading } = useAppState();

    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [globalBatteries, setGlobalBatteries] = useState<{ cns: number; muscular: number; spinal: number } | null>(null);

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

    const muscles = getSessionMusclesWithBatteries(session, exerciseList ?? [], perMuscle);

    if (muscles.length === 0) return null;

    const cns = globalBatteries ? Math.round(globalBatteries.cns) : 80;
    const spinal = globalBatteries ? Math.round(globalBatteries.spinal) : 85;

    const muscleAvg = Math.round(
        muscles.reduce((s, m) => s + m.battery, 0) / muscles.length
    );
    const message = getReadinessMessage(muscleAvg, cns, spinal);

    return (
        <div className={compact ? 'mb-3' : 'mb-4'}>
            <div className="flex items-center justify-between mb-2">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.12em]">
                    Músculos hoy
                </span>
                <span className="text-[9px] font-mono text-zinc-500">
                    Promedio {muscleAvg}%
                </span>
            </div>

            <div className={`flex flex-wrap gap-1.5 ${compact ? 'mb-2' : 'mb-2.5'}`}>
                {muscles.map((m) => (
                    <MuscleChip key={m.id} muscle={m} compact={compact} />
                ))}
            </div>

            <p className="text-xs text-zinc-400 leading-relaxed">
                {message}
            </p>
        </div>
    );
};

const MuscleChip: React.FC<{ muscle: SessionMuscleForBattery; compact?: boolean }> = ({
    muscle,
    compact,
}) => {
    const color = getBarColor(muscle.battery);
    return (
        <span
            className={`inline-flex items-center gap-1.5 ${compact ? 'py-0.5' : 'py-1'}`}
        >
            <span
                className="shrink-0 rounded-full w-1 h-1"
                style={{ backgroundColor: color }}
            />
            <span className={`truncate max-w-[70px] ${compact ? 'text-[9px]' : 'text-[10px]'} text-zinc-400`}>
                {muscle.label}
            </span>
            <span className={`tabular-nums ${compact ? 'text-[8px]' : 'text-[9px]'} text-zinc-500`}>
                {muscle.battery}%
            </span>
            <span
                className={`shrink-0 rounded-full overflow-hidden bg-white/[0.08] ${compact ? 'w-6 h-1' : 'w-8 h-1'}`}
            >
                <span
                    className="block h-full rounded-full transition-all duration-300"
                    style={{ width: `${muscle.battery}%`, backgroundColor: color }}
                />
            </span>
        </span>
    );
};
