import React, { useState } from 'react';
import { Program, Session, ExerciseMuscleInfo, ProgramWeek } from '../../types';
import { ActivityIcon, DumbbellIcon, ChevronDownIcon, XIcon } from '../icons';
import { WorkoutVolumeAnalysis } from '../WorkoutVolumeAnalysis';
import { CaupolicanBody } from '../CaupolicanBody';
import { RelativeStrengthAndBasicsWidget } from '../RelativeStrengthAndBasicsWidget';
import ExerciseHistoryWidget from '../ExerciseHistoryWidget';

interface MetricsCardProps {
    program: Program;
    history: any[];
    settings: any;
    isOnline: boolean;
    isActive: boolean;
    currentWeeks: (ProgramWeek & { mesoIndex: number })[];
    selectedWeekId: string | null;
    onSelectWeek: (id: string) => void;
    visualizerData: any;
    displayedSessions: Session[];
    totalAdherence: number;
    weeklyAdherence: { weekName: string; pct: number }[];
    programDiscomforts: { name: string; count: number }[];
    collapsed?: boolean;
    onToggleCollapse?: () => void;
}

type MetricFilter = 'todos' | 'volumen' | 'fuerza' | 'historial';

const FILTERS: { id: MetricFilter; label: string }[] = [
    { id: 'todos', label: 'Todos' },
    { id: 'volumen', label: 'Fatiga & Volumen' },
    { id: 'fuerza', label: 'Fuerza Relativa' },
    { id: 'historial', label: 'Historial' },
];

