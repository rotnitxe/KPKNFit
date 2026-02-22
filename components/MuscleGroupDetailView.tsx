// components/MuscleGroupDetailView.tsx
import React, { useMemo, useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { enrichWithWikipedia } from '../services/wikipediaEnrichment';
import { ExerciseMuscleInfo, MuscleHierarchy, MuscleSubGroup } from '../types';
import { SparklesIcon, ChevronRightIcon, DumbbellIcon, PencilIcon, StarIcon } from './icons';
import Button from './ui/Button';
import MuscleGroupEditorModal from './MuscleGroupEditorModal';
import { MuscleTrainingAnalysis } from './MuscleTrainingAnalysis';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo, isFavorite?: boolean }> = React.memo(({ exercise, isFavorite }) => {
    const { navigateTo } = useAppDispatch();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-3 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors"
        >
            <div className="flex items-center gap-2">
                {isFavorite && <StarIcon size={16} className="text-yellow-400" />}
                <div>
                    <h3 className="font-semibold text-white text-md">{exercise.name}</h3>
                    <p className="text-xs text-slate-400">{exercise.type} • {exercise.equipment}</p>
                </div>
            </div>
            <ChevronRightIcon className="text-slate-500" />
        </div>
    );
});

interface MuscleGroupDetailViewProps {
  muscleGroupId: string;
  isOnline: boolean;
}

