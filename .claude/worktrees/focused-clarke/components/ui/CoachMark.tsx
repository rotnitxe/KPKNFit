
// components/ui/CoachMark.tsx
import React, { useState, useEffect } from 'react';
import ReactDOM from 'react-dom';
import { XIcon, LightbulbIcon } from '../icons';
import Button from './Button';

interface CoachMarkProps {
  title: string;
  description: string;
  onClose: () => void;
  position?: 'top' | 'bottom' | 'center';
}

const CoachMark: React.FC<CoachMarkProps> = ({ title, description, onClose, position = 'bottom' }) => {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setIsVisible(true), 500);
    return () => clearTimeout(timer);
  }, []);

  const handleClose = () => {
      setIsVisible(false);
      setTimeout(onClose, 300);
  }

  const positionClasses = {
      top: 'top-20',
      bottom: 'bottom-28',
      center: 'top-1/2 -translate-y-1/2'
  };

  const content = (
    <div 
        className={`fixed left-4 right-4 z-[250] ${positionClasses[position]} transition-all duration-500 transform ${isVisible ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-4 scale-95'} pointer-events-none`}
    >
      <div className="bg-gradient-to-br from-indigo-900/95 to-slate-900/98 backdrop-blur-2xl border border-indigo-500/40 rounded-3xl p-5 shadow-[0_20px_50px_rgba(0,0,0,0.5)] relative overflow-hidden pointer-events-auto">
          <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-indigo-400 to-transparent opacity-50"></div>
          
          <div className="flex items-start gap-4">
              <div className="p-3 bg-indigo-500/20 rounded-2xl text-indigo-300 flex-shrink-0 animate-pulse">
                  <LightbulbIcon size={24} />
              </div>
              <div className="flex-grow">
                  <h4 className="font-black text-white text-lg mb-1 uppercase tracking-tight">{title}</h4>
                  <p className="text-slate-300 text-sm leading-relaxed font-medium">{description}</p>
              </div>
              <button onClick={handleClose} className="p-1 text-slate-500 hover:text-white transition-colors">
                  <XIcon size={20} />
              </button>
          </div>
          <div className="mt-4 flex justify-end">
              <Button onClick={handleClose} className="!py-2 !px-6 !text-[10px] uppercase font-black !bg-indigo-600 hover:!bg-indigo-500 border-none shadow-lg shadow-indigo-900/40">
                  Entendido
              </Button>
          </div>
      </div>
    </div>
  );

  return ReactDOM.createPortal(content, document.body);
};

export default CoachMark;
