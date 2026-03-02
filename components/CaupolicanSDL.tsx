import React, { useState } from 'react';

export const CaupolicanSDL = () => {
  const [useSvg, setUseSvg] = useState(false);
  return (
    <img
      src={useSvg ? '/caupolican-sdl.svg' : '/caupolican-sdl.png'}
      alt=""
      className={`w-full h-full object-contain ${useSvg ? 'brightness-0 invert' : ''}`}
      aria-hidden
      onError={() => setUseSvg(true)}
    />
  );
};
