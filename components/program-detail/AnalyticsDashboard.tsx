import React, { useState, useMemo } from 'react';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo } from '../../types';
import { ActivityIcon, DumbbellIcon, XIcon } from '../icons';
import { WorkoutVolumeAnalysis } from '../WorkoutVolumeAnalysis';
import { CaupolicanBody } from '../CaupolicanBody';
import { RelativeStrengthAndBasicsWidget } from '../RelativeStrengthAndBasicsWidget';
import ExerciseHistoryWidget from '../ExerciseHistoryWidget';
import { AugeAdaptiveCache } from '../../services/augeAdaptiveService';
import { BanisterTrend, BayesianConfidence, SelfImprovementScore } from '../ui/AugeDeepView';

type WidgetId = 'bodymap' | 'volume' | 'strength' | 'banister' | 'adherence' | 'recovery' | 'history';

interface AnalyticsDashboardProps {
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
    adaptiveCache: AugeAdaptiveCache;
    exerciseList: ExerciseMuscleInfo[];
}

const WIDGET_META: Record<WidgetId, { label: string; }> = {
    bodymap: { label: 'Mapa Corporal' },
    volume: { label: 'Volumen' },
    strength: { label: 'Fuerza' },
    banister: { label: 'AUGE Banister' },
    adherence: { label: 'Adherencia' },
    recovery: { label: 'Recuperación' },
    history: { label: 'Historial' },
};

const DEFAULT_WIDGETS: WidgetId[] = ['bodymap', 'volume', 'strength', 'banister', 'adherence', 'recovery', 'history'];

