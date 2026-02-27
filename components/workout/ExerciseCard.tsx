// components/workout/ExerciseCard.tsx
// Tarjeta compacta para el carrusel de ejercicios

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
  color,
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
    const timer = setTimeout(() => onLongPress(), 500);
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
        'flex-shrink-0 w-36 min-h-[56px] rounded-xl border-2 px-3 py-2.5 flex flex-col items-center justify-center gap-0.5 transition-all duration-200 active:scale-95 ' +
        (isSkipped ? 'opacity-60 border-slate-600 bg-slate-800/50' : '')
      }
      style={{
        borderColor: isSkipped ? '#475569' : (isActive ? color : `${color}40`),
        backgroundColor: isSkipped ? 'rgba(30,41,59,0.5)' : (isActive ? `${color}15` : 'rgba(15,23,42,0.8)'),
        boxShadow: isActive && !isSkipped ? `0 0 24px ${color}50, 0 0 48px ${color}25` : undefined,
      }}
    >
      <span className={'text-xs font-bold text-center line-clamp-2 leading-tight ' + (isSkipped ? 'text-slate-500' : 'text-white')}>
        {label}
      </span>
      {isSkipped && (
        <span className="text-[9px] font-black uppercase tracking-widest text-slate-500 mt-0.5">
          Omitido
        </span>
      )}
    </button>
  );
};

export default ExerciseCard;
