// components/MuscleGroupDetailView.tsx
import React, { useMemo, useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { enrichWithWikipedia } from '../services/wikipediaEnrichment';
import { ExerciseMuscleInfo, MuscleHierarchy, MuscleSubGroup } from '../types';
import { SparklesIcon, ChevronRightIcon, DumbbellIcon, PencilIcon, StarIcon } from './icons';
import MuscleGroupEditorModal from './MuscleGroupEditorModal';
import { MuscleTrainingAnalysis } from './MuscleTrainingAnalysis';
import { motion } from 'framer-motion';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo, isFavorite?: boolean }> = React.memo(({ exercise, isFavorite }) => {
    const { navigateTo } = useAppDispatch();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-4 flex justify-between items-center cursor-pointer rounded-[20px] bg-white/[0.02] border border-white/5 hover:bg-white/[0.08] hover:border-white/10 transition-all group"
        >
            <div className="flex items-center gap-3">
                {isFavorite && <div className="w-6 h-6 rounded-full bg-yellow-500/10 flex items-center justify-center"><StarIcon size={12} className="text-yellow-400" /></div>}
                <div>
                    <h3 className="font-bold text-white/90 text-sm group-hover:text-purple-400 transition-colors">{exercise.name}</h3>
                    <p className="text-[10px] font-black uppercase tracking-widest text-white/30 mt-1">{exercise.type} • {exercise.equipment}</p>
                </div>
            </div>
            <div className="w-8 h-8 rounded-full flex items-center justify-center text-white/20 group-hover:text-purple-400 group-hover:translate-x-1 transition-all">
                <ChevronRightIcon size={18} />
            </div>
        </div>
    );
});

interface MuscleGroupDetailViewProps {
  muscleGroupId: string;
  isOnline: boolean;
}

