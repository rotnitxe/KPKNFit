// components/workout/ExerciseCard.tsx
// Tarjeta horizontal de carrusel inspirada en M3 (outlined) + glass.

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

  const subtitle = exercises.length > 1
    ? `${exercises.length} ejercicios`
    : 'Bloque de trabajo';

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
        'workout-pressable workout-surface-card flex-shrink-0 w-[186px] min-h-[78px] px-4 py-3 text-left rounded-[12px] border transition-all duration-200 active:scale-[0.98] ' +
        (isSkipped
          ? 'opacity-65 bg-[var(--md-sys-color-surface-container)] border-[var(--md-sys-color-outline-variant)]'
          : isActive
            ? 'bg-[var(--md-sys-color-secondary-container)] border-[var(--md-sys-color-outline)] shadow-[0_8px_24px_rgba(0,0,0,0.10)]'
            : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] hover:border-[var(--md-sys-color-outline)]')
      }
    >
      <span className={'block text-[15px] leading-5 font-medium line-clamp-2 ' + (isSkipped ? 'text-[var(--md-sys-color-on-surface-variant)]' : 'text-[var(--md-sys-color-on-surface)]')}>
        {label}
      </span>
      <span className="block mt-1 text-[12px] leading-4 text-[var(--md-sys-color-on-surface-variant)]">
        {isSkipped ? 'Omitido' : subtitle}
      </span>
    </button>
  );
};

export default ExerciseCard;

