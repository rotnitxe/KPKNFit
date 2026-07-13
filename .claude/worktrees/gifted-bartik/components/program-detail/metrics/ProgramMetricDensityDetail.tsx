import React from 'react';
import { Program, Session } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';

interface ProgramMetricDensityDetailProps {
    program: Program;
    displayedSessions: Session[];
}

const ProgramMetricDensityDetail: React.FC<ProgramMetricDensityDetailProps> = () => (
    <ProgramMetricDetailLayout title="Densidad">
        <div className="space-y-6">
            <p className="text-[11px] text-[#8E8E93]">
                Sets/min, tiempo bajo tensión. Heatmap sesión, distribución temporal.
            </p>
            <div className="h-48 rounded-xl bg-[#1a1a1a] border border-[#E6E0E9] flex items-center justify-center">
                <span className="text-[12px] text-[#48484A] font-bold">Gráfico en desarrollo</span>
            </div>
        </div>
    </ProgramMetricDetailLayout>
);

export default ProgramMetricDensityDetail;
