import React, { useState, useMemo } from 'react';
import { Session, Exercise, ExerciseMuscleInfo } from '../../types';
import { ClockIcon, ActivityIcon } from '../icons';
import { calculateSetBatteryDrain, calculatePersonalizedBatteryTanks } from '../../services/auge';
import { normalizeMuscleGroup } from '../../services/volumeCalculator';
import { Settings } from '../../types';

interface SessionMetricsBlockProps {
    session: Session;
    exerciseList: ExerciseMuscleInfo[];
    settings: Settings;
    muscleDrainThreshold?: number;
}

const MiniWidget: React.FC<{ label: string; value: string | number; unit?: string; className?: string }> = ({ label, value, unit, className = '' }) => (
    <div className={`shrink-0 w-20 h-20 rounded-full bg-white/[0.04] border border-white/10 flex flex-col items-center justify-center ${className}`}>
        <span className="text-[10px] font-bold text-[#555] uppercase tracking-wider">{label}</span>
        <span className="text-sm font-black text-white font-mono">{value}{unit || ''}</span>
    </div>
);

export const SessionMetricsBlock: React.FC<SessionMetricsBlockProps> = ({
    session, exerciseList, settings, muscleDrainThreshold = 10,
}) => {
    const [expandedFatiga, setExpandedFatiga] = useState<'msc' | 'snc' | 'spinal' | null>(null);
    const tanks = useMemo(() => calculatePersonalizedBatteryTanks(settings), [settings]);

    const { totalSets, estimatedDuration, msc, snc, spinal, difficulty, muscleVolume, muscleDrain, sncRanking, spinalRanking } = useMemo(() => {
        let totalSets = 0, msc = 0, snc = 0, spinal = 0;
        const muscleVol: Record<string, number> = {};
        const muscleDrainMap: Record<string, number> = {};
        const sncList: { id: string; name: string; fatigue: number }[] = [];
        const spinalList: { id: string; name: string; fatigue: number }[] = [];
        let rpeSum = 0, rpeCount = 0;
        let rmProximitySum = 0, rmCount = 0;

        const allEx = [...(session.exercises || [])];
        (session.parts || []).forEach(p => allEx.push(...p.exercises));

        allEx.forEach(ex => {
            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.name);
            if (!info) return;
            const validSets = ex.sets?.filter(s => (s as any).type !== 'warmup') || [];
            totalSets += validSets.length;
            let exSnc = 0, exSpinal = 0, exMsc = 0;
            const muscleSetCount: Record<string, number> = {};

            validSets.forEach(set => {
                const acc = muscleSetCount[info.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'General'] || 0;
                const drain = calculateSetBatteryDrain(set, info, tanks, acc, ex.restTime || 90);
                muscleSetCount[info.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'General'] = acc + 1;
                exMsc += drain.muscularDrainPct;
                exSnc += drain.cnsDrainPct;
                exSpinal += (info.axialLoadFactor || 0) * 2; // per set

                const primaryMuscle = normalizeMuscleGroup(info.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'General');
                muscleVol[primaryMuscle] = (muscleVol[primaryMuscle] || 0) + 1;
                muscleDrainMap[primaryMuscle] = (muscleDrainMap[primaryMuscle] || 0) + drain.muscularDrainPct;

                if (set.targetRPE !== undefined) { rpeSum += set.targetRPE; rpeCount++; }
                if (set.intensityMode === 'failure') { rpeSum += 10; rpeCount++; }
                if (set.targetRIR !== undefined) { rpeSum += 10 - set.targetRIR; rpeCount++; }
                if (ex.trainingMode === 'percent' && set.targetPercentageRM) {
                    rmProximitySum += set.targetPercentageRM / 100;
                    rmCount++;
                }
            });

            sncList.push({ id: ex.id, name: ex.name, fatigue: exSnc });
            spinalList.push({ id: ex.id, name: ex.name, fatigue: exSpinal });
            msc += exMsc;
            snc += exSnc;
            spinal += exSpinal;
        });

        const avgRpe = rpeCount > 0 ? rpeSum / rpeCount : 0;
        const avgRm = rmCount > 0 ? rmProximitySum / rmCount : 0;
        const difficulty = Math.min(10, Math.round((avgRpe / 10) * 3 + (avgRm * 5) + 2));

        return {
            totalSets,
            estimatedDuration: Math.round(totalSets * 2.5),
            msc: Math.min(100, msc),
            snc: Math.min(100, snc),
            spinal: Math.min(100, spinal),
            difficulty,
            muscleVolume: Object.entries(muscleVol).map(([m, v]) => ({ muscle: m, sets: v })),
            muscleDrain: Object.entries(muscleDrainMap).filter(([, d]) => d >= muscleDrainThreshold).map(([m, d]) => ({ muscle: m, drain: d })).sort((a, b) => b.drain - a.drain),
            sncRanking: sncList.sort((a, b) => b.fatigue - a.fatigue),
            spinalRanking: spinalList.sort((a, b) => b.fatigue - a.fatigue),
        };
    }, [session, exerciseList, tanks, muscleDrainThreshold]);

    return (
        <div className="mx-4 mt-4 mb-6 space-y-4">
            {/* Mini-widgets carousel */}
            <div className="flex gap-4 overflow-x-auto no-scrollbar pb-2">
                <MiniWidget label="Duración" value={estimatedDuration} unit="min" />
                <MiniWidget label="Series" value={totalSets} />
                <MiniWidget label="Dificultad" value={difficulty} unit="/10" className="border-cyber-cyan/30" />
            </div>

            {/* Fatiga central */}
            <div className="rounded-xl bg-white/[0.03] border border-white/[0.08] p-4">
                <h3 className="text-[10px] font-black uppercase tracking-widest text-[#555] mb-3">Fatiga de sesión</h3>
                <div className="flex gap-3">
                    <button
                        onClick={() => setExpandedFatiga(expandedFatiga === 'msc' ? null : 'msc')}
                        className={`flex-1 p-3 rounded-lg border transition-all text-left ${expandedFatiga === 'msc' ? 'bg-[#FF3B30]/10 border-[#FF3B30]/30' : 'bg-white/[0.02] border-white/[0.06] hover:border-white/10'}`}
                    >
                        <div className="flex justify-between items-center mb-1">
                            <span className="text-[10px] font-bold text-[#555]">MSC</span>
                            <span className="text-xs font-mono font-bold text-white">{Math.round(msc)}%</span>
                        </div>
                        <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                            <div className="h-full bg-[#FF3B30] rounded-full transition-all" style={{ width: `${Math.min(100, msc)}%` }} />
                        </div>
                    </button>
                    <button
                        onClick={() => setExpandedFatiga(expandedFatiga === 'snc' ? null : 'snc')}
                        className={`flex-1 p-3 rounded-lg border transition-all text-left ${expandedFatiga === 'snc' ? 'bg-[#FFD60A]/10 border-[#FFD60A]/30' : 'bg-white/[0.02] border-white/[0.06] hover:border-white/10'}`}
                    >
                        <div className="flex justify-between items-center mb-1">
                            <span className="text-[10px] font-bold text-[#555]">SNC</span>
                            <span className="text-xs font-mono font-bold text-white">{Math.round(snc)}%</span>
                        </div>
                        <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                            <div className="h-full bg-[#FFD60A] rounded-full transition-all" style={{ width: `${Math.min(100, snc)}%` }} />
                        </div>
                    </button>
                    <button
                        onClick={() => setExpandedFatiga(expandedFatiga === 'spinal' ? null : 'spinal')}
                        className={`flex-1 p-3 rounded-lg border transition-all text-left ${expandedFatiga === 'spinal' ? 'bg-[#F97316]/10 border-[#F97316]/30' : 'bg-white/[0.02] border-white/[0.06] hover:border-white/10'}`}
                    >
                        <div className="flex justify-between items-center mb-1">
                            <span className="text-[10px] font-bold text-[#555]">Espinal</span>
                            <span className="text-xs font-mono font-bold text-white">{Math.round(spinal)}%</span>
                        </div>
                        <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                            <div className="h-full bg-[#F97316] rounded-full transition-all" style={{ width: `${Math.min(100, spinal)}%` }} />
                        </div>
                    </button>
                </div>

                {expandedFatiga === 'msc' && muscleDrain.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-white/5">
                        <p className="text-[10px] font-bold text-[#555] mb-2">Drenaje por músculo</p>
                        <div className="flex gap-2 overflow-x-auto no-scrollbar">
                            {muscleDrain.map(({ muscle, drain }) => (
                                <div key={muscle} className="shrink-0 w-24 p-2 rounded-lg bg-white/[0.03] border border-white/5 text-center">
                                    <div className="text-[9px] font-bold text-[#555] truncate">{muscle}</div>
                                    <div className="text-xs font-mono font-bold text-[#FF3B30]">{Math.round(drain)}%</div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
                {expandedFatiga === 'snc' && sncRanking.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-white/5">
                        <p className="text-[10px] font-bold text-[#555] mb-2">Ejercicios más fatigantes (SNC)</p>
                        <div className="space-y-1 max-h-32 overflow-y-auto">
                            {sncRanking.filter(r => r.fatigue > 0).map((r, i) => (
                                <div key={r.id} className="flex justify-between items-center text-xs">
                                    <span className="text-white truncate">{r.name}</span>
                                    <span className="text-[#FFD60A] font-mono shrink-0">{Math.round(r.fatigue)}%</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
                {expandedFatiga === 'spinal' && spinalRanking.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-white/5">
                        <p className="text-[10px] font-bold text-[#555] mb-2">Ejercicio más fatigante espinal</p>
                        <div className="space-y-1">
                            {spinalRanking.filter(r => r.fatigue > 0).slice(0, 5).map((r) => (
                                <div key={r.id} className="flex justify-between items-center text-xs">
                                    <span className="text-white truncate">{r.name}</span>
                                    <span className="text-[#F97316] font-mono shrink-0">{Math.round(r.fatigue)}%</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>

            {/* Volumen por músculo en tiempo real */}
            {muscleVolume.length > 0 && (
                <div className="rounded-lg bg-white/[0.02] border border-white/[0.05] p-3">
                    <h3 className="text-[10px] font-black uppercase tracking-widest text-[#555] mb-2">Volumen por músculo</h3>
                    <div className="flex flex-wrap gap-2">
                        {muscleVolume.map(({ muscle, sets }) => (
                            <div key={muscle} className="px-2 py-1 rounded bg-[#00F0FF]/10 border border-[#00F0FF]/20 text-[10px] font-bold">
                                {muscle}: {sets} sets
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
};
