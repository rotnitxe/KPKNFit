// components/SessionDetailView.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { Session, Program, Exercise } from '../types';
import { calculateIFI } from '../services/analysisService';
import { calculatePredictedSessionDrain } from '../services/auge';
import Card from './ui/Card';
import { BrainIcon, BarChartIcon, TrendingUpIcon, SparklesIcon, CheckCircleIcon, DumbbellIcon } from './icons';
import { WorkoutVolumeAnalysis } from './WorkoutVolumeAnalysis';
import { ExerciseLink } from './ExerciseLink';

interface SessionDetailViewProps {
    sessionInfo: { programId: string; sessionId: string };
}

const SessionDetailView: React.FC<SessionDetailViewProps> = ({ sessionInfo }) => {
    const { programs, history, isOnline, settings, exerciseList } = useAppState();

    const { session, program } = useMemo(() => {
        const prog = programs.find(p => p.id === sessionInfo.programId);
        if (!prog) return { session: null, program: null };
        const sess = prog.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks.flatMap(w => w.sessions)))).find(s => s.id === sessionInfo.sessionId);
        return { session: sess, program: prog };
    }, [sessionInfo, programs]);

    const sessionAnalysis = useMemo(() => {
        if (!session) return null;

        const sessionLogs = history.filter(log => log.sessionId === session.id);
        if (sessionLogs.length < 2) return { avgIFI: null, improvementIndex: 0 };
        
        const ifis = session.exercises
            .map(ex => calculateIFI(ex, exerciseList))
            .filter((ifi): ifi is number => ifi !== null);

        const avgIFI = ifis.length > 0 ? (ifis.reduce((a, b) => a + b, 0) / ifis.length) : null;
        
        const firstLog = sessionLogs[0];
        const lastLog = sessionLogs[sessionLogs.length - 1];

        const firstVolume = firstLog.completedExercises.reduce((sum, ex) => sum + ex.sets.reduce((s, set) => s + (set.weight || 0) * (set.completedReps || 0), 0), 0);
        const lastVolume = lastLog.completedExercises.reduce((sum, ex) => sum + ex.sets.reduce((s, set) => s + (set.weight || 0) * (set.completedReps || 0), 0), 0);

        const improvementIndex = firstVolume > 0 ? ((lastVolume - firstVolume) / firstVolume) * 100 : 0;

        return { avgIFI, improvementIndex };
    }, [session, history, programs, exerciseList]);

    // NUEVO: Calcular el drenaje de baterías y el estrés espinal de la sesión
    const drainStats = useMemo(() => {
        if (!session || !exerciseList.length) return null;
        return calculatePredictedSessionDrain(session, exerciseList);
    }, [session, exerciseList]);

    if (!session || !program) {
        return <div className="pt-24 text-center">Sesión no encontrada.</div>;
    }
    
    return (
        <div className="space-y-6">
            <header>
                <h1 className="text-3xl font-bold text-white">{session.name}</h1>
                <p className="text-slate-400">Análisis detallado de la sesión</p>
            </header>

            <Card>
                <h2 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><BrainIcon/> Métricas Clave</h2>
                <div className="grid grid-cols-3 gap-3 text-center">
                    <div className="bg-slate-800/50 p-3 rounded-lg flex flex-col justify-center">
                        <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">IFI Promedio</p>
                        <p className="text-2xl font-black text-primary-color">{sessionAnalysis?.avgIFI?.toFixed(1) ?? 'N/A'}</p>
                    </div>
                     <div className="bg-slate-800/50 p-3 rounded-lg flex flex-col justify-center">
                        <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Mejora</p>
                        <p className={`text-2xl font-black ${sessionAnalysis && sessionAnalysis.improvementIndex >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                            {sessionAnalysis ? `${sessionAnalysis.improvementIndex >= 0 ? '+' : ''}${sessionAnalysis.improvementIndex.toFixed(0)}%` : 'N/A'}
                        </p>
                    </div>
                    {/* NUEVO: CAJA DE ESTRÉS ESPINAL */}
                    <div className="bg-slate-800/50 p-3 rounded-lg flex flex-col justify-center relative overflow-hidden" title="Estrés biomecánico de la sesión">
                        <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Espinal</p>
                        <p className={`text-2xl font-black relative z-10 ${drainStats && drainStats.spinalDrain > 60 ? 'text-red-400' : drainStats && drainStats.spinalDrain > 30 ? 'text-yellow-400' : 'text-white'}`}>
                            {drainStats?.totalSpinalScore ?? 0}
                        </p>
                        {drainStats && drainStats.spinalDrain > 60 && (
                            <div className="absolute bottom-0 left-0 w-full h-1 bg-red-500"></div>
                        )}
                    </div>
                </div>
            </Card>

            <WorkoutVolumeAnalysis session={session} history={history} settings={settings} isOnline={isOnline} title="Volumen Efectivo de la Sesión" />

             <Card>
                <h2 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><DumbbellIcon /> Desglose por Ejercicio</h2>
                <div className="space-y-3">
                    {session.exercises.map(ex => {
                        const ifi = calculateIFI(ex, exerciseList);
                        const exLogs = history.filter(log => log.completedExercises.some(ce => ce.exerciseId === ex.id));
                        const lastPerformance = exLogs.length > 0 ? exLogs[exLogs.length-1].completedExercises.find(ce => ce.exerciseId === ex.id)?.sets[0] : null;

                        return (
                            <div key={ex.id} className="glass-card-nested p-3">
                                <div className="flex justify-between items-start">
                                    <h3 className="font-semibold text-white"><ExerciseLink name={ex.name} /></h3>
                                    {ifi && <span className="text-sm font-bold bg-slate-700 px-2 py-1 rounded-full">IFI: {ifi.toFixed(1)}</span>}
                                </div>
                                {lastPerformance && (
                                    <p className="text-xs text-slate-400">
                                        Última vez: {lastPerformance.weight}kg x {lastPerformance.completedReps} reps @RPE {lastPerformance.completedRPE}
                                    </p>
                                )}
                            </div>
                        )
                    })}
                </div>
            </Card>
            
             <Card>
                <h2 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><SparklesIcon/> Recomendaciones del Coach</h2>
                 <div className="space-y-2 text-sm text-slate-300">
                    <div className="flex items-start gap-2"><CheckCircleIcon size={16} className="text-green-400 mt-1 flex-shrink-0" /><p>El IFI promedio de <strong>{sessionAnalysis?.avgIFI?.toFixed(1)}</strong> indica una fatiga manejable. Considera aumentar el RPE en 0.5 en los ejercicios con IFI más bajo.</p></div>
                    <div className="flex items-start gap-2"><CheckCircleIcon size={16} className="text-green-400 mt-1 flex-shrink-0" /><p>Tu índice de mejora del <strong>{sessionAnalysis?.improvementIndex.toFixed(0)}%</strong> es excelente. Sigue aplicando sobrecarga progresiva en peso o repeticiones.</p></div>
                    <div className="flex items-start gap-2"><CheckCircleIcon size={16} className="text-green-400 mt-1 flex-shrink-0" /><p>El volumen de pectoral está en un rango óptimo. Para seguir progresando, podrías añadir una serie extra a las aperturas en la próxima fase de acumulación.</p></div>
                </div>
            </Card>
        </div>
    );
};

export default SessionDetailView;
