// components/WikiHomeView.tsx
import React from 'react';
import { ChevronRightIcon, ActivityIcon, BrainIcon, DumbbellIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';

const WikiSectionCard: React.FC<{
  title: string;
  subtitle: string;
  accentColor: string;
  onClick: () => void;
  icon: React.ReactNode;
}> = ({ title, subtitle, accentColor, onClick, icon }) => (
  <div
    onClick={onClick}
    className="p-4 rounded-2xl border border-white/5 bg-slate-900/50 hover:bg-slate-800/70 cursor-pointer transition-all duration-200 group active:scale-[0.99]"
  >
    <div className={`w-10 h-10 rounded-xl flex items-center justify-center mb-3 ${accentColor}`}>
      {icon}
    </div>
    <h3 className="font-bold text-white text-base group-hover:text-white transition-colors">{title}</h3>
    <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>
    <ChevronRightIcon className="text-slate-500 group-hover:text-white mt-2 transition-colors" size={16} />
  </div>
);

const WikiHomeView: React.FC = () => {
  const { muscleHierarchy, exerciseList, movementPatternDatabase, navigateTo } = useAppContext();

  const bodyPartCategories = Object.keys(muscleHierarchy.bodyPartHierarchy || {}).sort((a, b) => a.localeCompare(b));
  const specialCategories = Object.keys(muscleHierarchy.specialCategories || {});

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Hero */}
      <div className="text-center py-4">
        <h1 className="text-3xl font-black uppercase tracking-tighter text-white">Wiki/Lab</h1>
        <p className="text-slate-400 text-sm mt-1">Base de conocimiento para el entrenamiento</p>
      </div>

      {/* 5 Secciones principales */}
      <div className="grid grid-cols-2 gap-3">
        <WikiSectionCard
          title="Ejercicios"
          subtitle={`${exerciseList.length} en base de datos`}
          accentColor="bg-sky-500/20 text-sky-400"
          onClick={() => navigateTo('exercise-database')}
          icon={<DumbbellIcon size={20} />}
        />
        <WikiSectionCard
          title="Anatomía"
          subtitle="Músculos, articulaciones y tendones"
          accentColor="bg-purple-500/20 text-purple-400"
          onClick={() => navigateTo('muscle-category', { categoryName: bodyPartCategories[0] || 'Pecho' })}
          icon={<BrainIcon size={20} />}
        />
        <WikiSectionCard
          title="Patrones"
          subtitle={`${movementPatternDatabase?.length || 0} patrones`}
          accentColor="bg-emerald-500/20 text-emerald-400"
          onClick={() => movementPatternDatabase?.length && navigateTo('movement-pattern-detail', { movementPatternId: movementPatternDatabase[0].id })}
          icon={<ActivityIcon size={20} />}
        />
      </div>

      {/* Grupos Funcionales */}
      {specialCategories.length > 0 && (
        <div>
          <h2 className="text-lg font-bold text-white mb-3 flex items-center gap-2">
            <ActivityIcon className="text-sky-400" size={18} /> Grupos Funcionales
          </h2>
          <div className="grid grid-cols-2 gap-2">
            {specialCategories.map(cat => (
              <div
                key={cat}
                onClick={() => navigateTo('chain-detail', { chainId: cat })}
                className="p-3 rounded-xl bg-slate-900/50 border border-white/5 hover:border-sky-500/30 cursor-pointer transition-all"
              >
                <span className="font-semibold text-slate-200 text-sm">{cat}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Anatomía por categoría */}
      <div>
        <h2 className="text-lg font-bold text-white mb-3 flex items-center gap-2">
          <BrainIcon className="text-purple-400" size={18} /> Anatomía por zona
        </h2>
        <div className="space-y-2">
          {bodyPartCategories.map(cat => (
            <div
              key={cat}
              onClick={() => navigateTo('muscle-category', { categoryName: cat })}
              className="p-4 flex justify-between items-center rounded-xl bg-slate-900/30 border border-white/5 hover:border-purple-500/30 cursor-pointer"
            >
              <span className="font-semibold text-slate-200">{cat}</span>
              <ChevronRightIcon className="text-slate-500" size={18} />
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};

export default WikiHomeView;
