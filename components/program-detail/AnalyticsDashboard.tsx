import React, { useState, useMemo } from 'react';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo, Settings, PostSessionFeedback } from '../../types';
import { ActivityIcon, DumbbellIcon, XIcon, TargetIcon, ChevronDownIcon } from '../icons';
import { WorkoutVolumeAnalysis } from '../WorkoutVolumeAnalysis';
import { CaupolicanBody } from '../CaupolicanBody';
import AthleteProfilingWizard from '../AthleteProfilingWizard';
import { getIsraetelVolumeRecommendations, getKpnkVolumeRecommendations } from '../../services/volumeCalculator';
import { RelativeStrengthAndBasicsWidget } from '../RelativeStrengthAndBasicsWidget';
import ExerciseHistoryWidget from '../ExerciseHistoryWidget';
import { AugeAdaptiveCache } from '../../services/augeAdaptiveService';
import { BanisterTrend, BayesianConfidence, SelfImprovementScore } from '../ui/AugeDeepView';
import { calculateBrzycki1RM } from '../../utils/calculations';
import { getLocalDateString, getDatePartFromString } from '../../utils/dateUtils';
import { StarIcon } from '../icons';
import VolumeCalibrationHistoryWidget from './VolumeCalibrationHistoryWidget';
import VolumeRecalibrationModal from './VolumeRecalibrationModal';
import { MuscleStatsPanel } from '../MuscleStatsPanel';
import {
    getSuggestedVolumeAdjustments,
    applyVolumeAdjustments,
    shouldSuggestRecalibration,
    VolumeSuggestion,
} from '../../services/volumeCalibrationService';

type WidgetId = 'bodymap' | 'volume' | 'strength' | 'star1rm' | 'banister' | 'recovery' | 'history';

interface AnalyticsDashboardProps {
    program: Program;
    history: any[];
    settings: Settings | any;
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
    setSettings?: (s: Settings | ((prev: Settings) => Settings)) => void;
    onUpdateProgram?: (p: Program) => void;
    addToast?: (msg: string, type?: 'success' | 'error' | 'info') => void;
    postSessionFeedback?: PostSessionFeedback[] | null;
}

const WIDGET_META: Record<WidgetId, { label: string; }> = {
    bodymap: { label: 'Mapa Corporal' },
    volume: { label: 'Volumen' },
    strength: { label: 'Fuerza' },
    star1rm: { label: 'Progreso 1RM (estrella)' },
    banister: { label: 'AUGE Banister' },
    recovery: { label: 'Recuperación' },
    history: { label: 'Historial' },
};

const DEFAULT_WIDGETS: WidgetId[] = ['bodymap', 'volume', 'strength', 'star1rm', 'banister', 'recovery', 'history'];

