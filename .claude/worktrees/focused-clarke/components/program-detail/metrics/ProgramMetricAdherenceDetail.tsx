import React from 'react';
import { Program } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';

interface ProgramMetricAdherenceDetailProps {
    program: Program;
    totalAdherence: number;
    weeklyAdherence: { weekName: string; pct: number }[];
}

const ProgramMetricAdherenceDetail: React.FC<ProgramMetricAdherenceDetailProps> = ({
    totalAdherence, weeklyAdherence,
}) => (
    <ProgramMetricDetailLayout title="Adherencia">
        <div className="space-y-6">
            <p className="text-[11px] text-[#8E8E93]">
                % por semana, tendencia. Gráfico área, desglose por sesión.
            </p>
            <div className="flex flex-col items-center gap-4">
                <div className="relative w-24 h-24 shrink-0">
                    <svg viewBox="0 0 36 36" className="w-full h-full transform -rotate-90">
                        <path className="text-[#1a1a1a]" strokeWidth="3.5" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                        <path
                            strokeWidth="3.5" strokeDasharray={`${totalAdherence}, 100`} strokeLinecap="round" stroke="currentColor" fill="none"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                            style={{ color: totalAdherence >= 80 ? '#00F19F' : totalAdherence >= 50 ? '#FFD60A' : '#FF3B30' }}
                        />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                        <span className="text-2xl font-black text-white">{totalAdherence}%</span>
                    </div>
                </div>
                <div className="w-full space-y-2">
                    {weeklyAdherence.map((week, i) => (
                        <div key={i} className="flex items-center gap-2">
                            <span className="w-14 text-right text-[10px] font-bold text-[#48484A]">{week.weekName}</span>
                            <div className="flex-1 h-2.5 bg-[#1a1a1a] rounded-full overflow-hidden">
                                <div
                                    className="h-full rounded-full transition-all"
                                    style={{
                                        width: `${week.pct}%`,
                                        backgroundColor: week.pct === 100 ? '#00F19F' : week.pct > 50 ? '#FFD60A' : '#FF3B30',
                                    }}
                                />
                            </div>
                            <span className="w-8 text-[10px] font-bold text-white">{week.pct}%</span>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    </ProgramMetricDetailLayout>
);

export default ProgramMetricAdherenceDetail;
