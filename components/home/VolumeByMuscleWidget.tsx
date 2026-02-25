// components/home/VolumeByMuscleWidget.tsx
// Volumen de entrenamiento por músculo (últimas 4 semanas) con scroll interno

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { calculateUnifiedMuscleVolumeFromLogs } from '../../services/volumeCalculator';
import { LayersIcon } from '../icons';

const WEEKS_LOOKBACK = 4;

export const VolumeByMuscleWidget: React.FC<{
    onNavigate?: () => void;
}> = ({ onNavigate }) => {
    const { history, exerciseList } = useAppState();

    const volumeData = useMemo(() => {
        const cutoff = Date.now() - WEEKS_LOOKBACK * 7 * 24 * 60 * 60 * 1000;
        const recentLogs = (history || []).filter(log => new Date(log.date).getTime() > cutoff);
        return calculateUnifiedMuscleVolumeFromLogs(recentLogs, exerciseList || []);
    }, [history, exerciseList]);

    const maxVol = useMemo(() => Math.max(...volumeData.map(d => d.displayVolume), 1), [volumeData]);

    if (volumeData.length === 0) {
        return (
            <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
                <div className="flex justify-between items-center mb-3">
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                        <LayersIcon size={10} className="text-cyan-400" /> Volumen por Músculo
                    </span>
                </div>
                <p className="text-[10px] text-zinc-500 font-mono">Sin datos en últimas {WEEKS_LOOKBACK} semanas</p>
            </div>
        );
    }

    return (
        <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
            <div className="flex justify-between items-center p-4 pb-2">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                    <LayersIcon size={10} className="text-cyan-400" /> Volumen por Músculo
                </span>
                <span className="text-[8px] text-zinc-600 font-mono">últ. {WEEKS_LOOKBACK} sem.</span>
            </div>
            <div className="max-h-[200px] overflow-y-auto custom-scrollbar px-4 pb-4">
                <div className="space-y-2">
                    {volumeData.map(({ muscleGroup, displayVolume }) => {
                        const pct = (displayVolume / maxVol) * 100;
                        return (
                            <div key={muscleGroup} className="flex items-center gap-2">
                                <span className="text-[10px] font-bold text-white truncate w-24 shrink-0">{muscleGroup}</span>
                                <div className="flex-1 h-2 bg-white/5 rounded-full overflow-hidden min-w-0">
                                    <div
                                        className="h-full rounded-full bg-gradient-to-r from-cyan-600 to-cyan-400 transition-all"
                                        style={{ width: `${Math.max(pct, 4)}%` }}
                                    />
                                </div>
                                <span className="text-[9px] font-mono text-cyan-400 w-10 text-right shrink-0">{displayVolume}</span>
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};
