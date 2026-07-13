// components/home/HomeCardPageView.tsx
// Páginas dedicadas para cada tarjeta del Home (mismo diseño que Tú)

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { ArrowLeftIcon, SearchIcon, ChevronRightIcon } from '../icons';
import { Exercise, Program, WorkoutLog } from '../../types';
import { RelativeStrengthAndBasicsWidget } from '../RelativeStrengthAndBasicsWidget';
import { calculateBrzycki1RM, calculateIPFGLPoints } from '../../utils/calculations';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getLocalDateString } from '../../utils/dateUtils';
import { calculateDailyCalorieGoal } from '../../utils/calorieFormulas';
import WeightVsTargetChart from '../WeightVsTargetChart';
import BodyFatChart from '../BodyFatChart';
import MuscleMassChart from '../MuscleMassChart';
import FFMIChart from '../FFMIChart';
import ErrorBoundary from '../ui/ErrorBoundary';

const CARD_TITLES: Record<string, string> = {
    'exercise-history': 'Historial por Ejercicio',
    'star-1rm': '1RM Estrella',
    'relative-strength': 'Fuerza Relativa',
    'event-marks': 'Marcas por Evento',
    'ipf-gl': 'IPF GL',
    'macros': 'Macros · Composición',
    'evolution': 'Evolución hacia Meta',
    'calories-history': 'Historial de Calorías',
    'body-measures': 'Evolución de Medidas Corporales',
    'ffmi-imc': 'Evolución FFMI e IMC',
};

interface HomeCardPageViewProps {
    cardType: string;
    onBack: () => void;
}

export const HomeCardPageView: React.FC<HomeCardPageViewProps> = ({ cardType, onBack }) => {
    const { programs, activeProgramState, history, settings, bodyProgress, nutritionPlans, activeNutritionPlanId, nutritionLogs } = useAppState();
    const { handleBack } = useAppDispatch();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId) || programs[0];
    const title = CARD_TITLES[cardType] ?? cardType;

    const goBack = () => {
        if (onBack) onBack();
        else handleBack();
    };

    return (
        <div className="fixed inset-0 z-[100] bg-[#ECE6F0] text-white flex flex-col safe-area-root">
            {/* Header — mismo lenguaje que Home / CompactHeroBanner */}
            <div
                className="shrink-0 flex items-center gap-3 px-4 py-3"
                style={{ backgroundColor: '#1a1a1a', paddingTop: 'max(0.75rem, env(safe-area-inset-top, 0px))' }}
            >
                <button
                    onClick={goBack}
                    className="w-9 h-9 shrink-0 rounded-lg bg-white/5 flex items-center justify-center text-[#49454F] hover:text-white hover:bg-white/[0.08] transition-colors"
                    aria-label="Volver"
                >
                    <ArrowLeftIcon size={18} className="rotate-90" />
                </button>
                <h1 className="text-base font-black uppercase tracking-tight flex-1 text-white">{title}</h1>
            </div>

            {/* Content — max-w-md como Home, mismo padding */}
            <div className="flex-1 overflow-y-auto w-full max-w-md mx-auto px-6 py-5 pb-24">
                {cardType === 'exercise-history' && (
                    <ExerciseHistoryPage program={activeProgram} history={history || []} onBack={goBack} />
                )}
                {cardType === 'star-1rm' && <Star1RMPage program={activeProgram} />}
                {cardType === 'relative-strength' && <RelativeStrengthPage history={history || []} />}
                {cardType === 'event-marks' && <EventMarksPage program={activeProgram} />}
                {cardType === 'ipf-gl' && <IPFGLPage history={history || []} />}
                {cardType === 'macros' && <MacrosPage />}
                {cardType === 'evolution' && <EvolutionPage />}
                {cardType === 'calories-history' && <CaloriesHistoryPage />}
                {cardType === 'body-measures' && <BodyMeasuresPage />}
                {cardType === 'ffmi-imc' && <FFMIBMIPage />}
            </div>
        </div>
    );
};

// ─── Páginas dedicadas ───────────────────────────────────────────────────────

