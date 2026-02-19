
// components/MuscleGroupEditorModal.tsx
import React, { useState, useEffect, useMemo } from 'react';
import { MuscleGroupInfo, SessionBackground, CoverStyle } from '../types';
import { useAppDispatch, useAppState } from '../contexts/AppContext';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SaveIcon, SparklesIcon, Wand2Icon, PlusIcon, TrashIcon, StarIcon } from './icons';
import BackgroundEditorModal from './SessionBackgroundModal';

interface MuscleGroupEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  muscleGroup: MuscleGroupInfo;
}

const MuscleGroupEditorModal: React.FC<MuscleGroupEditorModalProps> = ({ isOpen, onClose, muscleGroup }) => {
  const { exerciseList, isOnline } = useAppState();
  const { updateMuscleGroupInfo } = useAppDispatch();
  const [localData, setLocalData] = useState<MuscleGroupInfo>(muscleGroup);
  const [isBgModalOpen, setIsBgModalOpen] = useState(false);
  const [isAddingExercise, setIsAddingExercise] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    setLocalData(muscleGroup);
  }, [muscleGroup]);

  const handleChange = <K extends keyof MuscleGroupInfo>(key: K, value: MuscleGroupInfo[K]) => {
    setLocalData(prev => ({ ...prev, [key]: value }));
  };
  
  const handleNestedChange = (
      group: 'importance' | 'volumeRecommendations', 
      key: keyof MuscleGroupInfo['importance'] | keyof MuscleGroupInfo['volumeRecommendations'], 
      value: string
  ) => {
      setLocalData(prev => ({
          ...prev,
          [group]: {
              ...prev[group],
              [key]: value
          }
      }));
  };

  const handleSave = () => {
    updateMuscleGroupInfo(muscleGroup.id, localData);
    onClose();
  };

  const handleSaveBackground = (bg?: SessionBackground, style?: CoverStyle, image?: string) => {
    setLocalData(prev => ({ 
        ...prev, 
        coverImage: image || prev.coverImage,
        coverStyle: style
    }));
  };

  const handleAddExercise = (exerciseId: string) => {
      const newRecommended = [...(localData.recommendedExercises || [])];
      if (newRecommended.length < 5 && !newRecommended.includes(exerciseId)) {
          newRecommended.push(exerciseId);
          handleChange('recommendedExercises', newRecommended);
      }
      setIsAddingExercise(false);
      setSearchQuery('');
  }
  
  const handleRemoveExercise = (exerciseId: string) => {
      const newRecommended = (localData.recommendedExercises || []).filter(id => id !== exerciseId);
      handleChange('recommendedExercises', newRecommended);
  }

  const handleSetFavorite = (exerciseId: string) => {
      handleChange('favoriteExerciseId', exerciseId);
  }

  const filteredExercises = useMemo(() => {
    if (!searchQuery) return [];
    return exerciseList.filter(ex => ex.name.toLowerCase().includes(searchQuery.toLowerCase())).slice(0, 10);
  }, [searchQuery, exerciseList]);


  return (
    <>
        {isBgModalOpen && (
            <BackgroundEditorModal
                isOpen={isBgModalOpen}
                onClose={() => setIsBgModalOpen(false)}
                onSave={handleSaveBackground}
                initialCoverImage={localData.coverImage}
                initialCoverStyle={localData.coverStyle}
                previewTitle={localData.name}
                isOnline={isOnline}
            />
        )}
        <Modal isOpen={isOpen} onClose={onClose} title={`Editar: ${muscleGroup.name}`}>
        <div className="space-y-4 max-h-[70vh] overflow-y-auto p-1">
            
            <Button onClick={() => setIsBgModalOpen(true)} variant="secondary" className="w-full">
                <Wand2Icon size={16}/> Editar Portada
            </Button>

            <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Descripción</label>
            <textarea value={localData.description} onChange={e => handleChange('description', e.target.value)} rows={4} className="w-full" />
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">Importancia en Movimiento</label>
                    <textarea value={localData.importance.movement} onChange={e => handleNestedChange('importance', 'movement', e.target.value)} rows={3} className="w-full" />
                </div>
                <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">Importancia en Salud</label>
                    <textarea value={localData.importance.health} onChange={e => handleNestedChange('importance', 'health', e.target.value)} rows={3} className="w-full" />
                </div>
            </div>

            <div>
                <h4 className="font-semibold text-white mb-2">Volumen Semanal Recomendado</h4>
                <div className="grid grid-cols-3 gap-2">
                    <div>
                        <label className="block text-xs text-slate-400">MEV (Mínimo Efectivo)</label>
                        <input type="text" value={localData.volumeRecommendations.mev} onChange={e => handleNestedChange('volumeRecommendations', 'mev', e.target.value)} className="w-full" />
                    </div>
                    <div>
                        <label className="block text-xs text-slate-400">MAV (Máximo Adaptativo)</label>
                        <input type="text" value={localData.volumeRecommendations.mav} onChange={e => handleNestedChange('volumeRecommendations', 'mav', e.target.value)} className="w-full" />
                    </div>
                    <div>
                        <label className="block text-xs text-slate-400">MRV (Máximo Recuperable)</label>
                        <input type="text" value={localData.volumeRecommendations.mrv} onChange={e => handleNestedChange('volumeRecommendations', 'mrv', e.target.value)} className="w-full" />
                    </div>
                </div>
            </div>

            <div>
                <h4 className="font-semibold text-white mb-2">Ejercicios Destacados (Máx. 5)</h4>
                <div className="space-y-2">
                    {(localData.recommendedExercises || []).map(exId => {
                        const exercise = exerciseList.find(e => e.id === exId);
                        return (
                            <div key={exId} className="flex items-center justify-between bg-slate-800 p-2 rounded-md">
                                <span>{exercise?.name || 'Ejercicio no encontrado'}</span>
                                <div className="flex gap-2">
                                     <button onClick={() => handleSetFavorite(exId)} title="Marcar como favorito">
                                        <StarIcon size={16} filled={localData.favoriteExerciseId === exId} className={localData.favoriteExerciseId === exId ? 'text-yellow-400' : 'text-slate-500'}/>
                                    </button>
                                    <button onClick={() => handleRemoveExercise(exId)}>
                                        <TrashIcon size={16} className="text-slate-500 hover:text-red-400"/>
                                    </button>
                                </div>
                            </div>
                        )
                    })}
                </div>
                {isAddingExercise ? (
                    <div className="mt-2 relative">
                        <input
                            type="text"
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            placeholder="Buscar ejercicio para añadir..."
                            className="w-full"
                            autoFocus
                        />
                        {searchQuery && (
                            <div className="absolute z-10 w-full mt-1 bg-slate-900 border border-slate-700 rounded-lg max-h-40 overflow-y-auto">
                                {filteredExercises.map(ex => (
                                    <div key={ex.id} onClick={() => handleAddExercise(ex.id)} className="p-2 cursor-pointer hover:bg-primary-color">
                                        {ex.name}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                ) : (localData.recommendedExercises || []).length < 5 && (
                    <div className="flex gap-2 mt-2">
                         <Button onClick={() => setIsAddingExercise(true)} variant="secondary" className="!text-xs !py-1">
                            <PlusIcon size={14} /> Añadir Manualmente
                        </Button>
                        <Button variant="secondary" className="!text-xs !py-1" disabled={true}>
                            <SparklesIcon size={14} /> Sugerir con IA
                        </Button>
                    </div>
                )}
            </div>
            
            <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
            <Button variant="secondary" onClick={onClose}>Cancelar</Button>
            <Button onClick={handleSave}><SaveIcon /> Guardar Cambios</Button>
            </div>
        </div>
        </Modal>
    </>
  );
};

export default MuscleGroupEditorModal;
