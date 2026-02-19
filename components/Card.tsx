

import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  title?: string;
}

const Card: React.FC<CardProps> = ({ children, className = '', title, ...props }) => {
  const cardClass = 'section-card';

  return (
    <div className={`${cardClass} p-4 ${className}`} {...props}>
      {children}
    </div>
  );
};

export default Card;