// components/JointDetailView.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ChevronRightIcon, DumbbellIcon } from './icons';

const JOINT_TYPE_LABELS: Record<string, string> = {
  'hinge': 'Bisagra',
  'ball-socket': 'Esferoidea',
  'pivot': 'Pivote',
  'gliding': 'Deslizante',
  'saddle': 'Silla de montar',
  'condyloid': 'Condílea',
};

interface JointDetailViewProps {
  jointId: string;
  isOnline: boolean;
}

const JointDetailView: React.FC<JointDetailViewProps> = ({ jointId }) => {
  const { jointDatabase, muscleGroupData, tendonDatabase } = useAppState();
  const { navigateTo } = useAppDispatch();

  const joint = jointDatabase?.find(j => j.id === jointId);
  const relatedMuscles = joint?.musclesCrossing?.map(mId => muscleGroupData.find(m => m.id === mId)).filter(Boolean) || [];
  const relatedTendons = joint?.tendonsRelated?.map(tId => tendonDatabase?.find(t => t.id === tId)).filter(Boolean) || [];

  if (!joint) {
    return (
      <div className="pt-[65px] text-center">
        <h2 className="text-2xl font-bold text-red-400">No encontrado</h2>
        <p className="text-slate-300 mt-2">Articulación "{jointId}" no existe en la base de datos.</p>
      </div>
    );
  }

  const typeLabel = JOINT_TYPE_LABELS[joint.type] || joint.type;

  return (
    <div className="pb-20 animate-fade-in">
      <header className="relative h-32 -mx-4 bg-gradient-to-b from-cyan-900/40 to-black">
        <div className="absolute inset-0 bg-gradient-to-t from-black via-transparent to-transparent" />
        <div className="absolute bottom-4 left-4 right-4">
          <span className="text-cyan-400 text-xs font-bold uppercase tracking-wider">{typeLabel}</span>
          <h1 className="text-3xl font-bold text-white mt-1">{joint.name}</h1>
        </div>
      </header>

      <div className="space-y-6 mt-6">
        <div className="glass-card-nested p-4">
          <h3 className="font-bold text-white mb-2">Descripción</h3>
          <p className="text-slate-300 text-sm">{joint.description}</p>
        </div>

        {relatedMuscles.length > 0 && (
          <div className="glass-card-nested p-4">
            <h3 className="font-bold text-white mb-3 flex items-center gap-2">
              <span className="text-purple-400">●</span> Músculos que la cruzan
            </h3>
            <div className="space-y-2">
              {relatedMuscles.map(m => m && (
                <div
                  key={m.id}
                  onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })}
                  className="p-3 flex justify-between items-center rounded-lg bg-slate-900/50 hover:bg-slate-800 cursor-pointer transition-colors"
                >
                  <span className="font-semibold text-slate-200">{m.name}</span>
                  <ChevronRightIcon className="text-slate-500" size={16} />
                </div>
              ))}
            </div>
          </div>
        )}

        {relatedTendons.length > 0 && (
          <div className="glass-card-nested p-4">
            <h3 className="font-bold text-white mb-3 flex items-center gap-2">
              <span className="text-amber-400">●</span> Tendones relacionados
            </h3>
            <div className="space-y-2">
              {relatedTendons.map(t => t && (
                <div
                  key={t.id}
                  onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                  className="p-3 flex justify-between items-center rounded-lg bg-slate-900/50 hover:bg-slate-800 cursor-pointer transition-colors"
                >
                  <span className="font-semibold text-slate-200">{t.name}</span>
                  <ChevronRightIcon className="text-slate-500" size={16} />
                </div>
              ))}
            </div>
          </div>
        )}

        {joint.commonInjuries && joint.commonInjuries.length > 0 && (
          <details className="glass-card-nested !p-0">
            <summary className="p-4 cursor-pointer flex justify-between items-center list-none">
              <h3 className="font-bold text-red-400/90">Lesiones Comunes</h3>
              <ChevronRightIcon className="details-arrow" />
            </summary>
            <div className="p-4 border-t border-slate-700/50 space-y-3">
              {joint.commonInjuries.map((inj, i) => (
                <div key={i} className="bg-slate-900/50 rounded-lg p-3">
                  <h4 className="font-semibold text-white text-sm">{inj.name}</h4>
                  <p className="text-slate-400 text-xs mt-1">{inj.description}</p>
                  {inj.returnProgressions && inj.returnProgressions.length > 0 && (
                    <p className="text-slate-500 text-xs mt-2">Progresión: {inj.returnProgressions.join(' → ')}</p>
                  )}
                </div>
              ))}
            </div>
          </details>
        )}
      </div>
    </div>
  );
};

export default JointDetailView;
