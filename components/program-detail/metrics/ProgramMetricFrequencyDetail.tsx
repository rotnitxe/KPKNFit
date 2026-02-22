import React from 'react';
import { Program, Session } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';

interface ProgramMetricFrequencyDetailProps {
    program: Program;
    displayedSessions: Session[];
}

const ProgramMetricFrequencyDetail: React.FC<ProgramMetricFrequencyDetailProps> = () => (
    <ProgramMetricDetailLayout title="Frecuencia">
        <div className="space-y-6">
            <p className="text-[11px] text-[#8E8E93]">
                Días/sem por grupo muscular. Gráfico barras, calendario de frecuencia.
            </p>
            <div className="h-48 rounded-xl bg-[#1a1a1a] border border-white/5 flex items-center justify-center">
                <span className="text-[12px] text-[#48484A] font-bold">Gráfico en desarrollo</span>
            </div>
        </div>
    </ProgramMetricDetailLayout>
);

export default ProgramMetricFrequencyDetail;
