// components/home/SquareCardsCarousel.tsx
// Carrusel de tarjetas cuadradas: 3 por fila, paginado, dots indicadores

import React, { useState, useRef, useEffect } from 'react';

const CARDS_PER_ROW = 3;
const GAP = 16;

interface SquareCardsCarouselProps {
    children: React.ReactNode[];
    title?: string;
    gap?: number;
}

export const SquareCardsCarousel: React.FC<SquareCardsCarouselProps> = ({
    children,
    title,
    gap = GAP,
}) => {
    const scrollRef = useRef<HTMLDivElement>(null);
    const [pageIndex, setPageIndex] = useState(0);
    const [containerWidth, setContainerWidth] = useState<number | null>(null);

    const pages = React.Children.toArray(children).reduce<React.ReactNode[][]>((acc, child, i) => {
        const pageIdx = Math.floor(i / CARDS_PER_ROW);
        if (!acc[pageIdx]) acc[pageIdx] = [];
        acc[pageIdx].push(child);
        return acc;
    }, []);

    const totalPages = Math.max(1, pages.length);
    const hasMultiplePages = pages.length > 1;

    useEffect(() => {
        const el = scrollRef.current;
        if (!el) return;
        const measure = () => setContainerWidth(el.clientWidth);
        measure();
        const ro = new ResizeObserver(measure);
        ro.observe(el);
        return () => ro.disconnect();
    }, []);

    useEffect(() => {
        const el = scrollRef.current;
        if (!el) return;
        const handleScroll = () => {
            const scrollLeft = el.scrollLeft;
            const width = el.clientWidth;
            const newPage = Math.round(scrollLeft / (width + gap));
            setPageIndex(Math.min(Math.max(0, newPage), totalPages - 1));
        };
        el.addEventListener('scroll', handleScroll, { passive: true });
        return () => el.removeEventListener('scroll', handleScroll);
    }, [totalPages]);

    // Ancho por tarjeta: (ancho_contenedor - gaps) / 3
    const pageWidth = containerWidth ?? 0;
    const gapsBetweenCards = (CARDS_PER_ROW - 1) * gap;
    const cardWidth = pageWidth > 0 ? Math.floor((pageWidth - gapsBetweenCards) / CARDS_PER_ROW) : 0;

    return (
        <div className="w-full">
            {title && (
                <h2 className="text-[10px] font-black uppercase tracking-widest text-zinc-500 mb-2">
                    {title}
                </h2>
            )}
            <div className="relative -mx-3 sm:-mx-4">
                <div
                    ref={scrollRef}
                    className="overflow-x-auto overflow-y-hidden no-scrollbar px-3 sm:px-4"
                    style={{
                        scrollSnapType: 'x mandatory',
                        WebkitOverflowScrolling: 'touch',
                        scrollBehavior: 'smooth',
                    }}
                >
                    <div
                        className="flex"
                        style={{
                            width: totalPages * pageWidth + (totalPages - 1) * gap,
                            gap: `${gap}px`,
                        }}
                    >
                        {pages.map((pageChildren, pageIdx) => (
                            <div
                                key={pageIdx}
                                className="shrink-0 snap-start flex"
                                style={{
                                    width: pageWidth,
                                    gap: `${gap}px`,
                                }}
                            >
                                {pageChildren.map((child, idx) => (
                                    <div
                                        key={idx}
                                        className="shrink-0 overflow-hidden rounded-xl"
                                        style={{
                                            width: cardWidth,
                                            aspectRatio: '1 / 1.1',
                                        }}
                                    >
                                        {child}
                                    </div>
                                ))}
                            </div>
                        ))}
                    </div>
                </div>
            </div>
            {hasMultiplePages && (
                <div className="flex justify-center gap-1.5 mt-2">
                    {Array.from({ length: totalPages }).map((_, i) => (
                        <button
                            key={i}
                            onClick={() => {
                                const w = scrollRef.current!.clientWidth;
                                scrollRef.current?.scrollTo({
                                    left: (w + gap) * i,
                                    behavior: 'smooth',
                                });
                            }}
                            aria-label={`Página ${i + 1}`}
                            className={`w-1.5 h-1.5 rounded-full transition-colors ${
                                i === pageIndex ? 'bg-cyan-500' : 'bg-zinc-600'
                            }`}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};
