
// components/FeedView.tsx
import React, { useMemo, useState, useEffect } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { WorkoutLog, AchievementUnlock, Settings, ExerciseMuscleInfo, Exercise, Program } from '../types';
import { ACHIEVEMENTS_LIST } from '../data/achievements';
import { formatLargeNumber, calculateBrzycki1RM } from '../utils/calculations';
import { generateWorkoutPostSummary, getCommunityHighlights, generateExerciseProgressReport } from '../services/aiService';
import { cacheService } from '../services/cacheService';
import Card from './ui/Card';
import * as Icons from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import ExerciseInfoModal from './ExerciseInfoModal';
import OnThisDayCard from './OnThisDayCard';
import Modal from './ui/Modal';
import { ExerciseLink } from './ExerciseLink';

type FeedItem =
    | { type: 'workout'; date: string; data: WorkoutLog }
    | { type: 'achievement'; date: string; data: AchievementUnlock };

// --- NEW SUB-COMPONENTS for the enhanced Feed ---

const TrendTags: React.FC = () => {
    const { history } = useAppContext();
    const trends = useMemo(() => {
        const tagCounts: Record<string, { count: number; type: 'positive' | 'negative' | 'neutral' }> = {};
        const recentHistory = history.slice(-30);

        const positiveKeywords = ['fuerte', 'bien', 'energía', 'pr', 'récord', 'fácil'];
        const negativeKeywords = ['dolor', 'molestia', 'cansado', 'fatiga', 'difícil', 'lesión'];

        recentHistory.forEach(log => {
            (log.discomforts || []).forEach(discomfort => {
                tagCounts[discomfort] = { count: (tagCounts[discomfort]?.count || 0) + 2, type: 'negative' };
            });
            if (log.fatigueLevel > 7) tagCounts['Fatiga Alta'] = { count: (tagCounts['Fatiga Alta']?.count || 0) + 1, type: 'negative' };
            if (log.mentalClarity < 4) tagCounts['Claridad Baja'] = { count: (tagCounts['Claridad Baja']?.count || 0) + 1, type: 'negative' };
            
            const notes = log.notes?.toLowerCase() || '';
            positiveKeywords.forEach(kw => {
                if (notes.includes(kw)) tagCounts[kw] = { count: (tagCounts[kw]?.count || 0) + 1, type: 'positive' };
            });
            negativeKeywords.forEach(kw => {
                if (notes.includes(kw)) tagCounts[kw] = { count: (tagCounts[kw]?.count || 0) + 1, type: 'negative' };
            });
        });

        return Object.entries(tagCounts)
            .map(([tag, data]) => ({ tag, ...data }))
            .sort((a, b) => b.count - a.count)
            .slice(0, 15);
    }, [history]);

    if (trends.length === 0) return null;

    const getColor = (type: string) => {
        if (type === 'positive') return 'bg-green-500/20 text-green-300';
        if (type === 'negative') return 'bg-red-500/20 text-red-300';
        return 'bg-slate-700/50 text-slate-300';
    };

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-3">Tus Tendencias</h3>
            <div className="flex flex-wrap gap-2">
                {trends.map(({ tag, count, type }) => (
                    <div key={tag} className={`px-3 py-1 rounded-full ${getColor(type)}`} style={{ fontSize: `${Math.min(1.2, 0.7 + count * 0.1)}rem` }}>
                        {tag}
                    </div>
                ))}
            </div>
        </Card>
    );
};

