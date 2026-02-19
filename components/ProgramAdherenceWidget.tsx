import React from 'react';
import { Program, WorkoutLog } from '../types';
import { CheckCircleIcon, XIcon } from './icons';

const ProgramAdherenceWidget: React.FC<{ program: Program; history: WorkoutLog[] }> = ({ program, history }) => {
    const totalSessions = program.macrocycles.flatMap(m => m.blocks?.flatMap(b => b.mesocycles.flatMap(me => me.weeks.flatMap(w => w.sessions)))).length || 1;
    const completedSessions = history.length; 
    
    const percentage = Math.min(100, Math.round((completedSessions / totalSessions) * 100));
    const isGood = percentage >= 80;

    return (
        <div className="bg-[#111] border border-white/10 rounded-3xl p-5 relative overflow-hidden">
            <h4 className="text-[9px] font-black text-gray-500 uppercase tracking-widest mb-2">Adherencia</h4>
            <div className="flex items-end gap-2">
                <span className={`text-4xl font-black ${isGood ? 'text-white' : 'text-red-400'}`}>{percentage}%</span>
                <span className="text-xs font-bold text-gray-500 mb-1">Completado</span>
            </div>
            
            <div className="w-full h-2 bg-gray-800 rounded-full mt-3 overflow-hidden">
                <div 
                    className={`h-full rounded-full transition-all duration-1000 ${isGood ? 'bg-white' : 'bg-red-500'}`} 
                    style={{ width: `${percentage}%` }}
                />
            </div>
            
            <div className="absolute top-4 right-4 opacity-20 text-white">
                {isGood ? <CheckCircleIcon size={32} /> : <XIcon size={32} />}
            </div>
        </div>
    );
};

export default ProgramAdherenceWidget;