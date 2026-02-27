// components/workout/CardCarouselBar.tsx
// Carrusel horizontal de tarjetas de ejercicios con drag-and-drop

import React, { useRef, useEffect } from 'react';
import { DragDropContext, Droppable, Draggable, DropResult } from '@hello-pangea/dnd';
import type { Exercise } from '../../types';
import ExerciseCard from './ExerciseCard';
import FinishCard from './FinishCard';
import { DragHandleIcon } from '../icons';

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
  onReorder?: (newItems: CarouselItemType[]) => void;
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
  onReorder,
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

  const handleDragEnd = (result: DropResult) => {
    if (!onReorder || !result.destination) return;
    if (result.source.index === result.destination.index) return;
    const newItems = Array.from(items);
    const [removed] = newItems.splice(result.source.index, 1);
    newItems.splice(result.destination.index, 0, removed);
    onReorder(newItems);
  };

  const renderCarousel = () => (
    <div
      ref={scrollRef}
      className="flex overflow-x-auto gap-3 px-4 py-3 no-scrollbar scroll-smooth"
      style={{ scrollbarWidth: 'none', WebkitOverflowScrolling: 'touch' }}
    >
      {items.map((item, idx) => {
        if (item.type === 'finish') {
          return (
            <div key="finish" ref={(el) => el && cardRefs.current.set('finish', el)}>
              <FinishCard
                isExpanded={finishCardExpanded}
                onPress={() => {}}
                onExpand={onFinishCardExpand}
                onFinish={onFinish}
                durationMinutes={durationMinutes}
                completedSetsCount={completedSetsCount}
                totalSetsCount={totalSetsCount}
                totalTonnage={totalTonnage}
              />
            </div>
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
            className="flex items-center gap-1 flex-shrink-0"
          >
            {onReorder && (
              <div className="cursor-grab active:cursor-grabbing touch-none p-1 rounded text-slate-500 hover:text-slate-400 shrink-0" title="Arrastrar para reordenar">
                <DragHandleIcon size={14} />
              </div>
            )}
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

  if (onReorder) {
    return (
      <DragDropContext onDragEnd={handleDragEnd}>
        <Droppable droppableId="carousel" direction="horizontal">
          {(provided) => (
            <div
              ref={(el) => {
                (scrollRef as React.MutableRefObject<HTMLDivElement | null>).current = el;
                provided.innerRef(el);
              }}
              {...provided.droppableProps}
              className="flex overflow-x-auto gap-3 px-4 py-3 no-scrollbar scroll-smooth"
              style={{ scrollbarWidth: 'none', WebkitOverflowScrolling: 'touch' }}
            >
              {items.map((item, idx) => {
                if (item.type === 'finish') {
                  return (
                    <Draggable key="finish" draggableId="finish" index={idx} isDragDisabled>
                      {(provided) => (
                        <div
                          ref={(el) => {
                            provided.innerRef(el);
                            if (el) cardRefs.current.set('finish', el);
                          }}
                          {...provided.draggableProps}
                          className="flex-shrink-0"
                        >
                          <FinishCard
                            isExpanded={finishCardExpanded}
                            onPress={() => {}}
                            onExpand={onFinishCardExpand}
                            onFinish={onFinish}
                            durationMinutes={durationMinutes}
                            completedSetsCount={completedSetsCount}
                            totalSetsCount={totalSetsCount}
                            totalTonnage={totalTonnage}
                          />
                        </div>
                      )}
                    </Draggable>
                  );
                }

                const exIds = item.exercises.map(e => e.id);
                const isSkipped = exIds.some(id => skippedIds.has(id));
                const isActive = exIds.includes(activeExerciseId ?? '');

                return (
                  <Draggable key={item.firstExerciseId} draggableId={item.firstExerciseId} index={idx}>
                    {(provided, snapshot) => (
                      <div
                        ref={(el) => {
                          provided.innerRef(el);
                          if (el) cardRefs.current.set(item.firstExerciseId, el);
                        }}
                        {...provided.draggableProps}
                        className={`flex items-center gap-1 flex-shrink-0 ${snapshot.isDragging ? 'opacity-80 z-50' : ''}`}
                      >
                        <div {...provided.dragHandleProps} className="cursor-grab active:cursor-grabbing touch-none p-1 rounded text-slate-500 hover:text-slate-400 shrink-0" title="Arrastrar para reordenar">
                          <DragHandleIcon size={14} />
                        </div>
                        <ExerciseCard
                          exercises={item.exercises}
                          color={item.color}
                          isActive={isActive}
                          isSkipped={isSkipped}
                          onPress={() => onSelectExercise(item.firstExerciseId)}
                          onLongPress={() => onLongPressExercise(item)}
                        />
                      </div>
                    )}
                  </Draggable>
                );
              })}
              {provided.placeholder}
            </div>
          )}
        </Droppable>
      </DragDropContext>
    );
  }

  return renderCarousel();
};

export default CardCarouselBar;
