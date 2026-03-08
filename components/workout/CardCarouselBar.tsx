// components/workout/CardCarouselBar.tsx
// Carrusel horizontal con drag nativo táctil (sigue el dedo en tiempo real)

import React, { useRef, useEffect, useState, useCallback } from 'react';
import type { Exercise } from '../../types';
import ExerciseCard from './ExerciseCard';
import FinishCard from './FinishCard';
import { DragHandleIcon } from '../icons';
import { hapticImpact } from '../../services/hapticsService';
import { ImpactStyle } from '../../services/hapticsService';

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

const DRAG_ACTIVATION_DELAY_MS = 120;
const DRAG_ACTIVATION_DISTANCE_PX = 5;

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
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dragPosition, setDragPosition] = useState<{ x: number; y: number } | null>(null);
  const [dragSourceIndex, setDragSourceIndex] = useState<number>(0);
  const dragStateRef = useRef<{
    active: boolean;
    activationTimer: ReturnType<typeof setTimeout> | null;
    startX: number;
    startY: number;
    pointerId: number;
    itemId: string;
    sourceIndex: number;
  } | null>(null);
  const dragJustEndedRef = useRef(false);

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

  const getDropIndex = useCallback((clientX: number): number => {
    const exerciseItems = items.filter((it): it is CarouselItem => it.type === 'exercise');
    if (exerciseItems.length === 0) return 0;

    let bestIdx = 0;
    let bestDist = Infinity;
    for (let i = 0; i < exerciseItems.length; i++) {
      const el = cardRefs.current.get(exerciseItems[i].firstExerciseId);
      if (!el) continue;
      const rect = el.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const dist = Math.abs(clientX - centerX);
      if (dist < bestDist) {
        bestDist = dist;
        bestIdx = i;
      }
    }
    return bestIdx;
  }, [items]);

  const handlePointerDown = useCallback((e: React.PointerEvent, itemId: string, index: number) => {
    if (!onReorder || itemId === 'finish') return;
    e.preventDefault();
    e.stopPropagation();
    dragStateRef.current = {
      active: false,
      activationTimer: setTimeout(() => {
        if (dragStateRef.current) {
          dragStateRef.current.active = true;
          setDraggingId(itemId);
          setDragSourceIndex(index);
          setDragPosition({ x: dragStateRef.current.startX, y: dragStateRef.current.startY });
          hapticImpact(ImpactStyle.Light);
        }
      }, DRAG_ACTIVATION_DELAY_MS),
      startX: e.clientX,
      startY: e.clientY,
      pointerId: e.pointerId,
      itemId,
      sourceIndex: index,
    };
  }, [onReorder]);

  useEffect(() => {
    const handlePointerMove = (e: PointerEvent) => {
      const state = dragStateRef.current;
      if (!state) return;

      const dx = Math.abs(e.clientX - state.startX);
      const dy = Math.abs(e.clientY - state.startY);
      if (!state.active && (dx > DRAG_ACTIVATION_DISTANCE_PX || dy > DRAG_ACTIVATION_DISTANCE_PX)) {
        if (state.activationTimer) {
          clearTimeout(state.activationTimer);
          state.activationTimer = null;
        }
        state.active = true;
        setDraggingId(state.itemId);
        setDragSourceIndex(state.sourceIndex);
        setDragPosition({ x: e.clientX, y: e.clientY });
        hapticImpact(ImpactStyle.Light);
      }

      if (state.active) {
        e.preventDefault();
        setDragPosition({ x: e.clientX, y: e.clientY });
      }
    };

    const handlePointerUp = (e: PointerEvent) => {
      const state = dragStateRef.current;
      if (!state) return;

      if (state.activationTimer) {
        clearTimeout(state.activationTimer);
      }

      if (state.active && onReorder) {
        e.preventDefault();
        dragJustEndedRef.current = true;
        setTimeout(() => { dragJustEndedRef.current = false; }, 280);
        const destIndex = getDropIndex(e.clientX);
        const exerciseItems = items.filter((it): it is CarouselItem => it.type === 'exercise');
        if (destIndex !== state.sourceIndex && destIndex >= 0 && destIndex < exerciseItems.length) {
          const newItems = Array.from(items);
          const finishItem = newItems.pop();
          const [removed] = newItems.splice(state.sourceIndex, 1);
          newItems.splice(destIndex, 0, removed);
          if (finishItem) newItems.push(finishItem);
          onReorder(newItems);
          hapticImpact(ImpactStyle.Light);
        }
      }

      dragStateRef.current = null;
      setDraggingId(null);
      setDragPosition(null);
    };

    window.addEventListener('pointermove', handlePointerMove, { capture: true, passive: false });
    window.addEventListener('pointerup', handlePointerUp, { capture: true });
    window.addEventListener('pointercancel', handlePointerUp, { capture: true });
    return () => {
      window.removeEventListener('pointermove', handlePointerMove, { capture: true });
      window.removeEventListener('pointerup', handlePointerUp, { capture: true });
      window.removeEventListener('pointercancel', handlePointerUp, { capture: true });
    };
  }, [items, onReorder, getDropIndex]);

  const draggedItem = draggingId ? items.find((it): it is CarouselItem => it.type === 'exercise' && it.firstExerciseId === draggingId) : null;

  return (
    <div
      ref={scrollRef}
      className={`flex overflow-x-auto gap-3 px-4 py-3 no-scrollbar scroll-smooth select-none ${draggingId ? 'overflow-hidden touch-none' : ''}`}
      style={{
        scrollbarWidth: 'none',
        WebkitOverflowScrolling: 'touch',
        touchAction: draggingId ? 'none' : 'pan-x',
      }}
    >
      {items.map((item, idx) => {
        if (item.type === 'finish') {
          return (
            <div key="finish" ref={(el) => { if (el) cardRefs.current.set('finish', el); }} className="flex-shrink-0">
              <FinishCard
                isExpanded={finishCardExpanded}
                onPress={() => { }}
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
        const isDragging = draggingId === item.firstExerciseId;

        return (
          <div
            key={item.firstExerciseId}
            ref={(el) => {
              if (el) cardRefs.current.set(item.firstExerciseId, el);
            }}
            className={`flex items-center gap-1 flex-shrink-0 transition-opacity duration-75 ${isDragging ? 'opacity-30' : ''}`}
          >
            {onReorder && (
              <div
                className="p-2 -m-2 text-[#737373] hover:text-white shrink-0 touch-manipulation"
                style={{ touchAction: 'none' }}
                onPointerDown={(e) => handlePointerDown(e, item.firstExerciseId, idx)}
                title="Mantén y arrastra para reordenar"
              >
                <DragHandleIcon size={16} />
              </div>
            )}
            <ExerciseCard
              exercises={item.exercises}
              color={item.color}
              isActive={isActive}
              isSkipped={isSkipped}
              onPress={() => {
                if (dragJustEndedRef.current) return;
                onSelectExercise(item.firstExerciseId);
              }}
              onLongPress={() => onLongPressExercise(item)}
            />
          </div>
        );
      })}

      {/* Clon flotante que sigue el dedo */}
      {draggedItem && dragPosition && (
        <div
          className="fixed z-[9999] pointer-events-none will-change-transform"
          style={{
            left: dragPosition.x,
            top: dragPosition.y,
            transform: 'translate(-50%, -50%)',
            width: 144,
          }}
        >
          <div className="scale-105 origin-center bg-[#3f3f3f] p-3 flex flex-col items-center justify-center min-h-[56px]">
            <span className="text-xs font-medium text-center line-clamp-2 leading-tight text-white">
              {draggedItem.exercises.length > 1
                ? draggedItem.exercises.map(e => e.name).join(' • ')
                : draggedItem.exercises[0]?.name ?? ''}
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

export default CardCarouselBar;
