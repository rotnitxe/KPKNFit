// components/WeeklyProgressAnalysis.tsx
import React, { useState, useEffect } from 'react';
import { useAppState } from '../contexts/AppContext';
import { generateImprovementSuggestions } from '../services/aiService';
import { ImprovementSuggestion } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { SparklesIcon, BarChartIcon, TrendingUpIcon, ZapIcon, ClockIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';

const ICON_MAP: Record<ImprovementSuggestion['category'], React.FC<any>> = {
    Progression: TrendingUpIcon,
    Volume: BarChartIcon,
    Intensity: ZapIcon,
    Recovery: ClockIcon,
};

const WeeklyProgressAnalysis: React.FC = () => {
    const { history, programs, settings, isOnline } = useAppState();
    const [suggestions, setSuggestions] = useState<ImprovementSuggestion[] | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const analyze = async () => {
            if (!isOnline) {
                setError("La función de IA requiere conexión a internet.");
                setIsLoading(false);
                return;
            }
            if (history.length < 3) {
                setError(null);
                setSuggestions(null); 
                setIsLoading(false);
                return;
            }
            setIsLoading(true);
            setError(null);
            try {
                const result = await generateImprovementSuggestions(history, programs, settings);
                setSuggestions(result);
            } catch (err: any) {
                setError(err.message || 'Ocurrió un error al generar el análisis.');
            } finally {
                setIsLoading(false);
            }
        };
        analyze();
    }, [history, programs, settings, isOnline]);
    
    return (
        <Card>
            <h3 className="text-2xl font-bold text-white mb-4 flex items-center gap-2">
                <SparklesIcon/> Sugerencias del Coach
            </h3>
            
            {isLoading && <SkeletonLoader lines={5} />}
            {error && <p className="text-red-400 mt-2 text-center text-sm">{error}</p>}
            
            {!isLoading && suggestions && suggestions.length > 0 && (
                <div className="space-y-4 animate-fade-in">
                    {suggestions.map((item, index) => {
                        const Icon = ICON_MAP[item.category] || SparklesIcon;
                        return (
                            <div key={index} className="bg-slate-900/50 p-4 rounded-lg border-l-4 border-primary-color">
                                <h4 className="font-bold text-white flex items-center gap-2"><Icon size={16}/> {item.title}</h4>
                                <p className="text-sm text-slate-300 mt-1">{item.suggestion}</p>
                            </div>
                        );
                    })}
                </div>
            )}
            
            {!isLoading && (!suggestions || suggestions.length === 0) && !error && (
                 <div className="text-center py-4 space-y-3">
                    <p className="text-sm text-slate-400">
                        {history.length < 3 
                            ? "Completa al menos 3 entrenamientos para recibir tus primeras sugerencias."
                            : "No hay nuevas sugerencias por ahora. ¡Sigue entrenando!"
                        }
                    </p>
                </div>
             )}
        </Card>
    );
};

export default WeeklyProgressAnalysis;