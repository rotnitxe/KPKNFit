// components/WikiHomeView.tsx
import React from 'react';
import { ChevronRightIcon, TrophyIcon, ActivityIcon, BrainIcon, DumbbellIcon } from './icons';
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
  const { muscleHierarchy, exerciseList, jointDatabase, tendonDatabase, movementPatternDatabase, navigateTo } = useAppContext();

  const bodyPartCategories = Object.keys(muscleHierarchy.bodyPartHierarchy || {}).sort((a, b) => a.localeCompare(b));
  const specialCategories = Object.keys(muscleHierarchy.specialCategories || {});
  const hallOfFameExercises = exerciseList.filter(ex => ex.isHallOfFame);

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
          subtitle="Músculos y grupos"
          accentColor="bg-purple-500/20 text-purple-400"
          onClick={() => navigateTo('muscle-category', { categoryName: bodyPartCategories[0] || 'Pecho' })}
          icon={<BrainIcon size={20} />}
        />
        <WikiSectionCard
          title="Articulaciones"
          subtitle={`${jointDatabase?.length || 0} articulaciones`}
          accentColor="bg-cyan-500/20 text-cyan-400"
          onClick={() => jointDatabase?.length && navigateTo('joint-detail', { jointId: jointDatabase[0].id })}
          icon={<ActivityIcon size={20} />}
        />
        <WikiSectionCard
          title="Tendones"
          subtitle={`${tendonDatabase?.length || 0} tendones`}
          accentColor="bg-amber-500/20 text-amber-400"
          onClick={() => tendonDatabase?.length && navigateTo('tendon-detail', { tendonId: tendonDatabase[0].id })}
          icon={<ActivityIcon size={20} />}
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

      {/* Articulaciones */}
      {(jointDatabase?.length || 0) > 0 && (
        <div>
          <h2 className="text-lg font-bold text-white mb-3 flex items-center gap-2">
            <span className="text-cyan-400">●</span> Articulaciones
          </h2>
          <div className="flex overflow-x-auto gap-2 pb-2 hide-scrollbar">
            {jointDatabase.slice(0, 8).map(j => (
              <button
                key={j.id}
                onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                className="flex-shrink-0 px-3 py-2 rounded-lg bg-slate-900/60 border border-cyan-500/20 hover:border-cyan-500/50 text-left transition-all"
              >
                <span className="text-sm font-semibold text-white block truncate max-w-[120px]">{j.name.split('(')[0].trim()}</span>
              </button>
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

      {/* Hall of Fame */}
      {hallOfFameExercises.length > 0 && (
        <div>
          <div className="flex justify-between items-center mb-3">
            <h2 className="text-lg font-bold text-yellow-400 flex items-center gap-2">
              <TrophyIcon size={18} /> Hall Of Fame
            </h2>
            <button onClick={() => navigateTo('hall-of-fame')} className="text-xs font-bold text-slate-500 hover:text-white">
              Ver todo
            </button>
          </div>
          <div className="flex overflow-x-auto snap-x snap-mandatory gap-3 pb-2 -mx-4 px-4 hide-scrollbar">
            {hallOfFameExercises.slice(0, 6).map(ex => (
              <div
                key={ex.id}
                onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                className="snap-center flex-shrink-0 w-36 h-28 bg-gradient-to-br from-slate-900 to-black rounded-xl p-3 flex flex-col justify-between cursor-pointer border border-yellow-900/30 hover:border-yellow-500/50 transition-all"
              >
                <h3 className="font-bold text-white text-sm leading-tight">{ex.name}</h3>
                <p className="text-[10px] text-yellow-500/80 font-bold uppercase">
                  {ex.involvedMuscles?.find(m => m.role === 'primary')?.muscle}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default WikiHomeView;
