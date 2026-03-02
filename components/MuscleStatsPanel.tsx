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
            <div className="flex-1 min-w-0 flex flex-col justify-center items-center py-6 px-4 bg-black/40 rounded-xl border border-white/5 border-dashed">
                <p className="text-[10px] font-bold text-zinc-600 uppercase tracking-widest text-center">
                    Selecciona un músculo
                </p>
                <p className="text-[9px] text-zinc-700 mt-1 text-center max-w-[140px]">
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
        <div className="flex-1 min-w-0 flex flex-col bg-black/60 rounded-xl border border-cyan-500/20 overflow-hidden shadow-[0_0_20px_rgba(0,241,255,0.08)]">
            {/* Header estilo HUD */}
            <div className="px-3 py-2 border-b border-cyan-500/20 bg-cyan-500/5 flex items-center justify-between">
                <h4 className="text-xs font-black text-white uppercase tracking-wider truncate">
                    {item.muscleGroup}
                </h4>
                {onClose && (
                    <button
                        onClick={onClose}
                        className="shrink-0 w-5 h-5 rounded bg-white/10 hover:bg-white/20 flex items-center justify-center text-zinc-400 hover:text-white transition-colors text-[10px] font-bold"
                    >
                        ×
                    </button>
                )}
            </div>

            {/* Stats estilo videojuego */}
            <div className="p-3 space-y-2.5">
                <div className="flex items-baseline justify-between gap-2">
                    <span className="text-[9px] font-bold text-zinc-500 uppercase tracking-widest">Volumen</span>
                    <span className="text-lg font-black text-cyan-400 tabular-nums">{item.displayVolume}</span>
                </div>
                <div className="text-[8px] text-zinc-600 font-mono">sets semanales</div>

                <div className="h-px bg-white/5" />

                <div className="flex items-center justify-between gap-2">
                    <span className="text-[9px] font-bold text-zinc-500 uppercase tracking-widest">Estado</span>
                    <span className={`text-[10px] font-black uppercase px-2 py-0.5 rounded border ${status.color} ${status.border}`}>
                        {status.label}
                    </span>
                </div>

                <div className="flex items-center justify-between gap-2">
                    <span className="text-[9px] font-bold text-zinc-500 uppercase tracking-widest">Rango</span>
                    <span className="text-[10px] font-mono text-zinc-400">{thresholds.rangeLabel}</span>
                </div>

                {(directSets > 0 || indirectSets > 0) && (
                    <>
                        <div className="h-px bg-white/5" />
                        <div className="flex items-center justify-between gap-2 text-[9px]">
                            <span className="font-bold text-zinc-500 uppercase">Directo</span>
                            <span className="font-mono text-emerald-400">{directSets}</span>
                        </div>
                        <div className="flex items-center justify-between gap-2 text-[9px]">
                            <span className="font-bold text-zinc-500 uppercase">Indirecto</span>
                            <span className="font-mono text-blue-400">{indirectSets}</span>
                        </div>
                    </>
                )}

                {dbInfo?.description && (
                    <>
                        <div className="h-px bg-white/5" />
                        <p className="text-[9px] text-zinc-500 leading-relaxed line-clamp-3">
                            {dbInfo.description}
                        </p>
                    </>
                )}
            </div>
        </div>
    );
};
