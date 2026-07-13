// components/onboarding/AnimatedSvgBackground.tsx
// Fondo SVG animado para Welcome y wizards (estética NERD)

import React, { useEffect, useRef, useState } from 'react';

export type SvgBackgroundVariant = 'vertical' | 'horizontal';
export type SvgBackgroundAnimation = 'parallax' | 'zoom' | 'scroll' | 'none';

interface AnimatedSvgBackgroundProps {
    src: string;
    variant: SvgBackgroundVariant;
    animation?: SvgBackgroundAnimation;
    opacity?: number;
    className?: string;
}

export const AnimatedSvgBackground: React.FC<AnimatedSvgBackgroundProps> = ({
    src,
    variant,
    animation = 'parallax',
    opacity = 0.28,
    className = '',
}) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const [scrollY, setScrollY] = useState(0);

    useEffect(() => {
        if (animation !== 'parallax' && animation !== 'scroll') return;
        const handleScroll = () => setScrollY(window.scrollY ?? 0);
        window.addEventListener('scroll', handleScroll, { passive: true });
        return () => window.removeEventListener('scroll', handleScroll);
    }, [animation]);

    const isVertical = variant === 'vertical';
    const parallaxOffset = scrollY * 0.15;

    return (
        <div
            ref={containerRef}
            className={`absolute inset-0 overflow-hidden pointer-events-none ${className}`}
            aria-hidden
        >
            <div
                className="absolute inset-0 transition-transform duration-300 ease-out"
                style={{
                    transform:
                        animation === 'parallax'
                            ? `translateY(${parallaxOffset * 0.5}px) scale(1.05)`
                            : animation === 'zoom'
                              ? 'scale(1.1)'
                              : animation === 'scroll'
                                ? `translateY(${-scrollY * 0.2}px)`
                                : undefined,
                }}
            >
                <img
                    src={src}
                    alt=""
                    className={`w-full h-full object-cover ${
                        animation === 'zoom' ? 'animate-zoom-slow' : ''
                    }`}
                    style={{
                        objectPosition: isVertical ? 'center top' : 'center center',
                        opacity,
                        minWidth: isVertical ? 'auto' : '120%',
                        minHeight: isVertical ? '120%' : 'auto',
                        filter: 'invert(1)',
                    }}
                />
            </div>
            {/* Overlay oscuro para contraste y legibilidad del texto (menos opaco para que el fondo se vea) */}
            <div
                className="absolute inset-0 bg-gradient-to-b from-black/65 via-black/55 to-black/70"
                style={{ opacity: 1 }}
            />
        </div>
    );
};
