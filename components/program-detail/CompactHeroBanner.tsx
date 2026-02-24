import React, { useState } from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, EditIcon, PlayIcon, SettingsIcon, XIcon } from '../icons';
import { SPLIT_TEMPLATES } from '../../data/splitTemplates';

interface CompactHeroBannerProps {
    program: Program;
    isActive: boolean;
    isPaused: boolean;
    onBack: () => void;
    onEdit: () => void;
    onStart: () => void;
    onPause: () => void;
    onOpenSplitChanger: () => void;
    onUpdateProgram?: (program: Program) => void;
    currentWeekIndex: number;
    totalWeeks: number;
    totalAdherence: number;
    trainingDaysCount: number;
}

const DAYS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

const CompactHeroBanner: React.FC<CompactHeroBannerProps> = ({
    program, isActive, isPaused, onBack, onEdit, onStart, onPause,
    onOpenSplitChanger, onUpdateProgram, currentWeekIndex, totalWeeks,
    totalAdherence, trainingDaysCount,
}) => {
    const [configOpen, setConfigOpen] = useState(false);
    const currentSplit = SPLIT_TEMPLATES.find(s => s.id === program.selectedSplitId);

    return (
        <div className="relative w-full shrink-0 min-h-[140px] sm:min-h-[150px]">
            {/* Background */}
            {program.coverImage ? (
                <img src={program.coverImage} alt="" className="absolute inset-0 w-full h-full object-cover" style={{ filter: 'blur(20px) brightness(0.3)' }} />
            ) : (
                <div className="absolute inset-0 bg-gradient-to-r from-[#0a0a0a] to-[#111]" />
            )}
            <div className="absolute inset-0 bg-gradient-to-r from-black/80 via-black/60 to-black/80" />

            {/* Content - 3 filas apiladas */}
            <div className="relative z-10 h-full flex flex-col gap-3 px-4 py-3">
                {/* Fila 1: Título */}
                <div className="flex items-center gap-3 shrink-0">
                    <button onClick={onBack} className="w-8 h-8 shrink-0 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white transition-colors">
                        <ChevronDownIcon size={18} className="rotate-90" />
                    </button>
                    <div className="flex-1 min-w-0">
                        <h1 className="text-lg font-black text-white uppercase tracking-tight leading-tight truncate">
                            {program.name}
                        </h1>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                        {isActive && (
                            <span className="bg-[#00F19F] text-black text-[10px] font-bold px-2.5 py-0.5 rounded-md">
                                Activo
                            </span>
                        )}
                        {isPaused && (
                            <span className="bg-yellow-400 text-black text-[10px] font-bold px-2.5 py-0.5 rounded-md">
                                Pausado
                            </span>
                        )}
                        {!isActive && !isPaused && (
                            <span className="bg-white/10 text-[#8E8E93] text-[10px] font-bold px-2.5 py-0.5 rounded-md">
                                Borrador
                            </span>
                        )}
                    </div>
                </div>

                {/* Fila 2: KPIs */}
                <div className="flex items-center gap-6 flex-wrap shrink-0">
                    <div className="flex items-center gap-1.5">
                        <span className="text-xl font-black text-white leading-none">{currentWeekIndex + 1}</span>
                        <span className="text-[10px] text-[#48484A] font-bold">/{totalWeeks > 0 ? totalWeeks : '∞'} sem</span>
                    </div>
                    <div className="w-px h-5 bg-white/10 hidden sm:block" />
                    <div className="flex items-center gap-1.5">
                        <span className="text-xl font-black leading-none" style={{ color: totalAdherence >= 80 ? '#00F19F' : totalAdherence >= 50 ? '#FFD60A' : '#FF3B30' }}>{totalAdherence}%</span>
                        <span className="text-[10px] text-[#48484A] font-bold">adher.</span>
                    </div>
                    <div className="w-px h-5 bg-white/10 hidden sm:block" />
                    <div className="flex items-center gap-1.5">
                        <span className="text-xl font-black text-white leading-none">{trainingDaysCount}</span>
                        <span className="text-[10px] text-[#48484A] font-bold">días/sem</span>
                    </div>
                </div>

                {/* Fila 3: Acciones */}
                <div className="flex items-center gap-2 shrink-0">
                    <button onClick={() => setConfigOpen(true)} className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white transition-colors">
                        <SettingsIcon size={16} />
                    </button>
                    <button onClick={onEdit} className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white transition-colors">
                        <EditIcon size={16} />
                    </button>
                    {isActive ? (
                        <button onClick={onPause} className="h-8 px-4 rounded-lg bg-yellow-400 flex items-center justify-center gap-1.5 text-black hover:brightness-110 transition-all">
                            <div className="flex gap-0.5"><div className="w-1 h-3 bg-black rounded-sm" /><div className="w-1 h-3 bg-black rounded-sm" /></div>
                            <span className="text-[10px] font-bold hidden sm:inline">Pausar</span>
                        </button>
                    ) : (
                        <button onClick={onStart} className="h-8 px-4 rounded-lg bg-[#00F0FF] flex items-center justify-center gap-1.5 text-white hover:brightness-110 transition-all shadow-[0_0_20px_rgba(0,240,255,0.3)]">
                            <PlayIcon size={14} fill="white" />
                            <span className="text-[10px] font-bold hidden sm:inline">{isPaused ? 'Reanudar' : 'Iniciar'}</span>
                        </button>
                    )}
                </div>
            </div>

            {/* Drawer de configuración (estilo app) */}
            {configOpen && (
                <>
                    <div className="fixed inset-0 z-[150] bg-black/60 backdrop-blur-sm animate-fade-in" onClick={() => setConfigOpen(false)} />
                    <div className="fixed inset-x-0 bottom-0 z-[151] max-h-[85vh] overflow-hidden flex flex-col rounded-t-2xl border-t border-[#00F0FF]/20 bg-[#0a0a0a] shadow-[0_-8px 32px rgba(0,0,0,0.5)] animate-slide-up">
                        <div className="shrink-0 flex items-center justify-between px-5 py-4 border-b border-white/10">
                            <h2 className="text-sm font-black text-white uppercase tracking-widest flex items-center gap-2">
                                <SettingsIcon size={18} className="text-[#00F0FF]" />
                                Ajustes del programa
                            </h2>
                            <button onClick={() => setConfigOpen(false)} className="p-2 rounded-xl text-[#8E8E93] hover:text-white hover:bg-white/5 transition-colors">
                                <XIcon size={18} />
                            </button>
                        </div>
                        <div className="flex-1 overflow-y-auto px-5 py-5 space-y-6">
                            <section>
                                <h3 className="text-[10px] font-bold text-[#8E8E93] uppercase tracking-wider mb-3">Modo</h3>
                                <div className="flex gap-2">
                                    {(['hypertrophy', 'powerlifting'] as const).map(m => (
                                        <button
                                            key={m}
                                            onClick={() => {
                                                if (onUpdateProgram) {
                                                    const updated = JSON.parse(JSON.stringify(program));
                                                    updated.mode = m;
                                                    onUpdateProgram(updated);
                                                }
                                            }}
                                            className={`flex-1 py-3 rounded-xl text-sm font-bold transition-all border ${program.mode === m ? 'bg-[#00F0FF]/15 border-[#00F0FF]/50 text-[#00F0FF]' : 'bg-white/[0.04] border-white/10 text-[#8E8E93] hover:border-white/20 hover:text-white'}`}
                                        >
                                            {m === 'hypertrophy' ? 'Hipertrofia' : 'Powerlifting'}
                                        </button>
                                    ))}
                                </div>
                            </section>
                            <section>
                                <h3 className="text-[10px] font-bold text-[#8E8E93] uppercase tracking-wider mb-3">Día de inicio de semana</h3>
                                <div className="flex gap-1 flex-wrap">
                                    {DAYS.map((d, i) => (
                                        <button
                                            key={i}
                                            onClick={() => {
                                                if (onUpdateProgram) {
                                                    const updated = JSON.parse(JSON.stringify(program));
                                                    updated.startDay = i;
                                                    onUpdateProgram(updated);
                                                }
                                            }}
                                            className={`min-w-[2.5rem] py-2.5 rounded-xl text-xs font-bold transition-all ${(program.startDay ?? 1) === i ? 'bg-[#00F0FF] text-black' : 'bg-white/[0.04] text-[#8E8E93] hover:bg-white/10 hover:text-white'}`}
                                        >
                                            {d}
                                        </button>
                                    ))}
                                </div>
                            </section>
                            <section>
                                <h3 className="text-[10px] font-bold text-[#8E8E93] uppercase tracking-wider mb-3">Split actual</h3>
                                <button
                                    onClick={() => { setConfigOpen(false); onOpenSplitChanger(); }}
                                    className="w-full text-left px-4 py-3 rounded-xl bg-white/[0.04] border border-white/10 hover:border-[#00F0FF]/30 transition-colors"
                                >
                                    <span className="text-sm font-bold text-white block">{currentSplit?.name || 'No definido'}</span>
                                    {currentSplit && (
                                        <div className="flex gap-1 mt-2">
                                            {currentSplit.pattern.map((d, i) => (
                                                <div key={i} className={`flex-1 h-1.5 rounded-full ${d.toLowerCase() === 'descanso' ? 'bg-white/10' : 'bg-[#00F0FF]/60'}`} title={d} />
                                            ))}
                                        </div>
                                    )}
                                    <span className="text-[10px] text-[#00F0FF] mt-1 inline-block">Toca para cambiar</span>
                                </button>
                            </section>
                            {program.description && (
                                <section>
                                    <h3 className="text-[10px] font-bold text-[#8E8E93] uppercase tracking-wider mb-2">Descripción</h3>
                                    <p className="text-xs text-[#8E8E93] leading-relaxed px-1">{program.description}</p>
                                </section>
                            )}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
};

export default CompactHeroBanner;
