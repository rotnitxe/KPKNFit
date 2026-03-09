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
import { motion } from 'framer-motion';

// --- Sub-components ---

const StarRating: React.FC<{ rating: number; onSetRating?: (rating: number) => void; colorClass?: string }> = ({ rating, onSetRating, colorClass = "text-yellow-400" }) => (
    <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map(star => (
            <button key={star} type="button" onClick={onSetRating ? () => onSetRating(star) : undefined} disabled={!onSetRating} aria-label={`Calificar con ${star} estrellas`}>
                <StarIcon size={28} filled={star <= rating} className={`${colorClass} ${onSetRating ? 'cursor-pointer hover:scale-110 transition-transform' : ''} ${star <= rating ? 'star-twinkle-animation' : 'text-[#ECE6F0]'}`} />
            </button>
        ))}
    </div>
);

const TagWithTooltip: React.FC<{ label: string }> = ({ label }) => (
    <div className="flex items-center px-3 py-1.5 bg-white/70 backdrop-blur-xl border border-black/[0.03] text-[#49454F] rounded-full shadow-sm">
        <span className="text-[11px] font-bold tracking-wide">{label}</span>
        <InfoTooltip term={label} />
    </div>
);

const IndicatorBar: React.FC<{ label: string; value: number; max?: number; details?: string }> = ({ label, value, max = 10, details }) => {
    const percentage = (value / max) * 100;
    const hue = (value / max * 120);
    const reversedHue = 120 - (value / max * 120);

    return (
        <div className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[20px] p-4 shadow-sm">
            <div className="flex justify-between items-baseline mb-2">
                <span className="text-sm font-bold text-[#1D1B20]">{label}</span>
                <span className="text-sm font-black text-primary">{value}/{max}</span>
            </div>
            <div className="w-full bg-[#ECE6F0] rounded-full h-3">
                <div 
                    className="h-3 rounded-full transition-all duration-500" 
                    style={{ 
                        width: `${percentage}%`, 
                        backgroundColor: `hsl(${label.includes('Riesgo') ? reversedHue : hue}, 70%, 45%)` 
                    }} 
                />
            </div>
            {details && <p className="text-xs text-[#49454F] opacity-60 mt-2">{details}</p>}
        </div>
    );
};

