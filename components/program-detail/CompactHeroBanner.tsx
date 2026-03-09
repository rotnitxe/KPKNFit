import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program } from '../../types';
import { ChevronLeftIcon, EditIcon, PlayIcon, PauseIcon, SettingsIcon, CheckCircleIcon, ZapIcon, TargetIcon } from '../icons';

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
    trainingDaysCount: number;
}

const CompactHeroBanner: React.FC<CompactHeroBannerProps> = ({
    program, isActive, isPaused, onBack, onEdit, onStart, onPause,
    onUpdateProgram, currentWeekIndex, totalWeeks,
    totalAdherence, trainingDaysCount,
}) => {
    const [modeDropdownOpen, setModeDropdownOpen] = useState(false);
    const modeRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (modeRef.current && !modeRef.current.contains(e.target as Node)) setModeDropdownOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const modeLabel = program.mode === 'powerlifting' ? 'Powerlifting' : program.mode === 'powerbuilding' ? 'Powerbuilding' : 'Hipertrofia';

    return (
        <div className="relative w-full overflow-visible flex flex-col items-center">
            {/* ── Liquid Glass Hero Container ── */}
            <div className="relative w-full aspect-[16/10] sm:aspect-[16/9] overflow-hidden shadow-2xl shadow-black/10">
                {/* Background Image / Gradient */}
                <AnimatePresence mode="wait">
                    <motion.div
                        key={program.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="absolute inset-0"
                    >
                        {program.coverImage ? (
                            <img src={program.coverImage} className="w-full h-full object-cover" alt="Cover" />
                        ) : (
                            <div className="absolute inset-0 bg-gradient-to-tr from-[#1C1B1F] via-[#49454F] to-[#ECE6F0]" />
                        )}
                        {/* Glass Overlays */}
                        <div className="absolute inset-0 bg-black/20 backdrop-blur-[2px]" />
                        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />
                    </motion.div>
                </AnimatePresence>

                {/* Top Navigation Bar (Floating) */}
                <div className="absolute top-0 left-0 right-0 p-6 flex justify-between items-center z-30" style={{ paddingTop: 'max(24px, env(safe-area-inset-top, 0px))' }}>
                    <button
                        onClick={onBack}
                        className="w-12 h-12 rounded-full bg-white/10 backdrop-blur-xl border border-white/20 flex items-center justify-center text-white active:scale-90 transition-all"
                    >
                        <ChevronLeftIcon size={24} />
                    </button>

                    <div className="flex gap-2">
                        <div className="relative" ref={modeRef}>
                            <button
                                onClick={() => setModeDropdownOpen(!modeDropdownOpen)}
                                className="w-24 h-11 px-4 rounded-full bg-white/10 backdrop-blur-xl border border-white/20 flex items-center justify-between text-white active:scale-95 transition-all"
                            >
                                <span className="text-[10px] font-black uppercase tracking-widest truncate mr-1">{modeLabel}</span>
                                <ChevronLeftIcon size={14} className="-rotate-90 opacity-50" />
                            </button>

                            <AnimatePresence>
                                {modeDropdownOpen && (
                                    <motion.div
                                        initial={{ opacity: 0, y: 10, scale: 0.95 }}
                                        animate={{ opacity: 1, y: 0, scale: 1 }}
                                        exit={{ opacity: 0, y: 10, scale: 0.95 }}
                                        className="absolute top-full right-0 mt-2 z-50 py-2 rounded-2xl bg-white border border-black/5 shadow-2xl min-w-[160px] overflow-hidden"
                                    >
                                        {(['hypertrophy', 'powerlifting', 'powerbuilding'] as const).map(m => (
                                            <button
                                                key={m}
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    if (onUpdateProgram) {
                                                        const updated = JSON.parse(JSON.stringify(program));
                                                        updated.mode = m;
                                                        onUpdateProgram(updated);
                                                    }
                                                    setModeDropdownOpen(false);
                                                }}
                                                className={`w-full px-5 py-3 text-left text-[10px] font-black uppercase tracking-widest transition-colors ${program.mode === m ? 'bg-black text-white' : 'text-black/60 hover:bg-black/5'}`}
                                            >
                                                {m === 'hypertrophy' ? 'Hipertrofia' : m === 'powerlifting' ? 'Powerlifting' : 'Powerbuilding'}
                                            </button>
                                        ))}
                                    </motion.div>
                                )}
                            </AnimatePresence>
                        </div>

                        <button
                            onClick={onEdit}
                            className="w-11 h-11 rounded-full bg-white/10 backdrop-blur-xl border border-white/20 flex items-center justify-center text-white active:scale-90 transition-all font-black uppercase tracking-widest"
                        >
                            <SettingsIcon size={20} />
                        </button>
                    </div>
                </div>

                {/* Hero Content */}
                <div className="absolute inset-x-0 bottom-0 p-8 pt-20 flex justify-between items-end z-20">
                    <div className="flex flex-col gap-2 flex-1 pr-4">
                        <motion.div
                            initial={{ opacity: 0, x: -20 }}
                            animate={{ opacity: 1, x: 0 }}
                            className="flex flex-wrap gap-2 mb-1"
                        >
                            <div className={`px-3 py-1 rounded-full border text-[9px] font-black uppercase tracking-widest flex items-center gap-1.5 ${isActive ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30' : isPaused ? 'bg-amber-500/20 text-amber-300 border-amber-500/30' : 'bg-white/10 text-white/60 border-white/20'}`}>
                                <div className={`w-1.5 h-1.5 rounded-full ${isActive ? 'bg-emerald-400 animate-pulse' : isPaused ? 'bg-amber-400' : 'bg-white/40'}`} />
                                {isActive ? 'Activo' : isPaused ? 'Pausado' : 'Borrador'}
                            </div>
                            {totalAdherence > 0 && (
                                <div className="px-3 py-1 rounded-full bg-blue-500/20 text-blue-300 border border-blue-500/30 text-[9px] font-black uppercase tracking-widest flex items-center gap-1.5">
                                    <CheckCircleIcon size={12} />
                                    {totalAdherence}% Adherencia
                                </div>
                            )}
                        </motion.div>

                        <motion.h1
                            initial={{ opacity: 0, y: 20 }}
                            animate={{ opacity: 1, y: 0 }}
                            className="text-white text-3xl sm:text-4xl font-black leading-[0.9] tracking-tighter"
                        >
                            {program.name}
                        </motion.h1>

                        <motion.p
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.1 }}
                            className="text-white/60 text-[11px] font-bold uppercase tracking-widest mt-1 max-w-[90%] leading-relaxed line-clamp-2"
                        >
                            {program.description || "Sin descripción proporcionada"}
                        </motion.p>
                    </div>

                    {/* Circular Action Button */}
                    <motion.button
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.9 }}
                        onClick={isActive ? onPause : onStart}
                        className={`w-20 h-20 rounded-full flex items-center justify-center shadow-2xl relative shrink-0 ${isActive ? 'bg-white text-black' : 'bg-white text-black shadow-white/10'}`}
                    >
                        {isActive && !isPaused ? (
                            <div className="flex gap-1.5">
                                <div className="w-1.5 h-7 bg-black rounded-full" />
                                <div className="w-1.5 h-7 bg-black rounded-full" />
                            </div>
                        ) : (
                            <PlayIcon size={32} className="ml-1.5" fill="currentColor" />
                        )}

                        {/* Animated Ring if active */}
                        {isActive && (
                            <div className="absolute inset-0 rounded-full border-2 border-emerald-500 animate-[ping_2s_infinite] opacity-30" />
                        )}
                    </motion.button>
                </div>
            </div>

            {/* ── Secondary Stats Bar (Material 3 Surface) ── */}
            <div className="w-full px-6 -mt-6 z-30">
                <div className="w-full bg-white rounded-[28px] p-5 shadow-xl border border-black/[0.03] flex items-center justify-between">
                    <div className="flex flex-col gap-1 flex-1 items-center border-r border-black/[0.05]">
                        <span className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">Semana</span>
                        <div className="flex items-baseline gap-1">
                            <span className="text-lg font-black text-black leading-none">{currentWeekIndex + 1}</span>
                            <span className="text-[10px] font-black text-black/20 uppercase tracking-widest">/ {totalWeeks}</span>
                        </div>
                    </div>
                    <div className="flex flex-col gap-1 flex-1 items-center border-r border-black/[0.05]">
                        <span className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">Frecuencia</span>
                        <div className="flex items-center gap-1.5">
                            <ZapIcon size={14} className="text-amber-500" />
                            <span className="text-lg font-black text-black leading-none">{trainingDaysCount}d</span>
                        </div>
                    </div>
                    <div className="flex flex-col gap-1 flex-1 items-center">
                        <span className="text-[10px] font-black text-black/30 uppercase tracking-[0.2em]">Enfoque</span>
                        <div className="flex items-center gap-1.5">
                            <TargetIcon size={14} className="text-blue-500" />
                            <span className="text-xs font-black text-black leading-none whitespace-nowrap overflow-hidden text-ellipsis">FUERZA</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default CompactHeroBanner;
