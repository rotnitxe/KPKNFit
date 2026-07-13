import React from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, DumbbellIcon, ActivityIcon } from '../icons';
import { SPLIT_TEMPLATES } from '../../data/splitTemplates';

const DAYS_OF_WEEK = [
    { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
    { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
    { label: 'Sábado', value: 6 },
];

interface DetailsSectionProps {
    program: Program;
    onUpdateField: (field: string, value: any) => void;
    onOpenSplitChanger: () => void;
}

const DetailsSection: React.FC<DetailsSectionProps> = ({
    program, onUpdateField, onOpenSplitChanger,
}) => {
    const currentSplit = SPLIT_TEMPLATES.find(s => s.id === program.selectedSplitId);

    return (
        <div className="space-y-6 p-4 rounded-3xl">
            <h3 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-[0.2em] flex items-center gap-2">
                <DumbbellIcon size={16} className="text-[var(--md-sys-color-primary)]" /> Detalles del Programa
            </h3>

            {/* Name */}
            <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 shadow-sm">
                <label className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest block mb-1">Nombre del Programa</label>
                <input
                    type="text"
                    value={program.name || ''}
                    onChange={e => onUpdateField('name', e.target.value)}
                    className="w-full bg-transparent text-headline-small font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tighter focus:ring-0 border-none p-0 placeholder-[var(--md-sys-color-outline-variant)]"
                    placeholder="PROGRAMA SIN NOMBRE"
                />
            </div>

            {/* Description */}
            <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 shadow-sm">
                <label className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest block mb-1">Descripción</label>
                <textarea
                    value={program.description || ''}
                    onChange={e => onUpdateField('description', e.target.value)}
                    rows={3}
                    className="w-full bg-transparent text-body-medium font-medium text-[var(--md-sys-color-on-surface-variant)] resize-none focus:ring-0 border-none p-0 placeholder-[var(--md-sys-color-outline-variant)]"
                    placeholder="Objetivos y metodología..."
                />
            </div>

            {/* Grid: mode + startDay */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 shadow-sm">
                    <label className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest block mb-2">Modalidad</label>
                    <div className="relative">
                        <select
                            value={program.mode || 'hypertrophy'}
                            onChange={e => onUpdateField('mode', e.target.value)}
                            className="appearance-none w-full bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] px-4 py-3 rounded-xl text-label-large font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface)] focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] transition-all outline-none"
                        >
                            <option value="hypertrophy">Hipertrofia</option>
                            <option value="powerlifting">Powerlifting</option>
                            <option value="powerbuilding">Powerbuilding</option>
                        </select>
                        <ChevronDownIcon size={14} className="absolute right-4 top-1/2 -translate-y-1/2 pointer-events-none text-[var(--md-sys-color-on-surface-variant)]" />
                    </div>
                </div>
                <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 shadow-sm">
                    <label className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest block mb-2">Inicio de Semana</label>
                    <div className="relative">
                        <select
                            value={program.startDay ?? 1}
                            onChange={e => onUpdateField('startDay', parseInt(e.target.value))}
                            className="appearance-none w-full bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] px-4 py-3 rounded-xl text-label-large font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface)] focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] transition-all outline-none"
                        >
                            {DAYS_OF_WEEK.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}
                        </select>
                        <ChevronDownIcon size={14} className="absolute right-4 top-1/2 -translate-y-1/2 pointer-events-none text-[var(--md-sys-color-on-surface-variant)]" />
                    </div>
                </div>
            </div>

            {/* Current split */}
            <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 shadow-sm">
                <div className="flex items-center justify-between mb-4">
                    <label className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Organización de Días (Split)</label>
                    <button
                        onClick={onOpenSplitChanger}
                        className="text-label-small font-black text-[var(--md-sys-color-primary)] uppercase tracking-widest hover:brightness-110 transition-colors"
                    >
                        Cambiar Split
                    </button>
                </div>
                {currentSplit ? (
                    <div className="bg-[var(--md-sys-color-surface-container-high)] rounded-xl p-4 border border-[var(--md-sys-color-outline-variant)]">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="w-10 h-10 rounded-full bg-[var(--md-sys-color-primary-container)] flex items-center justify-center text-[var(--md-sys-color-on-primary-container)]">
                                <ActivityIcon size={20} />
                            </div>
                            <span className="text-label-large font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-wider">{currentSplit.name}</span>
                        </div>
                        <div className="flex gap-1.5 h-2">
                            {currentSplit.pattern.map((day, i) => (
                                <div
                                    key={i}
                                    className={`flex-1 rounded-full ${day.toLowerCase() === 'descanso' ? 'bg-[var(--md-sys-color-outline-variant)]' : 'bg-[var(--md-sys-color-primary)] shadow-[0_0_8px_rgba(var(--md-sys-color-primary-rgb),0.3)]'}`}
                                    title={day}
                                />
                            ))}
                        </div>
                    </div>
                ) : (
                    <div className="py-8 text-center border-2 border-dashed border-[var(--md-sys-color-outline-variant)] rounded-xl bg-[var(--md-sys-color-surface-container-lowest)]">
                        <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)]/40 uppercase tracking-widest">No se ha definido un split</span>
                    </div>
                )}
            </div>
        </div>
    );
};

export default DetailsSection;
