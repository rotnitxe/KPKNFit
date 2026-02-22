import React, { useState } from 'react';
import { Program } from '../../types';
import { SettingsIcon, ChevronDownIcon } from '../icons';
import { SPLIT_TEMPLATES } from '../../data/splitTemplates';

const DAYS_OF_WEEK = [
    { label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 },
    { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 },
    { label: 'Sábado', value: 6 },
];

interface ProgramConfigCardProps {
    program: Program;
    onUpdateProgram?: (program: Program) => void;
    onOpenSplitChanger: () => void;
    collapsed?: boolean;
    onToggleCollapse?: () => void;
}

const ProgramConfigCard: React.FC<ProgramConfigCardProps> = ({
    program, onUpdateProgram, onOpenSplitChanger,
    collapsed = false, onToggleCollapse,
}) => {
    const currentSplit = SPLIT_TEMPLATES.find(s => s.id === program.selectedSplitId);
    const startDay = program.startDay ?? 1;

    return (
        <div className="bg-zinc-900/50 border border-white/5 rounded-2xl overflow-hidden transition-all duration-300">
            <button onClick={onToggleCollapse} className="w-full flex items-center justify-between p-4 text-left">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-zinc-500/10 border border-zinc-500/20 flex items-center justify-center">
                        <SettingsIcon size={16} className="text-zinc-400" />
                    </div>
                    <div>
                        <h3 className="text-xs font-black text-white uppercase tracking-widest">Configuración</h3>
                        <p className="text-[9px] text-zinc-500 font-bold">
                            {program.mode === 'powerlifting' ? 'Powerlifting' : 'Hipertrofia'} • {DAYS_OF_WEEK.find(d => d.value === startDay)?.label || 'Lunes'}
                        </p>
                    </div>
                </div>
                <ChevronDownIcon size={16} className={`text-zinc-500 transition-transform duration-300 ${collapsed ? '' : 'rotate-180'}`} />
            </button>

            {!collapsed && (
                <div className="px-4 pb-4 space-y-3 animate-fade-in">
                    {/* Training mode */}
                    <div className="flex items-center justify-between bg-zinc-950 p-3 rounded-xl border border-white/5">
                        <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Modo</span>
                        <select
                            value={program.mode || 'hypertrophy'}
                            onChange={e => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.mode = e.target.value;
                                onUpdateProgram?.(updated);
                            }}
                            className="bg-black border border-white/10 rounded-lg px-2 py-1 text-[10px] font-bold text-white appearance-none"
                        >
                            <option value="hypertrophy">Hipertrofia</option>
                            <option value="powerlifting">Powerlifting</option>
                        </select>
                    </div>

                    {/* Start day */}
                    <div className="flex items-center justify-between bg-zinc-950 p-3 rounded-xl border border-white/5">
                        <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Inicio Semana</span>
                        <select
                            value={startDay}
                            onChange={e => {
                                const updated = JSON.parse(JSON.stringify(program));
                                updated.startDay = parseInt(e.target.value);
                                onUpdateProgram?.(updated);
                            }}
                            className="bg-black border border-white/10 rounded-lg px-2 py-1 text-[10px] font-bold text-white appearance-none"
                        >
                            {DAYS_OF_WEEK.map(d => (
                                <option key={d.value} value={d.value}>{d.label}</option>
                            ))}
                        </select>
                    </div>

                    {/* Current split */}
                    <div className="bg-zinc-950 p-3 rounded-xl border border-white/5">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Split Actual</span>
                            <button
                                onClick={onOpenSplitChanger}
                                className="text-[9px] font-bold text-blue-400 hover:text-blue-300 transition-colors"
                            >
                                Cambiar
                            </button>
                        </div>
                        {currentSplit ? (
                            <div>
                                <span className="text-[11px] font-black text-white">{currentSplit.name}</span>
                                <div className="flex gap-1 mt-1.5">
                                    {currentSplit.pattern.map((day, i) => (
                                        <div
                                            key={i}
                                            className={`flex-1 h-1.5 rounded-full ${day.toLowerCase() === 'descanso' ? 'bg-zinc-800' : 'bg-white/40'}`}
                                        />
                                    ))}
                                </div>
                            </div>
                        ) : (
                            <span className="text-[10px] text-zinc-500">No definido</span>
                        )}
                    </div>

                    {/* Description */}
                    <div className="bg-zinc-950 p-3 rounded-xl border border-white/5">
                        <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-1.5">Descripción</span>
                        <textarea
                            className="w-full bg-transparent border-none text-[10px] text-zinc-300 resize-none focus:ring-0 p-0 placeholder-zinc-600"
                            rows={2}
                            placeholder="Sin descripción"
                            defaultValue={program.description || ''}
                            onBlur={e => {
                                if (e.target.value !== (program.description || '')) {
                                    const updated = JSON.parse(JSON.stringify(program));
                                    updated.description = e.target.value;
                                    onUpdateProgram?.(updated);
                                }
                            }}
                        />
                    </div>
                </div>
            )}
        </div>
    );
};

export default ProgramConfigCard;
