// components/workout/FinishCard.tsx
// Tarjeta final del carrusel, consistente con M3 outlined + glass.

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
        workout-surface-card flex-shrink-0 overflow-hidden transition-all duration-200 rounded-[12px] border bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)]
        ${expanded ? 'w-[280px] min-h-[130px]' : 'w-[186px] min-h-[78px]'}
      `}
    >
      <button
        type="button"
        onClick={handleToggle}
        className={`
          workout-pressable w-full h-full flex flex-col items-start justify-center gap-1
          px-4 py-3 text-left transition-all active:scale-[0.99]
          ${!expanded ? 'min-h-[78px]' : ''}
        `}
      >
        <div className="flex items-center gap-2">
          <CheckCircleIcon size={20} className="text-[var(--md-sys-color-primary)]" />
          <span className="text-[15px] leading-5 font-medium text-[var(--md-sys-color-on-surface)]">
            Finalizar sesión
          </span>
        </div>

        {!expanded && (
          <span className="text-[12px] leading-4 text-[var(--md-sys-color-on-surface-variant)]">
            {completedSetsCount}/{totalSetsCount} series
          </span>
        )}

        {expanded && (
          <div className="w-full mt-2 space-y-1 text-left">
            <p className="text-[12px] leading-4 text-[var(--md-sys-color-on-surface-variant)]">
              {durationMinutes} min • {completedSetsCount}/{totalSetsCount} series
            </p>
            {totalTonnage > 0 && (
              <p className="text-[12px] leading-4 text-[var(--md-sys-color-on-surface-variant)]">
                Tonelaje: {totalTonnage.toLocaleString()} kg
              </p>
            )}
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); onFinish(); }}
              className="workout-pressable w-full mt-2 py-2.5 rounded-[999px] bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] text-[11px] font-semibold uppercase tracking-wide"
            >
              Abrir finalización
            </button>
          </div>
        )}
      </button>
    </div>
  );
};

export default FinishCard;

