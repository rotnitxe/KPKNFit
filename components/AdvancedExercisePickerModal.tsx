// components/AdvancedExercisePickerModal.tsx
// Modal compartido para seleccionar ejercicios (SessionEditor + WorkoutSession)

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { ExerciseMuscleInfo } from '../types';
import { PlusIcon, XIcon, SearchIcon, DumbbellIcon, InfoIcon, ActivityIcon, ChevronLeftIcon, GridIcon } from './icons';
import { useAppContext } from '../contexts/AppContext';
import { calculatePersonalizedBatteryTanks, calculateSetBatteryDrain, getDynamicAugeMetrics, DISPLAY_ROLE_WEIGHTS } from '../services/auge';

export interface AdvancedExercisePickerModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (exercise: ExerciseMuscleInfo) => void;
  onCreateNew: () => void;
  exerciseList: ExerciseMuscleInfo[];
  initialSearch?: string;
}

export const AdvancedExercisePickerModal: React.FC<AdvancedExercisePickerModalProps> = ({
  isOpen,
  onClose,
  onSelect,
  onCreateNew,
  exerciseList,
  initialSearch,
}) => {
  const { settings } = useAppContext();
  const [search, setSearch] = useState(initialSearch || '');
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [tooltipExId, setTooltipExId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [sortKey, setSortKey] = useState<'name' | 'muscle' | 'fatigue'>('name');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');
  const inputRef = useRef<HTMLInputElement>(null);

  const categoryMap: Record<string, string[]> = {
    Pecho: ['pectoral', 'pecho'],
    Espalda: ['dorsal', 'trapecio', 'espalda', 'romboide'],
    Hombros: ['deltoide', 'hombro'],
    Piernas: ['cuádriceps', 'cuadriceps', 'isquio', 'glúteo', 'gluteo', 'pantorrilla', 'pierna', 'femoral'],
    Brazos: ['bíceps', 'biceps', 'tríceps', 'triceps', 'antebrazo', 'brazo'],
    Core: ['abdomen', 'core', 'lumbar', 'espalda baja'],
  };

  const topTierNames = [
    'sentadilla trasera', 'peso muerto convencional', 'peso muerto rumano',
    'sentadilla hack', 'sentadilla pendulum', 'extensión de cuádriceps', 'sissy squat',
    'curl femoral sentado', 'curl nórdico', 'hip-thrust', 'press banca',
    'press inclinado', 'cruce de poleas', 'elevaciones laterales en polea',
    'press de hombro en máquina', 'jalón al pecho', 'dominada libre', 'remo en t', 'remo pendlay',
  ];

  const isTopTier = (exName: string) => topTierNames.some((name) => exName.toLowerCase().includes(name.toLowerCase()));

  const getParentMuscle = (muscleName: string) => {
    const lower = muscleName.toLowerCase();
    if (lower.includes('deltoide')) return muscleName;
    if (lower.includes('pectoral') || lower.includes('pecho')) return 'Pectoral';
    if (lower.includes('cuádriceps') || lower.includes('cuadriceps') || lower.includes('vasto') || lower.includes('recto femoral')) return 'Cuádriceps';
    if (lower.includes('bíceps') || lower.includes('biceps')) return 'Bíceps';
    if (lower.includes('tríceps') || lower.includes('triceps')) return 'Tríceps';
    if (lower.includes('isquio') || lower.includes('femoral')) return 'Isquiosurales';
    if (lower.includes('glúteo') || lower.includes('gluteo')) return 'Glúteos';
    if (lower.includes('trapecio')) return 'Trapecio';
    if (lower.includes('dorsal')) return 'Dorsal';
    if (lower.includes('gemelo') || lower.includes('pantorrilla') || lower.includes('sóleo')) return 'Pantorrillas';
    if (lower.includes('abdomen') || lower.includes('core')) return 'Abdomen';
    return muscleName;
  };

  const getPrimaryMuscleName = (ex: ExerciseMuscleInfo) => {
    const primary = ex.involvedMuscles.find((m) => m.role === 'primary');
    return primary ? getParentMuscle(primary.muscle) : 'Varios';
  };

  const calculateIntrinsicFatigue = (ex: ExerciseMuscleInfo) => {
    const tanks = calculatePersonalizedBatteryTanks(settings);
    return calculateSetBatteryDrain({ targetReps: 10, targetRPE: 8 }, ex, tanks, 0, 90);
  };

  const getFatigueUI = (drain: { muscularDrainPct: number; cnsDrainPct: number }) => {
    const total = drain.cnsDrainPct + drain.muscularDrainPct;
    if (total <= 3.0) return { color: 'bg-emerald-500', text: 'text-emerald-500' };
    if (total <= 6.0) return { color: 'bg-yellow-500', text: 'text-yellow-500' };
    return { color: 'bg-red-500', text: 'text-red-500' };
  };

  const getAugeIndexes = (exName: string, exInfo: ExerciseMuscleInfo) => getDynamicAugeMetrics(exInfo, exName);

  useEffect(() => {
    if (isOpen) {
      if (initialSearch) setSearch(initialSearch);
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setSearch('');
      setActiveCategory(null);
      setTooltipExId(null);
    }
  }, [isOpen, initialSearch]);

  const handleSort = (key: 'name' | 'muscle' | 'fatigue') => {
    if (sortKey === key) setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const filteredAndSorted = useMemo(() => {
    let result = exerciseList;

    if (search) {
      const q = search.toLowerCase();
      result = result.filter(
        (e) =>
          e.name.toLowerCase().includes(q) ||
          (e.equipment && e.equipment.toLowerCase().includes(q)) ||
          (e.alias && e.alias.toLowerCase().includes(q))
      );
    } else if (activeCategory) {
      if (activeCategory === 'KPKN Top Tier') result = result.filter((e) => isTopTier(e.name));
      else if (activeCategory === 'Baja Fatiga')
        result = result.filter((e) => {
          const drain = calculateIntrinsicFatigue(e);
          return drain.cnsDrainPct + drain.muscularDrainPct <= 6.0;
        });
      else {
        const terms = categoryMap[activeCategory] || [];
        result = result.filter((e) =>
          e.involvedMuscles.some((m) => m.role === 'primary' && terms.some((term) => m.muscle.toLowerCase().includes(term)))
        );
      }
    } else if (viewMode === 'grid') {
      return [];
    }

    result = [...result].sort((a, b) => {
      if (sortKey === 'name') return sortDir === 'asc' ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name);
      if (sortKey === 'fatigue') {
        const fA = calculateIntrinsicFatigue(a);
        const fB = calculateIntrinsicFatigue(b);
        const totalA = fA.cnsDrainPct + fA.muscularDrainPct;
        const totalB = fB.cnsDrainPct + fB.muscularDrainPct;
        return sortDir === 'asc' ? totalA - totalB : totalB - totalA;
      }
      if (sortKey === 'muscle') {
        const mA = getPrimaryMuscleName(a);
        const mB = getPrimaryMuscleName(b);
        const comp = sortDir === 'asc' ? mA.localeCompare(mB) : mB.localeCompare(mA);
        if (comp !== 0) return comp;
        const fA = calculateIntrinsicFatigue(a);
        const fB = calculateIntrinsicFatigue(b);
        return fA.cnsDrainPct + fA.muscularDrainPct - (fB.cnsDrainPct + fB.muscularDrainPct);
      }
      return 0;
    });

    return result.slice(0, 50);
  }, [search, activeCategory, exerciseList, viewMode, sortKey, sortDir]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[99999] bg-black/60 backdrop-blur-md flex items-center justify-center p-4 sm:p-6 font-sans overflow-hidden animate-in fade-in duration-200">
      <div className="absolute inset-0" onClick={onClose} aria-hidden="true" />
      <div
        className="bg-zinc-950 border border-white/10 shadow-2xl relative z-10 flex flex-col w-full max-w-lg min-h-[60vh] max-h-[85vh] rounded-3xl overflow-hidden animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-4 border-b border-white/5 bg-black/50 backdrop-blur-lg shrink-0 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <button
                onClick={() => setViewMode((prev) => (prev === 'grid' ? 'list' : 'grid'))}
                className="p-2 bg-white/5 rounded-lg text-zinc-400 hover:text-white hover:bg-white/10 transition-colors"
              >
                {viewMode === 'grid' ? <ActivityIcon size={16} /> : <GridIcon size={16} />}
              </button>
              <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">
                {viewMode === 'grid' ? 'Categorías' : 'Lista Detallada'}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={onCreateNew}
                className="px-3 py-1.5 bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg text-[10px] font-black uppercase text-white transition-colors flex items-center gap-1"
              >
                <PlusIcon size={12} /> Crear
              </button>
              <button onClick={onClose} className="p-2 bg-white/5 rounded-full text-zinc-400 hover:text-red-500 transition-colors">
                <XIcon size={16} />
              </button>
            </div>
          </div>
          <div className="flex items-center gap-3 bg-zinc-900 border border-white/10 rounded-xl px-3 focus-within:border-white/30 transition-colors">
            {activeCategory && !search ? (
              <button onClick={() => setActiveCategory(null)} className="p-1 text-zinc-400 hover:text-white transition-colors">
                <ChevronLeftIcon size={18} />
              </button>
            ) : (
              <SearchIcon size={18} className="text-zinc-500" />
            )}
            <input
              ref={inputRef}
              className="flex-1 bg-transparent border-none outline-none text-sm font-bold text-white placeholder-zinc-600 h-10 p-0 focus:ring-0"
              placeholder={activeCategory ? `Buscar en ${activeCategory}...` : 'Nombre del ejercicio...'}
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                if (viewMode === 'grid') setViewMode('list');
              }}
            />
          </div>
        </div>
        <div className="overflow-y-auto flex-1 custom-scrollbar relative bg-zinc-950 p-2">
          {viewMode === 'grid' && !search && !activeCategory ? (
            <div className="grid grid-cols-2 gap-2 p-2 auto-rows-[80px]">
              {[
                { id: 'KPKN Top Tier', cols: 'col-span-2 row-span-1', border: 'border-yellow-500/30', text: 'text-yellow-500', label: '★ KPKN Top Tier' },
                { id: 'Baja Fatiga', cols: 'col-span-1 row-span-1', border: 'border-emerald-500/30', text: 'text-emerald-500', label: 'Baja Fatiga' },
                { id: 'Piernas', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Piernas' },
                { id: 'Pecho', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Pecho' },
                { id: 'Espalda', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Espalda' },
                { id: 'Hombros', cols: 'col-span-2 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Hombros' },
                { id: 'Brazos', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Brazos' },
                { id: 'Core', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Core' },
              ].map((cat) => (
                <button
                  key={cat.id}
                  onClick={() => {
                    setActiveCategory(cat.id);
                    setViewMode('list');
                  }}
                  className={`${cat.cols} bg-black border ${cat.border} hover:border-white/50 rounded-2xl p-4 text-left flex flex-col justify-center items-start transition-all group relative overflow-hidden`}
                >
                  <div className="absolute inset-0 bg-white/0 group-hover:bg-white/5 transition-colors" />
                  <span className={`font-black text-sm uppercase tracking-tight relative z-10 ${cat.text}`}>{cat.label}</span>
                </button>
              ))}
            </div>
          ) : (
            <div className="flex flex-col h-full">
              {filteredAndSorted.length > 0 && (
                <div className="grid grid-cols-[2fr_1fr_1fr] gap-2 px-4 py-2 border-b border-white/5 sticky top-0 bg-zinc-950 z-20">
                  <button onClick={() => handleSort('name')} className="text-left flex items-center gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">
                    Ejercicio {sortKey === 'name' && (sortDir === 'asc' ? '↑' : '↓')}
                  </button>
                  <button onClick={() => handleSort('muscle')} className="text-left flex items-center gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">
                    Músculo {sortKey === 'muscle' && (sortDir === 'asc' ? '↑' : '↓')}
                  </button>
                  <button onClick={() => handleSort('fatigue')} className="text-right flex items-center justify-end gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">
                    Fatiga {sortKey === 'fatigue' && (sortDir === 'asc' ? '↑' : '↓')}
                  </button>
                </div>
              )}
              <div className="space-y-1 p-2">
                {filteredAndSorted.map((ex) => {
                  const topTier = isTopTier(ex.name);
                  const fatigueScore = calculateIntrinsicFatigue(ex);
                  const fatigueUI = getFatigueUI(fatigueScore);
                  const primaryMuscle = getPrimaryMuscleName(ex);
                  const groupedMuscles = ex.involvedMuscles.reduce(
                    (acc, m) => {
                      const parent = getParentMuscle(m.muscle);
                      const value = DISPLAY_ROLE_WEIGHTS[m.role] ?? 0.2;
                      if (!acc[parent] || value > acc[parent]) acc[parent] = value;
                      return acc;
                    },
                    {} as Record<string, number>
                  );

                  return (
                    <div key={ex.id} className="w-full bg-black rounded-xl border border-white/5 hover:border-white/20 transition-all flex flex-col">
                      <div className="flex items-center justify-between px-2 py-1">
                        <button onClick={() => onSelect(ex)} className="flex-1 text-left py-2 px-3 flex flex-col group">
                          <span className={`font-bold text-[13px] leading-tight mb-1.5 break-words ${topTier ? 'text-yellow-400' : 'text-white'}`}>
                            {topTier && '★ '}
                            {ex.name}
                          </span>
                          <div className="flex justify-between items-center w-full">
                            <span className="text-[9px] text-zinc-500 uppercase font-bold truncate">
                              {ex.equipment} • {primaryMuscle}
                            </span>
                            <div className="flex items-center gap-1.5 shrink-0">
                              <div className={`w-1.5 h-1.5 rounded-full ${fatigueUI.color} shadow-[0_0_8px_currentColor]`} />
                              <span className="text-[9px] font-mono text-zinc-400 bg-zinc-900 px-1 rounded border border-white/5">
                                -{fatigueScore.cnsDrainPct.toFixed(1)}% <span className="text-yellow-500">⚡</span>
                              </span>
                              <span className="text-[9px] font-mono text-zinc-400 bg-zinc-900 px-1 rounded border border-white/5">
                                -{fatigueScore.muscularDrainPct.toFixed(1)}% <span className="text-red-400">🥩</span>
                              </span>
                            </div>
                          </div>
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setTooltipExId(tooltipExId === ex.id ? null : ex.id);
                          }}
                          className={`p-2 transition-colors ${tooltipExId === ex.id ? 'text-blue-400' : 'text-zinc-600 hover:text-white'}`}
                        >
                          <InfoIcon size={16} />
                        </button>
                      </div>
                      {tooltipExId === ex.id && (
                        <div className="bg-zinc-900 border-t border-white/5 p-4 animate-in slide-in-from-top-2 duration-200">
                          <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                              <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest">Aporte (1 Serie Efectiva)</span>
                              <div className="space-y-1">
                                {Object.entries(groupedMuscles).map(([muscle, maxVal], idx) => (
                                  <div key={idx} className="flex justify-between items-center text-[10px]">
                                    <span className="font-bold text-zinc-300">{muscle}</span>
                                    <span className="text-zinc-400 font-mono">+{(maxVal as number).toFixed(1)}</span>
                                  </div>
                                ))}
                              </div>
                            </div>
                            <div className="space-y-2 border-l border-white/10 pl-4">
                              <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest block">Índices AUGE</span>
                              <div className="bg-black border border-white/5 p-2 rounded-lg grid grid-cols-1 gap-1.5">
                                {(() => {
                                  const { efc, ssc, cnc } = getAugeIndexes(ex.name, ex);
                                  return (
                                    <>
                                      <div className="flex justify-between items-center">
                                        <span className="text-[9px] text-zinc-400">Metabólico (EFC)</span>
                                        <span className="text-[10px] font-mono text-white">{efc.toFixed(1)}</span>
                                      </div>
                                      <div className="flex justify-between items-center">
                                        <span className="text-[9px] text-zinc-400">Neural (CNC)</span>
                                        <span className="text-[10px] font-mono text-white">{cnc.toFixed(1)}</span>
                                      </div>
                                      <div className="flex justify-between items-center">
                                        <span className="text-[9px] text-zinc-400">Espinal (SSC)</span>
                                        <span className="text-[10px] font-mono text-red-400">{ssc.toFixed(1)}</span>
                                      </div>
                                    </>
                                  );
                                })()}
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
                {filteredAndSorted.length === 0 && (
                  <div className="text-center py-12">
                    <DumbbellIcon size={32} className="mx-auto text-zinc-800 mb-2" />
                    <p className="text-xs text-zinc-500 font-bold mb-4">No se encontraron ejercicios</p>
                    <button
                      onClick={onCreateNew}
                      className="px-6 py-2 bg-white text-black rounded-full font-black text-[10px] uppercase tracking-widest hover:scale-105 transition-transform"
                    >
                      Crear Ejercicio Personalizado
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
