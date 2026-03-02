import React, { useState } from 'react';

export const CaupolicanSquat = () => {
  const [useSvg, setUseSvg] = useState(false);
  return (
    <img
      src={useSvg ? '/caupolican-squat.svg' : '/caupolican-squat.png'}
      alt=""
      className={`w-full h-full object-contain ${useSvg ? 'brightness-0 invert' : ''}`}
      aria-hidden
      onError={() => setUseSvg(true)}
    />
  );
};
