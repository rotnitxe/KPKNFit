// components/EffectiveVolumeCard.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { calculateEffectiveWeeklyVolume } from '../services/analysisService';
import Card from './ui/Card';
import { InfoTooltip } from './ui/InfoTooltip';

const EffectiveVolumeCard: React.FC = () => {
    const { history, exerciseList, muscleHierarchy, settings } = useAppState();
    const volumeData = useMemo(() => {
        return calculateEffectiveWeeklyVolume(history, exerciseList, muscleHierarchy, settings).sort((a, b) => b.displayVolume - a.displayVolume).slice(0, 5);
    }, [history, exerciseList, muscleHierarchy, settings]);

    if (volumeData.length === 0) return null;

    const maxVolume = volumeData[0]?.displayVolume || 1;

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-1">
                Volumen Efectivo Semanal <InfoTooltip term="Series Efectivas" />
            </h3>
            <p className="text-sm text-slate-400 mb-4">Muestra las series semanales que realmente estimulan el crecimiento (RPE ≥ 6 o RIR ≤ 4).</p>
            <div className="space-y-3">
                {volumeData.map(item => (
                    <div key={item.muscleGroup}>
                        <div className="flex justify-between items-baseline mb-1">
                            <span className="text-sm font-semibold text-slate-200">{item.muscleGroup}</span>
                            <span className="text-xs font-bold text-white">{item.displayVolume} series</span>
                        </div>
                        <div className="w-full bg-slate-700 rounded-full h-2.5">
                            <div className="bg-primary-gradient h-2.5 rounded-full" style={{ width: `${(item.displayVolume / maxVolume) * 100}%` }}></div>
                        </div>
                    </div>
                ))}
            </div>
        </Card>
    );
};

export default EffectiveVolumeCard;