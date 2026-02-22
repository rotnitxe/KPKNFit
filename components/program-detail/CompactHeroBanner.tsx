import React, { useState } from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, DumbbellIcon, EditIcon, PlayIcon, SettingsIcon, XIcon } from '../icons';
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
                    <div className="relative">
                        <button onClick={() => setConfigOpen(!configOpen)} className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white transition-colors">
                            <SettingsIcon size={16} />
                        </button>
                        {configOpen && (
                            <>
                                <div className="fixed inset-0 z-[150]" onClick={() => setConfigOpen(false)} />
                                <div className="absolute bottom-full right-0 mb-2 z-[151] w-72 bg-[#1a1a1a] border border-white/10 rounded-xl p-4 shadow-2xl space-y-3">
                                    <div className="flex items-center justify-between">
                                        <span className="text-xs font-bold text-white uppercase tracking-wide">Configuración</span>
                                        <button onClick={() => setConfigOpen(false)} className="text-[#48484A] hover:text-white"><XIcon size={14} /></button>
                                    </div>
                                    {/* Mode */}
                                    <div>
                                        <label className="text-[10px] text-[#8E8E93] font-bold block mb-1">Modo</label>
                                        <div className="flex gap-1">
                                            {(['hypertrophy', 'powerlifting'] as const).map(m => (
                                                <button key={m} onClick={() => {
                                                    if (onUpdateProgram) {
                                                        const updated = JSON.parse(JSON.stringify(program));
                                                        updated.mode = m;
                                                        onUpdateProgram(updated);
                                                    }
                                                }} className={`flex-1 py-1.5 rounded-lg text-[10px] font-bold transition-colors ${program.mode === m ? 'bg-[#FC4C02] text-white' : 'bg-white/5 text-[#8E8E93] hover:text-white'}`}>
                                                    {m === 'hypertrophy' ? 'Hipertrofia' : 'Powerlifting'}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                    {/* Start day */}
                                    <div>
                                        <label className="text-[10px] text-[#8E8E93] font-bold block mb-1">Día de inicio</label>
                                        <div className="flex gap-0.5">
                                            {DAYS.map((d, i) => (
                                                <button key={i} onClick={() => {
                                                    if (onUpdateProgram) {
                                                        const updated = JSON.parse(JSON.stringify(program));
                                                        updated.startDay = i;
                                                        onUpdateProgram(updated);
                                                    }
                                                }} className={`flex-1 py-1.5 rounded text-[9px] font-bold transition-colors ${(program.startDay ?? 1) === i ? 'bg-[#FC4C02] text-white' : 'bg-white/5 text-[#48484A] hover:text-white'}`}>
                                                    {d}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                    {/* Split */}
                                    <div>
                                        <label className="text-[10px] text-[#8E8E93] font-bold block mb-1">Split actual</label>
                                        <button onClick={() => { setConfigOpen(false); onOpenSplitChanger(); }} className="w-full text-left px-3 py-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors">
                                            <span className="text-xs font-bold text-white">{currentSplit?.name || 'No definido'}</span>
                                            {currentSplit && (
                                                <div className="flex gap-0.5 mt-1">
                                                    {currentSplit.pattern.map((d, i) => (
                                                        <div key={i} className={`flex-1 h-1 rounded-full ${d.toLowerCase() === 'descanso' ? 'bg-[#48484A]' : 'bg-[#FC4C02]'}`} />
                                                    ))}
                                                </div>
                                            )}
                                        </button>
                                    </div>
                                    {/* Description */}
                                    {program.description && (
                                        <div>
                                            <label className="text-[10px] text-[#8E8E93] font-bold block mb-1">Descripción</label>
                                            <p className="text-[11px] text-[#8E8E93] leading-relaxed">{program.description}</p>
                                        </div>
                                    )}
                                </div>
                            </>
                        )}
                    </div>
                    <button onClick={onEdit} className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white transition-colors">
                        <EditIcon size={16} />
                    </button>
                    {isActive ? (
                        <button onClick={onPause} className="h-8 px-4 rounded-lg bg-yellow-400 flex items-center justify-center gap-1.5 text-black hover:brightness-110 transition-all">
                            <div className="flex gap-0.5"><div className="w-1 h-3 bg-black rounded-sm" /><div className="w-1 h-3 bg-black rounded-sm" /></div>
                            <span className="text-[10px] font-bold hidden sm:inline">Pausar</span>
                        </button>
                    ) : (
                        <button onClick={onStart} className="h-8 px-4 rounded-lg bg-[#FC4C02] flex items-center justify-center gap-1.5 text-white hover:brightness-110 transition-all shadow-[0_0_20px_rgba(252,76,2,0.3)]">
                            <PlayIcon size={14} fill="white" />
                            <span className="text-[10px] font-bold hidden sm:inline">{isPaused ? 'Reanudar' : 'Iniciar'}</span>
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default CompactHeroBanner;