const CommunityHighlights: React.FC = () => {
    const { settings, isOnline } = useAppContext();
    const [highlights, setHighlights] = useState<{ title: string; url: string }[] | null>(null);

    useEffect(() => {
        const fetchHighlights = async () => {
            if (isOnline) {
                try {
                    const result = await getCommunityHighlights(settings);
                    setHighlights(result.highlights);
                } catch (e) {
                    console.error("Failed to fetch community highlights", e);
                }
            }
        };
        fetchHighlights();
    }, [isOnline, settings]);

    if (!highlights || highlights.length === 0) return null;

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-3">Destacados de la Comunidad</h3>
            <div className="horizontal-scroll-container -mx-4 px-4">
                {highlights.map((item, i) => (
                    <a href={item.url} target="_blank" rel="noopener noreferrer" key={i} className="glass-card-nested !p-4 block w-full h-full">
                        <p className="font-semibold text-primary-color text-sm">{item.title}</p>
                    </a>
                ))}
            </div>
        </Card>
    );
};

const AIProgressReportModal: React.FC<{
    isOpen: boolean;
    onClose: () => void;
    exercise: Exercise | Program | null;
}> = ({ isOpen, onClose, exercise }) => {
    const { history, settings, isOnline } = useAppContext();
    const [report, setReport] = useState<{ summary: string; positives: string[]; areasForImprovement: string[] } | null>(null);
    const [isLoading, setIsLoading] = useState(false);

    useEffect(() => {
        const generateReport = async () => {
            if (!isOpen || !exercise || !isOnline) return;
            setIsLoading(true);
            setReport(null);
            try {
                const exerciseName = (exercise as Exercise).name;
                const relevantLogs = history.filter(log => (log.completedExercises || []).some(ex => ex.exerciseName.toLowerCase() === exerciseName.toLowerCase()));
                if(relevantLogs.length > 1) {
                    const result = await generateExerciseProgressReport(exerciseName, relevantLogs, settings);
                    setReport(result);
                } else {
                    setReport({summary: "No hay suficientes datos para un análisis detallado.", positives: [], areasForImprovement: []});
                }
            } catch (e) {
                console.error("Failed to generate exercise progress report", e);
                setReport({summary: "Error al generar el informe.", positives: [], areasForImprovement: []});
            } finally {
                setIsLoading(false);
            }
        };
        generateReport();
    }, [isOpen, exercise, history, settings, isOnline]);

    return (
        <Modal isOpen={isOpen} onClose={onClose} title={`Informe de Progreso: ${(exercise as Exercise)?.name}`}>
            {isLoading && <SkeletonLoader lines={5} />}
            {report && (
                <div className="space-y-4">
                    <p className="italic text-slate-300">"{report.summary}"</p>
                    <div>
                        <h4 className="font-semibold text-green-400 mb-2 flex items-center gap-2"><Icons.CheckCircleIcon /> Puntos Positivos</h4>
                        <ul className="list-disc list-inside space-y-1 text-slate-300 text-sm">{report.positives.map((p, i) => <li key={i}>{p}</li>)}</ul>
                    </div>
                     <div>
                        <h4 className="font-semibold text-yellow-400 mb-2 flex items-center gap-2"><Icons.SparklesIcon /> Áreas de Mejora</h4>
                        <ul className="list-disc list-inside space-y-1 text-slate-300 text-sm">{report.areasForImprovement.map((p, i) => <li key={i}>{p}</li>)}</ul>
                    </div>
                </div>
            )}
        </Modal>
    );
};

