// components/CoachView.tsx
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


export default CoachView;