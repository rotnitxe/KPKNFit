import React from 'react';
import { Program, Session } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';
import { RelativeStrengthAndBasicsWidget } from '../../RelativeStrengthAndBasicsWidget';

interface ProgramMetricStrengthDetailProps {
    program: Program;
    displayedSessions: Session[];
}

const ProgramMetricStrengthDetail: React.FC<ProgramMetricStrengthDetailProps> = ({
    displayedSessions,
}) => (
    <ProgramMetricDetailLayout title="Fuerza">
        <div className="space-y-6">
            <p className="text-[11px] text-[#8E8E93]">
                1RM estimado, progresión por ejercicio. Gráfico línea 1RM en el tiempo, ranking ejercicios.
            </p>
            <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
        </div>
    </ProgramMetricDetailLayout>
);

export default ProgramMetricStrengthDetail;