const StarExercises: React.FC = () => {
    const { programs, history, settings, exerciseList, muscleHierarchy } = useAppContext();
    const [selectedExercise, setSelectedExercise] = useState<Exercise | null>(null);
    const [infoModalExercise, setInfoModalExercise] = useState<ExerciseMuscleInfo | null>(null);

    const starExercises = useMemo(() => {
        const favs = new Map<string, Exercise>();
        programs.forEach(p => p.macrocycles.forEach(m => (m.blocks || []).forEach(b => b.mesocycles.forEach(meso => meso.weeks.forEach(w => w.sessions.forEach(s => {
            s.exercises.forEach(ex => {
                if (ex.isFavorite || ex.goal1RM) {
                    const key = ex.exerciseDbId || ex.name.toLowerCase();
                    if (!favs.has(key)) favs.set(key, ex);
                }
            });
        }))))));
        
        return Array.from(favs.values()).map(ex => {
            let best1RM = 0;
            history.forEach(log => {
                const completed = (log.completedExercises || []).find(ce => (ce.exerciseDbId || ce.exerciseName.toLowerCase()) === (ex.exerciseDbId || ex.name.toLowerCase()));
                if (completed) {
                    const maxSet1RM = Math.max(...completed.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
                    if (maxSet1RM > best1RM) best1RM = maxSet1RM;
                }
            });
            return { exercise: ex, best1RM };
        });
    }, [programs, history]);

    if (starExercises.length === 0) return null;

    return (
        <>
            <AIProgressReportModal isOpen={!!selectedExercise} onClose={() => setSelectedExercise(null)} exercise={selectedExercise} />
             <ExerciseInfoModal 
                exercise={infoModalExercise} 
                onClose={() => setInfoModalExercise(null)} 
                muscleHierarchy={muscleHierarchy}
            />
            
            <Card>
                <h3 className="text-xl font-bold text-yellow-300 mb-3 flex items-center gap-2"><Icons.StarIcon /> Ejercicios Estrella</h3>
                <div className="horizontal-scroll-container -mx-4 px-4">
                    {starExercises.map(({ exercise, best1RM }) => (
                        <div key={exercise.id} onClick={() => setSelectedExercise(exercise)} className="glass-card-nested !p-4 flex flex-col justify-between cursor-pointer">
                            <p className="font-bold text-white">{exercise.name}</p>
                            <div>
                                <p className="text-3xl font-black text-yellow-300">{best1RM.toFixed(1)}</p>
                                <p className="text-xs text-slate-400">1RM Estimado ({settings.weightUnit})</p>
                            </div>
                        </div>
                    ))}
                </div>
            </Card>
        </>
    );
};

const StagnationAlerts: React.FC = () => {
    const { history } = useAppContext();
    const stagnatedExercises = useMemo(() => {
        const exerciseData: Record<string, {e1rms: number[], count: number}> = {};
        history.slice(-20).forEach(log => {
            (log.completedExercises || []).forEach(ex => {
                const key = ex.exerciseDbId || ex.exerciseName.toLowerCase();
                if (!exerciseData[key]) exerciseData[key] = { e1rms: [], count: 0 };
                const max1RM = Math.max(...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
                if(max1RM > 0) {
                    exerciseData[key].e1rms.push(max1RM);
                    exerciseData[key].count++;
                }
            });
        });
        
        const alerts: string[] = [];
        Object.entries(exerciseData).forEach(([name, data]) => {
            if (data.count >= 3) {
                const recentPerformance = data.e1rms.slice(-3);
                if (recentPerformance.length === 3 && recentPerformance[2] <= recentPerformance[0] && recentPerformance[2] <= recentPerformance[1]) {
                    const exName = history.find(l => (l.completedExercises || []).some(e => (e.exerciseDbId || e.exerciseName.toLowerCase()) === name))?.completedExercises.find(e => (e.exerciseDbId || e.exerciseName.toLowerCase()) === name)?.exerciseName;
                    if(exName) alerts.push(exName);
                }
            }
        });
        return alerts;
    }, [history]);

    if (stagnatedExercises.length === 0) return null;
    
    return (
        <Card className="border-yellow-500/50 bg-yellow-900/20">
             <h3 className="text-xl font-bold text-yellow-300 mb-3 flex items-center gap-2"><Icons.AlertTriangleIcon /> Alerta de Estancamiento</h3>
             <p className="text-sm text-slate-300">Hemos detectado que tu progreso en los siguientes ejercicios se ha estancado. Considera una semana de descarga o cambiar el esquema de repeticiones:</p>
             <ul className="list-disc list-inside text-white font-semibold mt-2">
                {stagnatedExercises.map(name => <li key={name}>{name}</li>)}
             </ul>
        </Card>
    );
};

const WorkoutPost: React.FC<{ 
    log: WorkoutLog, 
    settings: Settings, 
    onEdit: (log: WorkoutLog) => void,
    onExerciseClick: (exerciseName: string, exerciseDbId?: string) => void
}> = ({ log, settings, onEdit, onExerciseClick }) => {
    const { history } = useAppContext();
    const [summary, setSummary] = useState<{ title: string; summary: string } | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const fetchSummary = async () => {
            if (log.postTitle || log.postSummary) {
                setSummary({ title: log.postTitle || log.sessionName, summary: log.postSummary || '' });
                setIsLoading(false); return;
            }
            const cacheKey = `workout_summary_${log.id}`;
            const cached = await cacheService.get<{ title: string; summary: string }>(cacheKey);
            if (cached) { setSummary(cached); setIsLoading(false); return; }
            
            setIsLoading(true);
            try {
                const previousLogs = history.filter(h => h.sessionId === log.sessionId && new Date(h.date) < new Date(log.date));
                const result = await generateWorkoutPostSummary(log, previousLogs, settings);
                setSummary(result); await cacheService.set(cacheKey, result);
            } catch (error) {
                console.error("Failed to generate summary:", error);
                setSummary({ title: `¡${log.sessionName} completado!`, summary: "¡Gran trabajo en la sesión de hoy!" });
            } finally { setIsLoading(false); }
        };
        fetchSummary();
    }, [log, history, settings]);
    
    const totalVolume = useMemo(() => (log.completedExercises || []).reduce((acc, ex) => acc + ex.sets.reduce((sAcc, s) => sAcc + (s.weight || 0) * (s.completedReps || 0), 0), 0), [log.completedExercises]);
    const photos = log.postPhotos || (log.photo ? [log.photo] : []); 

    return (
        <details className="glass-card !p-0 overflow-hidden">
            <summary className="p-4 cursor-pointer list-none">
                <div className="flex justify-between items-start">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-primary-color flex items-center justify-center"><Icons.DumbbellIcon size={20} /></div>
                        <div>
                            <h3 className="font-bold text-white">{log.programName}</h3>
                            <p className="text-xs text-slate-400">{new Date(log.date).toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); onEdit(log); }} className="p-2 text-slate-500 hover:text-white"><Icons.PencilIcon size={16} /></button>
                        <Icons.ChevronRightIcon className="details-arrow text-slate-400" />
                    </div>
                </div>
            </summary>
            <div className="px-4 pb-4 border-t border-slate-700/50">
                {photos.length > 0 && <img src={photos[0]} alt={`Foto de la sesión ${log.sessionName}`} className="w-full rounded-lg my-3 aspect-video object-cover" />}
                {isLoading ? <SkeletonLoader lines={4} /> : (
                    <div className="prose prose-sm prose-invert max-w-none mt-3">
                        <h4 className="text-white">{summary?.title}</h4>
                        <div dangerouslySetInnerHTML={{ __html: summary?.summary.replace(/\n/g, '<br />') || "" }} />
                    </div>
                )}
                {log.completedExercises && log.completedExercises.length > 0 && (
                    <div className="mt-4">
                        <h5 className="font-semibold text-slate-300 mb-2">Ejercicios Registrados</h5>
                        <div className="space-y-2">
                            {log.completedExercises.map(ex => (
                                <div key={ex.exerciseId} className="bg-slate-800/50 p-3 rounded-lg">
                                    <div className="flex justify-between items-center"><p className="font-semibold text-white"><ExerciseLink name={ex.exerciseName}/></p></div>
                                    <div className="text-xs text-slate-400 mt-1">{ex.sets.length} series</div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
                <div className="grid grid-cols-3 gap-3 text-center mt-4 pt-4 border-t border-slate-700/50">
                    <div className="bg-slate-900/50 rounded-xl p-2"><p className="text-lg font-bold font-mono text-white">{formatLargeNumber(totalVolume)}</p><p className="text-[10px] uppercase text-slate-400 font-semibold tracking-wider">Volumen ({settings.weightUnit})</p></div>
                    <div className="bg-slate-900/50 rounded-xl p-2"><p className="text-lg font-bold font-mono text-white">{Math.round((log.duration || 0) / 60)}</p><p className="text-[10px] uppercase text-slate-400 font-semibold tracking-wider">Minutos</p></div>
                    <div className="bg-slate-900/50 rounded-xl p-2"><p className="text-lg font-bold font-mono text-white">{(log.completedExercises || []).length}</p><p className="text-[10px] uppercase text-slate-400 font-semibold tracking-wider">Ejercicios</p></div>
                </div>
            </div>
        </details>
    );
};

const AchievementPost: React.FC<{ unlock: AchievementUnlock }> = ({ unlock }) => {
    const achievement = ACHIEVEMENTS_LIST.find(a => a.id === unlock.achievementId);
    if (!achievement) return null;
    const IconComponent = (Icons as any)[achievement.icon] || Icons.TrophyIcon;

    return (
        <Card className="animate-fade-in-up bg-gradient-to-br from-yellow-900/30 to-slate-900/50 border-yellow-400/30">
             <div className="flex items-center gap-4">
                <div className={`p-3 rounded-full bg-yellow-400/20`}><IconComponent size={24} className="text-yellow-400" /></div>
                <div className="flex-1">
                    <p className="text-xs text-yellow-400 font-semibold">¡LOGRO DESBLOQUEADO!</p>
                    <h3 className="font-bold text-yellow-300">{achievement.name}</h3>
                    <p className="text-sm text-slate-400">{achievement.description}</p>
                </div>
            </div>
        </Card>
    );
};

// --- MAIN FEED COMPONENT ---

const FeedView: React.FC<{
    openCreatePostModal: () => void;
    openEditPostModal: (log: WorkoutLog) => void;
    openCustomizationSheet: () => void;
}> = ({ openCreatePostModal, openEditPostModal, openCustomizationSheet }) => {
    const { history, unlockedAchievements, settings, exerciseList, muscleHierarchy } = useAppContext();
    const [infoModalExercise, setInfoModalExercise] = useState<ExerciseMuscleInfo | null>(null);
    
    const feedItems = useMemo(() => {
        const workoutPosts: FeedItem[] = history.map(log => ({ type: 'workout', date: log.date, data: log }));
        const achievementPosts: FeedItem[] = unlockedAchievements.map(ach => ({ type: 'achievement', date: ach.date, data: ach }));
        return [...workoutPosts, ...achievementPosts].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    }, [history, unlockedAchievements]);

    const handleExerciseClick = (exerciseName: string, exerciseDbId?: string) => {
        const exerciseInfo = exerciseList.find(e => e.id === exerciseDbId || e.name.toLowerCase() === exerciseName.toLowerCase());
        if (exerciseInfo) setInfoModalExercise(exerciseInfo);
    };

    return (
        <>
            <ExerciseInfoModal exercise={infoModalExercise} onClose={() => setInfoModalExercise(null)} muscleHierarchy={muscleHierarchy} />
            <div className="space-y-6">
                <TrendTags />
                <CommunityHighlights />
                <StarExercises />
                <StagnationAlerts />
                <OnThisDayCard />

                {feedItems.length === 0 ? (
                    <Card className="text-center py-12"><p className="text-slate-400">Tu feed está vacío. ¡Completa un entrenamiento para empezar!</p></Card>
                ) : (
                    feedItems.map((item, index) => {
                        if (item.type === 'workout') {
                            return <WorkoutPost key={`workout-${item.data.id}-${index}`} log={item.data} settings={settings} onEdit={openEditPostModal} onExerciseClick={handleExerciseClick} />;
                        } else if (item.type === 'achievement') {
                            return <AchievementPost key={`ach-${item.data.achievementId}-${index}`} unlock={item.data} />;
                        }
                        return null;
                    })
                )}
            </div>
        </>
    );
};

export default FeedView;
