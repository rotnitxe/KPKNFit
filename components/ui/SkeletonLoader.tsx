// components/ui/SkeletonLoader.tsx
import React from 'react';

interface SkeletonLoaderProps {
  className?: string;
  type?: 'line' | 'card' | 'circle';
  lines?: number;
}

const SkeletonLoader: React.FC<SkeletonLoaderProps> = ({ className = '', type = 'line', lines = 3 }) => {
  if (type === 'card') {
    return <div className={`bg-slate-700/50 rounded-lg animate-pulse ${className}`} />;
  }

  if (type === 'circle') {
    return <div className={`bg-slate-700/50 rounded-full animate-pulse ${className}`} />;
  }

  return (
    <div className={`space-y-2.5 ${className}`}>
      {Array.from({ length: lines }).map((_, index) => (
        <div
          key={index}
          className="h-4 bg-slate-700/50 rounded-md animate-pulse"
          style={{ width: `${100 - (index % 3) * 15}%` }} // Varying widths
        />
      ))}
    </div>
  );
};

export default SkeletonLoader;
