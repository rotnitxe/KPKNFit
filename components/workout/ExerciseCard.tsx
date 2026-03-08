// components/workout/ExerciseCard.tsx
// Carrusel de ejercicios — estilo "Tú": plano, sin tarjetas, integrado en fondo

import React from 'react';
import type { Exercise } from '../../types';

interface ExerciseCardProps {
  exercises: Exercise[];
  color: string;
  isActive: boolean;
  isSkipped?: boolean;
  onPress: () => void;
  onLongPress: () => void;
}

const ExerciseCard: React.FC<ExerciseCardProps> = ({
  exercises,
  isActive,
  isSkipped = false,
  onPress,
  onLongPress,
}) => {
  const label = exercises.length > 1
    ? exercises.map(e => e.name).join(' • ')
    : exercises[0]?.name ?? '';

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    onLongPress();
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    const timer = setTimeout(() => onLongPress(), 500) as unknown as number;
    (e.currentTarget as HTMLButtonElement & { _longPressTimer?: number })._longPressTimer = timer;
  };

  const clearTimer = (e: React.TouchEvent) => {
    const el = e.currentTarget as HTMLButtonElement & { _longPressTimer?: number };
    if (el._longPressTimer) {
      clearTimeout(el._longPressTimer);
      el._longPressTimer = undefined;
    }
  };

  return (
    <button
      type="button"
      onClick={onPress}
      onContextMenu={handleContextMenu}
      onTouchStart={handleTouchStart}
      onTouchEnd={clearTimer}
      onTouchCancel={clearTimer}
      className={
        'flex-shrink-0 w-36 min-h-[56px] px-3 py-2.5 flex flex-col items-center justify-center gap-0.5 transition-all duration-200 active:scale-95 ' +
        (isSkipped ? 'opacity-50 bg-[#1a1a1a]' : isActive ? 'bg-[#3f3f3f]' : 'bg-[#252525]')
      }
    >
      <span className={'text-xs font-medium text-center line-clamp-2 leading-tight ' + (isSkipped ? 'text-[#737373]' : 'text-white')}>
        {label}
      </span>
      {isSkipped && (
        <span className="text-[9px] font-medium uppercase tracking-wide text-[#737373] mt-0.5">
          Omitido
        </span>
      )}
    </button>
  );
};

export default ExerciseCard;
