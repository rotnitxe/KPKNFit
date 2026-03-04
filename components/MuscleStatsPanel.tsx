import React from 'react';
import { Program } from '../types';
import { getVolumeThresholdsForMuscle, MuscleVolumeThresholds } from '../services/volumeCalculator';
import { MUSCLE_GROUP_DATA } from '../data/muscleGroupDatabase';

/** Acepta datos con muscleGroup y displayVolume (visualizerData o analysisData) */
type MuscleVolumeData = { muscleGroup: string; displayVolume: number; directExercises?: { name: string; sets: number }[]; indirectExercises?: { name: string; sets: number }[] };

const getStatusFromThresholds = (sets: number, thresholds: MuscleVolumeThresholds, programMode?: string) => {
    const isPowerlifting = programMode === 'powerlifting' || programMode === 'powerbuilding' || programMode === 'strength';
    const { min, max } = thresholds;

    if (isPowerlifting) {
        if (sets === 0) return { label: 'Inactivo', color: 'text-zinc-500', border: 'border-zinc-600' };
        if (sets < 6) return { label: 'Bajo', color: 'text-blue-400', border: 'border-blue-500/50' };
        if (sets <= 12) return { label: 'Óptimo', color: 'text-emerald-400', border: 'border-emerald-500/50' };
        return { label: 'Alto', color: 'text-amber-400', border: 'border-amber-500/50' };
    }

    if (sets === 0) return { label: 'Inactivo', color: 'text-zinc-500', border: 'border-zinc-600' };
    if (sets < min) return { label: 'Subentreno', color: 'text-blue-400', border: 'border-blue-500/50' };
    if (sets <= max) return { label: 'Óptimo', color: 'text-emerald-400', border: 'border-emerald-500/50' };
    return { label: 'Sobreentreno', color: 'text-red-400', border: 'border-red-500/50' };
};

interface MuscleStatsPanelProps {
    selectedMuscle: string | null;
    data: MuscleVolumeData[];
    program?: Program | null;
    settings?: any;
    onClose?: () => void;
}

export const MuscleStatsPanel: React.FC<MuscleStatsPanelProps> = ({
    selectedMuscle, data, program, settings, onClose,
}) => {
    if (!selectedMuscle) {
        return (
            <div className="flex-1 min-w-0 flex flex-col justify-center items-center py-6 px-4 bg-[var(--md-sys-color-surface-container-low)] rounded-2xl border border-dashed border-[var(--md-sys-color-outline-variant)]">
                <p className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest text-center opacity-40">
                    Selecciona un músculo
                </p>
                <p className="text-label-sm text-[var(--md-sys-color-on-surface-variant)] mt-1 text-center max-w-[140px] opacity-30">
                    Toca una zona del cuerpo para ver sus stats
                </p>
            </div>
        );
    }

    const item = data.find(d =>
        d.muscleGroup.toLowerCase().includes(selectedMuscle.toLowerCase()) ||
        (selectedMuscle === 'Abdomen' && d.muscleGroup.toLowerCase().includes('abdom'))
    ) || {
        muscleGroup: selectedMuscle,
        displayVolume: 0,
        directExercises: [],
        indirectExercises: [],
    };

    const athleteScore = settings?.athleteScore ?? (program as any)?.athleteProfile ?? null;
    const thresholds = getVolumeThresholdsForMuscle(item.muscleGroup, { program, settings, athleteScore });
    const status = getStatusFromThresholds(item.displayVolume, thresholds, program?.mode);

    const dbInfo = MUSCLE_GROUP_DATA.find(m =>
        m.id.toLowerCase().includes(selectedMuscle.toLowerCase()) ||
        m.name.toLowerCase().includes(selectedMuscle.toLowerCase())
    );

    const directSets = item.directExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;
    const indirectSets = item.indirectExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;

    return (
        <div className="flex-1 min-w-0 flex flex-col bg-[var(--md-sys-color-surface-container-highest)] rounded-2xl border border-[var(--md-sys-color-outline-variant)] overflow-hidden shadow-2xl">
            {/* Header estilo M3 */}
            <div className="px-4 py-3 border-b border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface-container-high)] flex items-center justify-between">
                <h4 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-wider truncate">
                    {item.muscleGroup}
                </h4>
                {onClose && (
                    <button
                        onClick={onClose}
                        className="shrink-0 w-6 h-6 rounded-full bg-[var(--md-sys-color-surface-container-highest)] hover:bg-[var(--md-sys-color-surface-variant)] flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] transition-all active:scale-90"
                    >
                        ×
                    </button>
                )}
            </div>

            {/* Stats estilo M3 */}
            <div className="p-4 space-y-4">
                <div className="flex items-baseline justify-between gap-2">
                    <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Volumen</span>
                    <span className="text-2xl font-black tabular-nums" style={{ color: 'var(--md-sys-color-primary)' }}>{item.displayVolume}</span>
                </div>
                <div className="text-label-sm font-black uppercase tracking-widest opacity-40 -mt-3">sets semanales</div>

                <div className="h-px bg-[var(--md-sys-color-outline-variant)]/30" />

                <div className="flex items-center justify-between gap-2">
                    <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Estado</span>
                    <span className={`text-label-sm font-black uppercase px-2.5 py-1 rounded-lg border ${status.color.replace('text-', 'text-[var(--md-sys-color-on-surface)] bg-').split(' ')[0]} ${status.border}`} style={{ color: 'var(--md-sys-color-on-surface)' }}>
                        {status.label}
                    </span>
                </div>

                <div className="flex items-center justify-between gap-2">
                    <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Rango</span>
                    <span className="text-label-sm font-black opacity-50" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>{thresholds.rangeLabel}</span>
                </div>

                {(directSets > 0 || indirectSets > 0) && (
                    <>
                        <div className="h-px bg-[var(--md-sys-color-outline-variant)]/30" />
                        <div className="flex items-center justify-between gap-2 text-label-sm">
                            <span className="font-black text-[var(--md-sys-color-on-surface-variant)] uppercase opacity-60">Directo</span>
                            <span className="font-black" style={{ color: 'var(--md-sys-color-primary)' }}>{directSets}</span>
                        </div>
                        <div className="flex items-center justify-between gap-2 text-label-sm">
                            <span className="font-black text-[var(--md-sys-color-on-surface-variant)] uppercase opacity-60">Indirecto</span>
                            <span className="font-black" style={{ color: 'var(--md-sys-color-tertiary)' }}>{indirectSets}</span>
                        </div>
                    </>
                )}

                {dbInfo?.description && (
                    <>
                        <div className="h-px bg-[var(--md-sys-color-outline-variant)]/30" />
                        <p className="text-label-sm text-[var(--md-sys-color-on-surface-variant)] leading-relaxed line-clamp-3 opacity-60">
                            {dbInfo.description}
                        </p>
                    </>
                )}
            </div>
        </div>
    );
};
