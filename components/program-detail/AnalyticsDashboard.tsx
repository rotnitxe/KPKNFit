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
    setSettings?: (partial: Partial<Settings>) => void;
    onUpdateProgram?: (p: Program) => void;
    addToast?: (msg: string, type?: 'success' | 'achievement' | 'suggestion' | 'danger', title?: string, duration?: number, why?: string) => void;
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
            addToast?.('Selecciona KPKN Personalizado y completa el cuestionario abajo.', 'suggestion');
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
                addToast?.('Necesitas más sesiones con feedback para recalibrar.', 'suggestion');
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
            <div className="h-full flex items-center justify-center p-8" style={{ backgroundColor: 'var(--md-sys-color-background)' }}>
                <div className="text-center">
                    <DumbbellIcon size={32} className="mx-auto mb-4 opacity-30" style={{ color: 'var(--md-sys-color-on-surface-variant)' }} />
                    <h3 className="text-label-lg font-black uppercase tracking-[0.2em]" style={{ color: 'var(--md-sys-color-on-surface)' }}>Programa Inactivo</h3>
                    <p className="text-label-sm mt-2 opacity-60" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Inicia el programa para ver analytics.</p>
                </div>
            </div>
        );
    }

    return (
        <div style={{ backgroundColor: 'var(--md-sys-color-background)' }}>
            <div className="px-6 py-6 pb-[max(95px,calc(80px+env(safe-area-inset-bottom,0px)+12px))] space-y-10">
                <div className="text-label-sm font-black uppercase tracking-[0.2em] opacity-50" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Perspectiva Global</div>
                {/* ── Body Map ── */}
                {activeWidgets.includes('bodymap') && (
                    <section className="bg-[var(--md-sys-color-surface-container)] border border-[var(--md-sys-color-outline-variant)] rounded-3xl overflow-hidden p-6 shadow-sm">
                        <h3 className="text-label-sm font-black uppercase tracking-widest mb-4" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Mapa de Volumen</h3>
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
                            <div className="mt-4 flex flex-wrap justify-center gap-2">
                                {programDiscomforts.slice(0, 5).map((disc, idx) => (
                                    <span key={idx} className="text-[10px] font-black uppercase tracking-widest bg-[var(--md-sys-color-error-container)]/10 border border-[var(--md-sys-color-error-container)]/20 px-3 py-1 rounded-full" style={{ color: 'var(--md-sys-color-error)' }}>
                                        {disc.name} <span className="opacity-60">{disc.count}x</span>
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
                                className="w-full mb-4 py-3 px-4 rounded-2xl border border-[var(--md-sys-color-tertiary)]/30 bg-[var(--md-sys-color-tertiary-container)]/20 text-[var(--md-sys-color-tertiary)] text-label-sm font-black uppercase tracking-widest flex items-center justify-center gap-2 hover:bg-[var(--md-sys-color-tertiary-container)]/30 transition-all active:scale-[0.98]"
                            >
                                <TargetIcon size={16} />
                                {isRecalibrating ? 'Cargando…' : 'KPKN sugiere revisar tu volumen'}
                            </button>
                        )}
                        <div className="flex items-center justify-between gap-2 mb-4">
                            <h3 className="text-label-sm font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Volumen Semanal</h3>
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
                                            setSettings?.({ volumeSystem: sys as any });
                                        }
                                    }}
                                    className="text-label-sm font-black bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] rounded-xl px-3 py-1.5 text-[var(--md-sys-color-on-surface)] uppercase tracking-wider focus:ring-2 focus:ring-[var(--md-sys-color-primary)] outline-none"
                                >
                                    <option value="israetel">Israetel</option>
                                    <option value="kpnk">KPKN Personalizado</option>
                                    <option value="manual">Manual</option>
                                </select>
                                {program.volumeSystem === 'kpnk' && (
                                    <button
                                        onClick={handleRecalibrateClick}
                                        disabled={isRecalibrating}
                                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface-container-high)] text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)] text-label-sm font-black uppercase tracking-widest transition-all disabled:opacity-50 shadow-sm active:scale-95"
                                    >
                                        Re-calibrar
                                    </button>
                                )}
                                {program.volumeSystem === 'manual' && onUpdateProgram && (
                                    <button
                                        onClick={() => setShowManualEditor(prev => !prev)}
                                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-label-sm font-black uppercase tracking-wider transition-all ${showManualEditor
                                            ? 'border-[var(--md-sys-color-primary)] bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]'
                                            : 'border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface-container-high)] text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)]'
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
                            <div className="mb-6 p-6 bg-[var(--md-sys-color-surface-container-high)] rounded-[2rem] border border-[var(--md-sys-color-outline-variant)]/20 animate-fade-in space-y-4 shadow-inner">
                                <p className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-40">Series semanales por grupo muscular</p>
                                {(program.volumeRecommendations ?? []).map((rec) => (
                                    <div key={rec.muscleGroup} className="flex flex-wrap items-center gap-3 p-3 bg-[var(--md-sys-color-surface-container-low)] rounded-2xl border border-[var(--md-sys-color-outline-variant)]/10">
                                        <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] w-28 shrink-0 uppercase tracking-tight">{rec.muscleGroup}</span>
                                        <div className="flex items-center gap-2">
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
                                                className="w-14 bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)]/30 rounded-xl px-2 py-1.5 text-label-sm font-black text-[var(--md-sys-color-on-surface)] text-center focus:ring-2 focus:ring-[var(--md-sys-color-primary)] outline-none"
                                            />
                                            <span className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase opacity-30">min</span>
                                        </div>
                                        <div className="flex items-center gap-2">
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
                                                className="w-14 bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)]/30 rounded-xl px-2 py-1.5 text-label-sm font-black text-[var(--md-sys-color-on-surface)] text-center focus:ring-2 focus:ring-[var(--md-sys-color-primary)] outline-none"
                                            />
                                            <span className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase opacity-30">obj</span>
                                        </div>
                                        <div className="flex items-center gap-2">
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
                                                className="w-14 bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)]/30 rounded-xl px-2 py-1.5 text-label-sm font-black text-[var(--md-sys-color-on-surface)] text-center focus:ring-2 focus:ring-[var(--md-sys-color-primary)] outline-none"
                                            />
                                            <span className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] uppercase opacity-30">max</span>
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
                        <h3 className="text-label-sm font-black uppercase tracking-widest mb-4" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Fuerza Relativa</h3>
                        <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
                    </section>
                )}

                {/* ── Progreso 1RM ejercicios estrella ── */}
                {activeWidgets.includes('star1rm') && (
                    <section>
                        <h3 className="text-label-sm font-black uppercase tracking-widest mb-4 flex items-center gap-2" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                            <StarIcon size={16} filled style={{ color: 'var(--md-sys-color-tertiary)' }} />
                            Progreso 1RM — Ejercicios estrella
                        </h3>
                        <p className="text-label-sm mb-4 opacity-60" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                            Ejercicios marcados como estrella en Session Editor. 1RM estimado (Brzycki) por sesión.
                        </p>
                        {starExerciseNames.length === 0 ? (
                            <div className="bg-[var(--md-sys-color-surface-container)] rounded-2xl p-6 border border-[var(--md-sys-color-outline-variant)] text-center">
                                <p className="text-label-md font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface)' }}>No hay ejercicios estrella</p>
                                <p className="text-label-sm mt-1 opacity-60" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Márcalos con la estrella en el editor de sesión.</p>
                            </div>
                        ) : star1RMWithGoals.length === 0 ? (
                            <div className="bg-[var(--md-sys-color-surface-container)] rounded-2xl p-6 border border-[var(--md-sys-color-outline-variant)] text-center">
                                <p className="text-label-md font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface)' }}>Esperando datos</p>
                                <p className="text-label-sm mt-1 opacity-60" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Completa sesiones que los incluyan para ver el progreso.</p>
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {star1RMWithGoals.map(({ name, lastE1rm, prevE1rm, trend, goal1RM }) => {
                                    const progress = goal1RM != null && goal1RM > 0 && lastE1rm != null
                                        ? Math.min(100, (lastE1rm / goal1RM) * 100)
                                        : null;
                                    return (
                                        <div key={name} className="py-3.5 px-4 rounded-2xl bg-[var(--md-sys-color-surface-container)] border border-[var(--md-sys-color-outline-variant)] space-y-2.5">
                                            <div className="flex items-center justify-between gap-3">
                                                <span className="text-title-sm font-black uppercase tracking-tight truncate flex-1" style={{ color: 'var(--md-sys-color-on-surface)' }}>{name}</span>
                                                <span className="text-title-md font-black shrink-0" style={{ color: 'var(--md-sys-color-primary)' }}>{lastE1rm != null ? `${Math.round(lastE1rm)} kg` : '—'}</span>
                                                {goal1RM != null && <span className="text-label-sm font-black uppercase tracking-widest shrink-0" style={{ color: 'var(--md-sys-color-tertiary)' }}>meta {goal1RM}</span>}
                                                {trend === 'up' && <span className="text-title-sm font-black text-[var(--md-sys-color-primary)] shrink-0">↑</span>}
                                                {trend === 'down' && <span className="text-title-sm font-black text-[var(--md-sys-color-error)] shrink-0">↓</span>}
                                            </div>
                                            {progress != null && (
                                                <div className="h-2 bg-[var(--md-sys-color-surface-variant)] rounded-full overflow-hidden">
                                                    <div
                                                        className="h-full rounded-full transition-all"
                                                        style={{
                                                            width: `${progress}%`,
                                                            backgroundColor: progress >= 100 ? 'var(--md-sys-color-primary)' : progress >= 70 ? 'var(--md-sys-color-primary)' : 'var(--md-sys-color-tertiary)',
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
                        <h3 className="text-label-sm font-black uppercase tracking-widest mb-1" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>AUGE — Fitness vs Fatiga</h3>
                        <p className="text-label-sm mb-4 opacity-60" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                            {adaptiveCache.banister.verdict || 'Modelo Banister activo'}
                        </p>
                        <div className="bg-[var(--md-sys-color-surface-container)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4">
                            <BanisterTrend systemData={adaptiveCache.banister?.systems?.muscular || null} />
                        </div>
                    </section>
                )}

                {/* ── Recovery Map ── */}
                {activeWidgets.includes('recovery') && recoveryData.length > 0 && (
                    <section>
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-label-sm font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Recuperación</h3>
                            <span className="text-[10px] font-black uppercase tracking-widest px-3 py-1 rounded-full" style={{
                                backgroundColor: confidenceLabel === 'Alta' ? 'var(--md-sys-color-primary-container)' : confidenceLabel === 'Media' ? 'var(--md-sys-color-tertiary-container)' : 'var(--md-sys-color-error-container)',
                                color: confidenceLabel === 'Alta' ? 'var(--md-sys-color-on-primary-container)' : confidenceLabel === 'Media' ? 'var(--md-sys-color-on-tertiary-container)' : 'var(--md-sys-color-on-error-container)',
                                border: '1px solid var(--md-sys-color-outline-variant)'
                            }}>
                                Confianza {confidenceLabel}
                            </span>
                        </div>
                        <div className="space-y-1">
                            {recoveryData.map(([muscle, hours]) => (
                                <div key={muscle} className="flex items-center gap-2">
                                    <span className="w-24 text-label-sm font-black uppercase tracking-widest opacity-60 text-right" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>{muscle}</span>
                                    <div className="flex-1 h-3 bg-[var(--md-sys-color-surface-container-high)] rounded-full overflow-hidden">
                                        <div
                                            className="h-full rounded-full shadow-sm"
                                            style={{
                                                width: `${Math.min(100, (hours / 96) * 100)}%`,
                                                backgroundColor: hours > 72 ? 'var(--md-sys-color-error)' : hours > 48 ? 'var(--md-sys-color-tertiary)' : 'var(--md-sys-color-primary)',
                                            }}
                                        />
                                    </div>
                                    <span className="w-10 text-label-sm font-black text-right" style={{ color: 'var(--md-sys-color-on-surface)' }}>{Math.round(hours)}h</span>
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
                        <h3 className="text-label-sm font-black uppercase tracking-widest mb-4" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Historial de Ejercicios</h3>
                        <ExerciseHistoryWidget program={program} history={historyData} />
                    </section>
                )}
            </div>
        </div>
    );
};

export default AnalyticsDashboard;
