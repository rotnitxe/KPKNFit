// components/SubstituteExerciseSheet.tsx - Diseño unificado: gris medio-claro
import React, { useState, useEffect } from 'react';
import { Exercise, Settings } from '../types';
import { suggestExerciseAlternatives } from '../services/aiService';
import { useAppState } from '../contexts/AppContext';
import { XIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';

interface SubstituteExerciseSheetProps {
  isOpen: boolean;
  onClose: () => void;
  exercise: Exercise | null;
  onSelectAlternative: (newExerciseName: string) => void;
}

const SubstituteExerciseSheet: React.FC<SubstituteExerciseSheetProps> = ({ isOpen, onClose, exercise, onSelectAlternative }) => {
    const { settings, exerciseList, isOnline } = useAppState();
    const [reason, setReason] = useState<'busy' | 'pain' | 'variety' | null>(null);
    const [alternatives, setAlternatives] = useState<{ name: string; justification: string }[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    
    useEffect(() => {
        if (!isOpen) {
            setReason(null);
            setAlternatives([]);
            setIsLoading(false);
            setError(null);
        }
    }, [isOpen]);

    const handleReasonSelect = async (selectedReason: 'busy' | 'pain' | 'variety') => {
        if (!exercise || !isOnline) return;
        setReason(selectedReason);
        setIsLoading(true);
        setError(null);
        setAlternatives([]);
        
        const reasonText = { busy: 'equipo ocupado', pain: 'molestia leve', variety: 'buscar variedad' }[selectedReason];
        const primaryMuscle = exerciseList.find(ex => ex.id === exercise.exerciseDbId)?.involvedMuscles.find(m => m.role === 'primary')?.muscle || 'músculo principal';
        
        try {
            const result = await suggestExerciseAlternatives(exercise, reasonText, primaryMuscle, settings);
            setAlternatives(result.alternatives);
        } catch (err: any) {
            setError(err.message || "No se pudieron obtener alternativas.");
        } finally {
            setIsLoading(false);
        }
    };
    
    const renderContent = () => {
        if (isLoading) {
            return <div className="p-4"><SkeletonLoader lines={5} /></div>;
        }
        if (error) {
            return <p className="p-4 text-center text-[#525252]">{error}</p>;
        }
        if (reason && alternatives.length > 0) {
            return (
                <div className="space-y-3">
                    {alternatives.map((alt, index) => (
                        <button key={index} onClick={() => onSelectAlternative(alt.name)} className="w-full text-left p-3 bg-white border border-[#a3a3a3] hover:bg-[#f5f5f5]">
                            <p className="font-semibold text-[#1a1a1a]">{alt.name}</p>
                            <p className="text-xs text-[#525252]">{alt.justification}</p>
                        </button>
                    ))}
                </div>
            );
        }
        return (
            <div className="space-y-3">
                <button onClick={() => handleReasonSelect('busy')} className="w-full text-left py-3 px-4 bg-white text-[#1a1a1a] font-semibold text-sm border border-[#a3a3a3]">Equipo Ocupado</button>
                <button onClick={() => handleReasonSelect('pain')} className="w-full text-left py-3 px-4 bg-white text-[#1a1a1a] font-semibold text-sm border border-[#a3a3a3]">Molestia Leve</button>
                <button onClick={() => handleReasonSelect('variety')} className="w-full text-left py-3 px-4 bg-white text-[#1a1a1a] font-semibold text-sm border border-[#a3a3a3]">Buscar Variedad</button>
            </div>
        );
    }
    
    if (!isOpen) return null;
    
    return (
        <>
            <div className="fixed inset-0 z-[200] bg-black/30 animate-fade-in" onClick={onClose} aria-hidden />
            <div className="fixed left-0 right-0 bottom-0 z-[201] bg-[#e5e5e5] flex flex-col animate-slide-up" style={{ height: '90vh', maxHeight: '90dvh' }}>
                <header className="flex items-center justify-between p-4 shrink-0">
                    <h2 className="text-base font-black text-[#1a1a1a] uppercase tracking-tight">Sustituir: {exercise?.name}</h2>
                    <button onClick={onClose} className="p-2 text-[#525252] hover:text-[#1a1a1a]" aria-label="Cerrar">
                        <XIcon size={18} />
                    </button>
                </header>
                <div className="flex-1 overflow-y-auto p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
                    {!reason && <p className="text-sm text-[#525252] mb-4">¿Por qué quieres sustituir este ejercicio?</p>}
                    {renderContent()}
                </div>
            </div>
        </>
    );
};

export default SubstituteExerciseSheet;
