
// components/ExerciseFeedbackModal.tsx
import React, { useState } from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { CheckCircleIcon, AlertTriangleIcon, SwapIcon, BrainIcon } from './icons';
import { useAppDispatch } from '../contexts/AppContext';

interface ExerciseFeedbackModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (feedback: { jointLoad: number; technicalQuality: number; perceivedFatigue: number }) => void;
  exerciseName: string;
  exerciseDbId?: string;
}

const ExerciseFeedbackModal: React.FC<ExerciseFeedbackModalProps> = ({ isOpen, onClose, onSave, exerciseName, exerciseDbId }) => {
  const { addRecommendationTrigger, addToast } = useAppDispatch();
  const [jointLoad, setJointLoad] = useState(5);
  const [technicalQuality, setTechnicalQuality] = useState(8);
  const [perceivedFatigue, setPerceivedFatigue] = useState(5);
  const [showPainSuggestion, setShowPainSuggestion] = useState(false);

  const handleInitialSave = () => {
    if (jointLoad > 7) {
        setShowPainSuggestion(true);
        // Guardar trigger en storage
        addRecommendationTrigger({
            type: 'joint_pain',
            exerciseDbId,
            exerciseName,
            value: jointLoad,
            context: `Dolor articular detectado (${jointLoad}/10) en ${exerciseName}`
        });
    } else {
        commitFeedback();
    }
  };

  const commitFeedback = () => {
    onSave({ 
        jointLoad, 
        technicalQuality,
        perceivedFatigue,
    });
    // Reset state
    setJointLoad(5);
    setTechnicalQuality(8);
    setPerceivedFatigue(5);
    setShowPainSuggestion(false);
    onClose();
  };

  const handleRequestSubstitution = () => {
      // Esta acción se manejará a través del algoritmo de recomendación
      // pero por ahora damos feedback inmediato
      addToast(`Se ha registrado una solicitud de sustitución para ${exerciseName}.`, "suggestion");
      commitFeedback();
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={showPainSuggestion ? "⚠️ Alerta de Dolor" : `Feedback: ${exerciseName}`}>
      {!showPainSuggestion ? (
        <div className="space-y-6 p-2">
            <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">Carga Articular (1-Baja, 10-Alta)</label>
            <input type="range" min="1" max="10" value={jointLoad} onChange={(e) => setJointLoad(parseInt(e.target.value))} className="w-full" />
            <p className={`text-center font-bold text-lg ${jointLoad > 7 ? 'text-red-400' : 'text-white'}`}>{jointLoad}</p>
            </div>
            <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">Calidad Técnica (1-Mala, 10-Perfecta)</label>
            <input type="range" min="1" max="10" value={technicalQuality} onChange={(e) => setTechnicalQuality(parseInt(e.target.value))} className="w-full" />
            <p className="text-center font-bold text-lg">{technicalQuality}</p>
            </div>
            <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">Fatiga del Ejercicio (1-Baja, 10-Alta)</label>
            <input type="range" min="1" max="10" value={perceivedFatigue} onChange={(e) => setPerceivedFatigue(parseInt(e.target.value))} className="w-full" />
            <p className="text-center font-bold text-lg">{perceivedFatigue}</p>
            </div>
            <div className="flex justify-end pt-4 border-t border-border-color">
            <Button onClick={handleInitialSave} variant="primary" className="w-full !py-3 !text-base">
                <CheckCircleIcon size={20}/> Guardar y Continuar
            </Button>
            </div>
        </div>
      ) : (
        <div className="space-y-6 p-2 animate-fade-in">
            <div className="bg-red-900/20 border border-red-500/50 p-4 rounded-xl text-center">
                <AlertTriangleIcon size={40} className="mx-auto text-red-400 mb-2" />
                <h4 className="text-lg font-bold text-red-100">Estrés Articular Crítico</h4>
                <p className="text-sm text-red-200/70 mt-1">Has reportado un dolor de {jointLoad}/10. Seguir con este ejercicio podría causar una lesión.</p>
            </div>
            
            <div className="space-y-3">
                <p className="text-sm text-slate-300 font-semibold text-center italic">"Recomiendo sustituir este ejercicio por una variante más amigable en las próximas sesiones."</p>
                
                <Button onClick={handleRequestSubstitution} className="w-full !py-4 !justify-start">
                    <SwapIcon size={20}/> Solicitar Sustitución Inteligente
                </Button>
                
                <Button onClick={commitFeedback} variant="secondary" className="w-full !py-4 !justify-start">
                    <BrainIcon size={20}/> Ignorar (Continuar con el plan)
                </Button>
            </div>
        </div>
      )}
    </Modal>
  );
};

export default ExerciseFeedbackModal;
