
import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { XIcon } from '../icons';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  className?: string;
  useCustomContent?: boolean; // NEW: If true, removes default padding/scroll wrapper
}

const Modal: React.FC<ModalProps> = ({ isOpen, onClose, children, title, className = '', useCustomContent = false }) => {
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
        const timer = setTimeout(() => setShow(false), 300);
        return () => clearTimeout(timer);
    }
  }, [isOpen, onClose]);

  if (!isOpen && !show) return null;

  return ReactDOM.createPortal(
    <div
      className={`fixed inset-0 z-[200] flex items-center justify-center p-4 sm:p-6 transition-all duration-300 ease-out
                 ${isOpen ? 'opacity-100' : 'opacity-0'}`}
      role="dialog"
      aria-modal="true"
    >
      {/* Deep Darkened Backdrop with blur */}
      <div
        className="absolute inset-0 bg-black/80 backdrop-blur-sm transition-opacity duration-300 animate-fade-in-backdrop"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Elegant Glass Content with Entrance Animation */}
      <div
        className={`relative z-10 w-full max-w-md 
                   bg-[#0a0a0a]
                   rounded-3xl shadow-2xl ring-1 ring-white/10
                   flex flex-col max-h-[85vh]
                   transform transition-all duration-300 cubic-bezier(0.16, 1, 0.3, 1)
                   ${isOpen ? 'scale-100 translate-y-0 animate-modal-enter' : 'scale-95 translate-y-4'}
                   ${className}`}
      >
        {/* Subtle Gradient Glow at Top */}
        <div className="absolute top-0 inset-x-0 h-32 bg-gradient-to-b from-white/5 to-transparent pointer-events-none rounded-t-3xl z-0" />

        {title && (
          <div className="flex items-center justify-between px-6 py-5 flex-shrink-0 relative z-20 border-b border-white/5 bg-[#0a0a0a]/50 backdrop-blur-xl rounded-t-3xl">
            <h2 id="modal-title" className="text-lg font-bold text-white tracking-tight">
              {title}
            </h2>
            <button
              onClick={onClose}
              className="p-1.5 rounded-full text-slate-500 hover:bg-white/10 hover:text-white transition-all active:scale-90"
              aria-label="Cerrar"
            >
              <XIcon size={18} />
            </button>
          </div>
        )}
        
        {/* Content Area: Either default scrollable or custom layout */}
        {useCustomContent ? (
            <div className="flex-grow min-h-0 relative z-10 flex flex-col">
                {children}
            </div>
        ) : (
            <div className={`overflow-y-auto px-6 pb-8 custom-scrollbar relative z-10 ${!title ? 'pt-8' : ''}`}>
                {children}
            </div>
        )}
      </div>
    </div>,
    document.body
  );
};

export default Modal;
