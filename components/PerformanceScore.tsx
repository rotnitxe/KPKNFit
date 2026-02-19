
// components/PerformanceScore.tsx
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { WorkoutLog, PerformanceAnalysis, Settings, SkippedWorkoutLog } from '../types';
import { generatePerformanceAnalysis } from '../services/aiService';
import Card from './ui/Card';
import Button from './ui/Button';
import Modal from './ui/Modal';
import { SparklesIcon, CheckIcon, AlertTriangleIcon } from './icons';
import useStorage from '../hooks/useLocalStorage';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import SkeletonLoader from './ui/SkeletonLoader';
import { getWeekId } from '../utils/calculations';


interface PerformanceScoreProps {
  history: WorkoutLog[];
  skippedLogs: SkippedWorkoutLog[];
  isOnline: boolean;
  settings: Settings;
}

const PerformanceScore: React.FC<PerformanceScoreProps> = ({ history, skippedLogs, isOnline, settings }) => {
    const [cachedAnalyses, setCachedAnalyses] = useStorage<Array<{ analysis: PerformanceAnalysis, weekId: string }>>('performance-analysis-cache', []);
    
    const [analysis, setAnalysis] = useState<PerformanceAnalysis | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    const handleAnalyze = useCallback(async (force = false) => {
        if (!isOnline) {
            setError("Esta función requiere conexión a internet.");
            return;
        }
         if (history.length < 2) {
            return;
        }
        setIsLoading(true);
        setError(null);
        try {
            const result = await generatePerformanceAnalysis(history, skippedLogs, settings);
            setAnalysis(result);
            const currentWeekId = getWeekId(new Date(), settings.startWeekOn);
            setCachedAnalyses(prev => [...prev.filter(item => item.weekId !== currentWeekId), { analysis: result, weekId: currentWeekId }]);
        } catch (err: any) {
            setError(err.message || 'Ocurrió un error al generar el análisis.');
            setAnalysis(null);
        } finally {
            setIsLoading(false);
        }
    }, [history, isOnline, settings, setCachedAnalyses, skippedLogs]);
    
    useEffect(() => {
        const currentWeekId = getWeekId(new Date(), settings.startWeekOn);
        const currentWeekAnalysis = cachedAnalyses.find(item => item.weekId === currentWeekId);
        if (currentWeekAnalysis) {
            setAnalysis(currentWeekAnalysis.analysis);
            setError(null);
        } else {
            setAnalysis(null);
            setError(null);
        }
    }, [cachedAnalyses, settings.startWeekOn, history.length]);
    
    const getScoreColor = (score: number) => {
        if (score <= 2) return 'from-red-500 to-red-700';
        if (score <= 4) return 'from-yellow-400 to-orange-500';
        if (score <= 6) return 'from-green-400 to-emerald-500';
        return 'from-[var(--primary-color-400)] to-[var(--primary-color-600)]';
    };

    const chartData = useMemo(() => {
        if (!cachedAnalyses) return [];
        return cachedAnalyses
          .map(item => ({
            dateObj: new Date(item.weekId.split('-').join('/')),
            date: new Date(item.weekId.split('-').join('/')).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
            'PRIME SCORE': item.analysis.score,
          }))
          .sort((a, b) => a.dateObj.getTime() - b.dateObj.getTime());
    }, [cachedAnalyses]);


    return (
        <>
            <Card className="text-center performance-score-card">
                 <h3 className="text-xl font-bold text-white mb-4 relative z-10">PRIME SCORE Semanal</h3>
                 
                 {isLoading && (
                    <div className="p-8 space-y-4">
                        <SkeletonLoader type="circle" className="w-24 h-24 mx-auto" />
                        <SkeletonLoader lines={2} />
                    </div>
                 )}

                 {analysis && !isLoading && (
                    <div className="animate-fade-in relative z-10">
                        <div onClick={() => setIsModalOpen(true)} className="cursor-pointer group">
                             <p className={`text-7xl sm:text-8xl font-black bg-clip-text text-transparent bg-gradient-to-br ${getScoreColor(analysis.score)} transition-all duration-300 group-hover:opacity-80`}>
                                {analysis.score}
                             </p>
                             <p className="font-semibold text-white mt-2 group-hover:text-[var(--primary-color-400)] transition-colors">{analysis.summary}</p>
                             <p className="text-xs text-slate-500 mt-1">(clic para ver detalles)</p>
                        </div>
                         <Button onClick={() => handleAnalyze(true)} variant="secondary" className="mt-4 !text-xs !py-1" disabled={!isOnline || isLoading}>
                            Recalcular
                        </Button>
                    </div>
                 )}

                {!analysis && !isLoading && (
                     <div className="text-center p-4 relative z-10">
                        {history.length < 2 ? (
                            <p className="text-slate-400 text-sm">Se necesitan al menos 2 entrenamientos registrados para calcular.</p>
                        ) : (
                            <div className="space-y-3">
                                <p className="text-slate-400 text-sm">Analiza tu rendimiento de la última semana para obtener tu puntuación.</p>
                                <Button onClick={() => handleAnalyze()} disabled={!isOnline}>Calcular PRIME SCORE</Button>
                                {error && <p className="text-red-400 text-xs mt-2">{error}</p>}
                            </div>
                        )}
                     </div>
                 )}
            </Card>

            <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={`Análisis PRIME SCORE: ${analysis?.score}/7`}>
                {analysis && (
                    <div className="space-y-6 p-2">
                        <div>
                            <h4 className="text-lg font-semibold text-green-400 mb-2 flex items-center gap-2">
                                <CheckIcon/> Puntos Positivos
                            </h4>
                            <ul className="list-disc list-inside space-y-1 text-slate-300 text-sm">
                                {analysis.positivePoints.map((point, i) => <li key={i}>{point}</li>)}
                            </ul>
                        </div>
                         <div>
                            <h4 className="text-lg font-semibold text-red-400 mb-2 flex items-center gap-2">
                                <AlertTriangleIcon/> Áreas de Mejora
                            </h4>
                            <ul className="list-disc list-inside space-y-1 text-slate-300 text-sm">
                                {analysis.negativePoints.map((point, i) => <li key={i}>{point}</li>)}
                            </ul>
                        </div>
                         {chartData.length > 1 && (
                            <div className="mt-6 pt-4 border-t border-slate-700">
                                <h4 className="text-lg font-semibold text-white mb-2">Historial de PRIME SCORE</h4>
                                <ResponsiveContainer width="100%" height={200}>
                                    <LineChart data={chartData} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                                        <XAxis dataKey="date" stroke="#94a3b8" />
                                        <YAxis stroke="#94a3b8" domain={[1, 7]} />
                                        <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155' }} />
                                        <Line type="monotone" dataKey="PRIME SCORE" stroke="var(--primary-color-400)" strokeWidth={2} />
                                    </LineChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </div>
                )}
            </Modal>
        </>
    );
};

export default PerformanceScore;