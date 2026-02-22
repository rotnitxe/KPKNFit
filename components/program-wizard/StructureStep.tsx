import React from 'react';
import { SplitTemplate } from '../../data/splitTemplates';
import { ProgramTemplateOption } from '../../data/programTemplates';
import SplitGallery from '../SplitGallery';
import { DumbbellIcon, ChevronDownIcon } from '../icons';

const DAYS_OF_WEEK = [
    { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
    { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
    { label: 'Sábado', value: 6 },
];

interface StructureStepProps {
    templates: ProgramTemplateOption[];
    selectedTemplateId: string;
    onSelectTemplate: (id: string) => void;
    selectedSplit: SplitTemplate | null;
    onSelectSplit: (split: SplitTemplate) => void;
    startDay: number;
    onChangeStartDay: (day: number) => void;
    cycleDuration: number;
    onChangeCycleDuration: (d: number) => void;
    programName: string;
    onChangeProgramName: (name: string) => void;
    splitMode?: 'global' | 'per_block';
    onChangeSplitMode?: (mode: 'global' | 'per_block') => void;
    blockNames?: string[];
    activeBlockIndex?: number;
    onSelectBlockIndex?: (idx: number) => void;
    blockSplits?: Record<number, SplitTemplate>;
}

const StructureStep: React.FC<StructureStepProps> = ({
    templates, selectedTemplateId, onSelectTemplate,
    selectedSplit, onSelectSplit,
    startDay, onChangeStartDay,
    cycleDuration, onChangeCycleDuration,
    programName, onChangeProgramName,
    splitMode, onChangeSplitMode,
    blockNames, activeBlockIndex, onSelectBlockIndex, blockSplits,
}) => {
    const selectedTemplate = templates.find(t => t.id === selectedTemplateId);
    const isComplex = selectedTemplateId === 'power-complex';

    return (
        <div className="max-w-lg mx-auto py-6 px-4 space-y-8">
            {/* Program name */}
            <div className="space-y-2">
                <label className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block">Nombre del Programa</label>
                <input
                    type="text"
                    value={programName}
                    onChange={e => onChangeProgramName(e.target.value)}
                    placeholder="Mi programa..."
                    className="w-full bg-zinc-950 border border-white/10 rounded-xl px-4 py-3 text-sm font-bold text-white placeholder-zinc-600 focus:ring-1 focus:ring-white/30 focus:border-white/30"
                />
            </div>

            {/* Template selection */}
            <div className="space-y-3">
                <h3 className="text-[10px] font-black text-white uppercase tracking-widest text-center">Estructura Temporal</h3>
                <div className="flex gap-3 overflow-x-auto no-scrollbar pb-2">
                    {templates.map(t => {
                        const isSelected = selectedTemplateId === t.id;
                        return (
                            <button
                                key={t.id}
                                onClick={() => onSelectTemplate(t.id)}
                                className={`shrink-0 w-56 p-4 rounded-2xl flex flex-col items-center text-center transition-all border ${
                                    isSelected
                                        ? 'bg-white text-black border-white scale-[1.02] shadow-lg'
                                        : 'bg-black text-white border-white/10 opacity-60 hover:opacity-100 hover:border-white/30'
                                }`}
                            >
                                <div className={`p-2.5 rounded-full mb-2 ${isSelected ? 'bg-black/10' : 'bg-white/10'}`}>
                                    <DumbbellIcon size={18} className={isSelected ? 'text-black' : 'text-white'} />
                                </div>
                                <h4 className="text-sm font-black uppercase tracking-tight mb-1">{t.name}</h4>
                                <p className={`text-[9px] font-bold ${isSelected ? 'text-black/70' : 'text-zinc-400'}`}>{t.description}</p>
                            </button>
                        );
                    })}
                </div>
            </div>

            {/* Start day & duration */}
            <div className="grid grid-cols-2 gap-3">
                <div className="bg-zinc-950 border border-white/5 rounded-xl p-3">
                    <label className="text-[8px] font-black text-zinc-500 uppercase tracking-widest block mb-1.5">Inicio Semana</label>
                    <div className="relative">
                        <select
                            value={startDay}
                            onChange={e => onChangeStartDay(parseInt(e.target.value))}
                            className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-xs font-bold text-white appearance-none pr-6 focus:ring-1 focus:ring-white/30"
                        >
                            {DAYS_OF_WEEK.map(d => (
                                <option key={d.value} value={d.value}>{d.label}</option>
                            ))}
                        </select>
                        <ChevronDownIcon size={12} className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none text-zinc-500" />
                    </div>
                </div>
                <div className="bg-zinc-950 border border-white/5 rounded-xl p-3">
                    <label className="text-[8px] font-black text-zinc-500 uppercase tracking-widest block mb-1.5">Días por Ciclo</label>
                    <input
                        type="number"
                        min={1}
                        max={14}
                        value={cycleDuration}
                        onChange={e => onChangeCycleDuration(Math.max(1, parseInt(e.target.value) || 7))}
                        className="w-full bg-black border border-white/10 rounded-lg px-2 py-1.5 text-xs font-bold text-white text-center focus:ring-1 focus:ring-white/30"
                    />
                </div>
            </div>

            {/* Split mode selector (complex only) */}
            {isComplex && onChangeSplitMode && (
                <div className="bg-zinc-950 border border-white/5 rounded-xl p-1.5 flex">
                    <button
                        onClick={() => onChangeSplitMode('global')}
                        className={`flex-1 py-2.5 rounded-lg text-[9px] font-black uppercase tracking-widest transition-all ${
                            splitMode === 'global' ? 'bg-white text-black' : 'text-zinc-500 hover:text-white'
                        }`}
                    >
                        Global
                    </button>
                    <button
                        onClick={() => onChangeSplitMode('per_block')}
                        className={`flex-1 py-2.5 rounded-lg text-[9px] font-black uppercase tracking-widest transition-all ${
                            splitMode === 'per_block' ? 'bg-white text-black' : 'text-zinc-500 hover:text-white'
                        }`}
                    >
                        Por Bloque
                    </button>
                </div>
            )}

            {/* Block selector (per_block mode) */}
            {isComplex && splitMode === 'per_block' && blockNames && onSelectBlockIndex && (
                <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
                    {blockNames.map((name, idx) => {
                        const isActive = activeBlockIndex === idx;
                        const assigned = blockSplits?.[idx];
                        return (
                            <button
                                key={idx}
                                onClick={() => onSelectBlockIndex(idx)}
                                className={`shrink-0 px-4 py-2.5 rounded-xl text-[9px] font-black uppercase tracking-widest transition-all border ${
                                    isActive
                                        ? 'bg-white text-black border-white'
                                        : 'bg-black border-white/10 text-zinc-500 hover:text-white hover:border-white/30'
                                }`}
                            >
                                <span>{name}</span>
                                <div className={`w-1.5 h-1.5 rounded-full mt-1 mx-auto ${assigned ? 'bg-emerald-500' : 'bg-zinc-700'}`} />
                            </button>
                        );
                    })}
                </div>
            )}

            {/* Split gallery */}
            <div className="space-y-2">
                <h3 className="text-[10px] font-black text-white uppercase tracking-widest text-center">Distribución Semanal</h3>
                <div className="bg-zinc-950 border border-white/5 rounded-2xl overflow-hidden max-h-[50vh]">
                    <SplitGallery
                        onSelect={onSelectSplit}
                        currentSplitId={selectedSplit?.id}
                    />
                </div>
            </div>

            {/* Selected split preview */}
            {selectedSplit && (
                <div className="bg-zinc-950 border border-white/10 rounded-xl p-3">
                    <div className="flex items-center justify-between mb-2">
                        <span className="text-[10px] font-black text-white">{selectedSplit.name}</span>
                        <span className="text-[9px] text-zinc-500">{selectedSplit.pattern.filter(d => d.toLowerCase() !== 'descanso').length} días</span>
                    </div>
                    <div className="flex gap-1">
                        {selectedSplit.pattern.map((day, i) => {
                            const dayLabel = DAYS_OF_WEEK[(startDay + i) % 7]?.label.slice(0, 3) || '';
                            const isRest = day.toLowerCase() === 'descanso';
                            return (
                                <div key={i} className="flex-1 text-center">
                                    <div className="text-[7px] font-bold text-zinc-600 mb-0.5">{dayLabel}</div>
                                    <div className={`h-1.5 rounded-full ${isRest ? 'bg-zinc-800' : 'bg-white/40'}`} />
                                    <div className={`text-[6px] mt-0.5 font-bold truncate ${isRest ? 'text-zinc-700' : 'text-zinc-400'}`}>
                                        {isRest ? '-' : day}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
};

export default StructureStep;
