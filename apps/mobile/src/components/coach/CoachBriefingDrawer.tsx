import React from 'react';
import CoachBriefingModal from './CoachBriefingModal';
import type { TacticalVariant } from '@/components/ui/TacticalModal';

interface CoachBriefingDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  briefing: string;
  variant?: TacticalVariant;
}

export function CoachBriefingDrawer({ isOpen, onClose, briefing, variant = 'sheet' }: CoachBriefingDrawerProps) {
  return <CoachBriefingModal isOpen={isOpen} onClose={onClose} briefing={briefing} variant={variant} />;
}

export default CoachBriefingDrawer;

