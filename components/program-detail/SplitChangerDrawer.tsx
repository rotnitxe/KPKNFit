import React, { useState, useMemo } from 'react';
import { SPLIT_TEMPLATES, SplitTemplate, SplitTag } from '../../data/splitTemplates';
import { SearchIcon, XIcon, AlertTriangleIcon, ChevronDownIcon } from '../icons';

export type SplitChangeScope = 'week' | 'block' | 'program';

interface SplitChangerDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    onApply: (split: SplitTemplate, scope: SplitChangeScope, preserveExercises: boolean, startDay: number) => void;
    currentSplitId?: string;
    isSimpleProgram?: boolean;
    currentStartDay?: number;
}

const FILTER_TAGS: (SplitTag | 'Todos')[] = ['Todos', 'Recomendado por KPKN', 'Alta Frecuencia', 'Baja Frecuencia', 'Balanceado', 'Alto Volumen', 'Powerlifting', 'Personalizado'];
const DAYS_OF_WEEK = [
    { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
    { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
    { label: 'Sábado', value: 6 }
];

const SplitChangerDrawer: React.FC<SplitChangerDrawerProps> = ({
    isOpen, onClose, onApply, currentSplitId, isSimpleProgram, currentStartDay = 1
}) => {
    const [step, setStep] = useState<'gallery' | 'scope'>('gallery');
    const [selectedSplit, setSelectedSplit] = useState<SplitTemplate | null>(null);
    const [scope, setScope] = useState<SplitChangeScope>(isSimpleProgram ? 'program' : 'block');
    const [preserveExercises, setPreserveExercises] = useState(true);
    const [startDay, setStartDay] = useState(currentStartDay);
    const [filter, setFilter] = useState<SplitTag | 'Todos'>('Todos');
    const [search, setSearch] = useState('');

    const filteredSplits = useMemo(() => {
        return SPLIT_TEMPLATES.filter(s => {
            if (s.id === 'custom') return false;
            const matchesTag = filter === 'Todos' || s.tags.includes(filter);
            const q = search.toLowerCase();
            const matchesSearch = !q || s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q);
            return matchesTag && matchesSearch;
        });
    }, [filter, search]);

    const handleSelectSplit = (split: SplitTemplate) => {
        setSelectedSplit(split);
        setStep('scope');
    };

    const handleApply = () => {
        if (!selectedSplit) return;
        onApply(selectedSplit, scope, preserveExercises, startDay);
        onClose();
    };

    const handleBack = () => {
        setStep('gallery');
        setSelectedSplit(null);
    };

    if (!isOpen) return null;

    return (
        <>
            <div className="fixed inset-0 z-[101] bg-black/50" onClick={onClose} />
            <div className="fixed top-0 right-0 bottom-0 z-[102] w-[320px] max-w-[90vw] bg-[#0a0a0a] border-l border-cyber-cyan/20 flex flex-col animate-slide-left">
                {/* Header NERD */}
                <div className="px-4 py-3 border-b border-cyber-cyan/10 flex items-center justify-between shrink-0">
                    <div>
                        <h2 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90">
                            {step === 'gallery' ? 'Cambiar Split' : 'Configurar'}
                        </h2>
                        <p className="text-[9px] font-mono text-slate-500 uppercase tracking-wider mt-0.5">
                            {step === 'gallery' ? 'Selecciona plantilla' : selectedSplit?.name}
                        </p>
                    </div>
                    <button onClick={onClose} className="w-7 h-7 rounded-lg border border-cyber-cyan/20 flex items-center justify-center text-slate-500 hover:text-white hover:border-cyber-cyan/40 transition-colors">
                        <XIcon size={14} />
                    </button>
                </div>

                {step === 'gallery' ? (
                    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
                        {/* Search */}
                        <div className="px-4 py-3 border-b border-cyber-cyan/10 shrink-0">
                            <div className="relative">
                                <SearchIcon size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
                                <input
                                    type="text"
                                    value={search}
                                    onChange={e => setSearch(e.target.value)}
                                    placeholder="Buscar split..."
                                    className="w-full bg-[#0d0d0d] border border-cyber-cyan/20 rounded-lg pl-9 pr-3 py-2 text-[10px] font-mono text-white placeholder-slate-600 focus:border-cyber-cyan/50 focus:ring-0 outline-none"
                                />
                            </div>
                        </div>

                        {/* Filter tags */}
                        <div className="px-4 py-3 border-b border-cyber-cyan/10 shrink-0">
                            <div className="flex gap-2 overflow-x-auto pb-1 custom-scrollbar -mx-1 px-1">
                                {FILTER_TAGS.map(tag => (
                                    <button
                                        key={tag}
                                        onClick={() => setFilter(tag)}
                                        className={`px-2.5 py-1 rounded border shrink-0 whitespace-nowrap text-[9px] font-mono font-black uppercase tracking-wider transition-all ${
                                            filter === tag
                                                ? 'bg-cyber-cyan/20 text-cyber-cyan border-cyber-cyan/40'
                                                : 'bg-transparent text-slate-500 border-slate-700/50 hover:border-cyber-cyan/30'
                                        }`}
                                    >
                                        {tag}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Split list */}
                        <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar p-3 space-y-2">
                            {filteredSplits.map(split => {
                                const isCurrent = split.id === currentSplitId;
                                const trainingDays = split.pattern.filter(d => d.toLowerCase() !== 'descanso').length;
                                return (
                                    <button
                                        key={split.id}
                                        onClick={() => handleSelectSplit(split)}
                                        className={`w-full text-left p-3 rounded-xl border transition-all ${
                                            isCurrent
                                                ? 'bg-cyber-cyan/10 border-cyber-cyan/30'
                                                : 'bg-[#0d0d0d]/80 border-cyber-cyan/10 hover:border-cyber-cyan/30'
                                        }`}
                                    >
                                        <div className="flex items-start justify-between gap-2 mb-1.5">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 flex-wrap">
                                                    <span className="text-[11px] font-mono font-black text-white truncate">{split.name}</span>
                                                    {isCurrent && (
                                                        <span className="text-[8px] font-mono font-black uppercase px-2 py-0.5 rounded border border-cyber-cyan/40 bg-cyber-cyan/20 text-cyber-cyan shrink-0">
                                                            ACTUAL
                                                        </span>
                                                    )}
                                                </div>
                                                <p className="text-[9px] font-mono text-slate-500 mt-0.5 line-clamp-2">{split.description}</p>
                                            </div>
                                            <span className="text-[9px] font-mono font-black text-slate-500 shrink-0">{trainingDays}d</span>
                                        </div>
                                        <div className="flex gap-0.5 mt-1.5">
                                            {split.pattern.map((day, i) => (
                                                <div
                                                    key={i}
                                                    className={`flex-1 h-1 rounded-full ${
                                                        day.toLowerCase() === 'descanso' ? 'bg-slate-800' : 'bg-cyber-cyan/40'
                                                    }`}
                                                />
                                            ))}
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                ) : (
                    <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar p-4 space-y-5">
                        {/* Selected split preview */}
                        {selectedSplit && (
                            <div className="bg-[#0d0d0d] border border-cyber-cyan/20 rounded-xl p-4">
                                <div className="flex items-center justify-between mb-3">
                                    <span className="text-[11px] font-mono font-black text-white">{selectedSplit.name}</span>
                                    <button onClick={handleBack} className="text-[9px] font-mono font-bold text-cyber-cyan/90 hover:text-cyber-cyan border-b border-cyber-cyan/30">
                                        Cambiar
                                    </button>
                                </div>
                                <div className="flex gap-0.5">
                                    {selectedSplit.pattern.map((day, i) => {
                                        const dayLabel = DAYS_OF_WEEK[(startDay + i) % 7]?.label || '';
                                        const isRest = day.toLowerCase() === 'descanso';
                                        return (
                                            <div key={i} className="flex-1 text-center">
                                                <div className="text-[8px] font-mono font-bold text-slate-600 mb-1">{dayLabel.slice(0, 3)}</div>
                                                <div className={`h-1.5 rounded-full ${isRest ? 'bg-slate-800' : 'bg-cyber-cyan/40'}`} />
                                                <div className={`text-[7px] mt-1 font-mono font-bold truncate ${isRest ? 'text-slate-700' : 'text-slate-400'}`}>
                                                    {isRest ? '-' : day}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Start day */}
                        <div className="flex items-center justify-between">
                            <span className="text-[9px] font-mono font-black text-slate-400 uppercase tracking-widest">Inicio Semana</span>
                            <div className="relative">
                                <select
                                    value={startDay}
                                    onChange={e => setStartDay(parseInt(e.target.value))}
                                    className="appearance-none bg-[#0d0d0d] border border-cyber-cyan/20 rounded-lg px-3 py-1.5 pr-7 text-[10px] font-mono font-bold text-white focus:border-cyber-cyan/50 outline-none"
                                >
                                    {DAYS_OF_WEEK.map(d => (
                                        <option key={d.value} value={d.value}>{d.label}</option>
                                    ))}
                                </select>
                                <ChevronDownIcon size={12} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none text-slate-500" />
                            </div>
                        </div>

                        {/* Scope */}
                        <div>
                            <span className="text-[9px] font-mono font-black text-slate-400 uppercase tracking-widest block mb-2">Alcance</span>
                            <div className="space-y-2">
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('week')}
                                        className={`w-full text-left p-3 rounded-xl border transition-all ${
                                            scope === 'week' ? 'bg-cyber-cyan/10 border-cyber-cyan/30' : 'border-cyber-cyan/10 hover:border-cyber-cyan/30'
                                        }`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'week' ? 'border-cyber-cyan bg-cyber-cyan/30' : 'border-slate-600'}`}>
                                                {scope === 'week' && <div className="w-2 h-2 rounded-full bg-cyber-cyan" />}
                                            </div>
                                            <div>
                                                <span className="text-[10px] font-mono font-black text-white">Solo esta semana</span>
                                                <p className="text-[9px] font-mono text-slate-500">Sesiones de la semana actual</p>
                                            </div>
                                        </div>
                                    </button>
                                )}
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('block')}
                                        className={`w-full text-left p-3 rounded-xl border transition-all ${
                                            scope === 'block' ? 'bg-cyber-cyan/10 border-cyber-cyan/30' : 'border-cyber-cyan/10 hover:border-cyber-cyan/30'
                                        }`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'block' ? 'border-cyber-cyan bg-cyber-cyan/30' : 'border-slate-600'}`}>
                                                {scope === 'block' && <div className="w-2 h-2 rounded-full bg-cyber-cyan" />}
                                            </div>
                                            <div>
                                                <span className="text-[10px] font-mono font-black text-white">Todo el bloque</span>
                                                <p className="text-[9px] font-mono text-slate-500">Todas las semanas del bloque</p>
                                            </div>
                                        </div>
                                    </button>
                                )}
                                <button
                                    onClick={() => setScope('program')}
                                    className={`w-full text-left p-3 rounded-xl border transition-all ${
                                        scope === 'program' ? 'bg-cyber-cyan/10 border-cyber-cyan/30' : 'border-cyber-cyan/10 hover:border-cyber-cyan/30'
                                    }`}
                                >
                                    <div className="flex items-center gap-3">
                                        <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'program' ? 'border-cyber-cyan bg-cyber-cyan/30' : 'border-slate-600'}`}>
                                            {scope === 'program' && <div className="w-2 h-2 rounded-full bg-cyber-cyan" />}
                                        </div>
                                        <div>
                                            <span className="text-[10px] font-mono font-black text-white">{isSimpleProgram ? 'Todo el ciclo' : 'Todo el programa'}</span>
                                            <p className="text-[9px] font-mono text-slate-500">Semanas de todos los bloques</p>
                                        </div>
                                    </div>
                                </button>
                            </div>
                        </div>

                        {/* Preserve exercises */}
                        <div className="bg-[#0d0d0d]/80 border border-cyber-cyan/10 rounded-xl p-4">
                            <div className="flex items-start gap-3">
                                <AlertTriangleIcon size={14} className="text-amber-500/80 mt-0.5 shrink-0" />
                                <div className="flex-1">
                                    <span className="text-[9px] font-mono font-black text-slate-400 uppercase tracking-widest block mb-2">Ejercicios existentes</span>
                                    <div className="space-y-2">
                                        <button
                                            onClick={() => setPreserveExercises(true)}
                                            className={`w-full text-left px-3 py-2 rounded-lg border transition-all ${
                                                preserveExercises ? 'bg-cyber-cyan/10 border-cyber-cyan/30' : 'border-cyber-cyan/10'
                                            }`}
                                        >
                                            <span className="text-[10px] font-mono font-bold text-white">Preservar</span>
                                            <p className="text-[9px] font-mono text-slate-500">Mapea por nombre de sesión</p>
                                        </button>
                                        <button
                                            onClick={() => setPreserveExercises(false)}
                                            className={`w-full text-left px-3 py-2 rounded-lg border transition-all ${
                                                !preserveExercises ? 'bg-cyber-cyan/10 border-cyber-cyan/30' : 'border-cyber-cyan/10'
                                            }`}
                                        >
                                            <span className="text-[10px] font-mono font-bold text-white">Empezar limpio</span>
                                            <p className="text-[9px] font-mono text-slate-500">Sesiones vacías</p>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Footer */}
                {step === 'scope' && (
                    <div className="p-4 pb-[max(1rem,env(safe-area-inset-bottom))] border-t border-cyber-cyan/10 flex gap-3 shrink-0">
                        <button
                            onClick={handleBack}
                            className="flex-1 py-2.5 rounded-xl border border-cyber-cyan/20 text-[10px] font-mono font-black uppercase tracking-widest text-slate-400 hover:bg-cyber-cyan/10 hover:text-white transition-colors"
                        >
                            Atrás
                        </button>
                        <button
                            onClick={handleApply}
                            className="flex-1 py-2.5 rounded-xl bg-cyber-cyan text-black text-[10px] font-mono font-black uppercase tracking-widest hover:bg-cyber-cyan transition-colors"
                        >
                            Aplicar
                        </button>
                    </div>
                )}
            </div>
        </>
    );
};

export default SplitChangerDrawer;
