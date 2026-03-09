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
            {/* Backdrop M3 */}
            <div className={`fixed inset-0 z-[101] bg-black/20 backdrop-blur-sm transition-opacity ${isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'}`} onClick={onClose} />

            {/* M3 Bottom Sheet Container */}
            <div className={`fixed bottom-0 left-0 right-0 z-[102] w-full max-h-[90vh] bg-white/70 backdrop-blur-3xl rounded-t-[32px] flex flex-col transition-transform duration-500 ease-[cubic-bezier(0.32,0.72,0,1)] shadow-2xl ${isOpen ? 'translate-y-0' : 'translate-y-full'}`}>
                {/* Drag Handle Indicator */}
                <div className="w-full flex justify-center pt-4 pb-2 shrink-0">
                    <div className="w-8 h-1 rounded-full bg-black/10 opacity-80" />
                </div>

                {/* Header M3 */}
                <div className="px-6 py-2 flex items-center justify-between shrink-0">
                    <div>
                        <h2 className="text-[14px] font-black uppercase tracking-[0.2em] text-[#1D1B20]">
                            {step === 'gallery' ? 'Galería de Splits' : 'Configurar'}
                        </h2>
                        <p className="text-[9px] font-black uppercase tracking-widest text-zinc-400 mt-0.5">
                            {step === 'gallery' ? 'Selecciona una arquitectura base' : selectedSplit?.name}
                        </p>
                    </div>
                    <button onClick={onClose} aria-label="Cerrar" className="w-10 h-10 rounded-full flex items-center justify-center text-zinc-500 hover:bg-black/5 transition-colors">
                        <XIcon size={20} />
                    </button>
                </div>

                {step === 'gallery' ? (
                    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
                        {/* Search M3 */}
                        <div className="px-6 py-3 shrink-0">
                            <div className="relative">
                                <SearchIcon size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-zinc-400" />
                                <input
                                    type="text"
                                    value={search}
                                    onChange={e => setSearch(e.target.value)}
                                    placeholder="BUSCAR ARQUITECTURA..."
                                    className="w-full bg-black/[0.03] rounded-[20px] pl-12 pr-4 py-3.5 text-[11px] font-black uppercase tracking-widest text-[#1D1B20] placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 transition-all"
                                />
                            </div>
                        </div>

                        {/* Filter tags M3 */}
                        <div className="px-6 py-2 shrink-0">
                            <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar -mx-2 px-2">
                                {FILTER_TAGS.map(tag => (
                                    <button
                                        key={tag}
                                        onClick={() => setFilter(tag)}
                                        className={`px-4 py-1.5 rounded-xl border shrink-0 whitespace-nowrap text-[9px] font-black uppercase tracking-[0.15em] transition-all ${filter === tag
                                            ? 'bg-black text-white border-transparent shadow-lg'
                                            : 'bg-white/40 border-black/[0.05] text-zinc-400 hover:text-black hover:bg-white/60'
                                            }`}
                                    >
                                        {tag}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Split list M3 */}
                        <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar px-6 py-2 space-y-3 pb-[max(120px,calc(80px+env(safe-area-inset-bottom)))]">
                            {filteredSplits.map(split => {
                                const isCurrent = split.id === currentSplitId;
                                const trainingDays = split.pattern.filter(d => d.toLowerCase() !== 'descanso').length;
                                return (
                                    <button
                                        key={split.id}
                                        onClick={() => handleSelectSplit(split)}
                                        className={`w-full text-left p-4 rounded-2xl border transition-all ${isCurrent
                                            ? 'bg-blue-600 border-transparent shadow-xl shadow-blue-500/10'
                                            : 'bg-white/40 border-black/[0.03] hover:bg-white/60 hover:border-black/5'
                                            }`}
                                    >
                                        <div className="flex items-start justify-between gap-3 mb-2">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 flex-wrap mb-1">
                                                    <span className={`text-[12px] font-black uppercase tracking-tight truncate ${isCurrent ? 'text-white' : 'text-[#1D1B20]'}`}>
                                                        {split.name}
                                                    </span>
                                                    {isCurrent && (
                                                        <span className="text-[8px] font-black uppercase tracking-widest px-2 py-0.5 rounded-md bg-white/20 text-white shrink-0">
                                                            ACTUAL
                                                        </span>
                                                    )}
                                                </div>
                                                <p className={`text-[10px] font-medium leading-relaxed line-clamp-2 ${isCurrent ? 'text-white/70' : 'text-zinc-500'}`}>
                                                    {split.description}
                                                </p>
                                            </div>
                                            <span className={`text-[12px] font-black shrink-0 ${isCurrent ? 'text-white' : 'text-zinc-300'}`}>
                                                {trainingDays}D
                                            </span>
                                        </div>
                                        <div className="flex gap-1 mt-3">
                                            {split.pattern.map((day, i) => (
                                                <div
                                                    key={i}
                                                    className={`flex-1 h-1.5 rounded-full ${day.toLowerCase() === 'descanso'
                                                        ? 'bg-black/5'
                                                        : isCurrent ? 'bg-white/60' : 'bg-blue-500/60'
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
                    <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar px-6 py-4 space-y-6">
                        {/* Selected split preview M3 */}
                        {selectedSplit && (
                            <div className="bg-black/5 rounded-[24px] p-4">
                                <div className="flex items-center justify-between mb-4">
                                    <span className="text-[14px] font-black uppercase tracking-tight text-black">{selectedSplit.name}</span>
                                    <button onClick={handleBack} className="text-[10px] font-black uppercase tracking-widest text-blue-500 hover:text-blue-600 transition-colors">
                                        Cambiar
                                    </button>
                                </div>
                                <div className="flex gap-1">
                                    {selectedSplit.pattern.map((day, i) => {
                                        const dayLabel = DAYS_OF_WEEK[(startDay + i) % 7]?.label || '';
                                        const isRest = day.toLowerCase() === 'descanso';
                                        return (
                                            <div key={i} className="flex-1 text-center">
                                                <div className="text-[8px] font-black uppercase tracking-widest text-zinc-400 mb-2">{dayLabel.slice(0, 3)}</div>
                                                <div className={`h-1.5 rounded-full ${isRest ? 'bg-black/5' : 'bg-blue-500'}`} />
                                                <div className={`text-[9px] mt-1 font-black truncate uppercase ${isRest ? 'text-zinc-300' : 'text-black'}`}>
                                                    {isRest ? '-' : day}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Start day M3 */}
                        <div className="flex items-center justify-between bg-black/5 rounded-[24px] p-4">
                            <div>
                                <span className="text-[12px] font-black text-black block uppercase tracking-tight">Día de Inicio</span>
                                <span className="text-[9px] font-black uppercase tracking-widest text-zinc-400">Define el primer día de tu semana</span>
                            </div>
                            <div className="relative">
                                <select
                                    value={startDay}
                                    onChange={e => setStartDay(parseInt(e.target.value))}
                                    className="appearance-none bg-white border border-black/5 rounded-xl px-4 py-2 pr-8 text-[11px] font-black uppercase tracking-widest text-black focus:border-blue-500 outline-none"
                                >
                                    {DAYS_OF_WEEK.map(d => (
                                        <option key={d.value} value={d.value}>{d.label}</option>
                                    ))}
                                </select>
                                <ChevronDownIcon size={16} className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-zinc-400" />
                            </div>
                        </div>

                        {/* Scope M3 */}
                        <div>
                            <span className="text-[10px] font-black text-black/40 block mb-3 pl-2 uppercase tracking-[0.2em]">Alcance del cambio</span>
                            <div className="space-y-2">
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('week')}
                                        className={`w-full text-left p-4 rounded-[24px] border transition-all flex items-center gap-4 ${scope === 'week'
                                            ? 'bg-blue-50 border-blue-100 shadow-sm'
                                            : 'bg-black/[0.02] border-transparent hover:bg-black/[0.04]'
                                            }`}
                                    >
                                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'week' ? 'border-blue-500' : 'border-zinc-300'}`}>
                                            {scope === 'week' && <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />}
                                        </div>
                                        <div>
                                            <span className={`text-[12px] font-black block uppercase tracking-tight ${scope === 'week' ? 'text-blue-900' : 'text-black'}`}>Solo esta semana</span>
                                            <p className={`text-[9px] font-black uppercase tracking-widest line-clamp-1 ${scope === 'week' ? 'text-blue-500' : 'text-zinc-400'}`}>Las demás semanas no se verán afectadas</p>
                                        </div>
                                    </button>
                                )}
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('block')}
                                        className={`w-full text-left p-4 rounded-[24px] border transition-all flex items-center gap-4 ${scope === 'block'
                                            ? 'bg-blue-50 border-blue-100 shadow-sm'
                                            : 'bg-black/[0.02] border-transparent hover:bg-black/[0.04]'
                                            }`}
                                    >
                                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'block' ? 'border-blue-500' : 'border-zinc-300'}`}>
                                            {scope === 'block' && <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />}
                                        </div>
                                        <div>
                                            <span className={`text-[12px] font-black block uppercase tracking-tight ${scope === 'block' ? 'text-blue-900' : 'text-black'}`}>Todo el bloque</span>
                                            <p className={`text-[9px] font-black uppercase tracking-widest line-clamp-1 ${scope === 'block' ? 'text-blue-500' : 'text-zinc-400'}`}>Afectar todas las semanas del bloque actual</p>
                                        </div>
                                    </button>
                                )}
                                <button
                                    onClick={() => setScope('program')}
                                    className={`w-full text-left p-4 rounded-[24px] border transition-all flex items-center gap-4 ${scope === 'program'
                                        ? 'bg-blue-50 border-blue-100 shadow-sm'
                                        : 'bg-black/[0.02] border-transparent hover:bg-black/[0.04]'
                                        }`}
                                >
                                    <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'program' ? 'border-blue-500' : 'border-zinc-300'}`}>
                                        {scope === 'program' && <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />}
                                    </div>
                                    <div>
                                        <span className={`text-[12px] font-black block uppercase tracking-tight ${scope === 'program' ? 'text-blue-900' : 'text-black'}`}>{isSimpleProgram ? 'Todo el ciclo' : 'Todo el programa'}</span>
                                        <p className={`text-[9px] font-black uppercase tracking-widest line-clamp-1 ${scope === 'program' ? 'text-blue-500' : 'text-zinc-400'}`}>Reescribir toda la estructura base</p>
                                    </div>
                                </button>
                            </div>
                        </div>

                        {/* Preserve exercises M3 */}
                        <div className="bg-black/5 rounded-[24px] p-4">
                            <div className="flex items-start gap-4">
                                <div className="w-10 h-10 rounded-full bg-red-50 text-red-500 flex items-center justify-center shrink-0">
                                    <AlertTriangleIcon size={20} />
                                </div>
                                <div className="flex-1">
                                    <span className="text-[12px] font-black text-black block mb-3 uppercase tracking-tight">Manejo de Ejercicios</span>
                                    <div className="space-y-2">
                                        <button
                                            onClick={() => setPreserveExercises(true)}
                                            className={`w-full text-left p-3 rounded-xl border transition-all ${preserveExercises
                                                ? 'bg-white border-black/5 shadow-sm'
                                                : 'bg-transparent border-transparent text-zinc-400'
                                                }`}
                                        >
                                            <span className={`text-[11px] font-black block uppercase tracking-tight ${preserveExercises ? 'text-black' : 'text-zinc-400'}`}>Preservar existentes</span>
                                            <p className="text-[9px] font-black uppercase tracking-widest mt-0.5 opacity-40 text-black">Intentará reasignar los ejercicios actuales al nuevo split.</p>
                                        </button>
                                        <button
                                            onClick={() => setPreserveExercises(false)}
                                            className={`w-full text-left p-3 rounded-xl border transition-all ${!preserveExercises
                                                ? 'bg-white border-black/5 shadow-sm'
                                                : 'bg-transparent border-transparent text-zinc-400'
                                                }`}
                                        >
                                            <span className={`text-[11px] font-black block uppercase tracking-tight ${!preserveExercises ? 'text-red-500' : 'text-zinc-400'}`}>Empezar en blanco</span>
                                            <p className="text-[9px] font-black uppercase tracking-widest mt-0.5 opacity-40 text-black">Se borrarán todos los ejercicios programados.</p>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Footer M3 */}
                {step === 'scope' && (
                    <div className="p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] bg-white flex gap-3 shrink-0">
                        <button
                            onClick={handleBack}
                            className="flex-1 py-4 px-6 rounded-2xl border border-black/5 text-[10px] font-black uppercase tracking-widest text-zinc-400 hover:bg-black/5 transition-colors"
                        >
                            Atrás
                        </button>
                        <button
                            onClick={handleApply}
                            className="flex-1 py-4 px-6 rounded-2xl bg-black text-white text-[10px] font-black uppercase tracking-widest hover:brightness-110 transition-colors shadow-lg shadow-black/10"
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
