// ReadinessCheckModal.tsx
// Wrapper around ReadinessModal that persists the check-in to wellbeing store.
import React, { useCallback } from 'react';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { ReadinessModal } from '../workout/ReadinessModal';

interface Props {
  visible: boolean;
  onClose: () => void;
}

export const ReadinessCheckModal: React.FC<Props> = ({ visible, onClose }) => {
  const addDailyCheckIn = useWellbeingStore(state => state.addDailyCheckIn);

  const handleComplete = useCallback(
    (data: { sleep: number; mood: number; soreness: number; stress: number; motivation: number }) => {
      const todayDate = new Date().toISOString().split('T')[0];
      const moodStateMap: Record<number, 'happy' | 'neutral' | 'sad' | 'anxious' | 'energetic'> = {
        1: 'sad',
        2: 'anxious',
        3: 'neutral',
        4: 'happy',
        5: 'energetic',
      };
      const logData = {
        date: todayDate,
        sleepQuality: data.sleep,
        stressLevel: data.stress,
        doms: data.soreness,
        motivation: data.motivation,
        moodState: moodStateMap[data.mood],
      };
      void addDailyCheckIn(logData);
      onClose();
    },
    [addDailyCheckIn, onClose]
  );

  return (
    <ReadinessModal
      visible={visible}
      onClose={onClose}
      onComplete={handleComplete}
    />
  );
};
