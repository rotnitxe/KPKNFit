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
            className="w-full shrink-0 relative overflow-hidden bg-[var(--md-sys-color-surface-container-low)]"
            style={{ paddingTop: 'max(1rem, env(safe-area-inset-top, 0px))' }}
        >
            {/* Gradiente de fondo suave */}
            <div className="absolute inset-0 bg-gradient-to-br from-[var(--md-sys-color-surface-container-low)] via-[var(--md-sys-color-surface-container)] to-[var(--md-sys-color-surface-container-high)]" />
            <div className="absolute top-0 left-1/2 w-64 h-64 bg-[var(--md-sys-color-primary)]/5 blur-3xl rounded-full -translate-x-1/2 -translate-y-1/2" />
            <div className="absolute bottom-0 right-0 w-40 h-40 bg-[var(--md-sys-color-secondary)]/5 blur-2xl rounded-full translate-y-1/2 translate-x-1/2" />

            <div className="relative z-10 px-4 py-6">
                <h1 className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-[0.2em] mb-4">Tu progreso</h1>

                {/* Métricas en cards interactivas */}
                <div className="grid grid-cols-3 gap-2 mb-5">
                    {METRIC_CONFIG.map((m) => {
                        const isSelected = selectedMetric === m.id;
                        return (
                            <button
                                key={m.id}
                                onClick={() => onSelectMetric(m.id)}
                                className={`p-4 rounded-[24px] border transition-all active:scale-95 ${isSelected
                                        ? 'bg-[var(--md-sys-color-primary-container)] border-[var(--md-sys-color-primary)] shadow-sm'
                                        : 'bg-[var(--md-sys-color-surface-container-highest)] border-[var(--md-sys-color-outline-variant)]/30 hover:bg-[var(--md-sys-color-surface-container)]'
                                    }`}
                            >
                                <p className="text-[9px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-wider mb-1">{m.label}</p>
                                <p className={`text-xl font-black tabular-nums ${isSelected ? 'text-[var(--md-sys-color-on-primary-container)]' : 'text-[var(--md-sys-color-on-surface)]'}`}>
                                    {getValue(m.id)}
                                    <span className="text-[10px] font-bold opacity-60 ml-0.5">{getUnit(m.id)}</span>
                                </p>
                            </button>
                        );
                    })}
                </div>

                {/* Progreso hacia meta */}
                {activePlan && (
                    <div className="mb-5 p-6 rounded-[28px] bg-[var(--md-sys-color-surface)]/50 border border-[var(--md-sys-color-outline-variant)]/20 backdrop-blur-md">
                        <div className="flex gap-2 mb-4 bg-[var(--md-sys-color-surface-container-low)] p-1 rounded-2xl">
                            {METRIC_CONFIG.map((m) => (
                                <button
                                    key={m.id}
                                    onClick={() => onSelectMetric(m.id)}
                                    className={`flex-1 py-1.5 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all ${selectedMetric === m.id
                                            ? 'bg-[var(--md-sys-color-surface)] text-[var(--md-sys-color-on-surface)] shadow-sm'
                                            : 'text-[var(--md-sys-color-on-surface-variant)]/60'
                                        }`}
                                >
                                    {m.id === 'weight' ? 'Peso' : m.id === 'bodyFat' ? '% Gra' : '% Mus'}
                                </button>
                            ))}
                        </div>
                        {progressPct != null && (
                            <div className="mb-4">
                                <div className="flex justify-between text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest mb-2">
                                    <span>Progreso hacia meta</span>
                                    <span className="text-[var(--md-sys-color-primary)]">{progressPct}%</span>
                                </div>
                                <div className="h-3 bg-[var(--md-sys-color-surface-container)] rounded-full overflow-hidden">
                                    <div
                                        className="h-full rounded-full bg-gradient-to-r from-[var(--md-sys-color-primary)] to-[var(--md-sys-color-primary-container)] transition-all duration-1000 ease-out"
                                        style={{ width: `${Math.min(100, progressPct)}%` }}
                                    />
                                </div>
                            </div>
                        )}
                        <div className="flex justify-between items-center text-[10px] font-black uppercase tracking-widest">
                            <span className="text-[var(--md-sys-color-on-surface-variant)]/40">Tendencia</span>
                            <span className={trendColor.replace('-400', '-600')}>{trendLabel}</span>
                        </div>
                        {estimatedDate && (
                            <p className="text-[11px] text-[#49454F] mt-2 font-medium">
                                Fecha est. logro: {new Date(estimatedDate).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })}
                            </p>
                        )}
                    </div>
                )}

                {/* Botón principal — grande y táctil */}
                <button
                    onClick={onRegisterPress}
                    className="w-full py-5 rounded-[24px] bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] font-black text-[11px] uppercase tracking-[0.2em] shadow-lg shadow-[var(--md-sys-color-primary)]/20 active:scale-[0.98] transition-all flex items-center justify-center gap-3"
                >
                    Registrar avance
                </button>
            </div>
        </div>
    );
};
