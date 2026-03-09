import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program } from '../../types';
import { ChevronLeftIcon, EditIcon, PlayIcon, PauseIcon, SettingsIcon, CheckCircleIcon, SlidersIcon } from '../icons';

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
    const [focusDropdownOpen, setFocusDropdownOpen] = useState(false);
    const modeRef = useRef<HTMLDivElement>(null);
    const focusRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (modeRef.current && !modeRef.current.contains(e.target as Node)) setModeDropdownOpen(false);
            if (focusRef.current && !focusRef.current.contains(e.target as Node)) setFocusDropdownOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const modeLabel = program.mode === 'powerlifting' ? 'Powerlifting' : program.mode === 'powerbuilding' ? 'Powerbuilding' : 'Hipertrofia';

    const getFocusLabel = (phase?: string) => {
        switch (phase) {
            case 'accumulation': return 'Hipertrofia';
            case 'transformation': return 'Fuerza';
            case 'realization': return 'Realización';
            default: return 'Fuerza';
        }
    };

    return (
        <div className="relative w-full overflow-visible flex flex-col items-center">
            {/* ── Liquid Glass Hero Container ── */}
            <div className="relative w-full aspect-[4/3] sm:aspect-[16/9] overflow-hidden shadow-2xl shadow-black/10 rounded-b-[48px]">
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
                        <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/30 to-transparent" />
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
                                className="h-12 px-6 rounded-full bg-white/10 backdrop-blur-xl border border-white/20 flex items-center justify-between text-white active:scale-95 transition-all shadow-lg shadow-black/20"
                            >
                                <span className="text-xs font-black uppercase tracking-widest truncate mr-2">{modeLabel}</span>
                                <ChevronLeftIcon size={14} className="-rotate-90 opacity-50" />
                            </button>

                            <AnimatePresence>
                                {modeDropdownOpen && (
                                    <motion.div
                                        initial={{ opacity: 0, y: 10, scale: 0.95 }}
                                        animate={{ opacity: 1, y: 0, scale: 1 }}
                                        exit={{ opacity: 0, y: 10, scale: 0.95 }}
                                        className="absolute top-full right-0 mt-3 z-50 py-2 rounded-2xl bg-white/95 backdrop-blur-2xl border border-white/20 shadow-2xl min-w-[180px] overflow-hidden"
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
                                                className={`w-full px-5 py-4 text-left text-xs font-black uppercase tracking-widest transition-colors ${program.mode === m ? 'bg-black text-white' : 'text-black/60 hover:bg-black/5'}`}
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
                            className="w-12 h-12 rounded-full bg-white/10 backdrop-blur-xl border border-white/20 flex items-center justify-center text-white active:scale-90 transition-all shadow-lg shadow-black/20"
                        >
                            <SlidersIcon size={20} />
                        </button>
                    </div>
                </div>

                {/* Hero Content */}
                <div className="absolute inset-x-0 bottom-0 p-8 pt-24 flex justify-between items-end z-20">
                    <div className="flex flex-col gap-2 flex-1 pr-6">
                        <motion.div
                            initial={{ opacity: 0, x: -20 }}
                            animate={{ opacity: 1, x: 0 }}
                            className="flex flex-wrap gap-2 mb-2"
                        >
                            <div className={`px-4 py-2 rounded-full border text-[10px] font-black uppercase tracking-widest flex items-center gap-2 ${isActive ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30' : isPaused ? 'bg-amber-500/20 text-amber-300 border-amber-500/30' : 'bg-white/10 text-white/60 border-white/20'}`}>
                                <div className={`w-2 h-2 rounded-full ${isActive ? 'bg-emerald-400 animate-pulse outline outline-4 outline-emerald-400/20' : isPaused ? 'bg-amber-400' : 'bg-white/40'}`} />
                                {isActive ? 'Activo' : isPaused ? 'Pausado' : 'Borrador'}
                            </div>
                            {totalAdherence > 0 && (
                                <div className="px-4 py-2 rounded-full bg-blue-500/20 text-blue-300 border border-blue-500/30 text-[10px] font-black uppercase tracking-widest flex items-center gap-2 backdrop-blur-md">
                                    <CheckCircleIcon size={14} />
                                    {totalAdherence}% Adherencia
                                </div>
                            )}
                        </motion.div>

                        <motion.h1
                            initial={{ opacity: 0, y: 20 }}
                            animate={{ opacity: 1, y: 0 }}
                            className="text-white text-5xl sm:text-6xl font-black leading-[0.9] tracking-tighter"
                        >
                            {program.name}
                        </motion.h1>

                        <motion.p
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.1 }}
                            className="text-white/80 text-[14px] font-bold uppercase tracking-[0.05em] mt-3 max-w-[90%] leading-relaxed line-clamp-2"
                        >
                            {program.description || "Sin descripción proporcionada"}
                        </motion.p>
                    </div>

                    {/* Circular Action Button - Premium Glass Style */}
                    <motion.button
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                        onClick={isActive ? onPause : onStart}
                        className={`w-28 h-28 rounded-full flex items-center justify-center shadow-2xl relative shrink-0 transition-colors border-[6px] border-white/10 overflow-hidden ${isActive ? 'bg-white/20 text-white backdrop-blur-2xl' : 'bg-white text-black'}`}
                    >
                        {isActive && !isPaused ? (
                            <div className="flex gap-2.5">
                                <div className="w-2.5 h-10 bg-current rounded-full" />
                                <div className="w-2.5 h-10 bg-current rounded-full" />
                            </div>
                        ) : (
                            <PlayIcon size={48} className="ml-2" fill="currentColor" />
                        )}

                        {/* Pulsing ring if active */}
                        {isActive && (
                            <div className="absolute inset-0 rounded-full border-4 border-emerald-400/50 animate-[ping_3s_infinite]" />
                        )}
                    </motion.button>
                </div>
            </div>

            {/* ── Secondary Stats Bar (Compact & Serious Liquid Glass) ── */}
            <div className="w-full px-2 sm:px-6 -mt-12 z-30">
                <div className="w-full bg-white/85 backdrop-blur-[40px] rounded-[36px] p-2 shadow-[0_20px_60px_rgba(0,0,0,0.12)] border border-white/60 flex items-stretch divide-x divide-black/[0.04]">
                    {/* Stats Sections */}
                    <div className="flex-1 flex flex-col items-center py-4">
                        <span className="text-[10px] font-black text-zinc-400 uppercase tracking-[0.25em] mb-1">Semana</span>
                        <div className="flex items-baseline gap-1">
                            <span className="text-xl font-black text-[#1D1B20] leading-none">{currentWeekIndex + 1}</span>
                            <span className="text-xs font-bold text-zinc-300 uppercase leading-none">/ {totalWeeks}</span>
                        </div>
                    </div>

                    <div className="flex-1 flex flex-col items-center py-4">
                        <span className="text-[10px] font-black text-zinc-400 uppercase tracking-[0.25em] mb-1">Frecuencia</span>
                        <span className="text-xl font-black text-[#1D1B20] leading-none">{trainingDaysCount}D</span>
                    </div>

                    {/* Editable Focus Field */}
                    <div className="flex-1 relative" ref={focusRef}>
                        <button
                            onClick={() => setFocusDropdownOpen(!focusDropdownOpen)}
                            className="w-full h-full flex flex-col items-center py-4 hover:bg-black/[0.02] transition-colors rounded-r-[28px] group"
                        >
                            <span className="text-[10px] font-black text-zinc-400 uppercase tracking-[0.25em] mb-1">Enfoque</span>
                            <div className="flex items-center gap-1.5">
                                <span className="text-[14px] font-black text-blue-600 uppercase tracking-[0.1em]">{getFocusLabel(program.trainingPhase)}</span>
                                <ChevronLeftIcon size={12} className="-rotate-90 text-zinc-300 group-hover:text-blue-400 transition-colors" />
                            </div>
                        </button>

                        <AnimatePresence>
                            {focusDropdownOpen && (
                                <motion.div
                                    initial={{ opacity: 0, y: 10, scale: 0.95 }}
                                    animate={{ opacity: 1, y: 0, scale: 1 }}
                                    exit={{ opacity: 0, y: 10, scale: 0.95 }}
                                    className="absolute bottom-full right-0 mb-4 z-50 py-3 rounded-3xl bg-white/95 backdrop-blur-3xl border border-black/5 shadow-2xl min-w-[180px] overflow-hidden"
                                >
                                    {(['accumulation', 'transformation', 'realization'] as const).map(p => (
                                        <button
                                            key={p}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                if (onUpdateProgram) {
                                                    const updated = JSON.parse(JSON.stringify(program));
                                                    updated.trainingPhase = p;
                                                    onUpdateProgram(updated);
                                                }
                                                setFocusDropdownOpen(false);
                                            }}
                                            className={`w-full px-6 py-4 text-left text-[11px] font-black uppercase tracking-widest transition-colors ${program.trainingPhase === p ? 'bg-blue-600 text-white' : 'text-zinc-600 hover:bg-black/5'}`}
                                        >
                                            {getFocusLabel(p)}
                                        </button>
                                    ))}
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default CompactHeroBanner;
