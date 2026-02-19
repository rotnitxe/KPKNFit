// components/ExerciseDetailView.tsx
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExerciseMuscleInfo, MuscleHierarchy } from '../types';
import { getAICoachAnalysis, getCommunityOpinionForExercise, generateImage, searchGoogleImages, searchWebForExerciseVideos, estimateSFR, getPrimeStarsRating } from '../services/aiService';
import { StarIcon, SparklesIcon, BrainIcon, UploadIcon, VideoIcon, ImageIcon, PlusIcon, TrendingUpIcon, CheckCircleIcon, XCircleIcon, ChevronRightIcon, DumbbellIcon, ActivityIcon, TrophyIcon, UsersIcon, AlertTriangleIcon, ArrowUpIcon, ArrowDownIcon, PencilIcon } from './icons';
import Button from './ui/Button';
import { ExerciseLink } from './ExerciseLink';
import { InfoTooltip } from './ui/InfoTooltip';
import ImageSearchModal from './ImageSearchModal';
import SkeletonLoader from './ui/SkeletonLoader';
import { MuscleActivationView } from './ExerciseInfoModal';
import Card from './ui/Card';

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
    <div className="flex items-center px-2 py-1 bg-slate-700 text-slate-300 rounded-full">
        <span className="text-xs font-semibold">{label}</span>
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
        <Card>
             <h3 className="font-bold text-lg text-white mb-3 flex items-center gap-2"><BrainIcon /> Análisis Kinesiológico</h3>
             <div className="space-y-4">
                {resistanceProfile && (
                    <div className="bg-slate-900/50 p-3 rounded-lg">
                        <h4 className="font-semibold text-primary-color">Perfil de Resistencia</h4>
                        <p className="text-sm text-slate-300">Tensión Máxima: <span className="font-bold capitalize">{resistanceProfile.peakTensionPoint}</span></p>
                    </div>
                )}
                {commonMistakes && commonMistakes.length > 0 && (
                     <details className="glass-card-nested !p-0">
                        <summary className="p-3 cursor-pointer flex justify-between items-center list-none"><h4 className="font-semibold text-white">Faltas Técnicas Comunes</h4><ChevronRightIcon className="details-arrow"/></summary>
                        <ul className="p-3 border-t border-slate-700/50 list-disc list-inside space-y-2 text-sm">
                            {commonMistakes.map((item, i) => <li key={i}><strong className="text-slate-300">{item.mistake}:</strong> <span className="text-slate-400">{item.correction}</span></li>)}
                        </ul>
                    </details>
                )}
                 {anatomicalConsiderations && anatomicalConsiderations.length > 0 && (
                     <details className="glass-card-nested !p-0">
                        <summary className="p-3 cursor-pointer flex justify-between items-center list-none"><h4 className="font-semibold text-white">Consideraciones Anatómicas</h4><ChevronRightIcon className="details-arrow"/></summary>
                        <ul className="p-3 border-t border-slate-700/50 list-disc list-inside space-y-2 text-sm">
                            {anatomicalConsiderations.map((item, i) => <li key={i}><strong className="text-slate-300">{item.trait}:</strong> <span className="text-slate-400">{item.advice}</span></li>)}
                        </ul>
                    </details>
                )}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {progressions && progressions.length > 0 && (
                        <details className="glass-card-nested !p-0">
                            <summary className="p-3 cursor-pointer flex justify-between items-center list-none"><h4 className="font-semibold text-green-400 flex items-center gap-2"><ArrowUpIcon/> Progresiones</h4><ChevronRightIcon className="details-arrow"/></summary>
                            <ul className="p-3 border-t border-slate-700/50 list-disc list-inside space-y-2 text-sm">
                                {progressions.map((item, i) => <li key={i}><strong className="text-slate-300">{item.name}:</strong> <span className="text-slate-400">{item.description}</span></li>)}
                            </ul>
                        </details>
                    )}
                     {regressions && regressions.length > 0 && (
                        <details className="glass-card-nested !p-0">
                            <summary className="p-3 cursor-pointer flex justify-between items-center list-none"><h4 className="font-semibold text-yellow-400 flex items-center gap-2"><ArrowDownIcon/> Regresiones</h4><ChevronRightIcon className="details-arrow"/></summary>
                            <ul className="p-3 border-t border-slate-700/50 list-disc list-inside space-y-2 text-sm">
                                {regressions.map((item, i) => <li key={i}><strong className="text-slate-300">{item.name}:</strong> <span className="text-slate-400">{item.description}</span></li>)}
                            </ul>
                        </details>
                    )}
                </div>
                 {periodizationNotes && periodizationNotes.length > 0 && (
                     <details className="glass-card-nested !p-0">
                        <summary className="p-3 cursor-pointer flex justify-between items-center list-none"><h4 className="font-semibold text-white">Contexto de Programa</h4><ChevronRightIcon className="details-arrow"/></summary>
                        <div className="p-3 border-t border-slate-700/50 space-y-3 text-sm">
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
        </Card>
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
            <div className="glass-card p-4">
                <h3 className="font-bold text-lg text-white mb-3">Galería Multimedia</h3>
                <div className="relative">
                    <div className="flex overflow-x-auto snap-x snap-mandatory space-x-3 pb-3 hide-scrollbar">
                        {images.map((img, i) => (
                            <div key={`img-${i}`} className="snap-center flex-shrink-0 w-4/5 sm:w-3/5">
                                 <img src={img} alt={`${exerciseName} ${i+1}`} className="aspect-video w-full object-cover rounded-lg shadow-md" />
                            </div>
                        ))}
                        {videos.map((vid, i) => (
                             <div key={`vid-${i}`} className="snap-center flex-shrink-0 w-4/5 sm:w-3/5">
                                <a href={vid} target="_blank" rel="noopener noreferrer" className="aspect-video bg-black rounded-lg flex items-center justify-center text-white w-full h-full shadow-md">
                                    <VideoIcon size={32} />
                                </a>
                            </div>
                        ))}
                        {!hasMedia && (
                            <div className="flex-shrink-0 w-full text-center text-slate-500 text-sm py-8 bg-slate-900/50 rounded-lg">
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
                <div className="grid grid-cols-2 gap-2 text-xs mt-4">
                    <Button onClick={() => fileInputRef.current?.click()} variant="secondary"><UploadIcon size={12}/> Subir</Button>
                    <input type="file" ref={fileInputRef} onChange={handleUpload} accept="image/*" className="hidden"/>
                    <Button onClick={handleGenerateImage} variant="secondary" isLoading={isLoading === 'ai'} disabled={!isOnline || !!isLoading}><SparklesIcon size={12}/> Generar IA</Button>
                    <Button onClick={() => setIsImageSearchOpen(true)} variant="secondary" disabled={!isOnline || !!isLoading}><ImageIcon size={12}/> Buscar Imagen</Button>
                    <Button onClick={addVideoUrl} variant="secondary" disabled={!!isLoading}><PlusIcon size={12}/> Video URL</Button>
                    <Button onClick={handleSearchVideos} variant="secondary" isLoading={isLoading === 'webVideo'} disabled={!isOnline || !!isLoading}><VideoIcon size={12}/> Buscar Video</Button>
                </div>
            </div>
        </>
    )
};

const RecommendedMobility: React.FC<{ exerciseNames?: string[] }> = ({ exerciseNames }) => {
    if (!exerciseNames || exerciseNames.length === 0) return null;

    return (
        <div className="glass-card p-4">
            <h3 className="font-bold text-lg text-white mb-3">Movilidad Recomendada (Pre-sesión)</h3>
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
    const [isLoading, setIsLoading] = useState({ analysis: false, community: false, rating: false, sfr: false });
    
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
            if (!foundExercise.primeStars) fetchPrimeStarsRating(foundExercise);
            if (!foundExercise.sfr) fetchSFR(foundExercise);
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

    const fetchPrimeStarsRating = async (ex: ExerciseMuscleInfo) => {
         setIsLoading(prev => ({ ...prev, rating: true }));
        try {
            const ratingData = await getPrimeStarsRating(ex.name, settings);
            updateExerciseState({ primeStars: ratingData });
        } catch (e) { console.error(e); } finally {
            setIsLoading(prev => ({ ...prev, rating: false }));
        }
    };
    
    const fetchSFR = async (ex: ExerciseMuscleInfo) => {
        setIsLoading(prev => ({ ...prev, sfr: true }));
        try {
            const sfr = await estimateSFR(ex.name, settings);
            updateExerciseState({ sfr });
        } catch (e) { console.error(e); } finally {
            setIsLoading(prev => ({ ...prev, sfr: false }));
        }
    };

    const handleUserRating = (rating: number) => updateExerciseState({ userRating: rating });
    const handleFavoriteToggle = () => updateExerciseState({ isFavorite: !exercise?.isFavorite });
    const handleMediaUpdate = (images: string[], videos: string[]) => updateExerciseState({ images, videos });

    if (!exercise) {
        return <div className="pt-24 text-center">Cargando ejercicio...</div>;
    }

    const primaryMuscle = exercise.involvedMuscles?.find(m => m.role === 'primary')?.muscle || 'Desconocido';

    const getSfrColor = (score: number | undefined) => {
        if (score === undefined) return 'hsl(0, 0%, 50%)';
        const hue = score * 12; // 1 -> 12 (red), 10 -> 120 (green)
        return `hsl(${hue}, 80%, 45%)`;
    };

    return (
        <div className="pb-28 animate-fade-in">
            <header className="relative h-48">
                <div className="absolute bottom-4 left-4 right-4 z-10">
                     <h1 className="text-3xl font-bold text-white flex items-center gap-2">
                         <button onClick={handleFavoriteToggle} aria-label="Marcar como favorito">
                            <StarIcon size={28} filled={exercise.isFavorite} className={exercise.isFavorite ? 'text-yellow-400' : 'text-slate-600 hover:text-yellow-400'}/>
                         </button>
                         {exercise.name}
                         {exercise.isHallOfFame && <span title="Hall of Fame" className="p-1 bg-yellow-400/20 rounded-full"><TrophyIcon size={16} className="text-yellow-400"/></span>}
                    </h1>
                    {exercise.alias && <p className="text-slate-400 text-sm ml-10 italic">{exercise.alias}</p>}
                    <p className="text-slate-300 ml-10 mt-1">{primaryMuscle}</p>
                </div>
            </header>
            
            <div className="space-y-6 mt-6">
                <div className="flex flex-wrap gap-2">
                    <TagWithTooltip label={exercise.category} />
                    <TagWithTooltip label={exercise.type} />
                    <TagWithTooltip label={exercise.equipment} />
                    {exercise.force && <TagWithTooltip label={exercise.force} />}
                </div>
                
                <Card>
                    <div className="flex flex-col sm:flex-row justify-around items-center gap-6">
                        <div className="text-center">
                            <label className="text-sm font-semibold text-slate-300 mb-2 block">Tu Calificación</label>
                            <StarRating rating={exercise.userRating || 0} onSetRating={handleUserRating} />
                        </div>
                        <div className="w-full sm:w-px h-px sm:h-16 bg-slate-700"></div> {/* Divider */}
                        <div className="text-center">
                            <label className="text-sm font-semibold text-slate-300 mb-2 block">PrimeStars (IA)</label>
                            {isLoading.rating ? <SkeletonLoader className="h-5 w-24 mx-auto" lines={1} /> : <StarRating rating={exercise.primeStars?.score || 0} colorClass="text-sky-400"/>}
                        </div>
                    </div>
                </Card>

                {exercise.primeStars && !isLoading.rating && (
                     <blockquote className="glass-card !p-3 text-sm italic text-slate-300 border-l-4 border-sky-400">
                        {exercise.primeStars.justification}
                    </blockquote>
                )}
                
                <ImageVideoGallery images={exercise.images || []} videos={exercise.videos || []} exerciseName={exercise.name} isOnline={isOnline} onUpdate={handleMediaUpdate} />

                <div className="glass-card p-4">
                     <h3 className="font-bold text-lg text-white mb-2">Descripción</h3>
                    <p className="whitespace-pre-wrap text-slate-300">{exercise.description}</p>
                </div>
                
                {registeredVariants.length > 0 && (
                    <div className="glass-card p-4">
                         <h3 className="font-bold text-lg text-white mb-2">Variantes Registradas</h3>
                         <ul className="list-disc list-inside text-sm text-slate-300">
                            {registeredVariants.map(variant => <li key={variant}>{variant}</li>)}
                         </ul>
                    </div>
                )}

                <div className="glass-card p-4">
                    <h3 className="font-bold text-lg text-white mb-3 text-center">Análisis de Rendimiento</h3>
                    <div className="space-y-4">
                        {exercise.sfr ? (
                            <div className="flex flex-col items-center gap-4 text-center">
                                <div className="relative w-40 h-40">
                                    <svg className="w-full h-full" viewBox="0 0 36 36" style={{ transform: 'rotate(-90deg)' }}>
                                        <path className="text-slate-700" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="currentColor" strokeWidth="2.5"></path>
                                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeDasharray={`${exercise.sfr.score * 10}, 100`} style={{ stroke: getSfrColor(exercise.sfr.score), transition: 'stroke-dasharray 0.5s ease-out' }}></path>
                                    </svg>
                                    <div className="absolute inset-0 flex flex-col items-center justify-center">
                                        <span className="text-6xl font-black" style={{ color: getSfrColor(exercise.sfr.score) }}>{exercise.sfr.score}</span>
                                        <span className="text-sm font-bold text-slate-400">SFR</span>
                                    </div>
                                </div>
                                <div className="max-w-xs">
                                    <h4 className="font-semibold text-white">Ratio Estímulo-Fatiga</h4>
                                    <p className="text-xs text-slate-400">{exercise.sfr.justification}</p>
                                </div>
                            </div>
                        ) : isLoading.sfr ? <div className="flex justify-center"><SkeletonLoader type="circle" className="w-40 h-40" /></div> : null}

                        <div className="space-y-4 pt-4 border-t border-slate-700/50">
                            {exercise.setupTime && <IndicatorBar label="Tiempo de Preparación" value={exercise.setupTime} />}
                            {exercise.technicalDifficulty && <IndicatorBar label="Dificultad Técnica" value={exercise.technicalDifficulty} />}
                            {exercise.injuryRisk && <IndicatorBar label="Riesgo Lesivo" value={exercise.injuryRisk.level} details={exercise.injuryRisk.details} />}
                            {exercise.transferability && <IndicatorBar label="Transferencia Funcional" value={exercise.transferability} />}
                        </div>
                    </div>
                </div>

                {exercise.involvedMuscles && exercise.involvedMuscles.length > 0 && (
                    <div className="glass-card p-4">
                        <h3 className="font-bold text-lg text-white mb-3">Activación Muscular</h3>
                        <MuscleActivationView involvedMuscles={exercise.involvedMuscles} muscleHierarchy={muscleHierarchy} />
                    </div>
                )}
                
                <KinesiologyAnalysis exercise={exercise} />

                {exercise.recommendedMobility && exercise.recommendedMobility.length > 0 && <RecommendedMobility exerciseNames={exercise.recommendedMobility} />}
                
                <div className="glass-card p-4">
                    <h3 className="font-bold text-lg text-white mb-3 flex items-center gap-2"><SparklesIcon/> Análisis del Coach IA</h3>
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

                <div className="glass-card p-4">
                     <h3 className="font-bold text-lg text-white mb-3 flex items-center gap-2"><UsersIcon/> Opinión de la Comunidad</h3>
                     {isLoading.community ? <SkeletonLoader lines={3} /> : exercise.communityOpinion && exercise.communityOpinion.length > 0 ? (
                         <ul className="list-disc list-inside space-y-2 text-sm text-slate-300">
                            {exercise.communityOpinion.map((op, i) => <li key={i}>{op}</li>)}
                        </ul>
                     ) : <p className="text-sm text-slate-500">No hay opiniones disponibles.</p>}
                </div>

                {exercise.sportsRelevance && exercise.sportsRelevance.length > 0 && (
                     <div className="glass-card p-4">
                         <h3 className="font-bold text-lg text-white mb-3">Relevancia Deportiva</h3>
                        <div className="flex flex-wrap gap-2">
                            {exercise.sportsRelevance.map(sport => (
                                <span key={sport} className="px-2 py-1 bg-slate-700 text-slate-300 rounded-full text-xs font-semibold">{sport}</span>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};