import React from 'react';
import TacticalModal from './TacticalModal';
import Button from './Button';

interface TacticalDataEntryProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: () => void;
  title: string;
  submitLabel?: string;
  children: React.ReactNode;
  className?: string;
}

const TacticalDataEntry: React.FC<TacticalDataEntryProps> = ({
  isOpen,
  onClose,
  onSubmit,
  title,
  submitLabel = 'Guardar',
  children,
  className = '',
}) => (
  <TacticalModal
    isOpen={isOpen}
    onClose={onClose}
    title={title}
    variant="default"
    useCustomContent
    className={className}
  >
    <div className="flex flex-col flex-grow min-h-0">
      <div className="flex-grow overflow-y-auto custom-scrollbar px-5 py-4 text-left">
        <div className="space-y-4 [&_input]:font-mono [&_input]:bg-[#0A0B0E] [&_input]:border [&_input]:border-[#2A2D38] [&_input]:rounded-sm [&_input]:px-3 [&_input]:py-2 [&_input]:text-white [&_input]:placeholder-[#A0A7B8]/60 [&_input]:focus:border-[#00F0FF] [&_input]:outline-none [&_textarea]:font-mono [&_textarea]:bg-[#0A0B0E] [&_textarea]:border [&_textarea]:border-[#2A2D38] [&_textarea]:rounded-sm [&_textarea]:px-3 [&_textarea]:py-2 [&_textarea]:text-white [&_textarea]:placeholder-[#A0A7B8]/60 [&_textarea]:focus:border-[#00F0FF] [&_textarea]:outline-none [&_label]:text-[#A0A7B8] [&_label]:text-xs [&_label]:font-mono [&_label]:uppercase [&_label]:tracking-wider">
          {children}
        </div>
      </div>
      <div className="flex-shrink-0 px-5 py-4 border-t border-[#2A2D38] flex gap-3">
        <Button
          variant="secondary"
          onClick={onClose}
          className="flex-1 !border-[#2A2D38] !bg-transparent !text-[#A0A7B8] hover:!bg-[#2A2D38] font-mono"
        >
          Cancelar
        </Button>
        <Button
          onClick={onSubmit}
          className="flex-1 !bg-[#00F0FF] hover:!bg-[#00F0FF]/90 !border-[#00F0FF] !text-black font-mono"
        >
          {submitLabel}
        </Button>
      </div>
    </div>
  </TacticalModal>
);

export default TacticalDataEntry;
