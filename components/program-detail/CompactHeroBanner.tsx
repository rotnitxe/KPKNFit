import React, { useState, useRef, useEffect } from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, EditIcon, PlayIcon } from '../icons';

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
        <div className="relative w-full shrink-0 min-h-[120px] sm:min-h-[128px]" style={{ backgroundColor: '#FEF7FF' }}>
            {/* Background opcional: imagen difuminada */}
            {program.coverImage && (
                <>
                    <img src={program.coverImage} alt="" className="absolute inset-0 w-full h-full object-cover opacity-40" style={{ filter: 'blur(24px)' }} />
                    <div className="absolute inset-0 bg-[#FEF7FF]/85" />
                </>
            )}

            {/* Content - header compacto estilo Tú */}
            <div className="relative z-10 h-full flex flex-col gap-2.5 px-4 sm:px-6 py-3" style={{ paddingTop: 'max(28px, env(safe-area-inset-top, 0px))' }}>
                {/* Fila 1: Top App Bar */}
                <div className="flex items-center justify-between shrink-0 h-16">
                    <div className="flex items-center gap-4">
                        <button onClick={onBack} className="w-12 h-12 rounded-full flex items-center justify-center text-[var(--md-sys-color-on-surface)] hover:bg-[var(--md-sys-color-surface-variant)] transition-colors">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
                        </button>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className="relative" ref={modeRef}>
                            <button onClick={() => setModeDropdownOpen(!modeDropdownOpen)} className="w-12 h-12 rounded-full flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)] transition-colors relative">
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 20h9" /><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" /></svg>
                            </button>
                            {modeDropdownOpen && (
                                <div className="absolute top-full right-0 mt-1 z-50 py-2 rounded-xl border border-[var(--md-sys-color-outline-variant)] shadow-lg min-w-[160px] bg-[var(--md-sys-color-surface-container-high)]">
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
                                            className={`w-full px-4 py-3 text-left text-sm transition-colors ${program.mode === m ? 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface)] hover:bg-[var(--md-sys-color-surface-variant)]'}`}
                                        >
                                            {m === 'hypertrophy' ? 'Hipertrofia' : m === 'powerlifting' ? 'Powerlifting' : 'Powerbuilding'}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                        <button onClick={onEdit} className="w-12 h-12 rounded-full flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)] transition-colors">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>
                        </button>
                    </div>
                </div>

                {/* Fila 2: Título y Subtítulo */}
                <div className="flex flex-col px-4 mt-2">
                    <h1 className="text-[32px] font-normal leading-tight tracking-tight text-[var(--md-sys-color-on-surface)]">
                        {program.name}
                    </h1>
                    <p className="text-sm mt-1 text-[var(--md-sys-color-on-surface-variant)]">
                        {program.description || `${modeLabel} ${totalWeeks > 0 ? ` - ${totalWeeks} semanas` : ''}`}
                    </p>
                </div>

                {/* Fila 3: Assistive Chips */}
                <div className="flex items-center gap-2 px-4 mt-4 overflow-x-auto custom-scrollbar pb-2">
                    <div className="flex items-center gap-2 px-3 h-8 rounded-lg bg-[var(--md-sys-color-surface-container)] border border-[var(--md-sys-color-outline-variant)] shrink-0">
                        {isActive && <span className="w-2 h-2 rounded-full bg-[var(--md-sys-color-primary)]" />}
                        {isPaused && <span className="w-2 h-2 rounded-full bg-[var(--md-sys-color-tertiary)]" />}
                        {!isActive && !isPaused && <span className="w-2 h-2 rounded-full bg-[var(--md-sys-color-outline)]" />}
                        <span className="text-sm font-medium text-[var(--md-sys-color-on-surface)]">
                            {isActive ? 'Activo' : isPaused ? 'Pausado' : 'Borrador'}
                        </span>
                    </div>

                    <div className="flex items-center gap-2 px-3 h-8 rounded-lg bg-[var(--md-sys-color-surface-container)] border border-[var(--md-sys-color-outline-variant)] shrink-0">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-[var(--md-sys-color-primary)]"><rect width="18" height="18" x="3" y="3" rx="2" /><path d="M7 7h10" /><path d="M7 12h10" /><path d="M7 17h10" /></svg>
                        <span className="text-sm font-medium text-[var(--md-sys-color-on-surface)]">
                            {modeLabel}
                        </span>
                    </div>

                    {totalAdherence > 0 && (
                        <div className="flex items-center gap-2 px-3 h-8 rounded-lg bg-[var(--md-sys-color-surface-container)] border border-[var(--md-sys-color-outline-variant)] shrink-0">
                            <span className="text-sm font-medium text-[var(--md-sys-color-on-surface)]">
                                {totalAdherence}% adherencia
                            </span>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default CompactHeroBanner;
