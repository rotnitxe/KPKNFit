import React from 'react';
import { Program, WorkoutLog, Settings, BodyProgressLog, NutritionLog, SkippedWorkoutLog } from '../types';

interface CoachViewProps {
    programs: Program[];
    history: WorkoutLog[];
    skippedLogs: SkippedWorkoutLog[];
    settings: Settings;
    bodyProgress: BodyProgressLog[];
    nutritionLogs: NutritionLog[];
    isOnline: boolean;
}

const CoachView: React.FC<CoachViewProps> = (props) => {
    return (
        <div className="animate-fade-in space-y-8 pt-8 text-center">
            <div>
                <h1 className="text-4xl font-bold uppercase tracking-wider text-white">Coach IA</h1>
            </div>
            
            <div className="bg-zinc-900/50 border border-[#E6E0E9] p-8 rounded-2xl">
                <p className="text-[#49454F]">
                    El Dashboard del Coach está siendo actualizado y consolidado al nuevo sistema.
                </p>
            </div>
        </div>
    );
};

export default CoachView;