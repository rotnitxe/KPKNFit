// components/workout/CardCarouselBar.tsx
// Carrusel horizontal de tarjetas de ejercicios

import React, { useRef, useEffect } from 'react';
import type { Exercise } from '../../types';
import ExerciseCard from './ExerciseCard';
import FinishCard from './FinishCard';

export interface CarouselItem {
  type: 'exercise';
  exercises: Exercise[];
  color: string;
  firstExerciseId: string;
}

export interface CarouselFinishItem {
  type: 'finish';
}

export type CarouselItemType = CarouselItem | CarouselFinishItem;

interface CardCarouselBarProps {
  items: CarouselItemType[];
  activeExerciseId: string | null;
  skippedIds: Set<string>;
  onSelectExercise: (exerciseId: string) => void;
  onLongPressExercise: (item: CarouselItem) => void;
  onFinishCardExpand: () => void;
  onFinish: () => void;
  durationMinutes?: number;
  completedSetsCount?: number;
  totalSetsCount?: number;
  totalTonnage?: number;
  finishCardExpanded?: boolean;
}

const CardCarouselBar: React.FC<CardCarouselBarProps> = ({
  items,
  activeExerciseId,
  skippedIds,
  onSelectExercise,
  onLongPressExercise,
  onFinishCardExpand,
  onFinish,
  durationMinutes = 0,
  completedSetsCount = 0,
  totalSetsCount = 0,
  totalTonnage = 0,
  finishCardExpanded = false,
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);
  const cardRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  useEffect(() => {
    if (!activeExerciseId || !scrollRef.current) return;
    const firstExItem = items.find(
      (it): it is CarouselItem => it.type === 'exercise' && it.firstExerciseId === activeExerciseId
    );
    if (firstExItem) {
      const el = cardRefs.current.get(firstExItem.firstExerciseId);
      el?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
    }
  }, [activeExerciseId, items]);

  return (
    <div
      ref={scrollRef}
      className="flex overflow-x-auto gap-3 px-4 py-3 no-scrollbar scroll-smooth"
      style={{ scrollbarWidth: 'none', WebkitOverflowScrolling: 'touch' }}
    >
      {items.map((item, idx) => {
        if (item.type === 'finish') {
          return (
            <FinishCard
              key="finish"
              isExpanded={finishCardExpanded}
              onPress={() => {}}
              onExpand={onFinishCardExpand}
              onFinish={onFinish}
              durationMinutes={durationMinutes}
              completedSetsCount={completedSetsCount}
              totalSetsCount={totalSetsCount}
              totalTonnage={totalTonnage}
            />
          );
        }

        const exIds = item.exercises.map(e => e.id);
        const isSkipped = exIds.some(id => skippedIds.has(id));
        const isActive = exIds.includes(activeExerciseId ?? '');

        return (
          <div
            key={item.firstExerciseId}
            ref={(el) => {
              if (el) cardRefs.current.set(item.firstExerciseId, el);
            }}
          >
            <ExerciseCard
              exercises={item.exercises}
              color={item.color}
              isActive={isActive}
              isSkipped={isSkipped}
              onPress={() => onSelectExercise(item.firstExerciseId)}
              onLongPress={() => onLongPressExercise(item)}
            />
          </div>
        );
      })}
    </div>
  );
};

export default CardCarouselBar;
