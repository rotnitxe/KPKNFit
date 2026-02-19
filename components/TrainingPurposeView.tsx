// components/TrainingPurposeView.tsx
import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { generateExercisesForPurpose } from '../services/aiService';
import { ArrowLeftIcon, SparklesIcon, DumbbellIcon, BrainIcon } from './icons';
import Button from './ui/Button';
import Card from './ui/Card';
import SkeletonLoader from './ui/SkeletonLoader';
import { ExerciseLink } from './ExerciseLink';

interface AiResult {
    exercises: {
        name: string;
        justification: string;
        primaryMuscles: string[];
    }[];
}

const PREDEFINED_PURPOSES = [
    "Mejorar rendimiento en fútbol",
    "Ser más fuerte en el trabajo",
    "Ser el mejor en rugby",
    "Puños de acero en boxeo",
    "Aumentar salto vertical",
    "Correr más rápido (sprints)",
    "Vida longeva y saludable",
    "Pretemporada de Ski",
];

const TrainingPurposeView: React.FC = () => {
    const { settings, isOnline } = useAppState();
    const { handleBack } = useAppDispatch();
    const [purpose, setPurpose] = useState('');
    const [result, setResult] = useState<AiResult | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleGenerate = async (selectedPurpose?: string) => {
        const finalPurpose = selectedPurpose || purpose;
        if (!finalPurpose.trim() || !isOnline) return;

        setIsLoading(true);
        setError(null);
        setResult(null);
        setPurpose(finalPurpose); // Update input field if a button was clicked
        try {
            const response = await generateExercisesForPurpose(finalPurpose, settings);
            setResult(response);
        } catch (err: any) {
            setError(err.message || 'Error al generar ejercicios.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="pb-20 animate-fade-in">
            <header className="flex items-center gap-4 mb-6 -mx-4 px-4">
                <button onClick={handleBack} className="p-2 text-slate-300">
                    <ArrowLeftIcon />
                </button>
                <div>
                    <h1 className="text-3xl font-bold text-white">Laboratorio de Propósitos</h1>
                    <p className="text-slate-400 text-sm">Descubre qué ejercicios necesitas para tu meta.</p>
                </div>
            </header>

            <div className="space-y-6">
                <Card>
                    <div className="space-y-2">
                        <label className="block text-sm font-medium text-slate-300 mb-1">
                            Describe tu objetivo o selecciona uno:
                        </label>
                        <textarea
                            value={purpose}
                            onChange={(e) => setPurpose(e.target.value)}
                            placeholder="Ej: 'Quiero ser más explosivo para el baloncesto'..."
                            rows={3}
                            className="w-full"
                        />
                        <div className="flex flex-wrap gap-2">
                            {PREDEFINED_PURPOSES.map(p => (
                                <button key={p} onClick={() => handleGenerate(p)} className="text-xs px-2 py-1 bg-slate-800 hover:bg-slate-700 rounded-full">
                                    {p}
                                </button>
                            ))}
                        </div>
                    </div>
                    <Button onClick={() => handleGenerate()} disabled={!purpose.trim() || !isOnline || isLoading} isLoading={isLoading} className="w-full mt-4">
                        <SparklesIcon /> Generar Ejercicios Clave
                    </Button>
                </Card>

                {isLoading && (
                    <Card>
                        <SkeletonLoader lines={5} />
                    </Card>
                )}

                {error && <p className="text-red-400 text-center">{error}</p>}

                {result && (
                    <Card className="animate-fade-in">
                        <h2 className="text-2xl font-bold mb-4">Ejercicios para: <span className="text-primary-color">{purpose}</span></h2>
                        <div className="space-y-4">
                            {result.exercises.map((item, index) => (
                                <div key={index} className="bg-slate-900/50 p-4 rounded-lg">
                                    <h3 className="font-semibold text-white text-lg flex items-center gap-2"><DumbbellIcon size={16}/> <ExerciseLink name={item.name} /></h3>
                                    <p className="text-sm text-slate-400 my-1 italic">"{item.justification}"</p>
                                    <div className="flex items-center gap-2 mt-2">
                                        <BrainIcon size={14} className="text-sky-400"/>
                                        <p className="text-xs text-slate-300">
                                            <span className="font-semibold">Músculos Clave:</span> {item.primaryMuscles.join(', ')}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
};

export default TrainingPurposeView;