// components/CoachView.tsx
import React from 'react';
import { Program, WorkoutLog, Settings, BodyProgressLog, NutritionLog, SkippedWorkoutLog } from '../types';
import AICoachDashboard from './AICoachDashboard';
import OverallVolumeAnalysis from './OverallVolumeAnalysis';


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
        <div className="animate-fade-in space-y-8">
            <div>
                <h1 className="text-4xl font-bold uppercase tracking-wider">Coach IA</h1>
            </div>
            
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
                <div className="lg:col-span-2">
                    <AICoachDashboard {...props} />
                </div>
                <OverallVolumeAnalysis programs={props.programs} isOnline={props.isOnline} settings={props.settings} />
            </div>
        </div>
    );
};

export default CoachView;