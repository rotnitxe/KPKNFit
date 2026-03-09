import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Program } from '../../types';
import { ChevronLeftIcon, EditIcon, PlayIcon } from '../icons';

interface CompactHeroBannerProps {
    program: Program;
    isActive: boolean;
    isPaused: boolean;
    onBack: () => void;
    onEdit: () => void;
    onStart: () => void;
    onPause: () => void;
    onUpdateProgram?: (program: Program) => void;
    currentWeekIndex: number;
    totalWeeks: number;
    totalAdherence: number;
}

const focusOptions: { value: Program['mode']; label: string }[] = [
    { value: 'powerlifting', label: 'Powerlifting' },
    { value: 'powerbuilding', label: 'Powerbuilding' },
    { value: 'hypertrophy', label: 'Estética' },
];

const heroGradient = 'linear-gradient(135deg, #6750A4 0%, #D0BCFF 55%, #FEF7FF 100%)';

const CompactHeroBanner: React.FC<CompactHeroBannerProps> = ({
    program, isActive, isPaused, onBack, onEdit, onStart, onPause,
    onUpdateProgram, currentWeekIndex, totalWeeks, totalAdherence,
}) => {
    const [focusMode, setFocusMode] = useState<Program['mode']>(program.mode || 'powerlifting');

    useEffect(() => {
        setFocusMode(program.mode || 'powerlifting');
    }, [program.mode]);

    const handleFocusChange = (mode: Program['mode']) => {
        if (mode === focusMode) return;
        setFocusMode(mode);
        if (onUpdateProgram) {
            onUpdateProgram({ ...program, mode });
        }
    };

    const statusLabel = isActive ? 'Activo' : isPaused ? 'Pausado' : 'Borrador';
    const adherenceValue = Number.isFinite(totalAdherence) ? Math.round(totalAdherence) : 0;

    const statusTone = isActive ? 'bg-emerald-500/20 text-white border border-emerald-500/30' : isPaused ? 'bg-amber-500/20 text-white border border-amber-500/30' : 'bg-white/30 text-white border border-white/40';

    return (
        <div className="relative w-full px-5 pt-5 pb-4">
            <div className="relative overflow-hidden rounded-[34px] shadow-[0_35px_60px_rgba(17,16,24,0.45)]" style={{ background: heroGradient }}>
                <div className="absolute inset-0 bg-black/20" />
                <div className="relative z-10 px-4 py-4">
                    <div className="flex items-center justify-between">
                        <button
                            type="button"
                            onClick={onBack}
                            className="w-10 h-10 rounded-[14px] bg-white/20 border border-white/30 text-white flex items-center justify-center"
                        >
                            <ChevronLeftIcon size={20} />
                        </button>
                        <button
                            type="button"
                            onClick={onEdit}
                            className="w-10 h-10 rounded-[14px] bg-white/20 border border-white/30 text-white flex items-center justify-center"
                        >
                            <EditIcon size={18} />
                        </button>
                    </div>
                    <div className="mt-3 flex items-start justify-between gap-4">
                        <div className="flex-1">
                            <div className="text-[10px] uppercase tracking-[0.35em] text-white/70">Programa</div>
                            <h1 className="mt-1 text-[22px] font-black leading-tight text-white line-clamp-2">{program.name}</h1>
                            {program.description && (
                                <p className="mt-1 text-[12px] text-white/70 leading-relaxed line-clamp-2">{program.description}</p>
                            )}
                        </div>
                        <button
                            type="button"
                            onClick={isActive ? onPause : onStart}
                            className="relative z-20 w-12 h-12 rounded-[16px] bg-white text-black shadow-lg border border-black/10 flex items-center justify-center"
                        >
                            {isActive && !isPaused ? (
                                <div className="flex gap-1">
                                    <span className="w-1.5 h-6 bg-black rounded-full" />
                                    <span className="w-1.5 h-6 bg-black rounded-full" />
                                </div>
                            ) : (
                                <PlayIcon size={18} className="ml-0.5" />
                            )}
                        </button>
                    </div>
                    <div className="mt-4 flex items-center gap-3">
                        <span className="text-[10px] font-black uppercase tracking-[0.4em] text-white/60">Estado</span>
                        <span className={`rounded-full px-3 py-1 text-[10px] font-black uppercase tracking-[0.35em] ${statusTone}`}>{statusLabel}</span>
                    </div>
                </div>
            </div>

            <div className="z-20 -mt-4 w-full px-1">
                <div className="rounded-[26px] border border-black/[0.08] bg-white/95 px-4 py-4 shadow-[0_20px_40px_rgba(15,15,15,0.15)]">
                    <div className="flex items-center justify-between text-[10px] uppercase tracking-[0.4em] text-black/40">
                        <span>Semana</span>
                        <span>Adherencia</span>
                    </div>
                    <div className="mt-1 flex items-end justify-between">
                        <div className="text-2xl font-black leading-none text-black">{currentWeekIndex + 1} / {totalWeeks}</div>
                        <div className="text-2xl font-black leading-none text-black">{adherenceValue}%</div>
                    </div>
                    <div className="mt-4 flex flex-col gap-1">
                        <span className="text-[9px] uppercase tracking-[0.45em] text-black/40">Enfoque</span>
                        <div className="flex items-stretch gap-2">
                            {focusOptions.map((option) => (
                                <button
                                    key={option.value}
                                    type="button"
                                    onClick={() => handleFocusChange(option.value)}
                                    className="relative flex-1 rounded-[18px] border border-black/[0.08] px-2 py-2 text-[11px] font-black uppercase tracking-[0.3em] text-black/60 transition-colors"
                                >
                                    {focusMode === option.value && (
                                        <motion.span
                                            layoutId="program-focus-indicator"
                                            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                                            className="absolute inset-0 rounded-[18px] bg-black"
                                        />
                                    )}
                                    <span className={`relative z-10 ${focusMode === option.value ? 'text-white' : 'text-black/60'}`}>
                                        {option.label}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default CompactHeroBanner;