const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({
    program, history: historyData, settings, isOnline, isActive,
    currentWeeks, selectedWeekId, onSelectWeek,
    visualizerData, displayedSessions, totalAdherence, weeklyAdherence,
    programDiscomforts, adaptiveCache, exerciseList,
    setSettings, onUpdateProgram, addToast, postSessionFeedback,
}) => {
    const [activeWidgets] = useState<WidgetId[]>(DEFAULT_WIDGETS);
    const [selectedMusclePos, setSelectedMusclePos] = useState<{ muscle: string; x: number; y: number } | null>(null);
    const [focusedMuscle, setFocusedMuscle] = useState<string | null>(null);
    const [showCalibrationWizard, setShowCalibrationWizard] = useState(false);
    const [showManualEditor, setShowManualEditor] = useState(false);
    const [showRecalibrationModal, setShowRecalibrationModal] = useState(false);
    const [recalibrationSuggestions, setRecalibrationSuggestions] = useState<VolumeSuggestion[]>([]);
    const [isRecalibrating, setIsRecalibrating] = useState(false);

    const suggestRecalibration = shouldSuggestRecalibration(settings, postSessionFeedback);

    const handleRecalibrateClick = async () => {
        if (program.volumeSystem !== 'kpnk' || !program.volumeRecommendations?.length) {
            addToast?.('Selecciona KPKN Personalizado y completa el cuestionario abajo.', 'info');
            setShowCalibrationWizard(true);
            return;
        }
        setIsRecalibrating(true);
        try {
            const suggestions = await getSuggestedVolumeAdjustments({
                program,
                postSessionFeedback: postSessionFeedback || [],
                settings: settings || undefined,
                isOnline,
            });
            if (suggestions.length > 0) {
                setRecalibrationSuggestions(suggestions);
                setShowRecalibrationModal(true);
            } else {
                addToast?.('Necesitas más sesiones con feedback para recalibrar.', 'info');
            }
        } finally {
            setIsRecalibrating(false);
        }
    };

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

    const { starExerciseNames, starGoal1RMMap } = useMemo(() => {
        const set = new Set<string>();
        const goalMap = new Map<string, number>();
        program.macrocycles?.forEach(m =>
            m.blocks?.forEach(b =>
                b.mesocycles?.forEach(me =>
                    me.weeks?.forEach(w =>
                        w.sessions?.forEach(s =>
                            ((s.parts && s.parts.length > 0) ? s.parts.flatMap((p: any) => p.exercises ?? []) : (s.exercises ?? [])).forEach((ex: any) => {
                                if (ex.isStarTarget && ex.name) {
                                    set.add(ex.name);
                                    if (ex.goal1RM != null && ex.goal1RM > 0 && !goalMap.has(ex.name)) {
                                        goalMap.set(ex.name, ex.goal1RM);
                                    }
                                }
                            })
                        )
                    )
                )
            )
        );
        return { starExerciseNames: Array.from(set), starGoal1RMMap: goalMap };
    }, [program]);

    const star1RMData = useMemo(() => {
        const programLogs = (historyData || []).filter((log: any) => log.programId === program.id);
        const byExercise: Record<string, { date: string; e1rm: number }[]> = {};
        starExerciseNames.forEach(name => { byExercise[name] = []; });
        programLogs.forEach((log: any) => {
            const date = log.date || log.endTime;
            const dateStr = typeof date === 'number' ? getLocalDateString(new Date(date)) : getDatePartFromString(String(date));
            (log.completedExercises || []).forEach((ex: any) => {
                const name = ex.exerciseName || ex.name;
                if (!name || !starExerciseNames.includes(name)) return;
                let maxE1rm = 0;
                (ex.sets || []).forEach((s: any) => {
                    const w = s.weight ?? 0;
                    const r = s.completedReps ?? s.reps ?? 0;
                    if (w > 0 && r > 0) {
                        const e1rm = calculateBrzycki1RM(w, r);
                        if (e1rm > maxE1rm) maxE1rm = e1rm;
                    }
                });
                if (maxE1rm > 0) byExercise[name].push({ date: dateStr, e1rm: maxE1rm });
            });
        });
        return Object.entries(byExercise).map(([name, points]) => {
            const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date));
            const last = sorted[sorted.length - 1];
            const prev = sorted.length > 1 ? sorted[sorted.length - 2] : null;
            const trend = prev && last ? (last.e1rm > prev.e1rm ? 'up' : last.e1rm < prev.e1rm ? 'down' : 'same') : null;
            return { name, lastE1rm: last?.e1rm, prevE1rm: prev?.e1rm, trend, count: sorted.length };
        }).filter(x => x.lastE1rm != null);
    }, [program.id, historyData, starExerciseNames]);

    const star1RMWithGoals = useMemo(() => {
        const withGoal = star1RMData.map(d => ({ ...d, goal1RM: starGoal1RMMap.get(d.name) }));
        const withoutData = starExerciseNames.filter(n => !star1RMData.some(d => d.name === n));
        return [
            ...withGoal,
            ...withoutData.map(name => ({ name, lastE1rm: undefined, prevE1rm: undefined, trend: null as string | null, count: 0, goal1RM: starGoal1RMMap.get(name) })),
        ];
    }, [star1RMData, starExerciseNames, starGoal1RMMap]);

    if (!isActive) {
        return (
            <div className="h-full flex items-center justify-center p-8 bg-[#121212]">
                <div className="text-center">
                    <DumbbellIcon size={32} className="text-zinc-500 mx-auto mb-3" />
                    <h3 className="text-sm font-bold text-white uppercase mb-1">Programa Inactivo</h3>
                    <p className="text-xs text-zinc-500">Inicia el programa para ver analytics.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="bg-[#121212]">
            <div className="px-4 sm:px-6 py-4 pb-[max(95px,calc(80px+env(safe-area-inset-bottom,0px)+12px))] space-y-6">
                <div className="text-[10px] font-black text-white uppercase tracking-widest text-zinc-500 mb-2">Analytics</div>
                    {/* ── Body Map ── */}
                    {activeWidgets.includes('bodymap') && (
                        <section className="bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden p-4">
                            <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-3">Mapa de Volumen</h3>
                            <div className="flex flex-col sm:flex-row gap-4 items-stretch sm:items-start bg-black rounded-xl p-3">
                                <div className="flex justify-center shrink-0">
                                    <CaupolicanBody
                                        data={visualizerData as any}
                                        isPowerlifting={program.mode === 'powerlifting' || program.mode === 'powerbuilding'}
                                        focusedMuscle={selectedMusclePos?.muscle ?? focusedMuscle}
                                        discomforts={programDiscomforts}
                                        onMuscleClick={(muscle, x, y) => {
                                            setSelectedMusclePos(prev => prev?.muscle === muscle ? null : { muscle, x: x ?? 0, y: y ?? 0 });
                                            setFocusedMuscle(muscle);
                                        }}
                                        onBodyBackgroundClick={() => {
                                            setSelectedMusclePos(null);
                                            setFocusedMuscle(null);
                                        }}
                                    />
                                </div>
                                <MuscleStatsPanel
                                    selectedMuscle={selectedMusclePos?.muscle ?? null}
                                    data={visualizerData ?? []}
                                    program={program}
                                    settings={settings}
                                    onClose={() => {
                                        setSelectedMusclePos(null);
                                        setFocusedMuscle(null);
                                    }}
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
                        {suggestRecalibration && program.volumeSystem === 'kpnk' && (
                            <button
                                onClick={handleRecalibrateClick}
                                disabled={isRecalibrating}
                                className="w-full mb-3 py-2 px-3 rounded-xl border border-amber-500/30 bg-amber-500/10 text-amber-400 text-[10px] font-bold flex items-center justify-center gap-2 hover:bg-amber-500/20 transition-colors"
                            >
                                {isRecalibrating ? 'Cargando…' : 'KPKN sugiere revisar tu volumen'}
                            </button>
                        )}
                        <div className="flex items-center justify-between gap-2 mb-3">
                            <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest">Volumen Semanal</h3>
                            <div className="flex items-center gap-2">
                                <select
                                    value={program.volumeSystem ?? settings?.volumeSystem ?? 'kpnk'}
                                    onChange={(e) => {
                                        const sys = e.target.value as 'israetel' | 'kpnk' | 'manual';
                                        if (onUpdateProgram && sys === 'israetel') {
                                            onUpdateProgram({
                                                ...program,
                                                volumeSystem: 'israetel',
                                                volumeRecommendations: getIsraetelVolumeRecommendations(),
                                                volumeAlertsEnabled: program.volumeAlertsEnabled ?? true,
                                            });
                                            addToast?.('Guía Israetel aplicada al programa.', 'success');
                                        } else if (onUpdateProgram && sys === 'manual') {
                                            onUpdateProgram({
                                                ...program,
                                                volumeSystem: 'manual',
                                                volumeRecommendations: getIsraetelVolumeRecommendations().map(r => ({ ...r })),
                                                volumeAlertsEnabled: program.volumeAlertsEnabled ?? true,
                                            });
                                            setShowManualEditor(true);
                                            addToast?.('Modo Manual: edita los rangos debajo.', 'success');
                                        } else if (sys === 'kpnk') {
                                            setShowCalibrationWizard(true);
                                        } else if (!onUpdateProgram) {
                                            setSettings?.({ volumeSystem: sys });
                                        }
                                    }}
                                    className="text-[9px] font-bold bg-[#1a1a1a] border border-white/10 rounded-lg px-2 py-1 text-white uppercase tracking-wider focus:ring-1 focus:ring-white/30"
                                >
                                    <option value="israetel">Israetel</option>
                                    <option value="kpnk">KPKN Personalizado</option>
                                    <option value="manual">Manual</option>
                                </select>
                                {program.volumeSystem === 'kpnk' && (
                                    <button
                                        onClick={handleRecalibrateClick}
                                        disabled={isRecalibrating}
                                        className="flex items-center gap-1.5 px-2 py-1 rounded-lg border border-white/20 bg-white/5 text-zinc-500 hover:bg-white/10 text-[9px] font-bold uppercase tracking-wider transition-colors disabled:opacity-50"
                                    >
                                        Re-calibrar
                                    </button>
                                )}
                                {program.volumeSystem === 'manual' && onUpdateProgram && (
                                    <button
                                        onClick={() => setShowManualEditor(prev => !prev)}
                                        className={`flex items-center gap-1.5 px-2 py-1 rounded-lg border text-[9px] font-bold uppercase tracking-wider transition-colors ${
                                            showManualEditor
                                                ? 'border-white/30 bg-white/10 text-white'
                                                : 'border-white/20 bg-white/5 text-zinc-400 hover:bg-white/10'
                                        }`}
                                    >
                                        Editar valores
                                        <ChevronDownIcon size={10} className={`transition-transform ${showManualEditor ? 'rotate-180' : ''}`} />
                                    </button>
                                )}
                            </div>
                        </div>
                        {/* Acordeón edición manual */}
                        {showManualEditor && program.volumeSystem === 'manual' && onUpdateProgram && (program.volumeRecommendations?.length ?? 0) > 0 && (
                            <div className="mb-4 p-4 bg-zinc-900/40 rounded-xl border border-white/5 animate-fade-in space-y-3">
                                <p className="text-[10px] text-zinc-500">Mínimo, objetivo y máximo (series/semana) por grupo muscular.</p>
                                {(program.volumeRecommendations ?? []).map((rec) => (
                                    <div key={rec.muscleGroup} className="flex flex-wrap items-center gap-2 p-2 bg-black/30 rounded-lg">
                                        <span className="text-[10px] font-bold text-white w-24 shrink-0">{rec.muscleGroup}</span>
                                        <div className="flex items-center gap-1">
                                            <input
                                                type="number"
                                                min={0}
                                                max={30}
                                                value={rec.minEffectiveVolume}
                                                onChange={(e) => {
                                                    const v = parseInt(e.target.value, 10) || 0;
                                                    const next = (program.volumeRecommendations ?? []).map(r =>
                                                        r.muscleGroup === rec.muscleGroup ? { ...r, minEffectiveVolume: v } : r
                                                    );
                                                    onUpdateProgram({ ...program, volumeRecommendations: next });
                                                }}
                                                className="w-12 bg-black/50 border border-white/20 rounded px-1.5 py-0.5 text-[10px] text-white text-center"
                                            />
                                            <span className="text-[9px] text-zinc-500">min</span>
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <input
                                                type="number"
                                                min={1}
                                                max={30}
                                                value={Math.round((rec.minEffectiveVolume + rec.maxAdaptiveVolume) / 2)}
                                                onChange={(e) => {
                                                    const target = parseInt(e.target.value, 10);
                                                    if (isNaN(target)) return;
                                                    const v = Math.max(rec.minEffectiveVolume, 2 * target - rec.minEffectiveVolume);
                                                    const next = (program.volumeRecommendations ?? []).map(r =>
                                                        r.muscleGroup === rec.muscleGroup ? { ...r, maxAdaptiveVolume: v } : r
                                                    );
                                                    onUpdateProgram({ ...program, volumeRecommendations: next });
                                                }}
                                                className="w-12 bg-black/50 border border-white/20 rounded px-1.5 py-0.5 text-[10px] text-white text-center"
                                            />
                                            <span className="text-[9px] text-zinc-500">obj</span>
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <input
                                                type="number"
                                                min={1}
                                                max={40}
                                                value={rec.maxRecoverableVolume}
                                                onChange={(e) => {
                                                    const v = parseInt(e.target.value, 10) || 20;
                                                    const next = (program.volumeRecommendations ?? []).map(r =>
                                                        r.muscleGroup === rec.muscleGroup ? { ...r, maxRecoverableVolume: v } : r
                                                    );
                                                    onUpdateProgram({ ...program, volumeRecommendations: next });
                                                }}
                                                className="w-12 bg-black/50 border border-white/20 rounded px-1.5 py-0.5 text-[10px] text-white text-center"
                                            />
                                            <span className="text-[9px] text-zinc-500">max</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        {/* Acordeón de calibración KPKN inline */}
                        {showCalibrationWizard && (setSettings || onUpdateProgram) && (
                            <div className="mb-4 animate-fade-in">
                                <AthleteProfilingWizard
                                    inline
                                    onComplete={(score) => {
                                        if (onUpdateProgram) {
                                            onUpdateProgram({
                                                ...program,
                                                volumeSystem: 'kpnk',
                                                volumeRecommendations: getKpnkVolumeRecommendations(score, settings, 'Acumulación'),
                                                volumeAlertsEnabled: program.volumeAlertsEnabled ?? true,
                                                athleteProfileScore: score,
                                            });
                                        }
                                        setSettings?.({ athleteScore: score, volumeSystem: 'kpnk' });
                                        setShowCalibrationWizard(false);
                                        addToast?.('Calibración guardada. Los umbrales de volumen usan ahora tu perfil KPKN.', 'success');
                                    }}
                                    onCancel={() => setShowCalibrationWizard(false)}
                                />
                            </div>
                        )}
                        <WorkoutVolumeAnalysis
                            program={program}
                            sessions={displayedSessions}
                            history={historyData}
                            isOnline={isOnline}
                            settings={settings}
                        />
                        <div className="mt-4">
                            <VolumeCalibrationHistoryWidget history={settings?.volumeCalibrationHistory} />
                        </div>
                    </section>
                )}

                {/* Modal de recalibración */}
                {showRecalibrationModal && recalibrationSuggestions.length > 0 && onUpdateProgram && setSettings && (
                    <VolumeRecalibrationModal
                        suggestions={recalibrationSuggestions}
                        programName={program.name}
                        onConfirm={() => {
                            applyVolumeAdjustments({
                                program,
                                suggestions: recalibrationSuggestions,
                                source: 'manual',
                                onUpdateProgram,
                                onUpdateSettings: setSettings,
                                settings: settings || undefined,
                            });
                            setShowRecalibrationModal(false);
                            addToast?.('Volumen recalibrado correctamente.', 'success');
                        }}
                        onCancel={() => setShowRecalibrationModal(false)}
                    />
                )}

                {/* ── Strength ── */}
                {activeWidgets.includes('strength') && (
                    <section>
                        <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-3">Fuerza Relativa</h3>
                        <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
                    </section>
                )}

                {/* ── Progreso 1RM ejercicios estrella ── */}
                {activeWidgets.includes('star1rm') && (
                    <section>
                        <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-3 flex items-center gap-1.5">
                            <StarIcon size={14} filled className="text-amber-400" />
                            Progreso 1RM — Ejercicios estrella
                        </h3>
                        <p className="text-[10px] text-[#48484A] mb-3">
                            Ejercicios marcados como estrella en Session Editor. 1RM estimado (Brzycki) por sesión.
                        </p>
                        {starExerciseNames.length === 0 ? (
                            <div className="bg-[#0a0a0a] rounded-xl p-4 border border-white/10 text-center">
                                <p className="text-xs text-zinc-500">No hay ejercicios estrella en este programa.</p>
                                <p className="text-[10px] text-zinc-500 mt-1">Marca ejercicios con la estrella en el editor de sesión.</p>
                            </div>
                        ) : star1RMWithGoals.length === 0 ? (
                            <div className="bg-[#0a0a0a] rounded-xl p-4 border border-white/10 text-center">
                                <p className="text-xs text-zinc-500">Aún no hay datos de 1RM para los ejercicios estrella.</p>
                                <p className="text-[10px] text-zinc-500 mt-1">Completa sesiones que los incluyan para ver el progreso.</p>
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {star1RMWithGoals.map(({ name, lastE1rm, prevE1rm, trend, goal1RM }) => {
                                    const progress = goal1RM != null && goal1RM > 0 && lastE1rm != null
                                        ? Math.min(100, (lastE1rm / goal1RM) * 100)
                                        : null;
                                    return (
                                        <div key={name} className="py-2.5 px-3 rounded-xl bg-[#0a0a0a] border border-white/10 space-y-1.5">
                                            <div className="flex items-center justify-between gap-3">
                                                <span className="text-xs font-bold text-white truncate flex-1">{name}</span>
                                                <span className="text-sm font-black text-white shrink-0">{lastE1rm != null ? `${Math.round(lastE1rm)} kg` : '—'}</span>
                                                {goal1RM != null && <span className="text-[10px] text-amber-400 shrink-0">meta {goal1RM} kg</span>}
                                                {trend === 'up' && <span className="text-[10px] font-bold text-[#00F19F] shrink-0">↑</span>}
                                                {trend === 'down' && <span className="text-[10px] font-bold text-[#FF3B30] shrink-0">↓</span>}
                                                {trend === 'same' && prevE1rm != null && <span className="text-[10px] text-zinc-500 shrink-0">—</span>}
                                            </div>
                                            {progress != null && (
                                                <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                                                    <div
                                                        className="h-full rounded-full transition-all"
                                                        style={{
                                                            width: `${progress}%`,
                                                            backgroundColor: progress >= 100 ? '#22d3ee' : progress >= 70 ? '#22d3ee' : '#fbbf24',
                                                        }}
                                                    />
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </section>
                )}

                {/* ── AUGE Banister ── */}
                {activeWidgets.includes('banister') && adaptiveCache.banister && (
                    <section>
                        <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-1">AUGE — Fitness vs Fatiga</h3>
                        <p className="text-[10px] text-zinc-500 mb-3">
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
                            <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest">Recuperación</h3>
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
                                    <span className="w-20 text-[10px] font-bold text-zinc-500 truncate text-right">{muscle}</span>
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
                        <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-3">Historial de Ejercicios</h3>
                        <ExerciseHistoryWidget program={program} history={historyData} />
                    </section>
                )}
                </div>
        </div>
    );
};

export default AnalyticsDashboard;
