import React, { useState } from 'react';

export const CaupolicanCDL = () => {
  const [useSvg, setUseSvg] = useState(false);
  return (
    <img
      src={useSvg ? '/caupolican-cdl.svg' : '/caupolican-cdl.png'}
      alt=""
      className={`w-full h-full object-contain ${useSvg ? 'brightness-0 invert' : ''}`}
      aria-hidden
      onError={() => setUseSvg(true)}
    />
  );
};
