// components/home/MiniProgramWidget.tsx
// Mini-widget de métricas del programa: Volumen, Adherencia, Recuperación

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { DumbbellIcon, FlameIcon, ZapIcon, ChevronRightIcon } from '../icons';
import { calculateProgramAdherence } from '../../services/analysisService';
import { getCachedAdaptiveData } from '../../services/augeAdaptiveService';

export const MiniProgramWidget: React.FC<{ onNavigate: () => void }> = ({ onNavigate }) => {
    const { programs, history, activeProgramState, settings, exerciseList } = useAppState();

    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const metrics = useMemo(() => {
        if (!activeProgram || !history) return null;
        const adherenceData = calculateProgramAdherence(activeProgram, history, settings, exerciseList);
        const adherence = adherenceData?.adherencePercentage ?? 0;

        const programHistory = history.filter(h => h.programId === activeProgram.id);
        const last4Weeks = programHistory.slice(-20);
        const volumeSets = last4Weeks.reduce((acc, log) => {
            return acc + log.completedExercises.reduce((s, ex) => s + (ex.sets?.length || 0), 0);
        }, 0);

        const adaptiveCache = getCachedAdaptiveData();
        const recovery = adaptiveCache?.banister?.systems?.muscular?.performance?.slice(-1)[0];
        const recoveryPct = recovery != null ? Math.round(Math.max(0, Math.min(100, (recovery + 1) * 50))) : null;

        return { adherence, volume: volumeSets, recovery: recoveryPct };
    }, [activeProgram, history, exerciseList, settings]);

    if (!activeProgram) {
        return (
            <button
                onClick={onNavigate}
                className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4 text-left hover:border-white/20 transition-colors group"
            >
                <div className="flex justify-between items-center">
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em]">Programa</span>
                    <ChevronRightIcon size={14} className="text-zinc-500" />
                </div>
                <p className="text-[10px] text-zinc-500 font-mono mt-2">Sin programa activo</p>
            </button>
        );
    }

    return (
        <button
            onClick={onNavigate}
            className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4 text-left hover:border-white/20 transition-colors group"
        >
            <div className="flex justify-between items-center mb-3">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em]">Programa</span>
                <ChevronRightIcon size={14} className="text-zinc-500 group-hover:text-white transition-colors" />
            </div>
            <div className="grid grid-cols-3 gap-2">
                <div className="bg-black/30 rounded-lg p-2 border border-white/5">
                    <DumbbellIcon size={14} className="text-emerald-400 mb-1" />
                    <span className="text-[10px] font-mono font-black text-white block">{metrics ? Math.round(metrics.volume) : '—'}</span>
                    <span className="text-[8px] text-zinc-500">Vol</span>
                </div>
                <div className="bg-black/30 rounded-lg p-2 border border-white/5">
                    <FlameIcon size={14} className="text-rose-400 mb-1" />
                    <span className="text-[10px] font-mono font-black text-white block">{metrics?.adherence ?? '—'}%</span>
                    <span className="text-[8px] text-zinc-500">Adh.</span>
                </div>
                <div className="bg-black/30 rounded-lg p-2 border border-white/5">
                    <ZapIcon size={14} className="text-amber-400 mb-1" />
                    <span className="text-[10px] font-mono font-black text-white block">{metrics?.recovery ?? '—'}%</span>
                    <span className="text-[8px] text-zinc-500">Recup.</span>
                </div>
            </div>
        </button>
    );
};
