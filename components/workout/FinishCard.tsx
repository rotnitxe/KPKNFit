// components/workout/FinishCard.tsx
// Carrusel final — estilo "Tú": plano, gris, sin tarjetas

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
        flex-shrink-0 overflow-hidden transition-all duration-200 bg-[#252525]
        ${expanded ? 'w-64 min-h-[120px]' : 'w-36 min-h-[56px]'}
      `}
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
        <CheckCircleIcon size={expanded ? 24 : 20} className="text-white" />
        <span className="text-xs font-medium text-white uppercase tracking-wide">
          Finalizar
        </span>
        {expanded && (
          <div className="w-full mt-2 space-y-1 text-left">
            <p className="text-[10px] text-[#a3a3a3]">
              {durationMinutes} min • {completedSetsCount}/{totalSetsCount} series
            </p>
            {totalTonnage > 0 && (
              <p className="text-[10px] text-[#a3a3a3]">
                Tonelaje: {totalTonnage.toLocaleString()} kg
              </p>
            )}
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); onFinish(); }}
              className="w-full mt-2 py-2 bg-white text-[#1a1a1a] text-[10px] font-medium uppercase tracking-wide"
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
