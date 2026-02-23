// components/MuscleTrainingAnalysis.tsx
import React, { useState } from 'react';
import { WorkoutLog, Settings, MuscleGroupAnalysis } from '../types';
import { generateMuscleGroupAnalysis } from '../services/aiService';
import { SparklesIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';

interface MuscleTrainingAnalysisProps {
    muscleName: string;
    history: WorkoutLog[];
    isOnline: boolean;
    settings: Settings;
}

// FIX: Export the component
export const MuscleTrainingAnalysis: React.FC<MuscleTrainingAnalysisProps> = ({ muscleName, history, isOnline, settings }) => {
    const [analysis, setAnalysis] = useState<MuscleGroupAnalysis | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleGenerateAnalysis = async () => {
        if (!isOnline) {
            setError("Necesitas conexión a internet para analizar.");
            return;
        }
        setIsLoading(true);
        setError(null);
        try {
            // A simple filter, can be improved with more detailed muscle mapping
            const trainingData = history.filter(log =>
                log.completedExercises.some(ex =>
                    ex.exerciseName.toLowerCase().includes(muscleName.toLowerCase())
                )
            ).slice(-20); // Send last 20 relevant sessions to AI

            if (trainingData.length < 2) {
                throw new Error("No hay suficientes datos de entrenamiento para este músculo.");
            }

            const result = await generateMuscleGroupAnalysis(muscleName, trainingData, settings);
            setAnalysis(result);
        } catch (err: any) {
            setError(err.message || "No se pudo generar el análisis.");
        } finally {
            setIsLoading(false);
        }
    };

    const getAssessmentColor = (assessment?: MuscleGroupAnalysis['assessment']) => {
        switch (assessment) {
            case 'Optimo': return 'text-green-400';
            case 'Sobrecargado': return 'text-red-400';
            case 'Subentrenado': return 'text-yellow-400';
            default: return 'text-slate-400';
        }
    };

    return (
        <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3">Análisis de Entrenamiento (IA)</h3>
            {isLoading ? (
                <SkeletonLoader lines={4} />
            ) : analysis ? (
                <div className="space-y-3 animate-fade-in">
                    <p className="text-center">
                        <span className={`text-2xl font-bold ${getAssessmentColor(analysis.assessment)}`}>{analysis.assessment}</span>
                    </p>
                    <p className="text-sm text-slate-300 italic">"{analysis.summary}"</p>
                    <div>
                        <h4 className="font-semibold text-slate-200">Recomendaciones:</h4>
                        <ul className="list-disc list-inside text-sm text-slate-400">
                            {analysis.recommendations.map((rec, i) => <li key={i}>{rec}</li>)}
                        </ul>
                    </div>
                    <button onClick={handleGenerateAnalysis} className="w-full py-2 mt-2 rounded-lg border border-cyber-cyan/20 bg-[#0d0d0d] text-[10px] font-mono text-cyber-cyan/90 hover:border-cyber-cyan/40 transition-colors">
                        <SparklesIcon size={12} className="inline mr-1"/> Regenerar Análisis
                    </button>
                </div>
            ) : (
                <div className="text-center">
                    <p className="text-sm text-slate-400 mb-3">Obtén un análisis detallado sobre el volumen, frecuencia y progresión para este grupo muscular.</p>
                    <button onClick={handleGenerateAnalysis} disabled={!isOnline || history.length < 2} className="w-full py-2.5 rounded-lg border border-cyber-cyan/20 bg-[#0d0d0d] text-[10px] font-mono text-cyber-cyan/90 hover:border-cyber-cyan/40 transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                        <SparklesIcon size={12} className="inline mr-1"/> Analizar {muscleName}
                    </button>
                    {history.length < 2 && <p className="text-[10px] text-slate-500 font-mono mt-2">Necesitas al menos 2 entrenamientos registrados.</p>}
                    {error && <p className="text-red-400 text-sm mt-2">{error}</p>}
                </div>
            )}
        </div>
    );
};