// components/ExerciseInfoModal.tsx
import React, { useMemo, useState } from 'react';
import { ExerciseMuscleInfo, MuscleHierarchy } from '../types';
import Modal from './ui/Modal';
import { ChevronRightIcon, BrainIcon, ActivityIcon } from './icons';
import { KinesiologyAnalysis } from './ExerciseDetailView';
import { getDynamicAugeMetrics } from '../services/fatigueService';

export const MuscleActivationView: React.FC<{
    involvedMuscles: ExerciseMuscleInfo['involvedMuscles'];
    muscleHierarchy: MuscleHierarchy;
}> = ({ involvedMuscles = [], muscleHierarchy }) => {
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

    const childToParentMap = useMemo(() => {
        const map = new Map<string, string>();
        if (!muscleHierarchy || !muscleHierarchy.bodyPartHierarchy) return map;
        Object.values(muscleHierarchy.bodyPartHierarchy).flat().forEach(group => {
            if (typeof group === 'object' && group !== null) {
                const parent = Object.keys(group)[0];
                const children = Object.values(group)[0];
                children.forEach(child => map.set(child, parent));
            }
        });
        return map;
    }, [muscleHierarchy]);
    
    const groupedByParent = useMemo(() => {
        const groups: Record<string, { role: 'primary' | 'secondary' | 'stabilizer'; children: typeof involvedMuscles; activation: number }> = {};
        
        // Defensive check to ensure involvedMuscles is an array before processing
        if (!Array.isArray(involvedMuscles)) {
            return [];
        }

        involvedMuscles.forEach(muscleInfo => {
            if (!muscleInfo) return; // Defensive check for malformed data
            const parent = childToParentMap.get(muscleInfo.muscle) || muscleInfo.muscle;
            if (!groups[parent]) {
                groups[parent] = { role: 'stabilizer', children: [], activation: 0 };
            }
            groups[parent].children.push(muscleInfo);
            // The role of the parent group is the highest role among its children
            if (muscleInfo.role === 'primary') groups[parent].role = 'primary';
            else if (muscleInfo.role === 'secondary' && groups[parent].role === 'stabilizer') groups[parent].role = 'secondary';
            
            // Use the highest activation among children as the representative activation for the parent
            if(muscleInfo.activation > groups[parent].activation) {
                 groups[parent].activation = muscleInfo.activation;
            }
        });

        const roleOrder = { primary: 1, secondary: 2, stabilizer: 3 };

        return Object.entries(groups).sort(([, a], [, b]) => {
            const orderA = roleOrder[a.role];
            const orderB = roleOrder[b.role];

            if (orderA !== orderB) {
                return orderA - orderB;
            }

            // If roles are the same, sort by activation descending
            return b.activation - a.activation;
        });
    }, [involvedMuscles, childToParentMap]);
    
    const roleConfig = {
        primary: { label: 'Primario', color: 'bg-green-500', textColor: 'text-green-300', bgColor: 'bg-green-500/20' },
        secondary: { label: 'Secundario', color: 'bg-sky-500', textColor: 'text-sky-300', bgColor: 'bg-sky-500/20' },
        stabilizer: { label: 'Estabilizador', color: 'bg-emerald-500', textColor: 'text-emerald-300', bgColor: 'bg-emerald-500/20' },
    };

    const toggleGroup = (groupName: string) => {
        setExpandedGroups(prev => {
            const newSet = new Set(prev);
            if (newSet.has(groupName)) {
                newSet.delete(groupName);
            } else {
                newSet.add(groupName);
            }
            return newSet;
        });
    };

    return (
        <div className="space-y-4">
            {groupedByParent.map(([parentName, data]) => {
                const isExpanded = expandedGroups.has(parentName);
                const hasChildren = data.children.length > 1 || (data.children.length === 1 && data.children[0].muscle !== parentName);
                const roleInfo = roleConfig[data.role];

                return (
                    <div key={parentName}>
                        <div onClick={hasChildren ? () => toggleGroup(parentName) : undefined} className={`flex justify-between items-center ${hasChildren ? 'cursor-pointer' : ''}`}>
                             <div className="flex items-center gap-2">
                                <ChevronRightIcon size={16} className={`transition-transform text-slate-500 ${isExpanded ? 'rotate-90' : 'rotate-0'} ${!hasChildren ? 'invisible' : ''}`}/>
                                <span className="font-semibold text-slate-200 text-lg">{parentName}</span>
                                <span className={`text-xs font-bold px-1.5 py-0.5 rounded-full ${roleInfo.bgColor} ${roleInfo.textColor}`}>{roleInfo.label}</span>
                            </div>
                            <span className="font-mono text-slate-300 text-md">{Math.round(data.activation * 100)}%</span>
                        </div>
                        <div className="w-full bg-slate-700 rounded-full h-2.5 mt-1">
                            <div className={`${roleInfo.color} h-2.5 rounded-full`} style={{ width: `${data.activation * 100}%` }}></div>
                        </div>

                        {isExpanded && hasChildren && (
                            <div className="pl-6 mt-2 space-y-2 border-l-2 border-slate-700 ml-2 animate-fade-in">
                                {data.children.sort((a,b) => b.activation - a.activation).map((child, childIndex) => (
                                    <div key={childIndex}>
                                        <div className="flex justify-between items-baseline text-xs">
                                            <span className="font-semibold text-slate-400">{child.muscle}</span>
                                            <span className="font-mono text-slate-500">{Math.round(child.activation * 100)}%</span>
                                        </div>
                                        <div className="w-full bg-slate-800 rounded-full h-2 mt-1">
                                            <div className={`${roleConfig[child.role].color} opacity-80 h-2 rounded-full`} style={{ width: `${child.activation * 100}%` }}></div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                );
            })}
        </div>
    );
};


const ExerciseInfoModal: React.FC<{
  exercise: ExerciseMuscleInfo | null;
  onClose: () => void;
  muscleHierarchy: MuscleHierarchy;
}> = ({ exercise, onClose, muscleHierarchy }) => {
  
  // Hook de cálculo en vivo para métricas AUGE
  const augeMetrics = useMemo(() => {
    if (!exercise) return null;
    return getDynamicAugeMetrics(exercise);
  }, [exercise]);

  if (!exercise || !augeMetrics) return null;

  const getSfrColor = (score: number) => {
    const hue = score * 12; // 1 -> 12 (red), 10 -> 120 (green)
    return `hsl(${hue}, 80%, 45%)`;
  };

  return (
    <Modal isOpen={!!exercise} onClose={onClose} title={exercise.name}>
      <div className="p-2 space-y-4">
        <p className="text-slate-300 text-sm leading-relaxed">{exercise.description}</p>
        
        {/* --- NUEVO: Dashboard de Drenaje AUGE --- */}
        <div className="glass-card-nested p-4 border border-orange-500/20 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-24 h-24 bg-orange-500/5 rounded-bl-[100px] pointer-events-none"></div>
            <h3 className="font-bold text-sm text-orange-400 mb-3 flex items-center gap-2 uppercase tracking-widest relative z-10"><ActivityIcon size={16}/> Perfil de Drenaje</h3>
            <div className="grid grid-cols-3 gap-2 text-center relative z-10">
                <div className="bg-slate-950/50 p-2 rounded-xl border border-white/5">
                    <span className="block text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">EFC (Local)</span>
                    <span className="text-xl font-black text-white">{augeMetrics.efc.toFixed(1)}</span>
                    <span className="block text-[8px] text-slate-500 uppercase">/ 5.0</span>
                </div>
                <div className="bg-slate-950/50 p-2 rounded-xl border border-white/5">
                    <span className="block text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">CNC (SNC)</span>
                    <span className="text-xl font-black text-white">{augeMetrics.cnc.toFixed(1)}</span>
                    <span className="block text-[8px] text-slate-500 uppercase">/ 5.0</span>
                </div>
                <div className="bg-slate-950/50 p-2 rounded-xl border border-white/5">
                    <span className="block text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">SSC (Espinal)</span>
                    <span className="text-xl font-black text-white">{augeMetrics.ssc.toFixed(1)}</span>
                    <span className="block text-[8px] text-slate-500 uppercase">/ 2.0</span>
                </div>
            </div>
        </div>
        
        {exercise.sfr && (
            <div className="glass-card-nested p-4">
                 <h3 className="font-bold text-lg text-white mb-2">Ratio Estímulo-Fatiga (SFR)</h3>
                 <div className="flex items-center gap-4">
                    <div className="relative w-20 h-20">
                        <svg className="w-full h-full" viewBox="0 0 36 36">
                            <path className="text-slate-700" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="currentColor" strokeWidth="2.5"></path>
                            <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="currentColor" strokeWidth="3" strokeDasharray={`${exercise.sfr.score * 10}, 100`} style={{ stroke: getSfrColor(exercise.sfr.score) }}></path>
                        </svg>
                        <div className="absolute inset-0 flex items-center justify-center">
                            <span className="text-2xl font-bold">{exercise.sfr.score}</span>
                        </div>
                    </div>
                    <div>
                        <p className="text-xs text-slate-400">{exercise.sfr.justification}</p>
                    </div>
                </div>
            </div>
        )}
        
        <div className="glass-card-nested p-4">
            <h3 className="font-bold text-lg text-white mb-3">Activación Muscular</h3>
            <MuscleActivationView involvedMuscles={exercise.involvedMuscles} muscleHierarchy={muscleHierarchy} />
        </div>
        
        <KinesiologyAnalysis exercise={exercise} />
      </div>
    </Modal>
  );
};

export default ExerciseInfoModal;