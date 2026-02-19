// components/BodyLabView.tsx
import React, { useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { generateBodyLabAnalysis, analyzePosturePhoto } from '../services/aiService';
import { BodyLabAnalysis, BiomechanicalAnalysis } from '../types';
import Button from './ui/Button';
import Card from './ui/Card';
import { BrainIcon, SparklesIcon, TrendingUpIcon, ArrowDownIcon, ActivityIcon, ZapIcon, LightbulbIcon, CheckCircleIcon, PlusIcon, DumbbellIcon, XCircleIcon, CameraIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { takePicture } from '../services/cameraService';
import { optimizeImage } from '../services/imageService';

const PostureAnalysis: React.FC = () => {
    const { settings, isOnline } = useAppState();
    const { addToast } = useAppDispatch();
    const [photo, setPhoto] = useState<string | null>(null);
    const [analysis, setAnalysis] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);

    const handleTakePhoto = async () => {
        const imageDataUrl = await takePicture();
        if (imageDataUrl) {
            addToast("Optimizando imagen...", "suggestion");
            const optimized = await optimizeImage(imageDataUrl);
            setPhoto(optimized);
            handleAnalyze(optimized);
        }
    };
    
    const handleAnalyze = async (imageDataUrl: string) => {
        setIsLoading(true);
        setAnalysis(null);
        try {
            const base64Data = imageDataUrl.split(',')[1];
            const result = await analyzePosturePhoto(base64Data, settings);
            setAnalysis(result);
        } catch (e: any) {
            addToast(e.message || "Error al analizar la postura.", "danger");
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-3">Análisis Postural con IA</h3>
            {!photo && (
                <div className="text-center">
                    <p className="text-sm text-slate-400 mb-4">Sube una foto de cuerpo completo (de frente o de lado) para obtener un análisis postural básico.</p>
                    <Button onClick={handleTakePhoto} disabled={!isOnline || isLoading} isLoading={isLoading}>
                        <CameraIcon/> Subir Foto
                    </Button>
                </div>
            )}
            {photo && (
                <div>
                    <img src={photo} alt="Análisis de postura" className="rounded-lg w-full max-w-xs mx-auto mb-4"/>
                    {isLoading && <SkeletonLoader lines={4} />}
                    {analysis && (
                        <div className="prose prose-sm prose-invert max-w-none animate-fade-in" dangerouslySetInnerHTML={{ __html: analysis.replace(/\n/g, '<br />') }} />
                    )}
                    <Button onClick={handleTakePhoto} variant="secondary" className="w-full mt-4">
                        Tomar otra foto
                    </Button>
                </div>
            )}
        </Card>
    );
};


const BiomechanicalAnalysisView: React.FC<{ analysis: BiomechanicalAnalysis }> = ({ analysis }) => (
    <Card>
        <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><ActivityIcon /> Análisis Biomecánico</h3>
        <div className="space-y-4">
            <div className="glass-card-nested p-3 text-center">
                <p className="text-sm font-semibold text-slate-300">Ape Index</p>
                <p className="text-3xl font-bold text-white">{analysis.apeIndex.value.toFixed(2)}</p>
                <p className="text-xs text-slate-400">{analysis.apeIndex.interpretation}</p>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                    <h4 className="font-semibold text-green-400 mb-2 flex items-center gap-2"><CheckCircleIcon /> Ventajas</h4>
                    <ul className="space-y-2 text-sm">
                        {analysis.advantages.map((item, i) => (
                            <li key={i} className="bg-slate-800/50 p-2 rounded-md">
                                <p className="font-semibold text-slate-200">{item.title}</p>
                                <p className="text-xs text-slate-400">{item.explanation}</p>
                            </li>
                        ))}
                    </ul>
                </div>
                <div>
                    <h4 className="font-semibold text-yellow-400 mb-2 flex items-center gap-2"><XCircleIcon /> Desafíos</h4>
                     <ul className="space-y-2 text-sm">
                        {analysis.challenges.map((item, i) => (
                            <li key={i} className="bg-slate-800/50 p-2 rounded-md">
                                <p className="font-semibold text-slate-200">{item.title}</p>
                                <p className="text-xs text-slate-400">{item.explanation}</p>
                            </li>
                        ))}
                    </ul>
                </div>
            </div>
            <div>
                 <h4 className="font-semibold text-white mb-2 flex items-center gap-2"><DumbbellIcon /> Recomendaciones por Ejercicio</h4>
                 <ul className="space-y-2 text-sm">
                    {analysis.exerciseSpecificRecommendations.map((item, i) => (
                        <li key={i} className="bg-slate-800/50 p-2 rounded-md">
                            <p className="font-semibold text-slate-200">{item.exerciseName}</p>
                            <p className="text-xs text-slate-400">{item.recommendation}</p>
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    </Card>
);

const BiomechanicalCTA: React.FC = () => {
    const { setIsMeasurementsModalOpen } = useAppDispatch();
    return (
        <Card>
            <div className="text-center">
                <ActivityIcon size={48} className="mx-auto text-primary-color" />
                <h3 className="text-xl font-bold text-white mt-3">Análisis Biomecánico</h3>
                <p className="text-sm text-slate-300 mt-1 max-w-sm mx-auto">Añade tus medidas corporales (altura, envergadura, etc.) para que la IA analice tus palancas y te dé recomendaciones de técnica personalizadas.</p>
                <Button onClick={() => setIsMeasurementsModalOpen(true)} className="mt-4">
                    <PlusIcon /> Añadir Medidas
                </Button>
            </div>
        </Card>
    );
};


const BodyLabView: React.FC = () => {
    const { history, programs, settings, isOnline, bodyLabAnalysis, biomechanicalAnalysis } = useAppState();
    const { setBodyLabAnalysis } = useAppDispatch();
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleAnalyze = async () => {
        if (!isOnline) {
            setError("BodyLab requiere conexión a internet para realizar el análisis.");
            return;
        }
        if (history.length < 5) {
            setError("Se necesitan al menos 5 entrenamientos registrados para un análisis completo.");
            return;
        }
        setIsLoading(true);
        setError(null);
        try {
            const result = await generateBodyLabAnalysis(programs, history, settings);
            setBodyLabAnalysis(result);
        } catch (err: any) {
            setError(err.message || 'Ocurrió un error al generar tu perfil.');
        } finally {
            setIsLoading(false);
        }
    };
    
    const renderInitialState = () => (
        <div className="space-y-6">
            <Card className="text-center py-10">
                <BrainIcon size={64} className="mx-auto text-sky-400" />
                <h2 className="text-3xl font-bold text-white mt-4">Bienvenido a BodyLab</h2>
                <p className="text-slate-400 mt-2 max-w-md mx-auto">
                    Descubre tu perfil de atleta único. La IA analizará tus programas, progreso y hábitos para revelar tus fortalezas, debilidades y cómo optimizar tu entrenamiento.
                </p>
                <Button onClick={handleAnalyze} className="mt-8 !py-4 !px-8 !text-lg" disabled={!isOnline || isLoading} isLoading={isLoading}>
                    <SparklesIcon /> {isLoading ? 'Analizando...' : 'Generar mi Perfil de Atleta'}
                </Button>
                {error && <p className="text-red-400 text-sm mt-4">{error}</p>}
            </Card>
            {!biomechanicalAnalysis && <BiomechanicalCTA />}
            <PostureAnalysis />
        </div>
    );
    
    const renderLoadingState = () => (
        <div className="text-center py-10">
            <svg className="animate-spin h-12 w-12 text-sky-400 mx-auto" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <h2 className="text-2xl font-bold text-white mt-4 animate-pulse">Analizando tu ADN de Atleta...</h2>
            <p className="text-slate-400 mt-2">Esto puede tardar un momento.</p>
        </div>
    );

    const renderAnalysis = () => {
        if (!bodyLabAnalysis) return null;
        return (
            <div className="space-y-6 animate-fade-in">
                <Card className="text-center bg-gradient-to-br from-sky-900/20 to-transparent border-sky-600/50">
                    <h2 className="text-3xl font-bold text-sky-300">"{bodyLabAnalysis.profileTitle}"</h2>
                    <p className="text-slate-300 mt-2 italic">"{bodyLabAnalysis.profileSummary}"</p>
                </Card>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <Card>
                        <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><TrendingUpIcon className="text-green-400" /> Puntos Fuertes</h3>
                        <ul className="space-y-2">
                            {bodyLabAnalysis.strongPoints.map((point, i) => (
                                <li key={i} className="bg-slate-800/50 p-3 rounded-lg">
                                    <p className="font-semibold text-green-300">{point.muscle}</p>
                                    <p className="text-xs text-slate-400">{point.reason}</p>
                                </li>
                            ))}
                        </ul>
                    </Card>
                    <Card>
                        <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><ArrowDownIcon className="text-yellow-400" /> Puntos a Mejorar</h3>
                        <ul className="space-y-2">
                            {bodyLabAnalysis.weakPoints.map((point, i) => (
                                <li key={i} className="bg-slate-800/50 p-3 rounded-lg">
                                    <p className="font-semibold text-yellow-300">{point.muscle}</p>
                                    <p className="text-xs text-slate-400">{point.reason}</p>
                                </li>
                            ))}
                        </ul>
                    </Card>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                     <Card>
                        <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><ActivityIcon /> Análisis de Frecuencia</h3>
                        <div className="text-center p-4 bg-slate-800/50 rounded-lg">
                             <p className="text-3xl font-bold text-white">{bodyLabAnalysis.frequencyAnalysis.preferredType}</p>
                             <p className="text-sm text-slate-300 mt-1">{bodyLabAnalysis.frequencyAnalysis.summary}</p>
                        </div>
                    </Card>
                     <Card>
                        <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><ZapIcon /> Análisis de Recuperación</h3>
                         <div className="text-center p-4 bg-slate-800/50 rounded-lg">
                             <p className="text-5xl font-bold text-white">{bodyLabAnalysis.recoveryAnalysis.score}<span className="text-2xl">/10</span></p>
                             <p className="text-sm text-slate-300 mt-1">{bodyLabAnalysis.recoveryAnalysis.summary}</p>
                        </div>
                    </Card>
                </div>

                <Card>
                    <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2"><LightbulbIcon className="text-sky-300" /> Recomendaciones del Coach</h3>
                    <ul className="space-y-3">
                        {bodyLabAnalysis.recommendations.map((rec, i) => (
                            <li key={i} className="bg-slate-800/50 p-3 rounded-lg flex items-start gap-3">
                                <CheckCircleIcon size={20} className="text-sky-400 mt-1 flex-shrink-0" />
                                <div>
                                    <p className="font-semibold text-white">{rec.title}</p>
                                    <p className="text-xs text-slate-400">{rec.description}</p>
                                </div>
                            </li>
                        ))}
                    </ul>
                </Card>

                <PostureAnalysis />
                
                <div className="mt-8">
                    {biomechanicalAnalysis ? <BiomechanicalAnalysisView analysis={biomechanicalAnalysis} /> : <BiomechanicalCTA />}
                </div>

                <div className="text-center">
                    <Button onClick={handleAnalyze} variant="secondary" isLoading={isLoading} disabled={!isOnline}>
                        <SparklesIcon /> Regenerar Análisis de Atleta
                    </Button>
                </div>
            </div>
        );
    };

    return (
        <div className="pb-28">
            <header className="text-center mb-8">
                <h1 className="text-4xl font-bold text-white">BodyLab</h1>
                <p className="text-slate-400">Tu Perfil de Atleta Personalizado por IA</p>
            </header>
            
            {isLoading ? renderLoadingState() : (bodyLabAnalysis ? renderAnalysis() : renderInitialState())}
        </div>
    );
};

export default BodyLabView;