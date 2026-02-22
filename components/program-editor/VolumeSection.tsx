import React, { useMemo } from 'react';
import { Program, ExerciseMuscleInfo } from '../../types';
import { ActivityIcon } from '../icons';
import { calculateUnifiedMuscleVolume } from '../../services/volumeCalculator';

interface VolumeSectionProps {
    program: Program;
    exerciseList: ExerciseMuscleInfo[];
}

const VolumeSection: React.FC<VolumeSectionProps> = ({ program, exerciseList }) => {
    const weeklyVolumes = useMemo(() => {
        const result: { weekName: string; muscles: Record<string, number> }[] = [];
        program.macrocycles.forEach(macro => {
            (macro.blocks || []).forEach(block => {
                block.mesocycles.forEach(meso => {
                    meso.weeks.forEach(week => {
                        const vol = calculateUnifiedMuscleVolume(week.sessions, exerciseList);
                        const muscles: Record<string, number> = {};
                        vol.forEach(v => { muscles[v.muscleName] = v.displayVolume; });
                        result.push({ weekName: week.name, muscles });
                    });
                });
            });
        });
        return result;
    }, [program, exerciseList]);

    const allMuscles = useMemo(() => {
        const set = new Set<string>();
        weeklyVolumes.forEach(w => Object.keys(w.muscles).forEach(m => set.add(m)));
        return Array.from(set).sort();
    }, [weeklyVolumes]);

    const maxVolume = useMemo(() => {
        let max = 1;
        weeklyVolumes.forEach(w => Object.values(w.muscles).forEach(v => { if (v > max) max = v; }));
        return max;
    }, [weeklyVolumes]);

    return (
        <div className="space-y-4">
            <h3 className="text-xs font-black text-white uppercase tracking-widest flex items-center gap-2">
                <ActivityIcon size={14} className="text-zinc-400" /> Volumen Planificado
            </h3>

            {weeklyVolumes.length > 0 && allMuscles.length > 0 ? (
                <div className="bg-zinc-950 border border-white/5 rounded-xl p-4 overflow-x-auto">
                    {/* Heatmap */}
                    <div className="min-w-[400px]">
                        <div className="flex mb-2">
                            <div className="w-24 shrink-0" />
                            {weeklyVolumes.map((w, i) => (
                                <div key={i} className="flex-1 text-center text-[7px] font-black text-zinc-500 uppercase">
                                    {w.weekName.replace('Semana ', 'S')}
                                </div>
                            ))}
                        </div>
                        {allMuscles.slice(0, 15).map(muscle => (
                            <div key={muscle} className="flex items-center mb-0.5">
                                <div className="w-24 shrink-0 text-[8px] font-bold text-zinc-400 truncate pr-2">{muscle}</div>
                                {weeklyVolumes.map((w, i) => {
                                    const vol = w.muscles[muscle] || 0;
                                    const intensity = maxVolume > 0 ? vol / maxVolume : 0;
                                    return (
                                        <div key={i} className="flex-1 px-0.5">
                                            <div
                                                className="h-4 rounded-sm transition-colors"
                                                style={{
                                                    backgroundColor: vol === 0
                                                        ? 'rgb(39, 39, 42)'
                                                        : `rgba(59, 130, 246, ${0.15 + intensity * 0.85})`,
                                                }}
                                                title={`${muscle}: ${vol.toFixed(1)} sets`}
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        ))}
                    </div>

                    {/* Legend */}
                    <div className="flex items-center justify-end gap-2 mt-3">
                        <span className="text-[7px] text-zinc-500 font-bold">Bajo</span>
                        <div className="flex gap-0.5">
                            {[0.15, 0.35, 0.55, 0.75, 1].map((o, i) => (
                                <div key={i} className="w-4 h-2 rounded-sm" style={{ backgroundColor: `rgba(59, 130, 246, ${o})` }} />
                            ))}
                        </div>
                        <span className="text-[7px] text-zinc-500 font-bold">Alto</span>
                    </div>
                </div>
            ) : (
                <div className="bg-zinc-950 border border-white/5 rounded-xl p-6 text-center">
                    <ActivityIcon size={24} className="text-zinc-700 mx-auto mb-2" />
                    <p className="text-[9px] text-zinc-500 font-bold">Agrega sesiones con ejercicios para ver el volumen planificado.</p>
                </div>
            )}

            {/* Overtraining alerts placeholder */}
            <div className="bg-zinc-950 border border-white/5 rounded-xl p-4">
                <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-2">Alertas de Sobreentrenamiento</span>
                <p className="text-[9px] text-zinc-600 italic">Análisis predictivo disponible con suficientes datos de sesiones.</p>
            </div>
        </div>
    );
};

export default VolumeSection;
