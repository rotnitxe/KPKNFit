// components/ExerciseDetailView.tsx
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo, MuscleHierarchy } from '../types';
import { getAICoachAnalysis, getCommunityOpinionForExercise, generateImage, searchGoogleImages, searchWebForExerciseVideos } from '../services/aiService';
import { StarIcon, SparklesIcon, BrainIcon, UploadIcon, VideoIcon, ImageIcon, PlusIcon, TrendingUpIcon, CheckCircleIcon, XCircleIcon, ChevronRightIcon, DumbbellIcon, ActivityIcon, UsersIcon, AlertTriangleIcon, ArrowUpIcon, ArrowDownIcon, PencilIcon } from './icons';
import { ExerciseLink } from './ExerciseLink';
import { InfoTooltip } from './ui/InfoTooltip';
import ImageSearchModal from './ImageSearchModal';
import SkeletonLoader from './ui/SkeletonLoader';

// --- Sub-components ---

const StarRating: React.FC<{ rating: number; onSetRating?: (rating: number) => void; colorClass?: string }> = ({ rating, onSetRating, colorClass = "text-yellow-400" }) => (
    <div className="flex">
        {[1, 2, 3, 4, 5].map(star => (
            <button key={star} type="button" onClick={onSetRating ? () => onSetRating(star) : undefined} disabled={!onSetRating} aria-label={`Calificar con ${star} estrellas`}>
                <StarIcon size={24} filled={star <= rating} className={`${colorClass} ${onSetRating ? 'cursor-pointer' : ''} ${star <= rating ? 'star-twinkle-animation' : ''}`} />
            </button>
        ))}
    </div>
);

const TagWithTooltip: React.FC<{ label: string }> = ({ label }) => (
    <div className="flex items-center px-2 py-1 bg-[#0a0a0a] border border-cyber-cyan/20 text-slate-300 rounded-lg">
        <span className="text-[10px] font-mono font-semibold">{label}</span>
        <InfoTooltip term={label} />
    </div>
);

const IndicatorBar: React.FC<{ label: string; value: number; max?: number; details?: string }> = ({ label, value, max = 10, details }) => {
    const percentage = (value / max) * 100;
    const hue = (value/max * 120); // 0=red(0), 10=green(120)
    const reversedHue = 120 - (value / max * 120); // 10=red(0), 1=green(108)

    return (
        <div>
            <div className="flex justify-between items-baseline mb-1">
                <span className="text-sm font-semibold text-slate-200">{label}</span>
                <span className="text-sm font-bold text-white">{value}/{max}</span>
            </div>
            <div className="w-full bg-slate-700 rounded-full h-2.5">
                <div className="h-2.5 rounded-full transition-all" style={{ width: `${percentage}%`, backgroundColor: `hsl(${label.includes('Riesgo') ? reversedHue : hue}, 70%, 45%)` }}></div>
            </div>
            {details && <p className="text-xs text-slate-400 mt-1">{details}</p>}
        </div>
    );
};

