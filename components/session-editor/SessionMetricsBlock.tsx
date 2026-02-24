import React from 'react';
import { Session, ExerciseMuscleInfo } from '../../types';
import { Settings } from '../../types';

interface SessionMetricsBlockProps {
    session: Session;
    exerciseList: ExerciseMuscleInfo[];
    settings: Settings;
    muscleDrainThreshold?: number;
}

/** Duración, series, dificultad, fatiga y volumen por músculo se ven en el FAB AUGE (drawer). */
export const SessionMetricsBlock: React.FC<SessionMetricsBlockProps> = () => {
    return null;
};
