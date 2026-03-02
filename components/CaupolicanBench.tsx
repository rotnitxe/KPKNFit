import React, { useState } from 'react';

export const CaupolicanBench = () => {
  const [useSvg, setUseSvg] = useState(false);
  return (
    <img
      src={useSvg ? '/caupolican-bench.svg' : '/caupolican-bench.png'}
      alt=""
      className={`w-full h-full object-contain ${useSvg ? 'brightness-0 invert' : ''}`}
      aria-hidden
      onError={() => setUseSvg(true)}
    />
  );
};
