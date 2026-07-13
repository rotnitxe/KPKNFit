import React, { useMemo } from 'react';
import { Session, ExerciseMuscleInfo } from '../../types';
import { Settings } from '../../types';
import { calculateUnifiedMuscleVolume } from '../../services/volumeCalculator';
import { TargetIcon } from '../icons';

interface SessionMetricsBlockProps {
    session: Session;
    exerciseList: ExerciseMuscleInfo[];
    settings: Settings;
    muscleDrainThreshold?: number;
}

/** Volumen POR MÚSCULO (no totales). Usado por alertas y lógica de volumen basura. */
export const SessionMetricsBlock: React.FC<SessionMetricsBlockProps> = ({
    session,
    exerciseList,
}) => {
    const volumeData = useMemo(() => {
        return calculateUnifiedMuscleVolume([session], exerciseList);
    }, [session, exerciseList]);

    if (volumeData.length === 0) return null;

    return (
        <div className="px-4 py-3 mb-3 border-b border-white/[0.08] bg-black/50">
            <div className="flex items-center gap-1.5 mb-2">
                <TargetIcon size={12} className="text-[#00F0FF]" />
                <span className="text-[10px] font-bold uppercase tracking-wider text-[#999]">Volumen por músculo</span>
            </div>
            <div className="flex flex-wrap gap-x-4 gap-y-1.5">
                {volumeData.map(v => (
                    <div key={v.muscleGroup} className="flex items-center gap-2">
                        <span className="text-[10px] font-bold text-[#999] w-20 truncate">{v.muscleGroup}</span>
                        <span className="text-[10px] font-mono font-bold text-white">{v.displayVolume.toFixed(1)}</span>
                    </div>
                ))}
            </div>
        </div>
    );
};
