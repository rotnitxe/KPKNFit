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
        flex-shrink-0 rounded-lg border border-amber-500/30
        overflow-hidden transition-all duration-200
        ${expanded ? 'w-64 min-h-[120px]' : 'w-36 min-h-[56px]'}
      `}
      style={{
        backgroundColor: expanded ? 'rgba(120, 53, 15, 0.25)' : 'rgba(15,23,42,0.9)',
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
        <CheckCircleIcon size={expanded ? 24 : 20} className="text-amber-400" />
        <span className="text-xs font-bold text-amber-400 uppercase tracking-widest">
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
              className="w-full mt-2 py-2 rounded-lg border border-amber-500/30 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 text-[10px] font-bold uppercase tracking-widest transition-colors"
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
