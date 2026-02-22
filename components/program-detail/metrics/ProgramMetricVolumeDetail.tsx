import React from 'react';
import { Program, Session } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';
import { WorkoutVolumeAnalysis } from '../../WorkoutVolumeAnalysis';

interface ProgramMetricVolumeDetailProps {
    program: Program;
    history: any[];
    displayedSessions: Session[];
    settings: any;
    isOnline: boolean;
}

const ProgramMetricVolumeDetail: React.FC<ProgramMetricVolumeDetailProps> = ({
    program, history, displayedSessions, settings, isOnline,
}) => (
    <ProgramMetricDetailLayout title="Volumen D/I">
        <div className="space-y-6">
            <p className="text-[11px] text-[#8E8E93]">
                Sets directos vs indirectos por grupo muscular. MEV/MAV/MRV y tendencia semanal.
            </p>
            <WorkoutVolumeAnalysis
                program={program}
                sessions={displayedSessions}
                history={history}
                isOnline={isOnline}
                settings={settings}
            />
        </div>
    </ProgramMetricDetailLayout>
);

export default ProgramMetricVolumeDetail;
