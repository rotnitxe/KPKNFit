import React, { useState, useEffect } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { SessionBackground } from '../types';

// This is a custom hook to manage the cross-fade logic
const useBackgroundFade = (background?: SessionBackground) => {
  const [bg1, setBg1] = useState(background);
  const [bg2, setBg2] = useState(background);
  const [activeBg, setActiveBg] = useState(1);

  useEffect(() => {
    // Check if the new background is different from the currently active one
    const currentActiveBg = activeBg === 1 ? bg1 : bg2;
    if (JSON.stringify(background) !== JSON.stringify(currentActiveBg)) {
      if (activeBg === 1) {
        setBg2(background);
      } else {
        setBg1(background);
      }
      setActiveBg(prev => (prev === 1 ? 2 : 1));
    }
  }, [background, bg1, bg2, activeBg]);

  return { bg1, bg2, activeBg };
};

const getStyle = (bg?: SessionBackground): React.CSSProperties => {
  if (!bg) return { backgroundImage: 'none', backgroundColor: 'transparent', opacity: 0 };

  if (bg.type === 'color') {
    return { backgroundColor: bg.value, backgroundImage: 'none', opacity: 1 };
  }
  // image
  return {
    backgroundImage: `url("${bg.value}")`,
    filter: `blur(${bg.style?.blur ?? 8}px) brightness(${bg.style?.brightness ?? 0.6})`,
    backgroundColor: 'transparent',
    opacity: 1
  };
};

const AppBackground: React.FC = () => {
    const { settings, currentBackgroundOverride } = useAppContext();
    const background = currentBackgroundOverride || settings.appBackground;
    const { bg1, bg2, activeBg } = useBackgroundFade(background);

    const isParallax = settings.enableParallax && background?.type === 'image';

    useEffect(() => {
      // For solid colors, apply to body and we're done. This component won't render anything.
      if (background?.type === 'color') {
          document.body.style.backgroundImage = 'none';
          document.body.style.backgroundColor = background.value;
      } else {
          // For images or default, let the body use its theme styles.
          document.body.style.backgroundImage = '';
          document.body.style.backgroundColor = '';
      }
    }, [background]);

    // Only render image background divs if the background is an image.
    if (background?.type !== 'image') {
      return null;
    }

    return (
        <>
            <div
                id="app-background-1"
                className={`app-background ${activeBg === 1 ? 'visible' : ''} ${isParallax ? 'parallax' : ''}`}
                style={getStyle(bg1)}
            />
            <div
                id="app-background-2"
                className={`app-background ${activeBg === 2 ? 'visible' : ''} ${isParallax ? 'parallax' : ''}`}
                style={getStyle(bg2)}
            />
        </>
    );
};

export default AppBackground;
