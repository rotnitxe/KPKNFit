import React from 'react';
import { WorkoutLog, Settings } from '../types';

const RelativeStrengthCard: React.FC<{ history: WorkoutLog[]; settings: Settings }> = ({ history, settings }) => {
    // Placeholder: Aquí deberías buscar en 'history' los mejores levantamientos reales
    const squat = 0; 
    const bench = 0; 
    const deadlift = 0; 
    const bodyweight = 80; // Idealmente obtener del perfil del usuario

    const total = squat + bench + deadlift;
    const ratio = total > 0 ? (total / bodyweight).toFixed(2) : '0.00';

    return (
        <div className="bg-[#111] border border-white/10 rounded-3xl p-5">
            <h4 className="text-[9px] font-black text-gray-500 uppercase tracking-widest mb-3">Fuerza SBD</h4>
            
            <div className="flex justify-between items-end mb-4">
                <span className="text-3xl font-black text-white">{ratio}x</span>
                <span className="text-[10px] text-gray-400 mb-1">Peso Corporal</span>
            </div>

            <div className="space-y-1">
                <div className="flex justify-between text-[10px]">
                    <span className="text-gray-500">Squat</span>
                    <span className="font-bold text-white">{squat} {settings.weightUnit}</span>
                </div>
                 <div className="flex justify-between text-[10px]">
                    <span className="text-gray-500">Bench</span>
                    <span className="font-bold text-white">{bench} {settings.weightUnit}</span>
                </div>
                 <div className="flex justify-between text-[10px]">
                    <span className="text-gray-500">Deadlift</span>
                    <span className="font-bold text-white">{deadlift} {settings.weightUnit}</span>
                </div>
            </div>
        </div>
    );
};

export default RelativeStrengthCard;