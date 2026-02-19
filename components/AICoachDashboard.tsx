import React, { useState } from 'react';
import { Program, WorkoutLog, CoachInsight, Settings, BodyProgressLog, NutritionLog } from '../types';
import { getAICoachInsights } from '../services/aiService';
import Card from './ui/Card';
import Button from './ui/Button';
import { LightbulbIcon, AlertTriangleIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';

interface AICoachDashboardProps {
    history: WorkoutLog[];
    programs: Program[];
    isOnline: boolean;
    settings: Settings;
    bodyProgress: BodyProgressLog[];
    nutritionLogs: NutritionLog[];
}

const AICoachDashboard: React.FC<AICoachDashboardProps> = ({ history, programs, isOnline, settings, bodyProgress, nutritionLogs }) => {
    const [insight, setInsight] = useState<CoachInsight | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleAnalyze = async () => {
        if (!isOnline) {
            setError("La función de IA requiere conexión a internet.");
            return;
        }
        setIsLoading(true);
        setError(null);
        try {
            const result = await getAICoachInsights(history, programs, settings, bodyProgress, nutritionLogs);
            setInsight(result);
        } catch (err: any) {
            setError(err.message || 'Ocurrió un error.');
        } finally {
            setIsLoading(false);
        }
    };
    
    const alertStyles = {
        info: {
            icon: <LightbulbIcon size={24} className="text-[var(--primary-color-400)]" />,
            borderColor: 'border-[var(--primary-color-700)]',
            bgColor: 'bg-[var(--primary-color-950)]/30',
            titleColor: 'text-[var(--primary-color-400)]'
        },
        warning: {
            icon: <AlertTriangleIcon size={24} className="text-yellow-400" />,
            borderColor: 'border-yellow-600',
            bgColor: 'bg-yellow-950/30',
            titleColor: 'text-yellow-400'
        },
        danger: {
            icon: <AlertTriangleIcon size={24} className="text-red-400" />,
            borderColor: 'border-red-600',
            bgColor: 'bg-red-950/30',
            titleColor: 'text-red-400'
        }
    };

    const currentAlert = insight ? alertStyles[insight.alertLevel] : alertStyles.info;

    return (
        <Card className={`border-t-4 ${currentAlert.borderColor} ${currentAlert.bgColor}`}>
            <div className="flex items-start gap-4">
                <div className="flex-shrink-0 mt-1">{currentAlert.icon}</div>
                <div>
                    <h3 className={`text-2xl font-bold mb-2 ${currentAlert.titleColor}`}>
                        {insight ? insight.title : 'Tu Coach IA'}
                    </h3>

                    {history.length < 2 && (
                         <p className="text-slate-400">Completa al menos dos entrenamientos para obtener tu primer análisis de IA.</p>
                    )}

                    {!insight && !isLoading && history.length >= 2 && (
                        <div className="py-4">
                            <p className="text-slate-400 mb-4">Obtén insights sobre tu progreso, estancamientos y posibles riesgos de lesión, impulsados por IA.</p>
                            <Button onClick={handleAnalyze} isLoading={isLoading} disabled={!isOnline}>Analizar Mi Rendimiento</Button>
                        </div>
                    )}
                </div>
            </div>

            {isLoading && (
                 <div className="mt-4 pl-10 space-y-4">
                    <SkeletonLoader lines={4} />
                </div>
            )}
            
            {error && <p className="text-red-400 mt-2">{error}</p>}

            {insight && !isLoading &&(
                <div className="animate-fade-in mt-4 pl-10 space-y-4">
                    <div>
                        <h4 className="font-semibold text-white">Observaciones:</h4>
                        <p className="text-slate-300 whitespace-pre-wrap">{insight.findings}</p>
                    </div>
                     <div>
                        <h4 className="font-semibold text-white">Sugerencias:</h4>
                        <ul className="list-disc list-inside space-y-1 text-slate-300">
                           {insight.suggestions.map((s, i) => <li key={i}>{s}</li>)}
                        </ul>
                    </div>
                    <div className="text-center pt-4">
                        <Button onClick={handleAnalyze} isLoading={isLoading} variant="secondary" disabled={!isOnline}>Regenerar Análisis</Button>
                    </div>
                </div>
            )}
        </Card>
    );
};

export default AICoachDashboard;