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
                        vol.forEach(v => { muscles[v.muscleGroup] = v.displayVolume; });
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
        <div className="space-y-6 p-4 rounded-3xl">
            <h3 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-[0.2em] flex items-center gap-2">
                <ActivityIcon size={16} className="text-[var(--md-sys-color-primary)]" /> Distribución de Volumen
            </h3>

            {weeklyVolumes.length > 0 && allMuscles.length > 0 ? (
                <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-6 shadow-sm overflow-hidden">
                    <div className="flex items-center justify-between mb-6">
                        <div>
                            <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest block mb-1">Mapa de Calor</span>
                            <h4 className="text-title-small font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tight">Sets por Grupo Muscular</h4>
                        </div>
                        {/* Legend */}
                        <div className="flex items-center gap-3 bg-[var(--md-sys-color-surface-container-high)] px-3 py-1.5 rounded-full border border-[var(--md-sys-color-outline-variant)]">
                            <span className="text-[8px] text-[var(--md-sys-color-on-surface-variant)] font-black uppercase">Low</span>
                            <div className="flex gap-1">
                                {[0.1, 0.3, 0.5, 0.7, 0.9].map((o, i) => (
                                    <div key={i} className="w-4 h-3 rounded-md" style={{ backgroundColor: `rgba(var(--md-sys-color-primary-rgb), ${o})` }} />
                                ))}
                            </div>
                            <span className="text-[8px] text-[var(--md-sys-color-on-surface-variant)] font-black uppercase">High</span>
                        </div>
                    </div>

                    <div className="overflow-x-auto custom-scrollbar pb-4 text-[var(--md-sys-color-on-surface)]">
                        <div className="min-w-[500px]">
                            <div className="flex mb-4">
                                <div className="w-32 shrink-0" />
                                {weeklyVolumes.map((w, i) => (
                                    <div key={i} className="flex-1 text-center text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-tight">
                                        {w.weekName.replace('Semana ', 'W')}
                                    </div>
                                ))}
                            </div>
                            <div className="space-y-1.5">
                                {allMuscles.slice(0, 15).map(muscle => (
                                    <div key={muscle} className="flex items-center">
                                        <div className="w-32 shrink-0 text-label-small font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tighter truncate pr-4">{muscle}</div>
                                        {weeklyVolumes.map((w, i) => {
                                            const vol = w.muscles[muscle] || 0;
                                            const intensity = maxVolume > 0 ? vol / maxVolume : 0;
                                            return (
                                                <div key={i} className="flex-1 px-1">
                                                    <div
                                                        className="h-8 rounded-xl border border-[var(--md-sys-color-surface)] transition-all transform hover:scale-[1.05] hover:z-10 shadow-sm"
                                                        style={{
                                                            backgroundColor: vol === 0
                                                                ? 'var(--md-sys-color-surface-container-lowest)'
                                                                : `rgba(var(--md-sys-color-primary-rgb), ${0.1 + intensity * 0.8})`,
                                                        }}
                                                        title={`${muscle}: ${vol.toFixed(1)} sets`}
                                                    >
                                                        {vol > 0 && (
                                                            <div className="w-full h-full flex items-center justify-center">
                                                                <span className={`text-[8px] font-black ${intensity > 0.5 ? 'text-[var(--md-sys-color-on-primary)]' : 'text-[var(--md-sys-color-primary)]'}`}>
                                                                    {vol.toFixed(1)}
                                                                </span>
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>
            ) : (
                <div className="bg-[var(--md-sys-color-surface-container-low)] border-2 border-dashed border-[var(--md-sys-color-outline-variant)] rounded-3xl p-12 text-center">
                    <div className="w-20 h-20 bg-[var(--md-sys-color-surface-container-high)] rounded-full flex items-center justify-center mx-auto mb-4 shadow-sm border border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-outline-variant)]">
                        <ActivityIcon size={32} />
                    </div>
                    <p className="text-label-small text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-[0.2em]">Pendiente de asignación de carga</p>
                    <p className="text-[9px] text-[var(--md-sys-color-outline)] font-bold uppercase mt-2">Añade ejercicios a las semanas de entrenamiento</p>
                </div>
            )}

            {/* Smart Alerts */}
            <div className="bg-[var(--md-sys-color-tertiary-container)] text-[var(--md-sys-color-on-tertiary-container)] border border-[var(--md-sys-color-tertiary)]/20 rounded-3xl p-6 shadow-sm flex items-start gap-4">
                <div className="w-12 h-12 rounded-2xl bg-[var(--md-sys-color-surface)]/50 flex items-center justify-center text-[var(--md-sys-color-tertiary)] shrink-0 shadow-inner">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="m12 9 4.1 3-1.6 5h-5l-1.6-5L12 9z" /><path d="m12 3 1.9 5.8a2 2 0 0 0 1.8 1.4H22l-5 3.7a2 2 0 0 0-.7 2.3l1.9 5.8-4.9-3.5a2 2 0 0 0-2.4 0l-4.9 3.5 1.9-5.8a2 2 0 0 0-.7-2.3L2 10.2h6.3a2 2 0 0 0 1.8-1.4L12 3z" /></svg>
                </div>
                <div>
                    <span className="text-label-small font-black uppercase tracking-widest block mb-1">Carga Predictiva AI</span>
                    <h4 className="text-title-small font-black uppercase tracking-tight mb-2">Análisis de Estrés Sistémico</h4>
                    <p className="text-body-small font-bold uppercase tracking-tight leading-relaxed opacity-80">
                        El motor de biomecánica está procesando tus selecciones. Las alertas de sobreentrenamiento aparecerán una vez el volumen exceda el MRV teórico.
                    </p>
                </div>
            </div>
        </div>
    );
};

export default VolumeSection;
