import React, { useState, useMemo } from 'react';
import { SPLIT_TEMPLATES, SplitTemplate, SplitTag } from '../data/splitTemplates';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SearchIcon, CheckCircleIcon, XIcon, AlertTriangleIcon, ChevronDownIcon } from './icons';

export type SplitChangeScope = 'week' | 'block' | 'program';

interface SplitChangerModalProps {
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

const SplitChangerModal: React.FC<SplitChangerModalProps> = ({
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
        <div className="fixed inset-0 z-[9999] bg-black/90 backdrop-blur-sm flex items-end sm:items-center justify-center animate-fade-in">
            <div className="w-full max-w-lg max-h-[90vh] bg-[#0a0a0a] border border-white/10 rounded-t-3xl sm:rounded-3xl flex flex-col overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between p-5 border-b border-white/10">
                    <div>
                        <h2 className="text-lg font-black text-white uppercase tracking-tight">
                            {step === 'gallery' ? 'Cambiar Split' : 'Configurar Cambio'}
                        </h2>
                        <p className="text-[10px] text-zinc-500 font-bold uppercase tracking-widest mt-0.5">
                            {step === 'gallery' ? 'Selecciona una nueva plantilla' : selectedSplit?.name}
                        </p>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full border border-white/10 text-zinc-400 hover:text-white transition-colors">
                        <XIcon size={16} />
                    </button>
                </div>

                {step === 'gallery' ? (
                    <div className="flex flex-col flex-1 overflow-hidden">
                        {/* Search */}
                        <div className="px-5 py-3 border-b border-white/5">
                            <div className="relative">
                                <SearchIcon size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
                                <input
                                    type="text"
                                    value={search}
                                    onChange={e => setSearch(e.target.value)}
                                    placeholder="Buscar split..."
                                    className="w-full bg-zinc-900 border border-white/10 rounded-xl pl-9 pr-3 py-2.5 text-xs text-white placeholder-zinc-600 focus:ring-1 focus:ring-white/30 focus:border-white/30"
                                />
                            </div>
                        </div>

                        {/* Filter tags */}
                        <div className="px-5 py-2 flex gap-2 overflow-x-auto hide-scrollbar border-b border-white/5">
                            {FILTER_TAGS.map(tag => (
                                <button
                                    key={tag}
                                    onClick={() => setFilter(tag)}
                                    className={`px-3 py-1.5 rounded-full text-[9px] font-black uppercase tracking-wider whitespace-nowrap transition-all border ${
                                        filter === tag
                                            ? 'bg-white text-black border-white'
                                            : 'bg-transparent text-zinc-500 border-zinc-800 hover:border-zinc-500'
                                    }`}
                                >
                                    {tag}
                                </button>
                            ))}
                        </div>

                        {/* Split list */}
                        <div className="flex-1 overflow-y-auto custom-scrollbar p-3 space-y-2">
                            {filteredSplits.map(split => {
                                const isCurrent = split.id === currentSplitId;
                                const trainingDays = split.pattern.filter(d => d.toLowerCase() !== 'descanso').length;
                                return (
                                    <button
                                        key={split.id}
                                        onClick={() => handleSelectSplit(split)}
                                        className={`w-full text-left p-4 rounded-2xl border transition-all group ${
                                            isCurrent
                                                ? 'bg-white/5 border-white/20'
                                                : 'bg-zinc-900/50 border-white/5 hover:border-white/20 hover:bg-zinc-900'
                                        }`}
                                    >
                                        <div className="flex items-start justify-between mb-2">
                                            <div className="flex-1">
                                                <div className="flex items-center gap-2">
                                                    <span className="text-sm font-black text-white">{split.name}</span>
                                                    {isCurrent && (
                                                        <span className="text-[8px] font-black uppercase px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30">
                                                            ACTUAL
                                                        </span>
                                                    )}
                                                </div>
                                                <p className="text-[10px] text-zinc-500 mt-0.5">{split.description}</p>
                                            </div>
                                            <span className="text-[10px] font-black text-zinc-500">{trainingDays}d</span>
                                        </div>
                                        <div className="flex gap-1 mt-2">
                                            {split.pattern.map((day, i) => (
                                                <div
                                                    key={i}
                                                    className={`flex-1 h-1.5 rounded-full ${
                                                        day.toLowerCase() === 'descanso' ? 'bg-zinc-800' : 'bg-white/40'
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
                    <div className="flex-1 overflow-y-auto custom-scrollbar p-5 space-y-6">
                        {/* Selected split preview */}
                        {selectedSplit && (
                            <div className="bg-zinc-900 border border-white/10 rounded-2xl p-4">
                                <div className="flex items-center justify-between mb-3">
                                    <span className="text-sm font-black text-white">{selectedSplit.name}</span>
                                    <button onClick={handleBack} className="text-[10px] font-bold text-zinc-400 underline">
                                        Cambiar
                                    </button>
                                </div>
                                <div className="flex gap-1">
                                    {selectedSplit.pattern.map((day, i) => {
                                        const dayLabel = DAYS_OF_WEEK[(startDay + i) % 7]?.label || '';
                                        const isRest = day.toLowerCase() === 'descanso';
                                        return (
                                            <div key={i} className="flex-1 text-center">
                                                <div className="text-[8px] font-bold text-zinc-600 mb-1">{dayLabel.slice(0, 3)}</div>
                                                <div className={`h-1.5 rounded-full ${isRest ? 'bg-zinc-800' : 'bg-white/40'}`} />
                                                <div className={`text-[7px] mt-1 font-bold truncate ${isRest ? 'text-zinc-700' : 'text-zinc-400'}`}>
                                                    {isRest ? '-' : day}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Start day selector */}
                        <div className="flex items-center justify-between">
                            <span className="text-[10px] font-black text-zinc-400 uppercase tracking-widest">Inicio de Semana</span>
                            <div className="relative">
                                <select
                                    value={startDay}
                                    onChange={e => setStartDay(parseInt(e.target.value))}
                                    className="appearance-none bg-zinc-900 border border-white/10 rounded-lg px-3 py-1.5 pr-7 text-xs font-bold text-white focus:ring-1 focus:ring-white/30"
                                >
                                    {DAYS_OF_WEEK.map(d => (
                                        <option key={d.value} value={d.value}>{d.label}</option>
                                    ))}
                                </select>
                                <ChevronDownIcon size={12} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none text-zinc-500" />
                            </div>
                        </div>

                        {/* Scope selector */}
                        <div>
                            <span className="text-[10px] font-black text-zinc-400 uppercase tracking-widest block mb-3">Alcance del cambio</span>
                            <div className="space-y-2">
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('week')}
                                        className={`w-full text-left p-4 rounded-xl border transition-all ${
                                            scope === 'week' ? 'bg-white/5 border-white/30' : 'border-white/5 hover:border-white/15'
                                        }`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${scope === 'week' ? 'border-white' : 'border-zinc-600'}`}>
                                                {scope === 'week' && <div className="w-2.5 h-2.5 rounded-full bg-white" />}
                                            </div>
                                            <div>
                                                <span className="text-xs font-black text-white">Solo esta semana</span>
                                                <p className="text-[10px] text-zinc-500">Cambia las sesiones de la semana seleccionada</p>
                                            </div>
                                        </div>
                                    </button>
                                )}
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('block')}
                                        className={`w-full text-left p-4 rounded-xl border transition-all ${
                                            scope === 'block' ? 'bg-white/5 border-white/30' : 'border-white/5 hover:border-white/15'
                                        }`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${scope === 'block' ? 'border-white' : 'border-zinc-600'}`}>
                                                {scope === 'block' && <div className="w-2.5 h-2.5 rounded-full bg-white" />}
                                            </div>
                                            <div>
                                                <span className="text-xs font-black text-white">Todo el bloque</span>
                                                <p className="text-[10px] text-zinc-500">Aplica a todas las semanas del bloque actual</p>
                                            </div>
                                        </div>
                                    </button>
                                )}
                                <button
                                    onClick={() => setScope('program')}
                                    className={`w-full text-left p-4 rounded-xl border transition-all ${
                                        scope === 'program' ? 'bg-white/5 border-white/30' : 'border-white/5 hover:border-white/15'
                                    }`}
                                >
                                    <div className="flex items-center gap-3">
                                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${scope === 'program' ? 'border-white' : 'border-zinc-600'}`}>
                                            {scope === 'program' && <div className="w-2.5 h-2.5 rounded-full bg-white" />}
                                        </div>
                                        <div>
                                            <span className="text-xs font-black text-white">{isSimpleProgram ? 'Todo el ciclo' : 'Todo el programa'}</span>
                                            <p className="text-[10px] text-zinc-500">Aplica a todas las semanas de todos los bloques</p>
                                        </div>
                                    </div>
                                </button>
                            </div>
                        </div>

                        {/* Preserve exercises toggle */}
                        <div className="bg-zinc-900/50 border border-white/5 rounded-xl p-4">
                            <div className="flex items-start gap-3">
                                <AlertTriangleIcon size={16} className="text-yellow-500 mt-0.5 shrink-0" />
                                <div className="flex-1">
                                    <span className="text-[10px] font-black text-zinc-400 uppercase tracking-widest block mb-2">
                                        Ejercicios existentes
                                    </span>
                                    <div className="space-y-2">
                                        <button
                                            onClick={() => setPreserveExercises(true)}
                                            className={`w-full text-left px-3 py-2.5 rounded-lg border transition-all ${
                                                preserveExercises ? 'bg-white/5 border-white/20' : 'border-white/5'
                                            }`}
                                        >
                                            <span className="text-[11px] font-bold text-white">Intentar preservar</span>
                                            <p className="text-[9px] text-zinc-500">Mapea ejercicios por nombre de sesión al nuevo split</p>
                                        </button>
                                        <button
                                            onClick={() => setPreserveExercises(false)}
                                            className={`w-full text-left px-3 py-2.5 rounded-lg border transition-all ${
                                                !preserveExercises ? 'bg-white/5 border-white/20' : 'border-white/5'
                                            }`}
                                        >
                                            <span className="text-[11px] font-bold text-white">Empezar limpio</span>
                                            <p className="text-[9px] text-zinc-500">Crear sesiones vacías con los nuevos nombres</p>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Footer */}
                {step === 'scope' && (
                    <div className="p-5 border-t border-white/10 flex gap-3">
                        <Button variant="secondary" onClick={handleBack} className="flex-1 !py-3 !text-[10px]">
                            Atrás
                        </Button>
                        <button
                            onClick={handleApply}
                            className="flex-1 py-3 bg-white text-black text-[10px] font-black uppercase tracking-widest rounded-xl hover:bg-zinc-200 transition-colors"
                        >
                            Aplicar Cambio
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};

export default SplitChangerModal;
