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
    Espalda: ['dorsal', 'trapecio', 'espalda', 'romboide', 'erectores'],
    Hombros: ['deltoide', 'hombro'],
    Piernas: ['cuádriceps', 'cuadriceps', 'isquio', 'glúteo', 'gluteo', 'gluteos', 'pantorrilla', 'pierna', 'femoral', 'gastrocnemio', 'sóleo', 'soleo', 'vasto', 'recto femoral', 'aductores'],
    Brazos: ['bíceps', 'biceps', 'tríceps', 'triceps', 'antebrazo', 'brazo', 'braquiorradial'],
    Core: ['abdomen', 'core', 'lumbar', 'espalda baja', 'recto abdominal', 'oblicuos', 'transverso'],
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
      const normalizeText = (text: string) => text.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
      const rawSearch = normalizeText(search);
      const searchTerms = rawSearch
        .split(' ')
        .filter(t => t.length > 0 && !['de', 'con', 'en', 'el', 'la', 'los', 'las', 'y', 'a', 'para'].includes(t));

      const scoredResults = result.map((e) => {
        const primaryMuscle = getPrimaryMuscleName(e);
        const nameNorm = normalizeText(e.name);
        const searchTarget = normalizeText(`${e.name} ${e.equipment || ''} ${e.alias || ''} ${primaryMuscle}`);

        let score = 0;
        let matches = 0;

        // Exact matches
        if (nameNorm.includes(rawSearch)) {
          score += 100;
          if (nameNorm === rawSearch) score += 50;
          if (nameNorm.startsWith(rawSearch)) score += 20;
        }

        searchTerms.forEach(term => {
          if (searchTarget.includes(term)) {
            matches++;
            score += 10;
            // Prefix bonus
            if (searchTarget.split(' ').some(w => w.startsWith(term))) score += 5;
          } else {
            // Forgiving typo match logic
            if (term.length > 3) {
              const partialMatch = searchTarget.split(' ').some(w => w.length > 3 && (w.includes(term.slice(0, -1)) || term.includes(w.slice(0, -1))));
              if (partialMatch) {
                matches += 0.5;
                score += 3;
              }
            }
          }
        });

        // Boost KPKN Top Tier exercises if they match
        if (isTopTier(e.name) && matches > 0) score += 15;

        return { exercise: e, score, matches, matchedTermsCount: matches };
      }).filter(item => item.matches >= Math.max(1, searchTerms.length - 1.5) || item.score > 0);
      // Need at least one match, or most terms matched if multiple typed.

      result = scoredResults
        .sort((a, b) => b.score - a.score)
        .map(item => item.exercise);
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
    <div className="fixed inset-0 z-[99999] bg-black/40 flex items-center justify-center p-4 sm:p-6 font-sans overflow-hidden animate-in fade-in duration-200">
      <div className="absolute inset-0" onClick={onClose} aria-hidden="true" />
      <div
        className="bg-[#e5e5e5] border border-[#a3a3a3] shadow-xl relative z-10 flex flex-col w-full max-w-lg min-h-[60vh] max-h-[85vh] overflow-hidden animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-4 border-b border-[#a3a3a3] bg-white shrink-0 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <button
                onClick={() => setViewMode((prev) => (prev === 'grid' ? 'list' : 'grid'))}
                className="p-2 bg-[#f5f5f5] border border-[#a3a3a3] text-[#525252] hover:text-[#1a1a1a] hover:bg-[#e5e5e5] transition-colors"
              >
                {viewMode === 'grid' ? <ActivityIcon size={16} /> : <GridIcon size={16} />}
              </button>
              <span className="text-[10px] font-semibold uppercase tracking-wide text-[#525252]">
                {viewMode === 'grid' ? 'Categorías' : 'Lista Detallada'}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={onCreateNew}
                className="px-3 py-1.5 bg-white border border-[#a3a3a3] text-[#1a1a1a] hover:bg-[#f5f5f5] transition-colors flex items-center gap-1 font-medium text-[10px] uppercase"
              >
                <PlusIcon size={12} /> Crear
              </button>
              <button onClick={onClose} className="p-2 text-[#525252] hover:text-[#1a1a1a] transition-colors">
                <XIcon size={16} />
              </button>
            </div>
          </div>
          <div className="flex items-center gap-3 bg-white border border-[#a3a3a3] px-3 focus-within:border-[#737373] transition-colors">
            {activeCategory && !search ? (
              <button onClick={() => setActiveCategory(null)} className="p-1 text-[#525252] hover:text-[#1a1a1a] transition-colors">
                <ChevronLeftIcon size={18} />
              </button>
            ) : (
              <SearchIcon size={18} className="text-[#737373]" />
            )}
            <input
              ref={inputRef}
              className="flex-1 bg-transparent border-none outline-none text-sm font-medium text-[#1a1a1a] placeholder-[#a3a3a3] h-10 p-0 focus:ring-0"
              placeholder={activeCategory ? `Buscar en ${activeCategory}...` : 'Nombre del ejercicio...'}
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                if (viewMode === 'grid') setViewMode('list');
              }}
            />
          </div>
        </div>
        <div className="overflow-y-auto flex-1 custom-scrollbar relative bg-[#e5e5e5] p-2">
          {viewMode === 'grid' && !search && !activeCategory ? (
            <div className="grid grid-cols-2 gap-2 p-2 auto-rows-[80px]">
              {[
                { id: 'KPKN Top Tier', cols: 'col-span-2 row-span-1', border: 'border-[#525252]', text: 'text-[#1a1a1a]', label: '★ KPKN Top Tier' },
                { id: 'Baja Fatiga', cols: 'col-span-1 row-span-1', border: 'border-[#a3a3a3]', text: 'text-[#1a1a1a]', label: 'Baja Fatiga' },
                { id: 'Piernas', cols: 'col-span-1 row-span-1', border: 'border-[#a3a3a3]', text: 'text-[#1a1a1a]', label: 'Piernas' },
                { id: 'Pecho', cols: 'col-span-1 row-span-1', border: 'border-[#a3a3a3]', text: 'text-[#1a1a1a]', label: 'Pecho' },
                { id: 'Espalda', cols: 'col-span-1 row-span-1', border: 'border-[#a3a3a3]', text: 'text-[#1a1a1a]', label: 'Espalda' },
                { id: 'Hombros', cols: 'col-span-2 row-span-1', border: 'border-[#a3a3a3]', text: 'text-[#1a1a1a]', label: 'Hombros' },
                { id: 'Brazos', cols: 'col-span-1 row-span-1', border: 'border-[#a3a3a3]', text: 'text-[#1a1a1a]', label: 'Brazos' },
                { id: 'Core', cols: 'col-span-1 row-span-1', border: 'border-[#a3a3a3]', text: 'text-[#1a1a1a]', label: 'Core' },
              ].map((cat) => (
                <button
                  key={cat.id}
                  onClick={() => {
                    setActiveCategory(cat.id);
                    setViewMode('list');
                  }}
                  className={`${cat.cols} bg-white border ${cat.border} hover:border-[#737373] hover:bg-[#f5f5f5] p-4 text-left flex flex-col justify-center items-start transition-all`}
                >
                  <span className={`font-semibold text-sm uppercase tracking-tight ${cat.text}`}>{cat.label}</span>
                </button>
              ))}
            </div>
          ) : (
            <div className="flex flex-col h-full">
              {filteredAndSorted.length > 0 && (
                <div className="grid grid-cols-[2fr_1fr_1fr] gap-2 px-4 py-2 border-b border-[#a3a3a3] sticky top-0 bg-[#e5e5e5] z-20">
                  <button onClick={() => handleSort('name')} className="text-left flex items-center gap-1 text-[9px] font-semibold uppercase text-[#525252] hover:text-[#1a1a1a]">
                    Ejercicio {sortKey === 'name' && (sortDir === 'asc' ? '↑' : '↓')}
                  </button>
                  <button onClick={() => handleSort('muscle')} className="text-left flex items-center gap-1 text-[9px] font-semibold uppercase text-[#525252] hover:text-[#1a1a1a]">
                    Músculo {sortKey === 'muscle' && (sortDir === 'asc' ? '↑' : '↓')}
                  </button>
                  <button onClick={() => handleSort('fatigue')} className="text-right flex items-center justify-end gap-1 text-[9px] font-semibold uppercase text-[#525252] hover:text-[#1a1a1a]">
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
                    <div key={ex.id} className="w-full bg-white border border-[#a3a3a3] hover:border-[#737373] transition-all flex flex-col">
                      <div className="flex items-center justify-between px-2 py-1">
                        <button onClick={() => onSelect(ex)} className="flex-1 text-left py-2 px-3 flex flex-col group">
                          <span className={`font-semibold text-[13px] leading-tight mb-1.5 break-words ${topTier ? 'text-[#525252]' : 'text-[#1a1a1a]'}`}>
                            {topTier && '★ '}
                            {ex.name}
                          </span>
                          <div className="flex justify-between items-center w-full">
                            <span className="text-[9px] text-[#737373] uppercase font-medium truncate">
                              {ex.equipment} • {primaryMuscle}
                            </span>
                            <div className="flex items-center gap-1.5 shrink-0">
                              <div className={`w-1.5 h-1.5 rounded-full ${fatigueUI.color}`} />
                              <span className="text-[9px] text-[#525252] bg-[#f5f5f5] px-1 border border-[#a3a3a3]">
                                -{fatigueScore.cnsDrainPct.toFixed(1)}% ⚡
                              </span>
                              <span className="text-[9px] text-[#525252] bg-[#f5f5f5] px-1 border border-[#a3a3a3]">
                                -{fatigueScore.muscularDrainPct.toFixed(1)}% 🥩
                              </span>
                            </div>
                          </div>
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setTooltipExId(tooltipExId === ex.id ? null : ex.id);
                          }}
                          className={`p-2 transition-colors ${tooltipExId === ex.id ? 'text-[#525252]' : 'text-[#737373] hover:text-[#1a1a1a]'}`}
                        >
                          <InfoIcon size={16} />
                        </button>
                      </div>
                      {tooltipExId === ex.id && (
                        <div className="bg-[#f5f5f5] border-t border-[#a3a3a3] p-4 animate-in slide-in-from-top-2 duration-200">
                          <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                              <span className="text-[9px] font-semibold uppercase text-[#525252] tracking-wide">Aporte (1 Serie Efectiva)</span>
                              <div className="space-y-1">
                                {Object.entries(groupedMuscles).map(([muscle, maxVal], idx) => (
                                  <div key={idx} className="flex justify-between items-center text-[10px]">
                                    <span className="font-medium text-[#1a1a1a]">{muscle}</span>
                                    <span className="text-[#525252]">+{(maxVal as number).toFixed(1)}</span>
                                  </div>
                                ))}
                              </div>
                            </div>
                            <div className="space-y-2 border-l border-[#a3a3a3] pl-4">
                              <span className="text-[9px] font-semibold uppercase text-[#525252] tracking-wide block">Índices AUGE</span>
                              <div className="bg-white border border-[#a3a3a3] p-2 grid grid-cols-1 gap-1.5">
                                {(() => {
                                  const { efc, ssc, cnc } = getAugeIndexes(ex.name, ex);
                                  return (
                                    <>
                                      <div className="flex justify-between items-center">
                                        <span className="text-[9px] text-[#525252]">Metabólico (EFC)</span>
                                        <span className="text-[10px] font-medium text-[#1a1a1a]">{efc.toFixed(1)}</span>
                                      </div>
                                      <div className="flex justify-between items-center">
                                        <span className="text-[9px] text-[#525252]">Neural (CNC)</span>
                                        <span className="text-[10px] font-medium text-[#1a1a1a]">{cnc.toFixed(1)}</span>
                                      </div>
                                      <div className="flex justify-between items-center">
                                        <span className="text-[9px] text-[#525252]">Espinal (SSC)</span>
                                        <span className="text-[10px] font-medium text-[#525252]">{ssc.toFixed(1)}</span>
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
                    <DumbbellIcon size={32} className="mx-auto text-[#a3a3a3] mb-2" />
                    <p className="text-xs text-[#525252] font-medium mb-4">No se encontraron ejercicios</p>
                    <button
                      onClick={onCreateNew}
                      className="px-6 py-2 bg-white text-[#1a1a1a] border border-[#a3a3a3] font-semibold text-[10px] uppercase tracking-wide hover:bg-[#f5f5f5] transition-colors"
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
