import React from 'react';
import TacticalModal from './TacticalModal';
import Button from './Button';
import { AlertTriangleIcon } from '../icons';

export type ConfirmVariant = 'default' | 'destructive' | 'pr';

interface TacticalConfirmProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmVariant;
}

const TacticalConfirm: React.FC<TacticalConfirmProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  variant = 'default',
}) => {
  const modalVariant =
    variant === 'destructive' ? 'failure' : variant === 'pr' ? 'pr' : 'default';

  const handleConfirm = () => {
    onConfirm();
    onClose();
  };

  const confirmBtnClass =
    variant === 'destructive'
      ? 'flex-1 bg-[#FF2E43] hover:bg-[#FF2E43]/90 border-[#FF2E43] text-white font-mono'
      : variant === 'pr'
        ? 'flex-1 bg-[#FF7B00] hover:bg-[#FF7B00]/90 border-[#FF7B00] text-white font-mono'
        : 'flex-1 bg-[#00F0FF] hover:bg-[#00F0FF]/90 border-[#00F0FF] text-black font-mono';

  return (
    <TacticalModal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      variant={modalVariant}
    >
      <div className="space-y-5 text-left">
        {variant === 'destructive' && (
          <div className="flex justify-start">
            <AlertTriangleIcon size={32} className="text-[#FF2E43]" />
          </div>
        )}
        <p className="text-[#A0A7B8] text-sm font-mono leading-relaxed">{message}</p>
        <div className="flex gap-3 pt-2">
          <Button
            variant="secondary"
            onClick={onClose}
            className="flex-1 border-[#2A2D38] bg-transparent text-[#A0A7B8] hover:bg-[#2A2D38] font-mono"
          >
            {cancelLabel}
          </Button>
          <Button onClick={handleConfirm} className={confirmBtnClass}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </TacticalModal>
  );
};

export default TacticalConfirm;
