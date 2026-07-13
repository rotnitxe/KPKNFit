import React, { useState, useMemo } from 'react';
import { SPLIT_TEMPLATES, SplitTemplate, SplitTag } from '../../data/splitTemplates';
import { SearchIcon, XIcon, AlertTriangleIcon, ChevronDownIcon, CheckIcon } from '../icons';

export type SplitChangeScope = 'week' | 'block' | 'program';

interface SplitChangerDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    onApply: (split: SplitTemplate, scope: SplitChangeScope, preserveExercises: boolean, startDay: number) => void;
    currentSplitId?: string;
    isSimpleProgram?: boolean;
    currentStartDay?: number;
    displayMode?: 'drawer' | 'inline';
}

const FILTER_TAGS: (SplitTag | 'Todos')[] = ['Todos', 'Recomendado por KPKN', 'Alta Frecuencia', 'Baja Frecuencia', 'Balanceado', 'Alto Volumen', 'Powerlifting', 'Personalizado'];
const DAYS_OF_WEEK = [
    { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
    { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
    { label: 'Sábado', value: 6 }
];

const SplitChangerDrawer: React.FC<SplitChangerDrawerProps> = ({
    isOpen, onClose, onApply, currentSplitId, isSimpleProgram, currentStartDay = 1, displayMode = 'drawer'
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

    const isInline = displayMode === 'inline';

    return (
        <>
            {/* Backdrop Liquid Glass - solo en modo drawer */}
            {!isInline && (
                <div className="fixed inset-0 z-[101] bg-black/20 backdrop-blur-md" onClick={onClose} />
            )}

            {/* Bottom Sheet Liquid Glass Claro */}
            <div className={`${
                isInline 
                    ? 'relative z-auto w-full max-h-none bg-white rounded-[20px] flex flex-col shadow-sm border border-zinc-200' 
                    : 'fixed bottom-0 left-0 right-0 z-[102] w-full max-h-[90vh] bg-white/90 backdrop-blur-xl rounded-t-[32px] flex flex-col shadow-[0_-8px_32px_rgba(0,0,0,0.12)]'
            }`}>
                {/* Drag Handle - solo en modo drawer */}
                {!isInline && (
                    <div className="w-full flex justify-center pt-4 pb-2 shrink-0">
                        <div className="w-10 h-1.5 rounded-full bg-gradient-to-r from-zinc-300 via-zinc-400 to-zinc-300 opacity-60" />
                    </div>
                )}

                {/* Header */}
                <div className="px-6 py-4 flex items-center justify-between shrink-0 border-b border-zinc-100">
                    <div>
                        <h2 className="text-lg font-black text-zinc-900 tracking-tight">
                            {step === 'gallery' ? 'Cambiar Split' : 'Configurar Split'}
                        </h2>
                        <p className="text-[11px] text-zinc-500 uppercase tracking-widest mt-0.5">
                            {step === 'gallery' ? 'Selecciona una plantilla' : selectedSplit?.name}
                        </p>
                    </div>
                    {!isInline && (
                        <button onClick={onClose} className="w-10 h-10 rounded-full bg-zinc-100 hover:bg-zinc-200 transition-colors flex items-center justify-center">
                            <XIcon size={18} className="text-zinc-600" />
                        </button>
                    )}
                </div>

                {step === 'gallery' ? (
                    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
                        {/* Search */}
                        <div className="px-6 py-4 shrink-0">
                            <div className="relative">
                                <SearchIcon size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-zinc-400" />
                                <input
                                    type="text"
                                    value={search}
                                    onChange={e => setSearch(e.target.value)}
                                    placeholder="Buscar split..."
                                    className="w-full bg-zinc-50/80 backdrop-blur-sm rounded-[24px] pl-12 pr-4 py-3.5 text-sm text-zinc-900 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-purple-500/30 focus:bg-white transition-all"
                                />
                            </div>
                        </div>

                        {/* Filter tags */}
                        <div className="px-6 py-2 shrink-0">
                            <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar -mx-2 px-2">
                                {FILTER_TAGS.map(tag => (
                                    <button
                                        key={tag}
                                        onClick={() => setFilter(tag)}
                                        className={`px-4 py-2 rounded-full border shrink-0 whitespace-nowrap text-[9px] font-black uppercase tracking-[0.15em] transition-all ${filter === tag
                                            ? 'bg-gradient-to-r from-purple-500 to-purple-600 text-white border-transparent shadow-lg shadow-purple-500/30'
                                            : 'bg-white/80 backdrop-blur-sm text-zinc-500 border-zinc-200 hover:border-purple-300 hover:text-purple-600'
                                            }`}
                                    >
                                        {tag}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Split list */}
                        <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar px-6 py-3 space-y-3 pb-[max(140px,calc(100px+env(safe-area-inset-bottom)))]">
                            {filteredSplits.map(split => {
                                const isCurrent = split.id === currentSplitId;
                                const trainingDays = split.pattern.filter(d => d.toLowerCase() !== 'descanso').length;
                                return (
                                    <button
                                        key={split.id}
                                        onClick={() => handleSelectSplit(split)}
                                        className={`w-full text-left p-4 rounded-[20px] border transition-all backdrop-blur-sm ${isCurrent
                                            ? 'bg-gradient-to-br from-purple-50 to-white border-purple-300 shadow-lg shadow-purple-500/10'
                                            : 'bg-white/60 backdrop-blur-sm border-zinc-200/60 hover:bg-white hover:border-purple-200 hover:shadow-md'
                                            }`}
                                    >
                                        <div className="flex items-start justify-between gap-3 mb-3">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 flex-wrap mb-1.5">
                                                    <span className={`text-sm font-black truncate ${isCurrent ? 'text-purple-900' : 'text-zinc-900'}`}>
                                                        {split.name}
                                                    </span>
                                                    {isCurrent && (
                                                        <span className="text-[8px] font-black uppercase px-2 py-0.5 rounded-full bg-gradient-to-r from-purple-500 to-purple-600 text-white shrink-0 shadow-sm">
                                                            Actual
                                                        </span>
                                                    )}
                                                </div>
                                                <p className={`text-[10px] line-clamp-2 ${isCurrent ? 'text-purple-700/70' : 'text-zinc-500'}`}>
                                                    {split.description}
                                                </p>
                                            </div>
                                            <div className={`text-lg font-black shrink-0 ${isCurrent ? 'text-purple-600' : 'text-zinc-400'}`}>
                                                {trainingDays}d
                                            </div>
                                        </div>
                                        <div className="flex gap-1.5">
                                            {split.pattern.map((day, i) => (
                                                <div
                                                    key={i}
                                                    className={`flex-1 h-2 rounded-full transition-all ${day.toLowerCase() === 'descanso'
                                                        ? 'bg-zinc-100'
                                                        : isCurrent ? 'bg-gradient-to-r from-purple-500 to-purple-600' : 'bg-purple-400/60'
                                                        }`}
                                                    title={day}
                                                />
                                            ))}
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                ) : (
                    <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar px-6 py-4 space-y-5">
                        {/* Selected split preview */}
                        {selectedSplit && (
                            <div className="bg-gradient-to-br from-zinc-50 to-white rounded-[20px] p-4 border border-zinc-200/60">
                                <div className="flex items-center justify-between mb-4">
                                    <span className="text-sm font-black text-zinc-900">{selectedSplit.name}</span>
                                    <button onClick={handleBack} className="text-[9px] font-black uppercase tracking-[0.15em] text-purple-600 hover:text-purple-700 transition-colors">
                                        Cambiar
                                    </button>
                                </div>
                                <div className="flex gap-1.5">
                                    {selectedSplit.pattern.map((day, i) => {
                                        const dayLabel = DAYS_OF_WEEK[(startDay + i) % 7]?.label || '';
                                        const isRest = day.toLowerCase() === 'descanso';
                                        return (
                                            <div key={i} className="flex-1 text-center">
                                                <div className="text-[9px] font-bold text-zinc-400 mb-2">{dayLabel.slice(0, 3)}</div>
                                                <div className={`h-2 rounded-full transition-all ${isRest ? 'bg-zinc-100' : 'bg-gradient-to-r from-purple-500 to-purple-600'}`} />
                                                <div className={`text-[8px] mt-1.5 font-black truncate ${isRest ? 'text-zinc-400' : 'text-zinc-700'}`}>
                                                    {isRest ? '—' : day.split(' ')[0]}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Start day */}
                        <div className="bg-gradient-to-br from-zinc-50 to-white rounded-[20px] p-4 border border-zinc-200/60">
                            <div className="flex items-center justify-between">
                                <div>
                                    <span className="text-sm font-black text-zinc-900 block">Día de Inicio</span>
                                    <span className="text-[9px] text-zinc-500 uppercase tracking-wider">Define el primer día</span>
                                </div>
                                <div className="relative">
                                    <select
                                        value={startDay}
                                        onChange={e => setStartDay(parseInt(e.target.value))}
                                        className="appearance-none bg-white border border-zinc-200 rounded-xl px-4 py-2.5 pr-9 text-sm font-bold text-zinc-700 focus:border-purple-400 outline-none transition-colors"
                                    >
                                        {DAYS_OF_WEEK.map(d => (
                                            <option key={d.value} value={d.value}>{d.label}</option>
                                        ))}
                                    </select>
                                    <ChevronDownIcon size={14} className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-zinc-400" />
                                </div>
                            </div>
                        </div>

                        {/* Scope */}
                        <div>
                            <span className="text-[9px] font-black uppercase tracking-[0.2em] text-zinc-400 block mb-3 pl-2">Alcance</span>
                            <div className="space-y-2">
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('week')}
                                        className={`w-full text-left p-4 rounded-[20px] border transition-all backdrop-blur-sm ${scope === 'week'
                                            ? 'bg-gradient-to-br from-purple-50 to-white border-purple-300 shadow-lg shadow-purple-500/10'
                                            : 'bg-white/60 backdrop-blur-sm border-zinc-200/60 hover:bg-white hover:border-purple-200'
                                            }`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-all ${scope === 'week' ? 'border-purple-500 bg-purple-500' : 'border-zinc-300'}`}>
                                                {scope === 'week' && <CheckIcon size={12} className="text-white" />}
                                            </div>
                                            <div>
                                                <span className={`text-sm font-black block ${scope === 'week' ? 'text-purple-900' : 'text-zinc-700'}`}>Solo esta semana</span>
                                                <p className={`text-[9px] ${scope === 'week' ? 'text-purple-700/70' : 'text-zinc-500'}`}>Las demás semanas no se ven afectadas</p>
                                            </div>
                                        </div>
                                    </button>
                                )}
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('block')}
                                        className={`w-full text-left p-4 rounded-[20px] border transition-all backdrop-blur-sm ${scope === 'block'
                                            ? 'bg-gradient-to-br from-purple-50 to-white border-purple-300 shadow-lg shadow-purple-500/10'
                                            : 'bg-white/60 backdrop-blur-sm border-zinc-200/60 hover:bg-white hover:border-purple-200'
                                            }`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-all ${scope === 'block' ? 'border-purple-500 bg-purple-500' : 'border-zinc-300'}`}>
                                                {scope === 'block' && <CheckIcon size={12} className="text-white" />}
                                            </div>
                                            <div>
                                                <span className={`text-sm font-black block ${scope === 'block' ? 'text-purple-900' : 'text-zinc-700'}`}>Todo el bloque</span>
                                                <p className={`text-[9px] ${scope === 'block' ? 'text-purple-700/70' : 'text-zinc-500'}`}>Afecta todas las semanas del bloque</p>
                                            </div>
                                        </div>
                                    </button>
                                )}
                                <button
                                    onClick={() => setScope('program')}
                                    className={`w-full text-left p-4 rounded-[20px] border transition-all backdrop-blur-sm ${scope === 'program'
                                        ? 'bg-gradient-to-br from-purple-50 to-white border-purple-300 shadow-lg shadow-purple-500/10'
                                        : 'bg-white/60 backdrop-blur-sm border-zinc-200/60 hover:bg-white hover:border-purple-200'
                                        }`}
                                >
                                    <div className="flex items-center gap-3">
                                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 transition-all ${scope === 'program' ? 'border-purple-500 bg-purple-500' : 'border-zinc-300'}`}>
                                            {scope === 'program' && <CheckIcon size={12} className="text-white" />}
                                        </div>
                                        <div>
                                            <span className={`text-sm font-black block ${scope === 'program' ? 'text-purple-900' : 'text-zinc-700'}`}>{isSimpleProgram ? 'Todo el ciclo' : 'Todo el programa'}</span>
                                            <p className={`text-[9px] ${scope === 'program' ? 'text-purple-700/70' : 'text-zinc-500'}`}>Reescribe toda la estructura</p>
                                        </div>
                                    </div>
                                </button>
                            </div>
                        </div>

                        {/* Preserve exercises */}
                        <div className="bg-gradient-to-br from-amber-50/80 to-orange-50/80 rounded-[20px] p-4 border border-amber-200/60 backdrop-blur-sm">
                            <div className="flex items-start gap-3">
                                <div className="w-9 h-9 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center shrink-0">
                                    <AlertTriangleIcon size={18} />
                                </div>
                                <div className="flex-1">
                                    <span className="text-sm font-black text-zinc-900 block mb-3">Ejercicios</span>
                                    <div className="space-y-2">
                                        <button
                                            onClick={() => setPreserveExercises(true)}
                                            className={`w-full text-left p-3 rounded-xl border transition-all ${preserveExercises
                                                ? 'bg-white border-purple-300 shadow-md'
                                                : 'bg-white/60 border-zinc-200 hover:border-purple-200'
                                                }`}
                                        >
                                            <span className={`text-[9px] font-black block ${preserveExercises ? 'text-purple-900' : 'text-zinc-700'}`}>Preservar existentes</span>
                                            <p className={`text-[9px] mt-0.5 ${preserveExercises ? 'text-purple-700/70' : 'text-zinc-500'}`}>Reasigna ejercicios al nuevo split</p>
                                        </button>
                                        <button
                                            onClick={() => setPreserveExercises(false)}
                                            className={`w-full text-left p-3 rounded-xl border transition-all ${!preserveExercises
                                                ? 'bg-white border-red-300 shadow-md'
                                                : 'bg-white/60 border-zinc-200 hover:border-red-200'
                                                }`}
                                        >
                                            <span className={`text-[9px] font-black block ${!preserveExercises ? 'text-red-900' : 'text-zinc-700'}`}>Empezar en blanco</span>
                                            <p className={`text-[9px] mt-0.5 ${!preserveExercises ? 'text-red-700/70' : 'text-zinc-500'}`}>Borra todos los ejercicios</p>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Footer */}
                {step === 'scope' && (
                    <div className={`p-4 ${isInline ? '' : 'pb-[calc(1rem+env(safe-area-inset-bottom))]'} bg-white/80 backdrop-blur-xl border-t border-zinc-100 flex gap-3 shrink-0 ${isInline ? 'rounded-b-[20px]' : ''}`}>
                        <button
                            onClick={handleBack}
                            className="flex-1 py-3.5 px-6 rounded-full border-2 border-zinc-200 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-600 hover:bg-zinc-50 transition-all"
                        >
                            Atrás
                        </button>
                        <button
                            onClick={handleApply}
                            className="flex-1 py-3.5 px-6 rounded-full bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg shadow-purple-500/30"
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
export { SplitChangerDrawer };
