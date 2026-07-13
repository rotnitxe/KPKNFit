// components/WikiHomeView.tsx
import React, { useMemo, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import {
  ChevronRightIcon,
  ActivityIcon,
  BrainIcon,
  DumbbellIcon,
  SearchIcon,
  SparklesIcon,
  ClipboardListIcon,
  RulerIcon,
} from './icons';
import { motion } from 'framer-motion';

interface WikiSectionCardProps {
  title: string;
  subtitle: string;
  accentColor: string;
  onClick: () => void;
  icon: React.ReactNode;
  delay?: number;
  count?: number;
}

const WikiSectionCard: React.FC<WikiSectionCardProps> = ({
  title,
  subtitle,
  accentColor,
  onClick,
  icon,
  delay = 0,
  count,
}) => (
  <motion.div
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ delay, duration: 0.5, ease: 'easeOut' }}
    onClick={onClick}
    className="relative p-5 rounded-[32px] bg-white/70 backdrop-blur-xl border border-black/[0.03] hover:bg-white cursor-pointer transition-all duration-300 group active:scale-[0.98] shadow-sm overflow-hidden"
  >
    <div className="absolute -top-10 -right-10 w-32 h-32 bg-gradient-to-br from-primary/10 to-transparent rounded-full blur-3xl group-hover:from-primary/20 transition-colors" />
    <div className="relative z-10">
      <div className={`w-14 h-14 rounded-2xl flex items-center justify-center mb-4 ${accentColor} shadow-sm`}>
        {icon}
      </div>
      <div className="flex flex-col gap-1">
        <h3 className="font-black text-[#1D1B20] text-lg tracking-tight leading-tight group-hover:text-primary transition-colors">
          {title}
        </h3>
        <p className="text-[10px] font-black uppercase tracking-[0.15em] text-[#49454F] opacity-50">
          {subtitle}
        </p>
        {count !== undefined && (
          <p className="text-[9px] font-bold text-[#1D1B20] opacity-40 mt-1">
            {count} elementos
          </p>
        )}
      </div>
      <div className="mt-5 flex items-center gap-2 text-[9px] font-black uppercase tracking-widest text-[#49454F] opacity-40 group-hover:opacity-60 transition-opacity">
        Explorar <ChevronRightIcon size={12} />
      </div>
    </div>
  </motion.div>
);

interface ListItemProps {
  title: string;
  subtitle?: string;
  onClick: () => void;
  icon?: React.ReactNode;
  delay?: number;
  accent?: 'sky' | 'purple' | 'emerald' | 'amber';
}

const ListItem: React.FC<ListItemProps> = ({
  title,
  subtitle,
  onClick,
  icon,
  delay = 0,
  accent = 'sky',
}) => {
  const accentClasses = {
    sky: 'bg-[#ECE6F0] text-[#49454F]',
    purple: 'bg-[#F3EDF7] text-[#49454F]',
    emerald: 'bg-[#E8F5E9] text-[#49454F]',
    amber: 'bg-[#FFF8E1] text-[#49454F]',
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay, duration: 0.3 }}
      onClick={onClick}
      className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all active:scale-[0.99] group"
    >
      <div className="flex items-center gap-4">
        {icon && (
          <div className={`w-10 h-10 rounded-full flex items-center justify-center ${accentClasses[accent]}`}>
            {icon}
          </div>
        )}
        <div className="flex flex-col">
          <span className="font-bold text-[#1D1B20] tracking-tight">{title}</span>
          {subtitle && (
            <span className="text-[10px] font-medium text-[#49454F] opacity-50 mt-0.5">
              {subtitle}
            </span>
          )}
        </div>
      </div>
      <div className="w-8 h-8 rounded-full flex items-center justify-center text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all">
        <ChevronRightIcon size={18} />
      </div>
    </motion.div>
  );
};

