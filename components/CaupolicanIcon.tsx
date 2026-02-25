import React from 'react';

interface IconProps {
  className?: string;
  color?: string;
  size?: number | string;
  style?: React.CSSProperties;
}

export const CaupolicanIcon: React.FC<IconProps> = ({ className, color = 'currentColor', size = 150, style }) => {
  const sizeNum = typeof size === 'string' ? parseInt(size, 10) || 150 : size;
  return (
    <div
      className={className}
      style={{
        width: sizeNum,
        height: sizeNum,
        backgroundColor: color,
        mask: `url(/caupolican-icon.svg) no-repeat center`,
        maskSize: 'contain',
        WebkitMask: `url(/caupolican-icon.svg) no-repeat center`,
        WebkitMaskSize: 'contain',
        ...style,
      }}
      aria-hidden
    />
  );
};
