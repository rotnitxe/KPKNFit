import React from 'react';
import { SplitTemplate } from '../../data/splitTemplates';
import { DumbbellIcon, CalendarIcon, ActivityIcon } from '../icons';

const DAY_NAMES = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

interface ProgramPreviewPanelProps {
    programName: string;
    templateName: string;
    templateType: 'simple' | 'complex';
    selectedSplit: SplitTemplate | null;
    startDay: number;
    cycleDuration: number;
    blockNames?: string[];
    blockDurations?: number[];
    events?: { title: string; calculatedWeek?: number; repeatEveryXCycles?: number }[];
}

const ProgramPreviewPanel: React.FC<ProgramPreviewPanelProps> = ({
    programName, templateName, templateType, selectedSplit,
    startDay, cycleDuration, blockNames, blockDurations, events,
}) => {
    const trainingDays = selectedSplit ? selectedSplit.pattern.filter(d => d.toLowerCase() !== 'descanso').length : 0;
    const restDays = cycleDuration - trainingDays;

    return (
        <div className="h-full flex flex-col bg-zinc-950 border-l border-white/5 overflow-y-auto custom-scrollbar">
            <div className="p-4 border-b border-white/5">
                <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-1">Preview</h3>
                <p className="text-sm font-black text-white uppercase tracking-tight truncate">
                    {programName || 'Sin nombre'}
                </p>
                <p className="text-[9px] text-zinc-500 mt-0.5">{templateName} • {templateType === 'simple' ? 'Cíclico' : 'Bloques'}</p>
            </div>

            {/* Split Pattern */}
            {selectedSplit && (
                <div className="p-4 border-b border-white/5 space-y-3">
                    <div className="flex items-center justify-between">
                        <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Split</span>
                        <span className="text-[9px] font-bold text-zinc-500">{selectedSplit.name}</span>
                    </div>
                    <div className="flex gap-1">
                        {selectedSplit.pattern.map((day, i) => {
                            const dayName = DAY_NAMES[(startDay + i) % 7];
                            const isRest = day.toLowerCase() === 'descanso';
                            return (
                                <div key={i} className="flex-1 text-center">
                                    <div className="text-[7px] font-bold text-zinc-600 mb-1">{dayName}</div>
                                    <div className={`h-1.5 rounded-full ${isRest ? 'bg-zinc-800' : 'bg-white/40'}`} />
                                    <div className={`text-[6px] mt-0.5 font-bold truncate ${isRest ? 'text-zinc-700' : 'text-zinc-400'}`}>
                                        {isRest ? '-' : day}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                    <div className="flex gap-3 text-center">
                        <div className="flex-1 bg-zinc-900 rounded-lg p-2">
                            <div className="text-sm font-black text-white">{trainingDays}</div>
                            <div className="text-[7px] text-zinc-500 font-bold uppercase">Entreno</div>
                        </div>
                        <div className="flex-1 bg-zinc-900 rounded-lg p-2">
                            <div className="text-sm font-black text-white">{restDays}</div>
                            <div className="text-[7px] text-zinc-500 font-bold uppercase">Descanso</div>
                        </div>
                    </div>
                </div>
            )}

            {/* Block Structure (complex) */}
            {templateType === 'complex' && blockNames && blockDurations && (
                <div className="p-4 border-b border-white/5 space-y-2">
                    <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-2">Bloques</span>
                    <div className="flex gap-1 h-3 rounded-full overflow-hidden">
                        {blockNames.map((name, i) => {
                            const colors = ['bg-blue-500', 'bg-purple-500', 'bg-yellow-500', 'bg-emerald-500', 'bg-rose-500'];
                            const total = blockDurations.reduce((a, b) => a + b, 0);
                            const width = total > 0 ? (blockDurations[i] / total) * 100 : 0;
                            return (
                                <div key={i} className={`${colors[i % colors.length]} rounded-full`} style={{ width: `${width}%` }} title={`${name}: ${blockDurations[i]}sem`} />
                            );
                        })}
                    </div>
                    <div className="space-y-1 mt-2">
                        {blockNames.map((name, i) => (
                            <div key={i} className="flex items-center justify-between text-[9px]">
                                <span className="text-zinc-300 font-bold">{name}</span>
                                <span className="text-zinc-500">{blockDurations[i]}sem</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Events */}
            {events && events.length > 0 && (
                <div className="p-4 border-b border-white/5 space-y-2">
                    <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-1">Eventos</span>
                    {events.map((ev, i) => (
                        <div key={i} className="flex items-center gap-2 bg-zinc-900 rounded-lg p-2">
                            <div className="w-1.5 h-1.5 bg-yellow-400 rounded-full" />
                            <span className="text-[9px] text-zinc-300 font-bold flex-1 truncate">{ev.title}</span>
                            {ev.repeatEveryXCycles && <span className="text-[8px] text-zinc-500">c/{ev.repeatEveryXCycles}</span>}
                            {ev.calculatedWeek !== undefined && <span className="text-[8px] text-zinc-500">S{ev.calculatedWeek + 1}</span>}
                        </div>
                    ))}
                </div>
            )}

            {/* Empty state */}
            {!selectedSplit && (
                <div className="flex-1 flex items-center justify-center p-4">
                    <div className="text-center">
                        <DumbbellIcon size={32} className="text-zinc-800 mx-auto mb-2" />
                        <p className="text-[9px] text-zinc-600 font-bold">Selecciona un split para ver el preview</p>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ProgramPreviewPanel;
