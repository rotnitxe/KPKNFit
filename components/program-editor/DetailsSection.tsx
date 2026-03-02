import React from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, DumbbellIcon } from '../icons';
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
        <div className="space-y-4">
            <h3 className="text-xs font-medium text-white flex items-center gap-2">
                <DumbbellIcon size={14} className="text-[#a3a3a3]" /> Detalles del Programa
            </h3>

            {/* Name */}
            <div className="bg-[#252525] border border-[#3f3f3f] p-3">
                <label className="text-[8px] font-medium text-[#737373] uppercase tracking-widest block mb-1.5">Nombre</label>
                <input
                    type="text"
                    value={program.name || ''}
                    onChange={e => onUpdateField('name', e.target.value)}
                    className="w-full bg-transparent text-sm font-medium text-white focus:ring-0 border-none p-0"
                    placeholder="Nombre del programa"
                />
            </div>

            {/* Description */}
            <div className="bg-[#252525] border border-[#3f3f3f] p-3">
                <label className="text-[8px] font-medium text-[#737373] uppercase tracking-widest block mb-1.5">Descripción</label>
                <textarea
                    value={program.description || ''}
                    onChange={e => onUpdateField('description', e.target.value)}
                    rows={3}
                    className="w-full bg-transparent text-xs text-[#a3a3a3] resize-none focus:ring-0 border-none p-0 placeholder-[#525252]"
                    placeholder="Describe tu programa..."
                />
            </div>

            {/* Grid: mode + startDay */}
            <div className="grid grid-cols-2 gap-3">
                <div className="bg-[#252525] border border-[#3f3f3f] p-3">
                    <label className="text-[8px] font-medium text-[#737373] uppercase tracking-widest block mb-1.5">Modo</label>
                    <select
                        value={program.mode || 'hypertrophy'}
                        onChange={e => onUpdateField('mode', e.target.value)}
                        className="w-full bg-[#1a1a1a] border border-[#3f3f3f] px-2 py-1.5 text-xs font-medium text-white"
                    >
                        <option value="hypertrophy">Hipertrofia</option>
                        <option value="powerlifting">Powerlifting</option>
                        <option value="powerbuilding">Powerbuilding</option>
                    </select>
                </div>
                <div className="bg-[#252525] border border-[#3f3f3f] p-3">
                    <label className="text-[8px] font-medium text-[#737373] uppercase tracking-widest block mb-1.5">Inicio Semana</label>
                    <div className="relative">
                        <select
                            value={program.startDay ?? 1}
                            onChange={e => onUpdateField('startDay', parseInt(e.target.value))}
                            className="w-full bg-[#1a1a1a] border border-[#3f3f3f] px-2 py-1.5 pr-6 text-xs font-medium text-white"
                        >
                            {DAYS_OF_WEEK.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}
                        </select>
                        <ChevronDownIcon size={12} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none text-[#737373]" />
                    </div>
                </div>
            </div>

            {/* Current split */}
            <div className="bg-[#252525] border border-[#3f3f3f] p-3">
                <div className="flex items-center justify-between mb-2">
                    <label className="text-[8px] font-medium text-[#737373] uppercase tracking-widest">Split Actual</label>
                    <button onClick={onOpenSplitChanger} className="text-[9px] font-medium text-white hover:underline">
                        Cambiar
                    </button>
                </div>
                {currentSplit ? (
                    <>
                        <span className="text-[11px] font-medium text-white block mb-1.5">{currentSplit.name}</span>
                        <div className="flex gap-1">
                            {currentSplit.pattern.map((day, i) => (
                                <div key={i} className={`flex-1 h-1.5 ${day.toLowerCase() === 'descanso' ? 'bg-[#3f3f3f]' : 'bg-[#525252]'}`} />
                            ))}
                        </div>
                    </>
                ) : (
                    <span className="text-[10px] text-[#737373]">No definido</span>
                )}
            </div>

            {/* Author */}
            <div className="bg-[#252525] border border-[#3f3f3f] p-3">
                <label className="text-[8px] font-medium text-[#737373] uppercase tracking-widest block mb-1.5">Autor</label>
                <input
                    type="text"
                    value={program.author || ''}
                    onChange={e => onUpdateField('author', e.target.value)}
                    className="w-full bg-transparent text-xs font-medium text-white focus:ring-0 border-none p-0 placeholder-[#525252]"
                    placeholder="Nombre del autor"
                />
            </div>
        </div>
    );
};

export default DetailsSection;
