// components/MuscleCategoryView.tsx
import React, { useMemo, useEffect, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, PencilIcon } from './icons';
import MuscleGroupEditorModal from './MuscleGroupEditorModal';
import { MuscleGroupInfo, MuscleSubGroup } from '../types';
import Button from './ui/Button';

interface MuscleCategoryViewProps {
    categoryName: string;
}

const MuscleCategoryView: React.FC<MuscleCategoryViewProps> = ({ categoryName }) => {
    const { muscleHierarchy, muscleGroupData } = useAppState();
    const { navigateTo, setCurrentBackgroundOverride, openMuscleListEditor } = useAppDispatch();
    const [isEditorOpen, setIsEditorOpen] = useState(false);

    const categoryInfo = useMemo(() => {
        const mainMuscleId = categoryName.toLowerCase().replace(/\s+/g, '-');
        return muscleGroupData.find(m => m.id === mainMuscleId);
    }, [categoryName, muscleGroupData]);

    React.useEffect(() => {
        if (categoryInfo?.coverImage) {
            setCurrentBackgroundOverride({
                type: 'image',
                value: categoryInfo.coverImage,
                style: { blur: 16, brightness: 0.4 }
            });
        } else {
             setCurrentBackgroundOverride(undefined);
        }
        return () => setCurrentBackgroundOverride(undefined);
    }, [categoryInfo, setCurrentBackgroundOverride]);

    const muscleGroups = useMemo(() => {
        return muscleHierarchy.bodyPartHierarchy[categoryName] || [];
    }, [categoryName, muscleHierarchy]);

    const renderMuscleItem = (muscle: MuscleSubGroup) => {
        if (typeof muscle === 'string') {
            const muscleGroupId = muscle.toLowerCase().replace(/\s+/g, '-');
            return (
                 <div key={muscle} onClick={() => navigateTo('muscle-group-detail', { muscleGroupId })} className="p-4 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors glass-card">
                    <h2 className="text-xl font-bold text-primary-color">{muscle}</h2>
                    <ChevronRightIcon className="text-slate-500" />
                </div>
            );
        } else {
            // Músculo con porciones/cabezas: solo mostramos el padre; las porciones están en su página dedicada
            const parentMuscle = Object.keys(muscle)[0];
            const parentMuscleGroupId = parentMuscle.toLowerCase().replace(/\s+/g, '-').replace(/[()]/g, '');

            return (
                <div key={parentMuscle} onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: parentMuscleGroupId })} className="p-4 flex justify-between items-center cursor-pointer list-none hover:bg-slate-800/50 rounded-lg transition-colors glass-card">
                    <h2 className="text-xl font-bold text-primary-color">{parentMuscle}</h2>
                    <ChevronRightIcon className="text-slate-500" />
                </div>
            );
        }
    };

    return (
        <div className="pb-20 animate-fade-in">
             {isEditorOpen && categoryInfo && (
                <MuscleGroupEditorModal
                    isOpen={isEditorOpen}
                    onClose={() => setIsEditorOpen(false)}
                    muscleGroup={categoryInfo}
                />
            )}
            <header className="relative h-48 -mx-4">
                {categoryInfo?.coverImage && <img src={categoryInfo.coverImage} alt={categoryName} className="w-full h-full object-cover" />}
                <div className="absolute inset-0 bg-gradient-to-t from-black via-black/70 to-transparent" />
                 <div className="absolute top-2 right-2 flex gap-2">
                    {categoryInfo && <Button onClick={() => setIsEditorOpen(true)} variant="secondary" className="!text-xs !py-1"><PencilIcon size={14}/> Editar Página</Button>}
                     <Button onClick={() => openMuscleListEditor(categoryName, 'bodyPart')} variant="secondary" className="!text-xs !py-1"><PencilIcon size={14}/> Editar Grupo</Button>
                </div>
                <div className="absolute bottom-4 left-4 right-4">
                    <h1 className="text-4xl font-bold text-white">{categoryName}</h1>
                </div>
            </header>
            
            <div className="space-y-4 mt-6">
                 {categoryInfo && (
                    <div className="glass-card-nested p-4 mb-6">
                        <p className="text-sm text-slate-300 mb-2">{categoryInfo.description}</p>
                        <p className="text-sm text-slate-400 italic">{categoryInfo.importance.movement}</p>
                    </div>
                 )}

                {muscleGroups.map(muscle => renderMuscleItem(muscle))}
            </div>
        </div>
    );
};

export default MuscleCategoryView;