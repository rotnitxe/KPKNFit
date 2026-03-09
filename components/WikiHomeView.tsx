// components/WikiHomeView.tsx
import React from 'react';
import { ChevronRightIcon, ActivityIcon, BrainIcon, DumbbellIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';

import React, { useMemo } from 'react';
import { ChevronRightIcon, ActivityIcon, BrainIcon, DumbbellIcon, SearchIcon, SparklesIcon } from './icons';
import { useAppState } from '../contexts/AppContext';
import { motion } from 'framer-motion';

const WikiSectionCard: React.FC<{
  title: string;
  subtitle: string;
  accentColor: string;
  onClick: () => void;
  icon: React.ReactNode;
  delay?: number;
}> = ({ title, subtitle, accentColor, onClick, icon, delay = 0 }) => (
  <motion.div
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ delay, duration: 0.5, ease: "easeOut" }}
    onClick={onClick}
    className="relative p-6 rounded-[32px] border border-white/10 bg-white/5 backdrop-blur-2xl hover:bg-white/10 cursor-pointer transition-all duration-300 group active:scale-[0.98] shadow-2xl overflow-hidden"
  >
    <div className="absolute -top-10 -right-10 w-32 h-32 bg-white/5 rounded-full blur-3xl group-hover:bg-white/10 transition-colors" />
    <div className={`w-14 h-14 rounded-2xl flex items-center justify-center mb-5 ${accentColor} shadow-lg shadow-black/5`}>
      {icon}
    </div>
    <div className="flex flex-col gap-1">
      <h3 className="font-black text-white text-xl tracking-tight leading-tight group-hover:text-primary transition-colors">{title}</h3>
      <p className="text-[11px] font-black uppercase tracking-[0.15em] text-white/40">{subtitle}</p>
    </div>
    <div className="mt-6 flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-white/20 group-hover:text-white/60 transition-colors">
        Explorar <ChevronRightIcon size={14} />
    </div>
  </motion.div>
);