const BASIC_PATTERNS = {
    squat: { pl: ['trasera barra alta', 'trasera barra baja', 'high bar', 'low bar'] },
    bench: { pl: ['press banca', 'bench press'] },
    deadlift: { pl: ['convencional', 'sumo'] },
};

const ExerciseHistoryDetailInline: React.FC<{
    exercise: Exercise;
    programId: string;
    history: WorkoutLog[];
    settings: any;
    onBack: () => void;
}> = ({ exercise, programId, history, settings, onBack }) => {
    const exerciseHistory = useMemo(() => {
        const matchExercise = (ce: any) => {
            if (ce.exerciseDbId && exercise.exerciseDbId && ce.exerciseDbId === exercise.exerciseDbId) return true;
            if (ce.exerciseId === exercise.id) return true;
            const ceName = (ce.exerciseName || '').trim().toLowerCase();
            const exName = (exercise.name || '').trim().toLowerCase();
            return ceName && exName && ceName === exName;
        };
        const extract = (logs: WorkoutLog[]) =>
            logs.map(log => {
                const completedEx = log.completedExercises?.find(matchExercise);
                return completedEx ? { date: log.date, sets: completedEx.sets } : null;
            }).filter((l): l is { date: string; sets: any[] } => l != null)
              .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
        const fromProgram = extract(history.filter(l => l.programId === programId));
        return fromProgram.length > 0 ? fromProgram : extract(history);
    }, [history, programId, exercise]);

    const chartData = useMemo(() =>
        [...exerciseHistory].reverse().map(log => {
            const maxWeight = Math.max(...(log.sets.map((s: any) => s.weight || 0)));
            return {
                date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                'Peso Máximo': maxWeight > 0 ? maxWeight : null,
            };
        }).filter((d: any) => d['Peso Máximo'] != null),
        [exerciseHistory]
    );

    return (
        <div className="space-y-5">
            <button onClick={onBack} className="text-[10px] font-black text-[#49454F] uppercase tracking-widest hover:text-[#49454F] -ml-1">← Lista</button>
            <h3 className="text-sm font-black text-white uppercase tracking-tight">{exercise.name}</h3>
            {chartData.length > 1 ? (
                <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-4">
                    <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-4">Progresión (Peso Máx)</p>
                    <div className="h-[180px] w-full">
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={chartData}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" vertical={false} />
                                <XAxis dataKey="date" stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} />
                                <YAxis stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} unit={settings.weightUnit} domain={['dataMin - 5', 'auto']} />
                                <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '6px', fontSize: '11px' }} itemStyle={{ color: '#fff' }} />
                                <Line type="monotone" dataKey="Peso Máximo" stroke="rgba(255,255,255,0.7)" strokeWidth={2} dot={{ r: 2, fill: '#0a0a0a', strokeWidth: 1 }} />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </div>
            ) : (
                <p className="text-[11px] text-[#49454F] py-4">Necesitas más de una sesión para ver la gráfica.</p>
            )}
            <div className="space-y-2">
                {exerciseHistory.map((log, i) => (
                    <div key={i} className="bg-[#FEF7FF] border border-[#E6E0E9] p-3 rounded-xl">
                        <p className="font-black text-[#49454F] text-[10px] uppercase mb-2 tracking-widest">
                            {new Date(log.date).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'short' })}
                        </p>
                        <ul className="space-y-1">
                            {log.sets.map((set: any, si: number) => {
                                if (set.completedReps == null || set.weight == null) return null;
                                return (
                                    <li key={si} className="flex justify-between text-xs text-gray-400">
                                        <span className="font-mono text-white">{set.weight} {settings.weightUnit} x {set.completedReps}</span>
                                        {set.completedRPE && <span className="text-[9px] font-bold text-[#49454F]">RPE {set.completedRPE}</span>}
                                    </li>
                                );
                            })}
                        </ul>
                    </div>
                ))}
                {exerciseHistory.length === 0 && <p className="text-[11px] text-[#49454F] py-4">No hay registros aún.</p>}
            </div>
        </div>
    );
};