export const KinesiologyAnalysis: React.FC<{ exercise: ExerciseMuscleInfo }> = ({ exercise }) => {
    const { resistanceProfile, commonMistakes, anatomicalConsiderations, progressions, regressions, periodizationNotes } = exercise;
    const hasAnyData = resistanceProfile || commonMistakes?.length || anatomicalConsiderations?.length || progressions?.length || regressions?.length || periodizationNotes?.length;

    if (!hasAnyData) return null;

    return (
        <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
             <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3 flex items-center gap-2"><BrainIcon size={14} /> Análisis Kinesiológico</h3>
             <div className="space-y-4">
                {resistanceProfile && (
                    <div className="bg-[#0d0d0d] p-3 rounded-lg border border-white/5">
                        <h4 className="font-semibold text-primary-color">Perfil de Resistencia</h4>
                        <p className="text-sm text-slate-300">Tensión Máxima: <span className="font-bold capitalize">{resistanceProfile.peakTensionPoint}</span></p>
                    </div>
                )}
                {commonMistakes && commonMistakes.length > 0 && (
                     <details className="rounded-xl border border-white/5 overflow-hidden">
                        <summary className="p-3 cursor-pointer flex justify-between items-center list-none bg-[#0d0d0d]"><h4 className="text-[10px] font-mono font-bold text-white">Faltas Técnicas Comunes</h4><ChevronRightIcon className="details-arrow"/></summary>
                        <ul className="p-3 border-t border-white/5 list-disc list-inside space-y-2 text-sm text-slate-300">
                            {commonMistakes.map((item, i) => <li key={i}><strong className="text-slate-300">{item.mistake}:</strong> <span className="text-slate-400">{item.correction}</span></li>)}
                        </ul>
                    </details>
                )}
                 {anatomicalConsiderations && anatomicalConsiderations.length > 0 && (
                     <details className="rounded-xl border border-white/5 overflow-hidden">
                        <summary className="p-3 cursor-pointer flex justify-between items-center list-none bg-[#0d0d0d]"><h4 className="text-[10px] font-mono font-bold text-white">Consideraciones Anatómicas</h4><ChevronRightIcon className="details-arrow"/></summary>
                        <ul className="p-3 border-t border-white/5 list-disc list-inside space-y-2 text-sm text-slate-300">
                            {anatomicalConsiderations.map((item, i) => <li key={i}><strong className="text-slate-300">{item.trait}:</strong> <span className="text-slate-400">{item.advice}</span></li>)}
                        </ul>
                    </details>
                )}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {progressions && progressions.length > 0 && (
                        <details className="rounded-xl border border-white/5 overflow-hidden">
                            <summary className="p-3 cursor-pointer flex justify-between items-center list-none bg-[#0d0d0d]"><h4 className="text-[10px] font-mono font-bold text-green-400 flex items-center gap-2"><ArrowUpIcon size={12}/> Progresiones</h4><ChevronRightIcon className="details-arrow"/></summary>
                            <ul className="p-3 border-t border-white/5 list-disc list-inside space-y-2 text-sm text-slate-300">
                                {progressions.map((item, i) => <li key={i}><strong className="text-slate-300">{item.name}:</strong> <span className="text-slate-400">{item.description}</span></li>)}
                            </ul>
                        </details>
                    )}
                     {regressions && regressions.length > 0 && (
                        <details className="rounded-xl border border-white/5 overflow-hidden">
                            <summary className="p-3 cursor-pointer flex justify-between items-center list-none bg-[#0d0d0d]"><h4 className="text-[10px] font-mono font-bold text-yellow-400 flex items-center gap-2"><ArrowDownIcon size={12}/> Regresiones</h4><ChevronRightIcon className="details-arrow"/></summary>
                            <ul className="p-3 border-t border-white/5 list-disc list-inside space-y-2 text-sm text-slate-300">
                                {regressions.map((item, i) => <li key={i}><strong className="text-slate-300">{item.name}:</strong> <span className="text-slate-400">{item.description}</span></li>)}
                            </ul>
                        </details>
                    )}
                </div>
                 {periodizationNotes && periodizationNotes.length > 0 && (
                     <details className="rounded-xl border border-white/5 overflow-hidden">
                        <summary className="p-3 cursor-pointer flex justify-between items-center list-none bg-[#0d0d0d]"><h4 className="text-[10px] font-mono font-bold text-white">Contexto de Programa</h4><ChevronRightIcon className="details-arrow"/></summary>
                        <div className="p-3 border-t border-white/5 space-y-3 text-sm text-slate-300">
                            {periodizationNotes.map((item, i) => (
                                <div key={i}>
                                    <div className="flex justify-between items-center">
                                        <h5 className="font-bold text-primary-color capitalize">{item.phase}</h5>
                                        <StarRating rating={item.suitability} colorClass="text-primary-color"/>
                                    </div>
                                    <p className="text-xs text-slate-400 italic">"{item.notes}"</p>
                                </div>
                            ))}
                        </div>
                    </details>
                )}
             </div>
        </div>
    )
};


