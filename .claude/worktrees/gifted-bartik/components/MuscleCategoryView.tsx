// components/MuscleCategoryView.tsx
import React, { useMemo, useState, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, PencilIcon, BrainIcon } from './icons';
import MuscleGroupEditorModal from './MuscleGroupEditorModal';
import { MuscleSubGroup } from '../types';
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
    return muscleGroupData.find((m) => m.id === mainMuscleId);
  }, [categoryName, muscleGroupData]);

  useEffect(() => {
    if (categoryInfo?.coverImage) {
      setCurrentBackgroundOverride({
        type: 'image',
        value: categoryInfo.coverImage,
        style: { blur: 24, brightness: 0.3 },
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
        initial={{ opacity: 0, x: -10 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ delay: index * 0.05 }}
        onClick={() => navigateTo('muscle-group-detail', { muscleGroupId })}
        className="p-5 flex justify-between items-center cursor-pointer rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white transition-all active:scale-[0.99] group shadow-sm"
      >
        <div className="flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-[#F3EDF7] flex items-center justify-center text-purple-600 group-hover:bg-purple-600 group-hover:text-white transition-all">
            <BrainIcon size={18} />
          </div>
          <h2 className="text-lg font-bold text-[#1D1B20] group-hover:text-purple-600 transition-colors tracking-tight">
            {name}
          </h2>
        </div>
        <div className="w-8 h-8 rounded-full flex items-center justify-center text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all">
          <ChevronRightIcon size={20} />
        </div>
      </motion.div>
    );
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#FDFCFE] overflow-x-hidden relative pb-32">
      {isEditorOpen && categoryInfo && (
        <MuscleGroupEditorModal isOpen={isEditorOpen} onClose={() => setIsEditorOpen(false)} muscleGroup={categoryInfo} />
      )}

      {/* Header */}
      <header className="relative pt-12 pb-8 px-6">
        <div className="absolute top-4 right-6 flex gap-2 z-20">
          {categoryInfo && (
            <button
              onClick={() => setIsEditorOpen(true)}
              className="px-4 py-2 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] text-[9px] font-black uppercase tracking-widest text-[#49454F] hover:bg-white transition-colors flex items-center gap-2 shadow-sm"
            >
              <PencilIcon size={12} /> Info
            </button>
          )}
          <button
            onClick={() => openMuscleListEditor(categoryName, 'bodyPart')}
            className="px-4 py-2 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] text-[9px] font-black uppercase tracking-widest text-[#49454F] hover:bg-white transition-colors flex items-center gap-2 shadow-sm"
          >
            <PencilIcon size={12} /> Lista
          </button>
        </div>
        <div className="relative z-10 mt-8">
          <span className="text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 mb-2 block">
            Zona Anatómica
          </span>
          <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95]">{categoryName}</h1>
        </div>
      </header>

      {/* Category Info Card */}
      {categoryInfo && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="px-6 mb-6"
        >
          <div className="p-6 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] shadow-sm">
            <p className="text-sm text-[#49454F] opacity-70 leading-relaxed mb-4">{categoryInfo.description}</p>
            <div className="px-4 py-3 rounded-2xl bg-[#ECE6F0] border border-black/[0.03]">
              <p className="text-[10px] font-medium text-[#1D1B20] leading-snug">
                <span className="font-black text-primary uppercase tracking-widest block mb-1">Biomecánica</span>
                {categoryInfo.importance.movement}
              </p>
            </div>
          </div>
        </motion.div>
      )}

      {/* Muscle Groups List */}
      <div className="relative z-10 px-6 space-y-3">
        {muscleGroups.map((muscle, idx) => renderMuscleItem(muscle, idx))}
      </div>
    </div>
  );
};

export default MuscleCategoryView;
