// components/nutrition/ProgressHeroBanner.tsx
// Hero del tab Progreso: métricas, selector meta, tendencia, botón Registrar

import React, { useMemo } from 'react';

interface ProgressHeroBannerProps {
    weight: number | string;
    bodyFat: number | string;
    muscleMass: number | string;
    activePlan?: { goalType: string; goalValue: number; estimatedEndDate?: string; name: string } | null;
    progressPct: number | null;
    selectedMetric: 'weight' | 'bodyFat' | 'muscleMass';
    onSelectMetric: (m: 'weight' | 'bodyFat' | 'muscleMass') => void;
    estimatedDate: string | null;
    trendStatus: 'on_track' | 'behind' | 'ahead';
    onRegisterPress: () => void;
    weightUnit: string;
}

export const ProgressHeroBanner: React.FC<ProgressHeroBannerProps> = ({
    weight,
    bodyFat,
    muscleMass,
    activePlan,
    progressPct,
    selectedMetric,
    onSelectMetric,
    estimatedDate,
    trendStatus,
    onRegisterPress,
    weightUnit,
}) => {
    const trendLabel = useMemo(() => {
        if (trendStatus === 'on_track') return 'En camino';
        if (trendStatus === 'behind') return 'Retrasado';
        return 'Adelantado';
    }, [trendStatus]);

    const trendColor = useMemo(() => {
        if (trendStatus === 'on_track') return 'text-emerald-400';
        if (trendStatus === 'behind') return 'text-amber-400';
        return 'text-emerald-400';
    }, [trendStatus]);

    return (
        <div className="w-full shrink-0 bg-[#1a1a1a] px-4 py-4" style={{ paddingTop: 'max(1rem, env(safe-area-inset-top, 0px))' }}>
            <h1 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-3">
                Progreso
            </h1>

            <div className="grid grid-cols-3 gap-2 mb-4">
                <div className="py-2 border-b border-white/5">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">Peso</p>
                    <p className="text-sm font-black text-white font-mono">{weight}<span className="text-[10px] text-zinc-500 ml-0.5">{weightUnit}</span></p>
                </div>
                <div className="py-2 border-b border-white/5">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">% Grasa</p>
                    <p className="text-sm font-black text-white font-mono">{bodyFat}<span className="text-[10px] text-zinc-500 ml-0.5">%</span></p>
                </div>
                <div className="py-2 border-b border-white/5">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-wider">% Músculo</p>
                    <p className="text-sm font-black text-white font-mono">{muscleMass}<span className="text-[10px] text-zinc-500 ml-0.5">%</span></p>
                </div>
            </div>

            {activePlan && (
                <>
                    <div className="flex gap-2 mb-3">
                        {(['weight', 'bodyFat', 'muscleMass'] as const).map(m => (
                            <button
                                key={m}
                                onClick={() => onSelectMetric(m)}
                                className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-wider transition-colors ${
                                    selectedMetric === m
                                        ? 'bg-white text-black'
                                        : 'bg-white/5 text-zinc-500 hover:text-white'
                                }`}
                            >
                                {m === 'weight' ? 'Peso' : m === 'bodyFat' ? '% Grasa' : '% Músculo'}
                            </button>
                        ))}
                    </div>
                    {progressPct != null && (
                        <div className="mb-3">
                            <div className="flex justify-between text-[10px] text-zinc-500 mb-1">
                                <span>Progreso hacia meta</span>
                                <span>{progressPct}%</span>
                            </div>
                            <div className="h-1.5 bg-white/10 overflow-hidden">
                                <div
                                    className="h-full bg-emerald-500 transition-all"
                                    style={{ width: `${Math.min(100, progressPct)}%` }}
                                />
                            </div>
                        </div>
                    )}
                    <div className="flex justify-between items-center text-[10px] mb-4">
                        <span className="text-zinc-500">Tendencia</span>
                        <span className={`font-bold ${trendColor}`}>{trendLabel}</span>
                    </div>
                    {estimatedDate && (
                        <p className="text-[10px] text-zinc-500 mb-4 font-mono">
                            Fecha est. logro: {estimatedDate}
                        </p>
                    )}
                </>
            )}

            <button
                onClick={onRegisterPress}
                className="w-full py-3 bg-white text-black font-black text-sm uppercase tracking-wider hover:bg-white/90 transition-colors"
            >
                Registrar avance
            </button>
        </div>
    );
};
