// components/workout/FinishCard.tsx
// Tarjeta final del carrusel - expande con resumen y abre modal de finalización

import React, { useState } from 'react';
import { CheckCircleIcon } from '../icons';

interface FinishCardProps {
  isExpanded: boolean;
  onPress: () => void;
  onExpand: () => void;
  onFinish: () => void;
  durationMinutes?: number;
  completedSetsCount?: number;
  totalSetsCount?: number;
  totalTonnage?: number;
}

const FinishCard: React.FC<FinishCardProps> = ({
  isExpanded,
  onPress,
  onExpand,
  onFinish,
  durationMinutes = 0,
  completedSetsCount = 0,
  totalSetsCount = 0,
  totalTonnage = 0,
}) => {
  const [internalExpanded, setInternalExpanded] = useState(false);
  const expanded = isExpanded || internalExpanded;

  const handleToggle = () => {
    if (expanded) {
      onPress();
    } else {
      setInternalExpanded(true);
      onExpand();
    }
  };

  return (
    <div
      className={`
        flex-shrink-0 rounded-xl border-2 border-emerald-500/50
        overflow-hidden transition-all duration-200
        ${expanded ? 'w-64 min-h-[120px]' : 'w-36 min-h-[56px]'}
      `}
      style={{
        backgroundColor: expanded ? 'rgba(16,185,129,0.1)' : 'rgba(15,23,42,0.8)',
      }}
    >
      <button
        type="button"
        onClick={handleToggle}
        className={`
          w-full h-full flex flex-col items-center justify-center gap-1
          p-3 transition-all active:scale-95
          ${!expanded ? 'min-h-[56px]' : ''}
        `}
      >
        <CheckCircleIcon size={expanded ? 24 : 20} className="text-emerald-400" />
        <span className="text-xs font-bold text-emerald-400 uppercase tracking-widest">
          Finalizar
        </span>
        {expanded && (
          <div className="w-full mt-2 space-y-1 text-left">
            <p className="text-[10px] text-slate-400 font-mono">
              {durationMinutes} min • {completedSetsCount}/{totalSetsCount} series
            </p>
            {totalTonnage > 0 && (
              <p className="text-[10px] text-slate-400 font-mono">
                Tonelaje: {totalTonnage.toLocaleString()} kg
              </p>
            )}
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); onFinish(); }}
              className="w-full mt-2 py-2 rounded-lg bg-emerald-500 text-white text-[10px] font-black uppercase tracking-widest hover:bg-emerald-400 transition-colors"
            >
              Abrir modal de finalización
            </button>
          </div>
        )}
      </button>
    </div>
  );
};

export default FinishCard;
