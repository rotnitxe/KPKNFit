import React from 'react';
import { Program, Session } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';

interface ProgramMetricRPEDetailProps {
    program: Program;
    displayedSessions: Session[];
}

const ProgramMetricRPEDetail: React.FC<ProgramMetricRPEDetailProps> = () => (
    <ProgramMetricDetailLayout title="RPE/Intensidad">
        <div className="space-y-6">
            <p className="text-[11px] text-[#8E8E93]">
                Distribución RPE por sesión. Histograma, evolución RPE promedio.
            </p>
            <div className="h-48 rounded-xl bg-[#1a1a1a] border border-white/5 flex items-center justify-center">
                <span className="text-[12px] text-[#48484A] font-bold">Gráfico en desarrollo</span>
            </div>
        </div>
    </ProgramMetricDetailLayout>
);

export default ProgramMetricRPEDetail;