const WikiHomeView: React.FC = () => {
  const { muscleHierarchy, exerciseList, movementPatternDatabase, jointDatabase, tendonDatabase } = useAppState();
  const { navigateTo, handleBack } = useAppDispatch();
  const [searchQuery, setSearchQuery] = useState('');

  const bodyPartCategories = useMemo(
    () => Object.keys(muscleHierarchy.bodyPartHierarchy || {}).sort((a, b) => a.localeCompare(b)),
    [muscleHierarchy]
  );

  const specialCategories = useMemo(
    () => Object.keys(muscleHierarchy.specialCategories || {}),
    [muscleHierarchy]
  );

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigateTo('kpkn', { searchQuery: searchQuery.trim() });
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#FDFCFE] overflow-x-hidden relative pb-32">
      {/* ═══ Background Flow ═══ */}
      <div className="absolute top-0 left-0 right-0 h-[500px] pointer-events-none overflow-hidden z-0">
        <div className="absolute -top-[50px] -left-[100px] w-[400px] h-[400px] bg-gradient-to-br from-primary/5 to-transparent rounded-full blur-[100px]" />
        <div className="absolute top-[100px] -right-[50px] w-[350px] h-[350px] bg-gradient-to-bl from-purple-500/5 to-transparent rounded-full blur-[80px]" />
        <div className="absolute top-[300px] left-1/2 -translate-x-1/2 w-[500px] h-[500px] bg-gradient-to-t from-emerald-500/5 to-transparent rounded-full blur-[120px]" />
      </div>

      {/* ═══ Header ═══ */}
      <header className="relative z-10 px-6 pt-12 pb-8 flex flex-col gap-6">
        <div className="flex justify-between items-center">
          <button
            onClick={handleBack}
            className="w-10 h-10 rounded-full bg-white/70 backdrop-blur-xl border border-black/[0.03] flex items-center justify-center text-[#49454F] hover:bg-white transition-colors shadow-sm"
          >
            <ChevronRightIcon size={20} className="rotate-180" />
          </button>
          <div className="px-3 py-1.5 rounded-full bg-[#ECE6F0] border border-black/[0.02] backdrop-blur-md flex items-center gap-2">
            <SparklesIcon size={12} className="text-primary" />
            <span className="text-[8px] font-black uppercase tracking-widest text-[#49454F] opacity-60">
              Auge Knowledge Lab
            </span>
          </div>
          <div className="w-10" />
        </div>
        <div>
          <h1 className="text-[40px] font-black text-[#1D1B20] tracking-tighter leading-[0.95] mb-3">
            Wiki<span className="text-primary">Lab</span>
          </h1>
          <p className="text-sm font-medium text-[#49454F] opacity-60 max-w-[300px] leading-relaxed">
            La enciclopedia definitiva del entrenamiento, biomecánica y anatomía aplicada.
          </p>
        </div>

        {/* Search Bar */}
        <form onSubmit={handleSearch} className="relative">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar ejercicios, músculos, patrones..."
            className="w-full bg-white/80 backdrop-blur-xl border border-black/[0.05] rounded-2xl pl-12 pr-4 py-4 text-[#1D1B20] placeholder:text-[#49454F] opacity-50 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/30 text-sm font-medium"
          />
          <SearchIcon className="absolute left-4 top-1/2 -translate-y-1/2 text-[#49454F] opacity-40" size={20} />
        </form>
      </header>

      {/* ═══ Main Sections ═══ */}
      <section className="relative z-10 px-6 grid grid-cols-2 gap-3">
        <WikiSectionCard
          title="Ejercicios"
          subtitle={`${exerciseList.length} Movimientos`}
          accentColor="bg-[#ECE6F0] text-primary"
          onClick={() => navigateTo('exercise-database')}
          icon={<DumbbellIcon size={24} />}
          delay={0.1}
          count={exerciseList.length}
        />
        <WikiSectionCard
          title="Anatomía"
          subtitle="Atlas Humano"
          accentColor="bg-[#F3EDF7] text-purple-600"
          onClick={() => navigateTo('muscle-category', { categoryName: bodyPartCategories[0] || 'Pecho' })}
          icon={<BrainIcon size={24} />}
          delay={0.15}
        />
        <WikiSectionCard
          title="Patrones"
          subtitle={`${movementPatternDatabase?.length || 0} Patrones`}
          accentColor="bg-[#E8F5E9] text-emerald-600"
          onClick={() =>
            movementPatternDatabase?.length &&
            navigateTo('movement-pattern-detail', { movementPatternId: movementPatternDatabase[0].id })
          }
          icon={<ActivityIcon size={24} />}
          delay={0.2}
          count={movementPatternDatabase?.length || 0}
        />
        <WikiSectionCard
          title="Listas"
          subtitle="Colecciones"
          accentColor="bg-[#FFF8E1] text-amber-600"
          onClick={() => navigateTo('kpkn', { tab: 'listas' })}
          icon={<ClipboardListIcon size={24} />}
          delay={0.25}
        />
        <WikiSectionCard
          title="Palitos Biomecánicos"
          subtitle="Leverages & IK"
          accentColor="bg-[#E0F2FE] text-sky-700"
          onClick={() => navigateTo('wikilab-biomechanics')}
          icon={<RulerIcon size={24} />}
          delay={0.3}
        />
      </section>

      {/* ═══ Grupos Funcionales ═══ */}
      {specialCategories.length > 0 && (
        <section className="relative z-10 px-6 mt-8">
          <div className="flex items-baseline justify-between mb-4">
            <h2 className="text-[20px] font-black text-[#1D1B20] tracking-tight uppercase">
              Cadenas <span className="text-primary">Cinéticas</span>
            </h2>
            <span className="text-[9px] font-black text-[#49454F] opacity-40 uppercase tracking-widest">
              {specialCategories.length} Grupos
            </span>
          </div>
          <div className="grid grid-cols-2 gap-3">
            {specialCategories.map((cat, idx) => (
              <motion.div
                key={cat}
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.3 + idx * 0.05 }}
                onClick={() => navigateTo('chain-detail', { chainId: cat })}
                className="p-4 rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all active:scale-95 flex flex-col items-start gap-3 group shadow-sm"
              >
                <div className="w-8 h-8 rounded-xl bg-[#ECE6F0] flex items-center justify-center text-primary group-hover:bg-primary group-hover:text-white transition-all">
                  <ActivityIcon size={14} />
                </div>
                <span className="font-bold text-[#1D1B20] text-sm tracking-tight leading-tight">{cat}</span>
              </motion.div>
            ))}
          </div>
        </section>
      )}

      {/* ═══ Anatomía por zona ═══ */}
      <section className="relative z-10 px-6 mt-8">
        <div className="flex items-baseline justify-between mb-4">
          <h2 className="text-[20px] font-black text-[#1D1B20] tracking-tight uppercase">
            Atlas <span className="text-purple-600">Anatómico</span>
          </h2>
          <span className="text-[9px] font-black text-[#49454F] opacity-40 uppercase tracking-widest">
            {bodyPartCategories.length} Zonas
          </span>
        </div>
        <div className="space-y-2">
          {bodyPartCategories.map((cat, idx) => (
            <motion.div
              key={cat}
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.4 + idx * 0.05 }}
              onClick={() => navigateTo('muscle-category', { categoryName: cat })}
              className="p-4 flex justify-between items-center rounded-[24px] bg-white/70 backdrop-blur-xl border border-black/[0.02] hover:bg-white cursor-pointer transition-all active:scale-[0.99] group shadow-sm"
            >
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-[#F3EDF7] flex items-center justify-center text-purple-600 group-hover:bg-purple-600 group-hover:text-white transition-all">
                  <BrainIcon size={18} />
                </div>
                <span className="font-bold text-[#1D1B20] tracking-tight">{cat}</span>
              </div>
              <div className="w-8 h-8 rounded-full flex items-center justify-center text-[#49454F] opacity-20 group-hover:opacity-40 group-hover:translate-x-1 transition-all">
                <ChevronRightIcon size={20} />
              </div>
            </motion.div>
          ))}
        </div>
      </section>

      {/* ═══ Articulaciones y Tendones ═══ */}
      <section className="relative z-10 px-6 mt-8">
        <div className="flex items-baseline justify-between mb-4">
          <h2 className="text-[20px] font-black text-[#1D1B20] tracking-tight uppercase">
            Estructuras <span className="text-sky-600">Relacionadas</span>
          </h2>
          <span className="text-[9px] font-black text-[#49454F] opacity-40 uppercase tracking-widest">
            {(jointDatabase?.length || 0) + (tendonDatabase?.length || 0)} Elementos
          </span>
        </div>
        <div className="space-y-2">
          {(jointDatabase || []).slice(0, 6).map((j, idx) => (
            <ListItem
              key={j.id}
              title={j.name?.split('(')[0]?.trim() || j.name || ''}
              subtitle="Articulación"
              onClick={() => navigateTo('joint-detail', { jointId: j.id })}
              delay={0.5 + idx * 0.03}
              accent="sky"
              icon={<ActivityIcon size={16} />}
            />
          ))}
          {(tendonDatabase || []).slice(0, 4).map((t, idx) => (
            <ListItem
              key={t.id}
              title={t.name || ''}
              subtitle="Tendón"
              onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
              delay={0.5 + ((jointDatabase?.length || 0) + idx) * 0.03}
              accent="amber"
              icon={<SparklesIcon size={16} />}
            />
          ))}
        </div>
      </section>
    </div>
  );
};

export default WikiHomeView;