export const KinesiologyAnalysis: React.FC<{ exercise: ExerciseMuscleInfo }> = ({ exercise }) => {
    const { resistanceProfile, commonMistakes, anatomicalConsiderations, progressions, regressions, periodizationNotes } = exercise;
    const hasAnyData = resistanceProfile || commonMistakes?.length || anatomicalConsiderations?.length || progressions?.length || regressions?.length || periodizationNotes?.length;

    if (!hasAnyData) return null;

    return (
        <motion.div 
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.4 }}
            className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
        >
            <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-4 flex items-center gap-2">
                <BrainIcon size={16} /> Análisis Kinesiológico
            </h3>
            <div className="space-y-3">
                {resistanceProfile && (
                    <div className="bg-[#ECE6F0]/50 p-4 rounded-[16px] border border-black/[0.02]">
                        <h4 className="font-black text-primary text-sm mb-2">Perfil de Resistencia</h4>
                        <p className="text-sm text-[#49454F] opacity-80">Tensión Máxima: <span className="font-bold capitalize text-[#1D1B20]">{resistanceProfile.peakTensionPoint}</span></p>
                    </div>
                )}
                {commonMistakes && commonMistakes.length > 0 && (
                    <details className="rounded-[16px] border border-black/[0.03] overflow-hidden bg-white/50">
                        <summary className="p-4 cursor-pointer flex justify-between items-center list-none bg-white/70">
                            <h4 className="text-[11px] font-black text-[#1D1B20]">Faltas Técnicas Comunes</h4>
                            <ChevronRightIcon className="text-[#49454F] opacity-50 details-arrow" />
                        </summary>
                        <ul className="p-4 border-t border-black/[0.03] list-disc list-inside space-y-2 text-sm text-[#49454F] opacity-80">
                            {commonMistakes.map((item, i) => (
                                <li key={i}>
                                    <strong className="text-[#1D1B20]">{item.mistake}:</strong> 
                                    <span className="text-[#49454F] opacity-70 ml-1">{item.correction}</span>
                                </li>
                            ))}
                        </ul>
                    </details>
                )}
                {anatomicalConsiderations && anatomicalConsiderations.length > 0 && (
                    <details className="rounded-[16px] border border-black/[0.03] overflow-hidden bg-white/50">
                        <summary className="p-4 cursor-pointer flex justify-between items-center list-none bg-white/70">
                            <h4 className="text-[11px] font-black text-[#1D1B20]">Consideraciones Anatómicas</h4>
                            <ChevronRightIcon className="text-[#49454F] opacity-50 details-arrow" />
                        </summary>
                        <ul className="p-4 border-t border-black/[0.03] list-disc list-inside space-y-2 text-sm text-[#49454F] opacity-80">
                            {anatomicalConsiderations.map((item, i) => (
                                <li key={i}>
                                    <strong className="text-[#1D1B20]">{item.trait}:</strong> 
                                    <span className="text-[#49454F] opacity-70 ml-1">{item.advice}</span>
                                </li>
                            ))}
                        </ul>
                    </details>
                )}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {progressions && progressions.length > 0 && (
                        <details className="rounded-[16px] border border-black/[0.03] overflow-hidden bg-white/50">
                            <summary className="p-4 cursor-pointer flex justify-between items-center list-none bg-white/70">
                                <h4 className="text-[11px] font-black text-green-600 flex items-center gap-2">
                                    <ArrowUpIcon size={12} /> Progresiones
                                </h4>
                                <ChevronRightIcon className="text-[#49454F] opacity-50 details-arrow" />
                            </summary>
                            <ul className="p-4 border-t border-black/[0.03] list-disc list-inside space-y-2 text-sm text-[#49454F] opacity-80">
                                {progressions.map((item, i) => (
                                    <li key={i}>
                                        <strong className="text-[#1D1B20]">{item.name}:</strong> 
                                        <span className="text-[#49454F] opacity-70 ml-1">{item.description}</span>
                                    </li>
                                ))}
                            </ul>
                        </details>
                    )}
                    {regressions && regressions.length > 0 && (
                        <details className="rounded-[16px] border border-black/[0.03] overflow-hidden bg-white/50">
                            <summary className="p-4 cursor-pointer flex justify-between items-center list-none bg-white/70">
                                <h4 className="text-[11px] font-black text-yellow-600 flex items-center gap-2">
                                    <ArrowDownIcon size={12} /> Regresiones
                                </h4>
                                <ChevronRightIcon className="text-[#49454F] opacity-50 details-arrow" />
                            </summary>
                            <ul className="p-4 border-t border-black/[0.03] list-disc list-inside space-y-2 text-sm text-[#49454F] opacity-80">
                                {regressions.map((item, i) => (
                                    <li key={i}>
                                        <strong className="text-[#1D1B20]">{item.name}:</strong> 
                                        <span className="text-[#49454F] opacity-70 ml-1">{item.description}</span>
                                    </li>
                                ))}
                            </ul>
                        </details>
                    )}
                </div>
                {periodizationNotes && periodizationNotes.length > 0 && (
                    <details className="rounded-[16px] border border-black/[0.03] overflow-hidden bg-white/50">
                        <summary className="p-4 cursor-pointer flex justify-between items-center list-none bg-white/70">
                            <h4 className="text-[11px] font-black text-[#1D1B20]">Contexto de Programa</h4>
                            <ChevronRightIcon className="text-[#49454F] opacity-50 details-arrow" />
                        </summary>
                        <div className="p-4 border-t border-black/[0.03] space-y-3 text-sm">
                            {periodizationNotes.map((item, i) => (
                                <div key={i} className="bg-[#ECE6F0]/30 rounded-[12px] p-3">
                                    <div className="flex justify-between items-center mb-1">
                                        <h5 className="font-black text-primary capitalize text-sm">{item.phase}</h5>
                                        <StarRating rating={item.suitability} colorClass="text-primary" />
                                    </div>
                                    <p className="text-xs text-[#49454F] opacity-70 italic">"{item.notes}"</p>
                                </div>
                            ))}
                        </div>
                    </details>
                )}
            </div>
        </motion.div>
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
            <motion.div 
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.4 }}
                className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
            >
                <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-4">Galería Multimedia</h3>
                <div className="relative">
                    <div className="flex overflow-x-auto snap-x snap-mandatory space-x-3 pb-3 hide-scrollbar">
                        {images.map((img, i) => (
                            <div key={`img-${i}`} className="snap-center flex-shrink-0 w-4/5 sm:w-3/5">
                                <img 
                                    src={img} 
                                    alt={`${exerciseName} ${i + 1}`} 
                                    className="aspect-video w-full object-cover rounded-[16px] shadow-sm border border-black/[0.03]" 
                                />
                            </div>
                        ))}
                        {videos.map((vid, i) => (
                            <div key={`vid-${i}`} className="snap-center flex-shrink-0 w-4/5 sm:w-3/5">
                                <button 
                                    type="button" 
                                    onClick={() => import('../utils/inAppBrowser').then(m => m.openInAppBrowser(vid))} 
                                    className="aspect-video bg-white/90 backdrop-blur-xl rounded-[16px] flex items-center justify-center text-primary w-full h-full shadow-sm border border-black/[0.03] hover:scale-[1.02] transition-transform"
                                >
                                    <VideoIcon size={32} />
                                </button>
                            </div>
                        ))}
                        {!hasMedia && (
                            <div className="flex-shrink-0 w-full text-center text-[#49454F] opacity-60 text-sm font-bold py-8 bg-white/50 backdrop-blur-xl rounded-[16px] border border-black/[0.03]">
                                No hay imágenes ni videos. <br /> ¡Añade algunos!
                            </div>
                        )}
                    </div>
                    {hasMedia && (
                        <div className="absolute top-0 right-0 bottom-3 w-8 bg-gradient-to-l from-white/50 to-transparent pointer-events-none flex items-center justify-center">
                            <ChevronRightIcon className="text-[#49454F] opacity-30 animate-pulse" />
                        </div>
                    )}
                </div>
                <div className="grid grid-cols-2 gap-2 text-[11px] font-bold mt-4">
                    <button 
                        onClick={() => fileInputRef.current?.click()} 
                        className="py-3 px-4 rounded-[16px] border border-black/[0.03] bg-white/90 text-[#49454F] hover:bg-white hover:border-primary/30 transition-all shadow-sm active:scale-[0.98]"
                    >
                        <UploadIcon size={14} className="inline mr-1.5" /> Subir
                    </button>
                    <input type="file" ref={fileInputRef} onChange={handleUpload} accept="image/*" className="hidden" />
                    <button 
                        onClick={handleGenerateImage} 
                        disabled={!isOnline || !!isLoading} 
                        className="py-3 px-4 rounded-[16px] border border-black/[0.03] bg-white/90 text-[#49454F] hover:bg-white hover:border-primary/30 transition-all shadow-sm disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.98]"
                    >
                        <SparklesIcon size={14} className="inline mr-1.5" /> Generar IA
                    </button>
                    <button 
                        onClick={() => setIsImageSearchOpen(true)} 
                        disabled={!isOnline || !!isLoading} 
                        className="py-3 px-4 rounded-[16px] border border-black/[0.03] bg-white/90 text-[#49454F] hover:bg-white hover:border-primary/30 transition-all shadow-sm disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.98]"
                    >
                        <ImageIcon size={14} className="inline mr-1.5" /> Buscar Imagen
                    </button>
                    <button 
                        onClick={addVideoUrl} 
                        disabled={!!isLoading} 
                        className="py-3 px-4 rounded-[16px] border border-black/[0.03] bg-white/90 text-[#49454F] hover:bg-white hover:border-primary/30 transition-all shadow-sm disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.98]"
                    >
                        <PlusIcon size={14} className="inline mr-1.5" /> Video URL
                    </button>
                    <button 
                        onClick={handleSearchVideos} 
                        disabled={!isOnline || !!isLoading} 
                        className="py-3 px-4 rounded-[16px] border border-black/[0.03] bg-white/90 text-[#49454F] hover:bg-white hover:border-primary/30 transition-all shadow-sm disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.98]"
                    >
                        <VideoIcon size={14} className="inline mr-1.5" /> Buscar Video
                    </button>
                </div>
            </motion.div>
        </>
    )
};