const MuscleGroupDetailView: React.FC<MuscleGroupDetailViewProps> = ({ muscleGroupId, isOnline }) => {
    const { muscleGroupData, settings, exerciseList, muscleHierarchy, history } = useAppState();
    const { setCurrentBackgroundOverride, updateMuscleGroupInfo, navigateTo } = useAppDispatch();
    const [isEditorOpen, setIsEditorOpen] = useState(false);
    const [wikiExtract, setWikiExtract] = useState<string | null>(null);

    const muscleInfo = useMemo(() => {
        return muscleGroupData.find(m => m.id === muscleGroupId);
    }, [muscleGroupId, muscleGroupData]);

    useEffect(() => {
        if (muscleInfo?.coverImage) {
            setCurrentBackgroundOverride({
                type: 'image',
                value: muscleInfo.coverImage,
                style: { blur: 16, brightness: 0.4 }
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
        
        // Prioritize exercises that specifically target the sub-muscle if it's not a parent
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

    if (!muscleInfo) {
        return <div className="pt-[65px] text-center"><h2 className="text-2xl font-bold text-red-400">Error</h2><p className="text-slate-300 mt-2">No se encontró información para el grupo muscular con ID: "{muscleGroupId}". Asegúrese de que existe en la base de datos.</p></div>;
    }

    return (
        <div className="pb-20 animate-fade-in">
            {isEditorOpen && (
                <MuscleGroupEditorModal
                    isOpen={isEditorOpen}
                    onClose={() => setIsEditorOpen(false)}
                    muscleGroup={muscleInfo}
                />
            )}
            <header className="relative h-48 -mx-4">
                {muscleInfo.coverImage && <img src={muscleInfo.coverImage} alt={muscleInfo.name} className="w-full h-full object-cover" />}
                <div className="absolute inset-0 bg-gradient-to-t from-black via-black/70 to-transparent" />
                 <div className="absolute top-2 right-2 flex gap-2">
                    <Button onClick={() => setIsEditorOpen(true)} variant="secondary" className="!text-xs !py-1"><PencilIcon size={14}/> Editar Página</Button>
                </div>
                <div className="absolute bottom-4 left-4 right-4">
                    <h1 className="text-4xl font-bold text-white">{muscleInfo.name}</h1>
                </div>
            </header>

             <div className="space-y-6 mt-6">
                 <MuscleTrainingAnalysis muscleName={muscleInfo.name} history={history} isOnline={isOnline} settings={settings} />
                 <div className="glass-card-nested p-4">
                    <h3 className="font-bold text-lg text-white mb-2">Información General</h3>
                    <p className="whitespace-pre-wrap text-slate-300 text-sm">{muscleInfo.description}</p>
                    {!wikiExtract && (
                        <button
                            onClick={async () => {
                                const r = await enrichWithWikipedia(muscleInfo.name);
                                if (r?.extract) setWikiExtract(r.extract);
                            }}
                            className="mt-3 text-xs text-cyan-400 hover:text-cyan-300 font-medium"
                        >
                            + Más información (Wikipedia)
                        </button>
                    )}
                    {wikiExtract && (
                        <div className="mt-3 p-3 bg-slate-900/50 rounded-lg border border-cyan-500/20">
                            <p className="text-xs text-slate-400 italic">{wikiExtract}</p>
                        </div>
                    )}
                </div>

                {/* Porciones / Cabezas: solo en músculos que tienen subdivisiones */}
                {childMuscleInfos.length > 0 && (
                    <details className="glass-card-nested !p-0" open>
                        <summary className="p-4 cursor-pointer flex justify-between items-center list-none">
                            <h3 className="font-bold text-white">Porciones / Cabezas</h3>
                            <ChevronRightIcon className="details-arrow" />
                        </summary>
                        <div className="p-4 border-t border-slate-700/50 space-y-4">
                            {childMuscleInfos.map(portion => (
                                <div key={portion.id} className="bg-slate-900/50 rounded-xl p-4 border border-white/5">
                                    <h4 className="font-semibold text-white mb-2">{portion.name}</h4>
                                    <p className="text-sm text-slate-400 mb-2">{portion.description}</p>
                                    {(portion.origin || portion.insertion) && (
                                        <div className="text-xs text-slate-500 space-y-1 mt-2">
                                            {portion.origin && <p><span className="font-mono">Origen:</span> {portion.origin}</p>}
                                            {portion.insertion && <p><span className="font-mono">Inserción:</span> {portion.insertion}</p>}
                                        </div>
                                    )}
                                    {portion.mechanicalFunctions && portion.mechanicalFunctions.length > 0 && (
                                        <div className="flex flex-wrap gap-1 mt-2">
                                            {portion.mechanicalFunctions.map((f, i) => (
                                                <span key={i} className="px-2 py-0.5 bg-slate-800 rounded text-xs text-slate-400">{f}</span>
                                            ))}
                                        </div>
                                    )}
                                    {portion.volumeRecommendations && (
                                        <p className="text-xs text-slate-500 mt-2">
                                            MEV: {portion.volumeRecommendations.mev} · MAV: {portion.volumeRecommendations.mav} · MRV: {portion.volumeRecommendations.mrv}
                                        </p>
                                    )}
                                </div>
                            ))}
                        </div>
                    </details>
                )}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="glass-card-nested p-4">
                        <h4 className="font-bold text-md text-white mb-2">Importancia en Movimiento</h4>
                        <p className="text-sm text-slate-400">{muscleInfo.importance.movement}</p>
                    </div>
                     <div className="glass-card-nested p-4">
                        <h4 className="font-bold text-md text-white mb-2">Importancia en Salud</h4>
                        <p className="text-sm text-slate-400">{muscleInfo.importance.health}</p>
                    </div>
                </div>

                {/* Wiki/Lab: Origen e inserción */}
                {(muscleInfo.origin || muscleInfo.insertion) && (
                    <details className="glass-card-nested !p-0">
                        <summary className="p-4 cursor-pointer flex justify-between items-center list-none">
                            <h3 className="font-bold text-white">Origen e Inserción</h3>
                            <ChevronRightIcon className="details-arrow" />
                        </summary>
                        <div className="p-4 border-t border-slate-700/50 space-y-2">
                            {muscleInfo.origin && <p className="text-sm"><span className="text-slate-500 font-mono text-xs">Origen:</span> <span className="text-slate-300">{muscleInfo.origin}</span></p>}
                            {muscleInfo.insertion && <p className="text-sm"><span className="text-slate-500 font-mono text-xs">Inserción:</span> <span className="text-slate-300">{muscleInfo.insertion}</span></p>}
                        </div>
                    </details>
                )}

                {/* Wiki/Lab: Funciones mecánicas */}
                {muscleInfo.mechanicalFunctions && muscleInfo.mechanicalFunctions.length > 0 && (
                    <div className="glass-card-nested p-4">
                        <h3 className="font-bold text-white mb-2">Funciones Mecánicas</h3>
                        <div className="flex flex-wrap gap-2">
                            {muscleInfo.mechanicalFunctions.map((f, i) => (
                                <span key={i} className="px-2 py-1 bg-slate-800 rounded-lg text-sm text-slate-300">{f}</span>
                            ))}
                        </div>
                    </div>
                )}

                {/* Wiki/Lab: Articulaciones y tendones relacionados */}
                {((muscleInfo.relatedJoints?.length || 0) > 0 || (muscleInfo.relatedTendons?.length || 0) > 0) && (
                    <div className="glass-card-nested p-4">
                        <h3 className="font-bold text-white mb-2">Relaciones Anatómicas</h3>
                        <div className="flex flex-wrap gap-2">
                            {muscleInfo.relatedJoints?.map(jId => (
                                <button key={jId} onClick={() => navigateTo('joint-detail', { jointId: jId })} className="px-2 py-1 bg-cyan-900/40 border border-cyan-500/30 rounded-lg text-sm text-cyan-300 hover:bg-cyan-800/50 transition-colors">
                                    Articulación: {jId.replace(/-/g, ' ')}
                                </button>
                            ))}
                            {muscleInfo.relatedTendons?.map(tId => (
                                <button key={tId} onClick={() => navigateTo('tendon-detail', { tendonId: tId })} className="px-2 py-1 bg-amber-900/40 border border-amber-500/30 rounded-lg text-sm text-amber-300 hover:bg-amber-800/50 transition-colors">
                                    Tendón: {tId.replace(/tendon-|-/g, ' ')}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {/* Wiki/Lab: Lesiones comunes */}
                {muscleInfo.commonInjuries && muscleInfo.commonInjuries.length > 0 && (
                    <details className="glass-card-nested !p-0">
                        <summary className="p-4 cursor-pointer flex justify-between items-center list-none">
                            <h3 className="font-bold text-red-400/90">Lesiones Comunes</h3>
                            <ChevronRightIcon className="details-arrow" />
                        </summary>
                        <div className="p-4 border-t border-slate-700/50 space-y-3">
                            {muscleInfo.commonInjuries.map((inj, i) => (
                                <div key={i} className="bg-slate-900/50 rounded-lg p-3">
                                    <h4 className="font-semibold text-white text-sm">{inj.name}</h4>
                                    <p className="text-slate-400 text-xs mt-1">{inj.description}</p>
                                    {inj.returnProgressions && inj.returnProgressions.length > 0 && (
                                        <p className="text-slate-500 text-xs mt-2">Progresión: {inj.returnProgressions.join(' → ')}</p>
                                    )}
                                </div>
                            ))}
                        </div>
                    </details>
                )}

                 <div className="glass-card-nested p-4">
                    <h3 className="font-bold text-lg text-white mb-3">Volumen Semanal Recomendado (series)</h3>
                    <div className="grid grid-cols-3 gap-2 text-center">
                        <div><p className="text-2xl font-bold text-green-400">{muscleInfo.volumeRecommendations.mev}</p><p className="text-xs text-slate-400">MEV (Mínimo)</p></div>
                        <div><p className="text-2xl font-bold text-yellow-400">{muscleInfo.volumeRecommendations.mav}</p><p className="text-xs text-slate-400">MAV (Adaptativo)</p></div>
                        <div><p className="text-2xl font-bold text-red-400">{muscleInfo.volumeRecommendations.mrv}</p><p className="text-xs text-slate-400">MRV (Máximo)</p></div>
                    </div>
                </div>
                
                 {recommendedExercises.length > 0 && (
                    <div className="glass-card-nested p-4">
                         <h3 className="font-bold text-lg text-white mb-3 flex items-center gap-2"><SparklesIcon/> Ejercicios Destacados para {muscleInfo.name}</h3>
                        {recommendedExercises.map(ex => <ExerciseItem key={ex.id} exercise={ex} isFavorite={ex.id === muscleInfo.favoriteExerciseId}/>)}
                    </div>
                 )}
                 
                 {allExercises.length > 0 && (
                    <div className="glass-card-nested p-4">
                         <h3 className="font-bold text-lg text-white mb-3 flex items-center gap-2"><DumbbellIcon/> Todos los Ejercicios</h3>
                        {allExercises.map(ex => <ExerciseItem key={ex.id} exercise={ex} />)}
                    </div>
                 )}
            </div>
        </div>
    );
};

export default MuscleGroupDetailView;
