import React from 'react';
import type { Exercise, ExerciseSet } from '../../types';

interface ExerciseCardProps {
  exercises: Exercise[];
  color: string;
  isActive: boolean;
  isSkipped?: boolean;
  onPress: () => void;
  onLongPress: () => void;
}

const formatIntensity = (exercise: Exercise, set?: ExerciseSet): string | null => {
  if (!set) return null;
  if (set.isAmrap || set.intensityMode === 'amrap') {
    return set.isCalibrator ? 'AMRAP' : 'Fallo';
  }
  if (set.intensityMode === 'failure') return 'Fallo';
  if (set.intensityMode === 'rir' && set.targetRIR != null) return `RIR ${set.targetRIR}`;
  if (exercise.trainingMode === 'percent' && set.intensityMode === 'load' && set.targetPercentageRM != null) {
    return `${Math.round(set.targetPercentageRM)}% 1RM`;
  }
  if (set.targetRPE != null) return `RPE ${set.targetRPE}`;
  if (set.targetRIR != null) return `RIR ${set.targetRIR}`;
  if (set.targetPercentageRM != null) return `${Math.round(set.targetPercentageRM)}% 1RM`;
  return null;
};

const formatPrescription = (exercise?: Exercise): string => {
  if (!exercise || !exercise.sets || exercise.sets.length === 0) return 'Sin prescripcion';

  const count = exercise.sets.length;
  const leadSet = exercise.sets[0];
  let volume = `${count} series`;

  if (exercise.trainingMode === 'time' && leadSet.targetDuration != null) {
    volume = `${count}x${leadSet.targetDuration}s`;
  } else if (exercise.trainingMode === 'custom' && exercise.customUnit) {
    const customTarget = leadSet.targetDuration ?? leadSet.targetReps;
    volume = customTarget != null ? `${count}x${customTarget} ${exercise.customUnit}` : `${count} series`;
  } else if (leadSet.targetReps != null) {
    volume = `${count}x${leadSet.targetReps}`;
  }

  const intensity = formatIntensity(exercise, leadSet);
  return intensity ? `${volume} @ ${intensity}` : volume;
};

const ExerciseCard: React.FC<ExerciseCardProps> = ({
  exercises,
  color,
  isActive,
  isSkipped = false,
  onPress,
  onLongPress,
}) => {
  const primaryExercise = exercises[0];
  const label = exercises.length > 1
    ? exercises.map((exercise) => exercise.name).join(' • ')
    : primaryExercise?.name ?? '';
  const subtitle = exercises.length > 1
    ? `Bloque de ${exercises.length} ejercicios`
    : formatPrescription(primaryExercise);
  const secondaryMeta = exercises.length > 1
    ? formatPrescription(primaryExercise)
    : `${primaryExercise?.isUnilateral ? 'Unilateral' : 'Trabajo principal'}${primaryExercise?.restTime ? ` • ${primaryExercise.restTime}s descanso` : ''}`;

  const handleContextMenu = (event: React.MouseEvent) => {
    event.preventDefault();
    onLongPress();
  };

  const handleTouchStart = (event: React.TouchEvent) => {
    const timer = setTimeout(() => onLongPress(), 500) as unknown as number;
    (event.currentTarget as HTMLButtonElement & { _longPressTimer?: number })._longPressTimer = timer;
  };

  const clearTimer = (event: React.TouchEvent) => {
    const element = event.currentTarget as HTMLButtonElement & { _longPressTimer?: number };
    if (element._longPressTimer) {
      clearTimeout(element._longPressTimer);
      element._longPressTimer = undefined;
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
        'workout-pressable workout-surface-card relative flex-shrink-0 w-[208px] min-h-[108px] overflow-hidden rounded-[28px] border px-4 py-4 text-left transition-all duration-200 active:scale-[0.985] ' +
        (isSkipped
          ? 'opacity-70 bg-[var(--md-sys-color-surface-container)] border-[var(--md-sys-color-outline-variant)]'
          : isActive
            ? 'bg-[var(--md-sys-color-secondary-container)] border-white/80 shadow-[0_18px_34px_rgba(112,91,69,0.16)]'
            : 'bg-[var(--md-sys-color-surface)] border-[var(--md-sys-color-outline-variant)] hover:border-[var(--md-sys-color-outline)]')
      }
    >
      <div
        className="absolute left-4 right-4 top-0 h-[3px] rounded-b-full opacity-90"
        style={{ background: `linear-gradient(90deg, ${color}, rgba(255,255,255,0.8))` }}
      />

      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <span className={'block text-[18px] leading-5 font-medium tracking-[-0.02em] line-clamp-2 ' + (isSkipped ? 'text-[var(--md-sys-color-on-surface-variant)]' : 'text-[var(--md-sys-color-on-surface)]')}>
            {label}
          </span>
          <span className="mt-1 block text-[13px] leading-4 text-[var(--md-sys-color-on-surface-variant)]">
            {isSkipped ? 'Omitido' : subtitle}
          </span>
        </div>
        {primaryExercise?.isUnilateral && !isSkipped && (
          <span className="shrink-0 rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/60 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-[var(--md-sys-color-primary)]">
            Uni
          </span>
        )}
      </div>

      <div className="mt-auto pt-3">
        <span className="inline-flex max-w-full items-center rounded-full border border-[var(--md-sys-color-outline-variant)]/80 bg-white/55 px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--md-sys-color-on-surface-variant)]">
          <span className="truncate">{secondaryMeta}</span>
        </span>
      </div>
    </button>
  );
};

export default ExerciseCard;