const RecommendedMobility: React.FC<{ exerciseNames?: string[] }> = ({ exerciseNames }) => {
    if (!exerciseNames || exerciseNames.length === 0) return null;

    return (
        <motion.div 
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.4 }}
            className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
        >
            <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-3">Movilidad Recomendada (Pre-sesión)</h3>
            <ul className="list-disc list-inside space-y-1.5 text-[#49454F] opacity-80 text-sm">
                {exerciseNames.map((name, i) => (
                    <li key={i}>
                        <ExerciseLink name={name} />
                    </li>
                ))}
            </ul>
        </motion.div>
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
    }, [exerciseId, isOnline]);

    const updateExerciseState = (updatedData: Partial<ExerciseMuscleInfo>) => {
        setExercise(prev => {
            if (!prev) return null;
            const newExercise = { ...prev, ...updatedData };
            addOrUpdateCustomExercise(newExercise);
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
        return (
            <div className="pt-24 text-center text-[#49454F] opacity-60 font-bold">
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ duration: 0.3 }}
                >
                    Cargando ejercicio...
                </motion.div>
            </div>
        );
    }

    return (
        <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.4 }}
            className="tab-bar-safe-area bg-[#FDFCFE] min-h-screen"
        >
            {/* ═══ HEADER ═══ */}
            <header className="relative h-56 -mx-4 border-b border-black/[0.03] bg-gradient-to-b from-white/50 to-transparent">
                <div className="absolute bottom-5 left-6 right-6 z-10">
                    <motion.h1 
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.4, delay: 0.1 }}
                        className="text-[36px] font-black text-[#1D1B20] flex items-center gap-3 tracking-tighter leading-[0.95]"
                    >
                        <button 
                            onClick={handleFavoriteToggle} 
                            aria-label="Marcar como favorito"
                            className="hover:scale-110 active:scale-95 transition-transform"
                        >
                            <StarIcon 
                                size={32} 
                                filled={exercise.isFavorite} 
                                className={exercise.isFavorite ? 'text-yellow-400' : 'text-[#ECE6F0] hover:text-yellow-400'} 
                            />
                        </button>
                        {exercise.name}
                    </motion.h1>
                    {exercise.alias && (
                        <motion.p 
                            initial={{ opacity: 0, x: -10 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ duration: 0.4, delay: 0.15 }}
                            className="text-[#49454F] opacity-60 text-base font-medium ml-11 italic"
                        >
                            {exercise.alias}
                        </motion.p>
                    )}
                    <motion.p 
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.4, delay: 0.2 }}
                        className="text-[#49454F] opacity-80 font-bold ml-11 mt-1.5"
                    >
                        {primaryMuscle}
                    </motion.p>
                </div>
            </header>

            <div className="space-y-5 mt-6 px-6 pb-20">
                {/* Tags */}
                <motion.div 
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.4, delay: 0.25 }}
                    className="flex flex-wrap gap-2"
                >
                    <TagWithTooltip label={exercise.category} />
                    <TagWithTooltip label={exercise.type} />
                    <TagWithTooltip label={exercise.equipment} />
                    {exercise.force && <TagWithTooltip label={exercise.force} />}
                </motion.div>

                {/* Rating Card */}
                <motion.div 
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.4 }}
                    className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                >
                    <label className="text-[11px] font-black uppercase tracking-widest text-primary mb-3 block">Tu Calificación</label>
                    <StarRating rating={exercise.userRating || 0} onSetRating={handleUserRating} />
                </motion.div>

                {/* AUGE Interactivo */}
                {(exercise.efc != null || exercise.cnc != null || exercise.ssc != null) && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4 }}
                        className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                    >
                        <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-4">1 Serie Efectiva = Fatiga AUGE</h3>
                        <div className="grid grid-cols-3 gap-4">
                            {exercise.efc != null && (
                                <div>
                                    <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase tracking-wide">EFC</p>
                                    <div className="h-2.5 bg-[#ECE6F0] rounded-full mt-1.5 overflow-hidden">
                                        <div className="h-full bg-primary rounded-full transition-all duration-500" style={{ width: `${(exercise.efc / 5) * 100}%` }} />
                                    </div>
                                    <p className="text-sm font-black text-primary mt-1">{exercise.efc}/5</p>
                                </div>
                            )}
                            {exercise.cnc != null && (
                                <div>
                                    <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase tracking-wide">CNC</p>
                                    <div className="h-2.5 bg-[#ECE6F0] rounded-full mt-1.5 overflow-hidden">
                                        <div className="h-full bg-primary rounded-full transition-all duration-500" style={{ width: `${(exercise.cnc / 5) * 100}%` }} />
                                    </div>
                                    <p className="text-sm font-black text-primary mt-1">{exercise.cnc}/5</p>
                                </div>
                            )}
                            {exercise.ssc != null && (
                                <div>
                                    <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase tracking-wide">SSC</p>
                                    <div className="h-2.5 bg-[#ECE6F0] rounded-full mt-1.5 overflow-hidden">
                                        <div className="h-full bg-primary rounded-full transition-all duration-500" style={{ width: `${Math.min((exercise.ssc / 2) * 100, 100)}%` }} />
                                    </div>
                                    <p className="text-sm font-black text-primary mt-1">{exercise.ssc}/2</p>
                                </div>
                            )}
                        </div>
                    </motion.div>
                )}

                {/* Transferencia Funcional */}
                {exercise.functionalTransfer && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4 }}
                        className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                    >
                        <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-2">Transferencia Funcional</h3>
                        <p className="text-sm text-[#49454F] opacity-80 leading-relaxed">{exercise.functionalTransfer}</p>
                    </motion.div>
                )}

                {/* Malestares / Precauciones */}
                {(!!exercise.injuryRisk?.details || (exercise.commonMistakes?.length ?? 0) > 0) && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4 }}
                        className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                    >
                        <h3 className="text-[11px] font-black uppercase tracking-widest text-red-500 mb-3 flex items-center gap-2">
                            <AlertTriangleIcon size={14} /> Malestares / Precauciones
                        </h3>
                        {exercise.injuryRisk?.details && (
                            <p className="text-sm text-[#49454F] opacity-80 mb-3 leading-relaxed">{exercise.injuryRisk.details}</p>
                        )}
                        {exercise.commonMistakes && exercise.commonMistakes.length > 0 && (
                            <ul className="list-disc list-inside text-sm text-[#49454F] opacity-70 space-y-1.5">
                                {exercise.commonMistakes.map((m, i) => (
                                    <li key={i}>
                                        <span className="font-bold text-[#1D1B20]">{m.mistake}:</span> 
                                        <span className="ml-1">{m.correction}</span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </motion.div>
                )}

                {/* Ejercicios Similares */}
                {similarExercises.length > 0 && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4 }}
                        className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                    >
                        <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-3">Ejercicios Similares</h3>
                        <div className="flex flex-wrap gap-2">
                            {similarExercises.map(ex => (
                                <span key={ex.id} className="inline-block">
                                    <ExerciseLink name={ex.name} />
                                </span>
                            ))}
                        </div>
                    </motion.div>
                )}

                {/* Progresiones y Regresiones Inline */}
                {((exercise.progressions?.length || 0) + (exercise.regressions?.length || 0)) > 0 && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4 }}
                        className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                    >
                        <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-3">Ejercicios que Mejoran este Movimiento</h3>
                        <div className="space-y-2.5 text-sm">
                            {exercise.progressions?.map((p, i) => (
                                <div key={`prog-${i}`} className="flex items-start gap-2.5">
                                    <ArrowUpIcon size={16} className="text-green-600 flex-shrink-0 mt-0.5" />
                                    <div>
                                        <span className="font-bold text-[#1D1B20]">{p.name}:</span> 
                                        <span className="text-[#49454F] opacity-70 ml-1">{p.description}</span>
                                    </div>
                                </div>
                            ))}
                            {exercise.regressions?.map((r, i) => (
                                <div key={`reg-${i}`} className="flex items-start gap-2.5">
                                    <ArrowDownIcon size={16} className="text-yellow-600 flex-shrink-0 mt-0.5" />
                                    <div>
                                        <span className="font-bold text-[#1D1B20]">{r.name}:</span> 
                                        <span className="text-[#49454F] opacity-70 ml-1">{r.description}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </motion.div>
                )}

                {/* Galería */}
                <ImageVideoGallery 
                    images={exercise.images || []} 
                    videos={exercise.videos || []} 
                    exerciseName={exercise.name} 
                    isOnline={isOnline} 
                    onUpdate={handleMediaUpdate} 
                />

                {/* Descripción */}
                <motion.div 
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.4 }}
                    className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                >
                    <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-3">Descripción</h3>
                    <p className="whitespace-pre-wrap text-[#49454F] opacity-80 text-sm leading-relaxed">{exercise.description}</p>
                </motion.div>

                {/* Variantes Registradas */}
                {registeredVariants.length > 0 && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4 }}
                        className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                    >
                        <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-3">Variantes Registradas</h3>
                        <ul className="list-disc list-inside text-sm text-[#49454F] opacity-80 space-y-1.5">
                            {registeredVariants.map(variant => (
                                <li key={variant}>{variant}</li>
                            ))}
                        </ul>
                    </motion.div>
                )}

                {/* Análisis de Rendimiento */}
                <motion.div 
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.4 }}
                    className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                >
                    <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-4 text-center">Análisis de Rendimiento</h3>
                    <div className="space-y-3">
                        <div className="space-y-3">
                            {exercise.setupTime != null && <IndicatorBar label="Tiempo de Preparación" value={exercise.setupTime} />}
                            {exercise.technicalDifficulty != null && <IndicatorBar label="Curva de Aprendizaje" value={exercise.technicalDifficulty} />}
                            {exercise.injuryRisk && <IndicatorBar label="Riesgo Lesivo por Mala Técnica" value={exercise.injuryRisk.level} details={exercise.injuryRisk.details} />}
                            {exercise.transferability != null && <IndicatorBar label="Transferencia Funcional" value={exercise.transferability} />}
                        </div>

                        {((exercise.averageRestSeconds != null) || exercise.coreInvolvement || (exercise.bracingRecommended != null) || (exercise.strapsRecommended != null) || (exercise.bodybuildingScore != null)) && (
                            <div className="pt-4 border-t border-black/[0.03]">
                                <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase tracking-wide mb-3">Detalle operativo</p>
                                <div className="grid grid-cols-2 gap-3">
                                    {exercise.averageRestSeconds != null && (
                                        <div className="p-4 rounded-[16px] border border-black/[0.03] bg-white/90 shadow-sm">
                                            <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase">Descanso (s)</p>
                                            <p className="text-lg font-black text-primary mt-0.5">{exercise.averageRestSeconds}</p>
                                        </div>
                                    )}
                                    {exercise.coreInvolvement && (
                                        <div className="p-4 rounded-[16px] border border-black/[0.03] bg-white/90 shadow-sm">
                                            <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase">Core</p>
                                            <p className="text-sm font-bold text-primary capitalize mt-0.5">{exercise.coreInvolvement}</p>
                                        </div>
                                    )}
                                    {exercise.bracingRecommended != null && (
                                        <div className="p-4 rounded-[16px] border border-black/[0.03] bg-white/90 shadow-sm">
                                            <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase">Bracing</p>
                                            <p className="text-sm font-bold text-primary mt-0.5">{exercise.bracingRecommended ? 'Sí' : 'No'}</p>
                                        </div>
                                    )}
                                    {exercise.strapsRecommended != null && (
                                        <div className="p-4 rounded-[16px] border border-black/[0.03] bg-white/90 shadow-sm">
                                            <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase">Straps</p>
                                            <p className="text-sm font-bold text-primary mt-0.5">{exercise.strapsRecommended ? 'Recomendado' : 'Opcional'}</p>
                                        </div>
                                    )}
                                    {exercise.bodybuildingScore != null && (
                                        <div className="p-4 rounded-[16px] border border-black/[0.03] bg-white/90 shadow-sm">
                                            <p className="text-[10px] font-bold text-[#49454F] opacity-50 uppercase">Culturismo</p>
                                            <p className="text-lg font-black text-primary mt-0.5">{exercise.bodybuildingScore}/10</p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                </motion.div>

                {/* Kinesiology Analysis */}
                <KinesiologyAnalysis exercise={exercise} />

                {/* Recommended Mobility */}
                {exercise.recommendedMobility && exercise.recommendedMobility.length > 0 && (
                    <RecommendedMobility exerciseNames={exercise.recommendedMobility} />
                )}

                {/* AI Coach Analysis */}
                <motion.div 
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.4 }}
                    className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                >
                    <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-4 flex items-center gap-2">
                        <SparklesIcon size={16} /> Análisis del Coach IA
                    </h3>
                    {isLoading.analysis ? (
                        <SkeletonLoader lines={5} />
                    ) : exercise.aiCoachAnalysis ? (
                        <div className="space-y-4 text-sm">
                            <p className="italic text-[#49454F] opacity-80 leading-relaxed bg-[#ECE6F0]/30 rounded-[16px] p-4">
                                "{exercise.aiCoachAnalysis.summary}"
                            </p>
                            <div>
                                <h4 className="font-black text-green-600 mb-2 text-sm">Pros:</h4>
                                <ul className="list-disc list-inside text-[#49454F] opacity-80 space-y-1.5">
                                    {exercise.aiCoachAnalysis.pros.map((pro, i) => (
                                        <li key={i} className="leading-relaxed">{pro}</li>
                                    ))}
                                </ul>
                            </div>
                            <div>
                                <h4 className="font-black text-yellow-600 mb-2 text-sm">Contras:</h4>
                                <ul className="list-disc list-inside text-[#49454F] opacity-80 space-y-1.5">
                                    {exercise.aiCoachAnalysis.cons.map((con, i) => (
                                        <li key={i} className="leading-relaxed">{con}</li>
                                    ))}
                                </ul>
                            </div>
                        </div>
                    ) : (
                        <p className="text-sm text-[#49454F] opacity-50">No hay análisis disponible.</p>
                    )}
                </motion.div>

                {/* Community Opinion */}
                <motion.div 
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.4 }}
                    className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                >
                    <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-4 flex items-center gap-2">
                        <UsersIcon size={16} /> Opinión de la Comunidad
                    </h3>
                    {isLoading.community ? (
                        <SkeletonLoader lines={3} />
                    ) : exercise.communityOpinion && exercise.communityOpinion.length > 0 ? (
                        <ul className="list-disc list-inside space-y-2.5 text-sm text-[#49454F] opacity-80">
                            {exercise.communityOpinion.map((op, i) => (
                                <li key={i} className="leading-relaxed">{op}</li>
                            ))}
                        </ul>
                    ) : (
                        <p className="text-sm text-[#49454F] opacity-50">No hay opiniones disponibles.</p>
                    )}
                </motion.div>

                {/* Sports Relevance */}
                {exercise.sportsRelevance && exercise.sportsRelevance.length > 0 && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.4 }}
                        className="bg-white/70 backdrop-blur-xl border border-black/[0.03] rounded-[24px] p-5 shadow-sm"
                    >
                        <h3 className="text-[11px] font-black uppercase tracking-widest text-primary mb-3">Relevancia Deportiva</h3>
                        <div className="flex flex-wrap gap-2">
                            {exercise.sportsRelevance.map(sport => (
                                <span 
                                    key={sport} 
                                    className="px-3 py-1.5 bg-white/90 border border-black/[0.03] text-[#49454F] rounded-full text-[11px] font-bold shadow-sm"
                                >
                                    {sport}
                                </span>
                            ))}
                        </div>
                    </motion.div>
                )}
            </div>
        </motion.div>
    );
};
