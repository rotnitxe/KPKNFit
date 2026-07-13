import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Program } from '../../types';
import { ChevronLeftIcon, EditIcon, PlayIcon, ChevronDownIcon, PaletteIcon } from '../icons';

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

const gradientThemes: { id: string; name: string; gradient: string }[] = [
    { id: 'purple', name: 'Púrpura', gradient: 'linear-gradient(135deg, #6750A4 0%, #D0BCFF 55%, #FEF7FF 100%)' },
    { id: 'sunset', name: 'Cálido', gradient: 'linear-gradient(135deg, #FF6B35 0%, #FFB347 50%, #FFF3E0 100%)' },
    { id: 'ocean', name: 'Frío', gradient: 'linear-gradient(135deg, #006994 0%, #4FC3F7 50%, #E1F5FE 100%)' },
    { id: 'forest', name: 'Bosque', gradient: 'linear-gradient(135deg, #2E5D4B 0%, #66BB6A 50%, #E8F5E9 100%)' },
    { id: 'midnight', name: 'Oscuro', gradient: 'linear-gradient(135deg, #1A1A2E 0%, #4A4A6A 50%, #2D2D44 100%)' },
    { id: 'rose', name: 'Rosa', gradient: 'linear-gradient(135deg, #C2185B 0%, #F48FB1 50%, #FCE4EC 100%)' },
];

