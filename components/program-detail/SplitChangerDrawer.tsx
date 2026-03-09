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
            <div className={`fixed inset-0 z-[101] bg-[var(--md-sys-color-scrim)] backdrop-blur-sm transition-opacity ${isOpen ? 'opacity-50' : 'opacity-0 pointer-events-none'}`} onClick={onClose} />

            {/* M3 Bottom Sheet Container */}
            <div className={`fixed bottom-0 left-0 right-0 z-[102] w-full max-h-[90vh] bg-[var(--md-sys-color-surface-container-low)] rounded-t-[28px] flex flex-col transition-transform duration-300 ease-out shadow-2xl ${isOpen ? 'translate-y-0' : 'translate-y-full'}`}>
                {/* Drag Handle Indicator */}
                <div className="w-full flex justify-center pt-4 pb-2 shrink-0">
                    <div className="w-8 h-1 rounded-full bg-[var(--md-sys-color-outline-variant)] opacity-80" />
                </div>

                {/* Header M3 */}
                <div className="px-6 py-2 flex items-center justify-between shrink-0">
                    <div>
                        <h2 className="text-title-lg font-bold text-[var(--md-sys-color-on-surface)]">
                            {step === 'gallery' ? 'Cambiar Split' : 'Configurar Split'}
                        </h2>
                        <p className="text-body-md text-[var(--md-sys-color-on-surface-variant)] mt-0.5">
                            {step === 'gallery' ? 'Selecciona una plantilla base' : selectedSplit?.name}
                        </p>
                    </div>
                    <button onClick={onClose} aria-label="Cerrar" className="w-10 h-10 rounded-full flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)] transition-colors">
                        <XIcon size={20} />
                    </button>
                </div>

                {step === 'gallery' ? (
                    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
                        {/* Search M3 */}
                        <div className="px-6 py-3 shrink-0">
                            <div className="relative">
                                <SearchIcon size={20} className="absolute left-4 top-1/2 -translate-y-1/2 text-[var(--md-sys-color-on-surface-variant)]" />
                                <input
                                    type="text"
                                    value={search}
                                    onChange={e => setSearch(e.target.value)}
                                    placeholder="Buscar split..."
                                    className="w-full bg-[var(--md-sys-color-surface-container-high)] rounded-[28px] pl-12 pr-4 py-3.5 text-body-lg text-[var(--md-sys-color-on-surface)] placeholder-[var(--md-sys-color-on-surface-variant)] focus:outline-none focus:ring-2 focus:ring-[var(--md-sys-color-primary)] transition-shadow"
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
                                        className={`px-4 py-1.5 rounded-lg border shrink-0 whitespace-nowrap text-label-lg font-medium transition-all ${filter === tag
                                            ? 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)] border-transparent'
                                            : 'bg-transparent text-[var(--md-sys-color-on-surface-variant)] border-[var(--md-sys-color-outline)] hover:bg-[var(--md-sys-color-surface-variant)] hover:text-[var(--md-sys-color-on-surface)]'
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
                                        className={`w-full text-left p-4 rounded-xl border transition-all ${isCurrent
                                            ? 'bg-[var(--md-sys-color-primary-container)] border-[var(--md-sys-color-primary)]'
                                            : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] hover:bg-[var(--md-sys-color-surface-variant)]'
                                            }`}
                                    >
                                        <div className="flex items-start justify-between gap-3 mb-2">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 flex-wrap mb-1">
                                                    <span className={`text-title-md font-bold truncate ${isCurrent ? 'text-[var(--md-sys-color-on-primary-container)]' : 'text-[var(--md-sys-color-on-surface)]'}`}>
                                                        {split.name}
                                                    </span>
                                                    {isCurrent && (
                                                        <span className="text-label-sm font-bold uppercase px-2 py-0.5 rounded-md bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] shrink-0">
                                                            ACTUAL
                                                        </span>
                                                    )}
                                                </div>
                                                <p className={`text-body-sm line-clamp-2 ${isCurrent ? 'text-[var(--md-sys-color-on-primary-container)]/80' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>
                                                    {split.description}
                                                </p>
                                            </div>
                                            <span className={`text-title-md font-bold shrink-0 ${isCurrent ? 'text-[var(--md-sys-color-on-primary-container)]' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>
                                                {trainingDays}d
                                            </span>
                                        </div>
                                        <div className="flex gap-1 mt-3">
                                            {split.pattern.map((day, i) => (
                                                <div
                                                    key={i}
                                                    className={`flex-1 h-1.5 rounded-full ${day.toLowerCase() === 'descanso'
                                                        ? 'bg-[var(--md-sys-color-surface-container-highest)]'
                                                        : isCurrent ? 'bg-[var(--md-sys-color-primary)]' : 'bg-[var(--md-sys-color-primary)]/60'
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
                            <div className="bg-[var(--md-sys-color-surface-container)] rounded-[16px] p-4">
                                <div className="flex items-center justify-between mb-4">
                                    <span className="text-title-md font-bold text-[var(--md-sys-color-on-surface)]">{selectedSplit.name}</span>
                                    <button onClick={handleBack} className="text-label-lg font-bold text-[var(--md-sys-color-primary)] hover:text-[var(--md-sys-color-primary)]/80 transition-colors">
                                        Cambiar
                                    </button>
                                </div>
                                <div className="flex gap-1">
                                    {selectedSplit.pattern.map((day, i) => {
                                        const dayLabel = DAYS_OF_WEEK[(startDay + i) % 7]?.label || '';
                                        const isRest = day.toLowerCase() === 'descanso';
                                        return (
                                            <div key={i} className="flex-1 text-center">
                                                <div className="text-body-sm font-medium text-[var(--md-sys-color-on-surface-variant)] mb-2">{dayLabel.slice(0, 3)}</div>
                                                <div className={`h-1.5 rounded-full ${isRest ? 'bg-[var(--md-sys-color-surface-container-highest)]' : 'bg-[var(--md-sys-color-primary)]'}`} />
                                                <div className={`text-label-sm mt-1 font-bold truncate ${isRest ? 'text-[var(--md-sys-color-on-surface-variant)]/60' : 'text-[var(--md-sys-color-on-surface)]'}`}>
                                                    {isRest ? '-' : day}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Start day M3 */}
                        <div className="flex items-center justify-between bg-[var(--md-sys-color-surface-container)] rounded-[16px] p-4">
                            <div>
                                <span className="text-title-sm font-bold text-[var(--md-sys-color-on-surface)] block">Día de Inicio</span>
                                <span className="text-body-sm text-[var(--md-sys-color-on-surface-variant)]">Define el primer día de tu semana</span>
                            </div>
                            <div className="relative">
                                <select
                                    value={startDay}
                                    onChange={e => setStartDay(parseInt(e.target.value))}
                                    className="appearance-none bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-lg px-4 py-2 pr-8 text-body-md font-medium text-[var(--md-sys-color-on-surface)] focus:border-[var(--md-sys-color-primary)] outline-none"
                                >
                                    {DAYS_OF_WEEK.map(d => (
                                        <option key={d.value} value={d.value}>{d.label}</option>
                                    ))}
                                </select>
                                <ChevronDownIcon size={16} className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-[var(--md-sys-color-on-surface-variant)]" />
                            </div>
                        </div>

                        {/* Scope M3 */}
                        <div>
                            <span className="text-title-sm font-bold text-[var(--md-sys-color-on-surface)] block mb-3 pl-2">Alcance del cambio</span>
                            <div className="space-y-2">
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('week')}
                                        className={`w-full text-left p-4 rounded-[16px] border transition-all flex items-center gap-4 ${scope === 'week'
                                            ? 'bg-[var(--md-sys-color-secondary-container)] border-transparent'
                                            : 'bg-[var(--md-sys-color-surface-container)] border-transparent hover:bg-[var(--md-sys-color-surface-container-high)]'
                                            }`}
                                    >
                                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'week' ? 'border-[var(--md-sys-color-primary)]' : 'border-[var(--md-sys-color-on-surface-variant)]'}`}>
                                            {scope === 'week' && <div className="w-2.5 h-2.5 rounded-full bg-[var(--md-sys-color-primary)]" />}
                                        </div>
                                        <div>
                                            <span className={`text-title-sm font-bold block ${scope === 'week' ? 'text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface)]'}`}>Solo esta semana</span>
                                            <p className={`text-body-sm line-clamp-1 ${scope === 'week' ? 'text-[var(--md-sys-color-on-secondary-container)]/80' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>Las demás semanas no se verán afectadas</p>
                                        </div>
                                    </button>
                                )}
                                {!isSimpleProgram && (
                                    <button
                                        onClick={() => setScope('block')}
                                        className={`w-full text-left p-4 rounded-[16px] border transition-all flex items-center gap-4 ${scope === 'block'
                                            ? 'bg-[var(--md-sys-color-secondary-container)] border-transparent'
                                            : 'bg-[var(--md-sys-color-surface-container)] border-transparent hover:bg-[var(--md-sys-color-surface-container-high)]'
                                            }`}
                                    >
                                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'block' ? 'border-[var(--md-sys-color-primary)]' : 'border-[var(--md-sys-color-on-surface-variant)]'}`}>
                                            {scope === 'block' && <div className="w-2.5 h-2.5 rounded-full bg-[var(--md-sys-color-primary)]" />}
                                        </div>
                                        <div>
                                            <span className={`text-title-sm font-bold block ${scope === 'block' ? 'text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface)]'}`}>Todo el bloque</span>
                                            <p className={`text-body-sm line-clamp-1 ${scope === 'block' ? 'text-[var(--md-sys-color-on-secondary-container)]/80' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>Afectar todas las semanas del bloque actual</p>
                                        </div>
                                    </button>
                                )}
                                <button
                                    onClick={() => setScope('program')}
                                    className={`w-full text-left p-4 rounded-[16px] border transition-all flex items-center gap-4 ${scope === 'program'
                                        ? 'bg-[var(--md-sys-color-secondary-container)] border-transparent'
                                        : 'bg-[var(--md-sys-color-surface-container)] border-transparent hover:bg-[var(--md-sys-color-surface-container-high)]'
                                        }`}
                                >
                                    <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${scope === 'program' ? 'border-[var(--md-sys-color-primary)]' : 'border-[var(--md-sys-color-on-surface-variant)]'}`}>
                                        {scope === 'program' && <div className="w-2.5 h-2.5 rounded-full bg-[var(--md-sys-color-primary)]" />}
                                    </div>
                                    <div>
                                        <span className={`text-title-sm font-bold block ${scope === 'program' ? 'text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface)]'}`}>{isSimpleProgram ? 'Todo el ciclo' : 'Todo el programa'}</span>
                                        <p className={`text-body-sm line-clamp-1 ${scope === 'program' ? 'text-[var(--md-sys-color-on-secondary-container)]/80' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>Reescribir toda la estructura base</p>
                                    </div>
                                </button>
                            </div>
                        </div>

                        {/* Preserve exercises M3 */}
                        <div className="bg-[var(--md-sys-color-surface-container)] rounded-[16px] p-4">
                            <div className="flex items-start gap-4">
                                <div className="w-10 h-10 rounded-full bg-[var(--md-sys-color-error-container)]/50 text-[var(--md-sys-color-error)] flex items-center justify-center shrink-0">
                                    <AlertTriangleIcon size={20} />
                                </div>
                                <div className="flex-1">
                                    <span className="text-title-sm font-bold text-[var(--md-sys-color-on-surface)] block mb-3">Manejo de Ejercicios</span>
                                    <div className="space-y-2">
                                        <button
                                            onClick={() => setPreserveExercises(true)}
                                            className={`w-full text-left p-3 rounded-lg border transition-all ${preserveExercises
                                                ? 'bg-[var(--md-sys-color-secondary-container)] border-transparent'
                                                : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)]'
                                                }`}
                                        >
                                            <span className={`text-label-lg font-bold block ${preserveExercises ? 'text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface)]'}`}>Preservar existentes</span>
                                            <p className={`text-body-sm mt-0.5 ${preserveExercises ? 'text-[var(--md-sys-color-on-secondary-container)]/80' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>Intentará reasignar los ejercicios actuales al nuevo split.</p>
                                        </button>
                                        <button
                                            onClick={() => setPreserveExercises(false)}
                                            className={`w-full text-left p-3 rounded-lg border transition-all ${!preserveExercises
                                                ? 'bg-[var(--md-sys-color-error-container)] border-transparent'
                                                : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)]'
                                                }`}
                                        >
                                            <span className={`text-label-lg font-bold block ${!preserveExercises ? 'text-[var(--md-sys-color-on-error-container)]' : 'text-[var(--md-sys-color-on-surface)]'}`}>Empezar en blanco</span>
                                            <p className={`text-body-sm mt-0.5 ${!preserveExercises ? 'text-[var(--md-sys-color-on-error-container)]/80' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}>Se borrarán todos los ejercicios programados.</p>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Footer M3 */}
                {step === 'scope' && (
                    <div className="p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] bg-[var(--md-sys-color-surface-container-low)] flex gap-3 shrink-0">
                        <button
                            onClick={handleBack}
                            className="flex-1 py-3 px-6 rounded-full border border-[var(--md-sys-color-outline)] text-label-large font-bold text-[var(--md-sys-color-primary)] hover:bg-[var(--md-sys-color-primary)]/10 transition-colors"
                        >
                            Atrás
                        </button>
                        <button
                            onClick={handleApply}
                            className="flex-1 py-3 px-6 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] text-label-large font-bold hover:brightness-110 transition-colors shadow-sm"
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