function findBest1RM(h: WorkoutLog[], patterns: string[]) {
    let best = 0;
    h.forEach((log: any) => {
        (log.completedExercises || []).forEach((ex: any) => {
            const name = (ex.exerciseName || ex.name || '').toLowerCase();
            if (!patterns.some(p => name.includes(p))) return;
            (ex.sets || []).forEach((s: any) => {
                if (s.weight && (s.completedReps ?? s.reps)) {
                    const rm = calculateBrzycki1RM(s.weight, s.completedReps ?? s.reps);
                    if (rm > best) best = rm;
                }
            });
        });
    });
    return best;
}

const ExerciseHistoryPage: React.FC<{ program: Program | undefined; history: WorkoutLog[]; onBack: () => void }> = ({ program, history, onBack }) => {
    const { settings } = useAppState();
    const [selectedExercise, setSelectedExercise] = useState<Exercise | null>(null);
    const [searchTerm, setSearchTerm] = useState('');

    if (!program) {
        return (
            <p className="text-[11px] text-[#49454F] py-8">Crea o activa un programa para ver el historial por ejercicio.</p>
        );
    }

    if (selectedExercise) {
        return (
            <ExerciseHistoryDetailInline
                exercise={selectedExercise}
                programId={program.id}
                history={history}
                settings={settings}
                onBack={() => setSelectedExercise(null)}
            />
        );
    }

    const uniqueExercisesMap = new Map<string, Exercise>();
    program.macrocycles?.forEach(m =>
        (m.blocks || []).forEach(b =>
            b.mesocycles?.forEach(me =>
                me.weeks?.forEach(w =>
                    w.sessions?.forEach(s =>
                        (s.exercises || []).forEach((e: any) => {
                            if (!uniqueExercisesMap.has(e.name)) uniqueExercisesMap.set(e.name, e);
                        })
                    )
                )
            )
        )
    );
    const exercises = Array.from(uniqueExercisesMap.values())
        .filter(e => e.name.toLowerCase().includes(searchTerm.toLowerCase()))
        .sort((a, b) => a.name.localeCompare(b.name));

    return (
        <div className="flex flex-col gap-4">
            <div className="relative">
                <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-[#49454F]" size={14} />
                <input
                    type="text"
                    placeholder="Buscar ejercicio..."
                    value={searchTerm}
                    onChange={e => setSearchTerm(e.target.value)}
                    className="w-full bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl py-2.5 pl-9 pr-4 text-[13px] text-white placeholder:text-zinc-600 focus:outline-none focus:border-white/20"
                />
            </div>
            <div className="space-y-1.5">
                {exercises.map(exercise => (
                    <button
                        key={exercise.id}
                        onClick={() => setSelectedExercise(exercise)}
                        className="w-full flex items-center justify-between p-3.5 bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl hover:border-white/15 transition-colors group text-left"
                    >
                        <span className="font-semibold text-zinc-300 text-[13px] group-hover:text-white">{exercise.name}</span>
                        <ChevronRightIcon size={14} className="text-zinc-600 group-hover:text-[#49454F] shrink-0" />
                    </button>
                ))}
                {exercises.length === 0 && (
                    <p className="text-[11px] text-[#49454F] py-6">No se encontraron ejercicios.</p>
                )}
            </div>
        </div>
    );
};

const Star1RMPage: React.FC<{ program: Program | undefined }> = ({ program }) => {
    const { count, list } = useMemo(() => {
        let c = 0;
        const items: { name: string; goal?: number }[] = [];
        if (!program) return { count: 0, list: [] };
        program.macrocycles?.forEach(m =>
            m.blocks?.forEach(b =>
                b.mesocycles?.forEach(me =>
                    me.weeks?.forEach(w =>
                        w.sessions?.forEach(s => {
                            const exs = (s.parts?.length ? s.parts.flatMap((p: any) => p.exercises ?? []) : s.exercises ?? []) || [];
                            exs.forEach((ex: any) => {
                                if (ex.isStarTarget && ex.name) {
                                    c++;
                                    items.push({ name: ex.name, goal: ex.goal1RM });
                                }
                            });
                        })
                    )
                )
            )
        );
        return { count: c, list: items };
    }, [program]);

    if (!program || count === 0) {
        return (
            <p className="text-[11px] text-[#49454F] py-8">Marca ejercicios estrella en el editor de sesiones para ver tu progreso 1RM.</p>
        );
    }

    return (
        <div className="space-y-4">
            <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-4">
                <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-1">Ejercicios estrella</p>
                <p className="text-xl font-black text-white tabular-nums">{count}</p>
            </div>
            <div className="space-y-1.5">
                {list.map((item, i) => (
                    <div
                        key={i}
                        className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-3.5 flex justify-between items-center"
                    >
                        <span className="font-semibold text-white text-[13px]">{item.name}</span>
                        {item.goal != null && item.goal > 0 && (
                            <span className="text-[10px] font-bold text-[#49454F] tabular-nums">{item.goal} kg</span>
                        )}
                    </div>
                ))}
            </div>
            <p className="text-[10px] text-[#49454F]">
                Abre el detalle de tu programa para ver gráficas de progreso por ejercicio.
            </p>
        </div>
    );
};

const RelativeStrengthPage: React.FC<{ history: WorkoutLog[] }> = ({ history }) => {
    const displayedSessions = useMemo(() => {
        const all = history.slice(0, 30).flatMap((log: any) =>
            (log.completedExercises || []).map(() => ({}))
        );
        return Array(Math.min(10, all.length)).fill({});
    }, [history]);

    return (
        <div className="space-y-4">
            <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
        </div>
    );
};

const EventMarksPage: React.FC<{ program: Program | undefined }> = ({ program }) => {
    const events = (program as any)?.events || [];

    if (!program) {
        return <p className="text-[11px] text-[#49454F] py-8">Activa un programa para ver eventos.</p>;
    }

    if (events.length === 0) {
        return (
            <p className="text-[11px] text-[#49454F] py-8">Añade eventos o fechas clave en el editor de tu programa.</p>
        );
    }

    return (
        <div className="space-y-1.5">
            {events.map((e: any, i: number) => (
                <div key={e.id || i} className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-3.5">
                    <p className="font-semibold text-white text-[13px]">{e.title}</p>
                    <p className="text-[10px] text-[#49454F] mt-0.5 tracking-wider">
                        Semana {e.calculatedWeek ?? '?'} · {e.type === '1rm_test' ? 'Test 1RM' : e.type || 'Evento'}
                    </p>
                </div>
            ))}
        </div>
    );
};

const IPFGLPage: React.FC<{ history: WorkoutLog[] }> = ({ history }) => {
    const { programs, activeProgramState, settings } = useAppState();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const { points, isPowerlifter, total } = useMemo(() => {
        const mode = (activeProgram as any)?.mode;
        const isPL = mode === 'powerlifting' || mode === 'powerbuilding';
        if (!isPL) return { points: 0, isPowerlifter: false, total: 0 };

        const squat = findBest1RM(history, [...BASIC_PATTERNS.squat.pl]);
        const bench = findBest1RM(history, [...BASIC_PATTERNS.bench.pl]);
        const deadlift = findBest1RM(history, [...BASIC_PATTERNS.deadlift.pl]);
        const sum = squat + bench + deadlift;
        const weight = settings?.userVitals?.weight || 0;
        const gender = settings?.userVitals?.gender || 'male';
        const unit = settings?.weightUnit || 'kg';
        const pts = sum > 0 && weight > 0
            ? calculateIPFGLPoints(sum, weight, { gender, equipment: 'classic', lift: 'total', weightUnit: unit as 'kg' | 'lbs' })
            : 0;
        return { points: pts, isPowerlifter: true, total: sum };
    }, [history, activeProgram, settings]);

    if (!isPowerlifter) {
        return (
            <p className="text-[11px] text-[#49454F] py-8">IPF GL está disponible para programas de powerlifting o powerbuilding.</p>
        );
    }

    return (
        <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-5">
            <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-2">Puntos IPF GL</p>
            <p className="text-3xl font-black text-white tabular-nums tracking-tight">{Math.round(points)}</p>
            <p className="text-[11px] text-[#49454F] mt-2">Total: {total} kg (S+B+P)</p>
        </div>
    );
};

const MacrosPage: React.FC = () => {
    const { bodyProgress, settings } = useAppState();
    const lastLog = bodyProgress?.length ? bodyProgress[bodyProgress.length - 1] : null;
    const calorieGoal = useMemo(
        () => calculateDailyCalorieGoal(settings, settings.calorieGoalConfig),
        [settings]
    );

    return (
        <div className="space-y-4">
            <div className="grid grid-cols-2 gap-2">
                <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-4">
                    <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-1">Peso</p>
                    <p className="text-lg font-black text-white tabular-nums">{lastLog?.weight ?? settings?.userVitals?.weight ?? '--'} kg</p>
                </div>
                <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-4">
                    <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-1">Meta calórica</p>
                    <p className="text-lg font-black text-white tabular-nums">{calorieGoal ? Math.round(calorieGoal) : '--'} kcal</p>
                </div>
            </div>
            {bodyProgress?.length > 0 && (
                <div className="space-y-4 [&_.section-card]:bg-[#FEF7FF] [&_.section-card]:border [&_.section-card]:border-[#E6E0E9] [&_.section-card]:rounded-xl">
                    <WeightVsTargetChart />
                    <BodyFatChart />
                    <MuscleMassChart />
                </div>
            )}
        </div>
    );
};

const EvolutionPage: React.FC = () => {
    const { bodyProgress } = useAppState();

    return (
        <div className="space-y-4">
            {bodyProgress?.length > 0 ? (
                <div className="[&_.section-card]:bg-[#FEF7FF] [&_.section-card]:border [&_.section-card]:border-[#E6E0E9] [&_.section-card]:rounded-xl">
                    <WeightVsTargetChart />
                </div>
            ) : (
                <p className="text-[11px] text-[#49454F] py-8">Configura un plan de nutrición y registra tu composición para ver la evolución.</p>
            )}
        </div>
    );
};

const CaloriesHistoryPage: React.FC = () => {
    const { nutritionLogs } = useAppState();

    const chartData = useMemo(() => {
        const last14 = nutritionLogs
            .filter((l: any) => l.date && (l.status === 'consumed' || !l.status))
            .sort((a: any, b: any) => new Date(a.date).getTime() - new Date(b.date).getTime())
            .slice(-14);
        return last14.map((log: any) => {
            let cal = 0;
            (log.foods || []).forEach((f: any) => { cal += f.calories || 0; });
            return {
                date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                calorías: cal || null,
            };
        }).filter((d: any) => d.calorías != null);
    }, [nutritionLogs]);

    if (chartData.length === 0) {
        return (
            <p className="text-[11px] text-[#49454F] py-8">Registra comidas para ver el historial de calorías.</p>
        );
    }

    return (
        <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-4">
            <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-4">Últimos {chartData.length} días</p>
            <div className="h-[180px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={chartData}>
                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" vertical={false} />
                        <XAxis dataKey="date" stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} />
                        <YAxis stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} unit=" kcal" domain={['dataMin - 100', 'auto']} />
                        <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '6px', fontSize: '11px' }} itemStyle={{ color: '#fff' }} />
                        <Line type="monotone" dataKey="calorías" stroke="rgba(255,255,255,0.7)" strokeWidth={2} dot={{ r: 2, fill: '#0a0a0a', strokeWidth: 1 }} />
                    </LineChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
};