const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({
    program, history: historyData, settings, isOnline, isActive,
    currentWeeks, selectedWeekId, onSelectWeek,
    visualizerData, displayedSessions, totalAdherence, weeklyAdherence,
    programDiscomforts, adaptiveCache, exerciseList,
}) => {
    const [activeWidgets] = useState<WidgetId[]>(DEFAULT_WIDGETS);
    const [showAdherenceDetail, setShowAdherenceDetail] = useState(false);
    const [selectedMusclePos, setSelectedMusclePos] = useState<{ muscle: string; x: number; y: number } | null>(null);
    const [focusedMuscle, setFocusedMuscle] = useState<string | null>(null);

    const recoveryData = useMemo(() => {
        const entries = Object.entries(adaptiveCache.personalizedRecoveryHours || {});
        return entries.sort((a, b) => b[1] - a[1]).slice(0, 8);
    }, [adaptiveCache]);

    const confidenceLabel = useMemo(() => {
        const obs = adaptiveCache.totalObservations;
        if (obs >= 30) return 'Alta';
        if (obs >= 15) return 'Media';
        if (obs >= 5) return 'Baja';
        return 'Poblacional';
    }, [adaptiveCache]);

    if (!isActive) {
        return (
            <div className="h-full flex items-center justify-center p-8">
                <div className="text-center">
                    <DumbbellIcon size={32} className="text-[#48484A] mx-auto mb-3" />
                    <h3 className="text-sm font-bold text-white uppercase mb-1">Programa Inactivo</h3>
                    <p className="text-xs text-[#48484A]">Inicia el programa para ver analytics.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="h-full overflow-y-auto custom-scrollbar">
            {/* Header */}
            <div className="px-4 py-3 border-b border-white/5 flex items-center justify-between sticky top-0 bg-[#111] z-10">
                <span className="text-sm font-bold text-white uppercase tracking-wide">Analytics</span>
            </div>

            <div className="px-4 py-4 space-y-6">
                {/* ── Body Map ── */}
                {activeWidgets.includes('bodymap') && (
                    <section>
                        <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide mb-3">Mapa de Volumen</h3>
                        <div className="flex flex-col items-center">
                            <CaupolicanBody
                                data={visualizerData as any}
                                isPowerlifting={program.mode === 'powerlifting'}
                                focusedMuscle={focusedMuscle}
                                discomforts={programDiscomforts}
                                onMuscleClick={(muscle, x, y) => setSelectedMusclePos({ muscle, x, y })}
                            />
                        </div>
                        {programDiscomforts.length > 0 && (
                            <div className="mt-3 flex flex-wrap justify-center gap-1.5">
                                {programDiscomforts.slice(0, 5).map((disc, idx) => (
                                    <span key={idx} className="text-[10px] font-bold text-red-300 bg-red-500/10 border border-red-500/20 px-2 py-0.5 rounded-md">
                                        {disc.name} <span className="text-red-400">{disc.count}x</span>
                                    </span>
                                ))}
                            </div>
                        )}
                    </section>
                )}

                {/* ── Volume Analysis ── */}
                {activeWidgets.includes('volume') && (
                    <section>
                        <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide mb-3">Volumen Semanal</h3>
                        <WorkoutVolumeAnalysis
                            sessions={displayedSessions}
                            history={historyData}
                            isOnline={isOnline}
                            settings={settings}
                            selectedMuscleInfo={selectedMusclePos}
                            onCloseMuscle={() => setSelectedMusclePos(null)}
                        />
                    </section>
                )}

                {/* ── Adherence ── */}
                {activeWidgets.includes('adherence') && (
                    <section>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide">Adherencia</h3>
                            <button onClick={() => setShowAdherenceDetail(!showAdherenceDetail)} className="text-[10px] font-bold text-[#FC4C02] hover:underline">
                                {showAdherenceDetail ? 'Cerrar' : 'Detalle'}
                            </button>
                        </div>
                        <div className="flex items-center gap-4">
                            {/* Circular progress */}
                            <div className="relative w-20 h-20 shrink-0">
                                <svg viewBox="0 0 36 36" className="w-full h-full transform -rotate-90">
                                    <path className="text-[#1a1a1a]" strokeWidth="3.5" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                                    <path
                                        strokeWidth="3.5" strokeDasharray={`${totalAdherence}, 100`} strokeLinecap="round" stroke="currentColor" fill="none"
                                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                                        style={{ color: totalAdherence >= 80 ? '#00F19F' : totalAdherence >= 50 ? '#FFD60A' : '#FF3B30' }}
                                    />
                                </svg>
                                <div className="absolute inset-0 flex items-center justify-center">
                                    <span className="text-xl font-black text-white">{totalAdherence}%</span>
                                </div>
                            </div>
                            {/* Mini calendar */}
                            <div className="flex-1 grid grid-cols-7 gap-1">
                                {weeklyAdherence.slice(0, 14).map((w, i) => (
                                    <div
                                        key={i}
                                        className={`w-full aspect-square rounded-sm ${w.pct === 100 ? 'bg-[#00F19F]' : w.pct > 0 ? 'bg-[#FFD60A]' : 'bg-[#1a1a1a]'}`}
                                        title={`${w.weekName}: ${w.pct}%`}
                                    />
                                ))}
                            </div>
                        </div>
                        {showAdherenceDetail && (
                            <div className="mt-3 space-y-1.5">
                                {weeklyAdherence.map((week, i) => (
                                    <div key={i} className="flex items-center gap-2">
                                        <span className="w-12 text-right text-[10px] font-bold text-[#48484A]">S{i + 1}</span>
                                        <div className="flex-1 h-2 bg-[#1a1a1a] rounded-full overflow-hidden">
                                            <div
                                                className="h-full rounded-full transition-all"
                                                style={{
                                                    width: `${week.pct}%`,
                                                    backgroundColor: week.pct === 100 ? '#00F19F' : week.pct > 50 ? '#FFD60A' : '#FF3B30',
                                                }}
                                            />
                                        </div>
                                        <span className="w-8 text-[10px] font-bold text-white">{week.pct}%</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </section>
                )}

                {/* ── Strength ── */}
                {activeWidgets.includes('strength') && (
                    <section>
                        <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide mb-3">Fuerza Relativa</h3>
                        <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
                    </section>
                )}

                {/* ── AUGE Banister ── */}
                {activeWidgets.includes('banister') && adaptiveCache.banister && (
                    <section>
                        <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide mb-1">AUGE — Fitness vs Fatiga</h3>
                        <p className="text-[10px] text-[#48484A] mb-3">
                            {adaptiveCache.banister.verdict || 'Modelo Banister activo'}
                        </p>
                        <div className="bg-[#1a1a1a] rounded-xl p-3">
                            <BanisterTrend systemData={adaptiveCache.banister?.systems?.muscular || null} />
                        </div>
                    </section>
                )}

                {/* ── Recovery Map ── */}
                {activeWidgets.includes('recovery') && recoveryData.length > 0 && (
                    <section>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide">Recuperación</h3>
                            <span className="text-[10px] font-bold px-2 py-0.5 rounded-md" style={{
                                backgroundColor: confidenceLabel === 'Alta' ? 'rgba(0,241,159,0.1)' : confidenceLabel === 'Media' ? 'rgba(255,214,10,0.1)' : 'rgba(255,59,48,0.1)',
                                color: confidenceLabel === 'Alta' ? '#00F19F' : confidenceLabel === 'Media' ? '#FFD60A' : '#FF3B30',
                            }}>
                                {confidenceLabel}
                            </span>
                        </div>
                        <div className="space-y-1">
                            {recoveryData.map(([muscle, hours]) => (
                                <div key={muscle} className="flex items-center gap-2">
                                    <span className="w-20 text-[10px] font-bold text-[#8E8E93] truncate text-right">{muscle}</span>
                                    <div className="flex-1 h-2 bg-[#1a1a1a] rounded-full overflow-hidden">
                                        <div
                                            className="h-full rounded-full"
                                            style={{
                                                width: `${Math.min(100, (hours / 96) * 100)}%`,
                                                backgroundColor: hours > 72 ? '#FF3B30' : hours > 48 ? '#FFD60A' : '#00F19F',
                                            }}
                                        />
                                    </div>
                                    <span className="w-8 text-[10px] font-bold text-white text-right">{Math.round(hours)}h</span>
                                </div>
                            ))}
                        </div>
                        {adaptiveCache.selfImprovement && (
                            <div className="mt-4 bg-[#1a1a1a] rounded-xl p-3">
                                <SelfImprovementScore
                                    score={adaptiveCache.selfImprovement.overall_prediction_score}
                                    trend={adaptiveCache.selfImprovement.improvement_trend}
                                    recommendations={adaptiveCache.selfImprovement.recommendations}
                                />
                            </div>
                        )}
                    </section>
                )}

                {/* ── Exercise History ── */}
                {activeWidgets.includes('history') && (
                    <section>
                        <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide mb-3">Historial de Ejercicios</h3>
                        <ExerciseHistoryWidget program={program} history={historyData} />
                    </section>
                )}
            </div>
        </div>
    );
};

export default AnalyticsDashboard;
