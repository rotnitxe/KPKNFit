import React, { useState, useMemo } from 'react';
import { motion } from 'framer-motion';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo, Settings, PostSessionFeedback } from '../../types';
import { ActivityIcon, DumbbellIcon, XIcon, TargetIcon, ChevronDownIcon, StarIcon } from '../icons';
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
import VolumeCalibrationHistoryWidget from './VolumeCalibrationHistoryWidget';
import VolumeRecalibrationModal from './VolumeRecalibrationModal';
import { MuscleStatsPanel } from '../MuscleStatsPanel';
import {
    getSuggestedVolumeAdjustments,
    applyVolumeAdjustments,
    shouldSuggestRecalibration,
    VolumeSuggestion,
} from '../../services/volumeCalibrationService';

const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
        opacity: 1,
        transition: {
            staggerChildren: 0.1
        }
    }
};

const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: { opacity: 1, y: 0 }
};

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
            <div className="h-full flex items-center justify-center p-8 bg-[#FEF7FF]">
                <div className="text-center">
                    <div className="w-16 h-16 rounded-full bg-black/5 flex items-center justify-center mx-auto mb-6">
                        <DumbbellIcon size={32} className="opacity-20 text-black" />
                    </div>
                    <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-black/40">Programa Inactivo</h3>
                    <p className="text-[12px] mt-2 font-medium text-black/60">Inicia el programa para ver analíticas.</p>
                </div>
            </div>
        );
    }

    return (
        <motion.div
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            className="px-6 py-6 pb-[max(95px,calc(80px+env(safe-area-inset-bottom,0px)+12px))] space-y-8 bg-[#FEF7FF]"
        >
            <motion.div variants={itemVariants} className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 mb-2">Perspectiva Global</motion.div>

            {/* ── Body Map ── */}
            {activeWidgets.includes('bodymap') && (
                <motion.section variants={itemVariants} className="bg-white rounded-[32px] p-8 shadow-xl shadow-black/5 border border-black/[0.03] overflow-hidden relative">
                    <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500/5 blur-3xl rounded-full -mr-16 -mt-16" />
                    <h3 className="text-sm font-black text-black uppercase tracking-tight mb-8">Mapa de Volumen</h3>
                    <div className="flex flex-col gap-8 items-center bg-black/[0.02] rounded-[24px] p-6 border border-black/[0.03]">
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
                        <div className="mt-8 flex flex-wrap justify-center gap-2">
                            {programDiscomforts.slice(0, 5).map((disc, idx) => (
                                <span key={idx} className="text-[10px] font-black uppercase tracking-widest bg-red-50 border border-red-100 px-4 py-2 rounded-full text-red-600">
                                    {disc.name} <span className="opacity-50 ml-1">{disc.count}x</span>
                                </span>
                            ))}
                        </div>
                    )}
                </motion.section>
            )}

            {/* ── Volume Analysis ── */}
            {activeWidgets.includes('volume') && (
                <motion.section variants={itemVariants} className="bg-white rounded-[32px] p-8 shadow-xl shadow-black/5 border border-black/[0.03]">
                    {suggestRecalibration && program.volumeSystem === 'kpnk' && (
                        <button
                            onClick={handleRecalibrateClick}
                            disabled={isRecalibrating}
                            className="w-full mb-8 py-5 px-6 rounded-[24px] bg-[#0061A4] text-white text-[10px] font-black uppercase tracking-widest flex items-center justify-center gap-3 transition-all shadow-lg shadow-[#0061A4]/20 active:scale-[0.98]"
                        >
                            <TargetIcon size={18} />
                            {isRecalibrating ? 'Calculando sugerencias…' : 'KPKN sugiere revisión'}
                        </button>
                    )}
                    <div className="flex flex-col gap-6 mb-8">
                        <div className="flex items-center justify-between">
                            <h3 className="text-sm font-black text-black uppercase tracking-tight">Volumen Semanal</h3>
                            <div className="w-10 h-10 rounded-full bg-black/[0.03] flex items-center justify-center">
                                <ActivityIcon size={18} className="text-black/40" />
                            </div>
                        </div>

                        <div className="flex flex-wrap items-center gap-3">
                            <div className="relative flex-1 min-w-[180px]">
                                <select
                                    value={program.volumeSystem ?? settings?.volumeSystem ?? 'kpnk'}
                                    onChange={(e) => {
                                        const sys = e.target.value as 'israetel' | 'kpnk' | 'manual';
                                        if (onUpdateProgram && sys === 'israetel') {
                                            onUpdateProgram({ ...program, volumeSystem: 'israetel', volumeRecommendations: getIsraetelVolumeRecommendations(), volumeAlertsEnabled: program.volumeAlertsEnabled ?? true });
                                            addToast?.('Guía Israetel aplicada.', 'success');
                                        } else if (onUpdateProgram && sys === 'manual') {
                                            onUpdateProgram({ ...program, volumeSystem: 'manual', volumeRecommendations: getIsraetelVolumeRecommendations().map(r => ({ ...r })), volumeAlertsEnabled: program.volumeAlertsEnabled ?? true });
                                            setShowManualEditor(true);
                                            addToast?.('Modo Manual activado.', 'success');
                                        } else if (sys === 'kpnk') {
                                            setShowCalibrationWizard(true);
                                        } else if (!onUpdateProgram) {
                                            setSettings?.({ volumeSystem: sys as any });
                                        }
                                    }}
                                    className="w-full h-14 text-[11px] font-black uppercase tracking-widest bg-black/[0.03] border-none rounded-2xl px-5 text-black appearance-none focus:ring-2 focus:ring-black/5 outline-none transition-all"
                                >
                                    <option value="israetel">Israetel</option>
                                    <option value="kpnk">KPKN Personalizado</option>
                                    <option value="manual">Manual</option>
                                </select>
                                <div className="absolute right-5 top-1/2 -translate-y-1/2 pointer-events-none opacity-30">
                                    <ChevronDownIcon size={14} />
                                </div>
                            </div>

                            {program.volumeSystem === 'kpnk' && (
                                <button
                                    onClick={handleRecalibrateClick}
                                    disabled={isRecalibrating}
                                    className="h-14 px-6 rounded-2xl border border-black/5 text-[10px] font-black uppercase tracking-widest text-black/60 hover:text-black hover:bg-black/5 transition-all disabled:opacity-50"
                                >
                                    Re-calibrar
                                </button>
                            )}
                        </div>
                    </div>

                    {showManualEditor && program.volumeSystem === 'manual' && onUpdateProgram && (
                        <div className="mb-8 p-6 bg-black/[0.02] rounded-[24px] border border-black/5 space-y-4">
                            {(program.volumeRecommendations ?? []).map((rec) => (
                                <div key={rec.muscleGroup} className="flex flex-wrap items-center gap-4 p-4 bg-white rounded-2xl shadow-sm border border-black/[0.03]">
                                    <span className="text-[10px] font-black text-black w-24 uppercase tracking-tight">{rec.muscleGroup}</span>
                                    <div className="flex gap-2">
                                        {['min', 'obj', 'max'].map((type) => (
                                            <div key={type} className="flex flex-col items-center gap-1">
                                                <input
                                                    type="number"
                                                    value={type === 'min' ? rec.minEffectiveVolume : type === 'obj' ? Math.round((rec.minEffectiveVolume + rec.maxAdaptiveVolume) / 2) : rec.maxRecoverableVolume}
                                                    onChange={(e) => {
                                                        const v = parseInt(e.target.value, 10) || 0;
                                                        const next = (program.volumeRecommendations ?? []).map(r => {
                                                            if (r.muscleGroup !== rec.muscleGroup) return r;
                                                            if (type === 'min') return { ...r, minEffectiveVolume: v };
                                                            if (type === 'max') return { ...r, maxRecoverableVolume: v };
                                                            return { ...r, maxAdaptiveVolume: Math.max(r.minEffectiveVolume, 2 * v - r.minEffectiveVolume) };
                                                        });
                                                        onUpdateProgram({ ...program, volumeRecommendations: next });
                                                    }}
                                                    className="w-12 h-10 bg-black/[0.03] rounded-lg text-[11px] font-black text-center focus:ring-1 focus:ring-black/10 outline-none"
                                                />
                                                <span className="text-[8px] font-black uppercase opacity-20">{type}</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {showCalibrationWizard && (
                        <div className="mb-8 animate-in fade-in slide-in-from-top-4 duration-300">
                            <AthleteProfilingWizard
                                inline
                                onComplete={(score) => {
                                    if (onUpdateProgram) {
                                        onUpdateProgram({ ...program, volumeSystem: 'kpnk', volumeRecommendations: getKpnkVolumeRecommendations(score, settings, 'Acumulación'), volumeAlertsEnabled: program.volumeAlertsEnabled ?? true, athleteProfileScore: score });
                                    }
                                    setSettings?.({ athleteScore: score, volumeSystem: 'kpnk' });
                                    setShowCalibrationWizard(false);
                                    addToast?.('Calibración guardada.', 'success');
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
                    <div className="mt-8 pt-8 border-t border-black/[0.03]">
                        <VolumeCalibrationHistoryWidget history={settings?.volumeCalibrationHistory} />
                    </div>
                </motion.section>
            )}

            {/* ── Strength ── */}
            {activeWidgets.includes('strength') && (
                <motion.section variants={itemVariants} className="bg-white rounded-[32px] p-8 shadow-xl shadow-black/5 border border-black/[0.03]">
                    <h3 className="text-sm font-black text-black uppercase tracking-tight mb-8">Fuerza Relativa</h3>
                    <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
                </motion.section>
            )}

            {/* ── 1RM Progress ── */}
            {activeWidgets.includes('star1rm') && (
                <motion.section variants={itemVariants} className="bg-white rounded-[32px] p-8 shadow-xl shadow-black/5 border border-black/[0.03] relative overflow-hidden">
                    <div className="absolute top-0 right-0 w-32 h-32 bg-yellow-500/5 blur-3xl rounded-full -mr-16 -mt-16" />
                    <div className="flex items-center justify-between mb-8">
                        <div className="flex items-center gap-3">
                            <StarIcon size={20} filled className="text-yellow-500" />
                            <h3 className="text-sm font-black text-black uppercase tracking-tight">E1RM Estrellas</h3>
                        </div>
                    </div>

                    {starExerciseNames.length === 0 ? (
                        <div className="bg-black/[0.02] rounded-[24px] p-10 border border-black/5 text-center">
                            <p className="text-[12px] font-black text-black/20 uppercase tracking-widest">Sin objetivos estrella</p>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {star1RMWithGoals.map(({ name, lastE1rm, prevE1rm, trend, goal1RM }) => {
                                const progress = goal1RM != null && goal1RM > 0 && lastE1rm != null ? Math.min(100, (lastE1rm / goal1RM) * 100) : null;
                                return (
                                    <div key={name} className="p-5 rounded-[24px] bg-black/[0.02] border border-black/5 relative group hover:bg-white hover:shadow-lg transition-all duration-300">
                                        <div className="flex items-center justify-between gap-4 mb-4">
                                            <span className="text-[11px] font-black text-black uppercase tracking-tight truncate">{name}</span>
                                            <div className="flex items-center gap-3">
                                                <span className="text-lg font-black text-black">{lastE1rm != null ? Math.round(lastE1rm) : '—'}<span className="text-[10px] ml-1 opacity-30">KG</span></span>
                                                {trend === 'up' && <div className="w-6 h-6 rounded-full bg-green-50 flex items-center justify-center text-green-500 text-xs font-black">↑</div>}
                                            </div>
                                        </div>
                                        {progress != null && (
                                            <div className="space-y-2">
                                                <div className="h-2 bg-black/5 rounded-full overflow-hidden">
                                                    <motion.div
                                                        initial={{ width: 0 }}
                                                        animate={{ width: `${progress}%` }}
                                                        transition={{ duration: 1, ease: "easeOut" }}
                                                        className="h-full rounded-full bg-gradient-to-r from-blue-500 to-blue-400"
                                                    />
                                                </div>
                                                <div className="flex justify-between items-center text-[9px] font-black uppercase tracking-widest opacity-30">
                                                    <span>Progreso</span>
                                                    <span>Meta: {goal1RM}kg</span>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </motion.section>
            )}

            {/* ── AUGE Banister ── */}
            {activeWidgets.includes('banister') && adaptiveCache.banister && (
                <motion.section variants={itemVariants} className="bg-white rounded-[32px] p-8 shadow-xl shadow-black/5 border border-black/[0.03]">
                    <div className="mb-8">
                        <h3 className="text-sm font-black text-black uppercase tracking-tight mb-2">AUGE — Fitness vs Fatiga</h3>
                        <p className="text-[10px] font-black text-black/40 uppercase tracking-widest leading-relaxed">
                            {adaptiveCache.banister.verdict || 'Análisis de carga activa'}
                        </p>
                    </div>
                    <div className="bg-black/[0.02] border border-black/5 rounded-[24px] p-6 shadow-inner">
                        <BanisterTrend systemData={adaptiveCache.banister?.systems?.muscular || null} />
                    </div>
                </motion.section>
            )}

            {/* ── Recovery ── */}
            {activeWidgets.includes('recovery') && recoveryData.length > 0 && (
                <motion.section variants={itemVariants} className="bg-white rounded-[32px] p-8 shadow-xl shadow-black/5 border border-black/[0.03]">
                    <div className="flex items-center justify-between mb-8">
                        <h3 className="text-sm font-black text-black uppercase tracking-tight">Recuperación</h3>
                        <div className={`px-4 py-2 rounded-full text-[9px] font-black uppercase tracking-widest border border-black/5 ${confidenceLabel === 'Alta' ? 'bg-green-50 text-green-600' : confidenceLabel === 'Media' ? 'bg-yellow-50 text-yellow-600' : 'bg-red-50 text-red-600'
                            }`}>
                            Confianza {confidenceLabel}
                        </div>
                    </div>
                    <div className="space-y-5">
                        {recoveryData.map(([muscle, hours]) => (
                            <div key={muscle} className="flex flex-col gap-2">
                                <div className="flex justify-between items-center">
                                    <span className="text-[10px] font-black text-black uppercase tracking-tight">{muscle}</span>
                                    <span className="text-[11px] font-black text-black">{Math.round(hours)}<span className="text-[9px] ml-1 opacity-30">HRS</span></span>
                                </div>
                                <div className="h-2 bg-black/5 rounded-full overflow-hidden">
                                    <motion.div
                                        initial={{ width: 0 }}
                                        animate={{ width: `${Math.min(100, (hours / 96) * 100)}%` }}
                                        className={`h-full rounded-full ${hours > 72 ? 'bg-red-400' : hours > 48 ? 'bg-yellow-400' : 'bg-green-400'}`}
                                    />
                                </div>
                            </div>
                        ))}
                    </div>
                    {adaptiveCache.selfImprovement && (
                        <div className="mt-10 bg-black/[0.02] border border-black/5 rounded-[24px] p-6">
                            <SelfImprovementScore
                                score={adaptiveCache.selfImprovement.overall_prediction_score}
                                trend={adaptiveCache.selfImprovement.improvement_trend}
                                recommendations={adaptiveCache.selfImprovement.recommendations}
                            />
                        </div>
                    )}
                </motion.section>
            )}

            {/* ── History ── */}
            {activeWidgets.includes('history') && (
                <motion.section variants={itemVariants} className="bg-white rounded-[32px] p-8 shadow-xl shadow-black/5 border border-black/[0.03]">
                    <h3 className="text-sm font-black text-black uppercase tracking-tight mb-8">Historial</h3>
                    <ExerciseHistoryWidget program={program} history={historyData} />
                </motion.section>
            )}
        </motion.div>
    );
};

export default AnalyticsDashboard;
