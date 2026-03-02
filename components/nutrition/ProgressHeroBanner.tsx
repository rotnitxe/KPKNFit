// components/nutrition/ProgressHeroBanner.tsx
// Hero vistoso e interactivo: métricas con cards, selector táctil, progreso animado

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

const METRIC_CONFIG = [
    { id: 'weight' as const, label: 'Peso', shortLabel: 'kg' },
    { id: 'bodyFat' as const, label: '% Grasa', shortLabel: '%' },
    { id: 'muscleMass' as const, label: '% Músculo', shortLabel: '%' },
];

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

    const getValue = (m: 'weight' | 'bodyFat' | 'muscleMass') => {
        if (m === 'weight') return weight;
        if (m === 'bodyFat') return bodyFat;
        return muscleMass;
    };

    const getUnit = (m: 'weight' | 'bodyFat' | 'muscleMass') => {
        if (m === 'weight') return weightUnit;
        return '%';
    };

    return (
        <div
            className="w-full shrink-0 relative overflow-hidden"
            style={{ paddingTop: 'max(1rem, env(safe-area-inset-top, 0px))' }}
        >
            {/* Gradiente de fondo */}
            <div className="absolute inset-0 bg-gradient-to-br from-[#1e293b] via-[#0f172a] to-[#020617]" />
            <div className="absolute top-0 left-1/2 w-64 h-64 bg-sky-500/10 blur-3xl rounded-full -translate-x-1/2 -translate-y-1/2" />
            <div className="absolute bottom-0 right-0 w-40 h-40 bg-emerald-500/10 blur-2xl rounded-full translate-y-1/2 translate-x-1/2" />

            <div className="relative z-10 px-4 py-5">
                <h1 className="text-sm font-bold text-zinc-400 uppercase tracking-wider mb-4">Tu progreso</h1>

                {/* Métricas en cards interactivas */}
                <div className="grid grid-cols-3 gap-2 mb-5">
                    {METRIC_CONFIG.map((m) => {
                        const isSelected = selectedMetric === m.id;
                        return (
                            <button
                                key={m.id}
                                onClick={() => onSelectMetric(m.id)}
                                className={`p-4 rounded-2xl border transition-all active:scale-95 ${
                                    isSelected
                                        ? 'bg-white/15 border-white/30 shadow-lg shadow-white/5'
                                        : 'bg-white/5 border-white/10 hover:bg-white/10 hover:border-white/15'
                                }`}
                            >
                                <p className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider mb-1">{m.label}</p>
                                <p className="text-xl font-black text-white tabular-nums">
                                    {getValue(m.id)}
                                    <span className="text-xs font-bold text-zinc-500 ml-0.5">{getUnit(m.id)}</span>
                                </p>
                            </button>
                        );
                    })}
                </div>

                {/* Progreso hacia meta */}
                {activePlan && (
                    <div className="mb-5 p-4 rounded-2xl bg-black/30 border border-white/5 backdrop-blur-sm">
                        <div className="flex gap-2 mb-3">
                            {METRIC_CONFIG.map((m) => (
                                <button
                                    key={m.id}
                                    onClick={() => onSelectMetric(m.id)}
                                    className={`flex-1 py-2 rounded-xl text-xs font-bold transition-all ${
                                        selectedMetric === m.id
                                            ? 'bg-white text-black'
                                            : 'bg-white/5 text-zinc-400 hover:text-white'
                                    }`}
                                >
                                    {m.id === 'weight' ? 'Peso' : m.id === 'bodyFat' ? '% Grasa' : '% Músculo'}
                                </button>
                            ))}
                        </div>
                        {progressPct != null && (
                            <div className="mb-3">
                                <div className="flex justify-between text-xs text-zinc-400 mb-1">
                                    <span>Progreso hacia meta</span>
                                    <span className="font-black text-emerald-400">{progressPct}%</span>
                                </div>
                                <div className="h-3 bg-white/10 rounded-full overflow-hidden">
                                    <div
                                        className="h-full rounded-full bg-gradient-to-r from-emerald-600 to-emerald-400 transition-all duration-700 ease-out"
                                        style={{ width: `${Math.min(100, progressPct)}%` }}
                                    />
                                </div>
                            </div>
                        )}
                        <div className="flex justify-between items-center text-xs">
                            <span className="text-zinc-500">Tendencia</span>
                            <span className={`font-bold ${trendColor}`}>{trendLabel}</span>
                        </div>
                        {estimatedDate && (
                            <p className="text-[11px] text-zinc-500 mt-2 font-medium">
                                Fecha est. logro: {new Date(estimatedDate).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })}
                            </p>
                        )}
                    </div>
                )}

                {/* Botón principal — grande y táctil */}
                <button
                    onClick={onRegisterPress}
                    className="w-full py-4 rounded-2xl bg-white text-black font-black text-base shadow-xl active:scale-[0.98] transition-transform flex items-center justify-center gap-2"
                >
                    Registrar avance
                </button>
            </div>
        </div>
    );
};
