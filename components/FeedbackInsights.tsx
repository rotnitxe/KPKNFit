import React, { useMemo } from 'react';
import { PostSessionFeedback } from '../types';
import { calculateVolumeAdjustment } from '../services/volumeCalculator';
import { ActivityIcon, TrendingUpIcon, AlertTriangleIcon, CheckCircleIcon } from './icons'; // Asegúrate de tener estos iconos

interface FeedbackInsightsProps {
    feedbackHistory: PostSessionFeedback[];
    activeMuscles: string[]; // Músculos que se están entrenando en el programa actual
}

const FeedbackInsights: React.FC<FeedbackInsightsProps> = ({ feedbackHistory, activeMuscles }) => {
    
    const insights = useMemo(() => {
        const results: any[] = [];
        
        activeMuscles.forEach(muscle => {
            const adjustment = calculateVolumeAdjustment(muscle, feedbackHistory);
            if (adjustment.status !== 'optimal') {
                results.push({ muscle, ...adjustment });
            }
        });
        
        return results;
    }, [feedbackHistory, activeMuscles]);

    if (insights.length === 0) return null;

    return (
        <div className="bg-[#111] border border-white/10 rounded-2xl p-5 animate-fade-in mb-6">
            <div className="flex items-center gap-2 mb-4">
                <div className="bg-purple-500/20 p-2 rounded-lg text-purple-400">
                    <ActivityIcon size={18} />
                </div>
                <div>
                    <h3 className="text-sm font-black text-white uppercase tracking-tight">Análisis de Recuperación</h3>
                    <p className="text-[10px] text-gray-500">Basado en tus reportes post-entreno recientes</p>
                </div>
            </div>

            <div className="space-y-3">
                {insights.map((insight, idx) => (
                    <div key={idx} className={`p-3 rounded-xl border flex items-start gap-3 ${
                        insight.status === 'recovery_debt' 
                            ? 'bg-red-500/5 border-red-500/20' 
                            : 'bg-emerald-500/5 border-emerald-500/20'
                    }`}>
                        <div className={`mt-0.5 ${insight.status === 'recovery_debt' ? 'text-red-400' : 'text-emerald-400'}`}>
                            {insight.status === 'recovery_debt' ? <AlertTriangleIcon size={16} /> : <TrendingUpIcon size={16} />}
                        </div>
                        <div>
                            <div className="flex justify-between items-center mb-1">
                                <span className={`text-xs font-black uppercase ${insight.status === 'recovery_debt' ? 'text-red-200' : 'text-emerald-200'}`}>
                                    {insight.muscle}
                                </span>
                                <span className="text-[9px] font-bold bg-black/40 px-2 py-0.5 rounded text-white/70">
                                    {insight.factor > 1 ? `+${Math.round((insight.factor - 1)*100)}% Vol` : `-${Math.round((1 - insight.factor)*100)}% Vol`}
                                </span>
                            </div>
                            <p className="text-[10px] text-gray-400 leading-relaxed">
                                {insight.suggestion}
                            </p>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default FeedbackInsights;