// ─── Página Evolución de Medidas Corporales ─────────────────────────────────

const BodyMeasuresPage: React.FC = () => {
    const { bodyProgress } = useAppState();

    const customMeasureKeys = useMemo(() => {
        const keys = new Set<string>();
        bodyProgress.forEach(log => {
            if (log.measurements) Object.keys(log.measurements).forEach(k => keys.add(k));
        });
        return Array.from(keys);
    }, [bodyProgress]);

    const hasBasic = useMemo(() => {
        const withWeight = bodyProgress.filter(l => l.weight != null).length;
        const withFat = bodyProgress.filter(l => l.bodyFatPercentage != null).length;
        const withMuscle = bodyProgress.filter(l => l.muscleMassPercentage != null).length;
        return withWeight >= 2 || withFat >= 2 || withMuscle >= 2;
    }, [bodyProgress]);

    const hasCustom = customMeasureKeys.some(key => {
        const count = bodyProgress.filter(l => l.measurements?.[key] != null).length;
        return count >= 2;
    });

    if (!hasBasic && !hasCustom) {
        return (
            <p className="text-[11px] text-[#49454F] py-8">
                Registra peso, % grasa, % músculo o medidas corporales en Progreso corporal para ver la evolución.
            </p>
        );
    }

    return (
        <div className="space-y-4 [&_.section-card]:bg-[#FEF7FF] [&_.section-card]:border [&_.section-card]:border-[#E6E0E9] [&_.section-card]:rounded-xl">
            {hasBasic && (
                <>
                    <ErrorBoundary><WeightVsTargetChart /></ErrorBoundary>
                    <ErrorBoundary><BodyFatChart /></ErrorBoundary>
                    <ErrorBoundary><MuscleMassChart /></ErrorBoundary>
                </>
            )}
            {customMeasureKeys.map(key => {
                const chartData = bodyProgress
                    .filter(l => l.measurements?.[key] != null)
                    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
                    .map(l => ({
                        date: new Date(l.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                        [key]: l.measurements![key],
                    }));
                if (chartData.length < 2) return null;
                return (
                    <div key={key} className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-4">
                        <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-4">{key}</p>
                        <div className="h-[160px] w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={chartData}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" vertical={false} />
                                    <XAxis dataKey="date" stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} />
                                    <YAxis stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} domain={['dataMin - 1', 'dataMax + 1']} />
                                    <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '6px', fontSize: '11px' }} />
                                    <Line type="monotone" dataKey={key} stroke="#a78bfa" strokeWidth={2} dot={{ r: 2, fill: '#0a0a0a', strokeWidth: 1 }} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

// ─── Página Evolución FFMI e IMC ────────────────────────────────────────────

const FFMIBMIPage: React.FC = () => {
    const { bodyProgress, settings } = useAppState();
    const heightCm = settings.userVitals?.height;

    const chartData = useMemo(() => {
        if (!heightCm || heightCm <= 0) return [];
        const heightM = heightCm / 100;
        return bodyProgress
            .filter(l => l.weight && l.weight > 0)
            .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
            .map(l => {
                const bmi = l.weight! / (heightM * heightM);
                const ffmi = l.bodyFatPercentage != null
                    ? (() => {
                        const leanBodyMass = l.weight! * (1 - (l.bodyFatPercentage! / 100));
                        const rawFfmi = leanBodyMass / (heightM * heightM);
                        return rawFfmi + 6.1 * (1.8 - heightM);
                    })()
                    : null;
                return {
                    date: new Date(l.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                    IMC: parseFloat(bmi.toFixed(1)),
                    FFMI: ffmi != null ? parseFloat(ffmi.toFixed(1)) : null,
                };
            });
    }, [bodyProgress, heightCm]);

    const ffmiOnlyData = useMemo(() => chartData.filter(d => d.FFMI != null), [chartData]);
    const hasBmi = chartData.length >= 2;
    const hasFfmi = ffmiOnlyData.length >= 2;

    if (!hasBmi && !hasFfmi) {
        return (
            <p className="text-[11px] text-[#49454F] py-8">
                Configura tu estatura en ajustes y registra peso (+ % grasa para FFMI) en Progreso corporal.
            </p>
        );
    }

    return (
        <div className="space-y-4 [&_.section-card]:bg-[#FEF7FF] [&_.section-card]:border [&_.section-card]:border-[#E6E0E9] [&_.section-card]:rounded-xl">
            {hasFfmi && (
                <ErrorBoundary>
                    <FFMIChart />
                </ErrorBoundary>
            )}
            {hasBmi && (
                <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-xl p-4">
                    <p className="text-[10px] font-black text-[#49454F] uppercase tracking-widest mb-4">Evolución IMC</p>
                    <div className="h-[180px] w-full">
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={chartData}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" vertical={false} />
                                <XAxis dataKey="date" stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} />
                                <YAxis stroke="#52525b" tick={{ fill: '#71717a', fontSize: 10 }} tickLine={false} axisLine={false} domain={['dataMin - 1', 'dataMax + 1']} />
                                <Tooltip contentStyle={{ backgroundColor: '#0a0a0a', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '6px', fontSize: '11px' }} />
                                <Line type="monotone" dataKey="IMC" stroke="#10b981" strokeWidth={2} dot={{ r: 2, fill: '#0a0a0a', strokeWidth: 1 }} />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </div>
            )}
        </div>
    );
};