const MetricsCard: React.FC<MetricsCardProps> = ({
    program, history, settings, isOnline, isActive,
    currentWeeks, selectedWeekId, onSelectWeek,
    visualizerData, displayedSessions, totalAdherence, weeklyAdherence,
    programDiscomforts,
    collapsed = false, onToggleCollapse,
}) => {
    const [metricFilter, setMetricFilter] = useState<MetricFilter>('todos');
    const [showAdherenceModal, setShowAdherenceModal] = useState(false);
    const [selectedMusclePos, setSelectedMusclePos] = useState<{ muscle: string; x: number; y: number } | null>(null);
    const [focusedMuscle, setFocusedMuscle] = useState<string | null>(null);

    return (
        <div className="bg-zinc-900/50 border border-white/5 rounded-2xl overflow-hidden transition-all duration-300">
            <button onClick={onToggleCollapse} className="w-full flex items-center justify-between p-4 text-left">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
                        <ActivityIcon size={16} className="text-emerald-400" />
                    </div>
                    <div>
                        <h3 className="text-xs font-black text-white uppercase tracking-widest">Métricas</h3>
                        <p className="text-[9px] text-zinc-500 font-bold">
                            Adherencia {totalAdherence}% • {displayedSessions.length} sesiones
                        </p>
                    </div>
                </div>
                <ChevronDownIcon size={16} className={`text-zinc-500 transition-transform duration-300 ${collapsed ? '' : 'rotate-180'}`} />
            </button>

            {!collapsed && (
                <div className="px-4 pb-4 space-y-4 animate-fade-in relative">
                    {!isActive && (
                        <div className="absolute inset-0 z-50 backdrop-blur-md bg-black/60 rounded-2xl flex justify-center items-start pt-16">
                            <div className="flex flex-col items-center text-center p-4 max-w-xs bg-black/40 rounded-2xl border border-white/5">
                                <DumbbellIcon size={36} className="text-zinc-600 mb-3 animate-pulse" />
                                <h3 className="text-base font-black text-white uppercase mb-1">Programa Inactivo</h3>
                                <p className="text-[10px] text-zinc-400">Inicia este programa para ver métricas.</p>
                            </div>
                        </div>
                    )}

                    <div className={`space-y-4 ${!isActive ? 'opacity-20 pointer-events-none' : ''}`}>
                        {/* Week selector */}
                        {currentWeeks.length > 0 && (
                            <div className="flex items-center gap-1.5 justify-center overflow-x-auto no-scrollbar pb-1">
                                <span className="text-[8px] font-black text-zinc-600 uppercase tracking-widest mr-1">Sem:</span>
                                {currentWeeks.map((week, idx) => (
                                    <button
                                        key={week.id}
                                        onClick={() => onSelectWeek(week.id)}
                                        className={`shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-[9px] font-bold border transition-all ${
                                            week.id === selectedWeekId
                                                ? 'bg-blue-600 border-blue-400 text-white shadow-[0_0_10px_rgba(37,99,235,0.5)]'
                                                : 'border-zinc-800 bg-zinc-900/50 text-zinc-500 hover:bg-zinc-800'
                                        }`}
                                    >
                                        {idx + 1}
                                    </button>
                                ))}
                            </div>
                        )}

                        {/* Filter tabs */}
                        <div className="flex gap-1.5 overflow-x-auto no-scrollbar pb-2 border-b border-white/5">
                            {FILTERS.map(f => (
                                <button
                                    key={f.id}
                                    onClick={() => setMetricFilter(f.id)}
                                    className={`shrink-0 px-3 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest transition-colors border ${
                                        metricFilter === f.id
                                            ? 'bg-white text-black border-white'
                                            : 'bg-zinc-900/50 text-zinc-500 border-white/10 hover:text-white'
                                    }`}
                                >
                                    {f.label}
                                </button>
                            ))}
                        </div>

                        {/* Volume & Caupolicán */}
                        {(metricFilter === 'todos' || metricFilter === 'volumen') && (
                            <div className="relative">
                                <div className="absolute top-0 right-0 z-40">
                                    <button
                                        onClick={() => setShowAdherenceModal(true)}
                                        className="relative w-14 h-14 flex items-center justify-center group hover:scale-105 transition-transform"
                                    >
                                        <div className="absolute inset-0 bg-black rounded-full shadow-[0_0_15px_rgba(52,211,153,0.25)]" />
                                        <svg viewBox="0 0 36 36" className="w-full h-full transform -rotate-90 relative z-10">
                                            <path className="text-zinc-900" strokeWidth="3" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                                            <path className="text-emerald-400 drop-shadow-[0_0_8px_rgba(52,211,153,0.8)]" strokeWidth="3" strokeDasharray={`${totalAdherence}, 100`} strokeLinecap="round" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                                        </svg>
                                        <div className="absolute flex flex-col items-center justify-center z-20">
                                            <span className="text-white text-[11px] font-black leading-none">{totalAdherence}%</span>
                                        </div>
                                    </button>
                                </div>

                                <div className="flex flex-col items-center">
                                    <CaupolicanBody
                                        data={visualizerData as any}
                                        isPowerlifting={program.mode === 'powerlifting'}
                                        focusedMuscle={focusedMuscle}
                                        discomforts={programDiscomforts}
                                        onMuscleClick={(muscle, x, y) => setSelectedMusclePos({ muscle, x, y })}
                                    />
                                    {programDiscomforts.length > 0 && (
                                        <div className="mt-4 w-full max-w-[260px]">
                                            <h4 className="text-[8px] font-black text-red-400 uppercase tracking-widest mb-2 flex items-center justify-center gap-1.5">
                                                <ActivityIcon size={10} /> Foco de Tensión
                                            </h4>
                                            <div className="flex flex-wrap justify-center gap-1.5">
                                                {programDiscomforts.slice(0, 5).map((disc, idx) => (
                                                    <div key={idx} className="bg-red-950/40 border border-red-500/20 px-2 py-0.5 rounded-full flex items-center gap-1">
                                                        <span className="text-[9px] font-bold text-red-200">{disc.name}</span>
                                                        <span className="text-[8px] text-red-300 font-black">{disc.count}x</span>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                                <div className="mt-6 relative z-50">
                                    <WorkoutVolumeAnalysis
                                        sessions={displayedSessions}
                                        history={history}
                                        isOnline={isOnline}
                                        settings={settings}
                                        selectedMuscleInfo={selectedMusclePos}
                                        onCloseMuscle={() => setSelectedMusclePos(null)}
                                    />
                                </div>
                            </div>
                        )}

                        {/* Relative Strength */}
                        {(metricFilter === 'todos' || metricFilter === 'fuerza') && (
                            <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
                        )}

                        {/* History */}
                        {(metricFilter === 'todos' || metricFilter === 'historial') && (
                            <ExerciseHistoryWidget program={program} history={history} />
                        )}
                    </div>

                    {/* Adherence modal */}
                    {showAdherenceModal && (
                        <div
                            className="fixed inset-0 z-[120] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-fade-in"
                            onClick={() => setShowAdherenceModal(false)}
                        >
                            <div className="bg-zinc-950 border border-white/10 w-full max-w-sm rounded-2xl p-5 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                                <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-emerald-400 to-cyan-500 opacity-50 rounded-t-2xl" />
                                <div className="flex justify-between items-start mb-4">
                                    <div>
                                        <h3 className="text-lg font-black text-white uppercase tracking-tight">Adherencia</h3>
                                        <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest">Avance</p>
                                    </div>
                                    <button onClick={() => setShowAdherenceModal(false)} className="p-1.5 bg-zinc-900 rounded-full text-zinc-500 hover:text-white transition-colors">
                                        <XIcon size={14} />
                                    </button>
                                </div>
                                <div className="space-y-3 max-h-[50vh] overflow-y-auto custom-scrollbar">
                                    {weeklyAdherence.map((week, i) => (
                                        <div key={i} className="flex items-center gap-2">
                                            <span className="w-14 text-right text-[9px] font-black text-zinc-400 uppercase">{week.weekName}</span>
                                            <div className="flex-1 h-2.5 bg-black border border-white/5 rounded-full overflow-hidden">
                                                <div
                                                    className={`h-full rounded-full transition-all duration-1000 ${week.pct === 100 ? 'bg-emerald-400' : week.pct > 50 ? 'bg-yellow-400' : 'bg-red-500'}`}
                                                    style={{ width: `${week.pct}%` }}
                                                />
                                            </div>
                                            <span className="w-8 text-[9px] font-bold text-white">{week.pct}%</span>
                                        </div>
                                    ))}
                                </div>
                                <div className="mt-6 p-3 bg-zinc-900/50 rounded-xl border border-white/5 text-center">
                                    <p className="text-2xl font-black text-white">{totalAdherence}%</p>
                                    <p className="text-[8px] text-zinc-500 font-bold uppercase tracking-widest">Total</p>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default MetricsCard;
