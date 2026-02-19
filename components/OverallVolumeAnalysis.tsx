// components/OverallVolumeAnalysis.tsx
import React, { useState } from 'react';
import { Program, DetailedMuscleVolumeAnalysis, Settings } from '../types';
import { calculateDetailedVolumeForAllPrograms } from '../services/analysisService';
import Card from './ui/Card';
import Button from './ui/Button';
import { SparklesIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { useAppContext } from '../contexts/AppContext';

interface OverallVolumeAnalysisProps {
    programs: Program[];
    isOnline: boolean;
    settings: Settings;
}

const OverallVolumeAnalysis: React.FC<OverallVolumeAnalysisProps> = ({ programs, isOnline, settings }) => {
    const { exerciseList, muscleGroupData, muscleHierarchy } = useAppContext();
    const [analysis, setAnalysis] = useState<DetailedMuscleVolumeAnalysis[] | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [expandedMuscle, setExpandedMuscle] = useState<string | null>(null);

    const handleGenerate = async () => {
        setIsLoading(true);
        setError(null);
        setExpandedMuscle(null);
        try {
            const result = await calculateDetailedVolumeForAllPrograms(programs, settings, exerciseList, muscleHierarchy);
            setAnalysis(result);
        } catch (err: any) {
            setError(err.message || 'Ocurrió un error al generar el análisis.');
        } finally {
            setIsLoading(false);
        }
    };
    
    const handleToggleMuscle = (muscleGroup: string) => {
        setExpandedMuscle(prev => prev === muscleGroup ? null : muscleGroup);
    };

    const getVolumeColor = (sets: number) => {
        if (sets <= 10) return 'text-green-400';
        if (sets <= 20) return 'text-yellow-400';
        return 'text-red-400';
    };
    
    if (!programs.length) {
        return null;
    }

    return (
        <Card>
            <div className="flex justify-between items-start mb-4">
                <h3 className="text-2xl font-bold text-white flex items-center gap-2">
                    <SparklesIcon/> Análisis de Volumen Semanal
                </h3>
                 {analysis && (
                     <Button onClick={handleGenerate} isLoading={isLoading} disabled={!isOnline} variant="secondary" className="!text-xs !py-1">
                        Regenerar
                     </Button>
                 )}
            </div>
            
            {isLoading && <SkeletonLoader lines={5}/>}
            {error && <p className="text-red-400 mt-2 text-center">{error}</p>}
            
            {!isLoading && analysis && (
                <div className="animate-fade-in">
                    <div className="flex overflow-x-auto space-x-3 pb-3 -mx-4 px-4 hide-scrollbar">
                        {analysis.sort((a, b) => b.displayVolume - a.displayVolume).map(item => (
                            <button key={item.muscleGroup} onClick={() => handleToggleMuscle(item.muscleGroup)} className={`flex-shrink-0 p-3 rounded-2xl border-2 transition-all duration-300 w-28 text-center transform hover:-translate-y-1 ${expandedMuscle === item.muscleGroup ? 'bg-slate-800 border-primary-color' : 'bg-slate-900 border-slate-700 hover:border-slate-500'}`}>
                                <p className="font-bold text-white truncate">{item.muscleGroup}</p>
                                <p className={`text-3xl font-black ${getVolumeColor(item.displayVolume)}`}>{item.displayVolume}</p>
                                <p className="text-xs text-slate-400">Series</p>
                            </button>
                        ))}
                    </div>

                    {expandedMuscle && (() => {
                        const item = analysis.find(item => item.muscleGroup === expandedMuscle);
                        const muscleInfo = muscleGroupData.find(m => m.name === expandedMuscle);
                        if (!item) return null;

                        const totalDirectSets = item.directExercises.reduce((a, b) => a + b.sets, 0);
                        const totalIndirectSets = item.indirectExercises.reduce((a, b) => a + b.sets, 0);
                        
                        let landmarkStatus = 'Info no disponible';
                        if (muscleInfo) {
                           const parseRange = (rangeStr: string) => rangeStr.split('-').map(Number);
                           const mev = parseRange(muscleInfo.volumeRecommendations.mev);
                           const mav = parseRange(muscleInfo.volumeRecommendations.mav);
                           const mrv = parseRange(muscleInfo.volumeRecommendations.mrv);
                           if (item.displayVolume < mev[0]) landmarkStatus = 'Debajo de MEV';
                           else if (item.displayVolume >= mev[0] && item.displayVolume <= mev[1]) landmarkStatus = 'Rango MEV';
                           else if (item.displayVolume > mev[1] && item.displayVolume <= mav[1]) landmarkStatus = 'Rango MAV';
                           else if (item.displayVolume > mav[1] && item.displayVolume <= mrv[1]) landmarkStatus = 'Rango MRV';
                           else if (item.displayVolume > mrv[1]) landmarkStatus = 'Sobre MRV';
                        }
                        
                        return (
                            <div className="mt-4 pt-4 border-t border-slate-700 animate-fade-in">
                                <div>
                                    <h4 className="text-xl font-bold text-white mb-3">Detalle de: {item.muscleGroup}</h4>
                                     
                                    <div className="glass-card-nested p-3 mb-4">
                                        <h5 className="font-semibold text-slate-200 mb-2">Métricas Clave</h5>
                                        <div className="grid grid-cols-3 text-center text-xs">
                                            <div><span className="font-bold text-lg text-white">{item.displayVolume}</span><p className="text-slate-400">Volumen Efectivo</p></div>
                                            <div><span className="font-bold text-lg text-white">{item.frequency}</span><p className="text-slate-400">Frecuencia Semanal</p></div>
                                            <div><span className="font-bold text-lg text-white">{item.avgRestDays ?? 'N/A'}</span><p className="text-slate-400">Descanso Prom.</p></div>
                                        </div>
                                    </div>
                                    
                                     {muscleInfo && (
                                        <div className="glass-card-nested p-3 mb-4">
                                            <h5 className="font-semibold text-slate-200 mb-2">Puntos de Referencia de Volumen</h5>
                                            <div className="grid grid-cols-4 text-center text-xs">
                                                <div><span className="font-bold text-lg text-white">{item.displayVolume}</span><p className="text-slate-400">Actual</p></div>
                                                <div><span className="font-bold text-lg text-green-400">{muscleInfo.volumeRecommendations.mev}</span><p className="text-slate-400">MEV</p></div>
                                                <div><span className="font-bold text-lg text-yellow-400">{muscleInfo.volumeRecommendations.mav}</span><p className="text-slate-400">MAV</p></div>
                                                <div><span className="font-bold text-lg text-red-400">{muscleInfo.volumeRecommendations.mrv}</span><p className="text-slate-400">MRV</p></div>
                                            </div>
                                            <p className="text-center text-sm font-semibold mt-2 text-primary-color">{landmarkStatus}</p>
                                        </div>
                                    )}

                                     <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                        <div>
                                            <h5 className="font-semibold text-sky-400 mb-2">Trabajo Directo ({totalDirectSets} series)</h5>
                                            {item.directExercises.length > 0 ? (
                                                <ul className="space-y-1 text-sm text-slate-300">
                                                    {item.directExercises.map(ex => (
                                                        <li key={ex.name} className="flex justify-between bg-slate-800/50 p-2 rounded-md">
                                                            <span className="truncate pr-2">{ex.name}</span>
                                                            <span className="font-mono text-slate-400 flex-shrink-0">{ex.sets} series</span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            ) : <p className="text-sm text-slate-500 italic p-2">Ninguno</p>}
                                        </div>
                                        <div>
                                            <h5 className="font-semibold text-orange-400 mb-2">Trabajo Indirecto ({totalIndirectSets} series)</h5>
                                            {item.indirectExercises.length > 0 ? (
                                                <ul className="space-y-1 text-sm text-slate-300">
                                                    {item.indirectExercises.map(ex => (
                                                        <li key={ex.name} className="flex justify-between bg-slate-800/50 p-2 rounded-md">
                                                            <span className="truncate pr-2">{ex.name} <span className="text-xs text-slate-500">({ex.activationPercentage}%)</span></span>
                                                            <span className="font-mono text-slate-400 flex-shrink-0">{ex.sets} series</span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            ) : <p className="text-sm text-slate-500 italic p-2">Ninguno</p>}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })()}
                </div>
            )}
            
             {!isLoading && !analysis && !error && (
                 <div className="text-center py-4 space-y-3">
                    <p className="text-sm text-slate-400">Analiza el volumen de entrenamiento semanal para todos tus programas, desglosado por grupo muscular.</p>
                    <Button onClick={handleGenerate} isLoading={isLoading} disabled={!isOnline}>
                        Analizar Volumen
                    </Button>
                </div>
             )}

        </Card>
    );
};

export default OverallVolumeAnalysis;