const ImageVideoGallery: React.FC<{
    images: string[];
    videos: string[];
    exerciseName: string;
    isOnline: boolean;
    onUpdate: (images: string[], videos: string[]) => void;
}> = ({ images, videos, exerciseName, isOnline, onUpdate }) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [isLoading, setIsLoading] = useState<'ai' | 'webImage' | 'webVideo' | null>(null);
    const [isImageSearchOpen, setIsImageSearchOpen] = useState(false);
    const { settings } = useAppState();

    const handleGenerateImage = async () => {
        setIsLoading('ai');
        const fullPrompt = `cinematic fitness wallpaper, ${exerciseName}, dramatic lighting, high quality photo`;
        const result = await generateImage(fullPrompt, '16:9', settings);
        onUpdate([...images, result], videos);
        setIsLoading(null);
    };
    
    const handleSelectImageFromSearch = (imageUrl: string) => {
        onUpdate([...images, imageUrl], videos);
    };
    
    const handleSearchVideos = async () => {
        setIsLoading('webVideo');
        const webVideos = await searchWebForExerciseVideos(exerciseName, settings);
        onUpdate(images, [...videos, ...webVideos.videoUrls]);
        setIsLoading(null);
    };

    const handleUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            const reader = new FileReader();
            reader.onload = (event) => {
                onUpdate([...images, event.target!.result as string], videos);
            };
            reader.readAsDataURL(e.target.files[0]);
        }
    };
    
    const addVideoUrl = () => {
        const url = prompt("Pega la URL del video (YouTube, Instagram, etc.):");
        if (url) {
            onUpdate(images, [...videos, url]);
        }
    };

    const hasMedia = images.length > 0 || videos.length > 0;

    return (
        <>
            <ImageSearchModal
                isOpen={isImageSearchOpen}
                onClose={() => setIsImageSearchOpen(false)}
                onSelectImage={handleSelectImageFromSearch}
                initialQuery={exerciseName}
            />
            <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3">Galería Multimedia</h3>
                <div className="relative">
                    <div className="flex overflow-x-auto snap-x snap-mandatory space-x-3 pb-3 hide-scrollbar">
                        {images.map((img, i) => (
                            <div key={`img-${i}`} className="snap-center flex-shrink-0 w-4/5 sm:w-3/5">
                                 <img src={img} alt={`${exerciseName} ${i+1}`} className="aspect-video w-full object-cover rounded-lg shadow-md" />
                            </div>
                        ))}
                        {videos.map((vid, i) => (
                             <div key={`vid-${i}`} className="snap-center flex-shrink-0 w-4/5 sm:w-3/5">
                                <button type="button" onClick={() => import('../utils/inAppBrowser').then(m => m.openInAppBrowser(vid))} className="aspect-video bg-black rounded-lg flex items-center justify-center text-white w-full h-full shadow-md hover:opacity-90">
                                    <VideoIcon size={32} />
                                </button>
                            </div>
                        ))}
                        {!hasMedia && (
                            <div className="flex-shrink-0 w-full text-center text-slate-500 text-sm font-mono py-8 bg-[#0d0d0d] rounded-lg border border-white/5">
                                No hay imágenes ni videos. <br/> ¡Añade algunos!
                            </div>
                        )}
                    </div>
                     {hasMedia && (
                        <div className="absolute top-0 right-0 bottom-3 w-8 bg-gradient-to-l from-black/50 to-transparent pointer-events-none flex items-center justify-center">
                            <ChevronRightIcon className="text-white/50 animate-pulse"/>
                        </div>
                    )}
                </div>
                <div className="grid grid-cols-2 gap-2 text-[10px] font-mono mt-4">
                    <button onClick={() => fileInputRef.current?.click()} className="py-2 px-3 rounded-lg border border-cyber-cyan/20 bg-[#0d0d0d] text-slate-300 hover:border-cyber-cyan/40 transition-colors"><UploadIcon size={12} className="inline mr-1"/> Subir</button>
                    <input type="file" ref={fileInputRef} onChange={handleUpload} accept="image/*" className="hidden"/>
                    <button onClick={handleGenerateImage} disabled={!isOnline || !!isLoading} className="py-2 px-3 rounded-lg border border-cyber-cyan/20 bg-[#0d0d0d] text-slate-300 hover:border-cyber-cyan/40 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"><SparklesIcon size={12} className="inline mr-1"/> Generar IA</button>
                    <button onClick={() => setIsImageSearchOpen(true)} disabled={!isOnline || !!isLoading} className="py-2 px-3 rounded-lg border border-cyber-cyan/20 bg-[#0d0d0d] text-slate-300 hover:border-cyber-cyan/40 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"><ImageIcon size={12} className="inline mr-1"/> Buscar Imagen</button>
                    <button onClick={addVideoUrl} disabled={!!isLoading} className="py-2 px-3 rounded-lg border border-cyber-cyan/20 bg-[#0d0d0d] text-slate-300 hover:border-cyber-cyan/40 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"><PlusIcon size={12} className="inline mr-1"/> Video URL</button>
                    <button onClick={handleSearchVideos} disabled={!isOnline || !!isLoading} className="py-2 px-3 rounded-lg border border-cyber-cyan/20 bg-[#0d0d0d] text-slate-300 hover:border-cyber-cyan/40 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"><VideoIcon size={12} className="inline mr-1"/> Buscar Video</button>
                </div>
            </div>
        </>
    )
};

