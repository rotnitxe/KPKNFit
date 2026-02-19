import React from 'react';
import { VolumeRecommendation } from '../services/volumeCalculator';
import { AlertTriangleIcon, CheckCircleIcon, InfoIcon, TrendingUpIcon } from './icons';

interface VolumeBudgetBarProps {
    currentVolume: Record<string, number>; // Ej: { "Pectoral": 12.5, "Tríceps": 6 }
    recommendation: VolumeRecommendation;
}

const VolumeBudgetBar: React.FC<VolumeBudgetBarProps> = ({ currentVolume, recommendation }) => {
    const { minSets, maxSets, optimalSets, type } = recommendation;
    
    // Filtramos solo los músculos que tienen volumen activo
    const activeMuscles = (Object.entries(currentVolume) as [string, number][])
        .filter(([_, vol]) => vol > 0)
        .sort((a, b) => b[1] - a[1]); // Ordenar por volumen descendente

    if (activeMuscles.length === 0) {
        return (
            <div className="bg-[#111] border border-white/10 rounded-2xl p-6 text-center animate-fade-in">
                <div className="bg-white/5 w-12 h-12 rounded-full flex items-center justify-center mx-auto mb-3">
                    <TrendingUpIcon className="text-gray-500" />
                </div>
                <p className="text-xs font-bold text-gray-400 uppercase tracking-widest">
                    Añade ejercicios para ver el análisis de volumen
                </p>
                <p className="text-[10px] text-gray-600 mt-1">
                    El algoritmo calculará el impacto fraccional en tiempo real.
                </p>
            </div>
        );
    }

    return (
        <div className="bg-[#111] border border-white/10 rounded-2xl p-5 animate-fade-in space-y-4">
            <div className="flex justify-between items-center border-b border-white/5 pb-3">
                <div className="flex items-center gap-2">
                    <TrendingUpIcon size={16} className="text-white" />
                    <span className="text-xs font-black text-white uppercase tracking-widest">
                        Presupuesto de Volumen Semanal
                    </span>
                </div>
                <div className="text-[9px] font-bold text-gray-500 uppercase">
                    Objetivo: {minSets}-{maxSets} {type === 'sets' ? 'Series' : 'Levantamientos'}
                </div>
            </div>

            <div className="space-y-4">
                {activeMuscles.map(([muscle, volume]) => {
                    // Lógica de Semáforo (Traffic Light Logic)
                    let statusColor = 'bg-gray-600';
                    let statusText = 'Bajo';
                    const percentage = Math.min((volume / maxSets) * 100, 100);
                    
                    if (volume < minSets) {
                        statusColor = 'bg-yellow-500'; // Insuficiente
                        statusText = 'Bajo';
                    } else if (volume >= minSets && volume <= maxSets) {
                        statusColor = 'bg-emerald-500'; // Óptimo
                        statusText = 'Óptimo';
                    } else {
                        statusColor = 'bg-red-500'; // Excesivo
                        statusText = 'Alto';
                    }

                    return (
                        <div key={muscle} className="group">
                            <div className="flex justify-between items-end mb-1">
                                <span className="text-[10px] font-bold text-gray-300 uppercase">{muscle}</span>
                                <div className="flex items-center gap-1.5">
                                    <span className={`text-[9px] font-black uppercase ${volume > maxSets ? 'text-red-400' : 'text-white'}`}>
                                        {volume.toFixed(1)} <span className="text-gray-600">/ {maxSets}</span>
                                    </span>
                                    {volume > maxSets && <AlertTriangleIcon size={10} className="text-red-500" />}
                                    {volume >= minSets && volume <= maxSets && <CheckCircleIcon size={10} className="text-emerald-500" />}
                                </div>
                            </div>
                            
                            {/* Barra de Progreso */}
                            <div className="h-1.5 w-full bg-black rounded-full overflow-hidden relative">
                                {/* Zona Óptima Marker (Fondo grisáceo) */}
                                <div 
                                    className="absolute top-0 bottom-0 bg-white/10" 
                                    style={{ 
                                        left: `${(minSets / maxSets) * 100}%`, 
                                        right: '0%' // Asumimos que el max es el 100% visual
                                    }} 
                                />
                                {/* Fill */}
                                <div 
                                    className={`h-full rounded-full transition-all duration-500 ${statusColor}`} 
                                    style={{ width: `${percentage}%` }} 
                                />
                            </div>
                            
                            {/* Feedback de Sobrecarga */}
                            {volume > maxSets && (
                                <p className="text-[9px] text-red-400 mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                    Excede capacidad de recuperación (+{(volume - maxSets).toFixed(1)})
                                </p>
                            )}
                        </div>
                    );
                })}
            </div>
            
            <div className="flex gap-4 pt-2 border-t border-white/5 justify-center opacity-50">
                <div className="flex items-center gap-1.5">
                     <div className="w-1.5 h-1.5 rounded-full bg-yellow-500"></div>
                     <span className="text-[8px] font-bold text-gray-500 uppercase">Acumulación</span>
                </div>
                <div className="flex items-center gap-1.5">
                     <div className="w-1.5 h-1.5 rounded-full bg-emerald-500"></div>
                     <span className="text-[8px] font-bold text-gray-500 uppercase">Óptimo</span>
                </div>
                <div className="flex items-center gap-1.5">
                     <div className="w-1.5 h-1.5 rounded-full bg-red-500"></div>
                     <span className="text-[8px] font-bold text-gray-500 uppercase">Sobrecarga</span>
                </div>
            </div>
        </div>
    );
};

export default VolumeBudgetBar;