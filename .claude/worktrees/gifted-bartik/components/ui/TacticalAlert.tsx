import React from 'react';
import TacticalModal from './TacticalModal';
import { AlertTriangleIcon } from '../icons';

export type AlertVariant = 'failure' | 'pr' | 'default';

interface TacticalAlertProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  message: string;
  variant?: AlertVariant;
}

const TacticalAlert: React.FC<TacticalAlertProps> = ({
  isOpen,
  onClose,
  title,
  message,
  variant = 'default',
}) => {
  const modalVariant = variant === 'failure' ? 'failure' : variant === 'pr' ? 'pr' : 'default';

  return (
    <TacticalModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      variant={modalVariant}
    >
      <div className="flex items-start gap-4 text-left">
        {variant === 'failure' && (
          <div className="flex-shrink-0 text-[#FF2E43]">
            <AlertTriangleIcon size={24} />
          </div>
        )}
        <p className="text-[#A0A7B8] text-sm font-mono leading-relaxed">{message}</p>
      </div>
    </TacticalModal>
  );
};

export default TacticalAlert;
