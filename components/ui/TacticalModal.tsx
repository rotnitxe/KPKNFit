import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { XIcon } from '../icons';
import TacticalBackdrop from './TacticalBackdrop';

export type TacticalVariant = 'default' | 'failure' | 'pr';

interface TacticalModalProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  variant?: TacticalVariant;
  className?: string;
  useCustomContent?: boolean;
}

const TacticalModal: React.FC<TacticalModalProps> = ({
  isOpen,
  onClose,
  children,
  title,
  variant = 'default',
  className = '',
  useCustomContent = false,
}) => {
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setShow(true);
      const handleKeyDown = (e: KeyboardEvent) => {
        if (e.key === 'Escape') onClose();
      };
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    } else {
      const timer = setTimeout(() => setShow(false), 200);
      return () => clearTimeout(timer);
    }
  }, [isOpen, onClose]);

  if (!isOpen && !show) return null;

  const borderClass =
    variant === 'failure'
      ? 'tactical-modal-critical-failure'
      : variant === 'pr'
        ? 'tactical-modal-critical-pr'
        : '';

  return ReactDOM.createPortal(
    <div
      className={`fixed inset-0 z-[200] flex items-center justify-center p-4 sm:p-6 transition-opacity duration-200 ease-out
                  ${isOpen ? 'opacity-100' : 'opacity-0'}`}
      role="dialog"
      aria-modal="true"
    >
      <TacticalBackdrop onClick={onClose} variant="modal" />

      <div
        className={`
          relative z-10 w-full max-w-md tactical-modal-base
          flex flex-col max-h-[85vh]
          transform transition-all duration-200 ease-out
          ${isOpen ? 'animate-tactical-enter' : 'animate-tactical-exit opacity-0'}
          ${borderClass}
          ${className}
        `}
      >
        {title !== undefined && (
          <div className="flex items-center justify-between px-5 py-4 flex-shrink-0 border-b border-[#2A2D38]">
            <h2
              id="tactical-modal-title"
              className="text-base font-bold text-white font-mono text-left"
            >
              {title}
            </h2>
            <button
              onClick={onClose}
              className="p-1.5 rounded-sm text-[#A0A7B8] hover:bg-[#2A2D38] hover:text-white transition-colors duration-200"
              aria-label="Cerrar"
            >
              <XIcon size={18} />
            </button>
          </div>
        )}

        {useCustomContent ? (
          <div className="flex-grow min-h-0 flex flex-col font-mono">
            {children}
          </div>
        ) : (
          <div
            className={`overflow-y-auto px-5 pb-6 custom-scrollbar font-mono ${
              title === undefined ? 'pt-6' : 'pt-5'
            }`}
          >
            {children}
          </div>
        )}
      </div>
    </div>,
    document.body
  );
};

export default TacticalModal;
