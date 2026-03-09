// components/MuscleCategoryView.tsx
import React, { useMemo, useState, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, PencilIcon } from './icons';
import MuscleGroupEditorModal from './MuscleGroupEditorModal';
import { MuscleSubGroup } from '../types';
import Button from './ui/Button';
import { motion } from 'framer-motion';

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

    useEffect(() => {
        if (categoryInfo?.coverImage) {
            setCurrentBackgroundOverride({
                type: 'image',
                value: categoryInfo.coverImage,
                style: { blur: 24, brightness: 0.3 }
            });
        } else {
             setCurrentBackgroundOverride(undefined);
        }
        return () => setCurrentBackgroundOverride(undefined);
    }, [categoryInfo, setCurrentBackgroundOverride]);

    const muscleGroups = useMemo(() => {
        return muscleHierarchy.bodyPartHierarchy[categoryName] || [];
    }, [categoryName, muscleHierarchy]);

    const renderMuscleItem = (muscle: MuscleSubGroup, index: number) => {
        const isString = typeof muscle === 'string';
        const name = isString ? muscle : Object.keys(muscle)[0];
        const muscleGroupId = name.toLowerCase().replace(/\s+/g, '-').replace(/[()]/g, '');

        return (
            <motion.div 
                key={name} 
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
                onClick={() => navigateTo('muscle-group-detail', { muscleGroupId })} 
                className="p-5 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/[0.03] border border-white/5 hover:bg-white/[0.08] hover:border-white/10 transition-all active:scale-[0.99] group"
            >
                <h2 className="text-lg font-bold text-white/90 group-hover:text-purple-400 transition-colors tracking-tight">{name}</h2>
                <div className="w-8 h-8 rounded-full flex items-center justify-center text-white/20 group-hover:text-purple-400 group-hover:translate-x-1 transition-all">
                    <ChevronRightIcon size={20} />
                </div>
            </motion.div>
        );
    };

    return (
        <div className="min-h-screen flex flex-col bg-transparent overflow-x-hidden relative pb-32">
             {isEditorOpen && categoryInfo && (
                <MuscleGroupEditorModal
                    isOpen={isEditorOpen}
                    onClose={() => setIsEditorOpen(false)}
                    muscleGroup={categoryInfo}
                />
            )}
            
            <header className="relative pt-12 pb-8 px-6">
                 <div className="absolute top-4 right-6 flex gap-2 z-20">
                    {categoryInfo && (
                        <button onClick={() => setIsEditorOpen(true)} className="px-4 py-2 rounded-full bg-white/10 backdrop-blur-md border border-white/10 text-[10px] font-black uppercase tracking-widest text-white hover:bg-white/20 transition-colors flex items-center gap-2">
                            <PencilIcon size={12}/> Info
                        </button>
                    )}
                     <button onClick={() => openMuscleListEditor(categoryName, 'bodyPart')} className="px-4 py-2 rounded-full bg-white/10 backdrop-blur-md border border-white/10 text-[10px] font-black uppercase tracking-widest text-white hover:bg-white/20 transition-colors flex items-center gap-2">
                         <PencilIcon size={12}/> Lista
                     </button>
                </div>
                <div className="relative z-10 mt-8">
                    <span className="text-[10px] font-black uppercase tracking-widest text-purple-400 mb-2 block">Zona Anatómica</span>
                    <h1 className="text-5xl font-black text-white tracking-tighter leading-none">{categoryName}</h1>
                </div>
            </header>
            
            <div className="relative z-10 px-6 space-y-4">
                 {categoryInfo && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="p-6 rounded-[32px] bg-white/5 backdrop-blur-2xl border border-white/10 mb-8 shadow-2xl">
                        <p className="text-sm text-white/70 leading-relaxed mb-4">{categoryInfo.description}</p>
                        <div className="px-4 py-3 rounded-2xl bg-purple-500/10 border border-purple-500/20">
                            <p className="text-[11px] font-medium text-purple-200 leading-snug"><span className="font-black text-purple-400 uppercase tracking-widest block mb-1">Biomecánica</span>{categoryInfo.importance.movement}</p>
                        </div>
                    </motion.div>
                 )}

                <div className="space-y-3">
                    {muscleGroups.map((muscle, idx) => renderMuscleItem(muscle, idx))}
                </div>
            </div>
        </div>
    );
};

export default MuscleCategoryView;