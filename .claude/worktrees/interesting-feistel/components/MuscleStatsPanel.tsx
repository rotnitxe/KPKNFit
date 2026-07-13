import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program } from '../types';
import { getVolumeThresholdsForMuscle, MuscleVolumeThresholds } from '../services/volumeCalculator';
import { MUSCLE_GROUP_DATA } from '../data/muscleGroupDatabase';
import { XIcon } from './icons';

/** Acepta datos con muscleGroup y displayVolume (visualizerData o analysisData) */
type MuscleVolumeData = { muscleGroup: string; displayVolume: number; directExercises?: { name: string; sets: number }[]; indirectExercises?: { name: string; sets: number }[] };

const getStatusFromThresholds = (sets: number, thresholds: MuscleVolumeThresholds, programMode?: string) => {
    const isPowerlifting = programMode === 'powerlifting' || programMode === 'powerbuilding' || programMode === 'strength';
    const { min, max } = thresholds;

    if (isPowerlifting) {
        if (sets === 0) return { label: 'Inactivo', color: 'text-black/40', bg: 'bg-black/[0.03]' };
        if (sets < 6) return { label: 'Bajo', color: 'text-blue-500', bg: 'bg-blue-50' };
        if (sets <= 12) return { label: 'Óptimo', color: 'text-emerald-500', bg: 'bg-emerald-50' };
        return { label: 'Alto', color: 'text-amber-500', bg: 'bg-amber-50' };
    }

    if (sets === 0) return { label: 'Inactivo', color: 'text-black/40', bg: 'bg-black/[0.03]' };
    if (sets < min) return { label: 'Subentreno', color: 'text-blue-500', bg: 'bg-blue-50' };
    if (sets <= max) return { label: 'Óptimo', color: 'text-emerald-500', bg: 'bg-emerald-50' };
    return { label: 'Sobreentreno', color: 'text-red-500', bg: 'bg-red-50' };
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
    return (
        <AnimatePresence mode="wait">
            {!selectedMuscle ? (
                <motion.div
                    key="empty"
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    className="flex-1 min-w-0 flex flex-col justify-center items-center py-10 px-6 bg-black/[0.02] rounded-[32px] border border-dashed border-black/[0.05]"
                >
                    <div className="w-12 h-12 rounded-full bg-black/5 flex items-center justify-center mb-4">
                        <div className="w-1.5 h-1.5 rounded-full bg-black/20 animate-ping" />
                    </div>
                    <p className="text-[10px] font-black text-black/40 uppercase tracking-[0.2em] text-center">
                        Selecciona un músculo
                    </p>
                    <p className="text-[11px] text-black/20 mt-2 text-center max-w-[160px] font-medium leading-relaxed">
                        Toca una zona del cuerpo para ver sus métricas avanzadas
                    </p>
                </motion.div>
            ) : (() => {
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
                    <motion.div
                        key={selectedMuscle}
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: -20 }}
                        className="flex-1 min-w-0 flex flex-col bg-white rounded-[32px] border border-black/[0.03] overflow-hidden shadow-2xl shadow-black/5"
                    >
                        {/* Header */}
                        <div className="px-6 py-5 border-b border-black/[0.03] bg-black/[0.02] flex items-center justify-between">
                            <h4 className="text-[11px] font-black text-black uppercase tracking-widest truncate">
                                {item.muscleGroup}
                            </h4>
                            {onClose && (
                                <button
                                    onClick={onClose}
                                    className="shrink-0 w-8 h-8 rounded-full bg-white hover:bg-black/5 flex items-center justify-center text-black/60 transition-all active:scale-90 border border-black/5"
                                >
                                    <XIcon size={14} />
                                </button>
                            )}
                        </div>

                        {/* Content */}
                        <div className="p-6 space-y-6">
                            <div className="flex flex-col gap-1">
                                <div className="flex items-baseline justify-between">
                                    <span className="text-[10px] font-black text-black/20 uppercase tracking-widest">Sets Semanales</span>
                                    <span className="text-3xl font-black tabular-nums text-black">{item.displayVolume}</span>
                                </div>
                                <div className="h-1.5 w-full bg-black/5 rounded-full overflow-hidden mt-2">
                                    <motion.div
                                        initial={{ width: 0 }}
                                        animate={{ width: `${Math.min(100, (item.displayVolume / (thresholds.max || 1)) * 100)}%` }}
                                        className={`h-full rounded-full ${status.color.replace('text-', 'bg-')}`}
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div className={`p-4 rounded-2xl border border-black/[0.03] ${status.bg}`}>
                                    <div className="text-[8px] font-black text-black/20 uppercase tracking-widest mb-1">Estado</div>
                                    <div className={`text-[10px] font-black uppercase ${status.color}`}>{status.label}</div>
                                </div>
                                <div className="p-4 rounded-2xl border border-black/[0.03] bg-black/[0.02]">
                                    <div className="text-[8px] font-black text-black/20 uppercase tracking-widest mb-1">Objetivo</div>
                                    <div className="text-[10px] font-black uppercase text-black/60">{thresholds.rangeLabel}</div>
                                </div>
                            </div>

                            {(directSets > 0 || indirectSets > 0) && (
                                <div className="flex gap-4 p-4 bg-black/[0.02] rounded-2xl border border-black/[0.03]">
                                    <div className="flex-1">
                                        <div className="text-[8px] font-black text-black/20 uppercase tracking-widest mb-1">Directos</div>
                                        <div className="text-sm font-black text-black">{directSets}</div>
                                    </div>
                                    <div className="w-px bg-black/[0.05]" />
                                    <div className="flex-1 pl-4">
                                        <div className="text-[8px] font-black text-black/20 uppercase tracking-widest mb-1">Indirectos</div>
                                        <div className="text-sm font-black text-black">{indirectSets}</div>
                                    </div>
                                </div>
                            )}

                            {dbInfo?.description && (
                                <p className="text-[11px] font-medium text-black/40 leading-relaxed italic border-l-2 border-black/5 pl-4">
                                    {dbInfo.description}
                                </p>
                            )}
                        </div>
                    </motion.div>
                );
            })()}
        </AnimatePresence>
    );
};
