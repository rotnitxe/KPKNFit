import React, { useMemo } from 'react';
import { Program, Session } from '../../../types';
import ProgramMetricDetailLayout from './ProgramMetricDetailLayout';

interface ProgramMetricRecoveryDetailProps {
    program: Program;
    adaptiveCache: any;
}

const ProgramMetricRecoveryDetail: React.FC<ProgramMetricRecoveryDetailProps> = ({
    adaptiveCache,
}) => {
    const recoveryData = useMemo(() => {
        const entries = Object.entries(adaptiveCache?.personalizedRecoveryHours || {});
        return entries.sort((a: [string, any], b: [string, any]) => b[1] - a[1]).slice(0, 12);
    }, [adaptiveCache]);

    return (
        <ProgramMetricDetailLayout title="Recuperación">
            <div className="space-y-6">
                <p className="text-[11px] text-[#8E8E93]">
                    Horas por músculo. Mapa de barras, alertas de sobreentreno.
                </p>
                {recoveryData.length > 0 ? (
                    <div className="space-y-2">
                        {recoveryData.map(([muscle, hours]) => (
                            <div key={muscle} className="flex items-center gap-2">
                                <span className="w-24 text-[10px] font-bold text-[#8E8E93] truncate text-right shrink-0">{muscle}</span>
                                <div className="flex-1 h-3 bg-[#1a1a1a] rounded-full overflow-hidden">
                                    <div
                                        className="h-full rounded-full"
                                        style={{
                                            width: `${Math.min(100, (hours / 96) * 100)}%`,
                                            backgroundColor: hours > 72 ? '#FF3B30' : hours > 48 ? '#FFD60A' : '#00F19F',
                                        }}
                                    />
                                </div>
                                <span className="w-10 text-[10px] font-bold text-white text-right">{Math.round(hours)}h</span>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="h-48 rounded-xl bg-[#1a1a1a] border border-white/5 flex items-center justify-center">
                        <span className="text-[12px] text-[#48484A] font-bold">Datos insuficientes</span>
                    </div>
                )}
            </div>
        </ProgramMetricDetailLayout>
    );
};

export default ProgramMetricRecoveryDetail;
