import React, { useState, useEffect } from 'react';
import { CoachIcon } from '../icons';
import Button from './Button';

interface FloatingActionButtonProps {
  onClick: () => void;
  isHidden?: boolean;
}

const FloatingActionButton: React.FC<FloatingActionButtonProps> = ({ onClick, isHidden }) => {
  const [theme, setTheme] = useState('default');
  
  useEffect(() => {
    // Safely set the theme once the component is mounted to avoid accessing document.body before it exists
    setTheme(document.body.dataset.theme || 'default');

    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'attributes' && mutation.attributeName === 'data-theme') {
          setTheme((mutation.target as HTMLElement).dataset.theme || 'default');
        }
      });
    });
    observer.observe(document.body, { attributes: true });
    return () => observer.disconnect();
  }, []);
  
  if (isHidden) {
    return null;
  }

  if (theme === 'prime-next') {
    return (
        <div className="fab-coach animate-fade-in-up">
            <button 
                onClick={onClick} 
                className="btn-prime-next primary flex items-center justify-center gap-2 !rounded-full !p-4 shadow-lg shadow-blue-900/50"
                aria-label="Consultar al Coach"
            >
                <CoachIcon size={24} />
            </button>
        </div>
    );
  }

  return (
    <div className="fixed bottom-28 md:bottom-6 right-6 z-40 animate-fade-in-up">
      <Button 
        onClick={onClick} 
        className="!rounded-full !py-3 !px-4 shadow-lg flex items-center gap-2"
        aria-label="Consultar al Coach"
      >
        <CoachIcon size={20} />
        <span className="hidden sm:inline">Consultar al Coach</span>
      </Button>
    </div>
  );
};

export default FloatingActionButton;