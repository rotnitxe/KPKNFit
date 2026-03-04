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
        <div className="relative w-full shrink-0 min-h-[120px] sm:min-h-[128px]" style={{ backgroundColor: 'var(--md-sys-color-surface-container)' }}>
            {/* Background opcional: imagen difuminada */}
            {program.coverImage && (
                <>
                    <img src={program.coverImage} alt="" className="absolute inset-0 w-full h-full object-cover opacity-40" style={{ filter: 'blur(24px)' }} />
                    <div className="absolute inset-0 bg-[var(--md-sys-color-surface-container)]/85" />
                </>
            )}

            {/* Content - header compacto estilo Tú */}
            <div className="relative z-10 h-full flex flex-col gap-2.5 px-4 sm:px-6 py-3" style={{ paddingTop: 'max(28px, env(safe-area-inset-top, 0px))' }}>
                {/* Fila 1: Volver + Título + Chips */}
                <div className="flex items-center gap-3 shrink-0">
                    <button onClick={onBack} className="w-8 h-8 shrink-0 rounded-lg bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] transition-colors">
                        <ChevronDownIcon size={18} className="rotate-90" />
                    </button>
                    <div className="flex-1 min-w-0">
                        <h1 className="text-title-lg font-black uppercase tracking-tight leading-tight truncate" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                            {program.name}
                        </h1>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                        {isActive && (
                            <span className="bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] border border-[var(--md-sys-color-outline-variant)] text-[10px] font-black uppercase tracking-widest px-2.5 py-0.5 rounded-md">
                                Activo
                            </span>
                        )}
                        {isPaused && (
                            <span className="bg-[var(--md-sys-color-tertiary-container)] text-[var(--md-sys-color-on-tertiary-container)] border border-[var(--md-sys-color-outline-variant)] text-[10px] font-black uppercase tracking-widest px-2.5 py-0.5 rounded-md">
                                Pausado
                            </span>
                        )}
                        {!isActive && !isPaused && (
                            <span className="bg-[var(--md-sys-color-surface-variant)] border border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)] text-[10px] font-black uppercase tracking-widest px-2.5 py-0.5 rounded-md">
                                Borrador
                            </span>
                        )}
                    </div>
                </div>

                {/* Fila 2: KPIs - tipografía Tú */}
                <div className="flex items-center gap-5 flex-wrap shrink-0">
                    <div className="flex items-center gap-1.5">
                        <span className="text-lg font-black leading-none" style={{ color: 'var(--md-sys-color-on-surface)' }}>{currentWeekIndex + 1}</span>
                        <span className="text-[10px] font-bold uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>/{totalWeeks > 0 ? totalWeeks : '∞'} sem</span>
                    </div>
                    <div className="w-px h-4 hidden sm:block opacity-20" style={{ backgroundColor: 'var(--md-sys-color-outline-variant)' }} />
                    <div className="flex items-center gap-1.5">
                        <span className="text-lg font-black leading-none" style={{ color: totalAdherence >= 80 ? 'var(--md-sys-color-primary)' : totalAdherence >= 50 ? 'var(--md-sys-color-tertiary)' : 'var(--md-sys-color-error)' }}>{totalAdherence}%</span>
                        <span className="text-[10px] font-bold uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>adher.</span>
                    </div>
                    <div className="w-px h-4 hidden sm:block opacity-20" style={{ backgroundColor: 'var(--md-sys-color-outline-variant)' }} />
                    <div className="flex items-center gap-1.5">
                        <span className="text-lg font-black leading-none" style={{ color: 'var(--md-sys-color-on-surface)' }}>{trainingDaysCount}</span>
                        <span className="text-[10px] font-bold uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>días/sem</span>
                    </div>
                </div>

                {/* Fila 3: Modo editable + Acciones - estilo Tú */}
                <div className="flex items-center gap-2 shrink-0 flex-wrap">
                    <div className="relative" ref={modeRef}>
                        <button
                            onClick={() => setModeDropdownOpen(!modeDropdownOpen)}
                            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] text-[10px] font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] hover:border-[var(--md-sys-color-outline)] transition-colors"
                        >
                            {modeLabel}
                            <ChevronDownIcon size={12} className={`transition-transform ${modeDropdownOpen ? 'rotate-180' : ''}`} />
                        </button>
                        {modeDropdownOpen && (
                            <div className="absolute top-full left-0 mt-1 z-20 py-1 rounded-xl bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)] shadow-2xl min-w-[140px]">
                                {(['hypertrophy', 'powerlifting', 'powerbuilding'] as const).map(m => (
                                    <button
                                        key={m}
                                        onClick={() => {
                                            if (onUpdateProgram) {
                                                const updated = JSON.parse(JSON.stringify(program));
                                                updated.mode = m;
                                                onUpdateProgram(updated);
                                            }
                                            setModeDropdownOpen(false);
                                        }}
                                        className={`w-full px-3 py-2 text-left text-xs font-black uppercase tracking-widest transition-colors ${program.mode === m ? 'bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]' : 'text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] hover:bg-[var(--md-sys-color-surface-variant)]'}`}
                                    >
                                        {m === 'hypertrophy' ? 'Hipertrofia' : m === 'powerlifting' ? 'Powerlifting' : 'Powerbuilding'}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                    <button onClick={onEdit} className="w-8 h-8 rounded-lg bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] hover:bg-[var(--md-sys-color-surface-variant)] transition-colors">
                        <EditIcon size={16} />
                    </button>
                    {isActive ? (
                        <button onClick={onPause} className="h-8 px-4 rounded-lg bg-[var(--md-sys-color-tertiary-container)] border border-[var(--md-sys-color-outline-variant)] flex items-center justify-center gap-1.5 text-[var(--md-sys-color-on-tertiary-container)] hover:brightness-110 transition-all">
                            <div className="flex gap-0.5"><div className="w-1 h-3 bg-current rounded-sm" /><div className="w-1 h-3 bg-current rounded-sm" /></div>
                            <span className="text-[10px] font-bold hidden sm:inline">Pausar</span>
                        </button>
                    ) : (
                        <button onClick={onStart} className="h-8 px-4 rounded-lg bg-[var(--md-sys-color-primary)] border border-[var(--md-sys-color-outline-variant)] flex items-center justify-center gap-1.5 text-[var(--md-sys-color-on-primary)] hover:brightness-110 transition-all shadow-[0_4px_12px_rgba(var(--md-sys-color-primary-rgb),0.3)]">
                            <PlayIcon size={14} fill="currentColor" />
                            <span className="text-[10px] font-bold hidden sm:inline">{isPaused ? 'Reanudar' : 'Iniciar'}</span>
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default CompactHeroBanner;