const RecommendedMobility: React.FC<{ exerciseNames?: string[] }> = ({ exerciseNames }) => {
    if (!exerciseNames || exerciseNames.length === 0) return null;

    return (
        <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
            <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3">Movilidad Recomendada (Pre-sesión)</h3>
            <ul className="list-disc list-inside space-y-1 text-slate-300 text-sm">
                {exerciseNames.map((name, i) => (
                    <li key={i}>
                        <ExerciseLink name={name} />
                    </li>
                ))}
            </ul>
        </div>
    );
};

// --- Main Component ---

interface ExerciseDetailViewProps {
    exerciseId: string;
    isOnline: boolean;
    muscleHierarchy: MuscleHierarchy;
}

export const ExerciseDetailView: React.FC<ExerciseDetailViewProps> = ({ exerciseId, isOnline, muscleHierarchy }) => {
    const { settings, exerciseList, programs } = useAppState();
    const { setCurrentBackgroundOverride, addOrUpdateCustomExercise, openCustomExerciseEditor } = useAppDispatch();
    const [exercise, setExercise] = useState<ExerciseMuscleInfo | null>(null);
    const [isLoading, setIsLoading] = useState({ analysis: false, community: false });
    
    useEffect(() => {
        const foundExercise = exerciseList.find(e => e.id === exerciseId);
        if (foundExercise) {
            setExercise(foundExercise);
        }
    }, [exerciseId, exerciseList]);

    const registeredVariants = useMemo(() => {
        if (!exercise) return [];
        const variants = new Set<string>();
        programs.forEach(p => 
            p.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles)).forEach(meso => 
                meso.weeks.forEach(w => 
                    w.sessions.forEach(s => 
                        s.exercises.forEach(ex => {
                            if (ex.exerciseDbId === exercise.id && ex.variantName) {
                                variants.add(ex.variantName);
                            }
                        })
                    )
                )
            )
        );
        return Array.from(variants);
    }, [exercise, programs]);


    useEffect(() => {
        const mainImage = exercise?.images?.[0];
        if (mainImage) {
            setCurrentBackgroundOverride({ type: 'image', value: mainImage, style: { blur: 16, brightness: 0.4 } });
        } else {
             setCurrentBackgroundOverride(undefined);
        }
        return () => setCurrentBackgroundOverride(undefined);
    }, [exercise, setCurrentBackgroundOverride]);


    useEffect(() => {
        const foundExercise = exerciseList.find(e => e.id === exerciseId);
        if (foundExercise && isOnline) {
            if (!foundExercise.aiCoachAnalysis) fetchAICoachAnalysis(foundExercise);
            if (!foundExercise.communityOpinion) fetchCommunityOpinion(foundExercise);
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [exerciseId, isOnline]); // Fetch AI data only when ID changes

    const updateExerciseState = (updatedData: Partial<ExerciseMuscleInfo>) => {
        setExercise(prev => {
            if (!prev) return null;
            const newExercise = { ...prev, ...updatedData };
            addOrUpdateCustomExercise(newExercise); // Persist changes
            return newExercise;
        });
    };

    const fetchAICoachAnalysis = async (ex: ExerciseMuscleInfo) => {
        setIsLoading(prev => ({ ...prev, analysis: true }));
        try {
            const analysis = await getAICoachAnalysis(ex.name, settings);
            updateExerciseState({ aiCoachAnalysis: analysis });
        } catch (e) { console.error(e); } finally {
            setIsLoading(prev => ({ ...prev, analysis: false }));
        }
    };
    
    const fetchCommunityOpinion = async (ex: ExerciseMuscleInfo) => {
        setIsLoading(prev => ({ ...prev, community: true }));
        try {
            const opinions = await getCommunityOpinionForExercise(ex.name, settings);
            updateExerciseState({ communityOpinion: opinions });
        } catch (e) { console.error(e); } finally {
            setIsLoading(prev => ({ ...prev, community: false }));
        }
    };

    const handleUserRating = (rating: number) => updateExerciseState({ userRating: rating });
    const handleFavoriteToggle = () => updateExerciseState({ isFavorite: !exercise?.isFavorite });
    const handleMediaUpdate = (images: string[], videos: string[]) => updateExerciseState({ images, videos });

    const primaryMuscle = exercise?.involvedMuscles?.find(m => m.role === 'primary')?.muscle || 'Desconocido';
    const similarExercises = useMemo(() => {
        if (!exercise) return [];
        return exerciseList
            .filter(ex => ex.id !== exercise.id && (
                (ex.force && ex.force === exercise.force) ||
                ex.involvedMuscles?.some(m => m.role === 'primary' && m.muscle === primaryMuscle)
            ))
            .slice(0, 6);
    }, [exercise, exerciseList, primaryMuscle]);

    if (!exercise) {
        return <div className="pt-24 text-center text-slate-400 font-mono">Cargando ejercicio...</div>;
    }

    return (
        <div className="tab-bar-safe-area animate-fade-in bg-[#0a0a0a] min-h-screen">
            <header className="relative h-48 -mx-4 border-b border-cyber-cyan/20">
                <div className="absolute bottom-4 left-4 right-4 z-10">
                     <h1 className="text-3xl font-bold text-white flex items-center gap-2">
                         <button onClick={handleFavoriteToggle} aria-label="Marcar como favorito">
                            <StarIcon size={28} filled={exercise.isFavorite} className={exercise.isFavorite ? 'text-yellow-400' : 'text-slate-600 hover:text-yellow-400'}/>
                         </button>
                         {exercise.name}
                    </h1>
                    {exercise.alias && <p className="text-slate-400 text-sm ml-10 italic">{exercise.alias}</p>}
                    <p className="text-slate-300 ml-10 mt-1">{primaryMuscle}</p>
                </div>
            </header>
            
            <div className="space-y-6 mt-6 px-4">
                <div className="flex flex-wrap gap-2">
                    <TagWithTooltip label={exercise.category} />
                    <TagWithTooltip label={exercise.type} />
                    <TagWithTooltip label={exercise.equipment} />
                    {exercise.force && <TagWithTooltip label={exercise.force} />}
                </div>
                
                <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                    <label className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-2 block">Tu Calificación</label>
                    <StarRating rating={exercise.userRating || 0} onSetRating={handleUserRating} />
                </div>

                {/* AUGE interactivo */}
                {(exercise.efc != null || exercise.cnc != null || exercise.ssc != null) && (
                    <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                        <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3">1 Serie Efectiva = Fatiga AUGE</h3>
                        <div className="grid grid-cols-3 gap-4">
                            {exercise.efc != null && (
                                <div>
                                    <p className="text-[10px] font-mono text-slate-500 uppercase">EFC</p>
                                    <div className="h-2 bg-slate-800 rounded-full mt-1 overflow-hidden">
                                        <div className="h-full bg-cyber-cyan/80 rounded-full" style={{ width: `${(exercise.efc / 5) * 100}%` }} />
                                    </div>
                                    <p className="text-sm font-mono text-white mt-0.5">{exercise.efc}/5</p>
                                </div>
                            )}
                            {exercise.cnc != null && (
                                <div>
                                    <p className="text-[10px] font-mono text-slate-500 uppercase">CNC</p>
                                    <div className="h-2 bg-slate-800 rounded-full mt-1 overflow-hidden">
                                        <div className="h-full bg-cyber-cyan/80 rounded-full" style={{ width: `${(exercise.cnc / 5) * 100}%` }} />
                                    </div>
                                    <p className="text-sm font-mono text-white mt-0.5">{exercise.cnc}/5</p>
                                </div>
                            )}
                            {exercise.ssc != null && (
                                <div>
                                    <p className="text-[10px] font-mono text-slate-500 uppercase">SSC</p>
                                    <div className="h-2 bg-slate-800 rounded-full mt-1 overflow-hidden">
                                        <div className="h-full bg-cyber-cyan/80 rounded-full" style={{ width: `${Math.min((exercise.ssc / 2) * 100, 100)}%` }} />
                                    </div>
                                    <p className="text-sm font-mono text-white mt-0.5">{exercise.ssc}/2</p>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {exercise.functionalTransfer && (
                    <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                        <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-2">Transferencia Funcional</h3>
                        <p className="text-sm text-slate-300">{exercise.functionalTransfer}</p>
                    </div>
                )}

                {((exercise.injuryRisk?.details || exercise.commonMistakes?.length) ?? 0) > 0 && (
                    <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                        <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-red-400/90 mb-2">Malestares / Precauciones</h3>
                        {exercise.injuryRisk?.details && <p className="text-sm text-slate-300 mb-2">{exercise.injuryRisk.details}</p>}
                        {exercise.commonMistakes && exercise.commonMistakes.length > 0 && (
                            <ul className="list-disc list-inside text-sm text-slate-400 space-y-1">
                                {exercise.commonMistakes.map((m, i) => <li key={i}>{m.mistake}: {m.correction}</li>)}
                            </ul>
                        )}
                    </div>
                )}

                {similarExercises.length > 0 && (
                    <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                        <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3">Ejercicios Similares</h3>
                        <div className="flex flex-wrap gap-2">
                            {similarExercises.map(ex => (
                                <span key={ex.id} className="inline-block">
                                    <ExerciseLink name={ex.name} />
                                </span>
                            ))}
                        </div>
                    </div>
                )}

                {((exercise.progressions?.length || 0) + (exercise.regressions?.length || 0)) > 0 && (
                    <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                        <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3">Ejercicios que Mejoran este Movimiento</h3>
                        <div className="space-y-2 text-sm">
                            {exercise.progressions?.map((p, i) => (
                                <div key={`prog-${i}`} className="flex items-start gap-2">
                                    <ArrowUpIcon size={14} className="text-green-400 flex-shrink-0 mt-0.5" />
                                    <div><span className="font-semibold text-slate-200">{p.name}:</span> <span className="text-slate-400">{p.description}</span></div>
                                </div>
                            ))}
                            {exercise.regressions?.map((r, i) => (
                                <div key={`reg-${i}`} className="flex items-start gap-2">
                                    <ArrowDownIcon size={14} className="text-yellow-400 flex-shrink-0 mt-0.5" />
                                    <div><span className="font-semibold text-slate-200">{r.name}:</span> <span className="text-slate-400">{r.description}</span></div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
                
                <ImageVideoGallery images={exercise.images || []} videos={exercise.videos || []} exerciseName={exercise.name} isOnline={isOnline} onUpdate={handleMediaUpdate} />

                <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                     <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-2">Descripción</h3>
                    <p className="whitespace-pre-wrap text-slate-300 text-sm">{exercise.description}</p>
                </div>
                
                {registeredVariants.length > 0 && (
                    <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                         <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-2">Variantes Registradas</h3>
                         <ul className="list-disc list-inside text-sm text-slate-300">
                            {registeredVariants.map(variant => <li key={variant}>{variant}</li>)}
                         </ul>
                    </div>
                )}

                <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                    <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3 text-center">Análisis de Rendimiento</h3>
                    <div className="space-y-4">
                        <div className="space-y-4">
                            {exercise.setupTime != null && <IndicatorBar label="Tiempo de Preparación" value={exercise.setupTime} />}
                            {exercise.technicalDifficulty != null && <IndicatorBar label="Curva de Aprendizaje" value={exercise.technicalDifficulty} />}
                            {exercise.injuryRisk && <IndicatorBar label="Riesgo Lesivo por Mala Técnica" value={exercise.injuryRisk.level} details={exercise.injuryRisk.details} />}
                            {exercise.transferability != null && <IndicatorBar label="Transferencia Funcional" value={exercise.transferability} />}
                        </div>

                        {((exercise.averageRestSeconds != null) || exercise.coreInvolvement || (exercise.bracingRecommended != null) || (exercise.strapsRecommended != null) || (exercise.bodybuildingScore != null)) && (
                            <div className="pt-4 border-t border-white/5">
                                <p className="text-[10px] font-mono text-slate-500 uppercase mb-2">Detalle operativo</p>
                                <div className="grid grid-cols-2 gap-2">
                                    {exercise.averageRestSeconds != null && (
                                        <div className="p-3 rounded-xl border border-cyber-cyan/20 bg-[#0d0d0d]">
                                            <p className="text-[10px] font-mono text-slate-500 uppercase">Descanso (s)</p>
                                            <p className="text-lg font-mono text-cyber-cyan">{exercise.averageRestSeconds}</p>
                                        </div>
                                    )}
                                    {exercise.coreInvolvement && (
                                        <div className="p-3 rounded-xl border border-cyber-cyan/20 bg-[#0d0d0d]">
                                            <p className="text-[10px] font-mono text-slate-500 uppercase">Core</p>
                                            <p className="text-sm font-mono text-cyber-cyan capitalize">{exercise.coreInvolvement}</p>
                                        </div>
                                    )}
                                    {exercise.bracingRecommended != null && (
                                        <div className="p-3 rounded-xl border border-cyber-cyan/20 bg-[#0d0d0d]">
                                            <p className="text-[10px] font-mono text-slate-500 uppercase">Bracing</p>
                                            <p className="text-sm font-mono text-cyber-cyan">{exercise.bracingRecommended ? 'Sí' : 'No'}</p>
                                        </div>
                                    )}
                                    {exercise.strapsRecommended != null && (
                                        <div className="p-3 rounded-xl border border-cyber-cyan/20 bg-[#0d0d0d]">
                                            <p className="text-[10px] font-mono text-slate-500 uppercase">Straps</p>
                                            <p className="text-sm font-mono text-cyber-cyan">{exercise.strapsRecommended ? 'Recomendado' : 'Opcional'}</p>
                                        </div>
                                    )}
                                    {exercise.bodybuildingScore != null && (
                                        <div className="p-3 rounded-xl border border-cyber-cyan/20 bg-[#0d0d0d]">
                                            <p className="text-[10px] font-mono text-slate-500 uppercase">Culturismo</p>
                                            <p className="text-lg font-mono text-cyber-cyan">{exercise.bodybuildingScore}/10</p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                
                
                <KinesiologyAnalysis exercise={exercise} />

                {exercise.recommendedMobility && exercise.recommendedMobility.length > 0 && <RecommendedMobility exerciseNames={exercise.recommendedMobility} />}
                
                <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                    <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3 flex items-center gap-2"><SparklesIcon size={14}/> Análisis del Coach IA</h3>
                    {isLoading.analysis ? <SkeletonLoader lines={5} /> : exercise.aiCoachAnalysis ? (
                        <div className="space-y-4 text-sm">
                            <p className="italic text-slate-300">"{exercise.aiCoachAnalysis.summary}"</p>
                            <div>
                                <h4 className="font-semibold text-green-400 mb-1">Pros:</h4>
                                <ul className="list-disc list-inside text-slate-300">
                                    {exercise.aiCoachAnalysis.pros.map((pro, i) => <li key={i}>{pro}</li>)}
                                </ul>
                            </div>
                            <div>
                                 <h4 className="font-semibold text-yellow-400 mb-1">Contras:</h4>
                                <ul className="list-disc list-inside text-slate-300">
                                    {exercise.aiCoachAnalysis.cons.map((con, i) => <li key={i}>{con}</li>)}
                                </ul>
                            </div>
                        </div>
                    ) : <p className="text-sm text-slate-500">No hay análisis disponible.</p>}
                </div>

                <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                     <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3 flex items-center gap-2"><UsersIcon size={14}/> Opinión de la Comunidad</h3>
                     {isLoading.community ? <SkeletonLoader lines={3} /> : exercise.communityOpinion && exercise.communityOpinion.length > 0 ? (
                         <ul className="list-disc list-inside space-y-2 text-sm text-slate-300">
                            {exercise.communityOpinion.map((op, i) => <li key={i}>{op}</li>)}
                        </ul>
                     ) : <p className="text-sm text-slate-500">No hay opiniones disponibles.</p>}
                </div>

                {exercise.sportsRelevance && exercise.sportsRelevance.length > 0 && (
                     <div className="p-4 rounded-xl border border-cyber-cyan/20 bg-[#0a0a0a]">
                         <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-cyber-cyan/90 mb-3">Relevancia Deportiva</h3>
                        <div className="flex flex-wrap gap-2">
                            {exercise.sportsRelevance.map(sport => (
                                <span key={sport} className="px-2 py-1 bg-[#0d0d0d] border border-cyber-cyan/20 text-slate-300 rounded-lg text-[10px] font-mono">{sport}</span>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};