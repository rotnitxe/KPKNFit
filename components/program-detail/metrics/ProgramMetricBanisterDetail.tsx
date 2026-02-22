import React from 'react';
import { Program, Session } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';
import { BanisterTrend } from '../../ui/AugeDeepView';

interface ProgramMetricBanisterDetailProps {
    program: Program;
    adaptiveCache: any;
}

const ProgramMetricBanisterDetail: React.FC<ProgramMetricBanisterDetailProps> = ({
    adaptiveCache,
}) => (
    <ProgramMetricDetailLayout title="AUGE Banister">
        <div className="space-y-6">
            <p className="text-[11px] text-[#8E8E93]">
                Fitness vs fatiga. Curva Banister interactiva, predicciones.
            </p>
            {adaptiveCache?.banister && (
                <div className="bg-[#1a1a1a] rounded-xl p-4 border border-white/5">
                    <p className="text-[10px] text-[#48484A] mb-3">
                        {adaptiveCache.banister.verdict || 'Modelo Banister activo'}
                    </p>
                    <BanisterTrend systemData={adaptiveCache.banister?.systems?.muscular || null} />
                </div>
            )}
            {!adaptiveCache?.banister && (
                <div className="h-48 rounded-xl bg-[#1a1a1a] border border-white/5 flex items-center justify-center">
                    <span className="text-[12px] text-[#48484A] font-bold">Datos insuficientes</span>
                </div>
            )}
        </div>
    </ProgramMetricDetailLayout>
);

export default ProgramMetricBanisterDetail;
