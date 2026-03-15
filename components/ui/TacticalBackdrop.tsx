import React from 'react';

type BackdropVariant = 'modal' | 'toast';

interface TacticalBackdropProps {
  onClick?: () => void;
  variant?: BackdropVariant;
  className?: string;
}

const TacticalBackdrop: React.FC<TacticalBackdropProps> = ({
  onClick,
  variant = 'modal',
  className = '',
}) => (
  <div
    className={`
      absolute inset-0 transition-opacity duration-200 ease-out
      ${className}
    `}
    style={{ backgroundColor: 'rgba(0, 0, 0, 0.3)', backdropFilter: 'blur(4px)' }}
    onClick={onClick}
    aria-hidden="true"
  />
);

export default TacticalBackdrop;