const MuscleGroupDetailView: React.FC<MuscleGroupDetailViewProps> = ({ muscleGroupId, isOnline }) => {
    const { muscleGroupData, settings, exerciseList, muscleHierarchy, history, jointDatabase, tendonDatabase } = useAppState();
    const { setCurrentBackgroundOverride, navigateTo } = useAppDispatch();
    const [isEditorOpen, setIsEditorOpen] = useState(false);
    const [wikiExtract, setWikiExtract] = useState<string | null>(null);
    const [wikiLoading, setWikiLoading] = useState(false);
    const [wikiError, setWikiError] = useState(false);

    const muscleInfo = useMemo(() => {
        return muscleGroupData.find(m => m.id === muscleGroupId);
    }, [muscleGroupId, muscleGroupData]);

    useEffect(() => {
        if (muscleInfo?.coverImage) {
            setCurrentBackgroundOverride({
                type: 'image',
                value: muscleInfo.coverImage,
                style: { blur: 24, brightness: 0.3 }
            });
        } else {
             setCurrentBackgroundOverride(undefined);
        }
        return () => setCurrentBackgroundOverride(undefined);
    }, [muscleInfo, setCurrentBackgroundOverride]);
    
    const recommendedExercises = useMemo(() => {
        if (!muscleInfo || !muscleInfo.recommendedExercises) return [];
        return muscleInfo.recommendedExercises
            .map(id => exerciseList.find(ex => ex.id === id))
            .filter((ex): ex is ExerciseMuscleInfo => !!ex);
    }, [muscleInfo, exerciseList]);

    const childMuscleNames = useMemo(() => {
        if (!muscleInfo) return [];
        let children: string[] = [];
        Object.values(muscleHierarchy.bodyPartHierarchy).forEach((subgroups: MuscleSubGroup[]) => {
            subgroups.forEach(subgroup => {
                if (typeof subgroup === 'object' && subgroup !== null) {
                    const parentName = Object.keys(subgroup)[0];
                    if (parentName === muscleInfo.name) {
                        children = subgroup[parentName];
                    }
                }
            });
        });
        return children;
    }, [muscleInfo, muscleHierarchy]);

    const childMuscleInfos = useMemo(() => {
        return childMuscleNames
            .map(name => muscleGroupData.find(m => m.name === name))
            .filter((m): m is NonNullable<typeof m> => !!m);
    }, [childMuscleNames, muscleGroupData]);

    const allExercises = useMemo(() => {
        if (!muscleInfo) return [];

        const relevantMuscleNames = [muscleInfo.name, ...childMuscleNames];
        
        const filteredExercises = exerciseList.filter(ex => {
            if (relevantMuscleNames.includes(ex.subMuscleGroup || '')) {
                return true;
            }
            return ex.involvedMuscles.some(m => relevantMuscleNames.includes(m.muscle) && m.role === 'primary');
        });
        
        if (childMuscleNames.length === 0) {
            filteredExercises.sort((a, b) => {
                const aIsSpecific = a.subMuscleGroup === muscleInfo.name;
                const bIsSpecific = b.subMuscleGroup === muscleInfo.name;
                if (aIsSpecific && !bIsSpecific) return -1;
                if (!aIsSpecific && bIsSpecific) return 1;
                return 0;
            });
        }

        return filteredExercises;
    }, [exerciseList, muscleInfo, muscleHierarchy, childMuscleNames]);

    const exercisesByHead = useMemo(() => {
        if (muscleInfo?.name !== 'Deltoides' || childMuscleNames.length === 0) return null;
        const groups: Record<string, ExerciseMuscleInfo[]> = {};
        childMuscleNames.forEach(head => { groups[head] = []; });
        allExercises.forEach(ex => {
            const head = ex.subMuscleGroup || ex.involvedMuscles?.find(m => childMuscleNames.includes(m.muscle) && m.role === 'primary')?.muscle;
            if (head && groups[head]) groups[head].push(ex);
            else if (groups[childMuscleNames[0]]) groups[childMuscleNames[0]].push(ex);
        });
        return groups;
    }, [muscleInfo, childMuscleNames, allExercises]);

    if (!muscleInfo) {
        return <div className="pt-[65px] text-center"><h2 className="text-2xl font-bold text-red-400">Error</h2><p className="text-slate-300 mt-2">No se encontró información para el grupo muscular con ID: "{muscleGroupId}".</p></div>;
    }

    return (
        <div className="min-h-screen flex flex-col bg-transparent overflow-x-hidden relative pb-32">
            {isEditorOpen && (
                <MuscleGroupEditorModal
                    isOpen={isEditorOpen}
                    onClose={() => setIsEditorOpen(false)}
                    muscleGroup={muscleInfo}
                />
            )}
            
            <header className="relative pt-12 pb-8 px-6 flex justify-between items-end">
                <div className="relative z-10">
                    <span className="text-[10px] font-black uppercase tracking-widest text-purple-400 mb-2 block">Músculo</span>
                    <h1 className="text-5xl font-black text-white tracking-tighter leading-none">{muscleInfo.name}</h1>
                </div>
                 <button onClick={() => setIsEditorOpen(true)} className="relative z-20 px-4 py-2 rounded-full bg-white/10 backdrop-blur-md border border-white/10 text-[10px] font-black uppercase tracking-widest text-white hover:bg-white/20 transition-colors flex items-center gap-2">
                     <PencilIcon size={12}/> Info
                 </button>
            </header>

             <div className="relative z-10 px-6 space-y-6">
                 <div className="bg-white/5 backdrop-blur-2xl border border-white/10 rounded-[32px] overflow-hidden">
                     <MuscleTrainingAnalysis muscleName={muscleInfo.name} history={history} isOnline={isOnline} settings={settings} />
                 </div>

                 <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10 shadow-2xl">
                    <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Información General</h3>
                    <p className="whitespace-pre-wrap text-sm text-white/70 leading-relaxed">{muscleInfo.description}</p>
                    {!wikiExtract && (
                        <button
                            onClick={async () => {
                                setWikiLoading(true);
                                setWikiError(false);
                                try {
                                    const r = await enrichWithWikipedia(muscleInfo.name);
                                    if (r?.extract) setWikiExtract(r.extract);
                                    else setWikiError(true);
                                } catch {
                                    setWikiError(true);
                                } finally {
                                    setWikiLoading(false);
                                }
                            }}
                            disabled={wikiLoading}
                            className="mt-4 px-4 py-2 rounded-xl bg-purple-500/10 border border-purple-500/20 text-xs font-black uppercase tracking-widest text-purple-400 hover:bg-purple-500/20 transition-colors disabled:opacity-50"
                        >
                            {wikiLoading ? 'Cargando...' : wikiError ? 'Reintentar Wikipedia' : 'Wikipedia'}
                        </button>
                    )}
                    {wikiExtract && (
                        <div className="mt-4 p-4 bg-black/20 rounded-[20px] border border-white/5">
                            <p className="text-xs text-white/60 italic leading-relaxed">{wikiExtract}</p>
                        </div>
                    )}
                </motion.div>

                <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                    <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Importancia Estética</h3>
                    <p className="text-sm text-white/70 leading-relaxed">{muscleInfo.aestheticImportance || muscleInfo.aestheticRole || muscleInfo.importance?.health || `El ${muscleInfo.name} contribuye a la forma y proporción del cuerpo. Su desarrollo equilibrado mejora la simetría y la apariencia general.`}</p>
                </motion.div>

                {childMuscleInfos.length > 0 && (
                    <motion.details initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }} className="rounded-[32px] bg-white/5 border border-white/10 overflow-hidden group" open>
                        <summary className="p-6 cursor-pointer flex justify-between items-center list-none outline-none">
                            <h3 className="text-[10px] font-black uppercase tracking-widest text-purple-400">Porciones / Cabezas</h3>
                            <ChevronRightIcon className="text-white/40 group-open:rotate-90 transition-transform" />
                        </summary>
                        <div className="px-4 pb-4 space-y-3">
                            {childMuscleInfos.map(portion => (
                                <div key={portion.id} className="bg-black/20 rounded-[24px] p-5 border border-white/5">
                                    <h4 className="font-bold text-white/90 mb-2">{portion.name}</h4>
                                    <p className="text-sm text-white/60 leading-relaxed mb-3">{portion.description}</p>
                                    {(portion.origin || portion.insertion) && (
                                        <div className="text-xs text-white/50 space-y-1 mb-3">
                                            {portion.origin && <p><strong className="text-white/70 font-black uppercase tracking-widest text-[9px]">Origen:</strong> {portion.origin}</p>}
                                            {portion.insertion && <p><strong className="text-white/70 font-black uppercase tracking-widest text-[9px]">Inserción:</strong> {portion.insertion}</p>}
                                        </div>
                                    )}
                                    {portion.mechanicalFunctions && portion.mechanicalFunctions.length > 0 && (
                                        <div className="flex flex-wrap gap-2 mb-3">
                                            {portion.mechanicalFunctions.map((f, i) => (
                                                <span key={i} className="px-2 py-1 bg-white/5 rounded-lg text-[10px] font-black uppercase tracking-widest text-white/40">{f}</span>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </motion.details>
                )}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                        <h4 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Rol Biomecánico</h4>
                        <p className="text-sm text-white/70 leading-relaxed">{muscleInfo.importance.movement}</p>
                    </motion.div>
                     <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                        <h4 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Rol en la Salud</h4>
                        <p className="text-sm text-white/70 leading-relaxed">{muscleInfo.importance.health}</p>
                    </motion.div>
                </div>

                {(muscleInfo.relatedJoints?.length || 0) > 0 && (
                    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                        <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Articulaciones Relacionadas</h3>
                        <div className="flex flex-wrap gap-2">
                            {muscleInfo.relatedJoints?.map(jId => {
                                const joint = jointDatabase?.find(j => j.id === jId);
                                const label = joint?.name?.split('(')[0]?.trim() || jId.replace(/-/g, ' ');
                                return (
                                    <button key={jId} onClick={() => navigateTo('joint-detail', { jointId: jId })} className="px-4 py-2 bg-purple-500/10 border border-purple-500/20 rounded-xl text-xs font-black tracking-widest uppercase text-purple-400 hover:bg-purple-500/20 transition-colors">
                                        {label}
                                    </button>
                                );
                            })}
                        </div>
                    </motion.div>
                )}

                {(muscleInfo.relatedTendons?.length || 0) > 0 && (
                    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.6 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                        <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-3">Tendones Relacionados</h3>
                        <div className="flex flex-wrap gap-2">
                            {muscleInfo.relatedTendons?.map(tId => {
                                const tendon = tendonDatabase?.find(t => t.id === tId);
                                const label = tendon?.name || tId.replace(/tendon-|-/g, ' ');
                                return (
                                    <button key={tId} onClick={() => navigateTo('tendon-detail', { tendonId: tId })} className="px-4 py-2 bg-purple-500/10 border border-purple-500/20 rounded-xl text-xs font-black tracking-widest uppercase text-purple-400 hover:bg-purple-500/20 transition-colors">
                                        {label}
                                    </button>
                                );
                            })}
                        </div>
                    </motion.div>
                )}

                 <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.7 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                    <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-4">Volumen Semanal Recomendado</h3>
                    <div className="grid grid-cols-3 gap-3">
                        <div className="bg-black/20 rounded-[20px] p-4 text-center border border-white/5">
                            <p className="text-2xl font-black text-white/90">{muscleInfo.volumeRecommendations.mev}</p>
                            <p className="text-[9px] text-white/40 uppercase tracking-widest mt-1">Mínimo</p>
                        </div>
                        <div className="bg-purple-500/10 rounded-[20px] p-4 text-center border border-purple-500/20 shadow-lg shadow-purple-500/10">
                            <p className="text-2xl font-black text-purple-400">{muscleInfo.volumeRecommendations.mav}</p>
                            <p className="text-[9px] text-purple-400/50 uppercase tracking-widest mt-1">Óptimo</p>
                        </div>
                        <div className="bg-black/20 rounded-[20px] p-4 text-center border border-white/5">
                            <p className="text-2xl font-black text-white/90">{muscleInfo.volumeRecommendations.mrv}</p>
                            <p className="text-[9px] text-white/40 uppercase tracking-widest mt-1">Máximo</p>
                        </div>
                    </div>
                </motion.div>
                
                 {recommendedExercises.length > 0 && (
                    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.8 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                         <h3 className="text-[10px] font-black uppercase tracking-widest text-yellow-400 mb-4 flex items-center gap-2">
                            <SparklesIcon size={14}/> Ejercicios Destacados
                         </h3>
                        <div className="space-y-2">
                            {recommendedExercises.map(ex => <ExerciseItem key={ex.id} exercise={ex} isFavorite={ex.id === muscleInfo.favoriteExerciseId}/>)}
                        </div>
                    </motion.div>
                 )}
                 
                 {allExercises.length > 0 && (
                    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.9 }} className="p-6 rounded-[32px] bg-white/5 border border-white/10">
                         <h3 className="text-[10px] font-black uppercase tracking-widest text-white/40 mb-4 flex items-center gap-2">
                             <DumbbellIcon size={14}/> Todos los ejercicios
                         </h3>
                        <div className="space-y-4">
                            {exercisesByHead ? (
                                childMuscleNames.map(head => {
                                    const exs = exercisesByHead[head] || [];
                                    if (exs.length === 0) return null;
                                    return (
                                        <div key={head} className="space-y-2">
                                            <h4 className="text-[10px] font-black text-purple-400 uppercase tracking-widest ml-2 mb-2">{head}</h4>
                                            <div className="space-y-2">
                                                {exs.map(ex => <ExerciseItem key={ex.id} exercise={ex} />)}
                                            </div>
                                        </div>
                                    );
                                })
                            ) : (
                                <div className="space-y-2">
                                    {allExercises.map(ex => <ExerciseItem key={ex.id} exercise={ex} />)}
                                </div>
                            )}
                        </div>
                    </motion.div>
                 )}
            </div>
        </div>
    );
};

export default MuscleGroupDetailView;