const CompactHeroBanner: React.FC<CompactHeroBannerProps> = ({
    program, isActive, isPaused, onBack, onEdit, onStart, onPause,
    onUpdateProgram, currentWeekIndex, totalWeeks, totalAdherence,
}) => {
    const [focusMode, setFocusMode] = useState<Program['mode']>(program.mode || 'powerlifting');
    const [selectedGradient, setSelectedGradient] = useState(gradientThemes[0]);
    const [showFocusSelector, setShowFocusSelector] = useState(false);
    const [showThemeSelector, setShowThemeSelector] = useState(false);

    useEffect(() => {
        setFocusMode(program.mode || 'powerlifting');
    }, [program.mode]);

    const handleFocusChange = (mode: Program['mode']) => {
        if (mode === focusMode) return;
        setFocusMode(mode);
        setShowFocusSelector(false);
        if (onUpdateProgram) {
            onUpdateProgram({ ...program, mode });
        }
    };

    const handleGradientChange = (gradient: typeof gradientThemes[0]) => {
        setSelectedGradient(gradient);
        setShowThemeSelector(false);
    };

    const statusLabel = isActive ? 'Activo' : isPaused ? 'Pausado' : 'Borrador';
    const adherenceValue = Number.isFinite(totalAdherence) ? Math.round(totalAdherence) : 0;

    const statusTone = isActive ? 'bg-emerald-500/20 text-white border border-emerald-500/30' : isPaused ? 'bg-amber-500/20 text-white border border-amber-500/30' : 'bg-white/30 text-white border border-white/40';

    const isDarkTheme = selectedGradient.id === 'midnight';
    const borderColor = isDarkTheme ? 'border-white/20' : 'border-white/25';
    const labelColor = isDarkTheme ? 'text-white/60' : 'text-white/70';

    return (
        <div className="relative w-full">
            {/* Hero full-width */}
            <div className="relative transition-all duration-500" style={{ background: selectedGradient.gradient }}>
                <div className="absolute inset-0 bg-black/10" />
                <div className="relative z-10 px-4 pt-5 pb-3">
                    {/* Nav buttons */}
                    <div className="flex items-center justify-between mb-3">
                        <button
                            type="button"
                            onClick={onBack}
                            className="w-8 h-8 rounded-full bg-white/20 border border-white/30 text-white flex items-center justify-center backdrop-blur-sm"
                        >
                            <ChevronLeftIcon size={16} />
                        </button>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => setShowThemeSelector(!showThemeSelector)}
                                className="w-8 h-8 rounded-full bg-white/20 border border-white/30 text-white flex items-center justify-center backdrop-blur-sm"
                                title="Cambiar tema"
                            >
                                <PaletteIcon size={16} />
                            </button>
                            <span className={`px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-[0.25em] ${statusTone}`}>{statusLabel}</span>
                            <button
                                type="button"
                                onClick={onEdit}
                                className="w-8 h-8 rounded-full bg-white/20 border border-white/30 text-white flex items-center justify-center backdrop-blur-sm"
                            >
                                <EditIcon size={16} />
                            </button>
                        </div>
                    </div>

                    {/* Title and play button */}
                    <div className="flex items-start justify-between gap-3">
                        <div className="flex-1 min-w-0">
                            <h1 className="text-[20px] font-black leading-tight text-white line-clamp-2">{program.name}</h1>
                            {program.description && (
                                <p className="mt-1 text-[11px] text-white/80 leading-relaxed line-clamp-2">{program.description}</p>
                            )}
                        </div>
                        <button
                            type="button"
                            onClick={isActive ? onPause : onStart}
                            className="w-10 h-10 rounded-full bg-white text-black shadow-lg flex items-center justify-center flex-shrink-0"
                        >
                            {isActive && !isPaused ? (
                                <div className="flex gap-1">
                                    <span className="w-1 h-4 bg-black rounded-full" />
                                    <span className="w-1 h-4 bg-black rounded-full" />
                                </div>
                            ) : (
                                <PlayIcon size={16} className="ml-0.5" />
                            )}
                        </button>
                    </div>
                </div>

                {/* KPIs integrados */}
                <div className="relative z-10 px-4 pb-3">
                    <div className={`flex items-center gap-3 py-2.5 border-t ${borderColor}`}>
                        <div className="min-w-0">
                            <div className={`text-[7px] uppercase tracking-[0.25em] ${labelColor}`}>Semana</div>
                            <div className="text-base font-black leading-none text-white">{currentWeekIndex + 1} / {totalWeeks}</div>
                        </div>
                        <div className="min-w-0">
                            <div className={`text-[7px] uppercase tracking-[0.25em] ${labelColor}`}>Adherencia</div>
                            <div className="text-base font-black leading-none text-white">{adherenceValue}%</div>
                        </div>
                        <div className="flex-1 min-w-0">
                            <div className={`text-[7px] uppercase tracking-[0.25em] ${labelColor} mb-1`}>Enfoque</div>
                            <button
                                type="button"
                                onClick={() => setShowFocusSelector(!showFocusSelector)}
                                className="flex items-center gap-1.5 rounded-full bg-white/15 border border-white/25 px-2 py-1 backdrop-blur-sm"
                            >
                                <span className="text-[8px] font-black uppercase tracking-[0.2em] text-white truncate max-w-[80px]">
                                    {focusOptions.find(f => f.value === focusMode)?.label.split(' ')[0]}
                                </span>
                                <ChevronDownIcon size={10} className="text-white flex-shrink-0" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Focus Selector Dropdown */}
            {showFocusSelector && (
                <div className="absolute z-30 left-4 mt-2 bg-white rounded-2xl shadow-xl border border-black/5 overflow-hidden" onClick={() => setShowFocusSelector(false)}>
                    <div className="px-3 py-2 border-b border-black/5">
                        <span className="text-[8px] font-black uppercase tracking-[0.2em] text-black/50">Enfoque</span>
                    </div>
                    {focusOptions.map((option) => (
                        <button
                            key={option.value}
                            type="button"
                            onClick={(e) => { e.stopPropagation(); handleFocusChange(option.value); }}
                            className={`w-full px-4 py-3 text-left text-[10px] font-bold uppercase tracking-[0.15em] transition-colors border-b border-black/5 last:border-0 ${focusMode === option.value ? 'bg-black text-white' : 'text-black/70 hover:bg-black/5'}`}
                        >
                            {option.label}
                        </button>
                    ))}
                </div>
            )}

            {/* Theme Selector Dropdown */}
            {showThemeSelector && (
                <div className="absolute z-30 left-4 right-4 mt-1 bg-white rounded-2xl shadow-xl border border-black/5 overflow-hidden" onClick={() => setShowThemeSelector(false)}>
                    <div className="px-3 py-2 border-b border-black/5">
                        <span className="text-[9px] font-black uppercase tracking-[0.2em] text-black/50">Tema</span>
                    </div>
                    <div className="grid grid-cols-3 gap-2 p-3">
                        {gradientThemes.map((theme) => (
                            <button
                                key={theme.id}
                                type="button"
                                onClick={(e) => { e.stopPropagation(); handleGradientChange(theme); }}
                                className={`relative rounded-xl p-2 text-center transition-all ${selectedGradient.id === theme.id ? 'ring-2 ring-black ring-offset-1' : ''}`}
                                style={{ background: theme.gradient }}
                            >
                                <span className="text-[8px] font-bold text-white drop-shadow-md">{theme.name}</span>
                            </button>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
};

export default CompactHeroBanner;


