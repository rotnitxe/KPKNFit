// components/MuscleListEditorModal.tsx
import React, { useState, useEffect, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SaveIcon, TrashIcon, PlusIcon } from './icons';
import { MuscleSubGroup } from '../types';

interface MuscleListEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  categoryInfo: { name: string; type: 'bodyPart' | 'special' };
}

const MuscleListEditorModal: React.FC<MuscleListEditorModalProps> = ({ isOpen, onClose, categoryInfo }) => {
    const { muscleHierarchy, muscleGroupData } = useAppState();
    const { updateCategoryMuscles } = useAppDispatch();
    
    const [currentMuscles, setCurrentMuscles] = useState<string[]>([]);
    const [searchQuery, setSearchQuery] = useState('');

    useEffect(() => {
        if (isOpen) {
            let muscles: string[] = [];
            if (categoryInfo.type === 'bodyPart') {
                const groups = muscleHierarchy.bodyPartHierarchy[categoryInfo.name] || [];
                muscles = groups.map(g => typeof g === 'string' ? g : Object.keys(g)[0]);
            } else {
                muscles = muscleHierarchy.specialCategories[categoryInfo.name] || [];
            }
            setCurrentMuscles(muscles);
        }
    }, [isOpen, categoryInfo, muscleHierarchy]);

    const handleAddMuscle = (muscleName: string) => {
        if (!currentMuscles.includes(muscleName)) {
            setCurrentMuscles(prev => [...prev, muscleName].sort());
        }
        setSearchQuery('');
    };

    const handleRemoveMuscle = (muscleName: string) => {
        setCurrentMuscles(prev => prev.filter(m => m !== muscleName));
    };
    
    const handleSave = () => {
        updateCategoryMuscles(categoryInfo.name, currentMuscles, categoryInfo.type);
        onClose();
    };
    
    const allMuscles = useMemo(() => {
        return muscleGroupData.map(m => m.name).sort();
    }, [muscleGroupData]);

    const availableMuscles = useMemo(() => {
        const filtered = allMuscles.filter(m => !currentMuscles.includes(m));
        if (!searchQuery) return filtered;
        return filtered.filter(m => m.toLowerCase().includes(searchQuery.toLowerCase()));
    }, [allMuscles, currentMuscles, searchQuery]);

    return (
        <Modal isOpen={isOpen} onClose={onClose} title={`Editar Músculos de: ${categoryInfo.name}`}>
            <div className="space-y-4 max-h-[70vh] flex flex-col">
                <div className="flex-grow space-y-2 overflow-y-auto pr-2">
                    <h3 className="text-lg font-semibold text-white">Músculos Actuales</h3>
                    {currentMuscles.map(muscle => (
                        <div key={muscle} className="flex justify-between items-center bg-slate-800 p-2 rounded-md">
                            <span>{muscle}</span>
                            <button onClick={() => handleRemoveMuscle(muscle)}>
                                <TrashIcon size={16} className="text-slate-500 hover:text-red-400"/>
                            </button>
                        </div>
                    ))}
                </div>

                <div className="flex-shrink-0 pt-4 border-t border-slate-700 space-y-2">
                    <h3 className="text-lg font-semibold text-white">Añadir Músculo</h3>
                     <input
                        type="text"
                        value={searchQuery}
                        onChange={e => setSearchQuery(e.target.value)}
                        placeholder="Buscar músculo para añadir..."
                        className="w-full"
                    />
                    <div className="max-h-40 overflow-y-auto space-y-1">
                        {availableMuscles.slice(0, 15).map(muscle => (
                            <button key={muscle} onClick={() => handleAddMuscle(muscle)} className="w-full flex items-center gap-2 p-2 text-left rounded-md hover:bg-slate-700">
                                <PlusIcon size={14}/> {muscle}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="flex justify-end gap-2 pt-4 border-t border-slate-700">
                    <Button variant="secondary" onClick={onClose}>Cancelar</Button>
                    <Button onClick={handleSave}><SaveIcon /> Guardar Cambios</Button>
                </div>
            </div>
        </Modal>
    );
};

export default MuscleListEditorModal;