const WikiHomeView: React.FC = () => {
  const { muscleHierarchy, exerciseList, movementPatternDatabase, navigateTo } = useAppState();

  const bodyPartCategories = useMemo(() => 
    Object.keys(muscleHierarchy.bodyPartHierarchy || {}).sort((a, b) => a.localeCompare(b))
  , [muscleHierarchy]);

  const specialCategories = useMemo(() => 
    Object.keys(muscleHierarchy.specialCategories || {})
  , [muscleHierarchy]);

  return (
    <div className="min-h-screen flex flex-col bg-transparent overflow-x-hidden relative pb-32">
      {/* ─── Background Flow ─── */}
      <div className="absolute top-0 left-0 right-0 h-[600px] pointer-events-none overflow-hidden z-0">
        <div className="absolute -top-[100px] -left-[100px] w-[400px] h-[400px] bg-sky-500/20 rounded-full blur-[120px] animate-pulse" />
        <div className="absolute top-[100px] -right-[50px] w-[350px] h-[350px] bg-purple-500/20 rounded-full blur-[100px]" style={{ animationDelay: '2s' }} />
        <div className="absolute top-[400px] left-1/2 -translate-x-1/2 w-[500px] h-[500px] bg-emerald-500/10 rounded-full blur-[150px]" />
      </div>

      {/* ─── Header ─── */}
      <header className="relative z-10 px-6 pt-12 pb-8 flex flex-col gap-6">
        <div className="flex justify-between items-center">
             <div className="px-3 py-1 rounded-full bg-white/5 border border-white/10 backdrop-blur-md flex items-center gap-2">
                <SparklesIcon size={12} className="text-sky-400" />
                <span className="text-[9px] font-black uppercase tracking-widest text-white/60">Auge Knowledge Lab</span>
             </div>
             <button className="w-10 h-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center text-white/40">
                <SearchIcon size={18} />
             </button>
        </div>
        <div>
          <h1 className="text-5xl font-black text-white tracking-tighter leading-[0.9] mb-3">
            WIKI<span className="text-sky-400">LAB</span>
          </h1>
          <p className="text-sm font-medium text-white/50 max-w-[280px] leading-relaxed">
            La enciclopedia definitiva del entrenamiento, biomecánica y anatomía aplicada.
          </p>
        </div>
      </header>

      {/* ─── Main Sections ─── */}
      <section className="relative z-10 px-6 grid grid-cols-1 gap-4">
        <div className="grid grid-cols-2 gap-4">
            <WikiSectionCard
              title="Ejercicios"
              subtitle={`${exerciseList.length} Movimientos`}
              accentColor="bg-sky-500 text-white shadow-sky-500/20"
              onClick={() => navigateTo('exercise-database')}
              icon={<DumbbellIcon size={24} />}
              delay={0.1}
            />
            <WikiSectionCard
              title="Anatomía"
              subtitle="Atlas Humano"
              accentColor="bg-purple-500 text-white shadow-purple-500/20"
              onClick={() => navigateTo('muscle-category', { categoryName: bodyPartCategories[0] || 'Pecho' })}
              icon={<BrainIcon size={24} />}
              delay={0.2}
            />
        </div>
        <WikiSectionCard
          title="Patrones de Movimiento"
          subtitle={`${movementPatternDatabase?.length || 0} Patrones Biomecánicos`}
          accentColor="bg-emerald-500 text-white shadow-emerald-500/20"
          onClick={() => movementPatternDatabase?.length && navigateTo('movement-pattern-detail', { movementPatternId: movementPatternDatabase[0].id })}
          icon={<ActivityIcon size={24} />}
          delay={0.3}
        />
      </section>

      {/* ─── Grupos Funcionales ─── */}
      {specialCategories.length > 0 && (
        <section className="relative z-10 px-6 mt-12">
          <div className="flex items-baseline justify-between mb-6 px-1">
            <h2 className="text-xl font-black text-white tracking-tight uppercase">Cadenas <span className="text-sky-400">Cinéticas</span></h2>
            <span className="text-[10px] font-black text-white/20 uppercase tracking-widest">{specialCategories.length} Grupos</span>
          </div>
          <div className="grid grid-cols-2 gap-3">
            {specialCategories.map((cat, idx) => (
              <motion.div
                key={cat}
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.4 + (idx * 0.05) }}
                onClick={() => navigateTo('chain-detail', { chainId: cat })}
                className="p-5 rounded-[24px] bg-white/[0.03] border border-white/5 hover:bg-white/[0.08] hover:border-white/10 cursor-pointer transition-all active:scale-95 flex flex-col items-start gap-3 group"
              >
                <div className="w-8 h-8 rounded-xl bg-sky-500/10 flex items-center justify-center text-sky-400 group-hover:bg-sky-500 group-hover:text-white transition-all">
                    <ActivityIcon size={16} />
                </div>
                <span className="font-bold text-white/80 text-sm tracking-tight leading-tight">{cat}</span>
              </motion.div>
            ))}
          </div>
        </section>
      )}

      {/* ─── Anatomía por zona ─── */}
      <section className="relative z-10 px-6 mt-12">
        <div className="flex items-baseline justify-between mb-6 px-1">
          <h2 className="text-xl font-black text-white tracking-tight uppercase">Atlas <span className="text-purple-400">Anatómico</span></h2>
          <span className="text-[10px] font-black text-white/20 uppercase tracking-widest">{bodyPartCategories.length} Zonas</span>
        </div>
        <div className="space-y-3">
          {bodyPartCategories.map((cat, idx) => (
            <motion.div
              key={cat}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.5 + (idx * 0.05) }}
              onClick={() => navigateTo('muscle-category', { categoryName: cat })}
              className="p-5 flex justify-between items-center rounded-[24px] bg-white/[0.03] border border-white/5 hover:bg-white/[0.08] hover:border-white/10 cursor-pointer transition-all active:scale-[0.99] group"
            >
              <div className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-full bg-purple-500/10 flex items-center justify-center text-purple-400 group-hover:bg-purple-500 group-hover:text-white transition-all">
                    <BrainIcon size={18} />
                  </div>
                  <span className="font-bold text-white/80 tracking-tight">{cat}</span>
              </div>
              <div className="w-8 h-8 rounded-full flex items-center justify-center text-white/10 group-hover:text-white/60 group-hover:translate-x-1 transition-all">
                <ChevronRightIcon size={20} />
              </div>
            </motion.div>
          ))}
        </div>
      </section>

    </div>
  );
};

export default WikiHomeView;


export default WikiHomeView;
